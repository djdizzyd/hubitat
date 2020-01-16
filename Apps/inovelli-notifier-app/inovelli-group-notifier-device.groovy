metadata {
	definition (name: "Inovelli Group Notifier Child Device", namespace: "djdizzyd", author: "Bryan Copeland") {
		capability "Actuator"
        capability "Notification"
	}
}

def deviceNotification(value) {
    parent.sendNotification(device.getDisplayName(), value)
}
