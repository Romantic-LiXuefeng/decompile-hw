package tmsdk.common.module.urlcheck;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;

/* compiled from: Unknown */
public class WebPageDetail implements Parcelable {
    public static Creator<WebPageDetail> CREATOR = new Creator<WebPageDetail>() {
        public /* synthetic */ Object createFromParcel(Parcel parcel) {
            return u(parcel);
        }

        public WebPageDetail[] cw(int i) {
            return new WebPageDetail[i];
        }

        public /* synthetic */ Object[] newArray(int i) {
            return cw(i);
        }

        public WebPageDetail u(Parcel parcel) {
            WebPageDetail webPageDetail = new WebPageDetail();
            webPageDetail.title = parcel.readString();
            webPageDetail.description = parcel.readString();
            webPageDetail.webIconUrl = parcel.readString();
            webPageDetail.screenshotUrl = parcel.readString();
            webPageDetail.maliceType = parcel.readLong();
            webPageDetail.maliceTitle = parcel.readString();
            webPageDetail.maliceBody = parcel.readString();
            webPageDetail.flawName = parcel.readString();
            return webPageDetail;
        }
    };
    public String description = "";
    public String flawName = "";
    public String maliceBody = "";
    public String maliceTitle = "";
    public long maliceType = 0;
    public String screenshotUrl = "";
    public String title = "";
    public String webIconUrl = "";

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(this.title);
        parcel.writeString(this.description);
        parcel.writeString(this.webIconUrl);
        parcel.writeString(this.screenshotUrl);
        parcel.writeLong(this.maliceType);
        parcel.writeString(this.maliceTitle);
        parcel.writeString(this.maliceBody);
        parcel.writeString(this.flawName);
    }
}
