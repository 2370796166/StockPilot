package com.stockpilot.alert;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockpilot.alert.domain.SafetyStockRuleEntity;
import com.stockpilot.alert.mapper.LowStockAlertMapper;
import com.stockpilot.alert.mapper.SafetyStockRuleMapper;
import com.stockpilot.alert.service.LowStockEventApplicationService;
import com.stockpilot.inventory.service.InventoryQueryApplicationService;
import com.stockpilot.inventory.vo.InventoryBalanceVO;
import com.stockpilot.messaging.domain.BusinessEventNames;
import com.stockpilot.messaging.domain.CompletionBusinessEvent;
import com.stockpilot.messaging.mapper.ConsumedMessageMapper;
import com.stockpilot.messaging.mapper.MessageTraceMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LowStockEventApplicationServiceTest {
    @Test
    void belowThresholdOpensAlertUsingLatestMysqlBalance() {
        Fixture fixture = new Fixture("4.0000", "5.0000");
        assertTrue(fixture.service.handle(fixture.event));
        verify(fixture.alerts)
                .open(
                        5L,
                        10L,
                        20L,
                        30L,
                        new BigDecimal("5.0000"),
                        new BigDecimal("4.0000"),
                        fixture.event.messageId(),
                        fixture.event.eventName(),
                        fixture.event.businessNo());
    }

    @Test
    void recoveredBalanceResolvesExistingAlert() {
        Fixture fixture = new Fixture("5.0000", "5.0000");
        assertTrue(fixture.service.handle(fixture.event));
        verify(fixture.alerts)
                .resolve(
                        5L,
                        new BigDecimal("5.0000"),
                        new BigDecimal("5.0000"),
                        fixture.event.messageId(),
                        fixture.event.eventName(),
                        fixture.event.businessNo());
    }

    @Test
    void duplicateMessageSkipsAllBusinessSideEffects() {
        Fixture fixture = new Fixture("4.0000", "5.0000");
        when(fixture.consumed.claim(any(), any(), any(), any())).thenReturn(0);
        assertFalse(fixture.service.handle(fixture.event));
        verify(fixture.rules, never()).selectEnabledByDimension(10L, 20L, 30L);
        verify(fixture.alerts, never())
                .open(
                        any(Long.class),
                        any(Long.class),
                        any(Long.class),
                        any(Long.class),
                        any(),
                        any(),
                        any(),
                        any(),
                        any());
    }

    private static final class Fixture {
        final ConsumedMessageMapper consumed = mock(ConsumedMessageMapper.class);
        final MessageTraceMapper traces = mock(MessageTraceMapper.class);
        final SafetyStockRuleMapper rules = mock(SafetyStockRuleMapper.class);
        final LowStockAlertMapper alerts = mock(LowStockAlertMapper.class);
        final InventoryQueryApplicationService inventory =
                mock(InventoryQueryApplicationService.class);
        final CompletionBusinessEvent event =
                new CompletionBusinessEvent(
                        "7fdf128b-3b31-4776-9458-a61f44df70ac",
                        BusinessEventNames.SALES_OUTBOUND_COMPLETED,
                        1,
                        "SO-1",
                        Instant.parse("2026-08-18T00:00:00Z"),
                        new CompletionBusinessEvent.CompletionData(
                                1L,
                                10L,
                                List.of(new CompletionBusinessEvent.InventoryDimension(20L, 30L))));
        final LowStockEventApplicationService service;

        Fixture(String available, String threshold) {
            when(consumed.claim(any(), any(), any(), any())).thenReturn(1);
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
            service =
                    new LowStockEventApplicationService(consumed, traces, rules, alerts, inventory);
        }
    }
}
