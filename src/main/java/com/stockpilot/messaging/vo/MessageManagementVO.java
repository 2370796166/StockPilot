package com.stockpilot.messaging.vo;

import java.time.LocalDateTime;
import java.util.List;

public final class MessageManagementVO {
    private MessageManagementVO() {}

    public record Failure(
            long id,
            String messageId,
            String eventName,
            int eventVersion,
            String businessNo,
            String failureStage,
            String consumerName,
            String failureReason,
            String status,
            int occurrenceCount,
            LocalDateTime firstFailedAt,
            LocalDateTime lastFailedAt,
            LocalDateTime resolvedAt,
            Long resolvedBy,
            String resolutionNote) {}

    public record Trace(
            String stage,
            String consumerName,
            int attemptNo,
            String detail,
            LocalDateTime occurredAt) {}

    public record Detail(Failure failure, List<Trace> traces) {}

    public record Backlog(
            long pendingMessages,
            long failedMessages,
            long pendingFailures,
            Long oldestPendingAgeSeconds) {}
}
