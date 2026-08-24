package com.stockpilot.inventory.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class InventoryLedgerEntity {
    private Long id;
    private String ledgerNo;
    private InventoryBusinessType businessType;
    private String businessNo;
    private Long warehouseId;
    private Long locationId;
    private Long skuId;
    private BigDecimal beforeActualQuantity;
    private BigDecimal changeActualQuantity;
    private BigDecimal afterActualQuantity;
    private BigDecimal beforeAvailableQuantity;
    private BigDecimal changeAvailableQuantity;
    private BigDecimal afterAvailableQuantity;
    private BigDecimal beforeFrozenQuantity;
    private BigDecimal changeFrozenQuantity;
    private BigDecimal afterFrozenQuantity;
    private Integer balanceVersionBefore;
    private Integer balanceVersionAfter;
    private BigDecimal countBookQuantity;
    private BigDecimal countedQuantity;
    private BigDecimal differenceQuantity;
    private String adjustmentReason;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime occurredAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getLedgerNo() { return ledgerNo; }
    public void setLedgerNo(String ledgerNo) { this.ledgerNo = ledgerNo; }
    public InventoryBusinessType getBusinessType() { return businessType; }
    public void setBusinessType(InventoryBusinessType businessType) { this.businessType = businessType; }
    public String getBusinessNo() { return businessNo; }
    public void setBusinessNo(String businessNo) { this.businessNo = businessNo; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }
    public BigDecimal getBeforeActualQuantity() { return beforeActualQuantity; }
    public void setBeforeActualQuantity(BigDecimal value) { beforeActualQuantity = value; }
    public BigDecimal getChangeActualQuantity() { return changeActualQuantity; }
    public void setChangeActualQuantity(BigDecimal value) { changeActualQuantity = value; }
    public BigDecimal getAfterActualQuantity() { return afterActualQuantity; }
    public void setAfterActualQuantity(BigDecimal value) { afterActualQuantity = value; }
    public BigDecimal getBeforeAvailableQuantity() { return beforeAvailableQuantity; }
    public void setBeforeAvailableQuantity(BigDecimal value) { beforeAvailableQuantity = value; }
    public BigDecimal getChangeAvailableQuantity() { return changeAvailableQuantity; }
    public void setChangeAvailableQuantity(BigDecimal value) { changeAvailableQuantity = value; }
    public BigDecimal getAfterAvailableQuantity() { return afterAvailableQuantity; }
    public void setAfterAvailableQuantity(BigDecimal value) { afterAvailableQuantity = value; }
    public BigDecimal getBeforeFrozenQuantity() { return beforeFrozenQuantity; }
    public void setBeforeFrozenQuantity(BigDecimal value) { beforeFrozenQuantity = value; }
    public BigDecimal getChangeFrozenQuantity() { return changeFrozenQuantity; }
    public void setChangeFrozenQuantity(BigDecimal value) { changeFrozenQuantity = value; }
    public BigDecimal getAfterFrozenQuantity() { return afterFrozenQuantity; }
    public void setAfterFrozenQuantity(BigDecimal value) { afterFrozenQuantity = value; }
    public Integer getBalanceVersionBefore() { return balanceVersionBefore; }
    public void setBalanceVersionBefore(Integer value) { balanceVersionBefore = value; }
    public Integer getBalanceVersionAfter() { return balanceVersionAfter; }
    public void setBalanceVersionAfter(Integer value) { balanceVersionAfter = value; }
    public BigDecimal getCountBookQuantity() { return countBookQuantity; }
    public void setCountBookQuantity(BigDecimal value) { countBookQuantity = value; }
    public BigDecimal getCountedQuantity() { return countedQuantity; }
    public void setCountedQuantity(BigDecimal value) { countedQuantity = value; }
    public BigDecimal getDifferenceQuantity() { return differenceQuantity; }
    public void setDifferenceQuantity(BigDecimal value) { differenceQuantity = value; }
    public String getAdjustmentReason() { return adjustmentReason; }
    public void setAdjustmentReason(String value) { adjustmentReason = value; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
}
