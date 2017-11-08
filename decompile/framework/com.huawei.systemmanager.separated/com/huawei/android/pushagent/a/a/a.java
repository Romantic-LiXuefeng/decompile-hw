package com.huawei.android.pushagent.a.a;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import java.io.UnsupportedEncodingException;

/* compiled from: Unknown */
public class a {
    private static final char[] a = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    public static int a() {
        int intValue;
        Class[] clsArr = new Class[]{String.class, Integer.TYPE};
        Object[] objArr = new Object[]{"ro.build.hw_emui_api_level", Integer.valueOf(0)};
        try {
            Class cls = Class.forName("android.os.SystemProperties");
            intValue = ((Integer) cls.getDeclaredMethod("getInt", clsArr).invoke(cls, objArr)).intValue();
            try {
                c.a("PushLogSC2606", "getEmuiLevel:" + intValue);
            } catch (ClassNotFoundException e) {
                c.d("PushLogSC2606", " getEmuiLevel wrong, ClassNotFoundException");
                return intValue;
            } catch (LinkageError e2) {
                c.d("PushLogSC2606", " getEmuiLevel wrong, LinkageError");
                return intValue;
            } catch (NoSuchMethodException e3) {
                c.d("PushLogSC2606", " getEmuiLevel wrong, NoSuchMethodException");
                return intValue;
            } catch (NullPointerException e4) {
                c.d("PushLogSC2606", " getEmuiLevel wrong, NullPointerException");
                return intValue;
            } catch (Exception e5) {
                c.d("PushLogSC2606", " getEmuiLevel wrong");
                return intValue;
            }
        } catch (ClassNotFoundException e6) {
            intValue = 0;
            c.d("PushLogSC2606", " getEmuiLevel wrong, ClassNotFoundException");
            return intValue;
        } catch (LinkageError e7) {
            intValue = 0;
            c.d("PushLogSC2606", " getEmuiLevel wrong, LinkageError");
            return intValue;
        } catch (NoSuchMethodException e8) {
            intValue = 0;
            c.d("PushLogSC2606", " getEmuiLevel wrong, NoSuchMethodException");
            return intValue;
        } catch (NullPointerException e9) {
            intValue = 0;
            c.d("PushLogSC2606", " getEmuiLevel wrong, NullPointerException");
            return intValue;
        } catch (Exception e10) {
            intValue = 0;
            c.d("PushLogSC2606", " getEmuiLevel wrong");
            return intValue;
        }
        return intValue;
    }

    public static int a(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService("connectivity");
        if (connectivityManager == null) {
            return -1;
        }
        NetworkInfo[] allNetworkInfo = connectivityManager.getAllNetworkInfo();
        if (allNetworkInfo == null) {
            return -1;
        }
        for (int i = 0; i < allNetworkInfo.length; i++) {
            if (allNetworkInfo[i].getState() == State.CONNECTED) {
                return allNetworkInfo[i].getType();
            }
        }
        return -1;
    }

    public static String a(byte[] bArr) {
        if (bArr == null) {
            return null;
        }
        if (bArr.length == 0) {
            return "";
        }
        char[] cArr = new char[(bArr.length * 2)];
        for (int i = 0; i < bArr.length; i++) {
            byte b = bArr[i];
            cArr[i * 2] = (char) a[(b & 240) >> 4];
            cArr[(i * 2) + 1] = (char) a[b & 15];
        }
        return new String(cArr);
    }

    public static byte[] a(String str) {
        byte[] bArr = new byte[(str.length() / 2)];
        try {
            byte[] bytes = str.getBytes("UTF-8");
            for (int i = 0; i < bArr.length; i++) {
                bArr[i] = (byte) ((byte) (((byte) (Byte.decode("0x" + new String(new byte[]{(byte) bytes[i * 2]}, "UTF-8")).byteValue() << 4)) ^ Byte.decode("0x" + new String(new byte[]{(byte) bytes[(i * 2) + 1]}, "UTF-8")).byteValue()));
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return bArr;
    }
}
