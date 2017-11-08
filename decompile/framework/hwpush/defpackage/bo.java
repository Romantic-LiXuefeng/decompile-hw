package defpackage;

import com.huawei.android.pushagent.utils.multicard.MultiCard;
import java.lang.reflect.InvocationTargetException;

/* renamed from: bo */
class bo implements MultiCard {
    private static bo ci;

    bo() {
    }

    public static synchronized bo cj() {
        bo boVar;
        synchronized (bo.class) {
            if (ci == null) {
                ci = new bo();
            }
            boVar = ci;
        }
        return boVar;
    }

    public static Object ck() {
        Object obj = null;
        try {
            Class cls = Class.forName("android.telephony.MSimTelephonyManager");
            obj = cls.getDeclaredMethod("getDefault", new Class[0]).invoke(cls, new Object[0]);
        } catch (Exception e) {
            aw.v("MutiCardHwImpl", " getDefaultMSimTelephonyManager wrong " + e.toString());
        }
        return obj;
    }

    public String getDeviceId(int i) {
        String str;
        String str2 = "";
        Class[] clsArr = new Class[]{Integer.TYPE};
        Object[] objArr = new Object[]{Integer.valueOf(i)};
        try {
            Object ck = bo.ck();
            if (ck != null) {
                str = (String) ck.getClass().getMethod("getDeviceId", clsArr).invoke(ck, objArr);
                return str != null ? "" : str;
            }
        } catch (NoSuchMethodException e) {
            aw.v("MutiCardHwImpl", "getDeviceId NoSuchMethodException :" + e.toString());
            str = str2;
        } catch (IllegalAccessException e2) {
            aw.v("MutiCardHwImpl", "getDeviceId IllegalAccessException:" + e2.toString());
            str = str2;
        } catch (InvocationTargetException e3) {
            aw.v("MutiCardHwImpl", "getDeviceId InvocationTargetException:" + e3.toString());
            str = str2;
        } catch (Exception e4) {
            aw.v("MutiCardHwImpl", "getDeviceId exception:" + e4.toString());
        }
        str = str2;
        if (str != null) {
        }
    }
}
