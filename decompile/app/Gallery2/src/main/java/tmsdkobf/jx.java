package tmsdkobf;

import android.text.TextUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import tmsdk.common.utils.d;

/* compiled from: Unknown */
public class jx {
    private jp uH = new jp(1024);
    private Map<String, String> uI = new HashMap(16);
    private Map<String, String> uJ = new HashMap(16);
    private Map<String, String> uK = new HashMap(16);
    private boolean uL;

    private static boolean bI(String str) {
        return !TextUtils.isEmpty(str) && str.indexOf(42) >= 0;
    }

    private static boolean bJ(String str) {
        return str.length() > 0 && str.indexOf(42) == str.length() - 1;
    }

    private static String bK(String str) {
        return str.replace("\\", "\\\\").replace(".", "\\.").replace("+", "\\+").replace("*", ".*");
    }

    public void clear() {
        this.uH.clear();
        this.uI.clear();
        this.uJ.clear();
        this.uK.clear();
    }

    public String getName(String str) {
        String str2;
        String de = rb.de(str);
        if (rb.dd(de)) {
            try {
                str2 = (String) this.uH.get(Integer.parseInt(de));
            } catch (Throwable e) {
                d.a("ContactsMap", "minMatch to int", e);
                str2 = (String) this.uI.get(de);
            }
        } else {
            str2 = (String) this.uI.get(de);
        }
        if (str2 != null) {
            return str2;
        }
        CharSequence dc = rb.dc(str);
        CharSequence da = rb.da(dc);
        for (Entry entry : this.uJ.entrySet()) {
            de = (String) entry.getKey();
            if (rb.db(de)) {
                if (dc.startsWith(de)) {
                    return (String) entry.getValue();
                }
            } else if (da.startsWith(de)) {
                return (String) entry.getValue();
            }
        }
        for (Entry entry2 : this.uK.entrySet()) {
            Pattern compile = Pattern.compile((String) entry2.getKey());
            if (compile.matcher(dc).matches() || compile.matcher(da).matches()) {
                return (String) entry2.getValue();
            }
        }
        return null;
    }

    public void k(String str, String str2) {
        if (this.uL && bI(str)) {
            l(str, str2);
        } else if (!TextUtils.isEmpty(str)) {
            if (str2 == null) {
                str2 = "";
            }
            String de = rb.de(str);
            if (rb.dd(de)) {
                try {
                    this.uH.put(Integer.parseInt(de), str2);
                } catch (NumberFormatException e) {
                    d.c("ContactsMap", "Exception in parseInt(minMatch): " + e.getMessage());
                    this.uI.put(de, str2);
                }
            } else {
                this.uI.put(de, str2);
            }
        }
    }

    public void l(String str, String str2) {
        if (!TextUtils.isEmpty(str)) {
            if (str2 == null) {
                str2 = "";
            }
            if (bJ(str)) {
                this.uJ.put(str.substring(0, str.length() - 1), str2);
            } else {
                this.uK.put(bK(str), str2);
            }
        }
    }
}
