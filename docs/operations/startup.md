# 本地启动手册

这份手册用于在自己电脑上改代码：MySQL 放在 Docker 中，Java 后端和 Vue 前端在本机运行。

**只想运行项目、不改代码，直接看 [README 的 Docker 启动步骤](../../README.md#快速启动使用-docker)。**

下面使用 Windows PowerShell。所有命令都在项目根目录执行，也就是同时含 `pom.xml` 和 `docker-compose.yml` 的文件夹。

## 1. 准备环境和配置

安装 JDK 17、Maven 3.9+、Node.js 22、Git 和 Docker。打开 Docker Desktop，使用 Linux 容器。

先检查工具：

```powershell
java -version
mvn -version
node -v
docker info
```

Java 和 Maven 输出中的 Java 版本都应为 17；其他命令也要正常显示信息。如果找不到命令，安装工具、配置 PATH 后重新打开终端。

还没下载项目或没有 `.env`，先按 [README 第 2、3 步](../../README.md#第-2-步下载项目) 下载和配置。填写 JWT 密钥、首次管理员用户名和密码，其他配置先保持默认。已有 `.env` 不要覆盖。

## 2. 启动数据库

如果之前用过完整 Docker，先停掉其中的前后端，避免端口冲突：

```powershell
docker compose --profile app stop backend frontend
```

启动 MySQL：

```powershell
docker compose up -d mysql
docker compose ps
```

等 `mysql` 显示 `healthy`。默认连接地址为 `localhost:3307/stockpilot`，账号为 `stockpilot`，密码取 `.env` 的 `DB_PASSWORD`，应与建库时使用的密码一致。

如果改过 `MYSQL_HOST_PORT`，还要修改 `.env` 中 `DB_URL` 的端口，例如都改为 `33307`。旧数据卷不会因为改配置而自动换密码。

## 3. 启动后端

**IDEA 和终端任选一种，不要同时启动两份后端。**

在 IDEA 中：

1. 打开项目根目录，加载 Maven 项目。
2. 项目和 Maven 的 JDK 都选 17。Maven settings 和本地仓库通常用默认值即可。
3. 在 Maven 窗口点击“重新加载所有 Maven 项目”，等待依赖下载完成。
4. 找到 `src/main/java/com/stockpilot/StockPilotApplication.java`。
5. 运行配置的 Working directory 设为项目根目录（`$PROJECT_DIR$`），选择 `StockPilotApplication` 再运行；不要选成 Docker Compose 的 `frontend`。

或者在终端执行：

```powershell
mvn spring-boot:run
```

保持这个终端运行。第一次会下载依赖，程序启动时自动创建业务表，不用自己导入 SQL。

另开终端检查：

```powershell
Invoke-RestMethod http://localhost:8085/api/health | ConvertTo-Json
```

看到 `SUCCESS`，且 `status`、`database` 都是 `UP`，表示后端和数据库正常。

## 4. 启动前端并登录

另开 PowerShell，进入同一个项目根目录：

```powershell
npm --prefix frontend ci
npm --prefix frontend run dev
```

打开终端显示的网页地址，默认是 `http://localhost:5173`。输入 `.env` 中自己设置的管理员用户名和密码登录。两个终端都要保持运行。

新库没有库存，先建立仓库、库位和商品，再完成采购入库。想开启 AI、缓存或消息，按 [README 的可选功能](../../README.md#ai-和其他可选功能) 配置；本地开发需先启动对应的 Redis/RabbitMQ 服务，修改 `.env` 后重新启动本地后端。

## 5. 停止和排错

IDEA 点击停止；前后端终端按 `Ctrl+C`。数据库可以用 `docker compose stop mysql` 停止，数据会保留。下次从第 2 步重新启动即可。不要通过删除数据卷解决登录或启动问题。

| 遇到的问题 | 怎么处理 |
|---|---|
| 数据库连不上 | 确认 MySQL healthy，再核对 DB_URL 端口和原数据库密码 |
| 无法登录 | 检查根目录 `.env` 和运行目录；首次用户名、密码要同时填写，已有账号不会被重置 |
| 前端接口连不上 | 先检查后端健康；默认前端转发到 `localhost:8085` |
| 后端端口被占用 | 停止旧后端，或修改 SERVER_PORT 并同步前端代理 |

例如后端改为 `SERVER_PORT=18085`，停止前端后，在前端终端执行：

```powershell
$env:VITE_API_PROXY_TARGET='http://localhost:18085'
npm --prefix frontend run dev
```

更多问题看 [README 排错说明](../../README.md#启动失败先查这些)；数据库备份和迁移看 [MySQL 手册](mysql.md)。
