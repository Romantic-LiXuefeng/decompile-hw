package com.tencent.tmsecurelite.commom;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;

public final class DataEntity extends JSONObject implements Parcelable {
    public static final Creator<DataEntity> CREATOR = new Creator<DataEntity>() {
        public DataEntity createFromParcel(Parcel arg0) {
            try {
                return new DataEntity(arg0);
            } catch (JSONException e) {
                e.printStackTrace();
                return null;
            }
        }

        public DataEntity[] newArray(int arg0) {
            return new DataEntity[arg0];
        }
    };

    public DataEntity(Parcel src) throws JSONException {
        super(src.readString());
    }

    public static ArrayList<DataEntity> readFromParcel(Parcel src) {
        ArrayList<DataEntity> result = new ArrayList();
        try {
            int size = src.readInt();
            result.ensureCapacity(size);
            for (int i = 0; i < size; i++) {
                result.add(i, new DataEntity(src));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static void writeToParcel(List<DataEntity> datas, Parcel dest) {
        dest.writeInt(datas.size());
        for (DataEntity entity : datas) {
            entity.writeToParcel(dest, 0);
        }
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(toString());
    }
}
