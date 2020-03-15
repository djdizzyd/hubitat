

def hueByteToHue(byteValue) {
	// hue as 0-255 return hue as 0-360
	return Math.Round(byteValue * (360/255))
}

def hueToHueByte(hueValue) {
	// hue as 0-360 return hue as 0-255
	return Math.Round(hueValue / (360/255))
}

def hueToHuePrecision(hueValue) {
	// hue as 0-100 return hue as 0-360
	return Math.Round(hueVlaue * (360/100))
}

def huePrecisionToHue(hueValue) {
	// hue as 0-360 return hue as 0-100
	return Math.Round(hueValue / (360/100))
}

