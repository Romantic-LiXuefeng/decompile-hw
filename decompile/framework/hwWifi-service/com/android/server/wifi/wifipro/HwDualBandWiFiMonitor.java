package com.android.server.wifi.wifipro;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.NetworkInfo.State;
import android.net.wifi.WifiConfiguration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class HwDualBandWiFiMonitor {
    private IntentFilter intentFilter = new IntentFilter();
    private boolean isRegister = false;
    private BroadcastReceiver mBroadcastReceiver = new WifiBroadcastReceiver();
    private Context mContext;
    private Handler mHandler;

    private class WifiBroadcastReceiver extends BroadcastReceiver {
        private WifiBroadcastReceiver() {
        }

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("android.net.wifi.WIFI_STATE_CHANGED".equals(action)) {
                switch (intent.getIntExtra("wifi_state", 4)) {
                    case 1:
                        Log.e(HwDualBandMessageUtil.TAG, "WIFI_STATE_DISABLED");
                        HwDualBandWiFiMonitor.this.mHandler.sendEmptyMessage(4);
                        return;
                    case 3:
                        Log.e(HwDualBandMessageUtil.TAG, "WIFI_STATE_ENABLED");
                        HwDualBandWiFiMonitor.this.mHandler.sendEmptyMessage(3);
                        return;
                    default:
                        return;
                }
            } else if ("android.net.wifi.STATE_CHANGE".equals(action)) {
                NetworkInfo netInfo = (NetworkInfo) intent.getParcelableExtra("networkInfo");
                if (netInfo == null) {
                    return;
                }
                if (netInfo.getState() == State.DISCONNECTED) {
                    HwDualBandWiFiMonitor.this.mHandler.sendEmptyMessage(2);
                } else if (netInfo.getState() == State.CONNECTED) {
                    Log.e(HwDualBandMessageUtil.TAG, "NetworkInfo.State.CONNECTED");
                    HwDualBandWiFiMonitor.this.mHandler.sendEmptyMessage(1);
                } else if (netInfo.getDetailedState() == DetailedState.VERIFYING_POOR_LINK) {
                    Log.e(HwDualBandMessageUtil.TAG, "NetworkInfo.DetailedState.VERIFYING_POOR_LINK");
                    HwDualBandWiFiMonitor.this.mHandler.sendEmptyMessage(19);
                }
            } else if ("android.net.wifi.SCAN_RESULTS".equals(action)) {
                HwDualBandWiFiMonitor.this.mHandler.sendEmptyMessage(7);
            } else if ("android.net.wifi.CONFIGURED_NETWORKS_CHANGE".equals(action)) {
                int reason = intent.getIntExtra("changeReason", 4);
                Log.e(HwDualBandMessageUtil.TAG, "CONFIGURED_NETWORKS_CHANGED_ACTION reason = " + reason);
                if (reason == 1) {
                    WifiConfiguration netInfo2 = (WifiConfiguration) intent.getParcelableExtra("wifiConfiguration");
                    Log.e(HwDualBandMessageUtil.TAG, "CONFIGURED_NETWORKS_CHANGED_ACTION");
                    if (HwDualBandWiFiMonitor.this.isValid(netInfo2) && !netInfo2.isTempCreated) {
                        Log.e(HwDualBandMessageUtil.TAG, "CHANGE_REASON_REMOVED");
                        Bundle data = new Bundle();
                        data.putString("bssid", netInfo2.BSSID);
                        data.putString("ssid", netInfo2.SSID);
                        data.putInt(HwDualBandMessageUtil.MSG_KEY_AUTHTYPE, netInfo2.getAuthType());
                        Message msg = new Message();
                        msg.what = 8;
                        msg.setData(data);
                        HwDualBandWiFiMonitor.this.mHandler.sendMessage(msg);
                    }
                }
            } else if ("huawei.conn.NETWORK_CONDITIONS_MEASURED".equals(action)) {
                Log.e(HwDualBandMessageUtil.TAG, "ACTION_NETWORK_CONDITIONS_MEASURED");
                switch (intent.getIntExtra("extra_is_internet_ready", -1)) {
                    case -1:
                        Log.e(HwDualBandMessageUtil.TAG, "INTERNET_CHECK_RESULT_NO_INTERNET");
                        HwDualBandWiFiMonitor.this.mHandler.sendEmptyMessage(12);
                        return;
                    case 5:
                        HwDualBandWiFiMonitor.this.mHandler.sendEmptyMessage(11);
                        return;
                    case 6:
                        HwDualBandWiFiMonitor.this.mHandler.sendEmptyMessage(13);
                        return;
                    default:
                        return;
                }
            } else if ("android.intent.action.SCREEN_ON".equals(action)) {
                HwDualBandWiFiMonitor.this.mHandler.sendEmptyMessage(14);
            } else if ("android.intent.action.SCREEN_OFF".equals(action)) {
                HwDualBandWiFiMonitor.this.mHandler.sendEmptyMessage(15);
            }
        }
    }

    public HwDualBandWiFiMonitor(Context context, Handler handler) {
        this.mContext = context;
        this.mHandler = handler;
    }

    public void startMonitor() {
        registerBroadcastReceiver();
    }

    public void stopMonitor() {
        unRegisterBroadcastReceiver();
    }

    private void registerBroadcastReceiver() {
        if (!this.isRegister) {
            this.intentFilter.addAction("android.net.wifi.CONFIGURED_NETWORKS_CHANGE");
            this.intentFilter.addAction("huawei.conn.NETWORK_CONDITIONS_MEASURED");
            this.intentFilter.addAction("android.net.wifi.SCAN_RESULTS");
            this.intentFilter.addAction("android.net.wifi.WIFI_STATE_CHANGED");
            this.intentFilter.addAction("android.net.wifi.STATE_CHANGE");
            this.intentFilter.addAction("android.intent.action.SCREEN_ON");
            this.intentFilter.addAction("android.intent.action.SCREEN_OFF");
            this.mContext.registerReceiver(this.mBroadcastReceiver, this.intentFilter);
            this.isRegister = true;
        }
    }

    private void unRegisterBroadcastReceiver() {
        if (this.isRegister) {
            this.mContext.unregisterReceiver(this.mBroadcastReceiver);
            this.isRegister = false;
        }
    }

    private boolean isValid(WifiConfiguration config) {
        boolean z = true;
        if (config == null) {
            return false;
        }
        int cc = config.allowedKeyManagement.cardinality();
        Log.e(HwDualBandMessageUtil.TAG, "config isValid cardinality=" + cc);
        if (cc > 1) {
            z = false;
        }
        return z;
    }
}
