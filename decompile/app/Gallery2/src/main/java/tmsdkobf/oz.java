package tmsdkobf;

/* compiled from: Unknown */
public class oz {
    private static oo Fv;
    private static oo Fw;

    /* compiled from: Unknown */
    public static class a implements oo {
        private int Fx = 1;
        private Object mLock = new Object();

        public int fQ() {
            int i;
            synchronized (this.mLock) {
                i = this.Fx;
                this.Fx++;
            }
            return i;
        }
    }

    public static oo gj() {
        if (Fv == null) {
            synchronized (oz.class) {
                if (Fv == null) {
                    Fv = new a();
                }
            }
        }
        return Fv;
    }

    public static oo gk() {
        if (Fw == null) {
            synchronized (oz.class) {
                if (Fw == null) {
                    Fw = new a();
                }
            }
        }
        return Fw;
    }
}
