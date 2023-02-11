metadata {
    definition (name: "Tobeys Virtual Switch", namespace: "tljungberg", author: "Tobey Ljungberg") {
        capability "Actuator"
        capability "Switch"
}

    preference {
        //none in this driver
    }
}
def installed() {
    log.debug "installed()"
}

def updated() {
    log.debug "updated()"
}

def on() {
    sendEvent(name: "switch", value: "on", descriptionText: "${device.displayName} switch is on")
    }

def off() {
    sendEvent(name: "switch", value: "on", descriptionText: "${device.displayName} switch is off")
}

