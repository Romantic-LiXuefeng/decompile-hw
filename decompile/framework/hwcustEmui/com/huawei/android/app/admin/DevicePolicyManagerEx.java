package com.huawei.android.app.admin;

import android.content.ComponentName;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.util.Log;

public class DevicePolicyManagerEx {
    private static final String DESCRIPTOR = "android.app.admin.IDevicePolicyManager";
    private static final boolean HWLOGW_E = true;
    private static final String TAG = "DevicePolicyManagerEx";
    private static int TRANSACTION_getAllowSimplePassword = 202;
    private static int TRANSACTION_saveCurrentPwdStatus = 203;
    private static int TRANSACTION_setAllowSimplePassword = 201;

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public static void setAllowSimplePassword(ComponentName who, boolean mode) {
        int i = 1;
        Parcel _data = Parcel.obtain();
        Parcel _reply = Parcel.obtain();
        try {
            IBinder binder = ServiceManager.getService("device_policy");
            if (binder != null) {
                _data.writeInterfaceToken(DESCRIPTOR);
                if (who != null) {
                    _data.writeInt(1);
                    who.writeToParcel(_data, 0);
                } else {
                    _data.writeInt(0);
                }
                if (!mode) {
                    i = 0;
                }
                _data.writeInt(i);
                _data.writeInt(UserHandle.myUserId());
                binder.transact(TRANSACTION_setAllowSimplePassword, _data, _reply, 0);
                _reply.readException();
            } else {
                Log.e(TAG, "Call remote setAllowSimplePassword failed, can't get DEVICE_POLICY_SERVICE");
            }
            _reply.recycle();
            _data.recycle();
        } catch (RemoteException localRemoteException) {
            localRemoteException.printStackTrace();
        } catch (Throwable th) {
            _reply.recycle();
            _data.recycle();
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public static boolean getAllowSimplePassword(ComponentName who) {
        Parcel _data = Parcel.obtain();
        Parcel _reply = Parcel.obtain();
        boolean _result = false;
        try {
            IBinder binder = ServiceManager.getService("device_policy");
            if (binder != null) {
                _data.writeInterfaceToken(DESCRIPTOR);
                if (who != null) {
                    _data.writeInt(1);
                    who.writeToParcel(_data, 0);
                } else {
                    _data.writeInt(0);
                }
                _data.writeInt(UserHandle.myUserId());
                binder.transact(TRANSACTION_getAllowSimplePassword, _data, _reply, 0);
                _reply.readException();
                _result = _reply.readInt() != 0 ? HWLOGW_E : false;
            } else {
                Log.e(TAG, "Call remote getAllowSimplePassword failed, can't get DEVICE_POLICY_SERVICE");
            }
            _reply.recycle();
            _data.recycle();
        } catch (RemoteException localRemoteException) {
            localRemoteException.printStackTrace();
        } catch (Throwable th) {
            _reply.recycle();
            _data.recycle();
        }
        return _result;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public static void saveCurrentPwdStatus(boolean isCurrentPwdSimple) {
        int i = 0;
        Parcel _data = Parcel.obtain();
        Parcel _reply = Parcel.obtain();
        try {
            IBinder binder = ServiceManager.getService("device_policy");
            if (binder != null) {
                _data.writeInterfaceToken(DESCRIPTOR);
                if (isCurrentPwdSimple) {
                    i = 1;
                }
                _data.writeInt(i);
                _data.writeInt(UserHandle.myUserId());
                binder.transact(TRANSACTION_saveCurrentPwdStatus, _data, _reply, 0);
                _reply.readException();
            } else {
                Log.e(TAG, "Call remote saveCurrentPwdStatus failed, can't get DEVICE_POLICY_SERVICE");
            }
            _reply.recycle();
            _data.recycle();
        } catch (RemoteException localRemoteException) {
            localRemoteException.printStackTrace();
        } catch (Throwable th) {
            _reply.recycle();
            _data.recycle();
        }
    }
}
