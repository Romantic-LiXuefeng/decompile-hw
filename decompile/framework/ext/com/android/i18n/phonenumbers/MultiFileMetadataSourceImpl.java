package com.android.i18n.phonenumbers;

import com.android.i18n.phonenumbers.Phonemetadata.PhoneMetadata;
import com.android.i18n.phonenumbers.Phonemetadata.PhoneMetadataCollection;
import com.google.i18n.phonenumbers.Phonemetadata;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

final class MultiFileMetadataSourceImpl implements MetadataSource {
    private static final String META_DATA_FILE_PREFIX = "/com/android/i18n/phonenumbers/data/PhoneNumberMetadataProto";
    private static final Logger logger = Logger.getLogger(MultiFileMetadataSourceImpl.class.getName());
    private final Map<Integer, PhoneMetadata> countryCodeToNonGeographicalMetadataMap;
    private final String currentFilePrefix;
    private final MetadataLoader metadataLoader;
    private final Map<String, PhoneMetadata> regionToMetadataMap;

    public MultiFileMetadataSourceImpl(String currentFilePrefix, MetadataLoader metadataLoader) {
        this.regionToMetadataMap = Collections.synchronizedMap(new HashMap());
        this.countryCodeToNonGeographicalMetadataMap = Collections.synchronizedMap(new HashMap());
        this.currentFilePrefix = currentFilePrefix;
        this.metadataLoader = metadataLoader;
    }

    public MultiFileMetadataSourceImpl(MetadataLoader metadataLoader) {
        this(META_DATA_FILE_PREFIX, metadataLoader);
    }

    public PhoneMetadata getMetadataForRegion(String regionCode) {
        synchronized (this.regionToMetadataMap) {
            if (!this.regionToMetadataMap.containsKey(regionCode)) {
                loadMetadataFromFile(this.currentFilePrefix, regionCode, 0, this.metadataLoader);
            }
        }
        return (PhoneMetadata) this.regionToMetadataMap.get(regionCode);
    }

    public PhoneMetadata getMetadataForNonGeographicalRegion(int countryCallingCode) {
        synchronized (this.countryCodeToNonGeographicalMetadataMap) {
            if (!this.countryCodeToNonGeographicalMetadataMap.containsKey(Integer.valueOf(countryCallingCode))) {
                loadMetadataFromFile(this.currentFilePrefix, PhoneNumberUtil.REGION_CODE_FOR_NON_GEO_ENTITY, countryCallingCode, this.metadataLoader);
            }
        }
        return (PhoneMetadata) this.countryCodeToNonGeographicalMetadataMap.get(Integer.valueOf(countryCallingCode));
    }

    void loadMetadataFromFile(String filePrefix, String regionCode, int countryCallingCode, MetadataLoader metadataLoader) {
        String valueOf;
        IOException e;
        boolean isNonGeoRegion = PhoneNumberUtil.REGION_CODE_FOR_NON_GEO_ENTITY.equals(regionCode);
        StringBuilder append = new StringBuilder().append(filePrefix).append("_");
        if (isNonGeoRegion) {
            valueOf = String.valueOf(countryCallingCode);
        } else {
            valueOf = regionCode;
        }
        String fileName = append.append(valueOf).toString();
        InputStream source = metadataLoader.loadMetadata(fileName);
        if (source == null) {
            logger.log(Level.SEVERE, "missing metadata: " + fileName);
            throw new IllegalStateException("missing metadata: " + fileName);
        }
        try {
            ObjectInputStream in = new ObjectInputStream(source);
            try {
                List<Phonemetadata.PhoneMetadata> metadataList = loadMetadataAndCloseInput(in).getMetadataList();
                if (metadataList.isEmpty()) {
                    logger.log(Level.SEVERE, "empty metadata: " + fileName);
                    throw new IllegalStateException("empty metadata: " + fileName);
                }
                if (metadataList.size() > 1) {
                    logger.log(Level.WARNING, "invalid metadata (too many entries): " + fileName);
                }
                PhoneMetadata metadata = (PhoneMetadata) metadataList.get(0);
                if (isNonGeoRegion) {
                    this.countryCodeToNonGeographicalMetadataMap.put(Integer.valueOf(countryCallingCode), metadata);
                } else {
                    this.regionToMetadataMap.put(regionCode, metadata);
                }
            } catch (IOException e2) {
                e = e2;
                ObjectInputStream objectInputStream = in;
                logger.log(Level.SEVERE, "cannot load/parse metadata: " + fileName, e);
                throw new RuntimeException("cannot load/parse metadata: " + fileName, e);
            }
        } catch (IOException e3) {
            e = e3;
            logger.log(Level.SEVERE, "cannot load/parse metadata: " + fileName, e);
            throw new RuntimeException("cannot load/parse metadata: " + fileName, e);
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private static PhoneMetadataCollection loadMetadataAndCloseInput(ObjectInputStream source) {
        PhoneMetadataCollection metadataCollection = new PhoneMetadataCollection();
        try {
            metadataCollection.readExternal(source);
            try {
                source.close();
            } catch (IOException e) {
                logger.log(Level.WARNING, "error closing input stream (ignored)", e);
            }
        } catch (IOException e2) {
            logger.log(Level.WARNING, "error reading input (ignored)", e2);
        } catch (Throwable th) {
            try {
                source.close();
            } catch (IOException e22) {
                logger.log(Level.WARNING, "error closing input stream (ignored)", e22);
            }
        }
        return metadataCollection;
    }
}
