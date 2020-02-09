// this device is completely infuriating 
// Most of the features described in the datasheet are not available in the clusters
// My wish was to use the rbg light as a notification device 
// I also wanted the device to report when power was lost 


metadata {
    definition (name: "CentraLite 3420 NightLight", namespace: "djdizzyd", author: "Bryan Copeland") {
        capability "Actuator"
        capability "Configuration"
        capability "Refresh"
        capability "Switch"
        capability "Switch Level"
        capability "ChangeLevel"
        capability "Light"
        capability "PowerSource"


        fingerprint profileId: "0104", inClusters: "0000,0003,0004,0005,0006,0008,0B05", outClusters: "0019", manufacturer: "CentraLite", model: "CentraLite 3420", deviceJoinName: "CentraLite 3420 NightLight"
    }

    preferences {
        input name: "transitionTime", type: "enum", description: "", title: "Transition time", options: [[500:"500ms"],[1000:"1s"],[1500:"1.5s"],[2000:"2s"],[5000:"5s"]], defaultValue: 1000
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
    log.warn "Hue in degrees is: ${hiRezHue == true}"
    log.warn "debug logging is: ${logEnable == true}"
    log.warn "description logging is: ${txtEnable == true}"
    if (logEnable) runIn(1800,logsOff)
}

def parse(String description) {
    if (logEnable) log.debug "parse description: ${description}"
    if (description.startsWith("catchall")) return
    def descMap = zigbee.parseDescriptionAsMap(description)
    def descriptionText
    def rawValue = Integer.parseInt(descMap.value,16)
    def value
    def name
    def unit
    switch (descMap.clusterInt){
        case 6: //switch
            if (descMap.attrInt == 0){
                value = rawValue == 1 ? "on" : "off"
                name = "switch"
                if (device.currentValue("${name}") && value == device.currentValue("${name}")){
                    descriptionText = "${device.displayName} is ${value}"
                } else {
                    descriptionText = "${device.displayName} was turned ${value}"
                }
            } else {
                log.debug "0x0006:${descMap.attrId}:${rawValue}"
            }
            break
        case 8: //level
            if (descMap.attrInt == 0){
                unit = "%"
                value = Math.round(rawValue / 2.55)
                name = "level"
                if (device.currentValue("${name}") && value == device.currentValue("${name}").toInteger()){
                    descriptionText = "${device.displayName} is ${value}${unit}"
                } else {
                    descriptionText = "${device.displayName} was set to ${value}${unit}"
                }
            } else {
                log.debug "0x0008:${descMap.attrId}:${rawValue}"
            }
            break
    }
    if (logEnable) log.debug "evt- rawValue:${rawValue}, value: ${value}, descT: ${descriptionText}"
    if (descriptionText){
        if (txtEnable) log.info "${descriptionText}"
        sendEvent(name:name,value:value,descriptionText:descriptionText, unit: unit)
    }
}

def startLevelChange(direction){
    def upDown = direction == "down" ? 1 : 0
    def unitsPerSecond = 100
    return "he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0008 1 { 0x${intTo8bitUnsignedHex(upDown)} 0x${intTo16bitUnsignedHex(unitsPerSecond)} }"
}

def stopLevelChange(){
    return [
            "he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0008 3 {}}","delay 200",
            "he rattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0008 0 {}"
    ]
}

def on() {
    def cmd = [
            "he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0006 1 {}",
            "delay 1000",
            "he rattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0006 0 {}"
    ]
    return cmd
}

def off() {
    def cmd = [
            "he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0006 0 {}",
            "delay 1000",
            "he rattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0006 0 {}"
    ]
    return cmd
}

def refresh() {
    if (logEnable) log.debug "refresh"
    return  [
            //"he rattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0000 7 {}","delay 200",  //power source
            "he rattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0006 0 {}","delay 200",  //light state
            "he rattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0008 0 {}","delay 200"  //light level
    ]

}

def configure() {
    log.warn "configure..."
    runIn(1800,logsOff)
    def cmds = [
            //bindings
            //"zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0000 {${device.zigbeeId}} {}", "delay 200",
            "zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0006 {${device.zigbeeId}} {}", "delay 200",
            "zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0008 {${device.zigbeeId}} {}", "delay 200",
            //reporting
            //"he cr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0000 7 0x20 0 0xFFFF {}","delay 200",
            "he cr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0006 0 0x10 0 0xFFFF {}","delay 200",
            "he cr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0008 0 0x20 0 0xFFFF {}", "delay 200"
    ] + refresh()
    return cmds
}

def setLevel(value) {
    setLevel(value,(transitionTime?.toBigDecimal() ?: 1000) / 1000)
}

def setLevel(value,rate) {
    rate = rate.toBigDecimal()
    def scaledRate = (rate * 10).toInteger()
    def cmd = []
    def isOn = device.currentValue("switch") == "on"
    value = (value.toInteger() * 2.55).toInteger()
    if (isOn){
        cmd = [
                "he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0008 4 {0x${intTo8bitUnsignedHex(value)} 0x${intTo16bitUnsignedHex(scaledRate)}}",
                "delay ${(rate * 1000) + 400}",
                "he rattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0008 0 {}"
        ]
    } else {
        cmd = [
                "he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0008 4 {0x${intTo8bitUnsignedHex(value)} 0x0100}", "delay 200",
                "he rattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0006 0 {}", "delay 200",
                "he rattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0008 0 {}"
        ]
    }
    return cmd
}

def installed() {

}

def intTo16bitUnsignedHex(value) {
    def hexStr = zigbee.convertToHexString(value.toInteger(),4)
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2))
}

def intTo8bitUnsignedHex(value) {
    return zigbee.convertToHexString(value.toInteger(), 2)
}