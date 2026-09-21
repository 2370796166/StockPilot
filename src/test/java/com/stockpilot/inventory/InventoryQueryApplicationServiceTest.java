package com.stockpilot.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stockpilot.inventory.domain.InventoryBalanceEntity;
import com.stockpilot.inventory.domain.InventoryBusinessType;
import com.stockpilot.inventory.domain.InventoryLedgerEntity;
import com.stockpilot.inventory.mapper.InventoryBalanceMapper;
import com.stockpilot.inventory.mapper.InventoryLedgerMapper;
import com.stockpilot.inventory.request.InventoryBalancePageQuery;
import com.stockpilot.inventory.request.InventoryLedgerPageQuery;
import com.stockpilot.inventory.service.InventoryQueryApplicationService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class InventoryQueryApplicationServiceTest {
    @Test
    void balanceAndLedgerQueriesReturnPageMetadata() {
        InventoryBalanceMapper balances = mock(InventoryBalanceMapper.class);
        InventoryLedgerMapper ledgers = mock(InventoryLedgerMapper.class);
        InventoryQueryApplicationService service =
                new InventoryQueryApplicationService(balances, ledgers);

        InventoryBalancePageQuery balanceQuery = new InventoryBalancePageQuery();
        balanceQuery.setPage(2);
        balanceQuery.setSize(10);
        Page<InventoryBalanceEntity> balancePage = Page.of(2, 10, 21);
        balancePage.setRecords(List.of(balance()));
        when(balances.selectInventoryPage(any(), eq(balanceQuery))).thenReturn(balancePage);

        InventoryLedgerPageQuery ledgerQuery = new InventoryLedgerPageQuery();
        Page<InventoryLedgerEntity> ledgerPage = Page.of(1, 20, 1);
        ledgerPage.setRecords(List.of(ledger()));
        when(ledgers.selectInventoryPage(any(), eq(ledgerQuery))).thenReturn(ledgerPage);

        var balancesResult = service.pageBalances(balanceQuery);
        var ledgersResult = service.pageLedgers(ledgerQuery);

        assertEquals(21, balancesResult.total());
        assertEquals(2, balancesResult.page());
        assertEquals(10, balancesResult.size());
        assertEquals(1, balancesResult.records().size());
        assertEquals(1, ledgersResult.total());
        assertEquals(
                InventoryBusinessType.INITIALIZE, ledgersResult.records().get(0).businessType());
    }

    private InventoryBalanceEntity balance() {
        InventoryBalanceEntity entity = new InventoryBalanceEntity();
        entity.setId(1L);
        entity.setWarehouseId(1L);
        entity.setLocationId(2L);
        entity.setSkuId(3L);
        entity.setActualQuantity(new BigDecimal("5.0000"));
        entity.setAvailableQuantity(new BigDecimal("4.0000"));
        entity.setFrozenQuantity(new BigDecimal("1.0000"));
        entity.setVersion(2);
        return entity;
    }

    private InventoryLedgerEntity ledger() {
        BigDecimal zero = new BigDecimal("0.0000");
        InventoryLedgerEntity entity = new InventoryLedgerEntity();
        entity.setId(1L);
        entity.setLedgerNo("IL-1");
        entity.setBusinessType(InventoryBusinessType.INITIALIZE);
        entity.setBusinessNo("INIT-1-2-3");
        entity.setWarehouseId(1L);
        entity.setLocationId(2L);
        entity.setSkuId(3L);
        entity.setBeforeActualQuantity(zero);
        entity.setChangeActualQuantity(zero);
        entity.setAfterActualQuantity(zero);
        entity.setBeforeAvailableQuantity(zero);
        entity.setChangeAvailableQuantity(zero);
        entity.setAfterAvailableQuantity(zero);
        entity.setBeforeFrozenQuantity(zero);
        entity.setChangeFrozenQuantity(zero);
        entity.setAfterFrozenQuantity(zero);
        entity.setBalanceVersionBefore(0);
        entity.setBalanceVersionAfter(0);
        entity.setOperatorName("system");
        return entity;
    }
}
