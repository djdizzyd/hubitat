/*
*	LED Controller Driver
*	Code written for RGBGenie by Bryan Copeland with major contributions from Adam Kempenich
*
*   Updated 2020-02-26 Added importUrl and optional gamma correction on setColor events
*   Updated 2020-04-08 Update to current coding standards
*   Updated 2020-04-11 Added duplicate event filtering
*   Updated 2020-04-30 Updated CCT Range
*   Updated 2020-05-01 Fixed stupid mistake on config updating
*
*/


import groovy.json.JsonOutput
import hubitat.helper.ColorUtils
import groovy.transform.Field

metadata {
    definition (name: "RGBGenie LED Controller ZW", namespace: "rgbgenie", author: "RGBGenie", importUrl: "https://raw.githubusercontent.com/RGBGenie/Hubitat_RGBGenie/master/Drivers/Zwave-LED-Controller.groovy" ) {
        capability "Actuator"
        capability "ChangeLevel"
        capability "ColorControl"
        capability "ColorMode"
        capability "ColorTemperature"
        capability "Configuration"
        capability "HealthCheck"
        capability "LightEffects"
        capability "Refresh"
        capability "Sensor"
        capability "Switch"
        capability "SwitchLevel"

        attribute "colorMode", "string"
        attribute "lightEffects", "JSON_OBJECT"

        command "testRed"
        command "testGreen"
        command "testBlue"
        command "testWW"
        command "testCW"

        fingerprint mfr: "0330", prod: "0200", deviceId: "D002", inClusters:"0x5E,0x72,0x86,0x26,0x33,0x2B,0x2C,0x71,0x70,0x85,0x59,0x73,0x5A,0x55,0x98,0x9F,0x6C,0x7A", deviceJoinName: "RGBGenie LED Controller" // EU
        fingerprint mfr: "0330", prod: "0201", deviceId: "D002", inClusters:"0x5E,0x72,0x86,0x26,0x33,0x2B,0x2C,0x71,0x70,0x85,0x59,0x73,0x5A,0x55,0x98,0x9F,0x6C,0x7A", deviceJoinName: "RGBGenie LED Controller" // US
        fingerprint mfr: "0330", prod: "0202", deviceId: "D002", inClusters:"0x5E,0x72,0x86,0x26,0x33,0x2B,0x2C,0x71,0x70,0x85,0x59,0x73,0x5A,0x55,0x98,0x9F,0x6C,0x7A", deviceJoinName: "RGBGenie LED Controller" // ANZ
        fingerprint mfr: "0330", prod: "021A", deviceId: "D002", inClusters:"0x5E,0x72,0x86,0x26,0x33,0x2B,0x2C,0x71,0x70,0x85,0x59,0x73,0x5A,0x55,0x98,0x9F,0x6C,0x7A", deviceJoinName: "RGBGenie LED Controller" // RU

    }
    preferences {

        input name: "logEnable", type: "bool", description: "", title: "Enable Debug Logging", defaultValue: true

        if (getDataValue("deviceModel")=="" || getDataValue("deviceModel")==null) {
            input description: "The device type has not been detected.. Please press the configure button", title: "Device Type Detection", displayDuringSetup: false, type: "paragraph", element: "paragraph"
        } else {
            input name: "dimmerSpeed", type: "number", description: "", title: "Dimmer Ramp Rate 0-255", defaultValue: 0, required: true
            input name: "loadStateSave", type: "enum", description: "", title: "Power fail load state restore", defaultValue: 0, required: true, options: [0: "Shut Off Load", 1: "Turn On Load", 2: "Restore Last State"]
            input name: "deviceType", type: "enum", description: "", title: "Change Device Type", defaultValue: getDataValue("deviceModel"), required: false, options: [0: "Single Color", 1: "CCT", 2: "RGBW"]

            if (getDataValue("deviceModel") == "1" || getDataValue("deviceModel")=="2") {
                // Color, or Color Temperature Model-specific settings
                input name: "colorPrestage", type: "bool", description: "", title: "Enable Color Prestaging", defaultValue: false, required: true
                input name: "colorDuration", type: "number", description: "", title: "Color Transition Duration", defaultValue: 3, required: true
                input name: "wwKelvin", type: "number", description: "", title: "Warm White Temperature", defaultValue: 2700, required: true
            }
            if (getDataValue("deviceModel")=="1") {
                input name: "cwKelvin", type: "number", description: "", title: "Cold White Temperature", defaultValue: 6500, required: true
            }
            if (getDataValue("deviceModel")=="2") {
                // Color Model-specific settings
                input name: "wwComponent", type: "bool", description: "", title: "Enable Warm White Component", defaultValue: true, required: true
                input name: "enableGammaCorrect", type: "bool", description: "May cause a slight difference in reported color", title: "Enable gamma correction on setColor", defaultValue: false, required: true
            }
            input name: "stageModeSpeed", type: "number", description: "", title: "Light Effect Speed 0-255 (default 243)", defaultValue: 243, required: true
            input name: "stageModeHue", type: "number", description: "", title: "Hue Of Fixed Color Light Effects 0-360", defaultValue: 0, required: true
            if (getDataValue("deviceModel") == "2" && wwComponent) {
                input description: "<table><tr><th>Number</th><th>Color Component</th></tr><tr><td>1</td><td>Red</td></tr><tr><td>2</td><td>Green</td></tr><tr><td>3</td><td>Blue</td></tr><td>4</td><td>WarmWhite</td></tr></table>", title: "Output Descriptions", displayDuringSetup: false, type: "paragraph", element: "paragraph"
            } else if (getDataValue("deviceModel") == "2" && !wwComponent) {
                input description: "<table><tr><th>Number</th><th>Color Component</th></tr><tr><td>1</td><td>Red</td></tr><tr><td>2</td><td>Green</td></tr><tr><td>3</td><td>Blue</td></tr><td>4</td><td>Empty</td></tr></table>", title: "Output Descriptions", displayDuringSetup: false, type: "paragraph", element: "paragraph"
            } else if (getDataValue("deviceModel") == "1") {
                input description: "<table><tr><th>Number</th><th>Color Component</th></tr><tr><td>1</td><td>WarmWhite</td></tr><tr><td>2</td><td>ColdWhite</td></tr><tr><td>3</td><td>WarmWhite</td></tr><td>4</td><td>ColdWhite</td></tr></table>", title: "Output Descriptions", displayDuringSetup: false, type: "paragraph", element: "paragraph"
            }
        }
    }
}


@Field static String RED="red"
@Field static String GREEN="green"
@Field static String BLUE="blue"
@Field static String WARM_WHITE="warmWhite"
@Field static String COLD_WHITE="coldWhite"
@Field static List<String> RGBW_NAMES=["red", "green", "blue", "warmWhite"]
@Field static List<String> RGB_NAMES=["red", "green", "blue"]
@Field static List<String> CCT_NAMES=["warmWhite", "coldWhite"]

@Field static int COLOR_TEMP_MIN=2700
@Field static int COLOR_TEMP_MAX=6500
@Field static Map CMD_CLASS_VERS=[0x33:3,0x26:3,0x85:2,0x71:8,0x20:1]
@Field static Map ZWAVE_COLOR_COMPONENT_ID=[warmWhite: 0, coldWhite: 1, red: 2, green: 3, blue: 4]
@Field static Map lightEffects=[
        0:"None",
        1:"Fade in/out mode, fixed color",
        2:"Flash mode fixed color",
        3:"Rainbow Mode, fixed change effect",
        4:"Fade in/out mode, color changes randomly",
        5:"Flash Mode, color changes randomly",
        6:"Rainbow Mode, color changes randomly",
        7:"Random Mode"
]
private int getCOLOR_TEMP_DIFF_RGBW() {  COLOR_TEMP_MAX - wwKelvin }
private int getCOLOR_TEMP_DIFF() {  (cwKelvin?cwKelvin.toInteger():COLOR_TEMP_MAX) - (wwKelvin?wwKelvin.toInteger():COLOR_TEMP_MIN) }

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
    List<hubitat.zwave.Command> cmds=[]
    cmds.add(zwave.configurationV1.configurationSet(parameterNumber: 3, size: 1, scaledConfigurationValue: 1))
    cmds.add(zwave.switchMultilevelV3.switchMultilevelGet())
    cmds.add(zwave.basicV1.basicGet())
    sendToDevice(cmds)
    sendEvent(name:"lightEffects", value: JsonOutput.toJson(lightEffects))
    state.initialized=true
}

void pollDeviceData() {
    List<hubitat.zwave.Command> cmds = []
    cmds.add(zwave.versionV2.versionGet())
    cmds.add(zwave.manufacturerSpecificV2.deviceSpecificGet(deviceIdType: 1))
    cmds.add(zwave.configurationV2.configurationGet(parameterNumber: 4))
    cmds.add(zwave.associationV2.associationGet(groupingIdentifier:1))
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
    String encap = ""
    if (getDataValue("zwaveSecurePairingComplete") != "true") {
        return cmd
    } else {
        encap = "988100"
    }
    return "${encap}${cmd}"
}

void testRed() {
    sendToDevice(zwave.switchColorV3.switchColorSet(red: 255, green: 0, blue: 0, warmWhite:0, coldWhite:0))
}

void testGreen(){
    sendToDevice(zwave.switchColorV3.switchColorSet(red: 0, green: 255, blue: 0, warmWhite:0, coldWhite:0))
}

void testBlue(){
    sendToDevice(zwave.switchColorV3.switchColorSet(red: 0, green: 0, blue: 255, warmWhite:0, coldWhite:0))
}

void testWW(){
    sendToDevice(zwave.switchColorV3.switchColorSet(red: 0, green: 0, blue: 0, warmWhite: 255, coldWhite:0))
}

void testCW(){
    sendToDevice(zwave.switchColorV3.switchColorSet(red: 0, green: 0, blue: 0, warmWhite: 0, coldWhite:255))
}

void updated() {
    log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    unschedule()
    if (logEnable) runIn(1800,logsOff)
    List<hubitat.zwave.Command> cmds = []
    if(logEnable) log.debug "deviceModel: "+getDataValue("deviceModel") + " Updated setting: ${deviceType}"
    if (getDataValue("deviceModel") != deviceType.toString()) {
        cmds.add(zwave.configurationV2.configurationSet(parameterNumber: 4, size: 1, scaledConfigurationValue: deviceType.toInteger()))
        cmds.add(zwave.configurationV2.configurationGet(parameterNumber: 4))
    }
    cmds.add(zwave.configurationV2.configurationSet(parameterNumber: 2, size: 1, scaledConfigurationValue: loadStateSave.toInteger()))
    cmds.add(zwave.configurationV2.configurationSet(parameterNumber: 6, size: 1, configurationValue: [stageModeSpeed.toInteger()]))
    cmds.add(zwave.configurationV2.configurationSet(parameterNumber: 8, size: 1, scaledConfigurationValue: hueToHueByte(stageModeHue.toInteger())))
    sendToDevice(cmds)
}

private int hueToHueByte(int hueValue) {
    // hue as 0-360 return hue as 0-255
    return Math.round(hueValue / (360/255))
}

void installed() {
    log.info "installed()..."
}

void zwaveEvent(hubitat.zwave.commands.associationv2.AssociationGroupingsReport cmd) {
    if(logEnable) log.debug "Supported association groups: ${cmd.supportedGroupings}"
}

void zwaveEvent(hubitat.zwave.commands.associationgrpinfov3.AssociationGroupCommandListReport cmd) {
    if(logEnable) log.debug "association group command list report: ${cmd}"
}

void zwaveEvent(hubitat.zwave.commands.associationgrpinfov3.AssociationGroupInfoReport cmd) {
    if(logEnable) log.debug "association group info report"
}

void zwaveEvent(hubitat.zwave.commands.associationcommandconfigurationv1.CommandRecordsSupportedReport cmd) {
    if(logEnable) log.debug "association command config supported: ${cmd}"
}

void zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
    if (cmd.nodeId.any { it == zwaveHubNodeId }) {
        eventProcess(descriptionText: "$device.displayName is associated in group ${cmd.groupingIdentifier}")
    } else if (cmd.groupingIdentifier == 1) {
        eventProcess(descriptionText: "Associating $device.displayName in group ${cmd.groupingIdentifier}")
        sendToDevice(zwave.associationV1.associationSet(groupingIdentifier:cmd.groupingIdentifier, nodeId:zwaveHubNodeId))
    }
}

void zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd) {
    int scaledValue
    cmd.configurationValue.reverse().eachWithIndex { v, index -> scaledValue=scaledValue | v << (8*index) }
    if(logEnable) log.debug "got ConfigurationReport: $cmd"
    switch (cmd.parameterNumber) {
        case 4:
            device.updateDataValue("deviceModel", "${scaledValue}")
            if (scaledValue!=state.deviceType) {
                state.deviceType=(scaledValue)
            }
            runIn(1, refresh)
            break
        case 5:
            eventProcess(name: "effectName", value: lightEffects[scaledValue])
            state.effectNumber=scaledValue
            break
    }
}

void setEffect(effectNumber) {
    if(logEnable) log.debug "Got setEffect " + effectNumber
    List<hubitat.zwave.Command> cmds=[]
    cmds.add(zwave.configurationV2.configurationSet(parameterNumber: 5, size: 1, scaledConfigurationValue: effectNumber.toInteger()))
    cmds.add(zwave.configurationV2.configurationGet(parameterNumber: 5))
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
    switch (getDataValue("deviceModel")) {
        case "1":
            // CCT Device Type
            if (CCT_NAMES.every { state.colorReceived[it] != null }) {
                // Got all CCT colors
                int warmWhite = state.colorReceived["warmWhite"]
                int coldWhite = state.colorReceived["coldWhite"]
                int colorTemp = (wwKelvin?wwKelvin.toInteger():COLOR_TEMP_MIN) + (COLOR_TEMP_DIFF / 2)
                if (warmWhite != coldWhite) {
                    colorTemp = ((cwKelvin?cwKelvin.toInteger():COLOR_TEMP_MAX) - (COLOR_TEMP_DIFF * warmWhite) / 255) as Integer
                }
                eventProcess(name: "colorTemperature", value: colorTemp)
                // clear state values
                CCT_NAMES.collect { state.colorReceived[it] = null }
            }
            break
        case "2":
            // RGBW Device Type
            if (RGBW_NAMES.every { state.colorReceived[it] != null }) {
                if (device.currentValue("colorMode") == "RGB") {
                    List hsv=ColorUtils.rgbToHSV([state.colorReceived["red"], state.colorReceived["green"], state.colorReceived["blue"]])
                    int hue=hsv[0]
                    int sat=hsv[1]
                    int lvl=hsv[2]
                    if (hue != device.currentValue("hue")) {
                        eventProcess(name:"hue", value:Math.round(hue), unit:"%")
                        setGenericName(hue)
                    }
                    if (sat != device.currentValue("saturation")) {
                        eventProcess(name:"saturation", value:Math.round(sat), unit:"%")
                    }
                    if (lvl != device.currentValue("level")) {
                        eventProcess(name:"level", value:Math.round(lvl), unit:"%")
                    }
                } else {
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
                // clear state values
                RGBW_NAMES.collect { state.colorReceived[it] = null }
            }
            break
    }
}

private void dimmerEvents(hubitat.zwave.Command cmd) {
    String value = (cmd.value ? "on" : "off")
    eventProcess(name: "switch", value: value, descriptionText: "$device.displayName was turned $value")
    if (cmd.value) {
        eventProcess(name: "level", value: cmd.value == 99 ? 100 : cmd.value , unit: "%")
    }
}

void zwaveEvent(hubitat.zwave.commands.notificationv8.NotificationReport cmd) {
    if(logEnable) log.debug"Notification received: ${cmd}"
    if (cmd.notificationType == 9) {
        if (cmd.event == 7) {
            logWarn "Emergency shutoff load malfunction"
        }
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
    cmds.add(zwave.configurationV2.configurationGet(parameterNumber: 5))
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
    int duration=colorDuration?colorDuration:3
    if(logEnable) log.debug  "setColor($value)"
    if(value.hue > 100) value.hue=100
    if (state.deviceType==2) {
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
    } else {
        log.trace "setColor not supported on this device type"
    }
}

void setColorTemperature(temp) {
    // Sets the colorTemperature of a device
    int duration=colorDuration?colorDuration:3
    int warmWhite=0
    int coldWhite=0
    if (!cwKelvin) {
        if (temp > COLOR_TEMP_MAX) temp = COLOR_TEMP_MAX
    } else {
        if (temp > cwKelvin) temp = cwKelvin
    }
    if (!wwKelvin) {
        if (temp < COLOR_TEMP_MIN) temp = COLOR_TEMP_MIN
    } else {
        if (temp < wwKelvin) temp = wwKelvin
    }
    List<hubitat.zwave.Command> cmds = []
    if(logEnable) log.debug "setColorTemperature($temp)"
    switch (getDataValue("deviceModel")) {
        case "0":
            // Single Color Device Type
            log.trace "setColorTemperature not supported on this device type"
            return
            break
        case "1":
            // Full CCT Devie Type
            state.ctTarget=temp
            warmValue = (((cwKelvin?cwKelvin.toInteger():COLOR_TEMP_MAX) - temp) / COLOR_TEMP_DIFF * 255) as Integer
            coldValue = 255 - warmValue
            log.debug "temp: $temp - warm: $warmValue - cold: $coldValue ctDiff: $COLOR_TEMP_DIFF"
            cmds.add(zwave.switchColorV3.switchColorSet(warmWhite: warmValue, coldWhite: coldValue, dimmingDuration: duration))
            break
        case "2":
            // RGBW Device type
            if (wwComponent) {
                // LED strip has warm white
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
            break
    }
    if ((device.currentValue("switch") != "on") && !colorPrestage) {
        if(logEnable) log.debug "Turning device on with pre-staging"
        cmds.add(zwave.basicV1.basicSet(value: 0xFF))
        cmds.add(zwave.switchMultilevelV3.switchMultilevelGet())
    }
    cmds.addAll(queryAllColors())
    eventProcess(name: "colorMode", value: "CT")
    log.debug(cmds)
    sendToDevice(cmds)
}

private List<hubitat.zwave.Command> queryAllColors() {
    List<hubitat.zwave.Command> cmds=[]
    switch (getDataValue("deviceModel")) {
        case "1":
            // cct device type
            CCT_NAMES.collect { cmds.add(zwave.switchColorV3.switchColorGet(colorComponentId: ZWAVE_COLOR_COMPONENT_ID[it])) }
            break
        case "2":
            // rgbw device type
            RGBW_NAMES.collect { cmds.add(zwave.switchColorV3.switchColorGet(colorComponentId: ZWAVE_COLOR_COMPONENT_ID[it])) }
            break
    }
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

