package com.stockpilot.transfer.domain;

import java.math.BigDecimal;

public class StockTransferTransitEntity {
    private Long id;
    private Long transferId;
    private Long transferLineId;
    private BigDecimal outboundQuantity;
    private BigDecimal inTransitQuantity;
    private BigDecimal receivedQuantity;
    private String status;
    private Integer version;

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

    public Long getTransferLineId() {
        return transferLineId;
    }

    public void setTransferLineId(Long v) {
        transferLineId = v;
    }

    public BigDecimal getOutboundQuantity() {
        return outboundQuantity;
    }

    public void setOutboundQuantity(BigDecimal v) {
        outboundQuantity = v;
    }

    public BigDecimal getInTransitQuantity() {
        return inTransitQuantity;
    }

    public void setInTransitQuantity(BigDecimal v) {
        inTransitQuantity = v;
    }

    public BigDecimal getReceivedQuantity() {
        return receivedQuantity;
    }

    public void setReceivedQuantity(BigDecimal v) {
        receivedQuantity = v;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String v) {
        status = v;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer v) {
        version = v;
    }
}
