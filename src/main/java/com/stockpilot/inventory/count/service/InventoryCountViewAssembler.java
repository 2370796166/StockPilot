package com.stockpilot.inventory.count.service;

import com.stockpilot.inventory.count.domain.InventoryCountEntity;
import com.stockpilot.inventory.count.domain.InventoryCountLineEntity;
import com.stockpilot.inventory.count.vo.InventoryCountSummaryVO;
import com.stockpilot.inventory.count.vo.InventoryCountVO;
import java.util.List;

final class InventoryCountViewAssembler {
    private InventoryCountViewAssembler() {}

    static InventoryCountVO detail(
            InventoryCountEntity count, List<InventoryCountLineEntity> countLines) {
        return new InventoryCountVO(
                count.getId(),
                count.getCountNo(),
                count.getWarehouseId(),
                count.getStatus(),
                count.getRemark(),
                count.getVersion(),
                count.getCreatedAt(),
                count.getUpdatedAt(),
                countLines.stream()
                        .map(
                                line ->
                                        new InventoryCountVO.Line(
                                                line.getId(),
                                                line.getLineNo(),
                                                line.getLocationId(),
                                                line.getSkuId(),
                                                line.getSnapshotActualQuantity(),
                                                line.getSnapshotAvailableQuantity(),
                                                line.getSnapshotFrozenQuantity(),
                                                line.getSnapshotBalanceVersion(),
                                                line.getCountedQuantity(),
                                                line.getDifferenceQuantity(),
                                                line.getReason()))
                        .toList());
    }

    static InventoryCountSummaryVO summary(InventoryCountEntity count) {
        return new InventoryCountSummaryVO(
                count.getId(),
                count.getCountNo(),
                count.getWarehouseId(),
                count.getStatus(),
                count.getRemark(),
                count.getVersion(),
                count.getCreatedAt(),
                count.getUpdatedAt());
    }
}
