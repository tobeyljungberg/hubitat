/**
 *  Copyright 2016 Tobey Ljungberg (tljungberg)
 *
 *  Name: Evohome Heating Zone
 *
 *  Author: Tobey Ljungberg (tljungberg)
 *
 *  Date: 2016-04-08
 *
 *  Version: 0.09
 *
 *  Description:
 *   - This device handler is a child device for the Evohome (Connect) SmartApp.
 *   - For latest documentation see: https://github.com/tljungberg/SmartThings
 *
 *  Version History:
 *
 *   2016-04-08: v0.09
 *    - calculateOptimisations(): Fixed comparison of temperature values.
 * 
 *   2016-04-05: v0.08
 *    - New 'Update Refresh Time' setting from parent to control polling after making an update.
 *    - setThermostatMode(): Forces poll for all zones to ensure new thermostatMode is updated.
 * 
 *   2016-04-04: v0.07
 *    - generateEvent(): hides events if name or value are null.
 *    - generateEvent(): log.info message for new values.
 * 
 *   2016-04-03: v0.06
 *    - Initial Beta Release
 * 
 *  To Do:
 *   - Clean up device settings (preferences). Hide/Show prefSetpointDuration input dynamically depending on prefSetpointMode. - If supported for devices???
 *   - When thermostat mode is away or off, heatingSetpoint overrides should not allowed (although setting while away actually works). Should warn at least.
 *
 *  License:
 *   Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *   in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *   on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *   for the specific language governing permissions and limitations under the License.
 *
 */ 
metadata {
    definition (name: "Evohome Heating Zone", namespace: "tljungberg", author: "Tobey Ljungberg") {
        capability "Actuator"
        capability "Sensor"
        capability "Refresh"
        capability "Temperature Measurement"
        // capability "Thermostat"
        capability "Thermostat Heating Setpoint"
        capability "Thermostat Setpoint"
        capability "Thermostat Mode"
        capability "Thermostat Operating State"
        
        //command "poll" // Polling
        command "refresh" // Refresh
        command "setHeatingSetpoint" // Thermostat
        command "raiseSetpoint" // Custom
        command "lowerSetpoint" // Custom
        command "setThermostatMode" // Thermostat
        command "cycleThermostatMode" // Custom
        command "off" // Thermostat
        command "heat" // Thermostat
        command "auto" // Custom
        command "away" // Custom
        command "economy" // Custom
        command "dayOff" // Custom
        command "custom" // Custom
        command "resume" // Custom
        command "boost" // Custom
        command "suppress" // Custom
        command "generateEvent" // Custom
        command "test" // Custom

        attribute "temperature","number" // Temperature Measurement
        attribute "heatingSetpoint","number" // Thermostat
        attribute "thermostatSetpoint","number" // Thermostat
        attribute "thermostatSetpointMode", "string" // Custom
        attribute "thermostatSetpointUntil", "string" // Custom
        attribute "thermostatSetpointStatus", "string" // Custom
        attribute "thermostatMode", "string" // Thermostat
        attribute "thermostatOperatingState", "string" // Thermostat
        attribute "thermostatStatus", "string" // Custom
        attribute "scheduledSetpoint", "number" // Custom
        attribute "nextScheduledSetpoint", "number" // Custom
        attribute "nextScheduledTime", "string" // Custom
        attribute "optimisation", "string" // Custom
        attribute "windowFunction", "string" // Custom
    }

    tiles(scale: 2) {

        // Main multi
        multiAttributeTile(name:"multi", type:"thermostat", width:6, height:4) {
            tileAttribute("device.temperature", key: "PRIMARY_CONTROL") {
                attributeState("default", label:'${currentValue}°', unit:"C")
            }
            // Operating State - used to get background colour when type is 'thermostat'.
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
    
        // temperature tile:
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
        
        // thermostatSetpoint tiles:
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
        
        // thermostatStatus tile:
        valueTile("thermostatStatus", "device.thermostatStatus", height: 1, width: 6, decoration: "flat") {
            state "thermostatStatus", label:'${currentValue}', backgroundColor:"#ffffff"
        }
        // Individual Mode tiles:
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
        details(
            [
                "multi",
                "thermostatSetpoint","raiseSetpoint","boost","resume",
                "thermostatSetpointStatus","lowerSetpoint","suppress","refresh",
                "auto","away","custom","dayOff","economy","off"
            ]
        )
    }

    preferences {
        section("Log Level Settings") {
            input "prefLogLevel", "enum", title: "Log Level", 
                options: ["error", "warn", "info", "debug"], defaultValue: "info", 
                required: true, displayDuringSetup: true
        }
        section { // Setpoint Adjustments:
            input title: "Setpoint Duration", description: "Configure how long setpoint adjustments are applied for.", displayDuringSetup: true, type: "paragraph", element: "paragraph"
            input 'prefSetpointMode', 'enum', title: 'Until', description: '', options: ["Next Switchpoint", "Midday", "Midnight", "Duration", "Permanent"], defaultValue: "Next Switchpoint", required: true, displayDuringSetup: true
            input 'prefSetpointDuration', 'number', title: 'Duration (minutes)', description: 'Apply setpoint for this many minutes', range: "1..1440", defaultValue: 60, required: true, displayDuringSetup: true
            input title: "Setpoint Temperatures", description: "Configure preset temperatures for the 'Boost' and 'Suppress' buttons.", displayDuringSetup: true, type: "paragraph", element: "paragraph"
            input "prefBoostTemperature", "string", title: "'Boost' Temperature", defaultValue: "21.5", required: true, displayDuringSetup: true
            input "prefSuppressTemperature", "string", title: "'Suppress' Temperature", defaultValue: "15.0", required: true, displayDuringSetup: true
        }
    }
}

/**********************************************************************
 *  Logging Helper Method
 **********************************************************************/
/**
 * logMessage(level, msg)
 *
 * Logs a message only if the specified level is at or above the current log level.
 * Levels: error (1), warn (2), info (3), debug (4)
 */
private void logMessage(String level, String msg) {
    def levels = [ "error": 1, "warn": 2, "info": 3, "debug": 4 ]
    def configuredLevel = (state.logLevel ?: settings.prefLogLevel ?: "info").toLowerCase()
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
 *  Test Commands:
 **********************************************************************/
def test() {
    logMessage("debug", "${device.label}: test(): Properties: ${properties}")
    logMessage("debug", "${device.label}: test(): Settings: ${settings}")
    logMessage("debug", "${device.label}: test(): State: ${state}")
}

/**********************************************************************
 *  Setup and Configuration Commands:
 **********************************************************************/
def installed() {
    logMessage("debug", "${device.label}: Installed with settings: ${settings}")
    state.installedAt = now()
    
    // These default values will be overwritten by the Evohome SmartApp almost immediately:
    state.debug = false
    state.updateRefreshTime = 5 // Wait this many seconds after an update before polling.
    state.zoneType = 'RadiatorZone'
    state.minHeatingSetpoint = formatTemperature(5.0)
    state.maxHeatingSetpoint = formatTemperature(35.0)
    state.temperatureResolution = formatTemperature(0.5)
    state.windowFunctionTemperature = formatTemperature(5.0)
    state.targetSetpoint = state.minHeatingSetpoint
    
    // Populate state with default values for each preference/input:
    state.setpointMode = getInputDefaultValue('prefSetpointMode')
    state.setpointDuration = getInputDefaultValue('prefSetpointDuration')
    state.boostTemperature = getInputDefaultValue('prefBoostTemperature')
    state.suppressTemperature = getInputDefaultValue('prefSuppressTemperature')
    
    // Set log level from preferences - this is not imported from parent app so must be edited here for debug.
    state.logLevel = settings.prefLogLevel ?: "info"
}

def updated() {
    logMessage("debug", "${device.label}: Updating with settings: ${settings}")
    
    // Copy input values to state:
    state.setpointMode = settings.prefSetpointMode
    state.setpointDuration = settings.prefSetpointDuration
    state.boostTemperature = formatTemperature(settings.prefBoostTemperature)
    state.suppressTemperature = formatTemperature(settings.prefSuppressTemperature)
    
    // Update log level setting:
    state.logLevel = settings.prefLogLevel ?: "info"
}

/**********************************************************************
 *  SmartApp-Child Interface Commands:
 **********************************************************************/
void generateEvent(values) {
    logMessage("info", "${device.label}: generateEvent(): New values: ${values}")
    
    if(values) {
        values.each { name, value ->
            if ( name == 'minHeatingSetpoint' 
                || name == 'maxHeatingSetpoint' 
                || name == 'temperatureResolution' 
                || name == 'windowFunctionTemperature'
                || name == 'zoneType'
                || name == 'locationId'
                || name == 'gatewayId'
                || name == 'systemId'
                || name == 'zoneId'
                || name == 'schedule'
                || name == 'debug'
                || name == 'updateRefreshTime'
                ) {
                state."${name}" = value
            }
            else {
                if (name != null && value != null) {
                    sendEvent(name: name, value: value, displayed: true)
                }
                else {
                    sendEvent(name: name, value: value, displayed: false)
                }
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
 *  Capability-related Commands:
 **********************************************************************/
void poll() {
    logMessage("debug", "${device.label}: poll()")
    parent.poll(state.zoneId)
}

void refresh() {
    logMessage("debug", "${device.label}: refresh()")
    sendEvent(name: 'thermostatSetpointStatus', value: 'Updating', displayed: false)
    parent.poll(state.zoneId)
}

def setThermostatMode(String mode, until=-1) {
    logMessage("info", "${device.label}: setThermostatMode(Mode: ${mode}, Until: ${until})")
    
    if (!parent.setThermostatMode(state.systemId, mode, until)) {
        sendEvent(name: 'thermostatSetpointStatus', value: 'Updating', displayed: false)
        pseudoSleep(state.updateRefreshTime * 1000)
        parent.poll(0)
        return null
    }
    else {
        logMessage("error", "${device.label}: setThermostatMode(): Error: Unable to set thermostat mode.")
        return 'error'
    }
}

def setHeatingSetpoint(setpoint, until=-1) {
    logMessage("debug", "${device.label}: setHeatingSetpoint(Setpoint: ${setpoint}, Until: ${until})")
    
    setpoint = formatTemperature(setpoint)
    if (Float.parseFloat(setpoint) < Float.parseFloat(state.minHeatingSetpoint)) {
        logMessage("warn", "${device.label}: setHeatingSetpoint(): Specified setpoint (${setpoint}) is less than zone's minimum setpoint (${state.minHeatingSetpoint}).")
        setpoint = state.minHeatingSetpoint
    }
    else if (Float.parseFloat(setpoint) > Float.parseFloat(state.maxHeatingSetpoint)) {
        logMessage("warn", "${device.label}: setHeatingSetpoint(): Specified setpoint (${setpoint}) is greater than zone's maximum setpoint (${state.maxHeatingSetpoint}).")
        setpoint = state.maxHeatingSetpoint
    }
    
    def untilRes
    Calendar c = new GregorianCalendar()
    def tzOffset = location.timeZone.getOffset(new Date().getTime())
    
    if (-1 == until) {
        switch (state.setpointMode) {
            case 'Next Switchpoint':
                until = 'nextSwitchpoint'
                break
            case 'Midday':
                until = 'midday'
                break
            case 'Midnight':
                until = 'midnight'
                break
            case 'Duration':
                until = state.setpointDuration ?: 0
                break
            case 'Time':
                logMessage("debug", "${device.label}: setHeatingSetpoint(): Time: ${state.SetpointTime}")
                until = 'nextSwitchpoint'
                break
            case 'Permanent':
                until = 'permanent'
                break
            default:
                until = 'nextSwitchpoint'
                break
        }
    }
    
    if ('permanent' == until || 0 == until) {
        untilRes = 0
    }
    else if (until instanceof Date) {
        untilRes = until
    }
    else if ('nextSwitchpoint' == until) {
        untilRes = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", device.currentValue('nextScheduledTime'))
    }
    else if ('midday' == until) {
        untilRes = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", new Date().format("yyyy-MM-dd'T'12:00:00XX", location.timeZone)) 
    }
    else if ('midnight' == until) {
        c.add(Calendar.DATE, 1)
        untilRes = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", c.getTime().format("yyyy-MM-dd'T'00:00:00XX", location.timeZone))
    }
    else if (until ==~ /\d+.*T.*/) {
        untilRes = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", until)
    }
    else if (until.isNumber()) {
        untilRes = new Date(now() + (Math.round(until) * 60000))
    }
    else {
        logMessage("warn", "${device.label}: setHeatingSetpoint(): until value could not be parsed. Setpoint will be applied permanently.")
        untilRes = 0
    }
    
    logMessage("info", "${device.label}: setHeatingSetpoint(): Setting setpoint to: ${setpoint} until: ${untilRes}")
    
    if (!parent.setHeatingSetpoint(state.zoneId, setpoint, untilRes)) {
        sendEvent(name: 'heatingSetpoint', value: setpoint)
        sendEvent(name: 'thermostatSetpoint', value: setpoint)
        sendEvent(name: 'thermostatSetpointMode', value: (0 == untilRes) ? 'permanentOverride' : 'temporaryOverride')
        sendEvent(name: 'thermostatSetpointUntil', value: (0 == untilRes) ? null : untilRes.format("yyyy-MM-dd'T'HH:mm:00XX", TimeZone.getTimeZone('UTC')))
        calculateThermostatOperatingState()
        calculateOptimisations()
        calculateThermostatStatus()
        sendEvent(name: 'thermostatSetpointStatus', value: 'Updating', displayed: false)
        pseudoSleep(state.updateRefreshTime * 1000)
        parent.poll(state.zoneId)
        return null
    }
    else {
        logMessage("error", "${device.label}: setHeatingSetpoint(): Error: Unable to set heating setpoint.")
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
    }
    else {
        logMessage("error", "${device.label}: clearHeatingSetpoint(): Error: Unable to clear heating setpoint.")
        return 'error'
    }
}

void raiseSetpoint() {
    logMessage("debug", "${device.label}: raiseSetpoint()")
    
    def mode = device.currentValue("thermostatMode")
    def targetSp = new BigDecimal(state.targetSetpoint)
    def tempRes = new BigDecimal(state.temperatureResolution)
    def maxSp = new BigDecimal(state.maxHeatingSetpoint)
    
    if ('off' == mode || 'away' == mode) {
        logMessage("warn", "${device.label}: raiseSetpoint(): thermostat mode (${mode}) does not allow altering the temperature setpoint.")
    }
    else {
        targetSp += tempRes
        if (targetSp > maxSp) {
            targetSp = maxSp
        }
        state.targetSetpoint = targetSp
        logMessage("info", "${device.label}: raiseSetpoint(): Target setpoint raised to: ${targetSp}")
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
    
    if ('off' == mode || 'away' == mode) {
        logMessage("warn", "${device.label}: lowerSetpoint(): thermostat mode (${mode}) does not allow altering the temperature setpoint.")
    }
    else {
        targetSp -= tempRes 
        if (targetSp < minSp) {
            targetSp = minSp
        }
        state.targetSetpoint = targetSp
        logMessage("info", "${device.label}: lowerSetpoint(): Target setpoint lowered to: ${targetSp}")
        sendEvent(name: 'thermostatSetpointStatus', value: 'Updating', displayed: false)
        runIn(3, "alterSetpoint", [overwrite: true])
    }
}

private alterSetpoint() {
    logMessage("debug", "${device.label}: alterSetpoint()")
    setHeatingSetpoint(state.targetSetpoint)
}

/**********************************************************************
 *  Convenience Commands:
 **********************************************************************/
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

void away(until=-1) {
    logMessage("debug", "${device.label}: away()")
    setThermostatMode('away', until)
}

void custom(until=-1) {
    logMessage("debug", "${device.label}: custom()")
    setThermostatMode('custom', until)
}

void dayOff(until=-1) {
    logMessage("debug", "${device.label}: dayOff()")
    setThermostatMode('dayOff', until)
}

void economy(until=-1) {
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

/**********************************************************************
 *  Helper Commands:
 **********************************************************************/
private pseudoSleep(ms) {
    def start = now()
    while (now() < start + ms) {
        // busy wait (pseudo sleep)
    }
}

private getInputDefaultValue(inputName) {
    logMessage("debug", "${device.label}: getInputDefaultValue()")
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
    logMessage("debug", "${device.label}: formatThermostatModeForDisp()")
    switch (mode) {
        case 'auto':
            mode = 'Auto'
            break
        case 'economy':
            mode = 'Economy'
            break
        case 'away':
            mode = 'Away'
            break
        case 'custom':
            mode = 'Custom'
            break
        case 'dayOff':
            mode = 'Day Off'
            break
        case 'off':
            mode = 'Off'
            break
        default:
            mode = 'Unknown'
            break
    }
    return mode
}

private calculateThermostatOperatingState() {
    logMessage("debug", "${device.label}: calculateThermostatOperatingState()")
    def tOS
    if ('off' == device.currentValue('thermostatMode')) {
        tOS = 'off'
    }
    else if (device.currentValue("temperature") < device.currentValue("thermostatSetpoint")) {
        tOS = 'heating'
    }
    else {
        tOS = 'idle'
    }
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
    
    if ('auto' != device.currentValue('thermostatMode')) {
        // Do nothing – optimisations only apply in auto mode.
    }
    else if ('followSchedule' != device.currentValue('thermostatSetpointMode')) {
        // Manual override in effect.
    }
    else if (heatingSp == scheduledSp) {
        // No override
    }
    else if (heatingSp == nextScheduledSp) {
        newOptValue = 'active'
    }
    else if (heatingSp == windowTemp) {
        newWdfValue = 'active'
    }
    sendEvent(name: 'optimisation', value: newOptValue)
    sendEvent(name: 'windowFunction', value: newWdfValue)
}

private calculateThermostatStatus() {
    logMessage("debug", "${device.label}: calculateThermostatStatus()")
    def newThermostatStatus = ''
    def thermostatModeDisp = formatThermostatModeForDisp(device.currentValue('thermostatMode'))
    def setpoint = device.currentValue('thermostatSetpoint')
    
    if ('Off' == thermostatModeDisp) {
        newThermostatStatus = 'Off'
    }
    else if('heating' == device.currentValue('thermostatOperatingState')) {
        newThermostatStatus = "Heating to ${setpoint}° (${thermostatModeDisp})"
    }
    else {
        newThermostatStatus = "Idle (${thermostatModeDisp})"
    }
    sendEvent(name: 'thermostatStatus', value: newThermostatStatus)
}

private calculateThermostatSetpointStatus() {
    logMessage("debug", "${device.label}: calculateThermostatSetpointStatus()")
    def newThermostatSetpointStatus = ''
    def setpointMode = device.currentValue('thermostatSetpointMode')
    
    if ('off' == device.currentValue('thermostatMode')) {
        newThermostatSetpointStatus = 'Off'
    }
    else if ('away' == device.currentValue('thermostatMode')) {
        newThermostatSetpointStatus = 'Away'
    }
    else if ('active' == device.currentValue('optimisation')) {
        newThermostatSetpointStatus = 'Optimisation Active'
    }
    else if ('active' == device.currentValue('windowFunction')) {
        newThermostatSetpointStatus = 'Window Function Active'
    }
    else if ('followSchedule' == setpointMode) {
        newThermostatSetpointStatus = 'Following Schedule'
    }
    else if ('permanentOverride' == setpointMode) {
        newThermostatSetpointStatus = 'Permanent'
    }
    else {
        def untilStr = device.currentValue('thermostatSetpointUntil')
        if (untilStr) {
            def untilDate = new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", untilStr) 
            def untilDisp = ''
            if (untilDate.format("u") == new Date().format("u")) {
                untilDisp = untilDate.format("HH:mm", location.timeZone)
            }
            else {
                untilDisp = untilDate.format("HH:mm 'on' EEEE", location.timeZone)
            }
            newThermostatSetpointStatus = "Temporary Until ${untilDisp}"
        }
        else {
            newThermostatSetpointStatus = "Temporary"
        }
    }
    sendEvent(name: 'thermostatSetpointStatus', value: newThermostatSetpointStatus)
}
