package com.stockpilot.inventory.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stockpilot.inventory.domain.InventoryBalanceEntity;
import com.stockpilot.inventory.domain.InventoryLedgerEntity;
import com.stockpilot.inventory.infrastructure.mapper.InventoryBalanceMapper;
import com.stockpilot.inventory.infrastructure.mapper.InventoryLedgerMapper;
import com.stockpilot.inventory.request.InventoryBalancePageQuery;
import com.stockpilot.inventory.request.InventoryLedgerPageQuery;
import com.stockpilot.inventory.vo.InventoryBalanceVO;
import com.stockpilot.inventory.vo.InventoryLedgerVO;
import com.stockpilot.masterdata.vo.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class InventoryQueryApplicationService {
    private final InventoryBalanceMapper balances;
    private final InventoryLedgerMapper ledgers;

    public InventoryQueryApplicationService(InventoryBalanceMapper balances, InventoryLedgerMapper ledgers) {
        this.balances = balances;
        this.ledgers = ledgers;
    }

    public PageResult<InventoryBalanceVO> pageBalances(InventoryBalancePageQuery query) {
        IPage<InventoryBalanceEntity> page = balances.selectInventoryPage(
                Page.of(query.getPage(), query.getSize()), query);
        return new PageResult<>(page.getRecords().stream().map(InventoryViewConverter::balance).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    public PageResult<InventoryLedgerVO> pageLedgers(InventoryLedgerPageQuery query) {
        IPage<InventoryLedgerEntity> page = ledgers.selectInventoryPage(
                Page.of(query.getPage(), query.getSize()), query);
        return new PageResult<>(page.getRecords().stream().map(InventoryViewConverter::ledger).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    public Optional<InventoryBalanceVO> findBalance(long warehouseId, long locationId, long skuId) {
        InventoryBalanceEntity balance = balances.selectByDimension(warehouseId, locationId, skuId);
        return Optional.ofNullable(balance).map(InventoryViewConverter::balance);
    }
}
