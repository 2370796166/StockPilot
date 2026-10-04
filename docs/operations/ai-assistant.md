# 只读 AI 仓储助手

登录管理后台后进入 `/ai-assistant`。AI 默认关闭；不启用模型也可正常使用核心库存业务。模型只组织解释，数量和来源由 MySQL / BigDecimal 与后端权限校验决定。

## 配置

根目录 `.env` 中设置以下变量，修改后重启后端：

| 变量 | 默认值 | 说明 |
|---|---|---|
| AI_ENABLED | false | 是否调用模型 |
| AI_PROVIDER | CUSTOM | CUSTOM、QWEN、DEEPSEEK、GLM、DOUBAO、MOONSHOT |
| AI_BASE_URL | 空 | 覆盖预设地址，不包含 /chat/completions；QWEN/CUSTOM 必填 |
| AI_API_KEY | 空 | 后端密钥，不放入前端或提交到 Git |
| AI_MODEL | 空 | 账户实际可用、支持工具调用/JSON回答/非思考模式的模型或接入点 ID |
| AI_REQUEST_TIMEOUT | 20s | 单次模型 HTTP 时限，100ms～60s |
| AI_MAX_TOOL_CALLS | 6 | 单问题执行工具上限，1～8 |

例如：

```dotenv
AI_ENABLED=true
AI_PROVIDER=DEEPSEEK
AI_BASE_URL=
AI_MODEL=替换为账户实际可用且支持工具调用的模型名
AI_API_KEY=替换为仅保存在本机的密钥
```

供应商预设的当前代码配置：

| 供应商 | 基地址 | 适配行为 |
|---|---|---|
| QWEN | 必须显式填写账户地域/工作空间兼容地址 | enable_thinking=false |
| DEEPSEEK | https://api.deepseek.com | thinking.type=disabled |
| GLM | https://open.bigmodel.cn/api/paas/v4 | thinking.type=disabled |
| DOUBAO | https://ark.cn-beijing.volces.com/api/v3 | thinking.type=disabled |
| MOONSHOT | https://api.moonshot.cn/v1 | thinking.type=disabled，temperature=0.6 |
| CUSTOM | 必须显式填写兼容服务地址 | 通用兼容工具调用协议 |

预设只是本项目适配器配置，不代表供应商所有模型均可用。地址、密钥和模型需要属于同一服务/地域；强制思考模型不适用。正式基地址要求 HTTPS，只有 localhost/127.0.0.1 可用 HTTP 做本地模拟，不跟随重定向。当前没有在线模型配置页、密钥管理接口或自动切换供应商。

## 查询与权限

| 能力 | 权限与范围 |
|---|---|
| 商品/仓库/库位候选 | MASTER_DATA_READ，同名需用户选择 |
| 单仓/多仓库存 | INVENTORY_READ + MASTER_DATA_READ；仓库全范围汇总、库位分页 |
| 库存流水 | 库存和基础资料读取权限；限定维度的一页流水 |
| 按准确单号查单据 | 对应采购、销售、调拨或盘点 READ 权限 |
| 按准确流水号追溯 | 库存读取 + 来源单据读取权限 |
| 时间段变化汇总 | 库存和基础资料读取；一个商品、仓库、可选库位，最多92个自然日 |
| 当前冻结来源核对 | 库存、基础资料、销售和调拨读取四项权限全部具备 |

每次工具独立检查当前权限，模型和客户端 ID 不决定授权。缺少条件须澄清；候选选择后重新校验，不自动选择第一条。数量以十进制字符串返回，前端不自行用浮点数计算。

示例问题需替换为真实名称/单号：

- “A 商品在一号仓还有多少实际、可用和冻结库存？”
- “查询销售出库单 SOxxx 的状态和明细。”
- “流水 LGxxx 对应哪张业务单据？”
- “A 商品在一号仓从2026-10-01到2026-10-03有哪些库存变化？按业务动作汇总。”
- “A 商品在一号仓当前冻结来自哪些销售单和调拨单？”

## 证据与限制

结果包含查询时间、后端数量、分页范围和来源页面。时间区间包含起止日，按 Asia/Shanghai 计算；区间差量不等于期初/期末余额，不证明完整版本链。冻结/释放不是实际出库，调拨/盘亏不是销售消耗。

当前冻结来源只统计销售 RESERVED/APPROVED 和源端调拨 SUBMITTED/APPROVED；取消、出库、调出、在途和盘点维度锁不计入当前占用。余额与来源总量/分页在10秒 REPEATABLE_READ只读快照内核对，总量相同不证明逐库位/逐单据一致；差异保留，不自动修正库存。

模型请求不持有数据库事务。工具白名单拒绝 SQL、额外参数或模型网络地址；模型只接收白名单业务字段，不接收密码、JWT、后台密钥、操作人身份或备注。模型最终文字只提供定性解释，权威数字单独展示。

单次问题最多1000字符，候选选择最多3项，每页最多20项；模型响应最多64 KiB，最终文字最多2000字符。工具调用后最多一次最终整理请求。不是流式交互，前端 HTTP 时限为650秒，慢供应商可能等待较久。

## 状态与验证边界

关闭、配置缺失、无权限、无数据、缺参、候选选择、查询失败、超时、模型错误、无效响应和工具上限分别显示状态。查询失败不等于零库存，模型整理失败保留已查结构化结果。

接口为 `POST /api/ai/questions`，请求包含 `question` 和 `selections`；响应使用统一 ApiResponse，含 `status`、`answer`、`results` 和 `queriedAt`。

自动化测试使用 Mockito、MockMvc、组件测试和本机假供应商，不能证明真实国内模型兼容性。真实供应商联调仍未验证；本次真实数据库、应用接口与浏览器流程也未验证，见 [测试说明](../testing.md)。

没有 AI 写操作、自动审核、库存调整、预测、补货、OCR、知识库或会话记忆。使用前先按 [启动手册](startup.md) 确认基础业务正常。
