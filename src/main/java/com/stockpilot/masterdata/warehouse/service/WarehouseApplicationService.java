package com.stockpilot.masterdata.warehouse.service;

import com.stockpilot.masterdata.api.MasterDataErrorCode;
import com.stockpilot.masterdata.infrastructure.cache.*;
import com.stockpilot.masterdata.request.ChangeStatusRequest;
import com.stockpilot.masterdata.request.CreateMasterDataRequest;
import com.stockpilot.masterdata.request.UpdateMasterDataRequest;
import com.stockpilot.masterdata.service.MasterDataApplicationService;
import com.stockpilot.masterdata.vo.MasterDataVO;
import com.stockpilot.masterdata.warehouse.domain.WarehouseEntity;
import com.stockpilot.masterdata.warehouse.mapper.WarehouseMapper;
import com.stockpilot.shared.exception.BusinessException;
import org.springframework.stereotype.Service;

// 仓库基础资料在通用 CRUD 规则之上增加单键详情缓存及写后失效策略。
@Service
public class WarehouseApplicationService extends MasterDataApplicationService<WarehouseEntity> {
    private final ReferenceDataCache cache;

    public WarehouseApplicationService(WarehouseMapper mapper, ReferenceDataCache cache) {
        super(mapper, WarehouseEntity::new, "仓库编码已存在");
        this.cache = cache;
    }

    // 创建、更新或启停成功后统一删除详情 Key，避免后续请求继续读取旧值或旧的空值缓存。
    @Override
    public MasterDataVO create(CreateMasterDataRequest request) {
        MasterDataVO value = super.create(request);
        cache.evict(ReferenceCacheKind.WAREHOUSE, value.id());
        return value;
    }

    @Override
    public MasterDataVO update(long id, UpdateMasterDataRequest request) {
        MasterDataVO value = super.update(id, request);
        cache.evict(ReferenceCacheKind.WAREHOUSE, id);
        return value;
    }

    @Override
    public MasterDataVO changeStatus(long id, ChangeStatusRequest request) {
        MasterDataVO value = super.changeStatus(id, request);
        cache.evict(ReferenceCacheKind.WAREHOUSE, id);
        return value;
    }

    // 仓库详情采用 Cache Aside：命中直接返回，未命中查询 MySQL 后回填，Redis 故障自动降级。
    @Override
    public MasterDataVO detail(long id) {
        ReferenceCacheLookup<MasterDataVO> cached =
                cache.get(ReferenceCacheKind.WAREHOUSE, id, MasterDataVO.class);
        if (cached.hit()) {
            if (!cached.found()) throw new BusinessException(MasterDataErrorCode.NOT_FOUND);
            return cached.value();
        }
        try {
            MasterDataVO value = super.detail(id);
            cache.put(ReferenceCacheKind.WAREHOUSE, id, value);
            return value;
        } catch (BusinessException exception) {
            if (MasterDataErrorCode.NOT_FOUND.code().equals(exception.getErrorCode().code()))
                cache.putMissing(ReferenceCacheKind.WAREHOUSE, id);
            throw exception;
        }
    }
}
