package com.android.server.mtm.test;

import android.app.mtm.MultiTaskManager;
import android.app.mtm.MultiTaskPolicy;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.IPackageManager.Stub;
import android.os.Bundle;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import com.android.server.mtm.policy.MultiTaskPolicyMemoryCreator;
import com.android.server.pfw.autostartup.comm.XmlConst.ControlScope;
import com.android.server.rms.iaware.hiber.constant.AppHibernateCst;
import com.android.server.rms.resource.MemoryInnerResource;
import java.io.PrintWriter;

public final class TestMemoryPolicy {
    private static MultiTaskPolicyMemoryCreator mMemPolicy = null;

    public static final void test(PrintWriter pw, String[] args) {
        mMemPolicy = (MultiTaskPolicyMemoryCreator) MultiTaskPolicyMemoryCreator.getInstance();
        String cmd = args[1];
        MultiTaskPolicyMemoryCreator multiTaskPolicyMemoryCreator;
        if ("enable_log".equals(cmd)) {
            multiTaskPolicyMemoryCreator = mMemPolicy;
            MultiTaskPolicyMemoryCreator.enableDebug();
        } else if ("disable_log".equals(cmd)) {
            multiTaskPolicyMemoryCreator = mMemPolicy;
            MultiTaskPolicyMemoryCreator.disableDebug();
        } else if ("getMultiTaskPolicy".equals(cmd)) {
            runGetMultiTaskPolicy(pw, args);
        } else {
            pw.println("Bad command :" + cmd);
        }
    }

    private static void runGetMultiTaskPolicy(PrintWriter pw, String[] args) {
        Bundle data = new Bundle();
        if (args.length != 6 || args[2] == null || args[3] == null || args[4] == null || args[5] == null) {
            pw.println("args is invalid");
            return;
        }
        int resourcetype = Integer.parseInt(args[2]);
        String resourcename = args[3];
        int resourcestatus = Integer.parseInt(args[4]);
        data.putLong(MemoryInnerResource.MEMORY_PARAM_MEMNEEDTORECLAIM, Long.parseLong(args[5]));
        pw.println("resourcetype:" + resourcetype);
        pw.println("resourcename:" + resourcename);
        pw.println("resourcestatus:" + resourcestatus);
        pw.println("memneedtoclean:" + data.getLong(MemoryInnerResource.MEMORY_PARAM_MEMNEEDTORECLAIM));
        MultiTaskPolicy memPolicy = MultiTaskManager.getInstance().getMultiTaskPolicy(resourcetype, resourcename, resourcestatus, data);
        if (memPolicy != null) {
            printPolicy(pw, memPolicy);
        }
    }

    private static void printPolicy(PrintWriter pw, MultiTaskPolicy memPolicy) {
        int policy = memPolicy.getPolicy();
        if ((policy & 2) != 0) {
            pw.println("Policy:Forbid");
        }
        if ((policy & 4) != 0) {
            pw.println("Policy:Delay");
        }
        if ((policy & 8) != 0) {
            pw.println("Policy:Proxy");
        }
        if ((policy & 16) != 0) {
            pw.println("Policy:ProcessCpuset");
        }
        if ((policy & 32) != 0) {
            pw.println("Policy:ProcessKill");
            int[] pids = memPolicy.getPolicyData().getIntArray(MultiTaskPolicyMemoryCreator.POLICY_PROCESSKILL_PARAM);
            pw.println("ProcessInfo");
            printProcessInfo(pw, pids);
            pw.println("Policy:Processforcestop");
            pids = memPolicy.getPolicyData().getIntArray(MultiTaskPolicyMemoryCreator.POLICY_PROCESSFORCESTOP_PARAM);
            pw.println("ProcessInfo");
            printProcessInfo(pw, pids);
        }
        if ((policy & 1024) != 0) {
            pw.println("Policy:ProcessShrink");
            pids = memPolicy.getPolicyData().getIntArray(MultiTaskPolicyMemoryCreator.POLICY_PROCESSHRINK_PARAM);
            pw.println("ProcessInfo");
            printProcessInfo(pw, pids);
        }
        if ((policy & 64) != 0) {
            pw.println("Policy:ProcessFreeze");
        }
        if ((policy & 128) != 0) {
            pw.println("Policy:MemoryShrink");
        }
        if ((policy & 256) != 0) {
            pw.println("Policy:MemoryDropCache");
        }
    }

    public static void printProcessInfo(PrintWriter pw, int[] pids) {
        if (pids != null && pids.length > 0) {
            IPackageManager packageManager = Stub.asInterface(ServiceManager.getService(ControlScope.PACKAGE_ELEMENT_KEY));
            for (int i = 0; i < pids.length; i++) {
                pw.println("pid:" + pids[i] + " processName:" + getProcessDesc(pw, packageManager, pids[i]));
            }
        }
    }

    public static String getProcessDesc(PrintWriter pw, IPackageManager packageManager, int pid) {
        String packageName = getPackageName(pw, packageManager, Process.getUidForPid(pid));
        if (packageName == null || packageName.trim().equals(AppHibernateCst.INVALID_PKG)) {
            pw.println("getProcessDesc  mPackageName is null or empty string");
            return null;
        }
        ApplicationInfo AppInfo = getAppInfo(pw, packageManager, packageName);
        if (AppInfo != null) {
            return AppInfo.processName;
        }
        pw.println("getProcessDesc  mAppInfo is null");
        return null;
    }

    private static String getPackageName(PrintWriter pw, IPackageManager packageManager, int uid) {
        if (packageManager == null) {
            pw.println("init failed to get PackageManager handler");
            return null;
        }
        String[] packagenamelist = null;
        try {
            packagenamelist = packageManager.getPackagesForUid(uid);
        } catch (RemoteException e) {
            pw.println("Package manager has died");
        }
        if (packagenamelist != null) {
            return packagenamelist[0];
        }
        return null;
    }

    private static ApplicationInfo getAppInfo(PrintWriter pw, IPackageManager packageManager, String pkgName) {
        if (packageManager == null) {
            pw.println("init failed to get PackageManager handler");
            return null;
        }
        ApplicationInfo info = null;
        try {
            info = packageManager.getApplicationInfo(pkgName, 0, UserHandle.getCallingUserId());
        } catch (RemoteException e) {
            pw.println("Package manager has died");
        }
        if (info == null) {
            pw.println("can not get applicationinfo for pkgName:" + pkgName);
        }
        return info;
    }
}
