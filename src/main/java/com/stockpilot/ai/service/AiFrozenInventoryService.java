package com.stockpilot.ai.service;

import com.stockpilot.inventory.request.InventoryDimensionQuery;
import com.stockpilot.inventory.service.InventoryQueryApplicationService;
import com.stockpilot.inventory.vo.InventoryFrozenSourcePageVO;
import com.stockpilot.inventory.vo.InventoryWarehouseBalanceVO;
import com.stockpilot.sales.service.SalesOutboundApplicationService;
import com.stockpilot.transfer.service.StockTransferApplicationService;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Short MySQL snapshot for total reconciliation; contains no model/network request. */
@Service
public class AiFrozenInventoryService {
    private final InventoryQueryApplicationService inventoryQueryService;
    private final SalesOutboundApplicationService salesOutboundService;
    private final StockTransferApplicationService stockTransferService;

    public AiFrozenInventoryService(
            InventoryQueryApplicationService inventoryQueryService,
            SalesOutboundApplicationService salesOutboundService,
            StockTransferApplicationService stockTransferService) {
        this.inventoryQueryService = inventoryQueryService;
        this.salesOutboundService = salesOutboundService;
        this.stockTransferService = stockTransferService;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 10)
    public Snapshot query(InventoryDimensionQuery query, long page, long size) {
        if (query == null) throw new IllegalArgumentException("Missing inventory scope");
        InventoryDimensionQuery.validatePage(page, size);
        var balance = inventoryQueryService.dimensionTotals(query);
        var sales = salesOutboundService.frozenSources(query, page, size);
        var transfer = stockTransferService.frozenSources(query, page, size);
        BigDecimal sourceQuantity = sales.totalQuantity().add(transfer.totalQuantity());
        BigDecimal difference =
                balance.map(v -> v.frozenQuantity().subtract(sourceQuantity)).orElse(null);
        return new Snapshot(balance, sales, transfer, sourceQuantity, difference);
    }

    public record Snapshot(
            Optional<InventoryWarehouseBalanceVO> balance,
            InventoryFrozenSourcePageVO sales,
            InventoryFrozenSourcePageVO transfer,
            BigDecimal sourceQuantity,
            BigDecimal differenceQuantity) {}
}
