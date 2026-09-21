package com.stockpilot.masterdata.infrastructure.cache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.masterdata.domain.MasterDataStatus;
import com.stockpilot.masterdata.sku.vo.SkuVO;
import com.stockpilot.masterdata.vo.MasterDataVO;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisReferenceDataCacheTest {
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private RedisReferenceDataCache cache;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        ReferenceCacheProperties properties = new ReferenceCacheProperties();
        properties.setKeyPrefix("test:v1");
        properties.setSkuTtl(Duration.ofMinutes(20));
        properties.setWarehouseTtl(Duration.ofMinutes(40));
        properties.setNullTtl(Duration.ofSeconds(45));
        cache =
                new RedisReferenceDataCache(
                        redis, new ObjectMapper().findAndRegisterModules(), properties);
    }

    @Test
    void serializesVersionedJsonAndReadsItBack() {
        SkuVO value =
                new SkuVO(
                        7L,
                        "SKU07",
                        "商品",
                        null,
                        null,
                        "PCS",
                        MasterDataStatus.ENABLED,
                        null,
                        null,
                        null,
                        3);
        cache.put(ReferenceCacheKind.SKU, 7, value);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(values)
                .set(eq("test:v1:master:sku:id:7"), json.capture(), eq(Duration.ofMinutes(20)));
        assertTrue(json.getValue().contains("\"schemaVersion\":1"));
        assertTrue(json.getValue().contains("\"SKU07\""));

        when(values.get("test:v1:master:sku:id:7")).thenReturn(json.getValue());
        ReferenceCacheLookup<SkuVO> hit = cache.get(ReferenceCacheKind.SKU, 7, SkuVO.class);
        assertTrue(hit.hit());
        assertTrue(hit.found());
        assertEquals(value, hit.value());
    }

    @Test
    void usesIsolatedKeysAndConfiguredPositiveAndNullTtls() {
        cache.put(
                ReferenceCacheKind.WAREHOUSE,
                7,
                new MasterDataVO(7L, "WH07", "仓库", MasterDataStatus.ENABLED, null, null, null, 0));
        cache.putMissing(ReferenceCacheKind.SKU, 7);

        verify(values)
                .set(eq("test:v1:master:warehouse:id:7"), anyString(), eq(Duration.ofMinutes(40)));
        verify(values).set(eq("test:v1:master:sku:id:7"), anyString(), eq(Duration.ofSeconds(45)));
        assertNotEquals(
                cache.key(ReferenceCacheKind.SKU, 7), cache.key(ReferenceCacheKind.WAREHOUSE, 7));
    }

    @Test
    void redisFailureDegradesToMissAndNeverBreaksWritePath() {
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("offline"));
        doThrow(new RedisConnectionFailureException("offline"))
                .when(values)
                .set(anyString(), anyString(), any(Duration.class));
        doThrow(new RedisConnectionFailureException("offline")).when(redis).delete(anyString());

        assertFalse(cache.get(ReferenceCacheKind.SKU, 1, SkuVO.class).hit());
        assertDoesNotThrow(() -> cache.putMissing(ReferenceCacheKind.SKU, 1));
        assertDoesNotThrow(() -> cache.evict(ReferenceCacheKind.SKU, 1));
    }

    @Test
    void unknownSchemaVersionIsEvictedAndTreatedAsMiss() {
        String key = "test:v1:master:sku:id:8";
        when(values.get(key)).thenReturn("{\"schemaVersion\":2,\"found\":true,\"value\":{}}");

        assertFalse(cache.get(ReferenceCacheKind.SKU, 8, SkuVO.class).hit());
        verify(redis).delete(key);
    }
}
