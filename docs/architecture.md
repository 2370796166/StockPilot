# 后端架构上下文

## 当前已实现

### 技术栈与版本

- 单 Maven JAR：`com.stockpilot:stockpilot-backend:0.0.1-SNAPSHOT`
- Java 编译目标 17；检查环境实际 Maven 运行在 JDK 25
- Spring Boot 3.2.3
- MyBatis-Plus Spring Boot 3 Starter 3.5.5
- MySQL Connector/J：由 Spring Boot 依赖管理
- springdoc-openapi 2.0.4
- JUnit 5 / Spring Boot Test：由 Spring Boot 依赖管理
- Flyway 9.22.3（含 MySQL 支持模块）
- MySQL Docker 镜像：`mysql:8.0`，未固定补丁版本

### 当前目录结构

```text
StockPilot/
├─ pom.xml
├─ README.md
├─ docker-compose.yml
├─ .mvn/settings.xml
├─ docker/mysql/init/001-init.sql
├─ src/main/java/com/stockpilot/
│  ├─ StockPilotApplication.java
│  ├─ common/api
│  ├─ common/exception
│  ├─ config
│  ├─ health/{controller,application,infrastructure/mapper,vo}
│  ├─ masterdata/{warehouse,location,category,sku,supplier,...}
│  ├─ inventory/{controller,application,domain,infrastructure,request,vo}
│  ├─ inbound/{controller,application,domain,infrastructure,request,vo}
│  └─ security/{controller,application,auth,audit,domain,infrastructure,...}
├─ src/main/resources/db/migration
└─ src/test/java/com/stockpilot/{health,masterdata,inventory,inbound,security}
```

当前不是 Maven 多模块工程，已在单模块内按 `masterdata` 业务包实现基础资料。是否拆 Maven 子模块待业务复杂度增长后再评估。

### 当前 Java 包与调用链

```text
HealthController
→ HealthApplicationService
→ DatabaseHealthMapper
→ SELECT 1
```

- `common.api`：`ApiResponse`、`ErrorCode`、`CommonErrorCode`。
- `common.exception`：`BusinessException` 和 `GlobalExceptionHandler`。
- `config`：Mapper 扫描和 OpenAPI 元数据。
- `health`：真实检查 MySQL 连接。
- `masterdata`：仓库、库位、单层商品分类、SKU、供应商的 Controller、Application Service、Entity、Mapper、Request 和 VO。
- `security`：登录、JWT、用户/角色/权限与关联、双层 RBAC、BCrypt 和审计日志。
- `inventory`：库存余额、库存流水、只读分页接口、库存不变量和内部差量变更事务；不包含采购、销售、调拨或盘点流程。
- `inbound`：采购入库单创建/编辑、提交、审核和执行入库；仅通过库存应用服务修改余额与追加流水。

默认角色模型已与最终 MVP 对齐：`ADMIN`、`OPERATOR`、`AUDITOR`。安全框架仍支持动态维护角色和权限，但后续业务设计默认只围绕这三个角色扩展权限。

基础资料 Controller 只调用 Application Service。标准资料复用泛型 CRUD 应用服务；库位和 SKU 使用专用服务实现仓库/分类关联规则。数据库唯一约束承担并发重复创建的最终保障，应用层将 `DuplicateKeyException` 转换为 409 业务错误。

认证链路为：

```text
AuthenticationController
→ AuthenticationApplicationService
→ UserMapper + BCryptPasswordEncoder
→ JwtService
→ AuditService
```

受保护请求链路为：

```text
JwtAuthenticationFilter
→ JwtService验证HS256签名和过期时间
→ DatabaseUserDetailsService重新查询MySQL用户状态与有效权限
→ URL授权规则 + @PreAuthorize
→ Controller → Application Service → Mapper
```

JWT 只包含用户 ID、用户名、签发时间和过期时间，不包含角色权限，不使用服务端会话。用户、角色或权限停用后，已有 Token 的访问能力会因每次请求重载数据库而立即变化。

### 已实现基础设施

- 统一响应字段：`code`、`message`、`data`、`timestamp`。
- 基础资料 Request 使用 Bean Validation；全局处理请求体、字段、方法参数和业务异常。
- 业务异常、常用错误码和兜底系统异常处理。
- MyBatis-Plus 下划线转驼峰、数据库自增 ID 默认策略。
- OpenAPI JSON 与 Swagger UI。
- HikariCP 数据源配置。
- Docker Compose MySQL、数据卷、健康检查和首次初始化脚本。
- 项目内 Maven 设置，缓存位于 `.m2/repository`。
- MyBatis-Plus 已注册乐观锁和 MySQL 分页插件；基础资料及安全主体表使用 `@Version`。
- 用户角色、角色权限采用事务内“校验 → 删除旧关系 → 批量写入新关系”的整体替换。
- URL 规则与方法级注解实行双层授权，未明确配置的新接口默认拒绝。
- 库存 Controller 仅提供余额和流水 GET 分页查询，返回 VO；余额变更只允许通过 `InventoryMutationApplicationService`，更新余额与新增流水处于同一事务。
- 库存余额 Mapper 不继承通用 CRUD，只提供受限插入、查询和带预期版本的条件更新；流水 Mapper 只提供新增和查询。
- 采购入库使用受限 Mapper 和应用服务状态机；执行入库以 `SELECT ... FOR UPDATE` 锁定单据，并在同一事务更新库存、写流水和完成单据。

### 配置和环境

当前只有 `application.yml`，没有 dev/test/prod profile：

- 应用端口默认 `8080`。
- MySQL 宿主端口默认 `3307`，避免占用本机已有 `3306`。
- 数据库 URL、用户名、密码支持环境变量覆盖。
- Compose 端口支持 `MYSQL_HOST_PORT` 覆盖。

当前明文值是本地示例凭据，不能用于生产。Flyway 使用 `0.1.0` baseline 兼容已有骨架数据库。尚未建立独立测试配置或自动化数据库集成测试。

### 测试结构

现有测试包含 standalone MockMvc/Mockito 单元测试和安全 WebMvc 上下文测试，覆盖健康检查、基础资料规则、登录分支、BCrypt、JWT 签名与过期、401/403、停用用户旧 Token、默认拒绝、授权越权和采购入库状态机。`mysql-it` Maven 配置会在 Compose MySQL 中创建专用临时库，验证采购入库并发与事务回滚，完成后删除该测试库。

2026-08-14 采购入库实现后实际编译 105 个主源码、17 个测试源码；常规测试 55 个，真实 MySQL 集成测试 4 个。项目仍未使用 Testcontainers，集成测试依赖本地 Compose MySQL。

## 模块状态与后续计划

### 模块化单体业务包

计划按业务能力增加：

- `masterdata`：仓库、库位、商品分类、SKU、供应商（已实现）。
- `security`：登录、JWT、RBAC、用户/角色/权限管理和安全审计（已实现）。
- `inventory`：库存余额、条件更新和流水（已实现）。
- `inbound`：采购入库（已实现）。
- `outbound`：销售冻结与出库。
- `alert`：安全库存预警和 MQ 消费幂等。

依赖方向计划为：基础资料被库存及单据模块引用；入库/出库调用库存公开服务；库存不反向依赖单据模块；异步预警不能参与核心库存事务。

### 分层和对象职责

- Controller：HTTP 入参、Bean Validation、权限声明、调用 Application Service、返回 VO。
- Application Service：组织用例和本地事务；可直接调用本模块 Mapper。
- Entity：持久化记录，可承载少量真实状态行为，不直接返回前端。
- Request/DTO：接口输入和查询条件。
- Command：仅用于复杂、可复用的写业务意图，不与 Request 机械一一复制。
- VO：接口输出和组合/计算字段。
- Mapper：数据访问和必要的原子条件 SQL，不编排用例。
- Domain Service：只在存在跨对象核心规则时创建，禁止空壳 DDD。

### 后续基础设施

Redis、RabbitMQ、库存幂等、消息消费记录及更完整的集成测试均尚未实现。安全模块使用无状态 Spring Security：JWT 只携带用户身份与有效期，每次请求从 MySQL 重载用户状态和有效权限；密码使用 BCrypt，密钥由外部环境配置，方法级权限拒绝统一返回 403。

## 当前架构限制与风险

- 项目仍是单 Maven 模块；模块边界依赖包结构和调用纪律，没有编译期模块隔离。
- `application.yml` 没有 dev/test/prod profile，测试也没有独立数据库配置。
- JWT 为项目内手写 HS256 实现，没有 `jti`、Refresh Token 或单 Token 撤销能力。
- `audit_log` 是可查询的基础安全审计表，但数据库权限层尚未限制其 UPDATE/DELETE，也不等同于库存事实流水。
- 安全管理写操作在事务内记录成功审计；事务失败会连同审计一起回滚，因此不能据此声称所有失败写操作都有审计记录。
- 基础资料通用服务直接依赖 MyBatis-Plus `BaseMapper`，适合当前简单 CRUD；库存模块不得复制这种通用更新方式。
- 角色种子已通过 `V0.3.2` 从早期六角色收敛为三个 MVP 角色；采购入库在此基础上增加四个独立权限。
