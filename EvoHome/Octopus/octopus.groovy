/**
 * Smart Energy Usage and Agile Comparison for Hubitat Elevation
 *
 * Pulls up to 90 days of Octopus Energy half-hourly usage and presents
 * daily, weekly and monthly totals. Electricity cost comparison is estimated
 * from the historical unit rates for the active tariff agreements and Agile.
 * Gas usage is shown in the units returned by the meter API.
 */
import java.text.SimpleDateFormat

definition(
    name: "Smart Energy Usage",
    namespace: "tljungberg",
    author: "Tobey Ljungberg",
    description: "View Octopus Energy usage and estimate electricity savings on Agile.",
    category: "Convenience",
    singleInstance: false
)

preferences {
    page(name: "mainPage", title: "Smart Energy Usage", install: true, uninstall: true)
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Smart Energy Usage", install: true, uninstall: true, refreshInterval: 0) {
        section("Octopus account") {
            input "apiKey", "password", title: "API key", required: true
            input "accountNumber", "text", title: "Account number", required: true
            input "mpan", "text", title: "Electricity MPAN", required: true
            input "electricSerial", "text", title: "Electricity meter serial number", required: true
            input "mprn", "text", title: "Gas MPRN", required: true
            input "gasSerial", "text", title: "Gas meter serial number", required: true
            input "refreshButton", "button", title: "Refresh energy data"
            paragraph "The app requests the latest 90 days available from Octopus. API data can lag behind the current day."
        }

        section("Status") {
            if (state.lastError) paragraph "<b>Status:</b> ${html(state.lastError)}"
            else if (state.lastRefresh) paragraph "<b>Last refreshed:</b> ${html(state.lastRefresh)}"
            else paragraph "No data loaded yet. Save the settings, then tap Refresh energy data."
            if (state.currentElectricTariff) {
                paragraph "<b>Current electricity tariff:</b> ${html(state.currentElectricTariff)}"
            }
            if (state.alreadyOnAgile == true) {
                paragraph "Your current electricity tariff is Agile. The app shows actual Agile unit costs; estimated savings against Agile are £0."
            }
        }

        if (state.report) {
            section("Electricity") {
                paragraph "<b>Usage:</b> ${html(state.report.electricUnit)}"
                paragraph "<b>Estimated electricity unit charges over the available period:</b> ${html(state.report.currentElectricCost)} on your current tariff; ${html(state.report.agileElectricCost)} on Agile."
                paragraph "<b>Estimated Agile difference:</b> ${html(state.report.savings)}. Positive means Agile would have cost less. This estimate excludes standing charges."
                paragraph tableHtml("Electricity by day", state.report.electricDaily, "kWh")
                paragraph tableHtml("Electricity by week", state.report.electricWeekly, "kWh")
                paragraph tableHtml("Electricity by month", state.report.electricMonthly, "kWh")
            }
            section("Gas") {
                paragraph "Gas units are shown as returned by the meter API. Depending on the meter, this can be kWh or cubic metres."
                paragraph tableHtml("Gas by day", state.report.gasDaily, state.report.gasUnit)
                paragraph tableHtml("Gas by week", state.report.gasWeekly, state.report.gasUnit)
                paragraph tableHtml("Gas by month", state.report.gasMonthly, state.report.gasUnit)
            }
        }
    }
}

def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    if (apiKey && accountNumber && mpan && electricSerial && mprn && gasSerial) {
        runIn(2, refreshData)
    }
}

def appButtonHandler(String buttonName) {
    if (buttonName == "refreshButton") refreshData()
}

def refreshData() {
    state.lastError = null
    if (!credentialsReady()) {
        state.lastError = "Enter the API key, account number, MPAN, electricity serial, MPRN and gas serial first."
        return
    }

    try {
        String from = isoUtc(new Date(now() - (90L * 24L * 60L * 60L * 1000L)))
        String to = isoUtc(new Date(now()))
        Map account = getJson("/v1/accounts/${urlEncode(accountNumber.trim())}/", true)
        Map electricityPoint = findMeterPoint(account, "electricity_meter_points", "mpan", mpan.trim(), electricSerial.trim())
        Map gasPoint = findMeterPoint(account, "gas_meter_points", "mprn", mprn.trim(), gasSerial.trim())

        List electricUse = getConsumption("electricity-meter-points", mpan.trim(), electricSerial.trim(), from, to)
        List gasUse = getConsumption("gas-meter-points", mprn.trim(), gasSerial.trim(), from, to)

        List agreements = (electricityPoint.agreements ?: []).findAll { overlaps(it, from, to) }
        if (!agreements) throw new IllegalStateException("No electricity tariff agreements were found for the requested period.")

        String activeCode = activeAgreement(electricityPoint.agreements)?.tariff_code?.toString()
        if (!activeCode) throw new IllegalStateException("Could not identify the current electricity tariff from the account response.")
        state.currentElectricTariff = activeCode
        boolean alreadyAgile = activeCode.toUpperCase().contains("AGILE")
        state.alreadyOnAgile = alreadyAgile

        Map currentRates = [:]
        agreements.each { agreement ->
            String tariffCode = agreement.tariff_code?.toString()
            Map parsed = parseElectricTariff(tariffCode)
            if (!parsed) throw new IllegalStateException("Tariff ${tariffCode} is not a single-rate electricity tariff supported for cost comparison.")
            Date start = maxDate(parseDate(agreement.valid_from), parseDate(from))
            Date end = minDate(parseDate(agreement.valid_to) ?: new Date(now()), parseDate(to))
            if (start && end && start.before(end)) {
                String path = "/v1/products/${urlEncode(parsed.product)}/electricity-tariffs/${urlEncode(tariffCode)}/standard-unit-rates/"
                List rates = getPaged(path, [period_from: isoUtc(start), period_to: isoUtc(end), page_size: 5000], true)
                addRates(currentRates, rates)
            }
        }

        Map agileRates = [:]
        if (!alreadyAgile) {
            List agileProductSummaries = getPaged("/v1/products/", [brand: "OCTOPUS_ENERGY", is_variable: "true", is_business: "false", page_size: 500], false)
                .findAll { it.code?.toString()?.toUpperCase()?.startsWith("AGILE") && overlapsProduct(it, from, to) }
            if (!agileProductSummaries) throw new IllegalStateException("No Agile electricity product covering this 90-day period was found.")
            agileProductSummaries.each { summary ->
                Map product = getJson("/v1/products/${urlEncode(summary.code.toString())}/", false)
                String code = agileTariffCode(product, parseElectricTariff(activeCode)?.region)
                if (!code) throw new IllegalStateException("Could not find the Agile tariff for your electricity region in ${product.code}.")
                Map parsed = parseElectricTariff(code)
                Date start = laterOf(parseDate(product.available_from), parseDate(from))
                Date end = earlierOf(parseDate(product.available_to) ?: parseDate(to), parseDate(to))
                if (start && end && start.before(end)) {
                    String path = "/v1/products/${urlEncode(parsed.product)}/electricity-tariffs/${urlEncode(code)}/standard-unit-rates/"
                    addRates(agileRates, getPaged(path, [period_from: isoUtc(start), period_to: isoUtc(end), page_size: 5000], false))
                }
            }
        } else {
            agileRates = currentRates
        }

        Map report = buildReport(electricUse, gasUse, currentRates, agileRates, alreadyAgile)
        state.report = report
        state.lastRefresh = new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone ?: TimeZone.getTimeZone("Europe/London"))
    } catch (Exception e) {
        log.error "Energy data refresh failed: ${e.message}"
        state.lastError = "Refresh failed: ${e.message ?: e.class.simpleName}"
    }
}

private boolean credentialsReady() {
    return apiKey && accountNumber && mpan && electricSerial && mprn && gasSerial
}

private Map getJson(String path, boolean authenticated) {
    Map params = [uri: "https://api.oegb-kraken.energy${path}", contentType: "application/json", timeout: 30]
    if (authenticated) params.headers = [Authorization: basicAuthHeader()]
    Map result
    httpGet(params) { response ->
        if (response.status < 200 || response.status >= 300) throw new IllegalStateException("Octopus API returned HTTP ${response.status} for ${path}")
        result = response.data as Map
    }
    return result ?: [:]
}

private List getPaged(String path, Map query, boolean authenticated) {
    List rows = []
    String next = null
    boolean first = true
    int pages = 0
    while (true) {
        if (++pages > 80) throw new IllegalStateException("API pagination exceeded 80 pages for ${path}.")
        Map params = [uri: next ?: "https://api.oegb-kraken.energy${path}", contentType: "application/json", timeout: 30]
        if (first && query) params.query = query
        if (authenticated) params.headers = [Authorization: basicAuthHeader()]
        Map result
        httpGet(params) { response ->
            if (response.status < 200 || response.status >= 300) throw new IllegalStateException("Octopus API returned HTTP ${response.status} for ${path}")
            result = response.data as Map
        }
        if (result?.results instanceof Collection) rows.addAll(result.results)
        else if (result instanceof Map && result.containsKey("code")) rows << result
        next = result?.next?.toString()
        if (!next) break
        first = false
    }
    return rows
}

private List getConsumption(String endpoint, String pointId, String serial, String from, String to) {
    String path = "/v1/${endpoint}/${urlEncode(pointId)}/meters/${urlEncode(serial)}/consumption/"
    return getPaged(path, [period_from: from, period_to: to, page_size: 5000, order_by: "period"], true)
}

private Map findMeterPoint(Map account, String field, String idField, String id, String serial) {
    List points = []
    (account.properties ?: []).each { property -> points.addAll(property[field] ?: []) }
    Map point = points.find { it[idField]?.toString() == id }
    if (!point) throw new IllegalStateException("The account response did not contain ${field == 'electricity_meter_points' ? 'MPAN' : 'MPRN'} ${id}.")
    List meters = point.meters ?: []
    if (meters && !meters.any { it.serial_number?.toString()?.equalsIgnoreCase(serial) }) {
        throw new IllegalStateException("The meter serial number does not match ${idField.toUpperCase()} ${id} in the account response.")
    }
    return point
}

private Map activeAgreement(List agreements) {
    long current = now()
    return (agreements ?: []).find { Map agreement ->
        long start = parseDate(agreement.valid_from)?.time ?: 0L
        long end = parseDate(agreement.valid_to)?.time ?: Long.MAX_VALUE
        current >= start && current < end
    } as Map
}

private Map parseElectricTariff(String code) {
    if (!code) return null
    def matcher = (code =~ /^E-1R-(.+)-([A-P])$/)
    if (!matcher.matches()) return null
    return [product: matcher[0][1], region: matcher[0][2], code: code]
}

private String agileTariffCode(Map product, String region) {
    if (!region) return null
    Map regional = product.single_register_electricity_tariffs ?: [:]
    Map regionTariffs = regional["_${region}"] as Map
    if (!regionTariffs) return null
    Map payment = (regionTariffs.values().find { it instanceof Map && it.code } ?: [:]) as Map
    return payment.code?.toString()
}

private void addRates(Map ratesBySlot, List rates) {
    (rates ?: []).each { rate ->
        Date start = parseDate(rate.valid_from)
        Date end = parseDate(rate.valid_to) ?: new Date(now() + 1800000L)
        if (!start) return
        long slot = (long)(Math.floor(start.time / 1800000d) * 1800000L)
        // Agile returns half-hour rows; current tariff rows can span a longer fixed-price period.
        if (end && end.time - start.time > 1800000L) {
            for (long t = slot; t < end.time; t += 1800000L) ratesBySlot[t.toString()] = rate.value_inc_vat as BigDecimal
        } else {
            ratesBySlot[slot.toString()] = rate.value_inc_vat as BigDecimal
        }
    }
}

private Map buildReport(List electricUse, List gasUse, Map currentRates, Map agileRates, boolean alreadyAgile) {
    TimeZone tz = location.timeZone ?: TimeZone.getTimeZone("Europe/London")
    Map electricDay = [:]
    Map gasDay = [:]
    BigDecimal currentCost = 0G
    BigDecimal agileCost = 0G
    int pricedIntervals = 0
    int totalIntervals = 0
    Set gasUnits = [] as Set

    (electricUse ?: []).each { row ->
        Date start = parseDate(row.interval_start ?: row.period)
        BigDecimal amount = decimal(row.consumption)
        if (!start || amount == null) return
        String day = dayKey(start, tz)
        electricDay[day] = (electricDay[day] ?: 0G) + amount
        long slot = (long)(Math.floor(start.time / 1800000d) * 1800000L)
        String key = slot.toString()
        totalIntervals++
        BigDecimal currentRate = currentRates[key] as BigDecimal
        BigDecimal agileRate = agileRates[key] as BigDecimal
        if (alreadyAgile) agileRate = currentRate
        if (currentRate != null && agileRate != null) {
            currentCost += amount * currentRate / 100G
            agileCost += amount * agileRate / 100G
            pricedIntervals++
        }
    }

    (gasUse ?: []).each { row ->
        Date start = parseDate(row.interval_start ?: row.period)
        BigDecimal amount = decimal(row.consumption)
        if (!start || amount == null) return
        String day = dayKey(start, tz)
        gasDay[day] = (gasDay[day] ?: 0G) + amount
        if (row.unit) gasUnits << row.unit.toString()
    }

    Map report = [
        electricUnit: "kWh",
        gasUnit: gasUnits.size() == 1 ? gasUnits.first() : (gasUnits ? "API units" : "API units"),
        electricDaily: groupedRows(electricDay, "day", tz),
        electricWeekly: groupedRows(electricDay, "week", tz),
        electricMonthly: groupedRows(electricDay, "month", tz),
        gasDaily: groupedRows(gasDay, "day", tz),
        gasWeekly: groupedRows(gasDay, "week", tz),
        gasMonthly: groupedRows(gasDay, "month", tz),
        currentElectricCost: money(currentCost),
        agileElectricCost: money(agileCost),
        savings: money(currentCost - agileCost),
        pricedIntervals: pricedIntervals,
        totalIntervals: totalIntervals
    ]
    if (!alreadyAgile && totalIntervals > 0 && pricedIntervals < totalIntervals * 0.95) {
        state.lastError = "Prices were available for ${pricedIntervals} of ${totalIntervals} electricity intervals. Cost comparison is partial; verify the tariff history and Agile data before relying on it."
    }
    if (alreadyAgile) {
        report.currentElectricCost = money(agileCost)
        report.agileElectricCost = money(agileCost)
        report.savings = "£0.00"
    }
    return report
}

private List groupedRows(Map byDay, String grouping, TimeZone tz) {
    Map totals = [:]
    byDay.each { String key, BigDecimal amount ->
        Date date = parseDate(key + "T12:00:00Z")
        String bucket = grouping == "day" ? key : (grouping == "month" ? key.substring(0, 7) : weekKey(date, tz))
        totals[bucket] = (totals[bucket] ?: 0G) + amount
    }
    return totals.keySet().sort().reverse().take(grouping == "day" ? 14 : 16).collect { k -> [period: k, value: decimalText(totals[k])] }
}

private String tableHtml(String title, List rows, String unit) {
    String html = "<b>${title}</b><br/><table>"
    (rows ?: []).each { row -> html += "<tr><td>${html(row.period)}</td><td style='text-align:right'>${html(row.value)} ${html(unit)}</td></tr>" }
    return html + "</table>"
}

private String dayKey(Date date, TimeZone tz) {
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd")
    format.setTimeZone(tz)
    return format.format(date)
}

private String weekKey(Date date, TimeZone tz) {
    Calendar cal = Calendar.getInstance(tz)
    cal.setTime(date)
    cal.setFirstDayOfWeek(Calendar.MONDAY)
    cal.setMinimalDaysInFirstWeek(4)
    cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd")
    format.setTimeZone(tz)
    return format.format(cal.time)
}

private boolean overlaps(Map agreement, String from, String to) {
    Date start = parseDate(agreement.valid_from)
    Date end = parseDate(agreement.valid_to)
    Date fromDate = parseDate(from)
    Date toDate = parseDate(to)
    return (!end || !end.before(fromDate)) && (!start || start.before(toDate))
}

private boolean overlapsProduct(Map product, String from, String to) {
    Date start = parseDate(product.available_from)
    Date end = parseDate(product.available_to)
    Date fromDate = parseDate(from)
    Date toDate = parseDate(to)
    return (!end || !end.before(fromDate)) && (!start || start.before(toDate))
}

private Date parseDate(Object value) {
    if (!value) return null
    try {
        String text = value.toString()
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX")
        format.setTimeZone(TimeZone.getTimeZone("UTC"))
        try { return format.parse(text) } catch (ignored) { }
        format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX")
        format.setTimeZone(TimeZone.getTimeZone("UTC"))
        try { return format.parse(text) } catch (ignored) { }
        format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
        format.setTimeZone(TimeZone.getTimeZone("UTC"))
        try { return format.parse(text) } catch (ignored) { }
        format = new SimpleDateFormat("yyyy-MM-dd")
        format.setTimeZone(TimeZone.getTimeZone("UTC"))
        return format.parse(text)
    } catch (ignored) { return null }
}

private String isoUtc(Date date) {
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    format.setTimeZone(TimeZone.getTimeZone("UTC"))
    return format.format(date)
}

private Date maxDate(Date a, Date b) { (!a || (b && b.after(a))) ? b : a }
private Date minDate(Date a, Date b) { (!a || (b && b.before(a))) ? b : a }
private Date laterOf(Date a, Date b) { maxDate(a, b) }
private Date earlierOf(Date a, Date b) { minDate(a, b) }

private BigDecimal decimal(Object value) {
    try { return value == null ? null : new BigDecimal(value.toString()) } catch (ignored) { return null }
}

private String decimalText(Object value) {
    BigDecimal number = decimal(value) ?: 0G
    return number.setScale(3, BigDecimal.ROUND_HALF_UP).toPlainString()
}

private String money(Object value) {
    BigDecimal amount = decimal(value) ?: 0G
    return "£" + amount.setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString()
}

private String urlEncode(String value) {
    return java.net.URLEncoder.encode(value, "UTF-8")
}

private String basicAuthHeader() {
    return "Basic " + "${apiKey.trim()}:".getBytes("UTF-8").encodeBase64().toString()
}

private String html(Object value) {
    return (value == null ? "" : value.toString()).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
}
