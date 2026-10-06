package com.stockpilot.messaging.service;

import com.stockpilot.messaging.config.MessagingProperties;
import com.stockpilot.messaging.domain.OutboxMessageEntity;
import com.stockpilot.messaging.mapper.FailureRecordMapper;
import com.stockpilot.messaging.mapper.MessageTraceMapper;
import com.stockpilot.messaging.mapper.OutboxMessageMapper;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public OutboxPublicationApplicationService(
            OutboxMessageMapper outbox,
            MessageTraceMapper traces,
            FailureRecordMapper failures,
            OutboxTransport transport,
            MessagingProperties properties) {
        this.outbox = outbox;
        this.traces = traces;
        this.failures = failures;
        this.transport = transport;
        this.properties = properties;
    }

    // 锁定一条到期的待发布消息并增加尝试次数，等待 Broker confirm 后才标记为 PUBLISHED。
    // 发布失败按指数退避重新调度；达到最大次数后终止重试并创建人工补偿记录。
    @Transactional
    public boolean publishNextDue() {
        OutboxMessageEntity message = outbox.selectNextDueForUpdate();
        if (message == null) return false;
        if (outbox.markAttempt(message.getMessageId()) != 1)
            throw new IllegalStateException("Cannot record publisher attempt");
        int attempt = message.getPublishAttempts() + 1;
        if (traces.insert(
                        message.getMessageId(),
                        message.getEventName(),
                        message.getBusinessNo(),
                        "PUBLISH_ATTEMPT",
                        "outbox-publisher",
                        attempt,
                        null)
                != 1) throw new IllegalStateException("Cannot persist publisher attempt trace");
        try {
            // Broker 已确认但数据库状态尚未提交时进程仍可能退出，因此消费者必须按消息 ID 幂等。
            transport.publish(message);
        } catch (Exception exception) {
            recordFailure(message, attempt, exception);
            return true;
        }
        // Persistence failures must roll back. They are not transport failures to swallow/retry
        // here.
        if (outbox.markPublished(message.getMessageId()) != 1)
            throw new IllegalStateException("Outbox state changed before publisher confirm");
        if (traces.insert(
                        message.getMessageId(),
                        message.getEventName(),
                        message.getBusinessNo(),
                        "PUBLISHED",
                        "outbox-publisher",
                        attempt,
                        "broker publisher confirm ack")
                != 1)
            throw new IllegalStateException("Cannot persist publisher confirmation trace");
        return true;
    }

    // 将异常摘要限制在数据库字段长度内，记录重试轨迹，并区分可重试失败与最终失败。
    private void recordFailure(OutboxMessageEntity message, int attempt, Exception exception) {
        String reason =
                abbreviate(
                        exception.getMessage() == null
                                ? exception.getClass().getName()
                                : exception.getMessage(),
                        1000);
        boolean terminal = attempt >= properties.getPublisherMaxAttempts();
        String status = terminal ? "FAILED" : "PENDING";
        LocalDateTime next =
                LocalDateTime.now().plusSeconds(Math.min(900L, 1L << Math.min(attempt, 9)));
        if (outbox.markFailure(message.getMessageId(), status, next, reason) != 1)
            throw new IllegalStateException("Cannot persist publisher retry state", exception);
        traces.insert(
                message.getMessageId(),
                message.getEventName(),
                message.getBusinessNo(),
                terminal ? "PUBLISH_FAILED" : "PUBLISH_RETRY",
                "outbox-publisher",
                attempt,
                reason);
        if (terminal) {
            failures.upsert(
                    message.getMessageId(),
                    message.getEventName(),
                    message.getEventVersion(),
                    message.getBusinessNo(),
                    "PUBLISH",
                    "outbox-publisher",
                    message.getPayloadJson(),
                    reason);
        }
        log.warn(
                "Outbox publish failed messageId={} attempt={} terminal={}",
                message.getMessageId(),
                attempt,
                terminal,
                exception);
    }

    private String abbreviate(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}
