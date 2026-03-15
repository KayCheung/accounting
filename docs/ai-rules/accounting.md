# 账务领域规范（accounting.md）

## 一、财务核心律法（最高优先级，违反即为架构级 Bug）

### 1.1 绝对值计算法则

```
严禁负数运算，严禁通过负数冲正。

同向相加：newBalance = oldBalance + amount
反向相减：前置校验 oldBalance >= amount
         不足则抛 ServiceException(ResultCode.INSUFFICIENT_BALANCE)
```

- **严禁 SQL 计算余额**：禁止 `SET balance = balance + ?`
- 必须在 Java 内存中计算，记录 Pre / Post 余额快照后再写库

### 1.2 借贷平衡

- 每张凭证必须满足 `ΣDebit == ΣCredit`，不平衡则**阻断流程，不写库**
- **先证后账**：凭证持久化并通过借贷平衡校验后，才允许过账更新余额

### 1.3 红冲原则

- 采用"方向对调"：原凭证"借 A 贷 B" → 红冲凭证"借 B 贷 A"
- 金额始终保持正数，确保科目总账借贷发生额统计真实准确

### 1.4 配置驱动

- 过账逻辑必须动态解析 `t_accounting_rule`，**严禁硬编码科目号**

---

## 二、幂等设计（四层防护）

| 层次 | 实现方式 | 防护目标 |
|------|----------|----------|
| 入口幂等 | 分布式锁 + `trace_no + trace_seq` 唯一约束 | 防重复请求 |
| 凭证幂等 | `voucher_no` 唯一索引 | 防重复生成凭证 |
| 账户幂等 | `account_no` 唯一索引 + `SELECT FOR UPDATE` | 防并发重复开户 |
| MQ 消费幂等 | `message_id` 去重 + `t_message_receipt` 记录 | 防重复消费 |

**锁 Key 规范**（自动拼接 `tenantId` 前缀实现租户隔离）：

```
入口幂等锁：accounting:{tenantId}:lock:idempotent:trace:{trace_no}-{trace_seq}
引擎执行锁：accounting:{tenantId}:lock:posting:trx:{voucher_no}
```

- 加锁失败必须抛出 `ServiceException(ResultCode.IDEMPOTENT_CONFLICT)`，**不得静默忽略**

---

## 三、并发控制

### 3.1 悲观锁（实时路径）

```java
// 涉及多账户，必须按 account_no 升序，防止死锁
List<String> sorted = accountNos.stream().sorted().collect(Collectors.toList());
List<Account> locked = accountRepo.selectForUpdate(sorted);
```

### 3.2 乐观锁（缓冲路径）

```
默认：version 字段 CAS 更新
失败重试：最多 3 次，指数退避
超阈值：升级为 SELECT FOR UPDATE 悲观锁
仍失败：标记 status=4（失败）+ 触发告警，不阻塞其他账户
```

---

## 四、会计日期管理

- 会计日期在**业务流水入库时确定**（写入 `t_business_record.accounting_date`），全链路不可变更
- 日切后新请求写入 T+1，T 日存量业务继续在原会计日期处理至清理完毕
- 缓冲 Job 必须按 `accounting_date` 分区处理，**严禁跨日混处理**

---

## 五、关键状态机

```
凭证状态：
  PENDING(1) ──[过账开始]──▶ POSTING(2)
                               ├──[全部分录过账成功]──▶ POSTED(3)
                               └──[过账失败]──▶ FAILED(4)
  POSTED(3) ──[红冲]──▶ REVERSED(5)

分录状态：
  PENDING(1) ──[过账成功]──▶ POSTED(2)

事务状态：
  PROCESSING ──[成功]──▶ SUCCESS
             └──[失败/回滚]──▶ FAILED

账户状态：
  NORMAL(1) ──[冻结操作]──▶ FROZEN(2) ──[注销（余额=0）]──▶ CANCELLED(3)
  任意状态可叠加止入 / 止出风控标志（互不影响主状态）

本地消息状态：
  PENDING(1) ──[发送中]──▶ SENDING(2) ──[成功]──▶ SENT(3)
                                         └──[重试超限]──▶ FAILED(4)
```

---

## 六、日终核算（EOD）五阶段

```
阶段 1：瞬间切日        全局会计日期更新为 T+1，刷新缓存，新请求写 T+1
阶段 2：存量清理        处理所有 accounting_date=T 未完成的缓冲和事务
                        ⚠️ 失败必须 P0 阻断，严禁强制推进
阶段 3：余额快照        写入 t_account_balance_snapshot（snapshot_type=1 日快照）
阶段 4：试算平衡        ① 借贷平衡：ΣDebit == ΣCredit
                        ② 总分核对：科目总账 vs 分户余额合计
                        ③ 余额核对：账户余额 vs 明细最后一条 post_balance
                        ⚠️ 任一失败必须阻断归档，触发 P0 告警
阶段 4.5：期末结转      可选，查 t_period_end_transfer_rule 按 execute_order 执行
阶段 5：归档            标记 T 日账务关闭，记录日切完成时间
```

---

## 七、缓冲入账三种模式

| 模式 | 触发 | 锁策略 | Running Balance | 适用场景 |
|------|------|--------|----------------|----------|
| 逐条（buffer_mode=1） | 实时 | 乐观锁优先 | 逐条计算 | 准实时，可容忍秒级延迟 |
| 日间批量（buffer_mode=2） | 定时（如 5 分钟） | 汇总后悲观锁 | 按时间序反算 | 高频小额，减少锁竞争 |
| 日终批量（buffer_mode=3） | EOD 阶段 2 | 同上 | 同上 | 内部核算，实时性最低 |

**Running Balance 末条校验**：`post_balance_N` 必须等于账户当前余额，不等则触发告警。

---

## 八、禁止行为速查

| 类别 | 禁止行为 |
|------|----------|
| 财务计算 | SQL 计算余额 / 负数运算 / `new BigDecimal(100.0)` / `amount.equals()` |
| 事务 | `@Transactional` 注解 / 直接 `producer.send()` 不做补偿 |
| 锁 | 多账户加锁不升序 / 加锁失败静默忽略 / 锁 Key 无 tenantId 前缀 |
| 架构 | `accounting-api` 引入持久层 / 科目号硬编码 |
| 文件 | 修改 `docs/` 只读文件（`FIN-Core_Blueprint.md` 除外） |
| 代码质量 | 省略实现 / 未理解业务逻辑就生成代码 |
