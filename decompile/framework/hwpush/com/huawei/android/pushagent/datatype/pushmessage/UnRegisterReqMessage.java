package com.huawei.android.pushagent.datatype.pushmessage;

import android.text.TextUtils;
import com.huawei.android.pushagent.datatype.pushmessage.basic.PushMessage;
import defpackage.aw;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class UnRegisterReqMessage extends PushMessage {
    private String mToken = null;

    public UnRegisterReqMessage() {
        super(ay());
    }

    public UnRegisterReqMessage(String str) {
        super(ay());
        h(str);
    }

    private static byte ay() {
        return (byte) -42;
    }

    public String aS() {
        return this.mToken;
    }

    public PushMessage c(InputStream inputStream) {
        byte[] bArr = new byte[32];
        PushMessage.a(inputStream, bArr);
        this.mToken = new String(bArr, "UTF-8");
        return this;
    }

    public byte[] encode() {
        byte[] bArr = null;
        try {
            if (TextUtils.isEmpty(this.mToken)) {
                aw.e("PushLog2841", "encode error reason mToken is empty");
            } else {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                byteArrayOutputStream.write(k());
                byteArrayOutputStream.write(aS().getBytes("UTF-8"));
                bArr = byteArrayOutputStream.toByteArray();
            }
        } catch (Exception e) {
            aw.e("PushLog2841", "encode error " + e.toString());
        }
        return bArr;
    }

    public void h(String str) {
        this.mToken = str;
    }

    public String toString() {
        return "UnRegisterReqMessage[token:" + this.mToken + "]";
    }
}
