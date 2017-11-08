package android.net.wifi;

import android.content.Context;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.provider.SettingsEx.Systemex;
import android.text.TextUtils;
import android.util.Log;
import com.huawei.connectivitylog.ConnectivityLogManager;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class HwInnerWifiManagerImpl implements HwInnerWifiManager {
    private static final int CODE_ENABLE_HILINK_HANDSHAKE = 2001;
    private static final int CODE_GET_APLINKED_STA_LIST = 1005;
    private static final int CODE_GET_PPPOE_INFO_CONFIG = 1004;
    private static final int CODE_GET_SINGNAL_INFO = 1011;
    private static final int CODE_GET_SOFTAP_CHANNEL_LIST = 1009;
    private static final int CODE_GET_VOWIFI_DETECT_MODE = 1013;
    private static final int CODE_GET_VOWIFI_DETECT_PERIOD = 1015;
    private static final int CODE_GET_WPA_SUPP_CONFIG = 1001;
    private static final int CODE_IS_SUPPORT_VOWIFI_DETECT = 1016;
    private static final int CODE_SET_SOFTAP_DISASSOCIATESTA = 1007;
    private static final int CODE_SET_SOFTAP_MACFILTER = 1006;
    private static final int CODE_SET_VOWIFI_DETECT_MODE = 1012;
    private static final int CODE_SET_VOWIFI_DETECT_PERIOD = 1014;
    private static final int CODE_SET_WIFI_AP_EVALUATE_ENABLED = 1010;
    private static final int CODE_START_PPPOE_CONFIG = 1002;
    private static final int CODE_STOP_PPPOE_CONFIG = 1003;
    private static final int CODE_USER_HANDOVER_WIFI = 1008;
    private static final boolean DBG = false;
    private static final String DESCRIPTOR = "android.net.wifi.IWifiManager";
    public static final int EAP_AKA = 5;
    public static final int EAP_AKAPrim = 6;
    private static final String EAP_KEY = "eap";
    public static final int EAP_SIM = 4;
    private static final String PCSC_KEY = "pcsc";
    private static final String TAG = "HwInnerWifiManagerImpl";
    private static HwInnerWifiManager mInstance = new HwInnerWifiManagerImpl();
    private String wifi_level_threshold = SystemProperties.get("ro.config.hw_wifi_signal_level");

    public static HwInnerWifiManager getDefault() {
        return mInstance;
    }

    public String getAppendSsidWithRandomUuid(WifiConfiguration config, Context context) {
        if (1 == Systemex.getInt(context.getContentResolver(), "hw_softap_ssid_extend", 0)) {
            config.SSID += UUID.randomUUID().toString().substring(13, 18);
            Log.d(TAG, "new SSID:" + config.SSID);
        }
        return config.SSID;
    }

    public int calculateSignalLevelHW(int freq, int rssi) {
        int rssi_Level_4 = -65;
        int rssi_Level_3 = -75;
        int rssi_Level_2 = -82;
        int rssi_Level_1 = -88;
        if (ScanResult.is5GHz(freq)) {
            rssi_Level_4 = -65;
            rssi_Level_3 = -72;
            rssi_Level_2 = -79;
            rssi_Level_1 = -85;
        }
        if (!TextUtils.isEmpty(this.wifi_level_threshold)) {
            String[] wifi_level_threshold_arr = this.wifi_level_threshold.split(",");
            if (wifi_level_threshold_arr.length == 4) {
                try {
                    rssi_Level_4 = Integer.parseInt(wifi_level_threshold_arr[0]);
                    rssi_Level_3 = Integer.parseInt(wifi_level_threshold_arr[1]);
                    rssi_Level_2 = Integer.parseInt(wifi_level_threshold_arr[2]);
                    rssi_Level_1 = Integer.parseInt(wifi_level_threshold_arr[3]);
                } catch (Exception e) {
                    Log.i(TAG, "Exception occured,use the default level threshold! wifi_level_threshold = " + this.wifi_level_threshold, e);
                    rssi_Level_4 = -65;
                    rssi_Level_3 = -75;
                    rssi_Level_2 = -82;
                    rssi_Level_1 = -88;
                }
            } else {
                Log.e(TAG, "Customization of wifi level is illeagel! wifi_level_threshold =" + this.wifi_level_threshold);
            }
        }
        if (rssi_Level_4 <= rssi) {
            return 4;
        }
        if (rssi_Level_4 - 1 >= rssi && rssi_Level_3 <= rssi) {
            return 3;
        }
        if (rssi_Level_3 - 1 >= rssi && rssi_Level_2 <= rssi) {
            return 2;
        }
        if (rssi_Level_2 - 1 < rssi || rssi_Level_1 > rssi) {
            return 0;
        }
        return 1;
    }

    public int calculateSignalLevelHW(int rssi) {
        int rssi_Level_4 = -65;
        int rssi_Level_3 = -75;
        int rssi_Level_2 = -82;
        int rssi_Level_1 = -88;
        if (!TextUtils.isEmpty(this.wifi_level_threshold)) {
            String[] wifi_level_threshold_arr = this.wifi_level_threshold.split(",");
            if (wifi_level_threshold_arr.length == 4) {
                try {
                    rssi_Level_4 = Integer.parseInt(wifi_level_threshold_arr[0]);
                    rssi_Level_3 = Integer.parseInt(wifi_level_threshold_arr[1]);
                    rssi_Level_2 = Integer.parseInt(wifi_level_threshold_arr[2]);
                    rssi_Level_1 = Integer.parseInt(wifi_level_threshold_arr[3]);
                } catch (Exception e) {
                    Log.i(TAG, "Exception occured,use the default level threshold! wifi_level_threshold = " + this.wifi_level_threshold);
                    rssi_Level_1 = -88;
                    rssi_Level_2 = -82;
                    rssi_Level_3 = -75;
                    rssi_Level_4 = -65;
                }
            } else {
                Log.e(TAG, "Customization of wifi level is illeagel! wifi_level_threshold =" + this.wifi_level_threshold);
            }
        }
        if (rssi_Level_4 <= rssi) {
            return 4;
        }
        if (rssi_Level_3 <= rssi && rssi_Level_4 - 1 >= rssi) {
            return 3;
        }
        if (rssi_Level_2 > rssi || rssi_Level_3 - 1 < rssi) {
            return (rssi_Level_1 > rssi || rssi_Level_2 - 1 < rssi) ? 0 : 1;
        } else {
            return 2;
        }
    }

    public String getWpaSuppConfig() {
        Parcel _data = Parcel.obtain();
        Parcel _reply = Parcel.obtain();
        IBinder b = ServiceManager.getService(ConnectivityLogManager.SUBSYS_WIFI);
        String str = TAG;
        Log.d(str, "getWpaSuppConfig");
        if (b != null) {
            try {
                _data.writeInterfaceToken(DESCRIPTOR);
                b.transact(CODE_GET_WPA_SUPP_CONFIG, _data, _reply, 0);
                _reply.readException();
                str = _reply.readString();
                return str;
            } catch (RemoteException localRemoteException) {
                localRemoteException.printStackTrace();
            } finally {
                _reply.recycle();
                _data.recycle();
            }
        } else {
            _reply.recycle();
            _data.recycle();
            return null;
        }
    }

    public void startPPPOE(PPPOEConfig config) {
        Parcel _data = Parcel.obtain();
        Parcel _reply = Parcel.obtain();
        IBinder b = ServiceManager.getService(ConnectivityLogManager.SUBSYS_WIFI);
        Log.d(TAG, "startPPPOE");
        if (b != null) {
            try {
                _data.writeInterfaceToken(DESCRIPTOR);
                if (config != null) {
                    _data.writeInt(1);
                    config.writeToParcel(_data, 0);
                } else {
                    _data.writeInt(0);
                }
                b.transact(CODE_START_PPPOE_CONFIG, _data, _reply, 0);
                _reply.readException();
            } catch (RemoteException localRemoteException) {
                localRemoteException.printStackTrace();
                return;
            } finally {
                _reply.recycle();
                _data.recycle();
            }
        }
        _reply.recycle();
        _data.recycle();
    }

    public void stopPPPOE() {
        Parcel _data = Parcel.obtain();
        Parcel _reply = Parcel.obtain();
        IBinder b = ServiceManager.getService(ConnectivityLogManager.SUBSYS_WIFI);
        if (b != null) {
            try {
                _data.writeInterfaceToken(DESCRIPTOR);
                b.transact(1003, _data, _reply, 0);
                _reply.readException();
            } catch (RemoteException localRemoteException) {
                localRemoteException.printStackTrace();
                return;
            } finally {
                _reply.recycle();
                _data.recycle();
            }
        }
        _reply.recycle();
        _data.recycle();
    }

    public PPPOEInfo getPPPOEInfo() {
        Parcel _data = Parcel.obtain();
        Parcel _reply = Parcel.obtain();
        PPPOEInfo pPPOEInfo = null;
        IBinder b = ServiceManager.getService(ConnectivityLogManager.SUBSYS_WIFI);
        if (b != null) {
            try {
                _data.writeInterfaceToken(DESCRIPTOR);
                b.transact(1004, _data, _reply, 0);
                _reply.readException();
                if (_reply.readInt() != 0) {
                    pPPOEInfo = (PPPOEInfo) PPPOEInfo.CREATOR.createFromParcel(_reply);
                } else {
                    pPPOEInfo = null;
                }
                _reply.readException();
            } catch (RemoteException localRemoteException) {
                localRemoteException.printStackTrace();
            } finally {
                _reply.recycle();
                _data.recycle();
            }
        }
        _reply.recycle();
        _data.recycle();
        return pPPOEInfo;
    }

    public boolean setWifiEnterpriseConfigEapMethod(int eapMethod, HashMap<String, String> hashMap) {
        switch (eapMethod) {
            case 4:
            case 5:
            case 6:
                return true;
            default:
                return false;
        }
    }

    public boolean getHwMeteredHint(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(ConnectivityLogManager.SUBSYS_WIFI);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiManager.getWifiState() != 3 || wifiInfo == null) {
            return false;
        }
        Log.d(TAG, "SupplicantState = " + wifiInfo.getSupplicantState());
        if (wifiInfo.getSupplicantState() != SupplicantState.COMPLETED) {
            return false;
        }
        boolean isPhoneAP = wifiInfo.getMeteredHint();
        Log.d(TAG, "isPhoneAP = " + isPhoneAP);
        return isPhoneAP;
    }

    public List<String> getApLinkedStaList() {
        Parcel _data = Parcel.obtain();
        Parcel _reply = Parcel.obtain();
        IBinder b = ServiceManager.getService(ConnectivityLogManager.SUBSYS_WIFI);
        List<String> list = TAG;
        Log.d(list, "getApLinkedStaList");
        if (b != null) {
            try {
                _data.writeInterfaceToken(DESCRIPTOR);
                b.transact(1005, _data, _reply, 0);
                _reply.readException();
                list = _reply.createStringArrayList();
                return list;
            } catch (RemoteException localRemoteException) {
                localRemoteException.printStackTrace();
            } finally {
                _reply.recycle();
                _data.recycle();
            }
        } else {
            _reply.recycle();
            _data.recycle();
            return null;
        }
    }

    public void setSoftapMacFilter(String macFilter) {
        Parcel _data = Parcel.obtain();
        Parcel _reply = Parcel.obtain();
        IBinder b = ServiceManager.getService(ConnectivityLogManager.SUBSYS_WIFI);
        Log.d(TAG, "setSoftapMacFilter macFilter = " + macFilter);
        if (b != null) {
            try {
                _data.writeInterfaceToken(DESCRIPTOR);
                _data.writeString(macFilter);
                b.transact(1006, _data, _reply, 0);
                _reply.readException();
            } catch (RemoteException localRemoteException) {
                localRemoteException.printStackTrace();
                return;
            } finally {
                _reply.recycle();
                _data.recycle();
            }
        }
        _reply.recycle();
        _data.recycle();
    }

    public void setSoftapDisassociateSta(String mac) {
        Parcel _data = Parcel.obtain();
        Parcel _reply = Parcel.obtain();
        IBinder b = ServiceManager.getService(ConnectivityLogManager.SUBSYS_WIFI);
        Log.d(TAG, "setSoftapDisassociateSta mac = " + mac);
        if (b != null) {
            try {
                _data.writeInterfaceToken(DESCRIPTOR);
                _data.writeString(mac);
                b.transact(1007, _data, _reply, 0);
                _reply.readException();
            } catch (RemoteException localRemoteException) {
                localRemoteException.printStackTrace();
                return;
            } finally {
                _reply.recycle();
                _data.recycle();
            }
        }
        _reply.recycle();
        _data.recycle();
    }

    public void userHandoverWifi() {
        Parcel _data = Parcel.obtain();
        Parcel _reply = Parcel.obtain();
        IBinder b = ServiceManager.getService(ConnectivityLogManager.SUBSYS_WIFI);
        Log.d(TAG, "wifipro userHandoverWifi ");
        if (b != null) {
            try {
                _data.writeInterfaceToken(DESCRIPTOR);
                b.transact(1008, _data, _reply, 0);
                _reply.readException();
            } catch (RemoteException localRemoteException) {
                localRemoteException.printStackTrace();
                return;
            } finally {
                _reply.recycle();
                _data.recycle();
            }
        }
        _reply.recycle();
        _data.recycle();
    }

    public void setWifiApEvaluateEnabled(boolean enabled) {
        int i = 0;
        Parcel _data = Parcel.obtain();
        Parcel _reply = Parcel.obtain();
        IBinder b = ServiceManager.getService(ConnectivityLogManager.SUBSYS_WIFI);
        Log.d(TAG, "setWifiApEvaluateEnabled enabled :" + enabled);
        if (b != null) {
            try {
                _data.writeInterfaceToken(DESCRIPTOR);
                if (enabled) {
                    i = 1;
                }
                _data.writeInt(i);
                Log.d(TAG, "setWifiApEvaluateEnabled enabled :" + enabled);
                b.transact(1010, _data, _reply, 0);
                _reply.readException();
            } catch (RemoteException localRemoteException) {
                localRemoteException.printStackTrace();
                return;
            } finally {
                _reply.recycle();
                _data.recycle();
            }
        }
        _reply.recycle();
        _data.recycle();
    }

    public int[] getChannelListFor5G() {
        Parcel _data = Parcel.obtain();
        Parcel _reply = Parcel.obtain();
        IBinder b = ServiceManager.getService(ConnectivityLogManager.SUBSYS_WIFI);
        int[] _result = null;
        try {
            _data.writeInterfaceToken(DESCRIPTOR);
            b.transact(1009, _data, _reply, 0);
            _reply.readException();
            _result = _reply.createIntArray();
        } catch (RemoteException localRemoteException) {
            localRemoteException.printStackTrace();
        } finally {
            _reply.recycle();
            _data.recycle();
        }
        return _result;
    }

    public byte[] fetchWifiSignalInfoForVoWiFi() {
        Parcel _data = Parcel.obtain();
        Parcel _reply = Parcel.obtain();
        IBinder b = ServiceManager.getService(ConnectivityLogManager.SUBSYS_WIFI);
        Log.d(TAG, "fetchWifiSignalInfoForVoWiFi");
        byte[] _result = null;
        try {
            _data.writeInterfaceToken(DESCRIPTOR);
            b.transact(1011, _data, _reply, 0);
            _reply.readException();
            _result = _reply.createByteArray();
        } catch (RemoteException localRemoteException) {
            localRemoteException.printStackTrace();
        } finally {
            _reply.recycle();
            _data.recycle();
        }
        return _result;
    }

    public boolean setVoWifiDetectMode(WifiDetectConfInfo info) {
        Parcel _data = Parcel.obtain();
        Parcel _reply = Parcel.obtain();
        IBinder b = ServiceManager.getService(ConnectivityLogManager.SUBSYS_WIFI);
        Log.d(TAG, "setVoWifiDetectMode info :" + info);
        boolean _result = true;
        try {
            _data.writeInterfaceToken(DESCRIPTOR);
            if (info != null) {
                _data.writeInt(1);
                info.writeToParcel(_data, 0);
            } else {
                _data.writeInt(0);
            }
            b.transact(1012, _data, _reply, 0);
            _reply.readException();
        } catch (RemoteException localRemoteException) {
            localRemoteException.printStackTrace();
            _result = false;
        } finally {
            _reply.recycle();
            _data.recycle();
        }
        return _result;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public WifiDetectConfInfo getVoWifiDetectMode() {
        Parcel _data = Parcel.obtain();
        Parcel _reply = Parcel.obtain();
        IBinder b = ServiceManager.getService(ConnectivityLogManager.SUBSYS_WIFI);
        Log.d(TAG, "getVoWifiDetectMode");
        WifiDetectConfInfo wifiDetectConfInfo = null;
        try {
            _data.writeInterfaceToken(DESCRIPTOR);
            b.transact(1013, _data, _reply, 0);
            _reply.readException();
            if (_reply.readInt() != 0) {
                wifiDetectConfInfo = (WifiDetectConfInfo) WifiDetectConfInfo.CREATOR.createFromParcel(_data);
            } else {
                wifiDetectConfInfo = null;
            }
            _reply.recycle();
            _data.recycle();
        } catch (RemoteException localRemoteException) {
            localRemoteException.printStackTrace();
        } catch (Throwable th) {
            _reply.recycle();
            _data.recycle();
        }
        return wifiDetectConfInfo;
    }

    public boolean setVoWifiDetectPeriod(int period) {
        Parcel _data = Parcel.obtain();
        Parcel _reply = Parcel.obtain();
        IBinder b = ServiceManager.getService(ConnectivityLogManager.SUBSYS_WIFI);
        Log.d(TAG, "setVoWifiDetectPeriod period :" + period);
        boolean _result = true;
        try {
            _data.writeInterfaceToken(DESCRIPTOR);
            _data.writeInt(period);
            b.transact(1014, _data, _reply, 0);
            _reply.readException();
        } catch (RemoteException localRemoteException) {
            localRemoteException.printStackTrace();
            _result = false;
        } finally {
            _reply.recycle();
            _data.recycle();
        }
        return _result;
    }

    public int getVoWifiDetectPeriod() {
        Parcel _data = Parcel.obtain();
        Parcel _reply = Parcel.obtain();
        IBinder b = ServiceManager.getService(ConnectivityLogManager.SUBSYS_WIFI);
        Log.d(TAG, "getVoWifiDetectPeriod");
        int _result = -1;
        try {
            _data.writeInterfaceToken(DESCRIPTOR);
            b.transact(1015, _data, _reply, 0);
            _reply.readException();
            _result = _reply.readInt();
        } catch (RemoteException localRemoteException) {
            localRemoteException.printStackTrace();
        } finally {
            _reply.recycle();
            _data.recycle();
        }
        return _result;
    }

    public boolean isSupportVoWifiDetect() {
        Parcel _data = Parcel.obtain();
        Parcel _reply = Parcel.obtain();
        IBinder b = ServiceManager.getService(ConnectivityLogManager.SUBSYS_WIFI);
        Log.d(TAG, "isSupportVoWifiDetect");
        boolean[] _result = new boolean[1];
        try {
            _data.writeInterfaceToken(DESCRIPTOR);
            b.transact(1016, _data, _reply, 0);
            _reply.readException();
            _reply.readBooleanArray(_result);
        } catch (RemoteException localRemoteException) {
            localRemoteException.printStackTrace();
        } finally {
            _reply.recycle();
            _data.recycle();
        }
        return _result[0];
    }

    public void enableHiLinkHandshake(boolean uiEnable, String bssid) {
        int i = 0;
        Parcel _data = Parcel.obtain();
        Parcel _reply = Parcel.obtain();
        IBinder b = ServiceManager.getService(ConnectivityLogManager.SUBSYS_WIFI);
        try {
            _data.writeInterfaceToken(DESCRIPTOR);
            if (uiEnable) {
                i = 1;
            }
            _data.writeInt(i);
            _data.writeString(bssid);
            b.transact(2001, _data, _reply, 0);
            _reply.readException();
        } catch (RemoteException localRemoteException) {
            localRemoteException.printStackTrace();
        } finally {
            _reply.recycle();
            _data.recycle();
        }
    }
}
