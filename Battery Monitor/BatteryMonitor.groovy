definition(
    name: "Simple Battery Monitor",
    namespace: "tljungber",
    author: "Tobey Ljungberg",
    description: "Simple battery app",
    category: "Utility",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage", title: "My Page", install: true, uninstall: true) {
        section {
            paragraph "Hello, world!"
        }
    }
}

def installed() {
    log.trace "installed()"
}

def updated() {
    log.trace "updated()"
}

def uninstalled() {}