package com.stockpilot.inventory.vo;

import com.stockpilot.inventory.domain.InventoryBusinessType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InventoryLedgerVO(
        Long id,
        String ledgerNo,
        InventoryBusinessType businessType,
        String businessNo,
        Long warehouseId,
        Long locationId,
        Long skuId,
        BigDecimal beforeActualQuantity,
        BigDecimal changeActualQuantity,
        BigDecimal afterActualQuantity,
        BigDecimal beforeAvailableQuantity,
        BigDecimal changeAvailableQuantity,
        BigDecimal afterAvailableQuantity,
        BigDecimal beforeFrozenQuantity,
        BigDecimal changeFrozenQuantity,
        BigDecimal afterFrozenQuantity,
        Integer balanceVersionBefore,
        Integer balanceVersionAfter,
        BigDecimal countBookQuantity,
        BigDecimal countedQuantity,
        BigDecimal differenceQuantity,
        String adjustmentReason,
        Long operatorId,
        String operatorName,
        LocalDateTime occurredAt) {
}
