package com.android.server.location.gnsschrlog;

import java.util.ArrayList;
import java.util.List;

public class CSubSecordaryCardCellInfo extends ChrLogBaseModel {
    public List<CSubCurrentCell> cCurrentCellList = new ArrayList(8);
    public List<CSubNeighborCell> cNeighborCellList = new ArrayList(8);
    public ENCSubEventId enSubEventId = new ENCSubEventId();

    public void setCSubCurrentCellList(CSubCurrentCell pCurrentCell) {
        if (pCurrentCell != null) {
            this.cCurrentCellList.add(pCurrentCell);
            this.lengthMap.put("cCurrentCellList", Integer.valueOf((((ChrLogBaseModel) this.cCurrentCellList.get(0)).getTotalBytes() * this.cCurrentCellList.size()) + 2));
            this.fieldMap.put("cCurrentCellList", this.cCurrentCellList);
        }
    }

    public void setCSubNeighborCellList(CSubNeighborCell pNeighborCell) {
        if (pNeighborCell != null) {
            this.cNeighborCellList.add(pNeighborCell);
            this.lengthMap.put("cNeighborCellList", Integer.valueOf((((ChrLogBaseModel) this.cNeighborCellList.get(0)).getTotalBytes() * this.cNeighborCellList.size()) + 2));
            this.fieldMap.put("cNeighborCellList", this.cNeighborCellList);
        }
    }

    public CSubSecordaryCardCellInfo() {
        this.lengthMap.put("enSubEventId", Integer.valueOf(2));
        this.fieldMap.put("enSubEventId", this.enSubEventId);
        this.lengthMap.put("cCurrentCellList", Integer.valueOf(2));
        this.fieldMap.put("cCurrentCellList", this.cCurrentCellList);
        this.lengthMap.put("cNeighborCellList", Integer.valueOf(2));
        this.fieldMap.put("cNeighborCellList", this.cNeighborCellList);
        this.enSubEventId.setValue("SecordaryCardCellInfo");
    }
}
