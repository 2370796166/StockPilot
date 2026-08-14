package com.stockpilot.inbound.vo;

import com.stockpilot.inbound.domain.PurchaseReceiptStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PurchaseReceiptVO(
        Long id,
        String receiptNo,
        Long warehouseId,
        PurchaseReceiptStatus status,
        String remark,
        Long createdBy,
        String createdByName,
        Long submittedBy,
        String submittedByName,
        LocalDateTime submittedAt,
        Long approvedBy,
        String approvedByName,
        LocalDateTime approvedAt,
        Long completedBy,
        String completedByName,
        LocalDateTime completedAt,
        Integer version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<Line> lines) {

    public record Line(Long id, Integer lineNo, Long locationId, Long skuId, BigDecimal quantity) {
    }
}
