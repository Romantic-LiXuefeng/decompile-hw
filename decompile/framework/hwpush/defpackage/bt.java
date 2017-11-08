package defpackage;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/* renamed from: bt */
public class bt {
    private SharedPreferences cn;

    public bt(Context context, String str) {
        if (context == null) {
            throw new NullPointerException("context is null!");
        }
        this.cn = y(str);
    }

    private SharedPreferences y(String str) {
        File file = new File("/data/misc/hwpush", str + ".xml");
        try {
            Constructor declaredConstructor = Class.forName("android.app.SharedPreferencesImpl").getDeclaredConstructor(new Class[]{File.class, Integer.TYPE});
            declaredConstructor.setAccessible(true);
            return (SharedPreferences) declaredConstructor.newInstance(new Object[]{file, Integer.valueOf(0)});
        } catch (ClassNotFoundException e) {
            Log.e("PushLog2841", e.toString());
            return null;
        } catch (NoSuchMethodException e2) {
            Log.e("PushLog2841", e2.toString());
            return null;
        } catch (InstantiationException e3) {
            Log.e("PushLog2841", e3.toString());
            return null;
        } catch (IllegalAccessException e4) {
            Log.e("PushLog2841", e4.toString());
            return null;
        } catch (IllegalArgumentException e5) {
            Log.e("PushLog2841", e5.toString());
            return null;
        } catch (InvocationTargetException e6) {
            Log.e("PushLog2841", e6.toString());
            return null;
        }
    }

    public void a(String str, Float f) {
        if (this.cn != null) {
            Editor edit = this.cn.edit();
            if (edit != null) {
                edit.putFloat(str, f.floatValue()).commit();
            }
        }
    }

    public void a(String str, Integer num) {
        if (this.cn != null) {
            Editor edit = this.cn.edit();
            if (edit != null) {
                edit.putInt(str, num.intValue()).commit();
            }
        }
    }

    public void a(String str, Long l) {
        if (this.cn != null) {
            Editor edit = this.cn.edit();
            if (edit != null) {
                edit.putLong(str, l.longValue()).commit();
            }
        }
    }

    public void a(String str, boolean z) {
        if (this.cn != null) {
            Editor edit = this.cn.edit();
            if (edit != null) {
                edit.putBoolean(str, z).commit();
            }
        }
    }

    public void a(Map map) {
        for (Entry entry : map.entrySet()) {
            c((String) entry.getKey(), entry.getValue());
        }
    }

    public boolean a(ContentValues contentValues) {
        if (this.cn == null || contentValues == null || this.cn.edit() == null) {
            return false;
        }
        boolean z = true;
        for (Entry entry : contentValues.valueSet()) {
            z = !c((String) entry.getKey(), entry.getValue()) ? false : z;
        }
        return z;
    }

    public boolean c(String str, Object obj) {
        if (this.cn == null) {
            return false;
        }
        Editor edit = this.cn.edit();
        if (obj instanceof String) {
            edit.putString(str, String.valueOf(obj));
        } else if ((obj instanceof Integer) || (obj instanceof Short) || (obj instanceof Byte)) {
            edit.putInt(str, ((Integer) obj).intValue());
        } else if (obj instanceof Long) {
            edit.putLong(str, ((Long) obj).longValue());
        } else if (obj instanceof Float) {
            edit.putFloat(str, ((Float) obj).floatValue());
        } else if (obj instanceof Double) {
            edit.putFloat(str, (float) ((Double) obj).doubleValue());
        } else if (obj instanceof Boolean) {
            edit.putBoolean(str, ((Boolean) obj).booleanValue());
        }
        return edit.commit();
    }

    public boolean clear() {
        return this.cn != null ? this.cn.edit().clear().commit() : false;
    }

    public ContentValues co() {
        if (this.cn == null) {
            return null;
        }
        Map all = this.cn.getAll();
        if (all == null) {
            return null;
        }
        ContentValues contentValues = new ContentValues();
        for (Entry entry : all.entrySet()) {
            String str = (String) entry.getKey();
            Object value = entry.getValue();
            if (value instanceof String) {
                contentValues.put(str, String.valueOf(value));
            } else if ((value instanceof Integer) || (value instanceof Short) || (value instanceof Byte)) {
                contentValues.put(str, (Integer) value);
            } else if (value instanceof Long) {
                contentValues.put(str, (Long) value);
            } else if (value instanceof Float) {
                contentValues.put(str, (Float) value);
            } else if (value instanceof Double) {
                contentValues.put(str, Float.valueOf((float) ((Double) value).doubleValue()));
            } else if (value instanceof Boolean) {
                contentValues.put(str, (Boolean) value);
            }
        }
        return contentValues;
    }

    public boolean containsKey(String str) {
        return this.cn != null && this.cn.contains(str);
    }

    public boolean f(String str, String str2) {
        if (this.cn == null) {
            return false;
        }
        Editor edit = this.cn.edit();
        return edit != null ? edit.putString(str, str2).commit() : false;
    }

    public Map getAll() {
        return this.cn != null ? this.cn.getAll() : new HashMap();
    }

    public boolean getBoolean(String str, boolean z) {
        return this.cn != null ? this.cn.getBoolean(str, z) : z;
    }

    public int getInt(String str) {
        return this.cn != null ? this.cn.getInt(str, 0) : 0;
    }

    public long getLong(String str) {
        return this.cn != null ? this.cn.getLong(str, 0) : 0;
    }

    public String getString(String str) {
        return this.cn != null ? this.cn.getString(str, "") : "";
    }

    public boolean z(String str) {
        if (this.cn == null || !this.cn.contains(str)) {
            return false;
        }
        Editor remove = this.cn.edit().remove(str);
        remove.commit();
        return remove.commit();
    }
}
