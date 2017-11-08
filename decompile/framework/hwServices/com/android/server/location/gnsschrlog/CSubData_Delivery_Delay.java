package com.android.server.location.gnsschrlog;

public class CSubData_Delivery_Delay extends ChrLogBaseModel {
    public ENCSubEventId enSubEventId = new ENCSubEventId();
    public LogInt iDelayTime = new LogInt();

    public CSubData_Delivery_Delay() {
        this.lengthMap.put("enSubEventId", Integer.valueOf(2));
        this.fieldMap.put("enSubEventId", this.enSubEventId);
        this.lengthMap.put("iDelayTime", Integer.valueOf(4));
        this.fieldMap.put("iDelayTime", this.iDelayTime);
        this.enSubEventId.setValue("Data_Delivery_Delay");
    }
}
