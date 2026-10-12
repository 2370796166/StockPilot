package com.stockpilot.messaging.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.messaging.domain.CompletionBusinessEvent;
import com.stockpilot.messaging.mapper.FailureRecordMapper;
import com.stockpilot.messaging.mapper.MessageTraceMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeadLetterApplicationService {
    private final CompletionEventCodec codec;
    private final ObjectMapper objectMapper;
    private final FailureRecordMapper failures;
    private final MessageTraceMapper traces;

    public DeadLetterApplicationService(
            CompletionEventCodec codec,
            ObjectMapper objectMapper,
            FailureRecordMapper failures,
            MessageTraceMapper traces) {
        this.codec = codec;
        this.objectMapper = objectMapper;
        this.failures = failures;
        this.traces = traces;
    }

    @Transactional
    // 处理最终死信：保留原始载荷和失败上下文，形成可查询、可人工补偿的异常记录。
    public void record(String rawJson, MessageProperties properties) {
        String messageId = validId(properties.getMessageId());
        String eventName = bounded(header(properties, "eventName", "unknown-event"), 100);
        String businessNo = bounded(header(properties, "businessNo", "UNKNOWN"), 64);
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
        reason = bounded(reason, 2000);
        failures.upsert(
                messageId,
                eventName,
                eventVersion,
                businessNo,
                "CONSUME",
                CompletionEventConsumptionApplicationService.CONSUMER_NAME,
                payload,
                reason);
        traces.insert(
                messageId,
                eventName,
                businessNo,
                "DEAD_LETTERED",
                CompletionEventConsumptionApplicationService.CONSUMER_NAME,
                0,
                bounded(reason, 1000));
    }

    private String validJson(String raw) {
        try {
            objectMapper.readTree(raw);
            return raw;
        } catch (Exception exception) {
            try {
                return objectMapper.writeValueAsString(Map.of("rawPayload", raw));
            } catch (Exception impossible) {
                return "{\"rawPayload\":\"unavailable\"}";
            }
        }
    }

    private String validId(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (Exception exception) {
            return UUID.randomUUID().toString();
        }
    }

    private String header(MessageProperties properties, String name, String fallback) {
        Object value = properties.getHeaders().get(name);
        return value == null ? fallback : String.valueOf(value);
    }

    private int integerHeader(MessageProperties properties, String name, int fallback) {
        Object value = properties.getHeaders().get(name);
        int version = value instanceof Number number ? number.intValue() : fallback;
        return version > 0 ? version : fallback;
    }

    private String bounded(String value, int maxCharacters) {
        return value.codePointCount(0, value.length()) <= maxCharacters
                ? value
                : value.substring(0, value.offsetByCodePoints(0, maxCharacters));
    }
}
