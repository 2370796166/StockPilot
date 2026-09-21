package com.stockpilot.masterdata.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateMasterDataRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 255) String remark,
        @NotNull Integer version) {}
