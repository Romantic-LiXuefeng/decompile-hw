package com.android.server;

import android.app.BroadcastOptions;
import android.app.Notification;
import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.app.PendingIntent.OnFinished;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.INetworkManagementEventObserver;
import android.net.INetworkPolicyListener;
import android.net.INetworkPolicyListener.Stub;
import android.net.INetworkPolicyManager;
import android.net.INetworkStatsService;
import android.net.LinkProperties;
import android.net.LinkProperties.CompareResult;
import android.net.Network;
import android.net.NetworkAgent;
import android.net.NetworkCapabilities;
import android.net.NetworkConfig;
import android.net.NetworkFactory;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.NetworkInfo.State;
import android.net.NetworkMisc;
import android.net.NetworkPolicyManager;
import android.net.NetworkQuotaInfo;
import android.net.NetworkRequest;
import android.net.NetworkState;
import android.net.NetworkUtils;
import android.net.ProxyInfo;
import android.net.RouteInfo;
import android.net.UidRange;
import android.net.Uri;
import android.net.metrics.DefaultNetworkEvent;
import android.net.metrics.NetworkEvent;
import android.net.wifi.HiSiWifiComm;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.FileUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.INetworkManagementService;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings.Global;
import android.provider.Settings.Secure;
import android.security.KeyStore;
import android.telephony.HwTelephonyManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.LocalLog;
import android.util.LocalLog.ReadOnlyLocalLog;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import android.util.Xml;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.app.IBatteryStats;
import com.android.internal.net.LegacyVpnInfo;
import com.android.internal.net.NetworkStatsFactory;
import com.android.internal.net.VpnConfig;
import com.android.internal.net.VpnInfo;
import com.android.internal.net.VpnProfile;
import com.android.internal.util.AsyncChannel;
import com.android.internal.util.IndentingPrintWriter;
import com.android.internal.util.MessageUtils;
import com.android.internal.util.XmlUtils;
import com.android.server.am.BatteryStatsService;
import com.android.server.audio.AudioService;
import com.android.server.connectivity.DataConnectionStats;
import com.android.server.connectivity.KeepaliveTracker;
import com.android.server.connectivity.Nat464Xlat;
import com.android.server.connectivity.NetworkAgentInfo;
import com.android.server.connectivity.NetworkDiagnostics;
import com.android.server.connectivity.NetworkMonitor;
import com.android.server.connectivity.PacManager;
import com.android.server.connectivity.PermissionMonitor;
import com.android.server.connectivity.Tethering;
import com.android.server.connectivity.Vpn;
import com.android.server.net.BaseNetworkObserver;
import com.android.server.net.LockdownVpnTracker;
import com.android.server.policy.PhoneWindowManager;
import com.google.android.collect.Lists;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class ConnectivityService extends AbstractConnectivityService implements OnFinished {
    private static final String ACTION_PKT_CNT_SAMPLE_INTERVAL_ELAPSED = "android.net.ConnectivityService.action.PKT_CNT_SAMPLE_INTERVAL_ELAPSED";
    private static final String ATTR_MCC = "mcc";
    private static final String ATTR_MNC = "mnc";
    private static final int CODE_REMOVE_LEGACYROUTE_TO_HOST = 1015;
    private static final boolean DBG = true;
    private static final String DEFAULT_TCP_BUFFER_SIZES = "4096,87380,110208,4096,16384,110208";
    private static final String DEFAULT_TCP_RWND_KEY = "net.tcp.default_init_rwnd";
    private static final String DESCRIPTOR = "android.net.wifi.INetworkManager";
    private static final int DISABLED = 0;
    private static final int ENABLED = 1;
    private static final int EVENT_APPLY_GLOBAL_HTTP_PROXY = 9;
    private static final int EVENT_CHANGE_MOBILE_DATA_ENABLED = 2;
    private static final int EVENT_CLEAR_NET_TRANSITION_WAKELOCK = 8;
    private static final int EVENT_CONFIGURE_MOBILE_DATA_ALWAYS_ON = 30;
    private static final int EVENT_EXPIRE_NET_TRANSITION_WAKELOCK = 24;
    private static final int EVENT_PROMPT_UNVALIDATED = 29;
    private static final int EVENT_PROXY_HAS_CHANGED = 16;
    private static final int EVENT_REGISTER_NETWORK_AGENT = 18;
    private static final int EVENT_REGISTER_NETWORK_FACTORY = 17;
    private static final int EVENT_REGISTER_NETWORK_LISTENER = 21;
    private static final int EVENT_REGISTER_NETWORK_LISTENER_WITH_INTENT = 31;
    private static final int EVENT_REGISTER_NETWORK_REQUEST = 19;
    private static final int EVENT_REGISTER_NETWORK_REQUEST_WITH_INTENT = 26;
    private static final int EVENT_RELEASE_NETWORK_REQUEST = 22;
    private static final int EVENT_RELEASE_NETWORK_REQUEST_WITH_INTENT = 27;
    private static final int EVENT_SET_ACCEPT_UNVALIDATED = 28;
    private static final int EVENT_SYSTEM_READY = 25;
    private static final int EVENT_TIMEOUT_NETWORK_REQUEST = 20;
    private static final int EVENT_UNREGISTER_NETWORK_FACTORY = 23;
    private static final int INET_CONDITION_LOG_MAX_SIZE = 15;
    private static final boolean LOGD_BLOCKED_NETWORKINFO = true;
    private static final boolean LOGD_RULES = false;
    private static final int MAX_NETWORK_REQUESTS_PER_UID = 100;
    private static final int MAX_NETWORK_REQUEST_LOGS = 20;
    private static final int MAX_NET_ID = 65535;
    private static final int MAX_VALIDATION_LOGS = 10;
    private static final int MIN_NET_ID = 100;
    private static final String NETWORK_RESTORE_DELAY_PROP_NAME = "android.telephony.apn-restore";
    private static final String NOTIFICATION_ID = "CaptivePortal.Notification";
    private static final int PROMPT_UNVALIDATED_DELAY_MS = 8000;
    private static final String PROVISIONING_URL_PATH = "/data/misc/radio/provisioning_urls.xml";
    private static final int RESTORE_DEFAULT_NETWORK_DELAY = 60000;
    private static final boolean SAMPLE_DBG = false;
    private static final String TAG = "ConnectivityService";
    private static final String TAG_PROVISIONING_URL = "provisioningUrl";
    private static final String TAG_PROVISIONING_URLS = "provisioningUrls";
    private static final boolean VDBG = true;
    private static final SparseArray<String> sMagicDecoderRing = MessageUtils.findMessageNames(new Class[]{AsyncChannel.class, ConnectivityService.class, NetworkAgent.class});
    private static ConnectivityService sServiceInstance;
    private RouteInfo mBestLegacyRoute;
    @GuardedBy("mBlockedAppUids")
    private final HashSet<Integer> mBlockedAppUids = new HashSet();
    private final Context mContext;
    private String mCurrentTcpBufferSizes;
    private INetworkManagementEventObserver mDataActivityObserver = new BaseNetworkObserver() {
        public void interfaceClassDataActivityChanged(String label, boolean active, long tsNanos) {
            ConnectivityService.this.sendDataActivityBroadcast(Integer.parseInt(label), active, tsNanos);
        }
    };
    private DataConnectionStats mDataConnectionStats;
    private int mDefaultInetConditionPublished = 0;
    private final NetworkRequest mDefaultMobileDataRequest;
    private volatile ProxyInfo mDefaultProxy = null;
    private boolean mDefaultProxyDisabled = false;
    private final NetworkRequest mDefaultRequest;
    private ProxyInfo mGlobalProxy = null;
    private final InternalHandler mHandler;
    protected final HandlerThread mHandlerThread;
    private ArrayList mInetLog;
    private Intent mInitialBroadcast;
    private KeepaliveTracker mKeepaliveTracker;
    private KeyStore mKeyStore;
    private int mLegacyRouteNetId;
    private int mLegacyRouteUid;
    private LegacyTypeTracker mLegacyTypeTracker = new LegacyTypeTracker();
    private boolean mLockdownEnabled;
    private LockdownVpnTracker mLockdownTracker;
    @GuardedBy("mRulesLock")
    private ArraySet<String> mMeteredIfaces = new ArraySet();
    NetworkConfig[] mNetConfigs;
    @GuardedBy("mNetworkForNetId")
    private final SparseBooleanArray mNetIdInUse = new SparseBooleanArray();
    private WakeLock mNetTransitionWakeLock;
    private String mNetTransitionWakeLockCausedBy = "";
    private int mNetTransitionWakeLockSerialNumber;
    private int mNetTransitionWakeLockTimeout;
    private INetworkManagementService mNetd;
    private final HashMap<Messenger, NetworkAgentInfo> mNetworkAgentInfos = new HashMap();
    private final HashMap<Messenger, NetworkFactoryInfo> mNetworkFactoryInfos = new HashMap();
    @GuardedBy("mNetworkForNetId")
    private final SparseArray<NetworkAgentInfo> mNetworkForNetId = new SparseArray();
    private final SparseArray<NetworkAgentInfo> mNetworkForRequestId = new SparseArray();
    private int mNetworkPreference;
    private final LocalLog mNetworkRequestInfoLogs = new LocalLog(20);
    private final HashMap<NetworkRequest, NetworkRequestInfo> mNetworkRequests = new HashMap();
    int mNetworksDefined;
    private int mNextNetId = 100;
    private int mNextNetworkRequestId = 1;
    private int mNumDnsEntries;
    private NetworkInfo mP2pNetworkInfo = new NetworkInfo(13, 0, "WIFI_P2P", "");
    private PacManager mPacManager = null;
    private final WakeLock mPendingIntentWakeLock;
    private final PermissionMonitor mPermissionMonitor;
    private INetworkPolicyListener mPolicyListener = new Stub() {
        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public void onUidRulesChanged(int uid, int uidRules) {
            synchronized (ConnectivityService.this.mRulesLock) {
                if (ConnectivityService.this.mUidRules.get(uid, 0) == uidRules) {
                } else if (uidRules == 0) {
                    ConnectivityService.this.mUidRules.delete(uid);
                } else {
                    ConnectivityService.this.mUidRules.put(uid, uidRules);
                }
            }
        }

        public void onMeteredIfacesChanged(String[] meteredIfaces) {
            synchronized (ConnectivityService.this.mRulesLock) {
                ConnectivityService.this.mMeteredIfaces.clear();
                for (String iface : meteredIfaces) {
                    ConnectivityService.this.mMeteredIfaces.add(iface);
                }
            }
        }

        public void onRestrictBackgroundChanged(boolean restrictBackground) {
            synchronized (ConnectivityService.this.mRulesLock) {
                ConnectivityService.this.mRestrictBackground = restrictBackground;
            }
            if (restrictBackground) {
                ConnectivityService.log("onRestrictBackgroundChanged(true): disabling tethering");
                ConnectivityService.this.mTethering.untetherAll();
            }
        }

        public void onRestrictBackgroundWhitelistChanged(int uid, boolean whitelisted) {
        }

        public void onRestrictBackgroundBlacklistChanged(int uid, boolean blacklisted) {
        }
    };
    private INetworkPolicyManager mPolicyManager;
    List mProtectedNetworks;
    private final File mProvisioningUrlFile = new File(PROVISIONING_URL_PATH);
    private Object mProxyLock = new Object();
    private final int mReleasePendingIntentDelayMs;
    @GuardedBy("mRulesLock")
    private boolean mRestrictBackground;
    private Object mRulesLock = new Object();
    private final SettingsObserver mSettingsObserver;
    private INetworkStatsService mStatsService;
    private boolean mSystemReady;
    TelephonyManager mTelephonyManager;
    private boolean mTestMode;
    private Tethering mTethering;
    private final NetworkStateTrackerHandler mTrackerHandler;
    @GuardedBy("mRulesLock")
    private SparseIntArray mUidRules = new SparseIntArray();
    @GuardedBy("mUidToNetworkRequestCount")
    private final SparseIntArray mUidToNetworkRequestCount = new SparseIntArray();
    private BroadcastReceiver mUserIntentReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            int userId = intent.getIntExtra("android.intent.extra.user_handle", -10000);
            if (userId != -10000) {
                if ("android.intent.action.USER_STARTED".equals(action)) {
                    ConnectivityService.this.onUserStart(userId);
                } else if ("android.intent.action.USER_STOPPED".equals(action)) {
                    ConnectivityService.this.onUserStop(userId);
                } else if ("android.intent.action.USER_ADDED".equals(action)) {
                    ConnectivityService.this.onUserAdded(userId);
                } else if ("android.intent.action.USER_REMOVED".equals(action)) {
                    ConnectivityService.this.onUserRemoved(userId);
                } else if ("android.intent.action.USER_UNLOCKED".equals(action)) {
                    ConnectivityService.this.onUserUnlocked(userId);
                }
            }
        }
    };
    private UserManager mUserManager;
    private final ArrayDeque<ValidationLog> mValidationLogs = new ArrayDeque(10);
    @GuardedBy("mVpns")
    private final SparseArray<Vpn> mVpns = new SparseArray();

    private class InternalHandler extends Handler {
        public InternalHandler(Looper looper) {
            super(looper);
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public void handleMessage(Message msg) {
            boolean z = true;
            switch (msg.what) {
                case 8:
                case 24:
                    synchronized (ConnectivityService.this) {
                        if (msg.arg1 == ConnectivityService.this.mNetTransitionWakeLockSerialNumber && ConnectivityService.this.mNetTransitionWakeLock.isHeld()) {
                            ConnectivityService.this.mNetTransitionWakeLock.release();
                            String causedBy = ConnectivityService.this.mNetTransitionWakeLockCausedBy;
                            break;
                        }
                    }
                    break;
                case 9:
                    ConnectivityService.this.handleDeprecatedGlobalHttpProxy();
                    return;
                case 16:
                    ConnectivityService.this.handleApplyDefaultProxy((ProxyInfo) msg.obj);
                    return;
                case 17:
                    ConnectivityService.this.handleRegisterNetworkFactory((NetworkFactoryInfo) msg.obj);
                    return;
                case 18:
                    ConnectivityService.this.handleRegisterNetworkAgent((NetworkAgentInfo) msg.obj);
                    return;
                case 19:
                case 21:
                    ConnectivityService.this.handleRegisterNetworkRequest((NetworkRequestInfo) msg.obj);
                    return;
                case 22:
                    ConnectivityService.this.handleReleaseNetworkRequest((NetworkRequest) msg.obj, msg.arg1);
                    return;
                case 23:
                    ConnectivityService.this.handleUnregisterNetworkFactory((Messenger) msg.obj);
                    return;
                case 25:
                    for (NetworkAgentInfo nai : ConnectivityService.this.mNetworkAgentInfos.values()) {
                        nai.networkMonitor.systemReady = true;
                    }
                    return;
                case 26:
                case 31:
                    ConnectivityService.this.handleRegisterNetworkRequestWithIntent(msg);
                    return;
                case 27:
                    ConnectivityService.this.handleReleaseNetworkRequestWithIntent((PendingIntent) msg.obj, msg.arg1);
                    return;
                case 28:
                    ConnectivityService connectivityService = ConnectivityService.this;
                    Network network = (Network) msg.obj;
                    boolean z2 = msg.arg1 != 0;
                    if (msg.arg2 == 0) {
                        z = false;
                    }
                    connectivityService.handleSetAcceptUnvalidated(network, z2, z);
                    return;
                case 30:
                    ConnectivityService.this.handleMobileDataAlwaysOn();
                    return;
                case 528395:
                    ConnectivityService.this.mKeepaliveTracker.handleStartKeepalive(msg);
                    return;
                case 528396:
                    ConnectivityService.this.mKeepaliveTracker.handleStopKeepalive(ConnectivityService.this.getNetworkAgentInfoForNetwork((Network) msg.obj), msg.arg1, msg.arg2);
                    return;
                default:
                    return;
            }
        }
    }

    private class LegacyTypeTracker {
        private static final boolean DBG = true;
        private static final boolean VDBG = false;
        private ArrayList<NetworkAgentInfo>[] mTypeLists = new ArrayList[46];

        public void addSupportedType(int type) {
            if (this.mTypeLists[type] != null) {
                throw new IllegalStateException("legacy list for type " + type + "already initialized");
            }
            this.mTypeLists[type] = new ArrayList();
        }

        public boolean isTypeSupported(int type) {
            return (!ConnectivityManager.isNetworkTypeValid(type) || this.mTypeLists[type] == null) ? false : DBG;
        }

        public NetworkAgentInfo getNetworkForType(int type) {
            if (!isTypeSupported(type) || this.mTypeLists[type].isEmpty()) {
                return null;
            }
            return (NetworkAgentInfo) this.mTypeLists[type].get(0);
        }

        private void maybeLogBroadcast(NetworkAgentInfo nai, DetailedState state, int type, boolean isDefaultNetwork) {
            ConnectivityService.log("Sending " + state + " broadcast for type " + type + " " + nai.name() + " isDefaultNetwork=" + isDefaultNetwork);
        }

        public void add(int type, NetworkAgentInfo nai) {
            if (isTypeSupported(type)) {
                ArrayList<NetworkAgentInfo> list = this.mTypeLists[type];
                if (!list.contains(nai)) {
                    list.add(nai);
                    boolean isDefaultNetwork = ConnectivityService.this.isDefaultNetwork(nai);
                    if (list.size() == 1 || isDefaultNetwork) {
                        maybeLogBroadcast(nai, DetailedState.CONNECTED, type, isDefaultNetwork);
                        ConnectivityService.this.sendLegacyNetworkBroadcast(nai, DetailedState.CONNECTED, type);
                    }
                }
            }
        }

        public void remove(int type, NetworkAgentInfo nai, boolean wasDefault) {
            ArrayList<NetworkAgentInfo> list = this.mTypeLists[type];
            if (list != null && !list.isEmpty()) {
                boolean wasFirstNetwork = ((NetworkAgentInfo) list.get(0)).equals(nai);
                if (list.remove(nai)) {
                    DetailedState state = DetailedState.DISCONNECTED;
                    if (wasFirstNetwork || wasDefault) {
                        maybeLogBroadcast(nai, state, type, wasDefault);
                        ConnectivityService.this.sendLegacyNetworkBroadcast(nai, state, type);
                    }
                    if (!list.isEmpty() && wasFirstNetwork) {
                        ConnectivityService.log("Other network available for type " + type + ", sending connected broadcast");
                        NetworkAgentInfo replacement = (NetworkAgentInfo) list.get(0);
                        maybeLogBroadcast(replacement, state, type, ConnectivityService.this.isDefaultNetwork(replacement));
                        ConnectivityService.this.sendLegacyNetworkBroadcast(replacement, state, type);
                    }
                }
            }
        }

        public void remove(NetworkAgentInfo nai, boolean wasDefault) {
            for (int type = 0; type < this.mTypeLists.length; type++) {
                remove(type, nai, wasDefault);
            }
        }

        public void update(NetworkAgentInfo nai) {
            boolean isDefault = ConnectivityService.this.isDefaultNetwork(nai);
            DetailedState state = nai.networkInfo.getDetailedState();
            for (int type = 0; type < this.mTypeLists.length; type++) {
                boolean isFirst;
                ArrayList<NetworkAgentInfo> list = this.mTypeLists[type];
                boolean contains = list != null ? list.contains(nai) : false;
                if (list == null || list.size() <= 0) {
                    isFirst = false;
                } else {
                    boolean z;
                    if (nai == list.get(0)) {
                        z = DBG;
                    } else {
                        z = false;
                    }
                    isFirst = z;
                }
                if (isFirst || (contains && isDefault)) {
                    maybeLogBroadcast(nai, state, type, isDefault);
                    ConnectivityService.this.sendLegacyNetworkBroadcast(nai, state, type);
                }
            }
        }

        private String naiToString(NetworkAgentInfo nai) {
            String state;
            String name = nai != null ? nai.name() : "null";
            if (nai.networkInfo != null) {
                state = nai.networkInfo.getState() + "/" + nai.networkInfo.getDetailedState();
            } else {
                state = "???/???";
            }
            return name + " " + state;
        }

        public void dump(IndentingPrintWriter pw) {
            int type;
            pw.println("mLegacyTypeTracker:");
            pw.increaseIndent();
            pw.print("Supported types:");
            for (type = 0; type < this.mTypeLists.length; type++) {
                if (this.mTypeLists[type] != null) {
                    pw.print(" " + type);
                }
            }
            pw.println();
            pw.println("Current state:");
            pw.increaseIndent();
            type = 0;
            while (type < this.mTypeLists.length) {
                if (!(this.mTypeLists[type] == null || this.mTypeLists[type].size() == 0)) {
                    for (NetworkAgentInfo nai : this.mTypeLists[type]) {
                        pw.println(type + " " + naiToString(nai));
                    }
                }
                type++;
            }
            pw.decreaseIndent();
            pw.decreaseIndent();
            pw.println();
        }
    }

    private static class NetworkFactoryInfo {
        public final AsyncChannel asyncChannel;
        public final Messenger messenger;
        public final String name;

        public NetworkFactoryInfo(String name, Messenger messenger, AsyncChannel asyncChannel) {
            this.name = name;
            this.messenger = messenger;
            this.asyncChannel = asyncChannel;
        }
    }

    private class NetworkRequestInfo implements DeathRecipient {
        private static final /* synthetic */ int[] -com-android-server-ConnectivityService$NetworkRequestTypeSwitchesValues = null;
        final /* synthetic */ int[] $SWITCH_TABLE$com$android$server$ConnectivityService$NetworkRequestType;
        private final IBinder mBinder;
        final PendingIntent mPendingIntent;
        boolean mPendingIntentSent;
        final int mPid;
        private final NetworkRequestType mType;
        final int mUid;
        final Messenger messenger;
        final NetworkRequest request;

        private static /* synthetic */ int[] -getcom-android-server-ConnectivityService$NetworkRequestTypeSwitchesValues() {
            if (-com-android-server-ConnectivityService$NetworkRequestTypeSwitchesValues != null) {
                return -com-android-server-ConnectivityService$NetworkRequestTypeSwitchesValues;
            }
            int[] iArr = new int[NetworkRequestType.values().length];
            try {
                iArr[NetworkRequestType.LISTEN.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                iArr[NetworkRequestType.REQUEST.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                iArr[NetworkRequestType.TRACK_DEFAULT.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            -com-android-server-ConnectivityService$NetworkRequestTypeSwitchesValues = iArr;
            return iArr;
        }

        NetworkRequestInfo(NetworkRequest r, PendingIntent pi, NetworkRequestType type) {
            this.request = r;
            this.mPendingIntent = pi;
            this.messenger = null;
            this.mBinder = null;
            this.mPid = ConnectivityService.getCallingPid();
            this.mUid = ConnectivityService.getCallingUid();
            this.mType = type;
            enforceRequestCountLimit();
        }

        NetworkRequestInfo(Messenger m, NetworkRequest r, IBinder binder, NetworkRequestType type) {
            this.messenger = m;
            this.request = r;
            this.mBinder = binder;
            this.mPid = ConnectivityService.getCallingPid();
            this.mUid = ConnectivityService.getCallingUid();
            this.mType = type;
            this.mPendingIntent = null;
            enforceRequestCountLimit();
            try {
                this.mBinder.linkToDeath(this, 0);
            } catch (RemoteException e) {
                binderDied();
            }
        }

        private void enforceRequestCountLimit() {
            synchronized (ConnectivityService.this.mUidToNetworkRequestCount) {
                int networkRequests = ConnectivityService.this.mUidToNetworkRequestCount.get(this.mUid, 0) + 1;
                if (networkRequests >= 100) {
                    throw new IllegalArgumentException("Too many NetworkRequests filed");
                }
                ConnectivityService.this.mUidToNetworkRequestCount.put(this.mUid, networkRequests);
            }
        }

        private String typeString() {
            switch (-getcom-android-server-ConnectivityService$NetworkRequestTypeSwitchesValues()[this.mType.ordinal()]) {
                case 1:
                    return "Listen";
                case 2:
                    return "Request";
                case 3:
                    return "Track default";
                default:
                    return "unknown type";
            }
        }

        void unlinkDeathRecipient() {
            if (this.mBinder != null) {
                this.mBinder.unlinkToDeath(this, 0);
            }
        }

        public void binderDied() {
            ConnectivityService.log("ConnectivityService NetworkRequestInfo binderDied(" + this.request + ", " + this.mBinder + ")");
            ConnectivityService.this.releaseNetworkRequest(this.request);
        }

        public boolean isRequest() {
            if (this.mType == NetworkRequestType.TRACK_DEFAULT || this.mType == NetworkRequestType.REQUEST) {
                return true;
            }
            return false;
        }

        public String toString() {
            return typeString() + " from uid/pid:" + this.mUid + "/" + this.mPid + " for " + this.request + (this.mPendingIntent == null ? "" : " to trigger " + this.mPendingIntent);
        }
    }

    private enum NetworkRequestType {
        LISTEN,
        TRACK_DEFAULT,
        REQUEST
    }

    private class NetworkStateTrackerHandler extends Handler {
        public NetworkStateTrackerHandler(Looper looper) {
            super(looper);
        }

        private boolean maybeHandleAsyncChannelMessage(Message msg) {
            switch (msg.what) {
                case 69632:
                    ConnectivityService.this.handleAsyncChannelHalfConnect(msg);
                    break;
                case 69635:
                    NetworkAgentInfo nai = (NetworkAgentInfo) ConnectivityService.this.mNetworkAgentInfos.get(msg.replyTo);
                    if (nai != null) {
                        nai.asyncChannel.disconnect();
                        break;
                    }
                    break;
                case 69636:
                    ConnectivityService.this.handleAsyncChannelDisconnected(msg);
                    break;
                default:
                    return false;
            }
            return true;
        }

        private void maybeHandleNetworkAgentMessage(Message msg) {
            NetworkAgentInfo nai = (NetworkAgentInfo) ConnectivityService.this.mNetworkAgentInfos.get(msg.replyTo);
            if (nai == null) {
                ConnectivityService.log(String.format("%s from unknown NetworkAgent", new Object[]{(String) ConnectivityService.sMagicDecoderRing.get(msg.what, Integer.toString(msg.what))}));
                return;
            }
            switch (msg.what) {
                case 528385:
                    ConnectivityService.this.updateNetworkInfo(nai, msg.obj);
                    break;
                case 528386:
                    NetworkCapabilities networkCapabilities = msg.obj;
                    if (networkCapabilities.hasCapability(17) || networkCapabilities.hasCapability(16)) {
                        Slog.wtf(ConnectivityService.TAG, "BUG: " + nai + " has CS-managed capability.");
                    }
                    if (nai.everConnected && !nai.networkCapabilities.equalImmutableCapabilities(networkCapabilities)) {
                        Slog.wtf(ConnectivityService.TAG, "BUG: " + nai + " changed immutable capabilities: " + nai.networkCapabilities + " -> " + networkCapabilities);
                    }
                    ConnectivityService.this.updateCapabilities(nai, networkCapabilities);
                    break;
                case 528387:
                    ConnectivityService.log("Update of LinkProperties for " + nai.name() + "; created=" + nai.created + "; everConnected=" + nai.everConnected);
                    LinkProperties oldLp = nai.linkProperties;
                    synchronized (nai) {
                        nai.linkProperties = (LinkProperties) msg.obj;
                    }
                    if (nai.everConnected) {
                        ConnectivityService.this.updateLinkProperties(nai, oldLp);
                        break;
                    }
                    break;
                case 528388:
                    Integer score = msg.obj;
                    if (score != null) {
                        ConnectivityService.this.updateNetworkScore(nai, score.intValue());
                        break;
                    }
                    break;
                case 528389:
                    try {
                        ConnectivityService.this.mNetd.addVpnUidRanges(nai.network.netId, (UidRange[]) msg.obj);
                        break;
                    } catch (Exception e) {
                        ConnectivityService.loge("Exception in addVpnUidRanges: " + e);
                        break;
                    }
                case 528390:
                    try {
                        ConnectivityService.this.mNetd.removeVpnUidRanges(nai.network.netId, (UidRange[]) msg.obj);
                        break;
                    } catch (Exception e2) {
                        ConnectivityService.loge("Exception in removeVpnUidRanges: " + e2);
                        break;
                    }
                case 528392:
                    if (nai.everConnected && !nai.networkMisc.explicitlySelected) {
                        ConnectivityService.loge("ERROR: already-connected network explicitly selected.");
                    }
                    nai.networkMisc.explicitlySelected = true;
                    nai.networkMisc.acceptUnvalidated = ((Boolean) msg.obj).booleanValue();
                    break;
                case 528397:
                    ConnectivityService.this.mKeepaliveTracker.handleEventPacketKeepalive(nai, msg);
                    break;
                case AbstractConnectivityService.EVENT_SET_EXPLICITLY_UNSELECTED /*528585*/:
                    ConnectivityService.this.setExplicitlyUnselected((NetworkAgentInfo) ConnectivityService.this.mNetworkAgentInfos.get(msg.replyTo));
                    break;
                case AbstractConnectivityService.EVENT_UPDATE_NETWORK_CONCURRENTLY /*528586*/:
                    ConnectivityService.this.updateNetworkConcurrently((NetworkAgentInfo) ConnectivityService.this.mNetworkAgentInfos.get(msg.replyTo), (NetworkInfo) msg.obj);
                    break;
                case AbstractConnectivityService.EVENT_TRIGGER_ROAMING_NETWORK_MONITOR /*528587*/:
                    ConnectivityService.this.triggerRoamingNetworkMonitor((NetworkAgentInfo) ConnectivityService.this.mNetworkAgentInfos.get(msg.replyTo));
                    break;
            }
        }

        private boolean maybeHandleNetworkMonitorMessage(Message msg) {
            NetworkAgentInfo nai;
            switch (msg.what) {
                case 528485:
                    nai = (NetworkAgentInfo) ConnectivityService.this.mNetworkAgentInfos.get(msg.replyTo);
                    if (nai != null) {
                        ConnectivityService.this.rematchNetworkAndRequests(nai, ReapUnvalidatedNetworks.DONT_REAP);
                        break;
                    }
                    ConnectivityService.loge("EVENT_REMATCH_NETWORK_AND_REQUESTS from unknown NetworkAgent");
                    break;
                case NetworkMonitor.EVENT_NETWORK_TESTED /*532482*/:
                    synchronized (ConnectivityService.this.mNetworkForNetId) {
                        nai = (NetworkAgentInfo) ConnectivityService.this.mNetworkForNetId.get(msg.arg2);
                    }
                    if (nai != null) {
                        String str;
                        boolean valid = msg.arg1 == 0;
                        StringBuilder append = new StringBuilder().append(nai.name()).append(" validation ").append(valid ? "passed" : "failed");
                        if (msg.obj == null) {
                            str = "";
                        } else {
                            str = " with redirect to " + ((String) msg.obj);
                        }
                        ConnectivityService.log(append.append(str).toString());
                        if (valid != nai.lastValidated) {
                            int oldScore = nai.getCurrentScore();
                            nai.lastValidated = valid;
                            nai.everValidated |= valid;
                            ConnectivityService.this.updateCapabilities(nai, nai.networkCapabilities);
                            if (oldScore != nai.getCurrentScore()) {
                                ConnectivityService.this.sendUpdatedScoreToFactories(nai);
                            }
                        }
                        ConnectivityService.this.updateInetCondition(nai);
                        if (!ConnectivityService.this.reportPortalNetwork(nai, msg.arg1)) {
                            Bundle redirectUrlBundle = new Bundle();
                            redirectUrlBundle.putString(NetworkAgent.REDIRECT_URL_KEY, (String) msg.obj);
                            nai.asyncChannel.sendMessage(528391, valid ? 1 : 2, 0, redirectUrlBundle);
                            break;
                        }
                    }
                    break;
                case NetworkMonitor.EVENT_NETWORK_LINGER_COMPLETE /*532485*/:
                    nai = (NetworkAgentInfo) msg.obj;
                    if (ConnectivityService.this.isLiveNetworkAgent(nai, msg.what)) {
                        ConnectivityService.this.handleLingerComplete(nai);
                        break;
                    }
                    break;
                case NetworkMonitor.EVENT_PROVISIONING_NOTIFICATION /*532490*/:
                    int netId = msg.arg2;
                    boolean visible = msg.arg1 != 0;
                    synchronized (ConnectivityService.this.mNetworkForNetId) {
                        nai = (NetworkAgentInfo) ConnectivityService.this.mNetworkForNetId.get(netId);
                    }
                    if (!(nai == null || visible == nai.lastCaptivePortalDetected)) {
                        nai.lastCaptivePortalDetected = visible;
                        nai.everCaptivePortalDetected |= visible;
                        ConnectivityService.this.updateCapabilities(nai, nai.networkCapabilities);
                    }
                    if (visible) {
                        if (nai != null) {
                            ConnectivityService.this.setProvNotificationVisibleIntent(true, netId, NotificationType.SIGN_IN, nai.networkInfo.getType(), nai.networkInfo.getExtraInfo(), (PendingIntent) msg.obj, nai.networkMisc.explicitlySelected);
                            break;
                        }
                        ConnectivityService.loge("EVENT_PROVISIONING_NOTIFICATION from unknown NetworkMonitor");
                        break;
                    }
                    ConnectivityService.this.setProvNotificationVisibleIntent(false, netId, null, 0, null, null, false);
                    break;
                default:
                    return false;
            }
            return true;
        }

        public void handleMessage(Message msg) {
            if (!maybeHandleAsyncChannelMessage(msg) && !maybeHandleNetworkMonitorMessage(msg)) {
                maybeHandleNetworkAgentMessage(msg);
            }
        }
    }

    private enum NotificationType {
        SIGN_IN,
        NO_INTERNET
    }

    private enum ReapUnvalidatedNetworks {
        REAP,
        DONT_REAP
    }

    private static class SettingsObserver extends ContentObserver {
        private final Context mContext;
        private final Handler mHandler;
        private final HashMap<Uri, Integer> mUriEventMap = new HashMap();

        SettingsObserver(Context context, Handler handler) {
            super(null);
            this.mContext = context;
            this.mHandler = handler;
        }

        void observe(Uri uri, int what) {
            this.mUriEventMap.put(uri, Integer.valueOf(what));
            this.mContext.getContentResolver().registerContentObserver(uri, false, this);
        }

        public void onChange(boolean selfChange) {
            Slog.wtf(ConnectivityService.TAG, "Should never be reached.");
        }

        public void onChange(boolean selfChange, Uri uri) {
            Integer what = (Integer) this.mUriEventMap.get(uri);
            if (what != null) {
                this.mHandler.obtainMessage(what.intValue()).sendToTarget();
            } else {
                ConnectivityService.loge("No matching event to send for URI=" + uri);
            }
        }
    }

    private static class ValidationLog {
        final ReadOnlyLocalLog mLog;
        final Network mNetwork;
        final String mNetworkExtraInfo;

        ValidationLog(Network network, String networkExtraInfo, ReadOnlyLocalLog log) {
            this.mNetwork = network;
            this.mNetworkExtraInfo = networkExtraInfo;
            this.mLog = log;
        }
    }

    private void addValidationLogs(ReadOnlyLocalLog log, Network network, String networkExtraInfo) {
        synchronized (this.mValidationLogs) {
            while (this.mValidationLogs.size() >= 10) {
                this.mValidationLogs.removeLast();
            }
            this.mValidationLogs.addFirst(new ValidationLog(network, networkExtraInfo, log));
        }
    }

    protected HandlerThread createHandlerThread() {
        return new HandlerThread("ConnectivityServiceThread");
    }

    public ConnectivityService(Context context, INetworkManagementService netManager, INetworkStatsService statsService, INetworkPolicyManager policyManager) {
        boolean equals;
        log("ConnectivityService starting up");
        this.mDefaultRequest = createInternetRequestForTransport(-1);
        NetworkRequestInfo defaultNRI = new NetworkRequestInfo(null, this.mDefaultRequest, new Binder(), NetworkRequestType.REQUEST);
        this.mNetworkRequests.put(this.mDefaultRequest, defaultNRI);
        this.mNetworkRequestInfoLogs.log("REGISTER " + defaultNRI);
        this.mDefaultMobileDataRequest = createInternetRequestForTransport(0);
        this.mHandlerThread = createHandlerThread();
        this.mHandlerThread.start();
        this.mHandler = new InternalHandler(this.mHandlerThread.getLooper());
        this.mTrackerHandler = new NetworkStateTrackerHandler(this.mHandlerThread.getLooper());
        if (TextUtils.isEmpty(SystemProperties.get("net.hostname"))) {
            String id = Secure.getString(context.getContentResolver(), "android_id");
            if (id != null && id.length() > 0) {
                String name = SystemProperties.get("ro.config.marketing_name");
                if (TextUtils.isEmpty(name)) {
                    name = Build.MODEL.replace(" ", "_");
                    if (name != null && name.length() > 18) {
                        name = name.substring(0, 18);
                    }
                    name = name + "-" + id;
                    if (name != null && name.length() > 25) {
                        name = name.substring(0, 25);
                    }
                } else {
                    name = name.replace(" ", "_");
                    if (name.length() > 25) {
                        name = name.substring(0, 25);
                    }
                }
                SystemProperties.set("net.hostname", name);
            }
        }
        this.mReleasePendingIntentDelayMs = Secure.getInt(context.getContentResolver(), "connectivity_release_pending_intent_delay_ms", 5000);
        this.mContext = (Context) checkNotNull(context, "missing Context");
        this.mNetd = (INetworkManagementService) checkNotNull(netManager, "missing INetworkManagementService");
        this.mStatsService = (INetworkStatsService) checkNotNull(statsService, "missing INetworkStatsService");
        this.mPolicyManager = (INetworkPolicyManager) checkNotNull(policyManager, "missing INetworkPolicyManager");
        this.mKeyStore = KeyStore.getInstance();
        this.mTelephonyManager = (TelephonyManager) this.mContext.getSystemService("phone");
        try {
            this.mPolicyManager.setConnectivityListener(this.mPolicyListener);
            this.mRestrictBackground = this.mPolicyManager.getRestrictBackground();
        } catch (RemoteException e) {
            loge("unable to register INetworkPolicyListener" + e);
        }
        PowerManager powerManager = (PowerManager) context.getSystemService("power");
        this.mNetTransitionWakeLock = powerManager.newWakeLock(1, TAG);
        this.mNetTransitionWakeLockTimeout = this.mContext.getResources().getInteger(17694735);
        this.mPendingIntentWakeLock = powerManager.newWakeLock(1, TAG);
        this.mNetConfigs = new NetworkConfig[46];
        boolean wifiOnly = SystemProperties.getBoolean("ro.radio.noril", false);
        log("wifiOnly=" + wifiOnly);
        for (String naString : context.getResources().getStringArray(17235983)) {
            try {
                NetworkConfig n = new NetworkConfig(naString);
                log("naString=" + naString + " config=" + n);
                if (n.type > 45) {
                    loge("Error in networkAttributes - ignoring attempt to define type " + n.type);
                } else {
                    if (wifiOnly) {
                        if (ConnectivityManager.isNetworkTypeMobile(n.type)) {
                            log("networkAttributes - ignoring mobile as this dev is wifiOnly " + n.type);
                        }
                    }
                    if (this.mNetConfigs[n.type] != null) {
                        loge("Error in networkAttributes - ignoring attempt to redefine type " + n.type);
                    } else {
                        this.mLegacyTypeTracker.addSupportedType(n.type);
                        this.mNetConfigs[n.type] = n;
                        this.mNetworksDefined++;
                    }
                }
            } catch (Exception e2) {
            }
        }
        if (this.mNetConfigs[17] == null) {
            this.mLegacyTypeTracker.addSupportedType(17);
            this.mNetworksDefined++;
        }
        log("mNetworksDefined=" + this.mNetworksDefined);
        this.mProtectedNetworks = new ArrayList();
        for (int p : context.getResources().getIntArray(17235984)) {
            if (this.mNetConfigs[p] == null || this.mProtectedNetworks.contains(Integer.valueOf(p))) {
                loge("Ignoring protectedNetwork " + p);
            } else {
                this.mProtectedNetworks.add(Integer.valueOf(p));
            }
        }
        if (SystemProperties.get("cm.test.mode").equals("true")) {
            equals = SystemProperties.get("ro.build.type").equals("eng");
        } else {
            equals = false;
        }
        this.mTestMode = equals;
        this.mTethering = new Tethering(this.mContext, this.mNetd, statsService);
        this.mPermissionMonitor = new PermissionMonitor(this.mContext, this.mNetd);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.USER_STARTED");
        intentFilter.addAction("android.intent.action.USER_STOPPED");
        intentFilter.addAction("android.intent.action.USER_ADDED");
        intentFilter.addAction("android.intent.action.USER_REMOVED");
        intentFilter.addAction("android.intent.action.USER_UNLOCKED");
        this.mContext.registerReceiverAsUser(this.mUserIntentReceiver, UserHandle.ALL, intentFilter, null, null);
        try {
            this.mNetd.registerObserver(this.mTethering);
            this.mNetd.registerObserver(this.mDataActivityObserver);
        } catch (RemoteException e3) {
            loge("Error registering observer :" + e3);
        }
        this.mInetLog = new ArrayList();
        this.mSettingsObserver = new SettingsObserver(this.mContext, this.mHandler);
        registerSettingsCallbacks();
        this.mDataConnectionStats = new DataConnectionStats(this.mContext);
        this.mDataConnectionStats.startMonitoring();
        this.mPacManager = new PacManager(this.mContext, this.mHandler, 16);
        this.mUserManager = (UserManager) context.getSystemService("user");
        this.mKeepaliveTracker = new KeepaliveTracker(this.mHandler);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PKT_CNT_SAMPLE_INTERVAL_ELAPSED);
        if (HiSiWifiComm.hisiWifiEnabled()) {
            filter.addAction("android.net.wifi.p2p.WIFI_P2P_NETWORK_CHANGED_ACTION");
        }
        this.mContext.registerReceiver(new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (HiSiWifiComm.hisiWifiEnabled() && "android.net.wifi.p2p.WIFI_P2P_NETWORK_CHANGED_ACTION".equals(action)) {
                    ConnectivityService.this.mP2pNetworkInfo = (NetworkInfo) intent.getParcelableExtra("networkInfo");
                }
            }
        }, new IntentFilter(filter));
    }

    private NetworkRequest createInternetRequestForTransport(int transportType) {
        NetworkCapabilities netCap = new NetworkCapabilities();
        netCap.addCapability(12);
        netCap.addCapability(13);
        if (transportType > -1) {
            netCap.addTransportType(transportType);
        }
        return new NetworkRequest(netCap, -1, nextNetworkRequestId());
    }

    private void handleMobileDataAlwaysOn() {
        boolean isEnabled = true;
        boolean enable = Global.getInt(this.mContext.getContentResolver(), "mobile_data_always_on", 0) == 1;
        if (this.mNetworkRequests.get(this.mDefaultMobileDataRequest) == null) {
            isEnabled = false;
        }
        if (enable != isEnabled) {
            if (enable) {
                handleRegisterNetworkRequest(new NetworkRequestInfo(null, this.mDefaultMobileDataRequest, new Binder(), NetworkRequestType.REQUEST));
            } else {
                handleReleaseNetworkRequest(this.mDefaultMobileDataRequest, 1000);
            }
        }
    }

    private void registerSettingsCallbacks() {
        this.mSettingsObserver.observe(Global.getUriFor("http_proxy"), 9);
        this.mSettingsObserver.observe(Global.getUriFor("mobile_data_always_on"), 30);
    }

    private synchronized int nextNetworkRequestId() {
        int i;
        i = this.mNextNetworkRequestId;
        this.mNextNetworkRequestId = i + 1;
        return i;
    }

    protected int reserveNetId() {
        synchronized (this.mNetworkForNetId) {
            int i = 100;
            while (i <= MAX_NET_ID) {
                int netId = this.mNextNetId;
                int i2 = this.mNextNetId + 1;
                this.mNextNetId = i2;
                if (i2 > MAX_NET_ID) {
                    this.mNextNetId = 100;
                }
                if (this.mNetIdInUse.get(netId)) {
                    i++;
                } else {
                    this.mNetIdInUse.put(netId, true);
                    return netId;
                }
            }
            throw new IllegalStateException("No free netIds");
        }
    }

    private NetworkState getFilteredNetworkState(int networkType, int uid, boolean ignoreBlocked) {
        boolean z = true;
        if (!this.mLegacyTypeTracker.isTypeSupported(networkType)) {
            return NetworkState.EMPTY;
        }
        NetworkState state;
        NetworkAgentInfo nai = this.mLegacyTypeTracker.getNetworkForType(networkType);
        if (nai != null) {
            state = nai.getNetworkState();
            state.networkInfo.setType(networkType);
        } else {
            NetworkInfo info = new NetworkInfo(networkType, 0, ConnectivityManager.getNetworkTypeName(networkType), "");
            info.setDetailedState(DetailedState.DISCONNECTED, null, null);
            if (networkType == 13 || networkType == 1) {
                z = false;
            } else if (networkType == 0) {
                z = false;
            }
            info.setIsAvailable(z);
            state = new NetworkState(info, new LinkProperties(), new NetworkCapabilities(), null, null, null);
        }
        filterNetworkStateForUid(state, uid, ignoreBlocked);
        return state;
    }

    private NetworkAgentInfo getNetworkAgentInfoForNetwork(Network network) {
        if (network == null) {
            return null;
        }
        NetworkAgentInfo networkAgentInfo;
        synchronized (this.mNetworkForNetId) {
            networkAgentInfo = (NetworkAgentInfo) this.mNetworkForNetId.get(network.netId);
        }
        return networkAgentInfo;
    }

    private Network[] getVpnUnderlyingNetworks(int uid) {
        if (!this.mLockdownEnabled) {
            int user = UserHandle.getUserId(uid);
            synchronized (this.mVpns) {
                Vpn vpn = (Vpn) this.mVpns.get(user);
                if (vpn == null || !vpn.appliesToUid(uid)) {
                } else {
                    Network[] underlyingNetworks = vpn.getUnderlyingNetworks();
                    return underlyingNetworks;
                }
            }
        }
        return null;
    }

    private NetworkState getUnfilteredActiveNetworkState(int uid) {
        NetworkAgentInfo nai = null;
        NetworkAgentInfo o = this.mNetworkForRequestId.get(this.mDefaultRequest.requestId);
        if (o instanceof NetworkAgentInfo) {
            nai = o;
        }
        Network[] networks = getVpnUnderlyingNetworks(uid);
        if (networks != null) {
            if (networks.length > 0) {
                nai = getNetworkAgentInfoForNetwork(networks[0]);
            } else {
                nai = null;
            }
        }
        if (nai != null) {
            return nai.getNetworkState();
        }
        return NetworkState.EMPTY;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private boolean isNetworkWithLinkPropertiesBlocked(LinkProperties lp, int uid, boolean ignoreBlocked) {
        boolean z = false;
        if (ignoreBlocked || isSystem(uid)) {
            return false;
        }
        synchronized (this.mVpns) {
            Vpn vpn = (Vpn) this.mVpns.get(UserHandle.getUserId(uid));
            if (vpn == null || !vpn.isBlockingUid(uid)) {
            } else {
                return true;
            }
        }
    }

    private void maybeLogBlockedNetworkInfo(NetworkInfo ni, int uid) {
        if (ni != null) {
            boolean removed = false;
            boolean added = false;
            synchronized (this.mBlockedAppUids) {
                if (ni.getDetailedState() == DetailedState.BLOCKED && this.mBlockedAppUids.add(Integer.valueOf(uid))) {
                    added = true;
                } else if (ni.isConnected() && this.mBlockedAppUids.remove(Integer.valueOf(uid))) {
                    removed = true;
                }
            }
            if (added) {
                log("Returning blocked NetworkInfo to uid=" + uid);
            } else if (removed) {
                log("Returning unblocked NetworkInfo to uid=" + uid);
            }
        }
    }

    private void filterNetworkStateForUid(NetworkState state, int uid, boolean ignoreBlocked) {
        if (state != null && state.networkInfo != null && state.linkProperties != null) {
            if (isNetworkWithLinkPropertiesBlocked(state.linkProperties, uid, ignoreBlocked)) {
                state.networkInfo.setDetailedState(DetailedState.BLOCKED, null, null);
            }
            if (this.mLockdownTracker != null) {
                this.mLockdownTracker.augmentNetworkInfo(state.networkInfo);
            }
            long token = Binder.clearCallingIdentity();
            try {
                state.networkInfo.setMetered(this.mPolicyManager.isNetworkMetered(state));
            } catch (RemoteException e) {
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }
    }

    public NetworkInfo getActiveNetworkInfo() {
        enforceAccessPermission();
        int uid = Binder.getCallingUid();
        NetworkState state = getUnfilteredActiveNetworkState(uid);
        filterNetworkStateForUid(state, uid, false);
        maybeLogBlockedNetworkInfo(state.networkInfo, uid);
        return state.networkInfo;
    }

    public Network getActiveNetwork() {
        enforceAccessPermission();
        return getActiveNetworkForUidInternal(Binder.getCallingUid(), false);
    }

    public Network getActiveNetworkForUid(int uid, boolean ignoreBlocked) {
        enforceConnectivityInternalPermission();
        return getActiveNetworkForUidInternal(uid, ignoreBlocked);
    }

    private Network getActiveNetworkForUidInternal(int uid, boolean ignoreBlocked) {
        NetworkAgentInfo nai;
        Network network = null;
        int user = UserHandle.getUserId(uid);
        int vpnNetId = 0;
        synchronized (this.mVpns) {
            Vpn vpn = (Vpn) this.mVpns.get(user);
            if (vpn != null && vpn.appliesToUid(uid)) {
                vpnNetId = vpn.getNetId();
            }
        }
        if (vpnNetId != 0) {
            synchronized (this.mNetworkForNetId) {
                nai = (NetworkAgentInfo) this.mNetworkForNetId.get(vpnNetId);
            }
            if (nai != null) {
                return nai.network;
            }
        }
        nai = getDefaultNetwork();
        if (nai != null && isNetworkWithLinkPropertiesBlocked(nai.linkProperties, uid, ignoreBlocked)) {
            nai = null;
        }
        if (nai != null) {
            network = nai.network;
        }
        return network;
    }

    public NetworkInfo getActiveNetworkInfoUnfiltered() {
        enforceAccessPermission();
        return getUnfilteredActiveNetworkState(Binder.getCallingUid()).networkInfo;
    }

    public NetworkInfo getActiveNetworkInfoForUid(int uid, boolean ignoreBlocked) {
        enforceConnectivityInternalPermission();
        NetworkState state = getUnfilteredActiveNetworkState(uid);
        filterNetworkStateForUid(state, uid, ignoreBlocked);
        return state.networkInfo;
    }

    public NetworkInfo getNetworkInfo(int networkType) {
        enforceAccessPermission();
        if (HiSiWifiComm.hisiWifiEnabled() && 13 == networkType) {
            log("getNetworkInfo mP2pNetworkInfo:" + this.mP2pNetworkInfo);
            return new NetworkInfo(this.mP2pNetworkInfo);
        }
        int uid = Binder.getCallingUid();
        if (getVpnUnderlyingNetworks(uid) != null) {
            NetworkState state = getUnfilteredActiveNetworkState(uid);
            if (state.networkInfo != null && state.networkInfo.getType() == networkType) {
                filterNetworkStateForUid(state, uid, false);
                return state.networkInfo;
            }
        }
        return getFilteredNetworkState(networkType, uid, false).networkInfo;
    }

    public NetworkInfo getNetworkInfoForUid(Network network, int uid, boolean ignoreBlocked) {
        enforceAccessPermission();
        NetworkAgentInfo nai = getNetworkAgentInfoForNetwork(network);
        if (nai == null) {
            return null;
        }
        NetworkState state = nai.getNetworkState();
        filterNetworkStateForUid(state, uid, ignoreBlocked);
        return state.networkInfo;
    }

    public NetworkInfo[] getAllNetworkInfo() {
        enforceAccessPermission();
        ArrayList<NetworkInfo> result = Lists.newArrayList();
        for (int networkType = 0; networkType <= 45; networkType++) {
            NetworkInfo info = getNetworkInfo(networkType);
            if (info != null) {
                result.add(info);
            }
        }
        return (NetworkInfo[]) result.toArray(new NetworkInfo[result.size()]);
    }

    public Network getNetworkForType(int networkType) {
        enforceAccessPermission();
        int uid = Binder.getCallingUid();
        NetworkState state = getFilteredNetworkState(networkType, uid, false);
        if (isNetworkWithLinkPropertiesBlocked(state.linkProperties, uid, false)) {
            return null;
        }
        return state.network;
    }

    public Network[] getAllNetworks() {
        Network[] result;
        enforceAccessPermission();
        synchronized (this.mNetworkForNetId) {
            result = new Network[this.mNetworkForNetId.size()];
            for (int i = 0; i < this.mNetworkForNetId.size(); i++) {
                result[i] = ((NetworkAgentInfo) this.mNetworkForNetId.valueAt(i)).network;
            }
        }
        return result;
    }

    public NetworkCapabilities[] getDefaultNetworkCapabilitiesForUser(int userId) {
        enforceAccessPermission();
        HashMap<Network, NetworkCapabilities> result = new HashMap();
        NetworkAgentInfo nai = getDefaultNetwork();
        NetworkCapabilities nc = getNetworkCapabilitiesInternal(nai);
        if (nc != null) {
            result.put(nai.network, nc);
        }
        if (!this.mLockdownEnabled) {
            synchronized (this.mVpns) {
                Vpn vpn = (Vpn) this.mVpns.get(userId);
                if (vpn != null) {
                    Network[] networks = vpn.getUnderlyingNetworks();
                    if (networks != null) {
                        for (Network network : networks) {
                            nc = getNetworkCapabilitiesInternal(getNetworkAgentInfoForNetwork(network));
                            if (nc != null) {
                                result.put(network, nc);
                            }
                        }
                    }
                }
            }
        }
        return (NetworkCapabilities[]) result.values().toArray(new NetworkCapabilities[result.size()]);
    }

    public boolean isNetworkSupported(int networkType) {
        enforceAccessPermission();
        return this.mLegacyTypeTracker.isTypeSupported(networkType);
    }

    public LinkProperties getActiveLinkProperties() {
        enforceAccessPermission();
        return getUnfilteredActiveNetworkState(Binder.getCallingUid()).linkProperties;
    }

    public LinkProperties getLinkPropertiesForType(int networkType) {
        enforceAccessPermission();
        NetworkAgentInfo nai = this.mLegacyTypeTracker.getNetworkForType(networkType);
        if (nai == null) {
            return null;
        }
        LinkProperties linkProperties;
        synchronized (nai) {
            linkProperties = new LinkProperties(nai.linkProperties);
        }
        return linkProperties;
    }

    public LinkProperties getLinkProperties(Network network) {
        enforceAccessPermission();
        NetworkAgentInfo nai = getNetworkAgentInfoForNetwork(network);
        if (nai == null) {
            return null;
        }
        LinkProperties linkProperties;
        synchronized (nai) {
            linkProperties = new LinkProperties(nai.linkProperties);
        }
        return linkProperties;
    }

    private NetworkCapabilities getNetworkCapabilitiesInternal(NetworkAgentInfo nai) {
        if (nai != null) {
            synchronized (nai) {
                if (nai.networkCapabilities != null) {
                    NetworkCapabilities networkCapabilities = new NetworkCapabilities(nai.networkCapabilities);
                    return networkCapabilities;
                }
            }
        }
        return null;
    }

    public NetworkCapabilities getNetworkCapabilities(Network network) {
        enforceAccessPermission();
        return getNetworkCapabilitiesInternal(getNetworkAgentInfoForNetwork(network));
    }

    public NetworkState[] getAllNetworkState() {
        enforceConnectivityInternalPermission();
        ArrayList<NetworkState> result = Lists.newArrayList();
        for (Network network : getAllNetworks()) {
            NetworkAgentInfo nai = getNetworkAgentInfoForNetwork(network);
            if (nai != null) {
                result.add(nai.getNetworkState());
            }
        }
        return (NetworkState[]) result.toArray(new NetworkState[result.size()]);
    }

    public NetworkQuotaInfo getActiveNetworkQuotaInfo() {
        enforceAccessPermission();
        int uid = Binder.getCallingUid();
        long token = Binder.clearCallingIdentity();
        try {
            NetworkState state = getUnfilteredActiveNetworkState(uid);
            if (state.networkInfo != null) {
                try {
                    NetworkQuotaInfo networkQuotaInfo = this.mPolicyManager.getNetworkQuotaInfo(state);
                    return networkQuotaInfo;
                } catch (RemoteException e) {
                }
            }
            Binder.restoreCallingIdentity(token);
            return null;
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    public boolean isActiveNetworkMetered() {
        enforceAccessPermission();
        NetworkInfo info = getActiveNetworkInfo();
        return info != null ? info.isMetered() : false;
    }

    public boolean requestRouteToHostAddress(int networkType, byte[] hostAddress) {
        enforceChangePermission();
        if (this.mProtectedNetworks.contains(Integer.valueOf(networkType))) {
            enforceConnectivityInternalPermission();
        }
        try {
            InetAddress addr = InetAddress.getByAddress(hostAddress);
            if (ConnectivityManager.isNetworkTypeValid(networkType)) {
                NetworkAgentInfo nai = this.mLegacyTypeTracker.getNetworkForType(networkType);
                if (nai == null) {
                    if (this.mLegacyTypeTracker.isTypeSupported(networkType)) {
                        log("requestRouteToHostAddress on down network: " + networkType);
                    } else {
                        log("requestRouteToHostAddress on unsupported network: " + networkType);
                    }
                    return false;
                }
                DetailedState netState;
                synchronized (nai) {
                    netState = nai.networkInfo.getDetailedState();
                }
                if (netState == DetailedState.CONNECTED || netState == DetailedState.CAPTIVE_PORTAL_CHECK) {
                    int uid = Binder.getCallingUid();
                    long token = Binder.clearCallingIdentity();
                    try {
                        LinkProperties lp;
                        int netId;
                        synchronized (nai) {
                            lp = nai.linkProperties;
                            netId = nai.network.netId;
                        }
                        boolean ok = addLegacyRouteToHost(lp, addr, netId, uid);
                        log("requestRouteToHostAddress ok=" + ok);
                        Binder.restoreCallingIdentity(token);
                        return ok;
                    } catch (Throwable th) {
                        Binder.restoreCallingIdentity(token);
                    }
                } else {
                    log("requestRouteToHostAddress on down network (" + networkType + ") - dropped" + " netState=" + netState);
                    return false;
                }
            }
            log("requestRouteToHostAddress on invalid network: " + networkType);
            return false;
        } catch (UnknownHostException e) {
            log("requestRouteToHostAddress got " + e.toString());
            return false;
        }
    }

    private boolean addLegacyRouteToHost(LinkProperties lp, InetAddress addr, int netId, int uid) {
        RouteInfo bestRoute = RouteInfo.selectBestRoute(lp.getAllRoutes(), addr);
        if (bestRoute == null) {
            bestRoute = RouteInfo.makeHostRoute(addr, lp.getInterfaceName());
        } else {
            String iface = bestRoute.getInterface();
            if (bestRoute.getGateway().equals(addr)) {
                bestRoute = RouteInfo.makeHostRoute(addr, iface);
            } else {
                bestRoute = RouteInfo.makeHostRoute(addr, bestRoute.getGateway(), iface);
            }
        }
        if (!(this.mBestLegacyRoute == null || this.mBestLegacyRoute.getDestination() == null || bestRoute == null || bestRoute.getDestination() == null || this.mBestLegacyRoute.getDestination().getAddress() == null || !this.mBestLegacyRoute.getDestination().getAddress().equals(bestRoute.getDestination().getAddress()))) {
            removeLegacyRouteToHost(this.mLegacyRouteNetId, this.mBestLegacyRoute, this.mLegacyRouteUid);
            log("removing " + this.mBestLegacyRoute + " for interface " + this.mBestLegacyRoute.getInterface() + " mLegacyRouteNetId " + this.mLegacyRouteNetId + " mLegacyRouteUid " + this.mLegacyRouteUid);
        }
        if (bestRoute != null) {
            log("Adding " + bestRoute + " for interface " + bestRoute.getInterface() + " netId " + netId + " uid " + uid);
        }
        try {
            this.mNetd.addLegacyRouteForNetId(netId, bestRoute, uid);
            this.mBestLegacyRoute = bestRoute;
            this.mLegacyRouteNetId = netId;
            this.mLegacyRouteUid = uid;
            return true;
        } catch (Exception e) {
            loge("Exception trying to add a route: " + e);
            return false;
        }
    }

    private void removeLegacyRouteToHost(int netId, RouteInfo bestRoute, int uid) {
        Parcel _data = Parcel.obtain();
        Parcel _reply = Parcel.obtain();
        IBinder b = ServiceManager.getService("network_management");
        log("removeLegacyRouteToHost");
        if (b != null) {
            try {
                _data.writeInterfaceToken(DESCRIPTOR);
                _data.writeInt(netId);
                bestRoute.writeToParcel(_data, 0);
                _data.writeInt(uid);
                b.transact(CODE_REMOVE_LEGACYROUTE_TO_HOST, _data, _reply, 0);
                _reply.readException();
            } catch (RemoteException localRemoteException) {
                localRemoteException.printStackTrace();
                return;
            } catch (Exception e) {
                loge("Exception trying to remove a route: " + e);
                return;
            } finally {
                _reply.recycle();
                _data.recycle();
            }
        }
        _reply.recycle();
        _data.recycle();
    }

    private void enforceCrossUserPermission(int userId) {
        if (userId != UserHandle.getCallingUserId()) {
            this.mContext.enforceCallingOrSelfPermission("android.permission.INTERACT_ACROSS_USERS_FULL", TAG);
        }
    }

    private void enforceInternetPermission() {
        this.mContext.enforceCallingOrSelfPermission("android.permission.INTERNET", TAG);
    }

    protected void enforceAccessPermission() {
        this.mContext.enforceCallingOrSelfPermission("android.permission.ACCESS_NETWORK_STATE", TAG);
    }

    protected void enforceChangePermission() {
        ConnectivityManager.enforceChangePermission(this.mContext);
    }

    private void enforceTetherAccessPermission() {
        this.mContext.enforceCallingOrSelfPermission("android.permission.ACCESS_NETWORK_STATE", TAG);
    }

    private void enforceConnectivityInternalPermission() {
        this.mContext.enforceCallingOrSelfPermission("android.permission.CONNECTIVITY_INTERNAL", TAG);
    }

    private void enforceKeepalivePermission() {
        this.mContext.enforceCallingOrSelfPermission(KeepaliveTracker.PERMISSION, TAG);
    }

    public void sendConnectedBroadcast(NetworkInfo info) {
        enforceConnectivityInternalPermission();
        sendGeneralBroadcast(info, "android.net.conn.CONNECTIVITY_CHANGE");
    }

    private void sendInetConditionBroadcast(NetworkInfo info) {
        sendGeneralBroadcast(info, "android.net.conn.INET_CONDITION_ACTION");
    }

    private Intent makeGeneralIntent(NetworkInfo info, String bcastType) {
        if (this.mLockdownTracker != null) {
            NetworkInfo info2 = new NetworkInfo(info);
            this.mLockdownTracker.augmentNetworkInfo(info2);
            info = info2;
        }
        Intent intent = new Intent(bcastType);
        intent.putExtra("networkInfo", new NetworkInfo(info));
        intent.putExtra("networkType", info.getType());
        if (info.isFailover()) {
            intent.putExtra("isFailover", true);
            info.setFailover(false);
        }
        if (info.getReason() != null) {
            intent.putExtra(PhoneWindowManager.SYSTEM_DIALOG_REASON_KEY, info.getReason());
        }
        if (info.getExtraInfo() != null) {
            intent.putExtra("extraInfo", info.getExtraInfo());
        }
        intent.putExtra("inetCondition", this.mDefaultInetConditionPublished);
        return intent;
    }

    private void sendGeneralBroadcast(NetworkInfo info, String bcastType) {
        sendStickyBroadcast(makeGeneralIntent(info, bcastType));
    }

    private void sendDataActivityBroadcast(int deviceType, boolean active, long tsNanos) {
        Intent intent = new Intent("android.net.conn.DATA_ACTIVITY_CHANGE");
        intent.putExtra("deviceType", deviceType);
        intent.putExtra("isActive", active);
        intent.putExtra("tsNanos", tsNanos);
        long ident = Binder.clearCallingIdentity();
        try {
            this.mContext.sendOrderedBroadcastAsUser(intent, UserHandle.ALL, "android.permission.RECEIVE_DATA_ACTIVITY_CHANGE", null, null, 0, null, null);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    protected void sendStickyBroadcast(Intent intent) {
        synchronized (this) {
            if (!this.mSystemReady) {
                this.mInitialBroadcast = new Intent(intent);
            }
            intent.addFlags(67108864);
            log("sendStickyBroadcast: action=" + intent.getAction());
            Bundle bundle = null;
            long ident = Binder.clearCallingIdentity();
            if ("android.net.conn.CONNECTIVITY_CHANGE".equals(intent.getAction())) {
                NetworkInfo ni = (NetworkInfo) intent.getParcelableExtra("networkInfo");
                if (ni.getType() == 3) {
                    intent.setAction("android.net.conn.CONNECTIVITY_CHANGE_SUPL");
                    intent.addFlags(1073741824);
                } else {
                    BroadcastOptions opts = BroadcastOptions.makeBasic();
                    opts.setMaxManifestReceiverApiLevel(23);
                    bundle = opts.toBundle();
                }
                try {
                    BatteryStatsService.getService().noteConnectivityChanged(intent.getIntExtra("networkType", -1), ni != null ? ni.getState().toString() : "?");
                } catch (RemoteException e) {
                }
            }
            try {
                this.mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL, bundle);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
    }

    void systemReady() {
        loadGlobalProxy();
        synchronized (this) {
            this.mSystemReady = true;
            if (this.mInitialBroadcast != null) {
                this.mContext.sendStickyBroadcastAsUser(this.mInitialBroadcast, UserHandle.ALL);
                this.mInitialBroadcast = null;
            }
        }
        this.mHandler.sendMessage(this.mHandler.obtainMessage(9));
        updateLockdownVpn();
        this.mHandler.sendMessage(this.mHandler.obtainMessage(30));
        this.mHandler.sendMessage(this.mHandler.obtainMessage(25));
        this.mPermissionMonitor.startMonitoring();
    }

    private void setupDataActivityTracking(NetworkAgentInfo networkAgent) {
        int timeout;
        String iface = networkAgent.linkProperties.getInterfaceName();
        int type = -1;
        if (networkAgent.networkCapabilities.hasTransport(0)) {
            timeout = Global.getInt(this.mContext.getContentResolver(), "data_activity_timeout_mobile", 10);
            type = 0;
        } else if (networkAgent.networkCapabilities.hasTransport(1)) {
            timeout = Global.getInt(this.mContext.getContentResolver(), "data_activity_timeout_wifi", 15);
            type = 1;
        } else {
            timeout = 0;
        }
        if (timeout > 0 && iface != null && type != -1) {
            try {
                this.mNetd.addIdleTimer(iface, timeout, type);
            } catch (Exception e) {
                loge("Exception in setupDataActivityTracking " + e);
            }
        }
    }

    private void removeDataActivityTracking(NetworkAgentInfo networkAgent) {
        String iface = networkAgent.linkProperties.getInterfaceName();
        NetworkCapabilities caps = networkAgent.networkCapabilities;
        if (iface == null) {
            return;
        }
        if (caps.hasTransport(0) || caps.hasTransport(1)) {
            try {
                this.mNetd.removeIdleTimer(iface);
            } catch (Exception e) {
                loge("Exception in removeDataActivityTracking " + e);
            }
        }
    }

    private void updateMtu(LinkProperties newLp, LinkProperties oldLp) {
        String iface = newLp.getInterfaceName();
        int mtu = newLp.getMtu();
        if (oldLp != null && newLp.isIdenticalMtu(oldLp)) {
            log("identical MTU - not setting");
        } else if (!LinkProperties.isValidMtu(mtu, newLp.hasGlobalIPv6Address())) {
            if (mtu != 0) {
                loge("Unexpected mtu value: " + mtu + ", " + iface);
            }
        } else if (TextUtils.isEmpty(iface)) {
            loge("Setting MTU size with null iface.");
        } else {
            try {
                log("Setting MTU size: " + iface + ", " + mtu);
                this.mNetd.setMtu(iface, mtu);
            } catch (Exception e) {
                Slog.e(TAG, "exception in setMtu()" + e);
            }
        }
    }

    protected int getDefaultTcpRwnd() {
        return SystemProperties.getInt(DEFAULT_TCP_RWND_KEY, 0);
    }

    private void updateTcpBufferSizes(NetworkAgentInfo nai) {
        if (isDefaultNetwork(nai)) {
            String tcpBufferSizes = nai.linkProperties.getTcpBufferSizes();
            String[] values = null;
            if (tcpBufferSizes != null) {
                values = tcpBufferSizes.split(",");
            }
            if (values == null || values.length != 6) {
                log("Invalid tcpBufferSizes string: " + tcpBufferSizes + ", using defaults");
                tcpBufferSizes = DEFAULT_TCP_BUFFER_SIZES;
                values = tcpBufferSizes.split(",");
            }
            if (!tcpBufferSizes.equals(this.mCurrentTcpBufferSizes)) {
                try {
                    Slog.d(TAG, "Setting tx/rx TCP buffers to " + tcpBufferSizes);
                    String prefix = "/sys/kernel/ipv4/tcp_";
                    FileUtils.stringToFile("/sys/kernel/ipv4/tcp_rmem_min", values[0]);
                    FileUtils.stringToFile("/sys/kernel/ipv4/tcp_rmem_def", values[1]);
                    FileUtils.stringToFile("/sys/kernel/ipv4/tcp_rmem_max", values[2]);
                    FileUtils.stringToFile("/sys/kernel/ipv4/tcp_wmem_min", values[3]);
                    FileUtils.stringToFile("/sys/kernel/ipv4/tcp_wmem_def", values[4]);
                    FileUtils.stringToFile("/sys/kernel/ipv4/tcp_wmem_max", values[5]);
                    this.mCurrentTcpBufferSizes = tcpBufferSizes;
                } catch (IOException e) {
                    loge("Can't set TCP buffer sizes:" + e);
                }
                Integer rwndValue = Integer.valueOf(Global.getInt(this.mContext.getContentResolver(), "tcp_default_init_rwnd", getDefaultTcpRwnd()));
                String sysctlKey = "sys.sysctl.tcp_def_init_rwnd";
                if (rwndValue.intValue() != 0) {
                    SystemProperties.set("sys.sysctl.tcp_def_init_rwnd", rwndValue.toString());
                }
            }
        }
    }

    private void flushVmDnsCache() {
        Intent intent = new Intent("android.intent.action.CLEAR_DNS_CACHE");
        intent.addFlags(536870912);
        intent.addFlags(67108864);
        long ident = Binder.clearCallingIdentity();
        try {
            this.mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    public int getRestoreDefaultNetworkDelay(int networkType) {
        String restoreDefaultNetworkDelayStr = SystemProperties.get(NETWORK_RESTORE_DELAY_PROP_NAME);
        if (!(restoreDefaultNetworkDelayStr == null || restoreDefaultNetworkDelayStr.length() == 0)) {
            try {
                return Integer.parseInt(restoreDefaultNetworkDelayStr);
            } catch (NumberFormatException e) {
            }
        }
        int ret = RESTORE_DEFAULT_NETWORK_DELAY;
        if (networkType <= 45 && this.mNetConfigs[networkType] != null) {
            ret = this.mNetConfigs[networkType].restoreTime;
        }
        return ret;
    }

    private boolean argsContain(String[] args, String target) {
        for (String arg : args) {
            if (arg.equals(target)) {
                return true;
            }
        }
        return false;
    }

    private void dumpNetworkDiagnostics(IndentingPrintWriter pw) {
        List<NetworkDiagnostics> netDiags = new ArrayList();
        for (NetworkAgentInfo nai : this.mNetworkAgentInfos.values()) {
            netDiags.add(new NetworkDiagnostics(nai.network, new LinkProperties(nai.linkProperties), 5000));
        }
        for (NetworkDiagnostics netDiag : netDiags) {
            pw.println();
            netDiag.waitForMeasurements();
            netDiag.dump(pw);
        }
    }

    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        IndentingPrintWriter pw = new IndentingPrintWriter(writer, "  ");
        if (this.mContext.checkCallingOrSelfPermission("android.permission.DUMP") != 0) {
            pw.println("Permission Denial: can't dump ConnectivityService from from pid=" + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid());
        } else if (argsContain(args, "--diag")) {
            dumpNetworkDiagnostics(pw);
        } else {
            int i;
            pw.print("NetworkFactories for:");
            for (NetworkFactoryInfo nfi : this.mNetworkFactoryInfos.values()) {
                pw.print(" " + nfi.name);
            }
            pw.println();
            pw.println();
            NetworkAgentInfo defaultNai = getDefaultNetwork();
            pw.print("Active default network: ");
            if (defaultNai == null) {
                pw.println("none");
            } else {
                pw.println(defaultNai.network.netId);
            }
            pw.println();
            pw.println("Current Networks:");
            pw.increaseIndent();
            for (NetworkAgentInfo nai : this.mNetworkAgentInfos.values()) {
                pw.println(nai.toString());
                pw.increaseIndent();
                pw.println("Requests:");
                pw.increaseIndent();
                for (i = 0; i < nai.networkRequests.size(); i++) {
                    pw.println(((NetworkRequest) nai.networkRequests.valueAt(i)).toString());
                }
                pw.decreaseIndent();
                pw.println("Lingered:");
                pw.increaseIndent();
                for (NetworkRequest nr : nai.networkLingered) {
                    pw.println(nr.toString());
                }
                pw.decreaseIndent();
                pw.decreaseIndent();
            }
            pw.decreaseIndent();
            pw.println();
            pw.println("Metered Interfaces:");
            pw.increaseIndent();
            for (String value : this.mMeteredIfaces) {
                pw.println(value);
            }
            pw.decreaseIndent();
            pw.println();
            pw.print("Restrict background: ");
            pw.println(this.mRestrictBackground);
            pw.println();
            pw.println("Status for known UIDs:");
            pw.increaseIndent();
            int size = this.mUidRules.size();
            for (i = 0; i < size; i++) {
                int uid = this.mUidRules.keyAt(i);
                pw.print("UID=");
                pw.print(uid);
                int uidRules = this.mUidRules.get(uid, 0);
                pw.print(" rules=");
                pw.print(NetworkPolicyManager.uidRulesToString(uidRules));
                pw.println();
            }
            pw.println();
            pw.decreaseIndent();
            pw.println("Network Requests:");
            pw.increaseIndent();
            for (NetworkRequestInfo nri : this.mNetworkRequests.values()) {
                pw.println(nri.toString());
            }
            pw.println();
            pw.decreaseIndent();
            this.mLegacyTypeTracker.dump(pw);
            synchronized (this) {
                pw.print("mNetTransitionWakeLock: currently " + (this.mNetTransitionWakeLock.isHeld() ? "" : "not ") + "held");
                if (TextUtils.isEmpty(this.mNetTransitionWakeLockCausedBy)) {
                    pw.println(", last requested never");
                } else {
                    pw.println(", last requested for " + this.mNetTransitionWakeLockCausedBy);
                }
            }
            pw.println();
            this.mTethering.dump(fd, pw, args);
            pw.println();
            this.mKeepaliveTracker.dump(pw);
            pw.println();
            if (this.mInetLog != null && this.mInetLog.size() > 0) {
                pw.println();
                pw.println("Inet condition reports:");
                pw.increaseIndent();
                for (i = 0; i < this.mInetLog.size(); i++) {
                    pw.println(this.mInetLog.get(i));
                }
                pw.decreaseIndent();
            }
            if (!argsContain(args, "--short")) {
                pw.println();
                synchronized (this.mValidationLogs) {
                    pw.println("mValidationLogs (most recent first):");
                    for (ValidationLog p : this.mValidationLogs) {
                        pw.println(p.mNetwork + " - " + p.mNetworkExtraInfo);
                        pw.increaseIndent();
                        p.mLog.dump(fd, pw, args);
                        pw.decreaseIndent();
                    }
                }
                pw.println();
                pw.println("mNetworkRequestInfoLogs (most recent first):");
                pw.increaseIndent();
                this.mNetworkRequestInfoLogs.reverseDump(fd, pw, args);
                pw.decreaseIndent();
            }
        }
    }

    private boolean isLiveNetworkAgent(NetworkAgentInfo nai, int what) {
        if (nai.network == null) {
            return false;
        }
        NetworkAgentInfo officialNai = getNetworkAgentInfoForNetwork(nai.network);
        if (officialNai != null && officialNai.equals(nai)) {
            return true;
        }
        if (officialNai == null) {
            loge(((String) sMagicDecoderRing.get(what, Integer.toString(what))) + " - isLiveNetworkAgent found mismatched netId: " + officialNai + " - " + nai);
        } else {
            loge(((String) sMagicDecoderRing.get(what, Integer.toString(what))) + " - isLiveNetworkAgent found mismatched netId: " + officialNai + " - " + nai);
        }
        return false;
    }

    private boolean isRequest(NetworkRequest request) {
        return ((NetworkRequestInfo) this.mNetworkRequests.get(request)).isRequest();
    }

    private void linger(NetworkAgentInfo nai) {
        nai.lingering = true;
        NetworkEvent.logEvent(nai.network.netId, 5);
        nai.networkMonitor.sendMessage(NetworkMonitor.CMD_NETWORK_LINGER);
        notifyNetworkCallbacks(nai, 524291);
    }

    private void unlinger(NetworkAgentInfo nai) {
        nai.networkLingered.clear();
        if (nai.lingering) {
            nai.lingering = false;
            NetworkEvent.logEvent(nai.network.netId, 6);
            log("Canceling linger of " + nai.name());
            nai.networkMonitor.sendMessage(NetworkMonitor.CMD_NETWORK_CONNECTED);
        }
    }

    private void handleAsyncChannelHalfConnect(Message msg) {
        AsyncChannel ac = msg.obj;
        NetworkAgentInfo nai;
        if (this.mNetworkFactoryInfos.containsKey(msg.replyTo)) {
            if (msg.arg1 == 0) {
                log("NetworkFactory connected");
                for (NetworkRequestInfo nri : this.mNetworkRequests.values()) {
                    if (nri.isRequest()) {
                        int currentScore;
                        nai = (NetworkAgentInfo) this.mNetworkForRequestId.get(nri.request.requestId);
                        if (nai != null) {
                            currentScore = nai.getCurrentScore();
                        } else {
                            currentScore = 0;
                        }
                        ac.sendMessage(536576, currentScore, 0, nri.request);
                    }
                }
                return;
            }
            loge("Error connecting NetworkFactory");
            this.mNetworkFactoryInfos.remove(msg.obj);
        } else if (!this.mNetworkAgentInfos.containsKey(msg.replyTo)) {
        } else {
            if (msg.arg1 == 0) {
                log("NetworkAgent connected");
                ((NetworkAgentInfo) this.mNetworkAgentInfos.get(msg.replyTo)).asyncChannel.sendMessage(69633);
                return;
            }
            loge("Error connecting NetworkAgent");
            nai = (NetworkAgentInfo) this.mNetworkAgentInfos.remove(msg.replyTo);
            if (nai != null) {
                boolean wasDefault = isDefaultNetwork(nai);
                synchronized (this.mNetworkForNetId) {
                    this.mNetworkForNetId.remove(nai.network.netId);
                    this.mNetIdInUse.delete(nai.network.netId);
                }
                this.mLegacyTypeTracker.remove(nai, wasDefault);
            }
        }
    }

    private void handleAsyncChannelDisconnected(Message msg) {
        NetworkAgentInfo nai = (NetworkAgentInfo) this.mNetworkAgentInfos.get(msg.replyTo);
        if (nai != null) {
            log(nai.name() + " got DISCONNECTED, was satisfying " + nai.networkRequests.size());
            if (nai.networkInfo.isConnected()) {
                nai.networkInfo.setDetailedState(DetailedState.DISCONNECTED, null, null);
            }
            boolean wasDefault = isDefaultNetwork(nai);
            if (wasDefault) {
                this.mDefaultInetConditionPublished = 0;
                logDefaultNetworkEvent(null, nai);
            }
            notifyIfacesChangedForNetworkStats();
            notifyNetworkCallbacks(nai, 524292);
            this.mKeepaliveTracker.handleStopAllKeepalives(nai, -20);
            nai.networkMonitor.sendMessage(NetworkMonitor.CMD_NETWORK_DISCONNECTED);
            this.mNetworkAgentInfos.remove(msg.replyTo);
            updateClat(null, nai.linkProperties, nai);
            synchronized (this.mNetworkForNetId) {
                this.mNetworkForNetId.remove(nai.network.netId);
            }
            for (int i = 0; i < nai.networkRequests.size(); i++) {
                NetworkRequest request = (NetworkRequest) nai.networkRequests.valueAt(i);
                NetworkAgentInfo currentNetwork = (NetworkAgentInfo) this.mNetworkForRequestId.get(request.requestId);
                if (currentNetwork != null && currentNetwork.network.netId == nai.network.netId) {
                    this.mNetworkForRequestId.remove(request.requestId);
                    sendUpdatedScoreToFactories(request, 0);
                }
            }
            if (nai.networkRequests.get(this.mDefaultRequest.requestId) != null) {
                removeDataActivityTracking(nai);
                notifyLockdownVpn(nai);
                requestNetworkTransitionWakelock(nai.name());
            }
            this.mLegacyTypeTracker.remove(nai, wasDefault);
            rematchAllNetworksAndRequests(null, 0);
            if (nai.created) {
                try {
                    this.mNetd.removeNetwork(nai.network.netId);
                } catch (Exception e) {
                    loge("Exception removing network: " + e);
                }
            }
            synchronized (this.mNetworkForNetId) {
                this.mNetIdInUse.delete(nai.network.netId);
            }
            return;
        }
        NetworkFactoryInfo nfi = (NetworkFactoryInfo) this.mNetworkFactoryInfos.remove(msg.replyTo);
        if (nfi != null) {
            log("unregisterNetworkFactory for " + nfi.name);
        }
    }

    private NetworkRequestInfo findExistingNetworkRequestInfo(PendingIntent pendingIntent) {
        Intent intent = pendingIntent.getIntent();
        for (Entry<NetworkRequest, NetworkRequestInfo> entry : this.mNetworkRequests.entrySet()) {
            PendingIntent existingPendingIntent = ((NetworkRequestInfo) entry.getValue()).mPendingIntent;
            if (existingPendingIntent != null && existingPendingIntent.getIntent().filterEquals(intent)) {
                return (NetworkRequestInfo) entry.getValue();
            }
        }
        return null;
    }

    private void handleRegisterNetworkRequestWithIntent(Message msg) {
        NetworkRequestInfo nri = msg.obj;
        NetworkRequestInfo existingRequest = findExistingNetworkRequestInfo(nri.mPendingIntent);
        if (existingRequest != null) {
            log("Replacing " + existingRequest.request + " with " + nri.request + " because their intents matched.");
            handleReleaseNetworkRequest(existingRequest.request, getCallingUid());
        }
        handleRegisterNetworkRequest(nri);
    }

    private void handleRegisterNetworkRequest(NetworkRequestInfo nri) {
        this.mNetworkRequests.put(nri.request, nri);
        this.mNetworkRequestInfoLogs.log("REGISTER " + nri);
        if (!nri.isRequest()) {
            for (NetworkAgentInfo network : this.mNetworkAgentInfos.values()) {
                if (nri.request.networkCapabilities.hasSignalStrength() && network.satisfiesImmutableCapabilitiesOf(nri.request)) {
                    updateSignalStrengthThresholds(network, "REGISTER", nri.request);
                }
            }
        }
        rematchAllNetworksAndRequests(null, 0);
        if (!nri.isRequest() || this.mNetworkForRequestId.get(nri.request.requestId) != null) {
            return;
        }
        if (!SystemProperties.getBoolean("ro.config.hw_vowifi", false)) {
            sendUpdatedScoreToFactories(nri.request, 0);
        } else if (ifNeedToStartLteMmsTimer(nri.request)) {
            loge("need to start LteMmsTimer ,hold handleRegisterNetworkRequest ");
        } else {
            sendUpdatedScoreToFactories(nri.request, 0);
        }
    }

    private void handleReleaseNetworkRequestWithIntent(PendingIntent pendingIntent, int callingUid) {
        NetworkRequestInfo nri = findExistingNetworkRequestInfo(pendingIntent);
        if (nri != null) {
            handleReleaseNetworkRequest(nri.request, callingUid);
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private boolean unneeded(NetworkAgentInfo nai) {
        if (!nai.everConnected || nai.isVPN() || nai.lingering || ignoreRemovedByWifiPro(nai)) {
            return false;
        }
        boolean isDisabledMobileNetwork = (nai.networkInfo.getType() != 0 || this.mTelephonyManager.getDataEnabled()) ? false : !HwTelephonyManager.getDefault().isVSimEnabled();
        for (NetworkRequestInfo nri : this.mNetworkRequests.values()) {
            NetworkAgentInfo existing = (NetworkAgentInfo) this.mNetworkForRequestId.get(nri.request.requestId);
            if (existing == null) {
                log("can not find existing request info on mNetworkForRequestId, request is " + nri.request);
            }
            boolean checkNetworkSupportBip = !nai.satisfies(nri.request) ? checkNetworkSupportBip(nai, nri.request) : true;
            if (this.mDefaultRequest.requestId == nri.request.requestId && isDisabledMobileNetwork) {
                log("mobile net can't satisfy default network request when mobile data disabled");
                checkNetworkSupportBip = false;
            }
            if (nri.isRequest() && r4) {
                if (nai.networkRequests.get(nri.request.requestId) != null || (existing != null && existing.getCurrentScore() < nai.getCurrentScoreAsValidated())) {
                    return false;
                }
            }
        }
        return true;
    }

    private void handleReleaseNetworkRequest(NetworkRequest request, int callingUid) {
        NetworkRequestInfo nri = (NetworkRequestInfo) this.mNetworkRequests.get(request);
        if (nri != null) {
            if (1000 == callingUid || nri.mUid == callingUid) {
                log("releasing NetworkRequest " + request);
                nri.unlinkDeathRecipient();
                this.mNetworkRequests.remove(request);
                synchronized (this.mUidToNetworkRequestCount) {
                    int requests = this.mUidToNetworkRequestCount.get(nri.mUid, 0);
                    if (requests < 1) {
                        Slog.wtf(TAG, "BUG: too small request count " + requests + " for UID " + nri.mUid);
                    } else if (requests == 1) {
                        this.mUidToNetworkRequestCount.removeAt(this.mUidToNetworkRequestCount.indexOfKey(nri.mUid));
                    } else {
                        this.mUidToNetworkRequestCount.put(nri.mUid, requests - 1);
                    }
                }
                this.mNetworkRequestInfoLogs.log("RELEASE " + nri);
                NetworkAgentInfo nai;
                if (nri.isRequest()) {
                    int wasKept = 0;
                    for (NetworkAgentInfo nai2 : this.mNetworkAgentInfos.values()) {
                        if (nai2.networkRequests.get(nri.request.requestId) != null) {
                            nai2.networkRequests.remove(nri.request.requestId);
                            log(" Removing from current network " + nai2.name() + ", leaving " + nai2.networkRequests.size() + " requests.");
                            if (unneeded(nai2)) {
                                log("no live requests for " + nai2.name() + "; disconnecting");
                                teardownUnneededNetwork(nai2);
                            } else {
                                wasKept |= 1;
                            }
                        }
                    }
                    nai2 = (NetworkAgentInfo) this.mNetworkForRequestId.get(nri.request.requestId);
                    if (nai2 != null) {
                        this.mNetworkForRequestId.remove(nri.request.requestId);
                    }
                    if (!(nri.request.legacyType == -1 || nai2 == null)) {
                        boolean doRemove = true;
                        if (wasKept != 0) {
                            for (int i = 0; i < nai2.networkRequests.size(); i++) {
                                NetworkRequest otherRequest = (NetworkRequest) nai2.networkRequests.valueAt(i);
                                if (otherRequest.legacyType == nri.request.legacyType && isRequest(otherRequest)) {
                                    log(" still have other legacy request - leaving");
                                    doRemove = false;
                                }
                            }
                        }
                        if (doRemove) {
                            this.mLegacyTypeTracker.remove(nri.request.legacyType, nai2, false);
                        }
                    }
                    for (NetworkFactoryInfo nfi : this.mNetworkFactoryInfos.values()) {
                        nfi.asyncChannel.sendMessage(536577, nri.request);
                    }
                } else {
                    for (NetworkAgentInfo nai22 : this.mNetworkAgentInfos.values()) {
                        nai22.networkRequests.remove(nri.request.requestId);
                        if (nri.request.networkCapabilities.hasSignalStrength() && nai22.satisfiesImmutableCapabilitiesOf(nri.request)) {
                            updateSignalStrengthThresholds(nai22, "RELEASE", nri.request);
                        }
                    }
                }
                callCallbackForRequest(nri, null, 524296);
            } else {
                log("Attempt to release unowned NetworkRequest " + request);
            }
        }
    }

    public void setAcceptUnvalidated(Network network, boolean accept, boolean always) {
        int i;
        int i2 = 1;
        enforceConnectivityInternalPermission();
        InternalHandler internalHandler = this.mHandler;
        InternalHandler internalHandler2 = this.mHandler;
        if (accept) {
            i = 1;
        } else {
            i = 0;
        }
        if (!always) {
            i2 = 0;
        }
        internalHandler.sendMessage(internalHandler2.obtainMessage(28, i, i2, network));
    }

    private void handleSetAcceptUnvalidated(Network network, boolean accept, boolean always) {
        log("handleSetAcceptUnvalidated network=" + network + " accept=" + accept + " always=" + always);
        NetworkAgentInfo nai = getNetworkAgentInfoForNetwork(network);
        if (nai != null && !nai.everValidated) {
            if (!nai.networkMisc.explicitlySelected) {
                Slog.wtf(TAG, "BUG: setAcceptUnvalidated non non-explicitly selected network");
            }
            if (accept != nai.networkMisc.acceptUnvalidated) {
                int oldScore = nai.getCurrentScore();
                nai.networkMisc.acceptUnvalidated = accept;
                rematchAllNetworksAndRequests(nai, oldScore);
                sendUpdatedScoreToFactories(nai);
            }
            if (always) {
                nai.asyncChannel.sendMessage(528393, accept ? 1 : 0);
            }
            if (!accept) {
                nai.asyncChannel.sendMessage(528399);
                teardownUnneededNetwork(nai);
            }
        }
    }

    private void scheduleUnvalidatedPrompt(NetworkAgentInfo nai) {
        log("scheduleUnvalidatedPrompt " + nai.network);
        this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(29, nai.network), 8000);
    }

    private void handlePromptUnvalidated(Network network) {
        log("handlePromptUnvalidated " + network);
        NetworkAgentInfo nai = getNetworkAgentInfoForNetwork(network);
        if (nai != null && !nai.everValidated && !nai.everCaptivePortalDetected && nai.networkMisc.explicitlySelected && !nai.networkMisc.acceptUnvalidated) {
            Intent intent = new Intent("android.net.conn.PROMPT_UNVALIDATED");
            intent.setData(Uri.fromParts("netId", Integer.toString(network.netId), null));
            intent.addFlags(268435456);
            intent.setClassName("com.android.settings", "com.android.settings.wifi.WifiNoInternetDialog");
            boolean z = true;
            setProvNotificationVisibleIntent(z, nai.network.netId, NotificationType.NO_INTERNET, nai.networkInfo.getType(), nai.networkInfo.getExtraInfo(), PendingIntent.getActivityAsUser(this.mContext, 0, intent, 268435456, null, UserHandle.CURRENT), true);
        }
    }

    public int tether(String iface) {
        Log.d(TAG, "tether: ENTER");
        ConnectivityManager.enforceTetherChangePermission(this.mContext);
        if (!isTetheringSupported()) {
            return 3;
        }
        int status = this.mTethering.tether(iface);
        if (status == 0) {
            Log.d(TAG, "tether() return with ConnectivityManager.TETHER_ERROR_NO_ERROR");
            try {
                this.mPolicyManager.onTetheringChanged(iface, true);
            } catch (RemoteException e) {
            }
        } else {
            Log.w(TAG, "tether() return with status " + status);
        }
        return status;
    }

    public int untether(String iface) {
        ConnectivityManager.enforceTetherChangePermission(this.mContext);
        if (!isTetheringSupported()) {
            return 3;
        }
        int status = this.mTethering.untether(iface);
        if (status == 0) {
            try {
                this.mPolicyManager.onTetheringChanged(iface, false);
            } catch (RemoteException e) {
            }
        }
        return status;
    }

    public int getLastTetherError(String iface) {
        enforceTetherAccessPermission();
        if (isTetheringSupported()) {
            return this.mTethering.getLastTetherError(iface);
        }
        return 3;
    }

    public String[] getTetherableUsbRegexs() {
        enforceTetherAccessPermission();
        if (isTetheringSupported()) {
            return this.mTethering.getTetherableUsbRegexs();
        }
        return new String[0];
    }

    public String[] getTetherableWifiRegexs() {
        enforceTetherAccessPermission();
        if (isTetheringSupported()) {
            return this.mTethering.getTetherableWifiRegexs();
        }
        return new String[0];
    }

    public String[] getTetherableBluetoothRegexs() {
        enforceTetherAccessPermission();
        if (isTetheringSupported()) {
            return this.mTethering.getTetherableBluetoothRegexs();
        }
        return new String[0];
    }

    public int setUsbTethering(boolean enable) {
        ConnectivityManager.enforceTetherChangePermission(this.mContext);
        if (isTetheringSupported()) {
            return this.mTethering.setUsbTethering(enable);
        }
        return 3;
    }

    public String[] getTetherableIfaces() {
        enforceTetherAccessPermission();
        return this.mTethering.getTetherableIfaces();
    }

    public String[] getTetheredIfaces() {
        enforceTetherAccessPermission();
        return this.mTethering.getTetheredIfaces();
    }

    public String[] getTetheringErroredIfaces() {
        enforceTetherAccessPermission();
        return this.mTethering.getErroredIfaces();
    }

    public String[] getTetheredDhcpRanges() {
        enforceConnectivityInternalPermission();
        return this.mTethering.getTetheredDhcpRanges();
    }

    public boolean isTetheringSupported() {
        boolean z = true;
        enforceTetherAccessPermission();
        boolean tetherEnabledInSettings = Global.getInt(this.mContext.getContentResolver(), "tether_supported", SystemProperties.get("ro.tether.denied").equals("true") ? 0 : 1) != 0 ? !this.mUserManager.hasUserRestriction("no_config_tethering") : false;
        if (!tetherEnabledInSettings || !this.mUserManager.isAdminUser()) {
            return false;
        }
        if (this.mTethering.getTetherableUsbRegexs().length == 0 && this.mTethering.getTetherableWifiRegexs().length == 0) {
            if (this.mTethering.getTetherableBluetoothRegexs().length == 0) {
                return false;
            }
        }
        if (this.mTethering.getUpstreamIfaceTypes().length == 0) {
            z = false;
        }
        return z;
    }

    public void startTethering(int type, ResultReceiver receiver, boolean showProvisioningUi) {
        ConnectivityManager.enforceTetherChangePermission(this.mContext);
        if (isTetheringSupported()) {
            this.mTethering.startTethering(type, receiver, showProvisioningUi);
        } else {
            receiver.send(3, null);
        }
    }

    public void stopTethering(int type) {
        ConnectivityManager.enforceTetherChangePermission(this.mContext);
        this.mTethering.stopTethering(type);
    }

    private void requestNetworkTransitionWakelock(String forWhom) {
        Throwable th;
        synchronized (this) {
            try {
                if (this.mNetTransitionWakeLock.isHeld()) {
                    return;
                }
                int serialNum = this.mNetTransitionWakeLockSerialNumber + 1;
                this.mNetTransitionWakeLockSerialNumber = serialNum;
                try {
                    this.mNetTransitionWakeLock.acquire();
                    this.mNetTransitionWakeLockCausedBy = forWhom;
                    this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(24, serialNum, 0), (long) this.mNetTransitionWakeLockTimeout);
                } catch (Throwable th2) {
                    th = th2;
                    int i = serialNum;
                    throw th;
                }
            } catch (Throwable th3) {
                th = th3;
                throw th;
            }
        }
    }

    public void reportInetCondition(int networkType, int percentage) {
        NetworkAgentInfo nai = this.mLegacyTypeTracker.getNetworkForType(networkType);
        if (nai != null) {
            reportNetworkConnectivity(nai.network, percentage > 50);
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void reportNetworkConnectivity(Network network, boolean hasConnectivity) {
        NetworkAgentInfo nai;
        enforceAccessPermission();
        enforceInternetPermission();
        if (network == null) {
            nai = getDefaultNetwork();
        } else {
            nai = getNetworkAgentInfoForNetwork(network);
        }
        if (nai != null && nai.networkInfo.getState() != State.DISCONNECTING && nai.networkInfo.getState() != State.DISCONNECTED && hasConnectivity != nai.lastValidated) {
            int uid = Binder.getCallingUid();
            log("reportNetworkConnectivity(" + nai.network.netId + ", " + hasConnectivity + ") by " + uid);
            synchronized (nai) {
                if (!nai.everConnected) {
                } else if (isNetworkWithLinkPropertiesBlocked(nai.linkProperties, uid, false)) {
                } else {
                    nai.networkMonitor.sendMessage(NetworkMonitor.CMD_FORCE_REEVALUATION, uid);
                }
            }
        }
    }

    private ProxyInfo getDefaultProxy() {
        ProxyInfo ret;
        synchronized (this.mProxyLock) {
            ret = this.mGlobalProxy;
            if (ret == null && !this.mDefaultProxyDisabled) {
                ret = this.mDefaultProxy;
            }
        }
        return ret;
    }

    public ProxyInfo getProxyForNetwork(Network network) {
        if (network == null) {
            return getDefaultProxy();
        }
        ProxyInfo globalProxy = getGlobalProxy();
        if (globalProxy != null) {
            return globalProxy;
        }
        if (!NetworkUtils.queryUserAccess(Binder.getCallingUid(), network.netId)) {
            return null;
        }
        NetworkAgentInfo nai = getNetworkAgentInfoForNetwork(network);
        if (nai == null) {
            return null;
        }
        synchronized (nai) {
            ProxyInfo proxyInfo = nai.linkProperties.getHttpProxy();
            if (proxyInfo == null) {
                return null;
            }
            ProxyInfo proxyInfo2 = new ProxyInfo(proxyInfo);
            return proxyInfo2;
        }
    }

    private ProxyInfo canonicalizeProxyInfo(ProxyInfo proxy) {
        if (proxy == null || !TextUtils.isEmpty(proxy.getHost())) {
            return proxy;
        }
        if (proxy.getPacFileUrl() == null || Uri.EMPTY.equals(proxy.getPacFileUrl())) {
            return null;
        }
        return proxy;
    }

    private boolean proxyInfoEqual(ProxyInfo a, ProxyInfo b) {
        a = canonicalizeProxyInfo(a);
        b = canonicalizeProxyInfo(b);
        if (Objects.equals(a, b)) {
            return a != null ? Objects.equals(a.getHost(), b.getHost()) : true;
        } else {
            return false;
        }
    }

    public void setGlobalProxy(ProxyInfo proxyProperties) {
        enforceConnectivityInternalPermission();
        synchronized (this.mProxyLock) {
            if (proxyProperties == this.mGlobalProxy) {
            } else if (proxyProperties != null && proxyProperties.equals(this.mGlobalProxy)) {
            } else if (this.mGlobalProxy == null || !this.mGlobalProxy.equals(proxyProperties)) {
                String host = "";
                int port = 0;
                String exclList = "";
                String pacFileUrl = "";
                if (proxyProperties == null || (TextUtils.isEmpty(proxyProperties.getHost()) && Uri.EMPTY.equals(proxyProperties.getPacFileUrl()))) {
                    this.mGlobalProxy = null;
                } else if (proxyProperties.isValid()) {
                    this.mGlobalProxy = new ProxyInfo(proxyProperties);
                    host = this.mGlobalProxy.getHost();
                    port = this.mGlobalProxy.getPort();
                    exclList = this.mGlobalProxy.getExclusionListAsString();
                    if (!Uri.EMPTY.equals(proxyProperties.getPacFileUrl())) {
                        pacFileUrl = proxyProperties.getPacFileUrl().toString();
                    }
                } else {
                    log("Invalid proxy properties, ignoring: " + proxyProperties.toString());
                    return;
                }
                ContentResolver res = this.mContext.getContentResolver();
                long token = Binder.clearCallingIdentity();
                try {
                    Global.putString(res, "global_http_proxy_host", host);
                    Global.putInt(res, "global_http_proxy_port", port);
                    Global.putString(res, "global_http_proxy_exclusion_list", exclList);
                    Global.putString(res, "global_proxy_pac_url", pacFileUrl);
                    if (this.mGlobalProxy == null) {
                        proxyProperties = this.mDefaultProxy;
                    }
                    sendProxyBroadcast(proxyProperties);
                } finally {
                    Binder.restoreCallingIdentity(token);
                }
            }
        }
    }

    private void loadGlobalProxy() {
        ContentResolver res = this.mContext.getContentResolver();
        String host = Global.getString(res, "global_http_proxy_host");
        int port = Global.getInt(res, "global_http_proxy_port", 0);
        String exclList = Global.getString(res, "global_http_proxy_exclusion_list");
        String pacFileUrl = Global.getString(res, "global_proxy_pac_url");
        if (!(TextUtils.isEmpty(host) && TextUtils.isEmpty(pacFileUrl))) {
            ProxyInfo proxyProperties;
            if (TextUtils.isEmpty(pacFileUrl)) {
                proxyProperties = new ProxyInfo(host, port, exclList);
            } else {
                proxyProperties = new ProxyInfo(pacFileUrl);
            }
            if (proxyProperties.isValid()) {
                synchronized (this.mProxyLock) {
                    this.mGlobalProxy = proxyProperties;
                }
            } else {
                log("Invalid proxy properties, ignoring: " + proxyProperties.toString());
            }
        }
    }

    public ProxyInfo getGlobalProxy() {
        ProxyInfo proxyInfo;
        synchronized (this.mProxyLock) {
            proxyInfo = this.mGlobalProxy;
        }
        return proxyInfo;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void handleApplyDefaultProxy(ProxyInfo proxy) {
        if (proxy != null && TextUtils.isEmpty(proxy.getHost()) && Uri.EMPTY.equals(proxy.getPacFileUrl())) {
            proxy = null;
        }
        synchronized (this.mProxyLock) {
            if (this.mDefaultProxy != null && this.mDefaultProxy.equals(proxy)) {
            } else if (this.mDefaultProxy == proxy) {
            } else {
                if (proxy != null) {
                    if (!proxy.isValid()) {
                        log("Invalid proxy properties, ignoring: " + proxy.toString());
                        return;
                    }
                }
                if (!(this.mGlobalProxy == null || proxy == null || Uri.EMPTY.equals(proxy.getPacFileUrl()))) {
                    if (proxy.getPacFileUrl().equals(this.mGlobalProxy.getPacFileUrl())) {
                        this.mGlobalProxy = proxy;
                        sendProxyBroadcast(this.mGlobalProxy);
                        return;
                    }
                }
                this.mDefaultProxy = proxy;
                if (this.mGlobalProxy != null) {
                } else if (!this.mDefaultProxyDisabled) {
                    sendProxyBroadcast(proxy);
                }
            }
        }
    }

    private void updateProxy(LinkProperties newLp, LinkProperties oldLp, NetworkAgentInfo nai) {
        ProxyInfo oldProxyInfo = null;
        ProxyInfo httpProxy = newLp == null ? null : newLp.getHttpProxy();
        if (oldLp != null) {
            oldProxyInfo = oldLp.getHttpProxy();
        }
        if (!proxyInfoEqual(httpProxy, oldProxyInfo)) {
            sendProxyBroadcast(getDefaultProxy());
        }
    }

    private void handleDeprecatedGlobalHttpProxy() {
        String proxy = Global.getString(this.mContext.getContentResolver(), "http_proxy");
        if (!TextUtils.isEmpty(proxy)) {
            String[] data = proxy.split(":");
            if (data.length != 0) {
                String proxyHost = data[0];
                int proxyPort = 8080;
                if (data.length > 1) {
                    try {
                        proxyPort = Integer.parseInt(data[1]);
                    } catch (NumberFormatException e) {
                        return;
                    }
                }
                setGlobalProxy(new ProxyInfo(data[0], proxyPort, ""));
            }
        }
    }

    private void sendProxyBroadcast(ProxyInfo proxy) {
        if (proxy == null) {
            proxy = new ProxyInfo("", 0, "");
        }
        if (!this.mPacManager.setCurrentProxyScriptUrl(proxy)) {
            log("sending Proxy Broadcast for " + proxy);
            Intent intent = new Intent("android.intent.action.PROXY_CHANGE");
            intent.addFlags(603979776);
            intent.putExtra("android.intent.extra.PROXY_INFO", proxy);
            long ident = Binder.clearCallingIdentity();
            try {
                this.mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
    }

    private static void log(String s) {
        Slog.d(TAG, s);
    }

    private static void loge(String s) {
        Slog.e(TAG, s);
    }

    private static <T> T checkNotNull(T value, String message) {
        if (value != null) {
            return value;
        }
        throw new NullPointerException(message);
    }

    public boolean prepareVpn(String oldPackage, String newPackage, int userId) {
        enforceCrossUserPermission(userId);
        throwIfLockdownEnabled();
        synchronized (this.mVpns) {
            Vpn vpn = (Vpn) this.mVpns.get(userId);
            if (vpn != null) {
                boolean prepare = vpn.prepare(oldPackage, newPackage);
                return prepare;
            }
            return false;
        }
    }

    public void setVpnPackageAuthorization(String packageName, int userId, boolean authorized) {
        enforceCrossUserPermission(userId);
        synchronized (this.mVpns) {
            Vpn vpn = (Vpn) this.mVpns.get(userId);
            if (vpn != null) {
                vpn.setPackageAuthorization(packageName, authorized);
            }
        }
    }

    public ParcelFileDescriptor establishVpn(VpnConfig config) {
        ParcelFileDescriptor establish;
        throwIfLockdownEnabled();
        int user = UserHandle.getUserId(Binder.getCallingUid());
        synchronized (this.mVpns) {
            establish = ((Vpn) this.mVpns.get(user)).establish(config);
        }
        return establish;
    }

    public void startLegacyVpn(VpnProfile profile) {
        throwIfLockdownEnabled();
        LinkProperties egress = getActiveLinkProperties();
        if (egress == null) {
            throw new IllegalStateException("Missing active network connection");
        }
        int user = UserHandle.getUserId(Binder.getCallingUid());
        synchronized (this.mVpns) {
            Vpn mVpn = (Vpn) this.mVpns.get(user);
        }
        if (mVpn != null) {
            mVpn.startLegacyVpn(profile, this.mKeyStore, egress);
        }
    }

    public LegacyVpnInfo getLegacyVpnInfo(int userId) {
        LegacyVpnInfo legacyVpnInfo;
        enforceCrossUserPermission(userId);
        synchronized (this.mVpns) {
            legacyVpnInfo = ((Vpn) this.mVpns.get(userId)).getLegacyVpnInfo();
        }
        return legacyVpnInfo;
    }

    public VpnInfo[] getAllVpnInfo() {
        enforceConnectivityInternalPermission();
        if (this.mLockdownEnabled) {
            return new VpnInfo[0];
        }
        VpnInfo[] vpnInfoArr;
        synchronized (this.mVpns) {
            List<VpnInfo> infoList = new ArrayList();
            for (int i = 0; i < this.mVpns.size(); i++) {
                VpnInfo info = createVpnInfo((Vpn) this.mVpns.valueAt(i));
                if (info != null) {
                    infoList.add(info);
                }
            }
            vpnInfoArr = (VpnInfo[]) infoList.toArray(new VpnInfo[infoList.size()]);
        }
        return vpnInfoArr;
    }

    private VpnInfo createVpnInfo(Vpn vpn) {
        VpnInfo info = vpn.getVpnInfo();
        if (info == null) {
            return null;
        }
        Network[] underlyingNetworks = vpn.getUnderlyingNetworks();
        if (underlyingNetworks == null) {
            NetworkAgentInfo defaultNetwork = getDefaultNetwork();
            if (!(defaultNetwork == null || defaultNetwork.linkProperties == null)) {
                info.primaryUnderlyingIface = getDefaultNetwork().linkProperties.getInterfaceName();
            }
        } else if (underlyingNetworks.length > 0) {
            LinkProperties linkProperties = getLinkProperties(underlyingNetworks[0]);
            if (linkProperties != null) {
                info.primaryUnderlyingIface = linkProperties.getInterfaceName();
            }
        }
        if (info.primaryUnderlyingIface == null) {
            info = null;
        }
        return info;
    }

    public VpnConfig getVpnConfig(int userId) {
        enforceCrossUserPermission(userId);
        synchronized (this.mVpns) {
            Vpn vpn = (Vpn) this.mVpns.get(userId);
            if (vpn != null) {
                VpnConfig vpnConfig = vpn.getVpnConfig();
                return vpnConfig;
            }
            return null;
        }
    }

    public boolean updateLockdownVpn() {
        if (Binder.getCallingUid() != 1000) {
            Slog.w(TAG, "Lockdown VPN only available to AID_SYSTEM");
            return false;
        }
        this.mLockdownEnabled = LockdownVpnTracker.isEnabled();
        if (this.mLockdownEnabled) {
            String profileName = new String(this.mKeyStore.get("LOCKDOWN_VPN"));
            VpnProfile profile = VpnProfile.decode(profileName, this.mKeyStore.get("VPN_" + profileName));
            if (profile == null) {
                Slog.e(TAG, "Lockdown VPN configured invalid profile " + profileName);
                setLockdownTracker(null);
                return true;
            }
            int user = UserHandle.getUserId(Binder.getCallingUid());
            synchronized (this.mVpns) {
                Vpn vpn = (Vpn) this.mVpns.get(user);
                if (vpn == null) {
                    Slog.w(TAG, "VPN for user " + user + " not ready yet. Skipping lockdown");
                    return false;
                }
                setLockdownTracker(new LockdownVpnTracker(this.mContext, this.mNetd, this, vpn, profile));
            }
        } else {
            setLockdownTracker(null);
        }
        return true;
    }

    private void setLockdownTracker(LockdownVpnTracker tracker) {
        LockdownVpnTracker existing = this.mLockdownTracker;
        this.mLockdownTracker = null;
        if (existing != null) {
            existing.shutdown();
        }
        if (tracker != null) {
            try {
                this.mNetd.setFirewallEnabled(true);
                this.mNetd.setFirewallInterfaceRule("lo", true);
                this.mLockdownTracker = tracker;
                this.mLockdownTracker.init();
                return;
            } catch (RemoteException e) {
                return;
            }
        }
        this.mNetd.setFirewallEnabled(false);
    }

    private void throwIfLockdownEnabled() {
        if (this.mLockdownEnabled) {
            throw new IllegalStateException("Unavailable in lockdown mode");
        }
    }

    private boolean startAlwaysOnVpn(int userId) {
        synchronized (this.mVpns) {
            Vpn vpn = (Vpn) this.mVpns.get(userId);
            if (vpn == null) {
                Slog.wtf(TAG, "User " + userId + " has no Vpn configuration");
                return false;
            }
            boolean startAlwaysOnVpn = vpn.startAlwaysOnVpn();
            return startAlwaysOnVpn;
        }
    }

    public boolean setAlwaysOnVpnPackage(int userId, String packageName, boolean lockdown) {
        enforceConnectivityInternalPermission();
        enforceCrossUserPermission(userId);
        if (LockdownVpnTracker.isEnabled()) {
            return false;
        }
        synchronized (this.mVpns) {
            Vpn vpn = (Vpn) this.mVpns.get(userId);
            if (vpn == null) {
                Slog.w(TAG, "User " + userId + " has no Vpn configuration");
                return false;
            } else if (!vpn.setAlwaysOnPackage(packageName, lockdown)) {
                return false;
            } else if (startAlwaysOnVpn(userId)) {
                vpn.saveAlwaysOnPackage();
                return true;
            } else {
                vpn.setAlwaysOnPackage(null, false);
                return false;
            }
        }
    }

    public String getAlwaysOnVpnPackage(int userId) {
        enforceConnectivityInternalPermission();
        enforceCrossUserPermission(userId);
        synchronized (this.mVpns) {
            Vpn vpn = (Vpn) this.mVpns.get(userId);
            if (vpn == null) {
                Slog.w(TAG, "User " + userId + " has no Vpn configuration");
                return null;
            }
            String alwaysOnPackage = vpn.getAlwaysOnPackage();
            return alwaysOnPackage;
        }
    }

    public int checkMobileProvisioning(int suggestedTimeOutMs) {
        return -1;
    }

    private void setProvNotificationVisible(boolean visible, int networkType, String action) {
        boolean z = visible;
        setProvNotificationVisibleIntent(z, DumpState.DUMP_INSTALLS + (networkType + 1), NotificationType.SIGN_IN, networkType, null, PendingIntent.getBroadcast(this.mContext, 0, new Intent(action), 0), false);
    }

    private void setProvNotificationVisibleIntent(boolean visible, int id, NotificationType notifyType, int networkType, String extraInfo, PendingIntent intent, boolean highPriority) {
        log("setProvNotificationVisibleIntent " + notifyType + " visible=" + visible + " networkType=" + ConnectivityManager.getNetworkTypeName(networkType) + " extraInfo=" + extraInfo + " highPriority=" + highPriority);
        Resources r = Resources.getSystem();
        NotificationManager notificationManager = (NotificationManager) this.mContext.getSystemService("notification");
        if (visible) {
            Bitmap bmp = null;
            Notification notification = new Notification();
            if (notifyType != NotificationType.NO_INTERNET || networkType != 1) {
                if (notifyType == NotificationType.SIGN_IN) {
                    CharSequence title;
                    CharSequence details;
                    int icon;
                    int i;
                    switch (networkType) {
                        case 0:
                        case 5:
                            title = r.getString(17040326, new Object[]{Integer.valueOf(0)});
                            details = this.mTelephonyManager.getNetworkOperatorName();
                            icon = 17303214;
                            bmp = BitmapFactory.decodeResource(r, 33751592);
                            break;
                        case 1:
                            title = r.getString(17040325, new Object[]{Integer.valueOf(0)});
                            details = r.getString(17040327, new Object[]{extraInfo});
                            icon = 17303218;
                            if (startBrowserForWifiPortal(notification, extraInfo)) {
                                return;
                            }
                            break;
                        default:
                            title = r.getString(17040326, new Object[]{Integer.valueOf(0)});
                            details = r.getString(17040327, new Object[]{extraInfo});
                            icon = 17303214;
                            bmp = BitmapFactory.decodeResource(r, 33751592);
                            break;
                    }
                    Builder localOnly = new Builder(this.mContext).setWhen(0).setSmallIcon(icon).setLargeIcon(bmp).setAutoCancel(true).setTicker(title).setColor(this.mContext.getColor(17170519)).setContentTitle(title).setContentText(details).setContentIntent(intent).setLocalOnly(true);
                    if (highPriority) {
                        i = 1;
                    } else {
                        i = 0;
                    }
                    localOnly = localOnly.setPriority(i);
                    if (highPriority) {
                        i = -1;
                    } else {
                        i = 0;
                    }
                    try {
                        notificationManager.notifyAsUser(NOTIFICATION_ID, id, localOnly.setDefaults(i).setOnlyAlertOnce(true).build(), UserHandle.ALL);
                    } catch (NullPointerException npe) {
                        loge("setNotificationVisible: visible notificationManager npe=" + npe);
                        npe.printStackTrace();
                    }
                } else {
                    Slog.wtf(TAG, "Unknown notification type " + notifyType + "on network type " + ConnectivityManager.getNetworkTypeName(networkType));
                    return;
                }
            }
            return;
        }
        try {
            notificationManager.cancelAsUser(NOTIFICATION_ID, id, UserHandle.ALL);
        } catch (NullPointerException npe2) {
            loge("setNotificationVisible: cancel notificationManager npe=" + npe2);
            npe2.printStackTrace();
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private String getProvisioningUrlBaseFromFile() {
        XmlPullParserException e;
        IOException e2;
        Throwable th;
        FileReader fileReader = null;
        Configuration config = this.mContext.getResources().getConfiguration();
        try {
            FileReader fileReader2 = new FileReader(this.mProvisioningUrlFile);
            try {
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(fileReader2);
                XmlUtils.beginDocument(parser, TAG_PROVISIONING_URLS);
                while (true) {
                    XmlUtils.nextElement(parser);
                    String element = parser.getName();
                    if (element == null) {
                        break;
                    } else if (element.equals(TAG_PROVISIONING_URL)) {
                        String mcc = parser.getAttributeValue(null, ATTR_MCC);
                        if (mcc != null) {
                            try {
                                if (Integer.parseInt(mcc) == config.mcc) {
                                    String mnc = parser.getAttributeValue(null, ATTR_MNC);
                                    if (mnc != null && Integer.parseInt(mnc) == config.mnc) {
                                        parser.next();
                                        if (parser.getEventType() == 4) {
                                            break;
                                        }
                                    }
                                } else {
                                    continue;
                                }
                            } catch (NumberFormatException e3) {
                                loge("NumberFormatException in getProvisioningUrlBaseFromFile: " + e3);
                            }
                        } else {
                            continue;
                        }
                    }
                }
                if (fileReader2 != null) {
                    try {
                        fileReader2.close();
                    } catch (IOException e4) {
                    }
                }
                return null;
                return r11;
            } catch (FileNotFoundException e5) {
                fileReader = fileReader2;
            } catch (XmlPullParserException e6) {
                e = e6;
                fileReader = fileReader2;
            } catch (IOException e7) {
                e2 = e7;
                fileReader = fileReader2;
            } catch (Throwable th2) {
                th = th2;
                fileReader = fileReader2;
            }
        } catch (FileNotFoundException e8) {
            try {
                loge("Carrier Provisioning Urls file not found");
                if (fileReader != null) {
                    try {
                        fileReader.close();
                    } catch (IOException e9) {
                    }
                }
                return null;
            } catch (Throwable th3) {
                th = th3;
                if (fileReader != null) {
                    try {
                        fileReader.close();
                    } catch (IOException e10) {
                    }
                }
                throw th;
            }
        } catch (XmlPullParserException e11) {
            e = e11;
            loge("Xml parser exception reading Carrier Provisioning Urls file: " + e);
            if (fileReader != null) {
                try {
                    fileReader.close();
                } catch (IOException e12) {
                }
            }
            return null;
        } catch (IOException e13) {
            e2 = e13;
            loge("I/O exception reading Carrier Provisioning Urls file: " + e2);
            if (fileReader != null) {
                try {
                    fileReader.close();
                } catch (IOException e14) {
                }
            }
            return null;
        }
    }

    public String getMobileProvisioningUrl() {
        enforceConnectivityInternalPermission();
        String url = getProvisioningUrlBaseFromFile();
        if (TextUtils.isEmpty(url)) {
            url = this.mContext.getResources().getString(17039434);
            log("getMobileProvisioningUrl: mobile_provisioining_url from resource =" + url);
        } else {
            log("getMobileProvisioningUrl: mobile_provisioning_url from File =" + url);
        }
        if (TextUtils.isEmpty(url)) {
            return url;
        }
        String phoneNumber = this.mTelephonyManager.getLine1Number();
        if (TextUtils.isEmpty(phoneNumber)) {
            phoneNumber = "0000000000";
        }
        return String.format(url, new Object[]{this.mTelephonyManager.getSimSerialNumber(), this.mTelephonyManager.getDeviceId(), phoneNumber});
    }

    public void setProvisioningNotificationVisible(boolean visible, int networkType, String action) {
        enforceConnectivityInternalPermission();
        long ident = Binder.clearCallingIdentity();
        try {
            setProvNotificationVisible(visible, networkType, action);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    public void setAirplaneMode(boolean enable) {
        enforceConnectivityInternalPermission();
        long ident = Binder.clearCallingIdentity();
        try {
            Global.putInt(this.mContext.getContentResolver(), "airplane_mode_on", enable ? 1 : 0);
            Intent intent = new Intent("android.intent.action.AIRPLANE_MODE");
            intent.putExtra(AudioService.CONNECT_INTENT_KEY_STATE, enable);
            this.mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void onUserStart(int userId) {
        synchronized (this.mVpns) {
            if (((Vpn) this.mVpns.get(userId)) != null) {
                loge("Starting user already has a VPN");
                return;
            }
            Vpn userVpn = new Vpn(this.mHandler.getLooper(), this.mContext, this.mNetd, userId);
            this.mVpns.put(userId, userVpn);
            ContentResolver cr = this.mContext.getContentResolver();
            String alwaysOnPackage = Secure.getStringForUser(cr, "always_on_vpn_app", userId);
            boolean alwaysOnLockdown = Secure.getIntForUser(cr, "always_on_vpn_lockdown", 0, userId) != 0;
            if (alwaysOnPackage != null) {
                userVpn.setAlwaysOnPackage(alwaysOnPackage, alwaysOnLockdown);
            }
        }
    }

    private void onUserStop(int userId) {
        synchronized (this.mVpns) {
            Vpn userVpn = (Vpn) this.mVpns.get(userId);
            if (userVpn == null) {
                loge("Stopped user has no VPN");
                return;
            }
            userVpn.onUserStopped();
            this.mVpns.delete(userId);
        }
    }

    private void onUserAdded(int userId) {
        synchronized (this.mVpns) {
            int vpnsSize = this.mVpns.size();
            for (int i = 0; i < vpnsSize; i++) {
                ((Vpn) this.mVpns.valueAt(i)).onUserAdded(userId);
            }
        }
    }

    private void onUserRemoved(int userId) {
        synchronized (this.mVpns) {
            int vpnsSize = this.mVpns.size();
            for (int i = 0; i < vpnsSize; i++) {
                ((Vpn) this.mVpns.valueAt(i)).onUserRemoved(userId);
            }
        }
    }

    private void onUserUnlocked(int userId) {
        if (this.mUserManager.getUserInfo(userId).isPrimary() && LockdownVpnTracker.isEnabled()) {
            updateLockdownVpn();
        } else {
            startAlwaysOnVpn(userId);
        }
    }

    private void ensureRequestableCapabilities(NetworkCapabilities networkCapabilities) {
        String badCapability = networkCapabilities.describeFirstNonRequestableCapability();
        if (badCapability != null) {
            throw new IllegalArgumentException("Cannot request network with " + badCapability);
        }
    }

    private ArrayList<Integer> getSignalStrengthThresholds(NetworkAgentInfo nai) {
        SortedSet<Integer> thresholds = new TreeSet();
        synchronized (nai) {
            for (NetworkRequestInfo nri : this.mNetworkRequests.values()) {
                if (nri.request.networkCapabilities.hasSignalStrength() && nai.satisfiesImmutableCapabilitiesOf(nri.request)) {
                    thresholds.add(Integer.valueOf(nri.request.networkCapabilities.getSignalStrength()));
                }
            }
        }
        return new ArrayList(thresholds);
    }

    private void updateSignalStrengthThresholds(NetworkAgentInfo nai, String reason, NetworkRequest request) {
        String detail;
        ArrayList<Integer> thresholdsArray = getSignalStrengthThresholds(nai);
        Bundle thresholds = new Bundle();
        thresholds.putIntegerArrayList("thresholds", thresholdsArray);
        if (request == null || !request.networkCapabilities.hasSignalStrength()) {
            detail = reason;
        } else {
            detail = reason + " " + request.networkCapabilities.getSignalStrength();
        }
        log(String.format("updateSignalStrengthThresholds: %s, sending %s to %s", new Object[]{detail, Arrays.toString(thresholdsArray.toArray()), nai.name()}));
        nai.asyncChannel.sendMessage(528398, 0, 0, thresholds);
    }

    public NetworkRequest requestNetwork(NetworkCapabilities networkCapabilities, Messenger messenger, int timeoutMs, IBinder binder, int legacyType) {
        NetworkRequestType type;
        if (networkCapabilities == null) {
            type = NetworkRequestType.TRACK_DEFAULT;
        } else {
            type = NetworkRequestType.REQUEST;
        }
        if (type == NetworkRequestType.TRACK_DEFAULT) {
            networkCapabilities = new NetworkCapabilities(this.mDefaultRequest.networkCapabilities);
            enforceAccessPermission();
        } else {
            NetworkCapabilities networkCapabilities2 = new NetworkCapabilities(networkCapabilities);
            enforceNetworkRequestPermissions(networkCapabilities2);
            enforceMeteredApnPolicy(networkCapabilities2);
            networkCapabilities = networkCapabilities2;
        }
        ensureRequestableCapabilities(networkCapabilities);
        if (timeoutMs < 0 || timeoutMs > 6000000) {
            throw new IllegalArgumentException("Bad timeout specified");
        } else if ("*".equals(networkCapabilities.getNetworkSpecifier())) {
            throw new IllegalArgumentException("Invalid network specifier - must not be '*'");
        } else {
            NetworkRequest networkRequest;
            if (SystemProperties.getBoolean("ro.config.hw_vowifi", false)) {
                networkRequest = new NetworkRequest(changeWifiMmsNetworkCapabilities(networkCapabilities), legacyType, nextNetworkRequestId());
            } else {
                networkRequest = new NetworkRequest(networkCapabilities, legacyType, nextNetworkRequestId());
            }
            NetworkRequestInfo nri = new NetworkRequestInfo(messenger, networkRequest, binder, type);
            log("requestNetwork for " + nri);
            this.mHandler.sendMessage(this.mHandler.obtainMessage(19, nri));
            if (timeoutMs > 0) {
                this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(20, nri), (long) timeoutMs);
            }
            return networkRequest;
        }
    }

    private void enforceNetworkRequestPermissions(NetworkCapabilities networkCapabilities) {
        if (networkCapabilities.hasCapability(13)) {
            enforceChangePermission();
        } else {
            enforceConnectivityInternalPermission();
        }
    }

    public boolean requestBandwidthUpdate(Network network) {
        enforceAccessPermission();
        if (network == null) {
            return false;
        }
        synchronized (this.mNetworkForNetId) {
            NetworkAgentInfo nai = (NetworkAgentInfo) this.mNetworkForNetId.get(network.netId);
        }
        if (nai == null) {
            return false;
        }
        nai.asyncChannel.sendMessage(528394);
        return true;
    }

    private boolean isSystem(int uid) {
        return uid < 10000;
    }

    private void enforceMeteredApnPolicy(NetworkCapabilities networkCapabilities) {
        int uid = Binder.getCallingUid();
        if (!(isSystem(uid) || networkCapabilities.hasCapability(11))) {
            synchronized (this.mRulesLock) {
                int uidRules = this.mUidRules.get(uid, 32);
            }
            if (this.mRestrictBackground && (uidRules & 1) == 0 && (uidRules & 2) == 0) {
                networkCapabilities.addCapability(11);
            }
        }
    }

    public NetworkRequest pendingRequestForNetwork(NetworkCapabilities networkCapabilities, PendingIntent operation) {
        checkNotNull(operation, "PendingIntent cannot be null.");
        NetworkCapabilities networkCapabilities2 = new NetworkCapabilities(networkCapabilities);
        enforceNetworkRequestPermissions(networkCapabilities2);
        enforceMeteredApnPolicy(networkCapabilities2);
        ensureRequestableCapabilities(networkCapabilities2);
        NetworkRequest networkRequest = new NetworkRequest(networkCapabilities2, -1, nextNetworkRequestId());
        NetworkRequestInfo nri = new NetworkRequestInfo(networkRequest, operation, NetworkRequestType.REQUEST);
        log("pendingRequest for " + nri);
        this.mHandler.sendMessage(this.mHandler.obtainMessage(26, nri));
        return networkRequest;
    }

    private void releasePendingNetworkRequestWithDelay(PendingIntent operation) {
        this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(27, getCallingUid(), 0, operation), (long) this.mReleasePendingIntentDelayMs);
    }

    public void releasePendingNetworkRequest(PendingIntent operation) {
        checkNotNull(operation, "PendingIntent cannot be null.");
        this.mHandler.sendMessage(this.mHandler.obtainMessage(27, getCallingUid(), 0, operation));
    }

    private boolean hasWifiNetworkListenPermission(NetworkCapabilities nc) {
        if (nc == null) {
            return false;
        }
        int[] transportTypes = nc.getTransportTypes();
        if (transportTypes.length != 1 || transportTypes[0] != 1) {
            return false;
        }
        try {
            this.mContext.enforceCallingOrSelfPermission("android.permission.ACCESS_WIFI_STATE", TAG);
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    public NetworkRequest listenForNetwork(NetworkCapabilities networkCapabilities, Messenger messenger, IBinder binder) {
        if (!hasWifiNetworkListenPermission(networkCapabilities)) {
            enforceAccessPermission();
        }
        NetworkRequest networkRequest = new NetworkRequest(new NetworkCapabilities(networkCapabilities), -1, nextNetworkRequestId());
        NetworkRequestInfo nri = new NetworkRequestInfo(messenger, networkRequest, binder, NetworkRequestType.LISTEN);
        log("listenForNetwork for " + nri);
        this.mHandler.sendMessage(this.mHandler.obtainMessage(21, nri));
        return networkRequest;
    }

    public void pendingListenForNetwork(NetworkCapabilities networkCapabilities, PendingIntent operation) {
        checkNotNull(operation, "PendingIntent cannot be null.");
        if (!hasWifiNetworkListenPermission(networkCapabilities)) {
            enforceAccessPermission();
        }
        NetworkRequestInfo nri = new NetworkRequestInfo(new NetworkRequest(new NetworkCapabilities(networkCapabilities), -1, nextNetworkRequestId()), operation, NetworkRequestType.LISTEN);
        log("pendingListenForNetwork for " + nri);
        this.mHandler.sendMessage(this.mHandler.obtainMessage(21, nri));
    }

    public void releaseNetworkRequest(NetworkRequest networkRequest) {
        if (SystemProperties.getBoolean("ro.config.hw_vowifi", false)) {
            wifiMmsRelease(networkRequest);
        }
        this.mHandler.sendMessage(this.mHandler.obtainMessage(22, getCallingUid(), 0, networkRequest));
    }

    public void registerNetworkFactory(Messenger messenger, String name) {
        enforceConnectivityInternalPermission();
        this.mHandler.sendMessage(this.mHandler.obtainMessage(17, new NetworkFactoryInfo(name, messenger, new AsyncChannel())));
    }

    private void handleRegisterNetworkFactory(NetworkFactoryInfo nfi) {
        log("Got NetworkFactory Messenger for " + nfi.name);
        this.mNetworkFactoryInfos.put(nfi.messenger, nfi);
        nfi.asyncChannel.connect(this.mContext, this.mTrackerHandler, nfi.messenger);
    }

    public void unregisterNetworkFactory(Messenger messenger) {
        enforceConnectivityInternalPermission();
        this.mHandler.sendMessage(this.mHandler.obtainMessage(23, messenger));
    }

    private void handleUnregisterNetworkFactory(Messenger messenger) {
        NetworkFactoryInfo nfi = (NetworkFactoryInfo) this.mNetworkFactoryInfos.remove(messenger);
        if (nfi == null) {
            loge("Failed to find Messenger in unregisterNetworkFactory");
        } else {
            log("unregisterNetworkFactory for " + nfi.name);
        }
    }

    private NetworkAgentInfo getDefaultNetwork() {
        return (NetworkAgentInfo) this.mNetworkForRequestId.get(this.mDefaultRequest.requestId);
    }

    private boolean isDefaultNetwork(NetworkAgentInfo nai) {
        return nai == getDefaultNetwork();
    }

    private NetworkAgentInfo getIdenticalActiveNetworkAgentInfo(NetworkAgentInfo na) {
        if (na == null || na.networkInfo.getState() != State.CONNECTED) {
            return null;
        }
        NetworkAgentInfo bestNetwork = null;
        for (NetworkAgentInfo network : this.mNetworkAgentInfos.values()) {
            log("checking existed " + network.name());
            if (network == this.mLegacyTypeTracker.getNetworkForType(network.networkInfo.getType())) {
                LinkProperties curNetworkLp = network.linkProperties;
                LinkProperties newNetworkLp = na.linkProperties;
                if (network.networkInfo.getState() == State.CONNECTED && curNetworkLp != null && !TextUtils.isEmpty(curNetworkLp.getInterfaceName())) {
                    boolean isLpIdentical = curNetworkLp.keyEquals(newNetworkLp);
                    log("LinkProperties Identical are " + isLpIdentical);
                    if (TextUtils.equals(network.networkCapabilities.getNetworkSpecifier(), na.networkCapabilities.getNetworkSpecifier()) && isLpIdentical) {
                        log("apparently satisfied");
                        bestNetwork = network;
                        break;
                    }
                }
                log("some key parameter is null, ignore");
            } else {
                log("not recorded, ignore");
            }
        }
        return bestNetwork;
    }

    public int registerNetworkAgent(Messenger messenger, NetworkInfo networkInfo, LinkProperties linkProperties, NetworkCapabilities networkCapabilities, int currentScore, NetworkMisc networkMisc) {
        enforceConnectivityInternalPermission();
        NetworkAgentInfo nai = new NetworkAgentInfo(messenger, new AsyncChannel(), new Network(reserveNetId()), new NetworkInfo(networkInfo), new LinkProperties(linkProperties), new NetworkCapabilities(networkCapabilities), currentScore, this.mContext, this.mTrackerHandler, new NetworkMisc(networkMisc), this.mDefaultRequest, this);
        synchronized (this) {
            nai.networkMonitor.systemReady = this.mSystemReady;
        }
        addValidationLogs(nai.networkMonitor.getValidationLogs(), nai.network, networkInfo.getExtraInfo());
        log("registerNetworkAgent " + nai);
        this.mHandler.sendMessage(this.mHandler.obtainMessage(18, nai));
        return nai.network.netId;
    }

    private void handleRegisterNetworkAgent(NetworkAgentInfo na) {
        log("Got NetworkAgent Messenger");
        NetworkAgentInfo identicalNai = getIdenticalActiveNetworkAgentInfo(na);
        if (identicalNai != null) {
            synchronized (identicalNai) {
                identicalNai.networkCapabilities.combineCapabilitiesWithNoSpecifiers(na.networkCapabilities);
            }
            na.networkMonitor.sendMessage(NetworkMonitor.CMD_NETWORK_DISCONNECTED);
            rematchNetworkAndRequests(identicalNai, ReapUnvalidatedNetworks.REAP);
            return;
        }
        this.mNetworkAgentInfos.put(na.messenger, na);
        synchronized (this.mNetworkForNetId) {
            this.mNetworkForNetId.put(na.network.netId, na);
        }
        na.asyncChannel.connect(this.mContext, this.mTrackerHandler, na.messenger);
        NetworkInfo networkInfo = na.networkInfo;
        na.networkInfo = null;
        updateNetworkInfo(na, networkInfo);
        handleLteMobileDataStateChange(networkInfo);
        holdWifiNetworkMessenger(na);
    }

    private void updateLinkProperties(NetworkAgentInfo networkAgent, LinkProperties oldLp) {
        LinkProperties newLp = networkAgent.linkProperties;
        int netId = networkAgent.network.netId;
        if (networkAgent.clatd != null) {
            networkAgent.clatd.fixupLinkProperties(oldLp);
        }
        updateInterfaces(newLp, oldLp, netId);
        updateMtu(newLp, oldLp);
        updateTcpBufferSizes(networkAgent);
        updateRoutes(newLp, oldLp, netId);
        updateDnses(newLp, oldLp, netId);
        updateClat(newLp, oldLp, networkAgent);
        if (isDefaultNetwork(networkAgent)) {
            handleApplyDefaultProxy(newLp.getHttpProxy());
        } else {
            updateProxy(newLp, oldLp, networkAgent);
        }
        if (!Objects.equals(newLp, oldLp)) {
            notifyIfacesChangedForNetworkStats();
            notifyNetworkCallbacks(networkAgent, 524295);
        }
        this.mKeepaliveTracker.handleCheckKeepalivesStillValid(networkAgent);
    }

    private void updateClat(LinkProperties newLp, LinkProperties oldLp, NetworkAgentInfo nai) {
        boolean wasRunningClat = nai.clatd != null ? nai.clatd.isStarted() : false;
        boolean shouldRunClat = Nat464Xlat.requiresClat(nai);
        if (!wasRunningClat && shouldRunClat) {
            nai.clatd = new Nat464Xlat(this.mContext, this.mNetd, this.mTrackerHandler, nai);
            nai.clatd.start();
        } else if (wasRunningClat && !shouldRunClat) {
            nai.clatd.stop();
        }
    }

    private void updateInterfaces(LinkProperties newLp, LinkProperties oldLp, int netId) {
        CompareResult<String> interfaceDiff = new CompareResult();
        if (oldLp != null) {
            interfaceDiff = oldLp.compareAllInterfaceNames(newLp);
        } else if (newLp != null) {
            interfaceDiff.added = newLp.getAllInterfaceNames();
        }
        for (String iface : interfaceDiff.added) {
            try {
                log("Adding iface " + iface + " to network " + netId);
                this.mNetd.addInterfaceToNetwork(iface, netId);
            } catch (Exception e) {
                loge("Exception adding interface: " + e);
            }
        }
        for (String iface2 : interfaceDiff.removed) {
            try {
                log("Removing iface " + iface2 + " from network " + netId);
                this.mNetd.removeInterfaceFromNetwork(iface2, netId);
            } catch (Exception e2) {
                loge("Exception removing interface: " + e2);
            }
        }
    }

    private boolean updateRoutes(LinkProperties newLp, LinkProperties oldLp, int netId) {
        CompareResult<RouteInfo> routeDiff = new CompareResult();
        if (oldLp != null) {
            routeDiff = oldLp.compareAllRoutes(newLp);
        } else if (newLp != null) {
            routeDiff.added = newLp.getAllRoutes();
        }
        for (RouteInfo route : routeDiff.added) {
            if (!route.hasGateway()) {
                log("Adding Route [" + route + "] to network " + netId);
                try {
                    this.mNetd.addRoute(netId, route);
                } catch (Exception e) {
                    if (route.getDestination().getAddress() instanceof Inet4Address) {
                        loge("Exception in addRoute for non-gateway: " + e);
                    } else {
                        loge("Exception in addRoute for non-gateway: " + e);
                    }
                }
            }
        }
        for (RouteInfo route2 : routeDiff.added) {
            if (route2.hasGateway()) {
                log("Adding Route [" + route2 + "] to network " + netId);
                try {
                    this.mNetd.addRoute(netId, route2);
                } catch (Exception e2) {
                    if (route2.getGateway() instanceof Inet4Address) {
                        loge("Exception in addRoute for gateway: " + e2);
                    } else {
                        loge("Exception in addRoute for gateway: " + e2);
                    }
                }
            }
        }
        for (RouteInfo route22 : routeDiff.removed) {
            log("Removing Route [" + route22 + "] from network " + netId);
            try {
                this.mNetd.removeRoute(netId, route22);
            } catch (Exception e22) {
                loge("Exception in removeRoute: " + e22);
            }
        }
        if (routeDiff.added.isEmpty() && routeDiff.removed.isEmpty()) {
            return false;
        }
        return true;
    }

    private void updateDnses(LinkProperties newLp, LinkProperties oldLp, int netId) {
        if (oldLp == null || !newLp.isIdenticalDnses(oldLp)) {
            Collection<InetAddress> dnses = newLp.getDnsServers();
            log("Setting DNS servers for network " + netId + " to " + dnses);
            try {
                this.mNetd.setDnsConfigurationForNetwork(netId, NetworkUtils.makeStrings(dnses), newLp.getDomains());
            } catch (Exception e) {
                loge("Exception in setDnsConfigurationForNetwork: " + e);
            }
            NetworkAgentInfo defaultNai = getDefaultNetwork();
            if (defaultNai != null && defaultNai.network.netId == netId) {
                setDefaultDnsSystemProperties(dnses);
            }
            flushVmDnsCache();
        }
    }

    private void setDefaultDnsSystemProperties(Collection<InetAddress> dnses) {
        int last = 0;
        for (InetAddress dns : dnses) {
            last++;
            try {
                SystemProperties.set("net.dns" + last, dns.getHostAddress());
            } catch (RuntimeException e) {
                loge("SystemProperties set RuntimeException: " + e);
            }
        }
        for (int i = last + 1; i <= this.mNumDnsEntries; i++) {
            try {
                SystemProperties.set("net.dns" + i, "");
            } catch (RuntimeException e2) {
                loge("SystemProperties set RuntimeException: " + e2);
            }
        }
        this.mNumDnsEntries = last;
    }

    private void updateCapabilities(NetworkAgentInfo nai, NetworkCapabilities networkCapabilities) {
        NetworkCapabilities networkCapabilities2 = new NetworkCapabilities(networkCapabilities);
        if (nai.lastValidated) {
            networkCapabilities2.addCapability(16);
        } else {
            networkCapabilities2.removeCapability(16);
        }
        if (nai.lastCaptivePortalDetected) {
            networkCapabilities2.addCapability(17);
        } else {
            networkCapabilities2.removeCapability(17);
        }
        if (!Objects.equals(nai.networkCapabilities, networkCapabilities2)) {
            int oldScore = nai.getCurrentScore();
            if (nai.networkCapabilities.hasCapability(13) != networkCapabilities2.hasCapability(13)) {
                try {
                    this.mNetd.setNetworkPermission(nai.network.netId, networkCapabilities2.hasCapability(13) ? null : NetworkManagementService.PERMISSION_SYSTEM);
                } catch (RemoteException e) {
                    loge("Exception in setNetworkPermission: " + e);
                }
            }
            synchronized (nai) {
                nai.networkCapabilities = networkCapabilities2;
            }
            rematchAllNetworksAndRequests(nai, oldScore);
            notifyNetworkCallbacks(nai, 524294);
        }
    }

    private void sendUpdatedScoreToFactories(NetworkAgentInfo nai) {
        for (int i = 0; i < nai.networkRequests.size(); i++) {
            NetworkRequest nr = (NetworkRequest) nai.networkRequests.valueAt(i);
            if (isRequest(nr)) {
                sendUpdatedScoreToFactories(nr, nai.getCurrentScore());
            }
        }
    }

    protected void sendUpdatedScoreToFactories(NetworkRequest networkRequest, int score) {
        log("sending new Min Network Score(" + score + "): " + networkRequest.toString());
        for (NetworkFactoryInfo nfi : this.mNetworkFactoryInfos.values()) {
            nfi.asyncChannel.sendMessage(536576, score, 0, networkRequest);
        }
    }

    private void sendUpdatedScoreToFactoriesWhenWifiDisconnected(NetworkRequest networkRequest, int score) {
        for (NetworkFactoryInfo nfi : this.mNetworkFactoryInfos.values()) {
            if (!"Telephony".equals(nfi.name)) {
                nfi.asyncChannel.sendMessage(536576, score, 0, networkRequest);
            }
        }
    }

    private void sendPendingIntentForRequest(NetworkRequestInfo nri, NetworkAgentInfo networkAgent, int notificationType) {
        if (notificationType == 524290 && !nri.mPendingIntentSent) {
            Intent intent = new Intent();
            intent.putExtra("android.net.extra.NETWORK", networkAgent.network);
            intent.putExtra("android.net.extra.NETWORK_REQUEST", nri.request);
            nri.mPendingIntentSent = true;
            sendIntent(nri.mPendingIntent, intent);
        }
    }

    private void sendIntent(PendingIntent pendingIntent, Intent intent) {
        this.mPendingIntentWakeLock.acquire();
        try {
            log("Sending " + pendingIntent);
            pendingIntent.send(this.mContext, 0, intent, this, null);
        } catch (CanceledException e) {
            log(pendingIntent + " was not sent, it had been canceled.");
            this.mPendingIntentWakeLock.release();
            releasePendingNetworkRequest(pendingIntent);
        }
    }

    public void onSendFinished(PendingIntent pendingIntent, Intent intent, int resultCode, String resultData, Bundle resultExtras) {
        log("Finished sending " + pendingIntent);
        this.mPendingIntentWakeLock.release();
        releasePendingNetworkRequestWithDelay(pendingIntent);
    }

    private void callCallbackForRequest(NetworkRequestInfo nri, NetworkAgentInfo networkAgent, int notificationType) {
        if (nri.messenger != null) {
            Bundle bundle = new Bundle();
            bundle.putParcelable(NetworkRequest.class.getSimpleName(), new NetworkRequest(nri.request));
            Message msg = Message.obtain();
            if (!(notificationType == 524293 || notificationType == 524296)) {
                bundle.putParcelable(Network.class.getSimpleName(), networkAgent.network);
            }
            switch (notificationType) {
                case 524291:
                    msg.arg1 = 30000;
                    break;
                case 524294:
                    bundle.putParcelable(NetworkCapabilities.class.getSimpleName(), new NetworkCapabilities(networkAgent.networkCapabilities));
                    break;
                case 524295:
                    bundle.putParcelable(LinkProperties.class.getSimpleName(), new LinkProperties(networkAgent.linkProperties));
                    break;
            }
            msg.what = notificationType;
            msg.setData(bundle);
            try {
                nri.messenger.send(msg);
            } catch (RemoteException e) {
                loge("RemoteException caught trying to send a callback msg for " + nri.request);
            }
        }
    }

    private void teardownUnneededNetwork(NetworkAgentInfo nai) {
        for (int i = 0; i < nai.networkRequests.size(); i++) {
            NetworkRequest nr = (NetworkRequest) nai.networkRequests.valueAt(i);
            if (isRequest(nr)) {
                loge("Dead network still had at least " + nr);
                break;
            }
        }
        nai.asyncChannel.disconnect();
    }

    private void handleLingerComplete(NetworkAgentInfo oldNetwork) {
        if (oldNetwork == null) {
            loge("Unknown NetworkAgentInfo in handleLingerComplete");
            return;
        }
        log("handleLingerComplete for " + oldNetwork.name());
        teardownUnneededNetwork(oldNetwork);
    }

    protected void makeDefault(NetworkAgentInfo newNetwork) {
        log("Switching to new default network: " + newNetwork);
        setupDataActivityTracking(newNetwork);
        try {
            this.mNetd.setDefaultNetId(newNetwork.network.netId);
        } catch (Exception e) {
            loge("Exception setting default network :" + e);
        }
        notifyLockdownVpn(newNetwork);
        handleApplyDefaultProxy(newNetwork.linkProperties.getHttpProxy());
        updateTcpBufferSizes(newNetwork);
        setDefaultDnsSystemProperties(newNetwork.linkProperties.getDnsServers());
    }

    private void rematchNetworkAndRequests(NetworkAgentInfo newNetwork, ReapUnvalidatedNetworks reapUnvalidatedNetworks) {
        if (newNetwork.everConnected) {
            boolean keep = newNetwork.isVPN();
            boolean isNewDefault = false;
            NetworkAgentInfo oldDefaultNetwork = null;
            log("rematching " + newNetwork.name());
            boolean isDisabledMobileNetwork = (newNetwork.networkInfo.getType() != 0 || this.mTelephonyManager.getDataEnabled()) ? false : !HwTelephonyManager.getDefault().isVSimEnabled();
            ArrayList<NetworkAgentInfo> affectedNetworks = new ArrayList();
            ArrayList<NetworkRequestInfo> addedRequests = new ArrayList();
            log(" network has: " + newNetwork.networkCapabilities);
            for (NetworkRequestInfo nri : this.mNetworkRequests.values()) {
                NetworkAgentInfo currentNetwork = (NetworkAgentInfo) this.mNetworkForRequestId.get(nri.request.requestId);
                boolean satisfies = newNetwork.satisfies(nri.request);
                if (this.mDefaultRequest.requestId == nri.request.requestId && isDisabledMobileNetwork) {
                    log("mobile net can't satisfy default network request when mobile data disabled");
                    satisfies = false;
                }
                if (newNetwork == currentNetwork && satisfies) {
                    log("Network " + newNetwork.name() + " was already satisfying" + " request " + nri.request.requestId + ". No change.");
                    keep = true;
                } else {
                    if (!satisfies) {
                        if (!checkNetworkSupportBip(newNetwork, nri.request)) {
                            if (newNetwork.networkRequests.get(nri.request.requestId) != null) {
                                log("Network " + newNetwork.name() + " stopped satisfying" + " request " + nri.request.requestId);
                                newNetwork.networkRequests.remove(nri.request.requestId);
                                if (currentNetwork == newNetwork) {
                                    this.mNetworkForRequestId.remove(nri.request.requestId);
                                    sendUpdatedScoreToFactories(nri.request, 0);
                                } else if (nri.isRequest()) {
                                    Slog.wtf(TAG, "BUG: Removing request " + nri.request.requestId + " from " + newNetwork.name() + " without updating mNetworkForRequestId or factories!");
                                }
                                callCallbackForRequest(nri, newNetwork, 524292);
                            }
                        }
                    }
                    if (nri.isRequest()) {
                        log("currentScore = " + (currentNetwork != null ? currentNetwork.getCurrentScore() : 0) + ", newScore = " + newNetwork.getCurrentScore());
                        if (currentNetwork == null || currentNetwork.getCurrentScore() < newNetwork.getCurrentScore()) {
                            log("rematch for " + newNetwork.name());
                            if (currentNetwork != null) {
                                log("   accepting network in place of " + currentNetwork.name());
                                currentNetwork.networkRequests.remove(nri.request.requestId);
                                currentNetwork.networkLingered.add(nri.request);
                                affectedNetworks.add(currentNetwork);
                            } else {
                                log("   accepting network in place of null");
                            }
                            unlinger(newNetwork);
                            this.mNetworkForRequestId.put(nri.request.requestId, newNetwork);
                            if (!newNetwork.addRequest(nri.request)) {
                                Slog.wtf(TAG, "BUG: " + newNetwork.name() + " already has " + nri.request);
                            }
                            addedRequests.add(nri);
                            keep = true;
                            sendUpdatedScoreToFactories(nri.request, newNetwork.getCurrentScore());
                            if (this.mDefaultRequest.requestId == nri.request.requestId) {
                                isNewDefault = true;
                                oldDefaultNetwork = currentNetwork;
                            }
                        }
                    } else {
                        if (newNetwork.addRequest(nri.request)) {
                            addedRequests.add(nri);
                        }
                    }
                }
            }
            for (NetworkAgentInfo nai : affectedNetworks) {
                if (!nai.lingering) {
                    if (unneeded(nai)) {
                        linger(nai);
                    } else {
                        unlinger(nai);
                    }
                }
            }
            if (NetworkFactory.isDualCellDataEnable()) {
                log("isDualCellDataEnable is true so keep is true");
                keep = true;
            }
            if (isNewDefault) {
                makeDefault(newNetwork);
                logDefaultNetworkEvent(newNetwork, oldDefaultNetwork);
                synchronized (this) {
                    if (this.mNetTransitionWakeLock.isHeld()) {
                        this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(8, this.mNetTransitionWakeLockSerialNumber, 0), 1000);
                    }
                }
            }
            for (NetworkRequestInfo nri2 : addedRequests) {
                notifyNetworkCallback(newNetwork, nri2);
            }
            if (isNewDefault) {
                if (oldDefaultNetwork != null) {
                    this.mLegacyTypeTracker.remove(oldDefaultNetwork.networkInfo.getType(), oldDefaultNetwork, true);
                }
                this.mDefaultInetConditionPublished = newNetwork.lastValidated ? 100 : 0;
                this.mLegacyTypeTracker.add(newNetwork.networkInfo.getType(), newNetwork);
                notifyLockdownVpn(newNetwork);
            }
            if (keep) {
                try {
                    IBatteryStats bs = BatteryStatsService.getService();
                    int type = newNetwork.networkInfo.getType();
                    String baseIface = newNetwork.linkProperties.getInterfaceName();
                    bs.noteNetworkInterfaceType(baseIface, type);
                    for (LinkProperties stacked : newNetwork.linkProperties.getStackedLinks()) {
                        String stackedIface = stacked.getInterfaceName();
                        bs.noteNetworkInterfaceType(stackedIface, type);
                        NetworkStatsFactory.noteStackedIface(stackedIface, baseIface);
                    }
                } catch (RemoteException e) {
                }
                for (int i = 0; i < newNetwork.networkRequests.size(); i++) {
                    NetworkRequest nr = (NetworkRequest) newNetwork.networkRequests.valueAt(i);
                    if (nr.legacyType != -1 && isRequest(nr)) {
                        this.mLegacyTypeTracker.add(nr.legacyType, newNetwork);
                    }
                }
                if (newNetwork.isVPN()) {
                    this.mLegacyTypeTracker.add(17, newNetwork);
                }
            }
            if (reapUnvalidatedNetworks == ReapUnvalidatedNetworks.REAP) {
                for (NetworkAgentInfo nai2 : this.mNetworkAgentInfos.values()) {
                    if (unneeded(nai2)) {
                        log("Reaping " + nai2.name());
                        teardownUnneededNetwork(nai2);
                    }
                }
            }
        }
    }

    private void rematchAllNetworksAndRequests(NetworkAgentInfo changed, int oldScore) {
        if (changed == null || oldScore >= changed.getCurrentScore()) {
            NetworkAgentInfo[] nais = (NetworkAgentInfo[]) this.mNetworkAgentInfos.values().toArray(new NetworkAgentInfo[this.mNetworkAgentInfos.size()]);
            Arrays.sort(nais);
            for (NetworkAgentInfo nai : nais) {
                ReapUnvalidatedNetworks reapUnvalidatedNetworks;
                if (nai != nais[nais.length - 1]) {
                    reapUnvalidatedNetworks = ReapUnvalidatedNetworks.DONT_REAP;
                } else {
                    reapUnvalidatedNetworks = ReapUnvalidatedNetworks.REAP;
                }
                rematchNetworkAndRequests(nai, reapUnvalidatedNetworks);
            }
            return;
        }
        rematchNetworkAndRequests(changed, ReapUnvalidatedNetworks.REAP);
    }

    private void updateInetCondition(NetworkAgentInfo nai) {
        if (nai.everValidated && isDefaultNetwork(nai)) {
            int newInetCondition = nai.lastValidated ? 100 : 0;
            if (newInetCondition != this.mDefaultInetConditionPublished) {
                this.mDefaultInetConditionPublished = newInetCondition;
                sendInetConditionBroadcast(nai.networkInfo);
            }
        }
    }

    private void notifyLockdownVpn(NetworkAgentInfo nai) {
        if (this.mLockdownTracker == null) {
            return;
        }
        if (nai == null || !nai.isVPN()) {
            this.mLockdownTracker.onNetworkInfoChanged();
        } else {
            this.mLockdownTracker.onVpnStateChanged(nai.networkInfo);
        }
    }

    private void updateNetworkInfo(NetworkAgentInfo networkAgent, NetworkInfo newInfo) {
        State state = newInfo.getState();
        int oldScore = networkAgent.getCurrentScore();
        synchronized (networkAgent) {
            NetworkInfo oldInfo = networkAgent.networkInfo;
            networkAgent.networkInfo = newInfo;
        }
        notifyLockdownVpn(networkAgent);
        if (oldInfo == null || oldInfo.getState() != state) {
            log(networkAgent.name() + " EVENT_NETWORK_INFO_CHANGED, going from " + (oldInfo == null ? "null" : oldInfo.getState()) + " to " + state);
            enableDefaultTypeApnWhenWifiConnectionStateChanged(state, newInfo.getType());
            enableDefaultTypeApnWhenBlueToothTetheringStateChanged(networkAgent, newInfo);
            if (!networkAgent.created && (state == State.CONNECTED || (state == State.CONNECTING && networkAgent.isVPN()))) {
                try {
                    if (networkAgent.isVPN()) {
                        boolean z;
                        INetworkManagementService iNetworkManagementService = this.mNetd;
                        int i = networkAgent.network.netId;
                        boolean z2 = !networkAgent.linkProperties.getDnsServers().isEmpty();
                        if (networkAgent.networkMisc == null) {
                            z = true;
                        } else if (networkAgent.networkMisc.allowBypass) {
                            z = false;
                        } else {
                            z = true;
                        }
                        iNetworkManagementService.createVirtualNetwork(i, z2, z);
                    } else {
                        String str;
                        INetworkManagementService iNetworkManagementService2 = this.mNetd;
                        int i2 = networkAgent.network.netId;
                        if (networkAgent.networkCapabilities.hasCapability(13)) {
                            str = null;
                        } else {
                            str = NetworkManagementService.PERMISSION_SYSTEM;
                        }
                        iNetworkManagementService2.createPhysicalNetwork(i2, str);
                    }
                    networkAgent.created = true;
                } catch (Exception e) {
                    loge("Error creating network " + networkAgent.network.netId + ": " + e.getMessage());
                    return;
                }
            }
            if (!networkAgent.everConnected && state == State.CONNECTED) {
                networkAgent.everConnected = true;
                updateLinkProperties(networkAgent, null);
                notifyIfacesChangedForNetworkStats();
                networkAgent.networkMonitor.sendMessage(NetworkMonitor.CMD_NETWORK_CONNECTED);
                scheduleUnvalidatedPrompt(networkAgent);
                if (networkAgent.isVPN()) {
                    setVpnSettingValue(true);
                    synchronized (this.mProxyLock) {
                        if (!this.mDefaultProxyDisabled) {
                            this.mDefaultProxyDisabled = true;
                            if (this.mGlobalProxy == null && this.mDefaultProxy != null) {
                                sendProxyBroadcast(null);
                            }
                        }
                    }
                }
                updateSignalStrengthThresholds(networkAgent, "CONNECT", null);
                rematchNetworkAndRequests(networkAgent, ReapUnvalidatedNetworks.REAP);
                notifyNetworkCallbacks(networkAgent, 524289);
            } else if (state == State.DISCONNECTED) {
                networkAgent.asyncChannel.disconnect();
                if (networkAgent.isVPN()) {
                    setVpnSettingValue(false);
                    synchronized (this.mProxyLock) {
                        if (this.mDefaultProxyDisabled) {
                            this.mDefaultProxyDisabled = false;
                            if (this.mGlobalProxy == null && this.mDefaultProxy != null) {
                                sendProxyBroadcast(this.mDefaultProxy);
                            }
                        }
                    }
                }
            } else {
                int i3;
                if (oldInfo == null || oldInfo.getState() != State.SUSPENDED) {
                    if (state == State.SUSPENDED) {
                    }
                }
                if (networkAgent.getCurrentScore() != oldScore) {
                    rematchAllNetworksAndRequests(networkAgent, oldScore);
                }
                if (state == State.SUSPENDED) {
                    i3 = 524299;
                } else {
                    i3 = 524300;
                }
                notifyNetworkCallbacks(networkAgent, i3);
                this.mLegacyTypeTracker.update(networkAgent);
            }
            hintUserSwitchToMobileWhileWifiDisconnected(state, newInfo.getType());
            return;
        }
        if (oldInfo.isRoaming() != newInfo.isRoaming()) {
            log("roaming status changed, notifying NetworkStatsService");
            notifyIfacesChangedForNetworkStats();
        } else {
            log("ignoring duplicate network state non-change");
        }
    }

    private void updateNetworkScore(NetworkAgentInfo nai, int score) {
        log("updateNetworkScore for " + nai.name() + " to " + score);
        if (score < 0) {
            loge("updateNetworkScore for " + nai.name() + " got a negative score (" + score + ").  Bumping score to min of 0");
            score = 0;
        }
        int oldScore = nai.getCurrentScore();
        nai.setCurrentScore(score);
        rematchAllNetworksAndRequests(nai, oldScore);
        sendUpdatedScoreToFactories(nai);
    }

    protected void notifyNetworkCallback(NetworkAgentInfo nai, NetworkRequestInfo nri) {
        if (nri.mPendingIntent == null) {
            callCallbackForRequest(nri, nai, 524290);
        } else {
            sendPendingIntentForRequest(nri, nai, 524290);
        }
    }

    private void sendLegacyNetworkBroadcast(NetworkAgentInfo nai, DetailedState state, int type) {
        NetworkInfo info = new NetworkInfo(nai.networkInfo);
        info.setType(type);
        if (state != DetailedState.DISCONNECTED) {
            info.setDetailedState(state, null, info.getExtraInfo());
            sendConnectedBroadcast(info);
            return;
        }
        info.setDetailedState(state, info.getReason(), info.getExtraInfo());
        Intent intent = new Intent("android.net.conn.CONNECTIVITY_CHANGE");
        intent.putExtra("networkInfo", info);
        intent.putExtra("networkType", info.getType());
        if (info.isFailover()) {
            intent.putExtra("isFailover", true);
            nai.networkInfo.setFailover(false);
        }
        if (info.getReason() != null) {
            intent.putExtra(PhoneWindowManager.SYSTEM_DIALOG_REASON_KEY, info.getReason());
        }
        if (info.getExtraInfo() != null) {
            intent.putExtra("extraInfo", info.getExtraInfo());
        }
        NetworkAgentInfo networkAgentInfo = null;
        if (nai.networkRequests.get(this.mDefaultRequest.requestId) != null) {
            networkAgentInfo = getDefaultNetwork();
            if (networkAgentInfo != null) {
                intent.putExtra("otherNetwork", networkAgentInfo.networkInfo);
            } else {
                intent.putExtra("noConnectivity", true);
            }
        }
        intent.putExtra("inetCondition", this.mDefaultInetConditionPublished);
        sendStickyBroadcast(intent);
        if (networkAgentInfo != null) {
            sendConnectedBroadcast(networkAgentInfo.networkInfo);
        }
    }

    protected void notifyNetworkCallbacks(NetworkAgentInfo networkAgent, int notifyType) {
        log("notifyType " + notifyTypeToName(notifyType) + " for " + networkAgent.name());
        for (int i = 0; i < networkAgent.networkRequests.size(); i++) {
            NetworkRequestInfo nri = (NetworkRequestInfo) this.mNetworkRequests.get((NetworkRequest) networkAgent.networkRequests.valueAt(i));
            if (nri.mPendingIntent == null) {
                callCallbackForRequest(nri, networkAgent, notifyType);
            } else {
                sendPendingIntentForRequest(nri, networkAgent, notifyType);
            }
        }
    }

    private String notifyTypeToName(int notifyType) {
        switch (notifyType) {
            case 524289:
                return "PRECHECK";
            case 524290:
                return "AVAILABLE";
            case 524291:
                return "LOSING";
            case 524292:
                return "LOST";
            case 524293:
                return "UNAVAILABLE";
            case 524294:
                return "CAP_CHANGED";
            case 524295:
                return "IP_CHANGED";
            case 524296:
                return "RELEASED";
            default:
                return "UNKNOWN";
        }
    }

    private void notifyIfacesChangedForNetworkStats() {
        try {
            this.mStatsService.forceUpdateIfaces();
        } catch (Exception e) {
        }
    }

    public boolean addVpnAddress(String address, int prefixLength) {
        boolean addAddress;
        throwIfLockdownEnabled();
        int user = UserHandle.getUserId(Binder.getCallingUid());
        synchronized (this.mVpns) {
            addAddress = ((Vpn) this.mVpns.get(user)).addAddress(address, prefixLength);
        }
        return addAddress;
    }

    public boolean removeVpnAddress(String address, int prefixLength) {
        boolean removeAddress;
        throwIfLockdownEnabled();
        int user = UserHandle.getUserId(Binder.getCallingUid());
        synchronized (this.mVpns) {
            removeAddress = ((Vpn) this.mVpns.get(user)).removeAddress(address, prefixLength);
        }
        return removeAddress;
    }

    public boolean setUnderlyingNetworksForVpn(Network[] networks) {
        boolean success;
        throwIfLockdownEnabled();
        int user = UserHandle.getUserId(Binder.getCallingUid());
        synchronized (this.mVpns) {
            success = ((Vpn) this.mVpns.get(user)).setUnderlyingNetworks(networks);
        }
        if (success) {
            notifyIfacesChangedForNetworkStats();
        }
        return success;
    }

    public String getCaptivePortalServerUrl() {
        return NetworkMonitor.getCaptivePortalServerUrl(this.mContext);
    }

    public void startNattKeepalive(Network network, int intervalSeconds, Messenger messenger, IBinder binder, String srcAddr, int srcPort, String dstAddr) {
        enforceKeepalivePermission();
        this.mKeepaliveTracker.startNattKeepalive(getNetworkAgentInfoForNetwork(network), intervalSeconds, messenger, binder, srcAddr, srcPort, dstAddr, 4500);
    }

    public void stopKeepalive(Network network, int slot) {
        this.mHandler.sendMessage(this.mHandler.obtainMessage(528396, slot, 0, network));
    }

    public void factoryReset() {
        enforceConnectivityInternalPermission();
        if (!this.mUserManager.hasUserRestriction("no_network_reset")) {
            int userId = UserHandle.getCallingUserId();
            setAirplaneMode(false);
            if (!this.mUserManager.hasUserRestriction("no_config_tethering")) {
                for (String tether : getTetheredIfaces()) {
                    untether(tether);
                }
            }
            if (!this.mUserManager.hasUserRestriction("no_config_vpn")) {
                synchronized (this.mVpns) {
                    String alwaysOnPackage = getAlwaysOnVpnPackage(userId);
                    if (alwaysOnPackage != null) {
                        setAlwaysOnVpnPackage(userId, null, false);
                        setVpnPackageAuthorization(alwaysOnPackage, userId, false);
                    }
                }
                VpnConfig vpnConfig = getVpnConfig(userId);
                if (vpnConfig != null) {
                    if (vpnConfig.legacy) {
                        prepareVpn("[Legacy VPN]", "[Legacy VPN]", userId);
                    } else {
                        setVpnPackageAuthorization(vpnConfig.user, userId, false);
                        prepareVpn(null, "[Legacy VPN]", userId);
                    }
                }
            }
        }
    }

    public NetworkMonitor createNetworkMonitor(Context context, Handler handler, NetworkAgentInfo nai, NetworkRequest defaultRequest) {
        return new NetworkMonitor(context, handler, nai, defaultRequest);
    }

    private static void logDefaultNetworkEvent(NetworkAgentInfo newNai, NetworkAgentInfo prevNai) {
        int newNetid = 0;
        int prevNetid = 0;
        int[] transports = new int[0];
        boolean z = false;
        boolean hadIPv6 = false;
        if (newNai != null) {
            newNetid = newNai.network.netId;
            transports = newNai.networkCapabilities.getTransportTypes();
        }
        if (prevNai != null) {
            prevNetid = prevNai.network.netId;
            LinkProperties lp = prevNai.linkProperties;
            z = lp.hasIPv4Address() ? lp.hasIPv4DefaultRoute() : false;
            hadIPv6 = lp.hasGlobalIPv6Address() ? lp.hasIPv6DefaultRoute() : false;
        }
        DefaultNetworkEvent.logEvent(newNetid, transports, prevNetid, z, hadIPv6);
    }
}
