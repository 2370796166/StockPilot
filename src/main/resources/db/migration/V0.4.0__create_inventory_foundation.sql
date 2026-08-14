ALTER TABLE warehouse_location
    ADD UNIQUE KEY uk_location_id_warehouse (id, warehouse_id);

CREATE TABLE inventory_balance (
    id BIGINT NOT NULL AUTO_INCREMENT,
    warehouse_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    actual_quantity DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    available_quantity DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    frozen_quantity DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_balance_dimension (warehouse_id, location_id, sku_id),
    KEY idx_inventory_balance_sku (sku_id),
    CONSTRAINT fk_inventory_balance_location_warehouse
        FOREIGN KEY (location_id, warehouse_id) REFERENCES warehouse_location (id, warehouse_id),
    CONSTRAINT fk_inventory_balance_sku FOREIGN KEY (sku_id) REFERENCES sku (id),
    CONSTRAINT ck_inventory_balance_nonnegative CHECK (
        actual_quantity >= 0 AND available_quantity >= 0 AND frozen_quantity >= 0
    ),
    CONSTRAINT ck_inventory_balance_invariant CHECK (
        actual_quantity = available_quantity + frozen_quantity
    )
) ENGINE=InnoDB COMMENT='库存余额';

CREATE TABLE inventory_ledger (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ledger_no VARCHAR(64) NOT NULL,
    business_type VARCHAR(32) NOT NULL,
    business_no VARCHAR(64) NOT NULL,
    warehouse_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    before_actual_quantity DECIMAL(19,4) NOT NULL,
    change_actual_quantity DECIMAL(19,4) NOT NULL,
    after_actual_quantity DECIMAL(19,4) NOT NULL,
    before_available_quantity DECIMAL(19,4) NOT NULL,
    change_available_quantity DECIMAL(19,4) NOT NULL,
    after_available_quantity DECIMAL(19,4) NOT NULL,
    before_frozen_quantity DECIMAL(19,4) NOT NULL,
    change_frozen_quantity DECIMAL(19,4) NOT NULL,
    after_frozen_quantity DECIMAL(19,4) NOT NULL,
    balance_version_before INT UNSIGNED NOT NULL,
    balance_version_after INT UNSIGNED NOT NULL,
    operator_id BIGINT NULL,
    operator_name VARCHAR(64) NOT NULL,
    occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_ledger_no (ledger_no),
    UNIQUE KEY uk_inventory_ledger_business_dimension
        (business_type, business_no, warehouse_id, location_id, sku_id),
    KEY idx_inventory_ledger_dimension_time
        (warehouse_id, location_id, sku_id, occurred_at, id),
    KEY idx_inventory_ledger_business (business_type, business_no),
    KEY idx_inventory_ledger_sku_time (sku_id, occurred_at, id),
    CONSTRAINT fk_inventory_ledger_location_warehouse
        FOREIGN KEY (location_id, warehouse_id) REFERENCES warehouse_location (id, warehouse_id),
    CONSTRAINT fk_inventory_ledger_sku FOREIGN KEY (sku_id) REFERENCES sku (id),
    CONSTRAINT ck_inventory_ledger_before_nonnegative CHECK (
        before_actual_quantity >= 0
        AND before_available_quantity >= 0
        AND before_frozen_quantity >= 0
    ),
    CONSTRAINT ck_inventory_ledger_after_nonnegative CHECK (
        after_actual_quantity >= 0
        AND after_available_quantity >= 0
        AND after_frozen_quantity >= 0
    ),
    CONSTRAINT ck_inventory_ledger_before_invariant CHECK (
        before_actual_quantity = before_available_quantity + before_frozen_quantity
    ),
    CONSTRAINT ck_inventory_ledger_after_invariant CHECK (
        after_actual_quantity = after_available_quantity + after_frozen_quantity
    ),
    CONSTRAINT ck_inventory_ledger_delta CHECK (
        after_actual_quantity = before_actual_quantity + change_actual_quantity
        AND after_available_quantity = before_available_quantity + change_available_quantity
        AND after_frozen_quantity = before_frozen_quantity + change_frozen_quantity
    ),
    CONSTRAINT ck_inventory_ledger_version CHECK (
        (business_type = 'INITIALIZE' AND balance_version_after = balance_version_before)
        OR (business_type <> 'INITIALIZE' AND balance_version_after = balance_version_before + 1)
    )
) ENGINE=InnoDB COMMENT='不可变库存流水';

INSERT IGNORE INTO sys_permission(code, name, description)
VALUES ('INVENTORY_READ', '查看库存', '分页查询库存余额和库存流水');

INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission ON permission.code = 'INVENTORY_READ'
WHERE role.code IN ('ADMIN', 'OPERATOR', 'AUDITOR');
