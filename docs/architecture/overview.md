# 架构说明

StockPilot 使用 Java 17、Spring Boot 3.2.3、MyBatis-Plus 3.5.5、Flyway、Spring Security/JWT 和 MySQL 8。后端为单 Maven 模块、单 Spring Boot JAR；前端为 Vue 3、TypeScript、Vite 和 Element Plus。

## 模块职责

| 后端模块 | 职责 |
|---|---|
| masterdata | 仓库、库位、分类、SKU、供应商；可选商品/仓库详情缓存 |
| security | 登录、实时授权、用户角色权限管理、安全审计 |
| inventory | 库存查询、统一库存写入口、不可变流水；count 子模块管理静态盘点 |
| purchase / sales | 采购入库、销售冻结与出库状态机 |
| transfer | 源仓冻结、调出、在途和目标收货 |
| messaging | 同库 Outbox、RabbitMQ 投递、消费幂等、轨迹和失败记录 |
| alert | 查询 MySQL 最新可用量，创建或解除安全库存预警 |
| ai | 模型适配、受限只读工具、候选澄清和证据展示 |
| shared | 通用响应、分页、操作人契约、异常、配置和健康检查 |

模块内部按 `controller → service → mapper` 组织，按需保留 `domain`、`request`、`vo` 和 `infrastructure`。Controller 不调用 Mapper，跨模块只能调用公开 Service 或稳定的数据契约。源码架构测试检查跨模块 Mapper、shared 反向依赖、模块循环和安全实现依赖。

## 库存写入边界

采购、销售、调拨和盘点只通过 `InventoryMutationApplicationService` 写库存。库存余额条件更新、流水新增和对应单据状态在同一 MySQL 本地事务中提交。单据行锁、数量下限、版本条件和唯一约束共同处理并发与重复动作。

消息模块在采购/销售完成事务中写 Outbox；销售冻结/释放、调拨冻结/释放/收货、盘点调整使用 inventory 的稳定事件契约，通过同步事件监听在同一事务中记录 Outbox，避免 inventory 反向依赖 messaging。提交后异步投递；消费者通过 alert 公开 Service 更新预警，不负责库存扣减。Redis 只缓存商品/仓库详情，失败降级 MySQL，业务有效性校验仍直接读取数据库。

AI 调用公开业务 Service，每个工具检查权限及参数，不接受模型 SQL。模型网络请求不在数据库事务内；数量由 MySQL/BigDecimal 计算。当前冻结来源核对使用短期 REPEATABLE_READ 只读快照。

## 认证和前端

JWT 只保存身份和有效期。每次请求从 MySQL 重载用户、角色与权限；停用和授权变化即时影响已有 Token 的访问能力。密码使用 BCrypt，JWT 密钥由后端环境提供。未知接口默认拒绝。首次管理员创建、角色绑定和审计处于同一事务；通过 ADMIN 角色行锁协调并发初始化，不提升已有同名用户权限。

前端 `frontend/src/modules` 按业务划分页面/API/类型，公共组件与请求工具位于 `shared`。菜单、路由和按钮检查权限，最终授权、数量和状态机以服务端为准。开发环境通过 Vite 代理同源 `/api`，当前没有生产前端托管或新增跨域方案。Element Plus 的组件、指令和样式按页面局部引入；数量输入和输出采用十进制字符串契约。

运行步骤见 [启动手册](../operations/startup.md)，SQL 见 [数据库说明](database.md)，业务约束见 [库存规则](../business/inventory-rules.md)。
