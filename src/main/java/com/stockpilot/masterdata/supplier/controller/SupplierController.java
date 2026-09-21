package com.stockpilot.masterdata.supplier.controller;

import com.stockpilot.masterdata.controller.BaseMasterDataController;
import com.stockpilot.masterdata.supplier.domain.SupplierEntity;
import com.stockpilot.masterdata.supplier.service.SupplierApplicationService;
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
