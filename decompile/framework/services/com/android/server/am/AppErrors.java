package com.android.server.am;

import android.app.ActivityManager.ProcessErrorStateInfo;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityThread;
import android.app.ApplicationErrorReport;
import android.app.ApplicationErrorReport.AnrInfo;
import android.app.ApplicationErrorReport.CrashInfo;
import android.app.Dialog;
import android.common.HwFrameworkFactory;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Binder;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.Trace;
import android.os.UserHandle;
import android.provider.Settings.Secure;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.EventLog;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;
import android.util.TimeUtils;
import com.android.internal.app.ProcessMap;
import com.android.internal.logging.MetricsLogger;
import com.android.server.HwServiceFactory;
import com.android.server.HwServiceFactory.IHwBinderMonitor;
import com.android.server.Watchdog;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

class AppErrors {
    private static final boolean IS_DEBUG_VERSION;
    private static final String TAG = "ActivityManager";
    public static final Set<String> whitelist_for_short_time = new ArraySet();
    private ArraySet<String> mAppsNotReportingCrashes;
    private final ProcessMap<BadProcessInfo> mBadProcesses = new ProcessMap();
    private final Context mContext;
    private final boolean mIsNotShowAnrDialog = SystemProperties.getBoolean("ro.config.noshow_anrdialog", false);
    private final boolean mIswhitelist_for_short_time = SystemProperties.getBoolean("persist.sys.hwgmstemporary", false);
    private final ProcessMap<Long> mProcessCrashTimes = new ProcessMap();
    private final ProcessMap<Long> mProcessCrashTimesPersistent = new ProcessMap();
    private final ActivityManagerService mService;

    static final class BadProcessInfo {
        final String longMsg;
        final String shortMsg;
        final String stack;
        final long time;

        BadProcessInfo(long time, String shortMsg, String longMsg, String stack) {
            this.time = time;
            this.shortMsg = shortMsg;
            this.longMsg = longMsg;
            this.stack = stack;
        }
    }

    static {
        boolean z = true;
        if (SystemProperties.getInt("ro.logsystem.usertype", 1) != 3) {
            z = false;
        }
        IS_DEBUG_VERSION = z;
        whitelist_for_short_time.add("com.google.android.gms");
        whitelist_for_short_time.add("com.google.android.gsf");
        whitelist_for_short_time.add("com.google.android.gsf.login");
        whitelist_for_short_time.add("com.google.android.marvin.talkback");
        whitelist_for_short_time.add("com.android.chrome");
        whitelist_for_short_time.add("com.google.android.apps.books");
        whitelist_for_short_time.add("com.android.vending");
        whitelist_for_short_time.add("com.google.android.apps.docs");
        whitelist_for_short_time.add("com.google.android.apps.magazines");
        whitelist_for_short_time.add("com.google.android.apps.maps");
        whitelist_for_short_time.add("com.google.android.apps.photos");
        whitelist_for_short_time.add("com.google.android.apps.plus");
        whitelist_for_short_time.add("com.google.android.backuptransport");
        whitelist_for_short_time.add("com.google.android.configupdater");
        whitelist_for_short_time.add("com.google.android.ext.services");
        whitelist_for_short_time.add("com.google.android.ext.shared");
        whitelist_for_short_time.add("com.google.android.feedback");
        whitelist_for_short_time.add("com.google.android.gm");
        whitelist_for_short_time.add("com.google.android.googlequicksearchbox");
        whitelist_for_short_time.add("com.google.android.play.games");
        whitelist_for_short_time.add("com.google.android.inputmethod.latin");
        whitelist_for_short_time.add("com.google.android.music");
        whitelist_for_short_time.add("com.google.android.onetimeinitializer");
        whitelist_for_short_time.add("com.google.android.partnersetup");
        whitelist_for_short_time.add("com.google.android.play.games");
        whitelist_for_short_time.add("com.google.android.printservice.recommendation");
        whitelist_for_short_time.add("com.google.android.setupwizard");
        whitelist_for_short_time.add("com.google.android.syncadapters.calendar");
        whitelist_for_short_time.add("com.google.android.syncadapters.contacts");
        whitelist_for_short_time.add("com.google.android.talk");
        whitelist_for_short_time.add("com.google.android.tts");
        whitelist_for_short_time.add("com.google.android.videos");
        whitelist_for_short_time.add("com.google.android.youtube");
    }

    AppErrors(Context context, ActivityManagerService service) {
        this.mService = service;
        this.mContext = context;
    }

    boolean dumpLocked(FileDescriptor fd, PrintWriter pw, boolean needSep, String dumpPackage) {
        boolean printed;
        int processCount;
        int ip;
        String pname;
        int uidCount;
        int i;
        int puid;
        if (!this.mProcessCrashTimes.getMap().isEmpty()) {
            printed = false;
            long now = SystemClock.uptimeMillis();
            ArrayMap<String, SparseArray<Long>> pmap = this.mProcessCrashTimes.getMap();
            processCount = pmap.size();
            for (ip = 0; ip < processCount; ip++) {
                pname = (String) pmap.keyAt(ip);
                SparseArray<Long> uids = (SparseArray) pmap.valueAt(ip);
                uidCount = uids.size();
                for (i = 0; i < uidCount; i++) {
                    puid = uids.keyAt(i);
                    ProcessRecord r = (ProcessRecord) this.mService.mProcessNames.get(pname, puid);
                    if (dumpPackage == null || (r != null && r.pkgList.containsKey(dumpPackage))) {
                        if (!printed) {
                            if (needSep) {
                                pw.println();
                            }
                            needSep = true;
                            pw.println("  Time since processes crashed:");
                            printed = true;
                        }
                        pw.print("    Process ");
                        pw.print(pname);
                        pw.print(" uid ");
                        pw.print(puid);
                        pw.print(": last crashed ");
                        TimeUtils.formatDuration(now - ((Long) uids.valueAt(i)).longValue(), pw);
                        pw.println(" ago");
                    }
                }
            }
        }
        if (!this.mBadProcesses.getMap().isEmpty()) {
            printed = false;
            ArrayMap<String, SparseArray<BadProcessInfo>> pmap2 = this.mBadProcesses.getMap();
            processCount = pmap2.size();
            for (ip = 0; ip < processCount; ip++) {
                pname = (String) pmap2.keyAt(ip);
                SparseArray<BadProcessInfo> uids2 = (SparseArray) pmap2.valueAt(ip);
                uidCount = uids2.size();
                for (i = 0; i < uidCount; i++) {
                    puid = uids2.keyAt(i);
                    r = (ProcessRecord) this.mService.mProcessNames.get(pname, puid);
                    if (dumpPackage == null || (r != null && r.pkgList.containsKey(dumpPackage))) {
                        if (!printed) {
                            if (needSep) {
                                pw.println();
                            }
                            needSep = true;
                            pw.println("  Bad processes:");
                            printed = true;
                        }
                        BadProcessInfo info = (BadProcessInfo) uids2.valueAt(i);
                        pw.print("    Bad process ");
                        pw.print(pname);
                        pw.print(" uid ");
                        pw.print(puid);
                        pw.print(": crashed at time ");
                        pw.println(info.time);
                        if (info.shortMsg != null) {
                            pw.print("      Short msg: ");
                            pw.println(info.shortMsg);
                        }
                        if (info.longMsg != null) {
                            pw.print("      Long msg: ");
                            pw.println(info.longMsg);
                        }
                        if (info.stack != null) {
                            pw.println("      Stack:");
                            int lastPos = 0;
                            for (int pos = 0; pos < info.stack.length(); pos++) {
                                if (info.stack.charAt(pos) == '\n') {
                                    pw.print("        ");
                                    pw.write(info.stack, lastPos, pos - lastPos);
                                    pw.println();
                                    lastPos = pos + 1;
                                }
                            }
                            if (lastPos < info.stack.length()) {
                                pw.print("        ");
                                pw.write(info.stack, lastPos, info.stack.length() - lastPos);
                                pw.println();
                            }
                        }
                    }
                }
            }
        }
        return needSep;
    }

    boolean isBadProcessLocked(ApplicationInfo info) {
        return this.mBadProcesses.get(info.processName, info.uid) != null;
    }

    void clearBadProcessLocked(ApplicationInfo info) {
        this.mBadProcesses.remove(info.processName, info.uid);
    }

    void resetProcessCrashTimeLocked(ApplicationInfo info) {
        this.mProcessCrashTimes.remove(info.processName, info.uid);
    }

    void resetProcessCrashTimeLocked(boolean resetEntireUser, int appId, int userId) {
        ArrayMap<String, SparseArray<Long>> pmap = this.mProcessCrashTimes.getMap();
        for (int ip = pmap.size() - 1; ip >= 0; ip--) {
            SparseArray<Long> ba = (SparseArray) pmap.valueAt(ip);
            for (int i = ba.size() - 1; i >= 0; i--) {
                boolean remove = false;
                int entUid = ba.keyAt(i);
                if (resetEntireUser) {
                    if (UserHandle.getUserId(entUid) == userId) {
                        remove = true;
                    }
                } else if (userId == -1) {
                    if (UserHandle.getAppId(entUid) == appId) {
                        remove = true;
                    }
                } else if (entUid == UserHandle.getUid(userId, appId)) {
                    remove = true;
                }
                if (remove) {
                    ba.removeAt(i);
                }
            }
            if (ba.size() == 0) {
                pmap.removeAt(ip);
            }
        }
    }

    void loadAppsNotReportingCrashesFromConfigLocked(String appsNotReportingCrashesConfig) {
        if (appsNotReportingCrashesConfig != null) {
            String[] split = appsNotReportingCrashesConfig.split(",");
            if (split.length > 0) {
                this.mAppsNotReportingCrashes = new ArraySet();
                Collections.addAll(this.mAppsNotReportingCrashes, split);
            }
        }
    }

    void killAppAtUserRequestLocked(ProcessRecord app, Dialog fromDialog) {
        app.crashing = false;
        app.crashingReport = null;
        app.notResponding = false;
        app.notRespondingReport = null;
        if (app.anrDialog == fromDialog) {
            app.anrDialog = null;
        }
        if (app.waitDialog == fromDialog) {
            app.waitDialog = null;
        }
        if (app.pid > 0 && app.pid != ActivityManagerService.MY_PID) {
            handleAppCrashLocked(app, "user-terminated", null, null, null, null);
            app.kill("user request after error", true);
        }
    }

    void scheduleAppCrashLocked(int uid, int initialPid, String packageName, String message) {
        ProcessRecord proc = null;
        synchronized (this.mService.mPidsSelfLocked) {
            for (int i = 0; i < this.mService.mPidsSelfLocked.size(); i++) {
                ProcessRecord p = (ProcessRecord) this.mService.mPidsSelfLocked.valueAt(i);
                if (p.uid == uid) {
                    if (p.pid == initialPid) {
                        proc = p;
                        break;
                    } else if (p.pkgList.containsKey(packageName)) {
                        proc = p;
                    }
                }
            }
        }
        if (proc == null) {
            Slog.w(TAG, "crashApplication: nothing for uid=" + uid + " initialPid=" + initialPid + " packageName=" + packageName);
        } else {
            proc.scheduleCrash(message);
        }
    }

    void crashApplication(ProcessRecord r, CrashInfo crashInfo) {
        long origId = Binder.clearCallingIdentity();
        try {
            crashApplicationInner(r, crashInfo);
        } finally {
            Binder.restoreCallingIdentity(origId);
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    void crashApplicationInner(ProcessRecord r, CrashInfo crashInfo) {
        long timeMillis = System.currentTimeMillis();
        String shortMsg = crashInfo.exceptionClassName;
        String longMsg = crashInfo.exceptionMessage;
        String stackTrace = crashInfo.stackTrace;
        if (shortMsg != null && longMsg != null) {
            longMsg = shortMsg + ": " + longMsg;
        } else if (shortMsg != null) {
            longMsg = shortMsg;
        }
        AppErrorResult result = new AppErrorResult();
        synchronized (this.mService) {
            try {
                ActivityManagerService.boostPriorityForLockedSection();
                if (handleAppCrashInActivityController(r, crashInfo, shortMsg, longMsg, stackTrace, timeMillis)) {
                } else {
                    if (r != null) {
                        if (r.instrumentationClass != null) {
                            ActivityManagerService.resetPriorityAfterLockedSection();
                            return;
                        }
                    }
                    if (r != null) {
                        this.mService.mBatteryStatsService.noteProcessCrash(r.processName, r.uid);
                    }
                    Data data = new Data();
                    data.result = result;
                    data.proc = r;
                    if (r == null || !makeAppCrashingLocked(r, shortMsg, longMsg, stackTrace, data)) {
                    } else {
                        Message msg = Message.obtain();
                        msg.what = 1;
                        TaskRecord task = data.task;
                        msg.obj = data;
                        this.mService.mUiHandler.sendMessage(msg);
                    }
                }
            } finally {
                ActivityManagerService.resetPriorityAfterLockedSection();
            }
        }
    }

    private boolean handleAppCrashInActivityController(ProcessRecord r, CrashInfo crashInfo, String shortMsg, String longMsg, String stackTrace, long timeMillis) {
        if (this.mService.mController == null) {
            return false;
        }
        String str;
        if (r != null) {
            try {
                str = r.processName;
            } catch (RemoteException e) {
                this.mService.mController = null;
                Watchdog.getInstance().setActivityController(null);
            }
        } else {
            str = null;
        }
        int pid = r != null ? r.pid : Binder.getCallingPid();
        int uid = r != null ? r.info.uid : Binder.getCallingUid();
        if (!this.mService.mController.appCrashed(str, pid, shortMsg, longMsg, timeMillis, crashInfo.stackTrace)) {
            if ("1".equals(SystemProperties.get("ro.debuggable", "0")) && "Native crash".equals(crashInfo.exceptionClassName)) {
                Slog.w(TAG, "Skip killing native crashed app " + str + "(" + pid + ") during testing");
            } else {
                Slog.w(TAG, "Force-killing crashed app " + str + " at watcher's request");
                if (r == null) {
                    Process.killProcess(pid);
                    ActivityManagerService.killProcessGroup(uid, pid);
                } else if (!makeAppCrashingLocked(r, shortMsg, longMsg, stackTrace, null)) {
                    r.kill("crash", true);
                }
            }
            return true;
        }
        return false;
    }

    private boolean makeAppCrashingLocked(ProcessRecord app, String shortMsg, String longMsg, String stackTrace, Data data) {
        app.crashing = true;
        app.crashingReport = generateProcessError(app, 1, null, shortMsg, longMsg, stackTrace);
        startAppProblemLocked(app);
        app.stopFreezingAllLocked();
        return handleAppCrashLocked(app, "force-crash", shortMsg, longMsg, stackTrace, data);
    }

    void startAppProblemLocked(ProcessRecord app) {
        app.errorReportReceiver = null;
        for (int userId : this.mService.mUserController.getCurrentProfileIdsLocked()) {
            if (app.userId == userId) {
                app.errorReportReceiver = ApplicationErrorReport.getErrorReportReceiver(this.mContext, app.info.packageName, app.info.flags);
            }
        }
        this.mService.skipCurrentReceiverLocked(app);
    }

    private ProcessErrorStateInfo generateProcessError(ProcessRecord app, int condition, String activity, String shortMsg, String longMsg, String stackTrace) {
        ProcessErrorStateInfo report = new ProcessErrorStateInfo();
        report.condition = condition;
        report.processName = app.processName;
        report.pid = app.pid;
        report.uid = app.info.uid;
        report.tag = activity;
        report.shortMsg = shortMsg;
        report.longMsg = longMsg;
        report.stackTrace = stackTrace;
        return report;
    }

    Intent createAppErrorIntentLocked(ProcessRecord r, long timeMillis, CrashInfo crashInfo) {
        ApplicationErrorReport report = createAppErrorReportLocked(r, timeMillis, crashInfo);
        if (report == null) {
            return null;
        }
        Intent result = new Intent("android.intent.action.APP_ERROR");
        result.setComponent(r.errorReportReceiver);
        result.putExtra("android.intent.extra.BUG_REPORT", report);
        result.addFlags(268435456);
        return result;
    }

    private ApplicationErrorReport createAppErrorReportLocked(ProcessRecord r, long timeMillis, CrashInfo crashInfo) {
        boolean z = false;
        if (r.errorReportReceiver == null) {
            return null;
        }
        if (!r.crashing && !r.notResponding && !r.forceCrashReport) {
            return null;
        }
        ApplicationErrorReport report = new ApplicationErrorReport();
        report.packageName = r.info.packageName;
        report.installerPackageName = r.errorReportReceiver.getPackageName();
        report.processName = r.processName;
        report.time = timeMillis;
        if ((r.info.flags & 1) != 0) {
            z = true;
        }
        report.systemApp = z;
        if (r.crashing || r.forceCrashReport) {
            report.type = 1;
            report.crashInfo = crashInfo;
        } else if (r.notResponding) {
            report.type = 2;
            report.anrInfo = new AnrInfo();
            report.anrInfo.activity = r.notRespondingReport.tag;
            report.anrInfo.cause = r.notRespondingReport.shortMsg;
            report.anrInfo.info = r.notRespondingReport.longMsg;
        }
        return report;
    }

    boolean handleAppCrashLocked(ProcessRecord app, String reason, String shortMsg, String longMsg, String stackTrace, Data data) {
        Long crashTime;
        long now = SystemClock.uptimeMillis();
        Long crashTimePersistent;
        if (app.isolated) {
            crashTimePersistent = null;
            crashTime = null;
        } else {
            crashTime = (Long) this.mProcessCrashTimes.get(app.info.processName, app.uid);
            crashTimePersistent = (Long) this.mProcessCrashTimesPersistent.get(app.info.processName, app.uid);
        }
        if (crashTime == null || now >= crashTime.longValue() + 60000) {
            TaskRecord affectedTask = this.mService.mStackSupervisor.finishTopRunningActivityLocked(app, reason);
            if (data != null) {
                data.task = affectedTask;
            }
            if (!(data == null || r15 == null || now >= r15.longValue() + 60000)) {
                data.repeating = true;
            }
        } else {
            Slog.w(TAG, "Process " + app.info.processName + " has crashed too many times: killing!");
            EventLog.writeEvent(EventLogTags.AM_PROCESS_CRASHED_TOO_MUCH, new Object[]{Integer.valueOf(app.userId), app.info.processName, Integer.valueOf(app.uid)});
            this.mService.mStackSupervisor.handleAppCrashLocked(app);
            if (app.persistent) {
                this.mService.mStackSupervisor.resumeFocusedStackTopActivityLocked();
            } else {
                EventLog.writeEvent(EventLogTags.AM_PROC_BAD, new Object[]{Integer.valueOf(app.userId), Integer.valueOf(app.uid), app.info.processName});
                if (!app.isolated) {
                    ProcessMap processMap = this.mBadProcesses;
                    String str = app.info.processName;
                    processMap.put(str, app.uid, new BadProcessInfo(now, shortMsg, longMsg, stackTrace));
                    this.mProcessCrashTimes.remove(app.info.processName, app.uid);
                }
                app.bad = true;
                app.removed = true;
                this.mService.removeProcessLocked(app, false, false, "crash");
                this.mService.mStackSupervisor.resumeFocusedStackTopActivityLocked();
                return false;
            }
        }
        for (int i = app.services.size() - 1; i >= 0; i--) {
            ServiceRecord sr = (ServiceRecord) app.services.valueAt(i);
            sr.crashCount++;
        }
        ArrayList<ActivityRecord> activities = app.activities;
        if (app == this.mService.mHomeProcess && activities.size() > 0 && (this.mService.mHomeProcess.info.flags & 1) == 0) {
            for (int activityNdx = activities.size() - 1; activityNdx >= 0; activityNdx--) {
                ActivityRecord r = (ActivityRecord) activities.get(activityNdx);
                if (r.isHomeActivity()) {
                    Log.i(TAG, "Clearing package preferred activities from " + r.packageName);
                    try {
                        ActivityThread.getPackageManager().clearPackagePreferredActivities(r.packageName);
                    } catch (RemoteException e) {
                    }
                    this.mService.showUninstallLauncherDialog(r.packageName);
                }
            }
        }
        if (!app.isolated) {
            this.mProcessCrashTimes.put(app.info.processName, app.uid, Long.valueOf(now));
            this.mProcessCrashTimesPersistent.put(app.info.processName, app.uid, Long.valueOf(now));
        }
        if (app.crashHandler != null) {
            this.mService.mHandler.post(app.crashHandler);
        }
        return true;
    }

    private boolean hasForegroundUI(ProcessRecord proc) {
        boolean hasForegroundUI = proc != null ? proc.foregroundActivities : false;
        if (hasForegroundUI || proc == null) {
            return hasForegroundUI;
        }
        String packageName = proc.info.packageName;
        List<RunningTaskInfo> taskInfo = this.mService.getTasks(1, 0);
        if (taskInfo == null || taskInfo.size() <= 0) {
            return hasForegroundUI;
        }
        ComponentName componentInfo = ((RunningTaskInfo) taskInfo.get(0)).topActivity;
        if (componentInfo == null || packageName == null || !packageName.equalsIgnoreCase(componentInfo.getPackageName())) {
            return hasForegroundUI;
        }
        return true;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    void handleShowAppErrorUi(Message msg) {
        Throwable th;
        Data data = msg.obj;
        boolean showBackground = Secure.getInt(this.mContext.getContentResolver(), "anr_show_background", 0) != 0;
        boolean showCrashDialog = false;
        Dialog d = null;
        synchronized (this.mService) {
            ActivityManagerService.boostPriorityForLockedSection();
            ProcessRecord proc = data.proc;
            AppErrorResult res = data.result;
            boolean isDebuggable = "1".equals(SystemProperties.get("ro.debuggable", "0"));
            if (isDebuggable || proc == null || !proc.forceCrashReport) {
                if (proc != null) {
                    if (proc.info != null) {
                        String packageName = proc.info.packageName;
                        if (this.mIswhitelist_for_short_time && whitelist_for_short_time.contains(packageName)) {
                            ActivityManagerService.resetPriorityAfterLockedSection();
                            return;
                        }
                        try {
                            if (this.mService.getRecordCust() != null) {
                                this.mService.getRecordCust().appExitRecord(packageName, "crash");
                            }
                        } catch (Throwable th2) {
                            th = th2;
                            ActivityManagerService.resetPriorityAfterLockedSection();
                            throw th;
                        }
                    }
                }
                if (proc == null || proc.crashDialog == null) {
                    int isBackground = UserHandle.getAppId(proc.uid) >= 10000 ? proc.pid != ActivityManagerService.MY_PID ? 1 : 0 : 0;
                    for (int userId : this.mService.mUserController.getCurrentProfileIdsLocked()) {
                        isBackground &= proc.userId != userId ? 1 : 0;
                    }
                    if (isBackground == 0 || showBackground) {
                        boolean contains;
                        if (this.mAppsNotReportingCrashes != null) {
                            contains = this.mAppsNotReportingCrashes.contains(proc.info.packageName);
                        } else {
                            contains = false;
                        }
                        if (isDebuggable && this.mService.canShowErrorDialogs() && !r1) {
                            if (!HwFrameworkFactory.getVRSystemServiceManager().isVRMode() && hasForegroundUI(proc)) {
                                Dialog d2 = new AppErrorDialog(this.mContext, this.mService, data);
                                try {
                                    proc.crashDialog = d2;
                                    showCrashDialog = true;
                                    d = d2;
                                } catch (Throwable th3) {
                                    th = th3;
                                    ActivityManagerService.resetPriorityAfterLockedSection();
                                    throw th;
                                }
                            }
                        }
                        if (res != null) {
                            res.set(AppErrorDialog.CANT_SHOW);
                        }
                    } else {
                        Slog.w(TAG, "Skipping crash dialog of " + proc + ": background");
                        if (res != null) {
                            res.set(AppErrorDialog.BACKGROUND_USER);
                        }
                    }
                } else {
                    Slog.e(TAG, "App already has crash dialog: " + proc);
                    if (res != null) {
                        res.set(AppErrorDialog.ALREADY_SHOWING);
                    }
                }
            } else {
                Slog.w(TAG, "Skipping native crash dialog of " + proc);
                if (res != null) {
                    res.set(AppErrorDialog.CANT_SHOW);
                }
            }
        }
    }

    void stopReportingCrashesLocked(ProcessRecord proc) {
        if (this.mAppsNotReportingCrashes == null) {
            this.mAppsNotReportingCrashes = new ArraySet();
        }
        this.mAppsNotReportingCrashes.add(proc.info.packageName);
    }

    final void appNotResponding(ProcessRecord app, ActivityRecord activity, ActivityRecord parent, boolean aboveSystem, String annotation) {
        appNotResponding(app.pid, app, activity, parent, aboveSystem, annotation);
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    final void appNotResponding(int anrPid, ProcessRecord app, ActivityRecord activity, ActivityRecord parent, boolean aboveSystem, String annotation) {
        if (IS_DEBUG_VERSION) {
            ArrayMap<String, Object> params = new ArrayMap();
            params.put("checkType", "FocusWindowNullScene");
            params.put("anrActivityName", activity != null ? activity.toString() : null);
            if (HwServiceFactory.getWinFreezeScreenMonitor() != null) {
                HwServiceFactory.getWinFreezeScreenMonitor().cancelCheckFreezeScreen(params);
            }
        }
        ArrayList firstPids = new ArrayList(5);
        SparseArray lastPids = new SparseArray(20);
        Trace.traceBegin(64, "anr_event_sync: appPid=" + app.pid + ", appName=" + app.processName + ", category=" + annotation);
        Trace.traceEnd(64);
        if (Log.HWINFO) {
            HwFrameworkFactory.getLogException().cmd(HwBroadcastRadarUtil.KEY_ACTION, "copy_systrace_to_cache");
        }
        if (this.mService.mController != null) {
            try {
                if (this.mService.mController.appEarlyNotResponding(app.processName, app.pid, annotation) < 0 && app.pid != ActivityManagerService.MY_PID) {
                    app.kill("anr", true);
                }
            } catch (RemoteException e) {
                this.mService.mController = null;
                Watchdog.getInstance().setActivityController(null);
            }
        }
        long anrTime = SystemClock.uptimeMillis();
        this.mService.updateCpuStatsNow();
        boolean showBackground = Secure.getInt(this.mContext.getContentResolver(), "anr_show_background", 0) != 0;
        synchronized (this.mService) {
            try {
                ActivityManagerService.boostPriorityForLockedSection();
                if (this.mService.mShuttingDown) {
                    Slog.i(TAG, "During shutdown skipping ANR: " + app + " " + annotation);
                } else if (app.notResponding) {
                    Slog.i(TAG, "Skipping duplicate ANR: " + app + " " + annotation);
                    ActivityManagerService.resetPriorityAfterLockedSection();
                    return;
                } else if (app.crashing) {
                    Slog.i(TAG, "Crashing app skipping ANR: " + app + " " + annotation);
                    ActivityManagerService.resetPriorityAfterLockedSection();
                    return;
                } else if (anrPid != app.pid) {
                    Slog.i(TAG, "Skipping ANR because pid of " + app.processName + " is changed: " + "anr pid: " + anrPid + ", new pid: " + app.pid + " " + annotation);
                    ActivityManagerService.resetPriorityAfterLockedSection();
                    return;
                } else if (this.mService.handleANRFilterFIFO(app.uid, 2)) {
                    Slog.i(TAG, "During holding skipping ANR: " + app + " " + annotation + "uid = " + app.uid);
                    ActivityManagerService.resetPriorityAfterLockedSection();
                    return;
                } else {
                    app.notResponding = true;
                    EventLog.writeEvent(EventLogTags.AM_ANR, new Object[]{Integer.valueOf(app.userId), Integer.valueOf(app.pid), app.processName, Integer.valueOf(app.info.flags), annotation});
                    firstPids.add(Integer.valueOf(app.pid));
                    boolean isSilentANR = (showBackground || app.isInterestingToUserLocked() || app.pid == ActivityManagerService.MY_PID) ? false : true;
                    if (!isSilentANR) {
                        int parentPid = app.pid;
                        if (!(parent == null || parent.app == null || parent.app.pid <= 0)) {
                            parentPid = parent.app.pid;
                        }
                        if (parentPid != app.pid) {
                            firstPids.add(Integer.valueOf(parentPid));
                        }
                        IHwBinderMonitor iBinderM = HwServiceFactory.getIHwBinderMonitor();
                        if (iBinderM != null) {
                            iBinderM.addBinderPid(firstPids, app.pid);
                        }
                        if (!(ActivityManagerService.MY_PID == app.pid || ActivityManagerService.MY_PID == parentPid)) {
                            firstPids.add(Integer.valueOf(ActivityManagerService.MY_PID));
                        }
                        for (int i = this.mService.mLruProcesses.size() - 1; i >= 0; i--) {
                            ProcessRecord r = (ProcessRecord) this.mService.mLruProcesses.get(i);
                            if (!(r == null || r.thread == null)) {
                                int pid = r.pid;
                                if (!(pid <= 0 || pid == app.pid || pid == parentPid || pid == ActivityManagerService.MY_PID)) {
                                    if (r.persistent) {
                                        firstPids.add(Integer.valueOf(pid));
                                    } else {
                                        lastPids.put(pid, Boolean.TRUE);
                                    }
                                }
                            }
                        }
                    }
                }
            } finally {
                ActivityManagerService.resetPriorityAfterLockedSection();
            }
        }
    }

    private void makeAppNotRespondingLocked(ProcessRecord app, String activity, String shortMsg, String longMsg) {
        app.notResponding = true;
        app.notRespondingReport = generateProcessError(app, 2, activity, shortMsg, longMsg, null);
        startAppProblemLocked(app);
        app.stopFreezingAllLocked();
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    void handleShowAnrUi(Message msg) {
        Throwable th;
        synchronized (this.mService) {
            Dialog dialog;
            ActivityManagerService.boostPriorityForLockedSection();
            HashMap<String, Object> data = msg.obj;
            ProcessRecord proc = (ProcessRecord) data.get("app");
            if (!(proc == null || proc.info == null)) {
                String packageName = proc.info.packageName;
                if (this.mIswhitelist_for_short_time && whitelist_for_short_time.contains(packageName)) {
                    ActivityManagerService.resetPriorityAfterLockedSection();
                    return;
                }
                try {
                    if (this.mService.getRecordCust() != null) {
                        this.mService.getRecordCust().appExitRecord(packageName, "anr");
                    }
                } catch (Throwable th2) {
                    th = th2;
                    dialog = null;
                    ActivityManagerService.resetPriorityAfterLockedSection();
                    throw th;
                }
            }
            if (proc == null || proc.anrDialog == null) {
                Intent intent = new Intent("android.intent.action.ANR");
                if (!this.mService.mProcessesReady) {
                    intent.addFlags(1342177280);
                }
                this.mService.broadcastIntentLocked(null, null, intent, null, null, 0, null, null, null, -1, null, false, false, ActivityManagerService.MY_PID, 1000, 0);
                if (this.mService.canShowErrorDialogs() && !HwFrameworkFactory.getVRSystemServiceManager().isVRMode()) {
                    if (!this.mIsNotShowAnrDialog) {
                        dialog = new AppNotRespondingDialog(this.mService, this.mContext, proc, (ActivityRecord) data.get("activity"), msg.arg1 != 0);
                        try {
                            proc.anrDialog = dialog;
                        } catch (Throwable th3) {
                            th = th3;
                            ActivityManagerService.resetPriorityAfterLockedSection();
                            throw th;
                        }
                    }
                }
                MetricsLogger.action(this.mContext, 317, -1);
                this.mService.killAppAtUsersRequest(proc, null);
                dialog = null;
            } else {
                Slog.e(TAG, "App already has anr dialog: " + proc);
                MetricsLogger.action(this.mContext, 317, -2);
                ActivityManagerService.resetPriorityAfterLockedSection();
            }
        }
    }
}
