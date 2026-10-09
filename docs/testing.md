# 测试与构建检查

测试命令从项目根目录执行，前端命令在 `frontend` 目录执行。不要把模拟数据库、模型或组件测试当作真实基础设施与浏览器验收。

## 一键检查

```powershell
powershell -NoProfile -File scripts/verify.ps1
# 已准备专用基础设施并设置下文环境变量时：
powershell -NoProfile -File scripts/verify.ps1 -MySql -Rabbit
```

脚本依次执行后端 clean test、Spotless、打包、Compose 静态配置检查及前端测试、类型、Lint、格式和构建；任一步失败即停止。真实基础设施和浏览器/真实模型验收仍需独立执行。脚本不启停服务、不创建普通业务库、不清理开发队列；集成测试使用其自身的专用临时库。

## 常规检查

2026-10-08 日常AI业务查询258项常规、6项核心HTTP/MySQL、69项前端及构建检查通过；四问真实DeepSeek题集未执行。能力边界见 [AI 助手说明](operations/ai-assistant.md)。常规编译后，真实模型验收入口为 `python -B scripts/test-ai-live.py --agent --case business_queries`；可用 `--build-directory` 指定隔离构建的target目录，凭据仍只从原项目本机.env读取，不复制到测试目录。

历史验收结果不能代替当前改动的复验。普通测试不调用真实模型，真实MySQL/HTTP测试使用本机协议夹具。

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

## 显式真实 Agent 验收

先完成 Maven 编译和常规测试，再单独执行（不要同时运行 Maven clean/编译）：

```powershell
python -B scripts/test_ai_live_config.py
python -B scripts/test-ai-live.py --agent
```

第一条是离线配置测试；第二条从本机 `.env` 读取 DeepSeek 凭据，经 stdin 传给独立 Java 验收进程，创建随机专用 MySQL schema，使用真实 JWT/HTTP，结束后删除自己的库。默认 MySQL 为 localhost:33307，可用上述 STOCKPILOT_IT_ADMIN_* 覆盖。每轮最多28次模型请求，输出只记录脱敏诊断、案例和累计调用数。普通 mvn test、mysql-it、rabbit-it 均不会调用真实供应商；不加 --agent 的历史模式使用合成 Service。

浏览器模式是独立验收夹具，使用真实 MySQL 和本机延迟假模型，不能视为真实 DeepSeek 浏览器组合。

## 历史验证边界

Compose 静态检查使用 `docker compose config --quiet`。它不要求引擎运行，也不能证明容器已启动或数据库 SQL 可执行。

2026-10-04 本次整理：后端 166 项常规测试、前端 47 项测试通过；后端格式与 JAR、前端类型/Lint/格式/生产构建、Compose 静态配置检查通过。前端测试首次因沙箱子进程权限失败，获准重跑后通过。构建保留第三方 PURE 注释和大分块提示。

本次 Docker 引擎未运行，真实 MySQL/RabbitMQ 集成测试、空卷初始化、应用真实接口、浏览器和真实模型联调均**未验证**。本次未修改业务源码、迁移 SQL 或 Compose；这些源码包含此前尚未提交的业务改动。
