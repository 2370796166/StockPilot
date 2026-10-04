package com.stockpilot.sales.service;

import com.stockpilot.sales.domain.SalesOutboundEntity;
import com.stockpilot.sales.domain.SalesOutboundLineEntity;
import com.stockpilot.sales.vo.SalesOutboundSummaryVO;
import com.stockpilot.sales.vo.SalesOutboundVO;
import java.util.List;

final class SalesOutboundViewAssembler {
    private SalesOutboundViewAssembler() {}

    static SalesOutboundVO detail(
            SalesOutboundEntity outbound, List<SalesOutboundLineEntity> outboundLines) {
        return new SalesOutboundVO(
                outbound.getId(),
                outbound.getOutboundNo(),
                outbound.getWarehouseId(),
                outbound.getStatus(),
                outbound.getRemark(),
                outbound.getCreatedBy(),
                outbound.getCreatedByName(),
                outbound.getReservedBy(),
                outbound.getReservedByName(),
                outbound.getReservedAt(),
                outbound.getApprovedBy(),
                outbound.getApprovedByName(),
                outbound.getApprovedAt(),
                outbound.getCompletedBy(),
                outbound.getCompletedByName(),
                outbound.getCompletedAt(),
                outbound.getCancelledBy(),
                outbound.getCancelledByName(),
                outbound.getCancelledAt(),
                outbound.getVersion(),
                outbound.getCreatedAt(),
                outbound.getUpdatedAt(),
                outboundLines.stream()
                        .map(
                                line ->
                                        new SalesOutboundVO.Line(
                                                line.getId(),
                                                line.getLineNo(),
                                                line.getLocationId(),
                                                line.getSkuId(),
                                                line.getQuantity()))
                        .toList());
    }

    static SalesOutboundSummaryVO summary(SalesOutboundEntity outbound) {
        return new SalesOutboundSummaryVO(
                outbound.getId(),
                outbound.getOutboundNo(),
                outbound.getWarehouseId(),
                outbound.getStatus(),
                outbound.getRemark(),
                outbound.getCreatedByName(),
                outbound.getVersion(),
                outbound.getCreatedAt(),
                outbound.getUpdatedAt());
    }
}
