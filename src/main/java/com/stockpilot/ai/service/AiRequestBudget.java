package com.stockpilot.ai.service;

import com.stockpilot.ai.infrastructure.AiModelAdapter.ModelFailure;
import java.time.Duration;
import java.util.function.LongSupplier;

/** One monotonic deadline shared by every model round and tool checkpoint. */
final class AiRequestBudget {
    private final LongSupplier clock;
    private final long started;
    private final long timeoutNanos;

    AiRequestBudget(Duration timeout, LongSupplier clock) {
        this.clock = clock;
        this.started = clock.getAsLong();
        this.timeoutNanos = timeout.toNanos();
    }

    Duration remaining() {
        if (Thread.currentThread().isInterrupted()) throw new ModelFailure("REQUEST_CANCELLED");
        long remaining = timeoutNanos - (clock.getAsLong() - started);
        if (remaining < 1000000) throw new ModelFailure("QUESTION_TIMEOUT");
        return Duration.ofNanos(remaining);
    }
}
