package com.stockpilot.messaging.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.messaging.domain.BusinessEventNames;
import com.stockpilot.messaging.domain.CompletionBusinessEvent;
import com.stockpilot.messaging.domain.OutboxMessageEntity;
import com.stockpilot.messaging.infrastructure.mapper.MessageTraceMapper;
import com.stockpilot.messaging.infrastructure.mapper.OutboxMessageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

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

    @Transactional(propagation = Propagation.MANDATORY)
    public String enqueuePurchaseReceiptCompleted(
            long documentId, String businessNo, long warehouseId,
            List<CompletionBusinessEvent.InventoryDimension> dimensions) {
        return enqueue(BusinessEventNames.PURCHASE_RECEIPT_COMPLETED,
                BusinessEventNames.PURCHASE_RECEIPT_ROUTING_KEY,
                documentId, businessNo, warehouseId, dimensions);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String enqueueSalesOutboundCompleted(
            long documentId, String businessNo, long warehouseId,
            List<CompletionBusinessEvent.InventoryDimension> dimensions) {
        return enqueue(BusinessEventNames.SALES_OUTBOUND_COMPLETED,
                BusinessEventNames.SALES_OUTBOUND_ROUTING_KEY,
                documentId, businessNo, warehouseId, dimensions);
    }

    private String enqueue(String eventName, String routingKey, long documentId, String businessNo,
                           long warehouseId, List<CompletionBusinessEvent.InventoryDimension> dimensions) {
        String messageId = UUID.randomUUID().toString();
        List<CompletionBusinessEvent.InventoryDimension> normalized = dimensions.stream()
                .distinct()
                .sorted(Comparator.comparingLong(CompletionBusinessEvent.InventoryDimension::locationId)
                        .thenComparingLong(CompletionBusinessEvent.InventoryDimension::skuId))
                .toList();
        CompletionBusinessEvent event = new CompletionBusinessEvent(
                messageId, eventName, BusinessEventNames.VERSION_1, businessNo, Instant.now(),
                new CompletionBusinessEvent.CompletionData(documentId, warehouseId, normalized));
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
        traces.insert(messageId, eventName, businessNo, "OUTBOX_CREATED", null, 0,
                "business transaction recorded event");
        return messageId;
    }
}
