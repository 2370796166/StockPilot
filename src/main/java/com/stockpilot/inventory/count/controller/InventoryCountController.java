package com.stockpilot.inventory.count.controller;

import com.stockpilot.inventory.count.request.InventoryCountRequests;
import com.stockpilot.inventory.count.service.InventoryCountApplicationService;
import com.stockpilot.inventory.count.vo.*;
import com.stockpilot.shared.api.ApiResponse;
import com.stockpilot.shared.api.PageResult;
import com.stockpilot.shared.auth.AuthenticatedActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/inventory-counts")
public class InventoryCountController {
    private final InventoryCountApplicationService inventoryCountService;

    public InventoryCountController(InventoryCountApplicationService inventoryCountService) {
        this.inventoryCountService = inventoryCountService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('INVENTORY_COUNT_READ')")
    public ApiResponse<PageResult<InventoryCountSummaryVO>> page(
            @Valid @ModelAttribute InventoryCountRequests.PageQuery q) {
        return ApiResponse.success(inventoryCountService.page(q));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_COUNT_READ')")
    public ApiResponse<InventoryCountVO> get(@PathVariable @Positive long id) {
        return ApiResponse.success(inventoryCountService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('INVENTORY_COUNT_WRITE')")
    public ApiResponse<InventoryCountVO> create(
            @Valid @RequestBody InventoryCountRequests.Create r,
            @AuthenticationPrincipal AuthenticatedActor a) {
        return ApiResponse.success(inventoryCountService.create(r, a));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('INVENTORY_COUNT_WRITE')")
    public ApiResponse<InventoryCountVO> start(
            @PathVariable @Positive long id,
            @Valid @RequestBody InventoryCountRequests.Transition r,
            @AuthenticationPrincipal AuthenticatedActor a) {
        return ApiResponse.success(inventoryCountService.start(id, r, a));
    }

    @PutMapping("/{id}/results")
    @PreAuthorize("hasAuthority('INVENTORY_COUNT_WRITE')")
    public ApiResponse<InventoryCountVO> results(
            @PathVariable @Positive long id,
            @Valid @RequestBody InventoryCountRequests.RecordResults r,
            @AuthenticationPrincipal AuthenticatedActor a) {
        return ApiResponse.success(inventoryCountService.recordResults(id, r, a));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('INVENTORY_COUNT_WRITE')")
    public ApiResponse<InventoryCountVO> submit(
            @PathVariable @Positive long id,
            @Valid @RequestBody InventoryCountRequests.Transition r,
            @AuthenticationPrincipal AuthenticatedActor a) {
        return ApiResponse.success(inventoryCountService.submit(id, r, a));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('INVENTORY_COUNT_APPROVE')")
    public ApiResponse<InventoryCountVO> approve(
            @PathVariable @Positive long id,
            @Valid @RequestBody InventoryCountRequests.Transition r,
            @AuthenticationPrincipal AuthenticatedActor a) {
        return ApiResponse.success(inventoryCountService.approve(id, r, a));
    }

    @PostMapping("/{id}/adjust")
    @PreAuthorize("hasAuthority('INVENTORY_COUNT_ADJUST')")
    public ApiResponse<InventoryCountVO> adjust(
            @PathVariable @Positive long id, @AuthenticationPrincipal AuthenticatedActor a) {
        return ApiResponse.success(inventoryCountService.adjust(id, a));
    }
}
