package com.stockpilot.ai;

import com.fasterxml.jackson.databind.*;
import com.stockpilot.StockPilotApplication;
import com.stockpilot.acceptance.IntegrationTestInfrastructure;
import com.stockpilot.ai.infrastructure.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.*;

/**
 * Opt-in CLI: real DeepSeek + dedicated MySQL + JWT/HTTP. Never selected by ordinary test profiles.
 */
public class DeepSeekAgentAcceptance {
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    static final AtomicInteger CALLS = new AtomicInteger();
    static JsonNode credentials;
    static final TestRestTemplate HTTP = new TestRestTemplate();
    static String base, token;
    static int cases;

    public static void main(String[] ignored) throws Exception {
        credentials =
                JSON.readTree(
                        new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))
                                .readLine());
        boolean browserMode = credentials.path("mode").asText().equals("browser");
        boolean interactive =
                browserMode || credentials.path("mode").asText().equals("live-browser");
        boolean declineOnly = credentials.path("case").asText().equals("decline_only");
        boolean businessOnly = credentials.path("case").asText().equals("business_queries");
        if (!browserMode
                && !Set.of("deepseek-flash", "deepseek-chat", "deepseek-v4-pro")
                        .contains(credentials.path("model").asText()))
            throw new IllegalArgumentException("Unsupported model");
        String adminUrl =
                System.getenv()
                        .getOrDefault(
                                "STOCKPILOT_IT_ADMIN_URL",
                                "jdbc:mysql://localhost:33307/?serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false");
        String adminUser = System.getenv().getOrDefault("STOCKPILOT_IT_ADMIN_USER", "root");
        String adminPassword =
                System.getenv().getOrDefault("STOCKPILOT_IT_ADMIN_PASSWORD", "root_dev_only");
        String database = IntegrationTestInfrastructure.databaseName("stockpilot_agent_live_it");
        try (Connection c = DriverManager.getConnection(adminUrl, adminUser, adminPassword);
                Statement sql = c.createStatement()) {
            sql.execute(
                    "CREATE DATABASE "
                            + database
                            + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        com.sun.net.httpserver.HttpServer fixture = browserMode ? browserFixture() : null;
        try {
            var settings = new HashMap<String, Object>();
            settings.put(
                    "spring.datasource.url",
                    IntegrationTestInfrastructure.databaseUrl(adminUrl, database));
            settings.put("spring.datasource.username", adminUser);
            settings.put("spring.datasource.password", adminPassword);
            settings.put("stockpilot.cache.enabled", false);
            settings.put("stockpilot.messaging.enabled", false);
            settings.put("server.port", 0);
            settings.put(
                    "stockpilot.security.jwt-secret",
                    "agent-live-test-only-signing-secret-32-chars");
            settings.put("stockpilot.security.bootstrap-admin-username", "agent_live_admin");
            settings.put("stockpilot.security.bootstrap-admin-password", "AgentLive!2026");
            settings.put("stockpilot.ai.enabled", true);
            settings.put("stockpilot.ai.provider", browserMode ? "CUSTOM" : "DEEPSEEK");
            settings.put(
                    "stockpilot.ai.base-url",
                    browserMode
                            ? "http://127.0.0.1:" + fixture.getAddress().getPort()
                            : "https://api.deepseek.com");
            settings.put("stockpilot.ai.model", credentials.path("model").asText());
            settings.put("stockpilot.ai.api-key", credentials.path("apiKey").asText());
            try (var context =
                    new SpringApplicationBuilder(LiveApplication.class, LiveConfiguration.class)
                            .profiles("agent-live-acceptance")
                            .initializers(
                                    c ->
                                            c.getEnvironment()
                                                    .getPropertySources()
                                                    .addFirst(
                                                            new MapPropertySource(
                                                                    "dedicatedAgentAcceptance",
                                                                    settings)))
                            .run()) {
                credentials = null;
                settings.clear();
                base =
                        "http://127.0.0.1:"
                                + ((ServletWebServerApplicationContext) context)
                                        .getWebServer()
                                        .getPort();
                token =
                        post(
                                        "/api/auth/login",
                                        Map.of(
                                                "username",
                                                "agent_live_admin",
                                                "password",
                                                "AgentLive!2026"))
                                .path("accessToken")
                                .asText();
                long sku =
                        post(
                                        "/api/master-data/skus",
                                        Map.of("code", "AGENT_BOLT", "name", "螺栓", "unit", "件"))
                                .path("id")
                                .asLong();
                long first = warehouse("AGENT_WH1", "一号仓"), second = warehouse("AGENT_WH2", "二号仓");
                long firstLocation = location(first, "AGENT_LOC1"),
                        secondLocation = location(second, "AGENT_LOC2");
                receipt("AGENT_PR1", first, firstLocation, sku, "100.0000");
                receipt("AGENT_PR2", second, secondLocation, sku, "80.0000");
                var sales =
                        post(
                                "/api/outbound/sales-orders",
                                document(
                                        "outboundNo",
                                        "AGENT_SO1",
                                        first,
                                        firstLocation,
                                        sku,
                                        "20.0000"));
                post(
                        "/api/outbound/sales-orders/" + sales.path("id").asLong() + "/reserve",
                        Map.of("version", sales.path("version").asInt()));
                if (interactive) {
                    if (!browserMode)
                        prepareDecline(sku, first, second, firstLocation, secondLocation);
                    warehouse("AGENT_WH1_DUP", "一号仓");
                    post(
                            "/api/master-data/skus",
                            Map.of("code", "AGENT_BOLT2", "name", "螺栓", "unit", "件"));
                    System.out.println(
                            "BROWSER_ACCEPTANCE_READY backendPort="
                                    + ((ServletWebServerApplicationContext) context)
                                            .getWebServer()
                                            .getPort());
                    long expires = System.nanoTime() + Duration.ofMinutes(30).toNanos();
                    while (!java.nio.file.Files.exists(
                                    java.nio.file.Path.of(
                                            "local-agent-browser-" + database + ".stop.tmp"))
                            && System.nanoTime() < expires) Thread.sleep(500);
                    return;
                }
                String session = post("/api/ai/sessions", null).path("id").asText();
                if (businessOnly) {
                    var inventory = ask(session, "查询全部仓库全部商品，可用库存低于90的库存记录有哪些？");
                    expect(
                            "all_inventory_threshold",
                            inventory.path("reason").asText().equals("OK")
                                    && find(inventory, "list_inventory")
                                                    .path("inventory")
                                                    .path("total")
                                                    .asInt()
                                            == 2);
                    for (JsonNode row :
                            find(inventory, "list_inventory").path("inventory").path("records"))
                        if (!decimal(row.path("availableQuantity"), "80"))
                            throw new AssertionError("Wrong inventory threshold evidence");
                    var salesList = ask(session, "全公司有哪些销售单还没完成？查询销售单列表。");
                    expect(
                            "global_unfinished_sales",
                            salesList.path("reason").asText().equals("OK")
                                    && find(salesList, "list_documents")
                                            .path("documents")
                                            .path("records")
                                            .get(0)
                                            .path("businessNo")
                                            .asText()
                                            .equals("AGENT_SO1"));
                    var purchases = ask(session, "全公司今天完成入库的采购单有哪些？按完成时间查采购单列表。");
                    expect(
                            "completed_purchase_dates",
                            purchases.path("reason").asText().equals("OK")
                                    && find(purchases, "list_documents")
                                                    .path("documents")
                                                    .path("total")
                                                    .asInt()
                                            == 2
                                    && find(purchases, "list_documents")
                                            .path("dateField")
                                            .asText()
                                            .equals("COMPLETED"));
                    String fresh = post("/api/ai/sessions", null).path("id").asText();
                    var missing = ask(fresh, "查AGENT_BOLT当前有效销售和调拨冻结来源，并核对冻结总量。");
                    if (!missing.path("status").asText().equals("NEEDS_CLARIFICATION"))
                        throw new AssertionError("Expected missing warehouse");
                    var supplemented =
                            waitFor(
                                    post(
                                            "/api/ai/tasks/"
                                                    + missing.path("id").asText()
                                                    + "/input",
                                            Map.of(
                                                    "version",
                                                    missing.path("version").asLong(),
                                                    "message",
                                                    "就在一号仓")));
                    expect(
                            "natural_supplement",
                            supplemented.path("reason").asText().equals("OK")
                                    && decimal(
                                            find(supplemented, "query_frozen_sources")
                                                    .path("frozenTotals")
                                                    .path("sourceQuantity"),
                                            "20"));
                    System.out.println(
                            "AGENT_BUSINESS_LIVE_RESULT passed="
                                    + cases
                                    + " cases=4 modelRequests="
                                    + CALLS.get());
                    return;
                }
                if (declineOnly) {
                    prepareDecline(sku, first, second, firstLocation, secondLocation);
                    var decline =
                            ask(
                                    session,
                                    "AGENT_BOLT在一号仓最近七天库存为什么下降？请汇总完整区间变化，并追溯对应单据区分销售出库、调拨和盘亏。");
                    checkDecline(decline);
                    return;
                }
                var balance = ask(session, "AGENT_BOLT在一号仓还有多少实际、可用和冻结库存？");
                System.out.println(
                        "AGENT_LIVE_DIAGNOSTIC status="
                                + balance.path("status").asText()
                                + " reason="
                                + balance.path("reason").asText()
                                + " tools="
                                + java.util.stream.StreamSupport.stream(
                                                balance.path("results").spliterator(), false)
                                        .map(
                                                e ->
                                                        e.path("tool").asText()
                                                                + ":"
                                                                + e.path("status").asText())
                                        .toList());
                expect(
                        "simple",
                        balance.path("reason").asText().equals("OK")
                                && find(balance, "query_balances")
                                        .path("warehouses")
                                        .path("records")
                                        .get(0)
                                        .path("frozenQuantity")
                                        .asText()
                                        .equals("20.0000"));
                var frozen = ask(session, "为什么可用量比实际量少？请核对当前冻结来源。");
                expect(
                        "followup",
                        frozen.path("reason").asText().equals("OK")
                                && decimal(
                                        find(frozen, "query_frozen_sources")
                                                .path("frozenTotals")
                                                .path("sourceQuantity"),
                                        "20"));
                var orders = ask(session, "哪些销售单还没处理完？");
                expect(
                        "unfinished",
                        orders.path("reason").asText().equals("OK")
                                && find(orders, "query_sales_orders")
                                        .path("salesOrders")
                                        .path("records")
                                        .get(0)
                                        .path("businessNo")
                                        .asText()
                                        .equals("AGENT_SO1"));
                var compare = ask(session, "比较AGENT_BOLT在一号仓和二号仓的实际、可用及冻结库存。");
                expect(
                        "comparison",
                        compare.path("reason").asText().equals("OK")
                                && decimal(
                                        find(compare, "compare_inventory")
                                                .path("difference")
                                                .path("actualQuantity"),
                                        "20"));
                var analysis = ask(session, "AGENT_BOLT在一号仓最近七天实际库存为什么下降？请按业务动作解释，必要时核对单据。");
                expect(
                        "period",
                        analysis.path("reason").asText().equals("OK")
                                && decimal(
                                        find(analysis, "summarize_movements")
                                                .path("summary")
                                                .path("changeActualQuantity"),
                                        "0"));
                var switched = ask(session, "改查AGENT_BOLT在二号仓当前库存。");
                expect(
                        "switch",
                        switched.path("reason").asText().equals("OK")
                                && decimal(
                                        find(switched, "query_balances")
                                                .path("warehouses")
                                                .path("records")
                                                .get(0)
                                                .path("actualQuantity"),
                                        "80"));
                prepareDecline(sku, first, second, firstLocation, secondLocation);
                var decline =
                        ask(session, "AGENT_BOLT在一号仓最近七天库存为什么下降？请汇总完整区间变化，并追溯对应单据区分销售出库、调拨和盘亏。");
                checkDecline(decline);
                var ledger =
                        request(
                                        HttpMethod.GET,
                                        "/api/inventory/ledgers?warehouseId="
                                                + first
                                                + "&skuId="
                                                + sku
                                                + "&businessNo=AGENT_SHIP&businessType=OUTBOUND_SHIP",
                                        null)
                                .path("records")
                                .get(0);
                var traced = ask(session, "追溯流水" + ledger.path("ledgerNo").asText() + "对应的业务单据");
                expect(
                        "trace",
                        traced.path("reason").asText().equals("OK")
                                && find(traced, "trace_ledger")
                                        .path("outboundNo")
                                        .asText()
                                        .equals("AGENT_SHIP"));
                post(
                        "/api/master-data/skus",
                        Map.of("code", "AGENT_BOLT2", "name", "螺栓", "unit", "件"));
                var choice = ask(session, "螺栓在一号仓库存是多少？");
                if (!choice.path("status").asText().equals("NEEDS_SELECTION"))
                    throw new AssertionError("Expected ambiguity");
                choice =
                        post(
                                "/api/ai/tasks/" + choice.path("id").asText() + "/input",
                                Map.of(
                                        "version",
                                        choice.path("version").asLong(),
                                        "choices",
                                        Map.of("sku:螺栓", sku),
                                        "conditions",
                                        Map.of()));
                choice = waitFor(choice);
                expect(
                        "candidate",
                        choice.path("reason").asText().equals("OK")
                                && decimal(
                                        find(choice, "query_balances")
                                                .path("warehouses")
                                                .path("records")
                                                .get(0)
                                                .path("actualQuantity"),
                                        "86"));
                System.out.println(
                        "AGENT_LIVE_RESULT passed="
                                + cases
                                + " cases=9 modelRequests="
                                + CALLS.get()
                                + " database=REAL model=REAL transport=HTTP");
            }
        } finally {
            System.out.println(
                    "AGENT_LIVE_TOTAL modelRequests=" + CALLS.get() + " passed=" + cases);
            if (fixture != null) fixture.stop(0);
            credentials = null;
            token = null;
            try (Connection c = DriverManager.getConnection(adminUrl, adminUser, adminPassword);
                    Statement sql = c.createStatement()) {
                sql.execute("DROP DATABASE IF EXISTS " + database);
            }
        }
    }

    static boolean decimal(JsonNode value, String expected) {
        return value.isTextual()
                && new BigDecimal(value.asText()).compareTo(new BigDecimal(expected)) == 0;
    }

    static void checkDecline(JsonNode decline) {
        System.out.println(
                "AGENT_DECLINE_DIAGNOSTIC status="
                        + decline.path("status").asText()
                        + " reason="
                        + decline.path("reason").asText()
                        + " tools="
                        + java.util.stream.StreamSupport.stream(
                                        decline.path("results").spliterator(), false)
                                .map(e -> e.path("tool").asText() + ":" + e.path("status").asText())
                                .toList());
        expect(
                "decline_documents",
                decline.path("reason").asText().equals("OK")
                        && decimal(
                                find(decline, "summarize_movements")
                                        .path("summary")
                                        .path("changeActualQuantity"),
                                "-14")
                        && find(decline, "summarize_documents")
                                        .path("documentMovements")
                                        .path("records")
                                        .size()
                                >= 3);
    }

    static JsonNode find(JsonNode task, String tool) {
        for (JsonNode evidence : task.path("results"))
            if (evidence.path("tool").asText().equals(tool)) return evidence.path("data");
        throw new AssertionError(
                "Missing evidence " + tool + " reason=" + task.path("reason").asText());
    }

    static void expect(String name, boolean result) {
        System.out.println(
                "AGENT_LIVE_CASE name="
                        + name
                        + " passed="
                        + result
                        + " modelRequests="
                        + CALLS.get());
        if (!result) throw new AssertionError("Case failed: " + name);
        cases++;
    }

    static JsonNode ask(String session, String question) throws Exception {
        return waitFor(
                post(
                        "/api/ai/sessions/" + session + "/tasks",
                        Map.of("question", question, "requestId", UUID.randomUUID().toString())));
    }

    static JsonNode waitFor(JsonNode task) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(95).toNanos();
        while (task.path("status").asText().equals("RUNNING") && System.nanoTime() < deadline) {
            Thread.sleep(100);
            task = request(HttpMethod.GET, "/api/ai/tasks/" + task.path("id").asText(), null);
        }
        Thread.sleep(30);
        return task;
    }

    static long warehouse(String code, String name) {
        return post("/api/master-data/warehouses", Map.of("code", code, "name", name))
                .path("id")
                .asLong();
    }

    static long location(long warehouse, String code) {
        return post(
                        "/api/master-data/locations",
                        Map.of("warehouseId", warehouse, "code", code, "name", code))
                .path("id")
                .asLong();
    }

    static Map<String, Object> document(
            String key, String number, long warehouse, long location, long sku, String quantity) {
        return Map.of(
                key,
                number,
                "warehouseId",
                warehouse,
                "lines",
                List.of(Map.of("locationId", location, "skuId", sku, "quantity", quantity)));
    }

    static void receipt(String number, long warehouse, long location, long sku, String quantity) {
        var draft =
                post(
                        "/api/inbound/purchase-receipts",
                        document("receiptNo", number, warehouse, location, sku, quantity));
        String path = "/api/inbound/purchase-receipts/" + draft.path("id").asLong();
        var submitted = post(path + "/submit", Map.of("version", draft.path("version").asInt()));
        post(path + "/approve", Map.of("version", submitted.path("version").asInt()));
        post(path + "/complete", null);
    }

    static void prepareDecline(
            long sku, long first, long second, long firstLocation, long secondLocation) {
        var ship =
                post(
                        "/api/outbound/sales-orders",
                        document("outboundNo", "AGENT_SHIP", first, firstLocation, sku, "10.0000"));
        String shipPath = "/api/outbound/sales-orders/" + ship.path("id").asLong();
        ship = post(shipPath + "/reserve", Map.of("version", ship.path("version").asInt()));
        post(shipPath + "/approve", Map.of("version", ship.path("version").asInt()));
        post(shipPath + "/complete", null);
        var transfer =
                post(
                        "/api/transfers",
                        Map.of(
                                "transferNo",
                                "AGENT_MOVE",
                                "sourceWarehouseId",
                                first,
                                "targetWarehouseId",
                                second,
                                "lines",
                                List.of(
                                        Map.of(
                                                "sourceLocationId",
                                                firstLocation,
                                                "targetLocationId",
                                                secondLocation,
                                                "skuId",
                                                sku,
                                                "quantity",
                                                "5.0000"))));
        String transferPath = "/api/transfers/" + transfer.path("id").asLong();
        transfer =
                post(transferPath + "/submit", Map.of("version", transfer.path("version").asInt()));
        post(transferPath + "/approve", Map.of("version", transfer.path("version").asInt()));
        post(transferPath + "/dispatch", null);
        var count =
                post(
                        "/api/inventory-counts",
                        Map.of(
                                "countNo",
                                "AGENT_LOSS",
                                "warehouseId",
                                first,
                                "dimensions",
                                List.of(Map.of("locationId", firstLocation, "skuId", sku))));
        String countPath = "/api/inventory-counts/" + count.path("id").asLong();
        count = post(countPath + "/start", Map.of("version", count.path("version").asInt()));
        count =
                request(
                        HttpMethod.PUT,
                        countPath + "/results",
                        Map.of(
                                "version",
                                count.path("version").asInt(),
                                "results",
                                List.of(
                                        Map.of(
                                                "lineId",
                                                count.path("lines").get(0).path("id").asLong(),
                                                "countedQuantity",
                                                "83.0000",
                                                "reason",
                                                "Dedicated acceptance fixture"))));
        count = post(countPath + "/submit", Map.of("version", count.path("version").asInt()));
        post(countPath + "/approve", Map.of("version", count.path("version").asInt()));
        post(countPath + "/adjust", null);
        receipt("AGENT_REPLENISH", first, firstLocation, sku, "3.0000");
    }

    static JsonNode post(String path, Object body) {
        return request(HttpMethod.POST, path, body);
    }

    static JsonNode request(HttpMethod method, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        var response =
                HTTP.exchange(base + path, method, new HttpEntity<>(body, headers), JsonNode.class);
        if (!response.getStatusCode().is2xxSuccessful()
                || response.getBody() == null
                || !response.getBody().path("code").asText().equals("SUCCESS"))
            throw new AssertionError(
                    "HTTP request failed " + path + " status=" + response.getStatusCode().value());
        return response.getBody().path("data");
    }

    static com.sun.net.httpserver.HttpServer browserFixture() throws IOException {
        var server =
                com.sun.net.httpserver.HttpServer.create(
                        new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/chat/completions",
                exchange -> {
                    try {
                        JsonNode request = JSON.readTree(exchange.getRequestBody());
                        JsonNode messages = request.path("messages");
                        JsonNode last = messages.get(messages.size() - 1);
                        String name, arguments;
                        if (last.path("role").asText().equals("tool")) {
                            String rule =
                                    JSON.readTree(last.path("content").asText())
                                            .path("tool")
                                            .asText();
                            name = "finish_analysis";
                            arguments =
                                    JSON.writeValueAsString(
                                            Map.of(
                                                    "claims",
                                                    List.of(Map.of("rule", rule, "evidence", 0))));
                        } else {
                            String question = last.path("content").asText().split("\\n", 2)[0];
                            name =
                                    question.contains("冻结") || question.contains("为什么")
                                            ? "query_frozen_sources"
                                            : question.contains("未处理")
                                                    ? "query_sales_orders"
                                                    : "query_balances";
                            arguments =
                                    question.contains("螺栓") && !question.contains("AGENT_BOLT")
                                            ? "{\"sku\":\"螺栓\",\"warehouse\":\"一号仓\"}"
                                            : "{\"sku\":\"AGENT_BOLT\",\"warehouse\":\"一号仓\"}";
                        }
                        Thread.sleep(1500);
                        byte[] bytes =
                                JSON.writeValueAsBytes(
                                        Map.of(
                                                "choices",
                                                List.of(
                                                        Map.of(
                                                                "finish_reason",
                                                                "tool_calls",
                                                                "message",
                                                                Map.of(
                                                                        "role",
                                                                        "assistant",
                                                                        "tool_calls",
                                                                        List.of(
                                                                                Map.of(
                                                                                        "id",
                                                                                        UUID.randomUUID()
                                                                                                .toString(),
                                                                                        "type",
                                                                                        "function",
                                                                                        "function",
                                                                                        Map.of(
                                                                                                "name",
                                                                                                name,
                                                                                                "arguments",
                                                                                                arguments))))))));
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, bytes.length);
                        exchange.getResponseBody().write(bytes);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        exchange.close();
                    }
                });
        server.start();
        return server;
    }

    @Configuration(proxyBeanMethods = false)
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    @ComponentScan(
            basePackages = "com.stockpilot",
            excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = StockPilotApplication.class),
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern =
                                "com\\.stockpilot\\.security\\.TestProtectedController|com\\.stockpilot\\..*(Test|IT)\\$.*")
            })
    @Profile("agent-live-acceptance")
    static class LiveApplication {}

    @Configuration
    @Profile("agent-live-acceptance")
    static class LiveConfiguration {
        /** First INSERT only, in this disposable schema; no existing audit row is changed. */
        @Bean
        org.apache.ibatis.plugin.Interceptor historicalOpeningClock() {
            return new OpeningClock();
        }

        @Bean
        @Primary
        AiModelAdapter countedModel(AiProperties properties, ObjectMapper json) {
            return new AiModelAdapter(properties, json) {
                @Override
                public com.fasterxml.jackson.databind.node.ObjectNode complete(
                        com.fasterxml.jackson.databind.node.ArrayNode messages,
                        com.fasterxml.jackson.databind.node.ArrayNode tools,
                        Duration remaining) {
                    if (CALLS.get() >= 28) throw new ModelFailure("LIVE_REQUEST_LIMIT");
                    CALLS.incrementAndGet();
                    var response = super.complete(messages, tools, remaining);
                    for (JsonNode call : response.path("tool_calls"))
                        if (call.path("function").path("name").asText().equals("finish_analysis"))
                            System.out.println(
                                    "AGENT_FINISH_DIAGNOSTIC args="
                                            + call.path("function").path("arguments").asText());
                    return response;
                }
            };
        }
    }

    @org.apache.ibatis.plugin.Intercepts(
            @org.apache.ibatis.plugin.Signature(
                    type = org.apache.ibatis.executor.Executor.class,
                    method = "update",
                    args = {org.apache.ibatis.mapping.MappedStatement.class, Object.class}))
    static class OpeningClock implements org.apache.ibatis.plugin.Interceptor {
        @Override
        public Object intercept(org.apache.ibatis.plugin.Invocation invocation) throws Throwable {
            var statement = (org.apache.ibatis.mapping.MappedStatement) invocation.getArgs()[0];
            Object parameter = invocation.getArgs()[1];
            if (!statement
                            .getId()
                            .equals("com.stockpilot.inventory.mapper.InventoryLedgerMapper.insert")
                    || !(parameter
                            instanceof com.stockpilot.inventory.domain.InventoryLedgerEntity ledger)
                    || !Set.of("AGENT_PR1", "AGENT_PR2").contains(ledger.getBusinessNo()))
                return invocation.proceed();
            var executor = (org.apache.ibatis.executor.Executor) invocation.getTarget();
            Connection connection = executor.getTransaction().getConnection();
            if (!connection.getCatalog().matches("stockpilot_agent_live_it_[a-f0-9]{12}"))
                throw new IllegalStateException(
                        "Opening clock requires a dedicated disposable schema");
            try (Statement clock = connection.createStatement()) {
                clock.execute(
                        "SET SESSION timestamp="
                                + java.time.Instant.now()
                                        .minus(Duration.ofDays(8))
                                        .getEpochSecond());
                try {
                    return invocation.proceed();
                } finally {
                    clock.execute("SET SESSION timestamp=0");
                }
            }
        }
    }
}
