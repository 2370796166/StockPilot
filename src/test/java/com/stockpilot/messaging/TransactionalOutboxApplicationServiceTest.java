package com.stockpilot.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stockpilot.messaging.domain.BusinessEventNames;
import com.stockpilot.messaging.domain.CompletionBusinessEvent;
import com.stockpilot.messaging.domain.OutboxMessageEntity;
import com.stockpilot.messaging.mapper.MessageTraceMapper;
import com.stockpilot.messaging.mapper.OutboxMessageMapper;
import com.stockpilot.messaging.service.TransactionalOutboxApplicationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TransactionalOutboxApplicationServiceTest {
    @Test
    void persistsVersionedEventWithUniqueIdAndSortedDimensions() throws Exception {
        OutboxMessageMapper outbox = mock(OutboxMessageMapper.class);
        MessageTraceMapper traces = mock(MessageTraceMapper.class);
        when(outbox.insert(any())).thenReturn(1);
        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
        var service = new TransactionalOutboxApplicationService(outbox, traces, json);

        String id =
                service.enqueueSalesOutboundCompleted(
                        9L,
                        "SO-9",
                        2L,
                        List.of(
                                new CompletionBusinessEvent.InventoryDimension(8L, 7L),
                                new CompletionBusinessEvent.InventoryDimension(3L, 4L)));

        ArgumentCaptor<OutboxMessageEntity> captor =
                ArgumentCaptor.forClass(OutboxMessageEntity.class);
        verify(outbox).insert(captor.capture());
        OutboxMessageEntity saved = captor.getValue();
        assertEquals(id, saved.getMessageId());
        assertEquals(BusinessEventNames.SALES_OUTBOUND_COMPLETED, saved.getEventName());
        assertEquals(1, saved.getEventVersion());
        CompletionBusinessEvent payload =
                json.readValue(saved.getPayloadJson(), CompletionBusinessEvent.class);
        assertEquals(id, payload.messageId());
        assertEquals(
                List.of(3L, 8L),
                payload.data().dimensions().stream()
                        .map(CompletionBusinessEvent.InventoryDimension::locationId)
                        .toList());
        assertNotNull(payload.occurredAt());
        verify(traces)
                .insert(
                        id,
                        saved.getEventName(),
                        "SO-9",
                        "OUTBOX_CREATED",
                        null,
                        0,
                        "business transaction recorded event");
    }
}
