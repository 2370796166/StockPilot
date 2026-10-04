package com.stockpilot.alert;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockpilot.alert.domain.SafetyStockRuleEntity;
import com.stockpilot.alert.mapper.LowStockAlertMapper;
import com.stockpilot.alert.mapper.SafetyStockRuleMapper;
import com.stockpilot.alert.service.LowStockEvaluationCommand;
import com.stockpilot.alert.service.LowStockEventApplicationService;
import com.stockpilot.inventory.service.InventoryQueryApplicationService;
import com.stockpilot.inventory.vo.InventoryBalanceVO;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LowStockEventApplicationServiceTest {
    @Test
    void belowThresholdOpensAlertUsingLatestMysqlBalance() {
        Fixture fixture = new Fixture("4.0000", "5.0000");
        fixture.service.evaluate(fixture.command);
        verify(fixture.alerts)
                .open(
                        5L,
                        10L,
                        20L,
                        30L,
                        new BigDecimal("5.0000"),
                        new BigDecimal("4.0000"),
                        fixture.command.messageId(),
                        fixture.command.eventName(),
                        fixture.command.businessNo());
    }

    @Test
    void recoveredBalanceResolvesExistingAlert() {
        Fixture fixture = new Fixture("5.0000", "5.0000");
        fixture.service.evaluate(fixture.command);
        verify(fixture.alerts)
                .resolve(
                        5L,
                        new BigDecimal("5.0000"),
                        new BigDecimal("5.0000"),
                        fixture.command.messageId(),
                        fixture.command.eventName(),
                        fixture.command.businessNo());
    }

    private static final class Fixture {
        final SafetyStockRuleMapper rules = mock(SafetyStockRuleMapper.class);
        final LowStockAlertMapper alerts = mock(LowStockAlertMapper.class);
        final InventoryQueryApplicationService inventory =
                mock(InventoryQueryApplicationService.class);
        final LowStockEvaluationCommand command =
                new LowStockEvaluationCommand(
                        "7fdf128b-3b31-4776-9458-a61f44df70ac",
                        "stockpilot.sales-outbound.completed",
                        "SO-1",
                        10L,
                        List.of(new LowStockEvaluationCommand.InventoryDimension(20L, 30L)));
        final LowStockEventApplicationService service;

        Fixture(String available, String threshold) {
            SafetyStockRuleEntity rule = new SafetyStockRuleEntity();
            rule.setId(5L);
            rule.setThresholdQuantity(new BigDecimal(threshold));
            when(rules.selectEnabledByDimension(10L, 20L, 30L)).thenReturn(rule);
            when(inventory.findBalance(10L, 20L, 30L))
                    .thenReturn(
                            Optional.of(
                                    new InventoryBalanceVO(
                                            1L,
                                            10L,
                                            20L,
                                            30L,
                                            new BigDecimal(available),
                                            new BigDecimal(available),
                                            BigDecimal.ZERO.setScale(4),
                                            1,
                                            null,
                                            null)));
            service = new LowStockEventApplicationService(rules, alerts, inventory);
        }
    }
}
