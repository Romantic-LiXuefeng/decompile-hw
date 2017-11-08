package android.icu.util;

import android.icu.impl.Grego;
import android.icu.impl.ICUConfig;
import android.icu.impl.ICUResourceBundle;
import android.icu.impl.JavaTimeZone;
import android.icu.impl.OlsonTimeZone;
import android.icu.impl.TimeZoneAdapter;
import android.icu.impl.ZoneMeta;
import android.icu.text.TimeZoneFormat;
import android.icu.text.TimeZoneFormat.Style;
import android.icu.text.TimeZoneFormat.TimeType;
import android.icu.text.TimeZoneNames;
import android.icu.text.TimeZoneNames.NameType;
import android.icu.util.ULocale.Category;
import java.io.Serializable;
import java.util.Date;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.logging.Logger;

public abstract class TimeZone implements Serializable, Cloneable, Freezable<TimeZone> {
    static final /* synthetic */ boolean -assertionsDisabled;
    public static final int GENERIC_LOCATION = 7;
    public static final TimeZone GMT_ZONE = new ConstantZone(0, GMT_ZONE_ID).freeze();
    static final String GMT_ZONE_ID = "Etc/GMT";
    private static final Logger LOGGER = Logger.getLogger("android.icu.util.TimeZone");
    public static final int LONG = 1;
    public static final int LONG_GENERIC = 3;
    public static final int LONG_GMT = 5;
    public static final int SHORT = 0;
    public static final int SHORT_COMMONLY_USED = 6;
    public static final int SHORT_GENERIC = 2;
    public static final int SHORT_GMT = 4;
    public static final int TIMEZONE_ICU = 0;
    public static final int TIMEZONE_JDK = 1;
    private static final String TZIMPL_CONFIG_ICU = "ICU";
    private static final String TZIMPL_CONFIG_JDK = "JDK";
    private static final String TZIMPL_CONFIG_KEY = "android.icu.util.TimeZone.DefaultTimeZoneType";
    private static int TZ_IMPL = 0;
    public static final TimeZone UNKNOWN_ZONE = new ConstantZone(0, UNKNOWN_ZONE_ID).freeze();
    public static final String UNKNOWN_ZONE_ID = "Etc/Unknown";
    private static volatile TimeZone defaultZone = null;
    private static final long serialVersionUID = -744942128318337471L;
    private String ID;

    private static final class ConstantZone extends TimeZone {
        private static final long serialVersionUID = 1;
        private volatile transient boolean isFrozen;
        private int rawOffset;

        private ConstantZone(int rawOffset, String ID) {
            super(ID);
            this.isFrozen = false;
            this.rawOffset = rawOffset;
        }

        public int getOffset(int era, int year, int month, int day, int dayOfWeek, int milliseconds) {
            return this.rawOffset;
        }

        public void setRawOffset(int offsetMillis) {
            if (isFrozen()) {
                throw new UnsupportedOperationException("Attempt to modify a frozen TimeZone instance.");
            }
            this.rawOffset = offsetMillis;
        }

        public int getRawOffset() {
            return this.rawOffset;
        }

        public boolean useDaylightTime() {
            return false;
        }

        public boolean inDaylightTime(Date date) {
            return false;
        }

        public boolean isFrozen() {
            return this.isFrozen;
        }

        public TimeZone freeze() {
            this.isFrozen = true;
            return this;
        }

        public TimeZone cloneAsThawed() {
            ConstantZone tz = (ConstantZone) super.cloneAsThawed();
            tz.isFrozen = false;
            return tz;
        }
    }

    public enum SystemTimeZoneType {
        ANY,
        CANONICAL,
        CANONICAL_LOCATION
    }

    public abstract int getOffset(int i, int i2, int i3, int i4, int i5, int i6);

    public abstract int getRawOffset();

    public abstract boolean inDaylightTime(Date date);

    public abstract void setRawOffset(int i);

    public abstract boolean useDaylightTime();

    static {
        boolean z;
        if (TimeZone.class.desiredAssertionStatus()) {
            z = false;
        } else {
            z = true;
        }
        -assertionsDisabled = z;
        TZ_IMPL = 0;
        if (ICUConfig.get(TZIMPL_CONFIG_KEY, TZIMPL_CONFIG_ICU).equalsIgnoreCase(TZIMPL_CONFIG_JDK)) {
            TZ_IMPL = 1;
        }
    }

    @Deprecated
    protected TimeZone(String ID) {
        if (ID == null) {
            throw new NullPointerException();
        }
        this.ID = ID;
    }

    public int getOffset(long date) {
        int[] result = new int[2];
        getOffset(date, false, result);
        return result[0] + result[1];
    }

    public void getOffset(long date, boolean local, int[] offsets) {
        offsets[0] = getRawOffset();
        if (!local) {
            date += (long) offsets[0];
        }
        int[] fields = new int[6];
        int pass = 0;
        while (true) {
            Grego.timeToFields(date, fields);
            offsets[1] = getOffset(1, fields[0], fields[1], fields[2], fields[3], fields[5]) - offsets[0];
            if (pass == 0 && local && offsets[1] != 0) {
                date -= (long) offsets[1];
                pass++;
            } else {
                return;
            }
        }
    }

    public String getID() {
        return this.ID;
    }

    public void setID(String ID) {
        if (ID == null) {
            throw new NullPointerException();
        } else if (isFrozen()) {
            throw new UnsupportedOperationException("Attempt to modify a frozen TimeZone instance.");
        } else {
            this.ID = ID;
        }
    }

    public final String getDisplayName() {
        return _getDisplayName(3, false, ULocale.getDefault(Category.DISPLAY));
    }

    public final String getDisplayName(Locale locale) {
        return _getDisplayName(3, false, ULocale.forLocale(locale));
    }

    public final String getDisplayName(ULocale locale) {
        return _getDisplayName(3, false, locale);
    }

    public final String getDisplayName(boolean daylight, int style) {
        return getDisplayName(daylight, style, ULocale.getDefault(Category.DISPLAY));
    }

    public String getDisplayName(boolean daylight, int style, Locale locale) {
        return getDisplayName(daylight, style, ULocale.forLocale(locale));
    }

    public String getDisplayName(boolean daylight, int style, ULocale locale) {
        if (style >= 0 && style <= 7) {
            return _getDisplayName(style, daylight, locale);
        }
        throw new IllegalArgumentException("Illegal style: " + style);
    }

    private String _getDisplayName(int style, boolean daylight, ULocale locale) {
        if (locale == null) {
            throw new NullPointerException("locale is null");
        }
        Object obj;
        String str = null;
        TimeZoneFormat tzfmt;
        long date;
        int offset;
        if (style == 7 || style == 3 || style == 2) {
            tzfmt = TimeZoneFormat.getInstance(locale);
            date = System.currentTimeMillis();
            Output<TimeType> timeType = new Output(TimeType.UNKNOWN);
            switch (style) {
                case 2:
                    str = tzfmt.format(Style.GENERIC_SHORT, this, date, timeType);
                    break;
                case 3:
                    str = tzfmt.format(Style.GENERIC_LONG, this, date, timeType);
                    break;
                case 7:
                    str = tzfmt.format(Style.GENERIC_LOCATION, this, date, timeType);
                    break;
            }
            if (!(daylight && timeType.value == TimeType.STANDARD)) {
                if (!daylight && timeType.value == TimeType.DAYLIGHT) {
                }
            }
            offset = daylight ? getRawOffset() + getDSTSavings() : getRawOffset();
            str = style == 2 ? tzfmt.formatOffsetShortLocalizedGMT(offset) : tzfmt.formatOffsetLocalizedGMT(offset);
        } else if (style == 5 || style == 4) {
            tzfmt = TimeZoneFormat.getInstance(locale);
            offset = (daylight && useDaylightTime()) ? getRawOffset() + getDSTSavings() : getRawOffset();
            switch (style) {
                case 4:
                    str = tzfmt.formatOffsetISO8601Basic(offset, false, false, false);
                    break;
                case 5:
                    str = tzfmt.formatOffsetLocalizedGMT(offset);
                    break;
                default:
                    break;
            }
        } else {
            if (!-assertionsDisabled) {
                obj = (style == 1 || style == 0 || style == 6) ? 1 : null;
                if (obj == null) {
                    throw new AssertionError();
                }
            }
            date = System.currentTimeMillis();
            TimeZoneNames tznames = TimeZoneNames.getInstance(locale);
            NameType nameType = null;
            switch (style) {
                case 0:
                case 6:
                    if (!daylight) {
                        nameType = NameType.SHORT_STANDARD;
                        break;
                    }
                    nameType = NameType.SHORT_DAYLIGHT;
                    break;
                case 1:
                    if (!daylight) {
                        nameType = NameType.LONG_STANDARD;
                        break;
                    }
                    nameType = NameType.LONG_DAYLIGHT;
                    break;
            }
            str = tznames.getDisplayName(ZoneMeta.getCanonicalCLDRID(this), nameType, date);
            if (str == null) {
                tzfmt = TimeZoneFormat.getInstance(locale);
                offset = (daylight && useDaylightTime()) ? getRawOffset() + getDSTSavings() : getRawOffset();
                str = style == 1 ? tzfmt.formatOffsetLocalizedGMT(offset) : tzfmt.formatOffsetShortLocalizedGMT(offset);
            }
        }
        if (!-assertionsDisabled) {
            if (str != null) {
                obj = 1;
            } else {
                obj = null;
            }
            if (obj == null) {
                throw new AssertionError();
            }
        }
        return str;
    }

    public int getDSTSavings() {
        if (useDaylightTime()) {
            return 3600000;
        }
        return 0;
    }

    public boolean observesDaylightTime() {
        return !useDaylightTime() ? inDaylightTime(new Date()) : true;
    }

    public static TimeZone getTimeZone(String ID) {
        return getTimeZone(ID, TZ_IMPL, false);
    }

    public static TimeZone getFrozenTimeZone(String ID) {
        return getTimeZone(ID, TZ_IMPL, true);
    }

    public static TimeZone getTimeZone(String ID, int type) {
        return getTimeZone(ID, type, false);
    }

    private static TimeZone getTimeZone(String ID, int type, boolean frozen) {
        TimeZone result;
        if (type == 1) {
            result = JavaTimeZone.createTimeZone(ID);
            if (result != null) {
                if (frozen) {
                    result = result.freeze();
                }
                return result;
            }
        } else if (ID == null) {
            throw new NullPointerException();
        } else {
            result = ZoneMeta.getSystemTimeZone(ID);
        }
        if (result == null) {
            result = ZoneMeta.getCustomTimeZone(ID);
        }
        if (result == null) {
            LOGGER.fine("\"" + ID + "\" is a bogus id so timezone is falling back to Etc/Unknown(GMT).");
            result = UNKNOWN_ZONE;
        }
        if (!frozen) {
            result = result.cloneAsThawed();
        }
        return result;
    }

    public static synchronized void setDefaultTimeZoneType(int type) {
        synchronized (TimeZone.class) {
            if (type == 0 || type == 1) {
                TZ_IMPL = type;
            } else {
                throw new IllegalArgumentException("Invalid timezone type");
            }
        }
    }

    public static int getDefaultTimeZoneType() {
        return TZ_IMPL;
    }

    public static Set<String> getAvailableIDs(SystemTimeZoneType zoneType, String region, Integer rawOffset) {
        return ZoneMeta.getAvailableIDs(zoneType, region, rawOffset);
    }

    public static String[] getAvailableIDs(int rawOffset) {
        return (String[]) getAvailableIDs(SystemTimeZoneType.ANY, null, Integer.valueOf(rawOffset)).toArray(new String[0]);
    }

    public static String[] getAvailableIDs(String country) {
        return (String[]) getAvailableIDs(SystemTimeZoneType.ANY, country, null).toArray(new String[0]);
    }

    public static String[] getAvailableIDs() {
        return (String[]) getAvailableIDs(SystemTimeZoneType.ANY, null, null).toArray(new String[0]);
    }

    public static int countEquivalentIDs(String id) {
        return ZoneMeta.countEquivalentIDs(id);
    }

    public static String getEquivalentID(String id, int index) {
        return ZoneMeta.getEquivalentID(id, index);
    }

    public static TimeZone getDefault() {
        if (defaultZone == null) {
            synchronized (TimeZone.class) {
                if (defaultZone == null) {
                    if (TZ_IMPL == 1) {
                        defaultZone = new JavaTimeZone();
                    } else {
                        defaultZone = getFrozenTimeZone(java.util.TimeZone.getDefault().getID());
                    }
                }
            }
        }
        return defaultZone.cloneAsThawed();
    }

    public static synchronized void clearCachedDefault() {
        synchronized (TimeZone.class) {
            defaultZone = null;
        }
    }

    public static synchronized void setDefault(TimeZone tz) {
        synchronized (TimeZone.class) {
            defaultZone = tz;
            java.util.TimeZone jdkZone = null;
            if (defaultZone instanceof JavaTimeZone) {
                jdkZone = ((JavaTimeZone) defaultZone).unwrap();
            } else if (tz != null) {
                if (tz instanceof OlsonTimeZone) {
                    String icuID = tz.getID();
                    jdkZone = java.util.TimeZone.getTimeZone(icuID);
                    if (!icuID.equals(jdkZone.getID())) {
                        icuID = getCanonicalID(icuID);
                        jdkZone = java.util.TimeZone.getTimeZone(icuID);
                        if (!icuID.equals(jdkZone.getID())) {
                            jdkZone = null;
                        }
                    }
                }
                if (jdkZone == null) {
                    jdkZone = TimeZoneAdapter.wrap(tz);
                }
            }
            java.util.TimeZone.setDefault(jdkZone);
        }
    }

    public boolean hasSameRules(TimeZone other) {
        if (other != null && getRawOffset() == other.getRawOffset() && useDaylightTime() == other.useDaylightTime()) {
            return true;
        }
        return false;
    }

    public Object clone() {
        if (isFrozen()) {
            return this;
        }
        return cloneAsThawed();
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        return this.ID.equals(((TimeZone) obj).ID);
    }

    public int hashCode() {
        return this.ID.hashCode();
    }

    public static String getTZDataVersion() {
        return VersionInfo.getTZDataVersion();
    }

    public static String getCanonicalID(String id) {
        return getCanonicalID(id, null);
    }

    public static String getCanonicalID(String id, boolean[] isSystemID) {
        String str = null;
        boolean systemTzid = false;
        if (!(id == null || id.length() == 0)) {
            if (id.equals(UNKNOWN_ZONE_ID)) {
                str = UNKNOWN_ZONE_ID;
                systemTzid = false;
            } else {
                str = ZoneMeta.getCanonicalCLDRID(id);
                if (str != null) {
                    systemTzid = true;
                } else {
                    str = ZoneMeta.getCustomID(id);
                }
            }
        }
        if (isSystemID != null) {
            isSystemID[0] = systemTzid;
        }
        return str;
    }

    public static String getRegion(String id) {
        String region = null;
        if (!id.equals(UNKNOWN_ZONE_ID)) {
            region = ZoneMeta.getRegion(id);
        }
        if (region != null) {
            return region;
        }
        throw new IllegalArgumentException("Unknown system zone id: " + id);
    }

    public static String getWindowsID(String id) {
        boolean[] isSystemID = new boolean[]{false};
        id = getCanonicalID(id, isSystemID);
        if (!isSystemID[0]) {
            return null;
        }
        UResourceBundleIterator resitr = UResourceBundle.getBundleInstance("android/icu/impl/data/icudt56b", "windowsZones", ICUResourceBundle.ICU_DATA_CLASS_LOADER).get("mapTimezones").getIterator();
        while (resitr.hasNext()) {
            UResourceBundle winzone = resitr.next();
            if (winzone.getType() == 2) {
                UResourceBundleIterator rgitr = winzone.getIterator();
                while (rgitr.hasNext()) {
                    UResourceBundle regionalData = rgitr.next();
                    if (regionalData.getType() == 0) {
                        for (String tzid : regionalData.getString().split(" ")) {
                            if (tzid.equals(id)) {
                                return winzone.getKey();
                            }
                        }
                        continue;
                    }
                }
                continue;
            }
        }
        return null;
    }

    public static String getIDForWindowsID(String winid, String region) {
        String str = null;
        try {
            UResourceBundle zones = UResourceBundle.getBundleInstance("android/icu/impl/data/icudt56b", "windowsZones", ICUResourceBundle.ICU_DATA_CLASS_LOADER).get("mapTimezones").get(winid);
            if (region != null) {
                try {
                    str = zones.getString(region);
                    if (str != null) {
                        int endIdx = str.indexOf(32);
                        if (endIdx > 0) {
                            str = str.substring(0, endIdx);
                        }
                    }
                } catch (MissingResourceException e) {
                }
            }
            if (str == null) {
                str = zones.getString("001");
            }
        } catch (MissingResourceException e2) {
        }
        return str;
    }

    public boolean isFrozen() {
        return false;
    }

    public TimeZone freeze() {
        throw new UnsupportedOperationException("Needs to be implemented by the subclass.");
    }

    public TimeZone cloneAsThawed() {
        try {
            return (TimeZone) super.clone();
        } catch (Throwable e) {
            throw new ICUCloneNotSupportedException(e);
        }
    }
}
