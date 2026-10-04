package com.stockpilot.inventory.controller;

import com.stockpilot.inventory.request.InventoryBalancePageQuery;
import com.stockpilot.inventory.request.InventoryLedgerPageQuery;
import com.stockpilot.inventory.service.InventoryQueryApplicationService;
import com.stockpilot.inventory.vo.InventoryBalanceVO;
import com.stockpilot.inventory.vo.InventoryLedgerVO;
import com.stockpilot.shared.api.ApiResponse;
import com.stockpilot.shared.api.PageResult;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/inventory")
public class InventoryQueryController {
    private final InventoryQueryApplicationService inventoryQueryService;

    public InventoryQueryController(InventoryQueryApplicationService inventoryQueryService) {
        this.inventoryQueryService = inventoryQueryService;
    }

    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    @GetMapping("/balances")
    public ApiResponse<PageResult<InventoryBalanceVO>> balances(
            @Valid @ModelAttribute InventoryBalancePageQuery query) {
        return ApiResponse.success(inventoryQueryService.pageBalances(query));
    }

    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    @GetMapping("/ledgers")
    public ApiResponse<PageResult<InventoryLedgerVO>> ledgers(
            @Valid @ModelAttribute InventoryLedgerPageQuery query) {
        return ApiResponse.success(inventoryQueryService.pageLedgers(query));
    }
}
