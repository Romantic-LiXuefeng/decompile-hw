package tmsdkobf;

/* compiled from: Unknown */
public final class of extends fs {
    public int iCid = 0;
    public int iLac = 0;
    public short sMnc = (short) 0;

    public fs newInit() {
        return new of();
    }

    public void readFrom(fq fqVar) {
        this.iCid = fqVar.a(this.iCid, 0, true);
        this.iLac = fqVar.a(this.iLac, 1, true);
        this.sMnc = (short) fqVar.a(this.sMnc, 2, true);
    }

    public void writeTo(fr frVar) {
        frVar.write(this.iCid, 0);
        frVar.write(this.iLac, 1);
        frVar.a(this.sMnc, 2);
    }
}
