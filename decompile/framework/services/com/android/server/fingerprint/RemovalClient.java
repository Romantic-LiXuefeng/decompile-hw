package com.android.server.fingerprint;

import android.content.Context;
import android.hardware.fingerprint.IFingerprintDaemon;
import android.hardware.fingerprint.IFingerprintServiceReceiver;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Slog;

public abstract class RemovalClient extends ClientMonitor {
    private int mFingerId;

    public RemovalClient(Context context, long halDeviceId, IBinder token, IFingerprintServiceReceiver receiver, int fingerId, int groupId, int userId, boolean restricted, String owner) {
        super(context, halDeviceId, token, receiver, userId, groupId, restricted, owner);
        this.mFingerId = fingerId;
    }

    public int start() {
        IFingerprintDaemon daemon = getFingerprintDaemon();
        try {
            int result = daemon.remove(this.mFingerId, getRealUserIdForHal(getGroupId()));
            if (result != 0) {
                Slog.w("FingerprintService", "startRemove with id = " + this.mFingerId + " failed, result=" + result);
                onError(1);
                return result;
            }
        } catch (RemoteException e) {
            Slog.e("FingerprintService", "startRemove failed", e);
        }
        return 0;
    }

    public int stop(boolean initiatedByClient) {
        if (initiatedByClient) {
            onError(5);
        }
        return 0;
    }

    private boolean sendRemoved(int fingerId, int groupId) {
        IFingerprintServiceReceiver receiver = getReceiver();
        if (receiver != null) {
            try {
                receiver.onRemoved(getHalDeviceId(), fingerId, groupId);
            } catch (RemoteException e) {
                Slog.w("FingerprintService", "Failed to notify Removed:", e);
            }
        }
        if (fingerId == 0) {
            return true;
        }
        return false;
    }

    public boolean onRemoved(int fingerId, int groupId) {
        if (fingerId != 0) {
            FingerprintUtils.getInstance().removeFingerprintIdForUser(getContext(), fingerId, getTargetUserId());
        }
        return sendRemoved(fingerId, getGroupId());
    }

    public boolean onEnrollResult(int fingerId, int groupId, int rem) {
        Slog.w("FingerprintService", "onEnrollResult() called for remove!");
        return true;
    }

    public boolean onAuthenticated(int fingerId, int groupId) {
        Slog.w("FingerprintService", "onAuthenticated() called for remove!");
        return true;
    }

    public boolean onEnumerationResult(int fingerId, int groupId) {
        Slog.w("FingerprintService", "onEnumerationResult() called for remove!");
        return false;
    }
}
