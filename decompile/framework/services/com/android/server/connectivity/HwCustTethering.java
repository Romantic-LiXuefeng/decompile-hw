package com.android.server.connectivity;

import android.content.Context;
import java.util.HashMap;

public class HwCustTethering {
    public Context mContext = null;

    public HwCustTethering(Context context) {
        this.mContext = context;
    }

    public void registerBroadcast(Object publicSync, Tethering tethering, HashMap<String, TetherInterfaceSM> hashMap) {
    }
}
