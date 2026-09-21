package com.stockpilot.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stockpilot.messaging.domain.BusinessEventNames;
import com.stockpilot.messaging.domain.CompletionBusinessEvent;
import com.stockpilot.messaging.service.CompletionEventCodec;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompletionEventCodecTest {
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
    private final CompletionEventCodec codec = new CompletionEventCodec(json);

    @Test
    void acceptsVersionedMinimalCompletionEvent() throws Exception {
        var event = event(BusinessEventNames.PURCHASE_RECEIPT_COMPLETED, 1);
        var decoded = codec.decodeAndValidate(json.writeValueAsString(event));
        assertEquals(event, decoded);
        assertEquals(1, decoded.eventVersion());
        assertEquals("PR-100", decoded.businessNo());
    }

    @Test
    void rejectsUnknownVersionAndIncompletePayload() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        codec.decodeAndValidate(
                                json.writeValueAsString(
                                        event(BusinessEventNames.PURCHASE_RECEIPT_COMPLETED, 2))));
        assertThrows(IllegalArgumentException.class, () -> codec.decodeAndValidate("{}"));
    }

    private CompletionBusinessEvent event(String name, int version) {
        return new CompletionBusinessEvent(
                UUID.randomUUID().toString(),
                name,
                version,
                "PR-100",
                Instant.now(),
                new CompletionBusinessEvent.CompletionData(
                        10L,
                        20L,
                        List.of(new CompletionBusinessEvent.InventoryDimension(30L, 40L))));
    }
}
