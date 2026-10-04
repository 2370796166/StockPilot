package com.stockpilot.masterdata.service;

import com.stockpilot.masterdata.api.MasterDataErrorCode;
import com.stockpilot.masterdata.domain.MasterDataStatus;
import com.stockpilot.masterdata.domain.SkuEntity;
import com.stockpilot.masterdata.domain.WarehouseEntity;
import com.stockpilot.masterdata.domain.WarehouseLocationEntity;
import com.stockpilot.masterdata.mapper.SkuMapper;
import com.stockpilot.masterdata.mapper.WarehouseLocationMapper;
import com.stockpilot.masterdata.mapper.WarehouseMapper;
import com.stockpilot.shared.exception.BusinessException;
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

    // 校验可用于新库存业务的完整维度：仓库、库位和 SKU 必须存在且启用，库位还必须属于指定仓库。
    // 校验直接读取 MySQL，不依赖可能短暂陈旧的 Redis 缓存。
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
