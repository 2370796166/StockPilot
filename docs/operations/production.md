# 生产部署与恢复

本机开发配置保持原用法。正式部署使用 `production` 配置：关闭自动数据库迁移和 Swagger、强制制单人与审核人不同、新用户默认没有仓库数据权限。先在独立环境验证，再切换正式数据。

## 分开迁移与运行账号

1. 数据库管理员建立业务库与迁移账号；迁移账号负责 Flyway，不用于日常业务连接。
2. 打包 `mvn package`。在部署终端设置 `DB_URL`、`MIGRATION_DB_USERNAME`、`MIGRATION_DB_PASSWORD` 环境变量，使用独立入口执行迁移：

```powershell
java -cp target/stockpilot-backend-0.0.1-SNAPSHOT.jar '-Dloader.main=com.stockpilot.shared.migration.DatabaseMigration' org.springframework.boot.loader.launch.PropertiesLauncher
```

该入口不启动 HTTP、消息消费者或 AI，也不读取本机 `.env`；拒绝没有 Flyway 历史的非空库，不自动 baseline/repair/clean。既有数据库有正常 Flyway 历史时按版本升级。迁移完成后从该终端移除迁移密码环境变量。

3. 复制 [运行账号授权模板](../../docker/mysql/runtime-grants.sql) 到 Git 忽略的私有目录，替换库名、账号和密码，由数据库管理员执行。不要直接执行模板中的占位密码。
4. 给应用设置 `DB_USERNAME`、`DB_PASSWORD`、随机 `JWT_SECRET` 和 `SPRING_PROFILES_ACTIVE=production`，再启动 JAR 或重新创建 Compose 后端。生产配置不自动运行 Flyway，因此应用无需 DDL 权限。首次管理员创建后清空引导密码配置。

运行账号可以执行正常业务；库存流水、业务退回证据、消息轨迹、消费幂等与安全审计只授予追加权限。模板随业务表增加需要更新。备份、归档与恢复使用单独授权账号。

生产模式新建业务用户后，在“用户管理 → 仓库权限”分配仓库，再分配业务角色。仅有仓库范围不会授予接口权限，只有角色也不会扩大仓库范围。旧账号升级时保留原有全仓范围，应由管理员逐个调整。

## 对外访问

由你的域名入口配置有效证书和 HTTPS，再代理到前端 HTTP 端口；证书与私钥不入 Git。MySQL、Redis、RabbitMQ、后端及消息管理端口保持内网或 127.0.0.1 访问，防火墙只开放必要入口。不要把全局 `COMPOSE_BIND_IP` 改为 0.0.0.0 来开放全部服务。

应用提供单进程登录尝试预算：默认每账号每分钟50次、全局1000次；生产分别为20和300次。按账号规范化计数，重启后清零，返回429时稍后重试；不修改账号状态。公网入口可另设 IP 限流。多实例部署需重新评估入口预算，不能把当前内存计数当作集群限流。

“运行状态”展示连接池、进程请求累计、消息积压；`/api/management/metrics` 和 `/api/management/health` 需要运行状态读取权限。指标应只接入受控监控网络，不公开环境变量或配置端点。应用日志含请求关联 ID，响应头为 `X-Request-ID`；不记录密码、Token 或请求体。

MySQL 固定为8.0.46，Redis/RabbitMQ固定到当前验证镜像的摘要。固定版本保证可复现，不代表永久安全或持续支持；定期评估更新并重新执行 MySQL/Broker 回归，不能依赖浮动标签自动升级。

## 备份与恢复演练

明确最多允许丢失多久的数据、多久恢复服务，再安排备份频率、保留期与异地保存。备份需要包含全部业务表和 `flyway_schema_history`；使用支持一致性快照的方式，例如 `mysqldump --single-transaction --routines --triggers --set-gtid-purged=OFF --no-tablespaces`。通过密码提示或私有客户端配置提供凭据，备份文件不要提交 Git。

恢复演练在独立空库进行：导入备份，核对迁移版本、单据状态、逐维度三数量、完整流水累计与冻结来源，再以运行账号启动同版本应用，验证登录、读写和对账。记录实际恢复耗时和缺失数据窗口，不能把“备份文件生成成功”当作恢复成功。

需要恢复到最近一次全量备份之后的时间点时，还需按实际目标配置并保留 binlog，演练时间点恢复。正式恢复前保全故障库与现有备份，先隔离写入口；不要删除数据卷或任意修正库存来处理故障。
