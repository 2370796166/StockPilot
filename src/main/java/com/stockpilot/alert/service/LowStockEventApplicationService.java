package com.stockpilot.alert.service;

import com.stockpilot.alert.domain.SafetyStockRuleEntity;
import com.stockpilot.alert.mapper.LowStockAlertMapper;
import com.stockpilot.alert.mapper.SafetyStockRuleMapper;
import com.stockpilot.inventory.service.InventoryQueryApplicationService;
import com.stockpilot.inventory.vo.InventoryBalanceVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LowStockEventApplicationService {
    private final SafetyStockRuleMapper rules;
    private final LowStockAlertMapper alerts;
    private final InventoryQueryApplicationService inventory;

    public LowStockEventApplicationService(
            SafetyStockRuleMapper rules,
            LowStockAlertMapper alerts,
            InventoryQueryApplicationService inventory) {
        this.rules = rules;
        this.alerts = alerts;
        this.inventory = inventory;
    }

    // 只负责安全库存业务判断；消息幂等、重试和轨迹由 messaging 模块编排。
    @Transactional(propagation = Propagation.MANDATORY)
    public void evaluate(LowStockEvaluationCommand command) {
        for (LowStockEvaluationCommand.InventoryDimension dimension : command.dimensions()) {
            SafetyStockRuleEntity rule =
                    rules.selectEnabledByDimension(
                            command.warehouseId(), dimension.locationId(), dimension.skuId());
            if (rule == null) continue;
            InventoryBalanceVO balance =
                    inventory
                            .findBalance(
                                    command.warehouseId(),
                                    dimension.locationId(),
                                    dimension.skuId())
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "Inventory balance missing for completed event"));
            if (balance.availableQuantity().compareTo(rule.getThresholdQuantity()) < 0) {
                alerts.open(
                        rule.getId(),
                        command.warehouseId(),
                        dimension.locationId(),
                        dimension.skuId(),
                        rule.getThresholdQuantity(),
                        balance.availableQuantity(),
                        command.messageId(),
                        command.eventName(),
                        command.businessNo());
            } else {
                alerts.resolve(
                        rule.getId(),
                        rule.getThresholdQuantity(),
                        balance.availableQuantity(),
                        command.messageId(),
                        command.eventName(),
                        command.businessNo());
            }
        }
    }
}
