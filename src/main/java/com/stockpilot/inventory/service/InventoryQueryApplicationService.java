package com.stockpilot.inventory.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stockpilot.inventory.domain.InventoryBalanceEntity;
import com.stockpilot.inventory.domain.InventoryLedgerEntity;
import com.stockpilot.inventory.mapper.InventoryBalanceMapper;
import com.stockpilot.inventory.mapper.InventoryLedgerMapper;
import com.stockpilot.inventory.request.InventoryBalancePageQuery;
import com.stockpilot.inventory.request.InventoryLedgerPageQuery;
import com.stockpilot.inventory.vo.InventoryBalanceVO;
import com.stockpilot.inventory.vo.InventoryLedgerVO;
import com.stockpilot.shared.api.PageResult;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InventoryQueryApplicationService {
    @Transactional(readOnly = true, timeout = 10)
    public PageResult<com.stockpilot.inventory.vo.InventoryDocumentMovementVO> periodDocuments(
            com.stockpilot.inventory.request.InventoryPeriodQuery query, long page, long size) {
        if (query == null) throw new IllegalArgumentException("Missing inventory period");
        com.stockpilot.inventory.request.InventoryDimensionQuery.validatePage(page, size);
        var result = ledgers.selectPeriodDocuments(Page.of(page, size), query);
        return new PageResult<>(
                result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Transactional(readOnly = true, timeout = 10)
    public com.stockpilot.inventory.vo.InventoryPeriodSummaryVO periodSummary(
            com.stockpilot.inventory.request.InventoryPeriodQuery query) {
        if (query == null) throw new IllegalArgumentException("Missing inventory period");
        var movements = java.util.List.copyOf(ledgers.selectPeriodTotals(query));
        java.math.BigDecimal actual = java.math.BigDecimal.ZERO;
        java.math.BigDecimal available = java.math.BigDecimal.ZERO;
        java.math.BigDecimal frozen = java.math.BigDecimal.ZERO;
        long count = 0;
        for (var movement : movements) {
            count = Math.addExact(count, movement.ledgerCount());
            actual = actual.add(movement.changeActualQuantity());
            available = available.add(movement.changeAvailableQuantity());
            frozen = frozen.add(movement.changeFrozenQuantity());
        }
        return new com.stockpilot.inventory.vo.InventoryPeriodSummaryVO(
                movements, count, actual, available, frozen);
    }

    public Optional<com.stockpilot.inventory.vo.InventoryWarehouseBalanceVO> dimensionTotals(
            com.stockpilot.inventory.request.InventoryDimensionQuery query) {
        if (query == null) throw new IllegalArgumentException("Missing inventory dimension");
        return Optional.ofNullable(balances.selectDimensionTotals(query));
    }

    public Optional<InventoryLedgerVO> findLedger(String ledgerNo) {
        if (ledgerNo == null || !ledgerNo.matches("[A-Za-z0-9_-]{2,64}"))
            throw new IllegalArgumentException("Invalid ledger number");
        return Optional.ofNullable(ledgers.selectByLedgerNo(ledgerNo))
                .map(InventoryViewConverter::ledger);
    }

    // Both reads share a short read-only transaction; aggregation covers all matching locations.
    public com.stockpilot.inventory.vo.InventoryBalanceOverviewVO balanceOverview(
            InventoryBalancePageQuery query) {
        if (query.getSkuId() == null
                || query.getSkuId() <= 0
                || query.getPage() < 1
                || query.getSize() < 1
                || query.getSize() > 20)
            throw new IllegalArgumentException("Restricted balance query");
        var page = balances.selectWarehouseTotals(Page.of(query.getPage(), query.getSize()), query);
        return new com.stockpilot.inventory.vo.InventoryBalanceOverviewVO(
                new PageResult<>(
                        page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()),
                pageBalances(query));
    }

    private final InventoryBalanceMapper balances;
    private final InventoryLedgerMapper ledgers;

    public InventoryQueryApplicationService(
            InventoryBalanceMapper balances, InventoryLedgerMapper ledgers) {
        this.balances = balances;
        this.ledgers = ledgers;
    }

    // 分页查询库存余额视图；余额是当前状态，不提供删除或通过查询接口修改的能力。
    public PageResult<InventoryBalanceVO> pageBalances(InventoryBalancePageQuery query) {
        IPage<InventoryBalanceEntity> page =
                balances.selectInventoryPage(Page.of(query.getPage(), query.getSize()), query);
        return new PageResult<>(
                page.getRecords().stream().map(InventoryViewConverter::balance).toList(),
                page.getTotal(),
                page.getCurrent(),
                page.getSize());
    }

    // 分页查询追加式库存流水，用于追溯每次业务动作的前值、差量、后值和版本链。
    public PageResult<InventoryLedgerVO> pageLedgers(InventoryLedgerPageQuery query) {
        if (query == null || !query.isPeriodValid())
            throw new IllegalArgumentException("Invalid ledger period");
        IPage<InventoryLedgerEntity> page =
                ledgers.selectInventoryPage(Page.of(query.getPage(), query.getSize()), query);
        return new PageResult<>(
                page.getRecords().stream().map(InventoryViewConverter::ledger).toList(),
                page.getTotal(),
                page.getCurrent(),
                page.getSize());
    }

    // 按唯一库存维度读取最新余额；安全库存消费者也通过此入口查询 MySQL 权威数据。
    public Optional<InventoryBalanceVO> findBalance(long warehouseId, long locationId, long skuId) {
        InventoryBalanceEntity balance = balances.selectByDimension(warehouseId, locationId, skuId);
        return Optional.ofNullable(balance).map(InventoryViewConverter::balance);
    }
}
