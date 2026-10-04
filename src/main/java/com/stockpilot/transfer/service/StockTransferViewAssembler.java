package com.stockpilot.transfer.service;

import com.stockpilot.transfer.domain.StockTransferEntity;
import com.stockpilot.transfer.domain.StockTransferLineEntity;
import com.stockpilot.transfer.domain.StockTransferTransitEntity;
import com.stockpilot.transfer.vo.StockTransferSummaryVO;
import com.stockpilot.transfer.vo.StockTransferVO;
import java.util.List;

final class StockTransferViewAssembler {
    private StockTransferViewAssembler() {}

    static StockTransferVO detail(
            StockTransferEntity transfer,
            List<StockTransferLineEntity> transferLines,
            List<StockTransferTransitEntity> transitRecords) {
        return new StockTransferVO(
                transfer.getId(),
                transfer.getTransferNo(),
                transfer.getSourceWarehouseId(),
                transfer.getTargetWarehouseId(),
                transfer.getStatus(),
                transfer.getRemark(),
                transfer.getVersion(),
                transfer.getCreatedAt(),
                transfer.getUpdatedAt(),
                transferLines.stream()
                        .map(
                                line ->
                                        new StockTransferVO.Line(
                                                line.getId(),
                                                line.getLineNo(),
                                                line.getSourceLocationId(),
                                                line.getTargetLocationId(),
                                                line.getSkuId(),
                                                line.getQuantity()))
                        .toList(),
                transitRecords.stream()
                        .map(
                                transit ->
                                        new StockTransferVO.Transit(
                                                transit.getTransferLineId(),
                                                transit.getOutboundQuantity(),
                                                transit.getInTransitQuantity(),
                                                transit.getReceivedQuantity(),
                                                transit.getStatus(),
                                                transit.getVersion()))
                        .toList());
    }

    static StockTransferSummaryVO summary(StockTransferEntity transfer) {
        return new StockTransferSummaryVO(
                transfer.getId(),
                transfer.getTransferNo(),
                transfer.getSourceWarehouseId(),
                transfer.getTargetWarehouseId(),
                transfer.getStatus(),
                transfer.getRemark(),
                transfer.getVersion(),
                transfer.getCreatedAt(),
                transfer.getUpdatedAt());
    }
}
