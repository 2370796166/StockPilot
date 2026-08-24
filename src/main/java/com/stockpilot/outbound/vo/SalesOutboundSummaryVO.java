package com.stockpilot.outbound.vo;

import com.stockpilot.outbound.domain.SalesOutboundStatus;

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
        LocalDateTime updatedAt) {
}
