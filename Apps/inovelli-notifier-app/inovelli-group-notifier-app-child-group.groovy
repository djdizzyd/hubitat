definition(
    name: "Inovelli Group Notifier App Group Child",
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
                input "thisName", "text", title: "Name This Group", submitOnChange: true, required: true
                if(thisName) app.updateLabel("group-$thisName") else app.updateSetting("thisName", "Name")
                input "switchDevs", "device.InovelliDimmerRedSeriesDjdizzyd", title: "Switches", submitOnChange: false, required: true, multiple: true
        }
    }
}

installed() {
    initialize()
}

updated() {
    initialize()
}

initialize() {
    def groupDev = getChildDevice("inovelli_group_notifier_$thisName")
	if(!groupDev) groupDev = addChildDevice("djdizzyd", "Inovelli Group Notifier Child Device", "inovelli_group_notifier_$thisName", null, [label: thisName, name: thisName])
}

sendNotification(notificationText) {
    def effectValue = parent.getEffect(notificationText)
    if (effectValue > 0) {
        for (dev in switchDevs) {
            dev.notificationValue(effectValue)
        }
    }
}