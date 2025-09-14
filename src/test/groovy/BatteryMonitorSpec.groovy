import spock.lang.Specification
import spock.lang.Ignore

class BatteryMonitorSpec extends Specification {

    def script

    def setup() {
        def cl = new GroovyClassLoader()
        script = cl.parseClass(new File('Battery Monitor/BatteryMonitor.groovy')).newInstance()
        script.log = new TestLogger()
    }

    static class TestLogger {
        List entries = []
        def trace(msg) { entries << [level: 'trace', msg: msg] }
        def debug(msg) { entries << [level: 'debug', msg: msg] }
        def info(msg)  { entries << [level: 'info', msg: msg] }
        def warn(msg)  { entries << [level: 'warn', msg: msg] }
        def error(msg) { entries << [level: 'error', msg: msg] }
    }

    def "installed emits trace log"() {
        when:
        script.installed()
        then:
        script.log.entries == [[level: 'trace', msg: 'installed()']]
    }

    def "updated emits trace log"() {
        when:
        script.updated()
        then:
        script.log.entries == [[level: 'trace', msg: 'updated()']]
    }

    @Ignore("TODO: implement battery threshold alert functionality")
    def "triggers alert when battery below threshold"() {
        expect:
        true
    }
}
