/**
 *  Copyright 2016 Tobey Ljungberg (tljungberg)
 *
 *  Name: Evohome Heating Zone (Refactored)
 *
 *  Description:
 *   - Child device for the Evohome (Connect) SmartApp.
 *   - For latest documentation see: https://github.com/tljungberg/SmartThings
 *
 *  License:
 *   Apache License, Version 2.0
 */

metadata {
    definition (name: "Evohome Heating Zone", namespace: "tljungberg", author: "Tobey Ljungberg") {
        capability "Actuator"
        capability "Sensor"
        capability "Refresh"
        capability "Temperature Measurement"
        capability "Thermostat Heating Setpoint"
        capability "Thermostat Setpoint"
        capability "Thermostat Mode"
        capability "Thermostat Operating State"

        // Commands
        command "refresh"
        command "setHeatingSetpoint"
        command "raiseSetpoint"
        command "lowerSetpoint"
        command "setThermostatMode"
        command "cycleThermostatMode"
        command "off"
        command "heat"
        command "auto"
        command "away"
        command "economy"
        command "dayOff"
        command "custom"
        command "resume"
        command "boost"
        command "suppress"
        command "generateEvent"
        command "test"

        // Attributes
        attribute "temperature", "number"
        attribute "heatingSetpoint", "number"
        attribute "thermostatSetpoint", "number"
        attribute "thermostatSetpointMode", "string"
        attribute "thermostatSetpointUntil", "string"
        attribute "thermostatSetpointStatus", "string"
        attribute "thermostatMode", "string"
        attribute "thermostatOperatingState", "string"
        attribute "thermostatStatus", "string"
        attribute "scheduledSetpoint", "number"
        attribute "nextScheduledSetpoint", "number"
        attribute "nextScheduledTime", "string"
        attribute "optimisation", "string"
        attribute "windowFunction", "string"
    }

    tiles(scale: 2) {
        multiAttributeTile(name:"multi", type:"thermostat", width:6, height:4) {
            tileAttribute("device.temperature", key: "PRIMARY_CONTROL") {
                attributeState("default", label:'${currentValue}°', unit:"C")
            }
            tileAttribute("device.thermostatStatus", key: "OPERATING_STATE") {
                attributeState("Heating", backgroundColor:"#ffa81e", defaultState: true)
                attributeState("Idle (Auto)", backgroundColor:"#44b621")
                attributeState("Idle (Custom)", backgroundColor:"#44b621")
                attributeState("Idle (Day Off)", backgroundColor:"#44b621")
                attributeState("Idle (Economy)", backgroundColor:"#44b621")
                attributeState("Idle (Away)", backgroundColor:"#44b621")
                attributeState("Off", backgroundColor:"#269bd2")
            }
        }
    
        valueTile("temperature", "device.temperature", width: 2, height: 2, canChangeIcon: true) {
            state("temperature", label:'${currentValue}°', unit:"C", icon:"st.Weather.weather2",
                backgroundColors:[
                    [value: 0, color: "#153591"],
                    [value: 7, color: "#1e9cbb"],
                    [value: 15, color: "#90d2a7"],
                    [value: 23, color: "#44b621"],
                    [value: 28, color: "#f1d801"],
                    [value: 35, color: "#d04e00"],
                    [value: 37, color: "#bc2323"]
                ]
            )
        }
        
        // Setpoint tiles
        valueTile("thermostatSetpoint", "device.thermostatSetpoint", width: 3, height: 1) {
            state "thermostatSetpoint", label:'Setpoint: ${currentValue}°', unit:"C"
        }
        valueTile("thermostatSetpointStatus", "device.thermostatSetpointStatus", width: 3, height: 1, decoration: "flat") {
            state "thermostatSetpointStatus", label:'${currentValue}', backgroundColor:"#ffffff"
        }
        standardTile("raiseSetpoint", "device.thermostatSetpoint", width: 1, height: 1, decoration: "flat") {
            state "setpoint", action:"raiseSetpoint", icon:"st.thermostat.thermostat-up"
        }
        standardTile("lowerSetpoint", "device.thermostatSetpoint", width: 1, height: 1, decoration: "flat") {
            state "setpoint", action:"lowerSetpoint", icon:"st.thermostat.thermostat-down"
        }
        standardTile("resume", "device.resume", width: 1, height: 1, decoration: "flat") {
            state "default", action:"resume", label:'Resume', icon:"st.samsung.da.oven_ic_send"
        }
        standardTile("boost", "device.boost", inactiveLabel: false, decoration: "flat", width: 1, height: 1) {
            state "default", action:"boost", label:'Boost'
        }
        standardTile("suppress", "device.suppress", inactiveLabel: false, decoration: "flat", width: 1, height: 1) {
            state "default", action:"suppress", label:'Suppress'
        }
        
        valueTile("thermostatStatus", "device.thermostatStatus", height: 1, width: 6, decoration: "flat") {
            state "thermostatStatus", label:'${currentValue}', backgroundColor:"#ffffff"
        }
        // Mode tiles
        standardTile("auto", "device.auto", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", action:"auto", icon: "st.thermostat.auto"
        }
        standardTile("away", "device.away", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", action:"away", label:'Away'
        }
        standardTile("custom", "device.custom", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", action:"custom", label:'Custom'
        }
        standardTile("dayOff", "device.dayOff", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", action:"dayOff", label:'Day Off'
        }
        standardTile("economy", "device.economy", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", action:"economy", label:'Economy'
        }
        standardTile("off", "device.off", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", action:"off", icon:"st.thermostat.heating-cooling-off"
        }
        standardTile("refresh", "device.thermostatMode", inactiveLabel: false, decoration: "flat") {
            state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        standardTile("test", "device.test", width: 1, height: 1, decoration: "flat") {
            state "default", label:'Test', action:"test"
        }
        
        main "temperature"
        details([
            "multi",
            "thermostatSetpoint", "raiseSetpoint", "boost", "resume",
            "thermostatSetpointStatus", "lowerSetpoint", "suppress", "refresh",
            "auto", "away", "custom", "dayOff", "economy", "off"
        ])
    }

    preferences {
        section("Log Level Settings") {
            input "prefLogLevel", "enum", title: "Log Level", 
                  options: ["error", "warn", "info", "debug"], defaultValue: "info", 
                  required: true, displayDuringSetup: true
        }
        section("Setpoint Adjustments") {
            input title: "Setpoint Duration", description: "Configure how long setpoint adjustments are applied for.", 
                  displayDuringSetup: true, type: "paragraph", element: "paragraph"
            input 'prefSetpointMode', 'enum', title: 'Until', options: ["Next Switchpoint", "Midday", "Midnight", "Duration", "Permanent"],
                  defaultValue: "Next Switchpoint", required: true, displayDuringSetup: true
            input 'prefSetpointDuration', 'number', title: 'Duration (minutes)', description: 'Apply setpoint for this many minutes', 
                  range: "1..1440", defaultValue: 60, required: true, displayDuringSetup: true
            input title: "Setpoint Temperatures", description: "Configure preset temperatures for the 'Boost' and 'Suppress' buttons.", 
                  displayDuringSetup: true, type: "paragraph", element: "paragraph"
            input "prefBoostTemperature", "string", title: "'Boost' Temperature", defaultValue: "21.5", 
                  required: true, displayDuringSetup: true
            input "prefSuppressTemperature", "string", title: "'Suppress' Temperature", defaultValue: "15.0", 
                  required: true, displayDuringSetup: true
        }
    }
}

/******************************************************************************
 *  Logging Helper
 ******************************************************************************/
private void logMessage(String level, String msg) {
    def levels = [ "error": 1, "warn": 2, "info": 3, "debug": 4 ]
    def configuredLevel = (state.logLevel ?: settings.prefLogLevel ?: "info").toLowerCase()
    if (levels[level] <= levels[configuredLevel]) {
        switch(level) {
            case "error": log.error msg; break
            case "warn":  log.warn msg; break
            case "info":  log.info msg; break
            case "debug": log.debug msg; break
        }
    }
}

/******************************************************************************
 *  Test Commands
 ******************************************************************************/
def test() {
    logMessage("debug", "${device.label}: test() - Properties: ${properties}")
    logMessage("debug", "${device.label}: test() - Settings: ${settings}")
    logMessage("debug", "${device.label}: test() - State: ${state}")
}

/******************************************************************************
 *  Setup & Configuration
 ******************************************************************************/
def installed() {
    logMessage("debug", "${device.label}: Installed with settings: ${settings}")
    state.installedAt = now()

    // Default zone parameters – will be updated by parent shortly.
    state.debug = false
    state.updateRefreshTime = 5 // seconds to wait after an update before polling.
    state.zoneType = 'RadiatorZone'
    state.minHeatingSetpoint = formatTemperature(5.0)
    state.maxHeatingSetpoint = formatTemperature(35.0)
    state.temperatureResolution = formatTemperature(0.5)
    state.windowFunctionTemperature = formatTemperature(5.0)
    state.targetSetpoint = state.minHeatingSetpoint

    // Populate state with default preference values.
    state.setpointMode = getInputDefaultValue('prefSetpointMode')
    state.setpointDuration = getInputDefaultValue('prefSetpointDuration')
    state.boostTemperature = getInputDefaultValue('prefBoostTemperature')
    state.suppressTemperature = getInputDefaultValue('prefSuppressTemperature')
    
    state.logLevel = settings.prefLogLevel ?: "info"
}

def updated() {
    logMessage("debug", "${device.label}: Updating with settings: ${settings}")
    state.setpointMode = settings.prefSetpointMode
    state.setpointDuration = settings.prefSetpointDuration
    state.boostTemperature = formatTemperature(settings.prefBoostTemperature)
    state.suppressTemperature = formatTemperature(settings.prefSuppressTemperature)
    state.logLevel = settings.prefLogLevel ?: "info"
}

/******************************************************************************
 *  SmartApp-Child Interface Commands
 ******************************************************************************/
void generateEvent(values) {
    logMessage("info", "${device.label}: generateEvent() - New values: ${values}")
    if (values) {
        values.each { name, value ->
            // Save common configuration values into state.
            if (name in ['minHeatingSetpoint', 'maxHeatingSetpoint', 'temperatureResolution', 'windowFunctionTemperature',
                         'zoneType', 'locationId', 'gatewayId', 'systemId', 'zoneId', 'schedule', 'debug', 'updateRefreshTime']) {
                state."${name}" = value
            }
            else {
                // Only send events if name and value are not null.
                sendEvent(name: name, value: value, displayed: (name && value) ? true : false)
                if (name == 'heatingSetpoint') {
                    state.targetSetpoint = value
                }
            }
        }
    }
    calculateThermostatOperatingState()
    calculateOptimisations()
    calculateThermostatStatus()
    calculateThermostatSetpointStatus()
}

/******************************************************************************
 *  Capability-Related Commands
 ******************************************************************************/
void poll() {
    logMessage("debug", "${device.label}: poll()")
    parent.poll(state.zoneId)
}

void refresh() {
    logMessage("debug", "${device.label}: refresh()")
    sendEvent(name: 'thermostatSetpointStatus', value: 'Updating', displayed: false)
    parent.poll(state.zoneId)
}

def setThermostatMode(String mode, until = -1) {
    logMessage("info", "${device.label}: setThermostatMode(Mode: ${mode}, Until: ${until})")
    if (!parent.setThermostatMode(state.systemId, mode, until)) {
        sendEvent(name: 'thermostatSetpointStatus', value: 'Updating', displayed: false)
        pseudoSleep(state.updateRefreshTime * 1000)
        parent.poll(0)
        return null
    } else {
        logMessage("error", "${device.label}: setThermostatMode() - Error setting thermostat mode")
        return 'error'
    }
}

def setHeatingSetpoint(setpoint, until = -1) {
    logMessage("debug", "${device.label}: setHeatingSetpoint(Setpoint: ${setpoint}, Until: ${until})")
    setpoint = formatTemperature(setpoint)

    // Clamp setpoint to allowed range.
    if (Float.parseFloat(setpoint) < Float.parseFloat(state.minHeatingSetpoint)) {
        logMessage("warn", "${device.label}: Specified setpoint (${setpoint}) below minimum (${state.minHeatingSetpoint}); using minimum.")
        setpoint = state.minHeatingSetpoint
    } else if (Float.parseFloat(setpoint) > Float.parseFloat(state.maxHeatingSetpoint)) {
        logMessage("warn", "${device.label}: Specified setpoint (${setpoint}) above maximum (${state.maxHeatingSetpoint}); using maximum.")
        setpoint = state.maxHeatingSetpoint
    }

    // Determine the effective "until" value.
    def untilRes = resolveUntilValue(until)
    logMessage("info", "${device.label}: setHeatingSetpoint() - Setting ${setpoint} until ${untilRes}")

    if (!parent.setHeatingSetpoint(state.zoneId, setpoint, untilRes)) {
        // Update device attributes and then poll parent.
        sendEvent(name: 'heatingSetpoint', value: setpoint)
        sendEvent(name: 'thermostatSetpoint', value: setpoint)
        sendEvent(name: 'thermostatSetpointMode', value: (untilRes == 0) ? 'permanentOverride' : 'temporaryOverride')
        sendEvent(name: 'thermostatSetpointUntil', value: (untilRes == 0) ? null : untilRes.format("yyyy-MM-dd'T'HH:mm:00XX", TimeZone.getTimeZone('UTC')))
        calculateThermostatOperatingState()
        calculateOptimisations()
        calculateThermostatStatus()
        sendEvent(name: 'thermostatSetpointStatus', value: 'Updating', displayed: false)
        pseudoSleep(state.updateRefreshTime * 1000)
        parent.poll(state.zoneId)
        return null
    } else {
        logMessage("error", "${device.label}: setHeatingSetpoint() - Error setting heating setpoint.")
        return 'error'
    }
}

def clearHeatingSetpoint() {
    logMessage("info", "${device.label}: clearHeatingSetpoint()")
    if (!parent.clearHeatingSetpoint(state.zoneId)) {
        sendEvent(name: 'thermostatSetpointMode', value: 'followSchedule')
        sendEvent(name: 'thermostatSetpointStatus', value: 'Updating', displayed: false)
        pseudoSleep(state.updateRefreshTime * 1000)
        parent.poll(state.zoneId)
        return null
    } else {
        logMessage("error", "${device.label}: clearHeatingSetpoint() - Error clearing setpoint.")
        return 'error'
    }
}

void raiseSetpoint() {
    logMessage("debug", "${device.label}: raiseSetpoint()")
    def mode = device.currentValue("thermostatMode")
    def targetSp = new BigDecimal(state.targetSetpoint)
    def tempRes = new BigDecimal(state.temperatureResolution)
    def maxSp = new BigDecimal(state.maxHeatingSetpoint)
    
    if (mode in ['off', 'away']) {
        logMessage("warn", "${device.label}: raiseSetpoint() - Thermostat mode (${mode}) disallows setpoint adjustments.")
    } else {
        targetSp = (targetSp + tempRes) > maxSp ? maxSp : (targetSp + tempRes)
        state.targetSetpoint = targetSp
        logMessage("info", "${device.label}: raiseSetpoint() - Target setpoint raised to: ${targetSp}")
        sendEvent(name: 'thermostatSetpointStatus', value: 'Updating', displayed: false)
        runIn(3, "alterSetpoint", [overwrite: true])
    }
}

void lowerSetpoint() {
    logMessage("debug", "${device.label}: lowerSetpoint()")
    def mode = device.currentValue("thermostatMode")
    def targetSp = new BigDecimal(state.targetSetpoint)
    def tempRes = new BigDecimal(state.temperatureResolution)
    def minSp = new BigDecimal(state.minHeatingSetpoint)
    
    if (mode in ['off', 'away']) {
        logMessage("warn", "${device.label}: lowerSetpoint() - Thermostat mode (${mode}) disallows setpoint adjustments.")
    } else {
        targetSp = (targetSp - tempRes) < minSp ? minSp : (targetSp - tempRes)
        state.targetSetpoint = targetSp
        logMessage("info", "${device.label}: lowerSetpoint() - Target setpoint lowered to: ${targetSp}")
        sendEvent(name: 'thermostatSetpointStatus', value: 'Updating', displayed: false)
        runIn(3, "alterSetpoint", [overwrite: true])
    }
}

private alterSetpoint() {
    logMessage("debug", "${device.label}: alterSetpoint()")
    setHeatingSetpoint(state.targetSetpoint)
}

/******************************************************************************
 *  Convenience Commands
 ******************************************************************************/
void resume() {
    logMessage("debug", "${device.label}: resume()")
    clearHeatingSetpoint()
}

void auto() {
    logMessage("debug", "${device.label}: auto()")
    setThermostatMode('auto')
}

void heat() {
    logMessage("debug", "${device.label}: heat()")
    setThermostatMode('auto')
}

void off() {
    logMessage("debug", "${device.label}: off()")
    setThermostatMode('off')
}

void away(until = -1) {
    logMessage("debug", "${device.label}: away()")
    setThermostatMode('away', until)
}

void custom(until = -1) {
    logMessage("debug", "${device.label}: custom()")
    setThermostatMode('custom', until)
}

void dayOff(until = -1) {
    logMessage("debug", "${device.label}: dayOff()")
    setThermostatMode('dayOff', until)
}

void economy(until = -1) {
    logMessage("debug", "${device.label}: economy()")
    setThermostatMode('economy', until)
}

void boost() {
    logMessage("debug", "${device.label}: boost()")
    setHeatingSetpoint(state.boostTemperature)
}

void suppress() {
    logMessage("debug", "${device.label}: suppress()")
    setHeatingSetpoint(state.suppressTemperature)
}

/******************************************************************************
 *  Helper Methods
 ******************************************************************************/

/**
 * Resolve the effective "until" value for setHeatingSetpoint.
 */
private def resolveUntilValue(until) {
    Calendar c = new GregorianCalendar()
    def tz = location.timeZone
    def untilRes
    // If no until provided, use the default setpoint mode.
    if (until == -1) {
        switch (state.setpointMode) {
            case 'Next Switchpoint': until = 'nextSwitchpoint'; break
            case 'Midday': until = 'midday'; break
            case 'Midnight': until = 'midnight'; break
            case 'Duration': until = state.setpointDuration ?: 0; break
            case 'Time': until = 'nextSwitchpoint'; break
            case 'Permanent': until = 'permanent'; break
            default: until = 'nextSwitchpoint'; break
        }
    }
    // Process until value
    if (until in ['permanent', 0]) {
        untilRes = 0
    } else if (until instanceof Date) {
        untilRes = until
    } else if (until == 'nextSwitchpoint') {
        untilRes = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", device.currentValue('nextScheduledTime'))
    } else if (until == 'midday') {
        untilRes = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", new Date().format("yyyy-MM-dd'T'12:00:00XX", tz))
    } else if (until == 'midnight') {
        c.add(Calendar.DATE, 1)
        untilRes = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", c.getTime().format("yyyy-MM-dd'T'00:00:00XX", tz))
    } else if (until ==~ /\d+.*T.*/) {
        untilRes = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", until)
    } else if (until.isNumber()) {
        untilRes = new Date(now() + (Math.round(until) * 60000))
    } else {
        logMessage("warn", "${device.label}: resolveUntilValue() - Unable to parse until value; defaulting to permanent.")
        untilRes = 0
    }
    return untilRes
}

/**
 * Pseudo sleep (busy wait) for the given milliseconds.
 */
private pseudoSleep(ms) {
    def start = now()
    while (now() < start + ms) { /* busy-wait */ }
}

/**
 * Retrieve the default value for a given input name.
 */
private getInputDefaultValue(inputName) {
    logMessage("debug", "${device.label}: getInputDefaultValue() for ${inputName}")
    def returnValue
    properties.preferences?.sections.each { section ->
        section.input.each { input ->
            if (input.name == inputName) {
                returnValue = input.defaultValue
            }
        }
    }
    return returnValue
}

/**
 * Format a temperature value to one decimal place.
 */
private formatTemperature(t) {
    return Float.parseFloat("${t}").round(1).toString()
}

/**
 * Format thermostat mode for display.
 */
private formatThermostatModeForDisp(mode) {
    logMessage("debug", "${device.label}: formatThermostatModeForDisp()")
    switch (mode) {
        case 'auto':      return 'Auto'
        case 'economy':   return 'Economy'
        case 'away':      return 'Away'
        case 'custom':    return 'Custom'
        case 'dayOff':    return 'Day Off'
        case 'off':       return 'Off'
        default:          return 'Unknown'
    }
}

/******************************************************************************
 *  Calculation Methods
 ******************************************************************************/
private calculateThermostatOperatingState() {
    logMessage("debug", "${device.label}: calculateThermostatOperatingState()")
    def tOS = (device.currentValue('thermostatMode') == 'off') ? 'off' :
              (device.currentValue("temperature") < device.currentValue("thermostatSetpoint")) ? 'heating' : 'idle'
    sendEvent(name: 'thermostatOperatingState', value: tOS)
}

private calculateOptimisations() {
    logMessage("debug", "${device.label}: calculateOptimisations()")
    def newOptValue = 'inactive'
    def newWdfValue = 'inactive'
    def heatingSp = new BigDecimal(device.currentValue('heatingSetpoint'))
    def scheduledSp = new BigDecimal(device.currentValue('scheduledSetpoint'))
    def nextScheduledSp = new BigDecimal(device.currentValue('nextScheduledSetpoint'))
    def windowTemp = new BigDecimal(state.windowFunctionTemperature ?: formatTemperature(5.0))
    
    // Only apply optimisation in auto mode and when schedule is followed.
    if (device.currentValue('thermostatMode') == 'auto' && device.currentValue('thermostatSetpointMode') == 'followSchedule') {
        if (heatingSp == nextScheduledSp) {
            newOptValue = 'active'
        }
        else if (heatingSp == windowTemp) {
            newWdfValue = 'active'
        }
    }
    sendEvent(name: 'optimisation', value: newOptValue)
    sendEvent(name: 'windowFunction', value: newWdfValue)
}

private calculateThermostatStatus() {
    logMessage("debug", "${device.label}: calculateThermostatStatus()")
    def thermostatModeDisp = formatThermostatModeForDisp(device.currentValue('thermostatMode'))
    def setpoint = device.currentValue('thermostatSetpoint')
    def newThermostatStatus = (thermostatModeDisp == 'Off') ? 'Off' :
                              (device.currentValue('thermostatOperatingState') == 'heating') ? "Heating to ${setpoint}° (${thermostatModeDisp})" :
                              "Idle (${thermostatModeDisp})"
    sendEvent(name: 'thermostatStatus', value: newThermostatStatus)
}

private calculateThermostatSetpointStatus() {
    logMessage("debug", "${device.label}: calculateThermostatSetpointStatus()")
    def setpointMode = device.currentValue('thermostatSetpointMode')
    def newThermostatSetpointStatus = ''
    if (device.currentValue('thermostatMode') in ['off', 'away']) {
        newThermostatSetpointStatus = device.currentValue('thermostatMode').capitalize()
    }
    else if (device.currentValue('optimisation') == 'active') {
        newThermostatSetpointStatus = 'Optimisation Active'
    }
    else if (device.currentValue('windowFunction') == 'active') {
        newThermostatSetpointStatus = 'Window Function Active'
    }
    else if (setpointMode == 'followSchedule') {
        newThermostatSetpointStatus = 'Following Schedule'
    }
    else if (setpointMode == 'permanentOverride') {
        newThermostatSetpointStatus = 'Permanent'
    }
    else {
        def untilStr = device.currentValue('thermostatSetpointUntil')
        if (untilStr) {
            def untilDate = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", untilStr)
            def untilDisp = (untilDate.format("u") == new Date().format("u")) ?
                             untilDate.format("HH:mm", location.timeZone) :
                             untilDate.format("HH:mm 'on' EEEE", location.timeZone)
            newThermostatSetpointStatus = "Temporary Until ${untilDisp}"
        } else {
            newThermostatSetpointStatus = "Temporary"
        }
    }
    sendEvent(name: 'thermostatSetpointStatus', value: newThermostatSetpointStatus)
}
