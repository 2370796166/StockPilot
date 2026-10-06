package com.stockpilot.messaging.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.messaging.domain.BusinessEventNames;
import com.stockpilot.messaging.domain.CompletionBusinessEvent;
import com.stockpilot.messaging.domain.OutboxMessageEntity;
import com.stockpilot.messaging.mapper.MessageTraceMapper;
import com.stockpilot.messaging.mapper.OutboxMessageMapper;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionalOutboxApplicationService {
    private final OutboxMessageMapper outbox;
    private final MessageTraceMapper traces;
    private final ObjectMapper objectMapper;

    public TransactionalOutboxApplicationService(
            OutboxMessageMapper outbox, MessageTraceMapper traces, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.traces = traces;
        this.objectMapper = objectMapper;
    }

    // 记录采购入库完成事件：仅保存业务单号、仓库和受影响维度，不传输完整数据库实体。
    // 强制加入采购入库事务，防止业务回滚后仍遗留一条虚假的完成事件。
    @Transactional(propagation = Propagation.MANDATORY)
    public String enqueuePurchaseReceiptCompleted(
            long documentId,
            String businessNo,
            long warehouseId,
            List<CompletionBusinessEvent.InventoryDimension> dimensions) {
        return enqueue(
                BusinessEventNames.PURCHASE_RECEIPT_COMPLETED,
                BusinessEventNames.PURCHASE_RECEIPT_ROUTING_KEY,
                documentId,
                businessNo,
                warehouseId,
                dimensions);
    }

    // 记录销售出库完成事件，用于事务提交后异步触发安全库存检查。
    // 强制加入销售出库事务，使库存扣减、单据完成和事件落库保持原子性。
    @Transactional(propagation = Propagation.MANDATORY)
    public String enqueueSalesOutboundCompleted(
            long documentId,
            String businessNo,
            long warehouseId,
            List<CompletionBusinessEvent.InventoryDimension> dimensions) {
        return enqueue(
                BusinessEventNames.SALES_OUTBOUND_COMPLETED,
                BusinessEventNames.SALES_OUTBOUND_ROUTING_KEY,
                documentId,
                businessNo,
                warehouseId,
                dimensions);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String enqueueAvailabilityChanged(
            com.stockpilot.inventory.domain.InventoryAvailabilityChanged event) {
        if (event.documentId() <= 0
                || event.warehouseId() <= 0
                || event.businessNo() == null
                || event.businessNo().isBlank()
                || event.dimensions().isEmpty())
            throw new IllegalArgumentException("Incomplete inventory availability event");
        return enqueue(
                BusinessEventNames.availabilityEventName(event.action()),
                BusinessEventNames.AVAILABILITY_ROUTING_KEY,
                event.documentId(),
                event.businessNo(),
                event.warehouseId(),
                event.dimensions().stream()
                        .map(
                                d ->
                                        new CompletionBusinessEvent.InventoryDimension(
                                                d.locationId(), d.skuId()))
                        .toList());
    }

    // 生成全局唯一消息 ID，去重并稳定排序库存维度，然后同时写入 Outbox 和创建轨迹。
    private String enqueue(
            String eventName,
            String routingKey,
            long documentId,
            String businessNo,
            long warehouseId,
            List<CompletionBusinessEvent.InventoryDimension> dimensions) {
        String messageId = UUID.randomUUID().toString();
        List<CompletionBusinessEvent.InventoryDimension> normalized =
                dimensions.stream()
                        .distinct()
                        .sorted(
                                Comparator.comparingLong(
                                                CompletionBusinessEvent.InventoryDimension
                                                        ::locationId)
                                        .thenComparingLong(
                                                CompletionBusinessEvent.InventoryDimension::skuId))
                        .toList();
        CompletionBusinessEvent event =
                new CompletionBusinessEvent(
                        messageId,
                        eventName,
                        BusinessEventNames.VERSION_1,
                        businessNo,
                        Instant.now(),
                        new CompletionBusinessEvent.CompletionData(
                                documentId, warehouseId, normalized));
        OutboxMessageEntity message = new OutboxMessageEntity();
        message.setMessageId(messageId);
        message.setEventName(eventName);
        message.setEventVersion(BusinessEventNames.VERSION_1);
        message.setBusinessNo(businessNo);
        message.setRoutingKey(routingKey);
        try {
            message.setPayloadJson(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize completion event", exception);
        }
        if (outbox.insert(message) != 1) {
            throw new IllegalStateException("Cannot persist completion event outbox message");
        }
        traces.insert(
                messageId,
                eventName,
                businessNo,
                "OUTBOX_CREATED",
                null,
                0,
                "business transaction recorded event");
        return messageId;
    }
}
