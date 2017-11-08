package com.android.server.policy;

import android.app.ActivityManager;
import android.app.ActivityManager.StackId;
import android.app.ActivityManagerInternal;
import android.app.ActivityManagerInternal.SleepToken;
import android.app.ActivityManagerNative;
import android.app.AppOpsManager;
import android.app.IProcessObserver;
import android.app.IUiModeManager;
import android.app.IUiModeManager.Stub;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.app.UiModeManager;
import android.common.HwFrameworkFactory;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.CompatibilityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.hardware.hdmi.HdmiControlManager;
import android.hardware.hdmi.HdmiPlaybackClient;
import android.hardware.hdmi.HdmiPlaybackClient.OneTouchPlayCallback;
import android.hardware.input.InputManagerInternal;
import android.hsm.HwSystemManager;
import android.hwcontrol.HwWidgetFactory;
import android.media.AudioAttributes;
import android.media.AudioAttributes.Builder;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.media.IAudioService;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.session.MediaSessionLegacyHelper;
import android.os.Binder;
import android.os.Bundle;
import android.os.FactoryTest;
import android.os.Handler;
import android.os.IAodStateCallback;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.IDeviceIdleController;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.PowerManagerInternal;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UEventObserver;
import android.os.UEventObserver.UEvent;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings.Global;
import android.provider.Settings.Secure;
import android.provider.Settings.System;
import android.service.dreams.DreamManagerInternal;
import android.service.dreams.IDreamManager;
import android.telecom.TelecomManager;
import android.util.EventLog;
import android.util.Flog;
import android.util.Jlog;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.MutableBoolean;
import android.util.Slog;
import android.util.SparseArray;
import android.view.Display;
import android.view.IApplicationToken;
import android.view.IWindowManager;
import android.view.InputChannel;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.InputEventReceiver.Factory;
import android.view.KeyCharacterMap;
import android.view.KeyCharacterMap.FallbackAction;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.WindowManager.BadTokenException;
import android.view.WindowManager.LayoutParams;
import android.view.WindowManagerInternal;
import android.view.WindowManagerPolicy;
import android.view.WindowManagerPolicy.InputConsumer;
import android.view.WindowManagerPolicy.KeyguardDismissDoneListener;
import android.view.WindowManagerPolicy.OnKeyguardExitResult;
import android.view.WindowManagerPolicy.ScreenOnListener;
import android.view.WindowManagerPolicy.WindowManagerFuncs;
import android.view.WindowManagerPolicy.WindowState;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import com.android.internal.R;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.policy.IShortcutService;
import com.android.internal.policy.PhoneWindow;
import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.util.ScreenShapeHelper;
import com.android.internal.widget.PointerLocationView;
import com.android.server.GestureLauncherService;
import com.android.server.LocalServices;
import com.android.server.audio.AudioService;
import com.android.server.job.controllers.JobStatus;
import com.android.server.policy.keyguard.KeyguardServiceDelegate;
import com.android.server.policy.keyguard.KeyguardServiceDelegate.DrawnListener;
import com.android.server.power.IHwShutdownThread;
import com.android.server.statusbar.StatusBarManagerInternal;
import com.android.server.wm.WindowManagerService.H;
import com.huawei.android.provider.SettingsEx.Systemex;
import huawei.cust.HwCustUtils;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;

public class PhoneWindowManager extends AbsPhoneWindowManager implements WindowManagerPolicy {
    private static final String ACTION_ACTURAL_SHUTDOWN = "com.android.internal.app.SHUTDOWNBROADCAST";
    static final int APPLICATION_ABOVE_SUB_PANEL_SUBLAYER = 3;
    static final int APPLICATION_MEDIA_OVERLAY_SUBLAYER = -1;
    static final int APPLICATION_MEDIA_SUBLAYER = -2;
    static final int APPLICATION_PANEL_SUBLAYER = 1;
    static final int APPLICATION_SUB_PANEL_SUBLAYER = 2;
    private static final int BRIGHTNESS_STEPS = 10;
    static final boolean DEBUG = false;
    static final boolean DEBUG_INPUT = false;
    static final boolean DEBUG_KEYGUARD = false;
    static final boolean DEBUG_LAYOUT = false;
    static final boolean DEBUG_STARTING_WINDOW = false;
    static final boolean DEBUG_WAKEUP = false;
    protected static final int DISMISS_KEYGUARD_CONTINUE = 2;
    protected static final int DISMISS_KEYGUARD_NONE = 0;
    protected static final int DISMISS_KEYGUARD_START = 1;
    static final int DOUBLE_TAP_HOME_NOTHING = 0;
    static final int DOUBLE_TAP_HOME_RECENT_SYSTEM_UI = 1;
    static final boolean ENABLE_DESK_DOCK_HOME_CAPTURE = false;
    static final boolean HISI_PERF_OPT = SystemProperties.getBoolean("build.hisi_perf_opt", false);
    private static final String HUAWEI_PRE_CAMERA_START_MODE = "com.huawei.RapidCapture";
    private static final String HUAWEI_SHUTDOWN_PERMISSION = "huawei.android.permission.HWSHUTDOWN";
    protected static final boolean HWFLOW;
    private static final boolean IS_lOCK_UNNATURAL_ORIENTATION = SystemProperties.getBoolean("ro.config.lock_land_screen", false);
    private static final float KEYGUARD_SCREENSHOT_CHORD_DELAY_MULTIPLIER = 2.5f;
    static final int LAST_LONG_PRESS_HOME_BEHAVIOR = 2;
    static final int LONG_PRESS_BACK_GO_TO_VOICE_ASSIST = 1;
    static final int LONG_PRESS_BACK_NOTHING = 0;
    static final int LONG_PRESS_HOME_ASSIST = 2;
    static final int LONG_PRESS_HOME_NOTHING = 0;
    static final int LONG_PRESS_HOME_RECENT_SYSTEM_UI = 1;
    static final int LONG_PRESS_POWER_GLOBAL_ACTIONS = 1;
    static final int LONG_PRESS_POWER_NOTHING = 0;
    static final int LONG_PRESS_POWER_SHUT_OFF = 2;
    static final int LONG_PRESS_POWER_SHUT_OFF_NO_CONFIRM = 3;
    private static final int MSG_BACK_LONG_PRESS = 18;
    private static final int MSG_DISABLE_POINTER_LOCATION = 2;
    private static final int MSG_DISPATCH_MEDIA_KEY_REPEAT_WITH_WAKE_LOCK = 4;
    private static final int MSG_DISPATCH_MEDIA_KEY_WITH_WAKE_LOCK = 3;
    private static final int MSG_DISPATCH_SHOW_GLOBAL_ACTIONS = 10;
    private static final int MSG_DISPATCH_SHOW_RECENTS = 9;
    private static final int MSG_DISPOSE_INPUT_CONSUMER = 19;
    private static final int MSG_ENABLE_POINTER_LOCATION = 1;
    private static final int MSG_HIDE_BOOT_MESSAGE = 11;
    private static final int MSG_KEYGUARD_DRAWN_COMPLETE = 5;
    private static final int MSG_KEYGUARD_DRAWN_TIMEOUT = 6;
    private static final int MSG_LAUNCH_VOICE_ASSIST_WITH_WAKE_LOCK = 12;
    private static final int MSG_POWER_DELAYED_PRESS = 13;
    private static final int MSG_POWER_LONG_PRESS = 14;
    private static final int MSG_REQUEST_TRANSIENT_BARS = 16;
    private static final int MSG_REQUEST_TRANSIENT_BARS_ARG_NAVIGATION = 1;
    private static final int MSG_REQUEST_TRANSIENT_BARS_ARG_STATUS = 0;
    private static final int MSG_SHOW_TV_PICTURE_IN_PICTURE_MENU = 17;
    private static final int MSG_UPDATE_DREAMING_SLEEP_TOKEN = 15;
    private static final int MSG_WINDOW_MANAGER_DRAWN_COMPLETE = 7;
    static final int MULTI_PRESS_POWER_BRIGHTNESS_BOOST = 2;
    static final int MULTI_PRESS_POWER_NOTHING = 0;
    static final int MULTI_PRESS_POWER_THEATER_MODE = 1;
    static final int NAV_BAR_OPAQUE_WHEN_FREEFORM_OR_DOCKED = 0;
    static final int NAV_BAR_TRANSLUCENT_WHEN_FREEFORM_OPAQUE_OTHERWISE = 1;
    private static final int NaviHide = 1;
    private static final int NaviInit = -1;
    private static final int NaviShow = 0;
    private static final int NaviTransientShow = 2;
    private static final long PANIC_GESTURE_EXPIRATION = 30000;
    static final boolean PRINT_ANIM = false;
    private static final long SCREENSHOT_CHORD_DEBOUNCE_DELAY_MILLIS = 150;
    private static final int SCREENSHOT_DELAY = 0;
    static final int SHORT_PRESS_POWER_GO_HOME = 4;
    static final int SHORT_PRESS_POWER_GO_TO_SLEEP = 1;
    static final int SHORT_PRESS_POWER_NOTHING = 0;
    static final int SHORT_PRESS_POWER_REALLY_GO_TO_SLEEP = 2;
    static final int SHORT_PRESS_POWER_REALLY_GO_TO_SLEEP_AND_GO_HOME = 3;
    static final int SHORT_PRESS_SLEEP_GO_TO_SLEEP = 0;
    static final int SHORT_PRESS_SLEEP_GO_TO_SLEEP_AND_GO_HOME = 1;
    static final int SHORT_PRESS_WINDOW_NOTHING = 0;
    static final int SHORT_PRESS_WINDOW_PICTURE_IN_PICTURE = 1;
    static final boolean SHOW_PROCESSES_ON_ALT_MENU = false;
    static final boolean SHOW_STARTING_ANIMATIONS = true;
    public static final int START_AOD_BOOT = 1;
    public static final int START_AOD_SCREEN_OFF = 3;
    public static final int START_AOD_SCREEN_ON = 2;
    public static final int START_AOD_TURNING_ON = 5;
    public static final int START_AOD_USER_SWITCHED = 6;
    public static final int START_AOD_WAKE_UP = 4;
    public static final String SYSTEM_DIALOG_REASON_ASSIST = "assist";
    public static final String SYSTEM_DIALOG_REASON_GLOBAL_ACTIONS = "globalactions";
    public static final String SYSTEM_DIALOG_REASON_HOME_KEY = "homekey";
    public static final String SYSTEM_DIALOG_REASON_KEY = "reason";
    public static final String SYSTEM_DIALOG_REASON_RECENT_APPS = "recentapps";
    static final int SYSTEM_UI_CHANGING_LAYOUT = -1073709042;
    private static final String SYSUI_PACKAGE = "com.android.systemui";
    private static final String SYSUI_SCREENSHOT_ERROR_RECEIVER = "com.android.systemui.screenshot.ScreenshotServiceErrorReceiver";
    private static final String SYSUI_SCREENSHOT_SERVICE = "com.android.systemui.screenshot.TakeScreenshotService";
    static final String TAG = "WindowManager";
    private static final AudioAttributes VIBRATION_ATTRIBUTES = new Builder().setContentType(4).setUsage(13).build();
    static final int WAITING_FOR_DRAWN_TIMEOUT = 1000;
    static final int WAITING_FOR_KEYGUARD_DISMISS_TIMEOUT = 300;
    private static final int[] WINDOW_TYPES_WHERE_HOME_DOESNT_WORK = new int[]{2003, 2010};
    private static boolean bHasFrontFp = SystemProperties.getBoolean("ro.config.hw_front_fp_navi", false);
    static final int deviceGlobalActionKeyTimeout = 1000;
    static final boolean localLOGV = false;
    private static final boolean mSupportAod = "1".equals(SystemProperties.get("ro.config.support_aod", null));
    static final Rect mTmpContentFrame = new Rect();
    static final Rect mTmpDecorFrame = new Rect();
    static final Rect mTmpDisplayFrame = new Rect();
    static final Rect mTmpNavigationFrame = new Rect();
    static final Rect mTmpOutsetFrame = new Rect();
    static final Rect mTmpOverscanFrame = new Rect();
    static final Rect mTmpParentFrame = new Rect();
    private static final Rect mTmpRect = new Rect();
    static final Rect mTmpStableFrame = new Rect();
    static final Rect mTmpVisibleFrame = new Rect();
    private static boolean mUsingHwNavibar = SystemProperties.getBoolean("ro.config.hw_navigationbar", false);
    static SparseArray<String> sApplicationLaunchKeyCategories = new SparseArray();
    private static final String sProximityWndName = "Emui:ProximityWnd";
    boolean ifBootMessageShowing = false;
    private int mAODState = 1;
    boolean mAccelerometerDefault;
    AccessibilityManager mAccessibilityManager;
    ActivityManagerInternal mActivityManagerInternal;
    int mAllowAllRotations = -1;
    boolean mAllowLockscreenWhenOn;
    private boolean mAllowTheaterModeWakeFromCameraLens;
    private boolean mAllowTheaterModeWakeFromKey;
    private boolean mAllowTheaterModeWakeFromLidSwitch;
    private boolean mAllowTheaterModeWakeFromMotion;
    private boolean mAllowTheaterModeWakeFromMotionWhenNotDreaming;
    private boolean mAllowTheaterModeWakeFromPowerKey;
    private boolean mAllowTheaterModeWakeFromWakeGesture;
    private int mAodSwitch = 1;
    private AodSwitchObserver mAodSwitchObserver;
    AppOpsManager mAppOpsManager;
    HashSet<IApplicationToken> mAppsThatDismissKeyguard = new HashSet();
    HashSet<IApplicationToken> mAppsToBeHidden = new HashSet();
    boolean mAssistKeyLongPressed;
    boolean mAwake;
    volatile boolean mBackKeyHandled;
    volatile boolean mBeganFromNonInteractive;
    boolean mBootMessageNeedsHiding;
    ProgressDialog mBootMsgDialog = null;
    WakeLock mBroadcastWakeLock;
    BurnInProtectionHelper mBurnInProtectionHelper;
    long[] mCalendarDateVibePattern;
    volatile boolean mCameraGestureTriggeredDuringGoingToSleep;
    int mCameraLensCoverState = -1;
    boolean mCanHideNavigationBar = false;
    boolean mCarDockEnablesAccelerometer;
    Intent mCarDockIntent;
    int mCarDockRotation;
    private final Runnable mClearHideNavigationFlag = new Runnable() {
        public void run() {
            synchronized (PhoneWindowManager.this.mWindowManagerFuncs.getWindowManagerLock()) {
                PhoneWindowManager phoneWindowManager = PhoneWindowManager.this;
                phoneWindowManager.mForceClearedSystemUiFlags &= -3;
            }
            PhoneWindowManager.this.mWindowManagerFuncs.reevaluateStatusBarVisibility();
        }
    };
    long[] mClockTickVibePattern;
    boolean mConsumeSearchKeyUp;
    int mContentBottom;
    int mContentLeft;
    int mContentRight;
    int mContentTop;
    Context mContext;
    long[] mContextClickVibePattern;
    int mCurBottom;
    int mCurLeft;
    int mCurRight;
    int mCurTop;
    int mCurrentAppOrientation = -1;
    protected int mCurrentUserId;
    private HwCustPhoneWindowManager mCust = ((HwCustPhoneWindowManager) HwCustUtils.createObj(HwCustPhoneWindowManager.class, new Object[0]));
    private boolean mDeferBindKeyguard;
    int mDemoHdmiRotation;
    boolean mDemoHdmiRotationLock;
    int mDemoRotation;
    boolean mDemoRotationLock;
    boolean mDeskDockEnablesAccelerometer;
    Intent mDeskDockIntent;
    int mDeskDockRotation;
    int mDismissKeyguard = 0;
    Display mDisplay;
    private int mDisplayRotation;
    int mDockBottom;
    int mDockLayer;
    int mDockLeft;
    int mDockMode = 0;
    BroadcastReceiver mDockReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if ("android.intent.action.DOCK_EVENT".equals(intent.getAction())) {
                PhoneWindowManager.this.mDockMode = intent.getIntExtra("android.intent.extra.DOCK_STATE", 0);
            } else {
                try {
                    IUiModeManager uiModeService = Stub.asInterface(ServiceManager.getService("uimode"));
                    PhoneWindowManager.this.mUiMode = uiModeService.getCurrentModeType();
                } catch (RemoteException e) {
                }
            }
            PhoneWindowManager.this.updateRotation(PhoneWindowManager.SHOW_STARTING_ANIMATIONS);
            synchronized (PhoneWindowManager.this.mLock) {
                PhoneWindowManager.this.updateOrientationListenerLp();
            }
        }
    };
    int mDockRight;
    int mDockTop;
    final Rect mDockedStackBounds = new Rect();
    int mDoublePressOnPowerBehavior;
    private int mDoubleTapOnHomeBehavior;
    DreamManagerInternal mDreamManagerInternal;
    BroadcastReceiver mDreamReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if ("android.intent.action.DREAMING_STARTED".equals(intent.getAction())) {
                if (PhoneWindowManager.this.mKeyguardDelegate != null) {
                    PhoneWindowManager.this.mKeyguardDelegate.onDreamingStarted();
                }
            } else if ("android.intent.action.DREAMING_STOPPED".equals(intent.getAction()) && PhoneWindowManager.this.mKeyguardDelegate != null) {
                PhoneWindowManager.this.mKeyguardDelegate.onDreamingStopped();
            }
        }
    };
    boolean mDreamingLockscreen;
    SleepToken mDreamingSleepToken;
    boolean mDreamingSleepTokenNeeded;
    private boolean mEnableCarDockHomeCapture = SHOW_STARTING_ANIMATIONS;
    boolean mEnableShiftMenuBugReports = false;
    volatile boolean mEndCallKeyHandled;
    private final Runnable mEndCallLongPress = new Runnable() {
        public void run() {
            PhoneWindowManager.this.mEndCallKeyHandled = PhoneWindowManager.SHOW_STARTING_ANIMATIONS;
            if (!PhoneWindowManager.this.performHapticFeedbackLw(null, 0, false)) {
                PhoneWindowManager.this.performAuditoryFeedbackForAccessibilityIfNeed();
            }
            PhoneWindowManager.this.showGlobalActionsInternal();
        }
    };
    int mEndcallBehavior;
    private final SparseArray<FallbackAction> mFallbackActions = new SparseArray();
    IApplicationToken mFocusedApp;
    WindowState mFocusedWindow;
    int mForceClearedSystemUiFlags = 0;
    private boolean mForceDefaultOrientation = false;
    boolean mForceShowSystemBars;
    boolean mForceStatusBar;
    boolean mForceStatusBarFromKeyguard;
    private boolean mForceStatusBarTransparent;
    WindowState mForceStatusBarTransparentWin;
    boolean mForcingShowNavBar;
    int mForcingShowNavBarLayer;
    GlobalActions mGlobalActions;
    private GlobalKeyManager mGlobalKeyManager;
    private boolean mGoToSleepOnButtonPressTheaterMode;
    volatile boolean mGoingToSleep;
    private UEventObserver mHDMIObserver = new UEventObserver() {
        public void onUEvent(UEvent event) {
            PhoneWindowManager.this.setHdmiPlugged("1".equals(event.get("SWITCH_STATE")));
        }
    };
    Handler mHandler;
    protected boolean mHasCoverView = false;
    private boolean mHasFeatureWatch;
    boolean mHasNavigationBar = false;
    boolean mHasSoftInput = false;
    boolean mHaveBuiltInKeyboard;
    boolean mHavePendingMediaKeyRepeatWithWakeLock;
    HdmiControl mHdmiControl;
    boolean mHdmiPlugged;
    private final Runnable mHiddenNavPanic = new Runnable() {
        public void run() {
            synchronized (PhoneWindowManager.this.mWindowManagerFuncs.getWindowManagerLock()) {
                if (PhoneWindowManager.this.isUserSetupComplete()) {
                    PhoneWindowManager.this.mPendingPanicGestureUptime = SystemClock.uptimeMillis();
                    PhoneWindowManager.this.mNavigationBarController.showTransient();
                    return;
                }
            }
        }
    };
    boolean mHideLockScreen;
    final Factory mHideNavInputEventReceiverFactory = new Factory() {
        public InputEventReceiver createInputEventReceiver(InputChannel inputChannel, Looper looper) {
            return new HideNavInputEventReceiver(inputChannel, looper);
        }
    };
    boolean mHomeConsumed;
    boolean mHomeDoubleTapPending;
    private final Runnable mHomeDoubleTapTimeoutRunnable = new Runnable() {
        public void run() {
            if (PhoneWindowManager.this.mHomeDoubleTapPending) {
                PhoneWindowManager.this.mHomeDoubleTapPending = false;
                PhoneWindowManager.this.handleShortPressOnHome();
            }
        }
    };
    Intent mHomeIntent;
    boolean mHomePressed;
    IAodStateCallback mIAodStateCallback;
    private ImmersiveModeConfirmation mImmersiveModeConfirmation;
    boolean mImmersiveStatusChanged;
    int mIncallPowerBehavior;
    int mInitialMetaState;
    InputConsumer mInputConsumer = null;
    InputManagerInternal mInputManagerInternal;
    protected boolean mInterceptInputForWaitBrightness = false;
    long[] mKeyboardTapVibePattern;
    private boolean mKeyguardBound = false;
    KeyguardServiceDelegate mKeyguardDelegate;
    final Runnable mKeyguardDismissDoneCallback = new Runnable() {
        public void run() {
            Slog.i(PhoneWindowManager.TAG, "keyguard dismiss done!");
            PhoneWindowManager.this.finishKeyguardDismissDone();
        }
    };
    KeyguardDismissDoneListener mKeyguardDismissListener;
    boolean mKeyguardDrawComplete;
    final DrawnListener mKeyguardDrawnCallback = new DrawnListener() {
        public void onDrawn() {
            Slog.d(PhoneWindowManager.TAG, "mKeyguardDelegate.ShowListener.onDrawn.");
            PhoneWindowManager.this.mHandler.sendEmptyMessage(5);
        }
    };
    private boolean mKeyguardDrawnOnce;
    private boolean mKeyguardHidden;
    volatile boolean mKeyguardOccluded;
    private WindowState mKeyguardScrim;
    boolean mKeyguardSecure;
    boolean mKeyguardSecureIncludingHidden;
    int mLandscapeRotation = 0;
    boolean mLanguageSwitchKeyPressed;
    final Rect mLastDockedStackBounds = new Rect();
    int mLastDockedStackSysUiFlags;
    public boolean mLastFocusNeedsMenu = false;
    int mLastFullscreenStackSysUiFlags;
    int mLastHideNaviDockBottom = 0;
    WindowState mLastInputMethodTargetWindow = null;
    WindowState mLastInputMethodWindow = null;
    int mLastNaviStatus = -1;
    final Rect mLastNonDockedStackBounds = new Rect();
    int mLastShowNaviDockBottom = 0;
    private WindowState mLastStartingWindow;
    int mLastSystemUiFlags;
    int mLastTransientNaviDockBottom = 0;
    boolean mLidControlsScreenLock;
    boolean mLidControlsSleep;
    int mLidKeyboardAccessibility;
    int mLidNavigationAccessibility;
    int mLidOpenRotation;
    int mLidState = -1;
    protected final Object mLock = new Object();
    int mLockScreenTimeout;
    boolean mLockScreenTimerActive;
    private final LogDecelerateInterpolator mLogDecelerateInterpolator = new LogDecelerateInterpolator(100, 0);
    int mLongPressOnBackBehavior;
    private int mLongPressOnHomeBehavior;
    int mLongPressOnPowerBehavior;
    long[] mLongPressVibePattern;
    int mMetaState;
    BroadcastReceiver mMultiuserReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if ("android.intent.action.USER_SWITCHED".equals(intent.getAction())) {
                PhoneWindowManager.this.mSettingsObserver.onChange(false);
                if (PhoneWindowManager.mSupportAod) {
                    Slog.i(PhoneWindowManager.TAG, "AOD mAodSwitchObserver.onChange");
                    PhoneWindowManager.this.mAodSwitchObserver.onChange(false);
                    if (!(PhoneWindowManager.this.mScreenOnEarly || PhoneWindowManager.this.mScreenOnFully)) {
                        PhoneWindowManager.this.startAodService(6);
                    }
                }
                synchronized (PhoneWindowManager.this.mWindowManagerFuncs.getWindowManagerLock()) {
                    PhoneWindowManager.this.mLastSystemUiFlags = 0;
                    PhoneWindowManager.this.updateSystemUiVisibilityLw();
                }
            }
        }
    };
    int mNavBarOpacityMode = 0;
    WindowState mNavigationBar = null;
    boolean mNavigationBarCanMove = false;
    private final BarController mNavigationBarController = new BarController("NavigationBar", 134217728, 536870912, Integer.MIN_VALUE, 2, 134217728, DumpState.DUMP_VERSION);
    int[] mNavigationBarHeightForRotationDefault = new int[4];
    int[] mNavigationBarHeightForRotationInCarMode = new int[4];
    boolean mNavigationBarOnBottom = SHOW_STARTING_ANIMATIONS;
    int[] mNavigationBarWidthForRotationDefault = new int[4];
    int[] mNavigationBarWidthForRotationInCarMode = new int[4];
    final Rect mNonDockedStackBounds = new Rect();
    MyOrientationListener mOrientationListener;
    boolean mOrientationSensorEnabled = false;
    int mOverscanBottom = 0;
    int mOverscanLeft = 0;
    int mOverscanRight = 0;
    int mOverscanScreenHeight;
    int mOverscanScreenLeft;
    int mOverscanScreenTop;
    int mOverscanScreenWidth;
    int mOverscanTop = 0;
    private final HashMap<String, Boolean> mPackages = new HashMap();
    boolean mPendingCapsLockToggle;
    boolean mPendingMetaAction;
    private long mPendingPanicGestureUptime;
    int mPointerLocationMode = 0;
    PointerLocationView mPointerLocationView;
    int mPortraitRotation = 0;
    volatile boolean mPowerKeyHandled;
    volatile int mPowerKeyPressCounter;
    WakeLock mPowerKeyWakeLock;
    PowerManager mPowerManager;
    PowerManagerInternal mPowerManagerInternal;
    boolean mPreloadedRecentApps;
    int mRecentAppsHeldModifiers;
    volatile boolean mRecentsVisible;
    int mResettingSystemUiFlags = 0;
    int mRestrictedOverscanScreenHeight;
    int mRestrictedOverscanScreenLeft;
    int mRestrictedOverscanScreenTop;
    int mRestrictedOverscanScreenWidth;
    int mRestrictedScreenHeight;
    int mRestrictedScreenLeft;
    int mRestrictedScreenTop;
    int mRestrictedScreenWidth;
    boolean mSafeMode;
    long[] mSafeModeDisabledVibePattern;
    long[] mSafeModeEnabledVibePattern;
    ScreenLockTimeout mScreenLockTimeout = new ScreenLockTimeout();
    protected int mScreenOffReason = -1;
    SleepToken mScreenOffSleepToken;
    boolean mScreenOnEarly;
    boolean mScreenOnFully;
    ScreenOnListener mScreenOnListener;
    private boolean mScreenshotChordEnabled;
    private long mScreenshotChordPowerKeyTime;
    private boolean mScreenshotChordPowerKeyTriggered;
    private boolean mScreenshotChordVolumeDownKeyConsumed;
    private long mScreenshotChordVolumeDownKeyTime;
    private boolean mScreenshotChordVolumeDownKeyTriggered;
    private boolean mScreenshotChordVolumeUpKeyTriggered;
    ServiceConnection mScreenshotConnection = null;
    final Object mScreenshotLock = new Object();
    private final ScreenshotRunnable mScreenshotRunnable = new ScreenshotRunnable();
    final Runnable mScreenshotTimeout = new Runnable() {
        public void run() {
            synchronized (PhoneWindowManager.this.mScreenshotLock) {
                if (PhoneWindowManager.this.mScreenshotConnection != null) {
                    if (PhoneWindowManager.HWFLOW) {
                        Slog.i(PhoneWindowManager.TAG, "takeScreenshot  screenshot timeout");
                    }
                    PhoneWindowManager.this.mContext.unbindService(PhoneWindowManager.this.mScreenshotConnection);
                    PhoneWindowManager.this.mScreenshotConnection = null;
                    PhoneWindowManager.this.notifyScreenshotError();
                }
            }
        }
    };
    boolean mSearchKeyShortcutPending;
    SearchManager mSearchManager;
    int mSeascapeRotation = 0;
    private boolean mSecureDismissingKeyguard;
    final Object mServiceAquireLock = new Object();
    SettingsObserver mSettingsObserver;
    int mShortPressOnPowerBehavior;
    int mShortPressOnSleepBehavior;
    int mShortPressWindowBehavior;
    private LongSparseArray<IShortcutService> mShortcutKeyServices = new LongSparseArray();
    ShortcutManager mShortcutManager;
    boolean mShowingDream;
    boolean mShowingLockscreen;
    BroadcastReceiver mShutdownReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (PhoneWindowManager.ACTION_ACTURAL_SHUTDOWN.equals(intent.getAction())) {
                PhoneWindowManager.this.mOrientationListener.disable();
                PhoneWindowManager.this.mOrientationSensorEnabled = false;
            }
        }
    };
    int mStableBottom;
    int mStableFullscreenBottom;
    int mStableFullscreenLeft;
    int mStableFullscreenRight;
    int mStableFullscreenTop;
    int mStableLeft;
    int mStableRight;
    int mStableTop;
    WindowState mStatusBar = null;
    private final StatusBarController mStatusBarController = new StatusBarController();
    int mStatusBarHeight;
    int mStatusBarLayer;
    StatusBarManagerInternal mStatusBarManagerInternal;
    IStatusBarService mStatusBarService;
    boolean mSupportAutoRotation;
    private boolean mSupportLongPressPowerWhenNonInteractive;
    private SystemUiMonitor mSysUiMonitor;
    boolean mSystemBooted;
    int mSystemBottom;
    private SystemGesturesPointerEventListener mSystemGestures;
    int mSystemLeft;
    boolean mSystemReady;
    int mSystemRight;
    int mSystemTop;
    private final MutableBoolean mTmpBoolean = new MutableBoolean(false);
    WindowState mTopDockedOpaqueOrDimmingWindowState;
    WindowState mTopDockedOpaqueWindowState;
    WindowState mTopFullscreenOpaqueOrDimmingWindowState;
    WindowState mTopFullscreenOpaqueWindowState;
    boolean mTopIsFullscreen;
    boolean mTranslucentDecorEnabled = SHOW_STARTING_ANIMATIONS;
    int mTriplePressOnPowerBehavior;
    volatile boolean mTvPictureInPictureVisible;
    int mUiMode;
    IUiModeManager mUiModeManager;
    int mUndockedHdmiRotation;
    int mUnrestrictedScreenHeight;
    int mUnrestrictedScreenLeft;
    int mUnrestrictedScreenTop;
    int mUnrestrictedScreenWidth;
    int mUpsideDownRotation = 0;
    boolean mUseTvRouting;
    int mUserRotation = 0;
    int mUserRotationMode = 0;
    Vibrator mVibrator;
    long[] mVirtualKeyVibePattern;
    int mVoiceContentBottom;
    int mVoiceContentLeft;
    int mVoiceContentRight;
    int mVoiceContentTop;
    boolean mWakeGestureEnabledSetting;
    MyWakeGestureListener mWakeGestureListener;
    private WindowState mWinDismissingKeyguard;
    private WindowState mWinShowWhenLocked;
    IWindowManager mWindowManager;
    final Runnable mWindowManagerDrawCallback = new Runnable() {
        public void run() {
            Slog.i(PhoneWindowManager.TAG, "All windows ready for display!");
            PhoneWindowManager.this.mHandler.sendEmptyMessage(7);
        }
    };
    boolean mWindowManagerDrawComplete;
    WindowManagerFuncs mWindowManagerFuncs;
    WindowManagerInternal mWindowManagerInternal;

    /* renamed from: com.android.server.policy.PhoneWindowManager$26 */
    class AnonymousClass26 implements Runnable {
        final /* synthetic */ Rect val$dockedStackBounds;
        final /* synthetic */ int val$dockedVisibility;
        final /* synthetic */ Rect val$fullscreenStackBounds;
        final /* synthetic */ int val$fullscreenVisibility;
        final /* synthetic */ boolean val$needsMenu;
        final /* synthetic */ int val$visibility;
        final /* synthetic */ WindowState val$win;

        AnonymousClass26(int val$visibility, int val$fullscreenVisibility, int val$dockedVisibility, Rect val$fullscreenStackBounds, Rect val$dockedStackBounds, WindowState val$win, boolean val$needsMenu) {
            this.val$visibility = val$visibility;
            this.val$fullscreenVisibility = val$fullscreenVisibility;
            this.val$dockedVisibility = val$dockedVisibility;
            this.val$fullscreenStackBounds = val$fullscreenStackBounds;
            this.val$dockedStackBounds = val$dockedStackBounds;
            this.val$win = val$win;
            this.val$needsMenu = val$needsMenu;
        }

        public void run() {
            StatusBarManagerInternal statusbar = PhoneWindowManager.this.getStatusBarManagerInternal();
            if (statusbar != null) {
                statusbar.setSystemUiVisibility(this.val$visibility, this.val$fullscreenVisibility, this.val$dockedVisibility, -1, this.val$fullscreenStackBounds, this.val$dockedStackBounds, this.val$win.toString());
                statusbar.topAppWindowChanged(this.val$needsMenu);
            }
        }
    }

    class AodSwitchObserver extends ContentObserver {
        AodSwitchObserver(Handler handler) {
            super(handler);
            PhoneWindowManager.this.mAodSwitch = getAodSwitch();
        }

        void observe() {
            Slog.i(PhoneWindowManager.TAG, "AOD AodSwitchObserver observe");
            if (PhoneWindowManager.mSupportAod) {
                PhoneWindowManager.this.mContext.getContentResolver().registerContentObserver(Secure.getUriFor("aod_switch"), false, this, -1);
            }
        }

        public void onChange(boolean selfChange) {
            PhoneWindowManager.this.mAodSwitch = Secure.getIntForUser(PhoneWindowManager.this.mContext.getContentResolver(), "aod_switch", 1, ActivityManager.getCurrentUser());
        }

        private int getAodSwitch() {
            Slog.i(PhoneWindowManager.TAG, "AOD getAodSwitch ");
            if (!PhoneWindowManager.mSupportAod) {
                return 0;
            }
            PhoneWindowManager.this.mAodSwitch = Secure.getIntForUser(PhoneWindowManager.this.mContext.getContentResolver(), "aod_switch", 1, ActivityManager.getCurrentUser());
            return PhoneWindowManager.this.mAodSwitch;
        }
    }

    private final class AppDeathRecipient implements DeathRecipient {
        AppDeathRecipient() {
        }

        public void binderDied() {
            Slog.i(PhoneWindowManager.TAG, "Death received in " + this + " for thread " + PhoneWindowManager.this.mIAodStateCallback.asBinder());
            if (PhoneWindowManager.mSupportAod) {
                PhoneWindowManager.this.unregeditAodStateCallback(PhoneWindowManager.this.mIAodStateCallback);
                PhoneWindowManager.this.mIAodStateCallback = null;
                PhoneWindowManager.this.mPowerManager.setDozeOverrideFromAod(1, 0, null);
                PhoneWindowManager.this.mPowerManager.setAodState(-1, 0);
                PhoneWindowManager.this.mPowerManager.setAodState(-1, 2);
            }
        }
    }

    private static class HdmiControl {
        private final HdmiPlaybackClient mClient;

        private HdmiControl(HdmiPlaybackClient client) {
            this.mClient = client;
        }

        public void turnOnTv() {
            if (this.mClient != null) {
                this.mClient.oneTouchPlay(new OneTouchPlayCallback() {
                    public void onComplete(int result) {
                        if (result != 0) {
                            Log.w(PhoneWindowManager.TAG, "One touch play failed: " + result);
                        }
                    }
                });
            }
        }
    }

    final class HideNavInputEventReceiver extends InputEventReceiver {
        public HideNavInputEventReceiver(InputChannel inputChannel, Looper looper) {
            super(inputChannel, looper);
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public void onInputEvent(InputEvent event) {
            try {
                if ((event instanceof MotionEvent) && (event.getSource() & 2) != 0 && ((MotionEvent) event).getAction() == 0) {
                    boolean changed = false;
                    synchronized (PhoneWindowManager.this.mWindowManagerFuncs.getWindowManagerLock()) {
                        if (PhoneWindowManager.this.mInputConsumer == null) {
                            finishInputEvent(event, false);
                            return;
                        }
                        int newVal = ((PhoneWindowManager.this.mResettingSystemUiFlags | 2) | 1) | 4;
                        if (PhoneWindowManager.this.mResettingSystemUiFlags != newVal) {
                            PhoneWindowManager.this.mResettingSystemUiFlags = newVal;
                            changed = PhoneWindowManager.SHOW_STARTING_ANIMATIONS;
                        }
                        newVal = PhoneWindowManager.this.mForceClearedSystemUiFlags | 2;
                        if (PhoneWindowManager.this.mForceClearedSystemUiFlags != newVal) {
                            PhoneWindowManager.this.mForceClearedSystemUiFlags = newVal;
                            changed = PhoneWindowManager.SHOW_STARTING_ANIMATIONS;
                            PhoneWindowManager.this.mHandler.postDelayed(PhoneWindowManager.this.mClearHideNavigationFlag, 1000);
                        }
                    }
                }
                finishInputEvent(event, false);
            } catch (Throwable th) {
                finishInputEvent(event, false);
            }
        }
    }

    class MyOrientationListener extends WindowOrientationListener {

        private final class UpdateRotationRunnable implements Runnable {
            private final int mRotation;

            public UpdateRotationRunnable(int rotation) {
                this.mRotation = rotation;
            }

            public void run() {
                PhoneWindowManager.this.mPowerManagerInternal.powerHint(2, 0);
                if (PhoneWindowManager.this.mWindowManagerInternal.isDockedDividerResizing()) {
                    PhoneWindowManager.this.mWindowManagerInternal.setDockedStackDividerRotation(this.mRotation);
                } else {
                    Slog.i(PhoneWindowManager.TAG, "MyOrientationListener: updateRotation.");
                    PhoneWindowManager.this.updateRotation(false);
                }
                if (PhoneWindowManager.bHasFrontFp) {
                    PhoneWindowManager.this.updateSplitScreenView();
                }
            }
        }

        MyOrientationListener(Context context, Handler handler) {
            super(context, handler);
        }

        public void onProposedRotationChanged(int rotation) {
            Slog.i(PhoneWindowManager.TAG, "onProposedRotationChanged, rotation=" + rotation);
            Jlog.d(57, "");
            PhoneWindowManager.this.mHandler.post(new UpdateRotationRunnable(rotation));
        }
    }

    class MyWakeGestureListener extends WakeGestureListener {
        MyWakeGestureListener(Context context, Handler handler) {
            super(context, handler);
        }

        public void onWakeUp() {
            synchronized (PhoneWindowManager.this.mLock) {
                if (PhoneWindowManager.this.shouldEnableWakeGestureLp()) {
                    PhoneWindowManager.this.performHapticFeedbackLw(null, 1, false);
                    PhoneWindowManager.this.wakeUp(SystemClock.uptimeMillis(), PhoneWindowManager.this.mAllowTheaterModeWakeFromWakeGesture, "android.policy:GESTURE");
                }
            }
        }
    }

    private class PolicyHandler extends Handler {
        private PolicyHandler() {
        }

        public void handleMessage(Message msg) {
            boolean z = PhoneWindowManager.SHOW_STARTING_ANIMATIONS;
            PhoneWindowManager phoneWindowManager;
            switch (msg.what) {
                case 1:
                    PhoneWindowManager.this.enablePointerLocation();
                    return;
                case 2:
                    PhoneWindowManager.this.disablePointerLocation();
                    return;
                case 3:
                    PhoneWindowManager.this.dispatchMediaKeyWithWakeLock((KeyEvent) msg.obj);
                    return;
                case 4:
                    PhoneWindowManager.this.dispatchMediaKeyRepeatWithWakeLock((KeyEvent) msg.obj);
                    return;
                case 5:
                    PhoneWindowManager.this.finishKeyguardDrawn();
                    return;
                case 6:
                    Flog.i(NativeResponseCode.SERVICE_FOUND, "WindowManager Keyguard drawn timeout. Setting mKeyguardDrawComplete");
                    PhoneWindowManager.this.finishKeyguardDrawn();
                    return;
                case 7:
                    PhoneWindowManager.this.finishWindowsDrawn();
                    return;
                case 9:
                    PhoneWindowManager.this.showRecentApps(false, msg.arg1 != 0 ? PhoneWindowManager.SHOW_STARTING_ANIMATIONS : false);
                    return;
                case 10:
                    PhoneWindowManager.this.showGlobalActionsInternal();
                    return;
                case 11:
                    PhoneWindowManager.this.handleHideBootMessage();
                    return;
                case 12:
                    phoneWindowManager = PhoneWindowManager.this;
                    if (msg.arg1 == 0) {
                        z = false;
                    }
                    phoneWindowManager.launchVoiceAssistWithWakeLock(z);
                    return;
                case 13:
                    PhoneWindowManager phoneWindowManager2 = PhoneWindowManager.this;
                    long longValue = ((Long) msg.obj).longValue();
                    if (msg.arg1 == 0) {
                        z = false;
                    }
                    phoneWindowManager2.powerPress(longValue, z, msg.arg2);
                    PhoneWindowManager.this.finishPowerKeyPress();
                    return;
                case 14:
                    PhoneWindowManager.this.powerLongPress();
                    return;
                case 15:
                    phoneWindowManager = PhoneWindowManager.this;
                    if (msg.arg1 == 0) {
                        z = false;
                    }
                    phoneWindowManager.updateDreamingSleepToken(z);
                    return;
                case 16:
                    WindowState targetBar = msg.arg1 == 0 ? PhoneWindowManager.this.mStatusBar : PhoneWindowManager.this.mNavigationBar;
                    if (targetBar != null) {
                        PhoneWindowManager.this.requestTransientBars(targetBar);
                        return;
                    }
                    return;
                case 17:
                    PhoneWindowManager.this.showTvPictureInPictureMenuInternal();
                    return;
                case 18:
                    PhoneWindowManager.this.backLongPress();
                    return;
                case 19:
                    PhoneWindowManager.this.disposeInputConsumer((InputConsumer) msg.obj);
                    return;
                default:
                    return;
            }
        }
    }

    class ScreenLockTimeout implements Runnable {
        Bundle options;

        ScreenLockTimeout() {
        }

        public void run() {
            synchronized (this) {
                if (PhoneWindowManager.this.mKeyguardDelegate != null) {
                    PhoneWindowManager.this.mKeyguardDelegate.doKeyguardTimeout(this.options);
                }
                PhoneWindowManager.this.mLockScreenTimerActive = false;
                this.options = null;
            }
        }

        public void setLockOptions(Bundle options) {
            this.options = options;
        }
    }

    private class ScreenshotRunnable implements Runnable {
        private int mScreenshotType;

        private ScreenshotRunnable() {
            this.mScreenshotType = 1;
        }

        public void setScreenshotType(int screenshotType) {
            this.mScreenshotType = screenshotType;
        }

        public void run() {
            PhoneWindowManager.this.takeScreenshot(this.mScreenshotType);
        }
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = PhoneWindowManager.this.mContext.getContentResolver();
            resolver.registerContentObserver(System.getUriFor("end_button_behavior"), false, this, -1);
            resolver.registerContentObserver(Secure.getUriFor("incall_power_button_behavior"), false, this, -1);
            resolver.registerContentObserver(Secure.getUriFor("wake_gesture_enabled"), false, this, -1);
            resolver.registerContentObserver(System.getUriFor("accelerometer_rotation"), false, this, -1);
            resolver.registerContentObserver(System.getUriFor("user_rotation"), false, this, -1);
            resolver.registerContentObserver(System.getUriFor("screen_off_timeout"), false, this, -1);
            resolver.registerContentObserver(System.getUriFor("pointer_location"), false, this, -1);
            resolver.registerContentObserver(Secure.getUriFor("default_input_method"), false, this, -1);
            resolver.registerContentObserver(Secure.getUriFor("immersive_mode_confirmations"), false, this, -1);
            resolver.registerContentObserver(Global.getUriFor("policy_control"), false, this, -1);
            resolver.registerContentObserver(System.getUriFor(PhoneWindowManager.this.fingersense_enable), false, this, -1);
            resolver.registerContentObserver(System.getUriFor(PhoneWindowManager.this.fingersense_letters_enable), false, this, -1);
            resolver.registerContentObserver(System.getUriFor(PhoneWindowManager.this.line_gesture_enable), false, this, -1);
            resolver.registerContentObserver(System.getUriFor(PhoneWindowManager.this.navibar_enable), false, this, -1);
            PhoneWindowManager.this.updateSettings();
        }

        public void onChange(boolean selfChange) {
            PhoneWindowManager.this.updateSettings();
            PhoneWindowManager.this.updateRotation(false);
        }
    }

    private class SystemUiMonitor extends IProcessObserver.Stub {
        private final int targetUid;

        SystemUiMonitor() {
            int i;
            ApplicationInfo ai = null;
            try {
                ai = PhoneWindowManager.this.mContext.getPackageManager().getApplicationInfo(PhoneWindowManager.SYSUI_PACKAGE, 0);
            } catch (NameNotFoundException e) {
            }
            if (ai != null) {
                i = ai.uid;
            } else {
                i = -1;
            }
            this.targetUid = i;
        }

        public void onProcessStateChanged(int pid, int uid, int procState) {
            Flog.i(305, "Register SystemUi monitor onProcessStateChanged with targetUid : " + this.targetUid + " uid : " + uid + " procState: " + procState);
            if (UserHandle.isSameApp(uid, this.targetUid) && procState == 0) {
                Flog.i(305, "SystemUi started! Bind KeyguardService now.");
                PhoneWindowManager.this.mKeyguardDelegate.bindService(PhoneWindowManager.this.mContext);
                PhoneWindowManager.this.mKeyguardDelegate.onBootCompleted();
                PhoneWindowManager.this.mKeyguardBound = PhoneWindowManager.SHOW_STARTING_ANIMATIONS;
            }
        }

        public void onForegroundActivitiesChanged(int pid, int uid, boolean foregroundActivities) {
        }

        public void onProcessDied(int pid, int uid) {
        }

        void onSystemReady() {
            try {
                Flog.i(305, "Register SystemUi monitor.");
                ActivityManagerNative.getDefault().registerProcessObserver(this);
            } catch (RemoteException e) {
            }
        }

        void onSystemBooted() {
            try {
                Flog.i(305, "Unregister SystemUi monitor.");
                ActivityManagerNative.getDefault().unregisterProcessObserver(this);
            } catch (RemoteException e) {
            }
        }
    }

    private int updateSystemBarsLw(android.view.WindowManagerPolicy.WindowState r1, int r2, int r3) {
        /* JADX: method processing error */
/*
Error: jadx.core.utils.exceptions.DecodeException: Load method exception in method: com.android.server.policy.PhoneWindowManager.updateSystemBarsLw(android.view.WindowManagerPolicy$WindowState, int, int):int
	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:116)
	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:249)
	at jadx.core.ProcessClass.process(ProcessClass.java:34)
	at jadx.core.ProcessClass.processDependencies(ProcessClass.java:59)
	at jadx.core.ProcessClass.process(ProcessClass.java:42)
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:306)
	at jadx.api.JavaClass.decompile(JavaClass.java:62)
	at jadx.api.JadxDecompiler$1.run(JadxDecompiler.java:199)
Caused by: jadx.core.utils.exceptions.DecodeException: Unknown instruction: not-int
	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:569)
	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:56)
	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:102)
	... 7 more
*/
        /*
        // Can't load method instructions.
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.server.policy.PhoneWindowManager.updateSystemBarsLw(android.view.WindowManagerPolicy$WindowState, int, int):int");
    }

    private int updateSystemUiVisibilityLw() {
        /* JADX: method processing error */
/*
Error: jadx.core.utils.exceptions.DecodeException: Load method exception in method: com.android.server.policy.PhoneWindowManager.updateSystemUiVisibilityLw():int
	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:116)
	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:249)
	at jadx.core.ProcessClass.process(ProcessClass.java:34)
	at jadx.core.ProcessClass.processDependencies(ProcessClass.java:59)
	at jadx.core.ProcessClass.process(ProcessClass.java:42)
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:306)
	at jadx.api.JavaClass.decompile(JavaClass.java:62)
	at jadx.api.JadxDecompiler$1.run(JadxDecompiler.java:199)
Caused by: jadx.core.utils.exceptions.DecodeException: Unknown instruction: not-int
	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:569)
	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:56)
	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:102)
	... 7 more
*/
        /*
        // Can't load method instructions.
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.server.policy.PhoneWindowManager.updateSystemUiVisibilityLw():int");
    }

    public int adjustSystemUiVisibilityLw(int r1) {
        /* JADX: method processing error */
/*
Error: jadx.core.utils.exceptions.DecodeException: Load method exception in method: com.android.server.policy.PhoneWindowManager.adjustSystemUiVisibilityLw(int):int
	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:116)
	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:249)
	at jadx.core.ProcessClass.process(ProcessClass.java:34)
	at jadx.core.ProcessClass.processDependencies(ProcessClass.java:59)
	at jadx.core.ProcessClass.process(ProcessClass.java:42)
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:306)
	at jadx.api.JavaClass.decompile(JavaClass.java:62)
	at jadx.api.JadxDecompiler$1.run(JadxDecompiler.java:199)
Caused by: jadx.core.utils.exceptions.DecodeException: Unknown instruction: not-int
	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:569)
	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:56)
	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:102)
	... 7 more
*/
        /*
        // Can't load method instructions.
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.server.policy.PhoneWindowManager.adjustSystemUiVisibilityLw(int):int");
    }

    static {
        boolean isLoggable = !Log.HWINFO ? Log.HWModuleLog ? Log.isLoggable(TAG, 4) : false : SHOW_STARTING_ANIMATIONS;
        HWFLOW = isLoggable;
        sApplicationLaunchKeyCategories.append(64, "android.intent.category.APP_BROWSER");
        sApplicationLaunchKeyCategories.append(65, "android.intent.category.APP_EMAIL");
        sApplicationLaunchKeyCategories.append(207, "android.intent.category.APP_CONTACTS");
        sApplicationLaunchKeyCategories.append(208, "android.intent.category.APP_CALENDAR");
        sApplicationLaunchKeyCategories.append(209, "android.intent.category.APP_MUSIC");
        sApplicationLaunchKeyCategories.append(210, "android.intent.category.APP_CALCULATOR");
    }

    IStatusBarService getStatusBarService() {
        IStatusBarService iStatusBarService;
        synchronized (this.mServiceAquireLock) {
            if (this.mStatusBarService == null) {
                this.mStatusBarService = IStatusBarService.Stub.asInterface(ServiceManager.getService("statusbar"));
            }
            iStatusBarService = this.mStatusBarService;
        }
        return iStatusBarService;
    }

    StatusBarManagerInternal getStatusBarManagerInternal() {
        StatusBarManagerInternal statusBarManagerInternal;
        synchronized (this.mServiceAquireLock) {
            if (this.mStatusBarManagerInternal == null) {
                this.mStatusBarManagerInternal = (StatusBarManagerInternal) LocalServices.getService(StatusBarManagerInternal.class);
            }
            statusBarManagerInternal = this.mStatusBarManagerInternal;
        }
        return statusBarManagerInternal;
    }

    boolean needSensorRunningLp() {
        if (this.mSupportAutoRotation && (this.mCurrentAppOrientation == 4 || this.mCurrentAppOrientation == 10 || this.mCurrentAppOrientation == 7 || this.mCurrentAppOrientation == 6)) {
            return SHOW_STARTING_ANIMATIONS;
        }
        if ((this.mCarDockEnablesAccelerometer && this.mDockMode == 2) || (this.mDeskDockEnablesAccelerometer && (this.mDockMode == 1 || this.mDockMode == 3 || this.mDockMode == 4))) {
            return SHOW_STARTING_ANIMATIONS;
        }
        if (IS_lOCK_UNNATURAL_ORIENTATION || this.mUserRotationMode != 1) {
            return this.mSupportAutoRotation;
        }
        return false;
    }

    void updateOrientationListenerLp() {
        if (this.mOrientationListener.canDetectOrientation()) {
            boolean disable = SHOW_STARTING_ANIMATIONS;
            if (this.mScreenOnEarly && this.mAwake && this.mKeyguardDrawComplete && this.mWindowManagerDrawComplete && needSensorRunningLp()) {
                disable = false;
                if (!this.mOrientationSensorEnabled) {
                    this.mOrientationListener.enable();
                    this.mOrientationSensorEnabled = SHOW_STARTING_ANIMATIONS;
                }
            }
            if (disable && this.mOrientationSensorEnabled) {
                this.mOrientationListener.disable();
                this.mOrientationSensorEnabled = false;
            }
        }
    }

    private void interceptPowerKeyDown(KeyEvent event, boolean interactive) {
        HwFrameworkFactory.getHwNsdImpl().StopSdrForSpecial("powerdown", 26);
        if (!this.mPowerKeyWakeLock.isHeld()) {
            this.mPowerKeyWakeLock.acquire();
        }
        if (this.mPowerKeyPressCounter != 0) {
            this.mHandler.removeMessages(13);
        }
        if (this.mImmersiveModeConfirmation.onPowerKeyDown(interactive, SystemClock.elapsedRealtime(), isImmersiveMode(this.mLastSystemUiFlags))) {
            this.mHandler.post(this.mHiddenNavPanic);
        }
        if (interactive && !this.mScreenshotChordPowerKeyTriggered && (event.getFlags() & 1024) == 0) {
            this.mScreenshotChordPowerKeyTriggered = SHOW_STARTING_ANIMATIONS;
            this.mScreenshotChordPowerKeyTime = event.getDownTime();
            interceptScreenshotChord();
        }
        TelecomManager telecomManager = getTelecommService();
        boolean hungUp = false;
        if (telecomManager != null) {
            if (telecomManager.isRinging()) {
                telecomManager.silenceRinger();
            } else if ((this.mIncallPowerBehavior & 2) != 0 && telecomManager.isInCall() && interactive) {
                hungUp = telecomManager.endCall();
            }
        }
        GestureLauncherService gestureService = (GestureLauncherService) LocalServices.getService(GestureLauncherService.class);
        boolean z = false;
        if (gestureService != null) {
            z = gestureService.interceptPowerKeyDown(event, interactive, this.mTmpBoolean);
            if (this.mTmpBoolean.value && this.mGoingToSleep) {
                this.mCameraGestureTriggeredDuringGoingToSleep = SHOW_STARTING_ANIMATIONS;
            }
        }
        Slog.i(TAG, "hungUp=" + hungUp + ", mScreenshotChordVolumeDownKeyTriggered=" + this.mScreenshotChordVolumeDownKeyTriggered + ", mScreenshotChordVolumeDownKeyTriggered=" + this.mScreenshotChordVolumeUpKeyTriggered + ", gesturedServiceIntercepted=" + z);
        if (hungUp || this.mScreenshotChordVolumeDownKeyTriggered || this.mScreenshotChordVolumeUpKeyTriggered) {
            z = SHOW_STARTING_ANIMATIONS;
        }
        this.mPowerKeyHandled = z;
        if (!this.mPowerKeyHandled) {
            Message msg;
            if (!interactive) {
                wakeUpFromPowerKey(event.getDownTime());
                if (this.mSupportLongPressPowerWhenNonInteractive && hasLongPressOnPowerBehavior()) {
                    msg = this.mHandler.obtainMessage(14);
                    msg.setAsynchronous(SHOW_STARTING_ANIMATIONS);
                    this.mHandler.sendMessageDelayed(msg, 1000);
                    this.mBeganFromNonInteractive = SHOW_STARTING_ANIMATIONS;
                } else if (getMaxMultiPressPowerCount() <= 1) {
                    this.mPowerKeyHandled = SHOW_STARTING_ANIMATIONS;
                } else {
                    this.mBeganFromNonInteractive = SHOW_STARTING_ANIMATIONS;
                }
            } else if (hasLongPressOnPowerBehavior()) {
                msg = this.mHandler.obtainMessage(14);
                msg.setAsynchronous(SHOW_STARTING_ANIMATIONS);
                this.mHandler.sendMessageDelayed(msg, 1000);
            }
        }
    }

    private void interceptPowerKeyUp(KeyEvent event, boolean interactive, boolean canceled) {
        int i = 0;
        boolean z = !canceled ? this.mPowerKeyHandled : SHOW_STARTING_ANIMATIONS;
        this.mScreenshotChordPowerKeyTriggered = false;
        cancelPendingScreenshotChordAction();
        cancelPendingPowerKeyAction();
        if (!z) {
            this.mPowerKeyPressCounter++;
            int maxCount = getMaxMultiPressPowerCount();
            long eventTime = event.getDownTime();
            if (this.mPowerKeyPressCounter < maxCount) {
                Handler handler = this.mHandler;
                if (interactive) {
                    i = 1;
                }
                Message msg = handler.obtainMessage(13, i, this.mPowerKeyPressCounter, Long.valueOf(eventTime));
                msg.setAsynchronous(SHOW_STARTING_ANIMATIONS);
                this.mHandler.sendMessageDelayed(msg, (long) ViewConfiguration.getDoubleTapTimeout());
                return;
            }
            powerPress(eventTime, interactive, this.mPowerKeyPressCounter);
        }
        finishPowerKeyPress();
    }

    private void finishPowerKeyPress() {
        this.mBeganFromNonInteractive = false;
        this.mPowerKeyPressCounter = 0;
        if (this.mPowerKeyWakeLock.isHeld()) {
            this.mPowerKeyWakeLock.release();
        }
    }

    private void cancelPendingPowerKeyAction() {
        if (!this.mPowerKeyHandled) {
            this.mPowerKeyHandled = SHOW_STARTING_ANIMATIONS;
            this.mHandler.removeMessages(14);
        }
    }

    private void cancelPendingBackKeyAction() {
        if (!this.mBackKeyHandled) {
            this.mBackKeyHandled = SHOW_STARTING_ANIMATIONS;
            this.mHandler.removeMessages(18);
        }
    }

    private void powerPress(long eventTime, boolean interactive, int count) {
        if (!this.mScreenOnEarly || this.mScreenOnFully) {
            if (count != 2) {
                if (count != 3) {
                    if (interactive && !this.mBeganFromNonInteractive) {
                        switch (this.mShortPressOnPowerBehavior) {
                            case 0:
                                break;
                            case 1:
                                this.mPowerManager.goToSleep(eventTime, 4, 0);
                                break;
                            case 2:
                                this.mPowerManager.goToSleep(eventTime, 4, 1);
                                break;
                            case 3:
                                this.mPowerManager.goToSleep(eventTime, 4, 1);
                                launchHomeFromHotKey();
                                break;
                            case 4:
                                launchHomeFromHotKey(SHOW_STARTING_ANIMATIONS, false);
                                break;
                            default:
                                break;
                        }
                    }
                }
                powerMultiPressAction(eventTime, interactive, this.mTriplePressOnPowerBehavior);
            } else {
                powerMultiPressAction(eventTime, interactive, this.mDoublePressOnPowerBehavior);
            }
            return;
        }
        Slog.i(TAG, "Suppressed redundant power key press while already in the process of turning the screen on.");
    }

    private void powerMultiPressAction(long eventTime, boolean interactive, int behavior) {
        switch (behavior) {
            case 1:
                if (!isUserSetupComplete()) {
                    Slog.i(TAG, "Ignoring toggling theater mode - device not setup.");
                    return;
                } else if (isTheaterModeEnabled()) {
                    Slog.i(TAG, "Toggling theater mode off.");
                    Global.putInt(this.mContext.getContentResolver(), "theater_mode_on", 0);
                    if (!interactive) {
                        wakeUpFromPowerKey(eventTime);
                        return;
                    }
                    return;
                } else {
                    Slog.i(TAG, "Toggling theater mode on.");
                    Global.putInt(this.mContext.getContentResolver(), "theater_mode_on", 1);
                    if (this.mGoToSleepOnButtonPressTheaterMode && interactive) {
                        this.mPowerManager.goToSleep(eventTime, 4, 0);
                        return;
                    }
                    return;
                }
            case 2:
                Slog.i(TAG, "Starting brightness boost.");
                if (!interactive) {
                    wakeUpFromPowerKey(eventTime);
                }
                this.mPowerManager.boostScreenBrightness(eventTime);
                return;
            default:
                return;
        }
    }

    private int getMaxMultiPressPowerCount() {
        if (this.mTriplePressOnPowerBehavior != 0) {
            return 3;
        }
        if (this.mDoublePressOnPowerBehavior != 0) {
            return 2;
        }
        return 1;
    }

    private void powerLongPress() {
        boolean z = SHOW_STARTING_ANIMATIONS;
        int behavior = getResolvedLongPressOnPowerBehavior();
        switch (behavior) {
            case 1:
                this.mPowerKeyHandled = SHOW_STARTING_ANIMATIONS;
                if (!performHapticFeedbackLw(null, 0, false)) {
                    performAuditoryFeedbackForAccessibilityIfNeed();
                }
                showGlobalActionsInternal();
                return;
            case 2:
            case 3:
                this.mPowerKeyHandled = SHOW_STARTING_ANIMATIONS;
                performHapticFeedbackLw(null, 0, false);
                sendCloseSystemWindows(SYSTEM_DIALOG_REASON_GLOBAL_ACTIONS);
                WindowManagerFuncs windowManagerFuncs = this.mWindowManagerFuncs;
                if (behavior != 2) {
                    z = false;
                }
                windowManagerFuncs.shutdown(z);
                return;
            default:
                return;
        }
    }

    private void backLongPress() {
        this.mBackKeyHandled = SHOW_STARTING_ANIMATIONS;
        switch (this.mLongPressOnBackBehavior) {
            case 1:
                startActivityAsUser(new Intent("android.intent.action.VOICE_ASSIST"), UserHandle.CURRENT_OR_SELF);
                return;
            default:
                return;
        }
    }

    private void disposeInputConsumer(InputConsumer inputConsumer) {
        if (inputConsumer != null) {
            inputConsumer.dismiss();
        }
    }

    private void sleepPress(long eventTime) {
        if (this.mShortPressOnSleepBehavior == 1) {
            launchHomeFromHotKey(false, SHOW_STARTING_ANIMATIONS);
        }
    }

    private void sleepRelease(long eventTime) {
        switch (this.mShortPressOnSleepBehavior) {
            case 0:
            case 1:
                Slog.i(TAG, "sleepRelease() calling goToSleep(GO_TO_SLEEP_REASON_SLEEP_BUTTON)");
                this.mPowerManager.goToSleep(eventTime, 6, 0);
                return;
            default:
                return;
        }
    }

    private int getResolvedLongPressOnPowerBehavior() {
        if (FactoryTest.isLongPressOnPowerOffEnabled()) {
            return 3;
        }
        return this.mLongPressOnPowerBehavior;
    }

    private boolean hasLongPressOnPowerBehavior() {
        return getResolvedLongPressOnPowerBehavior() != 0 ? SHOW_STARTING_ANIMATIONS : false;
    }

    private boolean hasLongPressOnBackBehavior() {
        return this.mLongPressOnBackBehavior != 0 ? SHOW_STARTING_ANIMATIONS : false;
    }

    private void interceptScreenshotChord() {
        if (HWFLOW) {
            Slog.i(TAG, "takeScreenshot enabled  " + this.mScreenshotChordEnabled + "  VolumeDownKeyTriggered  " + this.mScreenshotChordVolumeDownKeyTriggered + " PowerKeyTriggered  " + this.mScreenshotChordPowerKeyTriggered + " VolumeUpKeyTriggered  " + this.mScreenshotChordVolumeUpKeyTriggered);
        }
        if (this.mScreenshotChordEnabled && this.mScreenshotChordVolumeDownKeyTriggered && this.mScreenshotChordPowerKeyTriggered && !this.mScreenshotChordVolumeUpKeyTriggered) {
            long now = SystemClock.uptimeMillis();
            if (HWFLOW) {
                Slog.i(TAG, "takeScreenshot downKeyTime=  " + this.mScreenshotChordVolumeDownKeyTime + " powerKeyTime=  " + this.mScreenshotChordPowerKeyTime + " now = " + now);
            }
            if (now <= this.mScreenshotChordVolumeDownKeyTime + SCREENSHOT_CHORD_DEBOUNCE_DELAY_MILLIS && now <= this.mScreenshotChordPowerKeyTime + SCREENSHOT_CHORD_DEBOUNCE_DELAY_MILLIS) {
                this.mScreenshotChordVolumeDownKeyConsumed = SHOW_STARTING_ANIMATIONS;
                cancelPendingPowerKeyAction();
                this.mScreenshotRunnable.setScreenshotType(1);
                Flog.bdReport(this.mContext, 71);
                this.mHandler.postDelayed(this.mScreenshotRunnable, getScreenshotChordLongPressDelay());
            }
        }
    }

    private long getScreenshotChordLongPressDelay() {
        if (this.mKeyguardDelegate.isShowing()) {
            return (long) (((float) ViewConfiguration.get(this.mContext).getDeviceGlobalActionKeyTimeout()) * KEYGUARD_SCREENSHOT_CHORD_DELAY_MULTIPLIER);
        }
        return 0;
    }

    private void cancelPendingScreenshotChordAction() {
        if (HWFLOW) {
            Slog.i(TAG, "takeScreenshot cancelPendingScreenshotChordAction");
        }
        this.mHandler.removeCallbacks(this.mScreenshotRunnable);
    }

    public void showGlobalActions() {
        this.mHandler.removeMessages(10);
        this.mHandler.sendEmptyMessage(10);
    }

    void showGlobalActionsInternal() {
        sendCloseSystemWindows(SYSTEM_DIALOG_REASON_GLOBAL_ACTIONS);
        if (HwPolicyFactory.ifUseHwGlobalActions()) {
            HwPolicyFactory.showHwGlobalActionsFragment(this.mContext, this.mWindowManagerFuncs, this.mPowerManager, isKeyguardShowingAndNotOccluded(), isKeyguardSecure(this.mCurrentUserId), isDeviceProvisioned());
            return;
        }
        if (this.mGlobalActions == null) {
            this.mGlobalActions = new GlobalActions(this.mContext, this.mWindowManagerFuncs);
        }
        boolean keyguardShowing = isKeyguardShowingAndNotOccluded();
        this.mGlobalActions.showDialog(keyguardShowing, isDeviceProvisioned());
        if (keyguardShowing) {
            this.mPowerManager.userActivity(SystemClock.uptimeMillis(), false);
        }
    }

    boolean isDeviceProvisioned() {
        if (Global.getInt(this.mContext.getContentResolver(), "device_provisioned", 0) != 0) {
            return SHOW_STARTING_ANIMATIONS;
        }
        return false;
    }

    boolean isUserSetupComplete() {
        return Secure.getIntForUser(this.mContext.getContentResolver(), "user_setup_complete", 0, -2) != 0 ? SHOW_STARTING_ANIMATIONS : false;
    }

    private void handleShortPressOnHome() {
        getHdmiControl().turnOnTv();
        if (this.mDreamManagerInternal == null || !this.mDreamManagerInternal.isDreaming()) {
            launchHomeFromHotKey();
        } else {
            this.mDreamManagerInternal.stopDream(false);
        }
    }

    private HdmiControl getHdmiControl() {
        if (this.mHdmiControl == null) {
            HdmiControlManager manager = (HdmiControlManager) this.mContext.getSystemService("hdmi_control");
            HdmiPlaybackClient client = null;
            if (manager != null) {
                client = manager.getPlaybackClient();
            }
            this.mHdmiControl = new HdmiControl(client);
        }
        return this.mHdmiControl;
    }

    private void handleLongPressOnHome(int deviceId) {
        if (this.mLongPressOnHomeBehavior != 0) {
            this.mHomeConsumed = SHOW_STARTING_ANIMATIONS;
            performHapticFeedbackLw(null, 0, false);
            switch (this.mLongPressOnHomeBehavior) {
                case 1:
                    toggleRecentApps();
                    break;
                case 2:
                    launchAssistAction(null, deviceId);
                    break;
                default:
                    Log.w(TAG, "Undefined home long press behavior: " + this.mLongPressOnHomeBehavior);
                    break;
            }
        }
    }

    private void handleDoubleTapOnHome() {
        if (this.mDoubleTapOnHomeBehavior == 1) {
            this.mHomeConsumed = SHOW_STARTING_ANIMATIONS;
            toggleRecentApps();
        }
    }

    private void showTvPictureInPictureMenu(KeyEvent event) {
        this.mHandler.removeMessages(17);
        Message msg = this.mHandler.obtainMessage(17);
        msg.setAsynchronous(SHOW_STARTING_ANIMATIONS);
        msg.sendToTarget();
    }

    private void showTvPictureInPictureMenuInternal() {
        StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
        if (statusbar != null) {
            statusbar.showTvPictureInPictureMenu();
        }
    }

    private boolean isRoundWindow() {
        return this.mContext.getResources().getConfiguration().isScreenRound();
    }

    public void init(Context context, IWindowManager windowManager, WindowManagerFuncs windowManagerFuncs) {
        boolean z;
        this.mContext = context;
        this.mWindowManager = windowManager;
        this.mWindowManagerFuncs = windowManagerFuncs;
        this.mWindowManagerInternal = (WindowManagerInternal) LocalServices.getService(WindowManagerInternal.class);
        this.mActivityManagerInternal = (ActivityManagerInternal) LocalServices.getService(ActivityManagerInternal.class);
        this.mInputManagerInternal = (InputManagerInternal) LocalServices.getService(InputManagerInternal.class);
        this.mDreamManagerInternal = (DreamManagerInternal) LocalServices.getService(DreamManagerInternal.class);
        this.mPowerManagerInternal = (PowerManagerInternal) LocalServices.getService(PowerManagerInternal.class);
        this.mAppOpsManager = (AppOpsManager) this.mContext.getSystemService("appops");
        this.mHasFeatureWatch = this.mContext.getPackageManager().hasSystemFeature("android.hardware.type.watch");
        boolean burnInProtectionEnabled = context.getResources().getBoolean(17957026);
        boolean burnInProtectionDevMode = SystemProperties.getBoolean("persist.debug.force_burn_in", false);
        if (burnInProtectionEnabled || burnInProtectionDevMode) {
            int minHorizontal;
            int maxHorizontal;
            int minVertical;
            int maxVertical;
            int maxRadius;
            if (burnInProtectionDevMode) {
                minHorizontal = -8;
                maxHorizontal = 8;
                minVertical = -8;
                maxVertical = -4;
                if (isRoundWindow()) {
                    maxRadius = 6;
                } else {
                    maxRadius = -1;
                }
            } else {
                Resources resources = context.getResources();
                minHorizontal = resources.getInteger(17694876);
                maxHorizontal = resources.getInteger(17694877);
                minVertical = resources.getInteger(17694878);
                maxVertical = resources.getInteger(17694879);
                maxRadius = resources.getInteger(17694875);
            }
            this.mBurnInProtectionHelper = new BurnInProtectionHelper(context, minHorizontal, maxHorizontal, minVertical, maxVertical, maxRadius);
        }
        PhoneWindowManager phoneWindowManager = this;
        this.mHandler = new PolicyHandler();
        this.mWakeGestureListener = new MyWakeGestureListener(this.mContext, this.mHandler);
        this.mOrientationListener = new MyOrientationListener(this.mContext, this.mHandler);
        try {
            this.mOrientationListener.setCurrentRotation(windowManager.getRotation());
        } catch (RemoteException e) {
        }
        this.mSettingsObserver = new SettingsObserver(this.mHandler);
        this.mSettingsObserver.observe();
        if (mSupportAod) {
            Slog.i(TAG, "AOD mAodSwitchObserver.observe");
            this.mAodSwitchObserver = new AodSwitchObserver(this.mHandler);
            this.mAodSwitchObserver.observe();
        }
        this.mShortcutManager = new ShortcutManager(context);
        this.mUiMode = context.getResources().getInteger(17694797);
        this.mHomeIntent = new Intent("android.intent.action.MAIN", null);
        this.mHomeIntent.addCategory("android.intent.category.HOME");
        this.mHomeIntent.addFlags(270533120);
        this.mEnableCarDockHomeCapture = context.getResources().getBoolean(17956926);
        this.mCarDockIntent = new Intent("android.intent.action.MAIN", null);
        this.mCarDockIntent.addCategory("android.intent.category.CAR_DOCK");
        this.mCarDockIntent.addFlags(270532608);
        this.mDeskDockIntent = new Intent("android.intent.action.MAIN", null);
        this.mDeskDockIntent.addCategory("android.intent.category.DESK_DOCK");
        this.mDeskDockIntent.addFlags(270532608);
        this.mPowerManager = (PowerManager) context.getSystemService("power");
        this.mBroadcastWakeLock = this.mPowerManager.newWakeLock(1, "PhoneWindowManager.mBroadcastWakeLock");
        this.mPowerKeyWakeLock = this.mPowerManager.newWakeLock(1, "PhoneWindowManager.mPowerKeyWakeLock");
        this.mEnableShiftMenuBugReports = "1".equals(SystemProperties.get("ro.debuggable"));
        this.mSupportAutoRotation = this.mContext.getResources().getBoolean(17956919);
        this.mLidOpenRotation = readRotation(17694789);
        this.mCarDockRotation = readRotation(17694794);
        this.mDeskDockRotation = readRotation(17694792);
        this.mUndockedHdmiRotation = readRotation(17694796);
        this.mCarDockEnablesAccelerometer = this.mContext.getResources().getBoolean(17956925);
        this.mDeskDockEnablesAccelerometer = this.mContext.getResources().getBoolean(17956924);
        this.mLidKeyboardAccessibility = this.mContext.getResources().getInteger(17694790);
        this.mLidNavigationAccessibility = this.mContext.getResources().getInteger(17694791);
        this.mLidControlsScreenLock = this.mContext.getResources().getBoolean(17956922);
        this.mLidControlsSleep = this.mContext.getResources().getBoolean(17956923);
        this.mTranslucentDecorEnabled = this.mContext.getResources().getBoolean(17956937);
        this.mAllowTheaterModeWakeFromKey = this.mContext.getResources().getBoolean(17956911);
        if (this.mAllowTheaterModeWakeFromKey) {
            z = SHOW_STARTING_ANIMATIONS;
        } else {
            z = this.mContext.getResources().getBoolean(17956910);
        }
        this.mAllowTheaterModeWakeFromPowerKey = z;
        this.mAllowTheaterModeWakeFromMotion = this.mContext.getResources().getBoolean(17956912);
        this.mAllowTheaterModeWakeFromMotionWhenNotDreaming = this.mContext.getResources().getBoolean(17956913);
        this.mAllowTheaterModeWakeFromCameraLens = this.mContext.getResources().getBoolean(17956909);
        this.mAllowTheaterModeWakeFromLidSwitch = this.mContext.getResources().getBoolean(17956914);
        this.mAllowTheaterModeWakeFromWakeGesture = this.mContext.getResources().getBoolean(17956908);
        this.mGoToSleepOnButtonPressTheaterMode = this.mContext.getResources().getBoolean(17956917);
        this.mSupportLongPressPowerWhenNonInteractive = this.mContext.getResources().getBoolean(17956918);
        this.mLongPressOnBackBehavior = this.mContext.getResources().getInteger(17694800);
        this.mShortPressOnPowerBehavior = this.mContext.getResources().getInteger(17694801);
        this.mLongPressOnPowerBehavior = this.mContext.getResources().getInteger(17694799);
        this.mDoublePressOnPowerBehavior = this.mContext.getResources().getInteger(17694802);
        this.mTriplePressOnPowerBehavior = this.mContext.getResources().getInteger(17694803);
        this.mShortPressOnSleepBehavior = this.mContext.getResources().getInteger(17694804);
        this.mUseTvRouting = AudioSystem.getPlatformType(this.mContext) == 2 ? SHOW_STARTING_ANIMATIONS : false;
        readConfigurationDependentBehaviors();
        this.mAccessibilityManager = (AccessibilityManager) context.getSystemService("accessibility");
        IntentFilter filter = new IntentFilter();
        filter.addAction(UiModeManager.ACTION_ENTER_CAR_MODE);
        filter.addAction(UiModeManager.ACTION_EXIT_CAR_MODE);
        filter.addAction(UiModeManager.ACTION_ENTER_DESK_MODE);
        filter.addAction(UiModeManager.ACTION_EXIT_DESK_MODE);
        filter.addAction("android.intent.action.DOCK_EVENT");
        Intent intent = context.registerReceiver(this.mDockReceiver, filter);
        context.registerReceiver(this.mShutdownReceiver, new IntentFilter(ACTION_ACTURAL_SHUTDOWN), HUAWEI_SHUTDOWN_PERMISSION, null);
        if (intent != null) {
            this.mDockMode = intent.getIntExtra("android.intent.extra.DOCK_STATE", 0);
        }
        filter = new IntentFilter();
        filter.addAction("android.intent.action.DREAMING_STARTED");
        filter.addAction("android.intent.action.DREAMING_STOPPED");
        context.registerReceiver(this.mDreamReceiver, filter);
        context.registerReceiver(this.mMultiuserReceiver, new IntentFilter("android.intent.action.USER_SWITCHED"));
        this.mSystemGestures = new SystemGesturesPointerEventListener(context, new Callbacks() {
            public void onSwipeFromTop() {
                if (!(PhoneWindowManager.this.isGestureIsolated() || Secure.getInt(PhoneWindowManager.this.mContext.getContentResolver(), "device_provisioned", 1) == 0 || PhoneWindowManager.this.swipeFromTop() || PhoneWindowManager.this.mStatusBar == null)) {
                    PhoneWindowManager.this.requestTransientBars(PhoneWindowManager.this.mStatusBar);
                }
            }

            public void onSwipeFromBottom() {
                if (!(PhoneWindowManager.this.isGestureIsolated() || Secure.getInt(PhoneWindowManager.this.mContext.getContentResolver(), "device_provisioned", 1) == 0 || PhoneWindowManager.this.swipeFromBottom() || PhoneWindowManager.this.mNavigationBar == null || !PhoneWindowManager.this.mNavigationBarOnBottom)) {
                    PhoneWindowManager.this.requestTransientBars(PhoneWindowManager.this.mNavigationBar);
                }
            }

            public void onSwipeFromRight() {
                if (!(PhoneWindowManager.this.isGestureIsolated() || PhoneWindowManager.this.swipeFromRight() || PhoneWindowManager.this.mNavigationBar == null || PhoneWindowManager.this.mNavigationBarOnBottom)) {
                    PhoneWindowManager.this.requestTransientBars(PhoneWindowManager.this.mNavigationBar);
                }
            }

            public void onFling(int duration) {
                if (PhoneWindowManager.this.mPowerManagerInternal != null) {
                    PhoneWindowManager.this.mPowerManagerInternal.powerHint(2, duration);
                }
            }

            public void onDebug() {
            }

            public void onDown() {
                PhoneWindowManager.this.mOrientationListener.onTouchStart();
            }

            public void onUpOrCancel() {
                PhoneWindowManager.this.mOrientationListener.onTouchEnd();
            }

            public void onMouseHoverAtTop() {
                PhoneWindowManager.this.mHandler.removeMessages(16);
                Message msg = PhoneWindowManager.this.mHandler.obtainMessage(16);
                msg.arg1 = 0;
                PhoneWindowManager.this.mHandler.sendMessageDelayed(msg, 500);
            }

            public void onMouseHoverAtBottom() {
                PhoneWindowManager.this.mHandler.removeMessages(16);
                Message msg = PhoneWindowManager.this.mHandler.obtainMessage(16);
                msg.arg1 = 1;
                PhoneWindowManager.this.mHandler.sendMessageDelayed(msg, 500);
            }

            public void onMouseLeaveFromEdge() {
                PhoneWindowManager.this.mHandler.removeMessages(16);
            }
        });
        this.mImmersiveModeConfirmation = new ImmersiveModeConfirmation(this.mContext);
        this.mWindowManagerFuncs.registerPointerEventListener(this.mSystemGestures);
        this.mVibrator = (Vibrator) context.getSystemService("vibrator");
        this.mLongPressVibePattern = getLongIntArray(this.mContext.getResources(), 17235999);
        this.mVirtualKeyVibePattern = getLongIntArray(this.mContext.getResources(), 17236000);
        this.mKeyboardTapVibePattern = getLongIntArray(this.mContext.getResources(), 17236001);
        this.mClockTickVibePattern = getLongIntArray(this.mContext.getResources(), 17236002);
        this.mCalendarDateVibePattern = getLongIntArray(this.mContext.getResources(), 17236003);
        this.mSafeModeDisabledVibePattern = getLongIntArray(this.mContext.getResources(), 17236004);
        this.mSafeModeEnabledVibePattern = getLongIntArray(this.mContext.getResources(), 17236005);
        this.mContextClickVibePattern = getLongIntArray(this.mContext.getResources(), 17236007);
        this.mScreenshotChordEnabled = this.mContext.getResources().getBoolean(17956906);
        this.mGlobalKeyManager = new GlobalKeyManager(this.mContext);
        initializeHdmiState();
        if (!this.mPowerManager.isInteractive()) {
            startedGoingToSleep(2);
            finishedGoingToSleep(2);
        }
        this.mWindowManagerInternal.registerAppTransitionListener(this.mStatusBarController.getAppTransitionListener());
        hwInit();
    }

    private void readConfigurationDependentBehaviors() {
        Resources res = this.mContext.getResources();
        this.mLongPressOnHomeBehavior = res.getInteger(17694817);
        if (this.mLongPressOnHomeBehavior < 0 || this.mLongPressOnHomeBehavior > 2) {
            this.mLongPressOnHomeBehavior = 0;
        }
        this.mDoubleTapOnHomeBehavior = res.getInteger(17694818);
        if (this.mDoubleTapOnHomeBehavior < 0 || this.mDoubleTapOnHomeBehavior > 1) {
            this.mDoubleTapOnHomeBehavior = 0;
        }
        this.mShortPressWindowBehavior = 0;
        if (this.mContext.getPackageManager().hasSystemFeature("android.software.picture_in_picture")) {
            this.mShortPressWindowBehavior = 1;
        }
        this.mNavBarOpacityMode = res.getInteger(17694884);
    }

    public void setInitialDisplaySize(Display display, int width, int height, int density) {
        if (this.mContext != null && display.getDisplayId() == 0) {
            int shortSize;
            int longSize;
            this.mDisplay = display;
            Resources res = this.mContext.getResources();
            if (width > height) {
                shortSize = height;
                longSize = width;
                this.mLandscapeRotation = 0;
                this.mSeascapeRotation = 2;
                if (res.getBoolean(17956921)) {
                    this.mPortraitRotation = 1;
                    this.mUpsideDownRotation = 3;
                } else {
                    this.mPortraitRotation = 3;
                    this.mUpsideDownRotation = 1;
                }
            } else {
                shortSize = width;
                longSize = height;
                this.mPortraitRotation = 0;
                this.mUpsideDownRotation = 2;
                if (res.getBoolean(17956921)) {
                    this.mLandscapeRotation = 3;
                    this.mSeascapeRotation = 1;
                } else {
                    this.mLandscapeRotation = 1;
                    this.mSeascapeRotation = 3;
                }
            }
            int shortSizeDp = (shortSize * 160) / density;
            int longSizeDp = (longSize * 160) / density;
            boolean z = (width == height || shortSizeDp > Systemex.getInt(this.mContext.getContentResolver(), "hw_split_navigation_bar_dp", 600)) ? false : SHOW_STARTING_ANIMATIONS;
            this.mNavigationBarCanMove = z;
            if (this.mNavigationBarCanMove) {
                int defaultRotation = SystemProperties.getInt("ro.panel.hw_orientation", 0) / 90;
                if (defaultRotation == 1 || defaultRotation == 3) {
                    this.mNavigationBarCanMove = false;
                }
            }
            this.mHasNavigationBar = res.getBoolean(17956970);
            String navBarOverride = SystemProperties.get("qemu.hw.mainkeys");
            if ("1".equals(navBarOverride)) {
                this.mHasNavigationBar = false;
            } else if ("0".equals(navBarOverride)) {
                this.mHasNavigationBar = SHOW_STARTING_ANIMATIONS;
            }
            if ("portrait".equals(SystemProperties.get("persist.demo.hdmirotation"))) {
                this.mDemoHdmiRotation = this.mPortraitRotation;
            } else {
                this.mDemoHdmiRotation = this.mLandscapeRotation;
            }
            this.mDemoHdmiRotationLock = SystemProperties.getBoolean("persist.demo.hdmirotationlock", false);
            if ("portrait".equals(SystemProperties.get("persist.demo.remoterotation"))) {
                this.mDemoRotation = this.mPortraitRotation;
            } else {
                this.mDemoRotation = this.mLandscapeRotation;
            }
            this.mDemoRotationLock = SystemProperties.getBoolean("persist.demo.rotationlock", false);
            z = (longSizeDp < 960 || shortSizeDp < 720 || !res.getBoolean(17956996)) ? false : "true".equals(SystemProperties.get("config.override_forced_orient")) ? false : SHOW_STARTING_ANIMATIONS;
            this.mForceDefaultOrientation = z;
        }
    }

    private boolean canHideNavigationBar() {
        return this.mHasNavigationBar;
    }

    public boolean isDefaultOrientationForced() {
        return this.mForceDefaultOrientation;
    }

    public void setDisplayOverscan(Display display, int left, int top, int right, int bottom) {
        if (display.getDisplayId() == 0) {
            this.mOverscanLeft = left;
            this.mOverscanTop = top;
            this.mOverscanRight = right;
            this.mOverscanBottom = bottom;
        }
    }

    public void updateSettings() {
        int i = 2;
        ContentResolver resolver = this.mContext.getContentResolver();
        boolean updateRotation = false;
        synchronized (this.mLock) {
            int userRotationMode;
            this.mEndcallBehavior = System.getIntForUser(resolver, "end_button_behavior", 2, -2);
            this.mIncallPowerBehavior = Secure.getIntForUser(resolver, "incall_power_button_behavior", 1, -2);
            boolean wakeGestureEnabledSetting = Secure.getIntForUser(resolver, "wake_gesture_enabled", 0, -2) != 0 ? SHOW_STARTING_ANIMATIONS : false;
            if (this.mWakeGestureEnabledSetting != wakeGestureEnabledSetting) {
                this.mWakeGestureEnabledSetting = wakeGestureEnabledSetting;
                updateWakeGestureListenerLp();
            }
            int userRotation = System.getIntForUser(resolver, "user_rotation", 0, -2);
            if (this.mUserRotation != userRotation) {
                this.mUserRotation = userRotation;
                updateRotation = SHOW_STARTING_ANIMATIONS;
            }
            if (System.getIntForUser(resolver, "accelerometer_rotation", 0, -2) != 0) {
                userRotationMode = 0;
            } else {
                userRotationMode = 1;
            }
            if (this.mUserRotationMode != userRotationMode) {
                this.mUserRotationMode = userRotationMode;
                updateRotation = SHOW_STARTING_ANIMATIONS;
                updateOrientationListenerLp();
            }
            if (this.mSystemReady) {
                int pointerLocation = System.getIntForUser(resolver, "pointer_location", 0, -2);
                if (this.mPointerLocationMode != pointerLocation) {
                    this.mPointerLocationMode = pointerLocation;
                    Handler handler = this.mHandler;
                    if (pointerLocation != 0) {
                        i = 1;
                    }
                    handler.sendEmptyMessage(i);
                }
            }
            this.mLockScreenTimeout = System.getIntForUser(resolver, "screen_off_timeout", 0, -2);
            String imId = Secure.getStringForUser(resolver, "default_input_method", -2);
            boolean hasSoftInput = (imId == null || imId.length() <= 0) ? false : SHOW_STARTING_ANIMATIONS;
            if (this.mHasSoftInput != hasSoftInput) {
                this.mHasSoftInput = hasSoftInput;
                updateRotation = SHOW_STARTING_ANIMATIONS;
            }
            if (this.mImmersiveModeConfirmation != null) {
                this.mImmersiveModeConfirmation.loadSetting(this.mCurrentUserId);
            }
        }
        synchronized (this.mWindowManagerFuncs.getWindowManagerLock()) {
            PolicyControl.reloadFromSetting(this.mContext);
        }
        if (updateRotation) {
            updateRotation(SHOW_STARTING_ANIMATIONS);
        }
    }

    private void updateWakeGestureListenerLp() {
        if (shouldEnableWakeGestureLp()) {
            this.mWakeGestureListener.requestWakeUpTrigger();
        } else {
            this.mWakeGestureListener.cancelWakeUpTrigger();
        }
    }

    private boolean shouldEnableWakeGestureLp() {
        if (!this.mWakeGestureEnabledSetting || this.mAwake) {
            return false;
        }
        if (this.mLidControlsSleep && this.mLidState == 0) {
            return false;
        }
        return this.mWakeGestureListener.isSupported();
    }

    private void enablePointerLocation() {
        if (this.mPointerLocationView == null) {
            this.mPointerLocationView = new PointerLocationView(this.mContext);
            this.mPointerLocationView.setPrintCoords(false);
            LayoutParams lp = new LayoutParams(-1, -1);
            lp.type = 2015;
            lp.flags = 1304;
            if (ActivityManager.isHighEndGfx()) {
                lp.flags |= 16777216;
                lp.privateFlags |= 2;
            }
            lp.format = -3;
            lp.setTitle("PointerLocation");
            WindowManager wm = (WindowManager) this.mContext.getSystemService("window");
            lp.inputFeatures |= 2;
            wm.addView(this.mPointerLocationView, lp);
            this.mWindowManagerFuncs.registerPointerEventListener(this.mPointerLocationView);
        }
    }

    private void disablePointerLocation() {
        if (this.mPointerLocationView != null) {
            this.mWindowManagerFuncs.unregisterPointerEventListener(this.mPointerLocationView);
            ((WindowManager) this.mContext.getSystemService("window")).removeView(this.mPointerLocationView);
            this.mPointerLocationView = null;
        }
    }

    private int readRotation(int resID) {
        try {
            switch (this.mContext.getResources().getInteger(resID)) {
                case 0:
                    return 0;
                case 90:
                    return 1;
                case 180:
                    return 2;
                case 270:
                    return 3;
            }
        } catch (NotFoundException e) {
        }
        return -1;
    }

    public int checkAddPermission(LayoutParams attrs, int[] outAppOp) {
        int type = attrs.type;
        outAppOp[0] = -1;
        if ((type < 1 || type > 99) && ((type < 1000 || type > 1999) && (type < IHwShutdownThread.SHUTDOWN_ANIMATION_WAIT_TIME || type > 2999))) {
            return -10;
        }
        if (type < IHwShutdownThread.SHUTDOWN_ANIMATION_WAIT_TIME || type > 2999) {
            return 0;
        }
        String permission = null;
        switch (type) {
            case 2002:
            case 2003:
            case 2006:
            case 2007:
            case 2010:
                if (!HwSystemManager.checkWindowType(attrs.privateFlags)) {
                    permission = "android.permission.SYSTEM_ALERT_WINDOW";
                    outAppOp[0] = 24;
                    break;
                }
                return 0;
            case 2005:
                outAppOp[0] = 45;
                break;
            case 2011:
            case 2013:
            case 2023:
            case 2030:
            case 2031:
            case 2032:
            case 2035:
            case 2102:
                break;
            default:
                permission = "android.permission.INTERNAL_SYSTEM_WINDOW";
                break;
        }
        if (permission != null) {
            if ("android.permission.SYSTEM_ALERT_WINDOW".equals(permission)) {
                int callingUid = Binder.getCallingUid();
                if (callingUid == 1000) {
                    return 0;
                }
                switch (this.mAppOpsManager.checkOpNoThrow(outAppOp[0], callingUid, attrs.packageName)) {
                    case 0:
                    case 1:
                        return 0;
                    case 2:
                        try {
                            return this.mContext.getPackageManager().getApplicationInfo(attrs.packageName, UserHandle.getUserId(callingUid)).targetSdkVersion < 23 ? 0 : -8;
                        } catch (NameNotFoundException e) {
                        }
                    default:
                        return this.mContext.checkCallingPermission(permission) != 0 ? -8 : 0;
                }
            } else if (this.mContext.checkCallingOrSelfPermission(permission) != 0) {
                return -8;
            }
        }
        return 0;
    }

    public boolean checkShowToOwnerOnly(LayoutParams attrs) {
        boolean z = SHOW_STARTING_ANIMATIONS;
        switch (attrs.type) {
            case 3:
            case IHwShutdownThread.SHUTDOWN_ANIMATION_WAIT_TIME /*2000*/:
            case 2001:
            case 2002:
            case 2007:
            case 2008:
            case 2009:
            case 2014:
            case 2017:
            case 2018:
            case 2019:
            case 2020:
            case 2021:
            case 2022:
            case 2024:
            case 2026:
            case 2027:
            case 2029:
            case 2030:
            case 2034:
                break;
            default:
                if ((attrs.privateFlags & 16) == 0) {
                    return SHOW_STARTING_ANIMATIONS;
                }
                break;
        }
        if (this.mContext.checkCallingOrSelfPermission("android.permission.INTERNAL_SYSTEM_WINDOW") == 0) {
            z = false;
        }
        return z;
    }

    public void adjustWindowParamsLw(LayoutParams attrs) {
        switch (attrs.type) {
            case IHwShutdownThread.SHUTDOWN_ANIMATION_WAIT_TIME /*2000*/:
                if (this.mKeyguardHidden) {
                    attrs.flags &= -1048577;
                    attrs.privateFlags &= -1025;
                    break;
                }
                break;
            case 2006:
            case 2015:
                attrs.flags |= 24;
                attrs.flags &= -262145;
                break;
        }
        if (attrs.type != IHwShutdownThread.SHUTDOWN_ANIMATION_WAIT_TIME) {
            attrs.privateFlags &= -1025;
        }
        if (ActivityManager.isHighEndGfx()) {
            if ((attrs.flags & Integer.MIN_VALUE) != 0) {
                attrs.subtreeSystemUiVisibility |= 512;
            }
            boolean forceWindowDrawsStatusBarBackground = (attrs.privateFlags & DumpState.DUMP_INTENT_FILTER_VERIFIERS) != 0 ? SHOW_STARTING_ANIMATIONS : false;
            if ((attrs.flags & Integer.MIN_VALUE) != 0 || (forceWindowDrawsStatusBarBackground && attrs.height == -1 && attrs.width == -1)) {
                attrs.subtreeSystemUiVisibility |= 1024;
            }
        }
    }

    void readLidState() {
        this.mLidState = this.mWindowManagerFuncs.getLidState();
    }

    private void readCameraLensCoverState() {
        this.mCameraLensCoverState = this.mWindowManagerFuncs.getCameraLensCoverState();
    }

    private boolean isHidden(int accessibilityMode) {
        boolean z = SHOW_STARTING_ANIMATIONS;
        switch (accessibilityMode) {
            case 1:
                if (this.mLidState != 0) {
                    z = false;
                }
                return z;
            case 2:
                if (this.mLidState != 1) {
                    z = false;
                }
                return z;
            default:
                return false;
        }
    }

    public void adjustConfigurationLw(Configuration config, int keyboardPresence, int navigationPresence) {
        boolean z = false;
        if ((keyboardPresence & 1) != 0) {
            z = SHOW_STARTING_ANIMATIONS;
        }
        this.mHaveBuiltInKeyboard = z;
        readConfigurationDependentBehaviors();
        readLidState();
        if (config.keyboard == 1 || (keyboardPresence == 1 && isHidden(this.mLidKeyboardAccessibility))) {
            config.hardKeyboardHidden = 2;
            if (!this.mHasSoftInput) {
                config.keyboardHidden = 2;
            }
        }
        if (config.navigation == 1 || (navigationPresence == 1 && isHidden(this.mLidNavigationAccessibility))) {
            config.navigationHidden = 2;
        }
    }

    public void onConfigurationChanged() {
        Resources res = this.mContext.getResources();
        this.mStatusBarHeight = res.getDimensionPixelSize(17104919);
        int[] iArr = this.mNavigationBarHeightForRotationDefault;
        int i = this.mPortraitRotation;
        int dimensionPixelSize = res.getDimensionPixelSize(17104920);
        this.mNavigationBarHeightForRotationDefault[this.mUpsideDownRotation] = dimensionPixelSize;
        iArr[i] = dimensionPixelSize;
        iArr = this.mNavigationBarHeightForRotationDefault;
        i = this.mLandscapeRotation;
        dimensionPixelSize = res.getDimensionPixelSize(17104921);
        this.mNavigationBarHeightForRotationDefault[this.mSeascapeRotation] = dimensionPixelSize;
        iArr[i] = dimensionPixelSize;
        iArr = this.mNavigationBarWidthForRotationDefault;
        i = this.mPortraitRotation;
        dimensionPixelSize = res.getDimensionPixelSize(17104922);
        this.mNavigationBarWidthForRotationDefault[this.mSeascapeRotation] = dimensionPixelSize;
        this.mNavigationBarWidthForRotationDefault[this.mLandscapeRotation] = dimensionPixelSize;
        this.mNavigationBarWidthForRotationDefault[this.mUpsideDownRotation] = dimensionPixelSize;
        iArr[i] = dimensionPixelSize;
        iArr = this.mNavigationBarHeightForRotationInCarMode;
        i = this.mPortraitRotation;
        dimensionPixelSize = res.getDimensionPixelSize(17104923);
        this.mNavigationBarHeightForRotationInCarMode[this.mUpsideDownRotation] = dimensionPixelSize;
        iArr[i] = dimensionPixelSize;
        iArr = this.mNavigationBarHeightForRotationInCarMode;
        i = this.mLandscapeRotation;
        dimensionPixelSize = res.getDimensionPixelSize(17104924);
        this.mNavigationBarHeightForRotationInCarMode[this.mSeascapeRotation] = dimensionPixelSize;
        iArr[i] = dimensionPixelSize;
        iArr = this.mNavigationBarWidthForRotationInCarMode;
        i = this.mPortraitRotation;
        dimensionPixelSize = res.getDimensionPixelSize(17104925);
        this.mNavigationBarWidthForRotationInCarMode[this.mSeascapeRotation] = dimensionPixelSize;
        this.mNavigationBarWidthForRotationInCarMode[this.mLandscapeRotation] = dimensionPixelSize;
        this.mNavigationBarWidthForRotationInCarMode[this.mUpsideDownRotation] = dimensionPixelSize;
        iArr[i] = dimensionPixelSize;
    }

    public int windowTypeToLayerLw(int type) {
        if (type >= 1 && type <= 99) {
            return 2;
        }
        switch (type) {
            case IHwShutdownThread.SHUTDOWN_ANIMATION_WAIT_TIME /*2000*/:
                return 16;
            case 2001:
            case 2033:
                return 4;
            case 2002:
                return 3;
            case 2003:
                return 11;
            case 2005:
                return 8;
            case 2006:
                return 20;
            case 2007:
                return 9;
            case 2008:
                return 7;
            case 2009:
                return 18;
            case 2010:
                return 24;
            case 2011:
                return 12;
            case 2012:
                return 13;
            case 2013:
                return 2;
            case 2014:
                return 17;
            case 2015:
                return 29;
            case 2016:
                return 27;
            case 2017:
                return 15;
            case 2018:
                return 31;
            case 2019:
                return 21;
            case 2020:
                return 19;
            case 2021:
                return 30;
            case 2022:
                return 6;
            case 2023:
            case 2102:
                return 10;
            case 2024:
                return 22;
            case 2026:
                return 26;
            case 2027:
                return 25;
            case 2029:
                return 14;
            case 2030:
                return 2;
            case 2031:
                return 5;
            case 2032:
                return 28;
            case 2034:
                return 2;
            case 2035:
                return 2;
            case 2036:
                return 23;
            default:
                Log.e(TAG, "Unknown window type: " + type);
                return 2;
        }
    }

    public int subWindowTypeToLayerLw(int type) {
        switch (type) {
            case 1000:
            case 1003:
                return 1;
            case 1001:
                return -2;
            case 1002:
                return 2;
            case 1004:
                return -1;
            case 1005:
                return 3;
            default:
                Log.e(TAG, "Unknown sub-window type: " + type);
                return 0;
        }
    }

    public int getMaxWallpaperLayer() {
        return windowTypeToLayerLw(IHwShutdownThread.SHUTDOWN_ANIMATION_WAIT_TIME);
    }

    private int getNavigationBarWidth(int rotation, int uiMode) {
        if ((uiMode & 15) == 3) {
            return this.mNavigationBarWidthForRotationInCarMode[rotation];
        }
        return this.mNavigationBarWidthForRotationDefault[rotation];
    }

    public int getNonDecorDisplayWidth(int fullWidth, int fullHeight, int rotation, int uiMode) {
        if (this.mHasNavigationBar && this.mNavigationBarCanMove && fullWidth > fullHeight) {
            return fullWidth - getNavigationBarWidth(rotation, uiMode);
        }
        return fullWidth;
    }

    private int getNavigationBarHeight(int rotation, int uiMode) {
        if ((uiMode & 15) == 3) {
            return this.mNavigationBarHeightForRotationInCarMode[rotation];
        }
        return this.mNavigationBarHeightForRotationDefault[rotation];
    }

    public int getNonDecorDisplayHeight(int fullWidth, int fullHeight, int rotation, int uiMode) {
        if (!this.mHasNavigationBar || (this.mNavigationBarCanMove && fullWidth >= fullHeight)) {
            return fullHeight;
        }
        return fullHeight - getNavigationBarHeight(rotation, uiMode);
    }

    public int getConfigDisplayWidth(int fullWidth, int fullHeight, int rotation, int uiMode) {
        return getNonDecorDisplayWidth(fullWidth, fullHeight, rotation, uiMode);
    }

    public int getConfigDisplayHeight(int fullWidth, int fullHeight, int rotation, int uiMode) {
        return getNonDecorDisplayHeight(fullWidth, fullHeight, rotation, uiMode) - this.mStatusBarHeight;
    }

    public boolean isForceHiding(LayoutParams attrs) {
        if ((attrs.privateFlags & 1024) == 0) {
            return ((isKeyguardHostWindow(attrs) && this.mKeyguardDelegate != null && this.mKeyguardDelegate.isShowing()) || attrs.type == 2029) ? SHOW_STARTING_ANIMATIONS : false;
        } else {
            return SHOW_STARTING_ANIMATIONS;
        }
    }

    public boolean isKeyguardHostWindow(LayoutParams attrs) {
        return attrs.type == IHwShutdownThread.SHUTDOWN_ANIMATION_WAIT_TIME ? SHOW_STARTING_ANIMATIONS : false;
    }

    public boolean canBeForceHidden(WindowState win, LayoutParams attrs) {
        boolean z = false;
        switch (attrs.type) {
            case IHwShutdownThread.SHUTDOWN_ANIMATION_WAIT_TIME /*2000*/:
            case 2013:
            case 2019:
            case 2023:
            case 2029:
            case 2102:
                return false;
            default:
                if (windowTypeToLayerLw(win.getBaseType()) < windowTypeToLayerLw(IHwShutdownThread.SHUTDOWN_ANIMATION_WAIT_TIME)) {
                    z = SHOW_STARTING_ANIMATIONS;
                }
                return z;
        }
    }

    public WindowState getWinShowWhenLockedLw() {
        return this.mWinShowWhenLocked;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public View addStartingWindow(IBinder appToken, String packageName, int theme, CompatibilityInfo compatInfo, CharSequence nonLocalizedLabel, int labelRes, int icon, int logo, int windowFlags, Configuration overrideConfig) {
        if (packageName == null) {
            return null;
        }
        View view = null;
        try {
            View view2;
            Context context = this.mContext;
            if (!(theme == context.getThemeResId() && labelRes == 0)) {
                try {
                    context = context.createPackageContext(packageName, 0);
                    context.setTheme(theme);
                } catch (NameNotFoundException e) {
                }
            }
            if (overrideConfig != null) {
                if (overrideConfig != Configuration.EMPTY) {
                    Context overrideContext = context.createConfigurationContext(overrideConfig);
                    overrideContext.setTheme(theme);
                    int resId = overrideContext.obtainStyledAttributes(R.styleable.Window).getResourceId(1, 0);
                    if (!(resId == 0 || overrideContext.getDrawable(resId) == null)) {
                        context = overrideContext;
                    }
                }
            }
            PhoneWindow win = (PhoneWindow) HwPolicyFactory.getHwPhoneWindow(context);
            win.setIsStartingWindow(SHOW_STARTING_ANIMATIONS);
            TypedArray ta = win.getWindowStyle();
            if (ta.getBoolean(12, false) || ta.getBoolean(14, false)) {
                if (!HISI_PERF_OPT) {
                    return null;
                }
                boolean accel = false;
                try {
                    accel = this.mWindowManager.getAccelPackages(packageName);
                } catch (RemoteException e2) {
                }
                Slog.i(TAG, "addStartingWindow pkgname " + packageName + ".accel " + accel);
                if (!accel || ta.getBoolean(14, false) || ta.getResourceId(1, 0) == 0) {
                    return null;
                }
            }
            CharSequence label = context.getResources().getText(labelRes, null);
            if (label != null) {
                win.setTitle(label, SHOW_STARTING_ANIMATIONS);
            } else {
                win.setTitle(nonLocalizedLabel, false);
            }
            win.setType(3);
            synchronized (this.mWindowManagerFuncs.getWindowManagerLock()) {
                if (this.mKeyguardHidden) {
                    windowFlags |= DumpState.DUMP_FROZEN;
                }
            }
            if (HwWidgetFactory.isHwDarkTheme(context)) {
                windowFlags |= 134217728;
            }
            win.setFlags(((windowFlags | 16) | 8) | DumpState.DUMP_INTENT_FILTER_VERIFIERS, ((windowFlags | 16) | 8) | DumpState.DUMP_INTENT_FILTER_VERIFIERS);
            win.setDefaultIcon(icon);
            win.setDefaultLogo(logo);
            win.setLayout(-1, -1);
            LayoutParams params = win.getAttributes();
            params.token = appToken;
            params.packageName = packageName;
            params.windowAnimations = win.getWindowStyle().getResourceId(8, 0);
            params.privateFlags |= 1;
            params.privateFlags |= 16;
            if (!compatInfo.supportsScreen()) {
                params.privateFlags |= 128;
            }
            params.setTitle("Starting " + packageName);
            WindowManager wm = (WindowManager) context.getSystemService("window");
            view = win.getDecorView();
            Flog.i(301, "addStartingWindow " + packageName + ": nonLocalizedLabel=" + nonLocalizedLabel + " theme=" + Integer.toHexString(theme) + " windowFlags=" + Integer.toHexString(windowFlags) + " isFloating=" + win.isFloating() + " appToken=" + appToken);
            setHasAcitionBar(win.hasFeature(8));
            wm.addView(view, params);
            if (view.getParent() != null) {
                view2 = view;
            } else {
                view2 = null;
            }
            if (view != null && view.getParent() == null) {
                Log.w(TAG, "view not successfully added to wm, removing view");
                wm.removeViewImmediate(view);
            }
            return view2;
        } catch (BadTokenException e3) {
            Log.w(TAG, appToken + " already running, starting window not displayed. " + e3.getMessage());
            if (view != null && view.getParent() == null) {
                Log.w(TAG, "view not successfully added to wm, removing view");
                null.removeViewImmediate(view);
            }
        } catch (RuntimeException e4) {
            Log.w(TAG, appToken + " failed creating starting window", e4);
            if (view != null && view.getParent() == null) {
                Log.w(TAG, "view not successfully added to wm, removing view");
                null.removeViewImmediate(view);
            }
        } catch (Throwable th) {
            if (view != null && view.getParent() == null) {
                Log.w(TAG, "view not successfully added to wm, removing view");
                null.removeViewImmediate(view);
            }
        }
    }

    public void removeStartingWindow(IBinder appToken, View window) {
        Flog.i(301, "Removing starting window for " + appToken + ": " + window);
        if (window != null) {
            ((WindowManager) this.mContext.getSystemService("window")).removeView(window);
        }
    }

    public int prepareAddWindowLw(WindowState win, LayoutParams attrs) {
        switch (attrs.type) {
            case IHwShutdownThread.SHUTDOWN_ANIMATION_WAIT_TIME /*2000*/:
                this.mContext.enforceCallingOrSelfPermission("android.permission.STATUS_BAR_SERVICE", "PhoneWindowManager");
                if (this.mStatusBar == null || !this.mStatusBar.isAlive()) {
                    this.mStatusBar = win;
                    this.mStatusBarController.setWindow(win);
                    break;
                }
                return -7;
            case 2014:
            case 2017:
            case 2024:
            case 2033:
                this.mContext.enforceCallingOrSelfPermission("android.permission.STATUS_BAR_SERVICE", "PhoneWindowManager");
                break;
            case 2019:
                this.mContext.enforceCallingOrSelfPermission("android.permission.STATUS_BAR_SERVICE", "PhoneWindowManager");
                if (this.mNavigationBar == null || !this.mNavigationBar.isAlive()) {
                    this.mNavigationBar = win;
                    this.mNavigationBarController.setWindow(win);
                    break;
                }
                return -7;
            case 2029:
                if (this.mKeyguardScrim == null) {
                    this.mKeyguardScrim = win;
                    break;
                }
                return -7;
        }
        return 0;
    }

    public void removeWindowLw(WindowState win) {
        if (this.mStatusBar == win) {
            this.mStatusBar = null;
            this.mStatusBarController.setWindow(null);
            this.mKeyguardDelegate.showScrim();
        } else if (this.mKeyguardScrim == win) {
            Log.v(TAG, "Removing keyguard scrim");
            this.mKeyguardScrim = null;
        }
        if (this.mNavigationBar == win) {
            this.mNavigationBar = null;
            this.mNavigationBarController.setWindow(null);
        }
    }

    public int selectAnimationLw(WindowState win, int transit) {
        int i = -1;
        if (win == this.mStatusBar) {
            boolean isKeyguard = (win.getAttrs().privateFlags & 1024) != 0 ? SHOW_STARTING_ANIMATIONS : false;
            if (transit == 2 || transit == 4) {
                if (!isKeyguard) {
                    i = 17432618;
                }
                return i;
            } else if (transit == 1 || transit == 3) {
                if (!isKeyguard) {
                    i = 17432617;
                }
                return i;
            }
        } else if (win == this.mNavigationBar) {
            if (win.getAttrs().windowAnimations != 0) {
                return 0;
            }
            if (this.mNavigationBarOnBottom) {
                if (transit == 2 || transit == 4) {
                    return 17432612;
                }
                if (transit == 1 || transit == 3) {
                    return 17432611;
                }
            } else if (transit == 2 || transit == 4) {
                return 17432616;
            } else {
                if (transit == 1 || transit == 3) {
                    return 17432615;
                }
            }
        } else if (win.getAttrs().type == 2034) {
            if ((win.getAttrs().flags & 536870912) != 0) {
                return selectDockedDividerAnimationLw(win, transit);
            }
            return 0;
        }
        if (transit == 5) {
            if (win.hasAppShownWindows()) {
                return 17432593;
            }
        } else if (win.getAttrs().type == 2023 && this.mDreamingLockscreen && transit == 1) {
            return -1;
        } else {
            if (this.mCust != null && this.mCust.isChargingAlbumSupported() && win.getAttrs().type == 2102 && this.mDreamingLockscreen) {
                return this.mCust.selectAnimationLw(transit);
            }
        }
        return 0;
    }

    private int selectDockedDividerAnimationLw(WindowState win, int transit) {
        int insets = this.mWindowManagerFuncs.getDockedDividerInsetsLw();
        Rect frame = win.getFrameLw();
        boolean behindNavBar = this.mNavigationBar != null ? (!this.mNavigationBarOnBottom || frame.top + insets < this.mNavigationBar.getFrameLw().top) ? !this.mNavigationBarOnBottom ? frame.left + insets >= this.mNavigationBar.getFrameLw().left ? SHOW_STARTING_ANIMATIONS : false : false : SHOW_STARTING_ANIMATIONS : false;
        boolean landscape = frame.height() > frame.width() ? SHOW_STARTING_ANIMATIONS : false;
        boolean offscreenLandscape = landscape ? frame.right - insets > 0 ? frame.left + insets >= win.getDisplayFrameLw().right ? SHOW_STARTING_ANIMATIONS : false : SHOW_STARTING_ANIMATIONS : false;
        boolean offscreenPortrait = !landscape ? frame.top - insets > 0 ? frame.bottom + insets >= win.getDisplayFrameLw().bottom ? SHOW_STARTING_ANIMATIONS : false : SHOW_STARTING_ANIMATIONS : false;
        boolean z = !offscreenLandscape ? offscreenPortrait : SHOW_STARTING_ANIMATIONS;
        if (behindNavBar || z) {
            return 0;
        }
        if (transit == 1 || transit == 3) {
            return 17432576;
        }
        if (transit == 2) {
            return 17432577;
        }
        return 0;
    }

    public void selectRotationAnimationLw(int[] anim) {
        if (this.mTopFullscreenOpaqueWindowState == null || !this.mTopIsFullscreen) {
            anim[1] = 0;
            anim[0] = 0;
            return;
        }
        switch (this.mTopFullscreenOpaqueWindowState.getAttrs().rotationAnimation) {
            case 1:
                anim[0] = 17432685;
                anim[1] = 17432683;
                return;
            case 2:
                anim[0] = 17432684;
                anim[1] = 17432683;
                return;
            default:
                anim[1] = 0;
                anim[0] = 0;
                return;
        }
    }

    public boolean validateRotationAnimationLw(int exitAnimId, int enterAnimId, boolean forceDefault) {
        boolean z = SHOW_STARTING_ANIMATIONS;
        switch (exitAnimId) {
            case 17432684:
            case 17432685:
                if (forceDefault) {
                    return false;
                }
                int[] anim = new int[2];
                selectRotationAnimationLw(anim);
                if (!(exitAnimId == anim[0] && enterAnimId == anim[1])) {
                    z = false;
                }
                return z;
            default:
                return SHOW_STARTING_ANIMATIONS;
        }
    }

    public Animation createForceHideEnterAnimation(boolean onWallpaper, boolean goingToNotificationShade) {
        if (goingToNotificationShade) {
            return AnimationUtils.loadAnimation(this.mContext, 17432660);
        }
        int i;
        Context context = this.mContext;
        if (onWallpaper) {
            i = 17432661;
        } else {
            i = 17432659;
        }
        return (AnimationSet) AnimationUtils.loadAnimation(context, i);
    }

    public Animation createForceHideWallpaperExitAnimation(boolean goingToNotificationShade) {
        if (goingToNotificationShade) {
            return null;
        }
        return AnimationUtils.loadAnimation(this.mContext, 17432664);
    }

    private static void awakenDreams() {
        IDreamManager dreamManager = getDreamManager();
        if (dreamManager != null) {
            try {
                dreamManager.awaken();
            } catch (RemoteException e) {
            }
        }
    }

    static IDreamManager getDreamManager() {
        return IDreamManager.Stub.asInterface(ServiceManager.checkService("dreams"));
    }

    TelecomManager getTelecommService() {
        return (TelecomManager) this.mContext.getSystemService("telecom");
    }

    static IAudioService getAudioService() {
        IAudioService audioService = IAudioService.Stub.asInterface(ServiceManager.checkService("audio"));
        if (audioService == null) {
            Log.w(TAG, "Unable to find IAudioService interface.");
        }
        return audioService;
    }

    boolean keyguardOn() {
        return !isKeyguardShowingAndNotOccluded() ? inKeyguardRestrictedKeyInputMode() : SHOW_STARTING_ANIMATIONS;
    }

    public long interceptKeyBeforeDispatching(WindowState win, KeyEvent event, int policyFlags) {
        boolean keyguardOn = keyguardOn();
        int keyCode = event.getKeyCode();
        int repeatCount = event.getRepeatCount();
        int metaState = event.getMetaState();
        int flags = event.getFlags();
        boolean down = event.getAction() == 0 ? SHOW_STARTING_ANIMATIONS : false;
        boolean canceled = event.isCanceled();
        if (this.mScreenshotChordEnabled && (flags & 1024) == 0) {
            if (this.mScreenshotChordVolumeDownKeyTriggered && !this.mScreenshotChordPowerKeyTriggered) {
                long now = SystemClock.uptimeMillis();
                long timeoutTime = this.mScreenshotChordVolumeDownKeyTime + SCREENSHOT_CHORD_DEBOUNCE_DELAY_MILLIS;
                if (now < timeoutTime) {
                    return timeoutTime - now;
                }
            }
            if (keyCode == 25 && this.mScreenshotChordVolumeDownKeyConsumed) {
                if (!down) {
                    this.mScreenshotChordVolumeDownKeyConsumed = false;
                }
                return -1;
            }
        }
        HwFrameworkFactory.getHwNsdImpl().StopSdrForSpecial("interceptKeyBeforeDispatching", keyCode);
        if (this.mPendingMetaAction && !KeyEvent.isMetaKey(keyCode)) {
            this.mPendingMetaAction = false;
        }
        if (!(!this.mPendingCapsLockToggle || KeyEvent.isMetaKey(keyCode) || KeyEvent.isAltKey(keyCode))) {
            this.mPendingCapsLockToggle = false;
        }
        int type;
        if (keyCode != 3) {
            if (keyCode == 82) {
                if (down && repeatCount == 0 && this.mEnableShiftMenuBugReports && (metaState & 1) == 1) {
                    this.mContext.sendOrderedBroadcastAsUser(new Intent("android.intent.action.BUG_REPORT"), UserHandle.CURRENT, null, null, null, 0, null, null);
                    return -1;
                }
            } else if (keyCode == 84) {
                if (!down) {
                    this.mSearchKeyShortcutPending = false;
                    if (this.mConsumeSearchKeyUp) {
                        this.mConsumeSearchKeyUp = false;
                        return -1;
                    }
                } else if (repeatCount == 0) {
                    this.mSearchKeyShortcutPending = SHOW_STARTING_ANIMATIONS;
                    this.mConsumeSearchKeyUp = false;
                }
                return 0;
            } else if (keyCode == 187) {
                if (!keyguardOn) {
                    if (down && repeatCount == 0) {
                        preloadRecentApps();
                    } else if (!down) {
                        toggleRecentApps();
                    }
                }
                return -1;
            } else if (keyCode == 42 && event.isMetaPressed()) {
                if (down) {
                    IStatusBarService service = getStatusBarService();
                    if (service != null) {
                        try {
                            service.expandNotificationsPanel();
                        } catch (RemoteException e) {
                        }
                    }
                }
            } else if (keyCode == 47 && event.isMetaPressed() && event.isCtrlPressed()) {
                if (down && repeatCount == 0) {
                    if (event.isShiftPressed()) {
                        type = 2;
                    } else {
                        type = 1;
                    }
                    this.mScreenshotRunnable.setScreenshotType(type);
                    this.mHandler.post(this.mScreenshotRunnable);
                    return -1;
                }
            } else if (keyCode == 76 && event.isMetaPressed()) {
                if (down && repeatCount == 0 && !isKeyguardLocked()) {
                    toggleKeyboardShortcutsMenu(event.getDeviceId());
                }
            } else if (keyCode == 219) {
                if (down) {
                    if (repeatCount == 0) {
                        this.mAssistKeyLongPressed = false;
                    } else if (repeatCount == 1) {
                        this.mAssistKeyLongPressed = SHOW_STARTING_ANIMATIONS;
                        if (!keyguardOn) {
                            launchAssistLongPressAction();
                        }
                    }
                } else if (this.mAssistKeyLongPressed) {
                    this.mAssistKeyLongPressed = false;
                } else if (!keyguardOn) {
                    launchAssistAction(null, event.getDeviceId());
                }
                return -1;
            } else if (keyCode == 231) {
                if (!down) {
                    Intent intent;
                    if (keyguardOn) {
                        IDeviceIdleController dic = IDeviceIdleController.Stub.asInterface(ServiceManager.getService("deviceidle"));
                        if (dic != null) {
                            try {
                                dic.exitIdle("voice-search");
                            } catch (RemoteException e2) {
                            }
                        }
                        intent = new Intent("android.speech.action.VOICE_SEARCH_HANDS_FREE");
                        intent.putExtra("android.speech.extras.EXTRA_SECURE", SHOW_STARTING_ANIMATIONS);
                    } else {
                        intent = new Intent("android.speech.action.WEB_SEARCH");
                    }
                    startActivityAsUser(voiceIntent, UserHandle.CURRENT_OR_SELF);
                }
            } else if (keyCode == 120) {
                if (down && repeatCount == 0) {
                    this.mScreenshotRunnable.setScreenshotType(1);
                    this.mHandler.post(this.mScreenshotRunnable);
                }
                return -1;
            } else if (keyCode == 221 || keyCode == 220) {
                if (down) {
                    int direction = keyCode == 221 ? 1 : -1;
                    if (System.getIntForUser(this.mContext.getContentResolver(), "screen_brightness_mode", 0, -3) != 0) {
                        System.putIntForUser(this.mContext.getContentResolver(), "screen_brightness_mode", 0, -3);
                    }
                    int min = this.mPowerManager.getMinimumScreenBrightnessSetting();
                    int max = this.mPowerManager.getMaximumScreenBrightnessSetting();
                    System.putIntForUser(this.mContext.getContentResolver(), "screen_brightness", Math.max(min, Math.min(max, System.getIntForUser(this.mContext.getContentResolver(), "screen_brightness", this.mPowerManager.getDefaultScreenBrightnessSetting(), -3) + (((((max - min) + 10) - 1) / 10) * direction))), -3);
                    startActivityAsUser(new Intent("android.intent.action.SHOW_BRIGHTNESS_DIALOG"), UserHandle.CURRENT_OR_SELF);
                }
                return -1;
            } else {
                if (!(keyCode == 24 || keyCode == 25)) {
                    if (keyCode == 164) {
                    }
                }
                if (this.mUseTvRouting) {
                    dispatchDirectAudioEvent(event);
                    return -1;
                }
            }
            boolean actionTriggered = false;
            if (KeyEvent.isModifierKey(keyCode)) {
                if (!this.mPendingCapsLockToggle) {
                    this.mInitialMetaState = this.mMetaState;
                    this.mPendingCapsLockToggle = SHOW_STARTING_ANIMATIONS;
                } else if (event.getAction() == 1) {
                    int altOnMask = this.mMetaState & 50;
                    int metaOnMask = this.mMetaState & 458752;
                    if (!(metaOnMask == 0 || altOnMask == 0 || this.mInitialMetaState != (this.mMetaState ^ (altOnMask | metaOnMask)))) {
                        this.mInputManagerInternal.toggleCapsLock(event.getDeviceId());
                        actionTriggered = SHOW_STARTING_ANIMATIONS;
                    }
                    this.mPendingCapsLockToggle = false;
                }
            }
            this.mMetaState = metaState;
            if (actionTriggered) {
                return -1;
            }
            if (KeyEvent.isMetaKey(keyCode)) {
                if (down) {
                    this.mPendingMetaAction = SHOW_STARTING_ANIMATIONS;
                } else if (this.mPendingMetaAction) {
                    launchAssistAction("android.intent.extra.ASSIST_INPUT_HINT_KEYBOARD", event.getDeviceId());
                }
                return -1;
            }
            KeyCharacterMap kcm;
            Intent shortcutIntent;
            if (this.mSearchKeyShortcutPending) {
                kcm = event.getKeyCharacterMap();
                if (kcm.isPrintingKey(keyCode)) {
                    this.mConsumeSearchKeyUp = SHOW_STARTING_ANIMATIONS;
                    this.mSearchKeyShortcutPending = false;
                    if (down && repeatCount == 0 && !keyguardOn) {
                        shortcutIntent = this.mShortcutManager.getIntent(kcm, keyCode, metaState);
                        if (shortcutIntent != null) {
                            shortcutIntent.addFlags(268435456);
                            try {
                                startActivityAsUser(shortcutIntent, UserHandle.CURRENT);
                                dismissKeyboardShortcutsMenu();
                            } catch (Throwable ex) {
                                Slog.w(TAG, "Dropping shortcut key combination because the activity to which it is registered was not found: SEARCH+" + KeyEvent.keyCodeToString(keyCode), ex);
                            }
                        } else {
                            Slog.i(TAG, "Dropping unregistered shortcut key combination: SEARCH+" + KeyEvent.keyCodeToString(keyCode));
                        }
                    }
                    return -1;
                }
            }
            if (down && repeatCount == 0 && !keyguardOn && (DumpState.DUMP_INSTALLS & metaState) != 0) {
                kcm = event.getKeyCharacterMap();
                if (kcm.isPrintingKey(keyCode)) {
                    shortcutIntent = this.mShortcutManager.getIntent(kcm, keyCode, -458753 & metaState);
                    if (shortcutIntent != null) {
                        shortcutIntent.addFlags(268435456);
                        try {
                            startActivityAsUser(shortcutIntent, UserHandle.CURRENT);
                            dismissKeyboardShortcutsMenu();
                        } catch (Throwable ex2) {
                            Slog.w(TAG, "Dropping shortcut key combination because the activity to which it is registered was not found: META+" + KeyEvent.keyCodeToString(keyCode), ex2);
                        }
                        return -1;
                    }
                }
            }
            if (down && repeatCount == 0 && !keyguardOn) {
                String category = (String) sApplicationLaunchKeyCategories.get(keyCode);
                if (category != null) {
                    Intent intent2 = Intent.makeMainSelectorActivity("android.intent.action.MAIN", category);
                    intent2.setFlags(268435456);
                    if (ActivityManager.isUserAMonkey()) {
                        intent2.addFlags(2097152);
                    }
                    try {
                        startActivityAsUser(intent2, UserHandle.CURRENT);
                        dismissKeyboardShortcutsMenu();
                    } catch (Throwable ex22) {
                        Slog.w(TAG, "Dropping application launch key because the activity to which it is registered was not found: keyCode=" + keyCode + ", category=" + category, ex22);
                    }
                    return -1;
                }
            }
            if (down && repeatCount == 0 && keyCode == 61) {
                if (this.mRecentAppsHeldModifiers == 0 && !keyguardOn && isUserSetupComplete()) {
                    int shiftlessModifiers = event.getModifiers() & -194;
                    if (KeyEvent.metaStateHasModifiers(shiftlessModifiers, 2)) {
                        this.mRecentAppsHeldModifiers = shiftlessModifiers;
                        showRecentApps(SHOW_STARTING_ANIMATIONS, false);
                        return -1;
                    }
                }
            } else if (!(down || this.mRecentAppsHeldModifiers == 0 || (this.mRecentAppsHeldModifiers & metaState) != 0)) {
                this.mRecentAppsHeldModifiers = 0;
                hideRecentApps(SHOW_STARTING_ANIMATIONS, false);
            }
            if (down && repeatCount == 0 && (keyCode == 204 || (keyCode == 62 && (458752 & metaState) != 0))) {
                this.mWindowManagerFuncs.switchInputMethod((metaState & HdmiCecKeycode.UI_SOUND_PRESENTATION_TREBLE_STEP_PLUS) == 0 ? SHOW_STARTING_ANIMATIONS : false);
                return -1;
            } else if (this.mLanguageSwitchKeyPressed && !down && (keyCode == 204 || keyCode == 62)) {
                this.mLanguageSwitchKeyPressed = false;
                return -1;
            } else if (isValidGlobalKey(keyCode) && this.mGlobalKeyManager.handleGlobalKey(this.mContext, keyCode, event)) {
                return -1;
            } else {
                if (down) {
                    long shortcutCode = (long) keyCode;
                    if (event.isCtrlPressed()) {
                        shortcutCode |= 17592186044416L;
                    }
                    if (event.isAltPressed()) {
                        shortcutCode |= 8589934592L;
                    }
                    if (event.isShiftPressed()) {
                        shortcutCode |= 4294967296L;
                    }
                    if (event.isMetaPressed()) {
                        shortcutCode |= 281474976710656L;
                    }
                    IShortcutService shortcutService = (IShortcutService) this.mShortcutKeyServices.get(shortcutCode);
                    if (shortcutService != null) {
                        try {
                            if (isUserSetupComplete()) {
                                shortcutService.notifyShortcutKeyPressed(shortcutCode);
                            }
                        } catch (RemoteException e3) {
                            this.mShortcutKeyServices.delete(shortcutCode);
                        }
                        return -1;
                    }
                }
                if ((DumpState.DUMP_INSTALLS & metaState) != 0) {
                    return -1;
                }
                return 0;
            }
        } else if (down) {
            LayoutParams attrs = win != null ? win.getAttrs() : null;
            if (attrs != null) {
                type = attrs.type;
                if (type == 2029 || type == 2009 || (attrs.privateFlags & 1024) != 0) {
                    return 0;
                }
                for (int i : WINDOW_TYPES_WHERE_HOME_DOESNT_WORK) {
                    if (type == i) {
                        return -1;
                    }
                }
            }
            if (repeatCount == 0) {
                this.mHomePressed = SHOW_STARTING_ANIMATIONS;
                if (this.mHomeDoubleTapPending) {
                    this.mHomeDoubleTapPending = false;
                    this.mHandler.removeCallbacks(this.mHomeDoubleTapTimeoutRunnable);
                    handleDoubleTapOnHome();
                } else if (this.mLongPressOnHomeBehavior == 1 || this.mDoubleTapOnHomeBehavior == 1) {
                    preloadRecentApps();
                }
            } else if (!((event.getFlags() & 128) == 0 || keyguardOn)) {
                handleLongPressOnHome(event.getDeviceId());
            }
            return -1;
        } else {
            cancelPreloadRecentApps();
            this.mHomePressed = false;
            if (this.mHomeConsumed) {
                this.mHomeConsumed = false;
                return -1;
            } else if (canceled) {
                Log.i(TAG, "Ignoring HOME; event canceled.");
                return -1;
            } else {
                TelecomManager telecomManager = getTelecommService();
                String incallscreen = SystemProperties.get("persist.sys.show_incallscreen", "0");
                if (telecomManager != null && telecomManager.isRinging() && "1".equals(incallscreen)) {
                    Log.i(TAG, "Ignoring HOME; there's a ringing incoming call.");
                    return -1;
                } else if (this.mDoubleTapOnHomeBehavior != 0) {
                    this.mHandler.removeCallbacks(this.mHomeDoubleTapTimeoutRunnable);
                    this.mHomeDoubleTapPending = SHOW_STARTING_ANIMATIONS;
                    this.mHandler.postDelayed(this.mHomeDoubleTapTimeoutRunnable, (long) ViewConfiguration.getDoubleTapTimeout());
                    return -1;
                } else {
                    handleShortPressOnHome();
                    return -1;
                }
            }
        }
    }

    public KeyEvent dispatchUnhandledKey(WindowState win, KeyEvent event, int policyFlags) {
        KeyEvent fallbackEvent = null;
        if ((event.getFlags() & 1024) == 0) {
            FallbackAction fallbackAction;
            KeyCharacterMap kcm = event.getKeyCharacterMap();
            int keyCode = event.getKeyCode();
            int metaState = event.getMetaState();
            boolean initialDown = event.getAction() == 0 ? event.getRepeatCount() == 0 ? SHOW_STARTING_ANIMATIONS : false : false;
            if (initialDown) {
                fallbackAction = kcm.getFallbackAction(keyCode, metaState);
            } else {
                fallbackAction = (FallbackAction) this.mFallbackActions.get(keyCode);
            }
            if (fallbackAction != null) {
                fallbackEvent = KeyEvent.obtain(event.getDownTime(), event.getEventTime(), event.getAction(), fallbackAction.keyCode, event.getRepeatCount(), fallbackAction.metaState, event.getDeviceId(), event.getScanCode(), event.getFlags() | 1024, event.getSource(), null);
                if (!interceptFallback(win, fallbackEvent, policyFlags)) {
                    fallbackEvent.recycle();
                    fallbackEvent = null;
                }
                if (initialDown) {
                    this.mFallbackActions.put(keyCode, fallbackAction);
                } else if (event.getAction() == 1) {
                    this.mFallbackActions.remove(keyCode);
                    fallbackAction.recycle();
                }
            }
        }
        return fallbackEvent;
    }

    private boolean interceptFallback(WindowState win, KeyEvent fallbackEvent, int policyFlags) {
        if ((interceptKeyBeforeQueueing(fallbackEvent, policyFlags) & 1) == 0 || interceptKeyBeforeDispatching(win, fallbackEvent, policyFlags) != 0) {
            return false;
        }
        return SHOW_STARTING_ANIMATIONS;
    }

    public void registerShortcutKey(long shortcutCode, IShortcutService shortcutService) throws RemoteException {
        synchronized (this.mLock) {
            IShortcutService service = (IShortcutService) this.mShortcutKeyServices.get(shortcutCode);
            if (service == null || !service.asBinder().pingBinder()) {
                this.mShortcutKeyServices.put(shortcutCode, shortcutService);
            } else {
                throw new RemoteException("Key already exists.");
            }
        }
    }

    private void launchAssistLongPressAction() {
        performHapticFeedbackLw(null, 0, false);
        sendCloseSystemWindows(SYSTEM_DIALOG_REASON_ASSIST);
        Intent intent = new Intent("android.intent.action.SEARCH_LONG_PRESS");
        intent.setFlags(268435456);
        try {
            SearchManager searchManager = getSearchManager();
            if (searchManager != null) {
                searchManager.stopSearch();
            }
            startActivityAsUser(intent, UserHandle.CURRENT);
        } catch (ActivityNotFoundException e) {
            Slog.w(TAG, "No activity to handle assist long press action.", e);
        }
    }

    protected void launchAssistAction() {
        launchAssistAction(null, Integer.MIN_VALUE);
    }

    private void launchAssistAction(String hint) {
        launchAssistAction(hint, Integer.MIN_VALUE);
    }

    protected void launchAssistAction(String hint, int deviceId) {
        sendCloseSystemWindows(SYSTEM_DIALOG_REASON_ASSIST);
        if (isUserSetupComplete()) {
            Bundle bundle = null;
            if (deviceId > Integer.MIN_VALUE) {
                bundle = new Bundle();
                bundle.putInt("android.intent.extra.ASSIST_INPUT_DEVICE_ID", deviceId);
            }
            if ((this.mContext.getResources().getConfiguration().uiMode & 15) == 4) {
                ((SearchManager) this.mContext.getSystemService("search")).launchLegacyAssist(hint, UserHandle.myUserId(), bundle);
            } else {
                if (hint != null) {
                    if (bundle == null) {
                        bundle = new Bundle();
                    }
                    bundle.putBoolean(hint, SHOW_STARTING_ANIMATIONS);
                }
                StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
                if (statusbar != null) {
                    statusbar.startAssist(bundle);
                }
            }
        }
    }

    private void startActivityAsUser(Intent intent, UserHandle handle) {
        if (isUserSetupComplete()) {
            this.mContext.startActivityAsUser(intent, handle);
        } else {
            Slog.i(TAG, "Not starting activity because user setup is in progress: " + intent);
        }
    }

    private SearchManager getSearchManager() {
        if (this.mSearchManager == null) {
            this.mSearchManager = (SearchManager) this.mContext.getSystemService("search");
        }
        return this.mSearchManager;
    }

    protected void preloadRecentApps() {
        this.mPreloadedRecentApps = SHOW_STARTING_ANIMATIONS;
        StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
        if (statusbar != null) {
            statusbar.preloadRecentApps();
        }
    }

    protected void cancelPreloadRecentApps() {
        if (this.mPreloadedRecentApps) {
            this.mPreloadedRecentApps = false;
            StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
            if (statusbar != null) {
                statusbar.cancelPreloadRecentApps();
            }
        }
    }

    protected void toggleRecentApps() {
        this.mPreloadedRecentApps = false;
        StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
        if (statusbar != null) {
            statusbar.toggleRecentApps();
        }
    }

    public void showRecentApps(boolean fromHome) {
        int i;
        this.mHandler.removeMessages(9);
        Handler handler = this.mHandler;
        if (fromHome) {
            i = 1;
        } else {
            i = 0;
        }
        handler.obtainMessage(9, i, 0).sendToTarget();
    }

    private void showRecentApps(boolean triggeredFromAltTab, boolean fromHome) {
        this.mPreloadedRecentApps = false;
        StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
        if (statusbar != null) {
            statusbar.showRecentApps(triggeredFromAltTab, fromHome);
        }
    }

    private void toggleKeyboardShortcutsMenu(int deviceId) {
        StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
        if (statusbar != null) {
            statusbar.toggleKeyboardShortcutsMenu(deviceId);
        }
    }

    private void dismissKeyboardShortcutsMenu() {
        StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
        if (statusbar != null) {
            statusbar.dismissKeyboardShortcutsMenu();
        }
    }

    private void hideRecentApps(boolean triggeredFromAltTab, boolean triggeredFromHome) {
        this.mPreloadedRecentApps = false;
        StatusBarManagerInternal statusbar = getStatusBarManagerInternal();
        if (statusbar != null) {
            statusbar.hideRecentApps(triggeredFromAltTab, triggeredFromHome);
        }
    }

    void launchHomeFromHotKey() {
        launchHomeFromHotKey(SHOW_STARTING_ANIMATIONS, SHOW_STARTING_ANIMATIONS);
    }

    void launchHomeFromHotKey(final boolean awakenFromDreams, boolean respectKeyguard) {
        if (respectKeyguard) {
            if (!isKeyguardShowingAndNotOccluded()) {
                if (!this.mHideLockScreen && this.mKeyguardDelegate.isInputRestricted()) {
                    Slog.w(TAG, "launchHomeFromHotKey verify unlock before launching home");
                    this.mKeyguardDelegate.verifyUnlock(new OnKeyguardExitResult() {
                        public void onKeyguardExitResult(boolean success) {
                            Slog.w(PhoneWindowManager.TAG, "launchHomeFromHotKey onKeyguardExitResult success ? " + success);
                            if (success) {
                                try {
                                    ActivityManagerNative.getDefault().stopAppSwitches();
                                } catch (RemoteException e) {
                                }
                                long origId = Binder.clearCallingIdentity();
                                try {
                                    PhoneWindowManager.this.sendCloseSystemWindows(PhoneWindowManager.SYSTEM_DIALOG_REASON_HOME_KEY);
                                    PhoneWindowManager.this.startDockOrHome(PhoneWindowManager.SHOW_STARTING_ANIMATIONS, awakenFromDreams);
                                } finally {
                                    Binder.restoreCallingIdentity(origId);
                                }
                            }
                        }
                    });
                    return;
                }
            }
            return;
        }
        try {
            ActivityManagerNative.getDefault().stopAppSwitches();
        } catch (RemoteException e) {
        }
        if (this.mRecentsVisible) {
            if (awakenFromDreams) {
                awakenDreams();
            }
            hideRecentApps(false, SHOW_STARTING_ANIMATIONS);
        } else {
            sendCloseSystemWindows(SYSTEM_DIALOG_REASON_HOME_KEY);
            startDockOrHome(SHOW_STARTING_ANIMATIONS, awakenFromDreams);
        }
    }

    public boolean getInsetHintLw(LayoutParams attrs, Rect taskBounds, int displayRotation, int displayWidth, int displayHeight, Rect outContentInsets, Rect outStableInsets, Rect outOutsets) {
        int fl = PolicyControl.getWindowFlags(null, attrs);
        int systemUiVisibility = PolicyControl.getSystemUiVisibility(null, attrs) | attrs.subtreeSystemUiVisibility;
        if (outOutsets != null ? shouldUseOutsets(attrs, fl) : false) {
            int outset = ScreenShapeHelper.getWindowOutsetBottomPx(this.mContext.getResources());
            if (outset > 0) {
                if (displayRotation == 0) {
                    outOutsets.bottom += outset;
                } else if (displayRotation == 1) {
                    outOutsets.right += outset;
                } else if (displayRotation == 2) {
                    outOutsets.top += outset;
                } else if (displayRotation == 3) {
                    outOutsets.left += outset;
                }
            }
        }
        if ((65792 & fl) == 65792) {
            int availRight;
            int availBottom;
            if (!canHideNavigationBar() || (systemUiVisibility & 512) == 0) {
                availRight = this.mRestrictedScreenLeft + this.mRestrictedScreenWidth;
                availBottom = this.mRestrictedScreenTop + this.mRestrictedScreenHeight;
            } else {
                availRight = this.mUnrestrictedScreenLeft + this.mUnrestrictedScreenWidth;
                availBottom = this.mUnrestrictedScreenTop + this.mUnrestrictedScreenHeight;
            }
            if ((systemUiVisibility & 256) != 0) {
                if ((fl & 1024) != 0) {
                    outContentInsets.set(this.mStableFullscreenLeft, this.mStableFullscreenTop, availRight - this.mStableFullscreenRight, availBottom - this.mStableFullscreenBottom);
                } else {
                    outContentInsets.set(this.mStableLeft, this.mStableTop, availRight - this.mStableRight, availBottom - this.mStableBottom);
                }
            } else if ((fl & 1024) != 0 || (33554432 & fl) != 0) {
                outContentInsets.setEmpty();
            } else if ((systemUiVisibility & 1028) == 0) {
                outContentInsets.set(this.mCurLeft, this.mCurTop, availRight - this.mCurRight, availBottom - this.mCurBottom);
            } else {
                outContentInsets.set(this.mCurLeft, this.mCurTop, availRight - this.mCurRight, availBottom - this.mCurBottom);
            }
            outStableInsets.set(this.mStableLeft, this.mStableTop, availRight - this.mStableRight, availBottom - this.mStableBottom);
            if (taskBounds != null) {
                calculateRelevantTaskInsets(taskBounds, outContentInsets, displayWidth, displayHeight);
                calculateRelevantTaskInsets(taskBounds, outStableInsets, displayWidth, displayHeight);
            }
            return this.mForceShowSystemBars;
        }
        outContentInsets.setEmpty();
        outStableInsets.setEmpty();
        return this.mForceShowSystemBars;
    }

    private void calculateRelevantTaskInsets(Rect taskBounds, Rect inOutInsets, int displayWidth, int displayHeight) {
        mTmpRect.set(0, 0, displayWidth, displayHeight);
        mTmpRect.inset(inOutInsets);
        mTmpRect.intersect(taskBounds);
        inOutInsets.set(mTmpRect.left - taskBounds.left, mTmpRect.top - taskBounds.top, taskBounds.right - mTmpRect.right, taskBounds.bottom - mTmpRect.bottom);
    }

    private boolean shouldUseOutsets(LayoutParams attrs, int fl) {
        return (attrs.type == 2013 || (33555456 & fl) != 0) ? SHOW_STARTING_ANIMATIONS : false;
    }

    public void beginLayoutLw(boolean isDefaultDisplay, int displayWidth, int displayHeight, int displayRotation, int uiMode) {
        int overscanLeft;
        int overscanTop;
        int overscanRight;
        int overscanBottom;
        this.mDisplayRotation = displayRotation;
        if (isDefaultDisplay) {
            switch (displayRotation) {
                case 1:
                    overscanLeft = this.mOverscanTop;
                    overscanTop = this.mOverscanRight;
                    overscanRight = this.mOverscanBottom;
                    overscanBottom = this.mOverscanLeft;
                    break;
                case 2:
                    overscanLeft = this.mOverscanRight;
                    overscanTop = this.mOverscanBottom;
                    overscanRight = this.mOverscanLeft;
                    overscanBottom = this.mOverscanTop;
                    break;
                case 3:
                    overscanLeft = this.mOverscanBottom;
                    overscanTop = this.mOverscanLeft;
                    overscanRight = this.mOverscanTop;
                    overscanBottom = this.mOverscanRight;
                    break;
                default:
                    overscanLeft = this.mOverscanLeft;
                    overscanTop = this.mOverscanTop;
                    overscanRight = this.mOverscanRight;
                    overscanBottom = this.mOverscanBottom;
                    break;
            }
        }
        overscanLeft = 0;
        overscanTop = 0;
        overscanRight = 0;
        overscanBottom = 0;
        this.mRestrictedOverscanScreenLeft = 0;
        this.mOverscanScreenLeft = 0;
        this.mRestrictedOverscanScreenTop = 0;
        this.mOverscanScreenTop = 0;
        this.mRestrictedOverscanScreenWidth = displayWidth;
        this.mOverscanScreenWidth = displayWidth;
        this.mRestrictedOverscanScreenHeight = displayHeight;
        this.mOverscanScreenHeight = displayHeight;
        this.mSystemLeft = 0;
        this.mSystemTop = 0;
        this.mSystemRight = displayWidth;
        this.mSystemBottom = displayHeight;
        this.mUnrestrictedScreenLeft = overscanLeft;
        this.mUnrestrictedScreenTop = overscanTop;
        this.mUnrestrictedScreenWidth = (displayWidth - overscanLeft) - overscanRight;
        this.mUnrestrictedScreenHeight = (displayHeight - overscanTop) - overscanBottom;
        this.mRestrictedScreenLeft = this.mUnrestrictedScreenLeft;
        this.mRestrictedScreenTop = this.mUnrestrictedScreenTop;
        int i = this.mUnrestrictedScreenWidth;
        this.mSystemGestures.screenWidth = i;
        this.mRestrictedScreenWidth = i;
        i = this.mUnrestrictedScreenHeight;
        this.mSystemGestures.screenHeight = i;
        this.mRestrictedScreenHeight = i;
        i = this.mUnrestrictedScreenLeft;
        this.mCurLeft = i;
        this.mStableFullscreenLeft = i;
        this.mStableLeft = i;
        this.mVoiceContentLeft = i;
        this.mContentLeft = i;
        this.mDockLeft = i;
        i = this.mUnrestrictedScreenTop;
        this.mCurTop = i;
        this.mStableFullscreenTop = i;
        this.mStableTop = i;
        this.mVoiceContentTop = i;
        this.mContentTop = i;
        this.mDockTop = i;
        i = displayWidth - overscanRight;
        this.mCurRight = i;
        this.mStableFullscreenRight = i;
        this.mStableRight = i;
        this.mVoiceContentRight = i;
        this.mContentRight = i;
        this.mDockRight = i;
        i = displayHeight - overscanBottom;
        this.mCurBottom = i;
        this.mStableFullscreenBottom = i;
        this.mStableBottom = i;
        this.mVoiceContentBottom = i;
        this.mContentBottom = i;
        this.mDockBottom = i;
        this.mDockLayer = 268435456;
        this.mStatusBarLayer = -1;
        Rect pf = mTmpParentFrame;
        Rect df = mTmpDisplayFrame;
        Rect of = mTmpOverscanFrame;
        Rect vf = mTmpVisibleFrame;
        Rect dcf = mTmpDecorFrame;
        i = this.mDockLeft;
        vf.left = i;
        of.left = i;
        df.left = i;
        pf.left = i;
        i = this.mDockTop;
        vf.top = i;
        of.top = i;
        df.top = i;
        pf.top = i;
        i = this.mDockRight;
        vf.right = i;
        of.right = i;
        df.right = i;
        pf.right = i;
        i = this.mDockBottom;
        vf.bottom = i;
        of.bottom = i;
        df.bottom = i;
        pf.bottom = i;
        dcf.setEmpty();
        if (isDefaultDisplay) {
            int sysui = this.mLastSystemUiFlags;
            boolean navVisible = (sysui & 2) == 0 ? SHOW_STARTING_ANIMATIONS : false;
            boolean navTranslucent = (-2147450880 & sysui) != 0 ? SHOW_STARTING_ANIMATIONS : false;
            boolean immersive = (sysui & 2048) != 0 ? SHOW_STARTING_ANIMATIONS : false;
            boolean immersiveSticky = (sysui & 4096) != 0 ? SHOW_STARTING_ANIMATIONS : false;
            LayoutParams focusAttrs = this.mFocusedWindow != null ? this.mFocusedWindow.getAttrs() : null;
            boolean isNeedHideNaviBarWin = focusAttrs != null ? (focusAttrs.privateFlags & Integer.MIN_VALUE) != 0 ? SHOW_STARTING_ANIMATIONS : false : false;
            boolean z = (immersive || immersiveSticky) ? SHOW_STARTING_ANIMATIONS : isNeedHideNaviBarWin;
            navTranslucent &= immersiveSticky ? 0 : 1;
            boolean isKeyguardShowing = (!isStatusBarKeyguard() || this.mHideLockScreen) ? false : SHOW_STARTING_ANIMATIONS;
            if (!isKeyguardShowing) {
                navTranslucent &= areTranslucentBarsAllowed();
            }
            boolean statusBarExpandedNotKeyguard = (isKeyguardShowing || this.mStatusBar == null || HwPolicyFactory.isHwGlobalActionsShowing() || this.mStatusBar.getAttrs().height != -1) ? false : this.mStatusBar.getAttrs().width == -1 ? SHOW_STARTING_ANIMATIONS : false;
            if (navVisible || z) {
                if (this.mInputConsumer != null) {
                    this.mHandler.sendMessage(this.mHandler.obtainMessage(19, this.mInputConsumer));
                    this.mInputConsumer = null;
                }
            } else if (this.mInputConsumer == null) {
                this.mInputConsumer = this.mWindowManagerFuncs.addInputConsumer(this.mHandler.getLooper(), this.mHideNavInputEventReceiverFactory);
            }
            navVisible |= canHideNavigationBar() ? 0 : 1;
            if (navVisible && mUsingHwNavibar) {
                navVisible = (!navVisible || computeNaviBarFlag()) ? false : SHOW_STARTING_ANIMATIONS;
            }
            String activityName = "com.google.android.gms/com.google.android.gms.auth.login.ShowErrorActivity";
            String windowName = null;
            if (this.mFocusedWindow != null) {
                windowName = this.mFocusedWindow.toString();
            }
            int type = focusAttrs != null ? focusAttrs.type : 0;
            boolean isKeyguardOn = ((type != 2000 || ((focusAttrs != null ? focusAttrs.privateFlags : 0) & 1024) == 0) && type != 2101) ? type == 2100 ? SHOW_STARTING_ANIMATIONS : false : SHOW_STARTING_ANIMATIONS;
            if (windowName != null && windowName.contains(activityName) && Secure.getInt(this.mContext.getContentResolver(), "device_provisioned", 1) == 0) {
                navVisible = SHOW_STARTING_ANIMATIONS;
            }
            if (layoutNavigationBar(displayWidth, displayHeight, displayRotation, uiMode, overscanRight, overscanBottom, dcf, navVisible, navTranslucent, z, isKeyguardOn, statusBarExpandedNotKeyguard) | layoutStatusBar(pf, df, of, vf, dcf, sysui, isKeyguardShowing)) {
                updateSystemUiVisibilityLw();
            }
        }
    }

    private boolean layoutStatusBar(Rect pf, Rect df, Rect of, Rect vf, Rect dcf, int sysui, boolean isKeyguardShowing) {
        if (this.mStatusBar != null) {
            int i = this.mUnrestrictedScreenLeft;
            of.left = i;
            df.left = i;
            pf.left = i;
            i = this.mUnrestrictedScreenTop;
            of.top = i;
            df.top = i;
            pf.top = i;
            i = this.mUnrestrictedScreenWidth + this.mUnrestrictedScreenLeft;
            of.right = i;
            df.right = i;
            pf.right = i;
            i = this.mUnrestrictedScreenHeight + this.mUnrestrictedScreenTop;
            of.bottom = i;
            df.bottom = i;
            pf.bottom = i;
            vf.left = this.mStableLeft;
            vf.top = this.mStableTop;
            vf.right = this.mStableRight;
            vf.bottom = this.mStableBottom;
            this.mStatusBarLayer = this.mStatusBar.getSurfaceLayer();
            this.mStatusBar.computeFrameLw(pf, df, vf, vf, vf, dcf, vf, vf);
            this.mStableTop = this.mUnrestrictedScreenTop + this.mStatusBarHeight;
            boolean statusBarTransient = (67108864 & sysui) != 0 ? SHOW_STARTING_ANIMATIONS : false;
            boolean statusBarTranslucent = (1073741832 & sysui) != 0 ? SHOW_STARTING_ANIMATIONS : false;
            if (!isKeyguardShowing) {
                statusBarTranslucent &= areTranslucentBarsAllowed();
            }
            if (this.mStatusBar.isVisibleLw() && !statusBarTransient) {
                this.mDockTop = this.mUnrestrictedScreenTop + this.mStatusBarHeight;
                i = this.mDockTop;
                this.mCurTop = i;
                this.mVoiceContentTop = i;
                this.mContentTop = i;
                i = this.mDockBottom;
                this.mCurBottom = i;
                this.mVoiceContentBottom = i;
                this.mContentBottom = i;
                i = this.mDockLeft;
                this.mCurLeft = i;
                this.mVoiceContentLeft = i;
                this.mContentLeft = i;
                i = this.mDockRight;
                this.mCurRight = i;
                this.mVoiceContentRight = i;
                this.mContentRight = i;
            }
            if (!(!this.mStatusBar.isVisibleLw() || this.mStatusBar.isAnimatingLw() || statusBarTransient || r10 || this.mStatusBarController.wasRecentlyTranslucent())) {
                this.mSystemTop = this.mUnrestrictedScreenTop + this.mStatusBarHeight;
            }
            if (this.mStatusBarController.checkHiddenLw()) {
                return SHOW_STARTING_ANIMATIONS;
            }
        }
        return false;
    }

    private boolean layoutNavigationBar(int displayWidth, int displayHeight, int displayRotation, int uiMode, int overscanRight, int overscanBottom, Rect dcf, boolean navVisible, boolean navTranslucent, boolean navAllowedHidden, boolean isKeyguardOn, boolean statusBarExpandedNotKeyguard) {
        if (this.mNavigationBar != null) {
            int naviBarHeightForRotationMin;
            boolean transientNavBarShowing = this.mNavigationBarController.isTransientShowing();
            this.mNavigationBarOnBottom = isNavigationBarOnBottom(displayWidth, displayHeight);
            if (this.mNavigationBarOnBottom) {
                mTmpNavigationFrame.set(0, (displayHeight - overscanBottom) - getNavigationBarHeight(displayRotation, uiMode), displayWidth, ((displayHeight - overscanBottom) + getNaviBarHeightForRotationMax(displayRotation)) - this.mNavigationBarHeightForRotationDefault[displayRotation]);
                if (isNaviBarMini()) {
                    naviBarHeightForRotationMin = displayHeight - getNaviBarHeightForRotationMin(displayRotation);
                    this.mStableFullscreenBottom = naviBarHeightForRotationMin;
                    this.mStableBottom = naviBarHeightForRotationMin;
                } else {
                    naviBarHeightForRotationMin = mTmpNavigationFrame.top;
                    this.mStableFullscreenBottom = naviBarHeightForRotationMin;
                    this.mStableBottom = naviBarHeightForRotationMin;
                }
                if (transientNavBarShowing) {
                    this.mNavigationBarController.setBarShowingLw(SHOW_STARTING_ANIMATIONS);
                    this.mLastNaviStatus = 2;
                    this.mLastTransientNaviDockBottom = this.mDockBottom;
                } else if (navVisible) {
                    if (this.mNavigationBarController.isTransientHiding()) {
                        Slog.v(TAG, "navigationbar is visible, but transientBarState is hiding, so reset a portrait screen");
                        this.mNavigationBarController.sethwTransientBarState(0);
                    }
                    this.mNavigationBarController.setBarShowingLw(SHOW_STARTING_ANIMATIONS);
                    this.mDockBottom = this.mStableBottom;
                    this.mRestrictedScreenHeight = this.mDockBottom - this.mRestrictedScreenTop;
                    this.mRestrictedOverscanScreenHeight = this.mDockBottom - this.mRestrictedOverscanScreenTop;
                    this.mLastNaviStatus = 0;
                    this.mLastShowNaviDockBottom = this.mDockBottom;
                } else {
                    this.mNavigationBarController.setBarShowingLw(statusBarExpandedNotKeyguard);
                    if (isKeyguardOn) {
                        switch (this.mLastNaviStatus) {
                            case 0:
                                if (this.mLastShowNaviDockBottom != 0) {
                                    this.mDockBottom = this.mLastShowNaviDockBottom;
                                    this.mRestrictedScreenHeight = this.mDockBottom - this.mRestrictedOverscanScreenTop;
                                    this.mRestrictedOverscanScreenHeight = this.mDockBottom - this.mRestrictedOverscanScreenTop;
                                    break;
                                }
                                break;
                            case 1:
                                if (this.mLastHideNaviDockBottom != 0) {
                                    this.mDockBottom = this.mLastHideNaviDockBottom;
                                    this.mRestrictedScreenHeight = this.mDockBottom - this.mRestrictedOverscanScreenTop;
                                    this.mRestrictedOverscanScreenHeight = this.mDockBottom - this.mRestrictedOverscanScreenTop;
                                    break;
                                }
                                break;
                            case 2:
                                if (this.mLastTransientNaviDockBottom != 0) {
                                    this.mDockBottom = this.mLastTransientNaviDockBottom;
                                    this.mRestrictedScreenHeight = this.mDockBottom - this.mRestrictedOverscanScreenTop;
                                    this.mRestrictedOverscanScreenHeight = this.mDockBottom - this.mRestrictedOverscanScreenTop;
                                    break;
                                }
                                break;
                            default:
                                Slog.v(TAG, "keyguard mLastNaviStatus is init");
                                break;
                        }
                    }
                    this.mLastNaviStatus = 1;
                    this.mLastHideNaviDockBottom = this.mDockBottom;
                }
                if (!(!navVisible || navTranslucent || navAllowedHidden || this.mNavigationBar.isAnimatingLw() || this.mNavigationBarController.wasRecentlyTranslucent() || mUsingHwNavibar)) {
                    this.mSystemBottom = this.mStableBottom;
                }
            } else {
                boolean isShowLeftNavBar = getNavibarAlignLeftWhenLand();
                if (isShowLeftNavBar) {
                    mTmpNavigationFrame.set(0, 0, this.mContext.getResources().getDimensionPixelSize(34472125), displayHeight);
                } else {
                    mTmpNavigationFrame.set((displayWidth - overscanRight) - getNavigationBarWidth(displayRotation, uiMode), 0, ((displayWidth - overscanRight) + getNaviBarWidthForRotationMax(displayRotation)) - this.mNavigationBarWidthForRotationDefault[displayRotation], displayHeight);
                }
                if (isNaviBarMini()) {
                    naviBarHeightForRotationMin = displayWidth - getNaviBarWidthForRotationMin(displayRotation);
                    this.mStableFullscreenRight = naviBarHeightForRotationMin;
                    this.mStableRight = naviBarHeightForRotationMin;
                } else if (isShowLeftNavBar) {
                    naviBarHeightForRotationMin = mTmpNavigationFrame.right;
                    this.mStableFullscreenLeft = naviBarHeightForRotationMin;
                    this.mStableLeft = naviBarHeightForRotationMin;
                    this.mStableRight = displayWidth;
                } else {
                    naviBarHeightForRotationMin = mTmpNavigationFrame.left;
                    this.mStableFullscreenRight = naviBarHeightForRotationMin;
                    this.mStableRight = naviBarHeightForRotationMin;
                }
                if (transientNavBarShowing) {
                    this.mNavigationBarController.setBarShowingLw(SHOW_STARTING_ANIMATIONS);
                    this.mLastNaviStatus = 2;
                } else if (navVisible) {
                    if (this.mNavigationBarController.isTransientHiding()) {
                        Slog.v(TAG, "navigationbar is visible, but transientBarState is hiding, so reset a landscape screen");
                        this.mNavigationBarController.sethwTransientBarState(0);
                    }
                    this.mNavigationBarController.setBarShowingLw(SHOW_STARTING_ANIMATIONS);
                    this.mDockRight = this.mStableRight;
                    if (isShowLeftNavBar) {
                        naviBarHeightForRotationMin = this.mStableLeft;
                        this.mDockLeft = naviBarHeightForRotationMin;
                        this.mRestrictedScreenLeft = naviBarHeightForRotationMin;
                        this.mRestrictedScreenWidth = this.mStableRight;
                        this.mRestrictedOverscanScreenWidth = this.mStableRight;
                    } else {
                        this.mRestrictedScreenWidth = this.mDockRight - this.mRestrictedScreenLeft;
                        this.mRestrictedOverscanScreenWidth = this.mDockRight - this.mRestrictedOverscanScreenLeft;
                    }
                    this.mLastNaviStatus = 0;
                } else {
                    this.mNavigationBarController.setBarShowingLw(statusBarExpandedNotKeyguard);
                    this.mLastNaviStatus = 1;
                }
                if (!(!navVisible || navTranslucent || navAllowedHidden || this.mNavigationBar.isAnimatingLw() || this.mNavigationBarController.wasRecentlyTranslucent() || mUsingHwNavibar)) {
                    this.mSystemRight = this.mStableRight;
                }
            }
            naviBarHeightForRotationMin = this.mDockTop;
            this.mCurTop = naviBarHeightForRotationMin;
            this.mVoiceContentTop = naviBarHeightForRotationMin;
            this.mContentTop = naviBarHeightForRotationMin;
            naviBarHeightForRotationMin = this.mDockBottom;
            this.mCurBottom = naviBarHeightForRotationMin;
            this.mVoiceContentBottom = naviBarHeightForRotationMin;
            this.mContentBottom = naviBarHeightForRotationMin;
            naviBarHeightForRotationMin = this.mDockLeft;
            this.mCurLeft = naviBarHeightForRotationMin;
            this.mVoiceContentLeft = naviBarHeightForRotationMin;
            this.mContentLeft = naviBarHeightForRotationMin;
            naviBarHeightForRotationMin = this.mDockRight;
            this.mCurRight = naviBarHeightForRotationMin;
            this.mVoiceContentRight = naviBarHeightForRotationMin;
            this.mContentRight = naviBarHeightForRotationMin;
            this.mStatusBarLayer = this.mNavigationBar.getSurfaceLayer();
            this.mNavigationBar.computeFrameLw(mTmpNavigationFrame, mTmpNavigationFrame, mTmpNavigationFrame, mTmpNavigationFrame, mTmpNavigationFrame, dcf, mTmpNavigationFrame, mTmpNavigationFrame);
            if (this.mNavigationBarController.checkHiddenLw()) {
                return SHOW_STARTING_ANIMATIONS;
            }
        }
        return false;
    }

    private boolean isNavigationBarOnBottom(int displayWidth, int displayHeight) {
        return (!this.mNavigationBarCanMove || displayWidth < displayHeight) ? SHOW_STARTING_ANIMATIONS : false;
    }

    public int getSystemDecorLayerLw() {
        if (this.mStatusBar != null && this.mStatusBar.isVisibleLw()) {
            return this.mStatusBar.getSurfaceLayer();
        }
        if (this.mNavigationBar == null || !this.mNavigationBar.isVisibleLw()) {
            return 0;
        }
        return this.mNavigationBar.getSurfaceLayer();
    }

    public void getContentRectLw(Rect r) {
        r.set(this.mContentLeft, this.mContentTop, this.mContentRight, this.mContentBottom);
    }

    void setAttachedWindowFrames(WindowState win, int fl, int adjust, WindowState attached, boolean insetDecors, Rect pf, Rect df, Rect of, Rect cf, Rect vf) {
        if (win.getSurfaceLayer() <= this.mDockLayer || attached.getSurfaceLayer() >= this.mDockLayer) {
            Rect displayFrameLw;
            if (adjust != 16) {
                cf.set((1073741824 & fl) != 0 ? attached.getContentFrameLw() : attached.getOverscanFrameLw());
                if (attached.getAttrs().type == 2011 && cf.bottom > attached.getContentFrameLw().bottom) {
                    cf.bottom = attached.getContentFrameLw().bottom;
                }
            } else {
                cf.set(attached.getContentFrameLw());
                if (attached.isVoiceInteraction()) {
                    if (cf.left < this.mVoiceContentLeft) {
                        cf.left = this.mVoiceContentLeft;
                    }
                    if (cf.top < this.mVoiceContentTop) {
                        cf.top = this.mVoiceContentTop;
                    }
                    if (cf.right > this.mVoiceContentRight) {
                        cf.right = this.mVoiceContentRight;
                    }
                    if (cf.bottom > this.mVoiceContentBottom) {
                        cf.bottom = this.mVoiceContentBottom;
                    }
                } else if (attached.getSurfaceLayer() < this.mDockLayer) {
                    if (cf.left < this.mContentLeft) {
                        cf.left = this.mContentLeft;
                    }
                    if (cf.top < this.mContentTop) {
                        cf.top = this.mContentTop;
                    }
                    if (cf.right > this.mContentRight) {
                        cf.right = this.mContentRight;
                    }
                    if (cf.bottom > this.mContentBottom) {
                        cf.bottom = this.mContentBottom;
                    }
                }
            }
            if (insetDecors) {
                displayFrameLw = attached.getDisplayFrameLw();
            } else {
                displayFrameLw = cf;
            }
            df.set(displayFrameLw);
            if (insetDecors) {
                cf = attached.getOverscanFrameLw();
            }
            of.set(cf);
            vf.set(attached.getVisibleFrameLw());
        } else {
            int i = this.mDockLeft;
            vf.left = i;
            cf.left = i;
            of.left = i;
            df.left = i;
            i = this.mDockTop;
            vf.top = i;
            cf.top = i;
            of.top = i;
            df.top = i;
            i = this.mDockRight;
            vf.right = i;
            cf.right = i;
            of.right = i;
            df.right = i;
            i = this.mDockBottom;
            vf.bottom = i;
            cf.bottom = i;
            of.bottom = i;
            df.bottom = i;
        }
        if ((fl & 256) == 0) {
            df = attached.getFrameLw();
        }
        pf.set(df);
    }

    private void applyStableConstraints(int sysui, int fl, Rect r) {
        if ((sysui & 256) == 0) {
            return;
        }
        if ((fl & 1024) != 0) {
            if (r.left < this.mStableFullscreenLeft) {
                r.left = this.mStableFullscreenLeft;
            }
            if (r.top < this.mStableFullscreenTop) {
                r.top = this.mStableFullscreenTop;
            }
            if (r.right > this.mStableFullscreenRight) {
                r.right = this.mStableFullscreenRight;
            }
            if (r.bottom > this.mStableFullscreenBottom) {
                r.bottom = this.mStableFullscreenBottom;
                return;
            }
            return;
        }
        if (r.left < this.mStableLeft) {
            r.left = this.mStableLeft;
        }
        if (r.top < this.mStableTop) {
            r.top = this.mStableTop;
        }
        if (r.right > this.mStableRight) {
            r.right = this.mStableRight;
        }
        if (r.bottom > this.mStableBottom) {
            r.bottom = this.mStableBottom;
        }
    }

    private boolean canReceiveInput(WindowState win) {
        return ((win.getAttrs().flags & 8) != 0 ? SHOW_STARTING_ANIMATIONS : false) ^ ((win.getAttrs().flags & DumpState.DUMP_INTENT_FILTER_VERIFIERS) != 0 ? SHOW_STARTING_ANIMATIONS : false) ? false : SHOW_STARTING_ANIMATIONS;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void layoutWindowLw(WindowState win, WindowState attached) {
        if ((win != this.mStatusBar || canReceiveInput(win)) && win != this.mNavigationBar) {
            boolean isVisibleLw;
            int i;
            LayoutParams attrs = win.getAttrs();
            boolean isDefaultDisplay = win.isDefaultDisplay();
            boolean needsToOffsetInputMethodTarget = isDefaultDisplay ? (win != this.mLastInputMethodTargetWindow || this.mLastInputMethodWindow == null) ? false : SHOW_STARTING_ANIMATIONS : false;
            if (needsToOffsetInputMethodTarget) {
                offsetInputMethodWindowLw(this.mLastInputMethodWindow);
            }
            int fl = PolicyControl.getWindowFlags(win, attrs);
            int pfl = attrs.privateFlags;
            int sim = attrs.softInputMode;
            int sysUiFl = PolicyControl.getSystemUiVisibility(win, null);
            Rect pf = mTmpParentFrame;
            Rect df = mTmpDisplayFrame;
            Rect of = mTmpOverscanFrame;
            Rect cf = mTmpContentFrame;
            Rect vf = mTmpVisibleFrame;
            Rect dcf = mTmpDecorFrame;
            Rect sf = mTmpStableFrame;
            Rect osf = null;
            dcf.setEmpty();
            boolean isNeedHideNaviBarWin = (!mUsingHwNavibar || (attrs.privateFlags & Integer.MIN_VALUE) == 0) ? false : SHOW_STARTING_ANIMATIONS;
            if (isDefaultDisplay && this.mHasNavigationBar && this.mNavigationBar != null) {
                isVisibleLw = this.mNavigationBar.isVisibleLw();
            } else {
                isVisibleLw = false;
            }
            int adjust = sim & 240;
            if (isDefaultDisplay) {
                sf.set(this.mStableLeft, this.mStableTop, this.mStableRight, this.mStableBottom);
            } else {
                sf.set(this.mOverscanLeft, this.mOverscanTop, this.mOverscanRight, this.mOverscanBottom);
            }
            if (isDefaultDisplay) {
                if (attrs.type == 2011) {
                    i = this.mDockLeft;
                    vf.left = i;
                    cf.left = i;
                    of.left = i;
                    df.left = i;
                    pf.left = i;
                    i = this.mDockTop;
                    vf.top = i;
                    cf.top = i;
                    of.top = i;
                    df.top = i;
                    pf.top = i;
                    i = this.mDockRight;
                    vf.right = i;
                    cf.right = i;
                    of.right = i;
                    df.right = i;
                    pf.right = i;
                    if (mUsingHwNavibar) {
                        i = this.mDockBottom;
                        vf.bottom = i;
                        cf.bottom = i;
                        df.bottom = i;
                        pf.bottom = i;
                        LayoutParams focusAttrs = this.mFocusedWindow != null ? this.mFocusedWindow.getAttrs() : null;
                        if (!(focusAttrs == null || (focusAttrs.privateFlags & 1024) == 0)) {
                            i = this.mSystemBottom;
                            of.bottom = i;
                            vf.bottom = i;
                            cf.bottom = i;
                            df.bottom = i;
                            pf.bottom = i;
                        }
                    } else {
                        i = this.mUnrestrictedScreenTop + this.mUnrestrictedScreenHeight;
                        of.bottom = i;
                        df.bottom = i;
                        pf.bottom = i;
                        i = this.mStableBottom;
                        vf.bottom = i;
                        cf.bottom = i;
                        if (this.mStatusBar != null && this.mFocusedWindow == this.mStatusBar) {
                            if (canReceiveInput(this.mStatusBar)) {
                                i = this.mStableRight;
                                vf.right = i;
                                cf.right = i;
                                of.right = i;
                                df.right = i;
                                pf.right = i;
                            }
                        }
                    }
                    attrs.gravity = 80;
                    this.mDockLayer = win.getSurfaceLayer();
                } else if (attrs.type == 2031) {
                    i = this.mUnrestrictedScreenLeft;
                    of.left = i;
                    df.left = i;
                    pf.left = i;
                    i = this.mUnrestrictedScreenTop;
                    of.top = i;
                    df.top = i;
                    pf.top = i;
                    i = this.mUnrestrictedScreenLeft + this.mUnrestrictedScreenWidth;
                    of.right = i;
                    df.right = i;
                    pf.right = i;
                    i = this.mUnrestrictedScreenTop + this.mUnrestrictedScreenHeight;
                    of.bottom = i;
                    df.bottom = i;
                    pf.bottom = i;
                    if (adjust != 16) {
                        cf.left = this.mDockLeft;
                        cf.top = this.mDockTop;
                        cf.right = this.mDockRight;
                        cf.bottom = this.mDockBottom;
                    } else {
                        cf.left = this.mContentLeft;
                        cf.top = this.mContentTop;
                        cf.right = this.mContentRight;
                        cf.bottom = this.mContentBottom;
                    }
                    if (adjust != 48) {
                        vf.left = this.mCurLeft;
                        vf.top = this.mCurTop;
                        vf.right = this.mCurRight;
                        vf.bottom = this.mCurBottom;
                    } else {
                        vf.set(cf);
                    }
                } else if (win == this.mStatusBar) {
                    i = this.mUnrestrictedScreenLeft;
                    of.left = i;
                    df.left = i;
                    pf.left = i;
                    i = this.mUnrestrictedScreenTop;
                    of.top = i;
                    df.top = i;
                    pf.top = i;
                    i = this.mUnrestrictedScreenWidth + this.mUnrestrictedScreenLeft;
                    of.right = i;
                    df.right = i;
                    pf.right = i;
                    i = this.mUnrestrictedScreenHeight + this.mUnrestrictedScreenTop;
                    of.bottom = i;
                    df.bottom = i;
                    pf.bottom = i;
                    i = this.mStableLeft;
                    vf.left = i;
                    cf.left = i;
                    i = this.mStableTop;
                    vf.top = i;
                    cf.top = i;
                    i = this.mStableRight;
                    vf.right = i;
                    cf.right = i;
                    vf.bottom = this.mStableBottom;
                    if (adjust == 16) {
                        cf.bottom = this.mContentBottom;
                    } else {
                        cf.bottom = this.mDockBottom;
                        vf.bottom = this.mContentBottom;
                    }
                } else {
                    dcf.left = this.mSystemLeft;
                    dcf.top = this.mSystemTop;
                    dcf.right = this.mSystemRight;
                    dcf.bottom = this.mSystemBottom;
                    boolean inheritTranslucentDecor = (attrs.privateFlags & 512) != 0 ? SHOW_STARTING_ANIMATIONS : false;
                    boolean isAppWindow = attrs.type >= 1 ? attrs.type <= 99 ? SHOW_STARTING_ANIMATIONS : false : false;
                    boolean topAtRest = (win != this.mTopFullscreenOpaqueWindowState || win.isAnimatingLw()) ? false : SHOW_STARTING_ANIMATIONS;
                    if (!(!isAppWindow || inheritTranslucentDecor || topAtRest)) {
                        if ((sysUiFl & 4) == 0 && (fl & 1024) == 0 && (67108864 & fl) == 0 && (Integer.MIN_VALUE & fl) == 0 && (DumpState.DUMP_INTENT_FILTER_VERIFIERS & pfl) == 0 && this.mStatusBar != null && this.mStatusBar.isVisibleLw() && (this.mLastSystemUiFlags & 67108864) == 0) {
                            dcf.top = this.mStableTop;
                        }
                        if ((134217728 & fl) == 0 && (sysUiFl & 2) == 0 && !isNeedHideNaviBarWin && (Integer.MIN_VALUE & fl) == 0) {
                            dcf.bottom = this.mStableBottom;
                            dcf.right = this.mStableRight;
                        }
                    }
                    if ((65792 & fl) != 65792) {
                        if ((fl & 256) == 0) {
                            if ((sysUiFl & 1536) != 0) {
                                if ((attrs.flags & Integer.MIN_VALUE) != 0) {
                                }
                            }
                            if (attached != null) {
                                setAttachedWindowFrames(win, fl, adjust, attached, false, pf, df, of, cf, vf);
                            } else if (attrs.type == 2014 || attrs.type == 2020) {
                                i = this.mRestrictedScreenLeft;
                                cf.left = i;
                                of.left = i;
                                df.left = i;
                                pf.left = i;
                                i = this.mRestrictedScreenTop;
                                cf.top = i;
                                of.top = i;
                                df.top = i;
                                pf.top = i;
                                i = this.mRestrictedScreenLeft + this.mRestrictedScreenWidth;
                                cf.right = i;
                                of.right = i;
                                df.right = i;
                                pf.right = i;
                                i = this.mRestrictedScreenTop + this.mRestrictedScreenHeight;
                                cf.bottom = i;
                                of.bottom = i;
                                df.bottom = i;
                                pf.bottom = i;
                            } else if (attrs.type == 2005 || attrs.type == 2003) {
                                i = this.mStableLeft;
                                cf.left = i;
                                of.left = i;
                                df.left = i;
                                pf.left = i;
                                i = this.mStableTop;
                                cf.top = i;
                                of.top = i;
                                df.top = i;
                                pf.top = i;
                                i = this.mStableRight;
                                cf.right = i;
                                of.right = i;
                                df.right = i;
                                pf.right = i;
                                if (attrs.type == 2003) {
                                    i = this.mCurBottom;
                                    cf.bottom = i;
                                    of.bottom = i;
                                    df.bottom = i;
                                    pf.bottom = i;
                                } else {
                                    i = this.mStableBottom;
                                    cf.bottom = i;
                                    of.bottom = i;
                                    df.bottom = i;
                                    pf.bottom = i;
                                }
                            } else {
                                pf.left = this.mContentLeft;
                                pf.top = this.mContentTop;
                                pf.right = this.mContentRight;
                                pf.bottom = this.mContentBottom;
                                if (win.isVoiceInteraction()) {
                                    i = this.mVoiceContentLeft;
                                    cf.left = i;
                                    of.left = i;
                                    df.left = i;
                                    i = this.mVoiceContentTop;
                                    cf.top = i;
                                    of.top = i;
                                    df.top = i;
                                    i = this.mVoiceContentRight;
                                    cf.right = i;
                                    of.right = i;
                                    df.right = i;
                                    i = this.mVoiceContentBottom;
                                    cf.bottom = i;
                                    of.bottom = i;
                                    df.bottom = i;
                                } else if (adjust != 16) {
                                    i = this.mDockLeft;
                                    cf.left = i;
                                    of.left = i;
                                    df.left = i;
                                    i = this.mDockTop;
                                    cf.top = i;
                                    of.top = i;
                                    df.top = i;
                                    i = this.mDockRight;
                                    cf.right = i;
                                    of.right = i;
                                    df.right = i;
                                    i = this.mDockBottom;
                                    cf.bottom = i;
                                    of.bottom = i;
                                    df.bottom = i;
                                } else {
                                    i = this.mContentLeft;
                                    cf.left = i;
                                    of.left = i;
                                    df.left = i;
                                    i = this.mContentTop;
                                    cf.top = i;
                                    of.top = i;
                                    df.top = i;
                                    i = this.mContentRight;
                                    cf.right = i;
                                    of.right = i;
                                    df.right = i;
                                    i = this.mContentBottom;
                                    cf.bottom = i;
                                    of.bottom = i;
                                    df.bottom = i;
                                }
                                if (adjust != 48) {
                                    vf.left = this.mCurLeft;
                                    vf.top = this.mCurTop;
                                    vf.right = this.mCurRight;
                                    vf.bottom = this.mCurBottom;
                                } else {
                                    vf.set(cf);
                                }
                            }
                        }
                        if (attrs.type == 2014 || attrs.type == 2017 || attrs.type == 2020) {
                            i = isVisibleLw ? this.mDockLeft : this.mUnrestrictedScreenLeft;
                            cf.left = i;
                            of.left = i;
                            df.left = i;
                            pf.left = i;
                            i = this.mUnrestrictedScreenTop;
                            cf.top = i;
                            of.top = i;
                            df.top = i;
                            pf.top = i;
                            if (isVisibleLw) {
                                i = this.mRestrictedScreenLeft + this.mRestrictedScreenWidth;
                            } else {
                                i = this.mUnrestrictedScreenLeft + this.mUnrestrictedScreenWidth;
                            }
                            cf.right = i;
                            of.right = i;
                            df.right = i;
                            pf.right = i;
                            if (isVisibleLw) {
                                i = this.mRestrictedScreenTop + this.mRestrictedScreenHeight;
                            } else {
                                i = this.mUnrestrictedScreenTop + this.mUnrestrictedScreenHeight;
                            }
                            cf.bottom = i;
                            of.bottom = i;
                            df.bottom = i;
                            pf.bottom = i;
                        } else if (attrs.type == 2019 || attrs.type == 2024) {
                            i = this.mUnrestrictedScreenLeft;
                            of.left = i;
                            df.left = i;
                            pf.left = i;
                            i = this.mUnrestrictedScreenTop;
                            of.top = i;
                            df.top = i;
                            pf.top = i;
                            i = this.mUnrestrictedScreenLeft + this.mUnrestrictedScreenWidth;
                            of.right = i;
                            df.right = i;
                            pf.right = i;
                            i = this.mUnrestrictedScreenTop + this.mUnrestrictedScreenHeight;
                            of.bottom = i;
                            df.bottom = i;
                            pf.bottom = i;
                        } else if ((attrs.type == 2015 || attrs.type == 2021 || attrs.type == 2036) && (fl & 1024) != 0) {
                            i = this.mOverscanScreenLeft;
                            cf.left = i;
                            of.left = i;
                            df.left = i;
                            pf.left = i;
                            i = this.mOverscanScreenTop;
                            cf.top = i;
                            of.top = i;
                            df.top = i;
                            pf.top = i;
                            i = this.mOverscanScreenLeft + this.mOverscanScreenWidth;
                            cf.right = i;
                            of.right = i;
                            df.right = i;
                            pf.right = i;
                            i = this.mOverscanScreenTop + this.mOverscanScreenHeight;
                            cf.bottom = i;
                            of.bottom = i;
                            df.bottom = i;
                            pf.bottom = i;
                        } else if (attrs.type == 2021) {
                            i = this.mOverscanScreenLeft;
                            cf.left = i;
                            of.left = i;
                            df.left = i;
                            pf.left = i;
                            i = this.mOverscanScreenTop;
                            cf.top = i;
                            of.top = i;
                            df.top = i;
                            pf.top = i;
                            i = this.mOverscanScreenLeft + this.mOverscanScreenWidth;
                            cf.right = i;
                            of.right = i;
                            df.right = i;
                            pf.right = i;
                            i = this.mOverscanScreenTop + this.mOverscanScreenHeight;
                            cf.bottom = i;
                            of.bottom = i;
                            df.bottom = i;
                            pf.bottom = i;
                        } else if (attrs.type == 2013) {
                            i = this.mOverscanScreenLeft;
                            df.left = i;
                            pf.left = i;
                            i = this.mOverscanScreenTop;
                            df.top = i;
                            pf.top = i;
                            i = this.mOverscanScreenLeft + this.mOverscanScreenWidth;
                            df.right = i;
                            pf.right = i;
                            i = this.mOverscanScreenTop + this.mOverscanScreenHeight;
                            df.bottom = i;
                            pf.bottom = i;
                            i = this.mUnrestrictedScreenLeft;
                            cf.left = i;
                            of.left = i;
                            i = this.mUnrestrictedScreenTop;
                            cf.top = i;
                            of.top = i;
                            i = this.mUnrestrictedScreenLeft + this.mUnrestrictedScreenWidth;
                            cf.right = i;
                            of.right = i;
                            i = this.mUnrestrictedScreenTop + this.mUnrestrictedScreenHeight;
                            cf.bottom = i;
                            of.bottom = i;
                        } else if ((33554432 & fl) != 0 && attrs.type >= 1 && attrs.type <= 1999) {
                            i = this.mOverscanScreenLeft;
                            cf.left = i;
                            of.left = i;
                            df.left = i;
                            pf.left = i;
                            i = this.mOverscanScreenTop;
                            cf.top = i;
                            of.top = i;
                            df.top = i;
                            pf.top = i;
                            i = this.mOverscanScreenLeft + this.mOverscanScreenWidth;
                            cf.right = i;
                            of.right = i;
                            df.right = i;
                            pf.right = i;
                            i = this.mOverscanScreenTop + this.mOverscanScreenHeight;
                            cf.bottom = i;
                            of.bottom = i;
                            df.bottom = i;
                            pf.bottom = i;
                        } else if ((canHideNavigationBar() && (sysUiFl & 512) != 0 && (attrs.type == IHwShutdownThread.SHUTDOWN_ANIMATION_WAIT_TIME || attrs.type == 2005 || attrs.type == 2034 || attrs.type == 2033 || (attrs.type >= 1 && attrs.type <= 1999))) || ((mUsingHwNavibar && attrs.type == 2013) || isNeedHideNaviBarWin)) {
                            i = this.mUnrestrictedScreenLeft;
                            cf.left = i;
                            of.left = i;
                            df.left = i;
                            pf.left = i;
                            i = this.mUnrestrictedScreenTop;
                            cf.top = i;
                            of.top = i;
                            df.top = i;
                            pf.top = i;
                            i = this.mUnrestrictedScreenLeft + this.mUnrestrictedScreenWidth;
                            cf.right = i;
                            of.right = i;
                            df.right = i;
                            pf.right = i;
                            i = this.mUnrestrictedScreenTop + this.mUnrestrictedScreenHeight;
                            cf.bottom = i;
                            of.bottom = i;
                            df.bottom = i;
                            pf.bottom = i;
                        } else if ((sysUiFl & 1024) != 0) {
                            i = this.mRestrictedScreenLeft;
                            of.left = i;
                            df.left = i;
                            pf.left = i;
                            i = this.mRestrictedScreenTop;
                            of.top = i;
                            df.top = i;
                            pf.top = i;
                            i = this.mRestrictedScreenLeft + this.mRestrictedScreenWidth;
                            of.right = i;
                            df.right = i;
                            pf.right = i;
                            i = this.mRestrictedScreenTop + this.mRestrictedScreenHeight;
                            of.bottom = i;
                            df.bottom = i;
                            pf.bottom = i;
                            if (adjust != 16) {
                                cf.left = this.mDockLeft;
                                cf.top = this.mDockTop;
                                cf.right = this.mDockRight;
                                cf.bottom = this.mDockBottom;
                            } else {
                                cf.left = this.mContentLeft;
                                cf.top = this.mContentTop;
                                cf.right = this.mContentRight;
                                cf.bottom = this.mContentBottom;
                            }
                        } else {
                            i = this.mRestrictedScreenLeft;
                            cf.left = i;
                            of.left = i;
                            df.left = i;
                            pf.left = i;
                            i = this.mRestrictedScreenTop;
                            cf.top = i;
                            of.top = i;
                            df.top = i;
                            pf.top = i;
                            i = this.mRestrictedScreenLeft + this.mRestrictedScreenWidth;
                            cf.right = i;
                            of.right = i;
                            df.right = i;
                            pf.right = i;
                            i = this.mRestrictedScreenTop + this.mRestrictedScreenHeight;
                            cf.bottom = i;
                            of.bottom = i;
                            df.bottom = i;
                            pf.bottom = i;
                        }
                        applyStableConstraints(sysUiFl, fl, cf);
                        if (adjust != 48) {
                            vf.left = this.mCurLeft;
                            vf.top = this.mCurTop;
                            vf.right = this.mCurRight;
                            vf.bottom = this.mCurBottom;
                        } else {
                            vf.set(cf);
                        }
                    } else if (attached != null) {
                        setAttachedWindowFrames(win, fl, adjust, attached, SHOW_STARTING_ANIMATIONS, pf, df, of, cf, vf);
                    } else {
                        if (attrs.type == 2014 || attrs.type == 2017) {
                            i = isVisibleLw ? this.mDockLeft : this.mUnrestrictedScreenLeft;
                            of.left = i;
                            df.left = i;
                            pf.left = i;
                            i = this.mUnrestrictedScreenTop;
                            of.top = i;
                            df.top = i;
                            pf.top = i;
                            if (isVisibleLw) {
                                i = this.mRestrictedScreenLeft + this.mRestrictedScreenWidth;
                            } else {
                                i = this.mUnrestrictedScreenLeft + this.mUnrestrictedScreenWidth;
                            }
                            of.right = i;
                            df.right = i;
                            pf.right = i;
                            if (isVisibleLw) {
                                i = this.mRestrictedScreenTop + this.mRestrictedScreenHeight;
                            } else {
                                i = this.mUnrestrictedScreenTop + this.mUnrestrictedScreenHeight;
                            }
                            of.bottom = i;
                            df.bottom = i;
                            pf.bottom = i;
                        } else if ((33554432 & fl) != 0 && attrs.type >= 1 && attrs.type <= 1999) {
                            i = this.mOverscanScreenLeft;
                            of.left = i;
                            df.left = i;
                            pf.left = i;
                            i = this.mOverscanScreenTop;
                            of.top = i;
                            df.top = i;
                            pf.top = i;
                            i = this.mOverscanScreenLeft + this.mOverscanScreenWidth;
                            of.right = i;
                            df.right = i;
                            pf.right = i;
                            i = this.mOverscanScreenTop + this.mOverscanScreenHeight;
                            of.bottom = i;
                            df.bottom = i;
                            pf.bottom = i;
                        } else if ((!canHideNavigationBar() || (sysUiFl & 512) == 0 || attrs.type < 1 || attrs.type > 1999) && !isNeedHideNaviBarWin) {
                            i = this.mRestrictedOverscanScreenLeft;
                            df.left = i;
                            pf.left = i;
                            i = this.mRestrictedOverscanScreenTop;
                            df.top = i;
                            pf.top = i;
                            i = this.mRestrictedOverscanScreenLeft + this.mRestrictedOverscanScreenWidth;
                            df.right = i;
                            pf.right = i;
                            i = this.mRestrictedOverscanScreenTop + this.mRestrictedOverscanScreenHeight;
                            df.bottom = i;
                            pf.bottom = i;
                            of.left = this.mUnrestrictedScreenLeft;
                            of.top = this.mUnrestrictedScreenTop;
                            of.right = this.mUnrestrictedScreenLeft + this.mUnrestrictedScreenWidth;
                            of.bottom = this.mUnrestrictedScreenTop + this.mUnrestrictedScreenHeight;
                        } else {
                            i = this.mOverscanScreenLeft;
                            df.left = i;
                            pf.left = i;
                            i = this.mOverscanScreenTop;
                            df.top = i;
                            pf.top = i;
                            i = this.mOverscanScreenLeft + this.mOverscanScreenWidth;
                            df.right = i;
                            pf.right = i;
                            i = this.mOverscanScreenTop + this.mOverscanScreenHeight;
                            df.bottom = i;
                            pf.bottom = i;
                            of.left = this.mUnrestrictedScreenLeft;
                            of.top = this.mUnrestrictedScreenTop;
                            of.right = this.mUnrestrictedScreenLeft + this.mUnrestrictedScreenWidth;
                            of.bottom = this.mUnrestrictedScreenTop + this.mUnrestrictedScreenHeight;
                        }
                        if ((fl & 1024) == 0) {
                            if (win.isVoiceInteraction()) {
                                cf.left = this.mVoiceContentLeft;
                                cf.top = this.mVoiceContentTop;
                                cf.right = this.mVoiceContentRight;
                                cf.bottom = this.mVoiceContentBottom;
                            } else if (adjust != 16) {
                                cf.left = this.mDockLeft;
                                cf.top = this.mDockTop;
                                cf.right = this.mDockRight;
                                cf.bottom = this.mDockBottom;
                            } else {
                                cf.left = this.mContentLeft;
                                cf.top = this.mContentTop;
                                cf.right = this.mContentRight;
                                cf.bottom = this.mContentBottom;
                            }
                            synchronized (this.mWindowManagerFuncs.getWindowManagerLock()) {
                                if (!win.hasDrawnLw()) {
                                    cf.top = this.mUnrestrictedScreenTop + this.mStatusBarHeight;
                                }
                            }
                            if ((attrs.flags & Integer.MIN_VALUE) == 0 && (attrs.flags & 67108864) == 0 && (sysUiFl & 3076) == 0 && attrs.type != 3) {
                                i = this.mUnrestrictedScreenTop + this.mStatusBarHeight;
                                vf.top = i;
                                dcf.top = i;
                            }
                        } else {
                            cf.left = this.mRestrictedScreenLeft;
                            cf.top = this.mRestrictedScreenTop;
                            cf.right = this.mRestrictedScreenLeft + this.mRestrictedScreenWidth;
                            cf.bottom = this.mRestrictedScreenTop + this.mRestrictedScreenHeight;
                        }
                        if (isNeedHideNaviBarWin) {
                            cf.bottom = this.mUnrestrictedScreenTop + this.mUnrestrictedScreenHeight;
                        }
                        applyStableConstraints(sysUiFl, fl, cf);
                        if (adjust != 48) {
                            vf.left = this.mCurLeft;
                            vf.top = this.mCurTop;
                            vf.right = this.mCurRight;
                            vf.bottom = this.mCurBottom;
                        } else {
                            vf.set(cf);
                        }
                    }
                }
            } else if (attached != null) {
                setAttachedWindowFrames(win, fl, adjust, attached, SHOW_STARTING_ANIMATIONS, pf, df, of, cf, vf);
            } else {
                i = this.mOverscanScreenLeft;
                cf.left = i;
                of.left = i;
                df.left = i;
                pf.left = i;
                i = this.mOverscanScreenTop;
                cf.top = i;
                of.top = i;
                df.top = i;
                pf.top = i;
                i = this.mOverscanScreenLeft + this.mOverscanScreenWidth;
                cf.right = i;
                of.right = i;
                df.right = i;
                pf.right = i;
                i = this.mOverscanScreenTop + this.mOverscanScreenHeight;
                cf.bottom = i;
                of.bottom = i;
                df.bottom = i;
                pf.bottom = i;
            }
            overrideRectForForceRotation(win, pf, df, of, cf, vf, dcf);
            if ((isNeedHideNaviBarWin || !mUsingHwNavibar) && attrs.type == 2004) {
                vf.top = 0;
                cf.top = 0;
                i = pf.bottom;
                vf.bottom = i;
                cf.bottom = i;
            }
            if (mUsingHwNavibar && (isNeedHideNaviBarWin || this.isNavibarHide)) {
                i = pf.bottom;
                vf.bottom = i;
                cf.bottom = i;
                dcf.bottom = i;
            }
            if (!((fl & 512) == 0 || attrs.type == 2010 || win.isInMultiWindowMode())) {
                df.top = -10000;
                df.left = -10000;
                df.bottom = 10000;
                df.right = 10000;
                if (attrs.type != 2013) {
                    vf.top = -10000;
                    vf.left = -10000;
                    cf.top = -10000;
                    cf.left = -10000;
                    of.top = -10000;
                    of.left = -10000;
                    vf.bottom = 10000;
                    vf.right = 10000;
                    cf.bottom = 10000;
                    cf.right = 10000;
                    of.bottom = 10000;
                    of.right = 10000;
                }
            }
            boolean useOutsets = shouldUseOutsets(attrs, fl);
            if (isDefaultDisplay && useOutsets) {
                osf = mTmpOutsetFrame;
                osf.set(cf.left, cf.top, cf.right, cf.bottom);
                int outset = ScreenShapeHelper.getWindowOutsetBottomPx(this.mContext.getResources());
                if (outset > 0) {
                    int rotation = this.mDisplayRotation;
                    if (rotation == 0) {
                        osf.bottom += outset;
                    } else if (rotation == 1) {
                        osf.right += outset;
                    } else if (rotation == 2) {
                        osf.top -= outset;
                    } else if (rotation == 3) {
                        osf.left -= outset;
                    }
                }
            }
            if (win.toString().contains("com.android.packageinstaller.permission.ui.GrantPermissionsActivity")) {
                i = this.mUnrestrictedScreenTop + this.mStatusBarHeight;
                vf.top = i;
                cf.top = i;
                of.top = i;
                df.top = i;
                pf.top = i;
            }
            win.computeFrameLw(pf, df, of, cf, vf, dcf, sf, osf);
            if (attrs.type == 2011 && win.isVisibleOrBehindKeyguardLw() && win.isDisplayedLw() && !win.getGivenInsetsPendingLw()) {
                setLastInputMethodWindowLw(null, null);
                offsetInputMethodWindowLw(win);
            }
            if (attrs.type == 2031 && win.isVisibleOrBehindKeyguardLw() && !win.getGivenInsetsPendingLw()) {
                offsetVoiceInputWindowLw(win);
            }
        }
    }

    private void offsetInputMethodWindowLw(WindowState win) {
        int top = Math.max(win.getDisplayFrameLw().top, win.getContentFrameLw().top) + win.getGivenContentInsetsLw().top;
        if (this.mContentBottom > top) {
            this.mContentBottom = top;
        }
        if (this.mVoiceContentBottom > top) {
            this.mVoiceContentBottom = top;
        }
        top = win.getVisibleFrameLw().top + win.getGivenVisibleInsetsLw().top;
        if (this.mCurBottom > top) {
            this.mCurBottom = top;
        }
    }

    private void offsetVoiceInputWindowLw(WindowState win) {
        int top = Math.max(win.getDisplayFrameLw().top, win.getContentFrameLw().top) + win.getGivenContentInsetsLw().top;
        if (this.mVoiceContentBottom > top) {
            this.mVoiceContentBottom = top;
        }
    }

    public void finishLayoutLw() {
    }

    public void beginPostLayoutPolicyLw(int displayWidth, int displayHeight) {
        boolean z = false;
        this.mTopFullscreenOpaqueWindowState = null;
        this.mTopFullscreenOpaqueOrDimmingWindowState = null;
        this.mTopDockedOpaqueWindowState = null;
        this.mTopDockedOpaqueOrDimmingWindowState = null;
        this.mForceStatusBarTransparentWin = null;
        this.mAppsToBeHidden.clear();
        this.mAppsThatDismissKeyguard.clear();
        this.mForceStatusBar = false;
        this.mForceStatusBarFromKeyguard = false;
        this.mForceStatusBarTransparent = false;
        this.mForcingShowNavBar = false;
        this.mForcingShowNavBarLayer = -1;
        this.mHideLockScreen = false;
        this.mAllowLockscreenWhenOn = false;
        this.mDismissKeyguard = 0;
        this.mShowingLockscreen = false;
        this.mShowingDream = false;
        this.mWinShowWhenLocked = null;
        this.mHasCoverView = false;
        this.mKeyguardSecure = isKeyguardSecure(this.mCurrentUserId);
        if (this.mKeyguardSecure && this.mKeyguardDelegate != null) {
            z = this.mKeyguardDelegate.isShowing();
        }
        this.mKeyguardSecureIncludingHidden = z;
    }

    public void applyPostLayoutPolicyLw(WindowState win, LayoutParams attrs, WindowState attached) {
        int fl = PolicyControl.getWindowFlags(win, attrs);
        if (this.mTopFullscreenOpaqueWindowState == null && win.isVisibleLw() && attrs.type == 2011) {
            this.mForcingShowNavBar = SHOW_STARTING_ANIMATIONS;
            this.mForcingShowNavBarLayer = win.getSurfaceLayer();
        }
        if (attrs.type == IHwShutdownThread.SHUTDOWN_ANIMATION_WAIT_TIME && (attrs.privateFlags & 1024) != 0) {
            this.mForceStatusBarFromKeyguard = SHOW_STARTING_ANIMATIONS;
            this.mShowingLockscreen = SHOW_STARTING_ANIMATIONS;
            if ((attrs.privateFlags & 4096) != 0) {
                this.mForceStatusBarTransparent = SHOW_STARTING_ANIMATIONS;
                this.mForceStatusBarTransparentWin = win;
            }
        }
        boolean appWindow = ((attrs.type < 1 || attrs.type >= IHwShutdownThread.SHUTDOWN_ANIMATION_WAIT_TIME) && attrs.type != 2100) ? attrs.type == 2101 ? SHOW_STARTING_ANIMATIONS : false : SHOW_STARTING_ANIMATIONS;
        boolean showWhenLocked = (DumpState.DUMP_FROZEN & fl) != 0 ? SHOW_STARTING_ANIMATIONS : false;
        boolean dismissKeyguard = (4194304 & fl) != 0 ? SHOW_STARTING_ANIMATIONS : false;
        int stackId = win.getStackId();
        if (this.mTopFullscreenOpaqueWindowState == null && ((attrs.type == 2100 || attrs.type == 2101) && !sProximityWndName.equals(attrs.getTitle()))) {
            this.mHasCoverView = SHOW_STARTING_ANIMATIONS;
        } else if (this.mTopFullscreenOpaqueWindowState == null && win.isVisibleOrBehindKeyguardLw() && !win.isGoneForLayoutLw()) {
            if ((fl & 2048) != 0) {
                if ((attrs.privateFlags & 1024) != 0) {
                    this.mForceStatusBarFromKeyguard = SHOW_STARTING_ANIMATIONS;
                } else {
                    this.mForceStatusBar = SHOW_STARTING_ANIMATIONS;
                }
            }
            if ((attrs.type == 2023 || attrs.type == 2102) && (!this.mDreamingLockscreen || (win.isVisibleLw() && win.hasDrawnLw()))) {
                this.mShowingDream = SHOW_STARTING_ANIMATIONS;
                appWindow = SHOW_STARTING_ANIMATIONS;
            }
            IApplicationToken appToken = win.getAppToken();
            if (appWindow && attached == null) {
                if (showWhenLocked) {
                    this.mAppsToBeHidden.remove(appToken);
                    this.mAppsThatDismissKeyguard.remove(appToken);
                    if (this.mAppsToBeHidden.isEmpty()) {
                        if (dismissKeyguard && !this.mKeyguardSecure) {
                            this.mAppsThatDismissKeyguard.add(appToken);
                        } else if (win.isDrawnLw() || win.hasAppShownWindows()) {
                            this.mWinShowWhenLocked = win;
                            this.mHideLockScreen = SHOW_STARTING_ANIMATIONS;
                            this.mForceStatusBarFromKeyguard = false;
                        }
                    }
                }
                if (dismissKeyguard) {
                    if (this.mKeyguardSecure) {
                        this.mAppsToBeHidden.add(appToken);
                    } else {
                        this.mAppsToBeHidden.remove(appToken);
                    }
                    this.mAppsThatDismissKeyguard.add(appToken);
                }
                if (!(showWhenLocked || dismissKeyguard)) {
                    if ("com.tencent.news/com.tencent.news.push.alive.offactivity.OffActivity".equals(attrs.getTitle()) || "com.tencent.news/com.tencent.news.push.alive.offactivity.HollowActivity".equals(attrs.getTitle()) || "com.tencent.reading/com.tencent.news.push.alive.offactivity.HollowActivity".equals(attrs.getTitle())) {
                        Slog.i(TAG, "ignore hidden app for com.tencent.news.push.alive.offactivity.HollowActivity or OffActivity");
                    } else {
                        this.mAppsToBeHidden.add(appToken);
                    }
                }
                if (isFullscreen(attrs) && StackId.normallyFullscreenWindows(stackId)) {
                    if (!this.mHasCoverView || isCoverWindow(win)) {
                        this.mTopFullscreenOpaqueWindowState = win;
                    } else {
                        Slog.i(TAG, "skip show window when cover added. Target: " + win);
                    }
                    if (this.mTopFullscreenOpaqueOrDimmingWindowState == null) {
                        this.mTopFullscreenOpaqueOrDimmingWindowState = win;
                    }
                    if (!this.mAppsThatDismissKeyguard.isEmpty() && this.mDismissKeyguard == 0) {
                        Flog.i(305, "Setting mDismissKeyguard true by win " + win);
                        int i = (this.mWinDismissingKeyguard == win && this.mSecureDismissingKeyguard == this.mKeyguardSecure) ? 2 : 1;
                        this.mDismissKeyguard = i;
                        this.mWinDismissingKeyguard = win;
                        this.mSecureDismissingKeyguard = this.mKeyguardSecure;
                        this.mForceStatusBarFromKeyguard = this.mShowingLockscreen ? this.mKeyguardSecure : false;
                    } else if (this.mAppsToBeHidden.isEmpty() && showWhenLocked && (win.isDrawnLw() || win.hasAppShownWindows())) {
                        Flog.i(305, "Setting mHideLockScreen to true by win " + win);
                        this.mHideLockScreen = SHOW_STARTING_ANIMATIONS;
                        this.mForceStatusBarFromKeyguard = false;
                    }
                    if ((fl & 1) != 0) {
                        this.mAllowLockscreenWhenOn = SHOW_STARTING_ANIMATIONS;
                    }
                }
                if (!(this.mKeyguardHidden || this.mWinShowWhenLocked == null || this.mWinShowWhenLocked.getAppToken() == win.getAppToken() || (attrs.flags & DumpState.DUMP_FROZEN) != 0)) {
                    win.hideLw(false);
                }
            }
        } else if (this.mTopFullscreenOpaqueWindowState == null && this.mWinShowWhenLocked == null && win.isAnimatingLw() && appWindow && showWhenLocked && this.mKeyguardHidden) {
            this.mHideLockScreen = SHOW_STARTING_ANIMATIONS;
            this.mWinShowWhenLocked = win;
        }
        boolean reallyVisible = (!win.isVisibleOrBehindKeyguardLw() || win.isGoneForLayoutLw()) ? false : SHOW_STARTING_ANIMATIONS;
        if (this.mTopFullscreenOpaqueOrDimmingWindowState == null && reallyVisible && win.isDimming() && StackId.normallyFullscreenWindows(stackId)) {
            this.mTopFullscreenOpaqueOrDimmingWindowState = win;
        }
        if (this.mTopDockedOpaqueWindowState == null && reallyVisible && appWindow && attached == null && isFullscreen(attrs) && stackId == 3) {
            this.mTopDockedOpaqueWindowState = win;
            if (this.mTopDockedOpaqueOrDimmingWindowState == null) {
                this.mTopDockedOpaqueOrDimmingWindowState = win;
            }
        }
        if (this.mTopDockedOpaqueOrDimmingWindowState == null && reallyVisible && win.isDimming() && stackId == 3) {
            this.mTopDockedOpaqueOrDimmingWindowState = win;
        }
    }

    private boolean isCoverWindow(WindowState win) {
        LayoutParams attrs = null;
        if (win != null) {
            attrs = win.getAttrs();
        }
        int type = attrs == null ? 0 : attrs.type;
        if ((type == 2100 || type == 2101) && !sProximityWndName.equals(attrs.getTitle())) {
            return SHOW_STARTING_ANIMATIONS;
        }
        return false;
    }

    private boolean isFullscreen(LayoutParams attrs) {
        if (attrs.x == 0 && attrs.y == 0 && attrs.width == -1 && attrs.height == -1) {
            return SHOW_STARTING_ANIMATIONS;
        }
        return false;
    }

    public int finishPostLayoutPolicyLw() {
        LayoutParams attrs;
        if (!(this.mWinShowWhenLocked == null || this.mTopFullscreenOpaqueWindowState == null || this.mWinShowWhenLocked.getAppToken() == this.mTopFullscreenOpaqueWindowState.getAppToken() || !isKeyguardLocked())) {
            LayoutParams attrs2 = this.mWinShowWhenLocked.getAttrs();
            attrs2.flags |= DumpState.DUMP_DEXOPT;
            this.mTopFullscreenOpaqueWindowState.hideLw(false);
            this.mTopFullscreenOpaqueWindowState = this.mWinShowWhenLocked;
        }
        int changes = 0;
        boolean z = false;
        if (this.mTopFullscreenOpaqueWindowState != null) {
            attrs = this.mTopFullscreenOpaqueWindowState.getAttrs();
        } else {
            attrs = null;
        }
        if (!this.mShowingDream) {
            this.mDreamingLockscreen = this.mShowingLockscreen;
            if (this.mDreamingSleepTokenNeeded) {
                this.mDreamingSleepTokenNeeded = false;
                this.mHandler.obtainMessage(15, 0, 1).sendToTarget();
            }
        } else if (!this.mDreamingSleepTokenNeeded) {
            this.mDreamingSleepTokenNeeded = SHOW_STARTING_ANIMATIONS;
            this.mHandler.obtainMessage(15, 1, 1).sendToTarget();
        }
        if (this.mStatusBar != null) {
            boolean shouldBeTransparent;
            if (!this.mForceStatusBarTransparent || this.mForceStatusBar) {
                shouldBeTransparent = false;
            } else {
                shouldBeTransparent = this.mForceStatusBarFromKeyguard ? false : SHOW_STARTING_ANIMATIONS;
            }
            if (!shouldBeTransparent) {
                this.mStatusBarController.setShowTransparent(false);
            } else if (!this.mStatusBar.isVisibleLw()) {
                this.mStatusBarController.setShowTransparent(SHOW_STARTING_ANIMATIONS);
            }
            LayoutParams statusBarAttrs = this.mStatusBar.getAttrs();
            boolean statusBarExpanded = statusBarAttrs.height == -1 ? statusBarAttrs.width == -1 ? SHOW_STARTING_ANIMATIONS : false : false;
            if (this.mForceStatusBar || this.mForceStatusBarFromKeyguard || this.mForceStatusBarTransparent || ((this.mForceStatusBarTransparent && this.mForceStatusBarTransparentWin != null && this.mForceStatusBarTransparentWin.isVisibleLw()) || statusBarExpanded)) {
                if (this.mStatusBarController.setBarShowingLw(SHOW_STARTING_ANIMATIONS)) {
                    changes = 1;
                }
                z = this.mTopIsFullscreen ? this.mStatusBar.isAnimatingLw() : false;
                if (this.mForceStatusBarFromKeyguard && this.mStatusBarController.isTransientShowing()) {
                    this.mStatusBarController.updateVisibilityLw(false, this.mLastSystemUiFlags, this.mLastSystemUiFlags);
                }
                if (statusBarExpanded && this.mNavigationBar != null && !computeNaviBarFlag() && this.mNavigationBarController.setBarShowingLw(SHOW_STARTING_ANIMATIONS)) {
                    changes |= 1;
                }
            } else if (this.mTopFullscreenOpaqueWindowState != null) {
                z = (PolicyControl.getWindowFlags(null, attrs) & 1024) == 0 ? (this.mLastSystemUiFlags & 4) != 0 ? SHOW_STARTING_ANIMATIONS : false : SHOW_STARTING_ANIMATIONS;
                if (this.mStatusBarController.isTransientShowing()) {
                    if (this.mStatusBarController.setBarShowingLw(SHOW_STARTING_ANIMATIONS)) {
                        changes = 1;
                    }
                } else if (!z || this.mWindowManagerInternal.isStackVisible(2) || this.mWindowManagerInternal.isStackVisible(3)) {
                    if (this.mStatusBarController.isTransientHiding()) {
                        Slog.v(TAG, "not fullscreen but transientBarState is hiding, so reset");
                        this.mStatusBarController.sethwTransientBarState(0);
                    }
                    if (this.mStatusBarController.setBarShowingLw(SHOW_STARTING_ANIMATIONS)) {
                        changes = 1;
                    }
                } else if (this.mStatusBarController.setBarShowingLw(false)) {
                    changes = 1;
                }
            }
        }
        if (this.mTopIsFullscreen != z) {
            if (!z) {
                changes |= 1;
            }
            this.mTopIsFullscreen = z;
        }
        if (!(this.mKeyguardDelegate == null || this.mStatusBar == null)) {
            if (this.mDismissKeyguard != 0 && !this.mKeyguardSecure) {
                this.mKeyguardHidden = SHOW_STARTING_ANIMATIONS;
                if (setKeyguardOccludedLw(SHOW_STARTING_ANIMATIONS)) {
                    changes |= 7;
                }
                if (this.mKeyguardDelegate.isShowing()) {
                    this.mHandler.post(new Runnable() {
                        public void run() {
                            PhoneWindowManager.this.mKeyguardDelegate.keyguardDone(false, false);
                        }
                    });
                }
            } else if (this.mHideLockScreen) {
                this.mKeyguardHidden = SHOW_STARTING_ANIMATIONS;
                this.mWinDismissingKeyguard = null;
                if (setKeyguardOccludedLw(SHOW_STARTING_ANIMATIONS)) {
                    changes |= 7;
                }
            } else if (this.mDismissKeyguard != 0) {
                this.mKeyguardHidden = false;
                if (setKeyguardOccludedLw(false)) {
                    changes |= 7;
                }
                if (this.mDismissKeyguard == 1) {
                    this.mHandler.post(new Runnable() {
                        public void run() {
                            Flog.i(305, "finishPostLayoutPolicyLw: need dismiss keyguard");
                            PhoneWindowManager.this.mKeyguardDelegate.dismiss();
                        }
                    });
                }
            } else if (isCoverWindow(this.mTopFullscreenOpaqueWindowState)) {
                Slog.v(TAG, "skip setKeyguardOccludedLw when top is cover.");
            } else if (this.mCust == null || !this.mCust.isAuthenticationGrace(this.mContext)) {
                this.mWinDismissingKeyguard = null;
                this.mSecureDismissingKeyguard = false;
                this.mKeyguardHidden = false;
                if (setKeyguardOccludedLw(false)) {
                    changes |= 7;
                }
            } else {
                this.mKeyguardHidden = SHOW_STARTING_ANIMATIONS;
                if (setKeyguardOccludedLw(SHOW_STARTING_ANIMATIONS)) {
                    changes |= 7;
                }
                this.mCust.skipAuthKeyguard(this.mKeyguardDelegate, this.mHandler);
            }
        }
        if ((updateSystemUiVisibilityLw() & SYSTEM_UI_CHANGING_LAYOUT) != 0) {
            changes |= 1;
        }
        updateLockScreenTimeout();
        return changes;
    }

    protected boolean setKeyguardOccludedLw(boolean isOccluded) {
        boolean wasOccluded = this.mKeyguardOccluded;
        boolean showing = this.mKeyguardDelegate.isShowing();
        LayoutParams attrs;
        if (wasOccluded && !isOccluded && showing) {
            this.mKeyguardOccluded = false;
            this.mKeyguardDelegate.setOccluded(false);
            attrs = this.mStatusBar.getAttrs();
            attrs.privateFlags |= 1024;
            return SHOW_STARTING_ANIMATIONS;
        } else if (wasOccluded || !isOccluded || !showing) {
            return false;
        } else {
            this.mKeyguardOccluded = SHOW_STARTING_ANIMATIONS;
            this.mKeyguardDelegate.setOccluded(SHOW_STARTING_ANIMATIONS);
            attrs = this.mStatusBar.getAttrs();
            attrs.privateFlags &= -1025;
            attrs = this.mStatusBar.getAttrs();
            attrs.flags &= -1048577;
            return SHOW_STARTING_ANIMATIONS;
        }
    }

    private boolean isStatusBarKeyguard() {
        if (this.mStatusBar == null || (this.mStatusBar.getAttrs().privateFlags & 1024) == 0) {
            return false;
        }
        return SHOW_STARTING_ANIMATIONS;
    }

    public boolean allowAppAnimationsLw() {
        if (isStatusBarKeyguard() || this.mShowingDream) {
            return false;
        }
        return SHOW_STARTING_ANIMATIONS;
    }

    public int focusChangedLw(WindowState lastFocus, WindowState newFocus) {
        this.mFocusedWindow = newFocus;
        if (this.mFocusedWindow != null) {
            updateSystemUiColorLw(this.mFocusedWindow);
            if (this.mLastStartingWindow != null && this.mLastStartingWindow.isVisibleLw() && this.mFocusedWindow.getAttrs().type == 2003) {
                updateSystemUiColorLw(this.mLastStartingWindow);
            }
        }
        if ((updateSystemUiVisibilityLw() & SYSTEM_UI_CHANGING_LAYOUT) != 0) {
            return 1;
        }
        return 0;
    }

    public void notifyLidSwitchChanged(long whenNanos, boolean lidOpen) {
        int newLidState = lidOpen ? 1 : 0;
        if (newLidState != this.mLidState) {
            this.mLidState = newLidState;
            applyLidSwitchState();
            updateRotation(SHOW_STARTING_ANIMATIONS);
            if (lidOpen) {
                wakeUp(SystemClock.uptimeMillis(), this.mAllowTheaterModeWakeFromLidSwitch, "android.policy:LID");
            } else if (!this.mLidControlsSleep) {
                this.mPowerManager.userActivity(SystemClock.uptimeMillis(), false);
            }
        }
    }

    public void notifyCameraLensCoverSwitchChanged(long whenNanos, boolean lensCovered) {
        boolean keyguardActive = false;
        int lensCoverState = lensCovered ? 1 : 0;
        if (this.mCameraLensCoverState != lensCoverState) {
            if (this.mCameraLensCoverState == 1 && lensCoverState == 0) {
                Intent intent;
                if (this.mKeyguardDelegate != null) {
                    keyguardActive = this.mKeyguardDelegate.isShowing();
                }
                if (keyguardActive) {
                    intent = new Intent("android.media.action.STILL_IMAGE_CAMERA_SECURE");
                } else {
                    intent = new Intent("android.media.action.STILL_IMAGE_CAMERA");
                }
                wakeUp(whenNanos / 1000000, this.mAllowTheaterModeWakeFromCameraLens, "android.policy:CAMERA_COVER");
                startActivityAsUser(intent, UserHandle.CURRENT_OR_SELF);
            }
            this.mCameraLensCoverState = lensCoverState;
        }
    }

    void setHdmiPlugged(boolean plugged) {
        if (this.mHdmiPlugged != plugged) {
            this.mHdmiPlugged = plugged;
            updateRotation(SHOW_STARTING_ANIMATIONS, SHOW_STARTING_ANIMATIONS);
            Intent intent = new Intent("android.intent.action.HDMI_PLUGGED");
            intent.addFlags(67108864);
            intent.putExtra(AudioService.CONNECT_INTENT_KEY_STATE, plugged);
            this.mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        }
    }

    void initializeHdmiState() {
        IOException ex;
        NumberFormatException ex2;
        Throwable th;
        boolean z = false;
        boolean plugged = false;
        if (new File("/sys/devices/virtual/switch/hdmi/state").exists()) {
            this.mHDMIObserver.startObserving("DEVPATH=/devices/virtual/switch/hdmi");
            String filename = "/sys/class/switch/hdmi/state";
            FileReader fileReader = null;
            try {
                FileReader reader = new FileReader("/sys/class/switch/hdmi/state");
                try {
                    char[] buf = new char[15];
                    int n = reader.read(buf);
                    if (n > 1) {
                        plugged = Integer.parseInt(new String(buf, 0, n + -1)) != 0 ? SHOW_STARTING_ANIMATIONS : false;
                    }
                    if (reader != null) {
                        try {
                            reader.close();
                        } catch (IOException e) {
                        }
                    }
                } catch (IOException e2) {
                    ex = e2;
                    fileReader = reader;
                    Slog.w(TAG, "Couldn't read hdmi state from /sys/class/switch/hdmi/state: " + ex);
                    if (fileReader != null) {
                        try {
                            fileReader.close();
                        } catch (IOException e3) {
                        }
                    }
                    this.mHdmiPlugged = plugged ? SHOW_STARTING_ANIMATIONS : false;
                    if (!this.mHdmiPlugged) {
                        z = SHOW_STARTING_ANIMATIONS;
                    }
                    setHdmiPlugged(z);
                } catch (NumberFormatException e4) {
                    ex2 = e4;
                    fileReader = reader;
                    try {
                        Slog.w(TAG, "Couldn't read hdmi state from /sys/class/switch/hdmi/state: " + ex2);
                        if (fileReader != null) {
                            try {
                                fileReader.close();
                            } catch (IOException e5) {
                            }
                        }
                        if (plugged) {
                        }
                        this.mHdmiPlugged = plugged ? SHOW_STARTING_ANIMATIONS : false;
                        if (this.mHdmiPlugged) {
                            z = SHOW_STARTING_ANIMATIONS;
                        }
                        setHdmiPlugged(z);
                    } catch (Throwable th2) {
                        th = th2;
                        if (fileReader != null) {
                            try {
                                fileReader.close();
                            } catch (IOException e6) {
                            }
                        }
                        throw th;
                    }
                } catch (Throwable th3) {
                    th = th3;
                    fileReader = reader;
                    if (fileReader != null) {
                        fileReader.close();
                    }
                    throw th;
                }
            } catch (IOException e7) {
                ex = e7;
                Slog.w(TAG, "Couldn't read hdmi state from /sys/class/switch/hdmi/state: " + ex);
                if (fileReader != null) {
                    fileReader.close();
                }
                if (plugged) {
                }
                this.mHdmiPlugged = plugged ? SHOW_STARTING_ANIMATIONS : false;
                if (this.mHdmiPlugged) {
                    z = SHOW_STARTING_ANIMATIONS;
                }
                setHdmiPlugged(z);
            } catch (NumberFormatException e8) {
                ex2 = e8;
                Slog.w(TAG, "Couldn't read hdmi state from /sys/class/switch/hdmi/state: " + ex2);
                if (fileReader != null) {
                    fileReader.close();
                }
                if (plugged) {
                }
                this.mHdmiPlugged = plugged ? SHOW_STARTING_ANIMATIONS : false;
                if (this.mHdmiPlugged) {
                    z = SHOW_STARTING_ANIMATIONS;
                }
                setHdmiPlugged(z);
            }
        }
        if (plugged) {
        }
        this.mHdmiPlugged = plugged ? SHOW_STARTING_ANIMATIONS : false;
        if (this.mHdmiPlugged) {
            z = SHOW_STARTING_ANIMATIONS;
        }
        setHdmiPlugged(z);
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void takeScreenshot(final int screenshotType) {
        synchronized (this.mScreenshotLock) {
            if (this.mScreenshotConnection == null) {
                ComponentName serviceComponent = new ComponentName(SYSUI_PACKAGE, SYSUI_SCREENSHOT_SERVICE);
                Intent serviceIntent = new Intent();
                serviceIntent.setComponent(serviceComponent);
                ServiceConnection conn = new ServiceConnection() {
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        synchronized (PhoneWindowManager.this.mScreenshotLock) {
                            if (PhoneWindowManager.this.mScreenshotConnection != this) {
                                return;
                            }
                            Messenger messenger = new Messenger(service);
                            Message msg = Message.obtain(null, screenshotType);
                            AnonymousClass19 myConn = this;
                            msg.replyTo = new Messenger(new Handler(PhoneWindowManager.this.mHandler.getLooper()) {
                                public void handleMessage(Message msg) {
                                    synchronized (PhoneWindowManager.this.mScreenshotLock) {
                                        if (PhoneWindowManager.this.mScreenshotConnection == this) {
                                            PhoneWindowManager.this.mContext.unbindService(PhoneWindowManager.this.mScreenshotConnection);
                                            if (PhoneWindowManager.HWFLOW) {
                                                Slog.i(PhoneWindowManager.TAG, "takeScreenshot  set mScreenshotConnection to null");
                                            }
                                            PhoneWindowManager.this.mScreenshotConnection = null;
                                            PhoneWindowManager.this.mHandler.removeCallbacks(PhoneWindowManager.this.mScreenshotTimeout);
                                        }
                                    }
                                }
                            });
                            msg.arg2 = 0;
                            msg.arg1 = 0;
                            if (PhoneWindowManager.this.mStatusBar != null && PhoneWindowManager.this.mStatusBar.isVisibleLw()) {
                                msg.arg1 = 1;
                            }
                            if (PhoneWindowManager.mUsingHwNavibar) {
                                if (!PhoneWindowManager.this.isNaviBarMini()) {
                                    msg.arg2 = 1;
                                }
                            } else if (PhoneWindowManager.this.mNavigationBar != null && PhoneWindowManager.this.mNavigationBar.isVisibleLw()) {
                                msg.arg2 = 1;
                            }
                            try {
                                messenger.send(msg);
                            } catch (RemoteException e) {
                            }
                        }
                    }

                    public void onServiceDisconnected(ComponentName name) {
                        PhoneWindowManager.this.notifyScreenshotError();
                    }
                };
                if (HWFLOW) {
                    Slog.i(TAG, "takeScreenshot  bindServiceAsUser");
                }
                if (this.mContext.bindServiceAsUser(serviceIntent, conn, 33554433, UserHandle.CURRENT)) {
                    this.mScreenshotConnection = conn;
                    this.mHandler.postDelayed(this.mScreenshotTimeout, JobStatus.DEFAULT_TRIGGER_UPDATE_DELAY);
                }
            } else if (HWFLOW) {
                Slog.i(TAG, "takeScreenshot  not start");
            }
        }
    }

    private void notifyScreenshotError() {
        ComponentName errorComponent = new ComponentName(SYSUI_PACKAGE, SYSUI_SCREENSHOT_ERROR_RECEIVER);
        Intent errorIntent = new Intent("android.intent.action.USER_PRESENT");
        errorIntent.setComponent(errorComponent);
        errorIntent.addFlags(335544320);
        this.mContext.sendBroadcastAsUser(errorIntent, UserHandle.CURRENT);
    }

    public int interceptKeyBeforeQueueing(KeyEvent event, int policyFlags) {
        if (this.mSystemBooted) {
            boolean z;
            boolean isWakeKey;
            int result;
            boolean interactive = (536870912 & policyFlags) != 0 ? SHOW_STARTING_ANIMATIONS : false;
            boolean down = event.getAction() == 0 ? SHOW_STARTING_ANIMATIONS : false;
            boolean canceled = event.isCanceled();
            int keyCode = event.getKeyCode();
            boolean isInjected = (16777216 & policyFlags) != 0 ? SHOW_STARTING_ANIMATIONS : false;
            if (this.mKeyguardDelegate == null) {
                z = false;
            } else if (interactive) {
                z = isKeyguardShowingAndNotOccluded();
            } else {
                z = this.mKeyguardDelegate.isShowing();
            }
            if (HWFLOW) {
                Log.d(TAG, "interceptKeyTq keycode=" + keyCode + " interactive=" + interactive + " keyguardActive=" + z + " policyFlags=" + Integer.toHexString(policyFlags) + " down " + down + " canceled " + canceled);
            }
            if ((policyFlags & 1) == 0) {
                isWakeKey = event.isWakeKey();
            } else {
                isWakeKey = SHOW_STARTING_ANIMATIONS;
            }
            if (interactive || (isInjected && !isWakeKey)) {
                result = 1;
                isWakeKey = false;
            } else if (interactive || !shouldDispatchInputWhenNonInteractive()) {
                result = 0;
                if (isWakeKey && !(down && isWakeKeyWhenScreenOff(keyCode))) {
                    isWakeKey = false;
                }
            } else {
                result = 1;
            }
            if (isValidGlobalKey(keyCode) && this.mGlobalKeyManager.shouldHandleGlobalKey(keyCode, event)) {
                if (isWakeKey) {
                    wakeUp(event.getEventTime(), this.mAllowTheaterModeWakeFromKey, "android.policy:KEY");
                }
                Log.d(TAG, "key: " + keyCode + " , handled globally, just return the result " + result);
                return result;
            }
            boolean useHapticFeedback = (!down || (policyFlags & 2) == 0) ? false : event.getRepeatCount() == 0 ? SHOW_STARTING_ANIMATIONS : false;
            Message msg;
            TelecomManager telecomManager;
            switch (keyCode) {
                case 4:
                    if (!down) {
                        boolean handled = this.mBackKeyHandled;
                        cancelPendingBackKeyAction();
                        if (handled) {
                            result &= -2;
                            break;
                        }
                    }
                    this.mBackKeyHandled = false;
                    if (hasLongPressOnBackBehavior()) {
                        msg = this.mHandler.obtainMessage(18);
                        msg.setAsynchronous(SHOW_STARTING_ANIMATIONS);
                        this.mHandler.sendMessageDelayed(msg, ViewConfiguration.get(this.mContext).getDeviceGlobalActionKeyTimeout());
                        break;
                    }
                    break;
                case 5:
                    if (down) {
                        telecomManager = getTelecommService();
                        if (telecomManager != null && telecomManager.isRinging()) {
                            Log.i(TAG, "interceptKeyBeforeQueueing: CALL key-down while ringing: Answer the call!");
                            telecomManager.acceptRingingCall();
                            result &= -2;
                            break;
                        }
                    }
                    break;
                case 6:
                    result &= -2;
                    if (!down) {
                        if (!this.mEndCallKeyHandled) {
                            this.mHandler.removeCallbacks(this.mEndCallLongPress);
                            if (!canceled && (((this.mEndcallBehavior & 1) == 0 || !goHome()) && (this.mEndcallBehavior & 2) != 0)) {
                                this.mPowerManager.goToSleep(event.getEventTime(), 4, 0);
                                isWakeKey = false;
                                break;
                            }
                        }
                    }
                    telecomManager = getTelecommService();
                    boolean hungUp = false;
                    if (telecomManager != null) {
                        hungUp = telecomManager.endCall();
                    }
                    if (interactive && !r9) {
                        this.mEndCallKeyHandled = false;
                        this.mHandler.postDelayed(this.mEndCallLongPress, ViewConfiguration.get(this.mContext).getDeviceGlobalActionKeyTimeout());
                        break;
                    }
                    this.mEndCallKeyHandled = SHOW_STARTING_ANIMATIONS;
                    break;
                    break;
                case H.WAITING_FOR_DRAWN_TIMEOUT /*24*/:
                case 25:
                case 164:
                    if (keyCode == 25) {
                        if (!down) {
                            this.mScreenshotChordVolumeDownKeyTriggered = false;
                            cancelPendingScreenshotChordAction();
                        } else if (interactive && !this.mScreenshotChordVolumeDownKeyTriggered && (event.getFlags() & 1024) == 0) {
                            this.mScreenshotChordVolumeDownKeyTriggered = SHOW_STARTING_ANIMATIONS;
                            this.mScreenshotChordVolumeDownKeyTime = event.getDownTime();
                            this.mScreenshotChordVolumeDownKeyConsumed = false;
                            cancelPendingPowerKeyAction();
                            interceptScreenshotChord();
                        }
                    } else if (keyCode == 24) {
                        if (!down) {
                            this.mScreenshotChordVolumeUpKeyTriggered = false;
                            cancelPendingScreenshotChordAction();
                        } else if (interactive && !this.mScreenshotChordVolumeUpKeyTriggered && (event.getFlags() & 1024) == 0) {
                            this.mScreenshotChordVolumeUpKeyTriggered = SHOW_STARTING_ANIMATIONS;
                            cancelPendingPowerKeyAction();
                            cancelPendingScreenshotChordAction();
                        }
                    }
                    if (down) {
                        telecomManager = getTelecommService();
                        if (telecomManager != null) {
                            if (!telecomManager.isRinging()) {
                                if (telecomManager.isInCall() && (result & 1) == 0) {
                                    MediaSessionLegacyHelper.getHelper(this.mContext).sendVolumeKeyEvent(event, false);
                                    break;
                                }
                            }
                            Log.i(TAG, "interceptKeyBeforeQueueing: VOLUME key-down while ringing: Silence ringer!");
                            telecomManager.silenceRinger();
                            result &= -2;
                            break;
                        }
                    }
                    Log.i(TAG, "Volume key pass to user " + ((result & 1) != 0 ? SHOW_STARTING_ANIMATIONS : false));
                    if (!this.mUseTvRouting) {
                        if ((result & 1) == 0) {
                            MediaSessionLegacyHelper.getHelper(this.mContext).sendVolumeKeyEvent(event, SHOW_STARTING_ANIMATIONS);
                            break;
                        }
                    }
                    result |= 1;
                    break;
                    break;
                case H.DO_ANIMATION_CALLBACK /*26*/:
                    result &= -2;
                    isWakeKey = false;
                    if (!down) {
                        interceptPowerKeyUp(event, interactive, canceled);
                        break;
                    }
                    interceptPowerKeyDown(event, interactive);
                    break;
                case HdmiCecKeycode.CEC_KEYCODE_RESERVED /*79*/:
                case HdmiCecKeycode.CEC_KEYCODE_INITIAL_CONFIGURATION /*85*/:
                case HdmiCecKeycode.CEC_KEYCODE_SELECT_BROADCAST_TYPE /*86*/:
                case HdmiCecKeycode.CEC_KEYCODE_SELECT_SOUND_PRESENTATION /*87*/:
                case 88:
                case 89:
                case 90:
                case 91:
                case 126:
                case 127:
                case 130:
                case NetdResponseCode.DnsProxyQueryResult /*222*/:
                    if (MediaSessionLegacyHelper.getHelper(this.mContext).isGlobalPriorityActive()) {
                        result &= -2;
                    }
                    if ((result & 1) == 0) {
                        this.mBroadcastWakeLock.acquire();
                        msg = this.mHandler.obtainMessage(3, new KeyEvent(event));
                        msg.setAsynchronous(SHOW_STARTING_ANIMATIONS);
                        msg.sendToTarget();
                        break;
                    }
                    break;
                case 171:
                    if (this.mShortPressWindowBehavior == 1 && this.mTvPictureInPictureVisible) {
                        if (!down) {
                            showTvPictureInPictureMenu(event);
                        }
                        result &= -2;
                        break;
                    }
                case NetdResponseCode.ClatdStatusResult /*223*/:
                    result &= -2;
                    isWakeKey = false;
                    if (!this.mPowerManager.isInteractive()) {
                        useHapticFeedback = false;
                    }
                    if (!down) {
                        sleepRelease(event.getEventTime());
                        break;
                    }
                    sleepPress(event.getEventTime());
                    break;
                case 224:
                    result &= -2;
                    isWakeKey = SHOW_STARTING_ANIMATIONS;
                    break;
                case 231:
                    if ((result & 1) == 0 && !down) {
                        this.mBroadcastWakeLock.acquire();
                        msg = this.mHandler.obtainMessage(12, z ? 1 : 0, 0);
                        msg.setAsynchronous(SHOW_STARTING_ANIMATIONS);
                        msg.sendToTarget();
                        break;
                    }
                case 276:
                    result &= -2;
                    isWakeKey = false;
                    if (!down) {
                        this.mPowerManagerInternal.setUserInactiveOverrideFromWindowManager();
                        break;
                    }
                    break;
                case 701:
                    if (down && !SystemProperties.getBoolean("sys.super_power_save", false)) {
                        quickOpenCameraService("flip");
                        break;
                    }
            }
            if (useHapticFeedback) {
                performHapticFeedbackLw(null, 1, false);
            }
            if (isWakeKey) {
                wakeUp(event.getEventTime(), this.mAllowTheaterModeWakeFromKey, "android.policy:KEY");
            }
            Log.d(TAG, "interceptKeyBeforeQueueing: key " + keyCode + " , result : " + result);
            return result;
        }
        Log.d(TAG, "we have not yet booted, don't let key events do anything.");
        return 0;
    }

    private void quickOpenCameraService(String command) {
        Intent intent = new Intent(HUAWEI_PRE_CAMERA_START_MODE);
        intent.setPackage("com.huawei.camera");
        intent.putExtra("command", command);
        this.mContext.startService(intent);
    }

    private static boolean isValidGlobalKey(int keyCode) {
        switch (keyCode) {
            case H.DO_ANIMATION_CALLBACK /*26*/:
            case NetdResponseCode.ClatdStatusResult /*223*/:
            case 224:
                return false;
            default:
                return SHOW_STARTING_ANIMATIONS;
        }
    }

    protected boolean isWakeKeyWhenScreenOff(int keyCode) {
        boolean z = SHOW_STARTING_ANIMATIONS;
        switch (keyCode) {
            case H.WAITING_FOR_DRAWN_TIMEOUT /*24*/:
            case 25:
            case 164:
                if (this.mDockMode == 0) {
                    z = false;
                }
                return z;
            case H.DO_DISPLAY_ADDED /*27*/:
            case HdmiCecKeycode.CEC_KEYCODE_RESERVED /*79*/:
            case HdmiCecKeycode.CEC_KEYCODE_INITIAL_CONFIGURATION /*85*/:
            case HdmiCecKeycode.CEC_KEYCODE_SELECT_BROADCAST_TYPE /*86*/:
            case HdmiCecKeycode.CEC_KEYCODE_SELECT_SOUND_PRESENTATION /*87*/:
            case 88:
            case 89:
            case 90:
            case 91:
            case 126:
            case 127:
            case 130:
            case NetdResponseCode.DnsProxyQueryResult /*222*/:
                return false;
            default:
                return SHOW_STARTING_ANIMATIONS;
        }
    }

    public int interceptMotionBeforeQueueingNonInteractive(long whenNanos, int policyFlags) {
        if ((policyFlags & 1) != 0 && wakeUp(whenNanos / 1000000, this.mAllowTheaterModeWakeFromMotion, "android.policy:MOTION")) {
            return 0;
        }
        if (shouldDispatchInputWhenNonInteractive() && !this.mInterceptInputForWaitBrightness) {
            return 1;
        }
        if (isTheaterModeEnabled() && (policyFlags & 1) != 0) {
            wakeUp(whenNanos / 1000000, this.mAllowTheaterModeWakeFromMotionWhenNotDreaming, "android.policy:MOTION");
        }
        return 0;
    }

    private boolean shouldDispatchInputWhenNonInteractive() {
        boolean displayOff = (this.mDisplay == null || this.mDisplay.getState() == 1) ? SHOW_STARTING_ANIMATIONS : false;
        if (displayOff && !this.mHasFeatureWatch) {
            return false;
        }
        if (isKeyguardShowingAndNotOccluded() && !displayOff) {
            return SHOW_STARTING_ANIMATIONS;
        }
        IDreamManager dreamManager = getDreamManager();
        if (dreamManager != null) {
            try {
                if (dreamManager.isDreaming()) {
                    return SHOW_STARTING_ANIMATIONS;
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "RemoteException when checking if dreaming", e);
            }
        }
        return false;
    }

    private void dispatchDirectAudioEvent(KeyEvent event) {
        if (event.getAction() == 0) {
            int keyCode = event.getKeyCode();
            String pkgName = this.mContext.getOpPackageName();
            switch (keyCode) {
                case H.WAITING_FOR_DRAWN_TIMEOUT /*24*/:
                    try {
                        getAudioService().adjustSuggestedStreamVolume(1, Integer.MIN_VALUE, 4101, pkgName, TAG);
                        break;
                    } catch (RemoteException e) {
                        Log.e(TAG, "Error dispatching volume up in dispatchTvAudioEvent.", e);
                        break;
                    }
                case 25:
                    try {
                        getAudioService().adjustSuggestedStreamVolume(-1, Integer.MIN_VALUE, 4101, pkgName, TAG);
                        break;
                    } catch (RemoteException e2) {
                        Log.e(TAG, "Error dispatching volume down in dispatchTvAudioEvent.", e2);
                        break;
                    }
                case 164:
                    try {
                        if (event.getRepeatCount() == 0) {
                            getAudioService().adjustSuggestedStreamVolume(101, Integer.MIN_VALUE, 4101, pkgName, TAG);
                            break;
                        }
                    } catch (RemoteException e22) {
                        Log.e(TAG, "Error dispatching mute in dispatchTvAudioEvent.", e22);
                        break;
                    }
                    break;
            }
        }
    }

    void dispatchMediaKeyWithWakeLock(KeyEvent event) {
        Slog.d(TAG, "dispatchMediaKeyWithWakeLock: action=" + event.getAction() + ", keyCode=" + event.getKeyCode() + ", flags=" + event.getFlags() + ", repeatCount=" + event.getRepeatCount());
        if (this.mHavePendingMediaKeyRepeatWithWakeLock) {
            Slog.d(TAG, "dispatchMediaKeyWithWakeLock: canceled repeat");
            this.mHandler.removeMessages(4);
            this.mHavePendingMediaKeyRepeatWithWakeLock = false;
            this.mBroadcastWakeLock.release();
        }
        dispatchMediaKeyWithWakeLockToAudioService(event);
        if (event.getAction() == 0 && event.getRepeatCount() == 0) {
            this.mHavePendingMediaKeyRepeatWithWakeLock = SHOW_STARTING_ANIMATIONS;
            Slog.d(TAG, "dispatchMediaKeyWithWakeLock: send repeat event");
            Message msg = this.mHandler.obtainMessage(4, event);
            msg.setAsynchronous(SHOW_STARTING_ANIMATIONS);
            this.mHandler.sendMessageDelayed(msg, (long) ViewConfiguration.getKeyRepeatTimeout());
            return;
        }
        this.mBroadcastWakeLock.release();
    }

    void dispatchMediaKeyRepeatWithWakeLock(KeyEvent event) {
        this.mHavePendingMediaKeyRepeatWithWakeLock = false;
        KeyEvent repeatEvent = KeyEvent.changeTimeRepeat(event, SystemClock.uptimeMillis(), 1, event.getFlags() | 128);
        Slog.d(TAG, "dispatchMediaKeyRepeatWithWakeLock: dispatch media key with long press");
        dispatchMediaKeyWithWakeLockToAudioService(repeatEvent);
        this.mBroadcastWakeLock.release();
    }

    void dispatchMediaKeyWithWakeLockToAudioService(KeyEvent event) {
        if (ActivityManagerNative.isSystemReady()) {
            MediaSessionLegacyHelper.getHelper(this.mContext).sendMediaButtonEvent(event, SHOW_STARTING_ANIMATIONS);
        }
    }

    void launchVoiceAssistWithWakeLock(boolean keyguardActive) {
        IDeviceIdleController dic = IDeviceIdleController.Stub.asInterface(ServiceManager.getService("deviceidle"));
        if (dic != null) {
            try {
                dic.exitIdle("voice-search");
            } catch (RemoteException e) {
            }
        }
        Intent voiceIntent = new Intent("android.speech.action.VOICE_SEARCH_HANDS_FREE");
        voiceIntent.putExtra("android.speech.extras.EXTRA_SECURE", keyguardActive);
        startActivityAsUser(voiceIntent, UserHandle.CURRENT_OR_SELF);
        this.mBroadcastWakeLock.release();
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void requestTransientBars(WindowState swipeTarget) {
        synchronized (this.mWindowManagerFuncs.getWindowManagerLock()) {
            if (isUserSetupComplete()) {
                boolean sb = this.mStatusBarController.checkShowTransientBarLw();
                boolean nb = this.mNavigationBarController.checkShowTransientBarLw();
                if (sb || nb) {
                    if (nb || swipeTarget != this.mNavigationBar) {
                        if (sb) {
                            this.mStatusBarController.showTransient();
                        }
                        if (nb) {
                            this.mNavigationBarController.showTransient();
                        }
                        this.mImmersiveModeConfirmation.confirmCurrentPrompt();
                        updateSystemUiVisibilityLw();
                    } else {
                        return;
                    }
                }
            }
        }
    }

    public void startedGoingToSleep(int why) {
        this.mCameraGestureTriggeredDuringGoingToSleep = false;
        this.mGoingToSleep = SHOW_STARTING_ANIMATIONS;
        if (this.mKeyguardDelegate != null && needTurnOff(why) && needTurnOffWithDismissFlag()) {
            Flog.i(305, "call onScreenTurnedOff(" + why + ")");
            this.mKeyguardDelegate.onStartedGoingToSleep(why);
        }
    }

    public void finishedGoingToSleep(int why) {
        EventLog.writeEvent(70000, 0);
        Flog.i(305, "Finished going to sleep... (why=" + why + ")");
        MetricsLogger.histogram(this.mContext, "screen_timeout", this.mLockScreenTimeout / 1000);
        this.mGoingToSleep = false;
        synchronized (this.mLock) {
            this.mAwake = false;
            updateWakeGestureListenerLp();
            updateOrientationListenerLp();
            updateLockScreenTimeout();
        }
        if (this.mKeyguardDelegate != null) {
            this.mKeyguardDelegate.onFinishedGoingToSleep(why, this.mCameraGestureTriggeredDuringGoingToSleep);
        }
        this.mCameraGestureTriggeredDuringGoingToSleep = false;
    }

    public void startedWakingUp() {
        EventLog.writeEvent(70000, 1);
        Flog.i(305, "Started waking up...");
        if (mSupportAod) {
            startAodService(4);
        }
        synchronized (this.mLock) {
            this.mAwake = SHOW_STARTING_ANIMATIONS;
            updateWakeGestureListenerLp();
            updateOrientationListenerLp();
            updateLockScreenTimeout();
        }
        if (this.mKeyguardDelegate != null) {
            this.mKeyguardDelegate.onStartedWakingUp();
        }
    }

    public void finishedWakingUp() {
    }

    public boolean isStatusBarKeyguardShowing() {
        return (!isStatusBarKeyguard() || this.mHideLockScreen) ? false : SHOW_STARTING_ANIMATIONS;
    }

    private void wakeUpFromPowerKey(long eventTime) {
        Flog.i(NativeResponseCode.SERVICE_FOUND, "Wakeing Up From PowerKey...");
        if (Jlog.isPerfTest()) {
            Jlog.i(2201, "JL_PWRSCRON_PWM_GETMESSAGE");
        }
        wakeUp(eventTime, this.mAllowTheaterModeWakeFromPowerKey, "android.policy:POWER");
    }

    private boolean wakeUp(long wakeTime, boolean wakeInTheaterMode, String reason) {
        boolean theaterModeEnabled = isTheaterModeEnabled();
        Flog.i(NativeResponseCode.SERVICE_FOUND, "Wake Up wakeInTheaterMode=" + wakeInTheaterMode + ", theaterModeEnabled=" + theaterModeEnabled);
        if (!wakeInTheaterMode && theaterModeEnabled) {
            return false;
        }
        if (theaterModeEnabled) {
            Global.putInt(this.mContext.getContentResolver(), "theater_mode_on", 0);
        }
        this.mPowerManager.wakeUp(wakeTime, reason);
        return SHOW_STARTING_ANIMATIONS;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void finishKeyguardDrawn() {
        synchronized (this.mLock) {
            if (!this.mScreenOnEarly || this.mKeyguardDrawComplete) {
            } else {
                this.mKeyguardDrawComplete = SHOW_STARTING_ANIMATIONS;
                if (this.mKeyguardDelegate != null) {
                    this.mHandler.removeMessages(6);
                }
                this.mWindowManagerDrawComplete = false;
                Flog.i(NativeResponseCode.SERVICE_FOUND, "finishKeyguardDrawn -> waitForAllWindowsDrawn");
                this.mWindowManagerInternal.waitForAllWindowsDrawn(this.mWindowManagerDrawCallback, 1000);
            }
        }
    }

    public void screenTurnedOff() {
        Flog.i(305, "Screen turned off...");
        updateScreenOffSleepToken(SHOW_STARTING_ANIMATIONS);
        synchronized (this.mLock) {
            this.mScreenOnEarly = false;
            this.mScreenOnFully = false;
            this.mKeyguardDrawComplete = false;
            this.mWindowManagerDrawComplete = false;
            this.mScreenOnListener = null;
            updateOrientationListenerLp();
            if (this.mKeyguardDelegate != null) {
                this.mKeyguardDelegate.onScreenTurnedOff();
            }
            if (mSupportAod) {
                startAodService(3);
            }
        }
    }

    public void screenTurningOn(ScreenOnListener screenOnListener) {
        updateScreenOffSleepToken(false);
        synchronized (this.mLock) {
            if (mSupportAod) {
                startAodService(5);
            }
            this.mScreenOnEarly = SHOW_STARTING_ANIMATIONS;
            this.mScreenOnFully = false;
            this.mKeyguardDrawComplete = false;
            this.mWindowManagerDrawComplete = false;
            this.mScreenOnListener = screenOnListener;
            if (this.mKeyguardDelegate != null) {
                this.mHandler.removeMessages(6);
                long timeoutDelay = 1000;
                if (!this.mKeyguardDrawnOnce) {
                    timeoutDelay = JobStatus.DEFAULT_TRIGGER_UPDATE_DELAY;
                }
                this.mHandler.sendEmptyMessageDelayed(6, timeoutDelay);
                Flog.i(305, "Screen turned on for keyguard");
                this.mKeyguardDelegate.onScreenTurningOn(this.mKeyguardDrawnCallback);
            } else {
                Flog.i(NativeResponseCode.SERVICE_FOUND, " null mKeyguardDelegate: setting mKeyguardDrawComplete.");
                finishKeyguardDrawn();
            }
        }
    }

    public void screenTurnedOn() {
        synchronized (this.mLock) {
            if (this.mKeyguardDelegate != null) {
                this.mKeyguardDelegate.onScreenTurnedOn();
            }
            if (mSupportAod) {
                startAodService(2);
            }
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void finishWindowsDrawn() {
        synchronized (this.mLock) {
            if (!this.mScreenOnEarly || this.mWindowManagerDrawComplete) {
            } else {
                this.mWindowManagerDrawComplete = SHOW_STARTING_ANIMATIONS;
                finishScreenTurningOn();
            }
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void finishScreenTurningOn() {
        synchronized (this.mLock) {
            updateOrientationListenerLp();
        }
        synchronized (this.mLock) {
            if (this.mScreenOnFully || !this.mScreenOnEarly || !this.mWindowManagerDrawComplete || (this.mAwake && !this.mKeyguardDrawComplete)) {
            } else {
                Slog.i(TAG, "Finished screen turning on...");
                ScreenOnListener listener = this.mScreenOnListener;
                this.mScreenOnListener = null;
                this.mScreenOnFully = SHOW_STARTING_ANIMATIONS;
                boolean enableScreen;
                if (this.mKeyguardDrawnOnce || !this.mAwake) {
                    enableScreen = false;
                } else {
                    this.mKeyguardDrawnOnce = SHOW_STARTING_ANIMATIONS;
                    enableScreen = SHOW_STARTING_ANIMATIONS;
                    if (this.mBootMessageNeedsHiding) {
                        this.mBootMessageNeedsHiding = false;
                        hideBootMessages();
                    }
                }
            }
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void handleHideBootMessage() {
        synchronized (this.mLock) {
            if (!this.mKeyguardDrawnOnce) {
                this.mBootMessageNeedsHiding = SHOW_STARTING_ANIMATIONS;
            }
        }
    }

    public boolean isScreenOn() {
        return this.mScreenOnFully;
    }

    public void enableKeyguard(boolean enabled) {
        if (this.mKeyguardDelegate != null) {
            this.mKeyguardDelegate.setKeyguardEnabled(enabled);
        }
    }

    public void exitKeyguardSecurely(OnKeyguardExitResult callback) {
        if (this.mKeyguardDelegate != null) {
            this.mKeyguardDelegate.verifyUnlock(callback);
        }
    }

    private boolean isKeyguardShowingAndNotOccluded() {
        boolean z = false;
        if (this.mKeyguardDelegate == null) {
            return false;
        }
        if (this.mKeyguardDelegate.isShowing() && !this.mKeyguardOccluded) {
            z = SHOW_STARTING_ANIMATIONS;
        }
        return z;
    }

    protected boolean keyguardIsShowingTq() {
        return isKeyguardShowingAndNotOccluded();
    }

    public boolean isKeyguardLocked() {
        return keyguardOn();
    }

    public boolean isKeyguardSecure(int userId) {
        if (this.mKeyguardDelegate == null) {
            return false;
        }
        return this.mKeyguardDelegate.isSecure(userId);
    }

    public boolean isKeyguardShowingOrOccluded() {
        return this.mKeyguardDelegate == null ? false : this.mKeyguardDelegate.isShowing();
    }

    public boolean inKeyguardRestrictedKeyInputMode() {
        if (this.mKeyguardDelegate == null) {
            return false;
        }
        return this.mKeyguardDelegate.isInputRestricted();
    }

    public void dismissKeyguardLw() {
        if (this.mKeyguardDelegate != null && this.mKeyguardDelegate.isShowing()) {
            this.mHandler.post(new Runnable() {
                public void run() {
                    PhoneWindowManager.this.mKeyguardDelegate.dismiss();
                }
            });
        }
    }

    public void notifyActivityDrawnForKeyguardLw() {
        if (this.mKeyguardDelegate != null) {
            this.mHandler.post(new Runnable() {
                public void run() {
                    PhoneWindowManager.this.mKeyguardDelegate.onActivityDrawn();
                }
            });
        }
    }

    public boolean isKeyguardDrawnLw() {
        boolean z;
        synchronized (this.mLock) {
            z = this.mKeyguardDrawnOnce;
        }
        return z;
    }

    public void startKeyguardExitAnimation(long startTime, long fadeoutDuration) {
        if (this.mKeyguardDelegate != null) {
            this.mKeyguardDelegate.startKeyguardExitAnimation(startTime, fadeoutDuration);
        }
    }

    public void getStableInsetsLw(int displayRotation, int displayWidth, int displayHeight, Rect outInsets) {
        outInsets.setEmpty();
        getNonDecorInsetsLw(displayRotation, displayWidth, displayHeight, outInsets);
        if (this.mStatusBar != null) {
            outInsets.top = this.mStatusBarHeight;
        }
    }

    public void getNonDecorInsetsLw(int displayRotation, int displayWidth, int displayHeight, Rect outInsets) {
        outInsets.setEmpty();
        if (this.mNavigationBar == null) {
            return;
        }
        if (isNavigationBarOnBottom(displayWidth, displayHeight)) {
            outInsets.bottom = getNavigationBarHeight(displayRotation, this.mUiMode);
        } else {
            outInsets.right = getNavigationBarWidth(displayRotation, this.mUiMode);
        }
    }

    public boolean isNavBarForcedShownLw(WindowState windowState) {
        return this.mForceShowSystemBars;
    }

    public boolean isDockSideAllowed(int dockSide) {
        boolean z = SHOW_STARTING_ANIMATIONS;
        if (this.mNavigationBarCanMove) {
            if (!(dockSide == 2 || dockSide == 1)) {
                z = false;
            }
            return z;
        }
        if (!(dockSide == 2 || dockSide == 1 || dockSide == 3)) {
            z = false;
        }
        return z;
    }

    void sendCloseSystemWindows() {
        PhoneWindow.sendCloseSystemWindows(this.mContext, null);
    }

    void sendCloseSystemWindows(String reason) {
        PhoneWindow.sendCloseSystemWindows(this.mContext, reason);
    }

    public int rotationForOrientationLw(int orientation, int lastRotation) {
        Slog.i(TAG, "rotationForOrientationLw(orient=" + orientation + ", last=" + lastRotation + "); user=" + this.mUserRotation + " " + (this.mUserRotationMode == 1 ? "USER_ROTATION_LOCKED" : ""));
        int defaultRotation = SystemProperties.getInt("ro.panel.hw_orientation", 0) / 90;
        if (this.mForceDefaultOrientation) {
            return defaultRotation;
        }
        synchronized (this.mLock) {
            int preferredRotation;
            int i;
            int sensorRotation = this.mOrientationListener.getProposedRotation();
            if (sensorRotation < 0) {
                sensorRotation = lastRotation;
            }
            if (this.mLidState == 1 && this.mLidOpenRotation >= 0) {
                preferredRotation = this.mLidOpenRotation;
            } else if (this.mDockMode == 2 && (this.mCarDockEnablesAccelerometer || this.mCarDockRotation >= 0)) {
                preferredRotation = this.mCarDockEnablesAccelerometer ? sensorRotation : this.mCarDockRotation;
            } else if ((this.mDockMode == 1 || this.mDockMode == 3 || this.mDockMode == 4) && (this.mDeskDockEnablesAccelerometer || this.mDeskDockRotation >= 0)) {
                preferredRotation = this.mDeskDockEnablesAccelerometer ? sensorRotation : this.mDeskDockRotation;
            } else if (this.mHdmiPlugged && this.mDemoHdmiRotationLock) {
                preferredRotation = this.mDemoHdmiRotation;
            } else if (this.mHdmiPlugged && this.mDockMode == 0 && this.mUndockedHdmiRotation >= 0) {
                preferredRotation = this.mUndockedHdmiRotation;
            } else if (this.mDemoRotationLock) {
                preferredRotation = this.mDemoRotation;
            } else if (orientation == 14) {
                preferredRotation = lastRotation;
            } else if (!this.mSupportAutoRotation) {
                preferredRotation = -1;
            } else if ((this.mUserRotationMode == 0 && (orientation == 2 || orientation == -1 || orientation == 11 || orientation == 12 || orientation == 13)) || orientation == 4 || orientation == 10 || orientation == 6 || orientation == 7) {
                if (this.mAllowAllRotations < 0) {
                    if (this.mContext.getResources().getBoolean(17956920)) {
                        i = 1;
                    } else {
                        i = 0;
                    }
                    this.mAllowAllRotations = i;
                }
                preferredRotation = (sensorRotation != 2 || this.mAllowAllRotations == 1 || orientation == 10 || orientation == 13) ? sensorRotation : lastRotation;
            } else {
                preferredRotation = (this.mUserRotationMode != 1 || orientation == 5) ? -1 : this.mUserRotation;
            }
            switch (orientation) {
                case 0:
                    if (isLandscapeOrSeascape(preferredRotation)) {
                        return preferredRotation;
                    }
                    i = this.mLandscapeRotation;
                    return i;
                case 1:
                    if (isAnyPortrait(preferredRotation)) {
                        return preferredRotation;
                    }
                    i = this.mPortraitRotation;
                    return i;
                case 6:
                case 11:
                    if (isLandscapeOrSeascape(preferredRotation)) {
                        return preferredRotation;
                    } else if (isLandscapeOrSeascape(lastRotation)) {
                        return lastRotation;
                    } else {
                        i = this.mLandscapeRotation;
                        return i;
                    }
                case 7:
                case 12:
                    if (isAnyPortrait(preferredRotation)) {
                        return preferredRotation;
                    } else if (isAnyPortrait(lastRotation)) {
                        return lastRotation;
                    } else {
                        i = this.mPortraitRotation;
                        return i;
                    }
                case 8:
                    if (isLandscapeOrSeascape(preferredRotation)) {
                        return preferredRotation;
                    }
                    i = this.mSeascapeRotation;
                    return i;
                case 9:
                    if (isAnyPortrait(preferredRotation)) {
                        return preferredRotation;
                    }
                    i = this.mUpsideDownRotation;
                    return i;
                default:
                    if (preferredRotation >= 0) {
                        return preferredRotation;
                    }
                    return defaultRotation;
            }
        }
    }

    public boolean rotationHasCompatibleMetricsLw(int orientation, int rotation) {
        switch (orientation) {
            case 0:
            case 6:
            case 8:
                return isLandscapeOrSeascape(rotation);
            case 1:
            case 7:
            case 9:
                return isAnyPortrait(rotation);
            default:
                return SHOW_STARTING_ANIMATIONS;
        }
    }

    public void setRotationLw(int rotation) {
        this.mOrientationListener.setCurrentRotation(rotation);
    }

    private boolean isLandscapeOrSeascape(int rotation) {
        return (rotation == this.mLandscapeRotation || rotation == this.mSeascapeRotation) ? SHOW_STARTING_ANIMATIONS : false;
    }

    private boolean isAnyPortrait(int rotation) {
        return (rotation == this.mPortraitRotation || rotation == this.mUpsideDownRotation) ? SHOW_STARTING_ANIMATIONS : false;
    }

    public int getUserRotationMode() {
        if (System.getIntForUser(this.mContext.getContentResolver(), "accelerometer_rotation", 0, -2) != 0) {
            return 0;
        }
        return 1;
    }

    public void setUserRotationMode(int mode, int rot) {
        ContentResolver res = this.mContext.getContentResolver();
        if (mode == 1) {
            System.putIntForUser(res, "user_rotation", rot, -2);
            System.putIntForUser(res, "accelerometer_rotation", 0, -2);
            return;
        }
        System.putIntForUser(res, "accelerometer_rotation", 1, -2);
    }

    public void setSafeMode(boolean safeMode) {
        int i;
        this.mSafeMode = safeMode;
        if (safeMode) {
            i = 10001;
        } else {
            i = 10000;
        }
        performHapticFeedbackLw(null, i, SHOW_STARTING_ANIMATIONS);
    }

    static long[] getLongIntArray(Resources r, int resid) {
        int[] ar = r.getIntArray(resid);
        if (ar == null) {
            return null;
        }
        long[] out = new long[ar.length];
        for (int i = 0; i < ar.length; i++) {
            out[i] = (long) ar[i];
        }
        return out;
    }

    public void systemReady() {
        this.mKeyguardDelegate = new KeyguardServiceDelegate(this.mContext);
        this.mKeyguardDelegate.onSystemReady();
        readCameraLensCoverState();
        updateUiMode();
        synchronized (this.mLock) {
            updateOrientationListenerLp();
            this.mSystemReady = SHOW_STARTING_ANIMATIONS;
            this.mHandler.post(new Runnable() {
                public void run() {
                    PhoneWindowManager.this.updateSettings();
                }
            });
            boolean bindKeyguardNow = this.mDeferBindKeyguard;
            if (bindKeyguardNow) {
                this.mDeferBindKeyguard = false;
            }
        }
        if (bindKeyguardNow) {
            this.mKeyguardDelegate.bindService(this.mContext);
            this.mKeyguardDelegate.onBootCompleted();
        } else {
            this.mSysUiMonitor = new SystemUiMonitor();
            this.mSysUiMonitor.onSystemReady();
        }
        this.mSystemGestures.systemReady();
        this.mImmersiveModeConfirmation.systemReady();
    }

    public void systemBooted() {
        if (this.mSysUiMonitor != null) {
            this.mSysUiMonitor.onSystemBooted();
        }
        boolean bindKeyguardNow = false;
        synchronized (this.mLock) {
            if (this.mKeyguardDelegate != null) {
                bindKeyguardNow = this.mKeyguardBound ? false : SHOW_STARTING_ANIMATIONS;
            } else {
                this.mDeferBindKeyguard = SHOW_STARTING_ANIMATIONS;
            }
        }
        if (bindKeyguardNow) {
            this.mKeyguardDelegate.bindService(this.mContext);
            this.mKeyguardDelegate.onBootCompleted();
        }
        synchronized (this.mLock) {
            this.mSystemBooted = SHOW_STARTING_ANIMATIONS;
        }
        startedWakingUp();
        screenTurningOn(null);
        screenTurnedOn();
        this.mSysUiMonitor = null;
    }

    public void showBootMessage(final CharSequence msg, boolean always) {
        final String[] s = msg.toString().split(":");
        if (s[0].equals("HOTA") && s.length == 3) {
            this.mHandler.post(new Runnable() {
                public void run() {
                    int curr = 0;
                    int total = 0;
                    try {
                        curr = Integer.parseInt(s[1]);
                        total = Integer.parseInt(s[2]);
                    } catch (NumberFormatException e) {
                        Log.e(PhoneWindowManager.TAG, "showBootMessage->NumberFormatException happened");
                    }
                    HwPolicyFactory.showBootMessage(PhoneWindowManager.this.mContext, curr, total);
                }
            });
            this.ifBootMessageShowing = SHOW_STARTING_ANIMATIONS;
        } else if (!this.ifBootMessageShowing) {
            this.mHandler.post(new Runnable() {
                public void run() {
                    if (PhoneWindowManager.this.mBootMsgDialog == null) {
                        int theme;
                        if (PhoneWindowManager.this.mHasFeatureWatch) {
                            theme = 16975067;
                        } else if (PhoneWindowManager.this.mContext.getPackageManager().hasSystemFeature("android.hardware.type.television")) {
                            theme = 16975012;
                        } else {
                            theme = 0;
                        }
                        PhoneWindowManager.this.mBootMsgDialog = new ProgressDialog(PhoneWindowManager.this.mContext, theme) {
                            public boolean dispatchKeyEvent(KeyEvent event) {
                                return PhoneWindowManager.SHOW_STARTING_ANIMATIONS;
                            }

                            public boolean dispatchKeyShortcutEvent(KeyEvent event) {
                                return PhoneWindowManager.SHOW_STARTING_ANIMATIONS;
                            }

                            public boolean dispatchTouchEvent(MotionEvent ev) {
                                return PhoneWindowManager.SHOW_STARTING_ANIMATIONS;
                            }

                            public boolean dispatchTrackballEvent(MotionEvent ev) {
                                return PhoneWindowManager.SHOW_STARTING_ANIMATIONS;
                            }

                            public boolean dispatchGenericMotionEvent(MotionEvent ev) {
                                return PhoneWindowManager.SHOW_STARTING_ANIMATIONS;
                            }

                            public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
                                return PhoneWindowManager.SHOW_STARTING_ANIMATIONS;
                            }
                        };
                        if (PhoneWindowManager.this.mContext.getPackageManager().isUpgrade()) {
                            PhoneWindowManager.this.mBootMsgDialog.setTitle(17040286);
                        } else {
                            PhoneWindowManager.this.mBootMsgDialog.setTitle(17040287);
                        }
                        PhoneWindowManager.this.mBootMsgDialog.setProgressStyle(0);
                        PhoneWindowManager.this.mBootMsgDialog.setIndeterminate(PhoneWindowManager.SHOW_STARTING_ANIMATIONS);
                        PhoneWindowManager.this.mBootMsgDialog.getWindow().setType(2021);
                        PhoneWindowManager.this.mBootMsgDialog.getWindow().addFlags(258);
                        PhoneWindowManager.this.mBootMsgDialog.getWindow().setDimAmount(1.0f);
                        LayoutParams lp = PhoneWindowManager.this.mBootMsgDialog.getWindow().getAttributes();
                        lp.screenOrientation = 5;
                        PhoneWindowManager.this.mBootMsgDialog.getWindow().setAttributes(lp);
                        PhoneWindowManager.this.mBootMsgDialog.setCancelable(false);
                        PhoneWindowManager.this.mBootMsgDialog.show();
                    }
                    PhoneWindowManager.this.mBootMsgDialog.setMessage(msg);
                }
            });
        }
    }

    public void hideBootMessages() {
        this.mHandler.sendEmptyMessage(11);
    }

    public void userActivity() {
        synchronized (this.mScreenLockTimeout) {
            if (this.mLockScreenTimerActive) {
                this.mHandler.removeCallbacks(this.mScreenLockTimeout);
                this.mHandler.postDelayed(this.mScreenLockTimeout, (long) this.mLockScreenTimeout);
            }
        }
    }

    public void lockNow(Bundle options) {
        this.mContext.enforceCallingOrSelfPermission("android.permission.DEVICE_POWER", null);
        this.mHandler.removeCallbacks(this.mScreenLockTimeout);
        if (options != null) {
            this.mScreenLockTimeout.setLockOptions(options);
        }
        this.mHandler.post(this.mScreenLockTimeout);
    }

    private void updateLockScreenTimeout() {
        synchronized (this.mScreenLockTimeout) {
            boolean isSecure;
            if (this.mAllowLockscreenWhenOn && this.mAwake && this.mKeyguardDelegate != null) {
                isSecure = this.mKeyguardDelegate.isSecure(this.mCurrentUserId);
            } else {
                isSecure = false;
            }
            if (this.mLockScreenTimerActive != isSecure) {
                if (isSecure) {
                    this.mHandler.removeCallbacks(this.mScreenLockTimeout);
                    this.mHandler.postDelayed(this.mScreenLockTimeout, (long) this.mLockScreenTimeout);
                } else {
                    this.mHandler.removeCallbacks(this.mScreenLockTimeout);
                }
                this.mLockScreenTimerActive = isSecure;
            }
        }
    }

    private void updateDreamingSleepToken(boolean acquire) {
        if (acquire) {
            if (this.mDreamingSleepToken == null) {
                this.mDreamingSleepToken = this.mActivityManagerInternal.acquireSleepToken("Dream");
            }
        } else if (this.mDreamingSleepToken != null) {
            this.mDreamingSleepToken.release();
            this.mDreamingSleepToken = null;
        }
    }

    private void updateScreenOffSleepToken(boolean acquire) {
        if (acquire) {
            if (this.mScreenOffSleepToken == null) {
                this.mScreenOffSleepToken = this.mActivityManagerInternal.acquireSleepToken("ScreenOff");
            }
        } else if (this.mScreenOffSleepToken != null) {
            this.mScreenOffSleepToken.release();
            this.mScreenOffSleepToken = null;
        }
    }

    public void enableScreenAfterBoot() {
        readLidState();
        applyLidSwitchState();
    }

    private void applyLidSwitchState() {
        if (this.mLidState == 0 && this.mLidControlsSleep) {
            this.mPowerManager.goToSleep(SystemClock.uptimeMillis(), 3, 1);
        } else if (this.mLidState == 0 && this.mLidControlsScreenLock) {
            this.mWindowManagerFuncs.lockDeviceNow();
        }
        synchronized (this.mLock) {
            updateWakeGestureListenerLp();
        }
    }

    void updateUiMode() {
        if (this.mUiModeManager == null) {
            this.mUiModeManager = Stub.asInterface(ServiceManager.getService("uimode"));
        }
        try {
            this.mUiMode = this.mUiModeManager.getCurrentModeType();
        } catch (RemoteException e) {
        }
    }

    void updateRotation(boolean alwaysSendConfiguration) {
        try {
            this.mWindowManager.updateRotation(alwaysSendConfiguration, false);
        } catch (RemoteException e) {
        }
    }

    void updateRotation(boolean alwaysSendConfiguration, boolean forceRelayout) {
        try {
            this.mWindowManager.updateRotation(alwaysSendConfiguration, forceRelayout);
        } catch (RemoteException e) {
        }
    }

    Intent createHomeDockIntent() {
        Intent intent;
        if (this.mUiMode == 3) {
            if (this.mEnableCarDockHomeCapture) {
                intent = this.mCarDockIntent;
            }
            intent = null;
        } else {
            if (this.mUiMode != 2) {
                if (this.mUiMode == 6 && (this.mDockMode == 1 || this.mDockMode == 4 || this.mDockMode == 3)) {
                    intent = this.mDeskDockIntent;
                } else {
                    intent = null;
                }
            }
            intent = null;
        }
        if (intent == null) {
            return null;
        }
        ActivityInfo ai = null;
        ResolveInfo info = this.mContext.getPackageManager().resolveActivityAsUser(intent, 65664, this.mCurrentUserId);
        if (info != null) {
            ai = info.activityInfo;
        }
        if (ai == null || ai.metaData == null || !ai.metaData.getBoolean("android.dock_home")) {
            return null;
        }
        Intent intent2 = new Intent(intent);
        intent2.setClassName(ai.packageName, ai.name);
        return intent2;
    }

    void startDockOrHome(boolean fromHomeKey, boolean awakenFromDreams) {
        Intent intent;
        if (awakenFromDreams) {
            awakenDreams();
        }
        Intent dock = createHomeDockIntent();
        if (dock != null) {
            if (fromHomeKey) {
                try {
                    dock.putExtra("android.intent.extra.FROM_HOME_KEY", fromHomeKey);
                } catch (ActivityNotFoundException e) {
                }
            }
            startActivityAsUser(dock, UserHandle.CURRENT);
            return;
        }
        if (fromHomeKey) {
            intent = new Intent(this.mHomeIntent);
            intent.putExtra("android.intent.extra.FROM_HOME_KEY", fromHomeKey);
        } else {
            intent = this.mHomeIntent;
        }
        startActivityAsUser(intent, UserHandle.CURRENT);
    }

    boolean goHome() {
        if (isUserSetupComplete()) {
            try {
                if (SystemProperties.getInt("persist.sys.uts-test-mode", 0) == 1) {
                    Log.d(TAG, "UTS-TEST-MODE");
                } else {
                    ActivityManagerNative.getDefault().stopAppSwitches();
                    sendCloseSystemWindows();
                    Intent dock = createHomeDockIntent();
                    if (dock != null && ActivityManagerNative.getDefault().startActivityAsUser(null, null, dock, dock.resolveTypeIfNeeded(this.mContext.getContentResolver()), null, null, 0, 1, null, null, -2) == 1) {
                        return false;
                    }
                }
                if (ActivityManagerNative.getDefault().startActivityAsUser(null, null, this.mHomeIntent, this.mHomeIntent.resolveTypeIfNeeded(this.mContext.getContentResolver()), null, null, 0, 1, null, null, -2) == 1) {
                    return false;
                }
            } catch (RemoteException e) {
            }
            return SHOW_STARTING_ANIMATIONS;
        }
        Slog.i(TAG, "Not going home because user setup is in progress.");
        return false;
    }

    public void setCurrentOrientationLw(int newOrientation) {
        synchronized (this.mLock) {
            if (newOrientation != this.mCurrentAppOrientation) {
                this.mCurrentAppOrientation = newOrientation;
                updateOrientationListenerLp();
            }
        }
    }

    private void performAuditoryFeedbackForAccessibilityIfNeed() {
        if (isGlobalAccessibilityGestureEnabled() && !((AudioManager) this.mContext.getSystemService("audio")).isSilentMode()) {
            Ringtone ringTone = RingtoneManager.getRingtone(this.mContext, System.DEFAULT_NOTIFICATION_URI);
            ringTone.setStreamType(3);
            ringTone.play();
        }
    }

    private boolean isTheaterModeEnabled() {
        return Global.getInt(this.mContext.getContentResolver(), "theater_mode_on", 0) == 1 ? SHOW_STARTING_ANIMATIONS : false;
    }

    private boolean isGlobalAccessibilityGestureEnabled() {
        return Global.getInt(this.mContext.getContentResolver(), "enable_accessibility_global_gesture_enabled", 0) == 1 ? SHOW_STARTING_ANIMATIONS : false;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public boolean performHapticFeedbackLw(WindowState win, int effectId, boolean always) {
        if (this.mVibrator == null) {
            this.mVibrator = (Vibrator) this.mContext.getSystemService("vibrator");
        }
        if (this.mVibrator == null || this.mKeyguardDelegate == null || !this.mVibrator.hasVibrator()) {
            return false;
        }
        if ((System.getIntForUser(this.mContext.getContentResolver(), "haptic_feedback_enabled", 0, -2) == 0 ? SHOW_STARTING_ANIMATIONS : false) && !always) {
            return false;
        }
        long[] pattern;
        int owningUid;
        String owningPackage;
        switch (effectId) {
            case 0:
                pattern = this.mLongPressVibePattern;
                break;
            case 1:
                pattern = this.mVirtualKeyVibePattern;
                break;
            case 3:
                pattern = this.mKeyboardTapVibePattern;
                break;
            case 4:
                pattern = this.mClockTickVibePattern;
                break;
            case 5:
                pattern = this.mCalendarDateVibePattern;
                break;
            case 6:
                pattern = this.mContextClickVibePattern;
                break;
            case 10000:
                pattern = this.mSafeModeDisabledVibePattern;
                break;
            case 10001:
                pattern = this.mSafeModeEnabledVibePattern;
                break;
            default:
                return false;
        }
        if (win != null) {
            owningUid = win.getOwningUid();
            owningPackage = win.getOwningPackage();
        } else {
            owningUid = Process.myUid();
            owningPackage = this.mContext.getOpPackageName();
        }
        if (pattern == null) {
            return false;
        }
        if (pattern.length == 1) {
            this.mVibrator.vibrate(owningUid, owningPackage, pattern[0], VIBRATION_ATTRIBUTES);
        } else {
            this.mVibrator.vibrate(owningUid, owningPackage, pattern, -1, VIBRATION_ATTRIBUTES);
        }
        return SHOW_STARTING_ANIMATIONS;
    }

    public void keepScreenOnStartedLw() {
    }

    public void keepScreenOnStoppedLw() {
        if (isKeyguardShowingAndNotOccluded()) {
            this.mPowerManager.userActivity(SystemClock.uptimeMillis(), false);
        }
    }

    public void updateSystemUiColorLw(WindowState win) {
    }

    private int updateLightStatusBarLw(int vis, WindowState opaque, WindowState opaqueOrDimming) {
        WindowState statusColorWin;
        if (!isStatusBarKeyguard() || this.mHideLockScreen) {
            statusColorWin = opaqueOrDimming;
        } else {
            statusColorWin = this.mStatusBar;
        }
        if (statusColorWin == null) {
            return vis;
        }
        if (statusColorWin == opaque) {
            return (vis & -8193) | (PolicyControl.getSystemUiVisibility(statusColorWin, null) & DumpState.DUMP_PREFERRED_XML);
        }
        if (statusColorWin == null || !statusColorWin.isDimming()) {
            return vis;
        }
        return vis & -8193;
    }

    private boolean drawsSystemBarBackground(WindowState win) {
        return (win == null || (win.getAttrs().flags & Integer.MIN_VALUE) != 0) ? SHOW_STARTING_ANIMATIONS : false;
    }

    private boolean forcesDrawStatusBarBackground(WindowState win) {
        return (win == null || (win.getAttrs().privateFlags & DumpState.DUMP_INTENT_FILTER_VERIFIERS) != 0) ? SHOW_STARTING_ANIMATIONS : false;
    }

    private int configureNavBarOpacity(int visibility, boolean dockedStackVisible, boolean freeformStackVisible, boolean isDockedDividerResizing) {
        if (this.mNavBarOpacityMode == 0) {
            if (dockedStackVisible || freeformStackVisible || isDockedDividerResizing) {
                visibility = setNavBarOpaqueFlag(visibility);
            }
        } else if (this.mNavBarOpacityMode == 1) {
            if (isDockedDividerResizing) {
                visibility = setNavBarOpaqueFlag(visibility);
            } else if (freeformStackVisible) {
                visibility = setNavBarTranslucentFlag(visibility);
            } else {
                visibility = setNavBarOpaqueFlag(visibility);
            }
        }
        if (areTranslucentBarsAllowed()) {
            return visibility;
        }
        return visibility & Integer.MAX_VALUE;
    }

    private int setNavBarOpaqueFlag(int visibility) {
        return visibility & 2147450879;
    }

    private int setNavBarTranslucentFlag(int visibility) {
        return (visibility & -32769) | Integer.MIN_VALUE;
    }

    private void clearClearableFlagsLw() {
        int newVal = this.mResettingSystemUiFlags | 7;
        if (newVal != this.mResettingSystemUiFlags) {
            this.mResettingSystemUiFlags = newVal;
            this.mWindowManagerFuncs.reevaluateStatusBarVisibility();
        }
    }

    private boolean isImmersiveMode(int vis) {
        if (this.mNavigationBar == null || (vis & 2) == 0 || (vis & 6144) == 0) {
            return false;
        }
        return canHideNavigationBar();
    }

    private boolean areTranslucentBarsAllowed() {
        return this.mTranslucentDecorEnabled;
    }

    public boolean hasNavigationBar() {
        return this.mHasNavigationBar;
    }

    public void setLastInputMethodWindowLw(WindowState ime, WindowState target) {
        this.mLastInputMethodWindow = ime;
        this.mLastInputMethodTargetWindow = target;
    }

    public int getInputMethodWindowVisibleHeightLw() {
        return this.mDockBottom - this.mCurBottom;
    }

    public void setCurrentUserLw(int newUserId) {
        this.mCurrentUserId = newUserId;
        if (this.mKeyguardDelegate != null) {
            this.mKeyguardDelegate.setCurrentUser(newUserId);
        }
        StatusBarManagerInternal statusBar = getStatusBarManagerInternal();
        if (statusBar != null) {
            statusBar.setCurrentUser(newUserId);
        }
        setLastInputMethodWindowLw(null, null);
    }

    public boolean canMagnifyWindow(int windowType) {
        switch (windowType) {
            case 2011:
            case 2012:
            case 2019:
            case 2027:
                return false;
            default:
                return SHOW_STARTING_ANIMATIONS;
        }
    }

    public boolean isTopLevelWindow(int windowType) {
        boolean z = SHOW_STARTING_ANIMATIONS;
        if (windowType < 1000 || windowType > 1999) {
            return SHOW_STARTING_ANIMATIONS;
        }
        if (windowType != 1003) {
            z = false;
        }
        return z;
    }

    public void dump(String prefix, PrintWriter pw, String[] args) {
        pw.print(prefix);
        pw.print("mSafeMode=");
        pw.print(this.mSafeMode);
        pw.print(" mSystemReady=");
        pw.print(this.mSystemReady);
        pw.print(" mSystemBooted=");
        pw.println(this.mSystemBooted);
        pw.print(prefix);
        pw.print("mLidState=");
        pw.print(this.mLidState);
        pw.print(" mLidOpenRotation=");
        pw.print(this.mLidOpenRotation);
        pw.print(" mCameraLensCoverState=");
        pw.print(this.mCameraLensCoverState);
        pw.print(" mHdmiPlugged=");
        pw.println(this.mHdmiPlugged);
        if (this.mLastSystemUiFlags == 0 && this.mResettingSystemUiFlags == 0) {
            if (this.mForceClearedSystemUiFlags != 0) {
            }
            if (this.mLastFocusNeedsMenu) {
                pw.print(prefix);
                pw.print("mLastFocusNeedsMenu=");
                pw.println(this.mLastFocusNeedsMenu);
            }
            pw.print(prefix);
            pw.print("mWakeGestureEnabledSetting=");
            pw.println(this.mWakeGestureEnabledSetting);
            pw.print(prefix);
            pw.print("mSupportAutoRotation=");
            pw.println(this.mSupportAutoRotation);
            pw.print(prefix);
            pw.print("mUiMode=");
            pw.print(this.mUiMode);
            pw.print(" mDockMode=");
            pw.print(this.mDockMode);
            pw.print(" mEnableCarDockHomeCapture=");
            pw.print(this.mEnableCarDockHomeCapture);
            pw.print(" mCarDockRotation=");
            pw.print(this.mCarDockRotation);
            pw.print(" mDeskDockRotation=");
            pw.println(this.mDeskDockRotation);
            pw.print(prefix);
            pw.print("mUserRotationMode=");
            pw.print(this.mUserRotationMode);
            pw.print(" mUserRotation=");
            pw.print(this.mUserRotation);
            pw.print(" mAllowAllRotations=");
            pw.println(this.mAllowAllRotations);
            pw.print(prefix);
            pw.print("mCurrentAppOrientation=");
            pw.println(this.mCurrentAppOrientation);
            pw.print(prefix);
            pw.print("mCarDockEnablesAccelerometer=");
            pw.print(this.mCarDockEnablesAccelerometer);
            pw.print(" mDeskDockEnablesAccelerometer=");
            pw.println(this.mDeskDockEnablesAccelerometer);
            pw.print(prefix);
            pw.print("mLidKeyboardAccessibility=");
            pw.print(this.mLidKeyboardAccessibility);
            pw.print(" mLidNavigationAccessibility=");
            pw.print(this.mLidNavigationAccessibility);
            pw.print(" mLidControlsScreenLock=");
            pw.println(this.mLidControlsScreenLock);
            pw.print(" mLidControlsSleep=");
            pw.println(this.mLidControlsSleep);
            pw.print(prefix);
            pw.print(" mLongPressOnBackBehavior=");
            pw.println(this.mLongPressOnBackBehavior);
            pw.print(prefix);
            pw.print("mShortPressOnPowerBehavior=");
            pw.print(this.mShortPressOnPowerBehavior);
            pw.print(" mLongPressOnPowerBehavior=");
            pw.println(this.mLongPressOnPowerBehavior);
            pw.print(prefix);
            pw.print("mDoublePressOnPowerBehavior=");
            pw.print(this.mDoublePressOnPowerBehavior);
            pw.print(" mTriplePressOnPowerBehavior=");
            pw.println(this.mTriplePressOnPowerBehavior);
            pw.print(prefix);
            pw.print("mHasSoftInput=");
            pw.println(this.mHasSoftInput);
            pw.print(prefix);
            pw.print("mAwake=");
            pw.println(this.mAwake);
            pw.print(prefix);
            pw.print("mScreenOnEarly=");
            pw.print(this.mScreenOnEarly);
            pw.print(" mScreenOnFully=");
            pw.println(this.mScreenOnFully);
            pw.print(prefix);
            pw.print("mKeyguardDrawComplete=");
            pw.print(this.mKeyguardDrawComplete);
            pw.print(" mWindowManagerDrawComplete=");
            pw.println(this.mWindowManagerDrawComplete);
            pw.print(prefix);
            pw.print("mOrientationSensorEnabled=");
            pw.println(this.mOrientationSensorEnabled);
            pw.print(prefix);
            pw.print("mOverscanScreen=(");
            pw.print(this.mOverscanScreenLeft);
            pw.print(",");
            pw.print(this.mOverscanScreenTop);
            pw.print(") ");
            pw.print(this.mOverscanScreenWidth);
            pw.print("x");
            pw.println(this.mOverscanScreenHeight);
            if (this.mOverscanLeft == 0 && this.mOverscanTop == 0 && this.mOverscanRight == 0) {
                if (this.mOverscanBottom != 0) {
                }
                pw.print(prefix);
                pw.print("mRestrictedOverscanScreen=(");
                pw.print(this.mRestrictedOverscanScreenLeft);
                pw.print(",");
                pw.print(this.mRestrictedOverscanScreenTop);
                pw.print(") ");
                pw.print(this.mRestrictedOverscanScreenWidth);
                pw.print("x");
                pw.println(this.mRestrictedOverscanScreenHeight);
                pw.print(prefix);
                pw.print("mUnrestrictedScreen=(");
                pw.print(this.mUnrestrictedScreenLeft);
                pw.print(",");
                pw.print(this.mUnrestrictedScreenTop);
                pw.print(") ");
                pw.print(this.mUnrestrictedScreenWidth);
                pw.print("x");
                pw.println(this.mUnrestrictedScreenHeight);
                pw.print(prefix);
                pw.print("mRestrictedScreen=(");
                pw.print(this.mRestrictedScreenLeft);
                pw.print(",");
                pw.print(this.mRestrictedScreenTop);
                pw.print(") ");
                pw.print(this.mRestrictedScreenWidth);
                pw.print("x");
                pw.println(this.mRestrictedScreenHeight);
                pw.print(prefix);
                pw.print("mStableFullscreen=(");
                pw.print(this.mStableFullscreenLeft);
                pw.print(",");
                pw.print(this.mStableFullscreenTop);
                pw.print(")-(");
                pw.print(this.mStableFullscreenRight);
                pw.print(",");
                pw.print(this.mStableFullscreenBottom);
                pw.println(")");
                pw.print(prefix);
                pw.print("mStable=(");
                pw.print(this.mStableLeft);
                pw.print(",");
                pw.print(this.mStableTop);
                pw.print(")-(");
                pw.print(this.mStableRight);
                pw.print(",");
                pw.print(this.mStableBottom);
                pw.println(")");
                pw.print(prefix);
                pw.print("mSystem=(");
                pw.print(this.mSystemLeft);
                pw.print(",");
                pw.print(this.mSystemTop);
                pw.print(")-(");
                pw.print(this.mSystemRight);
                pw.print(",");
                pw.print(this.mSystemBottom);
                pw.println(")");
                pw.print(prefix);
                pw.print("mCur=(");
                pw.print(this.mCurLeft);
                pw.print(",");
                pw.print(this.mCurTop);
                pw.print(")-(");
                pw.print(this.mCurRight);
                pw.print(",");
                pw.print(this.mCurBottom);
                pw.println(")");
                pw.print(prefix);
                pw.print("mContent=(");
                pw.print(this.mContentLeft);
                pw.print(",");
                pw.print(this.mContentTop);
                pw.print(")-(");
                pw.print(this.mContentRight);
                pw.print(",");
                pw.print(this.mContentBottom);
                pw.println(")");
                pw.print(prefix);
                pw.print("mVoiceContent=(");
                pw.print(this.mVoiceContentLeft);
                pw.print(",");
                pw.print(this.mVoiceContentTop);
                pw.print(")-(");
                pw.print(this.mVoiceContentRight);
                pw.print(",");
                pw.print(this.mVoiceContentBottom);
                pw.println(")");
                pw.print(prefix);
                pw.print("mDock=(");
                pw.print(this.mDockLeft);
                pw.print(",");
                pw.print(this.mDockTop);
                pw.print(")-(");
                pw.print(this.mDockRight);
                pw.print(",");
                pw.print(this.mDockBottom);
                pw.println(")");
                pw.print(prefix);
                pw.print("mDockLayer=");
                pw.print(this.mDockLayer);
                pw.print(" mStatusBarLayer=");
                pw.println(this.mStatusBarLayer);
                pw.print(prefix);
                pw.print("mShowingLockscreen=");
                pw.print(this.mShowingLockscreen);
                pw.print(" mShowingDream=");
                pw.print(this.mShowingDream);
                pw.print(" mDreamingLockscreen=");
                pw.print(this.mDreamingLockscreen);
                pw.print(" mDreamingSleepToken=");
                pw.println(this.mDreamingSleepToken);
                if (this.mLastInputMethodWindow != null) {
                    pw.print(prefix);
                    pw.print("mLastInputMethodWindow=");
                    pw.println(this.mLastInputMethodWindow);
                }
                if (this.mLastInputMethodTargetWindow != null) {
                    pw.print(prefix);
                    pw.print("mLastInputMethodTargetWindow=");
                    pw.println(this.mLastInputMethodTargetWindow);
                }
                if (this.mStatusBar != null) {
                    pw.print(prefix);
                    pw.print("mStatusBar=");
                    pw.print(this.mStatusBar);
                    pw.print(" isStatusBarKeyguard=");
                    pw.println(isStatusBarKeyguard());
                }
                if (this.mNavigationBar != null) {
                    pw.print(prefix);
                    pw.print("mNavigationBar=");
                    pw.println(this.mNavigationBar);
                }
                if (this.mFocusedWindow != null) {
                    pw.print(prefix);
                    pw.print("mFocusedWindow=");
                    pw.println(this.mFocusedWindow);
                }
                if (this.mFocusedApp != null) {
                    pw.print(prefix);
                    pw.print("mFocusedApp=");
                    pw.println(this.mFocusedApp);
                }
                if (this.mWinDismissingKeyguard != null) {
                    pw.print(prefix);
                    pw.print("mWinDismissingKeyguard=");
                    pw.println(this.mWinDismissingKeyguard);
                }
                if (this.mTopFullscreenOpaqueWindowState != null) {
                    pw.print(prefix);
                    pw.print("mTopFullscreenOpaqueWindowState=");
                    pw.println(this.mTopFullscreenOpaqueWindowState);
                }
                if (this.mTopFullscreenOpaqueOrDimmingWindowState != null) {
                    pw.print(prefix);
                    pw.print("mTopFullscreenOpaqueOrDimmingWindowState=");
                    pw.println(this.mTopFullscreenOpaqueOrDimmingWindowState);
                }
                if (this.mForcingShowNavBar) {
                    pw.print(prefix);
                    pw.print("mForcingShowNavBar=");
                    pw.println(this.mForcingShowNavBar);
                    pw.print("mForcingShowNavBarLayer=");
                    pw.println(this.mForcingShowNavBarLayer);
                }
                pw.print(prefix);
                pw.print("mTopIsFullscreen=");
                pw.print(this.mTopIsFullscreen);
                pw.print(" mHideLockScreen=");
                pw.println(this.mHideLockScreen);
                pw.print(prefix);
                pw.print("mForceStatusBar=");
                pw.print(this.mForceStatusBar);
                pw.print(" mForceStatusBarFromKeyguard=");
                pw.println(this.mForceStatusBarFromKeyguard);
                pw.print(prefix);
                pw.print("mDismissKeyguard=");
                pw.print(this.mDismissKeyguard);
                pw.print(" mWinDismissingKeyguard=");
                pw.print(this.mWinDismissingKeyguard);
                pw.print(" mHomePressed=");
                pw.println(this.mHomePressed);
                pw.print(prefix);
                pw.print("mAllowLockscreenWhenOn=");
                pw.print(this.mAllowLockscreenWhenOn);
                pw.print(" mLockScreenTimeout=");
                pw.print(this.mLockScreenTimeout);
                pw.print(" mLockScreenTimerActive=");
                pw.println(this.mLockScreenTimerActive);
                pw.print(prefix);
                pw.print("mEndcallBehavior=");
                pw.print(this.mEndcallBehavior);
                pw.print(" mIncallPowerBehavior=");
                pw.print(this.mIncallPowerBehavior);
                pw.print(" mLongPressOnHomeBehavior=");
                pw.println(this.mLongPressOnHomeBehavior);
                pw.print(prefix);
                pw.print("mLandscapeRotation=");
                pw.print(this.mLandscapeRotation);
                pw.print(" mSeascapeRotation=");
                pw.println(this.mSeascapeRotation);
                pw.print(prefix);
                pw.print("mPortraitRotation=");
                pw.print(this.mPortraitRotation);
                pw.print(" mUpsideDownRotation=");
                pw.println(this.mUpsideDownRotation);
                pw.print(prefix);
                pw.print("mDemoHdmiRotation=");
                pw.print(this.mDemoHdmiRotation);
                pw.print(" mDemoHdmiRotationLock=");
                pw.println(this.mDemoHdmiRotationLock);
                pw.print(prefix);
                pw.print("mUndockedHdmiRotation=");
                pw.println(this.mUndockedHdmiRotation);
                this.mGlobalKeyManager.dump(prefix, pw);
                this.mStatusBarController.dump(pw, prefix);
                this.mNavigationBarController.dump(pw, prefix);
                PolicyControl.dump(prefix, pw);
                if (this.mWakeGestureListener != null) {
                    this.mWakeGestureListener.dump(pw, prefix);
                }
                if (this.mOrientationListener != null) {
                    this.mOrientationListener.dump(pw, prefix);
                }
                if (this.mBurnInProtectionHelper != null) {
                    this.mBurnInProtectionHelper.dump(prefix, pw);
                }
                if (this.mKeyguardDelegate == null) {
                    this.mKeyguardDelegate.dump(prefix, pw);
                }
            }
            pw.print(prefix);
            pw.print("mOverscan left=");
            pw.print(this.mOverscanLeft);
            pw.print(" top=");
            pw.print(this.mOverscanTop);
            pw.print(" right=");
            pw.print(this.mOverscanRight);
            pw.print(" bottom=");
            pw.println(this.mOverscanBottom);
            pw.print(prefix);
            pw.print("mRestrictedOverscanScreen=(");
            pw.print(this.mRestrictedOverscanScreenLeft);
            pw.print(",");
            pw.print(this.mRestrictedOverscanScreenTop);
            pw.print(") ");
            pw.print(this.mRestrictedOverscanScreenWidth);
            pw.print("x");
            pw.println(this.mRestrictedOverscanScreenHeight);
            pw.print(prefix);
            pw.print("mUnrestrictedScreen=(");
            pw.print(this.mUnrestrictedScreenLeft);
            pw.print(",");
            pw.print(this.mUnrestrictedScreenTop);
            pw.print(") ");
            pw.print(this.mUnrestrictedScreenWidth);
            pw.print("x");
            pw.println(this.mUnrestrictedScreenHeight);
            pw.print(prefix);
            pw.print("mRestrictedScreen=(");
            pw.print(this.mRestrictedScreenLeft);
            pw.print(",");
            pw.print(this.mRestrictedScreenTop);
            pw.print(") ");
            pw.print(this.mRestrictedScreenWidth);
            pw.print("x");
            pw.println(this.mRestrictedScreenHeight);
            pw.print(prefix);
            pw.print("mStableFullscreen=(");
            pw.print(this.mStableFullscreenLeft);
            pw.print(",");
            pw.print(this.mStableFullscreenTop);
            pw.print(")-(");
            pw.print(this.mStableFullscreenRight);
            pw.print(",");
            pw.print(this.mStableFullscreenBottom);
            pw.println(")");
            pw.print(prefix);
            pw.print("mStable=(");
            pw.print(this.mStableLeft);
            pw.print(",");
            pw.print(this.mStableTop);
            pw.print(")-(");
            pw.print(this.mStableRight);
            pw.print(",");
            pw.print(this.mStableBottom);
            pw.println(")");
            pw.print(prefix);
            pw.print("mSystem=(");
            pw.print(this.mSystemLeft);
            pw.print(",");
            pw.print(this.mSystemTop);
            pw.print(")-(");
            pw.print(this.mSystemRight);
            pw.print(",");
            pw.print(this.mSystemBottom);
            pw.println(")");
            pw.print(prefix);
            pw.print("mCur=(");
            pw.print(this.mCurLeft);
            pw.print(",");
            pw.print(this.mCurTop);
            pw.print(")-(");
            pw.print(this.mCurRight);
            pw.print(",");
            pw.print(this.mCurBottom);
            pw.println(")");
            pw.print(prefix);
            pw.print("mContent=(");
            pw.print(this.mContentLeft);
            pw.print(",");
            pw.print(this.mContentTop);
            pw.print(")-(");
            pw.print(this.mContentRight);
            pw.print(",");
            pw.print(this.mContentBottom);
            pw.println(")");
            pw.print(prefix);
            pw.print("mVoiceContent=(");
            pw.print(this.mVoiceContentLeft);
            pw.print(",");
            pw.print(this.mVoiceContentTop);
            pw.print(")-(");
            pw.print(this.mVoiceContentRight);
            pw.print(",");
            pw.print(this.mVoiceContentBottom);
            pw.println(")");
            pw.print(prefix);
            pw.print("mDock=(");
            pw.print(this.mDockLeft);
            pw.print(",");
            pw.print(this.mDockTop);
            pw.print(")-(");
            pw.print(this.mDockRight);
            pw.print(",");
            pw.print(this.mDockBottom);
            pw.println(")");
            pw.print(prefix);
            pw.print("mDockLayer=");
            pw.print(this.mDockLayer);
            pw.print(" mStatusBarLayer=");
            pw.println(this.mStatusBarLayer);
            pw.print(prefix);
            pw.print("mShowingLockscreen=");
            pw.print(this.mShowingLockscreen);
            pw.print(" mShowingDream=");
            pw.print(this.mShowingDream);
            pw.print(" mDreamingLockscreen=");
            pw.print(this.mDreamingLockscreen);
            pw.print(" mDreamingSleepToken=");
            pw.println(this.mDreamingSleepToken);
            if (this.mLastInputMethodWindow != null) {
                pw.print(prefix);
                pw.print("mLastInputMethodWindow=");
                pw.println(this.mLastInputMethodWindow);
            }
            if (this.mLastInputMethodTargetWindow != null) {
                pw.print(prefix);
                pw.print("mLastInputMethodTargetWindow=");
                pw.println(this.mLastInputMethodTargetWindow);
            }
            if (this.mStatusBar != null) {
                pw.print(prefix);
                pw.print("mStatusBar=");
                pw.print(this.mStatusBar);
                pw.print(" isStatusBarKeyguard=");
                pw.println(isStatusBarKeyguard());
            }
            if (this.mNavigationBar != null) {
                pw.print(prefix);
                pw.print("mNavigationBar=");
                pw.println(this.mNavigationBar);
            }
            if (this.mFocusedWindow != null) {
                pw.print(prefix);
                pw.print("mFocusedWindow=");
                pw.println(this.mFocusedWindow);
            }
            if (this.mFocusedApp != null) {
                pw.print(prefix);
                pw.print("mFocusedApp=");
                pw.println(this.mFocusedApp);
            }
            if (this.mWinDismissingKeyguard != null) {
                pw.print(prefix);
                pw.print("mWinDismissingKeyguard=");
                pw.println(this.mWinDismissingKeyguard);
            }
            if (this.mTopFullscreenOpaqueWindowState != null) {
                pw.print(prefix);
                pw.print("mTopFullscreenOpaqueWindowState=");
                pw.println(this.mTopFullscreenOpaqueWindowState);
            }
            if (this.mTopFullscreenOpaqueOrDimmingWindowState != null) {
                pw.print(prefix);
                pw.print("mTopFullscreenOpaqueOrDimmingWindowState=");
                pw.println(this.mTopFullscreenOpaqueOrDimmingWindowState);
            }
            if (this.mForcingShowNavBar) {
                pw.print(prefix);
                pw.print("mForcingShowNavBar=");
                pw.println(this.mForcingShowNavBar);
                pw.print("mForcingShowNavBarLayer=");
                pw.println(this.mForcingShowNavBarLayer);
            }
            pw.print(prefix);
            pw.print("mTopIsFullscreen=");
            pw.print(this.mTopIsFullscreen);
            pw.print(" mHideLockScreen=");
            pw.println(this.mHideLockScreen);
            pw.print(prefix);
            pw.print("mForceStatusBar=");
            pw.print(this.mForceStatusBar);
            pw.print(" mForceStatusBarFromKeyguard=");
            pw.println(this.mForceStatusBarFromKeyguard);
            pw.print(prefix);
            pw.print("mDismissKeyguard=");
            pw.print(this.mDismissKeyguard);
            pw.print(" mWinDismissingKeyguard=");
            pw.print(this.mWinDismissingKeyguard);
            pw.print(" mHomePressed=");
            pw.println(this.mHomePressed);
            pw.print(prefix);
            pw.print("mAllowLockscreenWhenOn=");
            pw.print(this.mAllowLockscreenWhenOn);
            pw.print(" mLockScreenTimeout=");
            pw.print(this.mLockScreenTimeout);
            pw.print(" mLockScreenTimerActive=");
            pw.println(this.mLockScreenTimerActive);
            pw.print(prefix);
            pw.print("mEndcallBehavior=");
            pw.print(this.mEndcallBehavior);
            pw.print(" mIncallPowerBehavior=");
            pw.print(this.mIncallPowerBehavior);
            pw.print(" mLongPressOnHomeBehavior=");
            pw.println(this.mLongPressOnHomeBehavior);
            pw.print(prefix);
            pw.print("mLandscapeRotation=");
            pw.print(this.mLandscapeRotation);
            pw.print(" mSeascapeRotation=");
            pw.println(this.mSeascapeRotation);
            pw.print(prefix);
            pw.print("mPortraitRotation=");
            pw.print(this.mPortraitRotation);
            pw.print(" mUpsideDownRotation=");
            pw.println(this.mUpsideDownRotation);
            pw.print(prefix);
            pw.print("mDemoHdmiRotation=");
            pw.print(this.mDemoHdmiRotation);
            pw.print(" mDemoHdmiRotationLock=");
            pw.println(this.mDemoHdmiRotationLock);
            pw.print(prefix);
            pw.print("mUndockedHdmiRotation=");
            pw.println(this.mUndockedHdmiRotation);
            this.mGlobalKeyManager.dump(prefix, pw);
            this.mStatusBarController.dump(pw, prefix);
            this.mNavigationBarController.dump(pw, prefix);
            PolicyControl.dump(prefix, pw);
            if (this.mWakeGestureListener != null) {
                this.mWakeGestureListener.dump(pw, prefix);
            }
            if (this.mOrientationListener != null) {
                this.mOrientationListener.dump(pw, prefix);
            }
            if (this.mBurnInProtectionHelper != null) {
                this.mBurnInProtectionHelper.dump(prefix, pw);
            }
            if (this.mKeyguardDelegate == null) {
                this.mKeyguardDelegate.dump(prefix, pw);
            }
        }
        pw.print(prefix);
        pw.print("mLastSystemUiFlags=0x");
        pw.print(Integer.toHexString(this.mLastSystemUiFlags));
        pw.print(" mResettingSystemUiFlags=0x");
        pw.print(Integer.toHexString(this.mResettingSystemUiFlags));
        pw.print(" mForceClearedSystemUiFlags=0x");
        pw.println(Integer.toHexString(this.mForceClearedSystemUiFlags));
        if (this.mLastFocusNeedsMenu) {
            pw.print(prefix);
            pw.print("mLastFocusNeedsMenu=");
            pw.println(this.mLastFocusNeedsMenu);
        }
        pw.print(prefix);
        pw.print("mWakeGestureEnabledSetting=");
        pw.println(this.mWakeGestureEnabledSetting);
        pw.print(prefix);
        pw.print("mSupportAutoRotation=");
        pw.println(this.mSupportAutoRotation);
        pw.print(prefix);
        pw.print("mUiMode=");
        pw.print(this.mUiMode);
        pw.print(" mDockMode=");
        pw.print(this.mDockMode);
        pw.print(" mEnableCarDockHomeCapture=");
        pw.print(this.mEnableCarDockHomeCapture);
        pw.print(" mCarDockRotation=");
        pw.print(this.mCarDockRotation);
        pw.print(" mDeskDockRotation=");
        pw.println(this.mDeskDockRotation);
        pw.print(prefix);
        pw.print("mUserRotationMode=");
        pw.print(this.mUserRotationMode);
        pw.print(" mUserRotation=");
        pw.print(this.mUserRotation);
        pw.print(" mAllowAllRotations=");
        pw.println(this.mAllowAllRotations);
        pw.print(prefix);
        pw.print("mCurrentAppOrientation=");
        pw.println(this.mCurrentAppOrientation);
        pw.print(prefix);
        pw.print("mCarDockEnablesAccelerometer=");
        pw.print(this.mCarDockEnablesAccelerometer);
        pw.print(" mDeskDockEnablesAccelerometer=");
        pw.println(this.mDeskDockEnablesAccelerometer);
        pw.print(prefix);
        pw.print("mLidKeyboardAccessibility=");
        pw.print(this.mLidKeyboardAccessibility);
        pw.print(" mLidNavigationAccessibility=");
        pw.print(this.mLidNavigationAccessibility);
        pw.print(" mLidControlsScreenLock=");
        pw.println(this.mLidControlsScreenLock);
        pw.print(" mLidControlsSleep=");
        pw.println(this.mLidControlsSleep);
        pw.print(prefix);
        pw.print(" mLongPressOnBackBehavior=");
        pw.println(this.mLongPressOnBackBehavior);
        pw.print(prefix);
        pw.print("mShortPressOnPowerBehavior=");
        pw.print(this.mShortPressOnPowerBehavior);
        pw.print(" mLongPressOnPowerBehavior=");
        pw.println(this.mLongPressOnPowerBehavior);
        pw.print(prefix);
        pw.print("mDoublePressOnPowerBehavior=");
        pw.print(this.mDoublePressOnPowerBehavior);
        pw.print(" mTriplePressOnPowerBehavior=");
        pw.println(this.mTriplePressOnPowerBehavior);
        pw.print(prefix);
        pw.print("mHasSoftInput=");
        pw.println(this.mHasSoftInput);
        pw.print(prefix);
        pw.print("mAwake=");
        pw.println(this.mAwake);
        pw.print(prefix);
        pw.print("mScreenOnEarly=");
        pw.print(this.mScreenOnEarly);
        pw.print(" mScreenOnFully=");
        pw.println(this.mScreenOnFully);
        pw.print(prefix);
        pw.print("mKeyguardDrawComplete=");
        pw.print(this.mKeyguardDrawComplete);
        pw.print(" mWindowManagerDrawComplete=");
        pw.println(this.mWindowManagerDrawComplete);
        pw.print(prefix);
        pw.print("mOrientationSensorEnabled=");
        pw.println(this.mOrientationSensorEnabled);
        pw.print(prefix);
        pw.print("mOverscanScreen=(");
        pw.print(this.mOverscanScreenLeft);
        pw.print(",");
        pw.print(this.mOverscanScreenTop);
        pw.print(") ");
        pw.print(this.mOverscanScreenWidth);
        pw.print("x");
        pw.println(this.mOverscanScreenHeight);
        if (this.mOverscanBottom != 0) {
            pw.print(prefix);
            pw.print("mOverscan left=");
            pw.print(this.mOverscanLeft);
            pw.print(" top=");
            pw.print(this.mOverscanTop);
            pw.print(" right=");
            pw.print(this.mOverscanRight);
            pw.print(" bottom=");
            pw.println(this.mOverscanBottom);
        }
        pw.print(prefix);
        pw.print("mRestrictedOverscanScreen=(");
        pw.print(this.mRestrictedOverscanScreenLeft);
        pw.print(",");
        pw.print(this.mRestrictedOverscanScreenTop);
        pw.print(") ");
        pw.print(this.mRestrictedOverscanScreenWidth);
        pw.print("x");
        pw.println(this.mRestrictedOverscanScreenHeight);
        pw.print(prefix);
        pw.print("mUnrestrictedScreen=(");
        pw.print(this.mUnrestrictedScreenLeft);
        pw.print(",");
        pw.print(this.mUnrestrictedScreenTop);
        pw.print(") ");
        pw.print(this.mUnrestrictedScreenWidth);
        pw.print("x");
        pw.println(this.mUnrestrictedScreenHeight);
        pw.print(prefix);
        pw.print("mRestrictedScreen=(");
        pw.print(this.mRestrictedScreenLeft);
        pw.print(",");
        pw.print(this.mRestrictedScreenTop);
        pw.print(") ");
        pw.print(this.mRestrictedScreenWidth);
        pw.print("x");
        pw.println(this.mRestrictedScreenHeight);
        pw.print(prefix);
        pw.print("mStableFullscreen=(");
        pw.print(this.mStableFullscreenLeft);
        pw.print(",");
        pw.print(this.mStableFullscreenTop);
        pw.print(")-(");
        pw.print(this.mStableFullscreenRight);
        pw.print(",");
        pw.print(this.mStableFullscreenBottom);
        pw.println(")");
        pw.print(prefix);
        pw.print("mStable=(");
        pw.print(this.mStableLeft);
        pw.print(",");
        pw.print(this.mStableTop);
        pw.print(")-(");
        pw.print(this.mStableRight);
        pw.print(",");
        pw.print(this.mStableBottom);
        pw.println(")");
        pw.print(prefix);
        pw.print("mSystem=(");
        pw.print(this.mSystemLeft);
        pw.print(",");
        pw.print(this.mSystemTop);
        pw.print(")-(");
        pw.print(this.mSystemRight);
        pw.print(",");
        pw.print(this.mSystemBottom);
        pw.println(")");
        pw.print(prefix);
        pw.print("mCur=(");
        pw.print(this.mCurLeft);
        pw.print(",");
        pw.print(this.mCurTop);
        pw.print(")-(");
        pw.print(this.mCurRight);
        pw.print(",");
        pw.print(this.mCurBottom);
        pw.println(")");
        pw.print(prefix);
        pw.print("mContent=(");
        pw.print(this.mContentLeft);
        pw.print(",");
        pw.print(this.mContentTop);
        pw.print(")-(");
        pw.print(this.mContentRight);
        pw.print(",");
        pw.print(this.mContentBottom);
        pw.println(")");
        pw.print(prefix);
        pw.print("mVoiceContent=(");
        pw.print(this.mVoiceContentLeft);
        pw.print(",");
        pw.print(this.mVoiceContentTop);
        pw.print(")-(");
        pw.print(this.mVoiceContentRight);
        pw.print(",");
        pw.print(this.mVoiceContentBottom);
        pw.println(")");
        pw.print(prefix);
        pw.print("mDock=(");
        pw.print(this.mDockLeft);
        pw.print(",");
        pw.print(this.mDockTop);
        pw.print(")-(");
        pw.print(this.mDockRight);
        pw.print(",");
        pw.print(this.mDockBottom);
        pw.println(")");
        pw.print(prefix);
        pw.print("mDockLayer=");
        pw.print(this.mDockLayer);
        pw.print(" mStatusBarLayer=");
        pw.println(this.mStatusBarLayer);
        pw.print(prefix);
        pw.print("mShowingLockscreen=");
        pw.print(this.mShowingLockscreen);
        pw.print(" mShowingDream=");
        pw.print(this.mShowingDream);
        pw.print(" mDreamingLockscreen=");
        pw.print(this.mDreamingLockscreen);
        pw.print(" mDreamingSleepToken=");
        pw.println(this.mDreamingSleepToken);
        if (this.mLastInputMethodWindow != null) {
            pw.print(prefix);
            pw.print("mLastInputMethodWindow=");
            pw.println(this.mLastInputMethodWindow);
        }
        if (this.mLastInputMethodTargetWindow != null) {
            pw.print(prefix);
            pw.print("mLastInputMethodTargetWindow=");
            pw.println(this.mLastInputMethodTargetWindow);
        }
        if (this.mStatusBar != null) {
            pw.print(prefix);
            pw.print("mStatusBar=");
            pw.print(this.mStatusBar);
            pw.print(" isStatusBarKeyguard=");
            pw.println(isStatusBarKeyguard());
        }
        if (this.mNavigationBar != null) {
            pw.print(prefix);
            pw.print("mNavigationBar=");
            pw.println(this.mNavigationBar);
        }
        if (this.mFocusedWindow != null) {
            pw.print(prefix);
            pw.print("mFocusedWindow=");
            pw.println(this.mFocusedWindow);
        }
        if (this.mFocusedApp != null) {
            pw.print(prefix);
            pw.print("mFocusedApp=");
            pw.println(this.mFocusedApp);
        }
        if (this.mWinDismissingKeyguard != null) {
            pw.print(prefix);
            pw.print("mWinDismissingKeyguard=");
            pw.println(this.mWinDismissingKeyguard);
        }
        if (this.mTopFullscreenOpaqueWindowState != null) {
            pw.print(prefix);
            pw.print("mTopFullscreenOpaqueWindowState=");
            pw.println(this.mTopFullscreenOpaqueWindowState);
        }
        if (this.mTopFullscreenOpaqueOrDimmingWindowState != null) {
            pw.print(prefix);
            pw.print("mTopFullscreenOpaqueOrDimmingWindowState=");
            pw.println(this.mTopFullscreenOpaqueOrDimmingWindowState);
        }
        if (this.mForcingShowNavBar) {
            pw.print(prefix);
            pw.print("mForcingShowNavBar=");
            pw.println(this.mForcingShowNavBar);
            pw.print("mForcingShowNavBarLayer=");
            pw.println(this.mForcingShowNavBarLayer);
        }
        pw.print(prefix);
        pw.print("mTopIsFullscreen=");
        pw.print(this.mTopIsFullscreen);
        pw.print(" mHideLockScreen=");
        pw.println(this.mHideLockScreen);
        pw.print(prefix);
        pw.print("mForceStatusBar=");
        pw.print(this.mForceStatusBar);
        pw.print(" mForceStatusBarFromKeyguard=");
        pw.println(this.mForceStatusBarFromKeyguard);
        pw.print(prefix);
        pw.print("mDismissKeyguard=");
        pw.print(this.mDismissKeyguard);
        pw.print(" mWinDismissingKeyguard=");
        pw.print(this.mWinDismissingKeyguard);
        pw.print(" mHomePressed=");
        pw.println(this.mHomePressed);
        pw.print(prefix);
        pw.print("mAllowLockscreenWhenOn=");
        pw.print(this.mAllowLockscreenWhenOn);
        pw.print(" mLockScreenTimeout=");
        pw.print(this.mLockScreenTimeout);
        pw.print(" mLockScreenTimerActive=");
        pw.println(this.mLockScreenTimerActive);
        pw.print(prefix);
        pw.print("mEndcallBehavior=");
        pw.print(this.mEndcallBehavior);
        pw.print(" mIncallPowerBehavior=");
        pw.print(this.mIncallPowerBehavior);
        pw.print(" mLongPressOnHomeBehavior=");
        pw.println(this.mLongPressOnHomeBehavior);
        pw.print(prefix);
        pw.print("mLandscapeRotation=");
        pw.print(this.mLandscapeRotation);
        pw.print(" mSeascapeRotation=");
        pw.println(this.mSeascapeRotation);
        pw.print(prefix);
        pw.print("mPortraitRotation=");
        pw.print(this.mPortraitRotation);
        pw.print(" mUpsideDownRotation=");
        pw.println(this.mUpsideDownRotation);
        pw.print(prefix);
        pw.print("mDemoHdmiRotation=");
        pw.print(this.mDemoHdmiRotation);
        pw.print(" mDemoHdmiRotationLock=");
        pw.println(this.mDemoHdmiRotationLock);
        pw.print(prefix);
        pw.print("mUndockedHdmiRotation=");
        pw.println(this.mUndockedHdmiRotation);
        this.mGlobalKeyManager.dump(prefix, pw);
        this.mStatusBarController.dump(pw, prefix);
        this.mNavigationBarController.dump(pw, prefix);
        PolicyControl.dump(prefix, pw);
        if (this.mWakeGestureListener != null) {
            this.mWakeGestureListener.dump(pw, prefix);
        }
        if (this.mOrientationListener != null) {
            this.mOrientationListener.dump(pw, prefix);
        }
        if (this.mBurnInProtectionHelper != null) {
            this.mBurnInProtectionHelper.dump(prefix, pw);
        }
        if (this.mKeyguardDelegate == null) {
            this.mKeyguardDelegate.dump(prefix, pw);
        }
    }

    protected void cancelPendingPowerKeyActionForDistouch() {
        cancelPendingPowerKeyAction();
    }

    public boolean isTopIsFullscreen() {
        return this.mTopIsFullscreen;
    }

    public boolean isLastImmersiveMode() {
        return isImmersiveMode(this.mLastSystemUiFlags);
    }

    protected BarController getStatusBarController() {
        return this.mStatusBarController;
    }

    protected ImmersiveModeConfirmation getImmersiveModeConfirmation() {
        return this.mImmersiveModeConfirmation;
    }

    protected void updateHwSystemUiVisibilityLw() {
        updateSystemUiVisibilityLw();
    }

    protected void requestHwTransientBars(WindowState swipeTarget) {
        requestTransientBars(swipeTarget);
    }

    protected WindowState getCurrentWin() {
        if (this.mFocusedWindow != null) {
            return this.mFocusedWindow;
        }
        return this.mTopFullscreenOpaqueWindowState;
    }

    protected void hwInit() {
    }

    protected int getEmuiStyleValue(int styleValue) {
        return styleValue;
    }

    protected boolean getFloatingValue(int styleValue) {
        return false;
    }

    public boolean startAodService(int startState) {
        Slog.i(TAG, "AOD startAodService mAodSwitch=" + this.mAodSwitch + " mAODState=" + this.mAODState + " startState=" + startState);
        if (!mSupportAod) {
            return false;
        }
        if (this.mAodSwitch == 0) {
            this.mAODState = startState;
            return false;
        } else if (this.mAODState == startState) {
            return false;
        } else {
            this.mAODState = startState;
            this.mHandler.post(new Runnable() {
                public void run() {
                    Intent intent = null;
                    switch (PhoneWindowManager.this.mAODState) {
                        case 1:
                            intent = new Intent("com.huawei.aod.action.AOD_SERVICE_START");
                            break;
                        case 2:
                            if (PhoneWindowManager.this.mIAodStateCallback == null) {
                                intent = new Intent("com.huawei.aod.action.AOD_SCREEN_ON");
                                break;
                            }
                            try {
                                PhoneWindowManager.this.mIAodStateCallback.onScreenOn();
                                break;
                            } catch (RemoteException e) {
                                break;
                            }
                        case 3:
                            if (PhoneWindowManager.this.mIAodStateCallback == null) {
                                intent = new Intent("com.huawei.aod.action.AOD_SCREEN_OFF");
                                break;
                            }
                            try {
                                PhoneWindowManager.this.mIAodStateCallback.onScreenOff();
                                break;
                            } catch (RemoteException e2) {
                                break;
                            }
                        case 4:
                            if (PhoneWindowManager.this.mIAodStateCallback != null) {
                                try {
                                    PhoneWindowManager.this.mIAodStateCallback.onWakingUp();
                                    break;
                                } catch (RemoteException e3) {
                                    break;
                                }
                            }
                            break;
                        case 5:
                            if (PhoneWindowManager.this.mIAodStateCallback != null) {
                                try {
                                    PhoneWindowManager.this.mIAodStateCallback.onTurningOn();
                                    break;
                                } catch (RemoteException e4) {
                                    break;
                                }
                            }
                            break;
                        case 6:
                            intent = new Intent("com.huawei.aod.action.AOD_SCREEN_OFF");
                            break;
                    }
                    if (intent != null) {
                        intent.setComponent(new ComponentName("com.huawei.aod", "com.huawei.aod.AODService"));
                        PhoneWindowManager.this.mContext.startService(intent);
                    }
                }
            });
            return SHOW_STARTING_ANIMATIONS;
        }
    }

    public void regeditAodStateCallback(IAodStateCallback callback) {
        Slog.i(TAG, "AOD regeditAodStateCallback ");
        if (mSupportAod) {
            this.mIAodStateCallback = callback;
            try {
                callback.asBinder().linkToDeath(new AppDeathRecipient(), 0);
            } catch (RemoteException e) {
            }
        }
    }

    public void unregeditAodStateCallback(IAodStateCallback callback) {
        Slog.i(TAG, "AOD unregeditAodStateCallback ");
        if (mSupportAod) {
            this.mIAodStateCallback = null;
        }
    }
}
