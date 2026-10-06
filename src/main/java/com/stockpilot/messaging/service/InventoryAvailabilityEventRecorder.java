package com.stockpilot.messaging.service;

import com.stockpilot.inventory.domain.InventoryAvailabilityChanged;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryAvailabilityEventRecorder {
    private final TransactionalOutboxApplicationService transactionalOutboxService;

    public InventoryAvailabilityEventRecorder(
            TransactionalOutboxApplicationService transactionalOutboxService) {
        this.transactionalOutboxService = transactionalOutboxService;
    }

    // Synchronous listener: persist the event in the originating MySQL transaction, even with MQ
    // off.
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(InventoryAvailabilityChanged event) {
        transactionalOutboxService.enqueueAvailabilityChanged(event);
    }
}
