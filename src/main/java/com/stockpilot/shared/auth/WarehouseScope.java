package com.stockpilot.shared.auth;

import java.util.Set;

public record WarehouseScope(boolean allWarehouses, Set<Long> warehouseIds) {
    public WarehouseScope {
        warehouseIds = Set.copyOf(warehouseIds);
    }

    public static WarehouseScope unrestricted() {
        return new WarehouseScope(true, Set.of());
    }

    public boolean permits(long id) {
        return allWarehouses || warehouseIds.contains(id);
    }
}
