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
        section("Enable?") {
            input "enabled", "bool", title: "Enable this SmartApp?"
        }
        section("Debug") {
            input "debugMode", "bool", title: "Enable debug logging?", required: true, defaultValue: false
        }
    }
}
