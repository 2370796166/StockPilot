package com.stockpilot.messaging.service;

import com.stockpilot.messaging.domain.OutboxMessageEntity;

public interface OutboxTransport {
    void publish(OutboxMessageEntity message) throws Exception;
}
