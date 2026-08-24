package com.stockpilot.inventory.domain;

public enum InventoryBusinessType {
    INITIALIZE,
    PURCHASE_RECEIPT,
    OUTBOUND_FREEZE,
    OUTBOUND_RELEASE,
    OUTBOUND_SHIP,
    TRANSFER_FREEZE,
    TRANSFER_RELEASE,
    TRANSFER_OUT,
    TRANSFER_IN,
    INVENTORY_COUNT,
    INVENTORY_GAIN,
    INVENTORY_LOSS
}
