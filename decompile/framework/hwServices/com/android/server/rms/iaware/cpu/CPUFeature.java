package com.android.server.rms.iaware.cpu;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.rms.iaware.AwareConstant;
import android.rms.iaware.AwareConstant.FeatureType;
import android.rms.iaware.AwareConstant.ResourceType;
import android.rms.iaware.AwareLog;
import android.rms.iaware.CollectData;
import android.rms.iaware.DumpData;
import android.rms.iaware.IAwaredConnection;
import android.rms.iaware.StatisticsData;
import android.service.vr.IVrManager;
import android.service.vr.IVrStateCallbacks;
import android.service.vr.IVrStateCallbacks.Stub;
import com.android.server.PPPOEStateMachine;
import com.android.server.am.HwActivityManagerService;
import com.android.server.rms.iaware.IRDataRegister;
import com.android.server.rms.iaware.feature.RFeature;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class CPUFeature extends RFeature {
    private static final int BIGDATA_RECORD_PERIOD = 600;
    private static final int CONNECT_VR_DELAYED = 3000;
    private static final int CYCLE_MAX_NUM = 5;
    private static final int MSG_BASE_VALUE = 100;
    public static final int MSG_BOOST_KILL_SWITCH = 107;
    public static final int MSG_CPUCTL_SUBSWITCH = 140;
    public static final int MSG_CPUFEATURE_OFF = 114;
    public static final int MSG_MOVETO_BACKGROUND = 105;
    public static final int MSG_MOVETO_K_BACKGROUND = 104;
    public static final int MSG_PROCESS_GROUP_CHANGE = 106;
    public static final int MSG_RESET_BOOST_CPUS = 136;
    public static final int MSG_RESET_FREQUENCY = 113;
    public static final int MSG_RESET_TOP_APP_CPUSET = 117;
    public static final int MSG_SCREEN_OFF = 102;
    public static final int MSG_SCREEN_ON = 101;
    public static final int MSG_SET_BOOST_CPUS = 135;
    public static final int MSG_SET_CPUCONFIG = 124;
    public static final int MSG_SET_CPUSETCONFIG_SCREENOFF = 127;
    public static final int MSG_SET_CPUSETCONFIG_SCREENON = 126;
    public static final int MSG_SET_CPUSETCONFIG_VR = 125;
    public static final int MSG_SET_FG_CGROUP = 139;
    public static final int MSG_SET_FREQUENCY = 112;
    public static final int MSG_SET_INTERACTIVE_NOSAVE = 130;
    public static final int MSG_SET_INTERACTIVE_SAVE = 129;
    public static final int MSG_SET_INTERACTIVE_SET = 128;
    public static final int MSG_SET_INTERACTIVE_SPSAVE = 131;
    public static final int MSG_SET_LIMIT_BG_CGROUP = 138;
    public static final int MSG_SET_LIMIT_FG_CGROUP = 137;
    public static final int MSG_SET_TOP_APP_CPUSET = 116;
    public static final int MSG_START_BIGDATAPROCRECORD = 108;
    public static final int MSG_STOP_BIGDATAPROCRECORD = 109;
    public static final int MSG_THERMAL_LIMITED = 150;
    public static final int MSG_THERMAL_NOT_LIMITED = 151;
    public static final int MSG_THREAD_BOOST = 115;
    public static final int MSG_UI_BOOST = 118;
    private static final int MSG_VR_CONNECT = 134;
    public static final int MSG_VR_OFF = 123;
    public static final int MSG_VR_ON = 122;
    private static final int SCREEN_OFF_DELAYED = 5000;
    public static final int SEND_BYTE_NULL = -1;
    public static final int SEND_RETRY_TIME = 2;
    public static final int SEND_RETRY_TIME_OUT = -2;
    public static final int SEND_SUCCESS = 1;
    public static final int SWITCH_CPUCTL_FG_DETECT = 1024;
    public static final int SWITCH_FREQ_INTERACTIVE = 2;
    public static final int SWITCH_LOG = 1;
    public static final int SWITCH_THREAD_BOOST = 16;
    public static final int SWITCH_TOPAPP_BOOST = 32;
    public static final int SWITCH_VR_MODE = 256;
    private static final String TAG = "CPUFeature";
    private static boolean mCPUSetEnable;
    private CPUFeatureHandler mCPUFeatureHandler = new CPUFeatureHandler();
    private CPUFreqInteractive mCPUFreqInteractive;
    private CPUHighFgControl mCPUHighFgControl;
    private CPUProcessInherit mCPUProcessInherit = null;
    private CPUXmlConfiguration mCPUXmlConfiguration;
    private int mCycleNum = 0;
    private int mFeatureFlag = 0;
    private IVrManager mVRManager = null;
    private final IVrStateCallbacks mVrStateCallback = new Stub() {
        public void onVrStateChanged(boolean enable) throws RemoteException {
            AwareLog.d(CPUFeature.TAG, "vr state change:" + enable);
            CPUFeature.this.processVRConnected(enable);
        }
    };

    public class CPUFeatureHandler extends Handler {
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 101:
                    CPUFeature.this.doScreenOn();
                    CPUFeature.this.mCPUFreqInteractive.setFreqMsg();
                    return;
                case 102:
                    CPUFeature.this.doScreenOff();
                    CPUFeature.this.mCPUFreqInteractive.resetFreqMsg();
                    return;
                case CPUFeature.MSG_UI_BOOST /*118*/:
                    CpuThreadBoost.getInstance().uiBoost(msg.arg1, msg.obj);
                    return;
                case CPUFeature.MSG_VR_CONNECT /*134*/:
                    CPUFeature.this.connectVRListner();
                    return;
                case CPUFeature.MSG_SET_BOOST_CPUS /*135*/:
                    CPUFeature.this.sendPacketByMsgCode(CPUFeature.MSG_SET_BOOST_CPUS);
                    return;
                case CPUFeature.MSG_RESET_BOOST_CPUS /*136*/:
                    CPUFeature.this.sendPacketByMsgCode(CPUFeature.MSG_RESET_BOOST_CPUS);
                    return;
                case CPUFeature.MSG_CPUCTL_SUBSWITCH /*140*/:
                    CPUFeature.this.doSendCpuCtlSwitchMsg();
                    return;
                default:
                    AwareLog.e(CPUFeature.TAG, "error msg what = " + msg.what);
                    return;
            }
        }
    }

    public void featureSwitch(int r1, boolean r2) {
        /* JADX: method processing error */
/*
Error: jadx.core.utils.exceptions.DecodeException: Load method exception in method: com.android.server.rms.iaware.cpu.CPUFeature.featureSwitch(int, boolean):void
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
        throw new UnsupportedOperationException("Method not decompiled: com.android.server.rms.iaware.cpu.CPUFeature.featureSwitch(int, boolean):void");
    }

    private boolean isFeatureEnable(int f) {
        return mCPUSetEnable && (this.mFeatureFlag & f) != 0;
    }

    public CPUFeature(Context context, FeatureType type, IRDataRegister dataRegister) {
        super(context, type, dataRegister);
        this.mCPUFreqInteractive = new CPUFreqInteractive(this, context);
        this.mCPUHighFgControl = new CPUHighFgControl(this);
    }

    public ArrayList<DumpData> getDumpData(int time) {
        CPUKeyBackground.getInstance().insertCgroupProcsPidList();
        return CpuDumpRadar.getInstance().getDumpData(time);
    }

    public ArrayList<StatisticsData> getStatisticsData() {
        return CpuDumpRadar.getInstance().getStatisticsData();
    }

    public boolean reportData(CollectData data) {
        if (!mCPUSetEnable) {
            return false;
        }
        if (data == null) {
            AwareLog.e(TAG, "reportData: data is null!");
            return false;
        }
        int resId = data.getResId();
        if (resId == ResourceType.RESOURCE_SCREEN_ON.ordinal()) {
            removeScreenMessages();
            this.mCPUFeatureHandler.sendEmptyMessageDelayed(101, 0);
        } else if (resId == ResourceType.RESOURCE_SCREEN_OFF.ordinal()) {
            removeScreenMessages();
            this.mCPUFeatureHandler.sendEmptyMessageDelayed(102, 5000);
        }
        return true;
    }

    private void subscribeResourceTypes() {
        this.mIRDataRegister.subscribeData(ResourceType.RESOURCE_SCREEN_ON, this.mFeatureType);
        this.mIRDataRegister.subscribeData(ResourceType.RESOURCE_SCREEN_OFF, this.mFeatureType);
    }

    private void unsubscribeResourceTypes() {
        this.mIRDataRegister.unSubscribeData(ResourceType.RESOURCE_SCREEN_ON, this.mFeatureType);
        this.mIRDataRegister.unSubscribeData(ResourceType.RESOURCE_SCREEN_OFF, this.mFeatureType);
    }

    private void enableVRMode() {
        if (isFeatureEnable(256)) {
            connectVRListner();
        }
    }

    private void notifyCpusetSwitch(boolean enable) {
        HwActivityManagerService mHwAms = HwActivityManagerService.self();
        if (mHwAms != null) {
            mHwAms.setCpusetSwitch(enable);
        }
    }

    private void enableThreadBoost() {
        if (isFeatureEnable(16)) {
            CpuThreadBoost.getInstance().start(this, this.mCPUFeatureHandler, this.mContext);
        }
    }

    private void enableCPUHighFgControl() {
        if (isFeatureEnable(1024)) {
            this.mCPUHighFgControl.start();
            handleSendMsg(MSG_CPUCTL_SUBSWITCH);
        }
    }

    private void enableFreqInteractive() {
        if (isFeatureEnable(2)) {
            this.mCPUFreqInteractive.enable();
        }
    }

    private void startGameStateMoniter() {
        if (isFeatureEnable(2) || isFeatureEnable(16)) {
            this.mCPUFreqInteractive.startGameStateMoniter();
        }
    }

    private void enableTopAppBoost() {
        if (isFeatureEnable(32)) {
            CPUFeatureAMSCommunicator.getInstance().start(this);
        }
    }

    private void enableBigDataRecord() {
        if (AwareConstant.CURRENT_USER_TYPE == 3) {
            startBigDataProcRecord();
        }
    }

    public boolean enable() {
        if (mCPUSetEnable) {
            AwareLog.e(TAG, "cpuset has already enabled!");
            return true;
        }
        if (this.mCPUXmlConfiguration == null) {
            this.mCPUXmlConfiguration = new CPUXmlConfiguration();
        }
        this.mCPUXmlConfiguration.startSetProperty(this);
        setEnable(true);
        notifyCpusetSwitch(true);
        CPUKeyBackground.getInstance().start(this);
        setBoostKillSwitch(true);
        CPUPowerMode.getInstance().enable(this.mCPUFreqInteractive, this.mContext);
        try {
            this.mFeatureFlag = Integer.parseInt(SystemProperties.get("persist.sys.cpuset.subswitch", PPPOEStateMachine.PHASE_DEAD));
        } catch (IllegalArgumentException e) {
            AwareLog.e(TAG, "enable parseInt catch exception msg = " + e.getMessage());
        }
        enableThreadBoost();
        if (this.mCPUProcessInherit == null) {
            this.mCPUProcessInherit = new CPUProcessInherit();
        }
        this.mCPUProcessInherit.registerPorcessObserver();
        subscribeResourceTypes();
        enableCPUHighFgControl();
        if (isScreenOn()) {
            doScreenOn();
        }
        enableFreqInteractive();
        startGameStateMoniter();
        CPUResourceConfigControl.getInstance().enable();
        enableTopAppBoost();
        enableBigDataRecord();
        enableVRMode();
        cpuLog("CPUFeature enabled");
        return true;
    }

    private void startBigDataProcRecord() {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putInt(MSG_START_BIGDATAPROCRECORD);
        buffer.putInt(600);
        int resCode = sendPacket(buffer);
        if (resCode != 1) {
            AwareLog.e(TAG, "send startBigDataProcRecord failed, send error code:" + resCode);
        }
    }

    private void stopBigDataProcRecord() {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(MSG_STOP_BIGDATAPROCRECORD);
        int resCode = sendPacket(buffer);
        if (resCode != 1) {
            AwareLog.e(TAG, "send stopBigDataProcRecord failed, send error code:" + resCode);
        }
    }

    private void stopGameStateMoniter() {
        if (isFeatureEnable(2) || isFeatureEnable(16)) {
            this.mCPUFreqInteractive.stopGameStateMoniter();
        }
    }

    private void disableTopAppBoost() {
        if (isFeatureEnable(32)) {
            CPUFeatureAMSCommunicator.getInstance().stop();
        }
    }

    private void disableFreqInteractive() {
        if (isFeatureEnable(2)) {
            this.mCPUFreqInteractive.disable();
        }
    }

    private void disableCPUHighFgControl() {
        if (isFeatureEnable(1024)) {
            this.mCPUHighFgControl.stop();
            handleSendMsg(MSG_CPUCTL_SUBSWITCH);
        }
    }

    private void disableThreadBoost() {
        if (isFeatureEnable(16)) {
            CpuThreadBoost.getInstance().stop();
        }
    }

    private void disbaleBigDataRecord() {
        if (AwareConstant.CURRENT_USER_TYPE == 3) {
            stopBigDataProcRecord();
        }
    }

    public boolean disable() {
        if (mCPUSetEnable) {
            stopGameStateMoniter();
            disableFreqInteractive();
            disableTopAppBoost();
            disableCPUHighFgControl();
            cpuLog("CPUFeature destroyed");
            CPUKeyBackground.getInstance().destroy();
            disableThreadBoost();
            CPUPowerMode.getInstance().disable();
            setBoostKillSwitch(false);
            if (this.mCPUProcessInherit != null) {
                this.mCPUProcessInherit.unregisterPorcessObserver();
            }
            doFeatureOff();
            setEnable(false);
            notifyCpusetSwitch(false);
            disbaleBigDataRecord();
            unsubscribeResourceTypes();
            if (isFeatureEnable(256)) {
                callUnRegisterVRListener();
                this.mCPUFeatureHandler.removeMessages(MSG_VR_CONNECT);
            }
            CPUResourceConfigControl.getInstance().disable();
            return true;
        }
        AwareLog.e(TAG, "cpuset has already disabled!");
        return true;
    }

    public void cpuLog(String msg) {
        if (isFeatureEnable(1)) {
            AwareLog.d(TAG, msg);
        }
    }

    private void setEnable(boolean enable) {
        try {
            SystemProperties.set("persist.sys.cpuset.enable", enable ? PPPOEStateMachine.PHASE_INITIALIZE : PPPOEStateMachine.PHASE_DEAD);
            mCPUSetEnable = enable;
        } catch (Exception e) {
            AwareLog.e(TAG, "setEnable catch exception!");
        }
    }

    public static boolean isCpusetEnable() {
        return mCPUSetEnable;
    }

    private int doScreenOn() {
        long time = System.currentTimeMillis();
        cpuLog("get screen on event");
        sendPacketByMsgCode(101);
        CpuDumpRadar.getInstance().insertDumpInfo(time, "doScreenOn()", "enable cpuset", CpuDumpRadar.STATISTICS_SCREEN_ON_POLICY);
        return 0;
    }

    private int doScreenOff() {
        long time = System.currentTimeMillis();
        cpuLog("get screen off event");
        sendPacketByMsgCode(102);
        CpuDumpRadar.getInstance().insertDumpInfo(time, "doScreenOff()", "disable cpuset", CpuDumpRadar.STATISTICS_SCREEN_OFF_POLICY);
        return 0;
    }

    private int doFeatureOff() {
        sendPacketByMsgCode(MSG_CPUFEATURE_OFF);
        return 0;
    }

    public synchronized int sendPacket(ByteBuffer buffer) {
        if (buffer == null) {
            return -1;
        }
        int retry = 2;
        do {
            if (IAwaredConnection.getInstance().sendPacket(buffer.array(), 0, buffer.position())) {
                return 1;
            }
            retry--;
        } while (retry > 0);
        return -2;
    }

    private boolean isScreenOn() {
        return ((PowerManager) this.mContext.getSystemService("power")).isInteractive();
    }

    private void removeScreenMessages() {
        this.mCPUFeatureHandler.removeMessages(101);
        this.mCPUFeatureHandler.removeMessages(102);
    }

    private int setBoostKillSwitch(boolean isEnable) {
        if (!isCpusetEnable()) {
            return 0;
        }
        int i;
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putInt(MSG_BOOST_KILL_SWITCH);
        if (isEnable) {
            i = 1;
        } else {
            i = 0;
        }
        buffer.putInt(i);
        int resCode = sendPacket(buffer);
        if (resCode != 1) {
            AwareLog.e(TAG, "setBoostKillSwitch sendPacket failed, isEnable:" + isEnable + ",send error code:" + resCode);
        }
        return 0;
    }

    public void sendPacketByMsgCode(int msg) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(msg);
        int resCode = sendPacket(buffer);
        if (resCode != 1) {
            AwareLog.e(TAG, "sendPacketByMsgCode sendPacket failed, msg:" + msg + ",send error code:" + resCode);
        }
    }

    private void processVRConnected(boolean enable) {
        if (enable) {
            if (isFeatureEnable(32)) {
                CPUFeatureAMSCommunicator.getInstance().stop();
            }
            sendPacketByMsgCode(122);
            return;
        }
        if (isFeatureEnable(32)) {
            CPUFeatureAMSCommunicator.getInstance().start(this);
        }
        if (isScreenOn()) {
            sendPacketByMsgCode(123);
        }
    }

    private void connectVRListner() {
        this.mVRManager = IVrManager.Stub.asInterface(ServiceManager.getService("vrmanager"));
        if (this.mVRManager != null || this.mCycleNum >= 5) {
            this.mCycleNum = 0;
            callRegisterVRListener();
            return;
        }
        this.mCycleNum++;
        this.mCPUFeatureHandler.removeMessages(MSG_VR_CONNECT);
        this.mCPUFeatureHandler.sendEmptyMessageDelayed(MSG_VR_CONNECT, 3000);
    }

    private void callRegisterVRListener() {
        if (this.mVRManager != null) {
            try {
                this.mVRManager.registerListener(this.mVrStateCallback);
            } catch (RemoteException e) {
                AwareLog.e(TAG, "failed to register vr mode state listner:" + e);
            }
        }
    }

    private void callUnRegisterVRListener() {
        if (this.mVRManager != null) {
            try {
                this.mVRManager.unregisterListener(this.mVrStateCallback);
            } catch (RemoteException e) {
                AwareLog.e(TAG, "failed to unregister vr mode state listner:" + e);
            }
        }
    }

    private void doSendCpuCtlSwitchMsg() {
        sendPacketByMsgValueCode(MSG_CPUCTL_SUBSWITCH, isFeatureEnable(1024) ? 1 : 0);
    }

    private void handleSendMsg(int msg) {
        this.mCPUFeatureHandler.removeMessages(msg);
        this.mCPUFeatureHandler.sendEmptyMessage(msg);
    }

    private void sendPacketByMsgValueCode(int msg, int value) {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putInt(msg);
        buffer.putInt(value);
        int resCode = sendPacket(buffer);
        if (resCode != 1) {
            AwareLog.e(TAG, "sendPacketByMsgPidCode sendPacket failed, msg, value:" + msg + value + ", send error code:" + resCode);
        }
    }
}
