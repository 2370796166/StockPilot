package com.stockpilot.security.controller;

import com.stockpilot.security.service.WarehouseAuthorizationService;
import com.stockpilot.shared.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/security/users/{id}/warehouses")
public class WarehouseAuthorizationController {
    private final WarehouseAuthorizationService warehouseAuthorizationService;

    public WarehouseAuthorizationController(
            WarehouseAuthorizationService warehouseAuthorizationService) {
        this.warehouseAuthorizationService = warehouseAuthorizationService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SECURITY_GRANT')")
    public ApiResponse<com.stockpilot.security.vo.SecurityVO.WarehouseScope> get(
            @PathVariable @Positive long id) {
        return ApiResponse.success(warehouseAuthorizationService.get(id));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('SECURITY_GRANT')")
    public ApiResponse<com.stockpilot.security.vo.SecurityVO.WarehouseScope> update(
            @PathVariable @Positive long id,
            @Valid @RequestBody
                    com.stockpilot.security.request.SecurityRequests.WarehouseScope request) {
        return ApiResponse.success(warehouseAuthorizationService.update(id, request));
    }
}
