package com.stockpilot.masterdata.domain;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("warehouse_location")
public class WarehouseLocationEntity extends BaseMasterDataEntity {
    private Long warehouseId;

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }
}
