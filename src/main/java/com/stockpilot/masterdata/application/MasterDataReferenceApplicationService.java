package com.stockpilot.masterdata.application;

import com.stockpilot.common.exception.BusinessException;
import com.stockpilot.masterdata.api.MasterDataErrorCode;
import com.stockpilot.masterdata.domain.MasterDataStatus;
import com.stockpilot.masterdata.location.domain.WarehouseLocationEntity;
import com.stockpilot.masterdata.location.infrastructure.mapper.WarehouseLocationMapper;
import com.stockpilot.masterdata.sku.domain.SkuEntity;
import com.stockpilot.masterdata.sku.infrastructure.mapper.SkuMapper;
import com.stockpilot.masterdata.warehouse.domain.WarehouseEntity;
import com.stockpilot.masterdata.warehouse.infrastructure.mapper.WarehouseMapper;
import org.springframework.stereotype.Service;

@Service
public class MasterDataReferenceApplicationService {
    private final WarehouseMapper warehouses;
    private final WarehouseLocationMapper locations;
    private final SkuMapper skus;

    public MasterDataReferenceApplicationService(
            WarehouseMapper warehouses, WarehouseLocationMapper locations, SkuMapper skus) {
        this.warehouses = warehouses;
        this.locations = locations;
        this.skus = skus;
    }

    public void requireEnabledInventoryDimension(long warehouseId, long locationId, long skuId) {
        WarehouseEntity warehouse = warehouses.selectById(warehouseId);
        if (warehouse == null) {
            throw new BusinessException(MasterDataErrorCode.WAREHOUSE_NOT_FOUND);
        }
        if (warehouse.getStatus() != MasterDataStatus.ENABLED) {
            throw new BusinessException(MasterDataErrorCode.WAREHOUSE_DISABLED);
        }

        WarehouseLocationEntity location = locations.selectById(locationId);
        if (location == null || !Long.valueOf(warehouseId).equals(location.getWarehouseId())) {
            throw new BusinessException(MasterDataErrorCode.NOT_FOUND, "库位不存在或不属于指定仓库");
        }
        if (location.getStatus() != MasterDataStatus.ENABLED) {
            throw new BusinessException(MasterDataErrorCode.NOT_FOUND, "库位已停用");
        }

        SkuEntity sku = skus.selectById(skuId);
        if (sku == null) {
            throw new BusinessException(MasterDataErrorCode.NOT_FOUND, "SKU不存在");
        }
        if (sku.getStatus() != MasterDataStatus.ENABLED) {
            throw new BusinessException(MasterDataErrorCode.NOT_FOUND, "SKU已停用");
        }
    }
}
