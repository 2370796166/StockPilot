package com.stockpilot.purchase.service;

import com.stockpilot.purchase.domain.PurchaseReceiptEntity;
import com.stockpilot.purchase.domain.PurchaseReceiptLineEntity;
import com.stockpilot.purchase.vo.PurchaseReceiptSummaryVO;
import com.stockpilot.purchase.vo.PurchaseReceiptVO;
import java.util.List;

final class PurchaseReceiptViewAssembler {
    private PurchaseReceiptViewAssembler() {}

    static PurchaseReceiptVO detail(
            PurchaseReceiptEntity receipt, List<PurchaseReceiptLineEntity> receiptLines) {
        return new PurchaseReceiptVO(
                receipt.getId(),
                receipt.getReceiptNo(),
                receipt.getWarehouseId(),
                receipt.getStatus(),
                receipt.getRemark(),
                receipt.getCreatedBy(),
                receipt.getCreatedByName(),
                receipt.getSubmittedBy(),
                receipt.getSubmittedByName(),
                receipt.getSubmittedAt(),
                receipt.getApprovedBy(),
                receipt.getApprovedByName(),
                receipt.getApprovedAt(),
                receipt.getCompletedBy(),
                receipt.getCompletedByName(),
                receipt.getCompletedAt(),
                receipt.getVersion(),
                receipt.getCreatedAt(),
                receipt.getUpdatedAt(),
                receiptLines.stream()
                        .map(
                                line ->
                                        new PurchaseReceiptVO.Line(
                                                line.getId(),
                                                line.getLineNo(),
                                                line.getLocationId(),
                                                line.getSkuId(),
                                                line.getQuantity()))
                        .toList());
    }

    static PurchaseReceiptSummaryVO summary(PurchaseReceiptEntity receipt) {
        return new PurchaseReceiptSummaryVO(
                receipt.getId(),
                receipt.getReceiptNo(),
                receipt.getWarehouseId(),
                receipt.getStatus(),
                receipt.getRemark(),
                receipt.getCreatedByName(),
                receipt.getVersion(),
                receipt.getCreatedAt(),
                receipt.getUpdatedAt());
    }
}
