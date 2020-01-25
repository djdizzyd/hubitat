definition(
    name: "RoomModeChild",
    namespace: "djdizzyd",
    author: "Bryan Copeland",
    description: "Temperature Zone Manager Mode Child",
    category: "Convenience",
    parent: "djdizzyd:TempZoneRoomChild"
    iconUrl: "",
    iconX2Url: "")


preferences {
	page(name: "mainPage")
}

def mainPage() {
	dynamicPage(name: "mainPage", title: " ", install: true, uninstall: true) {
        section {
            paragraph "Room Settings"
            input "thisName", "text", title: "Room Name", submitOnChange: true, required: true
            if(thisName) app.updateLabel("Room-$thisName") else app.updateSetting("thisName", "Room-New")
            input "temperatureDevs", "capability.temperatureMeasurement", submitOnChange: true, required: true
            input "temperatureOffset", "decimal", submitOnChange: false, required: false, defaultValue: 0
            input "hasCooling", "bool", title: "Enable Cooling", submitOnChange: true, required: true
            input "hasHeating", "bool", title: "Enable Heating", submitOnChange: true, required: true
            if (hasCooling) {
                input "coolingDev", "capability.switch", title: "Cooling Switch", submitOnChange: true, required: true
			}
            if (hasHeating) {
                input "heatingDev", "capability.switch", title: "Heating Switch", submitOnChange: true, required: true     
			}
		}
		section {
            paragraph "Presense"
            input "presenseEnable", "bool", title: "Enable Presense Controls", submitOnChange: true, required: true
            if (presenseEnable) {
                app(name: "presenseApp", appName: "RoomPresenseChild", namespace: "djdizzyd", title: "Presense Settings", multiple: false)
            }
		}
        section {
            paragraph "Modes"
            input "modeEnable", "bool", title: "Enable Mode Control", submitOnChange: true, required: true
            if (modeEnable) {
                app(name: "modeApps", appName: "RoomModeChild", namespace: "djdizzyd", title: "Add Mode", multiple: true)     
			}
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