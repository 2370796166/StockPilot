package com.stockpilot.shared.query;

import jakarta.validation.constraints.AssertTrue;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Shared date semantics for business document lists; dates include both endpoints. */
public class DocumentDateRangeQuery {
    private LocalDate startDate;
    private LocalDate endDate;
    private DateField dateField = DateField.CREATED;
    private boolean unfinished;

    public enum DateField {
        CREATED,
        COMPLETED
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate value) {
        startDate = value;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate value) {
        endDate = value;
    }

    public LocalDate getEndExclusive() {
        return endDate == null ? null : endDate.plusDays(1);
    }

    public DateField getDateField() {
        return dateField;
    }

    public void setDateField(DateField value) {
        dateField = value;
    }

    public boolean isUnfinished() {
        return unfinished;
    }

    public void setUnfinished(boolean value) {
        unfinished = value;
    }

    @AssertTrue(message = "单据日期区间应包含起止日且不超过92天")
    public boolean isPeriodValid() {
        return dateField != null
                && (startDate == null && endDate == null
                        || startDate != null
                                && endDate != null
                                && !endDate.isBefore(startDate)
                                && ChronoUnit.DAYS.between(startDate, endDate) < 92
                                && startDate.getYear() >= 1970
                                && endDate.getYear() <= 9998);
    }
}
