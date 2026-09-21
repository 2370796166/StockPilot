package com.stockpilot.masterdata.sku.request;

import com.stockpilot.masterdata.request.PageQuery;
import jakarta.validation.constraints.Positive;

public class SkuPageQuery extends PageQuery {
    @Positive private Long categoryId;

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long v) {
        categoryId = v;
    }
}
