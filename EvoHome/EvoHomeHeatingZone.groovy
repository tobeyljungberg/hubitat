/**
 *  Copyright 2016 Tobey Ljungberg (tljungberg)
 *
 *  Name: Evohome Heating Zone
 *  Author: Tobey Ljungberg (tljungberg)
 *  Date: 2016-04-08
 *  Version: 0.09
 *
 *  Description:
 *    Child device for the Evohome (Connect) SmartApp.
 *
 *  License:
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at:
 *       http://www.apache.org/licenses/LICENSE-2.0
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
        
        valueTile("thermostatSetpoint", "device.thermostatSetpoint", width: 3, height: 1) {
            state "thermostatSetpoint", label:'Setpoint: ${currentValue}°', unit:"C"
        }
        valueTile("thermostatSetpointStatus", "device.thermostatSetpointStatus", width: 3, height: 1, decoration: "flat") {
            state "thermostatSetpointStatus", label:'${currentValue}', backgroundColor:"#ffffff"
        }
        standardTile("raiseSetpoint", "device.thermostatSetpoint", width: 1, height: 1, decoration: "flat") {
            state "default", action:"raiseSetpoint", icon:"st.thermostat.thermostat-up"
        }
        standardTile("lowerSetpoint", "device.thermostatSetpoint", width: 1, height: 1, decoration: "flat") {
            state "default", action:"lowerSetpoint", icon:"st.thermostat.thermostat-down"
        }
        standardTile("resume", "device.resume", width: 1, height: 1, decoration: "flat") {
            state "default", action:"resume", label:'Resume', icon:"st.samsung.da.oven_ic_send"
        }
        standardTile("boost", "device.boost", width: 1, height: 1, decoration: "flat") {
            state "default", action:"boost", label:'Boost'
        }
        standardTile("suppress", "device.suppress", width: 1, height: 1, decoration: "flat") {
            state "default", action:"suppress", label:'Suppress'
        }
        valueTile("thermostatStatus", "device.thermostatStatus", height: 1, width: 6, decoration: "flat") {
            state "default", label:'${currentValue}', backgroundColor:"#ffffff"
        }
        standardTile("refresh", "device.thermostatMode", decoration: "flat") {
            state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        standardTile("test", "device.test", width: 1, height: 1, decoration: "flat") {
            state "default", label:'Test', action:"test"
        }
        
        // Mode Tiles
        standardTile("auto", "device.auto", width: 2, height: 2, decoration: "flat") {
            state "default", action:"auto", icon:"st.thermostat.auto"
        }
        standardTile("away", "device.away", width: 2, height: 2, decoration: "flat") {
            state "default", action:"away", label:'Away'
        }
        standardTile("custom", "device.custom", width: 2, height: 2, decoration: "flat") {
            state "default", action:"custom", label:'Custom'
        }
        standardTile("dayOff", "device.dayOff", width: 2, height: 2, decoration: "flat") {
            state "default", action:"dayOff", label:'Day Off'
        }
        standardTile("economy", "device.economy", width: 2, height: 2, decoration: "flat") {
            state "default", action:"economy", label:'Economy'
        }
        standardTile("off", "device.off", width: 2, height: 2, decoration: "flat") {
            state "default", action:"off", icon:"st.thermostat.heating-cooling-off"
        }
        
        main "temperature"
        details(["multi", "thermostatSetpoint", "raiseSetpoint", "boost", "resume",
                 "thermostatSetpointStatus", "lowerSetpoint", "suppress", "refresh",
                 "auto", "away", "custom", "dayOff", "economy", "off"])
    }

    preferences {
        section("Setpoint Adjustments:") {
            input title: "Setpoint Duration", description: "Configure how long setpoint adjustments are applied for.", displayDuringSetup: true, type: "paragraph", element: "paragraph"
            input 'prefSetpointMode', 'enum', title: 'Until', options: ["Next Switchpoint", "Midday", "Midnight", "Duration", "Permanent"], defaultValue: "Next Switchpoint", required: true, displayDuringSetup: true
            input 'prefSetpointDuration', 'number', title: 'Duration (minutes)', description: 'Apply setpoint for this many minutes', range: "1..1440", defaultValue: 60, required: true, displayDuringSetup: true
            input title: "Setpoint Temperatures", description: "Configure preset temperatures for the 'Boost' and 'Suppress' buttons.", displayDuringSetup: true, type: "paragraph", element: "paragraph"
            input "prefBoostTemperature", "string", title: "'Boost' Temperature", defaultValue: "21.5", required: true, displayDuringSetup: true
            input "prefSuppressTemperature", "string", title: "'Suppress' Temperature", defaultValue: "15.0", required: true, displayDuringSetup: true
        }
    }
}

/**********************************************************************
 *  Test and Setup Commands
 **********************************************************************/
def test() {
    // For testing purposes only.
}

def installed() {
    log.debug "${app.label}: Installed with settings: ${settings}"
    state.installedAt = now()
    // Initialize state defaults; these will be overwritten by parent shortly.
    state.debug = false
    state.updateRefreshTime = 5
    state.zoneType = 'RadiatorZone'
    state.minHeatingSetpoint = formatTemperature(5.0)
    state.maxHeatingSetpoint = formatTemperature(35.0)
    state.temperatureResolution = formatTemperature(0.5)
    state.windowFunctionTemperature = formatTemperature(5.0)
    state.targetSetpoint = state.minHeatingSetpoint

    // Initialize preference values from defaults.
    state.setpointMode = getInputDefaultValue('prefSetpointMode')
    state.setpointDuration = getInputDefaultValue('prefSetpointDuration')
    state.boostTemperature = getInputDefaultValue('prefBoostTemperature')
    state.suppressTemperature = getInputDefaultValue('prefSuppressTemperature')
}

def updated() {
    if (state.debug) log.debug "${device.label}: Updated with settings: ${settings}"
    state.setpointMode = settings.prefSetpointMode
    state.setpointDuration = settings.prefSetpointDuration
    state.boostTemperature = formatTemperature(settings.prefBoostTemperature)
    state.suppressTemperature = formatTemperature(settings.prefSuppressTemperature)
}

/**********************************************************************
 *  SmartApp-Child Interface Commands
 **********************************************************************/
void generateEvent(values) {
    log.info "${device.label}: generateEvent(): New values: ${values}"
    if (values) {
        values.each { name, value ->
            // Internal state values
            if (name in ['minHeatingSetpoint', 'maxHeatingSetpoint', 'temperatureResolution', 
                         'windowFunctionTemperature', 'zoneType', 'locationId', 'gatewayId', 
                         'systemId', 'zoneId', 'schedule', 'debug', 'updateRefreshTime']) {
                state."${name}" = value
            } else { // Generate events for attributes
                sendEvent(name: name, value: value, displayed: (name && value))
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

/**********************************************************************
 *  Capability Commands
 **********************************************************************/
void poll() {
    if (state.debug) log.debug "${device.label}: poll()"
    parent.poll(state.zoneId)
}

void refresh() {
    if (state.debug) log.debug "${device.label}: refresh()"
    sendEvent(name: 'thermostatSetpointStatus', value: 'Updating', displayed: false)
    parent.poll(state.zoneId)
}

def setThermostatMode(String mode, until=-1) {
    log.info "${device.label}: setThermostatMode(Mode: ${mode}, Until: ${until})"
    if (!parent.setThermostatMode(state.systemId, mode, until)) {
        sendEvent(name: 'thermostatSetpointStatus', value: 'Updating', displayed: false)
        pseudoSleep(state.updateRefreshTime * 1000)
        parent.poll(0)
        return null
    } else {
        log.error "${device.label}: setThermostatMode(): Error setting mode."
        return 'error'
    }
}

def setHeatingSetpoint(setpoint, until=-1) {
    if (state.debug) log.debug "${device.label}: setHeatingSetpoint(Setpoint: ${setpoint}, Until: ${until})"
    setpoint = formatTemperature(setpoint)
    if (Float.parseFloat(setpoint) < Float.parseFloat(state.minHeatingSetpoint)) {
        log.warn "${device.label}: Specified setpoint (${setpoint}) below minimum; using ${state.minHeatingSetpoint}."
        setpoint = state.minHeatingSetpoint
    } else if (Float.parseFloat(setpoint) > Float.parseFloat(state.maxHeatingSetpoint)) {
        log.warn "${device.label}: Specified setpoint (${setpoint}) above maximum; using ${state.maxHeatingSetpoint}."
        setpoint = state.maxHeatingSetpoint
    }

    def untilRes
    Calendar c = new GregorianCalendar()
    // Determine default duration if not specified.
    if (-1 == until) {
        switch (state.setpointMode) {
            case 'Next Switchpoint': until = 'nextSwitchpoint'; break
            case 'Midday': until = 'midday'; break
            case 'Midnight': until = 'midnight'; break
            case 'Duration': until = state.setpointDuration ?: 0; break
            case 'Permanent': until = 'permanent'; break
            default: until = 'nextSwitchpoint'
        }
    }
    
    if (until == 'permanent' || until == 0) {
        untilRes = 0
    } else if (until instanceof Date) {
        untilRes = until
    } else if (until == 'nextSwitchpoint') {
        untilRes = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", device.currentValue('nextScheduledTime'))
    } else if (until == 'midday') {
        untilRes = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", new Date().format("yyyy-MM-dd'T'12:00:00XX", location.timeZone))
    } else if (until == 'midnight') {
        c.add(Calendar.DATE, 1)
        untilRes = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", c.getTime().format("yyyy-MM-dd'T'00:00:00XX", location.timeZone))
    } else if (until ==~ /\d+.*T.*/) {
        untilRes = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", until)
    } else if (until.isNumber()) {
        untilRes = new Date(now() + (Math.round(until) * 60000))
    } else {
        log.warn "${device.label}: Unable to parse until value; applying permanently."
        untilRes = 0
    }
    
    log.info "${device.label}: setHeatingSetpoint(): Setting setpoint to ${setpoint} until ${untilRes}"
    if (!parent.setHeatingSetpoint(state.zoneId, setpoint, untilRes)) {
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
        log.error "${device.label}: setHeatingSetpoint(): Error setting setpoint."
        return 'error'
    }
}

def clearHeatingSetpoint() {
    log.info "${device.label}: clearHeatingSetpoint()"
    if (!parent.clearHeatingSetpoint(state.zoneId)) {
        sendEvent(name: 'thermostatSetpointMode', value: 'followSchedule')
        sendEvent(name: 'thermostatSetpointStatus', value: 'Updating', displayed: false)
        pseudoSleep(state.updateRefreshTime * 1000)
        parent.poll(state.zoneId)
        return null
    } else {
        log.error "${device.label}: clearHeatingSetpoint(): Error clearing setpoint."
        return 'error'
    }
}

void raiseSetpoint() {
    if (state.debug) log.debug "${device.label}: raiseSetpoint()"
    def mode = device.currentValue("thermostatMode")
    def targetSp = new BigDecimal(state.targetSetpoint)
    def tempRes = new BigDecimal(state.temperatureResolution)
    def maxSp = new BigDecimal(state.maxHeatingSetpoint)
    
    if (mode in ['off', 'away']) {
        log.warn "${device.label}: Cannot raise setpoint in mode ${mode}."
    } else {
        targetSp = (targetSp + tempRes) > maxSp ? maxSp : (targetSp + tempRes)
        state.targetSetpoint = targetSp
        log.info "${device.label}: raiseSetpoint(): New target setpoint is ${targetSp}"
        sendEvent(name: 'thermostatSetpointStatus', value: 'Updating', displayed: false)
        runIn(3, "alterSetpoint", [overwrite: true])
    }
}

void lowerSetpoint() {
    if (state.debug) log.debug "${device.label}: lowerSetpoint()"
    def mode = device.currentValue("thermostatMode")
    def targetSp = new BigDecimal(state.targetSetpoint)
    def tempRes = new BigDecimal(state.temperatureResolution)
    def minSp = new BigDecimal(state.minHeatingSetpoint)
    
    if (mode in ['off', 'away']) {
        log.warn "${device.label}: Cannot lower setpoint in mode ${mode}."
    } else {
        targetSp = (targetSp - tempRes) < minSp ? minSp : (targetSp - tempRes)
        state.targetSetpoint = targetSp
        log.info "${device.label}: lowerSetpoint(): New target setpoint is ${targetSp}"
        sendEvent(name: 'thermostatSetpointStatus', value: 'Updating', displayed: false)
        runIn(3, "alterSetpoint", [overwrite: true])
    }
}

private alterSetpoint() {
    if (state.debug) log.debug "${device.label}: alterSetpoint()"
    setHeatingSetpoint(state.targetSetpoint)
}

/**********************************************************************
 *  Convenience Commands
 **********************************************************************/
void resume() { clearHeatingSetpoint() }
void auto() { setThermostatMode('auto') }
void heat() { setThermostatMode('auto') }
void off() { setThermostatMode('off') }
void away(until=-1) { setThermostatMode('away', until) }
void custom(until=-1) { setThermostatMode('custom', until) }
void dayOff(until=-1) { setThermostatMode('dayOff', until) }
void economy(until=-1) { setThermostatMode('economy', until) }
void boost() { setHeatingSetpoint(state.boostTemperature) }
void suppress() { setHeatingSetpoint(state.suppressTemperature) }

/**********************************************************************
 *  Helper Methods
 **********************************************************************/
private pseudoSleep(ms) {
    def start = now()
    while (now() < start + ms) { /* busy-wait */ }
}

private getInputDefaultValue(inputName) {
    if (state.debug) log.debug "${device.label}: getInputDefaultValue()"
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

private formatTemperature(t) {
    return Float.parseFloat("${t}").round(1).toString()
}

private formatThermostatModeForDisp(mode) {
    if (state.debug) log.debug "${device.label}: formatThermostatModeForDisp()"
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

/**********************************************************************
 *  Derived State Calculations
 **********************************************************************/
private calculateThermostatOperatingState() {
    if (state.debug) log.debug "${device.label}: calculateThermostatOperatingState()"
    def tOS = (device.currentValue('thermostatMode') == 'off') ? 'off' :
              (device.currentValue("temperature") < device.currentValue("thermostatSetpoint")) ? 'heating' : 'idle'
    sendEvent(name: 'thermostatOperatingState', value: tOS)
}

private calculateOptimisations() {
    if (state.debug) log.debug "${device.label}: calculateOptimisations()"
    def newOptValue = 'inactive'
    def newWdfValue = 'inactive'
    
    def heatingSp = new BigDecimal(device.currentValue('heatingSetpoint'))
    def scheduledSp = new BigDecimal(device.currentValue('scheduledSetpoint'))
    def nextScheduledSp = new BigDecimal(device.currentValue('nextScheduledSetpoint'))
    def windowTemp = new BigDecimal(state.windowFunctionTemperature ?: formatTemperature(5.0))
    
    if (device.currentValue('thermostatMode') == 'auto' &&
        device.currentValue('thermostatSetpointMode') == 'followSchedule') {
        if (heatingSp == nextScheduledSp) {
            newOptValue = 'active'
        } else if (heatingSp == windowTemp) {
            newWdfValue = 'active'
        }
    }
    
    sendEvent(name: 'optimisation', value: newOptValue)
    sendEvent(name: 'windowFunction', value: newWdfValue)
}

private calculateThermostatStatus() {
    if (state.debug) log.debug "${device.label}: calculateThermostatStatus()"
    def thermostatModeDisp = formatThermostatModeForDisp(device.currentValue('thermostatMode'))
    def setpoint = device.currentValue('thermostatSetpoint')
    def newStatus = (thermostatModeDisp == 'Off') ? 'Off' :
                    (device.currentValue('thermostatOperatingState') == 'heating') ? "Heating to ${setpoint}° (${thermostatModeDisp})" :
                    "Idle (${thermostatModeDisp})"
    sendEvent(name: 'thermostatStatus', value: newStatus)
}

private calculateThermostatSetpointStatus() {
    if (state.debug) log.debug "${device.label}: calculateThermostatSetpointStatus()"
    def setpointMode = device.currentValue('thermostatSetpointMode')
    def newStatus = ''
    
    if (device.currentValue('thermostatMode') == 'off') {
        newStatus = 'Off'
    } else if (device.currentValue('thermostatMode') == 'away') {
        newStatus = 'Away'
    } else if (device.currentValue('optimisation') == 'active') {
        newStatus = 'Optimisation Active'
    } else if (device.currentValue('windowFunction') == 'active') {
        newStatus = 'Window Function Active'
    } else if (setpointMode == 'followSchedule') {
        newStatus = 'Following Schedule'
    } else if (setpointMode == 'permanentOverride') {
        newStatus = 'Permanent'
    } else {
        def untilStr = device.currentValue('thermostatSetpointUntil')
        if (untilStr) {
            def untilDate = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", untilStr)
            def untilDisp = (untilDate.format("u") == new Date().format("u")) ?
                            untilDate.format("HH:mm", location.timeZone) :
                            untilDate.format("HH:mm 'on' EEEE", location.timeZone)
            newStatus = "Temporary Until ${untilDisp}"
        } else {
            newStatus = "Temporary"
        }
    }
    sendEvent(name: 'thermostatSetpointStatus', value: newStatus)
}