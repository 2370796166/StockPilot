package com.stockpilot.masterdata.location.request;

import jakarta.validation.constraints.*;

public record UpdateLocationRequest(
        @NotNull @Positive Long warehouseId,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 255) String remark,
        @NotNull Integer version) {}
