/**
 *  Copyright 2023 Tobey Ljungberg (tljungberg)
 *
 *  Name: Evohome (Connect) - Refactored Version
 *
 *  Author: Tobey Ljungberg (tljungberg)
 *
 *  Description:
 *   - Connect your Honeywell Evohome System to SmartThings.
 *   - Requires the Evohome Heating Zone device handler.
 *
 *  License: Apache License 2.0
 */
definition(
    name: "Evohome (Connect)",
    namespace: "tljungberg",
    author: "Tobey Ljungberg (tljungberg)",
    description: "Connect your Honeywell Evohome System to SmartThings.",
    category: "My Apps",
    iconUrl: "http://cdn.device-icons.smartthings.com/Home/home1-icn.png",
    iconX2Url: "http://cdn.device-icons.smartthings.com/Home/home1-icn.png",
    iconX3Url: "http://cdn.device-icons.smartthings.com/Home/home1-icn.png",
    singleInstance: true
)

preferences {

    section ("Evohome:") {
        input "prefEvohomeUsername", "text", title: "Username", required: true, displayDuringSetup: true
        input "prefEvohomePassword", "password", title: "Password", required: true, displayDuringSetup: true
        input title: "Advanced Settings:", displayDuringSetup: true, type: "paragraph", element: "paragraph", description: "Change these only if needed"
        input "prefEvohomeStatusPollInterval", "number", title: "Polling Interval (minutes)", range: "1..60", defaultValue: 5, required: true, displayDuringSetup: true, description: "Poll Evohome every n minutes"
        input "prefEvohomeUpdateRefreshTime", "number", title: "Update Refresh Time (seconds)", range: "2..60", defaultValue: 3, required: true, displayDuringSetup: true, description: "Wait n seconds after an update before polling"
        input "prefEvohomeWindowFuncTemp", "decimal", title: "Window Function Temperature", range: "0..100", defaultValue: 5.0, required: true, displayDuringSetup: true, description: "Must match Evohome controller setting"
        input title: "Thermostat Modes", description: "Configure how long thermostat modes are applied for by default. Set to zero to apply modes permanently.", displayDuringSetup: true, type: "paragraph", element: "paragraph"
        input 'prefThermostatModeDuration', 'number', title: 'Away/Custom/DayOff Mode (days):', range: "0..99", defaultValue: 0, required: true, displayDuringSetup: true, description: 'Apply thermostat modes for this many days'
        input 'prefThermostatEconomyDuration', 'number', title: 'Economy Mode (hours):', range: "0..24", defaultValue: 0, required: true, displayDuringSetup: true, description: 'Apply economy mode for this many hours'
    }

    section("General:") {
        input "prefLogLevel", "enum", title: "Log Level", options: ["error", "warn", "info", "debug"], defaultValue: "info", displayDuringSetup: true
    }
}

/**********************************************************************
 *  Logging Helper Method
 **********************************************************************/
private void logMessage(String level, String msg) {
    def levels = [ "error": 1, "warn": 2, "info": 3, "debug": 4 ]
    def configuredLevel = (atomicState.logLevel ?: settings.prefLogLevel ?: "info").toLowerCase()
    if (levels[level] <= levels[configuredLevel]) {
        switch(level) {
            case "error": log.error msg; break
            case "warn": log.warn msg; break
            case "info": log.info msg; break
            case "debug": log.debug msg; break
        }
    }
}

/**********************************************************************
 *  Setup and Configuration Commands
 **********************************************************************/
def installed() {
    atomicState.installedAt = now()
    logMessage("debug", "${app.label}: Installed with settings: ${settings}")
}

def uninstalled() {
    getChildDevices()?.each { deleteChildDevice(it.deviceNetworkId) }
}

void updated() {
    logMessage("debug", "${app.label}: Updating with settings: ${settings}")

    // Set log level from preferences.
    atomicState.logLevel = settings.prefLogLevel ?: "info"
    
    addInUseGlobalVar("EvohomeRequestPoll")
    addInUseGlobalVar("EvohomeLastPolled")
    
    // Evohome configuration
    atomicState.evohomeEndpoint = 'https://mytotalconnectcomfort.com/WebApi'
    atomicState.evohomeAuth = [ tokenLifetimePercentThreshold : 50 ]
    atomicState.evohomeStatusPollInterval = settings.prefEvohomeStatusPollInterval
    atomicState.evohomeSchedulePollInterval = 60
    atomicState.evohomeUpdateRefreshTime = settings.prefEvohomeUpdateRefreshTime
    
    // Thermostat mode durations
    atomicState.thermostatModeDuration = settings.prefThermostatModeDuration
    atomicState.thermostatEconomyDuration = settings.prefThermostatEconomyDuration
    
    // Authenticate, subscribe and schedule tasks.
    authenticate()
    manageSubscriptions()
    manageSchedules()
    getEvohomeConfig()
    updateChildDeviceConfig()
    
    runIn(5, "poll")
}

/**********************************************************************
 *  Schedule and Subscription Management
 **********************************************************************/
void manageSchedules() {
    logMessage("debug", "${app.label}: manageSchedules()")
    
    Random rand = new Random(now())
    def randomOffset = rand.nextInt(60)
    
    // Schedule manageAuth every 5 minutes.
    try { unschedule(manageAuth) } catch(e) {}
    schedule("${randomOffset} 0/5 * * * ?", "manageAuth")
    
    // Schedule poll at the configured polling interval.
    randomOffset = rand.nextInt(60)
    try { unschedule(poll) } catch(e) {}
    schedule("${randomOffset} 0/${settings.prefEvohomeStatusPollInterval} * * * ?", "poll")
}

void manageSubscriptions() {
    logMessage("debug", "${app.label}: manageSubscriptions()")
    unsubscribe()
    subscribe(app, handleAppTouch)
    subscribe(location, "variable:EvohomeRequestPoll", globalVarHandler)
}

void manageAuth() {
    logMessage("debug", "${app.label}: manageAuth()")
    
    if (!atomicState.evohomeAuth?.authToken || atomicState.evohomeAuthFailed ||
       (!atomicState.evohomeAuth.expiresAt.toString().isNumber() || now() >= atomicState.evohomeAuth.expiresAt)) {
        logMessage("info", "${app.label}: manageAuth(): Authenticating due to missing or expired token.")
        authenticate()
    } else {
        def refreshAt = atomicState.evohomeAuth.expiresAt - (1000 * (atomicState.evohomeAuth.tokenLifetime * atomicState.evohomeAuth.tokenLifetimePercentThreshold / 100))
        if (now() >= refreshAt) {
            logMessage("info", "${app.label}: manageAuth(): Token needs refresh.")
            refreshAuthToken()
        } else {
            logMessage("info", "${app.label}: manageAuth(): Token is valid.")
        }
    }
}

/**********************************************************************
 *  Polling and Event Handlers
 **********************************************************************/
void poll(onlyZoneId=-1) {
    logMessage("debug", "${app.label}: poll(${onlyZoneId})")
    
    if (atomicState.evohomeAuthFailed) {
        manageAuth()
    }
    
    if (onlyZoneId == 0) {
        getEvohomeStatus()
        updateChildDevice()
    } else if (onlyZoneId != -1) {
        getEvohomeStatus(onlyZoneId)
        updateChildDevice(onlyZoneId)
    } else {
        def statusThreshold = (atomicState.evohomeStatusPollInterval * 60) - 30
        def scheduleThreshold = (atomicState.evohomeSchedulePollInterval * 60) - 30

        if (!atomicState.evohomeStatusUpdatedAt || atomicState.evohomeStatusUpdatedAt + (1000 * statusThreshold) < now()) {
            getEvohomeStatus()
        }
        if (!atomicState.evohomeSchedulesUpdatedAt || atomicState.evohomeSchedulesUpdatedAt + (1000 * scheduleThreshold) < now()) {
            getEvohomeSchedules()
        }
        updateChildDevice()
    }
}

void handleAppTouch(evt) {
    logMessage("debug", "${app.label}: handleAppTouch()")
    poll()
}

void globalVarHandler(evt) {
    if (evt.value?.toString()?.toLowerCase() == "true") {
        logMessage("info", "${app.label}: globalVarHandler() - Triggering zone update.")
        poll(0)
        setGlobalVar("EvohomeLastPolled", now())
        setGlobalVar("EvohomeRequestPoll", "false")
    }
}

/**********************************************************************
 *  SmartApp-Child Interface Commands
 **********************************************************************/
void updateChildDeviceConfig() {
    logMessage("debug", "${app.label}: updateChildDeviceConfig()")
    def activeDnis = []
    
    atomicState.evohomeConfig.each { loc ->
        loc.gateways.each { gateway ->
            gateway.temperatureControlSystems.each { tcs ->
                tcs.zones.each { zone ->
                    def dni = generateDni(loc.locationInfo.locationId, gateway.gatewayInfo.gatewayId, tcs.systemId, zone.zoneId)
                    activeDnis << dni
                    
                    def values = [
                        debug: atomicState.logLevel,
                        updateRefreshTime: atomicState.evohomeUpdateRefreshTime,
                        minHeatingSetpoint: formatTemperature(zone?.heatSetpointCapabilities?.minHeatSetpoint),
                        maxHeatingSetpoint: formatTemperature(zone?.heatSetpointCapabilities?.maxHeatSetpoint),
                        temperatureResolution: zone?.heatSetpointCapabilities?.valueResolution,
                        windowFunctionTemperature: formatTemperature(settings.prefEvohomeWindowFuncTemp),
                        zoneType: zone?.zoneType,
                        locationId: loc.locationInfo.locationId,
                        gatewayId: gateway.gatewayInfo.gatewayId,
                        systemId: tcs.systemId,
                        zoneId: zone.zoneId
                    ]
                    
                    def d = getChildDevice(dni)
                    if (!d) {
                        try {
                            values.label = "${zone.name} Heating Zone (Evohome)"
                            logMessage("info", "${app.label}: Creating device: ${values.label} with DNI: ${dni}")
                            d = addChildDevice("tljungberg", "Evohome Heating Zone", dni, values)
                        } catch (e) {
                            logMessage("error", "${app.label}: Error creating device ${values.label} with DNI: ${dni}. Error: ${e}")
                        }
                    }
                    if (d) {
                        d.generateEvent(values)
                    }
                }
            }
        }
    }
    
    logMessage("debug", "${app.label}: Active DNIs: ${activeDnis}")
    def devicesToDelete = getChildDevices().findAll { !activeDnis.contains(it.deviceNetworkId) }
    logMessage("debug", "${app.label}: Deleting ${devicesToDelete.size()} devices.")
    devicesToDelete.each {
        logMessage("info", "${app.label}: Deleting device with DNI: ${it.deviceNetworkId}")
        try {
            deleteChildDevice(it.deviceNetworkId)
        } catch(e) {
            logMessage("error", "${app.label}: Error deleting device with DNI: ${it.deviceNetworkId}. Error: ${e}")
        }
    }
}

void updateChildDevice(onlyZoneId=-1) {
    logMessage("debug", "${app.label}: updateChildDevice(${onlyZoneId})")
    
    atomicState.evohomeStatus.each { loc ->
        loc.gateways.each { gateway ->
            gateway.temperatureControlSystems.each { tcs ->
                tcs.zones.each { zone ->
                    if (onlyZoneId == -1 || onlyZoneId == zone.zoneId) {
                        def dni = generateDni(loc.locationId, gateway.gatewayId, tcs.systemId, zone.zoneId)
                        def d = getChildDevice(dni)
                        if (d) {
                            def schedule = atomicState.evohomeSchedules.find { it.dni == dni }
                            def currSw = getCurrentSwitchpoint(schedule.schedule)
                            def nextSw = getNextSwitchpoint(schedule.schedule)
                            def values = [
                                temperature: formatTemperature(zone?.temperatureStatus?.temperature),
                                heatingSetpoint: formatTemperature(zone?.heatSetpointStatus?.targetTemperature),
                                thermostatSetpoint: formatTemperature(zone?.heatSetpointStatus?.targetTemperature),
                                thermostatSetpointMode: formatSetpointMode(zone?.heatSetpointStatus?.setpointMode),
                                thermostatSetpointUntil: zone?.heatSetpointStatus?.until,
                                thermostatMode: formatThermostatMode(tcs?.systemModeStatus?.mode),
                                scheduledSetpoint: formatTemperature(currSw.temperature),
                                nextScheduledSetpoint: formatTemperature(nextSw.temperature),
                                nextScheduledTime: nextSw.time
                            ]
                            logMessage("debug", "${app.label}: Updating device with DNI: ${dni} with data: ${values}")
                            d.generateEvent(values)
                        } else {
                            logMessage("debug", "${app.label}: Device with DNI: ${dni} not found; skipping update.")
                        }
                    }
                }
            }
        }
    }
}

/**********************************************************************
 *  Evohome API Commands
 **********************************************************************/
private updateAuthState(respData) {
    def tmpAuth = atomicState.evohomeAuth ?: [:]
    tmpAuth.lastUpdated = now()
    tmpAuth.authToken = respData?.access_token
    tmpAuth.tokenLifetime = (respData?.expires_in?.toInteger() ?: 0)
    tmpAuth.expiresAt = now() + (tmpAuth.tokenLifetime * 1000)
    tmpAuth.refreshToken = respData?.refresh_token
    atomicState.evohomeAuth = tmpAuth
    atomicState.evohomeAuthFailed = false
    logMessage("debug", "${app.label}: Updated auth state: ${atomicState.evohomeAuth}")
    
    def exp = new Date(tmpAuth.expiresAt)
    logMessage("info", "${app.label}: Auth Token Expires At: ${exp}")
    
    def tmpHeaders = atomicState.evohomeHeaders ?: [:]
    tmpHeaders.Authorization = "bearer ${atomicState.evohomeAuth.authToken}"
    tmpHeaders.applicationId = 'b013aa26-9724-4dbd-8897-048b9aada249'
    tmpHeaders.Accept = 'application/json, application/xml, text/json, text/x-json, text/javascript, text/xml'
    atomicState.evohomeHeaders = tmpHeaders
    logMessage("debug", "${app.label}: Updated headers: ${atomicState.evohomeHeaders}")
}

private void doAuth(String grantType, Map body) {
    body.grant_type = grantType
    body.scope = 'EMEA-V1-Basic EMEA-V1-Anonymous EMEA-V1-Get-Current-User-Account EMEA-V1-Get-Location-Installation-Info-By-UserId'
    
    def requestParams = [
        uri: 'https://mytotalconnectcomfort.com/WebApi',
        path: '/Auth/OAuth/Token',
        headers: [
            Authorization: 'Basic YjAxM2FhMjYtOTcyNC00ZGJkLTg4OTctMDQ4YjlhYWRhMjQ5OnRlc3Q=',
            Accept: 'application/json, application/xml, text/json, text/x-json, text/javascript, text/xml',
            'Content-Type': 'application/x-www-form-urlencoded; charset=utf-8'
        ],
        body: body
    ]
    
    try {
        httpPost(requestParams) { resp ->
            if(resp.status == 200 && resp.data) {
                updateAuthState(resp.data)
                getEvohomeUserAccount()
            } else {
                logMessage("error", "${app.label}: doAuth(): No Data. Status: ${resp.status}")
                atomicState.evohomeAuthFailed = true
            }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        logMessage("error", "${app.label}: doAuth(): Error: ${e.statusCode}")
        atomicState.evohomeAuthFailed = true
    }
}

private authenticate() {
    logMessage("debug", "${app.label}: authenticate()")
    doAuth('password', [ Username: settings.prefEvohomeUsername, Password: settings.prefEvohomePassword ])
}

private refreshAuthToken() {
    logMessage("debug", "${app.label}: refreshAuthToken()")
    doAuth('refresh_token', [ refresh_token: atomicState.evohomeAuth.refreshToken ])
}

private getEvohomeUserAccount() {
    logMessage("info", "${app.label}: getEvohomeUserAccount(): Retrieving user account info.")
    def requestParams = [
        uri: atomicState.evohomeEndpoint,
        path: '/WebAPI/emea/api/v1/userAccount',
        headers: atomicState.evohomeHeaders
    ]
    try {
        httpGet(requestParams) { resp ->
            if (resp.status == 200 && resp.data) {
                atomicState.evohomeUserAccount = resp.data
                logMessage("debug", "${app.label}: User account data: ${atomicState.evohomeUserAccount}")
            } else {
                logMessage("error", "${app.label}: getEvohomeUserAccount(): No Data. Status: ${resp.status}")
            }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        logMessage("error", "${app.label}: getEvohomeUserAccount(): Error: ${e.statusCode}")
        if (e.statusCode == 401) {
            atomicState.evohomeAuthFailed = true
        }
    }
}

private getEvohomeConfig() {
    logMessage("info", "${app.label}: getEvohomeConfig(): Retrieving configuration.")
    def requestParams = [
        uri: atomicState.evohomeEndpoint,
        path: '/WebAPI/emea/api/v1/location/installationInfo',
        query: [ userId: atomicState.evohomeUserAccount.userId, includeTemperatureControlSystems: 'True' ],
        headers: atomicState.evohomeHeaders
    ]
    try {
        httpGet(requestParams) { resp ->
            if (resp.status == 200 && resp.data) {
                logMessage("debug", "${app.label}: Config data: ${resp.data}")
                atomicState.evohomeConfig = resp.data
                atomicState.evohomeConfigUpdatedAt = now()
            } else {
                logMessage("error", "${app.label}: getEvohomeConfig(): No Data. Status: ${resp.status}")
                return 'error'
            }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        logMessage("error", "${app.label}: getEvohomeConfig(): Error: ${e.statusCode}")
        if (e.statusCode == 401) {
            atomicState.evohomeAuthFailed = true
        }
        return e
    }
}

private getEvohomeStatus(onlyZoneId=-1) {
    logMessage("debug", "${app.label}: getEvohomeStatus(${onlyZoneId})")
    def newStatus = []
    
    if (onlyZoneId == -1) {
        logMessage("info", "${app.label}: Retrieving status for all zones.")
        atomicState.evohomeConfig.each { loc ->
            def locStatus = getEvohomeLocationStatus(loc.locationInfo.locationId)
            if (locStatus) { newStatus << locStatus }
        }
        if (newStatus) {
            atomicState.evohomeStatus = newStatus
            atomicState.evohomeStatusUpdatedAt = now()
        }
    } else {
        logMessage("info", "${app.label}: Retrieving status for zone ID: ${onlyZoneId}")
        def zoneStatus = getEvohomeZoneStatus(onlyZoneId)
        if (zoneStatus) {
            newStatus = atomicState.evohomeStatus
            newStatus.each { loc ->
                loc.gateways.each { gateway ->
                    gateway.temperatureControlSystems.each { tcs ->
                        tcs.zones.each { zone ->
                            if (onlyZoneId == zone.zoneId) {
                                zone.activeFaults = zoneStatus.activeFaults
                                zone.heatSetpointStatus = zoneStatus.heatSetpointStatus
                                zone.temperatureStatus = zoneStatus.temperatureStatus
                            }
                        }
                    }
                }
            }
            atomicState.evohomeStatus = newStatus
        }
    }
}

private getEvohomeLocationStatus(locationId) {
    logMessage("debug", "${app.label}: getEvohomeLocationStatus for Location ID: ${locationId}")
    def requestParams = [
        uri: atomicState.evohomeEndpoint,
        path: "/WebAPI/emea/api/v1/location/${locationId}/status",
        query: [ includeTemperatureControlSystems: 'True' ],
        headers: atomicState.evohomeHeaders
    ]
    try {
        httpGet(requestParams) { resp ->
            if(resp.status == 200 && resp.data) {
                logMessage("debug", "${app.label}: Location status: ${resp.data}")
                return resp.data
            } else {
                logMessage("error", "${app.label}: getEvohomeLocationStatus(): No Data. Status: ${resp.status}")
                return false
            }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        logMessage("error", "${app.label}: getEvohomeLocationStatus(): Error: ${e.statusCode}")
        if (e.statusCode == 401) { atomicState.evohomeAuthFailed = true }
        return false
    }
}

private getEvohomeZoneStatus(zoneId) {
    logMessage("debug", "${app.label}: getEvohomeZoneStatus(${zoneId})")
    def requestParams = [
        uri: atomicState.evohomeEndpoint,
        path: "/WebAPI/emea/api/v1/temperatureZone/${zoneId}/status",
        headers: atomicState.evohomeHeaders
    ]
    try {
        httpGet(requestParams) { resp ->
            if(resp.status == 200 && resp.data) {
                logMessage("debug", "${app.label}: Zone status: ${resp.data}")
                return resp.data
            } else {
                logMessage("error", "${app.label}: getEvohomeZoneStatus(): No Data. Status: ${resp.status}")
                return false
            }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        logMessage("error", "${app.label}: getEvohomeZoneStatus(): Error: ${e.statusCode}")
        if (e.statusCode == 401) { atomicState.evohomeAuthFailed = true }
        return false
    }
}

private getEvohomeSchedules() {
    logMessage("info", "${app.label}: getEvohomeSchedules(): Retrieving schedules.")
    def schedules = []
    atomicState.evohomeConfig.each { loc ->
        loc.gateways.each { gateway ->
            gateway.temperatureControlSystems.each { tcs ->
                tcs.zones.each { zone ->
                    def dni = generateDni(loc.locationInfo.locationId, gateway.gatewayInfo.gatewayId, tcs.systemId, zone.zoneId)
                    def schedule = getEvohomeZoneSchedule(zone.zoneId)
                    if (schedule) {
                        schedules << [ zoneId: zone.zoneId, dni: dni, schedule: schedule ]
                    }
                }
            }
        }
    }
    if (schedules) {
        atomicState.evohomeSchedules = schedules
        atomicState.evohomeSchedulesUpdatedAt = now()
    }
    return schedules
}

private getEvohomeZoneSchedule(zoneId) {
    logMessage("debug", "${app.label}: getEvohomeZoneSchedule(${zoneId})")
    def requestParams = [
        uri: atomicState.evohomeEndpoint,
        path: "/WebAPI/emea/api/v1/temperatureZone/${zoneId}/schedule",
        headers: atomicState.evohomeHeaders
    ]
    try {
        httpGet(requestParams) { resp ->
            if(resp.status == 200 && resp.data) {
                logMessage("debug", "${app.label}: Zone schedule: ${resp.data}")
                return resp.data
            } else {
                logMessage("error", "${app.label}: getEvohomeZoneSchedule(): No Data. Status: ${resp.status}")
                return false
            }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        logMessage("error", "${app.label}: getEvohomeZoneSchedule(): Error: ${e.statusCode}")
        if (e.statusCode == 401) { atomicState.evohomeAuthFailed = true }
        return false
    }
}

def setThermostatMode(systemId, mode, until=-1) {
    logMessage("debug", "${app.label}: setThermostatMode(): SystemID: ${systemId}, Mode: ${mode}, Until: ${until}")
    mode = mode.toLowerCase()
    int modeIndex
    switch (mode) {
        case 'auto': modeIndex = 0; break
        case 'off': modeIndex = 1; break
        case 'economy': modeIndex = 2; break
        case 'away': modeIndex = 3; break
        case 'dayoff': modeIndex = 4; break
        case 'custom': modeIndex = 6; break
        default:
            logMessage("error", "${app.label}: setThermostatMode(): Unsupported mode: ${mode}")
            modeIndex = 999
            break
    }
    
    def untilRes
    if (until == -1 && mode == 'economy') {
        until = atomicState.thermostatEconomyDuration ?: 0
    } else if (until == -1 && (mode in ['away','dayoff','custom'])) {
        until = atomicState.thermostatModeDuration ?: 0
    }
    
    if (until in ['permanent', 0, -1]) {
        untilRes = 0
    } else if (until instanceof Date) {
        untilRes = until.format("yyyy-MM-dd'T'HH:mm:00XX", TimeZone.getTimeZone('UTC'))
    } else if (until ==~ /\d+.*T.*/) {
        untilRes = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", until).format("yyyy-MM-dd'T'HH:mm:00XX", TimeZone.getTimeZone('UTC'))
    } else if (until.isNumber() && mode == 'economy') {
        untilRes = new Date(now() + (Math.round(until) * 3600000)).format("yyyy-MM-dd'T'HH:mm:00XX", TimeZone.getTimeZone('UTC'))
    } else if (until.isNumber() && (mode in ['away','dayoff','custom'])) {
        untilRes = new Date(now() + (Math.round(until) * 86400000)).format("yyyy-MM-dd'T'00:00:00XX", location.timeZone)
    } else {
        logMessage("warn", "${device.label}: setThermostatMode(): Unparsable 'until' value. Applying permanently.")
        untilRes = 0
    }
    
    if (untilRes != 0 && (mode in ['away','dayoff','custom'])) {
        untilRes = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", untilRes).format("yyyy-MM-dd'T'00:00:00XX", location.timeZone)).format("yyyy-MM-dd'T'HH:mm:00XX", TimeZone.getTimeZone('UTC'))
    }
    
    def body
    if (untilRes == 0 || mode in ['off', 'auto']) {
        body = [ SystemMode: modeIndex, TimeUntil: null, Permanent: 'True' ]
        logMessage("info", "${app.label}: setThermostatMode(): Mode ${mode} set permanently.")
    } else {
        body = [ SystemMode: modeIndex, TimeUntil: untilRes, Permanent: 'False' ]
        logMessage("info", "${app.label}: setThermostatMode(): Mode ${mode} set until ${untilRes}.")
    }
    
    def requestParams = [
        uri: atomicState.evohomeEndpoint,
        path: "/WebAPI/emea/api/v1/temperatureControlSystem/${systemId}/mode",
        body: body,
        headers: atomicState.evohomeHeaders
    ]
    
    try {
        httpPutJson(requestParams) { resp ->
            if(resp.status == 201 && resp.data) {
                logMessage("debug", "${app.label}: setThermostatMode() response: ${resp.data}")
                return null
            } else {
                logMessage("error", "${app.label}: setThermostatMode(): No Data. Status: ${resp.status}")
                return 'error'
            }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        logMessage("error", "${app.label}: setThermostatMode(): Error: ${e}")
        if (e.statusCode == 401) { atomicState.evohomeAuthFailed = true }
        return e
    }
}

def setHeatingSetpoint(zoneId, setpoint, until=-1) {
    logMessage("debug", "${app.label}: setHeatingSetpoint(): Zone ID: ${zoneId}, Setpoint: ${setpoint}, Until: ${until}")
    setpoint = formatTemperature(setpoint)
    
    def untilRes
    if (until in ['permanent', 0, -1]) {
        untilRes = 0
    } else if (until instanceof Date) {
        untilRes = until.format("yyyy-MM-dd'T'HH:mm:00XX", TimeZone.getTimeZone('UTC'))
    } else if (until ==~ /\d+.*T.*/) {
        untilRes = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", until).format("yyyy-MM-dd'T'HH:mm:00XX", TimeZone.getTimeZone('UTC'))
    } else {
        logMessage("warn", "${device.label}: setHeatingSetpoint(): Unparsable 'until' value. Applying permanently.")
        untilRes = 0
    }
    
    def body
    if (untilRes == 0) {
        body = [ HeatSetpointValue: setpoint, SetpointMode: 1, TimeUntil: null ]
        logMessage("info", "${app.label}: setHeatingSetpoint(): Zone ${zoneId} set permanently to ${setpoint}")
    } else {
        body = [ HeatSetpointValue: setpoint, SetpointMode: 2, TimeUntil: untilRes ]
        logMessage("info", "${app.label}: setHeatingSetpoint(): Zone ${zoneId} set to ${setpoint} until ${untilRes}")
    }
    
    def requestParams = [
        uri: atomicState.evohomeEndpoint,
        path: "/WebAPI/emea/api/v1/temperatureZone/${zoneId}/heatSetpoint",
        body: body,
        headers: atomicState.evohomeHeaders
    ]
    
    try {
        httpPutJson(requestParams) { resp ->
            if(resp.status == 201 && resp.data) {
                logMessage("debug", "${app.label}: setHeatingSetpoint() response: ${resp.data}")
                return null
            } else {
                logMessage("error", "${app.label}: setHeatingSetpoint(): No Data. Status: ${resp.status}")
                return 'error'
            }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        logMessage("error", "${app.label}: setHeatingSetpoint(): Error: ${e}")
        if (e.statusCode == 401) { atomicState.evohomeAuthFailed = true }
        return e
    }
}

def clearHeatingSetpoint(zoneId) {
    logMessage("info", "${app.label}: clearHeatingSetpoint(): Zone ID: ${zoneId}")
    def requestParams = [
        uri: atomicState.evohomeEndpoint,
        path: "/WebAPI/emea/api/v1/temperatureZone/${zoneId}/heatSetpoint",
        body: [ HeatSetpointValue: 0.0, SetpointMode: 0, TimeUntil: null ],
        headers: atomicState.evohomeHeaders
    ]
    try {
        httpPutJson(requestParams) { resp ->
            if(resp.status == 201 && resp.data) {
                logMessage("debug", "${app.label}: clearHeatingSetpoint() response: ${resp.data}")
                return null
            } else {
                logMessage("error", "${app.label}: clearHeatingSetpoint(): No Data. Status: ${resp.status}")
                return 'error'
            }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        logMessage("error", "${app.label}: clearHeatingSetpoint(): Error: ${e}")
        if (e.statusCode == 401) { atomicState.evohomeAuthFailed = true }
        return e
    }
}

/**********************************************************************
 *  Helper Commands
 **********************************************************************/
private generateDni(locId, gatewayId, systemId, deviceId) {
    return 'Evohome.' + [locId, gatewayId, systemId, deviceId].join('.')
}

private formatTemperature(t) {
    return Float.parseFloat("${t}").round(1).toString()
}

private formatSetpointMode(mode) {
    switch (mode) {
        case 'FollowSchedule': return 'followSchedule'
        case 'PermanentOverride': return 'permanentOverride'
        case 'TemporaryOverride': return 'temporaryOverride'
        default:
            logMessage("error", "${app.label}: formatSetpointMode(): Unknown mode: ${mode}")
            return mode.toLowerCase()
    }
}

private formatThermostatMode(mode) {
    switch (mode) {
        case 'Auto': return 'auto'
        case 'AutoWithEco': return 'economy'
        case 'Away': return 'away'
        case 'Custom': return 'custom'
        case 'DayOff': return 'dayOff'
        case 'HeatingOff': return 'off'
        default:
            logMessage("error", "${app.label}: formatThermostatMode(): Unknown mode: ${mode}")
            return mode.toLowerCase()
    }
}

private getCurrentSwitchpoint(schedule) {
    logMessage("debug", "${app.label}: getCurrentSwitchpoint()")
    Calendar c = new GregorianCalendar()
    def today = c.getTime().format("EEEE", location.timeZone)
    def ScheduleToday = schedule.dailySchedules.find { it.dayOfWeek == today }
    
    ScheduleToday.switchpoints.sort { it.timeOfDay }
    ScheduleToday.switchpoints = ScheduleToday.switchpoints.reverse()
    def currentSwitchPoint = ScheduleToday.switchpoints.find { it.timeOfDay < c.getTime().format("HH:mm:ss", location.timeZone) }
    
    if (!currentSwitchPoint) {
        logMessage("debug", "${app.label}: No current switchpoint; using yesterday's schedule.")
        c.add(Calendar.DATE, -1)
        def yesterday = c.getTime().format("EEEE", location.timeZone)
        def ScheduleYesterday = schedule.dailySchedules.find { it.dayOfWeek == yesterday }
        ScheduleYesterday.switchpoints.sort { it.timeOfDay }
        ScheduleYesterday.switchpoints = ScheduleYesterday.switchpoints.reverse()
        currentSwitchPoint = ScheduleYesterday.switchpoints[0]
    }
    
    def localDateStr = c.getTime().format("yyyy-MM-dd'T'", location.timeZone) + currentSwitchPoint.timeOfDay + c.getTime().format("XX", location.timeZone)
    def isoDateStr = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", localDateStr)
                      .format("yyyy-MM-dd'T'HH:mm:ssXX", TimeZone.getTimeZone('UTC'))
    currentSwitchPoint.time = isoDateStr
    logMessage("debug", "${app.label}: Current switchpoint: ${currentSwitchPoint}")
    return currentSwitchPoint
}

private getNextSwitchpoint(schedule) {
    logMessage("debug", "${app.label}: getNextSwitchpoint()")
    Calendar c = new GregorianCalendar()
    def today = c.getTime().format("EEEE", location.timeZone)
    def ScheduleToday = schedule.dailySchedules.find { it.dayOfWeek == today }
    
    ScheduleToday.switchpoints.sort { it.timeOfDay }
    def nextSwitchPoint = ScheduleToday.switchpoints.find { it.timeOfDay > c.getTime().format("HH:mm:ss", location.timeZone) }
    
    if (!nextSwitchPoint) {
        logMessage("debug", "${app.label}: No next switchpoint today; using tomorrow's schedule.")
        c.add(Calendar.DATE, 1)
        def tomorrow = c.getTime().format("EEEE", location.timeZone)
        def ScheduleTmrw = schedule.dailySchedules.find { it.dayOfWeek == tomorrow }
        ScheduleTmrw.switchpoints.sort { it.timeOfDay }
        nextSwitchPoint = ScheduleTmrw.switchpoints[0]
    }
    
    def localDateStr = c.getTime().format("yyyy-MM-dd'T'", location.timeZone) + nextSwitchPoint.timeOfDay + c.getTime().format("XX", location.timeZone)
    def isoDateStr = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", localDateStr)
                      .format("yyyy-MM-dd'T'HH:mm:ssXX", TimeZone.getTimeZone('UTC'))
    nextSwitchPoint.time = isoDateStr
    logMessage("debug", "${app.label}: Next switchpoint: ${nextSwitchPoint}")
    return nextSwitchPoint
}
