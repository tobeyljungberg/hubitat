
definition(
    name:"Smart Bathroom Fan Controllers",
    namespace:"tljungberg",
    author:"Tobey Ljungberg",
    description: "Parent app for Smart Bathroom Fan",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
)


preferences {
    page name: "mainPage", title: "", install: true, uninstall: true
}

def installed() {
    logMessage("debug", "Installed using settings: ${settings}")
    initialize()
}

def initialize() {
    setVersion()
    logMessage("info", "Smart Bathroom Fan Parent Initialized")
}


def mainPage() {
    dynamicPage(name: "mainPage") {
        installCheck()

        if (state.appInstalled == 'COMPLETE') {
            display()

            section("Logging Options") {
                input "prefLogLevel", "enum", title: "Log Level",
                    options: ["error", "warn", "info", "debug"], defaultValue: "info",
                    required: true, displayDuringSetup: true
            }

            section ("") {
                app(name: "smartBathroomFanApp", appName: "Smart Bathroom Fan", namespace: "tljungberg", title: "Add a new Smart Bathroom Fan", multiple: true)
            }

        }
    }
}


def display() {
    section{paragraph "Version: $state.version"}
}


def installCheck() {
    state.appInstalled = app.getInstallationState()
    if(state.appInstalled != 'COMPLETE'){
        section{paragraph "Please hit 'Done' to install '${app.label}' parent app "}
    } else {
        logMessage("info", "Parent Installed OK")
    }
}


def setVersion() {
                state.version = "1.0"
    state.internalName = "SmartBathroomFanControllers"
    state.externalName = "Smart Bathroom Fan Controllers"
}

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
