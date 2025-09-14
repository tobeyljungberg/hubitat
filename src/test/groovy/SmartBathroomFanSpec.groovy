import spock.lang.Specification

class SmartBathroomFanSpec extends Specification {
    def script
    DummyFanSwitch fanSwitch

    def setup() {
        def cl = new GroovyClassLoader()
        script = cl.parseClass(new File('SmartBathroomFan/smart-bathroom-fan-child.groovy')).newInstance()
        script.log = new TestLogger()
        fanSwitch = new DummyFanSwitch()
        script.fanSwitch = fanSwitch
        script.state = [ambientHumidity: [40, 40, 40], fanOn: null, timestamp: 0]
        script.enabled = true
        script.metaClass.runIn = { time, func -> } // no-op for scheduling
        script.metaClass.now = { 0L }
        script.metaClass.propertyMissing = { String name ->
            if (name == 'checkManualOverride') { return { -> } }
        }
    }

    static class DummyFanSwitch {
        String currentSwitch = 'off'
        boolean onCalled = false
        boolean offCalled = false
        def on() { onCalled = true; currentSwitch = 'on' }
        def off() { offCalled = true; currentSwitch = 'off' }
    }

    static class TestLogger {
        List entries = []
        def debug(msg) { entries << [level: 'debug', msg: msg] }
        def info(msg)  { entries << [level: 'info', msg: msg] }
        def warn(msg)  { entries << [level: 'warn', msg: msg] }
        def error(msg) { entries << [level: 'error', msg: msg] }
    }

    def "eventHandler turns fan on when humidity high"() {
        given:
        script.humidityHigh = 10
        script.humidityLow = 5
        script.fanDelay = null
        def evt = [value: '55']

        when:
        script.eventHandler(evt)

        then:
        fanSwitch.onCalled
        script.state.fanOn != null
    }

    def "fanSwitchHandler activates manual override"() {
        given:
        script.manualOverrideTime = 5
        script.metaClass.now = { 1000L }
        def evt = [value: 'on']

        when:
        script.fanSwitchHandler(evt)

        then:
        script.state.manualOverrideExpiration == 1000L + 5 * 60000
    }
}

