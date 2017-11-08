package com.android.internal.telephony;

import android.common.HwFrameworkFactory;
import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.provider.Settings.SettingNotFoundException;
import android.provider.Settings.System;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.intelligentdataswitch.IDSConstants;
import com.android.internal.telephony.vsim.HwVSimUtils;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

public class HwFullNetwork extends Handler {
    private static final /* synthetic */ int[] -com-android-internal-telephony-HwFullNetwork$CommrilModeSwitchesValues = null;
    private static final int ACTIVE = 1;
    private static final int CARDTRAY_OUT_SLOT = 0;
    private static final String CARDTRAY_STATE_FILE = "/sys/kernel/sim/sim_hotplug_state";
    private static final int CARD_MAX = TelephonyManager.getDefault().getPhoneCount();
    private static final int CARD_TYPE_DUAL_MODE = 3;
    private static final int CARD_TYPE_NO_SIM = 0;
    private static final int CARD_TYPE_SINGLE_CDMA = 2;
    private static final int CARD_TYPE_SINGLE_GSM = 1;
    private static final int CHECK_CDMASIDE_TIMES = 5;
    private static final int COMBINE = 0;
    private static final int EVENT_DELAY_SET_CDMA_MODE_SIDE = 11;
    private static final int EVENT_DELAY_SWITCH_COMMRIL = 6;
    private static final int EVENT_GET_CDMA_MODE_SIDE_DONE = 12;
    private static final int EVENT_GET_RAT_COMBINE_MODE_DONE = 5;
    private static final int EVENT_RADIO_AVIALABLE = 4;
    private static final int EVENT_RADIO_UNAVAILABLE = 14;
    private static final int EVENT_RESTART_RILD = 1;
    private static final int EVENT_SET_CDMA_MODE_SIDE_DONE = 10;
    private static final int EVENT_SET_RAT_COMBINE_MODE_DONE = 2;
    private static final int EVENT_SIM_HOTPLUG = 13;
    private static final int EVENT_SWITCH_RFIC_CHANNEL_DONE = 3;
    private static final int EVENT_SWITCH_SIM_SLOT_CFG_DONE = 8;
    private static final int EVENT_SWTICH_COMMRIL_MODE_DONE = 7;
    private static final int INVALID = -1;
    private static final int INVALID_MODEM = -1;
    protected static final boolean IS_HISI_CDMA_SUPPORTED = SystemProperties.getBoolean(PROPERTY_HISI_CDMA_SUPPORTED, false);
    private static final boolean IS_SINGLE_CARD_TRAY = SystemProperties.getBoolean("persist.radio.single_card_tray", true);
    private static final boolean IS_SUPPORT_FULL_NETWORK = SystemProperties.getBoolean(PROPERTY_FULL_NETWORK_SUPPORT, false);
    private static final boolean IS_TUNERIC_LOW_PERF = SystemProperties.getBoolean("ro.hwpp.is_tuneric_low_perf", false);
    private static final int MODEM0 = 0;
    private static final int MODEM1 = 1;
    private static final int NOT_ACTIVE = 0;
    private static final int NOT_COMBINE = 1;
    static final String PROPERTY_CG_STANDBY_MODE = "persist.radio.cg_standby_mode";
    static final String PROPERTY_COMMRIL_MODE = "persist.radio.commril_mode";
    static final String PROPERTY_FULL_NETWORK_SUPPORT = "ro.config.full_network_support";
    static final String PROPERTY_HISI_CDMA_SUPPORTED = "ro.config.hisi_cdma_supported";
    private static final int RETRY_MAX_MILLI_SECONDS = 5000;
    private static final int RF0 = 0;
    private static final int RF1 = 1;
    private static final int SLOT1 = 1;
    private static final int SLOT2 = 2;
    private static final String TAG = "HwFullNetwork";
    private static final int TIME_DELAY_TO_SET_CDMA_MODE_SIDE = 5000;
    private static final int TIME_DELAY_TO_SWITCH_COMMRIL = 100;
    private static HwFullNetwork mInstance;
    private static final Object mLock = new Object();
    private static final boolean sIsPlatformSupportVSim = SystemProperties.getBoolean("ro.radio.vsim_support", false);
    private int CMODEM_STATUS = -1;
    private boolean bCheckedRatCombine = false;
    private boolean mAllCardsReady = false;
    private int mBalongSimSlot = 0;
    private int mCdmaSide = -1;
    CommandsInterface[] mCis;
    Context mContext;
    private int mDelayRetryCount = 0;
    private CommrilMode mExpectCommrilMode = CommrilMode.NON_MODE;
    private boolean[] mGetBalongSimSlotDone = new boolean[CARD_MAX];
    private boolean mGetCdmaSideDone = false;
    private boolean[] mGetUiccCardsStatusDone = new boolean[CARD_MAX];
    private HotplugState[] mHotplugState = new HotplugState[CARD_MAX];
    private boolean mIsOngoingRestartRild = false;
    private int mMainSlot = -1;
    private boolean mNeedSwitchCommrilMode = false;
    private int[] mOldMainSwitchTypes = new int[CARD_MAX];
    private int mPollingCount = 0;
    private boolean[] mRadioOn = new boolean[CARD_MAX];
    private int[] mRatCombineMode = new int[CARD_MAX];
    private Message mSetCommrilModeCompleteMsg;
    private int mSwitchCommrilTimes = 0;
    private int[] mSwitchTypes = new int[CARD_MAX];
    private boolean mWaitingSwitchCommrilMode = true;

    public enum CommrilMode {
        NON_MODE,
        SVLTE_MODE,
        CLG_MODE,
        CG_MODE,
        ULG_MODE,
        HISI_CGUL_MODE,
        HISI_CG_MODE
    }

    public enum HotplugState {
        STATE_PLUG_OUT,
        STATE_PLUG_IN
    }

    private static /* synthetic */ int[] -getcom-android-internal-telephony-HwFullNetwork$CommrilModeSwitchesValues() {
        if (-com-android-internal-telephony-HwFullNetwork$CommrilModeSwitchesValues != null) {
            return -com-android-internal-telephony-HwFullNetwork$CommrilModeSwitchesValues;
        }
        int[] iArr = new int[CommrilMode.values().length];
        try {
            iArr[CommrilMode.CG_MODE.ordinal()] = 1;
        } catch (NoSuchFieldError e) {
        }
        try {
            iArr[CommrilMode.CLG_MODE.ordinal()] = 2;
        } catch (NoSuchFieldError e2) {
        }
        try {
            iArr[CommrilMode.HISI_CGUL_MODE.ordinal()] = 3;
        } catch (NoSuchFieldError e3) {
        }
        try {
            iArr[CommrilMode.HISI_CG_MODE.ordinal()] = 4;
        } catch (NoSuchFieldError e4) {
        }
        try {
            iArr[CommrilMode.NON_MODE.ordinal()] = 6;
        } catch (NoSuchFieldError e5) {
        }
        try {
            iArr[CommrilMode.SVLTE_MODE.ordinal()] = 7;
        } catch (NoSuchFieldError e6) {
        }
        try {
            iArr[CommrilMode.ULG_MODE.ordinal()] = 5;
        } catch (NoSuchFieldError e7) {
        }
        -com-android-internal-telephony-HwFullNetwork$CommrilModeSwitchesValues = iArr;
        return iArr;
    }

    public static HwFullNetwork make(Context context, CommandsInterface[] ci) {
        HwFullNetwork hwFullNetwork;
        synchronized (mLock) {
            if (mInstance != null) {
                throw new RuntimeException("MSimUiccController.make() should only be called once");
            }
            mInstance = new HwFullNetwork(context, ci);
            hwFullNetwork = mInstance;
        }
        return hwFullNetwork;
    }

    private HwFullNetwork(Context context, CommandsInterface[] ci) {
        this.mCis = ci;
        this.mContext = context;
        if (HwAllInOneController.IS_FAST_SWITCH_SIMSLOT) {
            this.mWaitingSwitchCommrilMode = false;
        } else if (IS_SUPPORT_FULL_NETWORK && !HwModemCapability.isCapabilitySupport(3)) {
            for (int i = 0; i < this.mCis.length; i++) {
                Integer index = Integer.valueOf(i);
                this.mCis[i].registerForAvailable(this, 4, index);
                this.mRatCombineMode[i] = -1;
                this.mSwitchTypes[i] = -1;
                this.mGetUiccCardsStatusDone[i] = false;
                this.mGetBalongSimSlotDone[i] = false;
                this.mOldMainSwitchTypes[i] = -1;
                if (IS_HISI_CDMA_SUPPORTED) {
                    this.mCis[i].registerForSimHotPlug(this, 13, index);
                }
                this.mHotplugState[i] = HotplugState.STATE_PLUG_IN;
                this.mCis[i].registerForNotAvailable(this, 14, index);
            }
            logi("HwFullNetwork constructor!");
        }
    }

    public static HwFullNetwork getInstance() {
        HwFullNetwork hwFullNetwork;
        synchronized (mLock) {
            if (mInstance == null) {
                throw new RuntimeException("HwFullNetwork.getInstance can't be called before make()");
            }
            hwFullNetwork = mInstance;
        }
        return hwFullNetwork;
    }

    public void handleMessage(Message msg) {
        Integer index = getCiIndex(msg);
        AsyncResult ar = msg.obj;
        boolean[] zArr;
        int length;
        int i;
        switch (msg.what) {
            case 1:
                logd("Received EVENT_RESTART_RILD on index " + index + ", mPollingCount = " + this.mPollingCount);
                try {
                    if (this.mPollingCount != 0 || hasMessages(6)) {
                        logd("Don't restart rild now, waiting for other command set done...");
                        this.mWaitingSwitchCommrilMode = false;
                        return;
                    }
                    if (this.mNeedSwitchCommrilMode && this.mExpectCommrilMode != CommrilMode.NON_MODE) {
                        logd("setCommrilMode to " + this.mExpectCommrilMode);
                        setCommrilMode(this.mExpectCommrilMode);
                        this.mExpectCommrilMode = CommrilMode.NON_MODE;
                        this.mNeedSwitchCommrilMode = false;
                    }
                    logd("Finally, restart rild...");
                    this.mIsOngoingRestartRild = true;
                    disposeCardStatus();
                    if (HwHotplugController.IS_HOTSWAP_SUPPORT) {
                        HwHotplugController.getInstance().onRestartRild();
                    }
                    this.mCis[0].restartRild(null);
                    this.mWaitingSwitchCommrilMode = false;
                    return;
                } catch (RuntimeException e) {
                }
                break;
            case 2:
                logd("Received EVENT_SET_RAT_COMBINE_MODE_DONE on index " + index);
                if (ar == null || ar.exception != null) {
                    loge("Error! setRatCommbie is failed!!");
                }
                waitToRestartRild();
                return;
            case 3:
                logd("Received EVENT_SWITCH_RFIC_CHANNEL_DONE on index " + index);
                if (ar == null || ar.exception != null) {
                    loge("Error! switch rf channel is failed!!");
                }
                waitToRestartRild();
                return;
            case 4:
                logd("Received EVENT_RADIO_AVAILABLE on index " + index);
                this.mCis[index.intValue()].getHwRatCombineMode(obtainMessage(5, index));
                if (IS_HISI_CDMA_SUPPORTED && index.intValue() == 0) {
                    this.mCis[index.intValue()].getCdmaModeSide(obtainMessage(12, index));
                    this.mGetCdmaSideDone = false;
                }
                boolean ready = true;
                this.mRadioOn[index.intValue()] = true;
                zArr = this.mRadioOn;
                length = zArr.length;
                i = 0;
                while (i < length) {
                    if (zArr[i]) {
                        i++;
                    } else {
                        ready = false;
                        if (!ready) {
                            if (this.mSetCommrilModeCompleteMsg != null) {
                                logd("Switch CommrilMode Done!!");
                                AsyncResult.forMessage(this.mSetCommrilModeCompleteMsg, Boolean.valueOf(true), null);
                                this.mSetCommrilModeCompleteMsg.sendToTarget();
                                this.mSetCommrilModeCompleteMsg = null;
                            }
                            this.mIsOngoingRestartRild = false;
                            return;
                        } else if (this.mSetCommrilModeCompleteMsg != null) {
                            logd("clean iccids!!");
                            PhoneFactory.getSubInfoRecordUpdater().cleanIccids();
                            return;
                        } else {
                            return;
                        }
                    }
                }
                if (!ready) {
                    if (this.mSetCommrilModeCompleteMsg != null) {
                        logd("Switch CommrilMode Done!!");
                        AsyncResult.forMessage(this.mSetCommrilModeCompleteMsg, Boolean.valueOf(true), null);
                        this.mSetCommrilModeCompleteMsg.sendToTarget();
                        this.mSetCommrilModeCompleteMsg = null;
                    }
                    this.mIsOngoingRestartRild = false;
                    return;
                } else if (this.mSetCommrilModeCompleteMsg != null) {
                    logd("clean iccids!!");
                    PhoneFactory.getSubInfoRecordUpdater().cleanIccids();
                    return;
                } else {
                    return;
                }
            case 5:
                logd("Received EVENT_GET_RAT_COMBINE_MODE_DONE on index " + index);
                onGetRatCombineModeDone(ar, index);
                return;
            case 6:
                logd("Received EVENT_DELAY_SWITCH_COMMRIL on index " + index);
                if (IDSConstants.CHECK_VOICE_CALL_UI_ON_TOP_TIMER == this.mDelayRetryCount && this.mMainSlot == -1) {
                    logd("Blind-4G not set MainSlot, delay timer expire, Here do check using default 4G slotID!");
                    setMainSlot(HwFrameworkFactory.getHwInnerTelephonyManager().getDefault4GSlotId(), false);
                }
                trySwitchCommrilMode();
                return;
            case 7:
                logd("Received EVENT_SWTICH_COMMRIL_MODE_DONE on index " + index);
                logd("current commril mode is " + getCommrilMode().toString());
                if (isntFirstPowerup()) {
                    HwAllInOneController.getInstance().setWaitingSwitchBalongSlot(false);
                    return;
                }
                return;
            case 8:
                logd("Received EVENT_SWITCH_SIM_SLOT_CFG_DONE on index " + index);
                if (ar == null || ar.exception != null) {
                    loge("Error! switch balong sim slot failed!!");
                }
                waitToRestartRild();
                return;
            case 10:
                logd("Received EVENT_SET_CDMA_MODE_SIDE_DONE on index " + index);
                removeMessages(11);
                if (ar == null || ar.exception != null) {
                    loge("Error! setCdmaModeSide is failed!!");
                }
                waitToRestartRild();
                return;
            case 11:
                logd("Received EVENT_DELAY_SET_CDMA_MODE_SIDE");
                removeMessages(10);
                waitToRestartRild();
                return;
            case 12:
                logd("Received EVENT_GET_CDMA_MODE_SIDE_DONE on index " + index);
                onGetCdmaModeDone(ar, index);
                if (HwAllInOneController.IS_HISI_DSDX) {
                    HwAllInOneController.getInstance().checkIfAllCardsReady();
                    return;
                }
                return;
            case 13:
                onSimHotPlug(ar, index);
                return;
            case 14:
                logd("EVENT_RADIO_UNAVAILABLE, on index " + index);
                this.mSwitchTypes[index.intValue()] = -1;
                this.mGetUiccCardsStatusDone[index.intValue()] = false;
                this.mGetBalongSimSlotDone[index.intValue()] = false;
                this.mOldMainSwitchTypes[index.intValue()] = -1;
                boolean firstRadioUnavaliable = true;
                zArr = this.mRadioOn;
                length = zArr.length;
                i = 0;
                while (i < length) {
                    if (zArr[i]) {
                        i++;
                    } else {
                        firstRadioUnavaliable = false;
                        if (firstRadioUnavaliable) {
                            HwAllInOneController.getInstance().setCommrilRestartRild(true);
                        }
                        this.mRadioOn[index.intValue()] = false;
                        return;
                    }
                }
                if (firstRadioUnavaliable) {
                    HwAllInOneController.getInstance().setCommrilRestartRild(true);
                }
                this.mRadioOn[index.intValue()] = false;
                return;
            default:
                return;
        }
    }

    private void onGetRatCombineModeDone(AsyncResult ar, Integer index) {
        int slaveSlot = getAnotherSlotId(index.intValue());
        if (!(ar == null || ar.result == null)) {
            this.mRatCombineMode[index.intValue()] = ((int[]) ar.result)[0];
        }
        logd("mRatCombineMode[" + index + "] is " + this.mRatCombineMode[index.intValue()] + ", mRatCombineMode[" + slaveSlot + "] is " + this.mRatCombineMode[slaveSlot]);
    }

    private void onGetCdmaModeDone(AsyncResult ar, Integer index) {
        this.mGetCdmaSideDone = true;
        if (!HwVSimUtils.isAllowALSwitch()) {
            this.mCdmaSide = -1;
        } else if (!(ar == null || ar.result == null)) {
            this.mCdmaSide = ((int[]) ar.result)[0];
        }
        logd("mCdmaSide is " + this.mCdmaSide);
    }

    private void onSimHotPlug(AsyncResult ar, Integer index) {
        logd("onSimHotPlug");
        if (ar != null && ar.result != null && ((int[]) ar.result).length > 0 && HotplugState.STATE_PLUG_IN.ordinal() == ((int[]) ar.result)[0]) {
            singleCardPlugIn(index.intValue());
        }
    }

    public synchronized void onQueryCardTypeDone(AsyncResult ar, Integer index) {
        int slaveSlot = getAnotherSlotId(index.intValue());
        this.mOldMainSwitchTypes[index.intValue()] = this.mSwitchTypes[index.intValue()];
        if (!(ar == null || ar.result == null)) {
            this.mSwitchTypes[index.intValue()] = ((int[]) ar.result)[0] & 15;
        }
        logd("mSwitchTypes[" + index + "] = " + this.mSwitchTypes[index.intValue()] + ", mSwitchTypes[" + slaveSlot + "] = " + this.mSwitchTypes[slaveSlot]);
    }

    public void onGetBalongSimDone(AsyncResult ar, Integer index) {
        logd("onGetBalongSimDone");
        if (ar != null && ar.result != null && ((int[]) ar.result).length == 3) {
            int[] slots = ar.result;
            logd("slot result = " + Arrays.toString(slots));
            judgeBalongSimSlotFromResult(slots);
            this.mGetBalongSimSlotDone[index.intValue()] = true;
        } else if (ar == null || ar.result == null || ((int[]) ar.result).length != 2) {
            loge("onGetBalongSimDone error");
        } else {
            if (((int[]) ar.result)[1] + ((int[]) ar.result)[0] > 1) {
                this.mBalongSimSlot = ((int[]) ar.result)[0] - 1;
            } else {
                this.mBalongSimSlot = ((int[]) ar.result)[0];
            }
            this.mGetBalongSimSlotDone[index.intValue()] = true;
        }
        logd("mBalongSimSlot = " + this.mBalongSimSlot);
    }

    private void judgeBalongSimSlotFromResult(int[] slots) {
        boolean isMainSlotOnVSim = false;
        if (slots[0] == 0 && slots[1] == 1 && slots[2] == 2) {
            this.mBalongSimSlot = 0;
            isMainSlotOnVSim = false;
        } else if (slots[0] == 1 && slots[1] == 0 && slots[2] == 2) {
            this.mBalongSimSlot = 1;
            isMainSlotOnVSim = false;
        } else if (slots[0] == 2 && slots[1] == 1 && slots[2] == 0) {
            this.mBalongSimSlot = 0;
            isMainSlotOnVSim = true;
        } else if (slots[0] == 2 && slots[1] == 0 && slots[2] == 1) {
            this.mBalongSimSlot = 1;
            isMainSlotOnVSim = true;
        } else {
            loge("onGetBalongSimDone invalid slot result");
        }
        logd("isMainSlotOnVSim = " + isMainSlotOnVSim);
    }

    public synchronized void onGetIccCardStatusDone(AsyncResult ar, Integer index) {
        if (ar.exception != null) {
            loge("Error getting ICC status. RIL_REQUEST_GET_ICC_STATUS should never return an error: " + ar.exception);
        } else if (isValidIndex(index.intValue())) {
            this.mGetUiccCardsStatusDone[index.intValue()] = true;
        } else {
            loge("onGetIccCardStatusDone: invalid index : " + index);
        }
    }

    public void checkIfAllCardsReady() {
        if (!HwVSimUtils.isAllowALSwitch()) {
            logd("checkIfAllCardsReady, vsim on sub");
            this.mNeedSwitchCommrilMode = false;
            this.mWaitingSwitchCommrilMode = false;
        } else if ((IS_SINGLE_CARD_TRAY && isCardTrayOut(0)) || (!IS_SINGLE_CARD_TRAY && isCardTrayOut(0) && isCardTrayOut(1))) {
            logd("checkIfAllCardsReady, both card tray is out, disposeLocalCardStatus.");
            disposeLocalCardStatus();
        } else if (IS_SINGLE_CARD_TRAY && !isCardTrayOut(0) && this.mSwitchTypes[0] == 0 && this.mSwitchTypes[1] == 0 && !HwVSimUtils.isVSimInProcess() && !HwVSimUtils.isVSimCauseCardReload() && isCardHotPlugIn()) {
            disposeLocalCardStatus();
            if (HwAllInOneController.IS_HISI_DSDX && isGetCdmaModeDone()) {
                HwAllInOneController allInOneController = HwAllInOneController.getInstance();
                if (allInOneController != null) {
                    logd("All tray out. disposeCardStatus");
                    allInOneController.disposeCardStatus(true);
                }
            }
            logd("checkIfAllCardsReady, both cards are absent, return.");
        } else {
            boolean ready;
            int i;
            int countGetBalongSimSlotDone;
            if (-1 != this.mOldMainSwitchTypes[0] || -1 == this.mSwitchTypes[0] || this.mSwitchTypes[0] == 0) {
                if (!(-1 != this.mOldMainSwitchTypes[1] || -1 == this.mSwitchTypes[1] || this.mSwitchTypes[1] == 0)) {
                }
                ready = true;
                i = 0;
                while (i < CARD_MAX) {
                    if (this.mSwitchTypes[i] != -1) {
                        ready = false;
                        break;
                    } else if (!this.mGetUiccCardsStatusDone[i]) {
                        ready = false;
                        break;
                    } else {
                        i++;
                    }
                }
                countGetBalongSimSlotDone = 0;
                for (i = 0; i < CARD_MAX; i++) {
                    if (this.mGetBalongSimSlotDone[i]) {
                        countGetBalongSimSlotDone++;
                    }
                }
                if (countGetBalongSimSlotDone == 0) {
                    logd("mGetBalongSimSlotDone all false");
                    ready = false;
                }
                this.mAllCardsReady = ready;
                logd("mAllCardsReady is " + ready);
                if (this.mAllCardsReady && this.mSetCommrilModeCompleteMsg == null) {
                    logi("All uicc card ready!");
                    if (!HwAllInOneController.IS_HISI_DSDX) {
                        trySwitchCommrilMode();
                    }
                }
            }
            if (HwAllInOneController.IS_HISI_DSDX) {
                if (!this.mNeedSwitchCommrilMode) {
                    logd("checkIfAllCardsReady, set mNeedSwitchCommrilMode as true.");
                    this.mNeedSwitchCommrilMode = true;
                }
                if (!(-1 == this.mSwitchTypes[0] || this.mSwitchTypes[0] == 0)) {
                    this.mOldMainSwitchTypes[0] = this.mSwitchTypes[0];
                    logd("checkIfAllCardsReady, mOldMainSwitchTypes[SUB1] =" + this.mOldMainSwitchTypes[0]);
                }
                if (!(-1 == this.mSwitchTypes[1] || this.mSwitchTypes[1] == 0)) {
                    this.mOldMainSwitchTypes[1] = this.mSwitchTypes[1];
                    logd("checkIfAllCardsReady, mOldMainSwitchTypes[SUB2] =" + this.mOldMainSwitchTypes[1]);
                }
                vsimAdjustNeedSwitchCommrilMode();
            } else {
                logd("checkIfAllCardsReady, set mNeedSwitchCommrilMode as true.");
                this.mNeedSwitchCommrilMode = true;
            }
            ready = true;
            i = 0;
            while (i < CARD_MAX) {
                if (this.mSwitchTypes[i] != -1) {
                    if (!this.mGetUiccCardsStatusDone[i]) {
                        ready = false;
                        break;
                    }
                    i++;
                } else {
                    ready = false;
                    break;
                }
            }
            countGetBalongSimSlotDone = 0;
            for (i = 0; i < CARD_MAX; i++) {
                if (this.mGetBalongSimSlotDone[i]) {
                    countGetBalongSimSlotDone++;
                }
            }
            if (countGetBalongSimSlotDone == 0) {
                logd("mGetBalongSimSlotDone all false");
                ready = false;
            }
            this.mAllCardsReady = ready;
            logd("mAllCardsReady is " + ready);
            logi("All uicc card ready!");
            if (HwAllInOneController.IS_HISI_DSDX) {
                trySwitchCommrilMode();
            }
        }
    }

    public boolean switchCommrilMode(CommrilMode expectCommrilMode, int expectMainSlot, int currMainSlot, Message onCompleteMsg) {
        if (currMainSlot < 0 || currMainSlot >= CARD_MAX || expectMainSlot >= CARD_MAX) {
            loge("invalid slot, currMainSlot = " + currMainSlot + ", expectMainSlot = " + expectMainSlot);
            return false;
        } else if (this.mSetCommrilModeCompleteMsg != null) {
            loge("FullNetwork is doing switch commril mode, other module shouldn't call!");
            return false;
        } else {
            this.mNeedSwitchCommrilMode = true;
            this.mSetCommrilModeCompleteMsg = onCompleteMsg;
            switchCommrilMode(expectCommrilMode, currMainSlot);
            if (expectMainSlot >= 0) {
                Message callbackMsg = obtainMessage(8);
                if (expectMainSlot == 0) {
                    this.mCis[expectMainSlot].switchBalongSim(1, 2, callbackMsg);
                } else {
                    this.mCis[expectMainSlot].switchBalongSim(2, 1, callbackMsg);
                }
                incPollingCount(1);
            }
            return true;
        }
    }

    public void switchCommrilModeIfNeeded(int mainSlotId, int pollingStepNeedAdded) {
        if (!IS_SUPPORT_FULL_NETWORK) {
            logd("Not support full network!");
        } else if (!HwModemCapability.isCapabilitySupport(3)) {
            if (HwVSimUtils.isAllowALSwitch()) {
                logd("called by outside ,switch 4G slot to " + mainSlotId + " manully or blind-4G, check if need swtich commrilmode first.");
                if (HwAllInOneController.IS_HISI_DSDX && this.mSetCommrilModeCompleteMsg != null) {
                    loge("switchCommrilModeIfNeeded in switching mSetCommrilModeCompleteMsg is not null");
                    return;
                } else if (mainSlotId < 0 || mainSlotId >= CARD_MAX) {
                    loge("mainSlotId invalid, " + mainSlotId);
                    return;
                } else {
                    incPollingCount(pollingStepNeedAdded);
                    if (setMainSlot(mainSlotId, false)) {
                        trySwitchCommrilMode();
                    }
                    return;
                }
            }
            logd("switchCommrilModeIfNeeded, vsim on sub");
            this.mNeedSwitchCommrilMode = false;
            this.mWaitingSwitchCommrilMode = false;
        }
    }

    private void trySwitchCommrilMode() {
        if (HwVSimUtils.isAllowALSwitch()) {
            boolean isAllCardTrayOut;
            if (IS_SINGLE_CARD_TRAY && isCardTrayOut(0)) {
                isAllCardTrayOut = true;
            } else if (IS_SINGLE_CARD_TRAY || !isCardTrayOut(0)) {
                isAllCardTrayOut = false;
            } else {
                isAllCardTrayOut = isCardTrayOut(1);
            }
            if (isAllCardTrayOut) {
                logd("trySwitchCommrilMode, both card tray is out, disposeLocalCardStatus.");
                disposeLocalCardStatus();
                return;
            } else if (IS_SINGLE_CARD_TRAY && !isCardTrayOut(0) && ((this.mSwitchTypes[0] == 0 && this.mSwitchTypes[1] == 0 && isCardHotPlugIn()) || (-1 == this.mSwitchTypes[0] && -1 == this.mSwitchTypes[1]))) {
                logd("trySwitchCommrilMode, both cards are absent, return.");
                disposeLocalCardStatus();
                return;
            } else {
                logd("trySwitchCommrilMode, mAllCardsReady = " + this.mAllCardsReady + ", mMainSlot = " + this.mMainSlot);
                if (this.mAllCardsReady && this.mSetCommrilModeCompleteMsg == null && this.mMainSlot != -1) {
                    this.mDelayRetryCount = 0;
                    removeMessages(6);
                    switchCommrilModeIfNeeded();
                } else if (this.mDelayRetryCount < IDSConstants.CHECK_VOICE_CALL_UI_ON_TOP_TIMER) {
                    removeMessages(6);
                    sendEmptyMessageDelayed(6, 100);
                    this.mDelayRetryCount += 100;
                }
                return;
            }
        }
        logd("trySwitchCommrilMode, vsim on sub");
        this.mNeedSwitchCommrilMode = false;
        this.mWaitingSwitchCommrilMode = false;
    }

    private void switchCommrilModeIfNeeded() {
        logd("[switchCommrilModeIfNeeded]: mainslot = " + this.mMainSlot + ", cardType[SUB0] = " + this.mSwitchTypes[0] + ", cardType[SUB1] = " + this.mSwitchTypes[1]);
        CommrilMode currentMode = getCommrilMode();
        if (!(checkRatCombineModeMatched(currentMode, this.mMainSlot, this.mRatCombineMode) || IS_HISI_CDMA_SUPPORTED)) {
            currentMode = CommrilMode.NON_MODE;
            logd("[switchCommrilModeIfNeeded]: combineMode isn't Matched, set currentMode to NON_MODE");
        }
        if (!checkCdmaModeMatched(currentMode)) {
            currentMode = CommrilMode.NON_MODE;
            logd("[switchCommrilModeIfNeeded]: CdmaMode isn't Matched, set currentMode to NON_MODE");
        }
        if (!isBalongSimSynced()) {
            logd("mBalongSimSlot != mMainSlot");
        }
        this.mExpectCommrilMode = getExpectCommrilMode(this.mMainSlot, this.mSwitchTypes);
        logd("[switchCommrilModeIfNeeded]: CurrentCommrilMode = " + currentMode);
        if (this.mExpectCommrilMode == currentMode || this.mExpectCommrilMode == CommrilMode.NON_MODE) {
            logd("[switchCommrilModeIfNeeded]: Don't need switch commrilMode...");
            this.mNeedSwitchCommrilMode = false;
            this.mWaitingSwitchCommrilMode = false;
            this.mSwitchCommrilTimes = 0;
            if (HwAllInOneController.getInstance().isSetDualCardSlotComplete()) {
                HwAllInOneController.getInstance().setWaitingSwitchBalongSlot(false);
            }
            if (HwHotplugController.IS_HOTSWAP_SUPPORT) {
                logd("[switchCommrilModeIfNeeded]:Hotswap is supported!");
                HwHotplugController.getInstance().processNotifyPromptHotPlug(false);
                return;
            }
            logd("[switchCommrilModeIfNeeded]:Hot swap is not supported!");
            return;
        }
        logd("[switchCommrilModeIfNeeded]: Need switch commrilMode...");
        this.mSetCommrilModeCompleteMsg = obtainMessage(7);
        switchCommrilMode(this.mExpectCommrilMode, this.mBalongSimSlot);
        this.mSwitchCommrilTimes++;
        this.mNeedSwitchCommrilMode = true;
    }

    private void switchCommrilMode(CommrilMode newCommrilMode, int mainSlot) {
        logd("[switchCommrilMode]: newCommrilMode = " + newCommrilMode + ", mainSlot = " + mainSlot);
        int slaveSlot = getAnotherSlotId(mainSlot);
        switch (-getcom-android-internal-telephony-HwFullNetwork$CommrilModeSwitchesValues()[newCommrilMode.ordinal()]) {
            case 1:
                this.mCis[mainSlot].setHwRatCombineMode(1, obtainMessage(2, Integer.valueOf(mainSlot)));
                String cg_standby_mode = SystemProperties.get(PROPERTY_CG_STANDBY_MODE, "home");
                logd("[switchCommrilMode]: cg_standby_mode = " + cg_standby_mode);
                if ("roam_gsm".equals(cg_standby_mode)) {
                    this.mCis[slaveSlot].setHwRatCombineMode(1, obtainMessage(2, Integer.valueOf(slaveSlot)));
                    this.mCis[mainSlot].setHwRFChannelSwitch(0, obtainMessage(3, Integer.valueOf(mainSlot)));
                } else {
                    this.mCis[slaveSlot].setHwRatCombineMode(0, obtainMessage(2, Integer.valueOf(slaveSlot)));
                    this.mCis[mainSlot].setHwRFChannelSwitch(1, obtainMessage(3, Integer.valueOf(mainSlot)));
                }
                incPollingCount(3);
                logd("[switchCommrilMode]: Send set CG_MODE request done...");
                return;
            case 2:
                boolean clg_overseas_mode = SystemProperties.getBoolean("persist.radio.overseas_mode", false);
                logd("[switchCommrilMode]: clg_overseas_mode = " + clg_overseas_mode);
                if (clg_overseas_mode) {
                    this.mCis[mainSlot].setHwRatCombineMode(1, obtainMessage(2, Integer.valueOf(mainSlot)));
                } else {
                    this.mCis[mainSlot].setHwRatCombineMode(0, obtainMessage(2, Integer.valueOf(mainSlot)));
                }
                this.mCis[slaveSlot].setHwRatCombineMode(1, obtainMessage(2, Integer.valueOf(slaveSlot)));
                this.mCis[mainSlot].setHwRFChannelSwitch(0, obtainMessage(3, Integer.valueOf(mainSlot)));
                incPollingCount(3);
                logd("[switchCommrilMode]: Send set CLG_MODE request done...");
                return;
            case 3:
                this.mCis[this.mBalongSimSlot].setCdmaModeSide(0, obtainMessage(10));
                sendEmptyMessageDelayed(11, 5000);
                incPollingCount(1);
                logd("[switchCommrilMode]: Send set EVENT_SET_CDMA_MODE_SIDE_DONE to modem0 request done...");
                return;
            case 4:
                this.mCis[this.mBalongSimSlot].setCdmaModeSide(1, obtainMessage(10));
                sendEmptyMessageDelayed(11, 5000);
                incPollingCount(1);
                logd("[switchCommrilMode]: Send set EVENT_SET_CDMA_MODE_SIDE_DONE to modem1 request done...");
                return;
            case 5:
                this.mCis[mainSlot].setHwRatCombineMode(1, obtainMessage(2, Integer.valueOf(mainSlot)));
                this.mCis[slaveSlot].setHwRatCombineMode(1, obtainMessage(2, Integer.valueOf(slaveSlot)));
                this.mCis[mainSlot].setHwRFChannelSwitch(0, obtainMessage(3, Integer.valueOf(mainSlot)));
                incPollingCount(3);
                logd("[switchCommrilMode]: Send set ULG_MODE request done...");
                return;
            default:
                loge("[switchCommrilMode]: Error!! Shouldn't enter here!!");
                if (this.mSetCommrilModeCompleteMsg != null) {
                    AsyncResult.forMessage(this.mSetCommrilModeCompleteMsg, Boolean.valueOf(false), null);
                    this.mSetCommrilModeCompleteMsg.sendToTarget();
                    this.mSetCommrilModeCompleteMsg = null;
                    return;
                }
                return;
        }
    }

    private boolean checkCdmaModeMatched(CommrilMode mode) {
        logd("checkCdmaModeMatched enter mCheckCSideTime:" + this.mSwitchCommrilTimes);
        if (this.mCdmaSide == -1 || !IS_HISI_CDMA_SUPPORTED) {
            logd("checkCdmaModeMatched: mCdmaSide is invalid, return true.");
            return true;
        } else if (this.mSwitchCommrilTimes > 5) {
            return true;
        } else {
            switch (-getcom-android-internal-telephony-HwFullNetwork$CommrilModeSwitchesValues()[mode.ordinal()]) {
                case 3:
                    if (this.mCdmaSide == 0) {
                        return true;
                    }
                    break;
                case 4:
                    if (this.mCdmaSide == 1) {
                        return true;
                    }
                    break;
            }
            return false;
        }
    }

    private boolean checkRatCombineModeMatched(CommrilMode mode, int mainSlot, int[] ratCombineMode) {
        logd("checkRatCombineModeMatched: CommrilMode = " + mode + ", mainSlot = " + mainSlot + ", ratCombineMode = " + ratCombineMode[0] + ", " + ratCombineMode[1]);
        int slaveSlot = getAnotherSlotId(mainSlot);
        if (ratCombineMode[mainSlot] == -1 || ratCombineMode[slaveSlot] == -1) {
            logd("checkRatCombineModeMatched: ratCombineMode is invalid, return true.");
            return true;
        }
        if (IS_TUNERIC_LOW_PERF && HwModemCapability.isCapabilitySupport(14)) {
            int currentCmodemStatus;
            if (ratCombineMode[mainSlot] == 0 || ratCombineMode[slaveSlot] == 0) {
                currentCmodemStatus = 1;
            } else {
                currentCmodemStatus = 0;
            }
            logd("currentCmodemStatus:" + currentCmodemStatus + " oldCmodemStatus:" + this.CMODEM_STATUS);
            if (currentCmodemStatus != this.CMODEM_STATUS) {
                this.mCis[mainSlot].notifyCModemStatus(currentCmodemStatus, null);
                this.CMODEM_STATUS = currentCmodemStatus;
            }
        }
        if (this.bCheckedRatCombine) {
            logd("checkRatCombineModeMatched is already implemented, return true.");
            return true;
        }
        logd("checkRatCombineModeMatched set bCheckedRatCombine as true.");
        this.bCheckedRatCombine = true;
        switch (-getcom-android-internal-telephony-HwFullNetwork$CommrilModeSwitchesValues()[mode.ordinal()]) {
            case 1:
                String cg_standby_mode = SystemProperties.get(PROPERTY_CG_STANDBY_MODE, "home");
                logd("cg_standby_mode = " + cg_standby_mode);
                if ("roam_gsm".equals(cg_standby_mode)) {
                    return ratCombineMode[mainSlot] == 1 && ratCombineMode[slaveSlot] == 1;
                } else {
                    if (ratCombineMode[slaveSlot] == 0 && ratCombineMode[mainSlot] == 1) {
                        return true;
                    }
                }
            case 2:
                String overseas_mode = SystemProperties.get("persist.radio.overseas_mode", "false");
                logd("overseas_mode = " + overseas_mode);
                if ("true".equals(overseas_mode)) {
                    if (ratCombineMode[mainSlot] == 1 && ratCombineMode[slaveSlot] == 1) {
                        return true;
                    }
                } else if (ratCombineMode[mainSlot] == 0 && ratCombineMode[slaveSlot] == 1) {
                    return true;
                }
            case 5:
                if (ratCombineMode[mainSlot] == 1 && ratCombineMode[slaveSlot] == 1) {
                    return true;
                }
        }
    }

    private CommrilMode getExpectCommrilMode(int mainSlot, int[] cardType) {
        CommrilMode expectCommrilMode = CommrilMode.NON_MODE;
        if (mainSlot == -1) {
            logd("main slot invalid");
            return expectCommrilMode;
        }
        int anotherSlot = getAnotherSlotId(mainSlot);
        if (cardType[mainSlot] == 2 || cardType[mainSlot] == 3) {
            if (IS_HISI_CDMA_SUPPORTED) {
                expectCommrilMode = CommrilMode.HISI_CGUL_MODE;
            } else {
                expectCommrilMode = CommrilMode.CLG_MODE;
            }
        } else if (cardType[mainSlot] == 1 && (cardType[anotherSlot] == 2 || cardType[anotherSlot] == 3)) {
            if (IS_HISI_CDMA_SUPPORTED) {
                expectCommrilMode = CommrilMode.HISI_CG_MODE;
            } else {
                expectCommrilMode = CommrilMode.CG_MODE;
            }
        } else if (cardType[mainSlot] == 1) {
            if (IS_HISI_CDMA_SUPPORTED) {
                expectCommrilMode = CommrilMode.HISI_CGUL_MODE;
            } else {
                expectCommrilMode = CommrilMode.ULG_MODE;
            }
        } else if (cardType[mainSlot] == 0 && (cardType[anotherSlot] == 2 || cardType[anotherSlot] == 3)) {
            if (IS_HISI_CDMA_SUPPORTED) {
                expectCommrilMode = CommrilMode.HISI_CG_MODE;
            } else {
                expectCommrilMode = CommrilMode.CG_MODE;
            }
        } else if (cardType[mainSlot] == 0 && cardType[anotherSlot] == 1 && (CommrilMode.CG_MODE == getCommrilMode() || CommrilMode.HISI_CG_MODE == getCommrilMode())) {
            if (IS_HISI_CDMA_SUPPORTED) {
                expectCommrilMode = CommrilMode.HISI_CGUL_MODE;
            } else {
                expectCommrilMode = CommrilMode.ULG_MODE;
            }
        } else if (cardType[mainSlot] == 0 && cardType[anotherSlot] == 1 && CommrilMode.ULG_MODE == getCommrilMode() && this.mRatCombineMode[mainSlot] != this.mRatCombineMode[anotherSlot]) {
            expectCommrilMode = CommrilMode.ULG_MODE;
        } else {
            expectCommrilMode = CommrilMode.NON_MODE;
        }
        logd("[getExpectCommrilMode]: expectCommrilMode = " + expectCommrilMode);
        return expectCommrilMode;
    }

    private int getAnotherSlotId(int slotId) {
        return slotId == 0 ? 1 : 0;
    }

    private void setCommrilMode(CommrilMode mode) {
        SystemProperties.set(PROPERTY_COMMRIL_MODE, mode.toString());
    }

    public CommrilMode getCommrilMode() {
        return (CommrilMode) Enum.valueOf(CommrilMode.class, SystemProperties.get(PROPERTY_COMMRIL_MODE, "CLG_MODE"));
    }

    private Integer getCiIndex(Message msg) {
        Integer index = Integer.valueOf(0);
        if (msg == null) {
            return index;
        }
        if (msg.obj != null && (msg.obj instanceof Integer)) {
            return msg.obj;
        }
        if (msg.obj == null || !(msg.obj instanceof AsyncResult)) {
            return index;
        }
        AsyncResult ar = msg.obj;
        if (ar.userObj == null || !(ar.userObj instanceof Integer)) {
            return index;
        }
        return ar.userObj;
    }

    private boolean isValidIndex(int index) {
        return index >= 0 && index < CARD_MAX;
    }

    private boolean isntFirstPowerup() {
        logd(" isntFirstPowerup   ------>>>  " + true);
        return true;
    }

    public void singleCardPlugIn(int cardIndex) {
        logd("singleCardPlugIn cardIndex:" + cardIndex);
        if (cardIndex >= 0 && cardIndex < CARD_MAX) {
            this.mSwitchTypes[cardIndex] = -1;
            this.mHotplugState[cardIndex] = HotplugState.STATE_PLUG_IN;
            if (HwAllInOneController.IS_HISI_DSDX) {
                HwAllInOneController allInOneController = HwAllInOneController.getInstance();
                if (allInOneController != null) {
                    allInOneController.disposeCardStatus(cardIndex);
                }
            }
        }
    }

    public boolean isCardHotPlugIn() {
        if (!IS_SINGLE_CARD_TRAY || !IS_HISI_CDMA_SUPPORTED) {
            return true;
        }
        for (int i = 0; i < CARD_MAX; i++) {
            if (HotplugState.STATE_PLUG_OUT == this.mHotplugState[i]) {
                return false;
            }
        }
        return true;
    }

    public void setCardHotPlugState(HotplugState state) {
        if (IS_HISI_CDMA_SUPPORTED) {
            for (int i = 0; i < CARD_MAX; i++) {
                this.mHotplugState[i] = state;
            }
        }
    }

    public void disposeLocalCardStatus() {
        logd("disposeLocalCardStatus");
        for (int i = 0; i < this.mCis.length; i++) {
            this.mSwitchTypes[i] = -1;
            this.mGetUiccCardsStatusDone[i] = false;
            this.mGetBalongSimSlotDone[i] = false;
            this.mOldMainSwitchTypes[i] = -1;
        }
        this.mDelayRetryCount = 0;
        this.mAllCardsReady = false;
        this.mNeedSwitchCommrilMode = false;
        this.mWaitingSwitchCommrilMode = false;
        setCardHotPlugState(HotplugState.STATE_PLUG_OUT);
    }

    private void disposeCardStatus() {
        logd("disposeCardStatus");
        disposeLocalCardStatus();
        for (int i = 0; i < this.mCis.length; i++) {
            this.mRatCombineMode[i] = -1;
            this.mRadioOn[i] = false;
        }
        this.mCdmaSide = -1;
        HwAllInOneController.getInstance().disposeCardStatus(false);
        HwAllInOneController.getInstance().setCommrilRestartRild(true);
    }

    private int getUserSwitchDualCardSlots() {
        int subscription = 0;
        try {
            subscription = System.getInt(this.mContext.getContentResolver(), "switch_dual_card_slots");
        } catch (SettingNotFoundException e) {
            loge("Settings Exception Reading Dual Sim Switch Dual Card Slots Values");
        }
        return subscription;
    }

    private boolean isBalongSimSynced() {
        int currSlot = getUserSwitchDualCardSlots();
        logd("currSlot  = " + currSlot + ", mBalongSimSlot = " + this.mBalongSimSlot);
        return currSlot == this.mBalongSimSlot;
    }

    public void decPollingCount() {
        if (this.mPollingCount > 0) {
            this.mPollingCount--;
            logd("Dec, mPollingCount = " + this.mPollingCount);
            return;
        }
        loge("polling count already 0, can't dec!");
    }

    public void incPollingCount(int step) {
        this.mPollingCount += step;
        logd("Inc, mPollingCount = " + this.mPollingCount);
    }

    public boolean setMainSlot(int mainSlot, boolean triggerSwith) {
        if (this.mMainSlot == mainSlot && !HwAllInOneController.IS_HISI_DSDX) {
            logd("mainSlot is " + mainSlot + ", not change, do nothing!");
            return false;
        } else if (mainSlot == -1) {
            logd("mainSlot is -1, invalid parameter, do nothing!");
            return false;
        } else if (this.mIsOngoingRestartRild) {
            logd("Ready to restart rild, don't change mainSlot, wait till rild restarted!");
            return false;
        } else {
            this.mMainSlot = mainSlot;
            logi("setMainSlot " + mainSlot);
            if (triggerSwith) {
                trySwitchCommrilMode();
            }
            return true;
        }
    }

    public void waitToRestartRild() {
        logd("waitToRestartRild");
        decPollingCount();
        removeMessages(1);
        sendEmptyMessage(1);
    }

    public boolean getWaitingSwitchCommrilMode() {
        logd("getWaitingSwitchCommrilMode " + this.mWaitingSwitchCommrilMode);
        return this.mWaitingSwitchCommrilMode;
    }

    public boolean getNeedSwitchCommrilMode() {
        logd("getNeedSwitchCommrilMode " + this.mNeedSwitchCommrilMode);
        return this.mNeedSwitchCommrilMode;
    }

    private boolean isCardTrayOut(int SlotId) {
        Throwable th;
        boolean z = true;
        byte[] cardTrayState = new byte[4];
        FileInputStream fis = null;
        try {
            FileInputStream fis2 = new FileInputStream(CARDTRAY_STATE_FILE);
            try {
                int length = fis2.read(cardTrayState, 0, 4);
                fis2.close();
                if (length < 4) {
                    loge("isCardTrayOut read byte fail.");
                    if (fis2 != null) {
                        try {
                            fis2.close();
                        } catch (IOException e) {
                            return false;
                        }
                    }
                    return false;
                }
                if (fis2 != null) {
                    try {
                        fis2.close();
                    } catch (IOException e2) {
                        return false;
                    }
                }
                if (SlotId < 0 || SlotId > 1) {
                    return false;
                }
                if (cardTrayState[(SlotId * 2) + 1] != (byte) 0) {
                    z = false;
                }
                return z;
            } catch (IOException e3) {
                fis = fis2;
                try {
                    loge("isCardTrayOut Exception");
                    if (fis != null) {
                        try {
                            fis.close();
                        } catch (IOException e4) {
                            return false;
                        }
                    }
                    return false;
                } catch (Throwable th2) {
                    th = th2;
                    if (fis != null) {
                        try {
                            fis.close();
                        } catch (IOException e5) {
                            return false;
                        }
                    }
                    throw th;
                }
            } catch (Throwable th3) {
                th = th3;
                fis = fis2;
                if (fis != null) {
                    fis.close();
                }
                throw th;
            }
        } catch (IOException e6) {
            loge("isCardTrayOut Exception");
            if (fis != null) {
                fis.close();
            }
            return false;
        }
    }

    public boolean isOngoingRestartRild() {
        logd("isOngoingRestartRild " + this.mIsOngoingRestartRild);
        return this.mIsOngoingRestartRild;
    }

    private static void logd(String message) {
        Rlog.d(TAG, message);
    }

    private static void logi(String message) {
        Rlog.i(TAG, message);
    }

    private static void loge(String message) {
        Rlog.e(TAG, message);
    }

    public boolean isGetCdmaModeDone() {
        if (IS_HISI_CDMA_SUPPORTED) {
            return this.mGetCdmaSideDone;
        }
        return true;
    }

    private void vsimAdjustNeedSwitchCommrilMode() {
        if (HwVSimUtils.isVSimEnabled() || HwVSimUtils.isVSimCauseCardReload() || HwVSimUtils.isSubActivationUpdate() || !HwVSimUtils.isAllowALSwitch()) {
            logd("vsim on sub, set mNeedSwitchCommrilMode as false");
            this.mNeedSwitchCommrilMode = false;
        }
    }
}
