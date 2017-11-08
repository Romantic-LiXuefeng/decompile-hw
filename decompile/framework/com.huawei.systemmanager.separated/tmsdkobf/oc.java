package tmsdkobf;

import java.util.ArrayList;

/* compiled from: Unknown */
public final class oc extends fs {
    static ArrayList<oe> DY = new ArrayList();
    static od DZ = new od();
    static oh Ea = new oh();
    public od DW = null;
    public oh DX = null;
    public int iCid = 0;
    public int iLac = 0;
    public long luLoc = 0;
    public short sBsss = (short) 0;
    public short sDataState = (short) 0;
    public short sMcc = (short) 0;
    public short sMnc = (short) 0;
    public short sNetworkType = (short) 0;
    public short sNumNeighbors = (short) 0;
    public long uTimeInSeconds = 0;
    public ArrayList<oe> vecNeighbors = null;

    static {
        DY.add(new oe());
    }

    public fs newInit() {
        return new oc();
    }

    public void readFrom(fq fqVar) {
        this.uTimeInSeconds = fqVar.a(this.uTimeInSeconds, 0, true);
        this.sNetworkType = (short) fqVar.a(this.sNetworkType, 1, true);
        this.sDataState = (short) fqVar.a(this.sDataState, 2, true);
        this.iCid = fqVar.a(this.iCid, 3, true);
        this.iLac = fqVar.a(this.iLac, 4, true);
        this.luLoc = fqVar.a(this.luLoc, 5, true);
        this.sBsss = (short) fqVar.a(this.sBsss, 6, true);
        this.sMcc = (short) fqVar.a(this.sMcc, 7, true);
        this.sMnc = (short) fqVar.a(this.sMnc, 8, true);
        this.sNumNeighbors = (short) fqVar.a(this.sNumNeighbors, 9, true);
        this.vecNeighbors = (ArrayList) fqVar.b(DY, 10, true);
        this.DW = (od) fqVar.a(DZ, 11, true);
        this.DX = (oh) fqVar.a(Ea, 12, true);
    }

    public void writeTo(fr frVar) {
        frVar.b(this.uTimeInSeconds, 0);
        frVar.a(this.sNetworkType, 1);
        frVar.a(this.sDataState, 2);
        frVar.write(this.iCid, 3);
        frVar.write(this.iLac, 4);
        frVar.b(this.luLoc, 5);
        frVar.a(this.sBsss, 6);
        frVar.a(this.sMcc, 7);
        frVar.a(this.sMnc, 8);
        frVar.a(this.sNumNeighbors, 9);
        frVar.a(this.vecNeighbors, 10);
        frVar.a(this.DW, 11);
        frVar.a(this.DX, 12);
    }
}
