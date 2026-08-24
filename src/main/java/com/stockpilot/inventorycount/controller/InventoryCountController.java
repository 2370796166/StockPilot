package com.stockpilot.inventorycount.controller;

import com.stockpilot.common.api.ApiResponse;
import com.stockpilot.inventorycount.application.InventoryCountApplicationService;
import com.stockpilot.inventorycount.request.InventoryCountRequests;
import com.stockpilot.inventorycount.vo.*;
import com.stockpilot.masterdata.vo.PageResult;
import com.stockpilot.security.auth.StockPilotPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated @RestController @RequestMapping("/api/inventory-counts")
public class InventoryCountController {
    private final InventoryCountApplicationService service;
    public InventoryCountController(InventoryCountApplicationService service){this.service=service;}
    @GetMapping @PreAuthorize("hasAuthority('INVENTORY_COUNT_READ')")
    public ApiResponse<PageResult<InventoryCountSummaryVO>> page(@Valid @ModelAttribute InventoryCountRequests.PageQuery q){return ApiResponse.success(service.page(q));}
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('INVENTORY_COUNT_READ')")
    public ApiResponse<InventoryCountVO> get(@PathVariable @Positive long id){return ApiResponse.success(service.get(id));}
    @PostMapping @PreAuthorize("hasAuthority('INVENTORY_COUNT_WRITE')")
    public ApiResponse<InventoryCountVO> create(@Valid @RequestBody InventoryCountRequests.Create r,@AuthenticationPrincipal StockPilotPrincipal a){return ApiResponse.success(service.create(r,a));}
    @PostMapping("/{id}/start") @PreAuthorize("hasAuthority('INVENTORY_COUNT_WRITE')")
    public ApiResponse<InventoryCountVO> start(@PathVariable @Positive long id,@Valid @RequestBody InventoryCountRequests.Transition r,@AuthenticationPrincipal StockPilotPrincipal a){return ApiResponse.success(service.start(id,r,a));}
    @PutMapping("/{id}/results") @PreAuthorize("hasAuthority('INVENTORY_COUNT_WRITE')")
    public ApiResponse<InventoryCountVO> results(@PathVariable @Positive long id,@Valid @RequestBody InventoryCountRequests.RecordResults r,@AuthenticationPrincipal StockPilotPrincipal a){return ApiResponse.success(service.recordResults(id,r,a));}
    @PostMapping("/{id}/submit") @PreAuthorize("hasAuthority('INVENTORY_COUNT_WRITE')")
    public ApiResponse<InventoryCountVO> submit(@PathVariable @Positive long id,@Valid @RequestBody InventoryCountRequests.Transition r,@AuthenticationPrincipal StockPilotPrincipal a){return ApiResponse.success(service.submit(id,r,a));}
    @PostMapping("/{id}/approve") @PreAuthorize("hasAuthority('INVENTORY_COUNT_APPROVE')")
    public ApiResponse<InventoryCountVO> approve(@PathVariable @Positive long id,@Valid @RequestBody InventoryCountRequests.Transition r,@AuthenticationPrincipal StockPilotPrincipal a){return ApiResponse.success(service.approve(id,r,a));}
    @PostMapping("/{id}/adjust") @PreAuthorize("hasAuthority('INVENTORY_COUNT_ADJUST')")
    public ApiResponse<InventoryCountVO> adjust(@PathVariable @Positive long id,@AuthenticationPrincipal StockPilotPrincipal a){return ApiResponse.success(service.adjust(id,a));}
}
