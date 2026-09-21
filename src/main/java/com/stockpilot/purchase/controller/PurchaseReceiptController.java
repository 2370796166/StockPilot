package com.stockpilot.purchase.controller;

import com.stockpilot.masterdata.vo.PageResult;
import com.stockpilot.purchase.request.PurchaseReceiptRequests;
import com.stockpilot.purchase.service.PurchaseReceiptApplicationService;
import com.stockpilot.purchase.vo.PurchaseReceiptSummaryVO;
import com.stockpilot.purchase.vo.PurchaseReceiptVO;
import com.stockpilot.security.auth.StockPilotPrincipal;
import com.stockpilot.shared.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/inbound/purchase-receipts")
public class PurchaseReceiptController {
    private final PurchaseReceiptApplicationService purchaseReceiptService;

    public PurchaseReceiptController(PurchaseReceiptApplicationService purchaseReceiptService) {
        this.purchaseReceiptService = purchaseReceiptService;
    }

    @PreAuthorize("hasAuthority('PURCHASE_RECEIPT_READ')")
    @GetMapping
    public ApiResponse<PageResult<PurchaseReceiptSummaryVO>> page(
            @Valid @ModelAttribute PurchaseReceiptRequests.PageQuery query) {
        return ApiResponse.success(purchaseReceiptService.page(query));
    }

    @PreAuthorize("hasAuthority('PURCHASE_RECEIPT_READ')")
    @GetMapping("/{id}")
    public ApiResponse<PurchaseReceiptVO> get(@PathVariable @Positive long id) {
        return ApiResponse.success(purchaseReceiptService.get(id));
    }

    @PreAuthorize("hasAuthority('PURCHASE_RECEIPT_WRITE')")
    @PostMapping
    public ApiResponse<PurchaseReceiptVO> create(
            @Valid @RequestBody PurchaseReceiptRequests.Create request,
            @AuthenticationPrincipal StockPilotPrincipal actor) {
        return ApiResponse.success(purchaseReceiptService.create(request, actor));
    }

    @PreAuthorize("hasAuthority('PURCHASE_RECEIPT_WRITE')")
    @PutMapping("/{id}")
    public ApiResponse<PurchaseReceiptVO> update(
            @PathVariable @Positive long id,
            @Valid @RequestBody PurchaseReceiptRequests.Update request,
            @AuthenticationPrincipal StockPilotPrincipal actor) {
        return ApiResponse.success(purchaseReceiptService.update(id, request, actor));
    }

    @PreAuthorize("hasAuthority('PURCHASE_RECEIPT_WRITE')")
    @PostMapping("/{id}/submit")
    public ApiResponse<PurchaseReceiptVO> submit(
            @PathVariable @Positive long id,
            @Valid @RequestBody PurchaseReceiptRequests.Transition request,
            @AuthenticationPrincipal StockPilotPrincipal actor) {
        return ApiResponse.success(purchaseReceiptService.submit(id, request, actor));
    }

    @PreAuthorize("hasAuthority('PURCHASE_RECEIPT_APPROVE')")
    @PostMapping("/{id}/approve")
    public ApiResponse<PurchaseReceiptVO> approve(
            @PathVariable @Positive long id,
            @Valid @RequestBody PurchaseReceiptRequests.Transition request,
            @AuthenticationPrincipal StockPilotPrincipal actor) {
        return ApiResponse.success(purchaseReceiptService.approve(id, request, actor));
    }

    @PreAuthorize("hasAuthority('PURCHASE_RECEIPT_COMPLETE')")
    @PostMapping("/{id}/complete")
    public ApiResponse<PurchaseReceiptVO> complete(
            @PathVariable @Positive long id, @AuthenticationPrincipal StockPilotPrincipal actor) {
        return ApiResponse.success(purchaseReceiptService.complete(id, actor));
    }
}
