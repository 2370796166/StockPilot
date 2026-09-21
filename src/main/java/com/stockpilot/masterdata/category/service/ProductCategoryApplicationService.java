package com.stockpilot.masterdata.category.service;

import com.stockpilot.masterdata.category.domain.ProductCategoryEntity;
import com.stockpilot.masterdata.category.mapper.ProductCategoryMapper;
import com.stockpilot.masterdata.service.MasterDataApplicationService;
import org.springframework.stereotype.Service;

@Service
public class ProductCategoryApplicationService
        extends MasterDataApplicationService<ProductCategoryEntity> {
    public ProductCategoryApplicationService(ProductCategoryMapper mapper) {
        super(mapper, ProductCategoryEntity::new, "商品分类编码已存在");
    }
}
