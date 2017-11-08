package com.android.server.forcerotation;

import android.content.Context;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.UserHandle;
import android.provider.Settings.System;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Slog;
import android.view.Display;
import android.view.WindowManager;
import android.widget.Toast;
import com.android.server.am.ActivityRecord;
import com.huawei.forcerotation.IHwForceRotationManager.Stub;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class HwForceRotationManagerService extends Stub {
    private static final int MSG_SHOW_TOAST = 1;
    private static final String TAG = "HwForceRotationService";
    private Context mContext;
    private HwForceRotationLayout mFixedLandscapeLayout;
    private List<ForceRotationAppInfo> mForceRotationAppInfos;
    private HwForceRotationConfig mForceRotationConfig;
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    Slog.v(HwForceRotationManagerService.TAG, "show Toast message");
                    HwForceRotationManagerService.this.showToast();
                    return;
                default:
                    return;
            }
        }
    };
    private boolean mIsAppInForceRotationWhiteList = false;
    private String mTmpAppName;
    private Map<String, AppToastInfo> mToastedAppInfos;

    protected void showToast() {
        Toast.makeText(this.mContext, 33685901, 0).show();
    }

    public HwForceRotationManagerService(Context context, Handler uiHandler) {
        this.mContext = context;
        this.mForceRotationAppInfos = new ArrayList();
        this.mFixedLandscapeLayout = new HwForceRotationLayout(this.mContext, uiHandler, this);
        this.mForceRotationConfig = new HwForceRotationConfigLoader().load();
        this.mToastedAppInfos = new HashMap();
    }

    public boolean isForceRotationSwitchOpen() {
        boolean z = true;
        if (this.mContext == null || this.mContext.getContentResolver() == null) {
            return false;
        }
        if (System.getInt(this.mContext.getContentResolver(), "force_rotation_mode", 0) != 1) {
            z = false;
        }
        return z;
    }

    public synchronized boolean isAppInForceRotationWhiteList(String packageName) {
        return this.mForceRotationConfig.isAppSupportForceRotation(packageName);
    }

    public synchronized boolean isAppForceLandRotatable(String packageName, IBinder aToken) {
        if (this.mForceRotationConfig.isAppSupportForceRotation(packageName)) {
            return isAppForceLandRotatable(aToken);
        }
        Slog.d(TAG, "isAppForceLandRotatable app not supported, pn: " + packageName);
        return false;
    }

    protected synchronized boolean isAppForceLandRotatable(IBinder aToken) {
        boolean z = true;
        synchronized (this) {
            ForceRotationAppInfo portaitFRAI = null;
            ForceRotationAppInfo landscapeFRAI = null;
            Iterator<ForceRotationAppInfo> iter = this.mForceRotationAppInfos.iterator();
            while (iter.hasNext()) {
                ForceRotationAppInfo tmpFRAI = (ForceRotationAppInfo) iter.next();
                IBinder tmpToken = (IBinder) tmpFRAI.getmAppToken().get();
                int tmpOrientation = tmpFRAI.getmOrientation();
                if (ActivityRecord.forToken(tmpToken) == null) {
                    Slog.d(TAG, "ftk:pn=" + tmpFRAI.getmPackageName() + ", o=" + tmpOrientation);
                    iter.remove();
                } else if (aToken != tmpToken) {
                    continue;
                } else if (tmpOrientation == 1 || tmpOrientation == 7 || tmpOrientation == 9 || tmpOrientation == 12) {
                    portaitFRAI = tmpFRAI;
                    break;
                } else if (tmpOrientation == 0 || tmpOrientation == 6 || tmpOrientation == 8 || tmpOrientation == 11 || tmpOrientation == -1 || tmpOrientation == 4 || tmpOrientation == 5 || tmpOrientation == 10) {
                    landscapeFRAI = tmpFRAI;
                    break;
                } else {
                    Slog.d(TAG, "utk:pn=" + tmpFRAI.getmPackageName() + ", o=" + tmpOrientation);
                }
            }
            if (portaitFRAI == null && r1 != null) {
                z = false;
            }
        }
        return z;
    }

    protected synchronized ForceRotationAppInfo queryForceRotationAppInfo(IBinder aToken) {
        ForceRotationAppInfo frai;
        frai = null;
        Iterator<ForceRotationAppInfo> iter = this.mForceRotationAppInfos.iterator();
        while (iter.hasNext()) {
            ForceRotationAppInfo tmpFRAI = (ForceRotationAppInfo) iter.next();
            IBinder tmpToken = (IBinder) tmpFRAI.getmAppToken().get();
            int or = tmpFRAI.getmOrientation();
            if (ActivityRecord.forToken(tmpToken) == null) {
                iter.remove();
            } else if (aToken == tmpToken) {
                frai = tmpFRAI;
                break;
            }
        }
        return frai;
    }

    public synchronized boolean saveOrUpdateForceRotationAppInfo(String packageName, String componentName, IBinder aToken, int reqOrientation) {
        if (!this.mForceRotationConfig.isAppSupportForceRotation(packageName)) {
            Slog.i(TAG, "isAppSupportForceRotation-f,pn = " + packageName);
            return false;
        } else if (this.mForceRotationConfig.isActivitySupportForceRotation(componentName)) {
            saveOrUpdateForceRotationAppInfo(packageName, aToken, reqOrientation);
            return true;
        } else {
            Slog.i(TAG, "isActivitySupportForceRotation-t,cn = " + componentName);
            return false;
        }
    }

    protected synchronized void saveOrUpdateForceRotationAppInfo(String packageName, IBinder aToken, int reqOrientation) {
        ForceRotationAppInfo frai = queryForceRotationAppInfo(aToken);
        if (frai == null) {
            this.mForceRotationAppInfos.add(new ForceRotationAppInfo(packageName, aToken, reqOrientation));
        } else if (reqOrientation != frai.getmOrientation()) {
            frai.setmOrientation(reqOrientation);
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public synchronized void showToastIfNeeded(String packageName, int pid, String processName, IBinder aToken) {
        Display display = ((WindowManager) this.mContext.getSystemService("window")).getDefaultDisplay();
        DisplayMetrics dm = new DisplayMetrics();
        display.getRealMetrics(dm);
        if (dm.widthPixels < dm.heightPixels) {
            Slog.v(TAG, "the current screen is portrait");
        } else if (!isAppForceLandRotatable(packageName, aToken)) {
        } else {
            if (!TextUtils.isEmpty(packageName) && pid > 0) {
                AppToastInfo tmp = (AppToastInfo) this.mToastedAppInfos.get(packageName);
                if (tmp == null || (pid != tmp.getmPid() && processName.equals(tmp.getmProcessName()))) {
                    if (tmp == null) {
                        tmp = new AppToastInfo(packageName, processName, pid);
                    } else {
                        tmp.setmPid(pid);
                    }
                    this.mToastedAppInfos.put(packageName, tmp);
                    Message msg = this.mHandler.obtainMessage();
                    msg.what = 1;
                    this.mHandler.sendMessage(msg);
                }
            }
        }
    }

    public void applyForceRotationLayout(IBinder aToken, Rect vf) {
        Rect dv = null;
        if (this.mFixedLandscapeLayout != null) {
            dv = this.mFixedLandscapeLayout.getForceRotationLayout();
        }
        if (dv != null) {
            vf.set(dv);
        }
    }

    public int recalculateWidthForForceRotation(int width, int height, int logicalHeight, String packageName) {
        int resultWidth = width;
        if (width <= height || UserHandle.isIsolated(Binder.getCallingUid()) || !isForceRotationSwitchOpen()) {
            return resultWidth;
        }
        if (!(packageName == null || packageName.equals(this.mTmpAppName))) {
            this.mIsAppInForceRotationWhiteList = isAppInForceRotationWhiteList(packageName);
            this.mTmpAppName = packageName;
        }
        if (this.mIsAppInForceRotationWhiteList) {
            return logicalHeight;
        }
        return resultWidth;
    }
}
