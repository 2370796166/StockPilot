package com.stockpilot.masterdata.domain;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("supplier")
public class SupplierEntity extends BaseMasterDataEntity {
    private String contactName;
    private String contactPhone;

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }
}
