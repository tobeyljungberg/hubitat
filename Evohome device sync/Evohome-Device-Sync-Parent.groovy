
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
    log.debug "Installed using settings: ${settings}"
    initialize()
}

def initialize() {
    setVersion()
    log.info "Evohome Device Sync Initialized"
}


def mainPage() {
    dynamicPage(name: "mainPage") {
        installCheck()

        if (state.appInstalled == 'COMPLETE') {
            display()

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
        log.info "Parent Installed OK"
    }
}


def setVersion() {
		state.version = "1.0"
    state.internalName = "EvoHomeDeviceSyncDevices"
    state.externalName = "Evhome Device Sync Devices"
}