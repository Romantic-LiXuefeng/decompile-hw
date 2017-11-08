package com.android.server.wifi.wifipro;

import android.os.Build.VERSION;
import android.telephony.PhoneStateListener;
import android.util.Log;
import java.lang.reflect.Field;

public class DualPhoneStateListener extends PhoneStateListener {
    private static final Field FIELD_subID;
    private static int LOLLIPOP_VER = 21;
    private static String TAG = "MQoS";

    static {
        Field declaredField;
        if (VERSION.SDK_INT >= LOLLIPOP_VER) {
            declaredField = getDeclaredField(PhoneStateListener.class, "mSubId");
        } else {
            declaredField = getDeclaredField(PhoneStateListener.class, "mSubscription");
        }
        FIELD_subID = declaredField;
    }

    public DualPhoneStateListener(int subscription) {
        if (FIELD_subID == null) {
            throw new UnsupportedOperationException();
        }
        if (!FIELD_subID.isAccessible()) {
            FIELD_subID.setAccessible(true);
        }
        if (VERSION.SDK_INT == LOLLIPOP_VER) {
            setFieldValue(this, FIELD_subID, Long.valueOf((long) subscription));
        } else {
            setFieldValue(this, FIELD_subID, Integer.valueOf(subscription));
        }
    }

    public static int getSubscription(PhoneStateListener obj) {
        if (FIELD_subID == null) {
            throw new UnsupportedOperationException();
        }
        if (!FIELD_subID.isAccessible()) {
            FIELD_subID.setAccessible(true);
        }
        if (VERSION.SDK_INT == LOLLIPOP_VER) {
            return ((Long) getFieldValue(obj, FIELD_subID)).intValue();
        }
        return ((Integer) getFieldValue(obj, FIELD_subID)).intValue();
    }

    private static boolean isEmpty(String str) {
        boolean z = true;
        if (str == null || str.length() == 0) {
            return true;
        }
        if (str.trim().length() != 0) {
            z = false;
        }
        return z;
    }

    private static Field getDeclaredField(Class<?> targetClass, String name) {
        if (targetClass == null || isEmpty(name)) {
            return null;
        }
        try {
            return targetClass.getDeclaredField(name);
        } catch (SecurityException e) {
            Log.e(TAG, name + ":" + e.getCause());
            return null;
        } catch (NoSuchFieldException e2) {
            Log.e(TAG, name + ",no such field.");
            return null;
        }
    }

    private static void setFieldValue(Object receiver, Field field, Object value) {
        if (field != null) {
            try {
                field.set(receiver, value);
            } catch (Exception e) {
                Log.e(TAG, "Exception in setFieldValue: " + e.getClass().getSimpleName());
            }
        }
    }

    private static Object getFieldValue(Object receiver, Field field) {
        if (field == null) {
            return null;
        }
        try {
            return field.get(receiver);
        } catch (Exception e) {
            Log.e(TAG, "Exception in getFieldValue: " + e.getClass().getSimpleName());
            throw new UnsupportedOperationException();
        }
    }
}
