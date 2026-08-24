package com.stockpilot.inventory.application;

import com.stockpilot.common.exception.BusinessException;
import com.stockpilot.inventory.api.InventoryErrorCode;
import com.stockpilot.inventory.domain.InventoryBalanceEntity;
import com.stockpilot.inventory.domain.InventoryBalanceState;
import com.stockpilot.inventory.domain.InventoryBusinessType;
import com.stockpilot.inventory.domain.InventoryLedgerEntity;
import com.stockpilot.inventory.domain.InventoryQuantityChange;
import com.stockpilot.inventory.infrastructure.mapper.InventoryBalanceMapper;
import com.stockpilot.inventory.infrastructure.mapper.InventoryLedgerMapper;
import com.stockpilot.inventory.vo.InventoryBalanceVO;
import com.stockpilot.masterdata.application.MasterDataReferenceApplicationService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class InventoryMutationApplicationService {
    private final InventoryBalanceMapper balances;
    private final InventoryLedgerMapper ledgers;
    private final MasterDataReferenceApplicationService masterData;

    public InventoryMutationApplicationService(
            InventoryBalanceMapper balances,
            InventoryLedgerMapper ledgers,
            MasterDataReferenceApplicationService masterData) {
        this.balances = balances;
        this.ledgers = ledgers;
        this.masterData = masterData;
    }

    @Transactional
    public InventoryBalanceVO initializeZeroBalance(InitializeInventoryCommand command) {
        masterData.requireEnabledInventoryDimension(
                command.warehouseId(), command.locationId(), command.skuId());

        InventoryBalanceState zero = InventoryBalanceState.zero();
        InventoryBalanceEntity balance = new InventoryBalanceEntity();
        balance.setWarehouseId(command.warehouseId());
        balance.setLocationId(command.locationId());
        balance.setSkuId(command.skuId());
        balance.setActualQuantity(zero.actualQuantity());
        balance.setAvailableQuantity(zero.availableQuantity());
        balance.setFrozenQuantity(zero.frozenQuantity());
        balance.setVersion(0);

        try {
            if (balances.insert(balance) != 1) {
                throw new BusinessException(InventoryErrorCode.CONCURRENT_MODIFICATION);
            }
            if (ledgers.insert(initializationLedger(balance, command)) != 1) {
                throw new BusinessException(InventoryErrorCode.LEDGER_WRITE_FAILED);
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(InventoryErrorCode.BALANCE_ALREADY_EXISTS);
        }

        InventoryBalanceEntity saved = balances.selectById(balance.getId());
        if (saved == null) {
            throw new BusinessException(InventoryErrorCode.BALANCE_NOT_FOUND);
        }
        return InventoryViewConverter.balance(saved);
    }

    @Transactional
    public InventoryBalanceVO applyChange(InventoryChangeCommand command) {
        InventoryBalanceEntity balance = balances.selectByDimension(
                command.warehouseId(), command.locationId(), command.skuId());
        if (balance == null) {
            throw new BusinessException(InventoryErrorCode.BALANCE_NOT_FOUND);
        }
        InventoryBalanceState before = new InventoryBalanceState(
                balance.getActualQuantity(), balance.getAvailableQuantity(), balance.getFrozenQuantity());
        InventoryBalanceState after = before.apply(command.quantityChange());

        if (balances.updateStateIfVersionMatches(balance.getId(), command.expectedVersion(), after) != 1) {
            ensureNotCountLocked(command.warehouseId(), command.locationId(), command.skuId());
            throw new BusinessException(InventoryErrorCode.CONCURRENT_MODIFICATION);
        }

        try {
            if (ledgers.insert(changeLedger(before, after, command)) != 1) {
                throw new BusinessException(InventoryErrorCode.LEDGER_WRITE_FAILED);
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(InventoryErrorCode.DUPLICATE_BUSINESS_ACTION);
        }

        InventoryBalanceEntity saved = balances.selectById(balance.getId());
        if (saved == null) {
            throw new BusinessException(InventoryErrorCode.BALANCE_NOT_FOUND);
        }
        return InventoryViewConverter.balance(saved);
    }

    @Transactional
    public InventoryBalanceVO receivePurchase(PurchaseReceiptInventoryCommand command) {
        masterData.requireEnabledInventoryDimension(
                command.warehouseId(), command.locationId(), command.skuId());

        int inserted = balances.insertZeroIfAbsent(
                command.warehouseId(), command.locationId(), command.skuId());
        InventoryBalanceEntity balance = balances.selectByDimensionForUpdate(
                command.warehouseId(), command.locationId(), command.skuId());
        if (balance == null) {
            throw new BusinessException(InventoryErrorCode.BALANCE_NOT_FOUND);
        }
        ensureNotCountLocked(command.warehouseId(), command.locationId(), command.skuId());
        if (inserted == 1) {
            InitializeInventoryCommand initialize = new InitializeInventoryCommand(
                    command.warehouseId(), command.locationId(), command.skuId(),
                    command.operatorId(), command.operatorName());
            if (ledgers.insert(initializationLedger(balance, initialize)) != 1) {
                throw new BusinessException(InventoryErrorCode.LEDGER_WRITE_FAILED);
            }
        }

        InventoryBalanceState before = new InventoryBalanceState(
                balance.getActualQuantity(), balance.getAvailableQuantity(), balance.getFrozenQuantity());
        InventoryQuantityChange change = InventoryQuantityChange.receipt(command.quantity());
        InventoryBalanceState after = before.apply(change);
        if (balances.updateStateIfVersionMatches(balance.getId(), balance.getVersion(), after) != 1) {
            throw new BusinessException(InventoryErrorCode.CONCURRENT_MODIFICATION);
        }

        InventoryChangeCommand inventoryChange = new InventoryChangeCommand(
                command.warehouseId(), command.locationId(), command.skuId(), balance.getVersion(),
                InventoryBusinessType.PURCHASE_RECEIPT, command.receiptNo(), change,
                command.operatorId(), command.operatorName());
        try {
            if (ledgers.insert(changeLedger(before, after, inventoryChange)) != 1) {
                throw new BusinessException(InventoryErrorCode.LEDGER_WRITE_FAILED);
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(InventoryErrorCode.DUPLICATE_BUSINESS_ACTION);
        }

        InventoryBalanceEntity saved = balances.selectById(balance.getId());
        if (saved == null) {
            throw new BusinessException(InventoryErrorCode.BALANCE_NOT_FOUND);
        }
        return InventoryViewConverter.balance(saved);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryBalanceVO freezeOutbound(OutboundInventoryCommand command) {
        masterData.requireEnabledInventoryDimension(
                command.warehouseId(), command.locationId(), command.skuId());
        int affected = balances.freezeIfAvailable(
                command.warehouseId(), command.locationId(), command.skuId(), command.quantity());
        return finishOutboundMutation(command, InventoryQuantityChange.freeze(command.quantity()),
                affected, InventoryErrorCode.INSUFFICIENT_AVAILABLE);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryBalanceVO shipOutbound(OutboundInventoryCommand command) {
        int affected = balances.shipIfFrozen(
                command.warehouseId(), command.locationId(), command.skuId(), command.quantity());
        return finishOutboundMutation(command, InventoryQuantityChange.ship(command.quantity()),
                affected, InventoryErrorCode.INSUFFICIENT_FROZEN);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryBalanceVO releaseOutbound(OutboundInventoryCommand command) {
        int affected = balances.releaseIfFrozen(
                command.warehouseId(), command.locationId(), command.skuId(), command.quantity());
        return finishOutboundMutation(command, InventoryQuantityChange.release(command.quantity()),
                affected, InventoryErrorCode.INSUFFICIENT_FROZEN);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryBalanceVO freezeTransfer(TransferInventoryCommand command) {
        masterData.requireEnabledInventoryDimension(
                command.warehouseId(), command.locationId(), command.skuId());
        int affected = balances.freezeIfAvailable(
                command.warehouseId(), command.locationId(), command.skuId(), command.quantity());
        return finishTransferMutation(command, InventoryQuantityChange.freeze(command.quantity()),
                affected, InventoryErrorCode.INSUFFICIENT_AVAILABLE);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryBalanceVO releaseTransfer(TransferInventoryCommand command) {
        int affected = balances.releaseIfFrozen(
                command.warehouseId(), command.locationId(), command.skuId(), command.quantity());
        return finishTransferMutation(command, InventoryQuantityChange.release(command.quantity()),
                affected, InventoryErrorCode.INSUFFICIENT_FROZEN);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryBalanceVO outboundTransfer(TransferInventoryCommand command) {
        int affected = balances.shipIfFrozen(
                command.warehouseId(), command.locationId(), command.skuId(), command.quantity());
        return finishTransferMutation(command, InventoryQuantityChange.ship(command.quantity()),
                affected, InventoryErrorCode.INSUFFICIENT_FROZEN);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryBalanceVO inboundTransfer(TransferInventoryCommand command) {
        balances.insertZeroIfAbsent(command.warehouseId(), command.locationId(), command.skuId());
        InventoryBalanceEntity balance = balances.selectByDimensionForUpdate(
                command.warehouseId(), command.locationId(), command.skuId());
        if (balance == null) {
            throw new BusinessException(InventoryErrorCode.BALANCE_NOT_FOUND);
        }
        ensureNotCountLocked(command.warehouseId(), command.locationId(), command.skuId());
        InventoryBalanceState before = new InventoryBalanceState(
                balance.getActualQuantity(), balance.getAvailableQuantity(), balance.getFrozenQuantity());
        InventoryQuantityChange change = InventoryQuantityChange.receipt(command.quantity());
        InventoryBalanceState after = before.apply(change);
        if (balances.updateStateIfVersionMatches(balance.getId(), balance.getVersion(), after) != 1) {
            throw new BusinessException(InventoryErrorCode.CONCURRENT_MODIFICATION);
        }
        InventoryChangeCommand inventoryChange = new InventoryChangeCommand(
                command.warehouseId(), command.locationId(), command.skuId(), balance.getVersion(),
                command.businessType(), command.transferNo(), change,
                command.operatorId(), command.operatorName());
        insertChangeLedger(before, after, inventoryChange);
        InventoryBalanceEntity saved = balances.selectById(balance.getId());
        if (saved == null) throw new BusinessException(InventoryErrorCode.BALANCE_NOT_FOUND);
        return InventoryViewConverter.balance(saved);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryBalanceVO lockCountSnapshot(long warehouseId, long locationId, long skuId) {
        masterData.requireEnabledInventoryDimension(warehouseId, locationId, skuId);
        InventoryBalanceEntity balance = balances.selectByDimensionForUpdate(warehouseId, locationId, skuId);
        if (balance == null) {
            throw new BusinessException(InventoryErrorCode.BALANCE_NOT_FOUND);
        }
        ensureNotCountLocked(warehouseId, locationId, skuId);
        return InventoryViewConverter.balance(balance);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryBalanceVO adjustInventoryCount(InventoryCountAdjustmentCommand command) {
        if (command.countedQuantity().compareTo(command.snapshotFrozen()) < 0) {
            throw new BusinessException(InventoryErrorCode.INVARIANT_VIOLATION,
                    "实盘数量不能小于冻结库存，需先处理占用业务");
        }
        int affected = balances.adjustCountIfSnapshotMatches(
                command.countId(), command.countLineId(), command.warehouseId(), command.locationId(),
                command.skuId(), command.snapshotVersion(), command.snapshotActual(),
                command.snapshotAvailable(), command.snapshotFrozen(), command.countedQuantity());
        if (affected != 1) {
            throw new BusinessException(InventoryErrorCode.CONCURRENT_MODIFICATION,
                    "盘点快照、维度锁或库存版本已变化，不能执行调整");
        }
        InventoryBalanceEntity saved = balances.selectByDimension(
                command.warehouseId(), command.locationId(), command.skuId());
        if (saved == null) {
            throw new BusinessException(InventoryErrorCode.BALANCE_NOT_FOUND);
        }
        InventoryBalanceState before = new InventoryBalanceState(
                command.snapshotActual(), command.snapshotAvailable(), command.snapshotFrozen());
        InventoryBalanceState after = new InventoryBalanceState(
                saved.getActualQuantity(), saved.getAvailableQuantity(), saved.getFrozenQuantity());
        InventoryLedgerEntity ledger = changeLedger(before, after, new InventoryChangeCommand(
                command.warehouseId(), command.locationId(), command.skuId(), command.snapshotVersion(),
                InventoryBusinessType.INVENTORY_COUNT, command.countNo(),
                new InventoryQuantityChange(
                        after.actualQuantity().subtract(before.actualQuantity()),
                        after.availableQuantity().subtract(before.availableQuantity()),
                        BigDecimal.ZERO.setScale(4)),
                command.operatorId(), command.operatorName()));
        ledger.setCountBookQuantity(command.snapshotActual());
        ledger.setCountedQuantity(command.countedQuantity());
        ledger.setDifferenceQuantity(command.countedQuantity().subtract(command.snapshotActual()));
        ledger.setAdjustmentReason(command.reason());
        try {
            if (ledgers.insert(ledger) != 1) {
                throw new BusinessException(InventoryErrorCode.LEDGER_WRITE_FAILED);
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(InventoryErrorCode.DUPLICATE_BUSINESS_ACTION);
        }
        return InventoryViewConverter.balance(saved);
    }

    private InventoryBalanceVO finishTransferMutation(
            TransferInventoryCommand command,
            InventoryQuantityChange change,
            int affected,
            InventoryErrorCode insufficientError) {
        if (affected != 1) {
            ensureNotCountLocked(command.warehouseId(), command.locationId(), command.skuId());
            if (balances.selectByDimension(command.warehouseId(), command.locationId(), command.skuId()) == null) {
                throw new BusinessException(InventoryErrorCode.BALANCE_NOT_FOUND);
            }
            throw new BusinessException(insufficientError);
        }
        InventoryBalanceEntity saved = balances.selectByDimension(
                command.warehouseId(), command.locationId(), command.skuId());
        if (saved == null || saved.getVersion() == null || saved.getVersion() <= 0) {
            throw new BusinessException(InventoryErrorCode.CONCURRENT_MODIFICATION);
        }
        InventoryBalanceState after = new InventoryBalanceState(
                saved.getActualQuantity(), saved.getAvailableQuantity(), saved.getFrozenQuantity());
        InventoryBalanceState before = new InventoryBalanceState(
                after.actualQuantity().subtract(change.actualChange()),
                after.availableQuantity().subtract(change.availableChange()),
                after.frozenQuantity().subtract(change.frozenChange()));
        InventoryChangeCommand inventoryChange = new InventoryChangeCommand(
                command.warehouseId(), command.locationId(), command.skuId(), saved.getVersion() - 1,
                command.businessType(), command.transferNo(), change,
                command.operatorId(), command.operatorName());
        insertChangeLedger(before, after, inventoryChange);
        return InventoryViewConverter.balance(saved);
    }

    private void insertChangeLedger(
            InventoryBalanceState before, InventoryBalanceState after, InventoryChangeCommand command) {
        try {
            if (ledgers.insert(changeLedger(before, after, command)) != 1) {
                throw new BusinessException(InventoryErrorCode.LEDGER_WRITE_FAILED);
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(InventoryErrorCode.DUPLICATE_BUSINESS_ACTION);
        }
    }

    private InventoryBalanceVO finishOutboundMutation(
            OutboundInventoryCommand command,
            InventoryQuantityChange change,
            int affected,
            InventoryErrorCode insufficientError) {
        if (affected != 1) {
            ensureNotCountLocked(command.warehouseId(), command.locationId(), command.skuId());
            if (balances.selectByDimension(
                    command.warehouseId(), command.locationId(), command.skuId()) == null) {
                throw new BusinessException(InventoryErrorCode.BALANCE_NOT_FOUND);
            }
            throw new BusinessException(insufficientError);
        }

        InventoryBalanceEntity saved = balances.selectByDimension(
                command.warehouseId(), command.locationId(), command.skuId());
        if (saved == null || saved.getVersion() == null || saved.getVersion() <= 0) {
            throw new BusinessException(InventoryErrorCode.CONCURRENT_MODIFICATION);
        }
        InventoryBalanceState after = new InventoryBalanceState(
                saved.getActualQuantity(), saved.getAvailableQuantity(), saved.getFrozenQuantity());
        InventoryBalanceState before = new InventoryBalanceState(
                after.actualQuantity().subtract(change.actualChange()),
                after.availableQuantity().subtract(change.availableChange()),
                after.frozenQuantity().subtract(change.frozenChange()));
        int versionBefore = saved.getVersion() - 1;
        InventoryChangeCommand inventoryChange = new InventoryChangeCommand(
                command.warehouseId(), command.locationId(), command.skuId(), versionBefore,
                command.businessType(), command.outboundNo(), change,
                command.operatorId(), command.operatorName());
        try {
            if (ledgers.insert(changeLedger(before, after, inventoryChange)) != 1) {
                throw new BusinessException(InventoryErrorCode.LEDGER_WRITE_FAILED);
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(InventoryErrorCode.DUPLICATE_BUSINESS_ACTION);
        }
        return InventoryViewConverter.balance(saved);
    }

    private void ensureNotCountLocked(long warehouseId, long locationId, long skuId) {
        if (balances.countActiveCountLocks(warehouseId, locationId, skuId) > 0) {
            throw new BusinessException(InventoryErrorCode.COUNT_LOCKED);
        }
    }

    private InventoryLedgerEntity initializationLedger(
            InventoryBalanceEntity balance, InitializeInventoryCommand command) {
        BigDecimal zero = BigDecimal.ZERO.setScale(4);
        InventoryLedgerEntity ledger = new InventoryLedgerEntity();
        ledger.setLedgerNo("IL-" + UUID.randomUUID().toString().replace("-", ""));
        ledger.setBusinessType(InventoryBusinessType.INITIALIZE);
        ledger.setBusinessNo("INIT-" + command.warehouseId() + "-" + command.locationId() + "-" + command.skuId());
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

    private InventoryLedgerEntity changeLedger(
            InventoryBalanceState before,
            InventoryBalanceState after,
            InventoryChangeCommand command) {
        InventoryLedgerEntity ledger = new InventoryLedgerEntity();
        ledger.setLedgerNo("IL-" + UUID.randomUUID().toString().replace("-", ""));
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
}
