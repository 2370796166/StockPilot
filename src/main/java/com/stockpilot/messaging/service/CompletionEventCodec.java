package com.stockpilot.messaging.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.messaging.domain.BusinessEventNames;
import com.stockpilot.messaging.domain.CompletionBusinessEvent;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CompletionEventCodec {
    private final ObjectMapper objectMapper;

    public CompletionEventCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CompletionBusinessEvent decodeAndValidate(String json) {
        try {
            CompletionBusinessEvent event =
                    objectMapper
                            .readerFor(CompletionBusinessEvent.class)
                            .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                            .readValue(json);
            validate(event);
            return event;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid completion event JSON", exception);
        }
    }

    private void validate(CompletionBusinessEvent event) {
        if (event == null
                || event.messageId() == null
                || event.businessNo() == null
                || event.occurredAt() == null
                || event.data() == null
                || !BusinessEventNames.supportsInventoryChange(
                        event.eventName(), event.eventVersion())) {
            throw new IllegalArgumentException("Unsupported or incomplete completion event");
        }
        try {
            UUID.fromString(event.messageId());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid messageId", exception);
        }
        if (event.businessNo().isBlank()
                || event.businessNo().codePointCount(0, event.businessNo().length()) > 64
                || event.data().documentId() <= 0
                || event.data().warehouseId() <= 0
                || event.data().dimensions() == null
                || event.data().dimensions().isEmpty()
                || event.data().dimensions().stream()
                        .anyMatch(value -> value.locationId() <= 0 || value.skuId() <= 0)) {
            throw new IllegalArgumentException("Invalid completion event data");
        }
    }
}
