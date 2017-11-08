package sun.net.util;

public class IPAddressUtil {
    private static final int INADDR16SZ = 16;
    private static final int INADDR4SZ = 4;
    private static final int INT16SZ = 2;

    public static byte[] textToNumericFormatV4(String src) {
        if (src.length() == 0) {
            return null;
        }
        byte[] res = new byte[4];
        String[] s = src.split("\\.", -1);
        try {
            switch (s.length) {
                case 4:
                    for (int i = 0; i < 4; i++) {
                        long val = (long) Integer.parseInt(s[i]);
                        if (val < 0 || val > 255) {
                            return null;
                        }
                        res[i] = (byte) ((int) (val & 255));
                    }
                    return res;
                default:
                    return null;
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static byte[] textToNumericFormatV6(String src) {
        if (src.length() < 2) {
            return null;
        }
        char[] srcb = src.toCharArray();
        byte[] dst = new byte[16];
        int srcb_length = srcb.length;
        int pc = src.indexOf("%");
        if (pc == srcb_length - 1) {
            return null;
        }
        byte[] newdst;
        if (pc != -1) {
            srcb_length = pc;
        }
        int colonp = -1;
        int i = 0;
        if (srcb[0] == ':') {
            i = 1;
            if (srcb[1] != ':') {
                return null;
            }
        }
        int curtok = i;
        boolean saw_xdigit = false;
        int val = 0;
        int j = 0;
        int i2 = i;
        while (i2 < srcb_length) {
            int i3;
            int n;
            i = i2 + 1;
            char ch = srcb[i2];
            int chval = Character.digit(ch, 16);
            if (chval != -1) {
                val = (val << 4) | chval;
                if (val > 65535) {
                    return null;
                }
                saw_xdigit = true;
                i2 = i;
            } else if (ch == ':') {
                curtok = i;
                if (saw_xdigit) {
                    if (i == srcb_length) {
                        return null;
                    }
                    if (j + 2 > 16) {
                        return null;
                    }
                    i3 = j + 1;
                    dst[j] = (byte) ((val >> 8) & 255);
                    j = i3 + 1;
                    dst[i3] = (byte) (val & 255);
                    saw_xdigit = false;
                    val = 0;
                    i2 = i;
                } else if (colonp != -1) {
                    return null;
                } else {
                    colonp = j;
                    i2 = i;
                }
            } else if (ch != '.' || j + 4 > 16) {
                return null;
            } else {
                String ia4 = src.substring(curtok, srcb_length);
                int dot_count = 0;
                int index = 0;
                while (true) {
                    index = ia4.indexOf(46, index);
                    if (index == -1) {
                        break;
                    }
                    dot_count++;
                    index++;
                }
                if (dot_count != 3) {
                    return null;
                }
                byte[] v4addr = textToNumericFormatV4(ia4);
                if (v4addr == null) {
                    return null;
                }
                int k = 0;
                while (k < 4) {
                    i3 = j + 1;
                    dst[j] = v4addr[k];
                    k++;
                    j = i3;
                }
                saw_xdigit = false;
                if (saw_xdigit) {
                    i3 = j;
                } else if (j + 2 > 16) {
                    return null;
                } else {
                    i3 = j + 1;
                    dst[j] = (byte) ((val >> 8) & 255);
                    j = i3 + 1;
                    dst[i3] = (byte) (val & 255);
                    i3 = j;
                }
                if (colonp != -1) {
                    n = i3 - colonp;
                    if (i3 == 16) {
                        return null;
                    }
                    for (i = 1; i <= n; i++) {
                        dst[16 - i] = dst[(colonp + n) - i];
                        dst[(colonp + n) - i] = (byte) 0;
                    }
                    i3 = 16;
                }
                if (i3 != 16) {
                    return null;
                }
                newdst = convertFromIPv4MappedAddress(dst);
                if (newdst == null) {
                    return newdst;
                }
                return dst;
            }
        }
        if (saw_xdigit) {
            i3 = j;
        } else if (j + 2 > 16) {
            return null;
        } else {
            i3 = j + 1;
            dst[j] = (byte) ((val >> 8) & 255);
            j = i3 + 1;
            dst[i3] = (byte) (val & 255);
            i3 = j;
        }
        if (colonp != -1) {
            n = i3 - colonp;
            if (i3 == 16) {
                return null;
            }
            for (i = 1; i <= n; i++) {
                dst[16 - i] = dst[(colonp + n) - i];
                dst[(colonp + n) - i] = (byte) 0;
            }
            i3 = 16;
        }
        if (i3 != 16) {
            return null;
        }
        newdst = convertFromIPv4MappedAddress(dst);
        if (newdst == null) {
            return dst;
        }
        return newdst;
    }

    public static boolean isIPv4LiteralAddress(String src) {
        return textToNumericFormatV4(src) != null;
    }

    public static boolean isIPv6LiteralAddress(String src) {
        return textToNumericFormatV6(src) != null;
    }

    public static byte[] convertFromIPv4MappedAddress(byte[] addr) {
        if (!isIPv4MappedAddress(addr)) {
            return null;
        }
        byte[] newAddr = new byte[4];
        System.arraycopy(addr, 12, newAddr, 0, 4);
        return newAddr;
    }

    private static boolean isIPv4MappedAddress(byte[] addr) {
        return addr.length >= 16 && addr[0] == (byte) 0 && addr[1] == (byte) 0 && addr[2] == (byte) 0 && addr[3] == (byte) 0 && addr[4] == (byte) 0 && addr[5] == (byte) 0 && addr[6] == (byte) 0 && addr[7] == (byte) 0 && addr[8] == (byte) 0 && addr[9] == (byte) 0 && addr[10] == (byte) -1 && addr[11] == (byte) -1;
    }
}
