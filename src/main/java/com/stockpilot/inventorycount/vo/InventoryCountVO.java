package com.stockpilot.inventorycount.vo;

import com.stockpilot.inventorycount.domain.InventoryCountStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record InventoryCountVO(Long id,String countNo,Long warehouseId,InventoryCountStatus status,String remark,
    Integer version,LocalDateTime createdAt,LocalDateTime updatedAt,List<Line> lines) {
    public record Line(Long id,Integer lineNo,Long locationId,Long skuId,
        BigDecimal snapshotActualQuantity,BigDecimal snapshotAvailableQuantity,BigDecimal snapshotFrozenQuantity,
        Integer snapshotBalanceVersion,BigDecimal countedQuantity,BigDecimal differenceQuantity,String reason){}
}
