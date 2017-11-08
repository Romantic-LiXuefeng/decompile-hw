package tmsdk.common.utils;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemProperties;
import android.provider.Settings.Secure;
import android.support.v4.app.FragmentTransaction;
import android.telephony.TelephonyManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import tmsdkobf.ms;

/* compiled from: Unknown */
public final class h {
    private static String La = "tms_";
    private static String Lb = "[com.android.internal.telephony.ITelephonyRegistry]";
    private static Boolean Lc = null;
    private static String TELEPHONY_SERVICE = "[com.android.internal.telephony.ITelephony]";

    /* compiled from: Unknown */
    public static class a {
        public long Ld;
        public long Le;
    }

    public static String C(Context context) {
        String deviceId;
        try {
            deviceId = ((TelephonyManager) context.getSystemService("phone")).getDeviceId();
        } catch (Exception e) {
            e.printStackTrace();
            deviceId = null;
        }
        return deviceId != null ? deviceId : "00000000000000";
    }

    public static String D(Context context) {
        String subscriberId;
        try {
            subscriberId = ((TelephonyManager) context.getSystemService("phone")).getSubscriberId();
        } catch (Exception e) {
            e.printStackTrace();
            subscriberId = null;
        }
        return subscriberId != null ? subscriberId : "000000000000000";
    }

    public static String E(Context context) {
        WifiInfo connectionInfo;
        try {
            connectionInfo = ((WifiManager) context.getSystemService("wifi")).getConnectionInfo();
        } catch (Exception e) {
            e.printStackTrace();
            connectionInfo = null;
        }
        return connectionInfo == null ? null : connectionInfo.getMacAddress();
    }

    public static String F(Context context) {
        try {
            return ((TelephonyManager) context.getSystemService("phone")).getSimSerialNumber();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String G(Context context) {
        try {
            return Secure.getString(context.getContentResolver(), "android_id");
        } catch (Throwable th) {
            return "";
        }
    }

    public static int H(Context context) {
        return context.getResources().getDisplayMetrics().widthPixels;
    }

    public static int I(Context context) {
        return context.getResources().getDisplayMetrics().heightPixels;
    }

    public static int J(Context context) {
        return k.dj(D(context));
    }

    public static void a(File file, a aVar) {
        try {
            StatFs statFs = new StatFs(file.getPath());
            long blockSize = (long) statFs.getBlockSize();
            aVar.Ld = ((long) statFs.getAvailableBlocks()) * blockSize;
            aVar.Le = ((long) statFs.getBlockCount()) * blockSize;
        } catch (Throwable e) {
            d.a("PhoneInfoUtil", "getSizeInfo err:" + e.getMessage(), e);
        }
    }

    public static void a(a aVar) {
        if (ms.eW()) {
            a(Environment.getExternalStorageDirectory(), aVar);
            return;
        }
        aVar.Ld = 0;
        aVar.Le = 0;
    }

    public static void b(a aVar) {
        a(Environment.getDataDirectory(), aVar);
    }

    public static String dg(String str) {
        String str2 = SystemProperties.get(str);
        return str2 != null ? str2 : "";
    }

    public static String getRadioVersion() {
        String str = "";
        try {
            return (String) Class.forName("android.os.Build").getMethod("getRadioVersion", new Class[0]).invoke(null, new Object[0]);
        } catch (Throwable th) {
            d.f("PhoneInfoUtil", th);
            return str;
        }
    }

    @Deprecated
    public static String iB() {
        return "android_id";
    }

    public static String iC() {
        return Build.MODEL;
    }

    public static String iD() {
        return Build.PRODUCT;
    }

    public static boolean iE() {
        if (Lc == null) {
            try {
                String[] exec = ScriptHelper.exec("service", "list");
                if (exec != null) {
                    if (exec.length > 0) {
                        int i = 0;
                        int i2 = 0;
                        for (String str : exec) {
                            if (!str.contains(La)) {
                                if (str.contains(TELEPHONY_SERVICE)) {
                                    i2++;
                                } else if (str.contains(Lb)) {
                                    i++;
                                }
                            }
                        }
                        if (i2 <= 1 && i <= 1) {
                            Lc = Boolean.valueOf(false);
                        } else {
                            Lc = Boolean.valueOf(true);
                        }
                    }
                }
                Lc = Boolean.valueOf(false);
            } catch (Exception e) {
                Lc = Boolean.valueOf(false);
            }
        }
        if (Lc == null) {
            Lc = Boolean.valueOf(false);
        }
        return Lc.booleanValue();
    }

    public static String iF() {
        return VERSION.INCREMENTAL;
    }

    public static String iG() {
        return VERSION.RELEASE;
    }

    public static String iH() {
        return Build.BRAND;
    }

    public static String iI() {
        return Build.DEVICE;
    }

    public static String iJ() {
        return Build.BOARD;
    }

    public static String iK() {
        String str = "";
        try {
            InputStream fileInputStream = new FileInputStream("/proc/version");
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fileInputStream), FragmentTransaction.TRANSIT_EXIT_MASK);
            String str2 = "";
            StringBuilder stringBuilder = new StringBuilder("");
            while (true) {
                try {
                    str2 = bufferedReader.readLine();
                    if (str2 == null) {
                        break;
                    }
                    stringBuilder.append(str2);
                } catch (Throwable th) {
                    d.c("PhoneInfoUtil", th);
                }
            }
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (Throwable th2) {
                    d.c("PhoneInfoUtil", th2);
                }
            }
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (Throwable th22) {
                    d.c("PhoneInfoUtil", th22);
                }
            }
            str2 = stringBuilder.toString();
            if (str2 != null) {
                try {
                    if (!(str2 == "" || str2.equals(""))) {
                        str2 = str2.substring(str2.indexOf("version ") + "version ".length());
                        str2 = str2.substring(0, str2.indexOf(" "));
                        str = str2;
                        return str;
                    }
                } catch (Throwable th222) {
                    d.c("PhoneInfoUtil", th222);
                }
            }
            str2 = str;
            str = str2;
            return str;
            if (fileInputStream != null) {
                fileInputStream.close();
            }
            str2 = stringBuilder.toString();
            if (str2 != null) {
                str2 = str2.substring(str2.indexOf("version ") + "version ".length());
                str2 = str2.substring(0, str2.indexOf(" "));
                str = str2;
                return str;
            }
            str2 = str;
            str = str2;
            return str;
            str2 = stringBuilder.toString();
            if (str2 != null) {
                str2 = str2.substring(str2.indexOf("version ") + "version ".length());
                str2 = str2.substring(0, str2.indexOf(" "));
                str = str2;
                return str;
            }
            str2 = str;
            str = str2;
            return str;
        } catch (Throwable th2222) {
            d.c("PhoneInfoUtil", th2222);
            return str;
        }
    }

    public static String iL() {
        String str = Build.MANUFACTURER;
        return str != null ? str : "UNKNOWN";
    }
}
