package tmsdkobf;

import android.os.Process;
import java.lang.reflect.InvocationTargetException;

/* compiled from: Unknown */
public class nd {
    public static void a(RuntimeException runtimeException) {
        a(new Thread(), runtimeException, runtimeException.getMessage() + "|v=" + js.cy() + "|p=" + Process.myPid() + "|t=" + js.cz() + "|s=" + js.cA() + "|d=" + js.cB(), null);
    }

    public static void a(Thread thread, Throwable th, String str, byte[] bArr) {
        try {
            Class.forName("com.tencent.feedback.eup.CrashReport").getDeclaredMethod("handleCatchException", new Class[]{Thread.class, Throwable.class, String.class, byte[].class}).invoke(null, new Object[]{thread, th, str, bArr});
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e2) {
            e2.printStackTrace();
        } catch (IllegalAccessException e3) {
            e3.printStackTrace();
        } catch (InvocationTargetException e4) {
            e4.printStackTrace();
        } catch (Exception e5) {
            e5.printStackTrace();
        }
    }
}
