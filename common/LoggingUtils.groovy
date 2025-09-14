library (
    name: "LoggingUtils",
    namespace: "common",
    author: "Hubitat",
    description: "Shared logging helpers",
    category: "utilities",
    documentationLink: "",
    version: "1.0.0"
)

void logDebug(msg) {
    if (settings?.logEnable) {
        log.debug "$msg"
    }
}

void logInfo(msg) {
    if (settings?.logEnable) {
        log.info "$msg"
    }
}

void logWarn(msg) {
    if (settings?.logEnable) {
        log.warn "$msg"
    }
}

void logError(msg) {
    if (settings?.logEnable) {
        log.error "$msg"
    }
}

void logTrace(msg) {
    if (settings?.traceEnable) {
        log.trace "$msg"
    }
}

void logsOff() {
    log.warn "debug logging disabled..."
    if (device) {
        device.updateSetting("logEnable", [value: "false", type: "bool"])
        device.updateSetting("traceEnable", [value: "false", type: "bool"])
    } else {
        app?.updateSetting("logEnable", [value: "false", type: "bool"])
        app?.updateSetting("traceEnable", [value: "false", type: "bool"])
    }
}
