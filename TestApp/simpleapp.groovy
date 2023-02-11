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
    }
}

def installed() {
    log.trace "installed()"
}

def updated() {
    log.trace "updated()"
}

def uninstalled() {}