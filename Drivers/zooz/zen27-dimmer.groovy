/*
*	Zen27 Central Scene Dimmer
*	version: 1.2
*/

import groovy.transform.Field

metadata {
    definition (name: "Zooz Zen27 Central Scene Dimmer", namespace: "djdizzyd", author: "Bryan Copeland", importUrl: "https://raw.githubusercontent.com/djdizzyd/hubitat/master/Drivers/zooz/zen27-dimmer.groovy") {
        capability "SwitchLevel"
        capability "Switch"
        capability "Refresh"
        capability "Actuator"
        capability "Sensor"
        capability "Configuration"
        capability "ChangeLevel"
        capability "PushableButton"
        capability "Indicator"

        fingerprint mfr:"027A", prod:"A000", deviceId:"A002", inClusters:"0x5E,0x26,0x85,0x8E,0x59,0x55,0x86,0x72,0x5A,0x73,0x70,0x5B,0x9F,0x6C,0x7A", deviceJoinName: "Zooz Zen27 Dimmer" //US

    }
    preferences {
        configParams.each { input it.value.input }
        input name: "associationsG2", type: "string", description: "To add nodes to associations use the Hexidecimal nodeID from the z-wave device list separated by commas into the space below", title: "Associations Group 2"
        input name: "associationsG3", type: "string", description: "To add nodes to associations use the Hexidecimal nodeID from the z-wave device list separated by commas into the space below", title: "Associations Group 3"
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable text logging", defaultValue: false
    }
}
@Field static Map configParams = [
        1: [input: [name: "configParam1", type: "enum", title: "On/Off Paddle Orientation", description: "", defaultValue: 0, options: [0:"Normal",1:"Reverse",2:"Any paddle turns on/off"]], parameterSize: 1],
        2: [input: [name: "configParam2", type: "enum", title: "LED Indicator Control", description: "", defaultValue: 0, options: [0:"Indicator is on when switch is off",1:"Indicator is on when switch is on",2:"Indicator is always off",3:"Indicator is always on"]], parameterSize: 1],
        3: [input: [name: "configParam3", type: "enum", title: "Auto Turn-Off Timer", description: "", defaultValue: 0, options: [0:"Timer disabled",1:"Timer Enabled"]], parameterSize: 1],
        4: [input: [name: "configParam4", type: "number", title: "Auto Off Timer", description: "Minutes 1-65535", defaultValue: 60, ranges:"1..65535"], parameterSize:4],
        5: [input: [name: "configParam5", type: "enum", title: "Auto Turn-On Timer", description: "", defaultValue: 0, options: [0:"timer disabled",1:"timer enabled"]],parameterSize:1],
        6: [input: [name: "configParam6", type: "number", title: "Auto On Timer", description: "Minutes 1-65535", defaultValue: 60, ranges:"1..65535"], parameterSize: 4],
        7: [input: [name: "configParam7", type: "enum", title: "Association Reports", description: "", defaultValue: 15, options:[0:"none",1:"physical tap on ZEN27 only",2:"physical tap on 3-way switch only",3:"physical tap on ZEN27 or 3-way switch",4:"Z-Wave command from hub",5:"physical tap on ZEN27 or Z-Wave command",6:"physical tap on connected 3-way switch or Z-wave command",7:"physical tap on ZEN27 / 3-way switch / or Z-wave command",8:"timer only",9:"physical tap on ZEN27 or timer",10:"physical tap on 3-way switch or timer",11:"physical tap on ZEN 27 / 3-way switch or timer",12:"Z-wave command from hub or timer",13:"physical tap on ZEN27, Z-wave command, or timer",14:"physical tap on ZEN27 / 3-way switch / Z-wave command, or timer", 15:"all of the above"]],parameterSize:1],
        8: [input: [name: "configParam8", type: "enum", title: "On/Off Status After Power Failure", description: "", defaultValue: 2, options:[0:"Off",1:"On",2:"Last State"]],parameterSize:1],
        9: [input: [name: "configParam9", type: "number", title: "Ramp Rate Control", description: "Seconds: 0-99", defaultValue: 1, ranges:"0..99"], parameterSize:1],
        17: [input: [name: "configParam17", type: "enum", title: "Z-Wave Ramp Control", description: "", defaultValue: 1, options: [0:"Z-Wave ramp rate matches physical",1:"Z-Wave ramp rate is set independently"]],parameterSize:1],
        10: [input: [name: "configParam10", type: "number", title: "Minimum Level", description: "1-99%", defaultValue: 1, ranges:"1..99"], parameterSize:1],
        11: [input: [name: "configParam11", type: "number", title: "Maximum level", description: "1-99%", defaultValue: 99, ranges:"1..99"], parameterSize:1],
        12: [input: [name: "configParam12", type: "enum", title: "Double Tap Function", description: "", defaultValue: 0, options:[0:"Turn on full brightness",1:"Turn on to maximum level"]], parameterSize:1],
        14: [input: [name: "configParam14", type: "enum", title: "Double/Single Tap Function", description:"", defaultValue: 0, options:[0:"double tap to full / maximum brightness level enabled",1:"double tap to full / maximum brightness level disabled, single tap turns light on to last brightness level",2:" double tap to full / maximum brightness level disabled, single tap turns light on to full / maximum brightness level"]],parameterSize:1],
        13: [input: [name: "configParam13", type: "enum", title: "Enable/Disable Scene Control", defaultValue: 0, options:[0:"Scene control disabled",1:"scene control enabled"]],parameterSize:1],
        15: [input: [name: "configParam15", type: "enum", title: "Smart Bulb Mode", defaultValue: 1, options:[0:"physical paddle control disabled",1:"physical paddle control enabled",2:"physical paddle and z-wave control disabled"]],parameterSize: 1],
        20: [input: [name: "configParam20", type: "enum", title: "Report Type", defaultValue:0, options: [0:"report each brightness level to hub when physical / Z-Wave control is disabled for physical dimming (final level only reported if physical / Z-Wave control is enabled)",1:"report final brightness level only for physical dimming, regardless of the physical / Z-Wave control mode"]], parameterSize:1],
        21: [input: [name: "configParam21", type: "enum", title: "Report Type Disabled Physical", defaultValue:0, options: [0:"switch reports on/off status and changes LED indicator state even if physical and Z-Wave control is disabled", 1:"switch doesn't report on/off status or change LED indicator state when physical (and Z-Wave) control is disabled"]], parameterSize:1],
        15: [input: [name: "configParam15", type: "number", title: "Physical Dimming Speed", description: "Seconds 1-99", defaultValue: 4, ranges:"1..99"], parameterSize: 1],
        18: [input: [name: "configParam18", type: "number", title: "Custom Brightness Level On", description: "0 – last brightness level (default); 1 – 99 (%) for custom brightness level", defaultValue: 0, ranges: "0..99"], parameterSize:1],
        22: [input: [name: "configParam22", type: "number", title: "Night Light Mode", description: "0 – feature disabled; 1 – 99 (%). Default: 20", defaultValue: 20, ranges: "0..99"], parameterSize:1]
]
@Field static Map CMD_CLASS_VERS=[0x20:1,0x5B:3,0x86:3,0x72:2,0x8E:3,0x85:2,0x59:1,0x26:2,0x70:1]
@Field static int numberOfAssocGroups=3
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

void indicatorNever() {
    sendToDevice(configCmd(2,1,2))
}

void indicatorWhenOff() {
    sendToDevice(configCmd(2,1,0))
}

void indicatorWhenOn() {
    sendToDevice(configCmd(2,1,1))
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
    cmds.add(zwave.versionV3.versionGet())
    cmds.add(zwave.manufacturerSpecificV2.deviceSpecificGet(deviceIdType: 1))
    cmds.addAll(processAssociations())
    cmds.addAll(pollConfigs())
    sendEvent(name: "numberOfButtons", value: 8)
    sendToDevice(cmds)
}

void refresh() {
    List<hubitat.zwave.Command> cmds=[]
    cmds.add(zwave.switchMultilevelV2.switchMultilevelGet())
    cmds.add(zwave.basicV1.basicGet())
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

void startLevelChange(direction) {
    boolean upDownVal = direction == "down" ? true : false
    if (logEnable) log.debug "got startLevelChange(${direction})"
    sendToDevice(zwave.switchMultilevelV3.switchMultilevelStartLevelChange(ignoreStartLevel: true, startLevel: device.currentValue("level"), upDown: upDownVal, incDec: 1, stepSize: 1, dimmingDuration: 20))
}

void stopLevelChange() {
    sendToDevice(zwave.switchMultilevelV3.switchMultilevelStopLevelChange())
}

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
    if (logEnable) log.debug cmd
    dimmerEvents(cmd)
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

void on() {
    state.isDigital=true
    sendToDevice(zwave.basicV1.basicSet(value: 0xFF))
}

void off() {
    state.isDigital=true
    sendToDevice(zwave.basicV1.basicSet(value: 0x00))
}

void setLevel(level, duration=1) {
    state.isDigital=true
    if (logEnable) log.debug "setLevel($level, $duration)"
    if(level > 99) level = 99
    sendToDevice(zwave.switchMultilevelV2.switchMultilevelSet(value: level.toInteger(), dimmingDuration: duration))
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

void zwaveEvent(hubitat.zwave.commands.versionv3.VersionReport cmd) {
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

void zwaveEvent(hubitat.zwave.commands.centralscenev3.CentralSceneNotification cmd) {
    Map evt = [name: "pushed", type:"physical", isStateChange:true]
    if (cmd.sceneNumber==1) {
        if (cmd.keyAttributes==0) {
            evt.value=1
            evt.descriptionText="${device.displayName} button ${evt.value} pushed"
            if (txtEnable) log.info evt.descriptionText
            sendEvent(evt)
        } else if (cmd.keyAttributes==3) {
            evt.value=3
            evt.descriptionText="${device.displayName} button ${evt.value} pushed"
            if (txtEnable) log.info evt.descriptionText
            sendEvent(evt)
        } else if (cmd.keyAttributes==4) {
            evt.value=5
            evt.descriptionText="${device.displayName} button ${evt.value} pushed"
            if (txtEnable) log.info evt.descriptionText
            sendEvent(evt)
        } else if (cmd.keyAttributes==5) {
            evt.value=7
            evt.descriptionText="${device.displayName} button ${evt.value} pushed"
            if (txtEnable) log.info evt.descriptionText
            sendEvent(evt)
        }
    } else if (cmd.sceneNumber==2) {
        if (cmd.keyAttributes==0) {
            evt.value=2
            evt.descriptionText="${device.displayName} button ${evt.value} pushed"
            if (txtEnable) log.info evt.descriptionText
            sendEvent(evt)
        } else if (cmd.keyAttributes==3) {
            evt.value=4
            evt.descriptionText="${device.displayName} button ${evt.value} pushed"
            if (txtEnable) log.info evt.descriptionText
            sendEvent(evt)
        } else if (cmd.keyAttributes==4) {
            evt.value=6
            evt.descriptionText="${device.displayName} button ${evt.value} pushed"
            if (txtEnable) log.info evt.descriptionText
            sendEvent(evt)
        } else if (cmd.keyAttributes==5) {
            evt.value=8
            evt.descriptionText="${device.displayName} button ${evt.value} pushed"
            if (txtEnable) log.info evt.descriptionText
            sendEvent(evt)
        }
    }
}

