package tmsdkobf;

/* compiled from: Unknown */
public class mn {
    private StringBuffer Bh = new StringBuffer();
    private nc yq = new nc("CheckPoint");

    public void commit() {
        if (this.Bh.length() > 0) {
            String string = this.yq.getString("data", null);
            if (string == null) {
                string = "";
            }
            this.yq.a("data", string + this.Bh.toString(), true);
            this.Bh = new StringBuffer();
        }
    }

    public void r(int i, int i2) {
        this.Bh.append(i + ":" + i2 + ";");
    }
}
