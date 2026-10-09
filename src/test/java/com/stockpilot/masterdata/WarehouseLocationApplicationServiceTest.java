package com.stockpilot.masterdata;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stockpilot.masterdata.domain.MasterDataStatus;
import com.stockpilot.masterdata.domain.WarehouseEntity;
import com.stockpilot.masterdata.domain.WarehouseLocationEntity;
import com.stockpilot.masterdata.mapper.WarehouseLocationMapper;
import com.stockpilot.masterdata.mapper.WarehouseMapper;
import com.stockpilot.masterdata.request.CreateLocationRequest;
import com.stockpilot.masterdata.request.LocationPageQuery;
import com.stockpilot.masterdata.service.WarehouseLocationApplicationService;
import com.stockpilot.shared.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class WarehouseLocationApplicationServiceTest {
    @Test
    void shouldRejectMissingAndDisabledWarehouse() {
        WarehouseMapper warehouses = mock(WarehouseMapper.class);
        WarehouseLocationApplicationService service =
                new WarehouseLocationApplicationService(
                        mock(WarehouseLocationMapper.class), warehouses);
        assertEquals(
                "WAREHOUSE_404",
                assertThrows(BusinessException.class, () -> service.create(request(1L)))
                        .getErrorCode()
                        .code());
        WarehouseEntity disabled = warehouse(2L, "WH02", MasterDataStatus.DISABLED);
        when(warehouses.selectById(2L)).thenReturn(disabled);
        assertEquals(
                "WAREHOUSE_409",
                assertThrows(BusinessException.class, () -> service.create(request(2L)))
                        .getErrorCode()
                        .code());
    }

    @Test
    void shouldTranslateSameWarehouseDuplicateButAllowSameCodeInDifferentWarehouses() {
        WarehouseMapper warehouses = mock(WarehouseMapper.class);
        when(warehouses.selectById(1L)).thenReturn(warehouse(1L, "WH01", MasterDataStatus.ENABLED));
        when(warehouses.selectById(2L)).thenReturn(warehouse(2L, "WH02", MasterDataStatus.ENABLED));
        WarehouseLocationMapper duplicateMapper = mock(WarehouseLocationMapper.class);
        when(duplicateMapper.insert(any()))
                .thenThrow(new DuplicateKeyException("uk_location_warehouse_code"));
        WarehouseLocationApplicationService duplicateService =
                new WarehouseLocationApplicationService(duplicateMapper, warehouses);
        assertEquals(
                "该仓库中的库位编码已存在",
                assertThrows(BusinessException.class, () -> duplicateService.create(request(1L)))
                        .getMessage());

        WarehouseLocationMapper mapper = mock(WarehouseLocationMapper.class);
        java.util.Map<Long, WarehouseLocationEntity> values = new java.util.HashMap<>();
        when(mapper.insert(any()))
                .thenAnswer(
                        invocation -> {
                            WarehouseLocationEntity e = invocation.getArgument(0);
                            long id = values.size() + 1;
                            e.setId(id);
                            e.setVersion(0);
                            values.put(id, e);
                            return 1;
                        });
        when(mapper.selectById(anyLong()))
                .thenAnswer(invocation -> values.get(invocation.<Long>getArgument(0)));
        WarehouseLocationApplicationService service =
                new WarehouseLocationApplicationService(mapper, warehouses);
        assertEquals("A01", service.create(request(1L)).code());
        assertEquals("A01", service.create(request(2L)).code());
        assertEquals(2, values.size());
    }

    private static CreateLocationRequest request(long warehouseId) {
        return new CreateLocationRequest(warehouseId, "A01", "一号库位", null);
    }

    @Test
    void locationPageBatchesDistinctWarehousesAndPreservesOrderAndMissingReferences() {
        var locations = mock(WarehouseLocationMapper.class);
        var warehouses = mock(WarehouseMapper.class);
        var records = List.of(location(4, 1), location(3, 1), location(2, 2), location(1, 3));
        var page = new Page<WarehouseLocationEntity>(2, 4, 12);
        page.setRecords(records);
        when(locations.selectPage(any(Page.class), any())).thenReturn(page);
        when(warehouses.selectBatchIds(List.of(1L, 2L, 3L)))
                .thenReturn(
                        List.of(
                                warehouse(2, "WH02", MasterDataStatus.DISABLED),
                                warehouse(1, "WH01", MasterDataStatus.ENABLED)));
        var query = new LocationPageQuery();
        query.setPage(2);
        query.setSize(4);
        var result = new WarehouseLocationApplicationService(locations, warehouses).page(query);
        assertEquals(12, result.total());
        assertEquals(2, result.page());
        assertEquals(4, result.size());
        assertEquals(
                List.of(4L, 3L, 2L, 1L),
                result.records().stream().map(value -> value.id()).toList());
        assertEquals("WH01", result.records().get(0).warehouseCode());
        assertEquals("WH01", result.records().get(1).warehouseCode());
        assertEquals("WH02", result.records().get(2).warehouseCode());
        assertNull(result.records().get(3).warehouseCode());
        assertEquals("库位4", result.records().get(0).name());
        assertEquals(0, result.records().get(0).version());
        verify(warehouses, times(1)).selectBatchIds(List.of(1L, 2L, 3L));
        verify(warehouses, never()).selectById(anyLong());
    }

    @Test
    void emptyLocationPageDoesNotReadWarehouseReferences() {
        var locations = mock(WarehouseLocationMapper.class);
        var warehouses = mock(WarehouseMapper.class);
        when(locations.selectPage(any(Page.class), any()))
                .thenReturn(new Page<WarehouseLocationEntity>(1, 20, 0));
        assertTrue(
                new WarehouseLocationApplicationService(locations, warehouses)
                        .page(new LocationPageQuery())
                        .records()
                        .isEmpty());
        verifyNoInteractions(warehouses);
    }

    private static WarehouseLocationEntity location(long id, long warehouseId) {
        var value = new WarehouseLocationEntity();
        value.setId(id);
        value.setWarehouseId(warehouseId);
        value.setCode("L" + id);
        value.setName("库位" + id);
        value.setVersion(0);
        return value;
    }

    private static WarehouseEntity warehouse(long id, String code, MasterDataStatus status) {
        WarehouseEntity e = new WarehouseEntity();
        e.setId(id);
        e.setCode(code);
        e.setName(code);
        e.setStatus(status);
        e.setVersion(0);
        return e;
    }
}
