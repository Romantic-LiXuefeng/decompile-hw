package huawei.android.os;

import android.app.ActivityManagerNative;
import android.app.AppGlobals;
import android.os.Environment;
import android.os.FileUtils;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.util.Log;
import java.io.File;

public class HwEnvironment {
    private static final boolean IS_SUPPORT_CLONE_APP = SystemProperties.getBoolean("ro.config.hw_support_clone_app", false);
    private static final boolean IS_SWITCH_SD_ENABLED = "true".equals(SystemProperties.get("ro.config.switchPrimaryVolume", "false"));
    private static final String TAG = "HwEnvironment";
    private static UserEnvironmentSD sCurrentUserSd;

    public static class UserEnvironmentSD {
        private final int mUserId;

        public UserEnvironmentSD(int userId) {
            this.mUserId = userId;
        }

        public File[] getExternalDirs() {
            StorageVolume[] volumes = StorageManager.getVolumeList(this.mUserId, 256);
            File[] files = new File[volumes.length];
            for (int i = 0; i < volumes.length; i++) {
                files[i] = volumes[i].getPathFile();
            }
            return files;
        }

        @Deprecated
        public File getExternalStorageDirectory() {
            if (getExternalDirs().length == 1) {
                return getExternalDirs()[0];
            }
            return getExternalDirs()[1];
        }

        @Deprecated
        public File getExternalStoragePublicDirectory(String type) {
            if (getExternalDirs().length == 1) {
                return buildExternalStoragePublicDirs(type)[0];
            }
            return buildExternalStoragePublicDirs(type)[1];
        }

        public File[] getExternalDirsForApp() {
            return getExternalDirs();
        }

        public File getMediaDir() {
            return null;
        }

        public File[] buildExternalStoragePublicDirs(String type) {
            if (type == null) {
                return null;
            }
            return Environment.buildPaths(getExternalDirs(), new String[]{type});
        }
    }

    public static void initUserEnvironmentSD(int userId) {
        if (IS_SWITCH_SD_ENABLED) {
            sCurrentUserSd = new UserEnvironmentSD(userId);
        }
    }

    public static File getMediaStorageDirectory() {
        return sCurrentUserSd.getMediaDir();
    }

    public static File getExternalStorageDirectory() {
        if (sCurrentUserSd.getExternalDirs().length == 1) {
            return sCurrentUserSd.getExternalDirsForApp()[0];
        }
        return sCurrentUserSd.getExternalDirsForApp()[1];
    }

    public static File getExternalStoragePublicDirectory(String type) {
        if (sCurrentUserSd.getExternalDirs().length == 1) {
            return sCurrentUserSd.buildExternalStoragePublicDirs(type)[0];
        }
        return sCurrentUserSd.buildExternalStoragePublicDirs(type)[1];
    }

    public static File getExternalStorageState() {
        if (sCurrentUserSd.getExternalDirs().length == 1) {
            return sCurrentUserSd.getExternalDirs()[0];
        }
        return sCurrentUserSd.getExternalDirs()[1];
    }

    public static boolean checkPrimaryVolumeIsSD() {
        return IS_SWITCH_SD_ENABLED && 1 == SystemProperties.getInt("persist.sys.primarysd", 0);
    }

    public static File handleExternalStorageDirectoryForClone(File file) {
        if (!IS_SUPPORT_CLONE_APP) {
            return file;
        }
        int pid = Process.myPid();
        boolean isClonedProcess = isClonedProcess(pid);
        String packageName = null;
        if (isClonedProcess) {
            packageName = getPackageNameForPid(pid);
        }
        if (!isClonedProcess || file == null || packageName == null) {
            return file;
        }
        Log.i(TAG, "getExternalStorageDirectory for clone app, pid is " + pid);
        File fileForClone = new File(file, "_hwclone" + File.separator + packageName);
        if (!fileForClone.exists()) {
            boolean isGranted = false;
            try {
                isGranted = AppGlobals.getPackageManager().checkUidPermission("android.permission.WRITE_EXTERNAL_STORAGE", Process.myUid()) == 0;
            } catch (RemoteException e) {
                Log.e(TAG, "getExternalStorageDirectory error", e);
            }
            if (isGranted) {
                fileForClone.mkdirs();
                FileUtils.setPermissions(fileForClone.getPath(), 505, -1, -1);
            } else {
                Log.e(TAG, "getExternalStorageDirectory return default directory because clone-app process does not have permission WRITE_EXTERNAL_STORAGE");
                return file;
            }
        }
        return fileForClone;
    }

    public static File handleDateDirectoryForClone(File file, int euid) {
        if (!IS_SUPPORT_CLONE_APP || file == null || euid == 0) {
            return file;
        }
        File dataDirForClone = file.getPath().contains("_hwclone") ? file : new File(file, "_hwclone");
        if (!dataDirForClone.exists()) {
            dataDirForClone.mkdir();
            FileUtils.setPermissions(dataDirForClone.getPath(), 505, -1, -1);
        }
        Log.i(TAG, "mDataDirFile: " + dataDirForClone + ", euid: " + euid);
        return dataDirForClone;
    }

    private static boolean isClonedProcess(int pid) {
        boolean res = false;
        try {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            data.writeInterfaceToken("android.app.IActivityManager");
            data.writeInt(pid);
            ActivityManagerNative.getDefault().asBinder().transact(503, data, reply, 0);
            reply.readException();
            res = reply.readInt() != 0;
            reply.recycle();
            data.recycle();
        } catch (Exception e) {
            Log.e(TAG, "isClonedProcess", e);
        }
        return res;
    }

    private static String getPackageNameForPid(int pid) {
        String str = null;
        try {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            data.writeInterfaceToken("android.app.IActivityManager");
            data.writeInt(pid);
            ActivityManagerNative.getDefault().asBinder().transact(504, data, reply, 0);
            reply.readException();
            str = reply.readString();
            data.recycle();
            reply.recycle();
            return str;
        } catch (Exception e) {
            Log.e(TAG, "getPackageNameForPid", e);
            return str;
        }
    }
}
