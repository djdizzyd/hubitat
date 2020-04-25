/*
*   Micro Dimmer Controller Driver
*	Code written for RGBGenie by Bryan Copeland
*
*
*   Updated 2020-02-26 Added importUrl
*   Updated 2020-04-07 Re-write for current coding standards
*   Updated 2020-04-11 Added duplicate event filtering
*
*/
import groovy.transform.Field

metadata {
    definition (name: "RGBGenie Micro Controller ZW",namespace: "rgbgenie", author: "RGBGenie", importUrl: "https://raw.githubusercontent.com/RGBGenie/Hubitat_RGBGenie/master/Drivers/Zwave-Micro-Controller.groovy") {
        capability "Actuator"
        capability "Switch"
        capability "SwitchLevel"
        capability "ChangeLevel"
        capability "Refresh"
        capability "Configuration"

        fingerprint mfr: "0330", prod: "0201", deviceId: "D005", inClusters:"0x5E,0x72,0x86,0x26,0x2B,0x2C,0x71,0x70,0x85,0x59,0x73,0x5A,0x55,0x98,0x9F,0x6C,0x7A", deviceJoinName: "RGBGenie Micro Controller"

    }
    preferences {
        configParams.each { input it.value.input }
        input name: "logEnable", type: "bool", description: "", title: "Enable Debug Logging", defaultValue: true
    }
}

@Field static Map configParams = [
        2: [input: [name: "configParam2", type: "enum", title: "Power fail load state restore", description: "", defaultValue: 0, options: [0: "Shut Off Load", 1:"Turn On Load", 2:"Restore Last State"]], parameterSize: 1],
        4: [input: [name: "configParam4", type: "number", title: "Default Fade Time 0-254", description: "seconds", range: "0..254", defaultValue: 1], parameterSize: 1],
        5: [input: [name: "configParam5", type: "number", title: "Minimum Level", description: "percent", range: "0..50", defaultValue: 0], parameterSize: 1],
        6: [input: [name: "configParam6", type: "enum", title: "MOSFET Driving Type", description: "",  defaultValue: 0, options: [0: "Trailing Edge", 1:"Leading Edge"]], parameterSize: 1]
]
@Field static Map CMD_CLASS_VERS=[0x20:1,0x26:3,0x85:2,0x71:8,0x20:1,0x70:1]

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
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 2))
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 4))
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 5))
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 6))
    sendToDevice(cmds)
    state.initialized=true
}

void updated() {
    log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    unschedule()
    if (logEnable) runIn(1800,logsOff)
    runConfigs()
}

void runConfigs() {
    List<hubitat.zwave.Command> cmds=[]
    configParams.each { param, data ->
        if (settings[data.input.name]) {
            cmds.addAll(configCmd(param, data.parameterSize, settings[data.input.name]))
        }
    }
    sendToDevice(cmds)
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

void zwaveEvent(hubitat.zwave.commands.notificationv8.NotificationReport cmd) {
    if(logEnable) logDebug "Notification received: ${cmd}"
    if (cmd.notificationType == 9) {
        if (cmd.event == 7) {
            log.warn "Emergency shutoff load malfunction"
        }
    }
}

void installed() {
    device.updateSetting("logEnable", [value: "true", type: "bool"])
    runIn(1800,logsOff)
}

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
    dimmerEvents(cmd)
}

void zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd) {
    dimmerEvents(cmd)
}

private void dimmerEvents(hubitat.zwave.Command cmd) {
    String value = (cmd.value ? "on" : "off")
    eventProcess(name: "switch", value: value, descriptionText: "$device.displayName was turned $value", isStateChange: true)
    if (cmd.value) {
        eventProcess(name: "level", isStateChange: true, value: cmd.value == 99 ? 100 : cmd.value , unit: "%")
    }
}

void zwaveEvent(hubitat.zwave.Command cmd) {
    if (logEnable) log.debug "skip:${cmd}"
}

void refresh() {
    List<hubitat.zwave.Command> cmds=[]
    cmds.add(zwave.switchMultilevelV3.switchMultilevelGet())
    cmds.add(zwave.basicV1.basicGet())
    sendToDevice(cmds)
}

void on() {
    List<hubitat.zwave.Command> cmds=[]
    cmds.add(zwave.basicV1.basicSet(value: 0xFF))
    sendToDevice(cmds)
    runIn(2,refresh)
}

void off() {
    List<hubitat.zwave.Command> cmds=[]
    cmds.add(zwave.basicV1.basicSet(value: 0x00))
    sendToDevice(cmds)
    runIn(2,refresh)
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
    sendToDevice(cmds)
    runIn(1,refresh)
}

void setLevel(level) {
    setLevel(level, 1)
}

void setLevel(level, duration) {
    if (level > 99) level = 99
    if (logEnable) log.debug "setLevel($level, $duration)"
    List<hubitat.zwave.Command> cmds=[]
    cmds.add(zwave.switchMultilevelV3.switchMultilevelSet(value: level, dimmingDuration: duration))
    sendToDevice(cmds)
    runIn(duration, refresh)
}