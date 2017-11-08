package tmsdkobf;

import android.content.Context;
import android.os.Debug;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;
import java.util.concurrent.TimeUnit;
import tmsdk.common.creator.BaseManagerC;
import tmsdk.common.utils.d;
import tmsdkobf.qg.c;

/* compiled from: Unknown */
public class ke extends BaseManagerC implements tmsdkobf.kd.a, qg {
    private static tmsdkobf.qg.a vA = new tmsdkobf.qg.a() {
        public void a(c cVar) {
            Iterator it = ke.vn.iterator();
            while (it.hasNext()) {
                ((tmsdkobf.qg.a) it.next()).a(cVar);
            }
        }

        public void a(c cVar, int i) {
            Iterator it = ke.vn.iterator();
            while (it.hasNext()) {
                ((tmsdkobf.qg.a) it.next()).a(cVar, i);
            }
        }

        public void b(c cVar) {
            Iterator it = ke.vn.iterator();
            while (it.hasNext()) {
                ((tmsdkobf.qg.a) it.next()).b(cVar);
            }
        }
    };
    private static ArrayList<tmsdkobf.qg.a> vn = new ArrayList();
    private static long vw = 0;
    private static long vx = 0;
    private boolean isActive = false;
    private Object mLock = new Object();
    private ArrayList<tmsdkobf.qg.b> vm = new ArrayList();
    protected PriorityBlockingQueue<Runnable> vo = new PriorityBlockingQueue(5);
    protected LinkedList<a> vp = new LinkedList();
    protected ArrayList<a> vq = new ArrayList();
    protected HashMap<a, Thread> vr = new HashMap();
    private int vs;
    protected kd vt = null;
    private HandlerThread vu;
    private b vv;
    private volatile boolean vy = false;
    private kh vz;

    /* compiled from: Unknown */
    class a implements Comparable<a>, Runnable {
        final /* synthetic */ ke vB;
        private c vC = new c();

        public a(ke keVar, int i, Runnable runnable, String str, long j, boolean z, Object obj) {
            this.vB = keVar;
            if (str == null || str.length() == 0) {
                str = runnable.getClass().getName();
            }
            this.vC.Jk = 1;
            this.vC.priority = i;
            this.vC.name = str;
            this.vC.dI = j;
            this.vC.Jp = runnable;
            this.vC.Jo = z;
            this.vC.Jq = obj;
            this.vC.Jl = System.currentTimeMillis();
        }

        public int a(a aVar) {
            int abs = (int) (Math.abs(System.currentTimeMillis() - this.vC.Jl) / 200);
            int i = this.vC.priority;
            if (abs > 0) {
                i += abs;
            }
            return aVar.vC.priority - i;
        }

        public /* synthetic */ int compareTo(Object obj) {
            return a((a) obj);
        }

        public c dd() {
            return this.vC;
        }

        public void run() {
            if (this.vC != null && this.vC.Jp != null) {
                this.vC.Jp.run();
            }
        }
    }

    /* compiled from: Unknown */
    class b extends Handler {
        final /* synthetic */ ke vB;

        public b(ke keVar, Looper looper) {
            this.vB = keVar;
            super(looper);
        }

        public void handleMessage(Message message) {
            int i = 0;
            switch (message.what) {
                case 1:
                    removeMessages(message.what);
                    if (this.vB.cX()) {
                        d.d("ThreadPool", "thread pool is pause");
                        long currentTimeMillis = System.currentTimeMillis();
                        if ((ke.vw <= 0 ? 1 : 0) == 0) {
                            if (Math.abs(ke.vx - currentTimeMillis) <= ke.vw) {
                                i = 1;
                            }
                            if (i == 0) {
                                d.d("ThreadPool", "thread pool is auto wakeup");
                                this.vB.cW();
                            }
                        }
                        sendEmptyMessageDelayed(1, 1000);
                        return;
                    }
                    this.vB.cU();
                    return;
                default:
                    return;
            }
        }
    }

    private int cS() {
        int availableProcessors = (Runtime.getRuntime().availableProcessors() * 4) + 2;
        return availableProcessors <= 16 ? availableProcessors : 16;
    }

    private int cT() {
        return cS() * 2;
    }

    private void cU() {
        synchronized (this.mLock) {
            if (!this.vp.isEmpty()) {
                Iterator it = this.vp.iterator();
                if (it != null && it.hasNext()) {
                    a aVar = (a) it.next();
                    it.remove();
                    cV();
                    this.vt.execute(aVar);
                    Iterator it2 = vn.iterator();
                    while (it2.hasNext()) {
                        ((tmsdkobf.qg.a) it2.next()).a(aVar.dd(), this.vt.getActiveCount());
                    }
                }
            }
            if (!this.vp.isEmpty()) {
                this.vv.sendEmptyMessage(1);
            }
        }
    }

    private void cV() {
        if (this.vt.getCorePoolSize() < this.vs) {
            this.vt.setCorePoolSize(this.vs);
            this.vt.setMaximumPoolSize(this.vs);
        }
    }

    private boolean cX() {
        return this.vy;
    }

    private void cY() {
        if (this.vz == null) {
            this.vz = new kh();
            this.vz.a(new tmsdkobf.qg.a(this) {
                final /* synthetic */ ke vB;

                {
                    this.vB = r1;
                }

                public void a(c cVar) {
                    Iterator it = ke.vn.iterator();
                    while (it.hasNext()) {
                        ((tmsdkobf.qg.a) it.next()).a(cVar);
                    }
                }

                public void a(c cVar, int i) {
                    Iterator it = ke.vn.iterator();
                    while (it.hasNext()) {
                        ((tmsdkobf.qg.a) it.next()).a(cVar, i);
                    }
                }

                public void b(c cVar) {
                    Iterator it = ke.vn.iterator();
                    while (it.hasNext()) {
                        ((tmsdkobf.qg.a) it.next()).b(cVar);
                    }
                }
            });
        }
    }

    public static tmsdkobf.qg.a cZ() {
        return vA;
    }

    public HandlerThread a(String str, int i, long j) {
        return kf.a(str, i, j);
    }

    public Thread a(Runnable runnable, String str, long j) {
        cY();
        return this.vz.a(runnable, str, j);
    }

    public void a(int i, Runnable runnable, String str, long j, boolean z, Object obj) {
        synchronized (this.mLock) {
            a aVar = new a(this, i, runnable, str, j, z, obj);
            this.vp.add(aVar);
            this.vq.add(aVar);
            this.vv.sendEmptyMessage(1);
        }
    }

    public void a(Runnable runnable) {
        synchronized (this.mLock) {
            Thread c = c(runnable);
            if (c == null) {
                b(runnable);
            } else {
                c.interrupt();
            }
        }
    }

    public void a(Runnable runnable, String str, long j, boolean z, Object obj) {
        a(5, runnable, str, j, z, obj);
    }

    public void afterExecute(Runnable runnable, Throwable th) {
        synchronized (this.mLock) {
            int i;
            Iterator it;
            a aVar = (a) runnable;
            Iterator it2 = this.vr.keySet().iterator();
            if (it2 != null) {
                while (it2.hasNext()) {
                    a aVar2 = (a) it2.next();
                    if (aVar2 != null) {
                        if (aVar2.equals(aVar)) {
                            it2.remove();
                            i = 1;
                            break;
                        }
                    }
                }
                Object obj = null;
                if (obj != null) {
                    aVar.dd().Jm = System.currentTimeMillis() - aVar.dd().Jm;
                    aVar.dd().Jn = Debug.threadCpuTimeNanos() - aVar.dd().Jn;
                    it = vn.iterator();
                    while (it.hasNext()) {
                        ((tmsdkobf.qg.a) it.next()).b(aVar.dd());
                    }
                }
            }
            i = this.vt.getActiveCount();
            int size = this.vt.getQueue().size();
            int corePoolSize = this.vt.getCorePoolSize();
            if (i == 1 && size == 0) {
                if (corePoolSize > 0) {
                    this.vs = cS();
                    this.vt.setCorePoolSize(0);
                    this.vt.setMaximumPoolSize(this.vs + 2);
                    d.d("ThreadPool", "shrink core pool size: " + this.vt.getCorePoolSize());
                }
                it = this.vm.iterator();
                while (it.hasNext()) {
                    ((tmsdkobf.qg.b) it.next()).hJ();
                }
                this.isActive = false;
            }
        }
    }

    public void b(Runnable runnable, String str, long j, boolean z, Object obj) {
        synchronized (this.mLock) {
            Object aVar = new a(this, Integer.MAX_VALUE, runnable, str, j, z, obj);
            this.vq.add(aVar);
            this.vt.execute(aVar);
            if (this.vt.getActiveCount() >= this.vs && this.vs < cT()) {
                this.vs++;
                this.vt.setCorePoolSize(this.vs);
                this.vt.setMaximumPoolSize(this.vs);
                d.d("ThreadPool", "expand urgent core pool size: " + this.vs);
            } else {
                cV();
            }
            Iterator it = vn.iterator();
            while (it.hasNext()) {
                ((tmsdkobf.qg.a) it.next()).a(aVar.dd(), this.vt.getActiveCount());
            }
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public boolean b(Runnable runnable) {
        if (runnable == null) {
            return false;
        }
        synchronized (this.mLock) {
            Iterator it = this.vq.iterator();
            if (it != null) {
                Runnable runnable2;
                while (it.hasNext()) {
                    runnable2 = (a) it.next();
                    if (runnable2 != null && runnable2.dd() != null && runnable.equals(runnable2.dd().Jp)) {
                        it.remove();
                        break;
                    }
                }
                runnable2 = null;
                if (runnable2 != null) {
                    this.vt.remove(runnable2);
                    return true;
                }
            }
        }
    }

    public void beforeExecute(Thread thread, Runnable runnable) {
        synchronized (this.mLock) {
            Iterator it = this.vq.iterator();
            if (it != null) {
                a aVar = (a) runnable;
                int i = aVar.dd().priority;
                if (i < 1) {
                    i = 1;
                } else if (i > 10) {
                    i = 10;
                }
                thread.setPriority(i);
                while (it.hasNext()) {
                    a aVar2 = (a) it.next();
                    if (aVar2 != null && aVar2.equals(aVar)) {
                        it.remove();
                        i = 1;
                        break;
                    }
                }
                Object obj = null;
                if (obj != null) {
                    Iterator it2;
                    if (!this.isActive) {
                        it2 = this.vm.iterator();
                        while (it2.hasNext()) {
                            ((tmsdkobf.qg.b) it2.next()).hI();
                        }
                    }
                    it2 = vn.iterator();
                    while (it2.hasNext()) {
                        ((tmsdkobf.qg.a) it2.next()).a(aVar.dd());
                    }
                    aVar.dd().Jm = System.currentTimeMillis();
                    aVar.dd().Jn = Debug.threadCpuTimeNanos();
                    this.vr.put(aVar, thread);
                    thread.setName(aVar.dd().name);
                    this.isActive = true;
                }
            }
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public Thread c(Runnable runnable) {
        if (runnable == null) {
            return null;
        }
        synchronized (this.mLock) {
            Iterator it = this.vr.keySet().iterator();
            if (it != null) {
                Object obj;
                while (it.hasNext()) {
                    obj = (a) it.next();
                    if (!(obj == null || obj.dd() == null)) {
                        if (runnable.equals(obj.dd().Jp)) {
                            break;
                        }
                    }
                }
                obj = null;
                if (obj != null) {
                    Thread thread = (Thread) this.vr.get(obj);
                    return thread;
                }
            }
        }
    }

    public void cW() {
        synchronized (this.mLock) {
            this.vy = false;
            vx = 0;
            vw = 0;
            d.d("ThreadPool", "wake up threa pool");
        }
    }

    public int getSingletonType() {
        return 1;
    }

    public void n(long j) {
        synchronized (this.mLock) {
            this.vy = true;
            vx = System.currentTimeMillis();
            vw = j;
            d.d("ThreadPool", "pause thread pool");
        }
    }

    public void onCreate(Context context) {
        this.vs = cS();
        this.vt = new kd(0, this.vs + 2, 3, TimeUnit.SECONDS, this.vo, new CallerRunsPolicy());
        this.vt.a(this);
        this.vu = new HandlerThread("TMS_THREAD_POOL_HANDLER");
        this.vu.start();
        this.vv = new b(this, this.vu.getLooper());
        n(2000);
    }
}
