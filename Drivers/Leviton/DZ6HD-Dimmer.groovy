import groovy.transform.Field

metadata {
    definition (name: "Leviton DZ6HD Dimmer", namespace: "djdizzyd", author: "Bryan Copeland", importUrl: "") {
        capability "SwitchLevel"
        capability "Switch"
        capability "Refresh"
        capability "Actuator"
        capability "Sensor"
        capability "Configuration"
        capability "ChangeLevel"

        fingerprint mfr: "001D", prod: "3201", deviceId:"0001", inClusters:"0x5E,0x85,0x59,0x86,0x72,0x70,0x5A,0x73,0x26,0x20,0x27,0x2C,0x2B,0x7A", outClusters:"0x82", deviceJoinName: "Leviton DZ6HD Dimmer" //US

    }
    preferences {
        configParams.each { input it.value.input }
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}
@Field static Map configParams = [
        1: [input: [name: "configParam1", type: "number", title: "Fade on time", description: "0-253", defaultValue: 0, ranges: "0..253"], parameterSize: 1],
        2: [input: [name: "configParam2", type: "number", title: "Fade off time", description: "0-253", defaultValue: 0, ranges: "0..253"], parameterSize:1],
        3: [input: [name: "configParam3", type: "number", title: "Minimum Level", description: "0-100", defaultValue: 0, ranges: "0..100"], parameterSize:1],
        4: [input: [name: "configParam4", type: "number", title: "Maximum Level", description: "0-100", defaultValue: 100, ranges: "0..100"], parameterSize:1],
        5: [input: [name: "configParam5", type: "number", title: "Preset Level", description: "0-100 0: Last State", defaultValue:0, ranges: "0..100"], parameterSize:1],
        6: [input: [name: "configParam6", type: "number", title: "LED Dim Level Indicator Timeout", description: "0-255 0: always off, 1-254 seconds, 255: Always On", ranges: "0..255"], parameterSize:1],
        7: [input: [name: "configParam7", type: "enum", title: "LED Status", options: [0x00: "LED Off", 0xFE:"On When Load is On", 0xFF:"On When Load is Off"]], parameterSize: 1],
        8: [input: [name: "configParam8", type: "enum", title: "Load Type", options: [0x00: "Incandescent", 0x01: "LED", 0x02: "CFL"]], parameterSize:1]
]
@Field static Map CMD_CLASS_VERS=[0x26:1,0x86:1,0x70:1]

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
    state.initialized=true
    runIn(5, refresh)
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
    cmds.add(zwave.versionV1.versionGet())
    sendToDevice(cmds)
}

void refresh() {
    List<hubitat.zwave.Command> cmds=[]
    cmds.add(zwave.switchMultilevelV1.switchMultilevelGet())
    cmds.add(zwave.basicV1.basicGet())
    sendToDevice(cmds)
}

void installed() {
    if (logEnable) log.debug "installed()..."
    sendEvent(name: "level", value: 100, unit: "%")
    sendEvent(name: "switch", value: "on")
    initializeVars()
}

void eventProcess(Map evt) {
    if (device.currentValue(evt.name).toString() != evt.value.toString() || !eventFilter) {
        evt.isStateChange=true
        sendEvent(evt)
    }
}

void startLevelChange(direction) {
    boolean upDownVal = direction == "down" ? true : false
    if (logEnable) log.debug "got startLevelChange(${direction})"
    sendToDevice(zwave.switchMultilevelV1.switchMultilevelStartLevelChange(ignoreStartLevel: true, startLevel: device.currentValue("level"), upDown: upDownVal))
}

void stopLevelChange() {
    sendToDevice(zwave.switchMultilevelV1.switchMultilevelStopLevelChange())
}

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
    if (logEnable) log.debug cmd
    dimmerEvents(cmd)
}

void zwaveEvent(hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelReport cmd) {
    if (logEnable) log.debug cmd
    dimmerEvents(cmd)
}

private void dimmerEvents(hubitat.zwave.Command cmd) {
    def value = (cmd.value ? "on" : "off")
    eventProcess(name: "switch", value: value, descriptionText: "$device.displayName was turned $value")
    if (cmd.value) {
        eventProcess(name: "level", value: cmd.value == 99 ? 100 : cmd.value , unit: "%")
    }
}

void on() {
    sendToDevice(zwave.basicV1.basicSet(value: 0xFF))
}

void off() {
    sendToDevice(zwave.basicV1.basicSet(value: 0x00))
}

void setLevel(level) {
    if (logEnable) log.debug "setLevel($level)"
    if(level > 99) level = 99
    sendToDevice(zwave.switchMultilevelV1.switchMultilevelSet(value: level))
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

void zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
    if (logEnable) log.debug "version1 report: ${cmd}"
    device.updateDataValue("firmwareVersion", "${cmd.applicationVersion}.${cmd.applicationSubVersion}")
    device.updateDataValue("protocolVersion", "${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}")
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

void zwaveEvent(hubitat.zwave.Command cmd) {
    if (logEnable) log.debug "skip:${cmd}"
}
