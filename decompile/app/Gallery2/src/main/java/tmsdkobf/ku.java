package tmsdkobf;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import tmsdk.common.CallerIdent;
import tmsdk.common.utils.d;
import tmsdkobf.kp.a;
import tmsdkobf.kp.b;
import tmsdkobf.kp.c;

/* compiled from: Unknown */
public class ku implements kp {
    private static Object lock = new Object();
    private static ku wi;
    Handler handler;
    private HashSet<ks> rq;
    private ConcurrentLinkedQueue<a> wh;
    b wj;
    qh<Integer, al> wk;

    private ku() {
        this.wh = new ConcurrentLinkedQueue();
        this.wj = null;
        this.wk = new qh(20);
        this.handler = null;
        this.rq = new HashSet();
        this.handler = new Handler(this, Looper.getMainLooper()) {
            final /* synthetic */ ku wl;

            public void handleMessage(Message message) {
                switch (message.what) {
                    case 0:
                        this.wl.handler.removeMessages(0);
                        if (this.wl.wj != null && this.wl.wh.size() > 0) {
                            ArrayList arrayList = new ArrayList();
                            Iterator it = this.wl.wh.iterator();
                            while (it.hasNext()) {
                                a aVar = (a) it.next();
                                it.remove();
                                if (aVar != null) {
                                    arrayList.add(aVar);
                                }
                            }
                            this.wl.wj.q(this.wl.r(arrayList));
                            return;
                        }
                        return;
                    default:
                        return;
                }
            }
        };
    }

    private void a(int i, int i2, boolean z, int i3, long j, String str, byte[] bArr, short s) {
        al alVar = new al();
        switch (i) {
            case 1:
                alVar.bm = i2;
                alVar.valueType = i;
                alVar.i = i3;
                break;
            case 2:
                alVar.bm = i2;
                alVar.valueType = i;
                alVar.bn = j;
                break;
            case 3:
                alVar.bm = i2;
                alVar.valueType = i;
                alVar.bo = str;
                break;
            case 4:
                alVar.bm = i2;
                alVar.valueType = i;
                alVar.bp = bArr;
                break;
            case 5:
                alVar.bm = i2;
                alVar.valueType = i;
                alVar.bq = z;
                break;
            case 6:
                alVar.bm = i2;
                alVar.valueType = i;
                alVar.br = (short) s;
                break;
            default:
                return;
        }
        b(alVar);
        a aVar = new a();
        aVar.vV = alVar;
        d.e("KeyValueProfileService", "[profile上报][" + alVar.bm + "]");
        this.wh.add(aVar);
        this.handler.sendEmptyMessageDelayed(0, 1000);
    }

    public static void a(String str, al alVar, String str2) {
        if (alVar != null) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("keyid|" + alVar.bm);
            switch (alVar.valueType) {
                case 1:
                    stringBuilder.append("|int|" + alVar.i);
                    break;
                case 2:
                    stringBuilder.append("|long|" + alVar.bn);
                    break;
                case 3:
                    stringBuilder.append("|str|" + alVar.bo);
                    break;
                case 4:
                    stringBuilder.append("|byte[]|" + alVar.bp.length);
                    break;
                case 5:
                    stringBuilder.append("|bool|" + alVar.bq);
                    break;
                case 6:
                    stringBuilder.append("|short|" + alVar.br);
                    break;
                default:
                    return;
            }
            if (str2 != null) {
                stringBuilder.append(str2);
            }
            hu.h(str, stringBuilder.toString());
        }
    }

    private void b(al alVar) {
        this.wk.put(Integer.valueOf(alVar.bm), alVar);
    }

    public static ku dq() {
        if (wi == null) {
            synchronized (lock) {
                if (wi == null) {
                    wi = new ku();
                }
            }
        }
        return wi;
    }

    private ArrayList<a> r(ArrayList<a> arrayList) {
        if (arrayList == null || arrayList.size() <= 1) {
            return arrayList;
        }
        ArrayList arrayList2 = new ArrayList();
        Iterator it = arrayList.iterator();
        while (it.hasNext()) {
            a aVar = (a) it.next();
            if (aVar != null) {
                arrayList2.add(aVar);
            }
        }
        arrayList.clear();
        Collections.sort(arrayList2, new Comparator<a>(this) {
            final /* synthetic */ ku wl;

            {
                this.wl = r1;
            }

            public int a(a aVar, a aVar2) {
                boolean z = aVar.vV != null ? aVar.vV instanceof al : false;
                boolean z2 = aVar2.vV != null ? aVar2.vV instanceof al : false;
                if (!z && !z2) {
                    return 0;
                }
                if (!z && z2) {
                    return -1;
                }
                if (z && !z2) {
                    return 1;
                }
                if (aVar.action != aVar2.action) {
                    return aVar.action - aVar2.action;
                }
                return ((al) aVar.vV).bm - ((al) aVar2.vV).bm;
            }

            public /* synthetic */ int compare(Object obj, Object obj2) {
                return a((a) obj, (a) obj2);
            }
        });
        int size = arrayList2.size() - 1;
        for (int i = 0; i < size; i++) {
            aVar = (a) arrayList2.get(i);
            a aVar2 = (a) arrayList2.get(i + 1);
            if (aVar.action == aVar2.action) {
                al alVar = (aVar.vV != null && (aVar.vV instanceof al)) ? (al) aVar.vV : null;
                al alVar2 = (aVar2.vV != null && (aVar2.vV instanceof al)) ? (al) aVar2.vV : null;
                if (alVar == null) {
                    if (alVar2 == null) {
                    }
                }
                if (alVar != null && alVar2 == null) {
                    arrayList.add(aVar);
                } else {
                    if (alVar == null) {
                        if (alVar2 != null) {
                        }
                    }
                    if (alVar.bm != alVar2.bm) {
                        arrayList.add(aVar);
                    }
                }
            } else {
                arrayList.add(aVar);
            }
        }
        if (size >= 0) {
            arrayList.add(arrayList2.get(size));
        }
        return arrayList;
    }

    public void a(hw hwVar) {
        hv.bG().a(CallerIdent.getIdent(1, 4294967296L), 4, new c(this) {
            final /* synthetic */ ku wl;

            {
                this.wl = r1;
            }

            public ArrayList<fs> dn() {
                return kt.dp().getAll();
            }
        }, hwVar, kv.dr().ds());
    }

    public void a(b bVar) {
    }

    public void a(ks ksVar) {
        this.rq.add(ksVar);
        hv.bG().a(ksVar);
    }

    public void aZ(int i) {
    }

    public void b(b bVar) {
        this.wj = bVar;
        this.handler.sendEmptyMessage(0);
    }

    public void b(ks ksVar) {
        this.rq.remove(ksVar);
        hv.bG().b(ksVar);
    }

    public void c(int i, boolean z) {
        a(5, i, z, 0, 0, null, null, (short) 0);
    }

    public boolean dk() {
        return this.wj != null;
    }

    public int dl() {
        return 4;
    }

    public void dm() {
        ArrayList all = kt.dp().getAll();
        if (all != null && all.size() > 0) {
            Iterator it = all.iterator();
            while (it.hasNext()) {
                fs fsVar = (fs) it.next();
                if (fsVar instanceof al) {
                    al alVar = (al) fsVar;
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("key|");
                    stringBuilder.append(alVar.bm);
                    stringBuilder.append("|valueType|");
                    stringBuilder.append(alVar.valueType);
                    stringBuilder.append("|value|");
                    switch (alVar.valueType) {
                        case 1:
                            stringBuilder.append(alVar.i);
                            break;
                        case 2:
                            stringBuilder.append(alVar.bn);
                            break;
                        case 3:
                            stringBuilder.append(alVar.bo);
                            break;
                        case 4:
                            stringBuilder.append(alVar.bp);
                            break;
                    }
                    d.e("KeyValueProfileService", stringBuilder.toString());
                }
            }
        }
    }

    public void i(int i, int i2) {
        a(1, i, false, i2, 0, null, null, (short) 0);
    }

    public boolean o(ArrayList<a> arrayList) {
        if (arrayList == null || arrayList.size() <= 0) {
            return false;
        }
        ArrayList arrayList2 = new ArrayList();
        boolean a = kt.dp().a(arrayList, arrayList2);
        Iterator it = arrayList.iterator();
        while (it.hasNext()) {
            a aVar = (a) it.next();
            if (!(aVar == null || aVar.vV == null || !(aVar.vV instanceof al))) {
                a("KeyValueProfileService", (al) aVar.vV, "|ret|" + a);
            }
        }
        if (a && arrayList2.size() > 0) {
            int i = 0;
            while (i < arrayList2.size()) {
                Boolean bool = (Boolean) arrayList2.get(i);
                if (bool != null && !bool.booleanValue() && arrayList.size() > i && (((a) arrayList.get(i)).vV instanceof al)) {
                    byte[] a2 = hu.a(4, 0, (al) ((a) arrayList.get(i)).vV);
                    if (a2 != null) {
                        kv.dr().bd(a2.length);
                        hv.bG().g(4, kv.dr().ds());
                    }
                }
                i++;
            }
        }
        return a;
    }

    public void onImsiChanged() {
        d.e("ImsiChecker", "KV-setFirstReport:[true]");
        kv.dr().x(true);
    }

    public void p(ArrayList<a> arrayList) {
        if (arrayList != null && arrayList.size() > 0) {
            ArrayList arrayList2;
            ArrayList arrayList3;
            ArrayList arrayList4;
            ArrayList arrayList5;
            a aVar;
            if (kv.dr().dt()) {
                kv.dr().x(false);
                Iterator it = arrayList.iterator();
                arrayList5 = null;
                while (it.hasNext()) {
                    aVar = (a) it.next();
                    if (!(aVar == null || aVar.vV == null || !(aVar.vV instanceof al))) {
                        if (arrayList5 == null) {
                            arrayList5 = new ArrayList();
                        }
                        arrayList5.add((al) aVar.vV);
                    }
                }
                arrayList2 = arrayList5;
                arrayList3 = null;
                arrayList4 = null;
            } else {
                Iterator it2 = arrayList.iterator();
                arrayList5 = null;
                ArrayList arrayList6 = null;
                while (it2.hasNext()) {
                    aVar = (a) it2.next();
                    if (!(aVar == null || aVar.vV == null || !(aVar.vV instanceof al))) {
                        al alVar = (al) aVar.vV;
                        if (kt.dp().bc(alVar.bm) <= 0) {
                            if (arrayList5 == null) {
                                arrayList5 = new ArrayList();
                            }
                            arrayList5.add(alVar);
                        } else {
                            if (arrayList6 == null) {
                                arrayList6 = new ArrayList();
                            }
                            arrayList6.add(alVar);
                        }
                        arrayList6 = arrayList6;
                        arrayList5 = arrayList5;
                    }
                }
                arrayList2 = null;
                arrayList3 = arrayList5;
                arrayList4 = arrayList6;
            }
            if (arrayList2 != null && arrayList2.size() > 0) {
                hv.bG().a(CallerIdent.getIdent(1, 4294967296L), 4, 0, arrayList2);
            }
            if (arrayList4 != null && arrayList4.size() > 0) {
                hv.bG().a(CallerIdent.getIdent(1, 4294967296L), 4, 3, arrayList4);
            }
            if (arrayList3 != null && arrayList3.size() > 0) {
                hv.bG().a(CallerIdent.getIdent(1, 4294967296L), 4, 1, arrayList3);
            }
        }
    }
}
