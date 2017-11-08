package sun.security.ssl;

import java.security.AlgorithmConstraints;
import java.security.AlgorithmParameters;
import java.security.CryptoPrimitive;
import java.security.Key;
import java.util.HashSet;
import java.util.Set;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocket;
import sun.security.util.DisabledAlgorithmConstraints;

final class SSLAlgorithmConstraints implements AlgorithmConstraints {
    private static final AlgorithmConstraints tlsDisabledAlgConstraints = new TLSDisabledAlgConstraints();
    private static final AlgorithmConstraints x509DisabledAlgConstraints = new X509DisabledAlgConstraints();
    private boolean enabledX509DisabledAlgConstraints = true;
    private AlgorithmConstraints peerAlgConstraints = null;
    private AlgorithmConstraints userAlgConstraints = null;

    private static class BasicDisabledAlgConstraints extends DisabledAlgorithmConstraints {
        private static final /* synthetic */ int[] -sun-security-ssl-CipherSuite$KeyExchangeSwitchesValues = null;

        private static /* synthetic */ int[] -getsun-security-ssl-CipherSuite$KeyExchangeSwitchesValues() {
            if (-sun-security-ssl-CipherSuite$KeyExchangeSwitchesValues != null) {
                return -sun-security-ssl-CipherSuite$KeyExchangeSwitchesValues;
            }
            int[] iArr = new int[KeyExchange.values().length];
            try {
                iArr[KeyExchange.K_DHE_DSS.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                iArr[KeyExchange.K_DHE_RSA.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                iArr[KeyExchange.K_DH_ANON.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                iArr[KeyExchange.K_DH_DSS.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                iArr[KeyExchange.K_DH_RSA.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                iArr[KeyExchange.K_ECDHE_ECDSA.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
            try {
                iArr[KeyExchange.K_ECDHE_RSA.ordinal()] = 7;
            } catch (NoSuchFieldError e7) {
            }
            try {
                iArr[KeyExchange.K_ECDH_ANON.ordinal()] = 8;
            } catch (NoSuchFieldError e8) {
            }
            try {
                iArr[KeyExchange.K_ECDH_ECDSA.ordinal()] = 9;
            } catch (NoSuchFieldError e9) {
            }
            try {
                iArr[KeyExchange.K_ECDH_RSA.ordinal()] = 10;
            } catch (NoSuchFieldError e10) {
            }
            try {
                iArr[KeyExchange.K_KRB5.ordinal()] = 11;
            } catch (NoSuchFieldError e11) {
            }
            try {
                iArr[KeyExchange.K_KRB5_EXPORT.ordinal()] = 12;
            } catch (NoSuchFieldError e12) {
            }
            try {
                iArr[KeyExchange.K_NULL.ordinal()] = 13;
            } catch (NoSuchFieldError e13) {
            }
            try {
                iArr[KeyExchange.K_RSA.ordinal()] = 14;
            } catch (NoSuchFieldError e14) {
            }
            try {
                iArr[KeyExchange.K_RSA_EXPORT.ordinal()] = 15;
            } catch (NoSuchFieldError e15) {
            }
            try {
                iArr[KeyExchange.K_SCSV.ordinal()] = 16;
            } catch (NoSuchFieldError e16) {
            }
            -sun-security-ssl-CipherSuite$KeyExchangeSwitchesValues = iArr;
            return iArr;
        }

        BasicDisabledAlgConstraints(String propertyName) {
            super(propertyName);
        }

        protected Set<String> decomposes(KeyExchange keyExchange, boolean forCertPathOnly) {
            Set<String> components = new HashSet();
            switch (-getsun-security-ssl-CipherSuite$KeyExchangeSwitchesValues()[keyExchange.ordinal()]) {
                case 1:
                    components.add("DSA");
                    components.add("DSS");
                    components.add("DH");
                    components.add("DHE");
                    components.add("DiffieHellman");
                    components.add("DHE_DSS");
                    break;
                case 2:
                    components.add("RSA");
                    components.add("DH");
                    components.add("DHE");
                    components.add("DiffieHellman");
                    components.add("DHE_RSA");
                    break;
                case 3:
                    if (!forCertPathOnly) {
                        components.add("ANON");
                        components.add("DH");
                        components.add("DiffieHellman");
                        components.add("DH_ANON");
                        break;
                    }
                    break;
                case 4:
                    components.add("DSA");
                    components.add("DSS");
                    components.add("DH");
                    components.add("DiffieHellman");
                    components.add("DH_DSS");
                    break;
                case 5:
                    components.add("RSA");
                    components.add("DH");
                    components.add("DiffieHellman");
                    components.add("DH_RSA");
                    break;
                case 6:
                    components.add("ECDHE");
                    components.add("ECDSA");
                    components.add("ECDHE_ECDSA");
                    break;
                case 7:
                    components.add("ECDHE");
                    components.add("RSA");
                    components.add("ECDHE_RSA");
                    break;
                case 8:
                    if (!forCertPathOnly) {
                        components.add("ECDH");
                        components.add("ANON");
                        components.add("ECDH_ANON");
                        break;
                    }
                    break;
                case 9:
                    components.add("ECDH");
                    components.add("ECDSA");
                    components.add("ECDH_ECDSA");
                    break;
                case 10:
                    components.add("ECDH");
                    components.add("RSA");
                    components.add("ECDH_RSA");
                    break;
                case 11:
                    if (!forCertPathOnly) {
                        components.add("KRB5");
                        break;
                    }
                    break;
                case 12:
                    if (!forCertPathOnly) {
                        components.add("KRB5_EXPORT");
                        break;
                    }
                    break;
                case 13:
                    if (!forCertPathOnly) {
                        components.add("NULL");
                        break;
                    }
                    break;
                case 14:
                    components.add("RSA");
                    break;
                case 15:
                    components.add("RSA");
                    components.add("RSA_EXPORT");
                    break;
            }
            return components;
        }

        protected Set<String> decomposes(BulkCipher bulkCipher) {
            Set<String> components = new HashSet();
            if (bulkCipher.transformation != null) {
                components.addAll(super.decomposes(bulkCipher.transformation));
            }
            return components;
        }

        protected Set<String> decomposes(MacAlg macAlg) {
            Set<String> components = new HashSet();
            if (macAlg == CipherSuite.M_MD5) {
                components.add("MD5");
                components.add("HmacMD5");
            } else if (macAlg == CipherSuite.M_SHA) {
                components.add("SHA1");
                components.add("SHA-1");
                components.add("HmacSHA1");
            } else if (macAlg == CipherSuite.M_SHA256) {
                components.add("SHA256");
                components.add("SHA-256");
                components.add("HmacSHA256");
            } else if (macAlg == CipherSuite.M_SHA384) {
                components.add("SHA384");
                components.add("SHA-384");
                components.add("HmacSHA384");
            }
            return components;
        }
    }

    private static class SupportedSignatureAlgorithmConstraints implements AlgorithmConstraints {
        private String[] supportedAlgorithms;

        SupportedSignatureAlgorithmConstraints(String[] supportedAlgorithms) {
            if (supportedAlgorithms != null) {
                this.supportedAlgorithms = (String[]) supportedAlgorithms.clone();
            } else {
                this.supportedAlgorithms = null;
            }
        }

        public boolean permits(Set<CryptoPrimitive> primitives, String algorithm, AlgorithmParameters parameters) {
            if (algorithm == null || algorithm.length() == 0) {
                throw new IllegalArgumentException("No algorithm name specified");
            } else if (primitives == null || primitives.isEmpty()) {
                throw new IllegalArgumentException("No cryptographic primitive specified");
            } else if (this.supportedAlgorithms == null || this.supportedAlgorithms.length == 0) {
                return false;
            } else {
                int position = algorithm.indexOf("and");
                if (position > 0) {
                    algorithm = algorithm.substring(0, position);
                }
                for (String supportedAlgorithm : this.supportedAlgorithms) {
                    if (algorithm.equalsIgnoreCase(supportedAlgorithm)) {
                        return true;
                    }
                }
                return false;
            }
        }

        public final boolean permits(Set<CryptoPrimitive> set, Key key) {
            return true;
        }

        public final boolean permits(Set<CryptoPrimitive> primitives, String algorithm, Key key, AlgorithmParameters parameters) {
            if (algorithm != null && algorithm.length() != 0) {
                return permits(primitives, algorithm, parameters);
            }
            throw new IllegalArgumentException("No algorithm name specified");
        }
    }

    private static class TLSDisabledAlgConstraints extends BasicDisabledAlgConstraints {
        TLSDisabledAlgConstraints() {
            super(DisabledAlgorithmConstraints.PROPERTY_TLS_DISABLED_ALGS);
        }

        protected Set<String> decomposes(String algorithm) {
            if (algorithm.startsWith("SSL_") || algorithm.startsWith("TLS_")) {
                CipherSuite cipherSuite = null;
                try {
                    cipherSuite = CipherSuite.valueOf(algorithm);
                } catch (IllegalArgumentException e) {
                }
                if (cipherSuite != null) {
                    Set<String> components = new HashSet();
                    if (cipherSuite.keyExchange != null) {
                        components.addAll(decomposes(cipherSuite.keyExchange, false));
                    }
                    if (cipherSuite.cipher != null) {
                        components.addAll(decomposes(cipherSuite.cipher));
                    }
                    if (cipherSuite.macAlg != null) {
                        components.addAll(decomposes(cipherSuite.macAlg));
                    }
                    return components;
                }
            }
            return super.decomposes(algorithm);
        }
    }

    private static class X509DisabledAlgConstraints extends BasicDisabledAlgConstraints {
        X509DisabledAlgConstraints() {
            super(DisabledAlgorithmConstraints.PROPERTY_CERTPATH_DISABLED_ALGS);
        }

        protected Set<String> decomposes(String algorithm) {
            if (algorithm.startsWith("SSL_") || algorithm.startsWith("TLS_")) {
                CipherSuite cipherSuite = null;
                try {
                    cipherSuite = CipherSuite.valueOf(algorithm);
                } catch (IllegalArgumentException e) {
                }
                if (cipherSuite != null) {
                    Set<String> components = new HashSet();
                    if (cipherSuite.keyExchange != null) {
                        components.addAll(decomposes(cipherSuite.keyExchange, true));
                    }
                    return components;
                }
            }
            return super.decomposes(algorithm);
        }
    }

    SSLAlgorithmConstraints(AlgorithmConstraints algorithmConstraints) {
        this.userAlgConstraints = algorithmConstraints;
    }

    SSLAlgorithmConstraints(SSLSocket socket, boolean withDefaultCertPathConstraints) {
        if (socket != null) {
            this.userAlgConstraints = socket.getSSLParameters().getAlgorithmConstraints();
        }
        if (!withDefaultCertPathConstraints) {
            this.enabledX509DisabledAlgConstraints = false;
        }
    }

    SSLAlgorithmConstraints(SSLEngine engine, boolean withDefaultCertPathConstraints) {
        if (engine != null) {
            this.userAlgConstraints = engine.getSSLParameters().getAlgorithmConstraints();
        }
        if (!withDefaultCertPathConstraints) {
            this.enabledX509DisabledAlgConstraints = false;
        }
    }

    SSLAlgorithmConstraints(SSLSocket socket, String[] supportedAlgorithms, boolean withDefaultCertPathConstraints) {
        if (socket != null) {
            this.userAlgConstraints = socket.getSSLParameters().getAlgorithmConstraints();
            this.peerAlgConstraints = new SupportedSignatureAlgorithmConstraints(supportedAlgorithms);
        }
        if (!withDefaultCertPathConstraints) {
            this.enabledX509DisabledAlgConstraints = false;
        }
    }

    SSLAlgorithmConstraints(SSLEngine engine, String[] supportedAlgorithms, boolean withDefaultCertPathConstraints) {
        if (engine != null) {
            this.userAlgConstraints = engine.getSSLParameters().getAlgorithmConstraints();
            this.peerAlgConstraints = new SupportedSignatureAlgorithmConstraints(supportedAlgorithms);
        }
        if (!withDefaultCertPathConstraints) {
            this.enabledX509DisabledAlgConstraints = false;
        }
    }

    public boolean permits(Set<CryptoPrimitive> primitives, String algorithm, AlgorithmParameters parameters) {
        boolean permitted = true;
        if (this.peerAlgConstraints != null) {
            permitted = this.peerAlgConstraints.permits(primitives, algorithm, parameters);
        }
        if (permitted && this.userAlgConstraints != null) {
            permitted = this.userAlgConstraints.permits(primitives, algorithm, parameters);
        }
        if (permitted) {
            permitted = tlsDisabledAlgConstraints.permits(primitives, algorithm, parameters);
        }
        if (permitted && this.enabledX509DisabledAlgConstraints) {
            return x509DisabledAlgConstraints.permits(primitives, algorithm, parameters);
        }
        return permitted;
    }

    public boolean permits(Set<CryptoPrimitive> primitives, Key key) {
        boolean permitted = true;
        if (this.peerAlgConstraints != null) {
            permitted = this.peerAlgConstraints.permits(primitives, key);
        }
        if (permitted && this.userAlgConstraints != null) {
            permitted = this.userAlgConstraints.permits(primitives, key);
        }
        if (permitted) {
            permitted = tlsDisabledAlgConstraints.permits(primitives, key);
        }
        if (permitted && this.enabledX509DisabledAlgConstraints) {
            return x509DisabledAlgConstraints.permits(primitives, key);
        }
        return permitted;
    }

    public boolean permits(Set<CryptoPrimitive> primitives, String algorithm, Key key, AlgorithmParameters parameters) {
        boolean permitted = true;
        if (this.peerAlgConstraints != null) {
            permitted = this.peerAlgConstraints.permits(primitives, algorithm, key, parameters);
        }
        if (permitted && this.userAlgConstraints != null) {
            permitted = this.userAlgConstraints.permits(primitives, algorithm, key, parameters);
        }
        if (permitted) {
            permitted = tlsDisabledAlgConstraints.permits(primitives, algorithm, key, parameters);
        }
        if (permitted && this.enabledX509DisabledAlgConstraints) {
            return x509DisabledAlgConstraints.permits(primitives, algorithm, key, parameters);
        }
        return permitted;
    }
}
