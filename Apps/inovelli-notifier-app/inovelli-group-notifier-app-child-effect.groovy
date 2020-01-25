definition(
    name: "Inovelli Group Notifier App Effect Child",
    namespace: "djdizzyd",
    author: "Bryan Copeland",
    description: "Inovelli Group Notification Designer",
    category: "Convenience",
    parent: "djdizzyd:Inovelli Group Notifier App",
    iconUrl: "",
    iconX2Url: "")

preferences {
	page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: " ", install: true, uninstall: true) {
        section {
            input "thisName", "text", title: "Name This Notification", submitOnChange: true, required: true
            if(thisName) app.updateLabel("effect-$thisName") else app.updateSetting("thisName", "Name")
                input "color", "enum", title: "LED Effect Color - Notification", description: "Tap to set", submitOnChage: false, required: true, options: [
                    0:"Red",
                    21:"Orange",
                    42:"Yellow",
                    85:"Green",
                    127:"Cyan",
                    170:"Blue",
                    212:"Violet",
                    234:"Pink"]
                input "level", "enum", title: "LED Effect Level - Notification", description: "Tap to set", submitOnChange: false, required: true, options: [
                    0:"0%",
                    1:"10%",
                    2:"20%",
                    3:"30%",
                    4:"40%",
                    5:"50%",
                    6:"60%",
                    7:"70%",
                    8:"80%",
                    9:"90%",
                    10:"100%"]
                input "duration", "enum", title: "LED Effect Duration - Notification", description: "Tap to set", submitOnChange: false, required: true, options: [
                    1:"1 Second",
                    2:"2 Seconds",
                    3:"3 Seconds",
                    4:"4 Seconds",
                    5:"5 Seconds",
                    6:"6 Seconds",
                    7:"7 Seconds",
                    8:"8 Seconds",
                    9:"9 Seconds",
                    10:"10 Seconds",
                    20:"20 Seconds",
                    30:"30 Seconds",
                    40:"40 Seconds",
                    50:"50 Seconds",
                    60:"60 Seconds",
                    62:"2 Minutes",
                    63:"3 Minutes",
                    64:"4 Minutes",
                    65:"5 Minutes",
                    255:"Indefinetly"]
                input "type", "enum", title: "LED Effect Type - Notification", description: "Tap to set", submitOnChange: false, required: true, options: [
                    0:"Off",
                    1:"Solid",
                    2:"Chase",
                    3:"Fast Blink",
                    4:"Slow Blink",
                    5:"Pulse"]
        }
    }
}

def installed() {
    initialize()
}

def updated() {
    initialize()
}

def getEffectValue() {
    return calculateParameter()
}

def initialize() {
    
}

def calculateParameter() {
    def value = 0
    value += settings."color"!=null ? settings."color".toInteger() * 1 : 0
    value += settings."level"!=null ? settings."level".toInteger() * 256 : 0
    value += settings."duration"!=null ? settings."duration".toInteger() * 65536 : 0
    value += settings."type"!=null ? settings."type".toInteger() * 16777216 : 0
    return value
}