package com.stockpilot.masterdata;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.stockpilot.masterdata.domain.MasterDataStatus;
import com.stockpilot.masterdata.domain.SkuEntity;
import com.stockpilot.masterdata.domain.WarehouseEntity;
import com.stockpilot.masterdata.domain.WarehouseLocationEntity;
import com.stockpilot.masterdata.mapper.SkuMapper;
import com.stockpilot.masterdata.mapper.WarehouseLocationMapper;
import com.stockpilot.masterdata.mapper.WarehouseMapper;
import com.stockpilot.masterdata.service.MasterDataReferenceApplicationService;
import com.stockpilot.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MasterDataReferenceApplicationServiceTest {
    private WarehouseMapper warehouses;
    private WarehouseLocationMapper locations;
    private SkuMapper skus;
    private MasterDataReferenceApplicationService service;

    @BeforeEach
    void setUp() {
        warehouses = mock(WarehouseMapper.class);
        locations = mock(WarehouseLocationMapper.class);
        skus = mock(SkuMapper.class);
        service = new MasterDataReferenceApplicationService(warehouses, locations, skus);
    }

    @Test
    void rejectsMissingWarehouseLocationAndSku() {
        assertThrows(
                BusinessException.class, () -> service.requireEnabledInventoryDimension(1, 2, 3));

        when(warehouses.selectById(1L)).thenReturn(warehouse(MasterDataStatus.ENABLED));
        assertThrows(
                BusinessException.class, () -> service.requireEnabledInventoryDimension(1, 2, 3));

        when(locations.selectById(2L)).thenReturn(location(1L, MasterDataStatus.ENABLED));
        assertThrows(
                BusinessException.class, () -> service.requireEnabledInventoryDimension(1, 2, 3));
    }

    @Test
    void rejectsDisabledWarehouseLocationAndSku() {
        when(warehouses.selectById(1L)).thenReturn(warehouse(MasterDataStatus.DISABLED));
        assertThrows(
                BusinessException.class, () -> service.requireEnabledInventoryDimension(1, 2, 3));

        when(warehouses.selectById(1L)).thenReturn(warehouse(MasterDataStatus.ENABLED));
        when(locations.selectById(2L)).thenReturn(location(1L, MasterDataStatus.DISABLED));
        assertThrows(
                BusinessException.class, () -> service.requireEnabledInventoryDimension(1, 2, 3));

        when(locations.selectById(2L)).thenReturn(location(1L, MasterDataStatus.ENABLED));
        when(skus.selectById(3L)).thenReturn(sku(MasterDataStatus.DISABLED));
        assertThrows(
                BusinessException.class, () -> service.requireEnabledInventoryDimension(1, 2, 3));
    }

    private WarehouseEntity warehouse(MasterDataStatus status) {
        WarehouseEntity entity = new WarehouseEntity();
        entity.setId(1L);
        entity.setStatus(status);
        return entity;
    }

    private WarehouseLocationEntity location(long warehouseId, MasterDataStatus status) {
        WarehouseLocationEntity entity = new WarehouseLocationEntity();
        entity.setId(2L);
        entity.setWarehouseId(warehouseId);
        entity.setStatus(status);
        return entity;
    }

    private SkuEntity sku(MasterDataStatus status) {
        SkuEntity entity = new SkuEntity();
        entity.setId(3L);
        entity.setStatus(status);
        return entity;
    }
}
