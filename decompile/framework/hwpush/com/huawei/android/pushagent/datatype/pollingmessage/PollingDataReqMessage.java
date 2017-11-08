package com.huawei.android.pushagent.datatype.pollingmessage;

import com.huawei.android.pushagent.datatype.pollingmessage.basic.PollingMessage;
import defpackage.au;
import defpackage.aw;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class PollingDataReqMessage extends PollingMessage {
    private short mLength;
    private int mPollingId;
    private short mRequestId = ((short) (au.getUUID().hashCode() & 255));

    public PollingDataReqMessage(int i) {
        super(ay());
        this.mPollingId = i;
        this.mLength = (short) 7;
    }

    private static byte ay() {
        return (byte) 1;
    }

    public PollingMessage a(InputStream inputStream) {
        try {
            byte[] bArr = new byte[2];
            PollingMessage.a(inputStream, bArr);
            this.mRequestId = (short) au.g(bArr);
            bArr = new byte[4];
            PollingMessage.a(inputStream, bArr);
            this.mPollingId = au.byteArrayToInt(bArr);
        } catch (Throwable e) {
            aw.d("PushLog2841", e.toString(), e);
        }
        return this;
    }

    public byte[] encode() {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byteArrayOutputStream.write(au.c(this.mLength));
            byteArrayOutputStream.write(k());
            byteArrayOutputStream.write(au.c(this.mRequestId));
            byteArrayOutputStream.write(au.intToByteArray(this.mPollingId));
            aw.d("PushLog2841", "PollingDataReqMessage encode : baos " + au.f(byteArrayOutputStream.toByteArray()));
            return byteArrayOutputStream.toByteArray();
        } catch (Throwable e) {
            aw.d("PushLog2841", e.toString(), e);
            return null;
        }
    }

    public String toString() {
        return new StringBuffer(getClass().getSimpleName()).append(" mLength:").append(this.mLength).append(" cmdId:").append(aC()).append(" mRequestId:").append(this.mRequestId).append(" mPollingId:").append(this.mPollingId).toString();
    }
}
