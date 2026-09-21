package com.stockpilot.inventory.count.vo;

import com.stockpilot.inventory.count.domain.InventoryCountStatus;
import java.time.LocalDateTime;

public record InventoryCountSummaryVO(
        Long id,
        String countNo,
        Long warehouseId,
        InventoryCountStatus status,
        String remark,
        Integer version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
