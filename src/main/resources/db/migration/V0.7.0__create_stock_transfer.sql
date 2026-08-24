CREATE TABLE stock_transfer_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    transfer_no VARCHAR(64) NOT NULL,
    source_warehouse_id BIGINT NOT NULL,
    target_warehouse_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    remark VARCHAR(255) NULL,
    created_by BIGINT NOT NULL,
    created_by_name VARCHAR(64) NOT NULL,
    submitted_by BIGINT NULL,
    submitted_by_name VARCHAR(64) NULL,
    submitted_at DATETIME(3) NULL,
    approved_by BIGINT NULL,
    approved_by_name VARCHAR(64) NULL,
    approved_at DATETIME(3) NULL,
    outbound_by BIGINT NULL,
    outbound_by_name VARCHAR(64) NULL,
    outbound_at DATETIME(3) NULL,
    transit_by BIGINT NULL,
    transit_by_name VARCHAR(64) NULL,
    transit_at DATETIME(3) NULL,
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
    UNIQUE KEY uk_stock_transfer_no (transfer_no),
    UNIQUE KEY uk_stock_transfer_header_warehouses (id, source_warehouse_id, target_warehouse_id),
    KEY idx_stock_transfer_status (status, id),
    KEY idx_stock_transfer_source (source_warehouse_id, status, id),
    KEY idx_stock_transfer_target (target_warehouse_id, status, id),
    CONSTRAINT fk_stock_transfer_source_warehouse FOREIGN KEY (source_warehouse_id) REFERENCES warehouse (id),
    CONSTRAINT fk_stock_transfer_target_warehouse FOREIGN KEY (target_warehouse_id) REFERENCES warehouse (id),
    CONSTRAINT ck_stock_transfer_warehouses CHECK (source_warehouse_id <> target_warehouse_id),
    CONSTRAINT ck_stock_transfer_status CHECK (status IN (
        'DRAFT', 'SUBMITTED', 'APPROVED', 'OUTBOUND_COMPLETED', 'IN_TRANSIT', 'COMPLETED', 'CANCELLED'
    ))
) ENGINE=InnoDB COMMENT='仓库间库存调拨单';

CREATE TABLE stock_transfer_line (
    id BIGINT NOT NULL AUTO_INCREMENT,
    transfer_id BIGINT NOT NULL,
    source_warehouse_id BIGINT NOT NULL,
    target_warehouse_id BIGINT NOT NULL,
    line_no INT UNSIGNED NOT NULL,
    source_location_id BIGINT NOT NULL,
    target_location_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    quantity DECIMAL(19,4) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_transfer_line_no (transfer_id, line_no),
    UNIQUE KEY uk_stock_transfer_line_source_dimension
        (transfer_id, source_location_id, sku_id),
    UNIQUE KEY uk_stock_transfer_line_target_dimension
        (transfer_id, target_location_id, sku_id),
    UNIQUE KEY uk_stock_transfer_line_id_header (id, transfer_id),
    KEY idx_stock_transfer_line_sku (sku_id),
    CONSTRAINT fk_stock_transfer_line_header
        FOREIGN KEY (transfer_id, source_warehouse_id, target_warehouse_id)
        REFERENCES stock_transfer_order (id, source_warehouse_id, target_warehouse_id),
    CONSTRAINT fk_stock_transfer_line_source_location
        FOREIGN KEY (source_location_id, source_warehouse_id)
        REFERENCES warehouse_location (id, warehouse_id),
    CONSTRAINT fk_stock_transfer_line_target_location
        FOREIGN KEY (target_location_id, target_warehouse_id)
        REFERENCES warehouse_location (id, warehouse_id),
    CONSTRAINT fk_stock_transfer_line_sku FOREIGN KEY (sku_id) REFERENCES sku (id),
    CONSTRAINT ck_stock_transfer_line_number CHECK (line_no > 0),
    CONSTRAINT ck_stock_transfer_line_quantity CHECK (quantity > 0)
) ENGINE=InnoDB COMMENT='仓库间库存调拨明细';

CREATE TABLE stock_transfer_transit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    transfer_id BIGINT NOT NULL,
    transfer_line_id BIGINT NOT NULL,
    outbound_quantity DECIMAL(19,4) NOT NULL,
    in_transit_quantity DECIMAL(19,4) NOT NULL,
    received_quantity DECIMAL(19,4) NOT NULL,
    status VARCHAR(16) NOT NULL,
    outbound_at DATETIME(3) NOT NULL,
    received_at DATETIME(3) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_transfer_transit_line (transfer_line_id),
    KEY idx_stock_transfer_transit_header (transfer_id, status),
    CONSTRAINT fk_stock_transfer_transit_line
        FOREIGN KEY (transfer_line_id, transfer_id) REFERENCES stock_transfer_line (id, transfer_id),
    CONSTRAINT ck_stock_transfer_transit_status CHECK (status IN ('IN_TRANSIT', 'RECEIVED')),
    CONSTRAINT ck_stock_transfer_transit_quantities CHECK (
        outbound_quantity > 0
        AND in_transit_quantity >= 0
        AND received_quantity >= 0
        AND outbound_quantity = in_transit_quantity + received_quantity
        AND ((status = 'IN_TRANSIT' AND in_transit_quantity = outbound_quantity AND received_quantity = 0)
          OR (status = 'RECEIVED' AND in_transit_quantity = 0 AND received_quantity = outbound_quantity))
    )
) ENGINE=InnoDB COMMENT='仓库间调拨在途事实';

INSERT INTO sys_permission(code, name, description)
VALUES
    ('TRANSFER_READ', '查看库存调拨单', '查询调拨单、明细和在途记录'),
    ('TRANSFER_WRITE', '操作库存调拨单', '创建、编辑、提交和取消调拨单'),
    ('TRANSFER_APPROVE', '审核库存调拨单', '审核已冻结源库存的调拨单'),
    ('TRANSFER_OUTBOUND', '执行调拨出库', '扣减源仓已冻结库存并形成在途记录'),
    ('TRANSFER_INBOUND', '执行调拨入库', '接收在途库存并增加目标仓库存');

INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT role.id, permission.id FROM sys_role role
JOIN sys_permission permission ON permission.code IN (
    'TRANSFER_READ', 'TRANSFER_WRITE', 'TRANSFER_APPROVE', 'TRANSFER_OUTBOUND', 'TRANSFER_INBOUND')
WHERE role.code = 'ADMIN';

INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT role.id, permission.id FROM sys_role role
JOIN sys_permission permission ON permission.code IN (
    'TRANSFER_READ', 'TRANSFER_WRITE', 'TRANSFER_OUTBOUND', 'TRANSFER_INBOUND')
WHERE role.code = 'OPERATOR';

INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT role.id, permission.id FROM sys_role role
JOIN sys_permission permission ON permission.code IN ('TRANSFER_READ', 'TRANSFER_APPROVE')
WHERE role.code = 'AUDITOR';
