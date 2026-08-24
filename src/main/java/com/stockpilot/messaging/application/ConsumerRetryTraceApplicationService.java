package com.stockpilot.messaging.application;

import com.stockpilot.alert.application.LowStockEventApplicationService;
import com.stockpilot.messaging.domain.CompletionBusinessEvent;
import com.stockpilot.messaging.infrastructure.mapper.MessageTraceMapper;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ConsumerRetryTraceApplicationService {
    private final CompletionEventCodec codec;
    private final MessageTraceMapper traces;

    public ConsumerRetryTraceApplicationService(CompletionEventCodec codec, MessageTraceMapper traces) {
        this.codec = codec;
        this.traces = traces;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String json, MessageProperties properties, int attempt, RuntimeException failure) {
        String messageId = header(properties, "messageId", properties.getMessageId());
        String eventName = header(properties, "eventName", "unknown-event");
        String businessNo = header(properties, "businessNo", "UNKNOWN");
        try {
            CompletionBusinessEvent event = codec.decodeAndValidate(json);
            messageId = event.messageId();
            eventName = event.eventName();
            businessNo = event.businessNo();
        } catch (RuntimeException ignored) {
            if (messageId == null || messageId.length() != 36) messageId = UUID.randomUUID().toString();
        }
        String detail = failure.getMessage() == null ? failure.getClass().getName() : failure.getMessage();
        if (detail.length() > 1000) detail = detail.substring(0, 1000);
        traces.insert(messageId, eventName, businessNo, "CONSUME_RETRY",
                LowStockEventApplicationService.CONSUMER_NAME, attempt, detail);
    }

    private String header(MessageProperties properties, String name, String fallback) {
        Object value = properties.getHeaders().get(name);
        return value == null ? fallback : String.valueOf(value);
    }
}
