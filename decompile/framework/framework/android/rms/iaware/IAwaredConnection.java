package android.rms.iaware;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.LocalSocketAddress.Namespace;
import java.io.IOException;
import java.io.OutputStream;

public class IAwaredConnection {
    private static final String LOCAL_SOCKET_NAME = "iawared";
    private static final String TAG = "IAwaredConnection";
    private static IAwaredConnection instance = null;
    private OutputStream outStream = null;
    private LocalSocket sock = null;

    private IAwaredConnection() {
    }

    public static IAwaredConnection getInstance() {
        IAwaredConnection iAwaredConnection;
        synchronized (IAwaredConnection.class) {
            if (instance == null) {
                instance = new IAwaredConnection();
            }
            iAwaredConnection = instance;
        }
        return iAwaredConnection;
    }

    public synchronized void create() {
        if (!createImpl()) {
            AwareLog.e(TAG, "Failed to create connection");
        }
    }

    public synchronized void destroy() {
        destroyImpl();
    }

    public boolean sendPacket(byte[] msg) {
        if (msg != null && msg.length != 0) {
            return sendPacket(msg, 0, msg.length);
        }
        AwareLog.e(TAG, "Parameter check failed");
        return false;
    }

    public synchronized boolean sendPacket(byte[] msg, int offset, int count) {
        if (msg != null && offset >= 0 && count > 0) {
            if (offset <= msg.length - count) {
                if (createImpl()) {
                    try {
                        this.outStream.write(msg, offset, count);
                        this.outStream.flush();
                        return true;
                    } catch (IOException e) {
                        AwareLog.e(TAG, "Failed to write output stream, IOException");
                        destroyImpl();
                        return false;
                    }
                }
                AwareLog.e(TAG, "Failed to create connection");
                return false;
            }
        }
        AwareLog.e(TAG, "Parameter check failed");
        return false;
    }

    private boolean createImpl() {
        if (this.sock != null) {
            return true;
        }
        try {
            this.sock = new LocalSocket(3);
            this.sock.connect(new LocalSocketAddress(LOCAL_SOCKET_NAME, Namespace.RESERVED));
            this.outStream = this.sock.getOutputStream();
            return true;
        } catch (IOException e) {
            AwareLog.e(TAG, "Failed to create connection, IOException");
            destroyImpl();
            return false;
        } catch (SecurityException e2) {
            AwareLog.e(TAG, "Failed to create connection, SecurityException");
            destroyImpl();
            return false;
        }
    }

    private void destroyImpl() {
        if (this.outStream != null) {
            try {
                this.outStream.close();
            } catch (IOException e) {
                AwareLog.e(TAG, "Failed to close output stream, IOException");
            } catch (Throwable th) {
                this.outStream = null;
            }
            this.outStream = null;
        }
        if (this.sock != null) {
            try {
                this.sock.close();
            } catch (IOException e2) {
                AwareLog.e(TAG, "Failed to close local socket, IOException");
            } catch (Throwable th2) {
                this.sock = null;
            }
            this.sock = null;
        }
    }
}
