# step-04-task-4 · Prometheus 监控 + 告警规则

> 对应 `step-04-middleware.md` 子任务 4.4。
> ⚠️ 依赖 task-1 ~ task-3 全部完成后执行。

---

## 任务范围

配置 Prometheus 指标暴露，定义 P0 / P1 两级告警规则，
确保记账链路的核心健康状态可观测。

**产出文件**：
```
infrastructure/monitor/
└── AccountingMetrics.java         # 自定义业务指标定义

resources/
└── alert-rules.yml                # Prometheus 告警规则配置
```

---

## 实现规范

### AccountingMetrics 自定义指标

需要暴露以下业务指标（使用 Micrometer API）：

| 指标名 | 类型 | 说明 |
|--------|------|------|
| `accounting_posting_total` | Counter | 记账请求总数，含 `status`（success/failed）标签 |
| `accounting_posting_duration_seconds` | Timer | 记账执行耗时（P99 重点关注）|
| `accounting_buffer_pending_count` | Gauge | 缓冲队列待处理数量（实时从 DB 读取）|
| `accounting_local_message_failed_count` | Gauge | 本地消息表失败数量（status=3）|
| `accounting_eod_status` | Gauge | 日切状态：1-正常 / 0-异常 |

### alert-rules.yml 告警规则

**P0 级（立即介入，影响资金安全）**：

```yaml
- alert: PostingFailureRateHigh
  expr: rate(accounting_posting_total{status="failed"}[5m]) /
        rate(accounting_posting_total[5m]) > 0.001
  for: 1m
  annotations:
    summary: "记账失败率超过 0.1%"

- alert: EodFailed
  expr: accounting_eod_status == 0
  for: 0m
  annotations:
    summary: "日切失败，需立即人工介入"

- alert: TrialBalanceFailed
  expr: accounting_trial_balance_status == 0
  for: 0m
  annotations:
    summary: "试算平衡不通过，账务数据异常"
```

**P1 级（30 分钟内响应）**：

```yaml
- alert: BufferQueueBacklog
  expr: accounting_buffer_pending_count > 10000
  for: 5m
  annotations:
    summary: "缓冲队列积压超过 1 万条"

- alert: LocalMessageFailed
  expr: accounting_local_message_failed_count > 0
  for: 5m
  annotations:
    summary: "本地消息表存在失败消息，MQ 投递异常"
```

---

## Prompt 模板

```
@Java

【任务】
实现自定义业务指标类（AccountingMetrics）和 Prometheus 告警规则配置（alert-rules.yml）

【输入】
- docs/ai-rules/java.md（编码规范）
- docs/prompt/step-04-middleware.md（通用规范）
- docs/prompt/tasks/step-04-task-4-monitor.md（本文件，指标定义和告警规则）
- task-1 ~ task-3 产出：OnsProducerTemplate / LocalMessageRetryJob / DistributedLockTemplate
  （了解各组件结构，确认指标采集切入点）

【输出】
- AccountingMetrics.java（Micrometer 自定义指标，含 Javadoc 说明每个指标的采集时机）
- alert-rules.yml（P0 + P1 两级告警规则）
- 验证说明：本地启动后 /actuator/prometheus 应能看到自定义指标
每个文件首行注释写完整路径

【不要做】
- 不要自行实现指标收集框架，使用 Micrometer API
- 不要把告警阈值硬编码在 Java 代码中，配置在 alert-rules.yml
- P0 告警的 for 值不超过 1 分钟，P1 不超过 5 分钟
```

---

## 完成标准（Checklist）

- [ ] `AccountingMetrics` 五个指标定义完整，Javadoc 说明采集时机
- [ ] `alert-rules.yml` P0 三条 / P1 两条规则格式正确
- [ ] 本地启动后 `/actuator/prometheus` 可见自定义指标
- [ ] P0 告警 `for` 值不超过 1 分钟，确保快速响应
