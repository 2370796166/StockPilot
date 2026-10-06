package com.stockpilot.inventory.domain;

import java.util.List;

/** In-process transaction signal, not an asynchronous inventory mutation. */
public record InventoryAvailabilityChanged(
        Action action,
        long documentId,
        String businessNo,
        long warehouseId,
        List<Dimension> dimensions) {
    public InventoryAvailabilityChanged {
        dimensions = List.copyOf(dimensions);
    }

    public enum Action {
        SALES_RESERVED,
        SALES_CANCELLED,
        TRANSFER_SUBMITTED,
        TRANSFER_CANCELLED,
        TRANSFER_RECEIVED,
        COUNT_ADJUSTED
    }

    public record Dimension(long locationId, long skuId) {}
}
