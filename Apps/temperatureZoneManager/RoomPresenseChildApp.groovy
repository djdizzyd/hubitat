definition(
    name: "RoomPresenseChild",
    namespace: "djdizzyd",
    author: "Bryan Copeland",
    description: "Temperature Zone Manager Presense Child",
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
            paragraph "Presense Devices"
            input "presenseDevs", "capability.presenceSensor", submitOnChange: true, required: true, multiple: true
		}
		section {
            paragraph "Away Settings"
            if (parent.hasCooling) {
            
			}
		}
        section {
            paragraph "Home Settings"

		}
        section {
            paragraph "Sleep Settings"  

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
    app.updateLabel("Room-${parent.roomName}-Presense")
}