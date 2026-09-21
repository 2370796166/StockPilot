package com.stockpilot.sales;

import static org.junit.jupiter.api.Assertions.*;

import com.stockpilot.StockPilotApplication;
import com.stockpilot.sales.domain.SalesOutboundStatus;
import com.stockpilot.sales.request.SalesOutboundRequests;
import com.stockpilot.sales.service.SalesOutboundApplicationService;
import com.stockpilot.security.auth.StockPilotPrincipal;
import com.stockpilot.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest(classes = SalesOutboundMySqlIT.TestApplication.class)
@ContextConfiguration(initializers = SalesOutboundMySqlIT.MySqlInitializer.class)
class SalesOutboundMySqlIT {
    private static final String DATABASE = "stockpilot_outbound_it";
    private static final String ADMIN_URL =
            System.getenv()
                    .getOrDefault(
                            "STOCKPILOT_IT_ADMIN_URL",
                            "jdbc:mysql://localhost:3307/?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false");
    private static final String ADMIN_USER =
            System.getenv().getOrDefault("STOCKPILOT_IT_ADMIN_USER", "root");
    private static final String ADMIN_PASSWORD =
            System.getenv().getOrDefault("STOCKPILOT_IT_ADMIN_PASSWORD", "root_dev_only");
    private static final StockPilotPrincipal OPERATOR =
            new StockPilotPrincipal(101L, "it-operator");
    private static final StockPilotPrincipal AUDITOR = new StockPilotPrincipal(102L, "it-auditor");
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired private SalesOutboundApplicationService service;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_outbound_ledger");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_outbound_status");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_second_ship_ledger");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_second_release_ledger");
        jdbc.update("DELETE FROM inventory_ledger");
        jdbc.update("DELETE FROM inventory_balance");
        jdbc.update("DELETE FROM sales_outbound_line");
        jdbc.update("DELETE FROM sales_outbound_order");
        jdbc.update("DELETE FROM purchase_receipt_line");
        jdbc.update("DELETE FROM purchase_receipt");
        jdbc.update("DELETE FROM sku");
        jdbc.update("DELETE FROM warehouse_location");
        jdbc.update("DELETE FROM warehouse");
    }

    @AfterAll
    static void dropDedicatedDatabase() throws Exception {
        try (Connection connection =
                        DriverManager.getConnection(ADMIN_URL, ADMIN_USER, ADMIN_PASSWORD);
                Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + DATABASE);
        }
    }

    @Test
    void createEditReserveApproveCompleteAndRejectDuplicateOrCancellation() {
        Dimension dimension = createEnabledDimension("100.0000");
        var draft = createDraft("SO-FULL-", dimension, "10.0000");
        var edited =
                service.update(
                        draft.id(),
                        new SalesOutboundRequests.Update(
                                draft.version(),
                                dimension.warehouseId(),
                                "edited",
                                List.of(
                                        new SalesOutboundRequests.Line(
                                                dimension.locationId(),
                                                dimension.skuId(),
                                                new BigDecimal("12.0000")))),
                        OPERATOR);
        assertEquals(SalesOutboundStatus.DRAFT, edited.status());
        assertEquals("edited", edited.remark());

        var reserved =
                service.reserve(
                        edited.id(),
                        new SalesOutboundRequests.Transition(edited.version()),
                        OPERATOR);
        assertEquals(SalesOutboundStatus.RESERVED, reserved.status());
        assertBalance(dimension, "100.0000", "88.0000", "12.0000");
        assertEquals(1, ledgerCount(reserved.outboundNo(), "OUTBOUND_FREEZE"));
        assertLedgerChanges(
                reserved.outboundNo(), "OUTBOUND_FREEZE", "0.0000", "-12.0000", "12.0000");

        BusinessException duplicateReserve =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.reserve(
                                        edited.id(),
                                        new SalesOutboundRequests.Transition(edited.version()),
                                        OPERATOR));
        assertEquals("SALES_OUTBOUND_409_RESERVED", duplicateReserve.getErrorCode().code());

        var approved =
                service.approve(
                        reserved.id(),
                        new SalesOutboundRequests.Transition(reserved.version()),
                        AUDITOR);
        var completed = service.complete(approved.id(), OPERATOR);
        assertEquals(SalesOutboundStatus.COMPLETED, completed.status());
        assertBalance(dimension, "88.0000", "88.0000", "0.0000");
        assertEquals(1, ledgerCount(completed.outboundNo(), "OUTBOUND_SHIP"));
        assertLedgerChanges(
                completed.outboundNo(), "OUTBOUND_SHIP", "-12.0000", "0.0000", "-12.0000");
        assertEquals(
                "SALES_OUTBOUND_409_COMPLETED",
                assertThrows(
                                BusinessException.class,
                                () -> service.complete(completed.id(), OPERATOR))
                        .getErrorCode()
                        .code());
        assertEquals(
                "SALES_OUTBOUND_409_COMPLETED",
                assertThrows(
                                BusinessException.class,
                                () -> service.cancel(completed.id(), OPERATOR))
                        .getErrorCode()
                        .code());
        assertEquals(2, allLedgerCount(completed.outboundNo()));
    }

    @Test
    void cancelReservedOrApprovedReleasesExactlyOnce() {
        Dimension dimension = createEnabledDimension("100.0000");
        var first = reserve(createDraft("SO-CANCEL-R-", dimension, "15.0000"));
        var cancelled = service.cancel(first.id(), OPERATOR);
        assertEquals(SalesOutboundStatus.CANCELLED, cancelled.status());
        assertBalance(dimension, "100.0000", "100.0000", "0.0000");
        assertEquals(1, ledgerCount(cancelled.outboundNo(), "OUTBOUND_RELEASE"));
        assertLedgerChanges(
                cancelled.outboundNo(), "OUTBOUND_RELEASE", "0.0000", "15.0000", "-15.0000");
        assertEquals(
                "SALES_OUTBOUND_409_CANCELLED",
                assertThrows(
                                BusinessException.class,
                                () -> service.cancel(cancelled.id(), OPERATOR))
                        .getErrorCode()
                        .code());

        var second = reserve(createDraft("SO-CANCEL-A-", dimension, "20.0000"));
        var approved =
                service.approve(
                        second.id(),
                        new SalesOutboundRequests.Transition(second.version()),
                        AUDITOR);
        service.cancel(approved.id(), OPERATOR);
        assertBalance(dimension, "100.0000", "100.0000", "0.0000");
        assertEquals(1, ledgerCount(approved.outboundNo(), "OUTBOUND_RELEASE"));
    }

    @Test
    void twentyRealThreadsReserveAtMostAvailableInventoryWithoutResidue() throws Exception {
        assertTrue(AopUtils.isAopProxy(service));
        assertFalse(
                TransactionSynchronizationManager.isActualTransactionActive(),
                "集成测试线程不能用共享事务包裹并发请求");
        Dimension dimension = createEnabledDimension("100.0000");
        List<Long> orderIds = new ArrayList<>();
        List<String> orderNos = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            var draft = createDraft("SO-RACE-", dimension, "10.0000");
            orderIds.add(draft.id());
            orderNos.add(draft.outboundNo());
        }
        CountDownLatch ready = new CountDownLatch(20);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(20);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (long id : orderIds) {
                futures.add(
                        executor.submit(
                                () -> {
                                    ready.countDown();
                                    assertTrue(start.await(10, TimeUnit.SECONDS));
                                    try {
                                        return service.reserve(
                                                        id,
                                                        new SalesOutboundRequests.Transition(0),
                                                        OPERATOR)
                                                .status()
                                                .name();
                                    } catch (BusinessException exception) {
                                        return exception.getErrorCode().code();
                                    }
                                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<String> results = new ArrayList<>();
            for (Future<String> future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            assertEquals(10, results.stream().filter("RESERVED"::equals).count());
            assertEquals(
                    10,
                    results.stream()
                            .filter("INVENTORY_409_INSUFFICIENT_AVAILABLE"::equals)
                            .count());
        } finally {
            executor.shutdownNow();
        }
        assertBalance(dimension, "100.0000", "0.0000", "100.0000");
        assertEquals(
                10,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM inventory_ledger WHERE business_type='OUTBOUND_FREEZE'",
                        Integer.class));
        assertEquals(
                10,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sales_outbound_order WHERE status='RESERVED'",
                        Integer.class));
        assertEquals(
                10,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sales_outbound_order WHERE status='DRAFT'",
                        Integer.class));
        for (String no : orderNos) assertTrue(allLedgerCount(no) == 0 || allLedgerCount(no) == 1);
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM inventory_balance WHERE actual_quantity<0 OR available_quantity<0 OR frozen_quantity<0",
                        Integer.class));
        assertConcurrentFreezeLedgerChain(dimension);
    }

    @Test
    void multiLineReserveAndCompleteFailuresRollBackEveryEarlierLine() {
        Dimension first = createEnabledDimension("100.0000");
        Dimension insufficient = createAdditionalDimension(first.warehouseId(), "5.0000");
        var reserveFailure =
                createDraft(
                        "SO-MULTI-RESERVE-",
                        first.warehouseId(),
                        List.of(
                                new SalesOutboundRequests.Line(
                                        first.locationId(),
                                        first.skuId(),
                                        new BigDecimal("10.0000")),
                                new SalesOutboundRequests.Line(
                                        insufficient.locationId(),
                                        insufficient.skuId(),
                                        new BigDecimal("10.0000"))));

        assertEquals(
                "INVENTORY_409_INSUFFICIENT_AVAILABLE",
                assertThrows(BusinessException.class, () -> reserve(reserveFailure))
                        .getErrorCode()
                        .code());
        assertEquals("DRAFT", statusOf(reserveFailure.id()));
        assertBalance(first, "100.0000", "100.0000", "0.0000");
        assertBalance(insufficient, "5.0000", "5.0000", "0.0000");
        assertEquals(0, allLedgerCount(reserveFailure.outboundNo()));

        Dimension second = createAdditionalDimension(first.warehouseId(), "100.0000");
        var draft =
                createDraft(
                        "SO-MULTI-SHIP-",
                        first.warehouseId(),
                        List.of(
                                new SalesOutboundRequests.Line(
                                        first.locationId(),
                                        first.skuId(),
                                        new BigDecimal("10.0000")),
                                new SalesOutboundRequests.Line(
                                        second.locationId(),
                                        second.skuId(),
                                        new BigDecimal("10.0000"))));
        var reserved = reserve(draft);
        var approved =
                service.approve(
                        reserved.id(),
                        new SalesOutboundRequests.Transition(reserved.version()),
                        AUDITOR);
        jdbc.execute(
                ("""
                CREATE TRIGGER fail_second_ship_ledger BEFORE INSERT ON inventory_ledger FOR EACH ROW
                BEGIN IF NEW.business_type='OUTBOUND_SHIP' AND NEW.sku_id=%d
                THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced second ship ledger failure'; END IF; END
                """)
                        .formatted(second.skuId()));

        assertThrows(RuntimeException.class, () -> service.complete(approved.id(), OPERATOR));
        assertEquals("APPROVED", statusOf(approved.id()));
        assertBalance(first, "100.0000", "90.0000", "10.0000");
        assertBalance(second, "100.0000", "90.0000", "10.0000");
        assertEquals(2, ledgerCount(approved.outboundNo(), "OUTBOUND_FREEZE"));
        assertEquals(0, ledgerCount(approved.outboundNo(), "OUTBOUND_SHIP"));

        jdbc.execute(
                ("""
                CREATE TRIGGER fail_second_release_ledger BEFORE INSERT ON inventory_ledger FOR EACH ROW
                BEGIN IF NEW.business_type='OUTBOUND_RELEASE' AND NEW.sku_id=%d
                THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced second release ledger failure'; END IF; END
                """)
                        .formatted(second.skuId()));
        assertThrows(RuntimeException.class, () -> service.cancel(approved.id(), OPERATOR));
        assertEquals("APPROVED", statusOf(approved.id()));
        assertBalance(first, "100.0000", "90.0000", "10.0000");
        assertBalance(second, "100.0000", "90.0000", "10.0000");
        assertEquals(0, ledgerCount(approved.outboundNo(), "OUTBOUND_RELEASE"));
    }

    @Test
    void concurrentCancelAndCompleteCannotBothSucceed() throws Exception {
        Dimension dimension = createEnabledDimension("100.0000");
        var reserved = reserve(createDraft("SO-CANCEL-SHIP-RACE-", dimension, "25.0000"));
        var approved =
                service.approve(
                        reserved.id(),
                        new SalesOutboundRequests.Transition(reserved.version()),
                        AUDITOR);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> complete =
                    executor.submit(
                            () ->
                                    raceAction(
                                            ready,
                                            start,
                                            () ->
                                                    service.complete(approved.id(), OPERATOR)
                                                            .status()
                                                            .name()));
            Future<String> cancel =
                    executor.submit(
                            () ->
                                    raceAction(
                                            ready,
                                            start,
                                            () ->
                                                    service.cancel(approved.id(), OPERATOR)
                                                            .status()
                                                            .name()));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<String> results =
                    List.of(complete.get(20, TimeUnit.SECONDS), cancel.get(20, TimeUnit.SECONDS));
            assertEquals(
                    1,
                    results.stream()
                            .filter(value -> value.equals("COMPLETED") || value.equals("CANCELLED"))
                            .count());
        } finally {
            executor.shutdownNow();
        }

        String status = statusOf(approved.id());
        assertTrue(status.equals("COMPLETED") || status.equals("CANCELLED"));
        assertEquals(
                1,
                ledgerCount(approved.outboundNo(), "OUTBOUND_SHIP")
                        + ledgerCount(approved.outboundNo(), "OUTBOUND_RELEASE"));
        if (status.equals("COMPLETED")) assertBalance(dimension, "75.0000", "75.0000", "0.0000");
        else assertBalance(dimension, "100.0000", "100.0000", "0.0000");
    }

    @Test
    void reversedInputLineOrderStillUsesStableInventoryLockOrder() throws Exception {
        Dimension first = createEnabledDimension("100.0000");
        Dimension second = createAdditionalDimension(first.warehouseId(), "100.0000");
        var one =
                createDraft(
                        "SO-LOCK-ORDER-A-",
                        first.warehouseId(),
                        List.of(
                                new SalesOutboundRequests.Line(
                                        first.locationId(),
                                        first.skuId(),
                                        new BigDecimal("10.0000")),
                                new SalesOutboundRequests.Line(
                                        second.locationId(),
                                        second.skuId(),
                                        new BigDecimal("10.0000"))));
        var two =
                createDraft(
                        "SO-LOCK-ORDER-B-",
                        first.warehouseId(),
                        List.of(
                                new SalesOutboundRequests.Line(
                                        second.locationId(),
                                        second.skuId(),
                                        new BigDecimal("10.0000")),
                                new SalesOutboundRequests.Line(
                                        first.locationId(),
                                        first.skuId(),
                                        new BigDecimal("10.0000"))));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> a =
                    executor.submit(
                            () -> raceAction(ready, start, () -> reserve(one).status().name()));
            Future<String> b =
                    executor.submit(
                            () -> raceAction(ready, start, () -> reserve(two).status().name()));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(
                    List.of("RESERVED", "RESERVED"),
                    List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)));
        } finally {
            executor.shutdownNow();
        }
        assertBalance(first, "100.0000", "80.0000", "20.0000");
        assertBalance(second, "100.0000", "80.0000", "20.0000");
        assertEquals(
                4,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM inventory_ledger WHERE business_type='OUTBOUND_FREEZE'",
                        Integer.class));
    }

    @Test
    void ledgerOrStatusFailureRollsBackOrderInventoryAndLedger() {
        Dimension dimension = createEnabledDimension("100.0000");
        jdbc.execute(
                """
                CREATE TRIGGER fail_outbound_ledger BEFORE INSERT ON inventory_ledger FOR EACH ROW
                BEGIN IF NEW.business_type='OUTBOUND_FREEZE' AND NEW.business_no LIKE 'FAIL-LEDGER-%'
                THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced ledger failure'; END IF; END
                """);
        var ledgerFailure = createDraft("FAIL-LEDGER-", dimension, "10.0000");
        assertThrows(RuntimeException.class, () -> reserve(ledgerFailure));
        assertRollback(ledgerFailure.id(), ledgerFailure.outboundNo(), dimension);

        jdbc.execute(
                """
                CREATE TRIGGER fail_outbound_status BEFORE UPDATE ON sales_outbound_order FOR EACH ROW
                BEGIN IF NEW.status='RESERVED' AND NEW.outbound_no LIKE 'FAIL-STATUS-%'
                THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced status failure'; END IF; END
                """);
        var statusFailure = createDraft("FAIL-STATUS-", dimension, "10.0000");
        assertThrows(RuntimeException.class, () -> reserve(statusFailure));
        assertRollback(statusFailure.id(), statusFailure.outboundNo(), dimension);
    }

    @Test
    void rejectsInvalidQuantityMissingOrDisabledMasterDataAndInsufficientStock() {
        Dimension dimension = createEnabledDimension("5.0000");
        assertEquals(
                "SALES_OUTBOUND_400_LINE",
                assertThrows(
                                BusinessException.class,
                                () -> createDraft("SO-ZERO-", dimension, "0.0000"))
                        .getErrorCode()
                        .code());
        Dimension missingSku =
                new Dimension(dimension.warehouseId(), dimension.locationId(), 999999L);
        assertThrows(
                BusinessException.class, () -> createDraft("SO-MISSING-", missingSku, "1.0000"));
        var tooLarge = createDraft("SO-SHORT-", dimension, "10.0000");
        assertEquals(
                "INVENTORY_409_INSUFFICIENT_AVAILABLE",
                assertThrows(BusinessException.class, () -> reserve(tooLarge))
                        .getErrorCode()
                        .code());
        assertEquals("DRAFT", statusOf(tooLarge.id()));
        assertEquals(0, allLedgerCount(tooLarge.outboundNo()));
        var disabledBeforeFreeze = createDraft("SO-DISABLED-", dimension, "1.0000");
        jdbc.update("UPDATE sku SET status='DISABLED' WHERE id=?", dimension.skuId());
        assertThrows(BusinessException.class, () -> reserve(disabledBeforeFreeze));
        assertEquals("DRAFT", statusOf(disabledBeforeFreeze.id()));
        assertBalance(dimension, "5.0000", "5.0000", "0.0000");
        assertEquals(0, allLedgerCount(disabledBeforeFreeze.outboundNo()));
    }

    @Test
    void rejectsIllegalStateTransitionsAndEditingAfterReservation() {
        Dimension dimension = createEnabledDimension("100.0000");
        var draft = createDraft("SO-STATE-", dimension, "10.0000");
        assertEquals(
                "SALES_OUTBOUND_409_STATE",
                assertThrows(
                                BusinessException.class,
                                () ->
                                        service.approve(
                                                draft.id(),
                                                new SalesOutboundRequests.Transition(
                                                        draft.version()),
                                                AUDITOR))
                        .getErrorCode()
                        .code());
        var reserved = reserve(draft);
        assertEquals(
                "SALES_OUTBOUND_409_STATE",
                assertThrows(
                                BusinessException.class,
                                () ->
                                        service.update(
                                                reserved.id(),
                                                new SalesOutboundRequests.Update(
                                                        reserved.version(),
                                                        dimension.warehouseId(),
                                                        null,
                                                        List.of(
                                                                new SalesOutboundRequests.Line(
                                                                        dimension.locationId(),
                                                                        dimension.skuId(),
                                                                        BigDecimal.ONE))),
                                                OPERATOR))
                        .getErrorCode()
                        .code());
        assertEquals(
                "SALES_OUTBOUND_409_STATE",
                assertThrows(
                                BusinessException.class,
                                () -> service.complete(reserved.id(), OPERATOR))
                        .getErrorCode()
                        .code());
        assertBalance(dimension, "100.0000", "90.0000", "10.0000");
    }

    private com.stockpilot.sales.vo.SalesOutboundVO createDraft(
            String prefix, Dimension d, String quantity) {
        return createDraft(
                prefix,
                d.warehouseId(),
                List.of(
                        new SalesOutboundRequests.Line(
                                d.locationId(), d.skuId(), new BigDecimal(quantity))));
    }

    private com.stockpilot.sales.vo.SalesOutboundVO createDraft(
            String prefix, long warehouseId, List<SalesOutboundRequests.Line> lines) {
        return service.create(
                new SalesOutboundRequests.Create(
                        prefix + SEQUENCE.incrementAndGet(), warehouseId, null, lines),
                OPERATOR);
    }

    private com.stockpilot.sales.vo.SalesOutboundVO reserve(
            com.stockpilot.sales.vo.SalesOutboundVO value) {
        return service.reserve(
                value.id(), new SalesOutboundRequests.Transition(value.version()), OPERATOR);
    }

    private Dimension createEnabledDimension(String quantity) {
        int n = SEQUENCE.incrementAndGet();
        jdbc.update(
                "INSERT INTO warehouse(code,name,status,version) VALUES (?,?, 'ENABLED',0)",
                "W" + n,
                "Warehouse " + n);
        long warehouse =
                jdbc.queryForObject("SELECT id FROM warehouse WHERE code=?", Long.class, "W" + n);
        jdbc.update(
                "INSERT INTO warehouse_location(warehouse_id,code,name,status,version) VALUES (?,?,?,'ENABLED',0)",
                warehouse,
                "L" + n,
                "Location " + n);
        long location =
                jdbc.queryForObject(
                        "SELECT id FROM warehouse_location WHERE warehouse_id=? AND code=?",
                        Long.class,
                        warehouse,
                        "L" + n);
        jdbc.update(
                "INSERT INTO sku(code,name,unit,status,version) VALUES (?,?,?,'ENABLED',0)",
                "SKU" + n,
                "SKU " + n,
                "PCS");
        long sku = jdbc.queryForObject("SELECT id FROM sku WHERE code=?", Long.class, "SKU" + n);
        jdbc.update(
                "INSERT INTO inventory_balance(warehouse_id,location_id,sku_id,actual_quantity,available_quantity,frozen_quantity,version) VALUES (?,?,?,?,?,0,0)",
                warehouse,
                location,
                sku,
                new BigDecimal(quantity),
                new BigDecimal(quantity));
        return new Dimension(warehouse, location, sku);
    }

    private Dimension createAdditionalDimension(long warehouse, String quantity) {
        int n = SEQUENCE.incrementAndGet();
        jdbc.update(
                "INSERT INTO warehouse_location(warehouse_id,code,name,status,version) VALUES (?,?,?,'ENABLED',0)",
                warehouse,
                "L" + n,
                "Location " + n);
        long location =
                jdbc.queryForObject(
                        "SELECT id FROM warehouse_location WHERE warehouse_id=? AND code=?",
                        Long.class,
                        warehouse,
                        "L" + n);
        jdbc.update(
                "INSERT INTO sku(code,name,unit,status,version) VALUES (?,?,?,'ENABLED',0)",
                "SKU" + n,
                "SKU " + n,
                "PCS");
        long sku = jdbc.queryForObject("SELECT id FROM sku WHERE code=?", Long.class, "SKU" + n);
        jdbc.update(
                "INSERT INTO inventory_balance(warehouse_id,location_id,sku_id,actual_quantity,available_quantity,frozen_quantity,version) VALUES (?,?,?,?,?,0,0)",
                warehouse,
                location,
                sku,
                new BigDecimal(quantity),
                new BigDecimal(quantity));
        return new Dimension(warehouse, location, sku);
    }

    private void assertBalance(Dimension d, String actual, String available, String frozen) {
        Map<String, Object> row =
                jdbc.queryForMap(
                        "SELECT actual_quantity,available_quantity,frozen_quantity FROM inventory_balance WHERE warehouse_id=? AND location_id=? AND sku_id=?",
                        d.warehouseId(),
                        d.locationId(),
                        d.skuId());
        assertEquals(new BigDecimal(actual), row.get("actual_quantity"));
        assertEquals(new BigDecimal(available), row.get("available_quantity"));
        assertEquals(new BigDecimal(frozen), row.get("frozen_quantity"));
    }

    private void assertRollback(long id, String no, Dimension d) {
        assertEquals("DRAFT", statusOf(id));
        assertBalance(d, "100.0000", "100.0000", "0.0000");
        assertEquals(0, allLedgerCount(no));
    }

    private String statusOf(long id) {
        return jdbc.queryForObject(
                "SELECT status FROM sales_outbound_order WHERE id=?", String.class, id);
    }

    private int ledgerCount(String no, String type) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_ledger WHERE business_no=? AND business_type=?",
                Integer.class,
                no,
                type);
    }

    private int allLedgerCount(String no) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_ledger WHERE business_no=?", Integer.class, no);
    }

    private void assertLedgerChanges(
            String no, String type, String actual, String available, String frozen) {
        Map<String, Object> row =
                jdbc.queryForMap(
                        "SELECT change_actual_quantity,change_available_quantity,change_frozen_quantity FROM inventory_ledger WHERE business_no=? AND business_type=?",
                        no,
                        type);
        assertEquals(new BigDecimal(actual), row.get("change_actual_quantity"));
        assertEquals(new BigDecimal(available), row.get("change_available_quantity"));
        assertEquals(new BigDecimal(frozen), row.get("change_frozen_quantity"));
    }

    private void assertConcurrentFreezeLedgerChain(Dimension dimension) {
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        """
                SELECT before_actual_quantity, before_available_quantity, before_frozen_quantity,
                       change_actual_quantity, change_available_quantity, change_frozen_quantity,
                       after_actual_quantity, after_available_quantity, after_frozen_quantity,
                       balance_version_before, balance_version_after
                FROM inventory_ledger
                WHERE business_type='OUTBOUND_FREEZE' AND warehouse_id=? AND location_id=? AND sku_id=?
                ORDER BY balance_version_after
                """,
                        dimension.warehouseId(),
                        dimension.locationId(),
                        dimension.skuId());
        assertEquals(10, rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            assertEquals(i, ((Number) row.get("balance_version_before")).intValue());
            assertEquals(i + 1, ((Number) row.get("balance_version_after")).intValue());
            assertEquals(new BigDecimal("100.0000"), row.get("before_actual_quantity"));
            assertEquals(
                    new BigDecimal(100 - i * 10 + ".0000"), row.get("before_available_quantity"));
            assertEquals(new BigDecimal(i * 10 + ".0000"), row.get("before_frozen_quantity"));
            assertEquals(new BigDecimal("0.0000"), row.get("change_actual_quantity"));
            assertEquals(new BigDecimal("-10.0000"), row.get("change_available_quantity"));
            assertEquals(new BigDecimal("10.0000"), row.get("change_frozen_quantity"));
            assertEquals(new BigDecimal("100.0000"), row.get("after_actual_quantity"));
            assertEquals(
                    new BigDecimal(90 - i * 10 + ".0000"), row.get("after_available_quantity"));
            assertEquals(new BigDecimal((i + 1) * 10 + ".0000"), row.get("after_frozen_quantity"));
        }
    }

    private String raceAction(CountDownLatch ready, CountDownLatch start, Callable<String> action)
            throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        try {
            return action.call();
        } catch (BusinessException exception) {
            return exception.getErrorCode().code();
        }
    }

    static class MySqlInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        public void initialize(ConfigurableApplicationContext context) {
            try (Connection c = DriverManager.getConnection(ADMIN_URL, ADMIN_USER, ADMIN_PASSWORD);
                    Statement s = c.createStatement()) {
                s.execute("DROP DATABASE IF EXISTS " + DATABASE);
                s.execute(
                        "CREATE DATABASE "
                                + DATABASE
                                + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Cannot prepare outbound integration-test database", e);
            }
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                    context,
                    "spring.datasource.url=jdbc:mysql://localhost:3307/"
                            + DATABASE
                            + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false",
                    "spring.datasource.username=" + ADMIN_USER,
                    "spring.datasource.password=" + ADMIN_PASSWORD,
                    "spring.datasource.hikari.maximum-pool-size=25",
                    "stockpilot.security.jwt-secret=01234567890123456789012345678901");
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

    private record Dimension(long warehouseId, long locationId, long skuId) {}
}
