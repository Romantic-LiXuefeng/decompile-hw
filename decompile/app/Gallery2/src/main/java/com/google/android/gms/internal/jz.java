package com.google.android.gms.internal;

import com.android.gallery3d.gadget.XmlUtils;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

/* compiled from: Unknown */
public final class jz {
    private final int aad;
    private final byte[] buffer;
    private int position;

    /* compiled from: Unknown */
    public static class a extends IOException {
        a(int i, int i2) {
            super("CodedOutputStream was writing to a flat byte array and ran out of space (pos " + i + " limit " + i2 + ").");
        }
    }

    private jz(byte[] bArr, int i, int i2) {
        this.buffer = bArr;
        this.position = i;
        this.aad = i + i2;
    }

    public static int A(long j) {
        return C(D(j));
    }

    public static int B(boolean z) {
        return 1;
    }

    public static int C(long j) {
        return (-128 & j) == 0 ? 1 : (-16384 & j) == 0 ? 2 : (-2097152 & j) == 0 ? 3 : (-268435456 & j) == 0 ? 4 : (-34359738368L & j) == 0 ? 5 : (-4398046511104L & j) == 0 ? 6 : (-562949953421312L & j) == 0 ? 7 : (-72057594037927936L & j) == 0 ? 8 : (Long.MIN_VALUE & j) == 0 ? 9 : 10;
    }

    public static long D(long j) {
        return (j << 1) ^ (j >> 63);
    }

    public static int b(int i, ke keVar) {
        return cE(i) + c(keVar);
    }

    public static int b(int i, boolean z) {
        return cE(i) + B(z);
    }

    public static jz b(byte[] bArr, int i, int i2) {
        return new jz(bArr, i, i2);
    }

    public static int bQ(String str) {
        try {
            byte[] bytes = str.getBytes(XmlUtils.INPUT_ENCODING);
            return bytes.length + cG(bytes.length);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("UTF-8 not supported.");
        }
    }

    public static int c(ke keVar) {
        int c = keVar.c();
        return c + cG(c);
    }

    public static int cC(int i) {
        return i < 0 ? 10 : cG(i);
    }

    public static int cE(int i) {
        return cG(kh.i(i, 0));
    }

    public static int cG(int i) {
        return (i & -128) != 0 ? (i & -16384) != 0 ? (-2097152 & i) != 0 ? (-268435456 & i) != 0 ? 5 : 4 : 3 : 2 : 1;
    }

    public static int d(int i, long j) {
        return cE(i) + z(j);
    }

    public static int e(int i, long j) {
        return cE(i) + A(j);
    }

    public static int g(int i, int i2) {
        return cE(i) + cC(i2);
    }

    public static int g(int i, String str) {
        return cE(i) + bQ(str);
    }

    public static int z(long j) {
        return C(j);
    }

    public void A(boolean z) throws IOException {
        int i = 0;
        if (z) {
            i = 1;
        }
        cD(i);
    }

    public void B(long j) throws IOException {
        while ((-128 & j) != 0) {
            cD((((int) j) & 127) | 128);
            j >>>= 7;
        }
        cD((int) j);
    }

    public void a(int i, ke keVar) throws IOException {
        h(i, 2);
        b(keVar);
    }

    public void a(int i, boolean z) throws IOException {
        h(i, 0);
        A(z);
    }

    public void b(byte b) throws IOException {
        if (this.position != this.aad) {
            byte[] bArr = this.buffer;
            int i = this.position;
            this.position = i + 1;
            bArr[i] = (byte) b;
            return;
        }
        throw new a(this.position, this.aad);
    }

    public void b(int i, long j) throws IOException {
        h(i, 0);
        x(j);
    }

    public void b(int i, String str) throws IOException {
        h(i, 2);
        bP(str);
    }

    public void b(ke keVar) throws IOException {
        cF(keVar.eW());
        keVar.a(this);
    }

    public void bP(String str) throws IOException {
        byte[] bytes = str.getBytes(XmlUtils.INPUT_ENCODING);
        cF(bytes.length);
        p(bytes);
    }

    public void c(int i, long j) throws IOException {
        h(i, 0);
        y(j);
    }

    public void c(byte[] bArr, int i, int i2) throws IOException {
        if (this.aad - this.position < i2) {
            throw new a(this.position, this.aad);
        }
        System.arraycopy(bArr, i, this.buffer, this.position, i2);
        this.position += i2;
    }

    public void cB(int i) throws IOException {
        if (i < 0) {
            B((long) i);
        } else {
            cF(i);
        }
    }

    public void cD(int i) throws IOException {
        b((byte) i);
    }

    public void cF(int i) throws IOException {
        while ((i & -128) != 0) {
            cD((i & 127) | 128);
            i >>>= 7;
        }
        cD(i);
    }

    public void f(int i, int i2) throws IOException {
        h(i, 0);
        cB(i2);
    }

    public void h(int i, int i2) throws IOException {
        cF(kh.i(i, i2));
    }

    public int kM() {
        return this.aad - this.position;
    }

    public void kN() {
        if (kM() != 0) {
            throw new IllegalStateException("Did not write as much data as expected.");
        }
    }

    public void p(byte[] bArr) throws IOException {
        c(bArr, 0, bArr.length);
    }

    public void x(long j) throws IOException {
        B(j);
    }

    public void y(long j) throws IOException {
        B(D(j));
    }
}
