/**
 * IKEA Myggspray Motion Sensor (Matter)
 *
 * Adapted from the IKEA Vallhorn Zigbee driver while preserving user-facing behavior:
 * - motion
 * - illuminance
 * - battery
 * - health checks
 * - power source
 */
import groovy.transform.CompileStatic
import groovy.transform.Field
import groovy.time.TimeCategory

@Field static final String DRIVER_NAME = 'IKEA Myggspray Motion Sensor (Matter)'
@Field static final String DRIVER_VERSION = '1.0.0'

@Field static final Map<String, String> HEALTH_CHECK = [
    schedule: '0 0 0/1 ? * * *',
    thereshold: '43200'
]

metadata {
    definition(name: DRIVER_NAME, namespace: 'hubitat', author: 'Codex') {
        capability 'Configuration'
        capability 'Refresh'
        capability 'Sensor'
        capability 'MotionSensor'
        capability 'IlluminanceMeasurement'
        capability 'Battery'
        capability 'HealthCheck'
        capability 'PowerSource'

        // Matter adaptation using requested clusters: Identify, Descriptor, Occupancy Sensing
        fingerprint inClusters: '0003,001D,0406', controllerType: 'MAT', model: 'MYGGSPRAY Motion Sensor', manufacturer: 'IKEA of Sweden'

        attribute 'lastBattery', 'date'
        attribute 'healthStatus', 'enum', ['offline', 'online', 'unknown']
        attribute 'networkRejoinCount', 'number'
    }

    preferences {
        input(
            name: 'logLevel', type: 'enum', title: 'Log verbosity', required: true,
            description: 'Select what messages appear in the Logs section',
            options: ['1': 'Debug - log everything', '2': 'Info - log important events', '3': 'Warning - log events that require attention', '4': 'Error - log errors'],
            defaultValue: '1'
        )
    }
}

void installed() {
    log_warn 'Installing device ...'
    state.lastCx = DRIVER_VERSION
    state.lastTx = 0
    state.lastRx = 0
    state.networkRejoinCount = 0
}

void updated() {
    log_info '🎬 Saving preferences ...'
    unschedule()

    if (logLevel == null) {
        device.updateSetting('logLevel', [value: '1', type: 'enum'])
    }
    if (logLevel == '1') {
        runIn(1800, 'logsOff')
    }

    schedule(HEALTH_CHECK.schedule, 'healthCheck')
}

void logsOff() {
    log_info '⏲️ Automatically reverting log level to "Info"'
    device.updateSetting('logLevel', [value: '2', type: 'enum'])
}

void healthCheck() {
    log_debug '⏲️ Automatically running health check'
    String healthStatus = state.lastRx == 0 || state.lastRx == null
        ? 'unknown'
        : (now() - state.lastRx < Integer.parseInt(HEALTH_CHECK.thereshold) * 1000 ? 'online' : 'offline')
    utils_sendEvent name: 'healthStatus', value: healthStatus, type: 'physical', descriptionText: "Health status is ${healthStatus}"
}

void configure() {
    log_warn '⚙️ Configuring device ...'
    state.lastCx = DRIVER_VERSION
    state.lastTx = now()

    sendEvent name: 'healthStatus', value: 'online', descriptionText: 'Health status initialized to online'
    sendEvent name: 'checkInterval', value: 3600, unit: 'second', descriptionText: 'Health check interval is 3600 seconds'

    if (device.currentValue('powerSource', true) == null) {
        sendEvent name: 'powerSource', value: 'unknown', type: 'digital', descriptionText: 'Power source initialized to unknown'
    }

    if (device.currentValue('networkRejoinCount', true) == null) {
        utils_sendEvent name: 'networkRejoinCount', value: 0, descriptionText: 'network rejoin count initialized to zero', type: 'digital'
    }

    refresh()
}

void refresh() {
    log_info '🎬 Refreshing device state ...'
    state.lastTx = now()
}

void ping() {
    log_warn 'ping ...'
    runIn 5, 'pingExecute'
}

void pingExecute() {
    if (state.lastRx == 0 || state.lastRx == null) {
        log_info 'Did not send any messages since it was last configured'
        return
    }

    Date nowDate = new Date(Math.round(now() / 1000) * 1000)
    Date lastRx = new Date(Math.round((state.lastRx as long) / 1000) * 1000)
    String lastRxAgo = TimeCategory.minus(nowDate, lastRx).toString().replace('.000 seconds', ' seconds')
    log_info "Sent last message at ${lastRx.format('yyyy-MM-dd HH:mm:ss', location.timeZone)} (${lastRxAgo} ago)"

    Date thereshold = new Date(Math.round((state.lastRx as long) / 1000 + Integer.parseInt(HEALTH_CHECK.thereshold)) * 1000)
    String theresholdAgo = TimeCategory.minus(thereshold, lastRx).toString().replace('.000 seconds', ' seconds')
    log_info "Will be marked as offline if no message is received for ${theresholdAgo} (hardcoded)"

    String offlineMarkAgo = TimeCategory.minus(thereshold, nowDate).toString().replace('.000 seconds', ' seconds')
    log_info "Will be marked as offline if no message is received until ${thereshold.format('yyyy-MM-dd HH:mm:ss', location.timeZone)} (${offlineMarkAgo} from now)"
}

void parse(Object description) {
    log_debug "description=[${description}]"
    state.lastRx = now()

    if (state.lastCx != DRIVER_VERSION) {
        state.lastCx = DRIVER_VERSION
        configure()
    }

    if (device.currentValue('healthStatus', true) != 'online') {
        utils_sendEvent name: 'healthStatus', value: 'online', type: 'digital', descriptionText: 'Health status changed to online'
    }

    Map msg = normalizeMessage(description)
    if (msg.isEmpty()) {
        log_debug 'Ignored message: unsupported format'
        return
    }

    String type = state.containsKey('lastTx') && (now() - (state.lastTx as long) < 3000) ? 'digital' : 'physical'

    Integer clusterInt = toInt(msg.clusterInt ?: msg.cluster ?: msg.clusterId)
    Integer attrInt = toInt(msg.attrInt ?: msg.attrId ?: msg.attributeId)
    Integer commandInt = toInt(msg.commandInt ?: msg.command)
    String value = (msg.value ?: msg.rawValue)?.toString()?.toUpperCase()

    // Occupancy (0x0406 / attr 0x0000)
    if (clusterInt == 0x0406 && attrInt == 0x0000 && value != null) {
        String motion = (value in ['01', '1', 'TRUE']) ? 'active' : 'inactive'
        utils_sendEvent(name: 'motion', value: motion, type: 'physical', descriptionText: "Is ${motion}")
        utils_processedMessage 'Occupancy', "Occupancy/MeasuredValue=${value}"
        return
    }

    // Illuminance (commonly 0x0400 / attr 0x0000)
    if (clusterInt == 0x0400 && attrInt == 0x0000 && value != null) {
        Integer illuminanceRaw = safeHexToInt(value)
        if (illuminanceRaw == null) return
        if (illuminanceRaw == 0xFFFF) {
            log_warn 'Ignored invalid reported illuminance value: 0xFFFF'
            return
        }

        Integer lux = illuminanceRaw == 0 ? 0 : (int) Math.round(Math.pow(10d, (illuminanceRaw - 1) / 10000d))
        utils_sendEvent name: 'illuminance', value: lux, unit: 'lx', descriptionText: "Illuminance is ${lux} lux", type: type
        utils_processedMessage 'Illuminance', "Illuminance/MeasuredValue=${value}"
        return
    }

    // BatteryPercentageRemaining (0x0001 / attr 0x0021)
    if (clusterInt == 0x0001 && attrInt == 0x0021 && value != null) {
        if (value == 'FF') {
            log_warn 'Ignored invalid remaining battery percentage value: 0xFF'
            return
        }

        Integer raw = safeHexToInt(value)
        if (raw == null) return
        Integer percentage = (int) (raw / 2)
        Date lastBattery = new Date()
        utils_sendEvent name: 'battery', value: percentage, unit: '%', descriptionText: "Battery is ${percentage}% full", type: type
        utils_sendEvent name: 'lastBattery', value: lastBattery, descriptionText: "Last battery report time is ${lastBattery}", type: type
        utils_processedMessage 'Battery', "BatteryPercentage=${percentage}%"
        return
    }

    // PowerSource (0x0000 / attr 0x0007)
    if (clusterInt == 0x0000 && attrInt == 0x0007 && value != null) {
        String powerSource = 'unknown'
        switch (value) {
            case ['01', '02', '05', '06']:
                powerSource = 'mains'; break
            case '03':
                powerSource = 'battery'; break
            case '04':
                powerSource = 'dc'; break
        }
        utils_sendEvent name: 'powerSource', value: powerSource, type: 'digital', descriptionText: "Power source is ${powerSource}"
        utils_processedMessage 'PowerSource', "PowerSource=${value}"
        return
    }

    // Rejoin/commissioning heuristics
    if (clusterInt == 0x0013 || (clusterInt == 0x0000 && commandInt == 0x00)) {
        Integer networkRejoinCount = (device.currentValue('networkRejoinCount', true) ?: 0) + 1
        utils_sendEvent name: 'networkRejoinCount', value: networkRejoinCount, descriptionText: "Incremented network rejoin count to ${networkRejoinCount}", type: 'physical'
        utils_processedMessage 'Rejoin', "cluster=0x${Integer.toHexString(clusterInt)}"
        return
    }

    utils_processedMessage 'Ignored', "cluster=${msg.cluster ?: msg.clusterId}, attr=${msg.attrId ?: msg.attributeId}, value=${value}"
}

private Map normalizeMessage(Object description) {
    if (description instanceof Map) {
        return (Map) description
    }

    if (!(description instanceof String)) {
        return [:]
    }

    String desc = (String) description
    if (!desc.contains(':')) {
        return [:]
    }

    Map msg = [:]
    desc.split(',').each { String part ->
        List<String> kv = part.split(':', 2)
        if (kv.size() == 2) {
            msg[kv[0].trim()] = kv[1].trim()
        }
    }
    return msg
}

@CompileStatic
private Integer toInt(Object value) {
    if (value == null) return null
    if (value instanceof Integer) return (Integer) value

    String text = value.toString().trim()
    if (text.isEmpty()) return null
    try {
        if (text.startsWith('0x') || text.startsWith('0X')) {
            return Integer.parseInt(text.substring(2), 16)
        }
        if (text ==~ /^[0-9A-Fa-f]+$/ && text.length() <= 4) {
            return Integer.parseInt(text, 16)
        }
        return Integer.parseInt(text)
    } catch (Exception ignored) {
        return null
    }
}

@CompileStatic
private Integer safeHexToInt(String value) {
    if (value == null) return null
    String sanitized = value.replace('0x', '').replace('0X', '')
    try {
        return Integer.parseInt(sanitized, 16)
    } catch (Exception ignored) {
        log_warn "Ignored non-hex value: ${value}"
        return null
    }
}

private void utils_sendEvent(Map event) {
    boolean noInfo = event.remove('noInfo') == true
    if (!noInfo && (device.currentValue(event.name, true) != event.value || event.isStateChange)) {
        log_info "${event.descriptionText} [${event.type}]"
    } else {
        log_debug "${event.descriptionText} [${event.type}]"
    }
    sendEvent event
}

private void utils_processedMessage(String type, String details) {
    log_debug "▶ Processed message: type=${type}, ${details}"
}

private void log_debug(String message) {
    if (logLevel == '1') log.debug "${device.displayName} ${message.uncapitalize()}"
}

private void log_info(String message) {
    if (logLevel == null || logLevel <= '2') log.info "${device.displayName} ${message.uncapitalize()}"
}

private void log_warn(String message) {
    if (logLevel == null || logLevel <= '3') log.warn "${device.displayName} ${message.uncapitalize()}"
}

private void log_error(String message) {
    log.error "${device.displayName} ${message.uncapitalize()}"
}
