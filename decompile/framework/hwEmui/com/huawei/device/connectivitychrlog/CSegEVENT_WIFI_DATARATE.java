package com.huawei.device.connectivitychrlog;

public class CSegEVENT_WIFI_DATARATE extends ChrLogBaseEventModel {
    public ENCEventId enEventId = new ENCEventId();
    public LogInt iRSSI = new LogInt();
    public LogInt iRTT = new LogInt();
    public LogInt iReTranPkgs = new LogInt();
    public LogString strAP_SSID = new LogString(30);
    public LogDate tmTimeStamp = new LogDate(6);
    public LogByte ucCardIndex = new LogByte();
    public LogShort usLen = new LogShort();

    public CSegEVENT_WIFI_DATARATE() {
        this.lengthMap.put("enEventId", Integer.valueOf(1));
        this.fieldMap.put("enEventId", this.enEventId);
        this.lengthMap.put("usLen", Integer.valueOf(2));
        this.fieldMap.put("usLen", this.usLen);
        this.lengthMap.put("tmTimeStamp", Integer.valueOf(6));
        this.fieldMap.put("tmTimeStamp", this.tmTimeStamp);
        this.lengthMap.put("ucCardIndex", Integer.valueOf(1));
        this.fieldMap.put("ucCardIndex", this.ucCardIndex);
        this.lengthMap.put("strAP_SSID", Integer.valueOf(30));
        this.fieldMap.put("strAP_SSID", this.strAP_SSID);
        this.lengthMap.put("iRTT", Integer.valueOf(4));
        this.fieldMap.put("iRTT", this.iRTT);
        this.lengthMap.put("iReTranPkgs", Integer.valueOf(4));
        this.fieldMap.put("iReTranPkgs", this.iReTranPkgs);
        this.lengthMap.put("iRSSI", Integer.valueOf(4));
        this.fieldMap.put("iRSSI", this.iRSSI);
        this.enEventId.setValue("WIFI_DATARATE");
        this.usLen.setValue(getTotalLen());
    }
}
