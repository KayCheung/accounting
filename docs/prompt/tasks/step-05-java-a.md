# step-05-java-a · 账户域自定义 Mapper

## 任务目标

在 Step 3 生成的基础 AccountMapper / SubAccountMapper 之上，补充账户域业务所需的自定义查询方法。

## 自定义方法清单

| # | Mapper | 方法 | SQL 位置 | 说明 | 索引 |
|---|--------|------|----------|------|------|
| 1 | AccountMapper | `selectForUpdate(accountNo)` | AccountMapper.xml | 悲观锁查询，用于并发加锁 | uk_account_no (const) |
| 2 | AccountMapper | `selectByOwnerId(ownerId)` | AccountMapper.xml | 按所有者查询账户列表 | uk_owner_id (ref) |
| 3 | AccountMapper | `selectWithSubAccounts(accountNo)` | AccountMapper.xml | LEFT JOIN 账户+子账户，返回 AccountWithSubAccountsDTO | uk_account_no (const) |
| 4 | SubAccountMapper | `selectByAccountNo(accountNo)` | SubAccountMapper.xml | 按账户编号查询子账户列表 | uk_account_no (ref) |
| 5 | SubAccountMapper | `selectForUpdate(accountNo)` | SubAccountMapper.xml | 悲观锁查询子账户 | uk_account_no (ref) |
| 6 | AccountDetailMapper | `selectByVoucherNo(voucherNo)` | AccountDetailMapper.xml | 按凭证号查询账户明细 | uk_voucher_no (ref) |
| 7 | AccountFreezeDetailMapper | `selectExpired(now)` | AccountFreezeDetailMapper.xml | 查询已过期冻结记录 | idx_account_expire (range) |

## Repository 封装

- `AccountRepository`: 封装 AccountMapper 所有方法 + `selectByAccountNo`（MyBatis-Plus 条件查询）
- `SubAccountRepository`: 封装 SubAccountMapper 所有方法
- `AccountDetailRepository`: 封装 AccountDetailMapper + AccountFreezeDetailMapper

## DTO

- `AccountWithSubAccountsDTO extends AccountPO`: 含 `subAccounts` 字段，用于联查返回

## 单测覆盖

- `AccountRepositoryTest`: selectForUpdate 返回数据正确；selectByOwnerId 返回含子账户结构；selectWithSubAccounts 返回完整DTO
