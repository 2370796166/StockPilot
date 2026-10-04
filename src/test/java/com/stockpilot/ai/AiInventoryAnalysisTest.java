package com.stockpilot.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.stockpilot.ai.service.AiFrozenInventoryService;
import com.stockpilot.inventory.domain.InventoryBusinessType;
import com.stockpilot.inventory.mapper.*;
import com.stockpilot.inventory.request.*;
import com.stockpilot.inventory.service.InventoryQueryApplicationService;
import com.stockpilot.inventory.vo.*;
import com.stockpilot.sales.service.SalesOutboundApplicationService;
import com.stockpilot.shared.api.PageResult;
import com.stockpilot.transfer.service.StockTransferApplicationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class AiInventoryAnalysisTest {
    InventoryBalanceMapper balances = mock(InventoryBalanceMapper.class);
    InventoryLedgerMapper ledgers = mock(InventoryLedgerMapper.class);
    InventoryQueryApplicationService inventory =
            new InventoryQueryApplicationService(balances, ledgers);
    InventoryDimensionQuery dimension = new InventoryDimensionQuery(1, 2, 3L);

    @Test
    void invalidDimensionsAndPeriodsAreRejectedBeforeSql() {
        assertThrows(IllegalArgumentException.class, () -> new InventoryDimensionQuery(0, 2, null));
        assertThrows(IllegalArgumentException.class, () -> new InventoryDimensionQuery(1, 2, -1L));
        var start = LocalDate.of(2026, 1, 1);
        assertThrows(
                IllegalArgumentException.class,
                () -> new InventoryPeriodQuery(dimension, start, start.minusDays(1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new InventoryPeriodQuery(dimension, start, start.plusDays(92)));
        assertThrows(IllegalArgumentException.class, () -> inventory.periodSummary(null));
        assertThrows(IllegalArgumentException.class, () -> inventory.dimensionTotals(null));
        var page = new InventoryLedgerPageQuery();
        page.setStartDate(start);
        assertFalse(page.isPeriodValid());
        assertThrows(IllegalArgumentException.class, () -> inventory.pageLedgers(page));
        page.setEndDate(start.plusDays(91));
        assertTrue(page.isPeriodValid());
        page.setEndDate(start.plusDays(92));
        assertFalse(page.isPeriodValid());
        verifyNoInteractions(balances, ledgers);
    }

    @Test
    void inclusiveCalendarRangeUsesHalfOpenMidnightBoundaries() {
        var start = LocalDate.of(2026, 1, 1);
        var query = new InventoryPeriodQuery(dimension, start, start.plusDays(91));
        assertEquals(start.atStartOfDay(), query.startInclusive());
        assertEquals(start.plusDays(92).atStartOfDay(), query.endExclusive());
    }

    @Test
    void fullActionTotalsPreservePrecisionAndIncludeLossAndTransferSeparately() {
        var query =
                new InventoryPeriodQuery(
                        dimension, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3));
        var exact = new BigDecimal("999999999999999.1234");
        when(ledgers.selectPeriodTotals(query))
                .thenReturn(
                        List.of(
                                new InventoryMovementTotalVO(
                                        InventoryBusinessType.PURCHASE_RECEIPT,
                                        30,
                                        exact,
                                        exact,
                                        BigDecimal.ZERO),
                                new InventoryMovementTotalVO(
                                        InventoryBusinessType.OUTBOUND_FREEZE,
                                        40,
                                        BigDecimal.ZERO,
                                        new BigDecimal("-12.1234"),
                                        new BigDecimal("12.1234")),
                                new InventoryMovementTotalVO(
                                        InventoryBusinessType.TRANSFER_OUT,
                                        50,
                                        new BigDecimal("-10.0001"),
                                        BigDecimal.ZERO,
                                        new BigDecimal("-10.0001")),
                                new InventoryMovementTotalVO(
                                        InventoryBusinessType.INVENTORY_LOSS,
                                        60,
                                        new BigDecimal("-2.0001"),
                                        new BigDecimal("-2.0001"),
                                        BigDecimal.ZERO)));
        var summary = inventory.periodSummary(query);
        assertEquals(180, summary.ledgerCount());
        assertEquals(new BigDecimal("999999999999987.1232"), summary.changeActualQuantity());
        assertEquals(new BigDecimal("999999999999984.9999"), summary.changeAvailableQuantity());
        assertEquals(new BigDecimal("2.1233"), summary.changeFrozenQuantity());
        assertEquals(4, summary.movements().size());
        verify(ledgers).selectPeriodTotals(query);
        verifyNoMoreInteractions(ledgers);
        verifyNoInteractions(balances);
    }

    @Test
    void noLedgerRowsIsAnEmptySummaryButDatabaseFailurePropagates() {
        var query =
                new InventoryPeriodQuery(
                        dimension, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3));
        when(ledgers.selectPeriodTotals(query)).thenReturn(List.of());
        assertEquals(0, inventory.periodSummary(query).ledgerCount());
        when(ledgers.selectPeriodTotals(query))
                .thenThrow(new IllegalStateException("database unavailable"));
        assertThrows(IllegalStateException.class, () -> inventory.periodSummary(query));
    }

    @Test
    void reconciliationUsesFullTotalsAndMissingBalanceIsNotZero() {
        var queryService = mock(InventoryQueryApplicationService.class);
        var sales = mock(SalesOutboundApplicationService.class);
        var transfer = mock(StockTransferApplicationService.class);
        var frozen = new AiFrozenInventoryService(queryService, sales, transfer);
        when(queryService.dimensionTotals(dimension))
                .thenReturn(
                        Optional.of(
                                new InventoryWarehouseBalanceVO(
                                        2L,
                                        new BigDecimal("200"),
                                        new BigDecimal("79.8766"),
                                        new BigDecimal("120.1234"))));
        when(sales.frozenSources(dimension, 1, 1))
                .thenReturn(
                        new InventoryFrozenSourcePageVO(
                                new BigDecimal("100.1234"),
                                new PageResult<>(List.of(), 100, 1, 1)));
        when(transfer.frozenSources(dimension, 1, 1))
                .thenReturn(
                        new InventoryFrozenSourcePageVO(
                                new BigDecimal("20.0000"), new PageResult<>(List.of(), 2, 1, 1)));
        var result = frozen.query(dimension, 1, 1);
        assertEquals(new BigDecimal("120.1234"), result.sourceQuantity());
        assertEquals(0, result.differenceQuantity().signum());
        when(queryService.dimensionTotals(dimension)).thenReturn(Optional.empty());
        assertNull(frozen.query(dimension, 1, 1).differenceQuantity());
        assertThrows(IllegalArgumentException.class, () -> frozen.query(dimension, 0, 1));
        when(transfer.frozenSources(dimension, 1, 1))
                .thenThrow(new IllegalStateException("query failed"));
        assertThrows(IllegalStateException.class, () -> frozen.query(dimension, 1, 1));
    }
}
