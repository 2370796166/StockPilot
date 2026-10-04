package com.stockpilot.inventory.service;

import com.stockpilot.inventory.domain.InventoryBalanceState;
import com.stockpilot.inventory.domain.InventoryBusinessType;
import com.stockpilot.inventory.domain.InventoryLedgerEntity;
import com.stockpilot.inventory.domain.InventoryQuantityChange;
import java.math.BigDecimal;
import java.util.UUID;

final class InventoryLedgerFactory {
    private InventoryLedgerFactory() {}

    static InventoryLedgerEntity initialization(InitializeInventoryCommand command) {
        BigDecimal zero = BigDecimal.ZERO.setScale(4);
        InventoryLedgerEntity ledger = baseLedger();
        ledger.setBusinessType(InventoryBusinessType.INITIALIZE);
        ledger.setBusinessNo(
                "INIT-"
                        + command.warehouseId()
                        + "-"
                        + command.locationId()
                        + "-"
                        + command.skuId());
        ledger.setWarehouseId(command.warehouseId());
        ledger.setLocationId(command.locationId());
        ledger.setSkuId(command.skuId());
        ledger.setBeforeActualQuantity(zero);
        ledger.setChangeActualQuantity(zero);
        ledger.setAfterActualQuantity(zero);
        ledger.setBeforeAvailableQuantity(zero);
        ledger.setChangeAvailableQuantity(zero);
        ledger.setAfterAvailableQuantity(zero);
        ledger.setBeforeFrozenQuantity(zero);
        ledger.setChangeFrozenQuantity(zero);
        ledger.setAfterFrozenQuantity(zero);
        ledger.setBalanceVersionBefore(0);
        ledger.setBalanceVersionAfter(0);
        ledger.setOperatorId(command.operatorId());
        ledger.setOperatorName(command.operatorName());
        return ledger;
    }

    static InventoryLedgerEntity change(
            InventoryBalanceState before,
            InventoryBalanceState after,
            InventoryChangeCommand command) {
        InventoryLedgerEntity ledger = baseLedger();
        ledger.setBusinessType(command.businessType());
        ledger.setBusinessNo(command.businessNo());
        ledger.setWarehouseId(command.warehouseId());
        ledger.setLocationId(command.locationId());
        ledger.setSkuId(command.skuId());
        ledger.setBeforeActualQuantity(before.actualQuantity());
        ledger.setChangeActualQuantity(command.quantityChange().actualChange());
        ledger.setAfterActualQuantity(after.actualQuantity());
        ledger.setBeforeAvailableQuantity(before.availableQuantity());
        ledger.setChangeAvailableQuantity(command.quantityChange().availableChange());
        ledger.setAfterAvailableQuantity(after.availableQuantity());
        ledger.setBeforeFrozenQuantity(before.frozenQuantity());
        ledger.setChangeFrozenQuantity(command.quantityChange().frozenChange());
        ledger.setAfterFrozenQuantity(after.frozenQuantity());
        ledger.setBalanceVersionBefore(command.expectedVersion());
        ledger.setBalanceVersionAfter(command.expectedVersion() + 1);
        ledger.setOperatorId(command.operatorId());
        ledger.setOperatorName(command.operatorName());
        return ledger;
    }

    static InventoryLedgerEntity countAdjustment(
            InventoryBalanceState before,
            InventoryBalanceState after,
            InventoryCountAdjustmentCommand command) {
        InventoryLedgerEntity ledger =
                change(
                        before,
                        after,
                        new InventoryChangeCommand(
                                command.warehouseId(),
                                command.locationId(),
                                command.skuId(),
                                command.snapshotVersion(),
                                InventoryBusinessType.INVENTORY_COUNT,
                                command.countNo(),
                                new InventoryQuantityChange(
                                        after.actualQuantity().subtract(before.actualQuantity()),
                                        after.availableQuantity()
                                                .subtract(before.availableQuantity()),
                                        BigDecimal.ZERO.setScale(4)),
                                command.operatorId(),
                                command.operatorName()));
        ledger.setCountBookQuantity(command.snapshotActual());
        ledger.setCountedQuantity(command.countedQuantity());
        ledger.setDifferenceQuantity(command.countedQuantity().subtract(command.snapshotActual()));
        ledger.setAdjustmentReason(command.reason());
        return ledger;
    }

    private static InventoryLedgerEntity baseLedger() {
        InventoryLedgerEntity ledger = new InventoryLedgerEntity();
        ledger.setLedgerNo("IL-" + UUID.randomUUID().toString().replace("-", ""));
        return ledger;
    }
}
