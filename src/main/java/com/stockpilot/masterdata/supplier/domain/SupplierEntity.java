package com.stockpilot.masterdata.supplier.domain;
import com.baomidou.mybatisplus.annotation.TableName;
import com.stockpilot.masterdata.domain.BaseMasterDataEntity;
@TableName("supplier")
public class SupplierEntity extends BaseMasterDataEntity {
    private String contactName;
    private String contactPhone;
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
}
