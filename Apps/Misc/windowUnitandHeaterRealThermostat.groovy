definition(
    name: "AC and Space Heater Thermostat Controller",
    namespace: "djdizzyd",
    author: "Bryan Copeland",
    description: "AC Unit and Space Heater Thermostat Controller",
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
            input "enableCooling", "bool", title: "Enable Cooling", submitOnChange: true, required: true
            if (enableCooling) {
			    input "coolingOutletDev", "capability.switch", title: "Select Cooling Outlet", submitOnChange: true, required: true
            }
            input "enableHeating", "bool", title: "Enable Heating", submitOnChange: true, required: true
            if (enableHeating) {
			    input "heatingOutletDev", "capability.switch", title: "Select Heating Outlet", submitOnChange: true, required: true
            }
            input "realThermostat", "bool", title: "Use real thermostat?", submitOnChange: true, required: true
            if (realThermostat) {
                input "thermostatDev", "capability.thermostat", title: "Select Thermostat Device", submitOnChange: true, required: true
			} else {
                input "tempDev", "capability.temperatureMeasurement", title: "Select Temperature Device", submitOnChange: true, required: true
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



def uninstalled() {
    unsubscribe()
    unschedule()
    def virtualThermostatDev = getChildDevice("virtualThermostat_${app.id}")
    if (virtualThermostatDev) {
        deleteChildDevice(virtualThermostatDev.getDeviceNetworkId())
    }
}

def refreshThermostat() {
    // on occasion I had a thermostat miss a message
    if (realThermostat) {
        thermostatDev.refresh()
	} else {
        tempDev.refresh()
	}
}

def initialize() {
    def virtualThermostatDev = getChildDevice("virtualThermostat_${app.id}")
    if (realThermostat) {
        subscribe(thermostatDev, "thermostatOperatingState", thermostatHandler)
        if (virtualThermostatDev) {
            deleteChildDevice(virtualThermostatDev.getDeviceNetworkId())
		}
	} else {
	    if(!virtualThermostatDev) {
            virtualThermostatDev = addChildDevice("hubitat", "Virtual Thermostat", "virtualThermostat_${app.id}", null, [label: thisName, name: thisName])
        }
        def supportedModes="["
        if (enableCooling) { supportedModes += "cool, " }
        if (enableHeating) { supportedModes += "heat, " }
        supportedModes += "off]"
        virtualThermostatDev.setSupportedThermostatModes(supportedModes)
        virtualThermostatDev.setSupportedThermostatFanModes("[auto]")
	    subscribe(tempDev, "temperature", tempHandler)
        subscribe(virtualThermostatDev, "thermostatOperatingState", thermostatHandler)
        virtualThermostatDev.setTemperature(tempDev.currentValue("temperature"))
    }
    runEvery30Minutes("refreshThermostat")
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
            if (enableCooling) {
                log.debug "switching on " + coolingOutletDev.getDisplayName()
                coolingOutletDev.on()
            } else { 
                log.debug "Error got cooling event but no cooling support"     
			}
            break;
		 case "heating":
            if (enableHeating) {
			    log.debug "switching on " + heatingOutletDev.getDisplayName()
			    heatingOutletDev.on()
            } else {
                log.debug "Error got heating event but no heating support"     
			}
			break;
         default:
            if (enableCooling) {
			    if (coolingOutletDev.currentValue("switch") == "on") {
                	log.debug "switching off " + coolingOutletDev.getDisplayName()
                	coolingOutletDev.off()
			    }
            }
            if (enableHeating) {
			    if (heatingOutletDev.currentValue("switch") == "on") {
			    	log.debug "switching off " + heatingOutletDev.getDisplayName()
			    	heatingOutletDev.off()
			    }
            }
            break;
    }
}


