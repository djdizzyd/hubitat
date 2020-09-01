definition(
		name: "SunriseSunset",
		namespace: "djdizzyd",
		author: "Bryan Copeland",
		description: "My Sunrise/Sunset Rules",
		category: "Convenience",
		iconUrl: "",
		iconX2Url: "")

preferences {
	page(name: "mainPage")
}

def mainPage() {
	dynamicPage(name: "mainPage", title: " ", install: true, uninstall: true) {
		section {
			input "ctBulbDevs", "capability.colorTemperature", title: "Color Teperature Bulbs", submitOnChange: true, required: true, multiple: true
			input "sunriseDevsOff", "capability.switch", title: "Sunrise devs to turn off", submitOnChange: true, required: true, multiple: true
			input "sunsetDevsOn", "capability.switch", title: "Sunset devs to turn on", submitOnChange: true, required: true, multiple: true
		}
		section {
			input "testSunset", "button", title: "Test Sunset"
			input "testSunrise", "button", title: "Test Sunrise"
		}
	}
}

def appButtonHandler(btn) {
	switch(btn) {
		case "testSunset":
			sunsetActions()
			break;
		case "testSunrise":
			sunriseActions()
			break;
	}
}

def installed() {
	initialize()
}

def updated() {
	unsubscribe()
	unschedule()
	subscribe(location, "sunset", sunsetHandler)
	subscribe(location, "sunrise", sunriseHandler)
//	scheduleUpdate()
	initialize()
}

def sunriseHandler(evt) {
	log.info "Got sunrise event"
	sunriseActions()
}

def sunsetHandler(evt) {
	log.info "Got sunset event"
	sunsetActions()
}

def initialize() {

}

def scheduleUpdate() {
	unschedule()
	def riseAndSet = getSunriseAndSunset()
	log.debug "Sunrise: " + riseAndSet.sunrise.toString()
	log.debug "Sunset: " + riseAndSet.sunset.toString()
	def updateScheduler = new Date(riseAndSet.sunrise.getTime() + 79200000)
	log.debug "Scheduler Update: " + updateScheduler.toString()
	runOnce(updateScheduler, "scheduleUpdate")
	runOnce(riseAndSet.sunrise, "sunriseActions")
	runOnce(riseAndSet.sunset, "sunsetActions")
}

def sunriseActions() {
	for (dev in ctBulbDevs) {
		dev.setColorTemperature(6500)
		log.debug "Setting CT to 6500 on " + dev.getDisplayName()
	}
	for (dev in sunriseDevsOff) {
		log.debug "Turning off " + dev.getDisplayName()
		dev.off()
	}
}

def sunsetActions() {
	for (dev in sunsetDevsOn) {
		log.debug "Turning on " + dev.getDisplayName()
		dev.on()
	}
	for (dev in ctBulbDevs) {
		dev.setColorTemperature(2700)
		log.debug "Setting CT to 2700 on " + dev.getDisplayName()
	}

}

