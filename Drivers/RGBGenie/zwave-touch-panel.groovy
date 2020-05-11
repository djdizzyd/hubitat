/*
*	Touch Panel Driver
*	Code written for RGBGenie by Bryan Copeland
*
*   v2.3 - 2020-05-11
*/


import groovy.json.JsonOutput
import groovy.transform.Field

metadata {
    definition (name: "RGBGenie Touch Panel ZW", namespace: "rgbgenie", author: "RGBGenie", importUrl: "https://raw.githubusercontent.com/RGBGenie/Hubitat_RGBGenie/master/Drivers/Zwave-Touch-Panel.groovy") {
        capability "Actuator"
        capability "Configuration"
        capability "Refresh"

        fingerprint mfr:"0330", prod:"0301", deviceId:"A109", deviceJoinName: "RGBGenie Dimmer Touch Panel"
        fingerprint mfr:"0330", prod:"0301", deviceId:"A106", deviceJoinName: "RGBGenie 3 Scene Color Touch Panel"
        fingerprint mfr:"0330", prod:"0301", deviceId:"A105", deviceJoinName: "RGBGenie 3 Zone Color Touch Panel"
        fingerprint mfr:"0330", prod:"0301", deviceId:"A101", deviceJoinName: "RGBGenie Color Temperature Touch Panel"

    }
    preferences {
        input name: "addHubZone1", type: "bool", description: "This creates a child device on the zone for sending the panel actions to the hub. Using the built in mirror app allows synchronization of the panel actions to groups of lights", title: "Create child driver for Zone 1", required: true, defaultValue: false
        if (getDataValue("deviceId")!="41222") {
            input name: "addHubZone2", type: "bool", description: "This creates a child device on the zone for sending the panel actions to the hub. Using the built in mirror app allows synchronization of the panel actions to groups of lights", title: "Create child driver for Zone 2", required: true, defaultValue: false
            input name: "addHubZone3", type: "bool", description: "This creates a child device on the zone for sending the panel actions to the hub. Using the built in mirror app allows synchronization of the panel actions to groups of lights", title: "Create child driver for Zone 3", required: true, defaultValue: false
        }
        input name: "associationsZ1", type: "string", description: "To add nodes to zone associations use the Hexidecimal nodeID from the z-wave device list separated by commas into the space below", title: "Zone 1 Associations"
        if (getDataValue("deviceId")!="41222") {
            input name: "associationsZ2", type: "string", description: "To add nodes to zone associations use the Hexidecimal nodeID from the z-wave device list separated by commas into the space below", title: "Zone 2 Associations"
            input name: "associationsZ3", type: "string", description: "To add nodes to zone associations use the Hexidecimal nodeID from the z-wave device list separated by commas into the space below", title: "Zone 3 Associations"
        }
        input name: "sceneCaptureZ1", type: "bool", description: "", title: "Enable Scene Capture Zone 1", required: true, default: false
        if (getDataValue("deviceId")!="41222") {
            input name: "sceneCaptureZ2", type: "bool", description: "", title: "Enable Scene Capture Zone 2", required: true, default: false
            input name: "sceneCaptureZ3", type: "bool", description: "", title: "Enable Scene Capture Zone 3", required: true, default: false
        }
        input name: "logEnable", type: "bool", description: "", title: "Enable Debug Logging", defaultValue: true, required: true
    }
}

@Field static Map CMD_CLASS_VERS=[0x33:3,0x26:3,0x85:2,0x71:8,0x20:1]
@Field static int COLOR_TEMP_MIN=2700
@Field static int COLOR_TEMP_MAX=6500
private int getCOLOR_TEMP_DIFF() { COLOR_TEMP_MAX - COLOR_TEMP_MIN }

private boolean getZONE_MODEL() {
    if (getDataValue("deviceId")!="41222") {
        return true
    } else {
        return false
    }
}
private int getNUMBER_OF_GROUPS() {
    if (ZONE_MODEL) {
        return 4
    } else {
        return 2
    }
}

void initializeVars() {
    // first run only
    state.initialized=true
}

void installed() {
    device.updateSetting("logEnable", [value: "true", type: "bool"])
    runIn(1800,logsOff)
}

void configure() {
    if (!state.initialized) initializeVars()
    runIn(5,pollDeviceData)
}

void pollDeviceData() {
    List<hubitat.zwave.Command> cmds = []
    cmds.add(zwave.versionV2.versionGet())
    cmds.add(zwave.manufacturerSpecificV2.deviceSpecificGet(deviceIdType: 1))
    cmds.add(zwave.configurationV2.configurationGet(parameterNumber: 4))
    cmds.addAll(pollAssociations())
    sendToDevice(cmds)
}

void logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

void updated() {
    List<hubitat.zwave.Command> cmds=[]
    for (int i = 1 ; i <= 3; i++) {
        if (settings."addHubZone$i") {
            if (getChildDevice("${device.deviceNetworkId}-$i")) {
                // remove legacy naming
                deleteChildDevice("${device.deviceNetworkId}-$i")
            }
            if (getChildDevice("${device.deviceNetworkId}-$i-2-L")) {
                // remove legacy naming
                deleteChildDevice("${device.deviceNetworkId}-$i-2-L")
            }
            if (!getChildDevice("${device.id}-$i-2-L")) {
                com.hubitat.app.ChildDeviceWrapper child=addChildDevice("hubitat", "Generic Component RGBW", "${device.id}-$i-2-L", [completedSetup: true, label: "${device.displayName} (Zone$i) Light", isComponent: true, componentName: "zone$i", componentLabel: "Zone $i"])
            }
            if (!settings."sceneCaptureZ$i") {
                if(getChildDevice("${device.deviceNetworkId}-$i-2-B")) {
                    // remove legacy naming
                    deleteChildDevice("${device.deviceNetworkId}-$i-2-B")
                }
                if(!getChildDevice("${device.id}-$i-2-B")) {
                    com.hubitat.app.ChildDeviceWrapper child=addChildDevice("hubitat", "Generic Component Button Controller", "${device.id}-$i-2-B", [completedSetup: true, label: "${device.displayName} (Zone$i) Buttons", isComponent: true, componentName: "zone$i", componentLabel: "Zone $i"])
                    if (getDataValue("deviceId")=="41222") {
                        child.parse([[name: "numberOfButtons", value: 0]])
                    } else {
                        child.parse([[name: "numberOfButtons", value: 3]])
                    }
                }
            } else {
                if(getChildDevice("${device.deviceNetworkid}-$i-2-B")) {
                    // legacy naming
                    deleteChildDevice("${device.deviceNetworkId}-$i-2-B")
                }
                if(getChildDevice("${device.id}-$i-2-B")) {
                    deleteChildDevice("${device.id}-$i-2-B")
                }
            }
            cmds.addAll(addHubMultiChannel(i))
        } else {
            if (getChildDevice("${device.deviceNetworkId}-$i")) {
                deleteChildDevice("${device.deviceNetworkId}-$i")
            }
            if (getChildDevice("${device.deviceNetworkId}-$i-2-L")) {
                deleteChildDevice("${device.deviceNetworkId}-$i-2-L")
            }
            if (getChildDevice("${device.deviceNetworkId}-$i-2-B")) {
                deleteChildDevice("${device.deviceNetworkId}-$i-2-B")
            }
            if (getChildDevice("${device.id}-$i-2-B")) {
                deleteChildDevice("${device.id}-$i-2-B")
            }
            if (getChildDevice("${device.id}-$i-2-L")) {
                deleteChildDevice("${device.id}-$i-2-L")
            }
            cmds.addAll(removeHubMultiChannel(i))
        }
    }
    cmds.addAll(processAssociations())
    cmds.addAll(pollAssociations())
    if (logEnable) log.debug "updated cmds: ${cmds}"
    if (logEnable) runIn(1800,logsOff)
    sendToDevice(cmds)
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

void refresh() {
    List<hubitat.zwave.Command> cmds=[]
    cmds.AddAll(pollAssociations())
    sendToDevice(cmds)
}

List<hubitat.zwave.Command> pollAssociations() {
    List<hubitat.zwave.Command> cmds=[]
    for(int i = 1;i<=NUMBER_OF_GROUPS;i++) {
        cmds.add(zwave.associationV2.associationGet(groupingIdentifier:i))
        cmds.add(zwave.multiChannelAssociationV2.multiChannelAssociationGet(groupingIdentifier: i))
    }
    if (logEnable) log.debug "pollAssociations cmds: ${cmds}"
    return cmds
}

void zwaveEvent(hubitat.zwave.commands.multichannelassociationv2.MultiChannelAssociationReport cmd) {
    if (logEnable) log.debug "multichannel association report: ${cmd}"
    device.updateDataValue("zwaveAssociationMultiG${cmd.groupingIdentifier}", "${cmd.multiChannelNodeIds}" )
}

void zwaveEvent(hubitat.zwave.Command cmd) {
    if (logEnable) log.debug "skip:${cmd}"
}

List<hubitat.zwave.Command> setDefaultAssociation() {
    //def hubitatHubID = (zwaveHubNodeId.toString().format( '%02x', zwaveHubNodeId )).toUpperCase()
    List<hubitat.zwave.Command> cmds=[]
    cmds.add(zwave.associationV2.associationSet(groupingIdentifier: 1, nodeId: zwaveHubNodeId))
    cmds.add(zwave.multiChannelAssociationV2.multiChannelAssociationGet(groupingIdentifier: group))
    return cmds
}

List<hubitat.zwave.Command> addHubMultiChannel(zone) {
    List<hubitat.zwave.Command> cmds=[]
    int group=zone+1
    cmds.add(zwave.multiChannelAssociationV2.multiChannelAssociationSet(groupingIdentifier: group, nodeId: [0,zwaveHubNodeId,zone as Integer]))
    cmds.add(zwave.multiChannelAssociationV2.multiChannelAssociationGet(groupingIdentifier: group))
    return cmds
}

List<hubitat.zwave.Command> removeHubMultiChannel(zone) {
    List<hubitat.zwave.Command> cmds=[]
    int group=zone+1
    cmds.add(zwave.multiChannelAssociationV2.multiChannelAssociationRemove(groupingIdentifier: group, nodeId: [0,zwaveHubNodeId,zone as Integer]))
    return cmds
}

List<hubitat.zwave.Command> processAssociations(){
    List<hubitat.zwave.Command> cmds = []
    cmds.addAll(setDefaultAssociation())
    int associationGroups = NUMBER_OF_GROUPS
    for (int i = 2 ; i <= associationGroups; i++) {
        int z=i-1
        if (logEnable) log.debug "group: $i dataValue: " + getDataValue("zwaveAssociationG$i") + " parameterValue: " + settings."associationsZ$z"
        String parameterInput=settings."associationsZ$z"
        List<String> newNodeList = []
        List<String> oldNodeList = []
        if (getDataValue("zwaveAssociationG$i") != null) {
            getDataValue("zwaveAssociationG$i").minus("[").minus("]").split(",").each {
                if (it!="") {
                    oldNodeList.add(it.minus(" "))
                }
            }
        }
        if (parameterInput!=null) {
            parameterInput.minus("[").minus("]").split(",").each {
                if (it!="") {
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
                    cmds.add(zwave.associationV2.associationRemove(groupingIdentifier:i, nodeId:Integer.parseInt(it,16)))
                }
            }
            newNodeList.each {
                cmds.add(zwave.associationV2.associationSet(groupingIdentifier:i, nodeId:Integer.parseInt(it,16)))
            }
        }
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
    int zone=cmd.groupingIdentifier-1
    if (logEnable) log.debug "${cmd.groupingIdentifier} - $zone - $temp"
    if (zone > 0) {
        device.updateSetting("associationsZ$zone",[value: "${temp.toString().minus("[").minus("]")}", type: "string"])
    }
    updateDataValue("zwaveAssociationG${cmd.groupingIdentifier}", "$temp")
}

void zwaveEvent(hubitat.zwave.commands.associationv2.AssociationGroupingsReport cmd) {
    if (logEnable) log.debug "${device.label?device.label:device.name}: ${cmd}"
    sendEvent(name: "groups", value: cmd.supportedGroupings)
    log.info "${device.label?device.label:device.name}: Supported association groups: ${cmd.supportedGroupings}"
    state.associationGroups = cmd.supportedGroupings
}

void zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
    hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand()
    if (encapsulatedCommand) {
        epZwaveEvent(encapsulatedCommand, cmd.destinationEndPoint)
    }
}

void epZwaveEvent(hubitat.zwave.Command cmd, short ep) {
    if (logEnable) log.debug "skip: EP: ${ep} - CMD: ${cmd}"
}

void epZwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd, short ep) {
    dimmerEvents(cmd, ep)
}

void epZwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd, short ep) {
    if (logEnable) log.debug "basic set: ${cmd}"
    dimmerEvents(cmd, ep)
}

void epZwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd, short ep) {
    dimmerEvents(cmd, ep)
}

void epZwaveEvent(hubitat.zwave.commands.switchcolorv3.SwitchColorSet cmd, short ep) {
    com.hubitat.app.ChildDeviceWrapper child=getChildDevice("${device.id}-${ep}-2-L")
    if (child) {
        List<Map> evts=[]
        if (logEnable) log.debug "got SwitchColorReport: $cmd"
        int warmWhite = null
        int coldWhite = null
        int red = 0
        int green = 0
        int blue = 0
        cmd.colorComponents.each { k, v ->
            if (logEnable) log.debug "color component: $k : $v"
            switch (k) {
                case 0:
                    warmWhite = v
                    break
                case 1:
                    coldWhite = v
                    break
                case 2:
                    red = v
                    break
                case 3:
                    green = v
                    break
                case 4:
                    blue = v
                    break
            }
        }
        if (red > 0 || green > 0 || blue > 0) {
            List hsv = ColorUtils.rgbToHSV([red, green, blue])
            int hue = hsv[0]
            int sat = hsv[1]
            int lvl = hsv[2]
            if (hue != device.currentValue("hue")) {
                evts.add([name: "hue", value: Math.round(hue), unit: "%"])
            }
            if (sat != device.currentValue("saturation")) {
                evts.add([name: "saturation", value: Math.round(sat), unit: "%"])
            }
            evts.add([name: "colorMode", value: "RGB"])
        } else if (warmWhite != null && coldWhite != null) {
            evts.add([name: "colorMode", value: "CT"])
            int colorTemp = COLOR_TEMP_MIN + (COLOR_TEMP_DIFF / 2)
            if (warmWhite != coldWhite) {
                colorTemp = (COLOR_TEMP_MAX - (COLOR_TEMP_DIFF * warmWhite) / 255) as Integer
            }
            evts.add([name: "colorTemperature", value: colorTemp])
        } else if (warmWhite != null) {
            evts.add([name: "colorMode", value: "CT"])
            evts.add([name: "colorTemperature", value: 2700])
        }
        child.parse(evts)
    }
}

void levelChanging(Map options){
    short ep=options.ep
    com.hubitat.app.ChildDeviceWrapper child=getChildDevice("${device.id}-${ep}-2-L")
    int level=0
    if (options.upDown) {
        level=options.level-5
    } else {
        level=options.level+5
    }
    if (level>100) level=100
    if (level<0) level=0
    List<Map> evts=[]
    evts.add([name: "level", value: level == 99 ? 100 : level , unit: "%"])
    if (level>0 && level<100) {
        if (child.currentValue("switch")=="off") evts.add([name: "switch", value: "on"])
        runInMillis(500, "levelChanging", [data: [upDown: options.upDown, level: level]])
    } else if (level==0) {
        if (child.currentValue("switch")=="on") evts.add([name: "switch", value: "off"])
    }
    child.parse(evts)
}

void epZwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelStartLevelChange cmd, short ep){
    com.hubitat.app.ChildDeviceWrapper child=getChildDevice("${device.id}-${ep}-2-L")
    if (child) {
        runInMillis(500, levelChanging, [data: [upDown: cmd.upDown, level: child.currentValue("level"), ep: ep]])
    }
}

void epZwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelStopLevelChange cmd, short ep) {
    unschedule(levelChanging)
}

void epZwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelSet cmd, short ep) {
    com.hubitat.app.ChildDeviceWrapper child=getChildDevice("${device.id}-${ep}-2-L")
    if (child) {
        List<Map> evts=[]
        evts.add([name: "level", value: cmd.value])
        if (cmd.value>0) {
            if(child.currentValue("switch")!="on") evts.add([name: "switch", value: "on"])
        } else {
            if(child.currentValue("switch")!="off") evts.add([name: "switch", value: "off"])
        }
        child.parse(evts)
    }
}

private void dimmerEvents(hubitat.zwave.Command cmd, short ep) {
    com.hubitat.app.ChildDeviceWrapper child=getChildDevice("${device.id}-${ep}-2-L")
    if (child) {
        List<Map> evts=[]
        String value = (cmd.value ? "on" : "off")
        evts.add([name: "switch", value: value, descriptionText: "$device.displayName was turned $value"])
        if (cmd.value) {
            if (cmd.value > 100) cmd.value = 100
            evts.add([name: "level", value: cmd.value == 99 ? 100 : cmd.value, unit: "%"])
        }
        child.parse(evts)
    }
}

void epZwaveEvent(hubitat.zwave.commands.sceneactuatorconfv1.SceneActuatorConfSet cmd, short ep) {
    com.hubitat.app.ChildDeviceWrapper child=getChildDevice("${device.id}-${ep}-2-L")
    if (child) {
        if (settings."sceneCaptureZ${ep}") {
            if (!state.scene) {
                state.scene = [:]
            }
            if (child.currentValue("colorMode") == "RGB") {
                state.scene."${ep}"."${cmd.sceneId}" = ["hue": child.currentValue("hue"), "saturation": child.currentValue("saturation"), "level": child.currentValue("level"), "colorMode": child.currentValue("colorMode"), "switch": child.currentValue("switch")]
            } else {
                state.scene."${ep}"."${cmd.sceneId}" = ["colorTemperature": child.currentValue("colorTemperature"), "level": child.currentValue("level"), "switch": child.currentValue("switch"), "colorMode": child.currentValue("colorMode")]
            }
        } else {
            com.hubitat.app.ChildDeviceWrapper buttonChild=getChildDevice("${device.id}-${ep}-2-B")
            buttonChild.parse([[name: "pushed", value: (cmd.sceneId / 16)]])
        }
    }
}

void zwaveEvent(hubitat.zwave.commands.sceneactivationv1.SceneActivationSet cmd, short ep) {
    com.hubitat.app.ChildDeviceWrapper child=getChildDevice("${device.id}-${ep}-2-L")
    if (child) {
        List<Map> evts=[]
        if (settings."sceneCaptureZ${ep}") {
            if (!state.scene) {
                state.scene = [:]
            }
            state.scene."${ep}"."${cmd.sceneId}".each { k, v ->
                evts.add([name: k, value: v])
            }
        } else {
            com.hubitat.app.ChildDeviceWrapper buttonChild = getChildDevice("${device.id}-${ep}-2-B")
            buttonChild.parse([[name: "held", value: (cmd.sceneId / 16)]])
        }
        child.parse(evts)
    }
}

void componentRefresh(d){noCommands(d,"refresh")}
void componentOn(d){noCommands(d,"on")}
void componentOff(d){noCommands(d,"off")}
void componentSetLevel(d,l){noCommands(d,"setLevel")}
void componentStartLevelChange(d,dr){noCommands(d,"startLevelChange")}
void componentStopLevelChange(d){noCommands(d,"stopLevelChange")}
void componentSetColor(d,v) {noCommands(d,"setColor")}
void componentSetHue(d,v){noCommands(d,"setHue")}
void componentSetSaturation(d,v){noCommands(d,"setSaturation")}
void componentSetColorTemperature(d,v){noCommands(d,"setColorTemperature")}

void noCommands(d, cmd) {
    if (logEnable) log.trace "Command ${cmd} is not implemented on ${d.displayName}"
}