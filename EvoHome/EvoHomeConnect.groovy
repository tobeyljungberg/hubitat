/**
 *  Copyright 2023 Tobey Ljungberg (tljungberg)
 *
 *  Name: Evohome (Connect)
 *
 *  Author: Tobey Ljungberg (tljungberg)
 *
 *  Date: 2016-04-05
 *
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
 *
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
		// New log level preference replaces the old debug mode.
		input "prefLogLevel", "enum", title: "Log Level", options: ["error", "warn", "info", "debug"], defaultValue: "info", displayDuringSetup: true
	}
}

/**********************************************************************
 *  Logging Helper Method
 **********************************************************************/
/**
 * logMessage(level, msg)
 *
 * Logs a message only if the given level is equal to or more severe than
 * the configured log level.
 *
 * Levels: error (1), warn (2), info (3), debug (4)
 */
private void logMessage(String level, String msg) {
    def levels = [ "error": 1, "warn": 2, "info": 3, "debug": 4 ]
    def configuredLevel = (settings.prefLogLevel ?: "info").toLowerCase()
    if (levels[level] <= levels[configuredLevel]) {
        switch(level) {
            case "error":
                log.error msg
                break
            case "warn":
                log.warn msg
                break
            case "info":
                log.info msg
                break
            case "debug":
                log.debug msg
                break
        }
    }
}

/**********************************************************************
 *  Setup and Configuration Commands:
 **********************************************************************/

def installed() {
	atomicState.installedAt = now()
	logMessage("debug", "${app.label}: Installed with settings: ${settings}")
}

def uninstalled() {
	if(getChildDevices()) {
		removeChildDevices(getChildDevices())
	}
}

void updated() {
        logMessage("debug", "${app.label}: Updating with settings: ${settings}")

        addInUseGlobalVar("EvohomeRequestPoll")
        addInUseGlobalVar("EvohomeLastPolled")
	
	// Evohome:
	atomicState.evohomeEndpoint = 'https://mytotalconnectcomfort.com/WebApi'
	atomicState.evohomeAuth = [tokenLifetimePercentThreshold : 50]
	atomicState.evohomeStatusPollInterval = settings.prefEvohomeStatusPollInterval
	atomicState.evohomeSchedulePollInterval = 60
	atomicState.evohomeUpdateRefreshTime = settings.prefEvohomeUpdateRefreshTime
	
	// Thermostat Mode Durations:
	atomicState.thermostatModeDuration = settings.prefThermostatModeDuration
	atomicState.thermostatEconomyDuration = settings.prefThermostatEconomyDuration
	
	// Force Authentication:
	authenticate()

	// Refresh Subscriptions and Schedules:
	manageSubscriptions()
	manageSchedules()
	
	// Refresh child device configuration:
	getEvohomeConfig()
	updateChildDeviceConfig()
	
	// Run a poll after a short delay:
	runIn(5, "poll")
}

/**********************************************************************
 *  Management Commands:
 **********************************************************************/

void manageSchedules() {
	logMessage("debug", "${app.label}: manageSchedules()")
	
	Random rand = new Random(now())
	def randomOffset = 0
	
	// manageAuth (every 5 mins):
	if (1==1) {
		logMessage("debug", "${app.label}: manageSchedules(): Re-scheduling manageAuth()")
		try {
			unschedule(manageAuth)
		}
		catch(e) { }
		randomOffset = rand.nextInt(60)
		schedule("${randomOffset} 0/5 * * * ?", "manageAuth")
	}
	
	// poll():
	if (1==1) {
		logMessage("debug", "${app.label}: manageSchedules(): Re-scheduling poll()")
		try {
			unschedule(poll)
		}
		catch(e) { }
		randomOffset = rand.nextInt(60)
		schedule("${randomOffset} 0/${settings.prefEvohomeStatusPollInterval} * * * ?", "poll")
	}
}

void manageSubscriptions() {
	logMessage("debug", "${app.label}: manageSubscriptions()")
	
	unsubscribe()
	subscribe(app, handleAppTouch)
    subscribe(location, "variable:EvohomeRequestPoll", globalVarHandler)
}

void manageAuth() {
	logMessage("debug", "${app.label}: manageAuth()")
	
	if (!atomicState.evohomeAuth?.authToken) {
		logMessage("info", "${app.label}: manageAuth(): No Auth Token. Authenticating...")
		authenticate()
	}
	else if (atomicState.evohomeAuthFailed) {
		logMessage("info", "${app.label}: manageAuth(): Auth has failed. Authenticating...")
		authenticate()
	}
	else if (!atomicState.evohomeAuth.expiresAt.toString().isNumber() || now() >= atomicState.evohomeAuth.expiresAt) {
		logMessage("info", "${app.label}: manageAuth(): Auth Token has expired. Authenticating...")
		authenticate()
	}
	else {
		def refreshAt = atomicState.evohomeAuth.expiresAt - ( 1000 * (atomicState.evohomeAuth.tokenLifetime * atomicState.evohomeAuth.tokenLifetimePercentThreshold / 100))
		if (now() >= refreshAt) {
			logMessage("info", "${app.label}: manageAuth(): Auth Token needs to be refreshed before it expires.")
			refreshAuthToken()
		}
		else {
			logMessage("info", "${app.label}: manageAuth(): Auth Token is okay.")
		}
	}
}

void poll(onlyZoneId=-1) {
	logMessage("debug", "${app.label}: poll(${onlyZoneId})")
	
	if (atomicState.evohomeAuthFailed) {
		manageAuth()
	}
	
	if (onlyZoneId == 0) {
		getEvohomeStatus()
		updateChildDevice()
	}
	else if (onlyZoneId != -1) {
		getEvohomeStatus(onlyZoneId)
		updateChildDevice(onlyZoneId)
	}
	else {
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

/**********************************************************************
 *  Event Handlers:
 **********************************************************************/

void handleAppTouch(evt) {
	logMessage("debug", "${app.label}: handleAppTouch()")
	poll()
}

void globalVarHandler(evt) {
    if (evt.value?.toString()?.toLowerCase() == "true") {
        logMessage("info", "${app.label}: globalVarHandler() - Global variable EvohomeRequestPoll is true. Triggering zone update.")
        poll(0)
        def currentTime = now()
        setGlobalVar("EvohomeLastPolled", currentTime)
        setGlobalVar("EvohomeRequestPoll", "false")
    }
}

/**********************************************************************
 *  SmartApp-Child Interface Commands:
 **********************************************************************/

void updateChildDeviceConfig() {
	logMessage("debug", "${app.label}: updateChildDeviceConfig()")
	
	def activeDnis = []
	
	atomicState.evohomeConfig.each { loc ->
		loc.gateways.each { gateway ->
			gateway.temperatureControlSystems.each { tcs ->
				tcs.zones.each { zone ->
					def dni = generateDni(loc.locationInfo.locationId, gateway.gatewayInfo.gatewayId, tcs.systemId, zone.zoneId )
					activeDnis << dni
					
                                        def values = [
                                                'logLevel': settings.prefLogLevel ?: "info",
                                                'updateRefreshTime': atomicState.evohomeUpdateRefreshTime,
						'minHeatingSetpoint': formatTemperature(zone?.heatSetpointCapabilities?.minHeatSetpoint),
						'maxHeatingSetpoint': formatTemperature(zone?.heatSetpointCapabilities?.maxHeatSetpoint),
						'temperatureResolution': zone?.heatSetpointCapabilities?.valueResolution,
						'windowFunctionTemperature': formatTemperature(settings.prefEvohomeWindowFuncTemp),
						'zoneType': zone?.zoneType,
						'locationId': loc.locationInfo.locationId,
						'gatewayId': gateway.gatewayInfo.gatewayId,
						'systemId': tcs.systemId,
						'zoneId': zone.zoneId
					]
					
					def d = getChildDevice(dni)
					if(!d) {
						try {
							values.put('label', "${zone.name} Heating Zone (Evohome)")
							logMessage("info", "${app.label}: updateChildDeviceConfig(): Creating device: Name: ${values.label}, DNI: ${dni}")
		                   	d = addChildDevice("tljungberg", "Evohome Heating Zone", dni, values)
						} catch (e) {
							logMessage("error", "${app.label}: updateChildDeviceConfig(): Error creating device: Name: ${values.label}, DNI: ${dni}, Error: ${e}")
						}
					} 
					
					if(d) {
						d.generateEvent(values)
					}
				}
			}
		}
	}
	
	logMessage("debug", "${app.label}: updateChildDeviceConfig(): Active DNIs: ${activeDnis}")
	
	def delete = getChildDevices().findAll { !activeDnis.contains(it.deviceNetworkId) }
	
	logMessage("debug", "${app.label}: updateChildDeviceConfig(): Found ${delete.size} devices to delete.")
	
	delete.each {
		logMessage("info", "${app.label}: updateChildDeviceConfig(): Deleting device with DNI: ${it.deviceNetworkId}")
		try {
			deleteChildDevice(it.deviceNetworkId)
		}
		catch(e) {
			logMessage("error", "${app.label}: updateChildDeviceConfig(): Error deleting device with DNI: ${it.deviceNetworkId}. Error: ${e}")
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
						if(d) {
							def schedule = atomicState.evohomeSchedules.find { it.dni == dni}
							def currSw = getCurrentSwitchpoint(schedule.schedule)
							def nextSw = getNextSwitchpoint(schedule.schedule)
	
							def values = [
								'temperature': formatTemperature(zone?.temperatureStatus?.temperature),
								'heatingSetpoint': formatTemperature(zone?.heatSetpointStatus?.targetTemperature),
								'thermostatSetpoint': formatTemperature(zone?.heatSetpointStatus?.targetTemperature),
								'thermostatSetpointMode': formatSetpointMode(zone?.heatSetpointStatus?.setpointMode),
								'thermostatSetpointUntil': zone?.heatSetpointStatus?.until,
								'thermostatMode': formatThermostatMode(tcs?.systemModeStatus?.mode),
								'scheduledSetpoint': formatTemperature(currSw.temperature),
								'nextScheduledSetpoint': formatTemperature(nextSw.temperature),
								'nextScheduledTime': nextSw.time
							]
							logMessage("debug", "${app.label}: updateChildDevice(): Updating Device with DNI: ${dni} with data: ${values}")
							d.generateEvent(values)
						} else {
							logMessage("debug", "${app.label}: updateChildDevice(): Device with DNI: ${dni} does not exist, so skipping status update.")
						}
					}
				}
			}
		}
	}
}

/**********************************************************************
 *  Evohome API Commands:
 **********************************************************************/

private authenticate() {
	logMessage("debug", "${app.label}: authenticate()")
	
	def requestParams = [
		uri: 'https://mytotalconnectcomfort.com/WebApi',
		path: '/Auth/OAuth/Token',
		headers: [
			'Authorization': 'Basic YjAxM2FhMjYtOTcyNC00ZGJkLTg4OTctMDQ4YjlhYWRhMjQ5OnRlc3Q=',
			'Accept': 'application/json, application/xml, text/json, text/x-json, text/javascript, text/xml',
			'Content-Type':	'application/x-www-form-urlencoded; charset=utf-8'
		],
		body: [
			'grant_type':	'password',
			'scope':	'EMEA-V1-Basic EMEA-V1-Anonymous EMEA-V1-Get-Current-User-Account EMEA-V1-Get-Location-Installation-Info-By-UserId',
			'Username':	settings.prefEvohomeUsername,
			'Password':	settings.prefEvohomePassword
		]
	]

	try {
		httpPost(requestParams) { resp ->
			if(resp.status == 200 && resp.data) {
				def tmpAuth = atomicState.evohomeAuth ?: [:]
				tmpAuth.put('lastUpdated' , now())
				tmpAuth.put('authToken' , resp?.data?.access_token)
				tmpAuth.put('tokenLifetime' , resp?.data?.expires_in.toInteger() ?: 0)
				tmpAuth.put('expiresAt' , now() + (tmpAuth.tokenLifetime * 1000))
				tmpAuth.put('refreshToken' , resp?.data?.refresh_token)
				atomicState.evohomeAuth = tmpAuth
				atomicState.evohomeAuthFailed = false
				
				logMessage("debug", "${app.label}: authenticate(): New evohomeAuth: ${atomicState.evohomeAuth}")
				def exp = new Date(tmpAuth.expiresAt)
				logMessage("info", "${app.label}: authenticate(): New Auth Token Expires At: ${exp}")
				
				def tmpHeaders = atomicState.evohomeHeaders ?: [:]
				tmpHeaders.put('Authorization',"bearer ${atomicState.evohomeAuth.authToken}")
				tmpHeaders.put('applicationId', 'b013aa26-9724-4dbd-8897-048b9aada249')
				tmpHeaders.put('Accept', 'application/json, application/xml, text/json, text/x-json, text/javascript, text/xml')
				atomicState.evohomeHeaders = tmpHeaders
				
				logMessage("debug", "${app.label}: authenticate(): New evohomeHeaders: ${atomicState.evohomeHeaders}")
				
				getEvohomeUserAccount()
			}
			else {
				logMessage("error", "${app.label}: authenticate(): No Data. Response Status: ${resp.status}")
				atomicState.evohomeAuthFailed = true
			}
		}
	} catch (groovyx.net.http.HttpResponseException e) {
		logMessage("error", "${app.label}: authenticate(): Error: e.statusCode ${e.statusCode}")
		atomicState.evohomeAuthFailed = true
	}
}

private refreshAuthToken() {
	logMessage("debug", "${app.label}: refreshAuthToken()")
	
	def requestParams = [
		uri: 'https://mytotalconnectcomfort.com/WebApi',
		path: '/Auth/OAuth/Token',
		headers: [
			'Authorization': 'Basic YjAxM2FhMjYtOTcyNC00ZGJkLTg4OTctMDQ4YjlhYWRhMjQ5OnRlc3Q=',
			'Accept': 'application/json, application/xml, text/json, text/x-json, text/javascript, text/xml',
			'Content-Type':	'application/x-www-form-urlencoded; charset=utf-8'
		],
		body: [
			'grant_type':	'refresh_token',
			'scope':	'EMEA-V1-Basic EMEA-V1-Anonymous EMEA-V1-Get-Current-User-Account EMEA-V1-Get-Location-Installation-Info-By-UserId',
			'refresh_token':	atomicState.evohomeAuth.refreshToken
		]
	]

	try {
		httpPost(requestParams) { resp ->
			if(resp.status == 200 && resp.data) {
				def tmpAuth = atomicState.evohomeAuth ?: [:]
				tmpAuth.put('lastUpdated' , now())
				tmpAuth.put('authToken' , resp?.data?.access_token)
				tmpAuth.put('tokenLifetime' , resp?.data?.expires_in.toInteger() ?: 0)
				tmpAuth.put('expiresAt' , now() + (tmpAuth.tokenLifetime * 1000))
				tmpAuth.put('refreshToken' , resp?.data?.refresh_token)
				atomicState.evohomeAuth = tmpAuth
				atomicState.evohomeAuthFailed = false
				
				logMessage("debug", "${app.label}: refreshAuthToken(): New evohomeAuth: ${atomicState.evohomeAuth}")
				def exp = new Date(tmpAuth.expiresAt)
				logMessage("info", "${app.label}: refreshAuthToken(): New Auth Token Expires At: ${exp}")
				
				def tmpHeaders = atomicState.evohomeHeaders ?: [:]
				tmpHeaders.put('Authorization',"bearer ${atomicState.evohomeAuth.authToken}")
				tmpHeaders.put('applicationId', 'b013aa26-9724-4dbd-8897-048b9aada249')
				tmpHeaders.put('Accept', 'application/json, application/xml, text/json, text/x-json, text/javascript, text/xml')
				atomicState.evohomeHeaders = tmpHeaders
				
				logMessage("debug", "${app.label}: refreshAuthToken(): New evohomeHeaders: ${atomicState.evohomeHeaders}")
				
				getEvohomeUserAccount()
			}
			else {
				logMessage("error", "${app.label}: refreshAuthToken(): No Data. Response Status: ${resp.status}")
			}
		}
	} catch (groovyx.net.http.HttpResponseException e) {
		logMessage("error", "${app.label}: refreshAuthToken(): Error: e.statusCode ${e.statusCode}")
		if (e.statusCode == 401) {
			atomicState.evohomeAuthFailed = true
			authenticate()
		}
	}
}

private getEvohomeUserAccount() {
	logMessage("info", "${app.label}: getEvohomeUserAccount(): Getting user account information.")
	
	def requestParams = [
		uri: atomicState.evohomeEndpoint,
		path: '/WebAPI/emea/api/v1/userAccount',
		headers: atomicState.evohomeHeaders
	]

	try {
		httpGet(requestParams) { resp ->
			if (resp.status == 200 && resp.data) {
				atomicState.evohomeUserAccount = resp.data
				logMessage("debug", "${app.label}: getEvohomeUserAccount(): Data: ${atomicState.evohomeUserAccount}")
			}
			else {
				logMessage("error", "${app.label}: getEvohomeUserAccount(): No Data. Response Status: ${resp.status}")
			}
		}
	} catch (groovyx.net.http.HttpResponseException e) {
		logMessage("error", "${app.label}: getEvohomeUserAccount(): Error: e.statusCode ${e.statusCode}")
		if (e.statusCode == 401) {
			atomicState.evohomeAuthFailed = true
		}
	}
}

private getEvohomeConfig() {
	logMessage("info", "${app.label}: getEvohomeConfig(): Getting configuration for all locations.")
			
	def requestParams = [
		uri: atomicState.evohomeEndpoint,
		path: '/WebAPI/emea/api/v1/location/installationInfo',
		query: [
			'userId': atomicState.evohomeUserAccount.userId,
			'includeTemperatureControlSystems': 'True'
		],
		headers: atomicState.evohomeHeaders
	]

	try {
		httpGet(requestParams) { resp ->
			if (resp.status == 200 && resp.data) {
				logMessage("debug", "${app.label}: getEvohomeConfig(): Data: ${resp.data}")
				atomicState.evohomeConfig = resp.data
				atomicState.evohomeConfigUpdatedAt = now()
				return null
			}
			else {
				logMessage("error", "${app.label}: getEvohomeConfig(): No Data. Response Status: ${resp.status}")
				return 'error'
			}
		}
	} catch (groovyx.net.http.HttpResponseException e) {
		logMessage("error", "${app.label}: getEvohomeConfig(): Error: e.statusCode ${e.statusCode}")
		if (e.statusCode == 401) {
			atomicState.evohomeAuthFailed = true
		}
		return e
	}
}

private getEvohomeStatus(onlyZoneId=-1) {
	logMessage("debug", "${app.label}: getEvohomeStatus(${onlyZoneId})")
	
	def newEvohomeStatus = []
	
	if (onlyZoneId == -1) {
		logMessage("info", "${app.label}: getEvohomeStatus(): Getting status for all zones.")
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
	}
	else {
		logMessage("info", "${app.label}: getEvohomeStatus(): Getting status for zone ID: ${onlyZoneId}")
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
	logMessage("debug", "${app.label}: getEvohomeLocationStatus: Location ID: ${locationId}")
	
	def requestParams = [
		uri: atomicState.evohomeEndpoint,
		path: "/WebAPI/emea/api/v1/location/${locationId}/status", 
		query: [ 'includeTemperatureControlSystems': 'True'],
		headers: atomicState.evohomeHeaders
	]

	try {
		httpGet(requestParams) { resp ->
			if(resp.status == 200 && resp.data) {
				logMessage("debug", "${app.label}: getEvohomeLocationStatus: Data: ${resp.data}")
				return resp.data
			}
			else {
				logMessage("error", "${app.label}: getEvohomeLocationStatus:  No Data. Response Status: ${resp.status}")
				return false
			}
		}
	} catch (groovyx.net.http.HttpResponseException e) {
		logMessage("error", "${app.label}: getEvohomeLocationStatus: Error: e.statusCode ${e.statusCode}")
		if (e.statusCode == 401) {
			atomicState.evohomeAuthFailed = true
		}
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
				logMessage("debug", "${app.label}: getEvohomeZoneStatus: Data: ${resp.data}")
				return resp.data
			}
			else {
				logMessage("error", "${app.label}: getEvohomeZoneStatus:  No Data. Response Status: ${resp.status}")
				return false
			}
		}
	} catch (groovyx.net.http.HttpResponseException e) {
		logMessage("error", "${app.label}: getEvohomeZoneStatus: Error: e.statusCode ${e.statusCode}")
		if (e.statusCode == 401) {
			atomicState.evohomeAuthFailed = true
		}
		return false
	}
}

private getEvohomeSchedules() {
	logMessage("info", "${app.label}: getEvohomeSchedules(): Getting schedules for all zones.")
			
	def evohomeSchedules = []
		
	atomicState.evohomeConfig.each { loc ->
		loc.gateways.each { gateway ->
			gateway.temperatureControlSystems.each { tcs ->
				tcs.zones.each { zone ->
					def dni = generateDni(loc.locationInfo.locationId, gateway.gatewayInfo.gatewayId, tcs.systemId, zone.zoneId )
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
	logMessage("debug", "${app.label}: getEvohomeZoneSchedule(${zoneId})")
	
	def requestParams = [
		uri: atomicState.evohomeEndpoint,
		path: "/WebAPI/emea/api/v1/temperatureZone/${zoneId}/schedule",
		headers: atomicState.evohomeHeaders
	]

	try {
		httpGet(requestParams) { resp ->
			if(resp.status == 200 && resp.data) {
				logMessage("debug", "${app.label}: getEvohomeZoneSchedule: Data: ${resp.data}")
				return resp.data
			}
			else {
				logMessage("error", "${app.label}: getEvohomeZoneSchedule:  No Data. Response Status: ${resp.status}")
				return false
			}
		}
	} catch (groovyx.net.http.HttpResponseException e) {
		logMessage("error", "${app.label}: getEvohomeZoneSchedule: Error: e.statusCode ${e.statusCode}")
		if (e.statusCode == 401) {
			atomicState.evohomeAuthFailed = true
		}
		return false
	}
}

def setThermostatMode(systemId, mode, until=-1) {
	logMessage("debug", "${app.label}: setThermostatMode(): SystemID: ${systemId}, Mode: ${mode}, Until: ${until}")
	
	mode = mode.toLowerCase()
	int modeIndex
	switch (mode) {
		case 'auto':
			modeIndex = 0
			break
		case 'off':
			modeIndex = 1
			break
		case 'economy':
			modeIndex = 2
			break
		case 'away':
			modeIndex = 3
			break
		case 'dayoff':
			modeIndex = 4
			break
		case 'custom':
			modeIndex = 6
			break
		default:
			logMessage("error", "${app.label}: setThermostatMode(): Mode: ${mode} is not supported!")
			modeIndex = 999
			break
	}
	
	def untilRes
	if (-1 == until && 'economy' == mode) { 
		until = atomicState.thermostatEconomyDuration ?: 0
	}
	else if (-1 == until && ( 'away' == mode ||'dayoff' == mode ||'custom' == mode )) {
		until = atomicState.thermostatModeDuration ?: 0
	}
	
	if ('permanent' == until || 0 == until || -1 == until) {
		untilRes = 0
	}
	else if (until instanceof Date) {
		untilRes = until.format("yyyy-MM-dd'T'HH:mm:00XX", TimeZone.getTimeZone('UTC'))
	}
	else if (until ==~ /\d+.*T.*/) {
		untilRes = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", until).format("yyyy-MM-dd'T'HH:mm:00XX", TimeZone.getTimeZone('UTC'))
	}
	else if (until.isNumber() && 'economy' == mode) {
		untilRes = new Date( now() + (Math.round(until) * 3600000) ).format("yyyy-MM-dd'T'HH:mm:00XX", TimeZone.getTimeZone('UTC'))
	}
	else if (until.isNumber() && ('away' == mode ||'dayoff' == mode ||'custom' == mode )) {
		untilRes = new Date( now() + (Math.round(until) * 86400000) ).format("yyyy-MM-dd'T'00:00:00XX", location.timeZone)
	}
	else {
		logMessage("warn", "${device.label}: setThermostatMode(): until value could not be parsed. Mode will be applied permanently.")
		untilRes = 0
	}
	
	if (0 != untilRes && ('away' == mode ||'dayoff' == mode ||'custom' == mode )) { 
		untilRes = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", untilRes).format("yyyy-MM-dd'T'00:00:00XX", location.timeZone) ).format("yyyy-MM-dd'T'HH:mm:00XX", TimeZone.getTimeZone('UTC'))
	}
	
	def body
	if (0 == untilRes || 'off' == mode || 'auto' == mode) {
		body = ['SystemMode': modeIndex, 'TimeUntil': null, 'Permanent': 'True']
		logMessage("info", "${app.label}: setThermostatMode(): System ID: ${systemId}, Mode: ${mode}, Permanent: True")
	}
	else {
		body = ['SystemMode': modeIndex, 'TimeUntil': untilRes, 'Permanent': 'False']
		logMessage("info", "${app.label}: setThermostatMode(): System ID: ${systemId}, Mode: ${mode}, Permanent: False, Until: ${untilRes}")
	}
	
	def requestParams = [
		'uri': atomicState.evohomeEndpoint,
		'path': "/WebAPI/emea/api/v1/temperatureControlSystem/${systemId}/mode", 
		'body': body,
		'headers': atomicState.evohomeHeaders
	]
	
	try {
		httpPutJson(requestParams) { resp ->
			if(resp.status == 201 && resp.data) {
				logMessage("debug", "${app.label}: setThermostatMode(): Response: ${resp.data}")
				return null
			}
			else {
				logMessage("error", "${app.label}: setThermostatMode():  No Data. Response Status: ${resp.status}")
				return 'error'
			}
		}
	} catch (groovyx.net.http.HttpResponseException e) {
		logMessage("error", "${app.label}: setThermostatMode(): Error: ${e}")
		if (e.statusCode == 401) {
			atomicState.evohomeAuthFailed = true
		}
		return e
	}
}

def setHeatingSetpoint(zoneId, setpoint, until=-1) {
	logMessage("debug", "${app.label}: setHeatingSetpoint(): Zone ID: ${zoneId}, Setpoint: ${setpoint}, Until: ${until}")
	
	setpoint = formatTemperature(setpoint)
	
	def untilRes
	if ('permanent' == until || 0 == until || -1 == until) {
		untilRes = 0
	}
	else if (until instanceof Date) {
		untilRes = until.format("yyyy-MM-dd'T'HH:mm:00XX", TimeZone.getTimeZone('UTC'))
	}
	else if (until ==~ /\d+.*T.*/) {
		untilRes = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", until).format("yyyy-MM-dd'T'HH:mm:00XX", TimeZone.getTimeZone('UTC'))
	}
	else {
		logMessage("warn", "${device.label}: setHeatingSetpoint(): until value could not be parsed. Setpoint will be applied permanently.")
		untilRes = 0
	}
	
	def body
	if (0 == untilRes) {
		body = ['HeatSetpointValue': setpoint, 'SetpointMode': 1, 'TimeUntil': null]
		logMessage("info", "${app.label}: setHeatingSetpoint(): Zone ID: ${zoneId}, Setpoint: ${setpoint}, Until: Permanent")
	}
	else {
		body = ['HeatSetpointValue': setpoint, 'SetpointMode': 2, 'TimeUntil': untilRes]
		logMessage("info", "${app.label}: setHeatingSetpoint(): Zone ID: ${zoneId}, Setpoint: ${setpoint}, Until: ${untilRes}")
	}
	
	def requestParams = [
		'uri': atomicState.evohomeEndpoint,
		'path': "/WebAPI/emea/api/v1/temperatureZone/${zoneId}/heatSetpoint", 
		'body': body,
		'headers': atomicState.evohomeHeaders
	]
	
	try {
		httpPutJson(requestParams) { resp ->
			if(resp.status == 201 && resp.data) {
				logMessage("debug", "${app.label}: setHeatingSetpoint(): Response: ${resp.data}")
				return null
			}
			else {
				logMessage("error", "${app.label}: setHeatingSetpoint():  No Data. Response Status: ${resp.status}")
				return 'error'
			}
		}
	} catch (groovyx.net.http.HttpResponseException e) {
		logMessage("error", "${app.label}: setHeatingSetpoint(): Error: ${e}")
		if (e.statusCode == 401) {
			atomicState.evohomeAuthFailed = true
		}
		return e
	}
}

def clearHeatingSetpoint(zoneId) {
	logMessage("info", "${app.label}: clearHeatingSetpoint(): Zone ID: ${zoneId}")
	
	def requestParams = [
		'uri': atomicState.evohomeEndpoint,
		'path': "/WebAPI/emea/api/v1/temperatureZone/${zoneId}/heatSetpoint", 
		'body': ['HeatSetpointValue': 0.0, 'SetpointMode': 0, 'TimeUntil': null],
		'headers': atomicState.evohomeHeaders
	]
	
	try {
		httpPutJson(requestParams) { resp ->
			if(resp.status == 201 && resp.data) {
				logMessage("debug", "${app.label}: clearHeatingSetpoint(): Response: ${resp.data}")
				return null
			}
			else {
				logMessage("error", "${app.label}: clearHeatingSetpoint():  No Data. Response Status: ${resp.status}")
				return 'error'
			}
		}
	} catch (groovyx.net.http.HttpResponseException e) {
		logMessage("error", "${app.label}: clearHeatingSetpoint(): Error: ${e}")
		if (e.statusCode == 401) {
			atomicState.evohomeAuthFailed = true
		}
		return e
	}
}

/**********************************************************************
 *  Helper Commands:
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
			mode = 'followSchedule'
			break
		case 'PermanentOverride':
			mode = 'permanentOverride'
			break
		case 'TemporaryOverride':
			mode = 'temporaryOverride'
			break
		default:
			logMessage("error", "${app.label}: formatSetpointMode(): Mode: ${mode} unknown!")
			mode = mode.toLowerCase()
			break
	}
	return mode
}
 
private formatThermostatMode(mode) {
	switch (mode) {
		case 'Auto':
			mode = 'auto'
			break
		case 'AutoWithEco':
			mode = 'economy'
			break
		case 'Away':
			mode = 'away'
			break
		case 'Custom':
			mode = 'custom'
			break
		case 'DayOff':
			mode = 'dayOff'
			break
		case 'HeatingOff':
			mode = 'off'
			break
		default:
			logMessage("error", "${app.label}: formatThermostatMode(): Mode: ${mode} unknown!")
			mode = mode.toLowerCase()
			break
	}
	return mode
}
  
private getCurrentSwitchpoint(schedule) {
	logMessage("debug", "${app.label}: getCurrentSwitchpoint()")
	
	Calendar c = new GregorianCalendar()
	def ScheduleToday = schedule.dailySchedules.find { it.dayOfWeek == c.getTime().format("EEEE", location.timeZone) }
	
	ScheduleToday.switchpoints.sort {it.timeOfDay}
	ScheduleToday.switchpoints.reverse(true)
	def currentSwitchPoint = ScheduleToday.switchpoints.find {it.timeOfDay < c.getTime().format("HH:mm:ss", location.timeZone)}
	
	if (!currentSwitchPoint) {
		logMessage("debug", "${app.label}: getCurrentSwitchpoint(): No current switchpoints today, so must look to yesterday's schedule.")
		c.add(Calendar.DATE, -1 )
		def ScheduleYesterday = schedule.dailySchedules.find { it.dayOfWeek == c.getTime().format("EEEE", location.timeZone) }
		ScheduleYesterday.switchpoints.sort {it.timeOfDay}
		ScheduleYesterday.switchpoints.reverse(true)
		currentSwitchPoint = ScheduleYesterday.switchpoints[0]
	}
	
	def localDateStr = c.getTime().format("yyyy-MM-dd'T'", location.timeZone) + currentSwitchPoint.timeOfDay + c.getTime().format("XX", location.timeZone)
	def isoDateStr = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", localDateStr).format("yyyy-MM-dd'T'HH:mm:ssXX", TimeZone.getTimeZone('UTC'))
	currentSwitchPoint << [ 'time': isoDateStr ]
	logMessage("debug", "${app.label}: getCurrentSwitchpoint(): Current Switchpoint: ${currentSwitchPoint}")
	
	return currentSwitchPoint
}
 
private getNextSwitchpoint(schedule) {
	logMessage("debug", "${app.label}: getNextSwitchpoint()")
	
	Calendar c = new GregorianCalendar()
	def ScheduleToday = schedule.dailySchedules.find { it.dayOfWeek == c.getTime().format("EEEE", location.timeZone) }
	
	ScheduleToday.switchpoints.sort {it.timeOfDay}
	def nextSwitchPoint = ScheduleToday.switchpoints.find {it.timeOfDay > c.getTime().format("HH:mm:ss", location.timeZone)}
	
	if (!nextSwitchPoint) {
		logMessage("debug", "${app.label}: getNextSwitchpoint(): No more switchpoints today, so must look to tomorrow's schedule.")
		c.add(Calendar.DATE, 1 )
		def ScheduleTmrw = schedule.dailySchedules.find { it.dayOfWeek == c.getTime().format("EEEE", location.timeZone) }
		ScheduleTmrw.switchpoints.sort {it.timeOfDay}
		nextSwitchPoint = ScheduleTmrw.switchpoints[0]
	}
	
	def localDateStr = c.getTime().format("yyyy-MM-dd'T'", location.timeZone) + nextSwitchPoint.timeOfDay + c.getTime().format("XX", location.timeZone)
	def isoDateStr = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", localDateStr).format("yyyy-MM-dd'T'HH:mm:ssXX", TimeZone.getTimeZone('UTC'))
	nextSwitchPoint << [ 'time': isoDateStr ]
	logMessage("debug", "${app.label}: getNextSwitchpoint(): Next Switchpoint: ${nextSwitchPoint}")
	
	return nextSwitchPoint
}
