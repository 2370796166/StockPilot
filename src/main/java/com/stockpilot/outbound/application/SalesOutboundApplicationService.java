package com.stockpilot.outbound.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stockpilot.common.exception.BusinessException;
import com.stockpilot.inventory.application.InventoryMutationApplicationService;
import com.stockpilot.inventory.application.OutboundInventoryCommand;
import com.stockpilot.inventory.domain.InventoryBusinessType;
import com.stockpilot.masterdata.application.MasterDataReferenceApplicationService;
import com.stockpilot.masterdata.vo.PageResult;
import com.stockpilot.messaging.application.TransactionalOutboxApplicationService;
import com.stockpilot.messaging.domain.CompletionBusinessEvent;
import com.stockpilot.outbound.api.SalesOutboundErrorCode;
import com.stockpilot.outbound.domain.*;
import com.stockpilot.outbound.infrastructure.mapper.*;
import com.stockpilot.outbound.request.SalesOutboundRequests;
import com.stockpilot.outbound.vo.*;
import com.stockpilot.security.auth.StockPilotPrincipal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;

@Service
public class SalesOutboundApplicationService {
    private final SalesOutboundMapper outbounds;
    private final SalesOutboundLineMapper lines;
    private final MasterDataReferenceApplicationService masterData;
    private final InventoryMutationApplicationService inventory;
    private final TransactionalOutboxApplicationService outbox;

    public SalesOutboundApplicationService(SalesOutboundMapper outbounds, SalesOutboundLineMapper lines,
            MasterDataReferenceApplicationService masterData, InventoryMutationApplicationService inventory,
            TransactionalOutboxApplicationService outbox) {
        this.outbounds = outbounds;
        this.lines = lines;
        this.masterData = masterData;
        this.inventory = inventory;
        this.outbox = outbox;
    }

    @Transactional
    public SalesOutboundVO create(SalesOutboundRequests.Create request, StockPilotPrincipal actor) {
        requireActor(actor);
        List<SalesOutboundLineEntity> newLines = validateAndBuildLines(null, request.warehouseId(), request.lines());
        SalesOutboundEntity outbound = new SalesOutboundEntity();
        outbound.setOutboundNo(normalizeOutboundNo(request.outboundNo()));
        outbound.setWarehouseId(request.warehouseId());
        outbound.setStatus(SalesOutboundStatus.DRAFT);
        outbound.setRemark(normalizeRemark(request.remark()));
        outbound.setCreatedBy(actor.userId());
        outbound.setCreatedByName(actor.username());
        try {
            if (outbounds.insert(outbound) != 1) throw error(SalesOutboundErrorCode.PERSISTENCE_FAILURE);
        } catch (DuplicateKeyException exception) {
            throw error(SalesOutboundErrorCode.DUPLICATE_OUTBOUND_NO);
        }
        assignOutbound(outbound.getId(), request.warehouseId(), newLines);
        insertLines(newLines);
        return get(outbound.getId());
    }

    @Transactional
    public SalesOutboundVO update(long id, SalesOutboundRequests.Update request, StockPilotPrincipal actor) {
        requireActor(actor);
        SalesOutboundEntity current = requireLocked(id);
        requireState(current, SalesOutboundStatus.DRAFT);
        requireVersion(current, request.version());
        List<SalesOutboundLineEntity> newLines = validateAndBuildLines(id, request.warehouseId(), request.lines());
        lines.deleteByOutboundId(id);
        if (outbounds.updateDraft(id, request.version(), request.warehouseId(), normalizeRemark(request.remark())) != 1) {
            throw error(SalesOutboundErrorCode.CONCURRENT_MODIFICATION);
        }
        insertLines(newLines);
        return get(id);
    }

    @Transactional
    public SalesOutboundVO reserve(long id, SalesOutboundRequests.Transition request, StockPilotPrincipal actor) {
        requireActor(actor);
        SalesOutboundEntity current = requireLocked(id);
        if (current.getStatus() != SalesOutboundStatus.DRAFT) {
            if (current.getStatus() == SalesOutboundStatus.RESERVED) throw error(SalesOutboundErrorCode.ALREADY_RESERVED);
            throw invalidState(current, SalesOutboundStatus.DRAFT);
        }
        requireVersion(current, request.version());
        for (SalesOutboundLineEntity line : sorted(requireLines(id))) {
            inventory.freezeOutbound(command(current, line, InventoryBusinessType.OUTBOUND_FREEZE, actor));
        }
        if (outbounds.reserve(id, request.version(), actor.userId(), actor.username()) != 1) {
            throw error(SalesOutboundErrorCode.CONCURRENT_MODIFICATION);
        }
        return get(id);
    }

    @Transactional
    public SalesOutboundVO approve(long id, SalesOutboundRequests.Transition request, StockPilotPrincipal actor) {
        requireActor(actor);
        SalesOutboundEntity current = requireLocked(id);
        requireState(current, SalesOutboundStatus.RESERVED);
        requireVersion(current, request.version());
        if (outbounds.approve(id, request.version(), actor.userId(), actor.username()) != 1) {
            throw error(SalesOutboundErrorCode.CONCURRENT_MODIFICATION);
        }
        return get(id);
    }

    @Transactional
    public SalesOutboundVO complete(long id, StockPilotPrincipal actor) {
        requireActor(actor);
        SalesOutboundEntity current = requireLocked(id);
        if (current.getStatus() == SalesOutboundStatus.COMPLETED) throw error(SalesOutboundErrorCode.ALREADY_COMPLETED);
        requireState(current, SalesOutboundStatus.APPROVED);
        List<SalesOutboundLineEntity> outboundLines = sorted(requireLines(id));
        for (SalesOutboundLineEntity line : outboundLines) {
            inventory.shipOutbound(command(current, line, InventoryBusinessType.OUTBOUND_SHIP, actor));
        }
        if (outbounds.complete(id, current.getVersion(), actor.userId(), actor.username()) != 1) {
            throw error(SalesOutboundErrorCode.CONCURRENT_MODIFICATION);
        }
        outbox.enqueueSalesOutboundCompleted(id, current.getOutboundNo(), current.getWarehouseId(),
                outboundLines.stream().map(line -> new CompletionBusinessEvent.InventoryDimension(
                        line.getLocationId(), line.getSkuId())).toList());
        return get(id);
    }

    @Transactional
    public SalesOutboundVO cancel(long id, StockPilotPrincipal actor) {
        requireActor(actor);
        SalesOutboundEntity current = requireLocked(id);
        if (current.getStatus() == SalesOutboundStatus.COMPLETED) throw error(SalesOutboundErrorCode.ALREADY_COMPLETED);
        if (current.getStatus() == SalesOutboundStatus.CANCELLED) throw error(SalesOutboundErrorCode.ALREADY_CANCELLED);
        if (current.getStatus() != SalesOutboundStatus.RESERVED && current.getStatus() != SalesOutboundStatus.APPROVED) {
            throw invalidState(current, SalesOutboundStatus.RESERVED);
        }
        for (SalesOutboundLineEntity line : sorted(requireLines(id))) {
            inventory.releaseOutbound(command(current, line, InventoryBusinessType.OUTBOUND_RELEASE, actor));
        }
        if (outbounds.cancel(id, current.getVersion(), actor.userId(), actor.username()) != 1) {
            throw error(SalesOutboundErrorCode.CONCURRENT_MODIFICATION);
        }
        return get(id);
    }

    @Transactional(readOnly = true)
    public SalesOutboundVO get(long id) {
        SalesOutboundEntity outbound = outbounds.selectById(id);
        if (outbound == null) throw error(SalesOutboundErrorCode.NOT_FOUND);
        return toDetail(outbound, lines.selectByOutboundId(id));
    }

    @Transactional(readOnly = true)
    public PageResult<SalesOutboundSummaryVO> page(SalesOutboundRequests.PageQuery query) {
        if (StringUtils.hasText(query.getOutboundNo())) {
            query.setOutboundNo(query.getOutboundNo().trim().toUpperCase(Locale.ROOT));
        }
        IPage<SalesOutboundEntity> result = outbounds.selectPage(Page.of(query.getPage(), query.getSize()), query);
        return new PageResult<>(result.getRecords().stream().map(this::toSummary).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    private List<SalesOutboundLineEntity> validateAndBuildLines(Long outboundId, long warehouseId,
            List<SalesOutboundRequests.Line> requests) {
        if (requests == null || requests.isEmpty()) throw error(SalesOutboundErrorCode.EMPTY_LINES);
        Set<Dimension> dimensions = new HashSet<>();
        List<SalesOutboundLineEntity> result = new ArrayList<>(requests.size());
        int lineNo = 1;
        for (SalesOutboundRequests.Line request : requests) {
            if (request == null || request.locationId() == null || request.locationId() <= 0
                    || request.skuId() == null || request.skuId() <= 0) throw error(SalesOutboundErrorCode.INVALID_LINE);
            BigDecimal quantity = normalizeQuantity(request.quantity());
            if (!dimensions.add(new Dimension(request.locationId(), request.skuId()))) {
                throw new BusinessException(SalesOutboundErrorCode.INVALID_LINE, "同一销售出库单不能包含重复的库位和SKU维度");
            }
            masterData.requireEnabledInventoryDimension(warehouseId, request.locationId(), request.skuId());
            SalesOutboundLineEntity line = new SalesOutboundLineEntity();
            line.setOutboundId(outboundId);
            line.setWarehouseId(warehouseId);
            line.setLineNo(lineNo++);
            line.setLocationId(request.locationId());
            line.setSkuId(request.skuId());
            line.setQuantity(quantity);
            result.add(line);
        }
        return result;
    }

    private List<SalesOutboundLineEntity> requireLines(long id) {
        List<SalesOutboundLineEntity> result = lines.selectByOutboundId(id);
        if (result.isEmpty()) throw error(SalesOutboundErrorCode.EMPTY_LINES);
        return result;
    }

    private List<SalesOutboundLineEntity> sorted(List<SalesOutboundLineEntity> values) {
        return values.stream().sorted(Comparator.comparing(SalesOutboundLineEntity::getLocationId)
                .thenComparing(SalesOutboundLineEntity::getSkuId)).toList();
    }

    private OutboundInventoryCommand command(SalesOutboundEntity outbound, SalesOutboundLineEntity line,
            InventoryBusinessType type, StockPilotPrincipal actor) {
        return new OutboundInventoryCommand(outbound.getWarehouseId(), line.getLocationId(), line.getSkuId(),
                type, outbound.getOutboundNo(), line.getQuantity(), actor.userId(), actor.username());
    }

    private void assignOutbound(long id, long warehouseId, List<SalesOutboundLineEntity> values) {
        values.forEach(line -> { line.setOutboundId(id); line.setWarehouseId(warehouseId); });
    }

    private void insertLines(List<SalesOutboundLineEntity> values) {
        try {
            if (lines.insertBatch(values) != values.size()) throw error(SalesOutboundErrorCode.PERSISTENCE_FAILURE);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(SalesOutboundErrorCode.INVALID_LINE, "销售出库明细包含重复维度");
        }
    }

    private SalesOutboundEntity requireLocked(long id) {
        SalesOutboundEntity value = outbounds.selectByIdForUpdate(id);
        if (value == null) throw error(SalesOutboundErrorCode.NOT_FOUND);
        return value;
    }

    private void requireState(SalesOutboundEntity value, SalesOutboundStatus expected) {
        if (value.getStatus() != expected) throw invalidState(value, expected);
    }

    private BusinessException invalidState(SalesOutboundEntity value, SalesOutboundStatus expected) {
        return new BusinessException(SalesOutboundErrorCode.INVALID_STATE,
                "当前状态为" + value.getStatus() + "，要求状态为" + expected);
    }

    private void requireVersion(SalesOutboundEntity value, int expected) {
        if (!value.getVersion().equals(expected)) throw error(SalesOutboundErrorCode.CONCURRENT_MODIFICATION);
    }

    private void requireActor(StockPilotPrincipal actor) {
        if (actor == null || actor.userId() == null || actor.userId() <= 0 || !StringUtils.hasText(actor.username())) {
            throw new BusinessException(SalesOutboundErrorCode.PERSISTENCE_FAILURE, "当前操作人信息不完整");
        }
    }

    private String normalizeOutboundNo(String value) {
        if (!StringUtils.hasText(value)) throw new BusinessException(SalesOutboundErrorCode.INVALID_LINE, "销售出库单号不能为空");
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeRemark(String value) { return StringUtils.hasText(value) ? value.trim() : null; }

    private BigDecimal normalizeQuantity(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.scale() > 4 || value.precision() - value.scale() > 15) {
            throw new BusinessException(SalesOutboundErrorCode.INVALID_LINE, "出库数量必须大于0，且最多15位整数和4位小数");
        }
        return value.setScale(4);
    }

    private SalesOutboundVO toDetail(SalesOutboundEntity value, List<SalesOutboundLineEntity> outboundLines) {
        return new SalesOutboundVO(value.getId(), value.getOutboundNo(), value.getWarehouseId(), value.getStatus(),
                value.getRemark(), value.getCreatedBy(), value.getCreatedByName(), value.getReservedBy(),
                value.getReservedByName(), value.getReservedAt(), value.getApprovedBy(), value.getApprovedByName(),
                value.getApprovedAt(), value.getCompletedBy(), value.getCompletedByName(), value.getCompletedAt(),
                value.getCancelledBy(), value.getCancelledByName(), value.getCancelledAt(), value.getVersion(),
                value.getCreatedAt(), value.getUpdatedAt(), outboundLines.stream().map(line -> new SalesOutboundVO.Line(
                        line.getId(), line.getLineNo(), line.getLocationId(), line.getSkuId(), line.getQuantity())).toList());
    }

    private SalesOutboundSummaryVO toSummary(SalesOutboundEntity value) {
        return new SalesOutboundSummaryVO(value.getId(), value.getOutboundNo(), value.getWarehouseId(), value.getStatus(),
                value.getRemark(), value.getCreatedByName(), value.getVersion(), value.getCreatedAt(), value.getUpdatedAt());
    }

    private BusinessException error(SalesOutboundErrorCode code) { return new BusinessException(code); }
    private record Dimension(long locationId, long skuId) { }
}
