package com.stockpilot.masterdata.sku.controller;
import com.stockpilot.common.api.ApiResponse; import com.stockpilot.masterdata.request.ChangeStatusRequest;
import com.stockpilot.masterdata.sku.application.SkuApplicationService; import com.stockpilot.masterdata.sku.request.*;
import com.stockpilot.masterdata.sku.vo.SkuVO; import com.stockpilot.masterdata.vo.PageResult;
import jakarta.validation.Valid;import jakarta.validation.constraints.Positive;import org.springframework.validation.annotation.Validated;import org.springframework.web.bind.annotation.*;
@Validated @RestController @RequestMapping("/api/master-data/skus") public class SkuController{
 private final SkuApplicationService service;public SkuController(SkuApplicationService s){service=s;}
 @PostMapping public ApiResponse<SkuVO> create(@Valid @RequestBody CreateSkuRequest r){return ApiResponse.success(service.create(r));}
 @PutMapping("/{id}")public ApiResponse<SkuVO> update(@PathVariable @Positive long id,@Valid @RequestBody UpdateSkuRequest r){return ApiResponse.success(service.update(id,r));}
 @PatchMapping("/{id}/status")public ApiResponse<SkuVO> status(@PathVariable @Positive long id,@Valid @RequestBody ChangeStatusRequest r){return ApiResponse.success(service.changeStatus(id,r));}
 @GetMapping("/{id}")public ApiResponse<SkuVO> detail(@PathVariable @Positive long id){return ApiResponse.success(service.detail(id));}
 @GetMapping public ApiResponse<PageResult<SkuVO>> page(@Valid @ModelAttribute SkuPageQuery q){return ApiResponse.success(service.page(q));}
}
