package tmsdkobf;

/* compiled from: Unknown */
public final class bs extends fs {
    public int dX = 0;
    public String dY = "";
    public String sender = "";
    public String sms = "";
    public int uiCheckFlag = 0;
    public int uiCheckType = 0;
    public int uiSmsInOut = 0;
    public int uiSmsType = 0;

    public fs newInit() {
        return new bs();
    }

    public void readFrom(fq fqVar) {
        this.sender = fqVar.a(0, true);
        this.sms = fqVar.a(1, true);
        this.uiSmsType = fqVar.a(this.uiSmsType, 2, true);
        this.uiCheckType = fqVar.a(this.uiCheckType, 3, true);
        this.uiSmsInOut = fqVar.a(this.uiSmsInOut, 4, false);
        this.uiCheckFlag = fqVar.a(this.uiCheckFlag, 5, false);
        this.dX = fqVar.a(this.dX, 6, false);
        this.dY = fqVar.a(7, false);
    }

    public void writeTo(fr frVar) {
        frVar.a(this.sender, 0);
        frVar.a(this.sms, 1);
        frVar.write(this.uiSmsType, 2);
        frVar.write(this.uiCheckType, 3);
        if (this.uiSmsInOut != 0) {
            frVar.write(this.uiSmsInOut, 4);
        }
        if (this.uiCheckFlag != 0) {
            frVar.write(this.uiCheckFlag, 5);
        }
        if (this.dX != 0) {
            frVar.write(this.dX, 6);
        }
        if (this.dY != null) {
            frVar.a(this.dY, 7);
        }
    }
}
