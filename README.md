# StockPilot

一个仓储与库存管理项目，支持采购入库、销售出库、调拨、盘点、库存流水和账号权限管理。后端使用 Java 17 / Spring Boot 3，前端使用 Vue 3，数据保存在 MySQL。

**只想把项目跑起来，按下面 5 步操作即可。想用 IDEA 改代码，看 [本地启动手册](docs/operations/startup.md)。**

## 快速启动：使用 Docker

下面以 Windows PowerShell 为例。Docker 会准备数据库、构建并运行前后端，本机不用另外安装 Java、Maven、Node.js 或 MySQL。

### 第 1 步：安装工具

安装 [Git](https://git-scm.com/downloads/) 和 [Docker Desktop](https://docs.docker.com/desktop/setup/install/windows-install/)。打开 Docker Desktop，使用 Linux 容器，等待启动完成。

打开 PowerShell，执行 `docker info`。没有连接报错，再继续下一步。

### 第 2 步：下载项目

```powershell
git clone https://github.com/2370796166/StockPilot.git
cd StockPilot
Copy-Item .env.example .env
```

后面的命令都在这个 `StockPilot` 文件夹中执行，里面应有 `pom.xml` 和 `docker-compose.yml`。已有 `.env` 就直接编辑，别再复制覆盖；macOS/Linux 的复制命令是 `cp .env.example .env`。

### 第 3 步：设置登录账号

用记事本打开 `.env`（文件名不能是 `.env.txt`），找到并修改以下三项：

```dotenv
JWT_SECRET=粘贴下面生成的密钥
BOOTSTRAP_ADMIN_USERNAME=admin
BOOTSTRAP_ADMIN_PASSWORD=填写你自己的登录密码
```

`JWT_SECRET` 是程序用来校验登录的密钥。在 PowerShell 执行下面几行，把输出的一整行复制到 `JWT_SECRET=` 后：

```powershell
$jwtBytes = New-Object byte[] 32
$jwtRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRng.GetBytes($jwtBytes)
$jwtRng.Dispose()
[Convert]::ToBase64String($jwtBytes)
```

macOS/Linux 可以用 `openssl rand -base64 32` 生成。

用户名和密码就是稍后登录页面时要输入的，项目没有固定默认密码。把中文提示替换成实际值，不要重复添加同名配置。

配置值不加引号或行尾注释，密码先用字母、数字、下划线、连字符组合，避免 `$` 和反斜杠被特殊解析。

保存文件。其他设置先不改，Redis、RabbitMQ 和 AI 的功能开关保持 `false`，照样可以使用库存业务。真实密码只放 `.env`，不要改模板 `.env.example`。

### 第 4 步：启动

逐条执行：

```powershell
docker compose --profile app config --quiet
docker compose --profile app up -d --build
docker compose --profile app ps
```

第一条没报错，再执行第二条。第一次要下载镜像和依赖，请等待完成。第三条会列出 `mysql`、`redis`、`rabbitmq`、`backend`、`frontend` 五个服务，等它们都显示 `healthy`。

`starting` 表示还在启动，可稍后再执行第三条；`exited` 或 `unhealthy` 则查看下面的排错说明。完整启动会运行 Redis、RabbitMQ 容器，但是否使用它们仍由配置开关决定。

### 第 5 步：打开页面并登录

浏览器打开 **[http://localhost:5173](http://localhost:5173)**，输入第 3 步设置的用户名和密码。

也可以在 PowerShell 检查服务是否正常：

```powershell
Invoke-RestMethod http://localhost:5173/api/health | ConvertTo-Json
```

看到 `code=SUCCESS`，且 `status`、`database` 都是 `UP`，说明页面的 API 代理、后端和数据库已连通。

**能登录就启动成功了。库存为空是正常的：新数据库没有演示数据。** 先在页面创建仓库、库位和商品，再完成一笔采购入库，就能看到库存。

首次登录成功后，可清空 `.env` 的两项 `BOOTSTRAP_ADMIN_*`，再按下表重新加载配置；账号仍保留。以后改这两项不会重置旧账号密码，`JWT_SECRET` 则继续沿用。

## 平时怎么操作

以下命令在项目根目录执行：

| 想做什么 | 命令 |
|---|---|
| 停止项目，保留数据 | `docker compose --profile app stop` |
| 下次启动 | `docker compose --profile app up -d` |
| 修改 `.env` 后让配置生效 | `docker compose --profile app up -d --force-recreate backend` |
| 修改代码后重新构建 | `docker compose --profile app up -d --build backend frontend` |
| 查看后端日志 | `docker compose --profile app logs --tail=100 backend` |

数据在 Docker 数据卷中保存，普通停止不会丢失。**不要用 `down -v` 做日常停止，它会删除数据库和消息数据。**

## 启动失败，先查这些

| 遇到的问题 | 怎么处理 |
|---|---|
| Docker 连接失败 / 命名管道不存在 | 打开 Docker Desktop，使用 Linux 容器，先让 `docker info` 正常 |
| 找不到配置文件 | 回到项目根目录，确认有 `.env`，不是 `.env.txt` |
| 下载镜像或依赖失败 | 检查网络、代理及报错中无法访问的地址，修复后重试启动命令 |
| 只有三个服务，没有前后端 | 启动命令不能漏掉 `--profile app` |
| 端口被占用 | 在 `.env` 改对应端口，再执行 `up -d`，例子见下方 |
| 页面能开，接口报 502 | 看 `backend` 是否 healthy，再查后端日志 |
| 无法登录 | 检查 JWT 密钥、首次管理员用户名和密码，修改后重新加载配置。已有用户名不会重置密码 |
| 数据库密码改了却连不上 | 旧数据卷保留原密码；改 `.env` 不会自动修改数据库账号 |

例如页面端口 `5173` 被占用，在 `.env` 修改 `FRONTEND_HOST_PORT=15173`，重新执行 `docker compose --profile app up -d`，然后打开 `http://localhost:15173`。

## AI 和其他可选功能

### AI 助手

先让普通业务跑通，再到模型供应商的 API 平台申请自己的密钥。在根目录 `.env` 填写，例如 DeepSeek：

```dotenv
AI_ENABLED=true
AI_PROVIDER=DEEPSEEK
AI_BASE_URL=https://api.deepseek.com
AI_MODEL=填写你开通且支持工具调用的模型名
AI_API_KEY=填写你自己的API密钥
```

保存后执行 `docker compose --profile app up -d --force-recreate backend`，再进入页面里的“AI 仓储助手”。详细填写方式见 [AI 手册](docs/operations/ai-assistant.md)。

聊天网站账号不能代替 API Key；基础地址不要追加 `/chat/completions`。密钥只放后端 `.env`，不能放前端。AI 只查询、分析，不会自动改库存；必要问句和业务字段会发送到配置的模型供应商。

### 缓存和消息

需要时在 `.env` 设置 `CACHE_ENABLED=true` 或 `RABBITMQ_ENABLED=true`，再重新加载后端配置。Redis 缓存基础资料，RabbitMQ 处理异步预警；具体说明见下方手册。

## 简单演示

先建两个仓库、各一个库位和一个商品。编码用英文、数字，例如 `WH_A`、`LOC_A`、`SKU_A`；商品还要填单位。用管理员账号，对同一商品和库位按顺序操作：

| 操作 | 应看到的结果 |
|---|---|
| 源仓采购入库 100，提交、审核、完成 | 实际 100，可用 100，冻结 0 |
| 销售单 20，执行冻结 | 实际 100，可用 80，冻结 20 |
| 取消销售单 | 恢复为 100 / 100 / 0 |
| 调拨 15，提交、审核、调出、转在途、目标仓收货 | 源仓剩 85，目标仓收到 15 |
| 源仓盘点，开始后填实盘 83，提交、审核、调整 | 源仓变为 83，记录盘亏 2 |

在“库存流水”里可以查看每次变动的来源。采购只保存草稿或提交时不会增加库存，必须完成入库。

## 项目设计

- 库存统一通过库存服务修改，单据、余额、流水在同一个 MySQL 事务中提交。
- 条件更新、版本和唯一约束处理并发与重复动作；数量使用 `BigDecimal`，前端保留十进制字符串。
- Redis、RabbitMQ 承担辅助业务；AI 使用有权限限制的只读工具，库存结果以 MySQL 为准。

回归测试保留在 `src/test` 和 `frontend/tests`，不会放进最终应用 JAR 或页面资源。修改代码后可在根目录运行：

```powershell
mvn clean test
npm --prefix frontend ci
npm --prefix frontend test
```

这些测试需要本机 JDK 17、Maven 3.9+ 和 Node.js 22。当前是单企业项目，没有多租户、批次、成本核算或仓库级数据权限；安全库存规则和消息人工补偿暂无管理页面。

## 更多说明

| 手册 | 适合什么时候看 |
|---|---|
| [本地启动](docs/operations/startup.md) | 用 IDEA 或终端改代码、启动前后端 |
| [Docker](docs/operations/docker.md) | 改端口、了解数据卷、部署到服务器 |
| [MySQL](docs/operations/mysql.md) | 数据库连接、迁移、备份和集成测试 |
| [Redis](docs/operations/redis.md) | 开启缓存和排错 |
| [RabbitMQ](docs/operations/rabbitmq-reliability.md) | 开启消息、了解重试和死信 |
| [AI 助手](docs/operations/ai-assistant.md) | 配置模型、查询权限和使用方法 |

默认只允许本机访问。部署到服务器时，先更换模板中的数据库和消息密码，再配置 SSH 转发或 HTTPS 网页入口；步骤见 Docker 手册。
