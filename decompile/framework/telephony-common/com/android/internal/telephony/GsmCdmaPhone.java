package com.android.internal.telephony;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManagerNative;
import android.common.HwFrameworkFactory;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.SQLException;
import android.encrypt.PasswordUtil;
import android.hsm.HwSystemManager;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcel;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Settings.Global;
import android.provider.Settings.Secure;
import android.provider.Telephony.Carriers;
import android.telecom.VideoProfile;
import android.telephony.CarrierConfigManager;
import android.telephony.CellLocation;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.text.TextUtils;
import android.util.Log;
import com.android.ims.ImsManager;
import com.android.internal.telephony.CommandException.Error;
import com.android.internal.telephony.CommandsInterface.RadioState;
import com.android.internal.telephony.DctConstants.Activity;
import com.android.internal.telephony.DctConstants.State;
import com.android.internal.telephony.PhoneConstants.DataState;
import com.android.internal.telephony.PhoneInternalInterface.DataActivityState;
import com.android.internal.telephony.PhoneInternalInterface.SuppService;
import com.android.internal.telephony.cdma.CdmaMmiCode;
import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;
import com.android.internal.telephony.cdma.EriManager;
import com.android.internal.telephony.gsm.GsmMmiCode;
import com.android.internal.telephony.gsm.SuppServiceNotification;
import com.android.internal.telephony.test.SimulatedRadioControl;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccCardProxy;
import com.android.internal.telephony.uicc.IccException;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccVmNotSupportedException;
import com.android.internal.telephony.uicc.IsimRecords;
import com.android.internal.telephony.uicc.IsimUiccRecords;
import com.android.internal.telephony.uicc.RuimRecords;
import com.android.internal.telephony.uicc.SIMRecords;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.vsim.VSimUtilsInner;
import com.google.android.mms.pdu.CharacterSets;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GsmCdmaPhone extends AbstractGsmCdmaPhone {
    private static final /* synthetic */ int[] -com-android-internal-telephony-DctConstants$ActivitySwitchesValues = null;
    private static final /* synthetic */ int[] -com-android-internal-telephony-DctConstants$StateSwitchesValues = null;
    private static final int APPNAME_INDEX = 0;
    private static final int CALLINGPACKAGENAME_INDEX = 2;
    public static final int CANCEL_ECM_TIMER = 1;
    private static final String CDMA_PHONE = "CDMA";
    public static final String CF_ENABLED = "cf_enabled_key";
    private static final boolean DBG = true;
    private static final int DEFAULT_ECM_EXIT_TIMER_VALUE = 300000;
    private static final boolean FEATURE_VOLTE_DYN = SystemProperties.getBoolean("ro.config.hw_volte_dyn", false);
    private static final String GSM_PHONE = "GSM";
    private static final int IMEI_TEST_LEAST_LENGTH = 6;
    private static final int INVALID_SYSTEM_SELECTION_CODE = -1;
    private static final String IS683A_FEATURE_CODE = "*228";
    private static final int IS683A_FEATURE_CODE_NUM_DIGITS = 4;
    private static final int IS683A_SYS_SEL_CODE_NUM_DIGITS = 2;
    private static final int IS683A_SYS_SEL_CODE_OFFSET = 4;
    private static final int IS683_CONST_1900MHZ_A_BLOCK = 2;
    private static final int IS683_CONST_1900MHZ_B_BLOCK = 3;
    private static final int IS683_CONST_1900MHZ_C_BLOCK = 4;
    private static final int IS683_CONST_1900MHZ_D_BLOCK = 5;
    private static final int IS683_CONST_1900MHZ_E_BLOCK = 6;
    private static final int IS683_CONST_1900MHZ_F_BLOCK = 7;
    private static final int IS683_CONST_800MHZ_A_BAND = 0;
    private static final int IS683_CONST_800MHZ_B_BAND = 1;
    private static final boolean IS_FULL_NETWORK_SUPPORTED = SystemProperties.getBoolean("ro.config.full_network_support", false);
    public static final String LOG_TAG_STATIC = "GsmCdmaPhone";
    private static final int MAX_MAP_SIZE = 10;
    private static final int NAME_ARRAY_SIZE = 3;
    private static final int PROCESSNAME_INDEX = 1;
    public static final String PROPERTY_CDMA_HOME_OPERATOR_NUMERIC = "ro.cdma.home.operator.numeric";
    public static final int RESTART_ECM_TIMER = 0;
    private static final boolean VDBG = false;
    private static final String VM_NUMBER = "vm_number_key";
    private static final String VM_NUMBER_CDMA = "vm_number_key_cdma";
    private static final String VM_SIM_IMSI = "vm_sim_imsi_key";
    private static PasswordUtil mPasswordUtil = HwFrameworkFactory.getPasswordUtil();
    private static Pattern pOtaSpNumSchema = Pattern.compile("[,\\s]+");
    private static final boolean sHwInfo;
    public String LOG_TAG;
    private boolean mBroadcastEmergencyCallStateChanges;
    private BroadcastReceiver mBroadcastReceiver;
    public GsmCdmaCallTracker mCT;
    private String mCarrierOtaSpNumSchema;
    private CdmaSubscriptionSourceManager mCdmaSSM;
    public int mCdmaSubscriptionSource;
    private Registrant mEcmExitRespRegistrant;
    private final RegistrantList mEcmTimerResetRegistrants;
    private final RegistrantList mEriFileLoadedRegistrants;
    public EriManager mEriManager;
    private String mEsn;
    private Runnable mExitEcmRunnable;
    private IccCardProxy mIccCardProxy;
    private IccPhoneBookInterfaceManager mIccPhoneBookIntManager;
    private IccSmsInterfaceManager mIccSmsInterfaceManager;
    private String mImei;
    private String mImeiSv;
    private boolean mIsPhoneInEcmState;
    private IsimUiccRecords mIsimUiccRecords;
    private String mMeid;
    private ArrayList<MmiCode> mPendingMMIs;
    private int mPrecisePhoneType;
    private boolean mResetModemOnRadioTechnologyChange;
    private int mRilVersion;
    private AsyncResult mSSNResult;
    public ServiceStateTracker mSST;
    private SIMRecords mSimRecords;
    private RegistrantList mSsnRegistrants;
    protected String mUimid;
    private String mVmNumber;
    private WakeLock mWakeLock;
    Map<Integer, String[]> map;
    private AppType newAppType;
    private AppType oldAppType;
    private final BroadcastReceiver sConfigChangeReceiver;

    private static class Cfu {
        final Message mOnComplete;
        final String mSetCfNumber;

        Cfu(String cfNumber, Message onComplete) {
            this.mSetCfNumber = cfNumber;
            this.mOnComplete = onComplete;
        }
    }

    private static /* synthetic */ int[] -getcom-android-internal-telephony-DctConstants$ActivitySwitchesValues() {
        if (-com-android-internal-telephony-DctConstants$ActivitySwitchesValues != null) {
            return -com-android-internal-telephony-DctConstants$ActivitySwitchesValues;
        }
        int[] iArr = new int[Activity.values().length];
        try {
            iArr[Activity.DATAIN.ordinal()] = 1;
        } catch (NoSuchFieldError e) {
        }
        try {
            iArr[Activity.DATAINANDOUT.ordinal()] = 2;
        } catch (NoSuchFieldError e2) {
        }
        try {
            iArr[Activity.DATAOUT.ordinal()] = 3;
        } catch (NoSuchFieldError e3) {
        }
        try {
            iArr[Activity.DORMANT.ordinal()] = 4;
        } catch (NoSuchFieldError e4) {
        }
        try {
            iArr[Activity.NONE.ordinal()] = 12;
        } catch (NoSuchFieldError e5) {
        }
        -com-android-internal-telephony-DctConstants$ActivitySwitchesValues = iArr;
        return iArr;
    }

    private static /* synthetic */ int[] -getcom-android-internal-telephony-DctConstants$StateSwitchesValues() {
        if (-com-android-internal-telephony-DctConstants$StateSwitchesValues != null) {
            return -com-android-internal-telephony-DctConstants$StateSwitchesValues;
        }
        int[] iArr = new int[State.values().length];
        try {
            iArr[State.CONNECTED.ordinal()] = 1;
        } catch (NoSuchFieldError e) {
        }
        try {
            iArr[State.CONNECTING.ordinal()] = 2;
        } catch (NoSuchFieldError e2) {
        }
        try {
            iArr[State.DISCONNECTING.ordinal()] = 3;
        } catch (NoSuchFieldError e3) {
        }
        try {
            iArr[State.FAILED.ordinal()] = 4;
        } catch (NoSuchFieldError e4) {
        }
        try {
            iArr[State.IDLE.ordinal()] = 5;
        } catch (NoSuchFieldError e5) {
        }
        try {
            iArr[State.RETRYING.ordinal()] = 6;
        } catch (NoSuchFieldError e6) {
        }
        try {
            iArr[State.SCANNING.ordinal()] = 7;
        } catch (NoSuchFieldError e7) {
        }
        -com-android-internal-telephony-DctConstants$StateSwitchesValues = iArr;
        return iArr;
    }

    static {
        boolean z;
        if (SystemProperties.getBoolean("ro.debuggable", false)) {
            z = true;
        } else {
            z = SystemProperties.getBoolean("persist.sys.huawei.debug.on", false);
        }
        sHwInfo = z;
    }

    public GsmCdmaPhone(Context context, CommandsInterface ci, PhoneNotifier notifier, int phoneId, int precisePhoneType, TelephonyComponentFactory telephonyComponentFactory) {
        this(context, ci, notifier, false, phoneId, precisePhoneType, telephonyComponentFactory);
        this.LOG_TAG += "[SUB" + phoneId + "]";
    }

    public GsmCdmaPhone(Context context, CommandsInterface ci, PhoneNotifier notifier, boolean unitTestMode, int phoneId, int precisePhoneType, TelephonyComponentFactory telephonyComponentFactory) {
        String str;
        if (precisePhoneType == 1) {
            str = GSM_PHONE;
        } else {
            str = CDMA_PHONE;
        }
        super(str, notifier, context, ci, unitTestMode, phoneId, telephonyComponentFactory);
        this.LOG_TAG = LOG_TAG_STATIC;
        this.mSsnRegistrants = new RegistrantList();
        this.mCdmaSubscriptionSource = -1;
        this.mEriFileLoadedRegistrants = new RegistrantList();
        this.oldAppType = AppType.APPTYPE_UNKNOWN;
        this.newAppType = AppType.APPTYPE_UNKNOWN;
        this.mExitEcmRunnable = new Runnable() {
            public void run() {
                GsmCdmaPhone.this.exitEmergencyCallbackMode();
            }
        };
        this.mPendingMMIs = new ArrayList();
        this.mEcmTimerResetRegistrants = new RegistrantList();
        this.mResetModemOnRadioTechnologyChange = false;
        this.mBroadcastEmergencyCallStateChanges = false;
        this.mBroadcastReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                Rlog.d(GsmCdmaPhone.this.LOG_TAG, "mBroadcastReceiver: action " + intent.getAction());
                if (intent.getAction().equals("android.telephony.action.CARRIER_CONFIG_CHANGED")) {
                    GsmCdmaPhone.this.sendMessage(GsmCdmaPhone.this.obtainMessage(43));
                }
            }
        };
        this.sConfigChangeReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                GsmCdmaPhone.this.logd("Carrier config changed. Reloading config");
                if (intent.getAction().equals("android.telephony.action.CARRIER_CONFIG_CHANGED")) {
                    GsmCdmaPhone.this.mCi.getVoiceRadioTechnology(GsmCdmaPhone.this.obtainMessage(40));
                }
            }
        };
        this.map = new HashMap();
        this.mPrecisePhoneType = precisePhoneType;
        initOnce(ci);
        initRatSpecific(precisePhoneType);
        this.mSST = this.mTelephonyComponentFactory.makeServiceStateTracker(this, this.mCi);
        this.mDcTracker = this.mTelephonyComponentFactory.makeDcTracker(this);
        this.mSST.registerForNetworkAttached(this, 19, null);
        HwTelephonyFactory.getHwPhoneManager().setGsmCdmaPhone(this, context);
        restoreSavedRadioTech();
        this.mCi.getVoiceRadioTechnology(obtainMessage(40));
        logd("GsmCdmaPhone: constructor: sub = " + this.mPhoneId);
    }

    private void initOnce(CommandsInterface ci) {
        if (ci instanceof SimulatedRadioControl) {
            this.mSimulatedRadioControl = (SimulatedRadioControl) ci;
        }
        this.mCT = this.mTelephonyComponentFactory.makeGsmCdmaCallTracker(this);
        this.mIccPhoneBookIntManager = this.mTelephonyComponentFactory.makeIccPhoneBookInterfaceManager(this);
        this.mWakeLock = ((PowerManager) this.mContext.getSystemService("power")).newWakeLock(1, this.LOG_TAG);
        this.mIccSmsInterfaceManager = this.mTelephonyComponentFactory.makeIccSmsInterfaceManager(this);
        this.mIccCardProxy = this.mTelephonyComponentFactory.makeIccCardProxy(this.mContext, this.mCi, this.mPhoneId);
        this.mContext.registerReceiver(this.sConfigChangeReceiver, new IntentFilter("android.telephony.action.CARRIER_CONFIG_CHANGED"));
        this.mCi.registerForAvailable(this, 1, null);
        this.mCi.registerForOffOrNotAvailable(this, 8, null);
        this.mCi.registerForOn(this, 5, null);
        this.mCi.setOnSuppServiceNotification(this, 2, null);
        this.mCi.setOnUSSD(this, 7, null);
        this.mCi.setOnSs(this, 36, null);
        this.mCdmaSSM = this.mTelephonyComponentFactory.getCdmaSubscriptionSourceManagerInstance(this.mContext, this.mCi, this, 27, null);
        this.mEriManager = this.mTelephonyComponentFactory.makeEriManager(this, this.mContext, 0);
        this.mCi.setEmergencyCallbackMode(this, 25, null);
        this.mCi.registerForExitEmergencyCallbackMode(this, 26, null);
        this.mCarrierOtaSpNumSchema = TelephonyManager.from(this.mContext).getOtaSpNumberSchemaForPhone(getPhoneId(), "");
        this.mResetModemOnRadioTechnologyChange = SystemProperties.getBoolean("persist.radio.reset_on_switch", false);
        this.mCi.registerForRilConnected(this, 41, null);
        this.mCi.registerForVoiceRadioTechChanged(this, 39, null);
        this.mContext.registerReceiver(this.mBroadcastReceiver, new IntentFilter("android.telephony.action.CARRIER_CONFIG_CHANGED"));
    }

    private void initRatSpecific(int precisePhoneType) {
        this.mPendingMMIs.clear();
        this.mVmCount = 0;
        this.mEsn = null;
        this.mMeid = null;
        this.mPrecisePhoneType = precisePhoneType;
        TelephonyManager tm = TelephonyManager.from(this.mContext);
        if (isPhoneTypeGsm()) {
            this.mCi.setPhoneType(1);
            tm.setPhoneType(getPhoneId(), 1);
            this.mIccCardProxy.setVoiceRadioTech(3);
            return;
        }
        this.mCdmaSubscriptionSource = -1;
        this.mIsPhoneInEcmState = Boolean.parseBoolean(TelephonyManager.getTelephonyProperty(getPhoneId(), "ril.cdma.inecmmode", "false"));
        if (this.mIsPhoneInEcmState) {
            this.mCi.exitEmergencyCallbackMode(obtainMessage(26));
        }
        this.mCi.setPhoneType(2);
        tm.setPhoneType(getPhoneId(), 2);
        this.mIccCardProxy.setVoiceRadioTech(6);
        String operatorAlpha = SystemProperties.get("ro.cdma.home.operator.alpha");
        String operatorNumeric = SystemProperties.get(PROPERTY_CDMA_HOME_OPERATOR_NUMERIC);
        logd("init: operatorAlpha='" + operatorAlpha + "' operatorNumeric='" + operatorNumeric + "'");
        String simState = TelephonyManager.getTelephonyProperty(this.mPhoneId, "gsm.sim.state", "UNKNOWN");
        logd("init: simState = " + simState);
        boolean isCardAbsentOrNotReady;
        if ("UNKNOWN".equals(simState) || "ABSENT".equals(simState)) {
            isCardAbsentOrNotReady = true;
        } else {
            isCardAbsentOrNotReady = "NOT_READY".equals(simState);
        }
        if ((this.mUiccController.getUiccCardApplication(this.mPhoneId, 1) == null || isPhoneTypeCdmaLte()) && !r0) {
            if (!TextUtils.isEmpty(operatorAlpha)) {
                logd("init: set 'gsm.sim.operator.alpha' to operator='" + operatorAlpha + "'");
                tm.setSimOperatorNameForPhone(this.mPhoneId, operatorAlpha);
            }
            if (!TextUtils.isEmpty(operatorNumeric)) {
                logd("init: set 'gsm.sim.operator.numeric' to operator='" + operatorNumeric + "'");
                logd("update icc_operator_numeric=" + operatorNumeric);
                tm.setSimOperatorNumericForPhone(this.mPhoneId, operatorNumeric);
                SubscriptionController.getInstance().setMccMnc(operatorNumeric, getSubId());
                setIsoCountryProperty(operatorNumeric);
                logd("update mccmnc=" + operatorNumeric);
            }
        }
        updateCurrentCarrierInProvider(operatorNumeric);
    }

    private void setIsoCountryProperty(String operatorNumeric) {
        TelephonyManager tm = TelephonyManager.from(this.mContext);
        if (TextUtils.isEmpty(operatorNumeric)) {
            logd("setIsoCountryProperty: clear 'gsm.sim.operator.iso-country'");
            tm.setSimCountryIsoForPhone(this.mPhoneId, "");
            return;
        }
        String iso = "";
        try {
            iso = MccTable.countryCodeForMcc(Integer.parseInt(operatorNumeric.substring(0, 3)));
        } catch (NumberFormatException ex) {
            Rlog.e(this.LOG_TAG, "setIsoCountryProperty: countryCodeForMcc error", ex);
        } catch (StringIndexOutOfBoundsException ex2) {
            Rlog.e(this.LOG_TAG, "setIsoCountryProperty: countryCodeForMcc error", ex2);
        }
        logd("setIsoCountryProperty: set 'gsm.sim.operator.iso-country' to iso=" + iso);
        tm.setSimCountryIsoForPhone(this.mPhoneId, iso);
    }

    public boolean isPhoneTypeGsm() {
        return this.mPrecisePhoneType == 1;
    }

    public boolean isPhoneTypeCdma() {
        return this.mPrecisePhoneType == 2;
    }

    public boolean isPhoneTypeCdmaLte() {
        return this.mPrecisePhoneType == 6;
    }

    private void switchPhoneType(int precisePhoneType) {
        SubscriptionManager mSubscriptionManager = SubscriptionManager.from(getContext());
        boolean isInEcm = Boolean.parseBoolean(TelephonyManager.getTelephonyProperty(getPhoneId(), "ril.cdma.inecmmode", "false"));
        logd("switchPhoneType,isInEcm=" + isInEcm);
        logd("switchPhoneType:getPhoneId=" + getPhoneId() + "getDefaultDataPhoneId=" + mSubscriptionManager.getDefaultDataPhoneId());
        if (isInEcm && getPhoneId() == mSubscriptionManager.getDefaultDataPhoneId()) {
            this.mDcTracker.setInternalDataEnabled(true);
            notifyEmergencyCallRegistrants(false);
        }
        removeCallbacks(this.mExitEcmRunnable);
        initRatSpecific(precisePhoneType);
        this.mSST.updatePhoneType();
        setPhoneName(precisePhoneType == 1 ? GSM_PHONE : CDMA_PHONE);
        onUpdateIccAvailability();
        this.mCT.updatePhoneType();
        RadioState radioState = this.mCi.getRadioState();
        if (radioState.isAvailable()) {
            handleRadioAvailable();
            sendMessage(obtainMessage(1));
            if (radioState.isOn()) {
                handleRadioOn();
            }
        }
        if (!radioState.isAvailable() || !radioState.isOn()) {
            handleRadioOffOrNotAvailable();
        }
    }

    protected void finalize() {
        logd("GsmCdmaPhone finalized");
        if (this.mWakeLock.isHeld()) {
            Rlog.e(this.LOG_TAG, "UNEXPECTED; mWakeLock is held when finalizing.");
            this.mWakeLock.release();
        }
    }

    public ServiceState getServiceState() {
        if ((this.mSST == null || this.mSST.mSS.getState() != 0) && this.mImsPhone != null) {
            return ServiceState.mergeServiceStates(this.mSST == null ? new ServiceState() : this.mSST.mSS, this.mImsPhone.getServiceState());
        } else if (this.mSST != null) {
            return this.mSST.mSS;
        } else {
            return new ServiceState();
        }
    }

    public CellLocation getCellLocation() {
        if (isPhoneTypeGsm()) {
            return this.mSST.getCellLocation();
        }
        CdmaCellLocation loc = this.mSST.mCellLoc;
        if (Secure.getInt(getContext().getContentResolver(), "location_mode", 0) == 0) {
            CdmaCellLocation privateLoc = new CdmaCellLocation();
            privateLoc.setCellLocationData(loc.getBaseStationId(), Integer.MAX_VALUE, Integer.MAX_VALUE, loc.getSystemId(), loc.getNetworkId());
            loc = privateLoc;
        }
        return loc;
    }

    public PhoneConstants.State getState() {
        if (this.mImsPhone != null) {
            PhoneConstants.State imsState = this.mImsPhone.getState();
            if (imsState != PhoneConstants.State.IDLE) {
                return imsState;
            }
        }
        if (this.mCT == null) {
            return PhoneConstants.State.IDLE;
        }
        return this.mCT.mState;
    }

    public int getPhoneType() {
        if (this.mPrecisePhoneType == 1) {
            return 1;
        }
        return 2;
    }

    public ServiceStateTracker getServiceStateTracker() {
        return this.mSST;
    }

    public CallTracker getCallTracker() {
        return this.mCT;
    }

    public void updateVoiceMail() {
        if (isPhoneTypeGsm()) {
            int countVoiceMessages = 0;
            IccRecords r = (IccRecords) this.mIccRecords.get();
            if (r != null) {
                countVoiceMessages = r.getVoiceMessageCount();
            }
            int countVoiceMessagesStored = getStoredVoiceMessageCount();
            if (countVoiceMessages == -1 && countVoiceMessagesStored != 0) {
                countVoiceMessages = countVoiceMessagesStored;
            }
            logd("updateVoiceMail countVoiceMessages = " + countVoiceMessages + " subId " + getSubId());
            setVoiceMessageCount(countVoiceMessages);
            return;
        }
        setVoiceMessageCount(getStoredVoiceMessageCount());
    }

    public boolean getCallForwardingIndicator() {
        boolean cf = false;
        if (((IccRecords) this.mIccRecords.get()) != null) {
            cf = ((IccRecords) this.mIccRecords.get()).getVoiceCallForwardingFlag() == 1;
        }
        if (!cf) {
            cf = (!getCallForwardingPreference() || getSubscriberId() == null) ? false : getSubscriberId().equals(getVmSimImsi());
        }
        Rlog.d(this.LOG_TAG, "getCallForwardingIndicator getPhoneId=" + getPhoneId() + ", cf=" + cf);
        return cf;
    }

    public List<? extends MmiCode> getPendingMmiCodes() {
        return this.mPendingMMIs;
    }

    public DataState getDataConnectionState(String apnType) {
        DataState ret = DataState.DISCONNECTED;
        if (this.mSST == null) {
            ret = DataState.DISCONNECTED;
        } else if (this.mSST.getCurrentDataConnectionState() == 0 || (!isPhoneTypeCdma() && (!isPhoneTypeGsm() || apnType.equals("emergency")))) {
            switch (-getcom-android-internal-telephony-DctConstants$StateSwitchesValues()[this.mDcTracker.getState(apnType).ordinal()]) {
                case 1:
                case 3:
                    if (this.mCT.mState != PhoneConstants.State.IDLE && !this.mSST.isConcurrentVoiceAndDataAllowed()) {
                        ret = DataState.DISCONNECTED;
                        break;
                    }
                    ret = DataState.CONNECTED;
                    break;
                    break;
                case 2:
                case 7:
                    ret = DataState.CONNECTING;
                    break;
                case 4:
                case 5:
                case 6:
                    ret = DataState.DISCONNECTED;
                    break;
                default:
                    break;
            }
        } else {
            ret = DataState.DISCONNECTED;
        }
        logd("getDataConnectionState apnType=" + apnType + " ret=" + ret);
        return ret;
    }

    public DataActivityState getDataActivityState() {
        DataActivityState ret = DataActivityState.NONE;
        if (this.mSST.getCurrentDataConnectionState() != 0) {
            return ret;
        }
        switch (-getcom-android-internal-telephony-DctConstants$ActivitySwitchesValues()[this.mDcTracker.getActivity().ordinal()]) {
            case 1:
                return DataActivityState.DATAIN;
            case 2:
                return DataActivityState.DATAINANDOUT;
            case 3:
                return DataActivityState.DATAOUT;
            case 4:
                return DataActivityState.DORMANT;
            default:
                return DataActivityState.NONE;
        }
    }

    public void notifyPhoneStateChanged() {
        this.mNotifier.notifyPhoneState(this);
    }

    public void notifyPreciseCallStateChanged() {
        super.notifyPreciseCallStateChangedP();
    }

    public void notifyNewRingingConnection(Connection c) {
        super.notifyNewRingingConnectionP(c);
        HwTelephonyFactory.getHwChrServiceManager().reportCallException("Telephony", getSubId(), 1, LOG_TAG_STATIC);
    }

    public void notifyDisconnect(Connection cn) {
        this.mDisconnectRegistrants.notifyResult(cn);
        this.mNotifier.notifyDisconnectCause(cn.getDisconnectCause(), cn.getPreciseDisconnectCause());
    }

    public void notifyUnknownConnection(Connection cn) {
        super.notifyUnknownConnectionP(cn);
    }

    public boolean isInEmergencyCall() {
        if (isPhoneTypeGsm()) {
            return false;
        }
        return this.mCT.isInEmergencyCall();
    }

    protected void setIsInEmergencyCall() {
        if (!isPhoneTypeGsm()) {
            this.mCT.setIsInEmergencyCall();
        }
    }

    public boolean isInEcm() {
        if (isPhoneTypeGsm()) {
            return false;
        }
        return this.mIsPhoneInEcmState;
    }

    private void sendEmergencyCallbackModeChange() {
        Intent intent = new Intent("android.intent.action.EMERGENCY_CALLBACK_MODE_CHANGED");
        intent.putExtra("phoneinECMState", this.mIsPhoneInEcmState);
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, getPhoneId());
        ActivityManagerNative.broadcastStickyIntent(intent, null, -1);
        logd("sendEmergencyCallbackModeChange");
    }

    public void sendEmergencyCallStateChange(boolean callActive) {
        if (this.mBroadcastEmergencyCallStateChanges) {
            Intent intent = new Intent("android.intent.action.EMERGENCY_CALL_STATE_CHANGED");
            intent.putExtra("phoneInEmergencyCall", callActive);
            SubscriptionManager.putPhoneIdAndSubIdExtra(intent, getPhoneId());
            ActivityManagerNative.broadcastStickyIntent(intent, null, -1);
            Rlog.d(this.LOG_TAG, "sendEmergencyCallStateChange");
        }
    }

    public void setBroadcastEmergencyCallStateChanges(boolean broadcast) {
        this.mBroadcastEmergencyCallStateChanges = broadcast;
    }

    public void notifySuppServiceFailed(SuppService code) {
        this.mSuppServiceFailedRegistrants.notifyResult(code);
    }

    public void notifyServiceStateChanged(ServiceState ss) {
        super.notifyServiceStateChangedP(ss);
    }

    public void notifyLocationChanged() {
        this.mNotifier.notifyCellLocation(this);
    }

    public void notifyCallForwardingIndicator() {
        this.mNotifier.notifyCallForwardingChanged(this);
    }

    public void setSystemProperty(String property, String value) {
        if (!getUnitTestMode()) {
            if (isPhoneTypeGsm() || isPhoneTypeCdmaLte()) {
                TelephonyManager.setTelephonyProperty(this.mPhoneId, property, value);
            } else {
                super.setSystemProperty(property, value);
            }
        }
    }

    public void registerForSuppServiceNotification(Handler h, int what, Object obj) {
        this.mSsnRegistrants.addUnique(h, what, obj);
        if (this.mSSNResult != null) {
            this.mSsnRegistrants.notifyRegistrants(this.mSSNResult);
        }
        if (this.mSsnRegistrants.size() == 1) {
            this.mCi.setSuppServiceNotifications(true, null);
        }
    }

    public void unregisterForSuppServiceNotification(Handler h) {
        this.mSsnRegistrants.remove(h);
        this.mSSNResult = null;
    }

    public void registerForSimRecordsLoaded(Handler h, int what, Object obj) {
        this.mSimRecordsLoadedRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForSimRecordsLoaded(Handler h) {
        this.mSimRecordsLoadedRegistrants.remove(h);
    }

    public void acceptCall(int videoState) throws CallStateException {
        HwTelephonyFactory.getHwChrServiceManager().reportCallException("Telephony", getSubId(), 2, LOG_TAG_STATIC);
        Phone imsPhone = this.mImsPhone;
        if (imsPhone == null || !imsPhone.getRingingCall().isRinging()) {
            this.mCT.acceptCall();
        } else {
            imsPhone.acceptCall(videoState);
        }
    }

    public void rejectCall() throws CallStateException {
        this.mCT.rejectCall();
    }

    public void switchHoldingAndActive() throws CallStateException {
        this.mCT.switchWaitingOrHoldingAndActive();
    }

    public String getIccSerialNumber() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (!isPhoneTypeGsm() && r == null) {
            r = this.mUiccController.getIccRecords(this.mPhoneId, 1);
        }
        if (r != null) {
            return r.getIccId();
        }
        return null;
    }

    public String getFullIccSerialNumber() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (!isPhoneTypeGsm() && r == null) {
            r = this.mUiccController.getIccRecords(this.mPhoneId, 1);
        }
        if (r != null) {
            return r.getFullIccId();
        }
        return null;
    }

    public boolean canConference() {
        if (this.mImsPhone != null && this.mImsPhone.canConference()) {
            return true;
        }
        if (isPhoneTypeGsm()) {
            return this.mCT.canConference();
        }
        loge("canConference: not possible in CDMA");
        return false;
    }

    public void conference() {
        if (this.mImsPhone == null || !this.mImsPhone.canConference()) {
            if (isPhoneTypeGsm()) {
                this.mCT.conference();
            } else {
                loge("conference: not possible in CDMA");
            }
            return;
        }
        logd("conference() - delegated to IMS phone");
        try {
            this.mImsPhone.conference();
        } catch (CallStateException e) {
            loge(e.toString());
        }
    }

    public void enableEnhancedVoicePrivacy(boolean enable, Message onComplete) {
        if (isPhoneTypeGsm()) {
            loge("enableEnhancedVoicePrivacy: not expected on GSM");
        } else {
            this.mCi.setPreferredVoicePrivacy(enable, onComplete);
        }
    }

    public void getEnhancedVoicePrivacy(Message onComplete) {
        if (isPhoneTypeGsm()) {
            loge("getEnhancedVoicePrivacy: not expected on GSM");
        } else {
            this.mCi.getPreferredVoicePrivacy(onComplete);
        }
    }

    public void clearDisconnected() {
        this.mCT.clearDisconnected();
    }

    public boolean canTransfer() {
        if (isPhoneTypeGsm()) {
            return this.mCT.canTransfer();
        }
        loge("canTransfer: not possible in CDMA");
        return false;
    }

    public void explicitCallTransfer() {
        if (isPhoneTypeGsm()) {
            this.mCT.explicitCallTransfer();
        } else {
            loge("explicitCallTransfer: not possible in CDMA");
        }
    }

    public GsmCdmaCall getForegroundCall() {
        return this.mCT.mForegroundCall;
    }

    public GsmCdmaCall getBackgroundCall() {
        return this.mCT.mBackgroundCall;
    }

    public Call getRingingCall() {
        Phone imsPhone = this.mImsPhone;
        if (imsPhone != null && imsPhone.getRingingCall().isRinging()) {
            return imsPhone.getRingingCall();
        }
        if (this.mCT == null) {
            return null;
        }
        return this.mCT.mRingingCall;
    }

    private boolean handleCallDeflectionIncallSupplementaryService(String dialString) {
        if (dialString.length() > 1) {
            return false;
        }
        if (getRingingCall().getState() != Call.State.IDLE) {
            logd("MmiCode 0: rejectCall");
            try {
                this.mCT.rejectCall();
            } catch (CallStateException e) {
                Rlog.d(this.LOG_TAG, "reject failed", e);
                notifySuppServiceFailed(SuppService.REJECT);
            }
        } else if (getBackgroundCall().getState() != Call.State.IDLE) {
            logd("MmiCode 0: hangupWaitingOrBackground");
            this.mCT.hangupWaitingOrBackground();
        }
        return true;
    }

    private boolean handleCallWaitingIncallSupplementaryService(String dialString) {
        int len = dialString.length();
        if (len > 2) {
            return false;
        }
        GsmCdmaCall call = getForegroundCall();
        if (len > 1) {
            try {
                int callIndex = dialString.charAt(1) - 48;
                if (callIndex >= 1 && callIndex <= 19) {
                    logd("MmiCode 1: hangupConnectionByIndex " + callIndex);
                    this.mCT.hangupConnectionByIndex(call, callIndex);
                }
            } catch (CallStateException e) {
                Rlog.d(this.LOG_TAG, "hangup failed", e);
                notifySuppServiceFailed(SuppService.HANGUP);
            }
        } else if (call.getState() != Call.State.IDLE) {
            logd("MmiCode 1: hangup foreground");
            this.mCT.hangup(call);
        } else {
            logd("MmiCode 1: switchWaitingOrHoldingAndActive");
            this.mCT.switchWaitingOrHoldingAndActive();
        }
        return true;
    }

    private boolean handleCallHoldIncallSupplementaryService(String dialString) {
        int len = dialString.length();
        if (len > 2) {
            return false;
        }
        GsmCdmaCall call = getForegroundCall();
        if (len > 1) {
            try {
                int callIndex = dialString.charAt(1) - 48;
                GsmCdmaConnection conn = this.mCT.getConnectionByIndex(call, callIndex);
                if (conn == null || callIndex < 1 || callIndex > 19) {
                    logd("separate: invalid call index " + callIndex);
                    notifySuppServiceFailed(SuppService.SEPARATE);
                } else {
                    logd("MmiCode 2: separate call " + callIndex);
                    this.mCT.separate(conn);
                }
            } catch (CallStateException e) {
                Rlog.d(this.LOG_TAG, "separate failed", e);
                notifySuppServiceFailed(SuppService.SEPARATE);
            }
        } else {
            try {
                if (getRingingCall().getState() != Call.State.IDLE) {
                    logd("MmiCode 2: accept ringing call");
                    this.mCT.acceptCall();
                } else {
                    logd("MmiCode 2: switchWaitingOrHoldingAndActive");
                    this.mCT.switchWaitingOrHoldingAndActive();
                }
            } catch (CallStateException e2) {
                Rlog.d(this.LOG_TAG, "switch failed", e2);
                notifySuppServiceFailed(SuppService.SWITCH);
            }
        }
        return true;
    }

    private boolean handleMultipartyIncallSupplementaryService(String dialString) {
        if (dialString.length() > 1) {
            return false;
        }
        logd("MmiCode 3: merge calls");
        conference();
        return true;
    }

    private boolean handleEctIncallSupplementaryService(String dialString) {
        if (dialString.length() != 1) {
            return false;
        }
        logd("MmiCode 4: explicit call transfer");
        explicitCallTransfer();
        return true;
    }

    private boolean handleCcbsIncallSupplementaryService(String dialString) {
        if (dialString.length() > 1) {
            return false;
        }
        Rlog.i(this.LOG_TAG, "MmiCode 5: CCBS not supported!");
        notifySuppServiceFailed(SuppService.UNKNOWN);
        return true;
    }

    public boolean handleInCallMmiCommands(String dialString) throws CallStateException {
        if (isPhoneTypeGsm()) {
            Phone imsPhone = this.mImsPhone;
            if (imsPhone != null && imsPhone.getServiceState().getState() == 0) {
                return imsPhone.handleInCallMmiCommands(dialString);
            }
            if (!isInCall() || TextUtils.isEmpty(dialString)) {
                return false;
            }
            boolean result = false;
            switch (dialString.charAt(0)) {
                case '0':
                    result = handleCallDeflectionIncallSupplementaryService(dialString);
                    break;
                case '1':
                    result = handleCallWaitingIncallSupplementaryService(dialString);
                    break;
                case '2':
                    result = handleCallHoldIncallSupplementaryService(dialString);
                    break;
                case RadioNVItems.RIL_NV_CDMA_PRL_VERSION /*51*/:
                    result = handleMultipartyIncallSupplementaryService(dialString);
                    break;
                case RadioNVItems.RIL_NV_CDMA_BC10 /*52*/:
                    result = handleEctIncallSupplementaryService(dialString);
                    break;
                case RadioNVItems.RIL_NV_CDMA_BC14 /*53*/:
                    result = handleCcbsIncallSupplementaryService(dialString);
                    break;
            }
            return result;
        }
        loge("method handleInCallMmiCommands is NOT supported in CDMA!");
        return false;
    }

    public boolean isInCall() {
        Call.State foregroundCallState = getForegroundCall().getState();
        Call.State backgroundCallState = getBackgroundCall().getState();
        Call.State ringingCallState = getRingingCall().getState();
        if (foregroundCallState.isAlive() || backgroundCallState.isAlive()) {
            return true;
        }
        return ringingCallState.isAlive();
    }

    public Connection dial(String dialString, int videoState) throws CallStateException {
        return dial(dialString, null, videoState, null);
    }

    public Connection dial(String dialString, UUSInfo uusInfo, int videoState, Bundle intentExtras) throws CallStateException {
        if (isPhoneTypeGsm() || uusInfo == null) {
            boolean useImsForEmergency;
            boolean endsWith;
            Boolean valueOf;
            Integer valueOf2;
            HwTelephonyFactory.getHwChrServiceManager().reportCallException("Telephony", getSubId(), 0, LOG_TAG_STATIC);
            boolean isEmergency;
            if (TelephonyManager.getDefault().isMultiSimEnabled()) {
                isEmergency = PhoneNumberUtils.isEmergencyNumber(getSubId(), dialString);
            } else {
                isEmergency = PhoneNumberUtils.isEmergencyNumber(dialString);
            }
            Phone imsPhone = this.mImsPhone;
            boolean alwaysTryImsForEmergencyCarrierConfig = ((CarrierConfigManager) this.mContext.getSystemService("carrier_config")).getConfigForSubId(getSubId()).getBoolean("carrier_use_ims_first_for_emergency_bool");
            boolean imsUseEnabled = (isImsUseEnabled() && this.mCT.getState() == PhoneConstants.State.IDLE && imsPhone != null) ? (!getImsSwitch() || HwModemCapability.isCapabilitySupport(9)) ? (imsPhone.isVolteEnabled() || imsPhone.isWifiCallingEnabled() || (imsPhone.isVideoEnabled() && VideoProfile.isVideo(videoState))) ? imsPhone.getServiceState().getState() == 0 : false : true : false;
            if (imsPhone != null) {
                logd("mCT state = " + this.mCT.getState() + ", ims switch state = " + getImsSwitch() + ", isVideo = " + VideoProfile.isVideo(videoState) + ", video state = " + videoState);
            } else {
                logd("dial -> imsPhone is null");
            }
            if (imsPhone != null && isEmergency && alwaysTryImsForEmergencyCarrierConfig && ImsManager.isNonTtyOrTtyOnVolteEnabled(this.mContext) && imsPhone.getServiceState().getState() != 3) {
                useImsForEmergency = this.mCT.mForegroundCall.getState() != Call.State.ACTIVE;
            } else {
                useImsForEmergency = false;
            }
            String dialPart = PhoneNumberUtils.extractNetworkPortionAlt(PhoneNumberUtils.stripSeparators(dialString));
            if (dialPart.startsWith(CharacterSets.MIMENAME_ANY_CHARSET) || dialPart.startsWith("#")) {
                endsWith = dialPart.endsWith("#");
            } else {
                endsWith = false;
            }
            boolean isUtEnabled = imsPhone != null ? imsPhone.isUtEnabled() : false;
            if (VSimUtilsInner.isVSimOn()) {
                logd("vsim is on");
                imsUseEnabled = false;
                useImsForEmergency = false;
            }
            StringBuilder append = new StringBuilder().append("imsUseEnabled=").append(imsUseEnabled).append(", useImsForEmergency=").append(useImsForEmergency).append(", useImsForUt=").append(isUtEnabled).append(", isUt=").append(endsWith).append(", imsPhone=").append(imsPhone).append(", imsPhone.isVolteEnabled()=");
            if (imsPhone != null) {
                valueOf = Boolean.valueOf(imsPhone.isVolteEnabled());
            } else {
                valueOf = "N/A";
            }
            append = append.append(valueOf).append(", imsPhone.isVowifiEnabled()=");
            if (imsPhone != null) {
                valueOf = Boolean.valueOf(imsPhone.isWifiCallingEnabled());
            } else {
                valueOf = "N/A";
            }
            append = append.append(valueOf).append(", imsPhone.isVideoEnabled()=");
            if (imsPhone != null) {
                valueOf = Boolean.valueOf(imsPhone.isVideoEnabled());
            } else {
                valueOf = "N/A";
            }
            append = append.append(valueOf).append(", imsPhone.getServiceState().getState()=");
            if (imsPhone != null) {
                valueOf2 = Integer.valueOf(imsPhone.getServiceState().getState());
            } else {
                valueOf2 = "N/A";
            }
            logd(append.append(valueOf2).append(", mCT.mForegroundCall.getState=").append(this.mCT.mForegroundCall.getState()).toString());
            Phone.checkWfcWifiOnlyModeBeforeDial(this.mImsPhone, this.mContext);
            if (isPhoneTypeGsm() && ((imsUseEnabled && (!endsWith || isUtEnabled)) || useImsForEmergency)) {
                try {
                    logd("Trying IMS PS call");
                    return imsPhone.dial(dialString, uusInfo, videoState, intentExtras);
                } catch (CallStateException e) {
                    logd("IMS PS call exception " + e + "imsUseEnabled =" + imsUseEnabled + ", imsPhone =" + imsPhone);
                    if (!Phone.CS_FALLBACK.equals(e.getMessage())) {
                        CallStateException ce = new CallStateException(e.getMessage());
                        ce.setStackTrace(e.getStackTrace());
                        throw ce;
                    }
                }
            }
            if (this.mSST == null || this.mSST.mSS.getState() != 1 || this.mSST.mSS.getDataRegState() == 0 || isEmergency) {
                logd("Trying (non-IMS) CS call");
                if (isPhoneTypeGsm()) {
                    return dialInternal(dialString, null, 0, intentExtras);
                }
                return dialInternal(dialString, null, videoState, intentExtras);
            }
            throw new CallStateException("cannot dial in current state");
        }
        throw new CallStateException("Sending UUS information NOT supported in CDMA!");
    }

    protected Connection dialInternal(String dialString, UUSInfo uusInfo, int videoState, Bundle intentExtras) throws CallStateException {
        String newDialString = PhoneNumberUtils.stripSeparators(dialString);
        if (!isPhoneTypeGsm()) {
            return this.mCT.dial(newDialString);
        }
        if (handleInCallMmiCommands(newDialString)) {
            return null;
        }
        GsmMmiCode mmi = GsmMmiCode.newFromDialString(PhoneNumberUtils.extractNetworkPortionAlt(newDialString), this, (UiccCardApplication) this.mUiccApplication.get());
        logd("dialing w/ mmi '" + mmi + "'...");
        if (mmi == null) {
            return this.mCT.dial(newDialString, uusInfo, intentExtras);
        }
        if (mmi.isTemporaryModeCLIR()) {
            return this.mCT.dial(mmi.mDialingNumber, mmi.getCLIRMode(), uusInfo, intentExtras);
        }
        this.mPendingMMIs.add(mmi);
        this.mMmiRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
        if (FEATURE_VOLTE_DYN) {
            Rlog.d(this.LOG_TAG, "The FEATURE_VOLTE_DYN feature is on.");
            Phone imsPhone = this.mImsPhone;
            if (imsPhone != null && imsPhone.mHwImsPhone.isUtEnable()) {
                mmi.setImsPhone(this.mImsPhone);
            }
        } else {
            mmi.setImsPhone(this.mImsPhone);
        }
        try {
            mmi.processCode();
        } catch (CallStateException e) {
        }
        return null;
    }

    public boolean handlePinMmi(String dialString) {
        MmiCode mmi;
        if (isPhoneTypeGsm()) {
            mmi = GsmMmiCode.newFromDialString(dialString, this, (UiccCardApplication) this.mUiccApplication.get());
        } else {
            mmi = CdmaMmiCode.newFromDialString(dialString, this, (UiccCardApplication) this.mUiccApplication.get());
        }
        if (mmi == null || !mmi.isPinPukCommand()) {
            loge("Mmi is null or unrecognized!");
            return false;
        }
        this.mPendingMMIs.add(mmi);
        this.mMmiRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
        try {
            mmi.processCode();
        } catch (CallStateException e) {
        }
        return true;
    }

    public void sendUssdResponse(String ussdMessge) {
        if (isPhoneTypeGsm()) {
            GsmMmiCode mmi = GsmMmiCode.newFromUssdUserInput(ussdMessge, this, (UiccCardApplication) this.mUiccApplication.get());
            this.mPendingMMIs.add(mmi);
            this.mMmiRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
            mmi.sendUssd(ussdMessge);
            return;
        }
        loge("sendUssdResponse: not possible in CDMA");
    }

    public void sendDtmf(char c) {
        if (!PhoneNumberUtils.is12Key(c)) {
            loge("sendDtmf called with invalid character '" + c + "'");
        } else if (this.mCT.mState == PhoneConstants.State.OFFHOOK) {
            this.mCi.sendDtmf(c, null);
        }
    }

    public void startDtmf(char c) {
        Object obj = 1;
        if (!PhoneNumberUtils.is12Key(c) && (c < 'A' || c > 'D')) {
            obj = null;
        }
        if (obj == null) {
            loge("startDtmf called with invalid character '" + c + "'");
        } else {
            this.mCi.startDtmf(c, null);
        }
    }

    public void stopDtmf() {
        this.mCi.stopDtmf(null);
    }

    public void sendBurstDtmf(String dtmfString, int on, int off, Message onComplete) {
        if (isPhoneTypeGsm()) {
            loge("[GsmCdmaPhone] sendBurstDtmf() is a CDMA method");
            return;
        }
        boolean check = true;
        for (int itr = 0; itr < dtmfString.length(); itr++) {
            if (!PhoneNumberUtils.is12Key(dtmfString.charAt(itr))) {
                Rlog.e(this.LOG_TAG, "sendDtmf called with invalid character '" + dtmfString.charAt(itr) + "'");
                check = false;
                break;
            }
        }
        if (this.mCT.mState == PhoneConstants.State.OFFHOOK && check) {
            this.mCi.sendBurstDtmf(dtmfString, on, off, onComplete);
        }
    }

    public void setRadioPower(boolean power) {
        this.mSST.setRadioPower(power);
    }

    public void setRadioPower(boolean power, Message msg) {
        this.mSST.setRadioPower(power, msg);
    }

    private void storeVoiceMailNumber(String number, boolean isSaveIccRecord) {
        Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        if (isPhoneTypeGsm()) {
            editor.putString(getIccSerialNumber() + getPhoneId(), number);
            editor.putString(VM_NUMBER + getPhoneId(), number);
            editor.apply();
            setVmSimImsi(getSubscriberId());
            if (isSaveIccRecord) {
                ((IccRecords) this.mIccRecords.get()).setVoiceMailNumber(number);
                return;
            }
            return;
        }
        editor.putString(VM_NUMBER_CDMA + getPhoneId(), number);
        editor.apply();
    }

    public String getVoiceMailNumber() {
        String number;
        if (isPhoneTypeGsm()) {
            IccRecords r = (IccRecords) this.mIccRecords.get();
            number = r != null ? r.getVoiceMailNumber() : "";
            if (TextUtils.isEmpty(number)) {
                number = getVMNumberWhenIMSIChange();
            }
            if (TextUtils.isEmpty(number)) {
                number = PreferenceManager.getDefaultSharedPreferences(getContext()).getString(VM_NUMBER + getPhoneId(), null);
            }
        } else {
            number = PreferenceManager.getDefaultSharedPreferences(getContext()).getString(VM_NUMBER_CDMA + getPhoneId(), null);
        }
        if (TextUtils.isEmpty(number)) {
            String[] listArray = getContext().getResources().getStringArray(17236035);
            if (listArray != null && listArray.length > 0) {
                for (int i = 0; i < listArray.length; i++) {
                    if (!TextUtils.isEmpty(listArray[i])) {
                        String[] defaultVMNumberArray = listArray[i].split(";");
                        if (defaultVMNumberArray != null && defaultVMNumberArray.length > 0) {
                            if (defaultVMNumberArray.length != 1) {
                                if (defaultVMNumberArray.length == 2 && !TextUtils.isEmpty(defaultVMNumberArray[1]) && isMatchGid(defaultVMNumberArray[1])) {
                                    number = defaultVMNumberArray[0];
                                    break;
                                }
                            }
                            number = defaultVMNumberArray[0];
                        }
                    }
                }
            }
        }
        if (!isPhoneTypeGsm() && TextUtils.isEmpty(number)) {
            if (getContext().getResources().getBoolean(17956965)) {
                number = getLine1Number();
            } else {
                number = "*86";
            }
        }
        if (isPhoneTypeGsm()) {
            return number;
        }
        return HwTelephonyFactory.getHwPhoneManager().getCDMAVoiceMailNumberHwCust(this.mContext, getLine1Number(), getPhoneId());
    }

    public String getVoiceMailAlphaTag() {
        String ret = "";
        if (isPhoneTypeGsm()) {
            IccRecords r = (IccRecords) this.mIccRecords.get();
            return getDefaultVoiceMailAlphaTagText(this.mContext, r != null ? r.getVoiceMailAlphaTag() : "");
        } else if (ret == null || ret.length() == 0) {
            return this.mContext.getText(17039364).toString();
        } else {
            return ret;
        }
    }

    public String getDeviceId() {
        if (!VSimUtilsInner.isRadioAvailable(this.mPhoneId)) {
            Rlog.d(this.LOG_TAG, "getDeviceId, the phone is pending, mPhoneId is: " + this.mPhoneId);
            return VSimUtilsInner.getPendingDeviceInfoFromSP(VSimUtilsInner.DEVICE_ID_PREF);
        } else if (isPhoneTypeGsm()) {
            return this.mImei;
        } else {
            String id = getMeid();
            if (id == null || id.matches("^0*$")) {
                loge("getDeviceId(): MEID is not initialized use ESN");
                id = getEsn();
            }
            return id;
        }
    }

    public String getDeviceSvn() {
        if (!VSimUtilsInner.isRadioAvailable(this.mPhoneId)) {
            Rlog.d(this.LOG_TAG, "getDeviceSvn, the phone is pending, mPhoneId is: " + this.mPhoneId);
            return VSimUtilsInner.getPendingDeviceInfoFromSP(VSimUtilsInner.DEVICE_SVN_PREF);
        } else if (isPhoneTypeGsm() || isPhoneTypeCdmaLte()) {
            return this.mImeiSv;
        } else {
            loge("getDeviceSvn(): return 0");
            return ProxyController.MODEM_0;
        }
    }

    public IsimRecords getIsimRecords() {
        return this.mIsimUiccRecords;
    }

    public String getImei() {
        if (VSimUtilsInner.isRadioAvailable(this.mPhoneId)) {
            return this.mImei;
        }
        Rlog.d(this.LOG_TAG, "getImei, the phone is pending, mPhoneId is: " + this.mPhoneId);
        return VSimUtilsInner.getPendingDeviceInfoFromSP(VSimUtilsInner.IMEI_PREF);
    }

    public String getEsn() {
        if (!VSimUtilsInner.isRadioAvailable(this.mPhoneId)) {
            Rlog.d(this.LOG_TAG, "getEsn, the phone is pending, mPhoneId is: " + this.mPhoneId);
            return VSimUtilsInner.getPendingDeviceInfoFromSP(VSimUtilsInner.ESN_PREF);
        } else if (isPhoneTypeGsm()) {
            return super.getHwCdmaEsn();
        } else {
            if (5 == TelephonyManager.getDefault().getSimState(getSubId())) {
                this.mEsn = this.mCi.getHwUimid();
            }
            return this.mEsn;
        }
    }

    public String getMeid() {
        if (!VSimUtilsInner.isRadioAvailable(this.mPhoneId)) {
            Rlog.d(this.LOG_TAG, "getMeid, the phone is pending, mPhoneId is: " + this.mPhoneId);
            return VSimUtilsInner.getPendingDeviceInfoFromSP(VSimUtilsInner.MEID_PREF);
        } else if (!isPhoneTypeGsm()) {
            return this.mMeid;
        } else {
            loge("[GsmCdmaPhone] getMeid() is a CDMA method");
            return super.getMeid();
        }
    }

    public String getNai() {
        IccRecords r = this.mUiccController.getIccRecords(this.mPhoneId, 2);
        if (Log.isLoggable(this.LOG_TAG, 2)) {
            Rlog.v(this.LOG_TAG, "IccRecords is " + r);
        }
        if (r != null) {
            return r.getNAI();
        }
        return null;
    }

    public String getSubscriberId() {
        String str = null;
        IccRecords r;
        if (isPhoneTypeGsm()) {
            r = (IccRecords) this.mIccRecords.get();
            if (r != null) {
                str = r.getIMSI();
            }
            return str;
        } else if (!isPhoneTypeCdma() && !HuaweiTelephonyConfigs.isChinaTelecom() && !IS_FULL_NETWORK_SUPPORTED) {
            return this.mSimRecords != null ? this.mSimRecords.getIMSI() : "";
        } else if (this.mCdmaSubscriptionSource == 1) {
            Rlog.d(this.LOG_TAG, "getSubscriberId from mSST");
            return this.mSST.getImsi();
        } else {
            r = (IccRecords) this.mIccRecords.get();
            if (r != null) {
                str = r.getIMSI();
            }
            return str;
        }
    }

    public String getGroupIdLevel1() {
        String str = null;
        if (isPhoneTypeGsm()) {
            IccRecords r = (IccRecords) this.mIccRecords.get();
            if (r != null) {
                str = r.getGid1();
            }
            return str;
        } else if (isPhoneTypeCdma()) {
            loge("GID1 is not available in CDMA");
            return null;
        } else {
            return this.mSimRecords != null ? this.mSimRecords.getGid1() : "";
        }
    }

    public String getGroupIdLevel2() {
        String str = null;
        if (isPhoneTypeGsm()) {
            IccRecords r = (IccRecords) this.mIccRecords.get();
            if (r != null) {
                str = r.getGid2();
            }
            return str;
        } else if (isPhoneTypeCdma()) {
            loge("GID2 is not available in CDMA");
            return null;
        } else {
            return this.mSimRecords != null ? this.mSimRecords.getGid2() : "";
        }
    }

    public String getLine1Number() {
        String str = null;
        logForTest("getLine1Number", "get phone number..****");
        if (!isPhoneTypeGsm()) {
            return this.mSST.getMdnNumber();
        }
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            str = r.getMsisdnNumber();
        }
        return str;
    }

    public String getCdmaPrlVersion() {
        return this.mSST.getPrlVersion();
    }

    public String getCdmaMin() {
        return this.mSST.getCdmaMin();
    }

    public boolean isMinInfoReady() {
        return this.mSST.isMinInfoReady();
    }

    public String getMsisdn() {
        String str = null;
        logForTest("getMsisdn", "get phone number..****");
        if (isPhoneTypeGsm()) {
            IccRecords r = (IccRecords) this.mIccRecords.get();
            if (r != null) {
                str = r.getMsisdnNumber();
            }
            return str;
        } else if (isPhoneTypeCdmaLte()) {
            if (this.mSimRecords != null) {
                str = this.mSimRecords.getMsisdnNumber();
            }
            return str;
        } else {
            loge("getMsisdn: not expected on CDMA");
            return null;
        }
    }

    public String getLine1AlphaTag() {
        String str = null;
        if (isPhoneTypeGsm()) {
            IccRecords r = (IccRecords) this.mIccRecords.get();
            if (r != null) {
                str = r.getMsisdnAlphaTag();
            }
            return str;
        }
        loge("getLine1AlphaTag: not possible in CDMA");
        return null;
    }

    public boolean setLine1Number(String alphaTag, String number, Message onComplete) {
        if (isPhoneTypeGsm()) {
            IccRecords r = (IccRecords) this.mIccRecords.get();
            if (r == null) {
                return false;
            }
            r.setMsisdnNumber(alphaTag, number, onComplete);
            return true;
        }
        loge("setLine1Number: not possible in CDMA");
        return false;
    }

    public void setVoiceMailNumber(String alphaTag, String voiceMailNumber, Message onComplete) {
        if ("".equals(voiceMailNumber)) {
            PreferenceManager.getDefaultSharedPreferences(getContext()).edit().putString(VM_NUMBER, voiceMailNumber).commit();
        }
        this.mVmNumber = voiceMailNumber;
        Message resp = obtainMessage(20, 0, 0, onComplete);
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            r.setVoiceMailNumber(alphaTag, this.mVmNumber, resp);
        }
    }

    private boolean isValidCommandInterfaceCFReason(int commandInterfaceCFReason) {
        switch (commandInterfaceCFReason) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                return true;
            default:
                return false;
        }
    }

    public String getSystemProperty(String property, String defValue) {
        if (!isPhoneTypeGsm() && !isPhoneTypeCdmaLte()) {
            return super.getSystemProperty(property, defValue);
        }
        if (getUnitTestMode()) {
            return null;
        }
        return TelephonyManager.getTelephonyProperty(this.mPhoneId, property, defValue);
    }

    private boolean isValidCommandInterfaceCFAction(int commandInterfaceCFAction) {
        switch (commandInterfaceCFAction) {
            case 0:
            case 1:
            case 3:
            case 4:
                return true;
            default:
                return false;
        }
    }

    private boolean isCfEnable(int action) {
        return action == 1 || action == 3;
    }

    public void getCallForwardingOption(int commandInterfaceCFReason, Message onComplete) {
        if (isPhoneTypeGsm()) {
            Phone imsPhone = this.mImsPhone;
            if (imsPhone != null && imsPhone.mHwImsPhone.isUtEnable()) {
                imsPhone.getCallForwardingOption(commandInterfaceCFReason, onComplete);
            } else if (isValidCommandInterfaceCFReason(commandInterfaceCFReason)) {
                Message resp;
                logd("requesting call forwarding query.");
                if (commandInterfaceCFReason == 0) {
                    resp = obtainMessage(13, onComplete);
                } else {
                    resp = onComplete;
                }
                this.mCi.queryCallForwardStatus(commandInterfaceCFReason, 0, null, resp);
            }
        } else {
            loge("getCallForwardingOption: not possible in CDMA");
        }
    }

    public void setCallForwardingOption(int commandInterfaceCFAction, int commandInterfaceCFReason, String dialingNumber, int timerSeconds, Message onComplete) {
        if (isPhoneTypeGsm()) {
            Phone imsPhone = this.mImsPhone;
            if (imsPhone != null && imsPhone.mHwImsPhone.isUtEnable()) {
                imsPhone.setCallForwardingOption(commandInterfaceCFAction, commandInterfaceCFReason, dialingNumber, timerSeconds, onComplete);
            } else if (isValidCommandInterfaceCFAction(commandInterfaceCFAction) && isValidCommandInterfaceCFReason(commandInterfaceCFReason)) {
                Message resp;
                if (commandInterfaceCFReason == 0) {
                    int i;
                    Cfu cfu = new Cfu(dialingNumber, onComplete);
                    if (isCfEnable(commandInterfaceCFAction)) {
                        i = 1;
                    } else {
                        i = 0;
                    }
                    resp = obtainMessage(12, i, 0, cfu);
                } else {
                    resp = onComplete;
                }
                this.mCi.setCallForward(commandInterfaceCFAction, commandInterfaceCFReason, 1, processPlusSymbol(dialingNumber, getSubscriberId()), timerSeconds, resp);
            }
        } else {
            loge("setCallForwardingOption: not possible in CDMA");
        }
    }

    public void setCallForwardingOption(int commandInterfaceCFAction, int commandInterfaceCFReason, String dialingNumber, int serviceClass, int timerSeconds, Message onComplete) {
        if (isPhoneTypeGsm()) {
            Phone imsPhone = this.mImsPhone;
            if (imsPhone != null && imsPhone.mHwImsPhone.isUtEnable()) {
                imsPhone.setCallForwardingOption(commandInterfaceCFAction, commandInterfaceCFReason, dialingNumber, serviceClass, timerSeconds, onComplete);
            } else if (isValidCommandInterfaceCFAction(commandInterfaceCFAction) && isValidCommandInterfaceCFReason(commandInterfaceCFReason)) {
                Message resp;
                if (commandInterfaceCFReason == 0) {
                    int i;
                    Cfu cfu = new Cfu(dialingNumber, onComplete);
                    if (isCfEnable(commandInterfaceCFAction)) {
                        i = 1;
                    } else {
                        i = 0;
                    }
                    resp = obtainMessage(12, i, 0, cfu);
                } else {
                    resp = onComplete;
                }
                this.mCi.setCallForward(commandInterfaceCFAction, commandInterfaceCFReason, serviceClass, processPlusSymbol(dialingNumber, getSubscriberId()), timerSeconds, resp);
            }
        } else {
            loge("setCallForwardingOption: not possible in CDMA");
        }
    }

    public void getOutgoingCallerIdDisplay(Message onComplete) {
        if (isPhoneTypeGsm()) {
            Phone imsPhone = this.mImsPhone;
            if (imsPhone == null || !imsPhone.mHwImsPhone.isUtEnable()) {
                this.mCi.getCLIR(onComplete);
            } else {
                imsPhone.getOutgoingCallerIdDisplay(onComplete);
                return;
            }
        }
        loge("getOutgoingCallerIdDisplay: not possible in CDMA");
    }

    public void setOutgoingCallerIdDisplay(int commandInterfaceCLIRMode, Message onComplete) {
        if (isPhoneTypeGsm()) {
            Phone imsPhone = this.mImsPhone;
            if (imsPhone == null || !imsPhone.mHwImsPhone.isUtEnable()) {
                this.mCi.setCLIR(commandInterfaceCLIRMode, obtainMessage(18, commandInterfaceCLIRMode, 0, onComplete));
            } else {
                imsPhone.setOutgoingCallerIdDisplay(commandInterfaceCLIRMode, onComplete);
                return;
            }
        }
        loge("setOutgoingCallerIdDisplay: not possible in CDMA");
    }

    public void getCallWaiting(Message onComplete) {
        if (isPhoneTypeGsm()) {
            Phone imsPhone = this.mImsPhone;
            if (imsPhone == null || !imsPhone.mHwImsPhone.isUtEnable()) {
                this.mCi.queryCallWaiting(0, onComplete);
            } else {
                imsPhone.getCallWaiting(onComplete);
                return;
            }
        }
        this.mCi.queryCallWaiting(1, onComplete);
    }

    public void setCallWaiting(boolean enable, Message onComplete) {
        if (isPhoneTypeGsm()) {
            Phone imsPhone = this.mImsPhone;
            if (imsPhone == null || !imsPhone.mHwImsPhone.isUtEnable()) {
                this.mCi.setCallWaiting(enable, 1, onComplete);
            } else {
                imsPhone.setCallWaiting(enable, onComplete);
                return;
            }
        }
        loge("method setCallWaiting is NOT supported in CDMA!");
    }

    public void getAvailableNetworks(Message response) {
        if (isPhoneTypeGsm() || isPhoneTypeCdmaLte()) {
            this.mCi.getAvailableNetworks(getCustAvailableNetworksMessage(response));
            return;
        }
        loge("getAvailableNetworks: not possible in CDMA");
    }

    public void getNeighboringCids(Message response) {
        if (isPhoneTypeGsm()) {
            this.mCi.getNeighboringCids(response);
        } else if (response != null) {
            AsyncResult.forMessage(response).exception = new CommandException(Error.REQUEST_NOT_SUPPORTED);
            response.sendToTarget();
        }
    }

    public void setUiTTYMode(int uiTtyMode, Message onComplete) {
        if (this.mImsPhone != null) {
            this.mImsPhone.setUiTTYMode(uiTtyMode, onComplete);
        }
    }

    public void setMute(boolean muted) {
        if (this.mCT != null) {
            this.mCT.setMute(muted);
        }
    }

    public boolean getMute() {
        if (this.mCT == null) {
            return false;
        }
        return this.mCT.getMute();
    }

    public void getDataCallList(Message response) {
        this.mCi.getDataCallList(response);
    }

    public void updateServiceLocation() {
        this.mSST.enableSingleLocationUpdate();
    }

    public void enableLocationUpdates() {
        this.mSST.enableLocationUpdates();
    }

    public void disableLocationUpdates() {
        this.mSST.disableLocationUpdates();
    }

    public boolean getDataRoamingEnabled() {
        return this.mDcTracker.getDataOnRoamingEnabled();
    }

    public void setDataRoamingEnabled(boolean enable) {
        this.mDcTracker.setDataOnRoamingEnabled(enable);
    }

    public void registerForCdmaOtaStatusChange(Handler h, int what, Object obj) {
        this.mCi.registerForCdmaOtaProvision(h, what, obj);
    }

    public void unregisterForCdmaOtaStatusChange(Handler h) {
        this.mCi.unregisterForCdmaOtaProvision(h);
    }

    public void registerForSubscriptionInfoReady(Handler h, int what, Object obj) {
        this.mSST.registerForSubscriptionInfoReady(h, what, obj);
    }

    public void unregisterForSubscriptionInfoReady(Handler h) {
        this.mSST.unregisterForSubscriptionInfoReady(h);
    }

    public void setOnEcbModeExitResponse(Handler h, int what, Object obj) {
        this.mEcmExitRespRegistrant = new Registrant(h, what, obj);
    }

    public void unsetOnEcbModeExitResponse(Handler h) {
        this.mEcmExitRespRegistrant.clear();
    }

    public void registerForCallWaiting(Handler h, int what, Object obj) {
        this.mCT.registerForCallWaiting(h, what, obj);
    }

    public void unregisterForCallWaiting(Handler h) {
        this.mCT.unregisterForCallWaiting(h);
    }

    public boolean getDataEnabled() {
        return this.mDcTracker.getDataEnabled();
    }

    public void setDataEnabled(boolean enable) {
        logForTest("setDataEnabled", "setDataEnabled to.." + enable);
        if (HwSystemManager.allowOp(null, 4194304, getDataEnabled())) {
            if (HwTelephonyFactory.getHwDataConnectionManager().needSetUserDataEnabled(enable)) {
                this.mDcTracker.setDataEnabled(enable);
            } else {
                logd("setDataEnabled ignored by HwDataConnectionManager");
            }
        }
    }

    public void onMMIDone(MmiCode mmi) {
        if (!this.mPendingMMIs.remove(mmi)) {
            if (!isPhoneTypeGsm()) {
                return;
            }
            if (!(mmi.isUssdRequest() || ((GsmMmiCode) mmi).isSsInfo())) {
                return;
            }
        }
        this.mMmiCompleteRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
    }

    private void onNetworkInitiatedUssd(MmiCode mmi) {
        this.mMmiCompleteRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
    }

    private void onIncomingUSSD(int ussdMode, String ussdMessage) {
        if (!isPhoneTypeGsm()) {
            loge("onIncomingUSSD: not expected on GSM");
        }
        boolean isUssdRequest = ussdMode == 1;
        boolean isUssdError = (ussdMode == 0 || ussdMode == 1 || ussdMode == 12) ? false : ussdMode != 2;
        boolean isUssdRelease = ussdMode == 2;
        GsmMmiCode gsmMmiCode = null;
        int s = this.mPendingMMIs.size();
        for (int i = 0; i < s; i++) {
            if (((GsmMmiCode) this.mPendingMMIs.get(i)).isPendingUSSD()) {
                gsmMmiCode = (GsmMmiCode) this.mPendingMMIs.get(i);
                break;
            }
        }
        if (gsmMmiCode != null) {
            if (GsmMmiCode.USSD_REMOVE_ERROR_MSG) {
                gsmMmiCode.setIncomingUSSD(true);
            }
            if (isUssdRelease) {
                gsmMmiCode.onUssdFinished(ussdMessage, isUssdRequest);
            } else if (isUssdError) {
                gsmMmiCode.onUssdFinishedError();
            } else {
                gsmMmiCode.onUssdFinished(ussdMessage, isUssdRequest);
            }
        } else if (!isUssdError && ussdMessage != null) {
            onNetworkInitiatedUssd(GsmMmiCode.newNetworkInitiatedUssd(ussdMessage, isUssdRequest, this, (UiccCardApplication) this.mUiccApplication.get()));
        }
    }

    private void syncClirSetting() {
        int clirSetting = PreferenceManager.getDefaultSharedPreferences(getContext()).getInt(Phone.CLIR_KEY + getPhoneId(), -1);
        if (clirSetting >= 0) {
            this.mCi.setCLIR(clirSetting, null);
        }
    }

    private void handleRadioAvailable() {
        this.mCi.getBasebandVersion(obtainMessage(6));
        if (isPhoneTypeGsm()) {
            this.mCi.getIMEI(obtainMessage(9));
            this.mCi.getIMEISV(obtainMessage(10));
        } else {
            this.mCi.getDeviceIdentity(obtainMessage(21));
        }
        this.mCi.getRadioCapability(obtainMessage(35));
        startLceAfterRadioIsAvailable();
    }

    private void handleRadioOn() {
        this.mCi.getVoiceRadioTechnology(obtainMessage(40));
        if (!isPhoneTypeGsm()) {
            this.mCdmaSubscriptionSource = this.mCdmaSSM.getCdmaSubscriptionSource();
        }
        this.mCi.getRadioCapability(obtainMessage(35));
        setPreferredNetworkTypeIfSimLoaded();
    }

    private void handleRadioOffOrNotAvailable() {
        if (isPhoneTypeGsm()) {
            for (int i = this.mPendingMMIs.size() - 1; i >= 0; i--) {
                if (((GsmMmiCode) this.mPendingMMIs.get(i)).isPendingUSSD()) {
                    ((GsmMmiCode) this.mPendingMMIs.get(i)).onUssdFinishedError();
                }
            }
        }
        Phone imsPhone = this.mImsPhone;
        if (imsPhone != null) {
            imsPhone.getServiceState().setStateOff();
        }
        this.mRadioOffOrNotAvailableRegistrants.notifyRegistrants();
    }

    public void handleMessage(Message msg) {
        if (!beforeHandleMessage(msg)) {
            AsyncResult ar;
            Message onComplete;
            switch (msg.what) {
                case 1:
                    handleRadioAvailable();
                    updateReduceSARState();
                    break;
                case 2:
                    logd("Event EVENT_SSN Received");
                    if (isPhoneTypeGsm()) {
                        ar = (AsyncResult) msg.obj;
                        this.mSSNResult = ar;
                        SuppServiceNotification not = ar.result;
                        this.mSsnRegistrants.notifyRegistrants(ar);
                        break;
                    }
                    break;
                case 3:
                    if (isPhoneTypeGsm()) {
                        updateCurrentCarrierInProvider();
                        String imsi = getVmSimImsi();
                        String imsiFromSIM = getSubscriberId();
                        if (!(imsi == null || imsiFromSIM == null || imsiFromSIM.equals(imsi))) {
                            storeVoiceMailNumber(null, false);
                            setVmSimImsi(null);
                        }
                    }
                    this.mSimRecordsLoadedRegistrants.notifyRegistrants();
                    updateVoiceMail();
                    processEccNumber(this.mSST);
                    break;
                case 5:
                    logd("Event EVENT_RADIO_ON Received");
                    handleRadioOn();
                    break;
                case 6:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception == null) {
                        logd("Baseband version: " + ar.result);
                        TelephonyManager.from(this.mContext).setBasebandVersionForPhone(getPhoneId(), (String) ar.result);
                        break;
                    }
                    break;
                case 7:
                    String[] ussdResult = (String[]) ((AsyncResult) msg.obj).result;
                    if (ussdResult.length > 1) {
                        try {
                            onIncomingUSSD(Integer.parseInt(ussdResult[0]), ussdResult[1]);
                            break;
                        } catch (NumberFormatException e) {
                            Rlog.w(this.LOG_TAG, "error parsing USSD");
                            break;
                        }
                    }
                    break;
                case 8:
                    logd("Event EVENT_RADIO_OFF_OR_NOT_AVAILABLE Received");
                    handleRadioOffOrNotAvailable();
                    break;
                case 9:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception == null) {
                        this.mImei = (String) ar.result;
                        logForImei(GSM_PHONE);
                        getContext().sendBroadcast(new Intent("com.android.huawei.DM.IMEI_READY"));
                        break;
                    }
                    break;
                case 10:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception == null) {
                        this.mImeiSv = (String) ar.result;
                        break;
                    }
                    break;
                case 12:
                    ar = (AsyncResult) msg.obj;
                    IccRecords r = (IccRecords) this.mIccRecords.get();
                    Cfu cfu = ar.userObj;
                    if (ar.exception == null && r != null) {
                        setVoiceCallForwardingFlag(1, msg.arg1 == 1, cfu.mSetCfNumber);
                    }
                    if (cfu.mOnComplete != null) {
                        AsyncResult.forMessage(cfu.mOnComplete, ar.result, ar.exception);
                        cfu.mOnComplete.sendToTarget();
                        break;
                    }
                    break;
                case 13:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception == null) {
                        handleCfuQueryResult((CallForwardInfo[]) ar.result);
                    }
                    onComplete = (Message) ar.userObj;
                    if (onComplete != null) {
                        AsyncResult.forMessage(onComplete, ar.result, ar.exception);
                        onComplete.sendToTarget();
                        break;
                    }
                    break;
                case 18:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception == null) {
                        saveClirSetting(msg.arg1);
                    }
                    onComplete = (Message) ar.userObj;
                    if (onComplete != null) {
                        AsyncResult.forMessage(onComplete, ar.result, ar.exception);
                        onComplete.sendToTarget();
                        break;
                    }
                    break;
                case 19:
                    logd("Event EVENT_REGISTERED_TO_NETWORK Received");
                    if (isPhoneTypeGsm()) {
                        syncClirSetting();
                        break;
                    }
                    break;
                case 20:
                    ar = (AsyncResult) msg.obj;
                    if ((isPhoneTypeGsm() && IccVmNotSupportedException.class.isInstance(ar.exception)) || (!isPhoneTypeGsm() && IccException.class.isInstance(ar.exception))) {
                        storeVoiceMailNumber(this.mVmNumber, true);
                        ar.exception = null;
                    }
                    onComplete = ar.userObj;
                    if (onComplete != null) {
                        AsyncResult.forMessage(onComplete, ar.result, ar.exception);
                        onComplete.sendToTarget();
                        break;
                    }
                    break;
                case 21:
                    ar = msg.obj;
                    if (ar.exception == null) {
                        String[] respId = (String[]) ar.result;
                        this.mImei = respId[0];
                        logForImei(CDMA_PHONE);
                        this.mImeiSv = respId[1];
                        this.mEsn = respId[2];
                        this.mMeid = respId[3];
                        if (respId.length > 4) {
                            this.mUimid = respId[4];
                            SystemProperties.set("persist.radio.hwuimid", this.mUimid);
                            break;
                        }
                    }
                    break;
                case 22:
                    logd("Event EVENT_RUIM_RECORDS_LOADED Received");
                    updateCurrentCarrierInProvider();
                    processEccNumber(this.mSST);
                    break;
                case SmsHeader.ELT_ID_OBJECT_DISTR_INDICATOR /*23*/:
                    logd("Event EVENT_NV_READY Received");
                    prepareEri();
                    SubscriptionInfoUpdater subInfoRecordUpdater = PhoneFactory.getSubInfoRecordUpdater();
                    if (subInfoRecordUpdater != null) {
                        subInfoRecordUpdater.updateSubIdForNV(this.mPhoneId);
                        break;
                    }
                    break;
                case 25:
                    handleEnterEmergencyCallbackMode(msg);
                    break;
                case SmsHeader.ELT_ID_EXTENDED_OBJECT_DATA_REQUEST_CMD /*26*/:
                    handleExitEmergencyCallbackMode(msg);
                    break;
                case CallFailCause.CALL_FAIL_DESTINATION_OUT_OF_ORDER /*27*/:
                    logd("EVENT_CDMA_SUBSCRIPTION_SOURCE_CHANGED");
                    this.mCdmaSubscriptionSource = this.mCdmaSSM.getCdmaSubscriptionSource();
                    break;
                case CallFailCause.INVALID_NUMBER /*28*/:
                    ar = (AsyncResult) msg.obj;
                    if (!this.mSST.mSS.getIsManualSelection()) {
                        logd("SET_NETWORK_SELECTION_AUTOMATIC: already automatic, ignore");
                        break;
                    }
                    setNetworkSelectionModeAutomatic((Message) ar.result);
                    logd("SET_NETWORK_SELECTION_AUTOMATIC: set to automatic");
                    break;
                case CallFailCause.FACILITY_REJECTED /*29*/:
                    processIccRecordEvents(((Integer) ((AsyncResult) msg.obj).result).intValue());
                    break;
                case 35:
                    ar = (AsyncResult) msg.obj;
                    RadioCapability rc = ar.result;
                    if (ar.exception != null) {
                        Rlog.d(this.LOG_TAG, "get phone radio capability fail, no need to change mRadioCapability");
                    } else {
                        radioCapabilityUpdated(rc);
                    }
                    Rlog.d(this.LOG_TAG, "EVENT_GET_RADIO_CAPABILITY: phone rc: " + rc);
                    break;
                case 36:
                    ar = (AsyncResult) msg.obj;
                    logd("Event EVENT_SS received");
                    if (isPhoneTypeGsm()) {
                        new GsmMmiCode(this, (UiccCardApplication) this.mUiccApplication.get()).processSsData(ar);
                        break;
                    }
                    break;
                case 39:
                case RadioNVItems.RIL_NV_MIP_PROFILE_MN_HA_SS /*40*/:
                    String what = msg.what == 39 ? "EVENT_VOICE_RADIO_TECH_CHANGED" : "EVENT_REQUEST_VOICE_RADIO_TECH_DONE";
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception == null) {
                        if (ar.result != null && ((int[]) ar.result).length != 0) {
                            int newVoiceTech = ((int[]) ar.result)[0];
                            logd(what + ": newVoiceTech=" + newVoiceTech);
                            phoneObjectUpdater(newVoiceTech);
                            break;
                        }
                        loge(what + ": has no tech!");
                        break;
                    }
                    loge(what + ": exception=" + ar.exception);
                    break;
                    break;
                case 41:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception == null && ar.result != null) {
                        this.mRilVersion = ((Integer) ar.result).intValue();
                        break;
                    }
                    logd("Unexpected exception on EVENT_RIL_CONNECTED");
                    this.mRilVersion = -1;
                    break;
                    break;
                case 42:
                    phoneObjectUpdater(msg.arg1);
                    break;
                case CallFailCause.ACCESS_INFORMATION_DISCARDED /*43*/:
                    if (!this.mContext.getResources().getBoolean(17957018)) {
                        this.mCi.getVoiceRadioTechnology(obtainMessage(40));
                    }
                    ImsManager.updateImsServiceConfig(this.mContext, this.mPhoneId, true);
                    PersistableBundle b = ((CarrierConfigManager) getContext().getSystemService("carrier_config")).getConfigForSubId(getSubId());
                    if (b != null) {
                        boolean broadcastEmergencyCallStateChanges = b.getBoolean("broadcast_emergency_call_state_changes_bool");
                        logd("broadcastEmergencyCallStateChanges =" + broadcastEmergencyCallStateChanges);
                        setBroadcastEmergencyCallStateChanges(broadcastEmergencyCallStateChanges);
                    } else {
                        loge("didn't get broadcastEmergencyCallStateChanges from carrier config");
                    }
                    if (b != null) {
                        int config_cdma_roaming_mode = b.getInt("cdma_roaming_mode_int");
                        int current_cdma_roaming_mode = Global.getInt(getContext().getContentResolver(), "roaming_settings", -1);
                        switch (config_cdma_roaming_mode) {
                            case -1:
                                if (current_cdma_roaming_mode != config_cdma_roaming_mode) {
                                    logd("cdma_roaming_mode is going to changed to " + current_cdma_roaming_mode);
                                    setCdmaRoamingPreference(current_cdma_roaming_mode, obtainMessage(44));
                                    break;
                                }
                                break;
                            case 0:
                            case 1:
                            case 2:
                                logd("cdma_roaming_mode is going to changed to " + config_cdma_roaming_mode);
                                setCdmaRoamingPreference(config_cdma_roaming_mode, obtainMessage(44));
                                break;
                        }
                        loge("Invalid cdma_roaming_mode settings: " + config_cdma_roaming_mode);
                    } else {
                        loge("didn't get the cdma_roaming_mode changes from the carrier config.");
                    }
                    prepareEri();
                    if (!isPhoneTypeGsm()) {
                        this.mSST.pollState();
                        break;
                    }
                    break;
                case 44:
                    logd("cdma_roaming_mode change is done");
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
            afterHandleMessage(msg);
        }
    }

    private void logForImei(String phoneType) {
        if (this.mImei == null) {
            logd(phoneType + " mImei is null");
        } else if (6 > this.mImei.length()) {
            logd(phoneType + " mImei is in wrong format:" + this.mImei);
        } else {
            logd(phoneType + " mImei:****" + this.mImei.substring(this.mImei.length() - 6, this.mImei.length()));
        }
    }

    public UiccCardApplication getUiccCardApplication() {
        if (isPhoneTypeGsm()) {
            return this.mUiccController.getUiccCardApplication(this.mPhoneId, 1);
        }
        return this.mUiccController.getUiccCardApplication(this.mPhoneId, 2);
    }

    protected void onUpdateIccAvailability() {
        if (this.mUiccController != null) {
            UiccCardApplication newUiccApplication;
            if (isPhoneTypeGsm() || isPhoneTypeCdmaLte()) {
                newUiccApplication = this.mUiccController.getUiccCardApplication(this.mPhoneId, 3);
                IsimUiccRecords newIsimUiccRecords = null;
                if (newUiccApplication != null) {
                    newIsimUiccRecords = (IsimUiccRecords) newUiccApplication.getIccRecords();
                    logd("New ISIM application found");
                }
                this.mIsimUiccRecords = newIsimUiccRecords;
            }
            if (this.mSimRecords != null) {
                this.mSimRecords.unregisterForRecordsLoaded(this);
            }
            if (isPhoneTypeCdmaLte()) {
                newUiccApplication = this.mUiccController.getUiccCardApplication(this.mPhoneId, 1);
                SIMRecords sIMRecords = null;
                if (newUiccApplication != null) {
                    sIMRecords = (SIMRecords) newUiccApplication.getIccRecords();
                }
                this.mSimRecords = sIMRecords;
                if (this.mSimRecords != null) {
                    this.mSimRecords.registerForRecordsLoaded(this, 3, null);
                }
            } else {
                this.mSimRecords = null;
            }
            newUiccApplication = getUiccCardApplication();
            if (!isPhoneTypeGsm() && newUiccApplication == null) {
                logd("can't find 3GPP2 application; trying APP_FAM_3GPP");
                newUiccApplication = this.mUiccController.getUiccCardApplication(this.mPhoneId, 1);
            }
            UiccCardApplication app = (UiccCardApplication) this.mUiccApplication.get();
            if (newUiccApplication != null) {
                this.newAppType = newUiccApplication.getType();
            }
            logd("newAppType = " + this.newAppType + ", oldAppType = " + this.oldAppType);
            if (!(app == newUiccApplication && (this.newAppType == AppType.APPTYPE_UNKNOWN || this.oldAppType == AppType.APPTYPE_UNKNOWN || this.newAppType == this.oldAppType))) {
                if (app != null) {
                    logd("Removing stale icc objects.");
                    if (this.mIccRecords.get() != null) {
                        unregisterForIccRecordEvents();
                        this.mIccPhoneBookIntManager.updateIccRecords(null);
                    }
                    this.mIccRecords.set(null);
                    this.mUiccApplication.set(null);
                }
                if (newUiccApplication != null) {
                    logd("New Uicc application found");
                    this.oldAppType = newUiccApplication.getType();
                    logd("New Uicc application found. type = " + newUiccApplication.getType());
                    this.mUiccApplication.set(newUiccApplication);
                    this.mIccRecords.set(newUiccApplication.getIccRecords());
                    registerForIccRecordEvents();
                    this.mIccPhoneBookIntManager.updateIccRecords((IccRecords) this.mIccRecords.get());
                }
            }
        }
    }

    private void processIccRecordEvents(int eventCode) {
        switch (eventCode) {
            case 1:
                notifyCallForwardingIndicator();
                return;
            default:
                return;
        }
    }

    public boolean updateCurrentCarrierInProvider() {
        if (!isPhoneTypeGsm() && !isPhoneTypeCdmaLte()) {
            return true;
        }
        long currentDds = (long) PhoneFactory.getTopPrioritySubscriptionId();
        String operatorNumeric = getOperatorNumeric();
        logd("updateCurrentCarrierInProvider: mSubId = " + getSubId() + " currentDds = " + currentDds + " operatorNumeric = " + operatorNumeric);
        if (!TextUtils.isEmpty(operatorNumeric) && ((long) getSubId()) == currentDds) {
            try {
                String currentOperatorNumeric = HwTelephonyFactory.getHwPhoneManager().isRoamingBrokerActivated() ? HwTelephonyFactory.getHwPhoneManager().getRoamingBrokerOperatorNumeric() : operatorNumeric;
                Uri uri = Uri.withAppendedPath(Carriers.CONTENT_URI, Carriers.CURRENT);
                ContentValues map = new ContentValues();
                map.put("numeric", currentOperatorNumeric);
                this.mContext.getContentResolver().insert(uri, map);
                return true;
            } catch (SQLException e) {
                Rlog.e(this.LOG_TAG, "Can't store current operator", e);
            }
        }
        return false;
    }

    private boolean updateCurrentCarrierInProvider(String operatorNumeric) {
        if (isPhoneTypeCdma() || (isPhoneTypeCdmaLte() && this.mUiccController.getUiccCardApplication(this.mPhoneId, 1) == null)) {
            logd("CDMAPhone: updateCurrentCarrierInProvider called");
            if (!TextUtils.isEmpty(operatorNumeric)) {
                try {
                    Uri uri = Uri.withAppendedPath(Carriers.CONTENT_URI, Carriers.CURRENT);
                    ContentValues map = new ContentValues();
                    map.put("numeric", operatorNumeric);
                    logd("updateCurrentCarrierInProvider from system: numeric=" + operatorNumeric);
                    getContext().getContentResolver().insert(uri, map);
                    logd("update mccmnc=" + operatorNumeric);
                    return true;
                } catch (SQLException e) {
                    Rlog.e(this.LOG_TAG, "Can't store current operator", e);
                }
            }
            return false;
        }
        logd("updateCurrentCarrierInProvider not updated X retVal=true");
        return true;
    }

    private void handleCfuQueryResult(CallForwardInfo[] infos) {
        boolean z = false;
        if (((IccRecords) this.mIccRecords.get()) == null) {
            return;
        }
        if (infos == null || infos.length == 0) {
            setVoiceCallForwardingFlag(1, false, null);
            return;
        }
        int s = infos.length;
        for (int i = 0; i < s; i++) {
            if ((infos[i].serviceClass & 1) != 0) {
                if (infos[i].status == 1) {
                    z = true;
                }
                setVoiceCallForwardingFlag(1, z, infos[i].number);
                return;
            }
        }
    }

    public IccPhoneBookInterfaceManager getIccPhoneBookInterfaceManager() {
        return this.mIccPhoneBookIntManager;
    }

    public void registerForEriFileLoaded(Handler h, int what, Object obj) {
        this.mEriFileLoadedRegistrants.add(new Registrant(h, what, obj));
    }

    public void unregisterForEriFileLoaded(Handler h) {
        this.mEriFileLoadedRegistrants.remove(h);
    }

    public void prepareEri() {
        if (this.mEriManager == null) {
            Rlog.e(this.LOG_TAG, "PrepareEri: Trying to access stale objects");
            return;
        }
        this.mEriManager.loadEriFile();
        if (this.mEriManager.isEriFileLoaded()) {
            logd("ERI read, notify registrants");
            this.mEriFileLoadedRegistrants.notifyRegistrants();
        }
    }

    public boolean isEriFileLoaded() {
        return this.mEriManager.isEriFileLoaded();
    }

    public void activateCellBroadcastSms(int activate, Message response) {
        loge("[GsmCdmaPhone] activateCellBroadcastSms() is obsolete; use SmsManager");
        response.sendToTarget();
    }

    public void getCellBroadcastSmsConfig(Message response) {
        loge("[GsmCdmaPhone] getCellBroadcastSmsConfig() is obsolete; use SmsManager");
        response.sendToTarget();
    }

    public void setCellBroadcastSmsConfig(int[] configValuesArray, Message response) {
        loge("[GsmCdmaPhone] setCellBroadcastSmsConfig() is obsolete; use SmsManager");
        response.sendToTarget();
    }

    public boolean needsOtaServiceProvisioning() {
        boolean z = false;
        if (isPhoneTypeGsm()) {
            return false;
        }
        if (this.mSST.getOtasp() != 3) {
            z = true;
        }
        return z;
    }

    public boolean isCspPlmnEnabled() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        return r != null ? r.isCspPlmnEnabled() : false;
    }

    public boolean isManualNetSelAllowed() {
        int nwMode = Global.getInt(this.mContext.getContentResolver(), "preferred_network_mode" + getSubId(), Phone.PREFERRED_NT_MODE);
        logd("isManualNetSelAllowed in mode = " + nwMode);
        if (isManualSelProhibitedInGlobalMode() && (nwMode == 10 || nwMode == 7)) {
            logd("Manual selection not supported in mode = " + nwMode);
            return false;
        }
        logd("Manual selection is supported in mode = " + nwMode);
        return true;
    }

    private boolean isManualSelProhibitedInGlobalMode() {
        boolean isProhibited = false;
        String configString = getContext().getResources().getString(17039462);
        if (!TextUtils.isEmpty(configString)) {
            String[] configArray = configString.split(";");
            if (configArray != null && ((configArray.length == 1 && configArray[0].equalsIgnoreCase("true")) || (configArray.length == 2 && !TextUtils.isEmpty(configArray[1]) && configArray[0].equalsIgnoreCase("true") && isMatchGid(configArray[1])))) {
                isProhibited = true;
            }
        }
        logd("isManualNetSelAllowedInGlobal in current carrier is " + isProhibited);
        return isProhibited;
    }

    private void registerForIccRecordEvents() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            if (isPhoneTypeGsm() || (r instanceof SIMRecords)) {
                r.registerForNetworkSelectionModeAutomatic(this, 28, null);
                r.registerForRecordsEvents(this, 29, null);
                r.registerForRecordsLoaded(this, 3, null);
                r.registerForImsiReady(this, AbstractPhoneBase.EVENT_GET_IMSI_DONE, null);
                registerForCsgRecordsLoadedEvent();
            } else {
                r.registerForRecordsLoaded(this, 22, null);
            }
        }
    }

    private void unregisterForIccRecordEvents() {
        IccRecords r = (IccRecords) this.mIccRecords.get();
        if (r != null) {
            r.unregisterForNetworkSelectionModeAutomatic(this);
            r.unregisterForRecordsEvents(this);
            r.unregisterForRecordsLoaded(this);
            unregisterForCsgRecordsLoadedEvent();
        }
    }

    public void exitEmergencyCallbackMode() {
        if (!isPhoneTypeGsm()) {
            if (this.mWakeLock.isHeld()) {
                this.mWakeLock.release();
            }
            this.mCi.exitEmergencyCallbackMode(obtainMessage(26));
        } else if (this.mImsPhone != null) {
            this.mImsPhone.exitEmergencyCallbackMode();
        }
    }

    private void handleEnterEmergencyCallbackMode(Message msg) {
        Rlog.d(this.LOG_TAG, "handleEnterEmergencyCallbackMode,mIsPhoneInEcmState= " + this.mIsPhoneInEcmState);
        if (!this.mIsPhoneInEcmState) {
            this.mIsPhoneInEcmState = true;
            sendEmergencyCallbackModeChange();
            setSystemProperty("ril.cdma.inecmmode", "true");
            postDelayed(this.mExitEcmRunnable, SystemProperties.getLong("ro.cdma.ecmexittimer", 300000));
            this.mWakeLock.acquire();
        }
    }

    private void handleExitEmergencyCallbackMode(Message msg) {
        AsyncResult ar = msg.obj;
        Rlog.d(this.LOG_TAG, "handleExitEmergencyCallbackMode,ar.exception , mIsPhoneInEcmState " + ar.exception + this.mIsPhoneInEcmState);
        removeCallbacks(this.mExitEcmRunnable);
        if (this.mEcmExitRespRegistrant != null) {
            this.mEcmExitRespRegistrant.notifyRegistrant(ar);
        }
        if (ar.exception == null) {
            if (this.mWakeLock.isHeld()) {
                this.mWakeLock.release();
            }
            if (this.mIsPhoneInEcmState) {
                this.mIsPhoneInEcmState = false;
                setSystemProperty("ril.cdma.inecmmode", "false");
            }
            sendEmergencyCallbackModeChange();
            this.mDcTracker.setInternalDataEnabled(true);
            notifyEmergencyCallRegistrants(false);
        }
    }

    public void notifyEmergencyCallRegistrants(boolean started) {
        this.mEmergencyCallToggledRegistrants.notifyResult(Integer.valueOf(started ? 1 : 0));
    }

    public void handleTimerInEmergencyCallbackMode(int action) {
        switch (action) {
            case 0:
                postDelayed(this.mExitEcmRunnable, SystemProperties.getLong("ro.cdma.ecmexittimer", 300000));
                this.mEcmTimerResetRegistrants.notifyResult(Boolean.FALSE);
                return;
            case 1:
                removeCallbacks(this.mExitEcmRunnable);
                this.mEcmTimerResetRegistrants.notifyResult(Boolean.TRUE);
                return;
            default:
                Rlog.e(this.LOG_TAG, "handleTimerInEmergencyCallbackMode, unsupported action " + action);
                return;
        }
    }

    private static boolean isIs683OtaSpDialStr(String dialStr) {
        if (dialStr.length() != 4) {
            switch (extractSelCodeFromOtaSpNum(dialStr)) {
                case 0:
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                    return true;
                default:
                    return false;
            }
        } else if (dialStr.equals(IS683A_FEATURE_CODE)) {
            return true;
        } else {
            return false;
        }
    }

    private static int extractSelCodeFromOtaSpNum(String dialStr) {
        int dialStrLen = dialStr.length();
        int sysSelCodeInt = -1;
        if (dialStr.regionMatches(0, IS683A_FEATURE_CODE, 0, 4) && dialStrLen >= 6) {
            sysSelCodeInt = Integer.parseInt(dialStr.substring(4, 6));
        }
        Rlog.d(LOG_TAG_STATIC, "extractSelCodeFromOtaSpNum " + sysSelCodeInt);
        return sysSelCodeInt;
    }

    private static boolean checkOtaSpNumBasedOnSysSelCode(int sysSelCodeInt, String[] sch) {
        try {
            int selRc = Integer.parseInt(sch[1]);
            int i = 0;
            while (i < selRc) {
                if (!(TextUtils.isEmpty(sch[i + 2]) || TextUtils.isEmpty(sch[i + 3]))) {
                    int selMin = Integer.parseInt(sch[i + 2]);
                    int selMax = Integer.parseInt(sch[i + 3]);
                    if (sysSelCodeInt >= selMin && sysSelCodeInt <= selMax) {
                        return true;
                    }
                }
                i++;
            }
            return false;
        } catch (NumberFormatException ex) {
            Rlog.e(LOG_TAG_STATIC, "checkOtaSpNumBasedOnSysSelCode, error", ex);
            return false;
        }
    }

    private boolean isCarrierOtaSpNum(String dialStr) {
        boolean isOtaSpNum = false;
        int sysSelCodeInt = extractSelCodeFromOtaSpNum(dialStr);
        if (sysSelCodeInt == -1) {
            return false;
        }
        if (TextUtils.isEmpty(this.mCarrierOtaSpNumSchema)) {
            Rlog.d(this.LOG_TAG, "isCarrierOtaSpNum,ota schema pattern empty");
        } else {
            Matcher m = pOtaSpNumSchema.matcher(this.mCarrierOtaSpNumSchema);
            Rlog.d(this.LOG_TAG, "isCarrierOtaSpNum,schema" + this.mCarrierOtaSpNumSchema);
            if (m.find()) {
                String[] sch = pOtaSpNumSchema.split(this.mCarrierOtaSpNumSchema);
                if (TextUtils.isEmpty(sch[0]) || !sch[0].equals("SELC")) {
                    if (TextUtils.isEmpty(sch[0]) || !sch[0].equals("FC")) {
                        Rlog.d(this.LOG_TAG, "isCarrierOtaSpNum,ota schema not supported" + sch[0]);
                    } else {
                        if (dialStr.regionMatches(0, sch[2], 0, Integer.parseInt(sch[1]))) {
                            isOtaSpNum = true;
                        } else {
                            Rlog.d(this.LOG_TAG, "isCarrierOtaSpNum,not otasp number");
                        }
                    }
                } else if (sysSelCodeInt != -1) {
                    isOtaSpNum = checkOtaSpNumBasedOnSysSelCode(sysSelCodeInt, sch);
                } else {
                    Rlog.d(this.LOG_TAG, "isCarrierOtaSpNum,sysSelCodeInt is invalid");
                }
            } else {
                Rlog.d(this.LOG_TAG, "isCarrierOtaSpNum,ota schema pattern not right" + this.mCarrierOtaSpNumSchema);
            }
        }
        return isOtaSpNum;
    }

    public boolean isOtaSpNumber(String dialStr) {
        if (isPhoneTypeGsm()) {
            return super.isOtaSpNumber(dialStr);
        }
        boolean z = false;
        String dialableStr = PhoneNumberUtils.extractNetworkPortionAlt(dialStr);
        if (dialableStr != null) {
            z = isIs683OtaSpDialStr(dialableStr);
            if (!z) {
                z = isCarrierOtaSpNum(dialableStr);
            }
        }
        Rlog.d(this.LOG_TAG, "isOtaSpNumber " + z);
        return z;
    }

    public int getCdmaEriIconIndex() {
        if (isPhoneTypeGsm()) {
            return super.getCdmaEriIconIndex();
        }
        return getServiceState().getCdmaEriIconIndex();
    }

    public int getCdmaEriIconMode() {
        if (isPhoneTypeGsm()) {
            return super.getCdmaEriIconMode();
        }
        return getServiceState().getCdmaEriIconMode();
    }

    public String getCdmaEriText() {
        if (isPhoneTypeGsm()) {
            return super.getCdmaEriText();
        }
        return this.mEriManager.getCdmaEriText(getServiceState().getCdmaRoamingIndicator(), getServiceState().getCdmaDefaultRoamingIndicator());
    }

    private void phoneObjectUpdater(int newVoiceRadioTech) {
        logd("phoneObjectUpdater: newVoiceRadioTech=" + newVoiceRadioTech);
        if (newVoiceRadioTech == 14 || newVoiceRadioTech == 0) {
            PersistableBundle b = ((CarrierConfigManager) getContext().getSystemService("carrier_config")).getConfigForSubId(getSubId());
            if (b != null) {
                int volteReplacementRat = b.getInt("volte_replacement_rat_int");
                logd("phoneObjectUpdater: volteReplacementRat=" + volteReplacementRat);
                if (volteReplacementRat != 0) {
                    newVoiceRadioTech = volteReplacementRat;
                }
            } else {
                loge("phoneObjectUpdater: didn't get volteReplacementRat from carrier config");
            }
        }
        if (this.mRilVersion == 6 && getLteOnCdmaMode() == 1) {
            if (getPhoneType() == 2) {
                logd("phoneObjectUpdater: LTE ON CDMA property is set. Use CDMA Phone newVoiceRadioTech=" + newVoiceRadioTech + " mActivePhone=" + getPhoneName());
                return;
            } else {
                logd("phoneObjectUpdater: LTE ON CDMA property is set. Switch to CDMALTEPhone newVoiceRadioTech=" + newVoiceRadioTech + " mActivePhone=" + getPhoneName());
                newVoiceRadioTech = 6;
            }
        } else if (isShuttingDown()) {
            logd("Device is shutting down. No need to switch phone now.");
            return;
        } else {
            boolean matchCdma = ServiceState.isCdma(newVoiceRadioTech);
            boolean matchGsm = ServiceState.isGsm(newVoiceRadioTech);
            if ((matchCdma && getPhoneType() == 2) || (matchGsm && getPhoneType() == 1)) {
                logd("phoneObjectUpdater: No change ignore, newVoiceRadioTech=" + newVoiceRadioTech + " mActivePhone=" + getPhoneName());
                return;
            } else if (!(matchCdma || matchGsm)) {
                loge("phoneObjectUpdater: newVoiceRadioTech=" + newVoiceRadioTech + " doesn't match either CDMA or GSM - error! No phone change");
                return;
            }
        }
        if (newVoiceRadioTech == 0) {
            logd("phoneObjectUpdater: Unknown rat ignore,  newVoiceRadioTech=Unknown. mActivePhone=" + getPhoneName());
        } else if (CallManager.getInstance().hasActiveFgCall(getPhoneId())) {
            logd("has ActiveFgCall, should not updatePhoneObject");
        } else {
            boolean oldPowerState = false;
            if (this.mResetModemOnRadioTechnologyChange && this.mCi.getRadioState().isOn()) {
                oldPowerState = true;
                logd("phoneObjectUpdater: Setting Radio Power to Off");
                this.mCi.setRadioPower(false, null);
            }
            switchVoiceRadioTech(newVoiceRadioTech);
            if (this.mResetModemOnRadioTechnologyChange && oldPowerState) {
                logd("phoneObjectUpdater: Resetting Radio");
                this.mCi.setRadioPower(oldPowerState, null);
            }
            this.mIccCardProxy.setVoiceRadioTech(newVoiceRadioTech);
            Intent intent = new Intent("android.intent.action.RADIO_TECHNOLOGY");
            intent.addFlags(536870912);
            intent.putExtra("phoneName", getPhoneName());
            SubscriptionManager.putPhoneIdAndSubIdExtra(intent, this.mPhoneId);
            ActivityManagerNative.broadcastStickyIntent(intent, null, -1);
        }
    }

    private void switchVoiceRadioTech(int newVoiceRadioTech) {
        logd("Switching Voice Phone : " + getPhoneName() + " >>> " + (ServiceState.isGsm(newVoiceRadioTech) ? GSM_PHONE : CDMA_PHONE));
        if (ServiceState.isCdma(newVoiceRadioTech)) {
            switchPhoneType(6);
            TelephonyManager.setTelephonyProperty(this.mPhoneId, "persist.radio.last_phone_type", CDMA_PHONE);
        } else if (ServiceState.isGsm(newVoiceRadioTech)) {
            switchPhoneType(1);
            TelephonyManager.setTelephonyProperty(this.mPhoneId, "persist.radio.last_phone_type", GSM_PHONE);
        } else {
            loge("deleteAndCreatePhone: newVoiceRadioTech=" + newVoiceRadioTech + " is not CDMA or GSM (error) - aborting!");
        }
    }

    public IccSmsInterfaceManager getIccSmsInterfaceManager() {
        return this.mIccSmsInterfaceManager;
    }

    public void updatePhoneObject(int voiceRadioTech) {
        logd("updatePhoneObject: radioTechnology=" + voiceRadioTech);
        sendMessage(obtainMessage(42, voiceRadioTech, 0, null));
    }

    public void setImsRegistrationState(boolean registered) {
        this.mSST.setImsRegistrationState(registered);
    }

    public boolean getIccRecordsLoaded() {
        return this.mIccCardProxy.getIccRecordsLoaded();
    }

    public IccCard getIccCard() {
        return this.mIccCardProxy;
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("GsmCdmaPhone extends:");
        super.dump(fd, pw, args);
        pw.println(" mPrecisePhoneType=" + this.mPrecisePhoneType);
        pw.println(" mCT=" + this.mCT);
        pw.println(" mSST=" + this.mSST);
        pw.println(" mPendingMMIs=" + this.mPendingMMIs);
        pw.println(" mIccPhoneBookIntManager=" + this.mIccPhoneBookIntManager);
        pw.println(" mCdmaSSM=" + this.mCdmaSSM);
        pw.println(" mCdmaSubscriptionSource=" + this.mCdmaSubscriptionSource);
        pw.println(" mEriManager=" + this.mEriManager);
        pw.println(" mWakeLock=" + this.mWakeLock);
        pw.println(" mIsPhoneInEcmState=" + this.mIsPhoneInEcmState);
        pw.println(" mCarrierOtaSpNumSchema=" + this.mCarrierOtaSpNumSchema);
        if (!isPhoneTypeGsm()) {
            pw.println(" getCdmaEriIconIndex()=" + getCdmaEriIconIndex());
            pw.println(" getCdmaEriIconMode()=" + getCdmaEriIconMode());
            pw.println(" getCdmaEriText()=" + getCdmaEriText());
            pw.println(" isMinInfoReady()=" + isMinInfoReady());
        }
        pw.println(" isCspPlmnEnabled()=" + isCspPlmnEnabled());
        pw.flush();
        pw.println("++++++++++++++++++++++++++++++++");
        try {
            this.mIccCardProxy.dump(fd, pw, args);
        } catch (Exception e) {
            e.printStackTrace();
        }
        pw.flush();
        pw.println("++++++++++++++++++++++++++++++++");
    }

    public boolean setOperatorBrandOverride(String brand) {
        if (this.mUiccController == null) {
            return false;
        }
        UiccCard card = this.mUiccController.getUiccCard(getPhoneId());
        if (card == null) {
            return false;
        }
        boolean status = card.setOperatorBrandOverride(brand);
        if (status) {
            IccRecords iccRecords = (IccRecords) this.mIccRecords.get();
            if (iccRecords != null) {
                TelephonyManager.from(this.mContext).setSimOperatorNameForPhone(getPhoneId(), iccRecords.getServiceProviderName());
            }
            if (this.mSST != null) {
                this.mSST.pollState();
            }
        }
        return status;
    }

    public String getOperatorNumeric() {
        Object obj = null;
        String str = null;
        if (isPhoneTypeGsm()) {
            IccRecords r = (IccRecords) this.mIccRecords.get();
            if (r != null) {
                str = r.getOperatorNumeric();
            }
            if (isCTSimCard(getPhoneId())) {
                Rlog.d(this.LOG_TAG, "sub2 is dobule mode card.");
                str = SystemProperties.get("gsm.national_roaming.apn", "46003");
            }
        } else if (isCTSimCard(getPhoneId())) {
            str = SystemProperties.get(PROPERTY_CDMA_HOME_OPERATOR_NUMERIC, "46003");
            Rlog.d(this.LOG_TAG, "select china telecom hplmn " + str);
            return str;
        } else {
            IccRecords curIccRecords = null;
            if (this.mCdmaSubscriptionSource == 1) {
                str = SystemProperties.get(PROPERTY_CDMA_HOME_OPERATOR_NUMERIC);
            } else if (this.mCdmaSubscriptionSource == 0) {
                curIccRecords = this.mSimRecords;
                if (curIccRecords != null) {
                    str = curIccRecords.getOperatorNumeric();
                } else {
                    curIccRecords = (IccRecords) this.mIccRecords.get();
                    if (curIccRecords != null && (curIccRecords instanceof RuimRecords)) {
                        str = ((RuimRecords) curIccRecords).getRUIMOperatorNumeric();
                    }
                }
            }
            if (str == null) {
                StringBuilder append = new StringBuilder().append("getOperatorNumeric: Cannot retrieve operatorNumeric: mCdmaSubscriptionSource = ").append(this.mCdmaSubscriptionSource).append(" mIccRecords = ");
                if (curIccRecords != null) {
                    obj = Boolean.valueOf(curIccRecords.getRecordsLoaded());
                }
                loge(append.append(obj).toString());
            }
            logd("getOperatorNumeric: mCdmaSubscriptionSource = " + this.mCdmaSubscriptionSource + " operatorNumeric = " + str);
        }
        return str;
    }

    public void notifyEcbmTimerReset(Boolean flag) {
        this.mEcmTimerResetRegistrants.notifyResult(flag);
    }

    public void registerForEcmTimerReset(Handler h, int what, Object obj) {
        this.mEcmTimerResetRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForEcmTimerReset(Handler h) {
        this.mEcmTimerResetRegistrants.remove(h);
    }

    public void setVoiceMessageWaiting(int line, int countWaiting) {
        if (isPhoneTypeGsm()) {
            IccRecords r = (IccRecords) this.mIccRecords.get();
            if (r != null) {
                r.setVoiceMessageWaiting(line, countWaiting);
                return;
            } else {
                logd("SIM Records not found, MWI not updated");
                return;
            }
        }
        setVoiceMessageCount(countWaiting);
    }

    private void logd(String s) {
        Rlog.d(this.LOG_TAG, s);
    }

    private void logi(String s) {
        Rlog.i(this.LOG_TAG, s);
    }

    private void loge(String s) {
        Rlog.e(this.LOG_TAG, s);
    }

    public boolean isUtEnabled() {
        Phone imsPhone = this.mImsPhone;
        if (imsPhone != null) {
            return imsPhone.isUtEnabled();
        }
        logd("isUtEnabled: called for GsmCdma");
        return false;
    }

    public String getDtmfToneDelayKey() {
        if (isPhoneTypeGsm()) {
            return "gsm_dtmf_tone_delay_int";
        }
        return "cdma_dtmf_tone_delay_int";
    }

    public WakeLock getWakeLock() {
        return this.mWakeLock;
    }

    public void cleanDeviceId() {
        logd("cleanDeviceId");
        if (isPhoneTypeGsm()) {
            super.cleanDeviceId();
        } else {
            this.mMeid = null;
        }
        this.mImei = null;
    }

    public String getCdmaMlplVersion() {
        return this.mSST.getMlplVersion();
    }

    public String getCdmaMsplVersion() {
        return this.mSST.getMsplVersion();
    }

    public void testVoiceLoopBack(int mode) {
        this.mCi.testVoiceLoopBack(mode);
    }

    public String getMeidHw() {
        if (VSimUtilsInner.isRadioAvailable(this.mPhoneId)) {
            return this.mMeid;
        }
        Rlog.d(this.LOG_TAG, "getMeid, the phone is pending, mPhoneId is: " + this.mPhoneId);
        return VSimUtilsInner.getPendingDeviceInfoFromSP(VSimUtilsInner.MEID_PREF);
    }

    public void setMeidHw(String value) {
        this.mMeid = value;
    }

    public void onMMIDone(GsmMmiCode mmi, Exception e) {
        if (this.mPendingMMIs.remove(mmi) || mmi.isUssdRequest() || mmi.isSsInfo()) {
            this.mMmiCompleteRegistrants.notifyRegistrants(new AsyncResult(null, mmi, e));
        }
    }

    public void registerForCdmaWaitingNumberChanged(Handler h, int what, Object obj) {
        Rlog.i(this.LOG_TAG, "registerForCdmaWaitingNumberChanged");
        this.mCT.registerForCdmaWaitingNumberChanged(h, what, obj);
    }

    public void unregisterForCdmaWaitingNumberChanged(Handler h) {
        Rlog.i(this.LOG_TAG, "unregisterForCdmaWaitingNumberChanged");
        this.mCT.unregisterForCdmaWaitingNumberChanged(h);
    }

    private void logForTest(String operationName, String content) {
        if (sHwInfo) {
            int pid = Binder.getCallingPid();
            String appName = "";
            String processName = "";
            String callingPackageName = "";
            synchronized (this) {
                String[] name = (String[]) this.map.get(Integer.valueOf(pid));
                if (name != null) {
                    appName = name[0];
                    processName = name[1];
                    callingPackageName = name[2];
                } else {
                    loge("pid is not exist in map");
                    if (10 == this.map.size()) {
                        this.map.clear();
                    }
                    processName = getProcessName(pid);
                    callingPackageName = getPackageNameForPid(pid);
                    try {
                        appName = this.mContext.getPackageManager().getPackageInfo(callingPackageName, 0).applicationInfo.loadLabel(this.mContext.getPackageManager()).toString();
                    } catch (NameNotFoundException e) {
                        loge("get appname wrong");
                    } catch (NullPointerException e2) {
                        loge("get appname null");
                    }
                    this.map.put(Integer.valueOf(pid), new String[]{appName, processName, callingPackageName});
                }
            }
            logi("<" + appName + ">[" + callingPackageName + "][" + processName + "]" + ":" + "[" + operationName + "] " + content);
        }
    }

    private String getPackageNameForPid(int pid) {
        String str = null;
        try {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            data.writeInterfaceToken("android.app.IActivityManager");
            data.writeInt(pid);
            ActivityManagerNative.getDefault().asBinder().transact(504, data, reply, 0);
            reply.readException();
            str = reply.readString();
            data.recycle();
            reply.recycle();
            return str;
        } catch (RuntimeException e) {
            logd("RuntimeException");
            return str;
        } catch (Exception e2) {
            logd("getPackageNameForPid exception");
            return str;
        }
    }

    private String getProcessName(int pid) {
        String processName = "";
        List<RunningAppProcessInfo> l = ((ActivityManager) getContext().getSystemService("activity")).getRunningAppProcesses();
        if (l == null) {
            return processName;
        }
        for (RunningAppProcessInfo info : l) {
            try {
                if (info.pid == pid) {
                    processName = info.processName;
                }
            } catch (RuntimeException e) {
                logd("RuntimeException");
            } catch (Exception e2) {
                logd("Get The appName is wrong");
            }
        }
        return processName;
    }

    private void restoreSavedRadioTech() {
        if (this.mCi instanceof RIL) {
            RIL ci = this.mCi;
            boolean AirplaneModeOn = Global.getInt(this.mContext.getContentResolver(), "airplane_mode_on", 0) == 1;
            if (ci.getLastRadioTech() >= 0 && AirplaneModeOn) {
                loge("change to LastRadioTech" + ci.getLastRadioTech());
                phoneObjectUpdater(ci.getLastRadioTech());
            }
        }
    }
}
