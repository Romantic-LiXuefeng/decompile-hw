package tmsdk.common.module.network;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import java.util.Date;
import tmsdkobf.jf;

/* compiled from: Unknown */
public class NetworkInfoEntity extends jf implements Parcelable, Comparable<NetworkInfoEntity> {
    public static final Creator<NetworkInfoEntity> CREATOR = new Creator<NetworkInfoEntity>() {
        public NetworkInfoEntity[] bN(int i) {
            return new NetworkInfoEntity[i];
        }

        public /* synthetic */ Object createFromParcel(Parcel parcel) {
            return k(parcel);
        }

        public NetworkInfoEntity k(Parcel parcel) {
            NetworkInfoEntity networkInfoEntity = new NetworkInfoEntity();
            networkInfoEntity.mTotalForMonth = parcel.readLong();
            networkInfoEntity.mUsedForMonth = parcel.readLong();
            networkInfoEntity.mUsedTranslateForMonth = parcel.readLong();
            networkInfoEntity.mUsedReceiveForMonth = parcel.readLong();
            networkInfoEntity.mRetialForMonth = parcel.readLong();
            networkInfoEntity.mUsedForDay = parcel.readLong();
            networkInfoEntity.mUsedTranslateForDay = parcel.readLong();
            networkInfoEntity.mUsedReceiveForDay = parcel.readLong();
            networkInfoEntity.mStartDate = (Date) parcel.readSerializable();
            return networkInfoEntity;
        }

        public /* synthetic */ Object[] newArray(int i) {
            return bN(i);
        }
    };
    public long mRetialForMonth = 0;
    public Date mStartDate = new Date();
    public long mTotalForMonth = 0;
    public long mUsedForDay = 0;
    public long mUsedForMonth = 0;
    public long mUsedReceiveForDay = 0;
    public long mUsedReceiveForMonth = 0;
    public long mUsedTranslateForDay = 0;
    public long mUsedTranslateForMonth = 0;

    public int compareTo(NetworkInfoEntity networkInfoEntity) {
        return this.mStartDate.compareTo(networkInfoEntity.mStartDate);
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeLong(this.mTotalForMonth);
        parcel.writeLong(this.mUsedForMonth);
        parcel.writeLong(this.mUsedTranslateForMonth);
        parcel.writeLong(this.mUsedReceiveForMonth);
        parcel.writeLong(this.mRetialForMonth);
        parcel.writeLong(this.mUsedForDay);
        parcel.writeLong(this.mUsedTranslateForDay);
        parcel.writeLong(this.mUsedReceiveForDay);
        parcel.writeSerializable(this.mStartDate);
    }
}
