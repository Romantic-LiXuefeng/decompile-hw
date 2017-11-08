package com.android.server.rms.iaware;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.rms.iaware.AwareConstant.FeatureType;
import android.rms.iaware.AwareConstant.ResourceType;
import android.rms.iaware.AwareLog;
import android.rms.iaware.CollectData;
import android.rms.iaware.DumpData;
import android.rms.iaware.IAwaredConnection;
import android.rms.iaware.ICMSManager;
import android.rms.iaware.ICMSManager.Stub;
import android.rms.iaware.IReportDataCallback;
import android.rms.iaware.StatisticsData;
import android.util.Xml;
import com.android.internal.os.SomeArgs;
import com.android.server.pfw.autostartup.comm.XmlConst.ControlScope;
import com.android.server.rms.algorithm.AwareUserHabit;
import com.android.server.rms.iaware.feature.RFeature;
import huawei.cust.HwCfgFilePolicy;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.xmlpull.v1.XmlPullParser;

public class RFeatureManager implements IRDataRegister {
    private static final String CUST_PATH_DIR = "/data/cust/xml";
    private static final int FEATURE_MAX_DUMP_SIZE = 5;
    public static final int FEATURE_STATUS_DISABLE = 0;
    public static final int FEATURE_STATUS_ENABLE = 1;
    private static final String FEATURE_SWITCH_FILE_NAME = "iAwareFeatureSwitch.xml";
    private static final int MSG_CONFIG_UPDATE = 3;
    private static final int MSG_ENABLE_DISABLE_FEATURE = 1;
    private static final int MSG_INIT_FEATURE = 0;
    private static final int MSG_REPORT_DATA = 2;
    private static final int MSG_REPORT_DATA_CALLBACK = 4;
    private static final String TAG = "RFeatureManager";
    private static final int UNKNOWN_FEATURE_STATUS = -1;
    private static final String USER_HABIT_RECV_TRAIN_COMPLETED_PERMISSION = "com.huawei.iaware.userhabit.USERHABIT_PERMISSION";
    private static final String USER_HABIT_TRAIN_COMPLETED_ACTION = "com.huawei.iaware.userhabit.TRAIN_COMPLETED";
    private static final String XML_TAG_FEATURE_ID = "featureid";
    private static final String XML_TAG_FEATURE_SWITCH = "switch";
    private static final String XML_TAG_ITEM = "item";
    private static final String mAPPMngFeatureName = "com.android.server.rms.iaware.feature.APPMngFeature";
    private static final FeatureType mAPPMngFeatureType = FeatureType.FEATURE_APPMNG;
    private Context mContext = null;
    private final String[] mFeatureNames = new String[]{"com.android.server.rms.iaware.cpu.CPUFeature", "com.android.server.rms.iaware.srms.ResourceFeature", "com.android.server.rms.iaware.feature.APPHiberFeature", "com.android.server.rms.iaware.feature.MemoryFeature", "com.android.server.rms.iaware.feature.IOFeature", "com.android.server.rms.iaware.feature.DefragFeature"};
    private final FeatureType[] mFeatureTypes = new FeatureType[]{FeatureType.FEATURE_CPU, FeatureType.FEATURE_RESOURCE, FeatureType.FEATURE_APPHIBER, FeatureType.FEATURE_MEMORY, FeatureType.FEATURE_IO, FeatureType.FEATURE_DEFRAG};
    private boolean mIAwareEnabled = false;
    private XmlPullParser mParser = null;
    private IAwareDeviceStateReceiver mReceiver = null;
    private Map<FeatureType, FeatureWrapper> mRegisteredFeatures = null;
    private ReportedDataHandler mReportedDataHandler;
    private Map<ResourceType, ArrayList<FeatureType>> mSubscribeDataMap = null;
    private final int mSubscribedFeatureTypeNum;
    private final FeatureType[] mSubscribedFeatureTypes;

    private static class FeatureWrapper {
        private boolean mFeatureEnabled;
        private RFeature mFeatureInstance;

        public FeatureWrapper(RFeature instance, boolean status) {
            this.mFeatureInstance = instance;
            this.mFeatureEnabled = status;
        }

        public void setFeatureEnabled(boolean enable) {
            this.mFeatureEnabled = enable;
        }

        public boolean getFeatureEnabled() {
            return this.mFeatureEnabled;
        }

        public RFeature getFeatureInstance() {
            return this.mFeatureInstance;
        }
    }

    private class IAwareDeviceStateReceiver extends BroadcastReceiver {
        private IAwareDeviceStateReceiver() {
        }

        public void onReceive(Context context, Intent intent) {
            if (context == null || intent == null) {
                AwareLog.e(RFeatureManager.TAG, "BroadcastReceiver error parameters!");
                return;
            }
            String action = intent.getAction();
            if ("android.intent.action.SCREEN_ON".equals(action)) {
                RFeatureManager.this.reportData(new CollectData(ResourceType.getReousrceId(ResourceType.RESOURCE_SCREEN_ON), System.currentTimeMillis(), action));
            } else if ("android.intent.action.SCREEN_OFF".equals(action)) {
                RFeatureManager.this.reportData(new CollectData(ResourceType.getReousrceId(ResourceType.RESOURCE_SCREEN_OFF), System.currentTimeMillis(), action));
            } else if ("android.intent.action.BOOT_COMPLETED".equals(action)) {
                RFeatureManager.this.reportData(new CollectData(ResourceType.getReousrceId(ResourceType.RESOURCE_BOOT_COMPLETED), System.currentTimeMillis(), action));
            } else if ("android.intent.action.PACKAGE_REMOVED".equals(action)) {
                if (intent.getData() != null) {
                    resIDHabitStat = ResourceType.getReousrceId(ResourceType.RESOURCE_USERHABIT);
                    curtime = System.currentTimeMillis();
                    pkgName = intent.getData().getSchemeSpecificPart();
                    bdl = new Bundle();
                    bdl.putString(AwareUserHabit.USERHABIT_PACKAGE_NAME, pkgName);
                    bdl.putInt(AwareUserHabit.USERHABIT_INSTALL_APP_UPDATE, 2);
                    RFeatureManager.this.reportData(new CollectData(resIDHabitStat, curtime, bdl));
                }
            } else if ("android.intent.action.PACKAGE_ADDED".equals(action)) {
                if (intent.getData() != null) {
                    resIDHabitStat = ResourceType.getReousrceId(ResourceType.RESOURCE_USERHABIT);
                    curtime = System.currentTimeMillis();
                    pkgName = intent.getData().getSchemeSpecificPart();
                    bdl = new Bundle();
                    bdl.putString(AwareUserHabit.USERHABIT_PACKAGE_NAME, pkgName);
                    bdl.putInt(AwareUserHabit.USERHABIT_INSTALL_APP_UPDATE, 1);
                    RFeatureManager.this.reportData(new CollectData(resIDHabitStat, curtime, bdl));
                }
            } else if (RFeatureManager.USER_HABIT_TRAIN_COMPLETED_ACTION.equals(action)) {
                resIDHabitStat = ResourceType.getReousrceId(ResourceType.RESOURCE_USERHABIT);
                curtime = System.currentTimeMillis();
                bdl = new Bundle();
                bdl.putInt(AwareUserHabit.USERHABIT_INSTALL_APP_UPDATE, 3);
                RFeatureManager.this.reportData(new CollectData(resIDHabitStat, curtime, bdl));
            }
        }
    }

    private final class ReportedDataHandler extends Handler {
        public ReportedDataHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            ICMSManager awareservice;
            switch (msg.what) {
                case 0:
                    registerFeatures(msg.getData());
                    awareservice = Stub.asInterface(ServiceManager.getService("IAwareCMSService"));
                    if (awareservice == null) {
                        AwareLog.e(RFeatureManager.TAG, "get IAwareCMSService failed.");
                        break;
                    }
                    try {
                        RFeatureManager.this.mIAwareEnabled = awareservice.isIAwareEnabled();
                        if (RFeatureManager.this.mIAwareEnabled) {
                            registerAPPMngFeature(1);
                        } else {
                            registerAPPMngFeature(0);
                        }
                        if (RFeatureManager.this.mIAwareEnabled && RFeatureManager.this.mReceiver == null) {
                            RFeatureManager.this.mReceiver = new IAwareDeviceStateReceiver();
                            RFeatureManager.this.registerRDABroadcastReceiver();
                            AwareLog.d(RFeatureManager.TAG, "register RDA broadcast Receiver");
                            break;
                        }
                    } catch (RemoteException e) {
                        AwareLog.e(RFeatureManager.TAG, "call isIAwareEnabled failed." + e.getMessage());
                        break;
                    }
                case 1:
                    FeatureType featureType = FeatureType.getFeatureType(msg.arg2);
                    AwareLog.d(RFeatureManager.TAG, "handler message featureType:" + featureType.name() + "  status:" + msg.arg1);
                    if (RFeatureManager.this.mRegisteredFeatures.containsKey(featureType)) {
                        boolean status = msg.arg1 == 1;
                        FeatureWrapper feature = (FeatureWrapper) RFeatureManager.this.mRegisteredFeatures.get(featureType);
                        if (feature != null && feature.getFeatureInstance() != null) {
                            if (feature.getFeatureEnabled() != status) {
                                if (status) {
                                    feature.getFeatureInstance().enable();
                                } else {
                                    feature.getFeatureInstance().disable();
                                }
                                feature.setFeatureEnabled(status);
                                awareservice = Stub.asInterface(ServiceManager.getService("IAwareCMSService"));
                                if (awareservice == null) {
                                    AwareLog.e(RFeatureManager.TAG, "get IAwareCMSService failed.");
                                    break;
                                }
                                try {
                                    RFeatureManager.this.mIAwareEnabled = awareservice.isIAwareEnabled();
                                    if (!RFeatureManager.this.mIAwareEnabled || RFeatureManager.this.mReceiver != null) {
                                        if (!(RFeatureManager.this.mIAwareEnabled || RFeatureManager.this.mReceiver == null)) {
                                            enableAPPMngFeature(false);
                                            RFeatureManager.this.mContext.unregisterReceiver(RFeatureManager.this.mReceiver);
                                            AwareLog.d(RFeatureManager.TAG, "unregister RDA broadcast Receiver");
                                            RFeatureManager.this.mReceiver = null;
                                            RFeatureManager.this.mReportedDataHandler.removeMessages(2);
                                            IAwaredConnection.getInstance().destroy();
                                            break;
                                        }
                                    }
                                    enableAPPMngFeature(true);
                                    RFeatureManager.this.mReceiver = new IAwareDeviceStateReceiver();
                                    RFeatureManager.this.registerRDABroadcastReceiver();
                                    AwareLog.d(RFeatureManager.TAG, "register RDA broadcast Receiver");
                                    break;
                                } catch (RemoteException e2) {
                                    AwareLog.e(RFeatureManager.TAG, "call isIAwareEnabled failed." + e2.getMessage());
                                    break;
                                }
                            }
                            return;
                        }
                        AwareLog.e(RFeatureManager.TAG, "handleMessage ENABLE_DISABLE_FEATURE feature null: " + featureType.name());
                        return;
                    }
                    break;
                case 2:
                    deliveryDataToFeatures(msg.obj, null);
                    break;
                case 3:
                    notifyConfigUpdate();
                    break;
                case 4:
                    SomeArgs args = msg.obj;
                    CollectData collectData = args.arg1;
                    IReportDataCallback callback = args.arg2;
                    args.recycle();
                    deliveryDataToFeatures(collectData, callback);
                    break;
            }
        }

        private void enableAPPMngFeature(boolean enabled) {
            AwareLog.d(RFeatureManager.TAG, "Enable APPMng Feature enable = " + enabled);
            FeatureWrapper feature = (FeatureWrapper) RFeatureManager.this.mRegisteredFeatures.get(RFeatureManager.mAPPMngFeatureType);
            if (feature == null || feature.getFeatureInstance() == null) {
                AwareLog.e(RFeatureManager.TAG, "enableAPPMngFeature feature null!");
            } else if (feature.getFeatureEnabled() != enabled) {
                if (enabled) {
                    feature.getFeatureInstance().enable();
                } else {
                    feature.getFeatureInstance().disable();
                }
                feature.setFeatureEnabled(enabled);
            }
        }

        private void registerFeatures(Bundle bundle) {
            int featureNum = RFeatureManager.this.mFeatureNames.length;
            if (featureNum == 0) {
                AwareLog.e(RFeatureManager.TAG, "There is no feature will be registered.");
                return;
            }
            for (int index = 0; index < featureNum; index++) {
                FeatureWrapper wrapper = createFeatureWrapper(RFeatureManager.this.mFeatureNames[index], RFeatureManager.this.mFeatureTypes[index], bundle.getInt(RFeatureManager.this.mFeatureTypes[index].name(), 0) == 1);
                if (wrapper != null) {
                    RFeatureManager.this.mRegisteredFeatures.put(RFeatureManager.this.mFeatureTypes[index], wrapper);
                }
            }
        }

        private void registerAPPMngFeature(int featureStatus) {
            FeatureWrapper wrapper = createFeatureWrapper(RFeatureManager.mAPPMngFeatureName, RFeatureManager.mAPPMngFeatureType, featureStatus == 1);
            if (wrapper != null) {
                RFeatureManager.this.mRegisteredFeatures.put(RFeatureManager.mAPPMngFeatureType, wrapper);
            }
        }

        private FeatureWrapper createFeatureWrapper(String featureName, FeatureType featureType, boolean featureStatus) {
            AwareLog.d(RFeatureManager.TAG, "createFeatureWrapper name = " + featureName + " type = " + featureType);
            try {
                Constructor<?>[] featureConstructor = Class.forName(featureName).getConstructors();
                try {
                    AwareLog.d(RFeatureManager.TAG, "createFeatureWrapper constructor = " + featureConstructor[0].getName());
                    RFeature feature = (RFeature) featureConstructor[0].newInstance(new Object[]{RFeatureManager.this.mContext, featureType, RFeatureManager.this});
                    AwareLog.d(RFeatureManager.TAG, "createFeatureWrapper featureStatus = " + featureStatus);
                    if (featureStatus) {
                        feature.enable();
                    } else if (featureType == FeatureType.FEATURE_RESOURCE) {
                        feature.disable();
                    }
                    return new FeatureWrapper(feature, featureStatus);
                } catch (IllegalArgumentException e) {
                    AwareLog.e(RFeatureManager.TAG, "createFeatureWrapper newInstance IllegalArgumentException");
                    return null;
                } catch (IllegalAccessException e2) {
                    AwareLog.e(RFeatureManager.TAG, "createFeatureWrapper newInstance IllegalAccessException");
                    return null;
                } catch (InstantiationException e3) {
                    AwareLog.e(RFeatureManager.TAG, "createFeatureWrapper newInstance InstantiationException");
                    return null;
                } catch (InvocationTargetException e4) {
                    AwareLog.e(RFeatureManager.TAG, "createFeatureWrapper newInstance InvocationTargetException");
                    return null;
                }
            } catch (ClassNotFoundException e5) {
                AwareLog.e(RFeatureManager.TAG, "createFeatureWrapper forName ClassNotFoundException");
                return null;
            }
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        private void deliveryDataToFeatures(CollectData data, IReportDataCallback callback) {
            Throwable th;
            ResourceType resType = ResourceType.getResourceType(data.getResId());
            synchronized (RFeatureManager.this.mSubscribeDataMap) {
                try {
                    List<FeatureType> currlist = (List) RFeatureManager.this.mSubscribeDataMap.get(resType);
                    if (currlist != null) {
                        int subcribedFeatureNum = currlist.size();
                        if (subcribedFeatureNum < RFeatureManager.this.mSubscribedFeatureTypeNum) {
                            int index = 0;
                            for (FeatureType feature : currlist) {
                                int index2;
                                try {
                                    index2 = index + 1;
                                    RFeatureManager.this.mSubscribedFeatureTypes[index] = feature;
                                    index = index2;
                                } catch (Throwable th2) {
                                    th = th2;
                                    index2 = index;
                                }
                            }
                        } else {
                            AwareLog.e(RFeatureManager.TAG, "deliveryDataToFeatures subscribed too much features!");
                            return;
                        }
                    }
                    AwareLog.d(RFeatureManager.TAG, "deliveryDataToFeatures no subscribed features resType = " + resType.name());
                    return;
                } catch (Throwable th3) {
                    th = th3;
                }
            }
            throw th;
        }

        private void notifyConfigUpdate() {
            AwareLog.d(RFeatureManager.TAG, "notifyConfigUpdate");
            for (FeatureWrapper feature : RFeatureManager.this.mRegisteredFeatures.values()) {
                if (feature == null || feature.getFeatureInstance() == null) {
                    AwareLog.e(RFeatureManager.TAG, "notifyConfigUpdate feature null!");
                } else if (!feature.getFeatureInstance().configUpdate()) {
                    AwareLog.e(RFeatureManager.TAG, "notifyConfigUpdate return false");
                }
            }
        }
    }

    public boolean subscribeData(ResourceType resType, FeatureType featureType) {
        if (resType == null || featureType == null) {
            AwareLog.e(TAG, "subscribeData: error parameters!");
            return false;
        }
        AwareLog.d(TAG, "subscribeData resType = " + resType.name() + " featureType = " + featureType.name());
        synchronized (this.mSubscribeDataMap) {
            ArrayList<FeatureType> currlist = (ArrayList) this.mSubscribeDataMap.get(resType);
            if (currlist == null) {
                currlist = new ArrayList();
                this.mSubscribeDataMap.put(resType, currlist);
            }
            if (!currlist.contains(featureType)) {
                currlist.add(featureType);
            }
        }
        return true;
    }

    public boolean unSubscribeData(ResourceType resType, FeatureType featureType) {
        if (resType == null || featureType == null) {
            AwareLog.e(TAG, "unSubscribeData: error parameters!");
            return false;
        }
        AwareLog.d(TAG, "unSubscribeData resType = " + resType.name() + " featureType = " + featureType.name());
        synchronized (this.mSubscribeDataMap) {
            List<FeatureType> currlist = (List) this.mSubscribeDataMap.get(resType);
            if (currlist != null) {
                currlist.remove(featureType);
                if (currlist.size() == 0) {
                    this.mSubscribeDataMap.remove(resType);
                }
            }
        }
        return true;
    }

    public FeatureType[] getFeatureTypes() {
        return (FeatureType[]) this.mFeatureTypes.clone();
    }

    public int getFeatureStatus(int featureid) {
        FeatureType type = FeatureType.getFeatureType(featureid);
        if (type == FeatureType.FEATURE_INVALIDE_TYPE) {
            AwareLog.e(TAG, "getFeatureStatus invalid feature type");
            return 0;
        }
        FeatureWrapper wrapper = (FeatureWrapper) this.mRegisteredFeatures.get(type);
        if (wrapper == null) {
            AwareLog.e(TAG, "getFeatureStatus feature wrapper null, featureid = " + featureid);
            return 0;
        } else if (wrapper.getFeatureEnabled()) {
            return 1;
        } else {
            return 0;
        }
    }

    public void enableFeature(int type) {
        AwareLog.d(TAG, "enableFeature type = " + type);
        Message enableMessage = Message.obtain();
        enableMessage.what = 1;
        enableMessage.arg1 = 1;
        enableMessage.arg2 = type;
        this.mReportedDataHandler.sendMessage(enableMessage);
    }

    public void disableFeature(int type) {
        AwareLog.d(TAG, "disableFeature type = " + type);
        Message disableMessage = Message.obtain();
        disableMessage.what = 1;
        disableMessage.arg1 = 0;
        disableMessage.arg2 = type;
        this.mReportedDataHandler.sendMessage(disableMessage);
    }

    public void reportData(CollectData data) {
        Message dataMessage = Message.obtain();
        dataMessage.what = 2;
        dataMessage.obj = data;
        this.mReportedDataHandler.sendMessage(dataMessage);
    }

    public void reportDataWithCallback(CollectData data, IReportDataCallback callback) {
        AwareLog.d(TAG, "reportDataWithCallback");
        Message dataMessage = Message.obtain();
        dataMessage.what = 4;
        SomeArgs args = SomeArgs.obtain();
        args.arg1 = data;
        args.arg2 = callback;
        dataMessage.obj = args;
        this.mReportedDataHandler.sendMessage(dataMessage);
    }

    public void init(Bundle bundle) {
        AwareLog.d(TAG, "init bundle = " + bundle);
        Message initMessage = Message.obtain();
        initMessage.what = 0;
        initMessage.setData(bundle);
        this.mReportedDataHandler.sendMessage(initMessage);
    }

    public boolean isResourceNeeded(ResourceType resourceType) {
        return this.mIAwareEnabled;
    }

    public int isFeatureEnabled(int featureId) {
        if (FeatureType.getFeatureType(featureId) != FeatureType.FEATURE_INVALIDE_TYPE) {
            return parseFeatureSwitchFormCustFile(featureId);
        }
        AwareLog.e(TAG, "Enabling error feature id!");
        return -1;
    }

    public boolean configUpdate() {
        AwareLog.d(TAG, "configUpdate ");
        return this.mReportedDataHandler.sendEmptyMessage(3);
    }

    public int getDumpData(int time, List<DumpData> dumpData) {
        AwareLog.d(TAG, "getDumpData");
        for (FeatureWrapper v : this.mRegisteredFeatures.values()) {
            if (v == null || v.getFeatureInstance() == null) {
                AwareLog.e(TAG, "getDumpData feature null!");
            } else {
                ArrayList<DumpData> featureDumpData = v.getFeatureInstance().getDumpData(time);
                if (featureDumpData != null) {
                    int dumpDataSize = featureDumpData.size();
                    if (dumpDataSize > 5) {
                        AwareLog.e(TAG, "RDA getDumpData more than 5 items, size = " + dumpDataSize + " , id = " + ((DumpData) featureDumpData.get(0)).getFeatureId());
                        dumpDataSize = 5;
                    }
                    for (int index = 0; index < dumpDataSize; index++) {
                        dumpData.add((DumpData) featureDumpData.get(index));
                    }
                }
            }
        }
        return dumpData.size();
    }

    public int getStatisticsData(List<StatisticsData> statisticsData) {
        AwareLog.d(TAG, "getStatisticsData");
        for (FeatureWrapper v : this.mRegisteredFeatures.values()) {
            if (v == null || v.getFeatureInstance() == null) {
                AwareLog.e(TAG, "getStatisticsData feature null!");
            } else {
                ArrayList<StatisticsData> featureStatisticsData = v.getFeatureInstance().getStatisticsData();
                if (featureStatisticsData != null) {
                    statisticsData.addAll(featureStatisticsData);
                }
            }
        }
        return statisticsData.size();
    }

    public String saveBigData(int featureId, boolean clear) {
        AwareLog.d(TAG, "rt saveBigData");
        FeatureWrapper fw = (FeatureWrapper) this.mRegisteredFeatures.get(FeatureType.getFeatureType(featureId));
        if (fw == null) {
            AwareLog.d(TAG, "null FeatureWrapper");
            return null;
        }
        RFeature v = fw.getFeatureInstance();
        if (v != null) {
            return v.saveBigData(clear);
        }
        AwareLog.d(TAG, "null RFeature");
        return null;
    }

    public RFeatureManager(Context context, HandlerThread handlerThread) {
        AwareLog.d(TAG, "RFeatureManager created");
        this.mContext = context;
        this.mRegisteredFeatures = new HashMap();
        this.mSubscribeDataMap = new HashMap();
        this.mReportedDataHandler = new ReportedDataHandler(handlerThread.getLooper());
        this.mSubscribedFeatureTypeNum = this.mFeatureTypes.length + 1;
        this.mSubscribedFeatureTypes = new FeatureType[this.mSubscribedFeatureTypeNum];
        this.mParser = Xml.newPullParser();
    }

    public RFeature getRegisteredFeature(FeatureType type) {
        if (!this.mRegisteredFeatures.containsKey(type)) {
            return null;
        }
        FeatureWrapper feature = (FeatureWrapper) this.mRegisteredFeatures.get(type);
        if (feature != null) {
            return feature.getFeatureInstance();
        }
        AwareLog.e(TAG, "getRegisteredFeature feature null!");
        return null;
    }

    private void registerRDABroadcastReceiver() {
        IntentFilter deviceStates = new IntentFilter();
        deviceStates.addAction("android.intent.action.SCREEN_ON");
        deviceStates.addAction("android.intent.action.SCREEN_OFF");
        deviceStates.setPriority(1000);
        this.mContext.registerReceiverAsUser(this.mReceiver, UserHandle.ALL, deviceStates, null, null);
        IntentFilter bootStates = new IntentFilter();
        bootStates.addAction("android.intent.action.BOOT_COMPLETED");
        this.mContext.registerReceiverAsUser(this.mReceiver, UserHandle.ALL, bootStates, null, null);
        IntentFilter appStates = new IntentFilter();
        appStates.addAction("android.intent.action.PACKAGE_ADDED");
        appStates.addAction("android.intent.action.PACKAGE_REMOVED");
        appStates.addDataScheme(ControlScope.PACKAGE_ELEMENT_KEY);
        this.mContext.registerReceiverAsUser(this.mReceiver, UserHandle.ALL, appStates, null, null);
        IntentFilter completedTrain = new IntentFilter();
        completedTrain.addAction(USER_HABIT_TRAIN_COMPLETED_ACTION);
        this.mContext.registerReceiverAsUser(this.mReceiver, UserHandle.ALL, completedTrain, USER_HABIT_RECV_TRAIN_COMPLETED_PERMISSION, null);
    }

    private int parseFeatureSwitchFormCustFile(int featureId) {
        IOException ioe;
        Throwable th;
        Exception e;
        InputStream inputStream = null;
        XmlPullParser xmlPullParser = null;
        try {
            File custConfigFile = loadCustFeatureSwitchFile();
            if (custConfigFile.exists()) {
                InputStream is = new FileInputStream(custConfigFile);
                try {
                    xmlPullParser = this.mParser;
                    xmlPullParser.setInput(is, StandardCharsets.UTF_8.name());
                    inputStream = is;
                } catch (IOException e2) {
                    ioe = e2;
                    inputStream = is;
                    try {
                        AwareLog.e(TAG, "read xml failed, error:" + ioe.getMessage());
                        if (inputStream != null) {
                            try {
                                inputStream.close();
                            } catch (IOException e3) {
                                AwareLog.e(TAG, "close file input stream fail!!");
                            }
                        }
                        return -1;
                    } catch (Throwable th2) {
                        th = th2;
                        if (inputStream != null) {
                            try {
                                inputStream.close();
                            } catch (IOException e4) {
                                AwareLog.e(TAG, "close file input stream fail!!");
                            }
                        }
                        throw th;
                    }
                } catch (Exception e5) {
                    e = e5;
                    inputStream = is;
                    AwareLog.e(TAG, "read xml failed, error:" + e.getMessage());
                    if (inputStream != null) {
                        try {
                            inputStream.close();
                        } catch (IOException e6) {
                            AwareLog.e(TAG, "close file input stream fail!!");
                        }
                    }
                    return -1;
                } catch (Throwable th3) {
                    th = th3;
                    inputStream = is;
                    if (inputStream != null) {
                        inputStream.close();
                    }
                    throw th;
                }
            }
            if (xmlPullParser == null) {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e7) {
                        AwareLog.e(TAG, "close file input stream fail!!");
                    }
                }
                return -1;
            }
            boolean enterItemTag = false;
            Map configItem = null;
            for (int eventType = xmlPullParser.getEventType(); eventType != 1; eventType = xmlPullParser.next()) {
                String tagName = xmlPullParser.getName();
                if (eventType == 2) {
                    if (XML_TAG_ITEM.equals(tagName)) {
                        enterItemTag = true;
                        configItem = new HashMap();
                    } else if (tagName != null && enterItemTag) {
                        String value = xmlPullParser.nextText();
                        if (configItem != null) {
                            configItem.put(tagName, value);
                        }
                    }
                } else if (eventType != 3) {
                    continue;
                } else if (XML_TAG_ITEM.equals(tagName)) {
                    AwareLog.d(TAG, "exit item");
                    enterItemTag = false;
                    if (configItem != null && ((String) configItem.get(XML_TAG_FEATURE_ID)).equals(Integer.toString(featureId))) {
                        int parseInt = Integer.parseInt((String) configItem.get(XML_TAG_FEATURE_SWITCH));
                        if (inputStream != null) {
                            try {
                                inputStream.close();
                            } catch (IOException e8) {
                                AwareLog.e(TAG, "close file input stream fail!!");
                            }
                        }
                        return parseInt;
                    }
                } else {
                    continue;
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e9) {
                    AwareLog.e(TAG, "close file input stream fail!!");
                }
            }
            return -1;
        } catch (IOException e10) {
            ioe = e10;
        } catch (Exception e11) {
            e = e11;
        }
    }

    File loadCustFeatureSwitchFile() {
        try {
            File cfg = HwCfgFilePolicy.getCfgFile("xml/iAwareFeatureSwitch.xml", 0);
            if (cfg != null) {
                AwareLog.d(TAG, "cust switch file path is " + cfg.getAbsolutePath());
                return cfg;
            }
        } catch (NoClassDefFoundError e) {
            AwareLog.e(TAG, "loadCustFeatureSwitchFile NoClassDefFoundError : HwCfgFilePolicy ");
        }
        return new File(CUST_PATH_DIR + File.separator + FEATURE_SWITCH_FILE_NAME);
    }
}
