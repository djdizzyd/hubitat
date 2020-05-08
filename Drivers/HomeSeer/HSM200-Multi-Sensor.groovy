/*
*	HomeSeer HSM200 Multi-Sensor
*	version: 1.0
*/

import groovy.transform.Field

metadata {
    definition (name: "HomeSeer HSM200 Multi-Sensor", namespace: "djdizzyd", author: "Bryan Copeland", importUrl: "https://raw.githubusercontent.com/djdizzyd/hubitat/master/Drivers/HomeSeer/HSM200-Multi-Sensor.groovy") {
        capability "Switch"
        capability "Refresh"
        capability "Actuator"
        capability "Sensor"
        capability "Configuration"
        capability "MotionSensor"
        capability "IlluminanceMeasurement"
        capability "TemperatureMeasurement"
        capability "SwitchLevel"
        capability "ColorControl"

        fingerprint mfr:"000C", prod:"0004", deviceId:"0001", inClusters:"0x5E,0x71,0x31,0x33,0x26,0x72,0x86,0x59,0x85,0x70,0x77,0x5A,0x7A,0x73,0x9F,0x55,0x6C", deviceJoinName: "HomeSeer HSM200 Multi-Sensor"
    }
    preferences {
        configParams.each { input it.value.input }
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "TXT Descriptive logging", defaultValue: false
    }
}
@Field static Map configParams = [
        1: [input: [name: "configParam3", type: "number", title: "Lux Report Interval", description: "minutes 1-127 0-disable", defaultValue: 127, range:"0..127"], parameterSize:1],
        3: [input: [name: "configParam4", type: "number", title: "Temperature Report Interval", description: "minutes 1-127 0-disable", defaultValue: 127, range:"0..127"], parameterSize: 1]
]
@Field static Map ZWAVE_NOTIFICATION_TYPES=[0:"Reserverd", 1:"Smoke", 2:"CO", 3:"CO2", 4:"Heat", 5:"Water", 6:"Access Control", 7:"Home Security", 8:"Power Management", 9:"System", 10:"Emergency", 11:"Clock", 12:"First"]
@Field static Map CMD_CLASS_VERS=[0x33:1,0x26:2,0x86:2,0x72:2,0x70:1,0x85:2,0x59:1,0x31:6,0x71:8]
@Field static List<String> RGB_NAMES=["red", "green", "blue"]
@Field static Map ZWAVE_COLOR_COMPONENT_ID=[warmWhite: 0, coldWhite: 1, red: 2, green: 3, blue: 4]

void logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

void configure() {
    if (!state.initialized) initializeVars()
    runIn(5,pollDeviceData)
}

void initializeVars() {
    // first run only
    state.colorReceived=[red: null, green: null, blue: null, warmWhite: null, coldWhite: null]
    state.initialized=true
    runIn(5, refresh)
}

void updated() {
    log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    unschedule()
    if (logEnable) runIn(1800,logsOff)
    List<hubitat.zwave.Command> cmds=[]
    cmds.addAll(runConfigs())
    sendToDevice(cmds)
}

List<hubitat.zwave.Command> runConfigs() {
    List<hubitat.zwave.Command> cmds=[]
    configParams.each { param, data ->
        if (settings[data.input.name]) {
            cmds.addAll(configCmd(param, data.parameterSize, settings[data.input.name]))
        }
    }
    return cmds
}

List<hubitat.zwave.Command> pollConfigs() {
    List<hubitat.zwave.Command> cmds=[]
    configParams.each { param, data ->
        if (settings[data.input.name]) {
            cmds.add(zwave.configurationV1.configurationGet(parameterNumber: param.toInteger()))
        }
    }
    return cmds
}

List<hubitat.zwave.Command> configCmd(parameterNumber, size, scaledConfigurationValue) {
    List<hubitat.zwave.Command> cmds = []
    cmds.add(zwave.configurationV1.configurationSet(parameterNumber: parameterNumber.toInteger(), size: size.toInteger(), scaledConfigurationValue: scaledConfigurationValue.toInteger()))
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: parameterNumber.toInteger()))
    return cmds
}

void zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
    if(configParams[cmd.parameterNumber.toInteger()]) {
        Map configParam=configParams[cmd.parameterNumber.toInteger()]
        int scaledValue
        cmd.configurationValue.reverse().eachWithIndex { v, index -> scaledValue=scaledValue | v << (8*index) }
        device.updateSetting(configParam.input.name, [value: "${scaledValue}", type: configParam.input.type])
    }
}

void pollDeviceData() {
    List<hubitat.zwave.Command> cmds = []
    cmds.add(zwave.versionV2.versionGet())
    cmds.add(zwave.manufacturerSpecificV2.deviceSpecificGet(deviceIdType: 1))
    cmds.addAll(processAssociations())
    cmds.addAll(pollConfigs())
    cmds.add(zwave.configurationV1.configurationSet(parameterNumber: 1, size: 1, scaledConfigurationValue: 1))
    cmds.add(zwave.sensorMultilevelV6.sensorMultilevelGet(scale: 1, sensorType: 3))
    cmds.add(zwave.notificationV8.notificationGet(notificationType: 7, event:0))
    cmds.add(zwave.sensorMultilevelV6.sensorMultilevelGet(scale: (location.temperatureScale=="F"?1:0), sensorType: 1))
    cmds.addAll(queryAllColors())
    sendToDevice(cmds)
}

void refresh() {
    List<hubitat.zwave.Command> cmds=[]
    cmds.add(zwave.sensorMultilevelV6.sensorMultilevelGet(scale: 1, sensorType: 3))
    cmds.add(zwave.notificationV8.notificationGet(notificationType: 7, event:0))
    cmds.add(zwave.sensorMultilevelV6.sensorMultilevelGet(scale: (location.temperatureScale=="F"?1:0), sensorType: 1))
    cmds.add(zwave.switchMultilevelV2.switchMultilevelGet())
    cmds.addAll(queryAllColors())
    sendToDevice(cmds)
}

private void refreshColor() {
    sendToDevice(queryAllColors())
}

private List<hubitat.zwave.Command> queryAllColors() {
    List<hubitat.zwave.Command> cmds=[]
    RGB_NAMES.each { cmds.add(zwave.switchColorV1.switchColorGet(colorComponent: it, colorComponentId: ZWAVE_COLOR_COMPONENT_ID[it])) }
    return cmds
}

void installed() {
    if (logEnable) log.debug "installed()..."
    initializeVars()
}

void eventProcess(Map evt) {
    if (device.currentValue(evt.name).toString() != evt.value.toString() || !eventFilter) {
        evt.isStateChange=true
        sendEvent(evt)
    }
}

void zwaveEvent(hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {
    if (logEnable) log.debug "${cmd}"
    Map evt = [isStateChange:false]
    switch (cmd.sensorType) {
        case 1:
            evt.name = "temperature"
            evt.value = cmd.scaledSensorValue.toInteger()
            evt.unit = cmd.scale==0?"C":"F"
            evt.isStateChange=true
            evt.descriptionText="${device.displayName}: Temperature report received: ${evt.value}"
            break
        case 3:
            evt.name = "illuminance"
            evt.value = cmd.scaledSensorValue.toInteger()
            evt.unit = "lux"
            evt.isStateChange=true
            evt.descriptionText="${device.displayName}: Illuminance report received: ${evt.value}"
            break
    }
    if (evt.isStateChange) {
        if (txtEnable) log.info evt.descriptionText
        eventProcess(evt)
    }
}

void zwaveEvent(hubitat.zwave.commands.switchmultilevelv2.SwitchMultilevelReport cmd) {
    if (logEnable) log.debug cmd
    dimmerEvents(cmd)
}

private void dimmerEvents(hubitat.zwave.Command cmd) {
    String value = (cmd.value ? "on" : "off")
    String description = "${device.displayName} was turned ${value}"
    if (txtEnable) log.info description
    eventProcess(name: "switch", value: value, descriptionText: description, type: state.isDigital?"digital":"physical")
    if (cmd.value) {
        description = "${device.displayName} was set to ${cmd.value}"
        if (txtEnable) log.info description
        eventProcess(name: "level", value: cmd.value == 99 ? 100 : cmd.value , descriptionText: description, unit: "%", type: state.isDigital?"digital":"physical")
    }
    state.isDigital=false
}

void zwaveEvent(hubitat.zwave.commands.notificationv8.NotificationReport cmd) {
    Map evt = [isStateChange:false]
    log.info "Notification: " + ZWAVE_NOTIFICATION_TYPES[cmd.notificationType.toInteger()]
    if (cmd.notificationType==7) {
        // home security
        switch (cmd.event) {
            case 0:
                // state idle
                if (cmd.eventParametersLength > 0) {
                    switch (cmd.eventParameter[0]) {
                        case 7:
                            evt.name = "motion"
                            evt.value = "inactive"
                            evt.isStateChange = true
                            evt.descriptionText = "${device.displayName} motion became ${evt.value}"
                            break
                        case 8:
                            evt.name = "motion"
                            evt.value = "inactive"
                            evt.isStateChange = true
                            evt.descriptionText = "${device.displayName} motion became ${evt.value}"
                            break
                    }
                } else {
                    evt.name = "motion"
                    evt.value = "inactive"
                    evt.descriptionText = "${device.displayName} motion became ${evt.value}"
                    evt.isStateChange = true
                }
                break
            case 7:
                // motion detected (location provided)
                evt.name = "motion"
                evt.value = "active"
                evt.isStateChange = true
                evt.descriptionText = "${device.displayName} motion became ${evt.value}"
                break
            case 8:
                // motion detected
                evt.name = "motion"
                evt.value = "active"
                evt.isStateChange = true
                evt.descriptionText = "${device.displayName} motion became ${evt.value}"
                break
            case 254:
                // unknown event/state
                log.warn "Device sent unknown event / state notification"
                break
        }
    }
    if (evt.isStateChange) {
        if (txtEnable) log.info evt.descriptionText
        eventProcess(evt)
    }
}

void on() {
    state.isDigital=true
    setLevel(99)
}

void off() {
    state.isDigital=true
    setLevel(0)
}

void setLevel(level, duration=0) {
    state.isDigital=true
    if (logEnable) log.debug "setLevel($level, $duration)"
    if(level > 99) level = 99
    List<hubitat.zwave.Command> cmds=[]
    cmds.add(zwave.switchMultilevelV2.switchMultilevelSet(value: level.toInteger(), dimmingDuration: duration))
    cmds.add(zwave.switchMultilevelV2.switchMultilevelGet())
    sendToDevice(cmds)
}

void zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(CMD_CLASS_VERS)
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand)
    }
}

void parse(String description) {
    if (logEnable) log.debug "parse:${description}"
    hubitat.zwave.Command cmd = zwave.parse(description, CMD_CLASS_VERS)
    if (cmd) {
        zwaveEvent(cmd)
    }
}

void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd) {
    if (logEnable) log.debug "Supervision get: ${cmd}"
    hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(CMD_CLASS_VERS)
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand)
    }
    sendToDevice(new hubitat.zwave.commands.supervisionv1.SupervisionReport(sessionID: cmd.sessionID, reserved: 0, moreStatusUpdates: false, status: 0xFF, duration: 0))
}

void zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.DeviceSpecificReport cmd) {
    if (logEnable) log.debug "Device Specific Report: ${cmd}"
    switch (cmd.deviceIdType) {
        case 1:
            // serial number
            def serialNumber=""
            if (cmd.deviceIdDataFormat==1) {
                cmd.deviceIdData.each { serialNumber += hubitat.helper.HexUtils.integerToHexString(it & 0xff,1).padLeft(2, '0')}
            } else {
                cmd.deviceIdData.each { serialNumber += (char) it }
            }
            device.updateDataValue("serialNumber", serialNumber)
            break
    }
}

void zwaveEvent(hubitat.zwave.commands.versionv2.VersionReport cmd) {
    if (logEnable) log.debug "version2 report: ${cmd}"
    device.updateDataValue("firmwareVersion", "${cmd.firmware0Version}.${cmd.firmware0SubVersion}")
    device.updateDataValue("protocolVersion", "${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}")
    device.updateDataValue("hardwareVersion", "${cmd.hardwareVersion}")
}

void sendToDevice(List<hubitat.zwave.Command> cmds) {
    sendHubCommand(new hubitat.device.HubMultiAction(commands(cmds), hubitat.device.Protocol.ZWAVE))
}

void sendToDevice(hubitat.zwave.Command cmd) {
    sendHubCommand(new hubitat.device.HubAction(secureCommand(cmd), hubitat.device.Protocol.ZWAVE))
}

void sendToDevice(String cmd) {
    sendHubCommand(new hubitat.device.HubAction(secureCommand(cmd), hubitat.device.Protocol.ZWAVE))
}

List<String> commands(List<hubitat.zwave.Command> cmds, Long delay=300) {
    return delayBetween(cmds.collect{ secureCommand(it) }, delay)
}

String secureCommand(hubitat.zwave.Command cmd) {
    secureCommand(cmd.format())
}

String secureCommand(String cmd) {
    String encap=""
    if (getDataValue("zwaveSecurePairingComplete") != "true") {
        return cmd
    } else {
        encap = "988100"
    }
    return "${encap}${cmd}"
}

void zwaveEvent(hubitat.zwave.Command cmd) {
    if (logEnable) log.debug "skip:${cmd}"
}

List<hubitat.zwave.Command> setDefaultAssociation() {
    List<hubitat.zwave.Command> cmds=[]
    cmds.add(zwave.associationV2.associationSet(groupingIdentifier: 1, nodeId: zwaveHubNodeId))
    cmds.add(zwave.associationV2.associationGet(groupingIdentifier: 1))
    return cmds
}

List<hubitat.zwave.Command> processAssociations(){
    List<hubitat.zwave.Command> cmds = []
    cmds.addAll(setDefaultAssociation())
    if (logEnable) log.debug "processAssociations cmds: ${cmds}"
    return cmds
}


void zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
    if (logEnable) log.debug "${device.label?device.label:device.name}: ${cmd}"
    List<String> temp = []
    if (cmd.nodeId != []) {
        cmd.nodeId.each {
            temp.add(it.toString().format( '%02x', it.toInteger() ).toUpperCase())
        }
    }
    updateDataValue("zwaveAssociationG${cmd.groupingIdentifier}", "$temp")
}

void zwaveEvent(hubitat.zwave.commands.associationv2.AssociationGroupingsReport cmd) {
    if (logEnable) log.debug "${device.label?device.label:device.name}: ${cmd}"
    log.info "${device.label?device.label:device.name}: Supported association groups: ${cmd.supportedGroupings}"
    state.associationGroups = cmd.supportedGroupings
}

void setSaturation(percent) {
    if (logEnable) log.debug "setSaturation($percent)"
    setColor([saturation: percent, hue: device.currentValue("hue"), level: device.currentValue("level")])
}

void setHue(value) {
    if (logEnable) log.debug "setHue($value)"
    setColor([hue: value, saturation: 100, level: device.currentValue("level")])
}

void zwaveEvent(hubitat.zwave.commands.switchcolorv1.SwitchColorReport cmd) {
    if (logEnable) log.debug "got SwitchColorReport: $cmd"
    if (!state.colorReceived) initializeVars()
    state.colorReceived[cmd.colorComponent] = cmd.value
    if (RGB_NAMES.every { state.colorReceived[it] != null }) {
        List<Integer> colors = RGB_NAMES.collect { state.colorReceived[it] }
        if (logEnable) log.debug "colors: $colors"
        // Send the color as hex format
        String hexColor = "#" + colors.collect { Integer.toHexString(it).padLeft(2, "0") }.join("")
        eventProcess(name: "color", value: hexColor)
        // Send the color as hue and saturation
        List hsv = hubitat.helper.ColorUtils.rgbToHSV(colors)
        eventProcess(name: "hue", value: hsv[0].round(), descriptionText: "${device.displayName} hue is ${hsv[0].round()}")
        eventProcess(name: "saturation", value: hsv[1].round(), descriptionText: "${device.displayName} saturation is ${hsv[1].round()}")

        if ((hsv[0] > 0) && (hsv[1] > 0)) {
            setGenericName(hsv[0])
            eventProcess(name: "level", value: hsv[2].round(), descriptionText: "${device.displayName} level is ${hsv[2].round()}")
        }
    }
}

void setColor(value) {
    if (value.hue == null || value.saturation == null) return
    if (value.level == null) value.level=100
    if (logEnable) log.debug "setColor($value)"
    List<hubitat.zwave.Command> cmds = []
    List rgb = hubitat.helper.ColorUtils.hsvToRGB([value.hue, value.saturation, value.level])
    if (logEnable) log.debug "r:" + rgb[0] + ", g: " + rgb[1] +", b: " + rgb[2]
    cmds.add(zwave.switchColorV1.switchColorSet(red: rgb[0], green: rgb[1], blue: rgb[2]))
    if (device.currentValue("switch") != "on"){
        if (logEnable) log.debug "Light is off. Turning on"
        cmds.add(zwave.switchMultilevelV2.switchMultilevelSet(value: 99, dimmingDuration: 0))
        cmds.add(zwave.switchMultilevelV2.switchMultilevelGet())
    }
    sendToDevice(cmds)
    runIn(3,"refreshColor")
}

private void setGenericName(hue){
    String colorName
    hue = hue.toInteger()
    hue = (hue * 3.6)
    switch (hue.toInteger()){
        case 0..15: colorName = "Red"
            break
        case 16..45: colorName = "Orange"
            break
        case 46..75: colorName = "Yellow"
            break
        case 76..105: colorName = "Chartreuse"
            break
        case 106..135: colorName = "Green"
            break
        case 136..165: colorName = "Spring"
            break
        case 166..195: colorName = "Cyan"
            break
        case 196..225: colorName = "Azure"
            break
        case 226..255: colorName = "Blue"
            break
        case 256..285: colorName = "Violet"
            break
        case 286..315: colorName = "Magenta"
            break
        case 316..345: colorName = "Rose"
            break
        case 346..360: colorName = "Red"
            break
    }
    String descriptionText = "${device.getDisplayName()} color is ${colorName}"
    eventProcess(name: "colorName", value: colorName ,descriptionText: descriptionText)
}