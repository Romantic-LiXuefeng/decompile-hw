package com.android.server.rms.iaware.appmng;

import android.content.Context;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.rms.HwSysResManager;
import android.rms.IUpdateWhiteListCallback;
import android.rms.IUpdateWhiteListCallback.Stub;
import android.rms.iaware.AppTypeRecoManager;
import android.rms.iaware.AwareLog;
import android.util.ArrayMap;
import android.util.ArraySet;
import com.android.server.PPPOEStateMachine;
import com.android.server.am.HwActivityManagerService;
import com.android.server.rms.algorithm.AwareUserHabit;
import com.android.server.rms.algorithm.AwareUserHabitAlgorithm.HabitProtectListChangeListener;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AwareDefaultConfigList {
    private static final String ADJCUSTTOP_CNT_KEY = "ADJCUSTTOP_CNT";
    private static final String ALARM_CHK_KEY = "ALM";
    public static final String APPMNG_2G_CFG_KEY = "APPMNG_2G_CFG";
    public static final String APPMNG_3G_CFG_KEY = "APPMNG_3G_CFG";
    public static final String APPMNG_4G_CFG_KEY = "APPMNG_4G_CFG";
    private static final int APPMNG_BADAPP_TYPE = 7;
    private static final int APPMNG_CFG_ADJ_2G_TYPE = 4;
    private static final int APPMNG_CFG_ADJ_3G_TYPE = 3;
    private static final int APPMNG_CFG_ADJ_TYPE = 2;
    private static final int APPMNG_CFG_WHITE_TYPE = 1;
    private static final int APPMNG_LOWEND_PROTECTED_ID = 5;
    private static final int APPMNG_RESTARTAPP_TYPE = 6;
    private static final String BG_DELAY_KEY = "BGDCY";
    private static boolean DEBUG = false;
    public static final int GROUP_ID_FORBIDSTOP = 1;
    public static final int GROUP_ID_NOSPEC = 0;
    public static final int GROUP_ID_SHORTAGESTOP = 2;
    private static final int HABIT_PROT_MAX_CNT = 10000;
    public static final int HW_PERCEPTIBLE_APP_ADJ = 260;
    private static final String IM_CNT_KEY = "IM_CNT";
    private static final String KEYPROC_DECAY_KEY = "KEYPROC_DECAY";
    private static final String KILL_MORE_KEY = "KM";
    private static final String LOWEND_KEY = "LOW";
    private static final int MASK_CLEANRES = 1;
    private static final int MASK_FREQUENTLYUSED = 4;
    private static final int MASK_GROUP = 3840;
    private static final int MASK_RESTART = 2;
    private static final String MEM_THRD_KEY = "MEM_THRD";
    private static final String PG_PROTECT_KEY = "PROT";
    private static final String RESTART_KEY = "RESTART";
    private static final String SEPARATOR = "#";
    private static final String SYSPROC_DECAY_KEY = "SYSPROC_DECAY";
    private static final String TAG = "AwareDefaultConfigList";
    private static final String TOPN_CNT_KEY = "TOPN_CNT";
    private static AwareDefaultConfigList sInstance = null;
    private final ArraySet<String> mAdjustAdjList = new ArraySet();
    private final ArrayList<String> mAllHabitAppList = new ArrayList();
    private final ArraySet<String> mAwareProtectList = new ArraySet();
    private final ArrayMap<String, PackageConfigItem> mAwareProtectMap = new ArrayMap();
    private final ArraySet<String> mBadAppList = new ArraySet();
    private int mCfgAdjTypeId = 4;
    private Context mContext;
    private boolean mEnabled = false;
    private final ArraySet<String> mHabitFrequentUsed = new ArraySet();
    private HabitProtectListChangeListener mHabitListener = new HabitProtectListChangeListener() {
        public void onListChanged() {
            AwareDefaultConfigList.this.setHabitWhiteList();
        }
    };
    private boolean mHasReadXml = false;
    private final ArrayList<String> mKeyHabitAppList = new ArrayList();
    private boolean mLowEnd = false;
    private final ArraySet<String> mRestartAppList = new ArraySet();
    private IUpdateWhiteListCallback mUpdateWhiteListCallback = new Stub() {
        public void update() throws RemoteException {
            if (AwareDefaultConfigList.DEBUG) {
                AwareLog.d(AwareDefaultConfigList.TAG, "IUpdateWhiteListCallback update whiteList.");
            }
            synchronized (AwareDefaultConfigList.this) {
                AwareDefaultConfigList.this.mHasReadXml = false;
                AwareDefaultConfigList.this.mAwareProtectList.clear();
            }
            AwareDefaultConfigList.this.setStaticXmlWhiteList();
        }
    };

    static class AppMngCfgXml {
        int mAdjCustTopN = 0;
        boolean mAlarmChk = true;
        long mBgDecay = 7200;
        int mImCnt = 0;
        long mKeyDecay = 0;
        boolean mKillMore = false;
        boolean mLowEnd = false;
        long mMemThrd = 0;
        boolean mPgProtect = false;
        boolean mRestart = true;
        long mSysDecay = 0;
        int mTopNCnt = 0;

        AppMngCfgXml() {
        }
    }

    public static class ProcessConfigItem {
        public boolean mFrequentlyUsed;
        public int mGroupId;
        public String mName;
        public boolean mResCleanAllow;
        public boolean mRestartFlag;

        private ProcessConfigItem() {
        }

        public ProcessConfigItem(String name, int value) {
            boolean z;
            boolean z2 = true;
            this.mName = name;
            if ((value & 1) != 0) {
                z = true;
            } else {
                z = false;
            }
            this.mResCleanAllow = z;
            if ((value & 2) != 0) {
                z = true;
            } else {
                z = false;
            }
            this.mRestartFlag = z;
            if ((value & 4) == 0) {
                z2 = false;
            }
            this.mFrequentlyUsed = z2;
            this.mGroupId = ((value & AwareDefaultConfigList.MASK_GROUP) >> 8) & 15;
        }

        public ProcessConfigItem copy() {
            ProcessConfigItem dst = new ProcessConfigItem();
            dst.mName = this.mName;
            dst.mResCleanAllow = this.mResCleanAllow;
            dst.mRestartFlag = this.mRestartFlag;
            dst.mFrequentlyUsed = this.mFrequentlyUsed;
            dst.mGroupId = this.mGroupId;
            return dst;
        }
    }

    public static class PackageConfigItem extends ProcessConfigItem {
        ArrayMap<String, ProcessConfigItem> mProcessMap = new ArrayMap();

        public PackageConfigItem(String name, int value) {
            super(name, value);
        }

        public void add(ProcessConfigItem item) {
            if (item != null) {
                this.mProcessMap.put(item.mName, item);
            }
        }

        public boolean isEmpty() {
            return this.mProcessMap.isEmpty();
        }

        public ProcessConfigItem getItem(String processName) {
            return (ProcessConfigItem) this.mProcessMap.get(processName);
        }
    }

    private AwareDefaultConfigList() {
    }

    private void initialize(Context context) {
        this.mContext = context;
        this.mEnabled = true;
        setAllWhiteList();
        startObserver();
        if (this.mLowEnd) {
            AwareUserHabit usrhabit = AwareUserHabit.getInstance();
            if (usrhabit != null) {
                usrhabit.setLowEndFlag(true);
            }
        }
    }

    private void deInitialize() {
        AwareUserHabit habitInstance = AwareUserHabit.getInstance();
        if (habitInstance != null) {
            habitInstance.unregistHabitProtectListChangeListener(this.mHabitListener);
        }
        this.mEnabled = false;
        synchronized (this) {
            this.mHasReadXml = false;
            this.mAwareProtectList.clear();
            this.mAwareProtectMap.clear();
            this.mKeyHabitAppList.clear();
            this.mAllHabitAppList.clear();
        }
        synchronized (this.mAdjustAdjList) {
            this.mAdjustAdjList.clear();
        }
        synchronized (this.mRestartAppList) {
            this.mRestartAppList.clear();
        }
        synchronized (this.mBadAppList) {
            this.mBadAppList.clear();
        }
        synchronized (this.mHabitFrequentUsed) {
            this.mHabitFrequentUsed.clear();
        }
    }

    public static void enable(Context context) {
        if (DEBUG) {
            AwareLog.d(TAG, "WhiteList Feature enable!!!");
        }
        getInstance().initialize(context);
    }

    public static void disable() {
        if (DEBUG) {
            AwareLog.d(TAG, "WhiteList Feature disable!!!");
        }
        getInstance().deInitialize();
    }

    private void startObserver() {
        if (DEBUG) {
            AwareLog.d(TAG, "WhiteList Feature startObserver!!!");
        }
        startXmlObserver();
        startHabitObserver();
    }

    public static void enableDebug() {
        DEBUG = true;
    }

    public static void disableDebug() {
        DEBUG = false;
    }

    public static AwareDefaultConfigList getInstance() {
        AwareDefaultConfigList awareDefaultConfigList;
        synchronized (AwareDefaultConfigList.class) {
            if (sInstance == null) {
                sInstance = new AwareDefaultConfigList();
            }
            awareDefaultConfigList = sInstance;
        }
        return awareDefaultConfigList;
    }

    private void setAllWhiteList() {
        setStaticXmlWhiteList();
        setHabitWhiteList();
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void setStaticXmlWhiteList() {
        synchronized (this) {
            if (this.mHasReadXml) {
            }
        }
    }

    private List<String> getGcmAppList(AwareUserHabit habitInstance) {
        List<String> gcmList = habitInstance.getGCMAppList();
        if (gcmList == null || AppMngConfig.getAbroadFlag()) {
            return gcmList;
        }
        List<String> gcmListFilter = new ArrayList();
        for (String pkg : gcmList) {
            if (isNeedGcmByAppType(AppTypeRecoManager.getInstance().getAppType(pkg))) {
                gcmListFilter.add(pkg);
            }
        }
        return gcmListFilter;
    }

    private boolean isNeedGcmByAppType(int appType) {
        boolean z = true;
        switch (appType) {
            case -1:
            case 0:
            case 1:
            case 255:
                return true;
            default:
                if (appType <= 255) {
                    z = false;
                }
                return z;
        }
    }

    private void setHabitWhiteList() {
        AwareUserHabit habitInstance = AwareUserHabit.getInstance();
        if (habitInstance != null) {
            List<String> habitProtectList;
            List<String> gcmList = getGcmAppList(habitInstance);
            List<String> habitProtectListAll = habitInstance.getHabitProtectListAll(10000, 10000);
            if (DEBUG) {
                AwareLog.i(TAG, "AllHabitListChangeListener onListChanged list:" + habitProtectListAll);
            }
            if (habitProtectListAll != null) {
                if (gcmList != null) {
                    habitProtectListAll.addAll(gcmList);
                }
                updateAllImCache(habitProtectListAll);
            }
            if (this.mLowEnd) {
                int emailCnt = AppMngConfig.getImCnt() / 2;
                habitProtectList = habitInstance.queryHabitProtectAppList(AppMngConfig.getImCnt() - emailCnt, emailCnt);
            } else {
                habitProtectList = habitInstance.getHabitProtectList(10000, 10000);
            }
            List<String> keyImList = new ArrayList();
            if (habitProtectList != null) {
                keyImList.addAll(habitProtectList);
            }
            if (!(gcmList == null || this.mLowEnd)) {
                keyImList.addAll(gcmList);
            }
            if (DEBUG) {
                AwareLog.i(TAG, "HabitListChangeListener onListChanged list:" + keyImList);
            }
            updateKeyImCache(keyImList);
        }
    }

    private ArraySet<String> getWhiteListFromRMS(int rmsGroupId) {
        String str;
        int i = 0;
        ArraySet<String> whiteList = new ArraySet();
        HwSysResManager resManager = HwSysResManager.getInstance();
        if (this.mLowEnd) {
            str = resManager.getWhiteList(rmsGroupId, 5);
        } else {
            str = resManager.getWhiteList(rmsGroupId, 0);
        }
        if (str == null) {
            if (DEBUG) {
                AwareLog.e(TAG, "getWhiteListFromRMS failed because null whiteList!");
            }
            return null;
        }
        String[] contentArray = str.split(";");
        int length = contentArray.length;
        while (i < length) {
            String content = contentArray[i].trim();
            if (!content.isEmpty()) {
                whiteList.add(content);
            }
            i++;
        }
        return whiteList;
    }

    private void updateAppMngCfgFromRMS(int rmsGroupId) {
        String str = HwSysResManager.getInstance().getWhiteList(rmsGroupId, 1);
        if (str != null && str.contains("{") && str.contains("}")) {
            AppMngCfgXml cfg2G = new AppMngCfgXml();
            AppMngCfgXml cfg3G = new AppMngCfgXml();
            AppMngCfgXml cfg4G = new AppMngCfgXml();
            AppMngCfgXml appMngCfgXml = null;
            for (String content : str.split("\\}")) {
                if (content != null) {
                    String[] contentArraySplit = content.split("\\{");
                    if (contentArraySplit.length > 1) {
                        String keyString = contentArraySplit[0];
                        String valueString = contentArraySplit[1];
                        if (!(keyString == null || valueString == null)) {
                            keyString = keyString.trim();
                            valueString = valueString.trim();
                            if (keyString.contains(APPMNG_2G_CFG_KEY) && cfg2G.mMemThrd == 0) {
                                setAppMngCfg(valueString, cfg2G);
                            } else if (keyString.contains(APPMNG_3G_CFG_KEY) && cfg3G.mMemThrd == 0) {
                                setAppMngCfg(valueString, cfg3G);
                            } else if (keyString.contains(APPMNG_4G_CFG_KEY) && cfg4G.mMemThrd == 0) {
                                setAppMngCfg(valueString, cfg4G);
                            }
                        }
                    }
                }
            }
            long memMb = AppMngConfig.getMemorySize();
            if (cfg2G.mMemThrd != 0 && memMb < cfg2G.mMemThrd) {
                appMngCfgXml = cfg2G;
                this.mCfgAdjTypeId = 4;
            } else if (cfg3G.mMemThrd != 0 && memMb < cfg3G.mMemThrd) {
                appMngCfgXml = cfg3G;
                this.mCfgAdjTypeId = 3;
            } else if (cfg4G.mMemThrd != 0) {
                appMngCfgXml = cfg4G;
                this.mCfgAdjTypeId = 2;
            }
            if (appMngCfgXml != null) {
                this.mLowEnd = appMngCfgXml.mLowEnd;
                boolean restartFlag = appMngCfgXml.mRestart;
                AppMngConfig.setAbroadFlag(isAbroadArea());
                AppMngConfig.setRestartFlag(restartFlag);
                AppMngConfig.setTopN(appMngCfgXml.mTopNCnt);
                AppMngConfig.setImCnt(appMngCfgXml.mImCnt);
                AppMngConfig.setSysDecay(appMngCfgXml.mSysDecay);
                AppMngConfig.setKeySysDecay(appMngCfgXml.mKeyDecay);
                AppMngConfig.setAdjCustTopN(appMngCfgXml.mAdjCustTopN);
                AppMngConfig.setBgDecay(appMngCfgXml.mBgDecay);
                AppMngConfig.setPgProtectFlag(appMngCfgXml.mPgProtect);
                AppMngConfig.setAlarmChkFlag(appMngCfgXml.mAlarmChk);
                AppMngConfig.setKillMoreFlag(appMngCfgXml.mKillMore);
            }
        }
    }

    public boolean isLowEnd() {
        return this.mLowEnd;
    }

    private void updateAdjWhiteListFromRMS(int rmsGroupId) {
        Set<String> adjList = updateListFromRMS(rmsGroupId, this.mCfgAdjTypeId);
        if (adjList != null) {
            synchronized (this.mAdjustAdjList) {
                this.mAdjustAdjList.clear();
                this.mAdjustAdjList.addAll(adjList);
            }
        }
    }

    private void updateRestartAppListFromRMS(int rmsGroupId) {
        Set<String> adjList = updateListFromRMS(rmsGroupId, 6);
        if (adjList != null) {
            synchronized (this.mRestartAppList) {
                this.mRestartAppList.clear();
                this.mRestartAppList.addAll(adjList);
            }
        }
    }

    private void updateBadAppListFromRMS(int rmsGroupId) {
        Set<String> adjList = updateListFromRMS(rmsGroupId, 7);
        if (adjList != null) {
            synchronized (this.mBadAppList) {
                this.mBadAppList.clear();
                this.mBadAppList.addAll(adjList);
            }
        }
    }

    private Set<String> updateListFromRMS(int rmsGroupId, int whiteListType) {
        int i = 0;
        String str = HwSysResManager.getInstance().getWhiteList(rmsGroupId, whiteListType);
        if (str == null) {
            if (DEBUG) {
                AwareLog.e(TAG, "updateAdjWhiteListFromRMS failed because null whiteList!");
            }
            return null;
        }
        ArraySet<String> adjList = new ArraySet();
        String[] contentArray = str.split("#")[0].split(";");
        int length = contentArray.length;
        while (i < length) {
            String content = contentArray[i].trim();
            if (!content.isEmpty()) {
                adjList.add(content);
            }
            i++;
        }
        return adjList;
    }

    private void setAppMngCfg(String str, AppMngCfgXml cfg2G) {
        if (str != null && cfg2G != null) {
            for (String content : str.split(";")) {
                if (content != null) {
                    String[] names = content.trim().split(":");
                    if (names.length > 1) {
                        String cfgType = names[0];
                        String cfgValue = names[1];
                        if (!(cfgType == null || cfgValue == null)) {
                            cfgType = cfgType.trim();
                            int value = 0;
                            try {
                                value = Integer.parseInt(cfgValue.trim(), 10);
                            } catch (NumberFormatException e) {
                                AwareLog.e(TAG, "parseInt error");
                            }
                            if (MEM_THRD_KEY.equals(cfgType)) {
                                cfg2G.mMemThrd = (long) value;
                            } else if (TOPN_CNT_KEY.equals(cfgType)) {
                                cfg2G.mTopNCnt = value;
                            } else if (IM_CNT_KEY.equals(cfgType)) {
                                cfg2G.mImCnt = value;
                            } else if (SYSPROC_DECAY_KEY.equals(cfgType)) {
                                cfg2G.mSysDecay = (long) value;
                            } else if (KEYPROC_DECAY_KEY.equals(cfgType)) {
                                cfg2G.mKeyDecay = (long) value;
                            } else if (ADJCUSTTOP_CNT_KEY.equals(cfgType)) {
                                cfg2G.mAdjCustTopN = value;
                            } else if (RESTART_KEY.equals(cfgType)) {
                                cfg2G.mRestart = value != 0;
                            } else if (BG_DELAY_KEY.equals(cfgType)) {
                                cfg2G.mBgDecay = (long) value;
                            } else if (PG_PROTECT_KEY.equals(cfgType)) {
                                cfg2G.mPgProtect = value != 0;
                            } else if (ALARM_CHK_KEY.equals(cfgType)) {
                                cfg2G.mAlarmChk = value != 0;
                            } else if (KILL_MORE_KEY.equals(cfgType)) {
                                cfg2G.mKillMore = value != 0;
                            } else if (LOWEND_KEY.equals(cfgType)) {
                                cfg2G.mLowEnd = value != 0;
                            }
                        }
                    }
                }
            }
        }
    }

    private void parseAwareProtectList() {
        synchronized (this) {
            if (this.mAwareProtectList == null) {
                return;
            }
            this.mAwareProtectMap.clear();
            int value = 0;
            for (String str : this.mAwareProtectList) {
                if (str != null && str.contains("{") && str.contains("}")) {
                    int startIdx = str.indexOf("{");
                    int endIdx = str.indexOf("}");
                    if (startIdx + 1 < endIdx && startIdx + 1 < str.length()) {
                        String pkgName = str.substring(0, startIdx);
                        try {
                            value = Integer.parseInt(str.substring(startIdx + 1, endIdx), 16);
                        } catch (NumberFormatException e) {
                            AwareLog.e(TAG, "parseInt error");
                        }
                        String[] names = pkgName.split("#");
                        pkgName = names[0];
                        String str2 = names.length > 1 ? names[1] : null;
                        if (!pkgName.isEmpty()) {
                            PackageConfigItem item = (PackageConfigItem) this.mAwareProtectMap.get(pkgName);
                            if (item == null) {
                                item = new PackageConfigItem(pkgName, value);
                                this.mAwareProtectMap.put(pkgName, item);
                                if (DEBUG) {
                                    AwareLog.i(TAG, "pkgName:" + pkgName + " mGroupId:" + item.mGroupId + " restart:" + item.mRestartFlag + " clean:" + item.mResCleanAllow + " frequently used:" + item.mFrequentlyUsed);
                                }
                            }
                            if (str2 != null) {
                                ProcessConfigItem dItem = new ProcessConfigItem(str2, value);
                                item.add(dItem);
                                if (DEBUG) {
                                    AwareLog.i(TAG, "processName:" + str2 + " mGroupId:" + dItem.mGroupId + " restart:" + dItem.mRestartFlag + " clean:" + dItem.mResCleanAllow + " frequently used:" + item.mFrequentlyUsed);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void startHabitObserver() {
        setHabitWhiteList();
        AwareUserHabit habitInstance = AwareUserHabit.getInstance();
        if (habitInstance != null) {
            habitInstance.registHabitProtectListChangeListener(this.mHabitListener);
        }
    }

    private void startXmlObserver() {
        HwSysResManager.getInstance().registerResourceCallback(this.mUpdateWhiteListCallback);
    }

    public Map<String, PackageConfigItem> getAwareProtectMap() {
        if (!this.mEnabled) {
            return null;
        }
        ArrayMap<String, PackageConfigItem> map = new ArrayMap();
        setStaticXmlWhiteList();
        synchronized (this) {
            map.putAll(this.mAwareProtectMap);
        }
        return map;
    }

    public List<String> getKeyHabitAppList() {
        List<String> list = new ArrayList();
        if (!this.mEnabled) {
            return list;
        }
        synchronized (this) {
            list.addAll(this.mKeyHabitAppList);
        }
        return list;
    }

    public List<String> getAllHabitAppList() {
        List<String> list = new ArrayList();
        if (!this.mEnabled) {
            return list;
        }
        synchronized (this) {
            list.addAll(this.mAllHabitAppList);
        }
        return list;
    }

    public void updateKeyImCache(List<String> list) {
        if (list != null) {
            synchronized (this) {
                this.mKeyHabitAppList.clear();
                int maxCnt = 0;
                for (String pkgName : list) {
                    if (pkgName != null) {
                        this.mKeyHabitAppList.add(pkgName);
                        maxCnt++;
                        if (maxCnt >= AppMngConfig.getImCnt()) {
                            break;
                        }
                    }
                }
            }
        }
    }

    public void updateAllImCache(List<String> list) {
        if (list != null) {
            synchronized (this) {
                this.mAllHabitAppList.clear();
                for (String pkgName : list) {
                    if (pkgName != null) {
                        this.mAllHabitAppList.add(pkgName);
                    }
                }
            }
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw) {
        if (pw != null) {
            if (this.mEnabled) {
                pw.println("dump iAware Protect WhiteList Apps start --------");
                synchronized (this) {
                    for (String item : this.mAwareProtectList) {
                        pw.println(item);
                    }
                }
                pw.println("dump iAware Protect WhiteList Apps end-----------");
                pw.println("dump iAware Adjust Adj Apps start --------");
                synchronized (this.mAdjustAdjList) {
                    for (String item2 : this.mAdjustAdjList) {
                        pw.println(item2);
                    }
                }
                pw.println("dump iAware Adjust Adj Apps end-----------");
                pw.println("dump User Habit Frequent Used start-----------");
                synchronized (this.mHabitFrequentUsed) {
                    for (String item22 : this.mHabitFrequentUsed) {
                        pw.println(item22);
                    }
                }
                pw.println("dump User Habit Frequent Used end-----------");
                pw.println("dump User Habit WhiteList Apps start ------------");
                synchronized (this) {
                    for (String item222 : this.mKeyHabitAppList) {
                        pw.println(item222);
                    }
                }
                pw.println("dump User Habit WhiteList Apps end --------------");
                pw.println("dump User All Habit WhiteList Apps start ------------");
                synchronized (this) {
                    for (String item2222 : this.mAllHabitAppList) {
                        pw.println(item2222);
                    }
                }
                pw.println("dump User All Habit WhiteList Apps end --------------");
                pw.println("dump iAware Restart Apps start --------");
                synchronized (this.mRestartAppList) {
                    for (String item22222 : this.mRestartAppList) {
                        pw.println(item22222);
                    }
                }
                pw.println("dump iAware Restart Apps end-----------");
                pw.println("dump iAware Bad Apps start --------");
                synchronized (this.mBadAppList) {
                    for (String item222222 : this.mBadAppList) {
                        pw.println(item222222);
                    }
                }
                pw.println("dump iAware Bad Apps end-----------");
                pw.println("dump AppMng Config start ------------");
                dumpCfg(pw);
                pw.println("dump AppMng Configs end ------------");
                return;
            }
            pw.println("WhiteList feature not enabled.");
        }
    }

    private void dumpCfg(PrintWriter pw) {
        pw.println("memMB:" + AppMngConfig.getMemorySize());
        pw.println("topN:" + AppMngConfig.getTopN());
        pw.println("imCnt:" + AppMngConfig.getImCnt());
        pw.println("sysDecay:" + AppMngConfig.getSysDecay());
        pw.println("keySysDecay:" + AppMngConfig.getKeySysDecay());
        pw.println("adjCustTopN:" + AppMngConfig.getAdjCustTopN());
        pw.println("restart:" + AppMngConfig.getRestartFlag());
        pw.println("abroad:" + AppMngConfig.getAbroadFlag());
        pw.println("bgDecayMinute:" + AppMngConfig.getBgDecay());
        pw.println("pgProtectEn:" + AppMngConfig.getPgProtectFlag());
        pw.println("alarmChk:" + AppMngConfig.getAlarmCheckFlag());
        pw.println("killMore:" + AppMngConfig.getKillMoreFlag());
        pw.println("lowEnd:" + this.mLowEnd);
    }

    public void fillMostFrequentUsedApp(List<String> list) {
        if (this.mEnabled) {
            List<String> listHabit = new ArrayList();
            if (list != null) {
                listHabit.addAll(list);
            }
            Set<String> setAdjPkg = new ArraySet();
            setAdjPkg.addAll(listHabit);
            synchronized (this.mAdjustAdjList) {
                setAdjPkg.addAll(this.mAdjustAdjList);
            }
            HwActivityManagerService ams = HwActivityManagerService.self();
            if (ams != null) {
                ams.setAndRestoreMaxAdjIfNeed(setAdjPkg);
            }
            synchronized (this.mHabitFrequentUsed) {
                this.mHabitFrequentUsed.clear();
                this.mHabitFrequentUsed.addAll(listHabit);
            }
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public boolean isAppMngOomAdjCustomized(String pkg) {
        if (!this.mEnabled || pkg == null) {
            return false;
        }
        synchronized (this.mAdjustAdjList) {
            if (this.mAdjustAdjList.contains(pkg)) {
                return true;
            }
        }
    }

    public Set<String> getRestartAppList() {
        Set arraySet;
        synchronized (this.mRestartAppList) {
            arraySet = new ArraySet(this.mRestartAppList);
        }
        return arraySet;
    }

    public Set<String> getBadAppList() {
        Set arraySet;
        synchronized (this.mBadAppList) {
            arraySet = new ArraySet(this.mBadAppList);
        }
        return arraySet;
    }

    public static boolean isAbroadArea() {
        return !SystemProperties.get("ro.config.hw_optb", PPPOEStateMachine.PHASE_DEAD).equals("156");
    }
}
