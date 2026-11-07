# step-04-task-1 · RocketMQ 封装（Aliyun ONS）

> 对应 `step-04-middleware.md` 子任务 4.1。
> 无前置依赖，可第一个执行。

---

## 任务范围

封装统一的 ONS 消息发送模板和消费者基类，屏蔽 Aliyun ONS 原生 API 细节。

**产出文件**：
```
infrastructure/messaging/
├── OnsProducerTemplate.java       # 统一发送模板
└── AbstractMqConsumer.java        # 消费者抽象基类
```

---

## 实现规范

### OnsProducerTemplate

- 支持三种发送模式：同步（`send`）、异步（`sendAsync`）、延迟（`sendDelay`）
- 每条消息的 `UserProperties` 必须强制注入：
  - `tenantId`：从 `TenantContext` 获取
  - `traceId`：通过 `TraceContext.traceId()` 从 Skywalking SDK 获取，**严禁自研**
- 发送失败只记录 `error` 级别日志（含 `topic` / `messageId` / `tenantId`），**不抛异常阻塞主流程**
- 可靠性由本地消息表保障（见 task-2），本类不做重试

### AbstractMqConsumer

- 子类只需实现 `doConsume(Message msg)` 方法
- 基类内置消费幂等逻辑：
  1. 以 `message_id` 查询 `t_message_receipt`，已存在则直接返回 `CommitMessage`（幂等跳过）
  2. 执行 `doConsume()`
  3. 成功：写入 `t_message_receipt`（`status=1`）→ 返回 `CommitMessage`
  4. 失败：写入 `t_message_receipt`（`status=2`，含 `errorCode` / `errorMessage`）→ 返回 `ReconsumeLater`
- `consumer_group` 字段取子类注解或构造参数，不得硬编码

---

## Prompt 模板

```
@Java

【任务】
实现 OnsProducerTemplate（统一 ONS 消息发送模板）和 AbstractMqConsumer（消费者抽象基类）

【输入】
- docs/ai-rules/java.md（编码规范）
- docs/ai-rules/accounting.md（MQ 可靠性规范部分）
- docs/prompt/step-04-middleware.md（通用规范）
- docs/prompt/tasks/step-04-task-1-mq.md（本文件，实现规范）
- Step 3 产出：MessageReceiptPO 路径（accounting-core/.../infrastructure/persistence/entity/）

【输出】
- OnsProducerTemplate.java（含三种发送模式，完整 Javadoc）
- AbstractMqConsumer.java（含幂等校验逻辑，完整 Javadoc）
- 对应单测：OnsProducerTemplateTest.java / AbstractMqConsumerTest.java
每个文件首行注释写完整路径

【不要做】
- 不要使用 RocketMQ 原生客户端，必须使用 Aliyun ONS Client
- 不要在发送失败时抛异常阻塞主流程
- 不要自研 traceId 获取逻辑，必须使用 TraceContext.traceId()
- 不要在基类中硬编码 consumer_group
```

---

## 完成标准（Checklist）

- [ ] `OnsProducerTemplate` 三种发送模式代码完整，Javadoc 齐全
- [ ] `AbstractMqConsumer` 幂等校验逻辑正确，子类扩展点清晰
- [ ] 单测覆盖：相同 `message_id` 重复消费返回 `CommitMessage`（幂等跳过）
- [ ] 单测覆盖：消费失败时 `t_message_receipt` 写入 `status=2`
- [ ] 发送失败场景只打日志，不抛异常
