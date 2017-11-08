package java.nio.channels.spi;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

public abstract class AbstractSelectableChannel extends SelectableChannel {
    static final /* synthetic */ boolean -assertionsDisabled = (!AbstractSelectableChannel.class.desiredAssertionStatus());
    boolean blocking = true;
    private int keyCount = 0;
    private final Object keyLock = new Object();
    private SelectionKey[] keys = null;
    private final SelectorProvider provider;
    private final Object regLock = new Object();

    protected abstract void implCloseSelectableChannel() throws IOException;

    protected abstract void implConfigureBlocking(boolean z) throws IOException;

    public final java.nio.channels.SelectionKey register(java.nio.channels.Selector r1, int r2, java.lang.Object r3) throws java.nio.channels.ClosedChannelException {
        /* JADX: method processing error */
/*
Error: jadx.core.utils.exceptions.DecodeException: Load method exception in method: java.nio.channels.spi.AbstractSelectableChannel.register(java.nio.channels.Selector, int, java.lang.Object):java.nio.channels.SelectionKey
	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:116)
	at jadx.core.dex.nodes.ClassNode.load(ClassNode.java:249)
	at jadx.core.ProcessClass.process(ProcessClass.java:34)
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:306)
	at jadx.api.JavaClass.decompile(JavaClass.java:62)
	at jadx.api.JadxDecompiler$1.run(JadxDecompiler.java:199)
Caused by: jadx.core.utils.exceptions.DecodeException: Unknown instruction: not-int
	at jadx.core.dex.instructions.InsnDecoder.decode(InsnDecoder.java:569)
	at jadx.core.dex.instructions.InsnDecoder.process(InsnDecoder.java:56)
	at jadx.core.dex.nodes.MethodNode.load(MethodNode.java:102)
	... 5 more
*/
        /*
        // Can't load method instructions.
        */
        throw new UnsupportedOperationException("Method not decompiled: java.nio.channels.spi.AbstractSelectableChannel.register(java.nio.channels.Selector, int, java.lang.Object):java.nio.channels.SelectionKey");
    }

    protected AbstractSelectableChannel(SelectorProvider provider) {
        this.provider = provider;
    }

    public final SelectorProvider provider() {
        return this.provider;
    }

    private void addKey(SelectionKey k) {
        if (-assertionsDisabled || Thread.holdsLock(this.keyLock)) {
            int i = 0;
            if (this.keys != null && this.keyCount < this.keys.length) {
                i = 0;
                while (i < this.keys.length && this.keys[i] != null) {
                    i++;
                }
            } else if (this.keys == null) {
                this.keys = new SelectionKey[3];
            } else {
                SelectionKey[] ks = new SelectionKey[(this.keys.length * 2)];
                for (i = 0; i < this.keys.length; i++) {
                    ks[i] = this.keys[i];
                }
                this.keys = ks;
                i = this.keyCount;
            }
            this.keys[i] = k;
            this.keyCount++;
            return;
        }
        throw new AssertionError();
    }

    private SelectionKey findKey(Selector sel) {
        synchronized (this.keyLock) {
            if (this.keys == null) {
                return null;
            }
            int i = 0;
            while (i < this.keys.length) {
                if (this.keys[i] == null || this.keys[i].selector() != sel) {
                    i++;
                } else {
                    SelectionKey selectionKey = this.keys[i];
                    return selectionKey;
                }
            }
            return null;
        }
    }

    void removeKey(SelectionKey k) {
        synchronized (this.keyLock) {
            for (int i = 0; i < this.keys.length; i++) {
                if (this.keys[i] == k) {
                    this.keys[i] = null;
                    this.keyCount--;
                }
            }
            ((AbstractSelectionKey) k).invalidate();
        }
    }

    private boolean haveValidKeys() {
        synchronized (this.keyLock) {
            if (this.keyCount == 0) {
                return false;
            }
            int i = 0;
            while (i < this.keys.length) {
                if (this.keys[i] == null || !this.keys[i].isValid()) {
                    i++;
                } else {
                    return true;
                }
            }
            return false;
        }
    }

    public final boolean isRegistered() {
        boolean z = false;
        synchronized (this.keyLock) {
            if (this.keyCount != 0) {
                z = true;
            }
        }
        return z;
    }

    public final SelectionKey keyFor(Selector sel) {
        return findKey(sel);
    }

    protected final void implCloseChannel() throws IOException {
        implCloseSelectableChannel();
        synchronized (this.keyLock) {
            int count = this.keys == null ? 0 : this.keys.length;
            for (int i = 0; i < count; i++) {
                SelectionKey k = this.keys[i];
                if (k != null) {
                    k.cancel();
                }
            }
        }
    }

    public final boolean isBlocking() {
        boolean z;
        synchronized (this.regLock) {
            z = this.blocking;
        }
        return z;
    }

    public final Object blockingLock() {
        return this.regLock;
    }

    public final SelectableChannel configureBlocking(boolean block) throws IOException {
        synchronized (this.regLock) {
            if (!isOpen()) {
                throw new ClosedChannelException();
            } else if (this.blocking == block) {
                return this;
            } else {
                if (block) {
                    if (haveValidKeys()) {
                        throw new IllegalBlockingModeException();
                    }
                }
                implConfigureBlocking(block);
                this.blocking = block;
                return this;
            }
        }
    }
}
