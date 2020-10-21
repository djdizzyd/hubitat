
Zooz Zen22 Central Scene Dimmer
chat_bubble_outline
more_vert

Dashboards
Devices
Apps
Settings
Advanced
codeApps Code
codeDrivers Code
System Events
Logs
Zooz Zen22 Central Scene DimmerImport 
Spaces
 
4
 
No wrap
HelpDeleteSave
0
1
/*
2
*   Zen22 Central Scene Dimmer
3
*   version: 1.0
4
*/
5
​
6
import groovy.transform.Field
7
​
8
metadata {
9
    definition (name: "Zooz Zen22 Central Scene Dimmer", namespace: "djdizzyd", author: "Bryan Copeland", importUrl: "https://raw.githubusercontent.com/djdizzyd/hubitat/master/Drivers/zooz/zen22-dimmer.groovy") {
10
        capability "SwitchLevel"
11
        capability "Switch"
12
        capability "Refresh"
13
        capability "Actuator"
14
        capability "Sensor"
15
        capability "Configuration"
16
        capability "ChangeLevel"
17
        capability "PushableButton"
18
        capability "HoldableButton"
19
        capability "ReleasableButton"
20
        capability "Indicator"
21
​
22
        fingerprint mfr:"027A", prod:"B112", deviceId:"1F1C", inClusters:"0x5E,0x26,0x85,0x8E,0x59,0x55,0x86,0x72,0x5A,0x73,0x70,0x5B,0x9F,0x6C,0x7A", deviceJoinName: "Zooz Zen22 Dimmer" //US
23
​
24
    }
25
    preferences {
26
        configParams.each { input it.value.input }
27
        input name: "associationsG2", type: "string", description: "To add nodes to associations use the Hexidecimal nodeID from the z-wave device list separated by commas into the space below", title: "Associations Group 2"
28
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
29
        input name: "txtEnable", type: "bool", title: "Enable text logging", defaultValue: false
30
    }
31
}
32
@Field static Map configParams = [
33
        1: [input: [name: "configParam1", type: "enum", title: "On/Off Paddle Orientation", description: "", defaultValue: 0, options: [0:"Normal",1:"Reverse",2:"Any paddle turns on/off"]], parameterSize: 1],
34
        2: [input: [name: "configParam2", type: "enum", title: "LED Indicator Control", description: "", defaultValue: 0, options: [0:"Indicator is on when switch is off",1:"Indicator is on when switch is on",2:"Indicator is always off",3:"Indicator is always on"]], parameterSize: 1],
35
        3: [input: [name: "configParam3", type: "enum", title: "Auto Turn-Off Timer", description: "", defaultValue: 0, options: [0:"Timer disabled",1:"Timer Enabled"]], parameterSize: 1],
36
        4: [input: [name: "configParam4", type: "number", title: "Auto Off Timer", description: "Minutes 0-65535", defaultValue: 60, range:"0..65535"], parameterSize:4],
37
        5: [input: [name: "configParam5", type: "enum", title: "Auto Turn-On Timer", description: "", defaultValue: 0, options: [0:"timer disabled",1:"timer enabled"]],parameterSize:1],
38
        6: [input: [name: "configParam6", type: "number", title: "Auto On Timer", description: "Minutes 0-65535", defaultValue: 60, range:"0..65535"], parameterSize: 4],
39
        7: [input: [name: "configParam7", type: "enum", title: "Association Reports", description: "", defaultValue: 15, options:[0:"none",1:"physical tap on ZEN27 only",2:"physical tap on 3-way switch only",3:"physical tap on ZEN27 or 3-way switch",4:"Z-Wave command from hub",5:"physical tap on ZEN27 or Z-Wave command",6:"physical tap on connected 3-way switch or Z-wave command",7:"physical tap on ZEN27 / 3-way switch / or Z-wave command",8:"timer only",9:"physical tap on ZEN27 or timer",10:"physical tap on 3-way switch or timer",11:"physical tap on ZEN 27 / 3-way switch or timer",12:"Z-wave command from hub or timer",13:"physical tap on ZEN27, Z-wave command, or timer",14:"physical tap on ZEN27 / 3-way switch / Z-wave command, or timer", 15:"all of the above"]],parameterSize:1],
40
        8: [input: [name: "configParam8", type: "enum", title: "On/Off Status After Power Failure", description: "", defaultValue: 2, options:[0:"Off",1:"On",2:"Last State"]],parameterSize:1],
41
        9: [input: [name: "configParam9", type: "number", title: "Ramp Rate Control", description: "Seconds: 0-99", defaultValue: 1, range:"0..99"], parameterSize:1],
42
        17: [input: [name: "configParam17", type: "enum", title: "Z-Wave Ramp Control", description: "", defaultValue: 1, options: [0:"Z-Wave ramp rate matches physical",1:"Z-Wave ramp rate is set independently"]],parameterSize:1],
43
        10: [input: [name: "configParam10", type: "number", title: "Minimum Level", description: "1-99%", defaultValue: 1, range:"1..99"], parameterSize:1],
44
        11: [input: [name: "configParam11", type: "number", title: "Maximum level", description: "1-99%", defaultValue: 99, range:"1..99"], parameterSize:1],
45
        12: [input: [name: "configParam12", type: "enum", title: "Double Tap Function", description: "", defaultValue: 0, options:[0:"Turn on full brightness",1:"Turn on to maximum level"]], parameterSize:1],
46
        14: [input: [name: "configParam14", type: "enum", title: "Double/Single Tap Function", description:"", defaultValue: 0, options:[0:"double tap to full / maximum brightness level enabled",1:"double tap to full / maximum brightness level disabled, single tap turns light on to last brightness level",2:" double tap to full / maximum brightness level disabled, single tap turns light on to full / maximum brightness level"]],parameterSize:1],
47
        13: [input: [name: "configParam13", type: "enum", title: "Enable/Disable Scene Control", defaultValue: 0, options:[0:"Scene control disabled",1:"scene control enabled"]],parameterSize:1],
48
        15: [input: [name: "configParam15", type: "enum", title: "Smart Bulb Mode", defaultValue: 1, options:[0:"physical paddle control disabled",1:"physical paddle control enabled",2:"physical paddle and z-wave control disabled"]],parameterSize: 1],
49
        20: [input: [name: "configParam20", type: "enum", title: "Report Type", defaultValue:0, options: [0:"report each brightness level to hub when physical / Z-Wave control is disabled for physical dimming (final level only reported if physical / Z-Wave control is enabled)",1:"report final brightness level only for physical dimming, regardless of the physical / Z-Wave control mode"]], parameterSize:1],
50
        21: [input: [name: "configParam21", type: "enum", title: "Report Type Disabled Physical", defaultValue:0, options: [0:"switch reports on/off status and changes LED indicator state even if physical and Z-Wave control is disabled", 1:"switch doesn't report on/off status or change LED indicator state when physical (and Z-Wave) control is disabled"]], parameterSize:1],
51
        16: [input: [name: "configParam16", type: "number", title: "Physical Dimming Speed", description: "Seconds 1-99", defaultValue: 4, range:"1..99"], parameterSize: 1],
52
        18: [input: [name: "configParam18", type: "number", title: "Custom Brightness Level On", description: "0 – last brightness level (default); 1 – 99 (%) for custom brightness level", defaultValue: 0, range: "0..99"], parameterSize:1],
53
        19: [input: [name: "configParam19", type: "enum", title: "3-Way Switch Type", defaultValue: 0, options: [0:"regular mechanical 3-way on/off switch",1:"regular mechanical 3-way on/off switch, tap the paddles once to change state (light on or off), tap the paddles twice quickly to turn light on to full brightness, tap the paddles quickly 3 times to enable a dimming sequence",2:"momentary switch, click once to change status (light on or off), click twice quickly to turn light on to full brightness, press and hold to adjust brightness",3:"momentary switch, click once to change status (light on or off), click twice quickly to turn light on to full brightness, press and hold to adjust brightness"]],parameterSize:1],
54
        22: [input: [name: "configParam22", type: "number", title: "Night Light Mode", description: "0 – feature disabled; 1 – 99 (%). Default: 20", defaultValue: 20, range: "0..99"], parameterSize:1]
55
]
56
@Field static Map CMD_CLASS_VERS=[0x20:1,0x5B:3,0x86:3,0x72:2,0x8E:3,0x85:2,0x59:1,0x26:2,0x70:1]
57
@Field static int numberOfAssocGroups=3
58
void logsOff(){
59
    log.warn "debug logging disabled..."
60
    device.updateSetting("logEnable",[value:"false",type:"bool"])
61
}
62
​
63
void configure() {
64
    if (!state.initialized) initializeVars()
65
    runIn(5,pollDeviceData)
66
}
67
​
68
void initializeVars() {
69
    // first run only
70
    state.initialized=true
71
    runIn(5, refresh)
72
}
73
​
Location: Home
Terms of Service
Documentation
Community
Support
Copyright 2020 Hubitat, Inc.
