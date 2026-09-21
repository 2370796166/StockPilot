package com.stockpilot.masterdata.category.controller;

import com.stockpilot.masterdata.category.domain.ProductCategoryEntity;
import com.stockpilot.masterdata.category.service.ProductCategoryApplicationService;
import com.stockpilot.masterdata.controller.BaseMasterDataController;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Product Category")
@RestController
@RequestMapping("/api/master-data/categories")
public class ProductCategoryController extends BaseMasterDataController<ProductCategoryEntity> {
    public ProductCategoryController(ProductCategoryApplicationService productCategoryService) {
        super(productCategoryService);
    }
}
