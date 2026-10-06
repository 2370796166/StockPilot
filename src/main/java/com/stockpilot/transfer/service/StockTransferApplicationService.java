package com.stockpilot.transfer.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stockpilot.inventory.domain.InventoryAvailabilityChanged;
import com.stockpilot.inventory.domain.InventoryBusinessType;
import com.stockpilot.inventory.service.InventoryMutationApplicationService;
import com.stockpilot.inventory.service.TransferInventoryCommand;
import com.stockpilot.masterdata.service.MasterDataReferenceApplicationService;
import com.stockpilot.shared.api.PageResult;
import com.stockpilot.shared.auth.AuthenticatedActor;
import com.stockpilot.shared.exception.BusinessException;
import com.stockpilot.transfer.api.StockTransferErrorCode;
import com.stockpilot.transfer.domain.*;
import com.stockpilot.transfer.mapper.*;
import com.stockpilot.transfer.request.StockTransferRequests;
import com.stockpilot.transfer.vo.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class StockTransferApplicationService {
    @Transactional(readOnly = true)
    public com.stockpilot.inventory.vo.InventoryFrozenSourcePageVO frozenSources(
            com.stockpilot.inventory.request.InventoryDimensionQuery query, long page, long size) {
        if (query == null) throw new IllegalArgumentException("Missing inventory scope");
        com.stockpilot.inventory.request.InventoryDimensionQuery.validatePage(page, size);
        var total = transfers.selectFrozenTotal(query);
        var sources = transfers.selectFrozenSources(Page.of(page, size), query);
        return new com.stockpilot.inventory.vo.InventoryFrozenSourcePageVO(
                total,
                new PageResult<>(
                        sources.getRecords(),
                        sources.getTotal(),
                        sources.getCurrent(),
                        sources.getSize()));
    }

    @Transactional(readOnly = true)
    public StockTransferVO getByNumber(String number) {
        if (number == null || !number.matches("[A-Za-z0-9_-]{2,64}"))
            throw new IllegalArgumentException("Invalid document number");
        Long id = transfers.selectIdByNumber(number.toUpperCase(java.util.Locale.ROOT));
        if (id == null)
            throw new com.stockpilot.shared.exception.BusinessException(
                    StockTransferErrorCode.NOT_FOUND);
        return get(id);
    }

    private final StockTransferMapper transfers;
    private final StockTransferLineMapper lines;
    private final StockTransferTransitMapper transit;
    private final MasterDataReferenceApplicationService masterData;
    private final InventoryMutationApplicationService inventory;
    private final ApplicationEventPublisher events;

    public StockTransferApplicationService(
            StockTransferMapper stockTransferMapper,
            StockTransferLineMapper stockTransferLineMapper,
            StockTransferTransitMapper stockTransferTransitMapper,
            MasterDataReferenceApplicationService masterDataReferenceService,
            InventoryMutationApplicationService inventoryMutationService,
            ApplicationEventPublisher events) {
        transfers = stockTransferMapper;
        lines = stockTransferLineMapper;
        transit = stockTransferTransitMapper;
        masterData = masterDataReferenceService;
        inventory = inventoryMutationService;
        this.events = events;
    }

    // 创建调拨草稿：源仓和目标仓必须不同，每个源维度及目标维度在同一单据内均不可重复。
    // 此阶段只保存调拨计划，不冻结也不移动任何库存。
    @Transactional
    public StockTransferVO create(StockTransferRequests.Create r, AuthenticatedActor actor) {
        requireActor(actor);
        validateWarehouses(r.sourceWarehouseId(), r.targetWarehouseId());
        List<StockTransferLineEntity> values =
                buildLines(null, r.sourceWarehouseId(), r.targetWarehouseId(), r.lines());
        StockTransferEntity value = new StockTransferEntity();
        value.setTransferNo(normalizeNo(r.transferNo()));
        value.setSourceWarehouseId(r.sourceWarehouseId());
        value.setTargetWarehouseId(r.targetWarehouseId());
        value.setStatus(StockTransferStatus.DRAFT);
        value.setRemark(normalizeRemark(r.remark()));
        value.setCreatedBy(actor.userId());
        value.setCreatedByName(actor.username());
        try {
            if (transfers.insert(value) != 1)
                throw error(StockTransferErrorCode.PERSISTENCE_FAILURE);
        } catch (DuplicateKeyException e) {
            throw error(StockTransferErrorCode.DUPLICATE_NO);
        }
        assign(value.getId(), r.sourceWarehouseId(), r.targetWarehouseId(), values);
        insertLines(values);
        return get(value.getId());
    }

    // 修改调拨草稿：锁定 DRAFT 单据、校验版本后整体替换明细；提交后的调拨内容不可再编辑。
    @Transactional
    public StockTransferVO update(
            long id, StockTransferRequests.Update r, AuthenticatedActor actor) {
        requireActor(actor);
        StockTransferEntity current = locked(id);
        requireState(current, StockTransferStatus.DRAFT);
        requireVersion(current, r.version());
        validateWarehouses(r.sourceWarehouseId(), r.targetWarehouseId());
        List<StockTransferLineEntity> values =
                buildLines(id, r.sourceWarehouseId(), r.targetWarehouseId(), r.lines());
        lines.deleteByTransferId(id);
        if (transfers.updateDraft(
                        id,
                        r.version(),
                        r.sourceWarehouseId(),
                        r.targetWarehouseId(),
                        normalizeRemark(r.remark()))
                != 1) throw error(StockTransferErrorCode.CONCURRENT_MODIFICATION);
        insertLines(values);
        return get(id);
    }

    // 提交调拨：按源维度稳定排序并冻结源仓可用库存，随后将状态推进到 SUBMITTED。
    // 任一明细冻结失败会回滚整张调拨单已经产生的冻结和流水。
    @Transactional
    public StockTransferVO submit(
            long id, StockTransferRequests.Transition r, AuthenticatedActor actor) {
        requireActor(actor);
        StockTransferEntity current = locked(id);
        requireState(current, StockTransferStatus.DRAFT);
        requireVersion(current, r.version());
        List<StockTransferLineEntity> affectedLines = sourceSorted(requireLines(id));
        for (StockTransferLineEntity line : affectedLines)
            inventory.freezeTransfer(
                    sourceCommand(current, line, InventoryBusinessType.TRANSFER_FREEZE, actor));
        transition(current, StockTransferStatus.DRAFT, StockTransferStatus.SUBMITTED, actor);
        publishAvailability(
                current,
                InventoryAvailabilityChanged.Action.TRANSFER_SUBMITTED,
                false,
                affectedLines);
        return get(id);
    }

    // 审核调拨：仅将 SUBMITTED 单据确认为 APPROVED，不在审核阶段重复改变库存。
    @Transactional
    public StockTransferVO approve(
            long id, StockTransferRequests.Transition r, AuthenticatedActor actor) {
        requireActor(actor);
        StockTransferEntity current = locked(id);
        requireState(current, StockTransferStatus.SUBMITTED);
        requireVersion(current, r.version());
        transition(current, StockTransferStatus.SUBMITTED, StockTransferStatus.APPROVED, actor);
        return get(id);
    }

    // 调拨出库：从源仓扣除实际量和冻结量，并为每条明细建立独立在途记录。
    // 源库存、在途事实和 OUTBOUND_COMPLETED 状态在同一事务内原子提交。
    @Transactional
    public StockTransferVO dispatch(long id, AuthenticatedActor actor) {
        requireActor(actor);
        StockTransferEntity current = locked(id);
        if (current.getStatus() == StockTransferStatus.OUTBOUND_COMPLETED
                || current.getStatus() == StockTransferStatus.IN_TRANSIT)
            throw error(StockTransferErrorCode.ALREADY_OUTBOUND);
        if (current.getStatus() == StockTransferStatus.COMPLETED)
            throw error(StockTransferErrorCode.ALREADY_COMPLETED);
        requireState(current, StockTransferStatus.APPROVED);
        // 调出后数量不再属于源仓，独立在途记录用于表达尚未被目标仓接收的库存事实。
        for (StockTransferLineEntity line : sourceSorted(requireLines(id))) {
            inventory.outboundTransfer(
                    sourceCommand(current, line, InventoryBusinessType.TRANSFER_OUT, actor));
            StockTransferTransitEntity record = new StockTransferTransitEntity();
            record.setTransferId(id);
            record.setTransferLineId(line.getId());
            record.setOutboundQuantity(line.getQuantity());
            record.setInTransitQuantity(line.getQuantity());
            record.setReceivedQuantity(BigDecimal.ZERO.setScale(4));
            try {
                if (transit.insert(record) != 1)
                    throw error(StockTransferErrorCode.TRANSIT_CONFLICT);
            } catch (DuplicateKeyException e) {
                throw error(StockTransferErrorCode.TRANSIT_CONFLICT);
            }
        }
        transition(
                current,
                StockTransferStatus.APPROVED,
                StockTransferStatus.OUTBOUND_COMPLETED,
                actor);
        return get(id);
    }

    // 确认运输开始：要求每条调拨明细都有完整在途记录，再将单据推进到 IN_TRANSIT。
    @Transactional
    public StockTransferVO startTransit(
            long id, StockTransferRequests.Transition r, AuthenticatedActor actor) {
        requireActor(actor);
        StockTransferEntity current = locked(id);
        requireState(current, StockTransferStatus.OUTBOUND_COMPLETED);
        requireVersion(current, r.version());
        if (transit.selectByTransferId(id).size() != requireLines(id).size())
            throw error(StockTransferErrorCode.TRANSIT_CONFLICT);
        transition(
                current,
                StockTransferStatus.OUTBOUND_COMPLETED,
                StockTransferStatus.IN_TRANSIT,
                actor);
        return get(id);
    }

    // 目标仓收货：逐条增加目标库存并结清对应在途量，全部完成后将调拨单标记为 COMPLETED。
    // 调入失败、在途数量不符或状态更新失败都会回滚本次整单收货。
    @Transactional
    public StockTransferVO receive(long id, AuthenticatedActor actor) {
        requireActor(actor);
        StockTransferEntity current = locked(id);
        if (current.getStatus() == StockTransferStatus.COMPLETED)
            throw error(StockTransferErrorCode.ALREADY_COMPLETED);
        requireState(current, StockTransferStatus.IN_TRANSIT);
        // 按目标库存维度稳定排序，降低多明细收货并发更新时的死锁风险。
        List<StockTransferLineEntity> values = targetSorted(requireLines(id));
        Map<Long, StockTransferTransitEntity> records = new HashMap<>();
        for (StockTransferTransitEntity record : transit.selectByTransferId(id))
            records.put(record.getTransferLineId(), record);
        if (records.size() != values.size()) throw error(StockTransferErrorCode.TRANSIT_CONFLICT);
        for (StockTransferLineEntity line : values) {
            StockTransferTransitEntity record = records.get(line.getId());
            if (record == null
                    || !"IN_TRANSIT".equals(record.getStatus())
                    || record.getInTransitQuantity().compareTo(line.getQuantity()) != 0)
                throw error(StockTransferErrorCode.TRANSIT_CONFLICT);
            // 目标库存增加和对应在途记录结清处于同一事务，任一步失败都会使整单回滚。
            inventory.inboundTransfer(targetCommand(current, line, actor));
            if (transit.receive(line.getId(), line.getQuantity()) != 1)
                throw error(StockTransferErrorCode.TRANSIT_CONFLICT);
        }
        transition(current, StockTransferStatus.IN_TRANSIT, StockTransferStatus.COMPLETED, actor);
        publishAvailability(
                current, InventoryAvailabilityChanged.Action.TRANSFER_RECEIVED, true, values);
        return get(id);
    }

    // 取消调拨：仅 SUBMITTED 和 APPROVED 状态可释放源仓冻结；调出后禁止普通取消。
    @Transactional
    public StockTransferVO cancel(long id, AuthenticatedActor actor) {
        requireActor(actor);
        StockTransferEntity current = locked(id);
        if (current.getStatus() == StockTransferStatus.CANCELLED)
            throw error(StockTransferErrorCode.ALREADY_CANCELLED);
        if (current.getStatus() == StockTransferStatus.OUTBOUND_COMPLETED
                || current.getStatus() == StockTransferStatus.IN_TRANSIT
                || current.getStatus() == StockTransferStatus.COMPLETED)
            throw error(StockTransferErrorCode.ALREADY_OUTBOUND);
        if (current.getStatus() != StockTransferStatus.SUBMITTED
                && current.getStatus() != StockTransferStatus.APPROVED)
            throw error(StockTransferErrorCode.INVALID_STATE);
        StockTransferStatus before = current.getStatus();
        List<StockTransferLineEntity> affectedLines = sourceSorted(requireLines(id));
        for (StockTransferLineEntity line : affectedLines)
            inventory.releaseTransfer(
                    sourceCommand(current, line, InventoryBusinessType.TRANSFER_RELEASE, actor));
        transition(current, before, StockTransferStatus.CANCELLED, actor);
        publishAvailability(
                current,
                InventoryAvailabilityChanged.Action.TRANSFER_CANCELLED,
                false,
                affectedLines);
        return get(id);
    }

    // 查询调拨详情，同时返回调拨明细和每条明细的调出、在途、已收货数量。
    @Transactional(readOnly = true)
    public StockTransferVO get(long id) {
        StockTransferEntity value = transfers.selectById(id);
        if (value == null) throw error(StockTransferErrorCode.NOT_FOUND);
        return StockTransferViewAssembler.detail(
                value, lines.selectByTransferId(id), transit.selectByTransferId(id));
    }

    // 分页查询调拨摘要，支持按规范化后的调拨单号和业务状态筛选。
    @Transactional(readOnly = true)
    public PageResult<StockTransferSummaryVO> page(StockTransferRequests.PageQuery q) {
        if (StringUtils.hasText(q.getTransferNo()))
            q.setTransferNo(q.getTransferNo().trim().toUpperCase(Locale.ROOT));
        IPage<StockTransferEntity> p = transfers.selectPage(Page.of(q.getPage(), q.getSize()), q);
        return new PageResult<>(
                p.getRecords().stream().map(StockTransferViewAssembler::summary).toList(),
                p.getTotal(),
                p.getCurrent(),
                p.getSize());
    }

    private List<StockTransferLineEntity> buildLines(
            Long id, long source, long target, List<StockTransferRequests.Line> requests) {
        if (requests == null || requests.isEmpty())
            throw error(StockTransferErrorCode.INVALID_LINE);
        Set<SourceDimension> sources = new HashSet<>();
        Set<TargetDimension> targets = new HashSet<>();
        List<StockTransferLineEntity> result = new ArrayList<>();
        int no = 1;
        for (StockTransferRequests.Line r : requests) {
            if (r == null
                    || r.sourceLocationId() == null
                    || r.targetLocationId() == null
                    || r.skuId() == null) throw error(StockTransferErrorCode.INVALID_LINE);
            BigDecimal q = quantity(r.quantity());
            if (!sources.add(new SourceDimension(r.sourceLocationId(), r.skuId())))
                throw new BusinessException(
                        StockTransferErrorCode.INVALID_LINE, "同一调拨单的源库位和SKU维度不能重复");
            if (!targets.add(new TargetDimension(r.targetLocationId(), r.skuId())))
                throw new BusinessException(
                        StockTransferErrorCode.INVALID_LINE, "同一调拨单的目标库位和SKU维度不能重复");
            masterData.requireEnabledInventoryDimension(source, r.sourceLocationId(), r.skuId());
            masterData.requireEnabledInventoryDimension(target, r.targetLocationId(), r.skuId());
            StockTransferLineEntity line = new StockTransferLineEntity();
            line.setTransferId(id);
            line.setSourceWarehouseId(source);
            line.setTargetWarehouseId(target);
            line.setLineNo(no++);
            line.setSourceLocationId(r.sourceLocationId());
            line.setTargetLocationId(r.targetLocationId());
            line.setSkuId(r.skuId());
            line.setQuantity(q);
            result.add(line);
        }
        return result;
    }

    private void validateWarehouses(long source, long target) {
        if (source == target) throw error(StockTransferErrorCode.SAME_WAREHOUSE);
    }

    private List<StockTransferLineEntity> requireLines(long id) {
        List<StockTransferLineEntity> v = lines.selectByTransferId(id);
        if (v.isEmpty()) throw error(StockTransferErrorCode.INVALID_LINE);
        return v;
    }

    private List<StockTransferLineEntity> sourceSorted(List<StockTransferLineEntity> v) {
        return v.stream()
                .sorted(
                        Comparator.comparing(StockTransferLineEntity::getSourceLocationId)
                                .thenComparing(StockTransferLineEntity::getSkuId)
                                .thenComparing(StockTransferLineEntity::getTargetLocationId))
                .toList();
    }

    private List<StockTransferLineEntity> targetSorted(List<StockTransferLineEntity> v) {
        return v.stream()
                .sorted(
                        Comparator.comparing(StockTransferLineEntity::getTargetLocationId)
                                .thenComparing(StockTransferLineEntity::getSkuId)
                                .thenComparing(StockTransferLineEntity::getSourceLocationId))
                .toList();
    }

    private TransferInventoryCommand sourceCommand(
            StockTransferEntity h,
            StockTransferLineEntity l,
            InventoryBusinessType type,
            AuthenticatedActor a) {
        return new TransferInventoryCommand(
                h.getSourceWarehouseId(),
                l.getSourceLocationId(),
                l.getSkuId(),
                type,
                h.getTransferNo(),
                l.getQuantity(),
                a.userId(),
                a.username());
    }

    private TransferInventoryCommand targetCommand(
            StockTransferEntity h, StockTransferLineEntity l, AuthenticatedActor a) {
        return new TransferInventoryCommand(
                h.getTargetWarehouseId(),
                l.getTargetLocationId(),
                l.getSkuId(),
                InventoryBusinessType.TRANSFER_IN,
                h.getTransferNo(),
                l.getQuantity(),
                a.userId(),
                a.username());
    }

    private void publishAvailability(
            StockTransferEntity order,
            InventoryAvailabilityChanged.Action action,
            boolean target,
            List<StockTransferLineEntity> affectedLines) {
        events.publishEvent(
                new InventoryAvailabilityChanged(
                        action,
                        order.getId(),
                        order.getTransferNo(),
                        target ? order.getTargetWarehouseId() : order.getSourceWarehouseId(),
                        affectedLines.stream()
                                .map(
                                        line ->
                                                new InventoryAvailabilityChanged.Dimension(
                                                        target
                                                                ? line.getTargetLocationId()
                                                                : line.getSourceLocationId(),
                                                        line.getSkuId()))
                                .toList()));
    }

    private void transition(
            StockTransferEntity h,
            StockTransferStatus from,
            StockTransferStatus to,
            AuthenticatedActor a) {
        if (transfers.transition(
                        h.getId(), h.getVersion(), from.name(), to.name(), a.userId(), a.username())
                != 1) throw error(StockTransferErrorCode.CONCURRENT_MODIFICATION);
    }

    private StockTransferEntity locked(long id) {
        StockTransferEntity v = transfers.selectByIdForUpdate(id);
        if (v == null) throw error(StockTransferErrorCode.NOT_FOUND);
        return v;
    }

    private void requireState(StockTransferEntity v, StockTransferStatus s) {
        if (v.getStatus() != s)
            throw new BusinessException(
                    StockTransferErrorCode.INVALID_STATE, "当前状态为" + v.getStatus() + "，要求状态为" + s);
    }

    private void requireVersion(StockTransferEntity v, int n) {
        if (!v.getVersion().equals(n)) throw error(StockTransferErrorCode.CONCURRENT_MODIFICATION);
    }

    private void requireActor(AuthenticatedActor a) {
        if (a == null
                || a.userId() == null
                || a.userId() <= 0
                || !StringUtils.hasText(a.username()))
            throw error(StockTransferErrorCode.PERSISTENCE_FAILURE);
    }

    private void assign(long id, long source, long target, List<StockTransferLineEntity> v) {
        v.forEach(
                x -> {
                    x.setTransferId(id);
                    x.setSourceWarehouseId(source);
                    x.setTargetWarehouseId(target);
                });
    }

    private void insertLines(List<StockTransferLineEntity> v) {
        try {
            if (lines.insertBatch(v) != v.size())
                throw error(StockTransferErrorCode.PERSISTENCE_FAILURE);
        } catch (DuplicateKeyException e) {
            throw error(StockTransferErrorCode.INVALID_LINE);
        }
    }

    private String normalizeNo(String v) {
        return v.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeRemark(String v) {
        return StringUtils.hasText(v) ? v.trim() : null;
    }

    private BigDecimal quantity(BigDecimal v) {
        if (v == null || v.signum() <= 0 || v.scale() > 4 || v.precision() - v.scale() > 15)
            throw error(StockTransferErrorCode.INVALID_LINE);
        return v.setScale(4);
    }

    private BusinessException error(StockTransferErrorCode c) {
        return new BusinessException(c);
    }

    private record SourceDimension(long location, long sku) {}

    private record TargetDimension(long location, long sku) {}
}
