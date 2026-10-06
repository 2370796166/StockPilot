package com.stockpilot.inventory.count;

import static org.junit.jupiter.api.Assertions.*;

import com.stockpilot.StockPilotApplication;
import com.stockpilot.acceptance.IntegrationTestInfrastructure;
import com.stockpilot.inventory.count.domain.InventoryCountStatus;
import com.stockpilot.inventory.count.request.InventoryCountRequests;
import com.stockpilot.inventory.count.service.InventoryCountApplicationService;
import com.stockpilot.inventory.count.vo.InventoryCountVO;
import com.stockpilot.inventory.domain.*;
import com.stockpilot.inventory.service.*;
import com.stockpilot.shared.auth.AuthenticatedActor;
import com.stockpilot.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.support.TestPropertySourceUtils;

@SpringBootTest(classes = InventoryCountMySqlIT.TestApplication.class)
@ContextConfiguration(initializers = InventoryCountMySqlIT.MySqlInitializer.class)
class InventoryCountMySqlIT {
    private static final String DATABASE =
            IntegrationTestInfrastructure.databaseName("stockpilot_count_it");
    private static final String ADMIN_URL =
            System.getenv()
                    .getOrDefault(
                            "STOCKPILOT_IT_ADMIN_URL",
                            "jdbc:mysql://localhost:3307/?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false");
    private static final String ADMIN_USER =
            System.getenv().getOrDefault("STOCKPILOT_IT_ADMIN_USER", "root");
    private static final String ADMIN_PASSWORD =
            System.getenv().getOrDefault("STOCKPILOT_IT_ADMIN_PASSWORD", "root_dev_only");
    private static final AuthenticatedActor OPERATOR =
            new AuthenticatedActor(301L, "count-operator");
    private static final AuthenticatedActor AUDITOR = new AuthenticatedActor(302L, "count-auditor");
    private static final AtomicInteger SEQ = new AtomicInteger();
    @Autowired InventoryCountApplicationService service;
    @Autowired InventoryMutationApplicationService inventory;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_count_ledger");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_count_cancel");
        jdbc.update("DELETE FROM inventory_count_scope_lock");
        jdbc.update("DELETE FROM inventory_count_line");
        jdbc.update("DELETE FROM inventory_count_order");
        jdbc.update("DELETE FROM stock_transfer_transit");
        jdbc.update("DELETE FROM stock_transfer_line");
        jdbc.update("DELETE FROM stock_transfer_order");
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
    static void dropDatabase() throws Exception {
        try (Connection c = DriverManager.getConnection(ADMIN_URL, ADMIN_USER, ADMIN_PASSWORD);
                Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + DATABASE);
        }
    }

    @Test
    void noDifferenceStillAdjustsExactlyOnceAndWritesLedger() {
        Dimension d = dimension("10.0000", "10.0000", "0.0000");
        InventoryCountVO v = approved(d, "10.0000", "无差异");
        InventoryCountVO adjusted = service.adjust(v.id(), OPERATOR);
        assertEquals(InventoryCountStatus.ADJUSTED, adjusted.status());
        assertBalance(d, "10.0000", "10.0000", "0.0000");
        assertLedger(adjusted.countNo(), "10.0000", "10.0000", "0.0000", "无差异");
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM async_outbox_message WHERE event_name='stockpilot.inventory-count.adjusted' AND business_no=?",
                        Integer.class,
                        adjusted.countNo()));
        assertEquals("COUNT_409_ADJUSTED", code(() -> service.adjust(adjusted.id(), OPERATOR)));
    }

    @Test
    void gainIncreasesActualAndAvailableAndLedgerIsAccurate() {
        Dimension d = dimension("10.0000", "8.0000", "2.0000");
        InventoryCountVO v = approved(d, "13.0000", "发现漏登入库");
        service.adjust(v.id(), OPERATOR);
        assertBalance(d, "13.0000", "11.0000", "2.0000");
        assertLedger(v.countNo(), "10.0000", "13.0000", "3.0000", "发现漏登入库");
    }

    @Test
    void lossReducesAvailableButPreservesFrozenAndRejectsLossBelowFrozen() {
        Dimension d = dimension("10.0000", "6.0000", "4.0000");
        InventoryCountVO v = approved(d, "7.0000", "破损盘亏");
        service.adjust(v.id(), OPERATOR);
        assertBalance(d, "7.0000", "3.0000", "4.0000");
        Dimension bad = dimension("10.0000", "6.0000", "4.0000");
        InventoryCountVO invalid = approved(bad, "4.0000", "错误盘亏");
        // Simulate a legacy approved record: final inventory guard must still reject it.
        jdbc.update(
                "UPDATE inventory_count_line SET counted_quantity=3.0000,difference_quantity=-7.0000 WHERE count_id=?",
                invalid.id());
        assertEquals("INVENTORY_409_INVARIANT", code(() -> service.adjust(invalid.id(), OPERATOR)));
        assertEquals("APPROVED", status(invalid.id()));
        assertBalance(bad, "10.0000", "6.0000", "4.0000");
    }

    @Test
    void unapprovedAdjustmentIsRejected() {
        Dimension d = dimension("5.0000", "5.0000", "0.0000");
        InventoryCountVO draft = create(List.of(d));
        assertEquals("COUNT_409_STATE", code(() -> service.adjust(draft.id(), OPERATOR)));
    }

    @Test
    void staticCountBlocksInventoryChanges() {
        Dimension d = dimension("10.0000", "10.0000", "0.0000");
        InventoryCountVO draft = create(List.of(d));
        assertEquals(
                "INVENTORY_409_COUNT_LOCKED",
                code(
                        () ->
                                inventory.applyChange(
                                        new InventoryChangeCommand(
                                                d.warehouse,
                                                d.location,
                                                d.sku,
                                                0,
                                                InventoryBusinessType.PURCHASE_RECEIPT,
                                                "BLOCKED-" + SEQ.incrementAndGet(),
                                                InventoryQuantityChange.receipt(BigDecimal.ONE),
                                                OPERATOR.userId(),
                                                OPERATOR.username()))));
        assertBalance(d, "10.0000", "10.0000", "0.0000");
        assertEquals(1, scopeCount(draft.id()));
    }

    @Test
    void multiLineAdjustmentFailureRollsBackBalancesLedgersStateAndLocks() {
        Dimension first = dimension("10.0000", "10.0000", "0.0000");
        Dimension second = additional(first, "20.0000");
        InventoryCountVO v = approved(List.of(first, second), List.of("11.0000", "22.0000"));
        jdbc.execute(
                """
          CREATE TRIGGER fail_count_ledger BEFORE INSERT ON inventory_ledger FOR EACH ROW
          BEGIN IF NEW.business_type='INVENTORY_COUNT' AND NEW.sku_id=%d THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced count failure'; END IF; END
          """
                        .formatted(second.sku));
        assertThrows(RuntimeException.class, () -> service.adjust(v.id(), OPERATOR));
        assertEquals("APPROVED", status(v.id()));
        assertBalance(first, "10.0000", "10.0000", "0.0000");
        assertBalance(second, "20.0000", "20.0000", "0.0000");
        assertEquals(0, ledgerCount(v.countNo()));
        assertEquals(2, scopeCount(v.id()));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM async_outbox_message WHERE business_no=?",
                        Integer.class,
                        v.countNo()));
    }

    @Test
    void concurrentCountsCannotOwnSameDimension() throws Exception {
        Dimension d = dimension("10.0000", "10.0000", "0.0000");
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> a = pool.submit(() -> raceCreate(d, "A", ready, start));
            Future<String> b = pool.submit(() -> raceCreate(d, "B", ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<String> results =
                    List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter("DRAFT"::equals).count());
            assertEquals(
                    1,
                    results.stream()
                            .filter(
                                    x ->
                                            x.equals("INVENTORY_409_COUNT_LOCKED")
                                                    || x.equals("COUNT_409_DUPLICATE_SCOPE"))
                            .count());
        } finally {
            pool.shutdownNow();
        }
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM inventory_count_scope_lock", Integer.class));
    }

    @ParameterizedTest
    @EnumSource(
            value = InventoryCountStatus.class,
            names = {"DRAFT", "COUNTING", "SUBMITTED", "APPROVED"})
    void cancellationPreservesEvidenceAndQuantitiesAndReleasesOnlyItsScope(
            InventoryCountStatus stage) {
        Dimension d = dimension("10.0000", "6.0000", "4.0000");
        InventoryCountVO count = create(List.of(d));
        if (stage != InventoryCountStatus.DRAFT) {
            count =
                    service.start(
                            count.id(),
                            new InventoryCountRequests.Transition(count.version()),
                            OPERATOR);
            count =
                    service.recordResults(
                            count.id(),
                            new InventoryCountRequests.RecordResults(
                                    count.version(),
                                    List.of(
                                            new InventoryCountRequests.Result(
                                                    count.lines().get(0).id(),
                                                    new BigDecimal("8.0000"),
                                                    "真实盘亏"))),
                            OPERATOR);
            if (stage != InventoryCountStatus.COUNTING) {
                count =
                        service.submit(
                                count.id(),
                                new InventoryCountRequests.Transition(count.version()),
                                OPERATOR);
                if (stage == InventoryCountStatus.APPROVED)
                    count =
                            service.approve(
                                    count.id(),
                                    new InventoryCountRequests.Transition(count.version()),
                                    AUDITOR);
            }
        }
        var other = create(List.of(additional(d, "20.0000")));
        var before = count;
        var cancelled =
                service.cancel(
                        count.id(),
                        new InventoryCountRequests.Cancel(count.version(), "  现场中止  "),
                        OPERATOR);
        assertEquals(InventoryCountStatus.CANCELLED, cancelled.status());
        assertEquals(before.version() + 1, cancelled.version());
        assertEquals(before.lines(), cancelled.lines());
        assertEquals(OPERATOR.userId(), cancelled.cancelledBy());
        assertEquals(OPERATOR.username(), cancelled.cancelledByName());
        assertNotNull(cancelled.cancelledAt());
        assertEquals("现场中止", cancelled.cancelReason());
        assertEquals(0, scopeCount(cancelled.id()));
        assertEquals(1, scopeCount(other.id()));
        assertBalance(d, "10.0000", "6.0000", "4.0000");
        assertEquals(0, ledgerCount(count.countNo()));
        assertEquals(
                "COUNT_409_CANCELLED",
                code(
                        () ->
                                service.cancel(
                                        before.id(),
                                        new InventoryCountRequests.Cancel(before.version(), "重复"),
                                        OPERATOR)));
        assertEquals("COUNT_409_STATE", code(() -> service.adjust(before.id(), OPERATOR)));
        // Cancellation really restores the normal inventory write path.
        inventory.applyChange(
                new InventoryChangeCommand(
                        d.warehouse,
                        d.location,
                        d.sku,
                        0,
                        InventoryBusinessType.PURCHASE_RECEIPT,
                        "AFTER-CANCEL-" + SEQ.incrementAndGet(),
                        InventoryQuantityChange.receipt(BigDecimal.ONE),
                        OPERATOR.userId(),
                        OPERATOR.username()));
        assertBalance(d, "11.0000", "7.0000", "4.0000");
    }

    @Test
    void belowFrozenCannotBeSubmittedAndCanBeCancelledWithoutFakingTheCount() {
        Dimension d = dimension("10.0000", "6.0000", "4.0000");
        var count = create(List.of(d));
        count =
                service.start(
                        count.id(),
                        new InventoryCountRequests.Transition(count.version()),
                        OPERATOR);
        var recorded =
                service.recordResults(
                        count.id(),
                        new InventoryCountRequests.RecordResults(
                                count.version(),
                                List.of(
                                        new InventoryCountRequests.Result(
                                                count.lines().get(0).id(),
                                                new BigDecimal("3.0000"),
                                                "真实短缺"))),
                        OPERATOR);
        assertEquals(
                "COUNT_409_BELOW_FROZEN",
                code(
                        () ->
                                service.submit(
                                        recorded.id(),
                                        new InventoryCountRequests.Transition(recorded.version()),
                                        OPERATOR)));
        assertEquals("COUNTING", status(recorded.id()));
        assertEquals(1, scopeCount(recorded.id()));
        service.cancel(
                recorded.id(),
                new InventoryCountRequests.Cancel(recorded.version(), "先处理冻结来源"),
                OPERATOR);
        assertEquals(0, scopeCount(recorded.id()));
        assertBalance(d, "10.0000", "6.0000", "4.0000");
    }

    @Test
    void staleVersionAndAdjustedCountCannotBeCancelled() {
        Dimension d = dimension("10.0000", "10.0000", "0.0000");
        var count = approved(d, "11.0000", "盘盈");
        assertEquals(
                "COUNT_409_CONCURRENT",
                code(
                        () ->
                                service.cancel(
                                        count.id(),
                                        new InventoryCountRequests.Cancel(
                                                count.version() - 1, "旧版本"),
                                        OPERATOR)));
        assertEquals(1, scopeCount(count.id()));
        service.adjust(count.id(), OPERATOR);
        assertEquals(
                "COUNT_409_ADJUSTED",
                code(
                        () ->
                                service.cancel(
                                        count.id(),
                                        new InventoryCountRequests.Cancel(
                                                count.version(), "不能撤销调整"),
                                        OPERATOR)));
        assertBalance(d, "11.0000", "11.0000", "0.0000");
        assertEquals(1, ledgerCount(count.countNo()));
    }

    @Test
    void scopeDeletionFailureRollsBackCancellationAndItsAuditFields() {
        Dimension d = dimension("10.0000", "10.0000", "0.0000");
        var count = create(List.of(d));
        jdbc.execute(
                "CREATE TRIGGER fail_count_cancel BEFORE DELETE ON inventory_count_scope_lock FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced scope failure'");
        assertThrows(
                RuntimeException.class,
                () ->
                        service.cancel(
                                count.id(),
                                new InventoryCountRequests.Cancel(count.version(), "测试"),
                                OPERATOR));
        var saved = service.get(count.id());
        assertEquals(InventoryCountStatus.DRAFT, saved.status());
        assertEquals(count.version(), saved.version());
        assertNull(saved.cancelledBy());
        assertNull(saved.cancelReason());
        assertEquals(1, scopeCount(count.id()));
        assertBalance(d, "10.0000", "10.0000", "0.0000");
    }

    @Test
    void concurrentCancelAndAdjustHaveExactlyOneWinner() throws Exception {
        Dimension d = dimension("10.0000", "10.0000", "0.0000");
        var count = approved(d, "11.0000", "盘盈");
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var cancel =
                    pool.submit(
                            () ->
                                    raceAction(
                                            ready,
                                            start,
                                            () ->
                                                    service.cancel(
                                                            count.id(),
                                                            new InventoryCountRequests.Cancel(
                                                                    count.version(), "现场取消"),
                                                            OPERATOR)));
            var adjust =
                    pool.submit(
                            () ->
                                    raceAction(
                                            ready,
                                            start,
                                            () -> service.adjust(count.id(), OPERATOR)));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            var results =
                    List.of(cancel.get(20, TimeUnit.SECONDS), adjust.get(20, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter("OK"::equals).count());
            String finalState = status(count.id());
            assertTrue(finalState.equals("CANCELLED") || finalState.equals("ADJUSTED"));
            boolean adjusted = finalState.equals("ADJUSTED");
            assertBalance(
                    d,
                    adjusted ? "11.0000" : "10.0000",
                    adjusted ? "11.0000" : "10.0000",
                    "0.0000");
            assertEquals(adjusted ? 1 : 0, ledgerCount(count.countNo()));
            assertEquals(0, scopeCount(count.id()));
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    private String raceAction(CountDownLatch ready, CountDownLatch start, Runnable action)
            throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        try {
            action.run();
            return "OK";
        } catch (BusinessException exception) {
            return exception.getErrorCode().code();
        }
    }

    private String raceCreate(
            Dimension d, String suffix, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        try {
            return service.create(
                            new InventoryCountRequests.Create(
                                    "IC-RACE-" + suffix + SEQ.incrementAndGet(),
                                    d.warehouse,
                                    null,
                                    List.of(
                                            new InventoryCountRequests.Dimension(
                                                    d.location, d.sku))),
                            OPERATOR)
                    .status()
                    .name();
        } catch (BusinessException e) {
            return e.getErrorCode().code();
        }
    }

    private InventoryCountVO approved(Dimension d, String counted, String reason) {
        InventoryCountVO v = create(List.of(d));
        v = service.start(v.id(), new InventoryCountRequests.Transition(v.version()), OPERATOR);
        v =
                service.recordResults(
                        v.id(),
                        new InventoryCountRequests.RecordResults(
                                v.version(),
                                List.of(
                                        new InventoryCountRequests.Result(
                                                v.lines().get(0).id(),
                                                new BigDecimal(counted),
                                                reason))),
                        OPERATOR);
        v = service.submit(v.id(), new InventoryCountRequests.Transition(v.version()), OPERATOR);
        return service.approve(v.id(), new InventoryCountRequests.Transition(v.version()), AUDITOR);
    }

    private InventoryCountVO approved(List<Dimension> dims, List<String> quantities) {
        InventoryCountVO v = create(dims);
        v = service.start(v.id(), new InventoryCountRequests.Transition(v.version()), OPERATOR);
        List<InventoryCountRequests.Result> results = new ArrayList<>();
        for (int i = 0; i < v.lines().size(); i++)
            results.add(
                    new InventoryCountRequests.Result(
                            v.lines().get(i).id(), new BigDecimal(quantities.get(i)), "差异调整"));
        v =
                service.recordResults(
                        v.id(),
                        new InventoryCountRequests.RecordResults(v.version(), results),
                        OPERATOR);
        v = service.submit(v.id(), new InventoryCountRequests.Transition(v.version()), OPERATOR);
        return service.approve(v.id(), new InventoryCountRequests.Transition(v.version()), AUDITOR);
    }

    private InventoryCountVO create(List<Dimension> dims) {
        Dimension d = dims.get(0);
        return service.create(
                new InventoryCountRequests.Create(
                        "IC-" + SEQ.incrementAndGet(),
                        d.warehouse,
                        null,
                        dims.stream()
                                .map(x -> new InventoryCountRequests.Dimension(x.location, x.sku))
                                .toList()),
                OPERATOR);
    }

    private String code(Runnable r) {
        try {
            r.run();
            return "NO_ERROR";
        } catch (BusinessException e) {
            return e.getErrorCode().code();
        }
    }

    private String status(long id) {
        return jdbc.queryForObject(
                "SELECT status FROM inventory_count_order WHERE id=?", String.class, id);
    }

    private int scopeCount(long id) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_count_scope_lock WHERE count_id=?",
                Integer.class,
                id);
    }

    private int ledgerCount(String no) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_ledger WHERE business_type='INVENTORY_COUNT' AND business_no=?",
                Integer.class,
                no);
    }

    private void assertLedger(String no, String book, String counted, String diff, String reason) {
        Map<String, Object> row =
                jdbc.queryForMap(
                        "SELECT count_book_quantity,counted_quantity,difference_quantity,adjustment_reason FROM inventory_ledger WHERE business_type='INVENTORY_COUNT' AND business_no=?",
                        no);
        assertEquals(new BigDecimal(book), row.get("count_book_quantity"));
        assertEquals(new BigDecimal(counted), row.get("counted_quantity"));
        assertEquals(new BigDecimal(diff), row.get("difference_quantity"));
        assertEquals(reason, row.get("adjustment_reason"));
    }

    private void assertBalance(Dimension d, String actual, String available, String frozen) {
        Map<String, Object> row =
                jdbc.queryForMap(
                        "SELECT actual_quantity,available_quantity,frozen_quantity FROM inventory_balance WHERE warehouse_id=? AND location_id=? AND sku_id=?",
                        d.warehouse,
                        d.location,
                        d.sku);
        assertEquals(new BigDecimal(actual), row.get("actual_quantity"));
        assertEquals(new BigDecimal(available), row.get("available_quantity"));
        assertEquals(new BigDecimal(frozen), row.get("frozen_quantity"));
    }

    private Dimension dimension(String actual, String available, String frozen) {
        int n = SEQ.incrementAndGet();
        long w = warehouse("WC" + n), l = location(w, "LC" + n), s = sku("SC" + n);
        jdbc.update(
                "INSERT INTO inventory_balance(warehouse_id,location_id,sku_id,actual_quantity,available_quantity,frozen_quantity,version) VALUES(?,?,?,?,?,?,0)",
                w,
                l,
                s,
                new BigDecimal(actual),
                new BigDecimal(available),
                new BigDecimal(frozen));
        return new Dimension(w, l, s);
    }

    private Dimension additional(Dimension base, String actual) {
        int n = SEQ.incrementAndGet();
        long l = location(base.warehouse, "LC" + n), s = sku("SC" + n);
        jdbc.update(
                "INSERT INTO inventory_balance(warehouse_id,location_id,sku_id,actual_quantity,available_quantity,frozen_quantity,version) VALUES(?,?,?,?,?,0,0)",
                base.warehouse,
                l,
                s,
                new BigDecimal(actual),
                new BigDecimal(actual));
        return new Dimension(base.warehouse, l, s);
    }

    private long warehouse(String code) {
        jdbc.update(
                "INSERT INTO warehouse(code,name,status,version) VALUES(?,?,'ENABLED',0)",
                code,
                code);
        return jdbc.queryForObject("SELECT id FROM warehouse WHERE code=?", Long.class, code);
    }

    private long location(long w, String code) {
        jdbc.update(
                "INSERT INTO warehouse_location(warehouse_id,code,name,status,version) VALUES(?,?,?,'ENABLED',0)",
                w,
                code,
                code);
        return jdbc.queryForObject(
                "SELECT id FROM warehouse_location WHERE warehouse_id=? AND code=?",
                Long.class,
                w,
                code);
    }

    private long sku(String code) {
        jdbc.update(
                "INSERT INTO sku(code,name,unit,status,version) VALUES(?,?,?,'ENABLED',0)",
                code,
                code,
                "PCS");
        return jdbc.queryForObject("SELECT id FROM sku WHERE code=?", Long.class, code);
    }

    private record Dimension(long warehouse, long location, long sku) {}

    static class MySqlInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        public void initialize(ConfigurableApplicationContext context) {
            IntegrationTestInfrastructure.isolate(context, DATABASE);
            try (Connection c = DriverManager.getConnection(ADMIN_URL, ADMIN_USER, ADMIN_PASSWORD);
                    Statement s = c.createStatement()) {
                s.execute("DROP DATABASE IF EXISTS " + DATABASE);
                s.execute(
                        "CREATE DATABASE "
                                + DATABASE
                                + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Cannot prepare count integration-test database", e);
            }
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                    context,
                    "spring.datasource.url="
                            + IntegrationTestInfrastructure.databaseUrl(ADMIN_URL, DATABASE),
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
}
