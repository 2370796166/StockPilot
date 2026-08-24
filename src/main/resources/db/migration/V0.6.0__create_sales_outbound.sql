CREATE TABLE sales_outbound_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    outbound_no VARCHAR(64) NOT NULL,
    warehouse_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    remark VARCHAR(255) NULL,
    created_by BIGINT NOT NULL,
    created_by_name VARCHAR(64) NOT NULL,
    reserved_by BIGINT NULL,
    reserved_by_name VARCHAR(64) NULL,
    reserved_at DATETIME(3) NULL,
    approved_by BIGINT NULL,
    approved_by_name VARCHAR(64) NULL,
    approved_at DATETIME(3) NULL,
    completed_by BIGINT NULL,
    completed_by_name VARCHAR(64) NULL,
    completed_at DATETIME(3) NULL,
    cancelled_by BIGINT NULL,
    cancelled_by_name VARCHAR(64) NULL,
    cancelled_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sales_outbound_no (outbound_no),
    UNIQUE KEY uk_sales_outbound_id_warehouse (id, warehouse_id),
    KEY idx_sales_outbound_warehouse_status (warehouse_id, status, id),
    CONSTRAINT fk_sales_outbound_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id),
    CONSTRAINT ck_sales_outbound_status CHECK (
        status IN ('DRAFT', 'RESERVED', 'APPROVED', 'COMPLETED', 'CANCELLED')
    )
) ENGINE=InnoDB COMMENT='销售出库单';

CREATE TABLE sales_outbound_line (
    id BIGINT NOT NULL AUTO_INCREMENT,
    outbound_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    line_no INT UNSIGNED NOT NULL,
    location_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    quantity DECIMAL(19,4) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sales_outbound_line_no (outbound_id, line_no),
    UNIQUE KEY uk_sales_outbound_line_dimension (outbound_id, location_id, sku_id),
    KEY idx_sales_outbound_line_sku (sku_id),
    CONSTRAINT fk_sales_outbound_line_header
        FOREIGN KEY (outbound_id, warehouse_id) REFERENCES sales_outbound_order (id, warehouse_id),
    CONSTRAINT fk_sales_outbound_line_location
        FOREIGN KEY (location_id, warehouse_id) REFERENCES warehouse_location (id, warehouse_id),
    CONSTRAINT fk_sales_outbound_line_sku FOREIGN KEY (sku_id) REFERENCES sku (id),
    CONSTRAINT ck_sales_outbound_line_number CHECK (line_no > 0),
    CONSTRAINT ck_sales_outbound_line_quantity CHECK (quantity > 0)
) ENGINE=InnoDB COMMENT='销售出库单明细';

INSERT INTO sys_permission(code, name, description)
VALUES
    ('SALES_OUTBOUND_READ', '查看销售出库单', '查询销售出库单和明细'),
    ('SALES_OUTBOUND_WRITE', '操作销售出库单', '创建、编辑、冻结和取消销售出库单'),
    ('SALES_OUTBOUND_APPROVE', '审核销售出库单', '审核已冻结的销售出库单'),
    ('SALES_OUTBOUND_COMPLETE', '执行销售出库', '扣减已冻结库存并完成销售出库单');

INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission ON permission.code IN (
    'SALES_OUTBOUND_READ',
    'SALES_OUTBOUND_WRITE',
    'SALES_OUTBOUND_APPROVE',
    'SALES_OUTBOUND_COMPLETE'
)
WHERE role.code = 'ADMIN';

INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission ON permission.code IN (
    'SALES_OUTBOUND_READ',
    'SALES_OUTBOUND_WRITE',
    'SALES_OUTBOUND_COMPLETE'
)
WHERE role.code = 'OPERATOR';

INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission ON permission.code IN (
    'SALES_OUTBOUND_READ',
    'SALES_OUTBOUND_APPROVE'
)
WHERE role.code = 'AUDITOR';
