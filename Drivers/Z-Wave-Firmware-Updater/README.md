# Hubitat Z-Wave Firmware Updater #

The firmware updater is in the form of a driver. It is a utility based driver similar to the basicZwaveTool. It is designed to be switched to, used to update your devices, and then switch back to your original driver. 

While released exclusively to the Hubitat platform. This is not a product of Hubitat Inc. This is a community developed and community supported feature. 

## Use at your own risk ##
I am providing this as a convenience for the community and as such make no guarantees or warranties. If you damage a device from proper or improper use of this program you are on your own.

## Requirements:  ##
* Web server hosted OTZ or HEX update file. (can be local on your LAN or elsewhere ex: github
* Z-wave device that is OTA capable and supports Firmware Update MD Command Class V1-4 
* Good connectivity between the device and your Hubitat hub. 

## Warnings: ##
* Devices that are region specific, make sure you have a firmware file that is for your region.
* Some devices will require exclude/include when complete. This has been noticed on some hardware. Most will not require this.
* There are many provisions in this code and in the Z-wave spec that prevent this process from damaging your device. But I offer no guarantee or warranty. **Use at your own risk!**
* **This driver can only be used on 1 device at a time due to the shared memory requirement to enable this process.** 

## Process: ##
1. Go to the device details page for the device you want to update
2. Under device information change the driver to **Z-Wave Firmware Updater** and click **Save Device**
3. Type in the URL for the firmware update file in the **Update Firmware** command and then click Update Firmware
4. Wait.. This process can take a while
5. Watch under Current States for progress updates. 

At any time you can click **Abort Process** to stop the firmware update.

In the rare case of the process being locked from a previous attempt, you can click **Clear Lock** to clear the stale lock.

After clicking the **Update Firmware** button this process can take a while.. The firmware must be downloaded and processed into memory. Then must be parsed for information to verify that the firmware matches the device's firmware image. Only after all these steps will the device begin requesting parts of the firmware binary. You will see a progress indicator reporting the percentage of data that has been transferred. After the whole image has been transferred your device will verify it to and reply back with a status. And then if the status is good your device will flash itself and reboot.

## Note for sleepy (battery powered) devices: ## 
Throughout this process you may need to wake the device a few times before everything can be completed. Refer to the manual provided by the manufacturer for the wake mechanism for your device.

To report any bugs or issues please use the original forum thread:
https://community.hubitat.com/t/release-z-wave-firmware-updater/38237?u=bcopeland

