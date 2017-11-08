package com.android.server.wifi.wifipro;

import android.os.Bundle;

public interface INetworkQosCallBack {
    void onNetworkDetectionResult(int i, int i2);

    void onNetworkQosChange(int i, int i2);

    void onNotifyWifiSecurityStatus(Bundle bundle);

    void onWifiBqeDetectionResult(int i);

    void onWifiBqeReturnCurrentRssi(int i);

    void onWifiBqeReturnHistoryScore(WifiProEstimateApInfo wifiProEstimateApInfo);

    void onWifiBqeReturnRssiTH(WifiProEstimateApInfo wifiProEstimateApInfo);
}
