definition(
    name: "Heating Mode Sync",
    namespace: "tljungberg",
    author: "Tobey Ljungberg",
    description: "Evohome Presence Sync",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage", title: "Preferences", install: true, uninstall: true) {
        section {
            paragraph "Select the Evo Home zone that will be triggered when away mode is set."
            input "syncdevice", "capability.temperatureMeasurement", title: "EvoHome Pilot Zone:", required: true
        }
    }
}

def installed() {
    log.debug "App installed with settings: ${settings}"
    initialize ()
}

def updated() {
    log.debug "App updated, updating settings"
    initialize()
}

def uninstalled() {
    log.debug "App uninstalled, clearing subscriptions"
    unsubscribe()
}

def initialize() {
    log.debug "Current mode is: ${location.mode}"
    log.debug "Current Evohome Mode is: ${syncdevice.currentValue("thermostatMode")}"
    log.debug "Clearing old subscription"
    unsubscribe()
    subscribe (location, "mode", modeEventHandler)
}

def modeEventHandler(evt) {
    log.debug "Mode changed to: ${location.mode}"
    if (location.mode == "Away") {
    if (syncdevice.currentValue("thermostatMode") == "off") {
    log.debug "Skipping heating mode change as heating mode is ${syncdevice.currentValue("thermostatMode")}"
    } else if (syncdevice.currentValue("thermostatMode") == "auto") {
        log.debug "Heating mode is ${syncdevice.currentValue("thermostatMode")} and mode changed to ${location.mode}, setting heating to away."
        syncdevice.setThermostatMode('away')
    }
    } else if (location.mode != "Away") {
    if (syncdevice.currentValue("thermostatMode") == "off") {
    log.debug "Skipping heating mode change as heating mode is ${syncdevice.currentValue("thermostatMode")}"
    } else if (syncdevice.currentValue("thermostatMode") == "away") {
        log.debug "Heating mode is ${syncdevice.currentValue("thermostatMode")} and mode changed to ${location.mode}, setting heating to auto."
        syncdevice.setThermostatMode('auto')
    }
}
}