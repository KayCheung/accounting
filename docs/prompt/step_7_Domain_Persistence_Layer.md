# Step 7. Domain Modeling & Persistence Layer (领域建模与持久层实现)

## 1. 任务目标 (Mission)
本阶段基于“规则驱动”的 DDL 设计，构建财务系统的持久层模型（PO）、Mapper 接口及基础 Service。
1. **模型对齐**：将记账规则、分录明细、账户余额等物理表映射为 Java 领域对象。
2. **基建集成**：集成 MyBatis-Plus 插件，确保乐观锁、多租户及自动填充（审计字段）生效。
3. **轻量化服务**：实现规则预加载（Cache）与账户基础 CRUD，为 Step 8 的记账引擎打下底座。

## 2. 核心 PO 定义规约 (Persistent Objects)
所有实体必须位于 `com.kltb.accounting.core.domain.entity` 包下：
- **BaseEntity**: 提取公共字段 `id`, `tenant_id`, `version`, `create_time`, `update_time`, `is_delete`。
- **金额处理**: 使用 `BigDecimal`，并在 Mapper 映射中确保精度。
- **枚举映射**: 凭证类型、借贷方向、账户状态等必须使用枚举，并标注 `@EnumValue`。

## 3. 核心领域实体实现

### 3.1 记账规则 (AccountingRule)
- 映射 `t_accounting_rule`。
- 关键字段：`voucher_type` (枚举), `freeze_duration` (冻结时长)。
- 逻辑：作为入账匹配的索引头。

### 3.2 规则明细 (AccountingRuleDetail)
- 映射 `t_accounting_rule_detail`。
- 关键字段：`debit_credit` (借贷方向), `is_unilateral` (单边处理标识), `extend_script` (脚本策略)。

### 3.3 账户体系 (Account / SubAccount)
- 映射 `t_account` 与 `t_sub_account`。
- 必须实现乐观锁更新逻辑，防止余额并发篡改。

## 4. Mapper 接口规范
- 继承 `BaseMapper<T>`。
- 复杂查询（如：根据 BusinessCode 和 TradingCode 级联查询明细）需在 XML 中定义。

## 5. 轻量化 Service 职责
- **AccountingRuleService**:
   - 实现规则的本地缓存（如使用 Caffeine 或 Hutool Cache），避免记账时频繁查库。
   - 提供 `matchRule(businessCode, tradingCode, payChannel)` 核心检索接口。
- **AccountService**:
   - 提供基础的开户、账户状态校验。
   - 提供基于乐观锁的“余额前置检查”方法。

## 6. 校验逻辑 (Checkpoints)
- [ ] 实体类中 `version` 字段是否标注了 `@Version` 乐观锁注解？
- [ ] 逻辑删除字段 `is_delete` 是否配置了正确的值映射？
- [ ] `ContextInterceptor` 提取的 `tenantId` 是否能正确注入到 MyBatis-Plus 的拦截器中？

## 7. 下一步行动
- 完成持久层代码生成后，进入 **Step 8 (Posting Engine Implementation)**。
- 编写记账引擎核心逻辑：规则匹配 -> 分录生成 -> 余额校验 -> 异步/同步过账。