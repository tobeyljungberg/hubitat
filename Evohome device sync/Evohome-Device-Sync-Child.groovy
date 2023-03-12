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
        section("Device Type") {
            paragraph "Leave switch off if your virtual device is a thermostat, this will create a two way sync app. Leave on if your target device is a virtual temp sensor, this will create a one way sync app."
            input "istempsense", "bool", title: "Target device type Tempsensor?"
        }
        section("Device Selection") {
            input "evohomezone", "capability.temperatureMeasurement", title: "Evohome Zone:", required: true
            input "vdevicetarget", "capability.temperatureMeasurement", title: "Virtual Device Target:", required: true
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
    if (istempsense == false) {
        log.debug "Target device is thermostat."
    } else if (istempsense == true) {
        log.debug "Target device is temperature sensor."
    }
    log.debug "Setting allowed states on virtual device."
    if (istempsense == false) {
    vdevicetarget.setSupportedThermostatModes('["heat","off"]')
    } else if (istempsense == true) {
        log.debug "Skipping state setting as target is not thermostat."
    }
    log.debug "Populating virtual device starting values."
    def temp = evohomezone.currentValue("temperature")
    def heatset = evohomezone.currentValue("heatingSetpoint")
    def thermmode = evohomezone.currentValue("thermostatMode")
    def thermopmode = evohomezone.currentValue("thermostatOperatingState")
    if (istempsense == false) {
    vdevicetarget.setTemperature(temp)
    vdevicetarget.setHeatingSetpoint(heatset)
    if (thermmode == "auto") {
            vdevicetarget.setThermostatMode('heat')
        } else if (thermmode == "heat") {
            vdevicetarget.setThermostatMode('heat')
        } else if (thermmode == "off") {
            vdevicetarget.setThermostatMode('off')
    }
    vdevicetarget.setThermostatOperatingState(thermopmode)
    } else if (istempsense == true) {
        vdevicetarget.setTemperature(temp)
    }
    log.debug "Creating event subscriptions."
    if (istempsense == false) {
    subscribe(evohomezone, "temperature", evohomeHandler)
    subscribe(evohomezone, "heatingSetpoint", evohomeHandler)
    subscribe(evohomezone, "thermostatMode", evohomeHandler)
    subscribe(evohomezone, "thermostatOperatingState", evohomeHandler)
    subscribe(thermostat, "heatingSetpoint", thermostatHandler)
    subscribe(thermostat, "thermostatMode", thermostatHandler)
    } else if (istempsense == true) {
    subscribe(evohomezone, "temperature", evohomeTempHandler)
    }
}

def evohomeHandler(evt) {
    log.debug "evohomeHandler called with with event: name:${evt.name} source:${evt.source} value:${evt.value} isStateChange: ${evt.getIsStateChange()} isPhysical: ${evt.isPhysical()} isDigital: ${evt.isDigital()} data: ${evt.data} device: ${evt.device}"
    if (evt.name == "temperature") {
    vdevicetarget.setTemperature(evt.value)
    } else if (evt.name == "heatingSetpoint") {
    vdevicetarget.setHeatingSetpoint(evt.value)
    } else if (evt.name == "thermostatMode") {
        if (evt.value == "auto") {
            vdevicetarget.setThermostatMode('heat')
        } else if (evt.value == "heat") {
            vdevicetarget.setThermostatMode('heat')
        } else if (evt.value == "off") {
            vdevicetarget.setThermostatMode('off')
    }
    } else if (evt.name == "thermostatOperatingState") {
        vdevicetarget.setThermostatOperatingState(evt.value)
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

def evohomeTempHandler(evt) {
    log.debug "evohomeTempHandler called with with event: name:${evt.name} source:${evt.source} value:${evt.value} isStateChange: ${evt.getIsStateChange()} isPhysical: ${evt.isPhysical()} isDigital: ${evt.isDigital()} data: ${evt.data} device: ${evt.device}"
    vdevicetarget.setTemperature(evt.value)
    
}