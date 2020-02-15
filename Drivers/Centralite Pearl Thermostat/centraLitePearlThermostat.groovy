/**
 *	original port from jsconstantelos's ST handler
 */

import groovy.json.JsonOutput
import hubitat.zigbee.zcl.DataType
 
metadata {
	definition (name: "My Centralite Thermostat", namespace: "djdizzyd", author: "Bryan Copeland", importUrl: "https://raw.githubusercontent.com/djdizzyd/hubitat/master/Drivers/Centralite%20Pearl%20Thermostat/centraLitePearlThermostat.groovy") {
		capability "Actuator"
//        capability "Switch"
		capability "Temperature Measurement"
		capability "Thermostat"
		capability "Thermostat Mode"
		capability "Thermostat Fan Mode"
		capability "Thermostat Cooling Setpoint"
		capability "Thermostat Heating Setpoint"
		capability "Thermostat Operating State"
		capability "Configuration"
		capability "Battery"
		capability "Power Source"
		capability "Health Check"
		capability "Refresh"
		capability "Sensor"

        command "holdOn"
        command "holdOff"
	}
}

//*************
// parse events into clusters and attributes and do something with the data we received from the thermostat
//*************
def parse(String description) {
//	log.debug "Parse description : $description"
    if ((description?.startsWith("catchall:")) || (description?.startsWith("read attr -"))) {
		def descMap = zigbee.parseDescriptionAsMap(description)
        def tempScale = location.temperatureScale
//		log.debug "Desc Map : $descMap"
        // TEMPERATURE
		if (descMap.cluster == "0201" && descMap.attrId == "0000") {
        	if (descMap.value != null) {
            	def trimvalue = descMap.value[-4..-1]
                def celsius = Integer.parseInt(trimvalue, 16) / 100
                def fahrenheit = String.format("%3.1f",celsiusToFahrenheit(celsius))
                if (tempScale == "F") {
                    sendEvent("name": "temperature", "value": fahrenheit, "unit": temperatureScale, "displayed": true)
                    log.debug "TEMPERATURE is : ${fahrenheit}${temperatureScale}"
                } else {
                    sendEvent("name": "temperature", "value": celsius, "unit": temperatureScale, "displayed": true)
                    log.debug "TEMPERATURE is : ${celsius}${temperatureScale}"
                }
            }
        // COOLING SETPOINT
		} else if (descMap.cluster == "0201" && descMap.attrId == "0011") {
        	if (descMap.value != null) {
            	def trimvalue = descMap.value[-4..-1]
                def celsius = Integer.parseInt(trimvalue, 16) / 100
                def fahrenheit = String.format("%3.1f",celsiusToFahrenheit(celsius))
                if (tempScale == "F") {
                    sendEvent("name": "coolingSetpoint", "value": fahrenheit, "unit": temperatureScale, "displayed": true)
                    log.debug "COOLING SETPOINT is : ${fahrenheit}${temperatureScale}"
                } else {
                    sendEvent("name": "coolingSetpoint", "value": celsius, "unit": temperatureScale, "displayed": true)
                    log.debug "COOLING SETPOINT is : ${celsius}${temperatureScale}"
                }
            }
        // HEATING SETPOINT
		} else if (descMap.cluster == "0201" && descMap.attrId == "0012") {
        	if (descMap.value != null) {
            	def trimvalue = descMap.value[-4..-1]
                def celsius = Integer.parseInt(trimvalue, 16) / 100
                def fahrenheit = String.format("%3.1f",celsiusToFahrenheit(celsius))
                if (tempScale == "F") {
                    sendEvent("name": "heatingSetpoint", "value": fahrenheit, "unit": temperatureScale, "displayed": true)
                    log.debug "HEATING SETPOINT is : ${fahrenheit}${temperatureScale}"
                } else {
                    sendEvent("name": "heatingSetpoint", "value": celsius, "unit": temperatureScale, "displayed": true)
                    log.debug "HEATING SETPOINT is : ${celsius}${temperatureScale}"
                }
            }
        // THERMOSTAT MODE
		} else if (descMap.cluster == "0201" && descMap.attrId == "001c") {
        	def trimvalue = descMap.value[-2..-1]
			def modeValue = getModeMap()[trimvalue]
            sendEvent("name": "thermostatMode", "value": modeValue, "displayed": true)
            log.debug "THERMOSTAT MODE is : ${modeValue}"
        // THERMOSTAT FAN MODE
		} else if (descMap.cluster == "0202" && descMap.attrId == "0000") {
        	def trimvalue = descMap.value[-2..-1]
			def modeValue = getFanModeMap()[trimvalue]
            sendEvent("name": "thermostatFanMode", "value": modeValue, "displayed": true)
            log.debug "THERMOSTAT FAN MODE is : ${modeValue}"
        // BATTERY LEVEL
		} else if (descMap.cluster == "0001" && descMap.attrId == "0020") {
            def vBatt = Integer.parseInt(descMap.value,16) / 10
            def batteryValue =  ((vBatt - 2.1) / (3.0 - 2.1) * 100) as int
            sendEvent("name": "battery", "value": batteryValue, "displayed": true)
            log.debug "BATTERY LEVEL is : ${batteryValue}"
        // THERMOSTAT OPERATING STATE
		} else if (descMap.cluster == "0201" && descMap.attrId == "0029") {
        	def trimvalue = descMap.value[-4..-1]
            def stateValue = getThermostatOperatingState()[trimvalue]
            sendEvent("name": "thermostatOperatingState", "value": stateValue, "displayed": true)
            log.debug "THERMOSTAT OPERATING STATE is : ${stateValue}"
        // THERMOSTAT HOLD MODE
		} else if (descMap.cluster == "0201" && descMap.attrId == "0023") {
        	def trimvalue = descMap.value[-2..-1]
            def modeValue = getHoldModeMap()[trimvalue]
            sendEvent("name": "thermostatHoldMode", "value": modeValue, "displayed": true)
            log.debug "THERMOSTAT HOLD MODE is : ${modeValue}"
        // POWER SOURCE
		} else if (descMap.cluster == "0000" && descMap.attrId == "0007") {
        	getPowerSourceMap(descMap.value)
		} else {
//        	log.debug "UNKNOWN Cluster and Attribute : $descMap"
        }
	} else {
    	log.debug "UNKNOWN data from device : $description"
    }
}

def getCoolingSetpointRange() {
	(getTemperatureScale() == "C") ? [10, 35] : [50, 95]
}
def getHeatingSetpointRange() {
	(getTemperatureScale() == "C") ? [7, 32] : [45, 90]
}

def getModeMap() {
	[
    "00":"off",
    "01":"auto",
    "03":"cool",
    "04":"heat",
    "05":"emergency heat",
    "06":"precooling",
    "07":"fan only"
    ]
}

def getHoldModeMap() {
	[
    "00":"holdOff",
    "01":"holdOn"
    ]
}

def getPowerSourceMap(value) {
    if (value == "81") {
        sendEvent(name: "powerSource", value: "mains", "displayed": true)
        log.debug "POWER SOURCE is mains"
    } else {
      	sendEvent(name: "powerSource", value: "battery", "displayed": true)
        log.debug "POWER SOURCE is batteries"
    }
}

def getFanModeMap() {
	[
    "00":"off",
    "04":"on",
    "05":"auto"
    ]
}

def getThermostatOperatingState() {
	[
    "0000":"idle",
    "0001":"heating",
    "0002":"cooling",
    "0004":"Fan is Running",
    "0005":"heating",
    "0006":"cooling",
    "0008":"heating",
    "0009":"heating",
    "000A":"heating",
    "000D":"heating",
    "0010":"cooling",
    "0012":"cooling",
    "0014":"cooling",
    "0015":"cooling"
    ]
}

//********************************
// Send commands to the thermostat
//********************************

//Gets executed by a SmartApp via a virtual dimmer switch.  For example, "Alexa, set Downstairs Temperature to 72"
def setTemperature(value) {
    log.debug "Setting Temperature by a SmartApp to ${value}"
    def int desiredTemp = value.toInteger()
    if (device.currentValue("thermostatMode") == "heat") {
    	setHeatingSetpoint(desiredTemp)
	} else if (device.currentValue("thermostatMode") == "emergency heat") {   
        setHeatingSetpoint(desiredTemp)
	} else if (device.currentValue("thermostatMode") == "cool") {   
        setCoolingSetpoint(desiredTemp)
	} else {
    	log.debug "Can't adjust set point when unit isn't in heat, e-heat, or cool mode."
    }
}

def setHeatingSetpoint(degrees) {
	log.debug "Setting HEAT set point to ${degrees}"
    if (degrees != null) {
        def degreesInteger = Math.round(degrees)
        sendEvent("name": "heatingSetpoint", "value": degreesInteger)
        def celsius = (getTemperatureScale() == "C") ? degreesInteger : (fahrenheitToCelsius(degreesInteger) as Double).round(2)
        ["he wattr 0x${device.deviceNetworkId} 1 0x201 0x12 0x29 {" + hex(celsius * 100) + "}"]
    }
}

def setCoolingSetpoint(degrees) {
	log.debug "Setting COOL set point to ${degrees}"
    if (degrees != null) {
        def degreesInteger = Math.round(degrees)
        sendEvent("name": "coolingSetpoint", "value": degreesInteger)
        def celsius = (getTemperatureScale() == "C") ? degreesInteger : (fahrenheitToCelsius(degreesInteger) as Double).round(2)
        ["he wattr 0x${device.deviceNetworkId} 1 0x201 0x11 0x29 {" + hex(celsius * 100) + "}"]
    }
}

def setThermostatFanMode(mode) {
	if (state.supportedFanModes?.contains(mode)) {
		switch (mode) {
			case "on":
				fanOn()
				break
			case "auto":
				fanAuto()
				break
		}
	} else {
		log.debug "Unsupported fan mode $mode"
	}
}

def setThermostatMode(mode) {
	log.debug "set mode $mode (supported ${state.supportedThermostatModes})"
	if (state.supportedThermostatModes?.contains(mode)) {
		switch (mode) {
			case "heat":
				heat()
				break
			case "cool":
				cool()
				break
			case "auto":
				auto()
				break
			case "emergency heat":
				emergencyHeat()
				break
			case "off":
				offmode()
				break
		}
	} else {
		log.debug "Unsupported mode $mode"
	}
}

def setThermostatHoldMode() {
	def currentHoldMode = device.currentState("thermostatHoldMode")?.value
	def returnCommand
	switch (currentHoldMode) {
		case "holdOff":
			returnCommand = holdOn()
			break
		case "holdOn":
			returnCommand = holdOff()
			break
	}
	if(!currentHoldMode) { returnCommand = holdOff() }
	returnCommand
}

def offmode() {
	log.debug "Setting mode to OFF"
	sendEvent("name":"thermostatMode", "value":"off")
    [
		"he wattr 0x${device.deviceNetworkId} 1 0x201 0x1C 0x30 {00}", "delay 5000",
        "he rattr 0x${device.deviceNetworkId} 1 0x201 0x29"
	]
}

def cool() {
	log.debug "Setting mode to COOL"
	sendEvent("name":"thermostatMode", "value":"cool")
    [
		"he wattr 0x${device.deviceNetworkId} 1 0x201 0x1C 0x30 {03}", "delay 5000",
        "he rattr 0x${device.deviceNetworkId} 1 0x201 0x29"
	]
}

def heat() {
	log.debug "Setting mode to HEAT"
	sendEvent("name":"thermostatMode", "value":"heat")
    [
		"he wattr 0x${device.deviceNetworkId} 1 0x201 0x1C 0x30 {04}", "delay 5000",
        "he rattr 0x${device.deviceNetworkId} 1 0x201 0x29"
	]
}

def emergencyHeat() {
	log.debug "Setting mode to EMERGENCY HEAT"
	sendEvent("name":"thermostatMode", "value":"emergency heat")
    [
		"he wattr 0x${device.deviceNetworkId} 1 0x201 0x1C 0x30 {05}", "delay 5000",
        "he rattr 0x${device.deviceNetworkId} 1 0x201 0x29"
	]
}

def on() {
	fanOn()
}

def off() {
	fanAuto()
}

def fanOn() {
	log.debug "Setting fan to ON"
	sendEvent("name":"thermostatFanMode", "value":"on")
    sendEvent("name":"switch", "value":"on")
    [
		"he wattr 0x${device.deviceNetworkId} 1 0x202 0 0x30 {04}", "delay 5000",
        "he rattr 0x${device.deviceNetworkId} 1 0x201 0x29"
	]
}

def fanAuto() {
	log.debug "Setting fan to AUTO"
	sendEvent("name":"thermostatFanMode", "value":"auto")
    sendEvent("name":"switch", "value":"off")
    [
		"he wattr 0x${device.deviceNetworkId} 1 0x202 0 0x30 {05}", "delay 5000",
        "he rattr 0x${device.deviceNetworkId} 1 0x201 0x29"
	]
}

def holdOn() {
	log.debug "Setting hold to ON"
	sendEvent("name":"thermostatHoldMode", "value":"holdOn")
    [
		"he wattr 0x${device.deviceNetworkId} 1 0x201 0x23 0x30 {01}"
	]
}

def holdOff() {
	log.debug "Setting hold to OFF"
	sendEvent("name":"thermostatHoldMode", "value":"holdOff")
    [
		"he wattr 0x${device.deviceNetworkId} 1 0x201 0x23 0x30 {00}"
	]
}

// Commment out below if no C-wire since it will kill the batteries.
def poll() {
//	refresh()
	log.debug "Poll..."
	"he rattr 0x${device.deviceNetworkId} 1 0x201 0x29"
}

// PING is used by Device-Watch in attempt to reach the Device
def ping() {
//	refresh()
	log.debug "Ping..."
    "he rattr 0x${device.deviceNetworkId} 1 0x201 0x29"
}

def configure() {
	log.debug "Configuration starting..."
	sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 1 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])
	sendEvent(name: "coolingSetpointRange", value: coolingSetpointRange, displayed: false)
	sendEvent(name: "heatingSetpointRange", value: heatingSetpointRange, displayed: false)
	state.supportedThermostatModes = ["off", "heat", "cool", "emergency heat"]
	state.supportedFanModes = ["on", "auto"]
	sendEvent(name: "supportedThermostatModes", value: JsonOutput.toJson(state.supportedThermostatModes), displayed: false)
	sendEvent(name: "supportedThermostatFanModes", value: JsonOutput.toJson(state.supportedFanModes), displayed: false)
    log.debug "...bindings..."
	[
		"zdo bind 0x${device.deviceNetworkId} 1 1 0x000 {${device.zigbeeId}} {}", "delay 1000",
        "zdo bind 0x${device.deviceNetworkId} 1 1 0x001 {${device.zigbeeId}} {}", "delay 1000",
		"zdo bind 0x${device.deviceNetworkId} 1 1 0x201 {${device.zigbeeId}} {}", "delay 1000",
		"zdo bind 0x${device.deviceNetworkId} 1 1 0x202 {${device.zigbeeId}} {}", "delay 1000",
		"zcl global send-me-a-report 1 0x20 0x20 3600 86400 {01}", "delay 1000", // Battery report
		"send 0x${device.deviceNetworkId} 1 1"
	]
    log.debug "...reporting intervals..."
    [
    	zigbee.configureReporting(0x0201, 0x0029, 0x19, 5, 300, null), "delay 1000",	// Thermostat Operating State report to send whenever it changes (no min or max, or change threshold).  This is also known as Running State (Zen).
        zigbee.configureReporting(0x0201, 0x001c, 0x30, 0, 0, null), "delay 1000",
        zigbee.configureReporting(0x0000, 0x0007, 0x30, 0, 0, null)
	]
}

def refresh() {
	log.debug "Refreshing values..."
	[
		"he rattr 0x${device.deviceNetworkId} 1 0x000 0x07", "delay 200",
		"he rattr 0x${device.deviceNetworkId} 1 0x201 0", "delay 200",
		"he rattr 0x${device.deviceNetworkId} 1 0x201 0x11", "delay 200",
  		"he rattr 0x${device.deviceNetworkId} 1 0x201 0x12", "delay 200",
		"he rattr 0x${device.deviceNetworkId} 1 0x201 0x1C", "delay 200",
		"he rattr 0x${device.deviceNetworkId} 1 0x201 0x23", "delay 200",
        "he rattr 0x${device.deviceNetworkId} 1 0x201 0x29", "delay 200",
		"he rattr 0x${device.deviceNetworkId} 1 0x001 0x20", "delay 200",
        "he rattr 0x${device.deviceNetworkId} 1 0x001 0x3e", "delay 200",
		"he rattr 0x${device.deviceNetworkId} 1 0x202 0"
	]
    [
    	zigbee.configureReporting(0x0201, 0x0029, 0x19, 5, 300, null), "delay 1000",
        zigbee.configureReporting(0x0201, 0x001c, 0x30, 0, 0, null), "delay 1000",
        zigbee.configureReporting(0x0000, 0x0007, 0x30, 0, 0, null)
	]
}

private hex(value) {
	new BigInteger(Math.round(value).toString()).toString(16)
}