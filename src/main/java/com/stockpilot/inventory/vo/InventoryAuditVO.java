package com.stockpilot.inventory.vo;

import java.math.BigDecimal;

public final class InventoryAuditVO {
    private InventoryAuditVO() {}

    public record LedgerSummary(
            long ledgerCount,
            long brokenLinks,
            BigDecimal actualChange,
            BigDecimal availableChange,
            BigDecimal frozenChange) {}

    public record DocumentQuantity(long ledgerCount, BigDecimal actualChange) {}

    public record Snapshot(
            InventoryBalanceVO balance, LedgerSummary summary, InventoryLedgerVO latest) {}
}
