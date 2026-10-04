package com.stockpilot.inventory.vo;

import java.math.BigDecimal;
import java.util.List;

public record InventoryPeriodSummaryVO(
        List<InventoryMovementTotalVO> movements,
        long ledgerCount,
        BigDecimal changeActualQuantity,
        BigDecimal changeAvailableQuantity,
        BigDecimal changeFrozenQuantity) {}
