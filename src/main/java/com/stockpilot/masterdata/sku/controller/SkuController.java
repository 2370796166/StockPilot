package com.stockpilot.masterdata.sku.controller;
import com.stockpilot.common.api.ApiResponse; import com.stockpilot.masterdata.request.ChangeStatusRequest;
import com.stockpilot.masterdata.sku.application.SkuApplicationService; import com.stockpilot.masterdata.sku.request.*;
import com.stockpilot.masterdata.sku.vo.SkuVO; import com.stockpilot.masterdata.vo.PageResult;
import jakarta.validation.Valid;import jakarta.validation.constraints.Positive;import org.springframework.validation.annotation.Validated;import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@Validated @RestController @RequestMapping("/api/master-data/skus") public class SkuController{
 private final SkuApplicationService service;public SkuController(SkuApplicationService s){service=s;}
 @PreAuthorize("hasAuthority('MASTER_DATA_WRITE')") @PostMapping public ApiResponse<SkuVO> create(@Valid @RequestBody CreateSkuRequest r){return ApiResponse.success(service.create(r));}
 @PreAuthorize("hasAuthority('MASTER_DATA_WRITE')") @PutMapping("/{id}")public ApiResponse<SkuVO> update(@PathVariable @Positive long id,@Valid @RequestBody UpdateSkuRequest r){return ApiResponse.success(service.update(id,r));}
 @PreAuthorize("hasAuthority('MASTER_DATA_WRITE')") @PatchMapping("/{id}/status")public ApiResponse<SkuVO> status(@PathVariable @Positive long id,@Valid @RequestBody ChangeStatusRequest r){return ApiResponse.success(service.changeStatus(id,r));}
 @PreAuthorize("hasAuthority('MASTER_DATA_READ')") @GetMapping("/{id}")public ApiResponse<SkuVO> detail(@PathVariable @Positive long id){return ApiResponse.success(service.detail(id));}
 @PreAuthorize("hasAuthority('MASTER_DATA_READ')") @GetMapping public ApiResponse<PageResult<SkuVO>> page(@Valid @ModelAttribute SkuPageQuery q){return ApiResponse.success(service.page(q));}
}
