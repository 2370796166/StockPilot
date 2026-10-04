package com.stockpilot.inventory.vo;

import com.stockpilot.inventory.domain.InventoryBusinessType;
import java.math.BigDecimal;

/** All matching ledger rows, grouped by business action rather than a sampled page. */
public record InventoryMovementTotalVO(
        InventoryBusinessType businessType,
        long ledgerCount,
        BigDecimal changeActualQuantity,
        BigDecimal changeAvailableQuantity,
        BigDecimal changeFrozenQuantity) {}
