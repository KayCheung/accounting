# 示例：余额计算（Balance Calculation）

## 场景说明

用户充值 100 元，分录方向为贷方（CREDIT），账户余额方向为贷方（CREDIT），属于**同向相加**场景。

---

## 正确实现

```java
/**
 * 执行单条分录的余额更新
 *
 * 是否记账：是（更新 t_account 并写入 t_account_detail）
 * 异常处理：余额不足抛 INSUFFICIENT_BALANCE，由上层事务回滚
 *
 * @param account 当前账户（已加 FOR UPDATE 锁）
 * @param entry   当前分录
 */
private BigDecimal calculateNewBalance(Account account, VoucherEntry entry) {
    BigDecimal oldBalance = account.getBalance();
    BigDecimal amount = entry.getAmount();

    DebitCreditEnum entryDir   = entry.getDebitCredit();
    DebitCreditEnum accountDir = account.getBalanceDirection();

    if (entryDir == accountDir) {
        // 同向相加：贷方分录 + 贷方账户 → 余额增加
        return oldBalance.add(amount);
    } else {
        // 反向相减：必须前置校验，严禁产生负数
        if (oldBalance.compareTo(amount) < 0) {
            throw new ServiceException(ResultCode.INSUFFICIENT_BALANCE,
                    String.format("账户[%s]余额不足，当前[%s]，需扣[%s]",
                            account.getAccountNo(), oldBalance, amount));
        }
        return oldBalance.subtract(amount);
    }
}
```

---

## Pre/Post 余额快照记录

```java
// 计算新余额后，必须同步写入明细账（含快照）
AccountDetail detail = new AccountDetail()
        .setAccountNo(account.getAccountNo())
        .setPreBalance(oldBalance)           // 变动前余额快照
        .setAmount(entry.getAmount())
        .setDebitCredit(entry.getDebitCredit())
        .setPostBalance(newBalance)          // 变动后余额快照
        .setVoucherNo(voucher.getVoucherNo())
        .setAccountingDate(voucher.getAccountingDate());

accountDetailRepo.save(detail);

// 更新账户余额（绝对值写入，非 SQL 增量）
account.setBalance(newBalance);
accountRepo.updateById(account);
```

---

## 禁止写法

```java
// ❌ 错误 1：SQL 直接计算余额（无快照，无方向判断）
accountMapper.update(null,
    new LambdaUpdateWrapper<AccountPO>()
        .set(AccountPO::getBalance, account.getBalance().add(amount))
        .eq(AccountPO::getId, account.getId()));

// ❌ 错误 2：double 构造 BigDecimal（精度丢失）
BigDecimal amount = new BigDecimal(100.0);

// ❌ 错误 3：equals 比较金额
if (oldBalance.equals(amount)) { ... }

// ❌ 错误 4：负数冲正
BigDecimal reverseAmount = amount.negate(); // 严禁
```
