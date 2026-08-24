package com.stockpilot.masterdata;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stockpilot.cache.NoOpReferenceDataCache;
import com.stockpilot.common.exception.BusinessException;
import com.stockpilot.masterdata.domain.MasterDataStatus;
import com.stockpilot.masterdata.request.ChangeStatusRequest;
import com.stockpilot.masterdata.request.CreateMasterDataRequest;
import com.stockpilot.masterdata.request.PageQuery;
import com.stockpilot.masterdata.request.UpdateMasterDataRequest;
import com.stockpilot.masterdata.sku.application.SkuApplicationService;
import com.stockpilot.masterdata.sku.infrastructure.mapper.SkuMapper;
import com.stockpilot.masterdata.sku.request.CreateSkuRequest;
import com.stockpilot.masterdata.supplier.application.SupplierApplicationService;
import com.stockpilot.masterdata.supplier.infrastructure.mapper.SupplierMapper;
import com.stockpilot.masterdata.warehouse.application.WarehouseApplicationService;
import com.stockpilot.masterdata.warehouse.domain.WarehouseEntity;
import com.stockpilot.masterdata.warehouse.infrastructure.mapper.WarehouseMapper;
import com.stockpilot.masterdata.category.infrastructure.mapper.ProductCategoryMapper;
import com.stockpilot.masterdata.category.application.ProductCategoryApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MasterDataApplicationServiceTest {
    @Test
    void shouldCreateUpdateDetailPageAndChangeStatus() {
        WarehouseMapper mapper = mock(WarehouseMapper.class);
        WarehouseEntity stored = entity(1L, "WH01", "一号仓", MasterDataStatus.ENABLED, 0);
        when(mapper.insert(any())).thenAnswer(invocation -> {
            WarehouseEntity value = invocation.getArgument(0); value.setId(1L); return 1;
        });
        when(mapper.selectById(1L)).thenReturn(stored);
        when(mapper.updateById(any())).thenAnswer(invocation -> {
            WarehouseEntity value = invocation.getArgument(0);
            stored.setName(value.getName()); stored.setRemark(value.getRemark()); stored.setStatus(value.getStatus());
            stored.setVersion(value.getVersion() + 1); return 1;
        });
        when(mapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<WarehouseEntity> page = invocation.getArgument(0); page.setRecords(java.util.List.of(stored)); page.setTotal(1); return page;
        });
        WarehouseApplicationService service = new WarehouseApplicationService(mapper, NoOpReferenceDataCache.INSTANCE);

        assertEquals("WH01", service.create(new CreateMasterDataRequest("wh01", " 一号仓 ", null)).code());
        assertEquals("新名称", service.update(1, new UpdateMasterDataRequest(" 新名称 ", "备注", 0)).name());
        assertEquals("新名称", service.detail(1).name());
        assertEquals(1, service.page(new PageQuery()).total());
        assertEquals(MasterDataStatus.DISABLED,
                service.changeStatus(1, new ChangeStatusRequest(MasterDataStatus.DISABLED, 1)).status());
    }

    @Test
    void shouldReportWarehouseSkuAndSupplierDuplicateCodes() {
        WarehouseMapper warehouseMapper = mock(WarehouseMapper.class);
        when(warehouseMapper.insert(any())).thenThrow(new DuplicateKeyException("uk_warehouse_code"));
        BusinessException warehouse = assertThrows(BusinessException.class,
                () -> new WarehouseApplicationService(warehouseMapper, NoOpReferenceDataCache.INSTANCE).create(new CreateMasterDataRequest("WH01", "仓库", null)));
        assertEquals("仓库编码已存在", warehouse.getMessage());

        SkuMapper skuMapper = mock(SkuMapper.class);
        when(skuMapper.insert(any())).thenThrow(new DuplicateKeyException("uk_sku_code"));
        BusinessException sku = assertThrows(BusinessException.class,
                () -> new SkuApplicationService(skuMapper, mock(ProductCategoryMapper.class), NoOpReferenceDataCache.INSTANCE)
                        .create(new CreateSkuRequest("SKU01", "商品", null, "件", null)));
        assertEquals("SKU编码已存在", sku.getMessage());

        SupplierMapper supplierMapper = mock(SupplierMapper.class);
        when(supplierMapper.insert(any())).thenThrow(new DuplicateKeyException("uk_supplier_code"));
        BusinessException supplier = assertThrows(BusinessException.class,
                () -> new SupplierApplicationService(supplierMapper)
                        .create(new CreateMasterDataRequest("SUP01", "供应商", null)));
        assertEquals("供应商编码已存在", supplier.getMessage());

        ProductCategoryMapper categoryMapper = mock(ProductCategoryMapper.class);
        when(categoryMapper.insert(any())).thenThrow(new DuplicateKeyException("uk_product_category_code"));
        BusinessException category = assertThrows(BusinessException.class,
                () -> new ProductCategoryApplicationService(categoryMapper)
                        .create(new CreateMasterDataRequest("CAT01", "分类", null)));
        assertEquals("商品分类编码已存在", category.getMessage());
    }

    @Test
    void shouldReturnNotFoundForMissingDetail() {
        WarehouseMapper mapper = mock(WarehouseMapper.class);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> new WarehouseApplicationService(mapper, NoOpReferenceDataCache.INSTANCE).detail(999));
        assertEquals("MASTER_DATA_404", exception.getErrorCode().code());
    }

    private static WarehouseEntity entity(long id, String code, String name, MasterDataStatus status, int version) {
        WarehouseEntity entity = new WarehouseEntity(); entity.setId(id); entity.setCode(code); entity.setName(name);
        entity.setStatus(status); entity.setVersion(version); return entity;
    }
}
