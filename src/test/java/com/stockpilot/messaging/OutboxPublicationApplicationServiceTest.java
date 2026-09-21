package com.stockpilot.messaging;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockpilot.messaging.config.MessagingProperties;
import com.stockpilot.messaging.domain.BusinessEventNames;
import com.stockpilot.messaging.domain.OutboxMessageEntity;
import com.stockpilot.messaging.mapper.FailureRecordMapper;
import com.stockpilot.messaging.mapper.MessageTraceMapper;
import com.stockpilot.messaging.mapper.OutboxMessageMapper;
import com.stockpilot.messaging.service.OutboxPublicationApplicationService;
import com.stockpilot.messaging.service.OutboxTransport;
import org.junit.jupiter.api.Test;

class OutboxPublicationApplicationServiceTest {
    @Test
    void publisherConfirmMarksMessagePublished() throws Exception {
        Fixture fixture = new Fixture(0, 3);
        assertTrue(fixture.service.publishNextDue());
        verify(fixture.transport).publish(fixture.message);
        verify(fixture.outbox).markPublished(fixture.message.getMessageId());
        verify(fixture.failures, never())
                .upsert(any(), any(), any(Integer.class), any(), any(), any(), any(), any());
    }

    @Test
    void brokerUnavailableSchedulesBoundedRetryWithoutFailingBusinessTransaction()
            throws Exception {
        Fixture fixture = new Fixture(0, 3);
        doThrow(new IllegalStateException("broker unavailable"))
                .when(fixture.transport)
                .publish(fixture.message);
        assertTrue(fixture.service.publishNextDue());
        verify(fixture.outbox)
                .markFailure(
                        eq(fixture.message.getMessageId()),
                        eq("PENDING"),
                        any(),
                        eq("broker unavailable"));
        verify(fixture.failures, never())
                .upsert(any(), any(), any(Integer.class), any(), any(), any(), any(), any());
    }

    @Test
    void exhaustedPublisherAttemptsCreateOperatorVisibleFailure() throws Exception {
        Fixture fixture = new Fixture(2, 3);
        doThrow(new IllegalStateException("publisher nack"))
                .when(fixture.transport)
                .publish(fixture.message);
        assertTrue(fixture.service.publishNextDue());
        verify(fixture.outbox)
                .markFailure(
                        eq(fixture.message.getMessageId()),
                        eq("FAILED"),
                        any(),
                        eq("publisher nack"));
        verify(fixture.failures)
                .upsert(
                        fixture.message.getMessageId(),
                        fixture.message.getEventName(),
                        1,
                        fixture.message.getBusinessNo(),
                        "PUBLISH",
                        "outbox-publisher",
                        "{}",
                        "publisher nack");
    }

    private static final class Fixture {
        final OutboxMessageMapper outbox = mock(OutboxMessageMapper.class);
        final MessageTraceMapper traces = mock(MessageTraceMapper.class);
        final FailureRecordMapper failures = mock(FailureRecordMapper.class);
        final OutboxTransport transport = mock(OutboxTransport.class);
        final OutboxMessageEntity message = message();
        final OutboxPublicationApplicationService service;

        Fixture(int attempts, int maximum) {
            message.setPublishAttempts(attempts);
            when(outbox.selectNextDueForUpdate()).thenReturn(message);
            when(outbox.markAttempt(message.getMessageId())).thenReturn(1);
            when(outbox.markPublished(message.getMessageId())).thenReturn(1);
            MessagingProperties properties = new MessagingProperties();
            properties.setPublisherMaxAttempts(maximum);
            service =
                    new OutboxPublicationApplicationService(
                            outbox, traces, failures, transport, properties);
        }

        private static OutboxMessageEntity message() {
            OutboxMessageEntity value = new OutboxMessageEntity();
            value.setMessageId("7fdf128b-3b31-4776-9458-a61f44df70ac");
            value.setEventName(BusinessEventNames.SALES_OUTBOUND_COMPLETED);
            value.setEventVersion(1);
            value.setBusinessNo("SO-1");
            value.setRoutingKey(BusinessEventNames.SALES_OUTBOUND_ROUTING_KEY);
            value.setPayloadJson("{}");
            return value;
        }
    }
}
