package com.stockpilot.sales.vo;

import com.stockpilot.sales.domain.SalesOutboundStatus;
import java.time.LocalDateTime;

public record SalesOutboundSummaryVO(
        Long id,
        String outboundNo,
        Long warehouseId,
        SalesOutboundStatus status,
        String remark,
        String createdByName,
        Integer version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
