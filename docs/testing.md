# StockPilot 核心业务端到端测试

## 目标与边界

核心端到端测试验证真实 HTTP 接口、Spring Security、Service、本地事务、MyBatis、Flyway 和 MySQL 之间的完整调用链，不通过 Service 直调或手工 SQL 跳过业务规则。

测试入口为：

```text
src/test/java/com/stockpilot/e2e/CoreBusinessE2EMySqlIT.java
```

本测试不增加业务功能，也不替代采购、销售、调拨、盘点和消息模块已有的事务、并发及故障注入集成测试。

## 覆盖流程

单条场景按以下顺序执行：

1. 使用测试环境引导管理员登录，并通过 `/api/auth/me` 验证 `ADMIN` 角色。
2. 通过安全管理接口创建一个没有角色和权限的用户，并验证该用户可以登录但没有业务授权。
3. 通过基础资料接口创建仓库、库位、SKU 和供应商。
4. 创建采购入库草稿，依次提交、审核和完成。
5. 在采购审核阶段使用无权限用户调用审核接口，断言返回 HTTP 403，并再次查询单据确认状态和版本均未变化。
6. 查询采购完成后的库存余额和 `PURCHASE_RECEIPT` 流水。
7. 重复调用采购完成接口，断言返回 HTTP 409，余额和流水数量不变。
8. 创建销售出库草稿并冻结库存，查询冻结后的余额和 `OUTBOUND_FREEZE` 流水。
9. 使用相同版本重复冻结，断言返回 HTTP 409，冻结量和流水数量不变。
10. 审核并完成销售出库，查询最终余额及 `OUTBOUND_SHIP` 流水。
11. 重复调用销售完成接口，断言返回 HTTP 409，最终余额和流水数量不变。
12. 最终重新查询采购单和销售单，确认两者均为 `COMPLETED`。

关键数量采用以下固定场景，便于失败时人工复核：

| 阶段 | actual | available | frozen |
|---|---:|---:|---:|
| 采购入库 10 前 | 0 | 0 | 0 |
| 采购完成后 | 10 | 10 | 0 |
| 销售冻结 4 后 | 10 | 6 | 4 |
| 销售出库完成后 | 6 | 6 | 0 |

每个余额断言都同时校验：

```text
actual = available + frozen
actual >= 0
available >= 0
frozen >= 0
```

采购、冻结和出库流水还会验证三种数量的前值、变化值、后值，以及余额版本链 `0 → 1 → 2 → 3`。

## 测试数据与清理

测试启动前自动执行以下操作：

1. 删除上次异常退出可能遗留的 `stockpilot_core_e2e_it` 数据库。
2. 创建新的 UTF-8 MySQL 专用数据库。
3. 从空 schema 执行当前全部 Flyway 迁移。
4. 使用测试专用配置引导管理员；Redis 缓存和 RabbitMQ 消费保持关闭。
5. 通过真实 HTTP API 创建本次业务数据。

测试结束后自动删除 `stockpilot_core_e2e_it`。即使前一次进程异常中断，下一次初始化也会先清理同名专用数据库，因此固定测试编码和单号可以重复使用。测试不会修改 `stockpilot` 开发库，也不要求人工清表或直接插入库存。

默认连接 Compose MySQL：

```text
jdbc:mysql://localhost:3307/
root / root_dev_only
```

如本地集成测试管理员连接不同，可使用现有环境变量：

```powershell
$env:STOCKPILOT_IT_ADMIN_URL="jdbc:mysql://localhost:3307/?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
$env:STOCKPILOT_IT_ADMIN_USER="root"
$env:STOCKPILOT_IT_ADMIN_PASSWORD="替换为本地测试密码"
```

## 执行方法

先确认 MySQL 正常：

```powershell
docker compose up -d mysql
docker compose ps
```

只运行核心 HTTP 端到端场景：

```powershell
mvn -s .mvn/settings.xml -Pmysql-it "-Dit.test=CoreBusinessE2EMySqlIT" failsafe:integration-test failsafe:verify
```

运行全部常规测试和 MySQL 集成测试：

```powershell
mvn -s .mvn/settings.xml clean test
mvn -s .mvn/settings.xml -Pmysql-it verify
```

`mysql-it` profile 会自动发现 `*MySqlIT`，因此核心端到端测试已纳入现有 MySQL 集成测试体系。若 Windows 上已有应用进程占用后端 JAR，`clean` 或 `verify` 的打包阶段可能在测试前失败；应先由操作者确认并停止该应用进程，不得把非 clean 结果描述为干净构建通过。

## 失败诊断

HTTP 调用失败时，断言会输出：

- HTTP 方法和路径；
- 预期与实际状态码；
- 完整 API 响应体。

场景内任一断言失败时，还会追加专用测试库中的只读诊断快照：

- 采购单号、状态和版本；
- 销售单号、状态和版本；
- 库存三数量和余额版本；
- 流水业务类型、业务单号、三数量前值/变化值/后值及版本链。

诊断 SQL 只读取专用测试库，不用于准备业务状态或修正测试数据。

## MQ 异步测试约定

核心采购和销售库存正确性必须在 MySQL 本地事务提交时成立，不依赖 RabbitMQ，因此本核心端到端场景显式关闭消息消费者，不使用固定 `sleep`。

RabbitMQ 发布、重试和死信由 `MessagingRabbitIT` 独立验证。异步断言必须使用带截止时间的条件轮询；测试拓扑应与正在运行的开发应用隔离，避免两个消费者竞争固定业务队列。执行命令见 README 中的 `rabbit-it` 说明。

## 2026-08-24 本次真实结果

执行环境：Amazon Corretto JDK 17.0.15、Maven 3.9.4、Compose MySQL 8.0。

执行命令：

```powershell
mvn -s .mvn/settings.xml -Pmysql-it "-Dit.test=CoreBusinessE2EMySqlIT" failsafe:integration-test failsafe:verify
```

结果：测试专用数据库从空 schema 成功执行 10 个 Flyway 迁移至 `0.9.0`；核心 HTTP 端到端测试 1 个，0 失败、0 错误、0 跳过，构建成功。测试后专用数据库已自动删除。

随后执行常规测试和全部 MySQL 集成测试：

```powershell
mvn -s .mvn/settings.xml test
mvn -s .mvn/settings.xml -Pmysql-it "-Dit.test=*MySqlIT" failsafe:integration-test failsafe:verify
```

常规测试 83 个全部通过；MySQL 集成测试 31 个全部通过，其中包含核心 HTTP 端到端 1、采购 4、销售 9、调拨 7、盘点 7、消息事务 3，均为 0 失败、0 错误、0 跳过。六个专用测试库均从空 schema 迁移至 `0.9.0` 并在测试后删除。

`mvn -s .mvn/settings.xml clean test` 已执行但未进入编译和测试阶段：PID 17264 的既有后端进程占用 `target/stockpilot-backend-0.0.1-SNAPSHOT.jar`，Maven Clean Plugin 无法删除该文件。本轮未擅自停止该进程，因此不能声称干净构建通过；失败后补跑的非 clean 常规测试仍为 83 个全部通过。

本结果不代表浏览器 UI 自动化已完成，也不代表未经本测试覆盖的调拨、盘点或人工补偿管理流程完成了端到端验证。
