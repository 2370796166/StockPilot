package com.stockpilot.masterdata.infrastructure.cache;

// 缓存开关关闭时使用的空实现，使业务服务无需感知 Redis 是否启用。
public final class NoOpReferenceDataCache implements ReferenceDataCache {
    public static final NoOpReferenceDataCache INSTANCE = new NoOpReferenceDataCache();

    private NoOpReferenceDataCache() {}

    @Override
    public <T> ReferenceCacheLookup<T> get(ReferenceCacheKind kind, long id, Class<T> valueType) {
        return ReferenceCacheLookup.miss();
    }

    @Override
    public void put(ReferenceCacheKind kind, long id, Object value) {}

    @Override
    public void putMissing(ReferenceCacheKind kind, long id) {}

    @Override
    public void evict(ReferenceCacheKind kind, long id) {}
}
