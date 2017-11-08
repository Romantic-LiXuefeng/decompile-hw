package com.android.internal.telephony.cat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.content.res.Resources.NotFoundException;
import android.net.NetworkInfo;
import android.os.AsyncResult;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemProperties;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.HuaweiTelephonyConfigs;
import com.android.internal.telephony.ProxyController;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.cat.AppInterface.CommandType;
import com.android.internal.telephony.cat.CatCmdMessage.ChannelSettings;
import com.android.internal.telephony.cat.Duration.TimeUnit;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccRefreshResponse;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Locale;

public class CatService extends AbstractCatService {
    private static final /* synthetic */ int[] -com-android-internal-telephony-cat-AppInterface$CommandTypeSwitchesValues = null;
    private static final /* synthetic */ int[] -com-android-internal-telephony-cat-ResultCodeSwitchesValues = null;
    private static final boolean DBG = false;
    private static final int DEFAULT_SEND_RETRY_DELAY = 20000;
    private static final int DEV_ID_DISPLAY = 2;
    private static final int DEV_ID_KEYPAD = 1;
    private static final int DEV_ID_NETWORK = 131;
    private static final int DEV_ID_TERMINAL = 130;
    private static final int DEV_ID_UICC = 129;
    private static final int EVENT_LENGTH = 10;
    private static final int EVENT_SEND_ENVELOPE_RESULT = 100;
    private static final int EVENT_SEND_ENVELOPE_RETRY = 99;
    private static final int EVENT_SEND_RESPONSE_WAIT = 40;
    private static final int EVENT_SEND_RESPONSE_WAIT_TIME = 60;
    private static final int ID_BROWSER_TERMINATION_EVENT = 5;
    private static final int ID_BROWSING_STATUS_EVENT = 6;
    private static final int ID_CHANNEL_STATUS_EVENT = 0;
    private static final int ID_DATA_AVAILABLE_EVENT = 1;
    private static final int ID_IDLE_SCREEN_AVAILABLE_EVENT = 3;
    private static final int ID_LANGUAGE_SELECTION_EVENT = 4;
    private static final int ID_USER_ACTIVITY_EVENT = 2;
    private static final int MAX_SEND_RETRIES = 5;
    protected static final int MSG_ID_ALPHA_NOTIFY = 9;
    protected static final int MSG_ID_CALL_SETUP = 4;
    protected static final int MSG_ID_EVENT_NOTIFY = 3;
    protected static final int MSG_ID_ICC_CHANGED = 8;
    private static final int MSG_ID_ICC_RECORDS_LOADED = 20;
    private static final int MSG_ID_ICC_REFRESH = 30;
    protected static final int MSG_ID_PROACTIVE_COMMAND = 2;
    static final int MSG_ID_REFRESH = 5;
    static final int MSG_ID_RESPONSE = 6;
    static final int MSG_ID_RIL_MSG_DECODED = 10;
    protected static final int MSG_ID_SESSION_END = 1;
    static final int MSG_ID_SIM_READY = 7;
    static final String STK_DEFAULT = "Default Message";
    private static final boolean isHideStkpop = SystemProperties.getBoolean("ro.hwpp.hidestk", false);
    private static IccRecords mIccRecords;
    private static UiccCardApplication mUiccApplication;
    private static CatService[] sInstance = null;
    private static final Object sInstanceLock = new Object();
    private BipProxy mBipProxy = null;
    private CardState mCardState = CardState.CARDSTATE_ABSENT;
    private CommandsInterface mCmdIf;
    private Context mContext;
    private CatCmdMessage mCurrntCmd = null;
    private DefaultBearerStateReceiver mDefaultBearerStateReceiver = null;
    private int[] mEvents = new int[10];
    private HandlerThread mHandlerThread;
    private boolean mIsWifiConnected = false;
    private CatCmdMessage mMenuCmd = null;
    private RilMessageDecoder mMsgDecoder = null;
    private int mRetryCount = 1;
    private String mRetryHexString = null;
    private int mSlotId;
    private boolean mStkAppInstalled = false;
    private UiccController mUiccController;

    class DefaultBearerStateReceiver extends BroadcastReceiver {
        private Context mContext;
        private IntentFilter mFilter;
        private boolean mIsRegistered = false;

        public DefaultBearerStateReceiver(Context context) {
            this.mContext = context;
            this.mFilter = new IntentFilter();
            this.mFilter.addAction("android.net.wifi.STATE_CHANGE");
        }

        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                CatLog.d((Object) this, "Received broadcast: intent is null");
            } else if (intent.getAction() == null) {
                CatLog.d((Object) this, "Received broadcast: Action is null");
            } else if ("android.net.wifi.STATE_CHANGE".equals(intent.getAction())) {
                boolean oldIsWifiConnected = CatService.this.mIsWifiConnected;
                NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra("networkInfo");
                CatService.this.mIsWifiConnected = networkInfo != null ? networkInfo.isConnected() : false;
                if (oldIsWifiConnected != CatService.this.mIsWifiConnected) {
                    CatLog.d((Object) this, "WifiManager.NETWORK_STATE_CHANGED_ACTION: mIsWifiConnected=" + CatService.this.mIsWifiConnected);
                }
            }
        }

        public void startListening() {
            if (!this.mIsRegistered) {
                this.mContext.registerReceiver(this, this.mFilter);
                this.mIsRegistered = true;
            }
        }

        public void stopListening() {
            if (this.mIsRegistered) {
                this.mContext.unregisterReceiver(this);
                this.mIsRegistered = false;
            }
        }
    }

    private static /* synthetic */ int[] -getcom-android-internal-telephony-cat-AppInterface$CommandTypeSwitchesValues() {
        if (-com-android-internal-telephony-cat-AppInterface$CommandTypeSwitchesValues != null) {
            return -com-android-internal-telephony-cat-AppInterface$CommandTypeSwitchesValues;
        }
        int[] iArr = new int[CommandType.values().length];
        try {
            iArr[CommandType.CLOSE_CHANNEL.ordinal()] = 1;
        } catch (NoSuchFieldError e) {
        }
        try {
            iArr[CommandType.DISPLAY_TEXT.ordinal()] = 2;
        } catch (NoSuchFieldError e2) {
        }
        try {
            iArr[CommandType.GET_CHANNEL_STATUS.ordinal()] = 3;
        } catch (NoSuchFieldError e3) {
        }
        try {
            iArr[CommandType.GET_INKEY.ordinal()] = 4;
        } catch (NoSuchFieldError e4) {
        }
        try {
            iArr[CommandType.GET_INPUT.ordinal()] = 5;
        } catch (NoSuchFieldError e5) {
        }
        try {
            iArr[CommandType.LANGUAGE_NOTIFICATION.ordinal()] = 6;
        } catch (NoSuchFieldError e6) {
        }
        try {
            iArr[CommandType.LAUNCH_BROWSER.ordinal()] = 7;
        } catch (NoSuchFieldError e7) {
        }
        try {
            iArr[CommandType.OPEN_CHANNEL.ordinal()] = 8;
        } catch (NoSuchFieldError e8) {
        }
        try {
            iArr[CommandType.PLAY_TONE.ordinal()] = 9;
        } catch (NoSuchFieldError e9) {
        }
        try {
            iArr[CommandType.PROVIDE_LOCAL_INFORMATION.ordinal()] = 10;
        } catch (NoSuchFieldError e10) {
        }
        try {
            iArr[CommandType.RECEIVE_DATA.ordinal()] = 11;
        } catch (NoSuchFieldError e11) {
        }
        try {
            iArr[CommandType.REFRESH.ordinal()] = 12;
        } catch (NoSuchFieldError e12) {
        }
        try {
            iArr[CommandType.SELECT_ITEM.ordinal()] = 13;
        } catch (NoSuchFieldError e13) {
        }
        try {
            iArr[CommandType.SEND_DATA.ordinal()] = 14;
        } catch (NoSuchFieldError e14) {
        }
        try {
            iArr[CommandType.SEND_DTMF.ordinal()] = 15;
        } catch (NoSuchFieldError e15) {
        }
        try {
            iArr[CommandType.SEND_SMS.ordinal()] = 16;
        } catch (NoSuchFieldError e16) {
        }
        try {
            iArr[CommandType.SEND_SS.ordinal()] = 17;
        } catch (NoSuchFieldError e17) {
        }
        try {
            iArr[CommandType.SEND_USSD.ordinal()] = 18;
        } catch (NoSuchFieldError e18) {
        }
        try {
            iArr[CommandType.SET_POLL_INTERVALL.ordinal()] = 40;
        } catch (NoSuchFieldError e19) {
        }
        try {
            iArr[CommandType.SET_UP_CALL.ordinal()] = 19;
        } catch (NoSuchFieldError e20) {
        }
        try {
            iArr[CommandType.SET_UP_EVENT_LIST.ordinal()] = 20;
        } catch (NoSuchFieldError e21) {
        }
        try {
            iArr[CommandType.SET_UP_IDLE_MODE_TEXT.ordinal()] = 21;
        } catch (NoSuchFieldError e22) {
        }
        try {
            iArr[CommandType.SET_UP_MENU.ordinal()] = 22;
        } catch (NoSuchFieldError e23) {
        }
        -com-android-internal-telephony-cat-AppInterface$CommandTypeSwitchesValues = iArr;
        return iArr;
    }

    private static /* synthetic */ int[] -getcom-android-internal-telephony-cat-ResultCodeSwitchesValues() {
        if (-com-android-internal-telephony-cat-ResultCodeSwitchesValues != null) {
            return -com-android-internal-telephony-cat-ResultCodeSwitchesValues;
        }
        int[] iArr = new int[ResultCode.values().length];
        try {
            iArr[ResultCode.ACCESS_TECH_UNABLE_TO_PROCESS.ordinal()] = 40;
        } catch (NoSuchFieldError e) {
        }
        try {
            iArr[ResultCode.BACKWARD_MOVE_BY_USER.ordinal()] = 1;
        } catch (NoSuchFieldError e2) {
        }
        try {
            iArr[ResultCode.BEYOND_TERMINAL_CAPABILITY.ordinal()] = 41;
        } catch (NoSuchFieldError e3) {
        }
        try {
            iArr[ResultCode.BIP_ERROR.ordinal()] = 42;
        } catch (NoSuchFieldError e4) {
        }
        try {
            iArr[ResultCode.CMD_DATA_NOT_UNDERSTOOD.ordinal()] = 43;
        } catch (NoSuchFieldError e5) {
        }
        try {
            iArr[ResultCode.CMD_NUM_NOT_KNOWN.ordinal()] = 44;
        } catch (NoSuchFieldError e6) {
        }
        try {
            iArr[ResultCode.CMD_TYPE_NOT_UNDERSTOOD.ordinal()] = 45;
        } catch (NoSuchFieldError e7) {
        }
        try {
            iArr[ResultCode.CONTRADICTION_WITH_TIMER.ordinal()] = 46;
        } catch (NoSuchFieldError e8) {
        }
        try {
            iArr[ResultCode.FRAMES_ERROR.ordinal()] = 47;
        } catch (NoSuchFieldError e9) {
        }
        try {
            iArr[ResultCode.HELP_INFO_REQUIRED.ordinal()] = 2;
        } catch (NoSuchFieldError e10) {
        }
        try {
            iArr[ResultCode.LAUNCH_BROWSER_ERROR.ordinal()] = 3;
        } catch (NoSuchFieldError e11) {
        }
        try {
            iArr[ResultCode.MMS_ERROR.ordinal()] = 48;
        } catch (NoSuchFieldError e12) {
        }
        try {
            iArr[ResultCode.MMS_TEMPORARY.ordinal()] = 49;
        } catch (NoSuchFieldError e13) {
        }
        try {
            iArr[ResultCode.MULTI_CARDS_CMD_ERROR.ordinal()] = 50;
        } catch (NoSuchFieldError e14) {
        }
        try {
            iArr[ResultCode.NAA_CALL_CONTROL_TEMPORARY.ordinal()] = 51;
        } catch (NoSuchFieldError e15) {
        }
        try {
            iArr[ResultCode.NETWORK_CRNTLY_UNABLE_TO_PROCESS.ordinal()] = 52;
        } catch (NoSuchFieldError e16) {
        }
        try {
            iArr[ResultCode.NO_RESPONSE_FROM_USER.ordinal()] = 4;
        } catch (NoSuchFieldError e17) {
        }
        try {
            iArr[ResultCode.OK.ordinal()] = 5;
        } catch (NoSuchFieldError e18) {
        }
        try {
            iArr[ResultCode.PRFRMD_ICON_NOT_DISPLAYED.ordinal()] = 6;
        } catch (NoSuchFieldError e19) {
        }
        try {
            iArr[ResultCode.PRFRMD_LIMITED_SERVICE.ordinal()] = 7;
        } catch (NoSuchFieldError e20) {
        }
        try {
            iArr[ResultCode.PRFRMD_MODIFIED_BY_NAA.ordinal()] = 8;
        } catch (NoSuchFieldError e21) {
        }
        try {
            iArr[ResultCode.PRFRMD_NAA_NOT_ACTIVE.ordinal()] = 9;
        } catch (NoSuchFieldError e22) {
        }
        try {
            iArr[ResultCode.PRFRMD_TONE_NOT_PLAYED.ordinal()] = 10;
        } catch (NoSuchFieldError e23) {
        }
        try {
            iArr[ResultCode.PRFRMD_WITH_ADDITIONAL_EFS_READ.ordinal()] = 11;
        } catch (NoSuchFieldError e24) {
        }
        try {
            iArr[ResultCode.PRFRMD_WITH_MISSING_INFO.ordinal()] = 12;
        } catch (NoSuchFieldError e25) {
        }
        try {
            iArr[ResultCode.PRFRMD_WITH_MODIFICATION.ordinal()] = 13;
        } catch (NoSuchFieldError e26) {
        }
        try {
            iArr[ResultCode.PRFRMD_WITH_PARTIAL_COMPREHENSION.ordinal()] = 14;
        } catch (NoSuchFieldError e27) {
        }
        try {
            iArr[ResultCode.REQUIRED_VALUES_MISSING.ordinal()] = 53;
        } catch (NoSuchFieldError e28) {
        }
        try {
            iArr[ResultCode.SMS_RP_ERROR.ordinal()] = 54;
        } catch (NoSuchFieldError e29) {
        }
        try {
            iArr[ResultCode.SS_RETURN_ERROR.ordinal()] = 55;
        } catch (NoSuchFieldError e30) {
        }
        try {
            iArr[ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS.ordinal()] = 15;
        } catch (NoSuchFieldError e31) {
        }
        try {
            iArr[ResultCode.UICC_SESSION_TERM_BY_USER.ordinal()] = 16;
        } catch (NoSuchFieldError e32) {
        }
        try {
            iArr[ResultCode.USER_CLEAR_DOWN_CALL.ordinal()] = 56;
        } catch (NoSuchFieldError e33) {
        }
        try {
            iArr[ResultCode.USER_NOT_ACCEPT.ordinal()] = 17;
        } catch (NoSuchFieldError e34) {
        }
        try {
            iArr[ResultCode.USIM_CALL_CONTROL_PERMANENT.ordinal()] = 57;
        } catch (NoSuchFieldError e35) {
        }
        try {
            iArr[ResultCode.USSD_RETURN_ERROR.ordinal()] = 58;
        } catch (NoSuchFieldError e36) {
        }
        try {
            iArr[ResultCode.USSD_SS_SESSION_TERM_BY_USER.ordinal()] = 59;
        } catch (NoSuchFieldError e37) {
        }
        -com-android-internal-telephony-cat-ResultCodeSwitchesValues = iArr;
        return iArr;
    }

    private CatService(CommandsInterface ci, UiccCardApplication ca, IccRecords ir, Context context, IccFileHandler fh, UiccCard ic, int slotId) {
        if (ci == null || ca == null || ir == null || context == null || fh == null || ic == null) {
            throw new NullPointerException("Service: Input parameters must not be null");
        }
        this.mCmdIf = ci;
        this.mContext = context;
        this.mSlotId = slotId;
        this.mHandlerThread = new HandlerThread("Cat Telephony service" + slotId);
        this.mHandlerThread.start();
        this.mMsgDecoder = RilMessageDecoder.getInstance(this, fh, slotId);
        if (this.mMsgDecoder == null) {
            CatLog.d((Object) this, "Null RilMessageDecoder instance");
            return;
        }
        this.mMsgDecoder.start();
        this.mCmdIf.setOnCatSessionEnd(this, 1, null);
        this.mCmdIf.setOnCatProactiveCmd(this, 2, null);
        this.mCmdIf.setOnCatEvent(this, 3, null);
        this.mCmdIf.setOnCatCallSetUp(this, 4, null);
        this.mCmdIf.registerForIccRefresh(this, 30, null);
        this.mCmdIf.setOnCatCcAlphaNotify(this, 9, null);
        mIccRecords = ir;
        mUiccApplication = ca;
        mIccRecords.registerForRecordsLoaded(this, 20, null);
        CatLog.d((Object) this, "registerForRecordsLoaded slotid=" + this.mSlotId);
        this.mUiccController = UiccController.getInstance();
        this.mUiccController.registerForIccChanged(this, 8, null);
        this.mBipProxy = new BipProxy(this, this.mCmdIf, this.mContext);
        this.mDefaultBearerStateReceiver = new DefaultBearerStateReceiver(context);
        this.mDefaultBearerStateReceiver.startListening();
        this.mStkAppInstalled = isStkAppInstalled();
        CatLog.d((Object) this, "Running CAT service on Slotid: " + this.mSlotId + ". STK app installed:" + this.mStkAppInstalled);
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public static CatService getInstance(CommandsInterface ci, Context context, UiccCard ic, int slotId) {
        UiccCardApplication uiccCardApplication = null;
        IccFileHandler iccFileHandler = null;
        IccRecords ir = null;
        if (ic != null) {
            uiccCardApplication = ic.getApplicationIndex(0);
            if (uiccCardApplication != null) {
                iccFileHandler = uiccCardApplication.getIccFileHandler();
                ir = uiccCardApplication.getIccRecords();
            }
        }
        synchronized (sInstanceLock) {
            if (sInstance == null) {
                int simCount = TelephonyManager.getDefault().getSimCount();
                sInstance = new CatService[simCount];
                for (int i = 0; i < simCount; i++) {
                    sInstance[i] = null;
                }
            }
            if (sInstance[slotId] == null) {
                if (ci == null || uiccCardApplication == null || ir == null || context == null || iccFileHandler == null || ic == null) {
                } else {
                    sInstance[slotId] = new CatService(ci, uiccCardApplication, ir, context, iccFileHandler, ic, slotId);
                }
            } else if (ir != null) {
                if (mIccRecords != ir) {
                    if (mIccRecords != null) {
                        mIccRecords.unregisterForRecordsLoaded(sInstance[slotId]);
                    }
                    mIccRecords = ir;
                    mUiccApplication = uiccCardApplication;
                    mIccRecords.registerForRecordsLoaded(sInstance[slotId], 20, null);
                    CatLog.d(sInstance[slotId], "registerForRecordsLoaded slotid=" + slotId + " instance:" + sInstance[slotId]);
                }
            }
            CatService catService = sInstance[slotId];
            return catService;
        }
    }

    public void dispose() {
        synchronized (sInstanceLock) {
            CatLog.d((Object) this, "Disposing CatService object");
            mIccRecords.unregisterForRecordsLoaded(this);
            broadcastCardStateAndIccRefreshResp(CardState.CARDSTATE_ABSENT, null);
            this.mCmdIf.unSetOnCatSessionEnd(this);
            this.mCmdIf.unSetOnCatProactiveCmd(this);
            this.mCmdIf.unSetOnCatEvent(this);
            this.mCmdIf.unSetOnCatCallSetUp(this);
            this.mCmdIf.unSetOnCatCcAlphaNotify(this);
            this.mCmdIf.unregisterForIccRefresh(this);
            if (this.mUiccController != null) {
                this.mUiccController.unregisterForIccChanged(this);
                this.mUiccController = null;
            }
            this.mDefaultBearerStateReceiver.stopListening();
            this.mMsgDecoder.dispose();
            this.mMsgDecoder = null;
            this.mHandlerThread.quit();
            this.mHandlerThread = null;
            removeCallbacksAndMessages(null);
            if (sInstance != null) {
                if (SubscriptionManager.isValidSlotId(this.mSlotId)) {
                    sInstance[this.mSlotId] = null;
                } else {
                    CatLog.d((Object) this, "error: invaild slot id: " + this.mSlotId);
                }
            }
        }
    }

    protected void finalize() {
        CatLog.d((Object) this, "Service finalized");
    }

    private void handleRilMsg(RilMessage rilMsg) {
        if (rilMsg != null) {
            CommandParams cmdParams;
            switch (rilMsg.mId) {
                case 1:
                    handleSessionEnd();
                    break;
                case 2:
                    try {
                        cmdParams = (CommandParams) rilMsg.mData;
                        if (cmdParams != null) {
                            if (rilMsg.mResCode != ResultCode.OK && (ResultCode.PRFRMD_ICON_NOT_DISPLAYED != rilMsg.mResCode || CommandType.SET_UP_MENU != cmdParams.getCommandType())) {
                                sendTerminalResponse(cmdParams.mCmdDet, rilMsg.mResCode, false, 0, null);
                                break;
                            } else {
                                handleCommand(cmdParams, true);
                                break;
                            }
                        }
                    } catch (ClassCastException e) {
                        CatLog.d((Object) this, "Fail to parse proactive command");
                        if (this.mCurrntCmd != null) {
                            sendTerminalResponse(this.mCurrntCmd.mCmdDet, ResultCode.CMD_DATA_NOT_UNDERSTOOD, false, 0, null);
                            break;
                        }
                    }
                    break;
                case 3:
                    if ((rilMsg.mData instanceof CommandParams) && rilMsg.mResCode == ResultCode.OK) {
                        handleCommand(rilMsg.mData, false);
                        break;
                    }
                case 5:
                    cmdParams = rilMsg.mData;
                    if (cmdParams != null) {
                        handleCommand(cmdParams, false);
                        break;
                    }
                    break;
            }
        }
    }

    private boolean isSupportedSetupEventCommand(CatCmdMessage cmdMsg) {
        boolean flag = true;
        for (int eventVal : cmdMsg.getSetEventList().eventList) {
            CatLog.d((Object) this, "Event: " + eventVal);
            switch (eventVal) {
                case 5:
                case 7:
                    break;
                default:
                    flag = false;
                    break;
            }
        }
        return flag;
    }

    private void checkSetupEventCommand(CatCmdMessage cmdMsg) {
        for (int i = 0; i < 10; i++) {
            this.mEvents[i] = 0;
        }
        for (int eventval : cmdMsg.getSetEventList().eventList) {
            CatLog.d((Object) this, "Event: " + eventval);
            switch (eventval) {
                case 4:
                    CatLog.d((Object) this, "USER_ACTIVITY_EVENT is true");
                    this.mEvents[2] = 1;
                    break;
                case 5:
                    CatLog.d((Object) this, "IDLE_SCREEN_AVAILABLE_EVENT is true");
                    this.mEvents[3] = 1;
                    break;
                case 7:
                    CatLog.d((Object) this, "LANGUAGE_SELECTION_EVENT is true");
                    this.mEvents[4] = 1;
                    break;
                case 8:
                    CatLog.d((Object) this, "BROWSER_TERMINATION_EVENT is true");
                    this.mEvents[5] = 1;
                    break;
                case 9:
                    CatLog.d((Object) this, "DATA_AVAILABLE_EVENT is true");
                    this.mEvents[1] = 1;
                    break;
                case 10:
                    CatLog.d((Object) this, "CHANNEL_STATUS_EVENT is true");
                    this.mEvents[0] = 1;
                    break;
                case 15:
                    CatLog.d((Object) this, "BROWSING_STATUS_EVENT is true");
                    this.mEvents[6] = 1;
                    break;
                default:
                    break;
            }
        }
    }

    private void handleCommand(CommandParams cmdParams, boolean isProactiveCmd) {
        CatLog.d((Object) this, cmdParams.getCommandType().name());
        if (isProactiveCmd && this.mUiccController != null) {
            this.mUiccController.addCardLog("ProactiveCommand mSlotId=" + this.mSlotId + " cmdParams=" + cmdParams);
        }
        CatCmdMessage catCmdMessage = new CatCmdMessage(cmdParams);
        if (cmdParams.getCommandType() != null) {
            CatLog.d((Object) this, cmdParams.getCommandType().name());
            ResultCode resultCode;
            switch (-getcom-android-internal-telephony-cat-AppInterface$CommandTypeSwitchesValues()[cmdParams.getCommandType().ordinal()]) {
                case 1:
                case 8:
                case 11:
                case 14:
                    if (HuaweiTelephonyConfigs.isModemBipEnable()) {
                        BIPClientParams cmd = (BIPClientParams) cmdParams;
                        boolean noAlphaUsrCnf;
                        try {
                            noAlphaUsrCnf = this.mContext.getResources().getBoolean(17956997);
                        } catch (NotFoundException e) {
                            noAlphaUsrCnf = false;
                        }
                        if (cmd.mTextMsg.text != null || (!cmd.mHasAlphaId && !r23)) {
                            if (!this.mStkAppInstalled) {
                                CatLog.d((Object) this, "No STK application found.");
                                if (isProactiveCmd) {
                                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.BEYOND_TERMINAL_CAPABILITY, false, 0, null);
                                    return;
                                }
                            }
                            if (isProactiveCmd) {
                                if (!(cmdParams.getCommandType() == CommandType.CLOSE_CHANNEL || cmdParams.getCommandType() == CommandType.RECEIVE_DATA)) {
                                    if (cmdParams.getCommandType() == CommandType.SEND_DATA) {
                                    }
                                }
                                sendTerminalResponse(cmdParams.mCmdDet, ResultCode.OK, false, 0, null);
                                break;
                            }
                        }
                        CatLog.d((Object) this, "cmd " + cmdParams.getCommandType() + " with null alpha id");
                        if (isProactiveCmd) {
                            sendTerminalResponse(cmdParams.mCmdDet, ResultCode.OK, false, 0, null);
                        } else if (cmdParams.getCommandType() == CommandType.OPEN_CHANNEL) {
                            this.mCmdIf.handleCallSetupRequestFromSim(true, null);
                        }
                        return;
                    }
                    if (cmdParams.getCommandType() == CommandType.OPEN_CHANNEL) {
                        ChannelSettings newChannel = catCmdMessage.getChannelSettings();
                        if (newChannel != null) {
                            if (this.mBipProxy.canHandleNewChannel()) {
                                if (catCmdMessage.geTextMessage() != null && catCmdMessage.geTextMessage().responseNeeded) {
                                    CatLog.d((Object) this, "open channel text not null");
                                    break;
                                }
                            }
                            sendTerminalResponse(cmdParams.mCmdDet, ResultCode.BIP_ERROR, true, 1, new OpenChannelResponseData(newChannel.bufSize, null, newChannel.bearerDescription));
                            return;
                        }
                        sendTerminalResponse(cmdParams.mCmdDet, ResultCode.CMD_DATA_NOT_UNDERSTOOD, false, 0, null);
                        return;
                    }
                    catCmdMessage.setWifiConnectedFlag(this.mIsWifiConnected);
                    this.mCurrntCmd = catCmdMessage;
                    this.mBipProxy.handleBipCommand(catCmdMessage);
                    if (catCmdMessage.geTextMessage() == null || catCmdMessage.geTextMessage().text == null) {
                        return;
                    }
                    break;
                case 2:
                    if (isHideStkpop) {
                        int modemReboot = SystemProperties.getInt("gsm.stk.hide", 0);
                        CatLog.d((Object) this, "Receive DisplayTetxt modem reboot=" + modemReboot);
                        if (1 == modemReboot) {
                            CatLog.d((Object) this, "Modem reboot, avoid DisplayText");
                            SystemProperties.set("gsm.stk.hide", ProxyController.MODEM_0);
                            sendTerminalResponse(cmdParams.mCmdDet, ResultCode.BACKWARD_MOVE_BY_USER, false, 0, null);
                            return;
                        }
                    }
                    if (HuaweiTelephonyConfigs.isHisiPlatform() && !catCmdMessage.geTextMessage().responseNeeded) {
                        resultCode = cmdParams.mLoadIconFailed ? ResultCode.PRFRMD_ICON_NOT_DISPLAYED : ResultCode.OK;
                        Message sendTerminalResponseWait = obtainMessage(40);
                        sendTerminalResponseWait.obj = cmdParams.mCmdDet;
                        sendTerminalResponseWait.arg1 = resultCode.value();
                        sendMessageDelayed(sendTerminalResponseWait, 60);
                        break;
                    }
                case 3:
                    if (!HuaweiTelephonyConfigs.isModemBipEnable()) {
                        this.mCurrntCmd = catCmdMessage;
                        this.mBipProxy.handleBipCommand(catCmdMessage);
                        if (catCmdMessage.geTextMessage() == null || catCmdMessage.geTextMessage().text == null) {
                            return;
                        }
                    }
                    break;
                case 4:
                case 5:
                case 9:
                case 13:
                    break;
                case 6:
                    CatLog.d((Object) this, "handleProactiveCommand()  language = " + catCmdMessage.getLanguageNotification());
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.OK, false, 0, null);
                    return;
                case 7:
                    if ((((LaunchBrowserParams) cmdParams).mConfirmMsg.text != null && ((LaunchBrowserParams) cmdParams).mConfirmMsg.text.equals(STK_DEFAULT)) || ((LaunchBrowserParams) cmdParams).mConfirmMsg.text == null) {
                        ((LaunchBrowserParams) cmdParams).mConfirmMsg.text = this.mContext.getText(17040599).toString();
                        break;
                    }
                case 10:
                    switch (cmdParams.mCmdDet.commandQualifier) {
                        case 3:
                            sendTerminalResponse(cmdParams.mCmdDet, ResultCode.OK, false, 0, new DTTZResponseData(null));
                            break;
                        case 4:
                            sendTerminalResponse(cmdParams.mCmdDet, ResultCode.OK, false, 0, new LanguageResponseData(Locale.getDefault().getLanguage()));
                            break;
                        default:
                            sendTerminalResponse(cmdParams.mCmdDet, ResultCode.OK, false, 0, null);
                            break;
                    }
                    return;
                case 12:
                    cmdParams.mCmdDet.typeOfCommand = CommandType.SET_UP_IDLE_MODE_TEXT.value();
                    break;
                case 15:
                case 16:
                case 17:
                case 18:
                    if (((DisplayTextParams) cmdParams).mTextMsg.text != null && ((DisplayTextParams) cmdParams).mTextMsg.text.equals(STK_DEFAULT)) {
                        ((DisplayTextParams) cmdParams).mTextMsg.text = this.mContext.getText(17040598).toString();
                        break;
                    }
                case 19:
                    if (((CallSetupParams) cmdParams).mConfirmMsg.text != null && ((CallSetupParams) cmdParams).mConfirmMsg.text.equals(STK_DEFAULT)) {
                        ((CallSetupParams) cmdParams).mConfirmMsg.text = this.mContext.getText(17040600).toString();
                        break;
                    }
                case 20:
                    if (HuaweiTelephonyConfigs.isModemBipEnable()) {
                        if (isProactiveCmd) {
                            if (!isSupportedSetupEventCommand(catCmdMessage)) {
                                sendTerminalResponse(cmdParams.mCmdDet, ResultCode.BEYOND_TERMINAL_CAPABILITY, false, 0, null);
                                break;
                            } else {
                                sendTerminalResponse(cmdParams.mCmdDet, ResultCode.OK, false, 0, null);
                                break;
                            }
                        }
                    }
                    checkSetupEventCommand(catCmdMessage);
                    sendTerminalResponse(cmdParams.mCmdDet, ResultCode.OK, false, 0, null);
                    break;
                    break;
                case 21:
                    if (cmdParams.mLoadIconFailed) {
                        resultCode = ResultCode.PRFRMD_ICON_NOT_DISPLAYED;
                    } else {
                        resultCode = ResultCode.OK;
                    }
                    if (isProactiveCmd) {
                        sendTerminalResponse(cmdParams.mCmdDet, resultCode, false, 0, null);
                        break;
                    }
                    break;
                case 22:
                    CatLog.d((Object) this, "handleProactiveCommand()  SET_UP_MENU ");
                    if (removeMenu(catCmdMessage.getMenu())) {
                        this.mMenuCmd = null;
                    } else {
                        this.mMenuCmd = catCmdMessage;
                    }
                    if (cmdParams.mLoadIconFailed) {
                        resultCode = ResultCode.PRFRMD_ICON_NOT_DISPLAYED;
                    } else {
                        resultCode = ResultCode.OK;
                    }
                    if (isProactiveCmd) {
                        sendTerminalResponse(cmdParams.mCmdDet, resultCode, false, 0, null);
                        break;
                    }
                    break;
                default:
                    CatLog.d((Object) this, "Unsupported command");
                    return;
            }
            this.mCurrntCmd = catCmdMessage;
            broadcastCatCmdIntent(catCmdMessage);
            return;
        }
        CatLog.d((Object) this, "Unsupported command");
    }

    private void broadcastCatCmdIntent(CatCmdMessage cmdMsg) {
        Intent intent = new Intent(AppInterface.CAT_CMD_ACTION);
        intent.addFlags(268435456);
        intent.addFlags(67108864);
        intent.putExtra("STK CMD", cmdMsg);
        intent.putExtra("SLOT_ID", this.mSlotId);
        CatLog.d((Object) this, "Sending CmdMsg: " + cmdMsg + " on slotid:" + this.mSlotId);
        this.mContext.sendBroadcast(intent, AppInterface.STK_PERMISSION);
    }

    private void handleSessionEnd() {
        CatLog.d((Object) this, "SESSION END on " + this.mSlotId);
        this.mCurrntCmd = this.mMenuCmd;
        Intent intent = new Intent(AppInterface.CAT_SESSION_END_ACTION);
        intent.putExtra("SLOT_ID", this.mSlotId);
        intent.addFlags(268435456);
        this.mContext.sendBroadcast(intent, AppInterface.STK_PERMISSION);
    }

    public void sendTerminalResponse(CommandDetails cmdDet, ResultCode resultCode, boolean includeAdditionalInfo, int additionalInfo, ResponseData resp) {
        int length = 2;
        if (cmdDet != null) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            Input cmdInput = null;
            if (this.mCurrntCmd != null) {
                cmdInput = this.mCurrntCmd.geInput();
            }
            int tag = ComprehensionTlvTag.COMMAND_DETAILS.value();
            if (cmdDet.compRequired) {
                tag |= 128;
            }
            buf.write(tag);
            buf.write(3);
            buf.write(cmdDet.commandNumber);
            buf.write(cmdDet.typeOfCommand);
            buf.write(cmdDet.commandQualifier);
            buf.write(ComprehensionTlvTag.DEVICE_IDENTITIES.value());
            buf.write(2);
            buf.write(130);
            buf.write(129);
            tag = ComprehensionTlvTag.RESULT.value();
            if (cmdDet.compRequired) {
                tag |= 128;
            }
            buf.write(tag);
            if (!includeAdditionalInfo) {
                length = 1;
            }
            buf.write(length);
            buf.write(resultCode.value());
            if (includeAdditionalInfo) {
                buf.write(additionalInfo);
            }
            if (resp != null) {
                resp.format(buf);
            } else {
                encodeOptionalTags(cmdDet, resultCode, cmdInput, buf);
            }
            this.mCmdIf.sendTerminalResponse(IccUtils.bytesToHexString(buf.toByteArray()), null);
        }
    }

    private void encodeOptionalTags(CommandDetails cmdDet, ResultCode resultCode, Input cmdInput, ByteArrayOutputStream buf) {
        CommandType cmdType = CommandType.fromInt(cmdDet.typeOfCommand);
        if (cmdType != null) {
            switch (-getcom-android-internal-telephony-cat-AppInterface$CommandTypeSwitchesValues()[cmdType.ordinal()]) {
                case 4:
                    if (resultCode.value() == ResultCode.NO_RESPONSE_FROM_USER.value() && cmdInput != null && cmdInput.duration != null) {
                        getInKeyResponse(buf, cmdInput);
                        return;
                    }
                    return;
                case 10:
                    if (cmdDet.commandQualifier == 4 && resultCode.value() == ResultCode.OK.value()) {
                        getPliResponse(buf);
                        return;
                    }
                    return;
                default:
                    CatLog.d((Object) this, "encodeOptionalTags() Unsupported Cmd details=" + cmdDet);
                    return;
            }
        }
        CatLog.d((Object) this, "encodeOptionalTags() bad Cmd details=" + cmdDet);
    }

    private void getInKeyResponse(ByteArrayOutputStream buf, Input cmdInput) {
        buf.write(ComprehensionTlvTag.DURATION.value());
        buf.write(2);
        TimeUnit timeUnit = cmdInput.duration.timeUnit;
        buf.write(TimeUnit.SECOND.value());
        buf.write(cmdInput.duration.timeInterval);
    }

    private void getPliResponse(ByteArrayOutputStream buf) {
        String lang = Locale.getDefault().getLanguage();
        if (lang != null) {
            buf.write(ComprehensionTlvTag.LANGUAGE.value());
            ResponseData.writeLength(buf, lang.length());
            buf.write(lang.getBytes(), 0, lang.length());
        }
    }

    private void sendMenuSelection(int menuId, boolean helpRequired) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        buf.write(211);
        buf.write(0);
        buf.write(ComprehensionTlvTag.DEVICE_IDENTITIES.value() | 128);
        buf.write(2);
        buf.write(1);
        buf.write(129);
        buf.write(ComprehensionTlvTag.ITEM_ID.value() | 128);
        buf.write(1);
        buf.write(menuId);
        if (helpRequired) {
            buf.write(ComprehensionTlvTag.HELP_REQUEST.value());
            buf.write(0);
        }
        byte[] rawData = buf.toByteArray();
        rawData[1] = (byte) (rawData.length - 2);
        this.mCmdIf.sendEnvelope(IccUtils.bytesToHexString(rawData), null);
    }

    public void onEventDownload(CatEventMessage eventMsg) {
        CatLog.d((Object) this, "Download event: " + eventMsg.getEvent());
        if (eventMsg.getEvent() == 10 && this.mEvents[0] == 0) {
            CatLog.d((Object) this, "channel_status == 0 and don't send envelope to card");
        } else {
            eventDownload(eventMsg.getEvent(), eventMsg.getSourceId(), eventMsg.getDestId(), eventMsg.getAdditionalInfo(), eventMsg.isOneShot());
        }
    }

    private void eventDownload(int event, int sourceId, int destinationId, byte[] additionalInfo, boolean oneShot) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        buf.write(BerTlv.BER_EVENT_DOWNLOAD_TAG);
        buf.write(0);
        buf.write(ComprehensionTlvTag.EVENT_LIST.value() | 128);
        buf.write(1);
        buf.write(event);
        buf.write(ComprehensionTlvTag.DEVICE_IDENTITIES.value() | 128);
        buf.write(2);
        buf.write(sourceId);
        buf.write(destinationId);
        boolean isRetry = false;
        switch (event) {
            case 5:
                CatLog.d(sInstance, " Sending Idle Screen Available event download to ICC");
                break;
            case 7:
                CatLog.d(sInstance, " Sending Language Selection event download to ICC");
                buf.write(ComprehensionTlvTag.LANGUAGE.value() | 128);
                buf.write(2);
                break;
            case 9:
                if (!HuaweiTelephonyConfigs.isModemBipEnable()) {
                    isRetry = true;
                    CatLog.d((Object) this, "DATA_AVAILABLE_EVENT isRetry " + true);
                    break;
                }
                break;
        }
        if (additionalInfo != null) {
            for (byte b : additionalInfo) {
                buf.write(b);
            }
        }
        byte[] rawData = buf.toByteArray();
        rawData[1] = (byte) (rawData.length - 2);
        String hexString = IccUtils.bytesToHexString(rawData);
        CatLog.d((Object) this, "ENVELOPE COMMAND: " + hexString);
        if (isRetry) {
            this.mRetryHexString = hexString;
            CatLog.d((Object) this, "ENVELOPE COMMAND mRetryHexString: " + this.mRetryHexString);
            this.mCmdIf.sendEnvelope(hexString, obtainMessage(100));
            return;
        }
        this.mCmdIf.sendEnvelope(hexString, null);
    }

    public static AppInterface getInstance() {
        int slotId = 0;
        SubscriptionController sControl = SubscriptionController.getInstance();
        if (sControl != null) {
            slotId = sControl.getSlotId(sControl.getDefaultSubId());
        }
        return getInstance(null, null, null, slotId);
    }

    public static AppInterface getInstance(int slotId) {
        return getInstance(null, null, null, slotId);
    }

    public void handleMessage(Message msg) {
        CatLog.d((Object) this, "handleMessage[" + msg.what + "]");
        AsyncResult ar;
        switch (msg.what) {
            case 1:
            case 2:
            case 3:
            case 5:
                CatLog.d((Object) this, "ril message arrived,slotid:" + this.mSlotId);
                String str = null;
                if (msg.obj != null) {
                    ar = msg.obj;
                    if (!(ar == null || ar.result == null)) {
                        try {
                            str = (String) ar.result;
                            CatLog.d((Object) this, " cmdCode = " + str);
                            setLanguageNotificationCode(str);
                        } catch (ClassCastException e) {
                            return;
                        }
                    }
                }
                this.mMsgDecoder.sendStartDecodingMessageParams(new RilMessage(msg.what, str));
                return;
            case 4:
                this.mMsgDecoder.sendStartDecodingMessageParams(new RilMessage(msg.what, null));
                return;
            case 6:
                if (msg.obj instanceof CatResponseMessage) {
                    handleCmdResponse((CatResponseMessage) msg.obj);
                    return;
                }
                return;
            case 8:
                updateIccAvailability();
                return;
            case 9:
                CatLog.d((Object) this, "Received CAT CC Alpha message from card");
                if (msg.obj != null) {
                    ar = (AsyncResult) msg.obj;
                    if (ar == null || ar.result == null) {
                        CatLog.d((Object) this, "CAT Alpha message: ar.result is null");
                        return;
                    } else {
                        broadcastAlphaMessage((String) ar.result);
                        return;
                    }
                }
                CatLog.d((Object) this, "CAT Alpha message: msg.obj is null");
                return;
            case 10:
                if (msg.obj instanceof RilMessage) {
                    handleRilMsg((RilMessage) msg.obj);
                    return;
                }
                return;
            case 20:
                return;
            case 30:
                if (msg.obj != null) {
                    ar = (AsyncResult) msg.obj;
                    if (ar == null || ar.result == null) {
                        CatLog.d((Object) this, "Icc REFRESH with exception: " + ar.exception);
                        return;
                    } else {
                        broadcastCardStateAndIccRefreshResp(CardState.CARDSTATE_PRESENT, (IccRefreshResponse) ar.result);
                        return;
                    }
                }
                CatLog.d((Object) this, "IccRefresh Message is null");
                return;
            case 40:
                if (msg.obj == null || ResultCode.fromInt(msg.arg1) == null) {
                    CatLog.d((Object) this, "Wait Message is null");
                    return;
                }
                sendTerminalResponse((CommandDetails) msg.obj, ResultCode.fromInt(msg.arg1), false, 0, null);
                return;
            case 99:
                CatLog.d((Object) this, "SEND ENVELOPE retry times " + this.mRetryCount + " RETRY ENVELOPE COMMAND " + this.mRetryHexString);
                this.mCmdIf.sendEnvelope(this.mRetryHexString, obtainMessage(100));
                this.mRetryCount++;
                return;
            case 100:
                if (((AsyncResult) msg.obj).exception == null) {
                    CatLog.d((Object) this, "SEND ENVELOPE SUCCESS");
                    this.mRetryCount = 1;
                    this.mRetryHexString = null;
                    return;
                } else if (this.mRetryCount <= 5) {
                    sendMessageDelayed(obtainMessage(99), 20000);
                    return;
                } else {
                    CatLog.d((Object) this, "SEND ENVELOPE COMMAND exceed MAX RETRIES");
                    this.mRetryCount = 1;
                    this.mRetryHexString = null;
                    return;
                }
            default:
                throw new AssertionError("Unrecognized CAT command: " + msg.what);
        }
    }

    private void broadcastCardStateAndIccRefreshResp(CardState cardState, IccRefreshResponse iccRefreshState) {
        Intent intent = new Intent(AppInterface.CAT_ICC_STATUS_CHANGE);
        intent.addFlags(268435456);
        boolean cardPresent = cardState == CardState.CARDSTATE_PRESENT;
        if (iccRefreshState != null) {
            intent.putExtra(AppInterface.REFRESH_RESULT, iccRefreshState.refreshResult);
            CatLog.d((Object) this, "Sending IccResult with Result: " + iccRefreshState.refreshResult);
        }
        intent.putExtra(AppInterface.CARD_STATUS, cardPresent);
        intent.putExtra("SLOT_ID", this.mSlotId);
        CatLog.d((Object) this, "Sending Card Status: " + cardState + " " + "cardPresent: " + cardPresent);
        this.mContext.sendBroadcast(intent, AppInterface.STK_PERMISSION);
    }

    private void broadcastAlphaMessage(String alphaString) {
        CatLog.d((Object) this, "Broadcasting CAT Alpha message from card: " + alphaString);
        Intent intent = new Intent(AppInterface.CAT_ALPHA_NOTIFY_ACTION);
        intent.addFlags(268435456);
        intent.putExtra(AppInterface.ALPHA_STRING, alphaString);
        intent.putExtra("SLOT_ID", this.mSlotId);
        this.mContext.sendBroadcast(intent, AppInterface.STK_PERMISSION);
    }

    public synchronized void onCmdResponse(CatResponseMessage resMsg) {
        if (resMsg != null) {
            obtainMessage(6, resMsg).sendToTarget();
        }
    }

    private boolean validateResponse(CatResponseMessage resMsg) {
        boolean validResponse = false;
        if (CommandType.DISPLAY_TEXT.value() == resMsg.mCmdDet.typeOfCommand && this.mCurrntCmd == null && this.mMenuCmd == null) {
            return true;
        }
        if (resMsg.mCmdDet.typeOfCommand == CommandType.SET_UP_CALL.value() && this.mCurrntCmd == null) {
            CatLog.d((Object) this, "validateResponse: SET_UP_CALL");
            validResponse = true;
        }
        if (resMsg.mCmdDet.typeOfCommand == CommandType.SET_UP_EVENT_LIST.value() || resMsg.mCmdDet.typeOfCommand == CommandType.SET_UP_MENU.value()) {
            CatLog.d((Object) this, "CmdType: " + resMsg.mCmdDet.typeOfCommand);
            validResponse = true;
        } else if (this.mCurrntCmd != null) {
            validResponse = resMsg.mCmdDet.compareTo(this.mCurrntCmd.mCmdDet);
            CatLog.d((Object) this, "isResponse for last valid cmd: " + validResponse);
        }
        return validResponse;
    }

    private boolean removeMenu(Menu menu) {
        try {
            return menu.items.size() == 1 && menu.items.get(0) == null;
        } catch (NullPointerException e) {
            CatLog.d((Object) this, "Unable to get Menu's items size");
            return true;
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void handleCmdResponse(CatResponseMessage resMsg) {
        if (resMsg != null && validateResponse(resMsg)) {
            CatLog.d((Object) this, "code is " + resMsg.mResCode + ";type is " + resMsg.mCmdDet.typeOfCommand);
            if (!(resMsg.getCmdDetails() == null || this.mCurrntCmd == null || this.mCurrntCmd.mCmdDet == null)) {
                resMsg.getCmdDetails().compRequired = this.mCurrntCmd.mCmdDet.compRequired;
            }
            if (resMsg.envelopeCmd != null) {
                this.mCmdIf.sendEnvelope(resMsg.envelopeCmd, null);
                return;
            }
            boolean helpRequired = false;
            CommandDetails cmdDet = resMsg.getCmdDetails();
            CommandType type = CommandType.fromInt(cmdDet.typeOfCommand);
            switch (-getcom-android-internal-telephony-cat-ResultCodeSwitchesValues()[resMsg.mResCode.ordinal()]) {
                case 1:
                case 17:
                    if (type != CommandType.SET_UP_CALL) {
                        ResponseData resp;
                        if (type != CommandType.OPEN_CHANNEL) {
                            resp = null;
                            break;
                        } else if (!HuaweiTelephonyConfigs.isModemBipEnable()) {
                            if (!resMsg.mUsersConfirm && this.mCurrntCmd != null) {
                                if (this.mCurrntCmd.geTextMessage().responseNeeded) {
                                    ChannelSettings params = this.mCurrntCmd.getChannelSettings();
                                    resMsg.mResCode = ResultCode.USER_NOT_ACCEPT;
                                    resp = new OpenChannelResponseData(params.bufSize, null, params.bearerDescription);
                                    break;
                                }
                            }
                            resp = null;
                            break;
                        } else {
                            this.mCmdIf.handleCallSetupRequestFromSim(false, null);
                            this.mCurrntCmd = null;
                            return;
                        }
                    }
                    this.mCmdIf.handleCallSetupRequestFromSim(false, null);
                    this.mCurrntCmd = null;
                    return;
                    break;
                case 2:
                    helpRequired = true;
                    break;
                case 3:
                case 5:
                case 6:
                case 7:
                case 8:
                case 9:
                case 10:
                case 11:
                case 12:
                case 13:
                case 14:
                case 15:
                    break;
                case 4:
                    if (type == CommandType.SET_UP_CALL) {
                        this.mCurrntCmd = null;
                        return;
                    }
                    break;
                case 16:
                    break;
                default:
                    return;
            }
        }
    }

    private boolean isStkAppInstalled() {
        List<ResolveInfo> broadcastReceivers = this.mContext.getPackageManager().queryBroadcastReceivers(new Intent(AppInterface.CAT_CMD_ACTION), 128);
        if ((broadcastReceivers == null ? 0 : broadcastReceivers.size()) > 0) {
            return true;
        }
        return false;
    }

    public void update(CommandsInterface ci, Context context, UiccCard ic) {
        UiccCardApplication uiccCardApplication = null;
        IccRecords ir = null;
        if (ic != null) {
            uiccCardApplication = ic.getApplicationIndex(0);
            if (uiccCardApplication != null) {
                ir = uiccCardApplication.getIccRecords();
            }
        }
        synchronized (sInstanceLock) {
            if (ir != null) {
                if (mIccRecords != ir) {
                    if (mIccRecords != null) {
                        mIccRecords.unregisterForRecordsLoaded(this);
                    }
                    CatLog.d((Object) this, "Reinitialize the Service with SIMRecords and UiccCardApplication");
                    mIccRecords = ir;
                    mUiccApplication = uiccCardApplication;
                    mIccRecords.registerForRecordsLoaded(this, 20, null);
                    CatLog.d((Object) this, "registerForRecordsLoaded slotid=" + this.mSlotId + " instance:" + this);
                }
            }
        }
    }

    void updateIccAvailability() {
        if (this.mUiccController != null) {
            CardState newState = CardState.CARDSTATE_ABSENT;
            UiccCard newCard = this.mUiccController.getUiccCard(this.mSlotId);
            if (newCard != null) {
                newState = newCard.getCardState();
            }
            CardState oldState = this.mCardState;
            this.mCardState = newState;
            if (oldState != newState) {
                CatLog.d((Object) this, "New Card State = " + newState + " " + "Old Card State = " + oldState);
            }
            if (oldState == CardState.CARDSTATE_PRESENT && newState != CardState.CARDSTATE_PRESENT) {
                broadcastCardStateAndIccRefreshResp(newState, null);
            } else if (oldState != CardState.CARDSTATE_PRESENT && newState == CardState.CARDSTATE_PRESENT) {
                this.mCmdIf.reportStkServiceIsRunning(null);
            }
        }
    }
}
