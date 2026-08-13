# 数据库状态与设计基线

## 1. 当前已实现

### 数据库运行方式

- Docker Compose 使用 `mysql:8.0`。
- 数据库名 `stockpilot`，默认映射宿主端口 `3307` 到容器 `3306`。
- 数据保存在命名卷 `stockpilot_mysql_data`。
- `docker/mysql/init/001-init.sql` 只在首次创建空数据卷时由 MySQL 镜像自动执行。
- 已引入 Flyway。既有非空数据库以 `0.1.0` 为基线，基础资料迁移为 `0.2.0`，安全迁移为 `0.3.0` 和 `0.3.1`。
- `schema_version` 保留为历史骨架记录，后续版本以 `flyway_schema_history` 为准。

### 实际存在的表

2026-08-13 通过目标容器执行 `SHOW TABLES`，确认包含以下表：

#### `schema_version`

用途：记录基础骨架初始化版本。

| 字段 | 类型 | 约束 |
|---|---|---|
| `id` | `BIGINT` | 主键、自增、非空 |
| `version_no` | `VARCHAR(32)` | 非空、唯一键 `uk_schema_version_no` |
| `description` | `VARCHAR(255)` | 非空 |
| `installed_at` | `DATETIME(3)` | 非空，默认 `CURRENT_TIMESTAMP(3)` |

`V0.3.0` 新增 `sys_user`、`sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission` 和 `audit_log`。2026-08-13 已在目标 MySQL 验证迁移成功，0.1.0、0.2.0、0.3.0 均为 success；数据库中仍没有库存或单据表。

安全审查迁移 `V0.3.1` 新增独立 `SECURITY_GRANT` 权限并仅关联 `SYSTEM_ADMIN`，已在目标 MySQL 验证成功。

基础资料共同字段为 `id`、`code`、`name`、`status`、`remark`、创建/更新时间和乐观锁 `version`。SKU 额外包含可选 `category_id` 与必填 `unit`；库位包含必填 `warehouse_id`；供应商预留可选联系人和联系电话字段。

唯一约束：仓库、分类、SKU、供应商编码分别全局唯一；库位为 `(warehouse_id, code)` 唯一。库位到仓库、SKU 到分类使用真实外键。状态使用 CHECK 约束限定为 `ENABLED` 或 `DISABLED`。

### 已验证编码

数据库字符集为 `utf8mb4`，排序规则为 `utf8mb4_0900_ai_ci`。MySQL CLI 输出中文表注释时显示问号，但十六进制检查确认实际存储为正确 UTF-8 字节。

## 2. 已确认、待实现

以下只是设计基线，不代表表已经存在：

- 库存：`inventory_balance`、`inventory_ledger`、安全库存配置。
- 单据：采购入库单及明细、销售出库单及明细。
- 可靠性：幂等请求记录、MQ 消费记录、安全库存预警。基础安全审计表已经实现。

### 约束基线

- 业务主键计划使用 `BIGINT AUTO_INCREMENT`，业务单号与技术主键分离。
- 库存余额唯一维度：`warehouse_id + location_id + sku_id`。
- 入库单号、出库单号必须分别唯一，并使用不同前缀。
- 库位归属仓库、单据头仓库与明细仓库需由复合外键或等效数据库约束保证。
- 数量计划使用 `DECIMAL(19,4)`，Java 使用 `BigDecimal`；不得使用浮点数。
- 金额计划使用 `DECIMAL(19,4)` 和统一舍入规则；行金额、总金额的持久化方式实施前确认。
- 库存数量不得为负，且冻结量不得超过实际量。
- 库存余额和有并发状态流转的单据计划使用 `version INT UNSIGNED NOT NULL DEFAULT 0`。
- `version` 必须出现在更新条件并原子递增；库存防超卖还必须包含数量条件。
- 库存流水业务动作必须唯一，并记录库存版本链及前值、变化值、后值。

### 软删除规则

当前没有软删除字段。已确认不采用通用 `deleted` 配合业务编码唯一索引，因为重复删除同一编码会产生唯一键冲突并使语义复杂。基础资料使用 `ENABLED/DISABLED`；业务单据使用 `CANCELLED`；库存余额和流水不得删除。

## 3. 尚待确认

- 商品分类未来是否升级为树形结构。
- SKU 是否需要增加独立 SPU 模型。
- 供应商是否与 SKU 建立供货关系表；下一阶段可先不建立。
- 外键采用数据库真实约束还是仅应用维护；库存与单据核心关系倾向真实外键。
- 行金额是否使用生成列，订单头总金额是否持久化。
- 安全库存按仓库 + SKU 还是仓库 + 库位 + SKU。
- MySQL 镜像是否固定到明确补丁版本。
