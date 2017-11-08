package java.util.jar;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.security.AccessController;
import java.security.CodeSigner;
import java.security.cert.Certificate;
import java.util.Enumeration;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import sun.misc.IOUtils;
import sun.net.www.protocol.http.AuthenticationInfo;
import sun.security.action.GetPropertyAction;
import sun.security.util.ManifestEntryVerifier;

public class JarFile extends ZipFile {
    public static final String MANIFEST_NAME = "META-INF/MANIFEST.MF";
    static final String META_DIR = "META-INF/";
    private static String[] jarNames;
    private static String javaHome;
    private static int[] lastOcc = new int[128];
    private static int[] optoSft = new int[10];
    private static char[] src = new char[]{'c', 'l', 'a', AuthenticationInfo.SERVER_AUTHENTICATION, AuthenticationInfo.SERVER_AUTHENTICATION, '-', AuthenticationInfo.PROXY_AUTHENTICATION, 'a', 't', 'h'};
    private boolean computedHasClassPathAttribute;
    private boolean hasClassPathAttribute;
    private JarVerifier jv;
    private boolean jvInitialized;
    private JarEntry manEntry;
    private SoftReference<Manifest> manRef;
    private boolean verify;

    private class JarFileEntry extends JarEntry {
        JarFileEntry(ZipEntry ze) {
            super(ze);
        }

        public Attributes getAttributes() throws IOException {
            Manifest man = JarFile.this.getManifest();
            if (man != null) {
                return man.getAttributes(getName());
            }
            return null;
        }

        public Certificate[] getCertificates() {
            try {
                JarFile.this.maybeInstantiateVerifier();
                if (this.certs == null && JarFile.this.jv != null) {
                    this.certs = JarFile.this.jv.getCerts(JarFile.this, this);
                }
                if (this.certs == null) {
                    return null;
                }
                return (Certificate[]) this.certs.clone();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        public CodeSigner[] getCodeSigners() {
            try {
                JarFile.this.maybeInstantiateVerifier();
                if (this.signers == null && JarFile.this.jv != null) {
                    this.signers = JarFile.this.jv.getCodeSigners(JarFile.this, this);
                }
                if (this.signers == null) {
                    return null;
                }
                return (CodeSigner[]) this.signers.clone();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    private native String[] getMetaInfEntryNames();

    public JarFile(String name) throws IOException {
        this(new File(name), true, 1);
    }

    public JarFile(String name, boolean verify) throws IOException {
        this(new File(name), verify, 1);
    }

    public JarFile(File file) throws IOException {
        this(file, true, 1);
    }

    public JarFile(File file, boolean verify) throws IOException {
        this(file, verify, 1);
    }

    public JarFile(File file, boolean verify, int mode) throws IOException {
        super(file, mode);
        this.verify = verify;
    }

    public Manifest getManifest() throws IOException {
        return getManifestFromReference();
    }

    private synchronized Manifest getManifestFromReference() throws IOException {
        Manifest manifest;
        manifest = this.manRef != null ? (Manifest) this.manRef.get() : null;
        if (manifest == null) {
            JarEntry manEntry = getManEntry();
            if (manEntry != null) {
                if (this.verify) {
                    byte[] b = getBytes(manEntry);
                    manifest = new Manifest(new ByteArrayInputStream(b));
                    if (!this.jvInitialized) {
                        this.jv = new JarVerifier(b);
                    }
                } else {
                    manifest = new Manifest(super.getInputStream(manEntry));
                }
                this.manRef = new SoftReference(manifest);
            }
        }
        return manifest;
    }

    public JarEntry getJarEntry(String name) {
        return (JarEntry) getEntry(name);
    }

    public ZipEntry getEntry(String name) {
        ZipEntry ze = super.getEntry(name);
        if (ze != null) {
            return new JarFileEntry(ze);
        }
        return null;
    }

    public Enumeration<JarEntry> entries() {
        final Enumeration enum_ = super.entries();
        return new Enumeration<JarEntry>() {
            public boolean hasMoreElements() {
                return enum_.hasMoreElements();
            }

            public JarFileEntry nextElement() {
                return new JarFileEntry((ZipEntry) enum_.nextElement());
            }
        };
    }

    private void maybeInstantiateVerifier() throws IOException {
        if (this.jv == null && this.verify) {
            String[] names = getMetaInfEntryNames();
            if (names != null) {
                for (String toUpperCase : names) {
                    String name = toUpperCase.toUpperCase(Locale.ENGLISH);
                    if (name.endsWith(".DSA") || name.endsWith(".RSA") || name.endsWith(".EC") || name.endsWith(".SF")) {
                        getManifest();
                        return;
                    }
                }
            }
            this.verify = false;
        }
    }

    private void initializeVerifier() {
        IOException ex;
        try {
            String[] names = getMetaInfEntryNames();
            if (names != null) {
                int i = 0;
                ManifestEntryVerifier mev = null;
                while (i < names.length) {
                    JarEntry e = getJarEntry(names[i]);
                    if (e == null) {
                        throw new JarException("corrupted jar file");
                    }
                    ManifestEntryVerifier mev2;
                    try {
                        if (e.isDirectory()) {
                            mev2 = mev;
                        } else {
                            if (mev == null) {
                                mev2 = new ManifestEntryVerifier(getManifestFromReference());
                            } else {
                                mev2 = mev;
                            }
                            byte[] b = getBytes(e);
                            if (b != null && b.length > 0) {
                                this.jv.beginEntry(e, mev2);
                                this.jv.update(b.length, b, 0, b.length, mev2);
                                this.jv.update(-1, null, 0, 0, mev2);
                            }
                        }
                        i++;
                        mev = mev2;
                    } catch (IOException e2) {
                        ex = e2;
                        mev2 = mev;
                    }
                }
            }
        } catch (IOException e3) {
            ex = e3;
        }
        if (this.jv != null) {
            this.jv.doneWithMeta();
            if (JarVerifier.debug != null) {
                JarVerifier.debug.println("done with meta!");
            }
            if (this.jv.nothingToVerify()) {
                if (JarVerifier.debug != null) {
                    JarVerifier.debug.println("nothing to verify!");
                }
                this.jv = null;
                this.verify = false;
            }
        }
        this.jv = null;
        this.verify = false;
        if (JarVerifier.debug != null) {
            JarVerifier.debug.println("jarfile parsing error!");
            ex.printStackTrace();
        }
        if (this.jv != null) {
            this.jv.doneWithMeta();
            if (JarVerifier.debug != null) {
                JarVerifier.debug.println("done with meta!");
            }
            if (this.jv.nothingToVerify()) {
                if (JarVerifier.debug != null) {
                    JarVerifier.debug.println("nothing to verify!");
                }
                this.jv = null;
                this.verify = false;
            }
        }
    }

    private byte[] getBytes(ZipEntry ze) throws IOException {
        Throwable th;
        Throwable th2 = null;
        InputStream inputStream = null;
        try {
            inputStream = super.getInputStream(ze);
            byte[] readFully = IOUtils.readFully(inputStream, (int) ze.getSize(), true);
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Throwable th3) {
                    th2 = th3;
                }
            }
            if (th2 == null) {
                return readFully;
            }
            throw th2;
        } catch (Throwable th22) {
            Throwable th4 = th22;
            th22 = th;
            th = th4;
        }
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (Throwable th5) {
                if (th22 == null) {
                    th22 = th5;
                } else if (th22 != th5) {
                    th22.addSuppressed(th5);
                }
            }
        }
        if (th22 != null) {
            throw th22;
        }
        throw th;
    }

    public synchronized InputStream getInputStream(ZipEntry ze) throws IOException {
        maybeInstantiateVerifier();
        if (this.jv == null) {
            return super.getInputStream(ze);
        }
        if (!this.jvInitialized) {
            initializeVerifier();
            this.jvInitialized = true;
            if (this.jv == null) {
                return super.getInputStream(ze);
            }
        }
        return new VerifierStream(getManifestFromReference(), ze instanceof JarFileEntry ? (JarEntry) ze : getJarEntry(ze.getName()), super.getInputStream(ze), this.jv);
    }

    static {
        lastOcc[99] = 1;
        lastOcc[108] = 2;
        lastOcc[115] = 5;
        lastOcc[45] = 6;
        lastOcc[112] = 7;
        lastOcc[97] = 8;
        lastOcc[116] = 9;
        lastOcc[104] = 10;
        for (int i = 0; i < 9; i++) {
            optoSft[i] = 10;
        }
        optoSft[9] = 1;
    }

    private synchronized JarEntry getManEntry() {
        if (this.manEntry == null) {
            this.manEntry = getJarEntry(MANIFEST_NAME);
            if (this.manEntry == null) {
                String[] names = getMetaInfEntryNames();
                if (names != null) {
                    for (int i = 0; i < names.length; i++) {
                        if (MANIFEST_NAME.equals(names[i].toUpperCase(Locale.ENGLISH))) {
                            this.manEntry = getJarEntry(names[i]);
                            break;
                        }
                    }
                }
            }
        }
        return this.manEntry;
    }

    public boolean hasClassPathAttribute() throws IOException {
        if (this.computedHasClassPathAttribute) {
            return this.hasClassPathAttribute;
        }
        this.hasClassPathAttribute = false;
        if (!isKnownToNotHaveClassPathAttribute()) {
            JarEntry manEntry = getManEntry();
            if (manEntry != null) {
                byte[] b = getBytes(manEntry);
                int last = b.length - src.length;
                int i = 0;
                loop0:
                while (i <= last) {
                    int j = 9;
                    while (j >= 0) {
                        char c = (char) b[i + j];
                        if (((c - 65) | (90 - c)) >= 0) {
                            c = (char) (c + 32);
                        }
                        if (c != src[j]) {
                            i += Math.max((j + 1) - lastOcc[c & 127], optoSft[j]);
                        } else {
                            j--;
                        }
                    }
                    this.hasClassPathAttribute = true;
                    break loop0;
                }
            }
        }
        this.computedHasClassPathAttribute = true;
        return this.hasClassPathAttribute;
    }

    private boolean isKnownToNotHaveClassPathAttribute() {
        if (javaHome == null) {
            javaHome = (String) AccessController.doPrivileged(new GetPropertyAction("java.home"));
        }
        if (jarNames == null) {
            names = new String[10];
            String fileSep = File.separator;
            names[0] = fileSep + "rt.jar";
            int i = 1 + 1;
            names[1] = fileSep + "sunrsasign.jar";
            int i2 = i + 1;
            names[i] = fileSep + "jsse.jar";
            i = i2 + 1;
            names[i2] = fileSep + "jce.jar";
            i2 = i + 1;
            names[i] = fileSep + "charsets.jar";
            i = i2 + 1;
            names[i2] = fileSep + "dnsns.jar";
            i2 = i + 1;
            names[i] = fileSep + "ldapsec.jar";
            i = i2 + 1;
            names[i2] = fileSep + "localedata.jar";
            i2 = i + 1;
            names[i] = fileSep + "sunjce_provider.jar";
            i = i2 + 1;
            names[i2] = fileSep + "sunpkcs11.jar";
            jarNames = names;
        }
        String name = getName();
        if (name.startsWith(javaHome)) {
            names = jarNames;
            for (String endsWith : names) {
                if (name.endsWith(endsWith)) {
                    return true;
                }
            }
        }
        return false;
    }

    private synchronized void ensureInitialization() {
        try {
            maybeInstantiateVerifier();
            if (!(this.jv == null || this.jvInitialized)) {
                initializeVerifier();
                this.jvInitialized = true;
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    JarEntry newEntry(ZipEntry ze) {
        return new JarFileEntry(ze);
    }

    private Enumeration<String> unsignedEntryNames() {
        final Enumeration entries = entries();
        return new Enumeration<String>() {
            String name;

            public boolean hasMoreElements() {
                if (this.name != null) {
                    return true;
                }
                while (entries.hasMoreElements()) {
                    ZipEntry e = (ZipEntry) entries.nextElement();
                    String value = e.getName();
                    if (!e.isDirectory() && !JarVerifier.isSigningRelated(value)) {
                        this.name = value;
                        return true;
                    }
                }
                return false;
            }

            public String nextElement() {
                if (hasMoreElements()) {
                    String value = this.name;
                    this.name = null;
                    return value;
                }
                throw new NoSuchElementException();
            }
        };
    }
}
