package com.stockpilot.masterdata.location.request;
import com.stockpilot.masterdata.request.PageQuery;
import jakarta.validation.constraints.Positive;
public class LocationPageQuery extends PageQuery {
    @Positive private Long warehouseId;
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
}
