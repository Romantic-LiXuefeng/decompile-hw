package com.huawei.device.connectivitychrlog;

public class CSegEVENT_WIFI_HAL_DRIVER_DEVICE_EXCEPTION extends ChrLogBaseEventModel {
    public ENCEventId enEventId = new ENCEventId();
    public ENCWIFI_HAL_DRIVER_DEVICE_EXCEPTION_REASON enWIFI_HAL_DRIVER_DEVICE_EXCEPTION_REASON = new ENCWIFI_HAL_DRIVER_DEVICE_EXCEPTION_REASON();
    public LogDate tmTimeStamp = new LogDate(6);
    public LogByte ucCardIndex = new LogByte();
    public LogShort usLen = new LogShort();

    public CSegEVENT_WIFI_HAL_DRIVER_DEVICE_EXCEPTION() {
        this.lengthMap.put("enEventId", Integer.valueOf(1));
        this.fieldMap.put("enEventId", this.enEventId);
        this.lengthMap.put("usLen", Integer.valueOf(2));
        this.fieldMap.put("usLen", this.usLen);
        this.lengthMap.put("tmTimeStamp", Integer.valueOf(6));
        this.fieldMap.put("tmTimeStamp", this.tmTimeStamp);
        this.lengthMap.put("ucCardIndex", Integer.valueOf(1));
        this.fieldMap.put("ucCardIndex", this.ucCardIndex);
        this.lengthMap.put("enWIFI_HAL_DRIVER_DEVICE_EXCEPTION_REASON", Integer.valueOf(1));
        this.fieldMap.put("enWIFI_HAL_DRIVER_DEVICE_EXCEPTION_REASON", this.enWIFI_HAL_DRIVER_DEVICE_EXCEPTION_REASON);
        this.enEventId.setValue("WIFI_HAL_DRIVER_DEVICE_EXCEPTION");
        this.usLen.setValue(getTotalLen());
    }
}
