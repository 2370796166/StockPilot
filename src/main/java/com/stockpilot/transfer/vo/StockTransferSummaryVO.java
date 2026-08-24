package com.stockpilot.transfer.vo;

import com.stockpilot.transfer.domain.StockTransferStatus;
import java.time.LocalDateTime;

public record StockTransferSummaryVO(Long id,String transferNo,Long sourceWarehouseId,Long targetWarehouseId,
 StockTransferStatus status,String remark,Integer version,LocalDateTime createdAt,LocalDateTime updatedAt){}
