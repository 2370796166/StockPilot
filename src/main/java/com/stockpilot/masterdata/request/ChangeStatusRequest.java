package com.stockpilot.masterdata.request;

import com.stockpilot.masterdata.domain.MasterDataStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(@NotNull MasterDataStatus status, @NotNull Integer version) {}
