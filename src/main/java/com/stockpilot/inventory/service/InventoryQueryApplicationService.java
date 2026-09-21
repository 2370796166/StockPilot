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
import com.stockpilot.masterdata.vo.PageResult;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InventoryQueryApplicationService {
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
