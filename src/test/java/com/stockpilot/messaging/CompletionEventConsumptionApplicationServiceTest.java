package com.stockpilot.messaging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockpilot.alert.service.LowStockEvaluationCommand;
import com.stockpilot.alert.service.LowStockEventApplicationService;
import com.stockpilot.messaging.domain.BusinessEventNames;
import com.stockpilot.messaging.domain.CompletionBusinessEvent;
import com.stockpilot.messaging.mapper.ConsumedMessageMapper;
import com.stockpilot.messaging.mapper.MessageTraceMapper;
import com.stockpilot.messaging.service.CompletionEventConsumptionApplicationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompletionEventConsumptionApplicationServiceTest {
    private final ConsumedMessageMapper consumedMessages = mock(ConsumedMessageMapper.class);
    private final MessageTraceMapper traces = mock(MessageTraceMapper.class);
    private final LowStockEventApplicationService lowStockEvents =
            mock(LowStockEventApplicationService.class);
    private final CompletionEventConsumptionApplicationService service =
            new CompletionEventConsumptionApplicationService(
                    consumedMessages, traces, lowStockEvents);
    private final CompletionBusinessEvent event =
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

    @Test
    void claimedMessageInvokesBusinessConsumer() {
        when(consumedMessages.claim(any(), any(), any(), any())).thenReturn(1);

        assertTrue(service.handle(event));

        verify(lowStockEvents)
                .evaluate(
                        new LowStockEvaluationCommand(
                                event.messageId(),
                                event.eventName(),
                                event.businessNo(),
                                event.data().warehouseId(),
                                List.of(
                                        new LowStockEvaluationCommand.InventoryDimension(
                                                20L, 30L))));
    }

    @Test
    void duplicateMessageSkipsBusinessConsumer() {
        when(consumedMessages.claim(any(), any(), any(), any())).thenReturn(0);

        assertFalse(service.handle(event));

        verify(lowStockEvents, never()).evaluate(any());
    }
}
