package com.android.server.backup;

import android.app.ActivityManagerNative;
import android.app.AlarmManager;
import android.app.AppGlobals;
import android.app.IActivityManager;
import android.app.IBackupAgent;
import android.app.PackageInstallObserver;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.BackupProgress;
import android.app.backup.FullBackup;
import android.app.backup.FullBackupDataOutput;
import android.app.backup.IBackupManager;
import android.app.backup.IBackupObserver;
import android.app.backup.IFullBackupRestoreObserver;
import android.app.backup.IRestoreObserver;
import android.app.backup.IRestoreSession;
import android.app.backup.IRestoreSession.Stub;
import android.app.backup.RestoreDescription;
import android.app.backup.RestoreSet;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.IPackageDeleteObserver;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.Signature;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Binder;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Environment;
import android.os.Environment.UserEnvironment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.os.SELinux;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.WorkSource;
import android.os.storage.IMountService;
import android.provider.Settings.Global;
import android.provider.Settings.Secure;
import android.system.ErrnoException;
import android.system.Os;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.AtomicFile;
import android.util.EventLog;
import android.util.Log;
import android.util.Pair;
import android.util.Slog;
import android.util.SparseArray;
import android.util.StringBuilderPrinter;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.backup.IBackupTransport;
import com.android.internal.backup.IObbBackupService;
import com.android.server.AppWidgetBackupBridge;
import com.android.server.EventLogTags;
import com.android.server.SystemConfig;
import com.android.server.SystemService;
import com.android.server.am.HwBroadcastRadarUtil;
import com.android.server.backup.PackageManagerBackupAgent.Metadata;
import com.android.server.job.JobSchedulerShellCommand;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import libcore.io.IoUtils;

public class BackupManagerService {
    static final String BACKUP_ENABLE_FILE = "backup_enabled";
    static final String BACKUP_FILE_HEADER_MAGIC = "ANDROID BACKUP\n";
    static final int BACKUP_FILE_VERSION = 4;
    static final String BACKUP_MANIFEST_FILENAME = "_manifest";
    static final int BACKUP_MANIFEST_VERSION = 1;
    static final String BACKUP_METADATA_FILENAME = "_meta";
    static final int BACKUP_METADATA_VERSION = 1;
    static final int BACKUP_PW_FILE_VERSION = 2;
    static final int BACKUP_WIDGET_METADATA_TOKEN = 33549569;
    static final int BUSY_BACKOFF_FUZZ = 7200000;
    static final long BUSY_BACKOFF_MIN_MILLIS = 3600000;
    static final boolean COMPRESS_FULL_BACKUPS = true;
    static final int CURRENT_ANCESTRAL_RECORD_VERSION = 1;
    static final boolean DEBUG;
    static final boolean DEBUG_BACKUP_TRACE = true;
    static final boolean DEBUG_SCHEDULING = DEBUG;
    static final String ENCRYPTION_ALGORITHM_NAME = "AES-256";
    static final String INIT_SENTINEL_FILE_NAME = "_need_init_";
    static final String KEY_WIDGET_STATE = "￭￭widget";
    static final long MIN_FULL_BACKUP_INTERVAL = 86400000;
    static final boolean MORE_DEBUG = false;
    static final int MSG_BACKUP_RESTORE_STEP = 20;
    private static final int MSG_FULL_CONFIRMATION_TIMEOUT = 9;
    static final int MSG_OP_COMPLETE = 21;
    private static final int MSG_REQUEST_BACKUP = 15;
    private static final int MSG_RESTORE_TIMEOUT = 8;
    private static final int MSG_RETRY_CLEAR = 12;
    private static final int MSG_RETRY_INIT = 11;
    private static final int MSG_RUN_ADB_BACKUP = 2;
    private static final int MSG_RUN_ADB_RESTORE = 10;
    private static final int MSG_RUN_BACKUP = 1;
    private static final int MSG_RUN_CLEAR = 4;
    private static final int MSG_RUN_FULL_TRANSPORT_BACKUP = 14;
    private static final int MSG_RUN_GET_RESTORE_SETS = 6;
    private static final int MSG_RUN_INITIALIZE = 5;
    private static final int MSG_RUN_RESTORE = 3;
    private static final int MSG_TIMEOUT = 7;
    private static final int MSG_WIDGET_BROADCAST = 13;
    static final int OP_ACKNOWLEDGED = 1;
    static final int OP_PENDING = 0;
    static final int OP_TIMEOUT = -1;
    static final String PACKAGE_MANAGER_SENTINEL = "@pm@";
    static final int PBKDF2_HASH_ROUNDS = 10000;
    static final int PBKDF2_KEY_SIZE = 256;
    static final int PBKDF2_SALT_SIZE = 512;
    static final String PBKDF_CURRENT = "PBKDF2WithHmacSHA1";
    static final String PBKDF_FALLBACK = "PBKDF2WithHmacSHA1And8bit";
    private static final String RUN_BACKUP_ACTION = "android.app.backup.intent.RUN";
    private static final String RUN_INITIALIZE_ACTION = "android.app.backup.intent.INIT";
    static final int SCHEDULE_FILE_VERSION = 1;
    static final String SERVICE_ACTION_TRANSPORT_HOST = "android.backup.TRANSPORT_HOST";
    static final String SETTINGS_PACKAGE = "com.android.providers.settings";
    static final String SHARED_BACKUP_AGENT_PACKAGE = "com.android.sharedstoragebackup";
    private static final String TAG = "BackupManagerService";
    static final long TIMEOUT_BACKUP_INTERVAL = 30000;
    static final long TIMEOUT_FULL_BACKUP_INTERVAL = 300000;
    static final long TIMEOUT_FULL_CONFIRMATION = 60000;
    static final long TIMEOUT_INTERVAL = 10000;
    static final long TIMEOUT_RESTORE_FINISHED_INTERVAL = 30000;
    static final long TIMEOUT_RESTORE_INTERVAL = 60000;
    static final long TIMEOUT_SHARED_BACKUP_INTERVAL = 1800000;
    private static final long TRANSPORT_RETRY_INTERVAL = 3600000;
    static Trampoline sInstance;
    ActiveRestoreSession mActiveRestoreSession;
    private IActivityManager mActivityManager;
    final Object mAgentConnectLock = new Object();
    private AlarmManager mAlarmManager;
    Set<String> mAncestralPackages = null;
    long mAncestralToken = 0;
    boolean mAutoRestore;
    BackupHandler mBackupHandler;
    IBackupManager mBackupManagerBinder;
    final SparseArray<HashSet<String>> mBackupParticipants = new SparseArray();
    volatile boolean mBackupRunning;
    final List<String> mBackupTrace = new ArrayList();
    File mBaseStateDir;
    BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            TransportConnection conn;
            String action = intent.getAction();
            boolean replacing = false;
            boolean z = false;
            Bundle extras = intent.getExtras();
            String[] strArr = null;
            if ("android.intent.action.PACKAGE_ADDED".equals(action) || "android.intent.action.PACKAGE_REMOVED".equals(action) || "android.intent.action.PACKAGE_CHANGED".equals(action)) {
                Uri uri = intent.getData();
                if (uri != null) {
                    String pkgName = uri.getSchemeSpecificPart();
                    if (pkgName != null) {
                        strArr = new String[]{pkgName};
                    }
                    if ("android.intent.action.PACKAGE_CHANGED".equals(action)) {
                        try {
                            String[] components = intent.getStringArrayExtra("android.intent.extra.changed_component_name_list");
                            boolean tryBind = true;
                            synchronized (BackupManagerService.this.mTransports) {
                                conn = (TransportConnection) BackupManagerService.this.mTransportConnections.get(pkgName);
                                if (conn != null) {
                                    ServiceInfo svc = conn.mTransport;
                                    ComponentName componentName = new ComponentName(svc.packageName, svc.name);
                                    if (svc.packageName.equals(pkgName)) {
                                        String className = componentName.getClassName();
                                        boolean isTransport = false;
                                        for (String equals : components) {
                                            if (className.equals(equals)) {
                                                String flatName = componentName.flattenToShortString();
                                                BackupManagerService.this.mContext.unbindService(conn);
                                                BackupManagerService.this.mTransportConnections.remove(pkgName);
                                                BackupManagerService.this.mTransports.remove(BackupManagerService.this.mTransportNames.get(flatName));
                                                BackupManagerService.this.mTransportNames.remove(flatName);
                                                isTransport = true;
                                                break;
                                            }
                                        }
                                        if (!isTransport) {
                                            tryBind = false;
                                        }
                                    }
                                }
                            }
                            if (tryBind) {
                                BackupManagerService.this.checkForTransportAndBind(BackupManagerService.this.mPackageManager.getPackageInfo(pkgName, 0));
                            }
                        } catch (NameNotFoundException e) {
                        }
                        return;
                    }
                    z = "android.intent.action.PACKAGE_ADDED".equals(action);
                    replacing = extras.getBoolean("android.intent.extra.REPLACING", false);
                } else {
                    return;
                }
            } else if ("android.intent.action.EXTERNAL_APPLICATIONS_AVAILABLE".equals(action)) {
                z = true;
                strArr = intent.getStringArrayExtra("android.intent.extra.changed_package_list");
            } else if ("android.intent.action.EXTERNAL_APPLICATIONS_UNAVAILABLE".equals(action)) {
                z = false;
                strArr = intent.getStringArrayExtra("android.intent.extra.changed_package_list");
            }
            if (strArr != null && strArr.length != 0) {
                int uid = extras.getInt("android.intent.extra.UID");
                if (z) {
                    synchronized (BackupManagerService.this.mBackupParticipants) {
                        if (replacing) {
                            BackupManagerService.this.removePackageParticipantsLocked(strArr, uid);
                        }
                        BackupManagerService.this.addPackageParticipantsLocked(strArr);
                    }
                    long now = System.currentTimeMillis();
                    for (String packageName : strArr) {
                        PackageInfo app = BackupManagerService.this.mPackageManager.getPackageInfo(packageName, 0);
                        if (BackupManagerService.appGetsFullBackup(app) && BackupManagerService.appIsEligibleForBackup(app.applicationInfo)) {
                            BackupManagerService.this.enqueueFullBackup(packageName, now);
                            BackupManagerService.this.scheduleNextFullBackupJob(0);
                        } else {
                            try {
                                synchronized (BackupManagerService.this.mQueueLock) {
                                    BackupManagerService.this.dequeueFullBackupLocked(packageName);
                                }
                                BackupManagerService.this.writeFullBackupScheduleAsync();
                            } catch (NameNotFoundException e2) {
                                if (BackupManagerService.DEBUG) {
                                    Slog.w(BackupManagerService.TAG, "Can't resolve new app " + packageName);
                                }
                            }
                        }
                        synchronized (BackupManagerService.this.mTransports) {
                            conn = (TransportConnection) BackupManagerService.this.mTransportConnections.get(packageName);
                            if (conn != null) {
                                BackupManagerService.this.bindTransport(conn.mTransport);
                            } else {
                                BackupManagerService.this.checkForTransportAndBind(app);
                            }
                        }
                    }
                    BackupManagerService.this.dataChangedImpl(BackupManagerService.PACKAGE_MANAGER_SENTINEL);
                } else if (!replacing) {
                    synchronized (BackupManagerService.this.mBackupParticipants) {
                        BackupManagerService.this.removePackageParticipantsLocked(strArr, uid);
                    }
                }
            }
        }
    };
    final Object mClearDataLock = new Object();
    volatile boolean mClearingData;
    IBackupAgent mConnectedAgent;
    volatile boolean mConnecting;
    Context mContext;
    final Object mCurrentOpLock = new Object();
    final SparseArray<Operation> mCurrentOperations = new SparseArray();
    long mCurrentToken = 0;
    String mCurrentTransport;
    File mDataDir;
    boolean mEnabled;
    private File mEverStored;
    HashSet<String> mEverStoredApps = new HashSet();
    @GuardedBy("mQueueLock")
    ArrayList<FullBackupEntry> mFullBackupQueue;
    File mFullBackupScheduleFile;
    Runnable mFullBackupScheduleWriter = new Runnable() {
        public void run() {
            synchronized (BackupManagerService.this.mQueueLock) {
                try {
                    ByteArrayOutputStream bufStream = new ByteArrayOutputStream(4096);
                    DataOutputStream bufOut = new DataOutputStream(bufStream);
                    bufOut.writeInt(1);
                    int N = BackupManagerService.this.mFullBackupQueue.size();
                    bufOut.writeInt(N);
                    for (int i = 0; i < N; i++) {
                        FullBackupEntry entry = (FullBackupEntry) BackupManagerService.this.mFullBackupQueue.get(i);
                        bufOut.writeUTF(entry.packageName);
                        bufOut.writeLong(entry.lastBackup);
                    }
                    bufOut.flush();
                    AtomicFile af = new AtomicFile(BackupManagerService.this.mFullBackupScheduleFile);
                    FileOutputStream out = af.startWrite();
                    out.write(bufStream.toByteArray());
                    af.finishWrite(out);
                } catch (Exception e) {
                    Slog.e(BackupManagerService.TAG, "Unable to write backup schedule!", e);
                }
            }
        }
    };
    final SparseArray<FullParams> mFullConfirmations = new SparseArray();
    HandlerThread mHandlerThread;
    File mJournal;
    File mJournalDir;
    volatile long mLastBackupPass;
    private IMountService mMountService;
    private PackageManager mPackageManager;
    IPackageManager mPackageManagerBinder;
    private String mPasswordHash;
    private File mPasswordHashFile;
    private byte[] mPasswordSalt;
    private int mPasswordVersion;
    private File mPasswordVersionFile;
    HashMap<String, BackupRequest> mPendingBackups = new HashMap();
    HashSet<String> mPendingInits = new HashSet();
    private PowerManager mPowerManager;
    boolean mProvisioned;
    ContentObserver mProvisionedObserver;
    final Object mQueueLock = new Object();
    private final SecureRandom mRng = new SecureRandom();
    PendingIntent mRunBackupIntent;
    BroadcastReceiver mRunBackupReceiver;
    PendingIntent mRunInitIntent;
    BroadcastReceiver mRunInitReceiver;
    @GuardedBy("mQueueLock")
    PerformFullTransportBackupTask mRunningFullBackupTask;
    File mTokenFile;
    final Random mTokenGenerator = new Random();
    final ArrayMap<String, TransportConnection> mTransportConnections = new ArrayMap();
    final ArrayMap<String, String> mTransportNames = new ArrayMap();
    final Intent mTransportServiceIntent = new Intent(SERVICE_ACTION_TRANSPORT_HOST);
    final ArraySet<ComponentName> mTransportWhitelist;
    final ArrayMap<String, IBackupTransport> mTransports = new ArrayMap();
    WakeLock mWakelock;

    class ActiveRestoreSession extends Stub {
        private static final String TAG = "RestoreSession";
        boolean mEnded = false;
        private String mPackageName;
        RestoreSet[] mRestoreSets = null;
        private IBackupTransport mRestoreTransport = null;
        boolean mTimedOut = false;

        class EndRestoreRunnable implements Runnable {
            BackupManagerService mBackupManager;
            ActiveRestoreSession mSession;

            EndRestoreRunnable(BackupManagerService manager, ActiveRestoreSession session) {
                this.mBackupManager = manager;
                this.mSession = session;
            }

            public void run() {
                synchronized (this.mSession) {
                    this.mSession.mRestoreTransport = null;
                    this.mSession.mEnded = true;
                }
                this.mBackupManager.clearRestoreSession(this.mSession);
            }
        }

        ActiveRestoreSession(String packageName, String transport) {
            this.mPackageName = packageName;
            this.mRestoreTransport = BackupManagerService.this.getTransport(transport);
        }

        public void markTimedOut() {
            this.mTimedOut = true;
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public synchronized int getAvailableRestoreSets(IRestoreObserver observer) {
            BackupManagerService.this.mContext.enforceCallingOrSelfPermission("android.permission.BACKUP", "getAvailableRestoreSets");
            if (observer == null) {
                throw new IllegalArgumentException("Observer must not be null");
            } else if (this.mEnded) {
                throw new IllegalStateException("Restore session already ended");
            } else if (this.mTimedOut) {
                Slog.i(TAG, "Session already timed out");
                return -1;
            } else {
                long oldId = Binder.clearCallingIdentity();
                try {
                    if (this.mRestoreTransport == null) {
                        Slog.w(TAG, "Null transport getting restore sets");
                    } else {
                        BackupManagerService.this.mBackupHandler.removeMessages(8);
                        BackupManagerService.this.mWakelock.acquire();
                        BackupManagerService.this.mBackupHandler.sendMessage(BackupManagerService.this.mBackupHandler.obtainMessage(6, new RestoreGetSetsParams(this.mRestoreTransport, this, observer)));
                        Binder.restoreCallingIdentity(oldId);
                        return 0;
                    }
                } catch (Exception e) {
                    Slog.e(TAG, "Error in getAvailableRestoreSets", e);
                    return -1;
                } finally {
                    Binder.restoreCallingIdentity(oldId);
                }
            }
        }

        public synchronized int restoreAll(long token, IRestoreObserver observer) {
            BackupManagerService.this.mContext.enforceCallingOrSelfPermission("android.permission.BACKUP", "performRestore");
            if (BackupManagerService.DEBUG) {
                Slog.d(TAG, "restoreAll token=" + Long.toHexString(token) + " observer=" + observer);
            }
            if (this.mEnded) {
                throw new IllegalStateException("Restore session already ended");
            } else if (this.mTimedOut) {
                Slog.i(TAG, "Session already timed out");
                return -1;
            } else if (this.mRestoreTransport == null || this.mRestoreSets == null) {
                Slog.e(TAG, "Ignoring restoreAll() with no restore set");
                return -1;
            } else if (this.mPackageName != null) {
                Slog.e(TAG, "Ignoring restoreAll() on single-package session");
                return -1;
            } else {
                try {
                    String dirName = this.mRestoreTransport.transportDirName();
                    synchronized (BackupManagerService.this.mQueueLock) {
                        for (RestoreSet restoreSet : this.mRestoreSets) {
                            if (token == restoreSet.token) {
                                BackupManagerService.this.mBackupHandler.removeMessages(8);
                                long oldId = Binder.clearCallingIdentity();
                                BackupManagerService.this.mWakelock.acquire();
                                Message msg = BackupManagerService.this.mBackupHandler.obtainMessage(3);
                                msg.obj = new RestoreParams(this.mRestoreTransport, dirName, observer, token);
                                BackupManagerService.this.mBackupHandler.sendMessage(msg);
                                Binder.restoreCallingIdentity(oldId);
                                return 0;
                            }
                        }
                        Slog.w(TAG, "Restore token " + Long.toHexString(token) + " not found");
                        return -1;
                    }
                } catch (RemoteException e) {
                    Slog.e(TAG, "Unable to contact transport for restore");
                    return -1;
                }
            }
        }

        public synchronized int restoreSome(long token, IRestoreObserver observer, String[] packages) {
            BackupManagerService.this.mContext.enforceCallingOrSelfPermission("android.permission.BACKUP", "performRestore");
            if (BackupManagerService.DEBUG) {
                StringBuilder b = new StringBuilder(128);
                b.append("restoreSome token=");
                b.append(Long.toHexString(token));
                b.append(" observer=");
                b.append(observer.toString());
                b.append(" packages=");
                if (packages == null) {
                    b.append("null");
                } else {
                    b.append('{');
                    boolean first = true;
                    for (String s : packages) {
                        if (first) {
                            first = false;
                        } else {
                            b.append(", ");
                        }
                        b.append(s);
                    }
                    b.append('}');
                }
                Slog.d(TAG, b.toString());
            }
            if (this.mEnded) {
                throw new IllegalStateException("Restore session already ended");
            } else if (this.mTimedOut) {
                Slog.i(TAG, "Session already timed out");
                return -1;
            } else if (this.mRestoreTransport == null || this.mRestoreSets == null) {
                Slog.e(TAG, "Ignoring restoreAll() with no restore set");
                return -1;
            } else if (this.mPackageName != null) {
                Slog.e(TAG, "Ignoring restoreAll() on single-package session");
                return -1;
            } else {
                try {
                    String dirName = this.mRestoreTransport.transportDirName();
                    synchronized (BackupManagerService.this.mQueueLock) {
                        for (RestoreSet restoreSet : this.mRestoreSets) {
                            if (token == restoreSet.token) {
                                BackupManagerService.this.mBackupHandler.removeMessages(8);
                                long oldId = Binder.clearCallingIdentity();
                                BackupManagerService.this.mWakelock.acquire();
                                Message msg = BackupManagerService.this.mBackupHandler.obtainMessage(3);
                                msg.obj = new RestoreParams(this.mRestoreTransport, dirName, observer, token, packages, packages.length > 1);
                                BackupManagerService.this.mBackupHandler.sendMessage(msg);
                                Binder.restoreCallingIdentity(oldId);
                                return 0;
                            }
                        }
                        Slog.w(TAG, "Restore token " + Long.toHexString(token) + " not found");
                        return -1;
                    }
                } catch (RemoteException e) {
                    Slog.e(TAG, "Unable to contact transport for restore");
                    return -1;
                }
            }
        }

        public synchronized int restorePackage(String packageName, IRestoreObserver observer) {
            if (BackupManagerService.DEBUG) {
                Slog.v(TAG, "restorePackage pkg=" + packageName + " obs=" + observer);
            }
            if (this.mEnded) {
                throw new IllegalStateException("Restore session already ended");
            } else if (this.mTimedOut) {
                Slog.i(TAG, "Session already timed out");
                return -1;
            } else if (this.mPackageName == null || this.mPackageName.equals(packageName)) {
                try {
                    PackageInfo app = BackupManagerService.this.mPackageManager.getPackageInfo(packageName, 0);
                    if (BackupManagerService.this.mContext.checkPermission("android.permission.BACKUP", Binder.getCallingPid(), Binder.getCallingUid()) != -1 || app.applicationInfo.uid == Binder.getCallingUid()) {
                        long oldId = Binder.clearCallingIdentity();
                        try {
                            long token = BackupManagerService.this.getAvailableRestoreToken(packageName);
                            if (BackupManagerService.DEBUG) {
                                Slog.v(TAG, "restorePackage pkg=" + packageName + " token=" + Long.toHexString(token));
                            }
                            if (token == 0) {
                                if (BackupManagerService.DEBUG) {
                                    Slog.w(TAG, "No data available for this package; not restoring");
                                }
                                return -1;
                            }
                            String dirName = this.mRestoreTransport.transportDirName();
                            BackupManagerService.this.mBackupHandler.removeMessages(8);
                            BackupManagerService.this.mWakelock.acquire();
                            Message msg = BackupManagerService.this.mBackupHandler.obtainMessage(3);
                            msg.obj = new RestoreParams(this.mRestoreTransport, dirName, observer, token, app);
                            BackupManagerService.this.mBackupHandler.sendMessage(msg);
                            return 0;
                        } catch (RemoteException e) {
                            Slog.e(TAG, "Unable to contact transport for restore");
                            return -1;
                        } finally {
                            Binder.restoreCallingIdentity(oldId);
                        }
                    } else {
                        Slog.w(TAG, "restorePackage: bad packageName=" + packageName + " or calling uid=" + Binder.getCallingUid());
                        throw new SecurityException("No permission to restore other packages");
                    }
                } catch (NameNotFoundException e2) {
                    Slog.w(TAG, "Asked to restore nonexistent pkg " + packageName);
                    return -1;
                }
            } else {
                Slog.e(TAG, "Ignoring attempt to restore pkg=" + packageName + " on session for package " + this.mPackageName);
                return -1;
            }
        }

        public synchronized void endRestoreSession() {
            if (BackupManagerService.DEBUG) {
                Slog.d(TAG, "endRestoreSession");
            }
            if (this.mTimedOut) {
                Slog.i(TAG, "Session already timed out");
            } else if (this.mEnded) {
                throw new IllegalStateException("Restore session already ended");
            } else {
                BackupManagerService.this.mBackupHandler.post(new EndRestoreRunnable(BackupManagerService.this, this));
            }
        }
    }

    interface BackupRestoreTask {
        void execute();

        void handleTimeout();

        void operationComplete(long j);
    }

    class AdbRestoreFinishedLatch implements BackupRestoreTask {
        static final String TAG = "AdbRestoreFinishedLatch";
        final CountDownLatch mLatch = new CountDownLatch(1);

        AdbRestoreFinishedLatch() {
        }

        void await() {
            try {
                boolean latched = this.mLatch.await(BackupManagerService.TIMEOUT_FULL_BACKUP_INTERVAL, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Slog.w(TAG, "Interrupted!");
            }
        }

        public void execute() {
        }

        public void operationComplete(long result) {
            this.mLatch.countDown();
        }

        public void handleTimeout() {
            if (BackupManagerService.DEBUG) {
                Slog.w(TAG, "adb onRestoreFinished() timed out");
            }
            this.mLatch.countDown();
        }
    }

    private class BackupHandler extends Handler {
        public BackupHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    BackupManagerService.this.mLastBackupPass = System.currentTimeMillis();
                    IBackupTransport transport = BackupManagerService.this.getTransport(BackupManagerService.this.mCurrentTransport);
                    if (transport == null) {
                        Slog.v(BackupManagerService.TAG, "Backup requested but no transport available");
                        synchronized (BackupManagerService.this.mQueueLock) {
                            BackupManagerService.this.mBackupRunning = false;
                        }
                        BackupManagerService.this.mWakelock.release();
                        return;
                    }
                    ArrayList<BackupRequest> queue = new ArrayList();
                    File oldJournal = BackupManagerService.this.mJournal;
                    synchronized (BackupManagerService.this.mQueueLock) {
                        if (BackupManagerService.this.mPendingBackups.size() > 0) {
                            for (BackupRequest b : BackupManagerService.this.mPendingBackups.values()) {
                                queue.add(b);
                            }
                            if (BackupManagerService.DEBUG) {
                                Slog.v(BackupManagerService.TAG, "clearing pending backups");
                            }
                            BackupManagerService.this.mPendingBackups.clear();
                            BackupManagerService.this.mJournal = null;
                        }
                    }
                    boolean staged = true;
                    if (queue.size() > 0) {
                        try {
                            sendMessage(obtainMessage(20, new PerformBackupTask(transport, transport.transportDirName(), queue, oldJournal, null, null, false)));
                        } catch (RemoteException e) {
                            Slog.e(BackupManagerService.TAG, "Transport became unavailable attempting backup");
                            staged = false;
                        }
                    } else {
                        Slog.v(BackupManagerService.TAG, "Backup requested but nothing pending");
                        staged = false;
                    }
                    if (!staged) {
                        synchronized (BackupManagerService.this.mQueueLock) {
                            BackupManagerService.this.mBackupRunning = false;
                        }
                        BackupManagerService.this.mWakelock.release();
                        return;
                    }
                    return;
                case 2:
                    FullBackupParams params = msg.obj;
                    new Thread(new PerformAdbBackupTask(params.fd, params.observer, params.includeApks, params.includeObbs, params.includeShared, params.doWidgets, params.curPassword, params.encryptPassword, params.allApps, params.includeSystem, params.doCompress, params.packages, params.latch), "adb-backup").start();
                    return;
                case 3:
                    RestoreParams params2 = msg.obj;
                    Slog.d(BackupManagerService.TAG, "MSG_RUN_RESTORE observer=" + params2.observer);
                    sendMessage(obtainMessage(20, new PerformUnifiedRestoreTask(params2.transport, params2.observer, params2.token, params2.pkgInfo, params2.pmToken, params2.isSystemRestore, params2.filterSet)));
                    return;
                case 4:
                    ClearParams params3 = msg.obj;
                    new PerformClearTask(params3.transport, params3.packageInfo).run();
                    return;
                case 5:
                    HashSet<String> hashSet;
                    synchronized (BackupManagerService.this.mQueueLock) {
                        hashSet = new HashSet(BackupManagerService.this.mPendingInits);
                        BackupManagerService.this.mPendingInits.clear();
                    }
                    new PerformInitializeTask(hashSet).run();
                    return;
                case 6:
                    RestoreSet[] restoreSetArr = null;
                    RestoreGetSetsParams params4 = msg.obj;
                    try {
                        restoreSetArr = params4.transport.getAvailableRestoreSets();
                        synchronized (params4.session) {
                            params4.session.mRestoreSets = restoreSetArr;
                        }
                        if (restoreSetArr == null) {
                            EventLog.writeEvent(EventLogTags.RESTORE_TRANSPORT_FAILURE, new Object[0]);
                        }
                        if (params4.observer != null) {
                            try {
                                params4.observer.restoreSetsAvailable(restoreSetArr);
                            } catch (RemoteException e2) {
                                Slog.e(BackupManagerService.TAG, "Unable to report listing to observer");
                            } catch (Throwable e3) {
                                Slog.e(BackupManagerService.TAG, "Restore observer threw", e3);
                            }
                        }
                        removeMessages(8);
                        sendEmptyMessageDelayed(8, 60000);
                        BackupManagerService.this.mWakelock.release();
                        return;
                    } catch (Exception e4) {
                        try {
                            Slog.e(BackupManagerService.TAG, "Error from transport getting set list");
                            if (params4.observer != null) {
                                try {
                                    params4.observer.restoreSetsAvailable(restoreSetArr);
                                } catch (RemoteException e5) {
                                    Slog.e(BackupManagerService.TAG, "Unable to report listing to observer");
                                } catch (Throwable e32) {
                                    Slog.e(BackupManagerService.TAG, "Restore observer threw", e32);
                                }
                            }
                            removeMessages(8);
                            sendEmptyMessageDelayed(8, 60000);
                            BackupManagerService.this.mWakelock.release();
                            return;
                        } catch (Throwable th) {
                            if (params4.observer != null) {
                                try {
                                    params4.observer.restoreSetsAvailable(restoreSetArr);
                                } catch (RemoteException e6) {
                                    Slog.e(BackupManagerService.TAG, "Unable to report listing to observer");
                                } catch (Throwable e322) {
                                    Slog.e(BackupManagerService.TAG, "Restore observer threw", e322);
                                }
                            }
                            removeMessages(8);
                            sendEmptyMessageDelayed(8, 60000);
                            BackupManagerService.this.mWakelock.release();
                        }
                    }
                case 7:
                    BackupManagerService.this.handleTimeout(msg.arg1, msg.obj);
                    return;
                case 8:
                    synchronized (BackupManagerService.this) {
                        if (BackupManagerService.this.mActiveRestoreSession != null) {
                            Slog.w(BackupManagerService.TAG, "Restore session timed out; aborting");
                            BackupManagerService.this.mActiveRestoreSession.markTimedOut();
                            ActiveRestoreSession activeRestoreSession = BackupManagerService.this.mActiveRestoreSession;
                            activeRestoreSession.getClass();
                            post(new EndRestoreRunnable(BackupManagerService.this, BackupManagerService.this.mActiveRestoreSession));
                        }
                    }
                    return;
                case 9:
                    synchronized (BackupManagerService.this.mFullConfirmations) {
                        FullParams params5 = (FullParams) BackupManagerService.this.mFullConfirmations.get(msg.arg1);
                        if (params5 != null) {
                            Slog.i(BackupManagerService.TAG, "Full backup/restore timed out waiting for user confirmation");
                            BackupManagerService.this.signalFullBackupRestoreCompletion(params5);
                            BackupManagerService.this.mFullConfirmations.delete(msg.arg1);
                            if (params5.observer != null) {
                                try {
                                    params5.observer.onTimeout();
                                } catch (RemoteException e7) {
                                }
                            }
                        } else {
                            Slog.d(BackupManagerService.TAG, "couldn't find params for token " + msg.arg1);
                        }
                    }
                    return;
                case 10:
                    FullRestoreParams params6 = msg.obj;
                    new Thread(new PerformAdbRestoreTask(params6.fd, params6.curPassword, params6.encryptPassword, params6.observer, params6.latch), "adb-restore").start();
                    return;
                case 11:
                    synchronized (BackupManagerService.this.mQueueLock) {
                        BackupManagerService.this.recordInitPendingLocked(msg.arg1 != 0, (String) msg.obj);
                        BackupManagerService.this.mAlarmManager.set(0, System.currentTimeMillis(), BackupManagerService.this.mRunInitIntent);
                    }
                    return;
                case 12:
                    ClearRetryParams params7 = msg.obj;
                    BackupManagerService.this.clearBackupData(params7.transportName, params7.packageName);
                    return;
                case 13:
                    BackupManagerService.this.mContext.sendBroadcastAsUser(msg.obj, UserHandle.SYSTEM);
                    return;
                case 14:
                    new Thread(msg.obj, "transport-backup").start();
                    return;
                case 15:
                    BackupParams params8 = msg.obj;
                    ArrayList<BackupRequest> kvQueue = new ArrayList();
                    for (String packageName : params8.kvPackages) {
                        kvQueue.add(new BackupRequest(packageName));
                    }
                    BackupManagerService.this.mBackupRunning = true;
                    BackupManagerService.this.mWakelock.acquire();
                    sendMessage(obtainMessage(20, new PerformBackupTask(params8.transport, params8.dirName, kvQueue, null, params8.observer, params8.fullPackages, true)));
                    return;
                case 20:
                    try {
                        msg.obj.execute();
                        return;
                    } catch (ClassCastException e8) {
                        Slog.e(BackupManagerService.TAG, "Invalid backup task in flight, obj=" + msg.obj);
                        return;
                    }
                case 21:
                    try {
                        Pair<BackupRestoreTask, Long> taskWithResult = msg.obj;
                        ((BackupRestoreTask) taskWithResult.first).operationComplete(((Long) taskWithResult.second).longValue());
                        return;
                    } catch (ClassCastException e9) {
                        Slog.e(BackupManagerService.TAG, "Invalid completion in flight, obj=" + msg.obj);
                        return;
                    }
                default:
                    return;
            }
        }
    }

    class BackupParams {
        public String dirName;
        public ArrayList<String> fullPackages;
        public ArrayList<String> kvPackages;
        public IBackupObserver observer;
        public IBackupTransport transport;
        public boolean userInitiated;

        BackupParams(IBackupTransport transport, String dirName, ArrayList<String> kvPackages, ArrayList<String> fullPackages, IBackupObserver observer, boolean userInitiated) {
            this.transport = transport;
            this.dirName = dirName;
            this.kvPackages = kvPackages;
            this.fullPackages = fullPackages;
            this.observer = observer;
            this.userInitiated = userInitiated;
        }
    }

    class BackupRequest {
        public String packageName;

        BackupRequest(String pkgName) {
            this.packageName = pkgName;
        }

        public String toString() {
            return "BackupRequest{pkg=" + this.packageName + "}";
        }
    }

    enum BackupState {
        INITIAL,
        RUNNING_QUEUE,
        FINAL
    }

    class ClearDataObserver extends IPackageDataObserver.Stub {
        ClearDataObserver() {
        }

        public void onRemoveCompleted(String packageName, boolean succeeded) {
            synchronized (BackupManagerService.this.mClearDataLock) {
                BackupManagerService.this.mClearingData = false;
                BackupManagerService.this.mClearDataLock.notifyAll();
            }
        }
    }

    class ClearParams {
        public PackageInfo packageInfo;
        public IBackupTransport transport;

        ClearParams(IBackupTransport _transport, PackageInfo _info) {
            this.transport = _transport;
            this.packageInfo = _info;
        }
    }

    class ClearRetryParams {
        public String packageName;
        public String transportName;

        ClearRetryParams(String transport, String pkg) {
            this.transportName = transport;
            this.packageName = pkg;
        }
    }

    static class FileMetadata {
        String domain;
        String installerPackageName;
        long mode;
        long mtime;
        String packageName;
        String path;
        long size;
        int type;

        FileMetadata() {
        }

        public String toString() {
            StringBuilder sb = new StringBuilder(128);
            sb.append("FileMetadata{");
            sb.append(this.packageName);
            sb.append(',');
            sb.append(this.type);
            sb.append(',');
            sb.append(this.domain);
            sb.append(':');
            sb.append(this.path);
            sb.append(',');
            sb.append(this.size);
            sb.append('}');
            return sb.toString();
        }
    }

    class FullBackupEngine {
        IBackupAgent mAgent;
        File mFilesDir = new File("/data/system");
        boolean mIncludeApks;
        File mManifestFile = new File(this.mFilesDir, BackupManagerService.BACKUP_MANIFEST_FILENAME);
        File mMetadataFile = new File(this.mFilesDir, BackupManagerService.BACKUP_METADATA_FILENAME);
        OutputStream mOutput;
        PackageInfo mPkg;
        FullBackupPreflight mPreflightHook;
        BackupRestoreTask mTimeoutMonitor;

        class FullBackupRunner implements Runnable {
            IBackupAgent mAgent;
            PackageInfo mPackage;
            ParcelFileDescriptor mPipe;
            boolean mSendApk;
            int mToken;
            byte[] mWidgetData;
            boolean mWriteManifest;

            FullBackupRunner(PackageInfo pack, IBackupAgent agent, ParcelFileDescriptor pipe, int token, boolean sendApk, boolean writeManifest, byte[] widgetData) throws IOException {
                this.mPackage = pack;
                this.mWidgetData = widgetData;
                this.mAgent = agent;
                this.mPipe = ParcelFileDescriptor.dup(pipe.getFileDescriptor());
                this.mToken = token;
                this.mSendApk = sendApk;
                this.mWriteManifest = writeManifest;
            }

            /* JADX WARNING: inconsistent code. */
            /* Code decompiled incorrectly, please refer to instructions dump. */
            public void run() {
                try {
                    FullBackupDataOutput output = new FullBackupDataOutput(this.mPipe);
                    if (this.mWriteManifest) {
                        boolean writeWidgetData = this.mWidgetData != null;
                        FullBackupEngine.this.writeAppManifest(this.mPackage, FullBackupEngine.this.mManifestFile, this.mSendApk, writeWidgetData);
                        FullBackup.backupToTar(this.mPackage.packageName, null, null, FullBackupEngine.this.mFilesDir.getAbsolutePath(), FullBackupEngine.this.mManifestFile.getAbsolutePath(), output);
                        FullBackupEngine.this.mManifestFile.delete();
                        if (writeWidgetData) {
                            FullBackupEngine.this.writeMetadata(this.mPackage, FullBackupEngine.this.mMetadataFile, this.mWidgetData);
                            FullBackup.backupToTar(this.mPackage.packageName, null, null, FullBackupEngine.this.mFilesDir.getAbsolutePath(), FullBackupEngine.this.mMetadataFile.getAbsolutePath(), output);
                            FullBackupEngine.this.mMetadataFile.delete();
                        }
                    }
                    if (this.mSendApk) {
                        FullBackupEngine.this.writeApkToBackup(this.mPackage, output);
                    }
                    if (BackupManagerService.DEBUG) {
                        Slog.d(BackupManagerService.TAG, "Calling doFullBackup() on " + this.mPackage.packageName);
                    }
                    BackupManagerService.this.prepareOperationTimeout(this.mToken, BackupManagerService.TIMEOUT_FULL_BACKUP_INTERVAL, FullBackupEngine.this.mTimeoutMonitor);
                    this.mAgent.doFullBackup(this.mPipe, this.mToken, BackupManagerService.this.mBackupManagerBinder);
                    try {
                        this.mPipe.close();
                    } catch (IOException e) {
                    }
                } catch (IOException e2) {
                    Slog.e(BackupManagerService.TAG, "Error running full backup for " + this.mPackage.packageName);
                } catch (RemoteException e3) {
                    Slog.e(BackupManagerService.TAG, "Remote agent vanished during full backup of " + this.mPackage.packageName);
                    try {
                        this.mPipe.close();
                    } catch (IOException e4) {
                    }
                } catch (Throwable th) {
                    try {
                        this.mPipe.close();
                    } catch (IOException e5) {
                    }
                }
            }
        }

        FullBackupEngine(OutputStream output, FullBackupPreflight preflightHook, PackageInfo pkg, boolean alsoApks, BackupRestoreTask timeoutMonitor) {
            this.mOutput = output;
            this.mPreflightHook = preflightHook;
            this.mPkg = pkg;
            this.mIncludeApks = alsoApks;
            this.mTimeoutMonitor = timeoutMonitor;
        }

        public int preflightCheck() throws RemoteException {
            if (this.mPreflightHook == null) {
                return 0;
            }
            if (initializeAgent()) {
                return this.mPreflightHook.preflightFullBackup(this.mPkg, this.mAgent);
            }
            Slog.w(BackupManagerService.TAG, "Unable to bind to full agent for " + this.mPkg.packageName);
            return -1003;
        }

        public int backupOnePackage() throws RemoteException {
            boolean z = false;
            int result = -1003;
            if (initializeAgent()) {
                ParcelFileDescriptor[] parcelFileDescriptorArr = null;
                try {
                    boolean sendApk;
                    byte[] widgetBlob;
                    int token;
                    PackageInfo packageInfo;
                    IBackupAgent iBackupAgent;
                    ParcelFileDescriptor parcelFileDescriptor;
                    FullBackupRunner runner;
                    parcelFileDescriptorArr = ParcelFileDescriptor.createPipe();
                    ApplicationInfo app = this.mPkg.applicationInfo;
                    boolean isSharedStorage = this.mPkg.packageName.equals(BackupManagerService.SHARED_BACKUP_AGENT_PACKAGE);
                    if (this.mIncludeApks && !isSharedStorage) {
                        if ((app.privateFlags & 4) == 0) {
                            sendApk = (app.flags & 1) != 0 ? (app.flags & 128) != 0 : true;
                            widgetBlob = AppWidgetBackupBridge.getWidgetState(this.mPkg.packageName, 0);
                            token = BackupManagerService.this.generateToken();
                            packageInfo = this.mPkg;
                            iBackupAgent = this.mAgent;
                            parcelFileDescriptor = parcelFileDescriptorArr[1];
                            if (!isSharedStorage) {
                                z = true;
                            }
                            runner = new FullBackupRunner(packageInfo, iBackupAgent, parcelFileDescriptor, token, sendApk, z, widgetBlob);
                            parcelFileDescriptorArr[1].close();
                            parcelFileDescriptorArr[1] = null;
                            new Thread(runner, "app-data-runner").start();
                            BackupManagerService.this.routeSocketDataToOutput(parcelFileDescriptorArr[0], this.mOutput);
                            if (BackupManagerService.this.waitUntilOperationComplete(token)) {
                                Slog.e(BackupManagerService.TAG, "Full backup failed on package " + this.mPkg.packageName);
                            } else {
                                result = 0;
                            }
                            this.mOutput.flush();
                            if (parcelFileDescriptorArr != null) {
                                if (parcelFileDescriptorArr[0] != null) {
                                    parcelFileDescriptorArr[0].close();
                                }
                                if (parcelFileDescriptorArr[1] != null) {
                                    parcelFileDescriptorArr[1].close();
                                }
                            }
                        }
                    }
                    sendApk = false;
                    widgetBlob = AppWidgetBackupBridge.getWidgetState(this.mPkg.packageName, 0);
                    token = BackupManagerService.this.generateToken();
                    packageInfo = this.mPkg;
                    iBackupAgent = this.mAgent;
                    parcelFileDescriptor = parcelFileDescriptorArr[1];
                    if (isSharedStorage) {
                        z = true;
                    }
                    runner = new FullBackupRunner(packageInfo, iBackupAgent, parcelFileDescriptor, token, sendApk, z, widgetBlob);
                    parcelFileDescriptorArr[1].close();
                    parcelFileDescriptorArr[1] = null;
                    new Thread(runner, "app-data-runner").start();
                    BackupManagerService.this.routeSocketDataToOutput(parcelFileDescriptorArr[0], this.mOutput);
                    if (BackupManagerService.this.waitUntilOperationComplete(token)) {
                        result = 0;
                    } else {
                        Slog.e(BackupManagerService.TAG, "Full backup failed on package " + this.mPkg.packageName);
                    }
                    try {
                        this.mOutput.flush();
                        if (parcelFileDescriptorArr != null) {
                            if (parcelFileDescriptorArr[0] != null) {
                                parcelFileDescriptorArr[0].close();
                            }
                            if (parcelFileDescriptorArr[1] != null) {
                                parcelFileDescriptorArr[1].close();
                            }
                        }
                    } catch (IOException e) {
                        Slog.w(BackupManagerService.TAG, "Error bringing down backup stack");
                        result = JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE;
                        tearDown();
                        return result;
                    }
                } catch (IOException e2) {
                    Slog.e(BackupManagerService.TAG, "Error backing up " + this.mPkg.packageName + ": " + e2.getMessage());
                    result = -1003;
                    try {
                        this.mOutput.flush();
                        if (parcelFileDescriptorArr != null) {
                            if (parcelFileDescriptorArr[0] != null) {
                                parcelFileDescriptorArr[0].close();
                            }
                            if (parcelFileDescriptorArr[1] != null) {
                                parcelFileDescriptorArr[1].close();
                            }
                        }
                    } catch (IOException e3) {
                        Slog.w(BackupManagerService.TAG, "Error bringing down backup stack");
                        result = JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE;
                        tearDown();
                        return result;
                    }
                } catch (Throwable th) {
                    try {
                        this.mOutput.flush();
                        if (parcelFileDescriptorArr != null) {
                            if (parcelFileDescriptorArr[0] != null) {
                                parcelFileDescriptorArr[0].close();
                            }
                            if (parcelFileDescriptorArr[1] != null) {
                                parcelFileDescriptorArr[1].close();
                            }
                        }
                    } catch (IOException e4) {
                        Slog.w(BackupManagerService.TAG, "Error bringing down backup stack");
                    }
                }
            } else {
                Slog.w(BackupManagerService.TAG, "Unable to bind to full agent for " + this.mPkg.packageName);
            }
            tearDown();
            return result;
        }

        public void sendQuotaExceeded(long backupDataBytes, long quotaBytes) {
            if (initializeAgent()) {
                try {
                    this.mAgent.doQuotaExceeded(backupDataBytes, quotaBytes);
                } catch (RemoteException e) {
                    Slog.e(BackupManagerService.TAG, "Remote exception while telling agent about quota exceeded");
                }
            }
        }

        private boolean initializeAgent() {
            if (this.mAgent == null) {
                this.mAgent = BackupManagerService.this.bindToAgentSynchronous(this.mPkg.applicationInfo, 1);
            }
            if (this.mAgent != null) {
                return true;
            }
            return false;
        }

        private void writeApkToBackup(PackageInfo pkg, FullBackupDataOutput output) {
            String appSourceDir = pkg.applicationInfo.getBaseCodePath();
            FullBackup.backupToTar(pkg.packageName, "a", null, new File(appSourceDir).getParent(), appSourceDir, output);
            File obbDir = new UserEnvironment(0).buildExternalStorageAppObbDirs(pkg.packageName)[0];
            if (obbDir != null) {
                File[] obbFiles = obbDir.listFiles();
                if (obbFiles != null) {
                    String obbDirName = obbDir.getAbsolutePath();
                    for (File obb : obbFiles) {
                        FullBackup.backupToTar(pkg.packageName, "obb", null, obbDirName, obb.getAbsolutePath(), output);
                    }
                }
            }
        }

        private void writeAppManifest(PackageInfo pkg, File manifestFile, boolean withApk, boolean withWidgets) throws IOException {
            StringBuilder builder = new StringBuilder(4096);
            StringBuilderPrinter printer = new StringBuilderPrinter(builder);
            printer.println(Integer.toString(1));
            printer.println(pkg.packageName);
            printer.println(Integer.toString(pkg.versionCode));
            printer.println(Integer.toString(VERSION.SDK_INT));
            String installerName = BackupManagerService.this.mPackageManager.getInstallerPackageName(pkg.packageName);
            if (installerName == null) {
                installerName = "";
            }
            printer.println(installerName);
            printer.println(withApk ? "1" : "0");
            if (pkg.signatures == null) {
                printer.println("0");
            } else {
                printer.println(Integer.toString(pkg.signatures.length));
                for (Signature sig : pkg.signatures) {
                    printer.println(sig.toCharsString());
                }
            }
            FileOutputStream outstream = new FileOutputStream(manifestFile);
            outstream.write(builder.toString().getBytes());
            outstream.close();
            manifestFile.setLastModified(0);
        }

        private void writeMetadata(PackageInfo pkg, File destination, byte[] widgetData) throws IOException {
            StringBuilder b = new StringBuilder(512);
            StringBuilderPrinter printer = new StringBuilderPrinter(b);
            printer.println(Integer.toString(1));
            printer.println(pkg.packageName);
            BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(destination));
            DataOutputStream out = new DataOutputStream(bout);
            bout.write(b.toString().getBytes());
            if (widgetData != null && widgetData.length > 0) {
                out.writeInt(BackupManagerService.BACKUP_WIDGET_METADATA_TOKEN);
                out.writeInt(widgetData.length);
                out.write(widgetData);
            }
            bout.flush();
            out.close();
            destination.setLastModified(0);
        }

        private void tearDown() {
            if (this.mPkg != null) {
                BackupManagerService.this.tearDownAgentAndKill(this.mPkg.applicationInfo);
            }
        }
    }

    class FullBackupEntry implements Comparable<FullBackupEntry> {
        long lastBackup;
        String packageName;

        FullBackupEntry(String pkg, long when) {
            this.packageName = pkg;
            this.lastBackup = when;
        }

        public int compareTo(FullBackupEntry other) {
            if (this.lastBackup < other.lastBackup) {
                return -1;
            }
            if (this.lastBackup > other.lastBackup) {
                return 1;
            }
            return 0;
        }
    }

    class FullBackupObbConnection implements ServiceConnection {
        volatile IObbBackupService mService = null;

        FullBackupObbConnection() {
        }

        public void establish() {
            BackupManagerService.this.mContext.bindServiceAsUser(new Intent().setComponent(new ComponentName(BackupManagerService.SHARED_BACKUP_AGENT_PACKAGE, "com.android.sharedstoragebackup.ObbBackupService")), this, 1, UserHandle.SYSTEM);
        }

        public void tearDown() {
            BackupManagerService.this.mContext.unbindService(this);
        }

        public boolean backupObbs(PackageInfo pkg, OutputStream out) {
            boolean success = false;
            waitForConnection();
            ParcelFileDescriptor[] parcelFileDescriptorArr = null;
            try {
                parcelFileDescriptorArr = ParcelFileDescriptor.createPipe();
                int token = BackupManagerService.this.generateToken();
                BackupManagerService.this.prepareOperationTimeout(token, BackupManagerService.TIMEOUT_FULL_BACKUP_INTERVAL, null);
                this.mService.backupObbs(pkg.packageName, parcelFileDescriptorArr[1], token, BackupManagerService.this.mBackupManagerBinder);
                BackupManagerService.this.routeSocketDataToOutput(parcelFileDescriptorArr[0], out);
                success = BackupManagerService.this.waitUntilOperationComplete(token);
                try {
                    out.flush();
                    if (parcelFileDescriptorArr != null) {
                        if (parcelFileDescriptorArr[0] != null) {
                            parcelFileDescriptorArr[0].close();
                        }
                        if (parcelFileDescriptorArr[1] != null) {
                            parcelFileDescriptorArr[1].close();
                        }
                    }
                } catch (IOException e) {
                    Slog.w(BackupManagerService.TAG, "I/O error closing down OBB backup", e);
                }
            } catch (Exception e2) {
                Slog.w(BackupManagerService.TAG, "Unable to back up OBBs for " + pkg, e2);
                try {
                    out.flush();
                    if (parcelFileDescriptorArr != null) {
                        if (parcelFileDescriptorArr[0] != null) {
                            parcelFileDescriptorArr[0].close();
                        }
                        if (parcelFileDescriptorArr[1] != null) {
                            parcelFileDescriptorArr[1].close();
                        }
                    }
                } catch (IOException e3) {
                    Slog.w(BackupManagerService.TAG, "I/O error closing down OBB backup", e3);
                }
            } catch (Throwable th) {
                try {
                    out.flush();
                    if (parcelFileDescriptorArr != null) {
                        if (parcelFileDescriptorArr[0] != null) {
                            parcelFileDescriptorArr[0].close();
                        }
                        if (parcelFileDescriptorArr[1] != null) {
                            parcelFileDescriptorArr[1].close();
                        }
                    }
                } catch (IOException e32) {
                    Slog.w(BackupManagerService.TAG, "I/O error closing down OBB backup", e32);
                }
            }
            return success;
        }

        public void restoreObbFile(String pkgName, ParcelFileDescriptor data, long fileSize, int type, String path, long mode, long mtime, int token, IBackupManager callbackBinder) {
            waitForConnection();
            try {
                this.mService.restoreObbFile(pkgName, data, fileSize, type, path, mode, mtime, token, callbackBinder);
            } catch (Exception e) {
                Slog.w(BackupManagerService.TAG, "Unable to restore OBBs for " + pkgName, e);
            }
        }

        private void waitForConnection() {
            synchronized (this) {
                while (this.mService == null) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (this) {
                this.mService = IObbBackupService.Stub.asInterface(service);
                notifyAll();
            }
        }

        public void onServiceDisconnected(ComponentName name) {
            synchronized (this) {
                this.mService = null;
                notifyAll();
            }
        }
    }

    class FullParams {
        public String curPassword;
        public String encryptPassword;
        public ParcelFileDescriptor fd;
        public final AtomicBoolean latch = new AtomicBoolean(false);
        public IFullBackupRestoreObserver observer;

        FullParams() {
        }
    }

    class FullBackupParams extends FullParams {
        public boolean allApps;
        public boolean doCompress;
        public boolean doWidgets;
        public boolean includeApks;
        public boolean includeObbs;
        public boolean includeShared;
        public boolean includeSystem;
        public String[] packages;

        FullBackupParams(ParcelFileDescriptor output, boolean saveApks, boolean saveObbs, boolean saveShared, boolean alsoWidgets, boolean doAllApps, boolean doSystem, boolean compress, String[] pkgList) {
            super();
            this.fd = output;
            this.includeApks = saveApks;
            this.includeObbs = saveObbs;
            this.includeShared = saveShared;
            this.doWidgets = alsoWidgets;
            this.allApps = doAllApps;
            this.includeSystem = doSystem;
            this.doCompress = compress;
            this.packages = pkgList;
        }
    }

    interface FullBackupPreflight {
        long getExpectedSizeOrErrorCode();

        int preflightFullBackup(PackageInfo packageInfo, IBackupAgent iBackupAgent);
    }

    abstract class FullBackupTask implements Runnable {
        IFullBackupRestoreObserver mObserver;

        FullBackupTask(IFullBackupRestoreObserver observer) {
            this.mObserver = observer;
        }

        final void sendStartBackup() {
            if (this.mObserver != null) {
                try {
                    this.mObserver.onStartBackup();
                } catch (RemoteException e) {
                    Slog.w(BackupManagerService.TAG, "full backup observer went away: startBackup");
                    this.mObserver = null;
                }
            }
        }

        final void sendOnBackupPackage(String name) {
            if (this.mObserver != null) {
                try {
                    this.mObserver.onBackupPackage(name);
                } catch (RemoteException e) {
                    Slog.w(BackupManagerService.TAG, "full backup observer went away: backupPackage");
                    this.mObserver = null;
                }
            }
        }

        final void sendEndBackup() {
            if (this.mObserver != null) {
                try {
                    this.mObserver.onEndBackup();
                } catch (RemoteException e) {
                    Slog.w(BackupManagerService.TAG, "full backup observer went away: endBackup");
                    this.mObserver = null;
                }
            }
        }
    }

    abstract class RestoreEngine {
        public static final int SUCCESS = 0;
        static final String TAG = "RestoreEngine";
        public static final int TARGET_FAILURE = -2;
        public static final int TRANSPORT_FAILURE = -3;
        private AtomicInteger mResult = new AtomicInteger(0);
        private AtomicBoolean mRunning = new AtomicBoolean(false);

        RestoreEngine() {
        }

        public boolean isRunning() {
            return this.mRunning.get();
        }

        public void setRunning(boolean stillRunning) {
            synchronized (this.mRunning) {
                this.mRunning.set(stillRunning);
                this.mRunning.notifyAll();
            }
        }

        public int waitForResult() {
            synchronized (this.mRunning) {
                while (isRunning()) {
                    try {
                        this.mRunning.wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
            return getResult();
        }

        public int getResult() {
            return this.mResult.get();
        }

        public void setResult(int result) {
            this.mResult.set(result);
        }
    }

    class FullRestoreEngine extends RestoreEngine {
        private static final /* synthetic */ int[] -com-android-server-backup-BackupManagerService$RestorePolicySwitchesValues = null;
        final /* synthetic */ int[] $SWITCH_TABLE$com$android$server$backup$BackupManagerService$RestorePolicy;
        IBackupAgent mAgent;
        String mAgentPackage;
        boolean mAllowApks;
        boolean mAllowObbs;
        byte[] mBuffer;
        long mBytes;
        final HashSet<String> mClearedPackages = new HashSet();
        final RestoreDeleteObserver mDeleteObserver = new RestoreDeleteObserver();
        final RestoreInstallObserver mInstallObserver = new RestoreInstallObserver();
        final HashMap<String, Signature[]> mManifestSignatures = new HashMap();
        BackupRestoreTask mMonitorTask;
        FullBackupObbConnection mObbConnection = null;
        IFullBackupRestoreObserver mObserver;
        PackageInfo mOnlyPackage;
        final HashMap<String, String> mPackageInstallers = new HashMap();
        final HashMap<String, RestorePolicy> mPackagePolicies = new HashMap();
        ParcelFileDescriptor[] mPipes = null;
        ApplicationInfo mTargetApp;
        byte[] mWidgetData = null;

        class RestoreDeleteObserver extends IPackageDeleteObserver.Stub {
            final AtomicBoolean mDone = new AtomicBoolean();
            int mResult;

            RestoreDeleteObserver() {
            }

            public void reset() {
                synchronized (this.mDone) {
                    this.mDone.set(false);
                }
            }

            public void waitForCompletion() {
                synchronized (this.mDone) {
                    while (!this.mDone.get()) {
                        try {
                            this.mDone.wait();
                        } catch (InterruptedException e) {
                        }
                    }
                }
            }

            public void packageDeleted(String packageName, int returnCode) throws RemoteException {
                synchronized (this.mDone) {
                    this.mResult = returnCode;
                    this.mDone.set(true);
                    this.mDone.notifyAll();
                }
            }
        }

        class RestoreFileRunnable implements Runnable {
            IBackupAgent mAgent;
            FileMetadata mInfo;
            ParcelFileDescriptor mSocket;
            int mToken;

            RestoreFileRunnable(IBackupAgent agent, FileMetadata info, ParcelFileDescriptor socket, int token) throws IOException {
                this.mAgent = agent;
                this.mInfo = info;
                this.mToken = token;
                this.mSocket = ParcelFileDescriptor.dup(socket.getFileDescriptor());
            }

            public void run() {
                try {
                    this.mAgent.doRestoreFile(this.mSocket, this.mInfo.size, this.mInfo.type, this.mInfo.domain, this.mInfo.path, this.mInfo.mode, this.mInfo.mtime, this.mToken, BackupManagerService.this.mBackupManagerBinder);
                } catch (RemoteException e) {
                }
            }
        }

        class RestoreInstallObserver extends PackageInstallObserver {
            final AtomicBoolean mDone = new AtomicBoolean();
            String mPackageName;
            int mResult;

            RestoreInstallObserver() {
            }

            public void reset() {
                synchronized (this.mDone) {
                    this.mDone.set(false);
                }
            }

            public void waitForCompletion() {
                synchronized (this.mDone) {
                    while (!this.mDone.get()) {
                        try {
                            this.mDone.wait();
                        } catch (InterruptedException e) {
                        }
                    }
                }
            }

            int getResult() {
                return this.mResult;
            }

            public void onPackageInstalled(String packageName, int returnCode, String msg, Bundle extras) {
                synchronized (this.mDone) {
                    this.mResult = returnCode;
                    this.mPackageName = packageName;
                    this.mDone.set(true);
                    this.mDone.notifyAll();
                }
            }
        }

        private static /* synthetic */ int[] -getcom-android-server-backup-BackupManagerService$RestorePolicySwitchesValues() {
            if (-com-android-server-backup-BackupManagerService$RestorePolicySwitchesValues != null) {
                return -com-android-server-backup-BackupManagerService$RestorePolicySwitchesValues;
            }
            int[] iArr = new int[RestorePolicy.values().length];
            try {
                iArr[RestorePolicy.ACCEPT.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                iArr[RestorePolicy.ACCEPT_IF_APK.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                iArr[RestorePolicy.IGNORE.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            -com-android-server-backup-BackupManagerService$RestorePolicySwitchesValues = iArr;
            return iArr;
        }

        public FullRestoreEngine(BackupRestoreTask monitorTask, IFullBackupRestoreObserver observer, PackageInfo onlyPackage, boolean allowApks, boolean allowObbs) {
            super();
            this.mMonitorTask = monitorTask;
            this.mObserver = observer;
            this.mOnlyPackage = onlyPackage;
            this.mAllowApks = allowApks;
            this.mAllowObbs = allowObbs;
            this.mBuffer = new byte[DumpState.DUMP_VERSION];
            this.mBytes = 0;
        }

        public IBackupAgent getAgent() {
            return this.mAgent;
        }

        public byte[] getWidgetData() {
            return this.mWidgetData;
        }

        public boolean restoreOneFile(InputStream instream, boolean mustKillAgent) {
            if (isRunning()) {
                boolean z;
                FileMetadata info = readTarHeaders(instream);
                if (info != null) {
                    String pkg = info.packageName;
                    if (!pkg.equals(this.mAgentPackage)) {
                        if (this.mOnlyPackage == null || pkg.equals(this.mOnlyPackage.packageName)) {
                            if (!this.mPackagePolicies.containsKey(pkg)) {
                                this.mPackagePolicies.put(pkg, RestorePolicy.IGNORE);
                            }
                            if (this.mAgent != null) {
                                if (BackupManagerService.DEBUG) {
                                    Slog.d("RestoreEngine", "Saw new package; finalizing old one");
                                }
                                tearDownPipes();
                                tearDownAgent(this.mTargetApp);
                                this.mTargetApp = null;
                                this.mAgentPackage = null;
                            }
                        } else {
                            Slog.w("RestoreEngine", "Expected data for " + this.mOnlyPackage + " but saw " + pkg);
                            setResult(-3);
                            setRunning(false);
                            return false;
                        }
                    }
                    if (info.path.equals(BackupManagerService.BACKUP_MANIFEST_FILENAME)) {
                        this.mPackagePolicies.put(pkg, readAppManifest(info, instream));
                        this.mPackageInstallers.put(pkg, info.installerPackageName);
                        skipTarPadding(info.size, instream);
                        sendOnRestorePackage(pkg);
                    } else if (info.path.equals(BackupManagerService.BACKUP_METADATA_FILENAME)) {
                        readMetadata(info, instream);
                        skipTarPadding(info.size, instream);
                    } else {
                        boolean okay = true;
                        switch (-getcom-android-server-backup-BackupManagerService$RestorePolicySwitchesValues()[((RestorePolicy) this.mPackagePolicies.get(pkg)).ordinal()]) {
                            case 1:
                                if (info.domain.equals("a")) {
                                    if (BackupManagerService.DEBUG) {
                                        Slog.d("RestoreEngine", "apk present but ACCEPT");
                                    }
                                    okay = false;
                                    break;
                                }
                                break;
                            case 2:
                                if (!info.domain.equals("a")) {
                                    this.mPackagePolicies.put(pkg, RestorePolicy.IGNORE);
                                    okay = false;
                                    break;
                                }
                                Object obj;
                                if (BackupManagerService.DEBUG) {
                                    Slog.d("RestoreEngine", "APK file; installing");
                                }
                                okay = installApk(info, (String) this.mPackageInstallers.get(pkg), instream);
                                HashMap hashMap = this.mPackagePolicies;
                                if (okay) {
                                    obj = RestorePolicy.ACCEPT;
                                } else {
                                    obj = RestorePolicy.IGNORE;
                                }
                                hashMap.put(pkg, obj);
                                skipTarPadding(info.size, instream);
                                return true;
                            case 3:
                                okay = false;
                                break;
                            default:
                                Slog.e("RestoreEngine", "Invalid policy from manifest");
                                okay = false;
                                this.mPackagePolicies.put(pkg, RestorePolicy.IGNORE);
                                break;
                        }
                        if (!isRestorableFile(info)) {
                            okay = false;
                        }
                        if (BackupManagerService.DEBUG && okay && this.mAgent != null) {
                            Slog.i("RestoreEngine", "Reusing existing agent instance");
                        }
                        if (okay && this.mAgent == null) {
                            if (BackupManagerService.DEBUG) {
                                Slog.d("RestoreEngine", "Need to launch agent for " + pkg);
                            }
                            try {
                                this.mTargetApp = BackupManagerService.this.mPackageManager.getApplicationInfo(pkg, 0);
                                if (!this.mClearedPackages.contains(pkg)) {
                                    if (this.mTargetApp.backupAgentName == null) {
                                        if (BackupManagerService.DEBUG) {
                                            Slog.d("RestoreEngine", "Clearing app data preparatory to full restore");
                                        }
                                        BackupManagerService.this.clearApplicationDataSynchronous(pkg);
                                    }
                                    this.mClearedPackages.add(pkg);
                                }
                                setUpPipes();
                                this.mAgent = BackupManagerService.this.bindToAgentSynchronous(this.mTargetApp, 3);
                                this.mAgentPackage = pkg;
                            } catch (IOException e) {
                            } catch (NameNotFoundException e2) {
                            }
                            try {
                                if (this.mAgent == null) {
                                    Slog.e("RestoreEngine", "Unable to create agent for " + pkg);
                                    okay = false;
                                    tearDownPipes();
                                    this.mPackagePolicies.put(pkg, RestorePolicy.IGNORE);
                                }
                            } catch (IOException e3) {
                                if (BackupManagerService.DEBUG) {
                                    Slog.w("RestoreEngine", "io exception on restore socket read: " + e3.getMessage());
                                }
                                setResult(-3);
                                info = null;
                            }
                        }
                        if (okay && !pkg.equals(this.mAgentPackage)) {
                            Slog.e("RestoreEngine", "Restoring data for " + pkg + " but agent is for " + this.mAgentPackage);
                            okay = false;
                        }
                        if (okay) {
                            boolean agentSuccess = true;
                            long toCopy = info.size;
                            int token = BackupManagerService.this.generateToken();
                            try {
                                BackupManagerService.this.prepareOperationTimeout(token, BackupManagerService.TIMEOUT_FULL_BACKUP_INTERVAL, this.mMonitorTask);
                                if (info.domain.equals("obb")) {
                                    if (BackupManagerService.DEBUG) {
                                        Slog.d("RestoreEngine", "Restoring OBB file for " + pkg + " : " + info.path);
                                    }
                                    this.mObbConnection.restoreObbFile(pkg, this.mPipes[0], info.size, info.type, info.path, info.mode, info.mtime, token, BackupManagerService.this.mBackupManagerBinder);
                                } else if (this.mTargetApp.processName.equals("system")) {
                                    Slog.d("RestoreEngine", "system process agent - spinning a thread");
                                    new Thread(new RestoreFileRunnable(this.mAgent, info, this.mPipes[0], token), "restore-sys-runner").start();
                                } else {
                                    this.mAgent.doRestoreFile(this.mPipes[0], info.size, info.type, info.domain, info.path, info.mode, info.mtime, token, BackupManagerService.this.mBackupManagerBinder);
                                }
                            } catch (IOException e4) {
                                Slog.d("RestoreEngine", "Couldn't establish restore");
                                agentSuccess = false;
                                okay = false;
                            } catch (RemoteException e5) {
                                Slog.e("RestoreEngine", "Agent crashed during full restore");
                                agentSuccess = false;
                                okay = false;
                            }
                            if (okay) {
                                boolean pipeOkay = true;
                                FileOutputStream fileOutputStream = new FileOutputStream(this.mPipes[1].getFileDescriptor());
                                while (toCopy > 0) {
                                    int nRead = instream.read(this.mBuffer, 0, toCopy > ((long) this.mBuffer.length) ? this.mBuffer.length : (int) toCopy);
                                    if (nRead >= 0) {
                                        this.mBytes += (long) nRead;
                                    }
                                    if (nRead <= 0) {
                                        skipTarPadding(info.size, instream);
                                        agentSuccess = BackupManagerService.this.waitUntilOperationComplete(token);
                                    } else {
                                        toCopy -= (long) nRead;
                                        if (pipeOkay) {
                                            try {
                                                fileOutputStream.write(this.mBuffer, 0, nRead);
                                            } catch (IOException e32) {
                                                Slog.e("RestoreEngine", "Failed to write to restore pipe: " + e32.getMessage());
                                                pipeOkay = false;
                                            }
                                        }
                                    }
                                }
                                skipTarPadding(info.size, instream);
                                agentSuccess = BackupManagerService.this.waitUntilOperationComplete(token);
                            }
                            if (!agentSuccess) {
                                Slog.w("RestoreEngine", "Agent failure; ending restore");
                                BackupManagerService.this.mBackupHandler.removeMessages(7);
                                tearDownPipes();
                                tearDownAgent(this.mTargetApp);
                                this.mAgent = null;
                                this.mPackagePolicies.put(pkg, RestorePolicy.IGNORE);
                                if (this.mOnlyPackage != null) {
                                    setResult(-2);
                                    setRunning(false);
                                    return false;
                                }
                            }
                        }
                        if (!okay) {
                            long bytesToConsume = (info.size + 511) & -512;
                            while (bytesToConsume > 0) {
                                long nRead2 = (long) instream.read(this.mBuffer, 0, bytesToConsume > ((long) this.mBuffer.length) ? this.mBuffer.length : (int) bytesToConsume);
                                if (nRead2 >= 0) {
                                    this.mBytes += nRead2;
                                }
                                if (nRead2 > 0) {
                                    bytesToConsume -= nRead2;
                                }
                            }
                        }
                    }
                }
                if (info == null) {
                    tearDownPipes();
                    setRunning(false);
                    if (mustKillAgent) {
                        tearDownAgent(this.mTargetApp);
                    }
                }
                if (info != null) {
                    z = true;
                } else {
                    z = false;
                }
                return z;
            }
            Slog.w("RestoreEngine", "Restore engine used after halting");
            return false;
        }

        void setUpPipes() throws IOException {
            this.mPipes = ParcelFileDescriptor.createPipe();
        }

        void tearDownPipes() {
            if (this.mPipes != null) {
                try {
                    this.mPipes[0].close();
                    this.mPipes[0] = null;
                    this.mPipes[1].close();
                    this.mPipes[1] = null;
                } catch (IOException e) {
                    Slog.w("RestoreEngine", "Couldn't close agent pipes", e);
                }
                this.mPipes = null;
            }
        }

        void tearDownAgent(ApplicationInfo app) {
            if (this.mAgent != null) {
                BackupManagerService.this.tearDownAgentAndKill(app);
                this.mAgent = null;
            }
        }

        void handleTimeout() {
            tearDownPipes();
            setResult(-2);
            setRunning(false);
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        boolean installApk(FileMetadata info, String installerPackage, InputStream instream) {
            boolean okay = true;
            if (BackupManagerService.DEBUG) {
                Slog.d("RestoreEngine", "Installing from backup: " + info.packageName);
            }
            File apkFile = new File(BackupManagerService.this.mDataDir, info.packageName);
            try {
                FileOutputStream apkStream = new FileOutputStream(apkFile);
                byte[] buffer = new byte[DumpState.DUMP_VERSION];
                long size = info.size;
                while (size > 0) {
                    long toRead;
                    if (((long) buffer.length) < size) {
                        toRead = (long) buffer.length;
                    } else {
                        toRead = size;
                    }
                    int didRead = instream.read(buffer, 0, (int) toRead);
                    if (didRead >= 0) {
                        this.mBytes += (long) didRead;
                    }
                    apkStream.write(buffer, 0, didRead);
                    size -= (long) didRead;
                }
                apkStream.close();
                apkFile.setReadable(true, false);
                Uri packageUri = Uri.fromFile(apkFile);
                this.mInstallObserver.reset();
                BackupManagerService.this.mPackageManager.installPackage(packageUri, this.mInstallObserver, 34, installerPackage);
                this.mInstallObserver.waitForCompletion();
                if (this.mInstallObserver.getResult() != 1) {
                    if (this.mPackagePolicies.get(info.packageName) != RestorePolicy.ACCEPT) {
                        okay = false;
                    }
                } else {
                    boolean uninstall = false;
                    if (this.mInstallObserver.mPackageName.equals(info.packageName)) {
                        try {
                            PackageInfo pkg = BackupManagerService.this.mPackageManager.getPackageInfo(info.packageName, 64);
                            if ((pkg.applicationInfo.flags & DumpState.DUMP_VERSION) == 0) {
                                Slog.w("RestoreEngine", "Restore stream contains apk of package " + info.packageName + " but it disallows backup/restore");
                                okay = false;
                            } else if (!BackupManagerService.signaturesMatch((Signature[]) this.mManifestSignatures.get(info.packageName), pkg)) {
                                Slog.w("RestoreEngine", "Installed app " + info.packageName + " signatures do not match restore manifest");
                                okay = false;
                                uninstall = true;
                            } else if (pkg.applicationInfo.uid < 10000 && pkg.applicationInfo.backupAgentName == null) {
                                Slog.w("RestoreEngine", "Installed app " + info.packageName + " has restricted uid and no agent");
                                okay = false;
                            }
                        } catch (NameNotFoundException e) {
                            Slog.w("RestoreEngine", "Install of package " + info.packageName + " succeeded but now not found");
                            okay = false;
                        }
                    } else {
                        Slog.w("RestoreEngine", "Restore stream claimed to include apk for " + info.packageName + " but apk was really " + this.mInstallObserver.mPackageName);
                        okay = false;
                        uninstall = true;
                    }
                    if (uninstall) {
                        this.mDeleteObserver.reset();
                        BackupManagerService.this.mPackageManager.deletePackage(this.mInstallObserver.mPackageName, this.mDeleteObserver, 0);
                        this.mDeleteObserver.waitForCompletion();
                    }
                }
                apkFile.delete();
                return okay;
            } catch (IOException e2) {
                Slog.e("RestoreEngine", "Unable to transcribe restored apk for install");
                return false;
            } catch (Throwable th) {
                apkFile.delete();
            }
        }

        void skipTarPadding(long size, InputStream instream) throws IOException {
            long partial = (size + 512) % 512;
            if (partial > 0) {
                int needed = 512 - ((int) partial);
                if (readExactly(instream, new byte[needed], 0, needed) == needed) {
                    this.mBytes += (long) needed;
                    return;
                }
                throw new IOException("Unexpected EOF in padding");
            }
        }

        void readMetadata(FileMetadata info, InputStream instream) throws IOException {
            if (info.size > 65536) {
                throw new IOException("Metadata too big; corrupt? size=" + info.size);
            }
            byte[] buffer = new byte[((int) info.size)];
            if (((long) readExactly(instream, buffer, 0, (int) info.size)) == info.size) {
                this.mBytes += info.size;
                String[] str = new String[1];
                int offset = extractLine(buffer, 0, str);
                int version = Integer.parseInt(str[0]);
                if (version == 1) {
                    offset = extractLine(buffer, offset, str);
                    String pkg = str[0];
                    if (info.packageName.equals(pkg)) {
                        ByteArrayInputStream bin = new ByteArrayInputStream(buffer, offset, buffer.length - offset);
                        DataInputStream in = new DataInputStream(bin);
                        while (bin.available() > 0) {
                            int token = in.readInt();
                            int size = in.readInt();
                            if (size <= DumpState.DUMP_INSTALLS) {
                                switch (token) {
                                    case BackupManagerService.BACKUP_WIDGET_METADATA_TOKEN /*33549569*/:
                                        this.mWidgetData = new byte[size];
                                        in.read(this.mWidgetData);
                                        break;
                                    default:
                                        if (BackupManagerService.DEBUG) {
                                            Slog.i("RestoreEngine", "Ignoring metadata blob " + Integer.toHexString(token) + " for " + info.packageName);
                                        }
                                        in.skipBytes(size);
                                        break;
                                }
                            }
                            throw new IOException("Datum " + Integer.toHexString(token) + " too big; corrupt? size=" + info.size);
                        }
                        return;
                    }
                    Slog.w("RestoreEngine", "Metadata mismatch: package " + info.packageName + " but widget data for " + pkg);
                    return;
                }
                Slog.w("RestoreEngine", "Unsupported metadata version " + version);
                return;
            }
            throw new IOException("Unexpected EOF in widget data");
        }

        RestorePolicy readAppManifest(FileMetadata info, InputStream instream) throws IOException {
            if (info.size > 65536) {
                throw new IOException("Restore manifest too big; corrupt? size=" + info.size);
            }
            byte[] buffer = new byte[((int) info.size)];
            if (((long) readExactly(instream, buffer, 0, (int) info.size)) == info.size) {
                this.mBytes += info.size;
                RestorePolicy policy = RestorePolicy.IGNORE;
                String[] str = new String[1];
                try {
                    int offset = extractLine(buffer, 0, str);
                    int version = Integer.parseInt(str[0]);
                    if (version == 1) {
                        offset = extractLine(buffer, offset, str);
                        String manifestPackage = str[0];
                        if (manifestPackage.equals(info.packageName)) {
                            offset = extractLine(buffer, offset, str);
                            version = Integer.parseInt(str[0]);
                            offset = extractLine(buffer, offset, str);
                            Integer.parseInt(str[0]);
                            offset = extractLine(buffer, offset, str);
                            info.installerPackageName = str[0].length() > 0 ? str[0] : null;
                            offset = extractLine(buffer, offset, str);
                            boolean hasApk = str[0].equals("1");
                            offset = extractLine(buffer, offset, str);
                            int numSigs = Integer.parseInt(str[0]);
                            if (numSigs > 0) {
                                Object sigs = new Signature[numSigs];
                                for (int i = 0; i < numSigs; i++) {
                                    offset = extractLine(buffer, offset, str);
                                    sigs[i] = new Signature(str[0]);
                                }
                                this.mManifestSignatures.put(info.packageName, sigs);
                                try {
                                    PackageInfo pkgInfo = BackupManagerService.this.mPackageManager.getPackageInfo(info.packageName, 64);
                                    if ((DumpState.DUMP_VERSION & pkgInfo.applicationInfo.flags) == 0) {
                                        if (BackupManagerService.DEBUG) {
                                            Slog.i("RestoreEngine", "Restore manifest from " + info.packageName + " but allowBackup=false");
                                        }
                                        Slog.i("RestoreEngine", "Cannot restore package " + info.packageName + " without the matching .apk");
                                    } else if (pkgInfo.applicationInfo.uid >= 10000 || pkgInfo.applicationInfo.backupAgentName != null) {
                                        if (!BackupManagerService.signaturesMatch(sigs, pkgInfo)) {
                                            Slog.w("RestoreEngine", "Restore manifest signatures do not match installed application for " + info.packageName);
                                        } else if (pkgInfo.versionCode >= version) {
                                            Slog.i("RestoreEngine", "Sig + version match; taking data");
                                            policy = RestorePolicy.ACCEPT;
                                        } else if (this.mAllowApks) {
                                            Slog.i("RestoreEngine", "Data version " + version + " is newer than installed version " + pkgInfo.versionCode + " - requiring apk");
                                            policy = RestorePolicy.ACCEPT_IF_APK;
                                        } else {
                                            Slog.i("RestoreEngine", "Data requires newer version " + version + "; ignoring");
                                            policy = RestorePolicy.IGNORE;
                                        }
                                        if (policy == RestorePolicy.ACCEPT_IF_APK && !hasApk) {
                                            Slog.i("RestoreEngine", "Cannot restore package " + info.packageName + " without the matching .apk");
                                        }
                                    } else {
                                        Slog.w("RestoreEngine", "Package " + info.packageName + " is system level with no agent");
                                        Slog.i("RestoreEngine", "Cannot restore package " + info.packageName + " without the matching .apk");
                                    }
                                } catch (NameNotFoundException e) {
                                    if (this.mAllowApks) {
                                        if (BackupManagerService.DEBUG) {
                                            Slog.i("RestoreEngine", "Package " + info.packageName + " not installed; requiring apk in dataset");
                                        }
                                        policy = RestorePolicy.ACCEPT_IF_APK;
                                    } else {
                                        policy = RestorePolicy.IGNORE;
                                    }
                                }
                            } else {
                                Slog.i("RestoreEngine", "Missing signature on backed-up package " + info.packageName);
                            }
                        } else {
                            Slog.i("RestoreEngine", "Expected package " + info.packageName + " but restore manifest claims " + manifestPackage);
                        }
                    } else {
                        Slog.i("RestoreEngine", "Unknown restore manifest version " + version + " for package " + info.packageName);
                    }
                } catch (NumberFormatException e2) {
                    Slog.w("RestoreEngine", "Corrupt restore manifest for package " + info.packageName);
                } catch (IllegalArgumentException e3) {
                    Slog.w("RestoreEngine", e3.getMessage());
                }
                return policy;
            }
            throw new IOException("Unexpected EOF in manifest");
        }

        int extractLine(byte[] buffer, int offset, String[] outStr) throws IOException {
            int end = buffer.length;
            if (offset >= end) {
                throw new IOException("Incomplete data");
            }
            int pos = offset;
            while (pos < end && buffer[pos] != (byte) 10) {
                pos++;
            }
            outStr[0] = new String(buffer, offset, pos - offset);
            return pos + 1;
        }

        void dumpFileMetadata(FileMetadata info) {
        }

        FileMetadata readTarHeaders(InputStream instream) throws IOException {
            IOException e;
            byte[] block = new byte[512];
            FileMetadata fileMetadata = null;
            if (readTarHeader(instream, block)) {
                try {
                    FileMetadata info = new FileMetadata();
                    try {
                        info.size = extractRadix(block, 124, 12, 8);
                        info.mtime = extractRadix(block, 136, 12, 8);
                        info.mode = extractRadix(block, 100, 8, 8);
                        info.path = extractString(block, 345, 155);
                        String path = extractString(block, 0, 100);
                        if (path.length() > 0) {
                            if (info.path.length() > 0) {
                                info.path += '/';
                            }
                            info.path += path;
                        }
                        int typeChar = block[156];
                        if (typeChar == 120) {
                            boolean gotHeader = readPaxExtendedHeader(instream, info);
                            if (gotHeader) {
                                gotHeader = readTarHeader(instream, block);
                            }
                            if (gotHeader) {
                                typeChar = block[156];
                            } else {
                                throw new IOException("Bad or missing pax header");
                            }
                        }
                        switch (typeChar) {
                            case 0:
                                return null;
                            case 48:
                                info.type = 1;
                                break;
                            case 53:
                                info.type = 2;
                                if (info.size != 0) {
                                    Slog.w("RestoreEngine", "Directory entry with nonzero size in header");
                                    info.size = 0;
                                    break;
                                }
                                break;
                            default:
                                Slog.e("RestoreEngine", "Unknown tar entity type: " + typeChar);
                                throw new IOException("Unknown entity type " + typeChar);
                        }
                        if ("shared/".regionMatches(0, info.path, 0, "shared/".length())) {
                            info.path = info.path.substring("shared/".length());
                            info.packageName = BackupManagerService.SHARED_BACKUP_AGENT_PACKAGE;
                            info.domain = "shared";
                            if (BackupManagerService.DEBUG) {
                                Slog.i("RestoreEngine", "File in shared storage: " + info.path);
                            }
                        } else if ("apps/".regionMatches(0, info.path, 0, "apps/".length())) {
                            info.path = info.path.substring("apps/".length());
                            int slash = info.path.indexOf(47);
                            if (slash < 0) {
                                throw new IOException("Illegal semantic path in " + info.path);
                            }
                            info.packageName = info.path.substring(0, slash);
                            info.path = info.path.substring(slash + 1);
                            if (!(info.path.equals(BackupManagerService.BACKUP_MANIFEST_FILENAME) || info.path.equals(BackupManagerService.BACKUP_METADATA_FILENAME))) {
                                slash = info.path.indexOf(47);
                                if (slash < 0) {
                                    throw new IOException("Illegal semantic path in non-manifest " + info.path);
                                }
                                info.domain = info.path.substring(0, slash);
                                info.path = info.path.substring(slash + 1);
                            }
                        }
                        fileMetadata = info;
                    } catch (IOException e2) {
                        e = e2;
                        fileMetadata = info;
                        if (BackupManagerService.DEBUG) {
                            Slog.e("RestoreEngine", "Parse error in header: " + e.getMessage());
                        }
                        throw e;
                    }
                } catch (IOException e3) {
                    e = e3;
                    if (BackupManagerService.DEBUG) {
                        Slog.e("RestoreEngine", "Parse error in header: " + e.getMessage());
                    }
                    throw e;
                }
            }
            return fileMetadata;
        }

        private boolean isRestorableFile(FileMetadata info) {
            if ("c".equals(info.domain)) {
                return false;
            }
            if (("r".equals(info.domain) && info.path.startsWith("no_backup/")) || info.path.contains("..") || info.path.contains("//")) {
                return false;
            }
            return true;
        }

        private void HEXLOG(byte[] block) {
            int offset = 0;
            int todo = block.length;
            StringBuilder buf = new StringBuilder(64);
            while (todo > 0) {
                buf.append(String.format("%04x   ", new Object[]{Integer.valueOf(offset)}));
                int numThisLine = todo > 16 ? 16 : todo;
                for (int i = 0; i < numThisLine; i++) {
                    buf.append(String.format("%02x ", new Object[]{Byte.valueOf(block[offset + i])}));
                }
                Slog.i("hexdump", buf.toString());
                buf.setLength(0);
                todo -= numThisLine;
                offset += numThisLine;
            }
        }

        int readExactly(InputStream in, byte[] buffer, int offset, int size) throws IOException {
            if (size <= 0) {
                throw new IllegalArgumentException("size must be > 0");
            }
            int soFar = 0;
            while (soFar < size) {
                int nRead = in.read(buffer, offset + soFar, size - soFar);
                if (nRead <= 0) {
                    break;
                }
                soFar += nRead;
            }
            return soFar;
        }

        boolean readTarHeader(InputStream instream, byte[] block) throws IOException {
            int got = readExactly(instream, block, 0, 512);
            if (got == 0) {
                return false;
            }
            if (got < 512) {
                throw new IOException("Unable to read full block header");
            }
            this.mBytes += 512;
            return true;
        }

        boolean readPaxExtendedHeader(InputStream instream, FileMetadata info) throws IOException {
            if (info.size > 32768) {
                Slog.w("RestoreEngine", "Suspiciously large pax header size " + info.size + " - aborting");
                throw new IOException("Sanity failure: pax header size " + info.size);
            }
            byte[] data = new byte[(((int) ((info.size + 511) >> 9)) * 512)];
            if (readExactly(instream, data, 0, data.length) < data.length) {
                throw new IOException("Unable to read full pax header");
            }
            this.mBytes += (long) data.length;
            int contentSize = (int) info.size;
            int offset = 0;
            do {
                int eol = offset + 1;
                while (eol < contentSize && data[eol] != (byte) 32) {
                    eol++;
                }
                if (eol >= contentSize) {
                    throw new IOException("Invalid pax data");
                }
                int linelen = (int) extractRadix(data, offset, eol - offset, 10);
                int key = eol + 1;
                eol = (offset + linelen) - 1;
                int value = key + 1;
                while (data[value] != (byte) 61 && value <= eol) {
                    value++;
                }
                if (value > eol) {
                    throw new IOException("Invalid pax declaration");
                }
                String keyStr = new String(data, key, value - key, "UTF-8");
                String valStr = new String(data, value + 1, (eol - value) - 1, "UTF-8");
                if ("path".equals(keyStr)) {
                    info.path = valStr;
                } else if ("size".equals(keyStr)) {
                    info.size = Long.parseLong(valStr);
                } else if (BackupManagerService.DEBUG) {
                    Slog.i("RestoreEngine", "Unhandled pax key: " + key);
                }
                offset += linelen;
            } while (offset < contentSize);
            return true;
        }

        long extractRadix(byte[] data, int offset, int maxChars, int radix) throws IOException {
            long value = 0;
            int end = offset + maxChars;
            int i = offset;
            while (i < end) {
                byte b = data[i];
                if (b == (byte) 0 || b == (byte) 32) {
                    break;
                } else if (b < (byte) 48 || b > (radix + 48) - 1) {
                    throw new IOException("Invalid number in header: '" + ((char) b) + "' for radix " + radix);
                } else {
                    value = (((long) radix) * value) + ((long) (b - 48));
                    i++;
                }
            }
            return value;
        }

        String extractString(byte[] data, int offset, int maxChars) throws IOException {
            int end = offset + maxChars;
            int eos = offset;
            while (eos < end && data[eos] != (byte) 0) {
                eos++;
            }
            return new String(data, offset, eos - offset, "US-ASCII");
        }

        void sendStartRestore() {
            if (this.mObserver != null) {
                try {
                    this.mObserver.onStartRestore();
                } catch (RemoteException e) {
                    Slog.w("RestoreEngine", "full restore observer went away: startRestore");
                    this.mObserver = null;
                }
            }
        }

        void sendOnRestorePackage(String name) {
            if (this.mObserver != null) {
                try {
                    this.mObserver.onRestorePackage(name);
                } catch (RemoteException e) {
                    Slog.w("RestoreEngine", "full restore observer went away: restorePackage");
                    this.mObserver = null;
                }
            }
        }

        void sendEndRestore() {
            if (this.mObserver != null) {
                try {
                    this.mObserver.onEndRestore();
                } catch (RemoteException e) {
                    Slog.w("RestoreEngine", "full restore observer went away: endRestore");
                    this.mObserver = null;
                }
            }
        }
    }

    class FullRestoreParams extends FullParams {
        FullRestoreParams(ParcelFileDescriptor input) {
            super();
            this.fd = input;
        }
    }

    public static final class Lifecycle extends SystemService {
        public Lifecycle(Context context) {
            super(context);
            BackupManagerService.sInstance = new Trampoline(context);
        }

        public void onStart() {
            publishBinderService("backup", BackupManagerService.sInstance);
        }

        public void onUnlockUser(int userId) {
            boolean z = true;
            if (userId == 0) {
                BackupManagerService.sInstance.initialize(userId);
                if (!BackupManagerService.backupSettingMigrated(userId)) {
                    if (BackupManagerService.DEBUG) {
                        Slog.i(BackupManagerService.TAG, "Backup enable apparently not migrated");
                    }
                    ContentResolver r = BackupManagerService.sInstance.mContext.getContentResolver();
                    int enableState = Secure.getIntForUser(r, BackupManagerService.BACKUP_ENABLE_FILE, -1, userId);
                    if (enableState >= 0) {
                        if (BackupManagerService.DEBUG) {
                            boolean z2;
                            String str = BackupManagerService.TAG;
                            StringBuilder append = new StringBuilder().append("Migrating enable state ");
                            if (enableState != 0) {
                                z2 = true;
                            } else {
                                z2 = false;
                            }
                            Slog.i(str, append.append(z2).toString());
                        }
                        if (enableState == 0) {
                            z = false;
                        }
                        BackupManagerService.writeBackupEnableState(z, userId);
                        Secure.putStringForUser(r, BackupManagerService.BACKUP_ENABLE_FILE, null, userId);
                    } else if (BackupManagerService.DEBUG) {
                        Slog.i(BackupManagerService.TAG, "Backup not yet configured; retaining null enable state");
                    }
                }
                try {
                    BackupManagerService.sInstance.setBackupEnabled(BackupManagerService.readBackupEnableState(userId));
                } catch (RemoteException e) {
                }
            }
        }
    }

    class Operation {
        public BackupRestoreTask callback;
        public int state;

        Operation(int initialState, BackupRestoreTask callbackObj) {
            this.state = initialState;
            this.callback = callbackObj;
        }
    }

    class PerformAdbBackupTask extends FullBackupTask implements BackupRestoreTask {
        boolean mAllApps;
        FullBackupEngine mBackupEngine;
        boolean mCompress;
        String mCurrentPassword;
        PackageInfo mCurrentTarget;
        DeflaterOutputStream mDeflater;
        boolean mDoWidgets;
        String mEncryptPassword;
        boolean mIncludeApks;
        boolean mIncludeObbs;
        boolean mIncludeShared;
        boolean mIncludeSystem;
        final AtomicBoolean mLatch;
        ParcelFileDescriptor mOutputFile;
        ArrayList<String> mPackages;

        PerformAdbBackupTask(ParcelFileDescriptor fd, IFullBackupRestoreObserver observer, boolean includeApks, boolean includeObbs, boolean includeShared, boolean doWidgets, String curPassword, String encryptPassword, boolean doAllApps, boolean doSystem, boolean doCompress, String[] packages, AtomicBoolean latch) {
            ArrayList arrayList;
            super(observer);
            this.mLatch = latch;
            this.mOutputFile = fd;
            this.mIncludeApks = includeApks;
            this.mIncludeObbs = includeObbs;
            this.mIncludeShared = includeShared;
            this.mDoWidgets = doWidgets;
            this.mAllApps = doAllApps;
            this.mIncludeSystem = doSystem;
            if (packages == null) {
                arrayList = new ArrayList();
            } else {
                arrayList = new ArrayList(Arrays.asList(packages));
            }
            this.mPackages = arrayList;
            this.mCurrentPassword = curPassword;
            if (encryptPassword == null || "".equals(encryptPassword)) {
                this.mEncryptPassword = curPassword;
            } else {
                this.mEncryptPassword = encryptPassword;
            }
            this.mCompress = doCompress;
        }

        void addPackagesToSet(TreeMap<String, PackageInfo> set, List<String> pkgNames) {
            for (String pkgName : pkgNames) {
                if (!set.containsKey(pkgName)) {
                    try {
                        set.put(pkgName, BackupManagerService.this.mPackageManager.getPackageInfo(pkgName, 64));
                    } catch (NameNotFoundException e) {
                        Slog.w(BackupManagerService.TAG, "Unknown package " + pkgName + ", skipping");
                    }
                }
            }
        }

        private OutputStream emitAesBackupHeader(StringBuilder headerbuf, OutputStream ofstream) throws Exception {
            byte[] newUserSalt = BackupManagerService.this.randomBytes(512);
            SecretKey userKey = BackupManagerService.this.buildPasswordKey(BackupManagerService.PBKDF_CURRENT, this.mEncryptPassword, newUserSalt, 10000);
            byte[] masterPw = new byte[32];
            BackupManagerService.this.mRng.nextBytes(masterPw);
            byte[] checksumSalt = BackupManagerService.this.randomBytes(512);
            Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec masterKeySpec = new SecretKeySpec(masterPw, "AES");
            c.init(1, masterKeySpec);
            OutputStream finalOutput = new CipherOutputStream(ofstream, c);
            headerbuf.append(BackupManagerService.ENCRYPTION_ALGORITHM_NAME);
            headerbuf.append('\n');
            headerbuf.append(BackupManagerService.this.byteArrayToHex(newUserSalt));
            headerbuf.append('\n');
            headerbuf.append(BackupManagerService.this.byteArrayToHex(checksumSalt));
            headerbuf.append('\n');
            headerbuf.append(10000);
            headerbuf.append('\n');
            Cipher mkC = Cipher.getInstance("AES/CBC/PKCS5Padding");
            mkC.init(1, userKey);
            headerbuf.append(BackupManagerService.this.byteArrayToHex(mkC.getIV()));
            headerbuf.append('\n');
            byte[] IV = c.getIV();
            byte[] mk = masterKeySpec.getEncoded();
            byte[] checksum = BackupManagerService.this.makeKeyChecksum(BackupManagerService.PBKDF_CURRENT, masterKeySpec.getEncoded(), checksumSalt, 10000);
            ByteArrayOutputStream blob = new ByteArrayOutputStream(((IV.length + mk.length) + checksum.length) + 3);
            DataOutputStream dataOutputStream = new DataOutputStream(blob);
            dataOutputStream.writeByte(IV.length);
            dataOutputStream.write(IV);
            dataOutputStream.writeByte(mk.length);
            dataOutputStream.write(mk);
            dataOutputStream.writeByte(checksum.length);
            dataOutputStream.write(checksum);
            dataOutputStream.flush();
            headerbuf.append(BackupManagerService.this.byteArrayToHex(mkC.doFinal(blob.toByteArray())));
            headerbuf.append('\n');
            return finalOutput;
        }

        private void finalizeBackup(OutputStream out) {
            try {
                out.write(new byte[1024]);
            } catch (IOException e) {
                Slog.w(BackupManagerService.TAG, "Error attempting to finalize backup stream");
            }
        }

        public void run() {
            int i;
            PackageInfo pkg;
            Throwable e;
            Slog.i(BackupManagerService.TAG, "--- Performing full-dataset adb backup ---");
            TreeMap<String, PackageInfo> packagesToBackup = new TreeMap();
            FullBackupObbConnection fullBackupObbConnection = new FullBackupObbConnection();
            fullBackupObbConnection.establish();
            sendStartBackup();
            if (this.mAllApps) {
                List<PackageInfo> allPackages = BackupManagerService.this.mPackageManager.getInstalledPackages(64);
                for (i = 0; i < allPackages.size(); i++) {
                    pkg = (PackageInfo) allPackages.get(i);
                    if (this.mIncludeSystem || (pkg.applicationInfo.flags & 1) == 0) {
                        packagesToBackup.put(pkg.packageName, pkg);
                    }
                }
            }
            if (this.mDoWidgets) {
                List<String> pkgs = AppWidgetBackupBridge.getWidgetParticipants(0);
                if (pkgs != null) {
                    addPackagesToSet(packagesToBackup, pkgs);
                }
            }
            if (this.mPackages != null) {
                addPackagesToSet(packagesToBackup, this.mPackages);
            }
            Iterator<Entry<String, PackageInfo>> iter = packagesToBackup.entrySet().iterator();
            while (iter.hasNext()) {
                pkg = (PackageInfo) ((Entry) iter.next()).getValue();
                if (!BackupManagerService.appIsEligibleForBackup(pkg.applicationInfo) || BackupManagerService.appIsStopped(pkg.applicationInfo) || BackupManagerService.appIsKeyValueOnly(pkg)) {
                    iter.remove();
                }
            }
            ArrayList<PackageInfo> backupQueue = new ArrayList(packagesToBackup.values());
            OutputStream fileOutputStream = new FileOutputStream(this.mOutputFile.getFileDescriptor());
            OutputStream outputStream = null;
            try {
                boolean encrypting = this.mEncryptPassword != null && this.mEncryptPassword.length() > 0;
                if (!BackupManagerService.this.deviceIsEncrypted() || encrypting) {
                    OutputStream finalOutput = fileOutputStream;
                    if (BackupManagerService.this.backupPasswordMatches(this.mCurrentPassword)) {
                        OutputStream finalOutput2;
                        StringBuilder stringBuilder = new StringBuilder(1024);
                        stringBuilder.append(BackupManagerService.BACKUP_FILE_HEADER_MAGIC);
                        stringBuilder.append(4);
                        stringBuilder.append(this.mCompress ? "\n1\n" : "\n0\n");
                        if (encrypting) {
                            try {
                                finalOutput2 = emitAesBackupHeader(stringBuilder, fileOutputStream);
                            } catch (Exception e2) {
                                e = e2;
                                Slog.e(BackupManagerService.TAG, "Unable to emit archive header", e);
                                try {
                                    this.mOutputFile.close();
                                } catch (IOException e3) {
                                }
                                synchronized (BackupManagerService.this.mCurrentOpLock) {
                                    BackupManagerService.this.mCurrentOperations.clear();
                                }
                                synchronized (this.mLatch) {
                                    this.mLatch.set(true);
                                    this.mLatch.notifyAll();
                                }
                                sendEndBackup();
                                fullBackupObbConnection.tearDown();
                                if (BackupManagerService.DEBUG) {
                                    Slog.d(BackupManagerService.TAG, "Full backup pass complete.");
                                }
                                BackupManagerService.this.mWakelock.release();
                                return;
                            } catch (RemoteException e4) {
                                Slog.e(BackupManagerService.TAG, "App died during full backup");
                                if (outputStream != null) {
                                    try {
                                        outputStream.close();
                                    } catch (IOException e5) {
                                        synchronized (BackupManagerService.this.mCurrentOpLock) {
                                            BackupManagerService.this.mCurrentOperations.clear();
                                            synchronized (this.mLatch) {
                                                this.mLatch.set(true);
                                                this.mLatch.notifyAll();
                                                sendEndBackup();
                                                fullBackupObbConnection.tearDown();
                                                if (BackupManagerService.DEBUG) {
                                                    Slog.d(BackupManagerService.TAG, "Full backup pass complete.");
                                                }
                                                BackupManagerService.this.mWakelock.release();
                                                return;
                                            }
                                        }
                                    }
                                }
                                this.mOutputFile.close();
                                synchronized (BackupManagerService.this.mCurrentOpLock) {
                                    BackupManagerService.this.mCurrentOperations.clear();
                                    synchronized (this.mLatch) {
                                        this.mLatch.set(true);
                                        this.mLatch.notifyAll();
                                        sendEndBackup();
                                        fullBackupObbConnection.tearDown();
                                        if (BackupManagerService.DEBUG) {
                                            Slog.d(BackupManagerService.TAG, "Full backup pass complete.");
                                        }
                                        BackupManagerService.this.mWakelock.release();
                                    }
                                }
                            }
                        } else {
                            stringBuilder.append("none\n");
                            finalOutput2 = finalOutput;
                        }
                        try {
                            fileOutputStream.write(stringBuilder.toString().getBytes("UTF-8"));
                            if (this.mCompress) {
                                fileOutputStream = new DeflaterOutputStream(finalOutput2, new Deflater(9), true);
                            } else {
                                finalOutput = finalOutput2;
                            }
                            outputStream = finalOutput;
                            if (this.mIncludeShared) {
                                try {
                                    backupQueue.add(BackupManagerService.this.mPackageManager.getPackageInfo(BackupManagerService.SHARED_BACKUP_AGENT_PACKAGE, 0));
                                } catch (NameNotFoundException e6) {
                                    Slog.e(BackupManagerService.TAG, "Unable to find shared-storage backup handler");
                                }
                            }
                            int N = backupQueue.size();
                            i = 0;
                            while (i < N) {
                                pkg = (PackageInfo) backupQueue.get(i);
                                boolean isSharedStorage = pkg.packageName.equals(BackupManagerService.SHARED_BACKUP_AGENT_PACKAGE);
                                this.mBackupEngine = new FullBackupEngine(outputStream, null, pkg, this.mIncludeApks, this);
                                sendOnBackupPackage(isSharedStorage ? "Shared storage" : pkg.packageName);
                                this.mCurrentTarget = pkg;
                                this.mBackupEngine.backupOnePackage();
                                if (!this.mIncludeObbs || fullBackupObbConnection.backupObbs(pkg, outputStream)) {
                                    i++;
                                } else {
                                    throw new RuntimeException("Failure writing OBB stack for " + pkg);
                                }
                            }
                            finalizeBackup(outputStream);
                            if (outputStream != null) {
                                try {
                                    outputStream.close();
                                } catch (IOException e7) {
                                }
                            }
                            this.mOutputFile.close();
                            synchronized (BackupManagerService.this.mCurrentOpLock) {
                                BackupManagerService.this.mCurrentOperations.clear();
                            }
                            synchronized (this.mLatch) {
                                this.mLatch.set(true);
                                this.mLatch.notifyAll();
                            }
                            sendEndBackup();
                            fullBackupObbConnection.tearDown();
                            if (BackupManagerService.DEBUG) {
                                Slog.d(BackupManagerService.TAG, "Full backup pass complete.");
                            }
                            BackupManagerService.this.mWakelock.release();
                        } catch (Exception e8) {
                            e = e8;
                            finalOutput = finalOutput2;
                            Slog.e(BackupManagerService.TAG, "Unable to emit archive header", e);
                            this.mOutputFile.close();
                            synchronized (BackupManagerService.this.mCurrentOpLock) {
                                BackupManagerService.this.mCurrentOperations.clear();
                            }
                            synchronized (this.mLatch) {
                                this.mLatch.set(true);
                                this.mLatch.notifyAll();
                            }
                            sendEndBackup();
                            fullBackupObbConnection.tearDown();
                            if (BackupManagerService.DEBUG) {
                                Slog.d(BackupManagerService.TAG, "Full backup pass complete.");
                            }
                            BackupManagerService.this.mWakelock.release();
                            return;
                        } catch (RemoteException e42) {
                            Slog.e(BackupManagerService.TAG, "App died during full backup");
                            if (outputStream != null) {
                                try {
                                    outputStream.close();
                                } catch (IOException e52) {
                                    synchronized (BackupManagerService.this.mCurrentOpLock) {
                                        BackupManagerService.this.mCurrentOperations.clear();
                                        synchronized (this.mLatch) {
                                            this.mLatch.set(true);
                                            this.mLatch.notifyAll();
                                            sendEndBackup();
                                            fullBackupObbConnection.tearDown();
                                            if (BackupManagerService.DEBUG) {
                                                Slog.d(BackupManagerService.TAG, "Full backup pass complete.");
                                            }
                                            BackupManagerService.this.mWakelock.release();
                                            return;
                                        }
                                    }
                                }
                            }
                            this.mOutputFile.close();
                            synchronized (BackupManagerService.this.mCurrentOpLock) {
                                BackupManagerService.this.mCurrentOperations.clear();
                                synchronized (this.mLatch) {
                                    this.mLatch.set(true);
                                    this.mLatch.notifyAll();
                                    sendEndBackup();
                                    fullBackupObbConnection.tearDown();
                                    if (BackupManagerService.DEBUG) {
                                        Slog.d(BackupManagerService.TAG, "Full backup pass complete.");
                                    }
                                    BackupManagerService.this.mWakelock.release();
                                }
                            }
                        } catch (Throwable th) {
                            if (outputStream != null) {
                                try {
                                    outputStream.close();
                                } catch (IOException e9) {
                                    synchronized (BackupManagerService.this.mCurrentOpLock) {
                                        BackupManagerService.this.mCurrentOperations.clear();
                                        synchronized (this.mLatch) {
                                            this.mLatch.set(true);
                                            this.mLatch.notifyAll();
                                            sendEndBackup();
                                            fullBackupObbConnection.tearDown();
                                            if (BackupManagerService.DEBUG) {
                                                Slog.d(BackupManagerService.TAG, "Full backup pass complete.");
                                            }
                                            BackupManagerService.this.mWakelock.release();
                                        }
                                    }
                                }
                            }
                            this.mOutputFile.close();
                            synchronized (BackupManagerService.this.mCurrentOpLock) {
                                BackupManagerService.this.mCurrentOperations.clear();
                                synchronized (this.mLatch) {
                                    this.mLatch.set(true);
                                    this.mLatch.notifyAll();
                                    sendEndBackup();
                                    fullBackupObbConnection.tearDown();
                                    if (BackupManagerService.DEBUG) {
                                        Slog.d(BackupManagerService.TAG, "Full backup pass complete.");
                                    }
                                    BackupManagerService.this.mWakelock.release();
                                }
                            }
                        }
                        return;
                    }
                    if (BackupManagerService.DEBUG) {
                        Slog.w(BackupManagerService.TAG, "Backup password mismatch; aborting");
                    }
                    try {
                        this.mOutputFile.close();
                    } catch (IOException e10) {
                    }
                    synchronized (BackupManagerService.this.mCurrentOpLock) {
                        BackupManagerService.this.mCurrentOperations.clear();
                    }
                    synchronized (this.mLatch) {
                        this.mLatch.set(true);
                        this.mLatch.notifyAll();
                    }
                    sendEndBackup();
                    fullBackupObbConnection.tearDown();
                    if (BackupManagerService.DEBUG) {
                        Slog.d(BackupManagerService.TAG, "Full backup pass complete.");
                    }
                    BackupManagerService.this.mWakelock.release();
                    return;
                }
                Slog.e(BackupManagerService.TAG, "Unencrypted backup of encrypted device; aborting");
                try {
                    this.mOutputFile.close();
                } catch (IOException e11) {
                }
                synchronized (BackupManagerService.this.mCurrentOpLock) {
                    BackupManagerService.this.mCurrentOperations.clear();
                }
                synchronized (this.mLatch) {
                    this.mLatch.set(true);
                    this.mLatch.notifyAll();
                }
                sendEndBackup();
                fullBackupObbConnection.tearDown();
                if (BackupManagerService.DEBUG) {
                    Slog.d(BackupManagerService.TAG, "Full backup pass complete.");
                }
                BackupManagerService.this.mWakelock.release();
            } catch (RemoteException e422) {
                Slog.e(BackupManagerService.TAG, "App died during full backup");
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (IOException e522) {
                        synchronized (BackupManagerService.this.mCurrentOpLock) {
                            BackupManagerService.this.mCurrentOperations.clear();
                            synchronized (this.mLatch) {
                                this.mLatch.set(true);
                                this.mLatch.notifyAll();
                                sendEndBackup();
                                fullBackupObbConnection.tearDown();
                                if (BackupManagerService.DEBUG) {
                                    Slog.d(BackupManagerService.TAG, "Full backup pass complete.");
                                }
                                BackupManagerService.this.mWakelock.release();
                                return;
                            }
                        }
                    }
                }
                this.mOutputFile.close();
                synchronized (BackupManagerService.this.mCurrentOpLock) {
                    BackupManagerService.this.mCurrentOperations.clear();
                    synchronized (this.mLatch) {
                        this.mLatch.set(true);
                        this.mLatch.notifyAll();
                        sendEndBackup();
                        fullBackupObbConnection.tearDown();
                        if (BackupManagerService.DEBUG) {
                            Slog.d(BackupManagerService.TAG, "Full backup pass complete.");
                        }
                        BackupManagerService.this.mWakelock.release();
                    }
                }
            } catch (Throwable e12) {
                Slog.e(BackupManagerService.TAG, "Internal exception during full backup", e12);
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (IOException e13) {
                        synchronized (BackupManagerService.this.mCurrentOpLock) {
                            BackupManagerService.this.mCurrentOperations.clear();
                            synchronized (this.mLatch) {
                                this.mLatch.set(true);
                                this.mLatch.notifyAll();
                                sendEndBackup();
                                fullBackupObbConnection.tearDown();
                                if (BackupManagerService.DEBUG) {
                                    Slog.d(BackupManagerService.TAG, "Full backup pass complete.");
                                }
                                BackupManagerService.this.mWakelock.release();
                                return;
                            }
                        }
                    }
                }
                this.mOutputFile.close();
                synchronized (BackupManagerService.this.mCurrentOpLock) {
                    BackupManagerService.this.mCurrentOperations.clear();
                    synchronized (this.mLatch) {
                        this.mLatch.set(true);
                        this.mLatch.notifyAll();
                        sendEndBackup();
                        fullBackupObbConnection.tearDown();
                        if (BackupManagerService.DEBUG) {
                            Slog.d(BackupManagerService.TAG, "Full backup pass complete.");
                        }
                        BackupManagerService.this.mWakelock.release();
                    }
                }
            }
        }

        public void execute() {
        }

        public void operationComplete(long result) {
        }

        public void handleTimeout() {
            PackageInfo target = this.mCurrentTarget;
            if (BackupManagerService.DEBUG) {
                Slog.w(BackupManagerService.TAG, "adb backup timeout of " + target);
            }
            if (target != null) {
                BackupManagerService.this.tearDownAgentAndKill(this.mCurrentTarget.applicationInfo);
            }
        }
    }

    class PerformAdbRestoreTask implements Runnable {
        private static final /* synthetic */ int[] -com-android-server-backup-BackupManagerService$RestorePolicySwitchesValues = null;
        final /* synthetic */ int[] $SWITCH_TABLE$com$android$server$backup$BackupManagerService$RestorePolicy;
        IBackupAgent mAgent;
        String mAgentPackage;
        long mBytes;
        final HashSet<String> mClearedPackages = new HashSet();
        String mCurrentPassword;
        String mDecryptPassword;
        final RestoreDeleteObserver mDeleteObserver = new RestoreDeleteObserver();
        ParcelFileDescriptor mInputFile;
        final RestoreInstallObserver mInstallObserver = new RestoreInstallObserver();
        AtomicBoolean mLatchObject;
        final HashMap<String, Signature[]> mManifestSignatures = new HashMap();
        FullBackupObbConnection mObbConnection = null;
        IFullBackupRestoreObserver mObserver;
        final HashMap<String, String> mPackageInstallers = new HashMap();
        final HashMap<String, RestorePolicy> mPackagePolicies = new HashMap();
        ParcelFileDescriptor[] mPipes = null;
        ApplicationInfo mTargetApp;
        byte[] mWidgetData = null;

        class RestoreDeleteObserver extends IPackageDeleteObserver.Stub {
            final AtomicBoolean mDone = new AtomicBoolean();
            int mResult;

            RestoreDeleteObserver() {
            }

            public void reset() {
                synchronized (this.mDone) {
                    this.mDone.set(false);
                }
            }

            public void waitForCompletion() {
                synchronized (this.mDone) {
                    while (!this.mDone.get()) {
                        try {
                            this.mDone.wait();
                        } catch (InterruptedException e) {
                        }
                    }
                }
            }

            public void packageDeleted(String packageName, int returnCode) throws RemoteException {
                synchronized (this.mDone) {
                    this.mResult = returnCode;
                    this.mDone.set(true);
                    this.mDone.notifyAll();
                }
            }
        }

        class RestoreFileRunnable implements Runnable {
            IBackupAgent mAgent;
            FileMetadata mInfo;
            ParcelFileDescriptor mSocket;
            int mToken;

            RestoreFileRunnable(IBackupAgent agent, FileMetadata info, ParcelFileDescriptor socket, int token) throws IOException {
                this.mAgent = agent;
                this.mInfo = info;
                this.mToken = token;
                this.mSocket = ParcelFileDescriptor.dup(socket.getFileDescriptor());
            }

            public void run() {
                try {
                    this.mAgent.doRestoreFile(this.mSocket, this.mInfo.size, this.mInfo.type, this.mInfo.domain, this.mInfo.path, this.mInfo.mode, this.mInfo.mtime, this.mToken, BackupManagerService.this.mBackupManagerBinder);
                } catch (RemoteException e) {
                }
            }
        }

        class RestoreFinishedRunnable implements Runnable {
            final IBackupAgent mAgent;
            final int mToken;

            RestoreFinishedRunnable(IBackupAgent agent, int token) {
                this.mAgent = agent;
                this.mToken = token;
            }

            public void run() {
                try {
                    this.mAgent.doRestoreFinished(this.mToken, BackupManagerService.this.mBackupManagerBinder);
                } catch (RemoteException e) {
                }
            }
        }

        class RestoreInstallObserver extends PackageInstallObserver {
            final AtomicBoolean mDone = new AtomicBoolean();
            String mPackageName;
            int mResult;

            RestoreInstallObserver() {
            }

            public void reset() {
                synchronized (this.mDone) {
                    this.mDone.set(false);
                }
            }

            public void waitForCompletion() {
                synchronized (this.mDone) {
                    while (!this.mDone.get()) {
                        try {
                            this.mDone.wait();
                        } catch (InterruptedException e) {
                        }
                    }
                }
            }

            int getResult() {
                return this.mResult;
            }

            public void onPackageInstalled(String packageName, int returnCode, String msg, Bundle extras) {
                synchronized (this.mDone) {
                    this.mResult = returnCode;
                    this.mPackageName = packageName;
                    this.mDone.set(true);
                    this.mDone.notifyAll();
                }
            }
        }

        private static /* synthetic */ int[] -getcom-android-server-backup-BackupManagerService$RestorePolicySwitchesValues() {
            if (-com-android-server-backup-BackupManagerService$RestorePolicySwitchesValues != null) {
                return -com-android-server-backup-BackupManagerService$RestorePolicySwitchesValues;
            }
            int[] iArr = new int[RestorePolicy.values().length];
            try {
                iArr[RestorePolicy.ACCEPT.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                iArr[RestorePolicy.ACCEPT_IF_APK.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                iArr[RestorePolicy.IGNORE.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            -com-android-server-backup-BackupManagerService$RestorePolicySwitchesValues = iArr;
            return iArr;
        }

        PerformAdbRestoreTask(ParcelFileDescriptor fd, String curPassword, String decryptPassword, IFullBackupRestoreObserver observer, AtomicBoolean latch) {
            this.mInputFile = fd;
            this.mCurrentPassword = curPassword;
            this.mDecryptPassword = decryptPassword;
            this.mObserver = observer;
            this.mLatchObject = latch;
            this.mAgent = null;
            this.mAgentPackage = null;
            this.mTargetApp = null;
            this.mObbConnection = new FullBackupObbConnection();
            this.mClearedPackages.add("android");
            this.mClearedPackages.add(BackupManagerService.SETTINGS_PACKAGE);
        }

        public void run() {
            DataInputStream dataInputStream;
            Throwable th;
            Slog.i(BackupManagerService.TAG, "--- Performing full-dataset restore ---");
            this.mObbConnection.establish();
            sendStartRestore();
            if (Environment.getExternalStorageState().equals("mounted")) {
                this.mPackagePolicies.put(BackupManagerService.SHARED_BACKUP_AGENT_PACKAGE, RestorePolicy.ACCEPT);
            }
            FileInputStream fileInputStream = null;
            DataInputStream dataInputStream2 = null;
            try {
                if (BackupManagerService.this.backupPasswordMatches(this.mCurrentPassword)) {
                    boolean compressed;
                    InputStream preCompressStream;
                    boolean okay;
                    InputStream rawInStream;
                    this.mBytes = 0;
                    byte[] buffer = new byte[DumpState.DUMP_VERSION];
                    InputStream fileInputStream2 = new FileInputStream(this.mInputFile.getFileDescriptor());
                    try {
                        dataInputStream = new DataInputStream(fileInputStream2);
                        compressed = false;
                        preCompressStream = fileInputStream2;
                        okay = false;
                    } catch (IOException e) {
                        rawInStream = fileInputStream2;
                        try {
                            Slog.e(BackupManagerService.TAG, "Unable to read restore input");
                            tearDownPipes();
                            tearDownAgent(this.mTargetApp, true);
                            if (dataInputStream2 != null) {
                                try {
                                    dataInputStream2.close();
                                } catch (IOException e2) {
                                    Slog.w(BackupManagerService.TAG, "Close of restore data pipe threw", e2);
                                    synchronized (BackupManagerService.this.mCurrentOpLock) {
                                        BackupManagerService.this.mCurrentOperations.clear();
                                    }
                                    synchronized (this.mLatchObject) {
                                        this.mLatchObject.set(true);
                                        this.mLatchObject.notifyAll();
                                    }
                                    this.mObbConnection.tearDown();
                                    sendEndRestore();
                                    Slog.d(BackupManagerService.TAG, "Full restore pass complete.");
                                    BackupManagerService.this.mWakelock.release();
                                }
                            }
                            if (fileInputStream != null) {
                                fileInputStream.close();
                            }
                            this.mInputFile.close();
                            synchronized (BackupManagerService.this.mCurrentOpLock) {
                                BackupManagerService.this.mCurrentOperations.clear();
                            }
                            synchronized (this.mLatchObject) {
                                this.mLatchObject.set(true);
                                this.mLatchObject.notifyAll();
                            }
                            this.mObbConnection.tearDown();
                            sendEndRestore();
                            Slog.d(BackupManagerService.TAG, "Full restore pass complete.");
                            BackupManagerService.this.mWakelock.release();
                        } catch (Throwable th2) {
                            th = th2;
                            tearDownPipes();
                            tearDownAgent(this.mTargetApp, true);
                            if (dataInputStream2 != null) {
                                try {
                                    dataInputStream2.close();
                                } catch (IOException e22) {
                                    Slog.w(BackupManagerService.TAG, "Close of restore data pipe threw", e22);
                                    synchronized (BackupManagerService.this.mCurrentOpLock) {
                                        BackupManagerService.this.mCurrentOperations.clear();
                                    }
                                    synchronized (this.mLatchObject) {
                                        this.mLatchObject.set(true);
                                        this.mLatchObject.notifyAll();
                                    }
                                    this.mObbConnection.tearDown();
                                    sendEndRestore();
                                    Slog.d(BackupManagerService.TAG, "Full restore pass complete.");
                                    BackupManagerService.this.mWakelock.release();
                                    throw th;
                                }
                            }
                            if (fileInputStream != null) {
                                fileInputStream.close();
                            }
                            this.mInputFile.close();
                            synchronized (BackupManagerService.this.mCurrentOpLock) {
                                BackupManagerService.this.mCurrentOperations.clear();
                            }
                            synchronized (this.mLatchObject) {
                                this.mLatchObject.set(true);
                                this.mLatchObject.notifyAll();
                            }
                            this.mObbConnection.tearDown();
                            sendEndRestore();
                            Slog.d(BackupManagerService.TAG, "Full restore pass complete.");
                            BackupManagerService.this.mWakelock.release();
                            throw th;
                        }
                    } catch (Throwable th3) {
                        th = th3;
                        rawInStream = fileInputStream2;
                        tearDownPipes();
                        tearDownAgent(this.mTargetApp, true);
                        if (dataInputStream2 != null) {
                            dataInputStream2.close();
                        }
                        if (fileInputStream != null) {
                            fileInputStream.close();
                        }
                        this.mInputFile.close();
                        synchronized (BackupManagerService.this.mCurrentOpLock) {
                            BackupManagerService.this.mCurrentOperations.clear();
                        }
                        synchronized (this.mLatchObject) {
                            this.mLatchObject.set(true);
                            this.mLatchObject.notifyAll();
                        }
                        this.mObbConnection.tearDown();
                        sendEndRestore();
                        Slog.d(BackupManagerService.TAG, "Full restore pass complete.");
                        BackupManagerService.this.mWakelock.release();
                        throw th;
                    }
                    try {
                        byte[] streamHeader = new byte[BackupManagerService.BACKUP_FILE_HEADER_MAGIC.length()];
                        dataInputStream.readFully(streamHeader);
                        if (Arrays.equals(BackupManagerService.BACKUP_FILE_HEADER_MAGIC.getBytes("UTF-8"), streamHeader)) {
                            String s = readHeaderLine(fileInputStream2);
                            int archiveVersion = Integer.parseInt(s);
                            if (archiveVersion <= 4) {
                                boolean pbkdf2Fallback = archiveVersion == 1;
                                compressed = Integer.parseInt(readHeaderLine(fileInputStream2)) != 0;
                                s = readHeaderLine(fileInputStream2);
                                if (s.equals("none")) {
                                    okay = true;
                                } else if (this.mDecryptPassword == null || this.mDecryptPassword.length() <= 0) {
                                    Slog.w(BackupManagerService.TAG, "Archive is encrypted but no password given");
                                } else {
                                    preCompressStream = decodeAesHeaderAndInitialize(s, pbkdf2Fallback, fileInputStream2);
                                    if (preCompressStream != null) {
                                        okay = true;
                                    }
                                }
                            } else {
                                Slog.w(BackupManagerService.TAG, "Wrong header version: " + s);
                            }
                        } else {
                            Slog.w(BackupManagerService.TAG, "Didn't read the right header magic");
                        }
                        if (okay) {
                            InputStream in = compressed ? new InflaterInputStream(preCompressStream) : preCompressStream;
                            do {
                            } while (restoreOneFile(in, buffer));
                            tearDownPipes();
                            tearDownAgent(this.mTargetApp, true);
                            if (dataInputStream != null) {
                                try {
                                    dataInputStream.close();
                                } catch (IOException e222) {
                                    Slog.w(BackupManagerService.TAG, "Close of restore data pipe threw", e222);
                                }
                            }
                            if (fileInputStream2 != null) {
                                fileInputStream2.close();
                            }
                            this.mInputFile.close();
                            synchronized (BackupManagerService.this.mCurrentOpLock) {
                                BackupManagerService.this.mCurrentOperations.clear();
                            }
                            synchronized (this.mLatchObject) {
                                this.mLatchObject.set(true);
                                this.mLatchObject.notifyAll();
                            }
                            this.mObbConnection.tearDown();
                            sendEndRestore();
                            Slog.d(BackupManagerService.TAG, "Full restore pass complete.");
                            BackupManagerService.this.mWakelock.release();
                            dataInputStream2 = dataInputStream;
                            rawInStream = fileInputStream2;
                        }
                        Slog.w(BackupManagerService.TAG, "Invalid restore data; aborting.");
                        tearDownPipes();
                        tearDownAgent(this.mTargetApp, true);
                        if (dataInputStream != null) {
                            try {
                                dataInputStream.close();
                            } catch (IOException e2222) {
                                Slog.w(BackupManagerService.TAG, "Close of restore data pipe threw", e2222);
                            }
                        }
                        if (fileInputStream2 != null) {
                            fileInputStream2.close();
                        }
                        this.mInputFile.close();
                        synchronized (BackupManagerService.this.mCurrentOpLock) {
                            BackupManagerService.this.mCurrentOperations.clear();
                        }
                        synchronized (this.mLatchObject) {
                            this.mLatchObject.set(true);
                            this.mLatchObject.notifyAll();
                        }
                        this.mObbConnection.tearDown();
                        sendEndRestore();
                        Slog.d(BackupManagerService.TAG, "Full restore pass complete.");
                        BackupManagerService.this.mWakelock.release();
                        return;
                    } catch (IOException e3) {
                        dataInputStream2 = dataInputStream;
                        fileInputStream = fileInputStream2;
                        Slog.e(BackupManagerService.TAG, "Unable to read restore input");
                        tearDownPipes();
                        tearDownAgent(this.mTargetApp, true);
                        if (dataInputStream2 != null) {
                            dataInputStream2.close();
                        }
                        if (fileInputStream != null) {
                            fileInputStream.close();
                        }
                        this.mInputFile.close();
                        synchronized (BackupManagerService.this.mCurrentOpLock) {
                            BackupManagerService.this.mCurrentOperations.clear();
                        }
                        synchronized (this.mLatchObject) {
                            this.mLatchObject.set(true);
                            this.mLatchObject.notifyAll();
                        }
                        this.mObbConnection.tearDown();
                        sendEndRestore();
                        Slog.d(BackupManagerService.TAG, "Full restore pass complete.");
                        BackupManagerService.this.mWakelock.release();
                    } catch (Throwable th4) {
                        th = th4;
                        dataInputStream2 = dataInputStream;
                        fileInputStream = fileInputStream2;
                        tearDownPipes();
                        tearDownAgent(this.mTargetApp, true);
                        if (dataInputStream2 != null) {
                            dataInputStream2.close();
                        }
                        if (fileInputStream != null) {
                            fileInputStream.close();
                        }
                        this.mInputFile.close();
                        synchronized (BackupManagerService.this.mCurrentOpLock) {
                            BackupManagerService.this.mCurrentOperations.clear();
                        }
                        synchronized (this.mLatchObject) {
                            this.mLatchObject.set(true);
                            this.mLatchObject.notifyAll();
                        }
                        this.mObbConnection.tearDown();
                        sendEndRestore();
                        Slog.d(BackupManagerService.TAG, "Full restore pass complete.");
                        BackupManagerService.this.mWakelock.release();
                        throw th;
                    }
                }
                if (BackupManagerService.DEBUG) {
                    Slog.w(BackupManagerService.TAG, "Backup password mismatch; aborting");
                }
                tearDownPipes();
                tearDownAgent(this.mTargetApp, true);
                try {
                    this.mInputFile.close();
                } catch (IOException e22222) {
                    Slog.w(BackupManagerService.TAG, "Close of restore data pipe threw", e22222);
                }
                synchronized (BackupManagerService.this.mCurrentOpLock) {
                    BackupManagerService.this.mCurrentOperations.clear();
                }
                synchronized (this.mLatchObject) {
                    this.mLatchObject.set(true);
                    this.mLatchObject.notifyAll();
                }
                this.mObbConnection.tearDown();
                sendEndRestore();
                Slog.d(BackupManagerService.TAG, "Full restore pass complete.");
                BackupManagerService.this.mWakelock.release();
            } catch (IOException e4) {
                Slog.e(BackupManagerService.TAG, "Unable to read restore input");
                tearDownPipes();
                tearDownAgent(this.mTargetApp, true);
                if (dataInputStream2 != null) {
                    dataInputStream2.close();
                }
                if (fileInputStream != null) {
                    fileInputStream.close();
                }
                this.mInputFile.close();
                synchronized (BackupManagerService.this.mCurrentOpLock) {
                    BackupManagerService.this.mCurrentOperations.clear();
                }
                synchronized (this.mLatchObject) {
                    this.mLatchObject.set(true);
                    this.mLatchObject.notifyAll();
                }
                this.mObbConnection.tearDown();
                sendEndRestore();
                Slog.d(BackupManagerService.TAG, "Full restore pass complete.");
                BackupManagerService.this.mWakelock.release();
            }
        }

        String readHeaderLine(InputStream in) throws IOException {
            StringBuilder buffer = new StringBuilder(80);
            while (true) {
                int c = in.read();
                if (c >= 0 && c != 10) {
                    buffer.append((char) c);
                }
            }
            return buffer.toString();
        }

        InputStream attemptMasterKeyDecryption(String algorithm, byte[] userSalt, byte[] ckSalt, int rounds, String userIvHex, String masterKeyBlobHex, InputStream rawInStream, boolean doLog) {
            try {
                Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
                SecretKey userKey = BackupManagerService.this.buildPasswordKey(algorithm, this.mDecryptPassword, userSalt, rounds);
                c.init(2, new SecretKeySpec(userKey.getEncoded(), "AES"), new IvParameterSpec(BackupManagerService.this.hexToByteArray(userIvHex)));
                byte[] mkBlob = c.doFinal(BackupManagerService.this.hexToByteArray(masterKeyBlobHex));
                int len = mkBlob[0];
                byte[] IV = Arrays.copyOfRange(mkBlob, 1, len + 1);
                int offset = len + 1;
                int offset2 = offset + 1;
                len = mkBlob[offset];
                byte[] mk = Arrays.copyOfRange(mkBlob, offset2, offset2 + len);
                offset = offset2 + len;
                offset2 = offset + 1;
                if (Arrays.equals(BackupManagerService.this.makeKeyChecksum(algorithm, mk, ckSalt, rounds), Arrays.copyOfRange(mkBlob, offset2, offset2 + mkBlob[offset]))) {
                    IvParameterSpec ivSpec = new IvParameterSpec(IV);
                    c.init(2, new SecretKeySpec(mk, "AES"), ivSpec);
                    return new CipherInputStream(rawInStream, c);
                } else if (!doLog) {
                    return null;
                } else {
                    Slog.w(BackupManagerService.TAG, "Incorrect password");
                    return null;
                }
            } catch (InvalidAlgorithmParameterException e) {
                if (!doLog) {
                    return null;
                }
                Slog.e(BackupManagerService.TAG, "Needed parameter spec unavailable!", e);
                return null;
            } catch (BadPaddingException e2) {
                if (!doLog) {
                    return null;
                }
                Slog.w(BackupManagerService.TAG, "Incorrect password");
                return null;
            } catch (IllegalBlockSizeException e3) {
                if (!doLog) {
                    return null;
                }
                Slog.w(BackupManagerService.TAG, "Invalid block size in master key");
                return null;
            } catch (NoSuchAlgorithmException e4) {
                if (!doLog) {
                    return null;
                }
                Slog.e(BackupManagerService.TAG, "Needed decryption algorithm unavailable!");
                return null;
            } catch (NoSuchPaddingException e5) {
                if (!doLog) {
                    return null;
                }
                Slog.e(BackupManagerService.TAG, "Needed padding mechanism unavailable!");
                return null;
            } catch (InvalidKeyException e6) {
                if (!doLog) {
                    return null;
                }
                Slog.w(BackupManagerService.TAG, "Illegal password; aborting");
                return null;
            }
        }

        InputStream decodeAesHeaderAndInitialize(String encryptionName, boolean pbkdf2Fallback, InputStream rawInStream) {
            try {
                if (encryptionName.equals(BackupManagerService.ENCRYPTION_ALGORITHM_NAME)) {
                    byte[] userSalt = BackupManagerService.this.hexToByteArray(readHeaderLine(rawInStream));
                    byte[] ckSalt = BackupManagerService.this.hexToByteArray(readHeaderLine(rawInStream));
                    int rounds = Integer.parseInt(readHeaderLine(rawInStream));
                    String userIvHex = readHeaderLine(rawInStream);
                    String masterKeyBlobHex = readHeaderLine(rawInStream);
                    InputStream result = attemptMasterKeyDecryption(BackupManagerService.PBKDF_CURRENT, userSalt, ckSalt, rounds, userIvHex, masterKeyBlobHex, rawInStream, false);
                    if (result == null && pbkdf2Fallback) {
                        return attemptMasterKeyDecryption(BackupManagerService.PBKDF_FALLBACK, userSalt, ckSalt, rounds, userIvHex, masterKeyBlobHex, rawInStream, true);
                    }
                    return result;
                }
                Slog.w(BackupManagerService.TAG, "Unsupported encryption method: " + encryptionName);
                return null;
            } catch (NumberFormatException e) {
                Slog.w(BackupManagerService.TAG, "Can't parse restore data header");
                return null;
            } catch (IOException e2) {
                Slog.w(BackupManagerService.TAG, "Can't read input header");
                return null;
            }
        }

        boolean restoreOneFile(InputStream instream, byte[] buffer) {
            FileMetadata info;
            boolean z;
            try {
                info = readTarHeaders(instream);
                if (info != null) {
                    String pkg = info.packageName;
                    if (!pkg.equals(this.mAgentPackage)) {
                        if (!this.mPackagePolicies.containsKey(pkg)) {
                            this.mPackagePolicies.put(pkg, RestorePolicy.IGNORE);
                        }
                        if (this.mAgent != null) {
                            if (BackupManagerService.DEBUG) {
                                Slog.d(BackupManagerService.TAG, "Saw new package; finalizing old one");
                            }
                            tearDownPipes();
                            tearDownAgent(this.mTargetApp, true);
                            this.mTargetApp = null;
                            this.mAgentPackage = null;
                        }
                    }
                    if (info.path.equals(BackupManagerService.BACKUP_MANIFEST_FILENAME)) {
                        this.mPackagePolicies.put(pkg, readAppManifest(info, instream));
                        this.mPackageInstallers.put(pkg, info.installerPackageName);
                        skipTarPadding(info.size, instream);
                        sendOnRestorePackage(pkg);
                    } else if (info.path.equals(BackupManagerService.BACKUP_METADATA_FILENAME)) {
                        readMetadata(info, instream);
                        skipTarPadding(info.size, instream);
                    } else {
                        boolean okay = true;
                        switch (-getcom-android-server-backup-BackupManagerService$RestorePolicySwitchesValues()[((RestorePolicy) this.mPackagePolicies.get(pkg)).ordinal()]) {
                            case 1:
                                if (info.domain.equals("a")) {
                                    if (BackupManagerService.DEBUG) {
                                        Slog.d(BackupManagerService.TAG, "apk present but ACCEPT");
                                    }
                                    okay = false;
                                    break;
                                }
                                break;
                            case 2:
                                if (!info.domain.equals("a")) {
                                    this.mPackagePolicies.put(pkg, RestorePolicy.IGNORE);
                                    okay = false;
                                    break;
                                }
                                Object obj;
                                if (BackupManagerService.DEBUG) {
                                    Slog.d(BackupManagerService.TAG, "APK file; installing");
                                }
                                okay = installApk(info, (String) this.mPackageInstallers.get(pkg), instream);
                                HashMap hashMap = this.mPackagePolicies;
                                if (okay) {
                                    obj = RestorePolicy.ACCEPT;
                                } else {
                                    obj = RestorePolicy.IGNORE;
                                }
                                hashMap.put(pkg, obj);
                                skipTarPadding(info.size, instream);
                                return true;
                            case 3:
                                okay = false;
                                break;
                            default:
                                Slog.e(BackupManagerService.TAG, "Invalid policy from manifest");
                                okay = false;
                                this.mPackagePolicies.put(pkg, RestorePolicy.IGNORE);
                                break;
                        }
                        if (info.path.contains("..") || info.path.contains("//")) {
                            okay = false;
                        }
                        if (BackupManagerService.DEBUG && okay && this.mAgent != null) {
                            Slog.i(BackupManagerService.TAG, "Reusing existing agent instance");
                        }
                        if (okay && this.mAgent == null) {
                            if (BackupManagerService.DEBUG) {
                                Slog.d(BackupManagerService.TAG, "Need to launch agent for " + pkg);
                            }
                            try {
                                this.mTargetApp = BackupManagerService.this.mPackageManager.getApplicationInfo(pkg, 0);
                                if (!this.mClearedPackages.contains(pkg)) {
                                    if (this.mTargetApp.backupAgentName == null) {
                                        if (BackupManagerService.DEBUG) {
                                            Slog.d(BackupManagerService.TAG, "Clearing app data preparatory to full restore");
                                        }
                                        BackupManagerService.this.clearApplicationDataSynchronous(pkg);
                                    } else if (BackupManagerService.DEBUG) {
                                        Slog.d(BackupManagerService.TAG, "backup agent (" + this.mTargetApp.backupAgentName + ") => no clear");
                                    }
                                    this.mClearedPackages.add(pkg);
                                } else if (BackupManagerService.DEBUG) {
                                    Slog.d(BackupManagerService.TAG, "We've initialized this app already; no clear required");
                                }
                                setUpPipes();
                                this.mAgent = BackupManagerService.this.bindToAgentSynchronous(this.mTargetApp, 3);
                                this.mAgentPackage = pkg;
                            } catch (IOException e) {
                            } catch (NameNotFoundException e2) {
                            }
                            if (this.mAgent == null) {
                                if (BackupManagerService.DEBUG) {
                                    Slog.d(BackupManagerService.TAG, "Unable to create agent for " + pkg);
                                }
                                okay = false;
                                tearDownPipes();
                                this.mPackagePolicies.put(pkg, RestorePolicy.IGNORE);
                            }
                        }
                        if (okay && !pkg.equals(this.mAgentPackage)) {
                            Slog.e(BackupManagerService.TAG, "Restoring data for " + pkg + " but agent is for " + this.mAgentPackage);
                            okay = false;
                        }
                        if (okay) {
                            boolean agentSuccess = true;
                            long toCopy = info.size;
                            int token = BackupManagerService.this.generateToken();
                            try {
                                BackupManagerService.this.prepareOperationTimeout(token, BackupManagerService.TIMEOUT_FULL_BACKUP_INTERVAL, null);
                                if (info.domain.equals("obb")) {
                                    if (BackupManagerService.DEBUG) {
                                        Slog.d(BackupManagerService.TAG, "Restoring OBB file for " + pkg + " : " + info.path);
                                    }
                                    this.mObbConnection.restoreObbFile(pkg, this.mPipes[0], info.size, info.type, info.path, info.mode, info.mtime, token, BackupManagerService.this.mBackupManagerBinder);
                                } else {
                                    if (BackupManagerService.DEBUG) {
                                        Slog.d(BackupManagerService.TAG, "Invoking agent to restore file " + info.path);
                                    }
                                    if (this.mTargetApp.processName.equals("system")) {
                                        Slog.d(BackupManagerService.TAG, "system process agent - spinning a thread");
                                        new Thread(new RestoreFileRunnable(this.mAgent, info, this.mPipes[0], token), "restore-sys-runner").start();
                                    } else {
                                        this.mAgent.doRestoreFile(this.mPipes[0], info.size, info.type, info.domain, info.path, info.mode, info.mtime, token, BackupManagerService.this.mBackupManagerBinder);
                                    }
                                }
                            } catch (IOException e3) {
                                Slog.d(BackupManagerService.TAG, "Couldn't establish restore");
                                agentSuccess = false;
                                okay = false;
                            } catch (RemoteException e4) {
                                Slog.e(BackupManagerService.TAG, "Agent crashed during full restore");
                                agentSuccess = false;
                                okay = false;
                            }
                            if (okay) {
                                boolean pipeOkay = true;
                                FileOutputStream fileOutputStream = new FileOutputStream(this.mPipes[1].getFileDescriptor());
                                while (toCopy > 0) {
                                    int nRead = instream.read(buffer, 0, toCopy > ((long) buffer.length) ? buffer.length : (int) toCopy);
                                    if (nRead >= 0) {
                                        this.mBytes += (long) nRead;
                                    }
                                    if (nRead <= 0) {
                                        skipTarPadding(info.size, instream);
                                        agentSuccess = BackupManagerService.this.waitUntilOperationComplete(token);
                                    } else {
                                        toCopy -= (long) nRead;
                                        if (pipeOkay) {
                                            try {
                                                fileOutputStream.write(buffer, 0, nRead);
                                            } catch (Throwable e5) {
                                                Slog.e(BackupManagerService.TAG, "Failed to write to restore pipe", e5);
                                                pipeOkay = false;
                                            }
                                        }
                                    }
                                }
                                skipTarPadding(info.size, instream);
                                agentSuccess = BackupManagerService.this.waitUntilOperationComplete(token);
                            }
                            if (!agentSuccess) {
                                if (BackupManagerService.DEBUG) {
                                    Slog.d(BackupManagerService.TAG, "Agent failure restoring " + pkg + "; now ignoring");
                                }
                                BackupManagerService.this.mBackupHandler.removeMessages(7);
                                tearDownPipes();
                                tearDownAgent(this.mTargetApp, false);
                                this.mPackagePolicies.put(pkg, RestorePolicy.IGNORE);
                            }
                        }
                        if (!okay) {
                            if (BackupManagerService.DEBUG) {
                                Slog.d(BackupManagerService.TAG, "[discarding file content]");
                            }
                            long bytesToConsume = (info.size + 511) & -512;
                            while (bytesToConsume > 0) {
                                int toRead;
                                if (bytesToConsume > ((long) buffer.length)) {
                                    toRead = buffer.length;
                                } else {
                                    toRead = (int) bytesToConsume;
                                }
                                long nRead2 = (long) instream.read(buffer, 0, toRead);
                                if (nRead2 >= 0) {
                                    this.mBytes += nRead2;
                                }
                                if (nRead2 > 0) {
                                    bytesToConsume -= nRead2;
                                }
                            }
                        }
                    }
                }
            } catch (Throwable e52) {
                if (BackupManagerService.DEBUG) {
                    Slog.w(BackupManagerService.TAG, "io exception on restore socket read", e52);
                }
                info = null;
            }
            if (info != null) {
                z = true;
            } else {
                z = false;
            }
            return z;
        }

        void setUpPipes() throws IOException {
            this.mPipes = ParcelFileDescriptor.createPipe();
        }

        void tearDownPipes() {
            if (this.mPipes != null) {
                try {
                    this.mPipes[0].close();
                    this.mPipes[0] = null;
                    this.mPipes[1].close();
                    this.mPipes[1] = null;
                } catch (IOException e) {
                    Slog.w(BackupManagerService.TAG, "Couldn't close agent pipes", e);
                }
                this.mPipes = null;
            }
        }

        void tearDownAgent(ApplicationInfo app, boolean doRestoreFinished) {
            if (this.mAgent != null) {
                if (doRestoreFinished) {
                    try {
                        int token = BackupManagerService.this.generateToken();
                        AdbRestoreFinishedLatch latch = new AdbRestoreFinishedLatch();
                        BackupManagerService.this.prepareOperationTimeout(token, BackupManagerService.TIMEOUT_FULL_BACKUP_INTERVAL, latch);
                        if (this.mTargetApp.processName.equals("system")) {
                            new Thread(new RestoreFinishedRunnable(this.mAgent, token), "restore-sys-finished-runner").start();
                        } else {
                            this.mAgent.doRestoreFinished(token, BackupManagerService.this.mBackupManagerBinder);
                        }
                        latch.await();
                    } catch (RemoteException e) {
                        Slog.d(BackupManagerService.TAG, "Lost app trying to shut down");
                    }
                }
                BackupManagerService.this.mActivityManager.unbindBackupAgent(app);
                if (app.uid < 10000 || app.packageName.equals("com.android.backupconfirm")) {
                    if (BackupManagerService.DEBUG) {
                        Slog.d(BackupManagerService.TAG, "Not killing after full restore");
                    }
                    this.mAgent = null;
                }
                if (BackupManagerService.DEBUG) {
                    Slog.d(BackupManagerService.TAG, "Killing host process");
                }
                BackupManagerService.this.mActivityManager.killApplicationProcess(app.processName, app.uid);
                this.mAgent = null;
            }
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        boolean installApk(FileMetadata info, String installerPackage, InputStream instream) {
            boolean okay = true;
            if (BackupManagerService.DEBUG) {
                Slog.d(BackupManagerService.TAG, "Installing from backup: " + info.packageName);
            }
            File apkFile = new File(BackupManagerService.this.mDataDir, info.packageName);
            try {
                FileOutputStream apkStream = new FileOutputStream(apkFile);
                byte[] buffer = new byte[DumpState.DUMP_VERSION];
                long size = info.size;
                while (size > 0) {
                    long toRead;
                    if (((long) buffer.length) < size) {
                        toRead = (long) buffer.length;
                    } else {
                        toRead = size;
                    }
                    int didRead = instream.read(buffer, 0, (int) toRead);
                    if (didRead >= 0) {
                        this.mBytes += (long) didRead;
                    }
                    apkStream.write(buffer, 0, didRead);
                    size -= (long) didRead;
                }
                apkStream.close();
                apkFile.setReadable(true, false);
                Uri packageUri = Uri.fromFile(apkFile);
                this.mInstallObserver.reset();
                BackupManagerService.this.mPackageManager.installPackage(packageUri, this.mInstallObserver, 34, installerPackage);
                this.mInstallObserver.waitForCompletion();
                if (this.mInstallObserver.getResult() != 1) {
                    if (this.mPackagePolicies.get(info.packageName) != RestorePolicy.ACCEPT) {
                        okay = false;
                    }
                } else {
                    boolean uninstall = false;
                    if (this.mInstallObserver.mPackageName.equals(info.packageName)) {
                        try {
                            PackageInfo pkg = BackupManagerService.this.mPackageManager.getPackageInfo(info.packageName, 64);
                            if ((pkg.applicationInfo.flags & DumpState.DUMP_VERSION) == 0) {
                                Slog.w(BackupManagerService.TAG, "Restore stream contains apk of package " + info.packageName + " but it disallows backup/restore");
                                okay = false;
                            } else if (!BackupManagerService.signaturesMatch((Signature[]) this.mManifestSignatures.get(info.packageName), pkg)) {
                                Slog.w(BackupManagerService.TAG, "Installed app " + info.packageName + " signatures do not match restore manifest");
                                okay = false;
                                uninstall = true;
                            } else if (pkg.applicationInfo.uid < 10000 && pkg.applicationInfo.backupAgentName == null) {
                                Slog.w(BackupManagerService.TAG, "Installed app " + info.packageName + " has restricted uid and no agent");
                                okay = false;
                            }
                        } catch (NameNotFoundException e) {
                            Slog.w(BackupManagerService.TAG, "Install of package " + info.packageName + " succeeded but now not found");
                            okay = false;
                        }
                    } else {
                        Slog.w(BackupManagerService.TAG, "Restore stream claimed to include apk for " + info.packageName + " but apk was really " + this.mInstallObserver.mPackageName);
                        okay = false;
                        uninstall = true;
                    }
                    if (uninstall) {
                        this.mDeleteObserver.reset();
                        BackupManagerService.this.mPackageManager.deletePackage(this.mInstallObserver.mPackageName, this.mDeleteObserver, 0);
                        this.mDeleteObserver.waitForCompletion();
                    }
                }
                apkFile.delete();
                return okay;
            } catch (IOException e2) {
                Slog.e(BackupManagerService.TAG, "Unable to transcribe restored apk for install");
                return false;
            } catch (Throwable th) {
                apkFile.delete();
            }
        }

        void skipTarPadding(long size, InputStream instream) throws IOException {
            long partial = (size + 512) % 512;
            if (partial > 0) {
                int needed = 512 - ((int) partial);
                if (readExactly(instream, new byte[needed], 0, needed) == needed) {
                    this.mBytes += (long) needed;
                    return;
                }
                throw new IOException("Unexpected EOF in padding");
            }
        }

        void readMetadata(FileMetadata info, InputStream instream) throws IOException {
            if (info.size > 65536) {
                throw new IOException("Metadata too big; corrupt? size=" + info.size);
            }
            byte[] buffer = new byte[((int) info.size)];
            if (((long) readExactly(instream, buffer, 0, (int) info.size)) == info.size) {
                this.mBytes += info.size;
                String[] str = new String[1];
                int offset = extractLine(buffer, 0, str);
                int version = Integer.parseInt(str[0]);
                if (version == 1) {
                    offset = extractLine(buffer, offset, str);
                    String pkg = str[0];
                    if (info.packageName.equals(pkg)) {
                        ByteArrayInputStream bin = new ByteArrayInputStream(buffer, offset, buffer.length - offset);
                        DataInputStream in = new DataInputStream(bin);
                        while (bin.available() > 0) {
                            int token = in.readInt();
                            int size = in.readInt();
                            if (size <= DumpState.DUMP_INSTALLS) {
                                switch (token) {
                                    case BackupManagerService.BACKUP_WIDGET_METADATA_TOKEN /*33549569*/:
                                        this.mWidgetData = new byte[size];
                                        in.read(this.mWidgetData);
                                        break;
                                    default:
                                        if (BackupManagerService.DEBUG) {
                                            Slog.i(BackupManagerService.TAG, "Ignoring metadata blob " + Integer.toHexString(token) + " for " + info.packageName);
                                        }
                                        in.skipBytes(size);
                                        break;
                                }
                            }
                            throw new IOException("Datum " + Integer.toHexString(token) + " too big; corrupt? size=" + info.size);
                        }
                        return;
                    }
                    Slog.w(BackupManagerService.TAG, "Metadata mismatch: package " + info.packageName + " but widget data for " + pkg);
                    return;
                }
                Slog.w(BackupManagerService.TAG, "Unsupported metadata version " + version);
                return;
            }
            throw new IOException("Unexpected EOF in widget data");
        }

        RestorePolicy readAppManifest(FileMetadata info, InputStream instream) throws IOException {
            if (info.size > 65536) {
                throw new IOException("Restore manifest too big; corrupt? size=" + info.size);
            }
            byte[] buffer = new byte[((int) info.size)];
            if (((long) readExactly(instream, buffer, 0, (int) info.size)) == info.size) {
                this.mBytes += info.size;
                RestorePolicy policy = RestorePolicy.IGNORE;
                String[] str = new String[1];
                try {
                    int offset = extractLine(buffer, 0, str);
                    int version = Integer.parseInt(str[0]);
                    if (version == 1) {
                        offset = extractLine(buffer, offset, str);
                        String manifestPackage = str[0];
                        if (manifestPackage.equals(info.packageName)) {
                            offset = extractLine(buffer, offset, str);
                            version = Integer.parseInt(str[0]);
                            offset = extractLine(buffer, offset, str);
                            Integer.parseInt(str[0]);
                            offset = extractLine(buffer, offset, str);
                            info.installerPackageName = str[0].length() > 0 ? str[0] : null;
                            offset = extractLine(buffer, offset, str);
                            boolean hasApk = str[0].equals("1");
                            offset = extractLine(buffer, offset, str);
                            int numSigs = Integer.parseInt(str[0]);
                            if (numSigs > 0) {
                                Object sigs = new Signature[numSigs];
                                for (int i = 0; i < numSigs; i++) {
                                    offset = extractLine(buffer, offset, str);
                                    sigs[i] = new Signature(str[0]);
                                }
                                this.mManifestSignatures.put(info.packageName, sigs);
                                try {
                                    PackageInfo pkgInfo = BackupManagerService.this.mPackageManager.getPackageInfo(info.packageName, 64);
                                    if ((DumpState.DUMP_VERSION & pkgInfo.applicationInfo.flags) == 0) {
                                        if (BackupManagerService.DEBUG) {
                                            Slog.i(BackupManagerService.TAG, "Restore manifest from " + info.packageName + " but allowBackup=false");
                                        }
                                        Slog.i(BackupManagerService.TAG, "Cannot restore package " + info.packageName + " without the matching .apk");
                                    } else if (pkgInfo.applicationInfo.uid >= 10000 || pkgInfo.applicationInfo.backupAgentName != null) {
                                        if (!BackupManagerService.signaturesMatch(sigs, pkgInfo)) {
                                            Slog.w(BackupManagerService.TAG, "Restore manifest signatures do not match installed application for " + info.packageName);
                                        } else if (pkgInfo.versionCode >= version) {
                                            Slog.i(BackupManagerService.TAG, "Sig + version match; taking data");
                                            policy = RestorePolicy.ACCEPT;
                                        } else {
                                            Slog.d(BackupManagerService.TAG, "Data version " + version + " is newer than installed version " + pkgInfo.versionCode + " - requiring apk");
                                            policy = RestorePolicy.ACCEPT_IF_APK;
                                        }
                                        if (policy == RestorePolicy.ACCEPT_IF_APK && !hasApk) {
                                            Slog.i(BackupManagerService.TAG, "Cannot restore package " + info.packageName + " without the matching .apk");
                                        }
                                    } else {
                                        Slog.w(BackupManagerService.TAG, "Package " + info.packageName + " is system level with no agent");
                                        Slog.i(BackupManagerService.TAG, "Cannot restore package " + info.packageName + " without the matching .apk");
                                    }
                                } catch (NameNotFoundException e) {
                                    if (BackupManagerService.DEBUG) {
                                        Slog.i(BackupManagerService.TAG, "Package " + info.packageName + " not installed; requiring apk in dataset");
                                    }
                                    policy = RestorePolicy.ACCEPT_IF_APK;
                                }
                            } else {
                                Slog.i(BackupManagerService.TAG, "Missing signature on backed-up package " + info.packageName);
                            }
                        } else {
                            Slog.i(BackupManagerService.TAG, "Expected package " + info.packageName + " but restore manifest claims " + manifestPackage);
                        }
                    } else {
                        Slog.i(BackupManagerService.TAG, "Unknown restore manifest version " + version + " for package " + info.packageName);
                    }
                } catch (NumberFormatException e2) {
                    Slog.w(BackupManagerService.TAG, "Corrupt restore manifest for package " + info.packageName);
                } catch (IllegalArgumentException e3) {
                    Slog.w(BackupManagerService.TAG, e3.getMessage());
                }
                return policy;
            }
            throw new IOException("Unexpected EOF in manifest");
        }

        int extractLine(byte[] buffer, int offset, String[] outStr) throws IOException {
            int end = buffer.length;
            if (offset >= end) {
                throw new IOException("Incomplete data");
            }
            int pos = offset;
            while (pos < end && buffer[pos] != (byte) 10) {
                pos++;
            }
            outStr[0] = new String(buffer, offset, pos - offset);
            return pos + 1;
        }

        void dumpFileMetadata(FileMetadata info) {
            char c = 'x';
            char c2 = 'w';
            char c3 = 'r';
            if (BackupManagerService.DEBUG) {
                char c4;
                StringBuilder b = new StringBuilder(128);
                if (info.type == 2) {
                    c4 = 'd';
                } else {
                    c4 = '-';
                }
                b.append(c4);
                if ((info.mode & 256) != 0) {
                    c4 = 'r';
                } else {
                    c4 = '-';
                }
                b.append(c4);
                if ((info.mode & 128) != 0) {
                    c4 = 'w';
                } else {
                    c4 = '-';
                }
                b.append(c4);
                if ((info.mode & 64) != 0) {
                    c4 = 'x';
                } else {
                    c4 = '-';
                }
                b.append(c4);
                if ((info.mode & 32) != 0) {
                    c4 = 'r';
                } else {
                    c4 = '-';
                }
                b.append(c4);
                if ((info.mode & 16) != 0) {
                    c4 = 'w';
                } else {
                    c4 = '-';
                }
                b.append(c4);
                if ((info.mode & 8) != 0) {
                    c4 = 'x';
                } else {
                    c4 = '-';
                }
                b.append(c4);
                if ((info.mode & 4) == 0) {
                    c3 = '-';
                }
                b.append(c3);
                if ((info.mode & 2) == 0) {
                    c2 = '-';
                }
                b.append(c2);
                if ((info.mode & 1) == 0) {
                    c = '-';
                }
                b.append(c);
                b.append(String.format(" %9d ", new Object[]{Long.valueOf(info.size)}));
                b.append(new SimpleDateFormat("MMM dd HH:mm:ss ").format(new Date(info.mtime)));
                b.append(info.packageName);
                b.append(" :: ");
                b.append(info.domain);
                b.append(" :: ");
                b.append(info.path);
                Slog.i(BackupManagerService.TAG, b.toString());
            }
        }

        FileMetadata readTarHeaders(InputStream instream) throws IOException {
            IOException e;
            byte[] block = new byte[512];
            FileMetadata fileMetadata = null;
            if (readTarHeader(instream, block)) {
                try {
                    FileMetadata info = new FileMetadata();
                    try {
                        info.size = extractRadix(block, 124, 12, 8);
                        info.mtime = extractRadix(block, 136, 12, 8);
                        info.mode = extractRadix(block, 100, 8, 8);
                        info.path = extractString(block, 345, 155);
                        String path = extractString(block, 0, 100);
                        if (path.length() > 0) {
                            if (info.path.length() > 0) {
                                info.path += '/';
                            }
                            info.path += path;
                        }
                        int typeChar = block[156];
                        if (typeChar == 120) {
                            boolean gotHeader = readPaxExtendedHeader(instream, info);
                            if (gotHeader) {
                                gotHeader = readTarHeader(instream, block);
                            }
                            if (gotHeader) {
                                typeChar = block[156];
                            } else {
                                throw new IOException("Bad or missing pax header");
                            }
                        }
                        switch (typeChar) {
                            case 0:
                                if (BackupManagerService.DEBUG) {
                                    Slog.w(BackupManagerService.TAG, "Saw type=0 in tar header block, info=" + info);
                                }
                                return null;
                            case 48:
                                info.type = 1;
                                break;
                            case 53:
                                info.type = 2;
                                if (info.size != 0) {
                                    Slog.w(BackupManagerService.TAG, "Directory entry with nonzero size in header");
                                    info.size = 0;
                                    break;
                                }
                                break;
                            default:
                                Slog.e(BackupManagerService.TAG, "Unknown tar entity type: " + typeChar);
                                throw new IOException("Unknown entity type " + typeChar);
                        }
                        if ("shared/".regionMatches(0, info.path, 0, "shared/".length())) {
                            info.path = info.path.substring("shared/".length());
                            info.packageName = BackupManagerService.SHARED_BACKUP_AGENT_PACKAGE;
                            info.domain = "shared";
                            if (BackupManagerService.DEBUG) {
                                Slog.i(BackupManagerService.TAG, "File in shared storage: " + info.path);
                            }
                        } else if ("apps/".regionMatches(0, info.path, 0, "apps/".length())) {
                            info.path = info.path.substring("apps/".length());
                            int slash = info.path.indexOf(47);
                            if (slash < 0) {
                                throw new IOException("Illegal semantic path in " + info.path);
                            }
                            info.packageName = info.path.substring(0, slash);
                            info.path = info.path.substring(slash + 1);
                            if (!(info.path.equals(BackupManagerService.BACKUP_MANIFEST_FILENAME) || info.path.equals(BackupManagerService.BACKUP_METADATA_FILENAME))) {
                                slash = info.path.indexOf(47);
                                if (slash < 0) {
                                    throw new IOException("Illegal semantic path in non-manifest " + info.path);
                                }
                                info.domain = info.path.substring(0, slash);
                                info.path = info.path.substring(slash + 1);
                            }
                        }
                        fileMetadata = info;
                    } catch (IOException e2) {
                        e = e2;
                        fileMetadata = info;
                        if (BackupManagerService.DEBUG) {
                            Slog.e(BackupManagerService.TAG, "Parse error in header: " + e.getMessage());
                            HEXLOG(block);
                        }
                        throw e;
                    }
                } catch (IOException e3) {
                    e = e3;
                    if (BackupManagerService.DEBUG) {
                        Slog.e(BackupManagerService.TAG, "Parse error in header: " + e.getMessage());
                        HEXLOG(block);
                    }
                    throw e;
                }
            }
            return fileMetadata;
        }

        private void HEXLOG(byte[] block) {
            int offset = 0;
            int todo = block.length;
            StringBuilder buf = new StringBuilder(64);
            while (todo > 0) {
                buf.append(String.format("%04x   ", new Object[]{Integer.valueOf(offset)}));
                int numThisLine = todo > 16 ? 16 : todo;
                for (int i = 0; i < numThisLine; i++) {
                    buf.append(String.format("%02x ", new Object[]{Byte.valueOf(block[offset + i])}));
                }
                Slog.i("hexdump", buf.toString());
                buf.setLength(0);
                todo -= numThisLine;
                offset += numThisLine;
            }
        }

        int readExactly(InputStream in, byte[] buffer, int offset, int size) throws IOException {
            if (size <= 0) {
                throw new IllegalArgumentException("size must be > 0");
            }
            int soFar = 0;
            while (soFar < size) {
                int nRead = in.read(buffer, offset + soFar, size - soFar);
                if (nRead <= 0) {
                    break;
                }
                soFar += nRead;
            }
            return soFar;
        }

        boolean readTarHeader(InputStream instream, byte[] block) throws IOException {
            int got = readExactly(instream, block, 0, 512);
            if (got == 0) {
                return false;
            }
            if (got < 512) {
                throw new IOException("Unable to read full block header");
            }
            this.mBytes += 512;
            return true;
        }

        boolean readPaxExtendedHeader(InputStream instream, FileMetadata info) throws IOException {
            if (info.size > 32768) {
                Slog.w(BackupManagerService.TAG, "Suspiciously large pax header size " + info.size + " - aborting");
                throw new IOException("Sanity failure: pax header size " + info.size);
            }
            byte[] data = new byte[(((int) ((info.size + 511) >> 9)) * 512)];
            if (readExactly(instream, data, 0, data.length) < data.length) {
                throw new IOException("Unable to read full pax header");
            }
            this.mBytes += (long) data.length;
            int contentSize = (int) info.size;
            int offset = 0;
            do {
                int eol = offset + 1;
                while (eol < contentSize && data[eol] != (byte) 32) {
                    eol++;
                }
                if (eol >= contentSize) {
                    throw new IOException("Invalid pax data");
                }
                int linelen = (int) extractRadix(data, offset, eol - offset, 10);
                int key = eol + 1;
                eol = (offset + linelen) - 1;
                int value = key + 1;
                while (data[value] != (byte) 61 && value <= eol) {
                    value++;
                }
                if (value > eol) {
                    throw new IOException("Invalid pax declaration");
                }
                String keyStr = new String(data, key, value - key, "UTF-8");
                String valStr = new String(data, value + 1, (eol - value) - 1, "UTF-8");
                if ("path".equals(keyStr)) {
                    info.path = valStr;
                } else if ("size".equals(keyStr)) {
                    info.size = Long.parseLong(valStr);
                } else if (BackupManagerService.DEBUG) {
                    Slog.i(BackupManagerService.TAG, "Unhandled pax key: " + key);
                }
                offset += linelen;
            } while (offset < contentSize);
            return true;
        }

        long extractRadix(byte[] data, int offset, int maxChars, int radix) throws IOException {
            long value = 0;
            int end = offset + maxChars;
            int i = offset;
            while (i < end) {
                byte b = data[i];
                if (b == (byte) 0 || b == (byte) 32) {
                    break;
                } else if (b < (byte) 48 || b > (radix + 48) - 1) {
                    throw new IOException("Invalid number in header: '" + ((char) b) + "' for radix " + radix);
                } else {
                    value = (((long) radix) * value) + ((long) (b - 48));
                    i++;
                }
            }
            return value;
        }

        String extractString(byte[] data, int offset, int maxChars) throws IOException {
            int end = offset + maxChars;
            int eos = offset;
            while (eos < end && data[eos] != (byte) 0) {
                eos++;
            }
            return new String(data, offset, eos - offset, "US-ASCII");
        }

        void sendStartRestore() {
            if (this.mObserver != null) {
                try {
                    this.mObserver.onStartRestore();
                } catch (RemoteException e) {
                    Slog.w(BackupManagerService.TAG, "full restore observer went away: startRestore");
                    this.mObserver = null;
                }
            }
        }

        void sendOnRestorePackage(String name) {
            if (this.mObserver != null) {
                try {
                    this.mObserver.onRestorePackage(name);
                } catch (RemoteException e) {
                    Slog.w(BackupManagerService.TAG, "full restore observer went away: restorePackage");
                    this.mObserver = null;
                }
            }
        }

        void sendEndRestore() {
            if (this.mObserver != null) {
                try {
                    this.mObserver.onEndRestore();
                } catch (RemoteException e) {
                    Slog.w(BackupManagerService.TAG, "full restore observer went away: endRestore");
                    this.mObserver = null;
                }
            }
        }
    }

    class PerformBackupTask implements BackupRestoreTask {
        private static final /* synthetic */ int[] -com-android-server-backup-BackupManagerService$BackupStateSwitchesValues = null;
        private static final String TAG = "PerformBackupTask";
        final /* synthetic */ int[] $SWITCH_TABLE$com$android$server$backup$BackupManagerService$BackupState;
        IBackupAgent mAgentBinder;
        ParcelFileDescriptor mBackupData;
        File mBackupDataName;
        PackageInfo mCurrentPackage;
        BackupState mCurrentState = BackupState.INITIAL;
        boolean mFinished = false;
        File mJournal;
        ParcelFileDescriptor mNewState;
        File mNewStateName;
        IBackupObserver mObserver;
        ArrayList<BackupRequest> mOriginalQueue;
        ArrayList<String> mPendingFullBackups;
        ArrayList<BackupRequest> mQueue;
        ParcelFileDescriptor mSavedState;
        File mSavedStateName;
        File mStateDir;
        int mStatus;
        IBackupTransport mTransport;
        boolean mUserInitiated;

        private static /* synthetic */ int[] -getcom-android-server-backup-BackupManagerService$BackupStateSwitchesValues() {
            if (-com-android-server-backup-BackupManagerService$BackupStateSwitchesValues != null) {
                return -com-android-server-backup-BackupManagerService$BackupStateSwitchesValues;
            }
            int[] iArr = new int[BackupState.values().length];
            try {
                iArr[BackupState.FINAL.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                iArr[BackupState.INITIAL.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                iArr[BackupState.RUNNING_QUEUE.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            -com-android-server-backup-BackupManagerService$BackupStateSwitchesValues = iArr;
            return iArr;
        }

        public PerformBackupTask(IBackupTransport transport, String dirName, ArrayList<BackupRequest> queue, File journal, IBackupObserver observer, ArrayList<String> pendingFullBackups, boolean userInitiated) {
            this.mTransport = transport;
            this.mOriginalQueue = queue;
            this.mJournal = journal;
            this.mObserver = observer;
            this.mPendingFullBackups = pendingFullBackups;
            this.mUserInitiated = userInitiated;
            this.mStateDir = new File(BackupManagerService.this.mBaseStateDir, dirName);
            BackupManagerService.this.addBackupTrace("STATE => INITIAL");
        }

        public void execute() {
            switch (-getcom-android-server-backup-BackupManagerService$BackupStateSwitchesValues()[this.mCurrentState.ordinal()]) {
                case 1:
                    if (this.mFinished) {
                        Slog.e(TAG, "Duplicate finish");
                    } else {
                        finalizeBackup();
                    }
                    this.mFinished = true;
                    return;
                case 2:
                    beginBackup();
                    return;
                case 3:
                    invokeNextAgent();
                    return;
                default:
                    return;
            }
        }

        void beginBackup() {
            BackupManagerService.this.clearBackupTrace();
            StringBuilder b = new StringBuilder(256);
            b.append("beginBackup: [");
            for (BackupRequest req : this.mOriginalQueue) {
                b.append(' ');
                b.append(req.packageName);
            }
            b.append(" ]");
            BackupManagerService.this.addBackupTrace(b.toString());
            this.mAgentBinder = null;
            this.mStatus = 0;
            if (this.mOriginalQueue.isEmpty() && this.mPendingFullBackups.isEmpty()) {
                Slog.w(TAG, "Backup begun with an empty queue - nothing to do.");
                BackupManagerService.this.addBackupTrace("queue empty at begin");
                BackupManagerService.sendBackupFinished(this.mObserver, 0);
                executeNextState(BackupState.FINAL);
                return;
            }
            this.mQueue = (ArrayList) this.mOriginalQueue.clone();
            for (int i = 0; i < this.mQueue.size(); i++) {
                if (BackupManagerService.PACKAGE_MANAGER_SENTINEL.equals(((BackupRequest) this.mQueue.get(i)).packageName)) {
                    this.mQueue.remove(i);
                    break;
                }
            }
            if (BackupManagerService.DEBUG) {
                Slog.v(TAG, "Beginning backup of " + this.mQueue.size() + " targets");
            }
            File pmState = new File(this.mStateDir, BackupManagerService.PACKAGE_MANAGER_SENTINEL);
            try {
                String transportName = this.mTransport.transportDirName();
                EventLog.writeEvent(EventLogTags.BACKUP_START, transportName);
                if (this.mStatus == 0 && pmState.length() <= 0) {
                    Slog.i(TAG, "Initializing (wiping) backup state and transport storage");
                    BackupManagerService.this.addBackupTrace("initializing transport " + transportName);
                    BackupManagerService.this.resetBackupState(this.mStateDir);
                    this.mStatus = this.mTransport.initializeDevice();
                    BackupManagerService.this.addBackupTrace("transport.initializeDevice() == " + this.mStatus);
                    if (this.mStatus == 0) {
                        EventLog.writeEvent(EventLogTags.BACKUP_INITIALIZE, new Object[0]);
                    } else {
                        EventLog.writeEvent(EventLogTags.BACKUP_TRANSPORT_FAILURE, "(initialize)");
                        Slog.e(TAG, "Transport error in initializeDevice()");
                    }
                }
                if (this.mStatus == 0) {
                    this.mStatus = invokeAgentForBackup(BackupManagerService.PACKAGE_MANAGER_SENTINEL, IBackupAgent.Stub.asInterface(new PackageManagerBackupAgent(BackupManagerService.this.mPackageManager).onBind()), this.mTransport);
                    BackupManagerService.this.addBackupTrace("PMBA invoke: " + this.mStatus);
                    BackupManagerService.this.mBackupHandler.removeMessages(7);
                }
                if (this.mStatus == JobSchedulerShellCommand.CMD_ERR_NO_JOB) {
                    EventLog.writeEvent(EventLogTags.BACKUP_RESET, this.mTransport.transportDirName());
                }
                BackupManagerService.this.addBackupTrace("exiting prelim: " + this.mStatus);
                if (this.mStatus != 0) {
                    BackupManagerService.this.resetBackupState(this.mStateDir);
                    BackupManagerService.sendBackupFinished(this.mObserver, JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE);
                    executeNextState(BackupState.FINAL);
                }
            } catch (Exception e) {
                Slog.e(TAG, "Error in backup thread", e);
                BackupManagerService.this.addBackupTrace("Exception in backup thread: " + e);
                this.mStatus = JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE;
                BackupManagerService.this.addBackupTrace("exiting prelim: " + this.mStatus);
                if (this.mStatus != 0) {
                    BackupManagerService.this.resetBackupState(this.mStateDir);
                    BackupManagerService.sendBackupFinished(this.mObserver, JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE);
                    executeNextState(BackupState.FINAL);
                }
            } catch (Throwable th) {
                BackupManagerService.this.addBackupTrace("exiting prelim: " + this.mStatus);
                if (this.mStatus != 0) {
                    BackupManagerService.this.resetBackupState(this.mStateDir);
                    BackupManagerService.sendBackupFinished(this.mObserver, JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE);
                    executeNextState(BackupState.FINAL);
                }
            }
        }

        void invokeNextAgent() {
            this.mStatus = 0;
            BackupManagerService.this.addBackupTrace("invoke q=" + this.mQueue.size());
            if (this.mQueue.isEmpty()) {
                executeNextState(BackupState.FINAL);
                return;
            }
            BackupRequest request = (BackupRequest) this.mQueue.get(0);
            this.mQueue.remove(0);
            Slog.d(TAG, "starting key/value backup of " + request);
            BackupManagerService.this.addBackupTrace("launch agent for " + request.packageName);
            BackupState nextState;
            try {
                this.mCurrentPackage = BackupManagerService.this.mPackageManager.getPackageInfo(request.packageName, 64);
                if (!BackupManagerService.appIsEligibleForBackup(this.mCurrentPackage.applicationInfo)) {
                    Slog.i(TAG, "Package " + request.packageName + " no longer supports backup; skipping");
                    BackupManagerService.this.addBackupTrace("skipping - not eligible, completion is noop");
                    BackupManagerService.sendBackupOnPackageResult(this.mObserver, this.mCurrentPackage.packageName, -2001);
                    executeNextState(BackupState.RUNNING_QUEUE);
                    BackupManagerService.this.mWakelock.setWorkSource(null);
                    if (this.mStatus != 0) {
                        nextState = BackupState.RUNNING_QUEUE;
                        this.mAgentBinder = null;
                        if (this.mStatus == -1003) {
                            BackupManagerService.this.dataChangedImpl(request.packageName);
                            this.mStatus = 0;
                            if (this.mQueue.isEmpty()) {
                                nextState = BackupState.FINAL;
                            }
                            BackupManagerService.sendBackupOnPackageResult(this.mObserver, this.mCurrentPackage.packageName, -1003);
                        } else if (this.mStatus == -1004) {
                            this.mStatus = 0;
                            BackupManagerService.sendBackupOnPackageResult(this.mObserver, this.mCurrentPackage.packageName, -2002);
                        } else {
                            revertAndEndBackup();
                            nextState = BackupState.FINAL;
                        }
                        executeNextState(nextState);
                    } else {
                        BackupManagerService.this.addBackupTrace("expecting completion/timeout callback");
                    }
                } else if (BackupManagerService.appGetsFullBackup(this.mCurrentPackage)) {
                    Slog.i(TAG, "Package " + request.packageName + " requests full-data rather than key/value; skipping");
                    BackupManagerService.this.addBackupTrace("skipping - fullBackupOnly, completion is noop");
                    BackupManagerService.sendBackupOnPackageResult(this.mObserver, this.mCurrentPackage.packageName, -2001);
                    executeNextState(BackupState.RUNNING_QUEUE);
                    BackupManagerService.this.mWakelock.setWorkSource(null);
                    if (this.mStatus != 0) {
                        nextState = BackupState.RUNNING_QUEUE;
                        this.mAgentBinder = null;
                        if (this.mStatus == -1003) {
                            BackupManagerService.this.dataChangedImpl(request.packageName);
                            this.mStatus = 0;
                            if (this.mQueue.isEmpty()) {
                                nextState = BackupState.FINAL;
                            }
                            BackupManagerService.sendBackupOnPackageResult(this.mObserver, this.mCurrentPackage.packageName, -1003);
                        } else if (this.mStatus == -1004) {
                            this.mStatus = 0;
                            BackupManagerService.sendBackupOnPackageResult(this.mObserver, this.mCurrentPackage.packageName, -2002);
                        } else {
                            revertAndEndBackup();
                            nextState = BackupState.FINAL;
                        }
                        executeNextState(nextState);
                    } else {
                        BackupManagerService.this.addBackupTrace("expecting completion/timeout callback");
                    }
                } else if (BackupManagerService.appIsStopped(this.mCurrentPackage.applicationInfo)) {
                    BackupManagerService.this.addBackupTrace("skipping - stopped");
                    BackupManagerService.sendBackupOnPackageResult(this.mObserver, this.mCurrentPackage.packageName, -2001);
                    executeNextState(BackupState.RUNNING_QUEUE);
                    BackupManagerService.this.mWakelock.setWorkSource(null);
                    if (this.mStatus != 0) {
                        nextState = BackupState.RUNNING_QUEUE;
                        this.mAgentBinder = null;
                        if (this.mStatus == -1003) {
                            BackupManagerService.this.dataChangedImpl(request.packageName);
                            this.mStatus = 0;
                            if (this.mQueue.isEmpty()) {
                                nextState = BackupState.FINAL;
                            }
                            BackupManagerService.sendBackupOnPackageResult(this.mObserver, this.mCurrentPackage.packageName, -1003);
                        } else if (this.mStatus == -1004) {
                            this.mStatus = 0;
                            BackupManagerService.sendBackupOnPackageResult(this.mObserver, this.mCurrentPackage.packageName, -2002);
                        } else {
                            revertAndEndBackup();
                            nextState = BackupState.FINAL;
                        }
                        executeNextState(nextState);
                    } else {
                        BackupManagerService.this.addBackupTrace("expecting completion/timeout callback");
                    }
                } else {
                    try {
                        boolean z;
                        BackupManagerService.this.mWakelock.setWorkSource(new WorkSource(this.mCurrentPackage.applicationInfo.uid));
                        IBackupAgent agent = BackupManagerService.this.bindToAgentSynchronous(this.mCurrentPackage.applicationInfo, 0);
                        BackupManagerService backupManagerService = BackupManagerService.this;
                        StringBuilder append = new StringBuilder().append("agent bound; a? = ");
                        if (agent != null) {
                            z = true;
                        } else {
                            z = false;
                        }
                        backupManagerService.addBackupTrace(append.append(z).toString());
                        if (agent != null) {
                            this.mAgentBinder = agent;
                            this.mStatus = invokeAgentForBackup(request.packageName, agent, this.mTransport);
                        } else {
                            this.mStatus = -1003;
                        }
                    } catch (SecurityException ex) {
                        Slog.d(TAG, "error in bind/backup", ex);
                        this.mStatus = -1003;
                        BackupManagerService.this.addBackupTrace("agent SE");
                    }
                    BackupManagerService.this.mWakelock.setWorkSource(null);
                    if (this.mStatus != 0) {
                        nextState = BackupState.RUNNING_QUEUE;
                        this.mAgentBinder = null;
                        if (this.mStatus == -1003) {
                            BackupManagerService.this.dataChangedImpl(request.packageName);
                            this.mStatus = 0;
                            if (this.mQueue.isEmpty()) {
                                nextState = BackupState.FINAL;
                            }
                            BackupManagerService.sendBackupOnPackageResult(this.mObserver, this.mCurrentPackage.packageName, -1003);
                        } else if (this.mStatus == -1004) {
                            this.mStatus = 0;
                            BackupManagerService.sendBackupOnPackageResult(this.mObserver, this.mCurrentPackage.packageName, -2002);
                        } else {
                            revertAndEndBackup();
                            nextState = BackupState.FINAL;
                        }
                        executeNextState(nextState);
                    } else {
                        BackupManagerService.this.addBackupTrace("expecting completion/timeout callback");
                    }
                }
            } catch (NameNotFoundException e) {
                Slog.d(TAG, "Package does not exist; skipping");
                BackupManagerService.this.addBackupTrace("no such package");
                this.mStatus = -1004;
                BackupManagerService.this.mWakelock.setWorkSource(null);
                if (this.mStatus != 0) {
                    nextState = BackupState.RUNNING_QUEUE;
                    this.mAgentBinder = null;
                    if (this.mStatus == -1003) {
                        BackupManagerService.this.dataChangedImpl(request.packageName);
                        this.mStatus = 0;
                        if (this.mQueue.isEmpty()) {
                            nextState = BackupState.FINAL;
                        }
                        BackupManagerService.sendBackupOnPackageResult(this.mObserver, this.mCurrentPackage.packageName, -1003);
                    } else if (this.mStatus == -1004) {
                        this.mStatus = 0;
                        BackupManagerService.sendBackupOnPackageResult(this.mObserver, this.mCurrentPackage.packageName, -2002);
                    } else {
                        revertAndEndBackup();
                        nextState = BackupState.FINAL;
                    }
                    executeNextState(nextState);
                } else {
                    BackupManagerService.this.addBackupTrace("expecting completion/timeout callback");
                }
            } catch (Throwable th) {
                BackupManagerService.this.mWakelock.setWorkSource(null);
                if (this.mStatus != 0) {
                    nextState = BackupState.RUNNING_QUEUE;
                    this.mAgentBinder = null;
                    if (this.mStatus == -1003) {
                        BackupManagerService.this.dataChangedImpl(request.packageName);
                        this.mStatus = 0;
                        if (this.mQueue.isEmpty()) {
                            nextState = BackupState.FINAL;
                        }
                        BackupManagerService.sendBackupOnPackageResult(this.mObserver, this.mCurrentPackage.packageName, -1003);
                    } else if (this.mStatus == -1004) {
                        this.mStatus = 0;
                        BackupManagerService.sendBackupOnPackageResult(this.mObserver, this.mCurrentPackage.packageName, -2002);
                    } else {
                        revertAndEndBackup();
                        nextState = BackupState.FINAL;
                    }
                    executeNextState(nextState);
                } else {
                    BackupManagerService.this.addBackupTrace("expecting completion/timeout callback");
                }
            }
        }

        void finalizeBackup() {
            BackupManagerService.this.addBackupTrace("finishing");
            if (!(this.mJournal == null || this.mJournal.delete())) {
                Slog.e(TAG, "Unable to remove backup journal file " + this.mJournal);
            }
            if (BackupManagerService.this.mCurrentToken == 0 && this.mStatus == 0) {
                BackupManagerService.this.addBackupTrace("success; recording token");
                try {
                    BackupManagerService.this.mCurrentToken = this.mTransport.getCurrentRestoreSet();
                    BackupManagerService.this.writeRestoreTokens();
                } catch (RemoteException e) {
                    BackupManagerService.this.addBackupTrace("transport threw returning token");
                }
            }
            synchronized (BackupManagerService.this.mQueueLock) {
                BackupManagerService.this.mBackupRunning = false;
                if (this.mStatus == JobSchedulerShellCommand.CMD_ERR_NO_JOB) {
                    BackupManagerService.this.addBackupTrace("init required; rerunning");
                    try {
                        String name = BackupManagerService.this.getTransportName(this.mTransport);
                        if (name != null) {
                            BackupManagerService.this.mPendingInits.add(name);
                        } else if (BackupManagerService.DEBUG) {
                            Slog.w(TAG, "Couldn't find name of transport " + this.mTransport + " for init");
                        }
                    } catch (Exception e2) {
                        Slog.w(TAG, "Failed to query transport name heading for init", e2);
                    }
                    clearMetadata();
                    BackupManagerService.this.backupNow();
                }
            }
            BackupManagerService.this.clearBackupTrace();
            if (this.mStatus != 0 || this.mPendingFullBackups == null || this.mPendingFullBackups.isEmpty()) {
                switch (this.mStatus) {
                    case JobSchedulerShellCommand.CMD_ERR_NO_JOB /*-1001*/:
                        BackupManagerService.sendBackupFinished(this.mObserver, JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE);
                        break;
                    case 0:
                        BackupManagerService.sendBackupFinished(this.mObserver, 0);
                        break;
                    default:
                        BackupManagerService.sendBackupFinished(this.mObserver, JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE);
                        break;
                }
            }
            Slog.d(TAG, "Starting full backups for: " + this.mPendingFullBackups);
            String[] fullBackups = (String[]) this.mPendingFullBackups.toArray(new String[this.mPendingFullBackups.size()]);
            PerformFullTransportBackupTask task = new PerformFullTransportBackupTask(null, fullBackups, false, null, new CountDownLatch(1), this.mObserver, this.mUserInitiated);
            BackupManagerService.this.mWakelock.acquire();
            new Thread(task, "full-transport-requested").start();
            Slog.i(BackupManagerService.TAG, "K/V backup pass finished.");
            BackupManagerService.this.mWakelock.release();
        }

        void clearMetadata() {
            File pmState = new File(this.mStateDir, BackupManagerService.PACKAGE_MANAGER_SENTINEL);
            if (pmState.exists()) {
                pmState.delete();
            }
        }

        int invokeAgentForBackup(String packageName, IBackupAgent agent, IBackupTransport transport) {
            if (BackupManagerService.DEBUG) {
                Slog.d(TAG, "invokeAgentForBackup on " + packageName);
            }
            BackupManagerService.this.addBackupTrace("invoking " + packageName);
            this.mSavedStateName = new File(this.mStateDir, packageName);
            this.mBackupDataName = new File(BackupManagerService.this.mDataDir, packageName + ".data");
            this.mNewStateName = new File(this.mStateDir, packageName + ".new");
            this.mSavedState = null;
            this.mBackupData = null;
            this.mNewState = null;
            int token = BackupManagerService.this.generateToken();
            try {
                if (packageName.equals(BackupManagerService.PACKAGE_MANAGER_SENTINEL)) {
                    this.mCurrentPackage = new PackageInfo();
                    this.mCurrentPackage.packageName = packageName;
                }
                this.mSavedState = ParcelFileDescriptor.open(this.mSavedStateName, 402653184);
                this.mBackupData = ParcelFileDescriptor.open(this.mBackupDataName, 1006632960);
                if (!SELinux.restorecon(this.mBackupDataName)) {
                    Slog.e(TAG, "SELinux restorecon failed on " + this.mBackupDataName);
                }
                this.mNewState = ParcelFileDescriptor.open(this.mNewStateName, 1006632960);
                BackupManagerService.this.addBackupTrace("setting timeout");
                BackupManagerService.this.prepareOperationTimeout(token, 30000, this);
                BackupManagerService.this.addBackupTrace("calling agent doBackup()");
                agent.doBackup(this.mSavedState, this.mBackupData, this.mNewState, token, BackupManagerService.this.mBackupManagerBinder);
                BackupManagerService.this.addBackupTrace("invoke success");
                return 0;
            } catch (Exception e) {
                Slog.e(TAG, "Error invoking for backup on " + packageName);
                BackupManagerService.this.addBackupTrace("exception: " + e);
                EventLog.writeEvent(EventLogTags.BACKUP_AGENT_FAILURE, new Object[]{packageName, e.toString()});
                agentErrorCleanup();
                return -1003;
            }
        }

        public void failAgent(IBackupAgent agent, String message) {
            try {
                agent.fail(message);
            } catch (Exception e) {
                Slog.w(TAG, "Error conveying failure to " + this.mCurrentPackage.packageName);
            }
        }

        private String SHA1Checksum(byte[] input) {
            try {
                byte[] checksum = MessageDigest.getInstance("SHA-1").digest(input);
                StringBuffer sb = new StringBuffer(checksum.length * 2);
                for (byte toHexString : checksum) {
                    sb.append(Integer.toHexString(toHexString));
                }
                return sb.toString();
            } catch (NoSuchAlgorithmException e) {
                Slog.e(TAG, "Unable to use SHA-1!");
                return "00";
            }
        }

        private void writeWidgetPayloadIfAppropriate(FileDescriptor fd, String pkgName) throws IOException {
            Throwable th;
            Throwable th2;
            Throwable th3;
            Throwable th4;
            byte[] widgetState = AppWidgetBackupBridge.getWidgetState(pkgName, 0);
            File widgetFile = new File(this.mStateDir, pkgName + "_widget");
            boolean priorStateExists = widgetFile.exists();
            if (priorStateExists || widgetState != null) {
                String str = null;
                if (widgetState != null) {
                    str = SHA1Checksum(widgetState);
                    if (priorStateExists) {
                        th = null;
                        FileInputStream fileInputStream = null;
                        DataInputStream dataInputStream = null;
                        try {
                            FileInputStream fin = new FileInputStream(widgetFile);
                            try {
                                DataInputStream in = new DataInputStream(fin);
                                try {
                                    String priorChecksum = in.readUTF();
                                    if (in != null) {
                                        try {
                                            in.close();
                                        } catch (Throwable th5) {
                                            th = th5;
                                        }
                                    }
                                    if (fin != null) {
                                        try {
                                            fin.close();
                                        } catch (Throwable th6) {
                                            th2 = th6;
                                            if (th != null) {
                                                if (th != th2) {
                                                    th.addSuppressed(th2);
                                                    th2 = th;
                                                }
                                            }
                                        }
                                    }
                                    th2 = th;
                                    if (th2 != null) {
                                        throw th2;
                                    } else if (Objects.equals(str, priorChecksum)) {
                                        return;
                                    }
                                } catch (Throwable th7) {
                                    th2 = th7;
                                    dataInputStream = in;
                                    fileInputStream = fin;
                                    if (dataInputStream != null) {
                                        try {
                                            dataInputStream.close();
                                        } catch (Throwable th8) {
                                            th4 = th8;
                                            if (th != null) {
                                                if (th != th4) {
                                                    th.addSuppressed(th4);
                                                    th4 = th;
                                                }
                                            }
                                        }
                                    }
                                    th4 = th;
                                    if (fileInputStream != null) {
                                        try {
                                            fileInputStream.close();
                                        } catch (Throwable th9) {
                                            th = th9;
                                            if (th4 != null) {
                                                if (th4 != th) {
                                                    th4.addSuppressed(th);
                                                    th = th4;
                                                }
                                            }
                                        }
                                    }
                                    th = th4;
                                    if (th != null) {
                                        throw th;
                                    }
                                    throw th2;
                                }
                            } catch (Throwable th10) {
                                th2 = th10;
                                fileInputStream = fin;
                                if (dataInputStream != null) {
                                    dataInputStream.close();
                                }
                                th4 = th;
                                if (fileInputStream != null) {
                                    fileInputStream.close();
                                }
                                th = th4;
                                if (th != null) {
                                    throw th2;
                                }
                                throw th;
                            }
                        } catch (Throwable th11) {
                            th2 = th11;
                            if (dataInputStream != null) {
                                dataInputStream.close();
                            }
                            th4 = th;
                            if (fileInputStream != null) {
                                fileInputStream.close();
                            }
                            th = th4;
                            if (th != null) {
                                throw th;
                            }
                            throw th2;
                        }
                    }
                }
                BackupDataOutput out = new BackupDataOutput(fd);
                if (widgetState != null) {
                    th = null;
                    FileOutputStream fileOutputStream = null;
                    DataOutputStream dataOutputStream = null;
                    try {
                        FileOutputStream fout = new FileOutputStream(widgetFile);
                        try {
                            DataOutputStream stateOut = new DataOutputStream(fout);
                            try {
                                stateOut.writeUTF(str);
                                if (stateOut != null) {
                                    try {
                                        stateOut.close();
                                    } catch (Throwable th12) {
                                        th = th12;
                                    }
                                }
                                if (fout != null) {
                                    try {
                                        fout.close();
                                    } catch (Throwable th13) {
                                        th2 = th13;
                                        if (th != null) {
                                            if (th != th2) {
                                                th.addSuppressed(th2);
                                                th2 = th;
                                            }
                                        }
                                    }
                                }
                                th2 = th;
                                if (th2 != null) {
                                    throw th2;
                                }
                                out.writeEntityHeader(BackupManagerService.KEY_WIDGET_STATE, widgetState.length);
                                out.writeEntityData(widgetState, widgetState.length);
                            } catch (Throwable th14) {
                                th2 = th14;
                                dataOutputStream = stateOut;
                                fileOutputStream = fout;
                                if (dataOutputStream != null) {
                                    try {
                                        dataOutputStream.close();
                                    } catch (Throwable th15) {
                                        th4 = th15;
                                        if (th != null) {
                                            if (th != th4) {
                                                th.addSuppressed(th4);
                                                th4 = th;
                                            }
                                        }
                                    }
                                }
                                th4 = th;
                                if (fileOutputStream != null) {
                                    try {
                                        fileOutputStream.close();
                                    } catch (Throwable th16) {
                                        th = th16;
                                        if (th4 != null) {
                                            if (th4 != th) {
                                                th4.addSuppressed(th);
                                                th = th4;
                                            }
                                        }
                                    }
                                }
                                th = th4;
                                if (th != null) {
                                    throw th;
                                }
                                throw th2;
                            }
                        } catch (Throwable th17) {
                            th2 = th17;
                            fileOutputStream = fout;
                            if (dataOutputStream != null) {
                                dataOutputStream.close();
                            }
                            th4 = th;
                            if (fileOutputStream != null) {
                                fileOutputStream.close();
                            }
                            th = th4;
                            if (th != null) {
                                throw th2;
                            }
                            throw th;
                        }
                    } catch (Throwable th18) {
                        th2 = th18;
                        if (dataOutputStream != null) {
                            dataOutputStream.close();
                        }
                        th4 = th;
                        if (fileOutputStream != null) {
                            fileOutputStream.close();
                        }
                        th = th4;
                        if (th != null) {
                            throw th;
                        }
                        throw th2;
                    }
                }
                out.writeEntityHeader(BackupManagerService.KEY_WIDGET_STATE, -1);
                widgetFile.delete();
            }
        }

        public void operationComplete(long unusedResult) {
            if (this.mBackupData == null) {
                BackupManagerService.this.addBackupTrace("late opComplete; curPkg = " + (this.mCurrentPackage != null ? this.mCurrentPackage.packageName : "[none]"));
                return;
            }
            BackupState nextState;
            String pkgName = this.mCurrentPackage.packageName;
            long filepos = this.mBackupDataName.length();
            FileDescriptor fd = this.mBackupData.getFileDescriptor();
            ParcelFileDescriptor readFd;
            try {
                if (this.mCurrentPackage.applicationInfo != null && (this.mCurrentPackage.applicationInfo.flags & 1) == 0) {
                    readFd = ParcelFileDescriptor.open(this.mBackupDataName, 268435456);
                    BackupDataInput in = new BackupDataInput(readFd.getFileDescriptor());
                    while (in.readNextHeader()) {
                        String key = in.getKey();
                        if (key == null || key.charAt(0) < '＀') {
                            in.skipEntityData();
                        } else {
                            failAgent(this.mAgentBinder, "Illegal backup key: " + key);
                            BackupManagerService.this.addBackupTrace("illegal key " + key + " from " + pkgName);
                            EventLog.writeEvent(EventLogTags.BACKUP_AGENT_FAILURE, new Object[]{pkgName, "bad key"});
                            BackupManagerService.this.mBackupHandler.removeMessages(7);
                            BackupManagerService.sendBackupOnPackageResult(this.mObserver, pkgName, -1003);
                            agentErrorCleanup();
                            if (readFd != null) {
                                readFd.close();
                            }
                            return;
                        }
                    }
                    if (readFd != null) {
                        readFd.close();
                    }
                }
                writeWidgetPayloadIfAppropriate(fd, pkgName);
            } catch (IOException e) {
                Slog.w(TAG, "Unable to save widget state for " + pkgName);
                try {
                    Os.ftruncate(fd, filepos);
                } catch (ErrnoException e2) {
                    Slog.w(TAG, "Unable to roll back!");
                }
            } catch (Throwable th) {
                if (readFd != null) {
                    readFd.close();
                }
            }
            BackupManagerService.this.mBackupHandler.removeMessages(7);
            clearAgentState();
            BackupManagerService.this.addBackupTrace("operation complete");
            ParcelFileDescriptor parcelFileDescriptor = null;
            this.mStatus = 0;
            long j = 0;
            try {
                j = this.mBackupDataName.length();
                if (j > 0) {
                    if (this.mStatus == 0) {
                        parcelFileDescriptor = ParcelFileDescriptor.open(this.mBackupDataName, 268435456);
                        BackupManagerService.this.addBackupTrace("sending data to transport");
                        this.mStatus = this.mTransport.performBackup(this.mCurrentPackage, parcelFileDescriptor, this.mUserInitiated ? 1 : 0);
                    }
                    BackupManagerService.this.addBackupTrace("data delivered: " + this.mStatus);
                    if (this.mStatus == 0) {
                        BackupManagerService.this.addBackupTrace("finishing op on transport");
                        this.mStatus = this.mTransport.finishBackup();
                        BackupManagerService.this.addBackupTrace("finished: " + this.mStatus);
                    } else if (this.mStatus == -1002) {
                        BackupManagerService.this.addBackupTrace("transport rejected package");
                    }
                } else {
                    BackupManagerService.this.addBackupTrace("no data to send");
                }
                if (this.mStatus == 0) {
                    this.mBackupDataName.delete();
                    this.mNewStateName.renameTo(this.mSavedStateName);
                    BackupManagerService.sendBackupOnPackageResult(this.mObserver, pkgName, 0);
                    EventLog.writeEvent(EventLogTags.BACKUP_PACKAGE, new Object[]{pkgName, Long.valueOf(j)});
                    BackupManagerService.this.logBackupComplete(pkgName);
                } else if (this.mStatus == -1002) {
                    this.mBackupDataName.delete();
                    this.mNewStateName.delete();
                    BackupManagerService.sendBackupOnPackageResult(this.mObserver, pkgName, JobSchedulerShellCommand.CMD_ERR_CONSTRAINTS);
                    EventLogTags.writeBackupAgentFailure(pkgName, "Transport rejected");
                } else if (this.mStatus == -1005) {
                    BackupManagerService.sendBackupOnPackageResult(this.mObserver, pkgName, -1005);
                    EventLog.writeEvent(EventLogTags.BACKUP_QUOTA_EXCEEDED, pkgName);
                } else {
                    BackupManagerService.sendBackupOnPackageResult(this.mObserver, pkgName, JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE);
                    EventLog.writeEvent(EventLogTags.BACKUP_TRANSPORT_FAILURE, pkgName);
                }
                if (parcelFileDescriptor != null) {
                    try {
                        parcelFileDescriptor.close();
                    } catch (IOException e3) {
                    }
                }
            } catch (Exception e4) {
                BackupManagerService.sendBackupOnPackageResult(this.mObserver, pkgName, JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE);
                Slog.e(TAG, "Transport error backing up " + pkgName, e4);
                EventLog.writeEvent(EventLogTags.BACKUP_TRANSPORT_FAILURE, pkgName);
                this.mStatus = JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE;
                if (parcelFileDescriptor != null) {
                    try {
                        parcelFileDescriptor.close();
                    } catch (IOException e5) {
                    }
                }
            } catch (Throwable th2) {
                if (parcelFileDescriptor != null) {
                    try {
                        parcelFileDescriptor.close();
                    } catch (IOException e6) {
                    }
                }
            }
            if (this.mStatus == 0 || this.mStatus == -1002) {
                nextState = this.mQueue.isEmpty() ? BackupState.FINAL : BackupState.RUNNING_QUEUE;
            } else if (this.mStatus == -1005) {
                if (this.mAgentBinder != null) {
                    try {
                        this.mAgentBinder.doQuotaExceeded(j, this.mTransport.getBackupQuota(this.mCurrentPackage.packageName, false));
                    } catch (RemoteException e7) {
                        Slog.e(TAG, "Unable to contact backup agent for quota exceeded");
                    }
                }
                nextState = this.mQueue.isEmpty() ? BackupState.FINAL : BackupState.RUNNING_QUEUE;
            } else {
                revertAndEndBackup();
                nextState = BackupState.FINAL;
            }
            executeNextState(nextState);
        }

        public void handleTimeout() {
            Slog.e(TAG, "Timeout backing up " + this.mCurrentPackage.packageName);
            EventLog.writeEvent(EventLogTags.BACKUP_AGENT_FAILURE, new Object[]{this.mCurrentPackage.packageName, "timeout"});
            BackupManagerService.this.addBackupTrace("timeout of " + this.mCurrentPackage.packageName);
            agentErrorCleanup();
            BackupManagerService.this.dataChangedImpl(this.mCurrentPackage.packageName);
        }

        void revertAndEndBackup() {
            long delay;
            BackupManagerService.this.addBackupTrace("transport error; reverting");
            try {
                delay = this.mTransport.requestBackupTime();
            } catch (Exception e) {
                Slog.w(TAG, "Unable to contact transport for recommended backoff");
                delay = 0;
            }
            KeyValueBackupJob.schedule(BackupManagerService.this.mContext, delay);
            for (BackupRequest request : this.mOriginalQueue) {
                BackupManagerService.this.dataChangedImpl(request.packageName);
            }
        }

        void agentErrorCleanup() {
            BackupState backupState;
            this.mBackupDataName.delete();
            this.mNewStateName.delete();
            clearAgentState();
            if (this.mQueue.isEmpty()) {
                backupState = BackupState.FINAL;
            } else {
                backupState = BackupState.RUNNING_QUEUE;
            }
            executeNextState(backupState);
        }

        void clearAgentState() {
            try {
                if (this.mSavedState != null) {
                    this.mSavedState.close();
                }
            } catch (IOException e) {
            }
            try {
                if (this.mBackupData != null) {
                    this.mBackupData.close();
                }
            } catch (IOException e2) {
            }
            try {
                if (this.mNewState != null) {
                    this.mNewState.close();
                }
            } catch (IOException e3) {
            }
            synchronized (BackupManagerService.this.mCurrentOpLock) {
                BackupManagerService.this.mCurrentOperations.clear();
                this.mNewState = null;
                this.mBackupData = null;
                this.mSavedState = null;
            }
            if (this.mCurrentPackage.applicationInfo != null) {
                BackupManagerService.this.addBackupTrace("unbinding " + this.mCurrentPackage.packageName);
                try {
                    BackupManagerService.this.mActivityManager.unbindBackupAgent(this.mCurrentPackage.applicationInfo);
                } catch (RemoteException e4) {
                }
            }
        }

        void executeNextState(BackupState nextState) {
            BackupManagerService.this.addBackupTrace("executeNextState => " + nextState);
            this.mCurrentState = nextState;
            BackupManagerService.this.mBackupHandler.sendMessage(BackupManagerService.this.mBackupHandler.obtainMessage(20, this));
        }
    }

    class PerformClearTask implements Runnable {
        PackageInfo mPackage;
        IBackupTransport mTransport;

        PerformClearTask(IBackupTransport transport, PackageInfo packageInfo) {
            this.mTransport = transport;
            this.mPackage = packageInfo;
        }

        public void run() {
            try {
                new File(new File(BackupManagerService.this.mBaseStateDir, this.mTransport.transportDirName()), this.mPackage.packageName).delete();
                this.mTransport.clearBackupData(this.mPackage);
            } catch (RemoteException e) {
            } catch (Exception e2) {
                Slog.e(BackupManagerService.TAG, "Transport threw attempting to clear data for " + this.mPackage);
            } finally {
                try {
                    this.mTransport.finishBackup();
                } catch (RemoteException e3) {
                }
                BackupManagerService.this.mWakelock.release();
            }
        }
    }

    class PerformFullTransportBackupTask extends FullBackupTask {
        static final String TAG = "PFTBT";
        IBackupObserver mBackupObserver;
        FullBackupJob mJob;
        AtomicBoolean mKeepRunning = new AtomicBoolean(true);
        CountDownLatch mLatch;
        ArrayList<PackageInfo> mPackages;
        boolean mUpdateSchedule;
        boolean mUserInitiated;

        class SinglePackageBackupPreflight implements BackupRestoreTask, FullBackupPreflight {
            final CountDownLatch mLatch = new CountDownLatch(1);
            final AtomicLong mResult = new AtomicLong(-1003);
            final IBackupTransport mTransport;

            public SinglePackageBackupPreflight(IBackupTransport transport) {
                this.mTransport = transport;
            }

            public int preflightFullBackup(PackageInfo pkg, IBackupAgent agent) {
                int result;
                try {
                    int token = BackupManagerService.this.generateToken();
                    BackupManagerService.this.prepareOperationTimeout(token, BackupManagerService.TIMEOUT_FULL_BACKUP_INTERVAL, this);
                    BackupManagerService.this.addBackupTrace("preflighting");
                    agent.doMeasureFullBackup(token, BackupManagerService.this.mBackupManagerBinder);
                    this.mLatch.await(BackupManagerService.TIMEOUT_FULL_BACKUP_INTERVAL, TimeUnit.MILLISECONDS);
                    long totalSize = this.mResult.get();
                    if (totalSize < 0) {
                        return (int) totalSize;
                    }
                    result = this.mTransport.checkFullBackupSize(totalSize);
                    if (result == -1005) {
                        agent.doQuotaExceeded(totalSize, this.mTransport.getBackupQuota(pkg.packageName, true));
                    }
                    return result;
                } catch (Exception e) {
                    Slog.w(PerformFullTransportBackupTask.TAG, "Exception preflighting " + pkg.packageName + ": " + e.getMessage());
                    result = -1003;
                }
            }

            public void execute() {
            }

            public void operationComplete(long result) {
                this.mResult.set(result);
                this.mLatch.countDown();
            }

            public void handleTimeout() {
                this.mResult.set(-1003);
                this.mLatch.countDown();
            }

            public long getExpectedSizeOrErrorCode() {
                try {
                    this.mLatch.await(BackupManagerService.TIMEOUT_FULL_BACKUP_INTERVAL, TimeUnit.MILLISECONDS);
                    return this.mResult.get();
                } catch (InterruptedException e) {
                    return -1;
                }
            }
        }

        class SinglePackageBackupRunner implements Runnable, BackupRestoreTask {
            final CountDownLatch mBackupLatch = new CountDownLatch(1);
            private volatile int mBackupResult = -1003;
            private FullBackupEngine mEngine;
            final ParcelFileDescriptor mOutput;
            final FullBackupPreflight mPreflight;
            final CountDownLatch mPreflightLatch = new CountDownLatch(1);
            private volatile int mPreflightResult = -1003;
            final PackageInfo mTarget;

            SinglePackageBackupRunner(ParcelFileDescriptor output, PackageInfo target, IBackupTransport transport) throws IOException {
                this.mOutput = ParcelFileDescriptor.dup(output.getFileDescriptor());
                this.mTarget = target;
                this.mPreflight = new SinglePackageBackupPreflight(transport);
            }

            public void run() {
                this.mEngine = new FullBackupEngine(new FileOutputStream(this.mOutput.getFileDescriptor()), this.mPreflight, this.mTarget, false, this);
                try {
                    this.mPreflightResult = this.mEngine.preflightCheck();
                    this.mPreflightLatch.countDown();
                    if (this.mPreflightResult == 0) {
                        this.mBackupResult = this.mEngine.backupOnePackage();
                    }
                    this.mBackupLatch.countDown();
                    try {
                        this.mOutput.close();
                    } catch (IOException e) {
                        Slog.w(PerformFullTransportBackupTask.TAG, "Error closing transport pipe in runner");
                    }
                } catch (Exception e2) {
                    try {
                        Slog.e(PerformFullTransportBackupTask.TAG, "Exception during full package backup of " + this.mTarget.packageName);
                        try {
                            this.mOutput.close();
                        } catch (IOException e3) {
                            Slog.w(PerformFullTransportBackupTask.TAG, "Error closing transport pipe in runner");
                        }
                    } finally {
                        this.mBackupLatch.countDown();
                        try {
                            this.mOutput.close();
                        } catch (IOException e4) {
                            Slog.w(PerformFullTransportBackupTask.TAG, "Error closing transport pipe in runner");
                        }
                    }
                } catch (Throwable th) {
                    this.mPreflightLatch.countDown();
                }
            }

            public void sendQuotaExceeded(long backupDataBytes, long quotaBytes) {
                this.mEngine.sendQuotaExceeded(backupDataBytes, quotaBytes);
            }

            long getPreflightResultBlocking() {
                try {
                    this.mPreflightLatch.await(BackupManagerService.TIMEOUT_FULL_BACKUP_INTERVAL, TimeUnit.MILLISECONDS);
                    if (this.mPreflightResult == 0) {
                        return this.mPreflight.getExpectedSizeOrErrorCode();
                    }
                    return (long) this.mPreflightResult;
                } catch (InterruptedException e) {
                    return -1003;
                }
            }

            int getBackupResultBlocking() {
                try {
                    this.mBackupLatch.await(BackupManagerService.TIMEOUT_FULL_BACKUP_INTERVAL, TimeUnit.MILLISECONDS);
                    return this.mBackupResult;
                } catch (InterruptedException e) {
                    return -1003;
                }
            }

            public void execute() {
            }

            public void operationComplete(long result) {
            }

            public void handleTimeout() {
                if (BackupManagerService.DEBUG) {
                    Slog.w(PerformFullTransportBackupTask.TAG, "Full backup timeout of " + this.mTarget.packageName);
                }
                BackupManagerService.this.tearDownAgentAndKill(this.mTarget.applicationInfo);
            }
        }

        PerformFullTransportBackupTask(IFullBackupRestoreObserver observer, String[] whichPackages, boolean updateSchedule, FullBackupJob runningJob, CountDownLatch latch, IBackupObserver backupObserver, boolean userInitiated) {
            super(observer);
            this.mUpdateSchedule = updateSchedule;
            this.mLatch = latch;
            this.mJob = runningJob;
            this.mPackages = new ArrayList(whichPackages.length);
            this.mBackupObserver = backupObserver;
            this.mUserInitiated = userInitiated;
            for (String pkg : whichPackages) {
                try {
                    PackageInfo info = BackupManagerService.this.mPackageManager.getPackageInfo(pkg, 64);
                    if (!BackupManagerService.appIsEligibleForBackup(info.applicationInfo)) {
                        BackupManagerService.sendBackupOnPackageResult(this.mBackupObserver, pkg, -2001);
                    } else if (!BackupManagerService.appGetsFullBackup(info)) {
                        BackupManagerService.sendBackupOnPackageResult(this.mBackupObserver, pkg, -2001);
                    } else if (BackupManagerService.appIsStopped(info.applicationInfo)) {
                        BackupManagerService.sendBackupOnPackageResult(this.mBackupObserver, pkg, -2001);
                    } else {
                        this.mPackages.add(info);
                    }
                } catch (NameNotFoundException e) {
                    Slog.i(TAG, "Requested package " + pkg + " not found; ignoring");
                }
            }
        }

        public void setRunning(boolean running) {
            this.mKeepRunning.set(running);
        }

        public void run() {
            ParcelFileDescriptor[] parcelFileDescriptorArr = null;
            ParcelFileDescriptor[] parcelFileDescriptorArr2 = null;
            long backoff = 0;
            try {
                if (BackupManagerService.this.mEnabled && BackupManagerService.this.mProvisioned) {
                    IBackupTransport transport = BackupManagerService.this.getTransport(BackupManagerService.this.mCurrentTransport);
                    if (transport == null) {
                        Slog.w(TAG, "Transport not present; full data backup not performed");
                        if (BackupManagerService.DEBUG) {
                            Slog.i(TAG, "Full backup completed with status: " + JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE);
                        }
                        BackupManagerService.sendBackupFinished(this.mBackupObserver, JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE);
                        cleanUpPipes(null);
                        cleanUpPipes(null);
                        if (this.mJob != null) {
                            this.mJob.finishBackupPass();
                        }
                        synchronized (BackupManagerService.this.mQueueLock) {
                            BackupManagerService.this.mRunningFullBackupTask = null;
                        }
                        this.mLatch.countDown();
                        if (this.mUpdateSchedule) {
                            BackupManagerService.this.scheduleNextFullBackupJob(0);
                        }
                        Slog.i(BackupManagerService.TAG, "Full data backup pass finished.");
                        BackupManagerService.this.mWakelock.release();
                        return;
                    }
                    int N = this.mPackages.size();
                    byte[] buffer = new byte[DumpState.DUMP_PREFERRED_XML];
                    for (int i = 0; i < N; i++) {
                        PackageInfo currentPackage = (PackageInfo) this.mPackages.get(i);
                        String packageName = currentPackage.packageName;
                        if (BackupManagerService.DEBUG) {
                            Slog.i(TAG, "Initiating full-data transport backup of " + packageName);
                        }
                        EventLog.writeEvent(EventLogTags.FULL_BACKUP_PACKAGE, packageName);
                        parcelFileDescriptorArr2 = ParcelFileDescriptor.createPipe();
                        int backupPackageStatus = transport.performFullBackup(currentPackage, parcelFileDescriptorArr2[0], this.mUserInitiated ? 1 : 0);
                        if (backupPackageStatus == 0) {
                            parcelFileDescriptorArr2[0].close();
                            parcelFileDescriptorArr2[0] = null;
                            parcelFileDescriptorArr = ParcelFileDescriptor.createPipe();
                            SinglePackageBackupRunner backupRunner = new SinglePackageBackupRunner(parcelFileDescriptorArr[1], currentPackage, transport);
                            parcelFileDescriptorArr[1].close();
                            parcelFileDescriptorArr[1] = null;
                            new Thread(backupRunner, "package-backup-bridge").start();
                            FileInputStream fileInputStream = new FileInputStream(parcelFileDescriptorArr[0].getFileDescriptor());
                            FileOutputStream fileOutputStream = new FileOutputStream(parcelFileDescriptorArr2[1].getFileDescriptor());
                            long totalRead = 0;
                            long preflightResult = backupRunner.getPreflightResultBlocking();
                            if (preflightResult < 0) {
                                backupPackageStatus = (int) preflightResult;
                            } else {
                                while (this.mKeepRunning.get()) {
                                    int nRead = fileInputStream.read(buffer);
                                    if (nRead > 0) {
                                        fileOutputStream.write(buffer, 0, nRead);
                                        backupPackageStatus = transport.sendBackupData(nRead);
                                        totalRead += (long) nRead;
                                        if (this.mBackupObserver != null && preflightResult > 0) {
                                            BackupManagerService.sendBackupOnUpdate(this.mBackupObserver, packageName, new BackupProgress(preflightResult, totalRead));
                                        }
                                    }
                                    if (nRead <= 0 || backupPackageStatus != 0) {
                                        break;
                                    }
                                }
                                if (BackupManagerService.DEBUG_SCHEDULING) {
                                    Slog.i(TAG, "Full backup task told to stop");
                                }
                                if (backupPackageStatus == -1005) {
                                    long quota = transport.getBackupQuota(packageName, true);
                                    Slog.w(TAG, "Package hit quota limit in-flight " + packageName + ": " + totalRead + " of " + quota);
                                    backupRunner.sendQuotaExceeded(totalRead, quota);
                                }
                            }
                            if (this.mKeepRunning.get()) {
                                int finishResult = transport.finishBackup();
                                if (backupPackageStatus == 0) {
                                    backupPackageStatus = finishResult;
                                }
                            } else {
                                backupPackageStatus = JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE;
                                transport.cancelFullBackup();
                            }
                            if (backupPackageStatus == 0) {
                                int backupRunnerResult = backupRunner.getBackupResultBlocking();
                                if (backupRunnerResult != 0) {
                                    backupPackageStatus = backupRunnerResult;
                                }
                            }
                            if (backupPackageStatus != 0) {
                                Slog.e(TAG, "Error " + backupPackageStatus + " backing up " + packageName);
                            }
                            backoff = transport.requestFullBackupTime();
                            if (BackupManagerService.DEBUG_SCHEDULING) {
                                Slog.i(TAG, "Transport suggested backoff=" + backoff);
                            }
                        }
                        if (this.mUpdateSchedule) {
                            BackupManagerService.this.enqueueFullBackup(packageName, System.currentTimeMillis());
                        }
                        if (backupPackageStatus == -1002) {
                            BackupManagerService.sendBackupOnPackageResult(this.mBackupObserver, packageName, JobSchedulerShellCommand.CMD_ERR_CONSTRAINTS);
                            if (BackupManagerService.DEBUG) {
                                Slog.i(TAG, "Transport rejected backup of " + packageName + ", skipping");
                            }
                            EventLog.writeEvent(EventLogTags.FULL_BACKUP_AGENT_FAILURE, new Object[]{packageName, "transport rejected"});
                        } else if (backupPackageStatus == -1005) {
                            BackupManagerService.sendBackupOnPackageResult(this.mBackupObserver, packageName, -1005);
                            if (BackupManagerService.DEBUG) {
                                Slog.i(TAG, "Transport quota exceeded for package: " + packageName);
                                EventLog.writeEvent(EventLogTags.FULL_BACKUP_QUOTA_EXCEEDED, packageName);
                            }
                        } else if (backupPackageStatus == -1003) {
                            BackupManagerService.sendBackupOnPackageResult(this.mBackupObserver, packageName, -1003);
                            Slog.w(TAG, "Application failure for package: " + packageName);
                            EventLog.writeEvent(EventLogTags.BACKUP_AGENT_FAILURE, packageName);
                            BackupManagerService.this.tearDownAgentAndKill(currentPackage.applicationInfo);
                        } else if (backupPackageStatus != 0) {
                            BackupManagerService.sendBackupOnPackageResult(this.mBackupObserver, packageName, JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE);
                            Slog.w(TAG, "Transport failed; aborting backup: " + backupPackageStatus);
                            EventLog.writeEvent(EventLogTags.FULL_BACKUP_TRANSPORT_FAILURE, new Object[0]);
                            if (BackupManagerService.DEBUG) {
                                Slog.i(TAG, "Full backup completed with status: " + JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE);
                            }
                            BackupManagerService.sendBackupFinished(this.mBackupObserver, JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE);
                            cleanUpPipes(parcelFileDescriptorArr2);
                            cleanUpPipes(parcelFileDescriptorArr);
                            if (this.mJob != null) {
                                this.mJob.finishBackupPass();
                            }
                            synchronized (BackupManagerService.this.mQueueLock) {
                                BackupManagerService.this.mRunningFullBackupTask = null;
                            }
                            this.mLatch.countDown();
                            if (this.mUpdateSchedule) {
                                BackupManagerService.this.scheduleNextFullBackupJob(backoff);
                            }
                            Slog.i(BackupManagerService.TAG, "Full data backup pass finished.");
                            BackupManagerService.this.mWakelock.release();
                            return;
                        } else {
                            BackupManagerService.sendBackupOnPackageResult(this.mBackupObserver, packageName, 0);
                            EventLog.writeEvent(EventLogTags.FULL_BACKUP_SUCCESS, packageName);
                            BackupManagerService.this.logBackupComplete(packageName);
                        }
                        cleanUpPipes(parcelFileDescriptorArr2);
                        cleanUpPipes(parcelFileDescriptorArr);
                    }
                    if (BackupManagerService.DEBUG) {
                        Slog.i(TAG, "Full backup completed with status: " + 0);
                    }
                    BackupManagerService.sendBackupFinished(this.mBackupObserver, 0);
                    cleanUpPipes(parcelFileDescriptorArr2);
                    cleanUpPipes(parcelFileDescriptorArr);
                    if (this.mJob != null) {
                        this.mJob.finishBackupPass();
                    }
                    synchronized (BackupManagerService.this.mQueueLock) {
                        BackupManagerService.this.mRunningFullBackupTask = null;
                    }
                    this.mLatch.countDown();
                    if (this.mUpdateSchedule) {
                        BackupManagerService.this.scheduleNextFullBackupJob(backoff);
                    }
                    Slog.i(BackupManagerService.TAG, "Full data backup pass finished.");
                    BackupManagerService.this.mWakelock.release();
                    return;
                }
                if (BackupManagerService.DEBUG) {
                    Slog.i(TAG, "full backup requested but e=" + BackupManagerService.this.mEnabled + " p=" + BackupManagerService.this.mProvisioned + "; ignoring");
                }
                this.mUpdateSchedule = false;
                if (BackupManagerService.DEBUG) {
                    Slog.i(TAG, "Full backup completed with status: " + -2001);
                }
                BackupManagerService.sendBackupFinished(this.mBackupObserver, -2001);
                cleanUpPipes(null);
                cleanUpPipes(null);
                if (this.mJob != null) {
                    this.mJob.finishBackupPass();
                }
                synchronized (BackupManagerService.this.mQueueLock) {
                    BackupManagerService.this.mRunningFullBackupTask = null;
                }
                this.mLatch.countDown();
                if (this.mUpdateSchedule) {
                    BackupManagerService.this.scheduleNextFullBackupJob(0);
                }
                Slog.i(BackupManagerService.TAG, "Full data backup pass finished.");
                BackupManagerService.this.mWakelock.release();
            } catch (Exception e) {
                Slog.w(TAG, "Exception trying full transport backup", e);
                if (BackupManagerService.DEBUG) {
                    Slog.i(TAG, "Full backup completed with status: " + JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE);
                }
                BackupManagerService.sendBackupFinished(this.mBackupObserver, JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE);
                cleanUpPipes(parcelFileDescriptorArr2);
                cleanUpPipes(parcelFileDescriptorArr);
                if (this.mJob != null) {
                    this.mJob.finishBackupPass();
                }
                synchronized (BackupManagerService.this.mQueueLock) {
                    BackupManagerService.this.mRunningFullBackupTask = null;
                    this.mLatch.countDown();
                    if (this.mUpdateSchedule) {
                        BackupManagerService.this.scheduleNextFullBackupJob(backoff);
                    }
                    Slog.i(BackupManagerService.TAG, "Full data backup pass finished.");
                    BackupManagerService.this.mWakelock.release();
                }
            } catch (Throwable th) {
                if (BackupManagerService.DEBUG) {
                    Slog.i(TAG, "Full backup completed with status: " + JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE);
                }
                BackupManagerService.sendBackupFinished(this.mBackupObserver, JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE);
                cleanUpPipes(parcelFileDescriptorArr2);
                cleanUpPipes(parcelFileDescriptorArr);
                if (this.mJob != null) {
                    this.mJob.finishBackupPass();
                }
                synchronized (BackupManagerService.this.mQueueLock) {
                    BackupManagerService.this.mRunningFullBackupTask = null;
                    this.mLatch.countDown();
                    if (this.mUpdateSchedule) {
                        BackupManagerService.this.scheduleNextFullBackupJob(backoff);
                    }
                    Slog.i(BackupManagerService.TAG, "Full data backup pass finished.");
                    BackupManagerService.this.mWakelock.release();
                }
            }
        }

        void cleanUpPipes(ParcelFileDescriptor[] pipes) {
            if (pipes != null) {
                ParcelFileDescriptor fd;
                if (pipes[0] != null) {
                    fd = pipes[0];
                    pipes[0] = null;
                    try {
                        fd.close();
                    } catch (IOException e) {
                        Slog.w(TAG, "Unable to close pipe!");
                    }
                }
                if (pipes[1] != null) {
                    fd = pipes[1];
                    pipes[1] = null;
                    try {
                        fd.close();
                    } catch (IOException e2) {
                        Slog.w(TAG, "Unable to close pipe!");
                    }
                }
            }
        }
    }

    class PerformInitializeTask implements Runnable {
        HashSet<String> mQueue;

        PerformInitializeTask(HashSet<String> transportNames) {
            this.mQueue = transportNames;
        }

        public void run() {
            try {
                for (String transportName : this.mQueue) {
                    IBackupTransport transport = BackupManagerService.this.getTransport(transportName);
                    if (transport == null) {
                        Slog.e(BackupManagerService.TAG, "Requested init for " + transportName + " but not found");
                    } else {
                        Slog.i(BackupManagerService.TAG, "Initializing (wiping) backup transport storage: " + transportName);
                        EventLog.writeEvent(EventLogTags.BACKUP_START, transport.transportDirName());
                        long startRealtime = SystemClock.elapsedRealtime();
                        int status = transport.initializeDevice();
                        if (status == 0) {
                            status = transport.finishBackup();
                        }
                        if (status == 0) {
                            Slog.i(BackupManagerService.TAG, "Device init successful");
                            int millis = (int) (SystemClock.elapsedRealtime() - startRealtime);
                            EventLog.writeEvent(EventLogTags.BACKUP_INITIALIZE, new Object[0]);
                            BackupManagerService.this.resetBackupState(new File(BackupManagerService.this.mBaseStateDir, transport.transportDirName()));
                            EventLog.writeEvent(EventLogTags.BACKUP_SUCCESS, new Object[]{Integer.valueOf(0), Integer.valueOf(millis)});
                            synchronized (BackupManagerService.this.mQueueLock) {
                                BackupManagerService.this.recordInitPendingLocked(false, transportName);
                            }
                        } else {
                            Slog.e(BackupManagerService.TAG, "Transport error in initializeDevice()");
                            EventLog.writeEvent(EventLogTags.BACKUP_TRANSPORT_FAILURE, "(initialize)");
                            synchronized (BackupManagerService.this.mQueueLock) {
                                BackupManagerService.this.recordInitPendingLocked(true, transportName);
                            }
                            long delay = transport.requestBackupTime();
                            Slog.w(BackupManagerService.TAG, "Init failed on " + transportName + " resched in " + delay);
                            BackupManagerService.this.mAlarmManager.set(0, System.currentTimeMillis() + delay, BackupManagerService.this.mRunInitIntent);
                        }
                    }
                }
                BackupManagerService.this.mWakelock.release();
            } catch (RemoteException e) {
                BackupManagerService.this.mWakelock.release();
            } catch (Exception e2) {
                Slog.e(BackupManagerService.TAG, "Unexpected error performing init", e2);
                BackupManagerService.this.mWakelock.release();
            } catch (Throwable th) {
                BackupManagerService.this.mWakelock.release();
            }
        }
    }

    class PerformUnifiedRestoreTask implements BackupRestoreTask {
        private static final /* synthetic */ int[] -com-android-server-backup-BackupManagerService$UnifiedRestoreStateSwitchesValues = null;
        final /* synthetic */ int[] $SWITCH_TABLE$com$android$server$backup$BackupManagerService$UnifiedRestoreState;
        private List<PackageInfo> mAcceptSet;
        private IBackupAgent mAgent;
        ParcelFileDescriptor mBackupData;
        private File mBackupDataName;
        private int mCount;
        private PackageInfo mCurrentPackage;
        private boolean mDidLaunch;
        private boolean mFinished;
        private boolean mIsSystemRestore;
        ParcelFileDescriptor mNewState;
        private File mNewStateName;
        private IRestoreObserver mObserver;
        private PackageManagerBackupAgent mPmAgent;
        private int mPmToken;
        private RestoreDescription mRestoreDescription;
        private File mSavedStateName;
        private File mStageName;
        private long mStartRealtime = SystemClock.elapsedRealtime();
        private UnifiedRestoreState mState = UnifiedRestoreState.INITIAL;
        File mStateDir;
        private int mStatus;
        private PackageInfo mTargetPackage;
        private long mToken;
        private IBackupTransport mTransport;
        private byte[] mWidgetData;

        class EngineThread implements Runnable {
            FullRestoreEngine mEngine;
            FileInputStream mEngineStream;

            EngineThread(FullRestoreEngine engine, ParcelFileDescriptor engineSocket) {
                this.mEngine = engine;
                engine.setRunning(true);
                this.mEngineStream = new FileInputStream(engineSocket.getFileDescriptor(), true);
            }

            public boolean isRunning() {
                return this.mEngine.isRunning();
            }

            public int waitForResult() {
                return this.mEngine.waitForResult();
            }

            public void run() {
                while (this.mEngine.isRunning()) {
                    try {
                        this.mEngine.restoreOneFile(this.mEngineStream, false);
                    } finally {
                        IoUtils.closeQuietly(this.mEngineStream);
                    }
                }
            }

            public void handleTimeout() {
                IoUtils.closeQuietly(this.mEngineStream);
                this.mEngine.handleTimeout();
            }
        }

        class StreamFeederThread extends RestoreEngine implements Runnable, BackupRestoreTask {
            final String TAG = "StreamFeederThread";
            FullRestoreEngine mEngine;
            ParcelFileDescriptor[] mEnginePipes = ParcelFileDescriptor.createPipe();
            EngineThread mEngineThread;
            ParcelFileDescriptor[] mTransportPipes = ParcelFileDescriptor.createPipe();

            public StreamFeederThread() throws IOException {
                super();
                setRunning(true);
            }

            public void run() {
                PerformUnifiedRestoreTask performUnifiedRestoreTask;
                boolean z;
                UnifiedRestoreState nextState = UnifiedRestoreState.RUNNING_QUEUE;
                int status = 0;
                EventLog.writeEvent(EventLogTags.FULL_RESTORE_PACKAGE, PerformUnifiedRestoreTask.this.mCurrentPackage.packageName);
                this.mEngine = new FullRestoreEngine(this, null, PerformUnifiedRestoreTask.this.mCurrentPackage, false, false);
                this.mEngineThread = new EngineThread(this.mEngine, this.mEnginePipes[0]);
                ParcelFileDescriptor eWriteEnd = this.mEnginePipes[1];
                ParcelFileDescriptor tReadEnd = this.mTransportPipes[0];
                ParcelFileDescriptor tWriteEnd = this.mTransportPipes[1];
                int bufferSize = DumpState.DUMP_VERSION;
                byte[] buffer = new byte[DumpState.DUMP_VERSION];
                FileOutputStream engineOut = new FileOutputStream(eWriteEnd.getFileDescriptor());
                FileInputStream fileInputStream = new FileInputStream(tReadEnd.getFileDescriptor());
                new Thread(this.mEngineThread, "unified-restore-engine").start();
                while (status == 0) {
                    try {
                        int result = PerformUnifiedRestoreTask.this.mTransport.getNextFullRestoreDataChunk(tWriteEnd);
                        if (result > 0) {
                            if (result > bufferSize) {
                                bufferSize = result;
                                buffer = new byte[result];
                            }
                            int toCopy = result;
                            while (toCopy > 0) {
                                int n = fileInputStream.read(buffer, 0, toCopy);
                                engineOut.write(buffer, 0, n);
                                toCopy -= n;
                            }
                        } else if (result == -1) {
                            status = 0;
                            break;
                        } else {
                            Slog.e("StreamFeederThread", "Error " + result + " streaming restore for " + PerformUnifiedRestoreTask.this.mCurrentPackage.packageName);
                            EventLog.writeEvent(EventLogTags.RESTORE_TRANSPORT_FAILURE, new Object[0]);
                            status = result;
                        }
                    } catch (IOException e) {
                        Slog.e("StreamFeederThread", "Unable to route data for restore");
                        EventLog.writeEvent(EventLogTags.RESTORE_AGENT_FAILURE, new Object[]{PerformUnifiedRestoreTask.this.mCurrentPackage.packageName, "I/O error on pipes"});
                        status = -1003;
                        IoUtils.closeQuietly(this.mEnginePipes[1]);
                        IoUtils.closeQuietly(this.mTransportPipes[0]);
                        IoUtils.closeQuietly(this.mTransportPipes[1]);
                        this.mEngineThread.waitForResult();
                        IoUtils.closeQuietly(this.mEnginePipes[0]);
                        performUnifiedRestoreTask = PerformUnifiedRestoreTask.this;
                        if (this.mEngine.getAgent() != null) {
                            z = true;
                        } else {
                            z = false;
                        }
                        performUnifiedRestoreTask.mDidLaunch = z;
                        try {
                            PerformUnifiedRestoreTask.this.mTransport.abortFullRestore();
                        } catch (RemoteException e2) {
                            status = JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE;
                        }
                        BackupManagerService.this.clearApplicationDataSynchronous(PerformUnifiedRestoreTask.this.mCurrentPackage.packageName);
                        if (status == -1000) {
                            nextState = UnifiedRestoreState.FINAL;
                        } else {
                            nextState = UnifiedRestoreState.RUNNING_QUEUE;
                        }
                        PerformUnifiedRestoreTask.this.executeNextState(nextState);
                        setRunning(false);
                        return;
                    } catch (RemoteException e3) {
                        Slog.e("StreamFeederThread", "Transport failed during restore");
                        EventLog.writeEvent(EventLogTags.RESTORE_TRANSPORT_FAILURE, new Object[0]);
                        status = JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE;
                        IoUtils.closeQuietly(this.mEnginePipes[1]);
                        IoUtils.closeQuietly(this.mTransportPipes[0]);
                        IoUtils.closeQuietly(this.mTransportPipes[1]);
                        this.mEngineThread.waitForResult();
                        IoUtils.closeQuietly(this.mEnginePipes[0]);
                        performUnifiedRestoreTask = PerformUnifiedRestoreTask.this;
                        if (this.mEngine.getAgent() != null) {
                            z = true;
                        } else {
                            z = false;
                        }
                        performUnifiedRestoreTask.mDidLaunch = z;
                        try {
                            PerformUnifiedRestoreTask.this.mTransport.abortFullRestore();
                        } catch (RemoteException e4) {
                            status = JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE;
                        }
                        BackupManagerService.this.clearApplicationDataSynchronous(PerformUnifiedRestoreTask.this.mCurrentPackage.packageName);
                        if (status == -1000) {
                            nextState = UnifiedRestoreState.FINAL;
                        } else {
                            nextState = UnifiedRestoreState.RUNNING_QUEUE;
                        }
                        PerformUnifiedRestoreTask.this.executeNextState(nextState);
                        setRunning(false);
                        return;
                    } catch (Throwable th) {
                        IoUtils.closeQuietly(this.mEnginePipes[1]);
                        IoUtils.closeQuietly(this.mTransportPipes[0]);
                        IoUtils.closeQuietly(this.mTransportPipes[1]);
                        this.mEngineThread.waitForResult();
                        IoUtils.closeQuietly(this.mEnginePipes[0]);
                        PerformUnifiedRestoreTask.this.mDidLaunch = this.mEngine.getAgent() != null;
                        if (status == 0) {
                            nextState = UnifiedRestoreState.RESTORE_FINISHED;
                            PerformUnifiedRestoreTask.this.mAgent = this.mEngine.getAgent();
                            PerformUnifiedRestoreTask.this.mWidgetData = this.mEngine.getWidgetData();
                        } else {
                            try {
                                PerformUnifiedRestoreTask.this.mTransport.abortFullRestore();
                            } catch (RemoteException e5) {
                                status = JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE;
                            }
                            BackupManagerService.this.clearApplicationDataSynchronous(PerformUnifiedRestoreTask.this.mCurrentPackage.packageName);
                            if (status == -1000) {
                                nextState = UnifiedRestoreState.FINAL;
                            } else {
                                nextState = UnifiedRestoreState.RUNNING_QUEUE;
                            }
                        }
                        PerformUnifiedRestoreTask.this.executeNextState(nextState);
                        setRunning(false);
                    }
                }
                IoUtils.closeQuietly(this.mEnginePipes[1]);
                IoUtils.closeQuietly(this.mTransportPipes[0]);
                IoUtils.closeQuietly(this.mTransportPipes[1]);
                this.mEngineThread.waitForResult();
                IoUtils.closeQuietly(this.mEnginePipes[0]);
                PerformUnifiedRestoreTask.this.mDidLaunch = this.mEngine.getAgent() != null;
                if (status == 0) {
                    nextState = UnifiedRestoreState.RESTORE_FINISHED;
                    PerformUnifiedRestoreTask.this.mAgent = this.mEngine.getAgent();
                    PerformUnifiedRestoreTask.this.mWidgetData = this.mEngine.getWidgetData();
                } else {
                    try {
                        PerformUnifiedRestoreTask.this.mTransport.abortFullRestore();
                    } catch (RemoteException e6) {
                        status = JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE;
                    }
                    BackupManagerService.this.clearApplicationDataSynchronous(PerformUnifiedRestoreTask.this.mCurrentPackage.packageName);
                    if (status == -1000) {
                        nextState = UnifiedRestoreState.FINAL;
                    } else {
                        nextState = UnifiedRestoreState.RUNNING_QUEUE;
                    }
                }
                PerformUnifiedRestoreTask.this.executeNextState(nextState);
                setRunning(false);
            }

            public void execute() {
            }

            public void operationComplete(long result) {
            }

            public void handleTimeout() {
                if (BackupManagerService.DEBUG) {
                    Slog.w("StreamFeederThread", "Full-data restore target timed out; shutting down");
                }
                this.mEngineThread.handleTimeout();
                IoUtils.closeQuietly(this.mEnginePipes[1]);
                this.mEnginePipes[1] = null;
                IoUtils.closeQuietly(this.mEnginePipes[0]);
                this.mEnginePipes[0] = null;
            }
        }

        private static /* synthetic */ int[] -getcom-android-server-backup-BackupManagerService$UnifiedRestoreStateSwitchesValues() {
            if (-com-android-server-backup-BackupManagerService$UnifiedRestoreStateSwitchesValues != null) {
                return -com-android-server-backup-BackupManagerService$UnifiedRestoreStateSwitchesValues;
            }
            int[] iArr = new int[UnifiedRestoreState.values().length];
            try {
                iArr[UnifiedRestoreState.FINAL.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                iArr[UnifiedRestoreState.INITIAL.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                iArr[UnifiedRestoreState.RESTORE_FINISHED.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                iArr[UnifiedRestoreState.RESTORE_FULL.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                iArr[UnifiedRestoreState.RESTORE_KEYVALUE.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                iArr[UnifiedRestoreState.RUNNING_QUEUE.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
            -com-android-server-backup-BackupManagerService$UnifiedRestoreStateSwitchesValues = iArr;
            return iArr;
        }

        PerformUnifiedRestoreTask(IBackupTransport transport, IRestoreObserver observer, long restoreSetToken, PackageInfo targetPackage, int pmToken, boolean isFullSystemRestore, String[] filterSet) {
            this.mTransport = transport;
            this.mObserver = observer;
            this.mToken = restoreSetToken;
            this.mPmToken = pmToken;
            this.mTargetPackage = targetPackage;
            this.mIsSystemRestore = isFullSystemRestore;
            this.mFinished = false;
            this.mDidLaunch = false;
            if (targetPackage != null) {
                this.mAcceptSet = new ArrayList();
                this.mAcceptSet.add(targetPackage);
                return;
            }
            if (filterSet == null) {
                filterSet = packagesToNames(PackageManagerBackupAgent.getStorableApplications(BackupManagerService.this.mPackageManager));
                if (BackupManagerService.DEBUG) {
                    Slog.i(BackupManagerService.TAG, "Full restore; asking about " + filterSet.length + " apps");
                }
            }
            this.mAcceptSet = new ArrayList(filterSet.length);
            boolean hasSystem = false;
            boolean hasSettings = false;
            for (String packageInfo : filterSet) {
                try {
                    PackageInfo info = BackupManagerService.this.mPackageManager.getPackageInfo(packageInfo, 0);
                    if ("android".equals(info.packageName)) {
                        hasSystem = true;
                    } else if (BackupManagerService.SETTINGS_PACKAGE.equals(info.packageName)) {
                        hasSettings = true;
                    } else if (BackupManagerService.appIsEligibleForBackup(info.applicationInfo)) {
                        this.mAcceptSet.add(info);
                    }
                } catch (NameNotFoundException e) {
                }
            }
            if (hasSystem) {
                try {
                    this.mAcceptSet.add(0, BackupManagerService.this.mPackageManager.getPackageInfo("android", 0));
                } catch (NameNotFoundException e2) {
                }
            }
            if (hasSettings) {
                try {
                    this.mAcceptSet.add(BackupManagerService.this.mPackageManager.getPackageInfo(BackupManagerService.SETTINGS_PACKAGE, 0));
                } catch (NameNotFoundException e3) {
                }
            }
        }

        private String[] packagesToNames(List<PackageInfo> apps) {
            int N = apps.size();
            String[] names = new String[N];
            for (int i = 0; i < N; i++) {
                names[i] = ((PackageInfo) apps.get(i)).packageName;
            }
            return names;
        }

        public void execute() {
            switch (-getcom-android-server-backup-BackupManagerService$UnifiedRestoreStateSwitchesValues()[this.mState.ordinal()]) {
                case 1:
                    if (this.mFinished) {
                        Slog.e(BackupManagerService.TAG, "Duplicate finish");
                    } else {
                        finalizeRestore();
                    }
                    this.mFinished = true;
                    return;
                case 2:
                    startRestore();
                    return;
                case 3:
                    restoreFinished();
                    return;
                case 4:
                    restoreFull();
                    return;
                case 5:
                    restoreKeyValue();
                    return;
                case 6:
                    dispatchNextRestore();
                    return;
                default:
                    return;
            }
        }

        private void startRestore() {
            sendStartRestore(this.mAcceptSet.size());
            if (this.mIsSystemRestore) {
                AppWidgetBackupBridge.restoreStarting(0);
            }
            try {
                this.mStateDir = new File(BackupManagerService.this.mBaseStateDir, this.mTransport.transportDirName());
                PackageInfo pmPackage = new PackageInfo();
                pmPackage.packageName = BackupManagerService.PACKAGE_MANAGER_SENTINEL;
                this.mAcceptSet.add(0, pmPackage);
                this.mStatus = this.mTransport.startRestore(this.mToken, (PackageInfo[]) this.mAcceptSet.toArray(new PackageInfo[0]));
                if (this.mStatus != 0) {
                    Slog.e(BackupManagerService.TAG, "Transport error " + this.mStatus + "; no restore possible");
                    this.mStatus = JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE;
                    executeNextState(UnifiedRestoreState.FINAL);
                    return;
                }
                RestoreDescription desc = this.mTransport.nextRestorePackage();
                if (desc == null) {
                    Slog.e(BackupManagerService.TAG, "No restore metadata available; halting");
                    this.mStatus = JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE;
                    executeNextState(UnifiedRestoreState.FINAL);
                } else if (BackupManagerService.PACKAGE_MANAGER_SENTINEL.equals(desc.getPackageName())) {
                    this.mCurrentPackage = new PackageInfo();
                    this.mCurrentPackage.packageName = BackupManagerService.PACKAGE_MANAGER_SENTINEL;
                    this.mPmAgent = new PackageManagerBackupAgent(BackupManagerService.this.mPackageManager, null);
                    this.mAgent = IBackupAgent.Stub.asInterface(this.mPmAgent.onBind());
                    initiateOneRestore(this.mCurrentPackage, 0);
                    BackupManagerService.this.mBackupHandler.removeMessages(7);
                    if (!this.mPmAgent.hasMetadata()) {
                        Slog.e(BackupManagerService.TAG, "No restore metadata available, so not restoring");
                        EventLog.writeEvent(EventLogTags.RESTORE_AGENT_FAILURE, new Object[]{BackupManagerService.PACKAGE_MANAGER_SENTINEL, "Package manager restore metadata missing"});
                        this.mStatus = JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE;
                        BackupManagerService.this.mBackupHandler.removeMessages(20, this);
                        executeNextState(UnifiedRestoreState.FINAL);
                    }
                } else {
                    Slog.e(BackupManagerService.TAG, "Required metadata but got " + desc.getPackageName());
                    this.mStatus = JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE;
                    executeNextState(UnifiedRestoreState.FINAL);
                }
            } catch (RemoteException e) {
                Slog.e(BackupManagerService.TAG, "Unable to contact transport for restore");
                this.mStatus = JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE;
                BackupManagerService.this.mBackupHandler.removeMessages(20, this);
                executeNextState(UnifiedRestoreState.FINAL);
            }
        }

        private void dispatchNextRestore() {
            UnifiedRestoreState nextState = UnifiedRestoreState.FINAL;
            try {
                this.mRestoreDescription = this.mTransport.nextRestorePackage();
                String packageName = this.mRestoreDescription != null ? this.mRestoreDescription.getPackageName() : null;
                if (packageName == null) {
                    Slog.e(BackupManagerService.TAG, "Failure getting next package name");
                    EventLog.writeEvent(EventLogTags.RESTORE_TRANSPORT_FAILURE, new Object[0]);
                    nextState = UnifiedRestoreState.FINAL;
                } else if (this.mRestoreDescription == RestoreDescription.NO_MORE_PACKAGES) {
                    if (BackupManagerService.DEBUG) {
                        Slog.v(BackupManagerService.TAG, "No more packages; finishing restore");
                    }
                    int millis = (int) (SystemClock.elapsedRealtime() - this.mStartRealtime);
                    EventLog.writeEvent(EventLogTags.RESTORE_SUCCESS, new Object[]{Integer.valueOf(this.mCount), Integer.valueOf(millis)});
                    executeNextState(UnifiedRestoreState.FINAL);
                } else {
                    if (BackupManagerService.DEBUG) {
                        Slog.i(BackupManagerService.TAG, "Next restore package: " + this.mRestoreDescription);
                    }
                    sendOnRestorePackage(packageName);
                    Metadata metaInfo = this.mPmAgent.getRestoredMetadata(packageName);
                    if (metaInfo == null) {
                        Slog.e(BackupManagerService.TAG, "No metadata for " + packageName);
                        EventLog.writeEvent(EventLogTags.RESTORE_AGENT_FAILURE, new Object[]{packageName, "Package metadata missing"});
                        executeNextState(UnifiedRestoreState.RUNNING_QUEUE);
                        return;
                    }
                    try {
                        this.mCurrentPackage = BackupManagerService.this.mPackageManager.getPackageInfo(packageName, 64);
                        if (metaInfo.versionCode > this.mCurrentPackage.versionCode) {
                            if ((this.mCurrentPackage.applicationInfo.flags & DumpState.DUMP_INTENT_FILTER_VERIFIERS) == 0) {
                                Slog.w(BackupManagerService.TAG, "Package " + packageName + ": " + ("Version " + metaInfo.versionCode + " > installed version " + this.mCurrentPackage.versionCode));
                                EventLog.writeEvent(EventLogTags.RESTORE_AGENT_FAILURE, new Object[]{packageName, message});
                                executeNextState(UnifiedRestoreState.RUNNING_QUEUE);
                                return;
                            } else if (BackupManagerService.DEBUG) {
                                Slog.v(BackupManagerService.TAG, "Version " + metaInfo.versionCode + " > installed " + this.mCurrentPackage.versionCode + " but restoreAnyVersion");
                            }
                        }
                        this.mWidgetData = null;
                        int type = this.mRestoreDescription.getDataType();
                        if (type == 1) {
                            nextState = UnifiedRestoreState.RESTORE_KEYVALUE;
                        } else if (type == 2) {
                            nextState = UnifiedRestoreState.RESTORE_FULL;
                        } else {
                            Slog.e(BackupManagerService.TAG, "Unrecognized restore type " + type);
                            executeNextState(UnifiedRestoreState.RUNNING_QUEUE);
                            return;
                        }
                        executeNextState(nextState);
                    } catch (NameNotFoundException e) {
                        Slog.e(BackupManagerService.TAG, "Package not present: " + packageName);
                        EventLog.writeEvent(EventLogTags.RESTORE_AGENT_FAILURE, new Object[]{packageName, "Package missing on device"});
                        executeNextState(UnifiedRestoreState.RUNNING_QUEUE);
                    }
                }
            } catch (RemoteException e2) {
                Slog.e(BackupManagerService.TAG, "Can't get next target from transport; ending restore");
                EventLog.writeEvent(EventLogTags.RESTORE_TRANSPORT_FAILURE, new Object[0]);
                nextState = UnifiedRestoreState.FINAL;
            } finally {
                executeNextState(nextState);
            }
        }

        private void restoreKeyValue() {
            String packageName = this.mCurrentPackage.packageName;
            if (this.mCurrentPackage.applicationInfo.backupAgentName == null || "".equals(this.mCurrentPackage.applicationInfo.backupAgentName)) {
                EventLog.writeEvent(EventLogTags.RESTORE_AGENT_FAILURE, new Object[]{packageName, "Package has no agent"});
                executeNextState(UnifiedRestoreState.RUNNING_QUEUE);
                return;
            }
            Metadata metaInfo = this.mPmAgent.getRestoredMetadata(packageName);
            if (BackupUtils.signaturesMatch(metaInfo.sigHashes, this.mCurrentPackage)) {
                this.mAgent = BackupManagerService.this.bindToAgentSynchronous(this.mCurrentPackage.applicationInfo, 0);
                if (this.mAgent == null) {
                    Slog.w(BackupManagerService.TAG, "Can't find backup agent for " + packageName);
                    EventLog.writeEvent(EventLogTags.RESTORE_AGENT_FAILURE, new Object[]{packageName, "Restore agent missing"});
                    executeNextState(UnifiedRestoreState.RUNNING_QUEUE);
                    return;
                }
                this.mDidLaunch = true;
                try {
                    initiateOneRestore(this.mCurrentPackage, metaInfo.versionCode);
                    this.mCount++;
                } catch (Exception e) {
                    Slog.e(BackupManagerService.TAG, "Error when attempting restore: " + e.toString());
                    keyValueAgentErrorCleanup();
                    executeNextState(UnifiedRestoreState.RUNNING_QUEUE);
                }
                return;
            }
            Slog.w(BackupManagerService.TAG, "Signature mismatch restoring " + packageName);
            EventLog.writeEvent(EventLogTags.RESTORE_AGENT_FAILURE, new Object[]{packageName, "Signature mismatch"});
            executeNextState(UnifiedRestoreState.RUNNING_QUEUE);
        }

        void initiateOneRestore(PackageInfo app, int appVersionCode) {
            String packageName = app.packageName;
            if (BackupManagerService.DEBUG) {
                Slog.d(BackupManagerService.TAG, "initiateOneRestore packageName=" + packageName);
            }
            this.mBackupDataName = new File(BackupManagerService.this.mDataDir, packageName + ".restore");
            this.mStageName = new File(BackupManagerService.this.mDataDir, packageName + ".stage");
            this.mNewStateName = new File(this.mStateDir, packageName + ".new");
            this.mSavedStateName = new File(this.mStateDir, packageName);
            boolean staging = !packageName.equals("android");
            File downloadFile = staging ? this.mStageName : this.mBackupDataName;
            int token = BackupManagerService.this.generateToken();
            try {
                ParcelFileDescriptor stage = ParcelFileDescriptor.open(downloadFile, 1006632960);
                if (this.mTransport.getRestoreData(stage) != 0) {
                    Slog.e(BackupManagerService.TAG, "Error getting restore data for " + packageName);
                    EventLog.writeEvent(EventLogTags.RESTORE_TRANSPORT_FAILURE, new Object[0]);
                    stage.close();
                    downloadFile.delete();
                    executeNextState(UnifiedRestoreState.FINAL);
                    return;
                }
                if (staging) {
                    stage.close();
                    stage = ParcelFileDescriptor.open(downloadFile, 268435456);
                    this.mBackupData = ParcelFileDescriptor.open(this.mBackupDataName, 1006632960);
                    BackupDataInput in = new BackupDataInput(stage.getFileDescriptor());
                    BackupDataOutput out = new BackupDataOutput(this.mBackupData.getFileDescriptor());
                    byte[] buffer = new byte[DumpState.DUMP_PREFERRED_XML];
                    while (in.readNextHeader()) {
                        String key = in.getKey();
                        int size = in.getDataSize();
                        if (key.equals(BackupManagerService.KEY_WIDGET_STATE)) {
                            if (BackupManagerService.DEBUG) {
                                Slog.i(BackupManagerService.TAG, "Restoring widget state for " + packageName);
                            }
                            this.mWidgetData = new byte[size];
                            in.readEntityData(this.mWidgetData, 0, size);
                        } else {
                            if (size > buffer.length) {
                                buffer = new byte[size];
                            }
                            in.readEntityData(buffer, 0, size);
                            out.writeEntityHeader(key, size);
                            out.writeEntityData(buffer, size);
                        }
                    }
                    this.mBackupData.close();
                }
                stage.close();
                this.mBackupData = ParcelFileDescriptor.open(this.mBackupDataName, 268435456);
                this.mNewState = ParcelFileDescriptor.open(this.mNewStateName, 1006632960);
                BackupManagerService.this.prepareOperationTimeout(token, 60000, this);
                this.mAgent.doRestore(this.mBackupData, appVersionCode, this.mNewState, token, BackupManagerService.this.mBackupManagerBinder);
            } catch (Exception e) {
                Slog.e(BackupManagerService.TAG, "Unable to call app for restore: " + packageName, e);
                EventLog.writeEvent(EventLogTags.RESTORE_AGENT_FAILURE, new Object[]{packageName, e.toString()});
                keyValueAgentErrorCleanup();
                executeNextState(UnifiedRestoreState.RUNNING_QUEUE);
            }
        }

        private void restoreFull() {
            try {
                new Thread(new StreamFeederThread(), "unified-stream-feeder").start();
            } catch (IOException e) {
                Slog.e(BackupManagerService.TAG, "Unable to construct pipes for stream restore!");
                executeNextState(UnifiedRestoreState.RUNNING_QUEUE);
            }
        }

        private void restoreFinished() {
            try {
                int token = BackupManagerService.this.generateToken();
                BackupManagerService.this.prepareOperationTimeout(token, 30000, this);
                this.mAgent.doRestoreFinished(token, BackupManagerService.this.mBackupManagerBinder);
            } catch (Exception e) {
                Slog.e(BackupManagerService.TAG, "Unable to finalize restore of " + this.mCurrentPackage.packageName);
                EventLog.writeEvent(EventLogTags.RESTORE_AGENT_FAILURE, new Object[]{packageName, e.toString()});
                keyValueAgentErrorCleanup();
                executeNextState(UnifiedRestoreState.RUNNING_QUEUE);
            }
        }

        private void finalizeRestore() {
            try {
                this.mTransport.finishRestore();
            } catch (Exception e) {
                Slog.e(BackupManagerService.TAG, "Error finishing restore", e);
            }
            if (this.mObserver != null) {
                try {
                    this.mObserver.restoreFinished(this.mStatus);
                } catch (RemoteException e2) {
                    Slog.d(BackupManagerService.TAG, "Restore observer died at restoreFinished");
                }
            }
            BackupManagerService.this.mBackupHandler.removeMessages(8);
            if (this.mPmToken > 0) {
                try {
                    BackupManagerService.this.mPackageManagerBinder.finishPackageInstall(this.mPmToken, this.mDidLaunch);
                } catch (RemoteException e3) {
                }
            } else {
                BackupManagerService.this.mBackupHandler.sendEmptyMessageDelayed(8, 60000);
            }
            AppWidgetBackupBridge.restoreFinished(0);
            if (this.mIsSystemRestore && this.mPmAgent != null) {
                BackupManagerService.this.mAncestralPackages = this.mPmAgent.getRestoredPackages();
                BackupManagerService.this.mAncestralToken = this.mToken;
                BackupManagerService.this.writeRestoreTokens();
            }
            Slog.i(BackupManagerService.TAG, "Restore complete.");
            BackupManagerService.this.mWakelock.release();
        }

        void keyValueAgentErrorCleanup() {
            BackupManagerService.this.clearApplicationDataSynchronous(this.mCurrentPackage.packageName);
            keyValueAgentCleanup();
        }

        void keyValueAgentCleanup() {
            this.mBackupDataName.delete();
            this.mStageName.delete();
            try {
                if (this.mBackupData != null) {
                    this.mBackupData.close();
                }
            } catch (IOException e) {
            }
            try {
                if (this.mNewState != null) {
                    this.mNewState.close();
                }
            } catch (IOException e2) {
            }
            this.mNewState = null;
            this.mBackupData = null;
            this.mNewStateName.delete();
            if (this.mCurrentPackage.applicationInfo != null) {
                try {
                    BackupManagerService.this.mActivityManager.unbindBackupAgent(this.mCurrentPackage.applicationInfo);
                    boolean killAfterRestore = this.mCurrentPackage.applicationInfo.uid >= 10000 ? this.mRestoreDescription.getDataType() != 2 ? (DumpState.DUMP_INSTALLS & this.mCurrentPackage.applicationInfo.flags) != 0 : true : false;
                    if (this.mTargetPackage == null && killAfterRestore) {
                        if (BackupManagerService.DEBUG) {
                            Slog.d(BackupManagerService.TAG, "Restore complete, killing host process of " + this.mCurrentPackage.applicationInfo.processName);
                        }
                        BackupManagerService.this.mActivityManager.killApplicationProcess(this.mCurrentPackage.applicationInfo.processName, this.mCurrentPackage.applicationInfo.uid);
                    }
                } catch (RemoteException e3) {
                }
            }
            BackupManagerService.this.mBackupHandler.removeMessages(7, this);
            synchronized (BackupManagerService.this.mCurrentOpLock) {
                BackupManagerService.this.mCurrentOperations.clear();
            }
        }

        public void operationComplete(long unusedResult) {
            UnifiedRestoreState nextState;
            switch (-getcom-android-server-backup-BackupManagerService$UnifiedRestoreStateSwitchesValues()[this.mState.ordinal()]) {
                case 2:
                    nextState = UnifiedRestoreState.RUNNING_QUEUE;
                    break;
                case 3:
                    int size = (int) this.mBackupDataName.length();
                    EventLog.writeEvent(EventLogTags.RESTORE_PACKAGE, new Object[]{this.mCurrentPackage.packageName, Integer.valueOf(size)});
                    keyValueAgentCleanup();
                    if (this.mWidgetData != null) {
                        BackupManagerService.this.restoreWidgetData(this.mCurrentPackage.packageName, this.mWidgetData);
                    }
                    nextState = UnifiedRestoreState.RUNNING_QUEUE;
                    break;
                case 4:
                case 5:
                    nextState = UnifiedRestoreState.RESTORE_FINISHED;
                    break;
                default:
                    Slog.e(BackupManagerService.TAG, "Unexpected restore callback into state " + this.mState);
                    keyValueAgentErrorCleanup();
                    nextState = UnifiedRestoreState.FINAL;
                    break;
            }
            executeNextState(nextState);
        }

        public void handleTimeout() {
            Slog.e(BackupManagerService.TAG, "Timeout restoring application " + this.mCurrentPackage.packageName);
            EventLog.writeEvent(EventLogTags.RESTORE_AGENT_FAILURE, new Object[]{this.mCurrentPackage.packageName, "restore timeout"});
            keyValueAgentErrorCleanup();
            executeNextState(UnifiedRestoreState.RUNNING_QUEUE);
        }

        void executeNextState(UnifiedRestoreState nextState) {
            this.mState = nextState;
            BackupManagerService.this.mBackupHandler.sendMessage(BackupManagerService.this.mBackupHandler.obtainMessage(20, this));
        }

        void sendStartRestore(int numPackages) {
            if (this.mObserver != null) {
                try {
                    this.mObserver.restoreStarting(numPackages);
                } catch (RemoteException e) {
                    Slog.w(BackupManagerService.TAG, "Restore observer went away: startRestore");
                    this.mObserver = null;
                }
            }
        }

        void sendOnRestorePackage(String name) {
            if (this.mObserver != null && this.mObserver != null) {
                try {
                    this.mObserver.onUpdate(this.mCount, name);
                } catch (RemoteException e) {
                    Slog.d(BackupManagerService.TAG, "Restore observer died in onUpdate");
                    this.mObserver = null;
                }
            }
        }

        void sendEndRestore() {
            if (this.mObserver != null) {
                try {
                    this.mObserver.restoreFinished(this.mStatus);
                } catch (RemoteException e) {
                    Slog.w(BackupManagerService.TAG, "Restore observer went away: endRestore");
                    this.mObserver = null;
                }
            }
        }
    }

    class ProvisionedObserver extends ContentObserver {
        public ProvisionedObserver(Handler handler) {
            super(handler);
        }

        public void onChange(boolean selfChange) {
            boolean wasProvisioned = BackupManagerService.this.mProvisioned;
            boolean isProvisioned = BackupManagerService.this.deviceIsProvisioned();
            BackupManagerService backupManagerService = BackupManagerService.this;
            if (wasProvisioned) {
                isProvisioned = true;
            }
            backupManagerService.mProvisioned = isProvisioned;
            synchronized (BackupManagerService.this.mQueueLock) {
                if (BackupManagerService.this.mProvisioned && !wasProvisioned) {
                    if (BackupManagerService.this.mEnabled) {
                        KeyValueBackupJob.schedule(BackupManagerService.this.mContext);
                        BackupManagerService.this.scheduleNextFullBackupJob(0);
                    }
                }
            }
        }
    }

    class RestoreGetSetsParams {
        public IRestoreObserver observer;
        public ActiveRestoreSession session;
        public IBackupTransport transport;

        RestoreGetSetsParams(IBackupTransport _transport, ActiveRestoreSession _session, IRestoreObserver _observer) {
            this.transport = _transport;
            this.session = _session;
            this.observer = _observer;
        }
    }

    class RestoreParams {
        public String dirName;
        public String[] filterSet;
        public boolean isSystemRestore;
        public IRestoreObserver observer;
        public PackageInfo pkgInfo;
        public int pmToken;
        public long token;
        public IBackupTransport transport;

        RestoreParams(IBackupTransport _transport, String _dirName, IRestoreObserver _obs, long _token, PackageInfo _pkg) {
            this.transport = _transport;
            this.dirName = _dirName;
            this.observer = _obs;
            this.token = _token;
            this.pkgInfo = _pkg;
            this.pmToken = 0;
            this.isSystemRestore = false;
            this.filterSet = null;
        }

        RestoreParams(IBackupTransport _transport, String _dirName, IRestoreObserver _obs, long _token, String _pkgName, int _pmToken) {
            this.transport = _transport;
            this.dirName = _dirName;
            this.observer = _obs;
            this.token = _token;
            this.pkgInfo = null;
            this.pmToken = _pmToken;
            this.isSystemRestore = false;
            this.filterSet = new String[]{_pkgName};
        }

        RestoreParams(IBackupTransport _transport, String _dirName, IRestoreObserver _obs, long _token) {
            this.transport = _transport;
            this.dirName = _dirName;
            this.observer = _obs;
            this.token = _token;
            this.pkgInfo = null;
            this.pmToken = 0;
            this.isSystemRestore = true;
            this.filterSet = null;
        }

        RestoreParams(IBackupTransport _transport, String _dirName, IRestoreObserver _obs, long _token, String[] _filterSet, boolean _isSystemRestore) {
            this.transport = _transport;
            this.dirName = _dirName;
            this.observer = _obs;
            this.token = _token;
            this.pkgInfo = null;
            this.pmToken = 0;
            this.isSystemRestore = _isSystemRestore;
            this.filterSet = _filterSet;
        }
    }

    enum RestorePolicy {
        IGNORE,
        ACCEPT,
        ACCEPT_IF_APK
    }

    private class RunBackupReceiver extends BroadcastReceiver {
        private RunBackupReceiver() {
        }

        public void onReceive(Context context, Intent intent) {
            if (BackupManagerService.RUN_BACKUP_ACTION.equals(intent.getAction())) {
                synchronized (BackupManagerService.this.mQueueLock) {
                    if (BackupManagerService.this.mPendingInits.size() > 0) {
                        try {
                            BackupManagerService.this.mAlarmManager.cancel(BackupManagerService.this.mRunInitIntent);
                            BackupManagerService.this.mRunInitIntent.send();
                        } catch (CanceledException e) {
                            Slog.e(BackupManagerService.TAG, "Run init intent cancelled");
                        }
                    } else if (!BackupManagerService.this.mEnabled || !BackupManagerService.this.mProvisioned) {
                        Slog.w(BackupManagerService.TAG, "Backup pass but e=" + BackupManagerService.this.mEnabled + " p=" + BackupManagerService.this.mProvisioned);
                    } else if (BackupManagerService.this.mBackupRunning) {
                        Slog.i(BackupManagerService.TAG, "Backup time but one already running");
                    } else {
                        if (BackupManagerService.DEBUG) {
                            Slog.v(BackupManagerService.TAG, "Running a backup pass");
                        }
                        BackupManagerService.this.mBackupRunning = true;
                        BackupManagerService.this.mWakelock.acquire();
                        BackupManagerService.this.mBackupHandler.sendMessage(BackupManagerService.this.mBackupHandler.obtainMessage(1));
                    }
                }
            }
        }
    }

    private class RunInitializeReceiver extends BroadcastReceiver {
        private RunInitializeReceiver() {
        }

        public void onReceive(Context context, Intent intent) {
            if (BackupManagerService.RUN_INITIALIZE_ACTION.equals(intent.getAction())) {
                synchronized (BackupManagerService.this.mQueueLock) {
                    if (BackupManagerService.DEBUG) {
                        Slog.v(BackupManagerService.TAG, "Running a device init");
                    }
                    BackupManagerService.this.mWakelock.acquire();
                    BackupManagerService.this.mBackupHandler.sendMessage(BackupManagerService.this.mBackupHandler.obtainMessage(5));
                }
            }
        }
    }

    class TransportConnection implements ServiceConnection {
        ServiceInfo mTransport;

        public TransportConnection(ServiceInfo transport) {
            this.mTransport = transport;
        }

        public void onServiceConnected(ComponentName component, IBinder service) {
            if (BackupManagerService.DEBUG) {
                Slog.v(BackupManagerService.TAG, "Connected to transport " + component);
            }
            String name = component.flattenToShortString();
            try {
                IBackupTransport transport = IBackupTransport.Stub.asInterface(service);
                BackupManagerService.this.registerTransport(transport.name(), name, transport);
                EventLog.writeEvent(EventLogTags.BACKUP_TRANSPORT_LIFECYCLE, new Object[]{name, Integer.valueOf(1)});
            } catch (RemoteException e) {
                Slog.e(BackupManagerService.TAG, "Unable to register transport " + component);
                EventLog.writeEvent(EventLogTags.BACKUP_TRANSPORT_LIFECYCLE, new Object[]{name, Integer.valueOf(0)});
            }
        }

        public void onServiceDisconnected(ComponentName component) {
            if (BackupManagerService.DEBUG) {
                Slog.v(BackupManagerService.TAG, "Disconnected from transport " + component);
            }
            EventLog.writeEvent(EventLogTags.BACKUP_TRANSPORT_LIFECYCLE, new Object[]{component.flattenToShortString(), Integer.valueOf(0)});
            BackupManagerService.this.registerTransport(null, name, null);
        }
    }

    enum UnifiedRestoreState {
        INITIAL,
        RUNNING_QUEUE,
        RESTORE_KEYVALUE,
        RESTORE_FULL,
        RESTORE_FINISHED,
        FINAL
    }

    static {
        boolean isLoggable = !Log.HWINFO ? Log.HWModuleLog ? Log.isLoggable(TAG, 4) : false : true;
        DEBUG = isLoggable;
    }

    static Trampoline getInstance() {
        return sInstance;
    }

    int generateToken() {
        int token;
        do {
            synchronized (this.mTokenGenerator) {
                token = this.mTokenGenerator.nextInt();
            }
        } while (token < 0);
        return token;
    }

    public static boolean appIsEligibleForBackup(ApplicationInfo app) {
        if ((app.flags & DumpState.DUMP_VERSION) == 0) {
            return false;
        }
        if ((app.uid >= 10000 || app.backupAgentName != null) && !app.packageName.equals(SHARED_BACKUP_AGENT_PACKAGE)) {
            return true;
        }
        return false;
    }

    private static boolean appIsStopped(ApplicationInfo app) {
        return (app.flags & 2097152) != 0;
    }

    private static boolean appGetsFullBackup(PackageInfo pkg) {
        boolean z = true;
        if (pkg.applicationInfo.backupAgentName == null) {
            return true;
        }
        if ((pkg.applicationInfo.flags & 67108864) == 0) {
            z = false;
        }
        return z;
    }

    private static boolean appIsKeyValueOnly(PackageInfo pkg) {
        boolean z = false;
        if (SETTINGS_PACKAGE.equals(pkg.packageName)) {
            return false;
        }
        if (!appGetsFullBackup(pkg)) {
            z = true;
        }
        return z;
    }

    void addBackupTrace(String s) {
        synchronized (this.mBackupTrace) {
            this.mBackupTrace.add(s);
        }
    }

    void clearBackupTrace() {
        synchronized (this.mBackupTrace) {
            this.mBackupTrace.clear();
        }
    }

    public BackupManagerService(Context context, Trampoline parent) {
        FileInputStream fileInputStream;
        DataInputStream dataInputStream;
        FileInputStream fin;
        DataInputStream in;
        byte[] salt;
        IntentFilter filter;
        Intent backupIntent;
        Intent initIntent;
        String transport;
        List<ResolveInfo> hosts;
        String str;
        StringBuilder append;
        String str2;
        int i;
        Throwable th;
        this.mContext = context;
        this.mPackageManager = context.getPackageManager();
        this.mPackageManagerBinder = AppGlobals.getPackageManager();
        this.mActivityManager = ActivityManagerNative.getDefault();
        this.mAlarmManager = (AlarmManager) context.getSystemService("alarm");
        this.mPowerManager = (PowerManager) context.getSystemService("power");
        this.mMountService = IMountService.Stub.asInterface(ServiceManager.getService("mount"));
        this.mBackupManagerBinder = Trampoline.asInterface(parent.asBinder());
        this.mHandlerThread = new HandlerThread("backup", 10);
        this.mHandlerThread.start();
        this.mBackupHandler = new BackupHandler(this.mHandlerThread.getLooper());
        ContentResolver resolver = context.getContentResolver();
        this.mProvisioned = Global.getInt(resolver, "device_provisioned", 0) != 0;
        this.mAutoRestore = Secure.getInt(resolver, "backup_auto_restore", 1) != 0;
        this.mProvisionedObserver = new ProvisionedObserver(this.mBackupHandler);
        resolver.registerContentObserver(Global.getUriFor("device_provisioned"), false, this.mProvisionedObserver);
        this.mBaseStateDir = new File(Environment.getDataDirectory(), "backup");
        this.mBaseStateDir.mkdirs();
        if (!SELinux.restorecon(this.mBaseStateDir)) {
            Slog.e(TAG, "SELinux restorecon failed on " + this.mBaseStateDir);
        }
        this.mDataDir = new File(Environment.getDownloadCacheDirectory(), "backup_stage");
        this.mPasswordVersion = 1;
        this.mPasswordVersionFile = new File(this.mBaseStateDir, "pwversion");
        if (this.mPasswordVersionFile.exists()) {
            fileInputStream = null;
            dataInputStream = null;
            try {
                fin = new FileInputStream(this.mPasswordVersionFile);
                try {
                    in = new DataInputStream(fin);
                    try {
                        this.mPasswordVersion = in.readInt();
                        if (in != null) {
                            try {
                                in.close();
                            } catch (IOException e) {
                                Slog.w(TAG, "Error closing pw version files");
                            }
                        }
                        if (fin != null) {
                            fin.close();
                        }
                    } catch (IOException e2) {
                        dataInputStream = in;
                        fileInputStream = fin;
                        try {
                            Slog.e(TAG, "Unable to read backup pw version");
                            if (dataInputStream != null) {
                                try {
                                    dataInputStream.close();
                                } catch (IOException e3) {
                                    Slog.w(TAG, "Error closing pw version files");
                                }
                            }
                            if (fileInputStream != null) {
                                fileInputStream.close();
                            }
                            this.mPasswordHashFile = new File(this.mBaseStateDir, "pwhash");
                            if (this.mPasswordHashFile.exists()) {
                                fileInputStream = null;
                                dataInputStream = null;
                                try {
                                    fin = new FileInputStream(this.mPasswordHashFile);
                                    try {
                                        in = new DataInputStream(new BufferedInputStream(fin));
                                        try {
                                            salt = new byte[in.readInt()];
                                            in.readFully(salt);
                                            this.mPasswordHash = in.readUTF();
                                            this.mPasswordSalt = salt;
                                            if (in != null) {
                                                try {
                                                    in.close();
                                                } catch (IOException e4) {
                                                    Slog.w(TAG, "Unable to close streams");
                                                }
                                            }
                                            if (fin != null) {
                                                fin.close();
                                            }
                                        } catch (IOException e5) {
                                            dataInputStream = in;
                                            fileInputStream = fin;
                                            try {
                                                Slog.e(TAG, "Unable to read saved backup pw hash");
                                                if (dataInputStream != null) {
                                                    try {
                                                        dataInputStream.close();
                                                    } catch (IOException e6) {
                                                        Slog.w(TAG, "Unable to close streams");
                                                    }
                                                }
                                                if (fileInputStream != null) {
                                                    fileInputStream.close();
                                                }
                                                this.mRunBackupReceiver = new RunBackupReceiver();
                                                filter = new IntentFilter();
                                                filter.addAction(RUN_BACKUP_ACTION);
                                                context.registerReceiver(this.mRunBackupReceiver, filter, "android.permission.BACKUP", null);
                                                this.mRunInitReceiver = new RunInitializeReceiver();
                                                filter = new IntentFilter();
                                                filter.addAction(RUN_INITIALIZE_ACTION);
                                                context.registerReceiver(this.mRunInitReceiver, filter, "android.permission.BACKUP", null);
                                                backupIntent = new Intent(RUN_BACKUP_ACTION);
                                                backupIntent.addFlags(1073741824);
                                                this.mRunBackupIntent = PendingIntent.getBroadcast(context, 1, backupIntent, 0);
                                                initIntent = new Intent(RUN_INITIALIZE_ACTION);
                                                backupIntent.addFlags(1073741824);
                                                this.mRunInitIntent = PendingIntent.getBroadcast(context, 5, initIntent, 0);
                                                this.mJournalDir = new File(this.mBaseStateDir, "pending");
                                                this.mJournalDir.mkdirs();
                                                this.mJournal = null;
                                                this.mFullBackupScheduleFile = new File(this.mBaseStateDir, "fb-schedule");
                                                initPackageTracking();
                                                synchronized (this.mBackupParticipants) {
                                                    addPackageParticipantsLocked(null);
                                                }
                                                this.mTransportWhitelist = SystemConfig.getInstance().getBackupTransportWhitelist();
                                                transport = Secure.getString(context.getContentResolver(), "backup_transport");
                                                if (TextUtils.isEmpty(transport)) {
                                                    transport = null;
                                                }
                                                this.mCurrentTransport = transport;
                                                if (DEBUG) {
                                                    Slog.v(TAG, "Starting with transport " + this.mCurrentTransport);
                                                }
                                                hosts = this.mPackageManager.queryIntentServicesAsUser(this.mTransportServiceIntent, 0, 0);
                                                if (DEBUG) {
                                                    str = TAG;
                                                    append = new StringBuilder().append("Found transports: ");
                                                    if (hosts == null) {
                                                        str2 = "null";
                                                    } else {
                                                        str2 = Integer.valueOf(hosts.size());
                                                    }
                                                    Slog.v(str, append.append(str2).toString());
                                                }
                                                if (hosts != null) {
                                                    for (i = 0; i < hosts.size(); i++) {
                                                        tryBindTransport(((ResolveInfo) hosts.get(i)).serviceInfo);
                                                    }
                                                }
                                                parseLeftoverJournals();
                                                this.mWakelock = this.mPowerManager.newWakeLock(1, "*backup*");
                                            } catch (Throwable th2) {
                                                th = th2;
                                                if (dataInputStream != null) {
                                                    try {
                                                        dataInputStream.close();
                                                    } catch (IOException e7) {
                                                        Slog.w(TAG, "Unable to close streams");
                                                        throw th;
                                                    }
                                                }
                                                if (fileInputStream != null) {
                                                    fileInputStream.close();
                                                }
                                                throw th;
                                            }
                                        } catch (Throwable th3) {
                                            th = th3;
                                            dataInputStream = in;
                                            fileInputStream = fin;
                                            if (dataInputStream != null) {
                                                dataInputStream.close();
                                            }
                                            if (fileInputStream != null) {
                                                fileInputStream.close();
                                            }
                                            throw th;
                                        }
                                    } catch (IOException e8) {
                                        fileInputStream = fin;
                                        Slog.e(TAG, "Unable to read saved backup pw hash");
                                        if (dataInputStream != null) {
                                            dataInputStream.close();
                                        }
                                        if (fileInputStream != null) {
                                            fileInputStream.close();
                                        }
                                        this.mRunBackupReceiver = new RunBackupReceiver();
                                        filter = new IntentFilter();
                                        filter.addAction(RUN_BACKUP_ACTION);
                                        context.registerReceiver(this.mRunBackupReceiver, filter, "android.permission.BACKUP", null);
                                        this.mRunInitReceiver = new RunInitializeReceiver();
                                        filter = new IntentFilter();
                                        filter.addAction(RUN_INITIALIZE_ACTION);
                                        context.registerReceiver(this.mRunInitReceiver, filter, "android.permission.BACKUP", null);
                                        backupIntent = new Intent(RUN_BACKUP_ACTION);
                                        backupIntent.addFlags(1073741824);
                                        this.mRunBackupIntent = PendingIntent.getBroadcast(context, 1, backupIntent, 0);
                                        initIntent = new Intent(RUN_INITIALIZE_ACTION);
                                        backupIntent.addFlags(1073741824);
                                        this.mRunInitIntent = PendingIntent.getBroadcast(context, 5, initIntent, 0);
                                        this.mJournalDir = new File(this.mBaseStateDir, "pending");
                                        this.mJournalDir.mkdirs();
                                        this.mJournal = null;
                                        this.mFullBackupScheduleFile = new File(this.mBaseStateDir, "fb-schedule");
                                        initPackageTracking();
                                        synchronized (this.mBackupParticipants) {
                                            addPackageParticipantsLocked(null);
                                        }
                                        this.mTransportWhitelist = SystemConfig.getInstance().getBackupTransportWhitelist();
                                        transport = Secure.getString(context.getContentResolver(), "backup_transport");
                                        if (TextUtils.isEmpty(transport)) {
                                            transport = null;
                                        }
                                        this.mCurrentTransport = transport;
                                        if (DEBUG) {
                                            Slog.v(TAG, "Starting with transport " + this.mCurrentTransport);
                                        }
                                        hosts = this.mPackageManager.queryIntentServicesAsUser(this.mTransportServiceIntent, 0, 0);
                                        if (DEBUG) {
                                            str = TAG;
                                            append = new StringBuilder().append("Found transports: ");
                                            if (hosts == null) {
                                                str2 = Integer.valueOf(hosts.size());
                                            } else {
                                                str2 = "null";
                                            }
                                            Slog.v(str, append.append(str2).toString());
                                        }
                                        if (hosts != null) {
                                            for (i = 0; i < hosts.size(); i++) {
                                                tryBindTransport(((ResolveInfo) hosts.get(i)).serviceInfo);
                                            }
                                        }
                                        parseLeftoverJournals();
                                        this.mWakelock = this.mPowerManager.newWakeLock(1, "*backup*");
                                    } catch (Throwable th4) {
                                        th = th4;
                                        fileInputStream = fin;
                                        if (dataInputStream != null) {
                                            dataInputStream.close();
                                        }
                                        if (fileInputStream != null) {
                                            fileInputStream.close();
                                        }
                                        throw th;
                                    }
                                } catch (IOException e9) {
                                    Slog.e(TAG, "Unable to read saved backup pw hash");
                                    if (dataInputStream != null) {
                                        dataInputStream.close();
                                    }
                                    if (fileInputStream != null) {
                                        fileInputStream.close();
                                    }
                                    this.mRunBackupReceiver = new RunBackupReceiver();
                                    filter = new IntentFilter();
                                    filter.addAction(RUN_BACKUP_ACTION);
                                    context.registerReceiver(this.mRunBackupReceiver, filter, "android.permission.BACKUP", null);
                                    this.mRunInitReceiver = new RunInitializeReceiver();
                                    filter = new IntentFilter();
                                    filter.addAction(RUN_INITIALIZE_ACTION);
                                    context.registerReceiver(this.mRunInitReceiver, filter, "android.permission.BACKUP", null);
                                    backupIntent = new Intent(RUN_BACKUP_ACTION);
                                    backupIntent.addFlags(1073741824);
                                    this.mRunBackupIntent = PendingIntent.getBroadcast(context, 1, backupIntent, 0);
                                    initIntent = new Intent(RUN_INITIALIZE_ACTION);
                                    backupIntent.addFlags(1073741824);
                                    this.mRunInitIntent = PendingIntent.getBroadcast(context, 5, initIntent, 0);
                                    this.mJournalDir = new File(this.mBaseStateDir, "pending");
                                    this.mJournalDir.mkdirs();
                                    this.mJournal = null;
                                    this.mFullBackupScheduleFile = new File(this.mBaseStateDir, "fb-schedule");
                                    initPackageTracking();
                                    synchronized (this.mBackupParticipants) {
                                        addPackageParticipantsLocked(null);
                                    }
                                    this.mTransportWhitelist = SystemConfig.getInstance().getBackupTransportWhitelist();
                                    transport = Secure.getString(context.getContentResolver(), "backup_transport");
                                    if (TextUtils.isEmpty(transport)) {
                                        transport = null;
                                    }
                                    this.mCurrentTransport = transport;
                                    if (DEBUG) {
                                        Slog.v(TAG, "Starting with transport " + this.mCurrentTransport);
                                    }
                                    hosts = this.mPackageManager.queryIntentServicesAsUser(this.mTransportServiceIntent, 0, 0);
                                    if (DEBUG) {
                                        str = TAG;
                                        append = new StringBuilder().append("Found transports: ");
                                        if (hosts == null) {
                                            str2 = Integer.valueOf(hosts.size());
                                        } else {
                                            str2 = "null";
                                        }
                                        Slog.v(str, append.append(str2).toString());
                                    }
                                    if (hosts != null) {
                                        for (i = 0; i < hosts.size(); i++) {
                                            tryBindTransport(((ResolveInfo) hosts.get(i)).serviceInfo);
                                        }
                                    }
                                    parseLeftoverJournals();
                                    this.mWakelock = this.mPowerManager.newWakeLock(1, "*backup*");
                                }
                            }
                            this.mRunBackupReceiver = new RunBackupReceiver();
                            filter = new IntentFilter();
                            filter.addAction(RUN_BACKUP_ACTION);
                            context.registerReceiver(this.mRunBackupReceiver, filter, "android.permission.BACKUP", null);
                            this.mRunInitReceiver = new RunInitializeReceiver();
                            filter = new IntentFilter();
                            filter.addAction(RUN_INITIALIZE_ACTION);
                            context.registerReceiver(this.mRunInitReceiver, filter, "android.permission.BACKUP", null);
                            backupIntent = new Intent(RUN_BACKUP_ACTION);
                            backupIntent.addFlags(1073741824);
                            this.mRunBackupIntent = PendingIntent.getBroadcast(context, 1, backupIntent, 0);
                            initIntent = new Intent(RUN_INITIALIZE_ACTION);
                            backupIntent.addFlags(1073741824);
                            this.mRunInitIntent = PendingIntent.getBroadcast(context, 5, initIntent, 0);
                            this.mJournalDir = new File(this.mBaseStateDir, "pending");
                            this.mJournalDir.mkdirs();
                            this.mJournal = null;
                            this.mFullBackupScheduleFile = new File(this.mBaseStateDir, "fb-schedule");
                            initPackageTracking();
                            synchronized (this.mBackupParticipants) {
                                addPackageParticipantsLocked(null);
                            }
                            this.mTransportWhitelist = SystemConfig.getInstance().getBackupTransportWhitelist();
                            transport = Secure.getString(context.getContentResolver(), "backup_transport");
                            if (TextUtils.isEmpty(transport)) {
                                transport = null;
                            }
                            this.mCurrentTransport = transport;
                            if (DEBUG) {
                                Slog.v(TAG, "Starting with transport " + this.mCurrentTransport);
                            }
                            hosts = this.mPackageManager.queryIntentServicesAsUser(this.mTransportServiceIntent, 0, 0);
                            if (DEBUG) {
                                str = TAG;
                                append = new StringBuilder().append("Found transports: ");
                                if (hosts == null) {
                                    str2 = "null";
                                } else {
                                    str2 = Integer.valueOf(hosts.size());
                                }
                                Slog.v(str, append.append(str2).toString());
                            }
                            if (hosts != null) {
                                for (i = 0; i < hosts.size(); i++) {
                                    tryBindTransport(((ResolveInfo) hosts.get(i)).serviceInfo);
                                }
                            }
                            parseLeftoverJournals();
                            this.mWakelock = this.mPowerManager.newWakeLock(1, "*backup*");
                        } catch (Throwable th5) {
                            th = th5;
                            if (dataInputStream != null) {
                                try {
                                    dataInputStream.close();
                                } catch (IOException e10) {
                                    Slog.w(TAG, "Error closing pw version files");
                                    throw th;
                                }
                            }
                            if (fileInputStream != null) {
                                fileInputStream.close();
                            }
                            throw th;
                        }
                    } catch (Throwable th6) {
                        th = th6;
                        dataInputStream = in;
                        fileInputStream = fin;
                        if (dataInputStream != null) {
                            dataInputStream.close();
                        }
                        if (fileInputStream != null) {
                            fileInputStream.close();
                        }
                        throw th;
                    }
                } catch (IOException e11) {
                    fileInputStream = fin;
                    Slog.e(TAG, "Unable to read backup pw version");
                    if (dataInputStream != null) {
                        dataInputStream.close();
                    }
                    if (fileInputStream != null) {
                        fileInputStream.close();
                    }
                    this.mPasswordHashFile = new File(this.mBaseStateDir, "pwhash");
                    if (this.mPasswordHashFile.exists()) {
                        fileInputStream = null;
                        dataInputStream = null;
                        fin = new FileInputStream(this.mPasswordHashFile);
                        in = new DataInputStream(new BufferedInputStream(fin));
                        salt = new byte[in.readInt()];
                        in.readFully(salt);
                        this.mPasswordHash = in.readUTF();
                        this.mPasswordSalt = salt;
                        if (in != null) {
                            in.close();
                        }
                        if (fin != null) {
                            fin.close();
                        }
                    }
                    this.mRunBackupReceiver = new RunBackupReceiver();
                    filter = new IntentFilter();
                    filter.addAction(RUN_BACKUP_ACTION);
                    context.registerReceiver(this.mRunBackupReceiver, filter, "android.permission.BACKUP", null);
                    this.mRunInitReceiver = new RunInitializeReceiver();
                    filter = new IntentFilter();
                    filter.addAction(RUN_INITIALIZE_ACTION);
                    context.registerReceiver(this.mRunInitReceiver, filter, "android.permission.BACKUP", null);
                    backupIntent = new Intent(RUN_BACKUP_ACTION);
                    backupIntent.addFlags(1073741824);
                    this.mRunBackupIntent = PendingIntent.getBroadcast(context, 1, backupIntent, 0);
                    initIntent = new Intent(RUN_INITIALIZE_ACTION);
                    backupIntent.addFlags(1073741824);
                    this.mRunInitIntent = PendingIntent.getBroadcast(context, 5, initIntent, 0);
                    this.mJournalDir = new File(this.mBaseStateDir, "pending");
                    this.mJournalDir.mkdirs();
                    this.mJournal = null;
                    this.mFullBackupScheduleFile = new File(this.mBaseStateDir, "fb-schedule");
                    initPackageTracking();
                    synchronized (this.mBackupParticipants) {
                        addPackageParticipantsLocked(null);
                    }
                    this.mTransportWhitelist = SystemConfig.getInstance().getBackupTransportWhitelist();
                    transport = Secure.getString(context.getContentResolver(), "backup_transport");
                    if (TextUtils.isEmpty(transport)) {
                        transport = null;
                    }
                    this.mCurrentTransport = transport;
                    if (DEBUG) {
                        Slog.v(TAG, "Starting with transport " + this.mCurrentTransport);
                    }
                    hosts = this.mPackageManager.queryIntentServicesAsUser(this.mTransportServiceIntent, 0, 0);
                    if (DEBUG) {
                        str = TAG;
                        append = new StringBuilder().append("Found transports: ");
                        if (hosts == null) {
                            str2 = Integer.valueOf(hosts.size());
                        } else {
                            str2 = "null";
                        }
                        Slog.v(str, append.append(str2).toString());
                    }
                    if (hosts != null) {
                        for (i = 0; i < hosts.size(); i++) {
                            tryBindTransport(((ResolveInfo) hosts.get(i)).serviceInfo);
                        }
                    }
                    parseLeftoverJournals();
                    this.mWakelock = this.mPowerManager.newWakeLock(1, "*backup*");
                } catch (Throwable th7) {
                    th = th7;
                    fileInputStream = fin;
                    if (dataInputStream != null) {
                        dataInputStream.close();
                    }
                    if (fileInputStream != null) {
                        fileInputStream.close();
                    }
                    throw th;
                }
            } catch (IOException e12) {
                Slog.e(TAG, "Unable to read backup pw version");
                if (dataInputStream != null) {
                    dataInputStream.close();
                }
                if (fileInputStream != null) {
                    fileInputStream.close();
                }
                this.mPasswordHashFile = new File(this.mBaseStateDir, "pwhash");
                if (this.mPasswordHashFile.exists()) {
                    fileInputStream = null;
                    dataInputStream = null;
                    fin = new FileInputStream(this.mPasswordHashFile);
                    in = new DataInputStream(new BufferedInputStream(fin));
                    salt = new byte[in.readInt()];
                    in.readFully(salt);
                    this.mPasswordHash = in.readUTF();
                    this.mPasswordSalt = salt;
                    if (in != null) {
                        in.close();
                    }
                    if (fin != null) {
                        fin.close();
                    }
                }
                this.mRunBackupReceiver = new RunBackupReceiver();
                filter = new IntentFilter();
                filter.addAction(RUN_BACKUP_ACTION);
                context.registerReceiver(this.mRunBackupReceiver, filter, "android.permission.BACKUP", null);
                this.mRunInitReceiver = new RunInitializeReceiver();
                filter = new IntentFilter();
                filter.addAction(RUN_INITIALIZE_ACTION);
                context.registerReceiver(this.mRunInitReceiver, filter, "android.permission.BACKUP", null);
                backupIntent = new Intent(RUN_BACKUP_ACTION);
                backupIntent.addFlags(1073741824);
                this.mRunBackupIntent = PendingIntent.getBroadcast(context, 1, backupIntent, 0);
                initIntent = new Intent(RUN_INITIALIZE_ACTION);
                backupIntent.addFlags(1073741824);
                this.mRunInitIntent = PendingIntent.getBroadcast(context, 5, initIntent, 0);
                this.mJournalDir = new File(this.mBaseStateDir, "pending");
                this.mJournalDir.mkdirs();
                this.mJournal = null;
                this.mFullBackupScheduleFile = new File(this.mBaseStateDir, "fb-schedule");
                initPackageTracking();
                synchronized (this.mBackupParticipants) {
                    addPackageParticipantsLocked(null);
                }
                this.mTransportWhitelist = SystemConfig.getInstance().getBackupTransportWhitelist();
                transport = Secure.getString(context.getContentResolver(), "backup_transport");
                if (TextUtils.isEmpty(transport)) {
                    transport = null;
                }
                this.mCurrentTransport = transport;
                if (DEBUG) {
                    Slog.v(TAG, "Starting with transport " + this.mCurrentTransport);
                }
                hosts = this.mPackageManager.queryIntentServicesAsUser(this.mTransportServiceIntent, 0, 0);
                if (DEBUG) {
                    str = TAG;
                    append = new StringBuilder().append("Found transports: ");
                    if (hosts == null) {
                        str2 = "null";
                    } else {
                        str2 = Integer.valueOf(hosts.size());
                    }
                    Slog.v(str, append.append(str2).toString());
                }
                if (hosts != null) {
                    for (i = 0; i < hosts.size(); i++) {
                        tryBindTransport(((ResolveInfo) hosts.get(i)).serviceInfo);
                    }
                }
                parseLeftoverJournals();
                this.mWakelock = this.mPowerManager.newWakeLock(1, "*backup*");
            }
        }
        this.mPasswordHashFile = new File(this.mBaseStateDir, "pwhash");
        if (this.mPasswordHashFile.exists()) {
            fileInputStream = null;
            dataInputStream = null;
            fin = new FileInputStream(this.mPasswordHashFile);
            in = new DataInputStream(new BufferedInputStream(fin));
            salt = new byte[in.readInt()];
            in.readFully(salt);
            this.mPasswordHash = in.readUTF();
            this.mPasswordSalt = salt;
            if (in != null) {
                in.close();
            }
            if (fin != null) {
                fin.close();
            }
        }
        this.mRunBackupReceiver = new RunBackupReceiver();
        filter = new IntentFilter();
        filter.addAction(RUN_BACKUP_ACTION);
        context.registerReceiver(this.mRunBackupReceiver, filter, "android.permission.BACKUP", null);
        this.mRunInitReceiver = new RunInitializeReceiver();
        filter = new IntentFilter();
        filter.addAction(RUN_INITIALIZE_ACTION);
        context.registerReceiver(this.mRunInitReceiver, filter, "android.permission.BACKUP", null);
        backupIntent = new Intent(RUN_BACKUP_ACTION);
        backupIntent.addFlags(1073741824);
        this.mRunBackupIntent = PendingIntent.getBroadcast(context, 1, backupIntent, 0);
        initIntent = new Intent(RUN_INITIALIZE_ACTION);
        backupIntent.addFlags(1073741824);
        this.mRunInitIntent = PendingIntent.getBroadcast(context, 5, initIntent, 0);
        this.mJournalDir = new File(this.mBaseStateDir, "pending");
        this.mJournalDir.mkdirs();
        this.mJournal = null;
        this.mFullBackupScheduleFile = new File(this.mBaseStateDir, "fb-schedule");
        initPackageTracking();
        synchronized (this.mBackupParticipants) {
            addPackageParticipantsLocked(null);
        }
        this.mTransportWhitelist = SystemConfig.getInstance().getBackupTransportWhitelist();
        transport = Secure.getString(context.getContentResolver(), "backup_transport");
        if (TextUtils.isEmpty(transport)) {
            transport = null;
        }
        this.mCurrentTransport = transport;
        if (DEBUG) {
            Slog.v(TAG, "Starting with transport " + this.mCurrentTransport);
        }
        hosts = this.mPackageManager.queryIntentServicesAsUser(this.mTransportServiceIntent, 0, 0);
        if (DEBUG) {
            str = TAG;
            append = new StringBuilder().append("Found transports: ");
            if (hosts == null) {
                str2 = "null";
            } else {
                str2 = Integer.valueOf(hosts.size());
            }
            Slog.v(str, append.append(str2).toString());
        }
        if (hosts != null) {
            for (i = 0; i < hosts.size(); i++) {
                tryBindTransport(((ResolveInfo) hosts.get(i)).serviceInfo);
            }
        }
        parseLeftoverJournals();
        this.mWakelock = this.mPowerManager.newWakeLock(1, "*backup*");
    }

    private void initPackageTracking() {
        RandomAccessFile randomAccessFile;
        IOException e;
        Throwable th;
        IntentFilter filter;
        IntentFilter sdFilter;
        this.mTokenFile = new File(this.mBaseStateDir, "ancestral");
        try {
            randomAccessFile = new RandomAccessFile(this.mTokenFile, "r");
            if (randomAccessFile.readInt() == 1) {
                this.mAncestralToken = randomAccessFile.readLong();
                this.mCurrentToken = randomAccessFile.readLong();
                int numPackages = randomAccessFile.readInt();
                if (numPackages >= 0) {
                    this.mAncestralPackages = new HashSet();
                    for (int i = 0; i < numPackages; i++) {
                        this.mAncestralPackages.add(randomAccessFile.readUTF());
                    }
                }
            }
            randomAccessFile.close();
        } catch (FileNotFoundException e2) {
            Slog.v(TAG, "No ancestral data");
        } catch (IOException e3) {
            Slog.w(TAG, "Unable to read token file", e3);
        }
        this.mEverStored = new File(this.mBaseStateDir, "processed");
        File file = new File(this.mBaseStateDir, "processed.new");
        if (file.exists()) {
            file.delete();
        }
        if (this.mEverStored.exists()) {
            RandomAccessFile randomAccessFile2 = null;
            RandomAccessFile randomAccessFile3 = null;
            try {
                randomAccessFile = new RandomAccessFile(file, "rws");
                try {
                    RandomAccessFile in = new RandomAccessFile(this.mEverStored, "r");
                    while (true) {
                        try {
                            String pkg = in.readUTF();
                            try {
                                this.mPackageManager.getPackageInfo(pkg, 0);
                                this.mEverStoredApps.add(pkg);
                                randomAccessFile.writeUTF(pkg);
                            } catch (NameNotFoundException e4) {
                            }
                        } catch (EOFException e5) {
                            randomAccessFile3 = in;
                            randomAccessFile2 = randomAccessFile;
                        } catch (IOException e6) {
                            e3 = e6;
                            randomAccessFile3 = in;
                            randomAccessFile2 = randomAccessFile;
                        } catch (Throwable th2) {
                            th = th2;
                            randomAccessFile3 = in;
                            randomAccessFile2 = randomAccessFile;
                        }
                    }
                } catch (EOFException e7) {
                    randomAccessFile2 = randomAccessFile;
                    if (!file.renameTo(this.mEverStored)) {
                        Slog.e(TAG, "Error renaming " + file + " to " + this.mEverStored);
                    }
                    if (randomAccessFile2 != null) {
                        try {
                            randomAccessFile2.close();
                        } catch (IOException e8) {
                        }
                    }
                    if (randomAccessFile3 != null) {
                        try {
                            randomAccessFile3.close();
                        } catch (IOException e9) {
                        }
                    }
                    synchronized (this.mQueueLock) {
                        this.mFullBackupQueue = readFullBackupSchedule();
                    }
                    filter = new IntentFilter();
                    filter.addAction("android.intent.action.PACKAGE_ADDED");
                    filter.addAction("android.intent.action.PACKAGE_REMOVED");
                    filter.addAction("android.intent.action.PACKAGE_CHANGED");
                    filter.addDataScheme(HwBroadcastRadarUtil.KEY_PACKAGE);
                    this.mContext.registerReceiver(this.mBroadcastReceiver, filter);
                    sdFilter = new IntentFilter();
                    sdFilter.addAction("android.intent.action.EXTERNAL_APPLICATIONS_AVAILABLE");
                    sdFilter.addAction("android.intent.action.EXTERNAL_APPLICATIONS_UNAVAILABLE");
                    this.mContext.registerReceiver(this.mBroadcastReceiver, sdFilter);
                } catch (IOException e10) {
                    e3 = e10;
                    randomAccessFile2 = randomAccessFile;
                    try {
                        Slog.e(TAG, "Error in processed file", e3);
                        if (randomAccessFile2 != null) {
                            try {
                                randomAccessFile2.close();
                            } catch (IOException e11) {
                            }
                        }
                        if (randomAccessFile3 != null) {
                            try {
                                randomAccessFile3.close();
                            } catch (IOException e12) {
                            }
                        }
                        synchronized (this.mQueueLock) {
                            this.mFullBackupQueue = readFullBackupSchedule();
                        }
                        filter = new IntentFilter();
                        filter.addAction("android.intent.action.PACKAGE_ADDED");
                        filter.addAction("android.intent.action.PACKAGE_REMOVED");
                        filter.addAction("android.intent.action.PACKAGE_CHANGED");
                        filter.addDataScheme(HwBroadcastRadarUtil.KEY_PACKAGE);
                        this.mContext.registerReceiver(this.mBroadcastReceiver, filter);
                        sdFilter = new IntentFilter();
                        sdFilter.addAction("android.intent.action.EXTERNAL_APPLICATIONS_AVAILABLE");
                        sdFilter.addAction("android.intent.action.EXTERNAL_APPLICATIONS_UNAVAILABLE");
                        this.mContext.registerReceiver(this.mBroadcastReceiver, sdFilter);
                    } catch (Throwable th3) {
                        th = th3;
                        if (randomAccessFile2 != null) {
                            try {
                                randomAccessFile2.close();
                            } catch (IOException e13) {
                            }
                        }
                        if (randomAccessFile3 != null) {
                            try {
                                randomAccessFile3.close();
                            } catch (IOException e14) {
                            }
                        }
                        throw th;
                    }
                } catch (Throwable th4) {
                    th = th4;
                    randomAccessFile2 = randomAccessFile;
                    if (randomAccessFile2 != null) {
                        randomAccessFile2.close();
                    }
                    if (randomAccessFile3 != null) {
                        randomAccessFile3.close();
                    }
                    throw th;
                }
            } catch (EOFException e15) {
                if (file.renameTo(this.mEverStored)) {
                    Slog.e(TAG, "Error renaming " + file + " to " + this.mEverStored);
                }
                if (randomAccessFile2 != null) {
                    randomAccessFile2.close();
                }
                if (randomAccessFile3 != null) {
                    randomAccessFile3.close();
                }
                synchronized (this.mQueueLock) {
                    this.mFullBackupQueue = readFullBackupSchedule();
                }
                filter = new IntentFilter();
                filter.addAction("android.intent.action.PACKAGE_ADDED");
                filter.addAction("android.intent.action.PACKAGE_REMOVED");
                filter.addAction("android.intent.action.PACKAGE_CHANGED");
                filter.addDataScheme(HwBroadcastRadarUtil.KEY_PACKAGE);
                this.mContext.registerReceiver(this.mBroadcastReceiver, filter);
                sdFilter = new IntentFilter();
                sdFilter.addAction("android.intent.action.EXTERNAL_APPLICATIONS_AVAILABLE");
                sdFilter.addAction("android.intent.action.EXTERNAL_APPLICATIONS_UNAVAILABLE");
                this.mContext.registerReceiver(this.mBroadcastReceiver, sdFilter);
            } catch (IOException e16) {
                e3 = e16;
                Slog.e(TAG, "Error in processed file", e3);
                if (randomAccessFile2 != null) {
                    randomAccessFile2.close();
                }
                if (randomAccessFile3 != null) {
                    randomAccessFile3.close();
                }
                synchronized (this.mQueueLock) {
                    this.mFullBackupQueue = readFullBackupSchedule();
                }
                filter = new IntentFilter();
                filter.addAction("android.intent.action.PACKAGE_ADDED");
                filter.addAction("android.intent.action.PACKAGE_REMOVED");
                filter.addAction("android.intent.action.PACKAGE_CHANGED");
                filter.addDataScheme(HwBroadcastRadarUtil.KEY_PACKAGE);
                this.mContext.registerReceiver(this.mBroadcastReceiver, filter);
                sdFilter = new IntentFilter();
                sdFilter.addAction("android.intent.action.EXTERNAL_APPLICATIONS_AVAILABLE");
                sdFilter.addAction("android.intent.action.EXTERNAL_APPLICATIONS_UNAVAILABLE");
                this.mContext.registerReceiver(this.mBroadcastReceiver, sdFilter);
            }
        }
        synchronized (this.mQueueLock) {
            this.mFullBackupQueue = readFullBackupSchedule();
        }
        filter = new IntentFilter();
        filter.addAction("android.intent.action.PACKAGE_ADDED");
        filter.addAction("android.intent.action.PACKAGE_REMOVED");
        filter.addAction("android.intent.action.PACKAGE_CHANGED");
        filter.addDataScheme(HwBroadcastRadarUtil.KEY_PACKAGE);
        this.mContext.registerReceiver(this.mBroadcastReceiver, filter);
        sdFilter = new IntentFilter();
        sdFilter.addAction("android.intent.action.EXTERNAL_APPLICATIONS_AVAILABLE");
        sdFilter.addAction("android.intent.action.EXTERNAL_APPLICATIONS_UNAVAILABLE");
        this.mContext.registerReceiver(this.mBroadcastReceiver, sdFilter);
    }

    private ArrayList<FullBackupEntry> readFullBackupSchedule() {
        BufferedInputStream bufStream;
        Exception e;
        Object fstream;
        ArrayList<FullBackupEntry> arrayList;
        Throwable th;
        boolean changed = false;
        ArrayList<FullBackupEntry> arrayList2 = null;
        List<PackageInfo> apps = PackageManagerBackupAgent.getStorableApplications(this.mPackageManager);
        if (this.mFullBackupScheduleFile.exists()) {
            AutoCloseable autoCloseable = null;
            BufferedInputStream bufStream2 = null;
            DataInputStream in = null;
            try {
                InputStream fileInputStream = new FileInputStream(this.mFullBackupScheduleFile);
                try {
                    bufStream = new BufferedInputStream(fileInputStream);
                } catch (Exception e2) {
                    e = e2;
                    fstream = fileInputStream;
                    try {
                        Slog.e(TAG, "Unable to read backup schedule", e);
                        this.mFullBackupScheduleFile.delete();
                        arrayList2 = null;
                        IoUtils.closeQuietly(in);
                        IoUtils.closeQuietly(bufStream2);
                        IoUtils.closeQuietly(autoCloseable);
                        if (arrayList2 == null) {
                            changed = true;
                            arrayList = new ArrayList(apps.size());
                            for (PackageInfo info : apps) {
                                arrayList.add(new FullBackupEntry(info.packageName, 0));
                            }
                        }
                        if (changed) {
                            writeFullBackupScheduleAsync();
                        }
                        return arrayList2;
                    } catch (Throwable th2) {
                        th = th2;
                        IoUtils.closeQuietly(in);
                        IoUtils.closeQuietly(bufStream2);
                        IoUtils.closeQuietly(autoCloseable);
                        throw th;
                    }
                } catch (Throwable th3) {
                    th = th3;
                    fstream = fileInputStream;
                    IoUtils.closeQuietly(in);
                    IoUtils.closeQuietly(bufStream2);
                    IoUtils.closeQuietly(autoCloseable);
                    throw th;
                }
                try {
                    DataInputStream dataInputStream = new DataInputStream(bufStream);
                    try {
                        int version = dataInputStream.readInt();
                        if (version != 1) {
                            Slog.e(TAG, "Unknown backup schedule version " + version);
                            IoUtils.closeQuietly(dataInputStream);
                            IoUtils.closeQuietly(bufStream);
                            IoUtils.closeQuietly(fileInputStream);
                            return null;
                        }
                        int N = dataInputStream.readInt();
                        arrayList = new ArrayList(N);
                        try {
                            HashSet<String> foundApps = new HashSet(N);
                            int i = 0;
                            while (i < N) {
                                String pkgName = dataInputStream.readUTF();
                                long lastBackup = dataInputStream.readLong();
                                foundApps.add(pkgName);
                                try {
                                    PackageInfo pkg = this.mPackageManager.getPackageInfo(pkgName, 0);
                                    if (appGetsFullBackup(pkg) && appIsEligibleForBackup(pkg.applicationInfo)) {
                                        arrayList.add(new FullBackupEntry(pkgName, lastBackup));
                                        i++;
                                    } else {
                                        if (DEBUG) {
                                            Slog.i(TAG, "Package " + pkgName + " no longer eligible for full backup");
                                        }
                                        i++;
                                    }
                                } catch (NameNotFoundException e3) {
                                    if (DEBUG) {
                                        Slog.i(TAG, "Package " + pkgName + " not installed; dropping from full backup");
                                    }
                                }
                            }
                            for (PackageInfo app : apps) {
                                if (appGetsFullBackup(app) && appIsEligibleForBackup(app.applicationInfo) && !foundApps.contains(app.packageName)) {
                                    arrayList.add(new FullBackupEntry(app.packageName, 0));
                                    changed = true;
                                }
                            }
                            Collections.sort(arrayList);
                            IoUtils.closeQuietly(dataInputStream);
                            IoUtils.closeQuietly(bufStream);
                            IoUtils.closeQuietly(fileInputStream);
                            arrayList2 = arrayList;
                        } catch (Exception e4) {
                            e = e4;
                            in = dataInputStream;
                            bufStream2 = bufStream;
                            autoCloseable = fileInputStream;
                            arrayList2 = arrayList;
                        } catch (Throwable th4) {
                            th = th4;
                            in = dataInputStream;
                            bufStream2 = bufStream;
                            fstream = fileInputStream;
                            arrayList2 = arrayList;
                        }
                    } catch (Exception e5) {
                        e = e5;
                        in = dataInputStream;
                        bufStream2 = bufStream;
                        fstream = fileInputStream;
                        Slog.e(TAG, "Unable to read backup schedule", e);
                        this.mFullBackupScheduleFile.delete();
                        arrayList2 = null;
                        IoUtils.closeQuietly(in);
                        IoUtils.closeQuietly(bufStream2);
                        IoUtils.closeQuietly(autoCloseable);
                        if (arrayList2 == null) {
                            changed = true;
                            arrayList = new ArrayList(apps.size());
                            for (PackageInfo info2 : apps) {
                                arrayList.add(new FullBackupEntry(info2.packageName, 0));
                            }
                        }
                        if (changed) {
                            writeFullBackupScheduleAsync();
                        }
                        return arrayList2;
                    } catch (Throwable th5) {
                        th = th5;
                        in = dataInputStream;
                        bufStream2 = bufStream;
                        fstream = fileInputStream;
                        IoUtils.closeQuietly(in);
                        IoUtils.closeQuietly(bufStream2);
                        IoUtils.closeQuietly(autoCloseable);
                        throw th;
                    }
                } catch (Exception e6) {
                    e = e6;
                    bufStream2 = bufStream;
                    fstream = fileInputStream;
                    Slog.e(TAG, "Unable to read backup schedule", e);
                    this.mFullBackupScheduleFile.delete();
                    arrayList2 = null;
                    IoUtils.closeQuietly(in);
                    IoUtils.closeQuietly(bufStream2);
                    IoUtils.closeQuietly(autoCloseable);
                    if (arrayList2 == null) {
                        changed = true;
                        arrayList = new ArrayList(apps.size());
                        for (PackageInfo info22 : apps) {
                            arrayList.add(new FullBackupEntry(info22.packageName, 0));
                        }
                    }
                    if (changed) {
                        writeFullBackupScheduleAsync();
                    }
                    return arrayList2;
                } catch (Throwable th6) {
                    th = th6;
                    bufStream2 = bufStream;
                    fstream = fileInputStream;
                    IoUtils.closeQuietly(in);
                    IoUtils.closeQuietly(bufStream2);
                    IoUtils.closeQuietly(autoCloseable);
                    throw th;
                }
            } catch (Exception e7) {
                e = e7;
                Slog.e(TAG, "Unable to read backup schedule", e);
                this.mFullBackupScheduleFile.delete();
                arrayList2 = null;
                IoUtils.closeQuietly(in);
                IoUtils.closeQuietly(bufStream2);
                IoUtils.closeQuietly(autoCloseable);
                if (arrayList2 == null) {
                    changed = true;
                    arrayList = new ArrayList(apps.size());
                    for (PackageInfo info222 : apps) {
                        arrayList.add(new FullBackupEntry(info222.packageName, 0));
                    }
                }
                if (changed) {
                    writeFullBackupScheduleAsync();
                }
                return arrayList2;
            }
        }
        if (arrayList2 == null) {
            changed = true;
            arrayList = new ArrayList(apps.size());
            for (PackageInfo info2222 : apps) {
                if (appGetsFullBackup(info2222) && appIsEligibleForBackup(info2222.applicationInfo)) {
                    arrayList.add(new FullBackupEntry(info2222.packageName, 0));
                }
            }
        }
        if (changed) {
            writeFullBackupScheduleAsync();
        }
        return arrayList2;
    }

    private void writeFullBackupScheduleAsync() {
        this.mBackupHandler.removeCallbacks(this.mFullBackupScheduleWriter);
        this.mBackupHandler.post(this.mFullBackupScheduleWriter);
    }

    private void parseLeftoverJournals() {
        RandomAccessFile randomAccessFile;
        Exception e;
        Throwable th;
        int i = 0;
        File[] listFiles = this.mJournalDir.listFiles();
        int length = listFiles.length;
        while (i < length) {
            File f = listFiles[i];
            if (this.mJournal == null || f.compareTo(this.mJournal) != 0) {
                randomAccessFile = null;
                try {
                    Slog.i(TAG, "Found stale backup journal, scheduling");
                    RandomAccessFile in = new RandomAccessFile(f, "r");
                    while (true) {
                        try {
                            dataChangedImpl(in.readUTF());
                        } catch (EOFException e2) {
                            randomAccessFile = in;
                        } catch (Exception e3) {
                            e = e3;
                            randomAccessFile = in;
                        } catch (Throwable th2) {
                            th = th2;
                            randomAccessFile = in;
                        }
                    }
                } catch (EOFException e4) {
                    if (randomAccessFile != null) {
                        try {
                            randomAccessFile.close();
                        } catch (IOException e5) {
                        }
                    }
                    f.delete();
                    i++;
                } catch (Exception e6) {
                    e = e6;
                    try {
                        Slog.e(TAG, "Can't read " + f, e);
                        if (randomAccessFile != null) {
                            try {
                                randomAccessFile.close();
                            } catch (IOException e7) {
                            }
                        }
                        f.delete();
                        i++;
                    } catch (Throwable th3) {
                        th = th3;
                    }
                }
            } else {
                i++;
            }
        }
        return;
        if (randomAccessFile != null) {
            try {
                randomAccessFile.close();
            } catch (IOException e8) {
            }
        }
        f.delete();
        throw th;
        f.delete();
        throw th;
    }

    private SecretKey buildPasswordKey(String algorithm, String pw, byte[] salt, int rounds) {
        return buildCharArrayKey(algorithm, pw.toCharArray(), salt, rounds);
    }

    private SecretKey buildCharArrayKey(String algorithm, char[] pwArray, byte[] salt, int rounds) {
        try {
            return SecretKeyFactory.getInstance(algorithm).generateSecret(new PBEKeySpec(pwArray, salt, rounds, 256));
        } catch (InvalidKeySpecException e) {
            Slog.e(TAG, "Invalid key spec for PBKDF2!");
            return null;
        } catch (NoSuchAlgorithmException e2) {
            Slog.e(TAG, "PBKDF2 unavailable!");
            return null;
        }
    }

    private String buildPasswordHash(String algorithm, String pw, byte[] salt, int rounds) {
        SecretKey key = buildPasswordKey(algorithm, pw, salt, rounds);
        if (key != null) {
            return byteArrayToHex(key.getEncoded());
        }
        return null;
    }

    private String byteArrayToHex(byte[] data) {
        StringBuilder buf = new StringBuilder(data.length * 2);
        for (byte toHexString : data) {
            buf.append(Byte.toHexString(toHexString, true));
        }
        return buf.toString();
    }

    private byte[] hexToByteArray(String digits) {
        int bytes = digits.length() / 2;
        if (bytes * 2 != digits.length()) {
            throw new IllegalArgumentException("Hex string must have an even number of digits");
        }
        byte[] result = new byte[bytes];
        for (int i = 0; i < digits.length(); i += 2) {
            result[i / 2] = (byte) Integer.parseInt(digits.substring(i, i + 2), 16);
        }
        return result;
    }

    private byte[] makeKeyChecksum(String algorithm, byte[] pwBytes, byte[] salt, int rounds) {
        char[] mkAsChar = new char[pwBytes.length];
        for (int i = 0; i < pwBytes.length; i++) {
            mkAsChar[i] = (char) pwBytes[i];
        }
        return buildCharArrayKey(algorithm, mkAsChar, salt, rounds).getEncoded();
    }

    private byte[] randomBytes(int bits) {
        byte[] array = new byte[(bits / 8)];
        this.mRng.nextBytes(array);
        return array;
    }

    boolean passwordMatchesSaved(String algorithm, String candidatePw, int rounds) {
        if (this.mPasswordHash == null) {
            if (candidatePw == null || "".equals(candidatePw)) {
                return true;
            }
        } else if (candidatePw != null && candidatePw.length() > 0) {
            if (this.mPasswordHash.equalsIgnoreCase(buildPasswordHash(algorithm, candidatePw, this.mPasswordSalt, rounds))) {
                return true;
            }
        }
        return false;
    }

    public boolean setBackupPassword(String currentPw, String newPw) {
        Throwable th;
        this.mContext.enforceCallingOrSelfPermission("android.permission.BACKUP", "setBackupPassword");
        boolean pbkdf2Fallback = this.mPasswordVersion < 2;
        if (!passwordMatchesSaved(PBKDF_CURRENT, currentPw, 10000) && (!pbkdf2Fallback || !passwordMatchesSaved(PBKDF_FALLBACK, currentPw, 10000))) {
            return false;
        }
        this.mPasswordVersion = 2;
        FileOutputStream pwFout = null;
        DataOutputStream dataOutputStream = null;
        try {
            DataOutputStream pwOut;
            FileOutputStream pwFout2 = new FileOutputStream(this.mPasswordVersionFile);
            try {
                pwOut = new DataOutputStream(pwFout2);
            } catch (IOException e) {
                pwFout = pwFout2;
                try {
                    Slog.e(TAG, "Unable to write backup pw version; password not changed");
                    if (dataOutputStream != null) {
                        try {
                            dataOutputStream.close();
                        } catch (IOException e2) {
                            Slog.w(TAG, "Unable to close pw version record");
                            return false;
                        }
                    }
                    if (pwFout != null) {
                        pwFout.close();
                    }
                    return false;
                } catch (Throwable th2) {
                    th = th2;
                    if (dataOutputStream != null) {
                        try {
                            dataOutputStream.close();
                        } catch (IOException e3) {
                            Slog.w(TAG, "Unable to close pw version record");
                            throw th;
                        }
                    }
                    if (pwFout != null) {
                        pwFout.close();
                    }
                    throw th;
                }
            } catch (Throwable th3) {
                th = th3;
                pwFout = pwFout2;
                if (dataOutputStream != null) {
                    dataOutputStream.close();
                }
                if (pwFout != null) {
                    pwFout.close();
                }
                throw th;
            }
            try {
                pwOut.writeInt(this.mPasswordVersion);
                if (pwOut != null) {
                    try {
                        pwOut.close();
                    } catch (IOException e4) {
                        Slog.w(TAG, "Unable to close pw version record");
                    }
                }
                if (pwFout2 != null) {
                    pwFout2.close();
                }
                if (newPw != null && !newPw.isEmpty()) {
                    try {
                        byte[] salt = randomBytes(512);
                        String newPwHash = buildPasswordHash(PBKDF_CURRENT, newPw, salt, 10000);
                        OutputStream pwf = null;
                        OutputStream buffer = null;
                        DataOutputStream dataOutputStream2 = null;
                        try {
                            OutputStream fileOutputStream = new FileOutputStream(this.mPasswordHashFile);
                            try {
                                OutputStream buffer2 = new BufferedOutputStream(fileOutputStream);
                                try {
                                    DataOutputStream out = new DataOutputStream(buffer2);
                                    try {
                                        out.writeInt(salt.length);
                                        out.write(salt);
                                        out.writeUTF(newPwHash);
                                        out.flush();
                                        this.mPasswordHash = newPwHash;
                                        this.mPasswordSalt = salt;
                                        if (out != null) {
                                            out.close();
                                        }
                                        if (buffer2 != null) {
                                            buffer2.close();
                                        }
                                        if (fileOutputStream != null) {
                                            fileOutputStream.close();
                                        }
                                        return true;
                                    } catch (Throwable th4) {
                                        th = th4;
                                        dataOutputStream2 = out;
                                        buffer = buffer2;
                                        pwf = fileOutputStream;
                                        if (dataOutputStream2 != null) {
                                            dataOutputStream2.close();
                                        }
                                        if (buffer != null) {
                                            buffer.close();
                                        }
                                        if (pwf != null) {
                                            pwf.close();
                                        }
                                        throw th;
                                    }
                                } catch (Throwable th5) {
                                    th = th5;
                                    buffer = buffer2;
                                    pwf = fileOutputStream;
                                    if (dataOutputStream2 != null) {
                                        dataOutputStream2.close();
                                    }
                                    if (buffer != null) {
                                        buffer.close();
                                    }
                                    if (pwf != null) {
                                        pwf.close();
                                    }
                                    throw th;
                                }
                            } catch (Throwable th6) {
                                th = th6;
                                pwf = fileOutputStream;
                                if (dataOutputStream2 != null) {
                                    dataOutputStream2.close();
                                }
                                if (buffer != null) {
                                    buffer.close();
                                }
                                if (pwf != null) {
                                    pwf.close();
                                }
                                throw th;
                            }
                        } catch (Throwable th7) {
                            th = th7;
                            if (dataOutputStream2 != null) {
                                dataOutputStream2.close();
                            }
                            if (buffer != null) {
                                buffer.close();
                            }
                            if (pwf != null) {
                                pwf.close();
                            }
                            throw th;
                        }
                    } catch (IOException e5) {
                        Slog.e(TAG, "Unable to set backup password");
                        return false;
                    }
                } else if (!this.mPasswordHashFile.exists() || this.mPasswordHashFile.delete()) {
                    this.mPasswordHash = null;
                    this.mPasswordSalt = null;
                    return true;
                } else {
                    Slog.e(TAG, "Unable to clear backup password");
                    return false;
                }
            } catch (IOException e6) {
                dataOutputStream = pwOut;
                pwFout = pwFout2;
                Slog.e(TAG, "Unable to write backup pw version; password not changed");
                if (dataOutputStream != null) {
                    dataOutputStream.close();
                }
                if (pwFout != null) {
                    pwFout.close();
                }
                return false;
            } catch (Throwable th8) {
                th = th8;
                dataOutputStream = pwOut;
                pwFout = pwFout2;
                if (dataOutputStream != null) {
                    dataOutputStream.close();
                }
                if (pwFout != null) {
                    pwFout.close();
                }
                throw th;
            }
        } catch (IOException e7) {
            Slog.e(TAG, "Unable to write backup pw version; password not changed");
            if (dataOutputStream != null) {
                dataOutputStream.close();
            }
            if (pwFout != null) {
                pwFout.close();
            }
            return false;
        }
    }

    public boolean hasBackupPassword() {
        this.mContext.enforceCallingOrSelfPermission("android.permission.BACKUP", "hasBackupPassword");
        if (this.mPasswordHash == null || this.mPasswordHash.length() <= 0) {
            return false;
        }
        return true;
    }

    private boolean backupPasswordMatches(String currentPw) {
        if (hasBackupPassword()) {
            boolean pbkdf2Fallback = this.mPasswordVersion < 2;
            if (!(passwordMatchesSaved(PBKDF_CURRENT, currentPw, 10000) || (pbkdf2Fallback && passwordMatchesSaved(PBKDF_FALLBACK, currentPw, 10000)))) {
                if (DEBUG) {
                    Slog.w(TAG, "Backup password mismatch; aborting");
                }
                return false;
            }
        }
        return true;
    }

    void recordInitPendingLocked(boolean isPending, String transportName) {
        this.mBackupHandler.removeMessages(11);
        try {
            IBackupTransport transport = getTransport(transportName);
            if (transport != null) {
                File initPendingFile = new File(new File(this.mBaseStateDir, transport.transportDirName()), INIT_SENTINEL_FILE_NAME);
                if (isPending) {
                    this.mPendingInits.add(transportName);
                    try {
                        new FileOutputStream(initPendingFile).close();
                    } catch (IOException e) {
                    }
                } else {
                    initPendingFile.delete();
                    this.mPendingInits.remove(transportName);
                }
                return;
            }
        } catch (RemoteException e2) {
        }
        if (isPending) {
            this.mPendingInits.add(transportName);
            this.mBackupHandler.sendMessageDelayed(this.mBackupHandler.obtainMessage(11, isPending ? 1 : 0, 0, transportName), 3600000);
        }
    }

    void resetBackupState(File stateFileDir) {
        synchronized (this.mQueueLock) {
            this.mEverStoredApps.clear();
            this.mEverStored.delete();
            this.mCurrentToken = 0;
            writeRestoreTokens();
            for (File sf : stateFileDir.listFiles()) {
                if (!sf.getName().equals(INIT_SENTINEL_FILE_NAME)) {
                    sf.delete();
                }
            }
        }
        synchronized (this.mBackupParticipants) {
            int N = this.mBackupParticipants.size();
            for (int i = 0; i < N; i++) {
                HashSet<String> participants = (HashSet) this.mBackupParticipants.valueAt(i);
                if (participants != null) {
                    for (String packageName : participants) {
                        dataChangedImpl(packageName);
                    }
                }
            }
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void registerTransport(String name, String component, IBackupTransport transport) {
        synchronized (this.mTransports) {
            if (DEBUG) {
                Slog.v(TAG, "Registering transport " + component + "::" + name + " = " + transport);
            }
            if (transport != null) {
                this.mTransports.put(name, transport);
                this.mTransportNames.put(component, name);
            } else {
                this.mTransports.remove(this.mTransportNames.get(component));
                this.mTransportNames.remove(component);
            }
        }
    }

    void checkForTransportAndBind(PackageInfo pkgInfo) {
        List<ResolveInfo> hosts = this.mPackageManager.queryIntentServicesAsUser(new Intent(this.mTransportServiceIntent).setPackage(pkgInfo.packageName), 0, 0);
        if (hosts != null) {
            int N = hosts.size();
            for (int i = 0; i < N; i++) {
                tryBindTransport(((ResolveInfo) hosts.get(i)).serviceInfo);
            }
        }
    }

    boolean tryBindTransport(ServiceInfo info) {
        try {
            if ((this.mPackageManager.getPackageInfo(info.packageName, 0).applicationInfo.privateFlags & 8) != 0) {
                return bindTransport(info);
            }
            Slog.w(TAG, "Transport package " + info.packageName + " not privileged");
            return false;
        } catch (NameNotFoundException e) {
            Slog.w(TAG, "Problem resolving transport package " + info.packageName);
        }
    }

    boolean bindTransport(ServiceInfo transport) {
        ComponentName svcName = new ComponentName(transport.packageName, transport.name);
        if (this.mTransportWhitelist.contains(svcName)) {
            TransportConnection connection;
            Intent intent = new Intent(this.mTransportServiceIntent);
            intent.setComponent(svcName);
            synchronized (this.mTransports) {
                connection = (TransportConnection) this.mTransportConnections.get(transport.packageName);
                if (connection == null) {
                    connection = new TransportConnection(transport);
                    this.mTransportConnections.put(transport.packageName, connection);
                } else {
                    this.mContext.unbindService(connection);
                }
            }
            return this.mContext.bindServiceAsUser(intent, connection, 1, UserHandle.SYSTEM);
        }
        Slog.w(TAG, "Proposed transport " + svcName + " not whitelisted; ignoring");
        return false;
    }

    void addPackageParticipantsLocked(String[] packageNames) {
        List<PackageInfo> targetApps = allAgentPackages();
        if (packageNames != null) {
            for (String packageName : packageNames) {
                addPackageParticipantsLockedInner(packageName, targetApps);
            }
            return;
        }
        addPackageParticipantsLockedInner(null, targetApps);
    }

    private void addPackageParticipantsLockedInner(String packageName, List<PackageInfo> targetPkgs) {
        for (PackageInfo pkg : targetPkgs) {
            if (packageName == null || pkg.packageName.equals(packageName)) {
                int uid = pkg.applicationInfo.uid;
                HashSet<String> set = (HashSet) this.mBackupParticipants.get(uid);
                if (set == null) {
                    set = new HashSet();
                    this.mBackupParticipants.put(uid, set);
                }
                set.add(pkg.packageName);
                dataChangedImpl(pkg.packageName);
            }
        }
    }

    void removePackageParticipantsLocked(String[] packageNames, int oldUid) {
        if (packageNames == null) {
            Slog.w(TAG, "removePackageParticipants with null list");
            return;
        }
        for (String pkg : packageNames) {
            HashSet<String> set = (HashSet) this.mBackupParticipants.get(oldUid);
            if (set != null && set.contains(pkg)) {
                removePackageFromSetLocked(set, pkg);
                if (set.isEmpty()) {
                    this.mBackupParticipants.remove(oldUid);
                }
            }
        }
    }

    private void removePackageFromSetLocked(HashSet<String> set, String packageName) {
        if (set.contains(packageName)) {
            set.remove(packageName);
            this.mPendingBackups.remove(packageName);
        }
    }

    List<PackageInfo> allAgentPackages() {
        List<PackageInfo> packages = this.mPackageManager.getInstalledPackages(64);
        int a = packages.size() - 1;
        while (a >= 0) {
            PackageInfo pkg = (PackageInfo) packages.get(a);
            try {
                ApplicationInfo app = pkg.applicationInfo;
                if ((app.flags & DumpState.DUMP_VERSION) == 0 || app.backupAgentName == null || (app.flags & 67108864) != 0) {
                    packages.remove(a);
                    a--;
                } else {
                    app = this.mPackageManager.getApplicationInfo(pkg.packageName, 1024);
                    pkg.applicationInfo.sharedLibraryFiles = app.sharedLibraryFiles;
                    a--;
                }
            } catch (NameNotFoundException e) {
                packages.remove(a);
            }
        }
        return packages;
    }

    void logBackupComplete(String packageName) {
        Throwable th;
        if (!packageName.equals(PACKAGE_MANAGER_SENTINEL)) {
            synchronized (this.mEverStoredApps) {
                if (this.mEverStoredApps.add(packageName)) {
                    RandomAccessFile randomAccessFile = null;
                    try {
                        RandomAccessFile out = new RandomAccessFile(this.mEverStored, "rws");
                        try {
                            out.seek(out.length());
                            out.writeUTF(packageName);
                            if (out != null) {
                                try {
                                    out.close();
                                } catch (IOException e) {
                                }
                            }
                            randomAccessFile = out;
                        } catch (IOException e2) {
                            randomAccessFile = out;
                            try {
                                Slog.e(TAG, "Can't log backup of " + packageName + " to " + this.mEverStored);
                                if (randomAccessFile != null) {
                                    try {
                                        randomAccessFile.close();
                                    } catch (IOException e3) {
                                    }
                                }
                                return;
                            } catch (Throwable th2) {
                                th = th2;
                                if (randomAccessFile != null) {
                                    try {
                                        randomAccessFile.close();
                                    } catch (IOException e4) {
                                    }
                                }
                                throw th;
                            }
                        } catch (Throwable th3) {
                            th = th3;
                            randomAccessFile = out;
                            if (randomAccessFile != null) {
                                randomAccessFile.close();
                            }
                            throw th;
                        }
                    } catch (IOException e5) {
                        Slog.e(TAG, "Can't log backup of " + packageName + " to " + this.mEverStored);
                        if (randomAccessFile != null) {
                            randomAccessFile.close();
                        }
                        return;
                    }
                }
                return;
            }
        }
        return;
    }

    void removeEverBackedUp(String packageName) {
        IOException e;
        Throwable th;
        if (DEBUG) {
            Slog.v(TAG, "Removing backed-up knowledge of " + packageName);
        }
        synchronized (this.mEverStoredApps) {
            File tempKnownFile = new File(this.mBaseStateDir, "processed.new");
            RandomAccessFile randomAccessFile = null;
            try {
                RandomAccessFile known = new RandomAccessFile(tempKnownFile, "rws");
                try {
                    this.mEverStoredApps.remove(packageName);
                    for (String s : this.mEverStoredApps) {
                        known.writeUTF(s);
                    }
                    known.close();
                    randomAccessFile = null;
                    if (!tempKnownFile.renameTo(this.mEverStored)) {
                        throw new IOException("Can't rename " + tempKnownFile + " to " + this.mEverStored);
                    }
                } catch (IOException e2) {
                    e = e2;
                    randomAccessFile = known;
                    try {
                        Slog.w(TAG, "Error rewriting " + this.mEverStored, e);
                        this.mEverStoredApps.clear();
                        tempKnownFile.delete();
                        this.mEverStored.delete();
                        if (randomAccessFile != null) {
                            try {
                                randomAccessFile.close();
                            } catch (IOException e3) {
                            }
                        }
                        return;
                    } catch (Throwable th2) {
                        th = th2;
                        if (randomAccessFile != null) {
                            try {
                                randomAccessFile.close();
                            } catch (IOException e4) {
                            }
                        }
                        throw th;
                    }
                } catch (Throwable th3) {
                    th = th3;
                    randomAccessFile = known;
                    if (randomAccessFile != null) {
                        randomAccessFile.close();
                    }
                    throw th;
                }
            } catch (IOException e5) {
                e = e5;
                Slog.w(TAG, "Error rewriting " + this.mEverStored, e);
                this.mEverStoredApps.clear();
                tempKnownFile.delete();
                this.mEverStored.delete();
                if (randomAccessFile != null) {
                    randomAccessFile.close();
                }
                return;
            }
        }
        return;
    }

    void writeRestoreTokens() {
        try {
            RandomAccessFile af = new RandomAccessFile(this.mTokenFile, "rwd");
            af.writeInt(1);
            af.writeLong(this.mAncestralToken);
            af.writeLong(this.mCurrentToken);
            if (this.mAncestralPackages == null) {
                af.writeInt(-1);
            } else {
                af.writeInt(this.mAncestralPackages.size());
                if (DEBUG) {
                    Slog.v(TAG, "Ancestral packages:  " + this.mAncestralPackages.size());
                }
                for (String pkgName : this.mAncestralPackages) {
                    af.writeUTF(pkgName);
                }
            }
            af.close();
        } catch (IOException e) {
            Slog.w(TAG, "Unable to write token file:", e);
        }
    }

    private IBackupTransport getTransport(String transportName) {
        IBackupTransport transport;
        synchronized (this.mTransports) {
            transport = (IBackupTransport) this.mTransports.get(transportName);
            if (transport == null) {
                Slog.w(TAG, "Requested unavailable transport: " + transportName);
            }
        }
        return transport;
    }

    private String getTransportName(IBackupTransport transport) {
        synchronized (this.mTransports) {
            int N = this.mTransports.size();
            for (int i = 0; i < N; i++) {
                if (((IBackupTransport) this.mTransports.valueAt(i)).equals(transport)) {
                    String str = (String) this.mTransports.keyAt(i);
                    return str;
                }
            }
            return null;
        }
    }

    IBackupAgent bindToAgentSynchronous(ApplicationInfo app, int mode) {
        IBackupAgent agent = null;
        synchronized (this.mAgentConnectLock) {
            this.mConnecting = true;
            this.mConnectedAgent = null;
            try {
                if (this.mActivityManager.bindBackupAgent(app.packageName, mode, 0)) {
                    Slog.d(TAG, "awaiting agent for " + app);
                    long timeoutMark = System.currentTimeMillis() + 10000;
                    while (this.mConnecting && this.mConnectedAgent == null && System.currentTimeMillis() < timeoutMark) {
                        try {
                            this.mAgentConnectLock.wait(5000);
                        } catch (InterruptedException e) {
                            Slog.w(TAG, "Interrupted: " + e);
                            this.mActivityManager.clearPendingBackup();
                            return null;
                        }
                    }
                    if (this.mConnecting) {
                        Slog.w(TAG, "Timeout waiting for agent " + app);
                        this.mActivityManager.clearPendingBackup();
                        return null;
                    }
                    if (DEBUG) {
                        Slog.i(TAG, "got agent " + this.mConnectedAgent);
                    }
                    agent = this.mConnectedAgent;
                }
            } catch (RemoteException e2) {
            }
        }
        return agent;
    }

    void clearApplicationDataSynchronous(String packageName) {
        try {
            if ((this.mPackageManager.getPackageInfo(packageName, 0).applicationInfo.flags & 64) != 0) {
                ClearDataObserver observer = new ClearDataObserver();
                synchronized (this.mClearDataLock) {
                    this.mClearingData = true;
                    try {
                        this.mActivityManager.clearApplicationUserData(packageName, observer, 0);
                    } catch (RemoteException e) {
                    }
                    long timeoutMark = System.currentTimeMillis() + 10000;
                    while (this.mClearingData && System.currentTimeMillis() < timeoutMark) {
                        try {
                            this.mClearDataLock.wait(5000);
                        } catch (InterruptedException e2) {
                            this.mClearingData = false;
                        }
                    }
                }
            }
        } catch (NameNotFoundException e3) {
            Slog.w(TAG, "Tried to clear data for " + packageName + " but not found");
        }
    }

    public long getAvailableRestoreToken(String packageName) {
        this.mContext.enforceCallingOrSelfPermission("android.permission.BACKUP", "getAvailableRestoreToken");
        long token = this.mAncestralToken;
        synchronized (this.mQueueLock) {
            if (this.mEverStoredApps.contains(packageName)) {
                token = this.mCurrentToken;
            }
        }
        return token;
    }

    public int requestBackup(String[] packages, IBackupObserver observer) {
        this.mContext.enforceCallingPermission("android.permission.BACKUP", "requestBackup");
        if (packages == null || packages.length < 1) {
            Slog.e(TAG, "No packages named for backup request");
            sendBackupFinished(observer, JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE);
            throw new IllegalArgumentException("No packages are provided for backup");
        }
        IBackupTransport transport = getTransport(this.mCurrentTransport);
        if (transport == null) {
            sendBackupFinished(observer, JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE);
            return JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE;
        }
        ArrayList<String> fullBackupList = new ArrayList();
        ArrayList<String> kvBackupList = new ArrayList();
        for (String packageName : packages) {
            try {
                PackageInfo packageInfo = this.mPackageManager.getPackageInfo(packageName, 64);
                if (!appIsEligibleForBackup(packageInfo.applicationInfo)) {
                    sendBackupOnPackageResult(observer, packageName, -2001);
                } else if (appGetsFullBackup(packageInfo)) {
                    fullBackupList.add(packageInfo.packageName);
                } else {
                    kvBackupList.add(packageInfo.packageName);
                }
            } catch (NameNotFoundException e) {
                sendBackupOnPackageResult(observer, packageName, -2002);
            }
        }
        EventLog.writeEvent(EventLogTags.BACKUP_REQUESTED, new Object[]{Integer.valueOf(packages.length), Integer.valueOf(kvBackupList.size()), Integer.valueOf(fullBackupList.size())});
        try {
            String dirName = transport.transportDirName();
            Message msg = this.mBackupHandler.obtainMessage(15);
            msg.obj = new BackupParams(transport, dirName, kvBackupList, fullBackupList, observer, true);
            this.mBackupHandler.sendMessage(msg);
            return 0;
        } catch (RemoteException e2) {
            Slog.e(TAG, "Transport became unavailable while attempting backup");
            sendBackupFinished(observer, JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE);
            return JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE;
        }
    }

    void prepareOperationTimeout(int token, long interval, BackupRestoreTask callback) {
        synchronized (this.mCurrentOpLock) {
            this.mCurrentOperations.put(token, new Operation(0, callback));
            this.mBackupHandler.sendMessageDelayed(this.mBackupHandler.obtainMessage(7, token, 0, callback), interval);
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    boolean waitUntilOperationComplete(int token) {
        int finalState = 0;
        synchronized (this.mCurrentOpLock) {
            while (true) {
                Operation op = (Operation) this.mCurrentOperations.get(token);
                if (op != null) {
                    if (op.state != 0) {
                        break;
                    }
                    try {
                        this.mCurrentOpLock.wait();
                    } catch (InterruptedException e) {
                    }
                } else {
                    break;
                }
            }
        }
        this.mBackupHandler.removeMessages(7);
        if (finalState == 1) {
            return true;
        }
        return false;
    }

    void handleTimeout(int token, Object obj) {
        synchronized (this.mCurrentOpLock) {
            Operation op = (Operation) this.mCurrentOperations.get(token);
            int state = op != null ? op.state : -1;
            if (state == 1) {
                op = null;
                this.mCurrentOperations.delete(token);
            } else if (state == 0) {
                if (DEBUG) {
                    Slog.v(TAG, "TIMEOUT: token=" + Integer.toHexString(token));
                }
                op.state = -1;
            }
            this.mCurrentOpLock.notifyAll();
        }
        if (op != null && op.callback != null) {
            op.callback.handleTimeout();
        }
    }

    private void routeSocketDataToOutput(ParcelFileDescriptor inPipe, OutputStream out) throws IOException {
        DataInputStream in = new DataInputStream(new FileInputStream(inPipe.getFileDescriptor()));
        byte[] buffer = new byte[DumpState.DUMP_VERSION];
        while (true) {
            int chunkTotal = in.readInt();
            if (chunkTotal > 0) {
                while (chunkTotal > 0) {
                    int toRead;
                    if (chunkTotal > buffer.length) {
                        toRead = buffer.length;
                    } else {
                        toRead = chunkTotal;
                    }
                    int nRead = in.read(buffer, 0, toRead);
                    out.write(buffer, 0, nRead);
                    chunkTotal -= nRead;
                }
            } else {
                return;
            }
        }
    }

    void tearDownAgentAndKill(ApplicationInfo app) {
        if (app != null) {
            try {
                this.mActivityManager.unbindBackupAgent(app);
                if (app.uid >= 10000 && !app.packageName.equals("com.android.backupconfirm")) {
                    this.mActivityManager.killApplicationProcess(app.processName, app.uid);
                }
            } catch (RemoteException e) {
                Slog.d(TAG, "Lost app trying to shut down");
            }
        }
    }

    boolean deviceIsEncrypted() {
        boolean z = true;
        try {
            if (this.mMountService.getEncryptionState() == 1) {
                z = false;
            } else if (this.mMountService.getPasswordType() == 1) {
                z = false;
            }
            return z;
        } catch (Exception e) {
            Slog.e(TAG, "Unable to communicate with mount service: " + e.getMessage());
            return true;
        }
    }

    void scheduleNextFullBackupJob(long transportMinLatency) {
        synchronized (this.mQueueLock) {
            if (this.mFullBackupQueue.size() > 0) {
                long timeSinceLast = System.currentTimeMillis() - ((FullBackupEntry) this.mFullBackupQueue.get(0)).lastBackup;
                final long latency = Math.max(transportMinLatency, timeSinceLast < 86400000 ? 86400000 - timeSinceLast : 0);
                this.mBackupHandler.postDelayed(new Runnable() {
                    public void run() {
                        FullBackupJob.schedule(BackupManagerService.this.mContext, latency);
                    }
                }, 2500);
            } else if (DEBUG_SCHEDULING) {
                Slog.i(TAG, "Full backup queue empty; not scheduling");
            }
        }
    }

    void dequeueFullBackupLocked(String packageName) {
        for (int i = this.mFullBackupQueue.size() - 1; i >= 0; i--) {
            if (packageName.equals(((FullBackupEntry) this.mFullBackupQueue.get(i)).packageName)) {
                this.mFullBackupQueue.remove(i);
            }
        }
    }

    void enqueueFullBackup(String packageName, long lastBackedUp) {
        FullBackupEntry newEntry = new FullBackupEntry(packageName, lastBackedUp);
        synchronized (this.mQueueLock) {
            dequeueFullBackupLocked(packageName);
            int which = -1;
            if (lastBackedUp > 0) {
                which = this.mFullBackupQueue.size() - 1;
                while (which >= 0) {
                    if (((FullBackupEntry) this.mFullBackupQueue.get(which)).lastBackup <= lastBackedUp) {
                        this.mFullBackupQueue.add(which + 1, newEntry);
                        break;
                    }
                    which--;
                }
            }
            if (which < 0) {
                this.mFullBackupQueue.add(0, newEntry);
            }
        }
        writeFullBackupScheduleAsync();
    }

    private boolean fullBackupAllowable(IBackupTransport transport) {
        if (transport == null) {
            Slog.w(TAG, "Transport not present; full data backup not performed");
            return false;
        }
        try {
            if (new File(new File(this.mBaseStateDir, transport.transportDirName()), PACKAGE_MANAGER_SENTINEL).length() > 0) {
                return true;
            }
            if (DEBUG) {
                Slog.i(TAG, "Full backup requested but dataset not yet initialized");
            }
            return false;
        } catch (Exception e) {
            Slog.w(TAG, "Unable to contact transport");
            return false;
        }
    }

    boolean beginFullBackup(FullBackupJob scheduledJob) {
        long now = System.currentTimeMillis();
        FullBackupEntry entry = null;
        long latency = 86400000;
        if (!this.mEnabled || !this.mProvisioned) {
            return false;
        }
        if (this.mPowerManager.isPowerSaveMode()) {
            if (DEBUG) {
                Slog.i(TAG, "Deferring scheduled full backups in battery saver mode");
            }
            FullBackupJob.schedule(this.mContext, 14400000);
            return false;
        }
        if (DEBUG_SCHEDULING) {
            Slog.i(TAG, "Beginning scheduled full backup operation");
        }
        synchronized (this.mQueueLock) {
            if (this.mRunningFullBackupTask != null) {
                Slog.e(TAG, "Backup triggered but one already/still running!");
                return false;
            }
            boolean z = true;
            while (this.mFullBackupQueue.size() != 0) {
                boolean headBusy = false;
                if (!fullBackupAllowable(getTransport(this.mCurrentTransport))) {
                    z = false;
                    latency = 14400000;
                }
                if (z) {
                    entry = (FullBackupEntry) this.mFullBackupQueue.get(0);
                    long timeSinceRun = now - entry.lastBackup;
                    z = timeSinceRun >= 86400000;
                    if (!z) {
                        latency = 86400000 - timeSinceRun;
                        break;
                    }
                    try {
                        PackageInfo appInfo = this.mPackageManager.getPackageInfo(entry.packageName, 0);
                        if (appGetsFullBackup(appInfo)) {
                            if ((appInfo.applicationInfo.privateFlags & 4096) == 0) {
                                headBusy = this.mActivityManager.isAppForeground(appInfo.applicationInfo.uid);
                            } else {
                                headBusy = false;
                            }
                            if (headBusy) {
                                long nextEligible = (System.currentTimeMillis() + 3600000) + ((long) this.mTokenGenerator.nextInt(BUSY_BACKOFF_FUZZ));
                                if (DEBUG_SCHEDULING) {
                                    Slog.i(TAG, "Full backup time but " + entry.packageName + " is busy; deferring to " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(nextEligible)));
                                }
                                enqueueFullBackup(entry.packageName, nextEligible - 86400000);
                            }
                        } else {
                            this.mFullBackupQueue.remove(0);
                            headBusy = true;
                        }
                    } catch (NameNotFoundException e) {
                        z = this.mFullBackupQueue.size() > 1;
                    } catch (RemoteException e2) {
                    }
                }
                if (!headBusy) {
                    break;
                }
            }
            if (DEBUG) {
                Slog.i(TAG, "Backup queue empty; doing nothing");
            }
            z = false;
            if (z) {
                this.mFullBackupQueue.remove(0);
                this.mRunningFullBackupTask = new PerformFullTransportBackupTask(null, new String[]{entry.packageName}, true, scheduledJob, new CountDownLatch(1), null, false);
                this.mWakelock.acquire();
                new Thread(this.mRunningFullBackupTask).start();
                return true;
            }
            if (DEBUG_SCHEDULING) {
                Slog.i(TAG, "Nothing pending full backup; rescheduling +" + latency);
            }
            final long deferTime = latency;
            this.mBackupHandler.post(new Runnable() {
                public void run() {
                    FullBackupJob.schedule(BackupManagerService.this.mContext, deferTime);
                }
            });
            return false;
        }
    }

    void endFullBackup() {
        synchronized (this.mQueueLock) {
            if (this.mRunningFullBackupTask != null) {
                if (DEBUG_SCHEDULING) {
                    Slog.i(TAG, "Telling running backup to stop");
                }
                this.mRunningFullBackupTask.setRunning(false);
            }
        }
    }

    static boolean signaturesMatch(Signature[] storedSigs, PackageInfo target) {
        if (target == null) {
            return false;
        }
        if ((target.applicationInfo.flags & 1) != 0) {
            return true;
        }
        Signature[] deviceSigs = target.signatures;
        if ((storedSigs == null || storedSigs.length == 0) && (deviceSigs == null || deviceSigs.length == 0)) {
            return true;
        }
        if (storedSigs == null || deviceSigs == null) {
            return false;
        }
        for (Signature equals : storedSigs) {
            boolean match = false;
            for (Object equals2 : deviceSigs) {
                if (equals.equals(equals2)) {
                    match = true;
                    break;
                }
            }
            if (!match) {
                return false;
            }
        }
        return true;
    }

    void restoreWidgetData(String packageName, byte[] widgetData) {
        AppWidgetBackupBridge.restoreWidgetState(packageName, widgetData, 0);
    }

    private void dataChangedImpl(String packageName) {
        dataChangedImpl(packageName, dataChangedTargets(packageName));
    }

    private void dataChangedImpl(String packageName, HashSet<String> targets) {
        if (targets == null) {
            Slog.w(TAG, "dataChanged but no participant pkg='" + packageName + "'" + " uid=" + Binder.getCallingUid());
            return;
        }
        synchronized (this.mQueueLock) {
            if (targets.contains(packageName)) {
                if (this.mPendingBackups.put(packageName, new BackupRequest(packageName)) == null) {
                    writeToJournalLocked(packageName);
                }
            }
        }
        KeyValueBackupJob.schedule(this.mContext);
    }

    private HashSet<String> dataChangedTargets(String packageName) {
        if (this.mContext.checkPermission("android.permission.BACKUP", Binder.getCallingPid(), Binder.getCallingUid()) == -1) {
            HashSet<String> hashSet;
            synchronized (this.mBackupParticipants) {
                hashSet = (HashSet) this.mBackupParticipants.get(Binder.getCallingUid());
            }
            return hashSet;
        }
        HashSet<String> targets = new HashSet();
        if (PACKAGE_MANAGER_SENTINEL.equals(packageName)) {
            targets.add(PACKAGE_MANAGER_SENTINEL);
        } else {
            synchronized (this.mBackupParticipants) {
                int N = this.mBackupParticipants.size();
                for (int i = 0; i < N; i++) {
                    HashSet<String> s = (HashSet) this.mBackupParticipants.valueAt(i);
                    if (s != null) {
                        targets.addAll(s);
                    }
                }
            }
        }
        return targets;
    }

    private void writeToJournalLocked(String str) {
        IOException e;
        Throwable th;
        RandomAccessFile randomAccessFile = null;
        try {
            if (this.mJournal == null) {
                this.mJournal = File.createTempFile("journal", null, this.mJournalDir);
            }
            RandomAccessFile out = new RandomAccessFile(this.mJournal, "rws");
            try {
                out.seek(out.length());
                out.writeUTF(str);
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException e2) {
                    }
                }
                randomAccessFile = out;
            } catch (IOException e3) {
                e = e3;
                randomAccessFile = out;
                try {
                    Slog.e(TAG, "Can't write " + str + " to backup journal", e);
                    this.mJournal = null;
                    if (randomAccessFile != null) {
                        try {
                            randomAccessFile.close();
                        } catch (IOException e4) {
                        }
                    }
                } catch (Throwable th2) {
                    th = th2;
                    if (randomAccessFile != null) {
                        try {
                            randomAccessFile.close();
                        } catch (IOException e5) {
                        }
                    }
                    throw th;
                }
            } catch (Throwable th3) {
                th = th3;
                randomAccessFile = out;
                if (randomAccessFile != null) {
                    randomAccessFile.close();
                }
                throw th;
            }
        } catch (IOException e6) {
            e = e6;
            Slog.e(TAG, "Can't write " + str + " to backup journal", e);
            this.mJournal = null;
            if (randomAccessFile != null) {
                randomAccessFile.close();
            }
        }
    }

    public void dataChanged(final String packageName) {
        if (UserHandle.getCallingUserId() == 0) {
            final HashSet<String> targets = dataChangedTargets(packageName);
            if (targets == null) {
                Slog.w(TAG, "dataChanged but no participant pkg='" + packageName + "'" + " uid=" + Binder.getCallingUid());
            } else {
                this.mBackupHandler.post(new Runnable() {
                    public void run() {
                        BackupManagerService.this.dataChangedImpl(packageName, targets);
                    }
                });
            }
        }
    }

    public void clearBackupData(String transportName, String packageName) {
        if (DEBUG) {
            Slog.v(TAG, "clearBackupData() of " + packageName + " on " + transportName);
        }
        try {
            HashSet<String> apps;
            PackageInfo info = this.mPackageManager.getPackageInfo(packageName, 64);
            if (this.mContext.checkPermission("android.permission.BACKUP", Binder.getCallingPid(), Binder.getCallingUid()) == -1) {
                apps = (HashSet) this.mBackupParticipants.get(Binder.getCallingUid());
            } else {
                apps = new HashSet();
                int N = this.mBackupParticipants.size();
                for (int i = 0; i < N; i++) {
                    HashSet<String> s = (HashSet) this.mBackupParticipants.valueAt(i);
                    if (s != null) {
                        apps.addAll(s);
                    }
                }
            }
            if (apps.contains(packageName)) {
                this.mBackupHandler.removeMessages(12);
                synchronized (this.mQueueLock) {
                    IBackupTransport transport = getTransport(transportName);
                    if (transport == null) {
                        this.mBackupHandler.sendMessageDelayed(this.mBackupHandler.obtainMessage(12, new ClearRetryParams(transportName, packageName)), 3600000);
                        return;
                    }
                    long oldId = Binder.clearCallingIdentity();
                    this.mWakelock.acquire();
                    this.mBackupHandler.sendMessage(this.mBackupHandler.obtainMessage(4, new ClearParams(transport, info)));
                    Binder.restoreCallingIdentity(oldId);
                }
            }
        } catch (NameNotFoundException e) {
            Slog.d(TAG, "No such package '" + packageName + "' - not clearing backup data");
        }
    }

    public void backupNow() {
        this.mContext.enforceCallingOrSelfPermission("android.permission.BACKUP", "backupNow");
        if (this.mPowerManager.isPowerSaveMode()) {
            if (DEBUG) {
                Slog.v(TAG, "Not running backup while in battery save mode");
            }
            KeyValueBackupJob.schedule(this.mContext);
            return;
        }
        if (DEBUG) {
            Slog.v(TAG, "Scheduling immediate backup pass");
        }
        synchronized (this.mQueueLock) {
            try {
                this.mRunBackupIntent.send();
            } catch (CanceledException e) {
                Slog.e(TAG, "run-backup intent cancelled!");
            }
            KeyValueBackupJob.cancel(this.mContext);
        }
    }

    boolean deviceIsProvisioned() {
        if (Global.getInt(this.mContext.getContentResolver(), "device_provisioned", 0) != 0) {
            return true;
        }
        return false;
    }

    public void fullBackup(ParcelFileDescriptor fd, boolean includeApks, boolean includeObbs, boolean includeShared, boolean doWidgets, boolean doAllApps, boolean includeSystem, boolean compress, String[] pkgList) {
        this.mContext.enforceCallingPermission("android.permission.BACKUP", "fullBackup");
        if (UserHandle.getCallingUserId() != 0) {
            throw new IllegalStateException("Backup supported only for the device owner");
        } else if (doAllApps || includeShared || !(pkgList == null || pkgList.length == 0)) {
            long oldId = Binder.clearCallingIdentity();
            try {
                if (deviceIsProvisioned()) {
                    if (DEBUG) {
                        Slog.v(TAG, "Requesting full backup: apks=" + includeApks + " obb=" + includeObbs + " shared=" + includeShared + " all=" + doAllApps + " system=" + includeSystem + " pkgs=" + pkgList);
                    }
                    Slog.i(TAG, "Beginning full backup...");
                    FullBackupParams params = new FullBackupParams(fd, includeApks, includeObbs, includeShared, doWidgets, doAllApps, includeSystem, compress, pkgList);
                    int token = generateToken();
                    synchronized (this.mFullConfirmations) {
                        this.mFullConfirmations.put(token, params);
                    }
                    if (DEBUG) {
                        Slog.d(TAG, "Starting backup confirmation UI, token=" + token);
                    }
                    if (startConfirmationUi(token, "fullback")) {
                        this.mPowerManager.userActivity(SystemClock.uptimeMillis(), 0, 0);
                        startConfirmationTimeout(token, params);
                        if (DEBUG) {
                            Slog.d(TAG, "Waiting for full backup completion...");
                        }
                        waitForCompletion(params);
                        try {
                            fd.close();
                        } catch (IOException e) {
                        }
                        Binder.restoreCallingIdentity(oldId);
                        Slog.d(TAG, "Full backup processing complete.");
                        return;
                    }
                    Slog.e(TAG, "Unable to launch full backup confirmation");
                    this.mFullConfirmations.delete(token);
                    try {
                        fd.close();
                    } catch (IOException e2) {
                    }
                    Binder.restoreCallingIdentity(oldId);
                    Slog.d(TAG, "Full backup processing complete.");
                    return;
                }
                Slog.i(TAG, "Full backup not supported before setup");
                try {
                    fd.close();
                } catch (IOException e3) {
                }
                Binder.restoreCallingIdentity(oldId);
                Slog.d(TAG, "Full backup processing complete.");
            } catch (Throwable th) {
                try {
                    fd.close();
                } catch (IOException e4) {
                }
                Binder.restoreCallingIdentity(oldId);
                Slog.d(TAG, "Full backup processing complete.");
            }
        } else {
            throw new IllegalArgumentException("Backup requested but neither shared nor any apps named");
        }
    }

    public void fullTransportBackup(String[] pkgNames) {
        this.mContext.enforceCallingPermission("android.permission.BACKUP", "fullTransportBackup");
        if (UserHandle.getCallingUserId() != 0) {
            throw new IllegalStateException("Restore supported only for the device owner");
        }
        if (fullBackupAllowable(getTransport(this.mCurrentTransport))) {
            if (DEBUG) {
                Slog.d(TAG, "fullTransportBackup()");
            }
            long oldId = Binder.clearCallingIdentity();
            try {
                CountDownLatch latch = new CountDownLatch(1);
                PerformFullTransportBackupTask task = new PerformFullTransportBackupTask(null, pkgNames, false, null, latch, null, false);
                this.mWakelock.acquire();
                new Thread(task, "full-transport-master").start();
                while (true) {
                    try {
                        latch.await();
                        break;
                    } catch (InterruptedException e) {
                    }
                }
                long now = System.currentTimeMillis();
                for (String pkg : pkgNames) {
                    enqueueFullBackup(pkg, now);
                }
            } finally {
                Binder.restoreCallingIdentity(oldId);
            }
        } else {
            Slog.i(TAG, "Full backup not currently possible -- key/value backup not yet run?");
        }
        if (DEBUG) {
            Slog.d(TAG, "Done with full transport backup.");
        }
    }

    public void fullRestore(ParcelFileDescriptor fd) {
        this.mContext.enforceCallingPermission("android.permission.BACKUP", "fullRestore");
        if (UserHandle.getCallingUserId() != 0) {
            throw new IllegalStateException("Restore supported only for the device owner");
        }
        long oldId = Binder.clearCallingIdentity();
        try {
            if (deviceIsProvisioned()) {
                Slog.i(TAG, "Beginning full restore...");
                FullRestoreParams params = new FullRestoreParams(fd);
                int token = generateToken();
                synchronized (this.mFullConfirmations) {
                    this.mFullConfirmations.put(token, params);
                }
                if (DEBUG) {
                    Slog.d(TAG, "Starting restore confirmation UI, token=" + token);
                }
                if (startConfirmationUi(token, "fullrest")) {
                    this.mPowerManager.userActivity(SystemClock.uptimeMillis(), 0, 0);
                    startConfirmationTimeout(token, params);
                    if (DEBUG) {
                        Slog.d(TAG, "Waiting for full restore completion...");
                    }
                    waitForCompletion(params);
                    try {
                        fd.close();
                    } catch (IOException e) {
                        Slog.w(TAG, "Error trying to close fd after full restore: " + e);
                    }
                    Binder.restoreCallingIdentity(oldId);
                    Slog.i(TAG, "Full restore processing complete.");
                    return;
                }
                Slog.e(TAG, "Unable to launch full restore confirmation");
                this.mFullConfirmations.delete(token);
                try {
                    fd.close();
                } catch (IOException e2) {
                    Slog.w(TAG, "Error trying to close fd after full restore: " + e2);
                }
                Binder.restoreCallingIdentity(oldId);
                Slog.i(TAG, "Full restore processing complete.");
                return;
            }
            Slog.i(TAG, "Full restore not permitted before setup");
            try {
                fd.close();
            } catch (IOException e22) {
                Slog.w(TAG, "Error trying to close fd after full restore: " + e22);
            }
            Binder.restoreCallingIdentity(oldId);
            Slog.i(TAG, "Full restore processing complete.");
        } catch (Throwable th) {
            try {
                fd.close();
            } catch (IOException e222) {
                Slog.w(TAG, "Error trying to close fd after full restore: " + e222);
            }
            Binder.restoreCallingIdentity(oldId);
            Slog.i(TAG, "Full restore processing complete.");
        }
    }

    boolean startConfirmationUi(int token, String action) {
        try {
            Intent confIntent = new Intent(action);
            confIntent.setClassName("com.android.backupconfirm", "com.android.backupconfirm.BackupRestoreConfirmation");
            confIntent.putExtra("conftoken", token);
            confIntent.addFlags(268435456);
            this.mContext.startActivityAsUser(confIntent, UserHandle.SYSTEM);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    void startConfirmationTimeout(int token, FullParams params) {
        this.mBackupHandler.sendMessageDelayed(this.mBackupHandler.obtainMessage(9, token, 0, params), 60000);
    }

    void waitForCompletion(FullParams params) {
        synchronized (params.latch) {
            while (!params.latch.get()) {
                try {
                    params.latch.wait();
                } catch (InterruptedException e) {
                }
            }
        }
    }

    void signalFullBackupRestoreCompletion(FullParams params) {
        synchronized (params.latch) {
            params.latch.set(true);
            params.latch.notifyAll();
        }
    }

    public void acknowledgeFullBackupOrRestore(int token, boolean allow, String curPassword, String encPpassword, IFullBackupRestoreObserver observer) {
        if (DEBUG) {
            Slog.d(TAG, "acknowledgeFullBackupOrRestore : token=" + token + " allow=" + allow);
        }
        this.mContext.enforceCallingPermission("android.permission.BACKUP", "acknowledgeFullBackupOrRestore");
        long oldId = Binder.clearCallingIdentity();
        try {
            synchronized (this.mFullConfirmations) {
                FullParams params = (FullParams) this.mFullConfirmations.get(token);
                if (params != null) {
                    this.mBackupHandler.removeMessages(9, params);
                    this.mFullConfirmations.delete(token);
                    if (allow) {
                        int verb;
                        if (params instanceof FullBackupParams) {
                            verb = 2;
                        } else {
                            verb = 10;
                        }
                        params.observer = observer;
                        params.curPassword = curPassword;
                        params.encryptPassword = encPpassword;
                        this.mWakelock.acquire();
                        this.mBackupHandler.sendMessage(this.mBackupHandler.obtainMessage(verb, params));
                    } else {
                        Slog.w(TAG, "User rejected full backup/restore operation");
                        signalFullBackupRestoreCompletion(params);
                    }
                } else {
                    Slog.w(TAG, "Attempted to ack full backup/restore with invalid token");
                }
            }
            Binder.restoreCallingIdentity(oldId);
        } catch (Throwable th) {
            Binder.restoreCallingIdentity(oldId);
        }
    }

    private static boolean backupSettingMigrated(int userId) {
        return new File(new File(Environment.getDataDirectory(), "backup"), BACKUP_ENABLE_FILE).exists();
    }

    private static boolean readBackupEnableState(int userId) {
        Throwable th;
        Throwable th2 = null;
        File enableFile = new File(new File(Environment.getDataDirectory(), "backup"), BACKUP_ENABLE_FILE);
        if (enableFile.exists()) {
            FileInputStream fileInputStream = null;
            try {
                FileInputStream fin = new FileInputStream(enableFile);
                try {
                    boolean z;
                    if (fin.read() != 0) {
                        z = true;
                    } else {
                        z = false;
                    }
                    if (fin != null) {
                        try {
                            fin.close();
                        } catch (Throwable th3) {
                            th2 = th3;
                        }
                    }
                    if (th2 == null) {
                        return z;
                    }
                    try {
                        throw th2;
                    } catch (IOException e) {
                        fileInputStream = fin;
                    }
                } catch (Throwable th4) {
                    th = th4;
                    fileInputStream = fin;
                    if (fileInputStream != null) {
                        try {
                            fileInputStream.close();
                        } catch (Throwable th5) {
                            if (th2 == null) {
                                th2 = th5;
                            } else if (th2 != th5) {
                                th2.addSuppressed(th5);
                            }
                        }
                    }
                    if (th2 == null) {
                        try {
                            throw th2;
                        } catch (IOException e2) {
                            Slog.e(TAG, "Cannot read enable state; assuming disabled");
                            return false;
                        }
                    }
                    throw th;
                }
            } catch (Throwable th6) {
                th = th6;
                if (fileInputStream != null) {
                    fileInputStream.close();
                }
                if (th2 == null) {
                    throw th;
                }
                throw th2;
            }
        }
        if (DEBUG) {
            Slog.i(TAG, "isBackupEnabled() => false due to absent settings file");
        }
        return false;
    }

    private static void writeBackupEnableState(boolean enable, int userId) {
        Exception e;
        Object obj;
        Throwable th;
        File base = new File(Environment.getDataDirectory(), "backup");
        File enableFile = new File(base, BACKUP_ENABLE_FILE);
        File stage = new File(base, "backup_enabled-stage");
        AutoCloseable autoCloseable = null;
        try {
            FileOutputStream fout = new FileOutputStream(stage);
            try {
                fout.write(enable ? 1 : 0);
                fout.close();
                stage.renameTo(enableFile);
                IoUtils.closeQuietly(fout);
                FileOutputStream fileOutputStream = fout;
            } catch (IOException e2) {
                e = e2;
                obj = fout;
                try {
                    Slog.e(TAG, "Unable to record backup enable state; reverting to disabled: " + e.getMessage());
                    Secure.putStringForUser(sInstance.mContext.getContentResolver(), BACKUP_ENABLE_FILE, null, userId);
                    enableFile.delete();
                    stage.delete();
                    IoUtils.closeQuietly(autoCloseable);
                } catch (Throwable th2) {
                    th = th2;
                    IoUtils.closeQuietly(autoCloseable);
                    throw th;
                }
            } catch (Throwable th3) {
                th = th3;
                obj = fout;
                IoUtils.closeQuietly(autoCloseable);
                throw th;
            }
        } catch (IOException e3) {
            e = e3;
            Slog.e(TAG, "Unable to record backup enable state; reverting to disabled: " + e.getMessage());
            Secure.putStringForUser(sInstance.mContext.getContentResolver(), BACKUP_ENABLE_FILE, null, userId);
            enableFile.delete();
            stage.delete();
            IoUtils.closeQuietly(autoCloseable);
        }
    }

    public void setBackupEnabled(boolean enable) {
        this.mContext.enforceCallingOrSelfPermission("android.permission.BACKUP", "setBackupEnabled");
        Slog.i(TAG, "Backup enabled => " + enable);
        long oldId = Binder.clearCallingIdentity();
        try {
            boolean wasEnabled = this.mEnabled;
            synchronized (this) {
                writeBackupEnableState(enable, 0);
                this.mEnabled = enable;
            }
            synchronized (this.mQueueLock) {
                if (enable && !wasEnabled) {
                    if (this.mProvisioned) {
                        KeyValueBackupJob.schedule(this.mContext);
                        scheduleNextFullBackupJob(0);
                    }
                }
                if (!enable) {
                    KeyValueBackupJob.cancel(this.mContext);
                    if (wasEnabled && this.mProvisioned) {
                        synchronized (this.mTransports) {
                            HashSet<String> allTransports = new HashSet(this.mTransports.keySet());
                        }
                        for (String transport : allTransports) {
                            recordInitPendingLocked(true, transport);
                        }
                        this.mAlarmManager.set(0, System.currentTimeMillis(), this.mRunInitIntent);
                    }
                }
            }
            Binder.restoreCallingIdentity(oldId);
        } catch (Throwable th) {
            Binder.restoreCallingIdentity(oldId);
        }
    }

    public void setAutoRestore(boolean doAutoRestore) {
        this.mContext.enforceCallingOrSelfPermission("android.permission.BACKUP", "setAutoRestore");
        Slog.i(TAG, "Auto restore => " + doAutoRestore);
        long oldId = Binder.clearCallingIdentity();
        try {
            synchronized (this) {
                Secure.putInt(this.mContext.getContentResolver(), "backup_auto_restore", doAutoRestore ? 1 : 0);
                this.mAutoRestore = doAutoRestore;
            }
            Binder.restoreCallingIdentity(oldId);
        } catch (Throwable th) {
            Binder.restoreCallingIdentity(oldId);
        }
    }

    public void setBackupProvisioned(boolean available) {
        this.mContext.enforceCallingOrSelfPermission("android.permission.BACKUP", "setBackupProvisioned");
    }

    public boolean isBackupEnabled() {
        this.mContext.enforceCallingOrSelfPermission("android.permission.BACKUP", "isBackupEnabled");
        return this.mEnabled;
    }

    public String getCurrentTransport() {
        this.mContext.enforceCallingOrSelfPermission("android.permission.BACKUP", "getCurrentTransport");
        return this.mCurrentTransport;
    }

    public String[] listAllTransports() {
        this.mContext.enforceCallingOrSelfPermission("android.permission.BACKUP", "listAllTransports");
        ArrayList<String> known = new ArrayList();
        for (Entry<String, IBackupTransport> entry : this.mTransports.entrySet()) {
            if (entry.getValue() != null) {
                known.add((String) entry.getKey());
            }
        }
        if (known.size() <= 0) {
            return null;
        }
        String[] list = new String[known.size()];
        known.toArray(list);
        return list;
    }

    public String[] getTransportWhitelist() {
        String[] whitelist = new String[this.mTransportWhitelist.size()];
        for (int i = this.mTransportWhitelist.size() - 1; i >= 0; i--) {
            whitelist[i] = ((ComponentName) this.mTransportWhitelist.valueAt(i)).flattenToShortString();
        }
        return whitelist;
    }

    public String selectBackupTransport(String transport) {
        String prevTransport;
        this.mContext.enforceCallingOrSelfPermission("android.permission.BACKUP", "selectBackupTransport");
        synchronized (this.mTransports) {
            long oldId = Binder.clearCallingIdentity();
            try {
                prevTransport = this.mCurrentTransport;
                this.mCurrentTransport = transport;
                Secure.putString(this.mContext.getContentResolver(), "backup_transport", transport);
                Slog.v(TAG, "selectBackupTransport() set " + this.mCurrentTransport + " returning " + prevTransport);
            } finally {
                Binder.restoreCallingIdentity(oldId);
            }
        }
        return prevTransport;
    }

    public Intent getConfigurationIntent(String transportName) {
        this.mContext.enforceCallingOrSelfPermission("android.permission.BACKUP", "getConfigurationIntent");
        synchronized (this.mTransports) {
            IBackupTransport transport = (IBackupTransport) this.mTransports.get(transportName);
            if (transport != null) {
                try {
                    Intent intent = transport.configurationIntent();
                    return intent;
                } catch (RemoteException e) {
                    return null;
                }
            }
        }
    }

    public String getDestinationString(String transportName) {
        this.mContext.enforceCallingOrSelfPermission("android.permission.BACKUP", "getDestinationString");
        synchronized (this.mTransports) {
            IBackupTransport transport = (IBackupTransport) this.mTransports.get(transportName);
            if (transport != null) {
                try {
                    String text = transport.currentDestinationString();
                    return text;
                } catch (RemoteException e) {
                    return null;
                }
            }
        }
    }

    public Intent getDataManagementIntent(String transportName) {
        this.mContext.enforceCallingOrSelfPermission("android.permission.BACKUP", "getDataManagementIntent");
        synchronized (this.mTransports) {
            IBackupTransport transport = (IBackupTransport) this.mTransports.get(transportName);
            if (transport != null) {
                try {
                    Intent intent = transport.dataManagementIntent();
                    return intent;
                } catch (RemoteException e) {
                    return null;
                }
            }
        }
    }

    public String getDataManagementLabel(String transportName) {
        this.mContext.enforceCallingOrSelfPermission("android.permission.BACKUP", "getDataManagementLabel");
        synchronized (this.mTransports) {
            IBackupTransport transport = (IBackupTransport) this.mTransports.get(transportName);
            if (transport != null) {
                try {
                    String text = transport.dataManagementLabel();
                    return text;
                } catch (RemoteException e) {
                    return null;
                }
            }
        }
    }

    public void agentConnected(String packageName, IBinder agentBinder) {
        synchronized (this.mAgentConnectLock) {
            if (Binder.getCallingUid() == 1000) {
                Slog.d(TAG, "agentConnected pkg=" + packageName + " agent=" + agentBinder);
                this.mConnectedAgent = IBackupAgent.Stub.asInterface(agentBinder);
                this.mConnecting = false;
            } else {
                Slog.w(TAG, "Non-system process uid=" + Binder.getCallingUid() + " claiming agent connected");
            }
            this.mAgentConnectLock.notifyAll();
        }
    }

    public void agentDisconnected(String packageName) {
        synchronized (this.mAgentConnectLock) {
            if (Binder.getCallingUid() == 1000) {
                this.mConnectedAgent = null;
                this.mConnecting = false;
            } else {
                Slog.w(TAG, "Non-system process uid=" + Binder.getCallingUid() + " claiming agent disconnected");
            }
            this.mAgentConnectLock.notifyAll();
        }
    }

    public void restoreAtInstall(String packageName, int token) {
        if (Binder.getCallingUid() != 1000) {
            Slog.w(TAG, "Non-system process uid=" + Binder.getCallingUid() + " attemping install-time restore");
            return;
        }
        boolean skip = false;
        long restoreSet = getAvailableRestoreToken(packageName);
        if (DEBUG) {
            Slog.v(TAG, "restoreAtInstall pkg=" + packageName + " token=" + Integer.toHexString(token) + " restoreSet=" + Long.toHexString(restoreSet));
        }
        if (restoreSet == 0) {
            skip = true;
        }
        IBackupTransport transport = getTransport(this.mCurrentTransport);
        if (transport == null) {
            if (DEBUG) {
                Slog.w(TAG, "No transport");
            }
            skip = true;
        }
        if (!this.mAutoRestore) {
            if (DEBUG) {
                Slog.w(TAG, "Non-restorable state: auto=" + this.mAutoRestore);
            }
            skip = true;
        }
        if (!skip) {
            try {
                String dirName = transport.transportDirName();
                this.mWakelock.acquire();
                Message msg = this.mBackupHandler.obtainMessage(3);
                msg.obj = new RestoreParams(transport, dirName, null, restoreSet, packageName, token);
                this.mBackupHandler.sendMessage(msg);
            } catch (RemoteException e) {
                Slog.e(TAG, "Unable to contact transport");
                skip = true;
            }
        }
        if (skip) {
            if (DEBUG) {
                Slog.v(TAG, "Finishing install immediately");
            }
            try {
                this.mPackageManagerBinder.finishPackageInstall(token, false);
            } catch (RemoteException e2) {
            }
        }
    }

    public IRestoreSession beginRestoreSession(String packageName, String transport) {
        if (DEBUG) {
            Slog.v(TAG, "beginRestoreSession: pkg=" + packageName + " transport=" + transport);
        }
        boolean needPermission = true;
        if (transport == null) {
            transport = this.mCurrentTransport;
            if (packageName != null) {
                try {
                    if (this.mPackageManager.getPackageInfo(packageName, 0).applicationInfo.uid == Binder.getCallingUid()) {
                        needPermission = false;
                    }
                } catch (NameNotFoundException e) {
                    Slog.w(TAG, "Asked to restore nonexistent pkg " + packageName);
                    throw new IllegalArgumentException("Package " + packageName + " not found");
                }
            }
        }
        if (needPermission) {
            this.mContext.enforceCallingOrSelfPermission("android.permission.BACKUP", "beginRestoreSession");
        } else if (DEBUG) {
            Slog.d(TAG, "restoring self on current transport; no permission needed");
        }
        synchronized (this) {
            if (this.mActiveRestoreSession != null) {
                Slog.i(TAG, "Restore session requested but one already active");
                return null;
            } else if (this.mBackupRunning) {
                Slog.i(TAG, "Restore session requested but currently running backups");
                return null;
            } else {
                this.mActiveRestoreSession = new ActiveRestoreSession(packageName, transport);
                this.mBackupHandler.sendEmptyMessageDelayed(8, 60000);
                return this.mActiveRestoreSession;
            }
        }
    }

    void clearRestoreSession(ActiveRestoreSession currentSession) {
        synchronized (this) {
            if (currentSession != this.mActiveRestoreSession) {
                Slog.e(TAG, "ending non-current restore session");
            } else {
                if (DEBUG) {
                    Slog.v(TAG, "Clearing restore session and halting timeout");
                }
                this.mActiveRestoreSession = null;
                this.mBackupHandler.removeMessages(8);
            }
        }
    }

    public void opComplete(int token, long result) {
        synchronized (this.mCurrentOpLock) {
            Operation op = (Operation) this.mCurrentOperations.get(token);
            if (op != null) {
                if (op.state == -1) {
                    op = null;
                    this.mCurrentOperations.delete(token);
                } else {
                    op.state = 1;
                }
            }
            this.mCurrentOpLock.notifyAll();
        }
        if (op != null && op.callback != null) {
            this.mBackupHandler.sendMessage(this.mBackupHandler.obtainMessage(21, Pair.create(op.callback, Long.valueOf(result))));
        }
    }

    public boolean isAppEligibleForBackup(String packageName) {
        this.mContext.enforceCallingOrSelfPermission("android.permission.BACKUP", "isAppEligibleForBackup");
        try {
            PackageInfo packageInfo = this.mPackageManager.getPackageInfo(packageName, 64);
            if (!appIsEligibleForBackup(packageInfo.applicationInfo) || appIsStopped(packageInfo.applicationInfo)) {
                return false;
            }
            IBackupTransport transport = getTransport(this.mCurrentTransport);
            if (transport != null) {
                try {
                    return transport.isAppEligibleForBackup(packageInfo, appGetsFullBackup(packageInfo));
                } catch (RemoteException e) {
                    Slog.e(TAG, "Unable to contact transport");
                }
            }
            return true;
        } catch (NameNotFoundException e2) {
            return false;
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        this.mContext.enforceCallingOrSelfPermission("android.permission.DUMP", TAG);
        long identityToken = Binder.clearCallingIdentity();
        if (args != null) {
            int i = 0;
            int length = args.length;
            while (i < length) {
                String arg = args[i];
                if ("-h".equals(arg)) {
                    pw.println("'dumpsys backup' optional arguments:");
                    pw.println("  -h       : this help text");
                    pw.println("  a[gents] : dump information about defined backup agents");
                    Binder.restoreCallingIdentity(identityToken);
                    return;
                }
                try {
                    if ("agents".startsWith(arg)) {
                        dumpAgents(pw);
                        return;
                    }
                    i++;
                } finally {
                    Binder.restoreCallingIdentity(identityToken);
                }
            }
        }
        dumpInternal(pw);
        Binder.restoreCallingIdentity(identityToken);
    }

    private void dumpAgents(PrintWriter pw) {
        List<PackageInfo> agentPackages = allAgentPackages();
        pw.println("Defined backup agents:");
        for (PackageInfo pkg : agentPackages) {
            pw.print("  ");
            pw.print(pkg.packageName);
            pw.println(':');
            pw.print("      ");
            pw.println(pkg.applicationInfo.backupAgentName);
        }
    }

    private void dumpInternal(PrintWriter pw) {
        synchronized (this.mQueueLock) {
            pw.println("Backup Manager is " + (this.mEnabled ? "enabled" : "disabled") + " / " + (!this.mProvisioned ? "not " : "") + "provisioned / " + (this.mPendingInits.size() == 0 ? "not " : "") + "pending init");
            pw.println("Auto-restore is " + (this.mAutoRestore ? "enabled" : "disabled"));
            if (this.mBackupRunning) {
                pw.println("Backup currently running");
            }
            pw.println("Last backup pass started: " + this.mLastBackupPass + " (now = " + System.currentTimeMillis() + ')');
            pw.println("  next scheduled: " + KeyValueBackupJob.nextScheduled());
            pw.println("Transport whitelist:");
            for (ComponentName transport : this.mTransportWhitelist) {
                pw.print("    ");
                pw.println(transport.flattenToShortString());
            }
            pw.println("Available transports:");
            if (listAllTransports() != null) {
                for (String t : listAllTransports()) {
                    pw.println((t.equals(this.mCurrentTransport) ? "  * " : "    ") + t);
                    try {
                        IBackupTransport transport2 = getTransport(t);
                        File dir = new File(this.mBaseStateDir, transport2.transportDirName());
                        pw.println("       destination: " + transport2.currentDestinationString());
                        pw.println("       intent: " + transport2.configurationIntent());
                        for (File f : dir.listFiles()) {
                            pw.println("       " + f.getName() + " - " + f.length() + " state bytes");
                        }
                    } catch (Exception e) {
                        Slog.e(TAG, "Error in transport", e);
                        pw.println("        Error: " + e);
                    }
                }
            }
            pw.println("Pending init: " + this.mPendingInits.size());
            for (String s : this.mPendingInits) {
                pw.println("    " + s);
            }
            synchronized (this.mBackupTrace) {
                if (!this.mBackupTrace.isEmpty()) {
                    pw.println("Most recent backup trace:");
                    for (String s2 : this.mBackupTrace) {
                        pw.println("   " + s2);
                    }
                }
            }
            pw.print("Ancestral: ");
            pw.println(Long.toHexString(this.mAncestralToken));
            pw.print("Current:   ");
            pw.println(Long.toHexString(this.mCurrentToken));
            int N = this.mBackupParticipants.size();
            pw.println("Participants:");
            for (int i = 0; i < N; i++) {
                int uid = this.mBackupParticipants.keyAt(i);
                pw.print("  uid: ");
                pw.println(uid);
                for (String app : (HashSet) this.mBackupParticipants.valueAt(i)) {
                    pw.println("    " + app);
                }
            }
            pw.println("Ancestral packages: " + (this.mAncestralPackages == null ? "none" : Integer.valueOf(this.mAncestralPackages.size())));
            if (this.mAncestralPackages != null) {
                for (String pkg : this.mAncestralPackages) {
                    pw.println("    " + pkg);
                }
            }
            pw.println("Ever backed up: " + this.mEverStoredApps.size());
            for (String pkg2 : this.mEverStoredApps) {
                pw.println("    " + pkg2);
            }
            pw.println("Pending key/value backup: " + this.mPendingBackups.size());
            for (BackupRequest req : this.mPendingBackups.values()) {
                pw.println("    " + req);
            }
            pw.println("Full backup queue:" + this.mFullBackupQueue.size());
            for (FullBackupEntry entry : this.mFullBackupQueue) {
                pw.print("    ");
                pw.print(entry.lastBackup);
                pw.print(" : ");
                pw.println(entry.packageName);
            }
        }
    }

    private static void sendBackupOnUpdate(IBackupObserver observer, String packageName, BackupProgress progress) {
        if (observer != null) {
            try {
                observer.onUpdate(packageName, progress);
            } catch (RemoteException e) {
                if (DEBUG) {
                    Slog.w(TAG, "Backup observer went away: onUpdate");
                }
            }
        }
    }

    private static void sendBackupOnPackageResult(IBackupObserver observer, String packageName, int status) {
        if (observer != null) {
            try {
                observer.onResult(packageName, status);
            } catch (RemoteException e) {
                if (DEBUG) {
                    Slog.w(TAG, "Backup observer went away: onResult");
                }
            }
        }
    }

    private static void sendBackupFinished(IBackupObserver observer, int status) {
        if (observer != null) {
            try {
                observer.backupFinished(status);
            } catch (RemoteException e) {
                if (DEBUG) {
                    Slog.w(TAG, "Backup observer went away: backupFinished");
                }
            }
        }
    }
}
