package com.stockpilot.reconciliation.service;

import com.stockpilot.inventory.request.InventoryDimensionQuery;
import com.stockpilot.inventory.service.InventoryAuditQueryService;
import com.stockpilot.reconciliation.vo.InventoryReconciliationVO;
import com.stockpilot.sales.service.SalesOutboundApplicationService;
import com.stockpilot.shared.api.CommonErrorCode;
import com.stockpilot.shared.exception.BusinessException;
import com.stockpilot.transfer.service.StockTransferApplicationService;
import com.stockpilot.transfer.service.StockTransferAuditQueryService;
import java.time.Instant;
import java.util.ArrayList;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryReconciliationService {
    private final InventoryAuditQueryService inventoryAuditQueryService;
    private final SalesOutboundApplicationService salesOutboundService;
    private final StockTransferApplicationService stockTransferService;
    private final StockTransferAuditQueryService stockTransferAuditQueryService;

    public InventoryReconciliationService(
            InventoryAuditQueryService inventoryAuditQueryService,
            SalesOutboundApplicationService salesOutboundService,
            StockTransferApplicationService stockTransferService,
            StockTransferAuditQueryService stockTransferAuditQueryService) {
        this.inventoryAuditQueryService = inventoryAuditQueryService;
        this.salesOutboundService = salesOutboundService;
        this.stockTransferService = stockTransferService;
        this.stockTransferAuditQueryService = stockTransferAuditQueryService;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 10)
    public InventoryReconciliationVO check(long warehouseId, long locationId, long skuId) {
        com.stockpilot.shared.auth.WarehouseAccess.requireAll();
        var scope = new InventoryDimensionQuery(skuId, warehouseId, locationId);
        var facts = inventoryAuditQueryService.snapshot(warehouseId, locationId, skuId);
        var sources =
                salesOutboundService
                        .frozenSources(scope, 1, 1)
                        .totalQuantity()
                        .add(stockTransferService.frozenSources(scope, 1, 1).totalQuantity());
        var transfers = stockTransferAuditQueryService.audit(warehouseId, locationId, skuId);
        if (transfers.size() > 200)
            throw new BusinessException(
                    CommonErrorCode.BUSINESS_CONFLICT, "该维度调拨记录超过200条，请使用离线对账，在线结果未生成");
        var issues = new ArrayList<InventoryReconciliationVO.Issue>();
        var balance = facts.balance();
        var summary = facts.summary();
        var latest = facts.latest();
        if (balance == null) {
            issues.add(
                    new InventoryReconciliationVO.Issue(
                            "BALANCE_MISSING", null, "该维度没有库存余额，不能确认账实一致"));
        } else {
            if (balance.actualQuantity()
                                    .compareTo(
                                            balance.availableQuantity()
                                                    .add(balance.frozenQuantity()))
                            != 0
                    || balance.actualQuantity().signum() < 0
                    || balance.availableQuantity().signum() < 0
                    || balance.frozenQuantity().signum() < 0)
                issues.add(new InventoryReconciliationVO.Issue("INVARIANT", null, "三数量不变量不成立"));
            if (balance.actualQuantity().compareTo(summary.actualChange()) != 0
                    || balance.availableQuantity().compareTo(summary.availableChange()) != 0
                    || balance.frozenQuantity().compareTo(summary.frozenChange()) != 0)
                issues.add(
                        new InventoryReconciliationVO.Issue(
                                "LEDGER_TOTAL", null, "完整流水累计与当前余额不一致"));
            if (latest == null)
                issues.add(
                        new InventoryReconciliationVO.Issue("LEDGER_MISSING", null, "余额没有对应流水证据"));
            else if (!balance.version().equals(latest.balanceVersionAfter())
                    || balance.actualQuantity().compareTo(latest.afterActualQuantity()) != 0
                    || balance.availableQuantity().compareTo(latest.afterAvailableQuantity()) != 0
                    || balance.frozenQuantity().compareTo(latest.afterFrozenQuantity()) != 0)
                issues.add(
                        new InventoryReconciliationVO.Issue(
                                "LEDGER_TAIL", latest.businessNo(), "流水末值或版本与余额不一致"));
            if (balance.frozenQuantity().compareTo(sources) != 0)
                issues.add(
                        new InventoryReconciliationVO.Issue(
                                "FROZEN_SOURCE", null, "有效销售和调拨占用总量与该库位冻结量不一致"));
        }
        if (summary.brokenLinks() > 0)
            issues.add(
                    new InventoryReconciliationVO.Issue(
                            "LEDGER_CHAIN",
                            null,
                            "存在" + summary.brokenLinks() + "处流水起点或前后值/版本链断点"));
        for (var t : transfers) {
            boolean valid =
                    t.outboundQuantity() != null
                            && t.outboundQuantity().compareTo(t.quantity()) == 0
                            && t.outboundQuantity()
                                            .compareTo(
                                                    t.inTransitQuantity().add(t.receivedQuantity()))
                                    == 0;
            valid =
                    valid
                            && ("COMPLETED".equals(t.orderStatus())
                                    ? "RECEIVED".equals(t.transitStatus())
                                            && t.inTransitQuantity().signum() == 0
                                            && t.receivedQuantity().compareTo(t.quantity()) == 0
                                    : "IN_TRANSIT".equals(t.transitStatus())
                                            && t.receivedQuantity().signum() == 0
                                            && t.inTransitQuantity().compareTo(t.quantity()) == 0);
            var out =
                    inventoryAuditQueryService.documentQuantity(
                            t.transferNo(),
                            "TRANSFER_OUT",
                            t.sourceWarehouseId(),
                            t.sourceLocationId(),
                            skuId);
            var in =
                    inventoryAuditQueryService.documentQuantity(
                            t.transferNo(),
                            "TRANSFER_IN",
                            t.targetWarehouseId(),
                            t.targetLocationId(),
                            skuId);
            valid =
                    valid
                            && out.ledgerCount() == 1
                            && out.actualChange().compareTo(t.quantity().negate()) == 0
                            && ("COMPLETED".equals(t.orderStatus())
                                    ? in.ledgerCount() == 1
                                            && in.actualChange().compareTo(t.quantity()) == 0
                                    : in.ledgerCount() == 0);
            if (!valid)
                issues.add(
                        new InventoryReconciliationVO.Issue(
                                "TRANSFER_FACT", t.transferNo(), "调拨单、在途事实与两端流水不一致"));
        }
        return new InventoryReconciliationVO(
                warehouseId,
                locationId,
                skuId,
                Instant.now(),
                facts,
                sources,
                transfers.size(),
                issues);
    }
}
