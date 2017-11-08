package android.telephony;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.FreezeScreenScene;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.text.TextUtils;
import com.android.internal.telephony.HuaweiTelephonyConfigs;
import com.android.internal.telephony.HwModemCapability;
import com.android.internal.telephony.HwTelephonyIntentsInner;
import com.android.internal.telephony.HwTelephonyProperties;
import com.android.internal.telephony.IHwTelephony;
import com.android.internal.telephony.IHwTelephony.Stub;
import com.android.internal.telephony.IPhoneCallback;
import java.util.List;

public class HwTelephonyManagerInner {
    private static final /* synthetic */ int[] -android-telephony-HwTelephonyManagerInner$DataSettingModeTypeSwitchesValues = null;
    public static final String CARD_TYPE_SIM1 = "gsm.sim1.type";
    public static final String CARD_TYPE_SIM2 = "gsm.sim2.type";
    private static final String[] CDMA_CPLMNS = new String[]{"46003", "45502", "46012"};
    public static final int CDMA_MODE = 0;
    private static final String CHR_BROADCAST_PERMISSION = "com.huawei.android.permission.GET_CHR_DATA";
    public static final int CT_NATIONAL_ROAMING_CARD = 41;
    public static final int CU_DUAL_MODE_CARD = 42;
    public static final int DUAL_MODE_CG_CARD = 40;
    public static final int DUAL_MODE_TELECOM_LTE_CARD = 43;
    public static final int DUAL_MODE_UG_CARD = 50;
    public static final int EXTRA_VALUE_NEW_SIM = 1;
    public static final int EXTRA_VALUE_NOCHANGE = 4;
    public static final int EXTRA_VALUE_REMOVE_SIM = 2;
    public static final int EXTRA_VALUE_REPOSITION_SIM = 3;
    public static final int EXTR_VALUE_INSERT_SAME_SIM = 5;
    public static final int GSM_MODE = 1;
    public static final String INTENT_KEY_DETECT_STATUS = "simDetectStatus";
    public static final String INTENT_KEY_NEW_SIM_SLOT = "newSIMSlot";
    public static final String INTENT_KEY_NEW_SIM_STATUS = "newSIMStatus";
    public static final String INTENT_KEY_SIM_COUNT = "simCount";
    public static final int NOTIFY_CMODEM_STATUS_FAIL = -1;
    public static final int NOTIFY_CMODEM_STATUS_SUCCESS = 1;
    private static final String PROP_LTETDD_ENABLED = "persist.radio.ltetdd_enabled";
    private static final String PROP_LTE_ENABLED = "persist.radio.lte_enabled";
    private static final String PROP_VALUE_C_CARD_PLMN = "gsm.sim.c_card.plmn";
    public static final int ROAM_MODE = 2;
    public static final int SINGLE_MODE_RUIM_CARD = 30;
    public static final int SINGLE_MODE_SIM_CARD = 10;
    public static final int SINGLE_MODE_USIM_CARD = 20;
    public static final int SUPPORT_SYSTEMAPP_GET_DEVICEID = 1;
    private static final String TAG = "HwTelephonyManagerInner";
    public static final int UNKNOWN_CARD = -1;
    private static String callingAppName = "";
    private static boolean haveCheckedAppName = false;
    private static HwTelephonyManagerInner sInstance = new HwTelephonyManagerInner();

    public enum DataSettingModeType {
        MODE_LTE_OFF,
        MODE_LTETDD_ONLY,
        MODE_LTE_AND_AUTO,
        MODE_ERROR
    }

    private static /* synthetic */ int[] -getandroid-telephony-HwTelephonyManagerInner$DataSettingModeTypeSwitchesValues() {
        if (-android-telephony-HwTelephonyManagerInner$DataSettingModeTypeSwitchesValues != null) {
            return -android-telephony-HwTelephonyManagerInner$DataSettingModeTypeSwitchesValues;
        }
        int[] iArr = new int[DataSettingModeType.values().length];
        try {
            iArr[DataSettingModeType.MODE_ERROR.ordinal()] = 3;
        } catch (NoSuchFieldError e) {
        }
        try {
            iArr[DataSettingModeType.MODE_LTETDD_ONLY.ordinal()] = 1;
        } catch (NoSuchFieldError e2) {
        }
        try {
            iArr[DataSettingModeType.MODE_LTE_AND_AUTO.ordinal()] = 2;
        } catch (NoSuchFieldError e3) {
        }
        try {
            iArr[DataSettingModeType.MODE_LTE_OFF.ordinal()] = 4;
        } catch (NoSuchFieldError e4) {
        }
        -android-telephony-HwTelephonyManagerInner$DataSettingModeTypeSwitchesValues = iArr;
        return iArr;
    }

    private HwTelephonyManagerInner() {
    }

    public static HwTelephonyManagerInner getDefault() {
        return sInstance;
    }

    private IHwTelephony getIHwTelephony() throws RemoteException {
        IHwTelephony iHwTelephony = Stub.asInterface(ServiceManager.getService("phone_huawei"));
        if (iHwTelephony != null) {
            return iHwTelephony;
        }
        throw new RemoteException("getIHwTelephony return null");
    }

    public String getDemoString() {
        try {
            return getIHwTelephony().getDemoString();
        } catch (RemoteException ex) {
            ex.printStackTrace();
            return "ERROR";
        }
    }

    private int getDefaultSim() {
        return 0;
    }

    public String getMeid() {
        return getMeid(getDefaultSim());
    }

    public String getMeid(int slotId) {
        try {
            return getIHwTelephony().getMeidForSubscriber(slotId);
        } catch (RemoteException e) {
            return null;
        } catch (NullPointerException e2) {
            return null;
        }
    }

    public String getPesn() {
        return getPesn(getDefaultSim());
    }

    public String getPesn(int slotId) {
        try {
            return getIHwTelephony().getPesnForSubscriber(slotId);
        } catch (RemoteException e) {
            return null;
        } catch (NullPointerException e2) {
            return null;
        }
    }

    public String getNVESN() {
        try {
            return getIHwTelephony().getNVESN();
        } catch (RemoteException e) {
            return null;
        } catch (NullPointerException e2) {
            return null;
        }
    }

    public void closeRrc() {
        if (1000 == Binder.getCallingUid()) {
            try {
                getIHwTelephony().closeRrc();
            } catch (RemoteException e) {
            } catch (NullPointerException e2) {
            }
        }
    }

    public int getSubState(long subId) {
        try {
            return getIHwTelephony().getSubState((int) subId);
        } catch (RemoteException e) {
            return 0;
        }
    }

    public void setUserPrefDataSlotId(int slotId) {
        try {
            getIHwTelephony().setUserPrefDataSlotId(slotId);
        } catch (RemoteException e) {
        } catch (NullPointerException e2) {
        }
    }

    public boolean checkCdmaSlaveCardMode(int mode) {
        String commrilMode = SystemProperties.get(HwTelephonyProperties.PROPERTY_COMMRIL_MODE, "NON_MODE");
        String cg_standby_mode = SystemProperties.get(HwTelephonyProperties.PROPERTY_CG_STANDBY_MODE, "home");
        if (!isFullNetworkSupported() || !"CG_MODE".equals(commrilMode)) {
            return false;
        }
        switch (mode) {
            case 0:
                if (!"roam_gsm".equals(cg_standby_mode)) {
                    return true;
                }
                break;
            case 1:
                if ("roam_gsm".equals(cg_standby_mode)) {
                    return true;
                }
                break;
            case 2:
                if (!"home".equals(cg_standby_mode)) {
                    return true;
                }
                break;
        }
        return false;
    }

    public boolean isFullNetworkSupported() {
        return SystemProperties.getBoolean(HwTelephonyProperties.PROPERTY_FULL_NETWORK_SUPPORT, false);
    }

    public boolean isChinaTelecom(int slotId) {
        return !HuaweiTelephonyConfigs.isChinaTelecom() ? isCTSimCard(slotId) : true;
    }

    public boolean isCTSimCard(int slotId) {
        boolean isCTCardType;
        boolean result;
        int cardType = getCardType(slotId);
        Rlog.d(TAG, "[isCTSimCard]: cardType = " + cardType);
        switch (cardType) {
            case 30:
            case 41:
            case DUAL_MODE_TELECOM_LTE_CARD /*43*/:
                isCTCardType = true;
                break;
            default:
                isCTCardType = false;
                break;
        }
        if (!isCTCardType || HwModemCapability.isCapabilitySupport(9)) {
            result = isCTCardType;
        } else {
            boolean isCdmaCplmn = false;
            String cplmn = getCplmn(slotId);
            String[] strArr = CDMA_CPLMNS;
            int i = 0;
            int length = strArr.length;
            while (i < length) {
                if (strArr[i].equals(cplmn)) {
                    isCdmaCplmn = true;
                    Rlog.d(TAG, "[isCTSimCard]: hisi cdma  isCdmaCplmn = " + isCdmaCplmn);
                    result = isCdmaCplmn;
                    if (TextUtils.isEmpty(cplmn)) {
                        try {
                            result = getIHwTelephony().isCtSimCard(slotId);
                        } catch (RemoteException ex) {
                            ex.printStackTrace();
                        }
                    }
                    Rlog.d(TAG, "[isCTSimCard]: hisi cdma  isCdmaCplmn according iccid = " + result);
                } else {
                    i++;
                }
            }
            Rlog.d(TAG, "[isCTSimCard]: hisi cdma  isCdmaCplmn = " + isCdmaCplmn);
            result = isCdmaCplmn;
            if (TextUtils.isEmpty(cplmn)) {
                result = getIHwTelephony().isCtSimCard(slotId);
            }
            Rlog.d(TAG, "[isCTSimCard]: hisi cdma  isCdmaCplmn according iccid = " + result);
        }
        Rlog.d(TAG, "[isCTSimCard]: result = " + result);
        return result;
    }

    private String getCplmn(int slotId) {
        String result = "";
        String value = SystemProperties.get(PROP_VALUE_C_CARD_PLMN, "");
        if (!(value == null || "".equals(value))) {
            String[] substr = value.split(",");
            if (substr.length == 2 && Integer.parseInt(substr[1]) == slotId) {
                result = substr[0];
            }
        }
        Rlog.d(TAG, "getCplmn for Slot : " + slotId + " result is : " + result);
        return result;
    }

    public boolean isCDMASimCard(int slotId) {
        int cardType = getCardType(slotId);
        Rlog.d(TAG, "[isCDMASimCard]: cardType = " + cardType);
        switch (cardType) {
            case 30:
            case 40:
            case 41:
            case DUAL_MODE_TELECOM_LTE_CARD /*43*/:
                return true;
            default:
                return false;
        }
    }

    public int getCardType(int slotId) {
        if (slotId == 0) {
            return SystemProperties.getInt(CARD_TYPE_SIM1, -1);
        }
        if (slotId == 1) {
            return SystemProperties.getInt(CARD_TYPE_SIM2, -1);
        }
        return -1;
    }

    public boolean isDomesticCard(int slotId) {
        try {
            return getIHwTelephony().isDomesticCard(slotId);
        } catch (RemoteException e) {
            return true;
        } catch (NullPointerException e2) {
            return true;
        }
    }

    public boolean isCTCdmaCardInGsmMode() {
        try {
            return getIHwTelephony().isCTCdmaCardInGsmMode();
        } catch (RemoteException e) {
            return false;
        } catch (NullPointerException e2) {
            return false;
        }
    }

    public void setDefaultMobileEnable(boolean enabled) {
        try {
            getIHwTelephony().setDefaultMobileEnable(enabled);
        } catch (RemoteException e) {
        } catch (NullPointerException e2) {
        }
    }

    public void setDataEnabledWithoutPromp(boolean enabled) {
        try {
            getIHwTelephony().setDataEnabledWithoutPromp(enabled);
        } catch (RemoteException e) {
        } catch (NullPointerException e2) {
        }
    }

    public int getDataState(long subId) {
        if (subId >= 0) {
            try {
                if (subId < ((long) TelephonyManager.getDefault().getPhoneCount())) {
                    return getIHwTelephony().getDataStateForSubscriber((int) subId);
                }
            } catch (RemoteException e) {
                return 0;
            } catch (NullPointerException e2) {
                return 0;
            }
        }
        return 0;
    }

    public void setLteServiceAbility(int ability) {
        try {
            getIHwTelephony().setLteServiceAbility(ability);
        } catch (RemoteException ex) {
            ex.printStackTrace();
        } catch (NullPointerException ex2) {
            ex2.printStackTrace();
        }
    }

    public int getLteServiceAbility() {
        try {
            return getIHwTelephony().getLteServiceAbility();
        } catch (RemoteException e) {
            return 0;
        } catch (NullPointerException e2) {
            return 0;
        }
    }

    public boolean isSubDeactivedByPowerOff(long sub) {
        Rlog.d(TAG, "In isSubDeactivedByPowerOff");
        try {
            return getIHwTelephony().isSubDeactivedByPowerOff(sub);
        } catch (RemoteException ex) {
            Rlog.e(TAG, "RemoteException ex = " + ex);
            return false;
        }
    }

    public boolean isNeedToRadioPowerOn(long sub) {
        Rlog.d(TAG, "In isNeedToRadioPowerOn");
        try {
            return getIHwTelephony().isNeedToRadioPowerOn(sub);
        } catch (RemoteException ex) {
            Rlog.e(TAG, "RemoteException ex = " + ex);
            return true;
        }
    }

    public boolean isCardPresent(int slotId) {
        return TelephonyManager.getDefault().getSimState(slotId) != 1;
    }

    public void updateCrurrentPhone(int lteSlot) {
        try {
            getIHwTelephony().updateCrurrentPhone(lteSlot);
        } catch (RemoteException e) {
        } catch (NullPointerException e2) {
        }
    }

    public void setDefaultDataSlotId(int slotId) {
        try {
            getIHwTelephony().setDefaultDataSlotId(slotId);
        } catch (RemoteException e) {
        } catch (NullPointerException e2) {
        }
    }

    public int getDefault4GSlotId() {
        try {
            return getIHwTelephony().getDefault4GSlotId();
        } catch (RemoteException e) {
            return 0;
        }
    }

    public void setDefault4GSlotId(int slotId, Message msg) {
        Rlog.d(TAG, "In setDefault4GSlotId");
        try {
            getIHwTelephony().setDefault4GSlotId(slotId, msg);
        } catch (RemoteException ex) {
            Rlog.e(TAG, "RemoteException ex = " + ex);
        }
    }

    public boolean isSetDefault4GSlotIdEnabled() {
        Rlog.d(TAG, "In isSetDefault4GSlotIdEnabled");
        try {
            return getIHwTelephony().isSetDefault4GSlotIdEnabled();
        } catch (RemoteException ex) {
            Rlog.e(TAG, "RemoteException ex = " + ex);
            return false;
        }
    }

    public void waitingSetDefault4GSlotDone(boolean waiting) {
        Rlog.d(TAG, "In waitingSetDefault4GSlotDone");
        try {
            getIHwTelephony().waitingSetDefault4GSlotDone(waiting);
        } catch (RemoteException ex) {
            Rlog.e(TAG, "RemoteException ex = " + ex);
        }
    }

    public String getIccATR() {
        String strATR = SystemProperties.get("gsm.sim.hw_atr", "null");
        strATR = strATR + "," + SystemProperties.get("gsm.sim.hw_atr1", "null");
        Rlog.d(TAG, "getIccATR: [" + strATR + "]");
        return strATR;
    }

    public DataSettingModeType getDataSettingMode() {
        boolean isLteEnabled = SystemProperties.getBoolean(PROP_LTE_ENABLED, true);
        boolean isLteTddEnabled = SystemProperties.getBoolean(PROP_LTETDD_ENABLED, false);
        Rlog.d(TAG, "in getDataSettingMode isLteEnabled=" + isLteEnabled + " isLteTddEnabled=" + isLteTddEnabled);
        if (!isLteEnabled) {
            return DataSettingModeType.MODE_LTE_OFF;
        }
        if (isLteTddEnabled) {
            return DataSettingModeType.MODE_LTETDD_ONLY;
        }
        return DataSettingModeType.MODE_LTE_AND_AUTO;
    }

    private void doSetPreferredNetworkType(int nwMode) {
        Rlog.d(TAG, "[enter]doSetPreferredNetworkType nwMode:" + nwMode);
        try {
            getIHwTelephony().setPreferredNetworkType(nwMode);
        } catch (RemoteException e) {
        } catch (Exception e2) {
            Rlog.e(TAG, "doSetPreferredNetworkType failed!");
        }
    }

    private void doSetDataSettingModeFromLteAndAuto(DataSettingModeType dataMode) {
        switch (-getandroid-telephony-HwTelephonyManagerInner$DataSettingModeTypeSwitchesValues()[dataMode.ordinal()]) {
            case 1:
                doSetPreferredNetworkType(30);
                return;
            default:
                Rlog.e(TAG, "doSetDataSettingModeFromLteAndAuto failed! param err mode =" + dataMode);
                return;
        }
    }

    private void doSetDataSettingModeFromLteTddOnly(DataSettingModeType dataMode) {
        switch (-getandroid-telephony-HwTelephonyManagerInner$DataSettingModeTypeSwitchesValues()[dataMode.ordinal()]) {
            case 2:
                doSetPreferredNetworkType(61);
                return;
            default:
                Rlog.e(TAG, "doSetDataSettingModeLteTddOnly failed! param err mode =" + dataMode);
                return;
        }
    }

    public void setDataSettingMode(DataSettingModeType dataMode) {
        if (dataMode == DataSettingModeType.MODE_LTETDD_ONLY || dataMode == DataSettingModeType.MODE_LTE_AND_AUTO) {
            switch (-getandroid-telephony-HwTelephonyManagerInner$DataSettingModeTypeSwitchesValues()[dataMode.ordinal()]) {
                case 1:
                    doSetDataSettingModeFromLteAndAuto(dataMode);
                    break;
                case 2:
                    doSetDataSettingModeFromLteTddOnly(dataMode);
                    break;
            }
            return;
        }
        Rlog.e(TAG, "setDataSettingMode failed! param err mode =" + dataMode);
    }

    public boolean isSubDeactived(int subId) {
        return false;
    }

    public int getPreferredDataSubscription() {
        int subId = -1;
        try {
            subId = getIHwTelephony().getPreferredDataSubscription();
        } catch (RemoteException e) {
        }
        return subId;
    }

    public int getOnDemandDataSubId() {
        int subId = -1;
        try {
            subId = getIHwTelephony().getOnDemandDataSubId();
        } catch (RemoteException e) {
        }
        return subId;
    }

    public String getCdmaGsmImsi() {
        try {
            return getIHwTelephony().getCdmaGsmImsi();
        } catch (RemoteException e) {
            return null;
        }
    }

    public int getUiccCardType(int slotId) {
        try {
            return getIHwTelephony().getUiccCardType(slotId);
        } catch (RemoteException e) {
            return -1;
        }
    }

    public CellLocation getCellLocation(int slotId) {
        try {
            Bundle bundle = getIHwTelephony().getCellLocation(slotId);
            if (bundle == null || bundle.isEmpty()) {
                return null;
            }
            CellLocation cl = CellLocation.newFromBundle(bundle, slotId);
            if (cl == null || cl.isEmpty()) {
                return null;
            }
            return cl;
        } catch (RemoteException e) {
            return null;
        } catch (NullPointerException e2) {
            return null;
        }
    }

    public String getCdmaMlplVersion() {
        try {
            return getIHwTelephony().getCdmaMlplVersion();
        } catch (RemoteException e) {
            return null;
        }
    }

    public String getCdmaMsplVersion() {
        try {
            return getIHwTelephony().getCdmaMsplVersion();
        } catch (RemoteException e) {
            return null;
        }
    }

    public void printCallingAppNameInfo(boolean enable, Context context) {
        if (context != null) {
            int callingPid = Process.myPid();
            String appName = "";
            ActivityManager am = (ActivityManager) context.getSystemService(FreezeScreenScene.ACTIVITY_PARAM);
            if (am != null) {
                List<RunningAppProcessInfo> appProcessList = am.getRunningAppProcesses();
                if (appProcessList != null) {
                    for (RunningAppProcessInfo appProcess : appProcessList) {
                        if (appProcess.pid == callingPid) {
                            appName = appProcess.processName;
                        }
                    }
                    Rlog.d(TAG, "setDataEnabled: calling app is( " + appName + " ) setEanble( " + enable + " )");
                    triggerChrAppCloseDataSwitch(appName, enable, context);
                }
            }
        }
    }

    public void triggerChrAppCloseDataSwitch(String appName, boolean enable, Context context) {
        if (!("com.android.phone".equals(appName) || "system".equals(appName) || "com.android.systemui".equals(appName) || "com.android.settings".equals(appName))) {
            Rlog.d(TAG, "app" + appName + " close data switch! trigger Chr!");
            Intent apkIntent = new Intent(HwTelephonyIntentsInner.INTENT_DS_APP_CLOSE_DATA_SWITCH);
            apkIntent.putExtra("appname", appName);
            context.sendBroadcast(apkIntent, CHR_BROADCAST_PERMISSION);
        }
        TelephonyManager.getDefault().setDataEnabledProperties(appName, enable);
    }

    public String getUniqueDeviceId(int scope) {
        try {
            return getIHwTelephony().getUniqueDeviceId(scope);
        } catch (RemoteException ex) {
            Rlog.e(TAG, "getUniqueDeviceId RemoteException:" + ex);
            return null;
        } catch (NullPointerException ex2) {
            Rlog.e(TAG, "getUniqueDeviceId NullPointerException:" + ex2);
            return null;
        }
    }

    public boolean isLTESupported() {
        try {
            return getIHwTelephony().isLTESupported();
        } catch (RemoteException e) {
            return true;
        } catch (NullPointerException e2) {
            return true;
        }
    }

    public void testVoiceLoopBack(int mode) {
        try {
            if (getIHwTelephony() != null) {
                getIHwTelephony().testVoiceLoopBack(mode);
            }
        } catch (RemoteException ex) {
            Rlog.d(TAG, "testVoiceLoopBack RemoteException = " + ex);
        } catch (NullPointerException ex2) {
            Rlog.d(TAG, "testVoiceLoopBack NullPointerException = " + ex2);
        }
    }

    public int getSpecCardType(int slotId) {
        try {
            return getIHwTelephony().getSpecCardType(slotId);
        } catch (RemoteException e) {
            return -1;
        }
    }

    public boolean isCardUimLocked(int slotId) {
        try {
            return getIHwTelephony().isCardUimLocked(slotId);
        } catch (RemoteException e) {
            return false;
        }
    }

    public boolean isRadioOn(int slot) {
        try {
            return getIHwTelephony().isRadioOn(slot);
        } catch (RemoteException e) {
            return false;
        }
    }

    public boolean isPlatformSupportVsim() {
        return HwVSimManager.getDefault().isPlatformSupportVsim();
    }

    public boolean hasIccCardForVSim(int slotId) {
        return HwVSimManager.getDefault().hasIccCardForVSim(slotId);
    }

    public int getSimStateForVSim(int slotIdx) {
        return HwVSimManager.getDefault().getSimStateForVSim(slotIdx);
    }

    public int getVSimSubId() {
        return HwVSimManager.getDefault().getVSimSubId();
    }

    public int enableVSim(String imsi, int cardtype, int apntype, String acqorder, String challenge) {
        return HwVSimManager.getDefault().enableVSim(1, imsi, cardtype, apntype, acqorder, challenge);
    }

    public boolean disableVSim() {
        return HwVSimManager.getDefault().disableVSim();
    }

    public int setApn(int cardtype, int apntype, String challenge) {
        return HwVSimManager.getDefault().enableVSim(2, null, cardtype, apntype, null, challenge);
    }

    public int getSimMode(int subId) {
        return HwVSimManager.getDefault().getSimMode(subId);
    }

    public void recoverSimMode() {
        HwVSimManager.getDefault().recoverSimMode();
    }

    public String getRegPlmn(int subId) {
        return HwVSimManager.getDefault().getRegPlmn(subId);
    }

    public String getTrafficData() {
        return HwVSimManager.getDefault().getTrafficData();
    }

    public Boolean clearTrafficData() {
        return HwVSimManager.getDefault().clearTrafficData();
    }

    public int getSimStateViaSysinfoEx(int subId) {
        return HwVSimManager.getDefault().getSimStateViaSysinfoEx(subId);
    }

    public int getCpserr(int subId) {
        return HwVSimManager.getDefault().getCpserr(subId);
    }

    public int scanVsimAvailableNetworks(int subId, int type) {
        return HwVSimManager.getDefault().scanVsimAvailableNetworks(subId, type);
    }

    public boolean setUserReservedSubId(int subId) {
        return HwVSimManager.getDefault().setUserReservedSubId(subId);
    }

    public int getUserReservedSubId() {
        return HwVSimManager.getDefault().getUserReservedSubId();
    }

    public String getDevSubMode(int subscription) {
        return HwVSimManager.getDefault().getDevSubMode(subscription);
    }

    public String getPreferredNetworkTypeForVSim(int subscription) {
        return HwVSimManager.getDefault().getPreferredNetworkTypeForVSim(subscription);
    }

    public int getVSimCurCardType() {
        return HwVSimManager.getDefault().getVSimCurCardType();
    }

    public int getVSimFineState() {
        return 0;
    }

    public int getVSimCachedSubId() {
        return -1;
    }

    public boolean getWaitingSwitchBalongSlot() {
        try {
            return getIHwTelephony().getWaitingSwitchBalongSlot();
        } catch (RemoteException e) {
            return false;
        }
    }

    public String getCallingAppName(Context context) {
        if (context == null) {
            return "";
        }
        if (!haveCheckedAppName) {
            int callingPid = Process.myPid();
            ActivityManager am = (ActivityManager) context.getSystemService(FreezeScreenScene.ACTIVITY_PARAM);
            if (am == null) {
                return "";
            }
            List<RunningAppProcessInfo> appProcessList = am.getRunningAppProcesses();
            if (appProcessList == null) {
                return "";
            }
            for (RunningAppProcessInfo appProcess : appProcessList) {
                if (appProcess.pid == callingPid) {
                    setCallingAppName(appProcess.processName);
                    Rlog.d(TAG, "setCallingAppName : " + appProcess.processName);
                    break;
                }
            }
            setHaveCheckedAppName(true);
        }
        return callingAppName;
    }

    private static void setCallingAppName(String name) {
        callingAppName = name;
    }

    private static void setHaveCheckedAppName(boolean value) {
        haveCheckedAppName = value;
    }

    public boolean setISMCOEX(String ATCommand) {
        try {
            Rlog.d(TAG, "setISMCOEX = " + ATCommand);
            return getIHwTelephony().setISMCOEX(ATCommand);
        } catch (RemoteException ex) {
            Rlog.e(TAG, "setISMCOEX RemoteException:" + ex);
            return false;
        } catch (NullPointerException ex2) {
            Rlog.e(TAG, "setISMCOEX NullPointerException:" + ex2);
            return false;
        }
    }

    public String[] queryServiceCellBand() {
        Rlog.d(TAG, "queryServiceCellBand.");
        try {
            return getIHwTelephony().queryServiceCellBand();
        } catch (RemoteException ex) {
            Rlog.e(TAG, "queryServiceCellBand RemoteException:" + ex);
            return new String[0];
        } catch (NullPointerException ex2) {
            Rlog.e(TAG, "queryServiceCellBand NullPointerException:" + ex2);
            return new String[0];
        }
    }

    public boolean registerForRadioAvailable(IPhoneCallback callback) {
        try {
            Rlog.d(TAG, "registerForRadioAvailable");
            return getIHwTelephony().registerForRadioAvailable(callback);
        } catch (RemoteException ex) {
            Rlog.e(TAG, "registerForRadioAvailable RemoteException:" + ex);
            return false;
        } catch (NullPointerException ex2) {
            Rlog.e(TAG, "registerForRadioAvailable NullPointerException:" + ex2);
            return false;
        }
    }

    public boolean unregisterForRadioAvailable(IPhoneCallback callback) {
        try {
            Rlog.d(TAG, "unregisterForRadioAvailable");
            return getIHwTelephony().unregisterForRadioAvailable(callback);
        } catch (RemoteException ex) {
            Rlog.e(TAG, "unregisterForRadioAvailable RemoteException:" + ex);
            return false;
        } catch (NullPointerException ex2) {
            Rlog.e(TAG, "unregisterForRadioAvailable NullPointerException:" + ex2);
            return false;
        }
    }

    public boolean registerForRadioNotAvailable(IPhoneCallback callback) {
        try {
            Rlog.d(TAG, "registerForRadioNotAvailable");
            return getIHwTelephony().registerForRadioNotAvailable(callback);
        } catch (RemoteException ex) {
            Rlog.e(TAG, "registerForRadioNotAvailable RemoteException:" + ex);
            return false;
        } catch (NullPointerException ex2) {
            Rlog.e(TAG, "registerForRadioNotAvailable NullPointerException:" + ex2);
            return false;
        }
    }

    public boolean unregisterForRadioNotAvailable(IPhoneCallback callback) {
        try {
            Rlog.d(TAG, "unregisterForRadioNotAvailable");
            return getIHwTelephony().unregisterForRadioNotAvailable(callback);
        } catch (RemoteException ex) {
            Rlog.e(TAG, "unregisterForRadioNotAvailable RemoteException:" + ex);
            return false;
        } catch (NullPointerException ex2) {
            Rlog.e(TAG, "unregisterForRadioNotAvailable NullPointerException:" + ex2);
            return false;
        }
    }

    public boolean registerCommonImsaToMapconInfo(IPhoneCallback callback) {
        try {
            Rlog.d(TAG, "registerCommonImsaToMapconInfo");
            return getIHwTelephony().registerCommonImsaToMapconInfo(callback);
        } catch (RemoteException ex) {
            Rlog.e(TAG, "registerCommonImsaToMapconInfo RemoteException:" + ex);
            return false;
        } catch (NullPointerException ex2) {
            Rlog.e(TAG, "registerCommonImsaToMapconInfo NullPointerException:" + ex2);
            return false;
        }
    }

    public boolean unregisterCommonImsaToMapconInfo(IPhoneCallback callback) {
        try {
            Rlog.d(TAG, "unregisterCommonImsaToMapconInfo");
            return getIHwTelephony().unregisterCommonImsaToMapconInfo(callback);
        } catch (RemoteException ex) {
            Rlog.e(TAG, "unregisterCommonImsaToMapconInfo RemoteException:" + ex);
            return false;
        } catch (NullPointerException ex2) {
            Rlog.e(TAG, "unregisterCommonImsaToMapconInfo NullPointerException:" + ex2);
            return false;
        }
    }

    public boolean isRadioAvailable() {
        try {
            Rlog.d(TAG, "isRadioAvailable");
            return getIHwTelephony().isRadioAvailable();
        } catch (RemoteException ex) {
            Rlog.e(TAG, "isRadioAvailable RemoteException:" + ex);
            return false;
        } catch (NullPointerException ex2) {
            Rlog.e(TAG, "isRadioAvailable NullPointerException:" + ex2);
            return false;
        }
    }

    public void setImsSwitch(boolean value) {
        try {
            Rlog.d(TAG, "setImsSwitch" + value);
            getIHwTelephony().setImsSwitch(value);
        } catch (RemoteException ex) {
            Rlog.e(TAG, "setImsSwitch RemoteException:" + ex);
        } catch (NullPointerException ex2) {
            Rlog.e(TAG, "setImsSwitch NullPointerException:" + ex2);
        }
    }

    public boolean getImsSwitch() {
        try {
            Rlog.d(TAG, "getImsSwitch");
            return getIHwTelephony().getImsSwitch();
        } catch (RemoteException ex) {
            Rlog.e(TAG, "getImsSwitch RemoteException:" + ex);
            return false;
        } catch (NullPointerException ex2) {
            Rlog.e(TAG, "getImsSwitch NullPointerException:" + ex2);
            return false;
        }
    }

    public void setImsDomainConfig(int domainType) {
        try {
            Rlog.d(TAG, "setImsDomainConfig");
            getIHwTelephony().setImsDomainConfig(domainType);
        } catch (RemoteException ex) {
            Rlog.e(TAG, "setImsDomainConfig RemoteException:" + ex);
        } catch (NullPointerException ex2) {
            Rlog.e(TAG, "setImsDomainConfig NullPointerException:" + ex2);
        }
    }

    public boolean handleMapconImsaReq(byte[] Msg) {
        try {
            Rlog.d(TAG, "handleMapconImsaReq");
            return getIHwTelephony().handleMapconImsaReq(Msg);
        } catch (RemoteException ex) {
            Rlog.e(TAG, "handleMapconImsaReq RemoteException:" + ex);
            return false;
        } catch (NullPointerException ex2) {
            Rlog.e(TAG, "handleMapconImsaReq NullPointerException:" + ex2);
            return false;
        }
    }

    public int getUiccAppType() {
        try {
            Rlog.d(TAG, "getUiccAppType");
            return getIHwTelephony().getUiccAppType();
        } catch (RemoteException ex) {
            Rlog.e(TAG, "getUiccAppType RemoteException:" + ex);
            return 0;
        } catch (NullPointerException ex2) {
            Rlog.e(TAG, "getUiccAppType NullPointerException:" + ex2);
            return 0;
        }
    }

    public int getImsDomain() {
        try {
            Rlog.d(TAG, "getImsDomain");
            return getIHwTelephony().getImsDomain();
        } catch (RemoteException ex) {
            Rlog.e(TAG, "getImsDomain RemoteException:" + ex);
            return -1;
        } catch (NullPointerException ex2) {
            Rlog.e(TAG, "getImsDomain NullPointerException:" + ex2);
            return -1;
        }
    }

    public UiccAuthResponse handleUiccAuth(int auth_type, byte[] rand, byte[] auth) {
        try {
            Rlog.d(TAG, "handleUiccAuth");
            return getIHwTelephony().handleUiccAuth(auth_type, rand, auth);
        } catch (RemoteException ex) {
            Rlog.e(TAG, "handleUiccAuth RemoteException:" + ex);
            return null;
        } catch (NullPointerException ex2) {
            Rlog.e(TAG, "handleUiccAuth NullPointerException:" + ex2);
            return null;
        }
    }

    public boolean cmdForECInfo(int event, int action, byte[] buf) {
        try {
            return getIHwTelephony().cmdForECInfo(event, action, buf);
        } catch (RemoteException ex) {
            Rlog.e(TAG, "cmdForECInfo RemoteException:" + ex);
            return false;
        } catch (NullPointerException ex2) {
            Rlog.e(TAG, "cmdForECInfo NullPointerException:" + ex2);
            return false;
        }
    }

    public void notifyCModemStatus(int status, PhoneCallback callback) {
        try {
            Rlog.d(TAG, "notifyCModemStatus");
            getIHwTelephony().notifyCModemStatus(status, callback.mCallbackStub);
        } catch (RemoteException ex) {
            Rlog.e(TAG, "notifyCModemStatus RemoteException:" + ex);
        } catch (NullPointerException ex2) {
            Rlog.e(TAG, "notifyCModemStatus NullPointerException:" + ex2);
        }
    }

    public boolean notifyDeviceState(String device, String state, String extras) {
        try {
            Rlog.d(TAG, "notifyDeviceState, device =" + device + ", state = " + state);
            return getIHwTelephony().notifyDeviceState(device, state, extras);
        } catch (RemoteException ex) {
            Rlog.e(TAG, "notifyDeviceState RemoteException:" + ex);
            return false;
        } catch (NullPointerException ex2) {
            Rlog.e(TAG, "notifyDeviceState NullPointerException:" + ex2);
            return false;
        }
    }
}
