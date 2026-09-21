package com.stockpilot.transfer.domain;

import java.math.BigDecimal;

public class StockTransferLineEntity {
    private Long id;
    private Long transferId;
    private Long sourceWarehouseId;
    private Long targetWarehouseId;
    private Integer lineNo;
    private Long sourceLocationId;
    private Long targetLocationId;
    private Long skuId;
    private BigDecimal quantity;

    public Long getId() {
        return id;
    }

    public void setId(Long v) {
        id = v;
    }

    public Long getTransferId() {
        return transferId;
    }

    public void setTransferId(Long v) {
        transferId = v;
    }

    public Long getSourceWarehouseId() {
        return sourceWarehouseId;
    }

    public void setSourceWarehouseId(Long v) {
        sourceWarehouseId = v;
    }

    public Long getTargetWarehouseId() {
        return targetWarehouseId;
    }

    public void setTargetWarehouseId(Long v) {
        targetWarehouseId = v;
    }

    public Integer getLineNo() {
        return lineNo;
    }

    public void setLineNo(Integer v) {
        lineNo = v;
    }

    public Long getSourceLocationId() {
        return sourceLocationId;
    }

    public void setSourceLocationId(Long v) {
        sourceLocationId = v;
    }

    public Long getTargetLocationId() {
        return targetLocationId;
    }

    public void setTargetLocationId(Long v) {
        targetLocationId = v;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long v) {
        skuId = v;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal v) {
        quantity = v;
    }
}
