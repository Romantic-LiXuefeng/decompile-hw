package tmsdkobf;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import tmsdk.common.TMSDKContext;
import tmsdk.common.utils.d;
import tmsdk.common.utils.f;
import tmsdkobf.md.a;

/* compiled from: Unknown */
public class nx {
    private volatile boolean Av = false;
    private nv<String> DA;
    private nv<String> DB = new nv(5);
    private nv<String> DC = new nv(5);
    private nv<String> DD = new nv(5);
    private nv<String> DE = new nv(5);
    private nv<String> DF = new nv(5);
    private nv<String> DG = new nv(5);
    private a DH = new a(this) {
        final /* synthetic */ nx DI;

        {
            this.DI = r1;
        }

        public void eJ() {
            d.g("QQPimSecure", "OptimusReport @000");
            this.DI.fz();
        }
    };
    private Object Dy = new Object();
    private nv<String> Dz;

    private void a(final aj ajVar, final CountDownLatch countDownLatch) {
        if (ajVar != null) {
            jq.cu().a(3122, ajVar, null, 0, new lg(this) {
                final /* synthetic */ nx DI;

                public void onFinish(int i, int i2, int i3, int i4, fs fsVar) {
                    switch (oi.bV(i3)) {
                        case 0:
                            synchronized (this.DI.Dy) {
                                this.DI.DB.removeAll((Collection) ajVar.bd.get(Integer.valueOf(1)));
                                this.DI.DC.removeAll((Collection) ajVar.bd.get(Integer.valueOf(2)));
                                this.DI.DD.removeAll((Collection) ajVar.bd.get(Integer.valueOf(3)));
                                this.DI.DE.removeAll((Collection) ajVar.bd.get(Integer.valueOf(4)));
                                this.DI.DF.removeAll((Collection) ajVar.bd.get(Integer.valueOf(5)));
                                this.DI.DG.removeAll((Collection) ajVar.bd.get(Integer.valueOf(6)));
                            }
                            break;
                    }
                    countDownLatch.countDown();
                }
            }, 5000);
            return;
        }
        countDownLatch.countDown();
    }

    private void a(nv<String> nvVar, boolean z) {
        int size;
        Queue fw;
        if (z) {
            size = this.Dz.size();
            fw = this.Dz.fw();
        } else {
            size = this.DA.size();
            fw = this.DA.fw();
            if (size == 0) {
                size = this.Dz.size();
                fw = this.Dz.fw();
            }
        }
        if (size != 0) {
            StringBuilder stringBuilder = new StringBuilder();
            if (size <= 40) {
                for (String str : r1) {
                    stringBuilder.append(str + "``");
                }
            } else {
                size -= 20;
                int i = 0;
                for (String str2 : r1) {
                    if (i < 20) {
                        stringBuilder.append(str2 + "``");
                    }
                    if (i > size) {
                        stringBuilder.append(str2 + "``");
                    }
                    i++;
                }
            }
            if (z) {
                this.Dz.clear();
            } else {
                this.DA.clear();
            }
            nvVar.offer(stringBuilder.toString());
        }
    }

    private aj fA() {
        if (this.DD.size() == 0) {
            return null;
        }
        aj ajVar = new aj();
        ajVar.bc = 26;
        ajVar.bd = new HashMap();
        synchronized (this.Dy) {
            ajVar.bd.put(Integer.valueOf(1), this.DB.fx());
            ajVar.bd.put(Integer.valueOf(2), this.DC.fx());
            ajVar.bd.put(Integer.valueOf(3), this.DD.fx());
            ajVar.bd.put(Integer.valueOf(4), this.DE.fx());
            ajVar.bd.put(Integer.valueOf(5), this.DF.fx());
            ajVar.bd.put(Integer.valueOf(6), this.DG.fx());
        }
        return ajVar;
    }

    private void fz() {
        d.g("QQPimSecure", "OptimusReport sendOptimusData 00");
        if (fw.w().E().booleanValue() && this.DD.size() != 0 && f.B(TMSDKContext.getApplicaionContext()) && !this.Av) {
            this.Av = true;
            jq.ct().a(new Runnable(this) {
                final /* synthetic */ nx DI;

                {
                    this.DI = r1;
                }

                public void run() {
                    d.g("QQPimSecure", "OptimusReport sendOptimusData 01");
                    aj b = this.DI.fA();
                    if (b != null) {
                        CountDownLatch countDownLatch = new CountDownLatch(1);
                        this.DI.a(b, countDownLatch);
                        try {
                            countDownLatch.await(5000, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            d.g("QQPimSecure", "OptimusReport sendOptimusData InterruptedException!");
                        }
                    }
                    d.g("QQPimSecure", "OptimusReport sendOptimusData 10");
                    this.DI.Av = false;
                }
            }, "OptimusReport");
        }
    }

    public synchronized void a(String str, String str2, String str3, String str4, String str5, boolean z, boolean z2) {
        synchronized (this.Dy) {
            a(this.DG, z);
            this.DB.offer(str);
            this.DC.offer(str2);
            this.DD.offer(str3);
            this.DE.offer(str4);
            this.DF.offer(str5);
        }
        if (z2) {
            fz();
        }
    }

    public void cL(String str) {
        Date date = new Date();
        String replace = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(date) + "``" + str).replace("\n", "``").replace("|", ":");
        d.g("QQPimSecure", replace);
        synchronized (this.Dy) {
            if (this.Dz == null) {
                this.Dz = new nv(80);
                this.DA = new nv(80);
            }
            this.Dz.offer(replace);
        }
    }

    public void destroy() {
        md.eH().b(this.DH);
    }

    public void fy() {
        d.g("QQPimSecure", ".OptimusReport exchangeOptimusEventRecord()");
        synchronized (this.Dy) {
            if (this.Dz != null) {
                if (this.DA == null) {
                    this.DA = new nv(80);
                }
                this.DA.clear();
                this.DA.addAll(this.Dz.fw());
                return;
            }
        }
    }

    public void init() {
        md.eH().a(this.DH);
    }
}
