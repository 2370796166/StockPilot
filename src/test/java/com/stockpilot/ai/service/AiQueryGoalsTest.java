package com.stockpilot.ai.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.ai.vo.AiAnswerVO.Evidence;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiQueryGoalsTest {
    ObjectMapper json = new ObjectMapper();

    @Test
    void everyWarehouseAndPeriodGoalMustHaveItsOwnFreshEvidence() throws Exception {
        var goals =
                List.of(
                        new AiQueryGoals.Goal(
                                "summarize_movements",
                                json.readTree(
                                        "{\"warehouse\":\"W1\",\"startDate\":\"2026-10-01\"}")),
                        new AiQueryGoals.Goal(
                                "summarize_movements",
                                json.readTree(
                                        "{\"warehouse\":\"W2\",\"startDate\":\"2026-10-01\"}")));
        var first = evidence("E1", "OK");
        var second = evidence("E2", "NO_DATA");
        var executions =
                Map.of(
                        "E1",
                        json.readTree("{\"warehouse\":\"W1\",\"startDate\":\"2026-10-01\"}"),
                        "E2",
                        json.readTree("{\"warehouse\":\"W2\",\"startDate\":\"2026-10-02\"}"));
        assertFalse(AiQueryGoals.sufficient(goals, List.of(first), executions));
        assertFalse(AiQueryGoals.sufficient(goals, List.of(first, second), executions));
        assertTrue(
                AiQueryGoals.sufficient(
                        goals,
                        List.of(first, second),
                        Map.of(
                                "E1",
                                executions.get("E1"),
                                "E2",
                                json.readTree(
                                        "{\"warehouse\":\"W2\",\"startDate\":\"2026-10-01\"}"))));
        assertFalse(
                AiQueryGoals.sufficient(
                        goals, List.of(first, evidence("E2", "QUERY_FAILED")), executions));
    }

    Evidence evidence(String id, String status) {
        return new Evidence(
                "summarize_movements",
                status,
                "",
                json.createObjectNode(),
                List.of(),
                List.of(),
                Instant.now(),
                id);
    }
}
