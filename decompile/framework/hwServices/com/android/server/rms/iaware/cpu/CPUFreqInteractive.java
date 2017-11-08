package com.android.server.rms.iaware.cpu;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.rms.iaware.AwareLog;
import com.android.server.PPPOEStateMachine;
import com.huawei.pgmng.plug.PGSdk.Sink;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

class CPUFreqInteractive {
    private static final int CHANGE_FREQUENCY_DELAYED = 3000;
    private static final int NON_SAVE_POWER_MODE = 1;
    private static final String TAG = "CPUFreqInteractive";
    private static AtomicBoolean mIsFeatureEnable = new AtomicBoolean(false);
    private static AtomicBoolean sIsSpecialScene = new AtomicBoolean(false);
    private CPUAppRecogMngProxy mCPUAppRecogMngProxy;
    private CPUFeature mCPUFeatureInstance;
    private Sink mFreqInteractiveSink;
    private FreqInteractiveHandler mHandler = new FreqInteractiveHandler();
    private int mPowerMode = 1;

    private class FreqInteractiveHandler extends Handler {
        private FreqInteractiveHandler() {
        }

        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case CPUFeature.MSG_SET_FREQUENCY /*112*/:
                    CPUFreqInteractive.this.setFrequency();
                    return;
                case CPUFeature.MSG_RESET_FREQUENCY /*113*/:
                    CPUFreqInteractive.this.resetFrequency();
                    return;
                default:
                    AwareLog.w(CPUFreqInteractive.TAG, "handleMessage default msg what = " + msg.what);
                    return;
            }
        }
    }

    private class FreqInteractiveSink implements Sink {
        private FreqInteractiveSink() {
        }

        public void onStateChanged(int stateType, int eventType, int pid, String pkg, int uid) {
            AwareLog.d(CPUFreqInteractive.TAG, "onStateChanged stateType = " + stateType + " eventType = " + eventType + " pid = " + pid + " pkg = " + pkg);
            CPUFreqInteractive.this.removeFreqMessages();
            if (CPUFreqInteractive.this.mCPUAppRecogMngProxy.isGameType(stateType)) {
                if (eventType == 1) {
                    CPUFreqInteractive.sIsSpecialScene.set(true);
                    CPUFreqInteractive.this.resetFreqMsg();
                    CpuThreadBoost.getInstance().resetBoostCpus();
                } else if (eventType == 2) {
                    CPUFreqInteractive.sIsSpecialScene.set(false);
                    CPUFreqInteractive.this.setFreqMsg();
                    CpuThreadBoost.getInstance().setBoostCpus();
                }
            } else if (!CPUFreqInteractive.this.mCPUAppRecogMngProxy.isVideoType(stateType)) {
            } else {
                if (stateType == 10015) {
                    CPUFreqInteractive.sIsSpecialScene.set(true);
                    CPUFreqInteractive.this.resetFreqMsg();
                    CpuThreadBoost.getInstance().resetBoostCpus();
                } else if (stateType == 10016) {
                    CPUFreqInteractive.sIsSpecialScene.set(false);
                    CPUFreqInteractive.this.setFreqMsg();
                    CpuThreadBoost.getInstance().setBoostCpus();
                }
            }
        }
    }

    public CPUFreqInteractive(CPUFeature feature, Context context) {
        this.mCPUFeatureInstance = feature;
        this.mCPUAppRecogMngProxy = new CPUAppRecogMngProxy(context);
        this.mFreqInteractiveSink = new FreqInteractiveSink();
    }

    private boolean isBootCompleted() {
        return PPPOEStateMachine.PHASE_INITIALIZE.equals(SystemProperties.get("sys.boot_completed", PPPOEStateMachine.PHASE_DEAD));
    }

    public void enable() {
        if (mIsFeatureEnable.get()) {
            AwareLog.e(TAG, "CPUFreqInteractive has already enable!");
            return;
        }
        mIsFeatureEnable.set(true);
        if (CPUPowerMode.isPerformanceMode()) {
            if (isBootCompleted()) {
                this.mHandler.sendEmptyMessageDelayed(CPUFeature.MSG_SET_FREQUENCY, 0);
            } else {
                this.mHandler.sendEmptyMessageDelayed(CPUFeature.MSG_SET_FREQUENCY, 3000);
            }
        }
    }

    public void startGameStateMoniter() {
        this.mCPUAppRecogMngProxy.register(this.mFreqInteractiveSink);
    }

    public void stopGameStateMoniter() {
        this.mCPUAppRecogMngProxy.unregister(this.mFreqInteractiveSink);
    }

    public void disable() {
        if (mIsFeatureEnable.get()) {
            mIsFeatureEnable.set(false);
            this.mHandler.removeMessages(CPUFeature.MSG_SET_FREQUENCY);
            if (!CPUPowerMode.getInstance().isSuperPowerSave()) {
                resetFrequency();
            }
            return;
        }
        AwareLog.e(TAG, "CPUFreqInteractive has already disable!");
    }

    private void removeFreqMessages() {
        this.mHandler.removeMessages(CPUFeature.MSG_SET_FREQUENCY);
        this.mHandler.removeMessages(CPUFeature.MSG_RESET_FREQUENCY);
    }

    private void setFrequency() {
        long time = System.currentTimeMillis();
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(CPUFeature.MSG_SET_FREQUENCY);
        int resCode = this.mCPUFeatureInstance.sendPacket(buffer);
        if (resCode != 1) {
            AwareLog.e(TAG, "setFrequency sendPacket failed, send error code:" + resCode);
        }
        CpuDumpRadar.getInstance().insertDumpInfo(time, "setFrequency()", "set cpu frequency", CpuDumpRadar.STATISTICS_CHG_FREQ_POLICY);
    }

    private void resetFrequency() {
        long time = System.currentTimeMillis();
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putInt(CPUFeature.MSG_RESET_FREQUENCY);
        buffer.putInt(this.mPowerMode);
        int resCode = this.mCPUFeatureInstance.sendPacket(buffer);
        if (resCode != 1) {
            AwareLog.e(TAG, "resetFrequency sendPacket failed, send error code:" + resCode);
        }
        CpuDumpRadar.getInstance().insertDumpInfo(time, "resetFrequency()", "reset cpu frequency", CpuDumpRadar.STATISTICS_RESET_FREQ_POLICY);
    }

    private static boolean isSatisfied() {
        return mIsFeatureEnable.get() ? CPUPowerMode.isPerformanceMode() : false;
    }

    public static boolean isFGSpecialScene() {
        return sIsSpecialScene.get();
    }

    public void setFreqMsg() {
        if (isSatisfied()) {
            this.mHandler.sendEmptyMessage(CPUFeature.MSG_SET_FREQUENCY);
        }
    }

    public void resetFreqMsg() {
        if (isSatisfied()) {
            this.mHandler.sendEmptyMessage(CPUFeature.MSG_RESET_FREQUENCY);
        }
    }

    public void notifyToChangeFreq(int msg, int delayTime, int powerMode) {
        if (mIsFeatureEnable.get()) {
            removeFreqMessages();
            this.mHandler.sendEmptyMessageDelayed(msg, (long) delayTime);
            this.mPowerMode = powerMode;
        }
    }
}
