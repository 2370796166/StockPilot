package com.stockpilot.masterdata.request;

import jakarta.validation.constraints.Positive;

public class LocationPageQuery extends PageQuery {
    @Positive private Long warehouseId;

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }
}
