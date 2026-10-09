# Docker 与本地开发

第一次启动请按根目录 [README](../../README.md) 完成 `.env`、JWT随机密钥和首次管理员设置。`.env.example` 是可提交模板；真实 `.env` 不进入Git、Docker构建上下文或前端镜像。

## 两种方式

```powershell
# 完整Docker：五个服务和应用镜像构建
docker compose --profile app up -d --build
# IDEA方式：只启动数据库，应用在宿主机运行
docker compose up -d mysql
```

MySQL、Redis、RabbitMQ默认可用；backend/frontend属于app profile。基础设施健康后启动backend，backend健康后启动frontend。运行IDEA前先停止占用端口的Docker应用：`docker compose --profile app stop backend frontend`。

## 服务和端口

| 服务 | 镜像 | 默认宿主 → 容器端口 | 持久化 |
|---|---|---|---|
| mysql | mysql:8.0 | 3307 → 3306 | stockpilot_mysql_data命名卷 |
| redis | redis:7.2-alpine | 6380 → 6379 | 可恢复缓存，无持久卷 |
| rabbitmq | rabbitmq:3.13-management-alpine | 5673 → 5672；15673 → 15672 | stockpilot_rabbitmq_data命名卷 |
| backend | 本机多阶段构建stockpilot-backend:local | 8085 → 8085 | 业务数据在MySQL |
| frontend | 本机多阶段构建stockpilot-frontend:local | 5173 → 80 | Nginx提供构建后的Vue资源 |

默认绑定127.0.0.1。端口变量为MYSQL_HOST_PORT、REDIS_HOST_PORT、RABBITMQ_HOST_PORT、RABBITMQ_MANAGEMENT_HOST_PORT、BACKEND_HOST_PORT、FRONTEND_HOST_PORT。容器名称由Compose项目生成，使用服务名查日志。RabbitMQ主机名保持stockpilot-rabbitmq以保持其持久化节点身份。

容器之间使用mysql:3306、redis:6379、rabbitmq:5672；后端内固定8085。IDEA连接localhost和宿主映射端口，变更MYSQL_HOST_PORT时也要修改本地DB_URL。Nginx转发/api，支持SPA路由刷新和后端容器重新创建后的DNS解析。

## 配置与凭据

完整Docker后端通过运行时env_file读取根目录.env，再由environment覆盖容器内部地址。配置不会被复制到镜像。前端不接收.env和AI_API_KEY；浏览器只访问本项目API。

数据库和应用用户均为stockpilot，新MySQL卷用DB_PASSWORD设置应用密码，用MYSQL_ROOT_PASSWORD设置root密码。RabbitMQ固定用户stockpilot、vhost /stockpilot，新卷密码取RABBITMQ_PASSWORD。已有卷保留旧凭据，修改.env不等于重置数据库账号。

使用替代配置文件时，先设置STOCKPILOT_ENV_FILE为该文件路径，再使用同一文件：`docker compose --env-file 该文件 --profile app up -d --build`。这样Compose插值与后端env_file读取同一配置。

AI、缓存、消息开关默认关闭；开启后需重新创建Docker后端：

```powershell
docker compose --profile app up -d --force-recreate backend
```

只执行restart不会加载更改后的容器环境变量。源码更新则运行 `docker compose --profile app up -d --build backend frontend`。

## 检查和停止

```powershell
docker compose --profile app config --quiet
docker compose --profile app ps
docker compose --profile app logs --tail=100 backend frontend
Invoke-RestMethod http://localhost:5173/api/health
docker compose --profile app stop
```

根目录Dockerfile构建Java17后端，frontend/Dockerfile构建前端后由Nginx托管。镜像构建只打包；常规测试需独立执行 `mvn -s .mvn/settings.xml clean test` 和前端测试。

MySQL首次空卷执行docker/mysql/init/001-init.sql建立骨架；后端启动时Flyway建立业务表并执行待应用迁移。不要重复手动导入SQL，不修改迁移历史。卷实际名称带Compose项目名前缀，改目录或项目名会选择另一套卷。

stop保留容器和数据；`docker compose --profile app down`删除容器与网络、保留命名卷。日常不要加-v，它会删除数据库和消息数据。独立测试可用不同项目名和端口，不需要动已有卷。

本配置提供本机演示入口，公网部署仍需配置域名、HTTPS、真实凭据、数据库权限和备份。MySQL始终是库存权威来源，Redis/MQ不会接管核心库存写入。
