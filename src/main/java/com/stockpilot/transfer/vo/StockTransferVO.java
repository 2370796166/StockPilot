package com.stockpilot.transfer.vo;

import com.stockpilot.transfer.domain.StockTransferStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record StockTransferVO(Long id,String transferNo,Long sourceWarehouseId,Long targetWarehouseId,
 StockTransferStatus status,String remark,Integer version,LocalDateTime createdAt,LocalDateTime updatedAt,
 List<Line> lines,List<Transit> transitRecords) {
 public record Line(Long id,Integer lineNo,Long sourceLocationId,Long targetLocationId,Long skuId,BigDecimal quantity){}
 public record Transit(Long transferLineId,BigDecimal outboundQuantity,BigDecimal inTransitQuantity,
  BigDecimal receivedQuantity,String status,Integer version){}
}
