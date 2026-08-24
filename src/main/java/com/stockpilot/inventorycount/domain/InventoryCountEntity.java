package com.stockpilot.inventorycount.domain;

import java.time.LocalDateTime;

public class InventoryCountEntity {
    private Long id; private String countNo; private Long warehouseId; private InventoryCountStatus status;
    private String remark; private Long createdBy; private String createdByName;
    private Long countingBy; private String countingByName; private LocalDateTime countingAt;
    private Long submittedBy; private String submittedByName; private LocalDateTime submittedAt;
    private Long approvedBy; private String approvedByName; private LocalDateTime approvedAt;
    private Long adjustedBy; private String adjustedByName; private LocalDateTime adjustedAt;
    private LocalDateTime createdAt; private LocalDateTime updatedAt; private Integer version;
    public Long getId(){return id;} public void setId(Long v){id=v;} public String getCountNo(){return countNo;} public void setCountNo(String v){countNo=v;}
    public Long getWarehouseId(){return warehouseId;} public void setWarehouseId(Long v){warehouseId=v;} public InventoryCountStatus getStatus(){return status;} public void setStatus(InventoryCountStatus v){status=v;}
    public String getRemark(){return remark;} public void setRemark(String v){remark=v;} public Long getCreatedBy(){return createdBy;} public void setCreatedBy(Long v){createdBy=v;} public String getCreatedByName(){return createdByName;} public void setCreatedByName(String v){createdByName=v;}
    public Long getCountingBy(){return countingBy;} public void setCountingBy(Long v){countingBy=v;} public String getCountingByName(){return countingByName;} public void setCountingByName(String v){countingByName=v;} public LocalDateTime getCountingAt(){return countingAt;} public void setCountingAt(LocalDateTime v){countingAt=v;}
    public Long getSubmittedBy(){return submittedBy;} public void setSubmittedBy(Long v){submittedBy=v;} public String getSubmittedByName(){return submittedByName;} public void setSubmittedByName(String v){submittedByName=v;} public LocalDateTime getSubmittedAt(){return submittedAt;} public void setSubmittedAt(LocalDateTime v){submittedAt=v;}
    public Long getApprovedBy(){return approvedBy;} public void setApprovedBy(Long v){approvedBy=v;} public String getApprovedByName(){return approvedByName;} public void setApprovedByName(String v){approvedByName=v;} public LocalDateTime getApprovedAt(){return approvedAt;} public void setApprovedAt(LocalDateTime v){approvedAt=v;}
    public Long getAdjustedBy(){return adjustedBy;} public void setAdjustedBy(Long v){adjustedBy=v;} public String getAdjustedByName(){return adjustedByName;} public void setAdjustedByName(String v){adjustedByName=v;} public LocalDateTime getAdjustedAt(){return adjustedAt;} public void setAdjustedAt(LocalDateTime v){adjustedAt=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;} public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;} public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;}
}
