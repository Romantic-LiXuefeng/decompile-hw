package com.android.server.policy;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings.Global;
import android.util.HwSlog;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.vkey.SettingsHelper;
import com.android.server.rms.iaware.hiber.constant.AppHibernateCst;
import com.huawei.android.statistical.StatisticalUtils;
import huawei.android.os.HwGeneralManager;
import huawei.android.provider.FrontFingerPrintSettings;

public class NavigationBarPolicy implements OnGestureListener {
    static final boolean DEBUG = false;
    private static final boolean FRONT_FINGERPRINT_NAVIGATION = SystemProperties.getBoolean("ro.config.hw_front_fp_navi", false);
    static final int HIT_REGION_SCALE = 4;
    static int HIT_REGION_TO_MAX = 20;
    static int HIT_REGION_TO_TOP_BOTTOM = 130;
    private static final boolean IS_CHINA_AREA = "CN".equalsIgnoreCase(SystemProperties.get("ro.product.locale.region", AppHibernateCst.INVALID_PKG));
    private static final String TAG = "NavigationBarPolicy";
    private boolean IS_SUPPORT_PRESSURE = false;
    private Context mContext = null;
    private GestureDetector mDetector = null;
    boolean mForceMinNavigationBar = false;
    private boolean mImmersiveMode = false;
    private boolean mIsValidGesture = false;
    boolean mMinNavigationBar = false;
    private PhoneWindowManager mPolicy = null;
    private Point realSize = new Point();

    public NavigationBarPolicy(Context context, PhoneWindowManager policy) {
        boolean z = false;
        this.mContext = context;
        this.mPolicy = policy;
        this.mDetector = new GestureDetector(context, this);
        if (Global.getInt(this.mContext.getContentResolver(), "navigationbar_is_min", 0) != 0) {
            z = true;
        }
        this.mMinNavigationBar = z;
        updateRealSize();
        this.IS_SUPPORT_PRESSURE = HwGeneralManager.getInstance().isSupportForce();
    }

    public void addPointerEvent(MotionEvent event) {
        this.mDetector.onTouchEvent(event);
    }

    private void reset() {
        this.mIsValidGesture = false;
    }

    public void setImmersiveMode(boolean mode) {
        this.mImmersiveMode = mode;
    }

    private boolean touchDownIsValid(float pointX, float pointY) {
        if (this.mPolicy.mDisplay == null || this.mForceMinNavigationBar || (this.mPolicy.mKeyguardDelegate.isShowing() && !this.mPolicy.mKeyguardDelegate.isOccluded())) {
            return false;
        }
        if (this.IS_SUPPORT_PRESSURE && !IS_CHINA_AREA && !this.mImmersiveMode) {
            return false;
        }
        boolean ret = false;
        HIT_REGION_TO_MAX = (int) (((double) this.mContext.getResources().getDimensionPixelSize(17104920)) / 3.5d);
        if (this.mMinNavigationBar) {
            updateRealSize();
            ret = this.mPolicy.mNavigationBarOnBottom ? pointY > ((float) (this.realSize.y - HIT_REGION_TO_MAX)) : pointX > ((float) (this.realSize.x - HIT_REGION_TO_MAX));
        }
        return ret;
    }

    private void updateRealSize() {
        if (this.mPolicy.mDisplay != null) {
            this.mPolicy.mDisplay.getRealSize(this.realSize);
        }
    }

    public void updateNavigationBar(boolean minNaviBar) {
        this.mMinNavigationBar = minNaviBar;
        Global.putInt(this.mContext.getContentResolver(), "navigationbar_is_min", minNaviBar ? 1 : 0);
        this.mPolicy.mWindowManagerFuncs.reevaluateStatusBarSize(true);
    }

    private void sendBroadcast(boolean minNaviBar) {
        HwSlog.d(TAG, "sendBroadcast minNaviBar = " + minNaviBar);
        Intent intent = new Intent("com.huawei.navigationbar.statuschange");
        intent.putExtra("minNavigationBar", minNaviBar);
        this.mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
        StatisticalUtils.reportc(this.mContext, 61);
    }

    public boolean onDown(MotionEvent event) {
        this.mIsValidGesture = touchDownIsValid(event.getRawX(), event.getRawY());
        return false;
    }

    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        boolean z = true;
        if (isFlingOnOversea()) {
            HwSlog.d(TAG, "onFling::runmode is normal and not China area, return! ");
            return false;
        } else if (!isFlingOnFrontNaviMode() || isInLockTaskMode()) {
            if (this.IS_SUPPORT_PRESSURE && IS_CHINA_AREA) {
                boolean ret = false;
                if (this.mMinNavigationBar) {
                    ret = this.mPolicy.mNavigationBarOnBottom ? e2.getY() < ((float) (this.realSize.y - HIT_REGION_TO_TOP_BOTTOM)) : e2.getX() < ((float) (this.realSize.x - HIT_REGION_TO_TOP_BOTTOM));
                }
                if (!ret) {
                    HwSlog.d(TAG, "onFling::move distance is not enough, return! ");
                    return false;
                }
            }
            if (!this.mIsValidGesture || SettingsHelper.isTouchPlusOn(this.mContext)) {
                HwSlog.d(TAG, "onFling::not valid gesture or touch plus on, " + this.mIsValidGesture + ", return!");
                return false;
            }
            if (this.mPolicy.mNavigationBarOnBottom) {
                if (e1.getRawY() >= e2.getRawY()) {
                    z = false;
                }
                sendBroadcast(z);
            } else {
                if (e1.getRawX() >= e2.getRawX()) {
                    z = false;
                }
                sendBroadcast(z);
            }
            reset();
            return false;
        } else {
            HwSlog.d(TAG, "onFling::FRONT_FINGERPRINT_NAVIGATION, return! ");
            return false;
        }
    }

    private boolean isFlingOnOversea() {
        return "normal".equals(SystemProperties.get("ro.runmode", "normal")) && !IS_CHINA_AREA;
    }

    private boolean isFlingOnFrontNaviMode() {
        return "normal".equals(SystemProperties.get("ro.runmode", "normal")) && FRONT_FINGERPRINT_NAVIGATION && !isNaviBarEnabled();
    }

    private boolean isNaviBarEnabled() {
        if (this.mContext == null) {
            return true;
        }
        boolean isNaviBarEnable = FrontFingerPrintSettings.isNaviBarEnabled(this.mContext.getContentResolver());
        Log.d(TAG, "isNaviBarEnable is: " + isNaviBarEnable);
        return isNaviBarEnable;
    }

    private boolean isInLockTaskMode() {
        ActivityManager mActivityManager = (ActivityManager) this.mContext.getSystemService("activity");
        if (FrontFingerPrintSettings.FRONT_FINGERPRINT_NAVIGATION && FrontFingerPrintSettings.FRONT_FINGERPRINT_NAVIGATION_TRIKEY == 0 && mActivityManager.isInLockTaskMode()) {
            return true;
        }
        return false;
    }

    public void onLongPress(MotionEvent arg0) {
    }

    public boolean onScroll(MotionEvent arg0, MotionEvent arg1, float arg2, float arg3) {
        return false;
    }

    public void onShowPress(MotionEvent arg0) {
    }

    public boolean onSingleTapUp(MotionEvent arg0) {
        reset();
        return false;
    }
}
