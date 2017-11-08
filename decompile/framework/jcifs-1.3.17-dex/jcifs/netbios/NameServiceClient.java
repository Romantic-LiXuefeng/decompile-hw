package jcifs.netbios;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.StringTokenizer;
import jcifs.Config;
import jcifs.util.Hexdump;
import jcifs.util.LogStream;

class NameServiceClient implements Runnable {
    static final int DEFAULT_RCV_BUF_SIZE = 576;
    static final int DEFAULT_RETRY_COUNT = 2;
    static final int DEFAULT_RETRY_TIMEOUT = 3000;
    static final int DEFAULT_SND_BUF_SIZE = 576;
    static final int DEFAULT_SO_TIMEOUT = 5000;
    private static final InetAddress LADDR = Config.getInetAddress("jcifs.netbios.laddr", null);
    private static final int LPORT = Config.getInt("jcifs.netbios.lport", 0);
    static final int NAME_SERVICE_UDP_PORT = 137;
    private static final int RCV_BUF_SIZE = Config.getInt("jcifs.netbios.rcv_buf_size", 576);
    static final int RESOLVER_BCAST = 2;
    static final int RESOLVER_LMHOSTS = 1;
    static final int RESOLVER_WINS = 3;
    private static final int RETRY_COUNT = Config.getInt("jcifs.netbios.retryCount", 2);
    private static final int RETRY_TIMEOUT = Config.getInt("jcifs.netbios.retryTimeout", DEFAULT_RETRY_TIMEOUT);
    private static final String RO = Config.getProperty("jcifs.resolveOrder");
    private static final int SND_BUF_SIZE = Config.getInt("jcifs.netbios.snd_buf_size", 576);
    private static final int SO_TIMEOUT = Config.getInt("jcifs.netbios.soTimeout", DEFAULT_SO_TIMEOUT);
    private static LogStream log = LogStream.getInstance();
    private final Object LOCK;
    InetAddress baddr;
    private int closeTimeout;
    private DatagramPacket in;
    InetAddress laddr;
    private int lport;
    private int nextNameTrnId;
    private DatagramPacket out;
    private byte[] rcv_buf;
    private int[] resolveOrder;
    private HashMap responseTable;
    private byte[] snd_buf;
    private DatagramSocket socket;
    private Thread thread;

    NameServiceClient() {
        this(LPORT, LADDR);
    }

    NameServiceClient(int lport, InetAddress laddr) {
        this.LOCK = new Object();
        this.responseTable = new HashMap();
        this.nextNameTrnId = 0;
        this.lport = lport;
        this.laddr = laddr;
        try {
            this.baddr = Config.getInetAddress("jcifs.netbios.baddr", InetAddress.getByName("255.255.255.255"));
        } catch (UnknownHostException e) {
        }
        this.snd_buf = new byte[SND_BUF_SIZE];
        this.rcv_buf = new byte[RCV_BUF_SIZE];
        this.out = new DatagramPacket(this.snd_buf, SND_BUF_SIZE, this.baddr, NAME_SERVICE_UDP_PORT);
        this.in = new DatagramPacket(this.rcv_buf, RCV_BUF_SIZE);
        if (RO != null && RO.length() != 0) {
            int[] tmp = new int[3];
            StringTokenizer st = new StringTokenizer(RO, ",");
            int i = 0;
            while (st.hasMoreTokens()) {
                String s = st.nextToken().trim();
                int i2;
                if (s.equalsIgnoreCase("LMHOSTS")) {
                    i2 = i + 1;
                    tmp[i] = 1;
                    i = i2;
                } else if (s.equalsIgnoreCase("WINS")) {
                    if (NbtAddress.getWINSAddress() == null) {
                        r5 = log;
                        if (LogStream.level > 1) {
                            log.println("NetBIOS resolveOrder specifies WINS however the jcifs.netbios.wins property has not been set");
                        }
                    } else {
                        i2 = i + 1;
                        tmp[i] = 3;
                        i = i2;
                    }
                } else if (s.equalsIgnoreCase("BCAST")) {
                    i2 = i + 1;
                    tmp[i] = 2;
                    i = i2;
                } else if (!s.equalsIgnoreCase("DNS")) {
                    r5 = log;
                    if (LogStream.level > 1) {
                        log.println("unknown resolver method: " + s);
                    }
                }
            }
            this.resolveOrder = new int[i];
            System.arraycopy(tmp, 0, this.resolveOrder, 0, i);
        } else if (NbtAddress.getWINSAddress() == null) {
            this.resolveOrder = new int[2];
            this.resolveOrder[0] = 1;
            this.resolveOrder[1] = 2;
        } else {
            this.resolveOrder = new int[3];
            this.resolveOrder[0] = 1;
            this.resolveOrder[1] = 3;
            this.resolveOrder[2] = 2;
        }
    }

    int getNextNameTrnId() {
        int i = this.nextNameTrnId + 1;
        this.nextNameTrnId = i;
        if ((i & 65535) == 0) {
            this.nextNameTrnId = 1;
        }
        return this.nextNameTrnId;
    }

    void ensureOpen(int timeout) throws IOException {
        this.closeTimeout = 0;
        if (SO_TIMEOUT != 0) {
            this.closeTimeout = Math.max(SO_TIMEOUT, timeout);
        }
        if (this.socket == null) {
            this.socket = new DatagramSocket(this.lport, this.laddr);
            this.thread = new Thread(this, "JCIFS-NameServiceClient");
            this.thread.setDaemon(true);
            this.thread.start();
        }
    }

    void tryClose() {
        synchronized (this.LOCK) {
            if (this.socket != null) {
                this.socket.close();
                this.socket = null;
            }
            this.thread = null;
            this.responseTable.clear();
        }
    }

    public void run() {
        while (this.thread == Thread.currentThread()) {
            try {
                this.in.setLength(RCV_BUF_SIZE);
                this.socket.setSoTimeout(this.closeTimeout);
                this.socket.receive(this.in);
                r3 = log;
                if (LogStream.level > 3) {
                    log.println("NetBIOS: new data read from socket");
                }
                NameServicePacket response = (NameServicePacket) this.responseTable.get(new Integer(NameServicePacket.readNameTrnId(this.rcv_buf, 0)));
                if (!(response == null || response.received)) {
                    synchronized (response) {
                        response.readWireFormat(this.rcv_buf, 0);
                        response.received = true;
                        r3 = log;
                        if (LogStream.level > 3) {
                            log.println(response);
                            Hexdump.hexdump(log, this.rcv_buf, 0, this.in.getLength());
                        }
                        response.notify();
                    }
                }
            } catch (SocketTimeoutException e) {
                tryClose();
                return;
            } catch (Exception ex) {
                try {
                    LogStream logStream;
                    logStream = log;
                    if (LogStream.level > 2) {
                        ex.printStackTrace(log);
                    }
                    tryClose();
                    return;
                } catch (Throwable th) {
                    tryClose();
                }
            }
        }
        tryClose();
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    void send(NameServicePacket request, NameServicePacket response, int timeout) throws IOException {
        Throwable th;
        int max = NbtAddress.NBNS.length;
        if (max == 0) {
            max = 1;
        }
        synchronized (response) {
            int max2 = max;
            Integer nid = null;
            while (true) {
                Integer num;
                max = max2 - 1;
                if (max2 <= 0) {
                    break;
                }
                try {
                    synchronized (this.LOCK) {
                        try {
                            request.nameTrnId = getNextNameTrnId();
                            num = new Integer(request.nameTrnId);
                            try {
                                this.out.setAddress(request.addr);
                                this.out.setLength(request.writeWireFormat(this.snd_buf, 0));
                                response.received = false;
                                this.responseTable.put(num, response);
                                ensureOpen(timeout + 1000);
                                this.socket.send(this.out);
                                LogStream logStream = log;
                                if (LogStream.level > 3) {
                                    log.println(request);
                                    Hexdump.hexdump(log, this.snd_buf, 0, this.out.getLength());
                                }
                            } catch (Throwable th2) {
                                th = th2;
                                throw th;
                            }
                        } catch (Throwable th3) {
                            th = th3;
                            num = nid;
                        }
                    }
                } catch (InterruptedException e) {
                    InterruptedException ie = e;
                    num = nid;
                } catch (Throwable th4) {
                    th = th4;
                    num = nid;
                }
                return;
                max2 = max;
                nid = num;
            }
            return;
        }
        try {
            throw new IOException(ie.getMessage());
        } catch (Throwable th5) {
            th = th5;
            this.responseTable.remove(num);
            throw th;
        }
    }

    NbtAddress[] getAllByName(Name name, InetAddress addr) throws UnknownHostException {
        boolean z;
        int n;
        NameQueryRequest request = new NameQueryRequest(name);
        NameQueryResponse response = new NameQueryResponse();
        if (addr == null) {
            addr = NbtAddress.getWINSAddress();
        }
        request.addr = addr;
        if (request.addr == null) {
            z = true;
        } else {
            z = false;
        }
        request.isBroadcast = z;
        if (request.isBroadcast) {
            request.addr = this.baddr;
            n = RETRY_COUNT;
        } else {
            request.isBroadcast = false;
            n = 1;
        }
        do {
            try {
                send(request, response, RETRY_TIMEOUT);
                if (!response.received || response.resultCode != 0) {
                    n--;
                    if (n <= 0) {
                        break;
                    }
                } else {
                    return response.addrEntry;
                }
            } catch (IOException ioe) {
                LogStream logStream = log;
                if (LogStream.level > 1) {
                    ioe.printStackTrace(log);
                }
                throw new UnknownHostException(name.name);
            }
        } while (request.isBroadcast);
        throw new UnknownHostException(name.name);
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    NbtAddress getByName(Name name, InetAddress addr) throws UnknownHostException {
        boolean z = false;
        NameQueryRequest request = new NameQueryRequest(name);
        NameQueryResponse response = new NameQueryResponse();
        int n;
        if (addr != null) {
            request.addr = addr;
            if (addr.getAddress()[3] == (byte) -1) {
                z = true;
            }
            request.isBroadcast = z;
            n = RETRY_COUNT;
            do {
                try {
                    send(request, response, RETRY_TIMEOUT);
                    if (!response.received || response.resultCode != 0) {
                        n--;
                        if (n <= 0) {
                            break;
                        }
                    } else {
                        int last = response.addrEntry.length - 1;
                        response.addrEntry[last].hostName.srcHashCode = addr.hashCode();
                        return response.addrEntry[last];
                    }
                } catch (IOException ioe) {
                    LogStream logStream = log;
                    if (LogStream.level > 1) {
                        ioe.printStackTrace(log);
                    }
                    throw new UnknownHostException(name.name);
                }
            } while (request.isBroadcast);
            throw new UnknownHostException(name.name);
        }
        for (int i = 0; i < this.resolveOrder.length; i++) {
            switch (this.resolveOrder[i]) {
                case 1:
                    NbtAddress ans = Lmhosts.getByName(name);
                    if (ans == null) {
                        break;
                    }
                    ans.hostName.srcHashCode = 0;
                    return ans;
                case 2:
                case 3:
                    if (this.resolveOrder[i] != 3 || name.name == NbtAddress.MASTER_BROWSER_NAME || name.hexCode == 29) {
                        request.addr = this.baddr;
                        request.isBroadcast = true;
                    } else {
                        request.addr = NbtAddress.getWINSAddress();
                        request.isBroadcast = false;
                    }
                    int n2 = RETRY_COUNT;
                    while (true) {
                        n = n2 - 1;
                        if (n2 > 0) {
                            try {
                                send(request, response, RETRY_TIMEOUT);
                                if (!response.received || response.resultCode != 0) {
                                    if (this.resolveOrder[i] == 3) {
                                        break;
                                    }
                                    n2 = n;
                                } else {
                                    response.addrEntry[0].hostName.srcHashCode = request.addr.hashCode();
                                    return response.addrEntry[0];
                                }
                            } catch (IOException ioe2) {
                                logStream = log;
                                if (LogStream.level > 1) {
                                    ioe2.printStackTrace(log);
                                }
                                throw new UnknownHostException(name.name);
                            } catch (IOException e) {
                                break;
                            }
                        }
                        continue;
                    }
                    break;
                default:
                    continue;
            }
        }
        throw new UnknownHostException(name.name);
    }

    NbtAddress[] getNodeStatus(NbtAddress addr) throws UnknownHostException {
        NodeStatusResponse response = new NodeStatusResponse(addr);
        NodeStatusRequest request = new NodeStatusRequest(new Name("*\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000", 0, null));
        request.addr = addr.getInetAddress();
        int n = RETRY_COUNT;
        while (true) {
            int n2 = n - 1;
            if (n > 0) {
                try {
                    send(request, response, RETRY_TIMEOUT);
                    if (response.received && response.resultCode == 0) {
                        break;
                    }
                    n = n2;
                } catch (IOException ioe) {
                    LogStream logStream = log;
                    if (LogStream.level > 1) {
                        ioe.printStackTrace(log);
                    }
                    throw new UnknownHostException(addr.toString());
                }
            }
            throw new UnknownHostException(addr.hostName.name);
        }
        int srcHashCode = request.addr.hashCode();
        for (NbtAddress nbtAddress : response.addressArray) {
            nbtAddress.hostName.srcHashCode = srcHashCode;
        }
        return response.addressArray;
    }
}
