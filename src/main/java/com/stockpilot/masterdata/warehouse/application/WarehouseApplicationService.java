package com.stockpilot.masterdata.warehouse.application;
import com.stockpilot.masterdata.application.MasterDataApplicationService;
import com.stockpilot.masterdata.warehouse.domain.WarehouseEntity;
import com.stockpilot.masterdata.warehouse.infrastructure.mapper.WarehouseMapper;
import org.springframework.stereotype.Service;
@Service
public class WarehouseApplicationService extends MasterDataApplicationService<WarehouseEntity> {
    public WarehouseApplicationService(WarehouseMapper mapper) { super(mapper, WarehouseEntity::new, "仓库编码已存在"); }
}
