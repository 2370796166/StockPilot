package com.stockpilot.inventorycount.application;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stockpilot.common.exception.BusinessException;
import com.stockpilot.inventory.application.InventoryCountAdjustmentCommand;
import com.stockpilot.inventory.application.InventoryMutationApplicationService;
import com.stockpilot.inventory.vo.InventoryBalanceVO;
import com.stockpilot.inventorycount.api.InventoryCountErrorCode;
import com.stockpilot.inventorycount.domain.*;
import com.stockpilot.inventorycount.infrastructure.mapper.*;
import com.stockpilot.inventorycount.request.InventoryCountRequests;
import com.stockpilot.inventorycount.vo.*;
import com.stockpilot.masterdata.vo.PageResult;
import com.stockpilot.security.auth.StockPilotPrincipal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InventoryCountApplicationService {
    private final InventoryCountMapper counts; private final InventoryCountLineMapper lines;
    private final InventoryCountScopeMapper scopes; private final InventoryMutationApplicationService inventory;
    public InventoryCountApplicationService(InventoryCountMapper counts,InventoryCountLineMapper lines,
        InventoryCountScopeMapper scopes,InventoryMutationApplicationService inventory){this.counts=counts;this.lines=lines;this.scopes=scopes;this.inventory=inventory;}

    @Transactional(readOnly=true)
    public PageResult<InventoryCountSummaryVO> page(InventoryCountRequests.PageQuery q){
        var p=counts.selectPage(new Page<>(q.getPage(),q.getSize()),q);
        return new PageResult<>(p.getRecords().stream().map(this::summary).toList(),p.getTotal(),p.getCurrent(),p.getSize());
    }
    @Transactional(readOnly=true) public InventoryCountVO get(long id){var h=counts.selectById(id);if(h==null)throw error(InventoryCountErrorCode.NOT_FOUND);return detail(h,lines.selectByCountId(id));}

    @Transactional
    public InventoryCountVO create(InventoryCountRequests.Create r,StockPilotPrincipal actor){
        requireActor(actor); String no=r.countNo().trim().toUpperCase(Locale.ROOT);
        var dimensions=r.dimensions().stream().map(x->new Dimension(x.locationId(),x.skuId()))
            .sorted(Comparator.comparing(Dimension::locationId).thenComparing(Dimension::skuId)).toList();
        if(new HashSet<>(dimensions).size()!=dimensions.size())throw error(InventoryCountErrorCode.INVALID_LINE);
        InventoryCountEntity h=new InventoryCountEntity();h.setCountNo(no);h.setWarehouseId(r.warehouseId());h.setStatus(InventoryCountStatus.DRAFT);
        h.setRemark(StringUtils.hasText(r.remark())?r.remark().trim():null);h.setCreatedBy(actor.userId());h.setCreatedByName(actor.username());
        try{if(counts.insert(h)!=1)throw error(InventoryCountErrorCode.PERSISTENCE_FAILURE);}catch(DuplicateKeyException e){throw error(InventoryCountErrorCode.DUPLICATE_NO);}
        int lineNo=1;
        for(Dimension d:dimensions){
            InventoryBalanceVO snapshot=inventory.lockCountSnapshot(r.warehouseId(),d.locationId(),d.skuId());
            InventoryCountLineEntity line=new InventoryCountLineEntity();line.setCountId(h.getId());line.setWarehouseId(r.warehouseId());line.setLineNo(lineNo++);
            line.setLocationId(d.locationId());line.setSkuId(d.skuId());line.setSnapshotActualQuantity(snapshot.actualQuantity());
            line.setSnapshotAvailableQuantity(snapshot.availableQuantity());line.setSnapshotFrozenQuantity(snapshot.frozenQuantity());line.setSnapshotBalanceVersion(snapshot.version());
            try{
                if(lines.insert(line)!=1||scopes.insert(h.getId(),line.getId(),r.warehouseId(),d.locationId(),d.skuId())!=1)
                    throw error(InventoryCountErrorCode.PERSISTENCE_FAILURE);
            }catch(DuplicateKeyException e){throw error(InventoryCountErrorCode.DUPLICATE_SCOPE);}
        }
        return get(h.getId());
    }

    @Transactional public InventoryCountVO start(long id,InventoryCountRequests.Transition r,StockPilotPrincipal a){return simpleTransition(id,r.version(),InventoryCountStatus.DRAFT,InventoryCountStatus.COUNTING,a);}

    @Transactional
    public InventoryCountVO recordResults(long id,InventoryCountRequests.RecordResults r,StockPilotPrincipal a){
        requireActor(a);InventoryCountEntity h=locked(id);requireState(h,InventoryCountStatus.COUNTING);requireVersion(h,r.version());
        List<InventoryCountLineEntity> existing=lines.selectByCountId(id);
        Map<Long,InventoryCountLineEntity> byId=existing.stream().collect(Collectors.toMap(InventoryCountLineEntity::getId,Function.identity()));
        if(r.results().size()!=existing.size()||r.results().stream().map(InventoryCountRequests.Result::lineId).distinct().count()!=existing.size())
            throw error(InventoryCountErrorCode.INVALID_LINE);
        for(var result:r.results()){
            var line=byId.get(result.lineId());if(line==null)throw error(InventoryCountErrorCode.INVALID_LINE);
            BigDecimal counted=quantity(result.countedQuantity());String reason=result.reason().trim();
            if(lines.record(line.getId(),id,counted,counted.subtract(line.getSnapshotActualQuantity()),reason)!=1)
                throw error(InventoryCountErrorCode.CONCURRENT_MODIFICATION);
        }
        if(counts.touchCounting(id,h.getVersion())!=1)throw error(InventoryCountErrorCode.CONCURRENT_MODIFICATION);
        return get(id);
    }

    @Transactional
    public InventoryCountVO submit(long id,InventoryCountRequests.Transition r,StockPilotPrincipal a){
        InventoryCountEntity h=locked(id);requireActor(a);requireState(h,InventoryCountStatus.COUNTING);requireVersion(h,r.version());
        if(lines.countIncomplete(id)>0)throw error(InventoryCountErrorCode.INCOMPLETE_RESULT);
        transition(h,InventoryCountStatus.COUNTING,InventoryCountStatus.SUBMITTED,a);return get(id);
    }
    @Transactional public InventoryCountVO approve(long id,InventoryCountRequests.Transition r,StockPilotPrincipal a){return simpleTransition(id,r.version(),InventoryCountStatus.SUBMITTED,InventoryCountStatus.APPROVED,a);}

    @Transactional
    public InventoryCountVO adjust(long id,StockPilotPrincipal a){
        requireActor(a);InventoryCountEntity h=locked(id);
        if(h.getStatus()==InventoryCountStatus.ADJUSTED)throw error(InventoryCountErrorCode.ALREADY_ADJUSTED);
        requireState(h,InventoryCountStatus.APPROVED);
        List<InventoryCountLineEntity> sorted=lines.selectByCountId(id).stream()
            .sorted(Comparator.comparing(InventoryCountLineEntity::getLocationId).thenComparing(InventoryCountLineEntity::getSkuId)).toList();
        for(var line:sorted){
            inventory.adjustInventoryCount(new InventoryCountAdjustmentCommand(h.getId(),line.getId(),h.getCountNo(),h.getWarehouseId(),
                line.getLocationId(),line.getSkuId(),line.getSnapshotBalanceVersion(),line.getSnapshotActualQuantity(),
                line.getSnapshotAvailableQuantity(),line.getSnapshotFrozenQuantity(),line.getCountedQuantity(),line.getReason(),a.userId(),a.username()));
        }
        transition(h,InventoryCountStatus.APPROVED,InventoryCountStatus.ADJUSTED,a);
        if(scopes.deleteByCountId(id)!=sorted.size())throw error(InventoryCountErrorCode.PERSISTENCE_FAILURE);
        return get(id);
    }

    private InventoryCountVO simpleTransition(long id,int version,InventoryCountStatus from,InventoryCountStatus to,StockPilotPrincipal a){
        requireActor(a);var h=locked(id);requireState(h,from);requireVersion(h,version);transition(h,from,to,a);return get(id);
    }
    private void transition(InventoryCountEntity h,InventoryCountStatus from,InventoryCountStatus to,StockPilotPrincipal a){if(counts.transition(h.getId(),h.getVersion(),from.name(),to.name(),a.userId(),a.username())!=1)throw error(InventoryCountErrorCode.CONCURRENT_MODIFICATION);}
    private InventoryCountEntity locked(long id){var h=counts.selectByIdForUpdate(id);if(h==null)throw error(InventoryCountErrorCode.NOT_FOUND);return h;}
    private void requireState(InventoryCountEntity h,InventoryCountStatus s){if(h.getStatus()!=s)throw new BusinessException(InventoryCountErrorCode.INVALID_STATE,"当前状态为"+h.getStatus()+"，要求状态为"+s);}
    private void requireVersion(InventoryCountEntity h,int v){if(!h.getVersion().equals(v))throw error(InventoryCountErrorCode.CONCURRENT_MODIFICATION);}
    private void requireActor(StockPilotPrincipal a){if(a==null||a.userId()==null||a.userId()<=0||!StringUtils.hasText(a.username()))throw error(InventoryCountErrorCode.PERSISTENCE_FAILURE);}
    private BigDecimal quantity(BigDecimal v){if(v==null||v.signum()<0||v.scale()>4||v.precision()-v.scale()>15)throw error(InventoryCountErrorCode.INVALID_LINE);return v.setScale(4);}
    private InventoryCountVO detail(InventoryCountEntity h,List<InventoryCountLineEntity> list){return new InventoryCountVO(h.getId(),h.getCountNo(),h.getWarehouseId(),h.getStatus(),h.getRemark(),h.getVersion(),h.getCreatedAt(),h.getUpdatedAt(),list.stream().map(x->new InventoryCountVO.Line(x.getId(),x.getLineNo(),x.getLocationId(),x.getSkuId(),x.getSnapshotActualQuantity(),x.getSnapshotAvailableQuantity(),x.getSnapshotFrozenQuantity(),x.getSnapshotBalanceVersion(),x.getCountedQuantity(),x.getDifferenceQuantity(),x.getReason())).toList());}
    private InventoryCountSummaryVO summary(InventoryCountEntity h){return new InventoryCountSummaryVO(h.getId(),h.getCountNo(),h.getWarehouseId(),h.getStatus(),h.getRemark(),h.getVersion(),h.getCreatedAt(),h.getUpdatedAt());}
    private BusinessException error(InventoryCountErrorCode c){return new BusinessException(c);} private record Dimension(long locationId,long skuId){}
}
