package tmsdkobf;

/* compiled from: Unknown */
public final class oe extends fs {
    public int iCid = 0;
    public int iLac = 0;
    public short sBsss = (short) 0;
    public short sNetworkType = (short) 0;

    public fs newInit() {
        return new oe();
    }

    public void readFrom(fq fqVar) {
        this.sNetworkType = (short) fqVar.a(this.sNetworkType, 0, true);
        this.iCid = fqVar.a(this.iCid, 1, true);
        this.iLac = fqVar.a(this.iLac, 2, true);
        this.sBsss = (short) fqVar.a(this.sBsss, 3, true);
    }

    public void writeTo(fr frVar) {
        frVar.a(this.sNetworkType, 0);
        frVar.write(this.iCid, 1);
        frVar.write(this.iLac, 2);
        frVar.a(this.sBsss, 3);
    }
}
