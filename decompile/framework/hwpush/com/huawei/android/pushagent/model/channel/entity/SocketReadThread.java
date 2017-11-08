package com.huawei.android.pushagent.model.channel.entity;

import android.content.Context;
import android.net.Proxy;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.text.TextUtils;
import com.huawei.android.pushagent.PushService;
import com.huawei.android.pushagent.datatype.PushException;
import com.huawei.android.pushagent.datatype.PushException.ErrorType;
import com.huawei.bd.Reporter;
import defpackage.aa;
import defpackage.ac;
import defpackage.ad;
import defpackage.ae;
import defpackage.au;
import defpackage.aw;
import defpackage.bi;
import defpackage.bv;
import defpackage.j;
import defpackage.r;
import defpackage.z;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.Date;

public abstract class SocketReadThread extends Thread {
    private j ah = null;
    public ConnectEntity ai = null;
    public Context mContext = null;

    public enum SocketEvent {
        SocketEvent_CONNECTING,
        SocketEvent_CONNECTED,
        SocketEvent_CLOSE,
        SocketEvent_MSG_RECEIVED
    }

    public SocketReadThread(ConnectEntity connectEntity) {
        super("SocketRead_" + new SimpleDateFormat("HH:mm:ss").format(new Date()));
        this.ai = connectEntity;
        this.mContext = connectEntity.context;
        this.ah = connectEntity.Q;
    }

    private Socket a(String str, int i, boolean z) {
        Throwable e;
        Socket socket;
        try {
            socket = new Socket();
            try {
                String property;
                String str2;
                int parseInt;
                socket.getTcpNoDelay();
                if (this instanceof z) {
                    if (bv.ct()) {
                        aw.i("PushLog2841", "isSupportCtrlSocketV2, ctrlSocket");
                        bv.c(1, au.c(socket));
                    } else {
                        au.ctrlSockets(1, au.c(socket));
                    }
                }
                if (VERSION.SDK_INT >= 11) {
                    String property2 = System.getProperty("http.proxyHost");
                    property = System.getProperty("http.proxyPort");
                    if (property == null) {
                        property = "-1";
                    }
                    str2 = property2;
                    parseInt = Integer.parseInt(property);
                } else {
                    str2 = Proxy.getHost(this.mContext);
                    parseInt = Proxy.getPort(this.mContext);
                }
                int G = au.G(this.mContext);
                a(SocketEvent.SocketEvent_CONNECTING, new Bundle());
                aw.i("PushLog2841", "enter createSocket " + bi.w(str));
                boolean z2 = (TextUtils.isEmpty(str2) || -1 == parseInt || 1 == G) ? false : true;
                boolean ai = ae.l(this.mContext).ai();
                aw.i("PushLog2841", "useProxy is valid:" + z2 + ", allow proxy:" + ai);
                if (z && z2 && ai) {
                    aw.i("PushLog2841", "use Proxy " + str2 + ":" + parseInt + " to connect to push server.");
                    socket.connect(new InetSocketAddress(str2, parseInt), ((int) ae.l(this.mContext).I()) * 1000);
                    property = "CONNECT " + str + ":" + i;
                    socket.getOutputStream().write((property + " HTTP/1.1\r\nHost: " + property + "\r\n\r\n").getBytes("UTF-8"));
                    InputStream inputStream = socket.getInputStream();
                    StringBuilder stringBuilder = new StringBuilder(100);
                    G = 0;
                    do {
                        char read = (char) inputStream.read();
                        stringBuilder.append(read);
                        G = ((G == 0 || G == 2) && read == '\r') ? G + 1 : ((G == 1 || G == 3) && read == '\n') ? G + 1 : 0;
                    } while (G != 4);
                    property = new BufferedReader(new StringReader(stringBuilder.toString())).readLine();
                    if (property != null) {
                        aw.d("PushLog2841", "read data:" + bi.w(property));
                    }
                } else {
                    aw.i("PushLog2841", "create socket without proxy");
                    socket.connect(new InetSocketAddress(str, i), ((int) ae.l(this.mContext).I()) * 1000);
                }
                aw.i("PushLog2841", "write the lastcontectsucc_time to the pushConfig.xml file");
                socket.setSoTimeout(((int) ae.l(this.mContext).I()) * 1000);
                return socket;
            } catch (UnsupportedEncodingException e2) {
                e = e2;
                aw.d("PushLog2841", "call getBytes cause:" + e.toString(), e);
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (Throwable e3) {
                        aw.d("PushLog2841", "close socket cause:" + e3.toString(), e3);
                    }
                }
                throw new PushException("create socket failed", ErrorType.Err_Connect);
            } catch (SocketException e4) {
                e3 = e4;
                aw.d("PushLog2841", "call setSoTimeout cause:" + e3.toString(), e3);
                if (socket != null) {
                    socket.close();
                }
                throw new PushException("create socket failed", ErrorType.Err_Connect);
            } catch (IOException e5) {
                e3 = e5;
                aw.d("PushLog2841", "call connect cause:" + e3.toString(), e3);
                if (socket != null) {
                    socket.close();
                }
                throw new PushException("create socket failed", ErrorType.Err_Connect);
            } catch (Exception e6) {
                e3 = e6;
                aw.d("PushLog2841", "call createSocket cause:" + e3.toString(), e3);
                if (socket != null) {
                    socket.close();
                }
                throw new PushException("create socket failed", ErrorType.Err_Connect);
            }
        } catch (UnsupportedEncodingException e7) {
            e3 = e7;
            socket = null;
            aw.d("PushLog2841", "call getBytes cause:" + e3.toString(), e3);
            if (socket != null) {
                socket.close();
            }
            throw new PushException("create socket failed", ErrorType.Err_Connect);
        } catch (SocketException e8) {
            e3 = e8;
            socket = null;
            aw.d("PushLog2841", "call setSoTimeout cause:" + e3.toString(), e3);
            if (socket != null) {
                socket.close();
            }
            throw new PushException("create socket failed", ErrorType.Err_Connect);
        } catch (IOException e9) {
            e3 = e9;
            socket = null;
            aw.d("PushLog2841", "call connect cause:" + e3.toString(), e3);
            if (socket != null) {
                socket.close();
            }
            throw new PushException("create socket failed", ErrorType.Err_Connect);
        } catch (Exception e10) {
            e3 = e10;
            socket = null;
            aw.d("PushLog2841", "call createSocket cause:" + e3.toString(), e3);
            if (socket != null) {
                socket.close();
            }
            throw new PushException("create socket failed", ErrorType.Err_Connect);
        }
    }

    private void a(SocketEvent socketEvent, Bundle bundle) {
        this.ai.a(socketEvent, bundle);
    }

    private boolean bk() {
        Socket socket = null;
        try {
            long currentTimeMillis = System.currentTimeMillis();
            aw.d("PushLog2841", "start to create socket");
            if (this.ah == null || this.ah.E == null || this.ah.E.length() == 0) {
                aw.e("PushLog2841", "the addr is " + this.ah + " is invalid");
                return false;
            } else if (this.ah.F == null) {
                aw.e("PushLog2841", "config sslconetEntity.channelType cfgErr:" + this.ah.F + " cannot connect!!");
                return false;
            } else {
                socket = a(this.ah.E, this.ah.port, this.ah.G);
                aw.i("PushLog2841", "conetEntity.channelType:" + this.ah.F);
                switch (r.aj[this.ah.F.ordinal()]) {
                    case Reporter.ACTIVITY_CREATE /*1*/:
                        this.ai.S = new ac(this.mContext);
                        break;
                    case Reporter.ACTIVITY_RESUME /*2*/:
                        this.ai.S = new ad(this.mContext);
                        break;
                    case Reporter.ACTIVITY_PAUSE /*3*/:
                        this.ai.S = new ad(this.mContext);
                        break;
                    case Reporter.ACTIVITY_DESTROY /*4*/:
                        this.ai.S = new aa(this.mContext);
                        break;
                    default:
                        aw.e("PushLog2841", "conetEntity.channelType is invalid:" + this.ah.F);
                        PushService.c().stopService();
                        socket.close();
                        return false;
                }
                if (this.ai.S.a(socket)) {
                    socket.setSoTimeout(0);
                    aw.i("PushLog2841", "connect cost " + (System.currentTimeMillis() - currentTimeMillis) + " ms, result:" + this.ai.S.hasConnection());
                    if (this.ai.S.hasConnection()) {
                        InetSocketAddress inetSocketAddress = new InetSocketAddress(this.ah.E, this.ah.port);
                        Bundle bundle = new Bundle();
                        bundle.putString("server_ip", inetSocketAddress.getAddress().getHostAddress());
                        bundle.putInt("server_port", inetSocketAddress.getPort());
                        bundle.putString("client_ip", socket.getLocalAddress().getHostAddress());
                        bundle.putInt("client_port", socket.getLocalPort());
                        bundle.putInt("channelEntity", this.ai.bb().ordinal());
                        this.ai.a(SocketEvent.SocketEvent_CONNECTED, bundle);
                        return true;
                    }
                    aw.e("PushLog2841", "Socket connect failed");
                    throw new PushException("create SSLSocket failed", ErrorType.Err_Connect);
                }
                aw.e("PushLog2841", "call conetEntity.channel.init failed!!");
                socket.close();
                throw new PushException("init socket error", ErrorType.Err_Connect);
            }
        } catch (Throwable e) {
            aw.d("PushLog2841", "call connectSync cause " + e.toString(), e);
            if (socket != null) {
                try {
                    socket.close();
                } catch (Throwable e2) {
                    aw.a("PushLog2841", "close socket cause:" + e2.toString(), e2);
                }
            }
            throw new PushException(e, ErrorType.Err_Connect);
        }
    }

    public abstract void bl();

    public void run() {
        long currentTimeMillis = System.currentTimeMillis();
        try {
            if (bk()) {
                currentTimeMillis = System.currentTimeMillis();
                bl();
            }
            aw.d("PushLog2841", "normal to quit.");
            Bundle bundle = new Bundle();
            bundle.putLong("connect_time", System.currentTimeMillis() - currentTimeMillis);
            a(SocketEvent.SocketEvent_CLOSE, bundle);
            aw.i("PushLog2841", "total connect Time:" + (System.currentTimeMillis() - currentTimeMillis) + " process quit, so close socket");
            if (this.ai.S != null) {
                try {
                    this.ai.S.close();
                } catch (Exception e) {
                }
            }
        } catch (Throwable e2) {
            aw.d("PushLog2841", "connect occurs :" + e2.toString(), e2);
            Serializable serializable = e2.type;
            Bundle bundle2 = new Bundle();
            if (serializable != null) {
                bundle2.putSerializable("errorType", serializable);
            }
            bundle2.putString("push_exception", e2.toString());
            a(SocketEvent.SocketEvent_CLOSE, bundle2);
            aw.i("PushLog2841", "total connect Time:" + (System.currentTimeMillis() - currentTimeMillis) + " process quit, so close socket");
            if (this.ai.S != null) {
                try {
                    this.ai.S.close();
                } catch (Exception e3) {
                }
            }
        } catch (Throwable e22) {
            aw.d("PushLog2841", "connect cause :" + e22.toString(), e22);
            Bundle bundle3 = new Bundle();
            bundle3.putString("push_exception", e22.toString());
            a(SocketEvent.SocketEvent_CLOSE, bundle3);
            aw.i("PushLog2841", "total connect Time:" + (System.currentTimeMillis() - currentTimeMillis) + " process quit, so close socket");
            if (this.ai.S != null) {
                try {
                    this.ai.S.close();
                } catch (Exception e4) {
                }
            }
        } catch (Throwable th) {
            aw.i("PushLog2841", "total connect Time:" + (System.currentTimeMillis() - currentTimeMillis) + " process quit, so close socket");
            if (this.ai.S != null) {
                try {
                    this.ai.S.close();
                } catch (Exception e5) {
                }
            }
        }
        aw.d("PushLog2841", "connect thread exit!");
    }
}
