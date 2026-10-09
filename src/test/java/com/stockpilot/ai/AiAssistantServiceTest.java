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
    org.springframework.transaction.PlatformTransactionManager transactions =
            mock(org.springframework.transaction.PlatformTransactionManager.class);
    AiContinuationService continuations = new AiContinuationService(json);
    AiAssistantService assistant =
            new AiAssistantService(
                    model,
                    tools,
                    json,
                    continuations,
                    new AiReadOnlyToolExecutor(tools, transactions));
    AiQuestionRequest request = new AiQuestionRequest("查询库存", List.of());

    @BeforeEach
    void ready() {
        when(model.configurationStatus()).thenReturn("READY");
        when(model.maxToolCalls()).thenReturn(2);
        when(model.questionTimeout()).thenReturn(java.time.Duration.ofSeconds(90));
        when(tools.definitions()).thenReturn(json.createArrayNode());
        when(tools.mayQuery(anyString())).thenReturn(true);
        when(transactions.getTransaction(any()))
                .thenAnswer(
                        invocation ->
                                new org.springframework.transaction.support
                                        .SimpleTransactionStatus());
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

    ObjectNode discoveryCall(String id) throws Exception {
        ObjectNode response = call(id);
        ((ObjectNode) response.path("tool_calls").get(0).path("function"))
                .put("name", "find_sku")
                .put("arguments", "{\"keyword\":\"" + id + "\"}");
        return response;
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
        verify(model, never()).complete(any(), any(), any());
        verifyNoInteractions(tools);
    }

    @Test
    void quantitativeOrFullHistoryClaimsAreRejectedButEvidenceRetained() throws Exception {
        for (String claim : List.of("总出库量为一百", "完整历史说明", "可用库存100", "全部冻结源于销售", "调拨属于销售消耗")) {
            when(model.complete(any(), any(), any()))
                    .thenReturn(discoveryCall("x"), content(claim, "[0]", false));
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
        when(model.complete(any(), any(), any()))
                .thenReturn(discoveryCall("x"), content("请核对下方流水及业务动作", "[0]", false));
        var result = assistant.ask(request);
        assertEquals("OK", result.status());
        assertTrue(result.answer().contains("一页"));
        when(model.complete(any(), any(), any()))
                .thenReturn(discoveryCall("x"), content("有库存", "[9]", false));
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
        when(model.complete(any(), any(), any()))
                .thenReturn(toolCall, content("请核对下方业务动作与来源", "[0]", false));
        var result = assistant.ask(request);
        assertEquals("OK", result.status());
        assertTrue(result.answer().contains("不能证明逐库位"));
        assertEquals(toolName, result.results().get(0).tool());
    }

    @Test
    void noToolResultCannotInventBusinessAnswer() {
        when(model.complete(any(), any(), any())).thenReturn(content("有库存", "[]", false));
        assertEquals("INVALID_MODEL_RESPONSE", assistant.ask(request).status());
        when(model.complete(any(), any(), any())).thenReturn(content("请提供商品与仓库名称", "[]", true));
        assertEquals("NEEDS_CLARIFICATION", assistant.ask(request).status());
    }

    @Test
    void callLimitAndRepeatedIdentifiersTerminate() throws Exception {
        when(tools.execute(any(), any(), any())).thenReturn(evidence("OK"));
        // Discovery rounds may continue; their accumulated limit still prevents another round.
        when(model.complete(any(), any(), any()))
                .thenReturn(discoveryCall("a"), discoveryCall("b"), discoveryCall("c"));
        assertEquals("TOOL_LIMIT_EXCEEDED", assistant.ask(request).status());
        verify(tools, times(2)).execute(any(), any(), any());
        when(model.complete(any(), any(), any()))
                .thenReturn(discoveryCall("a"), discoveryCall("a"));
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
        when(model.complete(any(), any(), any())).thenReturn(call("x"));
        when(tools.execute(any(), any(), any())).thenReturn(evidence(status));
        assertEquals(status, assistant.ask(request).status());
        verify(model, times(1)).complete(any(), any(), any());
    }

    @Test
    void timeoutIsDistinctAndDoesNotErasePreviouslyQueriedData() throws Exception {
        when(model.complete(any(), any(), any()))
                .thenReturn(discoveryCall("x"))
                .thenThrow(new ModelFailure("MODEL_TIMEOUT"));
        when(tools.execute(any(), any(), any())).thenReturn(evidence("OK"));
        var result = assistant.ask(request);
        assertEquals("MODEL_TIMEOUT", result.status());
        assertEquals(1, result.results().size());
    }

    @Test
    void expiredQuestionNeverStartsAnotherProviderRound() {
        when(model.questionTimeout()).thenReturn(java.time.Duration.ofNanos(1));
        assertEquals("QUESTION_TIMEOUT", assistant.ask(request).status());
        verify(model, never()).complete(any(), any(), any());
        verify(tools, never()).execute(any(), any(), any());
    }

    @Test
    void totalTimeoutPreservesAlreadyQueriedEvidence() throws Exception {
        when(model.complete(any(), any(), any()))
                .thenReturn(discoveryCall("x"))
                .thenThrow(new ModelFailure("QUESTION_TIMEOUT"));
        when(tools.execute(any(), any(), any())).thenReturn(evidence("OK"));
        var result = assistant.ask(request);
        assertEquals("QUESTION_TIMEOUT", result.status());
        assertEquals(1, result.results().size());
    }

    @Test
    void malformedArgumentsAndOversizedBatchNeverReachTool() throws Exception {
        ObjectNode response = call("x");
        ((ObjectNode) response.path("tool_calls").get(0).path("function"))
                .put("arguments", "not json");
        when(model.complete(any(), any(), any())).thenReturn(response);
        assertEquals("INVALID_MODEL_RESPONSE", assistant.ask(request).status());
        verify(tools, never()).execute(any(), any(), any());
        when(model.maxToolCalls()).thenReturn(1);
        response = call("x");
        ((ArrayNode) response.get("tool_calls")).add(call("y").path("tool_calls").get(0));
        when(model.complete(any(), any(), any())).thenReturn(response);
        assertEquals("TOOL_LIMIT_EXCEEDED", assistant.ask(request).status());
        verify(tools, never()).execute(any(), any(), any());
    }

    @AfterEach
    void clearActor() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void chineseQuantityIsRejectedAndContradictoryModelProseNeverBecomesFacts() throws Exception {
        when(tools.execute(any(), any(), any())).thenReturn(evidence("OK"));
        when(model.complete(any(), any(), any()))
                .thenReturn(discoveryCall("a"), content("可用库存为一百", "[0]", false));
        var rejected = assistant.ask(request);
        assertEquals("INVALID_MODEL_RESPONSE", rejected.status());
        assertEquals(1, rejected.results().size());
        assertFalse(rejected.answer().contains("一百"));
        when(model.complete(any(), any(), any()))
                .thenReturn(call("b"), content("仓库没有库存", "[0]", false));
        var result = assistant.ask(request);
        assertEquals("OK", result.status());
        assertFalse(result.answer().contains("仓库没有库存"));
        assertTrue(result.answer().contains(result.results().get(0).message()));
    }

    @Test
    void validScopeWarningIsAcceptedAndBusinessQueryDoesNotNeedFinalConfirmation()
            throws Exception {
        when(tools.execute(any(), any(), any())).thenReturn(evidence("OK"));
        when(model.complete(any(), any(), any()))
                .thenReturn(discoveryCall("a"), content("当前结果不能证明完整历史", "[0]", false));
        var scoped = assistant.ask(request);
        assertEquals("OK", scoped.status());
        assertTrue(scoped.answer().contains("不能推断完整历史"));
        clearInvocations(model, tools);
        when(model.complete(any(), any(), any()))
                .thenReturn(call("business"))
                .thenThrow(new ModelFailure("MODEL_TIMEOUT"));
        var direct = assistant.ask(request);
        assertEquals("OK", direct.status());
        assertEquals(1, direct.results().size());
        assertTrue(direct.answer().contains(direct.results().get(0).message()));
        var messages = org.mockito.ArgumentCaptor.forClass(ArrayNode.class);
        verify(model, times(1)).complete(messages.capture(), any(), any());
        assertEquals(2, messages.getValue().size());
        assertEquals("user", messages.getValue().get(1).path("role").asText());
    }

    @Test
    void oneAllowedReferenceLookupCanFinishButCannotQueryAnotherTool() throws Exception {
        when(model.maxToolCalls()).thenReturn(1);
        when(tools.execute(any(), any(), any())).thenReturn(evidence("OK"));
        when(model.complete(any(), any(), any()))
                .thenReturn(discoveryCall("a"), content("请核对资料", "[0]", false));
        var result = assistant.ask(request);
        assertEquals("OK", result.status());
        assertEquals(1, result.results().size());
        var definitions = org.mockito.ArgumentCaptor.forClass(ArrayNode.class);
        verify(model, times(2)).complete(any(), definitions.capture(), any());
        assertTrue(definitions.getAllValues().get(1).isEmpty());
        verify(tools, times(1)).execute(any(), any(), any());
    }

    @Test
    void businessPlanProseCannotBecomeFactsOrTriggerAnotherModelRequest() throws Exception {
        ObjectNode plan = call("business");
        plan.put("content", "全部库存为一百，仓库没有库存");
        when(model.complete(any(), any(), any()))
                .thenReturn(plan)
                .thenThrow(new ModelFailure("MODEL_ERROR"));
        when(tools.execute(any(), any(), any())).thenReturn(evidence("OK"));
        var result = assistant.ask(request);
        assertEquals("OK", result.status());
        assertEquals(1, result.results().size());
        assertFalse(result.answer().contains("一百"));
        assertFalse(result.answer().contains("仓库没有库存"));
        assertTrue(result.answer().contains(result.results().get(0).message()));
        verify(model, times(1)).complete(any(), any(), any());
    }

    @Test
    void duplicateNormalizedQueriesWithinABatchReuseOneAuthorizedResult() throws Exception {
        ObjectNode batch = call("a");
        ((ObjectNode) batch.path("tool_calls").get(0).path("function"))
                .put("arguments", "{\"sku\":\"A\",\"warehouse\":\"一号仓\"}");
        ObjectNode second = (ObjectNode) batch.path("tool_calls").get(0).deepCopy();
        second.put("id", "b");
        ((ObjectNode) second.path("function"))
                .put("arguments", "{\"warehouse\":\"一号仓\",\"sku\":\" A \"}");
        ((ArrayNode) batch.path("tool_calls")).add(second);
        when(tools.canReuse(any())).thenReturn(true);
        when(tools.execute(any(), any(), any())).thenReturn(evidence("OK"));
        when(model.complete(any(), any(), any())).thenReturn(batch, content("查询完成", "[0]", false));
        var result = assistant.ask(request);
        assertEquals("OK", result.status());
        assertEquals(1, result.results().size());
        verify(tools, times(1)).execute(any(), any(), any());
    }

    @Test
    void resumedSelectionUsesSignedOriginalPlanWithoutCallingModelAgain() throws Exception {
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(
                        new org.springframework.security.authentication
                                .UsernamePasswordAuthenticationToken(
                                "reader", "unused", List.of()));
        when(model.complete(any(), any(), any())).thenReturn(call("original"));
        when(tools.execute(any(), any(), any()))
                .thenReturn(evidence("NEEDS_SELECTION"), evidence("OK"));
        var first = assistant.ask(request);
        assertNotNull(first.continuationToken());
        var selected =
                new AiQuestionRequest(
                        request.question(),
                        List.of(new AiQuestionRequest.Selection("sku", "A", 1L)),
                        first.continuationToken());
        var second = assistant.ask(selected);
        assertEquals("OK", second.status());
        verify(model, times(1)).complete(any(), any(), any());
        verify(tools, times(2)).execute(eq("query_ledgers"), eq(json.createObjectNode()), any());
        var invalid =
                assistant.ask(
                        new AiQuestionRequest(
                                "另一个问题", selected.selections(), first.continuationToken()));
        assertEquals("INVALID_ARGUMENTS", invalid.status());
        verify(tools, times(2)).execute(any(), any(), any());
    }

    @Test
    void wholeBatchIsParsedBeforeAnyQueryAndCompletedBusinessNeverStartsAnotherToolRound()
            throws Exception {
        ObjectNode batch = call("a");
        ObjectNode second = (ObjectNode) batch.path("tool_calls").get(0).deepCopy();
        second.put("id", "b");
        ((ObjectNode) second.path("function")).put("arguments", "broken");
        ((ArrayNode) batch.path("tool_calls")).add(second);
        when(model.complete(any(), any(), any())).thenReturn(batch);
        assertEquals("INVALID_MODEL_RESPONSE", assistant.ask(request).status());
        verify(tools, never()).execute(any(), any(), any());
        clearInvocations(model);
        when(tools.execute(any(), any(), any())).thenReturn(evidence("OK"));
        when(model.complete(any(), any(), any())).thenReturn(call("c"), call("d"));
        var complete = assistant.ask(request);
        assertEquals("OK", complete.status());
        assertEquals(1, complete.results().size());
        verify(tools, times(1)).execute(any(), any(), any());
        verify(model, times(1)).complete(any(), any(), any());
    }

    @Test
    void clarificationDoesNotAcquireADatabaseTransaction() throws Exception {
        ObjectNode response = call("clarify");
        ((ObjectNode) response.path("tool_calls").get(0).path("function"))
                .put("name", "clarify")
                .put("arguments", "{\"reason\":\"MISSING_INPUT\"}");
        when(model.complete(any(), any(), any())).thenReturn(response);
        when(tools.execute(any(), any(), any())).thenReturn(evidence("NEEDS_CLARIFICATION"));
        assertEquals("NEEDS_CLARIFICATION", assistant.ask(request).status());
        verifyNoInteractions(transactions);
        verify(model, times(1)).complete(any(), any(), any());
    }

    @Test
    void duplicateResultCannotBypassChangedPermissions() throws Exception {
        ObjectNode response = call("a");
        ObjectNode second = (ObjectNode) response.path("tool_calls").get(0).deepCopy();
        second.put("id", "b");
        ((ArrayNode) response.path("tool_calls")).add(second);
        when(model.complete(any(), any(), any())).thenReturn(response);
        when(tools.canReuse(any())).thenReturn(false);
        when(tools.execute(any(), any(), any())).thenReturn(evidence("OK"), evidence("FORBIDDEN"));
        assertEquals("FORBIDDEN", assistant.ask(request).status());
        verify(tools, times(2)).execute(any(), any(), any());
    }

    @Test
    void unauthorizedModelToolDoesNotAcquireADatabaseConnection() throws Exception {
        when(tools.mayQuery(anyString())).thenReturn(false);
        when(model.complete(any(), any(), any())).thenReturn(call("denied"));
        when(tools.execute(any(), any(), any())).thenReturn(evidence("FORBIDDEN"));
        assertEquals("FORBIDDEN", assistant.ask(request).status());
        verifyNoInteractions(transactions);
        verify(model, times(1)).complete(any(), any(), any());
    }
}
