package com.stockpilot.messaging.domain;

public final class BusinessEventNames {
    public static final int VERSION_1 = 1;
    public static final String PURCHASE_RECEIPT_COMPLETED = "stockpilot.purchase-receipt.completed";
    public static final String SALES_OUTBOUND_COMPLETED = "stockpilot.sales-outbound.completed";
    public static final String PURCHASE_RECEIPT_ROUTING_KEY =
            "business.purchase-receipt.completed.v1";
    public static final String SALES_OUTBOUND_ROUTING_KEY = "business.sales-outbound.completed.v1";
    public static final String AVAILABILITY_ROUTING_KEY =
            "business.inventory.availability-changed.v1";

    public static String availabilityEventName(
            com.stockpilot.inventory.domain.InventoryAvailabilityChanged.Action action) {
        return switch (action) {
            case SALES_RESERVED -> "stockpilot.sales-outbound.reserved";
            case SALES_CANCELLED -> "stockpilot.sales-outbound.cancelled";
            case TRANSFER_SUBMITTED -> "stockpilot.transfer.submitted";
            case TRANSFER_CANCELLED -> "stockpilot.transfer.cancelled";
            case TRANSFER_RECEIVED -> "stockpilot.transfer.received";
            case COUNT_ADJUSTED -> "stockpilot.inventory-count.adjusted";
        };
    }

    private BusinessEventNames() {}

    public static boolean supportsInventoryChange(String eventName, int version) {
        return version == VERSION_1
                && (PURCHASE_RECEIPT_COMPLETED.equals(eventName)
                        || SALES_OUTBOUND_COMPLETED.equals(eventName)
                        || java.util.Arrays.stream(
                                        com.stockpilot.inventory.domain.InventoryAvailabilityChanged
                                                .Action.values())
                                .anyMatch(
                                        action -> availabilityEventName(action).equals(eventName)));
    }
}
