package com.stockpilot.messaging.application;

import com.stockpilot.messaging.config.MessagingProperties;
import com.stockpilot.messaging.domain.OutboxMessageEntity;
import com.stockpilot.messaging.infrastructure.mapper.FailureRecordMapper;
import com.stockpilot.messaging.infrastructure.mapper.MessageTraceMapper;
import com.stockpilot.messaging.infrastructure.mapper.OutboxMessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@ConditionalOnProperty(prefix = "stockpilot.messaging", name = "enabled", havingValue = "true")
public class OutboxPublicationApplicationService {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublicationApplicationService.class);
    private final OutboxMessageMapper outbox;
    private final MessageTraceMapper traces;
    private final FailureRecordMapper failures;
    private final OutboxTransport transport;
    private final MessagingProperties properties;

    public OutboxPublicationApplicationService(
            OutboxMessageMapper outbox, MessageTraceMapper traces, FailureRecordMapper failures,
            OutboxTransport transport, MessagingProperties properties) {
        this.outbox = outbox;
        this.traces = traces;
        this.failures = failures;
        this.transport = transport;
        this.properties = properties;
    }

    @Transactional
    public boolean publishNextDue() {
        OutboxMessageEntity message = outbox.selectNextDueForUpdate();
        if (message == null) return false;
        if (outbox.markAttempt(message.getMessageId()) != 1) return true;
        int attempt = message.getPublishAttempts() + 1;
        traces.insert(message.getMessageId(), message.getEventName(), message.getBusinessNo(),
                "PUBLISH_ATTEMPT", "outbox-publisher", attempt, null);
        try {
            transport.publish(message);
            if (outbox.markPublished(message.getMessageId()) != 1) {
                throw new IllegalStateException("Outbox state changed before publisher confirm");
            }
            traces.insert(message.getMessageId(), message.getEventName(), message.getBusinessNo(),
                    "PUBLISHED", "outbox-publisher", attempt, "broker publisher confirm ack");
        } catch (Exception exception) {
            recordFailure(message, attempt, exception);
        }
        return true;
    }

    private void recordFailure(OutboxMessageEntity message, int attempt, Exception exception) {
        String reason = abbreviate(exception.getMessage() == null ? exception.getClass().getName() : exception.getMessage(), 1000);
        boolean terminal = attempt >= properties.getPublisherMaxAttempts();
        String status = terminal ? "FAILED" : "PENDING";
        LocalDateTime next = LocalDateTime.now().plusSeconds(Math.min(900L, 1L << Math.min(attempt, 9)));
        outbox.markFailure(message.getMessageId(), status, next, reason);
        traces.insert(message.getMessageId(), message.getEventName(), message.getBusinessNo(),
                terminal ? "PUBLISH_FAILED" : "PUBLISH_RETRY", "outbox-publisher", attempt, reason);
        if (terminal) {
            failures.upsert(message.getMessageId(), message.getEventName(), message.getEventVersion(),
                    message.getBusinessNo(), "PUBLISH", "outbox-publisher", message.getPayloadJson(), reason);
        }
        log.warn("Outbox publish failed messageId={} attempt={} terminal={}",
                message.getMessageId(), attempt, terminal, exception);
    }

    private String abbreviate(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}
