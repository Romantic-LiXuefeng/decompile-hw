package com.android.internal.telephony.cat;

import com.android.internal.telephony.CallFailCause;
import java.io.ByteArrayOutputStream;

/* compiled from: ResponseData */
class ReceiveDataResponseData extends ResponseData {
    private byte[] mData = null;
    private int mLength = 0;

    public ReceiveDataResponseData(byte[] data, int len) {
        this.mData = data;
        this.mLength = len;
    }

    public void format(ByteArrayOutputStream buf) {
        int tag = ComprehensionTlvTag.CHANNEL_DATA.value() | 128;
        if (this.mData != null) {
            buf.write(tag);
            if (this.mData.length > CallFailCause.INTERWORKING_UNSPECIFIED) {
                buf.write(129);
            }
            buf.write(this.mData.length & 255);
            for (byte b : this.mData) {
                buf.write(b);
            }
        }
        buf.write(ComprehensionTlvTag.CHANNEL_DATA_LENGTH.value() | 128);
        buf.write(1);
        buf.write(this.mLength & 255);
    }
}
