package com.android.server.security.securitydiagnose;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Flog;
import android.util.Log;
import com.android.server.pm.AntiMalComponentInfo;
import com.android.server.rms.iaware.hiber.constant.AppHibernateCst;
import com.android.server.security.core.IHwSecurityPlugin;
import com.android.server.security.core.IHwSecurityPlugin.Creator;
import com.android.server.security.deviceusage.HwOEMInfoAdapter;
import com.android.server.security.securitydiagnose.RootDetectReport.Listener;
import huawei.android.security.IHwSecurityDiagnosePlugin.Stub;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class HwSecurityDiagnosePlugin extends Stub implements IHwSecurityPlugin {
    private static final String BD_PACKAGE_NAME = "com.huawei.bd";
    private static final String BD_SERVICE_NAME = "com.huawei.bd.BDService";
    private static final boolean CHINA_RELEASE_VERSION = "CN".equalsIgnoreCase(SystemProperties.get("ro.product.locale.region", AppHibernateCst.INVALID_PKG));
    public static final Creator CREATOR = new Creator() {
        public IHwSecurityPlugin createPlugin(Context contxt) {
            IHwSecurityPlugin -get2;
            synchronized (HwSecurityDiagnosePlugin.mLock) {
                if (HwSecurityDiagnosePlugin.mInstance == null) {
                    HwSecurityDiagnosePlugin.mInstance = new HwSecurityDiagnosePlugin(contxt);
                }
                -get2 = HwSecurityDiagnosePlugin.mInstance;
            }
            return -get2;
        }

        public String getPluginPermission() {
            return HwSecurityDiagnosePlugin.PERMISSION;
        }
    };
    private static final long DELAY_INTERVAL = 6000;
    private static final int EVT_CHECK_BD_AGAIN = 1000;
    private static final int EVT_PROCESS_SECURE_DATA = 1001;
    private static final int EVT_REPORTER = 1002;
    private static final int EVT_START_ROOT_CHECK = 1003;
    private static final boolean HW_DEBUG;
    private static final String JSON_FORMAT_ENCODING = "UTF-8";
    private static final int MAX_SEND_APKS = 5;
    private static final int MAX_TRY_COUNT = 10;
    private static final String PERMISSION = "com.huawei.permission.SECURITY_DIAGNOSE";
    private static final String TAG = "HwSecurityDiagnosePlugin";
    private static HwSecurityDiagnosePlugin mInstance;
    private static final Object mLock = new Object();
    private ArrayList<AntiMalComponentInfo> mAntiMalComponentList;
    private Context mContext;
    private boolean mIsBootCompleted;
    private boolean mIsDBNotExist;
    private Handler mMyHandler;
    private ArrayList<QueueParams> mQueue;
    private BroadcastReceiver mReceiver;
    private Listener mRootReportListener;
    private int mTryCount;

    private static class QueueParams {
        public final Object mArg;
        public final int mReporter;

        private QueueParams(int reporter, Object arg) {
            this.mReporter = reporter;
            this.mArg = arg;
        }
    }

    private static class RootCheckTrigger extends Thread {
        private RootCheckTrigger() {
        }

        public void run() {
            if (HwSecurityDiagnosePlugin.HW_DEBUG) {
                Log.d(HwSecurityDiagnosePlugin.TAG, "rootCheckTask START!");
            }
            RootDetectReport.getInstance().triggerRootScan();
        }
    }

    static {
        boolean isLoggable = !Log.HWINFO ? Log.HWModuleLog ? Log.isLoggable(TAG, 4) : false : true;
        HW_DEBUG = isLoggable;
    }

    public IBinder asBinder() {
        return this;
    }

    public void onStart() {
        if (HW_DEBUG) {
            Log.d(TAG, "onStart()");
        }
        this.mContext.registerReceiver(this.mReceiver, new IntentFilter("android.intent.action.LOCKED_BOOT_COMPLETED"));
        RootDetectReport.init(this.mContext);
        RootDetectReport.getInstance().setListener(this.mRootReportListener);
    }

    public void onStop() {
        this.mContext.unregisterReceiver(this.mReceiver);
    }

    public int report(int reporterID, Bundle data) {
        if (!CHINA_RELEASE_VERSION) {
            return 0;
        }
        if (HW_DEBUG) {
            Log.d(TAG, "report reporterID = " + reporterID);
        }
        checkPermission(PERMISSION);
        this.mMyHandler.sendMessage(this.mMyHandler.obtainMessage(1002, reporterID, 0, data));
        return 0;
    }

    public void sendComponentInfo(Bundle data) {
        if (!CHINA_RELEASE_VERSION) {
            return;
        }
        if (data == null) {
            Log.e(TAG, "sendComponentInfo bundle is null!");
            return;
        }
        checkPermission(PERMISSION);
        ArrayList<AntiMalComponentInfo> componentList = data.getParcelableArrayList(HwSecDiagnoseConstant.COMPONENT_LIST);
        if (componentList == null || componentList.size() == 0) {
            if (HW_DEBUG) {
                Log.d(TAG, "sendComponentInfo componentList IS null!");
            }
            return;
        }
        synchronized (this.mAntiMalComponentList) {
            for (AntiMalComponentInfo acpi : componentList) {
                this.mAntiMalComponentList.add(acpi);
            }
        }
    }

    public boolean componentValid(String componentName) {
        if (HW_DEBUG) {
            Log.d(TAG, "componentValid componentName = " + componentName);
        }
        checkPermission(PERMISSION);
        if (TextUtils.isEmpty(componentName)) {
            return true;
        }
        synchronized (this.mAntiMalComponentList) {
            for (AntiMalComponentInfo acpi : this.mAntiMalComponentList) {
                if (componentName.equals(acpi.mName)) {
                    boolean isNormal = acpi.isNormal();
                    return isNormal;
                }
            }
            return true;
        }
    }

    public int getSystemStatus() {
        if (HW_DEBUG) {
            Log.d(TAG, "getSystemStatus");
        }
        checkPermission(PERMISSION);
        return 0;
    }

    private void checkPermission(String permission) {
        this.mContext.enforceCallingOrSelfPermission(permission, "Must have " + permission + " permission.");
    }

    private void reportInner(int reporterID, Object data) {
        if (HW_DEBUG) {
            Log.d(TAG, "reportInner reporterID = " + reporterID + " boot: " + this.mIsBootCompleted + " mIsDBNotExist = " + this.mIsDBNotExist);
        }
        if (!this.mIsBootCompleted) {
            cache(reporterID, data);
        } else if (this.mIsDBNotExist) {
            saveDataToOeminfo(reporterID, data);
        } else {
            cache(reporterID, data);
            checkHwBigDataExist();
        }
    }

    private HwSecurityDiagnosePlugin(Context contxt) {
        this.mTryCount = 0;
        this.mIsDBNotExist = false;
        this.mAntiMalComponentList = new ArrayList();
        this.mReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                if (intent == null) {
                    Log.e(HwSecurityDiagnosePlugin.TAG, "mReceiver intent is NULL!");
                    return;
                }
                String action = intent.getAction();
                if (HwSecurityDiagnosePlugin.HW_DEBUG) {
                    Log.d(HwSecurityDiagnosePlugin.TAG, "onReceive ACTION : " + action);
                }
                if ("android.intent.action.LOCKED_BOOT_COMPLETED".equals(action)) {
                    HwSecurityDiagnosePlugin.this.mIsBootCompleted = true;
                    if (HwSecurityDiagnosePlugin.CHINA_RELEASE_VERSION) {
                        HwSecurityDiagnosePlugin.this.mMyHandler.removeMessages(1000);
                        HwSecurityDiagnosePlugin.this.mMyHandler.sendEmptyMessage(1001);
                    }
                    HwSecurityDiagnosePlugin.this.mMyHandler.sendEmptyMessage(1003);
                }
            }
        };
        this.mMyHandler = new Handler() {
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case 1000:
                    case 1001:
                        HwSecurityDiagnosePlugin.this.checkHwBigDataExist();
                        return;
                    case 1002:
                        HwSecurityDiagnosePlugin.this.reportInner(msg.arg1, (Bundle) msg.obj);
                        return;
                    case 1003:
                        HwSecurityDiagnosePlugin.this.triggerRootCheck();
                        return;
                    default:
                        return;
                }
            }
        };
        this.mRootReportListener = new Listener() {
            public void onRootReport(JSONObject json) {
                if (HwSecurityDiagnosePlugin.CHINA_RELEASE_VERSION) {
                    HwSecurityDiagnosePlugin.this.reportInner(102, json);
                }
            }
        };
        this.mContext = contxt;
    }

    private ArrayList<QueueParams> getQueue() {
        synchronized (mLock) {
            if (this.mQueue == null) {
                this.mQueue = new ArrayList();
            }
        }
        return this.mQueue;
    }

    private void cache(int reporterID, Object data) {
        synchronized (mLock) {
            getQueue().add(new QueueParams(reporterID, data));
        }
    }

    private void triggerRootCheck() {
        new RootCheckTrigger().start();
    }

    private void retryCheck() {
        int i = this.mTryCount + 1;
        this.mTryCount = i;
        boolean needTry = i <= 10;
        if (HW_DEBUG) {
            Log.d(TAG, "retryCheck mTryCount = " + this.mTryCount);
        }
        if (needTry) {
            this.mMyHandler.sendEmptyMessageDelayed(1000, DELAY_INTERVAL);
            return;
        }
        this.mTryCount = 0;
        this.mIsDBNotExist = true;
        processQueue();
    }

    private void checkHwBigDataExist() {
        if (ServiceManager.getService(BD_SERVICE_NAME) == null) {
            retryCheck();
            return;
        }
        this.mTryCount = 0;
        processQueue();
    }

    private void processQueue() {
        synchronized (mLock) {
            if (this.mQueue != null) {
                if (HW_DEBUG) {
                    Log.d(TAG, "processQueue SIZE = " + this.mQueue.size() + " mIsDBNotExist " + this.mIsDBNotExist);
                }
                if (this.mIsDBNotExist) {
                    for (QueueParams params : this.mQueue) {
                        saveDataToOeminfo(params.mReporter, params.mArg);
                    }
                } else {
                    for (QueueParams params2 : this.mQueue) {
                        sendDataToBD(params2.mReporter, params2.mArg);
                    }
                }
                this.mQueue.clear();
            }
        }
    }

    private void saveDataToOeminfo(int reportId, Object data) {
        if (HW_DEBUG) {
            Log.d(TAG, "saveDataToOeminfo reportId = " + reportId + "\n data : " + data);
        }
        switch (reportId) {
            case 100:
                sendAntiMalDataToOEMInfo((Bundle) data);
                return;
            case 101:
                sendRenewDataToOEMInfo((Bundle) data);
                return;
            case 102:
                sendRootCheckDataToOEMInfo((JSONObject) data);
                return;
            default:
                Log.e(TAG, "saveDataToOeminfo The ID is invalid!");
                return;
        }
    }

    private void sendDataToBD(int reportId, Object data) {
        if (HW_DEBUG) {
            Log.d(TAG, "sendDataToBD reportId = " + reportId);
        }
        switch (reportId) {
            case 100:
                sendAntiMalDataToBD((Bundle) data);
                return;
            case 101:
                sendRenewDataToBD((Bundle) data);
                return;
            case 102:
                sendRootCheckDataToBD((JSONObject) data);
                return;
            default:
                Log.e(TAG, "saveDataToOeminfo The ID is invalid!");
                return;
        }
    }

    private JSONObject parcelAntiMalBaseData(Bundle bundle) {
        if (bundle == null) {
            Log.e(TAG, "sendAntiMalDataToBD bundle is NULL!");
            return null;
        }
        JSONObject json = new JSONObject();
        try {
            json.put(HwSecDiagnoseConstant.ANTIMAL_TIME, bundle.getString(HwSecDiagnoseConstant.ANTIMAL_TIME));
            json.put(HwSecDiagnoseConstant.ANTIMAL_ROOT_STATE, bundle.getInt(HwSecDiagnoseConstant.ANTIMAL_ROOT_STATE));
            json.put(HwSecDiagnoseConstant.ANTIMAL_FASTBOOT_STATE, bundle.getInt(HwSecDiagnoseConstant.ANTIMAL_FASTBOOT_STATE));
            json.put(HwSecDiagnoseConstant.ANTIMAL_SYSTEM_STATE, bundle.getInt(HwSecDiagnoseConstant.ANTIMAL_SYSTEM_STATE));
            json.put(HwSecDiagnoseConstant.ANTIMAL_MAL_COUNT, bundle.getInt(HwSecDiagnoseConstant.ANTIMAL_MAL_COUNT));
            json.put(HwSecDiagnoseConstant.ANTIMAL_DELETE_COUNT, bundle.getInt(HwSecDiagnoseConstant.ANTIMAL_DELETE_COUNT));
            json.put(HwSecDiagnoseConstant.ANTIMAL_TAMPER_COUNT, bundle.getInt(HwSecDiagnoseConstant.ANTIMAL_TAMPER_COUNT));
            json.put(HwSecDiagnoseConstant.ANTIMAL_SELINUX_STATE, bundle.getInt(HwSecDiagnoseConstant.ANTIMAL_SELINUX_STATE));
            json.put("SecVer", bundle.getString("SecVer"));
            json.put(HwSecDiagnoseConstant.ANTIMAL_SYSTEM_CUST_STATE, bundle.getInt(HwSecDiagnoseConstant.ANTIMAL_SYSTEM_CUST_STATE));
            json.put(HwSecDiagnoseConstant.ANTIMAL_USED_TIME, bundle.getString(HwSecDiagnoseConstant.ANTIMAL_USED_TIME));
            json.put("SecVer", bundle.getString("SecVer"));
            return json;
        } catch (Exception e) {
            Log.e(TAG, "parcelAntiMalData E:" + e);
            if (HW_DEBUG) {
                e.printStackTrace();
            }
            return null;
        }
    }

    private void sendAntiMalDataToOEMInfo(Bundle bundle) {
        JSONObject json = parcelAntiMalBaseData(bundle);
        if (json != null) {
            try {
                byte[] antiMalArry = json.toString().getBytes(JSON_FORMAT_ENCODING);
                HwOEMInfoAdapter.writeByteArrayToOeminfo(HwSecDiagnoseConstant.OEMINFO_ID_ANTIMAL, antiMalArry.length, antiMalArry);
                if (HW_DEBUG) {
                    Log.d(TAG, "sendAntiMalDataToOEMInfo STR:" + json.toString() + " LEN = " + antiMalArry.length);
                    return;
                }
                return;
            } catch (Exception e) {
                Log.e(TAG, "sendRenewDataToOEMInfo e :" + e);
                if (HW_DEBUG) {
                    e.printStackTrace();
                    return;
                }
                return;
            }
        }
        Log.e(TAG, "sendAntiMalDataToOEMInfo JSON IS NULL!");
    }

    private JSONObject apkInfoToJson(AntiMalApkInfo apkInfo) {
        JSONObject apkJson = new JSONObject();
        try {
            apkJson.put(HwSecDiagnoseConstant.ANTIMAL_APK_TYPE, apkInfo.mType);
            apkJson.put("PackageName", apkInfo.mPackageName);
            apkJson.put(HwSecDiagnoseConstant.ANTIMAL_APK_NAME, apkInfo.mApkName);
            apkJson.put(HwSecDiagnoseConstant.ANTIMAL_APK_PATH, apkInfo.mPath.replaceAll("\\/", "-"));
            apkJson.put(HwSecDiagnoseConstant.ANTIMAL_APK_VERSION, apkInfo.mVersion);
            apkJson.put(HwSecDiagnoseConstant.ANTIMAL_APK_LAST_MODIFY, apkInfo.mLastModifyTime);
            if (HW_DEBUG) {
                Log.d(TAG, "sendAntiMalDataToBD apkInfoToJson PATH: " + apkInfo);
            }
        } catch (Exception e) {
            Log.e(TAG, "apkInfoToJson EXCEPTION = " + e);
            if (HW_DEBUG) {
                e.printStackTrace();
            }
        }
        return apkJson;
    }

    private void sendAntiMalDataToBD(Bundle bundle) {
        JSONObject json = parcelAntiMalBaseData(bundle);
        if (bundle == null || json == null) {
            Log.e(TAG, "sendAntiMalDataToBD bundle is NULL!");
            return;
        }
        try {
            ArrayList<AntiMalApkInfo> apkInfoList = bundle.getParcelableArrayList(HwSecDiagnoseConstant.ANTIMAL_APK_LIST);
            if (apkInfoList == null || apkInfoList.size() <= 0) {
                if (HW_DEBUG) {
                    Log.d(TAG, "sendAntiMalDataToBD The list is empty!");
                }
                Flog.bdReport(this.mContext, 121, json, 27);
            } else {
                int size = apkInfoList.size();
                int sendCnt = apkInfoList.size() / 5;
                for (int j = 0; j < sendCnt; j++) {
                    JSONArray jsonArry = new JSONArray();
                    for (int i = 0; i < 5; i++) {
                        AntiMalApkInfo apkInfo = (AntiMalApkInfo) apkInfoList.get((j * 5) + i);
                        if (apkInfo != null) {
                            jsonArry.put(apkInfoToJson(apkInfo));
                        }
                    }
                    json.put(HwSecDiagnoseConstant.ANTIMAL_APK_LIST, jsonArry);
                    if (HW_DEBUG) {
                        Log.d(TAG, "sendAntiMalDataToBD LENGTH = " + json.toString().length() + "\n ANTIMAL data : " + json.toString());
                    }
                    Flog.bdReport(this.mContext, 121, json, 27);
                }
                int other = size % 5;
                if (other > 0) {
                    JSONArray jsons = new JSONArray();
                    for (int left = size - other; left < size; left++) {
                        AntiMalApkInfo ai = (AntiMalApkInfo) apkInfoList.get(left);
                        if (ai != null) {
                            jsons.put(apkInfoToJson(ai));
                        }
                    }
                    json.put(HwSecDiagnoseConstant.ANTIMAL_APK_LIST, jsons);
                    if (HW_DEBUG) {
                        Log.d(TAG, "sendAntiMalDataToBD LEFT LENGTH = " + json.toString().length() + "\n LEFT ANTIMAL data : " + json.toString());
                    }
                    Flog.bdReport(this.mContext, 121, json, 27);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "sendAntiMalDataToBD EXCEPTION = " + e);
            if (HW_DEBUG) {
                e.printStackTrace();
            }
        }
    }

    private JSONObject parcleRenewData(Bundle bundle) {
        if (bundle == null) {
            Log.e(TAG, "parcleRenewData bundle is NULL!");
            return null;
        }
        JSONObject json = new JSONObject();
        try {
            json.put(HwSecDiagnoseConstant.DEVICE_RENEW_SN_CODE, bundle.getString(HwSecDiagnoseConstant.DEVICE_RENEW_SN_CODE));
            json.put(HwSecDiagnoseConstant.DEVICE_RENEW_TIME, bundle.getString(HwSecDiagnoseConstant.DEVICE_RENEW_TIME));
            return json;
        } catch (Exception e) {
            Log.e(TAG, "parcleRenewData E: " + e);
            if (HW_DEBUG) {
                e.printStackTrace();
            }
            return null;
        }
    }

    private void sendRenewDataToBD(Bundle bundle) {
        JSONObject json = parcleRenewData(bundle);
        Flog.bdReport(this.mContext, 122, json, 27);
        if (HW_DEBUG) {
            Log.d(TAG, "sendRenewDataToBD data: " + json.toString());
        }
    }

    private void sendRenewDataToOEMInfo(Bundle bundle) {
        JSONObject json = parcleRenewData(bundle);
        if (json != null) {
            try {
                byte[] renewArry = json.toString().getBytes(JSON_FORMAT_ENCODING);
                HwOEMInfoAdapter.writeByteArrayToOeminfo(HwSecDiagnoseConstant.OEMINFO_ID_DEVICE_RENEW, renewArry.length, renewArry);
                if (HW_DEBUG) {
                    Log.d(TAG, "sendRenewDataToOEMInfo STR:" + json.toString() + " LEN = " + renewArry.length);
                    return;
                }
                return;
            } catch (Exception e) {
                Log.e(TAG, "sendRenewDataToOEMInfo e :" + e);
                if (HW_DEBUG) {
                    e.printStackTrace();
                    return;
                }
                return;
            }
        }
        Log.e(TAG, "sendRenewDataToOEMInfo JSON IS NULL!");
    }

    private void sendRootCheckDataToBD(JSONObject json) {
        if (json != null) {
            Flog.bdReport(this.mContext, 123, json, 27);
            if (HW_DEBUG) {
                Log.d(TAG, "sendRootCheckDataToBD data: " + json.toString());
            }
        }
    }

    private void sendRootCheckDataToOEMInfo(JSONObject json) {
        if (json == null) {
            Log.e(TAG, "sendRootCheckDataToOEMInfo json is NULL!");
            return;
        }
        try {
            byte[] bootArry = json.toString().getBytes(JSON_FORMAT_ENCODING);
            HwOEMInfoAdapter.writeByteArrayToOeminfo(HwSecDiagnoseConstant.OEMINFO_ID_ROOT_CHECK, bootArry.length, bootArry);
            if (HW_DEBUG) {
                Log.d(TAG, "sendRootCheckDataToOEMInfo STR:" + json.toString() + " LEN = " + bootArry.length);
            }
        } catch (Exception e) {
            Log.e(TAG, "sendRootCheckDataToOEMInfo e :" + e);
            if (HW_DEBUG) {
                e.printStackTrace();
            }
        }
    }
}
