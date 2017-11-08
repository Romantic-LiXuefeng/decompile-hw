package java.net;

import android.system.ErrnoException;
import android.system.GaiException;
import android.system.OsConstants;
import android.system.StructAddrinfo;
import dalvik.system.BlockGuard;
import java.io.IOException;
import java.util.Enumeration;
import libcore.io.Libcore;

class Inet6AddressImpl implements InetAddressImpl {
    private static final AddressCache addressCache = new AddressCache();
    private static InetAddress anyLocalAddress;
    private static InetAddress[] loopbackAddresses;

    private native String getHostByAddr0(byte[] bArr) throws UnknownHostException;

    private native boolean isReachable0(byte[] bArr, int i, int i2, byte[] bArr2, int i3, int i4) throws IOException;

    Inet6AddressImpl() {
    }

    public InetAddress[] lookupAllHostAddr(String host, int netId) throws UnknownHostException {
        if (host == null || host.isEmpty()) {
            return loopbackAddresses();
        }
        InetAddress result = InetAddress.parseNumericAddressNoThrow(host);
        if (result == null) {
            return lookupHostByName(host, netId);
        }
        if (InetAddress.disallowDeprecatedFormats(host, result) == null) {
            throw new UnknownHostException("Deprecated IPv4 address format: " + host);
        }
        return new InetAddress[]{InetAddress.disallowDeprecatedFormats(host, result)};
    }

    private static InetAddress[] lookupHostByName(String host, int netId) throws UnknownHostException {
        BlockGuard.getThreadPolicy().onNetwork();
        Object cachedResult = addressCache.get(host, netId);
        if (cachedResult == null) {
            try {
                StructAddrinfo hints = new StructAddrinfo();
                hints.ai_flags = OsConstants.AI_ADDRCONFIG;
                hints.ai_family = OsConstants.AF_UNSPEC;
                hints.ai_socktype = OsConstants.SOCK_STREAM;
                InetAddress[] addresses = Libcore.os.android_getaddrinfo(host, hints, netId);
                for (InetAddress address : addresses) {
                    address.holder().hostName = host;
                }
                addressCache.put(host, netId, addresses);
                return addresses;
            } catch (GaiException gaiException) {
                if ((gaiException.getCause() instanceof ErrnoException) && ((ErrnoException) gaiException.getCause()).errno == OsConstants.EACCES) {
                    throw new SecurityException("Permission denied (missing INTERNET permission?)", gaiException);
                }
                String detailMessage = "Unable to resolve host \"" + host + "\": " + Libcore.os.gai_strerror(gaiException.error);
                addressCache.putUnknownHost(host, netId, detailMessage);
                throw gaiException.rethrowAsUnknownHostException(detailMessage);
            }
        } else if (cachedResult instanceof InetAddress[]) {
            return (InetAddress[]) cachedResult;
        } else {
            throw new UnknownHostException((String) cachedResult);
        }
    }

    public String getHostByAddr(byte[] addr) throws UnknownHostException {
        BlockGuard.getThreadPolicy().onNetwork();
        return getHostByAddr0(addr);
    }

    public void clearAddressCache() {
        addressCache.clear();
    }

    public boolean isReachable(InetAddress addr, int timeout, NetworkInterface netif, int ttl) throws IOException {
        byte[] bArr = null;
        int scope = -1;
        int netif_scope = -1;
        if (netif != null) {
            Enumeration it = netif.getInetAddresses();
            while (it.hasMoreElements()) {
                InetAddress inetaddr = (InetAddress) it.nextElement();
                if (inetaddr.getClass().isInstance(addr)) {
                    bArr = inetaddr.getAddress();
                    if (inetaddr instanceof Inet6Address) {
                        netif_scope = ((Inet6Address) inetaddr).getScopeId();
                    }
                    if (bArr == null) {
                        return false;
                    }
                }
            }
            if (bArr == null) {
                return false;
            }
        }
        if (addr instanceof Inet6Address) {
            scope = ((Inet6Address) addr).getScopeId();
        }
        BlockGuard.getThreadPolicy().onNetwork();
        try {
            return isReachable0(addr.getAddress(), scope, timeout, bArr, ttl, netif_scope);
        } catch (IOException e) {
            return false;
        }
    }

    public InetAddress anyLocalAddress() {
        InetAddress inetAddress;
        synchronized (Inet6AddressImpl.class) {
            if (anyLocalAddress == null) {
                Inet6Address anyAddress = new Inet6Address();
                anyAddress.holder().hostName = "::";
                anyLocalAddress = anyAddress;
            }
            inetAddress = anyLocalAddress;
        }
        return inetAddress;
    }

    public InetAddress[] loopbackAddresses() {
        InetAddress[] inetAddressArr;
        synchronized (Inet6AddressImpl.class) {
            if (loopbackAddresses == null) {
                loopbackAddresses = new InetAddress[]{Inet6Address.LOOPBACK, Inet4Address.LOOPBACK};
            }
            inetAddressArr = loopbackAddresses;
        }
        return inetAddressArr;
    }
}
