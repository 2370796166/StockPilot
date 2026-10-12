package com.stockpilot.messaging.service;

import com.stockpilot.messaging.config.MessagingProperties;
import com.stockpilot.messaging.domain.OutboxMessageEntity;
import com.stockpilot.messaging.mapper.FailureRecordMapper;
import com.stockpilot.messaging.mapper.MessageTraceMapper;
import com.stockpilot.messaging.mapper.OutboxMessageMapper;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(prefix = "stockpilot.messaging", name = "enabled", havingValue = "true")
public class OutboxPublicationApplicationService {
    private static final Logger log =
            LoggerFactory.getLogger(OutboxPublicationApplicationService.class);
    private final OutboxMessageMapper outbox;
    private final MessageTraceMapper traces;
    private final FailureRecordMapper failures;
    private final OutboxTransport transport;
    private final MessagingProperties properties;
    private final TransactionTemplate transactions;

    public OutboxPublicationApplicationService(
            OutboxMessageMapper outbox,
            MessageTraceMapper traces,
            FailureRecordMapper failures,
            OutboxTransport transport,
            MessagingProperties properties,
            PlatformTransactionManager transactionManager) {
        this.outbox = outbox;
        this.traces = traces;
        this.failures = failures;
        this.transport = transport;
        this.properties = properties;
        transactions = new TransactionTemplate(transactionManager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactions.setTimeout(10);
    }

    /**
     * Durable leased claim and confirmation are separate short transactions; network I/O owns no DB
     * transaction.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean publishNextDue() {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException(
                    "Outbox transport must run outside a business transaction");
        OutboxMessageEntity message = transactions.execute(status -> claim());
        if (message == null) return false;
        try {
            transport.publish(message);
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            transactions.executeWithoutResult(status -> recordFailure(message, exception));
            return true;
        }
        transactions.executeWithoutResult(
                status -> {
                    if (outbox.markPublished(message.getMessageId(), message.getClaimToken()) != 1)
                        throw new IllegalStateException(
                                "Outbox claim expired or was replaced before confirmation");
                    failures.resolvePublisherRetry(message.getMessageId());
                    if (traces.insert(
                                    message.getMessageId(),
                                    message.getEventName(),
                                    message.getBusinessNo(),
                                    "PUBLISHED",
                                    "outbox-publisher",
                                    message.getPublishAttempts(),
                                    "broker publisher confirm ack")
                            != 1)
                        throw new IllegalStateException(
                                "Cannot persist publisher confirmation trace");
                });
        return true;
    }

    private OutboxMessageEntity claim() {
        if (properties
                                .getPublisherLease()
                                .compareTo(properties.getPublisherConfirmTimeout().plusSeconds(5))
                        <= 0
                || properties.getPublisherLease().compareTo(java.time.Duration.ofMinutes(10)) > 0)
            throw new IllegalStateException(
                    "Publisher lease must exceed confirm timeout by five seconds and be at most ten minutes");
        var message = outbox.selectNextDueForUpdate();
        if (message == null) return null;
        String token = UUID.randomUUID().toString();
        int attempt = message.getPublishAttempts() + 1;
        if (outbox.markAttempt(
                        message.getMessageId(),
                        token,
                        properties.getPublisherLease().toMillis() * 1000)
                != 1) throw new IllegalStateException("Cannot claim publisher attempt");
        if (traces.insert(
                        message.getMessageId(),
                        message.getEventName(),
                        message.getBusinessNo(),
                        "PUBLISH_ATTEMPT",
                        "outbox-publisher",
                        attempt,
                        null)
                != 1) throw new IllegalStateException("Cannot persist publisher attempt trace");
        message.setPublishAttempts(attempt);
        message.setClaimToken(token);
        return message;
    }

    private void recordFailure(OutboxMessageEntity message, Exception exception) {
        String reason =
                exception.getMessage() == null
                        ? exception.getClass().getSimpleName()
                        : exception.getMessage();
        reason = MessageFailureText.bounded(reason, 1000);
        int attempt = message.getPublishAttempts();
        boolean terminal = attempt >= properties.getPublisherMaxAttempts();
        LocalDateTime next =
                LocalDateTime.now().plusSeconds(Math.min(900L, 1L << Math.min(attempt, 9)));
        if (outbox.markFailure(
                        message.getMessageId(),
                        terminal ? "FAILED" : "PENDING",
                        next,
                        reason,
                        message.getClaimToken())
                != 1)
            throw new IllegalStateException(
                    "Outbox claim changed while recording transport failure", exception);
        if (traces.insert(
                        message.getMessageId(),
                        message.getEventName(),
                        message.getBusinessNo(),
                        terminal ? "PUBLISH_FAILED" : "PUBLISH_RETRY",
                        "outbox-publisher",
                        attempt,
                        reason)
                != 1) throw new IllegalStateException("Cannot persist publisher failure trace");
        if (terminal
                && failures.upsert(
                                message.getMessageId(),
                                message.getEventName(),
                                message.getEventVersion(),
                                message.getBusinessNo(),
                                "PUBLISH",
                                "outbox-publisher",
                                message.getPayloadJson(),
                                reason)
                        <= 0)
            throw new IllegalStateException("Cannot persist terminal publisher failure");
        log.warn(
                "Outbox publish failed messageId={} attempt={} terminal={} exceptionClass={}",
                message.getMessageId(),
                attempt,
                terminal,
                exception.getClass().getSimpleName());
    }
}
