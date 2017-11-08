package sun.security.ssl;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import javax.crypto.BadPaddingException;
import javax.net.ssl.SSLException;
import sun.misc.HexDumpEncoder;

final class EngineInputRecord extends InputRecord {
    static final /* synthetic */ boolean -assertionsDisabled;
    private static ByteBuffer tmpBB = ByteBuffer.allocate(0);
    private SSLEngineImpl engine;
    private boolean internalData;

    static {
        boolean z;
        if (EngineInputRecord.class.desiredAssertionStatus()) {
            z = false;
        } else {
            z = true;
        }
        -assertionsDisabled = z;
    }

    EngineInputRecord(SSLEngineImpl engine) {
        this.engine = engine;
    }

    byte contentType() {
        if (this.internalData) {
            return super.contentType();
        }
        return (byte) 23;
    }

    int bytesInCompletePacket(ByteBuffer buf) throws SSLException {
        boolean isShort = false;
        if (buf.remaining() < 5) {
            return -1;
        }
        int len;
        int pos = buf.position();
        byte byteZero = buf.get(pos);
        Object recordVersion;
        if (this.formatVerified || byteZero == (byte) 22 || byteZero == (byte) 21) {
            recordVersion = ProtocolVersion.valueOf(buf.get(pos + 1), buf.get(pos + 2));
            if (recordVersion.v < ProtocolVersion.MIN.v || recordVersion.major > ProtocolVersion.MAX.major) {
                throw new SSLException("Unsupported record version " + recordVersion);
            }
            this.formatVerified = true;
            len = (((buf.get(pos + 3) & 255) << 8) + (buf.get(pos + 4) & 255)) + 5;
        } else {
            if ((byteZero & 128) != 0) {
                isShort = true;
            }
            if (isShort && (buf.get(pos + 2) == (byte) 1 || buf.get(pos + 2) == (byte) 4)) {
                recordVersion = ProtocolVersion.valueOf(buf.get(pos + 3), buf.get(pos + 4));
                if ((recordVersion.v < ProtocolVersion.MIN.v || recordVersion.major > ProtocolVersion.MAX.major) && recordVersion.v != ProtocolVersion.SSL20Hello.v) {
                    throw new SSLException("Unsupported record version " + recordVersion);
                }
                len = ((buf.get(pos + 1) & 255) + ((byteZero & (isShort ? 127 : 63)) << 8)) + (isShort ? 2 : 3);
            } else {
                throw new SSLException("Unrecognized SSL message, plaintext connection?");
            }
        }
        return len;
    }

    ByteBuffer decrypt(MAC signer, CipherBox box, ByteBuffer bb) throws BadPaddingException {
        if (this.internalData) {
            decrypt(signer, box);
            return tmpBB;
        }
        BadPaddingException badPaddingException = null;
        int tagLen = signer.MAClen();
        int cipheredLength = bb.remaining();
        if (!box.isNullCipher()) {
            if (box.sanityCheck(tagLen, cipheredLength)) {
                try {
                    box.decrypt(bb, tagLen);
                } catch (BadPaddingException bpe) {
                    badPaddingException = bpe;
                } finally {
                    bb.rewind();
                }
            } else {
                throw new BadPaddingException("ciphertext sanity check failed");
            }
        }
        if (tagLen != 0) {
            int macOffset = bb.limit() - tagLen;
            if (bb.remaining() < tagLen) {
                if (badPaddingException == null) {
                    badPaddingException = new BadPaddingException("bad record");
                }
                macOffset = cipheredLength - tagLen;
                bb.limit(cipheredLength);
            }
            if (checkMacTags(contentType(), bb, signer, false) && r9 == null) {
                badPaddingException = new BadPaddingException("bad record MAC");
            }
            if (box.isCBCMode()) {
                int remainingLen = InputRecord.calculateRemainingLen(signer, cipheredLength, macOffset);
                if (remainingLen > this.buf.length) {
                    throw new RuntimeException("Internal buffer capacity error");
                }
                InputRecord.checkMacTags(contentType(), this.buf, 0, remainingLen, signer, true);
            }
            bb.limit(macOffset);
        }
        if (badPaddingException == null) {
            return bb.slice();
        }
        throw badPaddingException;
    }

    private static boolean checkMacTags(byte contentType, ByteBuffer bb, MAC signer, boolean isSimulated) {
        boolean z = false;
        int tagLen = signer.MAClen();
        int lim = bb.limit();
        int macData = lim - tagLen;
        bb.limit(macData);
        byte[] hash = signer.compute(contentType, bb, isSimulated);
        if (hash == null || tagLen != hash.length) {
            throw new RuntimeException("Internal MAC error");
        }
        bb.position(macData);
        bb.limit(lim);
        try {
            if (compareMacTags(bb, hash)[0] != 0) {
                z = true;
            }
            bb.rewind();
            bb.limit(macData);
            return z;
        } catch (Throwable th) {
            bb.rewind();
            bb.limit(macData);
        }
    }

    private static int[] compareMacTags(ByteBuffer bb, byte[] tag) {
        int[] results = new int[]{0, 0};
        for (byte b : tag) {
            if (bb.get() != b) {
                results[0] = results[0] + 1;
            } else {
                results[1] = results[1] + 1;
            }
        }
        return results;
    }

    void writeBuffer(OutputStream s, byte[] buf, int off, int len) throws IOException {
        this.engine.writer.putOutboundDataSync((ByteBuffer) ByteBuffer.allocate(len).put(buf, 0, len).flip());
    }

    ByteBuffer read(ByteBuffer srcBB) throws IOException {
        if (this.formatVerified && srcBB.get(srcBB.position()) == (byte) 23) {
            this.internalData = false;
            int srcPos = srcBB.position();
            int srcLim = srcBB.limit();
            Object recordVersion = ProtocolVersion.valueOf(srcBB.get(srcPos + 1), srcBB.get(srcPos + 2));
            if (recordVersion.v < ProtocolVersion.MIN.v || recordVersion.major > ProtocolVersion.MAX.major) {
                throw new SSLException("Unsupported record version " + recordVersion);
            }
            ByteBuffer bb;
            int len = bytesInCompletePacket(srcBB);
            if (!-assertionsDisabled) {
                if (!(len > 0)) {
                    throw new AssertionError();
                }
            }
            if (debug != null && Debug.isOn("packet")) {
                try {
                    HexDumpEncoder hd = new HexDumpEncoder();
                    srcBB.limit(srcPos + len);
                    bb = srcBB.duplicate();
                    System.out.println("[Raw read (bb)]: length = " + len);
                    hd.encodeBuffer(bb, System.out);
                } catch (IOException e) {
                }
            }
            srcBB.position(srcPos + 5);
            srcBB.limit(srcPos + len);
            bb = srcBB.slice();
            srcBB.position(srcBB.limit());
            srcBB.limit(srcLim);
            return bb;
        }
        this.internalData = true;
        read(new ByteBufferInputStream(srcBB), (OutputStream) null);
        return tmpBB;
    }
}
