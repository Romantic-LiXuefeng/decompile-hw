package android.rms;

import android.database.IContentObserver;
import android.net.Uri;
import android.os.Bundle;
import java.io.FileDescriptor;
import java.io.PrintWriter;

public interface HwSysResource {
    public static final int ACTIVITY = 36;
    public static final int ALARM = 13;
    public static final int ALOWED = 1;
    public static final int ANR = 24;
    public static final int APP = 19;
    public static final int APPMNGMEMNORMALNATIVE = 32;
    public static final int APPMNGMEMNORMALPERS = 33;
    public static final int APPMNGWHITELIST = 34;
    public static final int APPOPS = 14;
    public static final int APPSERVICE = 18;
    public static final int BASE = 10;
    public static final int BROADCAST = 11;
    public static final int BUDDYINFO = 100;
    public static final int CONTENTOBSERVER = 35;
    public static final int CPU = 21;
    public static final int CURSOR = 17;
    public static final int DELAY = 25;
    public static final int DELAYED = 3;
    public static final int DISALLOW = 4;
    public static final int DROPPED = 2;
    public static final int FACTOR_BASE = 100;
    public static final int FRAMELOST = 26;
    public static final int IO = 22;
    public static final int IOABN_DEV_LIFE_TIME_A = 29;
    public static final int IOABN_DEV_LIFE_TIME_B = 30;
    public static final int IOABN_DEV_RSV_BLK = 31;
    public static final int IOABN_WR_TOTAL = 28;
    public static final int IOABN_WR_UID = 27;
    public static final String KEY_LAUNCHTYPE = "launchfromActivity";
    public static final String KEY_PKG = "pkg";
    public static final String KEY_PROCESSTYPE = "processType";
    public static final String KEY_STARTTIME = "startTime";
    public static final String KEY_TOPPROCESS = "topProcess";
    public static final String KEY_UID = "callingUid";
    public static final String KEY_USAGETIME = "usageTime";
    public static final int MAINSERVICES = 101;
    public static final int MEMORY = 20;
    public static final int NOTIFICATION = 10;
    public static final int ORDEREDBROADCAST = 37;
    public static final int PIDS = 16;
    public static final int PROCESS_APP_HW = 1;
    public static final int PROCESS_APP_THIRDPARTY = 0;
    public static final int PROCESS_SYSTEM = 2;
    public static final int PROCESS_TOTAL = 3;
    public static final int PROCESS_TYPE_NUM = 4;
    public static final int PROCESS_UNKNOW = -1;
    public static final int PROVIDER = 15;
    public static final int RECEIVER = 12;
    public static final int RES_MONITOR_FEATURE = 2;
    public static final int RES_MULTI_QUEUE_FEATURE = 1;
    public static final int SCHEDGROUP = 23;
    public static final int SRMS_BIG_DATA_BEGIN = 10;
    public static final int SRMS_BIG_DATA_END = 13;
    public static final int SRMS_GET_STATISTICS_BEGIN = 0;
    public static final int SRMS_GET_STATISTICS_TOTAL = 1;
    public static final int SRMS_INTERVAL_LESS_100 = 12;
    public static final int SRMS_INTERVAL_LESS_20 = 10;
    public static final int SRMS_INTERVAL_LESS_60 = 11;
    public static final int SRMS_INTERVAL_MORE_100 = 13;
    public static final int SRMS_KEY_APP_BG = 1;
    public static final int SRMS_KEY_APP_FG = 0;
    public static final int TOTAL = 37;
    public static final int UNKNOWN = 0;
    public static final int WHITELIST = 0;
    public static final int WHITELIST_BROADCAST_SEND = 1;
    public static final int WHITELIST_BROADCAST_SEND_ACTION = 2;

    int acquire(int i, String str, int i2);

    int acquire(int i, String str, int i2, int i3);

    int acquire(Uri uri, IContentObserver iContentObserver, Bundle bundle);

    void clear(int i, String str, int i2);

    void dump(FileDescriptor fileDescriptor, PrintWriter printWriter);

    Bundle query();

    int queryPkgPolicy(int i, int i2, String str);

    void release(int i, String str, int i2);
}
