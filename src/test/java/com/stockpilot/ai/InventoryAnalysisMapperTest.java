package com.stockpilot.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.stockpilot.inventory.mapper.InventoryBalanceMapper;
import com.stockpilot.inventory.mapper.InventoryLedgerMapper;
import com.stockpilot.inventory.request.*;
import com.stockpilot.sales.mapper.SalesOutboundMapper;
import com.stockpilot.transfer.mapper.StockTransferMapper;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Parses actual annotation SQL and verifies binding; does not execute it on MySQL. */
class InventoryAnalysisMapperTest {
    @Test
    void recordDatesAndDimensionsBindThroughMybatisWithoutStringSqlInterpolation() {
        var configuration = new MybatisConfiguration();
        configuration.addMapper(InventoryLedgerMapper.class);
        var dimension = new InventoryDimensionQuery(7, 8, 9L);
        var query =
                new InventoryPeriodQuery(
                        dimension, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3));
        var parameters = Map.of("query", query);
        var bound =
                configuration
                        .getMappedStatement(
                                InventoryLedgerMapper.class.getName() + ".selectPeriodTotals")
                        .getBoundSql(parameters);
        assertFalse(bound.getSql().contains("2026-10-01"));
        assertTrue(bound.getSql().contains("location_id = ?"));
        var values =
                bound.getParameterMappings().stream()
                        .map(p -> configuration.newMetaObject(parameters).getValue(p.getProperty()))
                        .toList();
        assertEquals(
                java.util.List.of(7L, 8L, query.startInclusive(), query.endExclusive(), 9L),
                values);
        var warehouseQuery =
                new InventoryPeriodQuery(
                        new InventoryDimensionQuery(7, 8, null),
                        query.startDate(),
                        query.endDate());
        assertEquals(
                4,
                configuration
                        .getMappedStatement(
                                InventoryLedgerMapper.class.getName() + ".selectPeriodTotals")
                        .getBoundSql(Map.of("query", warehouseQuery))
                        .getParameterMappings()
                        .size());
    }

    @Test
    void activeReservationQueriesParseOptionalLocationAndKeepAuthoritativeStateFilters() {
        var configuration = new MybatisConfiguration();
        configuration.addMapper(SalesOutboundMapper.class);
        configuration.addMapper(StockTransferMapper.class);
        configuration.addMapper(InventoryBalanceMapper.class);
        var parameters = Map.of("query", new InventoryDimensionQuery(7, 8, null));
        var sales =
                configuration
                        .getMappedStatement(
                                SalesOutboundMapper.class.getName() + ".selectFrozenTotal")
                        .getBoundSql(parameters);
        var transfer =
                configuration
                        .getMappedStatement(
                                StockTransferMapper.class.getName() + ".selectFrozenTotal")
                        .getBoundSql(parameters);
        assertTrue(sales.getSql().contains("('RESERVED', 'APPROVED')"));
        assertTrue(transfer.getSql().contains("('SUBMITTED', 'APPROVED')"));
        assertTrue(transfer.getSql().contains("source_warehouse_id = ?"));
        assertEquals(2, sales.getParameterMappings().size());
        assertEquals(2, transfer.getParameterMappings().size());
        assertEquals(
                2,
                configuration
                        .getMappedStatement(
                                InventoryBalanceMapper.class.getName() + ".selectDimensionTotals")
                        .getBoundSql(parameters)
                        .getParameterMappings()
                        .size());
        assertEquals(
                3,
                configuration
                        .getMappedStatement(
                                StockTransferMapper.class.getName() + ".selectFrozenSources")
                        .getBoundSql(Map.of("query", new InventoryDimensionQuery(7, 8, 9L)))
                        .getParameterMappings()
                        .size());
    }
}
