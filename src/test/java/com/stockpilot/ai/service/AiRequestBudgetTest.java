package com.stockpilot.ai.service;

import static org.junit.jupiter.api.Assertions.*;

import com.stockpilot.ai.infrastructure.AiModelAdapter.ModelFailure;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class AiRequestBudgetTest {
    @Test
    void roundsShareOneMonotonicDeadlineRatherThanResettingTheTimeout() {
        AtomicLong clock = new AtomicLong(100);
        AiRequestBudget budget = new AiRequestBudget(Duration.ofSeconds(2), clock::get);
        assertEquals(Duration.ofSeconds(2), budget.remaining());
        clock.addAndGet(1500000000L);
        assertEquals(Duration.ofMillis(500), budget.remaining());
        clock.addAndGet(500000000L);
        assertEquals(
                "QUESTION_TIMEOUT", assertThrows(ModelFailure.class, budget::remaining).status());
    }

    @Test
    void interruptedRequestStopsWithoutClearingTheInterrupt() {
        AiRequestBudget budget = new AiRequestBudget(Duration.ofSeconds(1), System::nanoTime);
        Thread.currentThread().interrupt();
        try {
            assertEquals(
                    "REQUEST_CANCELLED",
                    assertThrows(ModelFailure.class, budget::remaining).status());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
