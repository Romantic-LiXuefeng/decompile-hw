package android.content.pm;

public abstract class AbsApplicationInfo {
    public static final int BLACK_LIST_APK = 268435456;
    public static final int FLAG_UPDATED_REMOVEABLE_APP = 67108864;
    public static final int PARSE_CUST_APK = 134217728;
    public static final int PARSE_IS_REMOVABLE_PREINSTALLED_APK = 33554432;

    public int getHwFlags() {
        return 0;
    }
}
