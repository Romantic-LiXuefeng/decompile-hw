package android.app;

import android.R;
import android.app.IActivityManager.ContentProviderHolder;
import android.app.assist.AssistContent;
import android.app.assist.AssistStructure;
import android.app.backup.BackupAgent;
import android.common.HwFrameworkFactory;
import android.common.HwFrameworkMonitor;
import android.content.BroadcastReceiver;
import android.content.BroadcastReceiver.PendingResult;
import android.content.ComponentCallbacks2;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.Context;
import android.content.IContentProvider;
import android.content.IIntentReceiver;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.InstrumentationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.content.res.AssetManager;
import android.content.res.CompatibilityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDebug;
import android.database.sqlite.SQLiteDebug.DbStats;
import android.database.sqlite.SQLiteDebug.PagerStats;
import android.ddm.DdmHandleAppName;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.hardware.display.DisplayManagerGlobal;
import android.hwtheme.HwThemeManager;
import android.net.ConnectivityManager;
import android.net.IConnectivityManager;
import android.net.Proxy;
import android.net.ProxyInfo;
import android.net.Uri;
import android.net.wifi.AnqpInformationElement;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.wifipro.NetworkHistoryUtils;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Debug.MemoryInfo;
import android.os.DropBoxManager;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.LocaleList;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue.IdleHandler;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.Trace;
import android.os.TransactionTooLargeException;
import android.os.UserHandle;
import android.provider.Settings.Global;
import android.provider.Settings.System;
import android.renderscript.RenderScriptCacheDir;
import android.rms.iaware.AwareNRTConstant;
import android.rog.AppRogInfo;
import android.rog.AppRogInfo.UpdateRog;
import android.rog.IHwRogListener.Stub;
import android.rog.IRogManager;
import android.scrollerboost.ScrollerBoostManager;
import android.security.NetworkSecurityPolicy;
import android.security.net.config.NetworkSecurityConfigProvider;
import android.service.notification.ZenModeConfig;
import android.util.ArrayMap;
import android.util.DisplayMetrics;
import android.util.EventLog;
import android.util.Flog;
import android.util.Jlog;
import android.util.Log;
import android.util.Pair;
import android.util.PrintWriterPrinter;
import android.util.Slog;
import android.util.SparseIntArray;
import android.util.SuperNotCalledException;
import android.view.ContextThemeWrapper;
import android.view.GraphicBuffer;
import android.view.ThreadedRenderer;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewManager;
import android.view.ViewRootImpl;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.WindowManagerGlobal;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.app.IVoiceInteractor;
import com.android.internal.content.ReferrerIntent;
import com.android.internal.os.BinderInternal;
import com.android.internal.os.RuntimeInit;
import com.android.internal.os.SamplingProfilerIntegration;
import com.android.internal.os.SomeArgs;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.FastPrintWriter;
import com.android.org.conscrypt.OpenSSLSocketImpl;
import com.android.org.conscrypt.TrustedCertificateStore;
import com.google.android.collect.Lists;
import com.huawei.pgmng.common.Utils;
import dalvik.system.CloseGuard;
import dalvik.system.VMDebug;
import dalvik.system.VMRuntime;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TimeZone;
import libcore.io.DropBox;
import libcore.io.DropBox.Reporter;
import libcore.io.EventLogger;
import libcore.io.IoUtils;
import libcore.net.event.NetworkEventDispatcher;
import org.apache.harmony.dalvik.ddmc.DdmVmInternal;

public final class ActivityThread {
    private static final int ACTIVITY_THREAD_CHECKIN_VERSION = 4;
    private static final boolean DEBUG_BACKUP = false;
    public static final boolean DEBUG_BROADCAST = false;
    public static final boolean DEBUG_CONFIGURATION = false;
    private static final boolean DEBUG_MEMORY_TRIM = false;
    static final boolean DEBUG_MESSAGES = false;
    private static final boolean DEBUG_ORDER = false;
    private static final boolean DEBUG_PROVIDER = false;
    private static final boolean DEBUG_RESULTS = false;
    private static final boolean DEBUG_SERVICE = false;
    private static final int DONT_REPORT = 2;
    private static final String HEAP_COLUMN = "%13s %8s %8s %8s %8s %8s %8s %8s";
    private static final String HEAP_FULL_COLUMN = "%13s %8s %8s %8s %8s %8s %8s %8s %8s %8s %8s";
    static final boolean HISI_PERF_OPT = SystemProperties.getBoolean("build.hisi_perf_opt", false);
    static final boolean IS_DEBUG_VERSION;
    private static final int LOG_AM_ON_PAUSE_CALLED = 30021;
    private static final int LOG_AM_ON_RESUME_CALLED = 30022;
    private static final int LOG_AM_ON_STOP_CALLED = 30049;
    private static final long MIN_TIME_BETWEEN_GCS = 5000;
    private static final String ONE_COUNT_COLUMN = "%21s %8d";
    private static final String ONE_COUNT_COLUMN_HEADER = "%21s %8s";
    private static final boolean REPORT_TO_ACTIVITY = true;
    public static final int SERVICE_DONE_EXECUTING_ANON = 0;
    public static final int SERVICE_DONE_EXECUTING_START = 1;
    public static final int SERVICE_DONE_EXECUTING_STOP = 2;
    private static final int SQLITE_MEM_RELEASED_EVENT_LOG_TAG = 75003;
    public static final String TAG = "ActivityThread";
    private static final Config THUMBNAIL_FORMAT = Config.RGB_565;
    private static final String TWO_COUNT_COLUMNS = "%21s %8d %21s %8d";
    private static final int USER_LEAVING = 1;
    static final boolean localLOGV = false;
    private static boolean mChangedFont = false;
    private static volatile ActivityThread sCurrentActivityThread;
    private static final ThreadLocal<Intent> sCurrentBroadcastIntent = new ThreadLocal();
    static volatile Handler sMainThreadHandler;
    static volatile IPackageManager sPackageManager;
    final ArrayMap<ProviderKey, AcquiringProviderRecord> mAcquiringProviderMap = new ArrayMap();
    final ArrayMap<IBinder, ActivityClientRecord> mActivities = new ArrayMap();
    final ArrayList<Application> mAllApplications = new ArrayList();
    final ApplicationThread mAppThread = new ApplicationThread();
    private Bitmap mAvailThumbnailBitmap = null;
    final ArrayMap<String, BackupAgent> mBackupAgents = new ArrayMap();
    AppBindData mBoundApplication;
    Configuration mCompatConfiguration;
    Configuration mConfiguration;
    Bundle mCoreSettings = null;
    int mCurDefaultDisplayDpi;
    boolean mDensityCompatMode;
    final GcIdler mGcIdler = new GcIdler();
    boolean mGcIdlerScheduled = false;
    final H mH = new H();
    private boolean mHasReadRogPro = false;
    Application mInitialApplication;
    Instrumentation mInstrumentation;
    String mInstrumentationAppDir = null;
    String mInstrumentationLibDir = null;
    String mInstrumentationPackageName = null;
    String[] mInstrumentationSplitAppDirs = null;
    String mInstrumentedAppDir = null;
    String mInstrumentedLibDir = null;
    String[] mInstrumentedSplitAppDirs = null;
    private boolean mIsInMultiWindowMode = false;
    boolean mJitEnabled = false;
    ArrayList<WeakReference<AssistStructure>> mLastAssistStructures = new ArrayList();
    private int mLastSessionId;
    @GuardedBy("mResourcesManager")
    int mLifecycleSeq = 0;
    final ArrayMap<IBinder, ProviderClientRecord> mLocalProviders = new ArrayMap();
    final ArrayMap<ComponentName, ProviderClientRecord> mLocalProvidersByName = new ArrayMap();
    final Looper mLooper = Looper.myLooper();
    private Configuration mMainThreadConfig = new Configuration();
    ActivityClientRecord mNewActivities = null;
    int mNumVisibleActivities = 0;
    final ArrayMap<Activity, ArrayList<OnActivityPausedListener>> mOnPauseListeners = new ArrayMap();
    final ArrayMap<String, WeakReference<LoadedApk>> mPackages = new ArrayMap();
    Configuration mPendingConfiguration = null;
    Profiler mProfiler;
    final ArrayMap<ProviderKey, ProviderClientRecord> mProviderMap = new ArrayMap();
    final ArrayMap<IBinder, ProviderRefCount> mProviderRefCountMap = new ArrayMap();
    final ArrayList<ActivityClientRecord> mRelaunchingActivities = new ArrayList();
    final ArrayMap<String, WeakReference<LoadedApk>> mResourcePackages = new ArrayMap();
    private final ResourcesManager mResourcesManager = ResourcesManager.getInstance();
    private HashMap<String, HwRogListener> mRogListenerSet = new HashMap();
    private boolean mRogSupported = false;
    final ArrayMap<IBinder, Service> mServices = new ArrayMap();
    boolean mSomeActivitiesChanged = false;
    private ContextImpl mSystemContext;
    boolean mSystemThread = false;
    private Canvas mThumbnailCanvas = null;
    private int mThumbnailHeight = -1;
    private int mThumbnailWidth = -1;

    static final class AcquiringProviderRecord {
        boolean acquiring = true;
        int requests = 1;

        AcquiringProviderRecord() {
        }
    }

    static final class ActivityClientRecord {
        Activity activity;
        ActivityInfo activityInfo;
        CompatibilityInfo compatInfo;
        Configuration createdConfig;
        String embeddedID = null;
        boolean hideForNow = false;
        int ident;
        Intent intent;
        boolean isForward;
        NonConfigurationInstances lastNonConfigurationInstances;
        int lastProcessedSeq = 0;
        Window mPendingRemoveWindow;
        WindowManager mPendingRemoveWindowManager;
        boolean mPreserveWindow;
        Configuration newConfig;
        ActivityClientRecord nextIdle = null;
        boolean onlyLocalRequest;
        Configuration overrideConfig;
        LoadedApk packageInfo;
        Activity parent = null;
        boolean paused = false;
        int pendingConfigChanges;
        List<ReferrerIntent> pendingIntents;
        List<ResultInfo> pendingResults;
        PersistableBundle persistentState;
        ProfilerInfo profilerInfo;
        String referrer;
        int relaunchSeq = 0;
        boolean startsNotResumed;
        Bundle state;
        boolean stopped = false;
        private Configuration tmpConfig = new Configuration();
        IBinder token;
        IVoiceInteractor voiceInteractor;
        Window window;

        ActivityClientRecord() {
        }

        public boolean isPreHoneycomb() {
            boolean z = false;
            if (this.activity == null) {
                return false;
            }
            if (this.activity.getApplicationInfo().targetSdkVersion < 11) {
                z = true;
            }
            return z;
        }

        public boolean isPersistable() {
            return this.activityInfo.persistableMode == 2;
        }

        public String toString() {
            ComponentName componentName = this.intent != null ? this.intent.getComponent() : null;
            return "ActivityRecord{" + Integer.toHexString(System.identityHashCode(this)) + " token=" + this.token + WifiEnterpriseConfig.CA_CERT_ALIAS_DELIMITER + (componentName == null ? "no component name" : componentName.toShortString()) + "}";
        }

        public String getStateString() {
            StringBuilder sb = new StringBuilder();
            sb.append("ActivityClientRecord{");
            sb.append("paused=").append(this.paused);
            sb.append(", stopped=").append(this.stopped);
            sb.append(", hideForNow=").append(this.hideForNow);
            sb.append(", startsNotResumed=").append(this.startsNotResumed);
            sb.append(", isForward=").append(this.isForward);
            sb.append(", pendingConfigChanges=").append(this.pendingConfigChanges);
            sb.append(", onlyLocalRequest=").append(this.onlyLocalRequest);
            sb.append(", preserveWindow=").append(this.mPreserveWindow);
            if (this.activity != null) {
                sb.append(", Activity{");
                sb.append("resumed=").append(this.activity.mResumed);
                sb.append(", stopped=").append(this.activity.mStopped);
                sb.append(", finished=").append(this.activity.isFinishing());
                sb.append(", destroyed=").append(this.activity.isDestroyed());
                sb.append(", startedActivity=").append(this.activity.mStartedActivity);
                sb.append(", temporaryPause=").append(this.activity.mTemporaryPause);
                sb.append(", changingConfigurations=").append(this.activity.mChangingConfigurations);
                sb.append(", visibleBehind=").append(this.activity.mVisibleBehind);
                sb.append("}");
            }
            sb.append("}");
            return sb.toString();
        }
    }

    static final class ActivityConfigChangeData {
        final IBinder activityToken;
        final Configuration overrideConfig;

        public ActivityConfigChangeData(IBinder token, Configuration config) {
            this.activityToken = token;
            this.overrideConfig = config;
        }
    }

    static final class AppBindData {
        ApplicationInfo appInfo;
        CompatibilityInfo compatInfo;
        Configuration config;
        int debugMode;
        boolean enableBinderTracking;
        LoadedApk info;
        ProfilerInfo initProfilerInfo;
        Bundle instrumentationArgs;
        ComponentName instrumentationName;
        IUiAutomationConnection instrumentationUiAutomationConnection;
        IInstrumentationWatcher instrumentationWatcher;
        boolean persistent;
        String processName;
        List<ProviderInfo> providers;
        boolean restrictedBackupMode;
        boolean trackAllocation;

        AppBindData() {
        }

        public String toString() {
            return "AppBindData{appInfo=" + this.appInfo + "}";
        }
    }

    private class ApplicationThread extends ApplicationThreadNative {
        private static final String DB_INFO_FORMAT = "  %8s %8s %14s %14s  %s";
        private int mLastProcessState;

        private ApplicationThread() {
            this.mLastProcessState = -1;
        }

        private void updatePendingConfiguration(Configuration config) {
            synchronized (ActivityThread.this.mResourcesManager) {
                if (ActivityThread.this.mPendingConfiguration == null || ActivityThread.this.mPendingConfiguration.isOtherSeqNewer(config)) {
                    ActivityThread.this.mPendingConfiguration = config;
                }
            }
        }

        public final void schedulePauseActivity(IBinder token, boolean finished, boolean userLeaving, int configChanges, boolean dontReport) {
            int i;
            int i2 = 0;
            int seq = ActivityThread.this.getLifecycleSeq();
            if (Jlog.isPerfTest()) {
                Jlog.i(2025, "whopid=" + Process.myPid());
            }
            ActivityThread activityThread = ActivityThread.this;
            int i3 = finished ? 102 : 101;
            if (userLeaving) {
                i = 1;
            } else {
                i = 0;
            }
            if (dontReport) {
                i2 = 2;
            }
            activityThread.sendMessage(i3, (Object) token, i | i2, configChanges, seq);
        }

        public final void scheduleStopActivity(IBinder token, boolean showWindow, int configChanges) {
            ActivityThread.this.sendMessage(showWindow ? 103 : 104, (Object) token, 0, configChanges, ActivityThread.this.getLifecycleSeq());
        }

        public final void scheduleWindowVisibility(IBinder token, boolean showWindow) {
            ActivityThread.this.sendMessage(showWindow ? 105 : 106, token);
        }

        public final void scheduleSleeping(IBinder token, boolean sleeping) {
            ActivityThread.this.sendMessage(137, token, sleeping ? 1 : 0);
        }

        public final void scheduleResumeActivity(IBinder token, int processState, boolean isForward, Bundle resumeArgs) {
            int i;
            int seq = ActivityThread.this.getLifecycleSeq();
            if (Jlog.isPerfTest()) {
                Jlog.i(2043, "pid=" + Process.myPid());
            }
            updateProcessState(processState, false);
            ActivityThread activityThread = ActivityThread.this;
            if (isForward) {
                i = 1;
            } else {
                i = 0;
            }
            activityThread.sendMessage(107, (Object) token, i, 0, seq);
        }

        public final void scheduleSendResult(IBinder token, List<ResultInfo> results) {
            ResultData res = new ResultData();
            res.token = token;
            res.results = results;
            ActivityThread.this.sendMessage(108, res);
        }

        public final void scheduleLaunchActivity(Intent intent, IBinder token, int ident, ActivityInfo info, Configuration curConfig, Configuration overrideConfig, CompatibilityInfo compatInfo, String referrer, IVoiceInteractor voiceInteractor, int procState, Bundle state, PersistableBundle persistentState, List<ResultInfo> pendingResults, List<ReferrerIntent> pendingNewIntents, boolean notResumed, boolean isForward, ProfilerInfo profilerInfo) {
            if (Jlog.isPerfTest()) {
                Jlog.i(2036, Intent.toPkgClsString(intent));
            }
            updateProcessState(procState, false);
            ActivityClientRecord r = new ActivityClientRecord();
            r.token = token;
            r.ident = ident;
            r.intent = intent;
            r.referrer = referrer;
            r.voiceInteractor = voiceInteractor;
            r.activityInfo = info;
            r.compatInfo = compatInfo;
            r.state = state;
            r.persistentState = persistentState;
            r.pendingResults = pendingResults;
            r.pendingIntents = pendingNewIntents;
            r.startsNotResumed = notResumed;
            r.isForward = isForward;
            r.profilerInfo = profilerInfo;
            r.overrideConfig = overrideConfig;
            updatePendingConfiguration(curConfig);
            ActivityThread.this.sendMessage(100, r);
        }

        public final void scheduleRelaunchActivity(IBinder token, List<ResultInfo> pendingResults, List<ReferrerIntent> pendingNewIntents, int configChanges, boolean notResumed, Configuration config, Configuration overrideConfig, boolean preserveWindow) {
            ActivityThread.this.requestRelaunchActivity(token, pendingResults, pendingNewIntents, configChanges, notResumed, config, overrideConfig, true, preserveWindow);
        }

        public final void scheduleNewIntent(List<ReferrerIntent> intents, IBinder token) {
            NewIntentData data = new NewIntentData();
            data.intents = intents;
            data.token = token;
            ActivityThread.this.sendMessage(112, data);
        }

        public final void scheduleDestroyActivity(IBinder token, boolean finishing, int configChanges) {
            ActivityThread.this.sendMessage(109, token, finishing ? 1 : 0, configChanges);
        }

        public final void scheduleReceiver(Intent intent, ActivityInfo info, CompatibilityInfo compatInfo, int resultCode, String data, Bundle extras, boolean sync, int sendingUser, int processState) {
            updateProcessState(processState, false);
            ReceiverData r = new ReceiverData(intent, resultCode, data, extras, sync, false, ActivityThread.this.mAppThread.asBinder(), sendingUser);
            r.info = info;
            r.compatInfo = compatInfo;
            ActivityThread.this.sendMessage(113, r);
        }

        public final void scheduleCreateBackupAgent(ApplicationInfo app, CompatibilityInfo compatInfo, int backupMode) {
            CreateBackupAgentData d = new CreateBackupAgentData();
            d.appInfo = app;
            d.compatInfo = compatInfo;
            d.backupMode = backupMode;
            ActivityThread.this.sendMessage(128, d);
        }

        public final void scheduleDestroyBackupAgent(ApplicationInfo app, CompatibilityInfo compatInfo) {
            CreateBackupAgentData d = new CreateBackupAgentData();
            d.appInfo = app;
            d.compatInfo = compatInfo;
            ActivityThread.this.sendMessage(129, d);
        }

        public final void scheduleCreateService(IBinder token, ServiceInfo info, CompatibilityInfo compatInfo, int processState) {
            updateProcessState(processState, false);
            CreateServiceData s = new CreateServiceData();
            s.token = token;
            s.info = info;
            s.compatInfo = compatInfo;
            ActivityThread.this.sendMessage(114, s);
        }

        public final void scheduleBindService(IBinder token, Intent intent, boolean rebind, int processState) {
            updateProcessState(processState, false);
            BindServiceData s = new BindServiceData();
            s.token = token;
            s.intent = intent;
            s.rebind = rebind;
            ActivityThread.this.sendMessage(121, s);
        }

        public final void scheduleUnbindService(IBinder token, Intent intent) {
            BindServiceData s = new BindServiceData();
            s.token = token;
            s.intent = intent;
            ActivityThread.this.sendMessage(122, s);
        }

        public final void scheduleServiceArgs(IBinder token, boolean taskRemoved, int startId, int flags, Intent args) {
            ServiceArgsData s = new ServiceArgsData();
            s.token = token;
            s.taskRemoved = taskRemoved;
            s.startId = startId;
            s.flags = flags;
            s.args = args;
            ActivityThread.this.sendMessage(115, s);
        }

        public final void scheduleStopService(IBinder token) {
            ActivityThread.this.sendMessage(116, token);
        }

        public final void bindApplication(String processName, ApplicationInfo appInfo, List<ProviderInfo> providers, ComponentName instrumentationName, ProfilerInfo profilerInfo, Bundle instrumentationArgs, IInstrumentationWatcher instrumentationWatcher, IUiAutomationConnection instrumentationUiConnection, int debugMode, boolean enableBinderTracking, boolean trackAllocation, boolean isRestrictedBackupMode, boolean persistent, Configuration config, CompatibilityInfo compatInfo, Map<String, IBinder> services, Bundle coreSettings) {
            if (Jlog.isPerfTest()) {
                Jlog.i(2034, "pid=" + Process.myPid() + "&processname=" + processName);
            }
            if (services != null) {
                ServiceManager.initServiceCache(services);
            }
            setCoreSettings(coreSettings);
            AppBindData data = new AppBindData();
            data.processName = processName;
            data.appInfo = appInfo;
            data.providers = providers;
            data.instrumentationName = instrumentationName;
            data.instrumentationArgs = instrumentationArgs;
            data.instrumentationWatcher = instrumentationWatcher;
            data.instrumentationUiAutomationConnection = instrumentationUiConnection;
            data.debugMode = debugMode;
            data.enableBinderTracking = enableBinderTracking;
            data.trackAllocation = trackAllocation;
            data.restrictedBackupMode = isRestrictedBackupMode;
            data.persistent = persistent;
            data.config = config;
            data.compatInfo = compatInfo;
            data.initProfilerInfo = profilerInfo;
            ActivityThread.this.sendMessage(110, data);
        }

        public final void scheduleExit() {
            ActivityThread.this.sendMessage(111, null);
        }

        public final void scheduleSuicide() {
            ActivityThread.this.sendMessage(130, null);
        }

        public void scheduleConfigurationChanged(Configuration config) {
            updatePendingConfiguration(config);
            ActivityThread.this.sendMessage(118, config);
        }

        public void updateTimeZone() {
            TimeZone.setDefault(null);
        }

        public void clearDnsCache() {
            InetAddress.clearDnsCache();
            NetworkEventDispatcher.getInstance().onNetworkConfigurationChanged();
        }

        public void setHttpProxy(String host, String port, String exclList, Uri pacFileUrl) {
            ConnectivityManager cm = ConnectivityManager.from(ActivityThread.this.getSystemContext());
            if (cm.getBoundNetworkForProcess() != null) {
                Proxy.setHttpProxySystemProperty(cm.getDefaultProxy());
            } else {
                Proxy.setHttpProxySystemProperty(host, port, exclList, pacFileUrl);
            }
        }

        public void processInBackground() {
            ActivityThread.this.mH.removeMessages(120);
            ActivityThread.this.mH.sendMessage(ActivityThread.this.mH.obtainMessage(120));
        }

        public void dumpService(FileDescriptor fd, IBinder servicetoken, String[] args) {
            DumpComponentInfo data = new DumpComponentInfo();
            try {
                data.fd = ParcelFileDescriptor.dup(fd);
                data.token = servicetoken;
                data.args = args;
                ActivityThread.this.sendMessage(123, (Object) data, 0, 0, true);
            } catch (IOException e) {
                Slog.w(ActivityThread.TAG, "dumpService failed", e);
            }
        }

        public void scheduleRegisteredReceiver(IIntentReceiver receiver, Intent intent, int resultCode, String dataStr, Bundle extras, boolean ordered, boolean sticky, int sendingUser, int processState) throws RemoteException {
            updateProcessState(processState, false);
            receiver.performReceive(intent, resultCode, dataStr, extras, ordered, sticky, sendingUser);
        }

        public void scheduleLowMemory() {
            ActivityThread.this.sendMessage(124, null);
        }

        public void scheduleActivityConfigurationChanged(IBinder token, Configuration overrideConfig, boolean reportToActivity) {
            ActivityThread.this.sendMessage(125, new ActivityConfigChangeData(token, overrideConfig), reportToActivity ? 1 : 0);
        }

        public void profilerControl(boolean start, ProfilerInfo profilerInfo, int profileType) {
            ActivityThread.this.sendMessage(127, profilerInfo, start ? 1 : 0, profileType);
        }

        public void dumpHeap(boolean managed, String path, ParcelFileDescriptor fd) {
            int i;
            DumpHeapData dhd = new DumpHeapData();
            dhd.path = path;
            dhd.fd = fd;
            ActivityThread activityThread = ActivityThread.this;
            if (managed) {
                i = 1;
            } else {
                i = 0;
            }
            activityThread.sendMessage(135, (Object) dhd, i, 0, true);
        }

        public void setSchedulingGroup(int group) {
            try {
                Process.setProcessGroup(Process.myPid(), group);
            } catch (Exception e) {
                Slog.w(ActivityThread.TAG, "Failed setting process group to " + group, e);
            }
        }

        public void dispatchPackageBroadcast(int cmd, String[] packages) {
            ActivityThread.this.sendMessage(133, packages, cmd);
        }

        public void scheduleCrash(String msg) {
            ActivityThread.this.sendMessage(134, msg);
        }

        public void dumpActivity(FileDescriptor fd, IBinder activitytoken, String prefix, String[] args) {
            DumpComponentInfo data = new DumpComponentInfo();
            try {
                data.fd = ParcelFileDescriptor.dup(fd);
                data.token = activitytoken;
                data.prefix = prefix;
                data.args = args;
                ActivityThread.this.sendMessage(136, (Object) data, 0, 0, true);
            } catch (IOException e) {
                Slog.w(ActivityThread.TAG, "dumpActivity failed", e);
            }
        }

        public void dumpProvider(FileDescriptor fd, IBinder providertoken, String[] args) {
            DumpComponentInfo data = new DumpComponentInfo();
            try {
                data.fd = ParcelFileDescriptor.dup(fd);
                data.token = providertoken;
                data.args = args;
                ActivityThread.this.sendMessage(141, (Object) data, 0, 0, true);
            } catch (IOException e) {
                Slog.w(ActivityThread.TAG, "dumpProvider failed", e);
            }
        }

        public void dumpMemInfo(FileDescriptor fd, MemoryInfo mem, boolean checkin, boolean dumpFullInfo, boolean dumpDalvik, boolean dumpSummaryOnly, boolean dumpUnreachable, String[] args) {
            if (fd != null) {
                PrintWriter pw = new FastPrintWriter(new FileOutputStream(fd));
                try {
                    dumpMemInfo(pw, mem, checkin, dumpFullInfo, dumpDalvik, dumpSummaryOnly, dumpUnreachable);
                } finally {
                    pw.flush();
                }
            }
        }

        private void dumpMemInfo(PrintWriter pw, MemoryInfo memInfo, boolean checkin, boolean dumpFullInfo, boolean dumpDalvik, boolean dumpSummaryOnly, boolean dumpUnreachable) {
            long nativeMax = Debug.getNativeHeapSize() / 1024;
            long nativeAllocated = Debug.getNativeHeapAllocatedSize() / 1024;
            long nativeFree = Debug.getNativeHeapFreeSize() / 1024;
            Runtime runtime = Runtime.getRuntime();
            runtime.gc();
            long dalvikMax = runtime.totalMemory() / 1024;
            long dalvikFree = runtime.freeMemory() / 1024;
            long dalvikAllocated = dalvikMax - dalvikFree;
            long viewInstanceCount = ViewDebug.getViewInstanceCount();
            long viewRootInstanceCount = ViewDebug.getViewRootImplCount();
            long appContextInstanceCount = Debug.countInstancesOfClass(ContextImpl.class);
            long activityInstanceCount = Debug.countInstancesOfClass(Activity.class);
            int globalAssetCount = AssetManager.getGlobalAssetCount();
            int globalAssetManagerCount = AssetManager.getGlobalAssetManagerCount();
            int binderLocalObjectCount = Debug.getBinderLocalObjectCount();
            int binderProxyObjectCount = Debug.getBinderProxyObjectCount();
            int binderDeathObjectCount = Debug.getBinderDeathObjectCount();
            long parcelSize = Parcel.getGlobalAllocSize();
            long parcelCount = Parcel.getGlobalAllocCount();
            long openSslSocketCount = Debug.countInstancesOfClass(OpenSSLSocketImpl.class);
            PagerStats stats = SQLiteDebug.getDatabaseInfo();
            ActivityThread.dumpMemInfoTable(pw, memInfo, checkin, dumpFullInfo, dumpDalvik, dumpSummaryOnly, Process.myPid(), ActivityThread.this.mBoundApplication != null ? ActivityThread.this.mBoundApplication.processName : "unknown", nativeMax, nativeAllocated, nativeFree, dalvikMax, dalvikAllocated, dalvikFree);
            int i;
            if (checkin) {
                pw.print(viewInstanceCount);
                pw.print(',');
                pw.print(viewRootInstanceCount);
                pw.print(',');
                pw.print(appContextInstanceCount);
                pw.print(',');
                pw.print(activityInstanceCount);
                pw.print(',');
                pw.print(globalAssetCount);
                pw.print(',');
                pw.print(globalAssetManagerCount);
                pw.print(',');
                pw.print(binderLocalObjectCount);
                pw.print(',');
                pw.print(binderProxyObjectCount);
                pw.print(',');
                pw.print(binderDeathObjectCount);
                pw.print(',');
                pw.print(openSslSocketCount);
                pw.print(',');
                pw.print(stats.memoryUsed / 1024);
                pw.print(',');
                pw.print(stats.memoryUsed / 1024);
                pw.print(',');
                pw.print(stats.pageCacheOverflow / 1024);
                pw.print(',');
                pw.print(stats.largestMemAlloc / 1024);
                for (i = 0; i < stats.dbStats.size(); i++) {
                    DbStats dbStats = (DbStats) stats.dbStats.get(i);
                    pw.print(',');
                    pw.print(dbStats.dbName);
                    pw.print(',');
                    pw.print(dbStats.pageSize);
                    pw.print(',');
                    pw.print(dbStats.dbSize);
                    pw.print(',');
                    pw.print(dbStats.lookaside);
                    pw.print(',');
                    pw.print(dbStats.cache);
                    pw.print(',');
                    pw.print(dbStats.cache);
                }
                pw.println();
                return;
            }
            pw.println(WifiEnterpriseConfig.CA_CERT_ALIAS_DELIMITER);
            pw.println(" Objects");
            ActivityThread.printRow(pw, ActivityThread.TWO_COUNT_COLUMNS, "Views:", Long.valueOf(viewInstanceCount), "ViewRootImpl:", Long.valueOf(viewRootInstanceCount));
            ActivityThread.printRow(pw, ActivityThread.TWO_COUNT_COLUMNS, "AppContexts:", Long.valueOf(appContextInstanceCount), "Activities:", Long.valueOf(activityInstanceCount));
            ActivityThread.printRow(pw, ActivityThread.TWO_COUNT_COLUMNS, "Assets:", Integer.valueOf(globalAssetCount), "AssetManagers:", Integer.valueOf(globalAssetManagerCount));
            ActivityThread.printRow(pw, ActivityThread.TWO_COUNT_COLUMNS, "Local Binders:", Integer.valueOf(binderLocalObjectCount), "Proxy Binders:", Integer.valueOf(binderProxyObjectCount));
            ActivityThread.printRow(pw, ActivityThread.TWO_COUNT_COLUMNS, "Parcel memory:", Long.valueOf(parcelSize / 1024), "Parcel count:", Long.valueOf(parcelCount));
            ActivityThread.printRow(pw, ActivityThread.TWO_COUNT_COLUMNS, "Death Recipients:", Integer.valueOf(binderDeathObjectCount), "OpenSSL Sockets:", Long.valueOf(openSslSocketCount));
            pw.println(WifiEnterpriseConfig.CA_CERT_ALIAS_DELIMITER);
            pw.println(" SQL");
            ActivityThread.printRow(pw, ActivityThread.ONE_COUNT_COLUMN, "MEMORY_USED:", Integer.valueOf(stats.memoryUsed / 1024));
            ActivityThread.printRow(pw, ActivityThread.TWO_COUNT_COLUMNS, "PAGECACHE_OVERFLOW:", Integer.valueOf(stats.pageCacheOverflow / 1024), "MALLOC_SIZE:", Integer.valueOf(stats.largestMemAlloc / 1024));
            pw.println(WifiEnterpriseConfig.CA_CERT_ALIAS_DELIMITER);
            int N = stats.dbStats.size();
            if (N > 0) {
                pw.println(" DATABASES");
                ActivityThread.printRow(pw, DB_INFO_FORMAT, "pgsz", "dbsz", "Lookaside(b)", "cache", "Dbname");
                for (i = 0; i < N; i++) {
                    dbStats = (DbStats) stats.dbStats.get(i);
                    String str = DB_INFO_FORMAT;
                    Object[] objArr = new Object[5];
                    objArr[0] = dbStats.pageSize > 0 ? String.valueOf(dbStats.pageSize) : WifiEnterpriseConfig.CA_CERT_ALIAS_DELIMITER;
                    objArr[1] = dbStats.dbSize > 0 ? String.valueOf(dbStats.dbSize) : WifiEnterpriseConfig.CA_CERT_ALIAS_DELIMITER;
                    objArr[2] = dbStats.lookaside > 0 ? String.valueOf(dbStats.lookaside) : WifiEnterpriseConfig.CA_CERT_ALIAS_DELIMITER;
                    objArr[3] = dbStats.cache;
                    objArr[4] = dbStats.dbName;
                    ActivityThread.printRow(pw, str, objArr);
                }
            }
            String assetAlloc = AssetManager.getAssetAllocations();
            if (assetAlloc != null) {
                pw.println(WifiEnterpriseConfig.CA_CERT_ALIAS_DELIMITER);
                pw.println(" Asset Allocations");
                pw.print(assetAlloc);
            }
            if (dumpUnreachable) {
                boolean showContents;
                if (ActivityThread.this.mBoundApplication == null || (ActivityThread.this.mBoundApplication.appInfo.flags & 2) == 0) {
                    showContents = Build.IS_DEBUGGABLE;
                } else {
                    showContents = true;
                }
                pw.println(WifiEnterpriseConfig.CA_CERT_ALIAS_DELIMITER);
                pw.println(" Unreachable memory");
                pw.print(Debug.getUnreachableMemory(100, showContents));
            }
        }

        public void dumpGfxInfo(FileDescriptor fd, String[] args) {
            ActivityThread.this.dumpGraphicsInfo(fd);
            WindowManagerGlobal.getInstance().dumpGfxInfo(fd, args);
        }

        private void dumpDatabaseInfo(FileDescriptor fd, String[] args) {
            PrintWriter pw = new FastPrintWriter(new FileOutputStream(fd));
            SQLiteDebug.dump(new PrintWriterPrinter(pw), args);
            pw.flush();
        }

        public void dumpDbInfo(FileDescriptor fd, final String[] args) {
            if (ActivityThread.this.mSystemThread) {
                try {
                    final ParcelFileDescriptor dup = ParcelFileDescriptor.dup(fd);
                    AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
                        public void run() {
                            try {
                                ApplicationThread.this.dumpDatabaseInfo(dup.getFileDescriptor(), args);
                            } finally {
                                IoUtils.closeQuietly(dup);
                            }
                        }
                    });
                } catch (IOException e) {
                    Log.w(ActivityThread.TAG, "Could not dup FD " + fd.getInt$());
                    return;
                }
            }
            dumpDatabaseInfo(fd, args);
        }

        public void unstableProviderDied(IBinder provider) {
            ActivityThread.this.sendMessage(142, provider);
        }

        public void requestAssistContextExtras(IBinder activityToken, IBinder requestToken, int requestType, int sessionId) {
            RequestAssistContextExtras cmd = new RequestAssistContextExtras();
            cmd.activityToken = activityToken;
            cmd.requestToken = requestToken;
            cmd.requestType = requestType;
            cmd.sessionId = sessionId;
            ActivityThread.this.sendMessage(143, cmd);
        }

        public void setCoreSettings(Bundle coreSettings) {
            ActivityThread.this.sendMessage(138, coreSettings);
        }

        public void updatePackageCompatibilityInfo(String pkg, CompatibilityInfo info) {
            UpdateCompatibilityData ucd = new UpdateCompatibilityData();
            ucd.pkg = pkg;
            ucd.info = info;
            ActivityThread.this.sendMessage(139, ucd);
        }

        public void scheduleTrimMemory(int level) {
            ActivityThread.this.sendMessage(140, null, level);
        }

        public void scheduleTranslucentConversionComplete(IBinder token, boolean drawComplete) {
            ActivityThread.this.sendMessage(144, token, drawComplete ? 1 : 0);
        }

        public void scheduleOnNewActivityOptions(IBinder token, ActivityOptions options) {
            ActivityThread.this.sendMessage(146, new Pair(token, options));
        }

        public void setProcessState(int state) {
            updateProcessState(state, true);
        }

        public void updateProcessState(int processState, boolean fromIpc) {
            synchronized (this) {
                if (this.mLastProcessState != processState) {
                    this.mLastProcessState = processState;
                    int dalvikProcessState = 1;
                    if (processState <= 6) {
                        dalvikProcessState = 0;
                    }
                    VMRuntime.getRuntime().updateProcessState(dalvikProcessState);
                }
            }
        }

        public void scheduleInstallProvider(ProviderInfo provider) {
            ActivityThread.this.sendMessage(145, provider);
        }

        public final void updateTimePrefs(boolean is24Hour) {
            DateFormat.set24HourTimePref(is24Hour);
        }

        public void scheduleCancelVisibleBehind(IBinder token) {
            ActivityThread.this.sendMessage(147, token);
        }

        public void scheduleBackgroundVisibleBehindChanged(IBinder token, boolean visible) {
            ActivityThread.this.sendMessage(148, token, visible ? 1 : 0);
        }

        public void scheduleEnterAnimationComplete(IBinder token) {
            ActivityThread.this.sendMessage(149, token);
        }

        public void notifyCleartextNetwork(byte[] firstPacket) {
            if (StrictMode.vmCleartextNetworkEnabled()) {
                StrictMode.onCleartextNetworkDetected(firstPacket);
            }
        }

        public void startBinderTracking() {
            ActivityThread.this.sendMessage(150, null);
        }

        public void stopBinderTrackingAndDump(FileDescriptor fd) {
            try {
                ActivityThread.this.sendMessage(151, ParcelFileDescriptor.dup(fd));
            } catch (IOException e) {
            }
        }

        public void scheduleMultiWindowModeChanged(IBinder token, boolean isInMultiWindowMode) throws RemoteException {
            ActivityThread.this.sendMessage(152, token, isInMultiWindowMode ? 1 : 0);
        }

        public void schedulePictureInPictureModeChanged(IBinder token, boolean isInPipMode) throws RemoteException {
            ActivityThread.this.sendMessage(153, token, isInPipMode ? 1 : 0);
        }

        public void scheduleLocalVoiceInteractionStarted(IBinder token, IVoiceInteractor voiceInteractor) throws RemoteException {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = token;
            args.arg2 = voiceInteractor;
            ActivityThread.this.sendMessage(154, args);
        }
    }

    static final class BindServiceData {
        Intent intent;
        boolean rebind;
        IBinder token;

        BindServiceData() {
        }

        public String toString() {
            return "BindServiceData{token=" + this.token + " intent=" + this.intent + "}";
        }
    }

    static final class ContextCleanupInfo {
        ContextImpl context;
        String what;
        String who;

        ContextCleanupInfo() {
        }
    }

    static final class CreateBackupAgentData {
        ApplicationInfo appInfo;
        int backupMode;
        CompatibilityInfo compatInfo;

        CreateBackupAgentData() {
        }

        public String toString() {
            return "CreateBackupAgentData{appInfo=" + this.appInfo + " backupAgent=" + this.appInfo.backupAgentName + " mode=" + this.backupMode + "}";
        }
    }

    static final class CreateServiceData {
        CompatibilityInfo compatInfo;
        ServiceInfo info;
        Intent intent;
        IBinder token;

        CreateServiceData() {
        }

        public String toString() {
            return "CreateServiceData{token=" + this.token + " className=" + this.info.name + " packageName=" + this.info.packageName + " intent=" + this.intent + "}";
        }
    }

    private class DropBoxReporter implements Reporter {
        private DropBoxManager dropBox;

        public void addData(String tag, byte[] data, int flags) {
            ensureInitialized();
            this.dropBox.addData(tag, data, flags);
        }

        public void addText(String tag, String data) {
            ensureInitialized();
            this.dropBox.addText(tag, data);
        }

        private synchronized void ensureInitialized() {
            if (this.dropBox == null) {
                this.dropBox = (DropBoxManager) ActivityThread.this.getSystemContext().getSystemService(Context.DROPBOX_SERVICE);
            }
        }
    }

    static final class DumpComponentInfo {
        String[] args;
        ParcelFileDescriptor fd;
        String prefix;
        IBinder token;

        DumpComponentInfo() {
        }
    }

    static final class DumpHeapData {
        ParcelFileDescriptor fd;
        String path;

        DumpHeapData() {
        }
    }

    private static class EventLoggingReporter implements EventLogger.Reporter {
        private EventLoggingReporter() {
        }

        public void report(int code, Object... list) {
            EventLog.writeEvent(code, list);
        }
    }

    final class GcIdler implements IdleHandler {
        GcIdler() {
        }

        public final boolean queueIdle() {
            ActivityThread.this.doGcIfNeeded();
            return false;
        }
    }

    private static class GraphicBufferCreate implements Runnable {
        private GraphicBufferCreate() {
        }

        public void run() {
            GraphicBuffer buf = GraphicBuffer.create(16, 16, 1, 256);
            if (buf != null) {
                Canvas c = buf.lockCanvas();
                if (c != null) {
                    buf.unlockCanvasAndPost(c);
                }
            }
        }
    }

    private class H extends Handler {
        public static final int ACTIVITY_CONFIGURATION_CHANGED = 125;
        public static final int BACKGROUND_VISIBLE_BEHIND_CHANGED = 148;
        public static final int BIND_APPLICATION = 110;
        public static final int BIND_SERVICE = 121;
        public static final int CANCEL_VISIBLE_BEHIND = 147;
        public static final int CLEAN_UP_CONTEXT = 119;
        public static final int CONFIGURATION_CHANGED = 118;
        public static final int CREATE_BACKUP_AGENT = 128;
        public static final int CREATE_SERVICE = 114;
        public static final int CUSTOM_MSG = 1000;
        public static final int DESTROY_ACTIVITY = 109;
        public static final int DESTROY_BACKUP_AGENT = 129;
        public static final int DISPATCH_PACKAGE_BROADCAST = 133;
        public static final int DUMP_ACTIVITY = 136;
        public static final int DUMP_HEAP = 135;
        public static final int DUMP_PROVIDER = 141;
        public static final int DUMP_SERVICE = 123;
        public static final int ENABLE_JIT = 132;
        public static final int ENTER_ANIMATION_COMPLETE = 149;
        public static final int EXIT_APPLICATION = 111;
        public static final int GC_WHEN_IDLE = 120;
        public static final int HIDE_WINDOW = 106;
        public static final int INSTALL_PROVIDER = 145;
        public static final int LAUNCH_ACTIVITY = 100;
        public static final int LOCAL_VOICE_INTERACTION_STARTED = 154;
        public static final int LOW_MEMORY = 124;
        public static final int MULTI_WINDOW_MODE_CHANGED = 152;
        public static final int NEW_INTENT = 112;
        public static final int ON_NEW_ACTIVITY_OPTIONS = 146;
        public static final int PAUSE_ACTIVITY = 101;
        public static final int PAUSE_ACTIVITY_FINISHING = 102;
        public static final int PICTURE_IN_PICTURE_MODE_CHANGED = 153;
        public static final int PROFILER_CONTROL = 127;
        public static final int RECEIVER = 113;
        public static final int RELAUNCH_ACTIVITY = 126;
        public static final int REMOVE_PROVIDER = 131;
        public static final int REQUEST_ASSIST_CONTEXT_EXTRAS = 143;
        public static final int RESUME_ACTIVITY = 107;
        public static final int ROG_INFO_UPDATE = 1001;
        public static final int ROG_REGISTER_LISTENER = 1002;
        public static final int SCHEDULE_CRASH = 134;
        public static final int SEND_RESULT = 108;
        public static final int SERVICE_ARGS = 115;
        public static final int SET_CORE_SETTINGS = 138;
        public static final int SHOW_WINDOW = 105;
        public static final int SLEEPING = 137;
        public static final int START_BINDER_TRACKING = 150;
        public static final int STOP_ACTIVITY_HIDE = 104;
        public static final int STOP_ACTIVITY_SHOW = 103;
        public static final int STOP_BINDER_TRACKING_AND_DUMP = 151;
        public static final int STOP_SERVICE = 116;
        public static final int SUICIDE = 130;
        public static final int TRANSLUCENT_CONVERSION_COMPLETE = 144;
        public static final int TRIM_MEMORY = 140;
        public static final int UNBIND_SERVICE = 122;
        public static final int UNSTABLE_PROVIDER_DIED = 142;
        public static final int UPDATE_PACKAGE_COMPATIBILITY_INFO = 139;

        private H() {
        }

        String codeToString(int code) {
            return Integer.toString(code);
        }

        public void handleMessage(Message msg) {
            SomeArgs args;
            switch (msg.what) {
                case 100:
                    Trace.traceBegin(64, "activityStart");
                    ActivityClientRecord r = msg.obj;
                    r.packageInfo = ActivityThread.this.getPackageInfoNoCheck(r.activityInfo.applicationInfo, r.compatInfo);
                    ActivityThread.this.handleLaunchActivity(r, null, "LAUNCH_ACTIVITY");
                    Trace.traceEnd(64);
                    break;
                case 101:
                    Trace.traceBegin(64, "activityPause");
                    args = msg.obj;
                    ActivityThread.this.handlePauseActivity((IBinder) args.arg1, false, (args.argi1 & 1) != 0, args.argi2, (args.argi1 & 2) != 0, args.argi3);
                    maybeSnapshot();
                    Trace.traceEnd(64);
                    break;
                case 102:
                    Trace.traceBegin(64, "activityPause");
                    args = (SomeArgs) msg.obj;
                    ActivityThread.this.handlePauseActivity((IBinder) args.arg1, true, (args.argi1 & 1) != 0, args.argi2, (args.argi1 & 2) != 0, args.argi3);
                    Trace.traceEnd(64);
                    break;
                case 103:
                    Trace.traceBegin(64, "activityStop");
                    args = (SomeArgs) msg.obj;
                    ActivityThread.this.handleStopActivity((IBinder) args.arg1, true, args.argi2, args.argi3);
                    Trace.traceEnd(64);
                    break;
                case 104:
                    Trace.traceBegin(64, "activityStop");
                    args = (SomeArgs) msg.obj;
                    ActivityThread.this.handleStopActivity((IBinder) args.arg1, false, args.argi2, args.argi3);
                    Trace.traceEnd(64);
                    break;
                case 105:
                    Trace.traceBegin(64, "activityShowWindow");
                    ActivityThread.this.handleWindowVisibility((IBinder) msg.obj, true);
                    Trace.traceEnd(64);
                    break;
                case 106:
                    Trace.traceBegin(64, "activityHideWindow");
                    ActivityThread.this.handleWindowVisibility((IBinder) msg.obj, false);
                    Trace.traceEnd(64);
                    break;
                case 107:
                    Trace.traceBegin(64, "activityResume");
                    Jlog.perfEvent(5, ProxyInfo.LOCAL_EXCL_LIST, new int[0]);
                    args = (SomeArgs) msg.obj;
                    ActivityThread.this.handleResumeActivity((IBinder) args.arg1, true, args.argi1 != 0, true, args.argi3, "RESUME_ACTIVITY");
                    Trace.traceEnd(64);
                    break;
                case 108:
                    Trace.traceBegin(64, "activityDeliverResult");
                    ActivityThread.this.handleSendResult((ResultData) msg.obj);
                    Trace.traceEnd(64);
                    break;
                case 109:
                    Trace.traceBegin(64, "activityDestroy");
                    ActivityThread.this.handleDestroyActivity((IBinder) msg.obj, msg.arg1 != 0, msg.arg2, false);
                    Trace.traceEnd(64);
                    break;
                case 110:
                    Trace.traceBegin(64, "bindApplication");
                    AppBindData data = msg.obj;
                    ScrollerBoostManager.getInstance().init();
                    ActivityThread.this.handleBindApplication(data);
                    Trace.traceEnd(64);
                    break;
                case 111:
                    if (ActivityThread.this.mInitialApplication != null) {
                        ActivityThread.this.mInitialApplication.onTerminate();
                    }
                    Looper.myLooper().quit();
                    break;
                case 112:
                    Trace.traceBegin(64, "activityNewIntent");
                    ActivityThread.this.handleNewIntent((NewIntentData) msg.obj);
                    Trace.traceEnd(64);
                    break;
                case 113:
                    Trace.traceBegin(64, "broadcastReceiveComp");
                    ActivityThread.this.handleReceiver((ReceiverData) msg.obj);
                    maybeSnapshot();
                    Trace.traceEnd(64);
                    break;
                case 114:
                    Trace.traceBegin(64, "serviceCreate: " + String.valueOf(msg.obj));
                    ActivityThread.this.handleCreateService((CreateServiceData) msg.obj);
                    Trace.traceEnd(64);
                    break;
                case 115:
                    Trace.traceBegin(64, "serviceStart: " + String.valueOf(msg.obj));
                    ActivityThread.this.handleServiceArgs((ServiceArgsData) msg.obj);
                    Trace.traceEnd(64);
                    break;
                case 116:
                    Trace.traceBegin(64, "serviceStop");
                    ActivityThread.this.handleStopService((IBinder) msg.obj);
                    maybeSnapshot();
                    Trace.traceEnd(64);
                    break;
                case 118:
                    Trace.traceBegin(64, "configChanged");
                    ActivityThread.this.mCurDefaultDisplayDpi = ((Configuration) msg.obj).densityDpi;
                    ActivityThread.this.handleConfigurationChanged((Configuration) msg.obj, null);
                    Trace.traceEnd(64);
                    break;
                case 119:
                    ContextCleanupInfo cci = msg.obj;
                    cci.context.performFinalCleanup(cci.who, cci.what);
                    break;
                case 120:
                    ActivityThread.this.scheduleGcIdler();
                    break;
                case 121:
                    Trace.traceBegin(64, "serviceBind");
                    ActivityThread.this.handleBindService((BindServiceData) msg.obj);
                    Trace.traceEnd(64);
                    break;
                case 122:
                    Trace.traceBegin(64, "serviceUnbind");
                    ActivityThread.this.handleUnbindService((BindServiceData) msg.obj);
                    Trace.traceEnd(64);
                    break;
                case 123:
                    ActivityThread.this.handleDumpService((DumpComponentInfo) msg.obj);
                    break;
                case 124:
                    Trace.traceBegin(64, "lowMemory");
                    ActivityThread.this.handleLowMemory();
                    Trace.traceEnd(64);
                    break;
                case 125:
                    Trace.traceBegin(64, "activityConfigChanged");
                    ActivityThread.this.handleActivityConfigurationChanged((ActivityConfigChangeData) msg.obj, msg.arg1 == 1);
                    Trace.traceEnd(64);
                    break;
                case 126:
                    Trace.traceBegin(64, "activityRestart");
                    ActivityThread.this.handleRelaunchActivity((ActivityClientRecord) msg.obj);
                    Trace.traceEnd(64);
                    break;
                case 127:
                    ActivityThread.this.handleProfilerControl(msg.arg1 != 0, (ProfilerInfo) msg.obj, msg.arg2);
                    break;
                case 128:
                    Trace.traceBegin(64, "backupCreateAgent");
                    ActivityThread.this.handleCreateBackupAgent((CreateBackupAgentData) msg.obj);
                    Trace.traceEnd(64);
                    break;
                case 129:
                    Trace.traceBegin(64, "backupDestroyAgent");
                    ActivityThread.this.handleDestroyBackupAgent((CreateBackupAgentData) msg.obj);
                    Trace.traceEnd(64);
                    break;
                case 130:
                    Process.killProcess(Process.myPid());
                    break;
                case 131:
                    Trace.traceBegin(64, "providerRemove");
                    ActivityThread.this.completeRemoveProvider((ProviderRefCount) msg.obj);
                    Trace.traceEnd(64);
                    break;
                case 132:
                    ActivityThread.this.ensureJitEnabled();
                    break;
                case 133:
                    Trace.traceBegin(64, "broadcastPackage");
                    ActivityThread.this.handleDispatchPackageBroadcast(msg.arg1, (String[]) msg.obj);
                    Trace.traceEnd(64);
                    break;
                case 134:
                    throw new RemoteServiceException((String) msg.obj);
                case 135:
                    ActivityThread.handleDumpHeap(msg.arg1 != 0, (DumpHeapData) msg.obj);
                    break;
                case 136:
                    ActivityThread.this.handleDumpActivity((DumpComponentInfo) msg.obj);
                    break;
                case 137:
                    Trace.traceBegin(64, "sleeping");
                    ActivityThread.this.handleSleeping((IBinder) msg.obj, msg.arg1 != 0);
                    Trace.traceEnd(64);
                    break;
                case 138:
                    Trace.traceBegin(64, "setCoreSettings");
                    ActivityThread.this.handleSetCoreSettings((Bundle) msg.obj);
                    Trace.traceEnd(64);
                    break;
                case 139:
                    ActivityThread.this.handleUpdatePackageCompatibilityInfo((UpdateCompatibilityData) msg.obj);
                    break;
                case 140:
                    Trace.traceBegin(64, "trimMemory");
                    ActivityThread.this.handleTrimMemory(msg.arg1);
                    Trace.traceEnd(64);
                    break;
                case 141:
                    ActivityThread.this.handleDumpProvider((DumpComponentInfo) msg.obj);
                    break;
                case 142:
                    ActivityThread.this.handleUnstableProviderDied((IBinder) msg.obj, false);
                    break;
                case 143:
                    ActivityThread.this.handleRequestAssistContextExtras((RequestAssistContextExtras) msg.obj);
                    break;
                case 144:
                    ActivityThread.this.handleTranslucentConversionComplete((IBinder) msg.obj, msg.arg1 == 1);
                    break;
                case 145:
                    ActivityThread.this.handleInstallProvider((ProviderInfo) msg.obj);
                    break;
                case 146:
                    Pair<IBinder, ActivityOptions> pair = msg.obj;
                    ActivityThread.this.onNewActivityOptions((IBinder) pair.first, (ActivityOptions) pair.second);
                    break;
                case 147:
                    ActivityThread.this.handleCancelVisibleBehind((IBinder) msg.obj);
                    break;
                case 148:
                    ActivityThread.this.handleOnBackgroundVisibleBehindChanged((IBinder) msg.obj, msg.arg1 > 0);
                    break;
                case 149:
                    ActivityThread.this.handleEnterAnimationComplete((IBinder) msg.obj);
                    break;
                case 150:
                    ActivityThread.this.handleStartBinderTracking();
                    break;
                case 151:
                    ActivityThread.this.handleStopBinderTrackingAndDump((ParcelFileDescriptor) msg.obj);
                    break;
                case 152:
                    ActivityThread.this.handleMultiWindowModeChanged((IBinder) msg.obj, msg.arg1 == 1);
                    break;
                case 153:
                    ActivityThread.this.handlePictureInPictureModeChanged((IBinder) msg.obj, msg.arg1 == 1);
                    break;
                case 154:
                    ActivityThread.this.handleLocalVoiceInteractionStarted((IBinder) ((SomeArgs) msg.obj).arg1, (IVoiceInteractor) ((SomeArgs) msg.obj).arg2);
                    break;
                case 1001:
                    ActivityThread.this.handleRogInfoUpdated((UpdateRog) msg.obj);
                    break;
                case 1002:
                    ActivityThread.this.handleRegisterRogListenerMsg((String) msg.obj);
                    break;
            }
            Object obj = msg.obj;
            if (obj instanceof SomeArgs) {
                ((SomeArgs) obj).recycle();
            }
        }

        private void maybeSnapshot() {
            if (ActivityThread.this.mBoundApplication != null && SamplingProfilerIntegration.isEnabled()) {
                String packageName = ActivityThread.this.mBoundApplication.info.mPackageName;
                PackageInfo packageInfo = null;
                try {
                    Context context = ActivityThread.this.getSystemContext();
                    if (context == null) {
                        Log.e(ActivityThread.TAG, "cannot get a valid context");
                        return;
                    }
                    PackageManager pm = context.getPackageManager();
                    if (pm == null) {
                        Log.e(ActivityThread.TAG, "cannot get a valid PackageManager");
                    } else {
                        packageInfo = pm.getPackageInfo(packageName, 1);
                        SamplingProfilerIntegration.writeSnapshot(ActivityThread.this.mBoundApplication.processName, packageInfo);
                    }
                } catch (NameNotFoundException e) {
                    Log.e(ActivityThread.TAG, "cannot get package info for " + packageName, e);
                }
            }
        }
    }

    final class HwRogListener extends Stub {
        private final String mPackageName;

        public HwRogListener(String packageName) {
            this.mPackageName = packageName;
        }

        public String getPackageName() {
            return this.mPackageName;
        }

        public void onRogSwitchStateChanged(boolean rogEnable, AppRogInfo rogInfo) {
            if (rogInfo != null) {
                if (!WindowManagerGlobal.getInstance().isRogInfoAlreadyExist(this.mPackageName)) {
                    WindowManagerGlobal.getInstance().addAppRogInfo(this.mPackageName, rogInfo);
                }
                if (!ActivityThread.this.mIsInMultiWindowMode) {
                    WindowManagerGlobal.getInstance().setRogEnableFactor(true);
                }
                WindowManagerGlobal.getInstance().setRogSwitchState(rogEnable);
                UpdateRog updateInfo = new UpdateRog();
                updateInfo.rogInfo = rogInfo;
                updateInfo.rogEnable = rogEnable;
                updateInfo.packageName = this.mPackageName;
                ActivityThread.this.sendMessage(1001, updateInfo);
            }
        }

        public void onRogInfoUpdated(AppRogInfo newInfo) {
            AppRogInfo oldInfo = WindowManagerGlobal.getInstance().getAppRogInfo(this.mPackageName);
            if (oldInfo != newInfo && (oldInfo == null || !oldInfo.equals(newInfo))) {
                if (!ActivityThread.this.mIsInMultiWindowMode) {
                    WindowManagerGlobal.getInstance().setRogEnableFactor(true);
                }
                WindowManagerGlobal.getInstance().addAppRogInfo(this.mPackageName, newInfo);
                UpdateRog updateInfo = new UpdateRog();
                updateInfo.rogInfo = newInfo;
                updateInfo.rogEnable = WindowManagerGlobal.getInstance().getRogSwitchState();
                updateInfo.packageName = this.mPackageName;
                ActivityThread.this.sendMessage(1001, updateInfo);
            }
        }
    }

    private class Idler implements IdleHandler {
        private Idler() {
        }

        public final boolean queueIdle() {
            ActivityClientRecord a = ActivityThread.this.mNewActivities;
            boolean stopProfiling = false;
            if (!(ActivityThread.this.mBoundApplication == null || ActivityThread.this.mProfiler.profileFd == null || !ActivityThread.this.mProfiler.autoStopProfiler)) {
                stopProfiling = true;
            }
            if (a != null) {
                ActivityThread.this.mNewActivities = null;
                IActivityManager am = ActivityManagerNative.getDefault();
                do {
                    boolean z;
                    StringBuilder append = new StringBuilder().append("Reporting idle of ").append(a).append(" finished=");
                    if (a.activity != null) {
                        z = a.activity.mFinished;
                    } else {
                        z = false;
                    }
                    Flog.i(101, append.append(z).toString());
                    if (!(a.activity == null || a.activity.mFinished)) {
                        try {
                            am.activityIdle(a.token, a.createdConfig, stopProfiling);
                            a.createdConfig = null;
                        } catch (RemoteException ex) {
                            throw ex.rethrowFromSystemServer();
                        }
                    }
                    ActivityClientRecord prev = a;
                    a = a.nextIdle;
                    prev.nextIdle = null;
                } while (a != null);
            }
            if (stopProfiling) {
                ActivityThread.this.mProfiler.stopProfiling();
            }
            ActivityThread.this.ensureJitEnabled();
            return false;
        }
    }

    static final class NewIntentData {
        List<ReferrerIntent> intents;
        IBinder token;

        NewIntentData() {
        }

        public String toString() {
            return "NewIntentData{intents=" + this.intents + " token=" + this.token + "}";
        }
    }

    static final class Profiler {
        boolean autoStopProfiler;
        boolean handlingProfiling;
        ParcelFileDescriptor profileFd;
        String profileFile;
        boolean profiling;
        int samplingInterval;

        Profiler() {
        }

        public void setProfiler(ProfilerInfo profilerInfo) {
            ParcelFileDescriptor fd = profilerInfo.profileFd;
            if (this.profiling) {
                if (fd != null) {
                    try {
                        fd.close();
                    } catch (IOException e) {
                    }
                }
                return;
            }
            if (this.profileFd != null) {
                try {
                    this.profileFd.close();
                } catch (IOException e2) {
                }
            }
            this.profileFile = profilerInfo.profileFile;
            this.profileFd = fd;
            this.samplingInterval = profilerInfo.samplingInterval;
            this.autoStopProfiler = profilerInfo.autoStopProfiler;
        }

        public void startProfiling() {
            boolean z = true;
            if (this.profileFd != null && !this.profiling) {
                try {
                    int bufferSize = SystemProperties.getInt("debug.traceview-buffer-size-mb", 8);
                    String str = this.profileFile;
                    FileDescriptor fileDescriptor = this.profileFd.getFileDescriptor();
                    int i = (bufferSize * 1024) * 1024;
                    if (this.samplingInterval == 0) {
                        z = false;
                    }
                    VMDebug.startMethodTracing(str, fileDescriptor, i, 0, z, this.samplingInterval);
                    this.profiling = true;
                } catch (RuntimeException e) {
                    Slog.w(ActivityThread.TAG, "Profiling failed on path " + this.profileFile);
                    try {
                        this.profileFd.close();
                        this.profileFd = null;
                    } catch (IOException e2) {
                        Slog.w(ActivityThread.TAG, "Failure closing profile fd", e2);
                    }
                }
            }
        }

        public void stopProfiling() {
            if (this.profiling) {
                this.profiling = false;
                Debug.stopMethodTracing();
                if (this.profileFd != null) {
                    try {
                        this.profileFd.close();
                    } catch (IOException e) {
                    }
                }
                this.profileFd = null;
                this.profileFile = null;
            }
        }
    }

    final class ProviderClientRecord {
        final ContentProviderHolder mHolder;
        final ContentProvider mLocalProvider;
        final String[] mNames;
        final IContentProvider mProvider;

        ProviderClientRecord(String[] names, IContentProvider provider, ContentProvider localProvider, ContentProviderHolder holder) {
            this.mNames = names;
            this.mProvider = provider;
            this.mLocalProvider = localProvider;
            this.mHolder = holder;
        }
    }

    private static final class ProviderKey {
        final String authority;
        final int userId;

        public ProviderKey(String authority, int userId) {
            this.authority = authority;
            this.userId = userId;
        }

        public boolean equals(Object o) {
            boolean z = false;
            if (!(o instanceof ProviderKey)) {
                return false;
            }
            ProviderKey other = (ProviderKey) o;
            if (Objects.equals(this.authority, other.authority) && this.userId == other.userId) {
                z = true;
            }
            return z;
        }

        public int hashCode() {
            return (this.authority != null ? this.authority.hashCode() : 0) ^ this.userId;
        }
    }

    private static final class ProviderRefCount {
        public final ProviderClientRecord client;
        public final ContentProviderHolder holder;
        public boolean removePending;
        public int stableCount;
        public int unstableCount;

        ProviderRefCount(ContentProviderHolder inHolder, ProviderClientRecord inClient, int sCount, int uCount) {
            this.holder = inHolder;
            this.client = inClient;
            this.stableCount = sCount;
            this.unstableCount = uCount;
        }
    }

    static final class ReceiverData extends PendingResult {
        CompatibilityInfo compatInfo;
        ActivityInfo info;
        Intent intent;

        public ReceiverData(Intent intent, int resultCode, String resultData, Bundle resultExtras, boolean ordered, boolean sticky, IBinder token, int sendingUser) {
            super(resultCode, resultData, resultExtras, 0, ordered, sticky, token, sendingUser, intent.getFlags());
            this.intent = intent;
        }

        public String toString() {
            return "ReceiverData{intent=" + this.intent + " packageName=" + this.info.packageName + " resultCode=" + getResultCode() + " resultData=" + getResultData() + " resultExtras=" + getResultExtras(false) + "}";
        }
    }

    static final class RequestAssistContextExtras {
        IBinder activityToken;
        IBinder requestToken;
        int requestType;
        int sessionId;

        RequestAssistContextExtras() {
        }
    }

    static final class ResultData {
        List<ResultInfo> results;
        IBinder token;

        ResultData() {
        }

        public String toString() {
            return "ResultData{token=" + this.token + " results" + this.results + "}";
        }
    }

    static final class ServiceArgsData {
        Intent args;
        int flags;
        int startId;
        boolean taskRemoved;
        IBinder token;

        ServiceArgsData() {
        }

        public String toString() {
            return "ServiceArgsData{token=" + this.token + " startId=" + this.startId + " args=" + this.args + "}";
        }
    }

    private static class StopInfo implements Runnable {
        ActivityClientRecord activity;
        CharSequence description;
        PersistableBundle persistentState;
        Bundle state;

        private StopInfo() {
        }

        public void run() {
            try {
                ActivityManagerNative.getDefault().activityStopped(this.activity.token, this.state, this.persistentState, this.description);
            } catch (RemoteException ex) {
                if (!(ex instanceof TransactionTooLargeException) || this.activity.packageInfo.getTargetSdkVersion() >= 24) {
                    throw ex.rethrowFromSystemServer();
                }
                Log.e(ActivityThread.TAG, "App sent too much data in instance state, so it was ignored", ex);
            }
        }
    }

    static final class UpdateCompatibilityData {
        CompatibilityInfo info;
        String pkg;

        UpdateCompatibilityData() {
        }
    }

    private native void dumpGraphicsInfo(FileDescriptor fileDescriptor);

    static {
        boolean z = true;
        if (SystemProperties.getInt("ro.logsystem.usertype", 1) != 3) {
            z = false;
        }
        IS_DEBUG_VERSION = z;
    }

    private int getLifecycleSeq() {
        int i;
        synchronized (this.mResourcesManager) {
            i = this.mLifecycleSeq;
            this.mLifecycleSeq = i + 1;
        }
        return i;
    }

    public static ActivityThread currentActivityThread() {
        return sCurrentActivityThread;
    }

    public static boolean isSystem() {
        return sCurrentActivityThread != null ? sCurrentActivityThread.mSystemThread : false;
    }

    public static String currentOpPackageName() {
        ActivityThread am = currentActivityThread();
        if (am == null || am.getApplication() == null) {
            return null;
        }
        return am.getApplication().getOpPackageName();
    }

    public static String currentPackageName() {
        ActivityThread am = currentActivityThread();
        if (am == null || am.mBoundApplication == null) {
            return null;
        }
        return am.mBoundApplication.appInfo.packageName;
    }

    public static String currentProcessName() {
        ActivityThread am = currentActivityThread();
        if (am == null || am.mBoundApplication == null) {
            return null;
        }
        return am.mBoundApplication.processName;
    }

    public static Application currentApplication() {
        ActivityThread am = currentActivityThread();
        if (am != null) {
            return am.mInitialApplication;
        }
        return null;
    }

    public static IPackageManager getPackageManager() {
        if (sPackageManager != null) {
            return sPackageManager;
        }
        sPackageManager = IPackageManager.Stub.asInterface(ServiceManager.getService(HwFrameworkMonitor.KEY_PACKAGE));
        return sPackageManager;
    }

    Configuration applyConfigCompatMainThread(int displayDensity, Configuration config, CompatibilityInfo compat) {
        if (config == null) {
            return null;
        }
        if (!compat.supportsScreen()) {
            this.mMainThreadConfig.setTo(config);
            config = this.mMainThreadConfig;
            compat.applyToConfiguration(displayDensity, config);
            compat.applyToConfigurationExt(null, displayDensity, config);
        }
        return config;
    }

    Resources getTopLevelResources(String resDir, String[] splitResDirs, String[] overlayDirs, String[] libDirs, int displayId, LoadedApk pkgInfo) {
        CompatibilityInfo info = pkgInfo.getCompatibilityInfo();
        if (!UserHandle.isIsolated(Process.myUid())) {
            int mode = 0;
            try {
                IActivityManager manager = ActivityManagerNative.getDefault();
                if (manager != null) {
                    mode = manager.getPackageScreenCompatMode(pkgInfo.getPackageName());
                }
            } catch (RemoteException re) {
                Log.e("SDR", "APS: SDR: ActivityThread.getPackageScreenCompatMode, RemoteException is thrown!", re);
            }
            if (mode == 1) {
                info = CompatibilityInfo.makeCompatibilityInfo(1);
            }
        }
        return this.mResourcesManager.getResources(null, resDir, splitResDirs, overlayDirs, libDirs, displayId, null, info, pkgInfo.getClassLoader());
    }

    final Handler getHandler() {
        return this.mH;
    }

    public final LoadedApk getPackageInfo(String packageName, CompatibilityInfo compatInfo, int flags) {
        return getPackageInfo(packageName, compatInfo, flags, UserHandle.myUserId());
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public final LoadedApk getPackageInfo(String packageName, CompatibilityInfo compatInfo, int flags, int userId) {
        boolean differentUser = UserHandle.myUserId() != userId;
        synchronized (this.mResourcesManager) {
            WeakReference weakReference;
            LoadedApk packageInfo;
            if (differentUser) {
                weakReference = null;
            } else if ((flags & 1) != 0) {
                weakReference = (WeakReference) this.mPackages.get(packageName);
            } else {
                weakReference = (WeakReference) this.mResourcePackages.get(packageName);
            }
            if (weakReference != null) {
                packageInfo = (LoadedApk) weakReference.get();
            } else {
                packageInfo = null;
            }
            if (packageInfo == null || !(packageInfo.mResources == null || packageInfo.mResources.getAssets().isUpToDate())) {
            } else if (packageInfo.isSecurityViolation() && (flags & 2) == 0) {
                throw new SecurityException("Requesting code from " + packageName + " to be run in process " + this.mBoundApplication.processName + "/" + this.mBoundApplication.appInfo.uid);
            }
        }
    }

    public final LoadedApk getPackageInfo(ApplicationInfo ai, CompatibilityInfo compatInfo, int flags) {
        boolean includeCode = false;
        if ((flags & 1) != 0) {
            includeCode = true;
        }
        boolean securityViolation = (!includeCode || ai.uid == 0 || ai.uid == 1000) ? false : this.mBoundApplication != null ? !UserHandle.isSameApp(ai.uid, this.mBoundApplication.appInfo.uid) : true;
        boolean registerPackage = includeCode && (1073741824 & flags) != 0;
        if ((flags & 3) != 1 || !securityViolation) {
            return getPackageInfo(ai, compatInfo, null, securityViolation, includeCode, registerPackage);
        }
        String msg = "Requesting code from " + ai.packageName + " (with uid " + ai.uid + ")";
        if (this.mBoundApplication != null) {
            msg = msg + " to be run in process " + this.mBoundApplication.processName + " (with uid " + this.mBoundApplication.appInfo.uid + ")";
        }
        throw new SecurityException(msg);
    }

    public final LoadedApk getPackageInfoNoCheck(ApplicationInfo ai, CompatibilityInfo compatInfo) {
        return getPackageInfo(ai, compatInfo, null, false, true, false);
    }

    public final LoadedApk peekPackageInfo(String packageName, boolean includeCode) {
        LoadedApk loadedApk = null;
        synchronized (this.mResourcesManager) {
            WeakReference<LoadedApk> ref;
            if (includeCode) {
                ref = (WeakReference) this.mPackages.get(packageName);
            } else {
                ref = (WeakReference) this.mResourcePackages.get(packageName);
            }
            if (ref != null) {
                loadedApk = (LoadedApk) ref.get();
            }
        }
        return loadedApk;
    }

    private LoadedApk getPackageInfo(ApplicationInfo aInfo, CompatibilityInfo compatInfo, ClassLoader baseLoader, boolean securityViolation, boolean includeCode, boolean registerPackage) {
        LoadedApk loadedApk;
        boolean differentUser = UserHandle.myUserId() != UserHandle.getUserId(aInfo.uid);
        synchronized (this.mResourcesManager) {
            WeakReference weakReference;
            if (differentUser) {
                weakReference = null;
            } else if (includeCode) {
                weakReference = (WeakReference) this.mPackages.get(aInfo.packageName);
            } else {
                weakReference = (WeakReference) this.mResourcePackages.get(aInfo.packageName);
            }
            loadedApk = weakReference != null ? (LoadedApk) weakReference.get() : null;
            if (loadedApk == null || !(loadedApk.mResources == null || loadedApk.mResources.getAssets().isUpToDate())) {
                boolean z = includeCode ? (aInfo.flags & 4) != 0 : false;
                loadedApk = new LoadedApk(this, aInfo, compatInfo, baseLoader, securityViolation, z, registerPackage);
                if (this.mSystemThread && ZenModeConfig.SYSTEM_AUTHORITY.equals(aInfo.packageName)) {
                    loadedApk.installSystemApplicationInfo(aInfo, getSystemContext().mPackageInfo.getClassLoader());
                }
                if (!differentUser) {
                    if (includeCode) {
                        this.mPackages.put(aInfo.packageName, new WeakReference(loadedApk));
                    } else {
                        this.mResourcePackages.put(aInfo.packageName, new WeakReference(loadedApk));
                    }
                }
            }
        }
        return loadedApk;
    }

    ActivityThread() {
    }

    public ApplicationThread getApplicationThread() {
        return this.mAppThread;
    }

    public Instrumentation getInstrumentation() {
        return this.mInstrumentation;
    }

    public boolean isProfiling() {
        if (this.mProfiler == null || this.mProfiler.profileFile == null || this.mProfiler.profileFd != null) {
            return false;
        }
        return true;
    }

    public String getProfileFilePath() {
        return this.mProfiler.profileFile;
    }

    public Looper getLooper() {
        return this.mLooper;
    }

    public Application getApplication() {
        return this.mInitialApplication;
    }

    public String getProcessName() {
        return this.mBoundApplication.processName;
    }

    public ContextImpl getSystemContext() {
        ContextImpl contextImpl;
        synchronized (this) {
            if (this.mSystemContext == null) {
                this.mSystemContext = ContextImpl.createSystemContext(this);
            }
            contextImpl = this.mSystemContext;
        }
        return contextImpl;
    }

    public void installSystemApplicationInfo(ApplicationInfo info, ClassLoader classLoader) {
        synchronized (this) {
            getSystemContext().installSystemApplicationInfo(info, classLoader);
            this.mProfiler = new Profiler();
        }
    }

    void ensureJitEnabled() {
        if (!this.mJitEnabled) {
            this.mJitEnabled = true;
            VMRuntime.getRuntime().startJitCompilation();
        }
    }

    void scheduleGcIdler() {
        if (!this.mGcIdlerScheduled) {
            this.mGcIdlerScheduled = true;
            Looper.myQueue().addIdleHandler(this.mGcIdler);
        }
        this.mH.removeMessages(120);
    }

    void unscheduleGcIdler() {
        if (this.mGcIdlerScheduled) {
            this.mGcIdlerScheduled = false;
            Looper.myQueue().removeIdleHandler(this.mGcIdler);
        }
        this.mH.removeMessages(120);
    }

    void doGcIfNeeded() {
        this.mGcIdlerScheduled = false;
        if (BinderInternal.getLastGcTime() + MIN_TIME_BETWEEN_GCS < SystemClock.uptimeMillis()) {
            BinderInternal.forceGc("bg");
        }
    }

    static void printRow(PrintWriter pw, String format, Object... objs) {
        pw.println(String.format(format, objs));
    }

    public static void dumpMemInfoTable(PrintWriter pw, MemoryInfo memInfo, boolean checkin, boolean dumpFullInfo, boolean dumpDalvik, boolean dumpSummaryOnly, int pid, String processName, long nativeMax, long nativeAllocated, long nativeFree, long dalvikMax, long dalvikAllocated, long dalvikFree) {
        int i;
        if (checkin) {
            pw.print(4);
            pw.print(',');
            pw.print(pid);
            pw.print(',');
            pw.print(processName);
            pw.print(',');
            pw.print(nativeMax);
            pw.print(',');
            pw.print(dalvikMax);
            pw.print(',');
            pw.print("N/A,");
            pw.print(nativeMax + dalvikMax);
            pw.print(',');
            pw.print(nativeAllocated);
            pw.print(',');
            pw.print(dalvikAllocated);
            pw.print(',');
            pw.print("N/A,");
            pw.print(nativeAllocated + dalvikAllocated);
            pw.print(',');
            pw.print(nativeFree);
            pw.print(',');
            pw.print(dalvikFree);
            pw.print(',');
            pw.print("N/A,");
            pw.print(nativeFree + dalvikFree);
            pw.print(',');
            pw.print(memInfo.nativePss);
            pw.print(',');
            pw.print(memInfo.dalvikPss);
            pw.print(',');
            pw.print(memInfo.otherPss);
            pw.print(',');
            pw.print(memInfo.getTotalPss());
            pw.print(',');
            pw.print(memInfo.nativeSwappablePss);
            pw.print(',');
            pw.print(memInfo.dalvikSwappablePss);
            pw.print(',');
            pw.print(memInfo.otherSwappablePss);
            pw.print(',');
            pw.print(memInfo.getTotalSwappablePss());
            pw.print(',');
            pw.print(memInfo.nativeSharedDirty);
            pw.print(',');
            pw.print(memInfo.dalvikSharedDirty);
            pw.print(',');
            pw.print(memInfo.otherSharedDirty);
            pw.print(',');
            pw.print(memInfo.getTotalSharedDirty());
            pw.print(',');
            pw.print(memInfo.nativeSharedClean);
            pw.print(',');
            pw.print(memInfo.dalvikSharedClean);
            pw.print(',');
            pw.print(memInfo.otherSharedClean);
            pw.print(',');
            pw.print(memInfo.getTotalSharedClean());
            pw.print(',');
            pw.print(memInfo.nativePrivateDirty);
            pw.print(',');
            pw.print(memInfo.dalvikPrivateDirty);
            pw.print(',');
            pw.print(memInfo.otherPrivateDirty);
            pw.print(',');
            pw.print(memInfo.getTotalPrivateDirty());
            pw.print(',');
            pw.print(memInfo.nativePrivateClean);
            pw.print(',');
            pw.print(memInfo.dalvikPrivateClean);
            pw.print(',');
            pw.print(memInfo.otherPrivateClean);
            pw.print(',');
            pw.print(memInfo.getTotalPrivateClean());
            pw.print(',');
            pw.print(memInfo.nativeSwappedOut);
            pw.print(',');
            pw.print(memInfo.dalvikSwappedOut);
            pw.print(',');
            pw.print(memInfo.otherSwappedOut);
            pw.print(',');
            pw.print(memInfo.getTotalSwappedOut());
            pw.print(',');
            if (memInfo.hasSwappedOutPss) {
                pw.print(memInfo.nativeSwappedOutPss);
                pw.print(',');
                pw.print(memInfo.dalvikSwappedOutPss);
                pw.print(',');
                pw.print(memInfo.otherSwappedOutPss);
                pw.print(',');
                pw.print(memInfo.getTotalSwappedOutPss());
                pw.print(',');
            } else {
                pw.print("N/A,");
                pw.print("N/A,");
                pw.print("N/A,");
                pw.print("N/A,");
            }
            for (i = 0; i < 17; i++) {
                pw.print(MemoryInfo.getOtherLabel(i));
                pw.print(',');
                pw.print(memInfo.getOtherPss(i));
                pw.print(',');
                pw.print(memInfo.getOtherSwappablePss(i));
                pw.print(',');
                pw.print(memInfo.getOtherSharedDirty(i));
                pw.print(',');
                pw.print(memInfo.getOtherSharedClean(i));
                pw.print(',');
                pw.print(memInfo.getOtherPrivateDirty(i));
                pw.print(',');
                pw.print(memInfo.getOtherPrivateClean(i));
                pw.print(',');
                pw.print(memInfo.getOtherSwappedOut(i));
                pw.print(',');
                if (memInfo.hasSwappedOutPss) {
                    pw.print(memInfo.getOtherSwappedOutPss(i));
                    pw.print(',');
                } else {
                    pw.print("N/A,");
                }
            }
            return;
        }
        if (!dumpSummaryOnly) {
            String str;
            String[] strArr;
            int i2;
            int myPss;
            int mySwappablePss;
            int mySharedDirty;
            int myPrivateDirty;
            int mySharedClean;
            int myPrivateClean;
            int mySwappedOut;
            int mySwappedOutPss;
            String str2;
            String[] strArr2;
            if (dumpFullInfo) {
                str = HEAP_FULL_COLUMN;
                strArr = new Object[11];
                strArr[0] = ProxyInfo.LOCAL_EXCL_LIST;
                strArr[1] = "Pss";
                strArr[2] = "Pss";
                strArr[3] = "Shared";
                strArr[4] = "Private";
                strArr[5] = "Shared";
                strArr[6] = "Private";
                strArr[7] = memInfo.hasSwappedOutPss ? "SwapPss" : "Swap";
                strArr[8] = "Heap";
                strArr[9] = "Heap";
                strArr[10] = "Heap";
                printRow(pw, str, strArr);
                printRow(pw, HEAP_FULL_COLUMN, ProxyInfo.LOCAL_EXCL_LIST, "Total", "Clean", "Dirty", "Dirty", "Clean", "Clean", "Dirty", "Size", "Alloc", "Free");
                printRow(pw, HEAP_FULL_COLUMN, ProxyInfo.LOCAL_EXCL_LIST, "------", "------", "------", "------", "------", "------", "------", "------", "------", "------");
                str = HEAP_FULL_COLUMN;
                strArr = new Object[11];
                strArr[0] = "Native Heap";
                strArr[1] = Integer.valueOf(memInfo.nativePss);
                strArr[2] = Integer.valueOf(memInfo.nativeSwappablePss);
                strArr[3] = Integer.valueOf(memInfo.nativeSharedDirty);
                strArr[4] = Integer.valueOf(memInfo.nativePrivateDirty);
                strArr[5] = Integer.valueOf(memInfo.nativeSharedClean);
                strArr[6] = Integer.valueOf(memInfo.nativePrivateClean);
                strArr[7] = Integer.valueOf(memInfo.hasSwappedOutPss ? memInfo.nativeSwappedOut : memInfo.nativeSwappedOutPss);
                strArr[8] = Long.valueOf(nativeMax);
                strArr[9] = Long.valueOf(nativeAllocated);
                strArr[10] = Long.valueOf(nativeFree);
                printRow(pw, str, strArr);
                str = HEAP_FULL_COLUMN;
                strArr = new Object[11];
                strArr[0] = "Dalvik Heap";
                strArr[1] = Integer.valueOf(memInfo.dalvikPss);
                strArr[2] = Integer.valueOf(memInfo.dalvikSwappablePss);
                strArr[3] = Integer.valueOf(memInfo.dalvikSharedDirty);
                strArr[4] = Integer.valueOf(memInfo.dalvikPrivateDirty);
                strArr[5] = Integer.valueOf(memInfo.dalvikSharedClean);
                strArr[6] = Integer.valueOf(memInfo.dalvikPrivateClean);
                strArr[7] = Integer.valueOf(memInfo.hasSwappedOutPss ? memInfo.dalvikSwappedOut : memInfo.dalvikSwappedOutPss);
                strArr[8] = Long.valueOf(dalvikMax);
                strArr[9] = Long.valueOf(dalvikAllocated);
                strArr[10] = Long.valueOf(dalvikFree);
                printRow(pw, str, strArr);
            } else {
                str = HEAP_COLUMN;
                strArr = new Object[8];
                strArr[0] = ProxyInfo.LOCAL_EXCL_LIST;
                strArr[1] = "Pss";
                strArr[2] = "Private";
                strArr[3] = "Private";
                strArr[4] = memInfo.hasSwappedOutPss ? "SwapPss" : "Swap";
                strArr[5] = "Heap";
                strArr[6] = "Heap";
                strArr[7] = "Heap";
                printRow(pw, str, strArr);
                printRow(pw, HEAP_COLUMN, ProxyInfo.LOCAL_EXCL_LIST, "Total", "Dirty", "Clean", "Dirty", "Size", "Alloc", "Free");
                printRow(pw, HEAP_COLUMN, ProxyInfo.LOCAL_EXCL_LIST, "------", "------", "------", "------", "------", "------", "------", "------");
                str = HEAP_COLUMN;
                strArr = new Object[8];
                strArr[0] = "Native Heap";
                strArr[1] = Integer.valueOf(memInfo.nativePss);
                strArr[2] = Integer.valueOf(memInfo.nativePrivateDirty);
                strArr[3] = Integer.valueOf(memInfo.nativePrivateClean);
                if (memInfo.hasSwappedOutPss) {
                    i2 = memInfo.nativeSwappedOutPss;
                } else {
                    i2 = memInfo.nativeSwappedOut;
                }
                strArr[4] = Integer.valueOf(i2);
                strArr[5] = Long.valueOf(nativeMax);
                strArr[6] = Long.valueOf(nativeAllocated);
                strArr[7] = Long.valueOf(nativeFree);
                printRow(pw, str, strArr);
                str = HEAP_COLUMN;
                strArr = new Object[8];
                strArr[0] = "Dalvik Heap";
                strArr[1] = Integer.valueOf(memInfo.dalvikPss);
                strArr[2] = Integer.valueOf(memInfo.dalvikPrivateDirty);
                strArr[3] = Integer.valueOf(memInfo.dalvikPrivateClean);
                if (memInfo.hasSwappedOutPss) {
                    i2 = memInfo.dalvikSwappedOutPss;
                } else {
                    i2 = memInfo.dalvikSwappedOut;
                }
                strArr[4] = Integer.valueOf(i2);
                strArr[5] = Long.valueOf(dalvikMax);
                strArr[6] = Long.valueOf(dalvikAllocated);
                strArr[7] = Long.valueOf(dalvikFree);
                printRow(pw, str, strArr);
            }
            int otherPss = memInfo.otherPss;
            int otherSwappablePss = memInfo.otherSwappablePss;
            int otherSharedDirty = memInfo.otherSharedDirty;
            int otherPrivateDirty = memInfo.otherPrivateDirty;
            int otherSharedClean = memInfo.otherSharedClean;
            int otherPrivateClean = memInfo.otherPrivateClean;
            int otherSwappedOut = memInfo.otherSwappedOut;
            int otherSwappedOutPss = memInfo.otherSwappedOutPss;
            for (i = 0; i < 17; i++) {
                myPss = memInfo.getOtherPss(i);
                mySwappablePss = memInfo.getOtherSwappablePss(i);
                mySharedDirty = memInfo.getOtherSharedDirty(i);
                myPrivateDirty = memInfo.getOtherPrivateDirty(i);
                mySharedClean = memInfo.getOtherSharedClean(i);
                myPrivateClean = memInfo.getOtherPrivateClean(i);
                mySwappedOut = memInfo.getOtherSwappedOut(i);
                mySwappedOutPss = memInfo.getOtherSwappedOutPss(i);
                if (myPss == 0 && mySharedDirty == 0 && myPrivateDirty == 0 && mySharedClean == 0 && myPrivateClean == 0) {
                    if ((memInfo.hasSwappedOutPss ? mySwappedOutPss : mySwappedOut) == 0) {
                    }
                }
                if (dumpFullInfo) {
                    str = HEAP_FULL_COLUMN;
                    strArr = new Object[11];
                    strArr[0] = MemoryInfo.getOtherLabel(i);
                    strArr[1] = Integer.valueOf(myPss);
                    strArr[2] = Integer.valueOf(mySwappablePss);
                    strArr[3] = Integer.valueOf(mySharedDirty);
                    strArr[4] = Integer.valueOf(myPrivateDirty);
                    strArr[5] = Integer.valueOf(mySharedClean);
                    strArr[6] = Integer.valueOf(myPrivateClean);
                    if (memInfo.hasSwappedOutPss) {
                        i2 = mySwappedOutPss;
                    } else {
                        i2 = mySwappedOut;
                    }
                    strArr[7] = Integer.valueOf(i2);
                    strArr[8] = ProxyInfo.LOCAL_EXCL_LIST;
                    strArr[9] = ProxyInfo.LOCAL_EXCL_LIST;
                    strArr[10] = ProxyInfo.LOCAL_EXCL_LIST;
                    printRow(pw, str, strArr);
                } else {
                    str = HEAP_COLUMN;
                    strArr = new Object[8];
                    strArr[0] = MemoryInfo.getOtherLabel(i);
                    strArr[1] = Integer.valueOf(myPss);
                    strArr[2] = Integer.valueOf(myPrivateDirty);
                    strArr[3] = Integer.valueOf(myPrivateClean);
                    if (memInfo.hasSwappedOutPss) {
                        i2 = mySwappedOutPss;
                    } else {
                        i2 = mySwappedOut;
                    }
                    strArr[4] = Integer.valueOf(i2);
                    strArr[5] = ProxyInfo.LOCAL_EXCL_LIST;
                    strArr[6] = ProxyInfo.LOCAL_EXCL_LIST;
                    strArr[7] = ProxyInfo.LOCAL_EXCL_LIST;
                    printRow(pw, str, strArr);
                }
                otherPss -= myPss;
                otherSwappablePss -= mySwappablePss;
                otherSharedDirty -= mySharedDirty;
                otherPrivateDirty -= myPrivateDirty;
                otherSharedClean -= mySharedClean;
                otherPrivateClean -= myPrivateClean;
                otherSwappedOut -= mySwappedOut;
                otherSwappedOutPss -= mySwappedOutPss;
            }
            if (dumpFullInfo) {
                str2 = HEAP_FULL_COLUMN;
                strArr2 = new Object[11];
                strArr2[0] = "Unknown";
                strArr2[1] = Integer.valueOf(otherPss);
                strArr2[2] = Integer.valueOf(otherSwappablePss);
                strArr2[3] = Integer.valueOf(otherSharedDirty);
                strArr2[4] = Integer.valueOf(otherPrivateDirty);
                strArr2[5] = Integer.valueOf(otherSharedClean);
                strArr2[6] = Integer.valueOf(otherPrivateClean);
                if (!memInfo.hasSwappedOutPss) {
                    otherSwappedOutPss = otherSwappedOut;
                }
                strArr2[7] = Integer.valueOf(otherSwappedOutPss);
                strArr2[8] = ProxyInfo.LOCAL_EXCL_LIST;
                strArr2[9] = ProxyInfo.LOCAL_EXCL_LIST;
                strArr2[10] = ProxyInfo.LOCAL_EXCL_LIST;
                printRow(pw, str2, strArr2);
                str = HEAP_FULL_COLUMN;
                strArr = new Object[11];
                strArr[0] = "TOTAL";
                strArr[1] = Integer.valueOf(memInfo.getTotalPss());
                strArr[2] = Integer.valueOf(memInfo.getTotalSwappablePss());
                strArr[3] = Integer.valueOf(memInfo.getTotalSharedDirty());
                strArr[4] = Integer.valueOf(memInfo.getTotalPrivateDirty());
                strArr[5] = Integer.valueOf(memInfo.getTotalSharedClean());
                strArr[6] = Integer.valueOf(memInfo.getTotalPrivateClean());
                if (memInfo.hasSwappedOutPss) {
                    i2 = memInfo.getTotalSwappedOutPss();
                } else {
                    i2 = memInfo.getTotalSwappedOut();
                }
                strArr[7] = Integer.valueOf(i2);
                strArr[8] = Long.valueOf(nativeMax + dalvikMax);
                strArr[9] = Long.valueOf(nativeAllocated + dalvikAllocated);
                strArr[10] = Long.valueOf(nativeFree + dalvikFree);
                printRow(pw, str, strArr);
            } else {
                str2 = HEAP_COLUMN;
                strArr2 = new Object[8];
                strArr2[0] = "Unknown";
                strArr2[1] = Integer.valueOf(otherPss);
                strArr2[2] = Integer.valueOf(otherPrivateDirty);
                strArr2[3] = Integer.valueOf(otherPrivateClean);
                if (!memInfo.hasSwappedOutPss) {
                    otherSwappedOutPss = otherSwappedOut;
                }
                strArr2[4] = Integer.valueOf(otherSwappedOutPss);
                strArr2[5] = ProxyInfo.LOCAL_EXCL_LIST;
                strArr2[6] = ProxyInfo.LOCAL_EXCL_LIST;
                strArr2[7] = ProxyInfo.LOCAL_EXCL_LIST;
                printRow(pw, str2, strArr2);
                str = HEAP_COLUMN;
                strArr = new Object[8];
                strArr[0] = "TOTAL";
                strArr[1] = Integer.valueOf(memInfo.getTotalPss());
                strArr[2] = Integer.valueOf(memInfo.getTotalPrivateDirty());
                strArr[3] = Integer.valueOf(memInfo.getTotalPrivateClean());
                if (memInfo.hasSwappedOutPss) {
                    i2 = memInfo.getTotalSwappedOutPss();
                } else {
                    i2 = memInfo.getTotalSwappedOut();
                }
                strArr[4] = Integer.valueOf(i2);
                strArr[5] = Long.valueOf(nativeMax + dalvikMax);
                strArr[6] = Long.valueOf(nativeAllocated + dalvikAllocated);
                strArr[7] = Long.valueOf(nativeFree + dalvikFree);
                printRow(pw, str, strArr);
            }
            if (dumpDalvik) {
                pw.println(WifiEnterpriseConfig.CA_CERT_ALIAS_DELIMITER);
                pw.println(" Dalvik Details");
                for (i = 17; i < 25; i++) {
                    myPss = memInfo.getOtherPss(i);
                    mySwappablePss = memInfo.getOtherSwappablePss(i);
                    mySharedDirty = memInfo.getOtherSharedDirty(i);
                    myPrivateDirty = memInfo.getOtherPrivateDirty(i);
                    mySharedClean = memInfo.getOtherSharedClean(i);
                    myPrivateClean = memInfo.getOtherPrivateClean(i);
                    mySwappedOut = memInfo.getOtherSwappedOut(i);
                    mySwappedOutPss = memInfo.getOtherSwappedOutPss(i);
                    if (myPss == 0 && mySharedDirty == 0 && myPrivateDirty == 0 && mySharedClean == 0 && myPrivateClean == 0) {
                        if ((memInfo.hasSwappedOutPss ? mySwappedOutPss : mySwappedOut) == 0) {
                        }
                    }
                    if (dumpFullInfo) {
                        str2 = HEAP_FULL_COLUMN;
                        strArr2 = new Object[11];
                        strArr2[0] = MemoryInfo.getOtherLabel(i);
                        strArr2[1] = Integer.valueOf(myPss);
                        strArr2[2] = Integer.valueOf(mySwappablePss);
                        strArr2[3] = Integer.valueOf(mySharedDirty);
                        strArr2[4] = Integer.valueOf(myPrivateDirty);
                        strArr2[5] = Integer.valueOf(mySharedClean);
                        strArr2[6] = Integer.valueOf(myPrivateClean);
                        if (!memInfo.hasSwappedOutPss) {
                            mySwappedOutPss = mySwappedOut;
                        }
                        strArr2[7] = Integer.valueOf(mySwappedOutPss);
                        strArr2[8] = ProxyInfo.LOCAL_EXCL_LIST;
                        strArr2[9] = ProxyInfo.LOCAL_EXCL_LIST;
                        strArr2[10] = ProxyInfo.LOCAL_EXCL_LIST;
                        printRow(pw, str2, strArr2);
                    } else {
                        str2 = HEAP_COLUMN;
                        strArr2 = new Object[8];
                        strArr2[0] = MemoryInfo.getOtherLabel(i);
                        strArr2[1] = Integer.valueOf(myPss);
                        strArr2[2] = Integer.valueOf(myPrivateDirty);
                        strArr2[3] = Integer.valueOf(myPrivateClean);
                        if (!memInfo.hasSwappedOutPss) {
                            mySwappedOutPss = mySwappedOut;
                        }
                        strArr2[4] = Integer.valueOf(mySwappedOutPss);
                        strArr2[5] = ProxyInfo.LOCAL_EXCL_LIST;
                        strArr2[6] = ProxyInfo.LOCAL_EXCL_LIST;
                        strArr2[7] = ProxyInfo.LOCAL_EXCL_LIST;
                        printRow(pw, str2, strArr2);
                    }
                }
            }
        }
        pw.println(WifiEnterpriseConfig.CA_CERT_ALIAS_DELIMITER);
        pw.println(" App Summary");
        printRow(pw, ONE_COUNT_COLUMN_HEADER, ProxyInfo.LOCAL_EXCL_LIST, "Pss(KB)");
        printRow(pw, ONE_COUNT_COLUMN_HEADER, ProxyInfo.LOCAL_EXCL_LIST, "------");
        printRow(pw, ONE_COUNT_COLUMN, "Java Heap:", Integer.valueOf(memInfo.getSummaryJavaHeap()));
        printRow(pw, ONE_COUNT_COLUMN, "Native Heap:", Integer.valueOf(memInfo.getSummaryNativeHeap()));
        printRow(pw, ONE_COUNT_COLUMN, "Code:", Integer.valueOf(memInfo.getSummaryCode()));
        printRow(pw, ONE_COUNT_COLUMN, "Stack:", Integer.valueOf(memInfo.getSummaryStack()));
        printRow(pw, ONE_COUNT_COLUMN, "Graphics:", Integer.valueOf(memInfo.getSummaryGraphics()));
        printRow(pw, ONE_COUNT_COLUMN, "Private Other:", Integer.valueOf(memInfo.getSummaryPrivateOther()));
        printRow(pw, ONE_COUNT_COLUMN, "System:", Integer.valueOf(memInfo.getSummarySystem()));
        pw.println(WifiEnterpriseConfig.CA_CERT_ALIAS_DELIMITER);
        if (memInfo.hasSwappedOutPss) {
            printRow(pw, TWO_COUNT_COLUMNS, "TOTAL:", Integer.valueOf(memInfo.getSummaryTotalPss()), "TOTAL SWAP PSS:", Integer.valueOf(memInfo.getSummaryTotalSwapPss()));
        } else {
            printRow(pw, TWO_COUNT_COLUMNS, "TOTAL:", Integer.valueOf(memInfo.getSummaryTotalPss()), "TOTAL SWAP (KB):", Integer.valueOf(memInfo.getSummaryTotalSwap()));
        }
    }

    public void registerOnActivityPausedListener(Activity activity, OnActivityPausedListener listener) {
        synchronized (this.mOnPauseListeners) {
            ArrayList<OnActivityPausedListener> list = (ArrayList) this.mOnPauseListeners.get(activity);
            if (list == null) {
                list = new ArrayList();
                this.mOnPauseListeners.put(activity, list);
            }
            list.add(listener);
        }
    }

    public void unregisterOnActivityPausedListener(Activity activity, OnActivityPausedListener listener) {
        synchronized (this.mOnPauseListeners) {
            ArrayList<OnActivityPausedListener> list = (ArrayList) this.mOnPauseListeners.get(activity);
            if (list != null) {
                list.remove(listener);
            }
        }
    }

    public final ActivityInfo resolveActivityInfo(Intent intent) {
        ActivityInfo aInfo = intent.resolveActivityInfo(this.mInitialApplication.getPackageManager(), 1024);
        if (aInfo == null) {
            Instrumentation.checkStartActivityResult(-2, intent);
        }
        return aInfo;
    }

    public final Activity startActivityNow(Activity parent, String id, Intent intent, ActivityInfo activityInfo, IBinder token, Bundle state, NonConfigurationInstances lastNonConfigurationInstances) {
        ActivityClientRecord r = new ActivityClientRecord();
        r.token = token;
        r.ident = 0;
        r.intent = intent;
        r.state = state;
        r.parent = parent;
        r.embeddedID = id;
        r.activityInfo = activityInfo;
        r.lastNonConfigurationInstances = lastNonConfigurationInstances;
        return performLaunchActivity(r, null);
    }

    public final Activity getActivity(IBinder token) {
        return ((ActivityClientRecord) this.mActivities.get(token)).activity;
    }

    public final void sendActivityResult(IBinder token, String id, int requestCode, int resultCode, Intent data) {
        ArrayList<ResultInfo> list = new ArrayList();
        list.add(new ResultInfo(id, requestCode, resultCode, data));
        this.mAppThread.scheduleSendResult(token, list);
    }

    private void sendMessage(int what, Object obj) {
        sendMessage(what, obj, 0, 0, false);
    }

    private void sendMessage(int what, Object obj, int arg1) {
        sendMessage(what, obj, arg1, 0, false);
    }

    private void sendMessage(int what, Object obj, int arg1, int arg2) {
        sendMessage(what, obj, arg1, arg2, false);
    }

    private void sendMessage(int what, Object obj, int arg1, int arg2, boolean async) {
        Message msg = Message.obtain();
        msg.what = what;
        msg.obj = obj;
        msg.arg1 = arg1;
        msg.arg2 = arg2;
        if (async) {
            msg.setAsynchronous(true);
        }
        this.mH.sendMessage(msg);
    }

    private void sendMessage(int what, Object obj, int arg1, int arg2, int seq) {
        Message msg = Message.obtain();
        msg.what = what;
        SomeArgs args = SomeArgs.obtain();
        args.arg1 = obj;
        args.argi1 = arg1;
        args.argi2 = arg2;
        args.argi3 = seq;
        msg.obj = args;
        this.mH.sendMessage(msg);
    }

    final void scheduleContextCleanup(ContextImpl context, String who, String what) {
        ContextCleanupInfo cci = new ContextCleanupInfo();
        cci.context = context;
        cci.who = who;
        cci.what = what;
        sendMessage(119, cci);
    }

    private Activity performLaunchActivity(ActivityClientRecord r, Intent customIntent) {
        ActivityInfo aInfo = r.activityInfo;
        if (r.packageInfo == null) {
            r.packageInfo = getPackageInfo(aInfo.applicationInfo, r.compatInfo, 1);
        }
        ComponentName component = r.intent.getComponent();
        if (component == null) {
            component = r.intent.resolveActivity(this.mInitialApplication.getPackageManager());
            r.intent.setComponent(component);
        }
        if (r.activityInfo.targetActivity != null) {
            ComponentName componentName = new ComponentName(r.activityInfo.packageName, r.activityInfo.targetActivity);
        }
        Activity activity = null;
        try {
            ClassLoader cl = r.packageInfo.getClassLoader();
            activity = this.mInstrumentation.newActivity(cl, component.getClassName(), r.intent);
            StrictMode.incrementExpectedActivityCount(activity.getClass());
            r.intent.setExtrasClassLoader(cl);
            r.intent.prepareToEnterProcess();
            if (r.state != null) {
                r.state.setClassLoader(cl);
            }
        } catch (Throwable e) {
            if (!this.mInstrumentation.onException(null, e)) {
                throw new RuntimeException("Unable to instantiate activity " + component + ": " + e.toString(), e);
            }
        }
        try {
            Application app = r.packageInfo.makeApplication(false, this.mInstrumentation);
            if (activity != null) {
                Context appContext = createBaseContextForActivity(r, activity);
                CharSequence title = r.activityInfo.loadLabel(appContext.getPackageManager());
                Configuration config = new Configuration(this.mCompatConfiguration);
                if (r.overrideConfig != null) {
                    config.updateFrom(r.overrideConfig);
                }
                Window window = null;
                if (r.mPendingRemoveWindow != null && r.mPreserveWindow) {
                    window = r.mPendingRemoveWindow;
                    r.mPendingRemoveWindow = null;
                    r.mPendingRemoveWindowManager = null;
                }
                activity.attach(appContext, this, getInstrumentation(), r.token, r.ident, app, r.intent, r.activityInfo, title, r.parent, r.embeddedID, r.lastNonConfigurationInstances, config, r.referrer, r.voiceInteractor, window);
                if (customIntent != null) {
                    activity.mIntent = customIntent;
                }
                r.lastNonConfigurationInstances = null;
                activity.mStartedActivity = false;
                int theme = r.activityInfo.getThemeResource();
                if (theme != 0) {
                    activity.setTheme(theme);
                }
                activity.mCalled = false;
                Slog.v(TAG, "ActivityThread,callActivityOnCreate");
                if (r.isPersistable()) {
                    this.mInstrumentation.callActivityOnCreate(activity, r.state, r.persistentState);
                } else {
                    this.mInstrumentation.callActivityOnCreate(activity, r.state);
                }
                if (activity.mCalled) {
                    r.activity = activity;
                    r.stopped = true;
                    if (!r.activity.mFinished) {
                        activity.performStart();
                        r.stopped = false;
                    }
                    if (!r.activity.mFinished) {
                        if (r.isPersistable()) {
                            if (!(r.state == null && r.persistentState == null)) {
                                this.mInstrumentation.callActivityOnRestoreInstanceState(activity, r.state, r.persistentState);
                            }
                        } else if (r.state != null) {
                            this.mInstrumentation.callActivityOnRestoreInstanceState(activity, r.state);
                        }
                    }
                    if (!r.activity.mFinished) {
                        activity.mCalled = false;
                        if (r.isPersistable()) {
                            this.mInstrumentation.callActivityOnPostCreate(activity, r.state, r.persistentState);
                        } else {
                            this.mInstrumentation.callActivityOnPostCreate(activity, r.state);
                        }
                        if (!activity.mCalled) {
                            throw new SuperNotCalledException("Activity " + r.intent.getComponent().toShortString() + " did not call through to super.onPostCreate()");
                        }
                    }
                }
                throw new SuperNotCalledException("Activity " + r.intent.getComponent().toShortString() + " did not call through to super.onCreate()");
            }
            r.paused = true;
            this.mActivities.put(r.token, r);
            Slog.d(TAG, "add activity client record, r= " + r + " token= " + r.token);
        } catch (SuperNotCalledException e2) {
            throw e2;
        } catch (Throwable e3) {
            if (!this.mInstrumentation.onException(activity, e3)) {
                throw new RuntimeException("Unable to start activity " + component + ": " + e3.toString(), e3);
            }
        }
        return activity;
    }

    private Context createBaseContextForActivity(ActivityClientRecord r, Activity activity) {
        try {
            Context appContext = ContextImpl.createActivityContext(this, r.packageInfo, r.token, ActivityManagerNative.getDefault().getActivityDisplayId(r.token), r.overrideConfig);
            appContext.setOuterContext(activity);
            Context baseContext = appContext;
            DisplayManagerGlobal dm = DisplayManagerGlobal.getInstance();
            String pkgName = SystemProperties.get("debug.second-display.pkg");
            if (pkgName == null || pkgName.isEmpty() || !r.packageInfo.mPackageName.contains(pkgName)) {
                return baseContext;
            }
            for (int id : dm.getDisplayIds()) {
                if (id != 0) {
                    return appContext.createDisplayContext(dm.getCompatibleDisplay(id, appContext.getDisplayAdjustments(id)));
                }
            }
            return baseContext;
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    private void handleLaunchActivity(ActivityClientRecord r, Intent customIntent, String reason) {
        boolean z = true;
        if (Jlog.isPerfTest() && r != null) {
            Jlog.i(2040, Intent.toPkgClsString(r.intent));
        }
        if (!(r == null || r.intent == null || r.intent.getComponent() == null)) {
            Jlog.d(336, r.intent.getComponent().getPackageName(), ProxyInfo.LOCAL_EXCL_LIST);
        }
        unscheduleGcIdler();
        this.mSomeActivitiesChanged = true;
        if (!(r == null || r.profilerInfo == null)) {
            this.mProfiler.setProfiler(r.profilerInfo);
            this.mProfiler.startProfiling();
        }
        handleConfigurationChanged(null, null);
        WindowManagerGlobal.initialize();
        Activity a = performLaunchActivity(r, customIntent);
        if (Jlog.isPerfTest()) {
            Jlog.i(2041, Intent.toPkgClsString(r.intent));
        }
        if (a != null) {
            r.createdConfig = new Configuration(this.mConfiguration);
            reportSizeConfigurations(r);
            Bundle oldState = r.state;
            IBinder iBinder = r.token;
            boolean z2 = r.isForward;
            if (r.activity.mFinished || r.startsNotResumed) {
                z = false;
            }
            handleResumeActivity(iBinder, false, z2, z, r.lastProcessedSeq, reason);
            if (!r.activity.mFinished && r.startsNotResumed) {
                performPauseActivityIfNeeded(r, reason);
                if (r.isPreHoneycomb()) {
                    r.state = oldState;
                    return;
                }
                return;
            }
            return;
        }
        try {
            ActivityManagerNative.getDefault().finishActivity(r.token, 0, null, 0);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    private void reportSizeConfigurations(ActivityClientRecord r) {
        Configuration[] configurations = r.activity.getResources().getSizeConfigurations();
        if (configurations != null) {
            SparseIntArray horizontal = new SparseIntArray();
            SparseIntArray vertical = new SparseIntArray();
            SparseIntArray smallest = new SparseIntArray();
            for (int i = configurations.length - 1; i >= 0; i--) {
                Configuration config = configurations[i];
                if (config.screenHeightDp != 0) {
                    vertical.put(config.screenHeightDp, 0);
                }
                if (config.screenWidthDp != 0) {
                    horizontal.put(config.screenWidthDp, 0);
                }
                if (config.smallestScreenWidthDp != 0) {
                    smallest.put(config.smallestScreenWidthDp, 0);
                }
            }
            try {
                ActivityManagerNative.getDefault().reportSizeConfigurations(r.token, horizontal.copyKeys(), vertical.copyKeys(), smallest.copyKeys());
            } catch (RemoteException ex) {
                throw ex.rethrowFromSystemServer();
            }
        }
    }

    private void deliverNewIntents(ActivityClientRecord r, List<ReferrerIntent> intents) {
        int N = intents.size();
        for (int i = 0; i < N; i++) {
            ReferrerIntent intent = (ReferrerIntent) intents.get(i);
            intent.setExtrasClassLoader(r.activity.getClassLoader());
            intent.prepareToEnterProcess();
            r.activity.mFragments.noteStateNotSaved();
            this.mInstrumentation.callActivityOnNewIntent(r.activity, intent);
        }
    }

    public final void performNewIntents(IBinder token, List<ReferrerIntent> intents) {
        ActivityClientRecord r = (ActivityClientRecord) this.mActivities.get(token);
        if (r != null) {
            boolean resumed;
            if (r.paused) {
                resumed = false;
            } else {
                resumed = true;
            }
            if (resumed) {
                r.activity.mTemporaryPause = true;
                this.mInstrumentation.callActivityOnPause(r.activity);
            }
            deliverNewIntents(r, intents);
            if (resumed) {
                r.activity.performResume();
                r.activity.mTemporaryPause = false;
            }
        }
    }

    private void handleNewIntent(NewIntentData data) {
        performNewIntents(data.token, data.intents);
    }

    public void handleRequestAssistContextExtras(RequestAssistContextExtras cmd) {
        AssistStructure structure;
        if (this.mLastSessionId != cmd.sessionId) {
            this.mLastSessionId = cmd.sessionId;
            for (int i = this.mLastAssistStructures.size() - 1; i >= 0; i--) {
                structure = (AssistStructure) ((WeakReference) this.mLastAssistStructures.get(i)).get();
                if (structure != null) {
                    structure.clearSendChannel();
                }
                this.mLastAssistStructures.remove(i);
            }
        }
        Bundle data = new Bundle();
        structure = null;
        AssistContent content = new AssistContent();
        ActivityClientRecord r = (ActivityClientRecord) this.mActivities.get(cmd.activityToken);
        Uri uri = null;
        if (r != null) {
            r.activity.getApplication().dispatchOnProvideAssistData(r.activity, data);
            r.activity.onProvideAssistData(data);
            uri = r.activity.onProvideReferrer();
            if (cmd.requestType == 1) {
                structure = new AssistStructure(r.activity);
                Intent activityIntent = r.activity.getIntent();
                if (activityIntent == null || !(r.window == null || (r.window.getAttributes().flags & 8192) == 0)) {
                    content.setDefaultIntent(new Intent());
                } else {
                    Intent intent = new Intent(activityIntent);
                    intent.setFlags(intent.getFlags() & -67);
                    intent.removeUnsafeExtras();
                    content.setDefaultIntent(intent);
                }
                r.activity.onProvideAssistContent(content);
            }
        }
        if (structure == null) {
            structure = new AssistStructure();
        }
        this.mLastAssistStructures.add(new WeakReference(structure));
        try {
            ActivityManagerNative.getDefault().reportAssistContextExtras(cmd.requestToken, data, structure, content, uri);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void handleTranslucentConversionComplete(IBinder token, boolean drawComplete) {
        ActivityClientRecord r = (ActivityClientRecord) this.mActivities.get(token);
        if (r != null) {
            r.activity.onTranslucentConversionComplete(drawComplete);
        }
    }

    public void onNewActivityOptions(IBinder token, ActivityOptions options) {
        ActivityClientRecord r = (ActivityClientRecord) this.mActivities.get(token);
        if (r != null) {
            r.activity.onNewActivityOptions(options);
        }
    }

    public void handleCancelVisibleBehind(IBinder token) {
        ActivityClientRecord r = (ActivityClientRecord) this.mActivities.get(token);
        if (r != null) {
            this.mSomeActivitiesChanged = true;
            Activity activity = r.activity;
            if (activity.mVisibleBehind) {
                activity.mCalled = false;
                activity.onVisibleBehindCanceled();
                if (activity.mCalled) {
                    activity.mVisibleBehind = false;
                } else {
                    throw new SuperNotCalledException("Activity " + activity.getLocalClassName() + " did not call through to super.onVisibleBehindCanceled()");
                }
            }
        }
        try {
            ActivityManagerNative.getDefault().backgroundResourcesReleased(token);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void handleOnBackgroundVisibleBehindChanged(IBinder token, boolean visible) {
        ActivityClientRecord r = (ActivityClientRecord) this.mActivities.get(token);
        if (r != null) {
            r.activity.onBackgroundVisibleBehindChanged(visible);
        }
    }

    public void handleInstallProvider(ProviderInfo info) {
        ThreadPolicy oldPolicy = StrictMode.allowThreadDiskWrites();
        try {
            installContentProviders(this.mInitialApplication, Lists.newArrayList(new ProviderInfo[]{info}));
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    private void handleEnterAnimationComplete(IBinder token) {
        ActivityClientRecord r = (ActivityClientRecord) this.mActivities.get(token);
        if (r != null) {
            r.activity.dispatchEnterAnimationComplete();
        }
    }

    private void handleStartBinderTracking() {
        Binder.enableTracing();
    }

    private void handleStopBinderTrackingAndDump(ParcelFileDescriptor fd) {
        try {
            Binder.disableTracing();
            Binder.getTransactionTracker().writeTracesToFile(fd);
        } finally {
            IoUtils.closeQuietly(fd);
            Binder.getTransactionTracker().clearTraces();
        }
    }

    private void handleMultiWindowModeChanged(IBinder token, boolean isInMultiWindowMode) {
        ActivityClientRecord r = (ActivityClientRecord) this.mActivities.get(token);
        if (r != null) {
            r.activity.dispatchMultiWindowModeChanged(isInMultiWindowMode);
        }
        handleMultiWindowModeChangedForRog(token, isInMultiWindowMode);
    }

    private void handlePictureInPictureModeChanged(IBinder token, boolean isInPipMode) {
        ActivityClientRecord r = (ActivityClientRecord) this.mActivities.get(token);
        if (r != null) {
            r.activity.dispatchPictureInPictureModeChanged(isInPipMode);
        }
    }

    private void handleLocalVoiceInteractionStarted(IBinder token, IVoiceInteractor interactor) {
        ActivityClientRecord r = (ActivityClientRecord) this.mActivities.get(token);
        if (r != null) {
            r.voiceInteractor = interactor;
            r.activity.setVoiceInteractor(interactor);
            if (interactor == null) {
                r.activity.onLocalVoiceInteractionStopped();
            } else {
                r.activity.onLocalVoiceInteractionStarted();
            }
        }
    }

    public static Intent getIntentBeingBroadcast() {
        return (Intent) sCurrentBroadcastIntent.get();
    }

    private void handleReceiver(ReceiverData data) {
        unscheduleGcIdler();
        String component = data.intent.getComponent().getClassName();
        LoadedApk packageInfo = getPackageInfoNoCheck(data.info.applicationInfo, data.compatInfo);
        IActivityManager mgr = ActivityManagerNative.getDefault();
        try {
            ClassLoader cl = packageInfo.getClassLoader();
            data.intent.setExtrasClassLoader(cl);
            data.intent.prepareToEnterProcess();
            data.setExtrasClassLoader(cl);
            BroadcastReceiver receiver = (BroadcastReceiver) cl.loadClass(component).newInstance();
            try {
                ContextImpl context = (ContextImpl) packageInfo.makeApplication(false, this.mInstrumentation).getBaseContext();
                sCurrentBroadcastIntent.set(data.intent);
                receiver.setPendingResult(data);
                Trace.traceBegin(64, "staticBroadcast.onReceive, " + data.intent + " receiver thread[" + Thread.currentThread().getName() + "(" + Process.myPid() + ")] className[" + component + "]");
                receiver.onReceive(context.getReceiverRestrictedContext(), data.intent);
                Trace.traceEnd(64);
            } catch (Exception e) {
                data.sendFinished(mgr);
                if (!this.mInstrumentation.onException(receiver, e)) {
                    throw new RuntimeException("Unable to start receiver " + component + ": " + e.toString(), e);
                }
            } finally {
                sCurrentBroadcastIntent.set(null);
            }
            if (receiver.getPendingResult() != null) {
                data.finish();
            }
        } catch (Exception e2) {
            data.sendFinished(mgr);
            throw new RuntimeException("Unable to instantiate receiver " + component + ": " + e2.toString(), e2);
        }
    }

    private void handleCreateBackupAgent(CreateBackupAgentData data) {
        try {
            if (getPackageManager().getPackageInfo(data.appInfo.packageName, 0, UserHandle.myUserId()).applicationInfo.uid != Process.myUid()) {
                Slog.w(TAG, "Asked to instantiate non-matching package " + data.appInfo.packageName);
                return;
            }
            unscheduleGcIdler();
            LoadedApk packageInfo = getPackageInfoNoCheck(data.appInfo, data.compatInfo);
            String packageName = packageInfo.mPackageName;
            if (packageName == null) {
                Slog.d(TAG, "Asked to create backup agent for nonexistent package");
                return;
            }
            String classname = data.appInfo.backupAgentName;
            if (classname == null && (data.backupMode == 1 || data.backupMode == 3)) {
                classname = "android.app.backup.FullBackupAgent";
            }
            IBinder iBinder = null;
            try {
                BackupAgent agent = (BackupAgent) this.mBackupAgents.get(packageName);
                if (agent != null) {
                    iBinder = agent.onBind();
                } else {
                    agent = (BackupAgent) packageInfo.getClassLoader().loadClass(classname).newInstance();
                    ContextImpl context = ContextImpl.createAppContext(this, packageInfo);
                    context.setOuterContext(agent);
                    agent.attach(context);
                    agent.onCreate();
                    iBinder = agent.onBind();
                    this.mBackupAgents.put(packageName, agent);
                }
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            } catch (Exception e2) {
                Slog.e(TAG, "Agent threw during creation: " + e2);
                if (!(data.backupMode == 2 || data.backupMode == 3)) {
                    throw e2;
                }
            } catch (Exception e22) {
                throw new RuntimeException("Unable to create BackupAgent " + classname + ": " + e22.toString(), e22);
            }
            ActivityManagerNative.getDefault().backupAgentCreated(packageName, iBinder);
        } catch (RemoteException e3) {
            throw e3.rethrowFromSystemServer();
        }
    }

    private void handleDestroyBackupAgent(CreateBackupAgentData data) {
        String packageName = getPackageInfoNoCheck(data.appInfo, data.compatInfo).mPackageName;
        BackupAgent agent = (BackupAgent) this.mBackupAgents.get(packageName);
        if (agent != null) {
            try {
                agent.onDestroy();
            } catch (Exception e) {
                Slog.w(TAG, "Exception thrown in onDestroy by backup agent of " + data.appInfo);
                e.printStackTrace();
            }
            this.mBackupAgents.remove(packageName);
            return;
        }
        Slog.w(TAG, "Attempt to destroy unknown backup agent " + data);
    }

    private void handleCreateService(CreateServiceData data) {
        unscheduleGcIdler();
        LoadedApk packageInfo = getPackageInfoNoCheck(data.info.applicationInfo, data.compatInfo);
        Context service = null;
        try {
            service = (Service) packageInfo.getClassLoader().loadClass(data.info.name).newInstance();
        } catch (Exception e) {
            if (!this.mInstrumentation.onException(service, e)) {
                throw new RuntimeException("Unable to instantiate service " + data.info.name + ": " + e.toString(), e);
            }
        }
        try {
            ContextImpl context = ContextImpl.createAppContext(this, packageInfo);
            context.setOuterContext(service);
            service.attach(context, this, data.info.name, data.token, packageInfo.makeApplication(false, this.mInstrumentation), ActivityManagerNative.getDefault());
            Trace.traceBegin(64, "service.onCreate, className:[" + service.getClassName() + "]");
            service.onCreate();
            Trace.traceEnd(64);
            this.mServices.put(data.token, service);
            ActivityManagerNative.getDefault().serviceDoneExecuting(data.token, 0, 0, 0);
            if (Jlog.isUBMEnable()) {
                Jlog.d(270, "SC#" + data.info.name + "(" + currentProcessName() + ")");
            }
        } catch (RemoteException e2) {
            Flog.w(102, "serviceDone failed when creating service " + data.info.name + ": " + e2.toString());
            throw e2.rethrowFromSystemServer();
        } catch (Exception e3) {
            if (!this.mInstrumentation.onException(service, e3)) {
                throw new RuntimeException("Unable to create service " + data.info.name + ": " + e3.toString(), e3);
            }
        }
    }

    private void handleBindService(BindServiceData data) {
        Service s = (Service) this.mServices.get(data.token);
        if (s != null) {
            try {
                data.intent.setExtrasClassLoader(s.getClassLoader());
                data.intent.prepareToEnterProcess();
                if (data.rebind) {
                    Trace.traceBegin(64, "service.onRebind, className:[" + s.getClassName() + "]");
                    s.onRebind(data.intent);
                    Trace.traceEnd(64);
                    ActivityManagerNative.getDefault().serviceDoneExecuting(data.token, 0, 0, 0);
                } else {
                    Trace.traceBegin(64, "service.onBind, className:[" + s.getClassName() + "]");
                    IBinder binder = s.onBind(data.intent);
                    Trace.traceEnd(64);
                    ActivityManagerNative.getDefault().publishService(data.token, data.intent, binder);
                }
                ensureJitEnabled();
                return;
            } catch (RemoteException ex) {
                Flog.w(102, "publishService failed when binding service " + s + " with " + data.intent + ": " + ex.toString());
                throw ex.rethrowFromSystemServer();
            } catch (Exception e) {
                if (!this.mInstrumentation.onException(s, e)) {
                    throw new RuntimeException("Unable to bind to service " + s + " with " + data.intent + ": " + e.toString(), e);
                }
                return;
            }
        }
        Flog.v(101, "service can't be binded");
    }

    private void handleUnbindService(BindServiceData data) {
        Service s = (Service) this.mServices.get(data.token);
        if (s != null) {
            try {
                data.intent.setExtrasClassLoader(s.getClassLoader());
                data.intent.prepareToEnterProcess();
                Trace.traceBegin(64, "service.onUnbind, className:[" + s.getClassName() + "]");
                boolean doRebind = s.onUnbind(data.intent);
                Trace.traceEnd(64);
                if (doRebind) {
                    ActivityManagerNative.getDefault().unbindFinished(data.token, data.intent, doRebind);
                } else {
                    ActivityManagerNative.getDefault().serviceDoneExecuting(data.token, 0, 0, 0);
                }
            } catch (RemoteException ex) {
                Flog.w(102, "Unable to finish unbind to service " + s + " with " + data.intent + ": " + ex.toString());
                throw ex.rethrowFromSystemServer();
            } catch (Exception e) {
                if (!this.mInstrumentation.onException(s, e)) {
                    throw new RuntimeException("Unable to unbind to service " + s + " with " + data.intent + ": " + e.toString(), e);
                }
            }
        }
    }

    private void handleDumpService(DumpComponentInfo info) {
        ThreadPolicy oldPolicy = StrictMode.allowThreadDiskWrites();
        try {
            Service s = (Service) this.mServices.get(info.token);
            if (s != null) {
                PrintWriter pw = new FastPrintWriter(new FileOutputStream(info.fd.getFileDescriptor()));
                s.dump(info.fd.getFileDescriptor(), pw, info.args);
                pw.flush();
            }
            IoUtils.closeQuietly(info.fd);
            StrictMode.setThreadPolicy(oldPolicy);
        } catch (Throwable th) {
            IoUtils.closeQuietly(info.fd);
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    private void handleDumpActivity(DumpComponentInfo info) {
        ThreadPolicy oldPolicy = StrictMode.allowThreadDiskWrites();
        try {
            ActivityClientRecord r = (ActivityClientRecord) this.mActivities.get(info.token);
            if (!(r == null || r.activity == null)) {
                PrintWriter pw = new FastPrintWriter(new FileOutputStream(info.fd.getFileDescriptor()));
                r.activity.dump(info.prefix, info.fd.getFileDescriptor(), pw, info.args);
                pw.flush();
            }
            IoUtils.closeQuietly(info.fd);
            StrictMode.setThreadPolicy(oldPolicy);
        } catch (Throwable th) {
            IoUtils.closeQuietly(info.fd);
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    private void handleDumpProvider(DumpComponentInfo info) {
        ThreadPolicy oldPolicy = StrictMode.allowThreadDiskWrites();
        try {
            ProviderClientRecord r = (ProviderClientRecord) this.mLocalProviders.get(info.token);
            if (!(r == null || r.mLocalProvider == null)) {
                PrintWriter pw = new FastPrintWriter(new FileOutputStream(info.fd.getFileDescriptor()));
                r.mLocalProvider.dump(info.fd.getFileDescriptor(), pw, info.args);
                pw.flush();
            }
            IoUtils.closeQuietly(info.fd);
            StrictMode.setThreadPolicy(oldPolicy);
        } catch (Throwable th) {
            IoUtils.closeQuietly(info.fd);
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    private void handleServiceArgs(ServiceArgsData data) {
        Service s = (Service) this.mServices.get(data.token);
        if (s != null) {
            try {
                int res;
                if (data.args != null) {
                    data.args.setExtrasClassLoader(s.getClassLoader());
                    data.args.prepareToEnterProcess();
                }
                if (data.taskRemoved) {
                    Trace.traceBegin(64, "service.onTaskRemoved, className:[" + s.getClassName() + "]");
                    s.onTaskRemoved(data.args);
                    Trace.traceEnd(64);
                    res = 1000;
                } else {
                    Trace.traceBegin(64, "service.onStartCommand, className:[" + s.getClassName() + "]");
                    res = s.onStartCommand(data.args, data.flags, data.startId);
                    Trace.traceEnd(64);
                }
                QueuedWork.waitToFinish();
                ActivityManagerNative.getDefault().serviceDoneExecuting(data.token, 1, data.startId, res);
                ensureJitEnabled();
            } catch (RemoteException e) {
                Flog.w(102, "Unable to finish starting service " + s + " with " + data.args + ": " + e.toString());
                throw e.rethrowFromSystemServer();
            } catch (Exception e2) {
                if (!this.mInstrumentation.onException(s, e2)) {
                    throw new RuntimeException("Unable to start service " + s + " with " + data.args + ": " + e2.toString(), e2);
                }
            }
        }
    }

    private void handleStopService(IBinder token) {
        Service s = (Service) this.mServices.remove(token);
        if (s != null) {
            try {
                Trace.traceBegin(64, "service.onDestroy, className:[" + s.getClassName() + "]");
                s.onDestroy();
                Trace.traceEnd(64);
                Context context = s.getBaseContext();
                if (context instanceof ContextImpl) {
                    ((ContextImpl) context).scheduleFinalCleanup(s.getClassName(), "Service");
                }
                QueuedWork.waitToFinish();
                ActivityManagerNative.getDefault().serviceDoneExecuting(token, 2, 0, 0);
                if (Jlog.isUBMEnable()) {
                    Jlog.d(AnqpInformationElement.ANQP_EMERGENCY_NAI, "SE#" + s.getClassName());
                    return;
                }
                return;
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            } catch (Exception e2) {
                if (this.mInstrumentation.onException(s, e2)) {
                    Slog.i(TAG, "handleStopService: exception for " + token, e2);
                    return;
                }
                throw new RuntimeException("Unable to stop service " + s + ": " + e2.toString(), e2);
            }
        }
        Slog.i(TAG, "handleStopService: token=" + token + " not found.");
    }

    public final ActivityClientRecord performResumeActivity(IBinder token, boolean clearHide, String reason) {
        ActivityClientRecord r = (ActivityClientRecord) this.mActivities.get(token);
        if (!(r == null || r.activity.mFinished)) {
            if (clearHide) {
                r.hideForNow = false;
                r.activity.mStartedActivity = false;
            }
            try {
                r.activity.onStateNotSaved();
                r.activity.mFragments.noteStateNotSaved();
                if (r.pendingIntents != null) {
                    deliverNewIntents(r, r.pendingIntents);
                    r.pendingIntents = null;
                }
                if (r.pendingResults != null) {
                    deliverResults(r, r.pendingResults);
                    r.pendingResults = null;
                }
                r.activity.performResume();
                for (int i = this.mRelaunchingActivities.size() - 1; i >= 0; i--) {
                    ActivityClientRecord relaunching = (ActivityClientRecord) this.mRelaunchingActivities.get(i);
                    if (relaunching.token == r.token && relaunching.onlyLocalRequest && relaunching.startsNotResumed) {
                        relaunching.startsNotResumed = false;
                    }
                }
                EventLog.writeEvent(LOG_AM_ON_RESUME_CALLED, new Object[]{Integer.valueOf(UserHandle.myUserId()), r.activity.getComponentName().getClassName(), reason});
                EventLog.writeEvent(LOG_AM_ON_RESUME_CALLED, new Object[]{Integer.valueOf(UserHandle.myUserId()), r.activity.getComponentName().getClassName()});
                r.paused = false;
                r.stopped = false;
                r.state = null;
                r.persistentState = null;
            } catch (Exception e) {
                if (!this.mInstrumentation.onException(r.activity, e)) {
                    throw new RuntimeException("Unable to resume activity " + r.intent.getComponent().toShortString() + ": " + e.toString(), e);
                }
            }
        }
        return r;
    }

    static final void cleanUpPendingRemoveWindows(ActivityClientRecord r, boolean force) {
        if (!r.mPreserveWindow || force) {
            if (r.mPendingRemoveWindow != null) {
                r.mPendingRemoveWindowManager.removeViewImmediate(r.mPendingRemoveWindow.getDecorView());
                IBinder wtoken = r.mPendingRemoveWindow.getDecorView().getWindowToken();
                if (wtoken != null) {
                    WindowManagerGlobal.getInstance().closeAll(wtoken, r.activity.getClass().getName(), "Activity");
                }
            }
            r.mPendingRemoveWindow = null;
            r.mPendingRemoveWindowManager = null;
        }
    }

    final void handleResumeActivity(IBinder token, boolean clearHide, boolean isForward, boolean reallyResume, int seq, String reason) {
        if (checkAndUpdateLifecycleSeq(seq, (ActivityClientRecord) this.mActivities.get(token), "resumeActivity")) {
            unscheduleGcIdler();
            this.mSomeActivitiesChanged = true;
            ActivityClientRecord r = performResumeActivity(token, clearHide, reason);
            if (r != null) {
                LayoutParams l;
                if (Jlog.isPerfTest()) {
                    Jlog.i(2044, Intent.toPkgClsString(r.intent));
                }
                Activity a = r.activity;
                int forwardBit = isForward ? 256 : 0;
                boolean willBeVisible = !a.mStartedActivity;
                if (!willBeVisible) {
                    try {
                        willBeVisible = ActivityManagerNative.getDefault().willActivityBeVisible(a.getActivityToken());
                    } catch (RemoteException e) {
                        throw e.rethrowFromSystemServer();
                    }
                }
                if (r.window == null && !a.mFinished && willBeVisible) {
                    r.window = r.activity.getWindow();
                    View decor = r.window.getDecorView();
                    decor.setVisibility(4);
                    ViewManager wm = a.getWindowManager();
                    l = r.window.getAttributes();
                    a.mDecor = decor;
                    l.type = 1;
                    l.softInputMode |= forwardBit;
                    if (r.mPreserveWindow) {
                        a.mWindowAdded = true;
                        r.mPreserveWindow = false;
                        ViewRootImpl impl = decor.getViewRootImpl();
                        if (impl != null) {
                            impl.notifyChildRebuilt();
                        }
                    }
                    if (a.mVisibleFromClient && !a.mWindowAdded) {
                        a.mWindowAdded = true;
                        wm.addView(decor, l);
                    }
                } else if (!willBeVisible) {
                    r.hideForNow = true;
                }
                cleanUpPendingRemoveWindows(r, false);
                if (!(r.activity.mFinished || !willBeVisible || r.activity.mDecor == null || r.hideForNow)) {
                    if (r.newConfig != null) {
                        performConfigurationChangedForActivity(r, r.newConfig, true);
                        r.newConfig = null;
                    }
                    l = r.window.getAttributes();
                    Flog.i(101, "Resuming " + r + " with isForward=" + isForward + ",forwardBitChanged=" + ((l.softInputMode & 256) != forwardBit) + " onlyLocalRequest=" + r.onlyLocalRequest);
                    if ((l.softInputMode & 256) != forwardBit) {
                        l.softInputMode = (l.softInputMode & -257) | forwardBit;
                        if (r.activity.mVisibleFromClient) {
                            a.getWindowManager().updateViewLayout(r.window.getDecorView(), l);
                        }
                    }
                    r.activity.mVisibleFromServer = true;
                    this.mNumVisibleActivities++;
                    if (r.activity.mVisibleFromClient) {
                        r.activity.makeVisible();
                    }
                }
                if (!r.onlyLocalRequest) {
                    r.nextIdle = this.mNewActivities;
                    this.mNewActivities = r;
                    Looper.myQueue().addIdleHandler(new Idler());
                }
                r.onlyLocalRequest = false;
                if (reallyResume) {
                    try {
                        ActivityManagerNative.getDefault().activityResumed(token);
                    } catch (RemoteException ex) {
                        throw ex.rethrowFromSystemServer();
                    }
                }
                ViewRootImpl.setIsFirstFrame(true);
                if (Jlog.isPerfTest()) {
                    Jlog.i(AwareNRTConstant.FIRST_EAPA_EVENT_ID, Intent.toPkgClsString(r.intent));
                }
                if (IS_DEBUG_VERSION) {
                    ArrayMap<String, Object> params = new ArrayMap();
                    params.put("checkType", "TransparentActivityScene");
                    params.put("looper", Looper.myLooper());
                    params.put("token", token);
                    if (r.window != null) {
                        params.put("layoutParams", r.window.getAttributes());
                    }
                    params.put("activity", r.activity);
                    if (HwFrameworkFactory.getAppFreezeScreenMonitor() != null) {
                        HwFrameworkFactory.getAppFreezeScreenMonitor().checkFreezeScreen(params);
                    }
                }
            } else {
                try {
                    ActivityManagerNative.getDefault().finishActivity(token, 0, null, 0);
                } catch (RemoteException ex2) {
                    throw ex2.rethrowFromSystemServer();
                }
            }
        }
    }

    private Bitmap createThumbnailBitmap(ActivityClientRecord r) {
        Bitmap thumbnail = this.mAvailThumbnailBitmap;
        if (thumbnail == null) {
            try {
                int h;
                int w = this.mThumbnailWidth;
                if (w < 0) {
                    Resources res = r.activity.getResources();
                    w = res.getDimensionPixelSize(R.dimen.thumbnail_width);
                    this.mThumbnailWidth = w;
                    h = res.getDimensionPixelSize(R.dimen.thumbnail_height);
                    this.mThumbnailHeight = h;
                } else {
                    h = this.mThumbnailHeight;
                }
                if (w > 0 && h > 0) {
                    thumbnail = Bitmap.createBitmap(r.activity.getResources().getDisplayMetrics(), w, h, THUMBNAIL_FORMAT);
                    thumbnail.eraseColor(0);
                }
            } catch (Exception e) {
                if (this.mInstrumentation.onException(r.activity, e)) {
                    return null;
                }
                throw new RuntimeException("Unable to create thumbnail of " + r.intent.getComponent().toShortString() + ": " + e.toString(), e);
            }
        }
        if (thumbnail == null) {
            return thumbnail;
        }
        Canvas cv = this.mThumbnailCanvas;
        if (cv == null) {
            cv = new Canvas();
            this.mThumbnailCanvas = cv;
        }
        cv.setBitmap(thumbnail);
        if (!r.activity.onCreateThumbnail(thumbnail, cv)) {
            this.mAvailThumbnailBitmap = thumbnail;
            thumbnail = null;
        }
        cv.setBitmap(null);
        return thumbnail;
    }

    private void handlePauseActivity(IBinder token, boolean finished, boolean userLeaving, int configChanges, boolean dontReport, int seq) {
        ActivityClientRecord r = (ActivityClientRecord) this.mActivities.get(token);
        if (checkAndUpdateLifecycleSeq(seq, r, "pauseActivity") && r != null) {
            if (Jlog.isPerfTest()) {
                Jlog.i(2026, Intent.toPkgClsString(r.intent, "who"));
            }
            if (userLeaving) {
                performUserLeavingActivity(r);
            }
            Activity activity = r.activity;
            activity.mConfigChangeFlags |= configChanges;
            performPauseActivity(token, finished, r.isPreHoneycomb(), "handlePauseActivity");
            if (r.isPreHoneycomb()) {
                QueuedWork.waitToFinish();
            }
            if (Jlog.isPerfTest()) {
                Jlog.i(2027, Intent.toPkgClsString(r.intent, "who"));
            }
            if (!dontReport) {
                try {
                    ActivityManagerNative.getDefault().activityPaused(token);
                } catch (RemoteException ex) {
                    throw ex.rethrowFromSystemServer();
                }
            }
            this.mSomeActivitiesChanged = true;
        }
    }

    final void performUserLeavingActivity(ActivityClientRecord r) {
        this.mInstrumentation.callActivityOnUserLeaving(r.activity);
    }

    final Bundle performPauseActivity(IBinder token, boolean finished, boolean saveState, String reason) {
        ActivityClientRecord r = (ActivityClientRecord) this.mActivities.get(token);
        if (r != null) {
            return performPauseActivity(r, finished, saveState, reason);
        }
        return null;
    }

    final Bundle performPauseActivity(ActivityClientRecord r, boolean finished, boolean saveState, String reason) {
        ArrayList<OnActivityPausedListener> listeners;
        if (r.paused) {
            if (r.activity.mFinished) {
                return null;
            }
            RuntimeException e = new RuntimeException("Performing pause of activity that is not resumed: " + r.intent.getComponent().toShortString());
            Slog.e(TAG, e.getMessage(), e);
        }
        if (finished) {
            r.activity.mFinished = true;
        }
        if (!r.activity.mFinished && saveState) {
            callCallActivityOnSaveInstanceState(r);
        }
        performPauseActivityIfNeeded(r, reason);
        synchronized (this.mOnPauseListeners) {
            listeners = (ArrayList) this.mOnPauseListeners.remove(r.activity);
        }
        int size = listeners != null ? listeners.size() : 0;
        for (int i = 0; i < size; i++) {
            ((OnActivityPausedListener) listeners.get(i)).onPaused(r.activity);
        }
        Bundle bundle = (r.activity.mFinished || !saveState) ? null : r.state;
        return bundle;
    }

    private void performPauseActivityIfNeeded(ActivityClientRecord r, String reason) {
        if (!r.paused) {
            try {
                r.activity.mCalled = false;
                this.mInstrumentation.callActivityOnPause(r.activity);
                EventLog.writeEvent(LOG_AM_ON_PAUSE_CALLED, new Object[]{Integer.valueOf(UserHandle.myUserId()), r.activity.getComponentName().getClassName(), reason});
                if (!r.activity.mCalled) {
                    throw new SuperNotCalledException("Activity " + safeToComponentShortString(r.intent) + " did not call through to super.onPause()");
                }
            } catch (SuperNotCalledException e) {
                throw e;
            } catch (Exception e2) {
                if (!this.mInstrumentation.onException(r.activity, e2)) {
                    throw new RuntimeException("Unable to pause activity " + safeToComponentShortString(r.intent) + ": " + e2.toString(), e2);
                }
            }
            r.paused = true;
        }
    }

    final void performStopActivity(IBinder token, boolean saveState, String reason) {
        performStopActivityInner((ActivityClientRecord) this.mActivities.get(token), null, false, saveState, reason);
    }

    private void performStopActivityInner(ActivityClientRecord r, StopInfo info, boolean keepShown, boolean saveState, String reason) {
        if (r != null) {
            if (!keepShown && r.stopped) {
                if (!r.activity.mFinished) {
                    RuntimeException e = new RuntimeException("Performing stop of activity that is already stopped: " + r.intent.getComponent().toShortString());
                    Slog.e(TAG, e.getMessage(), e);
                    Slog.e(TAG, r.getStateString());
                } else {
                    return;
                }
            }
            performPauseActivityIfNeeded(r, reason);
            if (info != null) {
                try {
                    info.description = r.activity.onCreateDescription();
                } catch (Exception e2) {
                    if (!this.mInstrumentation.onException(r.activity, e2)) {
                        throw new RuntimeException("Unable to save state of activity " + r.intent.getComponent().toShortString() + ": " + e2.toString(), e2);
                    }
                }
            }
            if (!r.activity.mFinished && saveState && r.state == null) {
                callCallActivityOnSaveInstanceState(r);
            }
            if (!keepShown) {
                try {
                    r.activity.performStop(false);
                } catch (Exception e22) {
                    if (!this.mInstrumentation.onException(r.activity, e22)) {
                        throw new RuntimeException("Unable to stop activity " + r.intent.getComponent().toShortString() + ": " + e22.toString(), e22);
                    }
                }
                r.stopped = true;
                EventLog.writeEvent(LOG_AM_ON_STOP_CALLED, new Object[]{Integer.valueOf(UserHandle.myUserId()), r.activity.getComponentName().getClassName(), reason});
            }
        }
    }

    private void updateVisibility(ActivityClientRecord r, boolean show) {
        View v = r.activity.mDecor;
        if (v == null) {
            return;
        }
        if (show) {
            if (!r.activity.mVisibleFromServer) {
                r.activity.mVisibleFromServer = true;
                this.mNumVisibleActivities++;
                if (r.activity.mVisibleFromClient) {
                    r.activity.makeVisible();
                }
            }
            if (r.newConfig != null) {
                performConfigurationChangedForActivity(r, r.newConfig, true);
                r.newConfig = null;
            }
        } else if (r.activity.mVisibleFromServer) {
            r.activity.mVisibleFromServer = false;
            this.mNumVisibleActivities--;
            v.setVisibility(4);
        }
    }

    private void handleStopActivity(IBinder token, boolean show, int configChanges, int seq) {
        ActivityClientRecord r = (ActivityClientRecord) this.mActivities.get(token);
        if (checkAndUpdateLifecycleSeq(seq, r, "stopActivity")) {
            Activity activity = r.activity;
            activity.mConfigChangeFlags |= configChanges;
            StopInfo info = new StopInfo();
            performStopActivityInner(r, info, show, true, "handleStopActivity");
            updateVisibility(r, show);
            if (!r.isPreHoneycomb()) {
                QueuedWork.waitToFinish();
            }
            info.activity = r;
            info.state = r.state;
            info.persistentState = r.persistentState;
            this.mH.post(info);
            this.mSomeActivitiesChanged = true;
            return;
        }
        Slog.e(TAG, "Stop activity error, ActivityClientRecord is removed, token= " + token);
    }

    private static boolean checkAndUpdateLifecycleSeq(int seq, ActivityClientRecord r, String action) {
        if (r == null) {
            return true;
        }
        if (seq < r.lastProcessedSeq) {
            return false;
        }
        r.lastProcessedSeq = seq;
        return true;
    }

    final void performRestartActivity(IBinder token) {
        ActivityClientRecord r = (ActivityClientRecord) this.mActivities.get(token);
        if (r.stopped) {
            r.activity.performRestart();
            r.stopped = false;
        }
    }

    private void handleWindowVisibility(IBinder token, boolean show) {
        ActivityClientRecord r = (ActivityClientRecord) this.mActivities.get(token);
        if (r == null) {
            Log.w(TAG, "handleWindowVisibility: no activity for token " + token);
            return;
        }
        if (!show && !r.stopped) {
            performStopActivityInner(r, null, show, false, "handleWindowVisibility");
        } else if (show && r.stopped) {
            unscheduleGcIdler();
            r.activity.performRestart();
            r.stopped = false;
        }
        if (r.activity.mDecor != null) {
            updateVisibility(r, show);
        }
        this.mSomeActivitiesChanged = true;
    }

    private void handleSleeping(IBinder token, boolean sleeping) {
        ActivityClientRecord r = (ActivityClientRecord) this.mActivities.get(token);
        if (r == null) {
            Log.w(TAG, "handleSleeping: no activity for token " + token);
            return;
        }
        if (sleeping) {
            if (!(r.stopped || r.isPreHoneycomb())) {
                try {
                    r.activity.performStop(false);
                } catch (Exception e) {
                    if (!this.mInstrumentation.onException(r.activity, e)) {
                        throw new RuntimeException("Unable to stop activity " + r.intent.getComponent().toShortString() + ": " + e.toString(), e);
                    }
                }
                r.stopped = true;
                EventLog.writeEvent(LOG_AM_ON_STOP_CALLED, new Object[]{Integer.valueOf(UserHandle.myUserId()), r.activity.getComponentName().getClassName(), "sleeping"});
            }
            if (!r.isPreHoneycomb()) {
                QueuedWork.waitToFinish();
            }
            try {
                ActivityManagerNative.getDefault().activitySlept(r.token);
            } catch (RemoteException ex) {
                throw ex.rethrowFromSystemServer();
            }
        } else if (r.stopped && r.activity.mVisibleFromServer) {
            r.activity.performRestart();
            r.stopped = false;
        }
    }

    private void handleSetCoreSettings(Bundle coreSettings) {
        synchronized (this.mResourcesManager) {
            this.mCoreSettings = coreSettings;
        }
        onCoreSettingsChange();
    }

    private void onCoreSettingsChange() {
        boolean debugViewAttributes = this.mCoreSettings.getInt(Global.DEBUG_VIEW_ATTRIBUTES, 0) != 0;
        if (debugViewAttributes != View.mDebugViewAttributes) {
            View.mDebugViewAttributes = debugViewAttributes;
            for (Entry<IBinder, ActivityClientRecord> entry : this.mActivities.entrySet()) {
                requestRelaunchActivity((IBinder) entry.getKey(), null, null, 0, false, null, null, false, false);
            }
        }
    }

    private void handleUpdatePackageCompatibilityInfo(UpdateCompatibilityData data) {
        LoadedApk apk = peekPackageInfo(data.pkg, false);
        if (apk != null) {
            apk.setCompatibilityInfo(data.info);
        }
        apk = peekPackageInfo(data.pkg, true);
        if (apk != null) {
            apk.setCompatibilityInfo(data.info);
        }
        handleConfigurationChanged(this.mConfiguration, data.info);
        WindowManagerGlobal.getInstance().reportNewConfiguration(this.mConfiguration);
    }

    private void deliverResults(ActivityClientRecord r, List<ResultInfo> results) {
        int N = results.size();
        for (int i = 0; i < N; i++) {
            ResultInfo ri = (ResultInfo) results.get(i);
            try {
                if (ri.mData != null) {
                    ri.mData.setExtrasClassLoader(r.activity.getClassLoader());
                    ri.mData.prepareToEnterProcess();
                }
                r.activity.dispatchActivityResult(ri.mResultWho, ri.mRequestCode, ri.mResultCode, ri.mData);
            } catch (Exception e) {
                if (!this.mInstrumentation.onException(r.activity, e)) {
                    throw new RuntimeException("Failure delivering result " + ri + " to activity " + r.intent.getComponent().toShortString() + ": " + e.toString(), e);
                }
            }
        }
    }

    private void handleSendResult(ResultData res) {
        ActivityClientRecord r = (ActivityClientRecord) this.mActivities.get(res.token);
        if (r != null) {
            boolean resumed = !r.paused;
            if (!r.activity.mFinished && r.activity.mDecor != null && r.hideForNow && resumed) {
                updateVisibility(r, true);
            }
            if (resumed) {
                try {
                    r.activity.mCalled = false;
                    r.activity.mTemporaryPause = true;
                    this.mInstrumentation.callActivityOnPause(r.activity);
                    if (!r.activity.mCalled) {
                        throw new SuperNotCalledException("Activity " + r.intent.getComponent().toShortString() + " did not call through to super.onPause()");
                    }
                } catch (SuperNotCalledException e) {
                    throw e;
                } catch (Exception e2) {
                    if (!this.mInstrumentation.onException(r.activity, e2)) {
                        throw new RuntimeException("Unable to pause activity " + r.intent.getComponent().toShortString() + ": " + e2.toString(), e2);
                    }
                }
            }
            deliverResults(r, res.results);
            if (resumed) {
                r.activity.performResume();
                r.activity.mTemporaryPause = false;
            }
        }
    }

    public final ActivityClientRecord performDestroyActivity(IBinder token, boolean finishing) {
        return performDestroyActivity(token, finishing, 0, false);
    }

    private ActivityClientRecord performDestroyActivity(IBinder token, boolean finishing, int configChanges, boolean getNonConfigInstance) {
        ActivityClientRecord r = (ActivityClientRecord) this.mActivities.get(token);
        Class cls = null;
        if (r != null) {
            cls = r.activity.getClass();
            Activity activity = r.activity;
            activity.mConfigChangeFlags |= configChanges;
            if (finishing) {
                r.activity.mFinished = true;
            }
            performPauseActivityIfNeeded(r, "destroy");
            if (!r.stopped) {
                try {
                    r.activity.performStop(r.mPreserveWindow);
                } catch (SuperNotCalledException e) {
                    throw e;
                } catch (Exception e2) {
                    if (!this.mInstrumentation.onException(r.activity, e2)) {
                        throw new RuntimeException("Unable to stop activity " + safeToComponentShortString(r.intent) + ": " + e2.toString(), e2);
                    }
                }
                r.stopped = true;
                EventLog.writeEvent(LOG_AM_ON_STOP_CALLED, new Object[]{Integer.valueOf(UserHandle.myUserId()), r.activity.getComponentName().getClassName(), "destroy"});
            }
            if (getNonConfigInstance) {
                try {
                    r.lastNonConfigurationInstances = r.activity.retainNonConfigurationInstances();
                } catch (Exception e22) {
                    if (!this.mInstrumentation.onException(r.activity, e22)) {
                        throw new RuntimeException("Unable to retain activity " + r.intent.getComponent().toShortString() + ": " + e22.toString(), e22);
                    }
                }
            }
            try {
                r.activity.mCalled = false;
                this.mInstrumentation.callActivityOnDestroy(r.activity);
                if (!r.activity.mCalled) {
                    throw new SuperNotCalledException("Activity " + safeToComponentShortString(r.intent) + " did not call through to super.onDestroy()");
                } else if (r.window != null) {
                    r.window.closeAllPanels();
                }
            } catch (SuperNotCalledException e3) {
                throw e3;
            } catch (Exception e222) {
                if (!this.mInstrumentation.onException(r.activity, e222)) {
                    throw new RuntimeException("Unable to destroy activity " + safeToComponentShortString(r.intent) + ": " + e222.toString(), e222);
                }
            }
        }
        this.mActivities.remove(token);
        Slog.d(TAG, "Remove activity client record, r= " + r + " token= " + token);
        StrictMode.decrementExpectedActivityCount(cls);
        return r;
    }

    private static String safeToComponentShortString(Intent intent) {
        ComponentName component = intent.getComponent();
        return component == null ? "[Unknown]" : component.toShortString();
    }

    private void handleDestroyActivity(IBinder token, boolean finishing, int configChanges, boolean getNonConfigInstance) {
        ActivityClientRecord r = performDestroyActivity(token, finishing, configChanges, getNonConfigInstance);
        if (r != null) {
            cleanUpPendingRemoveWindows(r, finishing);
            WindowManager wm = r.activity.getWindowManager();
            View v = r.activity.mDecor;
            if (v != null) {
                if (r.activity.mVisibleFromServer) {
                    this.mNumVisibleActivities--;
                }
                IBinder wtoken = v.getWindowToken();
                if (r.activity.mWindowAdded) {
                    if (r.mPreserveWindow) {
                        r.mPendingRemoveWindow = r.window;
                        r.mPendingRemoveWindowManager = wm;
                        r.window.clearContentView();
                    } else {
                        wm.removeViewImmediate(v);
                    }
                }
                if (wtoken != null && r.mPendingRemoveWindow == null) {
                    WindowManagerGlobal.getInstance().closeAll(wtoken, r.activity.getClass().getName(), "Activity");
                } else if (r.mPendingRemoveWindow != null) {
                    WindowManagerGlobal.getInstance().closeAllExceptView(token, v, r.activity.getClass().getName(), "Activity");
                }
                r.activity.mDecor = null;
            }
            if (r.mPendingRemoveWindow == null) {
                WindowManagerGlobal.getInstance().closeAll(token, r.activity.getClass().getName(), "Activity");
            }
            Context c = r.activity.getBaseContext();
            if (c instanceof ContextImpl) {
                ((ContextImpl) c).scheduleFinalCleanup(r.activity.getClass().getName(), "Activity");
            }
        }
        if (finishing) {
            try {
                ActivityManagerNative.getDefault().activityDestroyed(token);
            } catch (RemoteException ex) {
                throw ex.rethrowFromSystemServer();
            }
        }
        this.mSomeActivitiesChanged = true;
    }

    public final void requestRelaunchActivity(IBinder token, List<ResultInfo> pendingResults, List<ReferrerIntent> pendingNewIntents, int configChanges, boolean notResumed, Configuration config, Configuration overrideConfig, boolean fromServer, boolean preserveWindow) {
        synchronized (this.mResourcesManager) {
            ActivityClientRecord target;
            ActivityClientRecord target2;
            int i = 0;
            while (i < this.mRelaunchingActivities.size()) {
                ActivityClientRecord existing;
                try {
                    ActivityClientRecord r = (ActivityClientRecord) this.mRelaunchingActivities.get(i);
                    if (r.token == token) {
                        target = r;
                        if (pendingResults != null) {
                            if (r.pendingResults != null) {
                                r.pendingResults.addAll(pendingResults);
                            } else {
                                r.pendingResults = pendingResults;
                            }
                        }
                        if (pendingNewIntents != null) {
                            if (r.pendingIntents != null) {
                                r.pendingIntents.addAll(pendingNewIntents);
                            } else {
                                r.pendingIntents = pendingNewIntents;
                            }
                        }
                        if (!r.onlyLocalRequest && fromServer) {
                            ActivityManagerNative.getDefault().activityRelaunched(token);
                        }
                        target2 = target;
                        if (target2 != null) {
                            try {
                                target = new ActivityClientRecord();
                                target.token = token;
                                target.pendingResults = pendingResults;
                                target.pendingIntents = pendingNewIntents;
                                target.mPreserveWindow = preserveWindow;
                                if (!fromServer) {
                                    existing = (ActivityClientRecord) this.mActivities.get(token);
                                    if (existing != null) {
                                        target.startsNotResumed = existing.paused;
                                        target.overrideConfig = existing.overrideConfig;
                                    }
                                    target.onlyLocalRequest = true;
                                }
                                this.mRelaunchingActivities.add(target);
                                sendMessage(126, target);
                            } catch (Throwable th) {
                                Throwable th2 = th;
                                throw th2;
                            }
                        }
                        target = target2;
                        if (fromServer) {
                            target.startsNotResumed = notResumed;
                            target.onlyLocalRequest = false;
                        }
                        if (config != null) {
                            target.createdConfig = config;
                        }
                        if (overrideConfig != null) {
                            target.overrideConfig = overrideConfig;
                        }
                        target.pendingConfigChanges |= configChanges;
                        target.relaunchSeq = getLifecycleSeq();
                    }
                    i++;
                } catch (RemoteException e) {
                    throw e.rethrowFromSystemServer();
                } catch (Throwable th3) {
                    th2 = th3;
                }
            }
            target2 = null;
            if (target2 != null) {
                target = target2;
            } else {
                target = new ActivityClientRecord();
                target.token = token;
                target.pendingResults = pendingResults;
                target.pendingIntents = pendingNewIntents;
                target.mPreserveWindow = preserveWindow;
                if (fromServer) {
                    existing = (ActivityClientRecord) this.mActivities.get(token);
                    if (existing != null) {
                        target.startsNotResumed = existing.paused;
                        target.overrideConfig = existing.overrideConfig;
                    }
                    target.onlyLocalRequest = true;
                }
                this.mRelaunchingActivities.add(target);
                sendMessage(126, target);
            }
            if (fromServer) {
                target.startsNotResumed = notResumed;
                target.onlyLocalRequest = false;
            }
            if (config != null) {
                target.createdConfig = config;
            }
            if (overrideConfig != null) {
                target.overrideConfig = overrideConfig;
            }
            target.pendingConfigChanges |= configChanges;
            target.relaunchSeq = getLifecycleSeq();
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void handleRelaunchActivity(ActivityClientRecord tmp) {
        unscheduleGcIdler();
        this.mSomeActivitiesChanged = true;
        Configuration configuration = null;
        int configChanges = 0;
        synchronized (this.mResourcesManager) {
            int N = this.mRelaunchingActivities.size();
            IBinder token = tmp.token;
            tmp = null;
            int i = 0;
            while (i < N) {
                ActivityClientRecord r = (ActivityClientRecord) this.mRelaunchingActivities.get(i);
                if (r.token == token) {
                    tmp = r;
                    configChanges |= r.pendingConfigChanges;
                    this.mRelaunchingActivities.remove(i);
                    i--;
                    N--;
                }
                i++;
            }
            if (tmp == null) {
            } else if (this.mPendingConfiguration != null) {
                configuration = this.mPendingConfiguration;
                this.mPendingConfiguration = null;
            }
        }
    }

    private void callCallActivityOnSaveInstanceState(ActivityClientRecord r) {
        r.state = new Bundle();
        r.state.setAllowFds(false);
        if (r.isPersistable()) {
            r.persistentState = new PersistableBundle();
            this.mInstrumentation.callActivityOnSaveInstanceState(r.activity, r.state, r.persistentState);
            return;
        }
        this.mInstrumentation.callActivityOnSaveInstanceState(r.activity, r.state);
    }

    ArrayList<ComponentCallbacks2> collectComponentCallbacks(boolean allActivities, Configuration newConfig) {
        ArrayList<ComponentCallbacks2> callbacks = new ArrayList();
        synchronized (this.mResourcesManager) {
            int i;
            int NAPP = this.mAllApplications.size();
            for (i = 0; i < NAPP; i++) {
                callbacks.add((ComponentCallbacks2) this.mAllApplications.get(i));
            }
            int NACT = this.mActivities.size();
            for (i = 0; i < NACT; i++) {
                ActivityClientRecord ar = (ActivityClientRecord) this.mActivities.valueAt(i);
                Activity a = ar.activity;
                if (a != null) {
                    Configuration thisConfig = applyConfigCompatMainThread(this.mCurDefaultDisplayDpi, newConfig, ar.packageInfo.getCompatibilityInfo());
                    if (!ar.activity.mFinished && (allActivities || !ar.paused)) {
                        callbacks.add(a);
                    } else if (thisConfig != null) {
                        ar.newConfig = thisConfig;
                    }
                }
            }
            int NSVC = this.mServices.size();
            for (i = 0; i < NSVC; i++) {
                callbacks.add((ComponentCallbacks2) this.mServices.valueAt(i));
            }
        }
        synchronized (this.mProviderMap) {
            int NPRV = this.mLocalProviders.size();
            for (i = 0; i < NPRV; i++) {
                callbacks.add(((ProviderClientRecord) this.mLocalProviders.valueAt(i)).mLocalProvider);
            }
        }
        return callbacks;
    }

    private void performConfigurationChangedForActivity(ActivityClientRecord r, Configuration newBaseConfig, boolean reportToActivity) {
        r.tmpConfig.setTo(newBaseConfig);
        if (r.overrideConfig != null) {
            r.tmpConfig.updateFrom(r.overrideConfig);
        }
        performConfigurationChanged(r.activity, r.token, r.tmpConfig, r.overrideConfig, reportToActivity);
        freeTextLayoutCachesIfNeeded(r.activity.mCurrentConfig.diff(r.tmpConfig));
    }

    private static Configuration createNewConfigAndUpdateIfNotNull(Configuration base, Configuration override) {
        if (override == null) {
            return base;
        }
        Configuration newConfig = new Configuration(base);
        newConfig.updateFrom(override);
        return newConfig;
    }

    private void performConfigurationChanged(ComponentCallbacks2 cb, IBinder activityToken, Configuration newConfig, Configuration amOverrideConfig, boolean reportToActivity) {
        Activity activity = null;
        if (cb instanceof Activity) {
            activity = (Activity) cb;
        }
        if (activity != null) {
            activity.mCalled = false;
        }
        boolean shouldChangeConfig = false;
        if (activity == null || activity.mCurrentConfig == null) {
            shouldChangeConfig = true;
        } else {
            int diff = activity.mCurrentConfig.diff(newConfig);
            if (!(diff == 0 || (activity.mActivityInfo.getRealConfigChanged() & diff) == 0)) {
                shouldChangeConfig = true;
            }
        }
        if (shouldChangeConfig) {
            Configuration contextThemeWrapperOverrideConfig = null;
            if (cb instanceof ContextThemeWrapper) {
                contextThemeWrapperOverrideConfig = ((ContextThemeWrapper) cb).getOverrideConfiguration();
            }
            if (activityToken != null) {
                this.mResourcesManager.updateResourcesForActivity(activityToken, createNewConfigAndUpdateIfNotNull(amOverrideConfig, contextThemeWrapperOverrideConfig));
            }
            if (reportToActivity) {
                cb.onConfigurationChanged(createNewConfigAndUpdateIfNotNull(newConfig, contextThemeWrapperOverrideConfig));
            }
            if (activity == null) {
                return;
            }
            if (!reportToActivity || activity.mCalled) {
                activity.mConfigChangeFlags = 0;
                activity.mCurrentConfig = new Configuration(newConfig);
                return;
            }
            throw new SuperNotCalledException("Activity " + activity.getLocalClassName() + " did not call through to super.onConfigurationChanged()");
        }
    }

    public final void applyConfigurationToResources(Configuration config) {
        synchronized (this.mResourcesManager) {
            this.mResourcesManager.applyConfigurationToResourcesLocked(config, null);
        }
    }

    final Configuration applyCompatConfiguration(int displayDensity) {
        Configuration config = this.mConfiguration;
        if (this.mCompatConfiguration == null) {
            this.mCompatConfiguration = new Configuration();
        }
        this.mCompatConfiguration.setTo(this.mConfiguration);
        if (this.mResourcesManager.applyCompatConfigurationLocked(displayDensity, this.mCompatConfiguration)) {
            return this.mCompatConfiguration;
        }
        return config;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    final void handleConfigurationChanged(Configuration config, CompatibilityInfo compat) {
        ContextImpl contextImpl = getSystemContext();
        synchronized (this.mResourcesManager) {
            if (this.mPendingConfiguration != null) {
                if (!this.mPendingConfiguration.isOtherSeqNewer(config)) {
                    config = this.mPendingConfiguration;
                    this.mCurDefaultDisplayDpi = config.densityDpi;
                    updateDefaultDensity();
                }
                this.mPendingConfiguration = null;
            }
            if (config == null) {
                return;
            }
            this.mResourcesManager.applyConfigurationToResourcesLocked(config, compat);
            updateLocaleListFromAppContext(this.mInitialApplication.getApplicationContext(), this.mResourcesManager.getConfiguration().getLocales());
            if (this.mConfiguration == null) {
                this.mConfiguration = new Configuration();
            }
            if (this.mConfiguration.isOtherSeqNewer(config) || compat != null) {
                int configDiff = this.mConfiguration.updateFrom(config);
                config = applyCompatConfiguration(this.mCurDefaultDisplayDpi);
                Theme systemTheme = contextImpl.getTheme();
                if ((systemTheme.getChangingConfigurations() & configDiff) != 0) {
                    systemTheme.rebase();
                }
            }
        }
    }

    static void freeTextLayoutCachesIfNeeded(int configDiff) {
        boolean hasLocaleConfigChange = false;
        if (configDiff != 0) {
            if ((configDiff & 4) != 0) {
                hasLocaleConfigChange = true;
            }
            if (hasLocaleConfigChange) {
                Canvas.freeTextLayoutCaches();
            }
        }
    }

    final void handleActivityConfigurationChanged(ActivityConfigChangeData data, boolean reportToActivity) {
        ActivityClientRecord r = (ActivityClientRecord) this.mActivities.get(data.activityToken);
        if (r != null && r.activity != null) {
            r.overrideConfig = data.overrideConfig;
            performConfigurationChangedForActivity(r, this.mCompatConfiguration, reportToActivity);
            this.mSomeActivitiesChanged = true;
            handleActivityConfigChangedWithRog(r);
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    final void handleProfilerControl(boolean start, ProfilerInfo profilerInfo, int profileType) {
        if (start) {
            try {
                this.mProfiler.setProfiler(profilerInfo);
                this.mProfiler.startProfiling();
                try {
                    profilerInfo.profileFd.close();
                } catch (IOException e) {
                    Slog.w(TAG, "Failure closing profile fd", e);
                }
            } catch (RuntimeException e2) {
                Slog.w(TAG, "Profiling failed on path " + profilerInfo.profileFile + " -- can the process access this path?");
            } catch (Throwable th) {
                try {
                    profilerInfo.profileFd.close();
                } catch (IOException e3) {
                    Slog.w(TAG, "Failure closing profile fd", e3);
                }
            }
        } else {
            this.mProfiler.stopProfiling();
        }
    }

    public void stopProfiling() {
        this.mProfiler.stopProfiling();
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    static final void handleDumpHeap(boolean managed, DumpHeapData dhd) {
        if (managed) {
            try {
                Debug.dumpHprofData(dhd.path, dhd.fd.getFileDescriptor());
                try {
                    dhd.fd.close();
                } catch (IOException e) {
                    Slog.w(TAG, "Failure closing profile fd", e);
                }
            } catch (IOException e2) {
                Slog.w(TAG, "Managed heap dump failed on path " + dhd.path + " -- can the process access this path?");
            } catch (Throwable th) {
                try {
                    dhd.fd.close();
                } catch (IOException e3) {
                    Slog.w(TAG, "Failure closing profile fd", e3);
                }
            }
        } else {
            Debug.dumpNativeHeap(dhd.fd.getFileDescriptor());
        }
        try {
            ActivityManagerNative.getDefault().dumpHeapFinished(dhd.path);
        } catch (RemoteException e4) {
            throw e4.rethrowFromSystemServer();
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    final void handleDispatchPackageBroadcast(int cmd, String[] packages) {
        boolean hasPkgInfo = false;
        ResourcesManager resourcesManager;
        int i;
        WeakReference<LoadedApk> ref;
        switch (cmd) {
            case 0:
            case 2:
                boolean killApp = cmd == 0;
                if (packages != null) {
                    resourcesManager = this.mResourcesManager;
                    synchronized (resourcesManager) {
                        for (i = packages.length - 1; i >= 0; i--) {
                            if (!hasPkgInfo) {
                                ref = (WeakReference) this.mPackages.get(packages[i]);
                                if (ref == null || ref.get() == null) {
                                    ref = (WeakReference) this.mResourcePackages.get(packages[i]);
                                    if (!(ref == null || ref.get() == null)) {
                                        hasPkgInfo = true;
                                    }
                                } else {
                                    hasPkgInfo = true;
                                }
                            }
                            if (killApp) {
                                this.mPackages.remove(packages[i]);
                                this.mResourcePackages.remove(packages[i]);
                            }
                        }
                    }
                }
                break;
            case 3:
                if (packages != null) {
                    resourcesManager = this.mResourcesManager;
                    synchronized (resourcesManager) {
                        for (i = packages.length - 1; i >= 0; i--) {
                            ref = (WeakReference) this.mPackages.get(packages[i]);
                            LoadedApk loadedApk = ref != null ? (LoadedApk) ref.get() : null;
                            if (loadedApk != null) {
                                hasPkgInfo = true;
                            } else {
                                ref = (WeakReference) this.mResourcePackages.get(packages[i]);
                                loadedApk = ref != null ? (LoadedApk) ref.get() : null;
                                if (loadedApk != null) {
                                    hasPkgInfo = true;
                                }
                            }
                            if (loadedApk != null) {
                                try {
                                    String packageName = packages[i];
                                    ApplicationInfo aInfo = sPackageManager.getApplicationInfo(packageName, 0, UserHandle.myUserId());
                                    if (this.mActivities.size() > 0) {
                                        for (ActivityClientRecord ar : this.mActivities.values()) {
                                            if (ar.activityInfo.applicationInfo.packageName.equals(packageName)) {
                                                ar.activityInfo.applicationInfo = aInfo;
                                                ar.packageInfo = loadedApk;
                                            }
                                        }
                                    }
                                    loadedApk.updateApplicationInfo(aInfo, sPackageManager.getPreviousCodePaths(packageName));
                                } catch (RemoteException e) {
                                }
                            }
                        }
                    }
                }
                break;
        }
        ApplicationPackageManager.handlePackageBroadcast(cmd, packages, hasPkgInfo);
    }

    final void handleLowMemory() {
        ArrayList<ComponentCallbacks2> callbacks = collectComponentCallbacks(true, null);
        int N = callbacks.size();
        for (int i = 0; i < N; i++) {
            ((ComponentCallbacks2) callbacks.get(i)).onLowMemory();
        }
        if (Process.myUid() != 1000) {
            EventLog.writeEvent(SQLITE_MEM_RELEASED_EVENT_LOG_TAG, SQLiteDatabase.releaseMemory());
        }
        Canvas.freeCaches();
        Canvas.freeTextLayoutCaches();
        BinderInternal.forceGc("mem");
    }

    final void handleTrimMemory(int level) {
        ArrayList<ComponentCallbacks2> callbacks = collectComponentCallbacks(true, null);
        int N = callbacks.size();
        for (int i = 0; i < N; i++) {
            ((ComponentCallbacks2) callbacks.get(i)).onTrimMemory(level);
        }
        WindowManagerGlobal.getInstance().trimMemory(level);
    }

    private void setupGraphicsSupport(LoadedApk info, File cacheDir) {
        if (!Process.isIsolated()) {
            Trace.traceBegin(64, "setupGraphicsSupport");
            try {
                if (getPackageManager().getPackagesForUid(Process.myUid()) != null) {
                    ThreadedRenderer.setupDiskCache(cacheDir);
                    RenderScriptCacheDir.setupDiskCache(cacheDir);
                }
                Trace.traceEnd(64);
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            } catch (Throwable th) {
                Trace.traceEnd(64);
            }
        }
    }

    private void updateDefaultDensity() {
        int densityDpi = this.mCurDefaultDisplayDpi;
        if (!this.mDensityCompatMode && densityDpi != 0 && densityDpi != DisplayMetrics.DENSITY_DEVICE) {
            DisplayMetrics.DENSITY_DEVICE = densityDpi;
            Bitmap.setDefaultDensity(densityDpi);
        }
    }

    private String getInstrumentationLibrary(ApplicationInfo appInfo, InstrumentationInfo insInfo) {
        if (!(appInfo.primaryCpuAbi == null || appInfo.secondaryCpuAbi == null)) {
            String secondaryIsa = VMRuntime.getInstructionSet(appInfo.secondaryCpuAbi);
            String secondaryDexCodeIsa = SystemProperties.get("ro.dalvik.vm.isa." + secondaryIsa);
            if (!secondaryDexCodeIsa.isEmpty()) {
                secondaryIsa = secondaryDexCodeIsa;
            }
            if (VMRuntime.getRuntime().vmInstructionSet().equals(secondaryIsa)) {
                return insInfo.secondaryNativeLibraryDir;
            }
        }
        return insInfo.nativeLibraryDir;
    }

    private void updateLocaleListFromAppContext(Context context, LocaleList newLocaleList) {
        Locale bestLocale = context.getResources().getConfiguration().getLocales().get(0);
        int newLocaleListSize = newLocaleList.size();
        for (int i = 0; i < newLocaleListSize; i++) {
            if (bestLocale.equals(newLocaleList.get(i))) {
                LocaleList.setDefault(newLocaleList, i);
                return;
            }
        }
        LocaleList.setDefault(new LocaleList(bestLocale, newLocaleList));
    }

    private void handleBindApplication(AppBindData data) {
        if (Jlog.isPerfTest()) {
            Jlog.i(2038, "pid=" + Process.myPid());
        }
        VMRuntime.registerSensitiveThread();
        if (data.trackAllocation) {
            DdmVmInternal.enableRecentAllocations(true);
        }
        Process.setStartTimes(SystemClock.elapsedRealtime(), SystemClock.uptimeMillis());
        if (HISI_PERF_OPT) {
            new Thread(new GraphicBufferCreate()).start();
        }
        this.mBoundApplication = data;
        this.mConfiguration = new Configuration(data.config);
        this.mCompatConfiguration = new Configuration(data.config);
        this.mProfiler = new Profiler();
        if (data.initProfilerInfo != null) {
            this.mProfiler.profileFile = data.initProfilerInfo.profileFile;
            this.mProfiler.profileFd = data.initProfilerInfo.profileFd;
            this.mProfiler.samplingInterval = data.initProfilerInfo.samplingInterval;
            this.mProfiler.autoStopProfiler = data.initProfilerInfo.autoStopProfiler;
        }
        Process.setArgV0(data.processName);
        DdmHandleAppName.setAppName(data.processName, UserHandle.myUserId());
        if (data.persistent && !ActivityManager.isHighEndGfx()) {
            ThreadedRenderer.disable(false);
        }
        if (this.mProfiler.profileFd != null) {
            this.mProfiler.startProfiling();
        }
        if (data.appInfo.targetSdkVersion <= 12) {
            AsyncTask.setDefaultExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
        Message.updateCheckRecycle(data.appInfo.targetSdkVersion);
        TimeZone.setDefault(null);
        LocaleList.setDefault(data.config.getLocales());
        synchronized (this.mResourcesManager) {
            this.mResourcesManager.applyConfigurationToResourcesLocked(data.config, data.compatInfo);
            this.mCurDefaultDisplayDpi = data.config.densityDpi;
            applyCompatConfiguration(this.mCurDefaultDisplayDpi);
        }
        data.info = getPackageInfoNoCheck(data.appInfo, data.compatInfo);
        if ((data.appInfo.flags & 8192) == 0) {
            this.mDensityCompatMode = true;
            Bitmap.setDefaultDensity(160);
        }
        updateDefaultDensity();
        DateFormat.set24HourTimePref("24".equals(this.mCoreSettings.getString(System.TIME_12_24)));
        View.mDebugViewAttributes = this.mCoreSettings.getInt(Global.DEBUG_VIEW_ATTRIBUTES, 0) != 0;
        if ((data.appInfo.flags & 129) != 0) {
            StrictMode.conditionallyEnableDebugLogging();
        }
        if (data.appInfo.targetSdkVersion >= 11) {
            StrictMode.enableDeathOnNetwork();
        }
        if (data.appInfo.targetSdkVersion >= 24) {
            StrictMode.enableDeathOnFileUriExposure();
        }
        NetworkSecurityPolicy.getInstance().setCleartextTrafficPermitted((data.appInfo.flags & 134217728) != 0);
        if (data.debugMode != 0) {
            Debug.changeDebugPort(8100);
            if (data.debugMode == 2) {
                Slog.w(TAG, "Application " + data.info.getPackageName() + " is waiting for the debugger on port 8100...");
                IActivityManager mgr = ActivityManagerNative.getDefault();
                try {
                    mgr.showWaitingForDebugger(this.mAppThread, true);
                    Debug.waitForDebugger();
                    try {
                        mgr.showWaitingForDebugger(this.mAppThread, false);
                    } catch (RemoteException ex) {
                        throw ex.rethrowFromSystemServer();
                    }
                } catch (RemoteException ex2) {
                    throw ex2.rethrowFromSystemServer();
                }
            }
            Slog.w(TAG, "Application " + data.info.getPackageName() + " can be debugged on port 8100...");
        }
        boolean isAppDebuggable = (data.appInfo.flags & 2) != 0;
        Trace.setAppTracingAllowed(isAppDebuggable);
        if (isAppDebuggable && data.enableBinderTracking) {
            Binder.enableTracing();
        }
        Trace.traceBegin(64, "Setup proxies");
        IBinder b = ServiceManager.getService(Context.CONNECTIVITY_SERVICE);
        if (b != null) {
            try {
                Proxy.setHttpProxySystemProperty(IConnectivityManager.Stub.asInterface(b).getProxyForNetwork(null));
            } catch (RemoteException e) {
                Trace.traceEnd(64);
                throw e.rethrowFromSystemServer();
            }
        }
        Trace.traceEnd(64);
        if (data.instrumentationName != null) {
            try {
                InstrumentationInfo ii = new ApplicationPackageManager(null, getPackageManager()).getInstrumentationInfo(data.instrumentationName, 0);
                this.mInstrumentationPackageName = ii.packageName;
                this.mInstrumentationAppDir = ii.sourceDir;
                this.mInstrumentationSplitAppDirs = ii.splitSourceDirs;
                this.mInstrumentationLibDir = getInstrumentationLibrary(data.appInfo, ii);
                this.mInstrumentedAppDir = data.info.getAppDir();
                this.mInstrumentedSplitAppDirs = data.info.getSplitAppDirs();
                this.mInstrumentedLibDir = data.info.getLibDir();
            } catch (NameNotFoundException e2) {
                throw new RuntimeException("Unable to find instrumentation info for: " + data.instrumentationName);
            }
        }
        ii = null;
        Context appContext = ContextImpl.createAppContext(this, data.info);
        initRogInfoWhenLaunch(data);
        updateLocaleListFromAppContext(appContext, this.mResourcesManager.getConfiguration().getLocales());
        if (!(Process.isIsolated() || ZenModeConfig.SYSTEM_AUTHORITY.equals(appContext.getPackageName()))) {
            File cacheDir = appContext.getCacheDir();
            if (cacheDir != null) {
                System.setProperty("java.io.tmpdir", cacheDir.getAbsolutePath());
            } else {
                Log.v(TAG, "Unable to initialize \"java.io.tmpdir\" property due to missing cache directory");
            }
            File codeCacheDir = appContext.createDeviceProtectedStorageContext().getCodeCacheDir();
            if (codeCacheDir != null) {
                setupGraphicsSupport(data.info, codeCacheDir);
            } else {
                Log.e(TAG, "Unable to setupGraphicsSupport due to missing code-cache directory");
            }
        }
        Trace.traceBegin(64, "NetworkSecurityConfigProvider.install");
        NetworkSecurityConfigProvider.install(appContext);
        Trace.traceEnd(64);
        if (ii != null) {
            ApplicationInfo instrApp = new ApplicationInfo();
            ii.copyTo(instrApp);
            instrApp.initForUser(UserHandle.myUserId());
            ContextImpl instrContext = ContextImpl.createAppContext(this, getPackageInfo(instrApp, data.compatInfo, appContext.getClassLoader(), false, true, false));
            try {
                this.mInstrumentation = (Instrumentation) instrContext.getClassLoader().loadClass(data.instrumentationName.getClassName()).newInstance();
                this.mInstrumentation.init(this, instrContext, appContext, new ComponentName(ii.packageName, ii.name), data.instrumentationWatcher, data.instrumentationUiAutomationConnection);
                if (!(this.mProfiler.profileFile == null || ii.handleProfiling || this.mProfiler.profileFd != null)) {
                    this.mProfiler.handlingProfiling = true;
                    File file = new File(this.mProfiler.profileFile);
                    file.getParentFile().mkdirs();
                    Debug.startMethodTracing(file.toString(), 8388608);
                }
            } catch (Throwable e3) {
                throw new RuntimeException("Unable to instantiate instrumentation " + data.instrumentationName + ": " + e3.toString(), e3);
            }
        }
        this.mInstrumentation = new Instrumentation();
        if ((data.appInfo.flags & 1048576) != 0) {
            VMRuntime.getRuntime().clearGrowthLimit();
        } else {
            VMRuntime.getRuntime().clampGrowthLimit();
        }
        ThreadPolicy savedPolicy = StrictMode.allowThreadDiskWrites();
        Application app;
        try {
            app = data.info.makeApplication(data.restrictedBackupMode, null);
            this.mInitialApplication = app;
            if (!(data.restrictedBackupMode || ArrayUtils.isEmpty(data.providers))) {
                installContentProviders(app, data.providers);
                this.mH.sendEmptyMessageDelayed(132, 10000);
            }
            this.mInstrumentation.onCreate(data.instrumentationArgs);
            this.mInstrumentation.callApplicationOnCreate(app);
            HwThemeManager.initForThemeFont(this.mConfiguration);
            HwThemeManager.setThemeFont();
            if (getApplicationThread() != null) {
                getApplicationThread().scheduleTrimMemory(80);
            }
            if (!mChangedFont) {
                mChangedFont = true;
                Typeface.setCurrentUserId(UserHandle.myUserId());
                Typeface.loadSystemFonts();
            }
        } catch (Throwable e32) {
            if (!this.mInstrumentation.onException(app, e32)) {
                throw new RuntimeException("Unable to create application " + app.getClass().getName() + ": " + e32.toString(), e32);
            }
        } catch (Throwable e322) {
            throw new RuntimeException("Exception thrown in onCreate() of " + data.instrumentationName + ": " + e322.toString(), e322);
        } catch (Throwable th) {
            StrictMode.setThreadPolicy(savedPolicy);
        }
        StrictMode.setThreadPolicy(savedPolicy);
        if (Jlog.isPerfTest()) {
            Jlog.i(2039, "pid=" + Process.myPid());
        }
    }

    final void finishInstrumentation(int resultCode, Bundle results) {
        IActivityManager am = ActivityManagerNative.getDefault();
        if (this.mProfiler.profileFile != null && this.mProfiler.handlingProfiling && this.mProfiler.profileFd == null) {
            Debug.stopMethodTracing();
        }
        try {
            am.finishInstrumentation(this.mAppThread, resultCode, results);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    private void installContentProviders(Context context, List<ProviderInfo> providers) {
        ArrayList<ContentProviderHolder> results = new ArrayList();
        for (ProviderInfo cpi : providers) {
            ContentProviderHolder cph = installProvider(context, null, cpi, false, true, true);
            if (cph != null) {
                cph.noReleaseNeeded = true;
                results.add(cph);
            }
        }
        try {
            ActivityManagerNative.getDefault().publishContentProviders(getApplicationThread(), results);
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
    }

    public final IContentProvider acquireProvider(Context c, String auth, int userId, boolean stable) {
        int i;
        ProviderKey key = new ProviderKey(auth, userId);
        IContentProvider provider = acquireExistingProvider(c, key, stable);
        if (provider != null) {
            return provider;
        }
        ContentProviderHolder holder;
        boolean first = false;
        synchronized (this.mAcquiringProviderMap) {
            AcquiringProviderRecord r = (AcquiringProviderRecord) this.mAcquiringProviderMap.get(key);
            if (r == null) {
                r = new AcquiringProviderRecord();
                this.mAcquiringProviderMap.put(key, r);
                first = true;
            } else {
                r.requests++;
                if (r.requests > 15) {
                    Slog.i(TAG, r.requests + " threads acquire provider " + auth + " at the same time!");
                }
            }
        }
        ArrayMap arrayMap;
        int i2;
        if (first) {
            try {
                holder = ActivityManagerNative.getDefault().getContentProvider(getApplicationThread(), auth, userId, stable);
                synchronized (r) {
                    r.acquiring = false;
                    r.notifyAll();
                }
                arrayMap = this.mAcquiringProviderMap;
                synchronized (arrayMap) {
                    i2 = r.requests - 1;
                    r.requests = i2;
                    if (i2 == 0) {
                        this.mAcquiringProviderMap.remove(key);
                    }
                }
            } catch (RemoteException ex) {
                throw ex.rethrowFromSystemServer();
            } catch (Throwable th) {
                synchronized (r) {
                    r.acquiring = false;
                    r.notifyAll();
                    synchronized (this.mAcquiringProviderMap) {
                        i = r.requests - 1;
                        r.requests = i;
                        if (i == 0) {
                            this.mAcquiringProviderMap.remove(key);
                        }
                    }
                }
            }
        } else {
            synchronized (r) {
                while (r.acquiring) {
                    try {
                        r.wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
            try {
                holder = ActivityManagerNative.getDefault().getContentProvider(getApplicationThread(), auth, userId, stable);
                arrayMap = this.mAcquiringProviderMap;
                synchronized (arrayMap) {
                    i2 = r.requests - 1;
                    r.requests = i2;
                    if (i2 == 0) {
                        this.mAcquiringProviderMap.remove(key);
                    }
                }
            } catch (RemoteException ex2) {
                throw ex2.rethrowFromSystemServer();
            } catch (Throwable th2) {
                synchronized (this.mAcquiringProviderMap) {
                    i = r.requests - 1;
                    r.requests = i;
                    if (i == 0) {
                        this.mAcquiringProviderMap.remove(key);
                    }
                }
            }
        }
        if (holder == null) {
            Slog.e(TAG, "Failed to find provider info for " + auth);
            return null;
        }
        return installProvider(c, holder, holder.info, true, holder.noReleaseNeeded, stable).provider;
    }

    private final void incProviderRefLocked(ProviderRefCount prc, boolean stable) {
        if (stable) {
            prc.stableCount++;
            if (prc.stableCount == 1) {
                int unstableDelta;
                if (prc.removePending) {
                    unstableDelta = -1;
                    prc.removePending = false;
                    this.mH.removeMessages(131, prc);
                } else {
                    unstableDelta = 0;
                }
                try {
                    ActivityManagerNative.getDefault().refContentProvider(prc.holder.connection, 1, unstableDelta);
                    return;
                } catch (RemoteException e) {
                    return;
                }
            }
            return;
        }
        prc.unstableCount++;
        if (prc.unstableCount != 1) {
            return;
        }
        if (prc.removePending) {
            prc.removePending = false;
            this.mH.removeMessages(131, prc);
            return;
        }
        try {
            ActivityManagerNative.getDefault().refContentProvider(prc.holder.connection, 0, 1);
        } catch (RemoteException e2) {
        }
    }

    public final IContentProvider acquireExistingProvider(Context c, String auth, int userId, boolean stable) {
        return acquireExistingProvider(c, new ProviderKey(auth, userId), stable);
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private IContentProvider acquireExistingProvider(Context c, ProviderKey key, boolean stable) {
        synchronized (this.mProviderMap) {
            ProviderClientRecord pr = (ProviderClientRecord) this.mProviderMap.get(key);
            if (pr == null) {
                return null;
            }
            IContentProvider provider = pr.mProvider;
            IBinder jBinder = provider.asBinder();
            if (jBinder.isBinderAlive()) {
                ProviderRefCount prc = (ProviderRefCount) this.mProviderRefCountMap.get(jBinder);
                if (prc != null) {
                    incProviderRefLocked(prc, stable);
                }
            } else {
                Log.i(TAG, "Acquiring provider " + key.authority + " for user " + key.userId + ": existing object's process dead");
                handleUnstableProviderDiedLocked(jBinder, true);
                return null;
            }
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public final boolean releaseProvider(IContentProvider provider, boolean stable) {
        int i = 0;
        if (provider == null) {
            return false;
        }
        IBinder jBinder = provider.asBinder();
        synchronized (this.mProviderMap) {
            ProviderRefCount prc = (ProviderRefCount) this.mProviderRefCountMap.get(jBinder);
            if (prc == null) {
                return false;
            }
            boolean lastRef = false;
            if (stable) {
                if (prc.stableCount == 0) {
                    return false;
                }
                prc.stableCount--;
                if (prc.stableCount == 0) {
                    lastRef = prc.unstableCount == 0;
                    try {
                        IActivityManager iActivityManager = ActivityManagerNative.getDefault();
                        IBinder iBinder = prc.holder.connection;
                        if (lastRef) {
                            i = 1;
                        }
                        iActivityManager.refContentProvider(iBinder, -1, i);
                    } catch (RemoteException e) {
                    }
                }
            } else if (prc.unstableCount == 0) {
                return false;
            } else {
                prc.unstableCount--;
                if (prc.unstableCount == 0) {
                    if (prc.stableCount == 0) {
                        lastRef = true;
                    } else {
                        lastRef = false;
                    }
                    if (!lastRef) {
                        try {
                            ActivityManagerNative.getDefault().refContentProvider(prc.holder.connection, 0, -1);
                        } catch (RemoteException e2) {
                        }
                    }
                }
            }
            if (lastRef) {
                if (prc.removePending) {
                    Slog.w(TAG, "Duplicate remove pending of provider " + prc.holder.info.name);
                } else {
                    prc.removePending = true;
                    this.mH.sendMessage(this.mH.obtainMessage(131, prc));
                }
            }
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    final void completeRemoveProvider(ProviderRefCount prc) {
        synchronized (this.mProviderMap) {
            if (prc.removePending) {
                prc.removePending = false;
                IBinder jBinder = prc.holder.provider.asBinder();
                if (((ProviderRefCount) this.mProviderRefCountMap.get(jBinder)) == prc) {
                    this.mProviderRefCountMap.remove(jBinder);
                }
                for (int i = this.mProviderMap.size() - 1; i >= 0; i--) {
                    if (((ProviderClientRecord) this.mProviderMap.valueAt(i)).mProvider.asBinder() == jBinder) {
                        this.mProviderMap.removeAt(i);
                    }
                }
                prc.removePending = false;
            }
        }
    }

    final void handleUnstableProviderDied(IBinder provider, boolean fromClient) {
        synchronized (this.mProviderMap) {
            handleUnstableProviderDiedLocked(provider, fromClient);
        }
    }

    final void handleUnstableProviderDiedLocked(IBinder provider, boolean fromClient) {
        ProviderRefCount prc = (ProviderRefCount) this.mProviderRefCountMap.get(provider);
        if (prc != null) {
            this.mProviderRefCountMap.remove(provider);
            for (int i = this.mProviderMap.size() - 1; i >= 0; i--) {
                ProviderClientRecord pr = (ProviderClientRecord) this.mProviderMap.valueAt(i);
                if (pr != null && pr.mProvider.asBinder() == provider) {
                    Slog.i(TAG, "Removing dead content provider:" + pr.mProvider.toString());
                    this.mProviderMap.removeAt(i);
                }
            }
            if (fromClient) {
                try {
                    ActivityManagerNative.getDefault().unstableProviderDied(prc.holder.connection);
                } catch (RemoteException e) {
                }
            }
        }
    }

    final void appNotRespondingViaProvider(IBinder provider) {
        synchronized (this.mProviderMap) {
            ProviderRefCount prc = (ProviderRefCount) this.mProviderRefCountMap.get(provider);
            if (prc != null) {
                try {
                    ActivityManagerNative.getDefault().appNotRespondingViaProvider(prc.holder.connection);
                } catch (RemoteException e) {
                    throw e.rethrowFromSystemServer();
                }
            }
        }
    }

    private ProviderClientRecord installProviderAuthoritiesLocked(IContentProvider provider, ContentProvider localProvider, ContentProviderHolder holder) {
        String[] auths = holder.info.authority.split(";");
        int userId = UserHandle.getUserId(holder.info.applicationInfo.uid);
        ProviderClientRecord pcr = new ProviderClientRecord(auths, provider, localProvider, holder);
        for (String auth : auths) {
            ProviderKey key = new ProviderKey(auth, userId);
            if (((ProviderClientRecord) this.mProviderMap.get(key)) != null) {
                Slog.w(TAG, "Content provider " + pcr.mHolder.info.name + " already published as " + auth);
            } else {
                this.mProviderMap.put(key, pcr);
            }
        }
        return pcr;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private ContentProviderHolder installProvider(Context context, ContentProviderHolder holder, ProviderInfo info, boolean noisy, boolean noReleaseNeeded, boolean stable) {
        IContentProvider provider;
        Throwable th;
        ContentProvider contentProvider = null;
        if (holder == null || holder.provider == null) {
            if (noisy) {
                Slog.d(TAG, "Loading provider " + info.authority + ": " + info.name);
            }
            Context c = null;
            ApplicationInfo ai = info.applicationInfo;
            if (context.getPackageName().equals(ai.packageName)) {
                c = context;
            } else if (this.mInitialApplication == null || !this.mInitialApplication.getPackageName().equals(ai.packageName)) {
                try {
                    c = context.createPackageContext(ai.packageName, 1);
                } catch (NameNotFoundException e) {
                }
            } else {
                c = this.mInitialApplication;
            }
            c = getContextForClassLoader(ai, context, c);
            if (c == null) {
                Slog.w(TAG, "Unable to get context for package " + ai.packageName + " while loading content provider " + info.name);
                return null;
            }
            try {
                contentProvider = (ContentProvider) c.getClassLoader().loadClass(info.name).newInstance();
                provider = contentProvider.getIContentProvider();
                if (provider == null) {
                    Slog.e(TAG, "Failed to instantiate class " + info.name + " from sourceDir " + info.applicationInfo.sourceDir);
                    return null;
                }
                contentProvider.attachInfo(c, info);
            } catch (Exception e2) {
                if (this.mInstrumentation.onException(null, e2)) {
                    return null;
                }
                throw new RuntimeException("Unable to get provider " + info.name + ": " + e2.toString(), e2);
            }
        }
        provider = holder.provider;
        if (info.applicationInfo.uid > 10000 && !context.getPackageName().equals(info.applicationInfo.packageName)) {
            Utils.handleTimeOut("acquire_provider", info.applicationInfo.packageName, ProxyInfo.LOCAL_EXCL_LIST);
        }
        synchronized (this.mProviderMap) {
            try {
                IBinder jBinder = provider.asBinder();
                ContentProviderHolder retHolder;
                if (contentProvider != null) {
                    ComponentName cname = new ComponentName(info.packageName, info.name);
                    ProviderClientRecord pr = (ProviderClientRecord) this.mLocalProvidersByName.get(cname);
                    if (pr != null) {
                        provider = pr.mProvider;
                    } else {
                        ContentProviderHolder holder2 = new ContentProviderHolder(info);
                        try {
                            holder2.provider = provider;
                            holder2.noReleaseNeeded = true;
                            pr = installProviderAuthoritiesLocked(provider, contentProvider, holder2);
                            this.mLocalProviders.put(jBinder, pr);
                            this.mLocalProvidersByName.put(cname, pr);
                            holder = holder2;
                        } catch (Throwable th2) {
                            th = th2;
                            holder = holder2;
                            throw th;
                        }
                    }
                    retHolder = pr.mHolder;
                } else {
                    ProviderRefCount prc = (ProviderRefCount) this.mProviderRefCountMap.get(jBinder);
                    if (prc == null) {
                        ProviderClientRecord client = installProviderAuthoritiesLocked(provider, contentProvider, holder);
                        if (noReleaseNeeded) {
                            prc = new ProviderRefCount(holder, client, 1000, 1000);
                        } else if (stable) {
                            prc = new ProviderRefCount(holder, client, 1, 0);
                        } else {
                            prc = new ProviderRefCount(holder, client, 0, 1);
                        }
                        this.mProviderRefCountMap.put(jBinder, prc);
                    } else if (!noReleaseNeeded) {
                        incProviderRefLocked(prc, stable);
                        try {
                            ActivityManagerNative.getDefault().removeContentProvider(holder.connection, stable);
                        } catch (RemoteException e3) {
                        }
                    }
                    retHolder = prc.holder;
                }
            } catch (Throwable th3) {
                th = th3;
                throw th;
            }
        }
    }

    private void attach(boolean system) {
        sCurrentActivityThread = this;
        this.mSystemThread = system;
        if (system) {
            DdmHandleAppName.setAppName("system_process", UserHandle.myUserId());
            try {
                this.mInstrumentation = new Instrumentation();
                this.mInitialApplication = ContextImpl.createAppContext(this, getSystemContext().mPackageInfo).mPackageInfo.makeApplication(true, null);
                this.mInitialApplication.onCreate();
                HwThemeManager.setThemeFont();
                if (getApplicationThread() != null) {
                    getApplicationThread().scheduleTrimMemory(80);
                }
                Typeface.setCurrentUserId(UserHandle.myUserId());
                Typeface.loadSystemFonts();
            } catch (Exception e) {
                throw new RuntimeException("Unable to instantiate Application():" + e.toString(), e);
            }
        }
        ViewRootImpl.addFirstDrawHandler(new Runnable() {
            public void run() {
                ActivityThread.this.ensureJitEnabled();
            }
        });
        DdmHandleAppName.setAppName("<pre-initialized>", UserHandle.myUserId());
        RuntimeInit.setApplicationObject(this.mAppThread.asBinder());
        final IActivityManager mgr = ActivityManagerNative.getDefault();
        try {
            Slog.d(TAG, "ActivityThread,attachApplication");
            if (Jlog.isPerfTest()) {
                Jlog.i(AwareNRTConstant.USERHABIT_DATA_LOAD_FINISHED_EVENT_ID, "pid=" + Process.myPid());
            }
            mgr.attachApplication(this.mAppThread);
            BinderInternal.addGcWatcher(new Runnable() {
                public void run() {
                    if (ActivityThread.this.mSomeActivitiesChanged) {
                        Runtime runtime = Runtime.getRuntime();
                        if (runtime.totalMemory() - runtime.freeMemory() > (3 * runtime.maxMemory()) / 4) {
                            ActivityThread.this.mSomeActivitiesChanged = false;
                            try {
                                mgr.releaseSomeActivities(ActivityThread.this.mAppThread);
                            } catch (RemoteException e) {
                                throw e.rethrowFromSystemServer();
                            }
                        }
                    }
                }
            });
        } catch (RemoteException ex) {
            throw ex.rethrowFromSystemServer();
        }
        DropBox.setReporter(new DropBoxReporter());
        ViewRootImpl.addConfigCallback(new ComponentCallbacks2() {
            public void onConfigurationChanged(Configuration newConfig) {
                synchronized (ActivityThread.this.mResourcesManager) {
                    if (ActivityThread.this.mResourcesManager.applyConfigurationToResourcesLocked(newConfig, null)) {
                        ActivityThread.this.updateLocaleListFromAppContext(ActivityThread.this.mInitialApplication.getApplicationContext(), ActivityThread.this.mResourcesManager.getConfiguration().getLocales());
                        if (ActivityThread.this.mPendingConfiguration == null || ActivityThread.this.mPendingConfiguration.isOtherSeqNewer(newConfig)) {
                            ActivityThread.this.mPendingConfiguration = new Configuration(newConfig);
                            ActivityThread.this.sendMessage(118, newConfig);
                        }
                    }
                }
            }

            public void onLowMemory() {
            }

            public void onTrimMemory(int level) {
            }
        });
    }

    public static ActivityThread systemMain() {
        if (ActivityManager.isHighEndGfx()) {
            ThreadedRenderer.enableForegroundTrimming();
        } else {
            ThreadedRenderer.disable(true);
        }
        ActivityThread thread = new ActivityThread();
        thread.attach(true);
        return thread;
    }

    public final void installSystemProviders(List<ProviderInfo> providers) {
        if (providers != null) {
            installContentProviders(this.mInitialApplication, providers);
        }
    }

    public int getIntCoreSetting(String key, int defaultValue) {
        synchronized (this.mResourcesManager) {
            if (this.mCoreSettings != null) {
                int i = this.mCoreSettings.getInt(key, defaultValue);
                return i;
            }
            return defaultValue;
        }
    }

    public static void main(String[] args) {
        if (Jlog.isPerfTest()) {
            Jlog.i(AwareNRTConstant.APP_KILLED_EVENT_ID, "pid=" + Process.myPid());
        }
        Trace.traceBegin(64, "ActivityThreadMain");
        SamplingProfilerIntegration.start();
        CloseGuard.setEnabled(false);
        Environment.initForCurrentUser();
        Log.initHWLog();
        EventLogger.setReporter(new EventLoggingReporter());
        TrustedCertificateStore.setDefaultUserDirectory(Environment.getUserConfigDirectory(UserHandle.myUserId()));
        Process.setArgV0("<pre-initialized>");
        Looper.prepareMainLooper();
        ActivityThread thread = new ActivityThread();
        thread.attach(false);
        if (Jlog.isPerfTest()) {
            Jlog.i(2037, "pid=" + Process.myPid());
        }
        if (sMainThreadHandler == null) {
            sMainThreadHandler = thread.getHandler();
        }
        Trace.traceEnd(64);
        Looper.loop();
        throw new RuntimeException("Main thread loop unexpectedly exited");
    }

    private Context getContextForClassLoader(ApplicationInfo ai, Context context, Context defContext) {
        try {
            String packageName = ai.packageName;
            String sourceDir = ai.sourceDir;
            String publicSourceDir = ai.publicSourceDir;
            ApplicationInfo applicationInfo = getPackageManager().getApplicationInfo(packageName, 0, UserHandle.getUserId(ai.uid));
            if (applicationInfo == null) {
                Slog.w(TAG, packageName + " is uninstalled, can't install provider");
                return null;
            }
            if (!(applicationInfo.sourceDir.equals(sourceDir) && applicationInfo.publicSourceDir.equals(publicSourceDir))) {
                Slog.w(TAG, packageName + " is replaced, sourceDir is changed from " + sourceDir + " to " + applicationInfo.sourceDir + ", publicSourceDir is changed from " + publicSourceDir + " to " + applicationInfo.publicSourceDir);
                return context.createPackageContext(ai.packageName, 1);
            }
            return defContext;
        } catch (RemoteException e) {
        } catch (NameNotFoundException e2) {
        }
    }

    private boolean isRogSupported() {
        if (this.mHasReadRogPro) {
            return this.mRogSupported;
        }
        IRogManager mRogManager = HwFrameworkFactory.getRogManager();
        if (mRogManager == null) {
            return false;
        }
        this.mRogSupported = mRogManager.isRogSupported();
        this.mHasReadRogPro = true;
        return this.mRogSupported;
    }

    private void handleMultiWindowModeChangedForRog(IBinder token, boolean isInMultiWindowMode) {
        if (isRogSupported()) {
            this.mIsInMultiWindowMode = isInMultiWindowMode;
            boolean needUpdate = false;
            if (isInMultiWindowMode && WindowManagerGlobal.getInstance().getRogEnableFactor()) {
                WindowManagerGlobal.getInstance().setRogEnableFactor(false);
                needUpdate = true;
            }
            ActivityClientRecord r = (ActivityClientRecord) this.mActivities.get(token);
            if (r != null && needUpdate) {
                String packageName = r.packageInfo.mPackageName;
                Slog.d(TAG, "handleMultiWindowModeChangedForRog->name:" + packageName);
                AppRogInfo rogInfo = WindowManagerGlobal.getInstance().getAppRogInfo(packageName);
                if (rogInfo != null) {
                    applyRogChangedInternal(packageName, rogInfo, WindowManagerGlobal.getInstance().getRogSwitchState());
                    WindowManagerGlobal.getInstance().dispatchRogInfoUpdated(packageName, rogInfo);
                } else {
                    return;
                }
            }
            return;
        }
        Slog.d(TAG, "handleMultiWindowModeChangedForRog->rog3.0 is not supported by this device");
    }

    private void handleRogInfoUpdated(UpdateRog updateInfo) {
        applyRogChangedInternal(updateInfo.packageName, updateInfo.rogInfo, updateInfo.rogEnable);
        WindowManagerGlobal.getInstance().dispatchRogInfoUpdated(updateInfo.packageName, updateInfo.rogInfo);
    }

    private void applyRogChangedInternal(String packageName, AppRogInfo rogInfo, boolean rogEnable) {
        this.mResourcesManager.applyRogToResources(rogInfo, rogEnable);
        LoadedApk packageInfo = peekPackageInfo(packageName, false);
        if (packageInfo == null) {
            Slog.i(TAG, "applyRogChangedInternal->packageInfo is null");
            return;
        }
        DisplayMetrics dm = packageInfo.getResources(this).getDisplayMetrics();
        packageInfo.getResources(this).setRogInfo(rogInfo, rogEnable);
        if (!rogEnable || rogInfo == null) {
            this.mCurDefaultDisplayDpi = dm.noncompatDensityDpi;
        } else {
            this.mCurDefaultDisplayDpi = (int) ((((float) dm.noncompatDensityDpi) / rogInfo.mRogScale) + NetworkHistoryUtils.RECOVERY_PERCENTAGE);
        }
        setDmDensityDpi(this.mCurDefaultDisplayDpi);
        Bitmap.setDefaultDensity(this.mCurDefaultDisplayDpi);
    }

    private static void setDmDensityDpi(int newDensityDpi) {
        DisplayMetrics.DENSITY_DEVICE = newDensityDpi;
    }

    private void initRogInfoWhenLaunch(AppBindData appData) {
        Bundle bundle = appData.instrumentationArgs;
        if (bundle != null) {
            Parcelable rogInfoP = bundle.getParcelable(IRogManager.INFO_KEY);
            if (rogInfoP != null) {
                AppRogInfo rogInfo = (AppRogInfo) rogInfoP;
                boolean rogEnable = bundle.getBoolean(IRogManager.SWITCH_KEY);
                String packageName = rogInfo.mPackageName;
                if (rogInfo.isSupportHotSwitch()) {
                    sendRegisterRogListenerMsg(packageName);
                }
                WindowManagerGlobal.getInstance().setRogSwitchState(rogEnable);
                WindowManagerGlobal.getInstance().addAppRogInfo(packageName, rogInfo);
                if (rogEnable) {
                    applyRogChangedInternal(packageName, rogInfo, rogEnable);
                }
            }
        }
    }

    private void sendRegisterRogListenerMsg(String packageName) {
        sendMessage(1002, packageName);
    }

    private void handleRegisterRogListenerMsg(String packageName) {
        IRogManager rogManager = HwFrameworkFactory.getRogManager();
        if (rogManager != null) {
            if (((HwRogListener) this.mRogListenerSet.get(packageName)) != null) {
                Slog.i(TAG, "handleRegisterRogListenerMsg->this pkg already registered");
                return;
            }
            HwRogListener rogListener = new HwRogListener(packageName);
            this.mRogListenerSet.put(packageName, rogListener);
            rogManager.registerRogListener(rogListener);
        }
    }

    private void handleActivityConfigChangedWithRog(ActivityClientRecord r) {
        if (isRogSupported()) {
            String packageName = r.packageInfo.mPackageName;
            AppRogInfo rogInfo = WindowManagerGlobal.getInstance().getAppRogInfo(packageName);
            boolean rogEnable = WindowManagerGlobal.getInstance().getRogSwitchState();
            if (rogInfo != null) {
                applyRogChangedInternal(packageName, rogInfo, rogEnable);
            }
        }
    }
}
