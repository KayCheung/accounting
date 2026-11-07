# step-08-account-opening · 自动化开户引擎

> **Phase 4 第一步（⚠️ 最高风险模块起点）** | 归属：`@Java` 工程师
> 前置依赖：Step 5（`AccountRepository` / `SubAccountRepository` / `SubjectRepository` 已存在）、Step 6（科目 CRUD 已实现）、Step 7（开户模板 CRUD 已实现）

---

## 0. 前置补充任务（Java-A 开始编码前必须完成）

| # | 任务 | 涉及文件 | 说明 |
|---|------|---------|------|
| P0-2 | `AccountRepository` 补充 `existsByOwnerIdAndSubjectCode` | `AccountRepository.java` + `AccountMapper.java` | 防重复开户检查（按 `ownerId + subjectCode` 唯一键查询） |
| P0-3 | `SubjectRepository` 补充 `selectAllowOpenAccountLeafSubjects` | `SubjectRepository.java` + `AccountSubjectMapper.java` | 批量扫描内部账户：查询 `allow_open_account=1 AND is_leaf=1` 科目列表 |
| P0-4 | 创建 `TransactionConfig` | `accounting-core/.../config/TransactionConfig.java` | 配置 `TransactionTemplate` Bean，Step 8 起严禁 `@Transactional`，必须使用 `TransactionTemplate` |

**P0-4 TransactionConfig 参考实现**：

```java
@Configuration
public class TransactionConfig {
    @Bean
    public TransactionTemplate transactionTemplate(DataSource dataSource) {
        return new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }
}
```

> 上述三个补充任务由 Java-A 工程师在开始编码前一并完成，不单独拆分子任务文件。

---

## 1. 任务目标（Mission）

实现自动化开户引擎，支持两种开户模式：

1. **外部客户账户开户**：基于 `t_account_template` 开户模板，在记账流程中根据 `business_code + customer_type + subject_code` 自动匹配模板并创建账户
2. **内部账户开户**：系统初始化或科目配置变更时，为 `allow_open_account=1` 的末级科目自动创建内部账户

这是记账核心引擎的入口步骤，所有后续记账、凭证、过账流程都依赖账户已存在。**开户失败必须阻断记账流程**。

---

## 2. 必读资源

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、事务规范（严禁 `@Transactional`）、POJO 规范 |
| 2 | `docs/ai-rules/accounting.md` | 账务领域规范、状态机、余额方向约束 |
| 3 | `docs/design/domain-model.md` | 账户域、科目域、模板域模型 |
| 4 | `docs/sql/1-account.sql` | `t_account` / `t_sub_account` DDL |
| 5 | `docs/sql/4-subject.sql` | `t_account_template` / `t_account_subject` DDL |
| 6 | `docs/design/flowchart/auto_account_opening_flow.mmd` | 自动开户流程图（记账流程中触发） |
| 7 | `docs/design/flowchart/account_opening_flow.mmd` | 开户流程图（外部+内部两种模式） |
| 8 | `accounting-core/.../entity/AccountPO.java` | 账户 PO |
| 9 | `accounting-core/.../entity/SubAccountPO.java` | 子账户 PO |
| 10 | `accounting-core/.../entity/AccountTemplatePO.java` | 开户模板 PO |
| 11 | `accounting-core/.../repository/AccountRepository.java` | 已有 Repository |
| 12 | `accounting-core/.../repository/SubAccountRepository.java` | 已有 Repository |
| 13 | `accounting-core/.../repository/SubjectRepository.java` | 已有 Repository |
| 14 | `accounting-core/.../redis/DistributedLockTemplate.java` | 分布式锁模板（加锁执行） |

---

## 3. 任务分配

| 工程师 | 详细文件 | 负责内容 |
|--------|---------|---------|
| Java-A | `docs/prompt/tasks/step-08-java-a.md` | P0-2/P0-3/P0-4 补充 + 账户编号生成器 + 开户领域服务 + 外部客户账户开户 |
| Java-B | `docs/prompt/tasks/step-08-java-b.md` | 内部账户开户 + 子账户自动创建 + 批量扫描 |
| Java-C | `docs/prompt/tasks/step-08-java-c.md` | 开户 Application Service + Controller + 开户事件通知 |

> **依赖关系**：Java-A 最先完成（编号生成器和领域服务是基础设施）。Java-B 依赖 Java-A 的编号生成器。Java-C 依赖 Java-A/B 的领域服务，做上层编排。建议串行执行：A → B → C。

---

## 4. 核心业务规则

### 4.1 外部客户账户开户

```
触发场景（两种路径）：
  路径 A — 外部主动开户：前端页面手动触发，调用方传入 subjectCode
  路径 B — 记账自动开户：记账流程解析规则发现账户不存在，subjectCode 从规则解析
匹配规则：根据 business_code + customer_type + subject_code 查询 t_account_template
前置校验：
  - 模板必须存在且 status=2（启用）
  - 模板 auto_open=1（支持自动开户）
  - 关联科目必须为末级（is_leaf=1）且 allow_open_account=1
  - 账户唯一键 uk_owner_id(owner_id, subject_code) 不得重复
开户动作：
  - 生成账户编号（按 acct_no_rule）
  - 生成账户名称：当前阶段直接使用 customerId 作为 accountName，超过 32 字符截断
  - 创建 t_account 主账户（status=正常，balance=0，risk_status=正常）
  - 自动创建两条 t_sub_account（balance_type=1 可用 + balance_type=2 冻结）
  - 子账户余额方向继承主账户，初始余额为 0
后置动作：
  - 记录开户日志（requestNo / openDate）
  - 发送开户事件 MQ（预留，Step 10 接入真实 MQ）
```

### 4.2 内部账户开户

```
触发条件：系统初始化 / 科目配置变更 / 手动触发
扫描规则：查询 t_account_subject WHERE allow_open_account=1 AND is_leaf=1
唯一键检查：owner_id='INNER' + subject_code 是否已存在
开户动作：
  - 生成内部账户编号：INNER + subject_code + 序号（永久递增，不每日重置）
  - 创建 t_account 主账户（owner_id=INNER，owner_type=99 其他）
  - 自动创建两条 t_sub_account（可用 + 冻结）
  - 初始化余额为 0

批量扫描策略：
  - 非事务性操作：每个账户独立 try-catch，一个失败不回滚其他
  - 每个账户开户前需加分布式锁防并发
  - 失败记录原因后 continue，最终返回统计结果
```

### 4.3 账户编号生成规则

| 账户类型 | 规则 | 示例 |
|---------|------|------|
| 外部客户账户 | `{orgCode}{yyyyMMdd}{seq5}`，序号通过 Redis INCR，**每日重置** | `00120260301000001` |
| 内部账户 | `INNER` + 科目编码 + 3位序号，**永久递增不重置** | `INNER1001001` |

> **注意**：外部账户编号每日重置是因为含日期成分，天然不冲突。内部账户编号**不含日期**，必须永久递增，否则会导致同一天内编号冲突。`acct_no_rule` / `acct_name_rule` 当前阶段采用字符串模板方式实现，不支持 SpEL。Step 19 MCP 接入后再升级为动态表达式。

### 4.4 子账户约束

```
每个 t_account 必须有且仅有两条 t_sub_account：
  - balance_type=1（可用）
  - balance_type=2（冻结）
唯一键：uk(account_no, balance_type, balance_direction)
余额方向继承主账户 balance_direction
初始余额均为 0，后续通过记账流水更新
```

---

## 5. 需要创建的文件

```
accounting-api/
└── src/main/java/com/kltb/accounting/api/
    ├── request/
    │   └── AccountOpenRequest.java              # 外部开户请求 DTO
    │   └── InternalAccountOpenRequest.java      # 内部开户请求 DTO
    └── response/
        └── AccountOpenResponse.java             # 开户结果响应 DTO

accounting-core/
└── src/main/java/com/kltb/accounting/core/
    ├── domain/
    │   └── service/
    │       └── AccountOpeningDomainService.java # 开户领域服务（核心业务逻辑）
    ├── application/
    │   └── service/
    │       └── AccountOpeningApplicationService.java  # 开户应用服务（用例编排）
    │   └── assembler/
    │       └── AccountOpeningAssembler.java     # PO ↔ Request/Response 转换
    ├── infrastructure/
    │   ├── account/
    │   │   └── AccountNoGenerator.java          # 账户编号生成器
    │   └── config/
    │       └── TransactionConfig.java           # TransactionTemplate Bean 配置
    └── controller/
        └── AccountOpeningController.java        # 开户管理 Controller
```

---

## 6. 接口契约

所有接口路径前缀：`/accounting/account/opening`

### 6.1 POST `/accounting/account/opening/external` — 外部客户开户

**请求体** (`AccountOpenRequest`):
```json
{
  "businessCode": "LOAN",
  "customerId": "CUST001",
  "customerType": 2,
  "subjectCode": "1001",
  "requestNo": "REQ20260301000001"
}
```

**校验规则**：
- `businessCode`：必填，长度 1-32
- `customerId`：必填，长度 1-64
- `customerType`：必填，1=个人，2=企业，99=其他
- `subjectCode`：**可选**，长度 1-32。传入则精确匹配模板；不传则根据 businessCode + customerType 匹配首个启用模板
- `requestNo`：必填，长度 1-64，开户请求唯一标识

**业务逻辑**：
1. 检查 `ownerId(customerId) + subjectCode` 是否已有账户（防重复开户）
2. 查询 `t_account_template` 匹配 `businessCode + customerType + subjectCode`（若 subjectCode 为空则只匹配前两个）
3. 校验模板 status=2 且 auto_open=1
4. 校验科目 is_leaf=1 且 allow_open_account=1
5. 生成账户编号和名称（accountName = customerId 截断至 32 字符）
6. 在事务中创建主账户 + 两个子账户
7. 返回开户成功信息（accountNo / accountName / subjectCode）

### 6.2 POST `/accounting/account/opening/internal` — 内部账户开户

**请求体** (`InternalAccountOpenRequest`):
```json
{
  "subjectCode": "1001"
}
```

**业务逻辑**：
1. 校验科目存在且 is_leaf=1 且 allow_open_account=1
2. 检查内部账户是否已存在（ownerId=INNER + subjectCode）
3. 生成内部账户编号（INNER + subjectCode + 序号，永久递增）
4. 在事务中创建主账户 + 两个子账户
5. 返回开户成功信息

### 6.3 POST `/accounting/account/opening/internal/batch` — 批量扫描内部账户

**请求体**：无

**业务逻辑**：
1. 扫描所有 `allow_open_account=1 AND is_leaf=1` 的科目
2. 逐个检查内部账户是否存在，不存在则自动创建
3. 每个账户独立 try-catch，失败不中断
4. 返回创建统计（总数 / 已存在 / 新创建 / 失败）

### 6.4 GET `/accounting/account/opening/{accountNo}` — 查询账户信息

**响应**：账户基本信息（用于前端确认开户结果）

---

## 7. AccountOpeningDomainService 核心方法

```java
@Service
@RequiredArgsConstructor
public class AccountOpeningDomainService {

    private final AccountRepository accountRepository;
    private final SubAccountRepository subAccountRepository;
    private final SubjectRepository subjectRepository;
    private final AccountNoGenerator accountNoGenerator;
    private final DistributedLockTemplate distributedLockTemplate;

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
     */
    public AccountPO openExternalAccount(String businessCode, String customerId,
        CustomerTypeEnum customerType, String subjectCode, String requestNo);

    /**
     * 内部账户开户（完成持久化后返回 AccountPO）
     */
    public AccountPO openInternalAccount(String subjectCode);

    /**
     * 批量扫描并创建内部账户（非事务性，逐账户 try-catch）
     */
    public BatchOpenResult scanAndOpenInternalAccounts();
}
```

> **分层决策（P1-1/P1-2 修复）**：`AccountOpeningDomainService` 加 `@Service` 注解，通过构造器注入依赖。持久化操作（insert 主账户 + 子账户）在领域服务内部完成，Application Service 只负责编排和 DTO 转换。这与项目现有模式（如 `SubjectRepository` 作为 `@Repository`）保持一致。

---

## 8. AccountNoGenerator 编号生成器

```java
@Component
@RequiredArgsConstructor
public class AccountNoGenerator {

    private final RedissonClient redissonClient;

    /**
     * 外部客户账户编号生成
     * 格式：{orgCode}{yyyyMMdd}{seq5}，如 00120260301000001
     * Redis key：account:seq:ext:{orgCode}:{yyyyMMdd}（25 小时 TTL，每日自动重置）
     */
    public String generateExternalAccountNo(String orgCode);

    /**
     * 内部账户编号生成
     * 格式：INNER + subjectCode + seq3，如 INNER1001001
     * Redis key：account:seq:inner:{subjectCode}（**永久递增，不重置**）
     */
    public String generateInternalAccountNo(String subjectCode);
}
```

> **orgCode 来源**：当前阶段从 `application.yml` 配置获取（默认 `001`），后续 Step 19 MCP 接入后从上下文动态获取。

---

## 9. 编码要点

### 9.1 事务边界

- 开户操作必须在同一事务中完成（主账户 + 两个子账户）
- 使用 `TransactionTemplate` 管理事务，严禁 `@Transactional`
- 批量扫描（`scanAndOpenInternalAccounts`）为非事务性，每个账户独立事务

### 9.2 并发安全

- 外部账户唯一键：`uk_owner_id(owner_id, subject_code)`
- 内部账户唯一键：同上，owner_id=INNER
- 高并发场景下需加分布式锁，使用 `DistributedLockTemplate.execute()`：
  - lockKey 格式：`account:open:{ownerId}:{subjectCode}`（由模板自动拼装全键）
  - 双重检查：先查询 → 不存在则加锁 → 锁内再次查询 → 创建

### 9.3 异常体系

| 场景 | 异常类型 | ResultCode |
|------|---------|-----------|
| 模板不存在 | AccountException | ACCOUNT_TEMPLATE_NOT_FOUND |
| 模板未启用 | AccountException | ACCOUNT_TEMPLATE_NOT_ENABLED |
| 模板不支持自动开户 | AccountException | ACCOUNT_TEMPLATE_NOT_AUTO_OPEN |
| 科目不允许开户 | AccountException | SUBJECT_NOT_ALLOW_OPEN_ACCOUNT |
| 科目非末级 | AccountException | SUBJECT_NOT_LEAF |
| 账户已存在 | AccountException | ACCOUNT_ALREADY_EXISTS |
| 子账户创建失败 | AccountException | SUB_ACCOUNT_CREATE_FAILED |

### 9.4 与记账流程的集成点

开户引擎完成后，将在 Step 9（业务流水入库）和 Step 10（凭证生成引擎）中被调用：

```
记账流程 → 解析规则 → 检查账户是否存在
                        ↓ 不存在
                   调用 AccountOpeningDomainService
                        ↓ auto_open=1
                   自动开户 → 继续记账
                        ↓ auto_open=0
                   抛出异常 → 记账失败
```

本次只需实现开户能力，与记账流程的集成在 Step 10 完成。

---

## 10. 完成标准（Checklist）

### Java-A
- [ ] P0-2: `AccountRepository.existsByOwnerIdAndSubjectCode` + `AccountMapper` 补充
- [ ] P0-3: `SubjectRepository.selectAllowOpenAccountLeafSubjects` + `AccountSubjectMapper` 补充
- [ ] P0-4: `TransactionConfig` 创建 `TransactionTemplate` Bean
- [ ] `AccountNoGenerator` 实现外部 + 内部账户编号生成（Redis 序号）
- [ ] `AccountOpeningDomainService.openExternalAccount` 外部客户开户全流程
- [ ] 模板存在性 + 启用状态 + auto_open 校验
- [ ] 科目末级 + allow_open_account 校验
- [ ] 账户重复检查（唯一键冲突防护）
- [ ] 并发安全：`DistributedLockTemplate` 防重复开户

### Java-B
- [ ] `AccountOpeningDomainService.openInternalAccount` 内部账户开户
- [ ] `AccountOpeningDomainService.scanAndOpenInternalAccounts` 批量扫描（非事务性）
- [ ] 子账户自动创建（可用+冻结两个）
- [ ] 子账户余额方向正确继承主账户
- [ ] 初始余额为 0
- [ ] 批量扫描时单账户失败不影响其他账户

### Java-C
- [ ] `AccountOpeningApplicationService` 编排外部/内部开户用例
- [ ] `AccountOpeningController` 实现 4 个接口
- [ ] `AccountOpenRequest` / `InternalAccountOpenRequest` / `AccountOpenResponse` DTO
- [ ] `AccountOpeningAssembler` 完成 PO ↔ DTO 转换（包路径 `application/assembler/`）
- [ ] 开户成功后预留事件通知（当前只打印日志）
- [ ] 单测全通，含正常开户、模板缺失、模板未启用、重复开户、并发开户场景

### TL Review
- [ ] 开户事务完整性（主账户+子账户在同一事务，使用 TransactionTemplate）
- [ ] 并发开户防护正确（DistributedLockTemplate + 双重检查）
- [ ] 账户编号生成规则符合设计（外部每日重置，内部永久递增）
- [ ] 子账户约束满足（每个主账户必有且仅有两个子账户）
- [ ] 异常体系使用 AccountException，ResultCode 枚举无魔法值
- [ ] 领域服务加 @Service 注解，构造器注入依赖，内部完成持久化

---

## 11. 下一步行动

进入 **Step 9 · Journaling（业务流水入库）**，详见 `docs/prompt/step-09-journaling.md`。
