package com.stockpilot.masterdata.sku.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.stockpilot.masterdata.domain.BaseMasterDataEntity;

@TableName("sku")
public class SkuEntity extends BaseMasterDataEntity {
    private Long categoryId;
    private String unit;

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long v) {
        categoryId = v;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String v) {
        unit = v;
    }
}
