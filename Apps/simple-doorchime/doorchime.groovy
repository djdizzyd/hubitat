definition(
	name: "Simple Door Chime",
	namespace: "djdizzyd",
	author: "Bryan Copeland",
	description: "Simple Door Chime App",
	category: "Convenience",
	iconUrl: "",
	iconX2Url: "",
	importUrl: "https://raw.githubusercontent.com/djdizzyd/hubitat/master/Apps/simple-doorchime/doorchime.groovy"
)

preferences {
	page(name: "mainPage")
}

def mainPage() {
	dynamicPage(name: "mainPage", title: " ", install: true, uninstall: true) {
		section {
			input "thisName", "text", title: "Name this door chime", submitOnChange: true
			if(thisName) app.updateLabel("$thisName") else app.updateSetting("thisName", "Simple Door Chime")
			input "contactDev", "capability.contactSensor", title: "Select Contact Sensor", submitOnChange: true, required: true, multiple: true
			input "debounce", "bool", title: "Enable Debounce", submitOnChange: true, required: true, defaultValue: false
			if (debounce) {
				input "delayTime", "number", title: "Enter number of milliseconds to delay for debounce", submitOnChange: true, defaultValue: 1000
			}
			input "chimeType", "enum", title: "Type of chime device", options: ["chime": "chime", "speechSynthesis": "TTS", "tone": "Tone/Beep"], submitOnChange: true
			if (chimeType){
				input "chimeDev", "capability.$chimeType", title: "Select Chime Device", submitOnChange:true, required: true, hideWhenEmpty: "chimeType"
			}
			if (chimeDev) {
				switch (chimeType) {
					case "chime":
						if (chimeDev.hasAttribute("soundEffects")) {
					    def soundEffectsList = [:]
						for (soundEffect in chimeDev.currentState("soundEffects").getStringValue().minus("{").minus("}").split(",")) {
							def effect = soundEffect.split("=")
							soundEffectsList.put(effect[0],effect[0] + " - " + effect[1]) 
						}         
						log.debug "sound effects list: " + soundEffectsList
						input "soundNum", "enum", title: "Sound to play", options: soundEffectsList, submitOnChange: true, required: true
						} else {
							input "soundNum", "number", title: "Sound to play", submitOnChange: true, required: true
						}
						break;
					case "speechSynthesis":
						input "speakText", "text", title: "Text to speak", submitOnChange: true, required: true
						break;
				}
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
    log.debug "initializing"
    for (dev in contactDev) {
        log.debug "subscribing to " + dev.getDisplayName()
	    subscribe(dev, "contact", handler)
    }
}

def handler(evt) {
	if (debounce) {
		runInMillis(delayTime, debounced, [data: [o: evt.value, d: evt.device]])
		log.info "Contact $evt.device $evt.value, start delay of $delayTime milliseconds"
	} else {
		debounced([o: evt.value, d: evt.device])
	}
}

def debounced(data) {
	if(data.o == "open") {
		log.info "Contact $data.d debounced chiming" +  chimeDev.getDisplayName() + " sound number $soundNum"
		chimeAction()
	} 
}

def chimeAction() {
	log.debug "Chime Action"
	switch(chimeType) {
		case("chime"): 
			//chime Type
			log.debug "playing sound: $soundNum on " + chimeDev.getDisplayName()
			chimeDev.playSound(soundNum.toInteger())
			break;
		case("speechSynthesis"):
			// speech Type
			log.debug "Speaking '$speakText' on " + chimeDev.getDisplayName()
			chimeDev.speak(speakText)
			break;
		case("tone"):
			// tone Type
			log.debug "Sending beep to " + chimeDev.getDisplayName()
			chimeDev.beep()
			break;
	}
}