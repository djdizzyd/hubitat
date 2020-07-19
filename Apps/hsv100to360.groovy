definition(
        name: "Hue Color Mapper",
        namespace: "djdizzyd",
        author: "Bryan Copeland",
        description: "HSL 0-100 color map",
        singleInstance: true,
        category: "My Apps",
        documentationLink: "",
        iconUrl: " ",
        iconX2Url: " ",
        iconX3Url: " ")

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Hubitat HSV100 Colors", stall: true, uninstall: true) {
        section {
            String table = "<table border=1><tr><th>Hue<br/>360</th><th>Hue<br/>100</th><th>Color<br/>Hex</th></tr>"
            for (int i in 0..100) {
                List hsv100 = [i,100,100]
                List rgbColor = hubitat.helper.ColorUtils.hsvToRGB(hsv100)
                List hsv360 = hubitat.helper.ColorUtils.rgbToHSV360(rgbColor)
                String rgbHex = hubitat.helper.ColorUtils.rgbToHEX(rgbColor)
                if (i==100) hsv360[0]=360
                table += "<tr><td>${Math.round(hsv360[0])}</td><td>${i}</td><td bgcolor='${rgbHex}'>${rgbHex}</td></tr>"
            }
            table += "</table>"
            paragraph table
        }
    }
}

void installed() {

}

void updated() {

}