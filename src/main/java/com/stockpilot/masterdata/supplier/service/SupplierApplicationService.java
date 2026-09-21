package com.stockpilot.masterdata.supplier.service;

import com.stockpilot.masterdata.service.MasterDataApplicationService;
import com.stockpilot.masterdata.supplier.domain.SupplierEntity;
import com.stockpilot.masterdata.supplier.mapper.SupplierMapper;
import org.springframework.stereotype.Service;

@Service
public class SupplierApplicationService extends MasterDataApplicationService<SupplierEntity> {
    public SupplierApplicationService(SupplierMapper mapper) {
        super(mapper, SupplierEntity::new, "供应商编码已存在");
    }
}
