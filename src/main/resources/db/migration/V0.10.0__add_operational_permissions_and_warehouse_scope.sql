ALTER TABLE sys_user ADD COLUMN all_warehouses BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN warehouse_scope_version INT UNSIGNED NOT NULL DEFAULT 0;

CREATE TABLE sys_user_warehouse (
    user_id BIGINT NOT NULL, warehouse_id BIGINT NOT NULL,
    PRIMARY KEY(user_id,warehouse_id),
    CONSTRAINT fk_user_warehouse_user FOREIGN KEY(user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_user_warehouse_warehouse FOREIGN KEY(warehouse_id) REFERENCES warehouse(id)
) ENGINE=InnoDB COMMENT='用户可访问仓库范围';

INSERT INTO sys_permission(code,name,description) VALUES
('SAFETY_STOCK_READ','查看安全库存','查看安全库存规则与预警'),
('SAFETY_STOCK_WRITE','管理安全库存','维护规则和重新评估预警'),
('MESSAGE_FAILURE_READ','查看消息异常','查询失败消息和处理轨迹'),
('MESSAGE_FAILURE_WRITE','处理消息异常','重试原事件或记录人工处理'),
('INVENTORY_RECONCILE','库存对账','只读核对库存维度与完整流水和单据事实'),
('OPERATIONS_READ','查看运行状态','查询基础监控和业务积压');
INSERT INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.code IN
('SAFETY_STOCK_READ','SAFETY_STOCK_WRITE','MESSAGE_FAILURE_READ','MESSAGE_FAILURE_WRITE','INVENTORY_RECONCILE','OPERATIONS_READ') WHERE r.code='ADMIN';
INSERT INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.code IN
('SAFETY_STOCK_READ','MESSAGE_FAILURE_READ','INVENTORY_RECONCILE','OPERATIONS_READ') WHERE r.code='AUDITOR';
INSERT INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.code='SAFETY_STOCK_READ' WHERE r.code='OPERATOR';
