package com.stockpilot.health.application;

import com.stockpilot.health.infrastructure.mapper.DatabaseHealthMapper;
import com.stockpilot.health.vo.HealthVO;
import org.springframework.stereotype.Service;

@Service
public class HealthApplicationService {
    private final DatabaseHealthMapper databaseHealthMapper;

    public HealthApplicationService(DatabaseHealthMapper databaseHealthMapper) {
        this.databaseHealthMapper = databaseHealthMapper;
    }

    public HealthVO check() {
        Integer result = databaseHealthMapper.selectOne();
        if (result == null || result != 1) {
            throw new IllegalStateException("Database health check failed");
        }
        return new HealthVO("UP", "UP");
    }
}
