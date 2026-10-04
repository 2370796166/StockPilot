package com.stockpilot.masterdata.controller;

import com.stockpilot.masterdata.domain.WarehouseEntity;
import com.stockpilot.masterdata.service.WarehouseApplicationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Warehouse")
@RestController
@RequestMapping("/api/master-data/warehouses")
public class WarehouseController extends BaseMasterDataController<WarehouseEntity> {
    public WarehouseController(WarehouseApplicationService warehouseService) {
        super(warehouseService);
    }
}
