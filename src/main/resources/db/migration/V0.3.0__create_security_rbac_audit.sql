CREATE TABLE sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id), UNIQUE KEY uk_sys_user_username (username),
    CONSTRAINT ck_sys_user_status CHECK (status IN ('ENABLED','DISABLED'))
) ENGINE=InnoDB COMMENT='系统用户';

CREATE TABLE sys_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL, name VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id), UNIQUE KEY uk_sys_role_code (code),
    CONSTRAINT ck_sys_role_status CHECK (status IN ('ENABLED','DISABLED'))
) ENGINE=InnoDB COMMENT='系统角色';

CREATE TABLE sys_permission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(100) NOT NULL, name VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id), UNIQUE KEY uk_sys_permission_code (code),
    CONSTRAINT ck_sys_permission_status CHECK (status IN ('ENABLED','DISABLED'))
) ENGINE=InnoDB COMMENT='接口权限';

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL, role_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role(id)
) ENGINE=InnoDB COMMENT='用户角色关联';

CREATE TABLE sys_role_permission (
    role_id BIGINT NOT NULL, permission_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role(id),
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES sys_permission(id)
) ENGINE=InnoDB COMMENT='角色权限关联';

CREATE TABLE audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    operator_id BIGINT NULL, operator_name VARCHAR(64) NULL,
    action_type VARCHAR(64) NOT NULL, object_type VARCHAR(64) NOT NULL,
    object_id VARCHAR(100) NULL, result VARCHAR(16) NOT NULL,
    summary VARCHAR(500) NULL, occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), KEY idx_audit_operator_time (operator_id, occurred_at),
    KEY idx_audit_object (object_type, object_id),
    CONSTRAINT ck_audit_result CHECK (result IN ('SUCCESS','FAILURE'))
) ENGINE=InnoDB COMMENT='关键操作审计日志';

INSERT INTO sys_role(code,name) VALUES
('SYSTEM_ADMIN','系统管理员'),('WAREHOUSE_MANAGER','仓库管理员'),('PURCHASER','采购人员'),
('OUTBOUND_OPERATOR','出库操作员'),('AUDITOR','审核人员'),('VIEWER','只读人员');

INSERT INTO sys_permission(code,name,description) VALUES
('SECURITY_USER_READ','查看用户','查看用户资料'),('SECURITY_USER_WRITE','管理用户','创建、修改用户及分配角色'),
('SECURITY_ROLE_READ','查看角色','查看角色资料'),('SECURITY_ROLE_WRITE','管理角色','创建、修改角色及分配权限'),
('SECURITY_PERMISSION_READ','查看权限','查看权限资料'),('SECURITY_PERMISSION_WRITE','管理权限','创建和修改权限'),
('AUDIT_LOG_READ','查看审计日志','查询关键操作审计'),
('MASTER_DATA_READ','查看基础资料','查询基础资料'),('MASTER_DATA_WRITE','管理基础资料','创建和修改基础资料');

INSERT INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p WHERE r.code='SYSTEM_ADMIN';
INSERT INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.code IN ('MASTER_DATA_READ','MASTER_DATA_WRITE')
WHERE r.code='WAREHOUSE_MANAGER';
INSERT INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.code='MASTER_DATA_READ'
WHERE r.code IN ('PURCHASER','OUTBOUND_OPERATOR','AUDITOR','VIEWER');
