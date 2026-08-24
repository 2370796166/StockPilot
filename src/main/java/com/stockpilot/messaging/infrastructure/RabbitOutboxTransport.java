package com.stockpilot.messaging.infrastructure;

import com.stockpilot.messaging.application.OutboxTransport;
import com.stockpilot.messaging.config.MessagingProperties;
import com.stockpilot.messaging.domain.OutboxMessageEntity;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(prefix = "stockpilot.messaging", name = "enabled", havingValue = "true")
public class RabbitOutboxTransport implements OutboxTransport {
    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties properties;

    public RabbitOutboxTransport(RabbitTemplate rabbitTemplate, MessagingProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Override
    public void publish(OutboxMessageEntity message) throws Exception {
        CorrelationData correlation = new CorrelationData(message.getMessageId());
        rabbitTemplate.convertAndSend(properties.getExchange(), message.getRoutingKey(), message.getPayloadJson(), value -> {
            value.getMessageProperties().setContentType("application/json");
            value.getMessageProperties().setContentEncoding("UTF-8");
            value.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            value.getMessageProperties().setMessageId(message.getMessageId());
            value.getMessageProperties().setHeader("eventName", message.getEventName());
            value.getMessageProperties().setHeader("eventVersion", message.getEventVersion());
            value.getMessageProperties().setHeader("businessNo", message.getBusinessNo());
            return value;
        }, correlation);
        CorrelationData.Confirm confirm = correlation.getFuture().get(
                properties.getPublisherConfirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
        if (!confirm.isAck()) {
            throw new IllegalStateException("RabbitMQ publisher nack: " + confirm.getReason());
        }
        if (correlation.getReturned() != null) {
            throw new IllegalStateException("RabbitMQ returned unroutable message: "
                    + correlation.getReturned().getReplyText());
        }
    }
}
