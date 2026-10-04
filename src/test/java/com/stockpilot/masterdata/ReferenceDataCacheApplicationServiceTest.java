package com.stockpilot.masterdata;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.stockpilot.masterdata.domain.MasterDataStatus;
import com.stockpilot.masterdata.domain.ProductCategoryEntity;
import com.stockpilot.masterdata.domain.SkuEntity;
import com.stockpilot.masterdata.infrastructure.cache.*;
import com.stockpilot.masterdata.mapper.ProductCategoryMapper;
import com.stockpilot.masterdata.mapper.SkuMapper;
import com.stockpilot.masterdata.request.UpdateSkuRequest;
import com.stockpilot.masterdata.service.SkuApplicationService;
import com.stockpilot.shared.exception.BusinessException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReferenceDataCacheApplicationServiceTest {
    @Test
    void firstDetailMissesAndSecondDetailHitsCache() {
        SkuMapper mapper = mock(SkuMapper.class);
        when(mapper.selectById(1L)).thenReturn(sku(1L, "旧名称", 0));
        InMemoryCache cache = new InMemoryCache();
        SkuApplicationService service =
                new SkuApplicationService(mapper, mock(ProductCategoryMapper.class), cache);

        assertEquals("旧名称", service.detail(1).name());
        assertEquals("旧名称", service.detail(1).name());
        verify(mapper, times(1)).selectById(1L);
    }

    @Test
    void successfulUpdateEvictsCachedDetail() {
        SkuMapper mapper = mock(SkuMapper.class);
        SkuEntity stored = sku(1L, "旧名称", 0);
        when(mapper.selectById(1L)).thenReturn(stored);
        when(mapper.updateById(any()))
                .thenAnswer(
                        invocation -> {
                            SkuEntity changed = invocation.getArgument(0);
                            stored.setName(changed.getName());
                            stored.setUnit(changed.getUnit());
                            stored.setVersion(1);
                            return 1;
                        });
        InMemoryCache cache = new InMemoryCache();
        SkuApplicationService service =
                new SkuApplicationService(mapper, mock(ProductCategoryMapper.class), cache);
        service.detail(1);

        service.update(1, new UpdateSkuRequest("新名称", null, "PCS", null, 0));
        assertTrue(cache.evicted);
        assertEquals("新名称", service.detail(1).name());
        verify(mapper, atLeast(3)).selectById(1L);
    }

    @Test
    void missingDetailUsesShortLivedNegativeEntrySemantics() {
        SkuMapper mapper = mock(SkuMapper.class);
        InMemoryCache cache = new InMemoryCache();
        SkuApplicationService service =
                new SkuApplicationService(mapper, mock(ProductCategoryMapper.class), cache);

        assertThrows(BusinessException.class, () -> service.detail(99));
        assertThrows(BusinessException.class, () -> service.detail(99));
        verify(mapper, times(1)).selectById(99L);
    }

    @Test
    void cachedSkuUsesCurrentCategoryNameInsteadOfCachingDerivedField() {
        SkuMapper mapper = mock(SkuMapper.class);
        SkuEntity sku = sku(1L, "商品", 0);
        sku.setCategoryId(10L);
        when(mapper.selectById(1L)).thenReturn(sku);
        ProductCategoryMapper categories = mock(ProductCategoryMapper.class);
        when(categories.selectById(10L)).thenReturn(category("旧分类"), category("新分类"));
        InMemoryCache cache = new InMemoryCache();
        SkuApplicationService service = new SkuApplicationService(mapper, categories, cache);

        assertEquals("旧分类", service.detail(1).categoryName());
        assertEquals("新分类", service.detail(1).categoryName());
        verify(mapper, times(1)).selectById(1L);
        verify(categories, times(2)).selectById(10L);
    }

    private static SkuEntity sku(long id, String name, int version) {
        SkuEntity value = new SkuEntity();
        value.setId(id);
        value.setCode("SKU" + id);
        value.setName(name);
        value.setUnit("PCS");
        value.setStatus(MasterDataStatus.ENABLED);
        value.setVersion(version);
        return value;
    }

    private static ProductCategoryEntity category(String name) {
        ProductCategoryEntity value = new ProductCategoryEntity();
        value.setId(10L);
        value.setCode("CAT10");
        value.setName(name);
        value.setStatus(MasterDataStatus.ENABLED);
        value.setVersion(0);
        return value;
    }

    private static final class InMemoryCache implements ReferenceDataCache {
        private final Map<String, ReferenceCacheLookup<?>> values = new HashMap<>();
        private boolean evicted;

        @Override
        @SuppressWarnings("unchecked")
        public <T> ReferenceCacheLookup<T> get(
                ReferenceCacheKind kind, long id, Class<T> valueType) {
            return (ReferenceCacheLookup<T>)
                    values.getOrDefault(key(kind, id), ReferenceCacheLookup.miss());
        }

        @Override
        public void put(ReferenceCacheKind kind, long id, Object value) {
            values.put(key(kind, id), ReferenceCacheLookup.found(value));
        }

        @Override
        public void putMissing(ReferenceCacheKind kind, long id) {
            values.put(key(kind, id), ReferenceCacheLookup.missingValue());
        }

        @Override
        public void evict(ReferenceCacheKind kind, long id) {
            evicted = true;
            values.remove(key(kind, id));
        }

        private String key(ReferenceCacheKind kind, long id) {
            return kind.name() + ":" + id;
        }
    }
}
