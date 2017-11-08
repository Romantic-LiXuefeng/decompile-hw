package huawei.android.hwutil;

import android.content.res.Resources;
import android.content.res.ResourcesImpl;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ZipFileCache {
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_ICON = true;
    private static String ICONS = "icons";
    public static final int RES_INDEX_DEFAULT = 0;
    public static final int RES_INDEX_FRW = 2;
    public static final int RES_INDEX_HW_FRW = 4;
    public static final int RES_INDEX_LAND = 1;
    public static final int RES_INDEX_LAND_FRW = 3;
    public static final int RES_INDEX_LAND_HW_FRW = 5;
    private static final String TAG = "ZipFileCache";
    private static final int TRY_TIMES = 3;
    private static final ConcurrentHashMap<String, ZipFileCache> sZipFileCacheMaps = new ConcurrentHashMap();
    private String HWT_PATH_SKIN = "/data/skin";
    private String HWT_PATH_TEMP_SKIN = "/data/skin.tmp";
    private boolean mFileNotExist = false;
    private boolean mInited = false;
    private String mPath;
    private String mZip;
    private ZipFile mZipFile;
    private ZipResDir[] mZipResDir = new ZipResDir[]{new ZipResDir(-1, null), new ZipResDir(-1, null), new ZipResDir(-1, null), new ZipResDir(-1, null), new ZipResDir(-1, null), new ZipResDir(-1, null)};

    private static class ZipResDir {
        public int mDensity = -1;
        public String mDir = "";

        public ZipResDir(int density, String dir) {
            this.mDensity = density;
            this.mDir = dir;
        }
    }

    private ZipFileCache(String path, String zip) {
        this.mPath = path;
        this.mZip = zip;
        if (!this.mFileNotExist && !openZipFile() && ICONS.equals(zip)) {
            Log.w(TAG, "init icons failed when open zip file. mPath=" + this.mPath + ",mZip=" + this.mZip + ",mFileNotExist=" + this.mFileNotExist);
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public static ZipFileCache getAndCheckCachedZipFile(String path, String zip) {
        Throwable th;
        String key = path + "/" + zip;
        synchronized (ZipFileCache.class) {
            try {
                ZipFileCache zipFileCache = (ZipFileCache) sZipFileCacheMaps.get(key);
                if (zipFileCache == null) {
                    ZipFileCache zipFileCache2 = new ZipFileCache(path, zip);
                    try {
                        ZipFileCache oldValue;
                        if (zipFileCache2.mZipFile != null) {
                            oldValue = (ZipFileCache) sZipFileCacheMaps.putIfAbsent(key, zipFileCache2);
                            if (oldValue != null) {
                                return oldValue;
                            }
                            return zipFileCache2;
                        } else if (zipFileCache2.mFileNotExist) {
                            oldValue = (ZipFileCache) sZipFileCacheMaps.putIfAbsent(key, zipFileCache2);
                            if (oldValue != null) {
                                return oldValue;
                            }
                        }
                    } catch (Throwable th2) {
                        th = th2;
                        zipFileCache = zipFileCache2;
                        throw th;
                    }
                }
            } catch (Throwable th3) {
                th = th3;
                throw th;
            }
        }
    }

    public static synchronized void clear() {
        synchronized (ZipFileCache.class) {
            for (ZipFileCache zip : sZipFileCacheMaps.values()) {
                if (zip != null) {
                    zip.closeZipFile();
                }
            }
            sZipFileCacheMaps.clear();
        }
    }

    private synchronized boolean openZipFile() {
        try {
            File file = new File(this.mPath, this.mZip);
            File themeFile = new File(this.HWT_PATH_SKIN);
            File tempThemeFile = new File(this.HWT_PATH_TEMP_SKIN);
            if (file.exists() || !themeFile.exists() || tempThemeFile.exists()) {
                this.mZipFile = new ZipFile(file, 1);
                this.mInited = false;
                return true;
            }
            this.mFileNotExist = true;
            return false;
        } catch (IOException e) {
            closeZipFile();
            setEmpty();
            return false;
        }
    }

    private synchronized void closeZipFile() {
        if (this.mZipFile != null) {
            try {
                this.mZipFile.close();
            } catch (IOException e) {
            }
            this.mZipFile = null;
        }
    }

    private void closeInputStream(InputStream is) {
        if (is != null) {
            try {
                is.close();
            } catch (IOException e) {
            }
        }
    }

    private synchronized void setEmpty() {
        this.mPath = "";
        this.mZip = "";
        this.mZipFile = null;
        this.mFileNotExist = false;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public synchronized Bitmap getBitmapEntry(ResourcesImpl impl, String fileName) {
        if (this.mZipFile == null) {
            Log.w(TAG, "Get bitmap entry from zip file failed fileName=" + fileName);
            return null;
        }
        int reTryCount = 3;
        Bitmap bmp = null;
        InputStream inputStream = null;
        while (reTryCount > 0) {
            reTryCount--;
            try {
                ZipEntry entry = this.mZipFile.getEntry(fileName);
                if (entry != null) {
                    inputStream = this.mZipFile.getInputStream(entry);
                    bmp = BitmapFactory.decodeStream(inputStream);
                    if (bmp != null) {
                        bmp.setDensity(impl.getDisplayMetrics().densityDpi);
                        if (bmp.getWidth() != bmp.getHeight()) {
                            Log.i(TAG, "getBitmapEntry bmp width = " + bmp.getWidth() + "bmp height = " + bmp.getHeight() + " fileName = " + fileName);
                        }
                    }
                }
                break;
            } catch (Exception e) {
                closeZipFile();
                openZipFile();
                Log.e(TAG, "getBitmapEntry occur exception fileName = " + fileName + " e = " + e.getMessage());
            } finally {
                closeInputStream(inputStream);
            }
        }
    }

    public synchronized Bitmap getBitmapEntry(Resources res, String fileName) {
        ResourcesImpl impl = res.getImpl();
        if (impl != null) {
            return getBitmapEntry(impl, fileName);
        }
        Log.w(TAG, "resourcesImpl is null");
        return null;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public synchronized Bitmap getBitmapEntry(Resources res, TypedValue value, String fileName, Rect padding) {
        if (this.mZipFile == null) {
            return null;
        }
        Bitmap bitmap = null;
        InputStream inputStream = null;
        if (padding == null) {
            padding = new Rect();
        }
        Options opts = new Options();
        opts.inScreenDensity = res != null ? res.getDisplayMetrics().noncompatDensityDpi : DisplayMetrics.DENSITY_DEVICE;
        try {
            ZipEntry entry = this.mZipFile.getEntry(fileName);
            if (entry != null) {
                inputStream = this.mZipFile.getInputStream(entry);
                bitmap = BitmapFactory.decodeResourceStream(res, value, inputStream, padding, opts);
                if (bitmap != null) {
                    bitmap.setDensity(res != null ? res.getDisplayMetrics().densityDpi : DisplayMetrics.DENSITY_DEVICE);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getBitmapEntry(res,value,filename) occur exception fileName = " + fileName + " e = " + e.getMessage());
            return null;
        } finally {
            closeInputStream(inputStream);
        }
    }

    public synchronized Bitmap getBitmapEntry(Resources res, TypedValue value, String fileName) {
        return getBitmapEntry(res, value, fileName, null);
    }

    public synchronized ArrayList<Bitmap> getBitmapList(ResourcesImpl impl, String filePattern) {
        ArrayList<Bitmap> bmpList = new ArrayList();
        if (this.mZipFile == null) {
            return bmpList;
        }
        InputStream inputStream = null;
        Options opts = new Options();
        opts.inScreenDensity = impl != null ? impl.getDisplayMetrics().noncompatDensityDpi : DisplayMetrics.DENSITY_DEVICE;
        try {
            Enumeration<? extends ZipEntry> enumeration = this.mZipFile.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry zipEntry = (ZipEntry) enumeration.nextElement();
                String name = zipEntry.getName();
                int indexfile = name.indexOf(filePattern);
                int indexofpng = name.indexOf(".png");
                if (indexfile == 0 && indexofpng > 0) {
                    inputStream = this.mZipFile.getInputStream(zipEntry);
                    Bitmap bmp = BitmapFactory.decodeStream(inputStream, null, opts);
                    closeInputStream(inputStream);
                    if (bmp == null) {
                        continue;
                    } else {
                        bmp.setDensity(impl != null ? impl.getDisplayMetrics().densityDpi : DisplayMetrics.DENSITY_DEVICE);
                        bmpList.add(bmp);
                    }
                }
            }
            return bmpList;
        } catch (RuntimeException e) {
            closeInputStream(inputStream);
            bmpList.clear();
            return bmpList;
        } catch (Exception e2) {
            closeInputStream(inputStream);
            bmpList.clear();
            return bmpList;
        }
    }

    public synchronized ArrayList<Bitmap> getBitmapList(Resources res, String filePattern) {
        ArrayList<Bitmap> bmpList = new ArrayList();
        if (this.mZipFile == null) {
            return bmpList;
        }
        InputStream inputStream = null;
        Options opts = new Options();
        opts.inScreenDensity = res != null ? res.getDisplayMetrics().noncompatDensityDpi : DisplayMetrics.DENSITY_DEVICE;
        try {
            Enumeration<? extends ZipEntry> enumeration = this.mZipFile.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry zipEntry = (ZipEntry) enumeration.nextElement();
                String name = zipEntry.getName();
                int indexfile = name.indexOf(filePattern);
                int indexofpng = name.indexOf(".png");
                if (indexfile == 0 && indexofpng > 0) {
                    inputStream = this.mZipFile.getInputStream(zipEntry);
                    Bitmap bmp = BitmapFactory.decodeStream(inputStream, null, opts);
                    closeInputStream(inputStream);
                    if (bmp == null) {
                        continue;
                    } else {
                        bmp.setDensity(res != null ? res.getDisplayMetrics().densityDpi : DisplayMetrics.DENSITY_DEVICE);
                        bmpList.add(bmp);
                    }
                }
            }
            return bmpList;
        } catch (RuntimeException e) {
            closeInputStream(inputStream);
            bmpList.clear();
            return bmpList;
        } catch (Exception e2) {
            closeInputStream(inputStream);
            bmpList.clear();
            return bmpList;
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public synchronized Drawable getDrawableEntry(Resources res, TypedValue value, String fileName, Options opts) {
        if (this.mZipFile == null) {
            return null;
        }
        int reTryCount = 3;
        Drawable dr = null;
        InputStream inputStream = null;
        while (reTryCount > 0) {
            reTryCount--;
            try {
                ZipEntry entry = this.mZipFile.getEntry(fileName);
                if (entry != null) {
                    inputStream = this.mZipFile.getInputStream(entry);
                    dr = Drawable.createFromResourceStream(res, value, inputStream, fileName, opts);
                }
                break;
            } catch (Exception e) {
                closeZipFile();
                openZipFile();
                Log.e(TAG, "getDrawableEntry occur exception fileName = " + fileName + " e = " + e.getMessage());
            } finally {
                closeInputStream(inputStream);
            }
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public synchronized void initResDirInfo() {
        if (this.mZipFile != null && !this.mInited) {
            int i = 0;
            while (i < this.mZipResDir.length) {
                try {
                    for (Entry mapEntry : getZipResDirMap(i).entrySet()) {
                        if (this.mZipFile.getEntry(mapEntry.getKey().toString()) != null) {
                            this.mZipResDir[i].mDir = mapEntry.getKey().toString();
                            this.mZipResDir[i].mDensity = ((Integer) mapEntry.getValue()).intValue();
                            break;
                        }
                    }
                    i++;
                } catch (Exception e) {
                    Log.d(TAG, "initResDirInfo Exception = " + e.getMessage());
                    return;
                }
            }
            this.mInited = true;
        }
    }

    private HashMap<String, Integer> getZipResDirMap(int index) {
        HashMap<String, Integer> map = new HashMap();
        switch (index) {
            case 0:
                map.put("res/drawable-xxhdpi", Integer.valueOf(480));
                map.put("res/drawable-sw360dp-xxhdpi", Integer.valueOf(480));
                break;
            case 1:
                map.put("res/drawable-land-xxhdpi", Integer.valueOf(480));
                map.put("res/drawable-sw360dp-land-xxhdpi", Integer.valueOf(480));
                break;
            case 2:
                map.put("framework-res/res/drawable-xxhdpi", Integer.valueOf(480));
                break;
            case 3:
                map.put("framework-res/res/drawable-land-xxhdpi", Integer.valueOf(480));
                break;
            case 4:
                map.put("framework-res-hwext/res/drawable-xxhdpi", Integer.valueOf(480));
                break;
            case 5:
                map.put("framework-res-hwext/res/drawable-land-xxhdpi", Integer.valueOf(480));
                break;
        }
        return map;
    }

    public int getDrawableDensity(int index) {
        if (index >= this.mZipResDir.length) {
            return -1;
        }
        return this.mZipResDir[index].mDensity;
    }

    public String getDrawableDir(int index) {
        if (index >= this.mZipResDir.length) {
            return null;
        }
        return this.mZipResDir[index].mDir;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public synchronized InputStream getInputStreamEntry(String fileName) {
        if (this.mZipFile == null) {
            return null;
        }
        InputStream is = null;
        try {
            ZipEntry entry = this.mZipFile.getEntry(fileName);
            if (entry != null) {
                is = this.mZipFile.getInputStream(entry);
            }
        } catch (Exception e) {
            return null;
        }
    }
}
