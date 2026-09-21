package com.stockpilot.messaging.service;

import com.stockpilot.messaging.config.MessagingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "stockpilot.messaging", name = "enabled", havingValue = "true")
public class OutboxPublisherScheduler {
    private final OutboxPublicationApplicationService publications;
    private final MessagingProperties properties;

    public OutboxPublisherScheduler(
            OutboxPublicationApplicationService publications, MessagingProperties properties) {
        this.publications = publications;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${stockpilot.messaging.publisher-fixed-delay:2000}")
    public void publishDueMessages() {
        for (int index = 0; index < properties.getPublisherBatchSize(); index++) {
            if (!publications.publishNextDue()) return;
        }
    }
}
