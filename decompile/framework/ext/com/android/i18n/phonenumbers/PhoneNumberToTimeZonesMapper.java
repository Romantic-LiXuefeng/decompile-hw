package com.android.i18n.phonenumbers;

import com.android.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import com.android.i18n.phonenumbers.Phonenumber.PhoneNumber;
import com.android.i18n.phonenumbers.prefixmapper.PrefixTimeZonesMap;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PhoneNumberToTimeZonesMapper {
    private static final Logger LOGGER = Logger.getLogger(PhoneNumberToTimeZonesMapper.class.getName());
    private static final String MAPPING_DATA_DIRECTORY = "/com/android/i18n/phonenumbers/timezones/data/";
    private static final String MAPPING_DATA_FILE_NAME = "map_data";
    private static final String UNKNOWN_TIMEZONE = "Etc/Unknown";
    static final List<String> UNKNOWN_TIME_ZONE_LIST = new ArrayList(1);
    private PrefixTimeZonesMap prefixTimeZonesMap;

    private static class LazyHolder {
        private static final PhoneNumberToTimeZonesMapper INSTANCE = new PhoneNumberToTimeZonesMapper(PhoneNumberToTimeZonesMapper.loadPrefixTimeZonesMapFromFile("/com/android/i18n/phonenumbers/timezones/data/map_data"));

        private LazyHolder() {
        }
    }

    static {
        UNKNOWN_TIME_ZONE_LIST.add(UNKNOWN_TIMEZONE);
    }

    PhoneNumberToTimeZonesMapper(String prefixTimeZonesMapDataDirectory) {
        this.prefixTimeZonesMap = null;
        this.prefixTimeZonesMap = loadPrefixTimeZonesMapFromFile(prefixTimeZonesMapDataDirectory + MAPPING_DATA_FILE_NAME);
    }

    private PhoneNumberToTimeZonesMapper(PrefixTimeZonesMap prefixTimeZonesMap) {
        this.prefixTimeZonesMap = null;
        this.prefixTimeZonesMap = prefixTimeZonesMap;
    }

    private static PrefixTimeZonesMap loadPrefixTimeZonesMapFromFile(String path) {
        IOException e;
        Throwable th;
        InputStream source = PhoneNumberToTimeZonesMapper.class.getResourceAsStream(path);
        InputStream inputStream = null;
        PrefixTimeZonesMap map = new PrefixTimeZonesMap();
        try {
            InputStream in = new ObjectInputStream(source);
            try {
                map.readExternal(in);
                close(in);
                inputStream = in;
            } catch (IOException e2) {
                e = e2;
                inputStream = in;
                try {
                    LOGGER.log(Level.WARNING, e.toString());
                    close(inputStream);
                    return map;
                } catch (Throwable th2) {
                    th = th2;
                    close(inputStream);
                    throw th;
                }
            } catch (Throwable th3) {
                th = th3;
                inputStream = in;
                close(inputStream);
                throw th;
            }
        } catch (IOException e3) {
            e = e3;
            LOGGER.log(Level.WARNING, e.toString());
            close(inputStream);
            return map;
        }
        return map;
    }

    private static void close(InputStream in) {
        if (in != null) {
            try {
                in.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, e.toString());
            }
        }
    }

    public static synchronized PhoneNumberToTimeZonesMapper getInstance() {
        PhoneNumberToTimeZonesMapper -get0;
        synchronized (PhoneNumberToTimeZonesMapper.class) {
            -get0 = LazyHolder.INSTANCE;
        }
        return -get0;
    }

    public List<String> getTimeZonesForGeographicalNumber(PhoneNumber number) {
        return getTimeZonesForGeocodableNumber(number);
    }

    public List<String> getTimeZonesForNumber(PhoneNumber number) {
        PhoneNumberType numberType = PhoneNumberUtil.getInstance().getNumberType(number);
        if (numberType == PhoneNumberType.UNKNOWN) {
            return UNKNOWN_TIME_ZONE_LIST;
        }
        if (canBeGeocoded(numberType)) {
            return getTimeZonesForGeographicalNumber(number);
        }
        return getCountryLevelTimeZonesforNumber(number);
    }

    private boolean canBeGeocoded(PhoneNumberType numberType) {
        if (numberType == PhoneNumberType.FIXED_LINE || numberType == PhoneNumberType.MOBILE || numberType == PhoneNumberType.FIXED_LINE_OR_MOBILE) {
            return true;
        }
        return false;
    }

    public static String getUnknownTimeZone() {
        return UNKNOWN_TIMEZONE;
    }

    private List<String> getTimeZonesForGeocodableNumber(PhoneNumber number) {
        List<String> timezones = this.prefixTimeZonesMap.lookupTimeZonesForNumber(number);
        if (timezones.isEmpty()) {
            timezones = UNKNOWN_TIME_ZONE_LIST;
        }
        return Collections.unmodifiableList(timezones);
    }

    private List<String> getCountryLevelTimeZonesforNumber(PhoneNumber number) {
        List<String> timezones = this.prefixTimeZonesMap.lookupCountryLevelTimeZonesForNumber(number);
        if (timezones.isEmpty()) {
            timezones = UNKNOWN_TIME_ZONE_LIST;
        }
        return Collections.unmodifiableList(timezones);
    }
}
