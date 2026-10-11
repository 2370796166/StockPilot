package com.stockpilot.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.stockpilot.ai.vo.AiAnswerVO.Evidence;

/** Reauthorizes restored facts and candidates against the current warehouse scope. */
final class AiWarehouseEvidencePolicy {
    private AiWarehouseEvidencePolicy() {}

    static boolean visible(Evidence evidence) {
        var scope = com.stockpilot.shared.auth.WarehouseAccess.current();
        if (scope.allWarehouses()) return true;
        if (evidence.candidates().stream()
                .anyMatch(
                        c ->
                                "warehouse".equals(c.kind())
                                        ? !scope.permits(c.id())
                                        : "location".equals(c.kind())
                                                && (c.warehouseId() == null
                                                        || !scope.permits(c.warehouseId()))))
            return false;
        return warehouseDataVisible(evidence.data());
    }

    private static boolean warehouseDataVisible(JsonNode node) {
        if (node == null || node.isNull()) return true;
        var scope = com.stockpilot.shared.auth.WarehouseAccess.current();
        if (node.isArray()) {
            for (var child : node) if (!warehouseDataVisible(child)) return false;
        }
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                var value = field.getValue();
                String key = field.getKey();
                if ((key.equals("warehouseId") || key.endsWith("WarehouseId"))
                        && value.isIntegralNumber()
                        && !scope.permits(value.asLong())) return false;
                if (key.matches("warehouse:[0-9]+")
                        && !scope.permits(Long.parseLong(key.substring(10)))) return false;
                if (key.equals("warehouse")
                        && value.isObject()
                        && value.path("id").isIntegralNumber()
                        && !scope.permits(value.path("id").asLong())) return false;
                if (!warehouseDataVisible(value)) return false;
            }
        }
        return true;
    }
}
