package com.android.server.pm;

import android.content.Context;
import android.content.pm.PackageStats;
import android.os.Binder;
import android.os.Build;
import android.os.UserHandle;
import android.util.Slog;
import com.android.internal.os.InstallerConnection;
import com.android.internal.os.InstallerConnection.InstallerException;
import com.android.server.SystemService;
import dalvik.system.VMRuntime;
import java.util.Arrays;

public final class Installer extends SystemService {
    public static final int DEXOPT_BOOTCOMPLETE = 16;
    public static final int DEXOPT_DEBUGGABLE = 8;
    public static final int DEXOPT_OTA = 64;
    public static final int DEXOPT_PROFILE_GUIDED = 32;
    public static final int DEXOPT_PUBLIC = 2;
    public static final int DEXOPT_SAFEMODE = 4;
    public static final int FLAG_CLEAR_CACHE_ONLY = 256;
    public static final int FLAG_CLEAR_CODE_CACHE_ONLY = 512;
    private static final String TAG = "Installer";
    private final InstallerConnection mInstaller = new InstallerConnection();

    public Installer(Context context) {
        super(context);
    }

    public void setWarnIfHeld(Object warnIfHeld) {
        this.mInstaller.setWarnIfHeld(warnIfHeld);
    }

    public void onStart() {
        Slog.i(TAG, "Waiting for installd to be ready.");
        this.mInstaller.waitForConnection();
    }

    public void createAppData(String uuid, String pkgname, int userid, int flags, int appid, String seinfo, int targetSdkVersion) throws InstallerException {
        this.mInstaller.execute("create_app_data", new Object[]{uuid, pkgname, Integer.valueOf(userid), Integer.valueOf(flags), Integer.valueOf(appid), seinfo, Integer.valueOf(targetSdkVersion)});
    }

    public void createAppDataHwFixup(String uuid, String pkgname, int userid, int flags, int appid, String seinfo, int targetSdkVersion) throws InstallerException {
        this.mInstaller.execute("create_app_data_hwfixup", new Object[]{uuid, pkgname, Integer.valueOf(userid), Integer.valueOf(flags), Integer.valueOf(appid), seinfo, Integer.valueOf(targetSdkVersion)});
    }

    public void restoreCloneAppData(String uuid, String pkgname, int userid, int flags, int appid, String seinfo, String parentDataUserCePkgDir, String cloneDataUserCePkgDir, String parentDataUserDePkgDir, String cloneDataUserDePkgDir) throws InstallerException {
        this.mInstaller.execute("restore_clone_app_data", new Object[]{uuid, pkgname, Integer.valueOf(userid), Integer.valueOf(flags), Integer.valueOf(appid), seinfo, parentDataUserCePkgDir, cloneDataUserCePkgDir, parentDataUserDePkgDir, cloneDataUserDePkgDir});
    }

    public void restoreconAppData(String uuid, String pkgname, int userid, int flags, int appid, String seinfo) throws InstallerException {
        this.mInstaller.execute("restorecon_app_data", new Object[]{uuid, pkgname, Integer.valueOf(userid), Integer.valueOf(flags), Integer.valueOf(appid), seinfo});
    }

    public void migrateAppData(String uuid, String pkgname, int userid, int flags) throws InstallerException {
        this.mInstaller.execute("migrate_app_data", new Object[]{uuid, pkgname, Integer.valueOf(userid), Integer.valueOf(flags)});
    }

    public void clearAppData(String uuid, String pkgname, int userid, int flags, long ceDataInode) throws InstallerException {
        this.mInstaller.execute("clear_app_data", new Object[]{uuid, pkgname, Integer.valueOf(userid), Integer.valueOf(flags), Long.valueOf(ceDataInode)});
    }

    public void destroyAppData(String uuid, String pkgname, int userid, int flags, long ceDataInode) throws InstallerException {
        this.mInstaller.execute("destroy_app_data", new Object[]{uuid, pkgname, Integer.valueOf(userid), Integer.valueOf(flags), Long.valueOf(ceDataInode)});
    }

    public void moveCompleteApp(String from_uuid, String to_uuid, String package_name, String data_app_name, int appid, String seinfo, int targetSdkVersion) throws InstallerException {
        this.mInstaller.execute("move_complete_app", new Object[]{from_uuid, to_uuid, package_name, data_app_name, Integer.valueOf(appid), seinfo, Integer.valueOf(targetSdkVersion)});
    }

    public void getAppSize(String uuid, String pkgname, int userid, int flags, long ceDataInode, String codePath, PackageStats stats) throws InstallerException {
        String[] res = this.mInstaller.execute("get_app_size", new Object[]{uuid, pkgname, Integer.valueOf(userid), Integer.valueOf(flags), Long.valueOf(ceDataInode), codePath});
        try {
            stats.codeSize += Long.parseLong(res[1]);
            stats.dataSize += Long.parseLong(res[2]);
            stats.cacheSize += Long.parseLong(res[3]);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new InstallerException("Invalid size result: " + Arrays.toString(res));
        }
    }

    public long getAppDataInode(String uuid, String pkgname, int userid, int flags) throws InstallerException {
        String[] res = this.mInstaller.execute("get_app_data_inode", new Object[]{uuid, pkgname, Integer.valueOf(userid), Integer.valueOf(flags)});
        try {
            return Long.parseLong(res[1]);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new InstallerException("Invalid inode result: " + Arrays.toString(res));
        }
    }

    public void dexopt(String apkPath, int uid, String instructionSet, int dexoptNeeded, int dexFlags, String compilerFilter, String volumeUuid, String sharedLibraries) throws InstallerException {
        assertValidInstructionSet(instructionSet);
        this.mInstaller.dexopt(apkPath, uid, instructionSet, dexoptNeeded, dexFlags, compilerFilter, volumeUuid, sharedLibraries);
    }

    public void dexopt(String apkPath, int uid, String pkgName, String instructionSet, int dexoptNeeded, String outputPath, int dexFlags, String compilerFilter, String volumeUuid, String sharedLibraries) throws InstallerException {
        assertValidInstructionSet(instructionSet);
        this.mInstaller.dexopt(apkPath, uid, pkgName, instructionSet, dexoptNeeded, outputPath, dexFlags, compilerFilter, volumeUuid, sharedLibraries);
    }

    public boolean mergeProfiles(int uid, String pkgName) throws InstallerException {
        return this.mInstaller.mergeProfiles(uid, pkgName);
    }

    public boolean dumpProfiles(String gid, String packageName, String codePaths) throws InstallerException {
        return this.mInstaller.dumpProfiles(gid, packageName, codePaths);
    }

    public void idmap(String targetApkPath, String overlayApkPath, int uid) throws InstallerException {
        this.mInstaller.execute("idmap", new Object[]{targetApkPath, overlayApkPath, Integer.valueOf(uid)});
    }

    public void rmdex(String codePath, String instructionSet) throws InstallerException {
        assertValidInstructionSet(instructionSet);
        this.mInstaller.execute("rmdex", new Object[]{codePath, instructionSet});
    }

    public void rmPackageDir(String packageDir) throws InstallerException {
        this.mInstaller.execute("rmpackagedir", new Object[]{packageDir});
    }

    public void clearAppProfiles(String pkgName) throws InstallerException {
        this.mInstaller.execute("clear_app_profiles", new Object[]{pkgName});
    }

    public void destroyAppProfiles(String pkgName) throws InstallerException {
        this.mInstaller.execute("destroy_app_profiles", new Object[]{pkgName});
    }

    public void createUserData(String uuid, int userId, int userSerial, int flags) throws InstallerException {
        this.mInstaller.execute("create_user_data", new Object[]{uuid, Integer.valueOf(userId), Integer.valueOf(userSerial), Integer.valueOf(flags)});
    }

    public void destroyUserData(String uuid, int userId, int flags) throws InstallerException {
        this.mInstaller.execute("destroy_user_data", new Object[]{uuid, Integer.valueOf(userId), Integer.valueOf(flags)});
    }

    public void markBootComplete(String instructionSet) throws InstallerException {
        assertValidInstructionSet(instructionSet);
        this.mInstaller.execute("markbootcomplete", new Object[]{instructionSet});
    }

    public void freeCache(String uuid, long freeStorageSize) throws InstallerException {
        this.mInstaller.execute("freecache", new Object[]{uuid, Long.valueOf(freeStorageSize)});
    }

    public void unlink(String fileName) throws InstallerException {
        this.mInstaller.execute("unlink", new Object[]{fileName});
    }

    public void linkNativeLibraryDirectory(String uuid, String dataPath, String nativeLibPath32, int userId) throws InstallerException {
        this.mInstaller.execute("linklib", new Object[]{uuid, dataPath, nativeLibPath32, Integer.valueOf(userId)});
    }

    public void createOatDir(String oatDir, String dexInstructionSet) throws InstallerException {
        this.mInstaller.execute("createoatdir", new Object[]{oatDir, dexInstructionSet});
    }

    public void linkFile(String relativePath, String fromBase, String toBase) throws InstallerException {
        this.mInstaller.execute("linkfile", new Object[]{relativePath, fromBase, toBase});
    }

    public void moveAb(String apkPath, String instructionSet, String outputPath) throws InstallerException {
        this.mInstaller.execute("move_ab", new Object[]{apkPath, instructionSet, outputPath});
    }

    private static void assertValidInstructionSet(String instructionSet) throws InstallerException {
        String[] strArr = Build.SUPPORTED_ABIS;
        int i = 0;
        int length = strArr.length;
        while (i < length) {
            if (!VMRuntime.getInstructionSet(strArr[i]).equals(instructionSet)) {
                i++;
            } else {
                return;
            }
        }
        throw new InstallerException("Invalid instruction set: " + instructionSet);
    }

    public void rmClonedAppDataDir(String dir) throws InstallerException {
        if (UserHandle.getAppId(Binder.getCallingUid()) == 1000) {
            this.mInstaller.execute("rmClonedAppDataDir", new Object[]{dir});
        }
    }
}
