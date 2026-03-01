# Step 5-8 修正建议

## 检查日期
2026-03-01

## 检查范围
- Step 5: Project Initialization & API Infrastructure
- Step 6: Middleware Integration & Infrastructure
- Step 7: Domain Modeling & Persistence Layer
- Step 8: Configuration Module - Dict & Subject

## 发现的问题与修正建议

### 1. Step 7 - 删除 AccountingModeEnum 枚举

#### 问题描述
**文件**：`docs/prompt/step_7_Domain_Persistence_Layer.md`
**位置**：第 3 节 "全量枚举定义"

**当前内容**：
```markdown
- **规则类**: `VoucherTypeEnum`, `AccountingModeEnum` (1-实时, 2-异步)。
```

#### 冲突原因
根据业务逻辑澄清（Step 3），已经删除了 `t_accounting_rule.accounting_mode` 字段，统一通过分录级别的 `t_accounting_rule_detail.is_unilateral` 字段判断是否实时处理。

#### 修正建议
**删除**：`AccountingModeEnum` 枚举

**新增**：在分录明细相关枚举中添加 `UnilateralEnum`
```markdown
- **规则类**: `VoucherTypeEnum`, `UnilateralEnum` (0-否（异步MQ）, 1-是（实时处理）)。
```

#### 影响范围
- 删除 `domain/enums/AccountingModeEnum.java`
- 新增 `domain/enums/UnilateralEnum.java`
- `AccountingRule` 实体类不应包含 `accountingMode` 字段
- `AccountingRuleDetail` 实体类应包含 `isUnilateral` 字段（类型为 `UnilateralEnum`）

---

### 2. Step 7 - 修正 VoucherStatusEnum 枚举值

#### 问题描述
**文件**：`docs/prompt/step_7_Domain_Persistence_Layer.md`
**位置**：第 3 节 "全量枚举定义"

**当前内容**：
```markdown
- **凭证类**: `VoucherStatusEnum` (1-未过账, 2-过账中, 3-已过账, 4-失败)。
```

#### 冲突原因
根据业务逻辑澄清（Step 3），凭证状态 4 应该是"已冲销"而不是"失败"。红冲逻辑需要标记原凭证为"已冲销"状态。

#### 修正建议
**修改为**：
```markdown
- **凭证类**: `VoucherStatusEnum` (1-未过账, 2-过账中, 3-已过账, 4-已冲销)。
```

#### 影响范围
- `domain/enums/VoucherStatusEnum.java` 枚举值定义
- 红冲逻辑中更新原凭证状态为 `VoucherStatusEnum.REVERSED`（已冲销）

---

### 3. Step 8 - 补充内部账户开户逻辑细节

#### 问题描述
**文件**：`docs/prompt/step_8_Configuration_Module_Dict_Subject.md`
**位置**：第 3.1 节 "核心校验与自动联动"

**当前内容**：
```markdown
3. **内部账户联动**：
   - **逻辑点**：在创建或更新科目时，若判定 `is_leaf == 1`（末级）且 `allow_post == 1`（允许记账）。
   - **处理**：// TODO: 调用 AccountDomainService 及其相关 Repository 开立对应的内部账户。
```

#### 补充建议
**修改为**：
```markdown
3. **内部账户联动**：
   - **逻辑点**：在创建或更新科目时，若判定 `is_leaf == 1`（末级）且 `allow_post == 1`（允许记账）且 `allow_open_account == 1`（允许建明细账户）。
   - **处理**：// TODO: 调用 AccountDomainService 及其相关 Repository 开立对应的内部账户。
   - **开户规则**：
     - 内部账户的 `owner_id` 设置为 `INNER`
     - 内部账户的 `owner_type` 设置为 `99-其他`
     - 内部账户的 `account_no` 生成规则：`INNER-{subject_code}-{sequence}`
     - 内部账户的 `account_name` 设置为：`{subject_name}-内部账户`
     - 内部账户的 `balance_direction` 继承科目的 `debit_credit`
     - 同时创建两个子账户：
       - 可用子账户：`balance_type=1`
       - 冻结子账户：`balance_type=2`
     - 子账户的 `balance_direction` 与主账户保持一致
```

#### 影响范围
- `AccountDomainService` 需要实现内部账户开户逻辑
- 需要生成账户编号的工具类或服务

---

### 4. Step 7 - 补充缺失的枚举定义

#### 问题描述
根据数据库调整，需要新增一些枚举定义。

#### 补充建议
在 Step 7 的枚举定义中补充：

```markdown
- **分录类**: `UnilateralEnum` (0-否（异步MQ）, 1-是（实时处理）)。
- **账户作用域**: `AccountScopeEnum` (1-内部分户, 2-外部分户)。
- **增减方向**: `ChangeDirectionEnum` (1-增, 2-减)。
- **分摊方式**: `AllocationMethodEnum` (1-不分摊, 2-固定金额, 3-按比例)。
- **冻结状态**: `FreezeStatusEnum` (1-冻结, 2-已解冻)。
- **快照类型**: `SnapshotTypeEnum` (1-日快照, 2-月快照, 3-年快照, 4-自定义)。
```

---

## 修正后的完整枚举清单

### Step 7 - 第 3 节应修改为：

```markdown
## 3. 全量枚举定义 (Domain Enums)
> 存放路径：`domain/enums/`
> 规范：仅标注 `@EnumValue`。

- **通用状态**: `AvailableStatus` (1-待启用, 2-启用, 3-停用)。
- **科目类**: 
  - `SubjectCategoryEnum` (0-表外科目, 1-资产类, 2-负债类, 3-权益类, 4-共同类, 5-成本类, 6-损益类)
  - `NatureEnum` (1-非特殊性科目, 2-销账类科目, 3-贷款类科目, 4-现金类科目)
  - `DebitCreditEnum` (1-借, 2-贷)
  - `BalanceDirectionEnum` (1-借, 2-贷)
- **账户类**: 
  - `AccountStatusEnum` (1-正常, 2-冻结, 3-注销)
  - `RiskStatusEnum` (1-正常, 2-止入, 3-止出, 4-止入止出)
  - `BalanceTypeEnum` (1-可用余额, 2-冻结余额)
  - `OwnerTypeEnum` (1-个人, 2-企业, 99-其他)
- **规则类**: 
  - `VoucherTypeEnum` (付款凭证、收款凭证、转账凭证、汇总凭证、结账凭证、提现凭证、冻结解冻凭证等)
  - `AccountScopeEnum` (1-内部分户, 2-外部分户)
  - `UnilateralEnum` (0-否（异步MQ）, 1-是（实时处理）)
  - `AllocationMethodEnum` (1-不分摊, 2-固定金额, 3-按比例)
- **凭证类**: 
  - `VoucherStatusEnum` (1-未过账, 2-过账中, 3-已过账, 4-已冲销)
  - `PostingTypeEnum` (1-手工凭证, 2-机制凭证)
  - `TradeTypeEnum` (1-正常, 2-调账, 3-红字, 4-蓝字)
- **分录类**:
  - `EntryStatusEnum` (1-未过账, 2-已过账, 3-过账失败)
- **明细类**:
  - `ChangeDirectionEnum` (1-增, 2-减)
- **冻结类**:
  - `FreezeStatusEnum` (1-冻结, 2-已解冻)
- **缓冲类**: 
  - `BufferModeEnum` (1-异步逐条入账, 2-日间批量入账, 3-日终批量汇总入账)
  - `BufferStatusEnum` (1-待入账, 2-入账处理中, 3-入账成功, 4-入账失败)
- **事务类**:
  - `TransactionStatusEnum` (1-未提交, 2-部分提交, 3-全部提交, 4-部分回滚, 5-全部回滚, 6-失败)
- **业务流水类**:
  - `BusinessRecordStatusEnum` (1-处理中, 2-成功, 3-失败)
- **消息类**: 
  - `MessageStatusEnum` (1-待发送, 2-已发送, 3-发送失败, 4-已确认)
  - `ReceiptStatusEnum` (1-成功, 2-失败)
- **快照类**:
  - `SnapshotTypeEnum` (1-日快照, 2-月快照, 3-年快照, 4-自定义)
```

---

## 执行建议

### 1. 立即修正
- 更新 `docs/prompt/step_7_Domain_Persistence_Layer.md` 文件
- 更新 `docs/prompt/step_8_Configuration_Module_Dict_Subject.md` 文件

### 2. 代码实现时注意
- 不要生成 `AccountingModeEnum` 枚举类
- 生成 `UnilateralEnum` 枚举类
- `VoucherStatusEnum` 的第 4 个枚举值应该是 `REVERSED("已冲销")`
- 内部账户开户逻辑需要按照补充的规则实现

### 3. 验证清单
- [ ] 确认 `AccountingRule` 实体类不包含 `accountingMode` 字段
- [ ] 确认 `AccountingRuleDetail` 实体类包含 `isUnilateral` 字段
- [ ] 确认 `VoucherStatusEnum` 枚举值正确
- [ ] 确认内部账户开户逻辑完整

---

## 相关文档
- [业务架构与需求对齐](./step_3_architecture_requirements.md)
- [数据库设计调整建议](./database_adjustment_recommendations.md)
- [数据库调整总结](./database_adjustment_summary.md)
