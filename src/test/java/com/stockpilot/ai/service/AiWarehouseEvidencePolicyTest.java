package com.stockpilot.ai.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.ai.vo.AiAnswerVO.Candidate;
import com.stockpilot.ai.vo.AiAnswerVO.Evidence;
import com.stockpilot.shared.auth.WarehouseAccess;
import com.stockpilot.shared.auth.WarehouseScope;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AiWarehouseEvidencePolicyTest {
    @AfterEach
    void clear() {
        WarehouseAccess.clear();
    }

    @Test
    void restoredMixedWarehouseFactsCannotBeReusedAfterScopeRevocation() throws Exception {
        var data =
                new ObjectMapper()
                        .readTree("{\"records\":[{\"warehouseId\":3},{\"warehouseId\":7}]}");
        var evidence =
                new Evidence("list_inventory", "OK", "", data, List.of(), List.of(), Instant.now());
        assertTrue(AiWarehouseEvidencePolicy.visible(evidence));
        WarehouseAccess.bind(new WarehouseScope(false, Set.of(3L)));
        assertFalse(AiWarehouseEvidencePolicy.visible(evidence));
    }

    @Test
    void locationCandidatesCarryTheirWarehouseAndFailClosedIfScopeIsUnknown() {
        WarehouseAccess.bind(new WarehouseScope(false, Set.of(3L)));
        var data = new ObjectMapper().createObjectNode();
        assertFalse(
                AiWarehouseEvidencePolicy.visible(
                        new Evidence(
                                "find_location",
                                "NEEDS_CLARIFICATION",
                                "",
                                data,
                                List.of(new Candidate("location", "L", 5L, "L5", "库位", 7L)),
                                List.of(),
                                Instant.now())));
        assertFalse(
                AiWarehouseEvidencePolicy.visible(
                        new Evidence(
                                "find_location",
                                "NEEDS_CLARIFICATION",
                                "",
                                data,
                                List.of(new Candidate("location", "L", 5L, "L5", "库位")),
                                List.of(),
                                Instant.now())));
        assertTrue(
                AiWarehouseEvidencePolicy.visible(
                        new Evidence(
                                "find_location",
                                "NEEDS_CLARIFICATION",
                                "",
                                data,
                                List.of(new Candidate("location", "L", 5L, "L5", "库位", 3L)),
                                List.of(),
                                Instant.now())));
    }
}
