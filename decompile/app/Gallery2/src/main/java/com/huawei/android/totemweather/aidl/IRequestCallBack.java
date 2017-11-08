package com.huawei.android.totemweather.aidl;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

public interface IRequestCallBack extends IInterface {

    public static abstract class Stub extends Binder implements IRequestCallBack {

        private static class Proxy implements IRequestCallBack {
            private IBinder mRemote;

            Proxy(IBinder remote) {
                this.mRemote = remote;
            }

            public IBinder asBinder() {
                return this.mRemote;
            }

            public void onRequestResult(String weatherJsonData, RequestData requestData) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.huawei.android.totemweather.aidl.IRequestCallBack");
                    _data.writeString(weatherJsonData);
                    if (requestData != null) {
                        _data.writeInt(1);
                        requestData.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    this.mRemote.transact(1, _data, _reply, 0);
                    _reply.readException();
                    if (_reply.readInt() != 0) {
                        requestData.readFromParcel(_reply);
                    }
                    _reply.recycle();
                    _data.recycle();
                } catch (Throwable th) {
                    _reply.recycle();
                    _data.recycle();
                }
            }
        }

        public Stub() {
            attachInterface(this, "com.huawei.android.totemweather.aidl.IRequestCallBack");
        }

        public static IRequestCallBack asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface("com.huawei.android.totemweather.aidl.IRequestCallBack");
            if (iin == null || !(iin instanceof IRequestCallBack)) {
                return new Proxy(obj);
            }
            return (IRequestCallBack) iin;
        }

        public IBinder asBinder() {
            return this;
        }

        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            switch (code) {
                case 1:
                    RequestData requestData;
                    data.enforceInterface("com.huawei.android.totemweather.aidl.IRequestCallBack");
                    String _arg0 = data.readString();
                    if (data.readInt() != 0) {
                        requestData = (RequestData) RequestData.CREATOR.createFromParcel(data);
                    } else {
                        requestData = null;
                    }
                    onRequestResult(_arg0, requestData);
                    reply.writeNoException();
                    if (requestData != null) {
                        reply.writeInt(1);
                        requestData.writeToParcel(reply, 1);
                    } else {
                        reply.writeInt(0);
                    }
                    return true;
                case 1598968902:
                    reply.writeString("com.huawei.android.totemweather.aidl.IRequestCallBack");
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }
    }

    void onRequestResult(String str, RequestData requestData) throws RemoteException;
}
