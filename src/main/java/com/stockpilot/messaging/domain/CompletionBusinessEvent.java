package com.stockpilot.messaging.domain;

import java.time.Instant;
import java.util.List;

public record CompletionBusinessEvent(
        String messageId,
        String eventName,
        int eventVersion,
        String businessNo,
        Instant occurredAt,
        CompletionData data) {

    public record CompletionData(long documentId, long warehouseId, List<InventoryDimension> dimensions) {
        public CompletionData {
            dimensions = List.copyOf(dimensions);
        }
    }

    public record InventoryDimension(long locationId, long skuId) { }
}
