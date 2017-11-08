package android.util;

final class IMonitorNative {
    public static native boolean addAndDelDynamicPath(long j, String str);

    public static native boolean addDynamicPath(long j, String str);

    public static native long createEvent(int i);

    public static native void destoryEvent(long j);

    public static native boolean sendEvent(long j);

    public static native boolean setParam(long j, short s, long j2);

    public static native boolean setParamFloat(long j, short s, float f);

    public static native boolean setParamString(long j, short s, String str);

    public static native boolean setTime(long j, long j2);

    public static native boolean unsetParam(long j, short s);

    IMonitorNative() {
    }

    static {
        try {
            Log.d(IMonitor.TAG, "Load library imonitor_jni");
            System.loadLibrary("imonitor_jni");
        } catch (UnsatisfiedLinkError e) {
            Log.e(IMonitor.TAG, "Library imonitor_jni not found");
        }
    }
}
