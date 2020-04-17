// v0.03b
import groovy.transform.Field

metadata {
    definition (name: "Z-Wave Firmware Updater",namespace: "djdizzyd", author: "Bryan Copeland") {
        attribute "currentFirmwareVersion", "string"
        attribute "firmwareUpdateProgress", "string"
        attribute "firmwareUploadPercent", "string"
        attribute "manufacturerId", "string"
        attribute "firmwareId", "string"
        attribute "firmwareTarget", "number"
        attribute "firmwareFragmentSize", "number"
        attribute "lockedBy", "string"

        command "clearLock"
        command "getVersionReport"
        command "abortProcess"
        command "updateFirmware", [[name:"firmwareUrl", type: "STRING", description:"Firmware URL"]]
    }
    preferences {
        input name: "debugEnable", type: "bool", description: "", title: "Enable Debug Logging", defaultVaule: false
    }
}
@Field static Map CMD_CLASS_VERS=[0x85:1,0x86:1,0x7A:3]
@Field static String lockedBy="none"
@Field static String lockedByName=""
@Field static List<Byte> byteBuffer=[]
@Field static List<Byte> decompressedBytes=[]
@Field static Map firmwareTargets=[:]
@Field static Map firmwareUpdateInfo=[:]
@Field static Integer firmwareUpdateMdVersion=0
@Field static Map firmwareMdInfo=[:]
@Field static Map otzHeader=[:]
@Field static Map firmwareDescriptor=[:]
@Field static String theFirmwareUpdateUrl
@Field static Boolean locked=false
@Field static Boolean abort=false

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
            runIn(5,'wakeUp')
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
            runIn(5,'wakeUp')
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
    if (lastByte >= byteBuffer.size()-1) {
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
        int crc = zwaveCrc16(bytes)
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
                runIn(5,'wakeUp')
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
    updateProgress("got device current metadata")
    if (debugEnable) log.debug "FirmwareMDReport: ${cmd}"
    if (debugEnable) log.debug "firmwareMdReport: checksum ${cmd.checksum} firmwareId: ${cmd.firmwareId} manufacturerId: ${cmd.manufacturerId} maxFragmentSize: ${cmd.maxFragmentSize} firmwareTargets: ${cmd.numberOfTargets}"
    firmwareTargets[cmd.firmwareId] = 0
    int target=1
    if (cmd.firmwareIds.size() > 0) {
        cmd.firmwareIds.each {
            firmwareTargets[it]=target
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
    if (!abort) runIn(1, "firmwareStore")
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

void parseFirmwareImage() {
    if (abort) return
    httpGet([uri:theFirmwareUpdateUrl, textParser:true]){ resp->
        //log.info "download size: ${resp.data.text.length()}"
        int byteCursor=0
        int byteCursorPad=0
        updateProgress("Parsing firmware...")
        if (debugEnable) log.debug "packing all the bytes..."
        (resp.data.text =~ /(?m)(?:\:)([0-9A-F]{2})([0-9A-F]{4})([0-9A-F]{2})((?:[0-9A-F]{2})*)([0-9A-F]{2})/).each { line ->
            int numberOfBytes = hexStrToUnsignedInt(line[1])
            int startByte = hexStrToUnsignedInt(line[2])
            int rowType = hexStrToUnsignedInt(line[3])
            String data = line[4]
            String checksum = line[5]
            switch (rowType) {
                case 0: // data row
                    byteCursor = ((byteCursorPad & 0xFFFF) << 16) | (startByte & 0xFFFF)
                    hubitat.helper.HexUtils.hexStringToByteArray(data).each { byteItem ->
                        byteBuffer[byteCursor] = byteItem
                        byteCursor = byteCursor + 1
                    }
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
        log.info "firmware total bytes: " + byteBuffer.size()
    }
    thefirmwareUpdateUrl=""
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
    if (byteBuffer[0]==(byte)0x80) {
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
            runIn(5,'wakeUp')
            sendToDevice(rawpacket)
        }
    } else {
        unschedule('wakeUp')
        log.warn "Update failed to find a valid target"
        updateProgress("Failed to find matching firmware")
        memoryCleanup()
    }
}

void updateFirmware(String firmwareUrl) {
    if (!locked) {
        abort=false
        locked=true
        lockedBy=device.getDeviceNetworkId()
        lockedByName=device.getDisplayName()
        byteBuffer=[]
        theFirmwareUpdateUrl=firmwareUrl
        sendEvent(name: "currentFirmwareVersion", value:"")
        sendEvent(name: "firmwareUploadPercent", value:"")
        sendEvent(name: "manufacturerId", value:"")
        sendEvent(name: "firmwareTarget", value:null)
        updateProgress("Starting.. Getting current version")
        runIn(5, 'wakeUp')
        sendToDevice(zwave.versionV1.versionCommandClassGet(requestedCommandClass:0x7A))
    } else {
        crossDeviceLock()
        log.warn "Update process is currently running"
    }
}

private Integer byteBufferCrc() {
    int polynomial = 0x1021
    int crc=0x1D0F
    for (byte b : byteBuffer) {
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

void configure() {}

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
    sendHubCommand(new hubitat.device.HubAction(secureCommand(cmd), hubitat.device.Protocol.ZWAVE))
}

void sendToDevice(String cmd) {
    sendHubCommand(new hubitat.device.HubAction(secureCommand(cmd), hubitat.device.Protocol.ZWAVE))
}

List<String> commands(List<hubitat.zwave.Command> cmds, Long delay=200) {
    return delayBetween(cmds.collect{ secureCommand(it) }, delay)
}

String secureCommand(hubitat.zwave.Command cmd) {
    secureCommand(cmd.format())
}

String secureCommand(String cmd) {
    String encap=""
    if (getDataValue("zwaveSecurePairingComplete") != "true") {
        return cmd
    } else {
        encap = "988100"
    }
    return "${encap}${cmd}"
}