package com.stockpilot.masterdata.infrastructure.cache;

public enum ReferenceCacheKind {
    SKU("master:sku:id"),
    WAREHOUSE("master:warehouse:id");

    private final String keySegment;

    ReferenceCacheKind(String keySegment) {
        this.keySegment = keySegment;
    }

    public String keySegment() {
        return keySegment;
    }
}
