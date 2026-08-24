package com.stockpilot.inventory.application;

import java.math.BigDecimal;

public record InventoryCountAdjustmentCommand(
        long countId,
        long countLineId,
        String countNo,
        long warehouseId,
        long locationId,
        long skuId,
        int snapshotVersion,
        BigDecimal snapshotActual,
        BigDecimal snapshotAvailable,
        BigDecimal snapshotFrozen,
        BigDecimal countedQuantity,
        String reason,
        long operatorId,
        String operatorName) {
}
