package com.stockpilot.inventory.request;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Inclusive calendar dates, interpreted in the application's Asia/Shanghai database timezone. */
public record InventoryPeriodQuery(
        InventoryDimensionQuery dimension, LocalDate startDate, LocalDate endDate) {
    public InventoryPeriodQuery {
        if (dimension == null
                || startDate == null
                || endDate == null
                || startDate.getYear() < 1000
                || endDate.getYear() > 9998
                || endDate.isBefore(startDate)
                || ChronoUnit.DAYS.between(startDate, endDate) >= 92)
            throw new IllegalArgumentException(
                    "Inventory period must contain one to ninety-two days");
    }

    public LocalDateTime startInclusive() {
        return startDate.atStartOfDay();
    }

    public LocalDateTime endExclusive() {
        return endDate.plusDays(1).atStartOfDay();
    }
}
