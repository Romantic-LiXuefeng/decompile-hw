package com.android.server.wifi;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.IpConfiguration.ProxySettings;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.AuthAlgorithm;
import android.net.wifi.WifiConfiguration.GroupCipher;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.net.wifi.WifiConfiguration.PairwiseCipher;
import android.net.wifi.WifiConfiguration.Protocol;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Settings.Global;
import android.provider.Settings.SettingNotFoundException;
import android.provider.Settings.System;
import android.text.TextUtils;
import android.util.Log;
import com.huawei.device.connectivitychrlog.CSegEVENT_WIFI_USER_CONNECT;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.CRC32;

public class HwWifiCHRServiceImpl extends Handler implements HwWifiCHRService {
    private static final /* synthetic */ int[] -android-net-IpConfiguration$ProxySettingsSwitchesValues = null;
    private static final int AUTH_FAIL_UNRECV_COMPLETED = 4;
    private static final int AUTH_FAIL_UNRECV_GROUPSHAKE = 2;
    private static final int AUTH_FAIL_UNRECV_HANDSAHKE = 1;
    private static final int CLOSE_TIMEOUT_INTERVAL = 8000;
    private static final int CONNECT_TIMEOUT_INTERVAL = 45000;
    private static final int CONNECT_TYPE_DISABLE = 4;
    private static final int CONNECT_TYPE_FAIL = 1;
    private static final int CONNECT_TYPE_FORGET = 3;
    private static final int CONNECT_TYPE_OTHER_NET = 5;
    private static final int CONNECT_TYPE_RECONNECT = 2;
    private static final int CONNECT_TYPE_SUCC = 0;
    private static final int CONNECT_TYPE_TIMEOUT = 6;
    private static final int CONNECT_TYPE_TURNOFF = 7;
    private static final boolean DBG = true;
    private static final int EVENT_APK_CHANGED_SCAN_ENABLE = 10008;
    private static final int EVENT_APK_CHANGED_SCAN_TRIGGER = 10009;
    private static final int EVENT_APK_CHANGED_WIFI_DISABLE_NETWORK = 10007;
    private static final int EVENT_APK_CHANGED_WIFI_ENABLE_FALSE = 10006;
    private static final int EVENT_CHECK_DRIVER = 10010;
    private static final int EVENT_CLOSE_TIMEOUT = 10005;
    private static final int EVENT_CONNECT_FAILED = 10003;
    private static final int EVENT_CONNECT_TIMEOUT = 10002;
    private static final int EVENT_OPEN_TIMEOUT = 10004;
    private static final int MAX_CONNECT_RETRY_CNT = 3;
    private static final int MIN_FORGET_INTERVAL = 5000;
    private static final int MIN_RECONNECT_INTERVAL = 5000;
    private static final int OPEN_TIMEOUT_INTERVAL = 8000;
    private static final int OPEN_WIFI_STATE_NODE_INTERVAL = 10000;
    private static final int PROXY_NONE = 0;
    private static final int PROXY_PAC = 3;
    private static final int PROXY_STATIC = 1;
    private static final int PROXY_UNASSIGNED = 2;
    private static final int SEND_APKACTION_INTERVAL = 60000;
    private static final long SETTING_FLUSH_TIME = 60000;
    private static final String TAG = "HwWifiCHRService";
    private static int WIFI_TRIGGER_NONE = 0;
    private static int WIFI_TRIGGER_OFF = 2;
    private static int WIFI_TRIGGER_ON = 1;
    private static Context mContext = null;
    private static HwWifiCHRService sInstance = null;
    private int mApChannel = 0;
    private int mApkChangedScanEnable_Count = 0;
    private String mApkChangedScanEnable_Name = "";
    private int mApkChangedScanTrigger_Count = 0;
    private String mApkChangedScanTrigger_Name = "";
    private int mApkChangedWifiDisableNetwork_Count = 0;
    private String mApkChangedWifiDisableNetwork_Name = "";
    private int mApkChangedWifiEnableFalse_Count = 0;
    private String mApkChangedWifiEnableFalse_Name = "";
    private short mAssocRejectCnt = (short) 0;
    private String mAssocStatus = "";
    private short mAuthFailCnt = (short) 0;
    private String mAuthStatus = "";
    private short mDhcpFailCnt = (short) 0;
    private String mDhcpStatus = "";
    private short mDisconnectCnt = (short) 0;
    private long mGetSettingTimestamp = 0;
    private WifiConfiguration mLastConnectConfig = null;
    private int mLastWifiState = 4;
    private SupplicantState mLastWpaState;
    private WifiConfiguration mPendingConfig = null;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                String action = intent.getAction();
                if (action != null && action.equals("com.huawei.chr.wifi.action.WIFI_SCANALWAYS_CHANGED")) {
                    String apkName = intent.getStringExtra("apkName");
                    HwWifiCHRServiceImpl.this.mUserAction = intent.getStringExtra("userAction");
                    HwWifiCHRServiceImpl.this.updateApkChangewWifiStatus(3, apkName);
                }
            }
        }
    };
    private WifiConfiguration mRecentConfig = null;
    private int mRssi = 0;
    private int[] mRssis = new int[]{0, 0, 0, 0};
    private int mSettingNetAvailableNotify = 0;
    private int mSettingScanAlways = 0;
    private int mSettingSleepPolicy = 0;
    private int mSettingWiFiProState = 0;
    private int mSettingWiFiToPDP = 0;
    private String mTargetBssid = "";
    private String mUserAction = "";
    private long mUserConnectTimestamp = 0;
    private HwWifiCHRStateManager mWiFiCHRManager;
    private int mWifiTriggerState = WIFI_TRIGGER_NONE;

    private static /* synthetic */ int[] -getandroid-net-IpConfiguration$ProxySettingsSwitchesValues() {
        if (-android-net-IpConfiguration$ProxySettingsSwitchesValues != null) {
            return -android-net-IpConfiguration$ProxySettingsSwitchesValues;
        }
        int[] iArr = new int[ProxySettings.values().length];
        try {
            iArr[ProxySettings.NONE.ordinal()] = 4;
        } catch (NoSuchFieldError e) {
        }
        try {
            iArr[ProxySettings.PAC.ordinal()] = 1;
        } catch (NoSuchFieldError e2) {
        }
        try {
            iArr[ProxySettings.STATIC.ordinal()] = 2;
        } catch (NoSuchFieldError e3) {
        }
        try {
            iArr[ProxySettings.UNASSIGNED.ordinal()] = 3;
        } catch (NoSuchFieldError e4) {
        }
        -android-net-IpConfiguration$ProxySettingsSwitchesValues = iArr;
        return iArr;
    }

    public static synchronized void init(Context context) {
        synchronized (HwWifiCHRServiceImpl.class) {
            if (context != null) {
                if (mContext == null) {
                    mContext = context;
                    if (sInstance == null) {
                        HandlerThread thread = new HandlerThread(TAG);
                        thread.start();
                        sInstance = new HwWifiCHRServiceImpl(thread.getLooper());
                    }
                }
            }
        }
    }

    private void registerForBroadcasts() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("com.huawei.chr.wifi.action.WIFI_SCANALWAYS_CHANGED");
        if (mContext != null) {
            mContext.registerReceiver(this.mReceiver, intentFilter);
        }
    }

    public static HwWifiCHRService getDefault() {
        return sInstance;
    }

    public void connectFromUser(WifiConfiguration config) {
        LOGD("connectFromUser");
        long now = SystemClock.elapsedRealtime();
        removeMessages(EVENT_CONNECT_TIMEOUT);
        WifiConfiguration mTempConnectConfig = this.mLastConnectConfig;
        if (now - this.mUserConnectTimestamp < 5000 || mTempConnectConfig == null) {
            resetInfo();
            this.mUserConnectTimestamp = now;
            this.mLastConnectConfig = config;
            sendEmptyMessageDelayed(EVENT_CONNECT_TIMEOUT, 45000);
            return;
        }
        handleConnectFailed(2, 0);
        this.mPendingConfig = config;
    }

    public void forgetFromUser(int netId) {
        LOGD("forgetFromUser:netid:" + netId);
        WifiConfiguration mTempConnectConfig = this.mLastConnectConfig;
        if (netId == -1 || mTempConnectConfig == null || netId != mTempConnectConfig.networkId) {
            LOGD("ignore forgetFromUser" + netId + ", last connect: " + (mTempConnectConfig == null ? "null" : Integer.valueOf(mTempConnectConfig.networkId)));
            return;
        }
        if (SystemClock.elapsedRealtime() - this.mUserConnectTimestamp < 5000) {
            LOGD("ignore last connection when user remove configuration after connection in limited time");
            resetInfo();
        } else {
            handleConnectFailed(3, 0);
        }
        if (this.mWiFiCHRManager != null) {
            this.mWiFiCHRManager.updateTimeStampSessionFinish(SystemClock.elapsedRealtime());
            this.mWiFiCHRManager.reportHwCHRAccessNetworkEventInfoList(2);
        }
    }

    public void disableNetwork(int netId, int reason) {
        WifiConfiguration mTempConnectConfig = this.mLastConnectConfig;
        if (netId != -1 && mTempConnectConfig != null && netId == mTempConnectConfig.networkId) {
            handleConnectFailed(4, 0);
        }
    }

    public void updateConnectState(WifiConfiguration selectedConfig) {
        WifiConfiguration mTempConnectConfig = this.mLastConnectConfig;
        if (selectedConfig != null && mTempConnectConfig != null) {
            if (selectedConfig.networkId == mTempConnectConfig.networkId) {
                handleConnectFailed(0, 0);
            } else {
                handleConnectFailed(5, 0);
            }
        }
    }

    public void handleSupplicantStateChange(SupplicantState state) {
        if (state.ordinal() < SupplicantState.ASSOCIATING.ordinal() || state.ordinal() > SupplicantState.COMPLETED.ordinal()) {
            if (this.mLastWpaState == SupplicantState.ASSOCIATING) {
                this.mAssocRejectCnt = (short) (this.mAssocRejectCnt + 1);
            } else if (this.mLastWpaState == SupplicantState.ASSOCIATED) {
                this.mAuthFailCnt = (short) (this.mAuthFailCnt + 1);
                authFailEvent(1);
            } else if (this.mLastWpaState == SupplicantState.COMPLETED) {
                this.mDisconnectCnt = (short) (this.mDisconnectCnt + 1);
            } else if (this.mLastWpaState == SupplicantState.FOUR_WAY_HANDSHAKE) {
                this.mAuthFailCnt = (short) (this.mAuthFailCnt + 1);
                authFailEvent(2);
            } else if (this.mLastWpaState == SupplicantState.GROUP_HANDSHAKE) {
                this.mAuthFailCnt = (short) (this.mAuthFailCnt + 1);
                authFailEvent(4);
            }
        }
        this.mLastWpaState = state;
        fetchRssi(state);
        if ((this.mAssocRejectCnt + this.mAuthFailCnt) + this.mDisconnectCnt >= 3) {
            handleConnectFailed(1, 0);
        }
        if (SupplicantState.ASSOCIATING == state && this.mWiFiCHRManager != null) {
            this.mWiFiCHRManager.reportHwCHRAccessNetworkEventInfoList(1);
        }
    }

    public void updateWifiState(int state) {
        if (state == 0) {
            if (this.mLastConnectConfig != null) {
                handleConnectFailed(7, 0);
            }
        } else if (state == 3) {
            if (this.mWifiTriggerState == WIFI_TRIGGER_ON) {
                HwWifiStatStoreImpl.getDefault().updateWifiState(true, true);
                removeMessages(EVENT_OPEN_TIMEOUT);
            }
        } else if (state == 1 && this.mWifiTriggerState == WIFI_TRIGGER_OFF) {
            HwWifiStatStoreImpl.getDefault().updateWifiState(false, true);
            removeMessages(EVENT_CLOSE_TIMEOUT);
        }
        this.mLastWifiState = state;
    }

    public void assocRejectEvent(int status) {
        if ("".equals(this.mAssocStatus)) {
            this.mAssocStatus += String.valueOf(status);
        } else {
            this.mAssocStatus += "," + String.valueOf(status);
        }
    }

    public void dhcpfailedEvent(int status) {
        if ("".equals(this.mAssocStatus)) {
            this.mDhcpStatus += String.valueOf(status);
        } else {
            this.mDhcpStatus += "," + String.valueOf(status);
        }
    }

    public void setRssi(int rssi) {
        this.mRssi = rssi;
    }

    public void updateTargetBssid(String bssid) {
        LOGD("updateTargetBssid: " + bssid + ", current Bssid: " + this.mTargetBssid);
        WifiConfiguration mTempConnectConfig = this.mLastConnectConfig;
        if (TextUtils.isEmpty(bssid) || !TextUtils.isEmpty(this.mTargetBssid) || mTempConnectConfig == null) {
            LOGD("dont updateTargetBssid");
            return;
        }
        ScanResult sr = null;
        if (this.mWiFiCHRManager != null) {
            sr = this.mWiFiCHRManager.getScanResultByBssid(bssid);
        }
        String ssid = sr != null ? sr.SSID : "";
        String configssid = mTempConnectConfig.SSID;
        LOGD("ssid: " + ssid + ",configssid: " + configssid);
        if (!(TextUtils.isEmpty(ssid) || TextUtils.isEmpty(configssid))) {
            if (configssid.startsWith("\"") && configssid.length() >= 2) {
                configssid = configssid.substring(1, configssid.length() - 1);
                LOGD("configssid: " + configssid);
            }
            if (ssid.equals(configssid) && sr != null) {
                this.mTargetBssid = bssid;
                this.mApChannel = sr.frequency;
            }
        }
    }

    public void updateDhcpFailed() {
        this.mDhcpFailCnt = (short) (this.mDhcpFailCnt + 1);
    }

    public void fillUserConnectModel(CSegEVENT_WIFI_USER_CONNECT model) {
        StringBuilder sbuf = new StringBuilder();
        LOGD("fillUserConnectModel set ap mac: " + this.mTargetBssid);
        if (!TextUtils.isEmpty(this.mTargetBssid)) {
            model.strAP_MAC.setValue(this.mTargetBssid);
        }
        model.usAP_channel.setValue(this.mApChannel);
        WifiConfiguration mTempConnectConfig = this.mLastConnectConfig;
        if (mTempConnectConfig != null) {
            model.strAP_SSID.setValue(mTempConnectConfig.SSID);
            for (int k = 0; k < mTempConnectConfig.allowedKeyManagement.size(); k++) {
                if (mTempConnectConfig.allowedKeyManagement.get(k)) {
                    sbuf.append(HwCHRWifiCPUUsage.COL_SEP);
                    if (k < KeyMgmt.strings.length) {
                        sbuf.append(KeyMgmt.strings[k]);
                    } else {
                        sbuf.append("");
                    }
                }
            }
            model.strAP_key_mgmt.setValue(sbuf.toString());
            sbuf.delete(0, sbuf.length());
            for (int p = 0; p < mTempConnectConfig.allowedProtocols.size(); p++) {
                if (mTempConnectConfig.allowedProtocols.get(p)) {
                    sbuf.append(HwCHRWifiCPUUsage.COL_SEP);
                    if (p < Protocol.strings.length) {
                        sbuf.append(Protocol.strings[p]);
                    } else {
                        sbuf.append("");
                    }
                }
            }
            model.strAP_proto.setValue(sbuf.toString());
            sbuf.delete(0, sbuf.length());
            for (int a = 0; a < mTempConnectConfig.allowedAuthAlgorithms.size(); a++) {
                if (mTempConnectConfig.allowedAuthAlgorithms.get(a)) {
                    sbuf.append(HwCHRWifiCPUUsage.COL_SEP);
                    if (a < AuthAlgorithm.strings.length) {
                        sbuf.append(AuthAlgorithm.strings[a]);
                    } else {
                        sbuf.append("");
                    }
                }
            }
            model.strAP_auth_alg.setValue(sbuf.toString());
            sbuf.delete(0, sbuf.length());
            for (int pc = 0; pc < mTempConnectConfig.allowedPairwiseCiphers.size(); pc++) {
                if (mTempConnectConfig.allowedPairwiseCiphers.get(pc)) {
                    sbuf.append(HwCHRWifiCPUUsage.COL_SEP);
                    if (pc < PairwiseCipher.strings.length) {
                        sbuf.append(PairwiseCipher.strings[pc]);
                    } else {
                        sbuf.append("");
                    }
                }
            }
            model.strAP_pairwise.setValue(sbuf.toString());
            sbuf.delete(0, sbuf.length());
            for (int gc = 0; gc < mTempConnectConfig.allowedGroupCiphers.size(); gc++) {
                if (mTempConnectConfig.allowedGroupCiphers.get(gc)) {
                    sbuf.append(HwCHRWifiCPUUsage.COL_SEP);
                    if (gc < GroupCipher.strings.length) {
                        sbuf.append(GroupCipher.strings[gc]);
                    } else {
                        sbuf.append("");
                    }
                }
            }
            model.strAP_group.setValue(sbuf.toString());
            sbuf.delete(0, sbuf.length());
        }
        model.iAP_RSSI1.setValue(this.mRssis[0]);
        model.iAP_RSSI2.setValue(this.mRssis[1]);
        model.iAP_RSSI3.setValue(this.mRssis[2]);
        model.iAP_RSSI4.setValue(this.mRssis[3]);
        model.strFailureInfo.setValue(this.mAssocStatus + ";" + this.mAuthStatus);
        model.usAuthFailure.setValue(this.mAuthFailCnt);
        model.usAssocReject.setValue(this.mAssocRejectCnt);
        model.usDhcpFailure.setValue(this.mDhcpFailCnt);
        model.usDisconnect.setValue(this.mDisconnectCnt > this.mDhcpFailCnt ? (short) (this.mDisconnectCnt - this.mDhcpFailCnt) : (short) 0);
        model.ucScanAlwaysAvailble.setValue(getPersistedScanAlwaysAvailable());
        model.ucWIFIAlwaysNotifation.setValue(getWIFINetworkAvailableNotificationON());
        model.ucWIFISleepPolicy.setValue(getWIFISleepPolicy());
        model.ucWifiProStatus.setValue(getWIFIProStatus());
        model.ucProxySettings.setValue(getProxyStatus());
        model.strProxySettingInfo.setValue(getProxyInfo());
        model.ucWifiToPDP.setValue(getWIFITOPDP());
        model.iUsrTriggerDuration.setValue((int) (SystemClock.elapsedRealtime() - this.mUserConnectTimestamp));
        model.llconfigCRC.setValue(getConfigCrc());
        resetInfo();
    }

    private long getConfigCrc() {
        long result = 0;
        WifiConfiguration mTempConnectConfig = this.mLastConnectConfig;
        if (mTempConnectConfig != null) {
            if (TextUtils.isEmpty(mTempConnectConfig.preSharedKey)) {
                return 0;
            }
            if ("*".equals(mTempConnectConfig.preSharedKey)) {
                return 1;
            }
            String value = mTempConnectConfig.preSharedKey;
            CRC32 crc32 = new CRC32();
            crc32.update(value.getBytes(StandardCharsets.US_ASCII));
            result = crc32.getValue();
        }
        return result;
    }

    public void updateWifiTriggerState(boolean enable) {
        int ret = 0;
        try {
            ret = System.getInt(mContext.getContentResolver(), "wifi_on");
        } catch (SettingNotFoundException e) {
            LOGD("updateWifiTriggerState : " + e);
        }
        LOGD("updateWifiTriggerState : setting current : " + ret + " , mLastWifiState = " + this.mLastWifiState);
        if (hasMessages(EVENT_OPEN_TIMEOUT)) {
            removeMessages(EVENT_OPEN_TIMEOUT);
        }
        if (hasMessages(EVENT_CLOSE_TIMEOUT)) {
            removeMessages(EVENT_CLOSE_TIMEOUT);
        }
        if (enable) {
            if (this.mLastWifiState != 3) {
                sendEmptyMessageDelayed(EVENT_OPEN_TIMEOUT, 8000);
            }
        } else if (this.mLastWifiState != 1) {
            sendEmptyMessageDelayed(EVENT_CLOSE_TIMEOUT, 8000);
        }
        this.mWifiTriggerState = enable ? WIFI_TRIGGER_ON : WIFI_TRIGGER_OFF;
        HwWifiStatStoreImpl.getDefault().updateWifiTriggerState(enable);
        if (this.mWiFiCHRManager == null) {
            return;
        }
        if (WIFI_TRIGGER_ON == this.mWifiTriggerState) {
            this.mWiFiCHRManager.updateConnectType("WIFION_TO_SUCCESS");
            this.mWiFiCHRManager.updateTimeStampSessionStart(SystemClock.elapsedRealtime());
            return;
        }
        this.mWiFiCHRManager.updateTimeStampSessionFinish(SystemClock.elapsedRealtime());
        this.mWiFiCHRManager.reportHwCHRAccessNetworkEventInfoList(3);
    }

    public void updateOpenCloseStat(boolean enable) {
        if (enable) {
            removeMessages(EVENT_OPEN_TIMEOUT);
        } else {
            removeMessages(EVENT_CLOSE_TIMEOUT);
        }
        HwWifiStatStoreImpl.getDefault().updateWifiState(enable, false);
    }

    private void handleConnectFailed(int type, int delayed) {
        Message msg = obtainMessage(EVENT_CONNECT_FAILED);
        msg.arg1 = type;
        if (delayed <= 0) {
            sendMessage(msg);
        } else {
            sendMessageDelayed(msg, (long) delayed);
        }
    }

    private void uploadConnFailureCHR(int type) {
        if (this.mLastConnectConfig != null) {
            this.mWiFiCHRManager.uploadUserConnectFailed(type);
        }
    }

    private void resetInfo() {
        this.mAssocRejectCnt = (short) 0;
        this.mAuthFailCnt = (short) 0;
        this.mDhcpFailCnt = (short) 0;
        this.mDisconnectCnt = (short) 0;
        this.mUserConnectTimestamp = 0;
        this.mRssis[0] = 0;
        this.mRssis[1] = 0;
        this.mRssis[2] = 0;
        this.mRssis[3] = 0;
        this.mRssi = 0;
        this.mApChannel = 0;
        this.mTargetBssid = "";
        this.mAssocStatus = "";
        this.mAuthStatus = "";
        this.mDhcpStatus = "";
        this.mLastWpaState = SupplicantState.DISCONNECTED;
        this.mLastConnectConfig = null;
    }

    public void handleMessage(Message msg) {
        if (msg != null) {
            switch (msg.what) {
                case EVENT_CONNECT_TIMEOUT /*10002*/:
                case EVENT_CONNECT_FAILED /*10003*/:
                    uploadConnFailureCHR(msg.what == EVENT_CONNECT_TIMEOUT ? 6 : msg.arg1);
                    if (this.mPendingConfig != null) {
                        removeMessages(EVENT_CONNECT_TIMEOUT);
                        this.mUserConnectTimestamp = SystemClock.elapsedRealtime();
                        this.mLastConnectConfig = this.mPendingConfig;
                        sendEmptyMessageDelayed(EVENT_CONNECT_TIMEOUT, 45000);
                        this.mPendingConfig = null;
                        break;
                    }
                    break;
                case EVENT_OPEN_TIMEOUT /*10004*/:
                    if (this.mLastWifiState != 3) {
                        this.mWiFiCHRManager.updateWifiException(80, "TIMEOUT");
                        break;
                    }
                    break;
                case EVENT_CLOSE_TIMEOUT /*10005*/:
                    if (this.mLastWifiState != 1) {
                        this.mWiFiCHRManager.updateWifiException(81, "TIMEOUT");
                        break;
                    }
                    break;
                case EVENT_APK_CHANGED_WIFI_ENABLE_FALSE /*10006*/:
                    if (this.mWiFiCHRManager != null) {
                        this.mWiFiCHRManager.updateApkChangewWifiStatus(1, this.mApkChangedWifiEnableFalse_Name, this.mApkChangedWifiEnableFalse_Count, this.mUserAction);
                        this.mApkChangedWifiEnableFalse_Count = 0;
                        break;
                    }
                    break;
                case EVENT_APK_CHANGED_WIFI_DISABLE_NETWORK /*10007*/:
                    if (this.mWiFiCHRManager != null) {
                        this.mWiFiCHRManager.updateApkChangewWifiStatus(2, this.mApkChangedWifiDisableNetwork_Name, this.mApkChangedWifiDisableNetwork_Count, this.mUserAction);
                        this.mApkChangedWifiDisableNetwork_Count = 0;
                        break;
                    }
                    break;
                case EVENT_APK_CHANGED_SCAN_ENABLE /*10008*/:
                    if (this.mWiFiCHRManager != null) {
                        this.mWiFiCHRManager.updateApkChangewWifiStatus(3, this.mApkChangedScanEnable_Name, this.mApkChangedScanEnable_Count, this.mUserAction);
                        this.mApkChangedScanEnable_Count = 0;
                        this.mUserAction = "";
                        break;
                    }
                    break;
                case EVENT_APK_CHANGED_SCAN_TRIGGER /*10009*/:
                    if (this.mWiFiCHRManager != null) {
                        this.mWiFiCHRManager.updateApkChangewWifiStatus(4, this.mApkChangedScanTrigger_Name, this.mApkChangedScanTrigger_Count, this.mUserAction);
                        this.mApkChangedScanTrigger_Count = 0;
                        break;
                    }
                    break;
                case EVENT_CHECK_DRIVER /*10010*/:
                    if (this.mWiFiCHRManager != null) {
                        this.mWiFiCHRManager.updateWifiDirverException("/sys/devices/platform/bcmdhd_wlan.1/wifi_open_state");
                        break;
                    }
                    break;
            }
        }
    }

    private void fetchRssi(SupplicantState state) {
        int rssi = 0;
        if (state == SupplicantState.ASSOCIATING) {
            ScanResult sr = this.mWiFiCHRManager.getScanResultByBssid(this.mTargetBssid);
            rssi = sr != null ? sr.level : 0;
        } else if (state == SupplicantState.ASSOCIATED || state == SupplicantState.COMPLETED) {
            rssi = this.mRssi;
        }
        if (isRssiValid(rssi)) {
            int i = 0;
            while (i < this.mRssis.length) {
                if (isRssiValid(this.mRssis[i])) {
                    i++;
                } else {
                    this.mRssis[i] = rssi;
                    return;
                }
            }
        }
    }

    private void authFailEvent(int status) {
        if ("".equals(this.mAuthStatus)) {
            this.mAuthStatus += String.valueOf(status);
        } else {
            this.mAuthStatus += "," + String.valueOf(status);
        }
    }

    private boolean isRssiValid(int rssi) {
        return rssi > -100 && rssi < 0;
    }

    private HwWifiCHRServiceImpl(Looper looper) {
        super(looper);
        registerForBroadcasts();
        this.mWiFiCHRManager = HwWifiCHRStateManagerImpl.getDefault();
        sendEmptyMessageDelayed(EVENT_CHECK_DRIVER, 10000);
    }

    private void LOGD(String msg) {
        Log.d(TAG, msg);
    }

    private void LOGW(String msg) {
        Log.e(TAG, msg);
    }

    private void updateSettingsIfNeed() {
        long now = SystemClock.elapsedRealtime();
        if (now - this.mGetSettingTimestamp > SETTING_FLUSH_TIME) {
            this.mGetSettingTimestamp = now;
            if (mContext != null) {
                ContentResolver cr = mContext.getContentResolver();
                this.mSettingScanAlways = Global.getInt(cr, "wifi_scan_always_enabled", 0);
                this.mSettingSleepPolicy = Global.getInt(cr, "wifi_sleep_policy", 0);
                this.mSettingNetAvailableNotify = Global.getInt(cr, "wifi_networks_available_notification_on", 0);
                this.mSettingWiFiToPDP = System.getInt(cr, "wifi_to_pdp", 0);
                if (SystemProperties.getBoolean("ro.config.hw_wifipro_enable", false)) {
                    this.mSettingWiFiProState = System.getInt(cr, "smart_network_switching", 0) + 10;
                } else {
                    this.mSettingWiFiProState = 0;
                }
            }
        }
    }

    public int getPersistedScanAlwaysAvailable() {
        updateSettingsIfNeed();
        return this.mSettingScanAlways;
    }

    public int getWIFISleepPolicy() {
        updateSettingsIfNeed();
        return this.mSettingSleepPolicy;
    }

    public int getWIFINetworkAvailableNotificationON() {
        updateSettingsIfNeed();
        return this.mSettingNetAvailableNotify;
    }

    public int getWIFITOPDP() {
        updateSettingsIfNeed();
        return this.mSettingWiFiToPDP;
    }

    public int getWIFIProStatus() {
        updateSettingsIfNeed();
        return this.mSettingWiFiProState;
    }

    public void updateWIFIConfiguraion(WifiConfiguration cfg) {
        this.mRecentConfig = cfg;
        if (HwCHRWifiRelatedStateMonitor.make(null) != null) {
            HwCHRWifiRelatedStateMonitor.make(null).updateWIFIConfiguraion(cfg);
        }
    }

    public int getProxyStatus() {
        if (this.mRecentConfig == null) {
            return 0;
        }
        switch (-getandroid-net-IpConfiguration$ProxySettingsSwitchesValues()[this.mRecentConfig.getProxySettings().ordinal()]) {
            case 1:
                return 3;
            case 2:
                return 1;
            case 3:
                return 2;
            default:
                return 0;
        }
    }

    public void connectFromUserByConfig(WifiConfiguration config) {
        if (config != null) {
            connectFromUser(config);
        }
    }

    public void updateWIFIConfiguraionByConfig(WifiConfiguration config) {
        if (config != null) {
            updateWIFIConfiguraion(config);
        }
    }

    public void updateDhcpFailedState() {
    }

    public void updateConnectStateByConfig(WifiConfiguration config) {
        updateConnectState(config);
    }

    public String getProxyInfo() {
        if (this.mRecentConfig == null || this.mRecentConfig.getHttpProxy() == null) {
            return "";
        }
        return this.mRecentConfig.getHttpProxy().toString();
    }

    public void disableNetworkByNetworkId(int networkid) {
        WifiManager wifiManager = null;
        if (mContext != null) {
            wifiManager = (WifiManager) mContext.getSystemService("wifi");
        }
        if (!(wifiManager == null || networkid == -1)) {
            try {
                List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();
                if (configuredNetworks != null) {
                    for (WifiConfiguration config : configuredNetworks) {
                        if (config.networkId == networkid) {
                            wifiManager.disableNetwork(config.networkId);
                            LOGW("disable the Network: " + config.SSID + " networkid : " + config.networkId);
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void enableNetworkByNetworkId(int networkid) {
        WifiManager wifiManager = null;
        if (mContext != null) {
            wifiManager = (WifiManager) mContext.getSystemService("wifi");
        }
        if (!(wifiManager == null || networkid == -1)) {
            if (wifiManager.getWifiState() != 3) {
                LOGW("enable the network failed because the wifi state is disabled");
                return;
            }
            try {
                List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();
                if (configuredNetworks != null) {
                    for (WifiConfiguration config : configuredNetworks) {
                        if (config.networkId == networkid) {
                            wifiManager.enableNetwork(config.networkId, false);
                            LOGD("enable the Network: " + config.SSID + " networkid : " + config.networkId);
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void updateApkChangewWifiStatus(int apkAction, String apkName) {
        if (apkName != null && apkName.indexOf("android.uid.system") < 0) {
            if (apkName.length() > 32) {
                apkName = apkName.substring(apkName.length() - 32);
            }
            switch (apkAction) {
                case 1:
                    this.mApkChangedWifiEnableFalse_Name = apkName;
                    if (this.mApkChangedWifiEnableFalse_Count != 0) {
                        this.mApkChangedWifiEnableFalse_Count++;
                        break;
                    }
                    sendEmptyMessageDelayed(EVENT_APK_CHANGED_WIFI_ENABLE_FALSE, SETTING_FLUSH_TIME);
                    this.mApkChangedWifiEnableFalse_Count = 1;
                    break;
                case 2:
                    this.mApkChangedWifiDisableNetwork_Name = apkName;
                    if (this.mApkChangedWifiDisableNetwork_Count != 0) {
                        this.mApkChangedWifiDisableNetwork_Count++;
                        break;
                    }
                    sendEmptyMessageDelayed(EVENT_APK_CHANGED_WIFI_DISABLE_NETWORK, SETTING_FLUSH_TIME);
                    this.mApkChangedWifiDisableNetwork_Count = 1;
                    break;
                case 3:
                    this.mApkChangedScanEnable_Name = apkName;
                    if (this.mApkChangedScanEnable_Count != 0) {
                        this.mApkChangedScanEnable_Count++;
                        break;
                    }
                    sendEmptyMessageDelayed(EVENT_APK_CHANGED_SCAN_ENABLE, SETTING_FLUSH_TIME);
                    this.mApkChangedScanEnable_Count = 1;
                    break;
                case 4:
                    this.mApkChangedScanTrigger_Name = apkName;
                    if (this.mWifiTriggerState == WIFI_TRIGGER_OFF && getPersistedScanAlwaysAvailable() == 1) {
                        if (this.mApkChangedScanTrigger_Count != 0) {
                            this.mApkChangedScanTrigger_Count++;
                            break;
                        }
                        sendEmptyMessageDelayed(EVENT_APK_CHANGED_SCAN_TRIGGER, SETTING_FLUSH_TIME);
                        this.mApkChangedScanTrigger_Count = 1;
                        break;
                    }
            }
        }
    }

    protected static String getAppName(int pID) {
        String processName = "";
        if (mContext == null) {
            return null;
        }
        List<RunningAppProcessInfo> appProcessList = ((ActivityManager) mContext.getSystemService("activity")).getRunningAppProcesses();
        if (appProcessList == null) {
            return null;
        }
        for (RunningAppProcessInfo appProcess : appProcessList) {
            if (appProcess.pid == pID) {
                return appProcess.processName;
            }
        }
        return null;
    }
}
