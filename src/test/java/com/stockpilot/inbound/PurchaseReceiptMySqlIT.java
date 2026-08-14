package com.stockpilot.inbound;

import com.stockpilot.common.exception.BusinessException;
import com.stockpilot.StockPilotApplication;
import com.stockpilot.inbound.application.PurchaseReceiptApplicationService;
import com.stockpilot.inbound.domain.PurchaseReceiptStatus;
import com.stockpilot.inbound.request.PurchaseReceiptRequests;
import com.stockpilot.security.auth.StockPilotPrincipal;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.support.TestPropertySourceUtils;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = PurchaseReceiptMySqlIT.TestApplication.class)
@ContextConfiguration(initializers = PurchaseReceiptMySqlIT.MySqlInitializer.class)
class PurchaseReceiptMySqlIT {
    private static final String DATABASE = "stockpilot_purchase_it";
    private static final String ADMIN_URL = System.getenv().getOrDefault(
            "STOCKPILOT_IT_ADMIN_URL",
            "jdbc:mysql://localhost:3307/?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false");
    private static final String ADMIN_USER = System.getenv().getOrDefault("STOCKPILOT_IT_ADMIN_USER", "root");
    private static final String ADMIN_PASSWORD = System.getenv().getOrDefault(
            "STOCKPILOT_IT_ADMIN_PASSWORD", "root_dev_only");
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final StockPilotPrincipal OPERATOR = new StockPilotPrincipal(101L, "it-operator");
    private static final StockPilotPrincipal AUDITOR = new StockPilotPrincipal(102L, "it-auditor");

    @Autowired private PurchaseReceiptApplicationService service;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabaseAndInstallFailureTriggers() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_purchase_ledger");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_purchase_status");
        jdbc.update("DELETE FROM inventory_ledger");
        jdbc.update("DELETE FROM inventory_balance");
        jdbc.update("DELETE FROM purchase_receipt_line");
        jdbc.update("DELETE FROM purchase_receipt");
        jdbc.update("DELETE FROM sku");
        jdbc.update("DELETE FROM warehouse_location");
        jdbc.update("DELETE FROM warehouse");
        jdbc.execute("""
                CREATE TRIGGER fail_purchase_ledger
                BEFORE INSERT ON inventory_ledger
                FOR EACH ROW
                BEGIN
                    IF NEW.business_type = 'PURCHASE_RECEIPT'
                       AND NEW.business_no LIKE 'FAIL-LEDGER-%' THEN
                        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced ledger failure';
                    END IF;
                END
                """);
        jdbc.execute("""
                CREATE TRIGGER fail_purchase_status
                BEFORE UPDATE ON purchase_receipt
                FOR EACH ROW
                BEGIN
                    IF NEW.status = 'COMPLETED'
                       AND NEW.receipt_no LIKE 'FAIL-STATUS-%' THEN
                        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced status failure';
                    END IF;
                END
                """);
    }

    @AfterAll
    static void dropDedicatedDatabase() throws Exception {
        try (Connection connection = DriverManager.getConnection(ADMIN_URL, ADMIN_USER, ADMIN_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + DATABASE);
        }
    }

    @Test
    void completesReceiptOnceAndWritesBalanceAndLedger() {
        Dimension dimension = createEnabledDimension();
        long receiptId = createApproved("PR-NORMAL-" + SEQUENCE.incrementAndGet(), dimension,
                new BigDecimal("5.2500"));

        var completed = service.complete(receiptId, OPERATOR);

        assertEquals(PurchaseReceiptStatus.COMPLETED, completed.status());
        assertBalance(dimension, "5.2500", "5.2500", "0.0000");
        assertEquals(1, purchaseLedgerCount(completed.receiptNo(), dimension));

        BusinessException duplicate = assertThrows(BusinessException.class,
                () -> service.complete(receiptId, OPERATOR));
        assertEquals("PURCHASE_RECEIPT_409_COMPLETED", duplicate.getErrorCode().code());
        assertBalance(dimension, "5.2500", "5.2500", "0.0000");
        assertEquals(1, purchaseLedgerCount(completed.receiptNo(), dimension));
    }

    @Test
    void twoThreadsCompletingSameReceiptOnlyApplyInventoryOnce() throws Exception {
        Dimension dimension = createEnabledDimension();
        String receiptNo = "PR-CONCURRENT-" + SEQUENCE.incrementAndGet();
        long receiptId = createApproved(receiptNo, dimension, new BigDecimal("7.0000"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    try {
                        return service.complete(receiptId, OPERATOR).status().name();
                    } catch (BusinessException exception) {
                        return exception.getErrorCode().code();
                    }
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<String> results = futures.stream().map(future -> {
                try {
                    return future.get(20, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }).toList();

            assertTrue(results.contains("COMPLETED"));
            assertTrue(results.contains("PURCHASE_RECEIPT_409_COMPLETED"));
            assertBalance(dimension, "7.0000", "7.0000", "0.0000");
            assertEquals(1, purchaseLedgerCount(receiptNo, dimension));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void ledgerFailureRollsBackInventoryAndReceiptState() {
        Dimension dimension = createEnabledDimension();
        String receiptNo = "FAIL-LEDGER-" + SEQUENCE.incrementAndGet();
        long receiptId = createApproved(receiptNo, dimension, new BigDecimal("3.0000"));

        assertThrows(RuntimeException.class, () -> service.complete(receiptId, OPERATOR));

        assertEquals("APPROVED", statusOf(receiptId));
        assertEquals(0, balanceCount(dimension));
        assertEquals(0, dimensionLedgerCount(dimension));
    }

    @Test
    void statusFailureAfterLedgerInsertRollsBackLedgerAndInventory() {
        Dimension dimension = createEnabledDimension();
        String receiptNo = "FAIL-STATUS-" + SEQUENCE.incrementAndGet();
        long receiptId = createApproved(receiptNo, dimension, new BigDecimal("4.0000"));

        assertThrows(RuntimeException.class, () -> service.complete(receiptId, OPERATOR));

        assertEquals("APPROVED", statusOf(receiptId));
        assertEquals(0, balanceCount(dimension));
        assertEquals(0, dimensionLedgerCount(dimension));
    }

    private long createApproved(String receiptNo, Dimension dimension, BigDecimal quantity) {
        var draft = service.create(new PurchaseReceiptRequests.Create(
                receiptNo, dimension.warehouseId(), null,
                List.of(new PurchaseReceiptRequests.Line(
                        dimension.locationId(), dimension.skuId(), quantity))), OPERATOR);
        var submitted = service.submit(draft.id(),
                new PurchaseReceiptRequests.Transition(draft.version()), OPERATOR);
        var approved = service.approve(submitted.id(),
                new PurchaseReceiptRequests.Transition(submitted.version()), AUDITOR);
        return approved.id();
    }

    private Dimension createEnabledDimension() {
        int suffix = SEQUENCE.incrementAndGet();
        jdbc.update("INSERT INTO warehouse(code,name,status,version) VALUES (?,?, 'ENABLED',0)",
                "W" + suffix, "Warehouse " + suffix);
        Long warehouseId = jdbc.queryForObject("SELECT id FROM warehouse WHERE code=?", Long.class, "W" + suffix);
        jdbc.update("""
                INSERT INTO warehouse_location(warehouse_id,code,name,status,version)
                VALUES (?, ?, ?, 'ENABLED',0)
                """, warehouseId, "L" + suffix, "Location " + suffix);
        Long locationId = jdbc.queryForObject(
                "SELECT id FROM warehouse_location WHERE warehouse_id=? AND code=?",
                Long.class, warehouseId, "L" + suffix);
        jdbc.update("INSERT INTO sku(code,name,unit,status,version) VALUES (?,?,?,'ENABLED',0)",
                "SKU" + suffix, "SKU " + suffix, "PCS");
        Long skuId = jdbc.queryForObject("SELECT id FROM sku WHERE code=?", Long.class, "SKU" + suffix);
        return new Dimension(warehouseId, locationId, skuId);
    }

    private void assertBalance(Dimension dimension, String actual, String available, String frozen) {
        var values = jdbc.queryForMap("""
                SELECT actual_quantity, available_quantity, frozen_quantity
                FROM inventory_balance
                WHERE warehouse_id=? AND location_id=? AND sku_id=?
                """, dimension.warehouseId(), dimension.locationId(), dimension.skuId());
        assertEquals(new BigDecimal(actual), values.get("actual_quantity"));
        assertEquals(new BigDecimal(available), values.get("available_quantity"));
        assertEquals(new BigDecimal(frozen), values.get("frozen_quantity"));
    }

    private int purchaseLedgerCount(String receiptNo, Dimension dimension) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM inventory_ledger
                WHERE business_type='PURCHASE_RECEIPT' AND business_no=?
                  AND warehouse_id=? AND location_id=? AND sku_id=?
                """, Integer.class, receiptNo,
                dimension.warehouseId(), dimension.locationId(), dimension.skuId());
    }

    private int balanceCount(Dimension dimension) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM inventory_balance
                WHERE warehouse_id=? AND location_id=? AND sku_id=?
                """, Integer.class,
                dimension.warehouseId(), dimension.locationId(), dimension.skuId());
    }

    private int dimensionLedgerCount(Dimension dimension) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM inventory_ledger
                WHERE warehouse_id=? AND location_id=? AND sku_id=?
                """, Integer.class,
                dimension.warehouseId(), dimension.locationId(), dimension.skuId());
    }

    private String statusOf(long receiptId) {
        return jdbc.queryForObject("SELECT status FROM purchase_receipt WHERE id=?", String.class, receiptId);
    }

    static class MySqlInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            try (Connection connection = DriverManager.getConnection(ADMIN_URL, ADMIN_USER, ADMIN_PASSWORD);
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS " + DATABASE);
                statement.execute("CREATE DATABASE " + DATABASE
                        + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            } catch (Exception exception) {
                throw new IllegalStateException("Cannot prepare dedicated MySQL integration-test database", exception);
            }
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                    "spring.datasource.url=jdbc:mysql://localhost:3307/" + DATABASE
                            + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                            + "&allowPublicKeyRetrieval=true&useSSL=false",
                    "spring.datasource.username=" + ADMIN_USER,
                    "spring.datasource.password=" + ADMIN_PASSWORD,
                    "stockpilot.security.jwt-secret=01234567890123456789012345678901");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @ComponentScan(
            basePackages = "com.stockpilot",
            excludeFilters = {
                    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = StockPilotApplication.class),
                    @ComponentScan.Filter(type = FilterType.REGEX,
                            pattern = "com\\.stockpilot\\.security\\.TestProtectedController")
            })
    static class TestApplication {
    }

    private record Dimension(long warehouseId, long locationId, long skuId) {
    }
}
