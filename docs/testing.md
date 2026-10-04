# 测试与构建检查

测试命令从项目根目录执行，前端命令在 `frontend` 目录执行。不要把模拟数据库、模型或组件测试当作真实基础设施与浏览器验收。

## 常规检查

后端常规测试不需要 MySQL、Redis 或 RabbitMQ：

```powershell
mvn -s .mvn/settings.xml clean test
mvn -s .mvn/settings.xml spotless:check
mvn -s .mvn/settings.xml -DskipTests package
```

最后一条只打包，不运行测试；应在常规测试通过后执行。报告位于 `target/surefire-reports`，构建产物位于 `target`，均不提交。

前端：

```powershell
cd frontend
npm ci
npm run test
npm run typecheck
npm run lint
npm run format:check
npm run build
```

`frontend/tests` 是可重复执行的回归测试源码，应保留；`node_modules`、`dist` 和测试运行产物不提交。测试覆盖请求竞态、详情回显、防重复动作、登录状态、资料缓存和 AI 页面等。前端生产构建不等于已有生产托管方案。

## 真实 MySQL 集成测试

先启动 MySQL 并等待 healthy：

```powershell
docker compose up -d mysql
docker compose ps
mvn -s .mvn/settings.xml -Pmysql-it verify
```

默认管理员连接 `localhost:3307`，`root / root_dev_only`。如果使用其他端口或凭据，在运行测试的终端设置：

```powershell
$env:STOCKPILOT_IT_ADMIN_URL="jdbc:mysql://localhost:33307/?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
$env:STOCKPILOT_IT_ADMIN_USER="root"
$env:STOCKPILOT_IT_ADMIN_PASSWORD="替换为本地测试密码"
mvn -s .mvn/settings.xml -Pmysql-it verify
```

每类 IT 自动创建随机后缀的专用 schema，执行完整 Flyway 迁移，测试后只删除本次专用库，不使用 `stockpilot` 开发库。管理员账号须有建库和删库权限。测试显式隔离开发 `.env` 的缓存、消息和管理员引导设置。

覆盖采购、销售、调拨、盘点、消息事务以及真实 JWT/HTTP 核心链路，验证数量、流水、并发、幂等与回滚。只执行核心 HTTP 场景可用：

```powershell
mvn -s .mvn/settings.xml -Pmysql-it "-Dit.test=CoreBusinessE2EMySqlIT" verify
```

AI HTTP 测试使用本机假模型，不能证明真实供应商兼容性。异常退出可能留下本次随机库，应核实归属后清理。

## 真实 RabbitMQ 集成测试

```powershell
docker compose up -d mysql rabbitmq
docker compose ps
docker compose exec rabbitmq rabbitmqctl add_vhost /stockpilot-it
docker compose exec rabbitmq rabbitmqctl set_permissions -p /stockpilot-it stockpilot ".*" ".*" ".*"
mvn -s .mvn/settings.xml -Prabbit-it verify
```

首次运行才需要创建 `/stockpilot-it`，已存在时跳过 `add_vhost`。开发 vhost `/stockpilot` 与测试 vhost 独立，Compose 不自动创建测试 vhost。

测试 RabbitMQ 连接可通过 `STOCKPILOT_IT_RABBIT_HOST/PORT/USER/PASSWORD/VHOST` 覆盖，默认 `localhost:5673`、`stockpilot / stockpilot_dev`、`/stockpilot-it`。交换机和队列使用本次随机库名前缀，不清理开发队列。验证发布确认、重试、死信、未知事件版本和幂等；测试拓扑残留需核实归属后清理。

## 验证边界

Compose 静态检查使用 `docker compose config --quiet`。它不要求引擎运行，也不能证明容器已启动或数据库 SQL 可执行。

2026-10-04 本次整理：后端 166 项常规测试、前端 47 项测试通过；后端格式与 JAR、前端类型/Lint/格式/生产构建、Compose 静态配置检查通过。前端测试首次因沙箱子进程权限失败，获准重跑后通过。构建保留第三方 PURE 注释和大分块提示。

本次 Docker 引擎未运行，真实 MySQL/RabbitMQ 集成测试、空卷初始化、应用真实接口、浏览器和真实模型联调均**未验证**。本次未修改业务源码、迁移 SQL 或 Compose；这些源码包含此前尚未提交的业务改动。
