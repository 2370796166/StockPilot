# 数据库与 SQL 说明

MySQL 8 是库存唯一权威来源。默认数据库名 `stockpilot`，使用 `utf8mb4` / `utf8mb4_0900_ai_ci`。宿主机连接端口默认 `3307`，详见 [Docker 手册](../operations/docker.md)。

## 初始化与迁移

| SQL 入口 | 执行者 | 作用 |
|---|---|---|
| `docker/mysql/init/001-init.sql` | MySQL 镜像，首次空数据卷时 | 建立历史骨架 `schema_version`，不创建完整业务表 |
| `src/main/resources/db/migration/V*.sql` | 后端启动时 Flyway | 建立业务表、角色权限、数量约束和索引 |

Compose 通过 MySQL 镜像创建数据库与开发账号。自建 MySQL 时需要先建库和授权，再启动应用；空 schema 的业务建表由 Flyway 完成。不要将所有 SQL 拼接为重复的初始化脚本，也不要逐个手动导入迁移。

当前共 11 个迁移，最新版本 `0.9.1`：

| 版本 | 内容 |
|---|---|
| 0.2.0 | 仓库、库位、分类、SKU、供应商 |
| 0.3.0 | 用户、角色、权限、关联和安全审计 |
| 0.3.1 | 独立授权关系管理权限 |
| 0.3.2 | 默认角色收敛为 ADMIN、OPERATOR、AUDITOR |
| 0.4.0 | 库存余额、不可变流水和库存读取权限 |
| 0.5.0 | 采购入库单和明细 |
| 0.6.0 | 销售出库单和明细 |
| 0.7.0 | 调拨单、明细和独立在途事实 |
| 0.8.0 | 静态盘点、维度锁与流水盘点元数据 |
| 0.9.0 | Outbox、消费幂等、轨迹、失败记录和安全库存 |
| 0.9.1 | 仓库条件下的库存余额分页索引 |

配置保留 `baseline-on-migrate=true` 和 `baseline-version=0.1.0`，兼容早期骨架库。非空库必须确认为本项目骨架库，不能将任意已有业务库视作可直接接入的库。正式版本以 `flyway_schema_history` 为准，`schema_version` 只是历史骨架记录。已有迁移不可修改，数据库变更应新增迁移。

## 核心表与约束

| 表组 | 职责和关键约束 |
|---|---|
| warehouse、warehouse_location、product_category、sku、supplier | 编码唯一、启停状态、版本；库位属于仓库，SKU 可关联单层分类 |
| sys_user、sys_role、sys_permission、sys_user_role、sys_role_permission | BCrypt 密码、RBAC；授权关联使用复合主键防重复 |
| audit_log | 登录和安全管理审计；不替代库存流水 |
| inventory_balance | 仓库 + 库位 + SKU 唯一；数量 `DECIMAL(19,4)`；非负且 actual = available + frozen |
| inventory_ledger | 业务动作唯一、三数量前值/差量/后值、操作人、版本链；应用仅追加和查询 |
| purchase_receipt、purchase_receipt_line | 单号唯一、明细数量大于零、单据与明细仓库一致 |
| sales_outbound_order、sales_outbound_line | 五状态闭环、明细维度唯一、冻结与出库事实 |
| stock_transfer_order、stock_transfer_line、stock_transfer_transit | 源目标仓不同、两端库位归属、独立在途数量与收货约束 |
| inventory_count_order、inventory_count_line、inventory_count_scope_lock | 快照、实盘差异、仓库/库位/SKU维度锁唯一 |
| safety_stock_rule、low_stock_alert | 按库存维度的阈值和当前预警状态 |
| async_outbox_message、async_consumed_message、async_message_trace、async_failure_record | 事务事件、消息幂等、投递轨迹与失败补偿记录 |

数量使用 Java `BigDecimal`，不使用浮点数作为库存计算依据。外键和 CHECK 为数据库最终防线；核心写操作还需事务、条件更新和业务幂等，见 [库存规则](../business/inventory-rules.md)。

## 运维约定

- MySQL 数据保存在命名卷；重启不会重放初始化脚本，也不会改变原密码。
- 不修改或删除 Flyway 历史，不通过直连 SQL 任意改库存或流水，不用删除数据卷修复迁移失败。
- 开发数据库账号具有库级权限；应用层流水不可变不能替代生产数据库的账号权限隔离。
- 备份与恢复需要包含完整业务数据和迁移历史；生产迁移账号与运行账号拆分尚未实施。
- AI 只使用既有表进行受限查询，没有独立 AI 表或新的迁移。
- 本次文档整理未修改 SQL；真实 MySQL 迁移和空数据卷初始化本次未验证。
