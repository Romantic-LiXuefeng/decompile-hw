package com.android.server.am;

import android.app.AppGlobals;
import android.common.HwFrameworkFactory;
import android.common.HwFrameworkMonitor;
import android.content.IIntentReceiver;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.util.Flog;
import android.util.Slog;
import com.android.server.HwServiceFactory;
import com.android.server.power.IHwShutdownThread;
import com.huawei.pgmng.common.Utils;
import com.huawei.pgmng.log.LogPower;
import java.io.File;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

public class HwBroadcastQueue extends BroadcastQueue {
    static final boolean DEBUG_CONSUMPTION = false;
    private static final String MMS_PACKAGE_NAME = "com.android.mms";
    static final String TAG = "HwBroadcastQueue";
    private static final int TYPE_CONFIG_CLEAR = 0;
    private static final int TYPE_CONFIG_DROP_BC_ACTION = 2;
    private static final int TYPE_CONFIG_DROP_BC_BY_PID = 3;
    private static final int TYPE_CONFIG_MAX_PROXY_BC = 4;
    private static final int TYPE_CONFIG_PROXY_BC_ACTION = 1;
    private static final int TYPE_CONFIG_SAME_KIND_ACTION = 5;
    private static boolean mProxyFeature = true;
    static final boolean mtmBRManagerEnabled = SystemProperties.getBoolean("ro.config.multi_task_enable", false);
    private int MAX_PROXY_BROADCAST = 10;
    private boolean enableUploadRadar = true;
    private final HashMap<String, Set<String>> mActionExcludePkgs = new HashMap();
    private ArrayList<String> mActionWhiteList = new ArrayList();
    private final HashMap<String, ArrayList<String>> mAppDropActions = new HashMap();
    private final HashMap<String, ArrayList<String>> mAppProxyActions = new HashMap();
    private HwFrameworkMonitor mBroadcastMonitor = HwFrameworkFactory.getHwFrameworkMonitor();
    private HwBroadcastRadarUtil mBroadcastRadarUtil;
    private ArrayList<BroadcastRecord> mCopyOrderedBroadcasts;
    private AbsHwMtmBroadcastResourceManager mHwMtmBroadcastResourceManager = null;
    final ArrayList<BroadcastRecord> mOrderedPendingBroadcasts = new ArrayList();
    final ArrayList<BroadcastRecord> mParallelPendingBroadcasts = new ArrayList();
    private final HashMap<Integer, ArrayList<String>> mProcessDropActions = new HashMap();
    private final ArrayList<String> mProxyActions = new ArrayList();
    final ArrayList<String> mProxyBroadcastPkgs = new ArrayList();
    final HashMap<String, Integer> mProxyPkgsCount = new HashMap();
    private HashMap<String, BroadcastRadarRecord> mRadarBroadcastMap;
    HashMap<String, String> mSameKindsActionList = new HashMap<String, String>() {
        {
            put("android.intent.action.SCREEN_ON", "android.intent.action.SCREEN_OFF");
        }
    };

    static class BroadcastRadarRecord {
        String actionName;
        int count;
        String packageName;

        public BroadcastRadarRecord() {
            this.actionName = "";
            this.packageName = "";
            this.count = 0;
        }

        public BroadcastRadarRecord(String actionName, String packageName, int count) {
            this.actionName = actionName;
            this.packageName = packageName;
            this.count = count;
        }
    }

    HwBroadcastQueue(ActivityManagerService service, Handler handler, String name, long timeoutPeriod, boolean allowDelayBehindServices) {
        super(service, handler, name, timeoutPeriod, allowDelayBehindServices);
        String closeSwitcher = SystemProperties.get("persist.sys.pg_close_action", null);
        if (closeSwitcher != null && closeSwitcher.contains("proxy_bc")) {
            Slog.w(TAG, "close proxy bc ");
            mProxyFeature = false;
        }
        if (mtmBRManagerEnabled) {
            this.mHwMtmBroadcastResourceManager = HwServiceFactory.getMtmBRManager(this);
        }
        this.mCopyOrderedBroadcasts = new ArrayList();
        this.mBroadcastRadarUtil = new HwBroadcastRadarUtil();
        this.mRadarBroadcastMap = new HashMap();
        initActionWhiteList();
    }

    private static boolean isThirdParty(ProcessRecord process) {
        if (process == null || process.pid == ActivityManagerService.MY_PID || (process.info.flags & 1) != 0) {
            return false;
        }
        return true;
    }

    public void cleanupBroadcastLocked(ProcessRecord app) {
        boolean reschedule = false;
        if (isThirdParty(app)) {
            super.skipPendingBroadcastLocked(app.pid);
            for (int i = this.mOrderedBroadcasts.size() - 1; i >= 0; i--) {
                BroadcastRecord r = (BroadcastRecord) this.mOrderedBroadcasts.get(i);
                if (r.callerApp == app) {
                    boolean isSentToSelf = false;
                    List receivers = r.receivers;
                    int N = receivers != null ? receivers.size() : 0;
                    for (int j = 0; j < N; j++) {
                        ResolveInfo o = receivers.get(j);
                        if (o instanceof ResolveInfo) {
                            String appProcessName = app.processName;
                            String receiverProcessName = o.activityInfo.processName;
                            if (appProcessName != null && appProcessName.equals(receiverProcessName)) {
                                isSentToSelf = true;
                                break;
                            }
                        }
                    }
                    if (isSentToSelf) {
                        if (i == 0) {
                            cancelBroadcastTimeoutLocked();
                        }
                        this.mOrderedBroadcasts.remove(r);
                        reschedule = true;
                    }
                }
            }
            if (reschedule) {
                super.scheduleBroadcastsLocked();
            }
        }
    }

    private boolean canTrim(BroadcastRecord r1, BroadcastRecord r2) {
        if (r1 == null || r2 == null || r1.intent == null || r2.intent == null || r1.intent.getAction() == null || r2.intent.getAction() == null) {
            return false;
        }
        BroadcastFilter o1 = r1.receivers.get(0);
        BroadcastFilter o2 = r2.receivers.get(0);
        String pkg1 = getPkg(o1);
        String pkg2 = getPkg(o2);
        if (pkg1 != null && !pkg1.equals(pkg2)) {
            return false;
        }
        if (o1 != o2) {
            if ((o1 instanceof BroadcastFilter) && (o2 instanceof BroadcastFilter)) {
                if (o1.receiverList != o2.receiverList) {
                    return false;
                }
            } else if (!(o1 instanceof ResolveInfo) || !(o2 instanceof ResolveInfo)) {
                return false;
            } else {
                ResolveInfo info1 = (ResolveInfo) o1;
                ResolveInfo info2 = (ResolveInfo) o2;
                if (!(info1.activityInfo == info2.activityInfo && info1.providerInfo == info2.providerInfo && info1.serviceInfo == info2.serviceInfo)) {
                    return false;
                }
            }
        }
        String action1 = r1.intent.getAction();
        String action2 = r2.intent.getAction();
        if (action1.equals(action2)) {
            return true;
        }
        String a1 = (String) this.mSameKindsActionList.get(action1);
        String a2 = (String) this.mSameKindsActionList.get(action2);
        if ((a1 == null || !a1.equals(action2)) && (a2 == null || !a2.equals(action1))) {
            return false;
        }
        return true;
    }

    private void trimAndEnqueueBroadcast(boolean trim, boolean isParallel, BroadcastRecord r, String recevier) {
        int count = 0;
        if (this.mProxyPkgsCount.containsKey(recevier)) {
            count = ((Integer) this.mProxyPkgsCount.get(recevier)).intValue();
        }
        Iterator it;
        BroadcastRecord br;
        if (isParallel) {
            it = this.mParallelPendingBroadcasts.iterator();
            while (it.hasNext()) {
                br = (BroadcastRecord) it.next();
                if (trim && canTrim(r, br)) {
                    it.remove();
                    count--;
                    break;
                }
            }
            this.mParallelPendingBroadcasts.add(r);
        } else {
            it = this.mOrderedPendingBroadcasts.iterator();
            while (it.hasNext()) {
                br = (BroadcastRecord) it.next();
                if (trim && canTrim(r, br)) {
                    it.remove();
                    count--;
                    break;
                }
            }
            this.mOrderedPendingBroadcasts.add(r);
        }
        count++;
        this.mProxyPkgsCount.put(recevier, Integer.valueOf(count));
        Slog.v(TAG, "trim and enqueue " + this.mQueueName + " Parallel:(" + this.mParallelPendingBroadcasts.size() + ") Ordered:(" + this.mOrderedPendingBroadcasts.size() + ")(" + r + ")");
        if (count % this.MAX_PROXY_BROADCAST == 0) {
            Slog.i(TAG, "proxy max broadcasts, notify pg. recevier:" + recevier);
            notifyPG("overflow_bc", recevier, -1);
            if (count > this.MAX_PROXY_BROADCAST + 10) {
                Slog.w(TAG, "warnning, proxy more broadcast, notify pg. recevier:" + recevier);
                notifyPG("overflow_exception", recevier, -1);
            }
        }
    }

    private boolean shouldProxy(String pkg, int pid) {
        boolean z = false;
        if (!mProxyFeature) {
            return false;
        }
        if (pkg != null) {
            z = this.mProxyBroadcastPkgs.contains(pkg);
        }
        return z;
    }

    private void notifyPG(String action, String pkg, int pid) {
        Utils.handleTimeOut(action, pkg, String.valueOf(pid));
    }

    private boolean shouldNotifyPG(String action, String receiverPkg) {
        if (action == null || receiverPkg == null) {
            return true;
        }
        ArrayList<String> proxyActions = (ArrayList) this.mProxyActions.clone();
        if (this.mAppProxyActions.containsKey(receiverPkg)) {
            proxyActions = (ArrayList) this.mAppProxyActions.get(receiverPkg);
        }
        if (proxyActions != null && !proxyActions.contains(action)) {
            return true;
        }
        if (this.mActionExcludePkgs.containsKey(action)) {
            Set<String> pkgs = (Set) this.mActionExcludePkgs.get(action);
            if (pkgs != null && pkgs.contains(receiverPkg)) {
                return true;
            }
        }
        return false;
    }

    public boolean enqueueProxyBroadcast(boolean isParallel, BroadcastRecord r, Object target) {
        if (target == null) {
            return false;
        }
        String pkg = getPkg(target);
        int pid = getPid(target);
        int uid = getUid(target);
        if (pkg == null) {
            return false;
        }
        if (!shouldProxy(pkg, pid)) {
            return false;
        }
        List<Object> receiver = new ArrayList();
        receiver.add(target);
        IIntentReceiver resultTo = r.resultTo;
        if (!(isParallel || r.resultTo == null)) {
            if (ActivityManagerDebugConfig.DEBUG_BROADCAST) {
                Slog.v(TAG, "reset resultTo null");
            }
            resultTo = null;
        }
        String action = r.intent.getAction();
        boolean notify = shouldNotifyPG(action, pkg);
        if (notify) {
            LogPower.push(148, action, pkg, String.valueOf(pid), new String[]{r.callerPackage});
            if (ActivityManagerDebugConfig.DEBUG_BROADCAST) {
                Slog.v(TAG, "enqueueProxyBroadcast notify pg broadcast:" + action + " pkg:" + pkg + " pid:" + pid + " uid:" + uid);
            }
        }
        trimAndEnqueueBroadcast(!notify, isParallel, new BroadcastRecord(r.queue, r.intent, r.callerApp, r.callerPackage, r.callingPid, r.callingUid, r.resolvedType, r.requiredPermissions, r.appOp, r.options, receiver, resultTo, r.resultCode, r.resultData, r.resultExtras, r.ordered, r.sticky, r.initialSticky, r.userId), pkg);
        if (ActivityManagerDebugConfig.DEBUG_BROADCAST) {
            Slog.v(TAG, "enqueueProxyBroadcast enqueue broadcast:" + action + " pkg:" + pkg + " pid:" + pid + " uid:" + uid);
        }
        return true;
    }

    public void setProxyBCActions(List<String> actions) {
        synchronized (this.mService) {
            try {
                ActivityManagerService.boostPriorityForLockedSection();
                this.mProxyActions.clear();
                if (actions != null) {
                    Slog.i(TAG, "set default proxy broadcast actions:" + actions);
                    this.mProxyActions.addAll(actions);
                }
            } finally {
                ActivityManagerService.resetPriorityAfterLockedSection();
            }
        }
    }

    public void setActionExcludePkg(String action, String pkg) {
        if (action == null && pkg == null) {
            Slog.w(TAG, "clear mActionExcludePkgs");
            this.mActionExcludePkgs.clear();
        } else if (action == null || pkg == null) {
            Slog.w(TAG, "setActionExcludePkg invaild param");
        } else {
            Set<String> pkgs;
            if (this.mActionExcludePkgs.containsKey(action)) {
                pkgs = (Set) this.mActionExcludePkgs.get(action);
                pkgs.add(pkg);
            } else {
                pkgs = new HashSet();
                pkgs.add(pkg);
            }
            this.mActionExcludePkgs.put(action, pkgs);
        }
    }

    public void proxyBCConfig(int type, String key, List<String> value) {
        if (mProxyFeature) {
            Slog.i(TAG, String.format("proxy %s bc config [%d][%s][", new Object[]{this.mQueueName, Integer.valueOf(type), key}) + value + "]");
            synchronized (this.mService) {
                try {
                    ActivityManagerService.boostPriorityForLockedSection();
                    switch (type) {
                        case 0:
                            clearConfigLocked();
                            break;
                        case 1:
                            configProxyBCActionsLocked(key, value);
                            break;
                        case 2:
                            configDropBCActionsLocked(key, value);
                            break;
                        case 3:
                            configDropBCByPidLocked(key, value);
                            break;
                        case 4:
                            configMaxProxyBCLocked(key);
                            break;
                        case 5:
                            configSameActionLocked(key, value);
                            break;
                    }
                } finally {
                    ActivityManagerService.resetPriorityAfterLockedSection();
                }
                ActivityManagerService.resetPriorityAfterLockedSection();
            }
            return;
        }
        Slog.w(TAG, "proxy bc not support");
    }

    private void setAppProxyBCActions(String pkg, List<String> actions) {
        if (pkg != null) {
            Slog.i(TAG, "set " + pkg + " proxy broadcast actions:" + actions);
            if (actions != null) {
                this.mAppProxyActions.put(pkg, new ArrayList(actions));
                return;
            }
            this.mAppProxyActions.put(pkg, null);
        }
    }

    private void configProxyBCActionsLocked(String pkg, List<String> actions) {
        if (pkg == null && actions == null) {
            Slog.i(TAG, "invaild parameter for config proxy bc actions");
            return;
        }
        if (pkg == null) {
            setProxyBCActions(actions);
        } else {
            setAppProxyBCActions(pkg, actions);
        }
    }

    private void configDropBCActionsLocked(String pkg, List<String> actions) {
        if (pkg == null) {
            Slog.e(TAG, "config drop bc actions error");
            return;
        }
        Slog.i(TAG, pkg + " drop actions:" + actions);
        if (actions == null) {
            this.mAppDropActions.put(pkg, null);
        } else {
            this.mAppDropActions.put(pkg, new ArrayList(actions));
        }
    }

    private void configDropBCByPidLocked(String pid, List<String> actions) {
        if (pid == null) {
            Slog.e(TAG, "config drop bc actions by pid error");
            return;
        }
        try {
            Integer iPid = Integer.valueOf(Integer.parseInt(pid));
            Slog.i(TAG, iPid + " drop actions:" + actions);
            if (actions == null) {
                this.mProcessDropActions.put(iPid, null);
            } else {
                this.mProcessDropActions.put(iPid, new ArrayList(actions));
            }
        } catch (Exception e) {
            Slog.w(TAG, e.getMessage());
        }
    }

    private void configMaxProxyBCLocked(String count) {
        if (count == null) {
            Slog.e(TAG, "config max proxy broadcast error");
            return;
        }
        try {
            this.MAX_PROXY_BROADCAST = Integer.parseInt(count);
            Slog.i(TAG, "set max proxy broadcast :" + this.MAX_PROXY_BROADCAST);
        } catch (Exception e) {
            Slog.w(TAG, e.getMessage());
        }
    }

    private void configSameActionLocked(String action1, List<String> actions) {
        if (action1 == null || actions == null) {
            Slog.e(TAG, "invaild parameter for config same kind action");
            return;
        }
        for (String action2 : actions) {
            Slog.i(TAG, "config same action " + action1 + "<->" + action2);
            this.mSameKindsActionList.put(action1, action2);
        }
    }

    private void clearConfigLocked() {
        Slog.i(TAG, "clear all config");
        this.mProxyActions.clear();
        this.mAppProxyActions.clear();
        this.mAppDropActions.clear();
        this.mProcessDropActions.clear();
        this.mActionExcludePkgs.clear();
        this.mSameKindsActionList.clear();
    }

    private boolean dropActionLocked(String pkg, int pid, BroadcastRecord br) {
        String action = br.intent.getAction();
        if (pid == -1 || isAlivePid(pid)) {
            if (this.mProcessDropActions.containsKey(Integer.valueOf(pid))) {
                ArrayList<String> actions = (ArrayList) this.mProcessDropActions.get(Integer.valueOf(pid));
                if (actions == null) {
                    Slog.i(TAG, "process " + pid + " cache, drop all proxy broadcast, now drop :" + br);
                    return true;
                } else if (action != null && actions.contains(action)) {
                    Slog.i(TAG, "process " + pid + " cache, drop list broadcast, now drop :" + br);
                    return true;
                }
            }
            if (this.mAppDropActions.containsKey(pkg)) {
                ArrayList<String> dropActions = (ArrayList) this.mAppDropActions.get(pkg);
                if (dropActions == null) {
                    Slog.i(TAG, "pkg " + pkg + " cache, drop all proxy broadcast, now drop " + br);
                    return true;
                } else if (action != null && dropActions.contains(action)) {
                    Slog.i(TAG, "pkg " + pkg + " cache, drop list broadcast, now drop " + br);
                    return true;
                }
            }
            return false;
        }
        Slog.i(TAG, "process " + pid + " has died, drop " + br);
        return true;
    }

    private boolean isAlivePid(int pid) {
        return new File(new StringBuilder().append("/proc/").append(pid).toString()).exists();
    }

    public long proxyBroadcast(List<String> pkgs, boolean proxy) {
        if (mProxyFeature) {
            long j;
            if (ActivityManagerDebugConfig.DEBUG_BROADCAST) {
                Slog.d(TAG, (proxy ? "proxy " : "unproxy ") + this.mQueueName + " broadcast " + " pkgs:" + pkgs);
            }
            synchronized (this.mService) {
                try {
                    ActivityManagerService.boostPriorityForLockedSection();
                    j = 0;
                    boolean pending = this.mPendingBroadcastTimeoutMessage;
                    List<String> pkgList = new ArrayList();
                    String pkg;
                    if (proxy) {
                        pkgList = pkgs;
                        for (String pkg2 : pkgs) {
                            if (!this.mProxyBroadcastPkgs.contains(pkg2)) {
                                this.mProxyBroadcastPkgs.add(pkg2);
                            }
                        }
                        if (pending) {
                            if (this.mOrderedBroadcasts.size() > 0) {
                                BroadcastRecord r = (BroadcastRecord) this.mOrderedBroadcasts.get(0);
                                if (r.nextReceiver >= 1) {
                                    pkg2 = getPkg(r.receivers.get(r.nextReceiver - 1));
                                    if (pkg2 != null && pkgs.contains(pkg2)) {
                                        j = this.mTimeoutPeriod;
                                    }
                                }
                            }
                        }
                    } else {
                        int i;
                        if (pkgs != null) {
                            pkgList = pkgs;
                        } else {
                            ArrayList pkgList2 = (ArrayList) this.mProxyBroadcastPkgs.clone();
                        }
                        ArrayList<BroadcastRecord> orderedProxyBroadcasts = new ArrayList();
                        ArrayList<BroadcastRecord> parallelProxyBroadcasts = new ArrayList();
                        proxyBroadcastInnerLocked(this.mParallelPendingBroadcasts, pkgList, parallelProxyBroadcasts);
                        proxyBroadcastInnerLocked(this.mOrderedPendingBroadcasts, pkgList, orderedProxyBroadcasts);
                        this.mProcessDropActions.clear();
                        this.mProxyBroadcastPkgs.removeAll(pkgList);
                        for (String pkg22 : pkgList) {
                            if (this.mProxyPkgsCount.containsKey(pkg22)) {
                                this.mProxyPkgsCount.remove(pkg22);
                            }
                        }
                        if (ActivityManagerDebugConfig.DEBUG_BROADCAST) {
                            Slog.v(TAG, "unproxy " + this.mQueueName + " Broadcast pkg Parallel Broadcasts (" + this.mParallelBroadcasts + ")");
                        }
                        for (i = 0; i < parallelProxyBroadcasts.size(); i++) {
                            this.mParallelBroadcasts.add(i, (BroadcastRecord) parallelProxyBroadcasts.get(i));
                        }
                        if (ActivityManagerDebugConfig.DEBUG_BROADCAST) {
                            Slog.v(TAG, "unproxy " + this.mQueueName + " Broadcast pkg Parallel Broadcasts (" + this.mParallelBroadcasts + ")");
                        }
                        if (ActivityManagerDebugConfig.DEBUG_BROADCAST) {
                            Slog.v(TAG, "unproxy " + this.mQueueName + " Broadcast pkg Ordered Broadcasts (" + this.mOrderedBroadcasts + ")");
                        }
                        for (i = 0; i < orderedProxyBroadcasts.size(); i++) {
                            if (pending) {
                                this.mOrderedBroadcasts.add(i + 1, (BroadcastRecord) orderedProxyBroadcasts.get(i));
                            } else {
                                this.mOrderedBroadcasts.add(i, (BroadcastRecord) orderedProxyBroadcasts.get(i));
                            }
                        }
                        if (ActivityManagerDebugConfig.DEBUG_BROADCAST) {
                            Slog.v(TAG, "unproxy " + this.mQueueName + " Broadcast pkg Ordered Broadcasts (" + this.mOrderedBroadcasts + ")");
                        }
                        if (parallelProxyBroadcasts.size() > 0 || orderedProxyBroadcasts.size() > 0) {
                            if (ActivityManagerDebugConfig.DEBUG_BROADCAST) {
                                Slog.v(TAG, "unproxy " + this.mQueueName + " Broadcast pkg Parallel Broadcasts (" + parallelProxyBroadcasts.size() + ")(" + parallelProxyBroadcasts + ")");
                            }
                            if (ActivityManagerDebugConfig.DEBUG_BROADCAST) {
                                Slog.v(TAG, "unproxy " + this.mQueueName + " Broadcast pkg Ordered Broadcasts (" + orderedProxyBroadcasts.size() + ")(" + orderedProxyBroadcasts + ")");
                            }
                            scheduleBroadcastsLocked();
                        }
                    }
                } finally {
                    ActivityManagerService.resetPriorityAfterLockedSection();
                }
            }
            return j;
        }
        Slog.w(TAG, "proxy bc not support");
        return -1;
    }

    private void proxyBroadcastInnerLocked(ArrayList<BroadcastRecord> pendingBroadcasts, List<String> unProxyPkgs, ArrayList<BroadcastRecord> unProxyBroadcasts) {
        Iterator it = pendingBroadcasts.iterator();
        while (it.hasNext()) {
            BroadcastRecord br = (BroadcastRecord) it.next();
            Object nextReceiver = br.receivers.get(0);
            String proxyPkg = getPkg(nextReceiver);
            if (proxyPkg != null && unProxyPkgs.contains(proxyPkg)) {
                int pid = getPid(nextReceiver);
                it.remove();
                if (!dropActionLocked(proxyPkg, pid, br)) {
                    unProxyBroadcasts.add(br);
                }
            }
        }
    }

    private String getPkg(Object target) {
        if (target instanceof BroadcastFilter) {
            return ((BroadcastFilter) target).packageName;
        }
        if (!(target instanceof ResolveInfo)) {
            return null;
        }
        ResolveInfo info = (ResolveInfo) target;
        if (info.activityInfo == null || info.activityInfo.applicationInfo == null) {
            return null;
        }
        return info.activityInfo.applicationInfo.packageName;
    }

    private int getPid(Object target) {
        if (!(target instanceof BroadcastFilter)) {
            return target instanceof ResolveInfo ? -1 : -1;
        } else {
            BroadcastFilter filter = (BroadcastFilter) target;
            if (filter.receiverList == null) {
                return -1;
            }
            int pid = filter.receiverList.pid;
            if (pid > 0 || filter.receiverList.app == null) {
                return pid;
            }
            return filter.receiverList.app.pid;
        }
    }

    private int getUid(Object target) {
        if (target instanceof BroadcastFilter) {
            BroadcastFilter filter = (BroadcastFilter) target;
            if (filter.receiverList == null) {
                return -1;
            }
            int uid = filter.receiverList.uid;
            if (uid > 0 || filter.receiverList.app == null) {
                return uid;
            }
            return filter.receiverList.app.uid;
        } else if (!(target instanceof ResolveInfo)) {
            return -1;
        } else {
            ResolveInfo info = (ResolveInfo) target;
            if (info.activityInfo == null || info.activityInfo.applicationInfo == null) {
                return -1;
            }
            return info.activityInfo.applicationInfo.uid;
        }
    }

    public AbsHwMtmBroadcastResourceManager getMtmBRManager() {
        return this.mHwMtmBroadcastResourceManager;
    }

    public boolean getMtmBRManagerEnabled() {
        return mtmBRManagerEnabled;
    }

    public boolean uploadRadarMessage(int scene, Bundle data) {
        switch (scene) {
            case HwBroadcastRadarUtil.SCENE_DEF_BROADCAST_OVERLENGTH /*2801*/:
                int i = 0;
                while (i < this.mOrderedBroadcasts.size()) {
                    try {
                        this.mCopyOrderedBroadcasts.add((BroadcastRecord) this.mOrderedBroadcasts.get(i));
                        i++;
                    } catch (Exception e) {
                        Slog.w(TAG, e.getMessage());
                    } finally {
                        this.mCopyOrderedBroadcasts.clear();
                    }
                }
                handleBroadcastQueueOverlength(this.mCopyOrderedBroadcasts);
                return true;
            case 2803:
                handleReceiverTimeOutRadar();
                return true;
            default:
                return false;
        }
    }

    public void enqueueOrderedBroadcastLocked(BroadcastRecord r) {
        super.enqueueOrderedBroadcastLocked(r);
        if (SystemClock.uptimeMillis() > HwBroadcastRadarUtil.SYSTEM_BOOT_COMPLETED_TIME) {
            int brSize = this.mOrderedBroadcasts.size();
            if (!this.enableUploadRadar && brSize < HwBroadcastRadarUtil.MAX_BROADCASTQUEUE_LENGTH) {
                this.enableUploadRadar = true;
                Flog.i(HdmiCecKeycode.CEC_KEYCODE_SELECT_MEDIA_FUNCTION, "enable radar when current queue size is " + brSize + ".");
            }
            if (this.enableUploadRadar && brSize >= HwBroadcastRadarUtil.MAX_BROADCASTQUEUE_LENGTH) {
                uploadRadarMessage(HwBroadcastRadarUtil.SCENE_DEF_BROADCAST_OVERLENGTH, null);
                this.enableUploadRadar = false;
                Flog.i(HdmiCecKeycode.CEC_KEYCODE_SELECT_MEDIA_FUNCTION, "disable radar after radar uploaded, current size is " + brSize + ".");
            }
        }
    }

    private void initActionWhiteList() {
        this.mActionWhiteList.add("android.net.wifi.SCAN_RESULTS");
        this.mActionWhiteList.add("android.net.wifi.WIFI_STATE_CHANGED");
        this.mActionWhiteList.add("android.net.conn.CONNECTIVITY_CHANGE");
        this.mActionWhiteList.add("android.intent.action.TIME_TICK");
    }

    private void handleBroadcastQueueOverlength(ArrayList<BroadcastRecord> copyOrderedBroadcasts) {
        String callerPkg = "";
        String broadcastAction = "";
        boolean isContainsMMS = false;
        String curReceiverName = null;
        String curReceiverPkgName = null;
        for (int i = 0; i < copyOrderedBroadcasts.size(); i++) {
            BroadcastRecord br = (BroadcastRecord) copyOrderedBroadcasts.get(i);
            if (!(br == null || br.intent == null)) {
                if (br.nextReceiver > 0) {
                    BroadcastFilter curReceiver = br.receivers.get(br.nextReceiver - 1);
                    if (curReceiver instanceof BroadcastFilter) {
                        curReceiverPkgName = curReceiver.packageName;
                    } else if (curReceiver instanceof ResolveInfo) {
                        ResolveInfo info = (ResolveInfo) curReceiver;
                        if (info.activityInfo != null) {
                            curReceiverName = info.activityInfo.applicationInfo.name;
                            curReceiverPkgName = info.activityInfo.applicationInfo.packageName;
                        }
                    }
                }
                callerPkg = br.callerPackage;
                broadcastAction = br.intent.getAction();
                if (MMS_PACKAGE_NAME.equals(callerPkg) || "android.provider.Telephony.SMS_DELIVER".equals(broadcastAction) || "android.provider.Telephony.SMS_RECEIVED".equals(broadcastAction)) {
                    isContainsMMS = true;
                }
                BroadcastRadarRecord broadcastRadarRecord = (BroadcastRadarRecord) this.mRadarBroadcastMap.get(broadcastAction);
                if (broadcastRadarRecord == null) {
                    broadcastRadarRecord = new BroadcastRadarRecord(broadcastAction, callerPkg, 0);
                }
                broadcastRadarRecord.count++;
                this.mRadarBroadcastMap.put(broadcastAction, broadcastRadarRecord);
            }
        }
        String mostFrequentAction = "";
        int maxNum = 0;
        for (Entry<String, BroadcastRadarRecord> curActionEntry : this.mRadarBroadcastMap.entrySet()) {
            int curBroadcastNum = ((BroadcastRadarRecord) curActionEntry.getValue()).count;
            if (curBroadcastNum > maxNum) {
                maxNum = curBroadcastNum;
                mostFrequentAction = (String) curActionEntry.getKey();
            }
        }
        BroadcastRadarRecord brRecord = (BroadcastRadarRecord) this.mRadarBroadcastMap.get(mostFrequentAction);
        if (this.mActionWhiteList.contains(brRecord.actionName)) {
            Flog.i(HdmiCecKeycode.CEC_KEYCODE_SELECT_MEDIA_FUNCTION, "The action[" + brRecord.actionName + "] should be ignored for order broadcast queue overlength.");
            return;
        }
        String versionName = "unknown";
        if (curReceiverPkgName != null) {
            try {
                if (!curReceiverPkgName.isEmpty()) {
                    PackageInfo packageInfo = AppGlobals.getPackageManager().getPackageInfo(curReceiverPkgName, DumpState.DUMP_KEYSETS, 0);
                    if (packageInfo != null) {
                        versionName = packageInfo.versionName;
                    }
                }
            } catch (Exception e) {
                Slog.e(TAG, e.getMessage());
            }
        }
        Bundle data = new Bundle();
        data.putString(HwBroadcastRadarUtil.KEY_PACKAGE, brRecord.packageName);
        data.putString(HwBroadcastRadarUtil.KEY_ACTION, brRecord.actionName);
        data.putInt(HwBroadcastRadarUtil.KEY_ACTION_COUNT, brRecord.count);
        data.putString(HwBroadcastRadarUtil.KEY_RECEIVER, curReceiverName);
        data.putString(HwBroadcastRadarUtil.KEY_VERSION_NAME, versionName);
        data.putBoolean(HwBroadcastRadarUtil.KEY_MMS_BROADCAST_FLAG, isContainsMMS);
        this.mBroadcastRadarUtil.handleBroadcastQueueOverlength(data);
        if (this.mBroadcastMonitor != null) {
            this.mBroadcastMonitor.monitor(907400002, data);
        }
        this.mRadarBroadcastMap.clear();
    }

    private void handleReceiverTimeOutRadar() {
        if (this.mOrderedBroadcasts.size() == 0) {
            Slog.w(TAG, "handleReceiverTimeOutRadar, but mOrderedBroadcasts is empty");
            return;
        }
        BroadcastRecord r = (BroadcastRecord) this.mOrderedBroadcasts.get(0);
        if (r.receivers == null || r.nextReceiver <= 0) {
            Slog.w(TAG, "handleReceiverTimeOutRadar Timeout on receiver, but receiver is invalid.");
            return;
        }
        String pkg = null;
        String receiverName = null;
        String str = null;
        int uid = 0;
        long receiverTime = SystemClock.uptimeMillis() - r.receiverTime;
        if (r.intent != null) {
            str = r.intent.getAction();
            if (receiverTime < ((long) ((r.intent.getFlags() & 268435456) != 0 ? IHwShutdownThread.SHUTDOWN_ANIMATION_WAIT_TIME : 5000))) {
                Slog.w(TAG, "current receiver should not report timeout.");
                return;
            }
        }
        BroadcastFilter curReceiver = r.receivers.get(r.nextReceiver - 1);
        Flog.i(HdmiCecKeycode.CEC_KEYCODE_SELECT_MEDIA_FUNCTION, "receiver " + curReceiver + " took " + receiverTime + "ms when receive " + r);
        if (curReceiver instanceof BroadcastFilter) {
            pkg = curReceiver.packageName;
        } else if (curReceiver instanceof ResolveInfo) {
            ResolveInfo info = (ResolveInfo) curReceiver;
            if (info.activityInfo != null) {
                receiverName = info.activityInfo.name;
                if (info.activityInfo.applicationInfo != null) {
                    uid = info.activityInfo.applicationInfo.uid;
                    pkg = info.activityInfo.applicationInfo.packageName;
                }
            }
        }
        if (SystemClock.uptimeMillis() > HwBroadcastRadarUtil.SYSTEM_BOOT_COMPLETED_TIME) {
            String versionName = "";
            if (pkg != null) {
                try {
                    if (!pkg.isEmpty()) {
                        PackageInfo packageInfo = AppGlobals.getPackageManager().getPackageInfo(pkg, DumpState.DUMP_KEYSETS, UserHandle.getUserId(uid));
                        if (packageInfo != null) {
                            versionName = packageInfo.versionName;
                        }
                    }
                } catch (Exception e) {
                    Slog.e(TAG, e.getMessage());
                }
            }
            Bundle data = new Bundle();
            data.putString(HwBroadcastRadarUtil.KEY_PACKAGE, pkg);
            data.putString(HwBroadcastRadarUtil.KEY_RECEIVER, receiverName);
            data.putString(HwBroadcastRadarUtil.KEY_ACTION, str);
            data.putString(HwBroadcastRadarUtil.KEY_VERSION_NAME, versionName);
            data.putFloat(HwBroadcastRadarUtil.KEY_RECEIVE_TIME, ((float) receiverTime) / 1000.0f);
            data.putParcelable(HwBroadcastRadarUtil.KEY_BROADCAST_INTENT, r.intent);
            if (this.mBroadcastMonitor != null) {
                this.mBroadcastMonitor.monitor(907400003, data);
            }
            this.mBroadcastRadarUtil.handleReceiverTimeOut(data);
        }
    }

    final boolean dumpLocked(FileDescriptor fd, PrintWriter pw, String[] args, int opti, boolean dumpAll, String dumpPackage, boolean needSep) {
        boolean ret = super.dumpLocked(fd, pw, args, opti, dumpAll, dumpPackage, needSep);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        pw.println();
        if (mProxyFeature) {
            String app;
            Object actions;
            PrintWriter printWriter;
            pw.println("  Proxy broadcast [" + this.mQueueName + "] pkg:" + this.mProxyBroadcastPkgs);
            pw.println("    Default proxy actions :" + this.mProxyActions);
            pw.println("    APP proxy actions :");
            for (Entry entry : this.mAppProxyActions.entrySet()) {
                app = (String) entry.getKey();
                actions = entry.getValue();
                if (actions == null) {
                    pw.println("        " + app + " null");
                } else {
                    pw.println("        " + app + " " + ((ArrayList) actions));
                }
            }
            pw.println("    Same kind actions :");
            for (Entry entry2 : this.mSameKindsActionList.entrySet()) {
                String action2 = (String) entry2.getValue();
                printWriter = pw;
                printWriter.println("        " + ((String) entry2.getKey()) + " <-> " + action2);
            }
            pw.println("    APP drop actions :");
            for (Entry entry22 : this.mAppDropActions.entrySet()) {
                app = (String) entry22.getKey();
                actions = entry22.getValue();
                if (actions == null) {
                    pw.println("        " + app + " null");
                } else {
                    pw.println("        " + app + " " + ((ArrayList) actions));
                }
            }
            pw.println("    Process drop actions :");
            for (Entry entry222 : this.mProcessDropActions.entrySet()) {
                Integer process = (Integer) entry222.getKey();
                actions = entry222.getValue();
                if (actions == null) {
                    pw.println("        " + process + " null");
                } else {
                    pw.println("        " + process + " " + ((ArrayList) actions));
                }
            }
            pw.println("    Proxy pkgs broadcast count:");
            for (Entry entry2222 : this.mProxyPkgsCount.entrySet()) {
                Integer count = (Integer) entry2222.getValue();
                printWriter = pw;
                printWriter.println("        " + ((String) entry2222.getKey()) + " " + count);
            }
            pw.println("    Action exclude pkg:");
            for (Entry entry22222 : this.mActionExcludePkgs.entrySet()) {
                Set<String> pkgs = (Set) entry22222.getValue();
                printWriter = pw;
                printWriter.println("        " + ((String) entry22222.getKey()) + " " + pkgs);
            }
            pw.println("    MAX_PROXY_BROADCAST:" + this.MAX_PROXY_BROADCAST);
            pw.println("  Proxy Parallel Broadcast:" + this.mParallelPendingBroadcasts.size());
            if (this.mParallelPendingBroadcasts.size() <= 100) {
                for (BroadcastRecord br : this.mParallelPendingBroadcasts) {
                    br.dump(pw, "    ", sdf);
                }
            }
            pw.println("  Proxy Ordered Broadcast:" + this.mOrderedPendingBroadcasts.size());
            if (this.mOrderedPendingBroadcasts.size() <= 100) {
                for (BroadcastRecord br2 : this.mOrderedPendingBroadcasts) {
                    br2.dump(pw, "    ", sdf);
                }
            }
        }
        return ret;
    }

    public ArrayList<Integer> getIawareDumpData() {
        ArrayList<Integer> queueSizes = new ArrayList();
        queueSizes.add(Integer.valueOf(this.mParallelBroadcasts.size()));
        queueSizes.add(Integer.valueOf(this.mOrderedBroadcasts.size()));
        return queueSizes;
    }

    boolean cleanupDisabledPackageReceiversLocked(String packageName, Set<String> filterByClasses, int userId, boolean doit) {
        Slog.d(TAG, "cleanupDisabledPackageReceiversLocked for userId " + userId);
        if (2147383647 != userId) {
            return super.cleanupDisabledPackageReceiversLocked(packageName, filterByClasses, userId, doit);
        }
        boolean didSomething = false;
        int i = this.mParallelBroadcasts.size() - 1;
        while (i >= 0) {
            if (((BroadcastRecord) this.mParallelBroadcasts.get(i)).callerApp == null || ((BroadcastRecord) this.mParallelBroadcasts.get(i)).callerApp.info.euid != 0) {
                didSomething |= ((BroadcastRecord) this.mParallelBroadcasts.get(i)).cleanupDisabledPackageReceiversLocked(packageName, filterByClasses, 0, doit);
                if (!doit && didSomething) {
                    return true;
                }
            }
            i--;
        }
        i = this.mOrderedBroadcasts.size() - 1;
        while (i >= 0) {
            if (((BroadcastRecord) this.mOrderedBroadcasts.get(i)).callerApp == null || ((BroadcastRecord) this.mOrderedBroadcasts.get(i)).callerApp.info.euid != 0) {
                didSomething |= ((BroadcastRecord) this.mOrderedBroadcasts.get(i)).cleanupDisabledPackageReceiversLocked(packageName, filterByClasses, 0, doit);
                if (!doit && didSomething) {
                    return true;
                }
            }
            i--;
        }
        return didSomething;
    }
}
