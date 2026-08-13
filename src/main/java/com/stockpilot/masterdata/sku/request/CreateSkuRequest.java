package com.stockpilot.masterdata.sku.request;
import jakarta.validation.constraints.*;
public record CreateSkuRequest(
 @NotBlank @Size(max=32) @Pattern(regexp="^[A-Za-z][A-Za-z0-9_-]{1,31}$",message="编码须以字母开头，仅包含字母、数字、下划线或连字符，长度2-32") String code,
 @NotBlank @Size(max=100) String name,@Positive Long categoryId,@NotBlank @Size(max=20) String unit,@Size(max=255) String remark) {}
