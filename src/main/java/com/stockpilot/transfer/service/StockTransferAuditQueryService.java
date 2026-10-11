package com.stockpilot.transfer.service;

import com.stockpilot.transfer.mapper.StockTransferTransitMapper;
import com.stockpilot.transfer.vo.StockTransferAuditVO;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StockTransferAuditQueryService {
    private final StockTransferTransitMapper stockTransferTransitMapper;

    public StockTransferAuditQueryService(StockTransferTransitMapper stockTransferTransitMapper) {
        this.stockTransferTransitMapper = stockTransferTransitMapper;
    }

    public List<StockTransferAuditVO> audit(long warehouseId, long locationId, long skuId) {
        return stockTransferTransitMapper.audit(warehouseId, locationId, skuId);
    }
}
