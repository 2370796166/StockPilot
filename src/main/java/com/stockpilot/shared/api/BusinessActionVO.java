package com.stockpilot.shared.api;

import java.time.LocalDateTime;

public record BusinessActionVO(
        String action,
        String fromStatus,
        String toStatus,
        String reason,
        long operatorId,
        String operatorName,
        int documentVersion,
        String evidenceJson,
        LocalDateTime occurredAt) {}
