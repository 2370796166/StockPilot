package com.stockpilot.messaging.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stockpilot.messaging")
public class MessagingProperties {
    private boolean enabled;
    private String exchange = "stockpilot.business.v1";
    private String completionQueue = "stockpilot.completion.low-stock.v1";
    private String deadLetterExchange = "stockpilot.dead-letter.v1";
    private String deadLetterQueue = "stockpilot.completion.low-stock.dlq.v1";
    private String deadLetterRoutingKey = "stockpilot.completion.low-stock.dead";
    private int publisherBatchSize = 20;
    private int publisherMaxAttempts = 8;
    private Duration publisherConfirmTimeout = Duration.ofSeconds(5);
    private Duration publisherFixedDelay = Duration.ofSeconds(2);
    private int consumerMaxAttempts = 3;
    private Duration consumerInitialBackoff = Duration.ofMillis(500);
    private Duration consumerMaxBackoff = Duration.ofSeconds(5);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getCompletionQueue() {
        return completionQueue;
    }

    public void setCompletionQueue(String completionQueue) {
        this.completionQueue = completionQueue;
    }

    public String getDeadLetterExchange() {
        return deadLetterExchange;
    }

    public void setDeadLetterExchange(String deadLetterExchange) {
        this.deadLetterExchange = deadLetterExchange;
    }

    public String getDeadLetterQueue() {
        return deadLetterQueue;
    }

    public void setDeadLetterQueue(String deadLetterQueue) {
        this.deadLetterQueue = deadLetterQueue;
    }

    public String getDeadLetterRoutingKey() {
        return deadLetterRoutingKey;
    }

    public void setDeadLetterRoutingKey(String deadLetterRoutingKey) {
        this.deadLetterRoutingKey = deadLetterRoutingKey;
    }

    public int getPublisherBatchSize() {
        return publisherBatchSize;
    }

    public void setPublisherBatchSize(int publisherBatchSize) {
        this.publisherBatchSize = publisherBatchSize;
    }

    public int getPublisherMaxAttempts() {
        return publisherMaxAttempts;
    }

    public void setPublisherMaxAttempts(int publisherMaxAttempts) {
        this.publisherMaxAttempts = publisherMaxAttempts;
    }

    public Duration getPublisherConfirmTimeout() {
        return publisherConfirmTimeout;
    }

    public void setPublisherConfirmTimeout(Duration publisherConfirmTimeout) {
        this.publisherConfirmTimeout = publisherConfirmTimeout;
    }

    public Duration getPublisherFixedDelay() {
        return publisherFixedDelay;
    }

    public void setPublisherFixedDelay(Duration publisherFixedDelay) {
        this.publisherFixedDelay = publisherFixedDelay;
    }

    public int getConsumerMaxAttempts() {
        return consumerMaxAttempts;
    }

    public void setConsumerMaxAttempts(int consumerMaxAttempts) {
        this.consumerMaxAttempts = consumerMaxAttempts;
    }

    public Duration getConsumerInitialBackoff() {
        return consumerInitialBackoff;
    }

    public void setConsumerInitialBackoff(Duration consumerInitialBackoff) {
        this.consumerInitialBackoff = consumerInitialBackoff;
    }

    public Duration getConsumerMaxBackoff() {
        return consumerMaxBackoff;
    }

    public void setConsumerMaxBackoff(Duration consumerMaxBackoff) {
        this.consumerMaxBackoff = consumerMaxBackoff;
    }
}
