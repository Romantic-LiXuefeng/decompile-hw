package tmsdk.fg.module.qscanner;

import java.util.ArrayList;
import tmsdkobf.fq;
import tmsdkobf.fr;
import tmsdkobf.fs;

/* compiled from: Unknown */
public final class QScanReport extends fs {
    static ArrayList<QScanRecord> LU;
    public long id = 0;
    public ArrayList<QScanRecord> records = null;
    public int riskFound = 0;
    public String tips = "";
    public int type = 0;
    public int virusCured = 0;
    public int virusFound = 0;
    public int waitDealing = 0;

    public QScanReport(long j, ArrayList<QScanRecord> arrayList, int i, int i2, int i3, int i4, int i5, String str) {
        this.id = j;
        this.records = arrayList;
        this.type = i;
        this.virusFound = i2;
        this.virusCured = i3;
        this.waitDealing = i4;
        this.riskFound = i5;
        this.tips = str;
    }

    public void readFrom(fq fqVar) {
        this.id = fqVar.a(this.id, 0, true);
        if (LU == null) {
            LU = new ArrayList();
            LU.add(new QScanRecord());
        }
        this.records = (ArrayList) fqVar.b(LU, 1, true);
        this.type = fqVar.a(this.type, 2, true);
        this.virusFound = fqVar.a(this.virusFound, 3, false);
        this.virusCured = fqVar.a(this.virusCured, 4, false);
        this.waitDealing = fqVar.a(this.waitDealing, 5, false);
        this.riskFound = fqVar.a(this.riskFound, 6, false);
        this.tips = fqVar.a(7, false);
    }

    public void writeTo(fr frVar) {
        frVar.b(this.id, 0);
        frVar.a(this.records, 1);
        frVar.write(this.type, 2);
        frVar.write(this.virusFound, 3);
        frVar.write(this.virusCured, 4);
        frVar.write(this.waitDealing, 5);
        frVar.write(this.riskFound, 6);
        if (this.tips != null) {
            frVar.a(this.tips, 7);
        }
    }
}
