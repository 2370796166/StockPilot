package com.stockpilot.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.stockpilot.ai.service.AiAgentAnswerService;
import com.stockpilot.ai.vo.AiAnswerVO.Evidence;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiAgentAnswerTest {
    @Test
    void aCurrentFrozenSourceCanLinkItsDocumentWithoutPretendingItIsPeriodEvidence()
            throws Exception {
        var source =
                new Evidence(
                        "query_frozen_sources",
                        "OK",
                        "",
                        json.readTree(
                                "{\"sku\":{\"id\":1,\"code\":\"SKU1\"},\"warehouse\":{\"id\":2,\"code\":\"WH1\"},\"salesSources\":{\"records\":[{\"documentType\":\"SALES\",\"businessNo\":\"SO1\",\"warehouseId\":2,\"locationId\":3,\"skuId\":1}]}}"),
                        List.of(),
                        List.of(),
                        Instant.now());
        var document =
                new Evidence(
                        "get_document",
                        "OK",
                        "",
                        json.readTree(
                                "{\"documentType\":\"SALES\",\"outboundNo\":\"SO1\",\"warehouseId\":2,\"lines\":[{\"skuId\":1,\"locationId\":3}]}"),
                        List.of(),
                        List.of(),
                        Instant.now());
        var plan =
                json.readTree(
                        "{\"claims\":[{\"rule\":\"get_document\",\"evidence\":1,\"relatedEvidence\":[0]}]}");
        answers.validate(plan, List.of(source, document));
        ((com.fasterxml.jackson.databind.node.ObjectNode) document.data().path("lines").get(0))
                .put("locationId", 99);
        assertThrows(
                IllegalArgumentException.class,
                () -> answers.validate(plan, List.of(source, document)));
    }

    @Test
    void relatedDocumentRequiresQueriedBusinessLinkAndMatchingWarehouseSkuAndType()
            throws Exception {
        var groups =
                new Evidence(
                        "summarize_documents",
                        "OK",
                        "",
                        json.readTree(
                                "{\"sku\":{\"id\":1,\"code\":\"BOLT\"},\"warehouse\":{\"id\":2,\"code\":\"WH1\"},\"documentMovements\":{\"records\":[{\"businessNo\":\"SHIP001\",\"businessType\":\"OUTBOUND_SHIP\"}]}}"),
                        List.of(),
                        List.of(),
                        Instant.now());
        var document =
                new Evidence(
                        "get_document",
                        "OK",
                        "",
                        json.readTree(
                                "{\"outboundNo\":\"SHIP001\",\"documentType\":\"SALES\",\"warehouseId\":2,\"lines\":[{\"skuId\":1,\"locationId\":3}]}"),
                        List.of(),
                        List.of(),
                        Instant.now());
        var plan =
                json.readTree(
                        "{\"claims\":[{\"rule\":\"summarize_documents\",\"evidence\":0,\"relatedEvidence\":[1]}]}");
        answers.validate(plan, List.of(groups, document));
        var detail = (com.fasterxml.jackson.databind.node.ObjectNode) document.data();
        detail.put("warehouseId", 99);
        assertThrows(
                IllegalArgumentException.class,
                () -> answers.validate(plan, List.of(groups, document)));
        detail.put("warehouseId", 2).put("outboundNo", "UNKNOWN");
        assertThrows(
                IllegalArgumentException.class,
                () -> answers.validate(plan, List.of(groups, document)));
        detail.put("outboundNo", "SHIP001");
        ((com.fasterxml.jackson.databind.node.ObjectNode) detail.path("lines").get(0))
                .put("skuId", 99);
        assertThrows(
                IllegalArgumentException.class,
                () -> answers.validate(plan, List.of(groups, document)));
        ((com.fasterxml.jackson.databind.node.ObjectNode) detail.path("lines").get(0))
                .put("skuId", 1);
        detail.put("documentType", "COUNT").put("countNo", "SHIP001");
        assertThrows(
                IllegalArgumentException.class,
                () -> answers.validate(plan, List.of(groups, document)));
    }

    @Test
    void comparisonExplainsEachWarehouseAndDoesNotTurnMissingBalanceIntoZero() throws Exception {
        var evidence =
                new Evidence(
                                "compare_inventory",
                                "OK",
                                "同一快照，范围总量核对不证明逐单一致",
                                json.readTree(
                                        "{\"warehouse\":{\"name\":\"一号仓\"},\"otherWarehouse\":{\"name\":\"二号仓\"},\"comparison\":[{\"balanceExists\":true,\"actualQuantity\":\"86.0000\",\"availableQuantity\":\"66.0000\",\"frozenQuantity\":\"20.0000\",\"salesQuantity\":\"20.0000\",\"transferQuantity\":\"0.0000\"},{\"balanceExists\":false}]}"),
                                List.of(),
                                List.of(),
                                Instant.now())
                        .identified("E8");
        var answer = answers.render(List.of(evidence), "OK");
        assertTrue(answer.conclusions().get(0).text().contains("一号仓实际量86.0000"));
        assertTrue(answer.conclusions().get(0).text().contains("二号仓没有余额记录"));
        assertTrue(answer.uncertainties().stream().anyMatch(text -> text.contains("无法计算")));
        assertTrue(
                answer.conclusions().get(0).facts().stream()
                        .anyMatch(
                                fact ->
                                        fact.field().equals("/comparison/0/availableQuantity")
                                                && fact.value().equals("66.0000")));
    }

    @Test
    void movementExplanationBindsDocumentFactsAndRefusesDifferentPeriods() throws Exception {
        String scope =
                "\"sku\":{\"id\":1,\"code\":\"BOLT\",\"name\":\"螺栓\"},\"warehouse\":{\"id\":2,\"code\":\"WH1\"},\"period\":{\"startDate\":\"2026-10-01\",\"endDate\":\"2026-10-07\"},";
        var movements =
                new Evidence(
                                "summarize_movements",
                                "OK",
                                "完整区间",
                                json.readTree(
                                        "{"
                                                + scope
                                                + "\"summary\":{\"changeActualQuantity\":\"-14.0000\"},\"movements\":[{\"meaning\":\"销售实际出库\",\"changeActualQuantity\":\"-10.0000\"}]}"),
                                List.of(),
                                List.of(),
                                Instant.now())
                        .identified("E3");
        var documents =
                new Evidence(
                                "summarize_documents",
                                "OK",
                                "分页分组",
                                json.readTree(
                                        "{"
                                                + scope
                                                + "\"documentMovements\":{\"records\":[{\"businessNo\":\"SHIP001\",\"businessType\":\"OUTBOUND_SHIP\",\"changeActualQuantity\":\"-10.0000\"}],\"total\":4}}"),
                                List.of(),
                                List.of(),
                                Instant.now())
                        .identified("E4");
        var plan =
                json.readTree(
                        "{\"claims\":[{\"rule\":\"summarize_movements\",\"evidence\":0,\"relatedEvidence\":[1]}]}");
        // Real period-summary VO adds a derived exclusive boundary; document groups omit it.
        ((com.fasterxml.jackson.databind.node.ObjectNode) movements.data().path("period"))
                .put("endExclusive", "2026-10-08T00:00");
        answers.validate(plan, List.of(movements, documents));
        var explanation =
                answers.render(
                                List.of(movements, documents),
                                "OK",
                                plan,
                                List.of(movements, documents))
                        .conclusions()
                        .get(0);
        assertEquals("EXPLAIN_MOVEMENT_CHANGE", explanation.rule());
        assertEquals(List.of("E3", "E4"), explanation.evidenceIds());
        assertTrue(explanation.text().contains("SHIP001"));
        assertTrue(explanation.text().contains("分页"));
        assertTrue(
                explanation.facts().stream()
                        .anyMatch(
                                f -> f.evidenceId().equals("E4") && f.value().equals("-10.0000")));
        ((com.fasterxml.jackson.databind.node.ObjectNode) documents.data().path("period"))
                .put("endDate", "2026-10-08");
        assertThrows(
                IllegalArgumentException.class,
                () -> answers.validate(plan, List.of(movements, documents)));
    }

    @Test
    void stableReferencesSurviveFilteringAndIdentifiersAreNotComparedAsQuantities()
            throws Exception {
        var data =
                json.readTree(
                        "{\"businessNo\":\"001\",\"frozenTotals\":{\"sourceQuantity\":\"20.0000\"}}");
        var evidence =
                new Evidence(
                                "query_frozen_sources",
                                "OK",
                                "范围已核对",
                                data,
                                List.of(),
                                List.of(),
                                Instant.now())
                        .identified("E7");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        answers.validate(
                                json.readTree(
                                        "{\"claims\":[{\"rule\":\"query_frozen_sources\",\"evidence\":0,\"field\":\"/businessNo\",\"value\":\"1\"}]}"),
                                List.of(evidence)));
        assertEquals(
                "E7", answers.render(List.of(evidence), "OK").conclusions().get(0).evidenceId());
        assertTrue(
                answers.render(List.of(evidence), "OK").conclusions().get(0).facts().stream()
                        .allMatch(
                                fact ->
                                        evidence.data().at(fact.field()).isValueNode()
                                                && evidence.data()
                                                        .at(fact.field())
                                                        .asText()
                                                        .equals(fact.value())));
    }

    @Test
    void finalPlanSelectsConclusionsAndRejectsCrossScopeRelations() throws Exception {
        var first = frozen().identified("E1");
        var second = frozen().identified("E2");
        var plan =
                json.readTree("{\"claims\":[{\"rule\":\"query_frozen_sources\",\"evidence\":1}]}");
        answers.validate(plan, List.of(first, second));
        var result = answers.render(List.of(first, second), "OK", plan, List.of(first, second));
        assertEquals(1, result.conclusions().size());
        assertEquals("E2", result.conclusions().get(0).evidenceId());
        var wrong =
                json.readTree(
                        "{\"claims\":[{\"rule\":\"query_frozen_sources\",\"evidence\":0,\"relatedEvidence\":[1]}]}");
        assertThrows(
                IllegalArgumentException.class,
                () -> answers.validate(wrong, List.of(first, second)));
    }

    final ObjectMapper json = new ObjectMapper();
    final AiAgentAnswerService answers = new AiAgentAnswerService();

    Evidence frozen() throws Exception {
        return new Evidence(
                "query_frozen_sources",
                "OK",
                "总量核对不证明逐单据一致",
                json.readTree(
                        "{\"frozenTotals\":{\"sourceQuantity\":\"20.0000\",\"salesQuantity\":\"20.0000\",\"transferQuantity\":\"0.0000\"}}"),
                List.of(),
                List.of(),
                Instant.now());
    }

    @Test
    void anyWhitelistScalarCanBeCitedWithExactBackendValue() throws Exception {
        answers.validate(
                json.readTree(
                        "{\"claims\":[{\"rule\":\"query_frozen_sources\",\"evidence\":0,\"field\":\"/frozenTotals/sourceQuantity\",\"value\":\"20\"}]}"),
                List.of(frozen()));
        answers.validate(
                json.readTree(
                        "{\"claims\":[{\"rule\":\"query_frozen_sources\",\"evidence\":0,\"field\":\"/data/frozenTotals/sourceQuantity\",\"value\":\"20\"}]}"),
                List.of(frozen()));
        for (String field :
                List.of("/data/password", "/data/../frozenTotals/sourceQuantity", "/data"))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            answers.validate(
                                    json.readTree(
                                            "{\"claims\":[{\"rule\":\"query_frozen_sources\",\"evidence\":0,\"field\":\""
                                                    + field
                                                    + "\",\"value\":\"20\"}]}"),
                                    List.of(frozen())));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        answers.validate(
                                json.readTree(
                                        "{\"claims\":[{\"rule\":\"query_frozen_sources\",\"evidence\":0,\"field\":\"/data/frozenTotals/sourceQuantity\",\"value\":\"21\"}]}"),
                                List.of(frozen())));
    }

    @Test
    void fabricatedValueWrongEvidenceUnknownFieldAndFreeProseAreRejected() throws Exception {
        for (String claim :
                List.of(
                        "{\"rule\":\"query_frozen_sources\",\"evidence\":0,\"field\":\"/frozenTotals/sourceQuantity\",\"value\":\"21\"}",
                        "{\"rule\":\"query_frozen_sources\",\"evidence\":4294967296}",
                        "{\"rule\":\"query_frozen_sources\",\"evidence\":0,\"field\":\"/password\",\"value\":\"secret\"}",
                        "{\"rule\":\"query_frozen_sources\",\"evidence\":0,\"answer\":\"所有冻结已经出库\"}")) {
            var response = json.readTree("{\"claims\":[" + claim + "]}");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> answers.validate(response, List.of(frozen())));
        }
    }
}
