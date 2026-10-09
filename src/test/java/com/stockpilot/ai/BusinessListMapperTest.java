package com.stockpilot.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.stockpilot.inventory.mapper.InventoryBalanceMapper;
import com.stockpilot.inventory.request.InventoryBalancePageQuery;
import com.stockpilot.shared.query.DocumentDateRangeQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class BusinessListMapperTest {
    @Test
    void inventoryThresholdIsAnSqlFilterAcrossAllMatchingRecords() {
        var config = new Configuration();
        config.addMapper(InventoryBalanceMapper.class);
        var q = new InventoryBalancePageQuery();
        q.setBelowAvailable(new BigDecimal("20.1234"));
        var bound =
                config.getMappedStatement(
                                InventoryBalanceMapper.class.getName() + ".selectInventoryPage")
                        .getBoundSql(Map.of("query", q));
        assertTrue(bound.getSql().contains("available_quantity < ?"));
        assertTrue(
                bound.getParameterMappings().stream()
                        .anyMatch(p -> p.getProperty().equals("query.belowAvailable")));
        assertFalse(bound.getSql().contains("sku_id = ?"));
    }

    @Test
    void allDocumentListsApplyCompletedDatesAndTerminalStateFiltersInSql() {
        check(
                com.stockpilot.purchase.mapper.PurchaseReceiptMapper.class,
                new com.stockpilot.purchase.request.PurchaseReceiptRequests.PageQuery(),
                "query",
                "completed_at",
                "COMPLETED");
        check(
                com.stockpilot.sales.mapper.SalesOutboundMapper.class,
                new com.stockpilot.sales.request.SalesOutboundRequests.PageQuery(),
                "query",
                "completed_at",
                "CANCELLED");
        check(
                com.stockpilot.transfer.mapper.StockTransferMapper.class,
                new com.stockpilot.transfer.request.StockTransferRequests.PageQuery(),
                "q",
                "completed_at",
                "CANCELLED");
        check(
                com.stockpilot.inventory.count.mapper.InventoryCountMapper.class,
                new com.stockpilot.inventory.count.request.InventoryCountRequests.PageQuery(),
                "q",
                "adjusted_at",
                "ADJUSTED");
    }

    void check(
            Class<?> mapper,
            DocumentDateRangeQuery query,
            String key,
            String column,
            String terminal) {
        var config = new Configuration();
        config.addMapper(mapper);
        query.setStartDate(LocalDate.of(2026, 10, 1));
        query.setEndDate(LocalDate.of(2026, 10, 8));
        query.setDateField(DocumentDateRangeQuery.DateField.COMPLETED);
        query.setUnfinished(true);
        var statement = config.getMappedStatement(mapper.getName() + ".selectPage");
        var bound = statement.getBoundSql(Map.of(key, query));
        assertTrue(bound.getSql().contains(column + " >= ?"));
        assertTrue(bound.getSql().contains(column + " < ?"));
        assertTrue(bound.getSql().contains(terminal));
        assertTrue(
                bound.getParameterMappings().stream()
                        .anyMatch(p -> p.getProperty().equals(key + ".endExclusive")));
        query.setDateField(DocumentDateRangeQuery.DateField.CREATED);
        assertTrue(statement.getBoundSql(Map.of(key, query)).getSql().contains("created_at >= ?"));
        query.setEndDate(LocalDate.of(2027, 1, 1));
        assertFalse(query.isPeriodValid());
    }
}
