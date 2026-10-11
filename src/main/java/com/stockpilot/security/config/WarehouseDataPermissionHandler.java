package com.stockpilot.security.config;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import com.stockpilot.shared.auth.WarehouseAccess;
import java.util.Map;
import java.util.stream.Collectors;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import org.springframework.stereotype.Component;

@Component
public class WarehouseDataPermissionHandler implements MultiDataPermissionHandler {
    private static final Map<String, String[]> COLUMNS =
            Map.ofEntries(
                    Map.entry("warehouse", new String[] {"id"}),
                    Map.entry("warehouse_location", new String[] {"warehouse_id"}),
                    Map.entry("inventory_balance", new String[] {"warehouse_id"}),
                    Map.entry("inventory_ledger", new String[] {"warehouse_id"}),
                    Map.entry("purchase_receipt", new String[] {"warehouse_id"}),
                    Map.entry("purchase_receipt_line", new String[] {"warehouse_id"}),
                    Map.entry("sales_outbound_order", new String[] {"warehouse_id"}),
                    Map.entry("sales_outbound_line", new String[] {"warehouse_id"}),
                    Map.entry("inventory_count_order", new String[] {"warehouse_id"}),
                    Map.entry("inventory_count_line", new String[] {"warehouse_id"}),
                    Map.entry("inventory_count_scope_lock", new String[] {"warehouse_id"}),
                    Map.entry(
                            "stock_transfer_order",
                            new String[] {"source_warehouse_id", "target_warehouse_id"}),
                    Map.entry(
                            "stock_transfer_line",
                            new String[] {"source_warehouse_id", "target_warehouse_id"}),
                    Map.entry("safety_stock_rule", new String[] {"warehouse_id"}),
                    Map.entry("low_stock_alert", new String[] {"warehouse_id"}));

    @Override
    public Expression getSqlSegment(Table table, Expression where, String statementId) {
        var scope = WarehouseAccess.current();
        if (scope.allWarehouses()) return null;
        String[] columns =
                COLUMNS.get(table.getName().replace("`", "").toLowerCase(java.util.Locale.ROOT));
        if (columns == null) return null;
        String qualifier = table.getAlias() == null ? table.getName() : table.getAlias().getName();
        String ids =
                scope.warehouseIds().stream()
                        .sorted()
                        .map(String::valueOf)
                        .collect(Collectors.joining(","));
        String clause =
                scope.warehouseIds().isEmpty()
                        ? "1=0"
                        : java.util.Arrays.stream(columns)
                                .map(c -> qualifier + "." + c + " IN (" + ids + ")")
                                .collect(Collectors.joining(" AND "));
        try {
            return CCJSqlParserUtil.parseCondExpression(clause);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot enforce warehouse scope", e);
        }
    }
}
