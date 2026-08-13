# 当前阶段

基础工程整理与交接阶段。项目仍是单 Maven 模块的 Spring Boot 后端骨架，没有登录、基础资料、库存或业务单据代码。

# 已完成并验证

- Java 17 编译目标、Spring Boot 3.2.3 Maven 工程。2026-08-13 执行 `mvn -s .mvn/settings.xml -B -o clean test`，编译 12 个主源码、1 个测试源码，构建成功。
- 健康检查 HTTP 层及统一响应。`HealthControllerTest` 通过：1 个测试，0 失败、0 错误、0 跳过。
- 可执行 JAR。执行 `mvn -s .mvn/settings.xml -B -o -DskipTests package` 成功生成 `target/stockpilot-backend-0.0.1-SNAPSHOT.jar`。
- MyBatis-Plus 和 MySQL 数据源配置存在；健康检查通过 Mapper 执行 `SELECT 1`。
- Docker Compose MySQL。执行 `docker compose config --quiet` 成功；容器 `stockpilot-mysql` 当前为 `healthy`，宿主端口 `3307`。
- 数据库初始化。实际查询确认数据库仅有 `schema_version` 表，版本记录为 `0.1.0`。
- 应用与数据库端到端连接已于 2026-08-13 本轮实际启动验证：`GET /api/health` 返回 `SUCCESS`、应用 `UP`、数据库 `UP`；`/v3/api-docs` 返回标题 `StockPilot API`、版本 `v1`。验证后临时应用进程已停止。
- README、`.gitignore` 和项目 Maven settings 已存在并经本次内容核对。

# 已完成但未充分验证

- 参数校验依赖和异常处理代码已存在，但当前没有带校验注解的 Request，也没有针对参数异常、业务异常和系统异常的专门测试。
- Swagger UI 页面未在浏览器中人工检查；OpenAPI JSON 已验证。
- MySQL 初始化脚本已在现有数据卷执行，但未使用全新数据卷重复验证；不得为验证而擅自删除当前数据卷。
- 当前环境 Maven 实际运行在 JDK 25；项目按 `release 17` 编译，但本轮未使用独立 JDK 17 运行测试。

# 计划中但尚未开发

- 基础资料、认证、库存余额、库存流水、采购入库、销售出库。
- Redis、RabbitMQ、安全库存预警和消费幂等。
- 数据库正式迁移机制、业务表和数据库集成测试。
- Vue 3 前端。

# 已知问题

- 当前目录不是 Git 仓库，无法提供真实 `git status` 或 Git 差异；提交前需由用户初始化或指定正确仓库。
- `application.yml` 和 Compose 含相同的本地开发示例数据库凭据。未发现 Token、API 密钥、`.env` 或 `application-local.yml`；生产环境必须通过外部配置提供秘密。
- MySQL 镜像使用浮动标签 `mysql:8.0`，未固定补丁版本。
- 初始化方式仅适用于首次创建空数据卷，脚本修改不会自动作用于已有数据库；尚未采用 Flyway/Liquibase。
- `target/`、`.m2/` 和空的本地验证日志存在于工作区，但均被 `.gitignore` 覆盖；没有删除这些生成文件。
- PowerShell 默认读取 UTF-8 中文时显示乱码；显式 UTF-8 和数据库十六进制检查确认源码、README、SQL 注释实际内容正常。
- 辅助命令 `mvn -o dependency:tree` 因 dependency 插件未缓存而失败；核心编译、测试和打包不受影响。

# 下一阶段唯一目标

只开发“基础资料模块”：

- 仓库
- 库位
- 商品分类
- SKU
- 供应商

开始前应先确认商品分类层级、SKU 最小字段、数据库迁移方案和基础资料删除/停用规则。

# 下一阶段禁止提前开发

- 登录和 RBAC
- 库存余额
- 采购入库
- 销售出库
- 调拨和盘点
- Redis
- RabbitMQ
- 前端

# 启动与验证方法

```powershell
# 启动并查看MySQL
docker compose up -d mysql
docker compose ps

# 干净编译和测试
mvn -s .mvn/settings.xml clean test

# 打包
mvn -s .mvn/settings.xml -DskipTests package

# 启动应用
mvn -s .mvn/settings.xml spring-boot:run
```

验证地址：

```text
http://localhost:8080/api/health
http://localhost:8080/v3/api-docs
http://localhost:8080/swagger-ui.html
```

默认 MySQL 地址是 `localhost:3307`。如端口或凭据不同，通过 `MYSQL_HOST_PORT`、`DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 覆盖。
