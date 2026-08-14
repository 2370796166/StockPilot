package com.stockpilot.inventory.request;

import com.stockpilot.inventory.domain.InventoryBusinessType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class InventoryLedgerPageQuery {
    @Min(1) private long page = 1;
    @Min(1) @Max(100) private long size = 20;
    @Positive private Long warehouseId;
    @Positive private Long locationId;
    @Positive private Long skuId;
    private InventoryBusinessType businessType;
    @Size(max = 64) private String businessNo;

    public long getPage() { return page; }
    public void setPage(long page) { this.page = page; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }
    public InventoryBusinessType getBusinessType() { return businessType; }
    public void setBusinessType(InventoryBusinessType businessType) { this.businessType = businessType; }
    public String getBusinessNo() { return businessNo; }
    public void setBusinessNo(String businessNo) { this.businessNo = businessNo; }
}
