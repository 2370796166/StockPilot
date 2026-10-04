package com.stockpilot.messaging.infrastructure;

import com.stockpilot.messaging.domain.CompletionBusinessEvent;
import com.stockpilot.messaging.service.CompletionEventCodec;
import com.stockpilot.messaging.service.CompletionEventConsumptionApplicationService;
import com.stockpilot.messaging.service.ConsumerRetryTraceApplicationService;
import com.stockpilot.messaging.service.DeadLetterApplicationService;
import java.nio.charset.StandardCharsets;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "stockpilot.messaging", name = "enabled", havingValue = "true")
public class CompletionEventRabbitListener {
    private final CompletionEventCodec codec;
    private final CompletionEventConsumptionApplicationService completionEventConsumption;
    private final ConsumerRetryTraceApplicationService retryTraces;
    private final DeadLetterApplicationService deadLetters;

    public CompletionEventRabbitListener(
            CompletionEventCodec codec,
            CompletionEventConsumptionApplicationService completionEventConsumption,
            ConsumerRetryTraceApplicationService retryTraces,
            DeadLetterApplicationService deadLetters) {
        this.codec = codec;
        this.completionEventConsumption = completionEventConsumption;
        this.retryTraces = retryTraces;
        this.deadLetters = deadLetters;
    }

    @RabbitListener(
            queues = "${stockpilot.messaging.completion-queue}",
            containerFactory = "completionRabbitListenerContainerFactory")
    public void consume(Message message) {
        String json = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            CompletionBusinessEvent event = codec.decodeAndValidate(json);
            completionEventConsumption.handle(event);
        } catch (RuntimeException exception) {
            int attempt =
                    RetrySynchronizationManager.getContext() == null
                            ? 1
                            : RetrySynchronizationManager.getContext().getRetryCount() + 1;
            retryTraces.recordFailure(json, message.getMessageProperties(), attempt, exception);
            throw exception;
        }
    }

    @RabbitListener(
            queues = "${stockpilot.messaging.dead-letter-queue}",
            containerFactory = "deadLetterRabbitListenerContainerFactory")
    public void consumeDeadLetter(Message message) {
        deadLetters.record(
                new String(message.getBody(), StandardCharsets.UTF_8),
                message.getMessageProperties());
    }
}
