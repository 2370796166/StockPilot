package com.stockpilot.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.StockPilotApplication;
import com.stockpilot.acceptance.IntegrationTestInfrastructure;
import com.stockpilot.security.config.SecurityProperties;
import com.stockpilot.security.service.AuthenticationApplicationService;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.support.TestPropertySourceUtils;

@SpringBootTest(
        classes = CoreBusinessE2EMySqlIT.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = CoreBusinessE2EMySqlIT.MySqlInitializer.class)
class CoreBusinessE2EMySqlIT {
    private static final String DATABASE =
            IntegrationTestInfrastructure.databaseName("stockpilot_core_e2e_it");
    private static final String ADMIN_URL =
            System.getenv()
                    .getOrDefault(
                            "STOCKPILOT_IT_ADMIN_URL",
                            "jdbc:mysql://localhost:3307/?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false");
    private static final String ADMIN_USER =
            System.getenv().getOrDefault("STOCKPILOT_IT_ADMIN_USER", "root");
    private static final String ADMIN_PASSWORD =
            System.getenv().getOrDefault("STOCKPILOT_IT_ADMIN_PASSWORD", "root_dev_only");
    private static final String BOOTSTRAP_ADMIN = "e2e_admin";
    private static final String BOOTSTRAP_PASSWORD = "E2eAdmin!2026";
    private static final String NO_PERMISSION_USER = "e2e_no_permission";
    private static final String NO_PERMISSION_PASSWORD = "E2eNoPermission!2026";
    private static final BigDecimal RECEIPT_QUANTITY = new BigDecimal("10.0000");
    private static final BigDecimal OUTBOUND_QUANTITY = new BigDecimal("4.0000");
    // This provider is a deterministic local protocol fixture, never a real model.
    private static HttpServer modelServer;
    private static volatile String modelTool;
    private static volatile String modelArguments;
    private static volatile JsonNode modelRequest;
    private static final java.util.concurrent.atomic.AtomicInteger modelRequests =
            new java.util.concurrent.atomic.AtomicInteger();

    @Autowired private TestRestTemplate http;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AuthenticationApplicationService authenticationService;
    @SpyBean private SecurityProperties securityProperties;

    @BeforeEach
    void isolateBusinessFactsBetweenHttpScenarios() {
        for (String table :
                List.of(
                        "async_failure_record",
                        "async_message_trace",
                        "async_consumed_message",
                        "low_stock_alert",
                        "safety_stock_rule",
                        "async_outbox_message",
                        "inventory_count_scope_lock",
                        "inventory_count_line",
                        "inventory_count_order",
                        "stock_transfer_transit",
                        "stock_transfer_line",
                        "stock_transfer_order",
                        "sales_outbound_line",
                        "sales_outbound_order",
                        "purchase_receipt_line",
                        "purchase_receipt",
                        "inventory_ledger",
                        "inventory_balance",
                        "warehouse_location",
                        "sku",
                        "supplier",
                        "product_category",
                        "warehouse")) jdbc.update("DELETE FROM " + table);
    }

    @AfterAll
    static void dropDedicatedDatabase() throws Exception {
        if (modelServer != null) modelServer.stop(0);
        try (Connection connection =
                        DriverManager.getConnection(ADMIN_URL, ADMIN_USER, ADMIN_PASSWORD);
                Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + DATABASE);
        }
    }

    @Test
    void executesCorePurchaseAndSalesFlowThroughRealHttpApi() {
        try {
            executeCoreFlow();
        } catch (Throwable failure) {
            throw new AssertionError(
                    "Core E2E failure. Dedicated database snapshot=" + diagnosticSnapshot(),
                    failure);
        }
    }

    @Test
    void agentBusinessListsAndNaturalSupplementUseRealSqlAndHttp() throws Exception {
        String token = login(BOOTSTRAP_ADMIN, BOOTSTRAP_PASSWORD);
        long warehouse =
                requiredId(
                        success(
                                post(
                                        "/api/master-data/warehouses",
                                        masterData("LIST_WH", "List Warehouse"),
                                        token)));
        long location =
                requiredId(
                        success(
                                post(
                                        "/api/master-data/locations",
                                        Map.of(
                                                "warehouseId",
                                                warehouse,
                                                "code",
                                                "LIST_LOC",
                                                "name",
                                                "List Location"),
                                        token)));
        long sku =
                requiredId(
                        success(
                                post(
                                        "/api/master-data/skus",
                                        Map.of(
                                                "code",
                                                "LIST_SKU",
                                                "name",
                                                "List SKU",
                                                "unit",
                                                "PCS"),
                                        token)));
        var receipt =
                success(
                        post(
                                "/api/inbound/purchase-receipts",
                                document(
                                        "receiptNo",
                                        "LIST_PR",
                                        warehouse,
                                        location,
                                        sku,
                                        new BigDecimal("10")),
                                token));
        String receiptPath = "/api/inbound/purchase-receipts/" + requiredId(receipt);
        var submitted = success(post(receiptPath + "/submit", transition(receipt), token));
        success(post(receiptPath + "/approve", transition(submitted), token));
        success(postWithoutBody(receiptPath + "/complete", token));
        success(
                post(
                        "/api/outbound/sales-orders",
                        document(
                                "outboundNo",
                                "LIST_SO",
                                warehouse,
                                location,
                                sku,
                                new BigDecimal("2")),
                        token));
        String session = success(postWithoutBody("/api/ai/sessions", token)).path("id").asText();
        var inventory =
                agentQuery(
                        session,
                        "查LIST_WH全部商品，可用库存低于20的记录",
                        "list_inventory",
                        Map.of("warehouse", "LIST_WH", "belowAvailable", "20"),
                        token);
        assertEquals("COMPLETED", inventory.path("status").asText());
        assertTrue(
                inventory
                                .path("results")
                                .get(0)
                                .path("data")
                                .path("inventory")
                                .path("total")
                                .asLong()
                        > 0);
        for (JsonNode row :
                inventory.path("results").get(0).path("data").path("inventory").path("records"))
            assertTrue(
                    new BigDecimal(row.path("availableQuantity").asText())
                                    .compareTo(new BigDecimal("20"))
                            < 0);
        String today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString();
        var purchases =
                agentQuery(
                        session,
                        "今天完成的采购单据",
                        "list_documents",
                        Map.of(
                                "documentType",
                                "PURCHASE",
                                "dateField",
                                "COMPLETED",
                                "startDate",
                                today,
                                "endDate",
                                today),
                        token);
        assertEquals("COMPLETED", purchases.path("status").asText());
        assertTrue(
                purchases
                                .path("results")
                                .get(0)
                                .path("data")
                                .path("documents")
                                .path("total")
                                .asLong()
                        > 0);
        for (JsonNode row :
                purchases.path("results").get(0).path("data").path("documents").path("records"))
            assertEquals("COMPLETED", row.path("status").asText());
        for (String type : List.of("SALES", "TRANSFER", "COUNT")) {
            var list =
                    agentQuery(
                            session,
                            "查询未完成业务单据",
                            "list_documents",
                            Map.of(
                                    "documentType",
                                    type,
                                    "status",
                                    "UNFINISHED",
                                    "startDate",
                                    today,
                                    "endDate",
                                    today),
                            token);
            assertEquals("COMPLETED", list.path("status").asText(), list::toString);
            for (JsonNode row :
                    list.path("results").get(0).path("data").path("documents").path("records"))
                assertFalse(
                        List.of("COMPLETED", "ADJUSTED", "CANCELLED")
                                .contains(row.path("status").asText()));
        }
        String another = success(postWithoutBody("/api/ai/sessions", token)).path("id").asText();
        var paused =
                agentQuery(
                        another,
                        "LIST_SKU的冻结来源",
                        "query_frozen_sources",
                        Map.of("sku", "LIST_SKU"),
                        token);
        assertEquals("NEEDS_CLARIFICATION", paused.path("status").asText());
        modelTool = "query_frozen_sources";
        modelArguments = write(Map.of("sku", "LIST_SKU", "warehouse", "LIST_WH"));
        var resumed =
                success(
                        post(
                                "/api/ai/tasks/" + paused.path("id").asText() + "/input",
                                Map.of(
                                        "version",
                                        paused.path("version").asLong(),
                                        "message",
                                        "就在LIST_WH"),
                                token));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (resumed.path("status").asText().equals("RUNNING") && System.nanoTime() < deadline) {
            Thread.sleep(20);
            resumed = success(get("/api/ai/tasks/" + paused.path("id").asText(), token));
        }
        assertEquals("COMPLETED", resumed.path("status").asText(), resumed::toString);
        assertEquals(paused.path("id").asText(), resumed.path("id").asText());
    }

    @Test
    void maximumDecimalSurvivesRealHttpCreateEditReceiptAndCountCancellation() {
        String token = login(BOOTSTRAP_ADMIN, BOOTSTRAP_PASSWORD);
        var apiSchema = request(HttpMethod.GET, "/v3/api-docs", null, token, HttpStatus.OK);
        assertEquals(
                "string",
                apiSchema
                        .path("components")
                        .path("schemas")
                        .path("InventoryBalanceVO")
                        .path("properties")
                        .path("actualQuantity")
                        .path("type")
                        .asText());
        long warehouse =
                requiredId(
                        success(
                                post(
                                        "/api/master-data/warehouses",
                                        masterData("DEC_WH", "Decimal Warehouse"),
                                        token)));
        long location =
                requiredId(
                        success(
                                post(
                                        "/api/master-data/locations",
                                        Map.of(
                                                "warehouseId",
                                                warehouse,
                                                "code",
                                                "DEC_LOC",
                                                "name",
                                                "Decimal Location"),
                                        token)));
        long sku =
                requiredId(
                        success(
                                post(
                                        "/api/master-data/skus",
                                        Map.of(
                                                "code",
                                                "DEC_SKU",
                                                "name",
                                                "Decimal SKU",
                                                "unit",
                                                "PCS"),
                                        token)));
        String maximum = "999999999999999.1234";
        var draft =
                success(
                        post(
                                "/api/inbound/purchase-receipts",
                                document(
                                        "receiptNo",
                                        "DEC-PR",
                                        warehouse,
                                        location,
                                        sku,
                                        new BigDecimal(maximum)),
                                token));
        assertTrue(draft.path("lines").get(0).path("quantity").isTextual());
        assertEquals(maximum, draft.path("lines").get(0).path("quantity").asText());
        String path = "/api/inbound/purchase-receipts/" + requiredId(draft);
        var edited =
                success(
                        request(
                                HttpMethod.PUT,
                                path,
                                Map.of(
                                        "version",
                                        draft.path("version").asInt(),
                                        "warehouseId",
                                        warehouse,
                                        "remark",
                                        "exact roundtrip",
                                        "lines",
                                        List.of(
                                                Map.of(
                                                        "locationId",
                                                        location,
                                                        "skuId",
                                                        sku,
                                                        "quantity",
                                                        maximum))),
                                token,
                                HttpStatus.OK));
        assertEquals(maximum, edited.path("lines").get(0).path("quantity").asText());
        var submitted = success(post(path + "/submit", transition(edited), token));
        success(post(path + "/approve", transition(submitted), token));
        success(post(path + "/complete", null, token));
        var balance = balance(warehouse, location, sku, token);
        assertTrue(balance.path("actualQuantity").isTextual());
        assertEquals(maximum, balance.path("actualQuantity").asText());
        assertEquals(maximum, balance.path("availableQuantity").asText());
        var count =
                success(
                        post(
                                "/api/inventory-counts",
                                Map.of(
                                        "countNo",
                                        "DEC-COUNT",
                                        "warehouseId",
                                        warehouse,
                                        "dimensions",
                                        List.of(Map.of("locationId", location, "skuId", sku))),
                                token));
        assertEquals(maximum, count.path("lines").get(0).path("snapshotActualQuantity").asText());
        var cancelled =
                success(
                        post(
                                "/api/inventory-counts/" + requiredId(count) + "/cancel",
                                Map.of("version", count.path("version").asInt(), "reason", "中止复核"),
                                token));
        assertEquals("CANCELLED", cancelled.path("status").asText());
        assertEquals(BOOTSTRAP_ADMIN, cancelled.path("cancelledByName").asText());
        assertEquals("中止复核", cancelled.path("cancelReason").asText());
        assertEquals(
                maximum, balance(warehouse, location, sku, token).path("actualQuantity").asText());
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM inventory_count_scope_lock WHERE count_id=?",
                        Integer.class,
                        requiredId(count)));
    }

    @Test
    void bootstrapRoleFailureRollsBackTheUserAndRestartCanSucceed() {
        doReturn(" bootstrap_rollback ").when(securityProperties).bootstrapAdminUsername();
        doReturn("Rollback!2026").when(securityProperties).bootstrapAdminPassword();
        jdbc.execute(
                "CREATE TRIGGER fail_bootstrap_role BEFORE INSERT ON sys_user_role FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced bootstrap role failure'");
        try {
            assertThrows(RuntimeException.class, () -> authenticationService.run(null));
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM sys_user WHERE username='bootstrap_rollback'",
                            Integer.class));
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS fail_bootstrap_role");
        }
        authenticationService.run(null);
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE u.username='bootstrap_rollback' AND r.code='ADMIN'",
                        Integer.class));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sys_user WHERE username='bootstrap_rollback'",
                        Integer.class));
    }

    @Test
    void concurrentBootstrapCreatesOneAdministratorAndOneRoleBinding() throws Exception {
        doReturn("bootstrap_race").when(securityProperties).bootstrapAdminUsername();
        doReturn("Race!2026").when(securityProperties).bootstrapAdminPassword();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Void> run =
                    () -> {
                        ready.countDown();
                        assertTrue(start.await(10, TimeUnit.SECONDS));
                        authenticationService.run(null);
                        return null;
                    };
            var first = pool.submit(run);
            var second = pool.submit(run);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sys_user WHERE username='bootstrap_race'",
                        Integer.class));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE u.username='bootstrap_race' AND r.code='ADMIN'",
                        Integer.class));
    }

    private void executeCoreFlow() {
        String adminToken = login(BOOTSTRAP_ADMIN, BOOTSTRAP_PASSWORD);
        JsonNode currentAdmin = success(get("/api/auth/me", adminToken));
        assertEquals(
                BOOTSTRAP_ADMIN, currentAdmin.path("username").asText(), currentAdmin::toString);
        assertTrue(containsText(currentAdmin.path("roles"), "ADMIN"), currentAdmin::toString);

        JsonNode noPermissionUser =
                success(
                        post(
                                "/api/security/users",
                                Map.of(
                                        "username",
                                        NO_PERMISSION_USER,
                                        "password",
                                        NO_PERMISSION_PASSWORD,
                                        "displayName",
                                        "E2E no permission user"),
                                adminToken));
        assertTrue(noPermissionUser.path("roleIds").isArray(), noPermissionUser::toString);
        assertEquals(0, noPermissionUser.path("roleIds").size(), noPermissionUser::toString);
        String noPermissionToken = login(NO_PERMISSION_USER, NO_PERMISSION_PASSWORD);

        JsonNode warehouse =
                success(
                        post(
                                "/api/master-data/warehouses",
                                masterData("E2E_WH", "E2E Warehouse"),
                                adminToken));
        long warehouseId = requiredId(warehouse);
        assertEquals("ENABLED", warehouse.path("status").asText(), warehouse::toString);

        JsonNode location =
                success(
                        post(
                                "/api/master-data/locations",
                                Map.of(
                                        "warehouseId",
                                        warehouseId,
                                        "code",
                                        "E2E_LOC",
                                        "name",
                                        "E2E Location",
                                        "remark",
                                        "core-e2e"),
                                adminToken));
        long locationId = requiredId(location);
        assertEquals(warehouseId, location.path("warehouseId").asLong(), location::toString);

        JsonNode sku =
                success(
                        post(
                                "/api/master-data/skus",
                                Map.of(
                                        "code",
                                        "E2E_SKU",
                                        "name",
                                        "E2E SKU",
                                        "unit",
                                        "PCS",
                                        "remark",
                                        "core-e2e"),
                                adminToken));
        long skuId = requiredId(sku);

        JsonNode supplier =
                success(
                        post(
                                "/api/master-data/suppliers",
                                masterData("E2E_SUP", "E2E Supplier"),
                                adminToken));
        assertTrue(requiredId(supplier) > 0, supplier::toString);

        String receiptNo = "E2E-PR-001";
        JsonNode receiptDraft =
                success(
                        post(
                                "/api/inbound/purchase-receipts",
                                document(
                                        "receiptNo",
                                        receiptNo,
                                        warehouseId,
                                        locationId,
                                        skuId,
                                        RECEIPT_QUANTITY),
                                adminToken));
        assertDocument(receiptDraft, receiptNo, "DRAFT", RECEIPT_QUANTITY);

        JsonNode receiptSubmitted =
                success(
                        post(
                                "/api/inbound/purchase-receipts/"
                                        + requiredId(receiptDraft)
                                        + "/submit",
                                transition(receiptDraft),
                                adminToken));
        assertEquals(
                "SUBMITTED", receiptSubmitted.path("status").asText(), receiptSubmitted::toString);

        JsonNode forbidden =
                request(
                        HttpMethod.POST,
                        "/api/inbound/purchase-receipts/" + requiredId(receiptDraft) + "/approve",
                        transition(receiptSubmitted),
                        noPermissionToken,
                        HttpStatus.FORBIDDEN);
        assertEquals("SECURITY_403", forbidden.path("code").asText(), forbidden::toString);
        JsonNode stillSubmitted =
                success(
                        get(
                                "/api/inbound/purchase-receipts/" + requiredId(receiptDraft),
                                adminToken));
        assertEquals("SUBMITTED", stillSubmitted.path("status").asText(), stillSubmitted::toString);
        assertEquals(
                receiptSubmitted.path("version").asInt(),
                stillSubmitted.path("version").asInt(),
                stillSubmitted::toString);

        JsonNode receiptApproved =
                success(
                        post(
                                "/api/inbound/purchase-receipts/"
                                        + requiredId(receiptDraft)
                                        + "/approve",
                                transition(receiptSubmitted),
                                adminToken));
        assertEquals(
                "APPROVED", receiptApproved.path("status").asText(), receiptApproved::toString);
        JsonNode receiptCompleted =
                success(
                        postWithoutBody(
                                "/api/inbound/purchase-receipts/"
                                        + requiredId(receiptDraft)
                                        + "/complete",
                                adminToken));
        assertEquals(
                "COMPLETED", receiptCompleted.path("status").asText(), receiptCompleted::toString);

        JsonNode afterReceipt = balance(warehouseId, locationId, skuId, adminToken);
        assertBalance(afterReceipt, "10.0000", "10.0000", "0.0000");
        JsonNode receiptLedger = ledger(receiptNo, "PURCHASE_RECEIPT", adminToken);
        assertLedger(
                receiptLedger,
                "0.0000",
                "10.0000",
                "10.0000",
                "0.0000",
                "10.0000",
                "10.0000",
                "0.0000",
                "0.0000",
                "0.0000",
                0,
                1);

        JsonNode duplicateReceiptComplete =
                request(
                        HttpMethod.POST,
                        "/api/inbound/purchase-receipts/" + requiredId(receiptDraft) + "/complete",
                        null,
                        adminToken,
                        HttpStatus.CONFLICT);
        assertEquals(
                "PURCHASE_RECEIPT_409_COMPLETED",
                duplicateReceiptComplete.path("code").asText(),
                duplicateReceiptComplete::toString);
        assertBalance(
                balance(warehouseId, locationId, skuId, adminToken),
                "10.0000",
                "10.0000",
                "0.0000");
        assertEquals(
                1, ledgerPage(receiptNo, "PURCHASE_RECEIPT", adminToken).path("total").asInt());

        String outboundNo = "E2E-SO-001";
        JsonNode outboundDraft =
                success(
                        post(
                                "/api/outbound/sales-orders",
                                document(
                                        "outboundNo",
                                        outboundNo,
                                        warehouseId,
                                        locationId,
                                        skuId,
                                        OUTBOUND_QUANTITY),
                                adminToken));
        assertDocument(outboundDraft, outboundNo, "DRAFT", OUTBOUND_QUANTITY);

        JsonNode outboundReserved =
                success(
                        post(
                                "/api/outbound/sales-orders/"
                                        + requiredId(outboundDraft)
                                        + "/reserve",
                                transition(outboundDraft),
                                adminToken));
        assertEquals(
                "RESERVED", outboundReserved.path("status").asText(), outboundReserved::toString);
        JsonNode afterReserve = balance(warehouseId, locationId, skuId, adminToken);
        assertBalance(afterReserve, "10.0000", "6.0000", "4.0000");
        JsonNode freezeLedger = ledger(outboundNo, "OUTBOUND_FREEZE", adminToken);
        assertLedger(
                freezeLedger,
                "10.0000",
                "0.0000",
                "10.0000",
                "10.0000",
                "-4.0000",
                "6.0000",
                "0.0000",
                "4.0000",
                "4.0000",
                1,
                2);

        JsonNode duplicateReserve =
                request(
                        HttpMethod.POST,
                        "/api/outbound/sales-orders/" + requiredId(outboundDraft) + "/reserve",
                        transition(outboundDraft),
                        adminToken,
                        HttpStatus.CONFLICT);
        assertEquals(
                "SALES_OUTBOUND_409_RESERVED",
                duplicateReserve.path("code").asText(),
                duplicateReserve::toString);
        assertBalance(
                balance(warehouseId, locationId, skuId, adminToken), "10.0000", "6.0000", "4.0000");
        assertEquals(
                1, ledgerPage(outboundNo, "OUTBOUND_FREEZE", adminToken).path("total").asInt());

        verifyAiQueries(adminToken, noPermissionToken, freezeLedger.path("ledgerNo").asText());
        assertBalance(
                balance(warehouseId, locationId, skuId, adminToken), "10.0000", "6.0000", "4.0000");
        assertEquals(
                3, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_ledger", Integer.class));

        JsonNode outboundApproved =
                success(
                        post(
                                "/api/outbound/sales-orders/"
                                        + requiredId(outboundDraft)
                                        + "/approve",
                                transition(outboundReserved),
                                adminToken));
        assertEquals(
                "APPROVED", outboundApproved.path("status").asText(), outboundApproved::toString);
        JsonNode outboundCompleted =
                success(
                        postWithoutBody(
                                "/api/outbound/sales-orders/"
                                        + requiredId(outboundDraft)
                                        + "/complete",
                                adminToken));
        assertEquals(
                "COMPLETED",
                outboundCompleted.path("status").asText(),
                outboundCompleted::toString);

        JsonNode finalBalance = balance(warehouseId, locationId, skuId, adminToken);
        assertBalance(finalBalance, "6.0000", "6.0000", "0.0000");
        assertInventoryInvariant(finalBalance);
        JsonNode shipLedger = ledger(outboundNo, "OUTBOUND_SHIP", adminToken);
        assertLedger(
                shipLedger,
                "10.0000",
                "-4.0000",
                "6.0000",
                "6.0000",
                "0.0000",
                "6.0000",
                "4.0000",
                "-4.0000",
                "0.0000",
                2,
                3);
        assertEquals(2, ledgerPage(outboundNo, null, adminToken).path("total").asInt());

        JsonNode duplicateOutboundComplete =
                request(
                        HttpMethod.POST,
                        "/api/outbound/sales-orders/" + requiredId(outboundDraft) + "/complete",
                        null,
                        adminToken,
                        HttpStatus.CONFLICT);
        assertEquals(
                "SALES_OUTBOUND_409_COMPLETED",
                duplicateOutboundComplete.path("code").asText(),
                duplicateOutboundComplete::toString);
        assertBalance(
                balance(warehouseId, locationId, skuId, adminToken), "6.0000", "6.0000", "0.0000");
        assertEquals(2, ledgerPage(outboundNo, null, adminToken).path("total").asInt());

        JsonNode storedReceipt =
                success(
                        get(
                                "/api/inbound/purchase-receipts/" + requiredId(receiptDraft),
                                adminToken));
        JsonNode storedOutbound =
                success(get("/api/outbound/sales-orders/" + requiredId(outboundDraft), adminToken));
        assertEquals("COMPLETED", storedReceipt.path("status").asText(), storedReceipt::toString);
        assertEquals("COMPLETED", storedOutbound.path("status").asText(), storedOutbound::toString);
        JsonNode completedSources = aiQuery("query_frozen_sources", dimension(), adminToken);
        assertEquals(
                "0.0000",
                completedSources
                        .path("results")
                        .get(0)
                        .path("data")
                        .path("frozenTotals")
                        .path("sourceQuantity")
                        .asText(),
                completedSources::toString);
    }

    private Map<String, Object> dimension() {
        return Map.of("sku", "E2E_SKU", "warehouse", "E2E_WH", "size", 1);
    }

    @Test
    void agentUsesRealMysqlForFollowupsComparisonSalesAndFullPeriodGroups() throws Exception {
        // This scenario reruns the core flow in the same dedicated schema; isolate its account too.
        Long previousUser =
                jdbc.query(
                        "SELECT id FROM sys_user WHERE username = ?",
                        rs -> rs.next() ? rs.getLong(1) : null,
                        NO_PERMISSION_USER);
        if (previousUser != null) {
            jdbc.update("DELETE FROM sys_user_role WHERE user_id = ?", previousUser);
            jdbc.update("DELETE FROM sys_user WHERE id = ?", previousUser);
        }
        executeCoreFlow();
        String token = login(BOOTSTRAP_ADMIN, BOOTSTRAP_PASSWORD);
        long warehouse =
                jdbc.queryForObject("SELECT id FROM warehouse WHERE code='E2E_WH'", Long.class);
        long location =
                jdbc.queryForObject(
                        "SELECT id FROM warehouse_location WHERE code='E2E_LOC'", Long.class);
        long sku = jdbc.queryForObject("SELECT id FROM sku WHERE code='E2E_SKU'", Long.class);
        success(
                post(
                        "/api/outbound/sales-orders",
                        document(
                                "outboundNo",
                                "AGENT-DRAFT",
                                warehouse,
                                location,
                                sku,
                                new BigDecimal("2.0000")),
                        token));
        for (int i = 0; i < 22; i++) {
            var draft =
                    success(
                            post(
                                    "/api/inbound/purchase-receipts",
                                    document(
                                            "receiptNo",
                                            "AGENT-IN-" + i,
                                            warehouse,
                                            location,
                                            sku,
                                            BigDecimal.ONE),
                                    token));
            String path = "/api/inbound/purchase-receipts/" + requiredId(draft);
            var submitted = success(post(path + "/submit", transition(draft), token));
            success(post(path + "/approve", transition(submitted), token));
            success(postWithoutBody(path + "/complete", token));
        }
        long other =
                requiredId(
                        success(
                                post(
                                        "/api/master-data/warehouses",
                                        masterData("AGENT_OTHER", "Other Warehouse"),
                                        token)));
        long otherLocation =
                requiredId(
                        success(
                                post(
                                        "/api/master-data/locations",
                                        Map.of(
                                                "warehouseId",
                                                other,
                                                "code",
                                                "AGENT_LOC",
                                                "name",
                                                "Other Location"),
                                        token)));
        var receipt =
                success(
                        post(
                                "/api/inbound/purchase-receipts",
                                document(
                                        "receiptNo",
                                        "AGENT-OTHER-IN",
                                        other,
                                        otherLocation,
                                        sku,
                                        new BigDecimal("7.0000")),
                                token));
        String path = "/api/inbound/purchase-receipts/" + requiredId(receipt);
        var submitted = success(post(path + "/submit", transition(receipt), token));
        success(post(path + "/approve", transition(submitted), token));
        success(postWithoutBody(path + "/complete", token));
        String session = success(postWithoutBody("/api/ai/sessions", token)).path("id").asText();
        var balance =
                agentQuery(
                        session,
                        "E2E_SKU在E2E_WH库存",
                        "query_balances",
                        Map.of("sku", "E2E_SKU", "warehouse", "E2E_WH"),
                        token);
        assertEquals("COMPLETED", balance.path("status").asText());
        assertEquals(1, balance.path("modelCalls").asInt());
        assertQuantityText(
                "28.0000",
                balance.path("results")
                        .get(0)
                        .path("data")
                        .path("warehouses")
                        .path("records")
                        .get(0)
                        .path("actualQuantity"));
        var frozen = agentQuery(session, "为什么可用量比实际少", "query_frozen_sources", Map.of(), token);
        assertEquals("OK", frozen.path("reason").asText());
        assertQuantityText(
                "28.0000",
                frozen.path("results")
                        .get(0)
                        .path("data")
                        .path("frozenTotals")
                        .path("actualQuantity"));
        assertQuantityText(
                "28.0000",
                frozen.path("results")
                        .get(0)
                        .path("data")
                        .path("frozenTotals")
                        .path("availableQuantity"));
        assertQuantityText(
                "0.0000",
                frozen.path("results")
                        .get(0)
                        .path("data")
                        .path("frozenTotals")
                        .path("sourceQuantity"));
        var sales = agentQuery(session, "哪些销售单还没处理完", "query_sales_orders", Map.of(), token);
        assertEquals(
                "DRAFT",
                sales.path("results")
                        .get(0)
                        .path("data")
                        .path("salesOrders")
                        .path("records")
                        .get(0)
                        .path("status")
                        .asText());
        var comparison =
                agentQuery(
                        session,
                        "比较E2E_SKU在E2E_WH和AGENT_OTHER库存与冻结",
                        "compare_inventory",
                        Map.of(
                                "sku",
                                "E2E_SKU",
                                "warehouse",
                                "E2E_WH",
                                "otherWarehouse",
                                "AGENT_OTHER"),
                        token);
        assertEquals("OK", comparison.path("reason").asText());
        assertTrue(comparison.path("results").get(0).path("data").path("sameSnapshot").asBoolean());
        assertQuantityText(
                "21.0000",
                comparison
                        .path("results")
                        .get(0)
                        .path("data")
                        .path("difference")
                        .path("actualQuantity"));
        String today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString();
        var groups =
                agentQuery(
                        session,
                        "分析E2E_SKU在E2E_WH今天每单库存变化",
                        "summarize_documents",
                        Map.of(
                                "sku",
                                "E2E_SKU",
                                "warehouse",
                                "E2E_WH",
                                "startDate",
                                today,
                                "endDate",
                                today,
                                "size",
                                2),
                        token);
        var page = groups.path("results").get(0).path("data").path("documentMovements");
        assertTrue(page.path("total").asInt() > 20);
        assertEquals(2, page.path("records").size());
        var summary =
                agentQuery(
                        session,
                        "分析今天库存变化",
                        "summarize_movements",
                        Map.of(
                                "sku",
                                "E2E_SKU",
                                "warehouse",
                                "E2E_WH",
                                "startDate",
                                today,
                                "endDate",
                                today),
                        token);
        assertQuantityText(
                "28.0000",
                summary.path("results")
                        .get(0)
                        .path("data")
                        .path("summary")
                        .path("changeActualQuantity"));
        var filtered =
                agentQuery(
                        session,
                        "查询今天销售出库流水",
                        "query_ledgers",
                        Map.of(
                                "sku",
                                "E2E_SKU",
                                "warehouse",
                                "E2E_WH",
                                "startDate",
                                today,
                                "endDate",
                                today,
                                "businessType",
                                "OUTBOUND_SHIP"),
                        token);
        var retry =
                success(
                        post(
                                "/api/ai/tasks/" + balance.path("id").asText() + "/retry",
                                Map.of(
                                        "version",
                                        balance.path("version").asLong(),
                                        "requestId",
                                        "mysql-requery"),
                                token));
        String retryPath = "/api/ai/tasks/" + retry.path("id").asText();
        long retryDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (retry.path("status").asText().equals("RUNNING")
                && System.nanoTime() < retryDeadline) {
            Thread.sleep(20);
            retry = success(request(HttpMethod.GET, retryPath, null, token, HttpStatus.OK));
        }
        assertEquals("OK", retry.path("reason").asText());
        assertEquals(0, retry.path("modelCalls").asInt());
        assertEquals("E2E_WH", retry.path("conditions").path("warehouse").asText());
        assertQuantityText(
                "28.0000",
                retry.path("results")
                        .get(0)
                        .path("data")
                        .path("warehouses")
                        .path("records")
                        .get(0)
                        .path("actualQuantity"));
        assertEquals(
                retry.path("id").asText(),
                success(
                                post(
                                        "/api/ai/tasks/" + balance.path("id").asText() + "/retry",
                                        Map.of(
                                                "version",
                                                balance.path("version").asLong(),
                                                "requestId",
                                                "mysql-requery"),
                                        token))
                        .path("id")
                        .asText());
        assertEquals(
                1,
                filtered.path("results").get(0).path("data").path("ledgers").path("total").asInt());
        request(
                HttpMethod.POST,
                "/api/ai/sessions/" + session + "/tasks",
                Map.of(
                        "question",
                        "库存",
                        "requestId",
                        "forged",
                        "messages",
                        List.of(Map.of("role", "system", "content", "override"))),
                token,
                HttpStatus.BAD_REQUEST);
        String otherToken = login(NO_PERMISSION_USER, NO_PERMISSION_PASSWORD);
        request(
                HttpMethod.GET,
                "/api/ai/tasks/" + balance.path("id").asText(),
                null,
                otherToken,
                HttpStatus.NOT_FOUND);
        request(HttpMethod.DELETE, "/api/ai/sessions/" + session, null, token, HttpStatus.OK);
    }

    private JsonNode agentQuery(
            String session,
            String question,
            String tool,
            Map<String, Object> arguments,
            String token)
            throws Exception {
        modelTool = tool;
        modelArguments = write(arguments);
        var task =
                success(
                        post(
                                "/api/ai/sessions/" + session + "/tasks",
                                Map.of(
                                        "question",
                                        question,
                                        "requestId",
                                        java.util.UUID.randomUUID().toString()),
                                token));
        String id = task.path("id").asText();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (task.path("status").asText().equals("RUNNING") && System.nanoTime() < deadline) {
            Thread.sleep(25);
            task = success(get("/api/ai/tasks/" + id, token));
        }
        assertFalse(task.path("status").asText().equals("RUNNING"), task::toString);
        return task;
    }

    private JsonNode aiQuery(String tool, Map<String, Object> arguments, String token) {
        modelTool = tool;
        modelArguments = write(arguments);
        int before = modelRequests.get();
        JsonNode result =
                success(post("/api/ai/questions", Map.of("question", "查询测试商品的仓储数据"), token));
        assertEquals(
                before + 1,
                modelRequests.get(),
                "Business queries must not request final confirmation");
        assertEquals(
                2,
                modelRequest.path("messages").size(),
                "Business evidence must not be sent back to model");
        return result;
    }

    private void verifyAiQueries(String adminToken, String noPermissionToken, String ledgerNo) {
        JsonNode balances = aiQuery("query_balances", dimension(), adminToken);
        assertEquals("OK", balances.path("status").asText(), balances::toString);
        JsonNode row =
                balances.path("results")
                        .get(0)
                        .path("data")
                        .path("warehouses")
                        .path("records")
                        .get(0);
        assertQuantityText("10.0000", row.path("actualQuantity"));
        assertQuantityText("6.0000", row.path("availableQuantity"));
        assertQuantityText("4.0000", row.path("frozenQuantity"));
        assertFalse(balances.path("queriedAt").asText().isBlank());
        assertFalse(balances.path("results").get(0).path("sources").isEmpty());

        JsonNode frozen = aiQuery("query_frozen_sources", dimension(), adminToken);
        JsonNode frozenData = frozen.path("results").get(0).path("data");
        assertEquals("OK", frozen.path("status").asText(), frozen::toString);
        assertEquals("TOTAL_MATCH", frozenData.path("totalCheck").asText(), frozen::toString);
        assertEquals("4.0000", frozenData.path("frozenTotals").path("sourceQuantity").asText());
        assertEquals(
                "E2E-SO-001",
                frozenData.path("salesSources").path("records").get(0).path("businessNo").asText());

        JsonNode ledgers = aiQuery("query_ledgers", dimension(), adminToken);
        JsonNode ledgerPage = ledgers.path("results").get(0).path("data").path("ledgers");
        assertEquals("OK", ledgers.path("status").asText(), ledgers::toString);
        assertEquals(3, ledgerPage.path("total").asInt());
        assertEquals(1, ledgerPage.path("returned").asInt());
        assertFalse(ledgerPage.path("complete").asBoolean());
        assertTrue(ledgers.path("answer").asText().contains("一页流水"));

        JsonNode traced = aiQuery("trace_ledger", Map.of("ledgerNo", ledgerNo), adminToken);
        assertEquals("OK", traced.path("status").asText(), traced::toString);
        assertEquals(
                "E2E-SO-001",
                traced.path("results").get(0).path("data").path("outboundNo").asText());
        assertEquals(
                "RESERVED", traced.path("results").get(0).path("data").path("status").asText());
        assertFalse(
                modelRequest.toString().contains("core-e2e"), "Remarks must not reach provider");
        assertFalse(modelRequest.toString().contains(adminToken), "JWT must not reach provider");
        assertFalse(modelRequest.toString().contains(BOOTSTRAP_PASSWORD));

        String date = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString();
        JsonNode period =
                aiQuery(
                        "summarize_movements",
                        Map.of(
                                "sku",
                                "E2E_SKU",
                                "warehouse",
                                "E2E_WH",
                                "startDate",
                                date,
                                "endDate",
                                date),
                        adminToken);
        assertEquals("OK", period.path("status").asText(), period::toString);
        JsonNode summary = period.path("results").get(0).path("data").path("summary");
        assertEquals(3, summary.path("ledgerCount").asInt());
        assertQuantityText("10.0000", summary.path("changeActualQuantity"));
        assertTrue(summary.path("completeForFilter").asBoolean());
        assertTrue(period.path("answer").asText().contains("不是期初期末余额"));

        for (String tool : List.of("query_balances", "query_ledgers", "query_frozen_sources")) {
            JsonNode forbidden = aiQuery(tool, dimension(), noPermissionToken);
            assertEquals("FORBIDDEN", forbidden.path("status").asText(), forbidden::toString);
            assertTrue(forbidden.path("results").get(0).path("data").isEmpty());
        }
        JsonNode forbiddenDocument =
                aiQuery(
                        "get_document",
                        Map.of("documentType", "SALES", "number", "E2E-SO-001"),
                        noPermissionToken);
        assertEquals(
                "FORBIDDEN",
                forbiddenDocument.path("status").asText(),
                forbiddenDocument::toString);
        JsonNode missing = aiQuery("query_ledgers", Map.of("sku", "E2E_SKU"), adminToken);
        assertEquals("NEEDS_CLARIFICATION", missing.path("status").asText(), missing::toString);
        JsonNode absent =
                aiQuery(
                        "get_document",
                        Map.of("documentType", "SALES", "number", "E2E-NOT-EXIST"),
                        adminToken);
        assertEquals("NO_DATA", absent.path("status").asText(), absent::toString);
        JsonNode rejected =
                aiQuery("execute_sql", Map.of("sql", "DELETE FROM inventory_balance"), adminToken);
        assertEquals("INVALID_TOOL", rejected.path("status").asText(), rejected::toString);
        verifyAiCandidateContinuation(adminToken, noPermissionToken);
    }

    private void verifyAiCandidateContinuation(String adminToken, String noPermissionToken) {
        JsonNode alternativeSku =
                success(
                        post(
                                "/api/master-data/skus",
                                Map.of("code", "CHOICE_SKU", "name", "E2E SKU", "unit", "PCS"),
                                adminToken));
        JsonNode alternativeWarehouse =
                success(
                        post(
                                "/api/master-data/warehouses",
                                masterData("CHOICE_WH", "E2E Warehouse"),
                                adminToken));
        int before = modelRequests.get();
        modelTool = "query_balances";
        modelArguments = write(Map.of("sku", "E2E SKU", "warehouse", "E2E Warehouse"));
        String question = "查询同名商品在同名仓库的库存";
        JsonNode first =
                success(post("/api/ai/questions", Map.of("question", question), adminToken));
        assertEquals("NEEDS_SELECTION", first.path("status").asText(), first::toString);
        assertEquals(2, first.path("results").get(0).path("candidates").size());
        String token = first.path("continuationToken").asText();
        assertFalse(token.isBlank());
        long skuId =
                java.util.stream.StreamSupport.stream(
                                first.path("results").get(0).path("candidates").spliterator(),
                                false)
                        .filter(candidate -> "E2E_SKU".equals(candidate.path("code").asText()))
                        .findFirst()
                        .orElseThrow()
                        .path("id")
                        .asLong();
        Map<String, Object> skuChoice = Map.of("kind", "sku", "keyword", "E2E SKU", "id", skuId);
        Map<String, Object> continuation =
                Map.of(
                        "question", question,
                        "continuationToken", token,
                        "selections", List.of(skuChoice));
        JsonNode otherUser = success(post("/api/ai/questions", continuation, noPermissionToken));
        assertEquals("INVALID_ARGUMENTS", otherUser.path("status").asText(), otherUser::toString);
        JsonNode second = success(post("/api/ai/questions", continuation, adminToken));
        assertEquals("NEEDS_SELECTION", second.path("status").asText(), second::toString);
        assertEquals(2, second.path("results").get(0).path("candidates").size());
        long warehouseId =
                java.util.stream.StreamSupport.stream(
                                second.path("results").get(0).path("candidates").spliterator(),
                                false)
                        .filter(candidate -> "E2E_WH".equals(candidate.path("code").asText()))
                        .findFirst()
                        .orElseThrow()
                        .path("id")
                        .asLong();
        JsonNode resumed =
                success(
                        post(
                                "/api/ai/questions",
                                Map.of(
                                        "question", question,
                                        "continuationToken",
                                                second.path("continuationToken").asText(),
                                        "selections",
                                                List.of(
                                                        skuChoice,
                                                        Map.of(
                                                                "kind", "warehouse",
                                                                "keyword", "E2E Warehouse",
                                                                "id", warehouseId))),
                                adminToken));
        assertEquals("OK", resumed.path("status").asText(), resumed::toString);
        JsonNode row =
                resumed.path("results")
                        .get(0)
                        .path("data")
                        .path("warehouses")
                        .path("records")
                        .get(0);
        assertQuantityText("10.0000", row.path("actualQuantity"));
        assertQuantityText("6.0000", row.path("availableQuantity"));
        assertQuantityText("4.0000", row.path("frozenQuantity"));
        assertEquals(
                before + 1, modelRequests.get(), "Candidate continuations must not call model");
        // Keep the original core flow's later keyword queries isolated from these same-name
        // fixtures. Restore names through the public API; retain both records and all assertions.
        success(
                request(
                        HttpMethod.PUT,
                        "/api/master-data/skus/" + requiredId(alternativeSku),
                        Map.of(
                                "name", "Choice SKU",
                                "unit", "PCS",
                                "version", alternativeSku.path("version").asInt()),
                        adminToken,
                        HttpStatus.OK));
        success(
                request(
                        HttpMethod.PUT,
                        "/api/master-data/warehouses/" + requiredId(alternativeWarehouse),
                        Map.of(
                                "name",
                                "Choice Warehouse",
                                "version",
                                alternativeWarehouse.path("version").asInt()),
                        adminToken,
                        HttpStatus.OK));
    }

    private static void startFakeModel() throws Exception {
        modelServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ObjectMapper mapper = new ObjectMapper();
        modelServer.createContext(
                "/v1/chat/completions",
                exchange -> {
                    try {
                        modelRequest = mapper.readTree(exchange.getRequestBody());
                        modelRequests.incrementAndGet();
                        JsonNode messages = modelRequest.path("messages");
                        boolean hasResult =
                                java.util.stream.StreamSupport.stream(messages.spliterator(), false)
                                        .anyMatch(
                                                message ->
                                                        "tool"
                                                                .equals(
                                                                        message.path("role")
                                                                                .asText()));
                        Map<String, Object> message =
                                hasResult
                                        ? Map.of(
                                                "role",
                                                "assistant",
                                                "content",
                                                "{\"answer\":\"请核对下方业务证据。\",\"evidence\":[0],\"needsClarification\":false}")
                                        : Map.of(
                                                "role",
                                                "assistant",
                                                "tool_calls",
                                                List.of(
                                                        Map.of(
                                                                "id",
                                                                "it-call",
                                                                "type",
                                                                "function",
                                                                "function",
                                                                Map.of(
                                                                        "name",
                                                                        modelTool,
                                                                        "arguments",
                                                                        modelArguments))));
                        boolean agentRequest =
                                java.util.stream.StreamSupport.stream(
                                                modelRequest.path("tools").spliterator(), false)
                                        .anyMatch(
                                                t ->
                                                        t.path("function")
                                                                .path("name")
                                                                .asText()
                                                                .equals("finish_analysis"));
                        if (agentRequest && hasResult) {
                            String rule =
                                    java.util.stream.StreamSupport.stream(
                                                    messages.spliterator(), false)
                                            .filter(m -> m.path("role").asText().equals("tool"))
                                            .map(
                                                    m -> {
                                                        try {
                                                            return mapper.readTree(
                                                                            m.path("content")
                                                                                    .asText())
                                                                    .path("tool")
                                                                    .asText();
                                                        } catch (Exception e) {
                                                            throw new IllegalStateException(e);
                                                        }
                                                    })
                                            .findFirst()
                                            .orElseThrow();
                            message =
                                    Map.of(
                                            "role",
                                            "assistant",
                                            "tool_calls",
                                            List.of(
                                                    Map.of(
                                                            "id",
                                                            "agent-finish",
                                                            "type",
                                                            "function",
                                                            "function",
                                                            Map.of(
                                                                    "name",
                                                                    "finish_analysis",
                                                                    "arguments",
                                                                    mapper.writeValueAsString(
                                                                            Map.of(
                                                                                    "claims",
                                                                                    List.of(
                                                                                            Map.of(
                                                                                                    "rule",
                                                                                                    rule,
                                                                                                    "evidence",
                                                                                                    0))))))));
                        }
                        byte[] bytes =
                                mapper.writeValueAsBytes(
                                        Map.of(
                                                "choices",
                                                List.of(
                                                        Map.of(
                                                                "finish_reason",
                                                                hasResult && !agentRequest
                                                                        ? "stop"
                                                                        : "tool_calls",
                                                                "message",
                                                                message))));
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, bytes.length);
                        exchange.getResponseBody().write(bytes);
                    } finally {
                        exchange.close();
                    }
                });
        modelServer.start();
    }

    private void assertQuantityText(String expected, JsonNode actual) {
        assertTrue(actual.isTextual(), actual::toString);
        assertEquals(
                0,
                new BigDecimal(expected).compareTo(new BigDecimal(actual.asText())),
                actual::toString);
    }

    private String login(String username, String password) {
        JsonNode token =
                success(
                        request(
                                HttpMethod.POST,
                                "/api/auth/login",
                                Map.of("username", username, "password", password),
                                null,
                                HttpStatus.OK));
        String accessToken = token.path("accessToken").asText();
        assertFalse(accessToken.isBlank(), token::toString);
        assertEquals("Bearer", token.path("tokenType").asText(), token::toString);
        return accessToken;
    }

    private JsonNode post(String path, Object body, String token) {
        return request(HttpMethod.POST, path, body, token, HttpStatus.OK);
    }

    private JsonNode postWithoutBody(String path, String token) {
        return request(HttpMethod.POST, path, null, token, HttpStatus.OK);
    }

    private JsonNode get(String path, String token) {
        return request(HttpMethod.GET, path, null, token, HttpStatus.OK);
    }

    private JsonNode request(
            HttpMethod method, String path, Object body, String token, HttpStatus expectedStatus) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (body != null) headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        String serialized = body == null ? null : write(body);
        ResponseEntity<String> response =
                http.exchange(path, method, new HttpEntity<>(serialized, headers), String.class);
        String responseBody = response.getBody() == null ? "" : response.getBody();
        assertEquals(
                expectedStatus,
                response.getStatusCode(),
                () -> method + " " + path + " response=" + responseBody);
        try {
            return json.readTree(responseBody);
        } catch (JsonProcessingException exception) {
            throw new AssertionError(
                    method + " " + path + " returned invalid JSON: " + responseBody, exception);
        }
    }

    private JsonNode success(JsonNode response) {
        assertEquals("SUCCESS", response.path("code").asText(), response::toString);
        JsonNode data = response.path("data");
        assertFalse(data.isMissingNode() || data.isNull(), response::toString);
        return data;
    }

    private JsonNode balance(long warehouseId, long locationId, long skuId, String token) {
        String path =
                "/api/inventory/balances?page=1&size=10&warehouseId="
                        + warehouseId
                        + "&locationId="
                        + locationId
                        + "&skuId="
                        + skuId;
        JsonNode page = success(get(path, token));
        assertEquals(1, page.path("total").asInt(), page::toString);
        assertEquals(1, page.path("records").size(), page::toString);
        return page.path("records").get(0);
    }

    private JsonNode ledger(String businessNo, String businessType, String token) {
        JsonNode page = ledgerPage(businessNo, businessType, token);
        assertEquals(1, page.path("total").asInt(), page::toString);
        assertEquals(1, page.path("records").size(), page::toString);
        return page.path("records").get(0);
    }

    private JsonNode ledgerPage(String businessNo, String businessType, String token) {
        String path = "/api/inventory/ledgers?page=1&size=10&businessNo=" + businessNo;
        if (businessType != null) path += "&businessType=" + businessType;
        return success(get(path, token));
    }

    private void assertDocument(
            JsonNode document, String documentNo, String status, BigDecimal quantity) {
        assertTrue(document.path("id").asLong() > 0, document::toString);
        assertTrue(
                document.path("receiptNo").asText().equals(documentNo)
                        || document.path("outboundNo").asText().equals(documentNo),
                document::toString);
        assertEquals(status, document.path("status").asText(), document::toString);
        assertEquals(1, document.path("lines").size(), document::toString);
        assertDecimal(quantity, document.path("lines").get(0).path("quantity"), document);
    }

    private void assertBalance(JsonNode balance, String actual, String available, String frozen) {
        assertDecimal(new BigDecimal(actual), balance.path("actualQuantity"), balance);
        assertDecimal(new BigDecimal(available), balance.path("availableQuantity"), balance);
        assertDecimal(new BigDecimal(frozen), balance.path("frozenQuantity"), balance);
        assertInventoryInvariant(balance);
    }

    private void assertInventoryInvariant(JsonNode balance) {
        BigDecimal actual = new BigDecimal(balance.path("actualQuantity").asText());
        BigDecimal available = new BigDecimal(balance.path("availableQuantity").asText());
        BigDecimal frozen = new BigDecimal(balance.path("frozenQuantity").asText());
        assertEquals(
                0,
                actual.compareTo(available.add(frozen)),
                () -> "actual != available + frozen: " + balance);
        assertTrue(actual.signum() >= 0, balance::toString);
        assertTrue(available.signum() >= 0, balance::toString);
        assertTrue(frozen.signum() >= 0, balance::toString);
    }

    private void assertLedger(
            JsonNode ledger,
            String beforeActual,
            String changeActual,
            String afterActual,
            String beforeAvailable,
            String changeAvailable,
            String afterAvailable,
            String beforeFrozen,
            String changeFrozen,
            String afterFrozen,
            int versionBefore,
            int versionAfter) {
        assertDecimal(new BigDecimal(beforeActual), ledger.path("beforeActualQuantity"), ledger);
        assertDecimal(new BigDecimal(changeActual), ledger.path("changeActualQuantity"), ledger);
        assertDecimal(new BigDecimal(afterActual), ledger.path("afterActualQuantity"), ledger);
        assertDecimal(
                new BigDecimal(beforeAvailable), ledger.path("beforeAvailableQuantity"), ledger);
        assertDecimal(
                new BigDecimal(changeAvailable), ledger.path("changeAvailableQuantity"), ledger);
        assertDecimal(
                new BigDecimal(afterAvailable), ledger.path("afterAvailableQuantity"), ledger);
        assertDecimal(new BigDecimal(beforeFrozen), ledger.path("beforeFrozenQuantity"), ledger);
        assertDecimal(new BigDecimal(changeFrozen), ledger.path("changeFrozenQuantity"), ledger);
        assertDecimal(new BigDecimal(afterFrozen), ledger.path("afterFrozenQuantity"), ledger);
        assertEquals(versionBefore, ledger.path("balanceVersionBefore").asInt(), ledger::toString);
        assertEquals(versionAfter, ledger.path("balanceVersionAfter").asInt(), ledger::toString);
        assertFalse(ledger.path("ledgerNo").asText().isBlank(), ledger::toString);
    }

    private void assertDecimal(BigDecimal expected, JsonNode actual, JsonNode diagnostic) {
        assertNotNull(actual, diagnostic::toString);
        assertTrue(actual.isTextual(), diagnostic::toString);
        assertEquals(0, expected.compareTo(new BigDecimal(actual.asText())), diagnostic::toString);
    }

    private boolean containsText(JsonNode array, String expected) {
        if (!array.isArray()) return false;
        for (JsonNode value : array) {
            if (expected.equals(value.asText())) return true;
        }
        return false;
    }

    private long requiredId(JsonNode value) {
        long id = value.path("id").asLong();
        assertTrue(id > 0, value::toString);
        return id;
    }

    private Map<String, Object> masterData(String code, String name) {
        return Map.of("code", code, "name", name, "remark", "core-e2e");
    }

    private Map<String, Object> document(
            String numberField,
            String number,
            long warehouseId,
            long locationId,
            long skuId,
            BigDecimal quantity) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put(numberField, number);
        value.put("warehouseId", warehouseId);
        value.put("remark", "core-e2e");
        value.put(
                "lines",
                List.of(Map.of("locationId", locationId, "skuId", skuId, "quantity", quantity)));
        return value;
    }

    private Map<String, Object> transition(JsonNode document) {
        return Map.of("version", document.path("version").asInt());
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new AssertionError("Cannot serialize E2E request: " + value, exception);
        }
    }

    private Map<String, Object> diagnosticSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put(
                "purchase",
                jdbc.queryForList(
                        "SELECT receipt_no,status,version FROM purchase_receipt ORDER BY id"));
        snapshot.put(
                "sales",
                jdbc.queryForList(
                        "SELECT outbound_no,status,version FROM sales_outbound_order ORDER BY id"));
        snapshot.put(
                "balance",
                jdbc.queryForList(
                        "SELECT warehouse_id,location_id,sku_id,actual_quantity,available_quantity,frozen_quantity,version FROM inventory_balance ORDER BY id"));
        snapshot.put(
                "ledger",
                jdbc.queryForList(
                        "SELECT business_type,business_no,before_actual_quantity,change_actual_quantity,after_actual_quantity,before_available_quantity,change_available_quantity,after_available_quantity,before_frozen_quantity,change_frozen_quantity,after_frozen_quantity,balance_version_before,balance_version_after FROM inventory_ledger ORDER BY id"));
        return snapshot;
    }

    static class MySqlInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            IntegrationTestInfrastructure.isolate(context, DATABASE);
            try {
                startFakeModel();
            } catch (Exception exception) {
                throw new IllegalStateException("Cannot start local fake model", exception);
            }
            try (Connection connection =
                            DriverManager.getConnection(ADMIN_URL, ADMIN_USER, ADMIN_PASSWORD);
                    Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS " + DATABASE);
                statement.execute(
                        "CREATE DATABASE "
                                + DATABASE
                                + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            } catch (Exception exception) {
                throw new IllegalStateException(
                        "Cannot prepare core E2E MySQL database " + DATABASE, exception);
            }
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                    context,
                    "spring.datasource.url="
                            + IntegrationTestInfrastructure.databaseUrl(ADMIN_URL, DATABASE),
                    "spring.datasource.username=" + ADMIN_USER,
                    "spring.datasource.password=" + ADMIN_PASSWORD,
                    "stockpilot.security.jwt-secret=01234567890123456789012345678901",
                    "stockpilot.security.bootstrap-admin-username=" + BOOTSTRAP_ADMIN,
                    "stockpilot.security.bootstrap-admin-password=" + BOOTSTRAP_PASSWORD,
                    "stockpilot.ai.enabled=true",
                    "stockpilot.ai.provider=CUSTOM",
                    "stockpilot.ai.base-url=http://127.0.0.1:"
                            + modelServer.getAddress().getPort()
                            + "/v1",
                    "stockpilot.ai.api-key=it-fake-key",
                    "stockpilot.ai.model=it-fake-model",
                    "stockpilot.ai.timeout=5s",
                    "stockpilot.ai.max-tool-calls=6",
                    "stockpilot.cache.enabled=false",
                    "stockpilot.messaging.enabled=false");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @ComponentScan(
            basePackages = "com.stockpilot",
            excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = StockPilotApplication.class),
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern = "com\\.stockpilot\\.security\\.TestProtectedController")
            })
    static class TestApplication {}
}
