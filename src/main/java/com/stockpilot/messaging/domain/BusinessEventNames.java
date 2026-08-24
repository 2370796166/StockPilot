package com.stockpilot.messaging.domain;

public final class BusinessEventNames {
    public static final int VERSION_1 = 1;
    public static final String PURCHASE_RECEIPT_COMPLETED = "stockpilot.purchase-receipt.completed";
    public static final String SALES_OUTBOUND_COMPLETED = "stockpilot.sales-outbound.completed";
    public static final String PURCHASE_RECEIPT_ROUTING_KEY = "business.purchase-receipt.completed.v1";
    public static final String SALES_OUTBOUND_ROUTING_KEY = "business.sales-outbound.completed.v1";

    private BusinessEventNames() { }

    public static boolean supportsCompletion(String eventName, int version) {
        return version == VERSION_1 && (PURCHASE_RECEIPT_COMPLETED.equals(eventName)
                || SALES_OUTBOUND_COMPLETED.equals(eventName));
    }
}
