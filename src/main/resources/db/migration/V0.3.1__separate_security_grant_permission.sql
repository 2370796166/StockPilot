INSERT INTO sys_permission(code, name, description)
VALUES ('SECURITY_GRANT', '管理授权关系', '分配用户角色和角色权限');

INSERT INTO sys_role_permission(role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.code = 'SECURITY_GRANT'
WHERE r.code = 'SYSTEM_ADMIN';
