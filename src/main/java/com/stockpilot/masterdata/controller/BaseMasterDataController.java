package com.stockpilot.masterdata.controller;

import com.stockpilot.masterdata.domain.BaseMasterDataEntity;
import com.stockpilot.masterdata.request.ChangeStatusRequest;
import com.stockpilot.masterdata.request.CreateMasterDataRequest;
import com.stockpilot.masterdata.request.PageQuery;
import com.stockpilot.masterdata.request.UpdateMasterDataRequest;
import com.stockpilot.masterdata.service.MasterDataApplicationService;
import com.stockpilot.masterdata.vo.MasterDataVO;
import com.stockpilot.masterdata.vo.PageResult;
import com.stockpilot.shared.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
public abstract class BaseMasterDataController<T extends BaseMasterDataEntity> {
    private final MasterDataApplicationService<T> masterDataService;

    protected BaseMasterDataController(MasterDataApplicationService<T> masterDataService) {
        this.masterDataService = masterDataService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MASTER_DATA_WRITE')")
    public ApiResponse<MasterDataVO> create(@Valid @RequestBody CreateMasterDataRequest request) {
        return ApiResponse.success(masterDataService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_DATA_WRITE')")
    public ApiResponse<MasterDataVO> update(
            @PathVariable @Positive long id, @Valid @RequestBody UpdateMasterDataRequest request) {
        return ApiResponse.success(masterDataService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('MASTER_DATA_WRITE')")
    public ApiResponse<MasterDataVO> changeStatus(
            @PathVariable @Positive long id, @Valid @RequestBody ChangeStatusRequest request) {
        return ApiResponse.success(masterDataService.changeStatus(id, request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_DATA_READ')")
    public ApiResponse<MasterDataVO> detail(@PathVariable @Positive long id) {
        return ApiResponse.success(masterDataService.detail(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MASTER_DATA_READ')")
    public ApiResponse<PageResult<MasterDataVO>> page(@Valid @ModelAttribute PageQuery query) {
        return ApiResponse.success(masterDataService.page(query));
    }
}
