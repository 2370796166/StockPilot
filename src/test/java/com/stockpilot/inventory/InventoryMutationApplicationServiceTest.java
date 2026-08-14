package com.stockpilot.inventory;

import com.stockpilot.common.exception.BusinessException;
import com.stockpilot.inventory.application.InitializeInventoryCommand;
import com.stockpilot.inventory.application.InventoryMutationApplicationService;
import com.stockpilot.inventory.domain.InventoryBalanceEntity;
import com.stockpilot.inventory.domain.InventoryBusinessType;
import com.stockpilot.inventory.domain.InventoryLedgerEntity;
import com.stockpilot.inventory.domain.InventoryQuantityChange;
import com.stockpilot.inventory.application.InventoryChangeCommand;
import com.stockpilot.inventory.infrastructure.mapper.InventoryBalanceMapper;
import com.stockpilot.inventory.infrastructure.mapper.InventoryLedgerMapper;
import com.stockpilot.masterdata.application.MasterDataReferenceApplicationService;
import com.stockpilot.masterdata.domain.MasterDataStatus;
import com.stockpilot.masterdata.location.domain.WarehouseLocationEntity;
import com.stockpilot.masterdata.location.infrastructure.mapper.WarehouseLocationMapper;
import com.stockpilot.masterdata.sku.domain.SkuEntity;
import com.stockpilot.masterdata.sku.infrastructure.mapper.SkuMapper;
import com.stockpilot.masterdata.warehouse.domain.WarehouseEntity;
import com.stockpilot.masterdata.warehouse.infrastructure.mapper.WarehouseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class InventoryMutationApplicationServiceTest {
    private InventoryBalanceMapper balances;
    private InventoryLedgerMapper ledgers;
    private MasterDataReferenceApplicationService masterData;
    private InventoryMutationApplicationService service;

    @BeforeEach
    void setUp() {
        balances = mock(InventoryBalanceMapper.class);
        ledgers = mock(InventoryLedgerMapper.class);
        WarehouseMapper warehouses = mock(WarehouseMapper.class);
        WarehouseLocationMapper locations = mock(WarehouseLocationMapper.class);
        SkuMapper skus = mock(SkuMapper.class);
        masterData = new MasterDataReferenceApplicationService(warehouses, locations, skus);
        WarehouseEntity warehouse = new WarehouseEntity();
        warehouse.setId(1L);
        warehouse.setStatus(MasterDataStatus.ENABLED);
        WarehouseLocationEntity location = new WarehouseLocationEntity();
        location.setId(2L);
        location.setWarehouseId(1L);
        location.setStatus(MasterDataStatus.ENABLED);
        SkuEntity sku = new SkuEntity();
        sku.setId(3L);
        sku.setStatus(MasterDataStatus.ENABLED);
        when(warehouses.selectById(1L)).thenReturn(warehouse);
        when(locations.selectById(2L)).thenReturn(location);
        when(skus.selectById(3L)).thenReturn(sku);
        service = new InventoryMutationApplicationService(balances, ledgers, masterData);
    }

    @Test
    void createsInitialZeroBalanceAndImmutableLedgerTogether() {
        when(balances.insert(any())).thenAnswer(invocation -> {
            InventoryBalanceEntity entity = invocation.getArgument(0);
            entity.setId(9L);
            return 1;
        });
        when(ledgers.insert(any())).thenReturn(1);
        when(balances.selectById(9L)).thenAnswer(invocation -> savedZeroBalance());

        var result = service.initializeZeroBalance(new InitializeInventoryCommand(1, 2, 3, 7L, "alice"));

        assertEquals(new BigDecimal("0.0000"), result.actualQuantity());
        assertEquals(new BigDecimal("0.0000"), result.availableQuantity());
        assertEquals(new BigDecimal("0.0000"), result.frozenQuantity());
        assertEquals(0, result.version());
        verify(ledgers).insert(org.mockito.ArgumentMatchers.argThat(ledger -> validInitializationLedger(ledger)));
    }

    @Test
    void duplicateDimensionIsRejected() {
        doThrow(new DuplicateKeyException("uk_inventory_balance_dimension"))
                .when(balances).insert(any());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.initializeZeroBalance(new InitializeInventoryCommand(1, 2, 3, 7L, "alice")));

        assertEquals("INVENTORY_409_DUPLICATE", exception.getErrorCode().code());
    }

    @Test
    void changeUsesExpectedVersionAndWritesCompleteLedger() {
        InventoryBalanceEntity current = balance(9L, "10.0000", "8.0000", "2.0000", 4);
        InventoryBalanceEntity saved = balance(9L, "10.0000", "5.0000", "5.0000", 5);
        when(balances.selectByDimension(1, 2, 3)).thenReturn(current);
        when(balances.updateStateIfVersionMatches(eq(9L), eq(4), any())).thenReturn(1);
        when(ledgers.insert(any())).thenReturn(1);
        when(balances.selectById(9L)).thenReturn(saved);

        var result = service.applyChange(new InventoryChangeCommand(
                1, 2, 3, 4, InventoryBusinessType.OUTBOUND_FREEZE, "VERIFY-001",
                InventoryQuantityChange.freeze(new BigDecimal("3.0000")), 7L, "alice"));

        assertEquals(5, result.version());
        assertEquals(new BigDecimal("5.0000"), result.availableQuantity());
        verify(balances).updateStateIfVersionMatches(eq(9L), eq(4),
                org.mockito.ArgumentMatchers.argThat(state ->
                        new BigDecimal("10.0000").equals(state.actualQuantity())
                                && new BigDecimal("5.0000").equals(state.availableQuantity())
                                && new BigDecimal("5.0000").equals(state.frozenQuantity())));
        verify(ledgers).insert(org.mockito.ArgumentMatchers.argThat(ledger ->
                ledger.getBalanceVersionBefore() == 4
                        && ledger.getBalanceVersionAfter() == 5
                        && new BigDecimal("8.0000").equals(ledger.getBeforeAvailableQuantity())
                        && new BigDecimal("-3.0000").equals(ledger.getChangeAvailableQuantity())
                        && new BigDecimal("5.0000").equals(ledger.getAfterAvailableQuantity())));
    }

    @Test
    void staleVersionCannotWriteLedger() {
        when(balances.selectByDimension(1, 2, 3))
                .thenReturn(balance(9L, "10.0000", "8.0000", "2.0000", 4));
        when(balances.updateStateIfVersionMatches(eq(9L), eq(3), any())).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.applyChange(new InventoryChangeCommand(
                        1, 2, 3, 3, InventoryBusinessType.OUTBOUND_FREEZE, "VERIFY-002",
                        InventoryQuantityChange.freeze(new BigDecimal("1.0000")), 7L, "alice")));

        assertEquals("INVENTORY_409_CONCURRENT", exception.getErrorCode().code());
        verify(ledgers, never()).insert(any());
    }

    private InventoryBalanceEntity savedZeroBalance() {
        return balance(9L, "0.0000", "0.0000", "0.0000", 0);
    }

    private InventoryBalanceEntity balance(
            long id, String actual, String available, String frozen, int version) {
        InventoryBalanceEntity entity = new InventoryBalanceEntity();
        entity.setId(id);
        entity.setWarehouseId(1L);
        entity.setLocationId(2L);
        entity.setSkuId(3L);
        entity.setActualQuantity(new BigDecimal(actual));
        entity.setAvailableQuantity(new BigDecimal(available));
        entity.setFrozenQuantity(new BigDecimal(frozen));
        entity.setVersion(version);
        return entity;
    }

    private boolean validInitializationLedger(InventoryLedgerEntity ledger) {
        return ledger.getBusinessType() == InventoryBusinessType.INITIALIZE
                && "INIT-1-2-3".equals(ledger.getBusinessNo())
                && ledger.getLedgerNo().startsWith("IL-")
                && BigDecimal.ZERO.compareTo(ledger.getChangeActualQuantity()) == 0
                && BigDecimal.ZERO.compareTo(ledger.getChangeAvailableQuantity()) == 0
                && BigDecimal.ZERO.compareTo(ledger.getChangeFrozenQuantity()) == 0
                && Long.valueOf(7L).equals(ledger.getOperatorId())
                && "alice".equals(ledger.getOperatorName());
    }
}
