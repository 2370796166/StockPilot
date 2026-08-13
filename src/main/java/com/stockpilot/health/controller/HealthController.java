package com.stockpilot.health.controller;

import com.stockpilot.common.api.ApiResponse;
import com.stockpilot.health.application.HealthApplicationService;
import com.stockpilot.health.vo.HealthVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Health", description = "应用和数据库健康检查")
@RestController
@RequestMapping("/api/health")
public class HealthController {
    private final HealthApplicationService healthApplicationService;

    public HealthController(HealthApplicationService healthApplicationService) {
        this.healthApplicationService = healthApplicationService;
    }

    @Operation(summary = "检查应用与MySQL连接")
    @GetMapping
    public ApiResponse<HealthVO> health() {
        return ApiResponse.success(healthApplicationService.check());
    }
}
