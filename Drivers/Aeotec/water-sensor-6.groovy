/*
*	Aeotec Water Sensor 6
*	version: 1.4
*/

import groovy.transform.Field

metadata {
    definition (name: "Aeotec Water Sensor 6", namespace: "djdizzyd", author: "Bryan Copeland", importUrl: "https://raw.githubusercontent.com/djdizzyd/hubitat/master/Drivers/Aeotec/water-sensor-6.groovy") {
        capability "Refresh"
        capability "Actuator"
        capability "Sensor"
        capability "Configuration"
        capability "TemperatureMeasurement"
        capability "WaterSensor"
        capability "Battery"
        capability "PowerSource"

        fingerprint  mfr:"0086", prod:"0102", deviceId:"007A", inClusters:"0x5E,0x85,0x59,0x80,0x70,0x7A,0x71,0x73,0x31,0x86,0x84,0x60,0x8E,0x72,0x5A" , deviceJoinName: "Aeotec Water Sensor 6"
    }
    preferences {
        configParams.each { input it.value.input }
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "TXT Descriptive Logging", defaultValue: false
    }
}
@Field static Map configParams = [
        2: [input: [name: "configParam2", type: "enum", title: "Wakeup 10 minutes when re-power", description: "", defaultValue: 0, options:[0:"Disable",1:"Enable"]], parameterSize: 1],
        86: [input: [name: "configParam86", type:"enum", title: "Buzzer", description:"", defaultValue:1, options:[0:"disable",1:"enable"]], parameterSize:1],
        87: [input: [name: "configParam87", type:"enum", title: "Buzzer Alarm", description:"", defaultValue: 55, options:[1:"Water leak",2:"Vibration",4:"tilt",16:"freeze",32:"overheat",55:"All"]], parameterSize:1]
]
@Field static Map ZWAVE_NOTIFICATION_TYPES=[0:"Reserverd", 1:"Smoke", 2:"CO", 3:"CO2", 4:"Heat", 5:"Water", 6:"Access Control", 7:"Home Security", 8:"Power Management", 9:"System", 10:"Emergency", 11:"Clock", 12:"First"]
@Field static Map CMD_CLASS_VERS=[0x20:1,0x86:2,0x72:2,0x5B:3,0x70:1,0x85:2,0x59:1,0x31:5,0x71:7]

void logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

void setupChildren() {
    if(!getChildDevice("${device.deviceNetworkId}-1")) {
        com.hubitat.app.ChildDeviceWrapper child = addChildDevice("hubitat", "Generic Component Water Sensor", "${device.deviceNetworkId}-1", [completedSetup: true, label: "${device.displayName} (Sensor 1)", isComponent: true, componentName: "sensor1", componentLabel: "Sensor 1"])
    }
    if(!getChildDevice("${device.deviceNetworkId}-2")) {
        com.hubitat.app.ChildDeviceWrapper child = addChildDevice("hubitat", "Generic Component Water Sensor", "${device.deviceNetworkId}-2", [completedSetup: true, label: "${device.displayName} (Sensor 2)", isComponent: true, componentName: "sensor2", componentLabel: "Sensor 2"])
    }
}

void configure() {
    setupChildren()
    if (!state.initialized) initializeVars()
    runIn(5,pollDeviceData)
}

void initializeVars() {
    // first run only
    state.initialized=true
    state.sleepy=true
    runIn(5, refresh)
}

void updated() {
    setupChildren()
    log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    unschedule()
    if (logEnable) runIn(1800,logsOff)
    if (state.sleepy) {
        state.configUpdated=true
    } else {
        List<hubitat.zwave.Command> cmds = []
        cmds.addAll(runConfigs())
        sendToDevice(cmds)
    }
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
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 9))
    return cmds
}

List<hubitat.zwave.Command> configCmd(parameterNumber, size, scaledConfigurationValue) {
    List<hubitat.zwave.Command> cmds = []
    cmds.add(zwave.configurationV1.configurationSet(parameterNumber: parameterNumber.toInteger(), size: size.toInteger(), scaledConfigurationValue: scaledConfigurationValue.toInteger()))
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: parameterNumber.toInteger()))
    return cmds
}

void zwaveEvent(hubitat.zwave.commands.wakeupv2.WakeUpIntervalReport cmd) {
    state.wakeInterval = cmd.seconds
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
    cmds.add(zwave.sensorMultilevelV5.sensorMultilevelGet(scale: (location.temperatureScale=="F"?1:0), sensorType: 1))
    cmds.add(zwave.notificationV7.notificationGet(notificationType: 5, event:0))
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 136))
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


void zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
    int scaledValue
    cmd.configurationValue.reverse().eachWithIndex { v, index -> scaledValue=scaledValue | v << (8*index) }
    if(configParams[cmd.parameterNumber.toInteger()]) {
        Map configParam=configParams[cmd.parameterNumber.toInteger()]
        device.updateSetting(configParam.input.name, [value: "${scaledValue}", type: configParam.input.type])
    } else {
        if (cmd.parameterNumber==9) {
            Map evt=[name:"powerSource"]
            switch (cmd.configurationValue[0]) {
                case 0:
                    evt.value="mains"
                    evt.descriptionText = "${device.displayName} USB powered"
                    if (txtEnable) log.info evt.descriptionText
                    eventProcess(evt)
                    break
                case 1:
                    evt.value="battery"
                    evt.descriptionText = "${device.displayName} Battery powered"
                    if (txtEnable) log.info evt.descriptionText
                    eventProcess(evt)
                    break
            }
            switch (cmd.configurationValue[1]) {
                case 0:
                    state.sleepy=true
                    break
                case 1:
                    state.sleepy=true
                    break
                case 2:
                    state.sleepy=false
                    break
            }
        } else if (cmd.parameterNumber==136) {
            List<com.hubitat.app.ChildDeviceWrapper> child = []
            child.add(getChildDevice("${device.deviceNetworkId}-1"))
            child.add(getChildDevice("${device.deviceNetworkId}-2"))
            Map dryEvent=[name: "water", value: "dry", isStateChange: true]
            Map wetEvent=[name: "water", value: "wet", isStateChange: true]
            switch (cmd.configurationValue[0]) {
                case 0:
                    // child 1 dry and child 2 dry
                    if (child[0].currentValue("water")!="dry") child[0].parse([dryEvent])
                    if (child[1].currentValue("water")!="dry") child[1].parse([dryEvent])
                    break
                case 1:
                    // child 1 wet and child 2 dry
                    if (child[0].currentValue("water")!="wet") child[0].parse([wetEvent])
                    if (child[1].currentValue("water")!="dry") child[1].parse([dryEvent])
                    break
                case 2:
                    // child 1 dry and child 2 wet
                    if (child[0].currentValue("water")!="dry") child[0].parse([dryEvent])
                    if (child[1].currentValue("water")!="wet") child[1].parse([wetEvent])
                    break
                case 3:
                    // child 1 wet and child 2 wet
                    if (child[0].currentValue("water")!="wet") child[0].parse([wetEvent])
                    if (child[1].currentValue("water")!="wet") child[1].parse([wetEvent])
                    break
            }
        }
    }
}

void pollDeviceData() {
    List<hubitat.zwave.Command> cmds = []
    cmds.add(zwave.wakeUpV1.wakeUpIntervalSet(seconds: 43200, nodeid:zwaveHubNodeId))
    cmds.add(zwave.versionV2.versionGet())
    cmds.add(zwave.manufacturerSpecificV2.deviceSpecificGet(deviceIdType: 1))
    cmds.addAll(processAssociations())
    cmds.addAll(pollConfigs())
    cmds.add(zwave.sensorMultilevelV5.sensorMultilevelGet(scale: (location.temperatureScale=="F"?1:0), sensorType: 1))
    cmds.add(zwave.notificationV7.notificationGet(notificationType: 5, event:0))
    cmds.add(zwave.configurationV1.configurationSet(parameterNumber: 94, configurationValue:[1], size:1))
    cmds.add(zwave.configurationV1.configurationSet(parameterNumber: 101, configurationValue:[3], size:1))
    cmds.add(zwave.configurationV1.configurationSet(parameterNumber: 135, configurationValue:[3], size:1))
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 136))
    cmds.add(zwave.configurationV1.configurationSet(parameterNumber: 30, configurationValue: [55], size:1))
    cmds.add(zwave.batteryV1.batteryGet())
    sendToDevice(cmds)
}

void refresh() {
    List<hubitat.zwave.Command> cmds=[]
    cmds.add(zwave.sensorMultilevelV5.sensorMultilevelGet(scale: (location.temperatureScale=="F"?1:0), sensorType: 1))
    cmds.add(zwave.notificationV7.notificationGet(notificationType: 5, event:0))
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 136))
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
        case 1:
            evt.name = "temperature"
            evt.value = cmd.scaledSensorValue.toInteger()
            evt.unit = cmd.scale==0?"C":"F"
            evt.isStateChange=true
            evt.descriptionText="${device.displayName}: Temperature report received: ${evt.value}"
            break
    }
    if (evt.isStateChange) {
        if (txtEnable) log.info evt.descriptionText
        eventProcess(evt)
    }
}

void zwaveEvent(hubitat.zwave.commands.notificationv7.NotificationReport cmd) {
    Map evt = [isStateChange: false]
    log.info "Notification: " + ZWAVE_NOTIFICATION_TYPES[cmd.notificationType]
    log.debug "${cmd}"
    if (cmd.notificationType == 4) {
        // heat alarm
        switch (cmd.event) {
            case 0:
                log.info "${device.dsiplayName} Temperature Alarm: CLEARED"
                break
            case 1:
                log.warn "${device.displayName} Temperature Alarm: OVERHEAT DETECTED"
                break
            case 2:
                log.warn "${device.displayName} Temperature Alarm: OVERHEAT DETECTED"
                break
            case 5:
                log.warn "${device.displayName} Temperature Alarm: FREEZE CONDITIONS DETECTED"
                break
            case 6:
                log.warn "${device.displayName} Temperature Alarm: FREEZE CONDITIONS DETECTED"
                break
        }
    } else if (cmd.notificationType == 5) {
        // Water Alarm
        evt.name = "water"
        evt.isStateChange = true
        switch (cmd.event) {
            case 0:
                // state idle
                evt.value = "dry"
                evt.descriptionText = "${device.displayName} is ${evt.value}"
                break
            case 1:
                // water leak detected (location provided)
                evt.value = "wet"
                evt.descriptionText = "${device.displayName} is ${evt.value}"
                break
            case 2:
                // water leak detected
                evt.value = "wet"
                evt.descriptionText = "${device.displayName} is ${evt.value}"
                break
        }
    } else if (cmd.notificationType == 7) {
        // home security
        switch (cmd.event) {
            case 0:
                // state idle
                if (cmd.eventParametersLength > 0) {
                    switch (cmd.eventParameter[0]) {
                        case 3:
                            evt.name = "tamper"
                            evt.value = "clear"
                            evt.isStateChange = true
                            evt.descriptionText = "${device.displayName} tamper alert cover closed"
                            break
                    }
                } else {
                    // should probably do something here
                }
                break
            case 3:
                // Tampering cover removed
                evt.name = "tamper"
                evt.value = "detected"
                evt.isStateChange = true
                evt.descriptionText = "${device.displayName} tamper alert cover removed"
                break
            case 254:
                // unknown event/state
                log.warn "Device sent unknown event / state notification"
                break
        }
    } else if (cmd.notificationType == 8) {
        // power management
        switch (cmd.event) {
            case 0:
                // idle
                break
            case 1:
                // Power has been applied
                log.info "${device.displayName} Power has been applied"
                break
            case 2:
                // AC mains disconnected
                evt.name = "powerSource"
                evt.isStateChange = true
                evt.value = "battery"
                evt.descriptionText = "${device.displayName} AC mains disconnected"
                break
            case 3:
                // AC mains re-connected
                evt.name = "powerSource"
                evt.isStateChange = true
                evt.value = "mains"
                evt.descriptionText = "${device.displayName} AC mains re-connected"
                break
            case 4:
                // surge detected
                log.warn "${device.displayName} surge detected"
                break
            case 5:
                // voltage drop / drift
                break
            case 6:
                // Over-current detected
                break
            case 7:
                // Over-voltage detected
                break
            case 8:
                // over-load detected
                break
            case 9:
                // load error
                break
            case 10:
                // replace battery soon
                break
            case 11:
                // replace battery now
                break
            case 12:
                // battery is charging
                log.info "${device.displayName} Battery is charging"
                break
            case 13:
                // battery is fully charged
                break
            case 14:
                // charge battery soon
                break
            case 15:
                // charge battery now
                break
            case 16:
                // backup battery is low
                break
            case 17:
                // battery fluid is low
                break
            case 18:
                // backup battery disconnected
                break
            case 254:
                // unknown event / state
                break
        }
    }
    if (evt.isStateChange) {
        if (txtEnable) log.info evt.descriptionText
        sendEvent(evt)
    }
}

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
    if (logEnable) log.debug cmd
    // ignore anything here
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
    cmds.add(zwave.associationV2.associationSet(groupingIdentifier: 2, nodeId: zwaveHubNodeId))
    cmds.add(zwave.associationV2.associationGet(groupingIdentifier: 2))
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

