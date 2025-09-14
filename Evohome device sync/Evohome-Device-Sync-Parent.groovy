
definition(
    name:"Evohome Device Sync",
    namespace:"tljungberg",
    author:"Tobey Ljungberg",
    description: "Parent app for Evohome Device Sync",
    category: "Utility",
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

def updated() {
    logMessage("debug", "Updated with settings: ${settings}")
    initialize()
}

def initialize() {
    setVersion()
    logMessage("info", "Evohome Device Sync Initialized")
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
                app(name: "EvoHomeDeviceSync", appName: "Evohome Device Sync Child", namespace: "tljungberg", title: "Add a new sync app", multiple: true)
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
    state.internalName = "EvoHomeDeviceSyncDevices"
    state.externalName = "Evhome Device Sync Devices"
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
