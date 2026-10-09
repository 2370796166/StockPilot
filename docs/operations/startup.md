# StockPilot 启动手册

本文补充 IDEA/源码启动方式。完整 Docker 和新手首次配置请先按根目录 [README](../../README.md) 操作：`docker compose --profile app up -d --build` 会构建并启动前后端及基础设施；不启用 app profile 时可仅启动 MySQL，保留本地开发方式。Redis、RabbitMQ、AI 功能仍按开关启用。

## 环境要求

| 工具 | 要求 |
|---|---|
| Java | 推荐 JDK 17，项目编译目标为 17 |
| Maven | 3.9+，命令使用项目内 `.mvn/settings.xml` |
| Node.js / npm | Node.js 22+，安装依赖使用 `npm ci`；完整Docker模式不需要本机Node.js |
| Docker | 引擎已启动，支持 Compose v2；Windows 使用 Linux 容器 |

先执行 `java -version`、`mvn -version`、`node -v`、`npm -v`、`docker compose version` 和 `docker info`。确认 Maven 使用的 Java 版本，以及 Docker 引擎可连接。

下文命令使用 PowerShell。除前端命令外，均从包含 `pom.xml` 和 `docker-compose.yml` 的项目根目录执行。

## 配置本地环境

首次克隆：

```powershell
git clone https://github.com/2370796166/StockPilot.git
cd StockPilot
Copy-Item .env.example .env
```

已有 `.env` 时直接编辑。后端与 Compose 自动读取根目录 `.env`，操作系统环境变量优先于文件配置。`.env` 不进入 Git，`.env.example` 仅保留示例和占位值。

在 `.env` 中完成以下设置：

```dotenv
JWT_SECRET=替换为至少32字符的随机密钥
BOOTSTRAP_ADMIN_USERNAME=admin
BOOTSTRAP_ADMIN_PASSWORD=替换为你自己的管理员密码
```

可在 PowerShell 中用以下命令生成密钥，再将输出保存到 `.env`。后续启动沿用同一密钥，修改密钥会使旧 Token 失效。

```powershell
$jwtBytes = New-Object byte[] 32
$jwtRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRng.GetBytes($jwtBytes)
$jwtRng.Dispose()
[Convert]::ToBase64String($jwtBytes)
```

首次管理员的用户名和密码必须同时填写。启动时若用户名不存在，程序创建用户并绑定 `ADMIN`；用户名已存在则跳过，**不会重置密码、状态或角色**。首次登录成功后，清空 `.env` 中两项 `BOOTSTRAP_ADMIN_*`，并清除终端中同名环境变量；后续使用已创建的账号登录。项目没有固定的默认管理员密码。

默认数据库连接为 `localhost:3307/stockpilot`，用户 `stockpilot`，密码 `stockpilot_dev`。本地演示可沿用；自建 MySQL 时调整 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`，提前创建 UTF-8 数据库并给应用账号迁移所需的权限。

## 启动 MySQL

```powershell
docker compose up -d mysql
docker compose ps
```

等待 `mysql` 服务显示 `healthy`；容器名由 Compose 项目生成。异常时查看：

```powershell
docker compose logs --tail=100 mysql
```

首次空数据卷会自动运行 `docker/mysql/init/001-init.sql`。后端随后通过 Flyway 执行 `src/main/resources/db/migration` 下的全部待执行迁移，创建业务表与角色权限。不要重复手动导入这些 SQL；不要为重新初始化删除已有数据卷。详见 [MySQL 说明](mysql.md) 和 [Docker 手册](docker.md)。

## 启动后端

在项目根目录运行，并保持终端打开：

```powershell
mvn -s .mvn/settings.xml spring-boot:run
```

首次运行会下载依赖；`.mvn/settings.xml` 使用 Maven Central HTTPS，并将缓存放在被忽略的 `.m2/repository`。

日志出现应用启动成功后，在另一个终端检查：

```powershell
Invoke-RestMethod http://localhost:8085/api/health
```

正常响应的 `code` 为 `SUCCESS`，`data.status` 和 `data.database` 均为 `UP`。此接口检查应用和数据库，不代表 Redis、RabbitMQ 或模型可用。

也可打包后从根目录运行 JAR，二者任选一种：

```powershell
mvn -s .mvn/settings.xml clean package
java -jar target/stockpilot-backend-0.0.1-SNAPSHOT.jar
```

JAR 模式仍从当前工作目录读取 `.env`；从其他目录运行时需另行提供环境变量或配置文件。

## 启动前端并登录

在第二个终端，从项目根目录执行：

```powershell
cd frontend
npm ci
npm run dev
```

打开终端显示的地址，默认是 [http://localhost:5173](http://localhost:5173)。若端口被占用，Vite 可能选择下一端口，以实际输出为准。使用首次引导的用户名和密码登录。

前端通过 Vite 将 `/api` 代理到 `http://localhost:8085`。修改后端端口时，在**前端终端**设置目标并重启 Vite：

```powershell
$env:VITE_API_PROXY_TARGET="http://localhost:18085"
npm run dev
```

接口入口：

| 地址 | 用途 |
|---|---|
| `http://localhost:8085/api/health` | 应用与数据库健康检查 |
| `http://localhost:8085/swagger-ui.html` | Swagger UI |
| `http://localhost:8085/v3/api-docs` | OpenAPI JSON |
| `POST /api/auth/login` | 用户名密码登录 |
| `GET /api/auth/me` | 当前用户与实时权限，需要 Bearer Token |

首次使用先创建仓库、库位、商品和供应商，再走采购入库。草稿不会增加库存；采购完成后再验证库存余额和流水。角色默认分为管理员 `ADMIN`、业务员 `OPERATOR`、审核员 `AUDITOR`。

## 可选服务

基础业务只需 MySQL。修改 `.env` 后重启后端，使配置生效。

| 能力 | 启动命令 / 配置 | 说明 |
|---|---|---|
| 商品、仓库详情缓存 | `docker compose up -d redis`；`CACHE_ENABLED=true` | 默认地址 `localhost:6380`，故障时降级 MySQL |
| 异步安全库存预警 | `docker compose up -d rabbitmq`；`RABBITMQ_ENABLED=true` | 默认 AMQP `localhost:5673`，vhost `/stockpilot`；不负责核心库存扣减 |
| 只读 AI 助手 | `AI_ENABLED=true`，填写供应商、模型和密钥 | 详见 [AI 配置](ai-assistant.md)，密钥只保留在后端 |

## 常用配置

完整示例见根目录 `.env.example`。以下是最常调整的配置：

| 配置 | 默认值 | 修改时注意 |
|---|---|---|
| `SERVER_PORT` | `8085` | 同步修改前端代理目标 |
| `MYSQL_HOST_PORT` | `3307` | 仅改变容器映射；同步修改 `DB_URL` |
| `DB_URL` | 本地 `stockpilot` JDBC URL | 数据库实际地址、端口与库名必须匹配 |
| `DB_USERNAME` / `DB_PASSWORD` | `stockpilot` / `stockpilot_dev` | 本地后端连接凭据；Docker新卷用DB_PASSWORD初始化stockpilot用户，已有卷不自动改密码 |
| `JWT_SECRET` | 必须替换示例 | 至少 32 字符，无有效密钥不能登录 |
| `JWT_ACCESS_TOKEN_MINUTES` | `60` | Token 有效期 |
| `BOOTSTRAP_ADMIN_USERNAME` / `BOOTSTRAP_ADMIN_PASSWORD` | 空 | 首次建账号使用，不能用于重置既有账号 |
| `REDIS_HOST_PORT` / `REDIS_PORT` | `6380` / `6380` | 修改宿主映射时同步后端连接端口 |
| `RABBITMQ_HOST_PORT` / `RABBITMQ_PORT` | `5673` / `5673` | 修改宿主映射时同步后端连接端口 |
| `RABBITMQ_MANAGEMENT_HOST_PORT` | `15673` | 仅管理页面端口 |

例如 MySQL 使用 `33307` 时，在 `.env` 同时设置：

```dotenv
MYSQL_HOST_PORT=33307
DB_URL=jdbc:mysql://localhost:33307/stockpilot?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
```

不要只修改其中一个。完整 Docker 模式会自动使用容器内部端口。

## 常见故障

| 现象 | 排查方式 |
|---|---|
| Docker 报 Linux 引擎或命名管道不存在 | 启动 Docker Desktop、切换 Linux 容器，先确保 `docker info` 成功 |
| MySQL 未 healthy 或后端连接失败 | 查 MySQL 日志、映射端口、`DB_URL` 与凭据；已有卷仍保留原账号密码 |
| 登录提示 JWT 未配置 | 替换 `JWT_SECRET`，从根目录启动后端；检查终端同名变量是否覆盖 `.env` |
| 管理员登录失败 | 确认两个引导变量都填写；已有用户名不会被重置，修改配置后需重启后端 |
| 前端 API 返回 502 / 连接拒绝 | 确认后端健康，再核对 `VITE_API_PROXY_TARGET` 和实际后端端口 |
| 返回 401 / 403 | 401 检查登录、Token、用户状态；403 检查角色权限，无权限不是服务故障 |
| Flyway 校验失败 | 不修改已执行 SQL，也不盲目 repair；确认代码版本与数据库迁移历史一致 |
| Maven clean 无法删除 JAR | 确认是否有本项目 JAR 进程占用，先停止该应用，再重试 |

## 停止和日常重启

前后端终端按 `Ctrl+C` 停止。基础设施停止并保留数据：

```powershell
docker compose stop
```

下次执行 `docker compose up -d mysql`，再启动前后端即可。`docker compose down` 会移除容器和网络，但保留命名卷；不要给日常停止命令加 `-v`，该选项会删除数据库和 RabbitMQ 数据卷。
