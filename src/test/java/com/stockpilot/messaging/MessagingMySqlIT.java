package com.stockpilot.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.stockpilot.StockPilotApplication;
import com.stockpilot.acceptance.IntegrationTestInfrastructure;
import com.stockpilot.messaging.domain.BusinessEventNames;
import com.stockpilot.messaging.domain.CompletionBusinessEvent;
import com.stockpilot.messaging.service.CompletionEventConsumptionApplicationService;
import com.stockpilot.messaging.service.TransactionalOutboxApplicationService;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(classes = MessagingMySqlIT.TestApplication.class)
@ContextConfiguration(initializers = MessagingMySqlIT.MySqlInitializer.class)
class MessagingMySqlIT {
    private static final String DATABASE =
            IntegrationTestInfrastructure.databaseName("stockpilot_messaging_it");
    private static final String ADMIN_URL =
            System.getenv()
                    .getOrDefault(
                            "STOCKPILOT_IT_ADMIN_URL",
                            "jdbc:mysql://localhost:3307/?allowPublicKeyRetrieval=true&useSSL=false");
    private static final String ADMIN_USER =
            System.getenv().getOrDefault("STOCKPILOT_IT_ADMIN_USER", "root");
    private static final String ADMIN_PASSWORD =
            System.getenv().getOrDefault("STOCKPILOT_IT_ADMIN_PASSWORD", "root_dev_only");

    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;
    @Autowired TransactionalOutboxApplicationService outbox;
    @Autowired CompletionEventConsumptionApplicationService completionEventConsumption;

    @BeforeEach
    void cleanFacts() {
        jdbc.update("DELETE FROM async_message_trace");
        jdbc.update("DELETE FROM async_consumed_message");
        jdbc.update("DELETE FROM low_stock_alert");
        jdbc.update("DELETE FROM safety_stock_rule");
        jdbc.update("DELETE FROM async_outbox_message");
        jdbc.update("DELETE FROM inventory_ledger");
        jdbc.update("DELETE FROM inventory_balance");
        jdbc.update("DELETE FROM warehouse_location");
        jdbc.update("DELETE FROM sku");
        jdbc.update("DELETE FROM warehouse");
    }

    @AfterAll
    static void dropDatabase() throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(ADMIN_URL, ADMIN_USER, ADMIN_PASSWORD);
                Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + DATABASE);
        }
    }

    @Test
    void outboxParticipatesInBusinessTransactionAndRollbackLeavesNoSuccessEvent() {
        transactions.executeWithoutResult(
                status ->
                        outbox.enqueuePurchaseReceiptCompleted(
                                1L,
                                "PR-COMMIT",
                                2L,
                                List.of(new CompletionBusinessEvent.InventoryDimension(3L, 4L))));
        assertEquals(
                1,
                count("SELECT COUNT(*) FROM async_outbox_message WHERE business_no='PR-COMMIT'"));

        assertThrows(
                IllegalStateException.class,
                () ->
                        transactions.executeWithoutResult(
                                status -> {
                                    outbox.enqueueSalesOutboundCompleted(
                                            5L,
                                            "SO-ROLLBACK",
                                            2L,
                                            List.of(
                                                    new CompletionBusinessEvent.InventoryDimension(
                                                            3L, 4L)));
                                    throw new IllegalStateException("force business rollback");
                                }));
        assertEquals(
                0,
                count("SELECT COUNT(*) FROM async_outbox_message WHERE business_no='SO-ROLLBACK'"));
        assertEquals(
                0,
                count("SELECT COUNT(*) FROM async_message_trace WHERE business_no='SO-ROLLBACK'"));
    }

    @Test
    void duplicateConsumptionIsIdempotentAndLatestMysqlBalanceControlsAlertState() {
        Dimension dimension = createDimension("4.0000", "5.0000");
        CompletionBusinessEvent outbound =
                event(BusinessEventNames.SALES_OUTBOUND_COMPLETED, "SO-LOW", dimension);
        assertTrue(completionEventConsumption.handle(outbound));
        assertEquals(false, completionEventConsumption.handle(outbound));
        assertEquals(1, count("SELECT COUNT(*) FROM async_consumed_message"));
        assertEquals(
                "OPEN", jdbc.queryForObject("SELECT status FROM low_stock_alert", String.class));
        assertEquals(
                new BigDecimal("4.0000"),
                jdbc.queryForObject(
                        "SELECT available_quantity FROM low_stock_alert", BigDecimal.class));

        jdbc.update(
                "UPDATE inventory_balance SET actual_quantity=6,available_quantity=6 WHERE id=?",
                dimension.balanceId());
        CompletionBusinessEvent inbound =
                event(BusinessEventNames.PURCHASE_RECEIPT_COMPLETED, "PR-RECOVER", dimension);
        assertTrue(completionEventConsumption.handle(inbound));
        assertEquals(
                "RESOLVED",
                jdbc.queryForObject("SELECT status FROM low_stock_alert", String.class));
        assertEquals(1, count("SELECT COUNT(*) FROM async_message_trace WHERE stage='DUPLICATE'"));
    }

    @Test
    void partialConsumerFailureRollsBackSideEffectsAndSameMessageCanRetry() {
        Dimension first = createDimension("A", "2.0000", "5.0000");
        Dimension second = createDimension(first.warehouseId(), "B", "3.0000", "5.0000");
        jdbc.update("DELETE FROM inventory_balance WHERE id=?", second.balanceId());
        CompletionBusinessEvent event =
                new CompletionBusinessEvent(
                        UUID.randomUUID().toString(),
                        BusinessEventNames.SALES_OUTBOUND_COMPLETED,
                        1,
                        "SO-PARTIAL-ROLLBACK",
                        Instant.now(),
                        new CompletionBusinessEvent.CompletionData(
                                1L,
                                first.warehouseId(),
                                List.of(
                                        new CompletionBusinessEvent.InventoryDimension(
                                                first.locationId(), first.skuId()),
                                        new CompletionBusinessEvent.InventoryDimension(
                                                second.locationId(), second.skuId()))));

        assertThrows(IllegalStateException.class, () -> completionEventConsumption.handle(event));
        assertEquals(0, count("SELECT COUNT(*) FROM low_stock_alert"));
        assertEquals(0, count("SELECT COUNT(*) FROM async_consumed_message"));
        assertEquals(
                0,
                count(
                        "SELECT COUNT(*) FROM async_message_trace WHERE message_id='"
                                + event.messageId()
                                + "'"));

        jdbc.update(
                "INSERT INTO inventory_balance(warehouse_id,location_id,sku_id,actual_quantity,available_quantity,frozen_quantity,version) VALUES (?,?,?,?,?,0,0)",
                second.warehouseId(),
                second.locationId(),
                second.skuId(),
                new BigDecimal("3.0000"),
                new BigDecimal("3.0000"));
        assertTrue(completionEventConsumption.handle(event));
        assertEquals(2, count("SELECT COUNT(*) FROM low_stock_alert WHERE status='OPEN'"));
        assertEquals(
                1,
                count(
                        "SELECT COUNT(*) FROM async_consumed_message WHERE message_id='"
                                + event.messageId()
                                + "'"));
    }

    private CompletionBusinessEvent event(
            String eventName, String businessNo, Dimension dimension) {
        return new CompletionBusinessEvent(
                UUID.randomUUID().toString(),
                eventName,
                1,
                businessNo,
                Instant.now(),
                new CompletionBusinessEvent.CompletionData(
                        1L,
                        dimension.warehouseId(),
                        List.of(
                                new CompletionBusinessEvent.InventoryDimension(
                                        dimension.locationId(), dimension.skuId()))));
    }

    private Dimension createDimension(String available, String threshold) {
        return createDimension("", available, threshold);
    }

    private Dimension createDimension(String suffix, String available, String threshold) {
        String warehouseCode = "WMQ" + suffix;
        String locationCode = "LMQ" + suffix;
        String skuCode = "SKUMQ" + suffix;
        jdbc.update(
                "INSERT INTO warehouse(code,name,status,version) VALUES (?,'MQ Warehouse','ENABLED',0)",
                warehouseCode);
        long warehouse =
                jdbc.queryForObject(
                        "SELECT id FROM warehouse WHERE code=?", Long.class, warehouseCode);
        jdbc.update(
                "INSERT INTO warehouse_location(warehouse_id,code,name,status,version) VALUES (?,?,'MQ Location','ENABLED',0)",
                warehouse,
                locationCode);
        long location =
                jdbc.queryForObject(
                        "SELECT id FROM warehouse_location WHERE code=?", Long.class, locationCode);
        jdbc.update(
                "INSERT INTO sku(code,name,unit,status,version) VALUES (?,'MQ SKU','PCS','ENABLED',0)",
                skuCode);
        long sku = jdbc.queryForObject("SELECT id FROM sku WHERE code=?", Long.class, skuCode);
        jdbc.update(
                "INSERT INTO inventory_balance(warehouse_id,location_id,sku_id,actual_quantity,available_quantity,frozen_quantity,version) VALUES (?,?,?,?,?,0,0)",
                warehouse,
                location,
                sku,
                new BigDecimal(available),
                new BigDecimal(available));
        long balance =
                jdbc.queryForObject(
                        "SELECT id FROM inventory_balance WHERE warehouse_id=? AND location_id=? AND sku_id=?",
                        Long.class,
                        warehouse,
                        location,
                        sku);
        jdbc.update(
                "INSERT INTO safety_stock_rule(warehouse_id,location_id,sku_id,threshold_quantity,status,version) VALUES (?,?,?,?,'ENABLED',0)",
                warehouse,
                location,
                sku,
                new BigDecimal(threshold));
        return new Dimension(warehouse, location, sku, balance);
    }

    private Dimension createDimension(
            long warehouse, String suffix, String available, String threshold) {
        String locationCode = "LMQ" + suffix;
        String skuCode = "SKUMQ" + suffix;
        jdbc.update(
                "INSERT INTO warehouse_location(warehouse_id,code,name,status,version) VALUES (?,?,'MQ Location','ENABLED',0)",
                warehouse,
                locationCode);
        long location =
                jdbc.queryForObject(
                        "SELECT id FROM warehouse_location WHERE code=?", Long.class, locationCode);
        jdbc.update(
                "INSERT INTO sku(code,name,unit,status,version) VALUES (?,'MQ SKU','PCS','ENABLED',0)",
                skuCode);
        long sku = jdbc.queryForObject("SELECT id FROM sku WHERE code=?", Long.class, skuCode);
        jdbc.update(
                "INSERT INTO inventory_balance(warehouse_id,location_id,sku_id,actual_quantity,available_quantity,frozen_quantity,version) VALUES (?,?,?,?,?,0,0)",
                warehouse,
                location,
                sku,
                new BigDecimal(available),
                new BigDecimal(available));
        long balance =
                jdbc.queryForObject(
                        "SELECT id FROM inventory_balance WHERE warehouse_id=? AND location_id=? AND sku_id=?",
                        Long.class,
                        warehouse,
                        location,
                        sku);
        jdbc.update(
                "INSERT INTO safety_stock_rule(warehouse_id,location_id,sku_id,threshold_quantity,status,version) VALUES (?,?,?,?,'ENABLED',0)",
                warehouse,
                location,
                sku,
                new BigDecimal(threshold));
        return new Dimension(warehouse, location, sku, balance);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    static class MySqlInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            IntegrationTestInfrastructure.isolate(context, DATABASE);
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
                        "Cannot prepare messaging integration-test database", exception);
            }
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                    context,
                    "spring.datasource.url="
                            + IntegrationTestInfrastructure.databaseUrl(ADMIN_URL, DATABASE),
                    "spring.datasource.username=" + ADMIN_USER,
                    "spring.datasource.password=" + ADMIN_PASSWORD,
                    "stockpilot.messaging.enabled=false",
                    "stockpilot.security.jwt-secret=01234567890123456789012345678901");
        }
    }

    @SpringBootConfiguration
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

    private record Dimension(long warehouseId, long locationId, long skuId, long balanceId) {}
}
