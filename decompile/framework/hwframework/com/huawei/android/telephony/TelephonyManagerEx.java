package com.huawei.android.telephony;

import android.net.Uri;
import android.os.Message;
import android.os.ServiceManager;
import android.telephony.HwTelephonyManagerInner;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.IHwTelephony.Stub;
import com.android.internal.telephony.IPhoneCallback;
import com.android.internal.telephony.Phone;
import com.huawei.android.util.NoExtAPIException;

public class TelephonyManagerEx {
    public static final int ANTENNA_BOTH = 2;
    public static final int ANTENNA_DOWN = 0;
    public static final int ANTENNA_UP = 1;
    public static final int ANT_SWITCH_ASDIV_CONFIG_LOWER = 0;
    public static final int ANT_SWITCH_ASDIV_CONFIG_UPPER = 1;
    public static final int BIGPOWER_NO = 0;
    public static final int BIGPOWER_YES = 1;
    public static final String KEY1 = "key1";
    public static final int LTE_SWITCH_OFF = 0;
    public static final int LTE_SWITCH_ON = 1;
    public static final int SIM_STATE_ABSENT = 1;
    public static final int SIM_STATE_CARD_IO_ERROR = 6;
    public static final int SIM_STATE_DEACTIVED = 8;
    public static final int SIM_STATE_NETWORK_LOCKED = 4;
    public static final int SIM_STATE_NOT_READY = 7;
    public static final int SIM_STATE_PIN_REQUIRED = 2;
    public static final int SIM_STATE_PUK_REQUIRED = 3;
    public static final int SIM_STATE_READY = 5;
    public static final int SIM_STATE_UNKNOWN = 0;
    public static final int TYPE_ANTENNA = 2;
    public static final int TYPE_BAND = 1;
    public static final int TYPE_BIGPOWER = 4;
    public static final int UNKNOWN = -1;

    public static boolean isMultiSimEnabled(TelephonyManager object) {
        return object.isMultiSimEnabled();
    }

    public static int getPhoneType(TelephonyManager object) {
        return object.getPhoneType();
    }

    public static String getPesn(TelephonyManager object) {
        throw new NoExtAPIException("method not supported.");
    }

    public static boolean isVirtualNet() {
        throw new NoExtAPIException("method not supported.");
    }

    public static String getApnFilter() {
        throw new NoExtAPIException("method not supported.");
    }

    public static Uri getSimPhonebookProviderUri(int subscription) {
        throw new NoExtAPIException("method not supported.");
    }

    public static boolean isCTCdmaCardInGsmMode() {
        return HwTelephonyManagerInner.getDefault().isCTCdmaCardInGsmMode();
    }

    public static String getIccCardType() {
        return null;
    }

    public static boolean isMTKPlatform() {
        return false;
    }

    public static int getDefault4GSlotId() {
        return HwTelephonyManagerInner.getDefault().getDefault4GSlotId();
    }

    public static void setDefault4GSlotId(int slotId, Message msg) {
        HwTelephonyManagerInner.getDefault().setDefault4GSlotId(slotId, msg);
    }

    public static boolean isSetDefault4GSlotIdEnabled() {
        return HwTelephonyManagerInner.getDefault().isSetDefault4GSlotIdEnabled();
    }

    public static void waitingSetDefault4GSlotDone(boolean waiting) {
        HwTelephonyManagerInner.getDefault().waitingSetDefault4GSlotDone(waiting);
    }

    public static String getCdmaMlplVersion() {
        return HwTelephonyManagerInner.getDefault().getCdmaMlplVersion();
    }

    public static String getCdmaMsplVersion() {
        return HwTelephonyManagerInner.getDefault().getCdmaMsplVersion();
    }

    public static boolean checkCdmaSlaveCardMode(int mode) {
        return HwTelephonyManagerInner.getDefault().checkCdmaSlaveCardMode(mode);
    }

    public static boolean isCTSimCard(int slotId) {
        return HwTelephonyManagerInner.getDefault().isCTSimCard(slotId);
    }

    public static boolean isSubDeactivedByPowerOff(Phone phone) {
        return HwTelephonyManagerInner.getDefault().isSubDeactivedByPowerOff((long) phone.getSubId());
    }

    public static boolean isNeedToRadioPowerOn(Phone phone) {
        return HwTelephonyManagerInner.getDefault().isNeedToRadioPowerOn((long) phone.getSubId());
    }

    public static void setLteServiceAbility(int ability) {
        HwTelephonyManagerInner.getDefault().setLteServiceAbility(ability);
    }

    public static int getLteServiceAbility() {
        return HwTelephonyManagerInner.getDefault().getLteServiceAbility();
    }

    public static boolean registerForWirelessState(int type, int slotId, IPhoneCallback callback) {
        try {
            Stub.asInterface(ServiceManager.getService("phone_huawei")).registerForWirelessState(type, slotId, callback);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean unregisterForWirelessState(int type, int slotId, IPhoneCallback callback) {
        try {
            Stub.asInterface(ServiceManager.getService("phone_huawei")).unregisterForWirelessState(type, slotId, callback);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean setMaxTxPower(int type, int power) {
        try {
            Stub.asInterface(ServiceManager.getService("phone_huawei")).setMaxTxPower(type, power);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
