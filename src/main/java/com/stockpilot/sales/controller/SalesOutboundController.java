package com.stockpilot.sales.controller;

import com.stockpilot.masterdata.vo.PageResult;
import com.stockpilot.sales.request.SalesOutboundRequests;
import com.stockpilot.sales.service.SalesOutboundApplicationService;
import com.stockpilot.sales.vo.SalesOutboundSummaryVO;
import com.stockpilot.sales.vo.SalesOutboundVO;
import com.stockpilot.security.auth.StockPilotPrincipal;
import com.stockpilot.shared.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/outbound/sales-orders")
public class SalesOutboundController {
    private final SalesOutboundApplicationService salesOutboundService;

    public SalesOutboundController(SalesOutboundApplicationService salesOutboundService) {
        this.salesOutboundService = salesOutboundService;
    }

    @PreAuthorize("hasAuthority('SALES_OUTBOUND_READ')")
    @GetMapping
    public ApiResponse<PageResult<SalesOutboundSummaryVO>> page(
            @Valid @ModelAttribute SalesOutboundRequests.PageQuery query) {
        return ApiResponse.success(salesOutboundService.page(query));
    }

    @PreAuthorize("hasAuthority('SALES_OUTBOUND_READ')")
    @GetMapping("/{id}")
    public ApiResponse<SalesOutboundVO> get(@PathVariable @Positive long id) {
        return ApiResponse.success(salesOutboundService.get(id));
    }

    @PreAuthorize("hasAuthority('SALES_OUTBOUND_WRITE')")
    @PostMapping
    public ApiResponse<SalesOutboundVO> create(
            @Valid @RequestBody SalesOutboundRequests.Create request,
            @AuthenticationPrincipal StockPilotPrincipal actor) {
        return ApiResponse.success(salesOutboundService.create(request, actor));
    }

    @PreAuthorize("hasAuthority('SALES_OUTBOUND_WRITE')")
    @PutMapping("/{id}")
    public ApiResponse<SalesOutboundVO> update(
            @PathVariable @Positive long id,
            @Valid @RequestBody SalesOutboundRequests.Update request,
            @AuthenticationPrincipal StockPilotPrincipal actor) {
        return ApiResponse.success(salesOutboundService.update(id, request, actor));
    }

    @PreAuthorize("hasAuthority('SALES_OUTBOUND_WRITE')")
    @PostMapping("/{id}/reserve")
    public ApiResponse<SalesOutboundVO> reserve(
            @PathVariable @Positive long id,
            @Valid @RequestBody SalesOutboundRequests.Transition request,
            @AuthenticationPrincipal StockPilotPrincipal actor) {
        return ApiResponse.success(salesOutboundService.reserve(id, request, actor));
    }

    @PreAuthorize("hasAuthority('SALES_OUTBOUND_APPROVE')")
    @PostMapping("/{id}/approve")
    public ApiResponse<SalesOutboundVO> approve(
            @PathVariable @Positive long id,
            @Valid @RequestBody SalesOutboundRequests.Transition request,
            @AuthenticationPrincipal StockPilotPrincipal actor) {
        return ApiResponse.success(salesOutboundService.approve(id, request, actor));
    }

    @PreAuthorize("hasAuthority('SALES_OUTBOUND_COMPLETE')")
    @PostMapping("/{id}/complete")
    public ApiResponse<SalesOutboundVO> complete(
            @PathVariable @Positive long id, @AuthenticationPrincipal StockPilotPrincipal actor) {
        return ApiResponse.success(salesOutboundService.complete(id, actor));
    }

    @PreAuthorize("hasAuthority('SALES_OUTBOUND_WRITE')")
    @PostMapping("/{id}/cancel")
    public ApiResponse<SalesOutboundVO> cancel(
            @PathVariable @Positive long id, @AuthenticationPrincipal StockPilotPrincipal actor) {
        return ApiResponse.success(salesOutboundService.cancel(id, actor));
    }
}
