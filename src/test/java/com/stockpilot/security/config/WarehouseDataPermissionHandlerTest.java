package com.stockpilot.security.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.stockpilot.masterdata.infrastructure.cache.ReferenceDataCache;
import com.stockpilot.masterdata.mapper.WarehouseMapper;
import com.stockpilot.masterdata.service.WarehouseApplicationService;
import com.stockpilot.shared.auth.WarehouseAccess;
import com.stockpilot.shared.auth.WarehouseScope;
import com.stockpilot.shared.exception.BusinessException;
import java.util.Set;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.schema.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WarehouseDataPermissionHandlerTest {
    @AfterEach
    void clear() {
        WarehouseAccess.clear();
    }

    @Test
    void emptyScopeDeniesRatherThanOmittingTheCondition() {
        WarehouseAccess.bind(new WarehouseScope(false, Set.of()));
        assertEquals(
                "1 = 0",
                new WarehouseDataPermissionHandler()
                        .getSqlSegment(new Table("inventory_balance"), null, "query")
                        .toString());
    }

    @Test
    void transferRequiresAccessToBothWarehousesAndUsesItsAlias() {
        WarehouseAccess.bind(new WarehouseScope(false, Set.of(7L, 3L)));
        var table = new Table("stock_transfer_order");
        table.setAlias(new Alias("o"));
        assertEquals(
                "o.source_warehouse_id IN (3, 7) AND o.target_warehouse_id IN (3, 7)",
                new WarehouseDataPermissionHandler()
                        .getSqlSegment(table, null, "query")
                        .toString());
    }

    @Test
    void anUnauthorizedWarehouseCannotBeReadFromAnAlreadyPopulatedCache() {
        WarehouseAccess.bind(new WarehouseScope(false, Set.of(3L)));
        var mapper = mock(WarehouseMapper.class);
        var cache = mock(ReferenceDataCache.class);
        var service = new WarehouseApplicationService(mapper, cache);
        assertThrows(BusinessException.class, () -> service.detail(7));
        verifyNoInteractions(cache, mapper);
    }
}
