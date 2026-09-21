package com.stockpilot.masterdata.infrastructure.cache;

public interface ReferenceDataCache {
    <T> ReferenceCacheLookup<T> get(ReferenceCacheKind kind, long id, Class<T> valueType);

    void put(ReferenceCacheKind kind, long id, Object value);

    void putMissing(ReferenceCacheKind kind, long id);

    void evict(ReferenceCacheKind kind, long id);
}
