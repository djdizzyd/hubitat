/*
*	Aeotec DSC06106 Smart Energy Switch
*	version: 1.0
*/

import groovy.transform.Field

metadata {
    definition (name: "Aeotec DSC06106", namespace: "djdizzyd", author: "Bryan Copeland", importUrl: "https://raw.githubusercontent.com/djdizzyd/hubitat/master/Drivers/Aeotec/DSC06106.groovy") {
        capability "Refresh"
        capability "Actuator"
        capability "Sensor"
        capability "Outlet"
        capability "Configuration"
        capability "Switch"
        capability "PowerMeter"
        capability "EnergyMeter"
        capability "VoltageMeasurement"
        capability "Polling"

        fingerprint mfr:"0086", prod:"0003", deviceId:"0006", inClusters:"0x25,0x31,0x32,0x27,0x70,0x85,0x72,0x86", outClusters:"0x82", deviceJoinName: "Aeotec DSC06106"
    }
    preferences {
        configParams.each { input it.value.input }
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "TXT Descriptive Logging", defaultValue: false
    }
}
@Field static Map configParams = [
        90: [input: [name: "configParam90", type: "enum", title: "Report Type", description: "", defaultValue: 0, options:[0:"Time Interval Only",1:"Time and Change in Watts"]], parameterSize: 1],
        91: [input: [name: "configParam91", type: "number", title: "Minimum Watts Change Report", description:"W 0-32000", defaultValue: 50, range: "0..32000",], parameterSize:2],
        92: [input: [name: "configParam92", type: "number", title: "Minimum Watts Change Percent Report", description:"% 0-99", defaultValue: 10, range: "0..99"], parameterSize:1],
        111: [input: [name: "configParam111", type: "number", title: "Seconds for W report", description:"seconds 0-65000", defaultValue: 300, range: "0..65000"], parameterSize:4],
        112: [input: [name: "configParam112", type: "number", title: "Seconds for kWh report", description:"seconds 0-65000", defaultValue: 3600, range: "0..65000"], parameterSize:4]
]
@Field static Map CMD_CLASS_VERS=[0x86:1,0x72:1,0x85:1,0x70:1,0x32:2,0x31:3,0x25:1]

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
    List<hubitat.zwave.Command> cmds = []
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
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 9))
    return cmds
}

List<hubitat.zwave.Command> configCmd(parameterNumber, size, scaledConfigurationValue) {
    List<hubitat.zwave.Command> cmds = []
    cmds.add(zwave.configurationV1.configurationSet(parameterNumber: parameterNumber.toInteger(), size: size.toInteger(), scaledConfigurationValue: scaledConfigurationValue.toInteger()))
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: parameterNumber.toInteger()))
    return cmds
}

void zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
    int scaledValue
    cmd.configurationValue.reverse().eachWithIndex { v, index -> scaledValue=scaledValue | v << (8*index) }
    if(configParams[cmd.parameterNumber.toInteger()]) {
        Map configParam = configParams[cmd.parameterNumber.toInteger()]
        device.updateSetting(configParam.input.name, [value: "${scaledValue}", type: configParam.input.type])
    }
}

void pollDeviceData() {
    List<hubitat.zwave.Command> cmds = []
    cmds.add(zwave.configurationV1.configurationSet(parameterNumber: 1, size: 1, scaledConfigurationValue: 1))
    cmds.add(zwave.versionV1.versionGet())
    cmds.addAll(processAssociations())
    cmds.addAll(pollConfigs())
    cmds.add(zwave.switchBinaryV1.switchBinaryGet())
    cmds.add(zwave.configurationV1.configurationSet(parameterNumber: 80, size: 1, scaledConfigurationValue: 2))
    cmds.add(zwave.configurationV1.configurationSet(parameterNumber: 101, size: 4, scaledConfigurationValue: 2))
    cmds.add(zwave.configurationV1.configurationSet(parameterNumber: 102, size: 4, scaledConfigurationValue: 8))
    cmds.add(zwave.configurationV1.configurationSet(parameterNumber: 103, size: 4, scaledConfigurationValue: 0))
    cmds.add(zwave.configurationV1.configurationSet(parameterNumber: 113, size: 4, scaledConfigurationValue: 0))
    cmds.add(zwave.sensorMultilevelV3.sensorMultilevelGet())
    sendToDevice(cmds)
}

void installed() {
    if (logEnable) log.debug "installed()..."
    initializeVars()
}

void eventProcess(Map evt) {
    if (device.currentValue(evt.name).toString() != evt.value.toString() || !eventFilter) {
        if (txtEnable && evt.descriptionText) log.info evt.descriptionText
        evt.isStateChange=true
        sendEvent(evt)
    }
}

void zwaveEvent(hubitat.zwave.commands.sensormultilevelv3.SensorMultilevelReport cmd) {
    if (logEnable) log.debug "${cmd}"
    Map evt = [:]
    switch (cmd.sensorType) {
        case 4:
            evt.name = "power"
            evt.value = "${cmd.scaledSensorValue}"
            evt.unit = "W"
            evt.descriptionText="${device.displayName} power is: ${evt.value}W"
            eventProcess(evt)
            break
        case 15:
            evt.name = "voltage"
            evt.value = "${cmd.scaledSensorValue}"
            evt.unit = "V"
            evt.descriptionText="${device.displayName} voltage is: ${evt.value}W"
            eventProcess(evt)
            break
    }
}

void zwaveEvent(hubitat.zwave.commands.meterv2.MeterReport cmd) {
    if (logEnable) log.debug "${cmd}"
    Map evt = [:]
    if (cmd.meterType==1) {
        switch (cmd.scale) {
            case 0:
                evt.name = "energy"
                evt.value = "${cmd.scaledMeterValue}"
                evt.unit = "kWh"
                evt.descriptionText="${device.displayName} energy is: ${evt.value}"
                eventProcess(evt)
                break
            case 5:
                evt.name = "voltage"
                evt.value = "${cmd.scaledMeterValue}"
                evt.uit = "V"
                evt.descriptionText="${device.displayName} voltage is: ${evt.value}"
                eventProcess(evt)
                break
        }
    }
}

void zwaveEvent(hubitat.zwave.commands.hailv1.Hail cmd) {
    refresh()
}

void poll() {
    refresh()
}

void refresh() {
    List<hubitat.zwave.Command> cmds=[]
    cmds.add(zwave.sensorMultilevelV3.sensorMultilevelGet())
    cmds.add(zwave.meterV2.meterGet(scale:0))
    cmds.add(zwave.switchBinaryV1.switchBinaryGet())
    sendToDevice(cmds)
}

private void switchEvents(hubitat.zwave.Command cmd) {
    String value = (cmd.value ? "on" : "off")
    String description = "${device.displayName} was turned ${value}"
    eventProcess(name: "switch", value: value, descriptionText: description, type: state.isDigital?"digital":"physical")
    state.isDigital=false
}

void zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
    if (logEnable) log.debug cmd
    switchEvents(cmd)
}

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
    if (logEnable) log.debug cmd
    switchEvents(cmd)
}

void on() {
    state.isDigital=true
    List<hubitat.zwave.Command> cmds=[]
    cmds.add(zwave.switchBinaryV1.switchBinarySet(switchValue: 0xFF))
    sendToDevice(cmds)
}

void off() {
    state.isDigital=true
    List<hubitat.zwave.Command> cmds=[]
    cmds.add(zwave.switchBinaryV1.switchBinarySet(switchValue: 0x00))
    sendToDevice(cmds)
}

void parse(String description) {
    if (logEnable) log.debug "parse:${description}"
    hubitat.zwave.Command cmd = zwave.parse(description, CMD_CLASS_VERS)
    if (cmd) {
        zwaveEvent(cmd)
    }
}

void zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
    if (logEnable) log.debug "version3 report: ${cmd}"
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

List<hubitat.zwave.Command> setDefaultAssociation() {
    List<hubitat.zwave.Command> cmds=[]
    cmds.add(zwave.associationV1.associationSet(groupingIdentifier: 1, nodeId: zwaveHubNodeId))
    cmds.add(zwave.associationV1.associationGet(groupingIdentifier: 1))
    return cmds
}

List<hubitat.zwave.Command> processAssociations(){
    List<hubitat.zwave.Command> cmds = []
    cmds.addAll(setDefaultAssociation())
    if (logEnable) log.debug "processAssociations cmds: ${cmds}"
    return cmds
}


void zwaveEvent(hubitat.zwave.commands.associationv1.AssociationReport cmd) {
    if (logEnable) log.debug "${device.label?device.label:device.name}: ${cmd}"
    List<String> temp = []
    if (cmd.nodeId != []) {
        cmd.nodeId.each {
            temp.add(it.toString().format( '%02x', it.toInteger() ).toUpperCase())
        }
    }
    updateDataValue("zwaveAssociationG${cmd.groupingIdentifier}", "$temp")
}

void zwaveEvent(hubitat.zwave.commands.associationv1.AssociationGroupingsReport cmd) {
    if (logEnable) log.debug "${device.label?device.label:device.name}: ${cmd}"
    log.info "${device.label?device.label:device.name}: Supported association groups: ${cmd.supportedGroupings}"
    state.associationGroups = cmd.supportedGroupings
}