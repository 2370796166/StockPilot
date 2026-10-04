package com.stockpilot.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.stockpilot.ai.infrastructure.AiModelAdapter;
import com.stockpilot.ai.infrastructure.AiModelAdapter.ModelFailure;
import com.stockpilot.ai.request.AiQuestionRequest;
import com.stockpilot.ai.service.*;
import com.stockpilot.ai.vo.AiAnswerVO.Evidence;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AiAssistantServiceTest {
    ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    AiModelAdapter model = mock(AiModelAdapter.class);
    AiToolService tools = mock(AiToolService.class);
    AiAssistantService assistant = new AiAssistantService(model, tools, json);
    AiQuestionRequest request = new AiQuestionRequest("查询库存", List.of());

    @BeforeEach
    void ready() {
        when(model.configurationStatus()).thenReturn("READY");
        when(model.maxToolCalls()).thenReturn(2);
        when(tools.definitions()).thenReturn(json.createArrayNode());
    }

    ObjectNode call(String id) throws Exception {
        return (ObjectNode)
                json.readTree(
                        "{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\""
                                + id
                                + "\",\"type\":\"function\",\"function\":{\"name\":\"query_ledgers\",\"arguments\":\"{}\"}}]}");
    }

    ObjectNode content(String answer, String references, boolean clarification) {
        return json.createObjectNode()
                .put("role", "assistant")
                .put(
                        "content",
                        "{\"answer\":\""
                                + answer
                                + "\",\"evidence\":"
                                + references
                                + ",\"needsClarification\":"
                                + clarification
                                + "}");
    }

    Evidence evidence(String status) {
        return new Evidence(
                "query_ledgers",
                status,
                "仅查询一页流水，不能推断完整历史或全部冻结来源",
                json.createObjectNode(),
                List.of(),
                List.of(),
                Instant.now());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DISABLED", "CONFIGURATION_ERROR"})
    void unavailableConfigurationNeverCallsProviderOrBusiness(String status) {
        when(model.configurationStatus()).thenReturn(status);
        assertEquals(status, assistant.ask(request).status());
        verify(model, never()).complete(any(), any());
        verifyNoInteractions(tools);
    }

    @Test
    void quantitativeOrFullHistoryClaimsAreRejectedButEvidenceRetained() throws Exception {
        for (String claim : List.of("总出库量为一百", "完整历史说明", "可用库存100", "全部冻结源于销售", "调拨属于销售消耗")) {
            when(model.complete(any(), any())).thenReturn(call("x"), content(claim, "[0]", false));
            when(tools.execute(any(), any(), any())).thenReturn(evidence("OK"));
            var result = assistant.ask(request);
            assertEquals("INVALID_MODEL_RESPONSE", result.status());
            assertEquals(1, result.results().size());
            assertFalse(result.answer().contains(claim));
        }
    }

    @Test
    void modelAnswerMustCiteEvidenceAndServerAlwaysAppendsScope() throws Exception {
        when(tools.execute(any(), any(), any())).thenReturn(evidence("OK"));
        when(model.complete(any(), any()))
                .thenReturn(call("x"), content("请核对下方流水及业务动作", "[0]", false));
        var result = assistant.ask(request);
        assertEquals("OK", result.status());
        assertTrue(result.answer().contains("一页"));
        when(model.complete(any(), any())).thenReturn(call("x"), content("有库存", "[9]", false));
        assertEquals("INVALID_MODEL_RESPONSE", assistant.ask(request).status());
    }

    @ParameterizedTest
    @ValueSource(strings = {"summarize_movements", "query_frozen_sources"})
    void analysisBoundarySurvivesModelQualitativeAnswer(String toolName) throws Exception {
        var evidence =
                new Evidence(
                        toolName,
                        "OK",
                        "限定查询范围；不能证明逐库位一致或完整历史",
                        json.createObjectNode(),
                        List.of(),
                        List.of(),
                        Instant.now());
        when(tools.execute(any(), any(), any())).thenReturn(evidence);
        ObjectNode toolCall = call("analysis");
        ((ObjectNode) toolCall.path("tool_calls").get(0).path("function")).put("name", toolName);
        when(model.complete(any(), any()))
                .thenReturn(toolCall, content("请核对下方业务动作与来源", "[0]", false));
        var result = assistant.ask(request);
        assertEquals("OK", result.status());
        assertTrue(result.answer().contains("不能证明逐库位"));
        assertEquals(toolName, result.results().get(0).tool());
    }

    @Test
    void noToolResultCannotInventBusinessAnswer() {
        when(model.complete(any(), any())).thenReturn(content("有库存", "[]", false));
        assertEquals("INVALID_MODEL_RESPONSE", assistant.ask(request).status());
        when(model.complete(any(), any())).thenReturn(content("请提供商品与仓库名称", "[]", true));
        assertEquals("NEEDS_CLARIFICATION", assistant.ask(request).status());
    }

    @Test
    void callLimitAndRepeatedIdentifiersTerminate() throws Exception {
        when(tools.execute(any(), any(), any())).thenReturn(evidence("OK"));
        when(model.complete(any(), any())).thenReturn(call("a"), call("b"), call("c"));
        assertEquals("TOOL_LIMIT_EXCEEDED", assistant.ask(request).status());
        verify(tools, times(2)).execute(any(), any(), any());
        when(model.complete(any(), any())).thenReturn(call("a"), call("a"));
        assertEquals("INVALID_MODEL_RESPONSE", assistant.ask(request).status());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "FORBIDDEN",
                "NO_DATA",
                "QUERY_FAILED",
                "NEEDS_SELECTION",
                "NEEDS_CLARIFICATION",
                "INVALID_TOOL",
                "INVALID_ARGUMENTS"
            })
    void toolFailureOrClarificationStopsModelAndPreservesStatus(String status) throws Exception {
        when(model.complete(any(), any())).thenReturn(call("x"));
        when(tools.execute(any(), any(), any())).thenReturn(evidence(status));
        assertEquals(status, assistant.ask(request).status());
        verify(model, times(1)).complete(any(), any());
    }

    @Test
    void timeoutIsDistinctAndDoesNotErasePreviouslyQueriedData() throws Exception {
        when(model.complete(any(), any()))
                .thenReturn(call("x"))
                .thenThrow(new ModelFailure("MODEL_TIMEOUT"));
        when(tools.execute(any(), any(), any())).thenReturn(evidence("OK"));
        var result = assistant.ask(request);
        assertEquals("MODEL_TIMEOUT", result.status());
        assertEquals(1, result.results().size());
    }

    @Test
    void malformedArgumentsAndOversizedBatchNeverReachTool() throws Exception {
        ObjectNode response = call("x");
        ((ObjectNode) response.path("tool_calls").get(0).path("function"))
                .put("arguments", "not json");
        when(model.complete(any(), any())).thenReturn(response);
        assertEquals("INVALID_MODEL_RESPONSE", assistant.ask(request).status());
        verify(tools, never()).execute(any(), any(), any());
        when(model.maxToolCalls()).thenReturn(1);
        response = call("x");
        ((ArrayNode) response.get("tool_calls")).add(call("y").path("tool_calls").get(0));
        when(model.complete(any(), any())).thenReturn(response);
        assertEquals("TOOL_LIMIT_EXCEEDED", assistant.ask(request).status());
        verify(tools, never()).execute(any(), any(), any());
    }
}
