definition(
    name: "Evohome Device Sync Child",
    namespace: "tljungberg",
    author: "Tobey Ljungberg",
    description: "Child app for Evhome Device Sync",
    category: "My Apps",
    parent: "tljungberg:Evohome Device Sync",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png"
)

preferences {
    page(name: "prefPage", title:"", install: true, uninstall: true)
}

def prefPage() {
    def explanationText = "The evohome zone is the evohome device created by the evohome app, the virtual thermostat must be blank."

    dynamicPage(name: "prefPage", title: "Preferences", uninstall: true, install: true) {

        section("") {
            label title: "Enter a name for the child app", required: true
        }
        section("Device Selection") {
            input "evohomezone", "capability.temperatureMeasurement", title: "Evohome Zone:", required: true
            input "thermostat", "capability.thermostat", title: "Virtual Thermostat:", required: true
        }
    }
}

def installed() {
    log.debug "Device installed with settings: ${settings}"
    initialize()
}

def updated() {
    log.debug "Device settings updated"
    initialize()
}
def initialize() {
    log.debug "Clearing old subscriptons."
    unsubscribe()
    log.debug "Setting allowed states on virtual device."
    thermostat.setSupportedThermostatModes('["heat","off"]')
    log.debug "Populating virtual thermostat starting values."
    def temp = evohomezone.currentValue("temperature")
    def heatset = evohomezone.currentValue("heatingSetpoint")
    def thermmode = evohomezone.currentValue("thermostatMode")
    def thermopmode = evohomezone.currentValue("thermostatOperatingState")
    thermostat.setTemperature(temp)
    thermostat.setHeatingSetpoint(heatset)
    if (thermmode == "auto") {
            thermostat.setThermostatMode('heat')
        } else if (thermmode == "heat") {
            thermostat.setThermostatMode('heat')
        } else if (thermmode == "off") {
            thermostat.setThermostatMode('off')
    }
    thermostat.setThermostatOperatingState(thermopmode)
    log.debug "Creating event subscriptions."
    subscribe(evohomezone, "temperature", evohomeHandler)
    subscribe(evohomezone, "heatingSetpoint", evohomeHandler)
    subscribe(evohomezone, "thermostatMode", evohomeHandler)
    subscribe(evohomezone, "thermostatOperatingState", evohomeHandler)
    subscribe(thermostat, "heatingSetpoint", thermostatHandler)
    subscribe(thermostat, "thermostatMode", thermostatHandler)
}

def evohomeHandler(evt) {
    log.debug "evohomeHandler called with with event: name:${evt.name} source:${evt.source} value:${evt.value} isStateChange: ${evt.getIsStateChange()} isPhysical: ${evt.isPhysical()} isDigital: ${evt.isDigital()} data: ${evt.data} device: ${evt.device}"
    if (evt.name == "temperature") {
    thermostat.setTemperature(evt.value)
    } else if (evt.name == "heatingSetpoint") {
    thermostat.setHeatingSetpoint(evt.value)
    } else if (evt.name == "thermostatMode") {
        if (evt.value == "auto") {
            thermostat.setThermostatMode('heat')
        } else if (evt.value == "heat") {
            thermostat.setThermostatMode('heat')
        } else if (evt.value == "off") {
            thermostat.setThermostatMode('off')
    }
    } else if (evt.name == "thermostatOperatingState") {
        thermostat.setThermostatOperatingState(evt.value)
    }
}

def thermostatHandler(evt) {
    log.debug "thermostatHandler called with with event: name:${evt.name} source:${evt.source} value:${evt.value} isStateChange: ${evt.getIsStateChange()} isPhysical: ${evt.isPhysical()} isDigital: ${evt.isDigital()} data: ${evt.data} device: ${evt.device}"
    if (evt.name == "heatingSetpoint") {
            evohomezone.setHeatingSetpoint(evt.value)
        
    } else if (evt.name == "thermostatMode") {
        if (evt.value == "auto") {
            evohomezone.setThermostatMode('auto')
        } else if (evt.value == "heat") {
            evohomezone.setThermostatMode('auto')
        } else if (evt.value == "off") {
            evohomezone.setThermostatMode('off')
    }
    }
}