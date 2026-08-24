package com.stockpilot.messaging.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.alert.application.LowStockEventApplicationService;
import com.stockpilot.messaging.domain.CompletionBusinessEvent;
import com.stockpilot.messaging.infrastructure.mapper.FailureRecordMapper;
import com.stockpilot.messaging.infrastructure.mapper.MessageTraceMapper;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class DeadLetterApplicationService {
    private final CompletionEventCodec codec;
    private final ObjectMapper objectMapper;
    private final FailureRecordMapper failures;
    private final MessageTraceMapper traces;

    public DeadLetterApplicationService(
            CompletionEventCodec codec, ObjectMapper objectMapper,
            FailureRecordMapper failures, MessageTraceMapper traces) {
        this.codec = codec;
        this.objectMapper = objectMapper;
        this.failures = failures;
        this.traces = traces;
    }

    @Transactional
    public void record(String rawJson, MessageProperties properties) {
        String messageId = validId(properties.getMessageId());
        String eventName = header(properties, "eventName", "unknown-event");
        String businessNo = header(properties, "businessNo", "UNKNOWN");
        int eventVersion = integerHeader(properties, "eventVersion", 1);
        String payload = validJson(rawJson);
        try {
            CompletionBusinessEvent event = codec.decodeAndValidate(rawJson);
            messageId = event.messageId();
            eventName = event.eventName();
            businessNo = event.businessNo();
            eventVersion = event.eventVersion();
        } catch (RuntimeException ignored) {
            // Invalid payloads still need a durable operator-visible failure record.
        }
        String reason = header(properties, "x-exception-message", "consumer retries exhausted");
        if (reason.length() > 2000) reason = reason.substring(0, 2000);
        failures.upsert(messageId, eventName, eventVersion, businessNo, "CONSUME",
                LowStockEventApplicationService.CONSUMER_NAME, payload, reason);
        traces.insert(messageId, eventName, businessNo, "DEAD_LETTERED",
                LowStockEventApplicationService.CONSUMER_NAME, 0, reason.length() > 1000 ? reason.substring(0, 1000) : reason);
    }

    private String validJson(String raw) {
        try {
            objectMapper.readTree(raw);
            return raw;
        } catch (Exception exception) {
            try { return objectMapper.writeValueAsString(Map.of("rawPayload", raw)); }
            catch (Exception impossible) { return "{\"rawPayload\":\"unavailable\"}"; }
        }
    }

    private String validId(String value) {
        try { return UUID.fromString(value).toString(); }
        catch (Exception exception) { return UUID.randomUUID().toString(); }
    }

    private String header(MessageProperties properties, String name, String fallback) {
        Object value = properties.getHeaders().get(name);
        return value == null ? fallback : String.valueOf(value);
    }

    private int integerHeader(MessageProperties properties, String name, int fallback) {
        Object value = properties.getHeaders().get(name);
        return value instanceof Number number ? number.intValue() : fallback;
    }
}
