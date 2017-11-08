package com.android.internal.telephony.msim;

import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.HwTelephony.NumMatchs;
import android.telephony.Rlog;
import android.text.TextUtils;
import com.android.internal.telephony.HwIccProviderUtils;
import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.HwAdnRecordCache;
import com.android.internal.telephony.uicc.HwIccUtils;
import huawei.android.telephony.wrapper.IIccPhoneBookMSimWrapper;
import huawei.android.telephony.wrapper.OptWrapperFactory;
import huawei.android.telephony.wrapper.WrapperFactory;
import java.util.List;

public class HwMSimIccProviderUtils extends HwIccProviderUtils {
    static final String[] ADDRESS_BOOK_COLUMN_NAMES = new String[]{NumMatchs.NAME, "number", "emails", "efid", "index", "_id"};
    private static final int ADN_ALL = 8;
    private static final int ADN_SUB1 = 1;
    private static final int ADN_SUB2 = 2;
    private static final int ADN_SUB3 = 3;
    private static final boolean DBG = true;
    private static final int FDN_SUB1 = 4;
    private static final int FDN_SUB2 = 5;
    private static final int FDN_SUB3 = 6;
    private static final int SDN = 7;
    private static final String TAG = "HwMSimIccProvider";
    private static final UriMatcher URL_MATCHER = new UriMatcher(-1);
    private static volatile HwMSimIccProviderUtils instance = null;

    static {
        URL_MATCHER.addURI("iccmsim", "adn", 1);
        URL_MATCHER.addURI("iccmsim", "adn_sub2", 2);
        URL_MATCHER.addURI("iccmsim", "adn_sub3", 3);
        URL_MATCHER.addURI("iccmsim", "adn_all", 8);
        URL_MATCHER.addURI("iccmsim", "fdn", 4);
        URL_MATCHER.addURI("iccmsim", "fdn_sub2", 5);
        URL_MATCHER.addURI("iccmsim", "fdn_sub3", 6);
        URL_MATCHER.addURI("iccmsim", "sdn", 7);
    }

    private HwMSimIccProviderUtils(Context context) {
        super(context);
        this.mContext = context;
    }

    public static HwMSimIccProviderUtils getDefault(Context context) {
        if (instance == null) {
            instance = new HwMSimIccProviderUtils(context);
        }
        return instance;
    }

    public Cursor query(Uri url, String[] projection, String selection, String[] selectionArgs, String sort) {
        debugLog("begin query");
        boolean isQuerybyindex = false;
        AdnRecord searchAdn = new AdnRecord("", "");
        int efid = 0;
        int index = 0;
        if (selection != null) {
            String tag = "";
            String number = "";
            String[] parementers = initParameter(selection, true);
            tag = parementers[0];
            number = parementers[1];
            String sEfid = parementers[3];
            String sIndex = parementers[4];
            if (!(sEfid == null || sIndex == null)) {
                efid = Integer.parseInt(sEfid);
                index = Integer.parseInt(sIndex);
                isQuerybyindex = true;
            }
            searchAdn = new AdnRecord(efid, index, tag, number);
            Rlog.w("SimProvider", "query tag=" + tag + ",number = xxxxxx  ,efid = " + efid + " ,index = " + index);
        }
        return getQueryResult(url, isQuerybyindex, searchAdn);
    }

    private String[] initParameter(String where, boolean isQuery) {
        String tag;
        String str;
        String pin2 = null;
        String sEfid = null;
        String sIndex = null;
        if (isQuery) {
            tag = "";
            str = "";
        } else {
            tag = null;
            str = null;
        }
        String[] tokens = where.split("AND");
        int n = tokens.length;
        while (true) {
            n--;
            if (n >= 0) {
                String param = tokens[n];
                log("parsing '" + param + "'");
                String[] pair = param.split("=");
                if (pair.length != 2) {
                    Rlog.e(TAG, "resolve: bad whereClause parameter: " + param);
                } else {
                    String key = pair[0].trim();
                    String val = pair[1].trim();
                    if ("tag".equals(key)) {
                        tag = normalizeValue(val);
                    } else if ("number".equals(key)) {
                        str = normalizeValue(val);
                    } else if ("pin2".equals(key)) {
                        pin2 = normalizeValue(val);
                    } else if ("efid".equals(key)) {
                        sEfid = normalizeValue(val);
                    } else if ("index".equals(key)) {
                        sIndex = normalizeValue(val);
                    }
                }
            } else {
                return new String[]{tag, str, pin2, sEfid, sIndex};
            }
        }
    }

    private MatrixCursor getQueryResult(Uri url, boolean isQuerybyindex, AdnRecord searchAdn) {
        debugLog("isQuerybyindex = " + isQuerybyindex + "; subscription = " + -1);
        int subscription;
        switch (URL_MATCHER.match(url)) {
            case 1:
                debugLog("case ADN_SUB1");
                subscription = WrapperFactory.getHuaweiTelephonyManagerWrapper().getSubidFromSlotId(0);
                if (subscription == -1) {
                    return new MatrixCursor(ADDRESS_BOOK_COLUMN_NAMES);
                }
                if (isQuerybyindex) {
                    return loadFromEf(28474, searchAdn, subscription);
                }
                return loadFromEf(28474, subscription);
            case 2:
                debugLog("case ADN_SUB2");
                subscription = WrapperFactory.getHuaweiTelephonyManagerWrapper().getSubidFromSlotId(1);
                if (subscription == -1) {
                    return new MatrixCursor(ADDRESS_BOOK_COLUMN_NAMES);
                }
                if (isQuerybyindex) {
                    return loadFromEf(28474, searchAdn, subscription);
                }
                return loadFromEf(28474, subscription);
            case 3:
                debugLog("case ADN_SUB3");
                return loadFromEf(28474, 2);
            case 4:
                debugLog("case FDN_SUB1");
                subscription = WrapperFactory.getHuaweiTelephonyManagerWrapper().getSubidFromSlotId(0);
                if (subscription == -1) {
                    return new MatrixCursor(ADDRESS_BOOK_COLUMN_NAMES);
                }
                return loadFromEf(28475, subscription);
            case 5:
                debugLog("case FDN_SUB2");
                subscription = WrapperFactory.getHuaweiTelephonyManagerWrapper().getSubidFromSlotId(1);
                if (subscription == -1) {
                    return new MatrixCursor(ADDRESS_BOOK_COLUMN_NAMES);
                }
                return loadFromEf(28475, subscription);
            case 6:
                debugLog("case FDN_SUB3");
                return loadFromEf(28475, 2);
            case 7:
                debugLog("case SDN");
                return loadFromEf(28489, WrapperFactory.getMSimTelephonyManagerWrapper().getDefaultSubscription());
            default:
                debugLog("case default");
                throw new IllegalArgumentException("Unknown URL " + url);
        }
    }

    private void debugLog(String logStr) {
        log(logStr);
    }

    public String getType(Uri url) {
        switch (URL_MATCHER.match(url)) {
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
                return "vnd.android.cursor.dir/sim-contact";
            default:
                throw new IllegalArgumentException("Unknown URL " + url);
        }
    }

    public Uri insert(Uri url, ContentValues initialValues) {
        int efType;
        String pin2 = null;
        int subscription = 0;
        log("insert");
        int match = URL_MATCHER.match(url);
        switch (match) {
            case 1:
                subscription = 0;
                efType = 28474;
                break;
            case 2:
                subscription = 1;
                efType = 28474;
                break;
            case 3:
                subscription = 2;
                efType = 28474;
                break;
            case 4:
            case 5:
            case 6:
                efType = 28475;
                pin2 = initialValues.getAsString("pin2");
                Integer temp = initialValues.getAsInteger("subscription");
                if (temp != null) {
                    subscription = temp.intValue();
                    break;
                }
                break;
            default:
                log("insert match unknow url");
                throw new UnsupportedOperationException("Cannot insert into URL: " + url);
        }
        String tag = initialValues.getAsString("tag");
        if (tag == null) {
            tag = "";
        }
        String number = initialValues.getAsString("number");
        if (number == null) {
            number = "";
        }
        log("insert before getSubidFromSlotId subscription = " + subscription);
        log("insert before getSubidFromSlotId match = " + match);
        if (1 == match || 4 == match) {
            WrapperFactory.getHuaweiTelephonyManagerWrapper().getSubidFromSlotId(0);
            if (subscription == -1) {
                return null;
            }
        } else if (2 == match || 5 == match) {
            WrapperFactory.getHuaweiTelephonyManagerWrapper().getSubidFromSlotId(1);
            if (subscription == -1) {
                return null;
            }
        }
        log("insert after getSubidFromSlotId subscription = " + subscription);
        if (!addIccRecordToEf(efType, tag, number, null, pin2, subscription)) {
            return null;
        }
        StringBuilder buf = new StringBuilder("content://iccmsim/");
        switch (match) {
            case 1:
                buf.append("adn/");
                break;
            case 2:
                buf.append("adn_sub2/");
                break;
            case 3:
                buf.append("adn_sub3/");
                break;
            case 4:
                buf.append("fdn/");
                break;
            case 5:
                buf.append("fdn_sub2/");
                break;
            case 6:
                buf.append("fdn_sub3/");
                break;
        }
        buf.append(HwAdnRecordCache.s_index.get()).append("/");
        buf.append(HwAdnRecordCache.s_efid.get());
        log("returned string:" + buf.toString());
        Uri resultUri = Uri.parse(buf.toString());
        getContext().getContentResolver().notifyChange(url, null);
        return resultUri;
    }

    public int delete(Uri url, String where, String[] whereArgs) {
        int subscription;
        int efType;
        boolean notNeedDelete;
        debugLog("delete");
        int match = URL_MATCHER.match(url);
        switch (match) {
            case 1:
                subscription = 0;
                efType = 28474;
                break;
            case 2:
                subscription = 1;
                efType = 28474;
                break;
            case 3:
                subscription = 2;
                efType = 28474;
                break;
            case 4:
                subscription = 0;
                efType = 28475;
                break;
            case 5:
                subscription = 1;
                efType = 28475;
                break;
            case 6:
                subscription = 2;
                efType = 28475;
                break;
            default:
                throw new UnsupportedOperationException("Cannot insert into URL: " + url);
        }
        String[] parementers = initParameter(where, true);
        String tag = parementers[0];
        String number = parementers[1];
        String pin2 = parementers[2];
        String sEfid = parementers[3];
        String sIndex = parementers[4];
        if (efType == 4 || efType == 5 || efType == 6) {
            notNeedDelete = TextUtils.isEmpty(pin2);
        } else {
            notNeedDelete = false;
        }
        if (notNeedDelete) {
            return 0;
        }
        boolean matchSUB1 = 1 == match || 4 == match;
        boolean matchSUB2 = 2 == match || 5 == match;
        if (matchSUB1) {
            WrapperFactory.getHuaweiTelephonyManagerWrapper().getSubidFromSlotId(0);
            if (subscription == -1) {
                return 0;
            }
        } else if (matchSUB2) {
            WrapperFactory.getHuaweiTelephonyManagerWrapper().getSubidFromSlotId(1);
            if (subscription == -1) {
                return 0;
            }
        }
        boolean success = processDeleteIccRecord(efType, subscription, 0, tag, number, pin2, sEfid, sIndex, null);
        debugLog("sEfid=" + sEfid + ";sIndex=" + sIndex + ";efType=" + efType + ";index" + 0);
        if (!success) {
            return 0;
        }
        getContext().getContentResolver().notifyChange(url, null);
        return 1;
    }

    private boolean processDeleteIccRecord(int efType, int subscription, int index, String tag, String number, String pin2, String sEfid, String sIndex, String[] emails) {
        boolean efidAndIndexEmpty;
        if (sEfid != null) {
            if (!sEfid.equals("")) {
                efidAndIndexEmpty = false;
                if (efidAndIndexEmpty) {
                    return deleteIccRecordFromEf(efType, tag, number, emails, pin2, subscription);
                }
                efType = Integer.parseInt(sEfid);
                index = Integer.parseInt(sIndex);
                if (index <= 0) {
                    return deleteIccRecordFromEfByIndex(efType, index, emails, pin2, subscription);
                }
                return false;
            }
        }
        if (sIndex != null) {
            efidAndIndexEmpty = sIndex.equals("");
        } else {
            efidAndIndexEmpty = true;
        }
        if (efidAndIndexEmpty) {
            return deleteIccRecordFromEf(efType, tag, number, emails, pin2, subscription);
        }
        efType = Integer.parseInt(sEfid);
        index = Integer.parseInt(sIndex);
        if (index <= 0) {
            return false;
        }
        return deleteIccRecordFromEfByIndex(efType, index, emails, pin2, subscription);
    }

    public int update(Uri url, ContentValues values, String where, String[] whereArgs) {
        int subscription;
        int efType;
        boolean efidAndIndexEmpty;
        String pin2 = null;
        debugLog("update");
        int match = URL_MATCHER.match(url);
        switch (match) {
            case 1:
                subscription = 0;
                efType = 28474;
                break;
            case 2:
                subscription = 1;
                efType = 28474;
                break;
            case 3:
                subscription = 2;
                efType = 28474;
                break;
            case 4:
            case 5:
            case 6:
                efType = 28475;
                pin2 = values.getAsString("pin2");
                subscription = values.getAsInteger("subscription").intValue();
                break;
            default:
                throw new UnsupportedOperationException("Cannot insert into URL: " + url);
        }
        String tag = values.getAsString("tag");
        String number = values.getAsString("number");
        String newTag = values.getAsString("newTag");
        String newNumber = values.getAsString("newNumber");
        if (newTag == null) {
            newTag = "";
        }
        if (newNumber == null) {
            newNumber = "";
        }
        String Efid = values.getAsString("efid");
        String sIndex = values.getAsString("index");
        int index = 0;
        boolean z = false;
        boolean matchSUB1 = 1 == match || 4 == match;
        boolean matchSUB2 = 2 == match || 5 == match;
        if (matchSUB1) {
            WrapperFactory.getHuaweiTelephonyManagerWrapper().getSubidFromSlotId(0);
            if (subscription == -1) {
                return 0;
            }
        } else if (matchSUB2) {
            WrapperFactory.getHuaweiTelephonyManagerWrapper().getSubidFromSlotId(1);
            if (subscription == -1) {
                return 0;
            }
        }
        if (Efid != null) {
            if (!Efid.equals("")) {
                efidAndIndexEmpty = false;
                if (efidAndIndexEmpty) {
                    efType = Integer.parseInt(Efid);
                    index = Integer.parseInt(sIndex);
                    if (index > 0) {
                        z = updateIccRecordInEfByIndex(efType, index, newTag, newNumber, pin2, subscription);
                    }
                } else {
                    z = updateIccRecordInEf(efType, tag, number, newTag, newNumber, pin2, subscription);
                }
                debugLog("update: Efid=" + Efid + ";sIndex=" + sIndex + ";efType=" + efType + ";index=" + index + ";subscription=" + subscription);
                if (!z) {
                    return 0;
                }
                getContext().getContentResolver().notifyChange(url, null);
                return 1;
            }
        }
        efidAndIndexEmpty = sIndex != null ? sIndex.equals("") : true;
        if (efidAndIndexEmpty) {
            efType = Integer.parseInt(Efid);
            index = Integer.parseInt(sIndex);
            if (index > 0) {
                z = updateIccRecordInEfByIndex(efType, index, newTag, newNumber, pin2, subscription);
            }
        } else {
            z = updateIccRecordInEf(efType, tag, number, newTag, newNumber, pin2, subscription);
        }
        debugLog("update: Efid=" + Efid + ";sIndex=" + sIndex + ";efType=" + efType + ";index=" + index + ";subscription=" + subscription);
        if (!z) {
            return 0;
        }
        getContext().getContentResolver().notifyChange(url, null);
        return 1;
    }

    private MatrixCursor loadFromEf(int efType, int subscription) {
        List adnRecords = null;
        log("loadFromEf: efType=" + efType + "subscription = " + subscription);
        try {
            IIccPhoneBookMSimWrapper iccIpb = OptWrapperFactory.getIIccPhoneBookMSimWrapper();
            if (iccIpb != null) {
                adnRecords = iccIpb.getAdnRecordsInEf(efType, subscription);
            }
        } catch (RemoteException e) {
        } catch (SecurityException ex) {
            log(ex.toString());
        }
        if (adnRecords != null) {
            int N = adnRecords.size();
            MatrixCursor cursor = new MatrixCursor(ADDRESS_BOOK_COLUMN_NAMES, N);
            log("adnRecords.size=" + N);
            for (int i = 0; i < N; i++) {
                loadRecord((AdnRecord) adnRecords.get(i), cursor, i);
            }
            return cursor;
        }
        Rlog.w(TAG, "Cannot load ADN records");
        return new MatrixCursor(ADDRESS_BOOK_COLUMN_NAMES);
    }

    protected MatrixCursor loadFromEf(int efType, AdnRecord searchAdn, int subscription) {
        List adnRecords = null;
        log("loadFromEf: efType=" + efType + "subscription = " + subscription);
        try {
            IIccPhoneBookMSimWrapper iccIpb = OptWrapperFactory.getIIccPhoneBookMSimWrapper();
            if (iccIpb != null) {
                adnRecords = iccIpb.getAdnRecordsInEf(efType, subscription);
            }
        } catch (RemoteException e) {
        } catch (SecurityException ex) {
            log(ex.toString());
        }
        if (adnRecords != null) {
            int N = adnRecords.size();
            MatrixCursor cursor = new MatrixCursor(ADDRESS_BOOK_COLUMN_NAMES, N);
            log("adnRecords.size=" + N);
            for (int i = 0; i < N; i++) {
                if (HwIccUtils.equalAdn(searchAdn, (AdnRecord) adnRecords.get(i))) {
                    Rlog.w(TAG, "have one by efid and index");
                    loadRecord((AdnRecord) adnRecords.get(i), cursor, i);
                    break;
                }
            }
            return cursor;
        }
        Rlog.w(TAG, "Cannot load ADN records");
        return new MatrixCursor(ADDRESS_BOOK_COLUMN_NAMES);
    }

    private boolean addIccRecordToEf(int efType, String name, String number, String[] emails, String pin2, int subscription) {
        log("addIccRecordToEf: efType=" + efType + ", name=" + name + ", number=" + number + ", emails=" + "array" + ", subscription=" + subscription);
        boolean success = false;
        try {
            IIccPhoneBookMSimWrapper iccIpb = OptWrapperFactory.getIIccPhoneBookMSimWrapper();
            if (iccIpb != null) {
                success = iccIpb.updateAdnRecordsInEfBySearch(efType, "", "", name, number, pin2, subscription);
            }
        } catch (RemoteException e) {
        } catch (SecurityException ex) {
            log(ex.toString());
        }
        log("addIccRecordToEf: " + success);
        return success;
    }

    private boolean updateIccRecordInEf(int efType, String oldName, String oldNumber, String newName, String newNumber, String pin2, int subscription) {
        log("updateIccRecordInEf: efType=" + efType + ", oldname=" + oldName + ", oldnumber=" + oldNumber + ", newname=" + newName + ", newnumber=" + newNumber + ", subscription=" + subscription);
        boolean success = false;
        try {
            IIccPhoneBookMSimWrapper iccIpb = OptWrapperFactory.getIIccPhoneBookMSimWrapper();
            if (iccIpb != null) {
                success = iccIpb.updateAdnRecordsInEfBySearch(efType, oldName, oldNumber, newName, newNumber, pin2, subscription);
            }
        } catch (RemoteException e) {
        } catch (SecurityException ex) {
            log(ex.toString());
        }
        log("updateIccRecordInEf: " + success);
        return success;
    }

    private boolean deleteIccRecordFromEf(int efType, String name, String number, String[] emails, String pin2, int subscription) {
        log("deleteIccRecordFromEf: efType=" + efType + ", name=" + name + ", number=" + number + ", emails=" + "array" + ", pin2=" + pin2 + ", subscription=" + subscription);
        boolean success = false;
        try {
            IIccPhoneBookMSimWrapper iccIpb = OptWrapperFactory.getIIccPhoneBookMSimWrapper();
            if (iccIpb != null) {
                success = iccIpb.updateAdnRecordsInEfBySearch(efType, name, number, "", "", pin2, subscription);
            }
        } catch (RemoteException e) {
        } catch (SecurityException ex) {
            log(ex.toString());
        }
        log("deleteIccRecordFromEf: " + success);
        return success;
    }

    private boolean updateIccRecordInEfByIndex(int efType, int index, String newName, String newNumber, String pin2, int subscription) {
        log("updateIccRecordInEfByIndex: efType=" + efType + ", index=" + index + ", newname=" + newName + ", newnumber=" + newNumber + ", subscription=" + subscription);
        boolean success = false;
        try {
            IIccPhoneBookMSimWrapper iccIpb = OptWrapperFactory.getIIccPhoneBookMSimWrapper();
            if (iccIpb != null) {
                success = iccIpb.updateAdnRecordsInEfByIndex(efType, newName, newNumber, index, pin2, subscription);
            }
        } catch (RemoteException e) {
        } catch (SecurityException ex) {
            log(ex.toString());
        }
        log("updateIccRecordInEfByIndex: " + success);
        return success;
    }

    private boolean deleteIccRecordFromEfByIndex(int efType, int index, String[] emails, String pin2, int subscription) {
        log("deleteIccRecordFromEfByIndex: efType=" + efType + ", index=" + index + ", emails=" + "array" + ", pin2=" + pin2 + ", subscription=" + subscription);
        boolean success = false;
        try {
            IIccPhoneBookMSimWrapper iccIpb = OptWrapperFactory.getIIccPhoneBookMSimWrapper();
            if (iccIpb != null) {
                success = iccIpb.updateAdnRecordsInEfByIndex(efType, "", "", index, pin2, subscription);
            }
        } catch (RemoteException e) {
        } catch (SecurityException ex) {
            log(ex.toString());
        }
        log("deleteIccRecordFromEfByIndex: " + success);
        return success;
    }

    protected void log(String msg) {
        Rlog.d(TAG, "[MSimIccProvider] " + msg);
    }
}
