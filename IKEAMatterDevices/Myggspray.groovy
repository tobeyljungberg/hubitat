/**
 * IKEA Myggspray Motion Sensor (Matter)
 *
 * Built from scratch as a Matter-first Hubitat driver.
 *
 * Device capabilities targeted from public device support data for IKEA VALLHORN-class sensors:
 * - Occupancy / motion
 * - Illuminance
 * - Battery percentage
 * - Power source classification
 *
 * Notes:
 * - Matter OTA/Firmware updates are generally controller-managed.
 * - This driver includes a manual command that attempts hub-managed update checks when available.
 */
import groovy.transform.CompileStatic
import groovy.transform.Field

@Field static final String DRIVER_NAME = 'IKEA Myggspray Motion Sensor (Matter)'
@Field static final String DRIVER_VERSION = '2.0.0'
@Field static final Integer HEALTH_THRESHOLD_SECONDS = 43200
@Field static final String HEALTH_CRON = '0 0 0/1 ? * * *'

metadata {
    definition(
        name: DRIVER_NAME,
        namespace: 'hubitat',
        author: 'Codex',
        singleThreaded: true
    ) {
        capability 'Configuration'
        capability 'Initialize'
        capability 'Refresh'
        capability 'Sensor'
        capability 'MotionSensor'
        capability 'IlluminanceMeasurement'
        capability 'Battery'
        capability 'HealthCheck'
        capability 'PowerSource'

        // Requested Matter clusters
        fingerprint inClusters: '0003,001D,0406', controllerType: 'MAT', manufacturer: 'IKEA of Sweden', model: 'MYGGSPRAY Motion Sensor'

        attribute 'healthStatus', 'enum', ['offline', 'online', 'unknown']
        attribute 'lastBattery', 'date'
        attribute 'lastSeen', 'date'
        attribute 'networkRejoinCount', 'number'

        command 'checkForFirmwareUpdate'
    }

    preferences {
        input name: 'logLevel', type: 'enum', title: 'Log verbosity', required: true,
            options: ['1':'Debug', '2':'Info', '3':'Warn', '4':'Error'], defaultValue: '2'

        input name: 'enableTxt', type: 'bool', title: 'Enable descriptionText logging', defaultValue: true
    }
}

void installed() {
    initialize()
}

void updated() {
    initialize()
}

void initialize() {
    if (!logLevel) {
        device.updateSetting('logLevel', [type: 'enum', value: '2'])
    }
    unschedule()
    schedule(HEALTH_CRON, 'healthCheck')

    if (device.currentValue('healthStatus', true) == null) {
        sendEvent(name: 'healthStatus', value: 'unknown')
    }
    if (device.currentValue('networkRejoinCount', true) == null) {
        sendEvent(name: 'networkRejoinCount', value: 0)
    }

    sendEvent(name: 'checkInterval', value: 3600, unit: 'second')
    state.lastSeenTs = state.lastSeenTs ?: 0L
    state.lastTxTs = state.lastTxTs ?: 0L
    state.lastDriverVersion = DRIVER_VERSION

    if (logLevel == '1') {
        runIn(1800, 'logsOff')
    }

    logInfo "Initialized ${DRIVER_NAME} v${DRIVER_VERSION}"
}

void configure() {
    logInfo 'Configure requested'
    initialize()
    refresh()
}

void refresh() {
    logInfo 'Refresh requested (waiting for next Matter reports)'
    state.lastTxTs = now()
}

void ping() {
    logInfo 'Ping requested'
    healthCheck()
}

void logsOff() {
    device.updateSetting('logLevel', [type: 'enum', value: '2'])
    logInfo 'Debug logging disabled automatically'
}

void healthCheck() {
    long lastSeenTs = (state.lastSeenTs ?: 0L) as long
    String status

    if (lastSeenTs <= 0L) {
        status = 'unknown'
    } else {
        status = (now() - lastSeenTs) < (HEALTH_THRESHOLD_SECONDS * 1000L) ? 'online' : 'offline'
    }

    if (device.currentValue('healthStatus', true) != status) {
        emitEvent('healthStatus', status, "Health status is ${status}", 'digital')
    }
}

/**
 * Matter OTA is typically controller/platform managed.
 * This command attempts to call platform-managed firmware update hooks if available.
 */
void checkForFirmwareUpdate() {
    logInfo 'Checking for firmware update support'
    state.lastTxTs = now()
    try {
        if (device.respondsTo('updateFirmware')) {
            device.updateFirmware()
            logInfo 'Firmware update request sent to platform'
        } else {
            logWarn 'Platform does not expose updateFirmware() for this Matter device; OTA may still be automatic via controller'
        }
    } catch (Throwable t) {
        logWarn "Firmware update request failed: ${t.message}"
    }
}

void parse(Object description) {
    Map msg = normalizeMatterMessage(description)
    if (msg.isEmpty()) {
        logDebug "Ignored message (unsupported): ${description}"
        return
    }

    touchLastSeen()

    if (device.currentValue('healthStatus', true) != 'online') {
        emitEvent('healthStatus', 'online', 'Health status changed to online', 'digital')
    }

    String eventType = recentlyTransmitted() ? 'digital' : 'physical'

    String eventName = (msg.event ?: msg.name ?: '').toString().trim().toLowerCase()
    String attrName = (msg.attrName ?: msg.attribute ?: msg.attributeName ?: '').toString().trim().toLowerCase()
    Integer cluster = parseNumber(msg.clusterInt ?: msg.clusterId ?: msg.cluster)
    Integer attributeId = parseNumber(msg.attrInt ?: msg.attrId ?: msg.attributeId)
    Object rawValue = msg.value != null ? msg.value : msg.rawValue

    // Motion / occupancy
    if (eventName in ['motion', 'occupancy'] || attrName in ['occupancy', 'occupancystate', 'occupancydetected'] || (cluster == 0x0406 && attributeId == 0x0000)) {
        boolean occupied = toBooleanValue(rawValue)
        emitEvent('motion', occupied ? 'active' : 'inactive', "Motion is ${occupied ? 'active' : 'inactive'}", 'physical')
        return
    }

    // Illuminance
    if (eventName in ['illuminance', 'illuminance_lux'] || attrName in ['illuminance', 'measuredvalue'] || (cluster == 0x0400 && attributeId == 0x0000)) {
        Integer lux = parseIlluminance(rawValue)
        if (lux != null) {
            emitEvent('illuminance', lux, "Illuminance is ${lux} lux", eventType, 'lx')
        }
        return
    }

    // Battery percentage (supports direct % or half-percent units)
    if (eventName in ['battery', 'batterypercentage', 'battery_percent'] || attrName in ['battery', 'batterypercentageremaining', 'batpercentremaining']) {
        Integer pct = parseBatteryPercent(rawValue)
        if (pct != null) {
            Date stamp = new Date()
            emitEvent('battery', pct, "Battery is ${pct}%", eventType, '%')
            emitEvent('lastBattery', stamp, "Last battery report at ${stamp}", eventType)
        }
        return
    }

    // Power source
    if (eventName in ['powersource', 'power_source'] || attrName in ['powersource', 'powersourcevalue'] || (attributeId == 0x0007)) {
        String source = parsePowerSource(rawValue)
        emitEvent('powerSource', source, "Power source is ${source}", 'digital')
        return
    }

    // Optional: count likely rejoin events
    if (eventName in ['rejoin', 'deviceannce', 'commissioningcomplete'] || cluster == 0x0013) {
        Integer count = ((device.currentValue('networkRejoinCount', true) ?: 0) as Integer) + 1
        emitEvent('networkRejoinCount', count, "Incremented networkRejoinCount to ${count}", 'physical')
        return
    }

    logDebug "Unhandled Matter message: ${msg}"
}

private void touchLastSeen() {
    long ts = now()
    state.lastSeenTs = ts
    sendEvent(name: 'lastSeen', value: new Date(ts))
}

private boolean recentlyTransmitted() {
    long ts = (state.lastTxTs ?: 0L) as long
    return ts > 0L && (now() - ts) < 3000L
}

private Map normalizeMatterMessage(Object description) {
    if (description instanceof Map) {
        return (Map) description
    }

    if (!(description instanceof String)) {
        return [:]
    }

    String text = description.toString().trim()
    if (!text || !text.contains(':')) {
        return [:]
    }

    Map out = [:]
    text.split(',').each { String part ->
        List<String> kv = part.split(':', 2)
        if (kv.size() == 2) {
            out[kv[0].trim()] = kv[1].trim()
        }
    }
    return out
}

@CompileStatic
private Integer parseNumber(Object value) {
    if (value == null) return null
    if (value instanceof Integer) return (Integer) value
    if (value instanceof Long) return ((Long) value).intValue()

    String s = value.toString().trim()
    if (s.isEmpty()) return null

    try {
        if (s.startsWith('0x') || s.startsWith('0X')) return Integer.parseInt(s.substring(2), 16)
        if (s ==~ /^[0-9A-Fa-f]+$/) return Integer.parseInt(s, 16)
        return Integer.parseInt(s)
    } catch (Exception ignored) {
        return null
    }
}

@CompileStatic
private boolean toBooleanValue(Object value) {
    if (value == null) return false
    if (value instanceof Boolean) return (Boolean) value
    if (value instanceof Number) return ((Number) value).intValue() > 0

    String s = value.toString().trim().toLowerCase()
    return s in ['1', '01', 'true', 'active', 'occupied', 'detected']
}

@CompileStatic
private Integer parseIlluminance(Object value) {
    if (value == null) return null

    if (value instanceof Number) {
        int n = ((Number) value).intValue()
        if (n <= 0) return 0
        // Handle reported measuredValue format where lux = 10^((measuredValue-1)/10000)
        if (n > 65535) return n
        if (n == 0xFFFF) return null
        return Math.round((float) Math.pow(10d, (n - 1) / 10000d))
    }

    String s = value.toString().trim()
    if (s.isEmpty()) return null

    try {
        if (s ==~ /^[0-9]+$/) {
            int dec = Integer.parseInt(s)
            return parseIlluminance(dec)
        }
        int hex = Integer.parseInt(s.replace('0x', '').replace('0X', ''), 16)
        return parseIlluminance(hex)
    } catch (Exception ignored) {
        return null
    }
}

@CompileStatic
private Integer parseBatteryPercent(Object value) {
    if (value == null) return null

    if (value instanceof Number) {
        int n = ((Number) value).intValue()
        if (n < 0) return null
        if (n <= 100) return n
        if (n == 255) return null
        return Math.max(0, Math.min(100, (int) (n / 2)))
    }

    String s = value.toString().trim()
    if (s.isEmpty()) return null

    try {
        if (s.endsWith('%')) {
            int pct = Integer.parseInt(s[0..-2].trim())
            return Math.max(0, Math.min(100, pct))
        }
        if (s ==~ /^[0-9]+$/) {
            return parseBatteryPercent(Integer.parseInt(s))
        }
        int hex = Integer.parseInt(s.replace('0x', '').replace('0X', ''), 16)
        return parseBatteryPercent(hex)
    } catch (Exception ignored) {
        return null
    }
}

@CompileStatic
private String parsePowerSource(Object value) {
    if (value == null) return 'unknown'

    if (value instanceof Number) {
        switch (((Number) value).intValue()) {
            case 0x01:
            case 0x02:
            case 0x05:
            case 0x06:
                return 'mains'
            case 0x03:
                return 'battery'
            case 0x04:
                return 'dc'
            default:
                return 'unknown'
        }
    }

    String s = value.toString().trim().toLowerCase()
    if (s in ['battery', 'bat']) return 'battery'
    if (s in ['mains', 'line', 'ac']) return 'mains'
    if (s in ['dc']) return 'dc'

    Integer n = parseNumber(value)
    if (n != null) return parsePowerSource(n)
    return 'unknown'
}

private void emitEvent(String name, Object value, String descriptionText, String type = 'digital', String unit = null) {
    Map evt = [name: name, value: value, type: type]
    if (unit != null) evt.unit = unit
    if (enableTxt != false) evt.descriptionText = descriptionText

    if (shouldLogInfo(name, value)) {
        logInfo "${descriptionText} [${type}]"
    } else {
        logDebug "${descriptionText} [${type}]"
    }

    sendEvent(evt)
}

private boolean shouldLogInfo(String name, Object newValue) {
    Object oldValue = device.currentValue(name, true)
    return oldValue != newValue
}

private void logDebug(String msg) {
    if (logLevel == '1') log.debug "${device.displayName} ${msg}"
}

private void logInfo(String msg) {
    if ((logLevel ?: '2') <= '2') log.info "${device.displayName} ${msg}"
}

private void logWarn(String msg) {
    if ((logLevel ?: '2') <= '3') log.warn "${device.displayName} ${msg}"
}
