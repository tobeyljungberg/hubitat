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
            input "enableDebugLogging", "bool", title: "Enable Debug Logging?", defaultValue: false, required: true
        }
    }
}

// Logging helper function
private logIt(String level, String msg) {
    if(level == "debug") {
        if (enableDebugLogging) {
            log.debug msg
        }
    } else if (level == "info") {
        log.info msg
    } else if (level == "warn") {
        log.warn msg
    } else if (level == "error") {
        log.error msg
    }
}

def installed() {
    logIt("debug", "App installed with settings: ${settings}")
    initialize()
}

def updated() {
    logIt("debug", "App updated, updating settings")
    initialize()
}

def uninstalled() {
    logIt("debug", "App uninstalled, clearing subscriptions")
    unsubscribe()
}

def initialize() {
    logIt("debug", "Current location mode is: ${location.mode}")
    logIt("debug", "Current Evohome mode is: ${syncdevice.currentValue("thermostatMode")}")
    logIt("debug", "Clearing old subscriptions")
    unsubscribe()
    subscribe(location, "mode", modeEventHandler)
}

def modeEventHandler(evt) {
    logIt("debug", "Location mode was changed to: ${location.mode}")
    try {
        def currentMode = syncdevice.currentValue("thermostatMode")
        if (location.mode == "Away") {
            if (currentMode == "off" || currentMode == "away") {
                logIt("info", "Skipping heating mode change as heating mode is ${currentMode}")
            } else {
                // Store the current mode before switching to away
                state.previousHeatingMode = currentMode
                logIt("info", "Heating mode was ${currentMode} and location mode changed to Away. Storing previous mode and setting heating to away.")
                syncdevice.setThermostatMode('away')
            }
        } else { // Not Away
            if (currentMode == "off") {
                logIt("info", "Skipping heating mode change as heating mode is ${currentMode}")
            } else if (currentMode == "away") {
                // Decide which mode to restore based on the config switch
                def restoreMode = restoreModePreference ? (state.previousHeatingMode ?: 'auto') : 'auto'
                logIt("info", "Heating mode was away and location mode changed to ${location.mode}. Setting heating to ${restoreMode}.")
                syncdevice.setThermostatMode(restoreMode)
                state.previousHeatingMode = null
            }
        }
    } catch (Exception e) {
        logIt("error", "Error in modeEventHandler: ${e.message}")
    }
}