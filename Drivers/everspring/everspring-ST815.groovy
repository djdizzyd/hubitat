import groovy.transform.Field

metadata {
    definition(name: "Everspring ST815 Illumination Sensor", namespace: "djdizzyd", author: "Bryan Copeland") {
        capability "IlluminanceMeasurement"
        capability "Sensor"
        capability "Battery"
        capability "Configuration"

        fingerprint mfr: "0060", deviceType: "0007", deviceId: "0001", inClusters:"0x31,0x86,0x72,0x85,0x84,0x80,0x70,0x20,0x71", deviceJoinName: "Everspring ST815" //US & EU

    }
    preferences {
        configParams.each { input it.value.input }
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

@Field static Map CMD_CLASS_VERS = [0x20: 1, 0x25: 1, 0x30: 1, 0x31: 5, 0x80: 1, 0x84: 1, 0x71: 3, 0x9C: 1, 0x31:1, 0x86:1, 0x70:1]

@Field static Map configParams = [
        5: [input: [name: "configParam5", type: "number", title: "Auto Report", description: "(time interval) minutes 0-1439", range: "0..1439", defaultValue: 0], parameterSize: 2],
        6: [input: [name: "configParam6", type: "number", title: "Auto Report", description: "(Lux Level) Lux 30-1000", range: "30..1000", defaultValue: 0], parameterSize: 2]
]

void logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

void configure() {
    if (!state.initialized) initializeVars()
    pollDeviceData()
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
    state.configChange=true
}

void pollDeviceData() {
    List<hubitat.zwave.Command> cmds = []
    cmds.add(zwave.wakeUpV1.wakeUpIntervalSet(seconds: 43200, nodeid:zwaveHubNodeId))
    cmds.add(zwave.wakeUpV1.wakeUpIntervalGet())
    cmds.add(zwave.batteryV1.batteryGet())
    cmds.add(zwave.versionV1.versionGet())
    cmds.add(zwave.sensorMultiLevelV1.sensorMultiLevelGet())
    sendToDevice(cmds)
}

void refresh() {
    List<hubitat.zwave.Command> cmds=[]
    log.info "${device.displayName}: refresh()"
    cmds.add(zwave.batteryV1.batteryGet())
    cmds.add(zwave.sensorMultiLevelV1.sensorMultiLevelGet())
    sendToDevice(cmds)
}

void installed() {
    if (logEnable) log.debug "installed()..."
    initializeVars()
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

List<hubitat.zwave.Command> configCmd(parameterNumber, size, scaledConfigurationValue) {
    List<hubitat.zwave.Command> cmds = []
    cmds.add(zwave.configurationV1.configurationSet(parameterNumber: parameterNumber.toInteger(), size: size.toInteger(), scaledConfigurationValue: scaledConfigurationValue.toInteger()))
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: parameterNumber.toInteger()))
    return cmds
}

void zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(CMD_CLASS_VERS)
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand)
    }
}

void zwaveEvent(hubitat.zwave.commands.versionv2.VersionReport cmd) {
    if (logEnable) log.debug "version3 report: ${cmd}"
    device.updateDataValue("firmwareVersion", "${cmd.applicationVersion}.${cmd.applicationSubVersion}")
    device.updateDataValue("protocolVersion", "${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}")
}

void parse(String description) {
    log.debug "parse:${description}"
    hubitat.zwave.Command cmd = zwave.parse(description, CMD_CLASS_VERS)
    if (cmd) {
        zwaveEvent(cmd)
    }
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

void zwaveEvent(hubitat.zwave.Command cmd) {
    if (logEnable) log.debug "skip:${cmd}"
}

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
    if (logEnable) log.debug "Basic report: ${cmd}"
    // basic is not needed here
}

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
    if (logEnable) log.debug "Basic set: ${cmd}"
    // basic is not needed here
}

void zwaveEvent(hubitat.zwave.commands.sensormultilevelv1.SensorMultilevelReport cmd) {
    if (logEnable) log.debug "Sensor Multilevel: ${cmd}"
    if (cmd.sensorType == 3) {
        eventProcess(name: "illuminance", value: Math.round(cmd.scaledSensorValue), unit: "lux")
    }
}

void zwaveEvent(hubitat.zwave.commands.wakeupv1.WakeUpNotification cmd) {
    List<hubitat.zwave.Command> cmds=[]
    cmds.add(zwave.batteryV1.batteryGet())
    cmds.add(zwave.sensorMultiLevelV1.sensorMultiLevelGet())
    if (state.configChange) cmds.addAll(runConfigs())
    cmds.add(zwave.wakeUpV1.wakeUpNoMoreInformation())
    sendToDevice(cmds)
}

void zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
    Map evt = [name: "battery", unit: "%", isStateChange: true]
    if (cmd.batteryLevel == 0xFF) {
        evt.descriptionText = "${device.displayName} has a low battery"
        evt.value = "1"
    } else {
        evt.descriptionText = "${device.displayName} battery is ${cmd.batteryLevel}%"
        evt.value = "${cmd.batteryLevel}"
    }
    if (txtEnable) log.info evt.descriptionText
    eventProcess(evt)
}

void eventProcess(Map evt) {
    if (device.currentValue(evt.name).toString() != evt.value.toString()) {
        evt.isStateChange=true
        sendEvent(evt)
    }
}