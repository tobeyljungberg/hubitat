/**
 *  Copyright 2023 Tobey Ljungberg (tljungberg)
 *
 *  Name: Evohome (Connect)
 *  Author: Tobey Ljungberg (tljungberg)
 *  Date: 2016-04-05
 *  Version: 0.08
 *
 *  Description:
 *   - Connect your Honeywell Evohome System to SmartThings.
 *   - Requires the Evohome Heating Zone device handler.
 *   - For latest documentation see: https://github.com/tljungberg/SmartThings
 *
 *  License:
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at:
 *      http://www.apache.org/licenses/LICENSE-2.0
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
    section("Evohome:") {
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
        input "prefDebugMode", "bool", title: "Enable debug logging?", defaultValue: true, displayDuringSetup: true
    }
}

/**********************************************************************
 *  Setup and Configuration Commands
 **********************************************************************/
def installed() {
    atomicState.installedAt = now()
    log.debug "${app.label}: Installed with settings: ${settings}"
}

def uninstalled() {
    if(getChildDevices()) {
        removeChildDevices(getChildDevices())
    }
}

void updated() {
    if (atomicState.debug) log.debug "${app.label}: Updating with settings: ${settings}"

    // General
    atomicState.debug = settings.prefDebugMode

    // Evohome
    atomicState.evohomeEndpoint = 'https://mytotalconnectcomfort.com/WebApi'
    atomicState.evohomeAuth = [ tokenLifetimePercentThreshold: 50 ]
    atomicState.evohomeStatusPollInterval = settings.prefEvohomeStatusPollInterval
    atomicState.evohomeSchedulePollInterval = 60
    atomicState.evohomeUpdateRefreshTime = settings.prefEvohomeUpdateRefreshTime

    // Thermostat Mode Durations
    atomicState.thermostatModeDuration = settings.prefThermostatModeDuration
    atomicState.thermostatEconomyDuration = settings.prefThermostatEconomyDuration

    // Force Authentication
    authenticate()

    // Refresh Subscriptions and Schedules
    manageSubscriptions()
    manageSchedules()

    // Refresh child device configuration
    getEvohomeConfig()
    updateChildDeviceConfig()

    // Run a poll after a short delay
    runIn(5, "poll")
}

/**********************************************************************
 *  Management Commands
 **********************************************************************/
void manageSchedules() {
    if (atomicState.debug) log.debug "${app.label}: manageSchedules()"

    // Generate a random offset (1-60 seconds)
    Random rand = new Random(now())
    def randomOffset = rand.nextInt(60)

    // Reschedule manageAuth every 5 minutes
    try {
        unschedule(manageAuth)
    } catch(e) { }
    randomOffset = rand.nextInt(60)
    schedule("${randomOffset} 0/5 * * * ?", "manageAuth")

    // Reschedule poll every minute
    try {
        unschedule(poll)
    } catch(e) { }
    randomOffset = rand.nextInt(60)
    schedule("${randomOffset} 0/1 * * * ?", "poll")
}

void manageSubscriptions() {
    if (atomicState.debug) log.debug "${app.label}: manageSubscriptions()"
    unsubscribe()
    subscribe(app, handleAppTouch)
}

void manageAuth() {
    if (atomicState.debug) log.debug "${app.label}: manageAuth()"

    if (!atomicState.evohomeAuth?.authToken) {
        log.info "${app.label}: No Auth Token. Authenticating..."
        authenticate()
    } else if (atomicState.evohomeAuthFailed) {
        log.info "${app.label}: Auth has failed. Re-authenticating..."
        authenticate()
    } else if (!atomicState.evohomeAuth.expiresAt.toString().isNumber() || now() >= atomicState.evohomeAuth.expiresAt) {
        log.info "${app.label}: Auth Token has expired. Re-authenticating..."
        authenticate()
    } else {
        def refreshAt = atomicState.evohomeAuth.expiresAt - (1000 * (atomicState.evohomeAuth.tokenLifetime * atomicState.evohomeAuth.tokenLifetimePercentThreshold / 100))
        if (now() >= refreshAt) {
            log.info "${app.label}: Auth Token needs refresh."
            refreshAuthToken()
        } else {
            log.info "${app.label}: Auth Token is valid."
        }
    }
}

/**********************************************************************
 *  Polling and Event Handling
 **********************************************************************/
void poll(onlyZoneId=-1) {
    if (atomicState.debug) log.debug "${app.label}: poll(${onlyZoneId})"
    
    if (atomicState.evohomeAuthFailed) {
        manageAuth()
    }
    
    if (onlyZoneId == 0) { // Force update for all zones
        getEvohomeStatus()
        updateChildDevice()
    } else if (onlyZoneId != -1) { // Update specific zone
        getEvohomeStatus(onlyZoneId)
        updateChildDevice(onlyZoneId)
    } else {
        def evohomeStatusPollThresh = (atomicState.evohomeStatusPollInterval * 60) - 30
        def evohomeSchedulePollThresh = (atomicState.evohomeSchedulePollInterval * 60) - 30

        if (!atomicState.evohomeStatusUpdatedAt || atomicState.evohomeStatusUpdatedAt + (1000 * evohomeStatusPollThresh) < now()) {
            getEvohomeStatus()
        }
        if (!atomicState.evohomeSchedulesUpdatedAt || atomicState.evohomeSchedulesUpdatedAt + (1000 * evohomeSchedulePollThresh) < now()) {
            getEvohomeSchedules()
        }
        updateChildDevice()
    }
}

void handleAppTouch(evt) {
    if (atomicState.debug) log.debug "${app.label}: handleAppTouch()"
    poll()
}

/**********************************************************************
 *  SmartApp-Child Interface Commands
 **********************************************************************/
void updateChildDeviceConfig() {
    if (atomicState.debug) log.debug "${app.label}: updateChildDeviceConfig()"
    
    def activeDnis = []

    // Iterate through the configuration and create/update child devices
    atomicState.evohomeConfig.each { loc ->
        loc.gateways.each { gateway ->
            gateway.temperatureControlSystems.each { tcs ->
                tcs.zones.each { zone ->
                    def dni = generateDni(loc.locationInfo.locationId, gateway.gatewayInfo.gatewayId, tcs.systemId, zone.zoneId)
                    activeDnis << dni
                    
                    def values = [
                        'debug'                : atomicState.debug,
                        'updateRefreshTime'    : atomicState.evohomeUpdateRefreshTime,
                        'minHeatingSetpoint'   : formatTemperature(zone?.heatSetpointCapabilities?.minHeatSetpoint),
                        'maxHeatingSetpoint'   : formatTemperature(zone?.heatSetpointCapabilities?.maxHeatSetpoint),
                        'temperatureResolution': zone?.heatSetpointCapabilities?.valueResolution,
                        'windowFunctionTemperature': formatTemperature(settings.prefEvohomeWindowFuncTemp),
                        'zoneType'             : zone?.zoneType,
                        'locationId'           : loc.locationInfo.locationId,
                        'gatewayId'            : gateway.gatewayInfo.gatewayId,
                        'systemId'             : tcs.systemId,
                        'zoneId'               : zone.zoneId
                    ]
                    
                    def d = getChildDevice(dni)
                    if (!d) {
                        try {
                            values.put('label', "${zone.name} Heating Zone (Evohome)")
                            log.info "${app.label}: Creating device: ${values.label} (DNI: ${dni})"
                            d = addChildDevice("tljungberg", "Evohome Heating Zone", dni, values)
                        } catch (e) {
                            log.error "${app.label}: Error creating device ${values.label} (DNI: ${dni}): ${e}"
                        }
                    }
                    if (d) {
                        d.generateEvent(values)
                    }
                }
            }
        }
    }
    
    log.debug "${app.label}: Active DNIs: ${activeDnis}"
    
    // Delete any devices not present in the active DNIs list
    def delete = getChildDevices().findAll { !activeDnis.contains(it.deviceNetworkId) }
    log.debug "${app.label}: Found ${delete.size()} devices to delete."
    delete.each {
        log.info "${app.label}: Deleting device with DNI: ${it.deviceNetworkId}"
        try {
            deleteChildDevice(it.deviceNetworkId)
        } catch (e) {
            log.error "${app.label}: Error deleting device ${it.deviceNetworkId}: ${e}"
        }
    }
}

void updateChildDevice(onlyZoneId=-1) {
    if (atomicState.debug) log.debug "${app.label}: updateChildDevice(${onlyZoneId})"
    
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
                                'temperature'            : formatTemperature(zone?.temperatureStatus?.temperature),
                                'heatingSetpoint'        : formatTemperature(zone?.heatSetpointStatus?.targetTemperature),
                                'thermostatSetpoint'     : formatTemperature(zone?.heatSetpointStatus?.targetTemperature),
                                'thermostatSetpointMode' : formatSetpointMode(zone?.heatSetpointStatus?.setpointMode),
                                'thermostatSetpointUntil': zone?.heatSetpointStatus?.until,
                                'thermostatMode'         : formatThermostatMode(tcs?.systemModeStatus?.mode),
                                'scheduledSetpoint'      : formatTemperature(currSw.temperature),
                                'nextScheduledSetpoint'  : formatTemperature(nextSw.temperature),
                                'nextScheduledTime'      : nextSw.time
                            ]
                            if (atomicState.debug) log.debug "${app.label}: Updating device ${dni} with ${values}"
                            d.generateEvent(values)
                        } else {
                            if (atomicState.debug) log.debug "${app.label}: Device ${dni} does not exist; skipping update."
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
private authenticate() {
    if (atomicState.debug) log.debug "${app.label}: authenticate()"
    
    def requestParams = [
        uri   : 'https://mytotalconnectcomfort.com/WebApi',
        path  : '/Auth/OAuth/Token',
        headers: [
            'Authorization': 'Basic YjAxM2FhMjYtOTcyNC00ZGJkLTg4OTctMDQ4YjlhYWRhY...',
            'Accept'       : 'application/json, application/xml, text/json, text/x-json, text/javascript, text/xml',
            'Content-Type' : 'application/x-www-form-urlencoded; charset=utf-8'
        ],
        body: [
            'grant_type': 'password',
            'scope'     : 'EMEA-V1-Basic EMEA-V1-Anonymous EMEA-V1-Get-Current-User-Account EMEA-V1-Get-Location-Installation-Info-By-UserId',
            'Username'  : settings.prefEvohomeUsername,
            'Password'  : settings.prefEvohomePassword
        ]
    ]
    
    try {
        httpPost(requestParams) { resp ->
            if(resp.status == 200 && resp.data) {
                def tmpAuth = atomicState.evohomeAuth ?: [:]
                tmpAuth.lastUpdated = now()
                tmpAuth.authToken = resp?.data?.access_token
                tmpAuth.tokenLifetime = resp?.data?.expires_in.toInteger() ?: 0
                tmpAuth.expiresAt = now() + (tmpAuth.tokenLifetime * 1000)
                tmpAuth.refreshToken = resp?.data?.refresh_token
                atomicState.evohomeAuth = tmpAuth
                atomicState.evohomeAuthFailed = false

                if (atomicState.debug) log.debug "${app.label}: New evohomeAuth: ${atomicState.evohomeAuth}"
                def exp = new Date(tmpAuth.expiresAt)
                log.info "${app.label}: Auth Token Expires At: ${exp}"

                def tmpHeaders = atomicState.evohomeHeaders ?: [:]
                tmpHeaders.Authorization = "bearer ${atomicState.evohomeAuth.authToken}"
                tmpHeaders.applicationId = 'b013aa26-9724-4dbd-8897-048b9aada249'
                tmpHeaders.Accept = 'application/json, application/xml, text/json, text/x-json, text/javascript, text/xml'
                atomicState.evohomeHeaders = tmpHeaders

                if (atomicState.debug) log.debug "${app.label}: New evohomeHeaders: ${atomicState.evohomeHeaders}"
                getEvohomeUserAccount()
            } else {
                log.error "${app.label}: authenticate(): No Data. Response Status: ${resp.status}"
                atomicState.evohomeAuthFailed = true
            }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        log.error "${app.label}: authenticate(): Error: ${e.statusCode}"
        atomicState.evohomeAuthFailed = true
    }
}

private refreshAuthToken() {
    if (atomicState.debug) log.debug "${app.label}: refreshAuthToken()"

    def requestParams = [
        uri   : 'https://mytotalconnectcomfort.com/WebApi',
        path  : '/Auth/OAuth/Token',
        headers: [
            'Authorization': 'Basic YjAxM2FhMjYtOTcyNC00ZGJkLTg4OTctMDQ4YjlhYWRhY...',
            'Accept'       : 'application/json, application/xml, text/json, text/x-json, text/javascript, text/xml',
            'Content-Type' : 'application/x-www-form-urlencoded; charset=utf-8'
        ],
        body: [
            'grant_type'   : 'refresh_token',
            'scope'        : 'EMEA-V1-Basic EMEA-V1-Anonymous EMEA-V1-Get-Current-User-Account EMEA-V1-Get-Location-Installation-Info-By-UserId',
            'refresh_token': atomicState.evohomeAuth.refreshToken
        ]
    ]
    
    try {
        httpPost(requestParams) { resp ->
            if(resp.status == 200 && resp.data) {
                def tmpAuth = atomicState.evohomeAuth ?: [:]
                tmpAuth.lastUpdated = now()
                tmpAuth.authToken = resp?.data?.access_token
                tmpAuth.tokenLifetime = resp?.data?.expires_in.toInteger() ?: 0
                tmpAuth.expiresAt = now() + (tmpAuth.tokenLifetime * 1000)
                tmpAuth.refreshToken = resp?.data?.refresh_token
                atomicState.evohomeAuth = tmpAuth
                atomicState.evohomeAuthFailed = false

                if (atomicState.debug) log.debug "${app.label}: refreshAuthToken(): New evohomeAuth: ${atomicState.evohomeAuth}"
                def exp = new Date(tmpAuth.expiresAt)
                log.info "${app.label}: refreshAuthToken(): Auth Token Expires At: ${exp}"

                def tmpHeaders = atomicState.evohomeHeaders ?: [:]
                tmpHeaders.Authorization = "bearer ${atomicState.evohomeAuth.authToken}"
                tmpHeaders.applicationId = 'b013aa26-9724-4dbd-8897-048b9aada249'
                tmpHeaders.Accept = 'application/json, application/xml, text/json, text/x-json, text/javascript, text/xml'
                atomicState.evohomeHeaders = tmpHeaders

                if (atomicState.debug) log.debug "${app.label}: refreshAuthToken(): New evohomeHeaders: ${atomicState.evohomeHeaders}"
                getEvohomeUserAccount()
            } else {
                log.error "${app.label}: refreshAuthToken(): No Data. Response Status: ${resp.status}"
            }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        log.error "${app.label}: refreshAuthToken(): Error: ${e}"
        if (e.statusCode == 401) {
            atomicState.evohomeAuthFailed = true
            authenticate()
        }
    }
}

private getEvohomeUserAccount() {
    log.info "${app.label}: Getting user account information."
    
    def requestParams = [
        uri    : atomicState.evohomeEndpoint,
        path   : '/WebAPI/emea/api/v1/userAccount',
        headers: atomicState.evohomeHeaders
    ]
    
    try {
        httpGet(requestParams) { resp ->
            if (resp.status == 200 && resp.data) {
                atomicState.evohomeUserAccount = resp.data
                if (atomicState.debug) log.debug "${app.label}: User account data: ${atomicState.evohomeUserAccount}"
            } else {
                log.error "${app.label}: getEvohomeUserAccount(): No Data. Response Status: ${resp.status}"
            }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        log.error "${app.label}: getEvohomeUserAccount(): Error: ${e.statusCode}"
        if (e.statusCode == 401) {
            atomicState.evohomeAuthFailed = true
        }
    }
}

private getEvohomeConfig() {
    log.info "${app.label}: Getting configuration for all locations."

    def requestParams = [
        uri    : atomicState.evohomeEndpoint,
        path   : '/WebAPI/emea/api/v1/location/installationInfo',
        query  : [
            'userId': atomicState.evohomeUserAccount.userId,
            'includeTemperatureControlSystems': 'True'
        ],
        headers: atomicState.evohomeHeaders
    ]
    
    try {
        httpGet(requestParams) { resp ->
            if (resp.status == 200 && resp.data) {
                if (atomicState.debug) log.debug "${app.label}: Configuration data: ${resp.data}"
                atomicState.evohomeConfig = resp.data
                atomicState.evohomeConfigUpdatedAt = now()
            } else {
                log.error "${app.label}: getEvohomeConfig(): No Data. Response Status: ${resp.status}"
                return 'error'
            }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        log.error "${app.label}: getEvohomeConfig(): Error: ${e.statusCode}"
        if (e.statusCode == 401) {
            atomicState.evohomeAuthFailed = true
        }
        return e
    }
}

private getEvohomeStatus(onlyZoneId=-1) {
    if (atomicState.debug) log.debug "${app.label}: getEvohomeStatus(${onlyZoneId})"
    
    def newEvohomeStatus = []
    if (onlyZoneId == -1) {
        log.info "${app.label}: Getting status for all zones."
        atomicState.evohomeConfig.each { loc ->
            def locStatus = getEvohomeLocationStatus(loc.locationInfo.locationId)
            if (locStatus) {
                newEvohomeStatus << locStatus
            }
        }
        if (newEvohomeStatus) {
            atomicState.evohomeStatus = newEvohomeStatus
            atomicState.evohomeStatusUpdatedAt = now()
        }
    } else {
        log.info "${app.label}: Getting status for zone ID: ${onlyZoneId}"
        def newZoneStatus = getEvohomeZoneStatus(onlyZoneId)
        if (newZoneStatus) {
            newEvohomeStatus = atomicState.evohomeStatus
            newEvohomeStatus.each { loc ->
                loc.gateways.each { gateway ->
                    gateway.temperatureControlSystems.each { tcs ->
                        tcs.zones.each { zone ->
                            if (onlyZoneId == zone.zoneId) {
                                zone.activeFaults = newZoneStatus.activeFaults
                                zone.heatSetpointStatus = newZoneStatus.heatSetpointStatus
                                zone.temperatureStatus = newZoneStatus.temperatureStatus
                            }
                        }
                    }
                }
            }
            atomicState.evohomeStatus = newEvohomeStatus
        }
    }
}

private getEvohomeLocationStatus(locationId) {
    if (atomicState.debug) log.debug "${app.label}: getEvohomeLocationStatus: Location ID: ${locationId}"
    
    def requestParams = [
        uri    : atomicState.evohomeEndpoint,
        path   : "/WebAPI/emea/api/v1/location/${locationId}/status", 
        query  : [ 'includeTemperatureControlSystems': 'True' ],
        headers: atomicState.evohomeHeaders
    ]
    
    try {
        httpGet(requestParams) { resp ->
            if(resp.status == 200 && resp.data) {
                if (atomicState.debug) log.debug "${app.label}: Location status data: ${resp.data}"
                return resp.data
            } else {
                log.error "${app.label}: getEvohomeLocationStatus(): No Data. Response Status: ${resp.status}"
                return false
            }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        log.error "${app.label}: getEvohomeLocationStatus(): Error: ${e.statusCode}"
        if (e.statusCode == 401) {
            atomicState.evohomeAuthFailed = true
        }
        return false
    }
}

private getEvohomeZoneStatus(zoneId) {
    if (atomicState.debug) log.debug "${app.label}: getEvohomeZoneStatus(${zoneId})"
    
    def requestParams = [
        uri    : atomicState.evohomeEndpoint,
        path   : "/WebAPI/emea/api/v1/temperatureZone/${zoneId}/status",
        headers: atomicState.evohomeHeaders
    ]
    
    try {
        httpGet(requestParams) { resp ->
            if(resp.status == 200 && resp.data) {
                if (atomicState.debug) log.debug "${app.label}: Zone status data: ${resp.data}"
                return resp.data
            } else {
                log.error "${app.label}: getEvohomeZoneStatus(): No Data. Response Status: ${resp.status}"
                return false
            }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        log.error "${app.label}: getEvohomeZoneStatus(): Error: ${e.statusCode}"
        if (e.statusCode == 401) {
            atomicState.evohomeAuthFailed = true
        }
        return false
    }
}

private getEvohomeSchedules() {
    log.info "${app.label}: Getting schedules for all zones."
    
    def evohomeSchedules = []
    atomicState.evohomeConfig.each { loc ->
        loc.gateways.each { gateway ->
            gateway.temperatureControlSystems.each { tcs ->
                tcs.zones.each { zone ->
                    def dni = generateDni(loc.locationInfo.locationId, gateway.gatewayInfo.gatewayId, tcs.systemId, zone.zoneId)
                    def schedule = getEvohomeZoneSchedule(zone.zoneId)
                    if (schedule) {
                        evohomeSchedules << ['zoneId': zone.zoneId, 'dni': dni, 'schedule': schedule]
                    }
                }
            }
        }
    }
    if (evohomeSchedules) {
        atomicState.evohomeSchedules = evohomeSchedules
        atomicState.evohomeSchedulesUpdatedAt = now()
    }
    return evohomeSchedules
}

private getEvohomeZoneSchedule(zoneId) {
    if (atomicState.debug) log.debug "${app.label}: getEvohomeZoneSchedule(${zoneId})"
    
    def requestParams = [
        uri    : atomicState.evohomeEndpoint,
        path   : "/WebAPI/emea/api/v1/temperatureZone/${zoneId}/schedule",
        headers: atomicState.evohomeHeaders
    ]
    
    try {
        httpGet(requestParams) { resp ->
            if(resp.status == 200 && resp.data) {
                if (atomicState.debug) log.debug "${app.label}: Zone schedule data: ${resp.data}"
                return resp.data
            } else {
                log.error "${app.label}: getEvohomeZoneSchedule(): No Data. Response Status: ${resp.status}"
                return false
            }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        log.error "${app.label}: getEvohomeZoneSchedule(): Error: ${e.statusCode}"
        if (e.statusCode == 401) {
            atomicState.evohomeAuthFailed = true
        }
        return false
    }
}

def setThermostatMode(systemId, mode, until=-1) {
    if (atomicState.debug) log.debug "${app.label}: setThermostatMode(): SystemID: ${systemId}, Mode: ${mode}, Until: ${until}"
    
    mode = mode.toLowerCase()
    int modeIndex
    switch (mode) {
        case 'auto':      modeIndex = 0; break
        case 'off':       modeIndex = 1; break
        case 'economy':   modeIndex = 2; break
        case 'away':      modeIndex = 3; break
        case 'dayoff':    modeIndex = 4; break
        case 'custom':    modeIndex = 6; break
        default:
            log.error "${app.label}: setThermostatMode(): Unsupported mode: ${mode}"
            modeIndex = 999
            break
    }
    
    def untilRes
    if (-1 == until && mode == 'economy') { 
        until = atomicState.thermostatEconomyDuration ?: 0
    } else if (-1 == until && (mode == 'away' || mode == 'dayoff' || mode == 'custom')) {
        until = atomicState.thermostatModeDuration ?: 0
    }
    
    if ('permanent' == until || 0 == until || -1 == until) {
        untilRes = 0
    } else if (until instanceof Date) {
        untilRes = until.format("yyyy-MM-dd'T'HH:mm:00XX", TimeZone.getTimeZone('UTC'))
    } else if (until ==~ /\d+.*T.*/) {
        untilRes = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", until).format("yyyy-MM-dd'T'HH:mm:00XX", TimeZone.getTimeZone('UTC'))
    } else if (until.isNumber() && mode == 'economy') {
        untilRes = new Date(now() + (Math.round(until) * 3600000)).format("yyyy-MM-dd'T'HH:mm:00XX", TimeZone.getTimeZone('UTC'))
    } else if (until.isNumber() && (mode == 'away' || mode == 'dayoff' || mode == 'custom')) {
        untilRes = new Date(now() + (Math.round(until) * 86400000)).format("yyyy-MM-dd'T'00:00:00XX", location.timeZone)
    } else {
        log.warn "${device.label}: setThermostatMode(): Could not parse 'until' value. Applying permanently."
        untilRes = 0
    }
    
    if (untilRes != 0 && (mode == 'away' || mode == 'dayoff' || mode == 'custom')) { 
        untilRes = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", untilRes)
                     .format("yyyy-MM-dd'T'00:00:00XX", location.timeZone))
                     .format("yyyy-MM-dd'T'HH:mm:00XX", TimeZone.getTimeZone('UTC'))
    }
    
    def body = (untilRes == 0 || mode in ['off', 'auto']) ?
        [ 'SystemMode': modeIndex, 'TimeUntil': null, 'Permanent': 'True' ] :
        [ 'SystemMode': modeIndex, 'TimeUntil': untilRes, 'Permanent': 'False' ]
    
    log.info "${app.label}: setThermostatMode(): System ID: ${systemId}, Mode: ${mode}, " +
             (untilRes == 0 ? "Permanent" : "Until: ${untilRes}")
    
    def requestParams = [
        uri   : atomicState.evohomeEndpoint,
        path  : "/WebAPI/emea/api/v1/temperatureControlSystem/${systemId}/mode", 
        body  : body,
        headers: atomicState.evohomeHeaders
    ]
    
    try {
        httpPutJson(requestParams) { resp ->
            if(resp.status == 201 && resp.data) {
                if (atomicState.debug) log.debug "${app.label}: setThermostatMode() Response: ${resp.data}"
                return null
            } else {
                log.error "${app.label}: setThermostatMode(): No Data. Response Status: ${resp.status}"
                return 'error'
            }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        log.error "${app.label}: setThermostatMode(): Error: ${e}"
        if (e.statusCode == 401) {
            atomicState.evohomeAuthFailed = true
        }
        return e
    }
}

def setHeatingSetpoint(zoneId, setpoint, until=-1) {
    if (atomicState.debug) log.debug "${app.label}: setHeatingSetpoint(): Zone ID: ${zoneId}, Setpoint: ${setpoint}, Until: ${until}"
    
    setpoint = formatTemperature(setpoint)
    def untilRes
    if ('permanent' == until || 0 == until || -1 == until) {
        untilRes = 0
    } else if (until instanceof Date) {
        untilRes = until.format("yyyy-MM-dd'T'HH:mm:00XX", TimeZone.getTimeZone('UTC'))
    } else if (until ==~ /\d+.*T.*/) {
        untilRes = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", until).format("yyyy-MM-dd'T'HH:mm:00XX", TimeZone.getTimeZone('UTC'))
    } else {
        log.warn "${device.label}: setHeatingSetpoint(): Could not parse 'until' value. Applying permanently."
        untilRes = 0
    }
    
    def body = (untilRes == 0) ?
        [ 'HeatSetpointValue': setpoint, 'SetpointMode': 1, 'TimeUntil': null ] :
        [ 'HeatSetpointValue': setpoint, 'SetpointMode': 2, 'TimeUntil': untilRes ]
    
    log.info "${app.label}: setHeatingSetpoint(): Zone ID: ${zoneId}, Setpoint: ${setpoint}, " +
             (untilRes == 0 ? "Permanent" : "Until: ${untilRes}")
    
    def requestParams = [
        uri   : atomicState.evohomeEndpoint,
        path  : "/WebAPI/emea/api/v1/temperatureZone/${zoneId}/heatSetpoint", 
        body  : body,
        headers: atomicState.evohomeHeaders
    ]
    
    try {
        httpPutJson(requestParams) { resp ->
            if(resp.status == 201 && resp.data) {
                if (atomicState.debug) log.debug "${app.label}: setHeatingSetpoint() Response: ${resp.data}"
                return null
            } else {
                log.error "${app.label}: setHeatingSetpoint(): No Data. Response Status: ${resp.status}"
                return 'error'
            }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        log.error "${app.label}: setHeatingSetpoint(): Error: ${e}"
        if (e.statusCode == 401) {
            atomicState.evohomeAuthFailed = true
        }
        return e
    }
}

def clearHeatingSetpoint(zoneId) {
    log.info "${app.label}: clearHeatingSetpoint(): Zone ID: ${zoneId}"
    
    def requestParams = [
        uri   : atomicState.evohomeEndpoint,
        path  : "/WebAPI/emea/api/v1/temperatureZone/${zoneId}/heatSetpoint", 
        body  : [ 'HeatSetpointValue': 0.0, 'SetpointMode': 0, 'TimeUntil': null ],
        headers: atomicState.evohomeHeaders
    ]
    
    try {
        httpPutJson(requestParams) { resp ->
            if(resp.status == 201 && resp.data) {
                if (atomicState.debug) log.debug "${app.label}: clearHeatingSetpoint() Response: ${resp.data}"
                return null
            } else {
                log.error "${app.label}: clearHeatingSetpoint(): No Data. Response Status: ${resp.status}"
                return 'error'
            }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        log.error "${app.label}: clearHeatingSetpoint(): Error: ${e}"
        if (e.statusCode == 401) {
            atomicState.evohomeAuthFailed = true
        }
        return e
    }
}

/**********************************************************************
 *  Helper Commands
 **********************************************************************/
private generateDni(locId, gatewayId, systemId, deviceId) {
    return 'Evohome.' + [ locId, gatewayId, systemId, deviceId ].join('.')
}

private formatTemperature(t) {
    return Float.parseFloat("${t}").round(1).toString()
}

private formatSetpointMode(mode) {
    switch (mode) {
        case 'FollowSchedule':
            return 'followSchedule'
        case 'PermanentOverride':
            return 'permanentOverride'
        case 'TemporaryOverride':
            return 'temporaryOverride'
        default:
            log.error "${app.label}: formatSetpointMode(): Unknown mode: ${mode}!"
            return mode.toLowerCase()
    }
}

private formatThermostatMode(mode) {
    switch (mode) {
        case 'Auto':
            return 'auto'
        case 'AutoWithEco':
            return 'economy'
        case 'Away':
            return 'away'
        case 'Custom':
            return 'custom'
        case 'DayOff':
            return 'dayOff'
        case 'HeatingOff':
            return 'off'
        default:
            log.error "${app.label}: formatThermostatMode(): Unknown mode: ${mode}!"
            return mode.toLowerCase()
    }
}

private getCurrentSwitchpoint(schedule) {
    if (atomicState.debug) log.debug "${app.label}: getCurrentSwitchpoint()"
    
    Calendar c = new GregorianCalendar()
    def todayName = c.getTime().format("EEEE", location.timeZone)
    def ScheduleToday = schedule.dailySchedules.find { it.dayOfWeek == todayName }
    
    ScheduleToday.switchpoints.sort { it.timeOfDay }
    ScheduleToday.switchpoints.reverse(true)
    def currentSwitchPoint = ScheduleToday.switchpoints.find { it.timeOfDay < c.getTime().format("HH:mm:ss", location.timeZone) }
    
    if (!currentSwitchPoint) {
        if (atomicState.debug) log.debug "${app.label}: No current switchpoint today; checking yesterday's schedule."
        c.add(Calendar.DATE, -1)
        def yesterdayName = c.getTime().format("EEEE", location.timeZone)
        def ScheduleYesterday = schedule.dailySchedules.find { it.dayOfWeek == yesterdayName }
        ScheduleYesterday.switchpoints.sort { it.timeOfDay }
        ScheduleYesterday.switchpoints.reverse(true)
        currentSwitchPoint = ScheduleYesterday.switchpoints[0]
    }
    
    def localDateStr = c.getTime().format("yyyy-MM-dd'T'", location.timeZone) + currentSwitchPoint.timeOfDay + c.getTime().format("XX", location.timeZone)
    def isoDateStr = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", localDateStr)
                      .format("yyyy-MM-dd'T'HH:mm:ssXX", TimeZone.getTimeZone('UTC'))
    currentSwitchPoint << [ 'time': isoDateStr ]
    if (atomicState.debug) log.debug "${app.label}: Current Switchpoint: ${currentSwitchPoint}"
    return currentSwitchPoint
}

private getNextSwitchpoint(schedule) {
    if (atomicState.debug) log.debug "${app.label}: getNextSwitchpoint()"
    
    Calendar c = new GregorianCalendar()
    def todayName = c.getTime().format("EEEE", location.timeZone)
    def ScheduleToday = schedule.dailySchedules.find { it.dayOfWeek == todayName }
    
    ScheduleToday.switchpoints.sort { it.timeOfDay }
    def nextSwitchPoint = ScheduleToday.switchpoints.find { it.timeOfDay > c.getTime().format("HH:mm:ss", location.timeZone) }
    
    if (!nextSwitchPoint) {
        if (atomicState.debug) log.debug "${app.label}: No further switchpoints today; checking tomorrow's schedule."
        c.add(Calendar.DATE, 1)
        def tmrwName = c.getTime().format("EEEE", location.timeZone)
        def ScheduleTmrw = schedule.dailySchedules.find { it.dayOfWeek == tmrwName }
        ScheduleTmrw.switchpoints.sort { it.timeOfDay }
        nextSwitchPoint = ScheduleTmrw.switchpoints[0]
    }
    
    def localDateStr = c.getTime().format("yyyy-MM-dd'T'", location.timeZone) + nextSwitchPoint.timeOfDay + c.getTime().format("XX", location.timeZone)
    def isoDateStr = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", localDateStr)
                      .format("yyyy-MM-dd'T'HH:mm:ssXX", TimeZone.getTimeZone('UTC'))
    nextSwitchPoint << [ 'time': isoDateStr ]
    if (atomicState.debug) log.debug "${app.label}: Next Switchpoint: ${nextSwitchPoint}"
    return nextSwitchPoint
}