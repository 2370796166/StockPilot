package com.stockpilot.masterdata.category.application;
import com.stockpilot.masterdata.application.MasterDataApplicationService;
import com.stockpilot.masterdata.category.domain.ProductCategoryEntity;
import com.stockpilot.masterdata.category.infrastructure.mapper.ProductCategoryMapper;
import org.springframework.stereotype.Service;
@Service
public class ProductCategoryApplicationService extends MasterDataApplicationService<ProductCategoryEntity> {
    public ProductCategoryApplicationService(ProductCategoryMapper mapper) { super(mapper, ProductCategoryEntity::new, "商品分类编码已存在"); }
}
