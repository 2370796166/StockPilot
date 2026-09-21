package com.stockpilot.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.StockPilotApplication;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
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
    private static final String DATABASE = "stockpilot_core_e2e_it";
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

    @Autowired private TestRestTemplate http;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;

    @AfterAll
    static void dropDedicatedDatabase() throws Exception {
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
        BigDecimal actual = balance.path("actualQuantity").decimalValue();
        BigDecimal available = balance.path("availableQuantity").decimalValue();
        BigDecimal frozen = balance.path("frozenQuantity").decimalValue();
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
        assertEquals(0, expected.compareTo(actual.decimalValue()), diagnostic::toString);
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
                    "spring.datasource.url=jdbc:mysql://localhost:3307/"
                            + DATABASE
                            + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false",
                    "spring.datasource.username=" + ADMIN_USER,
                    "spring.datasource.password=" + ADMIN_PASSWORD,
                    "stockpilot.security.jwt-secret=01234567890123456789012345678901",
                    "stockpilot.security.bootstrap-admin-username=" + BOOTSTRAP_ADMIN,
                    "stockpilot.security.bootstrap-admin-password=" + BOOTSTRAP_PASSWORD,
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
