package com.android.internal.telephony.vsim.process;

import android.os.AsyncResult;
import android.os.Message;
import com.android.internal.telephony.uicc.IccCardStatus;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;
import com.android.internal.telephony.vsim.HwVSimConstants;
import com.android.internal.telephony.vsim.HwVSimController;
import com.android.internal.telephony.vsim.HwVSimController.EnableParam;
import com.android.internal.telephony.vsim.HwVSimEventReport.VSimEventInfoUtils;
import com.android.internal.telephony.vsim.HwVSimLog;
import com.android.internal.telephony.vsim.HwVSimModemAdapter;
import com.android.internal.telephony.vsim.HwVSimRequest;
import com.android.internal.telephony.vsim.HwVSimUtilsInner;

public class HwVSimEWorkProcessor extends HwVSimWorkProcessor {
    protected static final int GET_ICC_CARD_STATUS_RETRY_TIMES = 3;
    public static final String LOG_TAG = "VSimEWorkProcessor";
    protected int getIccCardStatusTimes = 0;

    public static HwVSimEWorkProcessor create(HwVSimController controller, HwVSimModemAdapter modemAdapter, HwVSimRequest request) {
        if (controller == null || !controller.isDirectProcess()) {
            return new HwVSimEWorkProcessor(controller, modemAdapter, request);
        }
        return new HwVSimEDWorkProcessor(controller, modemAdapter, request);
    }

    public HwVSimEWorkProcessor(HwVSimController controller, HwVSimModemAdapter modemAdapter, HwVSimRequest request) {
        super(controller, modemAdapter, request);
    }

    public boolean processMessage(Message msg) {
        switch (msg.what) {
            case 2:
                onGetSimStateDone(msg);
                return true;
            case 41:
                onRadioPowerOffDone(msg);
                return true;
            case 42:
                onCardPowerOffDone(msg);
                return true;
            case 43:
                onSwitchSlotDone(msg);
                return true;
            case HwVSimConstants.EVENT_SET_TEE_DATA_READY_DONE /*44*/:
                onSetTeeDataReadyDone(msg);
                return true;
            case HwVSimConstants.EVENT_CARD_POWER_ON_DONE /*45*/:
                onCardPowerOnDone(msg);
                return true;
            case HwVSimConstants.EVENT_RADIO_POWER_ON_DONE /*46*/:
                onRadioPowerOnDone(msg);
                return true;
            case HwVSimConstants.EVENT_SET_ACTIVE_MODEM_MODE_DONE /*47*/:
                onSetActiveModemModeDone(msg);
                return true;
            case HwVSimConstants.EVENT_GET_PREFERRED_NETWORK_TYPE_DONE /*48*/:
                onGetPreferredNetworkTypeDone(msg);
                return true;
            case HwVSimConstants.EVENT_SET_PREFERRED_NETWORK_TYPE_DONE /*49*/:
                onSetPreferredNetworkTypeDone(msg);
                return true;
            case HwVSimConstants.EVENT_ENABLE_VSIM_DONE /*51*/:
                onEnableVSimDone(msg);
                return true;
            case HwVSimConstants.EVENT_GET_ICC_STATUS_DONE /*79*/:
                onGetIccCardStatusDone(msg);
                return true;
            default:
                return false;
        }
    }

    public void doProcessException(AsyncResult ar, HwVSimRequest request) {
        doEnableProcessException(ar, request, Integer.valueOf(3));
    }

    protected void logd(String s) {
        HwVSimLog.VSimLogD(LOG_TAG, s);
    }

    protected void onSwitchSlotDone(Message msg) {
        logd("onSwitchSlotDone");
        VSimEventInfoUtils.setCauseType(this.mVSimController.mEventInfo, 5);
        AsyncResult ar = msg.obj;
        if (isAsyncResultValid(ar)) {
            this.mModemAdapter.onSwitchSlotDone(this, ar);
            this.mVSimController.clearAllMarkForCardReload();
            this.mVSimController.setBlockPinFlag(false);
            HwVSimRequest request = ar.userObj;
            int subId = request.mSubId;
            if (isSwapProcess()) {
                logd("mainSlot = " + request.getMainSlot());
            } else if (isCrossProcess()) {
                int mainSlot = request.getExpectSlot();
                request.setMainSlot(mainSlot);
                logd("update mainSlot to " + mainSlot);
                this.mVSimController.setUserSwitchDualCardSlots(mainSlot);
            }
            EnableParam arg = request.getArgument();
            EnableParam param = null;
            if (arg != null) {
                param = arg;
            }
            if (param == null) {
                doProcessException(ar, request);
                return;
            }
            int result = this.mVSimController.writeVsimToTA(param.imsi, param.cardType, param.apnType, param.challenge, param.taPath, param.vsimLoc, 0);
            if (result != 0) {
                doEnableProcessException(ar, request, Integer.valueOf(result));
                return;
            }
            if (HwVSimUtilsInner.isPlatformRealTripple()) {
                this.mVSimController.setVSimCurCardType(this.mVSimController.getCardTypeFromEnableParam(request));
                this.mModemAdapter.cardPowerOn(this, this.mRequest, 2, 11);
            } else {
                this.mModemAdapter.setTeeDataReady(this, this.mRequest, 2);
            }
        }
    }

    protected void onCardPowerOffDone(Message msg) {
        logd("onCardPowerOffDone");
        VSimEventInfoUtils.setCauseType(this.mVSimController.mEventInfo, 4);
        AsyncResult ar = msg.obj;
        if (isAsyncResultValidForRequestNotSupport(ar)) {
            HwVSimRequest request = ar.userObj;
            int subId = request.mSubId;
            logd("onCardPowerOffDone, subId: " + subId);
            int subCount = request.getSubCount();
            for (int i = 0; i < subCount; i++) {
                if (subId == request.getSubIdByIndex(i)) {
                    request.setCardOnOffMark(i, false);
                }
            }
            if (isAllMarkClear(request)) {
                getIccCardStatus(request, subId);
            }
        }
    }

    protected void onSetTeeDataReadyDone(Message msg) {
        logd("onSetTeeDataReadyDone");
        VSimEventInfoUtils.setCauseType(this.mVSimController.mEventInfo, 6);
        AsyncResult ar = msg.obj;
        if (isAsyncResultValid(ar, Integer.valueOf(4))) {
            this.mVSimController.setVSimCurCardType(this.mVSimController.getCardTypeFromEnableParam(ar.userObj));
            this.mModemAdapter.cardPowerOn(this, this.mRequest, 2, 11);
        }
    }

    protected void onCardPowerOnDone(Message msg) {
        logd("onCardPowerOnDone");
        VSimEventInfoUtils.setCauseType(this.mVSimController.mEventInfo, 7);
        AsyncResult ar = msg.obj;
        if (isAsyncResultValidForCardPowerOn(ar)) {
            logd("onCardPowerOnDone : subId  = " + ar.userObj.mSubId);
            this.mModemAdapter.setActiveModemMode(this, this.mRequest, 2);
            if (HwVSimUtilsInner.isPlatformRealTripple()) {
                this.mVSimController.allowData(2);
            }
        }
    }

    protected void onSetActiveModemModeDone(Message msg) {
        logd("onSetActiveModemModeDone");
        VSimEventInfoUtils.setCauseType(this.mVSimController.mEventInfo, 8);
        AsyncResult ar = msg.obj;
        if (isAsyncResultValid(ar)) {
            this.mModemAdapter.getPreferredNetworkType(this, this.mRequest, ar.userObj.mSubId);
        }
    }

    protected void onSetPreferredNetworkTypeDone(Message msg) {
        logd("onSetPreferredNetworkTypeDone");
        VSimEventInfoUtils.setCauseType(this.mVSimController.mEventInfo, 10);
        AsyncResult ar = msg.obj;
        if (isAsyncResultValid(ar)) {
            this.mModemAdapter.radioPowerOn(this, this.mRequest, ar.userObj.mSubId);
        }
    }

    protected void onRadioPowerOnDone(Message msg) {
        logd("onRadioPowerOnDone");
        VSimEventInfoUtils.setCauseType(this.mVSimController.mEventInfo, 11);
        if (isAsyncResultValid(msg.obj)) {
            enableVSimDone();
        }
    }

    protected void onEnableVSimDone(Message msg) {
        logd("onEnableVSimDone");
        VSimEventInfoUtils.setCauseType(this.mVSimController.mEventInfo, 12);
        notifyResult(this.mRequest, Integer.valueOf(0));
        if (this.mVSimController.isDirectProcess()) {
            this.mModemAdapter.onEDWorkTransitionState(this);
        } else {
            transitionToState(4);
        }
    }

    protected void enableVSimDone() {
        logd("enableVSimDone");
        Message onCompleted = obtainMessage(51, this.mRequest);
        AsyncResult.forMessage(onCompleted);
        onCompleted.sendToTarget();
    }

    protected boolean isAsyncResultValidForCardPowerOn(AsyncResult ar) {
        if (ar == null) {
            doProcessException(null, null);
            return false;
        }
        HwVSimRequest request = ar.userObj;
        if (request == null) {
            return false;
        }
        if (ar.exception == null || request.mSubId != 2) {
            return true;
        }
        doEnableProcessException(ar, request, Integer.valueOf(2));
        return false;
    }

    protected void onGetIccCardStatusDone(Message msg) {
        AsyncResult ar = msg.obj;
        if (isAsyncResultValidForRequestNotSupport(ar)) {
            HwVSimRequest request = ar.userObj;
            int subId = request.mSubId;
            IccCardStatus status = ar.result;
            CardState cardState = status == null ? CardState.CARDSTATE_ERROR : status.mCardState;
            logd("onGetIccCardStatusDone:mCardState[" + subId + "]=" + cardState);
            if (cardState == CardState.CARDSTATE_ABSENT || this.getIccCardStatusTimes >= 3) {
                logd("onGetIccCardStatusDone:->switchSimSlot");
                this.mModemAdapter.switchSimSlot(this, this.mRequest);
                this.getIccCardStatusTimes = 0;
            } else {
                this.getIccCardStatusTimes++;
                logd("onGetIccCardStatusDone: retry getIccCardStatus,Times=" + this.getIccCardStatusTimes);
                this.mModemAdapter.getIccCardStatus(this, request, subId);
            }
        }
    }

    protected void getIccCardStatus(HwVSimRequest request, int subId) {
        logd("onCardPowerOffDone,getIccCardStatus,wait card status is absent");
        this.getIccCardStatusTimes = 0;
        this.mModemAdapter.getIccCardStatus(this, request, subId);
    }
}
