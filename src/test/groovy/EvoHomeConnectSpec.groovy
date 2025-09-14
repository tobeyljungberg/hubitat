import spock.lang.Specification
import spock.lang.Unroll

class EvoHomeConnectSpec extends Specification {

    def script

    def setup() {
        def cl = new GroovyClassLoader()
        cl.parseClass('package groovyx.net.http\nclass HttpResponseException extends Exception {}')
        script = cl.parseClass(new File('EvoHome/EvoHomeConnect.groovy')).newInstance()
        script.atomicState = [:]
        script.settings = [:]
        script.location = [ timeZone: new java.util.SimpleTimeZone(0, 'UTC') ]
        script.log = new TestLogger()
        script.app = [ label: 'TestApp' ]
        Date.metaClass.format = { String pattern, TimeZone tz ->
            def df = new java.text.SimpleDateFormat(pattern)
            df.timeZone = tz
            df.format(delegate)
        }
        Date.metaClass.parse = { String pattern, String value ->
            def df = new java.text.SimpleDateFormat(pattern)
            df.parse(value)
        }
    }

    static class TestLogger {
        List entries = []
        def error(msg) { entries << [level: 'error', msg: msg] }
        def warn(msg)  { entries << [level: 'warn', msg: msg] }
        def info(msg)  { entries << [level: 'info', msg: msg] }
        def debug(msg) { entries << [level: 'debug', msg: msg] }
    }

    @Unroll
    def "logMessage with settings '#settingsLevel' and atomicState '#atomicLevel' logs '#msgLevel'? #shouldLog"() {
        given:
        script.settings.prefLogLevel = settingsLevel
        if (atomicLevel != null) {
            script.atomicState.logLevel = atomicLevel
        }

        when:
        script.logMessage(msgLevel, 'test')

        then:
        (script.log.entries*.level.contains(msgLevel)) == shouldLog

        where:
        settingsLevel | atomicLevel | msgLevel | shouldLog
        'info'        | null        | 'debug'  | false
        'debug'       | null        | 'debug'  | true
        'info'        | 'debug'     | 'debug'  | true
        'debug'       | 'warn'      | 'info'   | false
    }

    @Unroll
    def "formatThermostatMode converts '#input' to '#expected'"() {
        when:
        def result = script.formatThermostatMode(input)
        then:
        result == expected
        script.log.entries.empty
        where:
        input         | expected
        'Auto'        | 'auto'
        'AutoWithEco' | 'economy'
        'Away'        | 'away'
        'Custom'      | 'custom'
        'DayOff'      | 'dayOff'
        'HeatingOff'  | 'off'
    }

    def "formatThermostatMode logs error for unknown modes"() {
        when:
        def result = script.formatThermostatMode('Silly')
        then:
        result == 'silly'
        script.log.entries*.level == ['error']
    }

    @Unroll
    def "formatTemperature converts #input to '#expected'"() {
        when:
        def result = script.formatTemperature(input)
        then:
        result == expected
        where:
        input  | expected
        20     | '20.0'
        21.24  | '21.2'
        '22.26'| '22.3'
    }

    def "formatTemperature throws NumberFormatException for invalid input"() {
        when:
        script.formatTemperature('abc')
        then:
        thrown(NumberFormatException)
    }

    def createSchedule() {
        def tz = script.location.timeZone.toZoneId()
        def now = java.time.ZonedDateTime.now(tz)
        def formatter = java.time.format.DateTimeFormatter.ofPattern('EEEE')
        String today = now.format(formatter)
        String yesterday = now.minusDays(1).format(formatter)
        String tomorrow = now.plusDays(1).format(formatter)
        def schedule = [
            dailySchedules: [
                [dayOfWeek: today, switchpoints: [
                    [timeOfDay: '00:00:00', temperature: 20],
                    [timeOfDay: '23:59:59', temperature: 22]
                ]],
                [dayOfWeek: yesterday, switchpoints: [
                    [timeOfDay: '00:00:00', temperature: 19]
                ]],
                [dayOfWeek: tomorrow, switchpoints: [
                    [timeOfDay: '00:00:00', temperature: 21]
                ]]
            ]
        ]
        return schedule
    }

    def "getCurrentSwitchpoint returns current switchpoint"() {
        given:
        def schedule = createSchedule()
        when:
        def result = script.getCurrentSwitchpoint(schedule)
        then:
        result.temperature == 20
        new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", result.time).format('HH:mm:ss', script.location.timeZone) == '00:00:00'
    }

    def "getNextSwitchpoint returns next switchpoint"() {
        given:
        def schedule = createSchedule()
        when:
        def result = script.getNextSwitchpoint(schedule)
        then:
        result.temperature == 22
        new Date().parse("yyyy-MM-dd'T'HH:mm:ssXX", result.time).format('HH:mm:ss', script.location.timeZone) == '23:59:59'
    }
}
