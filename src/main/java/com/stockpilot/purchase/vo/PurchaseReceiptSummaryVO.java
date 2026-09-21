package com.stockpilot.purchase.vo;

import com.stockpilot.purchase.domain.PurchaseReceiptStatus;
import java.time.LocalDateTime;

public record PurchaseReceiptSummaryVO(
        Long id,
        String receiptNo,
        Long warehouseId,
        PurchaseReceiptStatus status,
        String remark,
        String createdByName,
        Integer version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
