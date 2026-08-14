package com.stockpilot.inventory.application;

import com.stockpilot.common.exception.BusinessException;
import com.stockpilot.inventory.api.InventoryErrorCode;
import com.stockpilot.inventory.domain.InventoryBalanceEntity;
import com.stockpilot.inventory.domain.InventoryBalanceState;
import com.stockpilot.inventory.domain.InventoryLedgerEntity;
import com.stockpilot.inventory.domain.InventoryQuantityChange;
import com.stockpilot.inventory.vo.InventoryBalanceVO;
import com.stockpilot.inventory.vo.InventoryLedgerVO;

final class InventoryViewConverter {
    private InventoryViewConverter() {
    }

    static InventoryBalanceVO balance(InventoryBalanceEntity entity) {
        InventoryBalanceState state = new InventoryBalanceState(
                entity.getActualQuantity(), entity.getAvailableQuantity(), entity.getFrozenQuantity());
        return new InventoryBalanceVO(
                entity.getId(), entity.getWarehouseId(), entity.getLocationId(), entity.getSkuId(),
                state.actualQuantity(), state.availableQuantity(), state.frozenQuantity(),
                entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    static InventoryLedgerVO ledger(InventoryLedgerEntity entity) {
        InventoryBalanceState before = new InventoryBalanceState(
                entity.getBeforeActualQuantity(),
                entity.getBeforeAvailableQuantity(),
                entity.getBeforeFrozenQuantity());
        InventoryQuantityChange change = new InventoryQuantityChange(
                entity.getChangeActualQuantity(),
                entity.getChangeAvailableQuantity(),
                entity.getChangeFrozenQuantity());
        InventoryBalanceState after = new InventoryBalanceState(
                entity.getAfterActualQuantity(),
                entity.getAfterAvailableQuantity(),
                entity.getAfterFrozenQuantity());
        if (!before.apply(change).equals(after)) {
            throw new BusinessException(InventoryErrorCode.INVARIANT_VIOLATION, "库存流水前后数量不一致");
        }
        return new InventoryLedgerVO(
                entity.getId(), entity.getLedgerNo(), entity.getBusinessType(), entity.getBusinessNo(),
                entity.getWarehouseId(), entity.getLocationId(), entity.getSkuId(),
                entity.getBeforeActualQuantity(), entity.getChangeActualQuantity(), entity.getAfterActualQuantity(),
                entity.getBeforeAvailableQuantity(), entity.getChangeAvailableQuantity(), entity.getAfterAvailableQuantity(),
                entity.getBeforeFrozenQuantity(), entity.getChangeFrozenQuantity(), entity.getAfterFrozenQuantity(),
                entity.getBalanceVersionBefore(), entity.getBalanceVersionAfter(),
                entity.getOperatorId(), entity.getOperatorName(), entity.getOccurredAt());
    }
}
