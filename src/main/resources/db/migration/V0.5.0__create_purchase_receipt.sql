CREATE TABLE purchase_receipt (
    id BIGINT NOT NULL AUTO_INCREMENT,
    receipt_no VARCHAR(64) NOT NULL,
    warehouse_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    remark VARCHAR(255) NULL,
    created_by BIGINT NOT NULL,
    created_by_name VARCHAR(64) NOT NULL,
    submitted_by BIGINT NULL,
    submitted_by_name VARCHAR(64) NULL,
    submitted_at DATETIME(3) NULL,
    approved_by BIGINT NULL,
    approved_by_name VARCHAR(64) NULL,
    approved_at DATETIME(3) NULL,
    completed_by BIGINT NULL,
    completed_by_name VARCHAR(64) NULL,
    completed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_purchase_receipt_no (receipt_no),
    UNIQUE KEY uk_purchase_receipt_id_warehouse (id, warehouse_id),
    KEY idx_purchase_receipt_warehouse_status (warehouse_id, status, id),
    CONSTRAINT fk_purchase_receipt_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id),
    CONSTRAINT ck_purchase_receipt_status CHECK (
        status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'COMPLETED')
    )
) ENGINE=InnoDB COMMENT='采购入库单';

CREATE TABLE purchase_receipt_line (
    id BIGINT NOT NULL AUTO_INCREMENT,
    receipt_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    line_no INT UNSIGNED NOT NULL,
    location_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    quantity DECIMAL(19,4) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_purchase_receipt_line_no (receipt_id, line_no),
    UNIQUE KEY uk_purchase_receipt_line_dimension (receipt_id, location_id, sku_id),
    KEY idx_purchase_receipt_line_sku (sku_id),
    CONSTRAINT fk_purchase_receipt_line_header
        FOREIGN KEY (receipt_id, warehouse_id) REFERENCES purchase_receipt (id, warehouse_id),
    CONSTRAINT fk_purchase_receipt_line_location
        FOREIGN KEY (location_id, warehouse_id) REFERENCES warehouse_location (id, warehouse_id),
    CONSTRAINT fk_purchase_receipt_line_sku FOREIGN KEY (sku_id) REFERENCES sku (id),
    CONSTRAINT ck_purchase_receipt_line_number CHECK (line_no > 0),
    CONSTRAINT ck_purchase_receipt_line_quantity CHECK (quantity > 0)
) ENGINE=InnoDB COMMENT='采购入库单明细';

INSERT INTO sys_permission(code, name, description)
VALUES
    ('PURCHASE_RECEIPT_READ', '查看采购入库单', '查询采购入库单和明细'),
    ('PURCHASE_RECEIPT_WRITE', '编辑采购入库单', '创建、编辑和提交采购入库单'),
    ('PURCHASE_RECEIPT_APPROVE', '审核采购入库单', '审核已提交的采购入库单'),
    ('PURCHASE_RECEIPT_COMPLETE', '执行采购入库', '将审核通过的采购入库单写入库存')
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    status = 'ENABLED';

INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission ON permission.code IN (
    'PURCHASE_RECEIPT_READ',
    'PURCHASE_RECEIPT_WRITE',
    'PURCHASE_RECEIPT_APPROVE',
    'PURCHASE_RECEIPT_COMPLETE'
)
WHERE role.code = 'ADMIN';

INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission ON permission.code IN (
    'PURCHASE_RECEIPT_READ',
    'PURCHASE_RECEIPT_WRITE',
    'PURCHASE_RECEIPT_COMPLETE'
)
WHERE role.code = 'OPERATOR';

INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission ON permission.code IN (
    'PURCHASE_RECEIPT_READ',
    'PURCHASE_RECEIPT_APPROVE'
)
WHERE role.code = 'AUDITOR';
