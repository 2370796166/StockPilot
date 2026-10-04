package com.stockpilot.masterdata.service;

import com.stockpilot.masterdata.domain.ProductCategoryEntity;
import com.stockpilot.masterdata.mapper.ProductCategoryMapper;
import org.springframework.stereotype.Service;

@Service
public class ProductCategoryApplicationService
        extends MasterDataApplicationService<ProductCategoryEntity> {
    public ProductCategoryApplicationService(ProductCategoryMapper mapper) {
        super(mapper, ProductCategoryEntity::new, "商品分类编码已存在");
    }
}
