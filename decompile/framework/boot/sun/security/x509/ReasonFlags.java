package sun.security.x509;

import java.io.IOException;
import java.util.Enumeration;
import sun.security.util.BitArray;
import sun.security.util.DerInputStream;
import sun.security.util.DerOutputStream;
import sun.security.util.DerValue;

public class ReasonFlags {
    public static final String AA_COMPROMISE = "aa_compromise";
    public static final String AFFILIATION_CHANGED = "affiliation_changed";
    public static final String CA_COMPROMISE = "ca_compromise";
    public static final String CERTIFICATE_HOLD = "certificate_hold";
    public static final String CESSATION_OF_OPERATION = "cessation_of_operation";
    public static final String KEY_COMPROMISE = "key_compromise";
    private static final String[] NAMES = new String[]{UNUSED, KEY_COMPROMISE, CA_COMPROMISE, AFFILIATION_CHANGED, SUPERSEDED, CESSATION_OF_OPERATION, CERTIFICATE_HOLD, PRIVILEGE_WITHDRAWN, AA_COMPROMISE};
    public static final String PRIVILEGE_WITHDRAWN = "privilege_withdrawn";
    public static final String SUPERSEDED = "superseded";
    public static final String UNUSED = "unused";
    private boolean[] bitString;

    private static int name2Index(String name) throws IOException {
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equalsIgnoreCase(name)) {
                return i;
            }
        }
        throw new IOException("Name not recognized by ReasonFlags");
    }

    private boolean isSet(int position) {
        return this.bitString[position];
    }

    private void set(int position, boolean val) {
        if (position >= this.bitString.length) {
            boolean[] tmp = new boolean[(position + 1)];
            System.arraycopy(this.bitString, 0, tmp, 0, this.bitString.length);
            this.bitString = tmp;
        }
        this.bitString[position] = val;
    }

    public ReasonFlags(byte[] reasons) {
        this.bitString = new BitArray(reasons.length * 8, reasons).toBooleanArray();
    }

    public ReasonFlags(boolean[] reasons) {
        this.bitString = reasons;
    }

    public ReasonFlags(BitArray reasons) {
        this.bitString = reasons.toBooleanArray();
    }

    public ReasonFlags(DerInputStream in) throws IOException {
        this.bitString = in.getDerValue().getUnalignedBitString(true).toBooleanArray();
    }

    public ReasonFlags(DerValue derVal) throws IOException {
        this.bitString = derVal.getUnalignedBitString(true).toBooleanArray();
    }

    public boolean[] getFlags() {
        return this.bitString;
    }

    public void set(String name, Object obj) throws IOException {
        if (obj instanceof Boolean) {
            set(name2Index(name), ((Boolean) obj).booleanValue());
            return;
        }
        throw new IOException("Attribute must be of type Boolean.");
    }

    public Object get(String name) throws IOException {
        return Boolean.valueOf(isSet(name2Index(name)));
    }

    public void delete(String name) throws IOException {
        set(name, Boolean.FALSE);
    }

    public String toString() {
        String s = "Reason Flags [\n";
        try {
            if (isSet(0)) {
                s = s + "  Unused\n";
            }
            if (isSet(1)) {
                s = s + "  Key Compromise\n";
            }
            if (isSet(2)) {
                s = s + "  CA Compromise\n";
            }
            if (isSet(3)) {
                s = s + "  Affiliation_Changed\n";
            }
            if (isSet(4)) {
                s = s + "  Superseded\n";
            }
            if (isSet(5)) {
                s = s + "  Cessation Of Operation\n";
            }
            if (isSet(6)) {
                s = s + "  Certificate Hold\n";
            }
            if (isSet(7)) {
                s = s + "  Privilege Withdrawn\n";
            }
            if (isSet(8)) {
                s = s + "  AA Compromise\n";
            }
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        return s + "]\n";
    }

    public void encode(DerOutputStream out) throws IOException {
        out.putTruncatedUnalignedBitString(new BitArray(this.bitString));
    }

    public Enumeration<String> getElements() {
        AttributeNameEnumeration elements = new AttributeNameEnumeration();
        for (Object addElement : NAMES) {
            elements.addElement(addElement);
        }
        return elements.elements();
    }
}
