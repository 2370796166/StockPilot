package com.stockpilot.inventory.service;

import com.stockpilot.inventory.mapper.InventoryBalanceMapper;
import com.stockpilot.inventory.mapper.InventoryLedgerMapper;
import com.stockpilot.inventory.vo.InventoryAuditVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InventoryAuditQueryService {
    private final InventoryBalanceMapper inventoryBalanceMapper;
    private final InventoryLedgerMapper inventoryLedgerMapper;

    public InventoryAuditQueryService(
            InventoryBalanceMapper inventoryBalanceMapper,
            InventoryLedgerMapper inventoryLedgerMapper) {
        this.inventoryBalanceMapper = inventoryBalanceMapper;
        this.inventoryLedgerMapper = inventoryLedgerMapper;
    }

    public InventoryAuditVO.Snapshot snapshot(long warehouseId, long locationId, long skuId) {
        var balance = inventoryBalanceMapper.selectByDimension(warehouseId, locationId, skuId);
        var latest = inventoryLedgerMapper.latest(warehouseId, locationId, skuId);
        return new InventoryAuditVO.Snapshot(
                balance == null ? null : InventoryViewConverter.balance(balance),
                inventoryLedgerMapper.auditLedger(warehouseId, locationId, skuId),
                latest == null ? null : InventoryViewConverter.ledger(latest));
    }

    public InventoryAuditVO.DocumentQuantity documentQuantity(
            String number, String type, long warehouseId, long locationId, long skuId) {
        return inventoryLedgerMapper.documentQuantity(number, type, warehouseId, locationId, skuId);
    }
}
