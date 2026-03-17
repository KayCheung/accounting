# step-04-middleware · 中间件集成与基础设施封装

## 资源声明

| 类型 | 文件 | 读取时机 |
|------|------|----------|
| Java 编码规范 | `docs/ai-rules/java.md` | 每次生成前必读 |
| 账务领域规范 | `docs/ai-rules/accounting.md` | 分布式锁 / 本地消息表规范必读 |
| 流水域 PO | Step 3 产出：`LocalMessagePO` / `MessageReceiptPO` | 4.2 实现依赖 |
| 支撑域 PO | Step 3 产出：`DictionaryPO` | 4.3 字典缓存依赖 |

---

## 1. 任务目标（Mission）

完成 RocketMQ、Redis（Redisson）、Prometheus 三个核心中间件的封装，
建立本地消息表（Outbox Pattern）可靠消息机制和分布式锁基础设施，
为 Phase 3 配置管理模块及后续记账引擎提供可靠的中间件底座。

本 Step 由 TL 负责，通过调用 `@Java` Agent 依次完成各子任务。

**子任务分配**：

| 子任务 | 详细文件                                     | 说明 |
|--------|------------------------------------------|------|
| 4.1 RocketMQ 封装 | `docs/prompt/tasks/step-04-task-1-mq.md` | 无前置依赖，第一个执行 |
| 4.2 本地消息表机制 | `docs/prompt/tasks/step-04-task-2-outbox.md`   | 依赖 4.1 完成 |
| 4.3 Redis / 分布式锁 / 字典缓存 | `docs/prompt/tasks/step-04-task-3-redis.md`    | 无前置依赖，可与 4.1 并行 |
| 4.4 Prometheus + 告警规则 | `docs/prompt/tasks/step-04-task-4-monitor.md`  | 依赖 4.1~4.3 完成 |

---

## 2. 执行顺序

```
4.1 MQ 封装 ──┐
              ├──▶ 4.2 本地消息表 ──▶ 4.4 监控告警
4.3 Redis ───┘
```

4.1 和 4.3 无互相依赖，可同时交给 `@Java` 在两个会话中并行执行。
4.2 依赖 4.1 的 `OnsProducerTemplate`，需等 4.1 完成。
4.4 在全部封装完成后配置，验证整体可观测性。

---

## 3. 通用规范

- 所有封装类放在 `accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/` 下对应子目录
- 分布式锁 Key 必须自动拼接 `tenantId` 前缀，格式：`accounting:{tenantId}:lock:{业务Key}`
- RocketMQ 使用 **Aliyun ONS Client**，不使用 RocketMQ 原生客户端
- 所有中间件封装类必须有完整 Javadoc，说明职责、使用方式、异常处理策略

---

## 4. 完成标准（Checklist）

### TL 执行项
- [ ] 4.1 ~ 4.4 全部子任务 `@Java` 生成完毕，代码已 Review
- [ ] Prometheus 指标已在本地 `/actuator/prometheus` 可访问
- [ ] P0 / P1 告警规则配置完毕

### @Java 产出验收
- [ ] `OnsProducerTemplate` 三种发送模式单测通过
- [ ] `AbstractMqConsumer` 幂等校验单测通过（相同 `message_id` 重复消费返回 `CommitMessage`）
- [ ] 本地消息表补偿 Job 单测通过（含指数退避间隔验证）
- [ ] `DistributedLockTemplate` 加锁 / 自动续期 / 失败抛异常单测通过
- [ ] 字典二级缓存单测通过（本地缓存命中 → Redis 回源 → DB 回源三条路径）

---

## 5. 下一步行动

进入 **Step 5 · Domain Alignment**，详见 `docs/prompt/step-05-alignment.md`。
