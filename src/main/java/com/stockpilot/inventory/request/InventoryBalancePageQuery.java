package com.stockpilot.inventory.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public class InventoryBalancePageQuery {
    @Min(1)
    private long page = 1;

    @Min(1)
    @Max(100)
    private long size = 20;

    @Positive private Long warehouseId;
    @Positive private Long locationId;
    @Positive private Long skuId;

    @DecimalMin("0")
    @Digits(integer = 15, fraction = 4)
    private BigDecimal belowAvailable;

    public BigDecimal getBelowAvailable() {
        return belowAvailable;
    }

    public void setBelowAvailable(BigDecimal value) {
        belowAvailable = value;
    }

    public long getPage() {
        return page;
    }

    public void setPage(long page) {
        this.page = page;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }
}
