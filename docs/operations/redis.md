# Redis 缓存使用说明

Redis 是可选的商品、仓库详情缓存，用于减少重复读取。库存余额、流水、业务校验和最终幂等仍使用 MySQL；Redis 不可用时详情读取降级到 MySQL。

## 启用

先按根目录 [README](../../README.md) 启动基本功能。IDEA 模式执行：

```powershell
docker compose up -d redis
docker compose ps
docker compose exec redis redis-cli ping
```

`PONG` 表示 Redis 服务可连接。根目录 `.env` 设置：

```dotenv
CACHE_ENABLED=true
REDIS_HOST=localhost
REDIS_PORT=6380
```

IDEA 后端重新运行；完整 Docker 已包含 Redis，修改开关后执行 `docker compose --profile app up -d --force-recreate backend`。容器内会自动连接 `redis:6379`。

## 参数与缓存行为

| 变量 | 默认 | 用途 |
|---|---|---|
| CACHE_ENABLED | false | 启用商品、仓库详情缓存 |
| REDIS_HOST / REDIS_PORT | localhost / 6380 | IDEA 后端连接位置 |
| REDIS_HOST_PORT | 6380 | Docker 对宿主机映射端口 |
| CACHE_KEY_PREFIX | stockpilot:v1 | 缓存键前缀 |
| CACHE_SKU_TTL | 30m | 商品详情有效期 |
| CACHE_WAREHOUSE_TTL | 30m | 仓库详情有效期 |
| CACHE_NULL_TTL | 60s | 不存在资料的短期缓存 |

修改宿主映射端口时，IDEA 模式同步修改 `REDIS_PORT`。商品或仓库更新、启停在 MySQL 成功后删除对应缓存。缓存值包含结构版本；连接、读写、删除或反序列化失败时降级数据库。

当前 Compose 的 Redis 无持久卷，重启丢失缓存后可重新加载，不影响库存数据。默认仅绑定本机地址，配置未提供 Redis 密码；不要直接将其暴露到公网。

## 常见问题

| 现象 | 检查 |
|---|---|
| PONG 正常但应用未用缓存 | `CACHE_ENABLED` 是否开启、后端是否已重启或重新创建 |
| 连接失败 | Redis 服务、IDEA 的宿主端口与 Docker 内部地址是否区分 |
| Redis 停止后仍能查询 | 正常降级行为；数据库仍是权威来源 |
| 缓存为空 | 尚未读取详情、缓存过期或容器已重启 |

不要通过清空整个 Redis 实例作为常规排查方式，实例可能还有其他项目的数据。启动和数据卷说明见 [Docker 手册](docker.md)。
