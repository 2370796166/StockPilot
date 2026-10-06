ALTER TABLE inventory_count_order
    ADD COLUMN cancelled_by BIGINT NULL,
    ADD COLUMN cancelled_by_name VARCHAR(64) NULL,
    ADD COLUMN cancelled_at DATETIME(3) NULL,
    ADD COLUMN cancel_reason VARCHAR(255) NULL,
    DROP CHECK ck_inventory_count_status,
    ADD CONSTRAINT ck_inventory_count_status
        CHECK (status IN ('DRAFT','COUNTING','SUBMITTED','APPROVED','ADJUSTED','CANCELLED')),
    ADD CONSTRAINT ck_inventory_count_cancellation
        CHECK ((status = 'CANCELLED' AND cancelled_by IS NOT NULL AND cancelled_by > 0
                AND cancelled_by_name IS NOT NULL AND CHAR_LENGTH(TRIM(cancelled_by_name)) > 0
                AND cancelled_at IS NOT NULL AND cancel_reason IS NOT NULL
                AND CHAR_LENGTH(TRIM(cancel_reason)) > 0)
            OR (status <> 'CANCELLED' AND cancelled_by IS NULL AND cancelled_by_name IS NULL
                AND cancelled_at IS NULL AND cancel_reason IS NULL));
