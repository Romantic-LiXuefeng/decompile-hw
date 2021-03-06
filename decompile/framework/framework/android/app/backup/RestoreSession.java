package android.app.backup;

import android.app.backup.IRestoreObserver.Stub;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

public class RestoreSession {
    static final String TAG = "RestoreSession";
    IRestoreSession mBinder;
    final Context mContext;
    RestoreObserverWrapper mObserver = null;

    private class RestoreObserverWrapper extends Stub {
        static final int MSG_RESTORE_FINISHED = 3;
        static final int MSG_RESTORE_SETS_AVAILABLE = 4;
        static final int MSG_RESTORE_STARTING = 1;
        static final int MSG_UPDATE = 2;
        final RestoreObserver mAppObserver;
        final Handler mHandler;

        RestoreObserverWrapper(Context context, RestoreObserver appObserver) {
            this.mHandler = new Handler(context.getMainLooper()) {
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case 1:
                            RestoreObserverWrapper.this.mAppObserver.restoreStarting(msg.arg1);
                            return;
                        case 2:
                            RestoreObserverWrapper.this.mAppObserver.onUpdate(msg.arg1, (String) msg.obj);
                            return;
                        case 3:
                            RestoreObserverWrapper.this.mAppObserver.restoreFinished(msg.arg1);
                            return;
                        case 4:
                            RestoreObserverWrapper.this.mAppObserver.restoreSetsAvailable((RestoreSet[]) msg.obj);
                            return;
                        default:
                            return;
                    }
                }
            };
            this.mAppObserver = appObserver;
        }

        public void restoreSetsAvailable(RestoreSet[] result) {
            this.mHandler.sendMessage(this.mHandler.obtainMessage(4, result));
        }

        public void restoreStarting(int numPackages) {
            this.mHandler.sendMessage(this.mHandler.obtainMessage(1, numPackages, 0));
        }

        public void onUpdate(int nowBeingRestored, String currentPackage) {
            this.mHandler.sendMessage(this.mHandler.obtainMessage(2, nowBeingRestored, 0, currentPackage));
        }

        public void restoreFinished(int error) {
            this.mHandler.sendMessage(this.mHandler.obtainMessage(3, error, 0));
        }
    }

    public int getAvailableRestoreSets(RestoreObserver observer) {
        int err = -1;
        try {
            err = this.mBinder.getAvailableRestoreSets(new RestoreObserverWrapper(this.mContext, observer));
        } catch (RemoteException e) {
            Log.d(TAG, "Can't contact server to get available sets");
        }
        return err;
    }

    public int restoreAll(long token, RestoreObserver observer) {
        int err = -1;
        if (this.mObserver != null) {
            Log.d(TAG, "restoreAll() called during active restore");
            return -1;
        }
        this.mObserver = new RestoreObserverWrapper(this.mContext, observer);
        try {
            err = this.mBinder.restoreAll(token, this.mObserver);
        } catch (RemoteException e) {
            Log.d(TAG, "Can't contact server to restore");
        }
        return err;
    }

    public int restoreSome(long token, RestoreObserver observer, String[] packages) {
        int err = -1;
        if (this.mObserver != null) {
            Log.d(TAG, "restoreAll() called during active restore");
            return -1;
        }
        this.mObserver = new RestoreObserverWrapper(this.mContext, observer);
        try {
            err = this.mBinder.restoreSome(token, this.mObserver, packages);
        } catch (RemoteException e) {
            Log.d(TAG, "Can't contact server to restore packages");
        }
        return err;
    }

    public int restorePackage(String packageName, RestoreObserver observer) {
        int err = -1;
        if (this.mObserver != null) {
            Log.d(TAG, "restorePackage() called during active restore");
            return -1;
        }
        this.mObserver = new RestoreObserverWrapper(this.mContext, observer);
        try {
            err = this.mBinder.restorePackage(packageName, this.mObserver);
        } catch (RemoteException e) {
            Log.d(TAG, "Can't contact server to restore package");
        }
        return err;
    }

    public void endRestoreSession() {
        try {
            this.mBinder.endRestoreSession();
        } catch (RemoteException e) {
            Log.d(TAG, "Can't contact server to get available sets");
        } catch (Throwable th) {
            this.mBinder = null;
        }
        this.mBinder = null;
    }

    RestoreSession(Context context, IRestoreSession binder) {
        this.mContext = context;
        this.mBinder = binder;
    }
}
