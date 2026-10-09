package com.stockpilot.ai.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stockpilot.ai.vo.AiAnswerVO.Evidence;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiAgentModelProtocolTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void duplicateKeysPreserveArgumentBoundariesTypesAndIgnoreFieldOrder() throws Exception {
        var embeddedFields = object("{\"sku\":\"A, warehouse=B\"}");
        var separateFields = object("{\"sku\":\"A\",\"warehouse\":\"B\"}");
        assertNotEquals(key(embeddedFields), key(separateFields));
        assertNotEquals(key(object("{\"page\":\"1\"}")), key(object("{\"page\":1}")));
        assertEquals(key(separateFields), key(object("{\"warehouse\":\"B\",\"sku\":\" A \"}")));
        assertEquals(embeddedFields, json.readTree(key(embeddedFields)));
    }

    @Test
    void planningSchemasUseOnlyCurrentBusinessToolsAndDoNotMutateTheirDefinitions() {
        ArrayNode available = json.createArrayNode();
        addTool(available, "find_skus");
        addTool(available, "clarify");
        addTool(available, "query_balances");
        ArrayNode before = available.deepCopy();
        var schema = AiAgentModelProtocol.definitions(json, available);
        assertEquals(before, available);
        var planner = function(schema, "plan_query");
        assertEquals(
                json.createArrayNode().add("query_balances"),
                planner.at("/parameters/properties/goals/items/properties/tool/enum"));
        assertFalse(
                planner.at(
                                "/parameters/properties/goals/items/properties/parameters/additionalProperties")
                        .asBoolean(true));
        assertTrue(function(schema, "finish_analysis").at("/parameters/properties").has("claims"));

        // A later permission-filtered definition set must not inherit earlier planning targets.
        var restricted = AiAgentModelProtocol.definitions(json, json.createArrayNode());
        assertNull(function(restricted, "plan_query"));
        assertNull(function(restricted, "request_conditions"));
        assertEquals(1, restricted.size());
    }

    @Test
    void observationKeepsExactQuantitiesAndStableEvidenceWithoutMutatingUiReferences()
            throws Exception {
        var data = object("{\"quantity\":\"999999999999999.4321\",\"references\":[{\"id\":1}]}");
        var before = data.deepCopy();
        var evidence =
                new Evidence(
                        "query_balances",
                        "OK",
                        "查询完成",
                        data,
                        List.of(),
                        List.of(),
                        Instant.parse("2026-10-09T00:00:00Z"),
                        "E3");
        var observed = json.readTree(AiAgentModelProtocol.modelObservation(json, evidence, 2));
        assertEquals(before, data);
        assertFalse(observed.path("data").has("references"));
        assertEquals("999999999999999.4321", observed.at("/data/quantity").asText());
        assertEquals("E3", observed.path("evidenceId").asText());
        assertEquals(2, observed.path("evidenceIndex").asInt());
    }

    private ObjectNode object(String value) throws Exception {
        return (ObjectNode) json.readTree(value);
    }

    private String key(ObjectNode arguments) throws Exception {
        return AiAgentModelProtocol.canonical(json, arguments);
    }

    private void addTool(ArrayNode tools, String name) {
        var function = tools.addObject().put("type", "function").putObject("function");
        function.put("name", name);
        var parameters = function.putObject("parameters");
        parameters.putArray("required").add("sku");
        parameters.putObject("properties").putObject("sku").put("type", "string");
    }

    private com.fasterxml.jackson.databind.JsonNode function(ArrayNode tools, String name) {
        for (var tool : tools) {
            if (tool.path("function").path("name").asText().equals(name))
                return tool.path("function");
        }
        return null;
    }
}
