import common.LoggingUtils

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
        section("Logging") {
            input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
            input name: "traceEnable", type: "bool", title: "Enable trace logging", defaultValue: false
        }
    }
}

def installed() {
    logTrace "installed()"
    if (logEnable || traceEnable) runIn(1800, logsOff)
}

def updated() {
    logTrace "updated()"
    if (logEnable || traceEnable) runIn(1800, logsOff)
}

def uninstalled() {}