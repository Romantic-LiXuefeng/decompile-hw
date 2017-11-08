package com.huawei.hsm;

import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import com.huawei.lcagent.client.MetricConstant;
import java.lang.ref.SoftReference;

public class MediaTransactWrapperEx {
    private static final String TAG = "MediaTransactWrapperEx";
    private static SoftReference<IBinder> mBinder = null;

    public static boolean registerMusicObserver(IHsmMusicWatch observer) {
        if (observer != null) {
            return operateMusicObserver(observer);
        }
        Log.e(TAG, "registerMusicObserver ->> register null observer not allowed.");
        return false;
    }

    public static boolean unregisterMusicObserver() {
        return operateMusicObserver(null);
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private static boolean operateMusicObserver(IHsmMusicWatch observer) {
        boolean retVal = true;
        boolean register = true;
        if (observer == null) {
            register = false;
        }
        try {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            IBinder b = getBinder();
            if (b != null) {
                data.writeInterfaceToken("com.huawei.hsm.IHsmCoreService");
                data.writeInt(register ? 0 : 1);
                data.writeInt(Process.myPid());
                if (observer != null) {
                    data.writeStrongBinder(observer.asBinder());
                }
                b.transact(MetricConstant.CAMERA_METRIC_ID_EX, data, reply, 0);
                reply.readException();
                retVal = 1 == reply.readInt();
            }
            recycleParcel(data);
            recycleParcel(reply);
        } catch (RemoteException e) {
            Log.e(TAG, "operateMusicObserver transact catch remote exception: " + e.toString() + "when register: " + register);
        } catch (Exception e2) {
            Log.e(TAG, "operateMusicObserver transact catch exception: " + e2.toString() + "when register: " + register);
        } catch (Throwable th) {
            recycleParcel(null);
            recycleParcel(null);
        }
        return retVal;
    }

    private static synchronized IBinder getBinder() {
        IBinder iBinder;
        synchronized (MediaTransactWrapperEx.class) {
            if (mBinder == null || mBinder.get() == null) {
                mBinder = new SoftReference(ServiceManager.getService("system.hsmcore"));
            }
            iBinder = (IBinder) mBinder.get();
        }
        return iBinder;
    }

    private static void recycleParcel(Parcel p) {
        if (p != null) {
            p.recycle();
        }
    }
}
