package com.stockpilot.reconciliation.vo;

import com.stockpilot.inventory.vo.InventoryAuditVO;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record InventoryReconciliationVO(
        long warehouseId,
        long locationId,
        long skuId,
        Instant checkedAt,
        InventoryAuditVO.Snapshot inventory,
        BigDecimal frozenSourceQuantity,
        int checkedTransfers,
        List<Issue> issues) {
    public record Issue(String code, String businessNo, String description) {}
}
