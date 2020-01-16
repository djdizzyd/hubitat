
definition(
    name: "Inovelli Group Notifier App",
    namespace: "djdizzyd",
    author: "Bryan Copeland",
    description: "Inovelli Group Notification Designer",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "")

preferences {
	page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: " ", install: true, uninstall: true) {
        section {
            app(name: "childGroups", appName: "Inovelli Group Notifier App Group Child", namespace: "djdizzyd", title: "New Group", multiple: true)
        }
        section {
            app(name: "childEffects", appName: "Inovelli Group Notifier App Effect Child", namespace: "djdizzyd", title: "New Effect", multiple: true)
		}
    }
}

def updated() {
    initialize()
}

def installed() {
    initialize()
}

def uninstalled() {

}

def getEffect(effectName) {
    def effectChild=getChildAppByLabel("effect-" + effectName)
    if(!effectChild) {
        return 0
	} 
    return effectChild.getEffectValue()
}


def initialize() {

}

def childUninstalled() {

}
