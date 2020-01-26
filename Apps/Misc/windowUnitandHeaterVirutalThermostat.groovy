definition(
    name: "Window Unit and Space Heater Thermostat Controller",
    namespace: "djdizzyd",
    author: "Bryan Copeland",
    description: "Window Unit and Space Heater Thermostat Controller",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "")

preferences {
	page(name: "mainPage")
}

def mainPage() {
	dynamicPage(name: "mainPage", title: " ", install: true, uninstall: true) {
		section {
			input "thisName", "text", title: "Name this unit; virtual thermostat device will have this name", submitOnChange: true
			if(thisName) app.updateLabel("$thisName") else app.updateSetting("thisName", "Window Unit Thermostat Controller")
			input "coolingOutletDev", "capability.switch", title: "Select Cooling Outlet", submitOnChange: true, required: true
			input "heatingOutletDev", "capability.switch", title: "Select Heating Outlet", submitOnChange: true, required: true
            input "tempDev", "capability.temperatureMeasurement", title: "Select Temperature Device", submitOnChange: true, required: true
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
	def virtualThermostatDev = getChildDevice("virtualThermostat_${app.id}")
	if(!virtualThermostatDev) virtualThermostatDev = addChildDevice("hubitat", "Virtual Thermostat", "virtualThermostat_${app.id}", null, [label: thisName, name: thisName])
    virtualThermostatDev.setSupportedThermostatModes("[cool, heat, off]")
    virtualThermostatDev.setThermostatMode("cool")
    virtualThermostatDev.setSupportedThermostatFanModes("[auto]")
	subscribe(tempDev, "temperature", tempHandler)
    subscribe(virtualThermostatDev, "thermostatOperatingState", thermostatHandler)
    virtualThermostatDev.setTemperature(tempDev.currentValue("temperature"))
    // subscribe(outletDev, "switch", switchHandler)
}

def tempHandler(evt) {
    if (!getChildDevice("virtualThermostat_${app.id}")) initialize()
    def virtualThermostatDev = getChildDevice("virtualThermostat_${app.id}")
    log.debug "Got temperature: ${evt.value} setting thermostat temperature"
    virtualThermostatDev.setTemperature(evt.value)
}

def thermostatHandler(evt) {
    log.debug "Got thermostate state ${evt.value}"
    switch(evt.value) {
         case "cooling":   
            log.debug "switching on " + coolingOutletDev.getDisplayName()
            coolingOutletDev.on()
            break;
		 case "heating":
			log.debug "switching on " + heatingOutletDev.getDisplayName()
			heatingOutletDev.on()
			break;
         case "idle":
			if (coolingOutletDev.currentValue("switch") == "on") {
            	log.debug "switching off " + coolingOutletDev.getDisplayName()
            	coolingOutletDev.off()
			}
			if (heatingOutletDev.currentValue("switch") == "on") {
				log.debug "switching off " + heatingOutletDev.getDisplayName()
				heatingOutletDev.off()
			}
            break;
    }
}


