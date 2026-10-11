package com.stockpilot.operations.controller;

import com.stockpilot.operations.service.OperationsStatusService;
import com.stockpilot.operations.vo.OperationsStatusVO;
import com.stockpilot.shared.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/operations/status")
public class OperationsStatusController {
    private final OperationsStatusService operationsStatusService;

    public OperationsStatusController(OperationsStatusService operationsStatusService) {
        this.operationsStatusService = operationsStatusService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('OPERATIONS_READ')")
    public ApiResponse<OperationsStatusVO> status() {
        return ApiResponse.success(operationsStatusService.status());
    }
}
