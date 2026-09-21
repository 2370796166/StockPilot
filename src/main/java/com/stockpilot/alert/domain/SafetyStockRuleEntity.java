package com.stockpilot.alert.domain;

import java.math.BigDecimal;

public class SafetyStockRuleEntity {
    private Long id;
    private Long warehouseId;
    private Long locationId;
    private Long skuId;
    private BigDecimal thresholdQuantity;
    private String status;
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public BigDecimal getThresholdQuantity() {
        return thresholdQuantity;
    }

    public void setThresholdQuantity(BigDecimal thresholdQuantity) {
        this.thresholdQuantity = thresholdQuantity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
