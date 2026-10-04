package com.stockpilot.ai.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("stockpilot.ai")
public record AiProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("CUSTOM") String provider,
        @DefaultValue("") String baseUrl,
        @DefaultValue("") String apiKey,
        @DefaultValue("") String model,
        @DefaultValue("20s") Duration timeout,
        @DefaultValue("6") int maxToolCalls) {
    @Override
    public String toString() {
        return "AiProperties[enabled=" + enabled + ", credentials=redacted]";
    }
}
