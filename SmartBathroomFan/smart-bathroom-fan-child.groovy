/**
 *  Smart Bathroom Fan
 *
 *  Copyright 2023 Tobey Ljungberg
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */


definition(
    name: "Smart Bathroom Fan",
    namespace: "tljungberg",
    author: "Tobey Ljungberg",
    description: "Turns on/off a switch (for an exhaust fan) based on humidity.",
    category: "My Apps",
    parent: "tljungberg:Smart Bathroom Fan Controllers",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png"
)


preferences {
    page(name: "prefPage", title:"", install: true, uninstall: true)
}

def prefPage() {
    def explanationText = "The threshold settings below are in percent above the 24-hour rolling average humidity detected by the sensor specified above."

    dynamicPage(name: "prefPage", title: "Preferences", uninstall: true, install: true) {
        section("") {
            label title: "Enter a name for child app", required: true
        }
        section("Device Selection") {
            input "sensor", "capability.relativeHumidityMeasurement", title: "Humidity sensor:", required: true
            input "fanSwitch", "capability.switch", title: "Fan switch", required: true
        }
        section("Thresholds") {
            paragraph(explanationText)
            input "humidityHigh", "number", title: "Turn fan on when room is this percent above average humidity:", required: true
            input "humidityLow", "number", title: "Turn fan off when room is this percent above average humidity:", required: true
            input "fanDelay", "number", title: "Turn fan off after this many minutes regardless of humidity:", required: false
        }
        section("Manual Override") {
            input "manualOverrideTime", "number", title: "Keep fan on for (minutes) after manual activation:", required: false
        }
        section("Enable?") {
            input "enabled", "bool", title: "Enable this SmartApp?"
        }
        section("Debug") {
            input "debugMode", "bool", title: "Enable debug logging?", required: true, defaultValue: false
        }
    }
}


def installed() {
    log "Installed with settings: ${settings}"
    state.ambientHumidity = []
    state.fanOn = null
    initialize()

    if (settings.debugMode) {
        log "Filling array with current humidity."
        fillHumidityArrayWithCurrent()
    }
}


def updated() {
    log "Updated with settings: ${settings}"
    if (!state.ambientHumidity) {
        log("Initializing array.")
        state.ambientHumidity = []
    }
    unsubscribe()
    initialize()
}


def initialize() {
    state.fanOn = null
    fanSwitch.off()
    subscribe(sensor, "humidity", eventHandler)
    subscribe(fanSwitch, "switch", fanSwitchHandler)  // <--- NEW subscription
    updateAmbientHumidity()
    createSchedule()
}


def updateState() {
    log("State updated!")
    state.timestamp = now()
}


def createSchedule() {
    try { unschedule() }
    catch (e) { log.warn("Hamster fell off the wheel.") }
    updateState()
    runEvery5Minutes(updateAmbientHumidity)
}


def poke() {
    updateAmbientHumidity()
    createSchedule()
}

def fanSwitchHandler(evt) {
    if (evt.value == "on") {
        // Check if the event was triggered physically (i.e. manually)
        if (evt.physical) {
            log("Fan manually turned on; enabling manual override.")
            // Set the manual override expiration time (in ms) if configured
            state.manualOverrideExpiration = manualOverrideTime ? now() + (manualOverrideTime * 60000) : null
            // Optionally, schedule a check when the override period expires
            if (manualOverrideTime) {
                runIn(manualOverrideTime * 60, checkManualOverride)
            }
        }
    } else if (evt.value == "off") {
        log("Fan manually turned off; clearing manual override.")
        state.manualOverrideExpiration = null
    }
}

def eventHandler(evt) {
    def eventValue = Double.parseDouble(evt.value.replace('%', ''))
    Float rollingAverage = state.ambientHumidity.sum() / state.ambientHumidity.size()

    if (eventValue >= (rollingAverage + humidityHigh) && fanSwitch.currentSwitch == "off" && enabled) {
        log("Humidity (${eventValue}) is more than ${humidityHigh}% above rolling average (${rollingAverage.round(1)}%).  Turning the fan ON.")
        state.fanOn = now()
        fanSwitch.on()
        // If auto-activated, schedule an auto-off using fanDelay
        if(fanDelay) {
            runIn(fanDelay * 60, fanOff)
        }
    } else if (eventValue <= (rollingAverage + humidityLow) && fanSwitch.currentSwitch == "on") {
        if (enabled) {
            // If a manual override is active, do not turn the fan off even if humidity is low
            if (state.manualOverrideExpiration && now() < state.manualOverrideExpiration) {
                log("Humidity is low but manual override is active; not turning fan off.")
            } else {
                log("Humidity (${eventValue}) is at most ${humidityLow}% above rolling average (${rollingAverage}%).  Turning the fan OFF.")
                fanOff()
            }
        } else {
            log("Humidity (${eventValue}) is at most ${humidityLow}% above rolling average (${rollingAverage}%).  APP is DISABLED, so not turning the fan OFF.")
        }
    } else if (state.fanOn != null && fanDelay && state.fanOn + fanDelay * 60000 <= now()){
        log("Fan timer elapsed. Turning the fan OFF.  Current humidity is ${eventValue}%.")
        fanOff()
    } else if (state.fanOn != null && fanSwitch.currentSwitch == "off") {
        log("Fan turned OFF by someone/something else.  Resetting.")
        state.fanOn = null
    }

    if (now() - state.timestamp > 360000) {
        log("Scheduler hamster died.  Spawning a new one.")
        poke()
    }
}

def fillHumidityArrayWithCurrent() {
    def q = state.ambientHumidity as Queue
    while (q.size() < 288) {
        q.add(sensor.currentHumidity)
    }
}

def updateAmbientHumidity() {
    updateState()

    // Pause updates if the fan is on (regardless of how it was activated)
    if (fanSwitch.currentSwitch == "on") {
        log("Fan is on, pausing humidity updates.")
        return
    }

    def q = state.ambientHumidity as Queue
    q.add(sensor.currentHumidity)

    while (q.size() > 288) {
        q.poll()
    }
    state.ambientHumidity = q

    Float rollingAverage = state.ambientHumidity.sum() / state.ambientHumidity.size()
    Float triggerPoint = rollingAverage + humidityHigh
    log("Rolling average: ${rollingAverage.round(1)}% - Currently ${sensor.currentHumidity}% - Trigger at ${triggerPoint.round(1)}%.")
}

def checkManualOverride() {
    log("Manual override period expired.")
    state.manualOverrideExpiration = null
    // Optionally, re-check humidity to decide if the fan should be turned off immediately
    def currentHumidity = Double.parseDouble(sensor.currentHumidity.toString().replace('%', ''))
    Float rollingAverage = state.ambientHumidity.sum() / state.ambientHumidity.size()
    if (currentHumidity <= (rollingAverage + humidityLow) && fanSwitch.currentSwitch == "on") {
         log("After manual override expired, humidity is low; turning fan off.")
         fanOff()
    } else {
         log("After manual override expired, humidity is still high; fan remains on.")
    }
}

def fanOff() {
    log("Turning fan OFF due to timer.")
    state.fanOn = null
    fanSwitch.off()
}

def log(msg) {
	log.debug(msg)
}

def setVersion(){
		state.version = "1.0"
		state.internalName = "SmartBathroomFan"
    state.externalName = "Smart Bathroom Fan"
}