package com.stockpilot.masterdata.location.domain;
import com.baomidou.mybatisplus.annotation.TableName;
import com.stockpilot.masterdata.domain.BaseMasterDataEntity;
@TableName("warehouse_location")
public class WarehouseLocationEntity extends BaseMasterDataEntity {
    private Long warehouseId;
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
}
