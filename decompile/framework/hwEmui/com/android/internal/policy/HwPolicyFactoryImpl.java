package com.android.internal.policy;

import android.content.Context;
import android.view.View;
import android.view.Window;
import com.android.internal.policy.HwPolicyFactory.Factory;

public class HwPolicyFactoryImpl implements Factory {
    private static final String TAG = "HwPolicyFactoryImpl";

    public Window getHwPhoneWindow(Context context) {
        return new HwPhoneWindow(context);
    }

    public Window getHwPhoneWindow(Context context, Window win) {
        return new HwPhoneWindow(context, win);
    }

    public PhoneLayoutInflater getHwPhoneLayoutInflater(Context context) {
        return new HwPhoneLayoutInflater(context);
    }

    public View getHwNavigationBarColorView(Context context) {
        return new HwNavigationBarColorView(context);
    }
}
