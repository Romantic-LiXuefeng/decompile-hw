package com.android.internal.telephony.cdma;

import android.content.res.Resources;
import android.os.Parcel;
import android.os.SystemProperties;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.SmsCbLocation;
import android.telephony.SmsCbMessage;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaSmsCbProgramData;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.telephony.GsmAlphabet.TextEncodingDetails;
import com.android.internal.telephony.HwModemCapability;
import com.android.internal.telephony.HwTelephonyFactory;
import com.android.internal.telephony.Sms7BitEncodingTranslator;
import com.android.internal.telephony.SmsConstants.MessageClass;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.SmsHeader.PortAddrs;
import com.android.internal.telephony.SmsMessageBase;
import com.android.internal.telephony.SmsMessageBase.SubmitPduBase;
import com.android.internal.telephony.cdma.sms.BearerData;
import com.android.internal.telephony.cdma.sms.CdmaSmsAddress;
import com.android.internal.telephony.cdma.sms.CdmaSmsSubaddress;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import com.android.internal.telephony.cdma.sms.UserData;
import com.android.internal.util.BitwiseInputStream;
import com.android.internal.util.HexDump;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;

public class SmsMessage extends SmsMessageBase {
    private static final byte BEARER_DATA = (byte) 8;
    private static final byte BEARER_REPLY_OPTION = (byte) 6;
    private static final byte CAUSE_CODES = (byte) 7;
    private static final String CT_SMS_SEND_CENTER = "10659401";
    private static final byte DESTINATION_ADDRESS = (byte) 4;
    private static final byte DESTINATION_SUB_ADDRESS = (byte) 5;
    private static final String LOGGABLE_TAG = "CDMA:SMS";
    static final String LOG_TAG = "SmsMessage";
    private static final byte ORIGINATING_ADDRESS = (byte) 2;
    private static final byte ORIGINATING_SUB_ADDRESS = (byte) 3;
    private static boolean PLUS_TRANFER_IN_AP = (!HwModemCapability.isCapabilitySupport(2));
    private static final int RETURN_ACK = 1;
    private static final int RETURN_NO_ACK = 0;
    private static final byte SERVICE_CATEGORY = (byte) 1;
    private static final byte TELESERVICE_IDENTIFIER = (byte) 0;
    private static final boolean VDBG = false;
    private BearerData mBearerData;
    private SmsEnvelope mEnvelope;
    private int status;

    public static class SubmitPdu extends SubmitPduBase {
    }

    public static SmsMessage createFromPdu(byte[] pdu) {
        SmsMessage msg = new SmsMessage();
        try {
            msg.parsePdu(pdu);
            return msg;
        } catch (RuntimeException ex) {
            Rlog.e(LOG_TAG, "SMS PDU parsing failed: ", ex);
            return null;
        } catch (OutOfMemoryError e) {
            Log.e(LOG_TAG, "SMS PDU parsing failed with out of memory: ", e);
            return null;
        }
    }

    public static SmsMessage newFromParcel(Parcel p) {
        int index;
        SmsMessage msg = new SmsMessage();
        SmsEnvelope env = new SmsEnvelope();
        CdmaSmsAddress addr = new CdmaSmsAddress();
        CdmaSmsSubaddress subaddr = new CdmaSmsSubaddress();
        env.teleService = p.readInt();
        if (p.readByte() != (byte) 0) {
            env.messageType = 1;
        } else if (env.teleService == 0) {
            env.messageType = 2;
        } else {
            env.messageType = 0;
        }
        env.serviceCategory = p.readInt();
        int addressDigitMode = p.readInt();
        addr.digitMode = (byte) (addressDigitMode & 255);
        addr.numberMode = (byte) (p.readInt() & 255);
        addr.ton = p.readInt();
        addr.numberPlan = (byte) (p.readInt() & 255);
        byte count = p.readByte();
        addr.numberOfDigits = count;
        byte[] data = new byte[count];
        for (byte index2 = (byte) 0; index2 < count; index2++) {
            data[index2] = p.readByte();
            if (addressDigitMode == 0) {
                data[index2] = msg.convertDtmfToAscii(data[index2]);
            }
        }
        addr.origBytes = data;
        if (PLUS_TRANFER_IN_AP) {
            String number = HwCustPlusAndIddNddConvertUtils.replaceIddNddWithPlusForSms(new String(addr.origBytes, Charset.defaultCharset()));
            if (addr.ton == 1 && number != null && number.length() > 0 && number.charAt(0) != '+') {
                Rlog.d(LOG_TAG, "newFromParcel ton == SmsAddress.TON_INTERNATIONAL");
                number = HwCustPlusAndIddNddConvertUtils.PLUS_PREFIX + number;
            }
            if (number != null) {
                addr.origBytes = number.getBytes(Charset.defaultCharset());
            } else {
                addr.origBytes = data;
            }
            addr.numberOfDigits = addr.origBytes.length;
        }
        subaddr.type = p.readInt();
        subaddr.odd = p.readByte();
        int count2 = p.readByte();
        if (count2 < 0) {
            count2 = 0;
        }
        data = new byte[count2];
        for (index = 0; index < count2; index++) {
            data[index] = p.readByte();
        }
        subaddr.origBytes = data;
        int countInt = p.readInt();
        if (countInt < 0) {
            countInt = 0;
        }
        data = new byte[countInt];
        for (index = 0; index < countInt; index++) {
            data[index] = p.readByte();
        }
        env.bearerData = data;
        env.origAddress = addr;
        env.origSubaddress = subaddr;
        msg.mOriginatingAddress = addr;
        msg.mEnvelope = env;
        msg.createPdu();
        return msg;
    }

    public static SmsMessage createFromEfRecord(int index, byte[] data) {
        try {
            SmsMessage msg = new SmsMessage();
            msg.mIndexOnIcc = index;
            if ((data[0] & 1) == 0) {
                Rlog.w(LOG_TAG, "SMS parsing failed: Trying to parse a free record");
                return null;
            }
            msg.mStatusOnIcc = data[0] & 7;
            int size = data[1] & 255;
            byte[] pdu = new byte[size];
            System.arraycopy(data, 2, pdu, 0, size);
            HwTelephonyFactory.getHwInnerSmsManager().parseRUIMPdu(msg, pdu);
            return msg;
        } catch (RuntimeException ex) {
            Rlog.e(LOG_TAG, "SMS PDU parsing failed: ", ex);
            return null;
        }
    }

    public static int getTPLayerLengthForPDU(String pdu) {
        Rlog.w(LOG_TAG, "getTPLayerLengthForPDU: is not supported in CDMA mode.");
        return 0;
    }

    public static SubmitPdu getSubmitPdu(String scAddr, String destAddr, String message, boolean statusReportRequested, SmsHeader smsHeader) {
        if (message == null || destAddr == null) {
            return null;
        }
        UserData uData = new UserData();
        uData.payloadStr = message;
        uData.userDataHeader = smsHeader;
        return privateGetSubmitPdu(destAddr, statusReportRequested, uData);
    }

    public static SubmitPdu getSubmitPdu(String scAddr, String destAddr, int destPort, byte[] data, boolean statusReportRequested) {
        PortAddrs portAddrs = new PortAddrs();
        portAddrs.destPort = destPort;
        portAddrs.origPort = 0;
        portAddrs.areEightBits = false;
        SmsHeader smsHeader = new SmsHeader();
        smsHeader.portAddrs = portAddrs;
        UserData uData = new UserData();
        if (CT_SMS_SEND_CENTER.equals(destAddr)) {
            uData.userDataHeader = null;
        } else {
            uData.userDataHeader = smsHeader;
        }
        uData.msgEncoding = 0;
        uData.msgEncodingSet = true;
        uData.payload = data;
        return privateGetSubmitPdu(destAddr, statusReportRequested, uData);
    }

    public static SubmitPdu getSubmitPdu(String destAddr, UserData userData, boolean statusReportRequested) {
        return privateGetSubmitPdu(destAddr, statusReportRequested, userData);
    }

    public int getProtocolIdentifier() {
        Rlog.w(LOG_TAG, "getProtocolIdentifier: is not supported in CDMA mode.");
        return 0;
    }

    public boolean isReplace() {
        Rlog.w(LOG_TAG, "isReplace: is not supported in CDMA mode.");
        return false;
    }

    public boolean isCphsMwiMessage() {
        Rlog.w(LOG_TAG, "isCphsMwiMessage: is not supported in CDMA mode.");
        return false;
    }

    public boolean isMWIClearMessage() {
        return this.mBearerData != null && this.mBearerData.numberOfMessages == 0;
    }

    public boolean isMWISetMessage() {
        return this.mBearerData != null && this.mBearerData.numberOfMessages > 0;
    }

    public boolean isMwiDontStore() {
        if (this.mBearerData == null || this.mBearerData.numberOfMessages <= 0 || this.mBearerData.userData != null) {
            return false;
        }
        return true;
    }

    public int getStatus() {
        return this.status << 16;
    }

    public boolean isStatusReportMessage() {
        return this.mBearerData.messageType == 4;
    }

    public boolean isReplyPathPresent() {
        Rlog.w(LOG_TAG, "isReplyPathPresent: is not supported in CDMA mode.");
        return false;
    }

    public static TextEncodingDetails calculateLength(CharSequence messageBody, boolean use7bitOnly, boolean isEntireMsg) {
        CharSequence newMsgBody = null;
        if (Resources.getSystem().getBoolean(17957019)) {
            newMsgBody = Sms7BitEncodingTranslator.translate(messageBody);
        }
        if (TextUtils.isEmpty(newMsgBody)) {
            newMsgBody = messageBody;
        }
        return BearerData.calcTextEncodingDetails(newMsgBody, use7bitOnly, isEntireMsg);
    }

    public static TextEncodingDetails calculateLength(CharSequence messageBody, boolean use7bitOnly) {
        return calculateLength(messageBody, use7bitOnly, false);
    }

    public int getTeleService() {
        return this.mEnvelope.teleService;
    }

    public int getMessageType() {
        if (this.mEnvelope.serviceCategory != 0) {
            return 1;
        }
        return 0;
    }

    private void parsePdu(byte[] pdu) {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(pdu));
        SmsEnvelope env = new SmsEnvelope();
        CdmaSmsAddress addr = new CdmaSmsAddress();
        try {
            env.messageType = dis.readInt();
            env.teleService = dis.readInt();
            env.serviceCategory = dis.readInt();
            addr.digitMode = dis.readByte();
            addr.numberMode = dis.readByte();
            addr.ton = dis.readByte();
            addr.numberPlan = dis.readByte();
            int length = dis.readUnsignedByte();
            addr.numberOfDigits = length;
            if (length > pdu.length) {
                throw new RuntimeException("createFromPdu: Invalid pdu, addr.numberOfDigits " + length + " > pdu len " + pdu.length);
            }
            addr.origBytes = new byte[length];
            dis.read(addr.origBytes, 0, length);
            env.bearerReply = dis.readInt();
            env.replySeqNo = dis.readByte();
            env.errorClass = dis.readByte();
            env.causeCode = dis.readByte();
            int bearerDataLength = dis.readInt();
            if (bearerDataLength > pdu.length) {
                throw new RuntimeException("createFromPdu: Invalid pdu, bearerDataLength " + bearerDataLength + " > pdu len " + pdu.length);
            }
            env.bearerData = new byte[bearerDataLength];
            dis.read(env.bearerData, 0, bearerDataLength);
            dis.close();
            this.mOriginatingAddress = addr;
            env.origAddress = addr;
            this.mEnvelope = env;
            this.mPdu = pdu;
            parseSms();
        } catch (IOException ex) {
            throw new RuntimeException("createFromPdu: conversion from byte array to object failed: " + ex, ex);
        } catch (Exception ex2) {
            Rlog.e(LOG_TAG, "createFromPdu: conversion from byte array to object failed: " + ex2);
        }
    }

    private void parsePduFromEfRecord(byte[] pdu) {
        ByteArrayInputStream bais = new ByteArrayInputStream(pdu);
        DataInputStream dis = new DataInputStream(bais);
        SmsEnvelope env = new SmsEnvelope();
        CdmaSmsAddress addr = new CdmaSmsAddress();
        CdmaSmsSubaddress subAddr = new CdmaSmsSubaddress();
        try {
            env.messageType = dis.readByte();
            while (dis.available() > 0) {
                int parameterId = dis.readByte();
                int parameterLen = dis.readUnsignedByte();
                byte[] parameterData = new byte[parameterLen];
                int index;
                switch (parameterId) {
                    case 0:
                        env.teleService = dis.readUnsignedShort();
                        Rlog.i(LOG_TAG, "teleservice = " + env.teleService);
                        break;
                    case 1:
                        env.serviceCategory = dis.readUnsignedShort();
                        break;
                    case 2:
                    case 4:
                        dis.read(parameterData, 0, parameterLen);
                        BitwiseInputStream addrBis = new BitwiseInputStream(parameterData);
                        addr.digitMode = addrBis.read(1);
                        addr.numberMode = addrBis.read(1);
                        int numberType = 0;
                        if (addr.digitMode == 1) {
                            numberType = addrBis.read(3);
                            addr.ton = numberType;
                            if (addr.numberMode == 0) {
                                addr.numberPlan = addrBis.read(4);
                            }
                        }
                        addr.numberOfDigits = addrBis.read(8);
                        byte[] data = new byte[addr.numberOfDigits];
                        if (addr.digitMode == 0) {
                            for (index = 0; index < addr.numberOfDigits; index++) {
                                data[index] = convertDtmfToAscii((byte) (addrBis.read(4) & 15));
                            }
                        } else if (addr.digitMode != 1) {
                            Rlog.e(LOG_TAG, "Incorrect Digit mode");
                        } else if (addr.numberMode == 0) {
                            for (index = 0; index < addr.numberOfDigits; index++) {
                                data[index] = (byte) (addrBis.read(8) & 255);
                            }
                        } else if (addr.numberMode != 1) {
                            Rlog.e(LOG_TAG, "Originating Addr is of incorrect type");
                        } else if (numberType == 2) {
                            Rlog.e(LOG_TAG, "TODO: Originating Addr is email id");
                        } else {
                            Rlog.e(LOG_TAG, "TODO: Originating Addr is data network address");
                        }
                        addr.origBytes = data;
                        Rlog.i(LOG_TAG, "Originating Addr=" + addr.toString());
                        break;
                    case 3:
                    case 5:
                        dis.read(parameterData, 0, parameterLen);
                        BitwiseInputStream bitwiseInputStream = new BitwiseInputStream(parameterData);
                        subAddr.type = bitwiseInputStream.read(3);
                        subAddr.odd = bitwiseInputStream.readByteArray(1)[0];
                        int subAddrLen = bitwiseInputStream.read(8);
                        byte[] subdata = new byte[subAddrLen];
                        for (index = 0; index < subAddrLen; index++) {
                            subdata[index] = convertDtmfToAscii((byte) (bitwiseInputStream.read(4) & 255));
                        }
                        subAddr.origBytes = subdata;
                        break;
                    case 6:
                        dis.read(parameterData, 0, parameterLen);
                        env.bearerReply = new BitwiseInputStream(parameterData).read(6);
                        break;
                    case 7:
                        dis.read(parameterData, 0, parameterLen);
                        BitwiseInputStream ccBis = new BitwiseInputStream(parameterData);
                        env.replySeqNo = ccBis.readByteArray(6)[0];
                        env.errorClass = ccBis.readByteArray(2)[0];
                        if (env.errorClass == (byte) 0) {
                            break;
                        }
                        env.causeCode = ccBis.readByteArray(8)[0];
                        break;
                    case 8:
                        dis.read(parameterData, 0, parameterLen);
                        env.bearerData = parameterData;
                        break;
                    default:
                        throw new Exception("unsupported parameterId (" + parameterId + ")");
                }
            }
            bais.close();
            dis.close();
        } catch (Exception ex) {
            Rlog.e(LOG_TAG, "parsePduFromEfRecord: conversion from pdu to SmsMessage failed" + ex);
        }
        this.mOriginatingAddress = addr;
        env.origAddress = addr;
        env.origSubaddress = subAddr;
        this.mEnvelope = env;
        this.mPdu = pdu;
        parseSms();
    }

    public void parseSms() {
        if (this.mEnvelope.teleService == SmsEnvelope.TELESERVICE_MWI) {
            this.mBearerData = new BearerData();
            if (this.mEnvelope.bearerData != null) {
                this.mBearerData.numberOfMessages = this.mEnvelope.bearerData[0] & 255;
            }
            return;
        }
        this.mBearerData = BearerData.decode(this.mEnvelope.bearerData);
        if (Rlog.isLoggable(LOGGABLE_TAG, 2)) {
            Rlog.d(LOG_TAG, "MT raw BearerData = '" + HexDump.toHexString(this.mEnvelope.bearerData) + "'");
            Rlog.d(LOG_TAG, "MT (decoded) BearerData = " + this.mBearerData);
        }
        this.mMessageRef = this.mBearerData.messageId;
        if (this.mBearerData.userData != null) {
            this.mUserData = this.mBearerData.userData.payload;
            this.mUserDataHeader = this.mBearerData.userData.userDataHeader;
            this.mMessageBody = this.mBearerData.userData.payloadStr;
        }
        if (!(this.mOriginatingAddress == null || this.mOriginatingAddress.origBytes == null)) {
            this.mOriginatingAddress.address = new String(this.mOriginatingAddress.origBytes);
            if (this.mOriginatingAddress.ton == 1 && this.mOriginatingAddress.address.charAt(0) != '+') {
                this.mOriginatingAddress.address = HwCustPlusAndIddNddConvertUtils.PLUS_PREFIX + this.mOriginatingAddress.address;
            }
        }
        if (this.mBearerData.msgCenterTimeStamp != null) {
            this.mScTimeMillis = this.mBearerData.msgCenterTimeStamp.toMillis(true);
        }
        if (this.mBearerData.messageType == 4) {
            if (this.mBearerData.messageStatusSet) {
                this.status = this.mBearerData.errorClass << 8;
                this.status |= this.mBearerData.messageStatus;
                HwTelephonyFactory.getHwInnerSmsManager().doubleSmsStatusCheck(this);
            } else {
                Rlog.d(LOG_TAG, "DELIVERY_ACK message without msgStatus (" + (this.mUserData == null ? "also missing" : "does have") + " userData).");
                this.status = 0;
            }
        } else if (!(this.mBearerData.messageType == 1 || this.mBearerData.messageType == 2)) {
            throw new RuntimeException("Unsupported message type: " + this.mBearerData.messageType);
        }
        if (this.mMessageBody != null) {
            parseMessageBody();
        } else if (this.mUserData != null) {
        }
    }

    public SmsCbMessage parseBroadcastSms() {
        BearerData bData = BearerData.decode(this.mEnvelope.bearerData, this.mEnvelope.serviceCategory);
        if (bData == null) {
            Rlog.w(LOG_TAG, "BearerData.decode() returned null");
            return null;
        }
        if (Rlog.isLoggable(LOGGABLE_TAG, 2)) {
            Rlog.d(LOG_TAG, "MT raw BearerData = " + HexDump.toHexString(this.mEnvelope.bearerData));
        }
        return new SmsCbMessage(2, 1, bData.messageId, new SmsCbLocation(TelephonyManager.getDefault().getNetworkOperator()), this.mEnvelope.serviceCategory, bData.getLanguage(), bData.userData.payloadStr, bData.priority, null, bData.cmasWarningInfo);
    }

    public MessageClass getMessageClass() {
        if (this.mBearerData.displayMode == 0) {
            return MessageClass.CLASS_0;
        }
        return MessageClass.UNKNOWN;
    }

    public static synchronized int getNextMessageId() {
        int msgId;
        synchronized (SmsMessage.class) {
            msgId = SystemProperties.getInt("persist.radio.cdma.msgid", 1);
            String nextMsgId = Integer.toString((msgId % 65535) + 1);
            try {
                SystemProperties.set("persist.radio.cdma.msgid", nextMsgId);
                if (Rlog.isLoggable(LOGGABLE_TAG, 2)) {
                    Rlog.d(LOG_TAG, "next persist.radio.cdma.msgid = " + nextMsgId);
                    Rlog.d(LOG_TAG, "readback gets " + SystemProperties.get("persist.radio.cdma.msgid"));
                }
            } catch (RuntimeException ex) {
                Rlog.e(LOG_TAG, "set nextMessage ID failed: " + ex);
            }
        }
        return msgId;
    }

    public static CdmaSmsAddress parseAddrForSMSMO(String destAddrStr) {
        String destAddress = processPlusCodeForSMSMO(destAddrStr);
        if (destAddress == null || destAddress.isEmpty()) {
            Rlog.e(LOG_TAG, "got null or empty address after processPlusCodeForSMSMO()!");
            return null;
        }
        CdmaSmsAddress destAddr = CdmaSmsAddress.parse(destAddress);
        if (destAddr != null) {
            return destAddr;
        }
        Rlog.e(LOG_TAG, "privateGetSubmitPdu, CdmaSmsAddress parse error.");
        return null;
    }

    public static String processPlusCodeForSMSMO(String destAddrStr) {
        if (destAddrStr == null || destAddrStr.isEmpty()) {
            Rlog.e(LOG_TAG, "destAddrStr is null or empty, just return!");
            return destAddrStr;
        } else if (destAddrStr.startsWith(HwCustPlusAndIddNddConvertUtils.PLUS_PREFIX)) {
            return HwCustPlusAndIddNddConvertUtils.replacePlusCodeWithIddNddForSms(destAddrStr);
        } else {
            Rlog.e(LOG_TAG, "there is no + code, no need to convert, just return!");
            return destAddrStr;
        }
    }

    private static SubmitPdu privateGetSubmitPdu(String destAddrStr, boolean statusReportRequested, UserData userData) {
        CdmaSmsAddress destAddr;
        if (PLUS_TRANFER_IN_AP) {
            destAddr = parseAddrForSMSMO(destAddrStr);
            if (destAddr == null) {
                Rlog.e(LOG_TAG, "privateGetSubmitPdu, CdmaSmsAddress parse error.");
                return null;
            }
        }
        destAddr = CdmaSmsAddress.parse(PhoneNumberUtils.cdmaCheckAndProcessPlusCodeForSms(destAddrStr));
        if (destAddr == null) {
            return null;
        }
        BearerData bearerData = new BearerData();
        bearerData.messageType = 2;
        bearerData.messageId = getNextMessageId();
        if (SystemProperties.getBoolean("ro.sms.roam_sms_no_dreport", false) && statusReportRequested) {
            int cdmaSubId = HwTelephonyFactory.getHwInnerSmsManager().getCdmaSub();
            boolean isRoaming = TelephonyManager.getDefault().isNetworkRoaming(cdmaSubId);
            Rlog.d(LOG_TAG, "cdmaSubId " + cdmaSubId + ", isRoaming " + isRoaming);
            if (isRoaming && userData != null) {
                statusReportRequested = false;
            }
        }
        bearerData.deliveryAckReq = statusReportRequested;
        bearerData.userAckReq = false;
        bearerData.readAckReq = false;
        bearerData.reportReq = false;
        bearerData.userData = userData;
        byte[] encodedBearerData = BearerData.encode(bearerData);
        if (Rlog.isLoggable(LOGGABLE_TAG, 2)) {
            Rlog.d(LOG_TAG, "MO (encoded) BearerData = " + bearerData);
            Rlog.d(LOG_TAG, "MO raw BearerData = '" + HexDump.toHexString(encodedBearerData) + "'");
        }
        if (encodedBearerData == null) {
            return null;
        }
        int teleservice = bearerData.hasUserDataHeader ? SmsEnvelope.TELESERVICE_WEMT : 4098;
        SmsEnvelope envelope = new SmsEnvelope();
        envelope.messageType = 0;
        envelope.teleService = teleservice;
        envelope.destAddress = destAddr;
        envelope.bearerReply = 1;
        envelope.bearerData = encodedBearerData;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(100);
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(envelope.teleService);
            dos.writeInt(0);
            dos.writeInt(0);
            dos.write(destAddr.digitMode);
            dos.write(destAddr.numberMode);
            dos.write(destAddr.ton);
            dos.write(destAddr.numberPlan);
            dos.write(destAddr.numberOfDigits);
            dos.write(destAddr.origBytes, 0, destAddr.origBytes.length);
            dos.write(0);
            dos.write(0);
            dos.write(0);
            dos.write(encodedBearerData.length);
            dos.write(encodedBearerData, 0, encodedBearerData.length);
            dos.close();
            SubmitPdu pdu = new SubmitPdu();
            pdu.encodedMessage = baos.toByteArray();
            pdu.encodedScAddress = null;
            return pdu;
        } catch (IOException ex) {
            Rlog.e(LOG_TAG, "creating SubmitPdu failed: " + ex);
            return null;
        }
    }

    private void createPdu() {
        SmsEnvelope env = this.mEnvelope;
        CdmaSmsAddress addr = env.origAddress;
        ByteArrayOutputStream baos = new ByteArrayOutputStream(100);
        DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(baos));
        try {
            dos.writeInt(env.messageType);
            dos.writeInt(env.teleService);
            dos.writeInt(env.serviceCategory);
            dos.writeByte(addr.digitMode);
            dos.writeByte(addr.numberMode);
            dos.writeByte(addr.ton);
            dos.writeByte(addr.numberPlan);
            dos.writeByte(addr.numberOfDigits);
            dos.write(addr.origBytes, 0, addr.origBytes.length);
            dos.writeInt(env.bearerReply);
            dos.writeByte(env.replySeqNo);
            dos.writeByte(env.errorClass);
            dos.writeByte(env.causeCode);
            dos.writeInt(env.bearerData.length);
            dos.write(env.bearerData, 0, env.bearerData.length);
            dos.close();
            this.mPdu = baos.toByteArray();
        } catch (IOException ex) {
            Rlog.e(LOG_TAG, "createPdu: conversion from object to byte array failed: " + ex);
        }
    }

    private byte convertDtmfToAscii(byte dtmfDigit) {
        switch (dtmfDigit) {
            case (byte) 0:
                return (byte) 68;
            case (byte) 1:
                return (byte) 49;
            case (byte) 2:
                return (byte) 50;
            case (byte) 3:
                return (byte) 51;
            case (byte) 4:
                return (byte) 52;
            case (byte) 5:
                return (byte) 53;
            case (byte) 6:
                return (byte) 54;
            case (byte) 7:
                return (byte) 55;
            case (byte) 8:
                return (byte) 56;
            case (byte) 9:
                return (byte) 57;
            case (byte) 10:
                return (byte) 48;
            case (byte) 11:
                return (byte) 42;
            case (byte) 12:
                return (byte) 35;
            case (byte) 13:
                return (byte) 65;
            case (byte) 14:
                return (byte) 66;
            case (byte) 15:
                return (byte) 67;
            default:
                return (byte) 32;
        }
    }

    public int getNumOfVoicemails() {
        return this.mBearerData.numberOfMessages;
    }

    public byte[] getIncomingSmsFingerprint() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(this.mEnvelope.serviceCategory);
        output.write(this.mEnvelope.teleService);
        output.write(this.mEnvelope.origAddress.origBytes, 0, this.mEnvelope.origAddress.origBytes.length);
        output.write(this.mEnvelope.bearerData, 0, this.mEnvelope.bearerData.length);
        output.write(this.mEnvelope.origSubaddress.origBytes, 0, this.mEnvelope.origSubaddress.origBytes.length);
        return output.toByteArray();
    }

    public ArrayList<CdmaSmsCbProgramData> getSmsCbProgramData() {
        return this.mBearerData.serviceCategoryProgramData;
    }
}
