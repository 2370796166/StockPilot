package com.stockpilot.masterdata;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.masterdata.domain.*;
import com.stockpilot.masterdata.mapper.*;
import com.stockpilot.masterdata.service.MasterDataReferenceQueryService;
import java.util.*;
import org.junit.jupiter.api.Test;

class MasterDataReferenceQueryServiceTest {
    @Test
    void multipleReferencesUseOneQueryPerKindAndOnlyDisplayFields() throws Exception {
        var skus = mock(SkuMapper.class);
        var warehouses = mock(WarehouseMapper.class);
        var locations = mock(WarehouseLocationMapper.class);
        SkuEntity sku = new SkuEntity();
        sku.setId(1L);
        sku.setCode("S1");
        sku.setName("商品");
        sku.setUnit("件");
        sku.setRemark("private remark");
        WarehouseEntity warehouse = new WarehouseEntity();
        warehouse.setId(2L);
        warehouse.setCode("W1");
        warehouse.setName("仓库");
        WarehouseLocationEntity location = new WarehouseLocationEntity();
        location.setId(3L);
        location.setWarehouseId(2L);
        location.setCode("L1");
        location.setName("库位");
        when(skus.selectBatchIds(anyCollection())).thenReturn(List.of(sku));
        when(warehouses.selectBatchIds(anyCollection())).thenReturn(List.of(warehouse));
        when(locations.selectBatchIds(anyCollection())).thenReturn(List.of(location));
        var result =
                new MasterDataReferenceQueryService(skus, warehouses, locations)
                        .references(Set.of(1L, 4L), Set.of(2L), Set.of(3L, 5L));
        assertEquals("件", result.get("sku:1").unit());
        assertEquals(2L, result.get("location:3").warehouseId());
        assertFalse(new ObjectMapper().writeValueAsString(result).contains("remark"));
        verify(skus).selectBatchIds(Set.of(1L, 4L));
        verify(warehouses).selectBatchIds(Set.of(2L));
        verify(locations).selectBatchIds(Set.of(3L, 5L));
        verifyNoMoreInteractions(skus, warehouses, locations);
    }

    @Test
    void emptyAndInvalidScopesNeverReachDatabase() {
        var skus = mock(SkuMapper.class);
        var warehouses = mock(WarehouseMapper.class);
        var locations = mock(WarehouseLocationMapper.class);
        var service = new MasterDataReferenceQueryService(skus, warehouses, locations);
        assertTrue(service.references(Set.of(), Set.of(), Set.of()).isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () -> service.references(Set.of(0L), Set.of(), Set.of()));
        assertThrows(
                IllegalArgumentException.class, () -> service.references(null, Set.of(), Set.of()));
        Set<Long> oversized = new HashSet<>();
        for (long id = 1; id <= 129; id++) oversized.add(id);
        assertThrows(
                IllegalArgumentException.class,
                () -> service.references(oversized, Set.of(), Set.of()));
        verifyNoInteractions(skus, warehouses, locations);
    }
}
