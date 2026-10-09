package com.stockpilot.ai.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.ai.vo.AiAnswerVO.Evidence;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiTaskEvidencePolicyTest {
    Evidence evidence(String tool) {
        return new Evidence(
                tool,
                "OK",
                "",
                new ObjectMapper().createObjectNode(),
                List.of(),
                List.of(),
                Instant.now());
    }

    @Test
    void compoundGoalsCannotFinishAfterOnlyTheFirstBusinessQuery() {
        String question = "比较两仓库存，并分析最近七天库存下降和对应单据";
        assertFalse(
                AiTaskEvidencePolicy.sufficient(question, List.of(evidence("compare_inventory"))));
        assertFalse(
                AiTaskEvidencePolicy.sufficient(
                        question,
                        List.of(evidence("compare_inventory"), evidence("summarize_movements"))));
        assertTrue(
                AiTaskEvidencePolicy.sufficient(
                        question,
                        List.of(
                                evidence("compare_inventory"),
                                evidence("summarize_movements"),
                                evidence("summarize_documents"))));
    }

    @Test
    void optionalDocumentChecksAndComparisonFrozenTotalsDoNotForceRedundantQueries() {
        assertTrue(
                AiTaskEvidencePolicy.sufficient(
                        "最近七天库存为什么下降，必要时核对单据", List.of(evidence("summarize_movements"))));
        assertTrue(
                AiTaskEvidencePolicy.sufficient(
                        "比较两仓库存和冻结来源", List.of(evidence("compare_inventory"))));
        assertFalse(
                AiTaskEvidencePolicy.sufficient(
                        "追溯流水对应单据", List.of(evidence("query_ledgers"), evidence("get_document"))));
    }
}
