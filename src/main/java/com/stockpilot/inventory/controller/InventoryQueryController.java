package com.stockpilot.inventory.controller;

import com.stockpilot.common.api.ApiResponse;
import com.stockpilot.inventory.application.InventoryQueryApplicationService;
import com.stockpilot.inventory.request.InventoryBalancePageQuery;
import com.stockpilot.inventory.request.InventoryLedgerPageQuery;
import com.stockpilot.inventory.vo.InventoryBalanceVO;
import com.stockpilot.inventory.vo.InventoryLedgerVO;
import com.stockpilot.masterdata.vo.PageResult;
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
    private final InventoryQueryApplicationService service;

    public InventoryQueryController(InventoryQueryApplicationService service) {
        this.service = service;
    }

    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    @GetMapping("/balances")
    public ApiResponse<PageResult<InventoryBalanceVO>> balances(
            @Valid @ModelAttribute InventoryBalancePageQuery query) {
        return ApiResponse.success(service.pageBalances(query));
    }

    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    @GetMapping("/ledgers")
    public ApiResponse<PageResult<InventoryLedgerVO>> ledgers(
            @Valid @ModelAttribute InventoryLedgerPageQuery query) {
        return ApiResponse.success(service.pageLedgers(query));
    }
}
