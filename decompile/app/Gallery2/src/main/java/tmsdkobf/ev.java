package tmsdkobf;

/* compiled from: Unknown */
public final class ev extends fs {
    static dq lj = new dq();
    public String cC = "";
    public String cE = "";
    public String cF = "";
    public String cG = "";
    public String cH = "";
    public String cM = "";
    public short cP = (short) 0;
    public int fL = 0;
    public int gH = 0;
    public String imsi = "";
    public String ja = "";
    public String je = "";
    public String jh = "";
    public int language = 0;
    public int le = 0;
    public dq lf = null;
    public int lg = 0;
    public double lh = 0.0d;
    public double li = 0.0d;
    public int product = 0;
    public String r = "";
    public int u = 0;

    public fs newInit() {
        return new ev();
    }

    public void readFrom(fq fqVar) {
        this.cC = fqVar.a(0, true);
        this.cE = fqVar.a(1, false);
        this.cF = fqVar.a(2, false);
        this.ja = fqVar.a(3, false);
        this.cG = fqVar.a(4, false);
        this.cH = fqVar.a(5, false);
        this.cM = fqVar.a(6, false);
        this.le = fqVar.a(this.le, 7, false);
        this.product = fqVar.a(this.product, 8, false);
        this.lf = (dq) fqVar.a(lj, 9, false);
        this.r = fqVar.a(10, false);
        this.imsi = fqVar.a(11, false);
        this.fL = fqVar.a(this.fL, 12, false);
        this.lg = fqVar.a(this.lg, 13, false);
        this.gH = fqVar.a(this.gH, 14, false);
        this.u = fqVar.a(this.u, 15, false);
        this.je = fqVar.a(16, false);
        this.cP = (short) fqVar.a(this.cP, 17, false);
        this.lh = fqVar.a(this.lh, 18, false);
        this.li = fqVar.a(this.li, 19, false);
        this.jh = fqVar.a(20, false);
        this.language = fqVar.a(this.language, 21, false);
    }

    public void writeTo(fr frVar) {
        frVar.a(this.cC, 0);
        if (this.cE != null) {
            frVar.a(this.cE, 1);
        }
        if (this.cF != null) {
            frVar.a(this.cF, 2);
        }
        if (this.ja != null) {
            frVar.a(this.ja, 3);
        }
        if (this.cG != null) {
            frVar.a(this.cG, 4);
        }
        if (this.cH != null) {
            frVar.a(this.cH, 5);
        }
        if (this.cM != null) {
            frVar.a(this.cM, 6);
        }
        if (this.le != 0) {
            frVar.write(this.le, 7);
        }
        if (this.product != 0) {
            frVar.write(this.product, 8);
        }
        if (this.lf != null) {
            frVar.a(this.lf, 9);
        }
        if (this.r != null) {
            frVar.a(this.r, 10);
        }
        if (this.imsi != null) {
            frVar.a(this.imsi, 11);
        }
        if (this.fL != 0) {
            frVar.write(this.fL, 12);
        }
        if (this.lg != 0) {
            frVar.write(this.lg, 13);
        }
        if (this.gH != 0) {
            frVar.write(this.gH, 14);
        }
        if (this.u != 0) {
            frVar.write(this.u, 15);
        }
        if (this.je != null) {
            frVar.a(this.je, 16);
        }
        if (this.cP != (short) 0) {
            frVar.a(this.cP, 17);
        }
        if (this.lh != 0.0d) {
            frVar.a(this.lh, 18);
        }
        if (this.li != 0.0d) {
            frVar.a(this.li, 19);
        }
        if (this.jh != null) {
            frVar.a(this.jh, 20);
        }
        if (this.language != 0) {
            frVar.write(this.language, 21);
        }
    }
}
