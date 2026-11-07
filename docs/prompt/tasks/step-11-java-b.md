# step-11-java-b · AsyncPostingDomainService + RollbackDomainService

> **Step 11 子任务** | 归属：`@Java` 工程师-B
> 前置依赖：Step 11 Java-A（实时过账 + 余额计算器已完成）

---

## 1. 任务目标

实现异步过账领域服务、回滚领域服务。

核心职责：
1. **AsyncPostingDomainService**：MQ 异步过账（本地消息写入 + 消费端过账逻辑 + 状态联动）
2. **RollbackDomainService**：实时路径失败回滚 + 单边记账回滚 + 重试判断
3. **PostingMessagePayload**：过账 MQ 消息体
4. **PostingMessageConsumer**：MQ 消费端监听器（可选，与 AsyncPostingDomainService 合并）

---

## 2. 必读资源

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、事务规范、POJO 规范 |
| 2 | `docs/ai-rules/accounting.md` | 财务律法、状态机、并发控制 |
| 3 | `docs/prompt/step-11-transaction.md` | Step 11 总体任务说明（§4.4 MQ 异步过账 / §4.5 回滚 / §4.6 单边回滚） |
| 4 | `docs/sql/2-voucher.sql` | `t_accounting_voucher` / `t_accounting_voucher_entry` DDL |
| 5 | `docs/sql/5-journal.sql` | `t_transaction` / `t_business_record` DDL |
| 6 | `docs/sql/6-infra.sql` | `t_local_message` / `t_message_receipt` DDL |
| 7 | `docs/design/domain-model.md` | 账户域、凭证域模型 |
| 8 | `accounting-core/.../infrastructure/messaging/LocalMessageService.java` | **已有**本地消息服务 |
| 9 | `accounting-core/.../domain/service/PostingDomainService.java` | Java-A 已实现的实时过账领域服务 |
| 10 | `accounting-core/.../infrastructure/account/AccountBalanceCalculator.java` | Java-A 已实现的余额计算器 |
| 11 | `accounting-core/.../repository/AccountingVoucherRepository.java` | 凭证仓储 |
| 12 | `accounting-core/.../repository/TransactionRepository.java` | 事务仓储 |

---

## 3. AsyncPostingDomainService（异步过账领域服务）

新建 `accounting-core/.../domain/service/AsyncPostingDomainService.java`。

```java
@Service
@RequiredArgsConstructor
public class AsyncPostingDomainService {

    private final LocalMessageService localMessageService;
    private final AccountingVoucherRepository accountingVoucherRepository;
    private final AccountRepository accountRepository;
    private final SubAccountRepository subAccountRepository;
    private final AccountDetailRepository accountDetailRepository;
    private final SubAccountDetailRepository subAccountDetailRepository;
    private final AccountBalanceCalculator balanceCalculator;

    /**
     * 写入异步过账本地消息（与实时过账同事务）
     *
     * 是否记账：否（仅写入消息，余额变更由消费端执行）
     * 异常处理：
     *   - [RuntimeException] → 由上层 catch，同事务自动回滚
     *
     * @param entry   分录记录（is_unilateral=0 且 is_buffered=0）
     * @param voucher 凭证记录
     */
    public void writeAsyncPostingMessage(
        AccountingVoucherEntryPO entry,
        AccountingVoucherPO voucher);

    /**
     * MQ 消费端：执行异步过账（独立事务）
     *
     * 是否记账：是
     * 异常处理：
     *   - [AccountException] → 余额不足 → 消费失败，触发重试
     *   - [RuntimeException] → 系统异常 → 消费失败，触发重试
     *
     * 幂等：先检查分录 status 是否已是 2，是则直接返回成功
     */
    public void consumeAsyncPostingMessage(PostingMessagePayload payload);

    /**
     * 检查凭证所有非缓冲分录（含异步+实时）是否都已过账
     *
     * 是否记账：否
     */
    public boolean areAllNonBufferEntriesPosted(String voucherNo);

    /**
     * 联动更新凭证状态和事务状态（MQ 消费端调用）
     * 当所有非缓冲分录都过账成功后：
     *   - 凭证 status=3(已过账)
     *   - 事务 status=2(成功)
     *
     * 是否记账：是
     */
    public void updateStatusIfAllNonBufferPosted(String voucherNo);
}
```

### writeAsyncPostingMessage 执行流程

```
输入：分录记录 + 凭证记录
  ↓
1. 构建 PostingMessagePayload（含 voucherNo, entryId, accountNo, subjectCode,
    debitCredit, changeDirection, amount, accountingDate, currency, summary）
2. 调用 localMessageService.sendLocalMessage(topic, tag, entryId, payload)
   （写入 t_local_message，status=1 待发送，与实时过账同事务）
```

### consumeAsyncPostingMessage 执行流程

```
输入：PostingMessagePayload（从 MQ 消息体反序列化）
  ↓
1. 【幂等检查】查询分录 status
   ↓ 已是 2(已过账) → 直接返回成功（防重复消费）
   ↓ 否则继续
2. 查询账户 AccountPO → 查询子账户 SubAccountPO
3. 按 account_no 升序 SELECT FOR UPDATE 加锁
4. 余额计算：balanceCalculator.calculateNewBalance()
5. 更新主账户余额 + version
6. 更新子账户余额 + version
7. 写入 t_account_detail（含 Pre/Post 快照）
8. 写入 t_sub_account_detail
9. 更新分录 status=2(已过账)
10. 调用 updateStatusIfAllNonBufferPosted(voucherNo)
    → 检查所有非缓冲分录 → 全部已过账 → 更新凭证/事务状态
11. 更新 t_local_message.status=2(已发送)
12. 写入 t_message_receipt（消费成功）
```

---

## 4. RollbackDomainService（回滚领域服务）

新建 `accounting-core/.../domain/service/RollbackDomainService.java`。

```java
@Service
@RequiredArgsConstructor
public class RollbackDomainService {

    private final TransactionTemplate transactionTemplate;
    private final TransactionRepository transactionRepository;
    private final AccountingVoucherRepository accountingVoucherRepository;
    private final BusinessRecordRepository businessRecordRepository;
    private final AccountRepository accountRepository;
    private final SubAccountRepository subAccountRepository;
    private final AccountDetailRepository accountDetailRepository;
    private final SubAccountDetailRepository subAccountDetailRepository;
    private final AccountBalanceCalculator balanceCalculator;

    /**
     * 实时过账失败回滚（事务外调用，使用独立事务更新状态）
     *
     * 是否记账：是（更新状态记录）
     * 异常处理：
     *   - [RuntimeException] → 独立事务包裹，不影响调用方
     *
     * @param txnNo     事务编号
     * @param voucherNo 凭证号
     * @param traceNo   系统跟踪号
     * @param failReason 失败原因
     */
    public void markTransactionFailed(
        String txnNo,
        String voucherNo,
        String traceNo,
        String failReason);

    /**
     * 单边记账回滚（异步分录失败，实时分录已提交）
     *
     * 是否记账：是（生成反向凭证 + 反向操作已过账分录）
     * 异常处理：
     *   - [AccountException] → 余额不足/账户异常 → 记录失败 + 触发告警
     *
     * 生成反向凭证号（REV + 原 voucherNo），写入 t_accounting_voucher 一条真实记录。
     * 反向分录使用新 entryId（REV_ENTRY + 序号），避免唯一约束冲突。
     *
     * @param voucherNo  原凭证号
     * @param txnNo      事务编号
     * @param failReason 失败原因
     */
    public void executeRollbackForAsyncFailure(
        String voucherNo,
        String txnNo,
        String failReason);

    /**
     * 判断异常是否可重试
     */
    public boolean isRetryable(Throwable ex);
}
```

### markTransactionFailed 执行流程

```
输入：txnNo + voucherNo + traceNo + failReason
  ↓
使用独立 TransactionTemplate 执行：
  1. 更新 t_transaction.status = 3(失败)，记录 fail_reason + finishTime
  2. 更新 t_business_record.status = 3(失败)（通过 traceNo 关联）
  3. 更新 t_accounting_voucher.status = 4(过账失败)
  4. 更新 t_accounting_voucher_entry.status = 3(过账失败)
  5. 提交
```

### executeRollbackForAsyncFailure 执行流程（P2-8 修复）

```
输入：voucherNo + txnNo + failReason
  ↓
1. 查询原凭证 + 所有已过账的实时分录（status=2）
2. 生成反向凭证号："REV" + voucherNo（如 REVVOU20260512000001）
3. 使用独立 TransactionTemplate 执行：
   a. 写入反向凭证到 t_accounting_voucher（status=4 过账失败，
      summary 标注"异步回滚-原凭证:xxx"）
   b. 对每个已过账分录：
      i.   生成新 entryId："REV_ENTRY" + 序号
      ii.  查询账户（SELECT FOR UPDATE）
      iii. 反向计算余额：changeDirection 取反（1↔2）
      iv.  更新主账户 + 子账户余额
      v.   写入反向明细到 t_account_detail（voucher_no=反向凭证号，
           summary="异步回滚-原entryId:xxx"，含 Pre/Post 快照）
      vi.  写入反向明细到 t_sub_account_detail（P2-2 修复）
      vii. 更新原分录 status=3(过账失败)
   c. 更新原凭证 status=4(过账失败)，记录失败原因
   d. 更新事务 status=3(失败)，记录失败原因
   e. 提交
```

### isRetryable 实现

```java
private static final List<Class<? extends Throwable>> RETRYABLE_EXCEPTIONS = List.of(
    PessimisticLockingFailureException.class,  // 锁冲突
    TimeoutException.class,                     // 超时
    TransientDataAccessResourceException.class  // 临时数据库异常
);

public boolean isRetryable(Throwable ex) {
    return RETRYABLE_EXCEPTIONS.stream().anyMatch(c -> c.isInstance(ex));
}
```

---

## 5. PostingMessagePayload（MQ 消息体）

新建 `accounting-core/.../messaging/PostingMessagePayload.java`。

```java
@Data
@AllArgsConstructor
@NoArgsConstructor  // Jackson 反序列化需要
public class PostingMessagePayload {
    private String voucherNo;
    private String entryId;
    private String accountNo;
    private String subjectCode;
    private Integer debitCredit;        // 1=借, 2=贷
    private Integer changeDirection;    // 1=增, 2=减（P2-7 补充）
    private BigDecimal amount;
    private LocalDate accountingDate;
    private String currency;
    private String summary;
}
```

---

## 6. MQ 消费端监听器（可选）

新建 `accounting-core/.../infrastructure/messaging/PostingMessageConsumer.java`。

或使用 `@RocketMQMessageListener` 注解在 `AsyncPostingDomainService` 上直接监听。

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class PostingMessageConsumer {

    private final AsyncPostingDomainService asyncPostingDomainService;
    private final RollbackDomainService rollbackDomainService;

    private static final int MAX_RETRY = 3;
    private static final long[] RETRY_DELAYS = {10_000, 30_000, 60_000}; // 10s, 30s, 60s

    @RocketMQMessageListener(
        topic = "${rocketmq.topic.posting}",
        consumerGroup = "${rocketmq.consumer-group.posting}",
        tag = "POSTING_ENTRY"
    )
    public void onMessage(String message) {
        PostingMessagePayload payload = JSON.parseObject(message, PostingMessagePayload.class);
        int retryCount = 0;
        while (true) {
            try {
                asyncPostingDomainService.consumeAsyncPostingMessage(payload);
                return; // 消费成功
            } catch (Exception e) {
                if (retryCount >= MAX_RETRY || !rollbackDomainService.isRetryable(e)) {
                    log.error("异步过账消费失败且不可重试: entryId={}", payload.getEntryId(), e);
                    // 调用单边回滚
                    asyncPostingDomainService.executeRollbackOnAsyncFailure(
                        payload.getVoucherNo(), payload.getEntryId(), e.getMessage());
                    throw e;
                }
                try {
                    Thread.sleep(RETRY_DELAYS[retryCount]);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
                retryCount++;
                log.warn("异步过账消费重试: attempt={}/{}", retryCount, MAX_RETRY);
            }
        }
    }
}
```

> **注意**：消费端自行实现指数退避重试逻辑，而非依赖 RocketMQ 默认重试间隔。不可重试异常（如余额不足）直接调用单边回滚并抛出，触发告警。

---

## 7. 需要创建的文件清单

| 文件 | 说明 |
|------|------|
| `AsyncPostingDomainService.java` | 异步过账领域服务 |
| `RollbackDomainService.java` | 回滚领域服务 |
| `PostingMessagePayload.java` | MQ 过账消息体 |
| `PostingMessageConsumer.java` | MQ 消费端监听器（可选） |

---

## 8. 需要修改的文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `BusinessRecordMapper.java` | 确认方法 | Step 9 已有 `updateStatusByTraceNo`，确认方法可用 |
| `BusinessRecordRepository.java` | 确认方法 | Step 9 已有，确认方法可用 |

---

## 9. 完成标准（Checklist）

- [ ] `AsyncPostingDomainService` 异步过账领域服务
- [ ] `writeAsyncPostingMessage`：本地消息写入（与实时过账同事务）
- [ ] `consumeAsyncPostingMessage`：MQ 消费端过账（独立事务，幂等检查：分录 status 已是 2 则直接返回）
- [ ] 消费端：按 account_no 升序加锁 → 余额计算 → 更新余额 → 记录明细
- [ ] 消费端：更新分录 status + 联动更新凭证状态 + 事务状态
- [ ] `areAllNonBufferEntriesPosted` 方法
- [ ] `updateStatusIfAllNonBufferPosted` 方法
- [ ] `RollbackDomainService` 回滚领域服务
- [ ] `markTransactionFailed`：通过独立事务更新事务/流水/凭证/分录状态
- [ ] `executeRollbackForAsyncFailure`：生成反向凭证号 REV+原号，新 entryId 避免唯一约束冲突
- [ ] `isRetryable` 方法（区分临时/永久异常）
- [ ] `PostingMessagePayload` 消息体（含 changeDirection，P2-7 修复）
- [ ] MQ 消费端监听器（含异常抛出触发重试）
- [ ] 单测：MQ 消费幂等、消费失败重试、单边回滚、isRetryable 判断
