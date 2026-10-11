package com.stockpilot.transfer.vo;

import java.math.BigDecimal;

public record StockTransferAuditVO(
        String transferNo,
        String orderStatus,
        long sourceWarehouseId,
        long sourceLocationId,
        long targetWarehouseId,
        long targetLocationId,
        long skuId,
        BigDecimal quantity,
        BigDecimal outboundQuantity,
        BigDecimal inTransitQuantity,
        BigDecimal receivedQuantity,
        String transitStatus) {}
