package tmsdkobf;

import java.util.ArrayList;
import java.util.Iterator;
import tmsdk.common.utils.d;
import tmsdkobf.pb.b;

/* compiled from: Unknown */
class op implements b {
    int gc = 0;

    public op(int i) {
        this.gc = i;
    }

    public void a(boolean z, int i, int i2, ArrayList<bq> arrayList) {
        if (i != 0) {
            ca(i);
        } else if (arrayList == null || arrayList.size() == 0) {
            if (arrayList != null) {
                d.e("TmsTcpManager", "ISharkDoneCallback : serverSashimis.size() == 0");
            } else {
                d.e("TmsTcpManager", "ISharkDoneCallback : serverSashimis == null");
            }
            ca(-250000);
        } else {
            Iterator it = arrayList.iterator();
            while (it.hasNext()) {
                bq bqVar = (bq) it.next();
                if (bqVar != null && bqVar.aZ == this.gc && bqVar.dK == 0) {
                    onSuccess();
                    break;
                }
            }
        }
    }

    protected void ca(int i) {
    }

    protected void onSuccess() {
    }
}
