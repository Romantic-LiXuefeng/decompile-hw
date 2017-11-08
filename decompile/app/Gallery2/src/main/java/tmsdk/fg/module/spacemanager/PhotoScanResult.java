package tmsdk.fg.module.spacemanager;

import android.util.Pair;
import java.util.ArrayList;

/* compiled from: Unknown */
public class PhotoScanResult {
    public long mInnerPicSize;
    public long mOutPicSize;
    public Pair<Integer, Long> mPhotoCountAndSize;
    public ArrayList<PhotoItem> mResultList;
    public Pair<Integer, Long> mScreenShotCountAndSize;

    /* compiled from: Unknown */
    public static class PhotoItem {
        public long mDbId;
        public boolean mIsOut = false;
        public boolean mIsScreenShot = false;
        public String mPath = "";
        public long mSize;
        public long mTime;
    }
}
