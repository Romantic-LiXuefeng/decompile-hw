package com.android.server.devicepolicy;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityManagerNative;
import android.app.AppGlobals;
import android.app.IUserSwitchObserver.Stub;
import android.app.NotificationManager;
import android.app.PackageInstallObserver;
import android.app.StatusBarManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.UserInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.nfc.NfcAdapter;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.IPowerManager;
import android.os.IRemoteCallback;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.storage.DiskInfo;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.os.storage.VolumeInfo;
import android.provider.Settings.Global;
import android.provider.Settings.Secure;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;
import android.util.Xml;
import com.android.internal.util.FastXmlSerializer;
import com.android.internal.util.JournaledFile;
import com.android.internal.util.XmlUtils;
import com.android.server.PPPOEStateMachine;
import com.android.server.devicepolicy.AbsDevicePolicyManagerService.HwActiveAdmin;
import com.android.server.devicepolicy.DevicePolicyManagerService.ActiveAdmin;
import com.android.server.devicepolicy.DevicePolicyManagerService.DevicePolicyData;
import com.android.server.location.gnsschrlog.GnssConnectivityLogManager;
import com.android.server.rms.iaware.hiber.constant.AppHibernateCst;
import huawei.com.android.server.policy.HwGlobalActionsData;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

public class HwDevicePolicyManagerService extends DevicePolicyManagerService implements IHwDevicePolicyManager {
    private static final boolean DBG = false;
    public static final String DESCRIPTOR = "com.android.internal.widget.ILockSettings";
    private static final String DEVICE_POLICIES_1_XML = "device_policies_1.xml";
    private static final String EXCHANGE_DOMAIN = "domain";
    private static final int EXCHANGE_PROVIDER_MAX_NUM = 20;
    private static final Set<String> HWDEVICE_OWNER_USER_RESTRICTIONS = new HashSet();
    private static final int MAX_QUERY_PROCESS = 10000;
    public static final int NOT_SUPPORT_SD_CRYPT = -1;
    private static final int OFF = 0;
    private static final int ON = 1;
    public static final String PRIVACY_MODE_ON = "privacy_mode_on";
    public static final int SD_CRYPT_STATE_DECRYPTED = 1;
    public static final int SD_CRYPT_STATE_DECRYPTING = 4;
    public static final int SD_CRYPT_STATE_ENCRYPTED = 2;
    public static final int SD_CRYPT_STATE_ENCRYPTING = 3;
    public static final int SD_CRYPT_STATE_INVALID = 0;
    public static final int SD_CRYPT_STATE_MISMATCH = 5;
    public static final int SD_CRYPT_STATE_WAIT_UNLOCK = 6;
    private static final String SETTINGS_MENUS_REMOVE = "settings_menus_remove";
    private static final int STATUS_BAR_DISABLE_MASK = 34013184;
    private static final String TAG = "HwDevicePolicyManagerService";
    private static final String USB_STORAGE = "usb";
    private static final boolean isSupportCrypt = SystemProperties.getBoolean("ro.config.support_sdcard_crypt", true);
    private static final boolean mHasHwMdmFeature = true;
    public static final int transaction_setActiveVisitorPasswordState = 1003;
    private final Context mContext;
    private HwAdminCache mHwAdminCache;
    private TransactionProcessor mProcessor = null;
    private final UserManager mUserManager;
    final SparseArray<DeviceVisitorPolicyData> mVisitorUserData = new SparseArray();

    private static class DeviceVisitorPolicyData {
        int mActivePasswordLength = 0;
        int mActivePasswordLetters = 0;
        int mActivePasswordLowerCase = 0;
        int mActivePasswordNonLetter = 0;
        int mActivePasswordNumeric = 0;
        int mActivePasswordQuality = 0;
        int mActivePasswordSymbols = 0;
        int mActivePasswordUpperCase = 0;
        int mFailedPasswordAttempts = 0;
        int mUserHandle;

        public DeviceVisitorPolicyData(int userHandle) {
            this.mUserHandle = userHandle;
        }
    }

    static class HwAdminCache {
        public static final int DISABLED_DEACTIVE_MDM_PACKAGES = 18;
        public static final int DISABLE_ADB = 11;
        public static final int DISABLE_BACK = 16;
        public static final int DISABLE_BLUETOOTH = 8;
        public static final int DISABLE_CHANGE_LAUNCHER = 17;
        public static final int DISABLE_DECRYPT_SDCARD = 19;
        public static final int DISABLE_GPS = 13;
        public static final int DISABLE_HOME = 14;
        public static final int DISABLE_INSTALLSOURCE = 2;
        public static final int DISABLE_SAFEMODE = 10;
        public static final int DISABLE_TASK = 15;
        public static final int DISABLE_USBOTG = 12;
        public static final int DISABLE_VOICE = 1;
        public static final int DISABLE_WIFI = 0;
        public static final int DISALLOWEDRUNNING_APP_LIST = 5;
        public static final int DISALLOWEDUNINSTALL_PACKAGE_LIST = 7;
        public static final int INSTALLPACKAGE_WHITELIST = 6;
        public static final int INSTALLSOURCE_WHITELIST = 3;
        public static final int NETWORK_ACCESS_WHITELIST = 9;
        public static final int PERSISTENTAPP_LIST = 4;
        private boolean disableAdb = false;
        private boolean disableBack = false;
        private boolean disableBluetooth = false;
        private boolean disableChangeLauncher = false;
        private boolean disableDecryptSDCard = false;
        private boolean disableGPS = false;
        private boolean disableHome = false;
        private boolean disableInstallSource = false;
        private boolean disableSafeMode = false;
        private boolean disableTask = false;
        private boolean disableUSBOtg = false;
        private boolean disableVoice = false;
        private boolean disableWifi = false;
        private List<String> disabledDeactiveMdmPackagesList = null;
        private List<String> disallowedRunningAppList = null;
        private List<String> disallowedUninstallPackageList = null;
        private List<String> installPackageWhitelist = null;
        private List<String> installSourceWhitelist = null;
        private Object mLock = new Object();
        private List<String> networkAccessWhitelist = null;
        private List<String> persistentAppList = null;

        HwAdminCache() {
        }

        public void syncHwAdminCache(int type, boolean value) {
            synchronized (this.mLock) {
                switch (type) {
                    case 0:
                        this.disableWifi = value;
                        break;
                    case 1:
                        this.disableVoice = value;
                        break;
                    case 2:
                        this.disableInstallSource = value;
                        break;
                    case 8:
                        this.disableBluetooth = value;
                        break;
                    case 10:
                        this.disableSafeMode = value;
                        break;
                    case 11:
                        this.disableAdb = value;
                        break;
                    case 12:
                        this.disableUSBOtg = value;
                        break;
                    case 13:
                        this.disableGPS = value;
                        break;
                    case 14:
                        this.disableHome = value;
                        break;
                    case 15:
                        this.disableTask = value;
                        break;
                    case 16:
                        this.disableBack = value;
                        break;
                    case 17:
                        this.disableChangeLauncher = value;
                        break;
                    case 19:
                        this.disableDecryptSDCard = value;
                        break;
                }
            }
        }

        public void syncHwAdminCache(int type, List<String> list) {
            synchronized (this.mLock) {
                switch (type) {
                    case 3:
                        this.installSourceWhitelist = list;
                        break;
                    case 4:
                        this.persistentAppList = list;
                        break;
                    case 5:
                        this.disallowedRunningAppList = list;
                        break;
                    case 6:
                        this.installPackageWhitelist = list;
                        break;
                    case 7:
                        this.disallowedUninstallPackageList = list;
                        break;
                    case 9:
                        this.networkAccessWhitelist = list;
                        break;
                    case 18:
                        this.disabledDeactiveMdmPackagesList = list;
                        break;
                }
            }
        }

        public boolean getCachedValue(int type) {
            boolean result = false;
            synchronized (this.mLock) {
                switch (type) {
                    case 0:
                        result = this.disableWifi;
                        break;
                    case 1:
                        result = this.disableVoice;
                        break;
                    case 2:
                        result = this.disableInstallSource;
                        break;
                    case 8:
                        result = this.disableBluetooth;
                        break;
                    case 10:
                        result = this.disableSafeMode;
                        break;
                    case 11:
                        result = this.disableAdb;
                        break;
                    case 12:
                        result = this.disableUSBOtg;
                        break;
                    case 13:
                        result = this.disableGPS;
                        break;
                    case 14:
                        result = this.disableHome;
                        break;
                    case 15:
                        result = this.disableTask;
                        break;
                    case 16:
                        result = this.disableBack;
                        break;
                    case 17:
                        result = this.disableChangeLauncher;
                        break;
                    case 19:
                        result = this.disableDecryptSDCard;
                        break;
                }
            }
            return result;
        }

        public List<String> getCachedList(int type) {
            List<String> result = null;
            synchronized (this.mLock) {
                switch (type) {
                    case 3:
                        result = this.installSourceWhitelist;
                        break;
                    case 4:
                        result = this.persistentAppList;
                        break;
                    case 5:
                        result = this.disallowedRunningAppList;
                        break;
                    case 6:
                        result = this.installPackageWhitelist;
                        break;
                    case 7:
                        result = this.disallowedUninstallPackageList;
                        break;
                    case 9:
                        result = this.networkAccessWhitelist;
                        break;
                    case 18:
                        result = this.disabledDeactiveMdmPackagesList;
                        break;
                }
            }
            return result;
        }
    }

    static {
        HWDEVICE_OWNER_USER_RESTRICTIONS.add("no_usb_file_transfer");
        HWDEVICE_OWNER_USER_RESTRICTIONS.add("no_physical_media");
        HWDEVICE_OWNER_USER_RESTRICTIONS.add("no_outgoing_calls");
        HWDEVICE_OWNER_USER_RESTRICTIONS.add("no_sms");
        HWDEVICE_OWNER_USER_RESTRICTIONS.add("no_config_tethering");
    }

    public HwDevicePolicyManagerService(Context context) {
        super(context);
        this.mContext = context;
        this.mUserManager = UserManager.get(context);
        HwDevicePolicyManagerServiceUtil.initialize(context);
        this.mProcessor = new TransactionProcessor(this);
        this.mHwAdminCache = new HwAdminCache();
    }

    DeviceVisitorPolicyData getVisitorUserData(int userHandle) {
        DeviceVisitorPolicyData policy;
        synchronized (this) {
            policy = (DeviceVisitorPolicyData) this.mVisitorUserData.get(userHandle);
            if (policy == null) {
                policy = new DeviceVisitorPolicyData(userHandle);
                this.mVisitorUserData.append(userHandle, policy);
                loadVisitorSettingsLocked(policy, userHandle);
            }
        }
        return policy;
    }

    private static JournaledFile makeJournaledFile2(int userHandle) {
        String base;
        if (userHandle == 0) {
            base = "/data/system/device_policies_1.xml";
        } else {
            base = new File(Environment.getUserSystemDirectory(userHandle), DEVICE_POLICIES_1_XML).getAbsolutePath();
        }
        return new JournaledFile(new File(base), new File(base + ".tmp"));
    }

    private void saveVisitorSettingsLock(int userHandle) {
        Throwable th;
        DeviceVisitorPolicyData policy = getVisitorUserData(userHandle);
        JournaledFile journal = makeJournaledFile2(userHandle);
        FileOutputStream fileOutputStream = null;
        try {
            FileOutputStream stream = new FileOutputStream(journal.chooseForWrite(), false);
            try {
                XmlSerializer out = new FastXmlSerializer();
                out.setOutput(stream, "utf-8");
                out.startDocument(null, Boolean.valueOf(true));
                out.startTag(null, "policies");
                if (policy.mActivePasswordQuality == 0 && policy.mActivePasswordLength == 0) {
                    if (policy.mActivePasswordUpperCase == 0 && policy.mActivePasswordLowerCase == 0 && policy.mActivePasswordLetters == 0 && policy.mActivePasswordNumeric == 0 && policy.mActivePasswordSymbols == 0) {
                        if (policy.mActivePasswordNonLetter != 0) {
                        }
                        out.endTag(null, "policies");
                        out.endDocument();
                        journal.commit();
                        if (stream != null) {
                            try {
                                stream.close();
                            } catch (IOException e) {
                            }
                        }
                        fileOutputStream = stream;
                    }
                }
                out.startTag(null, "active-password2");
                out.attribute(null, "quality", Integer.toString(policy.mActivePasswordQuality));
                out.attribute(null, "length", Integer.toString(policy.mActivePasswordLength));
                out.attribute(null, "uppercase", Integer.toString(policy.mActivePasswordUpperCase));
                out.attribute(null, "lowercase", Integer.toString(policy.mActivePasswordLowerCase));
                out.attribute(null, "letters", Integer.toString(policy.mActivePasswordLetters));
                out.attribute(null, "numeric", Integer.toString(policy.mActivePasswordNumeric));
                out.attribute(null, "symbols", Integer.toString(policy.mActivePasswordSymbols));
                out.attribute(null, "nonletter", Integer.toString(policy.mActivePasswordNonLetter));
                out.endTag(null, "active-password2");
                out.endTag(null, "policies");
                out.endDocument();
                journal.commit();
                if (stream != null) {
                    stream.close();
                }
                fileOutputStream = stream;
            } catch (IOException e2) {
                fileOutputStream = stream;
                try {
                    journal.rollback();
                    if (fileOutputStream != null) {
                        try {
                            fileOutputStream.close();
                        } catch (IOException e3) {
                        }
                    }
                } catch (Throwable th2) {
                    th = th2;
                    if (fileOutputStream != null) {
                        try {
                            fileOutputStream.close();
                        } catch (IOException e4) {
                        }
                    }
                    throw th;
                }
            } catch (Throwable th3) {
                th = th3;
                fileOutputStream = stream;
                if (fileOutputStream != null) {
                    fileOutputStream.close();
                }
                throw th;
            }
        } catch (IOException e5) {
            journal.rollback();
            if (fileOutputStream != null) {
                fileOutputStream.close();
            }
        }
    }

    private void loadVisitorSettingsLocked(DeviceVisitorPolicyData policy, int userHandle) {
        NullPointerException e;
        NumberFormatException e2;
        XmlPullParserException e3;
        IOException e4;
        IndexOutOfBoundsException e5;
        FileInputStream fileInputStream = null;
        File file = makeJournaledFile2(userHandle).chooseForRead();
        try {
            FileInputStream stream = new FileInputStream(file);
            try {
                int type;
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(stream, null);
                do {
                    type = parser.next();
                    if (type == 1) {
                        break;
                    }
                } while (type != 2);
                String tag = parser.getName();
                if ("policies".equals(tag)) {
                    type = parser.next();
                    int outerDepth = parser.getDepth();
                    while (true) {
                        type = parser.next();
                        if (type == 1 || (type == 3 && parser.getDepth() <= outerDepth)) {
                            fileInputStream = stream;
                        } else if (!(type == 3 || type == 4)) {
                            tag = parser.getName();
                            if ("active-password2".equals(tag)) {
                                policy.mActivePasswordQuality = Integer.parseInt(parser.getAttributeValue(null, "quality"));
                                policy.mActivePasswordLength = Integer.parseInt(parser.getAttributeValue(null, "length"));
                                policy.mActivePasswordUpperCase = Integer.parseInt(parser.getAttributeValue(null, "uppercase"));
                                policy.mActivePasswordLowerCase = Integer.parseInt(parser.getAttributeValue(null, "lowercase"));
                                policy.mActivePasswordLetters = Integer.parseInt(parser.getAttributeValue(null, "letters"));
                                policy.mActivePasswordNumeric = Integer.parseInt(parser.getAttributeValue(null, "numeric"));
                                policy.mActivePasswordSymbols = Integer.parseInt(parser.getAttributeValue(null, "symbols"));
                                policy.mActivePasswordNonLetter = Integer.parseInt(parser.getAttributeValue(null, "nonletter"));
                                XmlUtils.skipCurrentTag(parser);
                            } else {
                                Slog.w(TAG, "Unknown tag: " + tag);
                                XmlUtils.skipCurrentTag(parser);
                            }
                        }
                    }
                    fileInputStream = stream;
                    if (fileInputStream != null) {
                        try {
                            fileInputStream.close();
                            return;
                        } catch (IOException e6) {
                            return;
                        }
                    }
                    return;
                }
                throw new XmlPullParserException("Settings do not start with policies tag: found " + tag);
            } catch (NullPointerException e7) {
                e = e7;
                fileInputStream = stream;
            } catch (NumberFormatException e8) {
                e2 = e8;
                fileInputStream = stream;
            } catch (XmlPullParserException e9) {
                e3 = e9;
                fileInputStream = stream;
            } catch (FileNotFoundException e10) {
                fileInputStream = stream;
            } catch (IOException e11) {
                e4 = e11;
                fileInputStream = stream;
            } catch (IndexOutOfBoundsException e12) {
                e5 = e12;
                fileInputStream = stream;
            }
        } catch (NullPointerException e13) {
            e = e13;
            Slog.w(TAG, "failed parsing " + file + " " + e);
        } catch (NumberFormatException e14) {
            e2 = e14;
            Slog.w(TAG, "failed parsing " + file + " " + e2);
        } catch (XmlPullParserException e15) {
            e3 = e15;
            Slog.w(TAG, "failed parsing " + file + " " + e3);
        } catch (FileNotFoundException e16) {
        } catch (IOException e17) {
            e4 = e17;
            Slog.w(TAG, "failed parsing " + file + " " + e4);
        } catch (IndexOutOfBoundsException e18) {
            e5 = e18;
            Slog.w(TAG, "failed parsing " + file + " " + e5);
        }
    }

    private boolean isPrivacyModeEnabled() {
        if (Secure.getInt(this.mContext.getContentResolver(), PRIVACY_MODE_ON, 0) == 1) {
            return isFeatrueSupported();
        }
        return false;
    }

    private static boolean isFeatrueSupported() {
        return SystemProperties.getBoolean("ro.config.hw_privacymode", false);
    }

    public void systemReady(int phase) {
        super.systemReady(phase);
        if (isFeatrueSupported()) {
            if (this.mHasFeature) {
                Slog.w(TAG, "systemReady");
                synchronized (this) {
                    loadVisitorSettingsLocked(getVisitorUserData(0), 0);
                }
            } else {
                return;
            }
        }
        listenForUserSwitches();
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public boolean isActivePasswordSufficient(int userHandle, boolean parent) {
        boolean z = true;
        if (!isPrivacyModeEnabled()) {
            return super.isActivePasswordSufficient(userHandle, parent);
        }
        if (!super.isActivePasswordSufficient(userHandle, parent)) {
            return false;
        }
        Slog.w(TAG, "super is ActivePassword Sufficient");
        if (!this.mHasFeature) {
            return true;
        }
        synchronized (this) {
            DeviceVisitorPolicyData policy = getVisitorUserData(userHandle);
            if (policy.mActivePasswordQuality < getPasswordQuality(null, userHandle, parent) || policy.mActivePasswordLength < getPasswordMinimumLength(null, userHandle, parent)) {
            } else if (policy.mActivePasswordQuality != 393216) {
                return true;
            } else if (policy.mActivePasswordUpperCase < getPasswordMinimumUpperCase(null, userHandle, parent) || policy.mActivePasswordLowerCase < getPasswordMinimumLowerCase(null, userHandle, parent) || policy.mActivePasswordLetters < getPasswordMinimumLetters(null, userHandle, parent) || policy.mActivePasswordNumeric < getPasswordMinimumNumeric(null, userHandle, parent) || policy.mActivePasswordSymbols < getPasswordMinimumSymbols(null, userHandle, parent)) {
                z = false;
            } else if (policy.mActivePasswordNonLetter < getPasswordMinimumNonLetter(null, userHandle, parent)) {
                z = false;
            }
        }
    }

    public void setActiveVisitorPasswordState(int quality, int length, int letters, int uppercase, int lowercase, int numbers, int symbols, int nonletter, int userHandle) {
        if (this.mHasFeature) {
            enforceFullCrossUsersPermission(userHandle);
            this.mContext.enforceCallingOrSelfPermission("android.permission.BIND_DEVICE_ADMIN", null);
            DeviceVisitorPolicyData p = getVisitorUserData(userHandle);
            validateQualityConstant(quality);
            synchronized (this) {
                if (p.mActivePasswordQuality == quality && p.mActivePasswordLength == length && p.mFailedPasswordAttempts == 0 && p.mActivePasswordLetters == letters && p.mActivePasswordUpperCase == uppercase && p.mActivePasswordLowerCase == lowercase && p.mActivePasswordNumeric == numbers && p.mActivePasswordSymbols == symbols) {
                    if (p.mActivePasswordNonLetter != nonletter) {
                    }
                }
                long ident = Binder.clearCallingIdentity();
                try {
                    p.mActivePasswordQuality = quality;
                    p.mActivePasswordLength = length;
                    p.mActivePasswordLetters = letters;
                    p.mActivePasswordLowerCase = lowercase;
                    p.mActivePasswordUpperCase = uppercase;
                    p.mActivePasswordNumeric = numbers;
                    p.mActivePasswordSymbols = symbols;
                    p.mActivePasswordNonLetter = nonletter;
                    p.mFailedPasswordAttempts = 0;
                    saveVisitorSettingsLock(userHandle);
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
            }
        }
    }

    public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (this.mProcessor.processTransaction(code, data, reply)) {
            return true;
        }
        switch (code) {
            case 1003:
                Slog.w(TAG, "transaction_setActiveVisitorPasswordState");
                data.enforceInterface("com.android.internal.widget.ILockSettings");
                setActiveVisitorPasswordState(data.readInt(), data.readInt(), data.readInt(), data.readInt(), data.readInt(), data.readInt(), data.readInt(), data.readInt(), data.readInt());
                reply.writeNoException();
                return true;
            default:
                return super.onTransact(code, data, reply, flags);
        }
    }

    private void listenForUserSwitches() {
        try {
            ActivityManagerNative.getDefault().registerUserSwitchObserver(new Stub() {
                public void onUserSwitching(int newUserId, IRemoteCallback reply) {
                }

                public void onUserSwitchComplete(int newUserId) throws RemoteException {
                    HwDevicePolicyManagerService.this.syncHwDeviceSettingsLocked(newUserId);
                }

                public void onForegroundProfileSwitch(int newProfileId) {
                }
            });
        } catch (RemoteException e) {
            Slog.w(TAG, "Failed to listen for user switching event", e);
        }
    }

    private void enforceHwCrossUserPermission(int userHandle) {
        enforceFullCrossUsersPermission(userHandle);
        if (userHandle != 0) {
            throw new IllegalArgumentException("Invalid userId " + userHandle + ",should be:" + 0);
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setWifiDisabled(ComponentName who, boolean disabled, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_WIFI", "does not have wifi MDM permission!");
        synchronized (this) {
            if (who == null) {
                throw new IllegalArgumentException("ComponentName is null");
            }
            HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
            if (admin.disableWifi != disabled) {
                long callingId = Binder.clearCallingIdentity();
                try {
                    WifiManager mWifiManager = (WifiManager) this.mContext.getSystemService(GnssConnectivityLogManager.SUBSYS_WIFI);
                    if (mWifiManager.isWifiEnabled() && disabled && !mWifiManager.setWifiEnabled(false)) {
                        Binder.restoreCallingIdentity(callingId);
                        return;
                    }
                    admin.disableWifi = disabled;
                    saveSettingsLocked(userHandle);
                } finally {
                    Binder.restoreCallingIdentity(callingId);
                }
            }
        }
    }

    public boolean isWifiDisabled(ComponentName who, int userHandle) {
        synchronized (this) {
            if (who != null) {
                boolean z = getHwActiveAdmin(who, userHandle).disableWifi;
                return z;
            }
            DevicePolicyData policy = getUserData(userHandle);
            int N = policy.mAdminList.size();
            int i = 0;
            while (i < N) {
                ActiveAdmin admin = (ActiveAdmin) policy.mAdminList.get(i);
                if (admin.mHwActiveAdmin == null || !admin.mHwActiveAdmin.disableWifi) {
                    i++;
                } else {
                    return true;
                }
            }
            return false;
        }
    }

    public boolean isBluetoothDisabled(ComponentName who, int userHandle) {
        synchronized (this) {
            if (who != null) {
                boolean z = getHwActiveAdmin(who, userHandle).disableBluetooth;
                return z;
            }
            DevicePolicyData policy = getUserData(userHandle);
            int N = policy.mAdminList.size();
            int i = 0;
            while (i < N) {
                ActiveAdmin admin = (ActiveAdmin) policy.mAdminList.get(i);
                if (admin.mHwActiveAdmin == null || !admin.mHwActiveAdmin.disableBluetooth) {
                    i++;
                } else {
                    return true;
                }
            }
            return false;
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setBluetoothDisabled(ComponentName who, boolean disabled, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_BLUETOOTH", "does not have bluethooth MDM permission!");
        synchronized (this) {
            if (who == null) {
                throw new IllegalArgumentException("ComponentName is null");
            }
            HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
            if (admin.disableBluetooth != disabled) {
                long callingId = Binder.clearCallingIdentity();
                try {
                    BluetoothAdapter mBTAdapter = ((BluetoothManager) this.mContext.getSystemService("bluetooth")).getAdapter();
                    if (mBTAdapter.isEnabled() && disabled && !mBTAdapter.disable()) {
                        Binder.restoreCallingIdentity(callingId);
                        return;
                    }
                    admin.disableBluetooth = disabled;
                    saveSettingsLocked(userHandle);
                } finally {
                    Binder.restoreCallingIdentity(callingId);
                }
            }
        }
    }

    public void setWifiApDisabled(ComponentName who, boolean disabled, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_WIFI", "does not have Wifi AP MDM permission!");
        synchronized (this) {
            enforceUserRestrictionPermission(who, "no_config_tethering", userHandle);
            HwActiveAdmin ap = getHwActiveAdmin(who, userHandle);
            if (ap.disableWifiAp != disabled) {
                ap.disableWifiAp = disabled;
                saveSettingsLocked(userHandle);
            }
            hwSyncDeviceCapabilitiesLocked("no_config_tethering", userHandle);
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public boolean isWifiApDisabled(ComponentName who, int userHandle) {
        boolean z = false;
        synchronized (this) {
            if (who != null) {
                HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
                if (admin != null) {
                    z = admin.disableWifiAp;
                }
            } else if (this.mUserManager.hasUserRestriction("no_config_tethering", new UserHandle(userHandle))) {
                DevicePolicyData policy = getUserData(userHandle);
                int N = policy.mAdminList.size();
                int i = 0;
                while (i < N) {
                    ActiveAdmin admin2 = (ActiveAdmin) policy.mAdminList.get(i);
                    if (admin2.mHwActiveAdmin == null || !admin2.mHwActiveAdmin.disableWifiAp) {
                        i++;
                    } else {
                        return true;
                    }
                }
                return false;
            } else {
                return false;
            }
        }
    }

    public void setBootLoaderDisabled(ComponentName who, boolean disabled, int userHandle) {
    }

    public boolean isBootLoaderDisabled(ComponentName who, int userHandle) {
        return false;
    }

    public void setUSBDataDisabled(ComponentName who, boolean disabled, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_USB", "does not have USB MDM permission!");
        synchronized (this) {
            enforceUserRestrictionPermission(who, "no_usb_file_transfer", userHandle);
            HwActiveAdmin ap = getHwActiveAdmin(who, userHandle);
            if (ap.disableUSBData != disabled) {
                ap.disableUSBData = disabled;
                saveSettingsLocked(userHandle);
            }
            hwSyncDeviceCapabilitiesLocked("no_usb_file_transfer", userHandle);
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public boolean isUSBDataDisabled(ComponentName who, int userHandle) {
        boolean z = false;
        synchronized (this) {
            if (who != null) {
                HwActiveAdmin ap = getHwActiveAdmin(who, userHandle);
                if (ap != null) {
                    z = ap.disableUSBData;
                }
            } else if (this.mUserManager.hasUserRestriction("no_usb_file_transfer", new UserHandle(userHandle))) {
                DevicePolicyData policy = getUserData(userHandle);
                int N = policy.mAdminList.size();
                int i = 0;
                while (i < N) {
                    ActiveAdmin admin = (ActiveAdmin) policy.mAdminList.get(i);
                    if (admin.mHwActiveAdmin == null || !admin.mHwActiveAdmin.disableUSBData) {
                        i++;
                    } else {
                        return true;
                    }
                }
                return false;
            } else {
                return false;
            }
        }
    }

    public void setExternalStorageDisabled(ComponentName who, boolean disabled, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_SDCARD", "does not have SDCARD MDM permission!");
        synchronized (this) {
            enforceUserRestrictionPermission(who, "no_physical_media", userHandle);
            HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
            if (admin.disableExternalStorage != disabled) {
                admin.disableExternalStorage = disabled;
                saveSettingsLocked(userHandle);
            }
            hwSyncDeviceCapabilitiesLocked("no_physical_media", userHandle);
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public boolean isExternalStorageDisabled(ComponentName who, int userHandle) {
        boolean z = false;
        synchronized (this) {
            if (who != null) {
                HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
                if (admin != null) {
                    z = admin.disableExternalStorage;
                }
            } else if (this.mUserManager.hasUserRestriction("no_physical_media", new UserHandle(userHandle))) {
                DevicePolicyData policy = getUserData(userHandle);
                int N = policy.mAdminList.size();
                int i = 0;
                while (i < N) {
                    ActiveAdmin admin2 = (ActiveAdmin) policy.mAdminList.get(i);
                    if (admin2.mHwActiveAdmin == null || !admin2.mHwActiveAdmin.disableExternalStorage) {
                        i++;
                    } else {
                        return true;
                    }
                }
                return false;
            } else {
                return false;
            }
        }
    }

    public void setNFCDisabled(ComponentName who, boolean disabled, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_NFC", "does not have NFC MDM permission!");
        synchronized (this) {
            if (who == null) {
                throw new NullPointerException("ComponentName is null");
            }
            HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
            if (admin.disableNFC != disabled) {
                long callingId = Binder.clearCallingIdentity();
                try {
                    NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this.mContext);
                    if (nfcAdapter != null) {
                        boolean nfcOriginalState = nfcAdapter.isEnabled();
                        if (disabled && nfcOriginalState) {
                            boolean setDisableResult = nfcAdapter.disable();
                        }
                    }
                    admin.disableNFC = disabled;
                    saveSettingsLocked(userHandle);
                } finally {
                    Binder.restoreCallingIdentity(callingId);
                }
            }
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public boolean isNFCDisabled(ComponentName who, int userHandle) {
        boolean z = false;
        synchronized (this) {
            if (who != null) {
                HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
                if (admin != null) {
                    z = admin.disableNFC;
                }
            } else {
                DevicePolicyData policy = getUserData(userHandle);
                int N = policy.mAdminList.size();
                int i = 0;
                while (i < N) {
                    ActiveAdmin admin2 = (ActiveAdmin) policy.mAdminList.get(i);
                    if (admin2.mHwActiveAdmin == null || !admin2.mHwActiveAdmin.disableNFC) {
                        i++;
                    } else {
                        return true;
                    }
                }
                return false;
            }
        }
    }

    public void setDataConnectivityDisabled(ComponentName who, boolean disabled, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_CONNECTIVITY", "Does not hava data connectivity MDM permission.");
        synchronized (this) {
            if (who == null) {
                throw new IllegalArgumentException("ComponentName is null");
            }
            HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
            if (admin.disableDataConnectivity != disabled) {
                admin.disableDataConnectivity = disabled;
                saveSettingsLocked(userHandle);
            }
            if (disabled) {
                boolean z;
                TelephonyManager from = TelephonyManager.from(this.mContext);
                if (disabled) {
                    z = false;
                } else {
                    z = true;
                }
                from.setDataEnabled(z);
            }
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public boolean isDataConnectivityDisabled(ComponentName who, int userHandle) {
        boolean z = false;
        synchronized (this) {
            if (who != null) {
                HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
                if (admin != null) {
                    z = admin.disableDataConnectivity;
                }
            } else {
                DevicePolicyData policy = getUserData(userHandle);
                int N = policy.mAdminList.size();
                int i = 0;
                while (i < N) {
                    ActiveAdmin admin2 = (ActiveAdmin) policy.mAdminList.get(i);
                    if (admin2.mHwActiveAdmin == null || !admin2.mHwActiveAdmin.disableDataConnectivity) {
                        i++;
                    } else {
                        return true;
                    }
                }
                return false;
            }
        }
    }

    public void setVoiceDisabled(ComponentName who, boolean disabled, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_PHONE", "Does not hava phone disable MDM permission.");
        synchronized (this) {
            enforceUserRestrictionPermission(who, "no_outgoing_calls", userHandle);
            HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
            if (admin.disableVoice != disabled) {
                admin.disableVoice = disabled;
                saveSettingsLocked(userHandle);
            }
            hwSyncDeviceCapabilitiesLocked("no_outgoing_calls", userHandle);
        }
        if (this.mHwAdminCache != null) {
            this.mHwAdminCache.syncHwAdminCache(1, isVoiceDisabled(null, userHandle));
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public boolean isVoiceDisabled(ComponentName who, int userHandle) {
        boolean z = false;
        synchronized (this) {
            if (who != null) {
                HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
                if (admin != null) {
                    z = admin.disableVoice;
                }
            } else if (this.mUserManager.hasUserRestriction("no_outgoing_calls", new UserHandle(userHandle))) {
                DevicePolicyData policy = getUserData(userHandle);
                int N = policy.mAdminList.size();
                int i = 0;
                while (i < N) {
                    ActiveAdmin admin2 = (ActiveAdmin) policy.mAdminList.get(i);
                    if (admin2.mHwActiveAdmin == null || !admin2.mHwActiveAdmin.disableVoice) {
                        i++;
                    } else {
                        return true;
                    }
                }
                return false;
            } else {
                return false;
            }
        }
    }

    public void setSMSDisabled(ComponentName who, boolean disabled, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_MMS", "Does not hava SMS disable MDM permission.");
        synchronized (this) {
            enforceUserRestrictionPermission(who, "no_sms", userHandle);
            HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
            if (admin.disableSMS != disabled) {
                admin.disableSMS = disabled;
                saveSettingsLocked(userHandle);
            }
            hwSyncDeviceCapabilitiesLocked("no_sms", userHandle);
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public boolean isSMSDisabled(ComponentName who, int userHandle) {
        boolean z = false;
        synchronized (this) {
            if (who != null) {
                HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
                if (admin != null) {
                    z = admin.disableSMS;
                }
            } else if (this.mUserManager.hasUserRestriction("no_sms", new UserHandle(userHandle))) {
                DevicePolicyData policy = getUserData(userHandle);
                int N = policy.mAdminList.size();
                int i = 0;
                while (i < N) {
                    ActiveAdmin admin2 = (ActiveAdmin) policy.mAdminList.get(i);
                    if (admin2.mHwActiveAdmin == null || !admin2.mHwActiveAdmin.disableSMS) {
                        i++;
                    } else {
                        return true;
                    }
                }
                return false;
            } else {
                return false;
            }
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setStatusBarExpandPanelDisabled(ComponentName who, boolean disabled, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_DEVICE_MANAGER", "does not have DEVICE MANAGER permission!");
        synchronized (this) {
            HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
            if (admin.disableStatusBarExpandPanel != disabled) {
                if (!disabled || setStatusBarPanelDisabledInternal(userHandle)) {
                    admin.disableStatusBarExpandPanel = disabled;
                    saveSettingsLocked(userHandle);
                    if (!disabled) {
                        setStatusBarPanelEnableInternal(false, userHandle);
                    }
                } else {
                    Log.w(TAG, "cannot set statusBar disabled");
                }
            }
        }
    }

    private boolean setStatusBarPanelDisabledInternal(int userHandle) {
        long callingId = Binder.clearCallingIdentity();
        try {
            StatusBarManager statusBar = (StatusBarManager) this.mContext.getSystemService("statusbar");
            if (statusBar == null) {
                Log.w(TAG, "statusBar is null");
                return false;
            }
            statusBar.disable(STATUS_BAR_DISABLE_MASK);
            Binder.restoreCallingIdentity(callingId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "failed to set statusBar disabled.");
            return false;
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    private boolean setStatusBarPanelEnableInternal(boolean forceEnable, int userHandle) {
        long callingId = Binder.clearCallingIdentity();
        try {
            StatusBarManager statusBar = (StatusBarManager) this.mContext.getSystemService("statusbar");
            if (statusBar == null) {
                Log.w(TAG, "statusBar is null");
                return false;
            }
            if (forceEnable) {
                statusBar.disable(0);
            } else if (!isStatusBarExpandPanelDisabled(null, userHandle)) {
                statusBar.disable(0);
            }
            Binder.restoreCallingIdentity(callingId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "failed to set statusBar enabled.");
            return false;
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public boolean isStatusBarExpandPanelDisabled(ComponentName who, int userHandle) {
        boolean z = false;
        synchronized (this) {
            if (who != null) {
                HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
                if (admin != null) {
                    z = admin.disableStatusBarExpandPanel;
                }
            } else {
                DevicePolicyData policy = getUserData(userHandle);
                int N = policy.mAdminList.size();
                int i = 0;
                while (i < N) {
                    ActiveAdmin admin2 = (ActiveAdmin) policy.mAdminList.get(i);
                    if (admin2.mHwActiveAdmin == null || !admin2.mHwActiveAdmin.disableStatusBarExpandPanel) {
                        i++;
                    } else {
                        return true;
                    }
                }
                return false;
            }
        }
    }

    public void hangupCalling(ComponentName who, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_PHONE", "Does not hava hangup calling permission.");
        synchronized (this) {
            if (who == null) {
                throw new IllegalArgumentException("ComponentName is null");
            }
            if (getHwActiveAdmin(who, userHandle) != null) {
                long ident = Binder.clearCallingIdentity();
                try {
                    TelephonyManager.from(this.mContext).endCall();
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
            }
        }
    }

    public void installPackage(ComponentName who, String packagePath, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_APP_MANAGEMENT", "Does not hava application management permission.");
        synchronized (this) {
            if (who == null) {
                throw new IllegalArgumentException("ComponentName is null");
            }
            if (getHwActiveAdmin(who, userHandle) != null) {
                installPackage(packagePath, who.getPackageName());
            }
        }
    }

    public void uninstallPackage(ComponentName who, String packageName, boolean keepData, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_APP_MANAGEMENT", "Does not hava application management permission.");
        if (HwDevicePolicyManagerServiceUtil.isValidatePackageName(packageName)) {
            synchronized (this) {
                if (who == null) {
                    throw new IllegalArgumentException("ComponentName is null");
                }
                if (getHwActiveAdmin(who, userHandle) != null) {
                    uninstallPackage(packageName, keepData);
                }
            }
            return;
        }
        throw new IllegalArgumentException("packageName:" + packageName + " is invalid.");
    }

    public void clearPackageData(ComponentName who, String packageName, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_APP_MANAGEMENT", "does not have wifi MDM permission!");
        if (HwDevicePolicyManagerServiceUtil.isValidatePackageName(packageName)) {
            synchronized (this) {
                if (who == null) {
                    throw new IllegalArgumentException("ComponentName is null");
                } else if (TextUtils.isEmpty(packageName)) {
                    throw new IllegalArgumentException("packageNames is null or empty");
                } else {
                    enforceCheckNotSystemApp(packageName, userHandle);
                    if (getHwActiveAdmin(who, userHandle) != null) {
                        long id = Binder.clearCallingIdentity();
                        try {
                            boolean ret = ((ActivityManager) this.mContext.getSystemService("activity")).clearApplicationUserData(packageName, null);
                        } finally {
                            Binder.restoreCallingIdentity(id);
                        }
                    }
                }
            }
            return;
        }
        throw new IllegalArgumentException("packageName:" + packageName + " is invalid.");
    }

    public void enableInstallPackage(ComponentName who, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_APP_MANAGEMENT", "does not have wifi MDM permission!");
        synchronized (this) {
            if (who == null) {
                throw new IllegalArgumentException("ComponentName is null");
            }
            HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
            if (admin != null) {
                admin.disableInstallSource = false;
                admin.installSourceWhitelist = null;
            }
            saveSettingsLocked(userHandle);
        }
        if (this.mHwAdminCache != null) {
            this.mHwAdminCache.syncHwAdminCache(2, isInstallSourceDisabled(null, userHandle));
        }
    }

    public void disableInstallSource(ComponentName who, List<String> whitelist, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_APP_MANAGEMENT", "does not have wifi MDM permission!");
        if (HwDevicePolicyManagerServiceUtil.isValidatePackageNames(whitelist)) {
            synchronized (this) {
                if (who == null) {
                    throw new IllegalArgumentException("ComponentName is null");
                }
                if (whitelist != null) {
                    if (!whitelist.isEmpty()) {
                        HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
                        admin.disableInstallSource = true;
                        if (admin.installSourceWhitelist == null) {
                            admin.installSourceWhitelist = new ArrayList();
                        }
                        HwDevicePolicyManagerServiceUtil.isOverLimit(admin.installSourceWhitelist, whitelist);
                        HwDevicePolicyManagerServiceUtil.addListWithoutDuplicate(admin.installSourceWhitelist, whitelist);
                        saveSettingsLocked(userHandle);
                    }
                }
                throw new IllegalArgumentException("packageNames is null or empty");
            }
            if (this.mHwAdminCache != null) {
                this.mHwAdminCache.syncHwAdminCache(2, isInstallSourceDisabled(null, userHandle));
                this.mHwAdminCache.syncHwAdminCache(3, getInstallPackageSourceWhiteList(null, userHandle));
                return;
            }
            return;
        }
        throw new IllegalArgumentException("packageName:" + whitelist + " is invalid.");
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public boolean isInstallSourceDisabled(ComponentName who, int userHandle) {
        boolean z = false;
        synchronized (this) {
            if (who != null) {
                HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
                if (admin != null) {
                    z = admin.disableInstallSource;
                }
            } else {
                DevicePolicyData policy = getUserData(userHandle);
                int N = policy.mAdminList.size();
                int i = 0;
                while (i < N) {
                    ActiveAdmin admin2 = (ActiveAdmin) policy.mAdminList.get(i);
                    if (admin2.mHwActiveAdmin == null || !admin2.mHwActiveAdmin.disableInstallSource) {
                        i++;
                    } else {
                        return true;
                    }
                }
                return false;
            }
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public List<String> getInstallPackageSourceWhiteList(ComponentName who, int userHandle) {
        List<String> list = null;
        synchronized (this) {
            if (who != null) {
                HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
                if (!(admin.installSourceWhitelist == null || admin.installSourceWhitelist.isEmpty())) {
                    list = admin.installSourceWhitelist;
                }
            } else {
                DevicePolicyData policy = getUserData(userHandle);
                ArrayList<String> whiteList = new ArrayList();
                int N = policy.mAdminList.size();
                for (int i = 0; i < N; i++) {
                    ActiveAdmin admin2 = (ActiveAdmin) policy.mAdminList.get(i);
                    if (admin2.mHwActiveAdmin != null) {
                        HwDevicePolicyManagerServiceUtil.addListWithoutDuplicate(whiteList, admin2.mHwActiveAdmin.installSourceWhitelist);
                    }
                }
                return whiteList;
            }
        }
    }

    public void addPersistentApp(ComponentName who, List<String> packageNames, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_APP_MANAGEMENT", "Does not hava manager app MDM permission.");
        if (HwDevicePolicyManagerServiceUtil.isValidatePackageNames(packageNames)) {
            synchronized (this) {
                if (who == null) {
                    throw new IllegalArgumentException("ComponentName is null");
                }
                if (packageNames != null) {
                    if (!packageNames.isEmpty()) {
                        HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
                        if (admin.persistentAppList == null) {
                            admin.persistentAppList = new ArrayList();
                        }
                        HwDevicePolicyManagerServiceUtil.isOverLimit(admin.persistentAppList, packageNames);
                        filterOutSystemAppList(packageNames, userHandle);
                        HwDevicePolicyManagerServiceUtil.addListWithoutDuplicate(admin.persistentAppList, packageNames);
                        saveSettingsLocked(userHandle);
                    }
                }
                throw new IllegalArgumentException("packageNames is null or empty");
            }
            if (this.mHwAdminCache != null) {
                this.mHwAdminCache.syncHwAdminCache(4, getPersistentApp(null, userHandle));
                return;
            }
            return;
        }
        throw new IllegalArgumentException("packageName:" + packageNames + " is invalid.");
    }

    public void removePersistentApp(ComponentName who, List<String> packageNames, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_APP_MANAGEMENT", "Does not hava manager app MDM permission.");
        if (HwDevicePolicyManagerServiceUtil.isValidatePackageNames(packageNames)) {
            synchronized (this) {
                if (who == null) {
                    throw new IllegalArgumentException("ComponentName is null");
                }
                if (packageNames != null) {
                    if (!packageNames.isEmpty()) {
                        HwDevicePolicyManagerServiceUtil.removeItemsFromList(getHwActiveAdmin(who, userHandle).persistentAppList, packageNames);
                        saveSettingsLocked(userHandle);
                    }
                }
                throw new IllegalArgumentException("packageNames is null or empty");
            }
            if (this.mHwAdminCache != null) {
                this.mHwAdminCache.syncHwAdminCache(4, getPersistentApp(null, userHandle));
                return;
            }
            return;
        }
        throw new IllegalArgumentException("packageName:" + packageNames + " is invalid.");
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public List<String> getPersistentApp(ComponentName who, int userHandle) {
        List<String> list = null;
        synchronized (this) {
            if (who != null) {
                HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
                if (!(admin.persistentAppList == null || admin.persistentAppList.isEmpty())) {
                    list = admin.persistentAppList;
                }
            } else {
                DevicePolicyData policy = getUserData(userHandle);
                ArrayList<String> totalList = new ArrayList();
                int N = policy.mAdminList.size();
                for (int i = 0; i < N; i++) {
                    ActiveAdmin admin2 = (ActiveAdmin) policy.mAdminList.get(i);
                    if (admin2.mHwActiveAdmin != null) {
                        HwDevicePolicyManagerServiceUtil.addListWithoutDuplicate(totalList, admin2.mHwActiveAdmin.persistentAppList);
                    }
                }
                if (!totalList.isEmpty()) {
                    Object obj = totalList;
                }
            }
        }
    }

    public void addDisallowedRunningApp(ComponentName who, List<String> packageNames, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_APP_MANAGEMENT", "Does not hava manager app MDM permission.");
        if (HwDevicePolicyManagerServiceUtil.isValidatePackageNames(packageNames)) {
            synchronized (this) {
                if (who == null) {
                    throw new IllegalArgumentException("ComponentName is null");
                }
                if (packageNames != null) {
                    if (!packageNames.isEmpty()) {
                        HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
                        if (admin.disallowedRunningAppList == null) {
                            admin.disallowedRunningAppList = new ArrayList();
                        }
                        HwDevicePolicyManagerServiceUtil.isOverLimit(admin.disallowedRunningAppList, packageNames);
                        filterOutSystemAppList(packageNames, userHandle);
                        HwDevicePolicyManagerServiceUtil.addListWithoutDuplicate(admin.disallowedRunningAppList, packageNames);
                        saveSettingsLocked(userHandle);
                        for (String packageName : packageNames) {
                            killApplicationInner(packageName);
                        }
                    }
                }
                throw new IllegalArgumentException("packageNames is null or empty");
            }
            if (this.mHwAdminCache != null) {
                this.mHwAdminCache.syncHwAdminCache(5, getDisallowedRunningApp(null, userHandle));
                return;
            }
            return;
        }
        throw new IllegalArgumentException("packageName:" + packageNames + " is invalid.");
    }

    public void removeDisallowedRunningApp(ComponentName who, List<String> packageNames, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_APP_MANAGEMENT", "Does not hava manager app MDM permission.");
        if (HwDevicePolicyManagerServiceUtil.isValidatePackageNames(packageNames)) {
            synchronized (this) {
                if (who == null) {
                    throw new IllegalArgumentException("ComponentName is null");
                }
                if (packageNames != null) {
                    if (!packageNames.isEmpty()) {
                        HwDevicePolicyManagerServiceUtil.removeItemsFromList(getHwActiveAdmin(who, userHandle).disallowedRunningAppList, packageNames);
                        saveSettingsLocked(userHandle);
                    }
                }
                throw new IllegalArgumentException("packageNames is null or empty");
            }
            if (this.mHwAdminCache != null) {
                this.mHwAdminCache.syncHwAdminCache(5, getDisallowedRunningApp(null, userHandle));
                return;
            }
            return;
        }
        throw new IllegalArgumentException("packageName:" + packageNames + " is invalid.");
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public List<String> getDisallowedRunningApp(ComponentName who, int userHandle) {
        List<String> list = null;
        synchronized (this) {
            if (who != null) {
                HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
                if (!(admin.disallowedRunningAppList == null || admin.disallowedRunningAppList.isEmpty())) {
                    list = admin.disallowedRunningAppList;
                }
            } else {
                DevicePolicyData policy = getUserData(userHandle);
                ArrayList<String> totalList = new ArrayList();
                int N = policy.mAdminList.size();
                for (int i = 0; i < N; i++) {
                    ActiveAdmin admin2 = (ActiveAdmin) policy.mAdminList.get(i);
                    if (admin2.mHwActiveAdmin != null) {
                        HwDevicePolicyManagerServiceUtil.addListWithoutDuplicate(totalList, admin2.mHwActiveAdmin.disallowedRunningAppList);
                    }
                }
                if (!totalList.isEmpty()) {
                    Object obj = totalList;
                }
            }
        }
    }

    public void addInstallPackageWhiteList(ComponentName who, List<String> packageNames, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_APP_MANAGEMENT", "does not have app_management MDM permission!");
        if (HwDevicePolicyManagerServiceUtil.isValidatePackageNames(packageNames)) {
            synchronized (this) {
                if (who == null) {
                    throw new IllegalArgumentException("ComponentName is null");
                }
                if (packageNames != null) {
                    if (!packageNames.isEmpty()) {
                        HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
                        if (admin.installPackageWhitelist == null) {
                            admin.installPackageWhitelist = new ArrayList();
                        }
                        HwDevicePolicyManagerServiceUtil.isOverLimit(admin.installPackageWhitelist, packageNames);
                        HwDevicePolicyManagerServiceUtil.addListWithoutDuplicate(admin.installPackageWhitelist, packageNames);
                        saveSettingsLocked(userHandle);
                    }
                }
                throw new IllegalArgumentException("packageNames is null or empty");
            }
            if (this.mHwAdminCache != null) {
                this.mHwAdminCache.syncHwAdminCache(6, getInstallPackageWhiteList(null, userHandle));
                return;
            }
            return;
        }
        throw new IllegalArgumentException("packageName:" + packageNames + " is invalid.");
    }

    public void removeInstallPackageWhiteList(ComponentName who, List<String> packageNames, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_APP_MANAGEMENT", "does not have app_management MDM permission!");
        if (HwDevicePolicyManagerServiceUtil.isValidatePackageNames(packageNames)) {
            synchronized (this) {
                if (who == null) {
                    throw new IllegalArgumentException("ComponentName is null");
                }
                if (packageNames != null) {
                    if (!packageNames.isEmpty()) {
                        HwDevicePolicyManagerServiceUtil.removeItemsFromList(getHwActiveAdmin(who, userHandle).installPackageWhitelist, packageNames);
                        saveSettingsLocked(userHandle);
                    }
                }
                throw new IllegalArgumentException("packageNames is null or empty");
            }
            if (this.mHwAdminCache != null) {
                this.mHwAdminCache.syncHwAdminCache(6, getInstallPackageWhiteList(null, userHandle));
                return;
            }
            return;
        }
        throw new IllegalArgumentException("packageName:" + packageNames + " is invalid.");
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public List<String> getInstallPackageWhiteList(ComponentName who, int userHandle) {
        List<String> list = null;
        synchronized (this) {
            if (who != null) {
                HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
                if (!(admin.installPackageWhitelist == null || admin.installPackageWhitelist.isEmpty())) {
                    list = admin.installPackageWhitelist;
                }
            } else {
                DevicePolicyData policy = getUserData(userHandle);
                ArrayList<String> whitelist = new ArrayList();
                int N = policy.mAdminList.size();
                for (int i = 0; i < N; i++) {
                    ActiveAdmin admin2 = (ActiveAdmin) policy.mAdminList.get(i);
                    if (admin2.mHwActiveAdmin != null) {
                        HwDevicePolicyManagerServiceUtil.addListWithoutDuplicate(whitelist, admin2.mHwActiveAdmin.installPackageWhitelist);
                    }
                }
                if (!whitelist.isEmpty()) {
                    Object obj = whitelist;
                }
            }
        }
    }

    public void addDisallowedUninstallPackages(ComponentName who, List<String> packageNames, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_APP_MANAGEMENT", "does not have app_management MDM permission!");
        if (HwDevicePolicyManagerServiceUtil.isValidatePackageNames(packageNames)) {
            synchronized (this) {
                if (who == null) {
                    throw new IllegalArgumentException("ComponentName is null");
                }
                if (packageNames != null) {
                    if (!packageNames.isEmpty()) {
                        HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
                        if (admin.disallowedUninstallPackageList == null) {
                            admin.disallowedUninstallPackageList = new ArrayList();
                        }
                        HwDevicePolicyManagerServiceUtil.isOverLimit(admin.disallowedUninstallPackageList, packageNames);
                        filterOutSystemAppList(packageNames, userHandle);
                        HwDevicePolicyManagerServiceUtil.addListWithoutDuplicate(admin.disallowedUninstallPackageList, packageNames);
                        saveSettingsLocked(userHandle);
                    }
                }
                throw new IllegalArgumentException("packageNames is null or empty");
            }
            if (this.mHwAdminCache != null) {
                this.mHwAdminCache.syncHwAdminCache(7, getDisallowedUninstallPackageList(null, userHandle));
                return;
            }
            return;
        }
        throw new IllegalArgumentException("packageName:" + packageNames + " is invalid.");
    }

    public void removeDisallowedUninstallPackages(ComponentName who, List<String> packageNames, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_APP_MANAGEMENT", "does not have app_management MDM permission!");
        if (HwDevicePolicyManagerServiceUtil.isValidatePackageNames(packageNames)) {
            synchronized (this) {
                if (who == null) {
                    throw new IllegalArgumentException("ComponentName is null");
                }
                if (packageNames != null) {
                    if (!packageNames.isEmpty()) {
                        HwDevicePolicyManagerServiceUtil.removeItemsFromList(getHwActiveAdmin(who, userHandle).disallowedUninstallPackageList, packageNames);
                        saveSettingsLocked(userHandle);
                    }
                }
                throw new IllegalArgumentException("packageNames is null or empty");
            }
            if (this.mHwAdminCache != null) {
                this.mHwAdminCache.syncHwAdminCache(7, getDisallowedUninstallPackageList(null, userHandle));
                return;
            }
            return;
        }
        throw new IllegalArgumentException("packageName:" + packageNames + " is invalid.");
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public List<String> getDisallowedUninstallPackageList(ComponentName who, int userHandle) {
        List<String> list = null;
        synchronized (this) {
            if (who != null) {
                HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
                if (!(admin.disallowedUninstallPackageList == null || admin.disallowedUninstallPackageList.isEmpty())) {
                    list = admin.disallowedUninstallPackageList;
                }
            } else {
                DevicePolicyData policy = getUserData(userHandle);
                ArrayList<String> blacklist = new ArrayList();
                int N = policy.mAdminList.size();
                for (int i = 0; i < N; i++) {
                    ActiveAdmin admin2 = (ActiveAdmin) policy.mAdminList.get(i);
                    if (admin2.mHwActiveAdmin != null) {
                        HwDevicePolicyManagerServiceUtil.addListWithoutDuplicate(blacklist, admin2.mHwActiveAdmin.disallowedUninstallPackageList);
                    }
                }
                if (!blacklist.isEmpty()) {
                    Object obj = blacklist;
                }
            }
        }
    }

    public void addDisabledDeactivateMdmPackages(ComponentName who, List<String> packageNames, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_APP_MANAGEMENT", "does not have app_management MDM permission!");
        if (HwDevicePolicyManagerServiceUtil.isValidatePackageNames(packageNames)) {
            synchronized (this) {
                if (who == null) {
                    throw new IllegalArgumentException("ComponentName is null");
                }
                if (packageNames != null) {
                    if (!packageNames.isEmpty()) {
                        HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
                        if (admin.disabledDeactiveMdmPackagesList == null) {
                            admin.disabledDeactiveMdmPackagesList = new ArrayList();
                        }
                        HwDevicePolicyManagerServiceUtil.isOverLimit(admin.disabledDeactiveMdmPackagesList, packageNames);
                        filterOutSystemAppList(packageNames, userHandle);
                        HwDevicePolicyManagerServiceUtil.addListWithoutDuplicate(admin.disabledDeactiveMdmPackagesList, packageNames);
                        saveSettingsLocked(userHandle);
                    }
                }
                throw new IllegalArgumentException("packageNames is null or empty");
            }
            if (this.mHwAdminCache != null) {
                this.mHwAdminCache.syncHwAdminCache(18, getDisabledDeactivateMdmPackageList(null, userHandle));
                return;
            }
            return;
        }
        throw new IllegalArgumentException("packageName:" + packageNames + " is invalid.");
    }

    public void removeDisabledDeactivateMdmPackages(ComponentName who, List<String> packageNames, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_APP_MANAGEMENT", "does not have app_management MDM permission!");
        if (HwDevicePolicyManagerServiceUtil.isValidatePackageNames(packageNames)) {
            synchronized (this) {
                if (who == null) {
                    throw new IllegalArgumentException("ComponentName is null");
                }
                if (packageNames != null) {
                    if (!packageNames.isEmpty()) {
                        HwDevicePolicyManagerServiceUtil.removeItemsFromList(getHwActiveAdmin(who, userHandle).disabledDeactiveMdmPackagesList, packageNames);
                        saveSettingsLocked(userHandle);
                    }
                }
                throw new IllegalArgumentException("packageNames is null or empty");
            }
            if (this.mHwAdminCache != null) {
                this.mHwAdminCache.syncHwAdminCache(18, getDisabledDeactivateMdmPackageList(null, userHandle));
                return;
            }
            return;
        }
        throw new IllegalArgumentException("packageName:" + packageNames + " is invalid.");
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public List<String> getDisabledDeactivateMdmPackageList(ComponentName who, int userHandle) {
        List<String> list = null;
        synchronized (this) {
            if (who != null) {
                HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
                if (!(admin.disabledDeactiveMdmPackagesList == null || admin.disabledDeactiveMdmPackagesList.isEmpty())) {
                    list = admin.disabledDeactiveMdmPackagesList;
                }
            } else {
                DevicePolicyData policy = getUserData(userHandle);
                ArrayList<String> blacklist = new ArrayList();
                int N = policy.mAdminList.size();
                for (int i = 0; i < N; i++) {
                    ActiveAdmin admin2 = (ActiveAdmin) policy.mAdminList.get(i);
                    if (admin2.mHwActiveAdmin != null) {
                        HwDevicePolicyManagerServiceUtil.addListWithoutDuplicate(blacklist, admin2.mHwActiveAdmin.disabledDeactiveMdmPackagesList);
                    }
                }
                if (!blacklist.isEmpty()) {
                    Object obj = blacklist;
                }
            }
        }
    }

    public void killApplicationProcess(ComponentName who, String packageName, int userHandle) {
        if (HwDevicePolicyManagerServiceUtil.isValidatePackageName(packageName)) {
            enforceHwCrossUserPermission(userHandle);
            this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_APP_MANAGEMENT", "Does not hava application management permission.");
            synchronized (this) {
                if (who == null) {
                    throw new IllegalArgumentException("ComponentName is null");
                } else if (TextUtils.isEmpty(packageName)) {
                    throw new IllegalArgumentException("Package name is empty");
                } else if (packageName.equals(who.getPackageName())) {
                    throw new IllegalArgumentException("Can not kill the caller application");
                } else {
                    enforceCheckNotSystemApp(packageName, userHandle);
                    if (getHwActiveAdmin(who, userHandle) != null) {
                        killApplicationInner(packageName);
                    }
                }
            }
            return;
        }
        throw new IllegalArgumentException("packageName:" + packageName + " is invalid.");
    }

    private void killApplicationInner(String packageName) {
        long ident = Binder.clearCallingIdentity();
        try {
            ActivityManager am = (ActivityManager) this.mContext.getSystemService("activity");
            for (RunningTaskInfo ti : am.getRunningTasks(10000)) {
                if (packageName.equals(ti.baseActivity.getPackageName())) {
                    am.removeTask(ti.id);
                    break;
                }
            }
            Binder.restoreCallingIdentity(ident);
        } catch (Throwable th) {
            Binder.restoreCallingIdentity(ident);
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void shutdownOrRebootDevice(int code, ComponentName who, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_DEVICE_MANAGER", "does not have device_manager MDM permission!");
        synchronized (this) {
            if (who == null) {
                throw new IllegalArgumentException("ComponentName is null");
            }
            getHwActiveAdmin(who, userHandle);
            long callingId = Binder.clearCallingIdentity();
            try {
                IPowerManager power = IPowerManager.Stub.asInterface(ServiceManager.getService("power"));
                if (power != null) {
                    if (code == 1501) {
                        power.shutdown(false, null, false);
                    } else if (code == 1502) {
                        power.reboot(false, null, false);
                    }
                    Binder.restoreCallingIdentity(callingId);
                }
            } catch (RemoteException e) {
                Log.e(TAG, "exception is " + e.getMessage());
            } finally {
                Binder.restoreCallingIdentity(callingId);
            }
        }
    }

    public void configExchangeMailProvider(ComponentName who, Bundle para, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_EMAIL", "does not have EMAIL MDM permission!");
        synchronized (this) {
            if (who == null || para == null) {
                throw new IllegalArgumentException("ComponentName or para is null");
            } else if (HwDevicePolicyManagerServiceUtil.isValidExchangeParameter(para)) {
                HwActiveAdmin ap = getHwActiveAdmin(who, userHandle);
                if (ap.mailProviderlist == null) {
                    ap.mailProviderlist = new ArrayList();
                    ap.mailProviderlist.add(para);
                    saveSettingsLocked(userHandle);
                } else if (ap.mailProviderlist.size() + 1 > 20) {
                    throw new IllegalArgumentException("already exceeds max number.");
                } else {
                    boolean isAlready = false;
                    Object provider = null;
                    for (Bundle each : ap.mailProviderlist) {
                        if (HwDevicePolicyManagerServiceUtil.matchProvider(para.getString("domain"), each.getString("domain"))) {
                            isAlready = true;
                            provider = each;
                            break;
                        }
                    }
                    if (isAlready && provider != null) {
                        ap.mailProviderlist.remove(provider);
                    }
                    ap.mailProviderlist.add(para);
                    saveSettingsLocked(userHandle);
                }
            } else {
                throw new IllegalArgumentException("some paremeter is null");
            }
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public Bundle getMailProviderForDomain(ComponentName who, String domain, int userHandle) {
        if (userHandle != 0) {
            return null;
        }
        if (TextUtils.isEmpty(domain)) {
            throw new IllegalArgumentException("domain is empty.");
        }
        synchronized (this) {
            if (who != null) {
                HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
                if (admin.mailProviderlist == null) {
                    return null;
                }
                boolean matched = false;
                Bundle retProvider = null;
                for (Bundle provider : admin.mailProviderlist) {
                    matched = HwDevicePolicyManagerServiceUtil.matchProvider(domain, provider.getString("domain"));
                    if (matched) {
                        retProvider = provider;
                        break;
                    }
                }
                if (!matched) {
                    retProvider = null;
                }
            } else {
                DevicePolicyData policy = getUserData(userHandle);
                int N = policy.mAdminList.size();
                for (int i = 0; i < N; i++) {
                    ActiveAdmin admin2 = (ActiveAdmin) policy.mAdminList.get(i);
                    if (!(admin2.mHwActiveAdmin == null || admin2.mHwActiveAdmin.mailProviderlist == null)) {
                        for (Bundle provider2 : admin2.mHwActiveAdmin.mailProviderlist) {
                            if (HwDevicePolicyManagerServiceUtil.matchProvider(domain, provider2.getString("domain"))) {
                                return provider2;
                            }
                        }
                        continue;
                    }
                }
                return null;
            }
        }
    }

    public boolean isRooted(ComponentName who, int userHandle) {
        boolean z;
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_DEVICE_MANAGER", "does not have device_manager MDM permission!");
        synchronized (this) {
            if (who == null) {
                throw new IllegalArgumentException("ComponentName is null");
            }
            getHwActiveAdmin(who, userHandle);
            z = SystemProperties.get("huawei.check_root.hotapermit", "safe").equals("risk");
        }
        return z;
    }

    public void setSafeModeDisabled(ComponentName who, boolean disabled, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_DEVICE_MANAGER", "does not have DEVICE MANAGER permission!");
        synchronized (this) {
            if (who == null) {
                throw new IllegalArgumentException("ComponentName is null");
            }
            HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
            if (admin.disableSafeMode != disabled) {
                admin.disableSafeMode = disabled;
                saveSettingsLocked(userHandle);
            }
        }
        if (this.mHwAdminCache != null) {
            this.mHwAdminCache.syncHwAdminCache(10, isSafeModeDisabled(null, userHandle));
        }
    }

    public boolean isSafeModeDisabled(ComponentName who, int userHandle) {
        synchronized (this) {
            if (who != null) {
                boolean z = getHwActiveAdmin(who, userHandle).disableSafeMode;
                return z;
            }
            DevicePolicyData policy = getUserData(userHandle);
            int N = policy.mAdminList.size();
            int i = 0;
            while (i < N) {
                ActiveAdmin admin = (ActiveAdmin) policy.mAdminList.get(i);
                if (admin.mHwActiveAdmin == null || !admin.mHwActiveAdmin.disableSafeMode) {
                    i++;
                } else {
                    return true;
                }
            }
            return false;
        }
    }

    public void setAdbDisabled(ComponentName who, boolean disabled, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_USB", "does not have MDM_USB permission!");
        synchronized (this) {
            if (who == null) {
                throw new IllegalArgumentException("ComponentName is null");
            }
            HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
            if (admin.disableAdb != disabled) {
                admin.disableAdb = disabled;
                saveSettingsLocked(userHandle);
            }
        }
        long identityToken = Binder.clearCallingIdentity();
        if (disabled) {
            if (Global.getInt(this.mContext.getContentResolver(), "adb_enabled", 0) > 0) {
                Global.putInt(this.mContext.getContentResolver(), "adb_enabled", 0);
            }
        }
        Binder.restoreCallingIdentity(identityToken);
        if (this.mHwAdminCache != null) {
            this.mHwAdminCache.syncHwAdminCache(11, isAdbDisabled(null, userHandle));
        }
    }

    public boolean isAdbDisabled(ComponentName who, int userHandle) {
        synchronized (this) {
            if (who != null) {
                boolean z = getHwActiveAdmin(who, userHandle).disableAdb;
                return z;
            }
            DevicePolicyData policy = getUserData(userHandle);
            int N = policy.mAdminList.size();
            int i = 0;
            while (i < N) {
                ActiveAdmin admin = (ActiveAdmin) policy.mAdminList.get(i);
                if (admin.mHwActiveAdmin == null || !admin.mHwActiveAdmin.disableAdb) {
                    i++;
                } else {
                    return true;
                }
            }
            return false;
        }
    }

    public void setUSBOtgDisabled(ComponentName who, boolean disabled, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_USB", "does not have MDM_USB permission!");
        synchronized (this) {
            if (who == null) {
                throw new IllegalArgumentException("ComponentName is null");
            }
            HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
            if (admin.disableUSBOtg != disabled) {
                admin.disableUSBOtg = disabled;
                saveSettingsLocked(userHandle);
            }
        }
        long identityToken = Binder.clearCallingIdentity();
        StorageManager sm = (StorageManager) this.mContext.getSystemService("storage");
        for (StorageVolume storageVolume : sm.getVolumeList()) {
            if (storageVolume.isRemovable()) {
                if ("mounted".equals(sm.getVolumeState(storageVolume.getPath()))) {
                    VolumeInfo volumeInfo = sm.findVolumeByUuid(storageVolume.getUuid());
                    if (volumeInfo != null) {
                        DiskInfo diskInfo = volumeInfo.getDisk();
                        if (diskInfo != null && diskInfo.isUsb()) {
                            Slog.e(TAG, "find usb otg device mounted , umounted it");
                            sm.unmount(storageVolume.getId());
                        }
                    }
                }
            }
        }
        Binder.restoreCallingIdentity(identityToken);
        if (this.mHwAdminCache != null) {
            this.mHwAdminCache.syncHwAdminCache(12, isUSBOtgDisabled(null, userHandle));
        }
    }

    public boolean isUSBOtgDisabled(ComponentName who, int userHandle) {
        synchronized (this) {
            if (who != null) {
                boolean z = getHwActiveAdmin(who, userHandle).disableUSBOtg;
                return z;
            }
            DevicePolicyData policy = getUserData(userHandle);
            int N = policy.mAdminList.size();
            int i = 0;
            while (i < N) {
                ActiveAdmin admin = (ActiveAdmin) policy.mAdminList.get(i);
                if (admin.mHwActiveAdmin == null || !admin.mHwActiveAdmin.disableUSBOtg) {
                    i++;
                } else {
                    return true;
                }
            }
            return false;
        }
    }

    public void setGPSDisabled(ComponentName who, boolean disabled, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_LOCATION", "does not have MDM_LOCATION permission!");
        synchronized (this) {
            if (who == null) {
                throw new IllegalArgumentException("ComponentName is null");
            }
            HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
            if (admin.disableGPS != disabled) {
                admin.disableGPS = disabled;
                saveSettingsLocked(userHandle);
            }
        }
        if (isGPSTurnOn(null, userHandle) && disabled) {
            turnOnGPS(who, false, userHandle);
        }
        if (this.mHwAdminCache != null) {
            this.mHwAdminCache.syncHwAdminCache(13, isGPSDisabled(null, userHandle));
        }
    }

    public boolean isGPSDisabled(ComponentName who, int userHandle) {
        synchronized (this) {
            if (who != null) {
                boolean z = getHwActiveAdmin(who, userHandle).disableGPS;
                return z;
            }
            DevicePolicyData policy = getUserData(userHandle);
            int N = policy.mAdminList.size();
            int i = 0;
            while (i < N) {
                ActiveAdmin admin = (ActiveAdmin) policy.mAdminList.get(i);
                if (admin.mHwActiveAdmin == null || !admin.mHwActiveAdmin.disableGPS) {
                    i++;
                } else {
                    return true;
                }
            }
            return false;
        }
    }

    public void turnOnGPS(ComponentName who, boolean on, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_LOCATION", "does not have MDM_LOCATION permission!");
        if (who == null) {
            throw new IllegalArgumentException("ComponentName is null");
        } else if (isGPSTurnOn(null, userHandle) != on) {
            long identityToken = Binder.clearCallingIdentity();
            if (!Secure.setLocationProviderEnabledForUser(this.mContext.getContentResolver(), "gps", on, ActivityManager.getCurrentUser())) {
                Log.e(TAG, "setLocationProviderEnabledForUser failed");
            }
            Binder.restoreCallingIdentity(identityToken);
        }
    }

    public boolean isGPSTurnOn(ComponentName who, int userHandle) {
        long identityToken = Binder.clearCallingIdentity();
        boolean isGPSEnabled = Secure.isLocationProviderEnabledForUser(this.mContext.getContentResolver(), "gps", ActivityManager.getCurrentUser());
        Binder.restoreCallingIdentity(identityToken);
        return isGPSEnabled;
    }

    public void setTaskButtonDisabled(ComponentName who, boolean disabled, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_DEVICE_MANAGER", "does not have DEVICE MANAGER permission!");
        synchronized (this) {
            if (who == null) {
                throw new IllegalArgumentException("ComponentName is null");
            }
            HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
            if (admin.disableTaskKey != disabled) {
                admin.disableTaskKey = disabled;
                saveSettingsLocked(userHandle);
            }
        }
        if (this.mHwAdminCache != null) {
            this.mHwAdminCache.syncHwAdminCache(15, isTaskButtonDisabled(null, userHandle));
        }
    }

    public boolean isTaskButtonDisabled(ComponentName who, int userHandle) {
        synchronized (this) {
            if (who != null) {
                boolean z = getHwActiveAdmin(who, userHandle).disableTaskKey;
                return z;
            }
            DevicePolicyData policy = getUserData(userHandle);
            int N = policy.mAdminList.size();
            int i = 0;
            while (i < N) {
                ActiveAdmin admin = (ActiveAdmin) policy.mAdminList.get(i);
                if (admin.mHwActiveAdmin == null || !admin.mHwActiveAdmin.disableTaskKey) {
                    i++;
                } else {
                    return true;
                }
            }
            return false;
        }
    }

    public void setHomeButtonDisabled(ComponentName who, boolean disabled, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_DEVICE_MANAGER", "does not have DEVICE MANAGER permission!");
        synchronized (this) {
            if (who == null) {
                throw new IllegalArgumentException("ComponentName is null");
            }
            HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
            if (admin.disableHomeKey != disabled) {
                admin.disableHomeKey = disabled;
                saveSettingsLocked(userHandle);
            }
        }
        if (this.mHwAdminCache != null) {
            this.mHwAdminCache.syncHwAdminCache(14, isHomeButtonDisabled(null, userHandle));
        }
    }

    public boolean isHomeButtonDisabled(ComponentName who, int userHandle) {
        synchronized (this) {
            if (who != null) {
                boolean z = getHwActiveAdmin(who, userHandle).disableHomeKey;
                return z;
            }
            DevicePolicyData policy = getUserData(userHandle);
            int N = policy.mAdminList.size();
            int i = 0;
            while (i < N) {
                ActiveAdmin admin = (ActiveAdmin) policy.mAdminList.get(i);
                if (admin.mHwActiveAdmin == null || !admin.mHwActiveAdmin.disableHomeKey) {
                    i++;
                } else {
                    return true;
                }
            }
            return false;
        }
    }

    public void setBackButtonDisabled(ComponentName who, boolean disabled, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_DEVICE_MANAGER", "does not have DEVICE MANAGER permission!");
        synchronized (this) {
            if (who == null) {
                throw new IllegalArgumentException("ComponentName is null");
            }
            HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
            if (admin.disableBackKey != disabled) {
                admin.disableBackKey = disabled;
                saveSettingsLocked(userHandle);
            }
        }
        if (this.mHwAdminCache != null) {
            this.mHwAdminCache.syncHwAdminCache(16, isBackButtonDisabled(null, userHandle));
        }
    }

    public boolean isBackButtonDisabled(ComponentName who, int userHandle) {
        synchronized (this) {
            if (who != null) {
                boolean z = getHwActiveAdmin(who, userHandle).disableBackKey;
                return z;
            }
            DevicePolicyData policy = getUserData(userHandle);
            int N = policy.mAdminList.size();
            int i = 0;
            while (i < N) {
                ActiveAdmin admin = (ActiveAdmin) policy.mAdminList.get(i);
                if (admin.mHwActiveAdmin == null || !admin.mHwActiveAdmin.disableBackKey) {
                    i++;
                } else {
                    return true;
                }
            }
            return false;
        }
    }

    public void setSysTime(ComponentName who, long millis, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_DEVICE_MANAGER", "does not have device manager MDM permission!");
        synchronized (this) {
            if (who == null) {
                throw new IllegalArgumentException("ComponentName is null");
            }
            getHwActiveAdmin(who, userHandle);
            long id = Binder.clearCallingIdentity();
            SystemClock.setCurrentTimeMillis(millis);
            Binder.restoreCallingIdentity(id);
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setCustomSettingsMenu(ComponentName who, List<String> menusToDelete, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_DEVICE_MANAGER", "does not have device_manager MDM permission!");
        synchronized (this) {
            if (who == null) {
                throw new IllegalArgumentException("ComponentName is null");
            }
            getHwActiveAdmin(who, userHandle);
            if (menusToDelete == null || menusToDelete.isEmpty()) {
                Global.putStringForUser(this.mContext.getContentResolver(), SETTINGS_MENUS_REMOVE, AppHibernateCst.INVALID_PKG, userHandle);
                return;
            }
            long callingId = Binder.clearCallingIdentity();
            try {
                String oldMenus = Global.getStringForUser(this.mContext.getContentResolver(), SETTINGS_MENUS_REMOVE, userHandle);
                String splitter = ",";
                StringBuffer newMenus = new StringBuffer();
                if (!TextUtils.isEmpty(oldMenus)) {
                    newMenus.append(oldMenus);
                }
                for (String menu : menusToDelete) {
                    if (oldMenus == null || !oldMenus.contains(menu)) {
                        newMenus.append(menu).append(splitter);
                    }
                }
                Global.putStringForUser(this.mContext.getContentResolver(), SETTINGS_MENUS_REMOVE, newMenus.toString(), userHandle);
            } finally {
                Binder.restoreCallingIdentity(callingId);
            }
        }
    }

    public void setDefaultLauncher(ComponentName who, String packageName, String className, int userHandle) {
        if (TextUtils.isEmpty(packageName)) {
            throw new IllegalArgumentException("packageName is null or empty");
        } else if (TextUtils.isEmpty(className)) {
            throw new IllegalArgumentException("className is null or empty");
        } else {
            enforceHwCrossUserPermission(userHandle);
            this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.SDK_LAUNCHER", "Does not have sdk_launcher permission.");
            synchronized (this) {
                if (who == null) {
                    throw new IllegalArgumentException("ComponentName is null");
                }
                HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
                long callingId = Binder.clearCallingIdentity();
                LauncherUtils.setDefaultLauncher(this.mContext, packageName, className);
                Binder.restoreCallingIdentity(callingId);
                admin.disableChangeLauncher = true;
                saveSettingsLocked(userHandle);
                if (this.mHwAdminCache != null) {
                    this.mHwAdminCache.syncHwAdminCache(17, isChangeLauncherDisabled(null, userHandle));
                }
            }
        }
    }

    public void clearDefaultLauncher(ComponentName who, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.SDK_LAUNCHER", "Does not have sdk_launcher permission.");
        synchronized (this) {
            if (who == null) {
                throw new IllegalArgumentException("ComponentName is null");
            }
            getHwActiveAdmin(who, userHandle).disableChangeLauncher = false;
            saveSettingsLocked(userHandle);
            if (this.mHwAdminCache != null) {
                this.mHwAdminCache.syncHwAdminCache(17, isChangeLauncherDisabled(null, userHandle));
            }
            long callingId = Binder.clearCallingIdentity();
            LauncherUtils.clearDefaultLauncher(this.mContext);
            Binder.restoreCallingIdentity(callingId);
        }
    }

    public boolean isChangeLauncherDisabled(ComponentName who, int userHandle) {
        synchronized (this) {
            if (who != null) {
                boolean z = getHwActiveAdmin(who, userHandle).disableChangeLauncher;
                return z;
            }
            DevicePolicyData policy = getUserData(userHandle);
            int N = policy.mAdminList.size();
            int i = 0;
            while (i < N) {
                ActiveAdmin admin = (ActiveAdmin) policy.mAdminList.get(i);
                if (admin.mHwActiveAdmin == null || !admin.mHwActiveAdmin.disableChangeLauncher) {
                    i++;
                } else {
                    return true;
                }
            }
            return false;
        }
    }

    public Bitmap captureScreen(ComponentName who, int userHandle) {
        Bitmap bmp;
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_CAPTURE_SCREEN", "Does not have MDM_CAPTURE_SCREEN permission.");
        synchronized (this) {
            if (who == null) {
                throw new IllegalArgumentException("ComponentName is null");
            }
            getHwActiveAdmin(who, userHandle);
            long callingId = Binder.clearCallingIdentity();
            bmp = CaptureScreenUtils.captureScreen(this.mContext);
            Binder.restoreCallingIdentity(callingId);
        }
        return bmp;
    }

    public void addApn(ComponentName who, Map<String, String> apnInfo, int userHandle) {
        if (apnInfo == null || apnInfo.isEmpty()) {
            throw new IllegalArgumentException("apnInfo is empty.");
        }
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_APN", "Does not have apn permission.");
        synchronized (this) {
            if (who == null) {
                throw new IllegalArgumentException("ComponentName is null");
            }
            getHwActiveAdmin(who, userHandle);
            long callingId = Binder.clearCallingIdentity();
            ApnUtils.addApn(this.mContext.getContentResolver(), apnInfo);
            Binder.restoreCallingIdentity(callingId);
        }
    }

    public void deleteApn(ComponentName who, String apnId, int userHandle) {
        if (TextUtils.isEmpty(apnId)) {
            throw new IllegalArgumentException("apnId is empty.");
        }
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_APN", "Does not have apn permission.");
        synchronized (this) {
            if (who == null) {
                throw new IllegalArgumentException("ComponentName is null");
            }
            getHwActiveAdmin(who, userHandle);
            long callingId = Binder.clearCallingIdentity();
            ApnUtils.deleteApn(this.mContext.getContentResolver(), apnId);
            Binder.restoreCallingIdentity(callingId);
        }
    }

    public void updateApn(ComponentName who, Map<String, String> apnInfo, String apnId, int userHandle) {
        if (apnInfo == null || apnInfo.isEmpty()) {
            throw new IllegalArgumentException("apnInfo is empty.");
        } else if (TextUtils.isEmpty(apnId)) {
            throw new IllegalArgumentException("apnId is empty.");
        } else {
            enforceHwCrossUserPermission(userHandle);
            this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_APN", "Does not have apn permission.");
            synchronized (this) {
                if (who == null) {
                    throw new IllegalArgumentException("ComponentName is null");
                }
                getHwActiveAdmin(who, userHandle);
                long callingId = Binder.clearCallingIdentity();
                ApnUtils.updateApn(this.mContext.getContentResolver(), apnInfo, apnId);
                Binder.restoreCallingIdentity(callingId);
            }
        }
    }

    public void setPreferApn(ComponentName who, String apnId, int userHandle) {
        if (TextUtils.isEmpty(apnId)) {
            throw new IllegalArgumentException("apnId is empty.");
        }
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_APN", "Does not have apn permission.");
        synchronized (this) {
            if (who == null) {
                throw new IllegalArgumentException("ComponentName is null");
            }
            getHwActiveAdmin(who, userHandle);
            long callingId = Binder.clearCallingIdentity();
            ApnUtils.setPreferApn(this.mContext.getContentResolver(), apnId);
            Binder.restoreCallingIdentity(callingId);
        }
    }

    public List<String> queryApn(ComponentName who, Map<String, String> apnInfo, int userHandle) {
        if (apnInfo == null || apnInfo.isEmpty()) {
            throw new IllegalArgumentException("apnInfo is empty.");
        }
        List<String> ids;
        enforceHwCrossUserPermission(userHandle);
        synchronized (this) {
            if (who == null) {
                throw new IllegalArgumentException("ComponentName is null");
            }
            getHwActiveAdmin(who, userHandle);
            long callingId = Binder.clearCallingIdentity();
            ids = ApnUtils.queryApn(this.mContext.getContentResolver(), apnInfo);
            Binder.restoreCallingIdentity(callingId);
        }
        return ids;
    }

    public Map<String, String> getApnInfo(ComponentName who, String apnId, int userHandle) {
        if (TextUtils.isEmpty(apnId)) {
            throw new IllegalArgumentException("apnId is empty.");
        }
        Map<String, String> apnInfo;
        enforceHwCrossUserPermission(userHandle);
        synchronized (this) {
            if (who == null) {
                throw new IllegalArgumentException("ComponentName is null");
            }
            getHwActiveAdmin(who, userHandle);
            long callingId = Binder.clearCallingIdentity();
            apnInfo = ApnUtils.getApnInfo(this.mContext.getContentResolver(), apnId);
            Binder.restoreCallingIdentity(callingId);
        }
        return apnInfo;
    }

    public void addNetworkAccessWhitelist(ComponentName who, List<String> addrList, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_NETWORK_MANAGER", "does not have network_manager MDM permission!");
        if (HwDevicePolicyManagerServiceUtil.isValidIPAddrs(addrList)) {
            synchronized (this) {
                if (who == null) {
                    throw new IllegalArgumentException("ComponentName is null");
                }
                HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
                if (admin.networkAccessWhitelist == null) {
                    admin.networkAccessWhitelist = new ArrayList();
                }
                HwDevicePolicyManagerServiceUtil.isAddrOverLimit(admin.networkAccessWhitelist, addrList);
                HwDevicePolicyManagerServiceUtil.addListWithoutDuplicate(admin.networkAccessWhitelist, addrList);
                saveSettingsLocked(userHandle);
                setNetworkAccessWhitelist(admin.networkAccessWhitelist);
            }
            if (this.mHwAdminCache != null) {
                this.mHwAdminCache.syncHwAdminCache(9, getNetworkAccessWhitelist(null, userHandle));
                return;
            }
            return;
        }
        throw new IllegalArgumentException("addrlist invalid");
    }

    public void removeNetworkAccessWhitelist(ComponentName who, List<String> addrList, int userHandle) {
        enforceHwCrossUserPermission(userHandle);
        this.mContext.enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_NETWORK_MANAGER", "does not have network_manager MDM permission!");
        if (HwDevicePolicyManagerServiceUtil.isValidIPAddrs(addrList)) {
            synchronized (this) {
                if (who == null) {
                    throw new IllegalArgumentException("ComponentName is null");
                }
                HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
                HwDevicePolicyManagerServiceUtil.removeItemsFromList(admin.networkAccessWhitelist, addrList);
                saveSettingsLocked(userHandle);
                setNetworkAccessWhitelist(admin.networkAccessWhitelist);
            }
            if (this.mHwAdminCache != null) {
                this.mHwAdminCache.syncHwAdminCache(9, getNetworkAccessWhitelist(null, userHandle));
                return;
            }
            return;
        }
        throw new IllegalArgumentException("addrlist invalid");
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public List<String> getNetworkAccessWhitelist(ComponentName who, int userHandle) {
        List<String> list = null;
        synchronized (this) {
            if (who != null) {
                HwActiveAdmin admin = getHwActiveAdmin(who, userHandle);
                if (!(admin.networkAccessWhitelist == null || admin.networkAccessWhitelist.isEmpty())) {
                    list = admin.networkAccessWhitelist;
                }
            } else {
                DevicePolicyData policy = getUserData(userHandle);
                ArrayList<String> addrList = new ArrayList();
                int N = policy.mAdminList.size();
                for (int i = 0; i < N; i++) {
                    ActiveAdmin admin2 = (ActiveAdmin) policy.mAdminList.get(i);
                    if (admin2.mHwActiveAdmin != null) {
                        HwDevicePolicyManagerServiceUtil.addListWithoutDuplicate(addrList, admin2.mHwActiveAdmin.networkAccessWhitelist);
                    }
                }
                if (!addrList.isEmpty()) {
                    Object obj = addrList;
                }
            }
        }
    }

    private void setNetworkAccessWhitelist(List<String> whitelist) {
        String DESCRIPTOR_NETWORKMANAGEMENT_SERVICE = "android.os.INetworkManagementService";
        IBinder b = ServiceManager.getService("network_management");
        Parcel _data = Parcel.obtain();
        Parcel _reply = Parcel.obtain();
        if (b != null) {
            try {
                _data.writeInterfaceToken("android.os.INetworkManagementService");
                _data.writeStringList(whitelist);
                b.transact(1106, _data, _reply, 0);
                _reply.readException();
            } catch (RemoteException localRemoteException) {
                Log.e(TAG, "setNetworkAccessWhitelist error", localRemoteException);
                return;
            } finally {
                _reply.recycle();
                _data.recycle();
            }
        }
        _reply.recycle();
        _data.recycle();
    }

    public boolean getHwAdminCachedValue(int code) {
        int type = -1;
        switch (code) {
            case 4001:
                type = 0;
                break;
            case 4002:
                type = 1;
                break;
            case 4003:
                type = 2;
                break;
            case 4009:
                type = 8;
                break;
            case 4011:
                type = 10;
                break;
            case 4012:
                type = 11;
                break;
            case 4013:
                type = 12;
                break;
            case 4014:
                type = 13;
                break;
            case 4015:
                type = 14;
                break;
            case 4016:
                type = 15;
                break;
            case 4017:
                type = 16;
                break;
            case 4018:
                type = 17;
                break;
        }
        if (this.mHwAdminCache == null || type == -1) {
            return false;
        }
        return this.mHwAdminCache.getCachedValue(type);
    }

    public List<String> getHwAdminCachedList(int code) {
        List<String> result = null;
        int type = -1;
        switch (code) {
            case 4004:
                type = 3;
                break;
            case 4005:
                type = 4;
                break;
            case 4006:
                type = 5;
                break;
            case 4007:
                type = 6;
                break;
            case 4008:
                type = 7;
                break;
            case 4010:
                type = 9;
                break;
            case 4019:
                type = 18;
                break;
        }
        if (!(this.mHwAdminCache == null || type == -1)) {
            result = this.mHwAdminCache.getCachedList(type);
        }
        return result == null ? new ArrayList() : result;
    }

    private void enforceUserRestrictionPermission(ComponentName who, String key, int userHandle) {
        long id = Binder.clearCallingIdentity();
        try {
            UserInfo info = this.mUserManager.getUserInfo(userHandle);
            if (info == null) {
                throw new IllegalArgumentException("Invalid user: " + userHandle);
            } else if (info.isGuest()) {
                throw new IllegalStateException("Cannot call this method on a guest");
            } else if (who == null) {
                throw new IllegalArgumentException("Component is null");
            } else if (userHandle != 0 && HWDEVICE_OWNER_USER_RESTRICTIONS.contains(key)) {
                throw new SecurityException("Cannot set user restriction " + key);
            }
        } finally {
            Binder.restoreCallingIdentity(id);
        }
    }

    private HwActiveAdmin getHwActiveAdmin(ComponentName who, int userHandle) {
        ActiveAdmin admin = getActiveAdminUncheckedLocked(who, userHandle);
        if (admin == null) {
            throw new SecurityException("No active admin owned by uid " + Binder.getCallingUid() + ", ComponentName:" + who);
        } else if (admin.getUid() != Binder.getCallingUid()) {
            throw new SecurityException("Admin " + who + " is not owned by uid " + Binder.getCallingUid());
        } else {
            HwActiveAdmin hwadmin = admin.mHwActiveAdmin;
            if (hwadmin != null) {
                return hwadmin;
            }
            hwadmin = new HwActiveAdmin();
            admin.mHwActiveAdmin = hwadmin;
            return hwadmin;
        }
    }

    private void setHwUserRestriction(String key, boolean disable, int userHandle) {
        UserHandle user = new UserHandle(userHandle);
        boolean alreadyRestricted = this.mUserManager.hasUserRestriction(key, user);
        if (HWFLOW) {
            Log.i(TAG, "setUserRestriction for (" + key + ", " + userHandle + "), is alreadyRestricted: " + alreadyRestricted);
        }
        long id = Binder.clearCallingIdentity();
        if (disable && !alreadyRestricted) {
            if ("no_config_tethering".equals(key)) {
                WifiManager wifiManager = (WifiManager) this.mContext.getSystemService(GnssConnectivityLogManager.SUBSYS_WIFI);
                if (wifiManager.isWifiApEnabled()) {
                    wifiManager.setWifiApEnabled(null, false);
                    ((NotificationManager) this.mContext.getSystemService("notification")).cancelAsUser(null, 17303284, UserHandle.ALL);
                }
            } else if ("no_physical_media".equals(key)) {
                boolean hasExternalSdcard = StorageUtils.hasExternalSdcard(this.mContext);
                boolean dafaultIsSdcard = DefaultStorageLocation.isSdcard();
                if (hasExternalSdcard && !dafaultIsSdcard) {
                    Log.w(TAG, "call doUnMount");
                    StorageUtils.doUnMount(this.mContext);
                } else if (hasExternalSdcard && dafaultIsSdcard && StorageUtils.isSwitchPrimaryVolumeSupported()) {
                    throw new IllegalStateException("could not disable sdcard when it is primary card.");
                }
            } else if ("no_usb_file_transfer".equals(key)) {
                if (disable) {
                    Global.putStringForUser(this.mContext.getContentResolver(), "adb_enabled", PPPOEStateMachine.PHASE_DEAD, userHandle);
                }
                this.mUserManager.setUserRestriction("no_debugging_features", true, user);
            }
        }
        try {
            this.mUserManager.setUserRestriction(key, disable, user);
            if ("no_usb_file_transfer".equals(key) && !disable) {
                this.mUserManager.setUserRestriction("no_debugging_features", false, user);
            }
            Binder.restoreCallingIdentity(id);
            sendHwChangedNotification(userHandle);
        } catch (Throwable th) {
            Binder.restoreCallingIdentity(id);
        }
    }

    private void sendHwChangedNotification(int userHandle) {
        Intent intent = new Intent("android.app.action.DEVICE_POLICY_MANAGER_STATE_CHANGED");
        intent.setFlags(1073741824);
        long ident = Binder.clearCallingIdentity();
        try {
            this.mContext.sendBroadcastAsUser(intent, new UserHandle(userHandle));
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private boolean haveHwUserRestriction(String key, int userid) {
        UserHandle user = new UserHandle(userid);
        int userHandle = user.getIdentifier();
        return this.mUserManager.hasUserRestriction(key, user);
    }

    protected void syncHwDeviceSettingsLocked(int userHandle) {
        if (userHandle != 0) {
            Log.w(TAG, "userHandle is not USER_OWNER, return ");
            return;
        }
        try {
            synchronized (this) {
                for (String s : HWDEVICE_OWNER_USER_RESTRICTIONS) {
                    hwSyncDeviceCapabilitiesLocked(s, userHandle);
                }
            }
            hwSyncDeviceStatusBarLocked(userHandle);
        } catch (Exception e) {
            Log.e(TAG, "syncHwDeviceSettingsLocked exception is " + e.getMessage());
        }
        try {
            syncHwAdminCache(userHandle);
        } catch (Exception e2) {
            Log.e(TAG, "syncHwAdminCache exception is " + e2.getMessage());
        }
    }

    private void syncHwAdminCache(int userHandle) {
        if (this.mHwAdminCache == null) {
            this.mHwAdminCache = new HwAdminCache();
        }
        this.mHwAdminCache.syncHwAdminCache(0, isWifiDisabled(null, userHandle));
        this.mHwAdminCache.syncHwAdminCache(8, isBluetoothDisabled(null, userHandle));
        this.mHwAdminCache.syncHwAdminCache(1, isVoiceDisabled(null, userHandle));
        this.mHwAdminCache.syncHwAdminCache(2, isInstallSourceDisabled(null, userHandle));
        this.mHwAdminCache.syncHwAdminCache(3, getInstallPackageSourceWhiteList(null, userHandle));
        this.mHwAdminCache.syncHwAdminCache(4, getPersistentApp(null, userHandle));
        this.mHwAdminCache.syncHwAdminCache(5, getDisallowedRunningApp(null, userHandle));
        this.mHwAdminCache.syncHwAdminCache(6, getInstallPackageWhiteList(null, userHandle));
        this.mHwAdminCache.syncHwAdminCache(7, getDisallowedUninstallPackageList(null, userHandle));
        this.mHwAdminCache.syncHwAdminCache(18, getDisabledDeactivateMdmPackageList(null, userHandle));
        this.mHwAdminCache.syncHwAdminCache(9, getNetworkAccessWhitelist(null, userHandle));
        this.mHwAdminCache.syncHwAdminCache(10, isSafeModeDisabled(null, userHandle));
        this.mHwAdminCache.syncHwAdminCache(11, isAdbDisabled(null, userHandle));
        this.mHwAdminCache.syncHwAdminCache(12, isUSBOtgDisabled(null, userHandle));
        this.mHwAdminCache.syncHwAdminCache(13, isGPSDisabled(null, userHandle));
        this.mHwAdminCache.syncHwAdminCache(14, isHomeButtonDisabled(null, userHandle));
        this.mHwAdminCache.syncHwAdminCache(15, isTaskButtonDisabled(null, userHandle));
        this.mHwAdminCache.syncHwAdminCache(16, isBackButtonDisabled(null, userHandle));
        this.mHwAdminCache.syncHwAdminCache(17, isChangeLauncherDisabled(null, userHandle));
    }

    private void hwSyncDeviceCapabilitiesLocked(String restriction, int userHandle) {
        boolean disabled = false;
        boolean alreadyRestricted = haveHwUserRestriction(restriction, userHandle);
        DevicePolicyData policy = getUserData(userHandle);
        int N = policy.mAdminList.size();
        for (int i = 0; i < N; i++) {
            if (isUserRestrictionDisabled(restriction, ((ActiveAdmin) policy.mAdminList.get(i)).mHwActiveAdmin)) {
                disabled = true;
                break;
            }
        }
        if (disabled != alreadyRestricted) {
            if (HWFLOW) {
                Log.i(TAG, "Set " + restriction + " to " + disabled);
            }
            setHwUserRestriction(restriction, disabled, userHandle);
        }
    }

    private void hwSyncDeviceStatusBarLocked(int userHandle) {
        if (isStatusBarExpandPanelDisabled(null, userHandle)) {
            setStatusBarPanelDisabledInternal(userHandle);
        } else {
            setStatusBarPanelEnableInternal(true, userHandle);
        }
    }

    private boolean isUserRestrictionDisabled(String restriction, HwActiveAdmin admin) {
        if (admin == null) {
            return false;
        }
        if ("no_usb_file_transfer".equals(restriction) && admin.disableUSBData) {
            return true;
        }
        if ("no_outgoing_calls".equals(restriction) && admin.disableVoice) {
            return true;
        }
        if ("no_sms".equals(restriction) && admin.disableSMS) {
            return true;
        }
        if ("no_config_tethering".equals(restriction) && admin.disableWifiAp) {
            return true;
        }
        return "no_physical_media".equals(restriction) && admin.disableExternalStorage;
    }

    private void installPackage(String packagePath, String installerPackageName) {
        if (TextUtils.isEmpty(packagePath)) {
            throw new IllegalArgumentException("Install package path is empty");
        }
        long ident = Binder.clearCallingIdentity();
        try {
            final File tempFile = new File(packagePath.trim()).getCanonicalFile();
            if (tempFile.getName().endsWith(".apk")) {
                this.mContext.getPackageManager().installPackage(Uri.fromFile(tempFile), new PackageInstallObserver() {
                    public void onPackageInstalled(String basePackageName, int returnCode, String msg, Bundle extras) {
                        if (1 != returnCode) {
                            Log.e(HwDevicePolicyManagerService.TAG, "The package " + tempFile.getName() + "installed failed, error code: " + returnCode);
                        }
                    }
                }, 2, null);
                Binder.restoreCallingIdentity(ident);
            }
        } catch (IOException e) {
            Log.e(TAG, "Get canonical file failed for package path: " + packagePath + ", error: " + e.getMessage());
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private void uninstallPackage(String packageName, boolean keepData) {
        int i = 0;
        if (TextUtils.isEmpty(packageName)) {
            throw new IllegalArgumentException("Uninstall package name is empty");
        } else if (HwDevicePolicyManagerServiceUtil.isValidatePackageName(packageName)) {
            long ident = Binder.clearCallingIdentity();
            try {
                PackageManager pm = this.mContext.getPackageManager();
                if (pm.getApplicationInfo(packageName, 0) != null) {
                    if (keepData) {
                        i = 1;
                    }
                    pm.deletePackage(packageName, null, i);
                    Binder.restoreCallingIdentity(ident);
                }
            } catch (NameNotFoundException e) {
                Log.e(TAG, "Name not found for package: " + packageName);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        } else {
            throw new IllegalArgumentException("packageName:" + packageName + " is invalid.");
        }
    }

    private void filterOutSystemAppList(List<String> packageNames, int userHandle) {
        List<String> systemAppList = new ArrayList();
        try {
            for (String name : packageNames) {
                if (isSystemAppExcludePreInstalled(name)) {
                    systemAppList.add(name);
                }
            }
            if (!systemAppList.isEmpty()) {
                packageNames.removeAll(systemAppList);
            }
        } catch (Exception e) {
            Log.e(TAG, "filterOutSystemAppList exception is " + e);
        }
    }

    private void enforceCheckNotSystemApp(String packageName, int userHandle) {
        if (isSystemAppExcludePreInstalled(packageName)) {
            throw new IllegalArgumentException("could not operate system app");
        }
    }

    private boolean isSystemAppExcludePreInstalled(String packageName) {
        long id = Binder.clearCallingIdentity();
        try {
            IPackageManager pm = AppGlobals.getPackageManager();
            if (pm == null) {
                return false;
            }
            int userId = UserHandle.getCallingUserId();
            UserManager um = UserManager.get(this.mContext);
            if (um == null) {
                Log.e(TAG, "failed to get um");
                restoreCallingIdentity(id);
                return false;
            }
            UserInfo primaryUser = um.getProfileParent(userId);
            if (primaryUser == null) {
                primaryUser = um.getUserInfo(userId);
            }
            boolean isSystemAppExcludePreInstalled = isSystemAppExcludePreInstalled(pm, packageName, primaryUser.id);
            restoreCallingIdentity(id);
            return isSystemAppExcludePreInstalled;
        } catch (RemoteException e) {
            Log.e(TAG, "failed to check system app, RemoteException is  " + e);
            return false;
        } catch (Exception e2) {
            Log.e(TAG, "failed to check system app " + e2);
            return false;
        } finally {
            restoreCallingIdentity(id);
        }
    }

    private boolean isSystemAppExcludePreInstalled(IPackageManager pm, String packageName, int userId) throws RemoteException {
        if (packageName == null || packageName.equals(AppHibernateCst.INVALID_PKG)) {
            return false;
        }
        try {
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 8192, userId);
            if (appInfo == null) {
                return false;
            }
            int flags = appInfo.flags;
            boolean flag = true;
            if ((flags & 1) == 0) {
                Log.d(TAG, "packageName is not systemFlag");
                flag = false;
            } else if (!((flags & 1) == 0 || (flags & HwGlobalActionsData.FLAG_SHUTDOWN_CONFIRM) == 0)) {
                Log.w(TAG, "SystemApp preInstalledFlag");
                flag = false;
            }
            int hwFlags = appInfo.hwFlags;
            if (!((flags & 1) == 0 || (hwFlags & HwGlobalActionsData.FLAG_SHUTDOWN_CONFIRM) == 0)) {
                flag = false;
                Log.d(TAG, "packageName is not systemFlag");
            }
            if ((67108864 & hwFlags) != 0) {
                flag = false;
            }
            return flag;
        } catch (Exception e) {
            Log.e(TAG, "could not get appInfo, exception is " + e);
            return false;
        }
    }

    public int getSDCardEncryptionStatus() {
        if (!isSupportCrypt) {
            return -1;
        }
        String sdStatus = SystemProperties.get("vold.cryptsd.state");
        if (sdStatus.equals("invalid")) {
            return 0;
        }
        if (sdStatus.equals("encrypting")) {
            return 3;
        }
        if (sdStatus.equals("decrypting")) {
            return 4;
        }
        if (sdStatus.equals("disable")) {
            return 1;
        }
        if (sdStatus.equals("enable")) {
            return 2;
        }
        if (sdStatus.equals("mismatch")) {
            return 5;
        }
        if (sdStatus.equals("wait_unlock")) {
            return 6;
        }
        return 0;
    }

    public void setSDCardDecryptionDisabled(ComponentName who, boolean disabled, int userHandle) {
        synchronized (this) {
            if (who == null) {
                throw new IllegalArgumentException("ComponentName is null");
            }
            HwActiveAdmin admin = getHwActiveAdminForCallerLocked(who);
            if (admin.disableDecryptSDCard != disabled) {
                admin.disableDecryptSDCard = disabled;
                saveSettingsLocked(userHandle);
            }
        }
        if (this.mHwAdminCache != null) {
            this.mHwAdminCache.syncHwAdminCache(19, isSDCardDecryptionDisabled(null, userHandle));
        }
    }

    public boolean isSDCardDecryptionDisabled(ComponentName who, int userHandle) {
        synchronized (this) {
            if (who != null) {
                boolean z = getHwActiveAdminUncheckedLocked(who, userHandle).disableDecryptSDCard;
                return z;
            }
            DevicePolicyData policy = getUserData(userHandle);
            int N = policy.mAdminList.size();
            int i = 0;
            while (i < N) {
                ActiveAdmin admin = (ActiveAdmin) policy.mAdminList.get(i);
                if (admin.mHwActiveAdmin == null || !admin.mHwActiveAdmin.disableDecryptSDCard) {
                    i++;
                } else {
                    return true;
                }
            }
            return false;
        }
    }

    private HwActiveAdmin getHwActiveAdminUncheckedLocked(ComponentName who, int userHandle) {
        ActiveAdmin admin = getActiveAdminUncheckedLocked(who, userHandle);
        if (admin != null) {
            HwActiveAdmin hwadmin = admin.mHwActiveAdmin;
            if (hwadmin != null) {
                return hwadmin;
            }
            hwadmin = new HwActiveAdmin();
            admin.mHwActiveAdmin = hwadmin;
            return hwadmin;
        }
        throw new SecurityException("No active admin owned by uid " + Binder.getCallingUid() + ", ComponentName:" + who);
    }

    private HwActiveAdmin getHwActiveAdminForCallerLocked(ComponentName who) {
        ActiveAdmin admin = getActiveAdminForCallerLocked(who, 7);
        if (admin != null) {
            HwActiveAdmin hwadmin = admin.mHwActiveAdmin;
            if (hwadmin != null) {
                return hwadmin;
            }
            hwadmin = new HwActiveAdmin();
            admin.mHwActiveAdmin = hwadmin;
            return hwadmin;
        }
        throw new SecurityException("No active admin owned by uid " + Binder.getCallingUid() + ", ComponentName:" + who);
    }

    protected void clearWipeDataFactoryLowlevel(String reason) {
        if (((UserManager) this.mContext.getSystemService("user")).hasUserRestriction("no_factory_reset")) {
            Log.e(TAG, "Wiping data is not allowed for this user.");
            return;
        }
        Log.d(TAG, "wipeData, reason=" + reason);
        Intent intent = new Intent("android.intent.action.MASTER_CLEAR");
        intent.addFlags(285212672);
        intent.putExtra("masterClearWipeDataFactoryLowlevel", true);
        this.mContext.sendBroadcastAsUser(intent, UserHandle.SYSTEM);
    }
}
