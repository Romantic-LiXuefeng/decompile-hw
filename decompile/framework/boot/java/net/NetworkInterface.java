package java.net;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.NoSuchElementException;

public final class NetworkInterface {
    private static final int defaultIndex;
    private static final NetworkInterface defaultInterface = DefaultInterface.getDefault();
    private InetAddress[] addrs;
    private InterfaceAddress[] bindings;
    private NetworkInterface[] childs;
    private String displayName;
    private byte[] hardwareAddr;
    private int index;
    private String name;
    private NetworkInterface parent = null;
    private boolean virtual = false;

    private static native NetworkInterface[] getAll() throws SocketException;

    private static native NetworkInterface getByIndex0(int i) throws SocketException;

    private static native NetworkInterface getByInetAddress0(InetAddress inetAddress) throws SocketException;

    private static native NetworkInterface getByName0(String str) throws SocketException;

    private static native int getMTU0(String str, int i) throws SocketException;

    private static native boolean isLoopback0(String str, int i) throws SocketException;

    private static native boolean isP2P0(String str, int i) throws SocketException;

    private static native boolean isUp0(String str, int i) throws SocketException;

    private static native boolean supportsMulticast0(String str, int i) throws SocketException;

    static {
        if (defaultInterface != null) {
            defaultIndex = defaultInterface.getIndex();
        } else {
            defaultIndex = 0;
        }
    }

    NetworkInterface() {
    }

    NetworkInterface(String name, int index, InetAddress[] addrs) {
        this.name = name;
        this.index = index;
        this.addrs = addrs;
    }

    public String getName() {
        return this.name;
    }

    public Enumeration<InetAddress> getInetAddresses() {
        return 
/*
Method generation error in method: java.net.NetworkInterface.getInetAddresses():java.util.Enumeration<java.net.InetAddress>
jadx.core.utils.exceptions.CodegenException: Error generate insn: 0x0005: RETURN  (wrap: java.util.Enumeration
  0x0002: CONSTRUCTOR  (r0_0 java.util.Enumeration) = (r1_0 'this' java.net.NetworkInterface) java.net.NetworkInterface.1checkedAddresses.<init>(java.net.NetworkInterface):void CONSTRUCTOR) in method: java.net.NetworkInterface.getInetAddresses():java.util.Enumeration<java.net.InetAddress>
	at jadx.core.codegen.InsnGen.makeInsn(InsnGen.java:226)
	at jadx.core.codegen.InsnGen.makeInsn(InsnGen.java:203)
	at jadx.core.codegen.RegionGen.makeSimpleBlock(RegionGen.java:100)
	at jadx.core.codegen.RegionGen.makeRegion(RegionGen.java:50)
	at jadx.core.codegen.RegionGen.makeSimpleRegion(RegionGen.java:87)
	at jadx.core.codegen.RegionGen.makeRegion(RegionGen.java:53)
	at jadx.core.codegen.MethodGen.addInstructions(MethodGen.java:187)
	at jadx.core.codegen.ClassGen.addMethod(ClassGen.java:328)
	at jadx.core.codegen.ClassGen.addMethods(ClassGen.java:265)
	at jadx.core.codegen.ClassGen.addClassBody(ClassGen.java:228)
	at jadx.core.codegen.ClassGen.addClassCode(ClassGen.java:118)
	at jadx.core.codegen.ClassGen.makeClass(ClassGen.java:83)
	at jadx.core.codegen.CodeGen.visit(CodeGen.java:19)
	at jadx.core.ProcessClass.process(ProcessClass.java:43)
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:306)
	at jadx.api.JavaClass.decompile(JavaClass.java:62)
	at jadx.api.JadxDecompiler$1.run(JadxDecompiler.java:199)
Caused by: jadx.core.utils.exceptions.CodegenException: Error generate insn: 0x0002: CONSTRUCTOR  (r0_0 java.util.Enumeration) = (r1_0 'this' java.net.NetworkInterface) java.net.NetworkInterface.1checkedAddresses.<init>(java.net.NetworkInterface):void CONSTRUCTOR in method: java.net.NetworkInterface.getInetAddresses():java.util.Enumeration<java.net.InetAddress>
	at jadx.core.codegen.InsnGen.makeInsn(InsnGen.java:226)
	at jadx.core.codegen.InsnGen.addArg(InsnGen.java:101)
	at jadx.core.codegen.InsnGen.makeInsnBody(InsnGen.java:289)
	at jadx.core.codegen.InsnGen.makeInsn(InsnGen.java:220)
	... 16 more
Caused by: jadx.core.utils.exceptions.JadxRuntimeException: Null container variable
	at jadx.core.utils.RegionUtils.notEmpty(RegionUtils.java:151)
	at jadx.core.codegen.InsnGen.inlineAnonymousConstr(InsnGen.java:586)
	at jadx.core.codegen.InsnGen.makeConstructor(InsnGen.java:552)
	at jadx.core.codegen.InsnGen.makeInsnBody(InsnGen.java:339)
	at jadx.core.codegen.InsnGen.makeInsn(InsnGen.java:211)
	... 19 more

*/

        public List<InterfaceAddress> getInterfaceAddresses() {
            List<InterfaceAddress> lst = new ArrayList(1);
            SecurityManager sec = System.getSecurityManager();
            for (int j = 0; j < this.bindings.length; j++) {
                if (sec != null) {
                    try {
                        sec.checkConnect(this.bindings[j].getAddress().getHostAddress(), -1);
                    } catch (SecurityException e) {
                    }
                }
                lst.add(this.bindings[j]);
            }
            return lst;
        }

        public Enumeration<NetworkInterface> getSubInterfaces() {
            return new Enumeration<NetworkInterface>() {
                private int i = 0;

                public NetworkInterface nextElement() {
                    if (this.i < NetworkInterface.this.childs.length) {
                        NetworkInterface[] -get1 = NetworkInterface.this.childs;
                        int i = this.i;
                        this.i = i + 1;
                        return -get1[i];
                    }
                    throw new NoSuchElementException();
                }

                public boolean hasMoreElements() {
                    return this.i < NetworkInterface.this.childs.length;
                }
            };
        }

        public NetworkInterface getParent() {
            return this.parent;
        }

        public int getIndex() {
            return this.index;
        }

        public String getDisplayName() {
            return "".equals(this.displayName) ? null : this.displayName;
        }

        public static NetworkInterface getByName(String name) throws SocketException {
            if (name != null) {
                return getByName0(name);
            }
            throw new NullPointerException();
        }

        public static NetworkInterface getByIndex(int index) throws SocketException {
            if (index >= 0) {
                return getByIndex0(index);
            }
            throw new IllegalArgumentException("Interface index can't be negative");
        }

        public static NetworkInterface getByInetAddress(InetAddress addr) throws SocketException {
            if (addr == null) {
                throw new NullPointerException();
            }
            if (!(addr instanceof Inet4Address) ? addr instanceof Inet6Address : true) {
                return getByInetAddress0(addr);
            }
            throw new IllegalArgumentException("invalid address type");
        }

        public static Enumeration<NetworkInterface> getNetworkInterfaces() throws SocketException {
            final NetworkInterface[] netifs = getAll();
            if (netifs == null) {
                return null;
            }
            return new Enumeration<NetworkInterface>() {
                private int i = 0;

                public NetworkInterface nextElement() {
                    if (netifs == null || this.i >= netifs.length) {
                        throw new NoSuchElementException();
                    }
                    NetworkInterface[] networkInterfaceArr = netifs;
                    int i = this.i;
                    this.i = i + 1;
                    return networkInterfaceArr[i];
                }

                public boolean hasMoreElements() {
                    return netifs != null && this.i < netifs.length;
                }
            };
        }

        public boolean isUp() throws SocketException {
            return isUp0(this.name, this.index);
        }

        public boolean isLoopback() throws SocketException {
            return isLoopback0(this.name, this.index);
        }

        public boolean isPointToPoint() throws SocketException {
            return isP2P0(this.name, this.index);
        }

        public boolean supportsMulticast() throws SocketException {
            return supportsMulticast0(this.name, this.index);
        }

        public byte[] getHardwareAddress() throws SocketException {
            NetworkInterface ni = getByName0(this.name);
            if (ni != null) {
                return ni.hardwareAddr;
            }
            throw new SocketException("NetworkInterface doesn't exist anymore");
        }

        public int getMTU() throws SocketException {
            return getMTU0(this.name, this.index);
        }

        public boolean isVirtual() {
            return this.virtual;
        }

        public boolean equals(Object obj) {
            boolean z = true;
            if (!(obj instanceof NetworkInterface)) {
                return false;
            }
            NetworkInterface that = (NetworkInterface) obj;
            if (this.name != null) {
                if (!this.name.equals(that.name)) {
                    return false;
                }
            } else if (that.name != null) {
                return false;
            }
            if (this.addrs == null) {
                if (that.addrs != null) {
                    z = false;
                }
                return z;
            } else if (that.addrs == null || this.addrs.length != that.addrs.length) {
                return false;
            } else {
                for (int i = 0; i < count; i++) {
                    boolean found = false;
                    for (Object equals : that.addrs) {
                        if (this.addrs[i].equals(equals)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        return false;
                    }
                }
                return true;
            }
        }

        public int hashCode() {
            return this.name == null ? 0 : this.name.hashCode();
        }

        public String toString() {
            String result = "name:" + (this.name == null ? "null" : this.name);
            if (this.displayName != null) {
                return result + " (" + this.displayName + ")";
            }
            return result;
        }

        static NetworkInterface getDefault() {
            return defaultInterface;
        }
    }
