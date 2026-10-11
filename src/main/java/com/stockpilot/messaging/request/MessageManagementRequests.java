package com.stockpilot.messaging.request;

import jakarta.validation.constraints.*;

public final class MessageManagementRequests {
    private MessageManagementRequests() {}

    public record Action(
            @NotNull @Min(1) Integer occurrenceCount, @NotBlank @Size(max = 500) String note) {}
}
