package com.stockpilot.messaging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.messaging.mapper.FailureRecordMapper;
import com.stockpilot.messaging.mapper.MessageTraceMapper;
import com.stockpilot.messaging.service.CompletionEventCodec;
import com.stockpilot.messaging.service.DeadLetterApplicationService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.MessageProperties;

class DeadLetterApplicationServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final FailureRecordMapper failures = mock(FailureRecordMapper.class);
    private final MessageTraceMapper traces = mock(MessageTraceMapper.class);
    private final DeadLetterApplicationService service =
            new DeadLetterApplicationService(
                    new CompletionEventCodec(json), json, failures, traces);

    @ParameterizedTest
    @ValueSource(strings = {"", " \r\n\t", "{}{}", "{} trailing", "null{}"})
    void malformedBodyIsStoredAsCompleteJsonWithoutLosingOriginalText(String raw) throws Exception {
        var saved = record(raw);
        var node =
                json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(saved);
        assertTrue(node.isObject(), "Malformed payload must be wrapped in valid JSON");
        assertEquals(raw, node.path("rawPayload").asText());
    }

    @Test
    void validJsonIsPreservedForOperatorReview() throws Exception {
        String raw = " {\"unrecognized\":true} \n";
        assertEquals(raw, record(raw));
    }

    @Test
    void absentBodyCanStillProduceAValidFailureRecord() throws Exception {
        var saved = json.readTree(record(null));
        assertTrue(saved.path("rawPayload").isNull());
    }

    private String record(String raw) {
        var properties = new MessageProperties();
        properties.setMessageId(UUID.randomUUID().toString());
        service.record(raw, properties);
        var payload = ArgumentCaptor.forClass(String.class);
        verify(failures)
                .upsert(
                        eq(properties.getMessageId()),
                        anyString(),
                        anyInt(),
                        anyString(),
                        eq("CONSUME"),
                        anyString(),
                        payload.capture(),
                        anyString());
        verify(traces)
                .insert(
                        eq(properties.getMessageId()),
                        anyString(),
                        anyString(),
                        eq("DEAD_LETTERED"),
                        anyString(),
                        eq(0),
                        anyString());
        return payload.getValue();
    }
}
