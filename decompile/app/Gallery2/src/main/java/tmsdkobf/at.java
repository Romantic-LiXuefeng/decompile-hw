package tmsdkobf;

/* compiled from: Unknown */
public final class at extends fs {
    public int ap = 0;
    public int bC = 0;
    public String imsi = "";

    public fs newInit() {
        return new at();
    }

    public void readFrom(fq fqVar) {
        this.ap = fqVar.a(this.ap, 0, true);
        this.imsi = fqVar.a(1, false);
        this.bC = fqVar.a(this.bC, 2, false);
    }

    public void writeTo(fr frVar) {
        frVar.write(this.ap, 0);
        if (this.imsi != null) {
            frVar.a(this.imsi, 1);
        }
        if (this.bC != 0) {
            frVar.write(this.bC, 2);
        }
    }
}
