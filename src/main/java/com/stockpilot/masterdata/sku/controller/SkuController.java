package com.stockpilot.masterdata.sku.controller;

import com.stockpilot.masterdata.request.ChangeStatusRequest;
import com.stockpilot.masterdata.sku.request.*;
import com.stockpilot.masterdata.sku.service.SkuApplicationService;
import com.stockpilot.masterdata.sku.vo.SkuVO;
import com.stockpilot.masterdata.vo.PageResult;
import com.stockpilot.shared.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/master-data/skus")
public class SkuController {
    private final SkuApplicationService skuService;

    public SkuController(SkuApplicationService skuService) {
        this.skuService = skuService;
    }

    @PreAuthorize("hasAuthority('MASTER_DATA_WRITE')")
    @PostMapping
    public ApiResponse<SkuVO> create(@Valid @RequestBody CreateSkuRequest r) {
        return ApiResponse.success(skuService.create(r));
    }

    @PreAuthorize("hasAuthority('MASTER_DATA_WRITE')")
    @PutMapping("/{id}")
    public ApiResponse<SkuVO> update(
            @PathVariable @Positive long id, @Valid @RequestBody UpdateSkuRequest r) {
        return ApiResponse.success(skuService.update(id, r));
    }

    @PreAuthorize("hasAuthority('MASTER_DATA_WRITE')")
    @PatchMapping("/{id}/status")
    public ApiResponse<SkuVO> status(
            @PathVariable @Positive long id, @Valid @RequestBody ChangeStatusRequest r) {
        return ApiResponse.success(skuService.changeStatus(id, r));
    }

    @PreAuthorize("hasAuthority('MASTER_DATA_READ')")
    @GetMapping("/{id}")
    public ApiResponse<SkuVO> detail(@PathVariable @Positive long id) {
        return ApiResponse.success(skuService.detail(id));
    }

    @PreAuthorize("hasAuthority('MASTER_DATA_READ')")
    @GetMapping
    public ApiResponse<PageResult<SkuVO>> page(@Valid @ModelAttribute SkuPageQuery q) {
        return ApiResponse.success(skuService.page(q));
    }
}
