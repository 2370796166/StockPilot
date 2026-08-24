package com.stockpilot.masterdata.warehouse.application;
import com.stockpilot.cache.*;
import com.stockpilot.common.exception.BusinessException;
import com.stockpilot.masterdata.application.MasterDataApplicationService;
import com.stockpilot.masterdata.api.MasterDataErrorCode;
import com.stockpilot.masterdata.request.ChangeStatusRequest;
import com.stockpilot.masterdata.request.CreateMasterDataRequest;
import com.stockpilot.masterdata.request.UpdateMasterDataRequest;
import com.stockpilot.masterdata.vo.MasterDataVO;
import com.stockpilot.masterdata.warehouse.domain.WarehouseEntity;
import com.stockpilot.masterdata.warehouse.infrastructure.mapper.WarehouseMapper;
import org.springframework.stereotype.Service;
@Service
public class WarehouseApplicationService extends MasterDataApplicationService<WarehouseEntity> {
    private final ReferenceDataCache cache;
    public WarehouseApplicationService(WarehouseMapper mapper, ReferenceDataCache cache) {
        super(mapper, WarehouseEntity::new, "仓库编码已存在"); this.cache = cache;
    }
    @Override public MasterDataVO create(CreateMasterDataRequest request) {MasterDataVO value=super.create(request);cache.evict(ReferenceCacheKind.WAREHOUSE,value.id());return value;}
    @Override public MasterDataVO update(long id,UpdateMasterDataRequest request){MasterDataVO value=super.update(id,request);cache.evict(ReferenceCacheKind.WAREHOUSE,id);return value;}
    @Override public MasterDataVO changeStatus(long id,ChangeStatusRequest request){MasterDataVO value=super.changeStatus(id,request);cache.evict(ReferenceCacheKind.WAREHOUSE,id);return value;}
    @Override public MasterDataVO detail(long id){
        ReferenceCacheLookup<MasterDataVO> cached=cache.get(ReferenceCacheKind.WAREHOUSE,id,MasterDataVO.class);
        if(cached.hit()){if(!cached.found())throw new BusinessException(MasterDataErrorCode.NOT_FOUND);return cached.value();}
        try{MasterDataVO value=super.detail(id);cache.put(ReferenceCacheKind.WAREHOUSE,id,value);return value;}
        catch(BusinessException exception){if(MasterDataErrorCode.NOT_FOUND.code().equals(exception.getErrorCode().code()))cache.putMissing(ReferenceCacheKind.WAREHOUSE,id);throw exception;}
    }
}
