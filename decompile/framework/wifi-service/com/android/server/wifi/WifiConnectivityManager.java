package com.android.server.wifi;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.AlarmManager.OnAlarmListener;
import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiScanner.ChannelSpec;
import android.net.wifi.WifiScanner.PnoSettings;
import android.net.wifi.WifiScanner.PnoSettings.PnoNetwork;
import android.net.wifi.WifiScanner.ScanData;
import android.net.wifi.WifiScanner.ScanListener;
import android.net.wifi.WifiScanner.ScanSettings;
import android.os.Handler;
import android.os.Looper;
import android.util.LocalLog;
import android.util.Log;
import com.android.server.wifi.anqp.Constants;
import com.android.server.wifi.util.ScanDetailUtil;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class WifiConnectivityManager extends AbsWifiConnectivityManager {
    private static final int CHANNEL_LIST_AGE_MS = 3600000;
    private static final int CONNECTED_PNO_SCAN_INTERVAL_MS = 160000;
    private static final long CONTINUOUS_SCAN_TIMEOUT = 50000;
    private static final int DISCONNECTED_PNO_SCAN_INTERVAL_MS = 20000;
    private static final boolean ENABLE_BACKGROUND_SCAN = false;
    private static final boolean ENABLE_CONNECTED_PNO_SCAN = false;
    public static final int HW_MIN_PERIODIC_SCAN_INTERVAL_MS = 10000;
    private static final int LOW_RSSI_NETWORK_RETRY_MAX_DELAY_MS = 80000;
    private static final int LOW_RSSI_NETWORK_RETRY_START_DELAY_MS = 20000;
    public static final int MAX_CONNECTION_ATTEMPTS_RATE = 6;
    public static final int MAX_CONNECTION_ATTEMPTS_TIME_INTERVAL_MS = 240000;
    public static final int MAX_PERIODIC_SCAN_INTERVAL_MS = 160000;
    private static final int MAX_SCAN_RESTART_ALLOWED = 5;
    public static final int PERIODIC_SCAN_INTERVAL_MS = 20000;
    public static final String PERIODIC_SCAN_TIMER_TAG = "WifiConnectivityManager Schedule Periodic Scan Timer";
    private static final long RESET_TIME_STAMP = Long.MIN_VALUE;
    public static final String RESTART_CONNECTIVITY_SCAN_TIMER_TAG = "WifiConnectivityManager Restart Scan";
    private static final int RESTART_SCAN_DELAY_MS = 2000;
    public static final String RESTART_SINGLE_SCAN_TIMER_TAG = "WifiConnectivityManager Restart Single Scan";
    public static final int SCAN_COUNT_CHANGE_REASON_ADD = 0;
    public static final int SCAN_COUNT_CHANGE_REASON_MINUS = 1;
    public static final int SCAN_COUNT_CHANGE_REASON_RESET = 2;
    private static final boolean SCAN_IMMEDIATELY = true;
    private static final boolean SCAN_ON_SCHEDULE = false;
    private static final String TAG = "WifiConnectivityManager";
    private static final int WATCHDOG_INTERVAL_MS = 1200000;
    public static final String WATCHDOG_TIMER_TAG = "WifiConnectivityManager Schedule Watchdog Timer";
    public static final int WIFI_STATE_CONNECTED = 1;
    public static final int WIFI_STATE_DISCONNECTED = 2;
    public static final int WIFI_STATE_TRANSITIONING = 3;
    public static final int WIFI_STATE_UNKNOWN = 0;
    private final AlarmManager mAlarmManager;
    private int mBand5GHzBonus;
    private final Clock mClock;
    private final WifiConfigManager mConfigManager;
    private final LinkedList<Long> mConnectionAttemptTimeStamps;
    private int mContinuousScanCounter;
    private long mContinuousScanStartedTime;
    private int mCurrentConnectionBonus;
    private boolean mDbg;
    private final Handler mEventHandler;
    private int mInitialScoreMax;
    private String mLastConnectionAttemptBssid;
    private long mLastPeriodicSingleScanTimeStamp;
    private final LocalLog mLocalLog;
    private int mMin24GHzRssi;
    private int mMin5GHzRssi;
    private final PeriodicScanListener mPeriodicScanListener;
    private final OnAlarmListener mPeriodicScanTimerListener;
    private int mPeriodicSingleScanInterval;
    private final PnoScanListener mPnoScanListener;
    private final WifiQualifiedNetworkSelector mQualifiedNetworkSelector;
    private final OnAlarmListener mRestartScanListener;
    private int mSameNetworkBonus;
    private int mScanRestartCount;
    private final WifiScanner mScanner;
    private boolean mScreenOn;
    private int mSecureBonus;
    private int mSingleScanRestartCount;
    private final WifiStateMachine mStateMachine;
    private int mTotalConnectivityAttemptsRateLimited;
    private boolean mUntrustedConnectionAllowed;
    private final OnAlarmListener mWatchdogListener;
    private boolean mWifiConnectivityManagerEnabled;
    private boolean mWifiEnabled;
    private final WifiInfo mWifiInfo;
    private final WifiLastResortWatchdog mWifiLastResortWatchdog;
    private final WifiMetrics mWifiMetrics;
    private int mWifiState;

    private class PeriodicScanListener implements ScanListener {
        private List<ScanDetail> mScanDetails;

        private PeriodicScanListener() {
            this.mScanDetails = new ArrayList();
        }

        public void clearScanDetails() {
            this.mScanDetails.clear();
        }

        public void onSuccess() {
            WifiConnectivityManager.this.localLog("PeriodicScanListener onSuccess");
        }

        public void onFailure(int reason, String description) {
            Log.e(WifiConnectivityManager.TAG, "PeriodicScanListener onFailure: reason: " + reason + " description: " + description);
            WifiConnectivityManager wifiConnectivityManager = WifiConnectivityManager.this;
            int -get4 = wifiConnectivityManager.mScanRestartCount;
            wifiConnectivityManager.mScanRestartCount = -get4 + 1;
            if (-get4 < 5) {
                WifiConnectivityManager.this.scheduleDelayedConnectivityScan(WifiConnectivityManager.RESTART_SCAN_DELAY_MS);
                return;
            }
            WifiConnectivityManager.this.mScanRestartCount = 0;
            WifiConnectivityManager.this.localLog("Failed to successfully start periodic scan for 5 times");
        }

        public void onPeriodChanged(int periodInMs) {
            WifiConnectivityManager.this.localLog("PeriodicScanListener onPeriodChanged: actual scan period " + periodInMs + "ms");
        }

        public void onResults(ScanData[] results) {
            WifiConnectivityManager.this.handleScanResults(this.mScanDetails, "PeriodicScanListener");
            clearScanDetails();
            WifiConnectivityManager.this.mScanRestartCount = 0;
        }

        public void onFullResult(ScanResult fullScanResult) {
            if (WifiConnectivityManager.this.mDbg) {
                WifiConnectivityManager.this.localLog("PeriodicScanListener onFullResult: " + fullScanResult.SSID + " capabilities " + fullScanResult.capabilities);
            }
            this.mScanDetails.add(ScanDetailUtil.toScanDetail(fullScanResult));
            WifiConnectivityManager.this.mScanRestartCount = 0;
        }
    }

    private class PnoScanListener implements android.net.wifi.WifiScanner.PnoScanListener {
        private int mLowRssiNetworkRetryDelay;
        private List<ScanDetail> mScanDetails;

        private PnoScanListener() {
            this.mScanDetails = new ArrayList();
            this.mLowRssiNetworkRetryDelay = WifiConnectivityManager.PERIODIC_SCAN_INTERVAL_MS;
        }

        public void clearScanDetails() {
            this.mScanDetails.clear();
        }

        public void resetLowRssiNetworkRetryDelay() {
            this.mLowRssiNetworkRetryDelay = WifiConnectivityManager.PERIODIC_SCAN_INTERVAL_MS;
        }

        public int getLowRssiNetworkRetryDelay() {
            return this.mLowRssiNetworkRetryDelay;
        }

        public void onSuccess() {
            WifiConnectivityManager.this.localLog("PnoScanListener onSuccess");
        }

        public void onFailure(int reason, String description) {
            Log.e(WifiConnectivityManager.TAG, "PnoScanListener onFailure: reason: " + reason + " description: " + description);
            WifiConnectivityManager wifiConnectivityManager = WifiConnectivityManager.this;
            int -get4 = wifiConnectivityManager.mScanRestartCount;
            wifiConnectivityManager.mScanRestartCount = -get4 + 1;
            if (-get4 < 5) {
                WifiConnectivityManager.this.scheduleDelayedConnectivityScan(WifiConnectivityManager.RESTART_SCAN_DELAY_MS);
                return;
            }
            WifiConnectivityManager.this.mScanRestartCount = 0;
            WifiConnectivityManager.this.localLog("Failed to successfully start PNO scan for 5 times");
        }

        public void onPeriodChanged(int periodInMs) {
            WifiConnectivityManager.this.localLog("PnoScanListener onPeriodChanged: actual scan period " + periodInMs + "ms");
        }

        public void onResults(ScanData[] results) {
        }

        public void onFullResult(ScanResult fullScanResult) {
        }

        public void onPnoNetworkFound(ScanResult[] results) {
            WifiConnectivityManager.this.localLog("PnoScanListener: onPnoNetworkFound: results len = " + results.length);
            for (ScanResult result : results) {
                this.mScanDetails.add(ScanDetailUtil.toScanDetail(result));
            }
            boolean wasConnectAttempted = WifiConnectivityManager.this.handleScanResults(this.mScanDetails, "PnoScanListener");
            clearScanDetails();
            if (wasConnectAttempted) {
                resetLowRssiNetworkRetryDelay();
            } else {
                if (this.mLowRssiNetworkRetryDelay > WifiConnectivityManager.LOW_RSSI_NETWORK_RETRY_MAX_DELAY_MS) {
                    this.mLowRssiNetworkRetryDelay = WifiConnectivityManager.LOW_RSSI_NETWORK_RETRY_MAX_DELAY_MS;
                }
                WifiConnectivityManager.this.scheduleDelayedConnectivityScan(this.mLowRssiNetworkRetryDelay);
                this.mLowRssiNetworkRetryDelay *= 2;
            }
            WifiConnectivityManager.this.mScanRestartCount = 0;
        }
    }

    private class RestartSingleScanListener implements OnAlarmListener {
        private final boolean mIsFullBandScan;
        private final boolean mIsWatchdogTriggered;

        RestartSingleScanListener(boolean isWatchdogTriggered, boolean isFullBandScan) {
            this.mIsWatchdogTriggered = isWatchdogTriggered;
            this.mIsFullBandScan = isFullBandScan;
        }

        public void onAlarm() {
            WifiConnectivityManager.this.startSingleScan(this.mIsWatchdogTriggered, this.mIsFullBandScan);
        }
    }

    private class SingleScanListener implements ScanListener {
        private final boolean mIsFullBandScan;
        private final boolean mIsWatchdogTriggered;
        private List<ScanDetail> mScanDetails = new ArrayList();
        private long startScanTime;

        SingleScanListener(boolean isWatchdogTriggered, boolean isFullBandScan) {
            this.mIsWatchdogTriggered = isWatchdogTriggered;
            this.mIsFullBandScan = isFullBandScan;
            this.startScanTime = System.currentTimeMillis();
        }

        public void clearScanDetails() {
            this.mScanDetails.clear();
        }

        public void onSuccess() {
            WifiConnectivityManager.this.localLog("SingleScanListener onSuccess");
            WifiConnectivityManager.this.handleSingleScanSuccess();
        }

        public void onFailure(int reason, String description) {
            Log.e(WifiConnectivityManager.TAG, "SingleScanListener onFailure: reason: " + reason + " description: " + description);
            WifiConnectivityManager wifiConnectivityManager = WifiConnectivityManager.this;
            int -get6 = wifiConnectivityManager.mSingleScanRestartCount;
            wifiConnectivityManager.mSingleScanRestartCount = -get6 + 1;
            if (-get6 < 5) {
                WifiConnectivityManager.this.scheduleDelayedSingleScan(this.mIsWatchdogTriggered, this.mIsFullBandScan);
                return;
            }
            WifiConnectivityManager.this.mSingleScanRestartCount = 0;
            WifiConnectivityManager.this.localLog("Failed to successfully start single scan for 5 times");
        }

        public void onPeriodChanged(int periodInMs) {
            WifiConnectivityManager.this.localLog("SingleScanListener onPeriodChanged: actual scan period " + periodInMs + "ms");
        }

        public void onResults(ScanData[] results) {
            WifiConnectivityManager.this.localLog("SingleScanListener onResults ***time = " + (System.currentTimeMillis() - this.startScanTime) + " ms");
            boolean wasConnectAttempted = WifiConnectivityManager.this.handleScanResults(this.mScanDetails, "SingleScanListener");
            clearScanDetails();
            if (this.mIsWatchdogTriggered) {
                if (wasConnectAttempted) {
                    if (WifiConnectivityManager.this.mScreenOn) {
                        WifiConnectivityManager.this.mWifiMetrics.incrementNumConnectivityWatchdogBackgroundBad();
                    } else {
                        WifiConnectivityManager.this.mWifiMetrics.incrementNumConnectivityWatchdogPnoBad();
                    }
                } else if (WifiConnectivityManager.this.mScreenOn) {
                    WifiConnectivityManager.this.mWifiMetrics.incrementNumConnectivityWatchdogBackgroundGood();
                } else {
                    WifiConnectivityManager.this.mWifiMetrics.incrementNumConnectivityWatchdogPnoGood();
                }
            }
            if (WifiConnectivityManager.this.mScreenOn && WifiConnectivityManager.this.mWifiState == 2 && wasConnectAttempted && WifiConnectivityManager.this.mStateMachine.isTargetNetworkEnabled() && WifiConnectivityManager.this.mContinuousScanCounter < 10 && WifiConnectivityManager.this.mClock.elapsedRealtime() - WifiConnectivityManager.this.mContinuousScanStartedTime < WifiConnectivityManager.CONTINUOUS_SCAN_TIMEOUT) {
                WifiConnectivityManager wifiConnectivityManager = WifiConnectivityManager.this;
                wifiConnectivityManager.mContinuousScanCounter = wifiConnectivityManager.mContinuousScanCounter + 1;
                Log.d(WifiConnectivityManager.TAG, "start a continuous scan " + WifiConnectivityManager.this.mContinuousScanCounter + " time(s).");
                WifiConnectivityManager.this.startSingleScan(false, true);
            }
            if (WifiConnectivityManager.this.mScreenOn && WifiConnectivityManager.this.mWifiState != 1 && !wasConnectAttempted && WifiConnectivityManager.this.isWifiScanSpecialChannels()) {
                Log.w(WifiConnectivityManager.TAG, "*******wifi scan special channels, but no connect ap ,  force fullband scan ****");
                if (this.mScanDetails.size() > 0) {
                    for (ScanDetail scan : this.mScanDetails) {
                        Log.d(WifiConnectivityManager.TAG, "**scan special channels result, ssid: " + scan.toString());
                    }
                }
                WifiConnectivityManager.this.handleScanCountChanged(0);
                WifiConnectivityManager.this.startSingleScan(false, true);
            }
            WifiConnectivityManager.this.mSingleScanRestartCount = 0;
        }

        public void onFullResult(ScanResult fullScanResult) {
            if (WifiConnectivityManager.this.mDbg) {
                WifiConnectivityManager.this.localLog("SingleScanListener onFullResult: " + fullScanResult.SSID + " capabilities " + fullScanResult.capabilities);
            }
            this.mScanDetails.add(ScanDetailUtil.toScanDetail(fullScanResult));
            WifiConnectivityManager.this.mSingleScanRestartCount = 0;
        }
    }

    private void localLog(String log) {
        this.mLocalLog.log(log);
    }

    public boolean handleScanResults(List<ScanDetail> scanDetails, String listenerName) {
        localLog(listenerName + " onResults: start QNS");
        WifiConfiguration wifiConfiguration = null;
        if (this.mStateMachine.attemptAutoConnect()) {
            wifiConfiguration = this.mQualifiedNetworkSelector.selectQualifiedNetwork(false, this.mUntrustedConnectionAllowed, scanDetails, this.mStateMachine.isLinkDebouncing(), this.mStateMachine.isConnected(), this.mStateMachine.isDisconnected(), this.mStateMachine.isSupplicantTransientState());
            this.mWifiLastResortWatchdog.updateAvailableNetworks(this.mQualifiedNetworkSelector.getFilteredScanDetails());
        }
        this.mQualifiedNetworkSelector.handleAutoJoinCompleted(wifiConfiguration);
        if (wifiConfiguration == null) {
            return false;
        }
        localLog(listenerName + ": QNS candidate-" + wifiConfiguration.SSID);
        connectToNetwork(wifiConfiguration);
        return true;
    }

    public void resetContinuousScanThreshold() {
        this.mContinuousScanStartedTime = this.mClock.elapsedRealtime();
        this.mContinuousScanCounter = 0;
    }

    public WifiConnectivityManager(Context context, WifiStateMachine stateMachine, WifiScanner scanner, WifiConfigManager configManager, WifiInfo wifiInfo, WifiQualifiedNetworkSelector qualifiedNetworkSelector, WifiInjector wifiInjector, Looper looper) {
        this.mLocalLog = new LocalLog(ActivityManager.isLowRamDeviceStatic() ? Constants.ANQP_QUERY_LIST : 1024);
        this.mDbg = false;
        this.mWifiEnabled = false;
        this.mWifiConnectivityManagerEnabled = true;
        this.mScreenOn = false;
        this.mWifiState = 0;
        this.mUntrustedConnectionAllowed = false;
        this.mScanRestartCount = 0;
        this.mSingleScanRestartCount = 0;
        this.mTotalConnectivityAttemptsRateLimited = 0;
        this.mLastConnectionAttemptBssid = null;
        this.mPeriodicSingleScanInterval = PERIODIC_SCAN_INTERVAL_MS;
        this.mLastPeriodicSingleScanTimeStamp = RESET_TIME_STAMP;
        this.mRestartScanListener = new OnAlarmListener() {
            public void onAlarm() {
                WifiConnectivityManager.this.startConnectivityScan(true, true);
            }
        };
        this.mWatchdogListener = new OnAlarmListener() {
            public void onAlarm() {
                WifiConnectivityManager.this.watchdogHandler();
            }
        };
        this.mPeriodicScanTimerListener = new OnAlarmListener() {
            public void onAlarm() {
                WifiConnectivityManager.this.periodicScanTimerHandler();
            }
        };
        this.mContinuousScanCounter = 0;
        this.mContinuousScanStartedTime = RESET_TIME_STAMP;
        this.mPeriodicScanListener = new PeriodicScanListener();
        this.mPnoScanListener = new PnoScanListener();
        this.mStateMachine = stateMachine;
        this.mScanner = scanner;
        this.mConfigManager = configManager;
        this.mWifiInfo = wifiInfo;
        this.mQualifiedNetworkSelector = qualifiedNetworkSelector;
        this.mWifiLastResortWatchdog = wifiInjector.getWifiLastResortWatchdog();
        this.mWifiMetrics = wifiInjector.getWifiMetrics();
        this.mAlarmManager = (AlarmManager) context.getSystemService("alarm");
        this.mEventHandler = new Handler(looper);
        this.mClock = wifiInjector.getClock();
        this.mConnectionAttemptTimeStamps = new LinkedList();
        this.mMin5GHzRssi = -82;
        this.mMin24GHzRssi = -85;
        this.mBand5GHzBonus = 40;
        this.mCurrentConnectionBonus = this.mConfigManager.mCurrentNetworkBoost.get();
        this.mSameNetworkBonus = context.getResources().getInteger(17694746);
        this.mSecureBonus = context.getResources().getInteger(17694749);
        this.mInitialScoreMax = (this.mConfigManager.mThresholdSaturatedRssi24.get() + 85) * 4;
        Log.i(TAG, "PNO settings: min5GHzRssi " + this.mMin5GHzRssi + " min24GHzRssi " + this.mMin24GHzRssi + " currentConnectionBonus " + this.mCurrentConnectionBonus + " sameNetworkBonus " + this.mSameNetworkBonus + " secureNetworkBonus " + this.mSecureBonus + " initialScoreMax " + this.mInitialScoreMax);
        Log.i(TAG, "ConnectivityScanManager initialized ");
    }

    private boolean shouldSkipConnectionAttempt(Long timeMillis) {
        Iterator<Long> attemptIter = this.mConnectionAttemptTimeStamps.iterator();
        while (attemptIter.hasNext()) {
            if (timeMillis.longValue() - ((Long) attemptIter.next()).longValue() <= 240000) {
                break;
            }
            attemptIter.remove();
        }
        return this.mConnectionAttemptTimeStamps.size() >= 6;
    }

    private void noteConnectionAttempt(Long timeMillis) {
        this.mConnectionAttemptTimeStamps.addLast(timeMillis);
    }

    private void clearConnectionAttemptTimeStamps() {
        this.mConnectionAttemptTimeStamps.clear();
    }

    private void connectToNetwork(WifiConfiguration candidate) {
        ScanResult scanResultCandidate = candidate.getNetworkSelectionStatus().getCandidate();
        if (scanResultCandidate == null) {
            Log.e(TAG, "connectToNetwork: bad candidate - " + candidate + " scanResult: " + scanResultCandidate);
            return;
        }
        String targetBssid = scanResultCandidate.BSSID;
        String targetAssociationId = candidate.SSID + " : " + targetBssid;
        if (targetBssid == null || !((targetBssid.equals(this.mLastConnectionAttemptBssid) || targetBssid.equals(this.mWifiInfo.getBSSID())) && SupplicantState.isConnecting(this.mWifiInfo.getSupplicantState()))) {
            Long elapsedTimeMillis = Long.valueOf(this.mClock.elapsedRealtime());
            if (this.mScreenOn || !shouldSkipConnectionAttempt(elapsedTimeMillis)) {
                String currentAssociationId;
                noteConnectionAttempt(elapsedTimeMillis);
                this.mLastConnectionAttemptBssid = targetBssid;
                WifiConfiguration currentConnectedNetwork = this.mConfigManager.getWifiConfiguration(this.mWifiInfo.getNetworkId());
                if (currentConnectedNetwork == null) {
                    currentAssociationId = "Disconnected";
                } else {
                    currentAssociationId = this.mWifiInfo.getSSID() + " : " + this.mWifiInfo.getBSSID();
                }
                this.mStateMachine.setCHRConnectingSartTimestamp(elapsedTimeMillis.longValue());
                if (currentConnectedNetwork == null || !(currentConnectedNetwork.networkId == candidate.networkId || currentConnectedNetwork.isLinked(candidate))) {
                    localLog("connectToNetwork: Reconnect from " + currentAssociationId + " to " + targetAssociationId);
                    this.mStateMachine.autoConnectToNetwork(candidate.networkId, scanResultCandidate.BSSID);
                } else {
                    localLog("connectToNetwork: Roaming from " + currentAssociationId + " to " + targetAssociationId);
                    this.mStateMachine.autoRoamToNetwork(candidate.networkId, scanResultCandidate);
                }
                return;
            }
            localLog("connectToNetwork: Too many connection attempts. Skipping this attempt!");
            this.mTotalConnectivityAttemptsRateLimited++;
            return;
        }
        localLog("connectToNetwork: Either already connected or is connecting to " + targetAssociationId);
    }

    private int getScanBand() {
        return getScanBand(true);
    }

    private int getScanBand(boolean isFullBandScan) {
        if (!isFullBandScan) {
            return 0;
        }
        int freqBand = this.mStateMachine.getFrequencyBand();
        if (freqBand == 1) {
            return 6;
        }
        if (freqBand == 2) {
            return 1;
        }
        return 7;
    }

    private boolean setScanChannels(ScanSettings settings) {
        WifiConfiguration config = this.mStateMachine.getCurrentWifiConfiguration();
        if (config == null) {
            return false;
        }
        HashSet<Integer> freqs = this.mConfigManager.makeChannelList(config, CHANNEL_LIST_AGE_MS);
        if (freqs == null || freqs.size() == 0) {
            localLog("No scan channels for " + config.configKey() + ". Perform full band scan");
            return false;
        }
        int index = 0;
        settings.channels = new ChannelSpec[freqs.size()];
        for (Integer freq : freqs) {
            int index2 = index + 1;
            settings.channels[index] = new ChannelSpec(freq.intValue());
            index = index2;
        }
        return true;
    }

    private void watchdogHandler() {
        localLog("watchdogHandler");
        if (this.mWifiState == 2) {
            Log.i(TAG, "start a single scan from watchdogHandler");
            scheduleWatchdogTimer();
            startSingleScan(true, true);
        }
    }

    private void startPeriodicSingleScan() {
        long currentTimeStamp = this.mClock.elapsedRealtime();
        localLog("****start Periodic SingleScan,mPeriodicSingleScanInterval : " + (this.mPeriodicSingleScanInterval / 1000) + " s");
        if (!(this.mLastPeriodicSingleScanTimeStamp == RESET_TIME_STAMP || handleForceScan())) {
            long msSinceLastScan = currentTimeStamp - this.mLastPeriodicSingleScanTimeStamp;
            int mPeriodicScanInterval = PERIODIC_SCAN_INTERVAL_MS;
            if (isSupportWifiScanGenie()) {
                mPeriodicScanInterval = HW_MIN_PERIODIC_SCAN_INTERVAL_MS;
            }
            if (msSinceLastScan < ((long) mPeriodicScanInterval)) {
                localLog("Last periodic single scan started " + msSinceLastScan + "ms ago, defer this new scan request.");
                schedulePeriodicScanTimer(mPeriodicScanInterval - ((int) msSinceLastScan));
                return;
            }
        }
        boolean isFullBandScan = true;
        if (this.mWifiState == 1 && (this.mWifiInfo.txSuccessRate > 8.0d || this.mWifiInfo.rxSuccessRate > 16.0d)) {
            localLog("No full band scan due to heavy traffic, txSuccessRate=" + this.mWifiInfo.txSuccessRate + " rxSuccessRate=" + this.mWifiInfo.rxSuccessRate);
            isFullBandScan = false;
        }
        this.mLastPeriodicSingleScanTimeStamp = currentTimeStamp;
        handleScanCountChanged(0);
        startSingleScan(false, isFullBandScan);
        if (!isSupportWifiScanGenie() || this.mWifiState == 1) {
            localLog("****isSupportWifiScanGenie :  fasle: ");
            schedulePeriodicScanTimer(this.mPeriodicSingleScanInterval);
            this.mPeriodicSingleScanInterval *= 2;
            if (this.mPeriodicSingleScanInterval > 160000) {
                this.mPeriodicSingleScanInterval = 160000;
            }
        } else {
            this.mPeriodicSingleScanInterval = getPeriodicSingleScanInterval();
            localLog("****isSupportWifiScanGenie :  ScanInterval: " + this.mPeriodicSingleScanInterval);
            schedulePeriodicScanTimer(this.mPeriodicSingleScanInterval);
        }
    }

    private void resetLastPeriodicSingleScanTimeStamp() {
        this.mLastPeriodicSingleScanTimeStamp = RESET_TIME_STAMP;
    }

    private void periodicScanTimerHandler() {
        Log.i(TAG, "periodicScanTimerHandler mScreenOn " + this.mScreenOn);
        localLog("periodicScanTimerHandler");
        if (this.mScreenOn) {
            startPeriodicSingleScan();
        }
    }

    private void startSingleScan(boolean isWatchdogTriggered, boolean isFullBandScan) {
        if (this.mWifiEnabled && this.mWifiConnectivityManagerEnabled) {
            int i;
            this.mPnoScanListener.resetLowRssiNetworkRetryDelay();
            ScanSettings scanSettings = null;
            if (!isWifiScanSpecialChannels() || this.mWifiState == 1) {
                Log.e(TAG, "isWifiScanSpecialChannels is false,mWifiState : " + this.mWifiState);
            } else {
                scanSettings = getScanGenieSettings();
                localLog("****isWifiScanSpecialChannels *settings =**:" + scanSettings);
            }
            if (scanSettings == null) {
                scanSettings = new ScanSettings();
                if (!(isFullBandScan || setScanChannels(scanSettings))) {
                    isFullBandScan = true;
                }
                scanSettings.band = getScanBand(isFullBandScan);
                scanSettings.reportEvents = 3;
                scanSettings.numBssidsPerScan = 0;
                Set<Integer> hiddenNetworkIds = this.mConfigManager.getHiddenConfiguredNetworkIds();
                if (hiddenNetworkIds != null && hiddenNetworkIds.size() > 0) {
                    i = 0;
                    scanSettings.hiddenNetworkIds = new int[hiddenNetworkIds.size()];
                    for (Integer netId : hiddenNetworkIds) {
                        int i2 = i + 1;
                        scanSettings.hiddenNetworkIds[i] = netId.intValue();
                        i = i2;
                    }
                }
            }
            SingleScanListener singleScanListener = new SingleScanListener(isWatchdogTriggered, isFullBandScan);
            if (scanSettings.channels != null) {
                for (i = 0; i < scanSettings.channels.length; i++) {
                    localLog("settings  channels frequency: " + scanSettings.channels[i].frequency + ", dwellTimeMS: " + scanSettings.channels[i].dwellTimeMS + ", passive :" + scanSettings.channels[i].passive);
                }
            }
            this.mScanner.startScan(scanSettings, singleScanListener, WifiStateMachine.WIFI_WORK_SOURCE);
        }
    }

    private void startPeriodicScan(boolean scanImmediately) {
        this.mPnoScanListener.resetLowRssiNetworkRetryDelay();
        if (scanImmediately) {
            resetLastPeriodicSingleScanTimeStamp();
        }
        this.mPeriodicSingleScanInterval = PERIODIC_SCAN_INTERVAL_MS;
        handleScanCountChanged(2);
        startPeriodicSingleScan();
    }

    private void startDisconnectedPnoScan() {
        PnoSettings pnoSettings = new PnoSettings();
        ArrayList<PnoNetwork> pnoNetworkList = this.mConfigManager.retrieveDisconnectedPnoNetworkList();
        int listSize = pnoNetworkList.size();
        if (listSize == 0) {
            localLog("No saved network for starting disconnected PNO.");
            return;
        }
        pnoSettings.networkList = new PnoNetwork[listSize];
        pnoSettings.networkList = (PnoNetwork[]) pnoNetworkList.toArray(pnoSettings.networkList);
        pnoSettings.min5GHzRssi = this.mMin5GHzRssi;
        pnoSettings.min24GHzRssi = this.mMin24GHzRssi;
        pnoSettings.initialScoreMax = this.mInitialScoreMax;
        pnoSettings.currentConnectionBonus = this.mCurrentConnectionBonus;
        pnoSettings.sameNetworkBonus = this.mSameNetworkBonus;
        pnoSettings.secureBonus = this.mSecureBonus;
        pnoSettings.band5GHzBonus = this.mBand5GHzBonus;
        ScanSettings scanSettings = new ScanSettings();
        scanSettings.band = getScanBand();
        scanSettings.reportEvents = 4;
        scanSettings.numBssidsPerScan = 0;
        scanSettings.periodInMs = PERIODIC_SCAN_INTERVAL_MS;
        this.mPnoScanListener.clearScanDetails();
        this.mScanner.startDisconnectedPnoScan(scanSettings, pnoSettings, this.mPnoScanListener);
    }

    private void startConnectedPnoScan() {
    }

    private void scheduleWatchdogTimer() {
        Log.i(TAG, "scheduleWatchdogTimer");
        this.mAlarmManager.set(2, this.mClock.elapsedRealtime() + 1200000, WATCHDOG_TIMER_TAG, this.mWatchdogListener, this.mEventHandler);
    }

    private void schedulePeriodicScanTimer(int intervalMs) {
        this.mAlarmManager.setExact(2, this.mClock.elapsedRealtime() + ((long) intervalMs), PERIODIC_SCAN_TIMER_TAG, this.mPeriodicScanTimerListener, this.mEventHandler);
    }

    private void scheduleDelayedSingleScan(boolean isWatchdogTriggered, boolean isFullBandScan) {
        localLog("scheduleDelayedSingleScan");
        this.mAlarmManager.set(2, this.mClock.elapsedRealtime() + 2000, RESTART_SINGLE_SCAN_TIMER_TAG, new RestartSingleScanListener(isWatchdogTriggered, isFullBandScan), this.mEventHandler);
    }

    private void scheduleDelayedConnectivityScan(int msFromNow) {
        localLog("scheduleDelayedConnectivityScan");
        this.mAlarmManager.set(2, this.mClock.elapsedRealtime() + ((long) msFromNow), RESTART_CONNECTIVITY_SCAN_TIMER_TAG, this.mRestartScanListener, this.mEventHandler);
    }

    private void startConnectivityScan(boolean scanImmediately, boolean isRestartScan) {
        localLog("startConnectivityScan: screenOn=" + this.mScreenOn + " wifiState=" + this.mWifiState + " scanImmediately=" + scanImmediately + " wifiEnabled=" + this.mWifiEnabled + " wifiConnectivityManagerEnabled=" + this.mWifiConnectivityManagerEnabled);
        if (this.mWifiEnabled && this.mWifiConnectivityManagerEnabled) {
            stopConnectivityScan(isRestartScan);
            if (this.mWifiState == 1 || this.mWifiState == 2) {
                if (this.mScreenOn) {
                    startPeriodicScan(scanImmediately);
                } else if (this.mWifiState == 1) {
                    startConnectedPnoScan();
                } else {
                    startDisconnectedPnoScan();
                }
            }
        }
    }

    private void stopConnectivityScan(boolean isRestartScan) {
        this.mAlarmManager.cancel(this.mPeriodicScanTimerListener);
        this.mScanner.stopPnoScan(this.mPnoScanListener);
        if (!isRestartScan) {
            this.mScanRestartCount = 0;
        }
    }

    public void handleScreenStateChanged(boolean screenOn) {
        localLog("handleScreenStateChanged: screenOn=" + screenOn);
        this.mScreenOn = screenOn;
        startConnectivityScan(false, false);
    }

    public void handleConnectionStateChanged(int state) {
        localLog("handleConnectionStateChanged: state=" + state);
        this.mWifiState = state;
        if (this.mWifiState == 2) {
            scheduleWatchdogTimer();
        }
        handleScanCountChanged(2);
        startConnectivityScan(false, false);
    }

    public void setUntrustedConnectionAllowed(boolean allowed) {
        Log.i(TAG, "setUntrustedConnectionAllowed: allowed=" + allowed);
        if (this.mUntrustedConnectionAllowed != allowed) {
            this.mUntrustedConnectionAllowed = allowed;
            startConnectivityScan(true, false);
        }
    }

    public void connectToUserSelectNetwork(int netId, boolean persistent) {
        Log.i(TAG, "connectToUserSelectNetwork: netId=" + netId + " persist=" + persistent);
        this.mQualifiedNetworkSelector.userSelectNetwork(netId, persistent);
        clearConnectionAttemptTimeStamps();
    }

    public void forceConnectivityScan() {
        Log.i(TAG, "forceConnectivityScan");
        startConnectivityScan(true, false);
    }

    public boolean trackBssid(String bssid, boolean enable) {
        Log.i(TAG, "trackBssid: " + (enable ? "enable " : "disable ") + bssid);
        boolean ret = this.mQualifiedNetworkSelector.enableBssidForQualityNetworkSelection(bssid, enable);
        if (ret && !enable) {
            startConnectivityScan(true, false);
        }
        return ret;
    }

    public void setUserPreferredBand(int band) {
        Log.i(TAG, "User band preference: " + band);
        this.mQualifiedNetworkSelector.setUserPreferredBand(band);
        startConnectivityScan(true, false);
    }

    public void setWifiEnabled(boolean enable) {
        Log.i(TAG, "Set WiFi " + (enable ? "enabled" : "disabled"));
        this.mWifiEnabled = enable;
        if (!this.mWifiEnabled) {
            stopConnectivityScan(false);
            resetLastPeriodicSingleScanTimeStamp();
        }
    }

    public void enable(boolean enable) {
        Log.i(TAG, "Set WiFiConnectivityManager " + (enable ? "enabled" : "disabled"));
        this.mWifiConnectivityManagerEnabled = enable;
        if (!this.mWifiConnectivityManagerEnabled) {
            stopConnectivityScan(false);
            resetLastPeriodicSingleScanTimeStamp();
        }
    }

    public void enableVerboseLogging(int verbose) {
        boolean z = false;
        if (verbose > 0) {
            z = true;
        }
        this.mDbg = z;
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("Dump of WifiConnectivityManager");
        pw.println("WifiConnectivityManager - Log Begin ----");
        pw.println("WifiConnectivityManager - Number of connectivity attempts rate limited: " + this.mTotalConnectivityAttemptsRateLimited);
        this.mLocalLog.dump(fd, pw, args);
        pw.println("WifiConnectivityManager - Log End ----");
    }

    int getLowRssiNetworkRetryDelay() {
        return this.mPnoScanListener.getLowRssiNetworkRetryDelay();
    }

    long getLastPeriodicSingleScanTimeStamp() {
        return this.mLastPeriodicSingleScanTimeStamp;
    }
}
