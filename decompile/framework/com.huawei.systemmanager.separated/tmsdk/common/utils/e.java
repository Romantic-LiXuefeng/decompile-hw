package tmsdk.common.utils;

import android.util.Log;

/* compiled from: Unknown */
class e extends a {
    e() {
    }

    public void d(int i, String str, String str2) {
        if (str2 == null) {
            str2 = "(null)";
        }
        if (i == 10) {
            i = 3;
        }
        Log.println(i, str, str2);
    }
}
