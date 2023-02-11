metadata {
   definition (name: "Custom Virtual Switch", namespace: "MyNamespace", author: "My Name") {
      capability "Actuator"
      capability "Switch"
   }

   preferences {
      // none in this driver
   }
}

def installed() {
   log.debug "installed()"
}

def updated() {
   log.debug "updated()"
}

def on() {
    // With a real device, you would normally send a Z-Wave/Zigbee/etc. command to the device here
    // For a virtual device, we are simply generating an event to make the "switch" attribute "on"
    // (with a real device, you would usually wait to hear back from the device in parse() before doing this)
    sendEvent(name: "switch", value: "on", descriptionText: "${device.displayName} switch is on")
}

def off() {
    // Same notes as for on() apply here...
    sendEvent(name: "switch", value: "off", descriptionText: "${device.displayName} switch is off")
}