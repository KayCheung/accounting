# step-04-task-3 · Redis 封装（分布式锁 + 字典缓存）

> 对应 `step-04-middleware.md` 子任务 4.3。
> 无前置依赖，可与 task-1 并行执行。

---

## 任务范围

基于 Redisson 封装分布式锁模板，基于 Caffeine + Redis 实现字典二级缓存。

**产出文件**：
```
infrastructure/redis/
├── DistributedLockTemplate.java   # 分布式锁封装
└── DictionaryCacheService.java    # 字典二级缓存
```

---

## 实现规范

### DistributedLockTemplate

核心方法：

```java
/**
 * 加锁执行，失败抛 IDEMPOTENT_CONFLICT
 * @param lockKey    业务锁 Key（不含前缀，内部自动拼接 tenantId）
 * @param waitTime   等待加锁超时时间（秒）
 * @param leaseTime  持锁最大时间（秒），-1 表示启用 watchdog 自动续期
 * @param supplier   加锁后执行的业务逻辑
 */
<T> T execute(String lockKey, long waitTime, long leaseTime, Supplier<T> supplier);
```

- **Key 格式**：`accounting:{tenantId}:lock:{lockKey}`，`tenantId` 从 `TenantContext` 自动获取
- **加锁失败**：必须抛出 `ServiceException(ResultCode.IDEMPOTENT_CONFLICT)`，**不得返回 null 或静默忽略**
- **自动续期**：`leaseTime = -1` 时使用 Redisson watchdog 自动续期（默认 30s），适用于执行时间不确定的场景
- **锁释放**：finally 块保证释放，避免死锁

### DictionaryCacheService

二级缓存策略：

```
读取顺序：Caffeine 本地缓存 → Redis → DB（回源时同步写回两层缓存）

Caffeine：TTL 5 分钟，最大 1000 条，防止 Redis 故障时系统不可用
Redis：TTL 10 分钟，Key 格式：accounting:{tenantId}:dict:{dictType}

写入 / 更新 / 删除字典后，同步执行：
  1. 删除 Caffeine 本地缓存对应 key
  2. 删除 Redis 对应 key
  （不主动刷新，下次读取时回源重建）
```

- 提供 `getByType(String dictType)`：按类型获取全量字典项列表
- 提供 `refresh(String dictType)`：手动刷新指定类型缓存（管理后台调用）
- 提供 `refreshAll()`：刷新所有缓存（谨慎使用，仅供运维操作）

---

## Prompt 模板

```
@Java

【任务】
实现基于 Redisson 的分布式锁模板（DistributedLockTemplate）
和基于 Caffeine + Redis 的字典二级缓存（DictionaryCacheService）

【输入】
- docs/ai-rules/java.md（编码规范）
- docs/ai-rules/accounting.md（分布式锁 Key 规范、幂等设计部分）
- docs/prompt/step-04-middleware.md（通用规范）
- docs/prompt/tasks/step-04-task-3-redis.md（本文件，实现规范）
- Step 3 产出：DictionaryPO.java / DictionaryMapper.java

【输出】
- DistributedLockTemplate.java（含 Javadoc，说明 Key 格式、失败处理、续期策略）
- DictionaryCacheService.java（含二级缓存完整实现）
- 对应单测：DistributedLockTemplateTest.java / DictionaryCacheServiceTest.java
每个文件首行注释写完整路径

【不要做】
- 加锁失败不得静默忽略，必须抛 IDEMPOTENT_CONFLICT
- 锁 Key 不得由调用方自行拼接前缀，必须在 DistributedLockTemplate 内部统一处理
- 字典缓存不得只做单层（Redis 故障时必须有 Caffeine 兜底）
```

---

## 完成标准（Checklist）

- [ ] `DistributedLockTemplate` Key 格式正确，含 `tenantId` 前缀
- [ ] 单测：加锁失败抛出 `ServiceException(ResultCode.IDEMPOTENT_CONFLICT)`
- [ ] 单测：`leaseTime = -1` 时 watchdog 自动续期生效
- [ ] `DictionaryCacheService` 三条读取路径（本地 → Redis → DB）均已实现
- [ ] 单测：字典更新后，本地缓存和 Redis 缓存均被清除
