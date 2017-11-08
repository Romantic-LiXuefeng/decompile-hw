package android.icu.impl;

import java.io.InputStream;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.MissingResourceException;
import java.util.logging.Logger;

public final class ICUData {
    public static final String ICU_BASE_NAME = "android/icu/impl/data/icudt56b";
    public static final String ICU_BRKITR_BASE_NAME = "android/icu/impl/data/icudt56b/brkitr";
    public static final String ICU_BRKITR_NAME = "brkitr";
    public static final String ICU_BUNDLE = "data/icudt56b";
    public static final String ICU_COLLATION_BASE_NAME = "android/icu/impl/data/icudt56b/coll";
    public static final String ICU_CURR_BASE_NAME = "android/icu/impl/data/icudt56b/curr";
    static final String ICU_DATA_PATH = "android/icu/impl/";
    public static final String ICU_LANG_BASE_NAME = "android/icu/impl/data/icudt56b/lang";
    public static final String ICU_RBNF_BASE_NAME = "android/icu/impl/data/icudt56b/rbnf";
    public static final String ICU_REGION_BASE_NAME = "android/icu/impl/data/icudt56b/region";
    public static final String ICU_TRANSLIT_BASE_NAME = "android/icu/impl/data/icudt56b/translit";
    public static final String ICU_UNIT_BASE_NAME = "android/icu/impl/data/icudt56b/unit";
    public static final String ICU_ZONE_BASE_NAME = "android/icu/impl/data/icudt56b/zone";
    static final String PACKAGE_NAME = "icudt56b";
    private static final boolean logBinaryDataFromInputStream = false;
    private static final Logger logger = null;

    public static boolean exists(final String resourceName) {
        URL i;
        if (System.getSecurityManager() != null) {
            i = (URL) AccessController.doPrivileged(new PrivilegedAction<URL>() {
                public URL run() {
                    return ICUData.class.getResource(resourceName);
                }
            });
        } else {
            i = ICUData.class.getResource(resourceName);
        }
        return i != null;
    }

    private static InputStream getStream(final Class<?> root, final String resourceName, boolean required) {
        InputStream i;
        if (System.getSecurityManager() != null) {
            i = (InputStream) AccessController.doPrivileged(new PrivilegedAction<InputStream>() {
                public InputStream run() {
                    return root.getResourceAsStream(resourceName);
                }
            });
        } else {
            i = root.getResourceAsStream(resourceName);
        }
        if (i == null && required) {
            throw new MissingResourceException("could not locate data " + resourceName, root.getPackage().getName(), resourceName);
        }
        checkStreamForBinaryData(i, resourceName);
        return i;
    }

    static InputStream getStream(final ClassLoader loader, final String resourceName, boolean required) {
        InputStream i;
        if (System.getSecurityManager() != null) {
            i = (InputStream) AccessController.doPrivileged(new PrivilegedAction<InputStream>() {
                public InputStream run() {
                    return loader.getResourceAsStream(resourceName);
                }
            });
        } else {
            i = loader.getResourceAsStream(resourceName);
        }
        if (i == null && required) {
            throw new MissingResourceException("could not locate data", loader.toString(), resourceName);
        }
        checkStreamForBinaryData(i, resourceName);
        return i;
    }

    private static void checkStreamForBinaryData(InputStream is, String resourceName) {
    }

    public static InputStream getStream(ClassLoader loader, String resourceName) {
        return getStream(loader, resourceName, false);
    }

    public static InputStream getRequiredStream(ClassLoader loader, String resourceName) {
        return getStream(loader, resourceName, true);
    }

    public static InputStream getStream(String resourceName) {
        return getStream(ICUData.class, resourceName, false);
    }

    public static InputStream getRequiredStream(String resourceName) {
        return getStream(ICUData.class, resourceName, true);
    }

    public static InputStream getStream(Class<?> root, String resourceName) {
        return getStream((Class) root, resourceName, false);
    }

    public static InputStream getRequiredStream(Class<?> root, String resourceName) {
        return getStream((Class) root, resourceName, true);
    }
}
