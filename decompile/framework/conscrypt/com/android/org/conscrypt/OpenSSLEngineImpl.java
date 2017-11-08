package com.android.org.conscrypt;

import com.android.org.conscrypt.NativeCrypto.SSLHandshakeCallbacks;
import com.android.org.conscrypt.SSLParametersImpl.AliasChooser;
import com.android.org.conscrypt.SSLParametersImpl.PSKCallbacks;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.crypto.SecretKey;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

public class OpenSSLEngineImpl extends SSLEngine implements SSLHandshakeCallbacks, AliasChooser, PSKCallbacks {
    private static final /* synthetic */ int[] -com-android-org-conscrypt-OpenSSLEngineImpl$EngineStateSwitchesValues = null;
    private static OpenSSLBIOSource nullSource = OpenSSLBIOSource.wrap(ByteBuffer.allocate(0));
    OpenSSLKey channelIdPrivateKey;
    private EngineState engineState = EngineState.NEW;
    private OpenSSLSessionImpl handshakeSession;
    private OpenSSLBIOSink handshakeSink;
    private final OpenSSLBIOSink localToRemoteSink = OpenSSLBIOSink.create();
    private long sslNativePointer;
    private final SSLParametersImpl sslParameters;
    private OpenSSLSessionImpl sslSession;
    private final Object stateLock = new Object();

    private enum EngineState {
        NEW,
        MODE_SET,
        HANDSHAKE_WANTED,
        HANDSHAKE_STARTED,
        HANDSHAKE_COMPLETED,
        READY_HANDSHAKE_CUT_THROUGH,
        READY,
        CLOSED_INBOUND,
        CLOSED_OUTBOUND,
        CLOSED
    }

    private static /* synthetic */ int[] -getcom-android-org-conscrypt-OpenSSLEngineImpl$EngineStateSwitchesValues() {
        if (-com-android-org-conscrypt-OpenSSLEngineImpl$EngineStateSwitchesValues != null) {
            return -com-android-org-conscrypt-OpenSSLEngineImpl$EngineStateSwitchesValues;
        }
        int[] iArr = new int[EngineState.values().length];
        try {
            iArr[EngineState.CLOSED.ordinal()] = 1;
        } catch (NoSuchFieldError e) {
        }
        try {
            iArr[EngineState.CLOSED_INBOUND.ordinal()] = 2;
        } catch (NoSuchFieldError e2) {
        }
        try {
            iArr[EngineState.CLOSED_OUTBOUND.ordinal()] = 3;
        } catch (NoSuchFieldError e3) {
        }
        try {
            iArr[EngineState.HANDSHAKE_COMPLETED.ordinal()] = 4;
        } catch (NoSuchFieldError e4) {
        }
        try {
            iArr[EngineState.HANDSHAKE_STARTED.ordinal()] = 5;
        } catch (NoSuchFieldError e5) {
        }
        try {
            iArr[EngineState.HANDSHAKE_WANTED.ordinal()] = 6;
        } catch (NoSuchFieldError e6) {
        }
        try {
            iArr[EngineState.MODE_SET.ordinal()] = 7;
        } catch (NoSuchFieldError e7) {
        }
        try {
            iArr[EngineState.NEW.ordinal()] = 8;
        } catch (NoSuchFieldError e8) {
        }
        try {
            iArr[EngineState.READY.ordinal()] = 9;
        } catch (NoSuchFieldError e9) {
        }
        try {
            iArr[EngineState.READY_HANDSHAKE_CUT_THROUGH.ordinal()] = 10;
        } catch (NoSuchFieldError e10) {
        }
        -com-android-org-conscrypt-OpenSSLEngineImpl$EngineStateSwitchesValues = iArr;
        return iArr;
    }

    public OpenSSLEngineImpl(SSLParametersImpl sslParameters) {
        this.sslParameters = sslParameters;
    }

    public OpenSSLEngineImpl(String host, int port, SSLParametersImpl sslParameters) {
        super(host, port);
        this.sslParameters = sslParameters;
    }

    public void beginHandshake() throws SSLException {
        synchronized (this.stateLock) {
            if (!(this.engineState == EngineState.CLOSED || this.engineState == EngineState.CLOSED_OUTBOUND)) {
                if (this.engineState != EngineState.CLOSED_INBOUND) {
                    if (this.engineState == EngineState.HANDSHAKE_STARTED) {
                        throw new IllegalStateException("Handshake has already been started");
                    } else if (this.engineState != EngineState.MODE_SET) {
                        throw new IllegalStateException("Client/server mode must be set before handshake");
                    } else {
                        if (getUseClientMode()) {
                            this.engineState = EngineState.HANDSHAKE_WANTED;
                        } else {
                            this.engineState = EngineState.HANDSHAKE_STARTED;
                        }
                    }
                }
            }
            throw new IllegalStateException("Engine has already been closed");
        }
        try {
            long sslCtxNativePointer = this.sslParameters.getSessionContext().sslCtxNativePointer;
            this.sslNativePointer = NativeCrypto.SSL_new(sslCtxNativePointer);
            this.sslSession = this.sslParameters.getSessionToReuse(this.sslNativePointer, getPeerHost(), getPeerPort());
            this.sslParameters.setSSLParameters(sslCtxNativePointer, this.sslNativePointer, this, this, getPeerHost());
            this.sslParameters.setCertificateValidation(this.sslNativePointer);
            this.sslParameters.setTlsChannelId(this.sslNativePointer, this.channelIdPrivateKey);
            if (getUseClientMode()) {
                NativeCrypto.SSL_set_connect_state(this.sslNativePointer);
            } else {
                NativeCrypto.SSL_set_accept_state(this.sslNativePointer);
            }
            this.handshakeSink = OpenSSLBIOSink.create();
            if (false) {
                synchronized (this.stateLock) {
                    this.engineState = EngineState.CLOSED;
                }
                shutdownAndFreeSslNative();
            }
        } catch (IOException e) {
            if (e.getMessage().contains("unexpected CCS")) {
                Platform.logEvent(String.format("ssl_unexpected_ccs: host=%s", new Object[]{getPeerHost()}));
            }
            throw new SSLException(e);
        } catch (Throwable th) {
            if (true) {
                synchronized (this.stateLock) {
                    this.engineState = EngineState.CLOSED;
                    shutdownAndFreeSslNative();
                }
            }
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void closeInbound() throws SSLException {
        synchronized (this.stateLock) {
            if (this.engineState == EngineState.CLOSED) {
            } else if (this.engineState == EngineState.CLOSED_OUTBOUND) {
                this.engineState = EngineState.CLOSED;
            } else {
                this.engineState = EngineState.CLOSED_INBOUND;
            }
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void closeOutbound() {
        synchronized (this.stateLock) {
            if (this.engineState == EngineState.CLOSED || this.engineState == EngineState.CLOSED_OUTBOUND) {
            } else {
                if (!(this.engineState == EngineState.MODE_SET || this.engineState == EngineState.NEW)) {
                    shutdownAndFreeSslNative();
                }
                if (this.engineState == EngineState.CLOSED_INBOUND) {
                    this.engineState = EngineState.CLOSED;
                } else {
                    this.engineState = EngineState.CLOSED_OUTBOUND;
                }
            }
        }
    }

    public Runnable getDelegatedTask() {
        return null;
    }

    public String[] getEnabledCipherSuites() {
        return this.sslParameters.getEnabledCipherSuites();
    }

    public String[] getEnabledProtocols() {
        return this.sslParameters.getEnabledProtocols();
    }

    public boolean getEnableSessionCreation() {
        return this.sslParameters.getEnableSessionCreation();
    }

    public HandshakeStatus getHandshakeStatus() {
        synchronized (this.stateLock) {
            HandshakeStatus handshakeStatus;
            switch (-getcom-android-org-conscrypt-OpenSSLEngineImpl$EngineStateSwitchesValues()[this.engineState.ordinal()]) {
                case 1:
                case 2:
                case 3:
                case 7:
                case 8:
                case 9:
                case 10:
                    handshakeStatus = HandshakeStatus.NOT_HANDSHAKING;
                    return handshakeStatus;
                case 4:
                    if (this.handshakeSink.available() == 0) {
                        this.handshakeSink = null;
                        this.engineState = EngineState.READY;
                        handshakeStatus = HandshakeStatus.FINISHED;
                        return handshakeStatus;
                    }
                    handshakeStatus = HandshakeStatus.NEED_WRAP;
                    return handshakeStatus;
                case 5:
                    if (this.handshakeSink.available() > 0) {
                        handshakeStatus = HandshakeStatus.NEED_WRAP;
                        return handshakeStatus;
                    }
                    handshakeStatus = HandshakeStatus.NEED_UNWRAP;
                    return handshakeStatus;
                case 6:
                    if (getUseClientMode()) {
                        handshakeStatus = HandshakeStatus.NEED_WRAP;
                        return handshakeStatus;
                    }
                    handshakeStatus = HandshakeStatus.NEED_UNWRAP;
                    return handshakeStatus;
                default:
                    throw new IllegalStateException("Unexpected engine state: " + this.engineState);
            }
        }
    }

    public boolean getNeedClientAuth() {
        return this.sslParameters.getNeedClientAuth();
    }

    public SSLSession getSession() {
        if (this.sslSession == null) {
            return SSLNullSession.getNullSession();
        }
        return this.sslSession;
    }

    public String[] getSupportedCipherSuites() {
        return NativeCrypto.getSupportedCipherSuites();
    }

    public String[] getSupportedProtocols() {
        return NativeCrypto.getSupportedProtocols();
    }

    public boolean getUseClientMode() {
        return this.sslParameters.getUseClientMode();
    }

    public boolean getWantClientAuth() {
        return this.sslParameters.getWantClientAuth();
    }

    public boolean isInboundDone() {
        boolean z = true;
        if (this.sslNativePointer == 0) {
            synchronized (this.stateLock) {
                if (!(this.engineState == EngineState.CLOSED || this.engineState == EngineState.CLOSED_INBOUND)) {
                    z = false;
                }
            }
            return z;
        }
        if ((NativeCrypto.SSL_get_shutdown(this.sslNativePointer) & 2) == 0) {
            z = false;
        }
        return z;
    }

    public boolean isOutboundDone() {
        boolean z = true;
        if (this.sslNativePointer == 0) {
            synchronized (this.stateLock) {
                if (!(this.engineState == EngineState.CLOSED || this.engineState == EngineState.CLOSED_OUTBOUND)) {
                    z = false;
                }
            }
            return z;
        }
        if ((NativeCrypto.SSL_get_shutdown(this.sslNativePointer) & 1) == 0) {
            z = false;
        }
        return z;
    }

    public void setEnabledCipherSuites(String[] suites) {
        this.sslParameters.setEnabledCipherSuites(suites);
    }

    public void setEnabledProtocols(String[] protocols) {
        this.sslParameters.setEnabledProtocols(protocols);
    }

    public void setEnableSessionCreation(boolean flag) {
        this.sslParameters.setEnableSessionCreation(flag);
    }

    public void setNeedClientAuth(boolean need) {
        this.sslParameters.setNeedClientAuth(need);
    }

    public void setUseClientMode(boolean mode) {
        synchronized (this.stateLock) {
            if (this.engineState == EngineState.MODE_SET || this.engineState == EngineState.NEW) {
                this.engineState = EngineState.MODE_SET;
            } else {
                throw new IllegalArgumentException("Can not change mode after handshake: engineState == " + this.engineState);
            }
        }
        this.sslParameters.setUseClientMode(mode);
    }

    public void setWantClientAuth(boolean want) {
        this.sslParameters.setWantClientAuth(want);
    }

    private static void checkIndex(int length, int offset, int count) {
        if (offset < 0) {
            throw new IndexOutOfBoundsException("offset < 0");
        } else if (count < 0) {
            throw new IndexOutOfBoundsException("count < 0");
        } else if (offset > length) {
            throw new IndexOutOfBoundsException("offset > length");
        } else if (offset > length - count) {
            throw new IndexOutOfBoundsException("offset + count > length");
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public SSLEngineResult unwrap(ByteBuffer src, ByteBuffer[] dsts, int offset, int length) throws SSLException {
        if (src == null) {
            throw new IllegalArgumentException("src == null");
        } else if (dsts == null) {
            throw new IllegalArgumentException("dsts == null");
        } else {
            checkIndex(dsts.length, offset, length);
            int dstRemaining = 0;
            int i = 0;
            while (i < dsts.length) {
                ByteBuffer dst = dsts[i];
                if (dst == null) {
                    throw new IllegalArgumentException("one of the dst == null");
                } else if (dst.isReadOnly()) {
                    throw new ReadOnlyBufferException();
                } else {
                    if (i >= offset && i < offset + length) {
                        dstRemaining += dst.remaining();
                    }
                    i++;
                }
            }
            synchronized (this.stateLock) {
                if (this.engineState == EngineState.CLOSED || this.engineState == EngineState.CLOSED_INBOUND) {
                    SSLEngineResult sSLEngineResult = new SSLEngineResult(Status.CLOSED, getHandshakeStatus(), 0, 0);
                    return sSLEngineResult;
                } else if (this.engineState == EngineState.NEW || this.engineState == EngineState.MODE_SET) {
                    beginHandshake();
                }
            }
        }
    }

    private ByteBuffer getNextAvailableByteBuffer(ByteBuffer[] buffers, int offset, int length) {
        for (int i = offset; i < length; i++) {
            if (buffers[i].remaining() > 0) {
                return buffers[i];
            }
        }
        return null;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public SSLEngineResult wrap(ByteBuffer[] srcs, int offset, int length, ByteBuffer dst) throws SSLException {
        if (srcs == null) {
            throw new IllegalArgumentException("srcs == null");
        } else if (dst == null) {
            throw new IllegalArgumentException("dst == null");
        } else if (dst.isReadOnly()) {
            throw new ReadOnlyBufferException();
        } else {
            for (ByteBuffer src : srcs) {
                if (src == null) {
                    throw new IllegalArgumentException("one of the src == null");
                }
            }
            checkIndex(srcs.length, offset, length);
            if (dst.remaining() < NativeConstants.SSL3_RT_MAX_PACKET_SIZE) {
                return new SSLEngineResult(Status.BUFFER_OVERFLOW, getHandshakeStatus(), 0, 0);
            }
            synchronized (this.stateLock) {
                if (this.engineState == EngineState.CLOSED || this.engineState == EngineState.CLOSED_OUTBOUND) {
                    return new SSLEngineResult(Status.CLOSED, getHandshakeStatus(), 0, 0);
                } else if (this.engineState == EngineState.NEW || this.engineState == EngineState.MODE_SET) {
                    beginHandshake();
                }
            }
        }
    }

    private static int writeSinkToByteBuffer(OpenSSLBIOSink sink, ByteBuffer dst) {
        int toWrite = Math.min(sink.available(), dst.remaining());
        dst.put(sink.toByteArray(), sink.position(), toWrite);
        sink.skip((long) toWrite);
        return toWrite;
    }

    public int clientPSKKeyRequested(String identityHint, byte[] identity, byte[] key) {
        return this.sslParameters.clientPSKKeyRequested(identityHint, identity, key, this);
    }

    public int serverPSKKeyRequested(String identityHint, String identity, byte[] key) {
        return this.sslParameters.serverPSKKeyRequested(identityHint, identity, key, this);
    }

    public void onSSLStateChange(long sslSessionNativePtr, int type, int val) {
        synchronized (this.stateLock) {
            switch (type) {
                case 16:
                    this.engineState = EngineState.HANDSHAKE_STARTED;
                    break;
                case 32:
                    if (this.engineState == EngineState.HANDSHAKE_STARTED || this.engineState == EngineState.READY_HANDSHAKE_CUT_THROUGH) {
                        this.engineState = EngineState.HANDSHAKE_COMPLETED;
                        break;
                    }
                    throw new IllegalStateException("Completed handshake while in mode " + this.engineState);
            }
        }
    }

    public void verifyCertificateChain(long sslSessionNativePtr, long[] certRefs, String authMethod) throws CertificateException {
        try {
            X509TrustManager x509tm = this.sslParameters.getX509TrustManager();
            if (x509tm == null) {
                throw new CertificateException("No X.509 TrustManager");
            }
            if (certRefs != null) {
                if (certRefs.length != 0) {
                    X509Certificate[] peerCertChain = new OpenSSLX509Certificate[certRefs.length];
                    for (int i = 0; i < certRefs.length; i++) {
                        peerCertChain[i] = new OpenSSLX509Certificate(certRefs[i]);
                    }
                    this.handshakeSession = new OpenSSLSessionImpl(sslSessionNativePtr, null, peerCertChain, getPeerHost(), getPeerPort(), null);
                    if (this.sslParameters.getUseClientMode()) {
                        Platform.checkServerTrusted(x509tm, peerCertChain, authMethod, this);
                    } else {
                        Platform.checkClientTrusted(x509tm, peerCertChain, peerCertChain[0].getPublicKey().getAlgorithm(), this);
                    }
                    this.handshakeSession = null;
                    return;
                }
            }
            throw new SSLException("Peer sent no certificate");
        } catch (CertificateException e) {
            throw e;
        } catch (Exception e2) {
            throw new CertificateException(e2);
        } catch (Throwable th) {
            this.handshakeSession = null;
        }
    }

    public void clientCertificateRequested(byte[] keyTypeBytes, byte[][] asn1DerEncodedPrincipals) throws CertificateEncodingException, SSLException {
        this.sslParameters.chooseClientCertificate(keyTypeBytes, asn1DerEncodedPrincipals, this.sslNativePointer, this);
    }

    private void shutdown() {
        try {
            NativeCrypto.SSL_shutdown_BIO(this.sslNativePointer, nullSource.getContext(), this.localToRemoteSink.getContext(), this);
        } catch (IOException e) {
        }
    }

    private void shutdownAndFreeSslNative() {
        try {
            shutdown();
        } finally {
            free();
        }
    }

    private void free() {
        if (this.sslNativePointer != 0) {
            NativeCrypto.SSL_free(this.sslNativePointer);
            this.sslNativePointer = 0;
        }
    }

    protected void finalize() throws Throwable {
        try {
            free();
        } finally {
            super.finalize();
        }
    }

    public SSLSession getHandshakeSession() {
        return this.handshakeSession;
    }

    public String chooseServerAlias(X509KeyManager keyManager, String keyType) {
        if (keyManager instanceof X509ExtendedKeyManager) {
            return ((X509ExtendedKeyManager) keyManager).chooseEngineServerAlias(keyType, null, this);
        }
        return keyManager.chooseServerAlias(keyType, null, null);
    }

    public String chooseClientAlias(X509KeyManager keyManager, X500Principal[] issuers, String[] keyTypes) {
        if (keyManager instanceof X509ExtendedKeyManager) {
            return ((X509ExtendedKeyManager) keyManager).chooseEngineClientAlias(keyTypes, issuers, this);
        }
        return keyManager.chooseClientAlias(keyTypes, issuers, null);
    }

    public String chooseServerPSKIdentityHint(PSKKeyManager keyManager) {
        return keyManager.chooseServerKeyIdentityHint((SSLEngine) this);
    }

    public String chooseClientPSKIdentity(PSKKeyManager keyManager, String identityHint) {
        return keyManager.chooseClientKeyIdentity(identityHint, (SSLEngine) this);
    }

    public SecretKey getPSKKey(PSKKeyManager keyManager, String identityHint, String identity) {
        return keyManager.getKey(identityHint, identity, (SSLEngine) this);
    }
}
