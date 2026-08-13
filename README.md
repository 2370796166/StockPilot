# StockPilot Backend

StockPilot 是面向中小型制造或电商企业的智能仓储与库存管理平台。当前已实现后端基础工程、基础资料、认证、RBAC 和基础审计，不包含库存和业务单据。

## 当前能力

- Java 17、Spring Boot 3、Maven
- MyBatis-Plus 与 MySQL 8
- 统一响应、参数校验、全局异常与业务错误码
- OpenAPI / Swagger UI
- 同时检查应用与 MySQL 的健康接口
- Docker Compose MySQL 与初始化脚本
- JUnit 5 可运行测试
- 仓库、库位、商品分类、SKU、供应商管理
- 条件分页、详情、修改及启用/停用
- Flyway 数据库版本管理和数据库唯一约束
- Spring Security、BCrypt、JWT Access Token 和数据库驱动的接口权限
- 用户、角色、权限、关联管理及关键安全操作审计

## 基础资料接口

五类资源统一提供创建、修改、详情、分页及状态变更，不提供删除接口：

| 资源 | 路径 |
|---|---|
| 仓库 | `/api/master-data/warehouses` |
| 库位 | `/api/master-data/locations` |
| 商品分类 | `/api/master-data/categories` |
| SKU | `/api/master-data/skus` |
| 供应商 | `/api/master-data/suppliers` |

创建使用 `POST /资源路径`，修改使用 `PUT /资源路径/{id}`，详情使用 `GET /资源路径/{id}`，分页使用 `GET /资源路径?page=1&size=20`，状态变更使用 `PATCH /资源路径/{id}/status`。修改和状态变更必须携带响应中的 `version`，防止覆盖并发修改。

分页支持 `code`、`name`、`status` 条件，单页最大 100。库位还支持 `warehouseId`，SKU 还支持 `categoryId`。编码长度为 2–32，以字母开头，只允许字母、数字、下划线和连字符，保存时统一转成大写。

## 认证与权限

登录接口为 `POST /api/auth/login`。除健康检查、登录及 OpenAPI 页面外，接口必须携带 `Authorization: Bearer <accessToken>`。缺少或无效令牌统一返回 HTTP 401，无权限统一返回 HTTP 403。

安全管理接口位于 `/api/security`，包含用户、角色、权限、关联管理和审计日志查询。基础资料查询需要 `MASTER_DATA_READ`，写操作需要 `MASTER_DATA_WRITE`；用户角色和角色权限分配需要系统管理员持有的 `SECURITY_GRANT`。未明确列入安全规则的新接口默认拒绝访问。

仓库不保存默认账号或 JWT 密钥。首次启动可临时设置 `JWT_SECRET`、`BOOTSTRAP_ADMIN_USERNAME`、`BOOTSTRAP_ADMIN_PASSWORD` 创建管理员；创建后应清除两个引导账号变量。JWT 密钥至少 32 字符并持续由部署环境提供。本阶段仅提供默认 60 分钟的 Access Token，不提供 Refresh Token。

## 环境要求

- JDK 17 或更高版本（项目按 Java 17 编译）
- Maven 3.9+
- Docker Desktop 或兼容 Docker Compose 的环境

确认环境：

```bash
java -version
mvn -version
docker compose version
```

## 启动步骤

### 1. 启动 MySQL

在项目根目录执行：

```bash
docker compose up -d mysql
docker compose ps
```

等待 `stockpilot-mysql` 状态变为 `healthy`。首次创建数据卷时，MySQL 会执行 `docker/mysql/init/001-init.sql`；应用启动时 Flyway 自动执行后续迁移。

默认开发连接信息：

| 配置 | 默认值 |
|---|---|
| 地址 | `localhost:3307`（容器内仍为 `3306`） |
| 数据库 | `stockpilot` |
| 用户 | `stockpilot` |
| 密码 | `stockpilot_dev` |

默认密码仅用于本地开发，不能用于生产环境。

### 2. 运行测试

```bash
mvn -s .mvn/settings.xml clean test
```

### 3. 启动后端

```powershell
$env:JWT_SECRET="替换为至少32字符的随机密钥"
mvn -s .mvn/settings.xml spring-boot:run
```

如果本地数据库配置不同，可以覆盖环境变量：

```powershell
$env:DB_URL="jdbc:mysql://localhost:3307/stockpilot?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
$env:DB_USERNAME="stockpilot"
$env:DB_PASSWORD="stockpilot_dev"
$env:JWT_SECRET="替换为至少32字符的随机密钥"
mvn -s .mvn/settings.xml spring-boot:run
```

仓库内的 `.mvn/settings.xml` 将 Maven 本地缓存放在项目的 `.m2` 目录，并强制使用 Maven Central HTTPS，可避免全局 Maven 配置指向无写权限目录或旧 HTTP 镜像。该缓存目录已加入 `.gitignore`。

## 验证

健康检查：

```text
GET http://localhost:8080/api/health
```

MySQL 正常时返回：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {
    "status": "UP",
    "database": "UP"
  },
  "timestamp": "2026-08-13T00:00:00Z"
}
```

接口文档：

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>

## 配置项

| 环境变量 | 默认值 | 含义 |
|---|---|---|
| `SERVER_PORT` | `8080` | 后端端口 |
| `DB_URL` | 本地 `stockpilot` JDBC URL | 数据库地址 |
| `DB_USERNAME` | `stockpilot` | 数据库用户 |
| `DB_PASSWORD` | `stockpilot_dev` | 数据库密码 |
| `MYSQL_HOST_PORT` | `3307` | Docker MySQL 映射到宿主机的端口 |
| `JWT_SECRET` | 无 | JWT HMAC 密钥，至少 32 字符 |
| `JWT_ACCESS_TOKEN_MINUTES` | `60` | Access Token 有效分钟数 |
| `BOOTSTRAP_ADMIN_USERNAME` | 无 | 仅首次引导管理员时设置 |
| `BOOTSTRAP_ADMIN_PASSWORD` | 无 | 仅首次引导管理员时设置 |

## 包结构

```text
com.stockpilot
├─ common       统一响应、错误码和异常处理
├─ config       MyBatis-Plus、OpenAPI等配置
├─ health       健康检查的Controller、应用服务和Mapper
├─ masterdata   仓库、库位、分类、SKU和供应商
└─ security     登录、JWT、用户/角色/权限、RBAC和审计
```

Controller 只调用应用服务，不直接调用 Mapper。后续商品、库存、入库和出库模块也必须遵守该规则。

## 停止基础设施

停止容器但保留数据库数据：

```bash
docker compose down
```

本项目不会在普通启动流程中自动删除 MySQL 数据卷。
