# RabbitMQ 与 Outbox 运维

RabbitMQ 只处理采购/销售完成后的安全库存预警。库存数量、流水和单据状态在 MySQL 本地事务中完成，MQ 故障不回滚已提交的库存业务。

## 启用

```powershell
docker compose up -d rabbitmq
docker compose ps
```

等待 healthy，将根目录 `.env` 的 `RABBITMQ_ENABLED=true` 后重启后端。默认连接 `localhost:5673`，用户 `stockpilot / stockpilot_dev`，vhost `/stockpilot`。管理页面为 [http://localhost:15673](http://localhost:15673)。

## 数据与投递链路

1. 采购/销售完成事务同时写 `async_outbox_message`，回滚不留下成功事件。
2. 调度器读取已提交事件，持久消息使用 mandatory 和 publisher confirm/return；确认后更新发布状态。
3. 消费幂等键为消息 ID + 消费者名，与预警更新在同一 MySQL 事务提交。
4. 预警查询 MySQL 最新可用库存，按仓库/库位/SKU阈值打开或解除。

事件为 `stockpilot.purchase-receipt.completed` 和 `stockpilot.sales-outbound.completed` v1。交付允许重复，消费者依靠数据库幂等，不是端到端恰好一次。

## 默认拓扑与重试

| 配置 | 默认 |
|---|---|
| Exchange | stockpilot.business.v1 |
| 完成事件队列 | stockpilot.completion.low-stock.v1 |
| 死信 Exchange | stockpilot.dead-letter.v1 |
| 死信队列 | stockpilot.completion.low-stock.dlq.v1 |
| Outbox 批量 / 调度间隔 | 20 / 2 秒 |
| 最大发布尝试 | 8 |
| 消费尝试上限 | 3 |

完整环境变量见 `.env.example`。发布达到上限转为失败并写 `async_failure_record`；不会在 MQ 恢复后自动重放已失败记录。消费永久失败经确认投递死信，再落异常记录；死信发布失败时保留原消息重试。未知版本不进入 v1 业务处理器。

## 故障排查

| 现象 | 检查项 |
|---|---|
| Outbox 待发布积压 | 应用开关、Broker 健康、连接/vhost权限、端口及日志 |
| Broker 恢复仍有失败事件 | 查看失败记录；达到发布上限的事件不自动重放 |
| 队列有消息但没有预警 | 消费者配置、MySQL连接、规则是否启用、最新库存是否低于阈值 |
| 出现重复投递 | Broker 确认与数据库提交不是原子事务，核对消费幂等与轨迹 |
| 死信或未知版本 | 核对异常载荷、版本和失败原因，不直接修改库存抵消事件 |

只读排查表为 `async_outbox_message`、`async_consumed_message`、`async_message_trace`、`async_failure_record`、`safety_stock_rule`、`low_stock_alert`。不要清开发队列、改库存或手工重发消息作为常规恢复方式。

当前没有异常人工补偿管理 API/页面、自动失败重放或积压告警。RabbitMQ 单节点命名卷提供本地持久化，不代表高可用或备份。正式运维恢复流程需结合授权、备份和审计另行建立。

启动与数据卷见 [Docker 手册](docker.md)，真实 Broker 回归方法见 [测试说明](../testing.md)。
