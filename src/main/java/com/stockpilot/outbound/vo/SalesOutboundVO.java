package com.stockpilot.outbound.vo;

import com.stockpilot.outbound.domain.SalesOutboundStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record SalesOutboundVO(
        Long id,
        String outboundNo,
        Long warehouseId,
        SalesOutboundStatus status,
        String remark,
        Long createdBy,
        String createdByName,
        Long reservedBy,
        String reservedByName,
        LocalDateTime reservedAt,
        Long approvedBy,
        String approvedByName,
        LocalDateTime approvedAt,
        Long completedBy,
        String completedByName,
        LocalDateTime completedAt,
        Long cancelledBy,
        String cancelledByName,
        LocalDateTime cancelledAt,
        Integer version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<Line> lines) {

    public record Line(Long id, Integer lineNo, Long locationId, Long skuId, BigDecimal quantity) {
    }
}
