# StockPilot

面向中小型制造、电商企业的仓储与库存管理平台，包含 Java 17 / Spring Boot 3 后端与 Vue 3 管理后台。支持基础资料、用户权限、采购入库、销售冻结与出库、多仓调拨、静态盘点、库存流水，以及可选的 Redis 查询缓存、RabbitMQ 安全库存预警和只读 AI 助手。

库存数量、单据状态和流水以 MySQL 为准，核心写操作在本地事务中完成。后端为单 Maven 模块、单 Spring Boot 部署单元。

## 本地启动

完整步骤见 [启动手册](docs/operations/startup.md)。以下命令使用 PowerShell；需要 JDK 17、Maven 3.9+、Node.js 20+、Docker Compose v2。

```powershell
git clone https://github.com/2370796166/StockPilot.git
cd StockPilot
Copy-Item .env.example .env
```

编辑 `.env`：替换 `JWT_SECRET` 为至少 32 字符的随机密钥；首次启动填写 `BOOTSTRAP_ADMIN_USERNAME` 和 `BOOTSTRAP_ADMIN_PASSWORD`。项目没有内置默认登录账号。已有 `.env` 时直接编辑，不要重新复制覆盖。

```powershell
docker compose up -d mysql
docker compose ps
# MySQL 显示 healthy 后启动后端，保持此终端运行
mvn -s .mvn/settings.xml spring-boot:run
```

在另一个终端的项目根目录执行：

```powershell
cd frontend
npm ci
npm run dev
```

打开 [管理后台](http://localhost:5173)，使用刚设置的管理员账号登录。后端默认端口为 `8085`，MySQL 宿主端口为 `3307`。应用启动时由 Flyway 自动建表和升级数据库，无须逐个手动导入 SQL。

## 文档与 SQL

| 文件 | 用途 |
|---|---|
| [启动手册](docs/operations/startup.md) | 环境、首次登录、配置、检查与常见故障 |
| [Docker 手册](docs/operations/docker.md) | 基础设施启动、端口、数据卷和停止方式 |
| [数据库说明](docs/architecture/database.md) | SQL 入口、迁移顺序和核心表约束 |
| [初始化 SQL](docker/mysql/init/001-init.sql) | MySQL 首次空数据卷的骨架初始化 |
| [Flyway SQL](src/main/resources/db/migration) | 完整业务表、角色权限与索引迁移 |
| [AI 助手](docs/operations/ai-assistant.md) | 模型配置、读取权限与能力边界 |
| [RabbitMQ 运维](docs/operations/rabbitmq-reliability.md) | Outbox、重试、死信和失败记录 |
| [测试说明](docs/testing.md) | 后端、前端与真实基础设施测试命令 |
| [产品范围](docs/business/requirements.md) | 已实现功能与限制 |
| [库存规则](docs/business/inventory-rules.md) | 数量不变量、事务、幂等与状态机 |
| [架构说明](docs/architecture/overview.md) | 模块职责与依赖边界 |

## 当前限制

- Compose 只包含 MySQL、Redis 和 RabbitMQ，前后端需单独运行；尚未提供应用 Docker 镜像或生产前端托管配置。
- Redis、RabbitMQ、AI 默认关闭；MySQL 是基础业务运行的必需服务。
- 当前权限为接口级 RBAC，没有仓库级数据权限；JWT 只有 Access Token。
- 静态盘点没有取消或退回路径，创建后会锁定选定库存维度，调整完成才释放。
- 安全库存规则、预警和消息人工补偿尚无管理页面/API。
- AI 只读，真实模型供应商联调尚未验证，不支持自动制单或调整库存。
- 示例数据库与消息凭据仅用于本地开发，生产部署需另行配置。
