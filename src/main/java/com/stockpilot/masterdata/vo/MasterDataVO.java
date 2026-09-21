package com.stockpilot.masterdata.vo;

import com.stockpilot.masterdata.domain.MasterDataStatus;
import java.time.LocalDateTime;

public record MasterDataVO(
        Long id,
        String code,
        String name,
        MasterDataStatus status,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Integer version) {}
