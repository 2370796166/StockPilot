package com.stockpilot.ai.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record AiQuestionRequest(
        @NotBlank @Size(max = 1000) String question,
        @Size(max = 3) List<@Valid Selection> selections,
        @Size(max = 32768) String continuationToken) {
    public AiQuestionRequest(String question, List<Selection> selections) {
        this(question, selections, null);
    }

    public record Selection(
            @NotBlank @Pattern(regexp = "sku|warehouse|location") String kind,
            @NotBlank @Size(max = 100) String keyword,
            @NotNull @Positive Long id) {}
}
