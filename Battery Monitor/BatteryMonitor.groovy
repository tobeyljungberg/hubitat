definition(
    name: "Simple Battery Monitor",
    namespace: "tobeyljungberg",
    author: "Tobey Ljungberg",
    descriptiion: "A simple app to monitor battery levels",
    category: "Utility",
    iconUrl: "",
    iconX2Url: "",
)

preference {
    page(name: "mainPage", title: "Settings", install: true, uninstall: true) {
        section {
            paragraph "Test!"
        }
    }
}

def installed() {
    log.trace "installed()"
}

def updated() {
    log.trace "updated()"
}

def uninstalled() {
    log.trace "uninstalled()"
}