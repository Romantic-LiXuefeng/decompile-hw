package android.net.wifi;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.ProxyInfo;
import android.os.SystemProperties;
import android.provider.Settings.Global;
import android.util.Slog;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class HiSiWifiComm {
    private static String SLEEP_POLICY_FILE = "/sys/hi110x/sleep_policy";
    private static final String TAG = "HiSiWifiComm";
    public static final int checkboxNoSelected = 0;
    public static final int checkboxSelected = 1;
    public static final int mGroupOwnerIntent = 3;
    private Context mContext;

    public HiSiWifiComm(Context context) {
        this.mContext = context;
    }

    public static boolean hisiWifiEnabled() {
        if (SystemProperties.get("ro.connectivity.chiptype").equals("hi110x")) {
            return true;
        }
        return false;
    }

    public static void writeSleepOption(int policy) {
        Throwable th;
        Slog.d(TAG, "WriteSleepOption,policy is " + policy);
        FileOutputStream fileOutputStream = null;
        try {
            FileOutputStream os = new FileOutputStream(new File(SLEEP_POLICY_FILE), true);
            try {
                os.write(policy);
                if (os != null) {
                    try {
                        os.close();
                    } catch (IOException e) {
                        Slog.e(TAG, "Failed close fileOutputStream");
                    }
                }
                fileOutputStream = os;
            } catch (IOException e2) {
                fileOutputStream = os;
                try {
                    Slog.e(TAG, "Failed setting sleep policy");
                    if (fileOutputStream != null) {
                        try {
                            fileOutputStream.close();
                        } catch (IOException e3) {
                            Slog.e(TAG, "Failed close fileOutputStream");
                        }
                    }
                } catch (Throwable th2) {
                    th = th2;
                    if (fileOutputStream != null) {
                        try {
                            fileOutputStream.close();
                        } catch (IOException e4) {
                            Slog.e(TAG, "Failed close fileOutputStream");
                        }
                    }
                    throw th;
                }
            } catch (Throwable th3) {
                th = th3;
                fileOutputStream = os;
                if (fileOutputStream != null) {
                    fileOutputStream.close();
                }
                throw th;
            }
        } catch (IOException e5) {
            Slog.e(TAG, "Failed setting sleep policy");
            if (fileOutputStream != null) {
                fileOutputStream.close();
            }
        }
    }

    public boolean isP2pConnect() {
        NetworkInfo mNetworkInfo = null;
        ConnectivityManager mConnectivityManager = (ConnectivityManager) this.mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (mConnectivityManager != null) {
            mNetworkInfo = mConnectivityManager.getNetworkInfo(13);
        }
        if (mNetworkInfo == null) {
            return false;
        }
        boolean isConneted = mNetworkInfo.isConnected();
        Slog.d(TAG, "p2p isConneted: " + isConneted);
        return isConneted;
    }

    public int getSettingsGlobalIntValue(String key) {
        if (key == null || key.equals(ProxyInfo.LOCAL_EXCL_LIST)) {
            return 0;
        }
        return Global.getInt(this.mContext.getContentResolver(), key, 0);
    }

    public void changeShowDialogFlag(String key, boolean value) {
        if (key == null || key.equals(ProxyInfo.LOCAL_EXCL_LIST)) {
            Slog.d(TAG, "invalid key");
        } else if (value) {
            Global.putInt(this.mContext.getContentResolver(), key, 1);
        } else {
            Global.putInt(this.mContext.getContentResolver(), key, 0);
        }
    }
}
