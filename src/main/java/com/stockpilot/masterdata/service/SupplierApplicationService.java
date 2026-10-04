package com.stockpilot.masterdata.service;

import com.stockpilot.masterdata.domain.SupplierEntity;
import com.stockpilot.masterdata.mapper.SupplierMapper;
import org.springframework.stereotype.Service;

@Service
public class SupplierApplicationService extends MasterDataApplicationService<SupplierEntity> {
    public SupplierApplicationService(SupplierMapper mapper) {
        super(mapper, SupplierEntity::new, "供应商编码已存在");
    }
}
