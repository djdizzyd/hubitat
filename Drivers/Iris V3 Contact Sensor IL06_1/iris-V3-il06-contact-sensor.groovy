import hubitat.zigbee.clusters.iaszone.ZoneStatus

metadata {

	definition(name: "IrisV3 Contact Sensor IL06", namespace: "djdizzyd", author: "Bryan Copeland") {
		capability "Battery"
		capability "Configuration"
		capability "Contact Sensor"
		capability "Refresh"
        capability "Temperature Measurement"
		capability "Health Check"
		capability "Sensor"

		fingerprint inClusters: "0000,0001,0003,0020,0402,0500,0B05,FC01,FC02", outClusters: "0003,0019", manufacturer: "iMagic by GreatStar", model: "1116-S", deviceJoinName: "Iris V3 Contact Sensor"
	}
	preferences {
        input name: "tempOffset", type: "number", title: "Temperature Offset", defaultValue: 0
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
    }
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def updated(){
    log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    log.warn "description logging is: ${txtEnable == true}"
    if (logEnable) runIn(1800,logsOff)
}

def refresh() {
    if (logEnable) log.debug "refresh"
    def cmds = zigbee.readAttribute(0x0001, 0x0020) +
        // zigbee.readAttribute(0x0001, 0x0021) + 
        zigbee.readAttribute(0x0402, 0x0000) +
        zigbee.readAttribute(0x0500, 0x0002) +
        zigbee.enrollResponse()
    return cmds
}

def ping() {
    zigbee.readAttribute(0x500, 0x0002) 
}

def configure() {
    log.warn "configure..."
    sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 1 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])
    runIn(1800,logsOff)
    def cmds = refresh() +
        zigbee.batteryConfig() +
        zigbee.configureReporting(0x0500, 0x0002, DataType.BITMAP16, 30, 60 * 5, null) +
        zigbee.temperatureConfig(30, (60 * 30)) +
        zigbee.enrollResponse()
   
    return cmds
}





def parse(String description) {
    if (logEnable) log.debug "parse description: ${description}"
    if (description.startsWith("catchall")) return
    //def eventMap = zigbee.getEvent(description)
    if (description.startsWith("zone status")) {
        ZoneStatus zs=zigbee.parseZoneStatus(description)
        sendEvent(name: "contact", value: zs.isAlarm1Set() ? "open" : "closed")
	} else {
        def descMap = zigbee.parseDescriptionAsMap(description)
        def descriptionText
    
        def value
        def name
        def unit
        def rawValue = Integer.parseInt(descMap.value,16)

        switch (descMap.clusterInt){
            case 0x0500: //IAS 
                if (descMap.attrInt == 0x0002){ //zone status
                    def zs = new ZoneStatus(rawValue)
                    log.debug "zone status got"
                    name="contact"
                    value=zs.isAlarm1Set() ? "open" : "closed"
                    sendEvent(name: name, value: value)
                } else if (descMap.commandInt == 0x07) {
                    if (descMap.data[0] == "00") {
                        if (logEnable) log.debug "IAS ZONE REPORTING CONFIG RESPONSE: ${descMap}"
                        sendEvent(name: "checkInterval", value: 60 * 12, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])
                    } else {
                        log.warn "IAS ZONE REPORING CONFIG FAILED - Error Code: ${descMap.data[0]}"        
				    }
                } else {
                    log.debug "0x0500:${descMap.attrId}:${rawValue}"
                }
                break
            case 0x0001: //power configuration
                if (descMap.attrInt == 0x0020){
                    unit = "%"
                    def volts = rawValue / 10
                    if (!(rawValue == 0 || rawValue == 255)) {
                        def minVolts = 2.1
                        def maxVolts = 3.0
                        def pct = (volts - minVolts) / (maxVolts - minVolts)
                        def roundedPct = Math.round(pct * 100)
                        if (roundedPct <= 0) roundedPct = 1
                        value = Math.min(100, roundedPct)
					}
                    name = "battery"
                    sendEvent(name: name, value: value, unit: unit)
                } else {
                    log.debug "0x0001:${descMap.attrId}:${rawValue}"
                }
                break
            case 0x0402: //temperature
                if (descMap.attrInt == 0) {
                    name = "temperature"
                    unit = location.TemperatureScale
                    //if (logEnable) log.debug "0x0402:${descMap.attrId}:${rawValue}"
                    if (tempOffset) {
                        value = (int) convertTemperatureIfNeeded((rawValue / 100), "C", 2) + (int) tempOffset
				    } else {
                        value = convertTemperatureIfNeeded((rawValue / 100), "C", 2)
                    }
                    sendEvent(name: name, value: value, unit: unit)
		    	} else {
                    log.debug "0x0402:${descMap.attrId}:${rawValue}"     
    			}
                break
        }
        if (logEnable) log.debug "evt- rawValue:${rawValue}, value: ${value}, descT: ${descriptionText}"
    }
}

def intTo16bitUnsignedHex(value) {
    def hexStr = zigbee.convertToHexString(value.toInteger(),4)
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2))
}

def intTo8bitUnsignedHex(value) {
    return zigbee.convertToHexString(value.toInteger(), 2)
}

def installed() {

}

