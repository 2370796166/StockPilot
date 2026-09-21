package com.stockpilot.masterdata.sku.vo;

import com.stockpilot.masterdata.domain.MasterDataStatus;
import java.time.LocalDateTime;

public record SkuVO(
        Long id,
        String code,
        String name,
        Long categoryId,
        String categoryName,
        String unit,
        MasterDataStatus status,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Integer version) {}
