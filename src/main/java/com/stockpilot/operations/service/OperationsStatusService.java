package com.stockpilot.operations.service;

import com.stockpilot.messaging.service.MessageManagementService;
import com.stockpilot.operations.vo.OperationsStatusVO;
import com.stockpilot.shared.auth.WarehouseAccess;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OperationsStatusService {
    private final DataSource dataSource;
    private final MessageManagementService messageManagementService;
    private final MeterRegistry meterRegistry;
    private final boolean messagingEnabled;
    private final boolean cacheEnabled;

    public OperationsStatusService(
            DataSource dataSource,
            MessageManagementService messageManagementService,
            MeterRegistry meterRegistry,
            @Value("${stockpilot.messaging.enabled:false}") boolean messagingEnabled,
            @Value("${stockpilot.cache.enabled:false}") boolean cacheEnabled) {
        this.dataSource = dataSource;
        this.messageManagementService = messageManagementService;
        this.meterRegistry = meterRegistry;
        this.messagingEnabled = messagingEnabled;
        this.cacheEnabled = cacheEnabled;
    }

    public OperationsStatusVO status() {
        WarehouseAccess.requireAll();
        var pool =
                dataSource instanceof HikariDataSource hikari ? hikari.getHikariPoolMXBean() : null;
        long count = 0;
        double seconds = 0;
        for (var timer : meterRegistry.find("http.server.requests").timers()) {
            count += timer.count();
            seconds += timer.totalTime(TimeUnit.SECONDS);
        }
        var runtime = Runtime.getRuntime();
        return new OperationsStatusVO(
                Instant.now(),
                messagingEnabled,
                cacheEnabled,
                pool == null ? -1 : pool.getActiveConnections(),
                pool == null ? -1 : pool.getIdleConnections(),
                pool == null ? -1 : pool.getThreadsAwaitingConnection(),
                count,
                seconds,
                runtime.totalMemory() - runtime.freeMemory(),
                messageManagementService.backlog());
    }
}
