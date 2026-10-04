package com.stockpilot.masterdata.controller;

import com.stockpilot.masterdata.request.*;
import com.stockpilot.masterdata.request.ChangeStatusRequest;
import com.stockpilot.masterdata.service.WarehouseLocationApplicationService;
import com.stockpilot.masterdata.vo.LocationVO;
import com.stockpilot.shared.api.ApiResponse;
import com.stockpilot.shared.api.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/master-data/locations")
public class WarehouseLocationController {
    private final WarehouseLocationApplicationService warehouseLocationService;

    public WarehouseLocationController(
            WarehouseLocationApplicationService warehouseLocationService) {
        this.warehouseLocationService = warehouseLocationService;
    }

    @PreAuthorize("hasAuthority('MASTER_DATA_WRITE')")
    @PostMapping
    public ApiResponse<LocationVO> create(@Valid @RequestBody CreateLocationRequest r) {
        return ApiResponse.success(warehouseLocationService.create(r));
    }

    @PreAuthorize("hasAuthority('MASTER_DATA_WRITE')")
    @PutMapping("/{id}")
    public ApiResponse<LocationVO> update(
            @PathVariable @Positive long id, @Valid @RequestBody UpdateLocationRequest r) {
        return ApiResponse.success(warehouseLocationService.update(id, r));
    }

    @PreAuthorize("hasAuthority('MASTER_DATA_WRITE')")
    @PatchMapping("/{id}/status")
    public ApiResponse<LocationVO> status(
            @PathVariable @Positive long id, @Valid @RequestBody ChangeStatusRequest r) {
        return ApiResponse.success(warehouseLocationService.changeStatus(id, r));
    }

    @PreAuthorize("hasAuthority('MASTER_DATA_READ')")
    @GetMapping("/{id}")
    public ApiResponse<LocationVO> detail(@PathVariable @Positive long id) {
        return ApiResponse.success(warehouseLocationService.detail(id));
    }

    @PreAuthorize("hasAuthority('MASTER_DATA_READ')")
    @GetMapping
    public ApiResponse<PageResult<LocationVO>> page(@Valid @ModelAttribute LocationPageQuery q) {
        return ApiResponse.success(warehouseLocationService.page(q));
    }
}
