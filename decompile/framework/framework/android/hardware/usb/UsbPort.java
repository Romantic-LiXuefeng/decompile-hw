package android.hardware.usb;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import android.rms.iaware.AwareConstant.Database.HwAppType;
import com.android.internal.util.Preconditions;

public final class UsbPort implements Parcelable {
    public static final Creator<UsbPort> CREATOR = new Creator<UsbPort>() {
        public UsbPort createFromParcel(Parcel in) {
            return new UsbPort(in.readString(), in.readInt());
        }

        public UsbPort[] newArray(int size) {
            return new UsbPort[size];
        }
    };
    public static final int DATA_ROLE_DEVICE = 2;
    public static final int DATA_ROLE_HOST = 1;
    public static final int MODE_DFP = 1;
    public static final int MODE_DUAL = 3;
    public static final int MODE_UFP = 2;
    private static final int NUM_DATA_ROLES = 3;
    public static final int POWER_ROLE_SINK = 2;
    public static final int POWER_ROLE_SOURCE = 1;
    private final String mId;
    private final int mSupportedModes;

    public static java.lang.String roleCombinationsToString(int r1) {
        /* JADX: method processing error */
/*
Error: jadx.core.utils.exceptions.DecodeException: Load method exception in method: android.hardware.usb.UsbPort.roleCombinationsToString(int):java.lang.String
	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:116)
	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:249)
	at jadx.core.ProcessClass.process(ProcessClass.java:34)
	at jadx.core.ProcessClass.processDependencies(ProcessClass.java:59)
	at jadx.core.ProcessClass.process(ProcessClass.java:42)
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:306)
	at jadx.api.JavaClass.decompile(JavaClass.java:62)
	at jadx.api.JadxDecompiler$1.run(JadxDecompiler.java:199)
Caused by: jadx.core.utils.exceptions.DecodeException: Unknown instruction: not-int
	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:569)
	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:56)
	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:102)
	... 7 more
*/
        /*
        // Can't load method instructions.
        */
        throw new UnsupportedOperationException("Method not decompiled: android.hardware.usb.UsbPort.roleCombinationsToString(int):java.lang.String");
    }

    public UsbPort(String id, int supportedModes) {
        this.mId = id;
        this.mSupportedModes = supportedModes;
    }

    public String getId() {
        return this.mId;
    }

    public int getSupportedModes() {
        return this.mSupportedModes;
    }

    public static int combineRolesAsBit(int powerRole, int dataRole) {
        checkRoles(powerRole, dataRole);
        return 1 << ((powerRole * 3) + dataRole);
    }

    public static String modeToString(int mode) {
        switch (mode) {
            case 0:
                return "none";
            case 1:
                return "dfp";
            case 2:
                return "ufp";
            case 3:
                return "dual";
            default:
                return Integer.toString(mode);
        }
    }

    public static String powerRoleToString(int role) {
        switch (role) {
            case 0:
                return "no-power";
            case 1:
                return HwAppType.SOURCE;
            case 2:
                return "sink";
            default:
                return Integer.toString(role);
        }
    }

    public static String dataRoleToString(int role) {
        switch (role) {
            case 0:
                return "no-data";
            case 1:
                return "host";
            case 2:
                return UsbManager.EXTRA_DEVICE;
            default:
                return Integer.toString(role);
        }
    }

    public static void checkRoles(int powerRole, int dataRole) {
        Preconditions.checkArgumentInRange(powerRole, 0, 2, "powerRole");
        Preconditions.checkArgumentInRange(dataRole, 0, 2, "dataRole");
    }

    public String toString() {
        return "UsbPort{id=" + this.mId + ", supportedModes=" + modeToString(this.mSupportedModes) + "}";
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.mId);
        dest.writeInt(this.mSupportedModes);
    }
}
