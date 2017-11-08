package com.android.server.mtm.taskstatus;

import android.os.Bundle;
import android.os.SystemClock;
import android.rms.HwSysResManager;
import android.rms.iaware.AwareConstant.ResourceType;
import android.rms.iaware.CollectData;
import android.util.Slog;
import com.android.server.am.HwActivityManagerService;
import com.android.server.am.ProcessRecord;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map.Entry;

public class ProcessInfoCollector {
    private static final int CAPACITY = 10;
    private static final String TAG = "ProcessInfoCollector";
    private static ProcessInfoCollector mProcessInfoCollector = null;
    boolean DEBUG = false;
    boolean INFO = false;
    private ProcessInfo mCacheInfo = new ProcessInfo(0, 0);
    private HwActivityManagerService mHwAMS = null;
    private int mIndex = 0;
    private ProcessInfo[] mKilledProcList = new ProcessInfo[10];
    private HashMap<Integer, ProcessInfo> mProcMap = new HashMap();

    private ProcessInfoCollector() {
        init();
    }

    public static synchronized ProcessInfoCollector getInstance() {
        ProcessInfoCollector processInfoCollector;
        synchronized (ProcessInfoCollector.class) {
            if (mProcessInfoCollector == null) {
                mProcessInfoCollector = new ProcessInfoCollector();
            }
            processInfoCollector = mProcessInfoCollector;
        }
        return processInfoCollector;
    }

    private void init() {
        this.mHwAMS = HwActivityManagerService.self();
        if (this.mHwAMS == null) {
            Slog.e(TAG, "init failed to get HwAMS handler");
        }
    }

    public synchronized void recordKilledProcess(ProcessInfo info) {
        info.mKilledTime = SystemClock.uptimeMillis();
        if (this.mIndex >= 10) {
            this.mIndex = 0;
        }
        this.mKilledProcList[this.mIndex] = info;
        this.mIndex++;
    }

    public void removeKilledProcess(int pid) {
        if (pid < 0) {
            Slog.e(TAG, "removeKilledProcess: proc should not less than zero ");
        }
        synchronized (this.mProcMap) {
            if (((ProcessInfo) this.mProcMap.remove(Integer.valueOf(pid))) == null) {
                Slog.e(TAG, "removeKilledProcess: failed to find this proc ");
            }
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public ProcessInfo getProcessInfo(int pid) {
        Throwable th;
        if (pid < 0) {
            Slog.e(TAG, "getProcessInfo: proc should not less than zero ");
            return null;
        }
        ProcessInfo copyInfo = null;
        synchronized (this.mProcMap) {
            try {
                ProcessInfo info = (ProcessInfo) this.mProcMap.get(Integer.valueOf(pid));
                if (info == null) {
                    Slog.e(TAG, "getProcessInfo: failed to find this proc ");
                } else {
                    ProcessInfo copyInfo2 = new ProcessInfo(0, 0);
                    try {
                        ProcessInfo.copyProcessInfo(info, copyInfo2);
                        copyInfo = copyInfo2;
                    } catch (Throwable th2) {
                        th = th2;
                        throw th;
                    }
                }
            } catch (Throwable th3) {
                th = th3;
                throw th;
            }
        }
    }

    public ArrayList<ProcessInfo> getProcessInfoList() {
        ArrayList<ProcessInfo> procList = new ArrayList();
        synchronized (this.mProcMap) {
            for (Entry entry : this.mProcMap.entrySet()) {
                ProcessInfo Info = (ProcessInfo) entry.getValue();
                ProcessInfo copyInfo = new ProcessInfo(0, 0);
                ProcessInfo.copyProcessInfo(Info, copyInfo);
                procList.add(copyInfo);
            }
        }
        return procList;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void recordProcessInfo(int pid, int uid) {
        Throwable th;
        boolean exist = false;
        this.mCacheInfo.initialProcessInfo(pid, uid);
        if (this.mHwAMS.getProcessRecordFromMTM(this.mCacheInfo)) {
            synchronized (this.mProcMap) {
                try {
                    ProcessInfo curProcessInfo = (ProcessInfo) this.mProcMap.get(Integer.valueOf(pid));
                    if (curProcessInfo == null) {
                        ProcessInfo curProcessInfo2 = new ProcessInfo(pid, uid);
                        try {
                            this.mCacheInfo.mCreatedTime = SystemClock.elapsedRealtime();
                            curProcessInfo = curProcessInfo2;
                        } catch (Throwable th2) {
                            th = th2;
                            curProcessInfo = curProcessInfo2;
                            throw th;
                        }
                    }
                    this.mCacheInfo.mCreatedTime = curProcessInfo.mCreatedTime;
                    exist = true;
                    if (ProcessInfo.copyProcessInfo(this.mCacheInfo, curProcessInfo)) {
                        curProcessInfo.mCount++;
                        if (!exist) {
                            this.mProcMap.put(Integer.valueOf(pid), curProcessInfo);
                        }
                    } else {
                        Slog.e(TAG, "recordProcessInfo  source or target object is null");
                        return;
                    }
                } catch (Throwable th3) {
                    th = th3;
                    throw th;
                }
            }
        }
        Slog.e(TAG, "recordProcessInfo  failed to get process record");
    }

    public void enableDebug() {
        this.DEBUG = true;
        this.INFO = true;
    }

    public void disableDebug() {
        this.DEBUG = false;
        this.INFO = false;
    }

    public ArrayList getAMSLru() {
        if (this.mHwAMS != null) {
            ArrayList<ProcessRecord> list = this.mHwAMS.getAMSLru();
            if (this.DEBUG && list != null) {
                int size = list.size();
                for (int i = 0; i < size; i++) {
                    Slog.d(TAG, "getAMSLru,  process " + i + ":" + ((ProcessRecord) list.get(i)).processName);
                }
            }
            return list;
        }
        Slog.e(TAG, "getAMSLru, hw ams is null");
        return null;
    }

    public int getAMSLruBypid(int pid) {
        if (pid < 0) {
            Slog.e(TAG, "getAMSLruBypid: proc should not less than zero ");
            return -1;
        } else if (this.mHwAMS != null) {
            int lru = this.mHwAMS.getAMSLruBypid(pid);
            if (this.DEBUG) {
                Slog.d(TAG, " getAMSLruBypid,lru of process " + pid + ":" + lru);
            }
            return lru;
        } else {
            Slog.e(TAG, "getAMSLruBypid, hw ams is null");
            return -1;
        }
    }

    public boolean hasForegroundDeps(ArrayList<String> packageName) {
        if (this.mHwAMS != null) {
            synchronized (this.mProcMap) {
                for (Entry entry : this.mProcMap.entrySet()) {
                    ProcessInfo Info = (ProcessInfo) entry.getValue();
                    if (Info.mCurSchedGroup == 10 || Info.mCurAdj == 600) {
                        for (int j = 0; j < packageName.size(); j++) {
                            if (this.mHwAMS.hasDeps(Info, (String) packageName.get(j))) {
                                return true;
                            }
                        }
                        continue;
                    }
                }
            }
        } else {
            Slog.e(TAG, "hasForegroundDeps, hw ams is null");
        }
        return false;
    }

    public void dump(PrintWriter pw) {
        if (pw == null) {
            Slog.e(TAG, "dump PrintWriter pw is null");
            return;
        }
        pw.println("  Process Information Collector dump :");
        synchronized (this.mProcMap) {
            for (Entry entry : this.mProcMap.entrySet()) {
                ProcessInfo Info = (ProcessInfo) entry.getValue();
                pw.println("\r\n  Running Process information :");
                printProcInfo(Info, pw);
            }
        }
        synchronized (this) {
            for (int i = 0; i < this.mIndex; i++) {
                Info = this.mKilledProcList[i];
                pw.println("\r\n  Killed Process information " + i + ":");
                printProcInfo(Info, pw);
            }
        }
        this.mHwAMS.printLRU(pw);
    }

    private void reportToRms(ProcessInfo info) {
        HwSysResManager resManager = HwSysResManager.getInstance();
        if (resManager != null && resManager.isResourceNeeded(ResourceType.getReousrceId(ResourceType.RESOURCE_APPASSOC)) && info != null) {
            Bundle args = new Bundle();
            args.putInt("callPid", info.mPid);
            args.putInt("callUid", info.mUid);
            args.putString("callProcName", info.mProcessName);
            ArrayList<String> pkgs = new ArrayList();
            pkgs.addAll(info.mPackageName);
            args.putStringArrayList("pkgname", pkgs);
            args.putInt("relationType", 4);
            resManager.reportData(new CollectData(ResourceType.getReousrceId(ResourceType.RESOURCE_APPASSOC), System.currentTimeMillis(), args));
        }
    }

    private void printProcInfo(ProcessInfo Info, PrintWriter pw) {
        pw.println("  pid =  " + Info.mPid + ", uid = " + Info.mUid + ", count = " + Info.mCount);
        pw.println("  ProcessName = " + Info.mProcessName);
        pw.println("  Group = " + Info.mCurSchedGroup + " (-1:default, 0:backgroud, 5:top visible, 6:perceptible)");
        pw.println("  oom_Adj = " + Info.mCurAdj + ", AdjType = " + Info.mAdjType);
        pw.println("  package type = " + Info.mType + " (1:SYSTEM_SERVER, 2:SYSTEM_APP , 3:HW_INSTALL, 4:THIRDPARTY)");
        pw.println("  LRU = " + Info.mLru + " ( The first entry in the list is the least recently used)");
        pw.println("  FG Activities = " + Info.mForegroundActivities + ", FG Services = " + Info.mForegroundServices + ",Force FG =" + Info.mForceToForeground);
        int list_size = Info.mPackageName.size();
        for (int i = 0; i < list_size; i++) {
            pw.println("  package name = " + ((String) Info.mPackageName.get(i)));
        }
        if (Info.mCreatedTime > 0) {
            Date dat = new Date(Info.mCreatedTime);
            GregorianCalendar gc = new GregorianCalendar();
            gc.setTime(dat);
            pw.println("  created time = " + Info.mCreatedTime + ", data:" + new SimpleDateFormat("yyyy-MM-dd hh:mm:ss").format(gc.getTime()));
        }
        if (Info.mKilledTime > 0) {
            dat = new Date(Info.mKilledTime);
            gc = new GregorianCalendar();
            gc.setTime(dat);
            pw.println("  Killed time = " + Info.mKilledTime + ", data:" + new SimpleDateFormat("yyyy-MM-dd hh:mm:ss").format(gc.getTime()));
        }
    }
}
