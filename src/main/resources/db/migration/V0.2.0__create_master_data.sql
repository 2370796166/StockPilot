CREATE TABLE warehouse (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL,
    remark VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_warehouse_code (code),
    CONSTRAINT ck_warehouse_status CHECK (status IN ('ENABLED', 'DISABLED'))
) ENGINE=InnoDB COMMENT='仓库';

CREATE TABLE product_category (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL,
    remark VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_category_code (code),
    CONSTRAINT ck_product_category_status CHECK (status IN ('ENABLED', 'DISABLED'))
) ENGINE=InnoDB COMMENT='商品分类';

CREATE TABLE supplier (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(100) NOT NULL,
    contact_name VARCHAR(50) NULL,
    contact_phone VARCHAR(32) NULL,
    status VARCHAR(16) NOT NULL,
    remark VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_supplier_code (code),
    CONSTRAINT ck_supplier_status CHECK (status IN ('ENABLED', 'DISABLED'))
) ENGINE=InnoDB COMMENT='供应商';

CREATE TABLE sku (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(100) NOT NULL,
    category_id BIGINT NULL,
    unit VARCHAR(20) NOT NULL,
    status VARCHAR(16) NOT NULL,
    remark VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sku_code (code),
    KEY idx_sku_category (category_id),
    CONSTRAINT fk_sku_category FOREIGN KEY (category_id) REFERENCES product_category (id),
    CONSTRAINT ck_sku_status CHECK (status IN ('ENABLED', 'DISABLED'))
) ENGINE=InnoDB COMMENT='SKU';

CREATE TABLE warehouse_location (
    id BIGINT NOT NULL AUTO_INCREMENT,
    warehouse_id BIGINT NOT NULL,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL,
    remark VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_location_warehouse_code (warehouse_id, code),
    KEY idx_location_warehouse (warehouse_id),
    CONSTRAINT fk_location_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id),
    CONSTRAINT ck_location_status CHECK (status IN ('ENABLED', 'DISABLED'))
) ENGINE=InnoDB COMMENT='库位';
