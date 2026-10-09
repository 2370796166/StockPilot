# MySQL 使用说明

MySQL 8 保存商品、仓库、单据、库存余额、流水、账号权限和消息记录，是本项目库存数据的唯一权威来源。Redis 和 RabbitMQ 不替代数据库事务。

## 启动和连接

先按根目录 [README](../../README.md) 准备 `.env`。IDEA 模式只启动数据库：

```powershell
docker compose up -d mysql
docker compose ps
docker compose logs --tail=100 mysql
```

等待 `mysql` 服务 healthy。本机数据库客户端可使用以下配置：

| 配置 | 默认值 |
|---|---|
| 主机 / 端口 | `localhost` / `3307` |
| 数据库 | `stockpilot` |
| 用户 | `stockpilot` |
| 密码 | `.env` 的 `DB_PASSWORD`，模板为本地演示值 |
| 字符集 | `utf8mb4` |

IDEA 后端读取 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`。修改 `MYSQL_HOST_PORT` 时，需同步修改 `DB_URL` 的端口。完整 Docker 模式自动连接 `mysql:3306`，不需要将本机 URL 改成容器名。

新数据卷的应用密码取 `DB_PASSWORD`，root 密码取 `MYSQL_ROOT_PASSWORD`。**已有卷保留原密码，修改 `.env` 不会自动重置数据库账号。** 自建 MySQL 时先创建 `stockpilot` 数据库和应用账号，再配置连接信息；应用首次启动需要建表、索引和迁移所需权限。

## 初始化与 Flyway

| 入口 | 何时执行 | 作用 |
|---|---|---|
| `docker/mysql/init/001-init.sql` | MySQL 首次使用空卷 | 创建历史骨架，不创建完整业务表 |
| `src/main/resources/db/migration/V*.sql` | 后端启动 | Flyway 创建业务表并升级数据库 |

不需要逐个手动导入 SQL。当前最新迁移为 `0.9.2`，真实已执行版本查看 `flyway_schema_history`；`schema_version` 只是历史骨架记录。迁移脚本建立基础资料、权限、库存、四类单据、Outbox 和安全库存相关表。

库存数量使用 `DECIMAL(19,4)`，余额满足 `实际量 = 可用量 + 冻结量` 且各项非负。库存写入和不可变流水由后端统一事务管理，不应通过数据库客户端直接修改库存或删除流水。

## 数据保存与备份

MySQL 数据保存在 Compose 命名卷。`stop` 和不带 `-v` 的 `down` 保留数据；不要通过删卷解决登录或迁移失败。改项目目录或 Compose 项目名可能选到另一套卷，详见 [Docker 手册](docker.md)。

备份应包括完整业务数据和 Flyway 历史，可用数据库工具或 `mysqldump`，连接时使用密码提示，避免把密码写进命令或共享脚本。恢复前备份目标库，并确认备份版本与应用迁移兼容。SQL 备份可能含业务和账号数据，不要提交 Git。

本地应用账号具有库级权限；生产环境还需要账号权限隔离、定期备份和恢复验证。

## 真实集成测试

`mysql-it` 会创建随机专用测试库、从空库执行 Flyway，结束后删除该测试库。使用独立测试 MySQL 或有测试库创建/删除权限的账号，测试不会使用 `DB_URL` 指定的业务库。

```powershell
$env:STOCKPILOT_IT_ADMIN_URL='jdbc:mysql://127.0.0.1:3307/?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false'
$env:STOCKPILOT_IT_ADMIN_USER='你的测试数据库管理员'
$env:STOCKPILOT_IT_ADMIN_PASSWORD='你的测试数据库密码'
mvn -s .mvn/settings.xml -Pmysql-it verify
```

URL 指向 MySQL 服务，不填业务库名；与宿主映射端口保持一致。不提供环境变量时使用本地测试默认3307/root/root_dev_only。GitHub CI 使用独立 MySQL8.0.46 服务和合成凭据。

## 常见问题

| 现象 | 检查 |
|---|---|
| 连接被拒绝 | `mysql` 是否 healthy、宿主映射端口是否正确 |
| Access denied | 账号、密码和连接库；已有卷仍使用原凭据 |
| Unknown database | 自建 MySQL 是否已建库、`DB_URL` 库名是否正确 |
| Flyway 校验失败 | 代码与数据库版本是否匹配、已执行脚本是否被修改；不要盲目 repair |
| 新库没有库存或管理员 | 新库无演示库存；管理员须按 README 填写两项首次引导配置 |
