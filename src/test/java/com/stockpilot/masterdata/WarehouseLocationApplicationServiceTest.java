package com.stockpilot.masterdata;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.stockpilot.masterdata.domain.MasterDataStatus;
import com.stockpilot.masterdata.location.domain.WarehouseLocationEntity;
import com.stockpilot.masterdata.location.mapper.WarehouseLocationMapper;
import com.stockpilot.masterdata.location.request.CreateLocationRequest;
import com.stockpilot.masterdata.location.service.WarehouseLocationApplicationService;
import com.stockpilot.masterdata.warehouse.domain.WarehouseEntity;
import com.stockpilot.masterdata.warehouse.mapper.WarehouseMapper;
import com.stockpilot.shared.exception.BusinessException;
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
