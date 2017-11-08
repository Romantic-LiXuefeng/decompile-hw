package com.android.server.security.trustspace;

import android.app.AppGlobals;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.UserInfo;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.IUserManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings.System;
import android.trustspace.ITrustSpaceManager.Stub;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.Slog;
import com.android.internal.content.PackageMonitor;
import com.android.server.LocalServices;
import com.android.server.UiThread;
import com.android.server.pfw.autostartup.comm.XmlConst.PreciseIgnore;
import com.android.server.security.core.IHwSecurityPlugin;
import com.android.server.security.core.IHwSecurityPlugin.Creator;
import huawei.com.android.server.policy.HwGlobalActionsData;
import java.util.ArrayList;
import java.util.List;

public class TrustSpaceManagerService extends Stub implements IHwSecurityPlugin {
    private static final String ACTION_INTENT_PREVENTED = "huawei.intent.action.TRUSTSPACE_INTENT_PREVENTED";
    private static final String ACTION_PACKAGE_ADDED = "huawei.intent.action.TRUSTSPACE_PACKAGE_ADDED";
    private static final String ACTION_PACKAGE_REMOVED = "huawei.intent.action.TRUSTSPACE_PACKAGE_REMOVED";
    public static final Creator CREATOR = new Creator() {
        public IHwSecurityPlugin createPlugin(Context context) {
            if (TrustSpaceManagerService.HW_DEBUG) {
                Slog.d(TrustSpaceManagerService.TAG, "createPlugin");
            }
            return new TrustSpaceManagerService(context);
        }

        public String getPluginPermission() {
            return TrustSpaceManagerService.MANAGE_TRUSTSPACE;
        }
    };
    private static final String HW_APPMARKET_PACKAGENAME = "com.huawei.appmarket";
    private static final boolean HW_DEBUG = Log.HWINFO;
    private static final String HW_TRUSTSPACE_LAUNCHER = "com.huawei.trustspace.mainscreen.LoadActivity";
    private static final String HW_TRUSTSPACE_PACKAGENAME = "com.huawei.trustspace";
    private static final int INVALID_CLONED_PROFILE = -1000;
    private static final boolean IS_SUPPORT_CLONE_APP = SystemProperties.getBoolean("ro.config.hw_support_clone_app", false);
    private static final String MANAGE_TRUSTSPACE = "com.huawei.permission.MANAGE_TRUSTSPACE";
    private static final int RESULT_CODE_ERROR = -1;
    private static final String SETTINGS_TRUSTSPACE_CONTROL = "trust_space_switch";
    private static final String TAG = "TrustSpaceManagerService";
    private static final int TYPE_RISK = 303;
    private static final int TYPE_VIRUS = 305;
    private int mClonedProfileUserId;
    private Context mContext;
    private boolean mEnableTrustSpace = false;
    private final MyPackageMonitor mMyPackageMonitor = new MyPackageMonitor();
    private TrustSpaceSettings mSettings;
    private final MySettingsObserver mSettingsObserver = new MySettingsObserver();
    private boolean mSupportTrustSpace = false;
    private final ArraySet<String> mSystemApps = new ArraySet();
    private volatile boolean mSystemReady = false;
    private final ArraySet<Integer> mSystemUids = new ArraySet();
    private final ArrayMap<String, Integer> mVirusScanResult = new ArrayMap();

    private final class LocalServiceImpl implements TrustSpaceManagerInternal {
        private LocalServiceImpl() {
        }

        public boolean checkIntent(int type, String calleePackage, int callerUid, int callerPid, String callingPackage, int userId) {
            return TrustSpaceManagerService.this.checkIntent(type, calleePackage, callerUid, callerPid, callingPackage, userId);
        }

        public void initTrustSpace() {
            TrustSpaceManagerService.this.initTrustSpace();
        }

        public boolean isIntentProtectedApp(String pkg) {
            return TrustSpaceManagerService.this.isIntentProtectedAppInner(pkg);
        }
    }

    class MyBroadcastReceiver extends BroadcastReceiver {
        MyBroadcastReceiver() {
        }

        public void onReceive(Context context, Intent intent) {
            if ("android.intent.action.USER_ADDED".equals(intent.getAction())) {
                int userId = intent.getIntExtra("android.intent.extra.user_handle", -10000);
                Slog.d(TrustSpaceManagerService.TAG, "action add user" + userId);
                if (!(userId == -10000 || userId == 0)) {
                    TrustSpaceManagerService.this.enableTrustSpaceApp(false, false, userId);
                }
                if (TrustSpaceManagerService.this.isClonedProfile(userId)) {
                    TrustSpaceManagerService.this.updateClonedProfileUserId(userId);
                }
            }
        }
    }

    private final class MyPackageMonitor extends PackageMonitor {
        private MyPackageMonitor() {
        }

        public void onPackageAdded(String packageName, int uid) {
            TrustSpaceManagerService.this.handlePackageAppeared(packageName, 3, uid);
        }

        public void onPackageRemoved(String packageName, int uid) {
            TrustSpaceManagerService.this.handlePackageDisappeared(packageName, 3, uid);
        }

        public void onPackageUpdateStarted(String packageName, int uid) {
        }

        public void onPackageUpdateFinished(String packageName, int uid) {
            TrustSpaceManagerService.this.handlePackageAppeared(packageName, 1, uid);
        }
    }

    private class MySettingsObserver extends ContentObserver {
        private final Uri TRUSTSPACE_CONTROL_URI = System.getUriFor(TrustSpaceManagerService.SETTINGS_TRUSTSPACE_CONTROL);

        public MySettingsObserver() {
            super(new Handler());
        }

        public void registerContentObserver() {
            TrustSpaceManagerService.this.mContext.getContentResolver().registerContentObserver(this.TRUSTSPACE_CONTROL_URI, false, this, 0);
        }

        public void onChange(boolean selfChange, Uri uri) {
            if (this.TRUSTSPACE_CONTROL_URI.equals(uri)) {
                TrustSpaceManagerService.this.mEnableTrustSpace = TrustSpaceManagerService.this.isTrustSpaceEnable();
                Slog.i(TrustSpaceManagerService.TAG, "TrustSpace Enabled = " + TrustSpaceManagerService.this.mEnableTrustSpace);
            }
        }
    }

    public TrustSpaceManagerService(Context context) {
        this.mContext = context;
        this.mSettings = new TrustSpaceSettings();
    }

    public void onStart() {
        Slog.d(TAG, "TrustSpaceManagerService Start");
        LocalServices.addService(TrustSpaceManagerInternal.class, new LocalServiceImpl());
    }

    public void onStop() {
    }

    public IBinder asBinder() {
        return this;
    }

    private void handlePackageAppeared(String packageName, int reason, int uid) {
        if (isUseTrustSpace()) {
            int userId = UserHandle.getUserId(uid);
            if (userId == 0 || isClonedProfile(userId)) {
                if (HW_DEBUG) {
                    Slog.d(TAG, "onPackageAppeared:" + packageName + " reason=" + reason + " uid=" + uid);
                }
                if (userId == 0) {
                    removeVirusScanResult(packageName);
                }
                Intent intent = new Intent(ACTION_PACKAGE_ADDED);
                intent.putExtra(PreciseIgnore.COMP_COMM_RELATED_PACKAGE_ATTR_KEY, packageName);
                intent.putExtra("reason", reason);
                intent.putExtra("userId", userId);
                intent.setPackage(HW_TRUSTSPACE_PACKAGENAME);
                this.mContext.startService(intent);
            }
        }
    }

    private void handlePackageDisappeared(String packageName, int reason, int uid) {
        if (isUseTrustSpace()) {
            int userId = UserHandle.getUserId(uid);
            if (userId == 0 || isClonedProfile(userId)) {
                if (HW_DEBUG) {
                    Slog.d(TAG, "onPackageDisappeared:" + packageName + " reason=" + reason + " uid=" + uid);
                }
                if (userId == 0) {
                    removeVirusScanResult(packageName);
                    synchronized (this) {
                        boolean configChange = false;
                        boolean isProtectedApp = this.mSettings.isIntentProtectedApp(packageName);
                        boolean isTrustApp = this.mSettings.isTrustApp(packageName);
                        if (isProtectedApp && reason == 3) {
                            this.mSettings.removeIntentProtectedApp(packageName);
                            configChange = true;
                        }
                        if (isTrustApp && reason == 3) {
                            this.mSettings.removeTrustApp(packageName);
                            configChange = true;
                        }
                        if (configChange) {
                            this.mSettings.writePackages();
                        }
                    }
                }
                Intent intent = new Intent(ACTION_PACKAGE_REMOVED);
                intent.putExtra(PreciseIgnore.COMP_COMM_RELATED_PACKAGE_ATTR_KEY, packageName);
                intent.putExtra("reason", reason);
                intent.putExtra("userId", userId);
                intent.setPackage(HW_TRUSTSPACE_PACKAGENAME);
                this.mContext.startService(intent);
            }
        }
    }

    private boolean isTrustSpaceEnable() {
        return System.getIntForUser(this.mContext.getContentResolver(), SETTINGS_TRUSTSPACE_CONTROL, 1, 0) == 1;
    }

    private boolean isUseTrustSpace() {
        return this.mSupportTrustSpace ? this.mEnableTrustSpace : false;
    }

    private boolean calledFromValidUser() {
        int uid = Binder.getCallingUid();
        int userId = UserHandle.getUserId(uid);
        if (uid == 1000 || userId == 0) {
            return true;
        }
        return false;
    }

    private void removeVirusScanResult(String packageName) {
        synchronized (this.mVirusScanResult) {
            this.mVirusScanResult.remove(packageName);
        }
    }

    private void addVirusScanResult(String packageName, int type) {
        synchronized (this.mVirusScanResult) {
            this.mVirusScanResult.put(packageName, Integer.valueOf(type));
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private boolean isMaliciousApp(String packageName) {
        if (packageName == null) {
            return false;
        }
        synchronized (this.mVirusScanResult) {
            if (this.mVirusScanResult.containsKey(packageName)) {
                int type = ((Integer) this.mVirusScanResult.get(packageName)).intValue();
                if (type == 303 || type == 305) {
                    Slog.i(TAG, "find Malicious App:" + packageName);
                    return true;
                }
                return false;
            }
        }
        long costTime = SystemClock.uptimeMillis() - start;
        if (HW_DEBUG) {
            Slog.d(TAG, "isMaliciousApp cost: " + costTime);
        }
        if (r4 == null) {
            Slog.i(TAG, "isVirusApk, res is null");
            return false;
        }
        int resultCode = r4.getInt("result_code", -1);
        if (resultCode != -1) {
            addVirusScanResult(packageName, resultCode);
        }
        if (resultCode != 303 && resultCode != 305) {
            return false;
        }
        Slog.i(TAG, "find Malicious App:" + packageName);
        return true;
    }

    private boolean isSystemPackageInstalled(String packageName, int flag) {
        ApplicationInfo appInfo = null;
        try {
            appInfo = AppGlobals.getPackageManager().getApplicationInfo(packageName, flag, 0);
        } catch (RemoteException e) {
            Log.d(TAG, "Can't find package:" + packageName);
        }
        if (appInfo != null) {
            return appInfo.isSystemApp();
        }
        return false;
    }

    private boolean isSupportTrustSpaceInner() {
        if (isSystemPackageInstalled(HW_APPMARKET_PACKAGENAME, 0)) {
            return true;
        }
        return false;
    }

    private void enableTrustSpaceApp(boolean enablePackage, boolean enableLauncher, int userId) {
        int newPackageState;
        int newLauncherState;
        IPackageManager pm = AppGlobals.getPackageManager();
        ComponentName cName = new ComponentName(HW_TRUSTSPACE_PACKAGENAME, HW_TRUSTSPACE_LAUNCHER);
        if (enablePackage) {
            newPackageState = 0;
        } else {
            newPackageState = 2;
        }
        if (enableLauncher) {
            newLauncherState = 0;
        } else {
            newLauncherState = 2;
        }
        try {
            pm.setApplicationEnabledSetting(HW_TRUSTSPACE_PACKAGENAME, newPackageState, 0, userId, null);
            pm.setComponentEnabledSetting(cName, newLauncherState, 0, userId);
        } catch (Exception e) {
            Slog.w(TAG, "enableTrustSpaceApp fail: " + e.getMessage());
        }
    }

    private void loadSystemPackages() {
        ArraySet<Integer> tempUidSet = new ArraySet();
        ArraySet<String> tempPkgSet = new ArraySet();
        List<ApplicationInfo> allApp = null;
        try {
            allApp = AppGlobals.getPackageManager().getInstalledApplications(8192, 0).getList();
        } catch (RemoteException e) {
            Slog.e(TAG, "Get Installed Applications fail");
        }
        if (allApp != null) {
            for (ApplicationInfo app : allApp) {
                if (app.isSystemApp() && (app.hwFlags & HwGlobalActionsData.FLAG_SHUTDOWN_CONFIRM) == 0) {
                    tempUidSet.add(Integer.valueOf(app.uid));
                    tempPkgSet.add(app.packageName);
                }
            }
            this.mSystemUids.clear();
            this.mSystemUids.addAll(tempUidSet);
            this.mSystemApps.clear();
            this.mSystemApps.addAll(tempPkgSet);
        }
    }

    private int getClonedProfileId() {
        if (!IS_SUPPORT_CLONE_APP) {
            return -1000;
        }
        try {
            for (UserInfo info : ((IUserManager) ServiceManager.getService("user")).getProfiles(0, false)) {
                if (info.isClonedProfile()) {
                    return info.id;
                }
            }
            Slog.d(TAG, "Cloned Profile is not exist");
        } catch (RemoteException e) {
            Slog.w(TAG, "Failed to getProfiles");
        }
        return -1000;
    }

    private boolean isClonedProfile(int userId) {
        boolean z = false;
        if (!IS_SUPPORT_CLONE_APP) {
            return false;
        }
        try {
            UserInfo userInfo = ((IUserManager) ServiceManager.getService("user")).getUserInfo(userId);
            if (userInfo != null) {
                z = userInfo.isClonedProfile();
            }
            return z;
        } catch (RemoteException e) {
            Slog.w(TAG, "Failed to getUserInfo");
            return false;
        }
    }

    private void updateClonedProfileUserId(int userId) {
        if (IS_SUPPORT_CLONE_APP && userId != -1000) {
            this.mClonedProfileUserId = userId;
            Slog.i(TAG, "Cloned profile user" + userId);
        }
    }

    private boolean checkClonedProfile(int userId) {
        boolean z = false;
        if (!IS_SUPPORT_CLONE_APP || userId == -1000) {
            return false;
        }
        if (userId == this.mClonedProfileUserId) {
            z = true;
        }
        return z;
    }

    private void initTrustSpace() {
        if (!this.mSystemReady) {
            Slog.i(TAG, "TrustSpaceManagerService init begin");
            if (isSystemPackageInstalled(HW_TRUSTSPACE_PACKAGENAME, 0)) {
                synchronized (this) {
                    this.mSettings.readPackages();
                }
                this.mSupportTrustSpace = isSupportTrustSpaceInner();
                if (this.mSupportTrustSpace) {
                    loadSystemPackages();
                    this.mMyPackageMonitor.register(this.mContext, null, UserHandle.ALL, false);
                    IntentFilter broadcastFilter = new IntentFilter();
                    broadcastFilter.addAction("android.intent.action.USER_ADDED");
                    this.mContext.registerReceiver(new MyBroadcastReceiver(), broadcastFilter);
                    this.mSettingsObserver.registerContentObserver();
                    updateClonedProfileUserId(getClonedProfileId());
                    this.mEnableTrustSpace = isTrustSpaceEnable();
                }
                Slog.i(TAG, "Enable TrustSpace App: " + this.mSupportTrustSpace);
                enableTrustSpaceApp(this.mSupportTrustSpace, this.mEnableTrustSpace, 0);
                this.mSystemReady = true;
                Slog.i(TAG, "TrustSpaceManagerService init end");
                return;
            }
            Slog.e(TAG, "TrustSpace application is not exist");
        }
    }

    private boolean isValidUser(int type, int userId) {
        int i = 1;
        boolean isValidUser = userId == 0;
        if (type == 1) {
            if (userId != -1) {
                i = 0;
            }
            isValidUser |= i;
        }
        return isValidUser | checkClonedProfile(userId);
    }

    private boolean isSpecialCaller(String packageName, int uid) {
        if (packageName != null) {
            return this.mSystemApps.contains(packageName);
        }
        if (uid < 10000 || this.mSystemUids.contains(Integer.valueOf(uid))) {
            return true;
        }
        return false;
    }

    private boolean isSelfCall(String calleePackage, String callingPackage) {
        return calleePackage != null ? calleePackage.equals(callingPackage) : false;
    }

    private boolean isSpecialCallee(String packageName) {
        return this.mSystemApps.contains(packageName);
    }

    private boolean isSpecialPackage(String callingPackage, int callerUid, String calleePackage) {
        boolean isSpecialCaller = isSpecialCaller(callingPackage, callerUid);
        boolean isSpecialCallee = isSpecialCallee(calleePackage);
        synchronized (this) {
            boolean isCallerIntentProtected = this.mSettings.isIntentProtectedApp(callingPackage);
            boolean isCalleeIntentProtected = this.mSettings.isIntentProtectedApp(calleePackage);
        }
        if (isSpecialCaller && !isCallerIntentProtected) {
            return true;
        }
        if (!isSpecialCallee || isCalleeIntentProtected) {
            return false;
        }
        return true;
    }

    private boolean shouldNotify(int type, String target) {
        if ((type == 0 || type == 2 || type == 3) && !isMaliciousApp(target)) {
            return true;
        }
        return false;
    }

    private boolean checkIntent(int type, String calleePackage, int callerUid, int callerPid, String callingPackage, int userId) {
        if (!isUseTrustSpace() || !isValidUser(type, userId) || isSelfCall(calleePackage, callingPackage) || isSpecialPackage(callingPackage, callerUid, calleePackage)) {
            return false;
        }
        String typeString = TrustSpaceSettings.componentTypeToString(type);
        if (calleePackage == null || callingPackage == null) {
            Slog.w(TAG, "unknown Intent, type: " + typeString + " calleePackage: " + calleePackage + " callerPid:" + callerPid + " callerUid:" + callerUid + " callerPackage:" + callingPackage + " userId:" + userId);
            return false;
        }
        synchronized (this) {
            boolean isTrustcallee = this.mSettings.isTrustApp(calleePackage);
            boolean isTrustcaller = this.mSettings.isTrustApp(callingPackage);
            int calleeLevel = this.mSettings.getProtectionLevel(calleePackage) & 255;
            int callerLevel = this.mSettings.getProtectionLevel(callingPackage) & 255;
        }
        if (calleeLevel == 0 && callerLevel == 0) {
            return false;
        }
        if (HW_DEBUG) {
            Slog.d(TAG, "check Intent, type: " + typeString + " calleePackage: " + calleePackage + " calleelevel=" + calleeLevel + " callerPid:" + callerPid + " callerUid:" + callerUid + " callerPackage:" + callingPackage + " callerLevel=" + callerLevel + " userId:" + userId);
        }
        boolean needPrevent = false;
        if (calleeLevel == 1) {
            if (callerLevel != 0 || isTrustcaller) {
                return false;
            }
            if (isMaliciousApp(callingPackage)) {
                needPrevent = true;
            }
        } else if (callerLevel == 1) {
            if (calleeLevel != 0 || isTrustcallee) {
                return false;
            }
            if (isMaliciousApp(calleePackage)) {
                needPrevent = true;
            }
        } else if (calleeLevel == 2) {
            if (callerLevel != 0 || isTrustcaller) {
                return false;
            }
            needPrevent = true;
            if (shouldNotify(type, callingPackage)) {
                notifyIntentPrevented(typeString, calleePackage, calleeLevel, callingPackage, callerLevel);
            }
        } else if (callerLevel == 2) {
            if (calleeLevel != 0 || isTrustcallee) {
                return false;
            }
            needPrevent = true;
            if (shouldNotify(type, calleePackage)) {
                notifyIntentPrevented(typeString, calleePackage, calleeLevel, callingPackage, callerLevel);
            }
        }
        if (needPrevent) {
            Slog.i(TAG, "prevent Intent, type: " + typeString + " calleePackage: " + calleePackage + " calleelevel=" + calleeLevel + " callerPid:" + callerPid + " callerUid:" + callerUid + " callerPackage:" + callingPackage + " callerLevel=" + callerLevel + " userId:" + userId);
        }
        return needPrevent;
    }

    private void notifyIntentPrevented(String typeString, String calleePackage, int calleeLevel, String callerPackage, int callerLevel) {
        final String str = typeString;
        final String str2 = calleePackage;
        final String str3 = callerPackage;
        final int i = calleeLevel;
        final int i2 = callerLevel;
        UiThread.getHandler().post(new Runnable() {
            public void run() {
                Intent intent = new Intent(TrustSpaceManagerService.ACTION_INTENT_PREVENTED);
                intent.putExtra("component", str);
                intent.putExtra("callee", str2);
                intent.putExtra("caller", str3);
                intent.putExtra("calleeLevel", i);
                intent.putExtra("callerLevel", i2);
                intent.setPackage(TrustSpaceManagerService.HW_TRUSTSPACE_PACKAGENAME);
                TrustSpaceManagerService.this.mContext.startService(intent);
                Slog.d(TrustSpaceManagerService.TAG, "Notify intent prevented.");
            }
        });
    }

    private boolean isIntentProtectedAppInner(String packageName) {
        if (!calledFromValidUser() || packageName == null) {
            return false;
        }
        boolean isIntentProtectedApp;
        synchronized (this) {
            isIntentProtectedApp = this.mSettings.isIntentProtectedApp(packageName);
        }
        return isIntentProtectedApp;
    }

    public boolean addIntentProtectedApps(List<String> packages, int flags) {
        this.mContext.enforceCallingOrSelfPermission(MANAGE_TRUSTSPACE, null);
        if (!calledFromValidUser()) {
            return false;
        }
        synchronized (this) {
            for (String packageName : packages) {
                this.mSettings.addIntentProtectedApp(packageName, flags);
                if (HW_DEBUG) {
                    Slog.d(TAG, "add " + packageName + " to intent protected list, flags=" + flags);
                }
            }
            this.mSettings.writePackages();
        }
        return true;
    }

    public boolean removeIntentProtectedApp(String packageName) {
        this.mContext.enforceCallingOrSelfPermission(MANAGE_TRUSTSPACE, null);
        if (!calledFromValidUser() || packageName == null) {
            return false;
        }
        synchronized (this) {
            this.mSettings.removeIntentProtectedApp(packageName);
            this.mSettings.writePackages();
            if (HW_DEBUG) {
                Slog.d(TAG, "remove " + packageName + " from intent protected list");
            }
        }
        return true;
    }

    public List<String> getIntentProtectedApps(int flags) {
        this.mContext.enforceCallingOrSelfPermission(MANAGE_TRUSTSPACE, null);
        if (!calledFromValidUser()) {
            return new ArrayList(0);
        }
        List<String> intentProtectedApps;
        synchronized (this) {
            intentProtectedApps = this.mSettings.getIntentProtectedApps(flags);
        }
        return intentProtectedApps;
    }

    public boolean removeIntentProtectedApps(List<String> packages, int flags) {
        this.mContext.enforceCallingOrSelfPermission(MANAGE_TRUSTSPACE, null);
        if (!calledFromValidUser()) {
            return false;
        }
        synchronized (this) {
            this.mSettings.removeIntentProtectedApps(packages, flags);
            this.mSettings.writePackages();
            if (HW_DEBUG) {
                Slog.d(TAG, "remove apps in intent protected list, flag=" + flags);
            }
        }
        return true;
    }

    public boolean updateTrustApps(List<String> packages, int flag) {
        this.mContext.enforceCallingOrSelfPermission(MANAGE_TRUSTSPACE, null);
        if (!calledFromValidUser() || packages == null) {
            return false;
        }
        synchronized (this) {
            this.mSettings.updateTrustApps(packages, flag);
            this.mSettings.writePackages();
            if (HW_DEBUG) {
                Slog.d(TAG, "update trust apps, flag=" + flag);
            }
        }
        return true;
    }

    public boolean isIntentProtectedApp(String packageName) {
        this.mContext.enforceCallingOrSelfPermission(MANAGE_TRUSTSPACE, null);
        if (!calledFromValidUser() || packageName == null) {
            return false;
        }
        boolean isIntentProtectedApp;
        synchronized (this) {
            isIntentProtectedApp = this.mSettings.isIntentProtectedApp(packageName);
        }
        return isIntentProtectedApp;
    }

    public int getProtectionLevel(String packageName) {
        this.mContext.enforceCallingOrSelfPermission(MANAGE_TRUSTSPACE, null);
        if (!calledFromValidUser() || packageName == null) {
            return 0;
        }
        int protectionLevel;
        synchronized (this) {
            protectionLevel = this.mSettings.getProtectionLevel(packageName);
        }
        return protectionLevel;
    }

    public boolean isSupportTrustSpace() {
        this.mContext.enforceCallingOrSelfPermission(MANAGE_TRUSTSPACE, null);
        if (calledFromValidUser()) {
            return this.mSupportTrustSpace;
        }
        return false;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public boolean isHwTrustSpace(int userId) {
        boolean z = false;
        this.mContext.enforceCallingOrSelfPermission(MANAGE_TRUSTSPACE, null);
        if (!calledFromValidUser()) {
            return false;
        }
        long ident = Binder.clearCallingIdentity();
        try {
            UserInfo userInfo = ((IUserManager) ServiceManager.getService("user")).getUserInfo(userId);
            if (userInfo != null) {
                z = userInfo.isHwTrustSpace();
            }
            Binder.restoreCallingIdentity(ident);
            return z;
        } catch (RemoteException e) {
            Slog.d(TAG, "failed to getUserInfo");
            return false;
        } catch (Throwable th) {
            Binder.restoreCallingIdentity(ident);
        }
    }
}
