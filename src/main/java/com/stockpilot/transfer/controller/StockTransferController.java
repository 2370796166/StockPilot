package com.stockpilot.transfer.controller;

import com.stockpilot.masterdata.vo.PageResult;
import com.stockpilot.security.auth.StockPilotPrincipal;
import com.stockpilot.shared.api.ApiResponse;
import com.stockpilot.transfer.request.StockTransferRequests;
import com.stockpilot.transfer.service.StockTransferApplicationService;
import com.stockpilot.transfer.vo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/transfers")
public class StockTransferController {
    private final StockTransferApplicationService stockTransferService;

    public StockTransferController(StockTransferApplicationService stockTransferService) {
        this.stockTransferService = stockTransferService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TRANSFER_READ')")
    public ApiResponse<PageResult<StockTransferSummaryVO>> page(
            @Valid @ModelAttribute StockTransferRequests.PageQuery q) {
        return ApiResponse.success(stockTransferService.page(q));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TRANSFER_READ')")
    public ApiResponse<StockTransferVO> get(@PathVariable @Positive long id) {
        return ApiResponse.success(stockTransferService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TRANSFER_WRITE')")
    public ApiResponse<StockTransferVO> create(
            @Valid @RequestBody StockTransferRequests.Create r,
            @AuthenticationPrincipal StockPilotPrincipal a) {
        return ApiResponse.success(stockTransferService.create(r, a));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TRANSFER_WRITE')")
    public ApiResponse<StockTransferVO> update(
            @PathVariable @Positive long id,
            @Valid @RequestBody StockTransferRequests.Update r,
            @AuthenticationPrincipal StockPilotPrincipal a) {
        return ApiResponse.success(stockTransferService.update(id, r, a));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('TRANSFER_WRITE')")
    public ApiResponse<StockTransferVO> submit(
            @PathVariable @Positive long id,
            @Valid @RequestBody StockTransferRequests.Transition r,
            @AuthenticationPrincipal StockPilotPrincipal a) {
        return ApiResponse.success(stockTransferService.submit(id, r, a));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('TRANSFER_APPROVE')")
    public ApiResponse<StockTransferVO> approve(
            @PathVariable @Positive long id,
            @Valid @RequestBody StockTransferRequests.Transition r,
            @AuthenticationPrincipal StockPilotPrincipal a) {
        return ApiResponse.success(stockTransferService.approve(id, r, a));
    }

    @PostMapping("/{id}/dispatch")
    @PreAuthorize("hasAuthority('TRANSFER_OUTBOUND')")
    public ApiResponse<StockTransferVO> dispatch(
            @PathVariable @Positive long id, @AuthenticationPrincipal StockPilotPrincipal a) {
        return ApiResponse.success(stockTransferService.dispatch(id, a));
    }

    @PostMapping("/{id}/start-transit")
    @PreAuthorize("hasAuthority('TRANSFER_OUTBOUND')")
    public ApiResponse<StockTransferVO> startTransit(
            @PathVariable @Positive long id,
            @Valid @RequestBody StockTransferRequests.Transition r,
            @AuthenticationPrincipal StockPilotPrincipal a) {
        return ApiResponse.success(stockTransferService.startTransit(id, r, a));
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAuthority('TRANSFER_INBOUND')")
    public ApiResponse<StockTransferVO> receive(
            @PathVariable @Positive long id, @AuthenticationPrincipal StockPilotPrincipal a) {
        return ApiResponse.success(stockTransferService.receive(id, a));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('TRANSFER_WRITE')")
    public ApiResponse<StockTransferVO> cancel(
            @PathVariable @Positive long id, @AuthenticationPrincipal StockPilotPrincipal a) {
        return ApiResponse.success(stockTransferService.cancel(id, a));
    }
}
