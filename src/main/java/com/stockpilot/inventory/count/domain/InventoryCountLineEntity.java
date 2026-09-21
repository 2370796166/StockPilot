package com.stockpilot.inventory.count.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class InventoryCountLineEntity {
    private Long id;
    private Long countId;
    private Long warehouseId;
    private Integer lineNo;
    private Long locationId;
    private Long skuId;
    private BigDecimal snapshotActualQuantity;
    private BigDecimal snapshotAvailableQuantity;
    private BigDecimal snapshotFrozenQuantity;
    private Integer snapshotBalanceVersion;
    private BigDecimal countedQuantity;
    private BigDecimal differenceQuantity;
    private String reason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long v) {
        id = v;
    }

    public Long getCountId() {
        return countId;
    }

    public void setCountId(Long v) {
        countId = v;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long v) {
        warehouseId = v;
    }

    public Integer getLineNo() {
        return lineNo;
    }

    public void setLineNo(Integer v) {
        lineNo = v;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long v) {
        locationId = v;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long v) {
        skuId = v;
    }

    public BigDecimal getSnapshotActualQuantity() {
        return snapshotActualQuantity;
    }

    public void setSnapshotActualQuantity(BigDecimal v) {
        snapshotActualQuantity = v;
    }

    public BigDecimal getSnapshotAvailableQuantity() {
        return snapshotAvailableQuantity;
    }

    public void setSnapshotAvailableQuantity(BigDecimal v) {
        snapshotAvailableQuantity = v;
    }

    public BigDecimal getSnapshotFrozenQuantity() {
        return snapshotFrozenQuantity;
    }

    public void setSnapshotFrozenQuantity(BigDecimal v) {
        snapshotFrozenQuantity = v;
    }

    public Integer getSnapshotBalanceVersion() {
        return snapshotBalanceVersion;
    }

    public void setSnapshotBalanceVersion(Integer v) {
        snapshotBalanceVersion = v;
    }

    public BigDecimal getCountedQuantity() {
        return countedQuantity;
    }

    public void setCountedQuantity(BigDecimal v) {
        countedQuantity = v;
    }

    public BigDecimal getDifferenceQuantity() {
        return differenceQuantity;
    }

    public void setDifferenceQuantity(BigDecimal v) {
        differenceQuantity = v;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String v) {
        reason = v;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime v) {
        createdAt = v;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime v) {
        updatedAt = v;
    }
}
