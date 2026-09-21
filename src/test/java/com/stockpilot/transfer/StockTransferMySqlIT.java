package com.stockpilot.transfer;

import static org.junit.jupiter.api.Assertions.*;

import com.stockpilot.StockPilotApplication;
import com.stockpilot.security.auth.StockPilotPrincipal;
import com.stockpilot.shared.exception.BusinessException;
import com.stockpilot.transfer.domain.StockTransferStatus;
import com.stockpilot.transfer.request.StockTransferRequests;
import com.stockpilot.transfer.service.StockTransferApplicationService;
import com.stockpilot.transfer.vo.StockTransferVO;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.support.TestPropertySourceUtils;

@SpringBootTest(classes = StockTransferMySqlIT.TestApplication.class)
@ContextConfiguration(initializers = StockTransferMySqlIT.MySqlInitializer.class)
class StockTransferMySqlIT {
    private static final String DATABASE = "stockpilot_transfer_it";
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
            new StockPilotPrincipal(201L, "transfer-operator");
    private static final StockPilotPrincipal AUDITOR =
            new StockPilotPrincipal(202L, "transfer-auditor");
    private static final AtomicInteger SEQ = new AtomicInteger();
    @Autowired StockTransferApplicationService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_transfer_in_ledger");
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
    void normalTransferWritesSourceTargetLedgersAndTransitExactlyOnce() {
        Route r = route("100.0000");
        StockTransferVO submitted = submit(draft("TR-NORMAL-", r, "30.0000"));
        assertEquals(StockTransferStatus.SUBMITTED, submitted.status());
        assertBalance(r.sourceWarehouse, r.sourceLocation, r.sku, "100.0000", "70.0000", "30.0000");
        StockTransferVO approved =
                service.approve(
                        submitted.id(),
                        new StockTransferRequests.Transition(submitted.version()),
                        AUDITOR);
        StockTransferVO outbound = service.dispatch(approved.id(), OPERATOR);
        assertEquals(StockTransferStatus.OUTBOUND_COMPLETED, outbound.status());
        assertBalance(r.sourceWarehouse, r.sourceLocation, r.sku, "70.0000", "70.0000", "0.0000");
        assertEquals(
                "TRANSFER_409_OUTBOUND", code(() -> service.dispatch(outbound.id(), OPERATOR)));
        assertTransit(outbound.id(), "30.0000", "30.0000", "0.0000", "IN_TRANSIT");
        StockTransferVO moving =
                service.startTransit(
                        outbound.id(),
                        new StockTransferRequests.Transition(outbound.version()),
                        OPERATOR);
        StockTransferVO completed = service.receive(moving.id(), OPERATOR);
        assertEquals(StockTransferStatus.COMPLETED, completed.status());
        assertBalance(r.targetWarehouse, r.targetLocation, r.sku, "30.0000", "30.0000", "0.0000");
        assertTransit(completed.id(), "30.0000", "0.0000", "30.0000", "RECEIVED");
        assertEquals(
                "TRANSFER_409_COMPLETED", code(() -> service.receive(completed.id(), OPERATOR)));
        assertEquals(1, ledger(completed.transferNo(), "TRANSFER_FREEZE"));
        assertEquals(1, ledger(completed.transferNo(), "TRANSFER_OUT"));
        assertEquals(1, ledger(completed.transferNo(), "TRANSFER_IN"));
        assertEquals(0, ledger(completed.transferNo(), "TRANSFER_RELEASE"));
    }

    @Test
    void rejectsSameWarehouseAndMismatchedLocations() {
        Route r = route("10.0000");
        assertEquals(
                "TRANSFER_400_SAME_WAREHOUSE",
                code(
                        () ->
                                service.create(
                                        new StockTransferRequests.Create(
                                                "TR-SAME-" + SEQ.incrementAndGet(),
                                                r.sourceWarehouse,
                                                r.sourceWarehouse,
                                                null,
                                                List.of(
                                                        new StockTransferRequests.Line(
                                                                r.sourceLocation,
                                                                r.sourceLocation,
                                                                r.sku,
                                                                BigDecimal.ONE))),
                                        OPERATOR)));
        assertThrows(
                BusinessException.class,
                () ->
                        service.create(
                                new StockTransferRequests.Create(
                                        "TR-WRONG-" + SEQ.incrementAndGet(),
                                        r.sourceWarehouse,
                                        r.targetWarehouse,
                                        null,
                                        List.of(
                                                new StockTransferRequests.Line(
                                                        r.sourceLocation,
                                                        r.sourceLocation,
                                                        r.sku,
                                                        BigDecimal.ONE))),
                                OPERATOR));
    }

    @Test
    void insufficientSourceStockRollsBackWholeSubmission() {
        Route r = route("5.0000");
        StockTransferVO draft = draft("TR-SHORT-", r, "10.0000");
        assertEquals("INVENTORY_409_INSUFFICIENT_AVAILABLE", code(() -> submit(draft)));
        assertEquals("DRAFT", status(draft.id()));
        assertBalance(r.sourceWarehouse, r.sourceLocation, r.sku, "5.0000", "5.0000", "0.0000");
        assertEquals(0, allLedgers(draft.transferNo()));
    }

    @Test
    void cancelBeforeOutboundReleasesAndOutboundCannotBeCancelled() {
        Route r = route("100.0000");
        StockTransferVO submitted = submit(draft("TR-CANCEL-", r, "20.0000"));
        StockTransferVO cancelled = service.cancel(submitted.id(), OPERATOR);
        assertEquals(StockTransferStatus.CANCELLED, cancelled.status());
        assertBalance(r.sourceWarehouse, r.sourceLocation, r.sku, "100.0000", "100.0000", "0.0000");
        assertEquals(1, ledger(cancelled.transferNo(), "TRANSFER_RELEASE"));
        assertEquals(
                "TRANSFER_409_CANCELLED", code(() -> service.cancel(cancelled.id(), OPERATOR)));
        StockTransferVO second = submit(draft("TR-NOCANCEL-", r, "10.0000"));
        second =
                service.approve(
                        second.id(),
                        new StockTransferRequests.Transition(second.version()),
                        AUDITOR);
        second = service.dispatch(second.id(), OPERATOR);
        StockTransferVO outbound = second;
        assertEquals("TRANSFER_409_OUTBOUND", code(() -> service.cancel(outbound.id(), OPERATOR)));
    }

    @Test
    void targetReceiveFailureKeepsTransitAndDoesNotComplete() {
        Route r = route("100.0000");
        StockTransferVO v = submit(draft("TR-FAIL-IN-", r, "25.0000"));
        v = service.approve(v.id(), new StockTransferRequests.Transition(v.version()), AUDITOR);
        v = service.dispatch(v.id(), OPERATOR);
        v =
                service.startTransit(
                        v.id(), new StockTransferRequests.Transition(v.version()), OPERATOR);
        jdbc.execute(
                """
   CREATE TRIGGER fail_transfer_in_ledger BEFORE INSERT ON inventory_ledger FOR EACH ROW
   BEGIN IF NEW.business_type='TRANSFER_IN' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced transfer in failure'; END IF; END
   """);
        StockTransferVO moving = v;
        assertThrows(RuntimeException.class, () -> service.receive(moving.id(), OPERATOR));
        assertEquals("IN_TRANSIT", status(v.id()));
        assertTransit(v.id(), "25.0000", "25.0000", "0.0000", "IN_TRANSIT");
        assertEquals(0, ledger(v.transferNo(), "TRANSFER_IN"));
        assertEquals(0, balanceCount(r.targetWarehouse, r.targetLocation, r.sku));
    }

    @Test
    void multiSkuTransferUsesStableOrderAndMovesEveryLine() {
        Route first = route("100.0000");
        Route second = additionalRoute(first, "80.0000");
        StockTransferVO v =
                service.create(
                        new StockTransferRequests.Create(
                                "TR-MULTI-" + SEQ.incrementAndGet(),
                                first.sourceWarehouse,
                                first.targetWarehouse,
                                null,
                                List.of(
                                        new StockTransferRequests.Line(
                                                second.sourceLocation,
                                                second.targetLocation,
                                                second.sku,
                                                new BigDecimal("20.0000")),
                                        new StockTransferRequests.Line(
                                                first.sourceLocation,
                                                first.targetLocation,
                                                first.sku,
                                                new BigDecimal("30.0000")))),
                        OPERATOR);
        v = submit(v);
        v = service.approve(v.id(), new StockTransferRequests.Transition(v.version()), AUDITOR);
        v = service.dispatch(v.id(), OPERATOR);
        v =
                service.startTransit(
                        v.id(), new StockTransferRequests.Transition(v.version()), OPERATOR);
        v = service.receive(v.id(), OPERATOR);
        assertEquals(StockTransferStatus.COMPLETED, v.status());
        assertEquals(2, ledger(v.transferNo(), "TRANSFER_OUT"));
        assertEquals(2, ledger(v.transferNo(), "TRANSFER_IN"));
        assertBalance(
                first.targetWarehouse,
                first.targetLocation,
                first.sku,
                "30.0000",
                "30.0000",
                "0.0000");
        assertBalance(
                second.targetWarehouse,
                second.targetLocation,
                second.sku,
                "20.0000",
                "20.0000",
                "0.0000");
    }

    @Test
    void concurrentTransfersCannotOverReserveSourceStock() throws Exception {
        Route r = route("100.0000");
        StockTransferVO one = draft("TR-RACE-A-", r, "60.0000");
        StockTransferVO two = draft("TR-RACE-B-", r, "60.0000");
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> a = pool.submit(() -> raceSubmit(one, ready, start));
            Future<String> b = pool.submit(() -> raceSubmit(two, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<String> results =
                    List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter("SUBMITTED"::equals).count());
            assertEquals(
                    1,
                    results.stream()
                            .filter("INVENTORY_409_INSUFFICIENT_AVAILABLE"::equals)
                            .count());
        } finally {
            pool.shutdownNow();
        }
        assertBalance(r.sourceWarehouse, r.sourceLocation, r.sku, "100.0000", "40.0000", "60.0000");
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM inventory_ledger WHERE business_type='TRANSFER_FREEZE'",
                        Integer.class));
    }

    private String raceSubmit(StockTransferVO v, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        try {
            return submit(v).status().name();
        } catch (BusinessException e) {
            return e.getErrorCode().code();
        }
    }

    private StockTransferVO draft(String prefix, Route r, String q) {
        return service.create(
                new StockTransferRequests.Create(
                        prefix + SEQ.incrementAndGet(),
                        r.sourceWarehouse,
                        r.targetWarehouse,
                        null,
                        List.of(
                                new StockTransferRequests.Line(
                                        r.sourceLocation,
                                        r.targetLocation,
                                        r.sku,
                                        new BigDecimal(q)))),
                OPERATOR);
    }

    private StockTransferVO submit(StockTransferVO v) {
        return service.submit(v.id(), new StockTransferRequests.Transition(v.version()), OPERATOR);
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
                "SELECT status FROM stock_transfer_order WHERE id=?", String.class, id);
    }

    private int ledger(String no, String type) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_ledger WHERE business_no=? AND business_type=?",
                Integer.class,
                no,
                type);
    }

    private int allLedgers(String no) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_ledger WHERE business_no=?", Integer.class, no);
    }

    private int balanceCount(long w, long l, long s) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_balance WHERE warehouse_id=? AND location_id=? AND sku_id=?",
                Integer.class,
                w,
                l,
                s);
    }

    private void assertTransit(long id, String out, String moving, String received, String status) {
        Map<String, Object> row =
                jdbc.queryForMap(
                        "SELECT outbound_quantity,in_transit_quantity,received_quantity,status FROM stock_transfer_transit WHERE transfer_id=?",
                        id);
        assertEquals(new BigDecimal(out), row.get("outbound_quantity"));
        assertEquals(new BigDecimal(moving), row.get("in_transit_quantity"));
        assertEquals(new BigDecimal(received), row.get("received_quantity"));
        assertEquals(status, row.get("status"));
    }

    private void assertBalance(
            long w, long l, long s, String actual, String available, String frozen) {
        Map<String, Object> row =
                jdbc.queryForMap(
                        "SELECT actual_quantity,available_quantity,frozen_quantity FROM inventory_balance WHERE warehouse_id=? AND location_id=? AND sku_id=?",
                        w,
                        l,
                        s);
        assertEquals(new BigDecimal(actual), row.get("actual_quantity"));
        assertEquals(new BigDecimal(available), row.get("available_quantity"));
        assertEquals(new BigDecimal(frozen), row.get("frozen_quantity"));
    }

    private Route route(String quantity) {
        int n = SEQ.incrementAndGet();
        long source = warehouse("WS" + n), target = warehouse("WT" + n);
        long sl = location(source, "LS" + n), tl = location(target, "LT" + n), sku = sku("SK" + n);
        jdbc.update(
                "INSERT INTO inventory_balance(warehouse_id,location_id,sku_id,actual_quantity,available_quantity,frozen_quantity,version) VALUES(?,?,?,?,?,0,0)",
                source,
                sl,
                sku,
                new BigDecimal(quantity),
                new BigDecimal(quantity));
        return new Route(source, target, sl, tl, sku);
    }

    private Route additionalRoute(Route base, String quantity) {
        int n = SEQ.incrementAndGet();
        long sl = location(base.sourceWarehouse, "LS" + n),
                tl = location(base.targetWarehouse, "LT" + n),
                sku = sku("SK" + n);
        jdbc.update(
                "INSERT INTO inventory_balance(warehouse_id,location_id,sku_id,actual_quantity,available_quantity,frozen_quantity,version) VALUES(?,?,?,?,?,0,0)",
                base.sourceWarehouse,
                sl,
                sku,
                new BigDecimal(quantity),
                new BigDecimal(quantity));
        return new Route(base.sourceWarehouse, base.targetWarehouse, sl, tl, sku);
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

    private record Route(
            long sourceWarehouse,
            long targetWarehouse,
            long sourceLocation,
            long targetLocation,
            long sku) {}

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
                        "Cannot prepare transfer integration-test database", e);
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
}
