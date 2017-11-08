package com.huawei.servicehost.normal;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.view.Surface;
import com.huawei.servicehost.ImageDescriptor;

public interface IIPRequest4CreatePipeline extends IInterface {

    public static abstract class Stub extends Binder implements IIPRequest4CreatePipeline {
        private static final String DESCRIPTOR = "com.huawei.servicehost.normal.IIPRequest4CreatePipeline";
        static final int TRANSACTION_getCamera1CapImageConsumer = 21;
        static final int TRANSACTION_getCamera1CapSurface = 9;
        static final int TRANSACTION_getCamera1ImageConsumer = 17;
        static final int TRANSACTION_getCamera1Surface = 3;
        static final int TRANSACTION_getCamera2CapImageConsumer = 22;
        static final int TRANSACTION_getCamera2CapSurface = 10;
        static final int TRANSACTION_getCamera2ImageConsumer = 18;
        static final int TRANSACTION_getCamera2Surface = 4;
        static final int TRANSACTION_getDmapImageConsumer = 20;
        static final int TRANSACTION_getDmapSurface = 8;
        static final int TRANSACTION_getLayout = 1;
        static final int TRANSACTION_getMetadataImageConsumer = 19;
        static final int TRANSACTION_getMetadataSurface = 7;
        static final int TRANSACTION_setCamera1CapImageConsumer = 15;
        static final int TRANSACTION_setCamera1ImageConsumer = 11;
        static final int TRANSACTION_setCamera2CapImageConsumer = 16;
        static final int TRANSACTION_setCamera2ImageConsumer = 12;
        static final int TRANSACTION_setCaptureFormat = 24;
        static final int TRANSACTION_setDmapImageConsumer = 14;
        static final int TRANSACTION_setLayout = 2;
        static final int TRANSACTION_setMetadataImageConsumer = 13;
        static final int TRANSACTION_setPreview1Surface = 5;
        static final int TRANSACTION_setPreview2Surface = 6;
        static final int TRANSACTION_setPreviewFormat = 23;

        private static class Proxy implements IIPRequest4CreatePipeline {
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

            public String getLayout() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(1, _data, _reply, 0);
                    _reply.readException();
                    String _result = _reply.readString();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void setLayout(String val) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeString(val);
                    this.mRemote.transact(2, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public Surface getCamera1Surface() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    Surface surface;
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(3, _data, _reply, 0);
                    _reply.readException();
                    if (_reply.readInt() != 0) {
                        surface = (Surface) Surface.CREATOR.createFromParcel(_reply);
                    } else {
                        surface = null;
                    }
                    _reply.recycle();
                    _data.recycle();
                    return surface;
                } catch (Throwable th) {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public Surface getCamera2Surface() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    Surface surface;
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(4, _data, _reply, 0);
                    _reply.readException();
                    if (_reply.readInt() != 0) {
                        surface = (Surface) Surface.CREATOR.createFromParcel(_reply);
                    } else {
                        surface = null;
                    }
                    _reply.recycle();
                    _data.recycle();
                    return surface;
                } catch (Throwable th) {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void setPreview1Surface(Surface val) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (val != null) {
                        _data.writeInt(1);
                        val.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    this.mRemote.transact(5, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void setPreview2Surface(Surface val) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (val != null) {
                        _data.writeInt(1);
                        val.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    this.mRemote.transact(6, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public Surface getMetadataSurface() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    Surface surface;
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(7, _data, _reply, 0);
                    _reply.readException();
                    if (_reply.readInt() != 0) {
                        surface = (Surface) Surface.CREATOR.createFromParcel(_reply);
                    } else {
                        surface = null;
                    }
                    _reply.recycle();
                    _data.recycle();
                    return surface;
                } catch (Throwable th) {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public Surface getDmapSurface() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    Surface surface;
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(8, _data, _reply, 0);
                    _reply.readException();
                    if (_reply.readInt() != 0) {
                        surface = (Surface) Surface.CREATOR.createFromParcel(_reply);
                    } else {
                        surface = null;
                    }
                    _reply.recycle();
                    _data.recycle();
                    return surface;
                } catch (Throwable th) {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public Surface getCamera1CapSurface() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    Surface surface;
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(Stub.TRANSACTION_getCamera1CapSurface, _data, _reply, 0);
                    _reply.readException();
                    if (_reply.readInt() != 0) {
                        surface = (Surface) Surface.CREATOR.createFromParcel(_reply);
                    } else {
                        surface = null;
                    }
                    _reply.recycle();
                    _data.recycle();
                    return surface;
                } catch (Throwable th) {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public Surface getCamera2CapSurface() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    Surface surface;
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(10, _data, _reply, 0);
                    _reply.readException();
                    if (_reply.readInt() != 0) {
                        surface = (Surface) Surface.CREATOR.createFromParcel(_reply);
                    } else {
                        surface = null;
                    }
                    _reply.recycle();
                    _data.recycle();
                    return surface;
                } catch (Throwable th) {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void setCamera1ImageConsumer(IBinder val) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeStrongBinder(val);
                    this.mRemote.transact(11, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void setCamera2ImageConsumer(IBinder val) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeStrongBinder(val);
                    this.mRemote.transact(12, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void setMetadataImageConsumer(IBinder val) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeStrongBinder(val);
                    this.mRemote.transact(13, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void setDmapImageConsumer(IBinder val) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeStrongBinder(val);
                    this.mRemote.transact(14, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void setCamera1CapImageConsumer(IBinder val) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeStrongBinder(val);
                    this.mRemote.transact(15, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void setCamera2CapImageConsumer(IBinder val) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    _data.writeStrongBinder(val);
                    this.mRemote.transact(16, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public IBinder getCamera1ImageConsumer() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(17, _data, _reply, 0);
                    _reply.readException();
                    IBinder _result = _reply.readStrongBinder();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public IBinder getCamera2ImageConsumer() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(18, _data, _reply, 0);
                    _reply.readException();
                    IBinder _result = _reply.readStrongBinder();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public IBinder getMetadataImageConsumer() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(19, _data, _reply, 0);
                    _reply.readException();
                    IBinder _result = _reply.readStrongBinder();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public IBinder getDmapImageConsumer() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(20, _data, _reply, 0);
                    _reply.readException();
                    IBinder _result = _reply.readStrongBinder();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public IBinder getCamera1CapImageConsumer() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(21, _data, _reply, 0);
                    _reply.readException();
                    IBinder _result = _reply.readStrongBinder();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public IBinder getCamera2CapImageConsumer() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    this.mRemote.transact(22, _data, _reply, 0);
                    _reply.readException();
                    IBinder _result = _reply.readStrongBinder();
                    return _result;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void setPreviewFormat(ImageDescriptor val) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (val != null) {
                        _data.writeInt(1);
                        val.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    this.mRemote.transact(Stub.TRANSACTION_setPreviewFormat, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            public void setCaptureFormat(ImageDescriptor val) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(Stub.DESCRIPTOR);
                    if (val != null) {
                        _data.writeInt(1);
                        val.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    this.mRemote.transact(Stub.TRANSACTION_setCaptureFormat, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
        }

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IIPRequest4CreatePipeline asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin == null || !(iin instanceof IIPRequest4CreatePipeline)) {
                return new Proxy(obj);
            }
            return (IIPRequest4CreatePipeline) iin;
        }

        public IBinder asBinder() {
            return this;
        }

        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            Surface _result;
            Surface surface;
            IBinder _result2;
            ImageDescriptor imageDescriptor;
            switch (code) {
                case 1:
                    data.enforceInterface(DESCRIPTOR);
                    String _result3 = getLayout();
                    reply.writeNoException();
                    reply.writeString(_result3);
                    return true;
                case 2:
                    data.enforceInterface(DESCRIPTOR);
                    setLayout(data.readString());
                    reply.writeNoException();
                    return true;
                case 3:
                    data.enforceInterface(DESCRIPTOR);
                    _result = getCamera1Surface();
                    reply.writeNoException();
                    if (_result != null) {
                        reply.writeInt(1);
                        _result.writeToParcel(reply, 1);
                    } else {
                        reply.writeInt(0);
                    }
                    return true;
                case 4:
                    data.enforceInterface(DESCRIPTOR);
                    _result = getCamera2Surface();
                    reply.writeNoException();
                    if (_result != null) {
                        reply.writeInt(1);
                        _result.writeToParcel(reply, 1);
                    } else {
                        reply.writeInt(0);
                    }
                    return true;
                case 5:
                    data.enforceInterface(DESCRIPTOR);
                    if (data.readInt() != 0) {
                        surface = (Surface) Surface.CREATOR.createFromParcel(data);
                    } else {
                        surface = null;
                    }
                    setPreview1Surface(surface);
                    reply.writeNoException();
                    return true;
                case 6:
                    data.enforceInterface(DESCRIPTOR);
                    if (data.readInt() != 0) {
                        surface = (Surface) Surface.CREATOR.createFromParcel(data);
                    } else {
                        surface = null;
                    }
                    setPreview2Surface(surface);
                    reply.writeNoException();
                    return true;
                case 7:
                    data.enforceInterface(DESCRIPTOR);
                    _result = getMetadataSurface();
                    reply.writeNoException();
                    if (_result != null) {
                        reply.writeInt(1);
                        _result.writeToParcel(reply, 1);
                    } else {
                        reply.writeInt(0);
                    }
                    return true;
                case 8:
                    data.enforceInterface(DESCRIPTOR);
                    _result = getDmapSurface();
                    reply.writeNoException();
                    if (_result != null) {
                        reply.writeInt(1);
                        _result.writeToParcel(reply, 1);
                    } else {
                        reply.writeInt(0);
                    }
                    return true;
                case TRANSACTION_getCamera1CapSurface /*9*/:
                    data.enforceInterface(DESCRIPTOR);
                    _result = getCamera1CapSurface();
                    reply.writeNoException();
                    if (_result != null) {
                        reply.writeInt(1);
                        _result.writeToParcel(reply, 1);
                    } else {
                        reply.writeInt(0);
                    }
                    return true;
                case 10:
                    data.enforceInterface(DESCRIPTOR);
                    _result = getCamera2CapSurface();
                    reply.writeNoException();
                    if (_result != null) {
                        reply.writeInt(1);
                        _result.writeToParcel(reply, 1);
                    } else {
                        reply.writeInt(0);
                    }
                    return true;
                case 11:
                    data.enforceInterface(DESCRIPTOR);
                    setCamera1ImageConsumer(data.readStrongBinder());
                    reply.writeNoException();
                    return true;
                case 12:
                    data.enforceInterface(DESCRIPTOR);
                    setCamera2ImageConsumer(data.readStrongBinder());
                    reply.writeNoException();
                    return true;
                case 13:
                    data.enforceInterface(DESCRIPTOR);
                    setMetadataImageConsumer(data.readStrongBinder());
                    reply.writeNoException();
                    return true;
                case 14:
                    data.enforceInterface(DESCRIPTOR);
                    setDmapImageConsumer(data.readStrongBinder());
                    reply.writeNoException();
                    return true;
                case 15:
                    data.enforceInterface(DESCRIPTOR);
                    setCamera1CapImageConsumer(data.readStrongBinder());
                    reply.writeNoException();
                    return true;
                case 16:
                    data.enforceInterface(DESCRIPTOR);
                    setCamera2CapImageConsumer(data.readStrongBinder());
                    reply.writeNoException();
                    return true;
                case 17:
                    data.enforceInterface(DESCRIPTOR);
                    _result2 = getCamera1ImageConsumer();
                    reply.writeNoException();
                    reply.writeStrongBinder(_result2);
                    return true;
                case 18:
                    data.enforceInterface(DESCRIPTOR);
                    _result2 = getCamera2ImageConsumer();
                    reply.writeNoException();
                    reply.writeStrongBinder(_result2);
                    return true;
                case 19:
                    data.enforceInterface(DESCRIPTOR);
                    _result2 = getMetadataImageConsumer();
                    reply.writeNoException();
                    reply.writeStrongBinder(_result2);
                    return true;
                case 20:
                    data.enforceInterface(DESCRIPTOR);
                    _result2 = getDmapImageConsumer();
                    reply.writeNoException();
                    reply.writeStrongBinder(_result2);
                    return true;
                case 21:
                    data.enforceInterface(DESCRIPTOR);
                    _result2 = getCamera1CapImageConsumer();
                    reply.writeNoException();
                    reply.writeStrongBinder(_result2);
                    return true;
                case 22:
                    data.enforceInterface(DESCRIPTOR);
                    _result2 = getCamera2CapImageConsumer();
                    reply.writeNoException();
                    reply.writeStrongBinder(_result2);
                    return true;
                case TRANSACTION_setPreviewFormat /*23*/:
                    data.enforceInterface(DESCRIPTOR);
                    if (data.readInt() != 0) {
                        imageDescriptor = (ImageDescriptor) ImageDescriptor.CREATOR.createFromParcel(data);
                    } else {
                        imageDescriptor = null;
                    }
                    setPreviewFormat(imageDescriptor);
                    reply.writeNoException();
                    return true;
                case TRANSACTION_setCaptureFormat /*24*/:
                    data.enforceInterface(DESCRIPTOR);
                    if (data.readInt() != 0) {
                        imageDescriptor = (ImageDescriptor) ImageDescriptor.CREATOR.createFromParcel(data);
                    } else {
                        imageDescriptor = null;
                    }
                    setCaptureFormat(imageDescriptor);
                    reply.writeNoException();
                    return true;
                case 1598968902:
                    reply.writeString(DESCRIPTOR);
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }
    }

    IBinder getCamera1CapImageConsumer() throws RemoteException;

    Surface getCamera1CapSurface() throws RemoteException;

    IBinder getCamera1ImageConsumer() throws RemoteException;

    Surface getCamera1Surface() throws RemoteException;

    IBinder getCamera2CapImageConsumer() throws RemoteException;

    Surface getCamera2CapSurface() throws RemoteException;

    IBinder getCamera2ImageConsumer() throws RemoteException;

    Surface getCamera2Surface() throws RemoteException;

    IBinder getDmapImageConsumer() throws RemoteException;

    Surface getDmapSurface() throws RemoteException;

    String getLayout() throws RemoteException;

    IBinder getMetadataImageConsumer() throws RemoteException;

    Surface getMetadataSurface() throws RemoteException;

    void setCamera1CapImageConsumer(IBinder iBinder) throws RemoteException;

    void setCamera1ImageConsumer(IBinder iBinder) throws RemoteException;

    void setCamera2CapImageConsumer(IBinder iBinder) throws RemoteException;

    void setCamera2ImageConsumer(IBinder iBinder) throws RemoteException;

    void setCaptureFormat(ImageDescriptor imageDescriptor) throws RemoteException;

    void setDmapImageConsumer(IBinder iBinder) throws RemoteException;

    void setLayout(String str) throws RemoteException;

    void setMetadataImageConsumer(IBinder iBinder) throws RemoteException;

    void setPreview1Surface(Surface surface) throws RemoteException;

    void setPreview2Surface(Surface surface) throws RemoteException;

    void setPreviewFormat(ImageDescriptor imageDescriptor) throws RemoteException;
}
