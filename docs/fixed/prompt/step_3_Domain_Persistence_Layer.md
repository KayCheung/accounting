# Step 3. Domain Modeling & Persistence Layer (全量持久层映射)

## 1. 任务目标 (Mission)
基于 `docs/fixed/sql/1-init-schema-fixed.sql` 和 `docs/fixed/sql/2-init-schema-fixed.sql` 全量 DDL，在 DDD 架构下完成基础设施层（Infrastructure）的物理映射。
1. **PO 全量化**：映射 DDL 中定义的全部物理表，确保字段类型、精度及主键策略 100% 匹配。
2. **BaseEntity 约束**：定义全局实体基类，落实自增主键与时间戳逻辑删除方案。
3. **枚举全量化**：提取所有业务状态字段，转化为领域枚举。
4. **仓储架构闭环**：确立 Mapper、Converter 与基于 ServiceImpl 的 Repository 实现规范。

## 2. 核心基类规范 (BaseEntity)
> 存放路径：`infrastructure/persistence/entity/BaseEntity.java`

- **主键策略**：统一使用自增 ID (`IdType.AUTO`)。
- **逻辑删除**：配置为 `0-正常`，`非0-删除时间戳`（利用 `@TableLogic` 的 `delval` 特性解决唯一索引冲突）。
- **通用字段**：包含 `id`, `tenantId`, `isDelete`, `createTime`, `updateTime`。
- **自动填充**：`createTime` 与 `updateTime` 需配合 MyBatis-Plus 公共字段填充器处理。

## 3. 全量枚举定义 (Domain Enums)
> 存放路径：`domain/enums/`
> 规范：仅标注 `@EnumValue`，用于 MyBatis-Plus 映射；标注 `@JsonValue`，用于 Jackson 序列化。

### 3.1 通用状态枚举
- **AvailableStatus** (1-待启用, 2-启用, 3-停用)

### 3.2 科目类枚举
- **SubjectCategoryEnum** (0-表外科目, 1-资产类, 2-负债类, 3-权益类, 4-共同类, 5-成本类, 6-损益类)
- **NatureEnum** (1-非特殊性科目, 2-销账类科目, 3-贷款类科目, 4-现金类科目)
- **DebitCreditEnum** (1-借, 2-贷)
- **BalanceDirectionEnum** (1-借, 2-贷)

### 3.3 账户类枚举
- **AccountStatusEnum** (1-正常, 2-冻结, 3-注销)
- **RiskStatusEnum** (1-正常, 2-止入, 3-止出, 4-止入止出)
- **BalanceTypeEnum** (1-可用余额, 2-冻结余额)
- **OwnerTypeEnum** (1-个人, 2-企业, 99-其他)
- **CustomerTypeEnum** (1-个人, 2-企业, 99-其他)

### 3.4 规则类枚举
- **VoucherTypeEnum** (付款凭证、收款凭证、转账凭证、汇总凭证、结账凭证、提现凭证、冻结解冻凭证等，使用字典CODE)
- **AccountScopeEnum** (1-内部分户, 2-外部分户)
- **UnilateralEnum** (0-否（异步MQ）, 1-是（实时处理）)
- **AllocationMethodEnum** (1-不分摊, 2-固定金额, 3-按比例)

### 3.5 凭证类枚举
- **VoucherStatusEnum** (1-未过账, 2-过账中, 3-已过账, 4-过账失败, 5-已冲销)
- **PostingTypeEnum** (1-手工凭证, 2-机制凭证)
- **TradeTypeEnum** (1-正常, 2-调账, 3-红字, 4-蓝字)

### 3.6 分录类枚举
- **EntryStatusEnum** (1-未过账, 2-已过账, 3-过账失败)

### 3.7 明细类枚举
- **ChangeDirectionEnum** (1-增, 2-减)

### 3.8 冻结类枚举
- **FreezeStatusEnum** (1-冻结, 2-已解冻)

### 3.9 缓冲类枚举
- **BufferModeEnum** (1-异步逐条入账, 2-日间批量入账, 3-日终批量汇总入账)
- **BufferStatusEnum** (1-待入账, 2-入账处理中, 3-入账成功, 4-入账失败)

### 3.10 事务类枚举
- **TransactionStatusEnum** (1-处理中(PROCESSING), 2-成功(SUCCESS), 3-失败(FAILED))

### 3.11 业务流水类枚举
- **BusinessRecordStatusEnum** (1-处理中, 2-成功, 3-失败)

### 3.12 消息类枚举
- **MessageStatusEnum** (1-待发送, 2-已发送, 3-发送失败, 4-已确认)
- **ReceiptStatusEnum** (1-成功, 2-失败)

### 3.13 快照类枚举
- **SnapshotTypeEnum** (1-日快照, 2-月快照, 3-年快照, 4-自定义)

### 3.14 期末结转类枚举
- **TransferTypeEnum** (1-损益结转, 2-成本结转, 3-自定义结转)
- **TransferDirectionEnum** (1-借方余额结转到贷方, 2-贷方余额结转到借方)
- **TransferRecordStatusEnum** (1-处理中, 2-成功, 3-失败)

## 4. 全量持久化实体映射表 (Infrastructure POs)
> 存放路径：`infrastructure/persistence/entity/`
> **设计规约：全量表映射，所有 PO 必须继承 `BaseEntity`。**

### 4.1 会计科目相关表
1. **AccountSubjectPO** - 会计科目表 (`t_account_subject`)
2. **AccountSubjectAuxiliaryPO** - 会计科目辅助核算项 (`t_account_subject_auxiliary`)

### 4.2 账户相关表
3. **AccountTemplatePO** - 外部客户账户开户模板表 (`t_account_template`)
4. **AccountPO** - 账户表 (`t_account`)
5. **SubAccountPO** - 子账户表 (`t_sub_account`)

### 4.3 业务流水与事务处理
6. **BusinessRecordPO** - 业务记账流水表 (`t_business_record`)
7. **BusinessDetailPO** - 业务记账流水明细表 (`t_business_detail`)
8. **TransactionPO** - 事务表 (`t_transaction`)

### 4.4 凭证与分录
9. **AccountingRulePO** - 记账规则表 (`t_accounting_rule`)
10. **AccountingRuleDetailPO** - 记账规则明细表 (`t_accounting_rule_detail`)
11. **AccountingRuleAuxiliaryPO** - 记账规则辅助核算项表 (`t_accounting_rule_auxiliary`)
12. **AccountingVoucherPO** - 记账凭证表 (`t_accounting_voucher`)
13. **AccountingVoucherAttachmentPO** - 记账凭证附件表 (`t_accounting_voucher_attachment`)
14. **AccountingVoucherEntryPO** - 分录流水表 (`t_accounting_voucher_entry`)
15. **AccountingVoucherAuxiliaryPO** - 记账凭证辅助核算项明细 (`t_accounting_voucher_auxiliary`)

### 4.5 明细账
16. **AccountDetailPO** - 账户明细表 (`t_account_detail`)
17. **SubAccountDetailPO** - 子账户明细表 (`t_sub_account_detail`)
18. **AccountFreezeDetailPO** - 账户资金冻结明细表 (`t_account_freeze_detail`)

### 4.6 缓冲处理
19. **BufferPostingRulePO** - 缓冲入账规则表 (`t_buffer_posting_rule`)
20. **BufferPostingDetailPO** - 缓冲记账明细表 (`t_buffer_posting_detail`)

### 4.7 日终或日切
21. **AccountBalancePO** - 账户日余额表 (`t_account_balance`)
22. **AccountBalanceSnapshotPO** - 账户余额快照表 (`t_account_balance_snapshot`)

### 4.8 字典表
23. **DictionaryPO** - 字典表 (`t_dictionary`)

### 4.9 本地消息和消息回执表
24. **LocalMessagePO** - 本地消息表 (`t_local_message`)
25. **MessageReceiptPO** - 消息回执表 (`t_message_receipt`)

### 4.10 期末结转
26. **PeriodEndTransferRulePO** - 期末结转规则表 (`t_period_end_transfer_rule`)
27. **PeriodEndTransferRecordPO** - 期末结转记录表 (`t_period_end_transfer_record`)

## 5. 基础设施层组件规范

### 5.1 Mapper 路径分离
- **Java 接口**：`infrastructure/persistence/mapper/`
- **XML 映射**：`src/main/resources/mapper/`
- **查询要求**：
  - 必须默认过滤 `is_delete = 0`
  - `AccountMapper` 和 `SubAccountMapper` 需针对余额更新实现乐观锁 SQL（使用 `version` 字段）
  - 涉及金额计算的查询必须使用 `BigDecimal` 类型

### 5.2 对象转换器 (Converter)
- **存放路径**：`infrastructure/persistence/converter/`
- **职责**：定义 PO 与 Domain Entity 的映射契约，处理枚举对象与数据库数值的类型转换。
- **规范**：
  - 使用 MapStruct 或手动实现
  - 枚举转换必须处理 `@EnumValue` 和 `@JsonValue`
  - 金额字段必须保持 `BigDecimal` 类型

### 5.3 仓储实现 (Repository)
- **存放路径**：`infrastructure/persistence/repository/`
- **实现方案**：继承 MyBatis-Plus 的 `ServiceImpl` 并实现领域层 Repository 接口。
- **使用原则**：
  - 优先复用 `ServiceImpl` 内置方法
  - 涉及转换逻辑或多表聚合时再自定义实现
  - 涉及余额更新的操作必须使用乐观锁或悲观锁

## 6. 辅助工具定义 (Shared Utility)
- **存放路径**：`accounting-core` -> `src/main/java/com/kltb/accounting/core/shared/util/`
- **TreeNode<T> 接口**：定义 `getId`, `getParentId` 等契约，`AccountSubject` 需实现此接口。
- **TreeUtil 契约**：提供泛型树构建逻辑（需在构建前确保数据已过滤逻辑删除），用于 Step 4 的科目树维护。

## 7. 关键注意事项

### 7.1 已删除的字段和枚举
- ❌ **不要生成** `AccountingModeEnum` 枚举（已删除 `t_accounting_rule.accounting_mode` 字段）
- ❌ **不要在** `AccountingRulePO` 中包含 `accountingMode` 字段
- ✅ **必须在** `AccountingRuleDetailPO` 中包含 `isUnilateral` 字段（类型为 `UnilateralEnum`）

### 7.2 状态枚举值修正
- ✅ **TransactionStatusEnum** 只有 3 个状态（1-处理中, 2-成功, 3-失败）
- ✅ **VoucherStatusEnum** 有 5 个状态（1-未过账, 2-过账中, 3-已过账, 4-过账失败, 5-已冲销）
- ✅ **EntryStatusEnum** 有 3 个状态（1-未过账, 2-已过账, 3-过账失败）

### 7.3 字段类型映射
- **金额字段**：`DECIMAL(18,6)` → `BigDecimal`
- **日期字段**：`DATE` → `LocalDate`
- **时间字段**：`DATETIME` → `LocalDateTime`，`TIMESTAMP` → `LocalDateTime`
- **逻辑删除**：`BIGINT` → `Long`（时间戳方案）
- **版本号**：`BIGINT` 或 `INT` → `Long` 或 `Integer`

### 7.4 唯一索引字段
确保以下字段在实体类中正确映射：
- `trace_no` + `trace_seq`（业务请求幂等）
- `voucher_no`（凭证唯一）
- `entry_id`（分录唯一）
- `subject_code`（科目唯一）
- `account_no`（账户唯一）
- `txn_no`（事务唯一）
- `message_id`（消息唯一）
- `transfer_no`（结转流水唯一）

## 8. 校验点 (Checkpoints)
- [ ] 实体类数量是否与 DDL 表数量完全一致（27 个）？
- [ ] 所有 `TINYINT` 字段是否均有对应的领域枚举映射，并且被实体类引用？
- [ ] `BaseEntity` 的逻辑删除是否正确配置为时间戳？
- [ ] Repository 是否正确继承 `ServiceImpl` 并引用了对应的 Mapper？
- [ ] 是否已删除 `AccountingModeEnum` 枚举？
- [ ] 是否已新增 `UnilateralEnum` 枚举？
- [ ] `VoucherStatusEnum` 是否有 5 个状态？
- [ ] `TransactionStatusEnum` 是否只有 3 个状态？
- [ ] 所有金额字段是否使用 `BigDecimal` 类型？
- [ ] 所有唯一索引字段是否正确映射？

## 9. 下一步行动
- **Step 4: 配置管理模块 (Configuration Module)**
- **内容**：实现科目树、开户模板、记账规则、缓冲策略的全量 CRUD 维护。

## 10. 参考文档
- **DDL 文件**：`docs/fixed/sql/1-init-schema-fixed.sql`, `docs/fixed/sql/2-init-schema-fixed.sql`
- **Steering 文件**：`.kiro/steering/02-resource-alignment.md`, `.kiro/steering/03-architecture-requirements.md`, `.kiro/steering/04-technical-standards.md`
- **修正建议**：`docs/fixed/adjustment/step_5_8_corrections.md`
