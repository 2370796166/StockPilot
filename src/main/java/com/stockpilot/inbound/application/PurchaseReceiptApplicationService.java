package com.stockpilot.inbound.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stockpilot.common.exception.BusinessException;
import com.stockpilot.inbound.api.PurchaseReceiptErrorCode;
import com.stockpilot.inbound.domain.PurchaseReceiptEntity;
import com.stockpilot.inbound.domain.PurchaseReceiptLineEntity;
import com.stockpilot.inbound.domain.PurchaseReceiptStatus;
import com.stockpilot.inbound.infrastructure.mapper.PurchaseReceiptLineMapper;
import com.stockpilot.inbound.infrastructure.mapper.PurchaseReceiptMapper;
import com.stockpilot.inbound.request.PurchaseReceiptRequests;
import com.stockpilot.inbound.vo.PurchaseReceiptSummaryVO;
import com.stockpilot.inbound.vo.PurchaseReceiptVO;
import com.stockpilot.inventory.application.InventoryMutationApplicationService;
import com.stockpilot.inventory.application.PurchaseReceiptInventoryCommand;
import com.stockpilot.masterdata.application.MasterDataReferenceApplicationService;
import com.stockpilot.masterdata.vo.PageResult;
import com.stockpilot.security.auth.StockPilotPrincipal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class PurchaseReceiptApplicationService {
    private final PurchaseReceiptMapper receipts;
    private final PurchaseReceiptLineMapper lines;
    private final MasterDataReferenceApplicationService masterData;
    private final InventoryMutationApplicationService inventory;

    public PurchaseReceiptApplicationService(
            PurchaseReceiptMapper receipts,
            PurchaseReceiptLineMapper lines,
            MasterDataReferenceApplicationService masterData,
            InventoryMutationApplicationService inventory) {
        this.receipts = receipts;
        this.lines = lines;
        this.masterData = masterData;
        this.inventory = inventory;
    }

    @Transactional
    public PurchaseReceiptVO create(PurchaseReceiptRequests.Create request, StockPilotPrincipal actor) {
        requireActor(actor);
        List<PurchaseReceiptLineEntity> newLines = validateAndBuildLines(
                null, request.warehouseId(), request.lines());

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

    @Transactional
    public PurchaseReceiptVO update(
            long id, PurchaseReceiptRequests.Update request, StockPilotPrincipal actor) {
        requireActor(actor);
        PurchaseReceiptEntity current = requireLocked(id);
        requireState(current, PurchaseReceiptStatus.DRAFT);
        if (!current.getVersion().equals(request.version())) {
            throw new BusinessException(PurchaseReceiptErrorCode.CONCURRENT_MODIFICATION);
        }
        List<PurchaseReceiptLineEntity> newLines = validateAndBuildLines(
                id, request.warehouseId(), request.lines());

        lines.deleteByReceiptId(id);
        if (receipts.updateDraft(id, request.version(), request.warehouseId(),
                normalizeRemark(request.remark())) != 1) {
            throw new BusinessException(PurchaseReceiptErrorCode.CONCURRENT_MODIFICATION);
        }
        insertLines(newLines);
        return get(id);
    }

    @Transactional
    public PurchaseReceiptVO submit(
            long id, PurchaseReceiptRequests.Transition request, StockPilotPrincipal actor) {
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

    @Transactional
    public PurchaseReceiptVO approve(
            long id, PurchaseReceiptRequests.Transition request, StockPilotPrincipal actor) {
        requireActor(actor);
        PurchaseReceiptEntity current = requireLocked(id);
        requireState(current, PurchaseReceiptStatus.SUBMITTED);
        requireVersion(current, request.version());
        if (receipts.approve(id, request.version(), actor.userId(), actor.username()) != 1) {
            throw new BusinessException(PurchaseReceiptErrorCode.CONCURRENT_MODIFICATION);
        }
        return get(id);
    }

    @Transactional
    public PurchaseReceiptVO complete(long id, StockPilotPrincipal actor) {
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
        receiptLines.stream()
                .sorted(Comparator.comparing(PurchaseReceiptLineEntity::getLocationId)
                        .thenComparing(PurchaseReceiptLineEntity::getSkuId))
                .forEach(line -> {
                    masterData.requireEnabledInventoryDimension(
                            current.getWarehouseId(), line.getLocationId(), line.getSkuId());
                    inventory.receivePurchase(new PurchaseReceiptInventoryCommand(
                            current.getWarehouseId(), line.getLocationId(), line.getSkuId(),
                            current.getReceiptNo(), line.getQuantity(), actor.userId(), actor.username()));
                });

        if (receipts.complete(id, current.getVersion(), actor.userId(), actor.username()) != 1) {
            throw new BusinessException(PurchaseReceiptErrorCode.CONCURRENT_MODIFICATION);
        }
        return get(id);
    }

    @Transactional(readOnly = true)
    public PurchaseReceiptVO get(long id) {
        PurchaseReceiptEntity receipt = receipts.selectById(id);
        if (receipt == null) {
            throw new BusinessException(PurchaseReceiptErrorCode.NOT_FOUND);
        }
        return toDetail(receipt, lines.selectByReceiptId(id));
    }

    @Transactional(readOnly = true)
    public PageResult<PurchaseReceiptSummaryVO> page(PurchaseReceiptRequests.PageQuery query) {
        if (StringUtils.hasText(query.getReceiptNo())) {
            query.setReceiptNo(query.getReceiptNo().trim().toUpperCase(Locale.ROOT));
        }
        IPage<PurchaseReceiptEntity> result = receipts.selectPage(
                Page.of(query.getPage(), query.getSize()), query);
        return new PageResult<>(result.getRecords().stream().map(this::toSummary).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    private List<PurchaseReceiptLineEntity> validateAndBuildLines(
            Long receiptId, long warehouseId, List<PurchaseReceiptRequests.Line> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BusinessException(PurchaseReceiptErrorCode.EMPTY_LINES);
        }
        Set<Dimension> dimensions = new HashSet<>();
        java.util.ArrayList<PurchaseReceiptLineEntity> result = new java.util.ArrayList<>(requests.size());
        int lineNo = 1;
        for (PurchaseReceiptRequests.Line request : requests) {
            if (request == null || request.locationId() == null || request.locationId() <= 0
                    || request.skuId() == null || request.skuId() <= 0) {
                throw new BusinessException(PurchaseReceiptErrorCode.INVALID_LINE);
            }
            BigDecimal quantity = normalizeQuantity(request.quantity());
            Dimension dimension = new Dimension(request.locationId(), request.skuId());
            if (!dimensions.add(dimension)) {
                throw new BusinessException(PurchaseReceiptErrorCode.INVALID_LINE,
                        "同一采购入库单不能包含重复的库位和SKU维度");
            }
            masterData.requireEnabledInventoryDimension(warehouseId, request.locationId(), request.skuId());
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

    private void assignReceipt(long receiptId, long warehouseId, List<PurchaseReceiptLineEntity> receiptLines) {
        receiptLines.forEach(line -> {
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
            throw new BusinessException(PurchaseReceiptErrorCode.INVALID_LINE,
                    "采购入库明细包含重复维度");
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
            throw new BusinessException(PurchaseReceiptErrorCode.INVALID_STATE,
                    "当前状态为" + receipt.getStatus() + "，要求状态为" + expected);
        }
    }

    private void requireVersion(PurchaseReceiptEntity receipt, int expectedVersion) {
        if (!receipt.getVersion().equals(expectedVersion)) {
            throw new BusinessException(PurchaseReceiptErrorCode.CONCURRENT_MODIFICATION);
        }
    }

    private void requireActor(StockPilotPrincipal actor) {
        if (actor == null || actor.userId() == null || actor.userId() <= 0
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
        if (quantity == null || quantity.signum() <= 0 || quantity.scale() > 4
                || quantity.precision() - quantity.scale() > 15) {
            throw new BusinessException(PurchaseReceiptErrorCode.INVALID_LINE,
                    "入库数量必须大于0，且最多15位整数和4位小数");
        }
        return quantity.setScale(4);
    }

    private PurchaseReceiptVO toDetail(
            PurchaseReceiptEntity receipt, List<PurchaseReceiptLineEntity> receiptLines) {
        return new PurchaseReceiptVO(
                receipt.getId(), receipt.getReceiptNo(), receipt.getWarehouseId(), receipt.getStatus(),
                receipt.getRemark(), receipt.getCreatedBy(), receipt.getCreatedByName(),
                receipt.getSubmittedBy(), receipt.getSubmittedByName(), receipt.getSubmittedAt(),
                receipt.getApprovedBy(), receipt.getApprovedByName(), receipt.getApprovedAt(),
                receipt.getCompletedBy(), receipt.getCompletedByName(), receipt.getCompletedAt(),
                receipt.getVersion(), receipt.getCreatedAt(), receipt.getUpdatedAt(),
                receiptLines.stream().map(line -> new PurchaseReceiptVO.Line(
                        line.getId(), line.getLineNo(), line.getLocationId(),
                        line.getSkuId(), line.getQuantity())).toList());
    }

    private PurchaseReceiptSummaryVO toSummary(PurchaseReceiptEntity receipt) {
        return new PurchaseReceiptSummaryVO(
                receipt.getId(), receipt.getReceiptNo(), receipt.getWarehouseId(), receipt.getStatus(),
                receipt.getRemark(), receipt.getCreatedByName(), receipt.getVersion(),
                receipt.getCreatedAt(), receipt.getUpdatedAt());
    }

    private record Dimension(long locationId, long skuId) {
    }
}
