package com.stockpilot.ai.request;

import jakarta.validation.constraints.*;
import java.util.Map;

public final class AiAgentRequests {
    private AiAgentRequests() {}

    public record Question(
            @NotBlank @Size(max = 1000) String question,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,80}") String requestId) {}

    public record Input(
            @Min(0) long version,
            @Size(max = 8) Map<String, @Positive Long> choices,
            @Size(max = 8) Map<String, @NotBlank @Size(max = 100) String> conditions,
            @Size(max = 1) Map<String, @NotBlank @Size(max = 100) String> refinements,
            @Size(max = 1000) String message) {
        public Input(
                long version,
                Map<String, Long> choices,
                Map<String, String> conditions,
                Map<String, String> refinements) {
            this(version, choices, conditions, refinements, null);
        }

        public Input(long version, Map<String, Long> choices, Map<String, String> conditions) {
            this(version, choices, conditions, Map.of(), null);
        }
    }

    public record Retry(
            @Min(0) long version,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,80}") String requestId) {}
}
