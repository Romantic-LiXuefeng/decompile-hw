package com.android.server.rms.iaware.memory.policy;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.rms.iaware.AwareConstant;
import android.rms.iaware.AwareLog;
import com.android.server.mtm.iaware.appmng.AwareAppMngSort;
import com.android.server.rms.iaware.hiber.constant.AppHibernateCst;
import com.android.server.rms.iaware.memory.data.handle.DataInputHandle;
import com.android.server.rms.iaware.memory.utils.BigDataStore;
import com.android.server.rms.iaware.memory.utils.CpuReader;
import com.android.server.rms.iaware.memory.utils.EventTracker;
import com.android.server.rms.iaware.memory.utils.MemoryConstant;
import com.android.server.rms.iaware.memory.utils.MemoryReader;
import com.android.server.rms.iaware.memory.utils.MemoryUtils;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class IdleMemoryExecutor extends AbsMemoryExecutor {
    private static final int CONTINUOUS_LOW_MEM_COUNT_THRESHOLD = 3;
    private static final int MSG_CYCLE_RECYCLE = 11;
    private static final int PROTECTLRU_MIN_TIME_GAP = 600000;
    private static final int SYSTEM_MEMORY_STATE_BASE = 0;
    private static final int SYSTEM_MEMORY_STATE_LOW = 1;
    private static final int SYSTEM_MEMORY_STATE_NORMAL = 0;
    private static final String TAG = "AwareMem_IdleMemExec";
    private BigDataStore mBigDataStore;
    private ThreadPoolExecutor mIdleMemAppExecutor;
    private MemHandler mMemHandler;
    private int mProtectCacheFlag;
    private long mProtectCacheTimestamp;
    private long mSysCpuOverLoadCnt;
    private LimitedSizeQueue<Integer> mSysLowMemStateTracker;

    private static final class CpuResult {
        boolean mBusy;
        int mCpuLoad;
        String mTraceInfo;

        private CpuResult() {
            this.mCpuLoad = 0;
            this.mBusy = true;
            this.mTraceInfo = null;
        }
    }

    private static final class LimitedSizeQueue<K> extends LinkedList<K> {
        private static final long serialVersionUID = 6928859904407185256L;
        private int maxSize;

        public LimitedSizeQueue(int size) {
            this.maxSize = size;
        }

        public boolean add(K k) {
            boolean added = super.add(k);
            while (added && size() > this.maxSize) {
                super.remove();
            }
            return added;
        }
    }

    private final class MemHandler extends Handler {
        public MemHandler(Looper looper) {
            super(looper);
        }

        public void removeAllMessage() {
            removeMessages(11);
        }

        public Message getMessage(int what, int arg1, Object obj) {
            return obtainMessage(what, arg1, 0, obj);
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 11:
                    IdleMemoryExecutor.this.handleRecycleMsg(msg);
                    return;
                default:
                    return;
            }
        }
    }

    private static final class MemoryResult {
        long mAvailable;
        boolean mEmergency;
        long mRequest;
        String mScene;
        String mTraceInfo;

        private MemoryResult() {
            this.mRequest = 0;
            this.mAvailable = 0;
            this.mEmergency = false;
            this.mScene = null;
            this.mTraceInfo = null;
        }
    }

    public IdleMemoryExecutor() {
        this.mIdleMemAppExecutor = null;
        this.mBigDataStore = BigDataStore.getInstance();
        this.mSysLowMemStateTracker = null;
        this.mIdleMemAppExecutor = new ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS, new LinkedBlockingQueue(1), new MemThreadFactory("iaware.mem.default"));
        this.mSysCpuOverLoadCnt = 0;
        this.mProtectCacheFlag = 0;
        this.mProtectCacheTimestamp = SystemClock.uptimeMillis();
        this.mSysLowMemStateTracker = new LimitedSizeQueue(3);
        for (int i = 0; i < 3; i++) {
            this.mSysLowMemStateTracker.add(Integer.valueOf(0));
        }
    }

    public void disableMemoryRecover() {
        this.mMemState.setStatus(0);
        if (this.mMemHandler != null) {
            this.mMemHandler.removeAllMessage();
        }
    }

    public void stopMemoryRecover() {
        this.mStopAction.set(true);
        disableMemoryRecover();
    }

    public void executeMemoryRecover(Bundle extras) {
        int event = extras.getInt("event");
        if (this.mMemHandler != null) {
            this.mMemHandler.sendMessage(this.mMemHandler.getMessage(11, event, extras));
            AwareLog.d(TAG, "executeMemoryRecover event=" + event);
        }
    }

    public void setMemHandlerThread(HandlerThread handlerThread) {
        AwareLog.d(TAG, "setHandler: object=" + handlerThread);
        this.mMemHandler = new MemHandler(handlerThread.getLooper());
    }

    private void executeIdleMemory(int event, Bundle extras) {
        this.mStopAction.set(false);
        MemoryScenePolicy idleMemoryScenePolicy = createPolicyByMemCpu(extras);
        if (idleMemoryScenePolicy == null) {
            this.mMemState.setStatus(0);
            return;
        }
        idleMemoryScenePolicy.setExtras(extras);
        try {
            this.mIdleMemAppExecutor.execute(new CalcRunnable(idleMemoryScenePolicy, false));
        } catch (RejectedExecutionException e) {
            AwareLog.e(TAG, "Failed to execute! reject");
        } catch (Exception e2) {
            AwareLog.e(TAG, "Failed to execute! reset");
        }
    }

    private MemoryScenePolicy createPolicyByMemCpu(Bundle extras) {
        MemoryScenePolicyList memoryScenePolicyList = MemoryExecutorServer.getInstance().getMemoryScenePolicyList();
        if (memoryScenePolicyList == null || extras == null) {
            AwareLog.e(TAG, "createPolicyByMemCpu memoryScenePolicyList null");
            return null;
        }
        MemoryResult memResult = calculateMemStatus();
        long beginTime = System.currentTimeMillis();
        updateSysMemStates(memResult.mAvailable);
        if (!isProtectCache() && isContLowMemState()) {
            setProtectCache(1);
        } else if (!isUnprotectCache() && isContNormalMemState() && isProtectLruTimeOk()) {
            setProtectCache(0);
        } else {
            AwareLog.d(TAG, "Bybass set protect cache. States: " + this.mProtectCacheFlag + ", " + Arrays.toString(this.mSysLowMemStateTracker.toArray()));
        }
        AwareLog.d(TAG, "setProtectCache time " + (System.currentTimeMillis() - beginTime));
        updateExtraFreeKbytes(memResult.mAvailable);
        if (memResult.mScene == null) {
            EventTracker.getInstance().trackEvent(1001, 0, 0, memResult.mTraceInfo);
            return null;
        }
        MemoryScenePolicy policy = memoryScenePolicyList.getMemoryScenePolicy(memResult.mScene);
        if (policy == null || !policy.canBeExecuted()) {
            EventTracker.getInstance().trackEvent(1001, 0, 0, memResult.mTraceInfo + " but " + (policy == null ? "null policy" : "policy can not execute"));
            return null;
        }
        if (!memResult.mEmergency) {
            CpuResult cpuResult = calculateCPUStatus(policy, memResult.mAvailable);
            extras.putInt("cpuLoad", cpuResult.mCpuLoad);
            extras.putBoolean("cpuBusy", cpuResult.mBusy);
            if (cpuResult.mBusy) {
                EventTracker.getInstance().trackEvent(1001, 0, 0, cpuResult.mTraceInfo);
                this.mSysCpuOverLoadCnt++;
                return null;
            }
        }
        this.mSysCpuOverLoadCnt = 0;
        extras.putLong("reqMem", memResult.mRequest);
        policy.setExtras(extras);
        EventTracker.getInstance().trackEvent(1003, 0, 0, memResult.mTraceInfo);
        return policy;
    }

    private void updateExtraFreeKbytes(long availableRam) {
        if (availableRam > 0) {
            if (availableRam >= MemoryConstant.getIdleMemory()) {
                MemoryUtils.writeExtraFreeKbytes(MemoryConstant.DEFAULT_EXTRA_FREE_KBYTES);
            } else {
                MemoryUtils.writeExtraFreeKbytes(MemoryConstant.getConfigExtraFreeKbytes());
            }
        }
    }

    private MemoryResult calculateMemStatus() {
        boolean z = true;
        MemoryResult memResult = new MemoryResult();
        if (canRecoverExecute("calculateMemStatus")) {
            this.mMemState.setStatus(1);
            long availableRam = MemoryReader.getInstance().getMemAvailable();
            if (availableRam <= 0) {
                AwareLog.e(TAG, "calculateMemStatus read availableRam err!" + availableRam);
                memResult.mTraceInfo = "read availableRam err";
                return memResult;
            }
            AwareLog.d(TAG, "calculateMemStatus memory availableRam=" + availableRam);
            memResult.mAvailable = availableRam;
            if (availableRam >= MemoryConstant.getIdleMemory()) {
                memResult.mTraceInfo = "memory enough " + availableRam;
                setBelowThresholdTime();
            } else if (availableRam >= MemoryConstant.getCriticalMemory() + MemoryConstant.RECLAIM_KILL_GAP_MEMORY) {
                memResult.mRequest = MemoryConstant.getIdleMemory() - availableRam;
                memResult.mScene = MemoryConstant.MEM_SCENE_IDLE;
                memResult.mTraceInfo = "memory low " + availableRam;
                setBelowThresholdTime();
            } else if (availableRam >= MemoryConstant.getCriticalMemory()) {
                memResult.mTraceInfo = "memory in Critical and Idle gap " + availableRam;
            } else {
                if (AwareConstant.CURRENT_USER_TYPE == 3) {
                    if (this.mBigDataStore.belowThresholdTimeBegin == 0) {
                        this.mBigDataStore.belowThresholdTimeBegin = SystemClock.elapsedRealtime();
                    }
                    BigDataStore bigDataStore = this.mBigDataStore;
                    bigDataStore.lowMemoryManageCount++;
                }
                memResult.mRequest = MemoryConstant.getCriticalMemory() - availableRam;
                memResult.mScene = MemoryConstant.MEM_SCENE_DEFAULT;
                if (availableRam > MemoryConstant.getEmergencyMemory()) {
                    z = false;
                }
                memResult.mEmergency = z;
                memResult.mTraceInfo = memResult.mEmergency ? "memory emergemcy " : "memory critical ";
                memResult.mTraceInfo += availableRam;
            }
            return memResult;
        }
        memResult.mTraceInfo = "DME not running or interrupted";
        return memResult;
    }

    private CpuResult calculateCPUStatus(MemoryScenePolicy policy, long mem) {
        CpuResult cpuResult = new CpuResult();
        if (canRecoverExecute("calculateCPUStatus")) {
            this.mMemState.setStatus(2);
            long cpuLoad = CpuReader.getInstance().getCpuPercent();
            if (cpuLoad < 0) {
                AwareLog.e(TAG, "calculateCPUStatus faild to read cpuload=" + cpuLoad);
                cpuResult.mTraceInfo = "read cpuload err";
                cpuResult.mBusy = true;
                return cpuResult;
            }
            cpuResult.mCpuLoad = (int) cpuLoad;
            AwareLog.d(TAG, "calculateCPUStatus cpu pressure " + cpuLoad);
            if (cpuLoad < policy.getCpuPressure(mem, MemoryConstant.getIdleThresHold(), this.mSysCpuOverLoadCnt)) {
                cpuResult.mTraceInfo = "cpuload " + cpuLoad;
                cpuResult.mBusy = false;
                return cpuResult;
            }
            if (cpuLoad < policy.getCpuPressure(mem, MemoryConstant.getNormalThresHold(), this.mSysCpuOverLoadCnt)) {
                cpuResult.mBusy = DataInputHandle.getInstance().getActiveStatus() != 2;
                cpuResult.mTraceInfo = cpuResult.mBusy ? "phone in active state" : "cpuload " + cpuLoad;
            } else {
                cpuResult.mBusy = true;
                cpuResult.mTraceInfo = "cpuload high " + cpuLoad;
            }
            return cpuResult;
        }
        cpuResult.mTraceInfo = "DME not running or interrupted";
        cpuResult.mBusy = true;
        return cpuResult;
    }

    private boolean canRecoverExecute(String functionName) {
        if (!this.mStopAction.get()) {
            return true;
        }
        AwareLog.w(TAG, AppHibernateCst.INVALID_PKG + functionName + " iaware not running, action=" + this.mStopAction.get());
        return false;
    }

    private void handleRecycleMsg(Message msg) {
        int event = msg.arg1;
        AwareLog.d(TAG, "MemHandler event=" + event);
        Bundle extras = msg.obj;
        if (extras == null) {
            AwareLog.d(TAG, "MemHandler extras is null");
            return;
        }
        long timestamp = extras.getLong("timeStamp");
        if (this.mMemState.getStatus() != 0) {
            EventTracker.getInstance().trackEvent(1001, event, timestamp, "mem " + this.mMemState.toString());
        } else if (MemoryExecutorServer.getInstance().getBigMemAppLaunching()) {
            AwareLog.d(TAG, "MemHandler big memory app is running");
        } else {
            if (event > 0 && timestamp > 0) {
                EventTracker.getInstance().trackEvent(1000, event, timestamp, null);
            }
            executeIdleMemory(event, extras);
        }
    }

    private void setBelowThresholdTime() {
        if (AwareConstant.CURRENT_USER_TYPE == 3) {
            long belowThresholdTimeEnd = SystemClock.elapsedRealtime();
            if (this.mBigDataStore.belowThresholdTimeBegin > 0 && belowThresholdTimeEnd - this.mBigDataStore.belowThresholdTimeBegin > 0) {
                BigDataStore bigDataStore = this.mBigDataStore;
                bigDataStore.belowThresholdTime += belowThresholdTimeEnd - this.mBigDataStore.belowThresholdTimeBegin;
                this.mBigDataStore.belowThresholdTimeBegin = 0;
            }
        }
    }

    private void updateSysMemStates(long memAvailKB) {
        if (memAvailKB >= 0) {
            int state;
            if (memAvailKB < MemoryConstant.getCriticalMemory()) {
                state = 1;
            } else {
                state = 0;
            }
            this.mSysLowMemStateTracker.add(Integer.valueOf(state));
        }
    }

    private boolean isContLowMemState() {
        for (int i = 0; i < 3; i++) {
            if (((Integer) this.mSysLowMemStateTracker.get(i)).intValue() != 1) {
                return false;
            }
        }
        return true;
    }

    private boolean isContNormalMemState() {
        for (int i = 0; i < 3; i++) {
            if (((Integer) this.mSysLowMemStateTracker.get(i)).intValue() != 0) {
                return false;
            }
        }
        return true;
    }

    private boolean isProtectCache() {
        return this.mProtectCacheFlag == 1;
    }

    private boolean isUnprotectCache() {
        return this.mProtectCacheFlag == 0;
    }

    private void setProtectCache(int state) {
        if (state == 1 || state == 0) {
            this.mProtectCacheFlag = state;
            this.mProtectCacheTimestamp = SystemClock.uptimeMillis();
            MemoryUtils.dynamicSetProtectLru(state);
            AwareLog.d(TAG, "setProtectCache state: " + state + ". States: " + this.mProtectCacheFlag + ", " + Arrays.toString(this.mSysLowMemStateTracker.toArray()));
            return;
        }
        AwareLog.d(TAG, "setProtectCache invalid state: " + state + ". States: " + this.mProtectCacheFlag + ", " + Arrays.toString(this.mSysLowMemStateTracker.toArray()));
    }

    private boolean isProtectLruTimeOk() {
        long now = SystemClock.uptimeMillis();
        boolean ret = now > this.mProtectCacheTimestamp && now - this.mProtectCacheTimestamp >= AwareAppMngSort.PREVIOUS_APP_DIRCACTIVITY_DECAYTIME;
        AwareLog.d(TAG, "isProtectLruTimeOk: " + ret + ". States: " + this.mProtectCacheFlag + ", " + Arrays.toString(this.mSysLowMemStateTracker.toArray()));
        return ret;
    }
}
