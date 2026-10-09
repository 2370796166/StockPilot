package com.stockpilot.inventory.count.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stockpilot.inventory.count.api.InventoryCountErrorCode;
import com.stockpilot.inventory.count.domain.*;
import com.stockpilot.inventory.count.mapper.*;
import com.stockpilot.inventory.count.request.InventoryCountRequests;
import com.stockpilot.inventory.count.vo.*;
import com.stockpilot.inventory.domain.InventoryAvailabilityChanged;
import com.stockpilot.inventory.service.InventoryCountAdjustmentCommand;
import com.stockpilot.inventory.service.InventoryMutationApplicationService;
import com.stockpilot.inventory.vo.InventoryBalanceVO;
import com.stockpilot.shared.api.PageResult;
import com.stockpilot.shared.auth.AuthenticatedActor;
import com.stockpilot.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class InventoryCountApplicationService {
    @Transactional(readOnly = true)
    public InventoryCountVO getByNumber(String number) {
        if (number == null || !number.matches("[A-Za-z0-9_-]{2,64}"))
            throw new IllegalArgumentException("Invalid document number");
        Long id = counts.selectIdByNumber(number.toUpperCase(java.util.Locale.ROOT));
        if (id == null)
            throw new com.stockpilot.shared.exception.BusinessException(
                    InventoryCountErrorCode.NOT_FOUND);
        return get(id);
    }

    private final InventoryCountMapper counts;
    private final InventoryCountLineMapper lines;
    private final InventoryCountScopeMapper scopes;
    private final InventoryMutationApplicationService inventory;
    private final ApplicationEventPublisher events;

    public InventoryCountApplicationService(
            InventoryCountMapper counts,
            InventoryCountLineMapper lines,
            InventoryCountScopeMapper scopes,
            InventoryMutationApplicationService inventory,
            ApplicationEventPublisher events) {
        this.counts = counts;
        this.lines = lines;
        this.scopes = scopes;
        this.inventory = inventory;
        this.events = events;
    }

    // 分页查询盘点单摘要，用于查看当前盘点所处的业务阶段。
    @Transactional(readOnly = true)
    public PageResult<InventoryCountSummaryVO> page(InventoryCountRequests.PageQuery q) {
        if (q == null || !q.isPeriodValid())
            throw new IllegalArgumentException("Invalid document period");
        var p = counts.selectPage(new Page<>(q.getPage(), q.getSize()), q);
        return new PageResult<>(
                p.getRecords().stream().map(InventoryCountViewAssembler::summary).toList(),
                p.getTotal(),
                p.getCurrent(),
                p.getSize());
    }

    // 查询盘点详情，返回创建时的库存快照、实盘数量、差异及调整原因。
    @Transactional(readOnly = true)
    public InventoryCountVO get(long id) {
        var h = counts.selectById(id);
        if (h == null) throw error(InventoryCountErrorCode.NOT_FOUND);
        return InventoryCountViewAssembler.detail(h, lines.selectByCountId(id));
    }

    // 创建静态盘点：按稳定顺序锁定所有库存维度，保存三数量与版本快照并建立维度锁。
    // 维度锁存续期间，采购、销售、调拨和其他库存变更均不能修改这些库存行。
    @Transactional
    public InventoryCountVO create(InventoryCountRequests.Create r, AuthenticatedActor actor) {
        requireActor(actor);
        String no = r.countNo().trim().toUpperCase(Locale.ROOT);
        // 固定维度顺序后依次锁定余额，避免两个盘点任务以不同顺序竞争相同库存行。
        var dimensions =
                r.dimensions().stream()
                        .map(x -> new Dimension(x.locationId(), x.skuId()))
                        .sorted(
                                Comparator.comparing(Dimension::locationId)
                                        .thenComparing(Dimension::skuId))
                        .toList();
        if (new HashSet<>(dimensions).size() != dimensions.size())
            throw error(InventoryCountErrorCode.INVALID_LINE);
        InventoryCountEntity h = new InventoryCountEntity();
        h.setCountNo(no);
        h.setWarehouseId(r.warehouseId());
        h.setStatus(InventoryCountStatus.DRAFT);
        h.setRemark(StringUtils.hasText(r.remark()) ? r.remark().trim() : null);
        h.setCreatedBy(actor.userId());
        h.setCreatedByName(actor.username());
        try {
            if (counts.insert(h) != 1) throw error(InventoryCountErrorCode.PERSISTENCE_FAILURE);
        } catch (DuplicateKeyException e) {
            throw error(InventoryCountErrorCode.DUPLICATE_NO);
        }
        int lineNo = 1;
        for (Dimension d : dimensions) {
            // 快照保存三种数量及版本，并由唯一 scope 记录阻止盘点期间的其他库存写入。
            InventoryBalanceVO snapshot =
                    inventory.lockCountSnapshot(r.warehouseId(), d.locationId(), d.skuId());
            InventoryCountLineEntity line = new InventoryCountLineEntity();
            line.setCountId(h.getId());
            line.setWarehouseId(r.warehouseId());
            line.setLineNo(lineNo++);
            line.setLocationId(d.locationId());
            line.setSkuId(d.skuId());
            line.setSnapshotActualQuantity(snapshot.actualQuantity());
            line.setSnapshotAvailableQuantity(snapshot.availableQuantity());
            line.setSnapshotFrozenQuantity(snapshot.frozenQuantity());
            line.setSnapshotBalanceVersion(snapshot.version());
            try {
                if (lines.insert(line) != 1
                        || scopes.insert(
                                        h.getId(),
                                        line.getId(),
                                        r.warehouseId(),
                                        d.locationId(),
                                        d.skuId())
                                != 1) throw error(InventoryCountErrorCode.PERSISTENCE_FAILURE);
            } catch (DuplicateKeyException e) {
                throw error(InventoryCountErrorCode.DUPLICATE_SCOPE);
            }
        }
        return get(h.getId());
    }

    // 开始盘点：将 DRAFT 推进到 COUNTING，表示现场可以录入实盘结果。
    @Transactional
    public InventoryCountVO start(
            long id, InventoryCountRequests.Transition r, AuthenticatedActor a) {
        return simpleTransition(
                id, r.version(), InventoryCountStatus.DRAFT, InventoryCountStatus.COUNTING, a);
    }

    // 记录盘点结果：要求一次提交覆盖全部盘点行，自动计算实盘量与快照实际量的差异。
    // 更新单头版本用于阻止多个客户端以同一个旧版本相互覆盖结果。
    @Transactional
    public InventoryCountVO recordResults(
            long id, InventoryCountRequests.RecordResults r, AuthenticatedActor a) {
        requireActor(a);
        InventoryCountEntity h = locked(id);
        requireState(h, InventoryCountStatus.COUNTING);
        requireVersion(h, r.version());
        List<InventoryCountLineEntity> existing = lines.selectByCountId(id);
        Map<Long, InventoryCountLineEntity> byId =
                existing.stream()
                        .collect(
                                Collectors.toMap(
                                        InventoryCountLineEntity::getId, Function.identity()));
        if (r.results().size() != existing.size()
                || r.results().stream()
                                .map(InventoryCountRequests.Result::lineId)
                                .distinct()
                                .count()
                        != existing.size()) throw error(InventoryCountErrorCode.INVALID_LINE);
        for (var result : r.results()) {
            var line = byId.get(result.lineId());
            if (line == null) throw error(InventoryCountErrorCode.INVALID_LINE);
            BigDecimal counted = quantity(result.countedQuantity());
            String reason = result.reason().trim();
            if (lines.record(
                            line.getId(),
                            id,
                            counted,
                            counted.subtract(line.getSnapshotActualQuantity()),
                            reason)
                    != 1) throw error(InventoryCountErrorCode.CONCURRENT_MODIFICATION);
        }
        if (counts.touchCounting(id, h.getVersion()) != 1)
            throw error(InventoryCountErrorCode.CONCURRENT_MODIFICATION);
        return get(id);
    }

    // 提交盘点结果：只有所有明细都已录入实盘数量时，COUNTING 才能转为 SUBMITTED。
    @Transactional
    public InventoryCountVO submit(
            long id, InventoryCountRequests.Transition r, AuthenticatedActor a) {
        InventoryCountEntity h = locked(id);
        requireActor(a);
        requireState(h, InventoryCountStatus.COUNTING);
        requireVersion(h, r.version());
        if (lines.countIncomplete(id) > 0) throw error(InventoryCountErrorCode.INCOMPLETE_RESULT);
        requireExecutableResults(id);
        transition(h, InventoryCountStatus.COUNTING, InventoryCountStatus.SUBMITTED, a);
        return get(id);
    }

    // 审核盘点结果：确认差异可以执行，但此步骤尚不修改库存余额。
    @Transactional
    public InventoryCountVO approve(
            long id, InventoryCountRequests.Transition r, AuthenticatedActor a) {
        requireActor(a);
        InventoryCountEntity h = locked(id);
        requireState(h, InventoryCountStatus.SUBMITTED);
        requireVersion(h, r.version());
        requireExecutableResults(id);
        transition(h, InventoryCountStatus.SUBMITTED, InventoryCountStatus.APPROVED, a);
        return get(id);
    }

    // 执行盘点调整：逐维度校验原快照和盘点锁，再将差异同步作用于实际量与可用量。
    // 全部流水、ADJUSTED 状态和维度锁释放共同提交，避免部分调整或提前解锁。
    @Transactional
    public InventoryCountVO adjust(long id, AuthenticatedActor a) {
        requireActor(a);
        InventoryCountEntity h = locked(id);
        if (h.getStatus() == InventoryCountStatus.ADJUSTED)
            throw error(InventoryCountErrorCode.ALREADY_ADJUSTED);
        requireState(h, InventoryCountStatus.APPROVED);
        List<InventoryCountLineEntity> sorted =
                lines.selectByCountId(id).stream()
                        .sorted(
                                Comparator.comparing(InventoryCountLineEntity::getLocationId)
                                        .thenComparing(InventoryCountLineEntity::getSkuId))
                        .toList();
        for (var line : sorted) {
            inventory.adjustInventoryCount(
                    new InventoryCountAdjustmentCommand(
                            h.getId(),
                            line.getId(),
                            h.getCountNo(),
                            h.getWarehouseId(),
                            line.getLocationId(),
                            line.getSkuId(),
                            line.getSnapshotBalanceVersion(),
                            line.getSnapshotActualQuantity(),
                            line.getSnapshotAvailableQuantity(),
                            line.getSnapshotFrozenQuantity(),
                            line.getCountedQuantity(),
                            line.getReason(),
                            a.userId(),
                            a.username()));
        }
        transition(h, InventoryCountStatus.APPROVED, InventoryCountStatus.ADJUSTED, a);
        // 仅在全部明细调整及状态转换成功后释放维度锁；数量不符会触发整个事务回滚。
        if (scopes.deleteByCountId(id) != sorted.size())
            throw error(InventoryCountErrorCode.PERSISTENCE_FAILURE);
        events.publishEvent(
                new InventoryAvailabilityChanged(
                        InventoryAvailabilityChanged.Action.COUNT_ADJUSTED,
                        h.getId(),
                        h.getCountNo(),
                        h.getWarehouseId(),
                        sorted.stream()
                                .map(
                                        line ->
                                                new InventoryAvailabilityChanged.Dimension(
                                                        line.getLocationId(), line.getSkuId()))
                                .toList()));
        return get(id);
    }

    // Cancel keeps the original evidence and never changes inventory quantities.
    @Transactional
    public InventoryCountVO cancel(
            long id, InventoryCountRequests.Cancel request, AuthenticatedActor actor) {
        requireActor(actor);
        if (request == null
                || request.version() == null
                || !StringUtils.hasText(request.reason())
                || request.reason().trim().length() > 255)
            throw error(InventoryCountErrorCode.INVALID_LINE);
        InventoryCountEntity count = locked(id);
        if (count.getStatus() == InventoryCountStatus.ADJUSTED)
            throw error(InventoryCountErrorCode.ALREADY_ADJUSTED);
        if (count.getStatus() == InventoryCountStatus.CANCELLED)
            throw error(InventoryCountErrorCode.ALREADY_CANCELLED);
        requireVersion(count, request.version());
        List<InventoryCountLineEntity> sorted =
                lines.selectByCountId(id).stream()
                        .sorted(
                                Comparator.comparing(InventoryCountLineEntity::getLocationId)
                                        .thenComparing(InventoryCountLineEntity::getSkuId))
                        .toList();
        if (sorted.isEmpty()) throw error(InventoryCountErrorCode.INVALID_LINE);
        for (var line : sorted)
            inventory.lockCountBalanceForRelease(
                    count.getWarehouseId(), line.getLocationId(), line.getSkuId());
        if (counts.cancel(
                                id,
                                request.version(),
                                actor.userId(),
                                actor.username(),
                                request.reason().trim())
                        != 1
                || scopes.deleteByCountId(id) != sorted.size())
            throw error(InventoryCountErrorCode.PERSISTENCE_FAILURE);
        return get(id);
    }

    private void requireExecutableResults(long id) {
        var results = lines.selectByCountId(id);
        if (results.isEmpty()
                || results.stream().anyMatch(line -> line.getCountedQuantity() == null))
            throw error(InventoryCountErrorCode.INCOMPLETE_RESULT);
        if (results.stream()
                .anyMatch(
                        line ->
                                line.getCountedQuantity()
                                                .compareTo(line.getSnapshotFrozenQuantity())
                                        < 0)) throw error(InventoryCountErrorCode.BELOW_FROZEN);
    }

    private InventoryCountVO simpleTransition(
            long id,
            int version,
            InventoryCountStatus from,
            InventoryCountStatus to,
            AuthenticatedActor a) {
        requireActor(a);
        var h = locked(id);
        requireState(h, from);
        requireVersion(h, version);
        transition(h, from, to, a);
        return get(id);
    }

    private void transition(
            InventoryCountEntity h,
            InventoryCountStatus from,
            InventoryCountStatus to,
            AuthenticatedActor a) {
        if (counts.transition(
                        h.getId(), h.getVersion(), from.name(), to.name(), a.userId(), a.username())
                != 1) throw error(InventoryCountErrorCode.CONCURRENT_MODIFICATION);
    }

    private InventoryCountEntity locked(long id) {
        var h = counts.selectByIdForUpdate(id);
        if (h == null) throw error(InventoryCountErrorCode.NOT_FOUND);
        return h;
    }

    private void requireState(InventoryCountEntity h, InventoryCountStatus s) {
        if (h.getStatus() != s)
            throw new BusinessException(
                    InventoryCountErrorCode.INVALID_STATE, "当前状态为" + h.getStatus() + "，要求状态为" + s);
    }

    private void requireVersion(InventoryCountEntity h, int v) {
        if (!h.getVersion().equals(v)) throw error(InventoryCountErrorCode.CONCURRENT_MODIFICATION);
    }

    private void requireActor(AuthenticatedActor a) {
        if (a == null
                || a.userId() == null
                || a.userId() <= 0
                || !StringUtils.hasText(a.username()))
            throw error(InventoryCountErrorCode.PERSISTENCE_FAILURE);
    }

    private BigDecimal quantity(BigDecimal v) {
        if (v == null || v.signum() < 0 || v.scale() > 4 || v.precision() - v.scale() > 15)
            throw error(InventoryCountErrorCode.INVALID_LINE);
        return v.setScale(4);
    }

    private BusinessException error(InventoryCountErrorCode c) {
        return new BusinessException(c);
    }

    private record Dimension(long locationId, long skuId) {}
}
