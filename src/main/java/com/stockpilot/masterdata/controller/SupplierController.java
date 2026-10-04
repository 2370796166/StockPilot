package com.stockpilot.masterdata.controller;

import com.stockpilot.masterdata.domain.SupplierEntity;
import com.stockpilot.masterdata.service.SupplierApplicationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Supplier")
@RestController
@RequestMapping("/api/master-data/suppliers")
public class SupplierController extends BaseMasterDataController<SupplierEntity> {
    public SupplierController(SupplierApplicationService supplierService) {
        super(supplierService);
    }
}
