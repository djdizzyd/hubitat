// v1.01 fix off-by-one error
import groovy.transform.Field
import java.util.concurrent.ConcurrentHashMap
import java.util.TreeMap
import java.security.MessageDigest

metadata {
    definition (name: "Z-Wave Firmware Updater",namespace: "djdizzyd", author: "Bryan Copeland", importUrl: "https://raw.githubusercontent.com/djdizzyd/hubitat/master/Drivers/Z-Wave-Firmware-Updater/zwaveBinaryUpdater.groovy") {
        attribute "currentFirmwareVersion", "string"
        attribute "firmwareUpdateProgress", "string"
        attribute "firmwareUploadPercent", "string"
        attribute "manufacturerId", "string"
        attribute "firmwareId", "string"
        attribute "firmwareTarget", "number"
        attribute "firmwareFragmentSize", "number"
        attribute "lockedBy", "string"
        attribute "storedParameters", "string"
        attribute "storedParameterCount", "number"

        command "clearLock"
        command "getParameters", [[name: "parameterNumbers*", type: "STRING", description: "Range of property numbers to store. Use , as a separator, and - for ranges."],
                                  [name: "interval", type: "INTEGER", description: "Number of milliseconds to wait between consecutive zwave commands. Too slow and it will take a long time. Too fast and some commands may be dropped.", defaultValue: 200]]
        command "restoreParameters", [[name: "parameterNumbers", type: "STRING", description: "Range of property numbers to set to their cached value. Use , as a separator, and - for ranges. Leave blank to restore all that are stored."],
                                      [name: "interval", type: "INTEGER", description: "Number of milliseconds to wait between consecutive zwave commands. Too slow and it will take a long time. Too fast and some commands may be dropped.", defaultValue: 200]]
        command "setParameter", [[name:"parameterNumber",type:"INTEGER", description:"Parameter Number"],
                                 [name:"size",type:"ENUM", description:"Parameter Size", constraints:["1", "2", "4"]],
                                 [name:"value",type:"INTEGER", description:"Parameter Value"]]
        command "getVersionReport"
        command "abortProcess"
        command "updateFirmware", [[name:"binaryFirmwareUrl*", type: "STRING", description:"Firmware URL"],
                                   [name:"firmwareTarget", type: "ENUM", description: "Firmware Target", constraints: ["0","1","2","3","4","5"]],
                                   [name: "md5", type: "STRING", description: "MD5 checksum of firmware file. If set, update will abort if the downloaded file doesn't match this checksum"]
        ]
    }
    preferences {
        input name: "debugEnable", type: "bool", description: "", title: "Enable Debug Logging", defaultVaule: false
        input name: "sleepyTimeout", type: "integer", description: "The number of seconds to wait for the device to respond before deciding that the device must be asleep.", defaultValue: 5
    }
}
@Field static ConcurrentHashMap<Integer, Integer> parameterValues = new ConcurrentHashMap(32)
@Field static ConcurrentHashMap<Integer, Integer> parameterSizes = new ConcurrentHashMap(32)

@Field static Map CMD_CLASS_VERS=[0x85:1,0x86:1,0x7A:3]
@Field static String lockedBy="none"
@Field static String lockedByName=""
@Field static List<Byte> byteBuffer=[]
@Field static ByteArrayOutputStream newByteBuffer = new ByteArrayOutputStream()
@Field static List<byte[]> firmwareChunks = []
@Field static List<Byte> decompressedBytes=[]
@Field static Map firmwareTargets=[:]
@Field static Integer theFirmwareTarget
@Field static Map firmwareUpdateInfo=[:]
@Field static Integer firmwareUpdateMdVersion=0
@Field static Map firmwareMdInfo=[:]
@Field static Map otzHeader=[:]
@Field static Map firmwareDescriptor=[:]
@Field static String theFirmwareUpdateUrl
@Field static Boolean locked=false
@Field static Boolean abort=false
@Field static String expectedMd5Sum = null
@Field static Integer timeout = 5

@Field static Map firmwareUpdateMdStatus=[
        0:"The device was unable to receive the requested firmware data without checksum error",
        1:"The device was unable to receive the requested firmware data",
        2:"The transferred image does not match the Manufacturer ID",
        3:"The transferred image does not match the Firmware ID",
        4:"The transferred image does not match the Firmware Target",
        5:"Invalid file header information",
        6:"Invalid file header format",
        7:"Insufficient memory",
        8:"The transferred image does not match the Hardware version",
        253:"Firmware image downloaded successfully, waiting for activation command",
        254:"New image was successfully stored in temporary non-volatile memory. The device does not restart itself.",
        255:"New image was successfully stored in temporary non-volatile memory. The device will now start storing the new image in primary non-volatile memory dedicated to executable code. Then the device will restart itself."
]
@Field static Map firmwareUpdateMdActivationStatus=[
        0:"Invalid combination of manufacturer ID, firmware ID and Hardware Version or Firmware Target",
        1:"Error activating the firmware. Last known firmware image has been restored",
        255:"Firmware update completed successfully"
]
@Field static Map firmwareUpdateMdPrepareStatus=[
        0:"ERROR. Invalid combination of Manufacturer ID and Firmware ID",
        1:"ERROR. Device expected an authentication event to enable firmware update",
        2:"ERROR. The requested Fragment Size exceeds the Max Fragment Size",
        3:"ERROR. This firmware target is not downloadable",
        4:"ERROR. Invalid Hardware Version",
        255:"OK. The receiving node can initiate the firmware download of the target specified in the Firmware Update Meta Data Prepare Get Command."
]
@Field static Map firmwareUpdateMdRequestStatus=[
        0:"ERROR. Invalid combination of Manufacturer ID and Firmware ID",
        1:"ERROR. Device expected an authentication event to enable firmware update",
        2:"ERROR. The requested Fragment Size exceeds the Max Fragment Size",
        3:"ERROR. This firmware target is not upgradable",
        4:"ERROR. Invalid Hardware Version",
        5:"ERROR: Another firmware image is current being transferred",
        6:"ERROR: Insufficient battery level",
        255:"OK. The device will initiate the firmware update of the target specified in the Firmware Update Meta Data Request Get Command"
]

void updated() {}

List<Integer> parseRange(String range) {
//    log.debug "range $range"
    List<Integer> split = range.split(',').collect{it.trim()}.collect {
        it.contains('-') ? it.split('-')[0].toInteger()..it.split('-')[1].toInteger() : it.toInteger()
    }.flatten()
//    log.debug "split $split"
    split
}

List<String> getParameters(String range, Integer interval = null) {
    interval = interval ?: 200
    List<Integer> parameterNumbers = parseRange(range)
    log.info "getting current values for parameters $parameterNumbers"
    List<String> cmds = parameterNumbers.collect{zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: it))}
    log.debug cmds
    delayBetween(cmds, interval)
}

List<String> setParameter(Integer parameterNumber, Integer size, Integer value){
    log.debug("set parameter number $parameterNumber with size $size to $value")
    delayBetween([
            zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: value, parameterNumber: parameterNumber, size: size)),
            zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: parameterNumber))
    ],500)
}

List<String> restoreParameters(String range, Integer interval = null) {
    interval = interval ?: 200
    List<Integer> parameterNumbers = parseRange(range).findAll{parameterValues.containsKey(it) && parameterSizes.containsKey(it)}
    log.info "restoring stored values for parameters $parameterNumbers"
    List<String> cmds = parameterNumbers.collect{
        Integer size = parameterSizes.get(it)
        Integer value = parameterValues.get(it)
        log.debug("set parameter number $it with size $size to $value")
        [
                zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: value, parameterNumber: it, size: size)),
                zwaveSecureEncap(zwave.configurationV1.configurationGet(parameterNumber: it))
        ]
    }.flatten()
    delayBetween(cmds, interval)
}


void zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
    unschedule('showStoredParameters')
    log.info "ConfigurationReport- parameterNumber:${cmd.parameterNumber}, size:${cmd.size}, value:${cmd.scaledConfigurationValue}"
    parameterValues.put(cmd.parameterNumber as Integer, cmd.scaledConfigurationValue as Integer)
    parameterSizes.put(cmd.parameterNumber as Integer, cmd.size as Integer)
    runIn(3, 'showStoredParameters')
}

void showStoredParameters() {
    TreeMap<Integer, Integer> sortedParameters = new TreeMap(parameterValues)
    sendEvent(name: "storedParameters", value: sortedParameters.toString())
    sendEvent(name: "storedParameterCount", value: sortedParameters.size())
}

void clearLock() {
    memoryCleanup()
}

void crossDeviceLock() {
    log.debug "locked by: ${lockedBy}"
    if (locked) {
        if (lockedBy==device.getDeviceNetworkId()) {
            if (device.currentValue("lockedBy")!="This Process") {
                sendEvent(name: "lockedBy", value: "This process")
            }
        } else {
            sendEvent(name: "lockedBy", value: lockedByName)
        }
    } else {
        if (device.currentValue("lockedBy")!="None") sendEvent(name: "lockedBy", value: "None")
    }
}


void parse(String description) {
    if (logEnable) log.debug "parse:${description}"
    hubitat.zwave.Command cmd = zwave.parse(description, CMD_CLASS_VERS)
    if (cmd) {
        zwaveEvent(cmd)
    }
}

void zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
    unschedule('wakeUp')
    log.info "VersionReport- zWaveProtocolVersion:${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}"
    log.info "VersionReport- applicationVersion:${cmd.applicationVersion}.${cmd.applicationSubVersion}"
    sendEvent (name: "currentFirmwareVersion", value: "${cmd.applicationVersion}.${cmd.applicationSubVersion}")
    if (theFirmwareUpdateUrl != "") {
        if (!abort) {
            sendEvent (name: "firmwareUpdateProgress", value: "Getting firmware meta data...")
            runIn(timeout,'wakeUp')
            sendToDevice(zwave.firmwareUpdateMdV3.firmwareMdGet())
        }
    }
}

void zwaveEvent(hubitat.zwave.commands.versionv1.VersionCommandClassReport cmd) {
    unschedule('wakeUp')
    if (cmd.requestedCommandClass==122) {
        log.info "FirmwareUpdateMd version:${cmd.commandClassVersion}"
        firmwareUpdateMdVersion=cmd.commandClassVersion
        if (!abort) {
            runIn(timeout,'wakeUp')
            sendToDevice(zwave.versionV1.versionGet())
        }
    } else {
        log.info "CommandClassReport- class:${ "0x${intToHexStr(cmd.requestedCommandClass)}" }, version:${cmd.commandClassVersion}"
    }
}

void zwaveEvent(hubitat.zwave.Command cmd) {
    if (debugEnable) log.debug "skip: ${cmd}"
}

void zwaveEvent(hubitat.zwave.commands.firmwareupdatemdv3.FirmwareUpdateMdGet cmd) {
    unschedule('wakeUp')
    if (debugEnable) log.debug "Got request for fragment #:${cmd.reportNumber} packing report and sending"

    List<Byte> bytes=[]
    def reportNumber=cmd.reportNumber

    def startByte=((reportNumber-1)*firmwareMdInfo['maxFragmentSize'])
    Boolean lastReport=false
    def lastByte=startByte+firmwareMdInfo['maxFragmentSize']
    int percent=Math.round((reportNumber/(Math.ceil(byteBuffer.size()/firmwareMdInfo['maxFragmentSize'])))*100)
    if (device.currentValue("firmwareUploadPercent") != "${percent}%") {
        sendEvent(name: "firmwareUploadPercent", value: "${percent}%")
    }
    if (lastByte >= byteBuffer.size()) {
        lastReport=true
        lastByte = byteBuffer.size()
    }
    if (lastReport) {
        reportNumber+=32768
        updateProgress("Sent last fragment. Waiting on device...")
    }
    bytes.add(0x7A) // CC
    bytes.add(0x06) // command
    bytes.add((byte) ((reportNumber >> 8) & 0xFF))
    bytes.add((byte) (reportNumber & 0xFF))
    for (int i=startByte; i<lastByte; i++) {
        bytes.add(byteBuffer[i])
    }
    String newcmd=""
    bytes.each { newcmd+=hubitat.helper.HexUtils.integerToHexString(it & 0xff,1).padLeft(2, '0')}
    if (firmwareUpdateMdVersion>1) {
        Integer crc = zwaveCrc16(bytes)
        newcmd+=hubitat.helper.HexUtils.integerToHexString(crc,1).padLeft(4, '0')
    }
    if(!abort) sendToDevice(newcmd)
}

void zwaveEvent(hubitat.zwave.commands.firmwareupdatemdv3.FirmwareUpdateMdStatusReport cmd) {
    unschedule('wakeUp')
    if (debugEnable) log.debug "Status: ${cmd}"
    if (cmd.status < 253) {
        log.warn firmwareUpdateMdStatus[cmd.status as Integer]
        // we must stop here
        updateProgress("FAILED: " + firmwareUpdateMdStatus[cmd.status as Integer])
        memoryCleanup()
    } else {
        switch (cmd.status) {
            case 253:
                // need to activate
                log.info firmwareUpdateMdStatus[cmd.status as Integer]
                runIn(timeout,'wakeUp')
                response(activateFirmwareImage)
                break
            case 254:
                // need manual restart
                log.info firmwareUpdateMdStatus[cmd.status as Integer]
                updateProgress(firmwareUpdateMdStatus[cmd.status as Integer])
                memoryCleanup()
                break
            case 255:
                log.info firmwareUpdateMdStatus[cmd.status as Integer]
                updateProgress("Complete device is flashing...")
                memoryCleanup()
                break
        }
    }
}

void activateFirmwareImage() {
    String rawpacket="7A08" // FrmUpdMd FrmUpdActSet
    rawpacket+=hubitat.helper.HexUtils.integerToHexString(firmwareUpdateInfo['manufacturerId'],2).padLeft(4, '0')
    rawpacket+=hubitat.helper.HexUtils.integerToHexString(firmwareUpdateInfo['firmwareId'],2).padLeft(4, '0')
    rawpacket+=hubitat.helper.HexUtils.integerToHexString(firmwareUpdateInfo['checksum'],2).padLeft(4, '0')
    rawpacket+=hubitat.helper.HexUtils.integerToHexString(firmwareUpdateInfo['firmwareTarget'],1).padLeft(2, '0')
    if (!abort) {
        sendToDevice(rawpacket)
    }
}

void zwaveEvent(hubitat.zwave.commands.firmwareupdatemdv3.FirmwareUpdateMdRequestReport cmd) {
    unschedule('wakeUp')
    if (cmd.status < 255) {
        log.warn firmwareUpdateMdRequestStatus[cmd.status as Integer]
        updateProgress(firmwareUpdateMdRequestStatus[cmd.status])
        memoryCleanup()
    } else {
        log.info firmwareUpdateMdRequestStatus[cmd.status as Integer]
        updateProgress("Device Accepted Request.. Starting upload.. ")
        sendEvent(name: "firmwareUploadPercent", value: "0%")
    }
}

void abortProcess() {
    if (locked) {
        if (lockedBy==device.getDeviceNetworkId()) {
            abort=true
            sendEvent(name: "firmwareUpdateProgress", value:"ABORTED")
            memoryCleanup()
        } else {
            crossDeviceLock()
        }
    } else {
        abort=true
        sendEvent(name: "firmwareUpdateProgress", value:"ABORTED")
        memoryCleanup()
    }
}

void memoryCleanup() {
    // get rid of stuff we are storing in memory
    unschedule()
    byteBuffer=[]
    decompressedBytes=[]
    firmwareTargets=[:]
    firmwareUpdateInfo=[:]
    firmwareUpdateMdVersion=0
    firmwareMdInfo=[:]
    otzHeader=[:]
    firmwareDescriptor=[:]
    theFirmwareUpdateUrl=""
    locked=false
    lockedBy=""
    lockedByName=""
    crossDeviceLock()
}

void zwaveEvent(hubitat.zwave.commands.firmwareupdatemdv3.FirmwareMdReport cmd) {
    unschedule('wakeUp')
    unschedule('firmwareStore')
    updateProgress("got device current metadata")
    if (debugEnable) log.debug "FirmwareMDReport: ${cmd}"
    firmwareTargets[cmd.firmwareId] = 0
    if (theFirmwareTarget==0) firmwareDescriptor['firmwareId']=cmd.firmwareId
    int target=1
    if (cmd.firmwareIds.size() > 0) {
        cmd.firmwareIds.each {
            firmwareTargets[it]=target
            if (target==theFirmwareTarget) {
                firmwareDescriptor['firmwareId']=it
            }
            target=target+1
        }
    }
    firmwareMdInfo['checksum']=cmd.checksum
    firmwareMdInfo['manufacturerId']=cmd.manufacturerId
    firmwareMdInfo['targets']=cmd.numberOfTargets
    if (cmd.maxFragmentSize==null) {
        firmwareMdInfo['maxFragmentSize']=40
    } else {
        firmwareMdInfo['maxFragmentSize']=cmd.maxFragmentSize
    }
    sendEvent(name: "firmwareFragmentSize", value: firmwareMdInfo['maxFragmentSize'])
    if (!abort) runIn(2, "firmwareStore")
}

void updateProgress(message) {
    sendEvent(name: "firmwareUpdateProgress", value: message)
}

void wakeUp() {
    updateProgress("Please wake up your sleepy device")
}

void parseOtzHeader() {
    otzHeader['compressedLength']=((byteBuffer[1] & 0xff) << 24) | ((byteBuffer[2] & 0xff) << 16) | ((byteBuffer[3] & 0xff) << 8) | ((byteBuffer[4] & 0xff) << 0)as int
    otzHeader['crc16compressed']=((byteBuffer[5] & 0xff) << 8) | ((byteBuffer[6] & 0xff)  << 0) as int
    otzHeader['unCompressedCrc16']=((byteBuffer[7] & 0xff) << 8) | ((byteBuffer[8] & 0xff) << 0) as int
    otzHeader['scramblingKey']=""
    byteBuffer.subList(9,24).each { otzHeader['scramblingKey']+=Integer.toHexString(it.intValue() & 0xff) }
    otzHeader['firmwareDescriptorChecksum']=((byteBuffer[25] & 0xff) << 8) | ((byteBuffer[26] & 0xff) << 0) as int
    otzHeader['fastLzLevel']=(byte) ((byteBuffer[27] >>> 5) +1)
}

void decompressImage(Short compression_level) {
    int ip = 27; // start of compressed image
    int op = 0;
    long ctrl = byteBuffer[ip++] & 31;
    boolean loop = true;
    while(loop) {
        if (abort) break
        int ref = op;
        long len = ctrl >>> 5;
        long ofs = (ctrl & 31) << 8;
        if(ctrl >= 32) {
            int code;
            len--;
            ref -= ofs;
            if(len == 6) { // (len == 7-1)
                if (compression_level==1)  {
                    len += byteBuffer[ip++] & 0xff
                } else {
                    while(code==255) {
                        code = byteBuffer[ip++] & 0xff;
                        len += code;
                    }
                }
            }
            if (compression_level==1) {
                ref -= byteBuffer[ip++] & 0xff;
            } else {
                code = byteBuffer[ip++] & 0xff;
                ref -= code;
                if(code == 255 && ofs == 31 << 8) {
                    ofs = (byteBuffer[ip++] & 0xff) << 8;
                    ofs += byteBuffer[ip++] & 0xff;
                    ref = (int) (op-ofs-8191);
                }
            }
            if(ref-1 < 0) throw new Exception("(ref-1 < 0)");
            if(ip < byteBuffer.size()) ctrl = byteBuffer[ip++] & 0xff; else loop = false;
            if(ref == op) {
                byte b = decompressedBytes[ref-1];
                decompressedBytes[op++] = b;
                decompressedBytes[op++] = b;
                decompressedBytes[op++] = b;
                for(;len != 0; --len) decompressedBytes[op++] = b;
            } else {
                ref--;
                decompressedBytes[op++] = decompressedBytes[ref++];
                decompressedBytes[op++] = decompressedBytes[ref++];
                decompressedBytes[op++] = decompressedBytes[ref++];
                for(;len != 0; --len) decompressedBytes[op++] = decompressedBytes[ref++];
            }
        } else {
            ctrl++;
            if(ip+ctrl > byteBuffer.size()) throw new Exception("(ip+ctrl > in.length)");
            decompressedBytes[op++] = byteBuffer[ip++];
            for(--ctrl; ctrl != 0; ctrl--) decompressedBytes[op++] = byteBuffer[ip++];
            loop = ip < byteBuffer.size() ? true : false;
            if(loop) ctrl = byteBuffer[ip++] & 0xff;
        }
    }
}

Boolean isByteBufferHex() {
    byte[] hexHeader = new byte[9]
    ByteArrayInputStream inputBytes = new ByteArrayInputStream(newByteBuffer.toByteArray())
    Integer bytesRead = inputBytes.read(hexHeader, 0, 9)
    String hexString = ""
    hexHeader.eachByte {
        hexString += (char) (it & 0xFF)
    }
    log.debug hexString
    Boolean retVal = hexString.matches(/(?:\:)([0-9A-F]{2})([0-9A-F]{4})([0-9A-F]{2})/)
    log.debug "${retVal}"
    return retVal
}

void parseFirmwareImage() {
    if (abort) return
    httpGet([uri: theFirmwareUpdateUrl, textParser: true]) { resp ->
        //log.info "download size: ${resp.data.text.length()}"
        //log.debug "${resp.contentType}"
        String hexString
        if (resp.data instanceof java.io.ByteArrayInputStream) {
            Integer availableBytes = resp.data.available()
            log.info "${availableBytes} bytes"
            byte[] byteBuff = new byte[availableBytes]
            Integer bytesRead = resp.data.read(byteBuff, 0, availableBytes)
            newByteBuffer.write(byteBuff, 0, availableBytes)
            log.info "byte buffer size: ${newByteBuffer.size()}"
            log.info "${bytesRead} bytes read into memory"
            if (!isByteBufferHex()) {
                return
            }
            ByteArrayInputStream inputStream = new ByteArrayInputStream(newByteBuffer.toByteArray())
            hexString = inputStream.text
            newByteBuffer.reset()
        } else if (resp.data instanceof java.io.StringReader) {
            String hexValidate = ""
            char[] hexHeader = new char[9]
            Integer bytesRead = resp.data.read(hexHeader, 0, 9)
            hexHeader.each {
                hexValidate += it
            }
            log.debug hexValidate
            if (hexValidate.matches(/(?:\:)([0-9A-F]{2})([0-9A-F]{4})([0-9A-F]{2})/)) {
                log.debug "get hex from StringReader"
                hexString = resp.data.text
            }
        } else {
            log.warn "Unknown data received from http ${resp.data.class.name}"
        }
        if (hexString) {
            log.debug "Buffer is hex processing hex..."
            String md5 = MessageDigest.getInstance("MD5").digest(hexString.bytes).encodeHex().toString()
            log.info "firmware image has md5 sum $md5"
            if (expectedMd5Sum) {
                log.debug("comparing firmware image to expected md5 sum $expectedMd5Sum")
                if (md5 != expectedMd5Sum) {
                    log.error "Firmware download corrupted. Aborting."
                    abort = true
                    sendEvent(name: "firmwareUpdateProgress", description: "ABORTED: The downloaded firmware didn't match the expected md5 checksum. Try again?")
                    throw new GroovyRuntimeException("Firmware checksum mismatch")
                }
            }
            int byteCursor = 0
            int byteCursorPad = 0
            updateProgress("Parsing firmware...")
            if (debugEnable) log.debug "packing all the bytes..."
            (hexString =~ /(?m)(?:\:)([0-9A-F]{2})([0-9A-F]{4})([0-9A-F]{2})((?:[0-9A-F]{2})*)([0-9A-F]{2})/).each { line ->
                int numberOfBytes = hexStrToUnsignedInt(line[1])
                int startByte = hexStrToUnsignedInt(line[2])
                int rowType = hexStrToUnsignedInt(line[3])
                String data = line[4]
                String checksum = line[5]
                switch (rowType) {
                    case 0: // data row
                        int newByteCursor = ((byteCursorPad & 0xFFFF) << 16) | (startByte & 0xFFFF)
                        if (byteCursor < newByteCursor) {
                            Integer bytesToPad = newByteCursor - byteCursor
                            byte[] padBytes = new byte[bytesToPad - 1]
                            newByteBuffer.write(padBytes)
                            log.debug "Padded ${bytesToPad} bytes of data"
                            byteCursor=newByteCursor
                        }
                        byte[] byteChunk = hubitat.helper.HexUtils.hexStringToByteArray(data)
//                            log.debug  "ByteChunk: ${byteChunk}, ${byteCursor}, ${byteChunk.size()}"
                        newByteBuffer.write(byteChunk)
//                            log.debug "${newByteBuffer.size()}"
                        byteCursor = byteCursor + byteChunk.size()
                        break
                    case 1: // end of file
                        // nothing to do here we are at the end
                        if (debugEnable) log.debug "Parser: EOF"
                        break
                    case 2: // extended segment Address
                        byteCursorPad = hexStrToUnsignedInt(data)
                        if (debugEnable) log.debug "Parser: 16 bit shift: 0x${byteCursorPad}"
                        break
                    case 4: // extended linear adddress
                        byteCursorPad = hexStrToUnsignedInt(data)
                        if (debugEnable) log.debug "Parser: 16 bit shift: 0x${byteCursorPad}"
                        break
                    case 5: // start linear address
                        byteCursorPad = hexStrToUnsignedInt(data)
                        if (debugEnable) log.debug "Parser: 16 bit shift: 0x${byteCursorPad}"
                        break
                }
            }
            if (abort) return
            log.info "Sorted all the bytes. cleaning up some memory..."
            log.debug "Bytes read: ${byteCursor}"
            log.info "firmware total bytes: ${newByteBuffer.size()}"
        }
    }

    thefirmwareUpdateUrl = ""
}

void parseFirmwareDescriptor(List<Byte> descriptorBytes) {
    if (abort) return
    firmwareDescriptor['wFirmWareCommonSize']=((descriptorBytes[0] & 0xff) << 8) | (descriptorBytes[1] & 0xff)
    firmwareDescriptor['wFirmWareBank1Size']=((descriptorBytes[2] & 0xff) << 8) | (descriptorBytes[3] & 0xff)
    firmwareDescriptor['wFirmWareBank2Size']=((descriptorBytes[4] & 0xff) << 8) | (descriptorBytes[5] & 0xff)
    firmwareDescriptor['wFirmWareBank3Size']=((descriptorBytes[6] & 0xff) << 8) | (descriptorBytes[7] & 0xff)
    firmwareDescriptor['manufacturerId']=((descriptorBytes[8] & 0xff) << 8) | (descriptorBytes[9] & 0xff)
    firmwareDescriptor['firmwareId']=((descriptorBytes[10] & 0xff) << 8) | (descriptorBytes[11] & 0xff)
    firmwareDescriptor['checksum']=((descriptorBytes[12] & 0xff) << 8) | (descriptorBytes[13] & 0xff)
}

void padHexBuffer() {
    // hex files need to pad 0xFF
    for (int i=0; i<byteBuffer.size(); i++) {
        if (abort) break
        if (byteBuffer[i]==null) byteBuffer[i]=0xFF
    }
}

void firmwareStore() {
    if (abort) return
    updateProgress("Downloading firmware...")
    parseFirmwareImage()
    theFirmwareUpdateUrl=""
    //log.debug "new byte buffer size: ${newByteBuffer.size()}"
    log.info "byte buffer size: ${newByteBuffer.size()}"
    if (newByteBuffer.size()>0) {
        byte[] byteBuff = newByteBuffer.toByteArray()
        newByteBuffer.reset()
        log.debug "loading ${byteBuff.size()} bytes into list"
        for (int i in 0..(byteBuff.size()-1)) {
            byteBuffer[i] = byteBuff[i]
        }
        log.debug "done loading list"
        log.debug "calculating crc"
        newByteBuffer.write(byteBuff)
        int crc = byteBufferCrc()
        log.debug "done calculating crc: ${Integer.toHexString(crc)}"
        firmwareUpdateInfo['checksum'] = crc
        sendEvent(name: "firmwareTarget", value: theFirmwareTarget)
        updateProgress("Requesting device start...")
        String rawpacket="7A03" // FrmUpdMd FrmUpdReqGet

        rawpacket+=hubitat.helper.HexUtils.integerToHexString(firmwareMdInfo['manufacturerId'],2).padLeft(4,'0')
        rawpacket+=hubitat.helper.HexUtils.integerToHexString(firmwareDescriptor['firmwareId'],2).padLeft(4,'0')
        rawpacket+=hubitat.helper.HexUtils.integerToHexString(firmwareUpdateInfo['checksum'],2).padLeft(4, '0')
        if (firmwareUpdateMdVersion>2) {
            rawpacket+=hubitat.helper.HexUtils.integerToHexString(theFirmwareTarget,1).padLeft(2,'0')
            rawpacket+=hubitat.helper.HexUtils.integerToHexString(firmwareMdInfo['maxFragmentSize'],2).padLeft(4,'0')
        }
        if (firmwareUpdateMdVersion>3) rawpacket+="00" // activate firmware
        if (debugEnable) log.debug rawpacket
        if (!abort) {
            runIn(timeout,'wakeUp')
            sendToDevice(rawpacket)
        }

    }
/*    if (byteBuffer[0]==(byte)0x80) {
        firmwareUpdateInfo['imageType']="otz"
        log.info "got otz compressed image reading compression header"
        parseOtzHeader()
        if (debugEnable) log.debug "OTZ Headers: ${otzHeader}"
        updateProgress("Decompressing firmware...")
        decompressImage(otzHeader['fastLzLevel'])
        if (abort) return
        int firmwareDescriptorOffset = ((decompressedBytes[8] & 0xff) << 8) | (decompressedBytes[9] & 0xff)
        firmwareDescriptorOffset -= 0x1800 // bootloader
        parseFirmwareDescriptor(decompressedBytes.subList(firmwareDescriptorOffset, firmwareDescriptorOffset+14))
        binding.variables.remove 'decompressedBytes'
    } else {
        // not otz
        firmwareUpdateInfo['imageType']="hex"
        updateProgress("Padding hex bytes...")
        padHexBuffer()
        if (abort) return
        int firmwareDescriptorOffset = ((byteBuffer[0x1808] & 0xff) << 8) | (byteBuffer[0x1809] & 0xff)
        parseFirmwareDescriptor(byteBuffer.subList(firmwareDescriptorOffset, firmwareDescriptorOffset+14))
    }
    updateProgress("Calculating CRC...")
    if (abort) return
    log.info "calculating crc..."
    firmwareUpdateInfo['checksum']=byteBufferCrc()
    if (debugEnable) log.debug "Firmware Descriptor: ${firmwareDescriptor} "
    sendEvent(name: "manufacturerId", value: hubitat.helper.HexUtils.integerToHexString(firmwareDescriptor['manufacturerId'],2).padLeft(4,'0'))
    sendEvent(name: "firmwareId", value: hubitat.helper.HexUtils.integerToHexString(firmwareDescriptor['firmwareId'],2).padLeft(4,'0'))
    if (firmwareTargets[firmwareDescriptor['firmwareId']] != null) {
        sendEvent(name: "firmwareTarget", value: firmwareTargets[firmwareDescriptor['firmwareId']])
        updateProgress("Requesting device start...")
        String rawpacket="7A03" // FrmUpdMd FrmUpdReqGet
        rawpacket+=hubitat.helper.HexUtils.integerToHexString(firmwareDescriptor['manufacturerId'],2).padLeft(4,'0')
        rawpacket+=hubitat.helper.HexUtils.integerToHexString(firmwareDescriptor['firmwareId'],2).padLeft(4,'0')
        rawpacket+=hubitat.helper.HexUtils.integerToHexString(firmwareUpdateInfo['checksum'],2).padLeft(4, '0')
        if (firmwareUpdateMdVersion>2) {
            rawpacket+=hubitat.helper.HexUtils.integerToHexString(firmwareTargets[firmwareDescriptor['firmwareId']],1).padLeft(2,'0')
            rawpacket+=hubitat.helper.HexUtils.integerToHexString(firmwareMdInfo['maxFragmentSize'],2).padLeft(4,'0')
        }
        if (firmwareUpdateMdVersion>3) rawpacket+="00" // activate firmware
        if (debugEnable) log.debug rawpacket
        if (!abort) {
            runIn(timeout,'wakeUp')
            sendToDevice(rawpacket)
        }
    } else {
        unschedule('wakeUp')
        log.warn "Update failed to find a valid target"
        updateProgress("Failed to find matching firmware")
        memoryCleanup()
    }*/
}

void updateFirmware(String firmwareUrl, String firmwareTarget, String md5Sum = null) {
    if (!locked) {
        abort=false
        locked=true
        expectedMd5Sum = md5Sum
        lockedBy=device.getDeviceNetworkId()
        lockedByName=device.getDisplayName()
        newByteBuffer.reset()
        byteBuffer=[]
        theFirmwareUpdateUrl=firmwareUrl
        theFirmwareTarget=Integer.parseInt(firmwareTarget)

        sendEvent(name: "currentFirmwareVersion", value:"")
        sendEvent(name: "firmwareUploadPercent", value:"")
        sendEvent(name: "manufacturerId", value:"")
        sendEvent(name: "firmwareTarget", value:null)
        updateProgress("Starting.. Getting current version")
        runIn(timeout, 'wakeUp')
        sendToDevice(zwave.versionV1.versionCommandClassGet(requestedCommandClass:0x7A))
    } else {
        crossDeviceLock()
        log.warn "Update process is currently running"
    }
}

private Integer byteBufferCrc() {
    int polynomial = 0x1021
    int crc=0x1D0F
    for (byte b : newByteBuffer.toByteArray()) {
        for (int i=0; i < 8; i++) {
            if (abort) break
            boolean bit = ((b >> (7-i) & 1) == 1)
            boolean c15 = ((crc >> 15    & 1) == 1)
            crc <<= 1
            if (c15 ^ bit) crc ^= polynomial
        }
    }
    crc &= 0xffff
    if (!abort) return crc
}

private Integer zwaveCrc16(List<Byte> bytes) {
    int crc = 0x1D0F
    int polynomial = 0x1021
    for (byte b : bytes) {
        for (int i=0; i < 8; i++) {
            if (abort) return
            boolean bit = ((b >> (7-i) & 1) == 1)
            boolean c15 = ((crc >> 15    & 1) == 1)
            crc <<= 1
            if (c15 ^ bit) crc ^= polynomial
        }
    }
    crc &= 0xffff
    if (!abort) return crc
}

void getVersionReport(){
    if (!locked) {
        sendToDevice(zwave.versionV1.versionGet())
    } else {
        crossDeviceLock()
    }
}

void installed(){}

void configure() {
    timeout = sleepyTimeout ?: timeout
}

void zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(CMD_CLASS_VERS)
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand)
    }
}

void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd) {
    hubitat.zwave.Command encapsulatedCommand = cmd.encapsulatedCommand(CMD_CLASS_VERS)
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand)
    }
    sendToDevice(new hubitat.zwave.commands.supervisionv1.SupervisionReport(sessionID: cmd.sessionID, reserved: 0, moreStatusUpdates: false, status: 0xFF, duration: 0))
}

void sendToDevice(List<hubitat.zwave.Command> cmds) {
    sendHubCommand(new hubitat.device.HubMultiAction(commands(cmds), hubitat.device.Protocol.ZWAVE))
}

void sendToDevice(hubitat.zwave.Command cmd) {
    sendHubCommand(new hubitat.device.HubAction(zwaveSecureEncap(cmd), hubitat.device.Protocol.ZWAVE))
}

void sendToDevice(String cmd) {
    sendHubCommand(new hubitat.device.HubAction(zwaveSecureEncap(cmd), hubitat.device.Protocol.ZWAVE))
}

List<String> commands(List<hubitat.zwave.Command> cmds, Long delay=200) {
    return delayBetween(cmds.collect{ zwaveSecureEncap(it) }, delay)
}
