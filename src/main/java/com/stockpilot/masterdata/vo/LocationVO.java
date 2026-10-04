package com.stockpilot.masterdata.vo;

import com.stockpilot.masterdata.domain.MasterDataStatus;
import java.time.LocalDateTime;

public record LocationVO(
        Long id,
        Long warehouseId,
        String warehouseCode,
        String code,
        String name,
        MasterDataStatus status,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Integer version) {}
