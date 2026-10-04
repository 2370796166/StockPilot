package com.stockpilot.ai.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record AiQuestionRequest(
        @NotBlank @Size(max = 1000) String question,
        @Size(max = 3) List<@Valid Selection> selections) {
    public record Selection(
            @NotBlank @Pattern(regexp = "sku|warehouse|location") String kind,
            @NotBlank @Size(max = 100) String keyword,
            @NotNull @Positive Long id) {}
}
