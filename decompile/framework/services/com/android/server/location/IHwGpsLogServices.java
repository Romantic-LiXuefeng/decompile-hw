package com.android.server.location;

import android.location.Location;
import android.location.LocationRequest;
import android.net.NetworkInfo;
import com.android.internal.location.ProviderRequest;

public interface IHwGpsLogServices {
    void addBatchingStatus();

    void addGeofenceStatus();

    void initGps(boolean z, byte b);

    void netWorkLocation(String str, ProviderRequest providerRequest);

    void openGpsSwitchFail(int i);

    void permissionErr();

    void processGnssHalDriverEvent(String str);

    void startGps(boolean z, int i);

    void stopGps(boolean z);

    void updateAgpsState(int i, int i2);

    void updateApkName(LocationRequest locationRequest, String str);

    void updateGpsRunState(int i);

    void updateLocation(Location location, long j, String str);

    void updateNetworkState(NetworkInfo networkInfo);

    void updateNtpDloadStatus(boolean z);

    void updateNtpServerInfo(String str);

    void updateSetPosMode(boolean z);

    void updateSvStatus(int i, int[] iArr, float[] fArr, float[] fArr2, float[] fArr3);

    void updateXtraDloadStatus(boolean z);
}
