package android.net.metrics;

import android.graphics.Color;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import android.util.SparseArray;
import com.android.internal.util.MessageUtils;

public final class DhcpErrorEvent extends IpConnectivityEvent implements Parcelable {
    public static final int BOOTP_TOO_SHORT = makeErrorCode(4, 1);
    public static final int BUFFER_UNDERFLOW = makeErrorCode(5, 1);
    public static final Creator<DhcpErrorEvent> CREATOR = new Creator<DhcpErrorEvent>() {
        public DhcpErrorEvent createFromParcel(Parcel in) {
            return new DhcpErrorEvent(in);
        }

        public DhcpErrorEvent[] newArray(int size) {
            return new DhcpErrorEvent[size];
        }
    };
    public static final int DHCP_BAD_MAGIC_COOKIE = makeErrorCode(4, 2);
    public static final int DHCP_ERROR = 4;
    public static final int DHCP_INVALID_OPTION_LENGTH = makeErrorCode(4, 3);
    public static final int DHCP_NO_MSG_TYPE = makeErrorCode(4, 4);
    public static final int DHCP_UNKNOWN_MSG_TYPE = makeErrorCode(4, 5);
    public static final int L2_ERROR = 1;
    public static final int L2_TOO_SHORT = makeErrorCode(1, 1);
    public static final int L2_WRONG_ETH_TYPE = makeErrorCode(1, 2);
    public static final int L3_ERROR = 2;
    public static final int L3_INVALID_IP = makeErrorCode(2, 3);
    public static final int L3_NOT_IPV4 = makeErrorCode(2, 2);
    public static final int L3_TOO_SHORT = makeErrorCode(2, 1);
    public static final int L4_ERROR = 3;
    public static final int L4_NOT_UDP = makeErrorCode(3, 1);
    public static final int L4_WRONG_PORT = makeErrorCode(3, 2);
    public static final int MISC_ERROR = 5;
    public static final int RECEIVE_ERROR = makeErrorCode(5, 2);
    public final int errorCode;
    public final String ifName;

    static final class Decoder {
        static final SparseArray<String> constants = MessageUtils.findMessageNames(new Class[]{DhcpErrorEvent.class}, new String[]{"L2_", "L3_", "L4_", "BOOTP_", "DHCP_", "BUFFER_", "RECEIVE_"});

        Decoder() {
        }
    }

    private DhcpErrorEvent(String ifName, int errorCode) {
        this.ifName = ifName;
        this.errorCode = errorCode;
    }

    private DhcpErrorEvent(Parcel in) {
        this.ifName = in.readString();
        this.errorCode = in.readInt();
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeString(this.ifName);
        out.writeInt(this.errorCode);
    }

    public int describeContents() {
        return 0;
    }

    public static void logParseError(String ifName, int errorCode) {
        IpConnectivityEvent.logEvent(new DhcpErrorEvent(ifName, errorCode));
    }

    public static void logReceiveError(String ifName) {
        IpConnectivityEvent.logEvent(new DhcpErrorEvent(ifName, RECEIVE_ERROR));
    }

    public static int errorCodeWithOption(int errorCode, int option) {
        return (Color.RED & errorCode) | (option & 255);
    }

    private static int makeErrorCode(int type, int subtype) {
        return (type << 24) | ((subtype & 255) << 16);
    }

    public String toString() {
        return String.format("DhcpErrorEvent(%s, %s)", new Object[]{this.ifName, Decoder.constants.get(this.errorCode)});
    }
}
