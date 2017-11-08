package com.android.server.input;

import android.app.ActivityManager;
import android.app.IActivityController;
import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings.Global;
import android.provider.Settings.Secure;
import android.provider.Settings.System;
import android.service.dreams.DreamManagerInternal;
import android.util.Log;
import android.util.Slog;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.WindowManagerPolicy;
import com.android.server.DisplayThread;
import com.android.server.LocalServices;
import com.android.server.PPPOEStateMachine;
import com.android.server.am.ActivityRecord;
import com.android.server.rms.iaware.hiber.constant.AppHibernateCst;
import com.android.server.wm.WindowState;
import com.huawei.android.os.BuildEx.VERSION;
import com.huawei.pgmng.IPGPlugCallbacks;
import com.huawei.pgmng.PGAction;
import com.huawei.pgmng.PGPlug;
import huawei.android.os.HwGeneralManager;
import huawei.com.android.server.policy.HwGlobalActionsData;
import java.util.ArrayList;
import java.util.List;

public class HwInputManagerService extends InputManagerService {
    private static final String App_Left = "pressure_launch_app_left";
    private static final String App_Right = "pressure_launch_app_right";
    static final boolean ENABLE_BACK_TO_HOME = true;
    static final boolean ENABLE_LOCK_DEVICE = false;
    static final String FINGERPRINT_ANSWER_CALL = "fp_answer_call";
    static final String FINGERPRINT_BACK_TO_HOME = "fp_return_desk";
    static final String FINGERPRINT_CAMERA_SWITCH = "fp_take_photo";
    static final String FINGERPRINT_GALLERY_SLIDE = "fingerprint_gallery_slide";
    static final String FINGERPRINT_GO_BACK = "fp_go_back";
    static final String FINGERPRINT_LOCK_DEVICE = "fp_lock_device";
    static final String FINGERPRINT_MARKET_DEMO_SWITCH = "fingerprint_market_demo_switch";
    static final String FINGERPRINT_RECENT_APP = "fp_recent_application";
    static final String FINGERPRINT_SHOW_NOTIFICATION = "fp_show_notification";
    static final String FINGERPRINT_SLIDE_SWITCH = "fingerprint_slide_switch";
    static final String FINGERPRINT_STOP_ALARM = "fp_stop_alarm";
    private static final boolean FRONT_FINGERPRINT_NAVIGATION = SystemProperties.getBoolean("ro.config.hw_front_fp_navi", false);
    private static final int GOHOME_TIMEOUT_DELAY = 15000;
    private static final int HOME_TRACE_TIME_WINDOW = 3000;
    private static final int HT_STATE_DISABLE = 1;
    private static final int HT_STATE_GOING_HOME = 5;
    private static final int HT_STATE_IDLE = 3;
    private static final int HT_STATE_NO_INIT = 2;
    private static final int HT_STATE_PRE_GOING_HOME = 4;
    private static final int HT_STATE_UNKOWN = 0;
    private static final boolean HW_DEBUG = false;
    private static final boolean IS_CHINA_AREA = "CN".equalsIgnoreCase(SystemProperties.get("ro.product.locale.region", AppHibernateCst.INVALID_PKG));
    private static final int MIN_HOME_KEY_CLICK_COUNT = 2;
    private static final int MODAL_WINDOW_MASK = 40;
    private boolean IS_SUPPORT_PRESSURE = false;
    private String TAGPRESSURE = "pressure:hwInputMS";
    private boolean isSupportPg = true;
    boolean mAnswerCall = false;
    private ContentObserver mAppLeftStartObserver;
    private ContentObserver mAppRightStartObserver;
    boolean mBackToHome = false;
    private InputWindowHandle mCurFocusedWindowHandle = null;
    int mCurrentUserId = 0;
    private IActivityController mCustomController = null;
    private DreamManagerInternal mDreamManagerInternal = null;
    FingerPressNavigation mFingerPressNavi = null;
    boolean mFingerprintMarketDemoSwitch = false;
    private FingerprintNavigation mFingerprintNavigationFilter = new FingerprintNavigation(this.mContext);
    private Object mFreezeDetectLock = new Object();
    boolean mGallerySlide;
    boolean mGoBack = false;
    HwGuiShou mGuiShou;
    private Handler mHandler;
    boolean mHasReadDB = false;
    private int mHeight;
    private ComponentName mHomeComponent = null;
    private Handler mHomeTraceHandler = null;
    private int mHomeTraceState = 0;
    boolean mInjectCamera = false;
    private InputWindowHandle mLastFocusedWindowHandle = null;
    private boolean mLastImmersiveMode = false;
    private ContentObserver mMinNaviObserver;
    private boolean mModalWindowOnTop = false;
    private ContentObserver mNaviBarPosObserver;
    private PGPlug mPGPlug;
    private PgEventProcesser mPgEventProcesser = new PgEventProcesser();
    private WindowManagerPolicy mPolicy = null;
    private ContentObserver mPressLimitObserver;
    private boolean mPressNaviEnable = false;
    private ContentObserver mPressNaviObserver;
    boolean mRecentApp = false;
    private ArrayList<Long> mRecentHomeKeyTimes = new ArrayList();
    private final ContentResolver mResolver;
    final SettingsObserver mSettingsObserver;
    boolean mShowNotification = false;
    boolean mStopAlarm = false;
    private int mWidth;
    private Runnable mWindowSwitchChecker = new Runnable() {
        public void run() {
            HwInputManagerService.this.handleGoHomeTimeout();
        }
    };
    private String needTip = "pressure_needTip";

    private class BroadcastThread extends Thread {
        boolean mMinNaviBar;

        private BroadcastThread() {
        }

        public void setMiniNaviBar(boolean minNaviBar) {
            this.mMinNaviBar = minNaviBar;
        }

        public void run() {
            Log.d(HwInputManagerService.this.TAGPRESSURE, "sendBroadcast minNaviBar = " + this.mMinNaviBar);
            Intent intent = new Intent("com.huawei.navigationbar.statuschange");
            intent.putExtra("minNavigationBar", this.mMinNaviBar);
            HwInputManagerService.this.mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
        }
    }

    public final class HwInputManagerServiceInternal {
        public void notifyHomeLaunching() {
            HwInputManagerService.this.startTraceHomeKey();
        }

        public void setCustomActivityController(IActivityController controller) {
            HwInputManagerService.this.setCustomActivityControllerInternal(controller);
        }
    }

    private class PgEventProcesser implements IPGPlugCallbacks {
        private PgEventProcesser() {
        }

        public void onDaemonConnected() {
        }

        public void onConnectedTimeout() {
        }

        public boolean onEvent(int actionID, String msg) {
            if (PGAction.checkActionType(actionID) == 1 && PGAction.checkActionFlag(actionID) == 3) {
                if (actionID == 10002 || actionID == 10011) {
                    if (HwInputManagerService.this.mFingerPressNavi != null) {
                        HwInputManagerService.this.mFingerPressNavi.setGameScene(true);
                    }
                } else if (HwInputManagerService.this.mFingerPressNavi != null) {
                    HwInputManagerService.this.mFingerPressNavi.setGameScene(false);
                }
            }
            return true;
        }
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
            registerContentObserver(UserHandle.myUserId());
        }

        public void registerContentObserver(int userId) {
            if (!HwGuiShou.isGuiShouEnabled()) {
                HwInputManagerService.this.mResolver.registerContentObserver(Secure.getUriFor(HwInputManagerService.FINGERPRINT_SLIDE_SWITCH), false, this, userId);
            }
            HwInputManagerService.this.mResolver.registerContentObserver(Secure.getUriFor(HwInputManagerService.FINGERPRINT_CAMERA_SWITCH), false, this, userId);
            HwInputManagerService.this.mResolver.registerContentObserver(Secure.getUriFor(HwInputManagerService.FINGERPRINT_ANSWER_CALL), false, this, userId);
            HwInputManagerService.this.mResolver.registerContentObserver(Secure.getUriFor(HwInputManagerService.FINGERPRINT_SHOW_NOTIFICATION), false, this, userId);
            HwInputManagerService.this.mResolver.registerContentObserver(Secure.getUriFor(HwInputManagerService.FINGERPRINT_BACK_TO_HOME), false, this);
            HwInputManagerService.this.mResolver.registerContentObserver(Secure.getUriFor(HwInputManagerService.FINGERPRINT_STOP_ALARM), false, this, userId);
            HwInputManagerService.this.mResolver.registerContentObserver(Secure.getUriFor(HwInputManagerService.FINGERPRINT_LOCK_DEVICE), false, this);
            HwInputManagerService.this.mResolver.registerContentObserver(Secure.getUriFor(HwInputManagerService.FINGERPRINT_GO_BACK), false, this);
            HwInputManagerService.this.mResolver.registerContentObserver(Secure.getUriFor(HwInputManagerService.FINGERPRINT_RECENT_APP), false, this);
            HwInputManagerService.this.mResolver.registerContentObserver(System.getUriFor(HwInputManagerService.FINGERPRINT_MARKET_DEMO_SWITCH), false, this);
            HwInputManagerService.this.mResolver.registerContentObserver(Secure.getUriFor(HwInputManagerService.FINGERPRINT_GALLERY_SLIDE), false, this, userId);
        }

        public void onChange(boolean selfChange) {
            HwInputManagerService hwInputManagerService;
            boolean z;
            Slog.d("InputManager", "SettingDB has Changed");
            if (!HwGuiShou.isGuiShouEnabled()) {
                hwInputManagerService = HwInputManagerService.this;
                if (Secure.getIntForUser(HwInputManagerService.this.mResolver, HwInputManagerService.FINGERPRINT_CAMERA_SWITCH, 1, ActivityManager.getCurrentUser()) != 0) {
                    z = true;
                } else {
                    z = false;
                }
                hwInputManagerService.mInjectCamera = z;
                hwInputManagerService = HwInputManagerService.this;
                if (Secure.getIntForUser(HwInputManagerService.this.mResolver, HwInputManagerService.FINGERPRINT_ANSWER_CALL, 0, ActivityManager.getCurrentUser()) != 0) {
                    z = true;
                } else {
                    z = false;
                }
                hwInputManagerService.mAnswerCall = z;
                hwInputManagerService = HwInputManagerService.this;
                if (Secure.getIntForUser(HwInputManagerService.this.mResolver, HwInputManagerService.FINGERPRINT_STOP_ALARM, 0, ActivityManager.getCurrentUser()) != 0) {
                    z = true;
                } else {
                    z = false;
                }
                hwInputManagerService.mStopAlarm = z;
            }
            hwInputManagerService = HwInputManagerService.this;
            if (Secure.getIntForUser(HwInputManagerService.this.mResolver, HwInputManagerService.FINGERPRINT_SHOW_NOTIFICATION, 0, ActivityManager.getCurrentUser()) != 0) {
                z = true;
            } else {
                z = false;
            }
            hwInputManagerService.mShowNotification = z;
            hwInputManagerService = HwInputManagerService.this;
            if (Secure.getInt(HwInputManagerService.this.mResolver, HwInputManagerService.FINGERPRINT_BACK_TO_HOME, 0) != 0) {
                z = true;
            } else {
                z = false;
            }
            hwInputManagerService.mBackToHome = z;
            hwInputManagerService = HwInputManagerService.this;
            if (Secure.getInt(HwInputManagerService.this.mResolver, HwInputManagerService.FINGERPRINT_GO_BACK, 0) != 0) {
                z = true;
            } else {
                z = false;
            }
            hwInputManagerService.mGoBack = z;
            hwInputManagerService = HwInputManagerService.this;
            if (Secure.getInt(HwInputManagerService.this.mResolver, HwInputManagerService.FINGERPRINT_RECENT_APP, 0) != 0) {
                z = true;
            } else {
                z = false;
            }
            hwInputManagerService.mRecentApp = z;
            hwInputManagerService = HwInputManagerService.this;
            if (System.getInt(HwInputManagerService.this.mResolver, HwInputManagerService.FINGERPRINT_MARKET_DEMO_SWITCH, 0) == 1) {
                z = true;
            } else {
                z = false;
            }
            hwInputManagerService.mFingerprintMarketDemoSwitch = z;
            hwInputManagerService = HwInputManagerService.this;
            if (Secure.getIntForUser(HwInputManagerService.this.mResolver, HwInputManagerService.FINGERPRINT_GALLERY_SLIDE, 1, ActivityManager.getCurrentUser()) != 0) {
                z = true;
            } else {
                z = false;
            }
            hwInputManagerService.mGallerySlide = z;
            if (HwInputManagerService.this.mShowNotification || HwInputManagerService.this.mBackToHome || HwInputManagerService.this.mGoBack || HwInputManagerService.this.mRecentApp || HwInputManagerService.this.mFingerprintMarketDemoSwitch || HwInputManagerService.this.mGallerySlide || !HwGuiShou.isFPNavigationInThreeAppsEnabled(HwInputManagerService.this.mInjectCamera, HwInputManagerService.this.mAnswerCall, HwInputManagerService.this.mStopAlarm) || HwInputManagerService.FRONT_FINGERPRINT_NAVIGATION) {
                Slog.d("InputManager", "open fingerprint nav");
                System.putInt(HwInputManagerService.this.mResolver, HwInputManagerService.FINGERPRINT_SLIDE_SWITCH, 1);
                HwInputManagerService.this.mGuiShou.setFingerprintSlideSwitchValue(true);
                return;
            }
            Slog.d("InputManager", "close fingerprint nav");
            System.putInt(HwInputManagerService.this.mResolver, HwInputManagerService.FINGERPRINT_SLIDE_SWITCH, 0);
            HwInputManagerService.this.mGuiShou.setFingerprintSlideSwitchValue(false);
        }
    }

    public HwInputManagerService(Context context, Handler handler) {
        super(context);
        this.mResolver = context.getContentResolver();
        this.mSettingsObserver = new SettingsObserver(handler);
        this.mHandler = handler;
        this.mGuiShou = new HwGuiShou(this, context, handler, this.mFingerprintNavigationFilter);
        if (!this.mHasReadDB) {
            boolean z;
            this.mHasReadDB = true;
            HwGuiShou hwGuiShou = this.mGuiShou;
            if (!HwGuiShou.isGuiShouEnabled()) {
                this.mInjectCamera = Secure.getIntForUser(this.mResolver, FINGERPRINT_CAMERA_SWITCH, 1, ActivityManager.getCurrentUser()) != 0;
                if (Secure.getIntForUser(this.mResolver, FINGERPRINT_ANSWER_CALL, 0, ActivityManager.getCurrentUser()) != 0) {
                    z = true;
                } else {
                    z = false;
                }
                this.mAnswerCall = z;
                if (Secure.getIntForUser(this.mResolver, FINGERPRINT_STOP_ALARM, 0, ActivityManager.getCurrentUser()) != 0) {
                    z = true;
                } else {
                    z = false;
                }
                this.mStopAlarm = z;
            }
            if (Secure.getIntForUser(this.mResolver, FINGERPRINT_SHOW_NOTIFICATION, 0, ActivityManager.getCurrentUser()) != 0) {
                z = true;
            } else {
                z = false;
            }
            this.mShowNotification = z;
            if (Secure.getIntForUser(this.mResolver, FINGERPRINT_BACK_TO_HOME, 0, ActivityManager.getCurrentUser()) != 0) {
                z = true;
            } else {
                z = false;
            }
            this.mBackToHome = z;
            if (Secure.getIntForUser(this.mResolver, FINGERPRINT_GO_BACK, 0, ActivityManager.getCurrentUser()) != 0) {
                z = true;
            } else {
                z = false;
            }
            this.mGoBack = z;
            if (Secure.getIntForUser(this.mResolver, FINGERPRINT_RECENT_APP, 0, ActivityManager.getCurrentUser()) != 0) {
                z = true;
            } else {
                z = false;
            }
            this.mRecentApp = z;
            if (System.getInt(this.mResolver, FINGERPRINT_MARKET_DEMO_SWITCH, 0) == 1) {
                z = true;
            } else {
                z = false;
            }
            this.mFingerprintMarketDemoSwitch = z;
            if (Secure.getIntForUser(this.mResolver, FINGERPRINT_GALLERY_SLIDE, 1, ActivityManager.getCurrentUser()) != 0) {
                z = true;
            } else {
                z = false;
            }
            this.mGallerySlide = z;
            if (!(this.mShowNotification || this.mBackToHome || this.mGoBack || this.mRecentApp || this.mFingerprintMarketDemoSwitch || this.mGallerySlide)) {
                hwGuiShou = this.mGuiShou;
                if (HwGuiShou.isFPNavigationInThreeAppsEnabled(this.mInjectCamera, this.mAnswerCall, this.mStopAlarm) && !FRONT_FINGERPRINT_NAVIGATION) {
                    Slog.d("InputManager", "close fingerprint nav");
                    System.putInt(this.mResolver, FINGERPRINT_SLIDE_SWITCH, 0);
                    this.mGuiShou.setFingerprintSlideSwitchValue(false);
                    this.mGuiShou.registerPhoneStateListener();
                }
            }
            Slog.d("InputManager", "open fingerprint nav");
            System.putInt(this.mResolver, FINGERPRINT_SLIDE_SWITCH, 1);
            this.mGuiShou.setFingerprintSlideSwitchValue(true);
            this.mGuiShou.registerPhoneStateListener();
        }
        this.mGuiShou.registerFpSlideSwitchSettingsObserver();
        this.mCurrentUserId = ActivityManager.getCurrentUser();
    }

    private boolean isPressureModeOpenForChina() {
        return 1 == System.getIntForUser(this.mResolver, "virtual_notification_key_type", 0, this.mCurrentUserId);
    }

    private boolean isPressureModeOpenForOther() {
        return 1 == System.getIntForUser(this.mResolver, "virtual_notification_key_type", 0, this.mCurrentUserId);
    }

    private boolean isNavigationBarMin() {
        return 1 == Global.getInt(this.mResolver, "navigationbar_is_min", 0);
    }

    private void initObserver(Handler handler) {
        this.mPressNaviObserver = new ContentObserver(handler) {
            public void onChange(boolean selfChange) {
                HwInputManagerService.this.handleNaviChangeForTip();
                if (HwInputManagerService.IS_CHINA_AREA) {
                    HwInputManagerService.this.handleNaviChangeForChina();
                } else {
                    HwInputManagerService.this.handleNaviChangeForOther();
                }
            }
        };
        this.mMinNaviObserver = new ContentObserver(handler) {
            public void onChange(boolean selfChange) {
                if (HwInputManagerService.IS_CHINA_AREA) {
                    HwInputManagerService.this.handleNaviChangeForChina();
                }
            }
        };
        this.mPressLimitObserver = new ContentObserver(handler) {
            public void onChange(boolean selfChange) {
                float limit = System.getFloatForUser(HwInputManagerService.this.mContext.getContentResolver(), "pressure_habit_threshold", 0.2f, ActivityManager.getCurrentUser());
                Slog.d(HwInputManagerService.this.TAGPRESSURE, "mPressLimitObserver onChange open limit = " + limit);
                if (HwInputManagerService.this.mFingerPressNavi != null) {
                    HwInputManagerService.this.mFingerPressNavi.setPressureLimit(limit);
                }
            }
        };
        this.mNaviBarPosObserver = new ContentObserver(handler) {
            public void onChange(boolean selfChange) {
                int naviBarPos = System.getIntForUser(HwInputManagerService.this.mContext.getContentResolver(), "virtual_key_type", 0, ActivityManager.getCurrentUser());
                Slog.d(HwInputManagerService.this.TAGPRESSURE, "mPressNaviObserver onChange open naviBarPos = " + naviBarPos);
                if (HwInputManagerService.this.mFingerPressNavi != null) {
                    HwInputManagerService.this.mFingerPressNavi.setNaviBarPosition(naviBarPos);
                }
            }
        };
        this.mAppLeftStartObserver = new ContentObserver(handler) {
            public void onChange(boolean selfChange) {
                String appLeftPkg = System.getStringForUser(HwInputManagerService.this.mContext.getContentResolver(), HwInputManagerService.App_Left, ActivityManager.getCurrentUser());
                Slog.d(HwInputManagerService.this.TAGPRESSURE, "AppStartObserver onChange open appLeftPkg = " + appLeftPkg);
                if (HwInputManagerService.this.mFingerPressNavi != null && appLeftPkg != null) {
                    HwInputManagerService.this.mFingerPressNavi.setAppLeftPkg(appLeftPkg);
                }
            }
        };
        this.mAppRightStartObserver = new ContentObserver(handler) {
            public void onChange(boolean selfChange) {
                String appRightPkg = System.getStringForUser(HwInputManagerService.this.mContext.getContentResolver(), HwInputManagerService.App_Right, ActivityManager.getCurrentUser());
                Slog.d(HwInputManagerService.this.TAGPRESSURE, "AppStartObserver onChange open appRightPkg = " + appRightPkg);
                if (HwInputManagerService.this.mFingerPressNavi != null && appRightPkg != null) {
                    HwInputManagerService.this.mFingerPressNavi.setAppRightPkg(appRightPkg);
                }
            }
        };
        this.mResolver.registerContentObserver(System.getUriFor("virtual_notification_key_type"), false, this.mPressNaviObserver, -1);
        this.mResolver.registerContentObserver(Global.getUriFor("navigationbar_is_min"), false, this.mMinNaviObserver, -1);
        this.mResolver.registerContentObserver(System.getUriFor("pressure_habit_threshold"), false, this.mPressLimitObserver, -1);
        this.mResolver.registerContentObserver(System.getUriFor("virtual_key_type"), false, this.mNaviBarPosObserver, -1);
        this.mResolver.registerContentObserver(System.getUriFor(App_Left), false, this.mAppLeftStartObserver, -1);
        this.mResolver.registerContentObserver(System.getUriFor(App_Right), false, this.mAppRightStartObserver, -1);
    }

    private void handleNaviChangeForChina() {
        if (this.IS_SUPPORT_PRESSURE) {
            if (isPressureModeOpenForChina() && isNavigationBarMin()) {
                Slog.d(this.TAGPRESSURE, "mPressNaviObserver onChange open");
                this.mPressNaviEnable = true;
                this.mFingerPressNavi = FingerPressNavigation.getInstance(this.mContext);
                if (this.mFingerPressNavi != null) {
                    this.mFingerPressNavi.createPointerCircleAnimation();
                    this.mFingerPressNavi.setDisplayWidthAndHeight(this.mWidth, this.mHeight);
                    this.mFingerPressNavi.setMode(1);
                }
                SystemProperties.set("persist.sys.fingerpressnavi", PPPOEStateMachine.PHASE_INITIALIZE);
            } else {
                Slog.d(this.TAGPRESSURE, "mPressNaviObserver onChange close");
                this.mPressNaviEnable = false;
                this.mFingerPressNavi = FingerPressNavigation.getInstance(this.mContext);
                if (this.mFingerPressNavi != null) {
                    this.mFingerPressNavi.destoryPointerCircleAnimation();
                    this.mFingerPressNavi.setMode(0);
                }
                SystemProperties.set("persist.sys.fingerpressnavi", PPPOEStateMachine.PHASE_INITIALIZE);
            }
            int naviBarPos = System.getIntForUser(this.mContext.getContentResolver(), "virtual_key_type", 0, this.mCurrentUserId);
            if (this.mFingerPressNavi != null) {
                this.mFingerPressNavi.setNaviBarPosition(naviBarPos);
            }
        }
    }

    private void handleNaviChangeForOther() {
        if (this.IS_SUPPORT_PRESSURE) {
            if (isPressureModeOpenForOther()) {
                Slog.d(this.TAGPRESSURE, "mPressNaviObserver onChange open");
                this.mPressNaviEnable = true;
                this.mFingerPressNavi = FingerPressNavigation.getInstance(this.mContext);
                if (this.mFingerPressNavi != null) {
                    this.mFingerPressNavi.createPointerCircleAnimation();
                    this.mFingerPressNavi.setDisplayWidthAndHeight(this.mWidth, this.mHeight);
                    this.mFingerPressNavi.setMode(1);
                }
                sendBroadcast(true);
                SystemProperties.set("persist.sys.fingerpressnavi", PPPOEStateMachine.PHASE_INITIALIZE);
            } else {
                Slog.d(this.TAGPRESSURE, "mPressNaviObserver onChange close");
                this.mPressNaviEnable = false;
                this.mFingerPressNavi = FingerPressNavigation.getInstance(this.mContext);
                if (this.mFingerPressNavi != null) {
                    this.mFingerPressNavi.destoryPointerCircleAnimation();
                    this.mFingerPressNavi.setMode(0);
                }
                sendBroadcast(false);
                SystemProperties.set("persist.sys.fingerpressnavi", PPPOEStateMachine.PHASE_INITIALIZE);
            }
            int naviBarPos = System.getIntForUser(this.mContext.getContentResolver(), "virtual_key_type", 0, this.mCurrentUserId);
            if (this.mFingerPressNavi != null) {
                this.mFingerPressNavi.setNaviBarPosition(naviBarPos);
            }
        }
    }

    private void sendBroadcast(boolean minNaviBar) {
        BroadcastThread sendthread = new BroadcastThread();
        sendthread.setMiniNaviBar(minNaviBar);
        sendthread.start();
    }

    private void pressureinit() {
        float limit;
        int naviBarPos;
        String appLeftPkg;
        String appRightPkg;
        if (this.mPressNaviEnable) {
            Log.v(this.TAGPRESSURE, "systemRunning  mPressNaviEnable = " + this.mPressNaviEnable);
            this.mFingerPressNavi = FingerPressNavigation.getInstance(this.mContext);
            if (this.mFingerPressNavi != null) {
                this.mFingerPressNavi.createPointerCircleAnimation();
                limit = System.getFloatForUser(this.mContext.getContentResolver(), "pressure_habit_threshold", 0.2f, this.mCurrentUserId);
                naviBarPos = System.getIntForUser(this.mContext.getContentResolver(), "virtual_key_type", 0, this.mCurrentUserId);
                appLeftPkg = System.getStringForUser(this.mContext.getContentResolver(), App_Left, this.mCurrentUserId);
                if (appLeftPkg != null) {
                    this.mFingerPressNavi.setAppLeftPkg(appLeftPkg);
                } else {
                    this.mFingerPressNavi.setAppLeftPkg("none_app");
                }
                appRightPkg = System.getStringForUser(this.mContext.getContentResolver(), App_Right, this.mCurrentUserId);
                if (appRightPkg != null) {
                    this.mFingerPressNavi.setAppRightPkg(appRightPkg);
                } else {
                    this.mFingerPressNavi.setAppRightPkg("none_app");
                }
                this.mFingerPressNavi.setNaviBarPosition(naviBarPos);
                this.mFingerPressNavi.setPressureLimit(limit);
                this.mFingerPressNavi.setMode(1);
                SystemProperties.set("persist.sys.fingerpressnavi", PPPOEStateMachine.PHASE_INITIALIZE);
            }
        } else if (this.IS_SUPPORT_PRESSURE) {
            this.mFingerPressNavi = FingerPressNavigation.getInstance(this.mContext);
            if (this.mFingerPressNavi != null) {
                limit = System.getFloatForUser(this.mContext.getContentResolver(), "pressure_habit_threshold", 0.2f, this.mCurrentUserId);
                naviBarPos = System.getIntForUser(this.mContext.getContentResolver(), "virtual_key_type", 0, this.mCurrentUserId);
                appLeftPkg = System.getStringForUser(this.mContext.getContentResolver(), App_Left, this.mCurrentUserId);
                if (appLeftPkg != null) {
                    this.mFingerPressNavi.setAppLeftPkg(appLeftPkg);
                } else {
                    this.mFingerPressNavi.setAppLeftPkg("none_app");
                }
                appRightPkg = System.getStringForUser(this.mContext.getContentResolver(), App_Right, this.mCurrentUserId);
                if (appRightPkg != null) {
                    this.mFingerPressNavi.setAppRightPkg(appRightPkg);
                } else {
                    this.mFingerPressNavi.setAppRightPkg("none_app");
                }
                this.mFingerPressNavi.setNaviBarPosition(naviBarPos);
                this.mFingerPressNavi.setPressureLimit(limit);
                this.mFingerPressNavi.setMode(0);
                Log.v(this.TAGPRESSURE, "systemRunning  mPressNaviEnable = " + this.mPressNaviEnable);
                SystemProperties.set("persist.sys.fingerpressnavi", PPPOEStateMachine.PHASE_INITIALIZE);
            }
        } else {
            SystemProperties.set("persist.sys.fingerpressnavi", PPPOEStateMachine.PHASE_DEAD);
        }
    }

    private int isNeedTip() {
        return System.getIntForUser(this.mContext.getContentResolver(), this.needTip, 0, this.mCurrentUserId);
    }

    private void handleNaviChangeForTip() {
        if (isPressureModeOpenForChina()) {
            System.putIntForUser(this.mContext.getContentResolver(), this.needTip, 1, this.mCurrentUserId);
            this.mFingerPressNavi = FingerPressNavigation.getInstance(this.mContext);
            if (this.mFingerPressNavi != null) {
                this.mFingerPressNavi.setNeedTip(true);
                return;
            }
            return;
        }
        System.putIntForUser(this.mContext.getContentResolver(), this.needTip, 2, this.mCurrentUserId);
        this.mFingerPressNavi = FingerPressNavigation.getInstance(this.mContext);
        if (this.mFingerPressNavi != null) {
            this.mFingerPressNavi.setNeedTip(false);
        }
    }

    private void pressureinitData() {
        if (this.IS_SUPPORT_PRESSURE) {
            this.mFingerPressNavi = FingerPressNavigation.getInstance(this.mContext);
            if (this.mFingerPressNavi != null) {
                int istip = isNeedTip();
                if (istip == 0) {
                    System.putIntForUser(this.mContext.getContentResolver(), this.needTip, 1, this.mCurrentUserId);
                    this.mFingerPressNavi.setNeedTip(true);
                } else if (istip == 3) {
                    System.putIntForUser(this.mContext.getContentResolver(), this.needTip, 2, this.mCurrentUserId);
                    this.mFingerPressNavi.setNeedTip(false);
                } else if (istip == 1) {
                    this.mFingerPressNavi.setNeedTip(true);
                } else if (istip == 2) {
                    this.mFingerPressNavi.setNeedTip(false);
                }
            }
        }
    }

    public void setDisplayWidthAndHeight(int width, int height) {
        this.mWidth = width;
        this.mHeight = height;
        if (this.mFingerPressNavi != null) {
            this.mFingerPressNavi.setDisplayWidthAndHeight(width, height);
        }
    }

    public void setCurFocusWindow(WindowState focus) {
        if (this.mFingerPressNavi != null) {
            this.mFingerPressNavi.setCurFocusWindow(focus);
        }
    }

    public void setIsTopFullScreen(boolean isTopFullScreen) {
        if (this.mFingerPressNavi != null) {
            this.mFingerPressNavi.setIsTopFullScreen(isTopFullScreen);
        }
    }

    public void setImmersiveMode(boolean mode) {
        if (this.IS_SUPPORT_PRESSURE) {
            if (this.mFingerPressNavi != null) {
                this.mFingerPressNavi.setImmersiveMode(mode);
            }
            if (!(IS_CHINA_AREA || mode || !this.mLastImmersiveMode)) {
                Slog.d("InputManager", "setImmersiveMode  has Changedi mode = " + mode);
                handleNaviChangeForOther();
            }
            this.mLastImmersiveMode = mode;
        }
    }

    public void systemRunning() {
        super.systemRunning();
        this.IS_SUPPORT_PRESSURE = HwGeneralManager.getInstance().isSupportForce();
        if (IS_CHINA_AREA) {
            if (this.IS_SUPPORT_PRESSURE && isPressureModeOpenForChina() && isNavigationBarMin()) {
                this.mPressNaviEnable = true;
            }
        } else if (this.IS_SUPPORT_PRESSURE && isPressureModeOpenForOther()) {
            this.mPressNaviEnable = true;
        }
        if (this.IS_SUPPORT_PRESSURE) {
            initObserver(this.mHandler);
        }
        this.mFingerprintNavigationFilter.systemRunning();
        pressureinit();
        pressureinitData();
        if (this.isSupportPg) {
            initPgPlugThread();
        }
        if (isHomeTraceSupported()) {
            this.mHomeTraceState = 2;
        } else {
            this.mHomeTraceState = 1;
        }
    }

    public void start() {
        super.start();
        this.mContext.registerReceiver(new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                HwInputManagerService.this.mCurrentUserId = intent.getIntExtra("android.intent.extra.user_handle", 0);
                Slog.e(HwInputManagerService.this.TAGPRESSURE, "user switch mCurrentUserId = " + HwInputManagerService.this.mCurrentUserId);
                if (HwInputManagerService.this.IS_SUPPORT_PRESSURE) {
                    HwInputManagerService.this.pressureinit();
                    HwInputManagerService.this.pressureinitData();
                    if (HwInputManagerService.IS_CHINA_AREA) {
                        HwInputManagerService.this.handleNaviChangeForChina();
                    } else {
                        HwInputManagerService.this.handleNaviChangeForOther();
                    }
                }
            }
        }, new IntentFilter("android.intent.action.USER_SWITCHED"), null, this.mHandler);
    }

    private void initPgPlugThread() {
        this.mPGPlug = new PGPlug(this.mPgEventProcesser, "InputManager");
        new Thread(this.mPGPlug, "InputManager").start();
    }

    boolean filterInputEvent(InputEvent event, int policyFlags) {
        if (this.mFingerprintNavigationFilter.filterInputEvent(event, policyFlags)) {
            return false;
        }
        if (this.mFingerPressNavi == null || this.mFingerPressNavi.filterPressueInputEvent(event)) {
            return super.filterInputEvent(event, policyFlags);
        }
        return false;
    }

    boolean isFingprintEventUnHandled(KeyEvent event) {
        return (event.getHwFlags() & HwGlobalActionsData.FLAG_SILENTMODE_TRANSITING) != 0;
    }

    KeyEvent dispatchUnhandledKey(InputWindowHandle focus, KeyEvent event, int policyFlags) {
        if (!isFingprintEventUnHandled(event) || !this.mFingerprintNavigationFilter.dispatchUnhandledKey(event, policyFlags)) {
            return super.dispatchUnhandledKey(focus, event, policyFlags);
        }
        Slog.d("InputManager", "dispatchUnhandledKey flags=" + Integer.toHexString(event.getHwFlags()));
        return null;
    }

    public boolean isDefaultSlideSwitchOn() {
        if (this.mShowNotification || this.mBackToHome || this.mGoBack || this.mRecentApp || this.mFingerprintMarketDemoSwitch) {
            return true;
        }
        return this.mGallerySlide;
    }

    public void setCurrentUser(int newUserId, int[] currentProfileIds) {
        Slog.i("InputManager", "onUserSwitching, newUserId=" + newUserId);
        this.mGuiShou.registerFpSlideSwitchSettingsObserver(newUserId);
        this.mSettingsObserver.registerContentObserver(newUserId);
        this.mSettingsObserver.onChange(true);
        this.mFingerprintNavigationFilter.setCurrentUser(newUserId, currentProfileIds);
    }

    public void setInputWindows(InputWindowHandle[] windowHandles) {
        super.setInputWindows(windowHandles);
        checkHomeWindowResumed(windowHandles);
    }

    private boolean inSuperPowerSavingMode() {
        return SystemProperties.getBoolean("sys.super_power_save", false);
    }

    private boolean isHomeTraceSupported() {
        boolean z = true;
        if (!SystemProperties.get("ro.runmode", "normal").equals("normal") || SystemProperties.getInt("ro.logsystem.usertype", 1) != 3) {
            return false;
        }
        if (VERSION.EMUI_SDK_INT < 11) {
            z = false;
        }
        return z;
    }

    private void blockIfFreezeDetected() {
        synchronized (this.mFreezeDetectLock) {
            while (this.mHomeTraceState == 5) {
                try {
                    this.mFreezeDetectLock.wait();
                } catch (InterruptedException e) {
                }
            }
        }
    }

    private void notifyWindowSwitched() {
        synchronized (this.mFreezeDetectLock) {
            this.mHomeTraceState = 3;
            this.mFreezeDetectLock.notifyAll();
        }
    }

    private String getInputWindowInfo(InputWindowHandle windowHandle) {
        if (windowHandle == null) {
            return null;
        }
        return "windowHandle=" + windowHandle.name + ",windowHandle.InputApplicationHandle=" + (windowHandle.inputApplicationHandle != null ? windowHandle.inputApplicationHandle.name : "null");
    }

    private void handleGoHomeTimeout() {
        synchronized (this.mFreezeDetectLock) {
            Slog.e("InputManager", "Go home timeout, current focused window: " + getInputWindowInfo(this.mCurFocusedWindowHandle) + ", Preferred homeActivity = " + this.mHomeComponent);
            this.mHomeTraceState = 3;
        }
    }

    private boolean isHomeTraceEnabled() {
        if (this.mHomeTraceState == 2) {
            this.mHomeTraceState = initHomeTrace();
        }
        return this.mHomeTraceState >= 3;
    }

    private ComponentName getComponentName(InputWindowHandle windowHandle) {
        if (windowHandle == null) {
            Slog.i("InputManager", "non-home window: windowHandle is null");
            return null;
        }
        WindowState windowState = windowHandle.windowState;
        if (windowState == null) {
            Slog.i("InputManager", "non-home window: windowHandle's state is null");
            return null;
        }
        ActivityRecord r = ActivityRecord.forToken((IBinder) windowState.getAppToken());
        if (r != null) {
            return r.realActivity;
        }
        Slog.i("InputManager", "non-home window:Can not get ActivityRecord from token of windowHandle's windowState");
        return null;
    }

    private boolean isHomeWindow(ComponentName componentName) {
        boolean z = false;
        if (componentName == null) {
            return false;
        }
        String packageName = componentName.getPackageName();
        if (!(this.mHomeComponent == null || packageName == null)) {
            z = packageName.equals(this.mHomeComponent.getPackageName());
        }
        return z;
    }

    private boolean isHomeWindow(InputWindowHandle windowHandle) {
        if (getHomeActivity() == null) {
            return false;
        }
        return isHomeWindow(getComponentName(windowHandle));
    }

    private InputWindowHandle updateFocusedWindow(InputWindowHandle[] windowHandles) {
        synchronized (this.mFreezeDetectLock) {
            this.mCurFocusedWindowHandle = null;
            for (InputWindowHandle windowHandle : windowHandles) {
                if (!(windowHandle == null || !windowHandle.hasFocus || windowHandle.inputChannel == null)) {
                    this.mCurFocusedWindowHandle = windowHandle;
                }
            }
        }
        return this.mCurFocusedWindowHandle;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private boolean modalWindowOnTop() {
        boolean systemDialog = false;
        synchronized (this.mFreezeDetectLock) {
            if (this.mCurFocusedWindowHandle == null) {
                return false;
            }
            int curWindowType = this.mCurFocusedWindowHandle.layoutParamsType;
            int curLayoutParamsFlags = this.mCurFocusedWindowHandle.layoutParamsFlags;
        }
    }

    private boolean focusWindowChanged() {
        boolean z = false;
        synchronized (this.mFreezeDetectLock) {
            if (!(this.mLastFocusedWindowHandle == null || this.mCurFocusedWindowHandle == null || this.mLastFocusedWindowHandle == this.mCurFocusedWindowHandle)) {
                z = true;
            }
        }
        return z;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void checkHomeWindowResumed(InputWindowHandle[] windowHandles) {
        if (windowHandles != null && updateFocusedWindow(windowHandles) != null && isHomeTraceEnabled()) {
            if (modalWindowOnTop()) {
                finishTraceHomeKey();
            } else if (this.mHomeTraceHandler.hasCallbacks(this.mWindowSwitchChecker) && focusWindowChanged()) {
                synchronized (this.mFreezeDetectLock) {
                    Slog.i("InputManager", "Change focus window to " + this.mCurFocusedWindowHandle.name + " after clicked HOME key, freeze not detected");
                }
                finishTraceHomeKey();
            }
        }
    }

    private ComponentName getHomeActivity() {
        PackageManager pm = this.mContext.getPackageManager();
        if (pm == null) {
            Slog.e("InputManager", "getHomeActivity, can't get packagemanager");
            return null;
        }
        List<ResolveInfo> homeActivities = new ArrayList();
        ComponentName preferHomeActivity = pm.getHomeActivities(homeActivities);
        if (preferHomeActivity != null) {
            this.mHomeComponent = preferHomeActivity;
        } else if (homeActivities.size() == 0) {
            Slog.e("InputManager", "No home activity found!");
            return null;
        } else {
            ActivityInfo homeActivityInfo = ((ResolveInfo) homeActivities.get(0)).activityInfo;
            this.mHomeComponent = new ComponentName(homeActivityInfo.packageName, homeActivityInfo.name);
        }
        return this.mHomeComponent;
    }

    private int initHomeTrace() {
        int uiModeType = 0;
        UiModeManager uiModeManager = (UiModeManager) this.mContext.getSystemService("uimode");
        if (uiModeManager != null) {
            uiModeType = uiModeManager.getCurrentModeType();
        }
        if (uiModeType != 1) {
            Slog.e("InputManager", "current Ui mode is " + uiModeType + " not support to trace HOME!");
            return 1;
        } else if (getHomeActivity() == null) {
            Slog.e("InputManager", "do not trace HOME because no prefered homeActivity found!");
            return 1;
        } else {
            LocalServices.addService(HwInputManagerServiceInternal.class, new HwInputManagerServiceInternal());
            this.mDreamManagerInternal = (DreamManagerInternal) LocalServices.getService(DreamManagerInternal.class);
            this.mPolicy = (WindowManagerPolicy) LocalServices.getService(WindowManagerPolicy.class);
            this.mHomeTraceHandler = DisplayThread.getHandler();
            return 3;
        }
    }

    private boolean isUserSetupComplete() {
        return Secure.getIntForUser(this.mContext.getContentResolver(), "user_setup_complete", 0, -2) != 0;
    }

    private boolean ignoreHomeTrace() {
        if ((this.mDreamManagerInternal != null && this.mDreamManagerInternal.isDreaming()) || (this.mPolicy != null && this.mPolicy.isKeyguardLocked())) {
            return true;
        }
        if (!isUserSetupComplete()) {
            Slog.i("InputManager", "user setup not complete, skip the trace of HOME");
            return true;
        } else if (inSuperPowerSavingMode()) {
            Slog.i("InputManager", "in super power saving mode, skip the trace of HOME");
            return true;
        } else if (!this.mModalWindowOnTop) {
            return false;
        } else {
            Slog.i("InputManager", "Modal window on the top, skip the trace of HOME");
            return true;
        }
    }

    protected void finishTraceHomeKey() {
        this.mHomeTraceHandler.removeCallbacks(this.mWindowSwitchChecker);
        notifyWindowSwitched();
    }

    private boolean isHomeKeyUp(KeyEvent event) {
        return event.getKeyCode() == 3 && event.getAction() == 1 && !event.isCanceled();
    }

    protected void preStartTraceHome(InputWindowHandle focus, KeyEvent event) {
        if (focus != null && isHomeKeyUp(event)) {
            if (ActivityManager.isUserAMonkey() || hasCustomActivityController()) {
                Slog.d("InputManager", "trigger HOME in monkey mode or custom ActivityController set");
            } else if (isHomeTraceEnabled() && !ignoreHomeTrace() && !isHomeWindow(focus)) {
                long eventTime = event.getEventTime();
                this.mRecentHomeKeyTimes.add(Long.valueOf(eventTime));
                for (int i = this.mRecentHomeKeyTimes.size() - 1; i >= 0; i--) {
                    long interval = eventTime - ((Long) this.mRecentHomeKeyTimes.get(i)).longValue();
                    if (interval >= 0 && interval > 3000) {
                        this.mRecentHomeKeyTimes.remove(i);
                    }
                }
                if (this.mRecentHomeKeyTimes.size() < 2) {
                    Slog.i("InputManager", "click HOME " + this.mRecentHomeKeyTimes.size() + " times, trace if continuous click " + 2 + " times in " + HOME_TRACE_TIME_WINDOW + "ms");
                    return;
                }
                synchronized (this.mFreezeDetectLock) {
                    if (this.mHomeTraceState == 3) {
                        this.mHomeTraceState = 4;
                        this.mLastFocusedWindowHandle = focus;
                        Slog.i("InputManager", "start HOME in window:" + focus.name);
                    }
                }
            }
        }
    }

    protected void postStartTraceHome(InputWindowHandle focus) {
        synchronized (this.mFreezeDetectLock) {
            if (this.mHomeTraceState == 4) {
                Slog.i("InputManager", "Home app not launched at " + (focus != null ? focus.name : "null window"));
                this.mHomeTraceState = 3;
            }
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    protected boolean startTraceHomeKey() {
        synchronized (this.mFreezeDetectLock) {
            if (this.mHomeTraceState != 4) {
                return false;
            } else if (!this.mHomeTraceHandler.hasCallbacks(this.mWindowSwitchChecker)) {
                Slog.i("InputManager", "Launching HOME, keep checking if the window switch");
                this.mHomeTraceHandler.postDelayed(this.mWindowSwitchChecker, 15000);
                this.mHomeTraceState = 5;
            }
        }
    }

    private void setCustomActivityControllerInternal(IActivityController controller) {
        synchronized (this.mFreezeDetectLock) {
            this.mCustomController = controller;
        }
    }

    private boolean hasCustomActivityController() {
        boolean z;
        synchronized (this.mFreezeDetectLock) {
            z = this.mCustomController != null;
        }
        return z;
    }

    protected long interceptKeyBeforeDispatching(InputWindowHandle focus, KeyEvent event, int policyFlags) {
        preStartTraceHome(focus, event);
        long ret = super.interceptKeyBeforeDispatching(focus, event, policyFlags);
        postStartTraceHome(focus);
        return ret;
    }
}
