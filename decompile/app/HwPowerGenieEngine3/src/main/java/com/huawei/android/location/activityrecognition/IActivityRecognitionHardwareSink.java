package com.huawei.android.location.activityrecognition;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import com.huawei.powergenie.integration.adapter.NativeAdapter;

public interface IActivityRecognitionHardwareSink extends IInterface {

    public static abstract class Stub extends Binder implements IActivityRecognitionHardwareSink {

        private static class Proxy implements IActivityRecognitionHardwareSink {
            private IBinder mRemote;

            Proxy(IBinder remote) {
                this.mRemote = remote;
            }

            public IBinder asBinder() {
                return this.mRemote;
            }

            public void onActivityChanged(HwActivityChangedEvent event) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken("com.huawei.android.location.activityrecognition.IActivityRecognitionHardwareSink");
                    if (event == null) {
                        _data.writeInt(0);
                    } else {
                        _data.writeInt(1);
                        event.writeToParcel(_data, 0);
                    }
                    this.mRemote.transact(1, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
        }

        public Stub() {
            attachInterface(this, "com.huawei.android.location.activityrecognition.IActivityRecognitionHardwareSink");
        }

        public static IActivityRecognitionHardwareSink asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface("com.huawei.android.location.activityrecognition.IActivityRecognitionHardwareSink");
            if (iin != null && (iin instanceof IActivityRecognitionHardwareSink)) {
                return (IActivityRecognitionHardwareSink) iin;
            }
            return new Proxy(obj);
        }

        public IBinder asBinder() {
            return this;
        }

        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            switch (code) {
                case NativeAdapter.PLATFORM_MTK /*1*/:
                    HwActivityChangedEvent hwActivityChangedEvent;
                    data.enforceInterface("com.huawei.android.location.activityrecognition.IActivityRecognitionHardwareSink");
                    if (data.readInt() == 0) {
                        hwActivityChangedEvent = null;
                    } else {
                        hwActivityChangedEvent = (HwActivityChangedEvent) HwActivityChangedEvent.CREATOR.createFromParcel(data);
                    }
                    onActivityChanged(hwActivityChangedEvent);
                    reply.writeNoException();
                    return true;
                case 1598968902:
                    reply.writeString("com.huawei.android.location.activityrecognition.IActivityRecognitionHardwareSink");
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }
    }

    void onActivityChanged(HwActivityChangedEvent hwActivityChangedEvent) throws RemoteException;
}
