definition(
    name: "My First App",
    namespace: "MyNamespace",
    author: "My Name",
    description: "A simple example app",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage", title: "My Page", install: true, uninstall: true) {
        section {
            paragraph "Hello, world!"
        }
        section("Logging Options") {
            input "prefLogLevel", "enum", title: "Log Level",
                options: ["error", "warn", "info", "debug"], defaultValue: "info",
                required: true, displayDuringSetup: true
        }
    }
}

def installed() {
    logMessage("debug", "installed()")
}

def updated() {
    logMessage("debug", "updated()")
}

def uninstalled() {}

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
