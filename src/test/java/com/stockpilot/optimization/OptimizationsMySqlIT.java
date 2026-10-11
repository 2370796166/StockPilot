package com.stockpilot.optimization;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.StockPilotApplication;
import com.stockpilot.acceptance.IntegrationTestInfrastructure;
import com.stockpilot.ai.service.AiToolService;
import com.stockpilot.inventory.count.request.InventoryCountRequests;
import com.stockpilot.inventory.count.service.InventoryCountApplicationService;
import com.stockpilot.messaging.domain.BusinessEventNames;
import com.stockpilot.messaging.domain.CompletionBusinessEvent;
import com.stockpilot.messaging.mapper.FailureRecordMapper;
import com.stockpilot.purchase.request.PurchaseReceiptRequests;
import com.stockpilot.purchase.service.PurchaseReceiptApplicationService;
import com.stockpilot.sales.request.SalesOutboundRequests;
import com.stockpilot.sales.service.SalesOutboundApplicationService;
import com.stockpilot.security.auth.DatabaseUserDetailsService;
import com.stockpilot.shared.auth.AuthenticatedActor;
import com.stockpilot.shared.auth.WarehouseAccess;
import com.stockpilot.transfer.request.StockTransferRequests;
import com.stockpilot.transfer.service.StockTransferApplicationService;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.support.TestPropertySourceUtils;

@SpringBootTest(
        classes = OptimizationsMySqlIT.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = OptimizationsMySqlIT.DatabaseInitializer.class)
class OptimizationsMySqlIT {
    private static final String DATABASE =
            IntegrationTestInfrastructure.databaseName("stockpilot_optimization_it");
    private static final String ADMIN_URL =
            System.getenv()
                    .getOrDefault(
                            "STOCKPILOT_IT_ADMIN_URL",
                            "jdbc:mysql://localhost:3307/?allowPublicKeyRetrieval=true&useSSL=false");
    private static final String ADMIN_USER =
            System.getenv().getOrDefault("STOCKPILOT_IT_ADMIN_USER", "root");
    private static final String ADMIN_PASSWORD =
            System.getenv().getOrDefault("STOCKPILOT_IT_ADMIN_PASSWORD", "root_dev_only");
    private static final AuthenticatedActor MAKER = new AuthenticatedActor(7001L, "test-maker"),
            REVIEWER = new AuthenticatedActor(7002L, "test-reviewer");
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired PurchaseReceiptApplicationService purchases;
    @Autowired SalesOutboundApplicationService sales;
    @Autowired StockTransferApplicationService transfers;
    @Autowired InventoryCountApplicationService counts;
    @Autowired FailureRecordMapper failures;
    @Autowired com.stockpilot.messaging.mapper.OutboxMessageMapper outbox;
    @Autowired DatabaseUserDetailsService users;
    @Autowired AiToolService tools;
    @Autowired org.springframework.transaction.support.TransactionTemplate transactions;
    @Autowired com.stockpilot.alert.service.LowStockEventApplicationService lowStockEvents;
    private String admin;

    @Test
    void expiredClaimReplacementRejectsBothLateSuccessAndLateFailure() {
        String id = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO async_outbox_message(message_id,event_name,event_version,business_no,routing_key,payload_json,status,publish_attempts,next_attempt_at) VALUES(?, ?,1,?,'test',CAST('{}' AS JSON),'PENDING',0,CURRENT_TIMESTAMP(3))",
                id,
                BusinessEventNames.SALES_OUTBOUND_COMPLETED,
                no("LEASE"));
        String first = UUID.randomUUID().toString(), second = UUID.randomUUID().toString();
        transactions.executeWithoutResult(
                status -> {
                    assertEquals(1, outbox.markAttempt(id, first, 60_000_000L));
                    assertEquals(0, outbox.markAttempt(id, second, 60_000_000L));
                });
        jdbc.update(
                "UPDATE async_outbox_message SET claim_until=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP(3)) WHERE message_id=?",
                id);
        transactions.executeWithoutResult(
                status -> {
                    assertEquals(1, outbox.markAttempt(id, second, 60_000_000L));
                    assertEquals(0, outbox.markPublished(id, first));
                    assertEquals(
                            0,
                            outbox.markFailure(
                                    id,
                                    "FAILED",
                                    java.time.LocalDateTime.now(),
                                    "late failure",
                                    first));
                });
        var row =
                jdbc.queryForMap(
                        "SELECT status,claim_token,publish_attempts,last_error FROM async_outbox_message WHERE message_id=?",
                        id);
        assertEquals("PENDING", row.get("status"));
        assertEquals(second, row.get("claim_token"));
        assertEquals(2, ((Number) row.get("publish_attempts")).intValue());
        assertNull(row.get("last_error"));
        transactions.executeWithoutResult(
                status -> assertEquals(1, outbox.markPublished(id, second)));
        assertEquals(
                "PUBLISHED",
                jdbc.queryForObject(
                        "SELECT status FROM async_outbox_message WHERE message_id=?",
                        String.class,
                        id));
    }

    @Test
    void evaluationUsesCurrentBalanceAfterAnEarlierRepeatableReadSnapshot() {
        var d = dimension();
        receive(d, "10");
        call(
                HttpMethod.POST,
                "/api/safety-stock/rules",
                Map.of(
                        "warehouseId",
                        d.w,
                        "locationId",
                        d.l,
                        "skuId",
                        d.s,
                        "thresholdQuantity",
                        "5"),
                admin,
                200);
        var executor = Executors.newSingleThreadExecutor();
        try {
            transactions.executeWithoutResult(
                    status -> {
                        assertEquals(
                                0,
                                jdbc.queryForObject(
                                                "SELECT available_quantity FROM inventory_balance WHERE warehouse_id=?",
                                                BigDecimal.class,
                                                d.w)
                                        .compareTo(new BigDecimal("10")));
                        try {
                            executor.submit(
                                            () -> {
                                                var sale =
                                                        sales.create(
                                                                new SalesOutboundRequests.Create(
                                                                        no("NEW"),
                                                                        d.w,
                                                                        null,
                                                                        List.of(
                                                                                new SalesOutboundRequests
                                                                                        .Line(
                                                                                        d.l,
                                                                                        d.s,
                                                                                        new BigDecimal(
                                                                                                "9")))),
                                                                MAKER);
                                                sales.reserve(
                                                        sale.id(),
                                                        new SalesOutboundRequests.Transition(
                                                                sale.version()),
                                                        MAKER);
                                            })
                                    .get(20, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                        lowStockEvents.evaluate(
                                new com.stockpilot.alert.service.LowStockEvaluationCommand(
                                        UUID.randomUUID().toString(),
                                        BusinessEventNames.SALES_OUTBOUND_COMPLETED,
                                        no("EVENT"),
                                        d.w,
                                        List.of(
                                                new com.stockpilot.alert.service
                                                        .LowStockEvaluationCommand
                                                        .InventoryDimension(d.l, d.s))));
                    });
        } finally {
            executor.shutdownNow();
        }
        assertEquals(
                new BigDecimal("1.0000"),
                jdbc.queryForObject(
                        "SELECT available_quantity FROM low_stock_alert WHERE warehouse_id=?",
                        BigDecimal.class,
                        d.w));
        assertEquals(
                "OPEN",
                jdbc.queryForObject(
                        "SELECT status FROM low_stock_alert WHERE warehouse_id=?",
                        String.class,
                        d.w));
    }

    @BeforeEach
    void authenticate() {
        WarehouseAccess.clear();
        SecurityContextHolder.clearContext();
        admin = login("optimization-admin", "Optimization_test_20261011");
        assertEquals(DATABASE, jdbc.queryForObject("SELECT DATABASE()", String.class));
    }

    @AfterAll
    static void dropDedicatedSchema() throws Exception {
        try (Connection c = DriverManager.getConnection(ADMIN_URL, ADMIN_USER, ADMIN_PASSWORD);
                Statement s = c.createStatement()) {
            s.execute("DROP DATABASE " + DATABASE);
        }
    }

    @Test
    void rulesReevaluateOnCreateUpdateDisableAndRejectStaleWrites() {
        var d = dimension();
        var rule =
                call(
                        HttpMethod.POST,
                        "/api/safety-stock/rules",
                        Map.of(
                                "warehouseId",
                                d.w,
                                "locationId",
                                d.l,
                                "skuId",
                                d.s,
                                "thresholdQuantity",
                                "5.0000"),
                        admin,
                        200);
        long id = rule.path("id").asLong();
        assertTrue(id > 0);
        var alerts =
                call(
                        HttpMethod.GET,
                        "/api/safety-stock/alerts?warehouseId=" + d.w + "&status=OPEN",
                        null,
                        admin,
                        200);
        assertEquals(1, alerts.path("total").asInt());
        assertEquals("0.0000", alerts.path("records").get(0).path("availableQuantity").asText());
        assertEquals("RULE-" + id, alerts.path("records").get(0).path("businessNo").asText());
        var updated =
                call(
                        HttpMethod.PUT,
                        "/api/safety-stock/rules/" + id,
                        Map.of("version", 0, "thresholdQuantity", "0.0000", "status", "ENABLED"),
                        admin,
                        200);
        assertEquals(1, updated.path("version").asInt());
        call(
                HttpMethod.PUT,
                "/api/safety-stock/rules/" + id,
                Map.of("version", 0, "thresholdQuantity", "1", "status", "ENABLED"),
                admin,
                409);
        call(
                HttpMethod.PUT,
                "/api/safety-stock/rules/" + id,
                Map.of("version", 1, "thresholdQuantity", "5", "status", "DISABLED"),
                admin,
                200);
        assertEquals(
                0,
                call(
                                HttpMethod.GET,
                                "/api/safety-stock/alerts?warehouseId=" + d.w + "&status=OPEN",
                                null,
                                admin,
                                200)
                        .path("total")
                        .asInt());
        call(
                HttpMethod.POST,
                "/api/safety-stock/rules",
                Map.of(
                        "warehouseId",
                        d.w,
                        "locationId",
                        d.l,
                        "skuId",
                        d.s,
                        "thresholdQuantity",
                        "7"),
                admin,
                409);
        assertEquals(
                0, quantity("SELECT COUNT(*) FROM inventory_balance WHERE warehouse_id=" + d.w));
    }

    @Test
    void consumerCompensationKeepsIdentityAndInventoryAndRejectsMalformedEvents() throws Exception {
        var d = dimension();
        receive(d, "10");
        var event =
                new CompletionBusinessEvent(
                        UUID.randomUUID().toString(),
                        BusinessEventNames.PURCHASE_RECEIPT_COMPLETED,
                        1,
                        no("PR"),
                        Instant.now(),
                        new CompletionBusinessEvent.CompletionData(
                                1L,
                                d.w,
                                List.of(new CompletionBusinessEvent.InventoryDimension(d.l, d.s))));
        failures.upsert(
                event.messageId(),
                event.eventName(),
                1,
                event.businessNo(),
                "CONSUME",
                "low-stock-alert-v1",
                json.writeValueAsString(event),
                "simulated consumer failure");
        long id =
                jdbc.queryForObject(
                        "SELECT id FROM async_failure_record WHERE message_id=?",
                        Long.class,
                        event.messageId());
        long ledgers = quantity("SELECT COUNT(*) FROM inventory_ledger");
        var result =
                call(
                        HttpMethod.POST,
                        "/api/messages/failures/" + id + "/retry",
                        Map.of("occurrenceCount", 1, "note", "故障已修复"),
                        admin,
                        200);
        assertEquals("RESOLVED", result.path("failure").path("status").asText());
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM async_consumed_message WHERE message_id=?",
                        Integer.class,
                        event.messageId()));
        assertEquals(ledgers, quantity("SELECT COUNT(*) FROM inventory_ledger"));
        assertBalance(d, "10", "10", "0");
        call(
                HttpMethod.POST,
                "/api/messages/failures/" + id + "/retry",
                Map.of("occurrenceCount", 1, "note", "重复请求"),
                admin,
                409);
        String bad = UUID.randomUUID().toString();
        failures.upsert(
                bad, "unknown", 1, "INVALID", "CONSUME", "low-stock-alert-v1", "{}", "bad input");
        long badId =
                jdbc.queryForObject(
                        "SELECT id FROM async_failure_record WHERE message_id=?", Long.class, bad);
        call(
                HttpMethod.POST,
                "/api/messages/failures/" + badId + "/retry",
                Map.of("occurrenceCount", 1, "note", "不能重试"),
                admin,
                409);
        assertEquals(
                "PENDING",
                jdbc.queryForObject(
                        "SELECT status FROM async_failure_record WHERE id=?", String.class, badId));
        call(
                HttpMethod.POST,
                "/api/messages/failures/" + badId + "/resolve",
                Map.of("occurrenceCount", 1, "note", "已核查为无效事件，保留原证据"),
                admin,
                200);
    }

    @Test
    void reconciliationFindsMissingLedgerAndChecksEveryLocation() {
        var d = dimension();
        receive(d, "10");
        var sale =
                sales.create(
                        new SalesOutboundRequests.Create(
                                no("SO"),
                                d.w,
                                null,
                                List.of(
                                        new SalesOutboundRequests.Line(
                                                d.l, d.s, new BigDecimal("2")))),
                        MAKER);
        sales.reserve(sale.id(), new SalesOutboundRequests.Transition(sale.version()), MAKER);
        String uri =
                "/api/inventory/reconciliation?warehouseId="
                        + d.w
                        + "&locationId="
                        + d.l
                        + "&skuId="
                        + d.s;
        var good = call(HttpMethod.GET, uri, null, admin, 200);
        assertEquals(0, good.path("issues").size(), good.toPrettyString());
        assertEquals("2.0000", good.path("frozenSourceQuantity").asText());
        jdbc.update(
                "DELETE FROM inventory_ledger WHERE warehouse_id=? AND business_type='PURCHASE_RECEIPT'",
                d.w);
        var broken = call(HttpMethod.GET, uri, null, admin, 200);
        assertTrue(broken.path("issues").toString().contains("LEDGER_TOTAL"));
        assertTrue(broken.path("issues").toString().contains("LEDGER_CHAIN"));
        assertBalance(d, "10", "8", "2");
    }

    @Test
    void reconciliationVerifiesTransitAgainstBothWarehouseLedgers() {
        var source = dimension();
        var target = dimensionWithSku(source.s);
        receive(source, "9");
        var t =
                transfers.create(
                        new StockTransferRequests.Create(
                                no("TR"),
                                source.w,
                                target.w,
                                null,
                                List.of(
                                        new StockTransferRequests.Line(
                                                source.l,
                                                target.l,
                                                source.s,
                                                new BigDecimal("3")))),
                        MAKER);
        t = transfers.submit(t.id(), new StockTransferRequests.Transition(t.version()), MAKER);
        t = transfers.approve(t.id(), new StockTransferRequests.Transition(t.version()), REVIEWER);
        transfers.dispatch(t.id(), MAKER);
        String uri =
                "/api/inventory/reconciliation?warehouseId="
                        + source.w
                        + "&locationId="
                        + source.l
                        + "&skuId="
                        + source.s;
        var good = call(HttpMethod.GET, uri, null, admin, 200);
        assertEquals(0, good.path("issues").size(), good.toPrettyString());
        assertEquals(1, good.path("checkedTransfers").asInt());
        jdbc.update("DELETE FROM stock_transfer_transit WHERE transfer_id=?", t.id());
        assertTrue(
                call(HttpMethod.GET, uri, null, admin, 200)
                        .path("issues")
                        .toString()
                        .contains("TRANSFER_FACT"));
    }

    @Test
    void warehouseScopeFiltersPaginationDetailsWritesAndAiAndChangesExistingTokens() {
        var allowed = dimension();
        var hidden = dimension();
        receive(allowed, "4");
        receive(hidden, "5");
        String name = no("scope").toLowerCase();
        var u =
                call(
                        HttpMethod.POST,
                        "/api/security/users",
                        Map.of(
                                "username",
                                name,
                                "displayName",
                                "范围测试用户",
                                "password",
                                "Scope_test_20261011"),
                        admin,
                        200);
        long id = u.path("id").asLong();
        long role =
                jdbc.queryForObject("SELECT id FROM sys_role WHERE code='OPERATOR'", Long.class);
        call(
                HttpMethod.PUT,
                "/api/security/users/" + id + "/roles",
                Map.of("ids", List.of(role)),
                admin,
                200);
        var scope =
                call(
                        HttpMethod.PUT,
                        "/api/security/users/" + id + "/warehouses",
                        Map.of(
                                "allWarehouses",
                                false,
                                "warehouseIds",
                                List.of(allowed.w),
                                "version",
                                0),
                        admin,
                        200);
        assertEquals(1, scope.path("version").asInt());
        String token = login(name, "Scope_test_20261011");
        var balances =
                call(HttpMethod.GET, "/api/inventory/balances?page=1&size=1", null, token, 200);
        assertEquals(1, balances.path("total").asInt());
        assertEquals(allowed.w, balances.path("records").get(0).path("warehouseId").asLong());
        assertEquals(
                1,
                call(HttpMethod.GET, "/api/master-data/warehouses", null, token, 200)
                        .path("total")
                        .asInt());
        call(HttpMethod.GET, "/api/master-data/warehouses/" + hidden.w, null, token, 403);
        call(
                HttpMethod.POST,
                "/api/inbound/purchase-receipts",
                Map.of(
                        "receiptNo",
                        no("BAD"),
                        "warehouseId",
                        hidden.w,
                        "lines",
                        List.of(
                                Map.of(
                                        "locationId",
                                        hidden.l,
                                        "skuId",
                                        hidden.s,
                                        "quantity",
                                        "1"))),
                token,
                403);
        long hiddenReceipt =
                jdbc.queryForObject(
                        "SELECT id FROM purchase_receipt WHERE warehouse_id=? LIMIT 1",
                        Long.class,
                        hidden.w);
        call(HttpMethod.GET, "/api/inbound/purchase-receipts/" + hiddenReceipt, null, token, 404);
        var auth = users.load(id, name);
        SecurityContextHolder.getContext().setAuthentication(auth);
        WarehouseAccess.bind((com.stockpilot.shared.auth.WarehouseScope) auth.getDetails());
        try {
            var evidence = tools.execute("list_inventory", json.createObjectNode(), List.of());
            assertEquals("OK", evidence.status());
            assertFalse(evidence.data().toString().contains("\"warehouseId\":" + hidden.w));
            assertTrue(evidence.data().toString().contains("\"warehouseId\":" + allowed.w));
        } finally {
            WarehouseAccess.clear();
            SecurityContextHolder.clearContext();
        }
        call(
                HttpMethod.PUT,
                "/api/security/users/" + id + "/warehouses",
                Map.of("allWarehouses", false, "warehouseIds", List.of(), "version", 1),
                admin,
                200);
        assertEquals(
                0,
                call(HttpMethod.GET, "/api/inventory/balances", null, token, 200)
                        .path("total")
                        .asInt());
        call(
                HttpMethod.PUT,
                "/api/security/users/" + id + "/warehouses",
                Map.of("allWarehouses", true, "warehouseIds", List.of(), "version", 1),
                admin,
                409);
        call(HttpMethod.GET, "/api/messages/failures", null, token, 403);
    }

    @Test
    void purchaseSupplierReturnsAndCancellationPreserveOriginalEvidence() throws Exception {
        var d = dimension();
        String code = no("SUP");
        jdbc.update(
                "INSERT INTO supplier(code,name,status,version) VALUES(?,'测试供应商','ENABLED',0)",
                code);
        long supplier =
                jdbc.queryForObject("SELECT id FROM supplier WHERE code=?", Long.class, code);
        var p =
                purchases.create(
                        new PurchaseReceiptRequests.Create(
                                no("PR"),
                                d.w,
                                null,
                                List.of(
                                        new PurchaseReceiptRequests.Line(
                                                d.l, d.s, new BigDecimal("3"))),
                                supplier),
                        MAKER);
        assertEquals(supplier, p.supplierId());
        p = purchases.submit(p.id(), new PurchaseReceiptRequests.Transition(p.version()), MAKER);
        p =
                purchases.reject(
                        p.id(),
                        new PurchaseReceiptRequests.Reason(p.version(), "数量需要核实"),
                        REVIEWER);
        assertEquals("DRAFT", p.status().name());
        assertNull(p.submittedAt());
        assertEquals(1, p.actions().size());
        assertTrue(p.actions().get(0).evidenceJson().contains("3.0000"));
        var original = json.readTree(p.actions().get(0).evidenceJson());
        assertEquals(d.w, original.path("warehouseId").asLong());
        assertEquals(supplier, original.path("supplierId").asLong());
        assertEquals("测试供应商", original.path("supplierName").asText());
        assertEquals("SUBMITTED", original.path("status").asText());
        assertEquals(d.w, original.path("lines").get(0).path("warehouseId").asLong());
        assertEquals(1, original.path("lines").get(0).path("lineNo").asInt());
        var replacement = dimension();
        p =
                purchases.update(
                        p.id(),
                        new PurchaseReceiptRequests.Update(
                                p.version(),
                                replacement.w,
                                "退回后修改",
                                List.of(
                                        new PurchaseReceiptRequests.Line(
                                                replacement.l, replacement.s, new BigDecimal("7"))),
                                null),
                        MAKER);
        assertEquals(replacement.w, p.warehouseId());
        assertNull(p.supplierId());
        assertEquals(original, json.readTree(p.actions().get(0).evidenceJson()));
        int old = p.version();
        p =
                purchases.cancel(
                        p.id(), new PurchaseReceiptRequests.Reason(p.version(), "采购计划取消"), MAKER);
        assertEquals("CANCELLED", p.status().name());
        assertEquals(2, p.actions().size());
        assertEquals(original, json.readTree(p.actions().get(1).evidenceJson()));
        assertEquals(
                replacement.w,
                json.readTree(p.actions().get(0).evidenceJson()).path("warehouseId").asLong());
        long id = p.id();
        assertThrows(
                com.stockpilot.shared.exception.BusinessException.class,
                () -> purchases.cancel(id, new PurchaseReceiptRequests.Reason(old, "重复"), MAKER));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM inventory_balance WHERE warehouse_id=?",
                        Integer.class,
                        d.w));
    }

    @Test
    void countReturnPreservesLockAndOldResultsBeforeRecordingNewResults() {
        var d = dimension();
        receive(d, "10");
        var c =
                counts.create(
                        new InventoryCountRequests.Create(
                                no("CT"),
                                d.w,
                                null,
                                List.of(new InventoryCountRequests.Dimension(d.l, d.s))),
                        MAKER);
        c = counts.start(c.id(), new InventoryCountRequests.Transition(c.version()), MAKER);
        c =
                counts.recordResults(
                        c.id(),
                        new InventoryCountRequests.RecordResults(
                                c.version(),
                                List.of(
                                        new InventoryCountRequests.Result(
                                                c.lines().get(0).id(),
                                                new BigDecimal("9"),
                                                "初次实盘"))),
                        MAKER);
        c = counts.submit(c.id(), new InventoryCountRequests.Transition(c.version()), MAKER);
        c =
                counts.reject(
                        c.id(), new InventoryCountRequests.Cancel(c.version(), "请重新复核"), REVIEWER);
        assertEquals("COUNTING", c.status().name());
        assertEquals(1, c.actions().size());
        assertTrue(c.actions().get(0).evidenceJson().contains("9.0000"));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM inventory_count_scope_lock WHERE count_id=?",
                        Integer.class,
                        c.id()));
        c =
                counts.recordResults(
                        c.id(),
                        new InventoryCountRequests.RecordResults(
                                c.version(),
                                List.of(
                                        new InventoryCountRequests.Result(
                                                c.lines().get(0).id(),
                                                new BigDecimal("8"),
                                                "二次复核"))),
                        MAKER);
        assertTrue(c.actions().get(0).evidenceJson().contains("9.0000"));
        assertEquals(0, c.lines().get(0).countedQuantity().compareTo(new BigDecimal("8")));
        assertBalance(d, "10", "10", "0");
        counts.cancel(c.id(), new InventoryCountRequests.Cancel(c.version(), "现场中止"), MAKER);
    }

    @Test
    void cancelAndCompleteRaceHasExactlyOneInventoryOutcome() throws Exception {
        var d = dimension();
        var p =
                purchases.create(
                        new PurchaseReceiptRequests.Create(
                                no("RACE"),
                                d.w,
                                null,
                                List.of(
                                        new PurchaseReceiptRequests.Line(
                                                d.l, d.s, new BigDecimal("6")))),
                        MAKER);
        p = purchases.submit(p.id(), new PurchaseReceiptRequests.Transition(p.version()), MAKER);
        p =
                purchases.approve(
                        p.id(), new PurchaseReceiptRequests.Transition(p.version()), REVIEWER);
        final long id = p.id();
        final int version = p.version();
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var completed =
                    executor.submit(
                            () -> {
                                start.await();
                                try {
                                    purchases.complete(id, MAKER);
                                    return true;
                                } catch (com.stockpilot.shared.exception.BusinessException e) {
                                    return false;
                                }
                            });
            var cancelled =
                    executor.submit(
                            () -> {
                                start.await();
                                try {
                                    purchases.cancel(
                                            id,
                                            new PurchaseReceiptRequests.Reason(version, "并发取消"),
                                            MAKER);
                                    return true;
                                } catch (com.stockpilot.shared.exception.BusinessException e) {
                                    return false;
                                }
                            });
            start.countDown();
            assertNotEquals(
                    completed.get(20, TimeUnit.SECONDS), cancelled.get(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
        var result = purchases.get(id);
        assertEquals(
                result.status().name().equals("COMPLETED") ? 1 : 0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM inventory_ledger WHERE business_no=? AND business_type='PURCHASE_RECEIPT'",
                        Integer.class,
                        result.receiptNo()));
    }

    @Test
    void approvalSeparationAndMonitoringAuthorizationAreEnforced() {
        var d = dimension();
        var p =
                call(
                        HttpMethod.POST,
                        "/api/inbound/purchase-receipts",
                        Map.of(
                                "receiptNo",
                                no("OWN"),
                                "warehouseId",
                                d.w,
                                "lines",
                                List.of(Map.of("locationId", d.l, "skuId", d.s, "quantity", "1"))),
                        admin,
                        200);
        long id = p.path("id").asLong();
        call(
                HttpMethod.POST,
                "/api/inbound/purchase-receipts/" + id + "/submit",
                Map.of("version", 0),
                admin,
                200);
        call(
                HttpMethod.POST,
                "/api/inbound/purchase-receipts/" + id + "/approve",
                Map.of("version", 1),
                admin,
                409);
        var response =
                http.exchange(
                        "/api/operations/status",
                        HttpMethod.GET,
                        new HttpEntity<>(headers(admin)),
                        JsonNode.class);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getHeaders().getFirst("X-Request-ID"));
        assertTrue(response.getBody().path("data").path("observedRequests").asLong() >= 0);
        assertEquals(false, response.getBody().path("data").path("messagingEnabled").asBoolean());
        call(HttpMethod.GET, "/api/operations/status", null, null, 401);
        assertEquals(
                200,
                http.exchange(
                                "/api/management/metrics",
                                HttpMethod.GET,
                                new HttpEntity<>(headers(admin)),
                                JsonNode.class)
                        .getStatusCode()
                        .value());
    }

    private Dimension dimension() {
        String code = no("SKU");
        jdbc.update(
                "INSERT INTO sku(code,name,unit,status,version) VALUES(?,'优化测试商品','PCS','ENABLED',0)",
                code);
        return dimensionWithSku(
                jdbc.queryForObject("SELECT id FROM sku WHERE code=?", Long.class, code));
    }

    private Dimension dimensionWithSku(long sku) {
        String code = no("WH");
        jdbc.update(
                "INSERT INTO warehouse(code,name,status,version) VALUES(?,'优化测试仓库','ENABLED',0)",
                code);
        long w = jdbc.queryForObject("SELECT id FROM warehouse WHERE code=?", Long.class, code);
        String loc = no("LOC");
        jdbc.update(
                "INSERT INTO warehouse_location(warehouse_id,code,name,status,version) VALUES(?,?,'测试库位','ENABLED',0)",
                w,
                loc);
        return new Dimension(
                w,
                jdbc.queryForObject(
                        "SELECT id FROM warehouse_location WHERE code=?", Long.class, loc),
                sku);
    }

    private void receive(Dimension d, String quantity) {
        var p =
                purchases.create(
                        new PurchaseReceiptRequests.Create(
                                no("PR"),
                                d.w,
                                null,
                                List.of(
                                        new PurchaseReceiptRequests.Line(
                                                d.l, d.s, new BigDecimal(quantity)))),
                        MAKER);
        p = purchases.submit(p.id(), new PurchaseReceiptRequests.Transition(p.version()), MAKER);
        p =
                purchases.approve(
                        p.id(), new PurchaseReceiptRequests.Transition(p.version()), REVIEWER);
        purchases.complete(p.id(), MAKER);
    }

    private void assertBalance(Dimension d, String actual, String available, String frozen) {
        var row =
                jdbc.queryForMap(
                        "SELECT actual_quantity,available_quantity,frozen_quantity FROM inventory_balance WHERE warehouse_id=? AND location_id=? AND sku_id=?",
                        d.w,
                        d.l,
                        d.s);
        assertEquals(0, new BigDecimal(actual).compareTo((BigDecimal) row.get("actual_quantity")));
        assertEquals(
                0, new BigDecimal(available).compareTo((BigDecimal) row.get("available_quantity")));
        assertEquals(0, new BigDecimal(frozen).compareTo((BigDecimal) row.get("frozen_quantity")));
    }

    private String login(String name, String password) {
        return call(
                        HttpMethod.POST,
                        "/api/auth/login",
                        Map.of("username", name, "password", password),
                        null,
                        200)
                .path("accessToken")
                .asText();
    }

    private JsonNode call(HttpMethod method, String path, Object body, String token, int expected) {
        var response =
                http.exchange(path, method, new HttpEntity<>(body, headers(token)), JsonNode.class);
        assertEquals(
                expected, response.getStatusCode().value(), () -> path + " " + response.getBody());
        assertNotNull(response.getBody());
        if (expected == 200) assertEquals("SUCCESS", response.getBody().path("code").asText());
        return response.getBody().path("data");
    }

    private HttpHeaders headers(String token) {
        var h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) h.setBearerAuth(token);
        return h;
    }

    private long quantity(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private static String no(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private record Dimension(long w, long l, long s) {}

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    @org.springframework.context.annotation.ComponentScan(
            basePackages = "com.stockpilot",
            excludeFilters = {
                @org.springframework.context.annotation.ComponentScan.Filter(
                        type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
                        classes = StockPilotApplication.class),
                @org.springframework.context.annotation.ComponentScan.Filter(
                        type = org.springframework.context.annotation.FilterType.REGEX,
                        pattern = "com\\.stockpilot\\.security\\.TestProtectedController")
            })
    static class TestApplication {}

    static class DatabaseInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        public void initialize(ConfigurableApplicationContext context) {
            IntegrationTestInfrastructure.isolate(context, DATABASE);
            try (Connection c = DriverManager.getConnection(ADMIN_URL, ADMIN_USER, ADMIN_PASSWORD);
                    Statement s = c.createStatement()) {
                s.execute(
                        "CREATE DATABASE "
                                + DATABASE
                                + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Cannot create dedicated optimization test schema", e);
            }
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                    context,
                    "spring.datasource.url="
                            + IntegrationTestInfrastructure.databaseUrl(ADMIN_URL, DATABASE),
                    "spring.datasource.username=" + ADMIN_USER,
                    "spring.datasource.password=" + ADMIN_PASSWORD,
                    "stockpilot.security.jwt-secret=optimization-test-key-20261011-at-least-32-bytes",
                    "stockpilot.security.bootstrap-admin-username=optimization-admin",
                    "stockpilot.security.bootstrap-admin-password=Optimization_test_20261011",
                    "stockpilot.ai.enabled=false",
                    "stockpilot.business.separate-approval=true",
                    "management.health.redis.enabled=false",
                    "management.health.rabbit.enabled=false");
        }
    }
}
