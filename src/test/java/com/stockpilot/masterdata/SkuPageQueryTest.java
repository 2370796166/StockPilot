package com.stockpilot.masterdata;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stockpilot.masterdata.domain.ProductCategoryEntity;
import com.stockpilot.masterdata.domain.SkuEntity;
import com.stockpilot.masterdata.infrastructure.cache.ReferenceDataCache;
import com.stockpilot.masterdata.mapper.ProductCategoryMapper;
import com.stockpilot.masterdata.mapper.SkuMapper;
import com.stockpilot.masterdata.request.SkuPageQuery;
import com.stockpilot.masterdata.service.SkuApplicationService;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkuPageQueryTest {
    @Test
    void resolvesRepeatedMissingAndNullCategoriesInOneBatchWithoutChangingPagination() {
        SkuMapper skus = mock(SkuMapper.class);
        ProductCategoryMapper categories = mock(ProductCategoryMapper.class);
        Page<SkuEntity> page = new Page<>(2, 4, 12);
        page.setRecords(List.of(sku(1, 10L), sku(2, 10L), sku(3, null), sku(4, 20L)));
        when(skus.selectPage(any(Page.class), any())).thenReturn(page);
        ProductCategoryEntity category = new ProductCategoryEntity();
        category.setId(10L);
        category.setName("当前分类");
        when(categories.selectBatchIds(List.of(10L, 20L))).thenReturn(List.of(category));
        var result =
                new SkuApplicationService(skus, categories, mock(ReferenceDataCache.class))
                        .page(new SkuPageQuery());
        assertEquals(12, result.total());
        assertEquals(2, result.page());
        assertEquals(4, result.size());
        assertEquals(List.of(1L, 2L, 3L, 4L), result.records().stream().map(x -> x.id()).toList());
        assertEquals("当前分类", result.records().get(0).categoryName());
        assertEquals("当前分类", result.records().get(1).categoryName());
        assertNull(result.records().get(2).categoryName());
        assertNull(result.records().get(3).categoryName());
        verify(categories).selectBatchIds(List.of(10L, 20L));
        verifyNoMoreInteractions(categories);
    }

    @Test
    void emptyAndUncategorizedPagesDoNotQueryCategories() {
        SkuMapper skus = mock(SkuMapper.class);
        ProductCategoryMapper categories = mock(ProductCategoryMapper.class);
        Page<SkuEntity> empty = new Page<>(1, 20, 0);
        Page<SkuEntity> uncategorized = new Page<>(1, 20, 1);
        uncategorized.setRecords(List.of(sku(1, null)));
        when(skus.selectPage(any(Page.class), any())).thenReturn(empty, uncategorized);
        var service = new SkuApplicationService(skus, categories, mock(ReferenceDataCache.class));
        assertTrue(service.page(new SkuPageQuery()).records().isEmpty());
        assertNull(service.page(new SkuPageQuery()).records().get(0).categoryName());
        verifyNoInteractions(categories);
    }

    private SkuEntity sku(long id, Long categoryId) {
        SkuEntity entity = new SkuEntity();
        entity.setId(id);
        entity.setCategoryId(categoryId);
        return entity;
    }
}
