package com.stockpilot.shared.health.service;

import com.stockpilot.shared.health.mapper.DatabaseHealthMapper;
import com.stockpilot.shared.health.vo.HealthVO;
import org.springframework.stereotype.Service;

@Service
public class HealthApplicationService {
    private final DatabaseHealthMapper databaseHealthMapper;

    public HealthApplicationService(DatabaseHealthMapper databaseHealthMapper) {
        this.databaseHealthMapper = databaseHealthMapper;
    }

    // 返回应用存活状态并执行轻量数据库探测，用于区分应用可访问但 MySQL 不可用的情况。
    public HealthVO check() {
        Integer result = databaseHealthMapper.selectOne();
        if (result == null || result != 1) {
            throw new IllegalStateException("Database health check failed");
        }
        return new HealthVO("UP", "UP");
    }
}
