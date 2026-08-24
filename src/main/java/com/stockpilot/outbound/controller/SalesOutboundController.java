package com.stockpilot.outbound.controller;

import com.stockpilot.common.api.ApiResponse;
import com.stockpilot.masterdata.vo.PageResult;
import com.stockpilot.outbound.application.SalesOutboundApplicationService;
import com.stockpilot.outbound.request.SalesOutboundRequests;
import com.stockpilot.outbound.vo.SalesOutboundSummaryVO;
import com.stockpilot.outbound.vo.SalesOutboundVO;
import com.stockpilot.security.auth.StockPilotPrincipal;
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
    private final SalesOutboundApplicationService service;
    public SalesOutboundController(SalesOutboundApplicationService service) { this.service = service; }

    @PreAuthorize("hasAuthority('SALES_OUTBOUND_READ')") @GetMapping
    public ApiResponse<PageResult<SalesOutboundSummaryVO>> page(@Valid @ModelAttribute SalesOutboundRequests.PageQuery query) {
        return ApiResponse.success(service.page(query));
    }
    @PreAuthorize("hasAuthority('SALES_OUTBOUND_READ')") @GetMapping("/{id}")
    public ApiResponse<SalesOutboundVO> get(@PathVariable @Positive long id) { return ApiResponse.success(service.get(id)); }
    @PreAuthorize("hasAuthority('SALES_OUTBOUND_WRITE')") @PostMapping
    public ApiResponse<SalesOutboundVO> create(@Valid @RequestBody SalesOutboundRequests.Create request,
            @AuthenticationPrincipal StockPilotPrincipal actor) { return ApiResponse.success(service.create(request, actor)); }
    @PreAuthorize("hasAuthority('SALES_OUTBOUND_WRITE')") @PutMapping("/{id}")
    public ApiResponse<SalesOutboundVO> update(@PathVariable @Positive long id, @Valid @RequestBody SalesOutboundRequests.Update request,
            @AuthenticationPrincipal StockPilotPrincipal actor) { return ApiResponse.success(service.update(id, request, actor)); }
    @PreAuthorize("hasAuthority('SALES_OUTBOUND_WRITE')") @PostMapping("/{id}/reserve")
    public ApiResponse<SalesOutboundVO> reserve(@PathVariable @Positive long id, @Valid @RequestBody SalesOutboundRequests.Transition request,
            @AuthenticationPrincipal StockPilotPrincipal actor) { return ApiResponse.success(service.reserve(id, request, actor)); }
    @PreAuthorize("hasAuthority('SALES_OUTBOUND_APPROVE')") @PostMapping("/{id}/approve")
    public ApiResponse<SalesOutboundVO> approve(@PathVariable @Positive long id, @Valid @RequestBody SalesOutboundRequests.Transition request,
            @AuthenticationPrincipal StockPilotPrincipal actor) { return ApiResponse.success(service.approve(id, request, actor)); }
    @PreAuthorize("hasAuthority('SALES_OUTBOUND_COMPLETE')") @PostMapping("/{id}/complete")
    public ApiResponse<SalesOutboundVO> complete(@PathVariable @Positive long id,
            @AuthenticationPrincipal StockPilotPrincipal actor) { return ApiResponse.success(service.complete(id, actor)); }
    @PreAuthorize("hasAuthority('SALES_OUTBOUND_WRITE')") @PostMapping("/{id}/cancel")
    public ApiResponse<SalesOutboundVO> cancel(@PathVariable @Positive long id,
            @AuthenticationPrincipal StockPilotPrincipal actor) { return ApiResponse.success(service.cancel(id, actor)); }
}
