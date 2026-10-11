package com.stockpilot.messaging.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stockpilot.messaging.mapper.FailureRecordMapper;
import com.stockpilot.messaging.mapper.MessageTraceMapper;
import com.stockpilot.messaging.mapper.OutboxMessageMapper;
import com.stockpilot.messaging.request.MessageManagementRequests;
import com.stockpilot.messaging.vo.MessageManagementVO;
import com.stockpilot.security.audit.AuditService;
import com.stockpilot.shared.api.CommonErrorCode;
import com.stockpilot.shared.api.PageResult;
import com.stockpilot.shared.auth.AuthenticatedActor;
import com.stockpilot.shared.exception.BusinessException;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageManagementService {
    private final FailureRecordMapper failureRecordMapper;
    private final OutboxMessageMapper outboxMessageMapper;
    private final MessageTraceMapper messageTraceMapper;
    private final CompletionEventCodec completionEventCodec;
    private final CompletionEventConsumptionApplicationService completionEventConsumptionService;
    private final AuditService auditService;
    private final boolean messagingEnabled;

    public MessageManagementService(
            FailureRecordMapper failureRecordMapper,
            OutboxMessageMapper outboxMessageMapper,
            MessageTraceMapper messageTraceMapper,
            CompletionEventCodec completionEventCodec,
            CompletionEventConsumptionApplicationService completionEventConsumptionService,
            AuditService auditService,
            @Value("${stockpilot.messaging.enabled:false}") boolean messagingEnabled) {
        this.failureRecordMapper = failureRecordMapper;
        this.outboxMessageMapper = outboxMessageMapper;
        this.messageTraceMapper = messageTraceMapper;
        this.completionEventCodec = completionEventCodec;
        this.completionEventConsumptionService = completionEventConsumptionService;
        this.auditService = auditService;
        this.messagingEnabled = messagingEnabled;
    }

    @Transactional(readOnly = true)
    public PageResult<MessageManagementVO.Failure> page(
            long page, long size, String status, String businessNo) {
        com.stockpilot.shared.auth.WarehouseAccess.requireAll();
        if (page < 1
                || page > 10000
                || size < 1
                || size > 100
                || (status != null
                        && !Set.of("PENDING", "RETRY_REQUESTED", "RESOLVED").contains(status))
                || (businessNo != null && !businessNo.matches("[A-Za-z0-9_-]{2,64}")))
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER);
        var result = failureRecordMapper.page(Page.of(page, size), status, businessNo);
        return new PageResult<>(result.getRecords(), result.getTotal(), page, size);
    }

    @Transactional(readOnly = true)
    public MessageManagementVO.Detail detail(long id) {
        com.stockpilot.shared.auth.WarehouseAccess.requireAll();
        var failure = failureRecordMapper.find(id);
        if (failure == null) throw new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
        return new MessageManagementVO.Detail(
                failure, failureRecordMapper.traces(failure.messageId()));
    }

    @Transactional(readOnly = true)
    public MessageManagementVO.Backlog backlog() {
        return failureRecordMapper.backlog();
    }

    @Transactional
    public MessageManagementVO.Detail retry(
            long id, MessageManagementRequests.Action request, AuthenticatedActor actor) {
        com.stockpilot.shared.auth.WarehouseAccess.requireAll();
        Map<String, Object> record = requirePending(id, request);
        String messageId = String.valueOf(record.get("message_id"));
        com.stockpilot.messaging.domain.CompletionBusinessEvent event;
        try {
            event =
                    completionEventCodec.decodeAndValidate(
                            String.valueOf(record.get("payload_json")));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    CommonErrorCode.BUSINESS_CONFLICT, "原始事件格式或版本不支持重试，请人工核查并记录处理结论");
        }
        if (!messageId.equals(event.messageId())
                || !event.eventName().equals(record.get("event_name"))
                || !event.businessNo().equals(record.get("business_no"))
                || event.eventVersion() != ((Number) record.get("event_version")).intValue())
            throw new BusinessException(CommonErrorCode.BUSINESS_CONFLICT, "消息身份与原始事件不一致，不能重试");
        if ("PUBLISH".equals(record.get("failure_stage"))) {
            if (!messagingEnabled)
                throw new BusinessException(CommonErrorCode.BUSINESS_CONFLICT, "消息发布功能未启用，无法重试发布");
            var outbox = outboxMessageMapper.lock(messageId);
            if (outbox == null
                    || !"FAILED".equals(outbox.getStatus())
                    || !outbox.getPayloadJson().equals(String.valueOf(record.get("payload_json"))))
                throw new BusinessException(CommonErrorCode.BUSINESS_CONFLICT, "原始待发布消息不可重试");
            if (outboxMessageMapper.resetForManualRetry(messageId) != 1)
                throw new BusinessException(CommonErrorCode.BUSINESS_CONFLICT);
        } else if (!CompletionEventConsumptionApplicationService.CONSUMER_NAME.equals(
                record.get("consumer_name"))) {
            throw new BusinessException(CommonErrorCode.BUSINESS_CONFLICT, "不支持该消费者的人工重试");
        }
        if (failureRecordMapper.markRetryRequested(id, actor.userId(), request.note().trim()) != 1)
            throw new BusinessException(CommonErrorCode.BUSINESS_CONFLICT);
        if (messageTraceMapper.insert(
                        messageId,
                        event.eventName(),
                        event.businessNo(),
                        "MANUAL_RETRY",
                        String.valueOf(record.get("consumer_name")),
                        0,
                        "operatorId=" + actor.userId() + "; " + request.note().trim())
                != 1) throw new IllegalStateException("Cannot record manual retry");
        if ("CONSUME".equals(record.get("failure_stage"))) {
            completionEventConsumptionService.handle(event);
            if (failureRecordMapper.markResolved(id, actor.userId(), request.note().trim()) != 1)
                throw new IllegalStateException("Cannot resolve consumer retry");
        }
        auditService.record("MESSAGE_RETRY", "ASYNC_FAILURE", id, "SUCCESS", request.note().trim());
        return detail(id);
    }

    @Transactional
    public MessageManagementVO.Detail resolve(
            long id, MessageManagementRequests.Action request, AuthenticatedActor actor) {
        com.stockpilot.shared.auth.WarehouseAccess.requireAll();
        Map<String, Object> record = requirePending(id, request);
        if (failureRecordMapper.markResolved(id, actor.userId(), request.note().trim()) != 1)
            throw new BusinessException(CommonErrorCode.BUSINESS_CONFLICT);
        if (messageTraceMapper.insert(
                        String.valueOf(record.get("message_id")),
                        String.valueOf(record.get("event_name")),
                        String.valueOf(record.get("business_no")),
                        "RESOLVED",
                        String.valueOf(record.get("consumer_name")),
                        0,
                        "operatorId=" + actor.userId() + "; " + request.note().trim())
                != 1) throw new IllegalStateException("Cannot record manual resolution");
        auditService.record(
                "MESSAGE_RESOLVE", "ASYNC_FAILURE", id, "SUCCESS", request.note().trim());
        return detail(id);
    }

    private Map<String, Object> requirePending(long id, MessageManagementRequests.Action request) {
        Map<String, Object> record = failureRecordMapper.lock(id);
        if (record == null) throw new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
        if (!"PENDING".equals(record.get("status"))
                || ((Number) record.get("occurrence_count")).intValue()
                        != request.occurrenceCount())
            throw new BusinessException(CommonErrorCode.BUSINESS_CONFLICT, "失败记录已变化，请刷新后重试");
        return record;
    }
}
