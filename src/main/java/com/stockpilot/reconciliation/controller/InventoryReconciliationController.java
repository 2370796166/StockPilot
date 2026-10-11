package com.stockpilot.reconciliation.controller;

import com.stockpilot.reconciliation.service.InventoryReconciliationService;
import com.stockpilot.reconciliation.vo.InventoryReconciliationVO;
import com.stockpilot.shared.api.ApiResponse;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/inventory/reconciliation")
public class InventoryReconciliationController {
    private final InventoryReconciliationService inventoryReconciliationService;

    public InventoryReconciliationController(
            InventoryReconciliationService inventoryReconciliationService) {
        this.inventoryReconciliationService = inventoryReconciliationService;
    }

    @GetMapping
    @PreAuthorize(
            "hasAuthority('INVENTORY_RECONCILE') and hasAuthority('INVENTORY_READ') and hasAuthority('SALES_OUTBOUND_READ') and hasAuthority('TRANSFER_READ')")
    public ApiResponse<InventoryReconciliationVO> check(
            @Positive @RequestParam long warehouseId,
            @Positive @RequestParam long locationId,
            @Positive @RequestParam long skuId) {
        return ApiResponse.success(
                inventoryReconciliationService.check(warehouseId, locationId, skuId));
    }
}
