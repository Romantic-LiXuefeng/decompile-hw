package com.huawei.servicehost;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

public interface IImageAllocator extends IInterface {

    public static abstract class Stub extends Binder implements IImageAllocator {
        private static final String DESCRIPTOR = "com.huawei.servicehost.IImageAllocator";
        static final int TRANSACTION_createImageWrap = 1;

        private static class Proxy implements IImageAllocator {
            private IBinder mRemote;

            Proxy(IBinder remote) {
                this.mRemote = remote;
            }

            public IBinder asBinder() {
                return this.mRemote;
            }

            public String getInterfaceDescriptor() {
                return Stub.DESCRIPTOR;
            }

            public ImageWrap createImageWrap(ImageDescriptor val) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    ImageWrap imageWrap;
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (val != null) {
                        _data.writeInt(1);
                        val.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    this.mRemote.transact(1, _data, _reply, 0);
                    _reply.readException();
                    if (_reply.readInt() != 0) {
                        imageWrap = (ImageWrap) ImageWrap.CREATOR.createFromParcel(_reply);
                    } else {
                        imageWrap = null;
                    }
                    _reply.recycle();
                    _data.recycle();
                    return imageWrap;
                } catch (Throwable th) {
                    _reply.recycle();
                    _data.recycle();
                }
            }
        }

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IImageAllocator asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin == null || !(iin instanceof IImageAllocator)) {
                return new Proxy(obj);
            }
            return (IImageAllocator) iin;
        }

        public IBinder asBinder() {
            return this;
        }

        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            switch (code) {
                case 1:
                    ImageDescriptor imageDescriptor;
                    data.enforceInterface(DESCRIPTOR);
                    if (data.readInt() != 0) {
                        imageDescriptor = (ImageDescriptor) ImageDescriptor.CREATOR.createFromParcel(data);
                    } else {
                        imageDescriptor = null;
                    }
                    ImageWrap _result = createImageWrap(imageDescriptor);
                    reply.writeNoException();
                    if (_result != null) {
                        reply.writeInt(1);
                        _result.writeToParcel(reply, 1);
                    } else {
                        reply.writeInt(0);
                    }
                    return true;
                case 1598968902:
                    reply.writeString(DESCRIPTOR);
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }
    }

    ImageWrap createImageWrap(ImageDescriptor imageDescriptor) throws RemoteException;
}
