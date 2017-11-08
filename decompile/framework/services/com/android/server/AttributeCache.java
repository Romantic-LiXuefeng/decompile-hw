package com.android.server;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources.NotFoundException;
import android.content.res.TypedArray;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.SparseArray;
import com.android.internal.annotations.GuardedBy;
import java.lang.ref.WeakReference;

public final class AttributeCache {
    private static AttributeCache sInstance = null;
    @GuardedBy("this")
    private final Configuration mConfiguration = new Configuration();
    private final Context mContext;
    @GuardedBy("this")
    private final ArrayMap<String, WeakReference<Package>> mPackages = new ArrayMap();

    public static final class Entry {
        public final TypedArray array;
        public final Context context;

        public Entry(Context c, TypedArray ta) {
            this.context = c;
            this.array = ta;
        }

        void recycle() {
            if (this.array != null) {
                this.array.recycle();
            }
        }
    }

    public static final class Package {
        public final Context context;
        private final SparseArray<ArrayMap<int[], Entry>> mMap = new SparseArray();

        public Package(Context c) {
            this.context = c;
        }
    }

    public static void init(Context context) {
        if (sInstance == null) {
            sInstance = new AttributeCache(context);
        }
    }

    public static AttributeCache instance() {
        return sInstance;
    }

    public AttributeCache(Context context) {
        this.mContext = context;
    }

    public void removePackage(String packageName) {
        synchronized (this) {
            WeakReference<Package> ref = (WeakReference) this.mPackages.remove(packageName);
            Package packageR = ref != null ? (Package) ref.get() : null;
            if (packageR != null) {
                if (packageR.mMap != null) {
                    for (int i = 0; i < packageR.mMap.size(); i++) {
                        ArrayMap<int[], Entry> map = (ArrayMap) packageR.mMap.valueAt(i);
                        for (int j = 0; j < map.size(); j++) {
                            ((Entry) map.valueAt(j)).recycle();
                        }
                    }
                }
                packageR.context.getResources().flushLayoutCache();
            }
        }
    }

    public void updateConfiguration(Configuration config) {
        synchronized (this) {
            if ((-1073741985 & this.mConfiguration.updateFrom(config)) != 0) {
                this.mPackages.clear();
            }
        }
    }

    public Entry get(String packageName, int resId, int[] styleable, int userId) {
        Entry ent;
        synchronized (this) {
            WeakReference<Package> ref = (WeakReference) this.mPackages.get(packageName);
            Package packageR = ref != null ? (Package) ref.get() : null;
            ArrayMap arrayMap = null;
            Entry ent2 = null;
            if (packageR != null) {
                arrayMap = (ArrayMap) packageR.mMap.get(resId);
                if (arrayMap != null) {
                    ent2 = (Entry) arrayMap.get(styleable);
                    if (ent2 != null) {
                        return ent2;
                    }
                }
                ent = ent2;
            } else {
                try {
                    Context context = this.mContext.createPackageContextAsUser(packageName, 0, new UserHandle(userId));
                    if (context == null) {
                        return null;
                    }
                    packageR = new Package(context);
                    this.mPackages.put(packageName, new WeakReference(packageR));
                    ent = null;
                } catch (NameNotFoundException e) {
                    return null;
                }
            }
            if (arrayMap == null) {
                arrayMap = new ArrayMap();
                packageR.mMap.put(resId, arrayMap);
            }
            try {
                ent2 = new Entry(packageR.context, packageR.context.obtainStyledAttributes(resId, styleable));
                try {
                    arrayMap.put(styleable, ent2);
                    return ent2;
                } catch (NotFoundException e2) {
                    return null;
                }
            } catch (NotFoundException e3) {
                ent2 = ent;
                return null;
            }
        }
    }
}
