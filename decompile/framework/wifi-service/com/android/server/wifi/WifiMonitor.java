package com.android.server.wifi;

import android.net.NetworkInfo.DetailedState;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiSsid;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pProvDiscEvent;
import android.net.wifi.p2p.nsd.WifiP2pServiceResponse;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.LocalLog;
import android.util.Log;
import android.util.SparseArray;
import com.android.server.wifi.WifiStateMachine.SimAuthRequestData;
import com.android.server.wifi.anqp.Constants;
import com.android.server.wifi.hotspot2.IconEvent;
import com.android.server.wifi.hotspot2.Utils;
import com.android.server.wifi.p2p.WifiP2pServiceImpl.P2pStatus;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WifiMonitor {
    private static final String ADDR_STRING = "addr=";
    public static final int ANQP_DONE_EVENT = 147500;
    private static final String ANQP_DONE_STR = "ANQP-QUERY-DONE";
    public static final String ANT_CORE_ROB = "ANT_CORE_ROB";
    public static final int AP_STA_CONNECTED_EVENT = 147498;
    private static final String AP_STA_CONNECTED_STR = "AP-STA-CONNECTED";
    public static final int AP_STA_DISCONNECTED_EVENT = 147497;
    private static final String AP_STA_DISCONNECTED_STR = "AP-STA-DISCONNECTED";
    private static final String ASSOCIATED_WITH_STR = "Associated with ";
    public static final int ASSOCIATION_REJECTION_EVENT = 147499;
    private static final int ASSOC_REJECT = 9;
    private static final String ASSOC_REJECT_STR = "ASSOC-REJECT";
    public static final int AUTHENTICATION_FAILURE_EVENT = 147463;
    public static final int AUTHENTICATION_TIMEOUT_EVENT = 147501;
    private static final String AUTH_EVENT_PREFIX_STR = "Authentication with";
    private static final String AUTH_TIMEOUT_STR = "timed out.";
    private static final int BASE = 147456;
    private static final int BSS_ADDED = 12;
    private static final String BSS_ADDED_STR = "BSS-ADDED";
    private static final int BSS_REMOVED = 13;
    private static final String BSS_REMOVED_STR = "BSS-REMOVED";
    static final long CHECK_INTERVAL = 30000;
    private static final int CONFIG_AUTH_FAILURE = 18;
    private static final int CONFIG_MULTIPLE_PBC_DETECTED = 12;
    private static final int CONNECTED = 1;
    private static final String CONNECTED_STR = "CONNECTED";
    private static final String ConnectPrefix = "Connection to ";
    private static final String ConnectSuffix = " completed";
    private static boolean DBG = HWFLOW;
    private static final int DISCONNECTED = 2;
    private static final String DISCONNECTED_STR = "DISCONNECTED";
    public static final int DRIVER_HUNG_EVENT = 147468;
    private static final int DRIVER_STATE = 7;
    private static final String DRIVER_STATE_STR = "DRIVER-STATE";
    private static final String EAP_AUTH_FAILURE_STR = "EAP authentication failed";
    private static final int EAP_FAILURE = 8;
    private static final String EAP_FAILURE_STR = "EAP-FAILURE";
    public static final int EVENT_ANT_CORE_ROB = 147757;
    private static final int EVENT_PREFIX_LEN_STR = EVENT_PREFIX_STR.length();
    private static final String EVENT_PREFIX_STR = "CTRL-EVENT-";
    public static final int GAS_QUERY_DONE_EVENT = 147508;
    private static final String GAS_QUERY_DONE_STR = "GAS-QUERY-DONE";
    private static final String GAS_QUERY_PREFIX_STR = "GAS-QUERY-";
    public static final int GAS_QUERY_START_EVENT = 147507;
    private static final String GAS_QUERY_START_STR = "GAS-QUERY-START";
    public static final int HEART_BEAT_ACK_EVENT = 147506;
    private static final String HOST_AP_EVENT_PREFIX_STR = "AP";
    public static final String HS20_DEAUTH_STR = "HS20-DEAUTH-IMMINENT-NOTICE";
    private static final String HS20_ICON_STR = "RX-HS20-ICON";
    private static final String HS20_PREFIX_STR = "HS20-";
    public static final int HS20_REMEDIATION_EVENT = 147517;
    public static final String HS20_SUB_REM_STR = "HS20-SUBSCRIPTION-REMEDIATION";
    protected static final boolean HWFLOW;
    private static boolean HWLOGW_E = true;
    private static final String IDENTITY_STR = "IDENTITY";
    private static final int LINK_SPEED = 5;
    private static final String LINK_SPEED_STR = "LINK-SPEED";
    private static final int MAX_RECV_ERRORS = 10;
    public static final int NETWORK_CONNECTION_EVENT = 147459;
    public static final int NETWORK_DISCONNECTION_EVENT = 147460;
    public static final int P2P_DEVICE_FOUND_EVENT = 147477;
    private static final String P2P_DEVICE_FOUND_STR = "P2P-DEVICE-FOUND";
    public static final int P2P_DEVICE_LOST_EVENT = 147478;
    private static final String P2P_DEVICE_LOST_STR = "P2P-DEVICE-LOST";
    private static final String P2P_EVENT_PREFIX_STR = "P2P";
    public static final int P2P_FIND_STOPPED_EVENT = 147493;
    private static final String P2P_FIND_STOPPED_STR = "P2P-FIND-STOPPED";
    public static final int P2P_GC_INTERFACE_CREATED_EVENT = 147557;
    public static final int P2P_GO_INTERFACE_CREATED_EVENT = 147556;
    public static final int P2P_GO_NEGOTIATION_FAILURE_EVENT = 147482;
    public static final int P2P_GO_NEGOTIATION_REQUEST_EVENT = 147479;
    public static final int P2P_GO_NEGOTIATION_SUCCESS_EVENT = 147481;
    private static final String P2P_GO_NEG_FAILURE_STR = "P2P-GO-NEG-FAILURE";
    private static final String P2P_GO_NEG_REQUEST_STR = "P2P-GO-NEG-REQUEST";
    private static final String P2P_GO_NEG_SUCCESS_STR = "P2P-GO-NEG-SUCCESS";
    public static final int P2P_GROUP_FORMATION_FAILURE_EVENT = 147484;
    private static final String P2P_GROUP_FORMATION_FAILURE_STR = "P2P-GROUP-FORMATION-FAILURE";
    public static final int P2P_GROUP_FORMATION_SUCCESS_EVENT = 147483;
    private static final String P2P_GROUP_FORMATION_SUCCESS_STR = "P2P-GROUP-FORMATION-SUCCESS";
    public static final int P2P_GROUP_REMOVED_EVENT = 147486;
    private static final String P2P_GROUP_REMOVED_STR = "P2P-GROUP-REMOVED";
    public static final int P2P_GROUP_STARTED_EVENT = 147485;
    private static final String P2P_GROUP_STARTED_STR = "P2P-GROUP-STARTED";
    private static final String P2P_INTERFACE_CREATED_STR = "P2P-INTERFACE-CREATED";
    public static final int P2P_INVITATION_RECEIVED_EVENT = 147487;
    private static final String P2P_INVITATION_RECEIVED_STR = "P2P-INVITATION-RECEIVED";
    public static final int P2P_INVITATION_RESULT_EVENT = 147488;
    private static final String P2P_INVITATION_RESULT_STR = "P2P-INVITATION-RESULT";
    public static final int P2P_PROV_DISC_ENTER_PIN_EVENT = 147491;
    private static final String P2P_PROV_DISC_ENTER_PIN_STR = "P2P-PROV-DISC-ENTER-PIN";
    public static final int P2P_PROV_DISC_FAILURE_EVENT = 147495;
    private static final String P2P_PROV_DISC_FAILURE_STR = "P2P-PROV-DISC-FAILURE";
    public static final int P2P_PROV_DISC_PBC_REQ_EVENT = 147489;
    private static final String P2P_PROV_DISC_PBC_REQ_STR = "P2P-PROV-DISC-PBC-REQ";
    public static final int P2P_PROV_DISC_PBC_RSP_EVENT = 147490;
    private static final String P2P_PROV_DISC_PBC_RSP_STR = "P2P-PROV-DISC-PBC-RESP";
    public static final int P2P_PROV_DISC_SHOW_PIN_EVENT = 147492;
    private static final String P2P_PROV_DISC_SHOW_PIN_STR = "P2P-PROV-DISC-SHOW-PIN";
    public static final int P2P_REMOVE_AND_REFORM_GROUP_EVENT = 147496;
    private static final String P2P_REMOVE_AND_REFORM_GROUP_STR = "P2P-REMOVE-AND-REFORM-GROUP";
    public static final int P2P_SERV_DISC_RESP_EVENT = 147494;
    private static final String P2P_SERV_DISC_RESP_STR = "P2P-SERV-DISC-RESP";
    private static final String PASSWORD_MAY_BE_INCORRECT_STR = "pre-shared key may be incorrect";
    private static final int REASON_TKIP_ONLY_PROHIBITED = 1;
    private static final int REASON_WEP_PROHIBITED = 2;
    private static final String REENABLED_STR = "SSID-REENABLED";
    private static final int REQUEST_PREFIX_LEN_STR = REQUEST_PREFIX_STR.length();
    private static final String REQUEST_PREFIX_STR = "CTRL-REQ-";
    private static final String RESULT_STRING = "result=";
    public static final int RSN_PMKID_MISMATCH_EVENT = 147519;
    private static final String RSN_PMKID_STR = "RSN: PMKID mismatch";
    public static final int RX_HS20_ANQP_ICON_EVENT = 147509;
    private static final String RX_HS20_ANQP_ICON_STR = "RX-HS20-ANQP-ICON";
    private static final int RX_HS20_ANQP_ICON_STR_LEN = RX_HS20_ANQP_ICON_STR.length();
    private static final int SCAN_FAILED = 15;
    public static final int SCAN_FAILED_EVENT = 147473;
    private static final String SCAN_FAILED_STR = "SCAN-FAILED";
    private static final int SCAN_RESULTS = 4;
    public static final int SCAN_RESULTS_EVENT = 147461;
    private static final String SCAN_RESULTS_STR = "SCAN-RESULTS";
    private static final String SIM_STR = "SIM";
    private static final int[] SLEEP_TIME_RETRY = new int[]{100, 200, 400, 800, 1600};
    private static final int SSID_REENABLE = 11;
    public static final int SSID_REENABLED = 147470;
    private static final int SSID_TEMP_DISABLE = 10;
    public static final int SSID_TEMP_DISABLED = 147469;
    private static final int STATE_CHANGE = 3;
    private static final String STATE_CHANGE_STR = "STATE-CHANGE";
    private static final int STATE_EVENT_PROCESSING = 2;
    private static final int STATE_EVENT_WAIT = 1;
    private static final int STATE_THREAD_OVER = 0;
    public static final int SUPPLICANT_STATE_CHANGE_EVENT = 147462;
    public static final int SUP_CONNECTION_EVENT = 147457;
    public static final int SUP_DISCONNECTION_EVENT = 147458;
    public static final int SUP_REQUEST_IDENTITY = 147471;
    public static final int SUP_REQUEST_SIM_AUTH = 147472;
    private static final String TAG = "WifiMonitor";
    private static final String TARGET_BSSID_STR = "Trying to associate with ";
    private static final String TEMP_DISABLED_STR = "SSID-TEMP-DISABLED";
    private static final int TERMINATING = 6;
    private static final String TERMINATING_STR = "TERMINATING";
    private static final int UNKNOWN = 14;
    private static final boolean VDBG = HWFLOW;
    private static final String VOWIFI_DETECT_IRQ_STR = "VOWIFI_DETECT_IRQ";
    public static final int VOWIFI_DETECT_IRQ_STR_EVENT = 147520;
    public static final int WAPI_AUTHENTICATION_FAILURE_EVENT = 147474;
    private static final String WAPI_AUTHENTICATION_FAILURE_STR = "authentication failed";
    public static final int WAPI_CERTIFICATION_FAILURE_EVENT = 147475;
    private static final String WAPI_CERTIFICATION_FAILURE_STR = "certificate initialization failed";
    private static final String WAPI_EVENT_PREFIX_STR = "WAPI:";
    private static final String WPA_EVENT_PREFIX_STR = "WPA:";
    private static final String WPA_RECV_ERROR_STR = "recv error";
    private static final String WPS_FAIL_EAPOL_STR = "WPS-FAIL ";
    public static final int WPS_FAIL_EVENT = 147465;
    private static final String WPS_FAIL_PATTERN = "WPS-FAIL msg=\\d+(?: config_error=(\\d+))?(?: reason=(\\d+))?";
    private static final String WPS_FAIL_STR = "WPS-FAIL";
    public static final int WPS_OVERLAP_EVENT = 147466;
    private static final String WPS_OVERLAP_STR = "WPS-OVERLAP-DETECTED";
    public static final int WPS_START_OKC_EVENT = 147656;
    private static final String WPS_START_OKC_STR = "WPS_START_OKC";
    public static final int WPS_SUCCESS_EVENT = 147464;
    private static final String WPS_SUCCESS_STR = "WPS-SUCCESS";
    public static final int WPS_TIMEOUT_EVENT = 147467;
    private static final String WPS_TIMEOUT_STR = "WPS-TIMEOUT";
    private static int eventLogCounter = 0;
    private static Pattern mAssocRejectEventPattern = Pattern.compile("((?:[0-9a-f]{2}:){5}[0-9a-f]{2}) +status_code=([0-9]+)");
    private static Pattern mAssociatedPattern = Pattern.compile("Associated with ((?:[0-9a-f]{2}:){5}[0-9a-f]{2}).*");
    private static Pattern mConnectedEventPattern = Pattern.compile("((?:[0-9a-f]{2}:){5}[0-9a-f]{2}) .* \\[id=([0-9]+) ");
    private static Pattern mDisconnectedEventPattern = Pattern.compile("((?:[0-9a-f]{2}:){5}[0-9a-f]{2}) +reason=([0-9]+) +locally_generated=([0-1])");
    private static volatile int mEventToken;
    private static final Object mEventTokenLock = new Object();
    private static MonitorThread mMonitorThread;
    private static volatile long mMonitorThreadId;
    private static volatile int mMonitorThreadState;
    private static Pattern mOnlyStatusCodeAssocRejectEventPattern = Pattern.compile("status_code=([0-9]+)");
    private static Pattern mRequestGsmAuthPattern = Pattern.compile("SIM-([0-9]*):GSM-AUTH((:[0-9a-f]+)+) needed for SSID (.+)");
    private static Pattern mRequestIdentityPattern = Pattern.compile("IDENTITY-([0-9]+):Identity needed for SSID (.+)");
    private static Pattern mRequestUmtsAuthPattern = Pattern.compile("SIM-([0-9]*):UMTS-AUTH:([0-9a-f]+):([0-9a-f]+) needed for SSID (.+)");
    private static Pattern mTargetBSSIDPattern = Pattern.compile("Trying to associate with ((?:[0-9a-f]{2}:){5}[0-9a-f]{2}).*");
    private static WifiMonitor sWifiMonitor = new WifiMonitor();
    private boolean isWifiInterface;
    private boolean mConnected;
    private final Map<String, SparseArray<Set<Handler>>> mHandlerMap;
    private HwWifiCHRService mHwWifiCHRService;
    private HwWifiMonitor mHwWifiMonitor;
    private Map<String, Long> mLastConnectBSSIDs;
    private final Map<String, Boolean> mMonitoringMap;
    private int mRecvErrors;
    private HwWifiCHRStateManager mWiFiCHRManager;
    private final WifiNative mWifiNative;
    private long time1;
    private long time2;
    private DataUploader uploader;

    private class MonitorThread extends Thread {
        private final LocalLog mLocalLog = WifiNative.getLocalLog();

        public MonitorThread() {
            super(WifiMonitor.TAG);
            WifiMonitor.this.mWifiNative;
        }

        public void run() {
            if (WifiMonitor.DBG) {
                Log.d(WifiMonitor.TAG, "MonitorThread start with mConnected=" + WifiMonitor.this.mConnected);
            }
            WifiMonitor.mEventToken = 0;
            WifiMonitor.mMonitorThreadId = Thread.currentThread().getId();
            while (WifiMonitor.this.mConnected) {
                WifiMonitor.mMonitorThreadState = 1;
                String eventStr = WifiMonitor.this.mWifiNative.waitForEvent();
                WifiMonitor.mMonitorThreadState = 2;
                if (WifiMonitor.mEventToken >= Constants.SHORT_MASK) {
                    WifiMonitor.mEventToken = 0;
                }
                synchronized (WifiMonitor.mEventTokenLock) {
                    WifiMonitor.mEventToken = WifiMonitor.mEventToken + 1;
                }
                if (eventStr.indexOf(WifiMonitor.BSS_ADDED_STR) == -1 && eventStr.indexOf(WifiMonitor.BSS_REMOVED_STR) == -1) {
                    if (WifiMonitor.DBG) {
                        if (eventStr.contains("passphrase") || eventStr.contains("psk=")) {
                            Log.d(WifiMonitor.TAG, "Event :" + eventStr.substring(0, eventStr.indexOf(" ")) + " contains password hide it");
                        } else if (eventStr.indexOf("SCAN-STARTED") == -1) {
                            Log.d(WifiMonitor.TAG, "Event [" + eventStr + "]");
                        }
                    }
                    if (eventStr.contains("passphrase") || eventStr.contains("psk=")) {
                        this.mLocalLog.log("Event [" + eventStr.substring(0, eventStr.indexOf(" ")) + "] contains password hide it");
                    } else {
                        this.mLocalLog.log("Event [" + eventStr + "]");
                    }
                }
                if (WifiMonitor.this.dispatchEvent(eventStr)) {
                    WifiMonitor.mMonitorThread = null;
                    WifiMonitor.mMonitorThreadId = 0;
                    WifiMonitor.mMonitorThreadState = 0;
                    return;
                }
            }
            WifiMonitor.mMonitorThread = null;
            WifiMonitor.mMonitorThreadId = 0;
            WifiMonitor.mMonitorThreadState = 0;
            if (WifiMonitor.DBG) {
                Log.d(WifiMonitor.TAG, "MonitorThread exit because mConnected is false");
            }
        }
    }

    private static class WatchdogThread extends Thread {
        private long backupThreadId;
        private int preEventToken;

        public WatchdogThread() {
            super("WifiMonitorWatchdog");
        }

        private void waitMonent(long timeout) {
            synchronized (this) {
                long waitTime = timeout;
                long start = SystemClock.uptimeMillis();
                while (timeout > 0) {
                    try {
                        wait(timeout);
                    } catch (InterruptedException e) {
                        Log.wtf(WifiMonitor.TAG, e);
                    }
                    timeout = waitTime - (SystemClock.uptimeMillis() - start);
                }
            }
        }

        public void run() {
            this.backupThreadId = WifiMonitor.mMonitorThreadId;
            int wait_state_counter = 0;
            int pretoken = 0;
            while (WifiMonitor.mMonitorThreadState != 0 && this.backupThreadId == WifiMonitor.mMonitorThreadId) {
                if (WifiMonitor.mMonitorThreadState == 1) {
                    waitMonent(15000);
                    wait_state_counter++;
                    synchronized (WifiMonitor.mEventTokenLock) {
                        if (pretoken != WifiMonitor.mEventToken) {
                            pretoken = WifiMonitor.mEventToken;
                            wait_state_counter = 0;
                        }
                    }
                    if (wait_state_counter > 6) {
                        wait_state_counter = 0;
                        Log.d(WifiMonitor.TAG, "WatchdogThread wait state.");
                    }
                } else {
                    this.preEventToken = WifiMonitor.mEventToken;
                    waitMonent(WifiMonitor.CHECK_INTERVAL);
                    if (WifiMonitor.mMonitorThreadState == 2 && this.preEventToken == WifiMonitor.mEventToken && WifiMonitor.mMonitorThread != null) {
                        StackTraceElement[] stackTrace = WifiMonitor.mMonitorThread.getStackTrace();
                        Log.d(WifiMonitor.TAG, "WatchdogThread mMonitorThread statckTrace:");
                        for (StackTraceElement element : stackTrace) {
                            Log.d(WifiMonitor.TAG, "    at " + element);
                        }
                    }
                }
            }
        }
    }

    static {
        boolean isLoggable = !Log.HWINFO ? Log.HWModuleLog ? Log.isLoggable(TAG, 4) : false : true;
        HWFLOW = isLoggable;
    }

    public static WifiMonitor getInstance() {
        return sWifiMonitor;
    }

    private WifiMonitor() {
        this.mHwWifiMonitor = HwWifiServiceFactory.getHwWifiMonitor();
        this.time1 = -1;
        this.time2 = -1;
        this.isWifiInterface = false;
        this.mRecvErrors = 0;
        this.mConnected = false;
        this.mHandlerMap = new HashMap();
        this.mMonitoringMap = new HashMap();
        this.mLastConnectBSSIDs = new HashMap<String, Long>() {
            public Long get(String iface) {
                Long value = (Long) super.get(iface);
                if (value != null) {
                    return value;
                }
                return Long.valueOf(0);
            }
        };
        this.mWifiNative = WifiNative.getWlanNativeInterface();
        this.mWiFiCHRManager = HwWifiServiceFactory.getHwWifiCHRStateManager();
        this.mHwWifiCHRService = HwWifiServiceFactory.getHwWifiCHRService();
        this.isWifiInterface = HwWifiCHRStateManager.MAIN_IFACE.equals(this.mWifiNative.getInterfaceName());
        this.uploader = DataUploader.getInstance();
    }

    void enableVerboseLogging(int verbose) {
        if (verbose > 0) {
            DBG = true;
        } else {
            DBG = false;
        }
    }

    public synchronized void registerHandler(String iface, int what, Handler handler) {
        SparseArray<Set<Handler>> ifaceHandlers = (SparseArray) this.mHandlerMap.get(iface);
        if (ifaceHandlers == null) {
            ifaceHandlers = new SparseArray();
            this.mHandlerMap.put(iface, ifaceHandlers);
        }
        Set<Handler> ifaceWhatHandlers = (Set) ifaceHandlers.get(what);
        if (ifaceWhatHandlers == null) {
            ifaceWhatHandlers = new ArraySet();
            ifaceHandlers.put(what, ifaceWhatHandlers);
        }
        ifaceWhatHandlers.add(handler);
    }

    private boolean isMonitoring(String iface) {
        Boolean val = (Boolean) this.mMonitoringMap.get(iface);
        if (val == null) {
            return false;
        }
        return val.booleanValue();
    }

    private void setMonitoring(String iface, boolean enabled) {
        this.mMonitoringMap.put(iface, Boolean.valueOf(enabled));
    }

    private void setMonitoringNone() {
        for (String iface : this.mMonitoringMap.keySet()) {
            setMonitoring(iface, false);
        }
    }

    private boolean ensureConnectedLocked() {
        if (this.mConnected) {
            return true;
        }
        if (DBG) {
            Log.d(TAG, "connecting to supplicant");
        }
        int connectTries = 0;
        while (!this.mWifiNative.connectToSupplicant()) {
            if (connectTries >= 5) {
                return false;
            }
            try {
                Log.e(TAG, "startMonitoring connectTries sleep:" + SLEEP_TIME_RETRY[connectTries]);
                Thread.sleep((long) SLEEP_TIME_RETRY[connectTries]);
                connectTries++;
            } catch (InterruptedException e) {
            }
        }
        this.mConnected = true;
        mMonitorThread = new MonitorThread();
        mMonitorThread.start();
        new WatchdogThread().start();
        return true;
    }

    public synchronized boolean startMonitoring(String iface) {
        Log.d(TAG, "startMonitoring(" + iface + ") with mConnected = " + this.mConnected);
        if (ensureConnectedLocked()) {
            setMonitoring(iface, true);
            sendMessage(iface, (int) SUP_CONNECTION_EVENT);
            return true;
        }
        boolean originalMonitoring = isMonitoring(iface);
        setMonitoring(iface, true);
        sendMessage(iface, (int) SUP_DISCONNECTION_EVENT);
        setMonitoring(iface, originalMonitoring);
        Log.e(TAG, "startMonitoring(" + iface + ") failed!");
        return false;
    }

    public synchronized void stopMonitoring(String iface) {
        if (DBG) {
            Log.d(TAG, "stopMonitoring(" + iface + ")");
        }
        setMonitoring(iface, true);
        sendMessage(iface, (int) SUP_DISCONNECTION_EVENT);
        setMonitoring(iface, false);
    }

    public synchronized void stopSupplicant() {
        this.mWifiNative.stopSupplicant();
    }

    public synchronized void killSupplicant(boolean p2pSupported) {
        String suppState = System.getProperty("init.svc.wpa_supplicant");
        if (suppState == null) {
            suppState = "unknown";
        }
        String p2pSuppState = System.getProperty("init.svc.p2p_supplicant");
        if (p2pSuppState == null) {
            p2pSuppState = "unknown";
        }
        Log.e(TAG, "killSupplicant p2p" + p2pSupported + " init.svc.wpa_supplicant=" + suppState + " init.svc.p2p_supplicant=" + p2pSuppState);
        this.mWifiNative.killSupplicant(p2pSupported);
        this.mConnected = false;
        setMonitoringNone();
    }

    private void sendMessage(String iface, int what) {
        sendMessage(iface, Message.obtain(null, what));
    }

    private void sendMessage(String iface, int what, Object obj) {
        sendMessage(iface, Message.obtain(null, what, obj));
    }

    private void sendMessage(String iface, int what, int arg1) {
        sendMessage(iface, Message.obtain(null, what, arg1, 0));
    }

    private void sendMessage(String iface, int what, int arg1, int arg2) {
        sendMessage(iface, Message.obtain(null, what, arg1, arg2));
    }

    private void sendMessage(String iface, int what, int arg1, int arg2, Object obj) {
        sendMessage(iface, Message.obtain(null, what, arg1, arg2, obj));
    }

    private void sendMessage(String iface, Message message) {
        SparseArray<Set<Handler>> ifaceHandlers = (SparseArray) this.mHandlerMap.get(iface);
        boolean firstHandler;
        if (iface == null || ifaceHandlers == null) {
            if (DBG) {
                Log.d(TAG, "Sending to all monitors because there's no matching iface");
            }
            firstHandler = true;
            for (Entry<String, SparseArray<Set<Handler>>> entry : this.mHandlerMap.entrySet()) {
                if (isMonitoring((String) entry.getKey())) {
                    for (Handler handler : (Set) ((SparseArray) entry.getValue()).get(message.what)) {
                        if (firstHandler) {
                            firstHandler = false;
                            sendMessage(handler, message);
                        } else {
                            sendMessage(handler, Message.obtain(message));
                        }
                    }
                }
            }
        } else if (isMonitoring(iface)) {
            firstHandler = true;
            Set<Handler> ifaceWhatHandlers = (Set) ifaceHandlers.get(message.what);
            if (ifaceWhatHandlers != null) {
                for (Handler handler2 : ifaceWhatHandlers) {
                    if (firstHandler) {
                        firstHandler = false;
                        sendMessage(handler2, message);
                    } else {
                        sendMessage(handler2, Message.obtain(message));
                    }
                }
            }
        } else if (DBG) {
            Log.d(TAG, "Dropping event because (" + iface + ") is stopped");
        }
    }

    private void sendMessage(Handler handler, Message message) {
        if (handler != null) {
            message.setTarget(handler);
            message.sendToTarget();
        }
    }

    private synchronized boolean dispatchEvent(String eventStr) {
        String iface;
        if (eventStr.startsWith("IFNAME=")) {
            int space = eventStr.indexOf(32);
            if (space != -1) {
                iface = eventStr.substring(7, space);
                if (!this.mHandlerMap.containsKey(iface) && iface.startsWith("p2p-")) {
                    iface = "p2p0";
                }
                eventStr = eventStr.substring(space + 1);
            } else {
                Log.e(TAG, "Dropping malformed event (unparsable iface): " + eventStr);
                return false;
            }
        }
        iface = "p2p0";
        if (VDBG) {
            Log.d(TAG, "Dispatching event to interface: " + iface);
        }
        if (!dispatchEvent(eventStr, iface)) {
            return false;
        }
        this.mConnected = false;
        return true;
    }

    private void logDbg(String debug) {
        Log.e(TAG, debug);
    }

    private boolean dispatchEvent(String eventStr, String iface) {
        if (eventStr == null) {
            return false;
        }
        if (VDBG) {
            if (eventStr.indexOf(BSS_ADDED_STR) == -1) {
                if (eventStr.indexOf(BSS_REMOVED_STR) == -1) {
                    if (!eventStr.contains("passphrase")) {
                        if (!eventStr.contains("psk=")) {
                            logDbg("WifiMonitor:" + iface + " cnt=" + Integer.toString(eventLogCounter) + " dispatchEvent: " + eventStr);
                        }
                    }
                }
            }
        }
        if (this.mWiFiCHRManager != null) {
            this.mWiFiCHRManager.setCurrentMsgIface(iface);
        }
        if (eventStr.startsWith(EVENT_PREFIX_STR)) {
            String eventName = eventStr.substring(EVENT_PREFIX_LEN_STR);
            int nameEnd = eventName.indexOf(32);
            if (nameEnd != -1) {
                eventName = eventName.substring(0, nameEnd);
            }
            if (eventName.length() == 0) {
                if (DBG) {
                    Log.i(TAG, "Received wpa_supplicant event with empty event name");
                }
                eventLogCounter++;
                return false;
            }
            int event;
            int ind;
            if (eventName.equals(CONNECTED_STR)) {
                event = 1;
                long bssid = -1;
                int prefix = eventStr.indexOf(ConnectPrefix);
                if (prefix >= 0) {
                    int suffix = eventStr.indexOf(ConnectSuffix);
                    if (suffix > prefix) {
                        try {
                            bssid = Utils.parseMac(eventStr.substring(ConnectPrefix.length() + prefix, suffix));
                        } catch (IllegalArgumentException e) {
                            bssid = -1;
                        }
                    }
                }
                this.mLastConnectBSSIDs.put(iface, Long.valueOf(bssid));
                if (bssid == -1) {
                    Log.w(TAG, "Failed to parse out BSSID from '" + eventStr + "'");
                }
            } else {
                if (eventName.equals(DISCONNECTED_STR)) {
                    event = 2;
                } else {
                    if (eventName.equals(STATE_CHANGE_STR)) {
                        event = 3;
                    } else {
                        if (eventName.equals(SCAN_RESULTS_STR)) {
                            event = 4;
                        } else {
                            if (eventName.equals(SCAN_FAILED_STR)) {
                                event = 15;
                            } else {
                                if (eventName.equals(LINK_SPEED_STR)) {
                                    event = 5;
                                } else {
                                    if (eventName.equals(TERMINATING_STR)) {
                                        event = 6;
                                    } else {
                                        if (eventName.equals(DRIVER_STATE_STR)) {
                                            event = 7;
                                        } else {
                                            if (eventName.equals(EAP_FAILURE_STR)) {
                                                event = 8;
                                            } else {
                                                if (eventName.equals(ASSOC_REJECT_STR)) {
                                                    event = 9;
                                                } else {
                                                    if (eventName.equals(TEMP_DISABLED_STR)) {
                                                        event = 10;
                                                    } else {
                                                        if (eventName.equals(REENABLED_STR)) {
                                                            event = 11;
                                                        } else {
                                                            if (eventName.equals(BSS_ADDED_STR)) {
                                                                event = 12;
                                                            } else {
                                                                event = eventName.equals(BSS_REMOVED_STR) ? 13 : 14;
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            String eventData = eventStr;
            if (event == 7 || event == 5) {
                eventData = eventStr.split(" ")[1];
            } else if (event == 3 || event == 8) {
                ind = eventStr.indexOf(" ");
                if (ind != -1) {
                    eventData = eventStr.substring(ind + 1);
                }
            } else {
                ind = eventStr.indexOf(" - ");
                if (ind != -1) {
                    eventData = eventStr.substring(ind + 3);
                }
            }
            int i;
            if (event == 10 || event == 11) {
                String substr = null;
                int netId = -1;
                ind = eventStr.indexOf(" ");
                if (ind != -1) {
                    substr = eventStr.substring(ind + 1);
                }
                if (substr != null) {
                    for (String key : substr.split(" ")) {
                        if (key.regionMatches(0, "id=", 0, 3)) {
                            netId = 0;
                            for (int idx = 3; idx < key.length(); idx++) {
                                char c = key.charAt(idx);
                                if (c < '0' || c > '9') {
                                    break;
                                }
                                netId = (netId * 10) + (c - 48);
                            }
                        }
                    }
                }
                sendMessage(iface, event == 10 ? SSID_TEMP_DISABLED : SSID_REENABLED, netId, 0, substr);
            } else if (event == 3) {
                handleSupplicantStateChange(eventData, iface);
            } else if (event == 7) {
                handleDriverEvent(eventData, iface);
            } else if (event == 6) {
                if (eventData.startsWith(WPA_RECV_ERROR_STR)) {
                    i = this.mRecvErrors + 1;
                    this.mRecvErrors = i;
                    if (i <= 10) {
                        eventLogCounter++;
                        return false;
                    } else if (DBG) {
                        Log.d(TAG, "too many recv errors, closing connection");
                    }
                }
                sendMessage(null, (int) SUP_DISCONNECTION_EVENT, eventLogCounter);
                return true;
            } else if (event == 8) {
                if (eventData.startsWith(EAP_AUTH_FAILURE_STR)) {
                    logDbg("WifiMonitor send auth failure (EAP_AUTH_FAILURE) ");
                    if (this.mWiFiCHRManager != null) {
                        this.mWiFiCHRManager.uploadConnectFailed();
                    }
                    sendMessage(iface, (int) AUTHENTICATION_FAILURE_EVENT, eventLogCounter);
                }
            } else if (event == 9) {
                Matcher match = mAssocRejectEventPattern.matcher(eventData);
                String BSSID = "";
                int status = -1;
                if (match.find()) {
                    int statusGroupNumber;
                    if (match.groupCount() == 2) {
                        BSSID = match.group(1);
                        statusGroupNumber = 2;
                    } else {
                        BSSID = null;
                        statusGroupNumber = 1;
                    }
                    try {
                        status = Integer.parseInt(match.group(statusGroupNumber));
                    } catch (NumberFormatException e2) {
                        status = -1;
                    }
                } else {
                    Matcher match1 = mOnlyStatusCodeAssocRejectEventPattern.matcher(eventData);
                    if (match1.find()) {
                        status = Integer.parseInt(match1.group(1));
                    } else if (DBG) {
                        Log.d(TAG, "Assoc Reject: Could not parse assoc reject string");
                    }
                }
                if (this.mWiFiCHRManager != null) {
                    this.mWiFiCHRManager.uploadAssocRejectException(status, BSSID);
                }
                sendMessage(iface, ASSOCIATION_REJECTION_EVENT, eventLogCounter, status, BSSID);
            } else if ((event != 12 || VDBG) && (event != 13 || VDBG)) {
                handleEvent(event, eventData, iface);
            }
            this.mRecvErrors = 0;
            eventLogCounter++;
            return false;
        }
        if (eventStr.startsWith(WPS_SUCCESS_STR)) {
            sendMessage(iface, (int) WPS_SUCCESS_EVENT);
        } else {
            if (eventStr.startsWith(WPS_FAIL_STR)) {
                handleWpsFailEvent(eventStr, iface);
            } else {
                if (eventStr.startsWith(WPS_OVERLAP_STR)) {
                    sendMessage(iface, (int) WPS_OVERLAP_EVENT);
                } else {
                    if (eventStr.startsWith(WPS_TIMEOUT_STR)) {
                        sendMessage(iface, (int) WPS_TIMEOUT_EVENT);
                    } else {
                        if (eventStr.startsWith(WPS_START_OKC_STR)) {
                            sendMessage(iface, (int) WPS_START_OKC_EVENT);
                        } else {
                            if (eventStr.startsWith(P2P_EVENT_PREFIX_STR)) {
                                handleP2pEvents(eventStr, iface);
                            } else {
                                if (eventStr.startsWith(HOST_AP_EVENT_PREFIX_STR)) {
                                    handleHostApEvents(eventStr, iface);
                                } else {
                                    if (eventStr.startsWith(ANQP_DONE_STR)) {
                                        try {
                                            handleAnqpResult(eventStr, iface);
                                        } catch (IllegalArgumentException iae) {
                                            Log.e(TAG, "Bad ANQP event string: '" + eventStr + "': " + iae);
                                        }
                                    } else {
                                        if (eventStr.startsWith(HS20_ICON_STR)) {
                                            try {
                                                handleIconResult(eventStr, iface);
                                            } catch (IllegalArgumentException iae2) {
                                                Log.e(TAG, "Bad Icon event string: '" + eventStr + "': " + iae2);
                                            }
                                        } else {
                                            if (eventStr.startsWith(HS20_SUB_REM_STR)) {
                                                handleWnmFrame(String.format("%012x %s", new Object[]{this.mLastConnectBSSIDs.get(iface), eventStr}), iface);
                                            } else {
                                                if (eventStr.startsWith(HS20_DEAUTH_STR)) {
                                                    handleWnmFrame(String.format("%012x %s", new Object[]{this.mLastConnectBSSIDs.get(iface), eventStr}), iface);
                                                } else {
                                                    if (eventStr.startsWith(REQUEST_PREFIX_STR)) {
                                                        handleRequests(eventStr, iface);
                                                    } else {
                                                        if (eventStr.startsWith(TARGET_BSSID_STR)) {
                                                            handleTargetBSSIDEvent(eventStr, iface);
                                                        } else {
                                                            if (eventStr.startsWith(ASSOCIATED_WITH_STR)) {
                                                                handleAssociatedBSSIDEvent(eventStr, iface);
                                                            } else {
                                                                if (eventStr.startsWith(AUTH_EVENT_PREFIX_STR)) {
                                                                    if (eventStr.endsWith(AUTH_TIMEOUT_STR)) {
                                                                        sendMessage(iface, (int) AUTHENTICATION_FAILURE_EVENT);
                                                                    }
                                                                }
                                                                if (eventStr.startsWith(VOWIFI_DETECT_IRQ_STR)) {
                                                                    Log.d(TAG, "dispatchEvent Vo WifiDetect event ");
                                                                    sendMessage(iface, (int) VOWIFI_DETECT_IRQ_STR_EVENT);
                                                                } else {
                                                                    if (eventStr.startsWith(ANT_CORE_ROB)) {
                                                                        Log.d(TAG, "dispatchEvent ANT_CORE_ROB ");
                                                                        sendMessage(iface, (int) EVENT_ANT_CORE_ROB);
                                                                    } else {
                                                                        if (this.mWiFiCHRManager != null) {
                                                                            this.mWiFiCHRManager.handleCHREvents(eventStr);
                                                                        }
                                                                        if (HWLOGW_E) {
                                                                            Log.w(TAG, "couldn't identify event type - " + eventStr);
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        eventLogCounter++;
        return false;
    }

    private void handleDriverEvent(String state, String iface) {
        if (state != null && state.equals("HANGED")) {
            sendMessage(iface, (int) DRIVER_HUNG_EVENT);
        }
    }

    private void handleEvent(int event, String remainder, String iface) {
        if (DBG) {
            Log.d(TAG, "handleEvent " + Integer.toString(event) + " " + remainder);
        }
        switch (event) {
            case 1:
                handleNetworkStateChange(DetailedState.CONNECTED, remainder, iface);
                return;
            case 2:
                if (this.mWiFiCHRManager != null) {
                    this.mWiFiCHRManager.uploadDisconnectException(remainder);
                }
                handleNetworkStateChange(DetailedState.DISCONNECTED, remainder, iface);
                return;
            case 4:
                sendMessage(iface, (int) SCAN_RESULTS_EVENT);
                return;
            case 14:
                if (DBG) {
                    Log.w(TAG, "handleEvent unknown: " + Integer.toString(event) + " " + remainder);
                    return;
                }
                return;
            case 15:
                sendMessage(iface, (int) SCAN_FAILED_EVENT);
                return;
            default:
                return;
        }
    }

    private void handleTargetBSSIDEvent(String eventStr, String iface) {
        Object BSSID = null;
        Matcher match = mTargetBSSIDPattern.matcher(eventStr);
        if (match.find()) {
            BSSID = match.group(1);
        }
        sendMessage(iface, 131213, eventLogCounter, 0, BSSID);
        if (this.mWiFiCHRManager != null) {
            this.mWiFiCHRManager.updateAPSsidByEvent(eventStr);
        }
    }

    private void handleAssociatedBSSIDEvent(String eventStr, String iface) {
        Object BSSID = null;
        Matcher match = mAssociatedPattern.matcher(eventStr);
        if (match.find()) {
            BSSID = match.group(1);
        }
        sendMessage(iface, 131219, eventLogCounter, 0, BSSID);
    }

    private void handleWpsFailEvent(String dataString, String iface) {
        Matcher match = Pattern.compile(WPS_FAIL_PATTERN).matcher(dataString);
        int reason = 0;
        if (match.find()) {
            String cfgErrStr = match.group(1);
            String reasonStr = match.group(2);
            if (reasonStr != null) {
                int reasonInt = Integer.parseInt(reasonStr);
                switch (reasonInt) {
                    case 1:
                        sendMessage(iface, (int) WPS_FAIL_EVENT, 5);
                        return;
                    case 2:
                        sendMessage(iface, (int) WPS_FAIL_EVENT, 4);
                        return;
                    default:
                        reason = reasonInt;
                        break;
                }
            }
            if (cfgErrStr != null) {
                int cfgErrInt = Integer.parseInt(cfgErrStr);
                switch (cfgErrInt) {
                    case 12:
                        sendMessage(iface, (int) WPS_FAIL_EVENT, 3);
                        return;
                    case 18:
                        sendMessage(iface, (int) WPS_FAIL_EVENT, 6);
                        return;
                    default:
                        if (reason == 0) {
                            reason = cfgErrInt;
                            break;
                        }
                        break;
                }
            }
        } else if (dataString.equals(WPS_FAIL_EAPOL_STR)) {
            logDbg("Send WPS_FAIL_EVENT for eapol fail.");
            sendMessage(iface, WPS_FAIL_EVENT, 0, -1);
            return;
        }
        sendMessage(iface, WPS_FAIL_EVENT, 0, reason);
    }

    private P2pStatus p2pError(String dataString) {
        P2pStatus err = P2pStatus.UNKNOWN;
        String[] tokens = dataString.split(" ");
        if (tokens.length < 2) {
            return err;
        }
        String[] nameValue = tokens[1].split("=");
        if (nameValue.length != 2) {
            return err;
        }
        if (nameValue[1].equals("FREQ_CONFLICT")) {
            return P2pStatus.NO_COMMON_CHANNEL;
        }
        try {
            err = P2pStatus.valueOf(Integer.parseInt(nameValue[1]));
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        return err;
    }

    private WifiP2pDevice getWifiP2pDevice(String dataString) {
        try {
            return new WifiP2pDevice(dataString);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private WifiP2pGroup getWifiP2pGroup(String dataString) {
        try {
            return new WifiP2pGroup(dataString);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void handleP2pEvents(String dataString, String iface) {
        Object device;
        if (dataString.startsWith(P2P_DEVICE_FOUND_STR)) {
            device = getWifiP2pDevice(dataString);
            if (device != null) {
                sendMessage(iface, (int) P2P_DEVICE_FOUND_EVENT, device);
            }
        } else if (dataString.startsWith(P2P_DEVICE_LOST_STR)) {
            device = getWifiP2pDevice(dataString);
            if (device != null) {
                sendMessage(iface, (int) P2P_DEVICE_LOST_EVENT, device);
            }
        } else if (dataString.startsWith(P2P_FIND_STOPPED_STR)) {
            sendMessage(iface, (int) P2P_FIND_STOPPED_EVENT);
        } else if (dataString.startsWith(P2P_GO_NEG_REQUEST_STR)) {
            sendMessage(iface, (int) P2P_GO_NEGOTIATION_REQUEST_EVENT, new WifiP2pConfig(dataString));
        } else if (dataString.startsWith(P2P_GO_NEG_SUCCESS_STR)) {
            sendMessage(iface, (int) P2P_GO_NEGOTIATION_SUCCESS_EVENT);
        } else if (dataString.startsWith(P2P_GO_NEG_FAILURE_STR)) {
            sendMessage(iface, (int) P2P_GO_NEGOTIATION_FAILURE_EVENT, p2pError(dataString));
        } else if (dataString.startsWith(P2P_GROUP_FORMATION_SUCCESS_STR)) {
            sendMessage(iface, (int) P2P_GROUP_FORMATION_SUCCESS_EVENT);
        } else if (dataString.startsWith(P2P_GROUP_FORMATION_FAILURE_STR)) {
            sendMessage(iface, (int) P2P_GROUP_FORMATION_FAILURE_EVENT, p2pError(dataString));
        } else if (dataString.startsWith(P2P_GROUP_STARTED_STR)) {
            group = getWifiP2pGroup(dataString);
            if (group != null) {
                sendMessage(iface, (int) P2P_GROUP_STARTED_EVENT, group);
            }
        } else if (dataString.startsWith(P2P_INTERFACE_CREATED_STR)) {
            String[] tokens = dataString.split(" ");
            if (tokens[1].startsWith("GO")) {
                sendMessage(iface, (int) P2P_GO_INTERFACE_CREATED_EVENT, tokens[2]);
            } else {
                sendMessage(iface, (int) P2P_GC_INTERFACE_CREATED_EVENT, tokens[2]);
            }
        } else if (dataString.startsWith(P2P_GROUP_REMOVED_STR)) {
            group = getWifiP2pGroup(dataString);
            if (group != null) {
                sendMessage(iface, (int) P2P_GROUP_REMOVED_EVENT, group);
            }
        } else if (dataString.startsWith(P2P_INVITATION_RECEIVED_STR)) {
            sendMessage(iface, (int) P2P_INVITATION_RECEIVED_EVENT, new WifiP2pGroup(dataString));
        } else if (dataString.startsWith(P2P_INVITATION_RESULT_STR)) {
            sendMessage(iface, (int) P2P_INVITATION_RESULT_EVENT, p2pError(dataString));
        } else if (dataString.startsWith(P2P_PROV_DISC_PBC_REQ_STR)) {
            sendMessage(iface, (int) P2P_PROV_DISC_PBC_REQ_EVENT, new WifiP2pProvDiscEvent(dataString));
        } else if (dataString.startsWith(P2P_PROV_DISC_PBC_RSP_STR)) {
            sendMessage(iface, (int) P2P_PROV_DISC_PBC_RSP_EVENT, new WifiP2pProvDiscEvent(dataString));
        } else if (dataString.startsWith(P2P_PROV_DISC_ENTER_PIN_STR)) {
            sendMessage(iface, (int) P2P_PROV_DISC_ENTER_PIN_EVENT, new WifiP2pProvDiscEvent(dataString));
        } else if (dataString.startsWith(P2P_PROV_DISC_SHOW_PIN_STR)) {
            sendMessage(iface, (int) P2P_PROV_DISC_SHOW_PIN_EVENT, new WifiP2pProvDiscEvent(dataString));
        } else if (dataString.startsWith(P2P_PROV_DISC_FAILURE_STR)) {
            sendMessage(iface, (int) P2P_PROV_DISC_FAILURE_EVENT);
        } else if (dataString.startsWith(P2P_SERV_DISC_RESP_STR)) {
            Object list = WifiP2pServiceResponse.newInstance(dataString);
            if (list != null) {
                sendMessage(iface, (int) P2P_SERV_DISC_RESP_EVENT, list);
            } else {
                Log.e(TAG, "Null service resp " + dataString);
            }
        } else if (dataString.startsWith(P2P_REMOVE_AND_REFORM_GROUP_STR)) {
            Log.d(TAG, "Received event= " + dataString);
        }
    }

    private void handleHostApEvents(String dataString, String iface) {
        String[] tokens = dataString.split(" ");
        if (tokens[0].equals(AP_STA_CONNECTED_STR)) {
            sendMessage(iface, (int) AP_STA_CONNECTED_EVENT, new WifiP2pDevice(dataString));
        } else if (tokens[0].equals(AP_STA_DISCONNECTED_STR)) {
            sendMessage(iface, (int) AP_STA_DISCONNECTED_EVENT, new WifiP2pDevice(dataString));
        }
    }

    private void handleAnqpResult(String eventStr, String iface) {
        int addrPos = eventStr.indexOf(ADDR_STRING);
        int resPos = eventStr.indexOf(RESULT_STRING);
        if (addrPos < 0 || resPos < 0) {
            throw new IllegalArgumentException("Unexpected ANQP result notification");
        }
        int eoaddr = eventStr.indexOf(32, ADDR_STRING.length() + addrPos);
        if (eoaddr < 0) {
            eoaddr = eventStr.length();
        }
        int eoresult = eventStr.indexOf(32, RESULT_STRING.length() + resPos);
        if (eoresult < 0) {
            eoresult = eventStr.length();
        }
        try {
            sendMessage(iface, ANQP_DONE_EVENT, eventStr.substring(RESULT_STRING.length() + resPos, eoresult).equalsIgnoreCase("success") ? 1 : 0, 0, Long.valueOf(Utils.parseMac(eventStr.substring(ADDR_STRING.length() + addrPos, eoaddr))));
        } catch (IllegalArgumentException iae) {
            Log.e(TAG, "Bad MAC address in ANQP response: " + iae.getMessage());
        }
    }

    private void handleIconResult(String eventStr, String iface) {
        String[] segments = eventStr.split(" ");
        if (segments.length != 4) {
            throw new IllegalArgumentException("Incorrect number of segments");
        }
        try {
            String bssid = segments[1];
            sendMessage(iface, (int) RX_HS20_ANQP_ICON_EVENT, new IconEvent(Utils.parseMac(bssid), segments[2], Integer.parseInt(segments[3])));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Bad numeral");
        }
    }

    private void handleWnmFrame(String eventStr, String iface) {
        try {
            sendMessage(iface, (int) HS20_REMEDIATION_EVENT, WnmData.buildWnmData(eventStr));
        } catch (IOException e) {
            Log.w(TAG, "Bad WNM event: '" + eventStr + "'");
        }
    }

    private void handleRequests(String dataString, String iface) {
        String SSID = null;
        int reason = -2;
        String requestName = dataString.substring(REQUEST_PREFIX_LEN_STR);
        if (!TextUtils.isEmpty(requestName)) {
            if (requestName.startsWith(IDENTITY_STR)) {
                Matcher match = mRequestIdentityPattern.matcher(requestName);
                if (match.find()) {
                    SSID = match.group(2);
                    try {
                        reason = Integer.parseInt(match.group(1));
                    } catch (NumberFormatException e) {
                        reason = -1;
                    }
                } else {
                    Log.e(TAG, "didn't find SSID " + requestName);
                }
                sendMessage(iface, SUP_REQUEST_IDENTITY, eventLogCounter, reason, SSID);
            } else if (requestName.startsWith("SIM")) {
                Matcher matchGsm = mRequestGsmAuthPattern.matcher(requestName);
                Matcher matchUmts = mRequestUmtsAuthPattern.matcher(requestName);
                Object data = new SimAuthRequestData();
                if (matchGsm.find()) {
                    data.networkId = Integer.parseInt(matchGsm.group(1));
                    data.protocol = 4;
                    data.ssid = matchGsm.group(4);
                    data.data = matchGsm.group(2).split(":");
                    sendMessage(iface, (int) SUP_REQUEST_SIM_AUTH, data);
                } else if (matchUmts.find()) {
                    data.networkId = Integer.parseInt(matchUmts.group(1));
                    data.protocol = 5;
                    data.ssid = matchUmts.group(4);
                    data.data = new String[2];
                    data.data[0] = matchUmts.group(2);
                    data.data[1] = matchUmts.group(3);
                    sendMessage(iface, (int) SUP_REQUEST_SIM_AUTH, data);
                } else {
                    Log.e(TAG, "couldn't parse SIM auth request - " + requestName);
                }
            } else if (HWLOGW_E) {
                Log.w(TAG, "couldn't identify request type - " + dataString);
            }
        }
    }

    private void handleSupplicantStateChange(String dataString, String iface) {
        WifiSsid wifiSsid = null;
        int index = dataString.lastIndexOf("SSID=");
        if (index != -1) {
            wifiSsid = WifiSsid.createFromAsciiEncoded(dataString.substring(index + 5));
        }
        String BSSID = null;
        int networkId = -1;
        int newState = -1;
        for (String split : dataString.split(" ")) {
            String[] nameValue = split.split("=");
            if (nameValue.length == 2) {
                if (nameValue[0].equals("BSSID")) {
                    BSSID = nameValue[1];
                } else {
                    try {
                        int value = Integer.parseInt(nameValue[1]);
                        if (nameValue[0].equals("id")) {
                            networkId = value;
                        } else if (nameValue[0].equals("state")) {
                            newState = value;
                        }
                    } catch (NumberFormatException e) {
                    }
                }
            }
        }
        if (newState != -1) {
            SupplicantState newSupplicantState = SupplicantState.INVALID;
            for (SupplicantState state : SupplicantState.values()) {
                if (state.ordinal() == newState) {
                    newSupplicantState = state;
                    break;
                }
            }
            if (newSupplicantState == SupplicantState.INVALID) {
                Log.w(TAG, "Invalid supplicant state: " + newState);
            }
            if (this.mWiFiCHRManager != null) {
                this.mWiFiCHRManager.updateAP(networkId, newSupplicantState, BSSID, newState, this.mWifiNative);
            }
            sendMessage(iface, SUPPLICANT_STATE_CHANGE_EVENT, eventLogCounter, 0, new StateChangeResult(networkId, wifiSsid, BSSID, newSupplicantState));
        }
    }

    private void handleNetworkStateChange(DetailedState newState, String data, String iface) {
        String str = null;
        int networkId = -1;
        int reason = 0;
        int local = 0;
        Matcher match;
        if (newState == DetailedState.CONNECTED) {
            Object BSSID;
            match = mConnectedEventPattern.matcher(data);
            if (match.find()) {
                BSSID = match.group(1);
                try {
                    networkId = Integer.parseInt(match.group(2));
                } catch (NumberFormatException e) {
                    networkId = -1;
                }
            } else if (DBG) {
                Log.d(TAG, "handleNetworkStateChange: Couldnt find BSSID in event string");
            }
            if (this.isWifiInterface) {
                this.time1 = System.currentTimeMillis();
            }
            sendMessage(iface, NETWORK_CONNECTION_EVENT, networkId, 0, BSSID);
        } else if (newState == DetailedState.DISCONNECTED) {
            match = mDisconnectedEventPattern.matcher(data);
            if (match.find()) {
                str = match.group(1);
                try {
                    reason = Integer.parseInt(match.group(2));
                } catch (NumberFormatException e2) {
                    reason = -1;
                }
                try {
                    local = Integer.parseInt(match.group(3));
                } catch (NumberFormatException e3) {
                    local = -1;
                }
            } else if (DBG) {
                Log.d(TAG, "handleNetworkStateChange: Could not parse disconnect string");
            }
            if (DBG) {
                Log.d(TAG, "WifiMonitor notify network disconnect: " + str + " reason=" + Integer.toString(reason));
            }
            if (this.isWifiInterface) {
                this.uploader.e(53, "{RT:" + reason + "}");
                if (-1 != this.time1) {
                    this.time2 = System.currentTimeMillis();
                    this.uploader.saveEachTime(this.time1, this.time2);
                    this.time1 = -1;
                    this.time2 = -1;
                }
            }
            sendMessage(iface, NETWORK_DISCONNECTION_EVENT, local, reason, str);
        }
    }
}
