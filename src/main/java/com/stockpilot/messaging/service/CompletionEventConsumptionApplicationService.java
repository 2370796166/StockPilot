package com.stockpilot.messaging.service;

import com.stockpilot.alert.service.LowStockEvaluationCommand;
import com.stockpilot.alert.service.LowStockEventApplicationService;
import com.stockpilot.messaging.domain.CompletionBusinessEvent;
import com.stockpilot.messaging.mapper.ConsumedMessageMapper;
import com.stockpilot.messaging.mapper.MessageTraceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompletionEventConsumptionApplicationService {
    public static final String CONSUMER_NAME = "low-stock-alert-v1";

    private final ConsumedMessageMapper consumedMessages;
    private final MessageTraceMapper traces;
    private final LowStockEventApplicationService lowStockEvents;

    public CompletionEventConsumptionApplicationService(
            ConsumedMessageMapper consumedMessages,
            MessageTraceMapper traces,
            LowStockEventApplicationService lowStockEvents) {
        this.consumedMessages = consumedMessages;
        this.traces = traces;
        this.lowStockEvents = lowStockEvents;
    }

    // 消息幂等记录、预警变化和消费轨迹必须在同一事务提交。
    @Transactional
    public boolean handle(CompletionBusinessEvent event) {
        if (consumedMessages.claim(
                        event.messageId(), CONSUMER_NAME, event.eventName(), event.businessNo())
                == 0) {
            traces.insert(
                    event.messageId(),
                    event.eventName(),
                    event.businessNo(),
                    "DUPLICATE",
                    CONSUMER_NAME,
                    0,
                    "message already consumed");
            return false;
        }
        traces.insert(
                event.messageId(),
                event.eventName(),
                event.businessNo(),
                "CONSUME_STARTED",
                CONSUMER_NAME,
                0,
                null);
        lowStockEvents.evaluate(toCommand(event));
        traces.insert(
                event.messageId(),
                event.eventName(),
                event.businessNo(),
                "CONSUMED",
                CONSUMER_NAME,
                0,
                "low-stock evaluation committed");
        return true;
    }

    private LowStockEvaluationCommand toCommand(CompletionBusinessEvent event) {
        return new LowStockEvaluationCommand(
                event.messageId(),
                event.eventName(),
                event.businessNo(),
                event.data().warehouseId(),
                event.data().dimensions().stream()
                        .map(
                                dimension ->
                                        new LowStockEvaluationCommand.InventoryDimension(
                                                dimension.locationId(), dimension.skuId()))
                        .toList());
    }
}
