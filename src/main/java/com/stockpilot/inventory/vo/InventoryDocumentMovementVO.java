package com.stockpilot.inventory.vo;

import com.stockpilot.inventory.domain.InventoryBusinessType;
import java.math.BigDecimal;

public record InventoryDocumentMovementVO(
        String businessNo,
        InventoryBusinessType businessType,
        long ledgerCount,
        BigDecimal changeActualQuantity,
        BigDecimal changeAvailableQuantity,
        BigDecimal changeFrozenQuantity) {}
