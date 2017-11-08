package com.android.server.accessibility;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription.MotionEventGenerator;
import android.accessibilityservice.IAccessibilityServiceClient;
import android.accessibilityservice.IAccessibilityServiceConnection;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.PendingIntent;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ParceledListSlice;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.UserInfo;
import android.database.ContentObserver;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.Region.Op;
import android.hardware.display.DisplayManager;
import android.hardware.input.InputManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings.Secure;
import android.text.TextUtils;
import android.text.TextUtils.SimpleStringSplitter;
import android.util.Slog;
import android.util.SparseArray;
import android.view.Display;
import android.view.IWindow;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MagnificationSpec;
import android.view.MotionEvent;
import android.view.WindowInfo;
import android.view.WindowManager.LayoutParams;
import android.view.WindowManagerInternal;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityInteractionClient;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.accessibility.IAccessibilityInteractionConnection;
import android.view.accessibility.IAccessibilityInteractionConnectionCallback;
import android.view.accessibility.IAccessibilityManager.Stub;
import android.view.accessibility.IAccessibilityManagerClient;
import com.android.internal.content.PackageMonitor;
import com.android.internal.os.SomeArgs;
import com.android.server.LocalServices;
import com.android.server.power.IHwShutdownThread;
import com.android.server.statusbar.StatusBarManagerInternal;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class AccessibilityManagerService extends Stub {
    private static final char COMPONENT_NAME_SEPARATOR = ':';
    private static final boolean DEBUG = false;
    private static final String FUNCTION_DUMP = "dump";
    private static final String FUNCTION_REGISTER_UI_TEST_AUTOMATION_SERVICE = "registerUiTestAutomationService";
    private static final String GET_WINDOW_TOKEN = "getWindowToken";
    private static final String LOG_TAG = "AccessibilityManagerService";
    public static final int MAGNIFICATION_GESTURE_HANDLER_ID = 0;
    private static final int OWN_PROCESS_ID = Process.myPid();
    private static final String TEMPORARY_ENABLE_ACCESSIBILITY_UNTIL_KEYGUARD_REMOVED = "temporaryEnableAccessibilityStateUntilKeyguardRemoved";
    private static final int WAIT_FOR_USER_STATE_FULLY_INITIALIZED_MILLIS = 3000;
    private static final int WAIT_MOTION_INJECTOR_TIMEOUT_MILLIS = 1000;
    private static final int WAIT_WINDOWS_TIMEOUT_MILLIS = 5000;
    private static final int WINDOW_ID_UNKNOWN = -1;
    private static final ComponentName sFakeAccessibilityServiceComponentName = new ComponentName("foo.bar", "FakeService");
    private static int sIdCounter = 1;
    private static int sNextWindowId;
    private final Context mContext;
    private int mCurrentUserId = 0;
    private AlertDialog mEnableTouchExplorationDialog;
    private final List<AccessibilityServiceInfo> mEnabledServicesForFeedbackTempList = new ArrayList();
    private final RemoteCallbackList<IAccessibilityManagerClient> mGlobalClients = new RemoteCallbackList();
    private final SparseArray<AccessibilityConnectionWrapper> mGlobalInteractionConnections = new SparseArray();
    private final SparseArray<IBinder> mGlobalWindowTokens = new SparseArray();
    private boolean mHasInputFilter;
    private boolean mInitialized;
    private AccessibilityInputFilter mInputFilter;
    private InteractionBridge mInteractionBridge;
    private KeyEventDispatcher mKeyEventDispatcher;
    private final Object mLock = new Object();
    private MagnificationController mMagnificationController;
    private final MainHandler mMainHandler;
    private MotionEventInjector mMotionEventInjector;
    private final PackageManager mPackageManager;
    private final PowerManager mPowerManager;
    private final SecurityPolicy mSecurityPolicy;
    private final SimpleStringSplitter mStringColonSplitter = new SimpleStringSplitter(COMPONENT_NAME_SEPARATOR);
    private final List<AccessibilityServiceInfo> mTempAccessibilityServiceInfoList = new ArrayList();
    private final Set<ComponentName> mTempComponentNameSet = new HashSet();
    private final Point mTempPoint = new Point();
    private final Rect mTempRect = new Rect();
    private final Rect mTempRect1 = new Rect();
    private final UserManager mUserManager;
    private final SparseArray<UserState> mUserStates = new SparseArray();
    private final WindowManagerInternal mWindowManagerService;
    private WindowsForAccessibilityCallback mWindowsForAccessibilityCallback;

    private class AccessibilityConnectionWrapper implements DeathRecipient {
        private final IAccessibilityInteractionConnection mConnection;
        private final int mUserId;
        private final int mWindowId;

        public AccessibilityConnectionWrapper(int windowId, IAccessibilityInteractionConnection connection, int userId) {
            this.mWindowId = windowId;
            this.mUserId = userId;
            this.mConnection = connection;
        }

        public void linkToDeath() throws RemoteException {
            this.mConnection.asBinder().linkToDeath(this, 0);
        }

        public void unlinkToDeath() {
            this.mConnection.asBinder().unlinkToDeath(this, 0);
        }

        public void binderDied() {
            unlinkToDeath();
            synchronized (AccessibilityManagerService.this.mLock) {
                AccessibilityManagerService.this.removeAccessibilityInteractionConnectionLocked(this.mWindowId, this.mUserId);
            }
        }
    }

    private final class AccessibilityContentObserver extends ContentObserver {
        private final Uri mAccessibilitySoftKeyboardModeUri = Secure.getUriFor("accessibility_soft_keyboard_mode");
        private final Uri mAutoclickEnabledUri = Secure.getUriFor("accessibility_autoclick_enabled");
        private final Uri mDisplayColorMatrixUri = Secure.getUriFor("accessibility_display_color_matrix");
        private final Uri mDisplayDaltonizerEnabledUri = Secure.getUriFor("accessibility_display_daltonizer_enabled");
        private final Uri mDisplayDaltonizerUri = Secure.getUriFor("accessibility_display_daltonizer");
        private final Uri mDisplayInversionEnabledUri = Secure.getUriFor("accessibility_display_inversion_enabled");
        private final Uri mDisplayMagnificationEnabledUri = Secure.getUriFor("accessibility_display_magnification_enabled");
        private final Uri mEnabledAccessibilityServicesUri = Secure.getUriFor("enabled_accessibility_services");
        private final Uri mEnhancedWebAccessibilityUri = Secure.getUriFor("accessibility_script_injection");
        private final Uri mHighTextContrastUri = Secure.getUriFor("high_text_contrast_enabled");
        private final Uri mTouchExplorationEnabledUri = Secure.getUriFor("touch_exploration_enabled");
        private final Uri mTouchExplorationGrantedAccessibilityServicesUri = Secure.getUriFor("touch_exploration_granted_accessibility_services");

        public AccessibilityContentObserver(Handler handler) {
            super(handler);
        }

        public void register(ContentResolver contentResolver) {
            contentResolver.registerContentObserver(this.mTouchExplorationEnabledUri, false, this, -1);
            contentResolver.registerContentObserver(this.mDisplayMagnificationEnabledUri, false, this, -1);
            contentResolver.registerContentObserver(this.mAutoclickEnabledUri, false, this, -1);
            contentResolver.registerContentObserver(this.mEnabledAccessibilityServicesUri, false, this, -1);
            contentResolver.registerContentObserver(this.mTouchExplorationGrantedAccessibilityServicesUri, false, this, -1);
            contentResolver.registerContentObserver(this.mEnhancedWebAccessibilityUri, false, this, -1);
            contentResolver.registerContentObserver(this.mDisplayInversionEnabledUri, false, this, -1);
            contentResolver.registerContentObserver(this.mDisplayDaltonizerEnabledUri, false, this, -1);
            contentResolver.registerContentObserver(this.mDisplayDaltonizerUri, false, this, -1);
            contentResolver.registerContentObserver(this.mDisplayColorMatrixUri, false, this, -1);
            contentResolver.registerContentObserver(this.mHighTextContrastUri, false, this, -1);
            contentResolver.registerContentObserver(this.mAccessibilitySoftKeyboardModeUri, false, this, -1);
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public void onChange(boolean selfChange, Uri uri) {
            synchronized (AccessibilityManagerService.this.mLock) {
                UserState userState = AccessibilityManagerService.this.getCurrentUserStateLocked();
                if (userState.isUiAutomationSuppressingOtherServices()) {
                } else if (this.mTouchExplorationEnabledUri.equals(uri)) {
                    if (AccessibilityManagerService.this.readTouchExplorationEnabledSettingLocked(userState)) {
                        AccessibilityManagerService.this.onUserStateChangedLocked(userState);
                    }
                } else if (this.mDisplayMagnificationEnabledUri.equals(uri)) {
                    if (AccessibilityManagerService.this.readDisplayMagnificationEnabledSettingLocked(userState)) {
                        AccessibilityManagerService.this.onUserStateChangedLocked(userState);
                    }
                } else if (this.mAutoclickEnabledUri.equals(uri)) {
                    if (AccessibilityManagerService.this.readAutoclickEnabledSettingLocked(userState)) {
                        AccessibilityManagerService.this.onUserStateChangedLocked(userState);
                    }
                } else if (this.mEnabledAccessibilityServicesUri.equals(uri)) {
                    if (AccessibilityManagerService.this.readEnabledAccessibilityServicesLocked(userState)) {
                        AccessibilityManagerService.this.onUserStateChangedLocked(userState);
                    }
                } else if (this.mTouchExplorationGrantedAccessibilityServicesUri.equals(uri)) {
                    if (AccessibilityManagerService.this.readTouchExplorationGrantedAccessibilityServicesLocked(userState)) {
                        AccessibilityManagerService.this.onUserStateChangedLocked(userState);
                    }
                } else if (this.mEnhancedWebAccessibilityUri.equals(uri)) {
                    if (AccessibilityManagerService.this.readEnhancedWebAccessibilityEnabledChangedLocked(userState)) {
                        AccessibilityManagerService.this.onUserStateChangedLocked(userState);
                    }
                } else if (this.mDisplayInversionEnabledUri.equals(uri) || this.mDisplayDaltonizerEnabledUri.equals(uri) || this.mDisplayDaltonizerUri.equals(uri)) {
                    if (AccessibilityManagerService.this.readDisplayColorAdjustmentSettingsLocked(userState)) {
                        AccessibilityManagerService.this.updateDisplayColorAdjustmentSettingsLocked(userState);
                    }
                } else if (this.mDisplayColorMatrixUri.equals(uri)) {
                    AccessibilityManagerService.this.updateDisplayColorAdjustmentSettingsLocked(userState);
                } else if (this.mHighTextContrastUri.equals(uri)) {
                    if (AccessibilityManagerService.this.readHighTextContrastEnabledSettingLocked(userState)) {
                        AccessibilityManagerService.this.onUserStateChangedLocked(userState);
                    }
                } else if (this.mAccessibilitySoftKeyboardModeUri.equals(uri) && AccessibilityManagerService.this.readSoftKeyboardShowModeChangedLocked(userState)) {
                    AccessibilityManagerService.this.notifySoftKeyboardShowModeChangedLocked(userState.mSoftKeyboardShowMode);
                    AccessibilityManagerService.this.onUserStateChangedLocked(userState);
                }
            }
        }
    }

    private final class InteractionBridge {
        private final AccessibilityInteractionClient mClient = AccessibilityInteractionClient.getInstance();
        private final int mConnectionId;
        private final Display mDefaultDisplay;

        public InteractionBridge() {
            AccessibilityServiceInfo info = new AccessibilityServiceInfo();
            info.setCapabilities(1);
            info.flags |= 64;
            info.flags |= 2;
            Service service = new Service(-10000, AccessibilityManagerService.sFakeAccessibilityServiceComponentName, info);
            this.mConnectionId = service.mId;
            this.mClient.addConnection(this.mConnectionId, service);
            this.mDefaultDisplay = ((DisplayManager) AccessibilityManagerService.this.mContext.getSystemService("display")).getDisplay(0);
        }

        public void clearAccessibilityFocusNotLocked(int windowId) {
            AccessibilityNodeInfo focus = getAccessibilityFocusNotLocked(windowId);
            if (focus != null) {
                focus.performAction(128);
            }
        }

        public boolean getAccessibilityFocusClickPointInScreenNotLocked(Point outPoint) {
            AccessibilityNodeInfo focus = getAccessibilityFocusNotLocked();
            if (focus == null) {
                return false;
            }
            synchronized (AccessibilityManagerService.this.mLock) {
                Rect boundsInScreen = AccessibilityManagerService.this.mTempRect;
                focus.getBoundsInScreen(boundsInScreen);
                Rect windowBounds = AccessibilityManagerService.this.mTempRect1;
                AccessibilityManagerService.this.getWindowBounds(focus.getWindowId(), windowBounds);
                if (boundsInScreen.intersect(windowBounds)) {
                    MagnificationSpec spec = AccessibilityManagerService.this.getCompatibleMagnificationSpecLocked(focus.getWindowId());
                    if (!(spec == null || spec.isNop())) {
                        boundsInScreen.offset((int) (-spec.offsetX), (int) (-spec.offsetY));
                        boundsInScreen.scale(1.0f / spec.scale);
                    }
                    Point screenSize = AccessibilityManagerService.this.mTempPoint;
                    this.mDefaultDisplay.getRealSize(screenSize);
                    if (boundsInScreen.intersect(0, 0, screenSize.x, screenSize.y)) {
                        outPoint.set(boundsInScreen.centerX(), boundsInScreen.centerY());
                        return true;
                    }
                    return false;
                }
                return false;
            }
        }

        private AccessibilityNodeInfo getAccessibilityFocusNotLocked() {
            synchronized (AccessibilityManagerService.this.mLock) {
                int focusedWindowId = AccessibilityManagerService.this.mSecurityPolicy.mAccessibilityFocusedWindowId;
                if (focusedWindowId == -1) {
                    return null;
                }
                return getAccessibilityFocusNotLocked(focusedWindowId);
            }
        }

        private AccessibilityNodeInfo getAccessibilityFocusNotLocked(int windowId) {
            return this.mClient.findFocus(this.mConnectionId, windowId, AccessibilityNodeInfo.ROOT_NODE_ID, 2);
        }
    }

    private final class MainHandler extends Handler {
        public static final int MSG_ANNOUNCE_NEW_USER_IF_NEEDED = 5;
        public static final int MSG_CLEAR_ACCESSIBILITY_FOCUS = 9;
        public static final int MSG_SEND_ACCESSIBILITY_EVENT_TO_INPUT_FILTER = 1;
        public static final int MSG_SEND_CLEARED_STATE_TO_CLIENTS_FOR_USER = 3;
        public static final int MSG_SEND_KEY_EVENT_TO_INPUT_FILTER = 8;
        public static final int MSG_SEND_STATE_TO_CLIENTS = 2;
        public static final int MSG_SHOW_ENABLED_TOUCH_EXPLORATION_DIALOG = 7;
        public static final int MSG_UPDATE_INPUT_FILTER = 6;

        public MainHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    AccessibilityEvent event = msg.obj;
                    synchronized (AccessibilityManagerService.this.mLock) {
                        if (AccessibilityManagerService.this.mHasInputFilter && AccessibilityManagerService.this.mInputFilter != null) {
                            AccessibilityManagerService.this.mInputFilter.notifyAccessibilityEvent(event);
                        }
                    }
                    event.recycle();
                    return;
                case 2:
                    int clientState = msg.arg1;
                    int userId = msg.arg2;
                    sendStateToClients(clientState, AccessibilityManagerService.this.mGlobalClients);
                    sendStateToClientsForUser(clientState, userId);
                    return;
                case 3:
                    sendStateToClientsForUser(0, msg.arg1);
                    return;
                case 5:
                    announceNewUserIfNeeded();
                    return;
                case 6:
                    AccessibilityManagerService.this.updateInputFilter(msg.obj);
                    return;
                case 7:
                    AccessibilityManagerService.this.showEnableTouchExplorationDialog(msg.obj);
                    return;
                case 8:
                    KeyEvent event2 = msg.obj;
                    int policyFlags = msg.arg1;
                    synchronized (AccessibilityManagerService.this.mLock) {
                        if (AccessibilityManagerService.this.mHasInputFilter && AccessibilityManagerService.this.mInputFilter != null) {
                            AccessibilityManagerService.this.mInputFilter.sendInputEvent(event2, policyFlags);
                        }
                    }
                    event2.recycle();
                    return;
                case 9:
                    InteractionBridge bridge;
                    int windowId = msg.arg1;
                    synchronized (AccessibilityManagerService.this.mLock) {
                        bridge = AccessibilityManagerService.this.getInteractionBridgeLocked();
                    }
                    bridge.clearAccessibilityFocusNotLocked(windowId);
                    return;
                default:
                    return;
            }
        }

        private void announceNewUserIfNeeded() {
            synchronized (AccessibilityManagerService.this.mLock) {
                if (AccessibilityManagerService.this.getCurrentUserStateLocked().isHandlingAccessibilityEvents()) {
                    UserManager userManager = (UserManager) AccessibilityManagerService.this.mContext.getSystemService("user");
                    String message = AccessibilityManagerService.this.mContext.getString(17040665, new Object[]{userManager.getUserInfo(AccessibilityManagerService.this.mCurrentUserId).name});
                    AccessibilityEvent event = AccessibilityEvent.obtain(DumpState.DUMP_KEYSETS);
                    event.getText().add(message);
                    AccessibilityManagerService.this.sendAccessibilityEvent(event, AccessibilityManagerService.this.mCurrentUserId);
                }
            }
        }

        private void sendStateToClientsForUser(int clientState, int userId) {
            UserState userState;
            synchronized (AccessibilityManagerService.this.mLock) {
                userState = AccessibilityManagerService.this.getUserStateLocked(userId);
            }
            sendStateToClients(clientState, userState.mClients);
        }

        private void sendStateToClients(int clientState, RemoteCallbackList<IAccessibilityManagerClient> clients) {
            try {
                int userClientCount = clients.beginBroadcast();
                for (int i = 0; i < userClientCount; i++) {
                    try {
                        ((IAccessibilityManagerClient) clients.getBroadcastItem(i)).setState(clientState);
                    } catch (RemoteException e) {
                    }
                }
            } finally {
                clients.finishBroadcast();
            }
        }
    }

    final class SecurityPolicy {
        public static final int INVALID_WINDOW_ID = -1;
        private static final int RETRIEVAL_ALLOWING_EVENT_TYPES = 244159;
        public long mAccessibilityFocusNodeId = 2147483647L;
        public int mAccessibilityFocusedWindowId = -1;
        public int mActiveWindowId = -1;
        public int mFocusedWindowId = -1;
        private boolean mTouchInteractionInProgress;
        public List<AccessibilityWindowInfo> mWindows;

        SecurityPolicy() {
        }

        private boolean canDispatchAccessibilityEventLocked(AccessibilityEvent event) {
            switch (event.getEventType()) {
                case 32:
                case 64:
                case 128:
                case 256:
                case 512:
                case 1024:
                case DumpState.DUMP_KEYSETS /*16384*/:
                case DumpState.DUMP_DOMAIN_PREFERRED /*262144*/:
                case DumpState.DUMP_FROZEN /*524288*/:
                case DumpState.DUMP_DEXOPT /*1048576*/:
                case 2097152:
                case 4194304:
                case 16777216:
                    return true;
                default:
                    return isRetrievalAllowingWindow(event.getWindowId());
            }
        }

        public void clearWindowsLocked() {
            List<AccessibilityWindowInfo> windows = Collections.emptyList();
            int activeWindowId = this.mActiveWindowId;
            updateWindowsLocked(windows);
            this.mActiveWindowId = activeWindowId;
            this.mWindows = null;
        }

        public void updateWindowsLocked(List<AccessibilityWindowInfo> windows) {
            int i;
            if (this.mWindows == null) {
                this.mWindows = new ArrayList();
            }
            for (i = this.mWindows.size() - 1; i >= 0; i--) {
                ((AccessibilityWindowInfo) this.mWindows.remove(i)).recycle();
            }
            this.mFocusedWindowId = -1;
            if (!this.mTouchInteractionInProgress) {
                this.mActiveWindowId = -1;
            }
            boolean activeWindowGone = true;
            int windowCount = windows.size();
            if (windowCount > 0) {
                AccessibilityWindowInfo window;
                for (i = 0; i < windowCount; i++) {
                    window = (AccessibilityWindowInfo) windows.get(i);
                    int windowId = window.getId();
                    if (window.isFocused()) {
                        this.mFocusedWindowId = windowId;
                        if (!this.mTouchInteractionInProgress) {
                            this.mActiveWindowId = windowId;
                            window.setActive(true);
                        } else if (windowId == this.mActiveWindowId) {
                            activeWindowGone = false;
                        }
                    }
                    this.mWindows.add(window);
                }
                if (this.mTouchInteractionInProgress && activeWindowGone) {
                    this.mActiveWindowId = this.mFocusedWindowId;
                }
                for (i = 0; i < windowCount; i++) {
                    window = (AccessibilityWindowInfo) this.mWindows.get(i);
                    if (window.getId() == this.mActiveWindowId) {
                        window.setActive(true);
                    }
                    if (window.getId() == this.mAccessibilityFocusedWindowId) {
                        window.setAccessibilityFocused(true);
                    }
                }
            }
            notifyWindowsChanged();
        }

        public boolean computePartialInteractiveRegionForWindowLocked(int windowId, Region outRegion) {
            if (this.mWindows == null) {
                return false;
            }
            Region windowInteractiveRegion = null;
            boolean windowInteractiveRegionChanged = false;
            for (int i = this.mWindows.size() - 1; i >= 0; i--) {
                AccessibilityWindowInfo currentWindow = (AccessibilityWindowInfo) this.mWindows.get(i);
                Rect currentWindowBounds;
                if (windowInteractiveRegion == null) {
                    if (currentWindow.getId() == windowId) {
                        currentWindowBounds = AccessibilityManagerService.this.mTempRect;
                        currentWindow.getBoundsInScreen(currentWindowBounds);
                        outRegion.set(currentWindowBounds);
                        windowInteractiveRegion = outRegion;
                    }
                } else if (currentWindow.getType() != 4) {
                    currentWindowBounds = AccessibilityManagerService.this.mTempRect;
                    currentWindow.getBoundsInScreen(currentWindowBounds);
                    if (windowInteractiveRegion.op(currentWindowBounds, Op.DIFFERENCE)) {
                        windowInteractiveRegionChanged = true;
                    }
                }
            }
            return windowInteractiveRegionChanged;
        }

        public void updateEventSourceLocked(AccessibilityEvent event) {
            if ((event.getEventType() & RETRIEVAL_ALLOWING_EVENT_TYPES) == 0) {
                event.setSource(null);
            }
        }

        public void updateActiveAndAccessibilityFocusedWindowLocked(int windowId, long nodeId, int eventType, int eventAction) {
            Object -get7;
            switch (eventType) {
                case 32:
                    -get7 = AccessibilityManagerService.this.mLock;
                    synchronized (-get7) {
                        if (AccessibilityManagerService.this.mWindowsForAccessibilityCallback == null) {
                            this.mFocusedWindowId = getFocusedWindowId();
                            if (windowId == this.mFocusedWindowId) {
                                this.mActiveWindowId = windowId;
                                break;
                            }
                        }
                    }
                    break;
                case 128:
                    -get7 = AccessibilityManagerService.this.mLock;
                    synchronized (-get7) {
                        if (this.mTouchInteractionInProgress && this.mActiveWindowId != windowId) {
                            setActiveWindowLocked(windowId);
                            break;
                        }
                    }
                case DumpState.DUMP_VERSION /*32768*/:
                    -get7 = AccessibilityManagerService.this.mLock;
                    synchronized (-get7) {
                        if (this.mAccessibilityFocusedWindowId != windowId) {
                            AccessibilityManagerService.this.mMainHandler.obtainMessage(9, this.mAccessibilityFocusedWindowId, 0).sendToTarget();
                            AccessibilityManagerService.this.mSecurityPolicy.setAccessibilityFocusedWindowLocked(windowId);
                            this.mAccessibilityFocusNodeId = nodeId;
                            break;
                        }
                    }
                    break;
                case DumpState.DUMP_INSTALLS /*65536*/:
                    -get7 = AccessibilityManagerService.this.mLock;
                    synchronized (-get7) {
                        if (this.mAccessibilityFocusNodeId == nodeId) {
                            this.mAccessibilityFocusNodeId = 2147483647L;
                        }
                        if (this.mAccessibilityFocusNodeId == 2147483647L && this.mAccessibilityFocusedWindowId == windowId && eventAction != 64) {
                            this.mAccessibilityFocusedWindowId = -1;
                            break;
                        }
                    }
                default:
                    return;
            }
        }

        public void onTouchInteractionStart() {
            synchronized (AccessibilityManagerService.this.mLock) {
                this.mTouchInteractionInProgress = true;
            }
        }

        public void onTouchInteractionEnd() {
            synchronized (AccessibilityManagerService.this.mLock) {
                this.mTouchInteractionInProgress = false;
                int oldActiveWindow = AccessibilityManagerService.this.mSecurityPolicy.mActiveWindowId;
                setActiveWindowLocked(this.mFocusedWindowId);
                if (oldActiveWindow != AccessibilityManagerService.this.mSecurityPolicy.mActiveWindowId && this.mAccessibilityFocusedWindowId == oldActiveWindow && AccessibilityManagerService.this.getCurrentUserStateLocked().mAccessibilityFocusOnlyInActiveWindow) {
                    AccessibilityManagerService.this.mMainHandler.obtainMessage(9, oldActiveWindow, 0).sendToTarget();
                }
            }
        }

        public int getActiveWindowId() {
            if (this.mActiveWindowId == -1 && !this.mTouchInteractionInProgress) {
                this.mActiveWindowId = getFocusedWindowId();
            }
            return this.mActiveWindowId;
        }

        private void setActiveWindowLocked(int windowId) {
            if (this.mActiveWindowId != windowId) {
                this.mActiveWindowId = windowId;
                if (this.mWindows != null) {
                    int windowCount = this.mWindows.size();
                    for (int i = 0; i < windowCount; i++) {
                        AccessibilityWindowInfo window = (AccessibilityWindowInfo) this.mWindows.get(i);
                        window.setActive(window.getId() == windowId);
                    }
                }
                notifyWindowsChanged();
            }
        }

        private void setAccessibilityFocusedWindowLocked(int windowId) {
            if (this.mAccessibilityFocusedWindowId != windowId) {
                this.mAccessibilityFocusedWindowId = windowId;
                if (this.mWindows != null) {
                    int windowCount = this.mWindows.size();
                    for (int i = 0; i < windowCount; i++) {
                        AccessibilityWindowInfo window = (AccessibilityWindowInfo) this.mWindows.get(i);
                        window.setAccessibilityFocused(window.getId() == windowId);
                    }
                }
                notifyWindowsChanged();
            }
        }

        private void notifyWindowsChanged() {
            if (AccessibilityManagerService.this.mWindowsForAccessibilityCallback != null) {
                long identity = Binder.clearCallingIdentity();
                try {
                    AccessibilityEvent event = AccessibilityEvent.obtain(4194304);
                    event.setEventTime(SystemClock.uptimeMillis());
                    AccessibilityManagerService.this.sendAccessibilityEvent(event, AccessibilityManagerService.this.mCurrentUserId);
                } finally {
                    Binder.restoreCallingIdentity(identity);
                }
            }
        }

        public boolean canGetAccessibilityNodeInfoLocked(Service service, int windowId) {
            return canRetrieveWindowContentLocked(service) ? isRetrievalAllowingWindow(windowId) : false;
        }

        public boolean canRetrieveWindowsLocked(Service service) {
            return canRetrieveWindowContentLocked(service) ? service.mRetrieveInteractiveWindows : false;
        }

        public boolean canRetrieveWindowContentLocked(Service service) {
            return (service.mAccessibilityServiceInfo.getCapabilities() & 1) != 0;
        }

        public boolean canControlMagnification(Service service) {
            return (service.mAccessibilityServiceInfo.getCapabilities() & 16) != 0;
        }

        public boolean canPerformGestures(Service service) {
            return (service.mAccessibilityServiceInfo.getCapabilities() & 32) != 0;
        }

        private int resolveProfileParentLocked(int userId) {
            if (userId != AccessibilityManagerService.this.mCurrentUserId) {
                long identity = Binder.clearCallingIdentity();
                try {
                    UserInfo parent = AccessibilityManagerService.this.mUserManager.getProfileParent(userId);
                    if (parent != null) {
                        int identifier = parent.getUserHandle().getIdentifier();
                        return identifier;
                    }
                    Binder.restoreCallingIdentity(identity);
                } finally {
                    Binder.restoreCallingIdentity(identity);
                }
            }
            return userId;
        }

        public int resolveCallingUserIdEnforcingPermissionsLocked(int userId) {
            int callingUid = Binder.getCallingUid();
            if (callingUid != 0 && callingUid != 1000 && callingUid != IHwShutdownThread.SHUTDOWN_ANIMATION_WAIT_TIME) {
                int callingUserId = UserHandle.getUserId(callingUid);
                if (callingUserId == userId) {
                    return resolveProfileParentLocked(userId);
                }
                if (resolveProfileParentLocked(callingUserId) == AccessibilityManagerService.this.mCurrentUserId && (userId == -2 || userId == -3)) {
                    return AccessibilityManagerService.this.mCurrentUserId;
                }
                if (!hasPermission("android.permission.INTERACT_ACROSS_USERS") && !hasPermission("android.permission.INTERACT_ACROSS_USERS_FULL")) {
                    throw new SecurityException("Call from user " + callingUserId + " as user " + userId + " without permission INTERACT_ACROSS_USERS or " + "INTERACT_ACROSS_USERS_FULL not allowed.");
                } else if (userId == -2 || userId == -3) {
                    return AccessibilityManagerService.this.mCurrentUserId;
                } else {
                    throw new IllegalArgumentException("Calling user can be changed to only UserHandle.USER_CURRENT or UserHandle.USER_CURRENT_OR_SELF.");
                }
            } else if (userId == -2 || userId == -3) {
                return AccessibilityManagerService.this.mCurrentUserId;
            } else {
                return resolveProfileParentLocked(userId);
            }
        }

        public boolean isCallerInteractingAcrossUsers(int userId) {
            int callingUid = Binder.getCallingUid();
            if (Binder.getCallingPid() == Process.myPid() || callingUid == IHwShutdownThread.SHUTDOWN_ANIMATION_WAIT_TIME || userId == -2 || userId == -3) {
                return true;
            }
            return false;
        }

        private boolean isRetrievalAllowingWindow(int windowId) {
            boolean z = true;
            if (Binder.getCallingUid() == 1000 || windowId == this.mActiveWindowId) {
                return true;
            }
            if (findWindowById(windowId) == null) {
                z = false;
            }
            return z;
        }

        private AccessibilityWindowInfo findWindowById(int windowId) {
            if (this.mWindows != null) {
                int windowCount = this.mWindows.size();
                for (int i = 0; i < windowCount; i++) {
                    AccessibilityWindowInfo window = (AccessibilityWindowInfo) this.mWindows.get(i);
                    if (window.getId() == windowId) {
                        return window;
                    }
                }
            }
            return null;
        }

        private void enforceCallingPermission(String permission, String function) {
            if (AccessibilityManagerService.OWN_PROCESS_ID != Binder.getCallingPid() && !hasPermission(permission)) {
                throw new SecurityException("You do not have " + permission + " required to call " + function + " from pid=" + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid());
            }
        }

        private boolean hasPermission(String permission) {
            return AccessibilityManagerService.this.mContext.checkCallingPermission(permission) == 0;
        }

        private int getFocusedWindowId() {
            int -wrap15;
            IBinder token = AccessibilityManagerService.this.mWindowManagerService.getFocusedWindowToken();
            synchronized (AccessibilityManagerService.this.mLock) {
                -wrap15 = AccessibilityManagerService.this.findWindowIdLocked(token);
            }
            return -wrap15;
        }
    }

    class Service extends IAccessibilityServiceConnection.Stub implements ServiceConnection, DeathRecipient {
        AccessibilityServiceInfo mAccessibilityServiceInfo;
        ComponentName mComponentName;
        public Handler mEventDispatchHandler = new Handler(AccessibilityManagerService.this.mMainHandler.getLooper()) {
            public void handleMessage(Message message) {
                Service.this.notifyAccessibilityEventInternal(message.what, message.obj);
            }
        };
        int mEventTypes;
        int mFeedbackType;
        int mFetchFlags;
        int mId = 0;
        Intent mIntent;
        public final InvocationHandler mInvocationHandler = new InvocationHandler(AccessibilityManagerService.this.mMainHandler.getLooper());
        boolean mIsAutomation;
        boolean mIsDefault;
        long mNotificationTimeout;
        final IBinder mOverlayWindowToken = new Binder();
        Set<String> mPackageNames = new HashSet();
        final SparseArray<AccessibilityEvent> mPendingEvents = new SparseArray();
        boolean mRequestEnhancedWebAccessibility;
        boolean mRequestFilterKeyEvents;
        boolean mRequestTouchExplorationMode;
        final ResolveInfo mResolveInfo;
        boolean mRetrieveInteractiveWindows;
        IBinder mService;
        IAccessibilityServiceClient mServiceInterface;
        final int mUserId;
        boolean mWasConnectedAndDied;

        private final class InvocationHandler extends Handler {
            public static final int MSG_CLEAR_ACCESSIBILITY_CACHE = 2;
            public static final int MSG_ON_GESTURE = 1;
            private static final int MSG_ON_MAGNIFICATION_CHANGED = 5;
            private static final int MSG_ON_SOFT_KEYBOARD_STATE_CHANGED = 6;
            private boolean mIsMagnificationCallbackEnabled = false;
            private boolean mIsSoftKeyboardCallbackEnabled = false;

            public InvocationHandler(Looper looper) {
                super(looper, null, true);
            }

            public void handleMessage(Message message) {
                int type = message.what;
                switch (type) {
                    case 1:
                        Service.this.notifyGestureInternal(message.arg1);
                        return;
                    case 2:
                        Service.this.notifyClearAccessibilityCacheInternal();
                        return;
                    case 5:
                        SomeArgs args = message.obj;
                        Service.this.notifyMagnificationChangedInternal(args.arg1, ((Float) args.arg2).floatValue(), ((Float) args.arg3).floatValue(), ((Float) args.arg4).floatValue());
                        return;
                    case 6:
                        Service.this.notifySoftKeyboardShowModeChangedInternal(message.arg1);
                        return;
                    default:
                        throw new IllegalArgumentException("Unknown message: " + type);
                }
            }

            public void notifyMagnificationChangedLocked(Region region, float scale, float centerX, float centerY) {
                if (this.mIsMagnificationCallbackEnabled) {
                    SomeArgs args = SomeArgs.obtain();
                    args.arg1 = region;
                    args.arg2 = Float.valueOf(scale);
                    args.arg3 = Float.valueOf(centerX);
                    args.arg4 = Float.valueOf(centerY);
                    obtainMessage(5, args).sendToTarget();
                }
            }

            public void setMagnificationCallbackEnabled(boolean enabled) {
                this.mIsMagnificationCallbackEnabled = enabled;
            }

            public void notifySoftKeyboardShowModeChangedLocked(int showState) {
                if (this.mIsSoftKeyboardCallbackEnabled) {
                    obtainMessage(6, showState, 0).sendToTarget();
                }
            }

            public void setSoftKeyboardCallbackEnabled(boolean enabled) {
                this.mIsSoftKeyboardCallbackEnabled = enabled;
            }
        }

        public boolean findAccessibilityNodeInfoByAccessibilityId(int r19, long r20, int r22, android.view.accessibility.IAccessibilityInteractionConnectionCallback r23, int r24, long r25) throws android.os.RemoteException {
            /* JADX: method processing error */
/*
Error: jadx.core.utils.exceptions.JadxRuntimeException: Can't find block by offset: 0x0097 in list []
	at jadx.core.utils.BlockUtils.getBlockByOffset(BlockUtils.java:43)
	at jadx.core.dex.instructions.IfNode.initBlocks(IfNode.java:60)
	at jadx.core.dex.visitors.blocksmaker.BlockFinish.initBlocksInIfNodes(BlockFinish.java:48)
	at jadx.core.dex.visitors.blocksmaker.BlockFinish.visit(BlockFinish.java:33)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:31)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:17)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:14)
	at jadx.core.ProcessClass.process(ProcessClass.java:37)
	at jadx.core.ProcessClass.processDependencies(ProcessClass.java:59)
	at jadx.core.ProcessClass.process(ProcessClass.java:42)
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:306)
	at jadx.api.JavaClass.decompile(JavaClass.java:62)
	at jadx.api.JadxDecompiler$1.run(JadxDecompiler.java:199)
*/
            /*
            r18 = this;
            r2 = 0;
            r5 = android.graphics.Region.obtain();
            r0 = r18;
            r3 = com.android.server.accessibility.AccessibilityManagerService.this;
            r4 = r3.mLock;
            monitor-enter(r4);
            r3 = r18.isCalledForCurrentUserLocked();
            if (r3 != 0) goto L_0x0017;
        L_0x0014:
            r3 = 0;
            monitor-exit(r4);
            return r3;
        L_0x0017:
            r17 = r18.resolveAccessibilityWindowIdLocked(r19);
            r0 = r18;
            r3 = com.android.server.accessibility.AccessibilityManagerService.this;
            r3 = r3.mSecurityPolicy;
            r0 = r18;
            r1 = r17;
            r13 = r3.canGetAccessibilityNodeInfoLocked(r0, r1);
            if (r13 != 0) goto L_0x0030;
        L_0x002d:
            r3 = 0;
            monitor-exit(r4);
            return r3;
        L_0x0030:
            r0 = r18;
            r1 = r17;
            r2 = r0.getConnectionLocked(r1);
            if (r2 != 0) goto L_0x003d;
        L_0x003a:
            r3 = 0;
            monitor-exit(r4);
            return r3;
        L_0x003d:
            r0 = r18;
            r3 = com.android.server.accessibility.AccessibilityManagerService.this;
            r3 = r3.mSecurityPolicy;
            r0 = r17;
            r3 = r3.computePartialInteractiveRegionForWindowLocked(r0, r5);
            if (r3 != 0) goto L_0x0051;
        L_0x004d:
            r5.recycle();
            r5 = 0;
        L_0x0051:
            monitor-exit(r4);
            r9 = android.os.Binder.getCallingPid();
            r14 = android.os.Binder.clearCallingIdentity();
            r0 = r18;
            r3 = com.android.server.accessibility.AccessibilityManagerService.this;
            r0 = r17;
            r12 = r3.getCompatibleMagnificationSpecLocked(r0);
            r0 = r18;	 Catch:{ RemoteException -> 0x0088, all -> 0x0099 }
            r3 = r0.mFetchFlags;	 Catch:{ RemoteException -> 0x0088, all -> 0x0099 }
            r8 = r3 | r24;	 Catch:{ RemoteException -> 0x0088, all -> 0x0099 }
            r3 = r20;	 Catch:{ RemoteException -> 0x0088, all -> 0x0099 }
            r6 = r22;	 Catch:{ RemoteException -> 0x0088, all -> 0x0099 }
            r7 = r23;	 Catch:{ RemoteException -> 0x0088, all -> 0x0099 }
            r10 = r25;	 Catch:{ RemoteException -> 0x0088, all -> 0x0099 }
            r2.findAccessibilityNodeInfoByAccessibilityId(r3, r5, r6, r7, r8, r9, r10, r12);	 Catch:{ RemoteException -> 0x0088, all -> 0x0099 }
            r3 = 1;
            android.os.Binder.restoreCallingIdentity(r14);
            if (r5 == 0) goto L_0x0084;
        L_0x007b:
            r4 = android.os.Binder.isProxy(r2);
            if (r4 == 0) goto L_0x0084;
        L_0x0081:
            r5.recycle();
        L_0x0084:
            return r3;
        L_0x0085:
            r3 = move-exception;
            monitor-exit(r4);
            throw r3;
        L_0x0088:
            r16 = move-exception;
            android.os.Binder.restoreCallingIdentity(r14);
            if (r5 == 0) goto L_0x0097;
        L_0x008e:
            r3 = android.os.Binder.isProxy(r2);
            if (r3 == 0) goto L_0x0097;
        L_0x0094:
            r5.recycle();
        L_0x0097:
            r3 = 0;
            return r3;
        L_0x0099:
            r3 = move-exception;
            android.os.Binder.restoreCallingIdentity(r14);
            if (r5 == 0) goto L_0x00a8;
        L_0x009f:
            r4 = android.os.Binder.isProxy(r2);
            if (r4 == 0) goto L_0x00a8;
        L_0x00a5:
            r5.recycle();
        L_0x00a8:
            throw r3;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.server.accessibility.AccessibilityManagerService.Service.findAccessibilityNodeInfoByAccessibilityId(int, long, int, android.view.accessibility.IAccessibilityInteractionConnectionCallback, int, long):boolean");
        }

        public boolean findAccessibilityNodeInfosByText(int r20, long r21, java.lang.String r23, int r24, android.view.accessibility.IAccessibilityInteractionConnectionCallback r25, long r26) throws android.os.RemoteException {
            /* JADX: method processing error */
/*
Error: jadx.core.utils.exceptions.JadxRuntimeException: Can't find block by offset: 0x0097 in list []
	at jadx.core.utils.BlockUtils.getBlockByOffset(BlockUtils.java:43)
	at jadx.core.dex.instructions.IfNode.initBlocks(IfNode.java:60)
	at jadx.core.dex.visitors.blocksmaker.BlockFinish.initBlocksInIfNodes(BlockFinish.java:48)
	at jadx.core.dex.visitors.blocksmaker.BlockFinish.visit(BlockFinish.java:33)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:31)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:17)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:14)
	at jadx.core.ProcessClass.process(ProcessClass.java:37)
	at jadx.core.ProcessClass.processDependencies(ProcessClass.java:59)
	at jadx.core.ProcessClass.process(ProcessClass.java:42)
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:306)
	at jadx.api.JavaClass.decompile(JavaClass.java:62)
	at jadx.api.JadxDecompiler$1.run(JadxDecompiler.java:199)
*/
            /*
            r19 = this;
            r3 = 0;
            r7 = android.graphics.Region.obtain();
            r0 = r19;
            r4 = com.android.server.accessibility.AccessibilityManagerService.this;
            r5 = r4.mLock;
            monitor-enter(r5);
            r4 = r19.isCalledForCurrentUserLocked();
            if (r4 != 0) goto L_0x0017;
        L_0x0014:
            r4 = 0;
            monitor-exit(r5);
            return r4;
        L_0x0017:
            r18 = r19.resolveAccessibilityWindowIdLocked(r20);
            r0 = r19;
            r4 = com.android.server.accessibility.AccessibilityManagerService.this;
            r4 = r4.mSecurityPolicy;
            r0 = r19;
            r1 = r18;
            r2 = r4.canGetAccessibilityNodeInfoLocked(r0, r1);
            if (r2 != 0) goto L_0x0030;
        L_0x002d:
            r4 = 0;
            monitor-exit(r5);
            return r4;
        L_0x0030:
            r0 = r19;
            r1 = r18;
            r3 = r0.getConnectionLocked(r1);
            if (r3 != 0) goto L_0x003d;
        L_0x003a:
            r4 = 0;
            monitor-exit(r5);
            return r4;
        L_0x003d:
            r0 = r19;
            r4 = com.android.server.accessibility.AccessibilityManagerService.this;
            r4 = r4.mSecurityPolicy;
            r0 = r18;
            r4 = r4.computePartialInteractiveRegionForWindowLocked(r0, r7);
            if (r4 != 0) goto L_0x0051;
        L_0x004d:
            r7.recycle();
            r7 = 0;
        L_0x0051:
            monitor-exit(r5);
            r11 = android.os.Binder.getCallingPid();
            r16 = android.os.Binder.clearCallingIdentity();
            r0 = r19;
            r4 = com.android.server.accessibility.AccessibilityManagerService.this;
            r0 = r18;
            r14 = r4.getCompatibleMagnificationSpecLocked(r0);
            r0 = r19;	 Catch:{ RemoteException -> 0x0088, all -> 0x0099 }
            r10 = r0.mFetchFlags;	 Catch:{ RemoteException -> 0x0088, all -> 0x0099 }
            r4 = r21;	 Catch:{ RemoteException -> 0x0088, all -> 0x0099 }
            r6 = r23;	 Catch:{ RemoteException -> 0x0088, all -> 0x0099 }
            r8 = r24;	 Catch:{ RemoteException -> 0x0088, all -> 0x0099 }
            r9 = r25;	 Catch:{ RemoteException -> 0x0088, all -> 0x0099 }
            r12 = r26;	 Catch:{ RemoteException -> 0x0088, all -> 0x0099 }
            r3.findAccessibilityNodeInfosByText(r4, r6, r7, r8, r9, r10, r11, r12, r14);	 Catch:{ RemoteException -> 0x0088, all -> 0x0099 }
            r4 = 1;
            android.os.Binder.restoreCallingIdentity(r16);
            if (r7 == 0) goto L_0x0084;
        L_0x007b:
            r5 = android.os.Binder.isProxy(r3);
            if (r5 == 0) goto L_0x0084;
        L_0x0081:
            r7.recycle();
        L_0x0084:
            return r4;
        L_0x0085:
            r4 = move-exception;
            monitor-exit(r5);
            throw r4;
        L_0x0088:
            r15 = move-exception;
            android.os.Binder.restoreCallingIdentity(r16);
            if (r7 == 0) goto L_0x0097;
        L_0x008e:
            r4 = android.os.Binder.isProxy(r3);
            if (r4 == 0) goto L_0x0097;
        L_0x0094:
            r7.recycle();
        L_0x0097:
            r4 = 0;
            return r4;
        L_0x0099:
            r4 = move-exception;
            android.os.Binder.restoreCallingIdentity(r16);
            if (r7 == 0) goto L_0x00a8;
        L_0x009f:
            r5 = android.os.Binder.isProxy(r3);
            if (r5 == 0) goto L_0x00a8;
        L_0x00a5:
            r7.recycle();
        L_0x00a8:
            throw r4;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.server.accessibility.AccessibilityManagerService.Service.findAccessibilityNodeInfosByText(int, long, java.lang.String, int, android.view.accessibility.IAccessibilityInteractionConnectionCallback, long):boolean");
        }

        public boolean findAccessibilityNodeInfosByViewId(int r20, long r21, java.lang.String r23, int r24, android.view.accessibility.IAccessibilityInteractionConnectionCallback r25, long r26) throws android.os.RemoteException {
            /* JADX: method processing error */
/*
Error: jadx.core.utils.exceptions.JadxRuntimeException: Can't find block by offset: 0x0097 in list []
	at jadx.core.utils.BlockUtils.getBlockByOffset(BlockUtils.java:43)
	at jadx.core.dex.instructions.IfNode.initBlocks(IfNode.java:60)
	at jadx.core.dex.visitors.blocksmaker.BlockFinish.initBlocksInIfNodes(BlockFinish.java:48)
	at jadx.core.dex.visitors.blocksmaker.BlockFinish.visit(BlockFinish.java:33)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:31)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:17)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:14)
	at jadx.core.ProcessClass.process(ProcessClass.java:37)
	at jadx.core.ProcessClass.processDependencies(ProcessClass.java:59)
	at jadx.core.ProcessClass.process(ProcessClass.java:42)
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:306)
	at jadx.api.JavaClass.decompile(JavaClass.java:62)
	at jadx.api.JadxDecompiler$1.run(JadxDecompiler.java:199)
*/
            /*
            r19 = this;
            r3 = 0;
            r7 = android.graphics.Region.obtain();
            r0 = r19;
            r4 = com.android.server.accessibility.AccessibilityManagerService.this;
            r5 = r4.mLock;
            monitor-enter(r5);
            r4 = r19.isCalledForCurrentUserLocked();
            if (r4 != 0) goto L_0x0017;
        L_0x0014:
            r4 = 0;
            monitor-exit(r5);
            return r4;
        L_0x0017:
            r18 = r19.resolveAccessibilityWindowIdLocked(r20);
            r0 = r19;
            r4 = com.android.server.accessibility.AccessibilityManagerService.this;
            r4 = r4.mSecurityPolicy;
            r0 = r19;
            r1 = r18;
            r2 = r4.canGetAccessibilityNodeInfoLocked(r0, r1);
            if (r2 != 0) goto L_0x0030;
        L_0x002d:
            r4 = 0;
            monitor-exit(r5);
            return r4;
        L_0x0030:
            r0 = r19;
            r1 = r18;
            r3 = r0.getConnectionLocked(r1);
            if (r3 != 0) goto L_0x003d;
        L_0x003a:
            r4 = 0;
            monitor-exit(r5);
            return r4;
        L_0x003d:
            r0 = r19;
            r4 = com.android.server.accessibility.AccessibilityManagerService.this;
            r4 = r4.mSecurityPolicy;
            r0 = r18;
            r4 = r4.computePartialInteractiveRegionForWindowLocked(r0, r7);
            if (r4 != 0) goto L_0x0051;
        L_0x004d:
            r7.recycle();
            r7 = 0;
        L_0x0051:
            monitor-exit(r5);
            r11 = android.os.Binder.getCallingPid();
            r16 = android.os.Binder.clearCallingIdentity();
            r0 = r19;
            r4 = com.android.server.accessibility.AccessibilityManagerService.this;
            r0 = r18;
            r14 = r4.getCompatibleMagnificationSpecLocked(r0);
            r0 = r19;	 Catch:{ RemoteException -> 0x0088, all -> 0x0099 }
            r10 = r0.mFetchFlags;	 Catch:{ RemoteException -> 0x0088, all -> 0x0099 }
            r4 = r21;	 Catch:{ RemoteException -> 0x0088, all -> 0x0099 }
            r6 = r23;	 Catch:{ RemoteException -> 0x0088, all -> 0x0099 }
            r8 = r24;	 Catch:{ RemoteException -> 0x0088, all -> 0x0099 }
            r9 = r25;	 Catch:{ RemoteException -> 0x0088, all -> 0x0099 }
            r12 = r26;	 Catch:{ RemoteException -> 0x0088, all -> 0x0099 }
            r3.findAccessibilityNodeInfosByViewId(r4, r6, r7, r8, r9, r10, r11, r12, r14);	 Catch:{ RemoteException -> 0x0088, all -> 0x0099 }
            r4 = 1;
            android.os.Binder.restoreCallingIdentity(r16);
            if (r7 == 0) goto L_0x0084;
        L_0x007b:
            r5 = android.os.Binder.isProxy(r3);
            if (r5 == 0) goto L_0x0084;
        L_0x0081:
            r7.recycle();
        L_0x0084:
            return r4;
        L_0x0085:
            r4 = move-exception;
            monitor-exit(r5);
            throw r4;
        L_0x0088:
            r15 = move-exception;
            android.os.Binder.restoreCallingIdentity(r16);
            if (r7 == 0) goto L_0x0097;
        L_0x008e:
            r4 = android.os.Binder.isProxy(r3);
            if (r4 == 0) goto L_0x0097;
        L_0x0094:
            r7.recycle();
        L_0x0097:
            r4 = 0;
            return r4;
        L_0x0099:
            r4 = move-exception;
            android.os.Binder.restoreCallingIdentity(r16);
            if (r7 == 0) goto L_0x00a8;
        L_0x009f:
            r5 = android.os.Binder.isProxy(r3);
            if (r5 == 0) goto L_0x00a8;
        L_0x00a5:
            r7.recycle();
        L_0x00a8:
            throw r4;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.server.accessibility.AccessibilityManagerService.Service.findAccessibilityNodeInfosByViewId(int, long, java.lang.String, int, android.view.accessibility.IAccessibilityInteractionConnectionCallback, long):boolean");
        }

        public boolean findFocus(int r22, long r23, int r25, int r26, android.view.accessibility.IAccessibilityInteractionConnectionCallback r27, long r28) throws android.os.RemoteException {
            /* JADX: method processing error */
/*
Error: jadx.core.utils.exceptions.JadxRuntimeException: Can't find block by offset: 0x009d in list []
	at jadx.core.utils.BlockUtils.getBlockByOffset(BlockUtils.java:43)
	at jadx.core.dex.instructions.IfNode.initBlocks(IfNode.java:60)
	at jadx.core.dex.visitors.blocksmaker.BlockFinish.initBlocksInIfNodes(BlockFinish.java:48)
	at jadx.core.dex.visitors.blocksmaker.BlockFinish.visit(BlockFinish.java:33)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:31)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:17)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:14)
	at jadx.core.ProcessClass.process(ProcessClass.java:37)
	at jadx.core.ProcessClass.processDependencies(ProcessClass.java:59)
	at jadx.core.ProcessClass.process(ProcessClass.java:42)
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:306)
	at jadx.api.JavaClass.decompile(JavaClass.java:62)
	at jadx.api.JadxDecompiler$1.run(JadxDecompiler.java:199)
*/
            /*
            r21 = this;
            r5 = 0;
            r9 = android.graphics.Region.obtain();
            r0 = r21;
            r6 = com.android.server.accessibility.AccessibilityManagerService.this;
            r7 = r6.mLock;
            monitor-enter(r7);
            r6 = r21.isCalledForCurrentUserLocked();
            if (r6 != 0) goto L_0x0017;
        L_0x0014:
            r6 = 0;
            monitor-exit(r7);
            return r6;
        L_0x0017:
            r0 = r21;
            r1 = r22;
            r2 = r25;
            r20 = r0.resolveAccessibilityWindowIdForFindFocusLocked(r1, r2);
            r0 = r21;
            r6 = com.android.server.accessibility.AccessibilityManagerService.this;
            r6 = r6.mSecurityPolicy;
            r0 = r21;
            r1 = r20;
            r4 = r6.canGetAccessibilityNodeInfoLocked(r0, r1);
            if (r4 != 0) goto L_0x0036;
        L_0x0033:
            r6 = 0;
            monitor-exit(r7);
            return r6;
        L_0x0036:
            r0 = r21;
            r1 = r20;
            r5 = r0.getConnectionLocked(r1);
            if (r5 != 0) goto L_0x0043;
        L_0x0040:
            r6 = 0;
            monitor-exit(r7);
            return r6;
        L_0x0043:
            r0 = r21;
            r6 = com.android.server.accessibility.AccessibilityManagerService.this;
            r6 = r6.mSecurityPolicy;
            r0 = r20;
            r6 = r6.computePartialInteractiveRegionForWindowLocked(r0, r9);
            if (r6 != 0) goto L_0x0057;
        L_0x0053:
            r9.recycle();
            r9 = 0;
        L_0x0057:
            monitor-exit(r7);
            r13 = android.os.Binder.getCallingPid();
            r18 = android.os.Binder.clearCallingIdentity();
            r0 = r21;
            r6 = com.android.server.accessibility.AccessibilityManagerService.this;
            r0 = r20;
            r16 = r6.getCompatibleMagnificationSpecLocked(r0);
            r0 = r21;	 Catch:{ RemoteException -> 0x008e, all -> 0x009f }
            r12 = r0.mFetchFlags;	 Catch:{ RemoteException -> 0x008e, all -> 0x009f }
            r6 = r23;	 Catch:{ RemoteException -> 0x008e, all -> 0x009f }
            r8 = r25;	 Catch:{ RemoteException -> 0x008e, all -> 0x009f }
            r10 = r26;	 Catch:{ RemoteException -> 0x008e, all -> 0x009f }
            r11 = r27;	 Catch:{ RemoteException -> 0x008e, all -> 0x009f }
            r14 = r28;	 Catch:{ RemoteException -> 0x008e, all -> 0x009f }
            r5.findFocus(r6, r8, r9, r10, r11, r12, r13, r14, r16);	 Catch:{ RemoteException -> 0x008e, all -> 0x009f }
            r6 = 1;
            android.os.Binder.restoreCallingIdentity(r18);
            if (r9 == 0) goto L_0x008a;
        L_0x0081:
            r7 = android.os.Binder.isProxy(r5);
            if (r7 == 0) goto L_0x008a;
        L_0x0087:
            r9.recycle();
        L_0x008a:
            return r6;
        L_0x008b:
            r6 = move-exception;
            monitor-exit(r7);
            throw r6;
        L_0x008e:
            r17 = move-exception;
            android.os.Binder.restoreCallingIdentity(r18);
            if (r9 == 0) goto L_0x009d;
        L_0x0094:
            r6 = android.os.Binder.isProxy(r5);
            if (r6 == 0) goto L_0x009d;
        L_0x009a:
            r9.recycle();
        L_0x009d:
            r6 = 0;
            return r6;
        L_0x009f:
            r6 = move-exception;
            android.os.Binder.restoreCallingIdentity(r18);
            if (r9 == 0) goto L_0x00ae;
        L_0x00a5:
            r7 = android.os.Binder.isProxy(r5);
            if (r7 == 0) goto L_0x00ae;
        L_0x00ab:
            r9.recycle();
        L_0x00ae:
            throw r6;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.server.accessibility.AccessibilityManagerService.Service.findFocus(int, long, int, int, android.view.accessibility.IAccessibilityInteractionConnectionCallback, long):boolean");
        }

        public boolean focusSearch(int r20, long r21, int r23, int r24, android.view.accessibility.IAccessibilityInteractionConnectionCallback r25, long r26) throws android.os.RemoteException {
            /* JADX: method processing error */
/*
Error: jadx.core.utils.exceptions.JadxRuntimeException: Can't find block by offset: 0x0097 in list []
	at jadx.core.utils.BlockUtils.getBlockByOffset(BlockUtils.java:43)
	at jadx.core.dex.instructions.IfNode.initBlocks(IfNode.java:60)
	at jadx.core.dex.visitors.blocksmaker.BlockFinish.initBlocksInIfNodes(BlockFinish.java:48)
	at jadx.core.dex.visitors.blocksmaker.BlockFinish.visit(BlockFinish.java:33)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:31)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:17)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:14)
	at jadx.core.ProcessClass.process(ProcessClass.java:37)
	at jadx.core.ProcessClass.processDependencies(ProcessClass.java:59)
	at jadx.core.ProcessClass.process(ProcessClass.java:42)
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:306)
	at jadx.api.JavaClass.decompile(JavaClass.java:62)
	at jadx.api.JadxDecompiler$1.run(JadxDecompiler.java:199)
*/
            /*
            r19 = this;
            r3 = 0;
            r7 = android.graphics.Region.obtain();
            r0 = r19;
            r4 = com.android.server.accessibility.AccessibilityManagerService.this;
            r5 = r4.mLock;
            monitor-enter(r5);
            r4 = r19.isCalledForCurrentUserLocked();
            if (r4 != 0) goto L_0x0017;
        L_0x0014:
            r4 = 0;
            monitor-exit(r5);
            return r4;
        L_0x0017:
            r18 = r19.resolveAccessibilityWindowIdLocked(r20);
            r0 = r19;
            r4 = com.android.server.accessibility.AccessibilityManagerService.this;
            r4 = r4.mSecurityPolicy;
            r0 = r19;
            r1 = r18;
            r2 = r4.canGetAccessibilityNodeInfoLocked(r0, r1);
            if (r2 != 0) goto L_0x0030;
        L_0x002d:
            r4 = 0;
            monitor-exit(r5);
            return r4;
        L_0x0030:
            r0 = r19;
            r1 = r18;
            r3 = r0.getConnectionLocked(r1);
            if (r3 != 0) goto L_0x003d;
        L_0x003a:
            r4 = 0;
            monitor-exit(r5);
            return r4;
        L_0x003d:
            r0 = r19;
            r4 = com.android.server.accessibility.AccessibilityManagerService.this;
            r4 = r4.mSecurityPolicy;
            r0 = r18;
            r4 = r4.computePartialInteractiveRegionForWindowLocked(r0, r7);
            if (r4 != 0) goto L_0x0051;
        L_0x004d:
            r7.recycle();
            r7 = 0;
        L_0x0051:
            monitor-exit(r5);
            r11 = android.os.Binder.getCallingPid();
            r16 = android.os.Binder.clearCallingIdentity();
            r0 = r19;
            r4 = com.android.server.accessibility.AccessibilityManagerService.this;
            r0 = r18;
            r14 = r4.getCompatibleMagnificationSpecLocked(r0);
            r0 = r19;	 Catch:{ RemoteException -> 0x0088, all -> 0x0099 }
            r10 = r0.mFetchFlags;	 Catch:{ RemoteException -> 0x0088, all -> 0x0099 }
            r4 = r21;	 Catch:{ RemoteException -> 0x0088, all -> 0x0099 }
            r6 = r23;	 Catch:{ RemoteException -> 0x0088, all -> 0x0099 }
            r8 = r24;	 Catch:{ RemoteException -> 0x0088, all -> 0x0099 }
            r9 = r25;	 Catch:{ RemoteException -> 0x0088, all -> 0x0099 }
            r12 = r26;	 Catch:{ RemoteException -> 0x0088, all -> 0x0099 }
            r3.focusSearch(r4, r6, r7, r8, r9, r10, r11, r12, r14);	 Catch:{ RemoteException -> 0x0088, all -> 0x0099 }
            r4 = 1;
            android.os.Binder.restoreCallingIdentity(r16);
            if (r7 == 0) goto L_0x0084;
        L_0x007b:
            r5 = android.os.Binder.isProxy(r3);
            if (r5 == 0) goto L_0x0084;
        L_0x0081:
            r7.recycle();
        L_0x0084:
            return r4;
        L_0x0085:
            r4 = move-exception;
            monitor-exit(r5);
            throw r4;
        L_0x0088:
            r15 = move-exception;
            android.os.Binder.restoreCallingIdentity(r16);
            if (r7 == 0) goto L_0x0097;
        L_0x008e:
            r4 = android.os.Binder.isProxy(r3);
            if (r4 == 0) goto L_0x0097;
        L_0x0094:
            r7.recycle();
        L_0x0097:
            r4 = 0;
            return r4;
        L_0x0099:
            r4 = move-exception;
            android.os.Binder.restoreCallingIdentity(r16);
            if (r7 == 0) goto L_0x00a8;
        L_0x009f:
            r5 = android.os.Binder.isProxy(r3);
            if (r5 == 0) goto L_0x00a8;
        L_0x00a5:
            r7.recycle();
        L_0x00a8:
            throw r4;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.server.accessibility.AccessibilityManagerService.Service.focusSearch(int, long, int, int, android.view.accessibility.IAccessibilityInteractionConnectionCallback, long):boolean");
        }

        public Service(int userId, ComponentName componentName, AccessibilityServiceInfo accessibilityServiceInfo) {
            this.mUserId = userId;
            this.mResolveInfo = accessibilityServiceInfo.getResolveInfo();
            int -get19 = AccessibilityManagerService.sIdCounter;
            AccessibilityManagerService.sIdCounter = -get19 + 1;
            this.mId = -get19;
            this.mComponentName = componentName;
            this.mAccessibilityServiceInfo = accessibilityServiceInfo;
            this.mIsAutomation = AccessibilityManagerService.sFakeAccessibilityServiceComponentName.equals(componentName);
            if (!this.mIsAutomation) {
                this.mIntent = new Intent().setComponent(this.mComponentName);
                this.mIntent.putExtra("android.intent.extra.client_label", 17040473);
                long idendtity = Binder.clearCallingIdentity();
                try {
                    this.mIntent.putExtra("android.intent.extra.client_intent", PendingIntent.getActivity(AccessibilityManagerService.this.mContext, 0, new Intent("android.settings.ACCESSIBILITY_SETTINGS"), 0));
                } finally {
                    Binder.restoreCallingIdentity(idendtity);
                }
            }
            setDynamicallyConfigurableProperties(accessibilityServiceInfo);
        }

        public void setDynamicallyConfigurableProperties(AccessibilityServiceInfo info) {
            boolean z;
            boolean z2 = true;
            this.mEventTypes = info.eventTypes;
            this.mFeedbackType = info.feedbackType;
            String[] packageNames = info.packageNames;
            if (packageNames != null) {
                this.mPackageNames.addAll(Arrays.asList(packageNames));
            }
            this.mNotificationTimeout = info.notificationTimeout;
            if ((info.flags & 1) != 0) {
                z = true;
            } else {
                z = false;
            }
            this.mIsDefault = z;
            if (this.mIsAutomation || info.getResolveInfo().serviceInfo.applicationInfo.targetSdkVersion >= 16) {
                if ((info.flags & 2) != 0) {
                    this.mFetchFlags |= 8;
                } else {
                    this.mFetchFlags &= -9;
                }
            }
            if ((info.flags & 16) != 0) {
                this.mFetchFlags |= 16;
            } else {
                this.mFetchFlags &= -17;
            }
            if ((info.flags & 4) != 0) {
                z = true;
            } else {
                z = false;
            }
            this.mRequestTouchExplorationMode = z;
            if ((info.flags & 8) != 0) {
                z = true;
            } else {
                z = false;
            }
            this.mRequestEnhancedWebAccessibility = z;
            if ((info.flags & 32) != 0) {
                z = true;
            } else {
                z = false;
            }
            this.mRequestFilterKeyEvents = z;
            if ((info.flags & 64) == 0) {
                z2 = false;
            }
            this.mRetrieveInteractiveWindows = z2;
        }

        public boolean bindLocked() {
            UserState userState = AccessibilityManagerService.this.getUserStateLocked(this.mUserId);
            if (this.mIsAutomation) {
                userState.mBindingServices.add(this.mComponentName);
                this.mService = userState.mUiAutomationServiceClient.asBinder();
                AccessibilityManagerService.this.mMainHandler.post(new Runnable() {
                    public void run() {
                        Service.this.onServiceConnected(Service.this.mComponentName, Service.this.mService);
                    }
                });
                userState.mUiAutomationService = this;
            } else {
                long identity = Binder.clearCallingIdentity();
                try {
                    if (this.mService == null && AccessibilityManagerService.this.mContext.bindServiceAsUser(this.mIntent, this, 33554433, new UserHandle(this.mUserId))) {
                        userState.mBindingServices.add(this.mComponentName);
                    }
                    Binder.restoreCallingIdentity(identity);
                } catch (Throwable th) {
                    Binder.restoreCallingIdentity(identity);
                }
            }
            return false;
        }

        public boolean unbindLocked() {
            if (this.mService == null) {
                return false;
            }
            UserState userState = AccessibilityManagerService.this.getUserStateLocked(this.mUserId);
            AccessibilityManagerService.this.getKeyEventDispatcher().flush(this);
            if (this.mIsAutomation) {
                userState.destroyUiAutomationService();
            } else {
                AccessibilityManagerService.this.mContext.unbindService(this);
            }
            AccessibilityManagerService.this.removeServiceLocked(this, userState);
            resetLocked();
            return true;
        }

        public void disableSelf() {
            synchronized (AccessibilityManagerService.this.mLock) {
                UserState userState = AccessibilityManagerService.this.getUserStateLocked(this.mUserId);
                if (userState.mEnabledServices.remove(this.mComponentName)) {
                    long identity = Binder.clearCallingIdentity();
                    try {
                        AccessibilityManagerService.this.persistComponentNamesToSettingLocked("enabled_accessibility_services", userState.mEnabledServices, this.mUserId);
                        AccessibilityManagerService.this.onUserStateChangedLocked(userState);
                    } finally {
                        Binder.restoreCallingIdentity(identity);
                    }
                }
            }
        }

        public boolean canReceiveEventsLocked() {
            return (this.mEventTypes == 0 || this.mFeedbackType == 0 || this.mService == null) ? false : true;
        }

        public void setOnKeyEventResult(boolean handled, int sequence) {
            AccessibilityManagerService.this.getKeyEventDispatcher().setOnKeyEventResult(this, handled, sequence);
        }

        public AccessibilityServiceInfo getServiceInfo() {
            AccessibilityServiceInfo accessibilityServiceInfo;
            synchronized (AccessibilityManagerService.this.mLock) {
                accessibilityServiceInfo = this.mAccessibilityServiceInfo;
            }
            return accessibilityServiceInfo;
        }

        public boolean canRetrieveInteractiveWindowsLocked() {
            if (AccessibilityManagerService.this.mSecurityPolicy.canRetrieveWindowContentLocked(this)) {
                return this.mRetrieveInteractiveWindows;
            }
            return false;
        }

        public void setServiceInfo(AccessibilityServiceInfo info) {
            long identity = Binder.clearCallingIdentity();
            try {
                synchronized (AccessibilityManagerService.this.mLock) {
                    AccessibilityServiceInfo oldInfo = this.mAccessibilityServiceInfo;
                    if (oldInfo != null) {
                        oldInfo.updateDynamicallyConfigurableProperties(info);
                        setDynamicallyConfigurableProperties(oldInfo);
                    } else {
                        setDynamicallyConfigurableProperties(info);
                    }
                    AccessibilityManagerService.this.onUserStateChangedLocked(AccessibilityManagerService.this.getUserStateLocked(this.mUserId));
                }
                Binder.restoreCallingIdentity(identity);
            } catch (Throwable th) {
                Binder.restoreCallingIdentity(identity);
            }
        }

        public void onServiceConnected(ComponentName componentName, IBinder service) {
            synchronized (AccessibilityManagerService.this.mLock) {
                this.mService = service;
                this.mServiceInterface = IAccessibilityServiceClient.Stub.asInterface(service);
                UserState userState = AccessibilityManagerService.this.getUserStateLocked(this.mUserId);
                AccessibilityManagerService.this.addServiceLocked(this, userState);
                if (userState.mBindingServices.contains(this.mComponentName) || this.mWasConnectedAndDied) {
                    userState.mBindingServices.remove(this.mComponentName);
                    this.mWasConnectedAndDied = false;
                    try {
                        this.mServiceInterface.init(this, this.mId, this.mOverlayWindowToken);
                        AccessibilityManagerService.this.onUserStateChangedLocked(userState);
                    } catch (RemoteException re) {
                        Slog.w(AccessibilityManagerService.LOG_TAG, "Error while setting connection for service: " + service, re);
                        binderDied();
                    }
                } else {
                    binderDied();
                }
            }
        }

        private boolean isCalledForCurrentUserLocked() {
            return AccessibilityManagerService.this.mSecurityPolicy.resolveCallingUserIdEnforcingPermissionsLocked(-2) == AccessibilityManagerService.this.mCurrentUserId;
        }

        public List<AccessibilityWindowInfo> getWindows() {
            AccessibilityManagerService.this.ensureWindowsAvailableTimed();
            synchronized (AccessibilityManagerService.this.mLock) {
                if (!isCalledForCurrentUserLocked()) {
                    return null;
                } else if (!AccessibilityManagerService.this.mSecurityPolicy.canRetrieveWindowsLocked(this)) {
                    return null;
                } else if (AccessibilityManagerService.this.mSecurityPolicy.mWindows == null) {
                    return null;
                } else {
                    List<AccessibilityWindowInfo> windows = new ArrayList();
                    int windowCount = AccessibilityManagerService.this.mSecurityPolicy.mWindows.size();
                    for (int i = 0; i < windowCount; i++) {
                        AccessibilityWindowInfo windowClone = AccessibilityWindowInfo.obtain((AccessibilityWindowInfo) AccessibilityManagerService.this.mSecurityPolicy.mWindows.get(i));
                        windowClone.setConnectionId(this.mId);
                        windows.add(windowClone);
                    }
                    return windows;
                }
            }
        }

        public AccessibilityWindowInfo getWindow(int windowId) {
            AccessibilityManagerService.this.ensureWindowsAvailableTimed();
            synchronized (AccessibilityManagerService.this.mLock) {
                if (!isCalledForCurrentUserLocked()) {
                    return null;
                } else if (AccessibilityManagerService.this.mSecurityPolicy.canRetrieveWindowsLocked(this)) {
                    AccessibilityWindowInfo window = AccessibilityManagerService.this.mSecurityPolicy.findWindowById(windowId);
                    if (window != null) {
                        AccessibilityWindowInfo windowClone = AccessibilityWindowInfo.obtain(window);
                        windowClone.setConnectionId(this.mId);
                        return windowClone;
                    }
                    return null;
                } else {
                    return null;
                }
            }
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public void sendGesture(int sequence, ParceledListSlice gestureSteps) {
            synchronized (AccessibilityManagerService.this.mLock) {
                if (AccessibilityManagerService.this.mSecurityPolicy.canPerformGestures(this)) {
                    long endMillis = SystemClock.uptimeMillis() + 1000;
                    while (AccessibilityManagerService.this.mMotionEventInjector == null && SystemClock.uptimeMillis() < endMillis) {
                        try {
                            AccessibilityManagerService.this.mLock.wait(endMillis - SystemClock.uptimeMillis());
                        } catch (InterruptedException e) {
                        }
                    }
                    if (AccessibilityManagerService.this.mMotionEventInjector != null) {
                        List<MotionEvent> events = MotionEventGenerator.getMotionEventsFromGestureSteps(gestureSteps.getList());
                        if (((MotionEvent) events.get(events.size() - 1)).getAction() == 1) {
                            AccessibilityManagerService.this.mMotionEventInjector.injectEvents(events, this.mServiceInterface, sequence);
                            return;
                        }
                        Slog.e(AccessibilityManagerService.LOG_TAG, "Gesture is not well-formed");
                    } else {
                        Slog.e(AccessibilityManagerService.LOG_TAG, "MotionEventInjector installation timed out");
                    }
                }
            }
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public boolean performAccessibilityAction(int accessibilityWindowId, long accessibilityNodeId, int action, Bundle arguments, int interactionId, IAccessibilityInteractionConnectionCallback callback, long interrogatingTid) throws RemoteException {
            synchronized (AccessibilityManagerService.this.mLock) {
                if (isCalledForCurrentUserLocked()) {
                    int resolvedWindowId = resolveAccessibilityWindowIdLocked(accessibilityWindowId);
                    if (AccessibilityManagerService.this.mSecurityPolicy.canGetAccessibilityNodeInfoLocked(this, resolvedWindowId)) {
                        IAccessibilityInteractionConnection connection = getConnectionLocked(resolvedWindowId);
                        if (connection == null) {
                            return false;
                        }
                    }
                    return false;
                }
                return false;
            }
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public boolean performGlobalAction(int action) {
            synchronized (AccessibilityManagerService.this.mLock) {
                if (!isCalledForCurrentUserLocked()) {
                    return false;
                }
            }
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public float getMagnificationScale() {
            synchronized (AccessibilityManagerService.this.mLock) {
                if (!isCalledForCurrentUserLocked()) {
                    return 1.0f;
                }
            }
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public Region getMagnificationRegion() {
            synchronized (AccessibilityManagerService.this.mLock) {
                Region region = Region.obtain();
                if (isCalledForCurrentUserLocked()) {
                    magnificationController = AccessibilityManagerService.this.getMagnificationController();
                    boolean forceRegistration = AccessibilityManagerService.this.mSecurityPolicy.canControlMagnification(this);
                    initiallyRegistered = magnificationController.isRegisteredLocked();
                    if (!initiallyRegistered && forceRegistration) {
                        magnificationController.register();
                    }
                    long identity = Binder.clearCallingIdentity();
                    try {
                        magnificationController.getMagnificationRegion(region);
                    } finally {
                        Binder.restoreCallingIdentity(identity);
                        boolean initiallyRegistered;
                        if (!initiallyRegistered && forceRegistration) {
                            MagnificationController magnificationController;
                            magnificationController.unregister();
                        }
                    }
                } else {
                    return region;
                }
            }
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public float getMagnificationCenterX() {
            synchronized (AccessibilityManagerService.this.mLock) {
                if (!isCalledForCurrentUserLocked()) {
                    return 0.0f;
                }
            }
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public float getMagnificationCenterY() {
            synchronized (AccessibilityManagerService.this.mLock) {
                if (!isCalledForCurrentUserLocked()) {
                    return 0.0f;
                }
            }
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public boolean resetMagnification(boolean animate) {
            synchronized (AccessibilityManagerService.this.mLock) {
                if (!isCalledForCurrentUserLocked()) {
                    return false;
                } else if (!AccessibilityManagerService.this.mSecurityPolicy.canControlMagnification(this)) {
                    return false;
                }
            }
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public boolean setMagnificationScaleAndCenter(float scale, float centerX, float centerY, boolean animate) {
            synchronized (AccessibilityManagerService.this.mLock) {
                if (!isCalledForCurrentUserLocked()) {
                    return false;
                } else if (AccessibilityManagerService.this.mSecurityPolicy.canControlMagnification(this)) {
                    long identity = Binder.clearCallingIdentity();
                    try {
                        MagnificationController magnificationController = AccessibilityManagerService.this.getMagnificationController();
                        if (!magnificationController.isRegisteredLocked()) {
                            magnificationController.register();
                        }
                        boolean scaleAndCenter = magnificationController.setScaleAndCenter(scale, centerX, centerY, animate, this.mId);
                    } finally {
                        Binder.restoreCallingIdentity(identity);
                    }
                } else {
                    return false;
                }
            }
        }

        public void setMagnificationCallbackEnabled(boolean enabled) {
            this.mInvocationHandler.setMagnificationCallbackEnabled(enabled);
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public boolean setSoftKeyboardShowMode(int showMode) {
            synchronized (AccessibilityManagerService.this.mLock) {
                if (isCalledForCurrentUserLocked()) {
                    UserState userState = AccessibilityManagerService.this.getCurrentUserStateLocked();
                } else {
                    return false;
                }
            }
        }

        public void setSoftKeyboardCallbackEnabled(boolean enabled) {
            this.mInvocationHandler.setSoftKeyboardCallbackEnabled(enabled);
        }

        public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            AccessibilityManagerService.this.mSecurityPolicy.enforceCallingPermission("android.permission.DUMP", AccessibilityManagerService.FUNCTION_DUMP);
            synchronized (AccessibilityManagerService.this.mLock) {
                if (this.mAccessibilityServiceInfo.getResolveInfo() != null) {
                    pw.append("Service[label=" + this.mAccessibilityServiceInfo.getResolveInfo().loadLabel(AccessibilityManagerService.this.mContext.getPackageManager()));
                } else {
                    Slog.w(AccessibilityManagerService.LOG_TAG, "dump() mAccessibilityServiceInfo.getResolveInfo() is null");
                    pw.append("Service[label=null");
                }
                pw.append(", feedbackType" + AccessibilityServiceInfo.feedbackTypeToString(this.mFeedbackType));
                pw.append(", capabilities=" + this.mAccessibilityServiceInfo.getCapabilities());
                pw.append(", eventTypes=" + AccessibilityEvent.eventTypeToString(this.mEventTypes));
                pw.append(", notificationTimeout=" + this.mNotificationTimeout);
                pw.append("]");
            }
        }

        public void onServiceDisconnected(ComponentName componentName) {
        }

        public void onAdded() throws RemoteException {
            linkToOwnDeathLocked();
            long identity = Binder.clearCallingIdentity();
            try {
                AccessibilityManagerService.this.mWindowManagerService.addWindowToken(this.mOverlayWindowToken, 2032);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        public void onRemoved() {
            long identity = Binder.clearCallingIdentity();
            try {
                AccessibilityManagerService.this.mWindowManagerService.removeWindowToken(this.mOverlayWindowToken, true);
                unlinkToOwnDeathLocked();
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        public void linkToOwnDeathLocked() throws RemoteException {
            this.mService.linkToDeath(this, 0);
        }

        public void unlinkToOwnDeathLocked() {
            try {
                this.mService.unlinkToDeath(this, 0);
            } catch (NoSuchElementException e) {
                Slog.w(AccessibilityManagerService.LOG_TAG, "unlinkToDeath: " + this + ":", e);
            }
        }

        public void resetLocked() {
            try {
                this.mServiceInterface.init(null, this.mId, null);
            } catch (RemoteException e) {
            }
            this.mService = null;
            this.mServiceInterface = null;
        }

        public boolean isConnectedLocked() {
            return this.mService != null;
        }

        public void binderDied() {
            synchronized (AccessibilityManagerService.this.mLock) {
                if (isConnectedLocked()) {
                    this.mWasConnectedAndDied = true;
                    AccessibilityManagerService.this.getKeyEventDispatcher().flush(this);
                    UserState userState = AccessibilityManagerService.this.getUserStateLocked(this.mUserId);
                    AccessibilityManagerService.this.removeServiceLocked(this, userState);
                    resetLocked();
                    if (this.mIsAutomation) {
                        userState.mInstalledServices.remove(this.mAccessibilityServiceInfo);
                        userState.mEnabledServices.remove(this.mComponentName);
                        userState.destroyUiAutomationService();
                        AccessibilityManagerService.this.readConfigurationForUserStateLocked(userState);
                    }
                    if (this.mId == AccessibilityManagerService.this.getMagnificationController().getIdOfLastServiceToMagnify()) {
                        AccessibilityManagerService.this.getMagnificationController().resetIfNeeded(true);
                    }
                    AccessibilityManagerService.this.onUserStateChangedLocked(userState);
                    return;
                }
            }
        }

        public void notifyAccessibilityEvent(AccessibilityEvent event) {
            synchronized (AccessibilityManagerService.this.mLock) {
                Message message;
                int eventType = event.getEventType();
                AccessibilityEvent newEvent = AccessibilityEvent.obtain(event);
                if (this.mNotificationTimeout <= 0 || eventType == 2048) {
                    message = this.mEventDispatchHandler.obtainMessage(eventType, newEvent);
                } else {
                    AccessibilityEvent oldEvent = (AccessibilityEvent) this.mPendingEvents.get(eventType);
                    this.mPendingEvents.put(eventType, newEvent);
                    if (oldEvent != null) {
                        this.mEventDispatchHandler.removeMessages(eventType);
                        oldEvent.recycle();
                    }
                    message = this.mEventDispatchHandler.obtainMessage(eventType);
                }
                this.mEventDispatchHandler.sendMessageDelayed(message, this.mNotificationTimeout);
            }
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        private void notifyAccessibilityEventInternal(int eventType, AccessibilityEvent event) {
            synchronized (AccessibilityManagerService.this.mLock) {
                IAccessibilityServiceClient listener = this.mServiceInterface;
                if (listener == null) {
                    return;
                }
                if (event == null) {
                    event = (AccessibilityEvent) this.mPendingEvents.get(eventType);
                    if (event == null) {
                        return;
                    }
                    this.mPendingEvents.remove(eventType);
                }
                if (AccessibilityManagerService.this.mSecurityPolicy.canRetrieveWindowContentLocked(this)) {
                    event.setConnectionId(this.mId);
                } else {
                    event.setSource(null);
                }
                event.setSealed(true);
            }
        }

        public void notifyGesture(int gestureId) {
            this.mInvocationHandler.obtainMessage(1, gestureId, 0).sendToTarget();
        }

        public void notifyClearAccessibilityNodeInfoCache() {
            this.mInvocationHandler.sendEmptyMessage(2);
        }

        public void notifyMagnificationChangedLocked(Region region, float scale, float centerX, float centerY) {
            this.mInvocationHandler.notifyMagnificationChangedLocked(region, scale, centerX, centerY);
        }

        public void notifySoftKeyboardShowModeChangedLocked(int showState) {
            this.mInvocationHandler.notifySoftKeyboardShowModeChangedLocked(showState);
        }

        private void notifyMagnificationChangedInternal(Region region, float scale, float centerX, float centerY) {
            synchronized (AccessibilityManagerService.this.mLock) {
                IAccessibilityServiceClient listener = this.mServiceInterface;
            }
            if (listener != null) {
                try {
                    listener.onMagnificationChanged(region, scale, centerX, centerY);
                } catch (RemoteException re) {
                    Slog.e(AccessibilityManagerService.LOG_TAG, "Error sending magnification changes to " + this.mService, re);
                }
            }
        }

        private void notifySoftKeyboardShowModeChangedInternal(int showState) {
            synchronized (AccessibilityManagerService.this.mLock) {
                IAccessibilityServiceClient listener = this.mServiceInterface;
            }
            if (listener != null) {
                try {
                    listener.onSoftKeyboardShowModeChanged(showState);
                } catch (RemoteException re) {
                    Slog.e(AccessibilityManagerService.LOG_TAG, "Error sending soft keyboard show mode changes to " + this.mService, re);
                }
            }
        }

        private void notifyGestureInternal(int gestureId) {
            synchronized (AccessibilityManagerService.this.mLock) {
                IAccessibilityServiceClient listener = this.mServiceInterface;
            }
            if (listener != null) {
                try {
                    listener.onGesture(gestureId);
                } catch (RemoteException re) {
                    Slog.e(AccessibilityManagerService.LOG_TAG, "Error during sending gesture " + gestureId + " to " + this.mService, re);
                }
            }
        }

        private void notifyClearAccessibilityCacheInternal() {
            synchronized (AccessibilityManagerService.this.mLock) {
                IAccessibilityServiceClient listener = this.mServiceInterface;
            }
            if (listener != null) {
                try {
                    listener.clearAccessibilityCache();
                } catch (RemoteException re) {
                    Slog.e(AccessibilityManagerService.LOG_TAG, "Error during requesting accessibility info cache to be cleared.", re);
                }
            }
        }

        private void sendDownAndUpKeyEvents(int keyCode) {
            long token = Binder.clearCallingIdentity();
            long downTime = SystemClock.uptimeMillis();
            KeyEvent down = KeyEvent.obtain(downTime, downTime, 0, keyCode, 0, 0, -1, 0, 8, 257, null);
            InputManager.getInstance().injectInputEvent(down, 0);
            down.recycle();
            InputEvent up = KeyEvent.obtain(downTime, SystemClock.uptimeMillis(), 1, keyCode, 0, 0, -1, 0, 8, 257, null);
            InputManager.getInstance().injectInputEvent(up, 0);
            up.recycle();
            Binder.restoreCallingIdentity(token);
        }

        private void expandNotifications() {
            long token = Binder.clearCallingIdentity();
            ((StatusBarManager) AccessibilityManagerService.this.mContext.getSystemService("statusbar")).expandNotificationsPanel();
            Binder.restoreCallingIdentity(token);
        }

        private void expandQuickSettings() {
            long token = Binder.clearCallingIdentity();
            ((StatusBarManager) AccessibilityManagerService.this.mContext.getSystemService("statusbar")).expandSettingsPanel();
            Binder.restoreCallingIdentity(token);
        }

        private void openRecents() {
            long token = Binder.clearCallingIdentity();
            ((StatusBarManagerInternal) LocalServices.getService(StatusBarManagerInternal.class)).toggleRecentApps();
            Binder.restoreCallingIdentity(token);
        }

        private void showGlobalActions() {
            AccessibilityManagerService.this.mWindowManagerService.showGlobalActions();
        }

        private void toggleSplitScreen() {
            ((StatusBarManagerInternal) LocalServices.getService(StatusBarManagerInternal.class)).toggleSplitScreen();
        }

        private IAccessibilityInteractionConnection getConnectionLocked(int windowId) {
            AccessibilityConnectionWrapper wrapper = (AccessibilityConnectionWrapper) AccessibilityManagerService.this.mGlobalInteractionConnections.get(windowId);
            if (wrapper == null) {
                wrapper = (AccessibilityConnectionWrapper) AccessibilityManagerService.this.getCurrentUserStateLocked().mInteractionConnections.get(windowId);
            }
            if (wrapper == null || wrapper.mConnection == null) {
                return null;
            }
            return wrapper.mConnection;
        }

        private int resolveAccessibilityWindowIdLocked(int accessibilityWindowId) {
            if (accessibilityWindowId == Integer.MAX_VALUE) {
                return AccessibilityManagerService.this.mSecurityPolicy.getActiveWindowId();
            }
            return accessibilityWindowId;
        }

        private int resolveAccessibilityWindowIdForFindFocusLocked(int windowId, int focusType) {
            if (windowId == Integer.MAX_VALUE) {
                return AccessibilityManagerService.this.mSecurityPolicy.mActiveWindowId;
            }
            if (windowId == -2) {
                if (focusType == 1) {
                    return AccessibilityManagerService.this.mSecurityPolicy.mFocusedWindowId;
                }
                if (focusType == 2) {
                    return AccessibilityManagerService.this.mSecurityPolicy.mAccessibilityFocusedWindowId;
                }
            }
            return windowId;
        }
    }

    private class SettingsStringHelper {
        private static final String SETTINGS_DELIMITER = ":";
        private ContentResolver mContentResolver;
        private Set<String> mServices = new HashSet();
        private final String mSettingsName;
        private final int mUserId;

        public SettingsStringHelper(String name, int userId) {
            this.mUserId = userId;
            this.mSettingsName = name;
            this.mContentResolver = AccessibilityManagerService.this.mContext.getContentResolver();
            String servicesString = Secure.getStringForUser(this.mContentResolver, this.mSettingsName, userId);
            if (!TextUtils.isEmpty(servicesString)) {
                SimpleStringSplitter colonSplitter = new SimpleStringSplitter(SETTINGS_DELIMITER.charAt(0));
                colonSplitter.setString(servicesString);
                while (colonSplitter.hasNext()) {
                    this.mServices.add(colonSplitter.next());
                }
            }
        }

        public void addService(ComponentName component) {
            this.mServices.add(component.flattenToString());
        }

        public void deleteService(ComponentName component) {
            this.mServices.remove(component.flattenToString());
        }

        public void writeToSettings() {
            Secure.putStringForUser(this.mContentResolver, this.mSettingsName, TextUtils.join(SETTINGS_DELIMITER, this.mServices), this.mUserId);
        }
    }

    private class UserState {
        public boolean mAccessibilityFocusOnlyInActiveWindow;
        public final Set<ComponentName> mBindingServices = new HashSet();
        public final CopyOnWriteArrayList<Service> mBoundServices = new CopyOnWriteArrayList();
        public final RemoteCallbackList<IAccessibilityManagerClient> mClients = new RemoteCallbackList();
        public final Map<ComponentName, Service> mComponentNameToServiceMap = new HashMap();
        public final Set<ComponentName> mEnabledServices = new HashSet();
        public boolean mHasDisplayColorAdjustment;
        public final List<AccessibilityServiceInfo> mInstalledServices = new ArrayList();
        public final SparseArray<AccessibilityConnectionWrapper> mInteractionConnections = new SparseArray();
        public boolean mIsAutoclickEnabled;
        public boolean mIsDisplayMagnificationEnabled;
        public boolean mIsEnhancedWebAccessibilityEnabled;
        public boolean mIsFilterKeyEventsEnabled;
        public boolean mIsPerformGesturesEnabled;
        public boolean mIsTextHighContrastEnabled;
        public boolean mIsTouchExplorationEnabled;
        public int mLastSentClientState = -1;
        public ComponentName mServiceChangingSoftKeyboardMode;
        public int mSoftKeyboardShowMode = 0;
        public final Set<ComponentName> mTouchExplorationGrantedServices = new HashSet();
        private int mUiAutomationFlags;
        private final DeathRecipient mUiAutomationSerivceOnwerDeathRecipient = new DeathRecipient() {
            public void binderDied() {
                UserState.this.mUiAutomationServiceOwner.unlinkToDeath(UserState.this.mUiAutomationSerivceOnwerDeathRecipient, 0);
                UserState.this.mUiAutomationServiceOwner = null;
                if (UserState.this.mUiAutomationService != null) {
                    UserState.this.mUiAutomationService.binderDied();
                }
            }
        };
        private Service mUiAutomationService;
        private IAccessibilityServiceClient mUiAutomationServiceClient;
        private IBinder mUiAutomationServiceOwner;
        public final int mUserId;
        public final SparseArray<IBinder> mWindowTokens = new SparseArray();

        public UserState(int userId) {
            this.mUserId = userId;
        }

        public int getClientState() {
            int clientState = 0;
            if (isHandlingAccessibilityEvents()) {
                clientState = 1;
            }
            if (isHandlingAccessibilityEvents() && this.mIsTouchExplorationEnabled) {
                clientState |= 2;
            }
            if (this.mIsTextHighContrastEnabled) {
                return clientState | 4;
            }
            return clientState;
        }

        public boolean isHandlingAccessibilityEvents() {
            return (this.mBoundServices.isEmpty() && this.mBindingServices.isEmpty()) ? false : true;
        }

        public void onSwitchToAnotherUser() {
            if (this.mUiAutomationService != null) {
                this.mUiAutomationService.binderDied();
            }
            AccessibilityManagerService.this.unbindAllServicesLocked(this);
            this.mBoundServices.clear();
            this.mBindingServices.clear();
            this.mLastSentClientState = -1;
            this.mEnabledServices.clear();
            this.mTouchExplorationGrantedServices.clear();
            this.mIsTouchExplorationEnabled = false;
            this.mIsEnhancedWebAccessibilityEnabled = false;
            this.mIsDisplayMagnificationEnabled = false;
            this.mIsAutoclickEnabled = false;
            this.mSoftKeyboardShowMode = 0;
        }

        public void destroyUiAutomationService() {
            this.mUiAutomationService = null;
            this.mUiAutomationFlags = 0;
            this.mUiAutomationServiceClient = null;
            if (this.mUiAutomationServiceOwner != null) {
                this.mUiAutomationServiceOwner.unlinkToDeath(this.mUiAutomationSerivceOnwerDeathRecipient, 0);
                this.mUiAutomationServiceOwner = null;
            }
        }

        boolean isUiAutomationSuppressingOtherServices() {
            return this.mUiAutomationService != null && (this.mUiAutomationFlags & 1) == 0;
        }
    }

    final class WindowsForAccessibilityCallback implements android.view.WindowManagerInternal.WindowsForAccessibilityCallback {
        WindowsForAccessibilityCallback() {
        }

        public void onWindowsForAccessibilityChanged(List<WindowInfo> windows) {
            synchronized (AccessibilityManagerService.this.mLock) {
                List<AccessibilityWindowInfo> reportedWindows = new ArrayList();
                int receivedWindowCount = windows.size();
                for (int i = 0; i < receivedWindowCount; i++) {
                    AccessibilityWindowInfo reportedWindow = populateReportedWindow((WindowInfo) windows.get(i));
                    if (reportedWindow != null) {
                        reportedWindows.add(reportedWindow);
                    }
                }
                AccessibilityManagerService.this.mSecurityPolicy.updateWindowsLocked(reportedWindows);
                AccessibilityManagerService.this.mLock.notifyAll();
            }
        }

        private AccessibilityWindowInfo populateReportedWindow(WindowInfo window) {
            int windowId = AccessibilityManagerService.this.findWindowIdLocked(window.token);
            if (windowId < 0) {
                return null;
            }
            AccessibilityWindowInfo reportedWindow = AccessibilityWindowInfo.obtain();
            reportedWindow.setId(windowId);
            reportedWindow.setType(getTypeForWindowManagerWindowType(window.type));
            reportedWindow.setLayer(window.layer);
            reportedWindow.setFocused(window.focused);
            reportedWindow.setBoundsInScreen(window.boundsInScreen);
            reportedWindow.setTitle(window.title);
            reportedWindow.setAnchorId(window.accessibilityIdOfAnchor);
            int parentId = AccessibilityManagerService.this.findWindowIdLocked(window.parentToken);
            if (parentId >= 0) {
                reportedWindow.setParentId(parentId);
            }
            if (window.childTokens != null) {
                int childCount = window.childTokens.size();
                for (int i = 0; i < childCount; i++) {
                    int childId = AccessibilityManagerService.this.findWindowIdLocked((IBinder) window.childTokens.get(i));
                    if (childId >= 0) {
                        reportedWindow.addChild(childId);
                    }
                }
            }
            return reportedWindow;
        }

        private int getTypeForWindowManagerWindowType(int windowType) {
            switch (windowType) {
                case 1:
                case 2:
                case 3:
                case 1000:
                case 1001:
                case 1002:
                case 1003:
                case 1005:
                case 2002:
                case 2005:
                case 2007:
                    return 1;
                case IHwShutdownThread.SHUTDOWN_ANIMATION_WAIT_TIME /*2000*/:
                case 2001:
                case 2003:
                case 2006:
                case 2008:
                case 2009:
                case 2010:
                case 2014:
                case 2017:
                case 2019:
                case 2020:
                case 2024:
                case 2036:
                    return 3;
                case 2011:
                case 2012:
                    return 2;
                case 2032:
                    return 4;
                case 2034:
                    return 5;
                default:
                    return -1;
            }
        }
    }

    public java.util.List<android.accessibilityservice.AccessibilityServiceInfo> getEnabledAccessibilityServiceList(int r1, int r2) {
        /* JADX: method processing error */
/*
Error: jadx.core.utils.exceptions.DecodeException: Load method exception in method: com.android.server.accessibility.AccessibilityManagerService.getEnabledAccessibilityServiceList(int, int):java.util.List<android.accessibilityservice.AccessibilityServiceInfo>
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
        throw new UnsupportedOperationException("Method not decompiled: com.android.server.accessibility.AccessibilityManagerService.getEnabledAccessibilityServiceList(int, int):java.util.List<android.accessibilityservice.AccessibilityServiceInfo>");
    }

    private UserState getCurrentUserStateLocked() {
        return getUserStateLocked(this.mCurrentUserId);
    }

    public AccessibilityManagerService(Context context) {
        this.mContext = context;
        this.mPackageManager = this.mContext.getPackageManager();
        this.mPowerManager = (PowerManager) this.mContext.getSystemService("power");
        this.mWindowManagerService = (WindowManagerInternal) LocalServices.getService(WindowManagerInternal.class);
        this.mUserManager = (UserManager) context.getSystemService("user");
        this.mSecurityPolicy = new SecurityPolicy();
        this.mMainHandler = new MainHandler(this.mContext.getMainLooper());
        registerBroadcastReceivers();
        new AccessibilityContentObserver(this.mMainHandler).register(context.getContentResolver());
    }

    private UserState getUserStateLocked(int userId) {
        UserState state = (UserState) this.mUserStates.get(userId);
        if (state != null) {
            return state;
        }
        state = new UserState(userId);
        this.mUserStates.put(userId, state);
        return state;
    }

    private void registerBroadcastReceivers() {
        new PackageMonitor() {
            /* JADX WARNING: inconsistent code. */
            /* Code decompiled incorrectly, please refer to instructions dump. */
            public void onSomePackagesChanged() {
                synchronized (AccessibilityManagerService.this.mLock) {
                    if (getChangingUserId() != AccessibilityManagerService.this.mCurrentUserId) {
                        return;
                    }
                    UserState userState = AccessibilityManagerService.this.getCurrentUserStateLocked();
                    userState.mInstalledServices.clear();
                    if (!userState.isUiAutomationSuppressingOtherServices() && AccessibilityManagerService.this.readConfigurationForUserStateLocked(userState)) {
                        AccessibilityManagerService.this.onUserStateChangedLocked(userState);
                    }
                }
            }

            /* JADX WARNING: inconsistent code. */
            /* Code decompiled incorrectly, please refer to instructions dump. */
            public void onPackageRemoved(String packageName, int uid) {
                synchronized (AccessibilityManagerService.this.mLock) {
                    int userId = getChangingUserId();
                    if (userId != AccessibilityManagerService.this.mCurrentUserId) {
                        return;
                    }
                    UserState userState = AccessibilityManagerService.this.getUserStateLocked(userId);
                    Iterator<ComponentName> it = userState.mEnabledServices.iterator();
                    while (it.hasNext()) {
                        ComponentName comp = (ComponentName) it.next();
                        if (comp.getPackageName().equals(packageName)) {
                            it.remove();
                            AccessibilityManagerService.this.persistComponentNamesToSettingLocked("enabled_accessibility_services", userState.mEnabledServices, userId);
                            userState.mTouchExplorationGrantedServices.remove(comp);
                            AccessibilityManagerService.this.persistComponentNamesToSettingLocked("touch_exploration_granted_accessibility_services", userState.mTouchExplorationGrantedServices, userId);
                            if (!userState.isUiAutomationSuppressingOtherServices()) {
                                AccessibilityManagerService.this.onUserStateChangedLocked(userState);
                            }
                        }
                    }
                }
            }

            public boolean onHandleForceStop(Intent intent, String[] packages, int uid, boolean doit) {
                synchronized (AccessibilityManagerService.this.mLock) {
                    int userId = getChangingUserId();
                    if (userId != AccessibilityManagerService.this.mCurrentUserId) {
                        return false;
                    }
                    UserState userState = AccessibilityManagerService.this.getUserStateLocked(userId);
                    Iterator<ComponentName> it = userState.mEnabledServices.iterator();
                    while (it.hasNext()) {
                        String compPkg = ((ComponentName) it.next()).getPackageName();
                        for (String pkg : packages) {
                            if (compPkg.equals(pkg)) {
                                if (doit) {
                                    it.remove();
                                    AccessibilityManagerService.this.persistComponentNamesToSettingLocked("enabled_accessibility_services", userState.mEnabledServices, userId);
                                    if (!userState.isUiAutomationSuppressingOtherServices()) {
                                        AccessibilityManagerService.this.onUserStateChangedLocked(userState);
                                    }
                                } else {
                                    return true;
                                }
                            }
                        }
                    }
                    return false;
                }
            }
        }.register(this.mContext, null, UserHandle.ALL, true);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.USER_SWITCHED");
        intentFilter.addAction("android.intent.action.USER_UNLOCKED");
        intentFilter.addAction("android.intent.action.USER_REMOVED");
        intentFilter.addAction("android.intent.action.USER_PRESENT");
        intentFilter.addAction("android.os.action.SETTING_RESTORED");
        intentFilter.addAction("android.intent.action.LOCKED_BOOT_COMPLETED");
        this.mContext.registerReceiverAsUser(new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if ("android.intent.action.USER_SWITCHED".equals(action)) {
                    AccessibilityManagerService.this.switchUser(intent.getIntExtra("android.intent.extra.user_handle", 0));
                } else if ("android.intent.action.LOCKED_BOOT_COMPLETED".equals(action)) {
                    userState = AccessibilityManagerService.this.getCurrentUserStateLocked();
                    AccessibilityManagerService.this.readConfigurationForUserStateLocked(userState);
                    AccessibilityManagerService.this.onUserStateChangedLocked(userState);
                } else if ("android.intent.action.USER_UNLOCKED".equals(action)) {
                    AccessibilityManagerService.this.unlockUser(intent.getIntExtra("android.intent.extra.user_handle", 0));
                } else if ("android.intent.action.USER_REMOVED".equals(action)) {
                    AccessibilityManagerService.this.removeUser(intent.getIntExtra("android.intent.extra.user_handle", 0));
                } else {
                    Object -get7;
                    if ("android.intent.action.USER_PRESENT".equals(action)) {
                        userState = AccessibilityManagerService.this.getCurrentUserStateLocked();
                        if (!userState.isUiAutomationSuppressingOtherServices()) {
                            -get7 = AccessibilityManagerService.this.mLock;
                            synchronized (-get7) {
                                if (AccessibilityManagerService.this.readConfigurationForUserStateLocked(userState)) {
                                    AccessibilityManagerService.this.onUserStateChangedLocked(userState);
                                }
                            }
                        } else {
                            return;
                        }
                    } else if ("android.os.action.SETTING_RESTORED".equals(action)) {
                        if ("enabled_accessibility_services".equals(intent.getStringExtra("setting_name"))) {
                            -get7 = AccessibilityManagerService.this.mLock;
                            synchronized (-get7) {
                                AccessibilityManagerService.this.restoreEnabledAccessibilityServicesLocked(intent.getStringExtra("previous_value"), intent.getStringExtra("new_value"));
                            }
                        } else {
                            return;
                        }
                    } else {
                        return;
                    }
                }
            }
        }, UserHandle.ALL, intentFilter, null, null);
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public int addClient(IAccessibilityManagerClient client, int userId) {
        synchronized (this.mLock) {
            int resolvedUserId = this.mSecurityPolicy.resolveCallingUserIdEnforcingPermissionsLocked(userId);
            UserState userState = getUserStateLocked(resolvedUserId);
            if (this.mSecurityPolicy.isCallerInteractingAcrossUsers(userId)) {
                this.mGlobalClients.register(client);
                int clientState = userState.getClientState();
                return clientState;
            }
            userState.mClients.register(client);
            clientState = resolvedUserId == this.mCurrentUserId ? userState.getClientState() : 0;
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public boolean sendAccessibilityEvent(AccessibilityEvent event, int userId) {
        synchronized (this.mLock) {
            if (this.mSecurityPolicy.resolveCallingUserIdEnforcingPermissionsLocked(userId) != this.mCurrentUserId) {
                return true;
            }
            if (this.mSecurityPolicy.canDispatchAccessibilityEventLocked(event)) {
                this.mSecurityPolicy.updateActiveAndAccessibilityFocusedWindowLocked(event.getWindowId(), event.getSourceNodeId(), event.getEventType(), event.getAction());
                this.mSecurityPolicy.updateEventSourceLocked(event);
                notifyAccessibilityServicesDelayedLocked(event, false);
                notifyAccessibilityServicesDelayedLocked(event, true);
            }
            if (this.mHasInputFilter && this.mInputFilter != null) {
                this.mMainHandler.obtainMessage(1, AccessibilityEvent.obtain(event)).sendToTarget();
            }
            event.recycle();
        }
    }

    public List<AccessibilityServiceInfo> getInstalledAccessibilityServiceList(int userId) {
        synchronized (this.mLock) {
            UserState userState = getUserStateLocked(this.mSecurityPolicy.resolveCallingUserIdEnforcingPermissionsLocked(userId));
            if (userState.mUiAutomationService != null) {
                List<AccessibilityServiceInfo> installedServices = new ArrayList();
                installedServices.addAll(userState.mInstalledServices);
                installedServices.remove(userState.mUiAutomationService.mAccessibilityServiceInfo);
                return installedServices;
            }
            List<AccessibilityServiceInfo> list = userState.mInstalledServices;
            return list;
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void interrupt(int userId) {
        synchronized (this.mLock) {
            int resolvedUserId = this.mSecurityPolicy.resolveCallingUserIdEnforcingPermissionsLocked(userId);
            if (resolvedUserId != this.mCurrentUserId) {
                return;
            }
            CopyOnWriteArrayList<Service> services = getUserStateLocked(resolvedUserId).mBoundServices;
        }
        int i++;
    }

    public int addAccessibilityInteractionConnection(IWindow windowToken, IAccessibilityInteractionConnection connection, int userId) throws RemoteException {
        int windowId;
        synchronized (this.mLock) {
            int resolvedUserId = this.mSecurityPolicy.resolveCallingUserIdEnforcingPermissionsLocked(userId);
            windowId = sNextWindowId;
            sNextWindowId = windowId + 1;
            AccessibilityConnectionWrapper wrapper;
            if (this.mSecurityPolicy.isCallerInteractingAcrossUsers(userId)) {
                wrapper = new AccessibilityConnectionWrapper(windowId, connection, -1);
                wrapper.linkToDeath();
                this.mGlobalInteractionConnections.put(windowId, wrapper);
                this.mGlobalWindowTokens.put(windowId, windowToken.asBinder());
            } else {
                wrapper = new AccessibilityConnectionWrapper(windowId, connection, resolvedUserId);
                wrapper.linkToDeath();
                UserState userState = getUserStateLocked(resolvedUserId);
                userState.mInteractionConnections.put(windowId, wrapper);
                userState.mWindowTokens.put(windowId, windowToken.asBinder());
            }
        }
        return windowId;
    }

    public void removeAccessibilityInteractionConnection(IWindow window) {
        synchronized (this.mLock) {
            this.mSecurityPolicy.resolveCallingUserIdEnforcingPermissionsLocked(UserHandle.getCallingUserId());
            IBinder token = window.asBinder();
            if (removeAccessibilityInteractionConnectionInternalLocked(token, this.mGlobalWindowTokens, this.mGlobalInteractionConnections) >= 0) {
                return;
            }
            int userCount = this.mUserStates.size();
            for (int i = 0; i < userCount; i++) {
                UserState userState = (UserState) this.mUserStates.valueAt(i);
                if (removeAccessibilityInteractionConnectionInternalLocked(token, userState.mWindowTokens, userState.mInteractionConnections) >= 0) {
                    return;
                }
            }
        }
    }

    private int removeAccessibilityInteractionConnectionInternalLocked(IBinder windowToken, SparseArray<IBinder> windowTokens, SparseArray<AccessibilityConnectionWrapper> interactionConnections) {
        int count = windowTokens.size();
        for (int i = 0; i < count; i++) {
            if (windowTokens.valueAt(i) == windowToken) {
                int windowId = windowTokens.keyAt(i);
                windowTokens.removeAt(i);
                ((AccessibilityConnectionWrapper) interactionConnections.get(windowId)).unlinkToDeath();
                interactionConnections.remove(windowId);
                return windowId;
            }
        }
        return -1;
    }

    public void registerUiTestAutomationService(IBinder owner, IAccessibilityServiceClient serviceClient, AccessibilityServiceInfo accessibilityServiceInfo, int flags) {
        this.mSecurityPolicy.enforceCallingPermission("android.permission.RETRIEVE_WINDOW_CONTENT", FUNCTION_REGISTER_UI_TEST_AUTOMATION_SERVICE);
        accessibilityServiceInfo.setComponentName(sFakeAccessibilityServiceComponentName);
        synchronized (this.mLock) {
            UserState userState = getCurrentUserStateLocked();
            if (userState.mUiAutomationService != null) {
                throw new IllegalStateException("UiAutomationService " + serviceClient + "already registered!");
            }
            try {
                owner.linkToDeath(userState.mUiAutomationSerivceOnwerDeathRecipient, 0);
                userState.mUiAutomationServiceOwner = owner;
                userState.mUiAutomationServiceClient = serviceClient;
                userState.mUiAutomationFlags = flags;
                userState.mInstalledServices.add(accessibilityServiceInfo);
                if ((flags & 1) == 0) {
                    userState.mIsTouchExplorationEnabled = false;
                    userState.mIsEnhancedWebAccessibilityEnabled = false;
                    userState.mIsDisplayMagnificationEnabled = false;
                    userState.mIsAutoclickEnabled = false;
                    userState.mEnabledServices.clear();
                }
                userState.mEnabledServices.add(sFakeAccessibilityServiceComponentName);
                userState.mTouchExplorationGrantedServices.add(sFakeAccessibilityServiceComponentName);
                onUserStateChangedLocked(userState);
            } catch (RemoteException re) {
                Slog.e(LOG_TAG, "Couldn't register for the death of a UiTestAutomationService!", re);
            }
        }
    }

    public void unregisterUiTestAutomationService(IAccessibilityServiceClient serviceClient) {
        synchronized (this.mLock) {
            UserState userState = getCurrentUserStateLocked();
            if (userState.mUiAutomationService == null || serviceClient == null || userState.mUiAutomationService.mServiceInterface == null || userState.mUiAutomationService.mServiceInterface.asBinder() != serviceClient.asBinder()) {
                throw new IllegalStateException("UiAutomationService " + serviceClient + " not registered!");
            }
            userState.mUiAutomationService.binderDied();
        }
    }

    public void temporaryEnableAccessibilityStateUntilKeyguardRemoved(ComponentName service, boolean touchExplorationEnabled) {
        this.mSecurityPolicy.enforceCallingPermission("android.permission.TEMPORARY_ENABLE_ACCESSIBILITY", TEMPORARY_ENABLE_ACCESSIBILITY_UNTIL_KEYGUARD_REMOVED);
        if (this.mWindowManagerService.isKeyguardLocked()) {
            synchronized (this.mLock) {
                UserState userState = getCurrentUserStateLocked();
                if (userState.isUiAutomationSuppressingOtherServices()) {
                    return;
                }
                userState.mIsTouchExplorationEnabled = touchExplorationEnabled;
                userState.mIsEnhancedWebAccessibilityEnabled = false;
                userState.mIsDisplayMagnificationEnabled = false;
                userState.mIsAutoclickEnabled = false;
                userState.mEnabledServices.clear();
                userState.mEnabledServices.add(service);
                userState.mBindingServices.clear();
                userState.mTouchExplorationGrantedServices.clear();
                userState.mTouchExplorationGrantedServices.add(service);
                onUserStateChangedLocked(userState);
            }
        }
    }

    public IBinder getWindowToken(int windowId, int userId) {
        this.mSecurityPolicy.enforceCallingPermission("android.permission.RETRIEVE_WINDOW_TOKEN", GET_WINDOW_TOKEN);
        synchronized (this.mLock) {
            if (this.mSecurityPolicy.resolveCallingUserIdEnforcingPermissionsLocked(userId) != this.mCurrentUserId) {
                return null;
            } else if (this.mSecurityPolicy.findWindowById(windowId) == null) {
                return null;
            } else {
                IBinder token = (IBinder) this.mGlobalWindowTokens.get(windowId);
                if (token != null) {
                    return token;
                }
                IBinder iBinder = (IBinder) getCurrentUserStateLocked().mWindowTokens.get(windowId);
                return iBinder;
            }
        }
    }

    boolean onGesture(int gestureId) {
        boolean handled;
        synchronized (this.mLock) {
            handled = notifyGestureLocked(gestureId, false);
            if (!handled) {
                handled = notifyGestureLocked(gestureId, true);
            }
        }
        return handled;
    }

    boolean notifyKeyEvent(KeyEvent event, int policyFlags) {
        synchronized (this.mLock) {
            List<Service> boundServices = getCurrentUserStateLocked().mBoundServices;
            if (boundServices.isEmpty()) {
                return false;
            }
            boolean notifyKeyEventLocked = getKeyEventDispatcher().notifyKeyEventLocked(event, policyFlags, boundServices);
            return notifyKeyEventLocked;
        }
    }

    void notifyMagnificationChanged(Region region, float scale, float centerX, float centerY) {
        synchronized (this.mLock) {
            notifyMagnificationChangedLocked(region, scale, centerX, centerY);
        }
    }

    void setMotionEventInjector(MotionEventInjector motionEventInjector) {
        synchronized (this.mLock) {
            this.mMotionEventInjector = motionEventInjector;
            this.mLock.notifyAll();
        }
    }

    boolean getAccessibilityFocusClickPointInScreen(Point outPoint) {
        return getInteractionBridgeLocked().getAccessibilityFocusClickPointInScreenNotLocked(outPoint);
    }

    boolean getWindowBounds(int windowId, Rect outBounds) {
        IBinder token;
        synchronized (this.mLock) {
            token = (IBinder) this.mGlobalWindowTokens.get(windowId);
            if (token == null) {
                token = (IBinder) getCurrentUserStateLocked().mWindowTokens.get(windowId);
            }
        }
        this.mWindowManagerService.getWindowFrame(token, outBounds);
        if (outBounds.isEmpty()) {
            return false;
        }
        return true;
    }

    boolean accessibilityFocusOnlyInActiveWindow() {
        boolean z;
        synchronized (this.mLock) {
            z = this.mWindowsForAccessibilityCallback == null;
        }
        return z;
    }

    int getActiveWindowId() {
        return this.mSecurityPolicy.getActiveWindowId();
    }

    void onTouchInteractionStart() {
        this.mSecurityPolicy.onTouchInteractionStart();
    }

    void onTouchInteractionEnd() {
        this.mSecurityPolicy.onTouchInteractionEnd();
    }

    void onMagnificationStateChanged() {
        notifyClearAccessibilityCacheLocked();
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void switchUser(int userId) {
        synchronized (this.mLock) {
            if (this.mCurrentUserId == userId && this.mInitialized) {
                return;
            }
            UserState oldUserState = getCurrentUserStateLocked();
            oldUserState.onSwitchToAnotherUser();
            if (oldUserState.mClients.getRegisteredCallbackCount() > 0) {
                this.mMainHandler.obtainMessage(3, oldUserState.mUserId, 0).sendToTarget();
            }
            boolean announceNewUser = ((UserManager) this.mContext.getSystemService("user")).getUsers().size() > 1;
            this.mCurrentUserId = userId;
            UserState userState = getCurrentUserStateLocked();
            if (userState.mUiAutomationService != null) {
                userState.mUiAutomationService.binderDied();
            }
            readConfigurationForUserStateLocked(userState);
            onUserStateChangedLocked(userState);
            if (announceNewUser) {
                this.mMainHandler.sendEmptyMessageDelayed(5, 3000);
            }
        }
    }

    private void unlockUser(int userId) {
        synchronized (this.mLock) {
            if (this.mSecurityPolicy.resolveProfileParentLocked(userId) == this.mCurrentUserId) {
                onUserStateChangedLocked(getUserStateLocked(this.mCurrentUserId));
            }
        }
    }

    private void removeUser(int userId) {
        synchronized (this.mLock) {
            this.mUserStates.remove(userId);
        }
    }

    void restoreEnabledAccessibilityServicesLocked(String oldSetting, String newSetting) {
        readComponentNamesFromStringLocked(oldSetting, this.mTempComponentNameSet, false);
        readComponentNamesFromStringLocked(newSetting, this.mTempComponentNameSet, true);
        UserState userState = getUserStateLocked(0);
        userState.mEnabledServices.clear();
        userState.mEnabledServices.addAll(this.mTempComponentNameSet);
        persistComponentNamesToSettingLocked("enabled_accessibility_services", userState.mEnabledServices, 0);
        onUserStateChangedLocked(userState);
    }

    private InteractionBridge getInteractionBridgeLocked() {
        if (this.mInteractionBridge == null) {
            this.mInteractionBridge = new InteractionBridge();
        }
        return this.mInteractionBridge;
    }

    private boolean notifyGestureLocked(int gestureId, boolean isDefault) {
        UserState state = getCurrentUserStateLocked();
        for (int i = state.mBoundServices.size() - 1; i >= 0; i--) {
            Service service = (Service) state.mBoundServices.get(i);
            if (service.mRequestTouchExplorationMode && service.mIsDefault == isDefault) {
                service.notifyGesture(gestureId);
                return true;
            }
        }
        return false;
    }

    private void notifyClearAccessibilityCacheLocked() {
        UserState state = getCurrentUserStateLocked();
        for (int i = state.mBoundServices.size() - 1; i >= 0; i--) {
            ((Service) state.mBoundServices.get(i)).notifyClearAccessibilityNodeInfoCache();
        }
    }

    private void notifyMagnificationChangedLocked(Region region, float scale, float centerX, float centerY) {
        UserState state = getCurrentUserStateLocked();
        for (int i = state.mBoundServices.size() - 1; i >= 0; i--) {
            ((Service) state.mBoundServices.get(i)).notifyMagnificationChangedLocked(region, scale, centerX, centerY);
        }
    }

    private void notifySoftKeyboardShowModeChangedLocked(int showMode) {
        UserState state = getCurrentUserStateLocked();
        for (int i = state.mBoundServices.size() - 1; i >= 0; i--) {
            ((Service) state.mBoundServices.get(i)).notifySoftKeyboardShowModeChangedLocked(showMode);
        }
    }

    private void removeAccessibilityInteractionConnectionLocked(int windowId, int userId) {
        if (userId == -1) {
            this.mGlobalWindowTokens.remove(windowId);
            this.mGlobalInteractionConnections.remove(windowId);
            return;
        }
        UserState userState = getCurrentUserStateLocked();
        userState.mWindowTokens.remove(windowId);
        userState.mInteractionConnections.remove(windowId);
    }

    private boolean readInstalledAccessibilityServiceLocked(UserState userState) {
        this.mTempAccessibilityServiceInfoList.clear();
        List<ResolveInfo> installedServices = this.mPackageManager.queryIntentServicesAsUser(new Intent("android.accessibilityservice.AccessibilityService"), 819332, this.mCurrentUserId);
        int count = installedServices.size();
        for (int i = 0; i < count; i++) {
            ResolveInfo resolveInfo = (ResolveInfo) installedServices.get(i);
            ServiceInfo serviceInfo = resolveInfo.serviceInfo;
            if ("android.permission.BIND_ACCESSIBILITY_SERVICE".equals(serviceInfo.permission)) {
                try {
                    this.mTempAccessibilityServiceInfoList.add(new AccessibilityServiceInfo(resolveInfo, this.mContext));
                } catch (Exception xppe) {
                    Slog.e(LOG_TAG, "Error while initializing AccessibilityServiceInfo", xppe);
                }
            } else {
                Slog.w(LOG_TAG, "Skipping accessibilty service " + new ComponentName(serviceInfo.packageName, serviceInfo.name).flattenToShortString() + ": it does not require the permission " + "android.permission.BIND_ACCESSIBILITY_SERVICE");
            }
        }
        if (this.mTempAccessibilityServiceInfoList.equals(userState.mInstalledServices)) {
            this.mTempAccessibilityServiceInfoList.clear();
            return false;
        }
        userState.mInstalledServices.clear();
        userState.mInstalledServices.addAll(this.mTempAccessibilityServiceInfoList);
        this.mTempAccessibilityServiceInfoList.clear();
        return true;
    }

    private boolean readEnabledAccessibilityServicesLocked(UserState userState) {
        this.mTempComponentNameSet.clear();
        readComponentNamesFromSettingLocked("enabled_accessibility_services", userState.mUserId, this.mTempComponentNameSet);
        if (this.mTempComponentNameSet.equals(userState.mEnabledServices)) {
            this.mTempComponentNameSet.clear();
            return false;
        }
        userState.mEnabledServices.clear();
        userState.mEnabledServices.addAll(this.mTempComponentNameSet);
        if (userState.mUiAutomationService != null) {
            userState.mEnabledServices.add(sFakeAccessibilityServiceComponentName);
        }
        this.mTempComponentNameSet.clear();
        return true;
    }

    private boolean readTouchExplorationGrantedAccessibilityServicesLocked(UserState userState) {
        this.mTempComponentNameSet.clear();
        readComponentNamesFromSettingLocked("touch_exploration_granted_accessibility_services", userState.mUserId, this.mTempComponentNameSet);
        if (this.mTempComponentNameSet.equals(userState.mTouchExplorationGrantedServices)) {
            this.mTempComponentNameSet.clear();
            return false;
        }
        userState.mTouchExplorationGrantedServices.clear();
        userState.mTouchExplorationGrantedServices.addAll(this.mTempComponentNameSet);
        this.mTempComponentNameSet.clear();
        return true;
    }

    private void notifyAccessibilityServicesDelayedLocked(AccessibilityEvent event, boolean isDefault) {
        try {
            UserState state = getCurrentUserStateLocked();
            int count = state.mBoundServices.size();
            for (int i = 0; i < count; i++) {
                Service service = (Service) state.mBoundServices.get(i);
                if (service.mIsDefault == isDefault && canDispatchEventToServiceLocked(service, event)) {
                    service.notifyAccessibilityEvent(event);
                }
            }
        } catch (IndexOutOfBoundsException e) {
        }
    }

    private void addServiceLocked(Service service, UserState userState) {
        try {
            service.onAdded();
            userState.mBoundServices.add(service);
            userState.mComponentNameToServiceMap.put(service.mComponentName, service);
        } catch (RemoteException e) {
        }
    }

    private void removeServiceLocked(Service service, UserState userState) {
        userState.mBoundServices.remove(service);
        userState.mComponentNameToServiceMap.remove(service.mComponentName);
        service.onRemoved();
    }

    private boolean canDispatchEventToServiceLocked(Service service, AccessibilityEvent event) {
        if (!service.canReceiveEventsLocked()) {
            return false;
        }
        if (event.getWindowId() != -1 && !event.isImportantForAccessibility() && (service.mFetchFlags & 8) == 0) {
            return false;
        }
        int eventType = event.getEventType();
        if ((service.mEventTypes & eventType) != eventType) {
            return false;
        }
        Set<String> packageNames = service.mPackageNames;
        return !packageNames.isEmpty() ? packageNames.contains(event.getPackageName() != null ? event.getPackageName().toString() : null) : true;
    }

    private void unbindAllServicesLocked(UserState userState) {
        List<Service> services = userState.mBoundServices;
        int i = 0;
        int count = services.size();
        while (i < count) {
            if (((Service) services.get(i)).unbindLocked()) {
                i--;
                count--;
            }
            i++;
        }
    }

    private void readComponentNamesFromSettingLocked(String settingName, int userId, Set<ComponentName> outComponentNames) {
        readComponentNamesFromStringLocked(Secure.getStringForUser(this.mContext.getContentResolver(), settingName, userId), outComponentNames, false);
    }

    private void readComponentNamesFromStringLocked(String names, Set<ComponentName> outComponentNames, boolean doMerge) {
        if (!doMerge) {
            outComponentNames.clear();
        }
        if (names != null) {
            SimpleStringSplitter splitter = this.mStringColonSplitter;
            splitter.setString(names);
            while (splitter.hasNext()) {
                String str = splitter.next();
                if (str != null && str.length() > 0) {
                    ComponentName enabledService = ComponentName.unflattenFromString(str);
                    if (enabledService != null) {
                        outComponentNames.add(enabledService);
                    }
                }
            }
        }
    }

    private void persistComponentNamesToSettingLocked(String settingName, Set<ComponentName> componentNames, int userId) {
        StringBuilder builder = new StringBuilder();
        for (ComponentName componentName : componentNames) {
            if (builder.length() > 0) {
                builder.append(COMPONENT_NAME_SEPARATOR);
            }
            builder.append(componentName.flattenToShortString());
        }
        long identity = Binder.clearCallingIdentity();
        try {
            Secure.putStringForUser(this.mContext.getContentResolver(), settingName, builder.toString(), userId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private void updateServicesLocked(UserState userState) {
        Map<ComponentName, Service> componentNameToServiceMap = userState.mComponentNameToServiceMap;
        boolean isUnlockingOrUnlocked = ((UserManager) this.mContext.getSystemService(UserManager.class)).isUserUnlockingOrUnlocked(userState.mUserId);
        int i = 0;
        int count = userState.mInstalledServices.size();
        while (i < count && i < userState.mInstalledServices.size()) {
            AccessibilityServiceInfo installedService = (AccessibilityServiceInfo) userState.mInstalledServices.get(i);
            ComponentName componentName = ComponentName.unflattenFromString(installedService.getId());
            Service service = (Service) componentNameToServiceMap.get(componentName);
            if (!isUnlockingOrUnlocked && !installedService.isDirectBootAware()) {
                Slog.d(LOG_TAG, "Ignoring non-encryption-aware service " + componentName);
            } else if (!userState.mBindingServices.contains(componentName)) {
                if (userState.mEnabledServices.contains(componentName)) {
                    if (service == null) {
                        service = new Service(userState.mUserId, componentName, installedService);
                    } else if (userState.mBoundServices.contains(service)) {
                    }
                    service.bindLocked();
                } else if (service != null) {
                    service.unbindLocked();
                }
            }
            i++;
        }
        updateAccessibilityEnabledSetting(userState);
    }

    private void scheduleUpdateClientsIfNeededLocked(UserState userState) {
        int clientState = userState.getClientState();
        if (userState.mLastSentClientState == clientState) {
            return;
        }
        if (this.mGlobalClients.getRegisteredCallbackCount() > 0 || userState.mClients.getRegisteredCallbackCount() > 0) {
            userState.mLastSentClientState = clientState;
            this.mMainHandler.obtainMessage(2, clientState, userState.mUserId).sendToTarget();
        }
    }

    private void scheduleUpdateInputFilter(UserState userState) {
        this.mMainHandler.obtainMessage(6, userState).sendToTarget();
    }

    private void updateInputFilter(UserState userState) {
        boolean setInputFilter = false;
        AccessibilityInputFilter inputFilter = null;
        synchronized (this.mLock) {
            int flags = 0;
            if (userState.mIsDisplayMagnificationEnabled) {
                flags = 1;
            }
            if (userHasMagnificationServicesLocked(userState)) {
                flags |= 32;
            }
            if (userState.isHandlingAccessibilityEvents() && userState.mIsTouchExplorationEnabled) {
                flags |= 2;
            }
            if (userState.mIsFilterKeyEventsEnabled) {
                flags |= 4;
            }
            if (userState.mIsAutoclickEnabled) {
                flags |= 8;
            }
            if (userState.mIsPerformGesturesEnabled) {
                flags |= 16;
            }
            if (flags != 0) {
                if (!this.mHasInputFilter) {
                    this.mHasInputFilter = true;
                    if (this.mInputFilter == null) {
                        this.mInputFilter = new AccessibilityInputFilter(this.mContext, this);
                    }
                    inputFilter = this.mInputFilter;
                    setInputFilter = true;
                }
                this.mInputFilter.setUserAndEnabledFeatures(userState.mUserId, flags);
            } else if (this.mHasInputFilter) {
                this.mHasInputFilter = false;
                this.mInputFilter.setUserAndEnabledFeatures(userState.mUserId, 0);
                inputFilter = null;
                setInputFilter = true;
            }
        }
        if (setInputFilter) {
            this.mWindowManagerService.setInputFilter(inputFilter);
        }
    }

    private void showEnableTouchExplorationDialog(final Service service) {
        synchronized (this.mLock) {
            String label = service.mResolveInfo.loadLabel(this.mContext.getPackageManager()).toString();
            final UserState state = getCurrentUserStateLocked();
            if (state.mIsTouchExplorationEnabled) {
            } else if (this.mEnableTouchExplorationDialog == null || !this.mEnableTouchExplorationDialog.isShowing()) {
                this.mEnableTouchExplorationDialog = new Builder(this.mContext).setIconAttribute(16843605).setPositiveButton(17039370, new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        state.mTouchExplorationGrantedServices.add(service.mComponentName);
                        AccessibilityManagerService.this.persistComponentNamesToSettingLocked("touch_exploration_granted_accessibility_services", state.mTouchExplorationGrantedServices, state.mUserId);
                        UserState userState = AccessibilityManagerService.this.getUserStateLocked(service.mUserId);
                        userState.mIsTouchExplorationEnabled = true;
                        long identity = Binder.clearCallingIdentity();
                        try {
                            Secure.putIntForUser(AccessibilityManagerService.this.mContext.getContentResolver(), "touch_exploration_enabled", 1, service.mUserId);
                            AccessibilityManagerService.this.onUserStateChangedLocked(userState);
                        } finally {
                            Binder.restoreCallingIdentity(identity);
                        }
                    }
                }).setNegativeButton(17039360, new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }).setTitle(17040196).setMessage(this.mContext.getString(17040197, new Object[]{label})).create();
                this.mEnableTouchExplorationDialog.getWindow().setType(2003);
                LayoutParams attributes = this.mEnableTouchExplorationDialog.getWindow().getAttributes();
                attributes.privateFlags |= 16;
                this.mEnableTouchExplorationDialog.setCanceledOnTouchOutside(true);
                this.mEnableTouchExplorationDialog.show();
            }
        }
    }

    private void onUserStateChangedLocked(UserState userState) {
        this.mInitialized = true;
        updateLegacyCapabilitiesLocked(userState);
        updateServicesLocked(userState);
        updateWindowsForAccessibilityCallbackLocked(userState);
        updateAccessibilityFocusBehaviorLocked(userState);
        updateFilterKeyEventsLocked(userState);
        updateTouchExplorationLocked(userState);
        updatePerformGesturesLocked(userState);
        updateEnhancedWebAccessibilityLocked(userState);
        updateDisplayColorAdjustmentSettingsLocked(userState);
        updateMagnificationLocked(userState);
        updateSoftKeyboardShowModeLocked(userState);
        scheduleUpdateInputFilter(userState);
        scheduleUpdateClientsIfNeededLocked(userState);
    }

    private void updateAccessibilityFocusBehaviorLocked(UserState userState) {
        List<Service> boundServices = userState.mBoundServices;
        int boundServiceCount = boundServices.size();
        for (int i = 0; i < boundServiceCount; i++) {
            if (((Service) boundServices.get(i)).canRetrieveInteractiveWindowsLocked()) {
                userState.mAccessibilityFocusOnlyInActiveWindow = false;
                return;
            }
        }
        userState.mAccessibilityFocusOnlyInActiveWindow = true;
    }

    private void updateWindowsForAccessibilityCallbackLocked(UserState userState) {
        List<Service> boundServices = userState.mBoundServices;
        int boundServiceCount = boundServices.size();
        for (int i = 0; i < boundServiceCount; i++) {
            if (((Service) boundServices.get(i)).canRetrieveInteractiveWindowsLocked()) {
                if (this.mWindowsForAccessibilityCallback == null) {
                    this.mWindowsForAccessibilityCallback = new WindowsForAccessibilityCallback();
                    this.mWindowManagerService.setWindowsForAccessibilityCallback(this.mWindowsForAccessibilityCallback);
                }
                return;
            }
        }
        if (this.mWindowsForAccessibilityCallback != null) {
            this.mWindowsForAccessibilityCallback = null;
            this.mWindowManagerService.setWindowsForAccessibilityCallback(null);
            this.mSecurityPolicy.clearWindowsLocked();
        }
    }

    private void updateLegacyCapabilitiesLocked(UserState userState) {
        int installedServiceCount = userState.mInstalledServices.size();
        int i = 0;
        while (i < installedServiceCount && i < userState.mInstalledServices.size()) {
            AccessibilityServiceInfo serviceInfo = (AccessibilityServiceInfo) userState.mInstalledServices.get(i);
            if (serviceInfo != null) {
                ResolveInfo resolveInfo = serviceInfo.getResolveInfo();
                if ((serviceInfo.getCapabilities() & 2) == 0 && resolveInfo.serviceInfo.applicationInfo.targetSdkVersion <= 17) {
                    if (userState.mTouchExplorationGrantedServices.contains(new ComponentName(resolveInfo.serviceInfo.packageName, resolveInfo.serviceInfo.name))) {
                        serviceInfo.setCapabilities(serviceInfo.getCapabilities() | 2);
                    }
                }
            }
            i++;
        }
    }

    private void updatePerformGesturesLocked(UserState userState) {
        int serviceCount = userState.mBoundServices.size();
        for (int i = 0; i < serviceCount; i++) {
            if ((((Service) userState.mBoundServices.get(i)).mAccessibilityServiceInfo.getCapabilities() & 32) != 0) {
                userState.mIsPerformGesturesEnabled = true;
                return;
            }
        }
        userState.mIsPerformGesturesEnabled = false;
    }

    private void updateFilterKeyEventsLocked(UserState userState) {
        int serviceCount = userState.mBoundServices.size();
        int i = 0;
        while (i < serviceCount) {
            Service service = (Service) userState.mBoundServices.get(i);
            if (!service.mRequestFilterKeyEvents || (service.mAccessibilityServiceInfo.getCapabilities() & 8) == 0) {
                i++;
            } else {
                userState.mIsFilterKeyEventsEnabled = true;
                return;
            }
        }
        userState.mIsFilterKeyEventsEnabled = false;
    }

    private boolean readConfigurationForUserStateLocked(UserState userState) {
        return (((((((readInstalledAccessibilityServiceLocked(userState) | readEnabledAccessibilityServicesLocked(userState)) | readTouchExplorationGrantedAccessibilityServicesLocked(userState)) | readTouchExplorationEnabledSettingLocked(userState)) | readHighTextContrastEnabledSettingLocked(userState)) | readEnhancedWebAccessibilityEnabledChangedLocked(userState)) | readDisplayMagnificationEnabledSettingLocked(userState)) | readAutoclickEnabledSettingLocked(userState)) | readDisplayColorAdjustmentSettingsLocked(userState);
    }

    private void updateAccessibilityEnabledSetting(UserState userState) {
        long identity = Binder.clearCallingIdentity();
        try {
            Secure.putIntForUser(this.mContext.getContentResolver(), "accessibility_enabled", userState.isHandlingAccessibilityEvents() ? 1 : 0, userState.mUserId);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private boolean readTouchExplorationEnabledSettingLocked(UserState userState) {
        boolean touchExplorationEnabled = Secure.getIntForUser(this.mContext.getContentResolver(), "touch_exploration_enabled", 0, userState.mUserId) == 1;
        if (touchExplorationEnabled == userState.mIsTouchExplorationEnabled) {
            return false;
        }
        userState.mIsTouchExplorationEnabled = touchExplorationEnabled;
        return true;
    }

    private boolean readDisplayMagnificationEnabledSettingLocked(UserState userState) {
        boolean displayMagnificationEnabled = Secure.getIntForUser(this.mContext.getContentResolver(), "accessibility_display_magnification_enabled", 0, userState.mUserId) == 1;
        if (displayMagnificationEnabled == userState.mIsDisplayMagnificationEnabled) {
            return false;
        }
        userState.mIsDisplayMagnificationEnabled = displayMagnificationEnabled;
        return true;
    }

    private boolean readAutoclickEnabledSettingLocked(UserState userState) {
        boolean autoclickEnabled = Secure.getIntForUser(this.mContext.getContentResolver(), "accessibility_autoclick_enabled", 0, userState.mUserId) == 1;
        if (autoclickEnabled == userState.mIsAutoclickEnabled) {
            return false;
        }
        userState.mIsAutoclickEnabled = autoclickEnabled;
        return true;
    }

    private boolean readEnhancedWebAccessibilityEnabledChangedLocked(UserState userState) {
        boolean enhancedWeAccessibilityEnabled = Secure.getIntForUser(this.mContext.getContentResolver(), "accessibility_script_injection", 0, userState.mUserId) == 1;
        if (enhancedWeAccessibilityEnabled == userState.mIsEnhancedWebAccessibilityEnabled) {
            return false;
        }
        userState.mIsEnhancedWebAccessibilityEnabled = enhancedWeAccessibilityEnabled;
        return true;
    }

    private boolean readDisplayColorAdjustmentSettingsLocked(UserState userState) {
        boolean displayAdjustmentsEnabled = DisplayAdjustmentUtils.hasAdjustments(this.mContext, userState.mUserId);
        if (displayAdjustmentsEnabled == userState.mHasDisplayColorAdjustment) {
            return displayAdjustmentsEnabled;
        }
        userState.mHasDisplayColorAdjustment = displayAdjustmentsEnabled;
        return true;
    }

    private boolean readHighTextContrastEnabledSettingLocked(UserState userState) {
        boolean highTextContrastEnabled = Secure.getIntForUser(this.mContext.getContentResolver(), "high_text_contrast_enabled", 0, userState.mUserId) == 1;
        if (highTextContrastEnabled == userState.mIsTextHighContrastEnabled) {
            return false;
        }
        userState.mIsTextHighContrastEnabled = highTextContrastEnabled;
        return true;
    }

    private boolean readSoftKeyboardShowModeChangedLocked(UserState userState) {
        int softKeyboardShowMode = Secure.getIntForUser(this.mContext.getContentResolver(), "accessibility_soft_keyboard_mode", 0, userState.mUserId);
        if (softKeyboardShowMode == userState.mSoftKeyboardShowMode) {
            return false;
        }
        userState.mSoftKeyboardShowMode = softKeyboardShowMode;
        return true;
    }

    private void updateTouchExplorationLocked(UserState userState) {
        boolean enabled = false;
        int serviceCount = userState.mBoundServices.size();
        for (int i = 0; i < serviceCount; i++) {
            if (canRequestAndRequestsTouchExplorationLocked((Service) userState.mBoundServices.get(i))) {
                enabled = true;
                break;
            }
        }
        if (enabled != userState.mIsTouchExplorationEnabled) {
            userState.mIsTouchExplorationEnabled = enabled;
            long identity = Binder.clearCallingIdentity();
            try {
                Secure.putIntForUser(this.mContext.getContentResolver(), "touch_exploration_enabled", enabled ? 1 : 0, userState.mUserId);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }
    }

    private boolean canRequestAndRequestsTouchExplorationLocked(Service service) {
        if (!service.canReceiveEventsLocked() || !service.mRequestTouchExplorationMode) {
            return false;
        }
        if (service.mIsAutomation) {
            return true;
        }
        if (service.mResolveInfo.serviceInfo.applicationInfo.targetSdkVersion <= 17) {
            if (getUserStateLocked(service.mUserId).mTouchExplorationGrantedServices.contains(service.mComponentName)) {
                return true;
            }
            if (this.mEnableTouchExplorationDialog == null || !this.mEnableTouchExplorationDialog.isShowing()) {
                this.mMainHandler.obtainMessage(7, service).sendToTarget();
            }
        } else if ((service.mAccessibilityServiceInfo.getCapabilities() & 2) != 0) {
            return true;
        }
        return false;
    }

    private void updateEnhancedWebAccessibilityLocked(UserState userState) {
        boolean enabled = false;
        int serviceCount = userState.mBoundServices.size();
        for (int i = 0; i < serviceCount; i++) {
            if (canRequestAndRequestsEnhancedWebAccessibilityLocked((Service) userState.mBoundServices.get(i))) {
                enabled = true;
                break;
            }
        }
        if (enabled != userState.mIsEnhancedWebAccessibilityEnabled) {
            userState.mIsEnhancedWebAccessibilityEnabled = enabled;
            long identity = Binder.clearCallingIdentity();
            try {
                Secure.putIntForUser(this.mContext.getContentResolver(), "accessibility_script_injection", enabled ? 1 : 0, userState.mUserId);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }
    }

    private boolean canRequestAndRequestsEnhancedWebAccessibilityLocked(Service service) {
        if (!service.canReceiveEventsLocked() || !service.mRequestEnhancedWebAccessibility) {
            return false;
        }
        if (service.mIsAutomation || (service.mAccessibilityServiceInfo.getCapabilities() & 4) != 0) {
            return true;
        }
        return false;
    }

    private void updateDisplayColorAdjustmentSettingsLocked(UserState userState) {
        DisplayAdjustmentUtils.applyAdjustments(this.mContext, userState.mUserId);
    }

    private void updateMagnificationLocked(UserState userState) {
        if (userState.mUserId == this.mCurrentUserId) {
            if (userState.mIsDisplayMagnificationEnabled || userHasListeningMagnificationServicesLocked(userState)) {
                getMagnificationController();
                this.mMagnificationController.register();
            } else if (this.mMagnificationController != null) {
                this.mMagnificationController.unregister();
            }
        }
    }

    private boolean userHasMagnificationServicesLocked(UserState userState) {
        List<Service> services = userState.mBoundServices;
        int count = services.size();
        for (int i = 0; i < count; i++) {
            if (this.mSecurityPolicy.canControlMagnification((Service) services.get(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean userHasListeningMagnificationServicesLocked(UserState userState) {
        List<Service> services = userState.mBoundServices;
        int count = services.size();
        for (int i = 0; i < count; i++) {
            Service service = (Service) services.get(i);
            if (this.mSecurityPolicy.canControlMagnification(service) && service.mInvocationHandler.mIsMagnificationCallbackEnabled) {
                return true;
            }
        }
        return false;
    }

    private void updateSoftKeyboardShowModeLocked(UserState userState) {
        if (userState.mUserId == this.mCurrentUserId && userState.mSoftKeyboardShowMode != 0 && !userState.mEnabledServices.contains(userState.mServiceChangingSoftKeyboardMode)) {
            long identity = Binder.clearCallingIdentity();
            try {
                Secure.putIntForUser(this.mContext.getContentResolver(), "accessibility_soft_keyboard_mode", 0, userState.mUserId);
                userState.mSoftKeyboardShowMode = 0;
                userState.mServiceChangingSoftKeyboardMode = null;
                notifySoftKeyboardShowModeChangedLocked(userState.mSoftKeyboardShowMode);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }
    }

    private MagnificationSpec getCompatibleMagnificationSpecLocked(int windowId) {
        IBinder windowToken = (IBinder) this.mGlobalWindowTokens.get(windowId);
        if (windowToken == null) {
            windowToken = (IBinder) getCurrentUserStateLocked().mWindowTokens.get(windowId);
        }
        if (windowToken != null) {
            return this.mWindowManagerService.getCompatibleMagnificationSpecForWindow(windowToken);
        }
        return null;
    }

    private KeyEventDispatcher getKeyEventDispatcher() {
        if (this.mKeyEventDispatcher == null) {
            this.mKeyEventDispatcher = new KeyEventDispatcher(this.mMainHandler, 8, this.mLock, this.mPowerManager);
        }
        return this.mKeyEventDispatcher;
    }

    public void enableAccessibilityService(ComponentName componentName, int userId) {
        synchronized (this.mLock) {
            if (Binder.getCallingUid() != 1000) {
                throw new SecurityException("only SYSTEM can call enableAccessibilityService.");
            }
            SettingsStringHelper settingsHelper = new SettingsStringHelper("enabled_accessibility_services", userId);
            settingsHelper.addService(componentName);
            settingsHelper.writeToSettings();
            UserState userState = getUserStateLocked(userId);
            if (userState.mEnabledServices.add(componentName)) {
                onUserStateChangedLocked(userState);
            }
        }
    }

    public void disableAccessibilityService(ComponentName componentName, int userId) {
        synchronized (this.mLock) {
            if (Binder.getCallingUid() != 1000) {
                throw new SecurityException("only SYSTEM can call disableAccessibility");
            }
            SettingsStringHelper settingsHelper = new SettingsStringHelper("enabled_accessibility_services", userId);
            settingsHelper.deleteService(componentName);
            settingsHelper.writeToSettings();
            UserState userState = getUserStateLocked(userId);
            if (userState.mEnabledServices.remove(componentName)) {
                onUserStateChangedLocked(userState);
            }
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        this.mSecurityPolicy.enforceCallingPermission("android.permission.DUMP", FUNCTION_DUMP);
        synchronized (this.mLock) {
            pw.println("ACCESSIBILITY MANAGER (dumpsys accessibility)");
            pw.println();
            int userCount = this.mUserStates.size();
            for (int i = 0; i < userCount; i++) {
                int j;
                UserState userState = (UserState) this.mUserStates.valueAt(i);
                pw.append("User state[attributes:{id=" + userState.mUserId);
                pw.append(", currentUser=" + (userState.mUserId == this.mCurrentUserId));
                pw.append(", touchExplorationEnabled=" + userState.mIsTouchExplorationEnabled);
                pw.append(", displayMagnificationEnabled=" + userState.mIsDisplayMagnificationEnabled);
                pw.append(", autoclickEnabled=" + userState.mIsAutoclickEnabled);
                if (userState.mUiAutomationService != null) {
                    pw.append(", ");
                    userState.mUiAutomationService.dump(fd, pw, args);
                    pw.println();
                }
                pw.append("}");
                pw.println();
                pw.append("           services:{");
                int serviceCount = userState.mBoundServices.size();
                for (j = 0; j < serviceCount; j++) {
                    if (j > 0) {
                        pw.append(", ");
                        pw.println();
                        pw.append("                     ");
                    }
                    ((Service) userState.mBoundServices.get(j)).dump(fd, pw, args);
                }
                pw.println("}]");
                pw.println();
            }
            if (this.mSecurityPolicy.mWindows != null) {
                int windowCount = this.mSecurityPolicy.mWindows.size();
                for (j = 0; j < windowCount; j++) {
                    if (j > 0) {
                        pw.append(',');
                        pw.println();
                    }
                    pw.append("Window[");
                    pw.append(((AccessibilityWindowInfo) this.mSecurityPolicy.mWindows.get(j)).toString());
                    pw.append(']');
                }
            }
        }
    }

    private int findWindowIdLocked(IBinder token) {
        int globalIndex = this.mGlobalWindowTokens.indexOfValue(token);
        if (globalIndex >= 0) {
            return this.mGlobalWindowTokens.keyAt(globalIndex);
        }
        UserState userState = getCurrentUserStateLocked();
        int userIndex = userState.mWindowTokens.indexOfValue(token);
        if (userIndex >= 0) {
            return userState.mWindowTokens.keyAt(userIndex);
        }
        return -1;
    }

    private void ensureWindowsAvailableTimed() {
        synchronized (this.mLock) {
            if (this.mSecurityPolicy.mWindows != null) {
                return;
            }
            if (this.mWindowsForAccessibilityCallback == null) {
                onUserStateChangedLocked(getCurrentUserStateLocked());
            }
            if (this.mWindowsForAccessibilityCallback == null) {
                return;
            }
            long startMillis = SystemClock.uptimeMillis();
            while (this.mSecurityPolicy.mWindows == null) {
                long remainMillis = 5000 - (SystemClock.uptimeMillis() - startMillis);
                if (remainMillis <= 0) {
                    return;
                }
                try {
                    this.mLock.wait(remainMillis);
                } catch (InterruptedException e) {
                }
            }
        }
    }

    MagnificationController getMagnificationController() {
        MagnificationController magnificationController;
        synchronized (this.mLock) {
            if (this.mMagnificationController == null) {
                this.mMagnificationController = new MagnificationController(this.mContext, this, this.mLock);
                this.mMagnificationController.setUserId(this.mCurrentUserId);
            }
            magnificationController = this.mMagnificationController;
        }
        return magnificationController;
    }
}
