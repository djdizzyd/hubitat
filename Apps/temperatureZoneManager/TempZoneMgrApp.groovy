definition(
    name: "Temperature Zone Manager",
    namespace: "djdizzyd",
    author: "Bryan Copeland",
    description: "Temperature Zone Manager",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "")

preferences {
	page(name: "mainPage")
}

def mainPage() {
	dynamicPage(name: "mainPage", title: " ", install: true, uninstall: true) {
        section {
            paragraph "Settings:"
            input "masterTemp", "bool", title: "Enable whole-house temperature", required: true, submitOnChange: true
            if (masterTemp) {
                input "indoorTempDevs", "capability.temperatureMeasurement", title: "Master indoor temperature devices", required: true, multiple: true
            }
            input "enableDebug", "bool", title: "Enable Debug Logging", required: true, defaultValue: false
		}
		section {
            paragraph "Rooms:"
            app(name: "roomApps", appName: "TempZoneRoomChild", namespace: "djdizzyd", title: "New Room", multiple: true)
		}
	}
}

def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {

}


appLog(message, level="debug") {
// one log to rule them all
    switch(level) {
        case "debug":
            if (enableDebug) log.debug "${message}"
            break;
        case "warn": 
            log.warn "${message}"
            break;
        case "info":
            log.info "${message}"
            break;
        case "error":
            log.error "${message}"
            break;
    }
}

