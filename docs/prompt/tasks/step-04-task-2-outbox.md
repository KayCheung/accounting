# step-04-task-2 · 本地消息表机制（Outbox Pattern）

> 对应 `step-04-middleware.md` 子任务 4.2。
> ⚠️ 依赖 task-1 完成（需要 `OnsProducerTemplate`）。

---

## 任务范围

实现本地消息表的补偿 Job，保障消息与业务数据的最终一致性。
写入 `t_local_message` 的逻辑由业务方在 `TransactionTemplate` 中自行调用，本任务只负责补偿扫描部分。

**产出文件**：
```
infrastructure/mq/
└── LocalMessageService.java       # 本地消息表写入 / 查询封装

job/
└── LocalMessageRetryJob.java      # XXL-JOB 补偿扫描任务
```

---

## 实现规范

### LocalMessageService

- `save(LocalMessagePO message)`：写入本地消息表，**必须在业务 TransactionTemplate 内调用**，由调用方保证事务
- `markSent(String messageId)`：标记 `status=2`（已发送）
- `markFailed(String messageId, String reason)`：标记 `status=3`（失败）
- `queryPending(int limit)`：查询待发送消息（`status=1`，按 `next_retry_time <= NOW()` 过滤）

### LocalMessageRetryJob（XXL-JOB）

补偿逻辑：

```
1. 扫描 t_local_message：status=1（待发送）且 next_retry_time <= NOW()，每批最多 100 条
2. 逐条调用 OnsProducerTemplate 发送
3. 发送成功：更新 status=2（已发送）
4. 发送失败：
   - retry_count < max_retry：
       retry_count + 1
       next_retry_time = NOW() + 指数退避间隔（第1次10s / 第2次30s / 第3次60s）
   - retry_count >= max_retry：
       status=3（失败），记录 fail_reason，触发告警日志（ERROR 级别）
```

- Job 执行幂等：同一条消息不得被并发处理（可通过乐观锁或状态机保障）
- 支持 XXL-JOB 分片参数（`shardIndex` / `shardTotal`），按 `id % shardTotal = shardIndex` 分片

---

## Prompt 模板

```
@Java

【任务】
实现本地消息表的写入封装（LocalMessageService）和补偿扫描 Job（LocalMessageRetryJob）

【输入】
- docs/ai-rules/java.md（编码规范）
- docs/ai-rules/accounting.md（本地消息表规范部分）
- docs/prompt/step-04-middleware.md（通用规范）
- docs/prompt/tasks/step-04-task-2-outbox.md（本文件，实现规范）
- task-1 产出：OnsProducerTemplate.java（发送依赖）
- Step 3 产出：LocalMessagePO.java（数据对象）

【输出】
- LocalMessageService.java（消息写入 / 查询封装，含 Javadoc）
- LocalMessageRetryJob.java（XXL-JOB 任务，含分片逻辑）
- 对应单测：LocalMessageRetryJobTest.java
  重点场景：指数退避间隔验证 / 超重试次数标记失败 / 分片逻辑
每个文件首行注释写完整路径

【不要做】
- 不要在 LocalMessageService 中开启新事务，写入必须由调用方的事务保障
- 不要一次扫描全量数据，单批限制 100 条
- 不要忽略分片参数，必须支持 XXL-JOB 分片执行
```

---

## 完成标准（Checklist）

- [ ] `LocalMessageService` 四个方法实现完整，Javadoc 说明事务边界
- [ ] `LocalMessageRetryJob` 补偿逻辑正确，分片参数生效
- [ ] 单测：第 1 次失败后 `next_retry_time` 为 NOW + 10s
- [ ] 单测：第 2 次失败后 `next_retry_time` 为 NOW + 30s
- [ ] 单测：超过 `max_retry` 后 `status` 更新为 3，触发 ERROR 日志
