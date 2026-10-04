# Docker 基础设施手册

根目录 `docker-compose.yml` 用于本地开发的 MySQL、Redis 和 RabbitMQ。没有后端、前端容器，没有一键启动完整应用的 Dockerfile；应用启动见 [启动手册](startup.md)。

## 服务、端口与持久化

| 服务 | 镜像 | 默认宿主端口 → 容器端口 | 数据 |
|---|---|---|---|
| mysql | `mysql:8.0` | `3307 → 3306` | 命名卷 `stockpilot_mysql_data` |
| redis | `redis:7.2-alpine` | `6380 → 6379` | 无持久卷，可丢弃的查询缓存 |
| rabbitmq | `rabbitmq:3.13-management-alpine` | `5673 → 5672`；`15673 → 15672` | 命名卷 `stockpilot_rabbitmq_data` |

Compose 会为命名卷加项目名前缀，以 `docker volume ls` 显示的实际名称为准。固定容器名为 `stockpilot-mysql`、`stockpilot-redis`、`stockpilot-rabbitmq`，同机运行多个副本时会发生名称冲突。

MySQL 库名 `stockpilot`，开发用户 `stockpilot / stockpilot_dev`，root 密码 `root_dev_only`。RabbitMQ 用户 `stockpilot / stockpilot_dev`，开发 vhost `/stockpilot`；管理页面为 [http://localhost:15673](http://localhost:15673)。这些值在 Compose 中固定，后端的 `DB_PASSWORD` 和 `RABBITMQ_PASSWORD` 只改变连接配置，不会修改容器内部账号。

当前 Compose 是本地开发配置，端口没有限制为 loopback，Redis 未配置认证，镜像为浮动标签。部署到共享或公网环境前需调整暴露范围、凭据、版本和备份方案。

## 启动和检查

从项目根目录执行：

```powershell
docker compose config --quiet
docker compose up -d mysql
docker compose ps
docker compose logs --tail=100 mysql
```

基础业务等待 MySQL `healthy` 后即可启动。需要全部中间件时：

```powershell
docker compose up -d mysql redis rabbitmq
docker compose ps
```

仅启动容器不会启用应用功能；根目录 `.env` 中还需分别设置 `CACHE_ENABLED=true`、`RABBITMQ_ENABLED=true`，再重启后端。

## 端口调整

Compose 自动读取根目录 `.env` 中的 `MYSQL_HOST_PORT`、`REDIS_HOST_PORT`、`RABBITMQ_HOST_PORT` 和 `RABBITMQ_MANAGEMENT_HOST_PORT`。修改后重新执行 `docker compose up -d 对应服务`，并同步后端的 `DB_URL`、`REDIS_PORT` 或 `RABBITMQ_PORT`。

后端当前在宿主机运行，连接宿主映射端口。容器间地址 `mysql:3306`、`redis:6379`、`rabbitmq:5672` 不能直接用于宿主机后端。

## SQL 与数据卷

- `docker/mysql/init/001-init.sql` 以只读方式挂载至 `/docker-entrypoint-initdb.d`，只在首次初始化空 MySQL 数据卷时执行，建立骨架版本表。
- 完整业务表、权限和索引由后端启动时的 Flyway 迁移创建，SQL 位于 `src/main/resources/db/migration`。
- 已有数据卷不会因重启、重新创建容器或改环境变量而重放初始化脚本，也不会自动修改旧凭据。
- 更改项目目录名或 Compose 项目名可能选择另一个卷，看起来像“数据丢失”；先核对原卷与项目名，不要新建或删除卷来试错。

完整迁移说明见 [数据库说明](../architecture/database.md)。

## 日志、停止与恢复

```powershell
docker compose logs --tail=100 mysql redis rabbitmq
docker compose stop
docker compose up -d mysql
```

`stop` 保留容器与数据；`down` 移除容器和网络，但保留命名卷。日常停止不要使用 `down -v`，它会删除 MySQL 和 RabbitMQ 的数据卷。重建、升级或手动修改数据库前应先备份，Redis 缓存不作为备份对象。

MySQL 是库存唯一权威来源。Redis 不可用时详情读取降级 MySQL；RabbitMQ 不可用时库存事务照常提交，异步事件按 Outbox 重试和失败记录处理，见 [RabbitMQ 运维](rabbitmq-reliability.md)。
