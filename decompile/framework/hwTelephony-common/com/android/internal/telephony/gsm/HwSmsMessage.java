package com.android.internal.telephony.gsm;

import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.text.format.Time;
import com.android.internal.telephony.EncodeException;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.gsm.SmsMessage.PduParser;
import com.android.internal.telephony.gsm.SmsMessage.SubmitPdu;
import com.android.internal.util.BitwiseOutputStream;
import com.android.internal.util.BitwiseOutputStream.AccessException;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;

public abstract class HwSmsMessage {
    static final String LOG_TAG = "HwSmsMessage";
    public static final int SMS_TOA_UNKNOWN = 128;

    public static SubmitPdu getDeliverPdu(String scAddress, String scTimeStamp, String origAddress, String message, byte[] UDH) {
        byte[] timeStampArray;
        if (origAddress == null || message == null) {
            Rlog.e("SmsMessage", "enter getDeliverPdu().");
            return null;
        }
        SubmitPdu ret = new SubmitPdu();
        ByteArrayOutputStream bo = new ByteArrayOutputStream(176);
        if (scAddress == null) {
            ret.encodedScAddress = null;
        } else {
            ret.encodedScAddress = PhoneNumberUtils.networkPortionToCalledPartyBCDWithLength(scAddress);
        }
        bo.write((UDH != null ? 68 : 0) | 0);
        byte[] daBytes = PhoneNumberUtils.networkPortionToCalledPartyBCD(origAddress);
        if (daBytes == null) {
            bo.write(0);
            bo.write(SMS_TOA_UNKNOWN);
        } else {
            bo.write(((daBytes.length - 1) * 2) - ((daBytes[daBytes.length + -1] & 240) == 240 ? 1 : 0));
            bo.write(daBytes, 0, daBytes.length);
        }
        bo.write(0);
        byte[] userData;
        try {
            userData = GsmAlphabet.stringToGsm7BitPackedWithHeader(message, UDH);
            if ((userData[0] & 255) > 160) {
                Rlog.e("SmsMessage", "Message too long");
                return null;
            }
            bo.write(0);
            try {
                timeStampArray = encodeSCTimeStamp(scTimeStamp);
                bo.write(timeStampArray, 0, timeStampArray.length);
            } catch (AccessException err) {
                bo.write(0);
                bo.write(0);
                bo.write(0);
                bo.write(0);
                bo.write(0);
                bo.write(0);
                bo.write(0);
                Rlog.e(LOG_TAG, "encode SC timestamp failed." + err);
            }
            bo.write(userData, 0, userData.length);
            ret.encodedMessage = bo.toByteArray();
            return ret;
        } catch (EncodeException e) {
            try {
                userData = SmsMessage.encodeUCS2(message, UDH);
                if (userData.length > 141) {
                    return null;
                }
                bo.write(8);
                try {
                    timeStampArray = encodeSCTimeStamp(scTimeStamp);
                    bo.write(timeStampArray, 0, timeStampArray.length);
                } catch (AccessException exception) {
                    bo.write(0);
                    bo.write(0);
                    bo.write(0);
                    bo.write(0);
                    bo.write(0);
                    bo.write(0);
                    bo.write(0);
                    Rlog.e(LOG_TAG, "encode SC timestamp failed." + exception);
                }
                bo.write(userData, 0, userData.length);
            } catch (UnsupportedEncodingException uex) {
                Rlog.e(LOG_TAG, "Implausible UnsupportedEncodingException", uex);
                return null;
            }
        }
    }

    private static byte[] encodeSCTimeStamp(String mcTimeStamp) throws AccessException {
        BitwiseOutputStream outStream = new BitwiseOutputStream(7);
        if (mcTimeStamp == null || mcTimeStamp.length() == 0) {
            Rlog.e(LOG_TAG, "bad parm in encodeSCTimeStamp().");
            return outStream.toByteArray();
        }
        Time Date = new Time("UTC");
        Date.set(Long.parseLong(mcTimeStamp));
        Date.year %= 100;
        outStream.write(4, (byte) (Date.year % 10));
        outStream.write(4, (byte) (Date.year / 10));
        Date.month++;
        outStream.write(4, (byte) (Date.month % 10));
        outStream.write(4, (byte) (Date.month / 10));
        outStream.write(4, (byte) (Date.monthDay % 10));
        outStream.write(4, (byte) (Date.monthDay / 10));
        outStream.write(4, (byte) (Date.hour % 10));
        outStream.write(4, (byte) (Date.hour / 10));
        outStream.write(4, (byte) (Date.minute % 10));
        outStream.write(4, (byte) (Date.minute / 10));
        outStream.write(4, (byte) (Date.second % 10));
        outStream.write(4, (byte) (Date.second / 10));
        outStream.write(8, 0);
        return outStream.toByteArray();
    }

    public static String getUserDataGSM8Bit(PduParser p, int septetCount) {
        try {
            String ret = GsmAlphabet.gsm8BitUnpackedToString(p.mPdu, p.mCur, septetCount);
            p.mCur += septetCount;
            return ret;
        } catch (Exception e) {
            return null;
        }
    }
}
