# StockPilot

面向中小型制造、电商企业的仓储与库存管理平台。支持采购入库、销售出库、跨仓调拨、盘点、库存流水和账号权限管理，提供 Vue 管理页面和 Spring Boot 后端。

**第一次使用，建议先用 Docker 跑起来：按第 1～3 节操作，登录成功后再看第 5 节的业务演示。** Docker 会构建前后端并准备数据库，本机不需要另外安装 Java、Maven、Node.js 或 MySQL。

项目没有固定的默认登录密码；首次管理员由你配置。新数据库没有演示商品和库存，启动后需要在页面上创建。

## 阅读路线

| 你想做什么 | 从哪里开始 |
|---|---|
| 第一次在自己电脑上运行 | [1. 准备工具](#1-准备工具) → [2. 下载和配置](#2-下载项目并准备配置) → [3. Docker 启动](#3-用-docker-启动完整项目) |
| 在 IDEA 阅读代码、调试 | 先完成第 1、2 节，再看 [4. 本地开发](#4-用-idea或命令行做本地开发) |
| 登录后不知道怎么演示 | [5. 业务演示和日常操作](#5-登录后的业务演示和日常操作) |
| 开启 AI、缓存或消息 | [6. 可选功能](#6-ai-配置先准备-api再开启助手) |
| 启动、登录或下载报错 | [7. 常见问题](#7-常见问题按现象排查) |
| 部署到 Linux 服务器 | [8. 服务器部署](#8-在-linux-服务器上运行) |
| 了解设计、运行测试 | [9. 代码与验证](#9-代码结构设计和测试) |

## 1. 准备工具

### 只运行项目：安装 Git 和 Docker

Windows 用户安装 [Git](https://git-scm.com/downloads/) 和 [Docker Desktop](https://docs.docker.com/desktop/setup/install/windows-install/)。Docker Desktop 按官方教程配置 WSL 2，使用 **Linux 容器**，安装完成后还要打开 Docker Desktop，等待引擎启动。

打开 PowerShell，逐行执行：

```powershell
git --version
docker compose version
docker info
```

前两条应显示版本；第三条应显示 Docker 客户端和服务端信息，且没有连接错误。看到“命名管道不存在”或“无法连接 Docker daemon”时，先启动 Docker Desktop，不要继续构建。

macOS 可使用 Docker Desktop；Linux 使用 [Docker Engine 和 Compose 插件](https://docs.docker.com/engine/install/)。本项目使用 `docker compose`，不是旧命令 `docker-compose`。

### 要改代码：额外准备开发工具

仅选择第 4 节本地开发时，才需要 JDK 17、Maven 3.9+、[Node.js 22](https://nodejs.org/en/download) 和 IDEA。完整 Docker 启动会在镜像中安装构建工具。

## 2. 下载项目并准备配置

### 2.1 下载源码

在你准备存放项目的目录打开 PowerShell，执行：

```powershell
git clone https://github.com/2370796166/StockPilot.git
cd StockPilot
Get-ChildItem -Name
```

应能看到 `pom.xml`、`docker-compose.yml`、`.env.example`、`frontend` 和 `src`。**这个 StockPilot 文件夹就是项目根目录**，后面的命令除特别说明外都在这里执行。不要在 `frontend` 或 `src` 内执行 Docker 命令。

### 2.2 创建自己的配置文件

首次下载后执行：

```powershell
Copy-Item .env.example .env
notepad .env
```

`.env.example` 是供大家复制的模板；`.env` 是你自己的实际配置。**已有 `.env` 就直接编辑，不要再复制覆盖。** 文件名必须是 `.env`，不能保存为 `.env.txt`；在文件管理器里打开“显示文件扩展名”可以检查。

macOS/Linux 使用 `cp .env.example .env`，再用文本编辑器打开。第一次启动不需要复制 `frontend/.env.example`。

### 2.3 必须填好三项

在 `.env` 中找到下列已有配置行，修改等号右侧，**不要在文件末尾重复添加同名配置**：

```dotenv
JWT_SECRET=粘贴下一步生成的随机密钥
BOOTSTRAP_ADMIN_USERNAME=admin
BOOTSTRAP_ADMIN_PASSWORD=填写你自己设置并记住的管理员密码
```

| 配置 | 是什么 | 怎么填 |
|---|---|---|
| `JWT_SECRET` | 后端签发登录令牌的密钥 | 使用下方命令生成；至少 32 字符。不是 AI API Key |
| `BOOTSTRAP_ADMIN_USERNAME` | 首次创建的管理员用户名 | 第一次可填 `admin` |
| `BOOTSTRAP_ADMIN_PASSWORD` | 该管理员的登录密码 | 设置自己的密码；页面登录时输入同一个值 |

在 PowerShell 生成 JWT 密钥：

```powershell
$jwtBytes = New-Object byte[] 32
$jwtRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRng.GetBytes($jwtBytes)
$jwtRng.Dispose()
[Convert]::ToBase64String($jwtBytes)
```

将输出的一整行复制到 `JWT_SECRET=` 后。macOS/Linux 可用 `openssl rand -base64 32`。

第一次建议密码用字母、数字、下划线、连字符组合；配置值不要套引号、不要在行末追加注释，避免 `$` 和反斜杠导致 Compose 或 properties 解析与预期不同。把上面示例的中文提示替换成真实值，保存文件。

管理员用户名和密码必须同时填写。不存在该用户名时后端会创建并赋予 `ADMIN`；已存在时直接跳过，因此改这两项**不会重置旧账号密码**。登录确认成功后可将两项 `BOOTSTRAP_ADMIN_*` 清空并重新创建后端容器，已有账号仍保留。后续继续使用同一个 JWT 密钥，换密钥会使旧登录令牌失效。

### 2.4 其他配置先保持默认

```dotenv
CACHE_ENABLED=false
RABBITMQ_ENABLED=false
AI_ENABLED=false
```

这三个开关关闭时仍能完成采购、销售、调拨和盘点。先登录成功，再按第 6 节开启。

模板中的数据库和 RabbitMQ 密码适合本机体验。首次在服务器建库前，请设置自己的 `DB_PASSWORD`、`MYSQL_ROOT_PASSWORD`、`RABBITMQ_PASSWORD`；它们分别是数据库应用账号、数据库管理员、消息账号的密码，与网页登录密码无关。**已有数据卷仍使用旧密码，改 `.env` 不会自动修改库内账号。**

真实 `.env` 被 Git 忽略，也不会进入 Docker 镜像；不要把密钥填进 `.env.example` 或前端配置。终端中同名环境变量会覆盖 `.env`，排错时也要检查是否以前设置过这些变量。

## 3. 用 Docker 启动完整项目

### 3.1 检查配置，再启动

确认已保存 `.env`，从项目根目录逐条执行：

```powershell
docker compose --profile app config --quiet
docker compose --profile app up -d --build
docker compose --profile app ps
```

第一条没有输出且没有报错，表示配置语法通过；它不验证登录密码或数据库是否能连接。有错误时先修正，不要继续执行下一条。

第二条会下载镜像和 Maven/npm 依赖，构建应用并在后台运行。首次耗时取决于网络，看到构建输出不断变化时继续等待。命令中的 `--profile app` 表示同时启动前后端；漏掉它就只会启动基础设施。`-d` 表示后台运行，终端退出不会停止容器。

第三条应显示以下五个服务，最终状态均为 `healthy`：

| 服务 | 做什么 |
|---|---|
| `mysql` | 保存账号、单据、库存和流水 |
| `redis` | 提供可选的基础资料缓存 |
| `rabbitmq` | 提供可选的异步消息处理 |
| `backend` | 运行 Java 后端接口 |
| `frontend` | 使用 Nginx 提供页面并转发 API 请求 |

Redis、RabbitMQ 容器会随完整栈启动，但应用是否使用它们仍由第 2 节的开关决定。`starting` 表示仍在等待健康检查，可再次执行 `ps`；`exited` 或 `unhealthy` 则按第 7 节查看日志。

### 3.2 确认页面和数据库都能访问

在 PowerShell 执行：

```powershell
Invoke-RestMethod http://localhost:5173/api/health | ConvertTo-Json
```

应返回 `code` 为 `SUCCESS`，`data` 中 `status` 和 `database` 都为 `UP`。这表示页面的 API 代理、后端和 MySQL 可以连通；不代表 AI、Redis 或 RabbitMQ 已启用。

浏览器打开 **[http://localhost:5173](http://localhost:5173)**，输入第 2 节自己设置的管理员用户名和密码。能登录并打开库存页面就完成首次启动；库存列表为空是正常的，接着看第 5 节。

### 3.3 地址和端口

| 入口 | 默认地址 | 说明 |
|---|---|---|
| 管理页面 | `http://localhost:5173` | 平时使用这个地址 |
| 后端健康 | `http://localhost:8085/api/health` | 直接检查后端和数据库 |
| 接口文档 | `http://localhost:8085/swagger-ui.html` | 给开发者查看接口 |
| MySQL | `localhost:3307` | 数据库客户端连接地址 |
| Redis | `localhost:6380` | 本机可选缓存连接地址 |
| RabbitMQ | `localhost:5673` | 消息连接端口，不是网页 |
| RabbitMQ 管理页面 | `http://localhost:15673` | 用户 `stockpilot`，密码为 `RABBITMQ_PASSWORD` |

容器名称带项目名前缀，查日志时用 `mysql`、`backend` 等**服务名**即可。容器内部使用 `mysql:3306`、`redis:6379`、`rabbitmq:5672`，Compose 会自动配置，完整 Docker 模式不需要修改 `.env` 的 `DB_URL`。

如果提示端口被占用，修改 `.env` 中对应项，例如：

```dotenv
FRONTEND_HOST_PORT=15173
BACKEND_HOST_PORT=18085
MYSQL_HOST_PORT=33307
```

重新执行 `docker compose --profile app up -d`，页面地址改为 `http://localhost:15173`，后端健康地址改为 `http://localhost:18085/api/health`。若随后使用第 4 节本地开发，还要把 `DB_URL` 的 MySQL 端口改为 `33307`。

## 4. 用 IDEA（或命令行）做本地开发

这一节是另一种启动方式：MySQL 在 Docker 中运行，前后端在你的电脑上运行。已经用 Docker 登录成功、暂时不改代码时可以跳过。

### 4.1 检查工具和数据库

```powershell
java -version
mvn -version
node -v
npm -v
```

Java 和 Maven 输出中的 Java 版本都应为 17；Node.js 使用 22。缺少命令时安装工具并配置 PATH，再重新打开终端。

如果之前用过完整 Docker，先停止它的前后端，避免抢占端口：

```powershell
docker compose --profile app stop backend frontend
docker compose up -d mysql
docker compose ps
```

等待 MySQL `healthy`。根目录 `.env` 中的 `DB_URL` 必须匹配宿主机端口；默认是 `localhost:3307/stockpilot`，`DB_USERNAME=stockpilot`，密码与新 MySQL 卷创建时的 `DB_PASSWORD` 一致。

### 4.2 启动后端

1. IDEA 打开项目根目录，加载 `pom.xml` 为 Maven 项目。
2. Project SDK、Maven 导入和运行 JDK 都选择 17。
3. Maven 的 User settings file 选择项目中的 `.mvn/settings.xml`，重新加载依赖。
4. 在 `StockPilotApplication.java` 的运行配置中，将 Working directory 设置为项目根目录（`$PROJECT_DIR$`），然后运行 `main`。

也可以不用 IDEA，在项目根目录执行：

```powershell
mvn -s .mvn/settings.xml spring-boot:run
```

这个终端要保持运行。后端启动时 Flyway 自动创建业务表和权限，**无需手动逐个导入 SQL**。另开终端执行 `Invoke-RestMethod http://localhost:8085/api/health`，应看到 UP/UP。不需要 dotenv 插件。

### 4.3 启动前端

另开 PowerShell，进入项目根目录后执行：

```powershell
npm --prefix frontend ci
npm --prefix frontend run dev
```

打开终端显示的地址，默认 `http://localhost:5173`；端口占用时 Vite 可能选择其他端口，以实际输出为准。前后端这两个终端都要保持运行，按 `Ctrl+C` 停止。

默认前端把 `/api` 转发到 `http://localhost:8085`。如果改了本地后端 `SERVER_PORT`，先停止 Vite，在前端终端设置代理再启动，例如：

```powershell
$env:VITE_API_PROXY_TARGET='http://localhost:18085'
npm --prefix frontend run dev
```

`SERVER_PORT` 是本地 Java 端口；`BACKEND_HOST_PORT` 是 Docker 的映射端口，两者用途不同。更多配置见 [本地启动手册](docs/operations/startup.md)。

## 5. 登录后的业务演示和日常操作

### 5.1 先建立资料，再产生库存

用管理员账号，在“基础资料”中创建两个仓库、每个仓库各一个库位，以及一个商品。库位必须选择所属仓库；商品填写编码、名称和单位。编码可用 `WH_A`、`WH_B`、`LOC_A`、`LOC_B`、`SKU_A`，避免空格和中文编码。分类、供应商可按需要维护。

创建商品不会自动产生库存；库存由业务单据产生。下面每步都针对同一商品和选定库位，业务单号用不同的英文或数字编号：

| 步骤 | 在页面做什么 | 预期库存（实际 / 可用 / 冻结） |
|---|---|---|
| 1 | 源仓采购入库 100，依次提交、审核、完成 | 源仓 `100 / 100 / 0` |
| 2 | 创建源仓销售单 20，执行冻结 | 源仓 `100 / 80 / 20` |
| 3 | 取消该销售单 | 源仓恢复 `100 / 100 / 0`，流水保留冻结和释放记录 |
| 4 | 调拨 15 到目标仓，依次提交、审核、调出、转在途、收货 | 源仓 `85 / 85 / 0`，目标仓 `15 / 15 / 0`；收货前目标仓不增加 |
| 5 | 源仓盘点，开始后填实盘 83，提交、审核、调整 | 源仓 `83 / 83 / 0`，产生盘亏 2 的流水 |
| 6 | 打开库存流水，用业务单号筛选 | 可以追溯以上库存变动 |

“实际”是仓内总量，“冻结”是已被单据占用的数量，“可用”是还能分配的数量，始终满足 `实际 = 可用 + 冻结`。先用管理员完成整个流程，再体验业务员 `OPERATOR` 和审核员 `AUDITOR` 的权限区别。

### 5.2 停止、重启和更新

以下命令用于**完整 Docker 模式**，在项目根目录执行：

| 你要做什么 | 命令 |
|---|---|
| 停止项目，保留数据 | `docker compose --profile app stop` |
| 下次启动 | `docker compose --profile app up -d` |
| 查看应用日志 | `docker compose --profile app logs --tail=100 backend frontend` |
| 修改 `.env` 后加载新配置 | `docker compose --profile app up -d --force-recreate backend` |
| 修改前后端源码后重新构建 | `docker compose --profile app up -d --build backend frontend` |

`restart` 不会加载新的容器环境变量。确认本地修改已保存后，更新仓库代码可用 `git pull --ff-only`，然后按上表重新构建前后端；更新前先备份数据库，操作见 [MySQL 手册](docs/operations/mysql.md)。

数据保存在 Docker 命名卷中，停止容器、普通 `down` 不会删除它们。**日常停止不要执行 `down -v` 或删除数据卷**，否则会丢失数据库和消息数据。保持项目目录名和 Compose 项目名一致，改名可能切换到另一套空卷。

## 6. AI 配置：先准备 API，再开启助手

### 6.1 AI 助手（可选）

先完成普通登录和一笔采购，再开启 AI。聊天网站账号不能直接代替 API Key：到供应商 API 平台创建密钥、确认模型权限和额度，调用费用按供应商规则收取。

例如使用 [DeepSeek API 平台](https://platform.deepseek.com/)，在根目录 `.env` 修改：

```dotenv
AI_ENABLED=true
AI_PROVIDER=DEEPSEEK
AI_BASE_URL=https://api.deepseek.com
AI_MODEL=填入账户可调用且支持工具调用的模型名
AI_API_KEY=填入你自己的API密钥
```

千问使用 `AI_PROVIDER=QWEN`，`AI_BASE_URL` 按 [百炼基础地址说明](https://www.alibabacloud.com/help/en/model-studio/base-url) 填 OpenAI 兼容基础地址，`AI_MODEL` 填已开通的模型名，Key 与地址的地域/工作空间应匹配。支持的供应商及完整参数见 [AI 使用说明](docs/operations/ai-assistant.md)。

基础地址不要加 `/chat/completions`，不要填聊天网页地址；远程模型接口使用 HTTPS。模型需要支持工具调用和关闭思考模式。Docker 内的 `localhost` 是后端容器自身，不能直接指向宿主机模型服务。

保存配置后，Docker 模式执行：

```powershell
docker compose --profile app up -d --force-recreate backend
```

IDEA/命令行模式停止并重新启动后端。重新登录后进入“AI 仓储助手”，使用自己建立的仓库、商品名称提问，例如“源仓有哪些商品库存”。无数据、缺条件或出现同名候选时，按页面提示补充。

AI 只查询和分析，不会自动制单、审核或改库存。必要问句和业务字段会发送到你配置的模型供应商。真实 Key 只放后端 `.env`，不能放前端 `VITE_*`、源码或配置模板。

### 6.2 Redis 和 RabbitMQ（可选）

Docker 完整栈已经启动它们；本地开发模式先执行 `docker compose up -d redis rabbitmq`。再按需要修改 `.env`：

```dotenv
CACHE_ENABLED=true
RABBITMQ_ENABLED=true
```

与 AI 一样，修改后重新创建 Docker 后端或重新运行本地后端。Redis 缓存商品、仓库详情；RabbitMQ 用于库存变化后的异步预警，均不承担核心库存扣减。详见 [Redis 手册](docs/operations/redis.md)、[RabbitMQ 手册](docs/operations/rabbitmq-reliability.md)。

## 7. 常见问题：按现象排查

先查看失败的是哪个服务，不要反复删库、重装项目：

```powershell
docker compose --profile app ps -a
docker compose --profile app logs --tail=100 backend
docker compose logs --tail=100 mysql
```

| 现象 | 下一步 |
|---|---|
| `git`、`docker`、`mvn` 或 `npm` 不是命令 | 安装对应工具；完整 Docker 不需要 Maven/npm。重新打开终端检查 PATH |
| Docker 引擎/命名管道不存在 | 打开 Docker Desktop，使用 Linux 容器，先让 `docker info` 成功 |
| 找不到 `.env`、缺少 Compose 配置 | 回到同时含 `pom.xml` 和 `docker-compose.yml` 的根目录；检查是否误存为 `.env.txt` |
| 镜像、Maven 或 npm 下载失败 | 根据报错检查 Docker 镜像仓库、Maven Central 或 npm 的网络/代理；修复后重试构建，可复用缓存 |
| 端口被占用 | 按第 3.3 节改对应宿主端口；避免 Docker 应用与本地前后端同时运行 |
| 只有三个容器，没有页面 | 启动时漏了 `--profile app`；运行第 3.1 节完整命令 |
| `starting` 一直不结束 | 看对应服务日志；优先确认 MySQL healthy，再查 backend，最后查 frontend |
| 页面能开，但 `/api/health` 返回 502 | 查 backend 是否健康；本地开发检查 Java 端口和 Vite 代理 |
| 数据库连接失败 / Access denied | 核对数据库日志和凭据；本地开发还需匹配 DB_URL 端口。旧卷密码不会随 `.env` 改变 |
| 登录提示 JWT 未配置 | JWT_SECRET 至少 32 字符；Docker 重新创建后端，本地从根目录运行，并检查环境变量覆盖 |
| 管理员账号无法登录 | 用自己填的密码；首次创建需同时配置两个 BOOTSTRAP_ADMIN 字段。已有用户名不会重置密码 |
| 不小心忘记管理员密码 | 引导配置不是找回密码功能；有其他管理员时从用户管理修改。不要通过删数据卷“恢复”账号 |
| 登录后没有库存 | 新库是空的，先按第 5.1 节建资料并完成采购；保存或提交采购草稿不会增加库存 |
| 接口返回 401 / 403 | 401 重新登录并核对用户状态；403 检查角色权限，健康正常也可能没有业务权限 |
| AI 不可用，但健康为 UP | 检查 AI 开关、模型、Key 和供应商报错；健康接口只检查后端与数据库 |
| 修改 `.env` 没生效 | Docker 使用 `up -d --force-recreate backend`，本地重启后端；刷新网页不够 |
| Flyway 校验失败 | 不改已执行 SQL、不盲目 repair；核对源码版本和迁移历史，保留数据排查 |
| Maven clean 无法删除 JAR | 停止正在使用本项目 target JAR 的应用后重试，不要结束其他项目的 Java 进程 |

更多网络、数据卷和启动细节见 [Docker 手册](docs/operations/docker.md)。

## 8. 在 Linux 服务器上运行

先能在本机完成第 1～3 节，再在服务器安装 Git、Docker Engine 和 Compose 插件。以下是**服务器 SSH 终端中的 Bash 命令**，不要直接复制到 Windows PowerShell：

```bash
git clone https://github.com/2370796166/StockPilot.git
cd StockPilot
cp .env.example .env
chmod 600 .env
openssl rand -base64 32
nano .env
```

按第 2 节填写 JWT、管理员和自己的数据库/消息密码，保留 `COMPOSE_BIND_IP=127.0.0.1`；保存后执行：

```bash
docker compose --profile app config --quiet
docker compose --profile app up -d --build
docker compose --profile app ps
curl -fsS http://127.0.0.1:5173/api/health
```

服务器上的 `localhost` 指服务器，不是你的电脑。首次远程体验可以用 SSH 转发，在**自己电脑的新终端**执行（替换用户名和服务器 IP）：

```powershell
ssh -N -L 15173:127.0.0.1:5173 用户名@服务器IP
```

连接成功后保持终端运行，在自己电脑打开 `http://localhost:15173`。这条命令不会显示交互式服务器提示符。服务器若改了 `FRONTEND_HOST_PORT`，也要同步修改转发目标端口。

默认 Compose 仅监听本机，不能直接用服务器 IP 打开页面。需要让多人通过域名访问时，在服务器配置带 HTTPS 的反向代理，转发到 `127.0.0.1:5173`，按需开放网页入口；MySQL、Redis、RabbitMQ 保持本机绑定。服务器防火墙、HTTPS、定期数据库备份和运行账号权限需要自行配置，仓库没有一键生产部署脚本。

## 9. 代码结构、设计和测试

### 项目文件

| 目录或文件 | 用途 |
|---|---|
| `src/main` | Java 业务代码、配置和 Flyway 数据库迁移 |
| `frontend/src` | Vue 管理页面和 API 请求 |
| `src/test`、`frontend/tests` | 回归测试，用于验证修改；不进入最终应用 JAR 或页面资源 |
| `pom.xml`、`.mvn/settings.xml` | Maven 依赖、Java 构建与格式检查 |
| `frontend/package.json`、`package-lock.json` | 前端依赖和命令；锁文件用于保持依赖安装一致 |
| `Dockerfile`、`frontend/Dockerfile`、`docker-compose.yml` | 构建镜像及启动服务 |
| `docker/mysql/init`、`frontend/docker` | MySQL 首次初始化、Nginx 页面/API 代理 |
| `.env.example` | 可公开的配置模板；实际配置另存为 `.env` |

后端为 Java 17、Spring Boot 3 的模块化单体；前端使用 Vue 3、TypeScript、Vite、Element Plus。库存以 MySQL 为权威来源，数量使用 `DECIMAL(19,4)` / `BigDecimal`，前端和 JSON 保留十进制字符串。

| 要解决的问题 | 实现方式 | 代码入口 |
|---|---|---|
| 并发占用、重复执行 | 条件更新、单据行锁、版本与唯一约束，库存统一写入口 | [库存写服务](src/main/java/com/stockpilot/inventory/service/InventoryMutationApplicationService.java) |
| 单据、余额、流水同时成功或失败 | 同一 MySQL 本地事务，多明细失败整体回滚 | [销售服务](src/main/java/com/stockpilot/sales/service/SalesOutboundApplicationService.java) |
| 跨仓调拨、盘点审计 | 独立在途事实、盘点快照和维度锁 | [调拨](src/main/java/com/stockpilot/transfer/service/StockTransferApplicationService.java)、[盘点](src/main/java/com/stockpilot/inventory/count/service/InventoryCountApplicationService.java) |
| 消息故障、重复消费 | 同事务 Outbox、确认/重试、消费幂等与死信 | [消息发布](src/main/java/com/stockpilot/messaging/service/OutboxPublicationApplicationService.java) |
| AI 数量计算和查询权限 | 只读工具、逐次授权、预算与证据校验，后端计算数量 | [Agent](src/main/java/com/stockpilot/ai/service/AiAgentService.java)、[模型协议](src/main/java/com/stockpilot/ai/service/AiAgentModelProtocol.java) |

### 验证代码

以下命令在项目根目录执行，需要本机 JDK 17、Maven 和 Node.js。常规测试不连接真实数据库、消息服务或 AI 模型：

```powershell
mvn -s .mvn/settings.xml clean test spotless:check
npm --prefix frontend ci
npm --prefix frontend test
npm --prefix frontend run typecheck
npm --prefix frontend run lint
npm --prefix frontend run format:check
npm --prefix frontend run build
```

真实 MySQL、RabbitMQ 集成测试分别用 `-Pmysql-it verify`、`-Prabbit-it verify`，先按 [MySQL](docs/operations/mysql.md) 和 [RabbitMQ](docs/operations/rabbitmq-reliability.md) 手册准备独立测试环境。测试会创建并清理专用测试库和消息拓扑，不要给它生产账号或清空开发队列。

测试源码保留在仓库；个人验收脚本、工作流、手动模型探针和本地记录不随仓库提供。没有未经实测的 TPS、延迟或容量承诺。当前为单企业系统，不含多租户、批次、成本核算、仓库级权限或 AI 自动制单；安全库存规则和消息人工补偿暂无管理页面。

### 操作手册

| 手册 | 内容 |
|---|---|
| [本地启动](docs/operations/startup.md) | IDEA、宿主机连接和配置 |
| [Docker](docs/operations/docker.md) | 镜像、网络、端口和数据卷 |
| [MySQL](docs/operations/mysql.md) | 建库、迁移、备份和测试环境 |
| [Redis](docs/operations/redis.md) | 详情缓存开关和排错 |
| [RabbitMQ](docs/operations/rabbitmq-reliability.md) | Outbox、重试、死信和测试环境 |
| [AI 助手](docs/operations/ai-assistant.md) | 模型参数、权限、证据和使用边界 |
