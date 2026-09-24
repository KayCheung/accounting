# step-08-java-a · P0 补充 + 账户编号生成器 + 外部客户账户开户领域服务

> **Step 8 子任务** | 归属：`@Java` 工程师-A
> 前置依赖：Step 5（`AccountRepository` / `SubAccountRepository` / `SubjectRepository`）、Step 7（`AccountTemplateMapper`）

---

## 1. 任务目标

完成 Step 8 三项前置补充任务（P0-2/P0-3/P0-4），并实现账户编号生成器和外部客户账户开户的领域服务。

---

## 2. 必读资源

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、事务规范、POJO 规范 |
| 2 | `docs/ai-rules/accounting.md` | 账务领域规范、状态机 |
| 3 | `docs/sql/1-account.sql` | `t_account` / `t_sub_account` DDL |
| 4 | `docs/sql/4-subject.sql` | `t_account_template` / `t_account_subject` DDL |
| 5 | `docs/design/flowchart/auto_account_opening_flow.mmd` | 自动开户流程图 |
| 6 | `accounting-core/.../entity/AccountPO.java` | 账户 PO |
| 7 | `accounting-core/.../entity/SubAccountPO.java` | 子账户 PO |
| 8 | `accounting-core/.../entity/AccountTemplatePO.java` | 开户模板 PO |
| 9 | `accounting-core/.../repository/AccountRepository.java` | 已有 Repository |
| 10 | `accounting-core/.../repository/SubjectRepository.java` | 已有 Repository |
| 11 | `accounting-core/.../redis/DistributedLockTemplate.java` | 分布式锁模板 |

---

## 3. 前置补充任务（P0-2/P0-3/P0-4）

### 3.1 P0-2：AccountRepository 补充 existsByOwnerIdAndSubjectCode

**修改文件**：
- `accounting-core/.../mapper/AccountMapper.java` — 新增方法签名
- `accounting-core/.../repository/AccountRepository.java` — 新增封装方法

```java
// AccountMapper.java
boolean existsByOwnerIdAndSubjectCode(@Param("ownerId") String ownerId,
                                       @Param("subjectCode") String subjectCode);
```

```java
// AccountRepository.java
/**
 * 按 ownerId + subjectCode 唯一键检查账户是否存在
 */
public boolean existsByOwnerIdAndSubjectCode(String ownerId, String subjectCode) {
    return accountMapper.existsByOwnerIdAndSubjectCode(ownerId, subjectCode);
}
```

> MyBatis-Plus 的 `exists()` 方法可直接使用，或在 XML 中编写 `SELECT 1 FROM t_account WHERE owner_id=? AND subject_code=? AND is_delete=0 LIMIT 1`。

### 3.2 P0-3：SubjectRepository 补充 selectAllowOpenAccountLeafSubjects

**修改文件**：
- `accounting-core/.../mapper/AccountSubjectMapper.java` — 新增方法签名
- `accounting-core/.../repository/SubjectRepository.java` — 新增封装方法

```java
// AccountSubjectMapper.java
List<AccountSubjectPO> selectAllowOpenAccountLeafSubjects();
```

```java
// SubjectRepository.java
/**
 * 查询所有 allow_open_account=1 且 is_leaf=1 的科目
 */
public List<AccountSubjectPO> selectAllowOpenAccountLeafSubjects() {
    List<AccountSubjectPO> result = subjectMapper.selectAllowOpenAccountLeafSubjects();
    return result != null ? result : Collections.emptyList();
}
```

XML SQL：`SELECT * FROM t_account_subject WHERE allow_open_account=1 AND is_leaf=1 AND is_delete=0`

### 3.3 P0-4：创建 TransactionConfig

**新建文件**：`accounting-core/.../config/TransactionConfig.java`

```java
package com.kltb.accounting.core.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

/**
 * 事务模板配置，Step 8 起严禁 @Transactional，必须使用 TransactionTemplate。
 */
@Configuration
public class TransactionConfig {

    @Bean
    public TransactionTemplate transactionTemplate(DataSource dataSource) {
        return new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }
}
```

---

## 4. 需要创建的文件

```
accounting-core/
└── src/main/java/com/kltb/accounting/core/
    ├── infrastructure/
    │   └── account/
    │       └── AccountNoGenerator.java          # 账户编号生成器
    └── domain/
        └── service/
            └── AccountOpeningDomainService.java # 开户领域服务
```

---

## 5. AccountNoGenerator 实现要点

### 5.1 外部客户账户编号

```
格式：{orgCode}{yyyyMMdd}{seq5}
示例：00120260301000001
Redis key：account:seq:ext:{orgCode}:{yyyyMMdd}
TTL：25 小时（每日自动重置）
```

使用 Redisson `RAtomicLong` 实现 `INCR` 操作，序号从 1 开始，格式化为 5 位数字。

### 5.2 内部账户编号

```
格式：INNER + subjectCode + seq3
示例：INNER1001001
Redis key：account:seq:inner:{subjectCode}
注意：**永久递增，不重置**（不含日期成分，每日重置会导致编号冲突）
```

### 5.3 orgCode 获取

当前阶段 orgCode 从配置获取（默认 `001`），通过 `@Value("${accounting.org-code:001}")` 注入。

---

## 6. AccountOpeningDomainService 实现要点

### 6.1 类结构

```java
@Service
@RequiredArgsConstructor
public class AccountOpeningDomainService {

    private final AccountRepository accountRepository;
    private final SubAccountRepository subAccountRepository;
    private final SubjectRepository subjectRepository;
    private final AccountNoGenerator accountNoGenerator;
    private final DistributedLockTemplate distributedLockTemplate;
    private final TransactionTemplate transactionTemplate;
}
```

> **分层决策（P1-1/P1-2 修复）**：领域服务加 `@Service` 注解，通过构造器注入依赖。持久化操作在领域服务内部完成，Application Service 只负责编排和 DTO 转换。

### 6.2 openExternalAccount 方法

```java
/**
 * 外部客户账户开户（完成持久化后返回 AccountPO）
 * 是否记账：否（只是账户准备阶段）
 * 异常处理：
 *   - 模板不存在 → AccountException(ACCOUNT_TEMPLATE_NOT_FOUND)
 *   - 模板未启用 → AccountException(ACCOUNT_TEMPLATE_NOT_ENABLED)
 *   - 模板不支持自动开户 → AccountException(ACCOUNT_TEMPLATE_NOT_AUTO_OPEN)
 *   - 科目不允许开户 → AccountException(SUBJECT_NOT_ALLOW_OPEN_ACCOUNT)
 *   - 科目非末级 → AccountException(SUBJECT_NOT_LEAF)
 *   - 账户已存在 → AccountException(ACCOUNT_ALREADY_EXISTS)
 *
 * @param businessCode 业务线编码
 * @param customerId   客户ID（作为 ownerId）
 * @param customerType 客户类型枚举
 * @param subjectCode  科目编码（可选，传入则精确匹配模板，不传则匹配首个启用模板）
 * @param requestNo    开户请求号
 */
public AccountPO openExternalAccount(String businessCode, String customerId,
    CustomerTypeEnum customerType, String subjectCode, String requestNo);
```

**执行流程**：
1. 根据 `businessCode + customerType + subjectCode`（若 subjectCode 为空则只匹配前两个）查询开户模板
2. 校验模板 status=2（启用）且 autoOpen=true
3. 从模板获取 subjectCode
4. 校验科目 isLeaf=true 且 allowOpenAccount=1
5. 检查 `ownerId(customerId) + subjectCode` 是否已有账户（防重复）
6. 调用 `AccountNoGenerator.generateExternalAccountNo()` 生成账户编号
7. 生成账户名称：当前阶段直接使用 `customerId`，超过 32 字符截断
8. 在 `TransactionTemplate` 事务中：
   a. 构建 AccountPO 并 insert
   b. 调用内部方法创建两个 SubAccountPO 并 insert
9. 返回 AccountPO

### 6.3 并发开户防护

使用 `DistributedLockTemplate` 防并发重复开户：

```java
// lockKey 格式：account:open:{ownerId}:{subjectCode}
// 由 DistributedLockTemplate 自动拼装全键 accounting:{tenantId}:lock:{lockKey}
distributedLockTemplate.execute(
    "account:open:" + ownerId + ":" + subjectCode,
    5,   // wait 5s
    -1,  // watchdog 自动续期
    () -> {
        // 双重检查：锁内再次查询
        if (accountRepository.existsByOwnerIdAndSubjectCode(ownerId, subjectCode)) {
            throw new AccountException(ResultCode.ACCOUNT_ALREADY_EXISTS, "账户已存在");
        }
        // 执行开户逻辑...
        return accountPO;
    }
);
```

---

## 7. 编码要点

- 领域服务加 `@Service` 注解，构造器注入依赖
- 持久化操作在领域服务内部完成（与项目现有模式一致）
- 所有异常使用 `AccountException` + `ResultCode` 枚举，禁止魔法值
- 分布式锁 key 复用 `DistributedLockTemplate` 格式，传 `account:open:{ownerId}:{subjectCode}`

---

## 8. 完成标准

- [ ] P0-2: `AccountRepository.existsByOwnerIdAndSubjectCode` 正确实现
- [ ] P0-3: `SubjectRepository.selectAllowOpenAccountLeafSubjects` 正确实现
- [ ] P0-4: `TransactionConfig` 创建 `TransactionTemplate` Bean
- [ ] `AccountNoGenerator` 外部 + 内部编号生成正确
- [ ] Redis 序号：外部每日重置，内部永久递增
- [ ] 外部客户开户全流程正确（含事务+持久化）
- [ ] 模板存在性 + 启用状态 + auto_open 校验
- [ ] 科目末级 + allow_open_account 校验
- [ ] 账户重复检查
- [ ] 分布式锁防并发开户

---

## 9. 下一步

完成后通知 Java-B 可以开始，详见 `docs/prompt/tasks/step-08-java-b.md`。
