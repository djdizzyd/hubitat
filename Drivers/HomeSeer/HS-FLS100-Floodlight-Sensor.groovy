/*
*	HomeSeer HS-FLS100+ Floodlight Sensor
*	version: 1.0
*/

import groovy.transform.Field

metadata {
    definition (name: "HomeSeer HS-FLS100+ Floodlight Sensor", namespace: "djdizzyd", author: "Bryan Copeland", importUrl: "") {
        capability "Switch"
        capability "Refresh"
        capability "Actuator"
        capability "Sensor"
        capability "Configuration"
        capability "MotionSensor"
        capability "IlluminanceMeasurement"

        fingerprint mfr:"000C", prod:"0201", deviceId:"000B", inClusters:"0x5E,0x85,0x59,0x55,0x86,0x72,0x5A,0x73,0x9F,0x6C,0x7A,0x71,0x25,0x31,0x70,0x30", deviceJoinName: "HomeSeer HS-FLS100+"
    }
    preferences {
        configParams.each { input it.value.input }
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}
@Field static Map configParams = [
        1: [input: [name: "configParam1", type: "number", title: "PIR Trigger Off period", description: "seconds 8-720", defaultValue: 15, ranges:"8..720"], parameterSize: 2],
        2: [input: [name: "configParam2", type: "enum", title: "Lux Threshold Settings", description: "", defaultValue: 50, options:[0:"Always don't turn on light",255:"Turn Light on by PIR",30:"Turn light on @ 30 Lux",40:"Turn light on @ 40 lux",50:"Turn light on @ 50 lux", 60:"Turn light on @ 60 lux",70:"Turn light on @ 70 lux",80:"Turn light on @ 80 lux", 90:"Turn light on @ 90 lux", 100:"Turn light on @ 100 lux", 110:"Turn light on @ 110 lux", 120:"Turn light on @ 120 lux", 130:"Turn light on @ 130 lux", 140:"Turn light on @ 140 lux", 150:"Turn light on @ 150 lux", 160:"Turn light on @ 160 lux", 170:"Turn light on @ 170 lux", 180:"Turn light on @ 180 lux", 190:"Turn light on @ 190 lux", 200:"Turn light on @ 200 lux"]], parameterSize: 2],
        3: [input: [name: "configParam3", type: "number", title: "Lux level report", description: "minutes 0 to 1440 minutes", defaultValue: 10, ranges:"0..1440"], parameterSize: 2]
]
@Field static Map ZWAVE_NOTIFICATION_TYPES=[0:"Reserverd", 1:"Smoke", 2:"CO", 3:"CO2", 4:"Heat", 5:"Water", 6:"Access Control", 7:"Home Security", 8:"Power Management", 9:"System", 10:"Emergency", 11:"Clock", 12:"First"]
@Field static Map CMD_CLASS_VERS=[0x20:1,0x86:2,0x72:2,0x5B:3,0x70:1,0x85:2,0x59:1,0x31:5,0x71:4]

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
    cmds.add(zwave.sensorMultilevelV5.sensorMultilevelGet(scale: 1, sensorType: 3))
    cmds.add(zwave.notificationV4.notificationGet(notificationType: 7, event:0))
    cmds.add(zwave.switchBinaryV1.switchBinaryGet())
    sendToDevice(cmds)
}

void refresh() {
    List<hubitat.zwave.Command> cmds=[]
    cmds.add(zwave.switchBinaryV1.switchBinaryGet())
    cmds.add(zwave.sensorMultilevelV5.sensorMultilevelGet(scale: 1, sensorType: 3))
    cmds.add(zwave.notificationV4.notificationGet(notificationType: 7, event:0))
    sendToDevice(cmds)
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
        case 3:
            evt.name = "illuminance"
            evt.value = cmd.scaledSensorValue.toInteger()
            evt.unit = "lux"
            evt.isStateChange=true
            evt.description="${device.displayName}: Illuminance report received: ${evt.value}"
            break
    }
    if (evt.isStateChange) {
        if (txtEnable) log.info evt.descriptionText
        eventProcess(evt)
    }
}

void zwaveEvent(hubitat.zwave.commands.sensorbinaryv1.SensorBinaryReport cmd) {
    if (logEnable) log.debug "Sensor binary report: ${cmd}"
    // redundant and un-needed function
}

void zwaveEvent(hubitat.zwave.commands.notificationv4.NotificationReport cmd) {
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

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
    if (logEnable) log.debug cmd
    switchEvents(cmd)
}

private void switchEvents(hubitat.zwave.Command cmd) {
    String value = (cmd.value ? "on" : "off")
    String description = "${device.displayName} was turned ${value}"
    if (txtEnable) log.info description
    eventProcess(name: "switch", value: value, descriptionText: description, type: state.isDigital?"digital":"physical")
    state.isDigital=false
}

void zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
    if (logEnable) log.debug cmd
    switchEvents(cmd)
}

void on() {
    state.isDigital=true
    sendToDevice(zwave.basicV1.basicSet(value: 0xFF))
}

void off() {
    state.isDigital=true
    sendToDevice(zwave.basicV1.basicSet(value: 0x00))
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

