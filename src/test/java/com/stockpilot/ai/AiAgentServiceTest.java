package com.stockpilot.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.stockpilot.ai.infrastructure.*;
import com.stockpilot.ai.request.*;
import com.stockpilot.ai.service.*;
import com.stockpilot.ai.vo.*;
import com.stockpilot.ai.vo.AiAnswerVO.*;
import com.stockpilot.security.auth.DatabaseUserDetailsService;
import com.stockpilot.shared.auth.AuthenticatedActor;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

class AiAgentServiceTest {
    @Test
    void emptyFirstGoalDoesNotEndOtherDeclaredQueries() throws Exception {
        when(tools.isKnownTool(anyString())).thenReturn(true);
        var response =
                call(
                        "plan_query",
                        "{\"goals\":[{\"tool\":\"query_balances\",\"parameters\":{\"sku\":\"螺栓\",\"warehouse\":\"一号仓\"}},{\"tool\":\"query_frozen_sources\",\"parameters\":{\"sku\":\"螺栓\",\"warehouse\":\"一号仓\"}}]}");
        ((ArrayNode) response.path("tool_calls"))
                .add(
                        call("query_balances", "{\"sku\":\"螺栓\",\"warehouse\":\"一号仓\"}")
                                .path("tool_calls")
                                .get(0));
        when(model.complete(any(), any(), any()))
                .thenReturn(
                        response,
                        call("query_frozen_sources", "{\"sku\":\"螺栓\",\"warehouse\":\"一号仓\"}"));
        when(executor.execute(eq("query_balances"), any(), anyList(), any()))
                .thenReturn(evidence("query_balances", "NO_DATA"));
        var result = ask(agent.create().id(), "螺栓在一号仓的货况和占用情况");
        assertEquals("COMPLETED", result.status());
        assertEquals("OK", result.reason());
        assertEquals(2, result.toolCalls());
        assertEquals(2, result.results().size());
        verify(executor).execute(eq("query_frozen_sources"), any(), anyList(), any());
    }

    @Test
    void declaredGoalsPreventFinishingAParaphrasedCompoundQuestionAfterOneQuery() throws Exception {
        when(tools.isKnownTool("query_balances")).thenReturn(true);
        when(tools.isKnownTool("query_frozen_sources")).thenReturn(true);
        var response =
                call(
                        "plan_query",
                        "{\"goals\":[{\"tool\":\"query_balances\",\"parameters\":{\"sku\":\"螺栓\",\"warehouse\":\"一号仓\"}},{\"tool\":\"query_frozen_sources\",\"parameters\":{\"sku\":\"螺栓\",\"warehouse\":\"一号仓\"}}]}");
        ((ArrayNode) response.path("tool_calls"))
                .add(
                        call("query_balances", "{\"sku\":\"螺栓\",\"warehouse\":\"一号仓\"}")
                                .path("tool_calls")
                                .get(0));
        when(model.complete(any(), any(), any()))
                .thenReturn(
                        response,
                        call(
                                "finish_analysis",
                                "{\"claims\":[{\"rule\":\"query_balances\",\"evidence\":0}]}"));
        var result = ask(agent.create().id(), "看看螺栓在一号仓的货况和占用情况");
        assertEquals("PARTIAL", result.status());
        assertEquals("INSUFFICIENT_EVIDENCE", result.reason());
        assertEquals(1, result.toolCalls());
        assertEquals(1, result.results().size());
    }

    @Test
    void genericClarificationConsumesUserConditionsAndReplans() throws Exception {
        when(model.complete(any(), any(), any()))
                .thenReturn(
                        call("clarify", "{\"reason\":\"MISSING_INPUT\",\"tool\":\"NONE\"}"),
                        call("query_balances", "{\"sku\":\"螺栓\",\"warehouse\":\"一号仓\"}"));
        when(tools.execute(eq("clarify"), any(), anyList()))
                .thenReturn(
                        new Evidence(
                                "clarify",
                                "NEEDS_CLARIFICATION",
                                "请补充条件",
                                json.createObjectNode(),
                                List.of(),
                                List.of(),
                                Instant.now()));
        var first = ask(agent.create().id(), "查询库存");
        assertEquals(List.of("sku", "warehouse"), first.missingFields());
        var resumed =
                agent.input(
                        first.id(),
                        new AiAgentRequests.Input(
                                first.version(),
                                Map.of(),
                                Map.of("sku", "螺栓", "warehouse", "一号仓")));
        var result = waitFor(resumed.id());
        assertEquals("COMPLETED", result.status());
        verify(tools, times(1)).execute(eq("clarify"), any(), anyList());
        verify(model, times(2)).complete(any(), any(), any());
    }

    @Test
    void optionalWarehouseNeededByLocationIsAcceptedWithoutReaskingKnownSku() throws Exception {
        when(tools.definitions())
                .thenAnswer(
                        i -> {
                            var defs = json.createArrayNode();
                            var f = defs.addObject().put("type", "function").putObject("function");
                            f.put("name", "query_balances");
                            var p = f.putObject("parameters");
                            p.putArray("required").add("sku");
                            p.putObject("properties");
                            return defs;
                        });
        when(model.complete(any(), any(), any()))
                .thenReturn(call("query_balances", "{\"sku\":\"螺栓\",\"location\":\"A库位\"}"));
        when(executor.execute(eq("query_balances"), any(), anyList(), any()))
                .thenAnswer(
                        i ->
                                evidence(
                                        "query_balances",
                                        ((JsonNode) i.getArgument(1)).has("warehouse")
                                                ? "OK"
                                                : "NEEDS_CLARIFICATION"));
        var first = ask(agent.create().id(), "螺栓在A库位库存多少");
        assertEquals(List.of("warehouse"), first.missingFields());
        var result =
                waitFor(
                        agent.input(
                                        first.id(),
                                        new AiAgentRequests.Input(
                                                first.version(),
                                                Map.of(),
                                                Map.of("warehouse", "一号仓")))
                                .id());
        assertEquals("COMPLETED", result.status());
        verify(model, times(1)).complete(any(), any(), any());
    }

    @Test
    void naturalSupplementReplansSameTaskAndKeepsEvidenceHistory() throws Exception {
        when(model.complete(any(), any(), any()))
                .thenReturn(
                        call("query_balances", "{\"warehouse\":\"一号仓\"}"),
                        call("query_balances", "{\"sku\":\"螺栓\",\"warehouse\":\"一号仓\"}"));
        when(executor.execute(eq("query_balances"), any(), anyList(), any()))
                .thenAnswer(
                        i ->
                                evidence(
                                        "query_balances",
                                        ((JsonNode) i.getArgument(1)).has("sku")
                                                ? "OK"
                                                : "NEEDS_CLARIFICATION"));
        var first = ask(agent.create().id(), "一号仓还有多少库存");
        var result =
                waitFor(
                        agent.input(
                                        first.id(),
                                        new AiAgentRequests.Input(
                                                first.version(),
                                                Map.of(),
                                                Map.of(),
                                                Map.of(),
                                                "查螺栓，就在一号仓"))
                                .id());
        assertEquals(first.id(), result.id());
        assertEquals("COMPLETED", result.status());
        assertEquals(1, result.previousResults().size());
        assertTrue(result.question().contains("查螺栓"));
        verify(model, times(2)).complete(any(), any(), any());
    }

    @Test
    void currentSnapshotInsidePeriodAnalysisDoesNotEraseTheTasksDateRange() throws Exception {
        when(model.complete(any(), any(), any()))
                .thenReturn(
                        call(
                                "summarize_movements",
                                "{\"sku\":\"螺栓\",\"warehouse\":\"一号仓\",\"startDate\":\"2026-10-01\",\"endDate\":\"2026-10-07\"}"),
                        call("query_frozen_sources", "{}"),
                        call("query_ledgers", "{}"),
                        call(
                                "finish_analysis",
                                "{\"claims\":[{\"rule\":\"summarize_movements\",\"evidence\":0}]}"));
        var task = ask(agent.create().id(), "分析螺栓在一号仓库存下降及区间变化");
        assertEquals("OK", task.reason());
        verify(executor)
                .execute(
                        eq("query_ledgers"),
                        argThat(
                                args ->
                                        args.path("startDate").asText().equals("2026-10-01")
                                                && args.path("endDate")
                                                        .asText()
                                                        .equals("2026-10-07")),
                        anyList(),
                        any());
    }

    @Test
    void aVerifiedCodeAliasIsRewrittenToTheUserNameBeforeCandidateResolution() throws Exception {
        when(model.complete(any(), any(), any()))
                .thenReturn(
                        call(
                                "compare_inventory",
                                "{\"sku\":\"螺栓\",\"warehouse\":\"一号仓\",\"otherWarehouse\":\"AGENT_WH2\"}"));
        when(executor.mentionedName(eq("warehouse"), eq("AGENT_WH2"), anyString(), any()))
                .thenReturn("二号仓");
        var task = ask(agent.create().id(), "比较螺栓在一号仓和二号仓库存");
        assertEquals("OK", task.reason());
        verify(executor)
                .execute(
                        eq("compare_inventory"),
                        argThat(args -> args.path("otherWarehouse").asText().equals("二号仓")),
                        anyList(),
                        any());
        assertEquals(1, task.modelCalls());
    }

    @Test
    void modelCannotInventMissingWarehouseAndUserOnlySuppliesThatField() throws Exception {
        when(model.complete(any(), any(), any()))
                .thenReturn(
                        call(
                                "summarize_movements",
                                "{\"sku\":\"AGENT_BOLT\",\"warehouse\":\"默认仓库\",\"startDate\":\"2026-10-01\",\"endDate\":\"2026-10-07\"}"));
        when(executor.execute(eq("summarize_movements"), any(), anyList(), any()))
                .thenAnswer(
                        i -> {
                            JsonNode args = i.getArgument(1);
                            return evidence(
                                    "summarize_movements",
                                    args.has("warehouse") ? "OK" : "NEEDS_CLARIFICATION");
                        });
        var task = ask(agent.create().id(), "查询AGENT_BOLT最近七天的库存变化");
        assertEquals("NEEDS_CLARIFICATION", task.status());
        assertEquals(List.of("warehouse"), task.missingFields());
        verify(executor)
                .execute(
                        eq("summarize_movements"),
                        argThat(args -> !args.has("warehouse")),
                        anyList(),
                        any());
        agent.input(
                task.id(),
                new AiAgentRequests.Input(
                        task.version(), Map.of(), Map.of("warehouse", "AGENT_WH1")));
        var resumed = waitFor(task.id());
        assertEquals("OK", resumed.reason());
        assertEquals(1, resumed.modelCalls());
        verify(executor)
                .execute(
                        eq("summarize_movements"),
                        argThat(args -> args.path("warehouse").asText().equals("AGENT_WH1")),
                        anyList(),
                        any());
    }

    @Test
    void candidateRefinementAndRetryKeepOriginalPlanWithoutAnotherModelCall() throws Exception {
        when(model.complete(any(), any(), any()))
                .thenReturn(call("query_balances", "{\"sku\":\"螺栓\",\"warehouse\":\"一号仓\"}"));
        AtomicInteger queries = new AtomicInteger();
        when(executor.execute(eq("query_balances"), any(), anyList(), any()))
                .thenAnswer(
                        i -> {
                            JsonNode args = i.getArgument(1);
                            queries.incrementAndGet();
                            if (args.path("sku").asText().equals("螺栓"))
                                return new Evidence(
                                        "query_balances",
                                        "NEEDS_SELECTION",
                                        "请选择商品",
                                        json.createObjectNode(),
                                        List.of(new Candidate("sku", "螺栓", 1L, "SKU_BOLT", "螺栓")),
                                        List.of(),
                                        Instant.now());
                            return evidence("query_balances", "OK");
                        });
        var paused = ask(agent.create().id(), "螺栓在一号仓库存");
        agent.input(
                paused.id(),
                new AiAgentRequests.Input(
                        paused.version(), Map.of(), Map.of(), Map.of("sku:螺栓", "SKU_BOLT")));
        var complete = waitFor(paused.id());
        assertEquals("OK", complete.reason());
        assertEquals(1, complete.modelCalls());
        assertEquals("E2", complete.results().get(0).evidenceId());
        assertEquals("E1", complete.previousResults().get(0).evidenceId());
        var retry =
                agent.retry(
                        complete.id(), new AiAgentRequests.Retry(complete.version(), "retry-one"));
        assertEquals(
                retry.id(),
                agent.retry(
                                complete.id(),
                                new AiAgentRequests.Retry(complete.version(), "retry-one"))
                        .id());
        var retried = waitFor(retry.id());
        assertEquals("OK", retried.reason());
        assertEquals(0, retried.modelCalls());
        assertEquals(3, queries.get());
        verify(executor, times(2))
                .execute(
                        eq("query_balances"),
                        argThat(a -> a.path("sku").asText().equals("SKU_BOLT")),
                        anyList(),
                        any());
    }

    @Test
    void finishCannotUseBalanceAsEvidenceOfDeclineAndControlCallsDoNotConsumeToolBudget()
            throws Exception {
        when(model.complete(any(), any(), any()))
                .thenReturn(
                        call("query_balances", "{\"sku\":\"螺栓\",\"warehouse\":\"一号仓\"}"),
                        call(
                                "finish_analysis",
                                "{\"claims\":[{\"rule\":\"query_balances\",\"evidence\":0}]}"));
        var result = ask(agent.create().id(), "分析库存下降原因");
        assertEquals("INSUFFICIENT_EVIDENCE", result.reason());
        assertEquals("PARTIAL", result.status());
        assertEquals(1, result.toolCalls());
        assertEquals(1, result.results().size());
        assertEquals("E1", result.answer().conclusions().get(0).evidenceId());
    }

    @Test
    void lastAllowedRoundCanCompleteWhenCompletePeriodEvidenceExists() throws Exception {
        agent.shutdown();
        agent = create(1, 1, Duration.ofMinutes(30));
        when(model.complete(any(), any(), any()))
                .thenReturn(call("summarize_movements", "{\"sku\":\"螺栓\",\"warehouse\":\"一号仓\"}"));
        var result = ask(agent.create().id(), "分析最近七天库存变化");
        assertEquals("OK", result.reason());
        assertEquals(1, result.toolCalls());
        assertEquals(1, result.modelCalls());
    }

    ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    AiModelAdapter model = mock(AiModelAdapter.class);
    AiToolService tools = mock(AiToolService.class);
    AiReadOnlyToolExecutor executor = mock(AiReadOnlyToolExecutor.class);
    DatabaseUserDetailsService users = mock(DatabaseUserDetailsService.class);
    AiAgentService agent;
    AtomicReference<Authentication> permissions = new AtomicReference<>();

    @BeforeEach
    void init() {
        authenticate(1000L);
        permissions.set(SecurityContextHolder.getContext().getAuthentication());
        when(users.load(eq(1000L), anyString())).thenAnswer(i -> permissions.get());
        when(model.configurationStatus()).thenReturn("READY");
        when(model.questionTimeout()).thenReturn(Duration.ofSeconds(10));
        when(tools.definitions())
                .thenAnswer(
                        i -> {
                            ArrayNode definitions = json.createArrayNode();
                            for (String name :
                                    List.of(
                                            "query_balances",
                                            "query_frozen_sources",
                                            "query_sales_orders",
                                            "summarize_movements",
                                            "query_ledgers")) {
                                var f =
                                        definitions
                                                .addObject()
                                                .put("type", "function")
                                                .putObject("function");
                                f.put("name", name);
                                var params = f.putObject("parameters").put("type", "object");
                                params.putArray("required").add("sku").add("warehouse");
                                params.putObject("properties");
                            }
                            return definitions;
                        });
        when(tools.mayQuery(anyString())).thenReturn(true);
        when(tools.canReuse(any())).thenReturn(true);
        when(tools.authorizedView(any())).thenAnswer(i -> i.getArgument(0));
        when(executor.execute(anyString(), any(), anyList(), any()))
                .thenAnswer(i -> evidence(i.getArgument(0), "OK"));
        agent = create(4, 12, Duration.ofMinutes(30));
    }

    AiAgentService create(int rounds, int calls, Duration ttl) {
        return new AiAgentService(
                model,
                tools,
                executor,
                new AiAgentAnswerService(),
                users,
                new AiAgentProperties(rounds, calls, 65536, 100, 2, 10, ttl, Duration.ofMinutes(5)),
                json);
    }

    @AfterEach
    void stop() {
        agent.shutdown();
        SecurityContextHolder.clearContext();
    }

    void authenticate(Long id) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new AuthenticatedActor(id, "reader"),
                                null,
                                List.of(new SimpleGrantedAuthority("INVENTORY_READ"))));
    }

    ObjectNode call(String name, String arguments) {
        var response = json.createObjectNode().put("role", "assistant");
        response.putArray("tool_calls")
                .addObject()
                .put("id", UUID.randomUUID().toString())
                .put("type", "function")
                .putObject("function")
                .put("name", name)
                .put("arguments", arguments);
        return response;
    }

    Evidence evidence(String tool, String status) {
        ObjectNode data = json.createObjectNode();
        data.putObject("sku").put("name", "螺栓").put("code", "SKU_BOLT").put("id", 1);
        data.putObject("warehouse").put("name", "一号仓").put("code", "WH_ONE").put("id", 2);
        if (tool.equals("query_balances"))
            data.putObject("warehouses")
                    .putArray("records")
                    .addObject()
                    .put("warehouseId", 2)
                    .put("actualQuantity", "900719925474099.1234")
                    .put("availableQuantity", "900719925474089.1234")
                    .put("frozenQuantity", "10.0000");
        if (tool.equals("query_frozen_sources"))
            data.putObject("frozenTotals")
                    .put("salesQuantity", "6.0000")
                    .put("transferQuantity", "4.0000")
                    .put("frozenQuantity", "10.0000")
                    .put("differenceQuantity", "0.0000");
        return new Evidence(tool, status, "完整范围与分页分别标注", data, List.of(), List.of(), Instant.now());
    }

    AiAgentVO.Task waitFor(String id) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        AiAgentVO.Task task;
        do {
            task = agent.task(id);
            if (!task.status().equals("RUNNING")) {
                Thread.sleep(15);
                return agent.task(id);
            }
            Thread.sleep(10);
        } while (System.nanoTime() < end);
        throw new AssertionError("Agent did not finish");
    }

    AiAgentVO.Task ask(String session, String question) throws Exception {
        return waitFor(
                agent.submit(
                                session,
                                new AiAgentRequests.Question(
                                        question, UUID.randomUUID().toString()))
                        .id());
    }

    @Test
    void simpleQueryUsesOneModelCallAndPreservesDecimalEvidence() throws Exception {
        when(model.complete(any(), any(), any()))
                .thenReturn(call("query_balances", "{\"sku\":\"螺栓\",\"warehouse\":\"一号仓\"}"));
        var task = ask(agent.create().id(), "螺栓在一号仓还有多少库存");
        assertEquals("COMPLETED", task.status());
        assertEquals(1, task.modelCalls());
        assertTrue(task.answer().conclusions().get(0).text().contains("900719925474099.1234"));
        assertEquals("E1", task.answer().conclusions().get(0).facts().get(0).evidenceId());
    }

    @Test
    void followupUsesConfirmedScopeButQueriesFreshStockAndExplicitSwitchWins() throws Exception {
        when(model.complete(any(), any(), any()))
                .thenReturn(
                        call("query_balances", "{\"sku\":\"螺栓\",\"warehouse\":\"一号仓\"}"),
                        call("query_frozen_sources", "{}"),
                        call("query_balances", "{\"sku\":\"螺母\",\"warehouse\":\"二号仓\"}"));
        String session = agent.create().id();
        ask(session, "螺栓在一号仓库存");
        var follow = ask(session, "为什么可用量比实际量少");
        assertEquals("OK", follow.reason());
        assertEquals(1, follow.modelCalls(), "冻结工具本身提供同一快照的解释，无需模型再次确认");
        verify(executor)
                .execute(
                        eq("query_frozen_sources"),
                        argThat(
                                a ->
                                        a.path("sku").asText().equals("SKU_BOLT")
                                                && a.path("warehouse").asText().equals("WH_ONE")),
                        anyList(),
                        any());
        ask(session, "切换螺母在二号仓库存");
        verify(executor)
                .execute(
                        eq("query_balances"),
                        argThat(
                                a ->
                                        a.path("sku").asText().equals("螺母")
                                                && a.path("warehouse").asText().equals("二号仓")),
                        anyList(),
                        any());
    }

    @Test
    void multiStepAnalysisObservesEvidenceAndThenQueriesDocuments() throws Exception {
        List<ArrayNode> observed = new ArrayList<>();
        AtomicInteger round = new AtomicInteger();
        var responses =
                List.of(
                        call("summarize_movements", "{\"sku\":\"螺栓\",\"warehouse\":\"一号仓\"}"),
                        call(
                                "query_ledgers",
                                "{\"sku\":\"螺栓\",\"warehouse\":\"一号仓\",\"businessType\":\"OUTBOUND_SHIP\"}"),
                        call(
                                "finish_analysis",
                                "{\"claims\":[{\"rule\":\"summarize_movements\",\"evidence\":0},{\"rule\":\"query_ledgers\",\"evidence\":1}]}"));
        when(model.complete(any(), any(), any()))
                .thenAnswer(
                        i -> {
                            observed.add(((ArrayNode) i.getArgument(0)).deepCopy());
                            return responses.get(round.getAndIncrement());
                        });
        var task = ask(agent.create().id(), "分析螺栓最近七天库存为什么下降");
        assertEquals("OK", task.reason());
        assertEquals(2, task.results().size());
        assertEquals(3, task.modelCalls());
        assertTrue(
                observed.stream()
                        .anyMatch(
                                m ->
                                        m.toString().contains("tool_call_id")
                                                && m.toString().contains("summarize_movements")));
    }

    @Test
    void missingFieldResumesOriginalTaskWithoutReaskingWholeQuestion() throws Exception {
        when(model.complete(any(), any(), any()))
                .thenReturn(call("query_balances", "{\"sku\":\"螺栓\"}"));
        when(executor.execute(eq("query_balances"), any(), anyList(), any()))
                .thenAnswer(
                        i ->
                                ((JsonNode) i.getArgument(1)).has("warehouse")
                                        ? evidence("query_balances", "OK")
                                        : new Evidence(
                                                "query_balances",
                                                "NEEDS_CLARIFICATION",
                                                "请指定仓库",
                                                json.createObjectNode(),
                                                List.of(),
                                                List.of(),
                                                Instant.now()));
        String id =
                agent.submit(agent.create().id(), new AiAgentRequests.Question("查螺栓库存", "missing"))
                        .id();
        var waiting = waitFor(id);
        assertEquals(List.of("warehouse"), waiting.missingFields());
        agent.input(
                id,
                new AiAgentRequests.Input(waiting.version(), Map.of(), Map.of("warehouse", "一号仓")));
        var done = waitFor(id);
        assertEquals("OK", done.reason());
        assertEquals(1, done.modelCalls());
        assertEquals("查螺栓库存", done.question());
    }

    @Test
    void consecutiveSelectionsRetainPlanAndChoicesAndRejectForgedCandidate() throws Exception {
        when(model.complete(any(), any(), any()))
                .thenReturn(call("query_balances", "{\"sku\":\"螺栓\",\"warehouse\":\"一号仓\"}"));
        when(executor.execute(anyString(), any(), anyList(), any()))
                .thenAnswer(
                        i -> {
                            List<AiQuestionRequest.Selection> choices = i.getArgument(2);
                            String kind = choices.isEmpty() ? "sku" : "warehouse";
                            if (choices.size() == 2) return evidence("query_balances", "OK");
                            String keyword = kind.equals("sku") ? "螺栓" : "一号仓";
                            return new Evidence(
                                    "query_balances",
                                    "NEEDS_SELECTION",
                                    "请选择",
                                    json.createObjectNode(),
                                    List.of(
                                            new Candidate(
                                                    kind,
                                                    keyword,
                                                    kind.equals("sku") ? 1L : 2L,
                                                    "CODE",
                                                    "同名")),
                                    List.of(),
                                    Instant.now());
                        });
        var first = ask(agent.create().id(), "螺栓在一号仓库存");
        assertEquals("NEEDS_SELECTION", first.status());
        assertThrows(
                ResponseStatusException.class,
                () ->
                        agent.input(
                                first.id(),
                                new AiAgentRequests.Input(
                                        first.version(), Map.of("sku:螺栓", 99L), Map.of())));
        agent.input(
                first.id(),
                new AiAgentRequests.Input(first.version(), Map.of("sku:螺栓", 1L), Map.of()));
        var second = waitFor(first.id());
        assertEquals("NEEDS_SELECTION", second.status());
        agent.input(
                first.id(),
                new AiAgentRequests.Input(second.version(), Map.of("warehouse:一号仓", 2L), Map.of()));
        assertEquals("OK", waitFor(first.id()).reason());
        verify(model, times(1)).complete(any(), any(), any());
    }

    @Test
    void partialFailureAndInventedFactKeepSuccessfulEvidence() throws Exception {
        when(model.complete(any(), any(), any()))
                .thenReturn(
                        call("query_balances", "{\"sku\":\"螺栓\",\"warehouse\":\"一号仓\"}"),
                        call(
                                "finish_analysis",
                                "{\"claims\":[{\"rule\":\"query_balances\",\"evidence\":0,\"field\":\"/warehouses/records/0/actualQuantity\",\"value\":\"999\"}]}"));
        var t = ask(agent.create().id(), "分析库存为什么这样");
        assertEquals("PARTIAL", t.status());
        assertEquals("INVALID_MODEL_RESPONSE", t.reason());
        assertEquals(1, t.results().size());
        assertFalse(t.answer().conclusions().get(0).text().contains("999"));
    }

    @Test
    void repeatedCallStopsWithoutRepeatingDatabaseQuery() throws Exception {
        when(model.complete(any(), any(), any()))
                .thenAnswer(i -> call("query_balances", "{\"sku\":\"螺栓\",\"warehouse\":\"一号仓\"}"));
        var t = ask(agent.create().id(), "分析螺栓在一号仓库存为什么减少");
        assertEquals("REPEATED_TOOL_CALL", t.reason());
        verify(executor, times(1)).execute(anyString(), any(), anyList(), any());
    }

    @Test
    void sessionOwnershipUsesNumericIdentityAndClearRemovesTasks() throws Exception {
        var session = agent.create();
        String id =
                agent.submit(session.id(), new AiAgentRequests.Question("库存", "ownership")).id();
        authenticate(2000L);
        assertEquals(
                404,
                assertThrows(ResponseStatusException.class, () -> agent.task(id))
                        .getStatusCode()
                        .value());
        authenticate(Long.valueOf("1000"));
        assertEquals(session.id(), agent.session(session.id()).id());
        agent.clear(session.id());
        assertThrows(ResponseStatusException.class, () -> agent.task(id));
    }

    @Test
    void cancellationDuringModelWaitPreventsAnyToolFromStarting() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        when(model.complete(any(), any(), any()))
                .thenAnswer(
                        i -> {
                            entered.countDown();
                            Thread.sleep(10000);
                            return call("query_balances", "{}");
                        });
        var task = agent.submit(agent.create().id(), new AiAgentRequests.Question("库存", "cancel"));
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        assertEquals("CANCELLED", agent.cancel(task.id()).status());
        Thread.sleep(30);
        verifyNoInteractions(executor);
    }

    @Test
    void permissionsAreReloadedBeforeEveryToolAndRevocationStopsPlanning() throws Exception {
        when(model.complete(any(), any(), any()))
                .thenAnswer(
                        i -> {
                            permissions.set(null);
                            return call("query_balances", "{}");
                        });
        var t = ask(agent.create().id(), "库存");
        assertEquals("FORBIDDEN", t.reason());
        verifyNoInteractions(executor);
    }

    @Test
    void roundToolTimeAndExpiryLimitsAreIndependent() throws Exception {
        agent.shutdown();
        agent = create(1, 12, Duration.ofMinutes(30));
        when(model.complete(any(), any(), any()))
                .thenReturn(call("query_balances", "{\"sku\":\"螺栓\",\"warehouse\":\"一号仓\"}"));
        assertEquals("ROUND_LIMIT_EXCEEDED", ask(agent.create().id(), "分析为什么库存下降").reason());
        agent.shutdown();
        agent = create(4, 1, Duration.ofMinutes(30));
        assertEquals("TOOL_LIMIT_EXCEEDED", ask(agent.create().id(), "分析为什么库存下降").reason());
        agent.shutdown();
        agent = create(4, 12, Duration.ofMillis(10));
        var s = agent.create();
        Thread.sleep(30);
        assertThrows(ResponseStatusException.class, () -> agent.session(s.id()));
    }

    @Test
    void explicitConditionRequestResumesWithoutAnotherModelPlan() throws Exception {
        when(tools.isKnownTool("query_balances")).thenReturn(true);
        when(model.complete(any(), any(), any()))
                .thenReturn(
                        call(
                                "request_conditions",
                                "{\"queryTool\":\"query_balances\",\"parameters\":{\"sku\":\"螺栓\"}}"));
        when(executor.execute(anyString(), any(), anyList(), any()))
                .thenAnswer(
                        i ->
                                ((JsonNode) i.getArgument(1)).has("warehouse")
                                        ? evidence("query_balances", "OK")
                                        : new Evidence(
                                                "query_balances",
                                                "NEEDS_CLARIFICATION",
                                                "请指定仓库",
                                                json.createObjectNode(),
                                                List.of(),
                                                List.of(),
                                                Instant.now()));
        var waiting = ask(agent.create().id(), "查询螺栓库存");
        assertEquals(List.of("warehouse"), waiting.missingFields());
        agent.input(
                waiting.id(),
                new AiAgentRequests.Input(waiting.version(), Map.of(), Map.of("warehouse", "一号仓")));
        var done = waitFor(waiting.id());
        assertEquals("OK", done.reason());
        assertEquals(1, done.modelCalls());
    }

    @Test
    void toolFailureRetainsIndependentEvidenceAndDoesNotBecomeZeroStock() throws Exception {
        when(model.complete(any(), any(), any()))
                .thenReturn(
                        call("query_balances", "{\"sku\":\"螺栓\",\"warehouse\":\"一号仓\"}"),
                        call("query_ledgers", "{\"sku\":\"螺栓\",\"warehouse\":\"一号仓\"}"),
                        call(
                                "finish_analysis",
                                "{\"claims\":[{\"rule\":\"query_balances\",\"evidence\":0}]}"));
        when(executor.execute(eq("query_ledgers"), any(), anyList(), any()))
                .thenReturn(evidence("query_ledgers", "QUERY_FAILED"));
        var result = ask(agent.create().id(), "分析库存为什么减少");
        assertEquals("PARTIAL", result.status());
        assertEquals("QUERY_FAILED", result.reason());
        assertEquals(2, result.results().size());
        assertTrue(result.answer().conclusions().get(0).text().contains("900719925474099.1234"));
        assertFalse(result.answer().uncertainties().isEmpty());
    }

    @Test
    void staleQueryCannotChangeScopeAfterCancellationAndNewTask() throws Exception {
        CountDownLatch oldEntered = new CountDownLatch(1), releaseOld = new CountDownLatch(1);
        AtomicInteger execution = new AtomicInteger();
        when(model.complete(any(), any(), any()))
                .thenAnswer(i -> call("query_balances", "{\"sku\":\"螺栓\",\"warehouse\":\"一号仓\"}"));
        when(executor.execute(anyString(), any(), anyList(), any()))
                .thenAnswer(
                        i -> {
                            int n = execution.incrementAndGet();
                            if (n == 1) {
                                oldEntered.countDown();
                                assertTrue(releaseOld.await(3, TimeUnit.SECONDS));
                            }
                            var e = evidence("query_balances", "OK");
                            ((ObjectNode) e.data().path("warehouse"))
                                    .put("code", n == 1 ? "WH_OLD" : "WH_NEW");
                            return e;
                        });
        String session = agent.create().id();
        var old = agent.submit(session, new AiAgentRequests.Question("库存", "old"));
        assertTrue(oldEntered.await(2, TimeUnit.SECONDS));
        agent.cancel(old.id());
        var fresh = ask(session, "查询新仓库库存");
        assertEquals("OK", fresh.reason());
        releaseOld.countDown();
        Thread.sleep(40);
        assertEquals("WH_NEW", agent.session(session).context().get("warehouse"));
        assertEquals("CANCELLED", agent.task(old.id()).status());
    }

    @Test
    void candidateResumeArchivesPriorEvidenceAndRequeriesCompletedSteps() throws Exception {
        var first = call("query_balances", "{\"sku\":\"螺栓\",\"warehouse\":\"一号仓\"}");
        first.withArray("tool_calls")
                .add(
                        call("query_frozen_sources", "{\"sku\":\"螺栓\",\"warehouse\":\"一号仓\"}")
                                .path("tool_calls")
                                .get(0));
        when(model.complete(any(), any(), any()))
                .thenReturn(
                        first,
                        call(
                                "finish_analysis",
                                "{\"claims\":[{\"rule\":\"query_balances\",\"evidence\":0},{\"rule\":\"query_frozen_sources\",\"evidence\":1}]}"));
        AtomicInteger balances = new AtomicInteger();
        when(executor.execute(eq("query_balances"), any(), anyList(), any()))
                .thenAnswer(
                        i -> {
                            var e = evidence("query_balances", "OK");
                            if (balances.incrementAndGet() > 1)
                                ((ObjectNode) e.data().path("warehouses").path("records").get(0))
                                        .put("actualQuantity", "50.0000")
                                        .put("availableQuantity", "40.0000");
                            return e;
                        });
        when(executor.execute(eq("query_frozen_sources"), any(), anyList(), any()))
                .thenAnswer(
                        i ->
                                ((List<?>) i.getArgument(2)).isEmpty()
                                        ? new Evidence(
                                                "query_frozen_sources",
                                                "NEEDS_SELECTION",
                                                "请选择仓库",
                                                json.createObjectNode(),
                                                List.of(
                                                        new Candidate(
                                                                "warehouse",
                                                                "一号仓",
                                                                2L,
                                                                "WH_ONE",
                                                                "一号仓")),
                                                List.of(),
                                                Instant.now())
                                        : evidence("query_frozen_sources", "OK"));
        var paused = ask(agent.create().id(), "为什么库存与冻结不同");
        agent.input(
                paused.id(),
                new AiAgentRequests.Input(paused.version(), Map.of("warehouse:一号仓", 2L), Map.of()));
        var done = waitFor(paused.id());
        assertEquals("OK", done.reason());
        assertEquals(2, balances.get());
        assertEquals(2, done.previousResults().size());
        assertEquals(
                "50.0000",
                done.results()
                        .get(0)
                        .data()
                        .path("warehouses")
                        .path("records")
                        .get(0)
                        .path("actualQuantity")
                        .asText());
    }

    @Test
    void sevenDaysUsesServerCalendarAndContextLimitKeepsEvidence() throws Exception {
        when(tools.definitions())
                .thenAnswer(
                        i -> {
                            var definitions = json.createArrayNode();
                            var f =
                                    definitions
                                            .addObject()
                                            .put("type", "function")
                                            .putObject("function");
                            f.put("name", "summarize_movements");
                            f.putObject("parameters")
                                    .putArray("required")
                                    .add("sku")
                                    .add("warehouse")
                                    .add("startDate")
                                    .add("endDate");
                            return definitions;
                        });
        when(model.complete(any(), any(), any()))
                .thenReturn(
                        call("summarize_movements", "{\"sku\":\"螺栓\",\"warehouse\":\"一号仓\"}"),
                        call(
                                "finish_analysis",
                                "{\"claims\":[{\"rule\":\"summarize_movements\",\"evidence\":0}]}"));
        var task = ask(agent.create().id(), "分析最近七天库存变化");
        assertEquals("OK", task.reason());
        var today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        verify(executor)
                .execute(
                        eq("summarize_movements"),
                        argThat(
                                a ->
                                        a.path("startDate")
                                                        .asText()
                                                        .equals(today.minusDays(6).toString())
                                                && a.path("endDate")
                                                        .asText()
                                                        .equals(today.toString())),
                        anyList(),
                        any());
        agent.shutdown();
        agent =
                new AiAgentService(
                        model,
                        tools,
                        executor,
                        new AiAgentAnswerService(),
                        users,
                        new AiAgentProperties(
                                4,
                                12,
                                8192,
                                100,
                                2,
                                10,
                                Duration.ofMinutes(30),
                                Duration.ofMinutes(5)),
                        json);
        when(model.complete(any(), any(), any()))
                .thenReturn(call("summarize_movements", "{\"sku\":\"螺栓\",\"warehouse\":\"一号仓\"}"));
        var large = evidence("summarize_movements", "OK");
        ((ObjectNode) large.data()).put("scopeDescription", "x".repeat(12000));
        when(executor.execute(anyString(), any(), anyList(), any())).thenReturn(large);
        var limited = ask(agent.create().id(), "分析最近七天库存变化");
        assertEquals("CONTEXT_LIMIT_EXCEEDED", limited.reason());
        assertEquals(1, limited.results().size());
    }
}
