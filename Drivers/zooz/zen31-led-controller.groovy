/*
*	Zooz Zen31 LED Controller
*   v1.0
*
*/


import groovy.json.JsonOutput
import hubitat.helper.ColorUtils
import groovy.transform.Field

metadata {
    definition (name: "Zooz Zen31 LED Controller", namespace: "djdizzyd", author: "Bryan Copeland", importUrl: "https://raw.githubusercontent.com/djdizzyd/hubitat/master/Drivers/zooz/zen31-led-controller.groovy" ) {
        capability "Actuator"
        capability "ChangeLevel"
        capability "ColorControl"
        capability "ColorMode"
        capability "ColorTemperature"
        capability "Configuration"
        capability "LightEffects"
        capability "Refresh"
        capability "Sensor"
        capability "Switch"
        capability "SwitchLevel"

        attribute "colorMode", "string"
        attribute "lightEffects", "JSON_OBJECT"

        fingerprint mfr:"027A", prod:"0902", deviceId:"2000", inClusters:"0x5E,0x26,0x85,0x8E,0x59,0x55,0x86,0x72,0x5A,0x73,0x98,0x9F,0x31,0x70,0x56,0x71,0x60,0x32,0x33,0x7A,0x75,0x5B,0x22,0x6C", deviceJoinName: "Zooz Zen31 LED Controller"

    }
    preferences {
        configParams.each { input it.value.input }
        input name: "wwComponent", type: "bool", title: "Enable Warm White Component", defaultValue: true
        input name: "wwKelvin", type: "number", title: "Warm White LED Kelvin", defaultValue: 2700, required: true
        input name: "enableGammaCorrect", type: "bool", title: "Enable gamma correction", defaultValue: false
        input name: "colorPrestage", type: "bool", title: "Enable color prestage", defaultValue: false
        input name: "colorDuration", type: "number", title: "Color Transition", defaultValue: 1
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable text logging", defaultValue: false
    }
}
@Field static Map configParams = [
        1: [input: [name: "configParam1", type: "enum", title: "Status After Power Failure", description: "", defaultValue: 0, options: [0:"Off",1:"Last State",2:"On"]], parameterSize: 1],
        150: [input: [name: "configParam150", type: "enum", title: "RGBW/HSB Wall Switch Mode", description: "", defaultValue: 0, options: [0:"RGBW mode",1:"HSB mode"]], parameterSize: 1],
        151: [input: [name: "configParam151", type: "number", title: "Physical Ramp Rate", description: "seconds", defaultValue: 1, range:"0..127"], parameterSize: 1],
        152: [input: [name: "configParam152", type: "number", title: "Z-Wave Ramp Rate", description: "seconds", defaultValue: 1, range:"0..254"], parameterSize:2],
]
@Field static String RED="red"
@Field static String GREEN="green"
@Field static String BLUE="blue"
@Field static String WARM_WHITE="warmWhite"
@Field static String COLD_WHITE="coldWhite"
@Field static List<String> RGBW_NAMES=["red", "green", "blue", "warmWhite"]
@Field static int COLOR_TEMP_MIN=2700
@Field static int COLOR_TEMP_MAX=6500
@Field static Map CMD_CLASS_VERS=[0x33:3,0x26:3,0x85:2,0x71:8,0x20:1,0x31:11,0x86:2,0x32:3]
@Field static Map ZWAVE_COLOR_COMPONENT_ID=[warmWhite: 0, coldWhite: 1, red: 2, green: 3, blue: 4]
@Field static Map lightEffects=[0:"None", 6:"Fireplace", 7:"Storm", 8:"Rainbow", 9:"Polar Lights", 10:"Police"]
private int getCOLOR_TEMP_DIFF_RGBW() {  COLOR_TEMP_MAX - wwKelvin }

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
    state.colorReceived=["red": null, "green": null, "blue": null, "warmWhite": null, "coldWhite": null]
    sendEvent(name:"lightEffects", value: JsonOutput.toJson(lightEffects))
    state.effectNumber=0
    sendEvent(name:"colorMode", value: "RGB")
    eventProcess(name: "effectName", value: lightEffects[0])
    state.initialized=true
}

void pollDeviceData() {
    List<hubitat.zwave.Command> cmds = []
    cmds.add(zwave.versionV2.versionGet())
    cmds.add(zwave.switchMultilevelV3.switchMultilevelGet())
    cmds.add(zwave.basicV1.basicGet())
    cmds.add(zwave.manufacturerSpecificV2.deviceSpecificGet(deviceIdType: 1))
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 157))
    cmds.add(zwave.associationV2.associationGet(groupingIdentifier:1))
    cmds.add(zwave.configurationV1.configurationSet(parameterNumber: 66, scaledConfigurationValue: 0, size: 1))
    cmds.add(zwave.configurationV1.configurationSet(parameterNumber: 65, scaledConfigurationValue: 0, size: 1))
    cmds.add(zwave.configurationV1.configurationSet(parameterNumber: 62, scaledConfigurationValue: 0, size: 1))
    cmds.addAll(queryAllColors())
    sendToDevice(cmds)
}

void zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(CMD_CLASS_VERS)
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand)
    }
}

void eventProcess(Map evt) {
    if (device.currentValue(evt.name).toString() != evt.value.toString()) {
        evt.isStateChange=true
        sendEvent(evt)
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
    if (logEnable) log.debug "version3 report: ${cmd}"
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

List<String> commands(List<hubitat.zwave.Command> cmds, Long delay=200) {
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

void installed() {
    log.info "installed()..."
}

void updated() {
    log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    unschedule()
    if (logEnable) runIn(1800,logsOff)
    List<hubitat.zwave.Command> cmds=[]
    cmds.addAll(processAssociations())
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
    int scaledValue
    cmd.configurationValue.reverse().eachWithIndex { v, index -> scaledValue=scaledValue | v << (8*index) }
    if(configParams[cmd.parameterNumber.toInteger()]) {
        Map configParam=configParams[cmd.parameterNumber.toInteger()]
        device.updateSetting(configParam.input.name, [value: "${scaledValue}", type: configParam.input.type])
    } else {
        if (cmd.parameterNumber==157) {
            eventProcess(name: "effectName", value: lightEffects[scaledValue])
            state.effectNumber=scaledValue
        }
    }
}

void setEffect(effectNumber) {
    if(logEnable) log.debug "Got setEffect " + effectNumber
    List<hubitat.zwave.Command> cmds=[]
    cmds.add(zwave.configurationV2.configurationSet(parameterNumber: 157, size: 1, scaledConfigurationValue: effectNumber.toInteger()))
    cmds.add(zwave.configurationV2.configurationGet(parameterNumber: 157))
    if (device.currentValue("switch") != "on") {
        cmds.add(zwave.basicV1.basicSet(value: 0xFF))
        cmds.add(zwave.switchMultilevelV3.switchMultilevelGet())
    }
    sendToDevice(cmds)
}

void setNextEffect() {
    if (state.effectNumber < 7) setEffect(state.effectNumber+1)
}

void setPreviousEffect() {
    if (state.effectNumber > 0) setEffect(state.effectNumber-1)
}

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
    dimmerEvents(cmd)
}

void zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd) {
    dimmerEvents(cmd)
}

void zwaveEvent(hubitat.zwave.commands.switchcolorv3.SwitchColorReport cmd) {
    if(logEnable) log.debug"got SwitchColorReport: $cmd"
    if (!state.colorReceived) state.colorReceived=["red": null, "green": null, "blue": null, "warmWhite": null, "coldWhite": null]
    state.colorReceived[cmd.colorComponent] = cmd.targetValue
    if (RGBW_NAMES.every { state.colorReceived[it] != null }) {
        if (device.currentValue("colorMode") == "RGB") {
            List hsv=ColorUtils.rgbToHSV([state.colorReceived["red"], state.colorReceived["green"], state.colorReceived["blue"]])
            int hue=hsv[0]
            int sat=hsv[1]
            int lvl=hsv[2]
            eventProcess(name:"hue", value:Math.round(hue), unit:"%")
            setGenericName(hue)
            eventProcess(name:"saturation", value:Math.round(sat), unit:"%")
            eventProcess(name:"level", value:Math.round(lvl), unit:"%")
        } else if (device.currentValue("colorMode")=="CT"){
            if (wwComponent) {
                int colorTemp = COLOR_TEMP_MIN + (COLOR_TEMP_DIFF_RGBW / 2)
                int warmWhite = state.colorReceived["warmWhite"]
                int coldWhite = state.colorReceived["red"]
                if (warmWhite != coldWhite) colorTemp = (COLOR_TEMP_MAX - (COLOR_TEMP_DIFF_RGBW * warmWhite) / 255) as Integer
                eventProcess(name: "colorTemperature", value: colorTemp)
            } else {
                // Math is hard
                eventProcess(name: "colorTemperature", value: state.ctTarget)
            }
        }
        RGBW_NAMES.collect { state.colorReceived[it] = null }
    }
}

private void dimmerEvents(hubitat.zwave.Command cmd) {
    String value = (cmd.value ? "on" : "off")
    eventProcess(name: "switch", value: value, descriptionText: "$device.displayName was turned $value")
    if (cmd.value) {
        eventProcess(name: "level", value: cmd.value == 99 ? 100 : cmd.value , unit: "%")
    }
}

void zwaveEvent(hubitat.zwave.Command cmd) {
    if(logEnable) log.debug "skip:${cmd}"
}

void zwaveEvent(hubitat.zwave.commands.switchcolorv3.SwitchColorSupportedReport cmd) {
    if(logEnable) log.debug "${cmd}"
}

void on() {
    List<hubitat.zwave.Command> cmds=[]
    cmds.add(zwave.basicV1.basicSet(value: 0xFF))
    cmds.add(zwave.switchMultilevelV3.switchMultilevelGet())
    sendToDevice(cmds)
}

void off() {
    List<hubitat.zwave.Command> cmds=[]
    cmds.add(zwave.basicV1.basicSet(value: 0x00))
    cmds.add(zwave.switchMultilevelV3.switchMultilevelGet())
    sendToDevice(cmds)
}

void refresh() {
    // Queries a device for changes
    List<hubitat.zwave.Command> cmds=[]
    cmds.add(zwave.switchMultilevelV3.switchMultilevelGet())
    cmds.add(zwave.configurationV2.configurationGet(parameterNumber: 157))
    cmds.addAll(queryAllColors())
    sendToDevice(cmds)
}

void startLevelChange(direction) {
    int upDownVal = direction == "down" ? 1 : 0
    if (logEnable) log.debug "got startLevelChange(${direction})"
    List<hubitat.zwave.Command> cmds=[]
    cmds.add(zwave.switchMultilevelV2.switchMultilevelStartLevelChange(ignoreStartLevel: 1, startLevel: 1, upDown: upDownVal))
    sendToDevice(cmds)
}

void stopLevelChange() {
    List<hubitat.zwave.Command> cmds=[]
    cmds.add(zwave.switchMultilevelV3.switchMultilevelStopLevelChange())
    cmds.add(zwave.switchMultilevelV3.switchMultilevelGet())
    sendToDevice(cmds)
}

void setLevel(level) {
    int duration=1
    if(dimmerSpeed) duration=dimmerSpeed.toInteger()
    setLevel(level, duration)
}

void setLevel(level, duration) {
    // Sets the Level (brightness) of a device (0-99) over a duration (0-???) of (MS?)
    if(logEnable) log.debug "setLevel($level, $duration)"
    if(level > 99) level = 99
    Integer tt = (duration < 128 ? duration : 128 + Math.round(duration / 60)).toInteger()
    sendToDevice(zwave.switchMultilevelV3.switchMultilevelSet(value: level, dimmingDuration: tt))
}

void setSaturation(percent) {
    // Sets the Saturation of a device (0-100)
    if(percent>100) percent=100
    if(logEnable) log.debug "setSaturation($percent)"
    setColor(saturation: percent)
}

void setHue(value) {
    // Sets the Hue of a device (0-360) < Add setting for this
    if(logEnable) log.debug "setHue($value)"
    setColor(hue: value)
}

void setColor(value) {
    // Sets the color of a device from HSL
    Map setValue = [:]
    int duration=colorDuration?colorDuration:1
    if(logEnable) log.debug  "setColor($value)"
    if(value.hue > 100) value.hue=100
    setValue.hue = value.hue == null ? device.currentValue("hue") : value.hue
    setValue.saturation = value.saturation == null ? device.currentValue("saturation") : value.saturation
    setValue.level = value.level == null ? device.currentValue("level") : value.level
    if(logEnable) log.debug "setColor updated values to $setValue."
    // Device HSL values get updated with parse()
    List<hubitat.zwave.Command> cmds = []
    List rgb = ColorUtils.hsvToRGB([setValue.hue, setValue.saturation, setValue.level])
    if(logEnable) log.debug "HSL Converted to R:${rgb[0]} G:${rgb[1]} B:${rgb[2]}"
    if (enableGammaCorrect) {
        cmds.add(zwave.switchColorV3.switchColorSet(red: gammaCorrect(rgb[0]), green: gammaCorrect(rgb[1]), blue: gammaCorrect(rgb[2]), warmWhite:0, dimmingDuration: duration))
    } else {
        cmds.add(zwave.switchColorV3.switchColorSet(red: rgb[0], green: rgb[1], blue: rgb[2], warmWhite:0, dimmingDuration: duration))
    }
    if ((device.currentValue("switch") != "on") && !colorPrestage) {
        if (logEnable) log.debug "Turning device on with pre-staging"
        cmds.add(zwave.basicV1.basicSet(value: 0xFF))
        cmds.add(zwave.switchMultilevelV3.switchMultilevelGet())
    }
    cmds.addAll(queryAllColors())
    eventProcess(name: "colorMode", value: "RGB")
    sendToDevice(cmds)
}

void setColorTemperature(temp) {
    // Sets the colorTemperature of a device
    int duration=colorDuration?colorDuration:1
    int warmWhite=0
    int coldWhite=0
    if (temp > COLOR_TEMP_MAX) temp = COLOR_TEMP_MAX
    if (!wwKelvin) {
        if (temp < COLOR_TEMP_MIN) temp = COLOR_TEMP_MIN
    } else {
        if (temp < wwKelvin) temp = wwKelvin
    }
    List<hubitat.zwave.Command> cmds = []
    if(logEnable) log.debug "setColorTemperature($temp)"
    if (wwComponent) {
        if(temp < wwKelvin) temp = wwKelvin
        state.ctTarget=temp
        int warmValue = ((COLOR_TEMP_MAX - temp) / COLOR_TEMP_DIFF_RGBW * 255) as Integer
        int coldValue = 255 - warmValue
        Map rgbTemp = ctToRgb(6500)
        cmds.add(zwave.switchColorV3.switchColorSet(red: gammaCorrect(coldValue), green: gammaCorrect(Math.round(coldValue*0.9765)), blue: gammaCorrect(Math.round(coldValue*0.9922)), warmWhite: gammaCorrect(warmValue), dimmingDuration: duration))
    } else {
        // LED strip is RGB and has no white
        Map rgbTemp = ctToRgb(temp)
        state.ctTarget=temp
        if(logEnable) log.debug "r: " + rgbTemp["r"] + " g: " + rgbTemp["g"] + " b: "+ rgbTemp["b"]
        if(logEnable) log.debug "r: " + gammaCorrect(rgbTemp["r"]) + " g: " + gammaCorrect(rgbTemp["g"]) + " b: " + gammaCorrect(rgbTemp["b"])
        cmds.add(zwave.switchColorV3.switchColorSet(red: gammaCorrect(rgbTemp["r"]), green: gammaCorrect(rgbTemp["g"]), blue: gammaCorrect(rgbTemp["b"]), warmWhite: 0, dimmingDuration: duration))
    }
    if ((device.currentValue("switch") != "on") && !colorPrestage) {
        if(logEnable) log.debug "Turning device on with pre-staging"
        cmds.add(zwave.basicV1.basicSet(value: 0xFF))
        cmds.add(zwave.switchMultilevelV3.switchMultilevelGet())
    }
    cmds.addAll(queryAllColors())
    eventProcess(name: "colorMode", value: "CT")
    sendToDevice(cmds)
}

private List<hubitat.zwave.Command> queryAllColors() {
    List<hubitat.zwave.Command> cmds=[]
    RGBW_NAMES.every { cmds.add(zwave.switchColorV3.switchColorGet(colorComponentId: ZWAVE_COLOR_COMPONENT_ID[it])) }
    return cmds
}

private Map ctToRgb(colorTemp) {
    // ct with rgb only
    float red=0
    float blue=0
    float green=0
    def temperature = colorTemp / 100
    red = 255
    green=(99.4708025861 *  Math.log(temperature)) - 161.1195681661
    if (green < 0) green = 0
    if (green > 255) green = 255
    if (temperature >= 65) {
        blue=255
    } else if (temperature <= 19) {
        blue=0
    } else {
        blue = temperature - 10
        blue = (138.5177312231 * Math.log(blue)) - 305.0447927307
        if (blue < 0) blue = 0
        if (blue > 255) blue = 255
    }
    return ["r": Math.round(red), "g": Math.round(green), "b": Math.round(blue)]
}

private int gammaCorrect(value) {
    def temp=value/255
    def correctedValue=(temp>0.4045) ? Math.pow((temp+0.055)/ 1.055, 2.4) : (temp / 12.92)
    return Math.round(correctedValue * 255) as Integer
}

void setGenericTempName(temp){
    if (!temp) return
    String genericName
    int value = temp.toInteger()
    if (value <= 2000) genericName = "Sodium"
    else if (value <= 2100) genericName = "Starlight"
    else if (value < 2400) genericName = "Sunrise"
    else if (value < 2800) genericName = "Incandescent"
    else if (value < 3300) genericName = "Soft White"
    else if (value < 3500) genericName = "Warm White"
    else if (value < 4150) genericName = "Moonlight"
    else if (value <= 5000) genericName = "Horizon"
    else if (value < 5500) genericName = "Daylight"
    else if (value < 6000) genericName = "Electronic"
    else if (value <= 6500) genericName = "Skylight"
    else if (value < 20000) genericName = "Polar"
    eventProcess(name: "colorName", value: genericName)
}

void setGenericName(hue){
    String colorName
    hue = Math.round(hue * 3.6) as Integer
    switch (hue){
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
    if (device.currentValue("saturation") == 0) colorName = "White"
    eventProcess(name: "colorName", value: colorName)
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
    for (int i = 2; i<=numberOfAssocGroups; i++) {
        if (logEnable) log.debug "group: $i dataValue: " + getDataValue("zwaveAssociationG$i") + " parameterValue: " + settings."associationsG$i"
        String parameterInput=settings."associationsG$i"
        List<String> newNodeList = []
        List<String> oldNodeList = []
        if (getDataValue("zwaveAssociationG$i") != null) {
            getDataValue("zwaveAssociationG$i").minus("[").minus("]").split(",").each {
                if (it != "") {
                    oldNodeList.add(it.minus(" "))
                }
            }
        }
        if (parameterInput != null) {
            parameterInput.minus("[").minus("]").split(",").each {
                if (it != "") {
                    newNodeList.add(it.minus(" "))
                }
            }
        }
        if (oldNodeList.size > 0 || newNodeList.size > 0) {
            if (logEnable) log.debug "${oldNodeList.size} - ${newNodeList.size}"
            oldNodeList.each {
                if (!newNodeList.contains(it)) {
                    // user removed a node from the list
                    if (logEnable) log.debug "removing node: $it, from group: $i"
                    cmds.add(zwave.associationV2.associationRemove(groupingIdentifier: i, nodeId: Integer.parseInt(it, 16)))
                }
            }
            newNodeList.each {
                cmds.add(zwave.associationV2.associationSet(groupingIdentifier: i, nodeId: Integer.parseInt(it, 16)))
            }
        }
        cmds.add(zwave.associationV2.associationGet(groupingIdentifier: i))
    }
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