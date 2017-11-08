package com.android.server.rms.iaware.memory.data.dispatch;

import android.rms.iaware.AwareConstant.ResourceType;
import android.rms.iaware.AwareLog;
import android.rms.iaware.CollectData;
import com.android.server.rms.iaware.memory.data.content.AttrSegments;
import com.android.server.rms.iaware.memory.data.content.AttrSegments.Builder;
import com.android.server.rms.iaware.memory.data.handle.DataAppHandle;
import com.android.server.rms.iaware.memory.data.handle.DataDevStatusHandle;
import com.android.server.rms.iaware.memory.data.handle.DataInputHandle;
import java.util.concurrent.atomic.AtomicBoolean;

public class DataDispatch {
    private static final /* synthetic */ int[] -android-rms-iaware-AwareConstant$ResourceTypeSwitchesValues = null;
    private static final String TAG = "AwareMem_DataDispatch";
    private static DataDispatch sDataDispatch;
    private final AtomicBoolean mRunning = new AtomicBoolean(false);

    private static /* synthetic */ int[] -getandroid-rms-iaware-AwareConstant$ResourceTypeSwitchesValues() {
        if (-android-rms-iaware-AwareConstant$ResourceTypeSwitchesValues != null) {
            return -android-rms-iaware-AwareConstant$ResourceTypeSwitchesValues;
        }
        int[] iArr = new int[ResourceType.values().length];
        try {
            iArr[ResourceType.RESOURCE_APPASSOC.ordinal()] = 5;
        } catch (NoSuchFieldError e) {
        }
        try {
            iArr[ResourceType.RESOURCE_BOOT_COMPLETED.ordinal()] = 6;
        } catch (NoSuchFieldError e2) {
        }
        try {
            iArr[ResourceType.RESOURCE_HOME.ordinal()] = 7;
        } catch (NoSuchFieldError e3) {
        }
        try {
            iArr[ResourceType.RESOURCE_INVALIDE_TYPE.ordinal()] = 8;
        } catch (NoSuchFieldError e4) {
        }
        try {
            iArr[ResourceType.RESOURCE_SCREEN_OFF.ordinal()] = 1;
        } catch (NoSuchFieldError e5) {
        }
        try {
            iArr[ResourceType.RESOURCE_SCREEN_ON.ordinal()] = 2;
        } catch (NoSuchFieldError e6) {
        }
        try {
            iArr[ResourceType.RESOURCE_USERHABIT.ordinal()] = 9;
        } catch (NoSuchFieldError e7) {
        }
        try {
            iArr[ResourceType.RES_APP.ordinal()] = 3;
        } catch (NoSuchFieldError e8) {
        }
        try {
            iArr[ResourceType.RES_DEV_STATUS.ordinal()] = 10;
        } catch (NoSuchFieldError e9) {
        }
        try {
            iArr[ResourceType.RES_INPUT.ordinal()] = 4;
        } catch (NoSuchFieldError e10) {
        }
        -android-rms-iaware-AwareConstant$ResourceTypeSwitchesValues = iArr;
        return iArr;
    }

    public static DataDispatch getInstance() {
        DataDispatch dataDispatch;
        synchronized (DataDispatch.class) {
            if (sDataDispatch == null) {
                sDataDispatch = new DataDispatch();
            }
            dataDispatch = sDataDispatch;
        }
        return dataDispatch;
    }

    public void start() {
        AwareLog.i(TAG, "start");
        this.mRunning.set(true);
    }

    public void stop() {
        AwareLog.i(TAG, "stop");
        this.mRunning.set(false);
    }

    public int reportData(CollectData data) {
        if (!this.mRunning.get() || data == null) {
            AwareLog.e(TAG, "DataDispatch not start");
            return -1;
        }
        long timestamp = data.getTimeStamp();
        int ret = -1;
        AttrSegments attrSegments;
        switch (-getandroid-rms-iaware-AwareConstant$ResourceTypeSwitchesValues()[ResourceType.getResourceType(data.getResId()).ordinal()]) {
            case 1:
                ret = DataDevStatusHandle.getInstance().reportData(timestamp, 90011, null);
                break;
            case 2:
                ret = DataDevStatusHandle.getInstance().reportData(timestamp, 20011, null);
                break;
            case 3:
                attrSegments = parseCollectData(data);
                if (attrSegments.isValid()) {
                    ret = DataAppHandle.getInstance().reportData(timestamp, attrSegments.getEvent().intValue(), attrSegments);
                    break;
                }
                break;
            case 4:
                attrSegments = parseCollectData(data);
                if (attrSegments.isValid()) {
                    ret = DataInputHandle.getInstance().reportData(timestamp, attrSegments.getEvent().intValue(), attrSegments);
                    break;
                }
                break;
            default:
                AwareLog.e(TAG, "Invalid ResourceType");
                ret = -1;
                break;
        }
        return ret;
    }

    private AttrSegments parseCollectData(CollectData data) {
        String eventData = data.getData();
        Builder builder = new Builder();
        builder.addCollectData(eventData);
        return builder.build();
    }
}
