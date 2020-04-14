import groovy.transform.Field

metadata {

    definition (name: "Ring Alarm Range Extender", namespace: "djdizzyd", author: "Bryan Copeland") {
        capability "Actuator"
        capability "Refresh"
        capability "Sensor"
        capability "Configuration"
        capability "Battery"
        capability "PowerSource"

        fingerprint mfr:"0346", prod:"0401", deviceId:"0201", inClusters:"0x5E,0x85,0x59,0x55,0x86,0x72,0x5A,0x73,0x9F,0x80,0x71,0x6C,0x70,0x7A", deviceJoinName: "Ring Alarm Range Extender" //US
        fingerprint mfr:"0346", prod:"0401", deviceId:"0202", inClusters:"0x5E,0x85,0x59,0x55,0x86,0x72,0x5A,0x73,0x9F,0x80,0x71,0x6C,0x70,0x7A", deviceJoinName: "Ring Alarm Range Extender" //UK

    }
    preferences {
        configParams.each { input it.value.input }
        input name: "enableSwitch", type: "bool", title: "Enable power fail component switch", defaultValue: false
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
    }
}

@Field static Map configParams = [
        1: [input: [name: "configParam1", type: "number", title: "Battery Report Interval", description: "minutes", range: "4..70", defaultValue: 70], parameterSize: 1],
]
@Field static Map CMD_CLASS_VERS=[0x86:2,0x70:1,0x20:1]
@Field static Map ZWAVE_NOTIFICATION_TYPES=[
        0:"Reserverd",
        1:"Smoke",
        2:"CO",
        3:"CO2",
        4:"Heat",
        5:"Water",
        6:"Access Control",
        7:"Home Security",
        8:"Power Management",
        9:"System",
        10:"Emergency",
        11:"Clock",
        12:"First"
]

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
    if (!enableSwitch) {
        if (getChildDevice("${device.deviceNetworkId}-1")) {
            deleteChildDevice("${device.deviceNetworkId}-1")
        }
    } else {
        if(!getChildDevice("${device.deviceNetworkId}-1")) {
            com.hubitat.app.ChildDeviceWrapper child=addChildDevice("hubitat", "Generic Component Switch", "${device.deviceNetworkId}-1", [completedSetup: true, label: "${device.displayName} (Power Fail Switch)", isComponent: true, componentName: "powerfail switch"])
            if (device.currentValue("powerSource")) {
                if (device.currentValue("powerSource")=="mains") {
                    child.parse([[name: "switch", value: "on", isStateChange: true]])
                } else {
                    child.parse([[name: "switch", value: "off", isStateChange: true]])
                }
            }
        }
    }
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

void installed() {
    initializeVars()
}

void uninstalled() {

}

void initializeVars() {
    // first run only
    sendEvent(name:"battery", value:100)
    sendEvent(name:"powerSource", value:"mains")
    state.initialized=true
}

void configure() {
    if (!state.initialized) initializeVars()
    runIn(5,pollDeviceData)
}

void refresh() {

}

void pollDeviceData() {
    List<hubitat.zwave.Command> cmds = []
    cmds.add(zwave.versionV2.versionGet())
    cmds.add(zwave.manufacturerSpecificV2.deviceSpecificGet(deviceIdType: 1))
    cmds.add(zwave.batteryV1.batteryGet())
    cmds.add(zwave.notificationV8.notificationGet(notificationType: 8, event: 0))
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
    if (logEnable) log.debug "parse:${description}"
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
    if (txtEnable) log.info evt.descriptionText
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

void zwaveEvent(hubitat.zwave.commands.notificationv8.NotificationReport cmd) {
    Map evt = [isStateChange:false]
    log.info "Notification: " + ZWAVE_NOTIFICATION_TYPES[cmd.notificationType]
    if (cmd.notificationType==4) {
        // heat alarm
        switch (cmd.event) {
            case 0:
                log.info "${device.dsiplayName} Heat Alarm: CLEARED"
                break
            case 2:
                log.warn "${device.DisplayName} Heat Alarm: OVERHEAT DETECTED"
                break
        }
    } else if (cmd.notificationType==5) {
        // Water Alarm
        evt.name="water"
        evt.isStateChange=true
        switch (cmd.event) {
            case 0:
                // state idle
                evt.value="dry"
                evt.descriptionText="${device.displayName} is ${evt.value}"
                break
            case 1:
                // water leak detected (location provided)
                evt.value="wet"
                evt.descriptionText="${device.displayName} is ${evt.value}"
                break
            case 2:
                // water leak detected
                evt.value="wet"
                evt.descriptionText="${device.displayName} is ${evt.value}"
                break
        }
    } else if (cmd.notificationType==6) {
        // access control
        switch (cmd.event) {
            case 0:
                // state idle
                break
            case 22:
                // Window / door is open
                evt.name="contact"
                evt.isStateChange=true
                evt.value="open"
                evt.descriptionText="${device.displayName} is ${evt.value}"
                break
            case 23:
                // Window / door is closed
                evt.name="contact"
                evt.isStateChange=true
                evt.value="closed"
                evt.descriptionText="${device.displayName} is ${evt.value}"
                break
        }
    } else if (cmd.notificationType==7) {
        // home security
        switch (cmd.event) {
            case 0:
                // state idle
                if (cmd.eventParametersLength>0) {
                    switch (cmd.eventParameter[0]) {
                        case 1:
                            evt.name="contact"
                            evt.value="closed"
                            evt.isStateChange=true
                            evt.descriptionText="${device.displayName} contact became ${evt.value}"
                            break
                        case 2:
                            evt.name="contact"
                            evt.value="closed"
                            evt.isStateChange=true
                            evt.descriptionText="${device.displayName} contact became ${evt.value}"
                            break
                        case 3:
                            evt.name="tamper"
                            evt.value="clear"
                            evt.isStateChange=true
                            evt.descriptionText="${device.displayName} tamper alert cover closed"
                            break
                        case 7:
                            evt.name="motion"
                            evt.value="inactive"
                            evt.isStateChange=true
                            evt.descriptionText="${device.displayName} motion became ${evt.value}"
                            break
                        case 8:
                            evt.name="motion"
                            evt.value="inactive"
                            evt.isStateChange=true
                            evt.descriptionText="${device.displayName} motion became ${evt.value}"
                            break
                    }
                } else {
                    // should probably do something here
                }
                break
            case 1:
                // Intrusion (location provided)
                evt.name="contact"
                evt.value="open"
                evt.isStateChange=true
                evt.descriptionText="${device.displayName} contact became ${evt.value}"
                break
            case 2:
                // Intrusion
                evt.name="contact"
                evt.value="open"
                evt.isStateChange=true
                evt.descriptionText="${device.displayName} contact became ${evt.value}"
                break
            case 3:
                // Tampering cover removed
                evt.name="tamper"
                evt.value="detected"
                evt.isStateChange=true
                evt.descriptionText="${device.displayName} tamper alert cover removed"
                break
            case 4:
                // Tampering, invalid code
                log.warn "Invalid code"
                break
            case 5:
                // glass breakage (location provided)
                break
            case 6:
                // glass breakage
                break
            case 7:
                // motion detected (location provided)
                evt.name="motion"
                evt.value="active"
                evt.isStateChange=true
                evt.descriptionText="${device.displayName} motion became ${evt.value}"
                break
            case 8:
                // motion detected
                evt.name="motion"
                evt.value="active"
                evt.isStateChange=true
                evt.descriptionText="${device.displayName} motion became ${evt.value}"
                break
            case 9:
                // tampering product removed
                break
            case 10:
                // impact detected
                break
            case 11:
                // magnetic field interference detected
                break
            case 254:
                // unknown event/state
                log.warn "Device sent unknown event / state notification"
                break
        }
    } else if (cmd.notificationType==8) {
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
                evt.name="powerSource"
                evt.isStateChange=true
                evt.value="battery"
                evt.descriptionText="${device.displayName} AC mains disconnected"
                if (enableSwitch) {
                    com.hubitat.app.ChildDeviceWrapper child=getChildDevice("${device.deviceNetworkId}-1")
                    if (child) {
                        child.parse([[name: "switch", value: "off", isStateChange: true]])
                    }
                }
                break
            case 3:
                // AC mains re-connected
                evt.name="powerSource"
                evt.isStateChange=true
                evt.value="mains"
                evt.descriptionText="${device.displayName} AC mains re-connected"
                if (enableSwitch) {
                    com.hubitat.app.ChildDeviceWrapper child=getChildDevice("${device.deviceNetworkId}-1")
                    if (child) {
                        child.parse([[name: "switch", value: "on", isStateChange: true]])
                    }
                }
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
        eventProcess(evt)
    }
}

void eventProcess(Map evt) {
    if (device.currentValue(evt.name).toString() != evt.value.toString()) {
        evt.isStateChange=true
        sendEvent(evt)
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

