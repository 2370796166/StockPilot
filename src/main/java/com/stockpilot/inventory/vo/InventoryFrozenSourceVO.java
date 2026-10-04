package com.stockpilot.inventory.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Public inventory read contract implemented by the modules that own reservations. */
public record InventoryFrozenSourceVO(
        String documentType,
        String businessNo,
        long warehouseId,
        long locationId,
        long skuId,
        String status,
        BigDecimal quantity,
        LocalDateTime reservedAt) {}
