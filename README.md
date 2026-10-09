# StockPilot

面向中小型制造、电商企业的仓储与库存管理平台。Java 17 / Spring Boot 3 后端，Vue 3 管理后台，MySQL 保存库存权威数据。包含采购入库、销售冻结与出库、调拨、盘点、库存流水和权限管理；Redis 缓存、RabbitMQ 异步预警、只读 AI 助手按需开启。

**第一次使用，先选一种启动方式，再按顺序配置。** 项目没有固定的默认登录密码，新数据库也不会自动生成演示库存。

## 1. 选择启动方式

| 方式 | 本机需要安装 | 适用场景 |
|---|---|---|
| 完整 Docker | Git、Docker Desktop / Docker Engine + Compose v2 | 直接运行和体验，不需要本机 Java、Maven、Node.js |
| IDEA / 本地开发 | Git、JDK 17、Maven 3.9+、Node.js 22+、Docker | 阅读代码、断点调试、修改功能 |

Windows 的 Docker Desktop 要启动并使用 **Linux 容器**。先执行 `docker info`，不能连接引擎时先解决 Docker 启动问题。

下文主要使用 Windows PowerShell。除明确说明外，均在**项目根目录**执行，该目录同时包含 `pom.xml`、`docker-compose.yml` 和 `.env.example`。

## 2. 下载项目，准备配置

```powershell
git clone https://github.com/2370796166/StockPilot.git
cd StockPilot
Copy-Item .env.example .env
```

macOS/Linux 的复制命令为 `cp .env.example .env`。已有 `.env` 时直接编辑，**不要再次复制覆盖**。

模板完整名称是 **`.env.example`**，不是 `.env.exam`，位于仓库根目录。它只含空密钥、默认值和示例，可以提交到 Git。真正的 `.env` 被 Git 忽略，也不会进入 Docker 构建上下文。`frontend/.env.example` 是可选的前端 API 地址模板，第一次启动不需要另建前端 `.env`。

### 2.1 必须填写：JWT 密钥和首次管理员

打开根目录 `.env`，填写三项：

```dotenv
JWT_SECRET=把生成的随机密钥粘贴在这里
BOOTSTRAP_ADMIN_USERNAME=admin
BOOTSTRAP_ADMIN_PASSWORD=填写你自己的管理员密码
```

JWT 密钥至少32字符，用于签发登录令牌，**与 AI 的 API Key 是两个不同的配置**。在 PowerShell 中生成一次随机密钥：

```powershell
$jwtBytes = New-Object byte[] 32
$jwtRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRng.GetBytes($jwtBytes)
$jwtRng.Dispose()
[Convert]::ToBase64String($jwtBytes)
```

将输出复制到 `JWT_SECRET=` 后。macOS/Linux 也可用 `openssl rand -base64 32`。不要把真实值填进 `.env.example` 或 README。

首次管理员的用户名、密码必须同时填写。不存在该用户名时，后端创建账号并赋予 `ADMIN`；已存在则跳过，**修改这两项不会重置既有账号密码或提升角色**。记住自己设置的密码，首次登录成功后可清空两项 `BOOTSTRAP_ADMIN_*`。后续沿用同一个 JWT 密钥，否则旧登录令牌失效。

配置值不要额外套引号，也不要在值末尾追加注释：后端按 properties 文件读取 `.env`。操作系统中同名环境变量会覆盖文件值。

### 2.2 第一次保留默认开关

```dotenv
CACHE_ENABLED=false
RABBITMQ_ENABLED=false
AI_ENABLED=false
```

先确认能打开页面并登录，再按第5、6节启用可选能力；这些开关不影响核心库存业务。

## 3. 方式A：完整 Docker 启动

完成第2节后，从项目根目录执行：

```powershell
docker compose --profile app config --quiet
docker compose --profile app up -d --build
docker compose --profile app ps
```

第一条没有输出表示配置语法通过。首次启动会下载基础镜像和 Maven/npm 依赖并编译，耗时取决于网络。成功后有五个服务：`mysql`、`redis`、`rabbitmq`、`backend`、`frontend`。先等待基础设施 healthy，再启动后端和前端；前端由 Nginx 提供页面并转发 `/api`。

检查应用与数据库：

```powershell
Invoke-RestMethod http://localhost:5173/api/health
```

正常返回 `code=SUCCESS`、`data.status=UP`、`data.database=UP`。打开 **[http://localhost:5173](http://localhost:5173)**，使用第2节设置的账号登录。

| 入口 | 默认地址 | 用途 |
|---|---|---|
| 管理后台 | `http://localhost:5173` | 页面和同源 API 代理 |
| 后端健康 | `http://localhost:8085/api/health` | 检查应用与数据库 |
| Swagger | `http://localhost:8085/swagger-ui.html` | 后端接口文档 |
| MySQL | `localhost:3307` | 本机客户端或 IDEA 后端连接 |
| Redis | `localhost:6380` | 可选缓存 |
| RabbitMQ | `localhost:5673` | AMQP；管理页面为 `http://localhost:15673` |

默认只绑定本机 `127.0.0.1`。容器之间使用 `mysql:3306`、`redis:6379`、`rabbitmq:5672`，Compose 会覆盖容器内的连接地址，**不需要把 `.env` 里的 localhost 改成容器名**。Docker 内数据库、应用用户、消息用户和 vhost 分别是 `stockpilot`、`stockpilot`、`stockpilot`、`/stockpilot`。

新 MySQL 卷的应用密码取 `DB_PASSWORD`，root 密码取 `MYSQL_ROOT_PASSWORD`；RabbitMQ 新卷密码取 `RABBITMQ_PASSWORD`。模板中的演示密码只供本地使用。已有卷保留旧密码，修改 `.env` 不会自动改变数据库或消息账号。

端口被占用时，在 `.env` 修改：

```dotenv
BACKEND_HOST_PORT=18085
FRONTEND_HOST_PORT=15173
MYSQL_HOST_PORT=33307
```

再次执行 `docker compose --profile app up -d` 后，页面访问 `http://localhost:15173`，后端访问 `http://localhost:18085`。容器内部端口和 Nginx 代理不变。如果随后切换到 IDEA，还要把 `DB_URL` 的 MySQL 端口同步为33307。

常用命令：

```powershell
# 查看最近日志，用服务名，不依赖生成的容器名称
docker compose --profile app logs --tail=100 backend frontend
docker compose logs --tail=100 mysql rabbitmq
# 修改 .env 后重新创建后端，让环境变量生效
docker compose --profile app up -d --force-recreate backend
# 修改源码后重新构建应用
docker compose --profile app up -d --build backend frontend
# 停止完整栈，保留数据
docker compose --profile app stop
# 下次重新启动
docker compose --profile app up -d
```

镜像为本机构建的 `stockpilot-backend:local`、`stockpilot-frontend:local`，无需手动提供 JAR。镜像构建只打包，完整测试单独按第8节执行。这套配置提供本地运行和演示入口；公网部署仍需配置域名、HTTPS、真实凭据和备份。

## 4. 方式B：IDEA / 本地开发启动

### 4.1 检查工具

```powershell
java -version
mvn -version
node -v
npm -v
docker compose version
```

JDK 选择17，检查 `mvn -version` 显示的 Java 也是17；Node.js 建议22或更高。如果提示“不是命令”，先安装工具并配置 PATH，再重新打开终端。

### 4.2 只启动基础设施

```powershell
docker compose up -d mysql
docker compose ps
```

等待 mysql healthy。未启用 `app` profile 时不会默认启动前后端容器。如果刚用过方式A，先执行 `docker compose --profile app stop backend frontend`，避免8085、5173端口冲突。

本地默认连接配置：

```dotenv
DB_URL=jdbc:mysql://localhost:3307/stockpilot?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
DB_USERNAME=stockpilot
DB_PASSWORD=stockpilot_dev
```

改了 `MYSQL_HOST_PORT` 就同步修改 URL 中的端口。IDEA 连接宿主端口，不是容器内部3306。自建 MySQL8 也可以，需先建立 `stockpilot` 数据库和具备迁移权限的应用账号，再填写自己的地址与凭据。

### 4.3 在 IDEA 启动后端

1. 打开项目根目录或 `pom.xml`，作为 Maven 项目加载。
2. Project SDK、Maven 导入和运行 JDK 都选择17。
3. 在 Maven 的 User settings file 中选择项目 `.mvn/settings.xml`，重新加载依赖。
4. 运行 `src/main/java/com/stockpilot/StockPilotApplication.java` 的 `main` 方法。
5. Run Configuration 的 **Working directory 设置为项目根目录**（可用 `$PROJECT_DIR$`），确保能读取根目录 `.env`。

不需要 dotenv 插件，也不要把真实 Key 写入 IDEA 共享运行配置。也可在根目录直接执行：

```powershell
mvn -s .mvn/settings.xml spring-boot:run
```

终端保持打开。启动时 Flyway 自动创建业务表、角色和权限，**不需要手动逐个导入 SQL**。检查后端：

```powershell
Invoke-RestMethod http://localhost:8085/api/health
```

健康应为 UP/UP。也可从根目录打包后运行：

```powershell
mvn -s .mvn/settings.xml clean package
java -jar target/stockpilot-backend-0.0.1-SNAPSHOT.jar
```

### 4.4 启动前端

另开终端，从项目根目录执行：

```powershell
cd frontend
npm ci
npm run dev
```

打开终端显示的地址，默认 `http://localhost:5173`；端口被占用时 Vite 可能换端口，以实际输出为准。使用自己创建的管理员账号登录。

默认 `/api` 代理到 `http://localhost:8085`。若在 `.env` 改了本地后端 `SERVER_PORT=18085`，在**前端终端**配置代理后重启 Vite：

```powershell
$env:VITE_API_PROXY_TARGET="http://localhost:18085"
npm run dev
```

macOS/Linux 对应 `VITE_API_PROXY_TARGET=http://localhost:18085 npm run dev`。

## 5. 启用 Redis 和 RabbitMQ（可选）

完整 Docker 已启动这两个容器，但仍需在 `.env` 开启功能。IDEA 模式先运行：

```powershell
docker compose up -d redis rabbitmq
```

```dotenv
CACHE_ENABLED=true
RABBITMQ_ENABLED=true
```

Docker 后端执行 `docker compose --profile app up -d --force-recreate backend`；IDEA/命令行后端停止后重新运行。本地默认 Redis6380、RabbitMQ5673；容器模式自动使用内部端口。RabbitMQ 管理页默认用户 `stockpilot`，密码为 `.env` 的 `RABBITMQ_PASSWORD`。

Redis 只缓存商品、仓库详情，RabbitMQ 处理允许延迟的预警，核心库存正确性由 MySQL 保证。

## 6. AI 配置：先准备 API，再开启助手

AI 默认关闭，不影响核心库存功能。**聊天网站账号不等于 API 权限**：需到供应商 API 平台创建 API Key、开通模型，并确认账户能调用；按供应商规则计费。

所有 AI 配置只放在根目录 `.env`，不要放进前端 `VITE_*`、`.env.example`、Dockerfile 或 Git。前端请求本项目后端，由后端连接模型；镜像中不会包含真实 Key。

### 6.1 DeepSeek 示例

到 [DeepSeek API 平台](https://platform.deepseek.com/) 获取自己的 Key，编辑 `.env`：

```dotenv
AI_ENABLED=true
AI_PROVIDER=DEEPSEEK
AI_BASE_URL=https://api.deepseek.com
AI_MODEL=deepseek-flash
AI_API_KEY=在此填写你自己的API密钥
AI_REQUEST_TIMEOUT=20s
AI_TOTAL_TIMEOUT=90s
```

`deepseek-flash` 和基础地址以 [DeepSeek 官方文档](https://api-docs.deepseek.com/) 为依据；模型名称可能调整，以账户实际可调用的模型为准。项目要求支持工具调用和非思考模式，适配器会发送关闭思考参数。

### 6.2 通义千问 / 百炼示例

在百炼获取 Key 和可用模型，地址必须与 Key 的地域、工作空间和计费方案对应：

```dotenv
AI_ENABLED=true
AI_PROVIDER=QWEN
AI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
AI_MODEL=填写控制台可调用且支持工具的模型名称
AI_API_KEY=在此填写百炼API密钥
```

这是北京地域兼容接口示例。其他地域和专用工作空间按 [百炼基础地址说明](https://www.alibabacloud.com/help/en/model-studio/base-url) 填写，不能混用地域 Key，也不能用聊天网页地址。项目对 QWEN 发送 `enable_thinking=false`。

### 6.3 其他供应商与参数

| AI_PROVIDER | AI_BASE_URL 的项目默认值 | AI_MODEL 怎么填 |
|---|---|---|
| DEEPSEEK | `https://api.deepseek.com` | 账户可调用的工具模型 |
| QWEN | 必须填写 | 百炼控制台模型名，地址与地域匹配 |
| GLM | `https://open.bigmodel.cn/api/paas/v4` | 智谱 API 平台的模型名 |
| DOUBAO | `https://ark.cn-beijing.volces.com/api/v3` | 火山方舟模型名或接入点ID |
| MOONSHOT | `https://api.moonshot.cn/v1` | Moonshot API 平台的模型名 |
| CUSTOM | 必须填写 | Chat Completions/tools 兼容服务的模型名 |

这些默认地址来自当前适配器，不代表供应商所有模型都可用。基础地址**不要追加 `/chat/completions`**，项目会自己追加。远程地址要求 HTTPS；HTTP 只允许 localhost/127.0.0.1 本地夹具。Docker 里的 localhost 是后端容器自身，不能直接用于宿主机模型。

| 配置 | 默认 | 用途 |
|---|---|---|
| AI_REQUEST_TIMEOUT | 20s | 一轮模型请求等待上限 |
| AI_TOTAL_TIMEOUT | 90s | 一次任务总执行预算 |
| AI_AGENT_MAX_ROUNDS | 4 | 规划轮数，1～8 |
| AI_AGENT_MAX_TOOL_CALLS | 12 | 业务工具总调用数，1～24 |
| AI_AGENT_SESSION_TTL | 30m | 内存会话空闲有效期 |
| AI_AGENT_INPUT_TTL | 5m | 等待用户补参有效期 |
| AI_MAX_TOOL_CALLS | 6 | 旧同步 questions 接口预算，不是 Agent 页面总预算 |

其他参数在 `.env.example` 有默认值，第一次不要同时改很多项。修改配置后，Docker 模式重新创建后端，IDEA 模式重新运行；**仅刷新网页不会加载新配置**。不要输出或截图完整 `.env`，不要把 Key 发进助手聊天框。

### 6.4 在页面验证

1. 先确认登录和 `/api/health` 正常。
2. 用有库存/基础资料读取权限的账号进入“AI 仓储助手”；冻结来源和单据还需相应业务读取权限。
3. 先创建仓库、库位、商品并完成一笔采购，再用实际名称提问，例如“查白云仓全部商品库存”；新库无数据是正常现象。
4. 再试“哪些销售单还没完成”“这个商品的冻结来源是什么”。同名资料要选择候选，缺参时可直接发送“就在白云仓”。

助手只读，不会自动制单、审核或调整库存。必要业务字段和问句会发送到配置的模型供应商；计算、权限和最终库存事实仍以服务端与 MySQL 为准。失败会保留已有证据，不能把查询失败当作零库存。

## 7. 新手常见问题

| 现象 | 检查方法 |
|---|---|
| 找不到 .env.exam | 实际名称是.env.example；确认在根目录，文件管理器可能隐藏点开头文件 |
| Docker 引擎/命名管道不存在 | 启动 Docker Desktop，使用 Linux 容器，确保 docker info 成功 |
| 镜像或依赖下载失败 | 检查网络、Docker代理/镜像服务；需能访问镜像仓库、Maven Central和npm，重试可复用缓存 |
| 容器仍在 starting | 查看 compose ps 和 backend/mysql 日志，等待首次建库和迁移 |
| 3307/8085/5173被占用 | 改前面的端口变量；不要同时运行占同端口的 IDEA 和 Docker 应用 |
| MySQL 连接失败 | IDEA核对DB_URL端口；Docker检查mysql healthy；已有卷仍保留原密码 |
| 登录失败/没有默认账号 | 用自己设置的BOOTSTRAP_ADMIN_*；两项同时填写并重启，已有用户名不会重置密码 |
| JWT未配置 | 根目录.env填至少32字符随机密钥；IDEA工作目录为根目录，检查系统变量覆盖 |
| 页面能开但API502 | 检查后端健康；IDEA检查Vite代理；Docker检查backend日志和网络 |
| AI未启用 | AI_ENABLED=true后重启/重建后端；前端.env不是后端配置 |
| AI配置无效 | 检查供应商、模型、Key非空、基础地址和超时；不要使用完整chat/completions URL |
| AI请求失败 | 检查供应商401/403鉴权、402余额、429限流、5xx和网络；日志只记录错误分类 |
| AI无数据/要求选择 | 使用真实名称，先建立业务数据；同名需选择，部分工具需要仓库和日期 |
| AI权限不足 | 管理员授予业务读取权限；403不是启动失败 |
| 健康UP但AI不可用 | 健康只验证应用与数据库，不验证模型、Key、Redis或RabbitMQ |
| Flyway校验失败 | 不改已执行SQL，不盲目repair或删除卷；核对代码与数据库版本 |
| Maven clean删JAR失败 | 停止使用本项目target JAR的应用，再重建；不要结束其他项目Java进程 |

## 8. 测试、数据保存和停止

后端常规测试不连接外部数据库或模型：

```powershell
mvn -s .mvn/settings.xml clean test
```

前端测试：

```powershell
cd frontend
npm ci
npm test
npm run build
```

真实MySQL/RabbitMQ和真实模型验收需要显式运行，见 [测试说明](docs/testing.md)。普通测试或健康通过，不代表真实模型语义、性能和公网部署已全部验证。

IDEA点击停止，本地终端按Ctrl+C；完整Docker执行 `docker compose --profile app stop`。MySQL和RabbitMQ数据在命名卷里，重启保留；Flyway只执行未应用的迁移。

`docker compose --profile app down` 移除容器和网络、保留卷。**日常操作不要加 `-v`，会删除数据库和消息数据。** 改项目目录名或Compose项目名可能选另一套卷，不表示原数据已丢失。

## 9. 进一步阅读

| 文件 | 用途 |
|---|---|
| [.env.example](.env.example) | 完整配置模板，无真实密钥 |
| [Docker手册](docs/operations/docker.md) | 镜像、网络、数据卷和两种模式 |
| [启动手册](docs/operations/startup.md) | 本地开发启动补充 |
| [AI说明](docs/operations/ai-assistant.md) | 工具、权限、会话和证据边界 |
| [数据库说明](docs/architecture/database.md) | 初始化SQL、Flyway、核心表 |
| [库存规则](docs/business/inventory-rules.md) | 数量不变量、事务、幂等和状态机 |

仍为单企业模块化单体；没有多租户、批次、成本核算、Refresh Token或仓库级数据权限。安全库存规则和消息人工补偿暂无管理页面。AI只做只读查询，不支持自动制单和库存调整。
