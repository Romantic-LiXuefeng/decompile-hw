package com.huawei.servicehost;

import android.os.IBinder.DeathRecipient;
import android.os.RemoteException;
import android.util.Log;
import edu.umd.cs.findbugs.annotations.SuppressWarnings;

public class ImageProcessManager implements DeathRecipient {
    private static final boolean DEBUG = true;
    private static final String TAG = "ImageProcessManager";
    private static ImageProcessManager gImageProcessManager;
    private static final Object gLock = new Object();
    private IImageProcessService mImageProcessService;
    private final Object mLock = new Object();

    private ImageProcessManager() {
        getImageProcessService();
    }

    public static ImageProcessManager get() {
        ImageProcessManager imageProcessManager;
        synchronized (gLock) {
            if (gImageProcessManager == null) {
                gImageProcessManager = new ImageProcessManager();
            }
            imageProcessManager = gImageProcessManager;
        }
        return imageProcessManager;
    }

    public IImageProcessSession createIPSession(String type) {
        synchronized (this.mLock) {
            try {
                if (this.mImageProcessService != null) {
                    IImageProcessSession createIPSession = this.mImageProcessService.createIPSession(type);
                    return createIPSession;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "failed.");
            }
        }
        return null;
    }

    public IGlobalSession getGlobalSession() {
        synchronized (this.mLock) {
            try {
                if (this.mImageProcessService != null) {
                    IGlobalSession globalSession = this.mImageProcessService.getGlobalSession();
                    return globalSession;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "failed.");
            }
        }
        return null;
    }

    public IImageProcessService getImageProcessService() {
        synchronized (this.mLock) {
            if (this.mImageProcessService != null) {
                IImageProcessService iImageProcessService = this.mImageProcessService;
                return iImageProcessService;
            }
            this.mImageProcessService = ServiceFetcher.get().getImageProcessService();
            if (this.mImageProcessService == null) {
                Log.e(TAG, "fail to getImageProcessService.");
                return null;
            }
            try {
                Log.v(TAG, "link servicehost ok.");
                this.mImageProcessService.asBinder().linkToDeath(this, 0);
                iImageProcessService = this.mImageProcessService;
                return iImageProcessService;
            } catch (RemoteException e) {
                Log.e(TAG, "linkToDeath to camera servicehost failed.");
                return null;
            }
        }
    }

    @SuppressWarnings({"ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD"})
    public void binderDied() {
        synchronized (gLock) {
            gImageProcessManager = null;
        }
        synchronized (this.mLock) {
            Log.w(TAG, "camera servicehost died.");
            this.mImageProcessService = null;
        }
    }
}
