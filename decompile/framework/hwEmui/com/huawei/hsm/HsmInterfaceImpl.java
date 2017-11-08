package com.huawei.hsm;

import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.hsm.HwSystemManager.HsmInterface;
import android.location.Location;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Binder;
import android.util.Log;
import com.huawei.hsm.permission.PermissionManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HsmInterfaceImpl implements HsmInterface {
    private static final int RESTRICTED_NUM = 2;
    private static final String TAG = HsmInterfaceImpl.class.getSimpleName();

    public boolean canStartActivity(Context context, Intent intent) {
        return PermissionManager.canStartActivity(context, intent);
    }

    public boolean canSendBroadcast(Context context, Intent intent) {
        return PermissionManager.canSendBroadcast(context, intent);
    }

    public boolean allowOp(Uri uri, int action) {
        return PermissionManager.allowOp(uri, action);
    }

    public boolean allowOp(String destAddr, String smsBody, PendingIntent sentIntent) {
        return PermissionManager.allowOp(destAddr, smsBody, sentIntent);
    }

    public boolean allowOp(String destAddr, String smsBody, List<PendingIntent> sentIntents) {
        return PermissionManager.allowOp(destAddr, smsBody, (List) sentIntents);
    }

    public boolean allowOp(int type) {
        return PermissionManager.allowOp(type);
    }

    public boolean allowOp(Context cxt, int type) {
        return PermissionManager.allowOp(cxt, type);
    }

    public boolean allowOp(Context cxt, int type, boolean enable) {
        return PermissionManager.allowOp(cxt, type, enable);
    }

    public Cursor getDummyCursor(ContentResolver resolver, Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return PermissionManager.getDummyCursor(resolver, uri, projection, selection, selectionArgs, sortOrder);
    }

    public Location getFakeLocation(String name) {
        return PermissionManager.getFakeLocation(name);
    }

    public void setOutputFile(MediaRecorder recorder, long offset, long len) throws IllegalStateException, IOException {
        PermissionManager.setOutputFile(recorder, offset, len);
    }

    public boolean shouldInterceptAudience(String[] people, String pkgName) {
        return false;
    }

    public List<PackageInfo> getFakePackages(List<PackageInfo> installedList) {
        List<PackageInfo> fakeList = new ArrayList();
        int num = 0;
        for (PackageInfo packageInfo : installedList) {
            if (isSameUid(packageInfo.applicationInfo)) {
                fakeList.add(packageInfo);
            } else if (num < 2) {
                num++;
                fakeList.add(packageInfo);
            }
        }
        Log.d(TAG, "List " + getListStr(fakeList));
        return fakeList;
    }

    public List<ApplicationInfo> getFakeApplications(List<ApplicationInfo> installedList) {
        List<ApplicationInfo> fakeList = new ArrayList();
        int num = 0;
        for (ApplicationInfo applicationInfo : installedList) {
            if (isSameUid(applicationInfo)) {
                fakeList.add(applicationInfo);
            } else if (num < 2) {
                num++;
                fakeList.add(applicationInfo);
            }
        }
        Log.d(TAG, "List " + getListStr(fakeList));
        return fakeList;
    }

    public List<ResolveInfo> getFakeResolveInfoList(List<ResolveInfo> originalList) {
        if (originalList == null) {
            return originalList;
        }
        List<ResolveInfo> fakeList = new ArrayList();
        int num = 0;
        for (ResolveInfo resolveInfo : originalList) {
            ActivityInfo activityInfo = resolveInfo.activityInfo;
            if (activityInfo != null) {
                if (isSameUid(activityInfo.applicationInfo)) {
                    fakeList.add(resolveInfo);
                } else if (num < 2) {
                    fakeList.add(resolveInfo);
                    num++;
                }
            }
        }
        Log.d(TAG, "List " + getListStr(fakeList));
        return fakeList;
    }

    private boolean isSameUid(ApplicationInfo applicationInfo) {
        boolean z = false;
        if (applicationInfo == null) {
            return false;
        }
        if (applicationInfo.uid == Binder.getCallingUid()) {
            z = true;
        }
        return z;
    }

    private <T> String getListStr(List<T> list) {
        if (list == null) {
            return "NULL";
        }
        return Arrays.toString(list.toArray());
    }
}
