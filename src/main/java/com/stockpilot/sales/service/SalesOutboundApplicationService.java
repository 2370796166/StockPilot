package com.stockpilot.sales.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stockpilot.inventory.domain.InventoryAvailabilityChanged;
import com.stockpilot.inventory.domain.InventoryBusinessType;
import com.stockpilot.inventory.service.InventoryMutationApplicationService;
import com.stockpilot.inventory.service.OutboundInventoryCommand;
import com.stockpilot.masterdata.service.MasterDataReferenceApplicationService;
import com.stockpilot.messaging.domain.CompletionBusinessEvent;
import com.stockpilot.messaging.service.TransactionalOutboxApplicationService;
import com.stockpilot.sales.api.SalesOutboundErrorCode;
import com.stockpilot.sales.domain.*;
import com.stockpilot.sales.mapper.*;
import com.stockpilot.sales.request.SalesOutboundRequests;
import com.stockpilot.sales.vo.*;
import com.stockpilot.shared.api.PageResult;
import com.stockpilot.shared.auth.AuthenticatedActor;
import com.stockpilot.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SalesOutboundApplicationService {
    @Transactional(readOnly = true, timeout = 10)
    public PageResult<SalesInventoryOrderVO> inventoryOrders(
            com.stockpilot.inventory.request.InventoryDimensionQuery query,
            String status,
            long page,
            long size) {
        if (query == null
                || status == null
                || !Set.of("UNFINISHED", "DRAFT", "RESERVED", "APPROVED", "COMPLETED", "CANCELLED")
                        .contains(status))
            throw new IllegalArgumentException("Invalid sales query");
        com.stockpilot.inventory.request.InventoryDimensionQuery.validatePage(page, size);
        var result = outbounds.selectInventoryOrders(Page.of(page, size), query, status);
        return new PageResult<>(
                result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Transactional(readOnly = true)
    public com.stockpilot.inventory.vo.InventoryFrozenSourcePageVO frozenSources(
            com.stockpilot.inventory.request.InventoryDimensionQuery query, long page, long size) {
        if (query == null) throw new IllegalArgumentException("Missing inventory scope");
        com.stockpilot.inventory.request.InventoryDimensionQuery.validatePage(page, size);
        var total = outbounds.selectFrozenTotal(query);
        var sources = outbounds.selectFrozenSources(Page.of(page, size), query);
        return new com.stockpilot.inventory.vo.InventoryFrozenSourcePageVO(
                total,
                new PageResult<>(
                        sources.getRecords(),
                        sources.getTotal(),
                        sources.getCurrent(),
                        sources.getSize()));
    }

    @Transactional(readOnly = true)
    public SalesOutboundVO getByNumber(String number) {
        if (number == null || !number.matches("[A-Za-z0-9_-]{2,64}"))
            throw new IllegalArgumentException("Invalid document number");
        Long id = outbounds.selectIdByNumber(number.toUpperCase(java.util.Locale.ROOT));
        if (id == null)
            throw new com.stockpilot.shared.exception.BusinessException(
                    SalesOutboundErrorCode.NOT_FOUND);
        return get(id);
    }

    private final SalesOutboundMapper outbounds;
    private final SalesOutboundLineMapper lines;
    private final MasterDataReferenceApplicationService masterData;
    private final InventoryMutationApplicationService inventory;
    private final TransactionalOutboxApplicationService outbox;
    private final ApplicationEventPublisher events;

    public SalesOutboundApplicationService(
            SalesOutboundMapper outbounds,
            SalesOutboundLineMapper lines,
            MasterDataReferenceApplicationService masterData,
            InventoryMutationApplicationService inventory,
            TransactionalOutboxApplicationService outbox,
            ApplicationEventPublisher events) {
        this.outbounds = outbounds;
        this.lines = lines;
        this.masterData = masterData;
        this.inventory = inventory;
        this.outbox = outbox;
        this.events = events;
    }

    // 创建销售出库草稿：校验库存维度主数据和明细唯一性，但此时不冻结或扣减库存。
    // 单头与明细在同一事务保存，数据库唯一约束负责最终阻止重复单号。
    @Transactional
    public SalesOutboundVO create(SalesOutboundRequests.Create request, AuthenticatedActor actor) {
        requireActor(actor);
        List<SalesOutboundLineEntity> newLines =
                validateAndBuildLines(null, request.warehouseId(), request.lines());
        SalesOutboundEntity outbound = new SalesOutboundEntity();
        outbound.setOutboundNo(normalizeOutboundNo(request.outboundNo()));
        outbound.setWarehouseId(request.warehouseId());
        outbound.setStatus(SalesOutboundStatus.DRAFT);
        outbound.setRemark(normalizeRemark(request.remark()));
        outbound.setCreatedBy(actor.userId());
        outbound.setCreatedByName(actor.username());
        try {
            if (outbounds.insert(outbound) != 1)
                throw error(SalesOutboundErrorCode.PERSISTENCE_FAILURE);
        } catch (DuplicateKeyException exception) {
            throw error(SalesOutboundErrorCode.DUPLICATE_OUTBOUND_NO);
        }
        assignOutbound(outbound.getId(), request.warehouseId(), newLines);
        insertLines(newLines);
        return get(outbound.getId());
    }

    // 修改销售出库草稿：锁定单据、校验版本并整体替换明细；已冻结后的单据禁止修改。
    @Transactional
    public SalesOutboundVO update(
            long id, SalesOutboundRequests.Update request, AuthenticatedActor actor) {
        requireActor(actor);
        SalesOutboundEntity current = requireLocked(id);
        requireState(current, SalesOutboundStatus.DRAFT);
        requireVersion(current, request.version());
        List<SalesOutboundLineEntity> newLines =
                validateAndBuildLines(id, request.warehouseId(), request.lines());
        lines.deleteByOutboundId(id);
        if (outbounds.updateDraft(
                        id,
                        request.version(),
                        request.warehouseId(),
                        normalizeRemark(request.remark()))
                != 1) {
            throw error(SalesOutboundErrorCode.CONCURRENT_MODIFICATION);
        }
        insertLines(newLines);
        return get(id);
    }

    // 预占销售库存：仅允许 DRAFT 转为 RESERVED，并将可用量转入冻结量，实际库存保持不变。
    // 每条冻结使用数据库原子数量条件，任一明细库存不足会回滚此前已经冻结的明细。
    @Transactional
    public SalesOutboundVO reserve(
            long id, SalesOutboundRequests.Transition request, AuthenticatedActor actor) {
        requireActor(actor);
        SalesOutboundEntity current = requireLocked(id);
        if (current.getStatus() != SalesOutboundStatus.DRAFT) {
            if (current.getStatus() == SalesOutboundStatus.RESERVED)
                throw error(SalesOutboundErrorCode.ALREADY_RESERVED);
            throw invalidState(current, SalesOutboundStatus.DRAFT);
        }
        requireVersion(current, request.version());
        // 统一按库位、SKU 排序，使竞争相同库存维度的单据采用一致的更新顺序。
        List<SalesOutboundLineEntity> affectedLines = sorted(requireLines(id));
        for (SalesOutboundLineEntity line : affectedLines) {
            inventory.freezeOutbound(
                    command(current, line, InventoryBusinessType.OUTBOUND_FREEZE, actor));
        }
        if (outbounds.reserve(id, request.version(), actor.userId(), actor.username()) != 1) {
            throw error(SalesOutboundErrorCode.CONCURRENT_MODIFICATION);
        }
        publishAvailability(
                current, InventoryAvailabilityChanged.Action.SALES_RESERVED, affectedLines);
        return get(id);
    }

    // 审核销售出库单：仅确认 RESERVED 单据可以出库，不重复冻结也不提前扣减实际库存。
    @Transactional
    public SalesOutboundVO approve(
            long id, SalesOutboundRequests.Transition request, AuthenticatedActor actor) {
        requireActor(actor);
        SalesOutboundEntity current = requireLocked(id);
        requireState(current, SalesOutboundStatus.RESERVED);
        requireVersion(current, request.version());
        if (outbounds.approve(id, request.version(), actor.userId(), actor.username()) != 1) {
            throw error(SalesOutboundErrorCode.CONCURRENT_MODIFICATION);
        }
        return get(id);
    }

    // 完成销售出库：将已冻结数量从实际库存和冻结库存中同时扣除，可用库存不再变化。
    // 全部库存流水、单据完成状态及 Outbox 事件原子提交，确保核心扣减不依赖 RabbitMQ。
    @Transactional
    public SalesOutboundVO complete(long id, AuthenticatedActor actor) {
        requireActor(actor);
        SalesOutboundEntity current = requireLocked(id);
        if (current.getStatus() == SalesOutboundStatus.COMPLETED)
            throw error(SalesOutboundErrorCode.ALREADY_COMPLETED);
        requireState(current, SalesOutboundStatus.APPROVED);
        List<SalesOutboundLineEntity> outboundLines = sorted(requireLines(id));
        // 冻结后的出库不再校验主数据启停状态，避免 SKU 后续停用导致已占用库存无法闭环。
        for (SalesOutboundLineEntity line : outboundLines) {
            inventory.shipOutbound(
                    command(current, line, InventoryBusinessType.OUTBOUND_SHIP, actor));
        }
        if (outbounds.complete(id, current.getVersion(), actor.userId(), actor.username()) != 1) {
            throw error(SalesOutboundErrorCode.CONCURRENT_MODIFICATION);
        }
        // RabbitMQ 不参与核心扣减；完成事件先作为可恢复事实写入当前 MySQL 事务。
        outbox.enqueueSalesOutboundCompleted(
                id,
                current.getOutboundNo(),
                current.getWarehouseId(),
                outboundLines.stream()
                        .map(
                                line ->
                                        new CompletionBusinessEvent.InventoryDimension(
                                                line.getLocationId(), line.getSkuId()))
                        .toList());
        return get(id);
    }

    // 取消尚未出库的销售单：RESERVED 或 APPROVED 状态均可释放冻结量并恢复可用量。
    // 已完成出库的单据不能通过普通取消反向恢复库存。
    @Transactional
    public SalesOutboundVO cancel(long id, AuthenticatedActor actor) {
        requireActor(actor);
        SalesOutboundEntity current = requireLocked(id);
        if (current.getStatus() == SalesOutboundStatus.COMPLETED)
            throw error(SalesOutboundErrorCode.ALREADY_COMPLETED);
        if (current.getStatus() == SalesOutboundStatus.CANCELLED)
            throw error(SalesOutboundErrorCode.ALREADY_CANCELLED);
        if (current.getStatus() != SalesOutboundStatus.RESERVED
                && current.getStatus() != SalesOutboundStatus.APPROVED) {
            throw invalidState(current, SalesOutboundStatus.RESERVED);
        }
        List<SalesOutboundLineEntity> affectedLines = sorted(requireLines(id));
        for (SalesOutboundLineEntity line : affectedLines) {
            inventory.releaseOutbound(
                    command(current, line, InventoryBusinessType.OUTBOUND_RELEASE, actor));
        }
        if (outbounds.cancel(id, current.getVersion(), actor.userId(), actor.username()) != 1) {
            throw error(SalesOutboundErrorCode.CONCURRENT_MODIFICATION);
        }
        publishAvailability(
                current, InventoryAvailabilityChanged.Action.SALES_CANCELLED, affectedLines);
        return get(id);
    }

    private void publishAvailability(
            SalesOutboundEntity order,
            InventoryAvailabilityChanged.Action action,
            List<SalesOutboundLineEntity> affectedLines) {
        events.publishEvent(
                new InventoryAvailabilityChanged(
                        action,
                        order.getId(),
                        order.getOutboundNo(),
                        order.getWarehouseId(),
                        affectedLines.stream()
                                .map(
                                        line ->
                                                new InventoryAvailabilityChanged.Dimension(
                                                        line.getLocationId(), line.getSkuId()))
                                .toList()));
    }

    // 查询销售出库详情，包含预占、审核、完成或取消等各阶段审计信息。
    @Transactional(readOnly = true)
    public SalesOutboundVO get(long id) {
        SalesOutboundEntity outbound = outbounds.selectById(id);
        if (outbound == null) throw error(SalesOutboundErrorCode.NOT_FOUND);
        return SalesOutboundViewAssembler.detail(outbound, lines.selectByOutboundId(id));
    }

    // 分页查询销售出库摘要；查询单号与持久化单号采用相同的大写规范。
    @Transactional(readOnly = true)
    public PageResult<SalesOutboundSummaryVO> page(SalesOutboundRequests.PageQuery query) {
        if (query == null || !query.isPeriodValid())
            throw new IllegalArgumentException("Invalid document period");
        if (StringUtils.hasText(query.getOutboundNo())) {
            query.setOutboundNo(query.getOutboundNo().trim().toUpperCase(Locale.ROOT));
        }
        IPage<SalesOutboundEntity> result =
                outbounds.selectPage(Page.of(query.getPage(), query.getSize()), query);
        return new PageResult<>(
                result.getRecords().stream().map(SalesOutboundViewAssembler::summary).toList(),
                result.getTotal(),
                result.getCurrent(),
                result.getSize());
    }

    private List<SalesOutboundLineEntity> validateAndBuildLines(
            Long outboundId, long warehouseId, List<SalesOutboundRequests.Line> requests) {
        if (requests == null || requests.isEmpty()) throw error(SalesOutboundErrorCode.EMPTY_LINES);
        Set<Dimension> dimensions = new HashSet<>();
        List<SalesOutboundLineEntity> result = new ArrayList<>(requests.size());
        int lineNo = 1;
        for (SalesOutboundRequests.Line request : requests) {
            if (request == null
                    || request.locationId() == null
                    || request.locationId() <= 0
                    || request.skuId() == null
                    || request.skuId() <= 0) throw error(SalesOutboundErrorCode.INVALID_LINE);
            BigDecimal quantity = normalizeQuantity(request.quantity());
            if (!dimensions.add(new Dimension(request.locationId(), request.skuId()))) {
                throw new BusinessException(
                        SalesOutboundErrorCode.INVALID_LINE, "同一销售出库单不能包含重复的库位和SKU维度");
            }
            masterData.requireEnabledInventoryDimension(
                    warehouseId, request.locationId(), request.skuId());
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
        return values.stream()
                .sorted(
                        Comparator.comparing(SalesOutboundLineEntity::getLocationId)
                                .thenComparing(SalesOutboundLineEntity::getSkuId))
                .toList();
    }

    private OutboundInventoryCommand command(
            SalesOutboundEntity outbound,
            SalesOutboundLineEntity line,
            InventoryBusinessType type,
            AuthenticatedActor actor) {
        return new OutboundInventoryCommand(
                outbound.getWarehouseId(),
                line.getLocationId(),
                line.getSkuId(),
                type,
                outbound.getOutboundNo(),
                line.getQuantity(),
                actor.userId(),
                actor.username());
    }

    private void assignOutbound(long id, long warehouseId, List<SalesOutboundLineEntity> values) {
        values.forEach(
                line -> {
                    line.setOutboundId(id);
                    line.setWarehouseId(warehouseId);
                });
    }

    private void insertLines(List<SalesOutboundLineEntity> values) {
        try {
            if (lines.insertBatch(values) != values.size())
                throw error(SalesOutboundErrorCode.PERSISTENCE_FAILURE);
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

    private BusinessException invalidState(
            SalesOutboundEntity value, SalesOutboundStatus expected) {
        return new BusinessException(
                SalesOutboundErrorCode.INVALID_STATE,
                "当前状态为" + value.getStatus() + "，要求状态为" + expected);
    }

    private void requireVersion(SalesOutboundEntity value, int expected) {
        if (!value.getVersion().equals(expected))
            throw error(SalesOutboundErrorCode.CONCURRENT_MODIFICATION);
    }

    private void requireActor(AuthenticatedActor actor) {
        if (actor == null
                || actor.userId() == null
                || actor.userId() <= 0
                || !StringUtils.hasText(actor.username())) {
            throw new BusinessException(SalesOutboundErrorCode.PERSISTENCE_FAILURE, "当前操作人信息不完整");
        }
    }

    private String normalizeOutboundNo(String value) {
        if (!StringUtils.hasText(value))
            throw new BusinessException(SalesOutboundErrorCode.INVALID_LINE, "销售出库单号不能为空");
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeRemark(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private BigDecimal normalizeQuantity(BigDecimal value) {
        if (value == null
                || value.signum() <= 0
                || value.scale() > 4
                || value.precision() - value.scale() > 15) {
            throw new BusinessException(
                    SalesOutboundErrorCode.INVALID_LINE, "出库数量必须大于0，且最多15位整数和4位小数");
        }
        return value.setScale(4);
    }

    private BusinessException error(SalesOutboundErrorCode code) {
        return new BusinessException(code);
    }

    private record Dimension(long locationId, long skuId) {}
}
