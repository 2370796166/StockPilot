package com.stockpilot.messaging;

import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.messaging.mapper.MessageTraceMapper;
import com.stockpilot.messaging.service.CompletionEventCodec;
import com.stockpilot.messaging.service.ConsumerRetryTraceApplicationService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageProperties;

class ConsumerRetryTraceApplicationServiceTest {
    @Test
    void malformedMetadataAndSupplementaryCharactersFitTheTraceColumns() {
        var traces = mock(MessageTraceMapper.class);
        var service =
                new ConsumerRetryTraceApplicationService(
                        new CompletionEventCodec(new ObjectMapper()), traces);
        var properties = new MessageProperties();
        properties.setMessageId(UUID.randomUUID().toString());
        properties.setHeader("eventName", "x".repeat(101));
        properties.setHeader("businessNo", "y".repeat(65));
        service.recordFailure(
                "", properties, 1, new IllegalArgumentException("a".repeat(999) + "😀b"));
        verify(traces)
                .insert(
                        properties.getMessageId(),
                        "x".repeat(100),
                        "y".repeat(64),
                        "CONSUME_RETRY",
                        "low-stock-alert-v1",
                        1,
                        "a".repeat(999) + "😀");
    }
}
