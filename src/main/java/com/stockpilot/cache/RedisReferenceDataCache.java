package com.stockpilot.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisReferenceDataCache implements ReferenceDataCache {
    private static final Logger log = LoggerFactory.getLogger(RedisReferenceDataCache.class);
    private static final int SCHEMA_VERSION = 1;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ReferenceCacheProperties properties;

    public RedisReferenceDataCache(
            StringRedisTemplate redis, ObjectMapper objectMapper, ReferenceCacheProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public <T> ReferenceCacheLookup<T> get(ReferenceCacheKind kind, long id, Class<T> valueType) {
        String key = key(kind, id);
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) return ReferenceCacheLookup.miss();
            CacheEnvelope envelope = objectMapper.readValue(json, CacheEnvelope.class);
            if (envelope.schemaVersion() != SCHEMA_VERSION) {
                evict(kind, id);
                return ReferenceCacheLookup.miss();
            }
            if (!envelope.found()) return ReferenceCacheLookup.missingValue();
            if (envelope.value() == null || envelope.value().isNull()) {
                evict(kind, id);
                return ReferenceCacheLookup.miss();
            }
            return ReferenceCacheLookup.found(objectMapper.treeToValue(envelope.value(), valueType));
        } catch (Exception exception) {
            log.debug("Redis reference cache read failed for key {}, falling back to MySQL", key, exception);
            return ReferenceCacheLookup.miss();
        }
    }

    @Override
    public void put(ReferenceCacheKind kind, long id, Object value) {
        write(kind, id, new CacheEnvelope(SCHEMA_VERSION, true, objectMapper.valueToTree(value)),
                properties.ttl(kind));
    }

    @Override
    public void putMissing(ReferenceCacheKind kind, long id) {
        write(kind, id, new CacheEnvelope(SCHEMA_VERSION, false, null), properties.getNullTtl());
    }

    @Override
    public void evict(ReferenceCacheKind kind, long id) {
        String key = key(kind, id);
        try {
            redis.delete(key);
        } catch (Exception exception) {
            log.debug("Redis reference cache eviction failed for key {}; MySQL write remains authoritative", key, exception);
        }
    }

    private void write(ReferenceCacheKind kind, long id, CacheEnvelope envelope, java.time.Duration ttl) {
        String key = key(kind, id);
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(envelope), ttl);
        } catch (Exception exception) {
            log.debug("Redis reference cache write failed for key {}; response still comes from MySQL", key, exception);
        }
    }

    String key(ReferenceCacheKind kind, long id) {
        return properties.getKeyPrefix() + ":" + kind.keySegment() + ":" + id;
    }

    private record CacheEnvelope(int schemaVersion, boolean found, JsonNode value) { }
}
