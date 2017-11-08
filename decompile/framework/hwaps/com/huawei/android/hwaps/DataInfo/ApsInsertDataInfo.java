package com.huawei.android.hwaps.DataInfo;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import java.util.HashMap;

public class ApsInsertDataInfo implements Parcelable {
    public static final Creator<ApsInsertDataInfo> CREATOR = new Creator<ApsInsertDataInfo>() {
        public ApsInsertDataInfo createFromParcel(Parcel in) {
            return new ApsInsertDataInfo(in.readString(), in.readHashMap(null));
        }

        public ApsInsertDataInfo[] newArray(int size) {
            return new ApsInsertDataInfo[size];
        }
    };
    public String mUri = null;
    public HashMap<String, Object> mValues = null;

    public ApsInsertDataInfo(String strUri, HashMap<String, Object> values) {
        this.mUri = strUri;
        this.mValues = values;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        synchronized (this) {
            dest.writeString(this.mUri);
            dest.writeMap(this.mValues);
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (this.mUri != null) {
            sb.append("ApsUpdateDataInfo: Uri:").append(this.mUri);
        }
        if (this.mValues != null) {
            sb.append(" Values:");
            for (String name : this.mValues.keySet()) {
                Object obj = this.mValues.get(name);
                String obj2 = obj != null ? obj.toString() : null;
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(name).append("=").append(obj2);
            }
        }
        return sb.toString();
    }
}
