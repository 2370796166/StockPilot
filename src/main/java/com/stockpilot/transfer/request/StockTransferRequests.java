package com.stockpilot.transfer.request;

import com.stockpilot.transfer.domain.StockTransferStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public final class StockTransferRequests {
 private StockTransferRequests(){}
 public record Line(@NotNull @Positive Long sourceLocationId,@NotNull @Positive Long targetLocationId,
  @NotNull @Positive Long skuId,@NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=15,fraction=4) BigDecimal quantity){}
 public record Create(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{2,64}") String transferNo,
  @NotNull @Positive Long sourceWarehouseId,@NotNull @Positive Long targetWarehouseId,
  @Size(max=255) String remark,@NotEmpty List<@Valid Line> lines){}
 public record Update(@NotNull @Min(0) Integer version,@NotNull @Positive Long sourceWarehouseId,
  @NotNull @Positive Long targetWarehouseId,@Size(max=255) String remark,@NotEmpty List<@Valid Line> lines){}
 public record Transition(@NotNull @Min(0) Integer version){}
 public static final class PageQuery {
  @Min(1) private long page=1; @Min(1) @Max(100) private long size=20;
  @Size(max=64) private String transferNo; @Positive private Long sourceWarehouseId;
  @Positive private Long targetWarehouseId; private StockTransferStatus status;
  public long getPage(){return page;} public void setPage(long v){page=v;}
  public long getSize(){return size;} public void setSize(long v){size=v;}
  public String getTransferNo(){return transferNo;} public void setTransferNo(String v){transferNo=v;}
  public Long getSourceWarehouseId(){return sourceWarehouseId;} public void setSourceWarehouseId(Long v){sourceWarehouseId=v;}
  public Long getTargetWarehouseId(){return targetWarehouseId;} public void setTargetWarehouseId(Long v){targetWarehouseId=v;}
  public StockTransferStatus getStatus(){return status;} public void setStatus(StockTransferStatus v){status=v;}
 }
}
