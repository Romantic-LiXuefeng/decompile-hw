package com.loc;

import android.content.Context;
import android.util.Log;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

/* compiled from: AuthManager */
public class n {
    public static int a = -1;
    public static String b = "";
    private static v c;
    private static String d = "http://apiinit.amap.com/v3/log/init";
    private static String e = null;

    private static String a() {
        return d;
    }

    private static Map<String, String> a(Context context) {
        Map<String, String> hashMap = new HashMap();
        try {
            hashMap.put("resType", "json");
            hashMap.put("encode", "UTF-8");
            String a = o.a();
            hashMap.put("ts", a);
            hashMap.put("key", m.f(context));
            hashMap.put("scode", o.a(context, a, w.a("resType=json&encode=UTF-8&key=" + m.f(context))));
        } catch (Throwable th) {
            aa.a(th, "Auth", "gParams");
        }
        return hashMap;
    }

    public static void a(String str) {
        m.c(str);
    }

    public static synchronized boolean a(Context context, v vVar) {
        boolean a;
        synchronized (n.class) {
            a = a(context, vVar, true);
        }
        return a;
    }

    private static boolean a(Context context, v vVar, boolean z) {
        c = vVar;
        try {
            String a = a();
            Map hashMap = new HashMap();
            hashMap.put("Content-Type", "application/x-www-form-urlencoded");
            hashMap.put("Accept-Encoding", "gzip");
            hashMap.put("Connection", "Keep-Alive");
            hashMap.put("User-Agent", c.b);
            hashMap.put("X-INFO", o.a(context, c, null, z));
            hashMap.put("logversion", "2.1");
            hashMap.put("platinfo", String.format("platform=Android&sdkversion=%s&product=%s", new Object[]{c.a, c.c}));
            bo a2 = bo.a();
            bs xVar = new x();
            xVar.a(t.a(context));
            xVar.a(hashMap);
            xVar.b(a(context));
            xVar.a(a);
            return a(a2.b(xVar));
        } catch (Throwable th) {
            aa.a(th, "Auth", "getAuth");
            return true;
        }
    }

    private static boolean a(byte[] bArr) {
        if (bArr == null) {
            return true;
        }
        String str;
        try {
            str = new String(bArr, "UTF-8");
            try {
                JSONObject jSONObject = new JSONObject(str);
                if (jSONObject.has("status")) {
                    int i = jSONObject.getInt("status");
                    if (i == 1) {
                        a = 1;
                    } else if (i == 0) {
                        a = 0;
                    }
                }
                if (jSONObject.has("info")) {
                    b = jSONObject.getString("info");
                }
                if (a == 0) {
                    Log.i("AuthFailure", b);
                }
                return a == 1;
            } catch (Throwable e) {
                aa.a(e, "Auth", "lData");
                return false;
            } catch (Throwable e2) {
                aa.a(e2, "Auth", "lData");
                return false;
            }
        } catch (UnsupportedEncodingException e3) {
            str = new String(bArr);
        }
    }
}
