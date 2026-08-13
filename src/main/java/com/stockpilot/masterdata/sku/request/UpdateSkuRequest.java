package com.stockpilot.masterdata.sku.request;
import jakarta.validation.constraints.*;
public record UpdateSkuRequest(@NotBlank @Size(max=100) String name,@Positive Long categoryId,
 @NotBlank @Size(max=20) String unit,@Size(max=255) String remark,@NotNull Integer version) {}
