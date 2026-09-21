package com.stockpilot.inventory.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InventoryBalanceVO(
        Long id,
        Long warehouseId,
        Long locationId,
        Long skuId,
        BigDecimal actualQuantity,
        BigDecimal availableQuantity,
        BigDecimal frozenQuantity,
        Integer version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
