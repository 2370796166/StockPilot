package com.stockpilot.inventory.vo;

import java.math.BigDecimal;

/** Authoritative totals across every matching location, grouped by warehouse for one SKU. */
public record InventoryWarehouseBalanceVO(
        Long warehouseId,
        BigDecimal actualQuantity,
        BigDecimal availableQuantity,
        BigDecimal frozenQuantity) {}
