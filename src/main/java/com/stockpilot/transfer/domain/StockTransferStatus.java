package com.stockpilot.transfer.domain;

public enum StockTransferStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    OUTBOUND_COMPLETED,
    IN_TRANSIT,
    COMPLETED,
    CANCELLED
}
