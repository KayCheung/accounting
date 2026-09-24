# step-15-balance-query · 余额查询接口

> **Phase 5 第三步** | 归属：`@Java` 工程师（BE-A）
> 前置依赖：Step 14（资金冻结与解冻 — 子账户余额 + 冻结记录已就绪）
>
> **定位**：本 Step 为纯查询模块，不涉及写入操作。核心目标是提供高性能的聚合余额查询 + 明细分页查询 + 冻结记录查询接口，供前端页面与 MCP Tool 调用。

---

## 0. 前置补充任务（Java-A 开始编码前必须完成）

| # | 任务 | 涉及文件 | 说明 |
|---|------|---------|------|
| P0-1 | `AccountMapper` 补充余额查询方法 | `AccountMapper.java` + `AccountMapper.xml` | 按 accountNo 查询主账户余额 + 状态 + 风控状态 |
| P0-2 | `SubAccountMapper` 补充聚合查询 | `SubAccountMapper.java` + `SubAccountMapper.xml` | 按 accountNo 聚合查询可用/冻结子账户余额 |
| P0-3 | `AccountDetailMapper` 补充分页查询 | `AccountDetailMapper.java` + `AccountDetailMapper.xml` | 按 accountNo + 日期范围 + 类型过滤分页查询 |
| P0-4 | `BufferPostingDetailMapper` 补充缓冲预估 | `BufferPostingDetailMapper.java` + `BufferPostingDetail.xml` | 按 accountNo 查询待入账缓冲金额汇总 |
| P0-5 | `FreezeDetailMapper` 补充列表查询 | `FreezeDetailMapper.java`（已有）+ `FreezeDetailMapper.xml`（补充） | 按 accountNo 分页查询冻结记录 |
| P0-6 | `AccountRepository` / `SubAccountRepository` 等封装 | 对应 Repository 文件 | 封装 Mapper 方法，供 ApplicationService 调用 |

### P0-1: AccountMapper 补充余额查询

```java
// AccountMapper.java
/**
 * 按 accountNo 查询主账户余额 + 状态 + 风控状态
 */
AccountPO selectBalanceByAccountNo(@Param("accountNo") String accountNo);
```

对应 XML：

```xml
<select id="selectBalanceByAccountNo" resultType="AccountPO">
    SELECT account_no, account_name, subject_code, balance,
           status, risk_status, balance_direction, currency
    FROM t_account
    WHERE account_no = #{accountNo}
      AND is_delete = 0
</select>
```

### P0-2: SubAccountMapper 补充聚合查询

```java
// SubAccountMapper.java
/**
 * 按 accountNo 查询所有子账户余额
 */
List<SubAccountPO> selectBalanceByAccountNo(@Param("accountNo") String accountNo);
```

对应 XML：

```xml
<select id="selectBalanceByAccountNo" resultType="SubAccountPO">
    SELECT account_no, balance_type, balance_direction, balance, version
    FROM t_sub_account
    WHERE account_no = #{accountNo}
      AND is_delete = 0
</select>
```

### P0-3: AccountDetailMapper 补充分页查询

```java
// AccountDetailMapper.java
/**
 * 分页查询账户明细（按 accountNo + 日期范围 + 类型过滤）
 */
List<AccountDetailPO> selectPageByCondition(
    @Param("accountNo") String accountNo,
    @Param("startDate") LocalDate startDate,
    @Param("endDate") LocalDate endDate,
    @Param("tradeType") Integer tradeType,
    @Param("debitCredit") Integer debitCredit,
    @Param("offset") Long offset,
    @Param("limit") Integer limit);

/**
 * 统计账户明细总数（条件同上）
 */
Long countByCondition(
    @Param("accountNo") String accountNo,
    @Param("startDate") LocalDate startDate,
    @Param("endDate") LocalDate endDate,
    @Param("tradeType") Integer tradeType,
    @Param("debitCredit") Integer debitCredit);
```

对应 XML：

```xml
<select id="selectPageByCondition" resultType="AccountDetailPO">
    SELECT id, voucher_no, entry_id, txn_no, trace_no, trace_seq,
           subject_code, account_no, business_code, trading_code, pay_channel,
           trade_type, trade_time, debit_credit, change_direction,
           currency, pre_balance, amount, post_balance,
           accounting_date, summary, create_time
    FROM t_account_detail
    WHERE account_no = #{accountNo}
      AND is_delete = 0
      <if test="startDate != null">
          AND accounting_date &gt;= #{startDate}
      </if>
      <if test="endDate != null">
          AND accounting_date &lt;= #{endDate}
      </if>
      <if test="tradeType != null">
          AND trade_type = #{tradeType}
      </if>
      <if test="debitCredit != null">
          AND debit_credit = #{debitCredit}
      </if>
    ORDER BY trade_time DESC, id DESC
    LIMIT #{limit} OFFSET #{offset}
</select>

<select id="countByCondition" resultType="java.lang.Long">
    SELECT COUNT(*)
    FROM t_account_detail
    WHERE account_no = #{accountNo}
      AND is_delete = 0
      <if test="startDate != null">
          AND accounting_date &gt;= #{startDate}
      </if>
      <if test="endDate != null">
          AND accounting_date &lt;= #{endDate}
      </if>
      <if test="tradeType != null">
          AND trade_type = #{tradeType}
      </if>
      <if test="debitCredit != null">
          AND debit_credit = #{debitCredit}
      </if>
</select>
```

### P0-4: BufferPostingDetailMapper 补充缓冲预估

```java
// BufferPostingDetailMapper.java
/**
 * 按 accountNo 查询待入账缓冲金额汇总
 */
BigDecimal sumPendingAmountByAccountNo(@Param("accountNo") String accountNo);
```

对应 XML：

```xml
<select id="sumPendingAmountByAccountNo" resultType="java.math.BigDecimal">
    SELECT COALESCE(SUM(amount), 0)
    FROM t_buffer_posting_detail
    WHERE account_no = #{accountNo}
      AND status IN (1, 2)
      AND is_delete = 0
</select>
```

### P0-5: FreezeDetailMapper 补充列表查询

```java
// FreezeDetailMapper.java（在已有基础上补充）
/**
 * 分页查询冻结记录（按 accountNo 关联 t_account）
 */
List<AccountFreezeDetailPO> selectPageByAccountNo(
    @Param("accountNo") String accountNo,
    @Param("status") Integer status,
    @Param("offset") Long offset,
    @Param("limit") Integer limit);

/**
 * 统计冻结记录总数
 */
Long countByAccountNo(
    @Param("accountNo") String accountNo,
    @Param("status") Integer status);
```

对应 XML：

```xml
<select id="selectPageByAccountNo" resultType="AccountFreezeDetailPO">
    SELECT f.id, f.voucher_no, f.txn_no, f.business_code, f.trading_code,
           f.trace_no, f.trace_seq, f.trade_time, f.freeze_amount,
           f.status, f.expire_time, f.summary, f.create_time
    FROM t_account_freeze_detail f
    INNER JOIN t_account a ON f.account_no = a.account_no
    WHERE a.account_no = #{accountNo}
      AND f.is_delete = 0
      <if test="status != null">
          AND f.status = #{status}
      </if>
    ORDER BY f.create_time DESC
    LIMIT #{limit} OFFSET #{offset}
</select>

<select id="countByAccountNo" resultType="java.lang.Long">
    SELECT COUNT(*)
    FROM t_account_freeze_detail f
    INNER JOIN t_account a ON f.account_no = a.account_no
    WHERE a.account_no = #{accountNo}
      AND f.is_delete = 0
      <if test="status != null">
          AND f.status = #{status}
      </if>
</select>
```

> **注意**：`t_account_freeze_detail` 表没有 `account_no` 字段，需要通过 `txn_no` 或 `voucher_no` 关联 `t_accounting_voucher` 或 `t_transaction` 获取 `account_no`。
>
> **修正方案**：由于 `t_account_freeze_detail` 没有直接的 `account_no` 字段，需要通过关联查询。但为简化查询，我们在 `selectPageByAccountNo` 中改为直接按 `voucher_no` 关联 `t_accounting_voucher` 获取 `account_no`，或者更直接的方式——在应用层先获取账户对应的 `account_no`，再通过关联 `t_transaction` 获取冻结记录。
>
> **最终方案**：考虑到 `t_account_freeze_detail` 通过 `txn_no` 关联 `t_transaction`，而 `t_transaction` 不直接关联 `account_no`。冻结记录实际上是通过业务逻辑与账户关联的（冻结操作时指定了 accountNo）。因此最简方案是：**在 FreezeDomainService 查询时，先通过业务逻辑获取该账户相关的冻结记录（应用层过滤）**。
>
> **但这样性能不好**。让我们检查 DDL——`t_account_freeze_detail` 确实没有 `account_no` 字段。这意味着冻结记录需要通过 `txn_no` → `t_transaction` → 间接关联。
>
> **最合理方案**：在 `t_account_freeze_detail` 表增加 `account_no` 字段（通过 Flyway 迁移脚本），这样查询才能走索引。这是 DDL 设计的遗漏，Step 15 需要补充。

### P0-6: Repository 封装

各 Repository 封装对应 Mapper 方法，保持与已有模式一致（参考 `FreezeDetailRepository` / `AccountRepository`）。

---

## 0.5 DDL 补充（P0-5 修正）

` t_account_freeze_detail` 表缺少 `account_no` 字段，导致无法直接按账户查询冻结记录。需要补充迁移脚本：

```sql
-- docs/sql/8-balance-query-alter.sql
USE `accounting`;

-- 为 t_account_freeze_detail 增加 account_no 字段（便于按账户查询冻结记录）
ALTER TABLE t_account_freeze_detail
    ADD COLUMN account_no VARCHAR(32) NOT NULL DEFAULT '' COMMENT '账户编号' AFTER voucher_no;

-- 增加索引
ALTER TABLE t_account_freeze_detail
    ADD INDEX idx_account_no (account_no, status);

-- 已有数据回填（通过 txn_no 关联 t_transaction 获取 trace_no，再关联 t_business_record）
-- 实际部署时需根据业务数据编写回填脚本
UPDATE t_account_freeze_detail f
    INNER JOIN t_transaction t ON f.txn_no = t.txn_no
    INNER JOIN t_business_record r ON t.trace_no = r.trace_no
    SET f.account_no = r.account_no
    WHERE f.account_no = '';
```

> **注意**：`t_business_record` 也没有 `account_no` 字段。因此回填逻辑需要通过业务追溯号 `trace_no` 关联 `t_accounting_voucher` 或 `t_accounting_voucher_entry` 获取 `account_no`。
>
> **最简方案**：由于冻结记录是 Step 14 才引入的，如果是全新环境没有历史数据，可以直接加字段 + 索引即可，无需回填。如果有历史数据，需要编写专门的迁移脚本。

---

## 1. 任务目标（Mission）

实现余额查询相关接口，支持以下用例：

1. **聚合余额查询**：查询指定账户的完整余额信息（主账户余额 + 可用子账户余额 + 冻结子账户余额 + 缓冲预估金额）
2. **账户明细分页查询**：按日期范围、交易类型、借贷方向等条件分页查询账户变动明细
3. **冻结记录分页查询**：按账户查询冻结记录列表（可按状态过滤）

> **核心原则**：纯查询操作，不涉及写入；性能优先，确保查询走索引 + 分区裁剪。

---

## 2. 必读资源

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、查询规范、POJO 规范 |
| 2 | `docs/ai-rules/accounting.md` | 余额方向约束、查询规范 |
| 3 | `docs/sql/1-account.sql` | 账户/子账户/明细/冻结 DDL |
| 4 | `docs/sql/3-rule.sql` | 缓冲记账明细 DDL |
| 5 | `docs/prompt/step-14-freeze.md` | Step 14 冻结记录结构 |
| 6 | `accounting-core/.../interfaces/FreezeController.java` | Controller 编写模式参考 |
| 7 | `accounting-core/.../application/FreezeApplicationService.java` | ApplicationService 编排模式参考 |

---

## 3. 任务分配

| 工程师 | 详细文件 | 负责内容 |
|--------|---------|---------|
| Java-A | `docs/prompt/tasks/step-15-java-a.md` | P0-1~P0-6 补充 + BalanceQueryDomainService（聚合查询 + 明细查询 + 冻结查询）+ DDL 补充 |
| Java-B | `docs/prompt/tasks/step-15-java-b.md` | 性能优化（分区裁剪验证 + EXPLAIN 验证 + 索引优化） |
| Java-C | `docs/prompt/tasks/step-15-java-c.md` | Application Service + Controller（3 个接口）+ DTO + Assembler |

> **依赖关系**：Java-A 最先完成（Mapper/Repository 补充 + 领域服务 + DDL）。Java-B 依赖 Java-A 的 SQL 进行性能验证。Java-C 依赖 Java-A。建议串行执行：A → C → B（B 可并行）。

---

## 4. 核心业务规则

### 4.1 聚合余额查询

```
输入：accountNo（账户编号）

输出：
  - accountNo：账户编号
  - accountName：账户名称
  - subjectCode：科目编码
  - mainBalance：主账户余额（t_account.balance）
  - availableBalance：可用子账户余额（t_sub_account WHERE balance_type=1）
  - frozenBalance：冻结子账户余额（t_sub_account WHERE balance_type=2）
  - bufferEstimate：缓冲预估金额（t_buffer_posting_detail WHERE status IN (1,2)）
  - totalAvailable：总可用 = availableBalance + frozenBalance（冻结余额仍属账户所有）
  - status：账户状态
  - riskStatus：风控状态
  - balanceDirection：余额方向
  - currency：币种

查询策略：
  1. 查询主账户（1 次 SQL）
  2. 查询子账户列表（1 次 SQL，按 accountNo）
  3. 汇总缓冲预估（1 次 SQL，按 accountNo + status）
  4. 应用层组装聚合结果

性能目标：P99 < 100ms
```

### 4.2 账户明细分页查询

```
输入：
  - accountNo（必填）
  - startDate / endDate（选填，过滤会计日期范围）
  - tradeType（选填：1-正常, 2-调账, 3-红, 4-蓝）
  - debitCredit（选填：1-借, 2-贷）
  - pageNo / pageSize（分页参数）

输出：PageResponse<AccountDetailResponse>

查询策略：
  1. 使用 OFFSET/LIMIT 分页
  2. WHERE 条件必须走 idx_accounting_date 索引
  3. 按 trade_time DESC, id DESC 排序（最新在前）

分区裁剪验证：
  - t_account_detail 未分区，走 idx_accounting_date 复合索引
  - EXPLAIN 必须显示 using index，不能出现全表扫描

性能目标：P99 < 200ms（100 万级数据）
```

### 4.3 冻结记录分页查询

```
输入：
  - accountNo（必填）
  - status（选填：1-冻结, 2-已解冻）
  - pageNo / pageSize（分页参数）

输出：PageResponse<FreezeListResponse>

查询策略：
  1. 使用 OFFSET/LIMIT 分页
  2. WHERE account_no + status 走 idx_account_no 索引
  3. 按 create_time DESC 排序（最新在前）

性能目标：P99 < 100ms
```

---

## 5. 接口契约

所有接口路径前缀：`/accounting/account/balance`

### 5.1 GET `/accounting/account/balance/aggregate` — 聚合余额查询

**请求参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| accountNo | String | 是 | 账户编号 |

**响应** (`AggregateBalanceResponse`):
```json
{
  "accountNo": "00120260301000001",
  "accountName": "张三基本户",
  "subjectCode": "1002",
  "mainBalance": 1000.00,
  "availableBalance": 700.00,
  "frozenBalance": 300.00,
  "bufferEstimate": 50.00,
  "totalAvailable": 1000.00,
  "status": 1,
  "statusDesc": "正常",
  "riskStatus": 1,
  "riskStatusDesc": "正常",
  "balanceDirection": 1,
  "balanceDirectionDesc": "借方",
  "currency": "CNY",
  "queryTime": "2026-06-12 10:00:00"
}
```

### 5.2 GET `/accounting/account/balance/details` — 账户明细分页查询

**请求参数**（继承 `PageRequest`）：
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| accountNo | String | 是 | 账户编号 |
| startDate | String | 否 | 起始日期 yyyy-MM-dd |
| endDate | String | 否 | 结束日期 yyyy-MM-dd |
| tradeType | Integer | 否 | 交易类别 |
| debitCredit | Integer | 否 | 借贷方向 |

**响应** (`PageResponse<AccountDetailResponse>`):
```json
{
  "total": 150,
  "pages": 8,
  "current": 1,
  "list": [
    {
      "voucherNo": "VCH20260612000001",
      "entryId": "ENT20260612000001",
      "txnNo": "TXN20260612000001",
      "traceNo": "TRC20260612000001",
      "subjectCode": "1002",
      "accountNo": "00120260301000001",
      "businessCode": "LOAN",
      "tradingCode": "REPAYMENT",
      "tradeType": 1,
      "tradeTypeDesc": "正常",
      "tradeTime": "2026-06-12 10:00:00",
      "debitCredit": 1,
      "debitCreditDesc": "借方",
      "changeDirection": 1,
      "changeDirectionDesc": "增加",
      "currency": "CNY",
      "preBalance": 900.00,
      "amount": 100.00,
      "postBalance": 1000.00,
      "accountingDate": "2026-06-12",
      "summary": "贷款还款入账"
    }
  ]
}
```

### 5.3 GET `/accounting/account/balance/freeze-records` — 冻结记录分页查询

**请求参数**（继承 `PageRequest`）：
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| accountNo | String | 是 | 账户编号 |
| status | Integer | 否 | 状态：1-冻结, 2-已解冻 |

**响应** (`PageResponse<FreezeListResponse>`):
```json
{
  "total": 5,
  "pages": 1,
  "current": 1,
  "list": [
    {
      "freezeId": "FRZ20260611000001",
      "accountNo": "00120260301000001",
      "freezeAmount": 300.00,
      "status": 1,
      "statusDesc": "冻结",
      "expireTime": "2026-12-31 23:59:59",
      "tradeTime": "2026-06-11 10:00:00",
      "createTime": "2026-06-11 10:00:00",
      "summary": "客户异常交易风控"
    }
  ]
}
```

---

## 6. 需要创建/修改的文件

### 新建 DDL 补充

```
docs/sql/8-balance-query-alter.sql          # t_account_freeze_detail 补充 account_no 字段 + 索引
```

### 新建（Java-A）

```
accounting-core/
└── src/main/java/com/kltb/accounting/core/
    ├── domain/
    │   └── service/
    │       └── BalanceQueryDomainService.java    # 聚合查询 + 明细查询 + 冻结查询领域服务
    └── infrastructure/persistence/repository/
        └── AccountDetailRepository.java          # 账户明细仓储（如尚未存在则新建）
```

### 新建（Java-C）

```
accounting-api/
└── src/main/java/com/kltb/accounting/api/
    ├── request/
    │   ├── BalanceAggregateRequest.java          # 聚合余额查询请求（仅 accountNo）
    │   ├── AccountDetailQueryRequest.java         # 账户明细查询请求（含分页）
    │   └── FreezeRecordQueryRequest.java          # 冻结记录查询请求（含分页）
    └── response/
        ├── AggregateBalanceResponse.java          # 聚合余额响应
        └── AccountDetailResponse.java             # 账户明细响应
```

### 修改

| 文件 | 变更内容 |
|------|---------|
| `AccountMapper.java` | 新增 `selectBalanceByAccountNo` |
| `AccountMapper.xml` | 新增对应 SQL |
| `AccountRepository.java` | 新增 `selectBalanceByAccountNo` |
| `SubAccountMapper.java` | 新增 `selectBalanceByAccountNo` |
| `SubAccountMapper.xml` | 新增对应 SQL |
| `SubAccountRepository.java` | 新增 `selectBalanceByAccountNo` |
| `AccountDetailMapper.java` | 新增 `selectPageByCondition` / `countByCondition` |
| `AccountDetailMapper.xml` | 新增对应 SQL |
| `BufferPostingDetailMapper.java` | 新增 `sumPendingAmountByAccountNo` |
| `BufferPostingDetailMapper.xml` | 新增对应 SQL |
| `FreezeDetailMapper.java` | 新增 `selectPageByAccountNo` / `countByAccountNo` |
| `FreezeDetailMapper.xml` | 新增对应 SQL |
| `ResultCode.java` | 新增错误码（见下方） |

### 新增错误码

| Code | Enum | Description |
|------|------|-------------|
| `"3021"` | `ACCOUNT_NO_REQUIRED` | 账户编号不能为空 |

---

## 7. BalanceQueryDomainService 核心方法

```java
@Service
@RequiredArgsConstructor
public class BalanceQueryDomainService {

    private final AccountRepository accountRepository;
    private final SubAccountRepository subAccountRepository;
    private final BufferPostingDetailRepository bufferPostingDetailRepository;
    private final AccountDetailRepository accountDetailRepository;
    private final FreezeDetailRepository freezeDetailRepository;

    /**
     * 聚合余额查询（主账户 + 可用 + 冻结 + 缓冲预估）
     */
    public AggregateBalanceDTO queryAggregateBalance(String accountNo);

    /**
     * 账户明细分页查询
     */
    public PageDTO<AccountDetailDTO> queryAccountDetails(
            String accountNo, LocalDate startDate, LocalDate endDate,
            Integer tradeType, Integer debitCredit, int pageNo, int pageSize);

    /**
     * 冻结记录分页查询
     */
    public PageDTO<FreezeListDTO> queryFreezeRecords(
            String accountNo, Integer status, int pageNo, int pageSize);
}
```

> 说明：`PageDTO` 为领域层分页对象，`AccountDetailDTO` / `FreezeListDTO` 为领域层 DTO。
> 如项目无独立领域层 DTO，可直接使用 PO + 在 ApplicationService 组装。

---

## 8. 编码要点

### 8.1 查询规范

- 禁止使用 `SELECT *`，必须明确列出字段
- WHERE 条件必须走索引，禁止全表扫描
- 分页查询必须返回 total 和 pages
- 排序必须稳定（至少包含一个唯一键字段作为次要排序条件）

### 8.2 性能优化

```
1. 聚合查询：3 次独立 SQL（主账户 + 子账户 + 缓冲预估），应用层组装
   - 不使用 JOIN，避免锁升级
   - 子账户查询一次获取所有类型余额

2. 明细查询：
   - WHERE account_no + accounting_date 走 idx_accounting_date 索引
   - 日期范围查询利用分区裁剪（如已分区）

3. 冻结记录查询：
   - WHERE account_no + status 走 idx_account_no 索引
   - 按 create_time DESC 排序
```

### 8.3 字典翻译

响应中的枚举字段需要翻译为中文描述：
- `status`：1-正常, 2-冻结, 3-注销
- `riskStatus`：1-正常, 2-止入, 3-止出, 4-止入止出
- `balanceDirection`：1-借方, 2-贷方
- `tradeType`：1-正常, 2-调账, 3-红, 4-蓝
- `debitCredit`：1-借方, 2-贷方
- `changeDirection`：1-增加, 2-减少

翻译逻辑放在 Assembler 层完成。

### 8.4 空值处理

- 账户不存在：返回错误码 `ACCOUNT_NOT_FOUND`
- 子账户不存在：对应余额返回 0
- 无缓冲记录：bufferEstimate 返回 0
- 明细/冻结记录为空：返回空列表（total = 0）

---

## 9. 完成标准（Checklist）

### Java-A
- [ ] P0-1~P0-6 前置补充任务完成
- [ ] DDL 补充脚本（`t_account_freeze_detail` 增加 `account_no` + 索引）
- [ ] `BalanceQueryDomainService.queryAggregateBalance` 聚合查询（3 次 SQL + 应用层组装）
- [ ] `BalanceQueryDomainService.queryAccountDetails` 明细分页查询
- [ ] `BalanceQueryDomainService.queryFreezeRecords` 冻结分页查询
- [ ] 所有 SQL 走索引，无全表扫描
- [ ] 字典翻译完整（状态/方向/类型等枚举字段）

### Java-B
- [ ] EXPLAIN 验证所有查询走索引
- [ ] 分区裁剪验证（如适用）
- [ ] 聚合查询 P99 < 100ms
- [ ] 明细查询 P99 < 200ms
- [ ] 冻结查询 P99 < 100ms

### Java-C
- [ ] `BalanceQueryApplicationService` 编排 3 个用例
- [ ] `BalanceQueryController` 实现 3 个接口
- [ ] `AggregateBalanceRequest` / `AccountDetailQueryRequest` / `FreezeRecordQueryRequest` DTO
- [ ] `AggregateBalanceResponse` / `AccountDetailResponse` DTO
- [ ] `BalanceQueryAssembler` 完成 PO → Response 转换
- [ ] 参数校验：accountNo 必填、日期格式合法、分页参数合法

### TL Review
- [ ] 查询性能达标（EXPLAIN + 实际压测）
- [ ] 无 N+1 查询问题
- [ ] 字典翻译完整且正确
- [ ] 空值/异常处理正确
- [ ] DDL 补充脚本正确且可回滚

---

## 10. 与后续 Step 的关系

| Step | 依赖关系 |
|------|---------|
| Step 16（缓冲记账） | 无直接依赖 |
| Step 19（MCP 接入） | 余额查询将作为第一个 MCP Tool 暴露 |
| Step 23（前端业务页面） | 余额查询页面依赖本 Step 接口 |

---

## 11. 下一步行动

进入 **Step 16 · Buffer Posting（缓冲记账）**，详见 `docs/prompt/step-16-buffer-posting.md`。
