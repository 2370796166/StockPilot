CREATE TABLE inventory_count_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    count_no VARCHAR(64) NOT NULL,
    warehouse_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    remark VARCHAR(255) NULL,
    created_by BIGINT NOT NULL,
    created_by_name VARCHAR(64) NOT NULL,
    counting_by BIGINT NULL,
    counting_by_name VARCHAR(64) NULL,
    counting_at DATETIME(3) NULL,
    submitted_by BIGINT NULL,
    submitted_by_name VARCHAR(64) NULL,
    submitted_at DATETIME(3) NULL,
    approved_by BIGINT NULL,
    approved_by_name VARCHAR(64) NULL,
    approved_at DATETIME(3) NULL,
    adjusted_by BIGINT NULL,
    adjusted_by_name VARCHAR(64) NULL,
    adjusted_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_count_no (count_no),
    UNIQUE KEY uk_inventory_count_header_warehouse (id, warehouse_id),
    KEY idx_inventory_count_status (status, id),
    KEY idx_inventory_count_warehouse (warehouse_id, status, id),
    CONSTRAINT fk_inventory_count_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id),
    CONSTRAINT ck_inventory_count_status CHECK (status IN ('DRAFT','COUNTING','SUBMITTED','APPROVED','ADJUSTED'))
) ENGINE=InnoDB COMMENT='静态库存盘点单';

CREATE TABLE inventory_count_line (
    id BIGINT NOT NULL AUTO_INCREMENT,
    count_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    line_no INT UNSIGNED NOT NULL,
    location_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    snapshot_actual_quantity DECIMAL(19,4) NOT NULL,
    snapshot_available_quantity DECIMAL(19,4) NOT NULL,
    snapshot_frozen_quantity DECIMAL(19,4) NOT NULL,
    snapshot_balance_version INT UNSIGNED NOT NULL,
    counted_quantity DECIMAL(19,4) NULL,
    difference_quantity DECIMAL(19,4) NULL,
    reason VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_count_line_no (count_id, line_no),
    UNIQUE KEY uk_inventory_count_line_dimension (count_id, location_id, sku_id),
    UNIQUE KEY uk_inventory_count_line_id_header (id, count_id),
    KEY idx_inventory_count_line_sku (sku_id),
    CONSTRAINT fk_inventory_count_line_header
        FOREIGN KEY (count_id, warehouse_id) REFERENCES inventory_count_order (id, warehouse_id),
    CONSTRAINT fk_inventory_count_line_location
        FOREIGN KEY (location_id, warehouse_id) REFERENCES warehouse_location (id, warehouse_id),
    CONSTRAINT fk_inventory_count_line_sku FOREIGN KEY (sku_id) REFERENCES sku (id),
    CONSTRAINT ck_inventory_count_line_snapshot CHECK (
        line_no > 0
        AND snapshot_actual_quantity >= 0
        AND snapshot_available_quantity >= 0
        AND snapshot_frozen_quantity >= 0
        AND snapshot_actual_quantity = snapshot_available_quantity + snapshot_frozen_quantity
    ),
    CONSTRAINT ck_inventory_count_line_result CHECK (
        (counted_quantity IS NULL AND difference_quantity IS NULL AND reason IS NULL)
        OR (counted_quantity >= 0
            AND difference_quantity = counted_quantity - snapshot_actual_quantity
            AND reason IS NOT NULL AND CHAR_LENGTH(TRIM(reason)) > 0)
    )
) ENGINE=InnoDB COMMENT='库存盘点快照和实盘结果';

CREATE TABLE inventory_count_scope_lock (
    id BIGINT NOT NULL AUTO_INCREMENT,
    count_id BIGINT NOT NULL,
    count_line_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_count_scope_dimension (warehouse_id, location_id, sku_id),
    UNIQUE KEY uk_inventory_count_scope_line (count_line_id),
    KEY idx_inventory_count_scope_header (count_id),
    CONSTRAINT fk_inventory_count_scope_line
        FOREIGN KEY (count_line_id, count_id) REFERENCES inventory_count_line (id, count_id)
) ENGINE=InnoDB COMMENT='静态盘点库存维度写入锁';

ALTER TABLE inventory_ledger
    ADD COLUMN count_book_quantity DECIMAL(19,4) NULL AFTER balance_version_after,
    ADD COLUMN counted_quantity DECIMAL(19,4) NULL AFTER count_book_quantity,
    ADD COLUMN difference_quantity DECIMAL(19,4) NULL AFTER counted_quantity,
    ADD COLUMN adjustment_reason VARCHAR(255) NULL AFTER difference_quantity,
    ADD CONSTRAINT ck_inventory_ledger_count_metadata CHECK (
        (business_type = 'INVENTORY_COUNT'
            AND count_book_quantity IS NOT NULL
            AND counted_quantity IS NOT NULL
            AND difference_quantity = counted_quantity - count_book_quantity
            AND adjustment_reason IS NOT NULL
            AND CHAR_LENGTH(TRIM(adjustment_reason)) > 0)
        OR (business_type <> 'INVENTORY_COUNT'
            AND count_book_quantity IS NULL
            AND counted_quantity IS NULL
            AND difference_quantity IS NULL
            AND adjustment_reason IS NULL)
    );

INSERT INTO sys_permission(code, name, description)
VALUES
    ('INVENTORY_COUNT_READ', '查看库存盘点', '查询盘点单、快照和差异'),
    ('INVENTORY_COUNT_WRITE', '操作库存盘点', '创建、开始、录入和提交盘点单'),
    ('INVENTORY_COUNT_APPROVE', '审核库存盘点', '审核已提交的盘点单'),
    ('INVENTORY_COUNT_ADJUST', '执行盘点调整', '按已审核差异调整库存并写入流水');

INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT role.id, permission.id FROM sys_role role
JOIN sys_permission permission ON permission.code IN (
    'INVENTORY_COUNT_READ','INVENTORY_COUNT_WRITE','INVENTORY_COUNT_APPROVE','INVENTORY_COUNT_ADJUST')
WHERE role.code = 'ADMIN';

INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT role.id, permission.id FROM sys_role role
JOIN sys_permission permission ON permission.code IN (
    'INVENTORY_COUNT_READ','INVENTORY_COUNT_WRITE','INVENTORY_COUNT_ADJUST')
WHERE role.code = 'OPERATOR';

INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT role.id, permission.id FROM sys_role role
JOIN sys_permission permission ON permission.code IN ('INVENTORY_COUNT_READ','INVENTORY_COUNT_APPROVE')
WHERE role.code = 'AUDITOR';
