package tmsdkobf;

import android.os.Environment;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

/* compiled from: Unknown */
public class lv {
    private static final String[] wH = new String[]{"MI 2"};

    private static boolean a(ArrayList<String> arrayList, String str) {
        Iterator it = arrayList.iterator();
        while (it.hasNext()) {
            String str2 = (String) it.next();
            if (str.equals(str2)) {
                return true;
            }
            boolean equals;
            try {
                str2 = new File(str2).getCanonicalPath();
                String canonicalPath = new File(str).getCanonicalPath();
                if (!(str2 == null || canonicalPath == null)) {
                    equals = str2.equals(canonicalPath);
                    continue;
                    if (equals) {
                        return equals;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            equals = false;
            continue;
            if (equals) {
                return equals;
            }
        }
        return false;
    }

    public static ArrayList<String> dF() {
        String absolutePath;
        Exception e;
        Throwable th;
        ArrayList<String> arrayList = new ArrayList();
        if (lt.dD() == 0) {
            absolutePath = Environment.getExternalStorageDirectory().getAbsolutePath();
            if (absolutePath != null) {
                arrayList.add(absolutePath);
            }
        }
        BufferedReader bufferedReader;
        try {
            bufferedReader = new BufferedReader(new FileReader("/proc/mounts"));
            while (true) {
                absolutePath = bufferedReader.readLine();
                if (absolutePath == null) {
                    break;
                }
                if (!absolutePath.contains("vfat")) {
                    try {
                        if (!(absolutePath.contains("exfat") || absolutePath.contains("/mnt") || absolutePath.contains("fuse"))) {
                        }
                    } catch (Exception e2) {
                        e = e2;
                    }
                }
                String[] split = absolutePath.split("\\s+");
                if (split[1].equals(Environment.getExternalStorageDirectory().getPath())) {
                    if (!a(arrayList, split[1])) {
                        arrayList.add(split[1]);
                    }
                } else if (!(!absolutePath.contains("/dev/block/vold") || absolutePath.contains("/mnt/secure") || absolutePath.contains("/mnt/asec") || absolutePath.contains("/mnt/obb") || absolutePath.contains("/dev/mapper") || absolutePath.contains("tmpfs") || a(arrayList, split[1]))) {
                    arrayList.add(split[1]);
                }
            }
            u(arrayList);
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException e3) {
                    e3.printStackTrace();
                }
            }
            return arrayList;
        } catch (Exception e4) {
            e = e4;
            bufferedReader = null;
            try {
                e.printStackTrace();
                if (bufferedReader != null) {
                    try {
                        bufferedReader.close();
                    } catch (IOException e32) {
                        e32.printStackTrace();
                    }
                }
                return arrayList;
            } catch (Throwable th2) {
                th = th2;
                if (bufferedReader != null) {
                    try {
                        bufferedReader.close();
                    } catch (IOException e5) {
                        e5.printStackTrace();
                    }
                }
                throw th;
            }
        } catch (Throwable th3) {
            th = th3;
            bufferedReader = null;
            if (bufferedReader != null) {
                bufferedReader.close();
            }
            throw th;
        }
    }

    private static void u(ArrayList<String> arrayList) {
        if (arrayList != null && arrayList.size() > 0) {
            for (int i = 0; i < arrayList.size(); i++) {
                while (((String) arrayList.get(i)).endsWith("/")) {
                    arrayList.set(i, ((String) arrayList.get(i)).substring(0, ((String) arrayList.get(i)).length() - 1));
                }
            }
        }
    }
}
