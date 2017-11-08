package com.android.server.notification;

import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.UserHandle;
import android.service.notification.NotificationListenerService.Ranking;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Slog;
import com.android.server.job.JobSchedulerShellCommand;
import com.android.server.notification.NotificationManagerService.DumpFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

public class RankingHelper implements RankingConfig {
    private static final String ATT_IMPORTANCE = "importance";
    private static final String ATT_NAME = "name";
    private static final String ATT_PRIORITY = "priority";
    private static final String ATT_TOPIC_ID = "id";
    private static final String ATT_TOPIC_LABEL = "label";
    private static final String ATT_UID = "uid";
    private static final String ATT_VERSION = "version";
    private static final String ATT_VISIBILITY = "visibility";
    private static final int DEFAULT_IMPORTANCE = -1000;
    private static final int DEFAULT_PRIORITY = 0;
    private static final int DEFAULT_VISIBILITY = -1000;
    private static final String TAG = "RankingHelper";
    private static final String TAG_PACKAGE = "package";
    private static final String TAG_RANKING = "ranking";
    private static final int XML_VERSION = 1;
    private final Context mContext;
    private final GlobalSortKeyComparator mFinalComparator = new GlobalSortKeyComparator();
    private final NotificationComparator mPreliminaryComparator = new NotificationComparator();
    private final ArrayMap<String, NotificationRecord> mProxyByGroupTmp = new ArrayMap();
    private final RankingHandler mRankingHandler;
    private final ArrayMap<String, Record> mRecords = new ArrayMap();
    private final ArrayMap<String, Record> mRestoredWithoutUids = new ArrayMap();
    private final NotificationSignalExtractor[] mSignalExtractors;

    private static class Record {
        static int UNKNOWN_UID = -10000;
        int importance;
        String pkg;
        int priority;
        int uid;
        int visibility;

        private Record() {
            this.uid = UNKNOWN_UID;
            this.importance = JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE;
            this.priority = 0;
            this.visibility = JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE;
        }
    }

    public RankingHelper(Context context, RankingHandler rankingHandler, NotificationUsageStats usageStats, String[] extractorNames) {
        this.mContext = context;
        this.mRankingHandler = rankingHandler;
        int N = extractorNames.length;
        this.mSignalExtractors = new NotificationSignalExtractor[N];
        for (int i = 0; i < N; i++) {
            try {
                NotificationSignalExtractor extractor = (NotificationSignalExtractor) this.mContext.getClassLoader().loadClass(extractorNames[i]).newInstance();
                extractor.initialize(this.mContext, usageStats);
                extractor.setConfig(this);
                this.mSignalExtractors[i] = extractor;
            } catch (ClassNotFoundException e) {
                Slog.w(TAG, "Couldn't find extractor " + extractorNames[i] + ".", e);
            } catch (InstantiationException e2) {
                Slog.w(TAG, "Couldn't instantiate extractor " + extractorNames[i] + ".", e2);
            } catch (IllegalAccessException e3) {
                Slog.w(TAG, "Problem accessing extractor " + extractorNames[i] + ".", e3);
            }
        }
    }

    public <T extends NotificationSignalExtractor> T findExtractor(Class<T> extractorClass) {
        for (NotificationSignalExtractor extractor : this.mSignalExtractors) {
            if (extractorClass.equals(extractor.getClass())) {
                return extractor;
            }
        }
        return null;
    }

    public void extractSignals(NotificationRecord r) {
        for (NotificationSignalExtractor extractor : this.mSignalExtractors) {
            try {
                RankingReconsideration recon = extractor.process(r);
                if (recon != null) {
                    this.mRankingHandler.requestReconsideration(recon);
                }
            } catch (Throwable t) {
                Slog.w(TAG, "NotificationSignalExtractor failed.", t);
            }
        }
    }

    public void readXml(XmlPullParser parser, boolean forRestore) throws XmlPullParserException, IOException {
        PackageManager pm = this.mContext.getPackageManager();
        if (parser.getEventType() == 2) {
            if (TAG_RANKING.equals(parser.getName())) {
                this.mRecords.clear();
                this.mRestoredWithoutUids.clear();
                while (true) {
                    int type = parser.next();
                    if (type != 1) {
                        String tag = parser.getName();
                        if (type != 3 || !TAG_RANKING.equals(tag)) {
                            if (type == 2 && "package".equals(tag)) {
                                int uid = safeInt(parser, ATT_UID, Record.UNKNOWN_UID);
                                String name = parser.getAttributeValue(null, ATT_NAME);
                                if (!TextUtils.isEmpty(name)) {
                                    Record r;
                                    if (forRestore) {
                                        try {
                                            uid = pm.getPackageUidAsUser(name, 0);
                                        } catch (NameNotFoundException e) {
                                        }
                                    }
                                    if (uid == Record.UNKNOWN_UID) {
                                        r = (Record) this.mRestoredWithoutUids.get(name);
                                        if (r == null) {
                                            r = new Record();
                                            this.mRestoredWithoutUids.put(name, r);
                                        }
                                    } else {
                                        r = getOrCreateRecord(name, uid);
                                    }
                                    r.importance = safeInt(parser, ATT_IMPORTANCE, JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE);
                                    r.priority = safeInt(parser, ATT_PRIORITY, 0);
                                    r.visibility = safeInt(parser, ATT_VISIBILITY, JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE);
                                }
                            }
                        } else {
                            return;
                        }
                    }
                    throw new IllegalStateException("Failed to reach END_DOCUMENT");
                }
            }
        }
    }

    private static String recordKey(String pkg, int uid) {
        return pkg + "|" + uid;
    }

    private Record getOrCreateRecord(String pkg, int uid) {
        String key = recordKey(pkg, uid);
        Record r = (Record) this.mRecords.get(key);
        if (r != null) {
            return r;
        }
        r = new Record();
        r.pkg = pkg;
        r.uid = uid;
        this.mRecords.put(key, r);
        return r;
    }

    public void writeXml(XmlSerializer out, boolean forBackup) throws IOException {
        out.startTag(null, TAG_RANKING);
        out.attribute(null, ATT_VERSION, Integer.toString(1));
        int N = this.mRecords.size();
        for (int i = 0; i < N; i++) {
            Record r = (Record) this.mRecords.valueAt(i);
            if (r != null && (!forBackup || UserHandle.getUserId(r.uid) == 0)) {
                boolean hasNonDefaultSettings = (r.importance == JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE && r.priority == 0) ? r.visibility != JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE : true;
                if (hasNonDefaultSettings) {
                    out.startTag(null, "package");
                    out.attribute(null, ATT_NAME, r.pkg);
                    if (r.importance != JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE) {
                        out.attribute(null, ATT_IMPORTANCE, Integer.toString(r.importance));
                    }
                    if (r.priority != 0) {
                        out.attribute(null, ATT_PRIORITY, Integer.toString(r.priority));
                    }
                    if (r.visibility != JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE) {
                        out.attribute(null, ATT_VISIBILITY, Integer.toString(r.visibility));
                    }
                    if (!forBackup) {
                        out.attribute(null, ATT_UID, Integer.toString(r.uid));
                    }
                    out.endTag(null, "package");
                }
            }
        }
        out.endTag(null, TAG_RANKING);
    }

    private void updateConfig() {
        for (NotificationSignalExtractor config : this.mSignalExtractors) {
            config.setConfig(this);
        }
        this.mRankingHandler.requestSort();
    }

    public void sort(ArrayList<NotificationRecord> notificationList) {
        int i;
        int N = notificationList.size();
        for (i = N - 1; i >= 0; i--) {
            ((NotificationRecord) notificationList.get(i)).setGlobalSortKey(null);
        }
        Collections.sort(notificationList, this.mPreliminaryComparator);
        synchronized (this.mProxyByGroupTmp) {
            for (i = N - 1; i >= 0; i--) {
                NotificationRecord record = (NotificationRecord) notificationList.get(i);
                record.setAuthoritativeRank(i);
                String groupKey = record.getGroupKey();
                if (record.getNotification().isGroupSummary() || !this.mProxyByGroupTmp.containsKey(groupKey)) {
                    this.mProxyByGroupTmp.put(groupKey, record);
                }
            }
            for (i = 0; i < N; i++) {
                String groupSortKeyPortion;
                record = (NotificationRecord) notificationList.get(i);
                NotificationRecord groupProxy = (NotificationRecord) this.mProxyByGroupTmp.get(record.getGroupKey());
                String groupSortKey = record.getNotification().getSortKey();
                if (groupSortKey == null) {
                    groupSortKeyPortion = "nsk";
                } else if (groupSortKey.equals("")) {
                    groupSortKeyPortion = "esk";
                } else {
                    groupSortKeyPortion = "gsk=" + groupSortKey;
                }
                boolean isGroupSummary = record.getNotification().isGroupSummary();
                String str = "intrsv=%c:grnk=0x%04x:gsmry=%c:%s:rnk=0x%04x";
                Object[] objArr = new Object[5];
                objArr[0] = Character.valueOf(record.isRecentlyIntrusive() ? '0' : '1');
                objArr[1] = Integer.valueOf(groupProxy.getAuthoritativeRank());
                objArr[2] = Character.valueOf(isGroupSummary ? '0' : '1');
                objArr[3] = groupSortKeyPortion;
                objArr[4] = Integer.valueOf(record.getAuthoritativeRank());
                record.setGlobalSortKey(String.format(str, objArr));
            }
            this.mProxyByGroupTmp.clear();
        }
        Collections.sort(notificationList, this.mFinalComparator);
    }

    public int indexOf(ArrayList<NotificationRecord> notificationList, NotificationRecord target) {
        return Collections.binarySearch(notificationList, target, this.mFinalComparator);
    }

    private static int safeInt(XmlPullParser parser, String att, int defValue) {
        return tryParseInt(parser.getAttributeValue(null, att), defValue);
    }

    private static int tryParseInt(String value, int defValue) {
        if (TextUtils.isEmpty(value)) {
            return defValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defValue;
        }
    }

    private static boolean tryParseBool(String value, boolean defValue) {
        if (TextUtils.isEmpty(value)) {
            return defValue;
        }
        return Boolean.valueOf(value).booleanValue();
    }

    public int getPriority(String packageName, int uid) {
        return getOrCreateRecord(packageName, uid).priority;
    }

    public void setPriority(String packageName, int uid, int priority) {
        getOrCreateRecord(packageName, uid).priority = priority;
        updateConfig();
    }

    public int getVisibilityOverride(String packageName, int uid) {
        return getOrCreateRecord(packageName, uid).visibility;
    }

    public void setVisibilityOverride(String pkgName, int uid, int visibility) {
        getOrCreateRecord(pkgName, uid).visibility = visibility;
        updateConfig();
    }

    public int getImportance(String packageName, int uid) {
        return getOrCreateRecord(packageName, uid).importance;
    }

    public void setImportance(String pkgName, int uid, int importance) {
        getOrCreateRecord(pkgName, uid).importance = importance;
        updateConfig();
    }

    public void setEnabled(String packageName, int uid, boolean enabled) {
        boolean wasEnabled;
        int i = 0;
        if (getImportance(packageName, uid) != 0) {
            wasEnabled = true;
        } else {
            wasEnabled = false;
        }
        if (wasEnabled != enabled) {
            if (enabled) {
                i = JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE;
            }
            setImportance(packageName, uid, i);
        }
    }

    public void dump(PrintWriter pw, String prefix, DumpFilter filter) {
        if (filter == null) {
            pw.print(prefix);
            pw.print("mSignalExtractors.length = ");
            pw.println(N);
            for (Object println : this.mSignalExtractors) {
                pw.print(prefix);
                pw.print("  ");
                pw.println(println);
            }
        }
        if (filter == null) {
            pw.print(prefix);
            pw.println("per-package config:");
        }
        pw.println("Records:");
        dumpRecords(pw, prefix, filter, this.mRecords);
        pw.println("Restored without uid:");
        dumpRecords(pw, prefix, filter, this.mRestoredWithoutUids);
    }

    private static void dumpRecords(PrintWriter pw, String prefix, DumpFilter filter, ArrayMap<String, Record> records) {
        int N = records.size();
        for (int i = 0; i < N; i++) {
            Record r = (Record) records.valueAt(i);
            if (filter == null || filter.matches(r.pkg)) {
                String str;
                pw.print(prefix);
                pw.print("  ");
                pw.print(r.pkg);
                pw.print(" (");
                if (r.uid == Record.UNKNOWN_UID) {
                    str = "UNKNOWN_UID";
                } else {
                    str = Integer.toString(r.uid);
                }
                pw.print(str);
                pw.print(')');
                if (r.importance != JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE) {
                    pw.print(" importance=");
                    pw.print(Ranking.importanceToString(r.importance));
                }
                if (r.priority != 0) {
                    pw.print(" priority=");
                    pw.print(Notification.priorityToString(r.priority));
                }
                if (r.visibility != JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE) {
                    pw.print(" visibility=");
                    pw.print(Notification.visibilityToString(r.visibility));
                }
                pw.println();
            }
        }
    }

    public JSONObject dumpJson(DumpFilter filter) {
        JSONObject ranking = new JSONObject();
        JSONArray records = new JSONArray();
        try {
            ranking.put("noUid", this.mRestoredWithoutUids.size());
        } catch (JSONException e) {
        }
        int N = this.mRecords.size();
        for (int i = 0; i < N; i++) {
            Record r = (Record) this.mRecords.valueAt(i);
            if (filter == null || filter.matches(r.pkg)) {
                JSONObject record = new JSONObject();
                try {
                    record.put("userId", UserHandle.getUserId(r.uid));
                    record.put("packageName", r.pkg);
                    if (r.importance != JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE) {
                        record.put(ATT_IMPORTANCE, Ranking.importanceToString(r.importance));
                    }
                    if (r.priority != 0) {
                        record.put(ATT_PRIORITY, Notification.priorityToString(r.priority));
                    }
                    if (r.visibility != JobSchedulerShellCommand.CMD_ERR_NO_PACKAGE) {
                        record.put(ATT_VISIBILITY, Notification.visibilityToString(r.visibility));
                    }
                } catch (JSONException e2) {
                }
                records.put(record);
            }
        }
        try {
            ranking.put("records", records);
        } catch (JSONException e3) {
        }
        return ranking;
    }

    public JSONArray dumpBansJson(DumpFilter filter) {
        JSONArray bans = new JSONArray();
        for (Entry<Integer, String> ban : getPackageBans().entrySet()) {
            int userId = UserHandle.getUserId(((Integer) ban.getKey()).intValue());
            String packageName = (String) ban.getValue();
            if (filter == null || filter.matches(packageName)) {
                JSONObject banJson = new JSONObject();
                try {
                    banJson.put("userId", userId);
                    banJson.put("packageName", packageName);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                bans.put(banJson);
            }
        }
        return bans;
    }

    public Map<Integer, String> getPackageBans() {
        int N = this.mRecords.size();
        ArrayMap<Integer, String> packageBans = new ArrayMap(N);
        for (int i = 0; i < N; i++) {
            Record r = (Record) this.mRecords.valueAt(i);
            if (r.importance == 0) {
                packageBans.put(Integer.valueOf(r.uid), r.pkg);
            }
        }
        return packageBans;
    }

    public void onPackagesChanged(boolean queryReplace, String[] pkgList) {
        if (!queryReplace && pkgList != null && pkgList.length != 0 && !this.mRestoredWithoutUids.isEmpty()) {
            PackageManager pm = this.mContext.getPackageManager();
            boolean updated = false;
            for (String pkg : pkgList) {
                Record r = (Record) this.mRestoredWithoutUids.get(pkg);
                if (r != null) {
                    try {
                        r.uid = pm.getPackageUidAsUser(r.pkg, 0);
                        this.mRestoredWithoutUids.remove(pkg);
                        this.mRecords.put(recordKey(r.pkg, r.uid), r);
                        updated = true;
                    } catch (NameNotFoundException e) {
                    }
                }
            }
            if (updated) {
                updateConfig();
            }
        }
    }
}
