# 当前阶段

基础资料模块完成阶段。项目是单 Maven 模块的 Spring Boot 后端，已实现基础资料，没有登录、库存或业务单据代码。

# 已完成并验证

- 仓库、库位、单层商品分类、SKU、供应商的创建、修改、详情、条件分页和启用/停用接口。
- 仓库、SKU、供应商、分类编码全局唯一；库位编码在仓库内唯一。真实 HTTP 验证确认不同仓库可使用相同库位编码，重复仓库编码返回 HTTP 409。
- 库位创建和修改要求关联存在且已启用的仓库；SKU 可选关联存在且已启用的分类。
- 基础资料无删除接口，停用数据仍可通过详情和状态条件查询。
- 分页默认 20、上限 100；编码格式、必填字段和分页参数使用 Bean Validation。
- Flyway 已在现有 MySQL 8.0.46 数据库上成功从 `0.1.0` baseline 执行 `V0.2.0`，创建五张业务表及唯一键、外键、CHECK 约束。
- 2026-08-13 最终执行 `mvn -s .mvn/settings.xml clean test`：共 10 个测试，0 失败、0 错误、0 跳过。

- Java 17 编译目标、Spring Boot 3.2.3 Maven 工程。2026-08-13 交接复验编译 51 个主源码、4 个测试源码，构建成功。
- 健康检查 HTTP 层及统一响应。`HealthControllerTest` 通过：1 个测试，0 失败、0 错误、0 跳过。
- 可执行 JAR。执行 `mvn -s .mvn/settings.xml -B -o -DskipTests package` 成功生成 `target/stockpilot-backend-0.0.1-SNAPSHOT.jar`。
- MyBatis-Plus 和 MySQL 数据源配置存在；健康检查通过 Mapper 执行 `SELECT 1`。
- Docker Compose MySQL。执行 `docker compose config --quiet` 成功；容器 `stockpilot-mysql` 当前为 `healthy`，宿主端口 `3307`。
- 数据库迁移。实际查询确认 Flyway `0.1.0` baseline 和 `V0.2.0` 均成功，存在五张基础资料表及 `schema_version`、`flyway_schema_history`。
- 应用与数据库端到端连接已于 2026-08-13 交接时再次启动验证：`GET /api/health` 返回 `SUCCESS`、应用 `UP`、数据库 `UP`；`/v3/api-docs` 返回标题 `StockPilot API`、版本 `v1`；基础资料只读分页接口正常。验证后临时应用进程已停止。
- README、`.gitignore` 和项目 Maven settings 已存在并经本次内容核对。

# 已完成但未充分验证

- 参数校验和业务异常已有基础资料测试覆盖；兜底系统异常仍没有专门测试。
- Swagger UI 页面未在浏览器中人工检查；OpenAPI JSON 已验证。
- MySQL 初始化脚本已在现有数据卷执行，但未使用全新数据卷重复验证；不得为验证而擅自删除当前数据卷。
- 当前环境 Maven 实际运行在 JDK 25；项目按 `release 17` 编译，但本轮未使用独立 JDK 17 运行测试。

# 计划中但尚未开发

- 认证、库存余额、库存流水、采购入库、销售出库。
- Redis、RabbitMQ、安全库存预警和消费幂等。
- 数据库集成测试。
- Vue 3 前端。

# 已知问题

- 当前是 Git 仓库，分支为 `master` 并跟踪 `origin/master`；本阶段实现尚未提交。
- `application.yml` 和 Compose 含相同的本地开发示例数据库凭据。未发现 Token、API 密钥、`.env` 或 `application-local.yml`；生产环境必须通过外部配置提供秘密。
- MySQL 镜像使用浮动标签 `mysql:8.0`，未固定补丁版本。
- `001-init.sql` 仍仅适用于首次创建空数据卷；正式后续变更已改用 Flyway。
- `target/`、`.m2/`、IDE 文件和本地验证日志均被 `.gitignore` 覆盖，未混入 Git 差异。
- PowerShell 默认读取 UTF-8 中文时显示乱码；显式 UTF-8 和数据库十六进制检查确认源码、README、SQL 注释实际内容正常。
- 辅助命令 `mvn -o dependency:tree` 因 dependency 插件未缓存而失败；核心编译、测试和打包不受影响。

# 下一阶段唯一目标

待用户在新对话中明确指定；交接后不得自行继续开发任何模块。

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
