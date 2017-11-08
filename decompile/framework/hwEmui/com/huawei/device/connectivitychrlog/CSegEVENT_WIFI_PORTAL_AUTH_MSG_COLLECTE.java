package com.huawei.device.connectivitychrlog;

public class CSegEVENT_WIFI_PORTAL_AUTH_MSG_COLLECTE extends ChrLogBaseEventModel {
    public LogByteArray aucAPSsid = new LogByteArray(32);
    public LogByteArray aucSMS_body = new LogByteArray(140);
    public ENCEventId enEventId = new ENCEventId();
    public LogString strAPBssid = new LogString(17);
    public LogString strLOC_CellID = new LogString(32);
    public LogString strSMS_Num = new LogString(32);
    public LogDate tmTimeStamp = new LogDate(6);
    public LogByte ucAPSsid_len = new LogByte();
    public LogByte ucCardIndex = new LogByte();
    public LogByte ucSMS_body_len = new LogByte();
    public LogShort usLen = new LogShort();

    public CSegEVENT_WIFI_PORTAL_AUTH_MSG_COLLECTE() {
        this.lengthMap.put("enEventId", Integer.valueOf(1));
        this.fieldMap.put("enEventId", this.enEventId);
        this.lengthMap.put("usLen", Integer.valueOf(2));
        this.fieldMap.put("usLen", this.usLen);
        this.lengthMap.put("tmTimeStamp", Integer.valueOf(6));
        this.fieldMap.put("tmTimeStamp", this.tmTimeStamp);
        this.lengthMap.put("ucCardIndex", Integer.valueOf(1));
        this.fieldMap.put("ucCardIndex", this.ucCardIndex);
        this.lengthMap.put("strSMS_Num", Integer.valueOf(32));
        this.fieldMap.put("strSMS_Num", this.strSMS_Num);
        this.lengthMap.put("ucSMS_body_len", Integer.valueOf(1));
        this.fieldMap.put("ucSMS_body_len", this.ucSMS_body_len);
        this.lengthMap.put("aucSMS_body", Integer.valueOf(140));
        this.fieldMap.put("aucSMS_body", this.aucSMS_body);
        this.lengthMap.put("ucAPSsid_len", Integer.valueOf(1));
        this.fieldMap.put("ucAPSsid_len", this.ucAPSsid_len);
        this.lengthMap.put("aucAPSsid", Integer.valueOf(32));
        this.fieldMap.put("aucAPSsid", this.aucAPSsid);
        this.lengthMap.put("strAPBssid", Integer.valueOf(17));
        this.fieldMap.put("strAPBssid", this.strAPBssid);
        this.lengthMap.put("strLOC_CellID", Integer.valueOf(32));
        this.fieldMap.put("strLOC_CellID", this.strLOC_CellID);
        this.enEventId.setValue("WIFI_PORTAL_AUTH_MSG_COLLECTE");
        this.usLen.setValue(getTotalLen());
    }
}
