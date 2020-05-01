import groovy.transform.Field

/**
 * Advanced GoControl GC-TBZ48 Thermostat Driver
 * v1.4
 * updated 2020-05-01
 */

metadata {
    definition (name: "Advanced GoControl GC-TBZ48", namespace: "djdizzyd", author: "Bryan Copeland", importUrl: "https://raw.githubusercontent.com/djdizzyd/hubitat/master/Drivers/GoConntrol/goControlThermostat.groovy" ) {

        capability "Actuator"
        capability "Battery"
        capability "Configuration"
        capability "Refresh"
        capability "Sensor"
        capability "TemperatureMeasurement"
        capability "Thermostat"
        capability "ThermostatMode"
        capability "ThermostatFanMode"
        capability "ThermostatSetpoint"
        capability "ThermostatCoolingSetpoint"
        capability "ThermostatHeatingSetpoint"
        capability "ThermostatOperatingState"
        capability "FilterStatus"

        attribute "scpStatus", "string"
        attribute "mechStatus", "string"
        attribute "remoteTemperature", "string"
        attribute "currentSensorCal", "number"


        command "syncClock"
        command "filterReset"
        command "SensorCal", [[name:"calibration",type:"ENUM", description:"Number of degrees to add/subtract from thermostat sensor", constraints:["0", "-7", "-6", "-5", "-4", "-3", "-2", "-1", "0", "1", "2", "3", "4", "5", "6", "7"]]]


        fingerprint mfr:"014F", prod:"5442", deviceId:"5436", inClusters:"0x5E,0x59,0x5A,0x40,0x42,0x43,0x44,0x45,0x80,0x70,0x31,0x8F,0x86,0x72,0x85,0x2C,0x2B,0x73,0x81,0x7A", deviceJoinName: "GoControl GC-TBZ48"

    }
    preferences {
        configParams.each { input it.value.input }
        input "logEnable", "bool", title: "Enable debug logging", defaultValue: false
    }

}

@Field static Map CMD_CLASS_VERS=[0x7A:2, 0x81:1, 0x73:1, 0x2B:1, 0x2C:1, 0x85:2, 0x72:1, 0x86:2, 0x8F:1, 0x31:1, 0x70:1, 0x80:1, 0x45:1, 0x44:1, 043:2, 0x42:1, 0x40:1, 0x5A:1, 0x59:1, 0x5E:2]
@Field static Map THERMOSTAT_OPERATING_STATE=[0x00:"idle",0x01:"heating",0x02:"cooling",0x03:"fan only",0x04:"pending heat",0x05:"pending cool",0x06:"vent economizer"]
@Field static Map THERMOSTAT_MODE=[0x00:"off",0x01:"heat",0x02:"cool",0x03:"auto",0x04:"emergency heat"]
@Field static Map SET_THERMOSTAT_MODE=["off":0x00,"heat":0x01,"cool":0x02,"auto":0x03,"emergency heat":0x04]
@Field static Map THERMOSTAT_FAN_MODE=[0x00:"auto",0x01:"on",0x02:"auto",0x03:"on"]
@Field static Map SET_THERMOSTAT_FAN_MODE=["auto":0x00,"on":0x01]
@Field static Map THERMOSTAT_FAN_STATE=[0x00:"idle", 0x01:"running", 0x02:"running high"]
@Field static List<String> supportedThermostatFanModes=["on","auto"]
@Field static List<String> supportedThermostatModes=["auto", "off", "heat", "emergency heat", "cool"]
@Field static Map configParams = [
        1: [input: [name: "configParam1", type: "enum", title: "System Type", description: "", defaultValue: 0, options: [0:"Standard",1:"Heat Pump"]], parameterSize: 1],
        2: [input: [name: "configParam2", type: "enum", title: "Fan Type", description:"", defaultValue: 0, options: [0:"Gas (No fan w/Heat)", 1:"Electric (Fan w/Heat)"]], parameterSize: 1],
        3: [input: [name: "configParam3", type: "enum", title: "Change Over Type", description:"", defaultValue: 0, options: [0:"CO w/cool", 1:"CO w/heat"]], parameterSize: 1],
        4: [input: [name: "configParam4", type: "enum", title: "2nd Stage Heat Enable", defaultValue: 0, options: [0:"Disabled", 1:"Enabled"]], parameterSize: 1],
        5: [input: [name: "configParam5", type: "enum", title: "Aux Heat Enable", defaultValue: 0, options: [0:"Disabled", 1:"Enabled"]], parameterSize:1],
        6: [input: [name: "configParam6", type: "enum", title: "2nd Stage Cool Enable", defaultValue: 0, options: [0:"Disabled", 1:"Enabled"]], parameterSize:1],
        7: [input: [name: "configParam7", type: "enum", title: "C/F Type", defaultValue: 1, options: [0:"Centigrade", 1:"Fahrenheit"]], parameterSize:1],
        8: [input: [name: "configParam8", type: "number", title:"MOT", description: "Minimum Off Time", defaultValue: 5, range: "5..9"], parameterSize: 1],
        9: [input: [name: "configParam9", type: "number", title:"MRT", description: "Minimum Run Time", defaultValue: 3, range: "3..9"], parameterSize: 1],
        10: [input: [name: "configParam10", type: "number", title:"Setpoint H/C Delta", description: "degrees", defaultValue: 3, range: "3..15"], parameterSize: 1],
        11: [input: [name: "configParam11", type: "number", title:"H Delta Stage 1 ON", description: "degrees", defaultValue: 1, range: "1..6"], parameterSize: 1],
        12: [input: [name: "configParam12", type: "number", title:"H Delta Stage 1 OFF", description: "degrees", defaultValue: 0, range: "0..5"], parameterSize: 1],
        13: [input: [name: "configParam13", type: "number", title:"H Delta Stage 2 ON", description: "degrees", defaultValue: 2, range: "2..7"], parameterSize: 1],
        14: [input: [name: "configParam14", type: "number", title:"H Delta Stage 2 OFF", description: "degrees", defaultValue: 0, range: "0..6"], parameterSize: 1],
        15: [input: [name: "configParam15", type: "number", title:"H Delta Aux ON", description: "degrees", defaultValue: 3, range: "3..8"], parameterSize: 1],
        16: [input: [name: "configParam16", type: "number", title:"H Delta Aux OFF", description: "degrees", defaultValue: 0, range: "0..7"], parameterSize: 1],
        17: [input: [name: "configParam17", type: "number", title:"C Delta Stage 1 ON", description: "degrees", defaultValue: 1, range: "1..6"], parameterSize: 1],
        18: [input: [name: "configParam18", type: "number", title:"C Delta Stage 1 OFF", description: "degrees", defaultValue: 0, range: "0..5"], parameterSize: 1],
        19: [input: [name: "configParam19", type: "number", title:"C Delta Stage 2 ON", description: "degrees", defaultValue: 2, range: "2..7"], parameterSize: 1],
        20: [input: [name: "configParam20", type: "number", title:"C Delta Stage 2 OFF", description: "degrees", defaultValue: 0, range: "0..6"], parameterSize: 1],
        24: [input: [name: "configParam24", type: "enum", title: "Display Lock", defaultValue: 0, options:[0:"Unlocked",1:"Locked"]], parameterSize: 1],
        26: [input: [name: "configParam26", type: "number", title:"Backlight Timer", description: "seconds", defaultValue: 10, range: "0,10..30"], parameterSize: 1],
        33: [input: [name: "configParam33", type: "number", title:"Max Heat Setpoint", description: "degrees", defaultValue: 90, range: "30..109"], parameterSize: 1],
        34: [input: [name: "configParam34", type: "number", title:"Min Cool Setpoint", description: "degrees", defaultValue: 60, range: "33..112"], parameterSize: 1],
        38: [input: [name: "configParam38", type: "enum", title: "Schedule Enable", defaultValue: 0, options: [0:"Disabled", 1:"Enabled"]], parameterSize:1],
        39: [input: [name: "configParam39", type: "enum", title: "Run/Hold Mode", defaultValue: 0, options: [0:"Hold", 1:"Run"]], parameterSize:1],
        40: [input: [name: "configParam40", type: "enum", title: "Setback Mode", defaultValue: 0, options: [0:"No Setback", 1:"Un-Occupied Mode"]], parameterSize:1],
        41: [input: [name: "configParam41", type: "number", title:"Un-Occupied HSP", description: "degrees", defaultValue: 30, range: "30..109"], parameterSize: 1],
        42: [input: [name: "configParam42", type: "number", title:"Un-Occupied CSP", description: "degrees", defaultValue: 112, range: "33..112"], parameterSize: 1],
        43: [input: [name: "configParam43", type: "number", title:"Remote Sensor 1 Node Number", description: "", defaultValue: 0, range: "0..252"], parameterSize: 1],
        48: [input: [name: "configParam48", type: "number", title:"Internal Sensor Temp Offset", description: "degrees", defaultValue: 0, range: "-7..7"], parameterSize: 1],
        49: [input: [name: "configParam49", type: "number", title:"Remote Sensor Temp Offset", description: "degrees", defaultValue: 0, range: "-7..7"], parameterSize: 1],
        53: [input: [name: "configParam53", type: "number", title:"Filter Timer Max (hrs)", description: "hours", defaultValue: 4000, range: "0..4000"], parameterSize: 2],
        61: [input: [name: "configParam61", type: "number", title:"Fan Purge Heat", description: "seconds", defaultValue: 90, range: "0..90"], parameterSize: 1],
        62: [input: [name: "configParam62", type: "number", title:"Fan Purge Cool", description: "seconds", defaultValue: 90, range: "0..90"], parameterSize: 1],
        186: [input: [name: "configParam182", type: "number", title:"Temperature Report Threshold", description: "degrees", defaultValue: 1, range: "1..5"], parameterSize: 1],
        187: [input: [name: "configParam187", type: "number", title:"Temperature Report Periodic", description: "minutes", defaultValue: 0, range: "0..120"], parameterSize: 1]
]

void logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

void configure() {
    if (!state.initialized) initializeVars()
    runIn(10, "syncClock")
    runIn(5, "pollDeviceData")
    runEvery3Hours("syncClock")
}

void initializeVars() {
    // first run only
    state.each{k -> v
        state.remove("$k")
    }
    sendEvent(name:"supportedThermostatModes", value: supportedThermostatModes.toString().replaceAll(/"/,""), isStateChange:true)
    sendEvent(name:"supportedThermostatFanModes", value: supportedThermostatFanModes.toString().replaceAll(/"/,""), isStateChange:true)
    state.initialized=true
    runIn(5, refresh)
}

void installed() {
    if (logEnable) log.debug "installed()..."
    initializeVars()
}

void updated() {
    log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    unschedule()
    if (logEnable) runIn(1800,logsOff)
    runConfigs()
    runEvery3Hours("syncClock")
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

List<hubitat.zwave.Command> pollConfigs() {
    List<hubitat.zwave.Command> cmds=[]
    configParams.each { param, data ->
        if (settings[data.input.name]) {
            cmds.add(zwave.configurationV1.configurationGet(parameterNumber: param.toInteger()))
        }
    }
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 21))
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 22))
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 52))
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 54))
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 55))
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 46))
    return cmds
}

List<hubitat.zwave.Command> configCmd(parameterNumber, size, scaledConfigurationValue) {
    List<hubitat.zwave.Command> cmds = []
    int intval=scaledConfigurationValue.toInteger()
    if (size==1) {
        if (intval < 0) intval = 256 + intval
        cmds.add(zwave.configurationV1.configurationSet(parameterNumber: parameterNumber.toInteger(), size: size.toInteger(), configurationValue: [intval]))
    } else {
        cmds.add(zwave.configurationV1.configurationSet(parameterNumber: parameterNumber.toInteger(), size: size.toInteger(), scaledConfigurationValue: intval))
    }
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: parameterNumber.toInteger()))
    return cmds
}

void SensorCal(value) {
    if (logEnable) log.debug "SensorCal($value)"
    List<hubitat.zwave.Command> cmds=[]
    cmds.addAll(configCmd(48,1,value))
    sendToDevice(cmds)
}

void zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
    int scaledValue
    cmd.configurationValue.reverse().eachWithIndex { v, index -> scaledValue=scaledValue | v << (8*index) }
    if(configParams[cmd.parameterNumber.toInteger()]) {
        Map configParam=configParams[cmd.parameterNumber.toInteger()]
        if (configParam.parameterSize==1 && configParam.input.range) {
            if (configParam.input.range.matches("-(.*)")) {
                if (scaledValue > 127) {
                    scaledValue = scaledValue - 256
                }
            }
        }
        if (cmd.parameterNumber==48) {
            eventProcess(name: "currentSensorCal", value: scaledValue)
        }
        device.updateSetting(configParam.input.name, [value: "${scaledValue}", type: configParam.input.type])
    } else {
        switch (cmd.parameterNumber) {
            case 21:
                // mechanical status
                mechUpdate(scaledValue)
                break
            case 22:
                // scp status
                scpUpdate(scaledValue)
                break
            case 46:
                // remote senor temp
                if (scaledValue > 0) {
                    eventProcess(name:"remoteTemperature", value: "${scaledValue}", unit: configParam7 == 1 ? "F" : "C")
                }
                break
            case 52:
                // filter timer hours
                filterCheck(scaledValue)
                break
            case 54:
                // heat timer hours
                state.heatTimer=scaledValue
                break
            case 55:
                // cool timer hours
                state.coolTimer=scaledValue
                break
        }
    }
}

void eventProcess(Map evt) {
    if (device.currentValue(evt.name).toString() != evt.value.toString()) {
        evt.isStateChange=true
        sendEvent(evt)
    }
}

void filterReset() {
    List<hubitat.zwave.Command> cmds=[]
    cmds.addAll(configCmd(52,2,0))
    sendToDevice(cmds)
}

private void filterCheck(hours) {
    state.filterHours=hours
    if (hours>configParam53) {
        eventProcess(name: "filterStatus", value: "replace")
    } else {
        eventProcess(name: "filterStatus", value: "normal")
    }
}

private void scpUpdate(value) {
    List<String> scp = []
    if ((value & (1L << 0)) != 0) scp.add("HEAT")
    if ((value & (1L << 1)) != 0) scp.add("COOL")
    if ((value & (1L << 2)) != 0) scp.add("2ND")
    if ((value & (1L << 3)) != 0) scp.add("3RD")
    if ((value & (1L << 4)) != 0) scp.add("FAN")
    if ((value & (1L << 5)) != 0) scp.add("LAST")
    if ((value & (1L << 6)) != 0) scp.add("MOT")
    if ((value & (1L << 7)) != 0) scp.add("MRT")
    eventProcess(name: "scpStatus", value: scp.toString())
}

private void mechUpdate(value) {
    List<String> mechStatus = []
    if ((value & (1L << 0)) != 0) mechStatus.add("H1")
    if ((value & (1L << 1)) != 0) mechStatus.add("H2")
    if ((value & (1L << 2)) != 0) mechStatus.add("H3")
    if ((value & (1L << 3)) != 0) mechStatus.add("C1")
    if ((value & (1L << 4)) != 0) mechStatus.add("C2")
    if ((value & (1L << 5)) != 0) mechStatus.add("PHANTOM_F")
    if ((value & (1L << 6)) != 0) mechStatus.add("MECH_F")
    if ((value & (1L << 7)) != 0) mechStatus.add("MANUAL_F")
    if ((value & (1L << 8)) != 0) mechStatus.add("reserved")
    eventProcess(name: "mechStatus", value: mechStatus.toString())
}

void pollDeviceData() {
    List<hubitat.zwave.Command> cmds = []
    cmds.add(zwave.configurationV1.configurationSet(parameterNumber: 23, size: 2, configurationValue: [0xFF,0xFF]))
    cmds.add(zwave.versionV2.versionGet())
    cmds.addAll(pollConfigs())
    cmds.addAll(processAssociations())
    sendToDevice(cmds)
}

void refresh() {
    List<hubitat.zwave.Command> cmds=[]
    cmds.add(zwave.batteryV1.batteryGet())
    cmds.add(zwave.sensorMultilevelV1.sensorMultilevelGet())
    cmds.add(zwave.thermostatFanModeV1.thermostatFanModeGet())
    cmds.add(zwave.thermostatFanStateV1.thermostatFanStateGet())
    cmds.add(zwave.thermostatModeV1.thermostatModeGet())
    cmds.add(zwave.thermostatOperatingStateV1.thermostatOperatingStateGet())
    cmds.add(zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 1))
    cmds.add(zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 2))
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 21))
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 22))
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 52))
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 54))
    cmds.add(zwave.configurationV1.configurationGet(parameterNumber: 55))
    sendToDevice(cmds)
    runIn(10, "syncClock")
}

void syncClock() {
    Calendar currentDate = Calendar.getInstance()
    sendToDevice(zwave.clockV1.clockSet(hour: currentDate.get(Calendar.HOUR_OF_DAY), minute: currentDate.get(Calendar.MINUTE), weekday: currentDate.get(Calendar.DAY_OF_WEEK)))
}

void zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(CMD_CLASS_VERS)
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand)
    }
}

void zwaveEvent(hubitat.zwave.commands.multicmdv1.MultiCmdEncap cmd) {
    if (logEnable) log.debug "Got multicmd: ${cmd}"
    cmd.encapsulatedCommands(CMD_CLASS_VERS).each { encapsulatedCommand ->
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
    cmds.add(zwave.associationV2.associationSet(groupingIdentifier: 3, nodeId: zwaveHubNodeId))
    cmds.add(zwave.associationV2.associationGet(groupingIdentifier: 3))
    return cmds
}

List<hubitat.zwave.Command> processAssociations(){
    List<hubitat.zwave.Command> cmds = []
    cmds.addAll(setDefaultAssociation())
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

void zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
    if (logEnable) log.debug "got battery report: ${cmd.batteryLevel}"
    Map evt = [name: "battery", unit: "%"]
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

void zwaveEvent(hubitat.zwave.commands.sensormultilevelv1.SensorMultilevelReport cmd) {
    if (cmd.sensorType.toInteger() == 1) {
        if (logEnable) log.debug "got temp: ${cmd.scaledSensorValue}"
        eventProcess(name: "temperature", value: cmd.scaledSensorValue, unit: cmd.scale == 1 ? "F" : "C")
    }
}

void setpointCalc(String newmode, String unit, value) {
    String mode="cool"
    if (device.currentValue("thermostatMode")=="heat" || device.currentValue("thermostatMode")=="emergency heat") {
        state.lastMode="heat"
        mode="heat"
    } else if (device.currentValue("thermostatMode")=="cool") {
        state.lastMode="cool"
        mode="cool"
    } else if (device.currentValue("thermostatOperatingState")=="heating" || device.currentValue("thermostatOperatingState")=="pending heat") {
        state.lastMode="heat"
        mode="heat"
    } else if (device.currentValue("thermostatOperatingState")=="cooling" || device.currentValue("thermostatOperatingState")=="pending cool") {
        state.lastMode="cool"
        mode="cool"
    } else if (state.lastMode) {
        mode=state.lastMode
    }
    if (newmode==mode) {
        eventProcess(name: "thermostatSetpoint", value: value, unit: unit)
    }
}

void zwaveEvent(hubitat.zwave.commands.thermostatsetpointv2.ThermostatSetpointReport cmd) {
    if (logEnable) log.debug "Got thermostat setpoint report: ${cmd}"
    if (device.currentValue("thermostatMode")=="heat") mode="heat"
    if (device.currentValue(""))
        String unit=cmd.scale == 1 ? "F" : "C"
    switch (cmd.setpointType) {
        case 1:
            eventProcess(name: "heatingSetpoint", value: cmd.scaledValue, unit: unit)
            setpointCalc("heat", unit, cmd.scaledValue)
            break
        case 2:
            eventProcess(name: "coolingSetpoint", value: cmd.scaledValue, unit: unit)
            setpointCalc("cool", unit, cmd.scaledValue)
            break
    }
}

void zwaveEvent(hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport cmd) {
    if (logEnable) log.debug "Got thermostat operating state report: ${cmd}"
    String newstate=THERMOSTAT_OPERATING_STATE[cmd.operatingState.toInteger()]
    if (logEnable) log.debug "Translated state: " + newstate
    eventProcess(name: "thermostatOperatingState", value: newstate)
    if (newstate=="cooling") {
        state.lastMode="cool"
    } else if (newstate=="heating") {
        state.lastMode="heat"
    } else if (newstate=="pending heat") {
        state.lastMode="heat"
    } else if (newstate=="pending cool") {
        state.lastMode="cool"
    }
}

void zwaveEvent(hubitat.zwave.commands.thermostatfanstatev1.ThermostatFanStateReport cmd) {
    if (logEnable) log.debug "Got thermostat fan state report: ${cmd}"
    String newstate=THERMOSTAT_FAN_STATE[cmd.fanOperatingState.toInteger()]
    if (logEnable) log.debug "Translated fan state: " + newstate
    log.info "Fan state: " + newstate
    sendToDevice(zwave.configurationV1.configurationGet(parameterNumber: 52))
    if (newstate=="idle" && (device.currentValue("thermostatOperatingState")=="heating" || device.currentValue=="cooling")) sendToDevice(zwave.thermostatOperatingStateV1.thermostatOperatingStateGet())
}

void zwaveEvent(hubitat.zwave.commands.thermostatfanmodev1.ThermostatFanModeReport cmd) {
    if (logEnable) log.debug "Got thermostat fan mode report: ${cmd}"
    String newmode=THERMOSTAT_FAN_MODE[cmd.fanMode.toInteger()]
    if (logEnable) log.debug "Translated fan mode: " + newmode
    eventProcess(name: "thermostatFanMode", value: newmode)
}

void zwaveEvent(hubitat.zwave.commands.thermostatmodev1.ThermostatModeReport cmd) {
    if (logEnable) log.debug "Got thermostat mode report: ${cmd}"
    String newmode=THERMOSTAT_MODE[cmd.mode.toInteger()]
    if (logEnable) log.debug "Translated thermostat mode: " + newmode
    eventProcess(name: "thermostatMode", value: newmode)
}

void zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
    // setup basic reports for missed operating state changes
    if (cmd.value.toInteger()==0xFF) {
        if (device.currentValue("thermostatOperatingState")!="heating" || device.currentValue!="cooling") sendToDevice(zwave.thermostatOperatingStateV1.thermostatOperatingStateGet())
    } else {
        if (device.currentValue("thermostatOperatingState")=="heating" || device.currentValue=="cooling") sendToDevice(zwave.thermostatOperatingStateV1.thermostatOperatingStateGet())
    }
}

private void setSetpoint(setPointType, value) {
    List<hubitat.zwave.Command> cmds=[]
    cmds.add(zwave.thermostatSetpointV2.thermostatSetpointSet(setpointType: setPointType, scale: getTemperatureScale()=="F" ? 1:0 , precision: 0, scaledValue: value))
    cmds.add(zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: setPointType))
    sendToDevice(cmds)
}

void setHeatingSetpoint(degrees) {
    if (logEnable) log.debug "setHeatingSetpoint(${degrees}) called"
    setSetpoint(1,degrees)
}

void setCoolingSetpoint(degrees) {
    if (logEnable) log.debug "setCoolingSetpoint(${degrees}) called"
    setSetpoint(2,degrees)
}

void setThermostatMode(mode) {
    if (logEnable) log.debug "setThermostatMode($mode)"
    List<hubitat.zwave.Command> cmds = []
    if (logEnable) log.debug "setting zwave thermostat mode ${SET_THERMOSTAT_MODE[mode]}"
    cmds.add(zwave.thermostatModeV1.thermostatModeSet(mode: SET_THERMOSTAT_MODE[mode]))
    cmds.add(zwave.thermostatModeV1.thermostatModeGet())
    sendToDevice(cmds)
}

void off() {
    setThermostatMode("off")
}

void on() {
    log.warn "Ambiguous use of on()"
}

void heat() {
    setThermostatMode("heat")
}

void emergencyHeat() {
    setThermostatMode("emergency heat")
}

void cool() {
    setThermostatMode("cool")
}

void auto() {
    setThermostatMode("auto")
}

void setThermostatFanMode(mode) {
    if (logEnable) log.debug "setThermostatFanMode($mode)"
    List<hubitat.zwave.Command> cmds = []
    if (logEnable) log.debug "setting zwave thermostat fan mode ${SET_THERMOSTAT_FAN_MODE[mode]}"
    cmds.add(zwave.thermostatFanModeV1.thermostatFanModeSet(fanMode: SET_THERMOSTAT_FAN_MODE[mode]))
    cmds.add(zwave.thermostatFanModeV1.thermostatFanModeGet())
    sendToDevice(cmds)
}

void fanOn() {
    setThermostatFanMode("on")
}

void fanAuto() {
    setThermostatFanMode("auto")
}

void fanCirculate() {
    log.warn "fanCirculate is not supported by this device"
}

void setSchedule() {
    log.warn "setSchedule is not supported by this driver"
}