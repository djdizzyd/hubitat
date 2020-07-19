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
            String table = "<table border=1><tr><th>Hue<br/>360</th><th>Hue<br/>100</th><th>Color<br/>Hex</th>"
            for (int i in (95..0).step(5)) {
                table += "<th>sat<br/>${i}</th>"
            }
            table +="</tr>"
            for (int h in 0..100) {
                List hsv100 = [h,100,100]
                List rgbColor = hubitat.helper.ColorUtils.hsvToRGB(hsv100)
                List hsv360 = hubitat.helper.ColorUtils.rgbToHSV360(rgbColor)
                String rgbHex = hubitat.helper.ColorUtils.rgbToHEX(rgbColor)
                if (h==100) hsv360[0]=360
                table += "<tr><td>${Math.round(hsv360[0])}</td><td>${h}</td><td bgcolor='${rgbHex}'>${rgbHex}</td>"
                for (int s in (95..0).step(5)) {
                    String rgbColorSat = hubitat.helper.ColorUtils.rgbToHEX(hubitat.helper.ColorUtils.hsvToRGB([h,s,100]))
                    table += "<td bgcolor='${rgbColorSat}'></td>"
                }
                table += "</tr>"
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