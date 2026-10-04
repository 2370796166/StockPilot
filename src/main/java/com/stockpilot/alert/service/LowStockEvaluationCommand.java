package com.stockpilot.alert.service;

import java.util.List;

public record LowStockEvaluationCommand(
        String messageId,
        String eventName,
        String businessNo,
        long warehouseId,
        List<InventoryDimension> dimensions) {

    public LowStockEvaluationCommand {
        dimensions = List.copyOf(dimensions);
    }

    public record InventoryDimension(long locationId, long skuId) {}
}
