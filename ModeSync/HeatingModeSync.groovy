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
        if (location.mode == "Away") {
            if (syncdevice.currentValue("thermostatMode") == "off") {
                logIt("info", "Skipping heating mode change as heating mode is ${syncdevice.currentValue("thermostatMode")}")
            } else if (syncdevice.currentValue("thermostatMode") in ["auto", "custom"]) {
                logIt("info", "Heating mode is ${syncdevice.currentValue("thermostatMode")} and location mode changed to ${location.mode}, setting heating to away.")
                syncdevice.setThermostatMode('away')
            }
        } else { // Any mode that is not "Away"
            if (syncdevice.currentValue("thermostatMode") == "off") {
                logIt("info", "Skipping heating mode change as heating mode is ${syncdevice.currentValue("thermostatMode")}")
            } else if (syncdevice.currentValue("thermostatMode") == "away") {
                logIt("info", "Heating mode is ${syncdevice.currentValue("thermostatMode")} and location mode changed to ${location.mode}, setting heating to auto.")
                syncdevice.setThermostatMode('auto')
            }
        }
    } catch (Exception e) {
        logIt("error", "Error in modeEventHandler: ${e.message}")
    }
}