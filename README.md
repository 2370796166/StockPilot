# StockPilot

StockPilot 是面向中小型制造或电商企业的智能仓储与库存管理平台。当前包含模块化单体后端和 `frontend` 目录下的 Vue 3 管理后台。管理后台已接通登录、权限路由、基础资料、采购、销售、库存、调拨、盘点、用户和角色的真实后端接口。

项目需求、架构和进度以以下文档为准：

- `docs/requirements.md`：MVP范围和产品规则
- `docs/architecture.md`：当前代码结构与后续模块边界
- `docs/inventory-rules.md`：未来库存实现必须遵守的不变量
- `docs/database.md`：实际数据库状态和待实现基线
- `docs/rabbitmq-reliability.md`：RabbitMQ 与业务事件故障边界、补偿和生产化计划
- `docs/progress.md`：当前验证结果和下一阶段限制

## 当前能力

- Java 17、Spring Boot 3、Maven
- MyBatis-Plus 与 MySQL 8
- 统一响应、参数校验、全局异常与业务错误码
- OpenAPI / Swagger UI
- 同时检查应用与 MySQL 的健康接口
- Docker Compose MySQL、可选 Redis 查询缓存与初始化脚本
- JUnit 5 可运行测试
- 仓库、库位、商品分类、SKU、供应商管理
- SKU、仓库单键详情可选Redis缓存，支持短期空值和MySQL降级
- 条件分页、详情、修改及启用/停用
- Flyway 数据库版本管理和数据库唯一约束
- Spring Security、BCrypt、JWT Access Token 和数据库驱动的接口权限
- 用户、角色、权限、关联管理及关键安全操作审计
- 库存余额和库存流水分页查询，库存维度为仓库、库位、SKU
- 内部零库存初始化用例；没有公开库存写接口或手工调整能力
- 采购入库单草稿、编辑、提交、审核和执行入库闭环
- 采购入库完成时在同一事务更新库存余额、追加流水并完成单据
- 销售出库单草稿、编辑、原子冻结、审核、实际出库和取消释放闭环
- 销售冻结使用数据库条件更新防止负库存，冻结/出库/释放分别追加不可变流水
- 仓库间调拨草稿、提交冻结、审核、源仓调出、在途确认和目标仓收货闭环
- 在途库存使用独立事实表，不计入源仓或目标仓实际库存；调出和调入分别追加流水
- 静态盘点保存账面快照并锁定仓库/库位/SKU维度，实盘、审核后一次性调整库存并写盘点元数据流水
- 入库完成和出库完成在业务事务内写入简化 Outbox，事务回滚不会产生成功事件
- RabbitMQ 使用持久消息、mandatory、发布确认、有限发布/消费重试、死信队列、消息追踪和异常补偿记录
- 安全库存消费者按仓库、库位、SKU 查询 MySQL 最新可用量，重复投递由消费记录唯一键保证幂等

## 基础资料接口

五类资源统一提供创建、修改、详情、分页及状态变更，不提供删除接口：

| 资源 | 路径 |
|---|---|
| 仓库 | `/api/master-data/warehouses` |
| 库位 | `/api/master-data/locations` |
| 商品分类 | `/api/master-data/categories` |
| SKU | `/api/master-data/skus` |
| 供应商 | `/api/master-data/suppliers` |

创建使用 `POST /资源路径`，修改使用 `PUT /资源路径/{id}`，详情使用 `GET /资源路径/{id}`，分页使用 `GET /资源路径?page=1&size=20`，状态变更使用 `PATCH /资源路径/{id}/status`。修改和状态变更必须携带响应中的 `version`，防止覆盖并发修改。

分页支持 `code`、`name`、`status` 条件，单页最大 100。库位还支持 `warehouseId`，SKU 还支持 `categoryId`。编码长度为 2–32，以字母开头，只允许字母、数字、下划线和连字符，保存时统一转成大写。

## 认证与权限

登录接口为 `POST /api/auth/login`，当前用户与权限接口为 `GET /api/auth/me`。除健康检查、登录及 OpenAPI 页面外，接口必须携带 `Authorization: Bearer <accessToken>`。缺少或无效令牌统一返回 HTTP 401，无权限统一返回 HTTP 403。

安全管理接口位于 `/api/security`，包含用户、角色、权限、关联管理和审计日志查询。基础资料查询需要 `MASTER_DATA_READ`，写操作需要 `MASTER_DATA_WRITE`；用户角色和角色权限分配需要系统管理员持有的 `SECURITY_GRANT`。未明确列入安全规则的新接口默认拒绝访问。

最终 MVP 采用 `ADMIN`、`OPERATOR`、`AUDITOR` 三个默认角色。Flyway `V0.3.2` 已将早期六角色种子收敛为这三个角色；角色名称不代表采购、出库等业务已经实现。具体权限基线见 `docs/requirements.md` 和 `docs/decisions.md`。

仓库不保存默认账号或 JWT 密钥。首次启动可临时设置 `JWT_SECRET`、`BOOTSTRAP_ADMIN_USERNAME`、`BOOTSTRAP_ADMIN_PASSWORD` 创建管理员；创建后应清除两个引导账号变量。JWT 密钥至少 32 字符并持续由部署环境提供。本阶段仅提供默认 60 分钟的 Access Token，不提供 Refresh Token。

## 库存查询接口

库存接口需要 `INVENTORY_READ` 权限，当前只提供分页查询：

| 资源 | 方法与路径 |
|---|---|
| 库存余额 | `GET /api/inventory/balances` |
| 库存流水 | `GET /api/inventory/ledgers` |

余额支持按 `warehouseId`、`locationId`、`skuId` 过滤；流水还支持 `businessType`、`businessNo`。分页参数为 `page`、`size`，单页最多 100 条。当前没有 POST、PUT、PATCH 或 DELETE 库存接口，不能手工调整库存。

## 采购入库接口

采购入库接口位于 `/api/inbound/purchase-receipts`：

| 动作 | 方法与路径 | 权限 |
|---|---|---|
| 分页/详情 | `GET /api/inbound/purchase-receipts`、`GET /{id}` | `PURCHASE_RECEIPT_READ` |
| 创建草稿 | `POST /api/inbound/purchase-receipts` | `PURCHASE_RECEIPT_WRITE` |
| 编辑草稿 | `PUT /api/inbound/purchase-receipts/{id}` | `PURCHASE_RECEIPT_WRITE` |
| 提交 | `POST /api/inbound/purchase-receipts/{id}/submit` | `PURCHASE_RECEIPT_WRITE` |
| 审核 | `POST /api/inbound/purchase-receipts/{id}/approve` | `PURCHASE_RECEIPT_APPROVE` |
| 执行入库 | `POST /api/inbound/purchase-receipts/{id}/complete` | `PURCHASE_RECEIPT_COMPLETE` |

状态固定为 `DRAFT → SUBMITTED → APPROVED → COMPLETED`。业务单号创建后不可修改；只有草稿可以整体替换明细。执行入库按单据行加锁防重复，成功后不能编辑或再次执行。

## 销售出库接口

销售出库接口位于 `/api/outbound/sales-orders`：

| 动作 | 方法与路径 | 权限 |
|---|---|---|
| 分页/详情 | `GET /api/outbound/sales-orders`、`GET /{id}` | `SALES_OUTBOUND_READ` |
| 创建/编辑草稿 | `POST /api/outbound/sales-orders`、`PUT /{id}` | `SALES_OUTBOUND_WRITE` |
| 冻结库存 | `POST /api/outbound/sales-orders/{id}/reserve` | `SALES_OUTBOUND_WRITE` |
| 审核 | `POST /api/outbound/sales-orders/{id}/approve` | `SALES_OUTBOUND_APPROVE` |
| 实际出库 | `POST /api/outbound/sales-orders/{id}/complete` | `SALES_OUTBOUND_COMPLETE` |
| 取消并释放 | `POST /api/outbound/sales-orders/{id}/cancel` | `SALES_OUTBOUND_WRITE` |

主流程为 `DRAFT → RESERVED → APPROVED → COMPLETED`；`RESERVED` 或 `APPROVED` 可转为 `CANCELLED`。冻结、出库、释放均通过数据库原子条件更新，并与单据状态和对应流水处于同一事务。

## 仓库间调拨接口

调拨接口位于 `/api/transfers`。主流程为
`DRAFT → SUBMITTED → APPROVED → OUTBOUND_COMPLETED → IN_TRANSIT → COMPLETED`。
提交时冻结源库存；`SUBMITTED` 或 `APPROVED` 可取消并释放；调出后禁止普通取消。

| 动作 | 方法与路径 | 权限 |
|---|---|---|
| 分页/详情 | `GET /api/transfers`、`GET /{id}` | `TRANSFER_READ` |
| 创建/编辑 | `POST /api/transfers`、`PUT /{id}` | `TRANSFER_WRITE` |
| 提交冻结/取消 | `POST /{id}/submit`、`POST /{id}/cancel` | `TRANSFER_WRITE` |
| 审核 | `POST /{id}/approve` | `TRANSFER_APPROVE` |
| 源仓调出/进入运输 | `POST /{id}/dispatch`、`POST /{id}/start-transit` | `TRANSFER_OUTBOUND` |
| 目标仓收货 | `POST /{id}/receive` | `TRANSFER_INBOUND` |

## 库存盘点接口

盘点接口位于 `/api/inventory-counts`。采用静态盘点，主流程为
`DRAFT → COUNTING → SUBMITTED → APPROVED → ADJUSTED`。创建盘点单时保存快照并建立维度锁；从创建成功到调整完成，普通库存业务不能修改被盘点维度。

| 动作 | 方法与路径 | 权限 |
|---|---|---|
| 分页/详情 | `GET /api/inventory-counts`、`GET /{id}` | `INVENTORY_COUNT_READ` |
| 创建/开始/录入/提交 | `POST /api/inventory-counts`、`POST /{id}/start`、`PUT /{id}/results`、`POST /{id}/submit` | `INVENTORY_COUNT_WRITE` |
| 审核 | `POST /{id}/approve` | `INVENTORY_COUNT_APPROVE` |
| 调整 | `POST /{id}/adjust` | `INVENTORY_COUNT_ADJUST` |

## 环境要求

- JDK 17 或更高版本（项目按 Java 17 编译）
- Maven 3.9+
- Node.js 20+ 与 npm（管理后台）
- Docker Desktop 或兼容 Docker Compose 的环境

确认环境：

```bash
java -version
mvn -version
npm -version
docker compose version
```

## 启动步骤

### 1. 启动 MySQL、RabbitMQ 和可选 Redis

在项目根目录执行：

```bash
docker compose up -d mysql rabbitmq
docker compose ps
```

等待 `stockpilot-mysql` 和 `stockpilot-rabbitmq` 状态变为 `healthy`。RabbitMQ AMQP/管理端口默认映射为 `5673`/`15673`。首次创建数据卷时，MySQL 会执行 `docker/mysql/init/001-init.sql`；应用启动时 Flyway 自动执行后续迁移。

需要启用 SKU、仓库详情缓存时，再启动 Redis 并打开缓存开关：

```powershell
docker compose up -d redis
$env:CACHE_ENABLED="true"
```

Redis 默认映射到 `localhost:6380`。缓存关闭或 Redis 不可用时，详情查询直接降级到 MySQL；Redis 不参与库存扣减、库存判断或单据幂等。

异步消息默认关闭。启动 RabbitMQ 后设置 `$env:RABBITMQ_ENABLED="true"` 再启动应用即可启用 Outbox 发布和消费者。MQ 不可用时，已经成功提交的入库/出库业务不回滚；Outbox 保持待发布并有限重试，达到 8 次后转为失败和人工补偿记录。RabbitMQ 交付允许重复，消费者必须保持幂等。

默认开发连接信息：

| 配置 | 默认值 |
|---|---|
| 地址 | `localhost:3307`（容器内仍为 `3306`） |
| 数据库 | `stockpilot` |
| 用户 | `stockpilot` |
| 密码 | `stockpilot_dev` |

默认密码仅用于本地开发，不能用于生产环境。

### 2. 运行测试

```bash
mvn -s .mvn/settings.xml clean test
```

真实 MySQL 并发和事务回滚集成测试需要本地 Compose MySQL 正常运行：

```bash
mvn -s .mvn/settings.xml -Pmysql-it verify
```

真实 RabbitMQ 发布、重试和死信测试还需要 Compose MySQL 与 RabbitMQ 均健康：

```bash
mvn -s .mvn/settings.xml -Prabbit-it verify
```

### 3. 启动后端

```powershell
$env:JWT_SECRET="替换为至少32字符的随机密钥"
mvn -s .mvn/settings.xml spring-boot:run
```

如果本地数据库配置不同，可以覆盖环境变量：

```powershell
$env:DB_URL="jdbc:mysql://localhost:3307/stockpilot?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
$env:DB_USERNAME="stockpilot"
$env:DB_PASSWORD="stockpilot_dev"
$env:JWT_SECRET="替换为至少32字符的随机密钥"
mvn -s .mvn/settings.xml spring-boot:run
```

仓库内的 `.mvn/settings.xml` 将 Maven 本地缓存放在项目的 `.m2` 目录，并强制使用 Maven Central HTTPS，可避免全局 Maven 配置指向无写权限目录或旧 HTTP 镜像。该缓存目录已加入 `.gitignore`。

### 4. 启动管理后台

后端运行后，在新终端执行：

```powershell
cd frontend
npm install
npm run dev
```

开发服务器默认位于 `http://localhost:5173`，并将同源 `/api` 请求代理至 `http://localhost:8080`。生产交付前执行：

```powershell
npm run lint
npm run typecheck
npm run build
```

管理后台包含登录、布局、权限路由、基础资料、采购入库、销售出库、库存余额、库存流水、调拨、盘点、用户和角色页面。首页没有后端统计聚合接口，因此不展示虚构统计数据。

## 验证

健康检查：

```text
GET http://localhost:8080/api/health
```

MySQL 正常时返回：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {
    "status": "UP",
    "database": "UP"
  },
  "timestamp": "2026-08-13T00:00:00Z"
}
```

接口文档：

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>

## 配置项

| 环境变量 | 默认值 | 含义 |
|---|---|---|
| `SERVER_PORT` | `8080` | 后端端口 |
| `DB_URL` | 本地 `stockpilot` JDBC URL | 数据库地址 |
| `DB_USERNAME` | `stockpilot` | 数据库用户 |
| `DB_PASSWORD` | `stockpilot_dev` | 数据库密码 |
| `MYSQL_HOST_PORT` | `3307` | Docker MySQL 映射到宿主机的端口 |
| `CACHE_ENABLED` | `false` | 是否启用 SKU、仓库详情 Redis 缓存 |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6380` | Redis 地址 |
| `REDIS_HOST_PORT` | `6380` | Docker Redis 映射到宿主机的端口 |
| `RABBITMQ_ENABLED` | `false` | 是否启用 Outbox 发布调度和 RabbitMQ 消费者 |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5673` | RabbitMQ AMQP 地址 |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | `stockpilot` / `stockpilot_dev` | 本地 RabbitMQ 凭据 |
| `RABBITMQ_VHOST` | `/stockpilot` | RabbitMQ virtual host |
| `RABBITMQ_PUBLISHER_MAX_ATTEMPTS` | `8` | Outbox 发布最大尝试次数 |
| `RABBITMQ_CONSUMER_MAX_ATTEMPTS` | `3` | 单次消息消费最大尝试次数 |
| `CACHE_KEY_PREFIX` | `stockpilot:v1` | 缓存 Key 和格式版本前缀 |
| `CACHE_SKU_TTL` / `CACHE_WAREHOUSE_TTL` | `30m` / `30m` | 正常详情缓存 TTL |
| `CACHE_NULL_TTL` | `60s` | 不存在数据的短期空值 TTL |
| `JWT_SECRET` | 无 | JWT HMAC 密钥，至少 32 字符 |
| `JWT_ACCESS_TOKEN_MINUTES` | `60` | Access Token 有效分钟数 |
| `BOOTSTRAP_ADMIN_USERNAME` | 无 | 仅首次引导管理员时设置 |
| `BOOTSTRAP_ADMIN_PASSWORD` | 无 | 仅首次引导管理员时设置 |

## 包结构

```text
com.stockpilot
├─ common       统一响应、错误码和异常处理
├─ cache        Redis基础资料缓存、版本化序列化与不可用降级
├─ config       MyBatis-Plus、OpenAPI等配置
├─ health       健康检查的Controller、应用服务和Mapper
├─ masterdata   仓库、库位、分类、SKU和供应商
├─ inventory    库存余额、库存流水和内部库存变更服务
├─ inbound      采购入库闭环
├─ outbound     销售冻结、审核、出库和取消释放
├─ transfer     仓库间调拨和独立在途库存
├─ inventorycount 静态盘点、差异审核和库存调整
├─ messaging    事务性Outbox、RabbitMQ发布/消费、重试、死信和追踪
├─ alert        安全库存规则与低库存预警状态
└─ security     登录、JWT、用户/角色/权限、RBAC和审计
```

Controller 只调用应用服务，不直接调用 Mapper。采购、出库、调拨和盘点模块均通过库存应用服务修改余额。

## 停止基础设施

停止容器但保留数据库数据：

```bash
docker compose down
```

本项目不会在普通启动流程中自动删除 MySQL 数据卷。
