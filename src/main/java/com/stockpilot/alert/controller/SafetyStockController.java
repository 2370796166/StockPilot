package com.stockpilot.alert.controller;

import com.stockpilot.alert.request.SafetyStockRequests;
import com.stockpilot.alert.service.SafetyStockManagementService;
import com.stockpilot.alert.vo.SafetyStockVO;
import com.stockpilot.shared.api.ApiResponse;
import com.stockpilot.shared.api.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/safety-stock")
public class SafetyStockController {
    private final SafetyStockManagementService safetyStockManagementService;

    public SafetyStockController(SafetyStockManagementService safetyStockManagementService) {
        this.safetyStockManagementService = safetyStockManagementService;
    }

    @GetMapping("/rules")
    @PreAuthorize("hasAuthority('SAFETY_STOCK_READ')")
    public ApiResponse<PageResult<SafetyStockVO.Rule>> rules(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) @Positive Long warehouseId,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(
                safetyStockManagementService.rules(page, size, warehouseId, status));
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasAuthority('SAFETY_STOCK_READ')")
    public ApiResponse<PageResult<SafetyStockVO.Alert>> alerts(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) @Positive Long warehouseId,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(
                safetyStockManagementService.alerts(page, size, warehouseId, status));
    }

    @PostMapping("/rules")
    @PreAuthorize("hasAuthority('SAFETY_STOCK_WRITE')")
    public ApiResponse<SafetyStockVO.Rule> create(
            @Valid @RequestBody SafetyStockRequests.Create request) {
        return ApiResponse.success(safetyStockManagementService.create(request));
    }

    @PutMapping("/rules/{id}")
    @PreAuthorize("hasAuthority('SAFETY_STOCK_WRITE')")
    public ApiResponse<SafetyStockVO.Rule> update(
            @Positive @PathVariable long id,
            @Valid @RequestBody SafetyStockRequests.Update request) {
        return ApiResponse.success(safetyStockManagementService.update(id, request));
    }

    @PostMapping("/rules/{id}/evaluate")
    @PreAuthorize("hasAuthority('SAFETY_STOCK_WRITE')")
    public ApiResponse<Void> evaluate(@Positive @PathVariable long id) {
        safetyStockManagementService.reevaluate(id);
        return ApiResponse.success();
    }
}
