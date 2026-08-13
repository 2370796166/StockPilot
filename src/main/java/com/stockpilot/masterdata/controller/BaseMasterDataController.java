package com.stockpilot.masterdata.controller;

import com.stockpilot.common.api.ApiResponse;
import com.stockpilot.masterdata.application.MasterDataApplicationService;
import com.stockpilot.masterdata.domain.BaseMasterDataEntity;
import com.stockpilot.masterdata.request.ChangeStatusRequest;
import com.stockpilot.masterdata.request.CreateMasterDataRequest;
import com.stockpilot.masterdata.request.PageQuery;
import com.stockpilot.masterdata.request.UpdateMasterDataRequest;
import com.stockpilot.masterdata.vo.MasterDataVO;
import com.stockpilot.masterdata.vo.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@Validated
public abstract class BaseMasterDataController<T extends BaseMasterDataEntity> {
    private final MasterDataApplicationService<T> service;
    protected BaseMasterDataController(MasterDataApplicationService<T> service) { this.service = service; }

    @PostMapping @PreAuthorize("hasAuthority('MASTER_DATA_WRITE')")
    public ApiResponse<MasterDataVO> create(@Valid @RequestBody CreateMasterDataRequest request) {
        return ApiResponse.success(service.create(request));
    }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('MASTER_DATA_WRITE')")
    public ApiResponse<MasterDataVO> update(@PathVariable @Positive long id,
                                            @Valid @RequestBody UpdateMasterDataRequest request) {
        return ApiResponse.success(service.update(id, request));
    }
    @PatchMapping("/{id}/status") @PreAuthorize("hasAuthority('MASTER_DATA_WRITE')")
    public ApiResponse<MasterDataVO> changeStatus(@PathVariable @Positive long id,
                                                   @Valid @RequestBody ChangeStatusRequest request) {
        return ApiResponse.success(service.changeStatus(id, request));
    }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('MASTER_DATA_READ')")
    public ApiResponse<MasterDataVO> detail(@PathVariable @Positive long id) {
        return ApiResponse.success(service.detail(id));
    }
    @GetMapping @PreAuthorize("hasAuthority('MASTER_DATA_READ')")
    public ApiResponse<PageResult<MasterDataVO>> page(@Valid @ModelAttribute PageQuery query) {
        return ApiResponse.success(service.page(query));
    }
}
