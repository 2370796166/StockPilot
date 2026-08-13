package com.stockpilot.masterdata.supplier.application;
import com.stockpilot.masterdata.application.MasterDataApplicationService;
import com.stockpilot.masterdata.supplier.domain.SupplierEntity;
import com.stockpilot.masterdata.supplier.infrastructure.mapper.SupplierMapper;
import org.springframework.stereotype.Service;
@Service
public class SupplierApplicationService extends MasterDataApplicationService<SupplierEntity> {
    public SupplierApplicationService(SupplierMapper mapper) { super(mapper, SupplierEntity::new, "供应商编码已存在"); }
}
