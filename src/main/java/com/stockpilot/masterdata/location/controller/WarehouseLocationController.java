package com.stockpilot.masterdata.location.controller;
import com.stockpilot.common.api.ApiResponse;
import com.stockpilot.masterdata.location.application.WarehouseLocationApplicationService;
import com.stockpilot.masterdata.location.request.*;
import com.stockpilot.masterdata.location.vo.LocationVO;
import com.stockpilot.masterdata.request.ChangeStatusRequest;
import com.stockpilot.masterdata.vo.PageResult;
import jakarta.validation.Valid; import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated; import org.springframework.web.bind.annotation.*;
@Validated @RestController @RequestMapping("/api/master-data/locations")
public class WarehouseLocationController {
 private final WarehouseLocationApplicationService service; public WarehouseLocationController(WarehouseLocationApplicationService s){service=s;}
 @PostMapping public ApiResponse<LocationVO> create(@Valid @RequestBody CreateLocationRequest r){return ApiResponse.success(service.create(r));}
 @PutMapping("/{id}") public ApiResponse<LocationVO> update(@PathVariable @Positive long id,@Valid @RequestBody UpdateLocationRequest r){return ApiResponse.success(service.update(id,r));}
 @PatchMapping("/{id}/status") public ApiResponse<LocationVO> status(@PathVariable @Positive long id,@Valid @RequestBody ChangeStatusRequest r){return ApiResponse.success(service.changeStatus(id,r));}
 @GetMapping("/{id}") public ApiResponse<LocationVO> detail(@PathVariable @Positive long id){return ApiResponse.success(service.detail(id));}
 @GetMapping public ApiResponse<PageResult<LocationVO>> page(@Valid @ModelAttribute LocationPageQuery q){return ApiResponse.success(service.page(q));}
}
