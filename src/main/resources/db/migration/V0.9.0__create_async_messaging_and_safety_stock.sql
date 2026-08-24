CREATE TABLE safety_stock_rule (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    warehouse_id        BIGINT NOT NULL,
    location_id         BIGINT NOT NULL,
    sku_id              BIGINT NOT NULL,
    threshold_quantity  DECIMAL(19,4) NOT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version             INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_safety_stock_rule_dimension (warehouse_id, location_id, sku_id),
    CONSTRAINT fk_safety_stock_rule_location FOREIGN KEY (location_id, warehouse_id)
        REFERENCES warehouse_location (id, warehouse_id),
    CONSTRAINT fk_safety_stock_rule_sku FOREIGN KEY (sku_id) REFERENCES sku (id),
    CONSTRAINT ck_safety_stock_rule_threshold CHECK (threshold_quantity >= 0),
    CONSTRAINT ck_safety_stock_rule_status CHECK (status IN ('ENABLED','DISABLED'))
) ENGINE=InnoDB COMMENT='安全库存阈值规则';

CREATE TABLE low_stock_alert (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    rule_id             BIGINT NOT NULL,
    warehouse_id        BIGINT NOT NULL,
    location_id         BIGINT NOT NULL,
    sku_id              BIGINT NOT NULL,
    threshold_quantity  DECIMAL(19,4) NOT NULL,
    available_quantity  DECIMAL(19,4) NOT NULL,
    status              VARCHAR(16) NOT NULL,
    source_message_id   CHAR(36) NOT NULL,
    source_event_name   VARCHAR(100) NOT NULL,
    business_no         VARCHAR(64) NOT NULL,
    first_triggered_at  DATETIME(3) NOT NULL,
    last_evaluated_at   DATETIME(3) NOT NULL,
    resolved_at         DATETIME(3) NULL,
    version             INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_low_stock_alert_rule (rule_id),
    KEY idx_low_stock_alert_status_time (status, last_evaluated_at),
    CONSTRAINT fk_low_stock_alert_rule FOREIGN KEY (rule_id) REFERENCES safety_stock_rule (id),
    CONSTRAINT ck_low_stock_alert_quantities CHECK (threshold_quantity >= 0 AND available_quantity >= 0),
    CONSTRAINT ck_low_stock_alert_status CHECK (status IN ('OPEN','RESOLVED'))
) ENGINE=InnoDB COMMENT='当前低库存预警状态';

CREATE TABLE async_outbox_message (
    message_id          CHAR(36) NOT NULL,
    event_name          VARCHAR(100) NOT NULL,
    event_version       INT UNSIGNED NOT NULL,
    business_no         VARCHAR(64) NOT NULL,
    routing_key         VARCHAR(100) NOT NULL,
    payload_json        JSON NOT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    publish_attempts    INT UNSIGNED NOT NULL DEFAULT 0,
    next_attempt_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_error          VARCHAR(1000) NULL,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    published_at        DATETIME(3) NULL,
    PRIMARY KEY (message_id),
    KEY idx_async_outbox_due (status, next_attempt_at, created_at),
    CONSTRAINT ck_async_outbox_version CHECK (event_version > 0),
    CONSTRAINT ck_async_outbox_status CHECK (status IN ('PENDING','PUBLISHED','FAILED'))
) ENGINE=InnoDB COMMENT='事务性消息Outbox';

CREATE TABLE async_consumed_message (
    message_id          CHAR(36) NOT NULL,
    consumer_name       VARCHAR(100) NOT NULL,
    event_name          VARCHAR(100) NOT NULL,
    business_no         VARCHAR(64) NOT NULL,
    consumed_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (message_id, consumer_name)
) ENGINE=InnoDB COMMENT='消息消费幂等记录';

CREATE TABLE async_message_trace (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    message_id          CHAR(36) NOT NULL,
    event_name          VARCHAR(100) NOT NULL,
    business_no         VARCHAR(64) NOT NULL,
    stage               VARCHAR(32) NOT NULL,
    consumer_name       VARCHAR(100) NULL,
    attempt_no          INT UNSIGNED NOT NULL DEFAULT 0,
    detail              VARCHAR(1000) NULL,
    occurred_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_async_trace_message_time (message_id, occurred_at),
    KEY idx_async_trace_business (business_no, occurred_at),
    CONSTRAINT ck_async_trace_stage CHECK (stage IN (
        'OUTBOX_CREATED','PUBLISH_ATTEMPT','PUBLISHED','PUBLISH_RETRY','PUBLISH_FAILED',
        'CONSUME_STARTED','CONSUME_RETRY','CONSUMED','DUPLICATE','DEAD_LETTERED','MANUAL_RETRY','RESOLVED'))
) ENGINE=InnoDB COMMENT='异步消息追踪日志';

CREATE TABLE async_failure_record (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    message_id          CHAR(36) NOT NULL,
    event_name          VARCHAR(100) NOT NULL,
    event_version       INT UNSIGNED NOT NULL,
    business_no         VARCHAR(64) NOT NULL,
    failure_stage       VARCHAR(16) NOT NULL,
    consumer_name       VARCHAR(100) NULL,
    payload_json        JSON NOT NULL,
    failure_reason      VARCHAR(2000) NOT NULL,
    status              VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    occurrence_count    INT UNSIGNED NOT NULL DEFAULT 1,
    first_failed_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_failed_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    resolved_at         DATETIME(3) NULL,
    resolved_by         BIGINT NULL,
    resolution_note     VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_async_failure_message_stage (message_id, failure_stage, consumer_name),
    KEY idx_async_failure_status_time (status, last_failed_at),
    CONSTRAINT ck_async_failure_stage CHECK (failure_stage IN ('PUBLISH','CONSUME')),
    CONSTRAINT ck_async_failure_status CHECK (status IN ('PENDING','RETRY_REQUESTED','RESOLVED'))
) ENGINE=InnoDB COMMENT='异步永久失败与人工补偿记录';
