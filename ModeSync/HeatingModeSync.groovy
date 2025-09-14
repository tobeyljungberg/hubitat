definition(
    name: "Heating Mode Sync",
    namespace: "tljungberg",
    author: "Tobey Ljungberg",
    description: "Allows you to change the EvoHome heating mode based on the hub mode.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage", title: "Preferences", install: true, uninstall: true) {
        section("Zone Selection") {
            paragraph "Select the Evo Home zone that will be triggered when away mode is set."
            input "syncdevice", "capability.temperatureMeasurement", title: "EvoHome Pilot Zone:", required: true
        }
        section("Location Mode Configuration") {
            paragraph "For this automation to work you need to have the location mode set to Away when you are away. This can be done manually or by using another SmartApp."
        }
        section("Mode Restore Option") {
            paragraph "Decide what heating mode should be restored when returning from Away. If enabled, it will restore the previous mode; if disabled, it will default to 'auto'."
            input "restoreModePreference", "bool", title: "Restore previous heating mode?", defaultValue: true, required: true
        }
        section("Logging Options") {
            input "prefLogLevel", "enum", title: "Log Level", 
                options: ["error", "warn", "info", "debug"], defaultValue: "info", 
                required: true, displayDuringSetup: true
        }
    }
}

def installed() {
    logMessage("debug", "App installed with settings: ${settings}")
    initialize()
}

def updated() {
    logMessage("debug", "App updated, updating settings")
    initialize()
}

def uninstalled() {
    logMessage("debug", "App uninstalled, clearing subscriptions")
    unsubscribe()
}

def initialize() {
    logMessage("info", "Current location mode is: ${location.mode}")
    logMessage("info", "Current Evohome mode is: ${syncdevice.currentValue("thermostatMode")}")
    logMessage("debug", "Clearing old subscriptions")
    unsubscribe()
    subscribe(location, "mode", modeEventHandler)

    addInUseGlobalVar("EvohomeRequestPoll")
	addInUseGlobalVar("EvohomeLastPolled")
	
    logMessage("info", "App started")
}

def modeEventHandler(evt) {
    logMessage("debug", "Location mode was changed to: ${location.mode}")
    try {
        def currentMode = syncdevice.currentValue("thermostatMode")
        if (location.mode == "Away") {
            if (currentMode == "off" || currentMode == "away") {
                logMessage("info", "Skipping heating mode change as heating mode is ${currentMode}")
            } else {
                // Tell evohome connect to poll for latest values
                //Get current timestamp
                def preTimeObj = getGlobalVar("EvohomeLastPolled")
				def prevalue = preTimeObj?.value?.toString()
                logMessage("debug", "preTime is ${prevalue}")
                //Trigger Poll
                setGlobalVar("EvohomeRequestPoll", "true")
                //Wait for Poll to complete
                pauseExecution(15000)
                //Compare values to see if poll has updated.
                def postTimeObj = getGlobalVar("EvohomeLastPolled")
				def postvalue = postTimeObj?.value?.toString()
                logMessage("debug", "postTime is ${postvalue}")
                if (prevalue < postvalue) {
                logMessage("debug", "Poll successful, updating heating mode")
                // Decide which mode to restore based on the config switch
                // Store the current mode before switching to away
                state.previousHeatingMode = currentMode
                logMessage("info", "Heating mode was ${currentMode} and location mode changed to Away. Storing previous mode and setting heating to away.")
                syncdevice.setThermostatMode('away')
                }
                else if (prevalue >= postvalue) {
                        logMessage("error", "Poll failed, not updating heating mode")
                    }
            }
        } else { // Not Away
            if (currentMode == "off") {
                logMessage("info", "Skipping heating mode change as heating mode is ${currentMode}")
            } else if (currentMode == "away") {
                
                def restoreMode = restoreModePreference ? (state.previousHeatingMode ?: 'auto') : 'auto'
                logMessage("info", "Heating mode was away and location mode changed to ${location.mode}. Setting heating to ${restoreMode}.")
                syncdevice.setThermostatMode(restoreMode)
                state.previousHeatingMode = null
                }
                    
            }
        }
     catch (Exception e) {
        logMessage("error", "Error in modeEventHandler: ${e.message}")
    }
}

// Logging helper function modeled after Evohome Heating Zone
private logMessage(String level, String msg) {
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
