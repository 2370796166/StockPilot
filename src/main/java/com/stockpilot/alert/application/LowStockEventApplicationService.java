package com.stockpilot.alert.application;

import com.stockpilot.alert.domain.SafetyStockRuleEntity;
import com.stockpilot.alert.infrastructure.mapper.LowStockAlertMapper;
import com.stockpilot.alert.infrastructure.mapper.SafetyStockRuleMapper;
import com.stockpilot.inventory.application.InventoryQueryApplicationService;
import com.stockpilot.inventory.vo.InventoryBalanceVO;
import com.stockpilot.messaging.domain.CompletionBusinessEvent;
import com.stockpilot.messaging.infrastructure.mapper.ConsumedMessageMapper;
import com.stockpilot.messaging.infrastructure.mapper.MessageTraceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LowStockEventApplicationService {
    public static final String CONSUMER_NAME = "low-stock-alert-v1";

    private final ConsumedMessageMapper consumedMessages;
    private final MessageTraceMapper traces;
    private final SafetyStockRuleMapper rules;
    private final LowStockAlertMapper alerts;
    private final InventoryQueryApplicationService inventory;

    public LowStockEventApplicationService(
            ConsumedMessageMapper consumedMessages, MessageTraceMapper traces,
            SafetyStockRuleMapper rules, LowStockAlertMapper alerts,
            InventoryQueryApplicationService inventory) {
        this.consumedMessages = consumedMessages;
        this.traces = traces;
        this.rules = rules;
        this.alerts = alerts;
        this.inventory = inventory;
    }

    @Transactional
    public boolean handle(CompletionBusinessEvent event) {
        if (consumedMessages.claim(event.messageId(), CONSUMER_NAME,
                event.eventName(), event.businessNo()) == 0) {
            traces.insert(event.messageId(), event.eventName(), event.businessNo(),
                    "DUPLICATE", CONSUMER_NAME, 0, "message already consumed");
            return false;
        }
        traces.insert(event.messageId(), event.eventName(), event.businessNo(),
                "CONSUME_STARTED", CONSUMER_NAME, 0, null);
        for (CompletionBusinessEvent.InventoryDimension dimension : event.data().dimensions()) {
            SafetyStockRuleEntity rule = rules.selectEnabledByDimension(
                    event.data().warehouseId(), dimension.locationId(), dimension.skuId());
            if (rule == null) continue;
            InventoryBalanceVO balance = inventory.findBalance(
                            event.data().warehouseId(), dimension.locationId(), dimension.skuId())
                    .orElseThrow(() -> new IllegalStateException("Inventory balance missing for completed event"));
            if (balance.availableQuantity().compareTo(rule.getThresholdQuantity()) < 0) {
                alerts.open(rule.getId(), event.data().warehouseId(), dimension.locationId(), dimension.skuId(),
                        rule.getThresholdQuantity(), balance.availableQuantity(), event.messageId(),
                        event.eventName(), event.businessNo());
            } else {
                alerts.resolve(rule.getId(), rule.getThresholdQuantity(), balance.availableQuantity(),
                        event.messageId(), event.eventName(), event.businessNo());
            }
        }
        traces.insert(event.messageId(), event.eventName(), event.businessNo(),
                "CONSUMED", CONSUMER_NAME, 0, "low-stock evaluation committed");
        return true;
    }
}
