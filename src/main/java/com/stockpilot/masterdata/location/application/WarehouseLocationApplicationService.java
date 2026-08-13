package com.stockpilot.masterdata.location.application;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stockpilot.common.exception.BusinessException;
import com.stockpilot.masterdata.api.MasterDataErrorCode;
import com.stockpilot.masterdata.domain.MasterDataStatus;
import com.stockpilot.masterdata.location.domain.WarehouseLocationEntity;
import com.stockpilot.masterdata.location.infrastructure.mapper.WarehouseLocationMapper;
import com.stockpilot.masterdata.location.request.*;
import com.stockpilot.masterdata.location.vo.LocationVO;
import com.stockpilot.masterdata.request.ChangeStatusRequest;
import com.stockpilot.masterdata.vo.PageResult;
import com.stockpilot.masterdata.warehouse.domain.WarehouseEntity;
import com.stockpilot.masterdata.warehouse.infrastructure.mapper.WarehouseMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.Locale;

@Service
public class WarehouseLocationApplicationService {
    private final WarehouseLocationMapper mapper; private final WarehouseMapper warehouseMapper;
    public WarehouseLocationApplicationService(WarehouseLocationMapper mapper, WarehouseMapper warehouseMapper) {
        this.mapper=mapper; this.warehouseMapper=warehouseMapper;
    }
    public LocationVO create(CreateLocationRequest r) {
        requireEnabledWarehouse(r.warehouseId());
        WarehouseLocationEntity e=new WarehouseLocationEntity(); e.setWarehouseId(r.warehouseId());
        e.setCode(r.code().trim().toUpperCase(Locale.ROOT)); e.setName(r.name().trim());
        e.setRemark(trim(r.remark())); e.setStatus(MasterDataStatus.ENABLED);
        try { mapper.insert(e); } catch (DuplicateKeyException ex) {
            throw new BusinessException(MasterDataErrorCode.DUPLICATE_CODE,"该仓库中的库位编码已存在");
        }
        return toVO(require(e.getId()));
    }
    public LocationVO update(long id, UpdateLocationRequest r) {
        requireEnabledWarehouse(r.warehouseId()); WarehouseLocationEntity e=require(id);
        e.setWarehouseId(r.warehouseId()); e.setName(r.name().trim()); e.setRemark(trim(r.remark())); e.setVersion(r.version());
        try { if(mapper.updateById(e)!=1) throw new BusinessException(MasterDataErrorCode.CONCURRENT_MODIFICATION); }
        catch (DuplicateKeyException ex) { throw new BusinessException(MasterDataErrorCode.DUPLICATE_CODE,"该仓库中的库位编码已存在"); }
        return toVO(require(id));
    }
    public LocationVO changeStatus(long id, ChangeStatusRequest r) {
        WarehouseLocationEntity e=require(id); e.setStatus(r.status()); e.setVersion(r.version());
        if(mapper.updateById(e)!=1) throw new BusinessException(MasterDataErrorCode.CONCURRENT_MODIFICATION);
        return toVO(require(id));
    }
    public LocationVO detail(long id){ return toVO(require(id)); }
    public PageResult<LocationVO> page(LocationPageQuery q){
        LambdaQueryWrapper<WarehouseLocationEntity> w=new LambdaQueryWrapper<WarehouseLocationEntity>()
                .eq(q.getWarehouseId()!=null,WarehouseLocationEntity::getWarehouseId,q.getWarehouseId())
                .like(StringUtils.hasText(q.getCode()),WarehouseLocationEntity::getCode,trim(q.getCode()))
                .like(StringUtils.hasText(q.getName()),WarehouseLocationEntity::getName,trim(q.getName()))
                .eq(q.getStatus()!=null,WarehouseLocationEntity::getStatus,q.getStatus())
                .orderByDesc(WarehouseLocationEntity::getId);
        Page<WarehouseLocationEntity> p=mapper.selectPage(Page.of(q.getPage(),q.getSize()),w);
        return new PageResult<>(p.getRecords().stream().map(this::toVO).toList(),p.getTotal(),p.getCurrent(),p.getSize());
    }
    private WarehouseEntity requireEnabledWarehouse(long id){ WarehouseEntity w=warehouseMapper.selectById(id);
        if(w==null) throw new BusinessException(MasterDataErrorCode.WAREHOUSE_NOT_FOUND);
        if(w.getStatus()!=MasterDataStatus.ENABLED) throw new BusinessException(MasterDataErrorCode.WAREHOUSE_DISABLED); return w; }
    private WarehouseLocationEntity require(long id){WarehouseLocationEntity e=mapper.selectById(id); if(e==null) throw new BusinessException(MasterDataErrorCode.NOT_FOUND); return e;}
    private LocationVO toVO(WarehouseLocationEntity e){WarehouseEntity w=warehouseMapper.selectById(e.getWarehouseId()); return new LocationVO(e.getId(),e.getWarehouseId(),w==null?null:w.getCode(),e.getCode(),e.getName(),e.getStatus(),e.getRemark(),e.getCreatedAt(),e.getUpdatedAt(),e.getVersion());}
    private static String trim(String s){return StringUtils.hasText(s)?s.trim():null;}
}
