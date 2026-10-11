package com.stockpilot.alert.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class SafetyStockVO {
    private SafetyStockVO() {}

    public record Rule(
            long id,
            long warehouseId,
            long locationId,
            long skuId,
            BigDecimal thresholdQuantity,
            String status,
            int version) {}

    public record Alert(
            long id,
            long ruleId,
            long warehouseId,
            long locationId,
            long skuId,
            BigDecimal thresholdQuantity,
            BigDecimal availableQuantity,
            String status,
            String sourceMessageId,
            String sourceEventName,
            String businessNo,
            LocalDateTime firstTriggeredAt,
            LocalDateTime lastEvaluatedAt,
            LocalDateTime resolvedAt) {}
}
