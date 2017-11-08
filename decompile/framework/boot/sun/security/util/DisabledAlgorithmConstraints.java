package sun.security.util;

import java.security.AccessController;
import java.security.AlgorithmConstraints;
import java.security.AlgorithmParameters;
import java.security.CryptoPrimitive;
import java.security.Key;
import java.security.PrivilegedAction;
import java.security.Security;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DisabledAlgorithmConstraints implements AlgorithmConstraints {
    public static final String PROPERTY_CERTPATH_DISABLED_ALGS = "jdk.certpath.disabledAlgorithms";
    public static final String PROPERTY_TLS_DISABLED_ALGS = "jdk.tls.disabledAlgorithms";
    private static Map<String, String[]> disabledAlgorithmsMap = Collections.synchronizedMap(new HashMap());
    private static Map<String, KeySizeConstraints> keySizeConstraintsMap = Collections.synchronizedMap(new HashMap());
    private String[] disabledAlgorithms;
    private KeySizeConstraints keySizeConstraints;

    private static class KeySizeConstraint {
        private static final /* synthetic */ int[] -sun-security-util-DisabledAlgorithmConstraints$KeySizeConstraint$OperatorSwitchesValues = null;
        private int maxSize;
        private int minSize;
        private int prohibitedSize = -1;

        enum Operator {
            EQ,
            NE,
            LT,
            LE,
            GT,
            GE;

            static Operator of(String s) {
                if (s.equals("==")) {
                    return EQ;
                }
                if (s.equals("!=")) {
                    return NE;
                }
                if (s.equals("<")) {
                    return LT;
                }
                if (s.equals("<=")) {
                    return LE;
                }
                if (s.equals(">")) {
                    return GT;
                }
                if (s.equals(">=")) {
                    return GE;
                }
                throw new IllegalArgumentException(s + " is not a legal Operator");
            }
        }

        private static /* synthetic */ int[] -getsun-security-util-DisabledAlgorithmConstraints$KeySizeConstraint$OperatorSwitchesValues() {
            if (-sun-security-util-DisabledAlgorithmConstraints$KeySizeConstraint$OperatorSwitchesValues != null) {
                return -sun-security-util-DisabledAlgorithmConstraints$KeySizeConstraint$OperatorSwitchesValues;
            }
            int[] iArr = new int[Operator.values().length];
            try {
                iArr[Operator.EQ.ordinal()] = 1;
            } catch (NoSuchFieldError e) {
            }
            try {
                iArr[Operator.GE.ordinal()] = 2;
            } catch (NoSuchFieldError e2) {
            }
            try {
                iArr[Operator.GT.ordinal()] = 3;
            } catch (NoSuchFieldError e3) {
            }
            try {
                iArr[Operator.LE.ordinal()] = 4;
            } catch (NoSuchFieldError e4) {
            }
            try {
                iArr[Operator.LT.ordinal()] = 5;
            } catch (NoSuchFieldError e5) {
            }
            try {
                iArr[Operator.NE.ordinal()] = 6;
            } catch (NoSuchFieldError e6) {
            }
            -sun-security-util-DisabledAlgorithmConstraints$KeySizeConstraint$OperatorSwitchesValues = iArr;
            return iArr;
        }

        public KeySizeConstraint(Operator operator, int length) {
            int i = 0;
            switch (-getsun-security-util-DisabledAlgorithmConstraints$KeySizeConstraint$OperatorSwitchesValues()[operator.ordinal()]) {
                case 1:
                    this.minSize = 0;
                    this.maxSize = Integer.MAX_VALUE;
                    this.prohibitedSize = length;
                    return;
                case 2:
                    this.minSize = 0;
                    if (length > 1) {
                        i = length - 1;
                    }
                    this.maxSize = i;
                    return;
                case 3:
                    this.minSize = 0;
                    this.maxSize = length;
                    return;
                case 4:
                    this.minSize = length + 1;
                    this.maxSize = Integer.MAX_VALUE;
                    return;
                case 5:
                    this.minSize = length;
                    this.maxSize = Integer.MAX_VALUE;
                    return;
                case 6:
                    this.minSize = length;
                    this.maxSize = length;
                    return;
                default:
                    this.minSize = Integer.MAX_VALUE;
                    this.maxSize = -1;
                    return;
            }
        }

        public boolean disables(Key key) {
            boolean z = true;
            int size = KeyUtil.getKeySize(key);
            if (size == 0) {
                return true;
            }
            if (size <= 0) {
                return false;
            }
            if (size >= this.minSize && size <= this.maxSize && this.prohibitedSize != size) {
                z = false;
            }
            return z;
        }
    }

    private static class KeySizeConstraints {
        private static final Pattern pattern = Pattern.compile("(\\S+)\\s+keySize\\s*(<=|<|==|!=|>|>=)\\s*(\\d+)");
        private Map<String, Set<KeySizeConstraint>> constraintsMap = Collections.synchronizedMap(new HashMap());

        public KeySizeConstraints(String[] restrictions) {
            for (String restriction : restrictions) {
                if (!(restriction == null || restriction.isEmpty())) {
                    Matcher matcher = pattern.matcher(restriction);
                    if (matcher.matches()) {
                        String algorithm = matcher.group(1);
                        Operator operator = Operator.of(matcher.group(2));
                        int length = Integer.parseInt(matcher.group(3));
                        algorithm = algorithm.toLowerCase(Locale.ENGLISH);
                        synchronized (this.constraintsMap) {
                            if (!this.constraintsMap.containsKey(algorithm)) {
                                this.constraintsMap.put(algorithm, new HashSet());
                            }
                            ((Set) this.constraintsMap.get(algorithm)).add(new KeySizeConstraint(operator, length));
                        }
                    } else {
                        continue;
                    }
                }
            }
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public boolean disables(Key key) {
            String algorithm = key.getAlgorithm().toLowerCase(Locale.ENGLISH);
            synchronized (this.constraintsMap) {
                if (this.constraintsMap.containsKey(algorithm)) {
                    for (KeySizeConstraint constraint : (Set) this.constraintsMap.get(algorithm)) {
                        if (constraint.disables(key)) {
                            return true;
                        }
                    }
                }
            }
        }
    }

    public DisabledAlgorithmConstraints(String propertyName) {
        synchronized (disabledAlgorithmsMap) {
            if (!disabledAlgorithmsMap.containsKey(propertyName)) {
                loadDisabledAlgorithmsMap(propertyName);
            }
            this.disabledAlgorithms = (String[]) disabledAlgorithmsMap.get(propertyName);
            this.keySizeConstraints = (KeySizeConstraints) keySizeConstraintsMap.get(propertyName);
        }
    }

    public final boolean permits(Set<CryptoPrimitive> primitives, String algorithm, AlgorithmParameters parameters) {
        if (algorithm == null || algorithm.length() == 0) {
            throw new IllegalArgumentException("No algorithm name specified");
        } else if (primitives == null || primitives.isEmpty()) {
            throw new IllegalArgumentException("No cryptographic primitive specified");
        } else {
            Set<String> elements = null;
            for (String disabled : this.disabledAlgorithms) {
                if (!(disabled == null || disabled.isEmpty())) {
                    if (disabled.equalsIgnoreCase(algorithm)) {
                        return false;
                    }
                    if (elements == null) {
                        elements = decomposes(algorithm);
                    }
                    for (String element : r3) {
                        if (disabled.equalsIgnoreCase(element)) {
                            return false;
                        }
                    }
                    continue;
                }
            }
            return true;
        }
    }

    public final boolean permits(Set<CryptoPrimitive> primitives, Key key) {
        return checkConstraints(primitives, "", key, null);
    }

    public final boolean permits(Set<CryptoPrimitive> primitives, String algorithm, Key key, AlgorithmParameters parameters) {
        if (algorithm != null && algorithm.length() != 0) {
            return checkConstraints(primitives, algorithm, key, parameters);
        }
        throw new IllegalArgumentException("No algorithm name specified");
    }

    protected Set<String> decomposes(String algorithm) {
        if (algorithm == null || algorithm.length() == 0) {
            return new HashSet();
        }
        String[] transTockens = Pattern.compile("/").split(algorithm);
        Set<String> elements = new HashSet();
        for (String transTocken : transTockens) {
            if (!(transTocken == null || transTocken.length() == 0)) {
                for (String token : Pattern.compile("with|and", 2).split(transTocken)) {
                    if (!(token == null || token.length() == 0)) {
                        elements.add(token);
                    }
                }
            }
        }
        if (elements.contains("SHA1") && !elements.contains("SHA-1")) {
            elements.add("SHA-1");
        }
        if (elements.contains("SHA-1") && !elements.contains("SHA1")) {
            elements.add("SHA1");
        }
        if (elements.contains("SHA224") && !elements.contains("SHA-224")) {
            elements.add("SHA-224");
        }
        if (elements.contains("SHA-224") && !elements.contains("SHA224")) {
            elements.add("SHA224");
        }
        if (elements.contains("SHA256") && !elements.contains("SHA-256")) {
            elements.add("SHA-256");
        }
        if (elements.contains("SHA-256") && !elements.contains("SHA256")) {
            elements.add("SHA256");
        }
        if (elements.contains("SHA384") && !elements.contains("SHA-384")) {
            elements.add("SHA-384");
        }
        if (elements.contains("SHA-384") && !elements.contains("SHA384")) {
            elements.add("SHA384");
        }
        if (elements.contains("SHA512") && !elements.contains("SHA-512")) {
            elements.add("SHA-512");
        }
        if (elements.contains("SHA-512") && !elements.contains("SHA512")) {
            elements.add("SHA512");
        }
        return elements;
    }

    private boolean checkConstraints(Set<CryptoPrimitive> primitives, String algorithm, Key key, AlgorithmParameters parameters) {
        if (key == null) {
            throw new IllegalArgumentException("The key cannot be null");
        } else if ((algorithm == null || algorithm.length() == 0 || permits(primitives, algorithm, parameters)) && permits(primitives, key.getAlgorithm(), null) && !this.keySizeConstraints.disables(key)) {
            return true;
        } else {
            return false;
        }
    }

    private static void loadDisabledAlgorithmsMap(final String propertyName) {
        String property = (String) AccessController.doPrivileged(new PrivilegedAction<String>() {
            public String run() {
                return Security.getProperty(propertyName);
            }
        });
        Object algorithmsInProperty = null;
        if (!(property == null || property.isEmpty())) {
            if (property.charAt(0) == '\"' && property.charAt(property.length() - 1) == '\"') {
                property = property.substring(1, property.length() - 1);
            }
            algorithmsInProperty = property.split(",");
            for (int i = 0; i < algorithmsInProperty.length; i++) {
                algorithmsInProperty[i] = algorithmsInProperty[i].trim();
            }
        }
        if (algorithmsInProperty == null) {
            algorithmsInProperty = new String[0];
        }
        disabledAlgorithmsMap.put(propertyName, algorithmsInProperty);
        keySizeConstraintsMap.put(propertyName, new KeySizeConstraints(algorithmsInProperty));
    }
}
