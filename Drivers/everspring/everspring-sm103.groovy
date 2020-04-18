import groovy.transform.Field

metadata {
    definition(name: "Everspring SM103 Contact Sensor", namespace: "djdizzyd", author: "Bryan Copeland") {
        capability "ContactSensor"
        capability "Sensor"
        capability "Battery"
        capability "Configuration"

        fingerprint mfr: "0060", prod: "0002", model: "0003", deviceJoinName: "Everspring Door/Window Sensor" //US & EU

    }
    preferences {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

@Field static Map CMD_CLASS_VERS = [0x20: 1, 0x25: 1, 0x30: 1, 0x31: 5, 0x80: 1, 0x84: 1, 0x71: 3, 0x9C: 1]

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
}

void pollDeviceData() {
    List<hubitat.zwave.Command> cmds = []
    cmds.add(zwave.wakeUpV1.wakeUpIntervalSet(seconds: 43200, nodeid:zwaveHubNodeId))
    cmds.add(zwave.wakeUpV1.wakeUpIntervalGet())
    cmds.add(zwave.batteryV1.batteryGet())
    cmds.add(zwave.sensorBinaryV2.sensorBinaryGet(sensorType: 10))
    cmds.add(zwave.basicV1.basicGet())
    sendToDevice(cmds)
}

void refresh() {
    List<hubitat.zwave.Command> cmds=[]
    log.info "${device.displayName}: refresh()"
    cmds.add(zwave.batteryV1.batteryGet())
    cmds.add(zwave.sensorBinaryV2.sensorBinaryGet(sensorType: 10))
    cmds.add(zwave.basicV1.basicGet())
    sendToDevice(cmds)
}

void installed() {
    if (logEnable) log.debug "installed()..."
    initializeVars()
}

void zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(CMD_CLASS_VERS)
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand)
    }
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
    log.debug "skip:${cmd}"
}

void sensorValueEvent(value) {
    if (value) {
        sendEvent(name: "contact", value: "open", descriptionText: "${device.displayName} is open")
    } else {
        sendEvent(name: "contact", value: "closed", descriptionText: "${device.displayName} is closed")
    }
}

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
    log.debug "Basic report: ${cmd}"
    sensorValueEvent(cmd.value)
}

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
    log.debug "Basic set: ${cmd}"
    sensorValueEvent(cmd.value)
}

void zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
    log.debug "switch binary report: ${cmd}"
    sensorValueEvent(cmd.value)
}

void zwaveEvent(hubitat.zwave.commands.sensorbinaryv1.SensorBinaryReport cmd) {
    log.debug "sensor binary: ${cmd}"
    sensorValueEvent(cmd.sensorValue)
}

void zwaveEvent(hubitat.zwave.commands.sensoralarmv1.SensorAlarmReport cmd) {
    log.debug "sensor alarm report ${cmd}"
    sensorValueEvent(cmd.sensorState)
}

void zwaveEvent(hubitat.zwave.commands.wakeupv1.WakeUpNotification cmd) {
    List<hubitat.zwave.Command> cmds=[]
    cmds.add(zwave.batteryV1.batteryGet())
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
    sendEvent(evt)
}