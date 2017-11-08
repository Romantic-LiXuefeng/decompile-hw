package java.util.concurrent;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.AbstractQueue;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import sun.misc.Unsafe;

public class LinkedTransferQueue<E> extends AbstractQueue<E> implements TransferQueue<E>, Serializable {
    private static final int ASYNC = 1;
    private static final int CHAINED_SPINS = 64;
    private static final int FRONT_SPINS = 128;
    private static final long HEAD;
    private static final boolean MP;
    private static final int NOW = 0;
    private static final long SWEEPVOTES;
    static final int SWEEP_THRESHOLD = 32;
    private static final int SYNC = 2;
    private static final long TAIL;
    private static final int TIMED = 3;
    private static final Unsafe U = Unsafe.getUnsafe();
    private static final long serialVersionUID = -3223113410248163686L;
    volatile transient Node head;
    private volatile transient int sweepVotes;
    private volatile transient Node tail;

    final class Itr implements Iterator<E> {
        private Node lastPred;
        private Node lastRet;
        private E nextItem;
        private Node nextNode;

        private void advance(Node prev) {
            Node r = this.lastRet;
            if (r == null || r.isMatched()) {
                Node b = this.lastPred;
                if (b != null && !b.isMatched()) {
                    while (true) {
                        Node s = b.next;
                        if (s != null && s != b && s.isMatched()) {
                            Node n = s.next;
                            if (n == null || n == s) {
                                break;
                            }
                            b.casNext(s, n);
                        } else {
                            break;
                        }
                    }
                }
                this.lastPred = null;
            } else {
                this.lastPred = r;
            }
            this.lastRet = prev;
            Node p = prev;
            while (true) {
                E s2 = p == null ? LinkedTransferQueue.this.head : p.next;
                if (s2 != null) {
                    if (s2 != p) {
                        E item = s2.item;
                        if (!s2.isData) {
                            if (item == null) {
                                break;
                            }
                        } else if (!(item == null || item == s2)) {
                            E itemE = item;
                            this.nextItem = item;
                            this.nextNode = s2;
                            return;
                        }
                        if (p != null) {
                            E n2 = s2.next;
                            if (n2 == null) {
                                break;
                            } else if (s2 == n2) {
                                p = null;
                            } else {
                                p.casNext(s2, n2);
                            }
                        } else {
                            p = s2;
                        }
                    } else {
                        p = null;
                    }
                } else {
                    break;
                }
            }
            this.nextNode = null;
            this.nextItem = null;
        }

        Itr() {
            advance(null);
        }

        public final boolean hasNext() {
            return this.nextNode != null;
        }

        public final E next() {
            Node p = this.nextNode;
            if (p == null) {
                throw new NoSuchElementException();
            }
            E e = this.nextItem;
            advance(p);
            return e;
        }

        public final void remove() {
            Node lastRet = this.lastRet;
            if (lastRet == null) {
                throw new IllegalStateException();
            }
            this.lastRet = null;
            if (lastRet.tryMatchData()) {
                LinkedTransferQueue.this.unsplice(this.lastPred, lastRet);
            }
        }
    }

    final class LTQSpliterator<E> implements Spliterator<E> {
        static final int MAX_BATCH = 33554432;
        int batch;
        Node current;
        boolean exhausted;

        LTQSpliterator() {
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public Spliterator<E> trySplit() {
            int b = this.batch;
            int n = b <= 0 ? 1 : b >= MAX_BATCH ? MAX_BATCH : b + 1;
            if (!this.exhausted) {
                Node p = this.current;
                if (p == null) {
                    p = LinkedTransferQueue.this.firstDataNode();
                }
                if (p.next != null) {
                    Object[] a = new Object[n];
                    int i = 0;
                    do {
                        Node e = p.item;
                        if (e != p) {
                            a[i] = e;
                            if (e != null) {
                                i++;
                            }
                        }
                        Node p2 = p.next;
                        if (p == p2) {
                            p = LinkedTransferQueue.this.firstDataNode();
                        } else {
                            p = p2;
                        }
                        if (p == null || i >= n) {
                            this.current = p;
                        }
                    } while (p.isData);
                    this.current = p;
                    if (p == null) {
                        this.exhausted = true;
                    }
                    if (i > 0) {
                        this.batch = i;
                        return Spliterators.spliterator(a, 0, i, 4368);
                    }
                }
            }
            return null;
        }

        public void forEachRemaining(Consumer<? super E> action) {
            if (action == null) {
                throw new NullPointerException();
            } else if (!this.exhausted) {
                Node p = this.current;
                if (p == null) {
                    p = LinkedTransferQueue.this.firstDataNode();
                    if (p == null) {
                        return;
                    }
                }
                this.exhausted = true;
                do {
                    Node e = p.item;
                    if (!(e == null || e == p)) {
                        action.accept(e);
                    }
                    Node p2 = p.next;
                    if (p == p2) {
                        p = LinkedTransferQueue.this.firstDataNode();
                    } else {
                        p = p2;
                    }
                    if (p == null) {
                        return;
                    }
                } while (p.isData);
            }
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public boolean tryAdvance(Consumer<? super E> action) {
            if (action == null) {
                throw new NullPointerException();
            }
            if (!this.exhausted) {
                Object e;
                Node p = this.current;
                if (p == null) {
                    p = LinkedTransferQueue.this.firstDataNode();
                }
                do {
                    e = p.item;
                    if (e == p) {
                        e = null;
                    }
                    Node p2 = p.next;
                    if (p == p2) {
                        p = LinkedTransferQueue.this.firstDataNode();
                    } else {
                        p = p2;
                    }
                    if (e != null || p == null) {
                        this.current = p;
                    }
                } while (p.isData);
                this.current = p;
                if (p == null) {
                    this.exhausted = true;
                }
                if (e != null) {
                    action.accept(e);
                    return true;
                }
            }
            return false;
        }

        public long estimateSize() {
            return Long.MAX_VALUE;
        }

        public int characteristics() {
            return 4368;
        }
    }

    static final class Node {
        private static final long ITEM;
        private static final long NEXT;
        private static final Unsafe U = Unsafe.getUnsafe();
        private static final long WAITER;
        private static final long serialVersionUID = -3375979862319811754L;
        final boolean isData;
        volatile Object item;
        volatile Node next;
        volatile Thread waiter;

        final boolean casNext(Node cmp, Node val) {
            return U.compareAndSwapObject(this, NEXT, cmp, val);
        }

        final boolean casItem(Object cmp, Object val) {
            return U.compareAndSwapObject(this, ITEM, cmp, val);
        }

        Node(Object item, boolean isData) {
            U.putObject(this, ITEM, item);
            this.isData = isData;
        }

        final void forgetNext() {
            U.putObject(this, NEXT, this);
        }

        final void forgetContents() {
            U.putObject(this, ITEM, this);
            U.putObject(this, WAITER, null);
        }

        final boolean isMatched() {
            Node x = this.item;
            if (x == this) {
                return true;
            }
            if ((x == null) == this.isData) {
                return true;
            }
            return false;
        }

        final boolean isUnmatchedRequest() {
            return !this.isData && this.item == null;
        }

        final boolean cannotPrecede(boolean haveData) {
            boolean d = this.isData;
            if (d != haveData) {
                Node x = this.item;
                if (x != this) {
                    if ((x != null) == d) {
                        return true;
                    }
                }
            }
            return false;
        }

        final boolean tryMatchData() {
            Node x = this.item;
            if (x == null || x == this || !casItem(x, null)) {
                return false;
            }
            LockSupport.unpark(this.waiter);
            return true;
        }

        static {
            try {
                ITEM = U.objectFieldOffset(Node.class.getDeclaredField("item"));
                NEXT = U.objectFieldOffset(Node.class.getDeclaredField("next"));
                WAITER = U.objectFieldOffset(Node.class.getDeclaredField("waiter"));
            } catch (ReflectiveOperationException e) {
                throw new Error(e);
            }
        }
    }

    static {
        boolean z = true;
        if (Runtime.getRuntime().availableProcessors() <= 1) {
            z = false;
        }
        MP = z;
        try {
            HEAD = U.objectFieldOffset(LinkedTransferQueue.class.getDeclaredField("head"));
            TAIL = U.objectFieldOffset(LinkedTransferQueue.class.getDeclaredField("tail"));
            SWEEPVOTES = U.objectFieldOffset(LinkedTransferQueue.class.getDeclaredField("sweepVotes"));
            Class<?> ensureLoaded = LockSupport.class;
        } catch (ReflectiveOperationException e) {
            throw new Error(e);
        }
    }

    private boolean casTail(Node cmp, Node val) {
        return U.compareAndSwapObject(this, TAIL, cmp, val);
    }

    private boolean casHead(Node cmp, Node val) {
        return U.compareAndSwapObject(this, HEAD, cmp, val);
    }

    private boolean casSweepVotes(int cmp, int val) {
        return U.compareAndSwapInt(this, SWEEPVOTES, cmp, val);
    }

    private E xfer(E e, boolean haveData, int how, long nanos) {
        if (haveData && e == null) {
            throw new NullPointerException();
        }
        Node node = null;
        Node pred;
        do {
            Node h = this.head;
            E p = h;
            while (p != null) {
                boolean isData = p.isData;
                E item = p.item;
                if (item != p) {
                    if ((item != null) == isData) {
                        if (isData == haveData) {
                            break;
                        } else if (p.casItem(item, e)) {
                            Node q = p;
                            while (q != h) {
                                Node n = q.next;
                                if (this.head == h) {
                                    if (n == null) {
                                        n = q;
                                    }
                                    if (casHead(h, n)) {
                                        h.forgetNext();
                                        break;
                                    }
                                }
                                h = this.head;
                                if (h == null) {
                                    break;
                                }
                                q = h.next;
                                if (q == null || !q.isMatched()) {
                                    break;
                                }
                            }
                            LockSupport.unpark(p.waiter);
                            E itemE = item;
                            return item;
                        }
                    }
                }
                E n2 = p.next;
                if (p != n2) {
                    p = n2;
                } else {
                    h = this.head;
                    p = h;
                }
            }
            if (how == 0) {
                break;
            }
            if (node == null) {
                node = new Node(e, haveData);
            }
            pred = tryAppend(node, haveData);
        } while (pred == null);
        if (how != 1) {
            boolean z;
            if (how == 3) {
                z = true;
            } else {
                z = false;
            }
            return awaitMatch(node, pred, e, z, nanos);
        }
        return e;
    }

    private Node tryAppend(Node s, boolean haveData) {
        Node t = this.tail;
        Node node = t;
        while (true) {
            if (node == null) {
                node = this.head;
                if (node == null) {
                    if (casHead(null, s)) {
                        return s;
                    }
                }
            }
            if (!node.cannotPrecede(haveData)) {
                Node n = node.next;
                if (n == null) {
                    if (node.casNext(null, s)) {
                        break;
                    }
                    node = node.next;
                } else {
                    if (node != t) {
                        Node u = this.tail;
                        if (t != u) {
                            t = u;
                            node = u;
                        }
                    }
                    node = node != n ? n : null;
                }
            } else {
                return null;
            }
        }
        if (node != t) {
            while (true) {
                if (this.tail != t || !casTail(t, s)) {
                    t = this.tail;
                    if (t == null) {
                        break;
                    }
                    s = t.next;
                    if (s == null) {
                        break;
                    }
                    s = s.next;
                    if (s == null || s == t) {
                        break;
                    }
                } else {
                    break;
                }
            }
        }
        return node;
    }

    private E awaitMatch(Node s, Node pred, E e, boolean timed, long nanos) {
        long deadline = timed ? System.nanoTime() + nanos : 0;
        Thread w = Thread.currentThread();
        int spins = -1;
        ThreadLocalRandom threadLocalRandom = null;
        while (true) {
            E item = s.item;
            if (item != e) {
                s.forgetContents();
                E itemE = item;
                return item;
            } else if (w.isInterrupted() || (timed && nanos <= 0)) {
                unsplice(pred, s);
                if (s.casItem(e, s)) {
                    return e;
                }
            } else if (spins < 0) {
                spins = spinsFor(pred, s.isData);
                if (spins > 0) {
                    threadLocalRandom = ThreadLocalRandom.current();
                }
            } else if (spins > 0) {
                spins--;
                if (threadLocalRandom.nextInt(64) == 0) {
                    Thread.yield();
                }
            } else if (s.waiter == null) {
                s.waiter = w;
            } else if (timed) {
                nanos = deadline - System.nanoTime();
                if (nanos > 0) {
                    LockSupport.parkNanos(this, nanos);
                }
            } else {
                LockSupport.park(this);
            }
        }
    }

    private static int spinsFor(Node pred, boolean haveData) {
        if (MP && pred != null) {
            if (pred.isData != haveData) {
                return 192;
            }
            if (pred.isMatched()) {
                return 128;
            }
            if (pred.waiter == null) {
                return 64;
            }
        }
        return 0;
    }

    final Node succ(Node p) {
        Node next = p.next;
        return p == next ? this.head : next;
    }

    final Node firstDataNode() {
        loop0:
        while (true) {
            Node p = this.head;
            while (p != null) {
                Node item = p.item;
                if (!p.isData) {
                    if (item == null) {
                        break loop0;
                    }
                } else if (!(item == null || item == p)) {
                    return p;
                }
                Node p2 = p.next;
                if (p != p2) {
                    p = p2;
                }
            }
            break loop0;
        }
        return null;
    }

    private int countOfMode(boolean data) {
        int count;
        loop0:
        while (true) {
            count = 0;
            Node p = this.head;
            while (p != null) {
                if (!p.isMatched()) {
                    if (p.isData == data) {
                        count++;
                        if (count == Integer.MAX_VALUE) {
                            break loop0;
                        }
                    }
                    return 0;
                }
                Node p2 = p.next;
                if (p != p2) {
                    p = p2;
                }
            }
            break loop0;
        }
        return count;
    }

    public String toString() {
        Object[] objArr = null;
        loop0:
        while (true) {
            int charLength = 0;
            Node p = this.head;
            int size = 0;
            while (p != null) {
                int size2;
                Node item = p.item;
                if (p.isData) {
                    if (item == null || item == p) {
                        size2 = size;
                    } else {
                        if (objArr == null) {
                            objArr = new String[4];
                        } else if (size == objArr.length) {
                            String[] a = (String[]) Arrays.copyOf(objArr, size * 2);
                        }
                        String s = item.toString();
                        size2 = size + 1;
                        objArr[size] = s;
                        charLength += s.length();
                    }
                } else if (item == null) {
                    break loop0;
                } else {
                    size2 = size;
                }
                Node p2 = p.next;
                if (p != p2) {
                    p = p2;
                    size = size2;
                }
            }
            break loop0;
        }
        if (size == 0) {
            return "[]";
        }
        return Helpers.toString(objArr, size, charLength);
    }

    private Object[] toArrayInternal(Object[] a) {
        Object[] x = a;
        loop0:
        while (true) {
            Node p = this.head;
            int size = 0;
            while (p != null) {
                int size2;
                Node item = p.item;
                if (p.isData) {
                    if (item == null || item == p) {
                        size2 = size;
                    } else {
                        if (x == null) {
                            x = new Object[4];
                        } else if (size == x.length) {
                            x = Arrays.copyOf(x, (size + 4) * 2);
                        }
                        size2 = size + 1;
                        x[size] = item;
                    }
                } else if (item == null) {
                    break loop0;
                } else {
                    size2 = size;
                }
                Node p2 = p.next;
                if (p != p2) {
                    p = p2;
                    size = size2;
                }
            }
            break loop0;
        }
        if (x == null) {
            return new Object[0];
        }
        if (a == null || size > a.length) {
            if (size != x.length) {
                x = Arrays.copyOf(x, size);
            }
            return x;
        }
        if (a != x) {
            System.arraycopy(x, 0, a, 0, size);
        }
        if (size < a.length) {
            a[size] = null;
        }
        return a;
    }

    public Object[] toArray() {
        return toArrayInternal(null);
    }

    public <T> T[] toArray(T[] a) {
        if (a != null) {
            return toArrayInternal(a);
        }
        throw new NullPointerException();
    }

    public Spliterator<E> spliterator() {
        return new LTQSpliterator();
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    final void unsplice(Node pred, Node s) {
        s.waiter = null;
        if (!(pred == null || pred == s || pred.next != s)) {
            Node n = s.next;
            if (n == null || (n != s && pred.casNext(s, n) && pred.isMatched())) {
                while (true) {
                    Node h = this.head;
                    if (h != pred && h != s && h != null) {
                        if (!h.isMatched()) {
                            break;
                        }
                        Node hn = h.next;
                        if (hn != null) {
                            if (hn != h && casHead(h, hn)) {
                                h.forgetNext();
                            }
                        } else {
                            return;
                        }
                    }
                }
            }
        }
    }

    private void sweep() {
        Node p = this.head;
        while (p != null) {
            Node s = p.next;
            if (s == null) {
                return;
            }
            if (s.isMatched()) {
                Node n = s.next;
                if (n != null) {
                    if (s == n) {
                        p = this.head;
                    } else {
                        p.casNext(s, n);
                    }
                } else {
                    return;
                }
            }
            p = s;
        }
    }

    private boolean findAndRemove(Object e) {
        if (e != null) {
            Node pred = null;
            Node p = this.head;
            while (p != null) {
                Node item = p.item;
                if (!p.isData) {
                    if (item == null) {
                        break;
                    }
                } else if (item != null && item != p && e.equals(item) && p.tryMatchData()) {
                    unsplice(pred, p);
                    return true;
                }
                pred = p;
                p = p.next;
                if (p == pred) {
                    pred = null;
                    p = this.head;
                }
            }
        }
        return false;
    }

    public LinkedTransferQueue(Collection<? extends E> c) {
        this();
        addAll(c);
    }

    public void put(E e) {
        xfer(e, true, 1, 0);
    }

    public boolean offer(E e, long timeout, TimeUnit unit) {
        xfer(e, true, 1, 0);
        return true;
    }

    public boolean offer(E e) {
        xfer(e, true, 1, 0);
        return true;
    }

    public boolean add(E e) {
        xfer(e, true, 1, 0);
        return true;
    }

    public boolean tryTransfer(E e) {
        return xfer(e, true, 0, 0) == null;
    }

    public void transfer(E e) throws InterruptedException {
        if (xfer(e, true, 2, 0) != null) {
            Thread.interrupted();
            throw new InterruptedException();
        }
    }

    public boolean tryTransfer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        if (xfer(e, true, 3, unit.toNanos(timeout)) == null) {
            return true;
        }
        if (!Thread.interrupted()) {
            return false;
        }
        throw new InterruptedException();
    }

    public E take() throws InterruptedException {
        E e = xfer(null, false, 2, 0);
        if (e != null) {
            return e;
        }
        Thread.interrupted();
        throw new InterruptedException();
    }

    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        E e = xfer(null, false, 3, unit.toNanos(timeout));
        if (e != null || !Thread.interrupted()) {
            return e;
        }
        throw new InterruptedException();
    }

    public E poll() {
        return xfer(null, false, 0, 0);
    }

    public int drainTo(Collection<? super E> c) {
        if (c == null) {
            throw new NullPointerException();
        } else if (c == this) {
            throw new IllegalArgumentException();
        } else {
            int n = 0;
            while (true) {
                E e = poll();
                if (e == null) {
                    return n;
                }
                c.add(e);
                n++;
            }
        }
    }

    public int drainTo(Collection<? super E> c, int maxElements) {
        if (c == null) {
            throw new NullPointerException();
        } else if (c == this) {
            throw new IllegalArgumentException();
        } else {
            int n = 0;
            while (n < maxElements) {
                E e = poll();
                if (e == null) {
                    break;
                }
                c.add(e);
                n++;
            }
            return n;
        }
    }

    public Iterator<E> iterator() {
        return new Itr();
    }

    public E peek() {
        loop0:
        while (true) {
            E p = this.head;
            while (p != null) {
                E item = p.item;
                if (!p.isData) {
                    if (item == null) {
                        break loop0;
                    }
                } else if (!(item == null || item == p)) {
                    E e = item;
                    return item;
                }
                E p2 = p.next;
                if (p != p2) {
                    p = p2;
                }
            }
            break loop0;
        }
        return null;
    }

    public boolean isEmpty() {
        return firstDataNode() == null;
    }

    public boolean hasWaitingConsumer() {
        loop0:
        while (true) {
            Node p = this.head;
            while (p != null) {
                Node item = p.item;
                if (p.isData) {
                    if (!(item == null || item == p)) {
                        break loop0;
                    }
                } else if (item == null) {
                    return true;
                }
                Node p2 = p.next;
                if (p != p2) {
                    p = p2;
                }
            }
            break loop0;
        }
        return false;
    }

    public int size() {
        return countOfMode(true);
    }

    public int getWaitingConsumerCount() {
        return countOfMode(false);
    }

    public boolean remove(Object o) {
        return findAndRemove(o);
    }

    public boolean contains(Object o) {
        if (o != null) {
            Node p = this.head;
            while (p != null) {
                Node item = p.item;
                if (p.isData) {
                    if (!(item == null || item == p || !o.equals(item))) {
                        return true;
                    }
                } else if (item == null) {
                    break;
                }
                p = succ(p);
            }
        }
        return false;
    }

    public int remainingCapacity() {
        return Integer.MAX_VALUE;
    }

    private void writeObject(ObjectOutputStream s) throws IOException {
        s.defaultWriteObject();
        for (E e : this) {
            s.writeObject(e);
        }
        s.writeObject(null);
    }

    private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
        s.defaultReadObject();
        while (true) {
            E item = s.readObject();
            if (item != null) {
                offer(item);
            } else {
                return;
            }
        }
    }
}
