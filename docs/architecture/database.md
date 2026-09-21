# 数据库状态与设计基线

## 1. 当前已实现

### 数据库运行方式

- Docker Compose 使用 `mysql:8.0`。
- 数据库名 `stockpilot`，默认映射宿主端口 `3307` 到容器 `3306`。
- 数据保存在命名卷 `stockpilot_mysql_data`。
- `docker/mysql/init/001-init.sql` 只在首次创建空数据卷时由 MySQL 镜像自动执行。
- 已引入 Flyway。既有非空数据库以 `0.1.0` 为基线，基础资料迁移为 `0.2.0`，安全迁移为 `0.3.0`、`0.3.1` 和 `0.3.2`，库存底座迁移为 `0.4.0`，采购入库迁移为 `0.5.0`，销售出库迁移为 `0.6.0`，仓库间调拨迁移为 `0.7.0`，静态盘点迁移为 `0.8.0`，异步消息和安全库存迁移为`0.9.0`。
- `schema_version` 保留为历史骨架记录，后续版本以 `flyway_schema_history` 为准。

### 实际存在的表

2026-08-24通过目标容器确认当前Flyway版本为`0.9.0`，共有31张表：

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
sales_outbound_order
sales_outbound_line
stock_transfer_order
stock_transfer_line
stock_transfer_transit
inventory_count_order
inventory_count_line
inventory_count_scope_lock
safety_stock_rule
low_stock_alert
async_outbox_message
async_consumed_message
async_message_trace
async_failure_record
```

其中 `schema_version` 是骨架遗留版本记录，`flyway_schema_history` 是 Flyway 元数据；业务/应用表为其余29张。

#### `schema_version`

用途：记录基础骨架初始化版本。

| 字段 | 类型 | 约束 |
|---|---|---|
| `id` | `BIGINT` | 主键、自增、非空 |
| `version_no` | `VARCHAR(32)` | 非空、唯一键 `uk_schema_version_no` |
| `description` | `VARCHAR(255)` | 非空 |
| `installed_at` | `DATETIME(3)` | 非空，默认 `CURRENT_TIMESTAMP(3)` |

`V0.3.0` 新增安全表；`V0.4.0` 新增库存余额和库存流水；`V0.5.0` 新增采购入库头表、明细表和四个采购权限；`V0.6.0` 新增销售出库头表、明细表和四个销售权限；`V0.7.0` 新增调拨头表、明细表、在途表和五个调拨权限；`V0.8.0` 新增盘点头、明细、维度锁表、四个盘点权限及流水盘点元数据。2026-08-14 已在四个 MySQL 专用空库验证迁移链可执行至 `0.8.0`。

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
- 迁移至 `0.8.0` 后种子数据为 28 个权限，包含 `SECURITY_GRANT`、`INVENTORY_READ`、四个采购入库权限、四个销售出库权限、五个调拨权限和四个盘点权限。

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
- 分页索引：`idx_inventory_balance_warehouse_id(warehouse_id, id)`；依据 2026-08-24 的真实 `EXPLAIN ANALYZE` 增加，用于仓库条件下按 `id DESC` 分页，避免对该仓库全部余额做额外排序。
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

#### `sales_outbound_order` / `sales_outbound_line`

- `outbound_no` 全局唯一，状态限定为 `DRAFT`、`RESERVED`、`APPROVED`、`COMPLETED`、`CANCELLED`。
- 头表保存制单、冻结、审核、完成、取消操作人与时间；版本列和状态条件更新共同防止非法跳转。
- 明细数量必须大于零，同单库位/SKU 维度唯一；复合外键保证明细仓库、库位和单据头一致。

#### `stock_transfer_order` / `stock_transfer_line` / `stock_transfer_transit`

- 调拨状态限定为 `DRAFT`、`SUBMITTED`、`APPROVED`、`OUTBOUND_COMPLETED`、`IN_TRANSIT`、`COMPLETED`、`CANCELLED`。
- 数据库 `CHECK` 保证源仓库与目标仓库不同；复合外键保证源、目标库位分别属于对应仓库。
- 同一调拨单的源库位/SKU维度和目标库位/SKU维度分别唯一，与库存流水业务动作唯一键保持一致。
- 在途表按调拨明细唯一，保存调出量、当前在途量、已收货量和状态；约束保证三者算术关系及状态一致。
- 调出创建在途记录，目标收货使用条件更新把在途量转为已收货量；在途数量不写入任何仓库的库存余额。

#### `inventory_count_order` / `inventory_count_line` / `inventory_count_scope_lock`

- 盘点状态限定为 `DRAFT`、`COUNTING`、`SUBMITTED`、`APPROVED`、`ADJUSTED`；单号全局唯一并保存各状态操作人和版本。
- 明细按盘点单内库位/SKU唯一，保存 actual、available、frozen、balance version 快照，以及实盘量、自动差异和原因；数据库约束校验快照不变量和差异算术。
- 维度锁表按 `(warehouse_id, location_id, sku_id)` 唯一，阻止同维度并行盘点和普通库存变更；成功调整后物理释放该操作性锁，盘点头、明细和流水保留审计事实。
- `inventory_ledger` 对 `INVENTORY_COUNT` 强制保存账面量、实盘量、差异和原因，其他业务类型不得填写这些字段。

#### `flyway_schema_history`

由 Flyway 管理，不得由业务代码修改。采购、销售、调拨、盘点和消息专用临时库均已成功回放迁移至 `0.9.0`。

### 已验证编码

数据库字符集为 `utf8mb4`，排序规则为 `utf8mb4_0900_ai_ci`。MySQL CLI 输出中文表注释时显示问号，但十六进制检查确认实际存储为正确 UTF-8 字节。

#### 异步消息与安全库存表（`V0.9.0`）

- `safety_stock_rule`：仓库 + 库位 + SKU 唯一阈值规则；阈值非负，可启用或停用。
- `low_stock_alert`：每条规则一条当前预警状态，保存阈值、最新可用量、来源消息和业务单号；状态为 `OPEN` 或 `RESOLVED`。
- `async_outbox_message`：以 UUID `message_id` 为主键，保存事件名、版本、业务单号、路由、最小 JSON 载荷、发布次数和状态。
- `async_consumed_message`：以消息 ID + 消费者名为主键，作为消费事务内的幂等占位。
- `async_message_trace`：记录 Outbox 创建、发布、重试、消费、重复和死信阶段。
- `async_failure_record`：保存达到发布/消费重试上限的载荷、原因、发生次数及人工处理状态、处理人和备注。

## 2. 已确认、待实现

以下只是设计基线，不代表表已经存在：

- 库存：`inventory_balance`、`inventory_ledger` 和仓库 + 库位 + SKU 安全库存配置已实现。
- 单据：采购入库单、销售出库单、仓库间调拨单和静态盘点单及其明细已实现。
- 可靠性：Outbox、MQ 消费幂等记录、追踪、异常补偿记录和安全库存预警已实现；通用 HTTP 幂等请求记录仍待实现。

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
- MySQL 镜像是否固定到明确补丁版本。
- `warehouse_location` 的普通仓库索引是否与复合唯一索引重复，实施库存查询后用 `EXPLAIN` 决定。
- 审计表是否需要数据库账号层面的 INSERT/SELECT 权限限制和归档策略。
