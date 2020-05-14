import groovy.transform.Field
/*
*	Wink Motion Sensor
*   v1.0
*/

metadata {

    definition (name: "Wink Lookout Motion Sensor", namespace: "djdizzyd", author: "Bryan Copeland", importUrl: "https://raw.githubusercontent.com/djdizzyd/hubitat/master/Drivers/Wink/motion-sensor.groovy") {
        capability "Actuator"
        capability "Sensor"
        capability "Configuration"
        capability "Battery"
        capability "MotionSensor"
        capability "Configuration"
        capability "IlluminanceMeasurement"

        fingerprint mfr:"017F", prod:"0101", deviceId:"0001", inClusters:"0x5E,0x86,0x72,0x5A,0x73,0x80,0x31,0x71,0x30,0x70,0x85,0x59,0x84", deviceJoinName: "Wink Lookout Motion Sensor"
    }
    preferences {
        configParams.each { input it.value.input }
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
    }
}

@Field static Map configParams = [
        1: [input: [name: "configParam1", type: "number", title: "Sensitivity Level", description: "8-255", range: "8..255", defaultValue: 8], parameterSize: 1],
        2: [input: [name: "configParam2", type: "number", title: "Motion Cleared Time", description: "seconds 5-600", range: "5..600", defaultValue: 5], parameterSize: 2],
        7: [input: [name: "configParam7", type: "number", title: "Light Sensing Interval", description: "seconds 50-3600", range: "60..3600", defaultValue: 3600], parameterSize: 2],
        10: [input: [name: "configParam10", type: "enum", title: "LED Indicator", description: "", defaultValue: 1, options: [0: "Disable", 1: "Enable"]], parameterSize: 1]
]


@Field static Map CMD_CLASS_VERS=[0x86:2,0x70:1,0x20:1,0x71:4,0x31:7]
@Field static Map ZWAVE_NOTIFICATION_TYPES=[0:"Reserverd", 1:"Smoke", 2:"CO", 3:"CO2", 4:"Heat", 5:"Water", 6:"Access Control", 7:"Home Security", 8:"Power Management", 9:"System", 10:"Emergency", 11:"Clock", 12:"First"]

void logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

void updated() {
    log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    log.warn "description logging is: ${txtEnable == true}"
    unschedule()
    if (logEnable) runIn(1800,logsOff)
    state.configChange=true
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

void eventProcess(Map evt) {
    if (device.currentValue(evt.name).toString() != evt.value.toString()) {
        evt.isStateChange=true
        sendEvent(evt)
    }
}

void zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpIntervalReport cmd) {
    state.wakeInterval=cmd.seconds
}

void zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpNotification cmd) {
    log.info "${device.displayName} Device wakeup notification"
    // let's do some wakeup stuff here
    List<hubitat.zwave.Command> cmds=[]
    cmds.add(zwave.batteryV1.batteryGet())
    if (state.configUpdated) {
        cmds.addAll(runConfigs())
        state.configUpdated=false
    }
    cmds.add(zwave.sensorMultilevelV7.sensorMultilevelGet(sensorType:3, scale:1))
    cmds.add(zwave.wakeUpV1.wakeUpNoMoreInformation())
    sendToDevice(cmds)
}

void installed() {
    initializeVars()
}

void uninstalled() {

}

void initializeVars() {
    // first run only
    sendEvent(name:"battery", value:100)
    state.initialized=true
}

void configure() {
    if (!state.initialized) initializeVars()
    runIn(5,pollDeviceData)
}

void refresh() {
    List<hubitat.zwave.Command> cmds = []
    cmds.add(zwave.sensorMultilevelV7.sensorMultilevelGet(sensorType:3, scale:1))
    cmds.add(zwave.notificationV4.notificationGet(notificationType: 7, event: 0))
    cmds.add(zwave.batteryV1.batteryGet())
    sendToDevice(cmds)
}

void pollDeviceData() {
    List<hubitat.zwave.Command> cmds = []
    cmds.add(zwave.versionV2.versionGet())
    cmds.add(zwave.manufacturerSpecificV2.deviceSpecificGet(deviceIdType: 1))
    cmds.add(zwave.batteryV1.batteryGet())
    cmds.add(zwave.notificationV4.notificationGet(notificationType: 7, event: 0))
    cmds.add(zwave.wakeUpV1.wakeUpIntervalSet(seconds: 43200, nodeid:getZwaveHubNodeId()))
    cmds.add(zwave.wakeUpV1.wakeUpIntervalGet())
    sendToDevice(cmds)
}

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
    if(logEnable) log.debug "Basic report: ${cmd.value}"
    // this is redundant/ambiguous and I don't care what happens here
}

void zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
    if(configParams[cmd.parameterNumber.toInteger()]) {
        Map configParam=configParams[cmd.parameterNumber.toInteger()]
        int scaledValue
        cmd.configurationValue.reverse().eachWithIndex { v, index -> scaledValue=scaledValue | v << (8*index) }
        device.updateSetting(configParam.input.name, [value: "${scaledValue}", type: configParam.input.type])
    }
}

void parse(String description) {
    log.debug "parse:${description}"
    hubitat.zwave.Command cmd = zwave.parse(description, CMD_CLASS_VERS)
    if (cmd) {
        zwaveEvent(cmd)
    }
}

void zwaveEvent(hubitat.zwave.Command cmd) {
    if (logEnable) log.debug "skip:${cmd}"
}

void zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(CMD_CLASS_VERS)
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand)
    }
}

void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd) {
    if (logEnable) log.debug "Supervision get: ${cmd}"
    if (cmd.commandClassIdentifier == 0x6F) {
        parseEntryControl(cmd.commandIdentifier, cmd.commandByte)
    } else {
        hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(CMD_CLASS_VERS)
        if (encapsulatedCommand) {
            zwaveEvent(encapsulatedCommand)
        }
    }
    sendToDevice(new hubitat.zwave.commands.supervisionv1.SupervisionReport(sessionID: cmd.sessionID, reserved: 0, moreStatusUpdates: false, status: 0xFF, duration: 0))
}

void zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
    Map evt = [name: "battery", unit: "%"]
    if (cmd.batteryLevel == 0xFF) {
        evt.descriptionText = "${device.displayName} has a low battery"
        evt.value = "1"
    } else {
        evt.descriptionText = "${device.displayName} battery is ${cmd.batteryLevel}%"
        evt.value = "${cmd.batteryLevel}"
    }
    eventProcess(evt)
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

void zwaveEvent(hubitat.zwave.commands.sensormultilevelv7.SensorMultilevelReport cmd) {
    if (logEnable) log.debug "${cmd}"
    Map evt = [:]
    switch (cmd.sensorType) {
        case 3:
            evt.name = "illuminance"
            evt.value = cmd.scaledSensorValue.toInteger()
            evt.unit = "lux"
            evt.description="${device.displayName}: Illuminance report received: ${evt.value}"
            eventProcess(evt)
            break
    }
}

void zwaveEvent(hubitat.zwave.commands.notificationv4.NotificationReport cmd) {
    Map evt = [:]
    log.info "Notification: " + ZWAVE_NOTIFICATION_TYPES[cmd.notificationType.toInteger()]
    if (cmd.notificationType==7) {
        // home security
        switch (cmd.event) {
            case 0:
                // state idle
                if (cmd.eventParametersLength>0) {
                    switch (cmd.eventParameter[0]) {
                        case 7:
                            evt.name="motion"
                            evt.value="inactive"
                            evt.descriptionText="${device.displayName} motion became ${evt.value}"
                            eventProcess(evt)
                            break
                        case 8:
                            evt.name="motion"
                            evt.value="inactive"
                            evt.descriptionText="${device.displayName} motion became ${evt.value}"
                            eventProcess(evt)
                            break

                    }
                } else {
                    // should probably do something here
                    evt.name="motion"
                    evt.value="inactive"
                    evt.descriptionText="${device.displayName} motion became ${evt.value}"
                    eventProcess(evt)
                }
                break
            case 7:
                // motion detected (location provided)
                evt.name="motion"
                evt.value="active"
                evt.descriptionText="${device.displayName} motion became ${evt.value}"
                eventProcess(evt)
                break
            case 8:
                // motion detected
                evt.name="motion"
                evt.value="active"
                evt.isStateChange=true
                evt.descriptionText="${device.displayName} motion became ${evt.value}"
                eventProcess(evt)
                break
            case 254:
                // unknown event/state
                log.warn "Device sent unknown event / state notification"
                break
        }
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
    String encap=""
    if (getDataValue("zwaveSecurePairingComplete") != "true") {
        return cmd
    } else {
        encap = "988100"
    }
    return "${encap}${cmd}"
}