ALTER TABLE purchase_receipt ADD COLUMN supplier_id BIGINT NULL,
    ADD COLUMN supplier_name VARCHAR(100) NULL,
    ADD CONSTRAINT fk_purchase_supplier FOREIGN KEY(supplier_id) REFERENCES supplier(id),
    DROP CHECK ck_purchase_receipt_status;
ALTER TABLE purchase_receipt ADD CONSTRAINT ck_purchase_receipt_status
    CHECK(status IN ('DRAFT','SUBMITTED','APPROVED','COMPLETED','CANCELLED'));

CREATE TABLE purchase_receipt_action (
    id BIGINT NOT NULL AUTO_INCREMENT, receipt_id BIGINT NOT NULL,
    action VARCHAR(16) NOT NULL, from_status VARCHAR(16) NOT NULL,to_status VARCHAR(16) NOT NULL,
    reason VARCHAR(255) NOT NULL, operator_id BIGINT NOT NULL,operator_name VARCHAR(64) NOT NULL,
    document_version INT UNSIGNED NOT NULL, evidence_json JSON NOT NULL,
    occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),PRIMARY KEY(id),
    UNIQUE KEY uk_purchase_action_version(receipt_id,document_version),
    CONSTRAINT fk_purchase_action FOREIGN KEY(receipt_id) REFERENCES purchase_receipt(id),
    CONSTRAINT ck_purchase_action CHECK(action IN ('RETURN','CANCEL') AND CHAR_LENGTH(TRIM(reason))>0)
) ENGINE=InnoDB COMMENT='采购退回取消的不可变证据';

CREATE TABLE inventory_count_action (
    id BIGINT NOT NULL AUTO_INCREMENT, count_id BIGINT NOT NULL,
    action VARCHAR(16) NOT NULL, from_status VARCHAR(16) NOT NULL,to_status VARCHAR(16) NOT NULL,
    reason VARCHAR(255) NOT NULL,operator_id BIGINT NOT NULL,operator_name VARCHAR(64) NOT NULL,
    document_version INT UNSIGNED NOT NULL,evidence_json JSON NOT NULL,
    occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),PRIMARY KEY(id),
    UNIQUE KEY uk_count_action_version(count_id,document_version),
    CONSTRAINT fk_count_action FOREIGN KEY(count_id) REFERENCES inventory_count_order(id),
    CONSTRAINT ck_count_action CHECK(action='RETURN' AND CHAR_LENGTH(TRIM(reason))>0)
) ENGINE=InnoDB COMMENT='盘点退回的不可变实盘证据';
