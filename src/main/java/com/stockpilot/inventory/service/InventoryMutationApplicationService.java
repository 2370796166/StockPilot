package com.stockpilot.inventory.service;

import com.stockpilot.inventory.api.InventoryErrorCode;
import com.stockpilot.inventory.domain.InventoryBalanceEntity;
import com.stockpilot.inventory.domain.InventoryBalanceState;
import com.stockpilot.inventory.domain.InventoryBusinessType;
import com.stockpilot.inventory.domain.InventoryLedgerEntity;
import com.stockpilot.inventory.domain.InventoryQuantityChange;
import com.stockpilot.inventory.mapper.InventoryBalanceMapper;
import com.stockpilot.inventory.mapper.InventoryLedgerMapper;
import com.stockpilot.inventory.vo.InventoryBalanceVO;
import com.stockpilot.masterdata.service.MasterDataReferenceApplicationService;
import com.stockpilot.shared.exception.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
// 所有业务库存变更统一收口于本服务，避免业务模块直接操作库存表而绕过流水和事务约束。
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

    // 显式创建零库存维度，并追加 INITIALIZE 流水；同一仓库、库位、SKU 只能初始化一次。
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
            if (ledgers.insert(InventoryLedgerFactory.initialization(command)) != 1) {
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

    // 按调用方给定的预期版本执行通用差量变更，同时校验三数量不变量并追加版本流水。
    // 该入口主要用于内部受控场景，普通 HTTP 接口不允许直接调整库存。
    @Transactional
    public InventoryBalanceVO applyChange(InventoryChangeCommand command) {
        InventoryBalanceEntity balance =
                balances.selectByDimension(
                        command.warehouseId(), command.locationId(), command.skuId());
        if (balance == null) {
            throw new BusinessException(InventoryErrorCode.BALANCE_NOT_FOUND);
        }
        InventoryBalanceState before =
                new InventoryBalanceState(
                        balance.getActualQuantity(),
                        balance.getAvailableQuantity(),
                        balance.getFrozenQuantity());
        InventoryBalanceState after = before.apply(command.quantityChange());

        if (balances.updateStateIfVersionMatches(balance.getId(), command.expectedVersion(), after)
                != 1) {
            ensureNotCountLocked(command.warehouseId(), command.locationId(), command.skuId());
            throw new BusinessException(InventoryErrorCode.CONCURRENT_MODIFICATION);
        }

        try {
            if (ledgers.insert(InventoryLedgerFactory.change(before, after, command)) != 1) {
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

    // 执行采购入库库存变化：必要时创建零余额，然后同步增加实际量与可用量并记录入库流水。
    @Transactional
    public InventoryBalanceVO receivePurchase(PurchaseReceiptInventoryCommand command) {
        masterData.requireEnabledInventoryDimension(
                command.warehouseId(), command.locationId(), command.skuId());

        int inserted =
                balances.insertZeroIfAbsent(
                        command.warehouseId(), command.locationId(), command.skuId());
        InventoryBalanceEntity balance =
                balances.selectByDimensionForUpdate(
                        command.warehouseId(), command.locationId(), command.skuId());
        if (balance == null) {
            throw new BusinessException(InventoryErrorCode.BALANCE_NOT_FOUND);
        }
        ensureNotCountLocked(command.warehouseId(), command.locationId(), command.skuId());
        if (inserted == 1) {
            InitializeInventoryCommand initialize =
                    new InitializeInventoryCommand(
                            command.warehouseId(),
                            command.locationId(),
                            command.skuId(),
                            command.operatorId(),
                            command.operatorName());
            if (ledgers.insert(InventoryLedgerFactory.initialization(initialize)) != 1) {
                throw new BusinessException(InventoryErrorCode.LEDGER_WRITE_FAILED);
            }
        }

        InventoryBalanceState before =
                new InventoryBalanceState(
                        balance.getActualQuantity(),
                        balance.getAvailableQuantity(),
                        balance.getFrozenQuantity());
        InventoryQuantityChange change = InventoryQuantityChange.receipt(command.quantity());
        InventoryBalanceState after = before.apply(change);
        if (balances.updateStateIfVersionMatches(balance.getId(), balance.getVersion(), after)
                != 1) {
            throw new BusinessException(InventoryErrorCode.CONCURRENT_MODIFICATION);
        }

        InventoryChangeCommand inventoryChange =
                new InventoryChangeCommand(
                        command.warehouseId(),
                        command.locationId(),
                        command.skuId(),
                        balance.getVersion(),
                        InventoryBusinessType.PURCHASE_RECEIPT,
                        command.receiptNo(),
                        change,
                        command.operatorId(),
                        command.operatorName());
        try {
            if (ledgers.insert(InventoryLedgerFactory.change(before, after, inventoryChange))
                    != 1) {
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

    // 冻结销售库存：可用量减少、冻结量增加、实际量不变；必须加入销售单据事务。
    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryBalanceVO freezeOutbound(OutboundInventoryCommand command) {
        masterData.requireEnabledInventoryDimension(
                command.warehouseId(), command.locationId(), command.skuId());
        // 可用量条件由 MySQL 在 UPDATE 中原子判断，避免“先查询、后扣减”导致并发超卖。
        int affected =
                balances.freezeIfAvailable(
                        command.warehouseId(),
                        command.locationId(),
                        command.skuId(),
                        command.quantity());
        return finishOutboundMutation(
                command,
                InventoryQuantityChange.freeze(command.quantity()),
                affected,
                InventoryErrorCode.INSUFFICIENT_AVAILABLE);
    }

    // 完成销售出库：实际量和冻结量同步减少；仅消费此前已经冻结的库存。
    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryBalanceVO shipOutbound(OutboundInventoryCommand command) {
        int affected =
                balances.shipIfFrozen(
                        command.warehouseId(),
                        command.locationId(),
                        command.skuId(),
                        command.quantity());
        return finishOutboundMutation(
                command,
                InventoryQuantityChange.ship(command.quantity()),
                affected,
                InventoryErrorCode.INSUFFICIENT_FROZEN);
    }

    // 取消销售占用：冻结量减少、可用量恢复，实际库存保持不变。
    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryBalanceVO releaseOutbound(OutboundInventoryCommand command) {
        int affected =
                balances.releaseIfFrozen(
                        command.warehouseId(),
                        command.locationId(),
                        command.skuId(),
                        command.quantity());
        return finishOutboundMutation(
                command,
                InventoryQuantityChange.release(command.quantity()),
                affected,
                InventoryErrorCode.INSUFFICIENT_FROZEN);
    }

    // 提交调拨时冻结源仓库存，防止等待审核或调出期间被其他出库业务占用。
    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryBalanceVO freezeTransfer(TransferInventoryCommand command) {
        masterData.requireEnabledInventoryDimension(
                command.warehouseId(), command.locationId(), command.skuId());
        int affected =
                balances.freezeIfAvailable(
                        command.warehouseId(),
                        command.locationId(),
                        command.skuId(),
                        command.quantity());
        return finishTransferMutation(
                command,
                InventoryQuantityChange.freeze(command.quantity()),
                affected,
                InventoryErrorCode.INSUFFICIENT_AVAILABLE);
    }

    // 调拨取消时释放源仓冻结库存，仅恢复可用量，不改变源仓实际库存。
    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryBalanceVO releaseTransfer(TransferInventoryCommand command) {
        int affected =
                balances.releaseIfFrozen(
                        command.warehouseId(),
                        command.locationId(),
                        command.skuId(),
                        command.quantity());
        return finishTransferMutation(
                command,
                InventoryQuantityChange.release(command.quantity()),
                affected,
                InventoryErrorCode.INSUFFICIENT_FROZEN);
    }

    // 调拨出库：从源仓实际量和冻结量中扣除，库存随后由独立在途记录承接。
    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryBalanceVO outboundTransfer(TransferInventoryCommand command) {
        int affected =
                balances.shipIfFrozen(
                        command.warehouseId(),
                        command.locationId(),
                        command.skuId(),
                        command.quantity());
        return finishTransferMutation(
                command,
                InventoryQuantityChange.ship(command.quantity()),
                affected,
                InventoryErrorCode.INSUFFICIENT_FROZEN);
    }

    // 调拨入库：目标仓实际量和可用量同步增加，冻结量不变。
    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryBalanceVO inboundTransfer(TransferInventoryCommand command) {
        // 调入维度允许此前不存在余额；零余额创建与后续入库仍处于调用方的同一事务中。
        balances.insertZeroIfAbsent(command.warehouseId(), command.locationId(), command.skuId());
        InventoryBalanceEntity balance =
                balances.selectByDimensionForUpdate(
                        command.warehouseId(), command.locationId(), command.skuId());
        if (balance == null) {
            throw new BusinessException(InventoryErrorCode.BALANCE_NOT_FOUND);
        }
        ensureNotCountLocked(command.warehouseId(), command.locationId(), command.skuId());
        InventoryBalanceState before =
                new InventoryBalanceState(
                        balance.getActualQuantity(),
                        balance.getAvailableQuantity(),
                        balance.getFrozenQuantity());
        InventoryQuantityChange change = InventoryQuantityChange.receipt(command.quantity());
        InventoryBalanceState after = before.apply(change);
        if (balances.updateStateIfVersionMatches(balance.getId(), balance.getVersion(), after)
                != 1) {
            throw new BusinessException(InventoryErrorCode.CONCURRENT_MODIFICATION);
        }
        InventoryChangeCommand inventoryChange =
                new InventoryChangeCommand(
                        command.warehouseId(),
                        command.locationId(),
                        command.skuId(),
                        balance.getVersion(),
                        command.businessType(),
                        command.transferNo(),
                        change,
                        command.operatorId(),
                        command.operatorName());
        insertChangeLedger(before, after, inventoryChange);
        InventoryBalanceEntity saved = balances.selectById(balance.getId());
        if (saved == null) throw new BusinessException(InventoryErrorCode.BALANCE_NOT_FOUND);
        return InventoryViewConverter.balance(saved);
    }

    // 创建盘点快照前锁定库存行并确认维度未被其他盘点占用，返回三数量和版本快照。
    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryBalanceVO lockCountSnapshot(long warehouseId, long locationId, long skuId) {
        masterData.requireEnabledInventoryDimension(warehouseId, locationId, skuId);
        InventoryBalanceEntity balance =
                balances.selectByDimensionForUpdate(warehouseId, locationId, skuId);
        if (balance == null) {
            throw new BusinessException(InventoryErrorCode.BALANCE_NOT_FOUND);
        }
        ensureNotCountLocked(warehouseId, locationId, skuId);
        return InventoryViewConverter.balance(balance);
    }

    // 根据已审核盘点结果调整实际量与可用量，冻结量保持不变，并写入包含账面量和实盘量的流水。
    // 实盘量不得小于冻结量，否则现有占用业务将失去可兑现的实际库存。
    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryBalanceVO adjustInventoryCount(InventoryCountAdjustmentCommand command) {
        if (command.countedQuantity().compareTo(command.snapshotFrozen()) < 0) {
            throw new BusinessException(
                    InventoryErrorCode.INVARIANT_VIOLATION, "实盘数量不能小于冻结库存，需先处理占用业务");
        }
        // 同时核对盘点维度锁、三数量快照和库存版本，防止使用已经失效的盘点结果调整库存。
        int affected =
                balances.adjustCountIfSnapshotMatches(
                        command.countId(),
                        command.countLineId(),
                        command.warehouseId(),
                        command.locationId(),
                        command.skuId(),
                        command.snapshotVersion(),
                        command.snapshotActual(),
                        command.snapshotAvailable(),
                        command.snapshotFrozen(),
                        command.countedQuantity());
        if (affected != 1) {
            throw new BusinessException(
                    InventoryErrorCode.CONCURRENT_MODIFICATION, "盘点快照、维度锁或库存版本已变化，不能执行调整");
        }
        // 条件更新成功后读取最新余额，再用本次差量反推出更新前状态，形成连续的审计流水。
        InventoryBalanceEntity saved =
                balances.selectByDimension(
                        command.warehouseId(), command.locationId(), command.skuId());
        if (saved == null) {
            throw new BusinessException(InventoryErrorCode.BALANCE_NOT_FOUND);
        }
        InventoryBalanceState before =
                new InventoryBalanceState(
                        command.snapshotActual(),
                        command.snapshotAvailable(),
                        command.snapshotFrozen());
        InventoryBalanceState after =
                new InventoryBalanceState(
                        saved.getActualQuantity(),
                        saved.getAvailableQuantity(),
                        saved.getFrozenQuantity());
        InventoryLedgerEntity ledger =
                InventoryLedgerFactory.countAdjustment(before, after, command);
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
            if (balances.selectByDimension(
                            command.warehouseId(), command.locationId(), command.skuId())
                    == null) {
                throw new BusinessException(InventoryErrorCode.BALANCE_NOT_FOUND);
            }
            throw new BusinessException(insufficientError);
        }
        InventoryBalanceEntity saved =
                balances.selectByDimension(
                        command.warehouseId(), command.locationId(), command.skuId());
        if (saved == null || saved.getVersion() == null || saved.getVersion() <= 0) {
            throw new BusinessException(InventoryErrorCode.CONCURRENT_MODIFICATION);
        }
        InventoryBalanceState after =
                new InventoryBalanceState(
                        saved.getActualQuantity(),
                        saved.getAvailableQuantity(),
                        saved.getFrozenQuantity());
        InventoryBalanceState before =
                new InventoryBalanceState(
                        after.actualQuantity().subtract(change.actualChange()),
                        after.availableQuantity().subtract(change.availableChange()),
                        after.frozenQuantity().subtract(change.frozenChange()));
        InventoryChangeCommand inventoryChange =
                new InventoryChangeCommand(
                        command.warehouseId(),
                        command.locationId(),
                        command.skuId(),
                        saved.getVersion() - 1,
                        command.businessType(),
                        command.transferNo(),
                        change,
                        command.operatorId(),
                        command.operatorName());
        insertChangeLedger(before, after, inventoryChange);
        return InventoryViewConverter.balance(saved);
    }

    private void insertChangeLedger(
            InventoryBalanceState before,
            InventoryBalanceState after,
            InventoryChangeCommand command) {
        try {
            if (ledgers.insert(InventoryLedgerFactory.change(before, after, command)) != 1) {
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
                            command.warehouseId(), command.locationId(), command.skuId())
                    == null) {
                throw new BusinessException(InventoryErrorCode.BALANCE_NOT_FOUND);
            }
            throw new BusinessException(insufficientError);
        }

        InventoryBalanceEntity saved =
                balances.selectByDimension(
                        command.warehouseId(), command.locationId(), command.skuId());
        if (saved == null || saved.getVersion() == null || saved.getVersion() <= 0) {
            throw new BusinessException(InventoryErrorCode.CONCURRENT_MODIFICATION);
        }
        InventoryBalanceState after =
                new InventoryBalanceState(
                        saved.getActualQuantity(),
                        saved.getAvailableQuantity(),
                        saved.getFrozenQuantity());
        InventoryBalanceState before =
                new InventoryBalanceState(
                        after.actualQuantity().subtract(change.actualChange()),
                        after.availableQuantity().subtract(change.availableChange()),
                        after.frozenQuantity().subtract(change.frozenChange()));
        int versionBefore = saved.getVersion() - 1;
        InventoryChangeCommand inventoryChange =
                new InventoryChangeCommand(
                        command.warehouseId(),
                        command.locationId(),
                        command.skuId(),
                        versionBefore,
                        command.businessType(),
                        command.outboundNo(),
                        change,
                        command.operatorId(),
                        command.operatorName());
        try {
            if (ledgers.insert(InventoryLedgerFactory.change(before, after, inventoryChange))
                    != 1) {
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
}
