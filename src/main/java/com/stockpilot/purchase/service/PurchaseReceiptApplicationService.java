package com.stockpilot.purchase.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stockpilot.inventory.service.InventoryMutationApplicationService;
import com.stockpilot.inventory.service.PurchaseReceiptInventoryCommand;
import com.stockpilot.masterdata.service.MasterDataReferenceApplicationService;
import com.stockpilot.messaging.domain.CompletionBusinessEvent;
import com.stockpilot.messaging.service.TransactionalOutboxApplicationService;
import com.stockpilot.purchase.api.PurchaseReceiptErrorCode;
import com.stockpilot.purchase.domain.PurchaseReceiptEntity;
import com.stockpilot.purchase.domain.PurchaseReceiptLineEntity;
import com.stockpilot.purchase.domain.PurchaseReceiptStatus;
import com.stockpilot.purchase.mapper.PurchaseReceiptLineMapper;
import com.stockpilot.purchase.mapper.PurchaseReceiptMapper;
import com.stockpilot.purchase.request.PurchaseReceiptRequests;
import com.stockpilot.purchase.vo.PurchaseReceiptSummaryVO;
import com.stockpilot.purchase.vo.PurchaseReceiptVO;
import com.stockpilot.shared.api.PageResult;
import com.stockpilot.shared.auth.AuthenticatedActor;
import com.stockpilot.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PurchaseReceiptApplicationService {
    @Transactional(readOnly = true)
    public PurchaseReceiptVO getByNumber(String number) {
        if (number == null || !number.matches("[A-Za-z0-9_-]{2,64}"))
            throw new IllegalArgumentException("Invalid document number");
        Long id = receipts.selectIdByNumber(number.toUpperCase(java.util.Locale.ROOT));
        if (id == null)
            throw new com.stockpilot.shared.exception.BusinessException(
                    PurchaseReceiptErrorCode.NOT_FOUND);
        return get(id);
    }

    private final PurchaseReceiptMapper receipts;
    private final PurchaseReceiptLineMapper lines;
    private final MasterDataReferenceApplicationService masterData;
    private final InventoryMutationApplicationService inventory;
    private final TransactionalOutboxApplicationService outbox;

    public PurchaseReceiptApplicationService(
            PurchaseReceiptMapper receipts,
            PurchaseReceiptLineMapper lines,
            MasterDataReferenceApplicationService masterData,
            InventoryMutationApplicationService inventory,
            TransactionalOutboxApplicationService outbox) {
        this.receipts = receipts;
        this.lines = lines;
        this.masterData = masterData;
        this.inventory = inventory;
        this.outbox = outbox;
    }

    // 创建采购入库草稿：校验仓库、库位、SKU 和明细唯一性，规范化单号后一次性保存单头与明细。
    // 单号由数据库唯一约束兜底，任何明细保存失败都会回滚整张单据。
    @Transactional
    public PurchaseReceiptVO create(
            PurchaseReceiptRequests.Create request, AuthenticatedActor actor) {
        requireActor(actor);
        List<PurchaseReceiptLineEntity> newLines =
                validateAndBuildLines(null, request.warehouseId(), request.lines());

        PurchaseReceiptEntity receipt = new PurchaseReceiptEntity();
        receipt.setReceiptNo(normalizeReceiptNo(request.receiptNo()));
        receipt.setWarehouseId(request.warehouseId());
        receipt.setStatus(PurchaseReceiptStatus.DRAFT);
        receipt.setRemark(normalizeRemark(request.remark()));
        receipt.setCreatedBy(actor.userId());
        receipt.setCreatedByName(actor.username());
        try {
            if (receipts.insert(receipt) != 1) {
                throw new BusinessException(PurchaseReceiptErrorCode.PERSISTENCE_FAILURE);
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(PurchaseReceiptErrorCode.DUPLICATE_RECEIPT_NO);
        }
        assignReceipt(receipt.getId(), request.warehouseId(), newLines);
        insertLines(newLines);
        return get(receipt.getId());
    }

    // 修改采购入库草稿：先锁定单据并校验 DRAFT 状态与版本，再整体替换明细。
    // 删除旧明细、更新单头和插入新明细处于同一事务，不会留下半更新状态。
    @Transactional
    public PurchaseReceiptVO update(
            long id, PurchaseReceiptRequests.Update request, AuthenticatedActor actor) {
        requireActor(actor);
        PurchaseReceiptEntity current = requireLocked(id);
        requireState(current, PurchaseReceiptStatus.DRAFT);
        if (!current.getVersion().equals(request.version())) {
            throw new BusinessException(PurchaseReceiptErrorCode.CONCURRENT_MODIFICATION);
        }
        List<PurchaseReceiptLineEntity> newLines =
                validateAndBuildLines(id, request.warehouseId(), request.lines());

        lines.deleteByReceiptId(id);
        if (receipts.updateDraft(
                        id,
                        request.version(),
                        request.warehouseId(),
                        normalizeRemark(request.remark()))
                != 1) {
            throw new BusinessException(PurchaseReceiptErrorCode.CONCURRENT_MODIFICATION);
        }
        insertLines(newLines);
        return get(id);
    }

    // 提交采购入库单：仅允许 DRAFT 转为 SUBMITTED，并拒绝没有明细的空单据。
    // 行锁和版本条件共同防止两个请求同时推进同一张单据。
    @Transactional
    public PurchaseReceiptVO submit(
            long id, PurchaseReceiptRequests.Transition request, AuthenticatedActor actor) {
        requireActor(actor);
        PurchaseReceiptEntity current = requireLocked(id);
        requireState(current, PurchaseReceiptStatus.DRAFT);
        requireVersion(current, request.version());
        if (lines.countByReceiptId(id) == 0) {
            throw new BusinessException(PurchaseReceiptErrorCode.EMPTY_LINES);
        }
        if (receipts.submit(id, request.version(), actor.userId(), actor.username()) != 1) {
            throw new BusinessException(PurchaseReceiptErrorCode.CONCURRENT_MODIFICATION);
        }
        return get(id);
    }

    // 审核采购入库单：仅允许 SUBMITTED 转为 APPROVED，记录审核人与审核时间。
    // 此步骤只确认业务单据，不在审核阶段提前增加库存。
    @Transactional
    public PurchaseReceiptVO approve(
            long id, PurchaseReceiptRequests.Transition request, AuthenticatedActor actor) {
        requireActor(actor);
        PurchaseReceiptEntity current = requireLocked(id);
        requireState(current, PurchaseReceiptStatus.SUBMITTED);
        requireVersion(current, request.version());
        if (receipts.approve(id, request.version(), actor.userId(), actor.username()) != 1) {
            throw new BusinessException(PurchaseReceiptErrorCode.CONCURRENT_MODIFICATION);
        }
        return get(id);
    }

    // 完成采购入库：锁定已审核单据，将每条明细同步增加实际库存和可用库存并写入不可变流水。
    // 单据状态、全部库存变化、流水和 Outbox 事件属于同一 MySQL 本地事务，失败时整体回滚。
    @Transactional
    public PurchaseReceiptVO complete(long id, AuthenticatedActor actor) {
        requireActor(actor);
        PurchaseReceiptEntity current = requireLocked(id);
        if (current.getStatus() == PurchaseReceiptStatus.COMPLETED) {
            throw new BusinessException(PurchaseReceiptErrorCode.ALREADY_COMPLETED);
        }
        requireState(current, PurchaseReceiptStatus.APPROVED);

        List<PurchaseReceiptLineEntity> receiptLines = lines.selectByReceiptId(id);
        if (receiptLines.isEmpty()) {
            throw new BusinessException(PurchaseReceiptErrorCode.EMPTY_LINES);
        }
        // 使用稳定的库存维度顺序执行多明细入库，降低并发单据发生数据库死锁的概率。
        receiptLines.stream()
                .sorted(
                        Comparator.comparing(PurchaseReceiptLineEntity::getLocationId)
                                .thenComparing(PurchaseReceiptLineEntity::getSkuId))
                .forEach(
                        line -> {
                            // 库存服务在写入前统一校验基础资料，避免每条明细重复查询。
                            inventory.receivePurchase(
                                    new PurchaseReceiptInventoryCommand(
                                            current.getWarehouseId(),
                                            line.getLocationId(),
                                            line.getSkuId(),
                                            current.getReceiptNo(),
                                            line.getQuantity(),
                                            actor.userId(),
                                            actor.username()));
                        });

        if (receipts.complete(id, current.getVersion(), actor.userId(), actor.username()) != 1) {
            throw new BusinessException(PurchaseReceiptErrorCode.CONCURRENT_MODIFICATION);
        }
        // 这里只写入同库 Outbox；单据、库存、流水和待发布事件随当前事务共同提交或回滚。
        outbox.enqueuePurchaseReceiptCompleted(
                id,
                current.getReceiptNo(),
                current.getWarehouseId(),
                receiptLines.stream()
                        .map(
                                line ->
                                        new CompletionBusinessEvent.InventoryDimension(
                                                line.getLocationId(), line.getSkuId()))
                        .toList());
        return get(id);
    }

    // 查询采购入库详情，同时返回单头状态、各阶段操作人以及完整明细。
    @Transactional(readOnly = true)
    public PurchaseReceiptVO get(long id) {
        PurchaseReceiptEntity receipt = receipts.selectById(id);
        if (receipt == null) {
            throw new BusinessException(PurchaseReceiptErrorCode.NOT_FOUND);
        }
        return PurchaseReceiptViewAssembler.detail(receipt, lines.selectByReceiptId(id));
    }

    // 分页查询采购入库摘要；单号查询统一转为大写以匹配创建时的规范化规则。
    @Transactional(readOnly = true)
    public PageResult<PurchaseReceiptSummaryVO> page(PurchaseReceiptRequests.PageQuery query) {
        if (query == null || !query.isPeriodValid())
            throw new IllegalArgumentException("Invalid document period");
        if (StringUtils.hasText(query.getReceiptNo())) {
            query.setReceiptNo(query.getReceiptNo().trim().toUpperCase(Locale.ROOT));
        }
        IPage<PurchaseReceiptEntity> result =
                receipts.selectPage(Page.of(query.getPage(), query.getSize()), query);
        return new PageResult<>(
                result.getRecords().stream().map(PurchaseReceiptViewAssembler::summary).toList(),
                result.getTotal(),
                result.getCurrent(),
                result.getSize());
    }

    private List<PurchaseReceiptLineEntity> validateAndBuildLines(
            Long receiptId, long warehouseId, List<PurchaseReceiptRequests.Line> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BusinessException(PurchaseReceiptErrorCode.EMPTY_LINES);
        }
        Set<Dimension> dimensions = new HashSet<>();
        java.util.ArrayList<PurchaseReceiptLineEntity> result =
                new java.util.ArrayList<>(requests.size());
        int lineNo = 1;
        for (PurchaseReceiptRequests.Line request : requests) {
            if (request == null
                    || request.locationId() == null
                    || request.locationId() <= 0
                    || request.skuId() == null
                    || request.skuId() <= 0) {
                throw new BusinessException(PurchaseReceiptErrorCode.INVALID_LINE);
            }
            BigDecimal quantity = normalizeQuantity(request.quantity());
            Dimension dimension = new Dimension(request.locationId(), request.skuId());
            if (!dimensions.add(dimension)) {
                throw new BusinessException(
                        PurchaseReceiptErrorCode.INVALID_LINE, "同一采购入库单不能包含重复的库位和SKU维度");
            }
            masterData.requireEnabledInventoryDimension(
                    warehouseId, request.locationId(), request.skuId());
            PurchaseReceiptLineEntity line = new PurchaseReceiptLineEntity();
            line.setReceiptId(receiptId);
            line.setWarehouseId(warehouseId);
            line.setLineNo(lineNo++);
            line.setLocationId(request.locationId());
            line.setSkuId(request.skuId());
            line.setQuantity(quantity);
            result.add(line);
        }
        return result;
    }

    private void assignReceipt(
            long receiptId, long warehouseId, List<PurchaseReceiptLineEntity> receiptLines) {
        receiptLines.forEach(
                line -> {
                    line.setReceiptId(receiptId);
                    line.setWarehouseId(warehouseId);
                });
    }

    private void insertLines(List<PurchaseReceiptLineEntity> receiptLines) {
        try {
            if (lines.insertBatch(receiptLines) != receiptLines.size()) {
                throw new BusinessException(PurchaseReceiptErrorCode.PERSISTENCE_FAILURE);
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(PurchaseReceiptErrorCode.INVALID_LINE, "采购入库明细包含重复维度");
        }
    }

    private PurchaseReceiptEntity requireLocked(long id) {
        PurchaseReceiptEntity receipt = receipts.selectByIdForUpdate(id);
        if (receipt == null) {
            throw new BusinessException(PurchaseReceiptErrorCode.NOT_FOUND);
        }
        return receipt;
    }

    private void requireState(PurchaseReceiptEntity receipt, PurchaseReceiptStatus expected) {
        if (receipt.getStatus() != expected) {
            throw new BusinessException(
                    PurchaseReceiptErrorCode.INVALID_STATE,
                    "当前状态为" + receipt.getStatus() + "，要求状态为" + expected);
        }
    }

    private void requireVersion(PurchaseReceiptEntity receipt, int expectedVersion) {
        if (!receipt.getVersion().equals(expectedVersion)) {
            throw new BusinessException(PurchaseReceiptErrorCode.CONCURRENT_MODIFICATION);
        }
    }

    private void requireActor(AuthenticatedActor actor) {
        if (actor == null
                || actor.userId() == null
                || actor.userId() <= 0
                || !StringUtils.hasText(actor.username())) {
            throw new BusinessException(PurchaseReceiptErrorCode.PERSISTENCE_FAILURE, "当前操作人信息不完整");
        }
    }

    private String normalizeReceiptNo(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(PurchaseReceiptErrorCode.INVALID_LINE, "采购入库单号不能为空");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeRemark(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private BigDecimal normalizeQuantity(BigDecimal quantity) {
        if (quantity == null
                || quantity.signum() <= 0
                || quantity.scale() > 4
                || quantity.precision() - quantity.scale() > 15) {
            throw new BusinessException(
                    PurchaseReceiptErrorCode.INVALID_LINE, "入库数量必须大于0，且最多15位整数和4位小数");
        }
        return quantity.setScale(4);
    }

    private record Dimension(long locationId, long skuId) {}
}
