package com.stockpilot.cache;

public final class NoOpReferenceDataCache implements ReferenceDataCache {
    public static final NoOpReferenceDataCache INSTANCE = new NoOpReferenceDataCache();

    private NoOpReferenceDataCache() {
    }

    @Override
    public <T> ReferenceCacheLookup<T> get(ReferenceCacheKind kind, long id, Class<T> valueType) {
        return ReferenceCacheLookup.miss();
    }

    @Override public void put(ReferenceCacheKind kind, long id, Object value) { }
    @Override public void putMissing(ReferenceCacheKind kind, long id) { }
    @Override public void evict(ReferenceCacheKind kind, long id) { }
}
