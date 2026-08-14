# StockPilot 开发规则

## 项目定位

StockPilot 是面向中小型制造或电商企业的智能仓储与库存管理平台，核心目标是正确实现采购入库、销售出库、库存冻结、库存流水及后续调拨、盘点和安全库存预警。功能数量服从核心业务正确性。

## 技术栈

- Java 17、Spring Boot 3、Maven
- 已接入 MyBatis-Plus、MySQL 8、Flyway、Spring Security、JWT、Docker Compose、JUnit 5
- 计划在核心库存业务完成后接入 Redis、RabbitMQ；Vue 3 后端稳定后再开发
- 架构为模块化单体；禁止自行引入 Spring Cloud、微服务、Kubernetes 或分布式事务

## 长期架构规则

- 单一 Spring Boot 部署单元，按业务能力划分包和模块。
- 业务包内部按 `controller`、`application`、`domain`、`infrastructure` 等职责组织，但不为模仿 DDD 创建空壳接口、重复模型或无行为对象。
- Controller 只处理 HTTP 适配、参数校验和响应转换，只能调用 Application Service，禁止直接调用 Mapper。
- Application Service 组织用例和事务，可以调用本模块 Mapper、必要的领域规则及其他模块公开服务。
- Mapper 只负责数据访问，不编排业务流程；跨模块禁止调用对方 Mapper。
- Entity 对应持久化记录；Request/DTO 用于输入；VO 用于输出；Command 仅用于有独立业务意图且参数较复杂或需要复用的写操作。
- 不机械创建 `Service + ServiceImpl`。只有确有多实现或隔离需求时才定义接口。
- 采购、销售、调拨和盘点不得直接更新库存表，库存写入必须统一收口到库存服务。

## 数据与中间件规则

- MySQL 是库存数据唯一权威来源。Redis 缓存或锁不能决定库存最终正确性。
- Redis 仅用于缓存、短期限流、JWT 辅助或入口幂等等可丢失、可恢复的辅助能力。
- RabbitMQ 仅用于允许延迟的真实异步业务；不得把核心库存扣减交给消费者。
- 未到用户指定阶段不得提前接入 Redis 或 RabbitMQ。
- 库存操作必须同时考虑本地事务、并发条件更新、业务幂等、唯一约束和不可变库存流水审计。
- 核心库存数据不得通过普通接口物理删除或任意调整。

## 开发前检查

修改代码前必须按顺序阅读：

1. `AGENTS.md`
2. `docs/progress.md`
3. `docs/requirements.md`
4. 与任务相关的 `docs/architecture.md`、`docs/inventory-rules.md`、`docs/database.md`
5. `docs/decisions.md`
6. 当前真实代码、配置、测试和 Git 状态

如果文档与代码不一致，以真实代码为准，并更新文档说明差异。

## 验证与真实性

- 修改后至少执行 `mvn -s .mvn/settings.xml clean test`。
- 修改构建或运行配置后还需执行打包、Docker Compose 配置检查，并在环境允许时启动应用验证相关接口。
- 数据库变更必须验证迁移/初始化脚本在目标 MySQL 版本可执行。
- 禁止虚构测试结果、压测结果、TPS、P95、P99、并发容量或性能提升。
- 禁止为了让测试通过而删除、跳过、放宽断言或弱化已有测试。
- 未执行的验证必须明确写为“未验证”；失败不得描述为通过。
- 每次只完成用户指定范围，禁止提前开发后续功能。
- 不擅自删除用户文件、重置工作区或覆盖有效文档。
