package com.android.server.rms.iaware.memory.utils;

import android.os.Process;
import android.rms.iaware.AwareLog;
import com.android.server.rms.collector.MemInfoReader;

public class MemoryReader {
    private static final int BACKUP_APP_ADJ = 3;
    private static final int CACHED_APP_MAX_ADJ = 15;
    private static final int CACHED_APP_MIN_ADJ = 9;
    private static final int FOREGROUND_APP_ADJ = 0;
    private static final String LMK_KILL_COUNT = "/sys/module/lowmemorykiller/parameters/kill_count";
    private static final int LMK_MINFREE_SIZE = 6;
    private static final String MEMINFO_ALLOC_COUNT = "/sys/kernel/debug/slowpath_count";
    private static final int PERCEPTIBLE_APP_ADJ = 2;
    private static final int PREVIOUS_APP_ADJ = 7;
    private static final int[] PROCESS_STATM_FORMAT = new int[]{32, 8224, 8224, 32, 32, 32, 32};
    private static final int PROCESS_STATM_SHARED = 1;
    private static final int PROCESS_STATM_VSS = 0;
    private static final int PROCESS_SWAPS_TOTAL = 0;
    private static final int PROCESS_SWAPS_USED = 1;
    private static final int PROC_COLON_TERM = 58;
    private static final int PROC_LINE_TERM = 10;
    private static final int[] PROC_MEMINFO_FORMAT = new int[]{PROC_COLON_TERM, 8250, 8250};
    private static final String PROC_MEMINFO_NAME = "/proc/meminfo_lite";
    private static final int[] PROC_SWAPS_FORMAT = new int[]{266, 265, 8201, 8201};
    private static final String PROC_SWAPS_NAME = "/proc/swaps";
    private static final int SERVICE_B_ADJ = 8;
    private static final String TAG = "AwareMem_MemReader";
    private static final int VISIBLE_APP_ADJ = 1;
    private static final Object mLock = new Object();
    private static MemoryReader sReader;
    private long mMemAvailable = 0;
    private long mMemFree = 0;
    private MemInfoReader mMemInfo = new MemInfoReader();
    private long mMemTotal = 0;

    public static MemoryReader getInstance() {
        MemoryReader memoryReader;
        synchronized (mLock) {
            if (sReader == null) {
                sReader = new MemoryReader();
            }
            memoryReader = sReader;
        }
        return memoryReader;
    }

    private MemoryReader() {
    }

    public long getTotalRam() {
        long j;
        synchronized (mLock) {
            if (0 == this.mMemTotal) {
                this.mMemInfo.readMemInfo();
                this.mMemTotal = this.mMemInfo.getTotalSizeKb();
            }
            j = this.mMemTotal;
        }
        return j;
    }

    public long getFreeRam() {
        synchronized (mLock) {
            if (updateMemoryInfo() != 0) {
                return -1;
            }
            long j = this.mMemFree;
            return j;
        }
    }

    public long getMemAvailable() {
        synchronized (mLock) {
            if (updateMemoryInfo() != 0) {
                return -1;
            }
            long j = this.mMemAvailable;
            return j;
        }
    }

    public static boolean isZramOK() {
        long[] out = new long[2];
        if (Process.readProcFile(PROC_SWAPS_NAME, PROC_SWAPS_FORMAT, null, out, null)) {
            long total = out[0];
            long used = out[1];
            if (used < 0 || total < 0 || total - used <= MemoryConstant.getReservedZramSpace()) {
                return false;
            }
            return true;
        }
        AwareLog.e(TAG, "getUsedZram failed");
        return false;
    }

    public static long getPssForPid(int pid) {
        if (pid < 1) {
            return 0;
        }
        long[] statmData = new long[2];
        if (Process.readProcFile("/proc/" + pid + "/statm", PROCESS_STATM_FORMAT, null, statmData, null)) {
            long vss = statmData[0];
            long shared = statmData[1];
            if (vss > shared) {
                return (vss - shared) << 2;
            }
        }
        return 0;
    }

    private int updateMemoryInfo() {
        long[] memData = new long[2];
        if (Process.readProcFile(PROC_MEMINFO_NAME, PROC_MEMINFO_FORMAT, null, memData, null)) {
            this.mMemFree = memData[0];
            this.mMemAvailable = memData[1];
        } else if (this.mMemInfo.readMemInfo() != 0) {
            return -1;
        } else {
            this.mMemFree = this.mMemInfo.getFreeSizeKb();
            this.mMemAvailable = this.mMemInfo.getCachedSizeKb() + this.mMemInfo.getFreeSizeKb();
        }
        return 0;
    }

    public static long getLmkOccurCount() {
        long[] occurCount = new long[1];
        if (Process.readProcFile(LMK_KILL_COUNT, new int[]{8224}, null, occurCount, null)) {
            return occurCount[0];
        }
        return 0;
    }

    public static String[] getMeminfoAllocCount() {
        String[] meminfoAllocCount = new String[2];
        if (Process.readProcFile(MEMINFO_ALLOC_COUNT, new int[]{4106, 4106}, meminfoAllocCount, null, null)) {
            return meminfoAllocCount;
        }
        return new String[0];
    }
}
