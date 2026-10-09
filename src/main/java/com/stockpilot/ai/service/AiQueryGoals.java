package com.stockpilot.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.stockpilot.ai.vo.AiAnswerVO.Evidence;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Explicit query goals are checked against fresh executions and their requested scope. */
final class AiQueryGoals {
    record Goal(String tool, JsonNode parameters) {}

    static boolean sufficient(
            List<Goal> goals, List<Evidence> results, Map<String, JsonNode> executions) {
        return !goals.isEmpty()
                && goals.stream()
                        .allMatch(
                                goal ->
                                        results.stream()
                                                .anyMatch(
                                                        e -> {
                                                            if (!goal.tool().equals(e.tool())
                                                                    || !Set.of("OK", "NO_DATA")
                                                                            .contains(e.status()))
                                                                return false;
                                                            JsonNode args =
                                                                    executions.get(e.evidenceId());
                                                            if (args == null) return false;
                                                            var fields = goal.parameters().fields();
                                                            while (fields.hasNext()) {
                                                                var field = fields.next();
                                                                if (field.getValue()
                                                                        .equals(
                                                                                args.path(
                                                                                        field
                                                                                                .getKey())))
                                                                    continue;
                                                                String key = field.getKey();
                                                                if (!Set.of(
                                                                                "sku",
                                                                                "warehouse",
                                                                                "otherWarehouse",
                                                                                "location")
                                                                        .contains(key))
                                                                    return false;
                                                                JsonNode candidate =
                                                                        e.data().path(key);
                                                                String value =
                                                                        field.getValue().asText();
                                                                if (!value.equals(
                                                                                candidate
                                                                                        .path(
                                                                                                "keyword")
                                                                                        .asText())
                                                                        && !value.equals(
                                                                                candidate
                                                                                        .path(
                                                                                                "code")
                                                                                        .asText())
                                                                        && !value.equals(
                                                                                candidate
                                                                                        .path(
                                                                                                "name")
                                                                                        .asText()))
                                                                    return false;
                                                            }
                                                            return true;
                                                        }));
    }
}
