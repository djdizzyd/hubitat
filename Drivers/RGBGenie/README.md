# LED Controllers

All RGBgenie Z-Wave Plus LED controllers work through the Zwave-LED-Controller.groovy driver
The LED controller driver allows you to change the type of device so you are not limited by the original model purchase. You can adjust between CCT, Single Color, and RGBW models, and it will change the device's functionality at a firmware level.

RGBW Models:
You can optionally enable / disable the WarmWhite component so if you are using RGB only LED strips you won't get signals sent to the WarmWhite output mistakenly.
When using just RGB LEDs color temperature is simulated using just RGB color elemets. Between 2700K to 6500K
When using RGBW LEDs, to get the best color temperature result, you can set the kelvin temperature of the white LED element, to match the specs of your LED strips, and the color temperature range will adjust to the capabilities of your LED strip. The Cold White color temperatures are created using the RGB elements.

Features:
* Color Temperature Control
* RGB Color Control
* Dimmer Controls
* Light Effects
* Ability to change the device type at the firmware level swap between CT/RGBW/Single Color 
* Color temperature control on CCT & RGBW models even using RGB only LED strips


# Touch Panels

A child driver can be loaded for each zone on the multi zone models, the Scene only models are a single Zone by default.
This child driver can be used with the Hubitat built-in mirror me application to syncronize the output of the touch panel to groups of devices that do not support Z-wave associations. For example, Zigbee or Wifi devices.
Optionally the child drivers can utilize the Scene buttons as button controllers or scene capture / activate. With the button controller functionality on a 3 scene / 3 zone touch panel you would end up with 9 buttons that can be pushed or held to have actions mapped to any functionality in hubitat.

All touch panels support up to 12 associations directly per zone. These can be utilized in any combination per zone, including multiple occurrences across each zone in the multi-zone units. Child devices occupy 1 of the 12 available associations per zone when activated.. 

Features:
* Scene capture / activate
* Button controller
* Multiple Child devices on the multi-zone capable models
* Combinations of z-wave direct association and hub control
* Ability to control any number of devices through the hub's mirror app Z-wave, Zigbee, Wifi, etc
* Configurable between scene activation / scene capture and button control features
* Insane amount of possible configurations and uses

# Device / Driver List

|Model|Description|Driver(s)|
| --- | --- | --- |
|ZV-1008|RGBW LED Controller with built-in power supply|Zwave-LED-Controller.groovy|
|ZW-1000|CCT LED Controller|Zwave-LED-Controller.groovy|
|ZW-1001|Single Color LED Controller|Zwave-LED-Controller.groovy|
|ZW-1002|RGBW LED Controller|Zwave-LED-Controller.groovy|
|ZW-3001|3 Zone / 3 Scene Single Color Touch Panel White|Zwave-Touch-Panel.groovy|
|ZW-3002|3 Scene Color Touch Panel White|Zwave-Touch-Panel.groovy|
|ZW-3003|3 Scene Color Touch Panel Black|Zwave-Touch-Panel.groovy|
|ZW-3004|3 Zone Color Touch Panel White|Zwave-Touch-Panel.groovy|
|ZW-3005|3 Zone Color Touch Panel Black|Zwave-Touch-Panel.groovy|
|ZW-3011|3 Zone / 3 Scene CCT Color Touch Panel White|Zwave-Touch-Panel.groovy|
|ZW-4001|Micro Controller and Lamp Module|Zwave-Micro-Controller.groovy|

# Changelog
2020-02-26
* Added importUrl to all drivers
* Added optional gamma correction to LED Controller Driver

Updated 2020-03-11 
* Improvements in color reporting reliability

Updated 2020-04-08
* Major coding style improvements
* Removal of custom child driver in favor of built-in component drivers
* Future proofing

Updated 2020-04-11 
* Added duplicate event filtering
* Fixed typo in micro controller preferences

When updating to this version please click save on device preference

Follow the original thread for support / bug reports / updates:

[[RELEASE] RGBGenie Z-Wave Device Drivers (all)](https://community.hubitat.com/t/release-rgbgenie-z-wave-device-drivers-all/34999?u=bcopeland)
