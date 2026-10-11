package com.stockpilot.alert.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public final class SafetyStockRequests {
    private SafetyStockRequests() {}

    public record Create(
            @Positive long warehouseId,
            @Positive long locationId,
            @Positive long skuId,
            @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4)
                    BigDecimal thresholdQuantity) {}

    public record Update(
            @NotNull @Min(0) Integer version,
            @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4)
                    BigDecimal thresholdQuantity,
            @NotBlank @Pattern(regexp = "ENABLED|DISABLED") String status) {}
}
