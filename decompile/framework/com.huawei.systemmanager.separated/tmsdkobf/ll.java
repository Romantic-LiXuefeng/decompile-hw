package tmsdkobf;

import java.util.concurrent.atomic.AtomicInteger;

/* compiled from: Unknown */
public class ll {
    boolean wE = false;
    AtomicInteger wF = new AtomicInteger();

    public boolean dC() {
        return this.wE;
    }

    public void setState(int i) {
        this.wF.set(i);
    }
}
