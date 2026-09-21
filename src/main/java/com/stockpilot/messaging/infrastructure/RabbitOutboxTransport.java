package com.stockpilot.messaging.infrastructure;

import com.stockpilot.messaging.config.MessagingProperties;
import com.stockpilot.messaging.domain.OutboxMessageEntity;
import com.stockpilot.messaging.service.OutboxTransport;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

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
    // 以持久消息发送 JSON 事件，并把契约名称、版本和业务单号写入消息头供消费者校验和追踪。
    // mandatory return 与 publisher confirm 同时用于识别不可路由消息和 Broker 拒绝确认。
    public void publish(OutboxMessageEntity message) throws Exception {
        CorrelationData correlation = new CorrelationData(message.getMessageId());
        rabbitTemplate.convertAndSend(
                properties.getExchange(),
                message.getRoutingKey(),
                message.getPayloadJson(),
                value -> {
                    value.getMessageProperties().setContentType("application/json");
                    value.getMessageProperties().setContentEncoding("UTF-8");
                    value.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    value.getMessageProperties().setMessageId(message.getMessageId());
                    value.getMessageProperties().setHeader("eventName", message.getEventName());
                    value.getMessageProperties()
                            .setHeader("eventVersion", message.getEventVersion());
                    value.getMessageProperties().setHeader("businessNo", message.getBusinessNo());
                    return value;
                },
                correlation);
        // 只有收到 publisher confirm 且消息未被退回，调用方才能将 Outbox 标记为已发布。
        CorrelationData.Confirm confirm =
                correlation
                        .getFuture()
                        .get(
                                properties.getPublisherConfirmTimeout().toMillis(),
                                TimeUnit.MILLISECONDS);
        if (!confirm.isAck()) {
            throw new IllegalStateException("RabbitMQ publisher nack: " + confirm.getReason());
        }
        if (correlation.getReturned() != null) {
            throw new IllegalStateException(
                    "RabbitMQ returned unroutable message: "
                            + correlation.getReturned().getReplyText());
        }
    }
}
