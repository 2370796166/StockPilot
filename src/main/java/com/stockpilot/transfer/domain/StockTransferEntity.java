package com.stockpilot.transfer.domain;

import java.time.LocalDateTime;

public class StockTransferEntity {
    private Long id;
    private String transferNo;
    private Long sourceWarehouseId;
    private Long targetWarehouseId;
    private StockTransferStatus status;
    private String remark;
    private Long createdBy;
    private String createdByName;
    private Long submittedBy;
    private String submittedByName;
    private LocalDateTime submittedAt;
    private Long approvedBy;
    private String approvedByName;
    private LocalDateTime approvedAt;
    private Long outboundBy;
    private String outboundByName;
    private LocalDateTime outboundAt;
    private Long transitBy;
    private String transitByName;
    private LocalDateTime transitAt;
    private Long completedBy;
    private String completedByName;
    private LocalDateTime completedAt;
    private Long cancelledBy;
    private String cancelledByName;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long v) {
        id = v;
    }

    public String getTransferNo() {
        return transferNo;
    }

    public void setTransferNo(String v) {
        transferNo = v;
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

    public StockTransferStatus getStatus() {
        return status;
    }

    public void setStatus(StockTransferStatus v) {
        status = v;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String v) {
        remark = v;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long v) {
        createdBy = v;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String v) {
        createdByName = v;
    }

    public Long getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(Long v) {
        submittedBy = v;
    }

    public String getSubmittedByName() {
        return submittedByName;
    }

    public void setSubmittedByName(String v) {
        submittedByName = v;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime v) {
        submittedAt = v;
    }

    public Long getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(Long v) {
        approvedBy = v;
    }

    public String getApprovedByName() {
        return approvedByName;
    }

    public void setApprovedByName(String v) {
        approvedByName = v;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime v) {
        approvedAt = v;
    }

    public Long getOutboundBy() {
        return outboundBy;
    }

    public void setOutboundBy(Long v) {
        outboundBy = v;
    }

    public String getOutboundByName() {
        return outboundByName;
    }

    public void setOutboundByName(String v) {
        outboundByName = v;
    }

    public LocalDateTime getOutboundAt() {
        return outboundAt;
    }

    public void setOutboundAt(LocalDateTime v) {
        outboundAt = v;
    }

    public Long getTransitBy() {
        return transitBy;
    }

    public void setTransitBy(Long v) {
        transitBy = v;
    }

    public String getTransitByName() {
        return transitByName;
    }

    public void setTransitByName(String v) {
        transitByName = v;
    }

    public LocalDateTime getTransitAt() {
        return transitAt;
    }

    public void setTransitAt(LocalDateTime v) {
        transitAt = v;
    }

    public Long getCompletedBy() {
        return completedBy;
    }

    public void setCompletedBy(Long v) {
        completedBy = v;
    }

    public String getCompletedByName() {
        return completedByName;
    }

    public void setCompletedByName(String v) {
        completedByName = v;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime v) {
        completedAt = v;
    }

    public Long getCancelledBy() {
        return cancelledBy;
    }

    public void setCancelledBy(Long v) {
        cancelledBy = v;
    }

    public String getCancelledByName() {
        return cancelledByName;
    }

    public void setCancelledByName(String v) {
        cancelledByName = v;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(LocalDateTime v) {
        cancelledAt = v;
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

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer v) {
        version = v;
    }
}
