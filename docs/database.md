# 数据库状态与设计基线

## 1. 当前已实现

### 数据库运行方式

- Docker Compose 使用 `mysql:8.0`。
- 数据库名 `stockpilot`，默认映射宿主端口 `3307` 到容器 `3306`。
- 数据保存在命名卷 `stockpilot_mysql_data`。
- `docker/mysql/init/001-init.sql` 只在首次创建空数据卷时由 MySQL 镜像自动执行。
- 已引入 Flyway。既有非空数据库以 `0.1.0` 为基线，基础资料迁移为 `0.2.0`，安全迁移为 `0.3.0`、`0.3.1` 和 `0.3.2`，库存底座迁移为 `0.4.0`，采购入库迁移为 `0.5.0`。
- `schema_version` 保留为历史骨架记录，后续版本以 `flyway_schema_history` 为准。

### 实际存在的表

2026-08-14 本轮通过目标容器确认共有 17 张表：

```text
schema_version
flyway_schema_history
warehouse
warehouse_location
product_category
sku
supplier
sys_user
sys_role
sys_permission
sys_user_role
sys_role_permission
audit_log
inventory_balance
inventory_ledger
purchase_receipt
purchase_receipt_line
```

其中 `schema_version` 是骨架遗留版本记录，`flyway_schema_history` 是 Flyway 元数据；业务/应用表为其余 15 张。

#### `schema_version`

用途：记录基础骨架初始化版本。

| 字段 | 类型 | 约束 |
|---|---|---|
| `id` | `BIGINT` | 主键、自增、非空 |
| `version_no` | `VARCHAR(32)` | 非空、唯一键 `uk_schema_version_no` |
| `description` | `VARCHAR(255)` | 非空 |
| `installed_at` | `DATETIME(3)` | 非空，默认 `CURRENT_TIMESTAMP(3)` |

`V0.3.0` 新增安全表；`V0.4.0` 新增库存余额和库存流水；`V0.5.0` 新增采购入库头表、明细表和四个采购权限。2026-08-14 已在目标 MySQL 验证迁移链成功，0.1.0 至 0.5.0 均为 success；数据库中仍没有销售、调拨或盘点单据表。

安全审查迁移 `V0.3.1` 新增独立 `SECURITY_GRANT` 权限；`V0.3.2` 将早期六角色种子及已有用户角色关系收敛到三个 MVP 角色。两次迁移均已在目标 MySQL 验证成功。

基础资料共同字段为 `id`、`code`、`name`、`status`、`remark`、创建/更新时间和乐观锁 `version`。SKU 额外包含可选 `category_id` 与必填 `unit`；库位包含必填 `warehouse_id`；供应商预留可选联系人和联系电话字段。

唯一约束：仓库、分类、SKU、供应商编码分别全局唯一；库位为 `(warehouse_id, code)` 唯一。库位到仓库、SKU 到分类使用真实外键。状态使用 CHECK 约束限定为 `ENABLED` 或 `DISABLED`。

### 当前表结构明细

#### `warehouse`

- 字段：`id`、`code`、`name`、`status`、`remark`、`created_at`、`updated_at`、`version`。
- 主键：`id BIGINT AUTO_INCREMENT`。
- 唯一约束：`uk_warehouse_code(code)`。
- 状态约束：`ENABLED`、`DISABLED`。

#### `warehouse_location`

- 字段：公共基础资料字段以及 `warehouse_id`。
- 主键：`id BIGINT AUTO_INCREMENT`。
- 唯一约束：`uk_location_warehouse_code(warehouse_id, code)`。
- 普通索引：`idx_location_warehouse(warehouse_id)`；该索引与唯一索引左前缀重叠，是否保留应结合真实查询计划评估。
- 外键：`warehouse_id → warehouse.id`。

#### `product_category`

- 字段与仓库公共字段相同。
- 主键：`id BIGINT AUTO_INCREMENT`。
- 唯一约束：`uk_product_category_code(code)`。
- 当前为单层分类，没有父级字段。

#### `sku`

- 字段：公共基础资料字段以及可空 `category_id`、必填 `unit`。
- 主键：`id BIGINT AUTO_INCREMENT`。
- 唯一约束：`uk_sku_code(code)`。
- 普通索引：`idx_sku_category(category_id)`。
- 外键：`category_id → product_category.id`。

#### `supplier`

- 字段：公共基础资料字段以及可空 `contact_name`、`contact_phone`。
- 主键：`id BIGINT AUTO_INCREMENT`。
- 唯一约束：`uk_supplier_code(code)`。
- 当前没有供应商与 SKU 供货关系表。

#### `sys_user`

- 字段：`id`、`username`、`password_hash`、`display_name`、`status`、时间字段、`version`。
- 主键：`id BIGINT AUTO_INCREMENT`。
- 唯一约束：`uk_sys_user_username(username)`。
- `password_hash VARCHAR(100)` 保存 BCrypt 哈希；接口 VO 不返回该字段。

#### `sys_role`

- 字段：`id`、`code`、`name`、`status`、时间字段、`version`。
- 主键：`id BIGINT AUTO_INCREMENT`。
- 唯一约束：`uk_sys_role_code(code)`。
- 当前种子数据为 `ADMIN`、`OPERATOR`、`AUDITOR` 3 个角色。`V0.3.2` 将早期六角色关系按最小权限原则迁移：系统管理员映射到 `ADMIN`，审核员保留为 `AUDITOR`，其余早期业务/只读角色映射到 `OPERATOR`。

#### `sys_permission`

- 字段：`id`、`code`、`name`、`description`、`status`、时间字段、`version`。
- 主键：`id BIGINT AUTO_INCREMENT`。
- 唯一约束：`uk_sys_permission_code(code)`。
- 当前种子数据为 15 个权限，包含 `SECURITY_GRANT`、`INVENTORY_READ` 和四个采购入库权限。

#### `sys_user_role`

- 字段：`user_id`、`role_id`、`created_at`。
- 复合主键：`(user_id, role_id)`，同时防止重复授权。
- 外键分别指向 `sys_user`、`sys_role`；没有级联删除。

#### `sys_role_permission`

- 字段：`role_id`、`permission_id`、`created_at`。
- 复合主键：`(role_id, permission_id)`，同时防止重复授权。
- 外键分别指向 `sys_role`、`sys_permission`；没有级联删除。

#### `audit_log`

- 字段：`id`、操作人快照、动作类型、对象类型/ID、结果、摘要和发生时间。
- 主键：`id BIGINT AUTO_INCREMENT`。
- 普通索引：`idx_audit_operator_time(operator_id, occurred_at)`、`idx_audit_object(object_type, object_id)`。
- `result` 只允许 `SUCCESS`、`FAILURE`。
- 当前是基础安全审计，不是库存流水；数据库层未禁止更新或删除。

#### `inventory_balance`

- 主键：`id BIGINT AUTO_INCREMENT`。
- 唯一约束：`uk_inventory_balance_dimension(warehouse_id, location_id, sku_id)`。
- 数量：`actual_quantity`、`available_quantity`、`frozen_quantity` 均为 `DECIMAL(19,4)`。
- 并发字段：`version INT UNSIGNED`；条件更新要求匹配预期版本并原子加 1。
- `CHECK`：三个数量非负，且 `actual_quantity = available_quantity + frozen_quantity`。
- 复合外键保证库位属于指定仓库，SKU 使用真实外键。

#### `inventory_ledger`

- 流水编号全局唯一；`(business_type, business_no, warehouse_id, location_id, sku_id)` 唯一，作为业务动作幂等防线。
- 分别保存 actual、available、frozen 的前值、变化值和后值，保存余额版本前后值、操作人及毫秒时间。
- `CHECK` 同时校验前后状态不变量、变化量算术关系和版本链。
- 应用 Mapper 只提供新增和分页查询，不提供更新或删除；当前数据库运行账号仍有直接 SQL 修改能力，生产级账号权限拆分待实现。

#### `purchase_receipt`

- 业务单号 `receipt_no` 全局唯一，状态只允许 `DRAFT`、`SUBMITTED`、`APPROVED`、`COMPLETED`。
- 保存仓库、制单/提交/审核/完成人及相应时间，并使用 `version` 进行状态条件更新。
- `(id, warehouse_id)` 唯一键供明细复合外键使用，保证明细仓库与单据头一致。

#### `purchase_receipt_line`

- 数量为 `DECIMAL(19,4)` 且必须大于零。
- `(receipt_id, line_no)` 和 `(receipt_id, location_id, sku_id)` 分别唯一。
- 复合外键同时保证明细仓库与单据头一致、库位属于指定仓库；SKU 使用真实外键。

#### `flyway_schema_history`

由 Flyway 管理，不得由业务代码修改。目标库当前记录 `0.1.0` 至 `0.5.0`，均为成功。另以采购入库专用临时库成功回放 `0.2.0` 至 `0.5.0`。

### 已验证编码

数据库字符集为 `utf8mb4`，排序规则为 `utf8mb4_0900_ai_ci`。MySQL CLI 输出中文表注释时显示问号，但十六进制检查确认实际存储为正确 UTF-8 字节。

## 2. 已确认、待实现

以下只是设计基线，不代表表已经存在：

- 库存：`inventory_balance`、`inventory_ledger` 已实现；安全库存配置待实现。
- 单据：采购入库单及明细已实现；销售出库单及明细待实现。
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
- `warehouse_location` 的普通仓库索引是否与复合唯一索引重复，实施库存查询后用 `EXPLAIN` 决定。
- 审计表是否需要数据库账号层面的 INSERT/SELECT 权限限制和归档策略。
