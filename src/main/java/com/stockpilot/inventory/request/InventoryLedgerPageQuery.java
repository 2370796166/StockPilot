package com.stockpilot.inventory.request;

import com.stockpilot.inventory.domain.InventoryBusinessType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class InventoryLedgerPageQuery {
    @org.springframework.format.annotation.DateTimeFormat(
            iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
    private java.time.LocalDate startDate;

    @org.springframework.format.annotation.DateTimeFormat(
            iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
    private java.time.LocalDate endDate;

    public java.time.LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(java.time.LocalDate value) {
        startDate = value;
    }

    public java.time.LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(java.time.LocalDate value) {
        endDate = value;
    }

    public java.time.LocalDateTime getStartInclusive() {
        return startDate == null ? null : startDate.atStartOfDay();
    }

    public java.time.LocalDateTime getEndExclusive() {
        return endDate == null ? null : endDate.plusDays(1).atStartOfDay();
    }

    @jakarta.validation.constraints.AssertTrue(message = "日期须成对提供、顺序正确，且最多九十二个自然日")
    public boolean isPeriodValid() {
        return startDate == null && endDate == null
                || startDate != null
                        && endDate != null
                        && startDate.getYear() >= 1000
                        && endDate.getYear() <= 9998
                        && !endDate.isBefore(startDate)
                        && java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) < 92;
    }

    @Min(1)
    private long page = 1;

    @Min(1)
    @Max(100)
    private long size = 20;

    @Positive private Long warehouseId;
    @Positive private Long locationId;
    @Positive private Long skuId;
    private InventoryBusinessType businessType;

    @Size(max = 64)
    private String businessNo;

    public long getPage() {
        return page;
    }

    public void setPage(long page) {
        this.page = page;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public InventoryBusinessType getBusinessType() {
        return businessType;
    }

    public void setBusinessType(InventoryBusinessType businessType) {
        this.businessType = businessType;
    }

    public String getBusinessNo() {
        return businessNo;
    }

    public void setBusinessNo(String businessNo) {
        this.businessNo = businessNo;
    }
}
