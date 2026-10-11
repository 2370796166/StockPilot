package com.stockpilot.operations.vo;

import com.stockpilot.messaging.vo.MessageManagementVO;
import java.time.Instant;

public record OperationsStatusVO(
        Instant observedAt,
        boolean messagingEnabled,
        boolean cacheEnabled,
        int activeConnections,
        int idleConnections,
        int waitingConnections,
        long observedRequests,
        double totalRequestSeconds,
        long heapUsedBytes,
        MessageManagementVO.Backlog backlog) {}
