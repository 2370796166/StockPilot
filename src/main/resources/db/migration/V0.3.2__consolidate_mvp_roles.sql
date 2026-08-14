INSERT INTO sys_role(code, name, status)
VALUES
    ('ADMIN', '管理员', 'ENABLED'),
    ('OPERATOR', '业务员', 'ENABLED')
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    status = 'ENABLED';

INSERT IGNORE INTO sys_user_role(user_id, role_id)
SELECT ur.user_id, target.id
FROM sys_user_role ur
JOIN sys_role source ON source.id = ur.role_id
JOIN sys_role target ON target.code = 'ADMIN'
WHERE source.code = 'SYSTEM_ADMIN';

INSERT IGNORE INTO sys_user_role(user_id, role_id)
SELECT ur.user_id, target.id
FROM sys_user_role ur
JOIN sys_role source ON source.id = ur.role_id
JOIN sys_role target ON target.code = 'OPERATOR'
WHERE source.code IN ('WAREHOUSE_MANAGER', 'PURCHASER', 'OUTBOUND_OPERATOR', 'VIEWER');

DELETE ur
FROM sys_user_role ur
JOIN sys_role role ON role.id = ur.role_id
WHERE role.code IN ('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'PURCHASER', 'OUTBOUND_OPERATOR', 'VIEWER');

DELETE rp
FROM sys_role_permission rp
JOIN sys_role role ON role.id = rp.role_id
WHERE role.code IN (
    'SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'PURCHASER', 'OUTBOUND_OPERATOR', 'VIEWER',
    'ADMIN', 'OPERATOR', 'AUDITOR'
);

DELETE FROM sys_role
WHERE code IN ('SYSTEM_ADMIN', 'WAREHOUSE_MANAGER', 'PURCHASER', 'OUTBOUND_OPERATOR', 'VIEWER');

INSERT INTO sys_role_permission(role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
CROSS JOIN sys_permission permission
WHERE role.code = 'ADMIN';

INSERT INTO sys_role_permission(role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission ON permission.code = 'MASTER_DATA_READ'
WHERE role.code = 'OPERATOR';

INSERT INTO sys_role_permission(role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission ON permission.code IN ('MASTER_DATA_READ', 'AUDIT_LOG_READ')
WHERE role.code = 'AUDITOR';
