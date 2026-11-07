# Step 17: EOD & Trial Balance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现日切与试算平衡引擎 —— 日终前置检查、日余额计算、试算平衡、期末结转、余额快照生成 + XXL-JOB 调度 + 3 个 API 接口

**Architecture:** 4 个 DomainService（EodCheck / TrialBalance / Eod / PeriodEndTransfer）各司其职，EodApplicationService 做编排层，EodJobHandler 定时调度（23:55），EodController 暴露 3 个 REST 接口。全部使用 TransactionTemplate 管理事务，严禁 @Transactional。

**Tech Stack:** Java 17, Spring Boot 3.x, MyBatis-Plus, XXL-JOB, MySQL 5.7, Lombok

---

## File Structure

### 新建文件（15 个）

| 文件 | 职责 |
|------|------|
| `accounting-core/.../repository/AccountBalanceRepository.java` | 日余额仓储封装 |
| `accounting-core/.../repository/AccountBalanceSnapshotRepository.java` | 快照仓储封装 |
| `accounting-core/.../repository/PeriodEndTransferRuleRepository.java` | 结转规则仓储封装 |
| `accounting-core/.../repository/PeriodEndTransferRecordRepository.java` | 结转记录仓储封装 |
| `accounting-core/.../domain/service/EodCheckDomainService.java` | 5 项日切前置检查 |
| `accounting-core/.../domain/service/TrialBalanceDomainService.java` | 试算平衡（科目汇总 + 差额阈值） |
| `accounting-core/.../domain/service/EodDomainService.java` | 日余额计算 + 批量 Upsert + 快照生成 |
| `accounting-core/.../domain/service/PeriodEndTransferDomainService.java` | 期末结转规则执行 + 凭证生成 |
| `accounting-core/.../application/service/EodApplicationService.java` | 应用层编排（手动触发用例） |
| `accounting-core/.../application/assembler/EodAssembler.java` | DTO 转换器 |
| `accounting-core/.../interfaces/EodController.java` | 3 个 REST 接口 |
| `accounting-job/.../job/EodJobHandler.java` | XXL-JOB 日切总调度 |
| `accounting-api/request/EodExecuteRequest.java` | 手动触发日切请求 |
| `accounting-api/response/EodExecuteResponse.java` | 日切执行响应 |
| `accounting-api/response/EodPreCheckResponse.java` | 前置检查响应 |
| `accounting-api/response/TrialBalanceResponse.java` | 试算平衡响应 |

### 修改文件（8 个）

| 文件 | 变更 |
|------|------|
| `AccountBalanceMapper.java` | 新增 `batchUpsertBalance` 方法 |
| `AccountBalanceMapper.xml` (新建) | batchUpsertBalance SQL |
| `AccountBalanceSnapshotMapper.java` | 新增 `batchInsertSnapshot` 方法 |
| `AccountBalanceSnapshotMapper.xml` (新建) | batchInsertSnapshot SQL |
| `PeriodEndTransferRuleMapper.java` | 新增 `selectEnabledRules` 方法 |
| `PeriodEndTransferRecordMapper.java` | 新增 `selectByAccountingDate` 方法 |
| `TransactionMapper.java` | 新增 `countByAccountingDateAndStatus` 方法 |
| `TransactionMapper.xml` | countByAccountingDateAndStatus SQL |
| `AccountingVoucherEntryMapper.java` | 新增 `sumEntriesBySubject` 方法 |
| `AccountingVoucherEntryMapper.xml` | sumEntriesBySubject SQL |
| `AccountingVoucherMapper.java` | 新增 `countByAccountingDateAndStatus` 方法 |
| `AccountingVoucherMapper.xml` | countByAccountingDateAndStatus SQL |
| `ResultCode.java` | 新增 2026~2029 错误码 |

---

### Task 1: P0-1 ~ P0-4 Mapper 方法补充 + XML

**Files:**
- Modify: `AccountBalanceMapper.java`
- Create: `accounting-core/src/main/resources/mapper/AccountBalanceMapper.xml`
- Modify: `AccountBalanceSnapshotMapper.java`
- Create: `accounting-core/src/main/resources/mapper/AccountBalanceSnapshotMapper.xml`
- Modify: `PeriodEndTransferRuleMapper.java`
- Modify: `PeriodEndTransferRecordMapper.java`

- [ ] **Step 1: AccountBalanceMapper 新增 batchUpsertBalance 方法**

在 `AccountBalanceMapper.java` 中添加：

```java
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountBalancePO;
import org.apache.ibatis.annotations.Param;
import java.util.List;

int batchUpsertBalance(@Param("list") List<AccountBalancePO> list);
```

- [ ] **Step 2: AccountBalanceMapper.xml 编写**

创建 `accounting-core/src/main/resources/mapper/AccountBalanceMapper.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.kltb.accounting.core.infrastructure.persistence.mapper.AccountBalanceMapper">

    <insert id="batchUpsertBalance">
        INSERT INTO t_account_balance
            (accounting_date, subject_code, account_no, currency,
             balance_direction, begin_balance, debit_amount, credit_amount, end_balance,
             create_time, update_time, is_delete, tenant_id)
        VALUES
        <foreach collection="list" item="item" separator=",">
            (#{item.accountingDate}, #{item.subjectCode}, #{item.accountNo}, #{item.currency},
             #{item.balanceDirection.code}, #{item.beginBalance}, #{item.debitAmount}, #{item.creditAmount}, #{item.endBalance},
             NOW(), NOW(), 0, #{item.tenantId})
        </foreach>
        ON DUPLICATE KEY UPDATE
            debit_amount = VALUES(debit_amount),
            credit_amount = VALUES(credit_amount),
            end_balance = VALUES(end_balance),
            balance_direction = VALUES(balance_direction),
            update_time = NOW()
    </insert>

</mapper>
```

- [ ] **Step 3: AccountBalanceSnapshotMapper 新增 batchInsertSnapshot 方法**

在 `AccountBalanceSnapshotMapper.java` 中添加：

```java
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountBalanceSnapshotPO;
import org.apache.ibatis.annotations.Param;
import java.util.List;

int batchInsertSnapshot(@Param("list") List<AccountBalanceSnapshotPO> list);
```

- [ ] **Step 4: AccountBalanceSnapshotMapper.xml 编写**

创建 `accounting-core/src/main/resources/mapper/AccountBalanceSnapshotMapper.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.kltb.accounting.core.infrastructure.persistence.mapper.AccountBalanceSnapshotMapper">

    <insert id="batchInsertSnapshot">
        INSERT INTO t_account_balance_snapshot
            (snapshot_date, snapshot_type, snapshot_time, subject_code, account_no,
             currency, balance_direction, balance, ext_json,
             create_time, update_time, is_delete, tenant_id)
        VALUES
        <foreach collection="list" item="item" separator=",">
            (#{item.snapshotDate}, #{item.snapshotType.code}, #{item.snapshotTime},
             #{item.subjectCode}, #{item.accountNo}, #{item.currency},
             #{item.balanceDirection.code}, #{item.balance}, #{item.extJson},
             NOW(), NOW(), 0, #{item.tenantId})
        </foreach>
    </insert>

</mapper>
```

- [ ] **Step 5: PeriodEndTransferRuleMapper 新增 selectEnabledRules**

在 `PeriodEndTransferRuleMapper.java` 中添加：

```java
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.domain.enums.AvailableStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.PeriodEndTransferRulePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

default List<PeriodEndTransferRulePO> selectEnabledRules(@Param("transferType") Integer transferType) {
    LambdaQueryWrapper<PeriodEndTransferRulePO> wrapper = new LambdaQueryWrapper<PeriodEndTransferRulePO>()
            .eq(PeriodEndTransferRulePO::getStatus, AvailableStatusEnum.ENABLED)
            .eq(PeriodEndTransferRulePO::getIsDelete, 0)
            .orderByAsc(PeriodEndTransferRulePO::getExecuteOrder);
    if (transferType != null) {
        wrapper.eq(PeriodEndTransferRulePO::getTransferType, transferType);
    }
    return this.selectList(wrapper);
}
```

- [ ] **Step 6: PeriodEndTransferRecordMapper 新增 selectByAccountingDate**

在 `PeriodEndTransferRecordMapper.java` 中添加：

```java
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.PeriodEndTransferRecordPO;
import org.apache.ibatis.annotations.Mapper;
import java.time.LocalDate;
import java.util.List;

default List<PeriodEndTransferRecordPO> selectByAccountingDate(LocalDate accountingDate) {
    return this.selectList(new LambdaQueryWrapper<PeriodEndTransferRecordPO>()
            .eq(PeriodEndTransferRecordPO::getAccountingDate, accountingDate)
            .eq(PeriodEndTransferRecordPO::getIsDelete, 0));
}
```

- [ ] **Step 7: Commit**

```bash
git add accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/AccountBalanceMapper.java
git add accounting-core/src/main/resources/mapper/AccountBalanceMapper.xml
git add accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/AccountBalanceSnapshotMapper.java
git add accounting-core/src/main/resources/mapper/AccountBalanceSnapshotMapper.xml
git add accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/PeriodEndTransferRuleMapper.java
git add accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/PeriodEndTransferRecordMapper.java
git commit -m "feat(step-17): P0-1~P0-4 Mapper 方法补充 + XML"
```

---

### Task 2: P0-5 ~ P0-7 Mapper 方法补充 + XML

**Files:**
- Modify: `TransactionMapper.java`
- Modify: `accounting-core/src/main/resources/mapper/TransactionMapper.xml`
- Modify: `AccountingVoucherEntryMapper.java`
- Modify: `accounting-core/src/main/resources/mapper/AccountingVoucherEntryMapper.xml`
- Modify: `AccountingVoucherMapper.java`
- Modify: `accounting-core/src/main/resources/mapper/AccountingVoucherMapper.xml`

- [ ] **Step 1: TransactionMapper 新增 countByAccountingDateAndStatus**

在 `TransactionMapper.java` 中添加：

```java
default int countByAccountingDateAndStatus(LocalDate accountingDate, Integer status) {
    LambdaQueryWrapper<TransactionPO> wrapper = new LambdaQueryWrapper<TransactionPO>()
            .eq(TransactionPO::getAccountingDate, accountingDate)
            .eq(TransactionPO::getIsDelete, 0);
    if (status != null) {
        wrapper.eq(TransactionPO::getStatus, status);
    }
    return this.selectCount(wrapper).intValue();
}
```

- [ ] **Step 2: AccountingVoucherEntryMapper 新增 sumEntriesBySubject**

在 `AccountingVoucherEntryMapper.java` 中添加：

```java
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

List<Map<String, Object>> sumEntriesBySubject(@Param("accountingDate") LocalDate accountingDate);
```

- [ ] **Step 3: AccountingVoucherEntryMapper.xml 新增 sumEntriesBySubject SQL**

在 `accounting-core/src/main/resources/mapper/AccountingVoucherEntryMapper.xml` 的 `</mapper>` 前添加：

```xml
<select id="sumEntriesBySubject" resultType="java.util.HashMap">
    SELECT subject_code,
           SUM(CASE WHEN debit_credit = 1 THEN amount ELSE 0 END) AS total_debit,
           SUM(CASE WHEN debit_credit = 2 THEN amount ELSE 0 END) AS total_credit,
           COUNT(*) AS entry_count
    FROM t_accounting_voucher_entry
    WHERE accounting_date = #{accountingDate}
      AND status = 2
      AND is_delete = 0
    GROUP BY subject_code
</select>
```

- [ ] **Step 4: AccountingVoucherMapper 新增 countByAccountingDateAndStatus**

在 `AccountingVoucherMapper.java` 中添加：

```java
default int countByAccountingDateAndStatus(LocalDate accountingDate, Integer status) {
    LambdaQueryWrapper<AccountingVoucherPO> wrapper = new LambdaQueryWrapper<AccountingVoucherPO>()
            .eq(AccountingVoucherPO::getAccountingDate, accountingDate)
            .eq(AccountingVoucherPO::getIsDelete, 0);
    if (status != null) {
        wrapper.eq(AccountingVoucherPO::getStatus, status);
    }
    return this.selectCount(wrapper).intValue();
}
```

- [ ] **Step 5: TransactionMapper.xml 新增 countByAccountingDateAndStatus SQL**

在 `accounting-core/src/main/resources/mapper/TransactionMapper.xml` 的 `</mapper>` 前添加：

```xml
<select id="countByAccountingDateAndStatus" resultType="java.lang.Integer">
    SELECT COUNT(*) FROM t_transaction
    WHERE accounting_date = #{accountingDate}
      AND is_delete = 0
      <if test="status != null">
      AND status = #{status}
      </if>
</select>
```

- [ ] **Step 6: AccountingVoucherMapper.xml 新增 countByAccountingDateAndStatus SQL**

在 `accounting-core/src/main/resources/mapper/AccountingVoucherMapper.xml` 的 `</mapper>` 前添加：

```xml
<select id="countByAccountingDateAndStatus" resultType="java.lang.Integer">
    SELECT COUNT(*) FROM t_accounting_voucher
    WHERE accounting_date = #{accountingDate}
      AND is_delete = 0
      <if test="status != null">
      AND status = #{status}
      </if>
</select>
```

- [ ] **Step 7: Commit**

```bash
git add accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/TransactionMapper.java
git add accounting-core/src/main/resources/mapper/TransactionMapper.xml
git add accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/AccountingVoucherEntryMapper.java
git add accounting-core/src/main/resources/mapper/AccountingVoucherEntryMapper.xml
git add accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/AccountingVoucherMapper.java
git add accounting-core/src/main/resources/mapper/AccountingVoucherMapper.xml
git commit -m "feat(step-17): P0-5~P0-7 Mapper 方法补充 + XML"
```

---

### Task 3: P0-8 DDL 确认 + ResultCode 错误码新增

**Files:**
- Read: `docs/sql/all-tables.sql` (确认表结构)
- Modify: `accounting-api/src/main/java/com/kltb/accounting/api/constant/ResultCode.java`

- [ ] **Step 1: DDL 确认**

读取 `docs/sql/all-tables.sql`，确认以下表结构完整：
- `t_account_balance` — 已有，确认字段匹配 AccountBalancePO
- `t_account_balance_snapshot` — 已有，确认字段匹配 AccountBalanceSnapshotPO
- `t_period_end_transfer_rule` — 已有，确认字段匹配 PeriodEndTransferRulePO
- `t_period_end_transfer_record` — 已有，确认字段匹配 PeriodEndTransferRecordPO

如果缺少字段，需要补充 ALTER TABLE 语句到对应的 SQL 文件中。

- [ ] **Step 2: ResultCode 新增错误码**

在 `ResultCode.java` 的现有错误码之后（找到 2025 附近的位置）添加：

```java
EOD_PRECHECK_FAILED("2026", "日切前置检查失败（存在未处理项）"),
EOD_TRANSFER_FAILED("2027", "期末结转执行失败"),
DAILY_BALANCE_NEGATIVE("2028", "日余额计算出现负值"),
EOD_ALREADY_EXECUTED("2029", "日切已执行，不可重复执行"),
```

- [ ] **Step 3: Commit**

```bash
git add accounting-api/src/main/java/com/kltb/accounting/api/constant/ResultCode.java
git commit -m "feat(step-17): P0-8 ResultCode 新增 2026~2029 错误码"
```

---

### Task 4: Repository 仓储层（4 个文件）

**Files:**
- Create: `accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/repository/AccountBalanceRepository.java`
- Create: `accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/repository/AccountBalanceSnapshotRepository.java`
- Create: `accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/repository/PeriodEndTransferRuleRepository.java`
- Create: `accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/repository/PeriodEndTransferRecordRepository.java`

- [ ] **Step 1: AccountBalanceRepository**

```java
package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountBalancePO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountBalanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 账户日余额仓储
 */
@Repository
@RequiredArgsConstructor
public class AccountBalanceRepository {

    private final AccountBalanceMapper accountBalanceMapper;

    /**
     * 批量 Upsert 日余额
     */
    public void batchUpsert(List<AccountBalancePO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        accountBalanceMapper.batchUpsertBalance(list);
    }

    /**
     * 查询指定会计日期的日余额
     */
    public List<AccountBalancePO> selectByDate(LocalDate accountingDate) {
        return accountBalanceMapper.selectList(new LambdaQueryWrapper<AccountBalancePO>()
                .eq(AccountBalancePO::getAccountingDate, accountingDate)
                .eq(AccountBalancePO::getIsDelete, 0));
    }

    /**
     * 查询指定账户前一日末余额
     */
    public AccountBalancePO selectPreviousDayBalance(String accountNo, LocalDate previousDate) {
        return accountBalanceMapper.selectOne(new LambdaQueryWrapper<AccountBalancePO>()
                .eq(AccountBalancePO::getAccountNo, accountNo)
                .eq(AccountBalancePO::getAccountingDate, previousDate)
                .eq(AccountBalancePO::getIsDelete, 0)
                .last("LIMIT 1"));
    }
}
```

- [ ] **Step 2: AccountBalanceSnapshotRepository**

```java
package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.kltb.accounting.core.infrastructure.persistence.entity.AccountBalanceSnapshotPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountBalanceSnapshotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 账户余额快照仓储
 */
@Repository
@RequiredArgsConstructor
public class AccountBalanceSnapshotRepository {

    private final AccountBalanceSnapshotMapper snapshotMapper;

    /**
     * 批量插入快照
     */
    public void batchInsert(List<AccountBalanceSnapshotPO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        snapshotMapper.batchInsertSnapshot(list);
    }
}
```

- [ ] **Step 3: PeriodEndTransferRuleRepository**

```java
package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.kltb.accounting.core.domain.enums.TransferTypeEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.PeriodEndTransferRulePO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.PeriodEndTransferRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 期末结转规则仓储
 */
@Repository
@RequiredArgsConstructor
public class PeriodEndTransferRuleRepository {

    private final PeriodEndTransferRuleMapper ruleMapper;

    /**
     * 查询启用中的结转规则（按 execute_order 升序）
     *
     * @param transferType 结转类型，null 表示查询全部
     */
    public List<PeriodEndTransferRulePO> selectEnabledRules(TransferTypeEnum transferType) {
        return ruleMapper.selectEnabledRules(transferType != null ? transferType.getCode() : null);
    }
}
```

- [ ] **Step 4: PeriodEndTransferRecordRepository**

```java
package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.PeriodEndTransferRecordPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.PeriodEndTransferRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 期末结转记录仓储
 */
@Repository
@RequiredArgsConstructor
public class PeriodEndTransferRecordRepository {

    private final PeriodEndTransferRecordMapper recordMapper;

    /**
     * 按会计日期查询结转记录
     */
    public List<PeriodEndTransferRecordPO> selectByAccountingDate(LocalDate accountingDate) {
        return recordMapper.selectByAccountingDate(accountingDate);
    }

    /**
     * 检查指定规则在指定日期是否已有成功结转记录（幂等控制）
     */
    public boolean existsSuccessfulTransfer(LocalDate accountingDate, String ruleCode) {
        return recordMapper.selectList(new LambdaQueryWrapper<PeriodEndTransferRecordPO>()
                .eq(PeriodEndTransferRecordPO::getAccountingDate, accountingDate)
                .eq(PeriodEndTransferRecordPO::getRuleCode, ruleCode)
                .eq(PeriodEndTransferRecordPO::getStatus, com.kltb.accounting.core.domain.enums.TransferRecordStatusEnum.SUCCESS)
                .eq(PeriodEndTransferRecordPO::getIsDelete, 0))
                .stream().findFirst().isPresent();
    }

    /**
     * 插入结转记录
     */
    public void insert(PeriodEndTransferRecordPO record) {
        recordMapper.insert(record);
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/repository/AccountBalanceRepository.java
git add accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/repository/AccountBalanceSnapshotRepository.java
git add accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/repository/PeriodEndTransferRuleRepository.java
git add accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/repository/PeriodEndTransferRecordRepository.java
git commit -m "feat(step-17): Repository 仓储层（4 个）"
```

---

### Task 5: EodCheckDomainService（日切前置检查）

**Files:**
- Create: `accounting-core/src/main/java/com/kltb/accounting/core/domain/service/EodCheckDomainService.java`

- [ ] **Step 1: 编写 EodCheckDomainService**

```java
package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.BufferModeEnum;
import com.kltb.accounting.core.domain.enums.BufferStatusEnum;
import com.kltb.accounting.core.domain.enums.FreezeStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.repository.*;
import com.kltb.accounting.core.shared.exception.AccountException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 日切前置检查领域服务
 * <p>
 * 职责：验证当日所有过账是否完成、无处理中事务、无未过账凭证。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EodCheckDomainService {

    private final BufferPostingDetailRepository bufferPostingDetailRepository;
    private final TransactionRepository transactionRepository;
    private final AccountingVoucherRepository voucherRepository;
    private final BusinessRecordRepository businessRecordRepository;
    private final FreezeDetailRepository freezeDetailRepository;

    /**
     * 日切前置检查
     */
    public EodPreCheckResult checkEodPreconditions(LocalDate accountingDate) {
        // 1. 缓冲明细检查：当日无 status=待入账/处理中 的缓冲明细
        int bufferPending = bufferPostingDetailRepository.countByAccountingDateAndStatus(
                accountingDate, BufferStatusEnum.PENDING.getCode());

        // 2. 事务状态检查：当日无 status=处理中 的事务
        int processingTxn = transactionRepository.countByAccountingDateAndStatus(
                accountingDate, com.kltb.accounting.core.domain.enums.TransactionStatusEnum.PROCESSING.getCode());

        // 3. 凭证状态检查：当日无 status=未过账/过账中 的凭证
        int unpostedVoucher = voucherRepository.countByAccountingDateAndStatus(
                accountingDate, com.kltb.accounting.core.domain.enums.VoucherStatusEnum.PENDING.getCode());
        int postingVoucher = voucherRepository.countByAccountingDateAndStatus(
                accountingDate, com.kltb.accounting.core.domain.enums.VoucherStatusEnum.POSTING.getCode());
        int totalUnpostedVoucher = unpostedVoucher + postingVoucher;

        // 4. 业务流水检查：当日无 status=处理中 的业务流水
        int processingJournal = businessRecordRepository.countByAccountingDateAndStatus(
                accountingDate, com.kltb.accounting.core.domain.enums.BusinessRecordStatusEnum.PROCESSING.getCode());

        // 5. 冻结记录检查：无过期未解冻记录
        int expiredFreeze = freezeDetailRepository.countExpiredUnfrozen();

        boolean allPassed = bufferPending == 0 && processingTxn == 0
                && totalUnpostedVoucher == 0 && processingJournal == 0 && expiredFreeze == 0;

        if (!allPassed) {
            log.error("[EOD-PRECHECK-FAILED] date={}, bufferPending={}, processingTxn={}, "
                            + "unpostedVoucher={}, processingJournal={}, expiredFreeze={}",
                    accountingDate, bufferPending, processingTxn, totalUnpostedVoucher,
                    processingJournal, expiredFreeze);
        }

        return new EodPreCheckResult(
                accountingDate, allPassed, bufferPending, processingTxn,
                totalUnpostedVoucher, processingJournal, expiredFreeze);
    }

    /**
     * 检查日切前置条件，不通过时抛出 AccountException
     */
    public void checkAndThrow(LocalDate accountingDate) {
        EodPreCheckResult result = checkEodPreconditions(accountingDate);
        if (!result.isAllPassed()) {
            throw new AccountException(ResultCode.EOD_PRECHECK_FAILED,
                    "日切前置检查失败: date=" + accountingDate
                            + ", bufferPending=" + result.getBufferPending()
                            + ", processingTxn=" + result.getProcessingTxn()
                            + ", unpostedVoucher=" + result.getUnpostedVoucher()
                            + ", processingJournal=" + result.getProcessingJournal()
                            + ", expiredFreeze=" + result.getExpiredFreeze());
        }
    }

    @Data
    public static class EodPreCheckResult {
        private final LocalDate accountingDate;
        private final boolean allPassed;
        private final int bufferPending;
        private final int processingTxn;
        private final int unpostedVoucher;
        private final int processingJournal;
        private final int expiredFreeze;
    }
}
```

- [ ] **Step 2: 检查 BufferPostingDetailRepository 是否有 countByAccountingDateAndStatus 方法**

如果没有，需要补充：

在 `BufferPostingDetailMapper.java` 中添加：

```java
default int countByAccountingDateAndStatus(LocalDate accountingDate, Integer status) {
    return this.selectCount(new LambdaQueryWrapper<BufferPostingDetailPO>()
            .eq(BufferPostingDetailPO::getAccountingDate, accountingDate)
            .eq(BufferPostingDetailPO::getStatus, status)
            .eq(BufferPostingDetailPO::getIsDelete, 0)).intValue();
}
```

在 `BufferPostingDetailRepository.java` 中添加：

```java
public int countByAccountingDateAndStatus(LocalDate accountingDate, Integer status) {
    return bufferPostingDetailMapper.countByAccountingDateAndStatus(accountingDate, status);
}
```

- [ ] **Step 3: 检查 FreezeDetailRepository 是否有 countExpiredUnfrozen 方法**

如果没有，需要补充：

在 `AccountFreezeDetailMapper.java` 中添加：

```java
default int countExpiredUnfrozen() {
    return this.selectCount(new LambdaQueryWrapper<AccountFreezeDetailPO>()
            .le(AccountFreezeDetailPO::getExpireTime, java.time.LocalDateTime.now())
            .eq(AccountFreezeDetailPO::getStatus, com.kltb.accounting.core.domain.enums.FreezeStatusEnum.FROZEN.getCode())
            .eq(AccountFreezeDetailPO::getIsDelete, 0)).intValue();
}
```

在 `FreezeDetailRepository.java` 中添加：

```java
public int countExpiredUnfrozen() {
    return accountFreezeDetailMapper.countExpiredUnfrozen();
}
```

- [ ] **Step 4: Commit**

```bash
git add accounting-core/src/main/java/com/kltb/accounting/core/domain/service/EodCheckDomainService.java
git commit -m "feat(step-17): EodCheckDomainService 日切前置检查"
```

---

### Task 6: TrialBalanceDomainService（试算平衡）

**Files:**
- Create: `accounting-core/src/main/java/com/kltb/accounting/core/domain/service/TrialBalanceDomainService.java`

- [ ] **Step 1: 编写 TrialBalanceDomainService**

```java
package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountingVoucherEntryMapper;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubjectRepository;
import com.kltb.accounting.core.shared.exception.AccountException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 试算平衡领域服务
 * <p>
 * 职责：按科目汇总当日已过账分录的借贷方发生额，验证全局借贷平衡。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrialBalanceDomainService {

    private static final BigDecimal BALANCE_THRESHOLD = new BigDecimal("0.000001");

    private final AccountingVoucherEntryMapper voucherEntryMapper;
    private final SubjectRepository subjectRepository;

    /**
     * 执行试算平衡
     */
    public TrialBalanceResult executeTrialBalance(LocalDate accountingDate) {
        List<Map<String, Object>> subjectSummaries = voucherEntryMapper.sumEntriesBySubject(accountingDate);

        BigDecimal totalDebitAll = BigDecimal.ZERO;
        BigDecimal totalCreditAll = BigDecimal.ZERO;
        List<SubjectDetail> subjectDetails = new ArrayList<>();

        for (Map<String, Object> row : subjectSummaries) {
            String subjectCode = (String) row.get("subject_code");
            BigDecimal totalDebit = toBigDecimal(row.get("total_debit"));
            BigDecimal totalCredit = toBigDecimal(row.get("total_credit"));

            totalDebitAll = totalDebitAll.add(totalDebit);
            totalCreditAll = totalCreditAll.add(totalCredit);

            BigDecimal netDiff = totalDebit.subtract(totalCredit);
            subjectDetails.add(new SubjectDetail(
                    subjectCode,
                    getSubjectName(subjectCode),
                    totalDebit,
                    totalCredit,
                    netDiff));
        }

        BigDecimal diff = totalDebitAll.subtract(totalCreditAll).abs();
        boolean passed = diff.compareTo(BALANCE_THRESHOLD) <= 0;

        List<SubjectDetail> imbalancedSubjects = new ArrayList<>();
        if (!passed) {
            for (SubjectDetail detail : subjectDetails) {
                if (detail.getNetDiff().abs().compareTo(BALANCE_THRESHOLD) > 0) {
                    imbalancedSubjects.add(detail);
                    log.error("[TRIAL-BALANCE-IMBALANCED] subject={}, debit={}, credit={}, diff={}",
                            detail.getSubjectCode(), detail.getTotalDebit(),
                            detail.getTotalCredit(), detail.getNetDiff().abs());
                }
            }

            log.error("[TRIAL-BALANCE-FAILED] date={}, totalDebit={}, totalCredit={}, diff={}",
                    accountingDate, totalDebitAll, totalCreditAll, diff);
        }

        return new TrialBalanceResult(
                accountingDate, passed, totalDebitAll, totalCreditAll,
                diff, subjectDetails, imbalancedSubjects);
    }

    /**
     * 执行试算平衡，不平衡时抛出 AccountException
     */
    public TrialBalanceResult executeAndThrow(LocalDate accountingDate) {
        TrialBalanceResult result = executeTrialBalance(accountingDate);
        if (!result.isPassed()) {
            throw new AccountException(ResultCode.TRIAL_BALANCE_FAILED,
                    "试算平衡失败: date=" + accountingDate
                            + ", totalDebit=" + result.getTotalDebit()
                            + ", totalCredit=" + result.getTotalCredit()
                            + ", diff=" + result.getDiff());
        }
        return result;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        return BigDecimal.ZERO;
    }

    private String getSubjectName(String subjectCode) {
        AccountSubjectPO subject = subjectRepository.selectByCode(subjectCode);
        return subject != null ? subject.getSubjectName() : subjectCode;
    }

    @Data
    public static class TrialBalanceResult {
        private final LocalDate accountingDate;
        private final boolean passed;
        private final BigDecimal totalDebit;
        private final BigDecimal totalCredit;
        private final BigDecimal diff;
        private final List<SubjectDetail> subjectDetails;
        private final List<SubjectDetail> imbalancedSubjects;
    }

    @Data
    public static class SubjectDetail {
        private final String subjectCode;
        private final String subjectName;
        private final BigDecimal totalDebit;
        private final BigDecimal totalCredit;
        private final BigDecimal netDiff;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add accounting-core/src/main/java/com/kltb/accounting/core/domain/service/TrialBalanceDomainService.java
git commit -m "feat(step-17): TrialBalanceDomainService 试算平衡"
```

---

### Task 7: EodDomainService（日余额计算 + 快照生成）

**Files:**
- Create: `accounting-core/src/main/java/com/kltb/accounting/core/domain/service/EodDomainService.java`

- [ ] **Step 1: 编写 EodDomainService**

```java
package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.BalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.SnapshotTypeEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.*;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountingVoucherEntryMapper;
import com.kltb.accounting.core.infrastructure.persistence.repository.*;
import com.kltb.accounting.core.shared.exception.AccountException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 日切领域服务 —— 日余额计算 + 快照生成
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EodDomainService {

    private final AccountBalanceRepository accountBalanceRepository;
    private final AccountBalanceSnapshotRepository snapshotRepository;
    private final AccountingVoucherRepository voucherRepository;
    private final AccountingVoucherEntryMapper voucherEntryMapper;
    private final AccountRepository accountRepository;
    private final TransactionTemplate transactionTemplate;

    /**
     * 计算日余额
     */
    public List<AccountBalancePO> calculateDailyBalances(LocalDate accountingDate) {
        List<Map<String, Object>> entries = voucherEntryMapper.sumEntriesBySubject(accountingDate);

        // 按 account_no 分组汇总（注意：sumEntriesBySubject 按 subject_code 汇总，
        // 需要再关联 account_no，此处简化为按 subject_code 汇总）
        List<AccountBalancePO> balances = new ArrayList<>();

        for (Map<String, Object> row : entries) {
            String subjectCode = (String) row.get("subject_code");
            BigDecimal debitAmount = toBigDecimal(row.get("total_debit"));
            BigDecimal creditAmount = toBigDecimal(row.get("total_credit"));

            // 查询该科目下有当日过账的账户
            List<AccountBalancePO> accountBalances = calculateAccountBalances(
                    subjectCode, accountingDate, debitAmount, creditAmount);
            balances.addAll(accountBalances);
        }

        return balances;
    }

    /**
     * 按账户计算日余额（核心逻辑）
     */
    private List<AccountBalancePO> calculateAccountBalances(
            String subjectCode, LocalDate accountingDate,
            BigDecimal totalDebit, BigDecimal totalCredit) {

        // 简化：此处按 subject_code 级别写入 t_account_balance
        // 如果需要按 account_no 细分，需要在 sumEntriesBySubject 中补充 account_no 字段
        AccountBalancePO balance = new AccountBalancePO();
        balance.setAccountingDate(accountingDate);
        balance.setSubjectCode(subjectCode);
        balance.setAccountNo(""); // 按科目汇总级别
        balance.setCurrency("CNY");
        balance.setDebitAmount(totalDebit);
        balance.setCreditAmount(totalCredit);

        // 查询前一日末余额
        AccountBalancePO previousDay = accountBalanceRepository
                .selectPreviousDayBalance("", accountingDate.minusDays(1));
        if (previousDay != null) {
            balance.setBeginBalance(previousDay.getEndBalance());
            balance.setBalanceDirection(previousDay.getBalanceDirection());
        } else {
            balance.setBeginBalance(BigDecimal.ZERO);
            balance.setBalanceDirection(BalanceDirectionEnum.DEBIT);
        }

        BigDecimal beginBalance = balance.getBeginBalance();
        BalanceDirectionEnum direction = balance.getBalanceDirection();

        BigDecimal endBalance;
        if (direction == BalanceDirectionEnum.DEBIT) {
            endBalance = beginBalance.add(totalDebit).subtract(totalCredit);
        } else {
            endBalance = beginBalance.add(totalCredit).subtract(totalDebit);
        }

        // 负数余额检测
        if (endBalance.compareTo(BigDecimal.ZERO) < 0) {
            log.error("[EOD-BALANCE-NEGATIVE] subjectCode={}, endBalance={}, "
                            + "beginBalance={}, debitAmount={}, creditAmount={}",
                    subjectCode, endBalance, beginBalance, totalDebit, totalCredit);
            throw new AccountException(ResultCode.DAILY_BALANCE_NEGATIVE,
                    "日余额计算出现负值: subjectCode=" + subjectCode);
        }

        balance.setEndBalance(endBalance);
        return Collections.singletonList(balance);
    }

    /**
     * 批量 Upsert 日余额
     */
    public void upsertDailyBalances(LocalDate accountingDate, List<AccountBalancePO> balances) {
        if (balances == null || balances.isEmpty()) {
            return;
        }

        transactionTemplate.execute(status -> {
            try {
                accountBalanceRepository.batchUpsert(balances);
                return null;
            } catch (Exception e) {
                status.setRollbackOnly();
                throw e;
            }
        });

        log.info("[EOD-BALANCE-UPSERT] date={}, count={}", accountingDate, balances.size());
    }

    /**
     * 生成日快照
     */
    public int generateDailySnapshot(LocalDate accountingDate) {
        List<AccountBalancePO> dayBalances = accountBalanceRepository.selectByDate(accountingDate);
        if (dayBalances == null || dayBalances.isEmpty()) {
            return 0;
        }

        List<AccountBalanceSnapshotPO> snapshots = new ArrayList<>();
        LocalDateTime snapshotTime = LocalDateTime.now();

        for (AccountBalancePO balance : dayBalances) {
            AccountBalanceSnapshotPO snapshot = new AccountBalanceSnapshotPO();
            snapshot.setSnapshotDate(balance.getAccountingDate());
            snapshot.setSnapshotType(SnapshotTypeEnum.DAY);
            snapshot.setSnapshotTime(snapshotTime);
            snapshot.setSubjectCode(balance.getSubjectCode());
            snapshot.setAccountNo(balance.getAccountNo());
            snapshot.setCurrency(balance.getCurrency());
            snapshot.setBalanceDirection(balance.getBalanceDirection());
            snapshot.setBalance(balance.getEndBalance());
            snapshot.setExtJson(String.format("{\"debitAmount\":%s,\"creditAmount\":%s}",
                    balance.getDebitAmount(), balance.getCreditAmount()));
            snapshots.add(snapshot);

            // 月末日额外生成月快照
            if (isMonthEnd(accountingDate)) {
                AccountBalanceSnapshotPO monthSnapshot = new AccountBalanceSnapshotPO();
                monthSnapshot.setSnapshotDate(balance.getAccountingDate());
                monthSnapshot.setSnapshotType(SnapshotTypeEnum.MONTH);
                monthSnapshot.setSnapshotTime(snapshotTime);
                monthSnapshot.setSubjectCode(balance.getSubjectCode());
                monthSnapshot.setAccountNo(balance.getAccountNo());
                monthSnapshot.setCurrency(balance.getCurrency());
                monthSnapshot.setBalanceDirection(balance.getBalanceDirection());
                monthSnapshot.setBalance(balance.getEndBalance());
                monthSnapshot.setExtJson(String.format("{\"debitAmount\":%s,\"creditAmount\":%s}",
                        balance.getDebitAmount(), balance.getCreditAmount()));
                snapshots.add(monthSnapshot);
            }
        }

        if (!snapshots.isEmpty()) {
            transactionTemplate.execute(status -> {
                try {
                    snapshotRepository.batchInsert(snapshots);
                    return null;
                } catch (Exception e) {
                    status.setRollbackOnly();
                    throw e;
                }
            });
        }

        log.info("[EOD-SNAPSHOT] date={}, daySnapshots={}, totalSnapshots={}",
                accountingDate, dayBalances.size(), snapshots.size());
        return snapshots.size();
    }

    private boolean isMonthEnd(LocalDate date) {
        return date.getDayOfMonth() == date.lengthOfMonth();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        return BigDecimal.ZERO;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add accounting-core/src/main/java/com/kltb/accounting/core/domain/service/EodDomainService.java
git commit -m "feat(step-17): EodDomainService 日余额计算 + 快照生成"
```

---

### Task 8: PeriodEndTransferDomainService（期末结转）

**Files:**
- Create: `accounting-core/src/main/java/com/kltb/accounting/core/domain/service/PeriodEndTransferDomainService.java`

- [ ] **Step 1: 编写 PeriodEndTransferDomainService**

```java
package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.*;
import com.kltb.accounting.core.infrastructure.persistence.entity.*;
import com.kltb.accounting.core.infrastructure.persistence.repository.*;
import com.kltb.accounting.core.shared.exception.AccountException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 期末结转领域服务
 * <p>
 * 职责：按 execute_order 升序逐条执行结转规则，生成结转凭证。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PeriodEndTransferDomainService {

    private final PeriodEndTransferRuleRepository ruleRepository;
    private final PeriodEndTransferRecordRepository recordRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final AccountingVoucherRepository voucherRepository;
    private final SubjectRepository subjectRepository;
    private final TransactionTemplate transactionTemplate;

    /**
     * 执行期末结转规则
     */
    public List<TransferRuleResult> executeTransferRules(LocalDate accountingDate) {
        List<PeriodEndTransferRulePO> rules = ruleRepository.selectEnabledRules(null);
        if (rules == null || rules.isEmpty()) {
            log.info("[EOD-TRANSFER] 无启用的结转规则: date={}", accountingDate);
            return List.of();
        }

        List<TransferRuleResult> results = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;

        for (PeriodEndTransferRulePO rule : rules) {
            try {
                // 幂等控制：检查当日是否已有成功结转记录
                if (recordRepository.existsSuccessfulTransfer(accountingDate, rule.getRuleCode())) {
                    log.info("[EOD-TRANSFER] 规则已执行，跳过: ruleCode={}, date={}",
                            rule.getRuleCode(), accountingDate);
                    continue;
                }

                TransferRuleResult result = executeSingleRule(rule, accountingDate);
                results.add(result);
                if (result.getStatus() == TransferRecordStatusEnum.SUCCESS) {
                    successCount++;
                } else {
                    failedCount++;
                }
            } catch (Exception e) {
                log.error("[EOD-TRANSFER-FAILED] rule={}, reason={}",
                        rule.getRuleCode(), e.getMessage(), e);
                TransferRuleResult failResult = new TransferRuleResult(
                        rule.getRuleCode(), rule.getRuleName(), null, null,
                        BigDecimal.ZERO, TransferRecordStatusEnum.FAILURE,
                        e.getMessage());
                results.add(failResult);
                failedCount++;
            }
        }

        log.info("[EOD-TRANSFER-SUMMARY] date={}, rules={}, success={}, failed={}",
                accountingDate, rules.size(), successCount, failedCount);
        return results;
    }

    /**
     * 执行单条结转规则
     */
    private TransferRuleResult executeSingleRule(PeriodEndTransferRulePO rule, LocalDate accountingDate) {
        String transferNo = generateTransferNo(accountingDate, rule.getRuleCode());

        return transactionTemplate.execute(status -> {
            try {
                // 1. 解析通配符
                String pattern = wildcardToLikePattern(rule.getSourceSubjectCode());

                // 2. 查询符合条件的账户日余额
                List<AccountBalancePO> balances = accountBalanceRepository.selectByDate(accountingDate);
                List<AccountBalancePO> matchedBalances = balances.stream()
                        .filter(b -> b.getSubjectCode() != null && b.getSubjectCode().startsWith(pattern.replace("%", "")))
                        .filter(b -> b.getEndBalance() != null && b.getEndBalance().compareTo(BigDecimal.ZERO) != 0)
                        .toList();

                if (matchedBalances.isEmpty()) {
                    log.info("[EOD-TRANSFER] 无符合条件的余额账户: ruleCode={}, pattern={}",
                            rule.getRuleCode(), pattern);
                    return new TransferRuleResult(
                            rule.getRuleCode(), rule.getRuleName(), transferNo, null,
                            BigDecimal.ZERO, TransferRecordStatusEnum.SUCCESS, null);
                }

                // 3. 计算结转总金额
                BigDecimal totalAmount = BigDecimal.ZERO;
                for (AccountBalancePO balance : matchedBalances) {
                    totalAmount = totalAmount.add(balance.getEndBalance().abs());
                }

                // 4. 生成结转凭证
                String voucherNo = generateTransferVoucher(
                        rule, accountingDate, transferNo, matchedBalances);

                // 5. 更新结转后余额为零
                for (AccountBalancePO balance : matchedBalances) {
                    balance.setEndBalance(BigDecimal.ZERO);
                }
                accountBalanceRepository.batchUpsert(matchedBalances);

                // 6. 记录结转结果
                PeriodEndTransferRecordPO record = new PeriodEndTransferRecordPO();
                record.setTransferNo(transferNo);
                record.setAccountingDate(accountingDate);
                record.setTransferType(rule.getTransferType());
                record.setRuleCode(rule.getRuleCode());
                record.setVoucherNo(voucherNo);
                record.setTotalAmount(totalAmount);
                record.setStatus(TransferRecordStatusEnum.SUCCESS);
                record.setExecuteTime(LocalDateTime.now());
                record.setFinishTime(LocalDateTime.now());
                recordRepository.insert(record);

                log.info("[EOD-TRANSFER] 结转成功: ruleCode={}, transferNo={}, voucherNo={}, amount={}",
                        rule.getRuleCode(), transferNo, voucherNo, totalAmount);

                return new TransferRuleResult(
                        rule.getRuleCode(), rule.getRuleName(), transferNo, voucherNo,
                        totalAmount, TransferRecordStatusEnum.SUCCESS, null);
            } catch (Exception e) {
                status.setRollbackOnly();
                throw e;
            }
        });
    }

    /**
     * 生成结转凭证
     */
    private String generateTransferVoucher(
            PeriodEndTransferRulePO rule, LocalDate accountingDate,
            String transferNo, List<AccountBalancePO> balances) {

        String voucherNo = "V" + accountingDate.format(DateTimeFormatter.BASIC_ISO_DATE)
                + String.format("%06d", System.currentTimeMillis() % 1000000);

        // 凭证头
        AccountingVoucherPO voucher = new AccountingVoucherPO();
        voucher.setVoucherNo(voucherNo);
        voucher.setTxnNo("");
        voucher.setTraceNo(transferNo);
        voucher.setTraceSeq(1);
        voucher.setVoucherType("结账凭证");
        voucher.setPostingType(PostingTypeEnum.AUTOMATIC);
        voucher.setBusinessCode("EOD");
        voucher.setTradingCode("");
        voucher.setPayChannel("");
        voucher.setTradeType(TradeTypeEnum.BLUE);
        voucher.setTradeTime(LocalDateTime.now());
        voucher.setAmount(balances.stream()
                .map(AccountBalancePO::getEndBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        voucher.setStatus(VoucherStatusEnum.PENDING);
        voucher.setAccountingDate(accountingDate);
        voucher.setSummary(renderSummary(rule.getSummaryTemplate(), accountingDate));
        voucher.setBookkeeperName("SYSTEM");
        voucherRepository.insert(voucher);

        // 分录
        int rowNum = 0;
        for (AccountBalancePO balance : balances) {
            rowNum++;
            AccountingVoucherEntryPO entry = new AccountingVoucherEntryPO();
            entry.setVoucherNo(voucherNo);
            entry.setEntryId(String.valueOf(System.nanoTime()));
            entry.setRowNum(rowNum);
            entry.setSubjectCode(balance.getSubjectCode());
            entry.setAccountNo(balance.getAccountNo());
            entry.setAmount(balance.getEndBalance().abs());
            entry.setCurrency(balance.getCurrency());
            entry.setSummary(rule.getRuleName());
            entry.setStatus(VoucherEntryStatusEnum.PENDING);
            entry.setAccountingDate(accountingDate);

            // 根据结转方向确定借贷方向
            if (rule.getTransferDirection() == TransferDirectionEnum.DEBIT_TO_CREDIT) {
                entry.setDebitCredit(DebitCreditEnum.DEBIT);
            } else {
                entry.setDebitCredit(DebitCreditEnum.CREDIT);
            }
            voucherRepository.insertEntry(entry);

            // 目标科目分录
            rowNum++;
            AccountingVoucherEntryPO targetEntry = new AccountingVoucherEntryPO();
            targetEntry.setVoucherNo(voucherNo);
            targetEntry.setEntryId(String.valueOf(System.nanoTime() + 1));
            targetEntry.setRowNum(rowNum);
            targetEntry.setSubjectCode(rule.getTargetSubjectCode());
            targetEntry.setAccountNo("");
            targetEntry.setAmount(balance.getEndBalance().abs());
            targetEntry.setCurrency(balance.getCurrency());
            targetEntry.setSummary(rule.getRuleName());
            targetEntry.setStatus(VoucherEntryStatusEnum.PENDING);
            targetEntry.setAccountingDate(accountingDate);

            if (rule.getTransferDirection() == TransferDirectionEnum.DEBIT_TO_CREDIT) {
                targetEntry.setDebitCredit(DebitCreditEnum.CREDIT);
            } else {
                targetEntry.setDebitCredit(DebitCreditEnum.DEBIT);
            }
            voucherRepository.insertEntry(targetEntry);
        }

        return voucherNo;
    }

    private String wildcardToLikePattern(String wildcard) {
        return wildcard.replace("*", "%");
    }

    private String generateTransferNo(LocalDate date, String ruleCode) {
        long seq = System.currentTimeMillis() % 10000;
        return String.format("EODTR%s%04d",
                date.format(DateTimeFormatter.BASIC_ISO_DATE), seq);
    }

    private String renderSummary(String template, LocalDate date) {
        if (template == null || template.isEmpty()) {
            return date.format(DateTimeFormatter.ofPattern("yyyy年MM月期末结转"));
        }
        return template.replace("{year}", String.valueOf(date.getYear()))
                .replace("{month}", String.valueOf(date.getMonthValue()));
    }

    @Data
    public static class TransferRuleResult {
        private final String ruleCode;
        private final String ruleName;
        private final String transferNo;
        private final String voucherNo;
        private final BigDecimal totalAmount;
        private final TransferRecordStatusEnum status;
        private final String failReason;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add accounting-core/src/main/java/com/kltb/accounting/core/domain/service/PeriodEndTransferDomainService.java
git commit -m "feat(step-17): PeriodEndTransferDomainService 期末结转"
```

---

### Task 9: DTO + Assembler

**Files:**
- Create: `accounting-api/src/main/java/com/kltb/accounting/api/request/EodExecuteRequest.java`
- Create: `accounting-api/src/main/java/com/kltb/accounting/api/response/EodExecuteResponse.java`
- Create: `accounting-api/src/main/java/com/kltb/accounting/api/response/EodPreCheckResponse.java`
- Create: `accounting-api/src/main/java/com/kltb/accounting/api/response/TrialBalanceResponse.java`
- Create: `accounting-core/src/main/java/com/kltb/accounting/core/application/assembler/EodAssembler.java`

- [ ] **Step 1: EodExecuteRequest**

```java
package com.kltb.accounting.api.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 手动触发日切请求
 */
@Data
public class EodExecuteRequest {

    /**
     * 会计日期（必填）
     */
    @NotNull(message = "会计日期不能为空")
    private LocalDate accountingDate;

    /**
     * 是否跳过前置检查（默认 false）
     */
    private boolean skipPreCheck = false;

    /**
     * 是否执行期末结转（默认 true）
     */
    private boolean executeTransfer = true;
}
```

- [ ] **Step 2: EodPreCheckResponse**

```java
package com.kltb.accounting.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 日切前置检查响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EodPreCheckResponse {

    private LocalDate accountingDate;
    private boolean allPassed;
    private List<CheckItem> checks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckItem {
        private String name;
        private int count;
        private boolean passed;
    }
}
```

- [ ] **Step 3: TrialBalanceResponse**

```java
package com.kltb.accounting.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 试算平衡响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrialBalanceResponse {

    private LocalDate accountingDate;
    private boolean passed;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private BigDecimal diff;
    private List<SubjectDetail> subjectDetails;
    private List<SubjectDetail> imbalancedSubjects;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubjectDetail {
        private String subjectCode;
        private String subjectName;
        private BigDecimal totalDebit;
        private BigDecimal totalCredit;
        private BigDecimal netDiff;
    }
}
```

- [ ] **Step 4: EodExecuteResponse**

```java
package com.kltb.accounting.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 日切执行响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EodExecuteResponse {

    private LocalDate accountingDate;
    private boolean preCheckPassed;
    private EodPreCheckResponse preCheckDetails;
    private boolean balanceCalculated;
    private int balanceCount;
    private boolean trialBalancePassed;
    private TrialBalanceResponse trialBalanceDetails;
    private List<TransferRecord> transferResults;
    private boolean snapshotGenerated;
    private int snapshotCount;
    private long totalDurationMs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransferRecord {
        private String ruleCode;
        private String ruleName;
        private String transferNo;
        private String voucherNo;
        private BigDecimal totalAmount;
        private int status;
    }
}
```

- [ ] **Step 5: EodAssembler**

```java
package com.kltb.accounting.core.application.assembler;

import com.kltb.accounting.api.response.EodExecuteResponse;
import com.kltb.accounting.api.response.EodPreCheckResponse;
import com.kltb.accounting.api.response.TrialBalanceResponse;
import com.kltb.accounting.core.domain.service.EodCheckDomainService;
import com.kltb.accounting.core.domain.service.PeriodEndTransferDomainService;
import com.kltb.accounting.core.domain.service.TrialBalanceDomainService;
import com.kltb.accounting.core.domain.enums.TransferRecordStatusEnum;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 日切 DTO 转换器
 */
@Component
public class EodAssembler {

    public EodPreCheckResponse toPreCheckResponse(EodCheckDomainService.EodPreCheckResult result) {
        return EodPreCheckResponse.builder()
                .accountingDate(result.getAccountingDate())
                .allPassed(result.isAllPassed())
                .checks(List.of(
                        EodPreCheckResponse.CheckItem.builder()
                                .name("bufferPending").count(result.getBufferPending())
                                .passed(result.getBufferPending() == 0).build(),
                        EodPreCheckResponse.CheckItem.builder()
                                .name("processingTxn").count(result.getProcessingTxn())
                                .passed(result.getProcessingTxn() == 0).build(),
                        EodPreCheckResponse.CheckItem.builder()
                                .name("unpostedVoucher").count(result.getUnpostedVoucher())
                                .passed(result.getUnpostedVoucher() == 0).build(),
                        EodPreCheckResponse.CheckItem.builder()
                                .name("processingJournal").count(result.getProcessingJournal())
                                .passed(result.getProcessingJournal() == 0).build(),
                        EodPreCheckResponse.CheckItem.builder()
                                .name("expiredFreeze").count(result.getExpiredFreeze())
                                .passed(result.getExpiredFreeze() == 0).build()
                ))
                .build();
    }

    public TrialBalanceResponse toTrialBalanceResponse(TrialBalanceDomainService.TrialBalanceResult result) {
        return TrialBalanceResponse.builder()
                .accountingDate(result.getAccountingDate())
                .passed(result.isPassed())
                .totalDebit(result.getTotalDebit())
                .totalCredit(result.getTotalCredit())
                .diff(result.getDiff())
                .subjectDetails(result.getSubjectDetails().stream()
                        .map(d -> TrialBalanceResponse.SubjectDetail.builder()
                                .subjectCode(d.getSubjectCode())
                                .subjectName(d.getSubjectName())
                                .totalDebit(d.getTotalDebit())
                                .totalCredit(d.getTotalCredit())
                                .netDiff(d.getNetDiff())
                                .build())
                        .toList())
                .imbalancedSubjects(result.getImbalancedSubjects().stream()
                        .map(d -> TrialBalanceResponse.SubjectDetail.builder()
                                .subjectCode(d.getSubjectCode())
                                .subjectName(d.getSubjectName())
                                .totalDebit(d.getTotalDebit())
                                .totalCredit(d.getTotalCredit())
                                .netDiff(d.getNetDiff())
                                .build())
                        .toList())
                .build();
    }

    public List<EodExecuteResponse.TransferRecord> toTransferRecords(
            List<PeriodEndTransferDomainService.TransferRuleResult> results) {
        return results.stream()
                .map(r -> EodExecuteResponse.TransferRecord.builder()
                        .ruleCode(r.getRuleCode())
                        .ruleName(r.getRuleName())
                        .transferNo(r.getTransferNo())
                        .voucherNo(r.getVoucherNo())
                        .totalAmount(r.getTotalAmount())
                        .status(r.getStatus() == TransferRecordStatusEnum.SUCCESS ? 2 : 3)
                        .build())
                .toList();
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add accounting-api/src/main/java/com/kltb/accounting/api/request/EodExecuteRequest.java
git add accounting-api/src/main/java/com/kltb/accounting/api/response/EodExecuteResponse.java
git add accounting-api/src/main/java/com/kltb/accounting/api/response/EodPreCheckResponse.java
git add accounting-api/src/main/java/com/kltb/accounting/api/response/TrialBalanceResponse.java
git add accounting-core/src/main/java/com/kltb/accounting/core/application/assembler/EodAssembler.java
git commit -m "feat(step-17): DTO + Assembler"
```

---

### Task 10: EodApplicationService + EodController

**Files:**
- Create: `accounting-core/src/main/java/com/kltb/accounting/core/application/service/EodApplicationService.java`
- Create: `accounting-core/src/main/java/com/kltb/accounting/core/interfaces/EodController.java`

- [ ] **Step 1: EodApplicationService**

```java
package com.kltb.accounting.core.application.service;

import com.kltb.accounting.api.request.EodExecuteRequest;
import com.kltb.accounting.api.response.EodExecuteResponse;
import com.kltb.accounting.api.response.EodPreCheckResponse;
import com.kltb.accounting.api.response.TrialBalanceResponse;
import com.kltb.accounting.core.application.assembler.EodAssembler;
import com.kltb.accounting.core.domain.service.*;
import com.kltb.accounting.core.domain.service.EodCheckDomainService.EodPreCheckResult;
import com.kltb.accounting.core.domain.service.PeriodEndTransferDomainService.TransferRuleResult;
import com.kltb.accounting.core.domain.service.TrialBalanceDomainService.TrialBalanceResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 日切应用层编排服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EodApplicationService {

    private final EodCheckDomainService eodCheckDomainService;
    private final EodDomainService eodDomainService;
    private final TrialBalanceDomainService trialBalanceDomainService;
    private final PeriodEndTransferDomainService periodEndTransferDomainService;
    private final EodAssembler eodAssembler;

    /**
     * 执行日切流程
     */
    public EodExecuteResponse executeEod(EodExecuteRequest request) {
        long startTime = System.currentTimeMillis();
        LocalDate accountingDate = request.getAccountingDate();

        // Step 1: 前置检查
        boolean preCheckPassed = false;
        EodPreCheckResult checkResult = null;
        if (!request.isSkipPreCheck()) {
            checkResult = eodCheckDomainService.checkEodPreconditions(accountingDate);
            preCheckPassed = checkResult.isAllPassed();
            if (!preCheckPassed) {
                return buildFailureResponse(request, startTime, accountingDate,
                        eodAssembler.toPreCheckResponse(checkResult));
            }
        } else {
            preCheckPassed = true;
        }

        // Step 2: 日余额计算
        List<com.kltb.accounting.core.infrastructure.persistence.entity.AccountBalancePO> balances =
                eodDomainService.calculateDailyBalances(accountingDate);
        eodDomainService.upsertDailyBalances(accountingDate, balances);
        boolean balanceCalculated = true;

        // Step 3: 试算平衡
        boolean trialBalancePassed = false;
        TrialBalanceResult trialResult = null;
        try {
            trialResult = trialBalanceDomainService.executeTrialBalance(accountingDate);
            trialBalancePassed = trialResult.isPassed();
            if (!trialBalancePassed) {
                return buildTrialBalanceFailureResponse(request, startTime, accountingDate,
                        preCheckPassed ? eodAssembler.toPreCheckResponse(checkResult) : null,
                        balanceCalculated, balances.size(),
                        eodAssembler.toTrialBalanceResponse(trialResult));
            }
        } catch (Exception e) {
            log.error("[EOD-APP] 试算平衡异常: {}", e.getMessage(), e);
            return buildTrialBalanceFailureResponse(request, startTime, accountingDate,
                    preCheckPassed ? eodAssembler.toPreCheckResponse(checkResult) : null,
                    balanceCalculated, balances.size(),
                    TrialBalanceResponse.builder()
                            .accountingDate(accountingDate)
                            .passed(false)
                            .build());
        }

        // Step 4: 期末结转（可选）
        List<TransferRuleResult> transferResults = List.of();
        if (request.isExecuteTransfer() && trialBalancePassed) {
            transferResults = periodEndTransferDomainService.executeTransferRules(accountingDate);
        }

        // Step 5: 快照生成
        int snapshotCount = 0;
        boolean snapshotGenerated = false;
        if (trialBalancePassed) {
            snapshotCount = eodDomainService.generateDailySnapshot(accountingDate);
            snapshotGenerated = snapshotCount > 0;
        }

        long totalDuration = System.currentTimeMillis() - startTime;
        log.info("[EOD-APP] 执行完成: preCheck={}, balanceCount={}, trialBalance={}, "
                        + "transferRules={}, snapshotCount={}, duration={}ms",
                preCheckPassed, balances.size(), trialBalancePassed,
                transferResults.size(), snapshotCount, totalDuration);

        return EodExecuteResponse.builder()
                .accountingDate(accountingDate)
                .preCheckPassed(preCheckPassed)
                .preCheckDetails(preCheckPassed && checkResult != null
                        ? eodAssembler.toPreCheckResponse(checkResult) : null)
                .balanceCalculated(balanceCalculated)
                .balanceCount(balances.size())
                .trialBalancePassed(trialBalancePassed)
                .trialBalanceDetails(trialResult != null
                        ? eodAssembler.toTrialBalanceResponse(trialResult) : null)
                .transferResults(eodAssembler.toTransferRecords(transferResults))
                .snapshotGenerated(snapshotGenerated)
                .snapshotCount(snapshotCount)
                .totalDurationMs(totalDuration)
                .build();
    }

    /**
     * 查询日切前置检查结果
     */
    public EodPreCheckResponse getPreCheckResult(LocalDate accountingDate) {
        EodPreCheckResult result = eodCheckDomainService.checkEodPreconditions(accountingDate);
        return eodAssembler.toPreCheckResponse(result);
    }

    /**
     * 查询试算平衡结果
     */
    public TrialBalanceResponse getTrialBalance(LocalDate accountingDate) {
        TrialBalanceResult result = trialBalanceDomainService.executeTrialBalance(accountingDate);
        return eodAssembler.toTrialBalanceResponse(result);
    }

    private EodExecuteResponse buildFailureResponse(
            EodExecuteRequest request, long startTime, LocalDate accountingDate,
            EodPreCheckResponse preCheckDetails) {
        return EodExecuteResponse.builder()
                .accountingDate(accountingDate)
                .preCheckPassed(false)
                .preCheckDetails(preCheckDetails)
                .balanceCalculated(false)
                .balanceCount(0)
                .trialBalancePassed(false)
                .transferResults(List.of())
                .snapshotGenerated(false)
                .snapshotCount(0)
                .totalDurationMs(System.currentTimeMillis() - startTime)
                .build();
    }

    private EodExecuteResponse buildTrialBalanceFailureResponse(
            EodExecuteRequest request, long startTime, LocalDate accountingDate,
            EodPreCheckResponse preCheckDetails, boolean balanceCalculated, int balanceCount,
            TrialBalanceResponse trialBalanceDetails) {
        return EodExecuteResponse.builder()
                .accountingDate(accountingDate)
                .preCheckPassed(preCheckDetails != null)
                .preCheckDetails(preCheckDetails)
                .balanceCalculated(balanceCalculated)
                .balanceCount(balanceCount)
                .trialBalancePassed(false)
                .trialBalanceDetails(trialBalanceDetails)
                .transferResults(List.of())
                .snapshotGenerated(false)
                .snapshotCount(0)
                .totalDurationMs(System.currentTimeMillis() - startTime)
                .build();
    }
}
```

- [ ] **Step 2: EodController**

```java
package com.kltb.accounting.core.interfaces;

import com.kltb.accounting.api.request.EodExecuteRequest;
import com.kltb.accounting.api.response.ApiResponse;
import com.kltb.accounting.api.response.EodExecuteResponse;
import com.kltb.accounting.api.response.EodPreCheckResponse;
import com.kltb.accounting.api.response.TrialBalanceResponse;
import com.kltb.accounting.core.application.service.EodApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 日切管理 Controller
 */
@RestController
@RequestMapping("/accounting/eod")
@RequiredArgsConstructor
@Validated
@Tag(name = "日切管理", description = "日切与试算平衡接口")
public class EodController {

    private final EodApplicationService eodApplicationService;

    @PostMapping("/execute")
    @Operation(summary = "手动触发日切", description = "执行日切前置检查、日余额计算、试算平衡、期末结转、快照生成")
    public ApiResponse<EodExecuteResponse> executeEod(@Valid @RequestBody EodExecuteRequest request) {
        EodExecuteResponse response = eodApplicationService.executeEod(request);
        return ApiResponse.ok(response);
    }

    @GetMapping("/precheck")
    @Operation(summary = "查询日切前置检查结果", description = "检查当日所有过账是否完成、无处理中事务、无未过账凭证")
    public ApiResponse<EodPreCheckResponse> getPreCheckResult(
            @Parameter(name = "accountingDate", description = "会计日期 yyyy-MM-dd")
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate accountingDate) {
        return ApiResponse.ok(eodApplicationService.getPreCheckResult(accountingDate));
    }

    @GetMapping("/trial-balance")
    @Operation(summary = "查询试算平衡结果", description = "按科目汇总当日已过账分录的借贷方发生额，验证借贷平衡")
    public ApiResponse<TrialBalanceResponse> getTrialBalance(
            @Parameter(name = "accountingDate", description = "会计日期 yyyy-MM-dd")
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate accountingDate) {
        return ApiResponse.ok(eodApplicationService.getTrialBalance(accountingDate));
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add accounting-core/src/main/java/com/kltb/accounting/core/application/service/EodApplicationService.java
git add accounting-core/src/main/java/com/kltb/accounting/core/interfaces/EodController.java
git commit -m "feat(step-17): EodApplicationService + EodController"
```

---

### Task 11: EodJobHandler（日切总调度）

**Files:**
- Create: `accounting-job/src/main/java/com/kltb/accounting/job/job/EodJobHandler.java`

- [ ] **Step 1: EodJobHandler**

```java
package com.kltb.accounting.job.job;

import com.kltb.accounting.api.request.EodExecuteRequest;
import com.kltb.accounting.api.response.EodExecuteResponse;
import com.kltb.accounting.core.application.service.EodApplicationService;
import com.kltb.accounting.core.domain.service.EodCheckDomainService;
import com.kltb.accounting.core.domain.service.TrialBalanceDomainService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 日切总调度任务
 * <p>
 * 调度配置：每日 23:55 执行（在 BufferPostingEodJob 23:50 之后 5 分钟）
 * 路由策略：FIRST（单实例执行）
 * 参数传递：通过 XXL-JOB 任务参数传入会计日期（格式 yyyy-MM-dd），不传则默认当日。
 * <p>
 * 执行流程：
 * Step 1: 日切前置检查
 * Step 2: 日余额计算
 * Step 3: 试算平衡
 * Step 4: 期末结转
 * Step 5: 余额快照生成
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EodJobHandler {

    private final EodApplicationService eodApplicationService;
    private final EodCheckDomainService eodCheckDomainService;
    private final TrialBalanceDomainService trialBalanceDomainService;

    @XxlJob("eodJob")
    public void execute() {
        String param = XxlJobHelper.getJobParam();
        LocalDate targetDate = parseAccountingDate(param);

        log.info("[EOD-JOB] 开始执行: date={}", targetDate);

        long startTime = System.currentTimeMillis();

        try {
            EodExecuteRequest request = new EodExecuteRequest();
            request.setAccountingDate(targetDate);
            request.setSkipPreCheck(false);
            request.setExecuteTransfer(true);

            EodExecuteResponse response = eodApplicationService.executeEod(request);

            log.info("[EOD-JOB] 执行完成: preCheck={}, balanceCount={}, trialBalance={}, "
                            + "transferCount={}, snapshotCount={}, duration={}ms",
                    response.isPreCheckPassed(), response.getBalanceCount(),
                    response.isTrialBalancePassed(),
                    response.getTransferResults() != null ? response.getTransferResults().size() : 0,
                    response.getSnapshotCount(), response.getTotalDurationMs());

            if (!response.isPreCheckPassed()) {
                log.error("[EOD-JOB] 前置检查失败: {}", response.getPreCheckDetails());
                XxlJobHelper.handleFail("日切前置检查失败");
                return;
            }

            if (!response.isTrialBalancePassed()) {
                log.error("[EOD-JOB] 试算平衡失败: {}", response.getTrialBalanceDetails());
                XxlJobHelper.handleFail("试算平衡失败");
                return;
            }

        } catch (Exception e) {
            log.error("[EOD-JOB] 执行异常: {}", e.getMessage(), e);
            XxlJobHelper.handleFail("日切执行异常: " + e.getMessage());
        }
    }

    private LocalDate parseAccountingDate(String param) {
        if (param != null && !param.trim().isEmpty()) {
            try {
                return LocalDate.parse(param.trim());
            } catch (DateTimeParseException e) {
                log.error("[EOD-JOB] 参数解析失败: param={}, 使用当前日期", param);
            }
        }
        return LocalDate.now();
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add accounting-job/src/main/java/com/kltb/accounting/job/job/EodJobHandler.java
git commit -m "feat(step-17): EodJobHandler 日切总调度"
```

---

### Task 12: 编译验证 + 最终整理

- [ ] **Step 1: 编译验证**

```bash
mvn compile -q
```

预期：无编译错误

- [ ] **Step 2: 修复编译问题**

根据编译错误逐一修复。常见问题：
- 缺失 import
- 方法签名不匹配
- Repository 方法未定义

- [ ] **Step 3: 最终 Commit**

```bash
git status
git add .
git commit -m "feat(step-17): EOD & Trial Balance 完整实现

- P0-1~P0-8: Mapper 方法补充 + XML + DDL 确认
- 4 个 Repository 仓储
- 4 个 DomainService（日切检查/试算平衡/日余额/期末结转）
- EodApplicationService + EodController（3 个接口）
- EodJobHandler（XXL-JOB 日切总调度）
- DTO + Assembler
- 新增错误码 2026~2029"
```

---

## 自回归检查

完成以上任务后，对照 Step 17 完成标准（Checklist）逐项检查：

### P0 前置任务
- [ ] P0-1: AccountBalanceMapper.batchUpsertBalance + XML
- [ ] P0-2: AccountBalanceSnapshotMapper.batchInsertSnapshot + XML
- [ ] P0-3: PeriodEndTransferRuleMapper.selectEnabledRules
- [ ] P0-4: PeriodEndTransferRecordMapper.selectByAccountingDate
- [ ] P0-5: TransactionMapper.countByAccountingDateAndStatus + XML
- [ ] P0-6: AccountingVoucherEntryMapper.sumEntriesBySubject + XML
- [ ] P0-7: AccountingVoucherMapper.countByAccountingDateAndStatus + XML
- [ ] P0-8: DDL 确认

### Agent-B：日切检查 + 试算平衡
- [ ] EodCheckDomainService.checkEodPreconditions 实现 5 项检查
- [ ] TrialBalanceDomainService.executeTrialBalance 按科目汇总借贷
- [ ] 差额阈值 0.000001 判定
- [ ] 不平衡时返回差异明细列表

### Agent-C：日余额计算 + 快照生成
- [ ] EodDomainService.calculateDailyBalances 计算逻辑正确
- [ ] 期末余额按余额方向正确计算
- [ ] 负数余额检测并告警
- [ ] batchUpsertBalance 批量写入
- [ ] generateDailySnapshot 生成日快照
- [ ] 月末日额外生成月快照

### Agent-D：期末结转执行
- [ ] PeriodEndTransferDomainService.executeTransferRules 按 execute_order 升序执行
- [ ] 通配符解析正确
- [ ] 结转凭证生成
- [ ] 单条规则失败不中断
- [ ] 幂等控制

### Agent-E：日切总调度 + 接口
- [ ] EodJobHandler 编排流程
- [ ] EodApplicationService 编排手动触发用例
- [ ] EodController 实现 3 个接口
- [ ] DTO 完整
