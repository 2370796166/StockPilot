package com.stockpilot.inventory.request;

/** Restricted single-product, single-warehouse read scope. */
public record InventoryDimensionQuery(long skuId, long warehouseId, Long locationId) {
    public InventoryDimensionQuery {
        if (skuId <= 0 || warehouseId <= 0 || (locationId != null && locationId <= 0))
            throw new IllegalArgumentException("Invalid inventory dimension");
    }

    public static void validatePage(long page, long size) {
        if (page < 1 || page > 1000 || size < 1 || size > 20)
            throw new IllegalArgumentException("Restricted inventory source page");
    }
}
