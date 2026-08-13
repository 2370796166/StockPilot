# StockPilot Backend

StockPilot 是面向中小型制造或电商企业的智能仓储与库存管理平台。本版本只包含可运行的后端工程骨架，不包含登录、商品或库存业务。

## 当前能力

- Java 17、Spring Boot 3、Maven
- MyBatis-Plus 与 MySQL 8
- 统一响应、参数校验、全局异常与业务错误码
- OpenAPI / Swagger UI
- 同时检查应用与 MySQL 的健康接口
- Docker Compose MySQL 与初始化脚本
- JUnit 5 可运行测试

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

等待 `stockpilot-mysql` 状态变为 `healthy`。首次创建数据卷时，MySQL 会自动执行 `docker/mysql/init/001-init.sql`。

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

```bash
mvn -s .mvn/settings.xml spring-boot:run
```

如果本地数据库配置不同，可以覆盖环境变量：

```powershell
$env:DB_URL="jdbc:mysql://localhost:3307/stockpilot?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
$env:DB_USERNAME="stockpilot"
$env:DB_PASSWORD="stockpilot_dev"
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

## 包结构

```text
com.stockpilot
├─ common       统一响应、错误码和异常处理
├─ config       MyBatis-Plus、OpenAPI等配置
└─ health       健康检查的Controller、应用服务和Mapper
```

Controller 只调用应用服务，不直接调用 Mapper。后续商品、库存、入库和出库模块也必须遵守该规则。

## 停止基础设施

停止容器但保留数据库数据：

```bash
docker compose down
```

本项目不会在普通启动流程中自动删除 MySQL 数据卷。
