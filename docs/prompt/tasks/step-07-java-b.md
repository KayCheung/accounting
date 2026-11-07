# step-07-java-b · 记账规则管理接口（F-4）含明细与辅助核算项

> **Step 7 子任务** | 归属：`@Java` 工程师-B
> 前置依赖：Step 3（PO/Mapper 已生成）、Step 5（自定义 Mapper 方法已就绪）、Step 6（接口风格参考）

---

## 1. 任务目标

实现记账规则管理完整接口，包含规则 CRUD、规则明细管理（借贷平衡校验）、辅助核算项管理（按比例分摊补差校验）、SpEL 脚本预加载校验、规则状态切换等业务逻辑，
为配置管理页面提供记账规则维护能力。

---

## 2. 必读资源

在开始编码前，按顺序读取：

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、POJO 规范、事务规范 |
| 2 | `docs/ai-rules/accounting.md` | 财务律法、借贷平衡、状态机 |
| 3 | `docs/sql/3-rule.sql` | `t_accounting_rule` + `t_accounting_rule_detail` + `t_accounting_rule_auxiliary` DDL |
| 4 | `accounting-core/.../entity/AccountingRulePO.java` | 规则 PO 类 |
| 5 | `accounting-core/.../entity/AccountingRuleDetailPO.java` | 规则明细 PO 类 |
| 6 | `accounting-core/.../entity/AccountingRuleAuxiliaryPO.java` | 辅助核算项 PO 类 |
| 7 | `accounting-core/.../mapper/AccountingRuleMapper.java` | 规则 Mapper |
| 8 | `accounting-core/.../mapper/AccountingRuleDetailMapper.java` | 规则明细 Mapper |
| 9 | `accounting-core/.../mapper/AccountingRuleAuxiliaryMapper.java` | 辅助核算项 Mapper |
| 10 | `accounting-api/.../response/ApiResponse.java` | 统一响应体 |
| 11 | `accounting-api/.../response/PageResponse.java` | 分页响应体 |
| 12 | `accounting-api/.../request/PageRequest.java` | 分页请求基类 |
| 13 | `accounting-api/.../constant/ResultCode.java` | 错误码枚举 |
| 14 | Step 7 主文件 `docs/prompt/step-07-template-rule.md` | 规则业务约束 |
| 15 | `docs/design/domain-model.md` | 规则域模型说明 |
| 16 | `docs/design/flowchart/accounting_flow.mmd` | 入账流程图（规则解析参考） |
| 17 | `docs/design/flowchart/standard_posting_flow_detailed.mmd` | 标准入账全流程（规则联动参考） |

---

## 3. 需要创建的文件

```
accounting-api/
└── src/main/java/com/kltb/accounting/api/
    ├── request/
    │   └── RuleCreateRequest.java               # 创建规则请求（含明细列表）
    │   └── RuleUpdateRequest.java               # 更新规则请求（含明细列表）
    │   └── RuleQueryRequest.java                # 分页查询请求（继承 PageRequest）
    │   └── RuleEntryRequest.java                # 规则明细行请求
    │   └── RuleAuxiliaryRequest.java            # 辅助核算项请求
    └── interfaces/
        └── RuleController.java                  # 记账规则 Controller

accounting-core/
└── src/main/java/com/kltb/accounting/core/
    ├── application/
    │   └── RuleApplicationService.java          # 规则应用服务（用例编排）
    └── application/converter/
        └── RuleConverter.java                   # PO ↔ Request/Response 转换
```

> 注意：`AccountingRuleRepository` 已由 Step 5 创建，本次不新建，只需补充缺失方法。
> `AccountingRuleDetailMapper` 和 `AccountingRuleAuxiliaryMapper` 需要新增批量操作和逻辑删除方法。

---

## 4. AccountingRuleRepository 补充要求

`AccountingRuleRepository` 已由 Step 5 创建（记账规则与缓冲记账持久化仓储），现有方法：
- `selectByBusinessKey(String, String, String)` / `selectEnabledRules()` / `insertRule(AccountingRulePO)` / `updateRuleById(AccountingRulePO)`
- `selectDetailsWithAuxiliary(Long)` / `insertRuleDetail(AccountingRuleDetailPO)` / `insertRuleAuxiliary(AccountingRuleAuxiliaryPO)`
- `selectAuxiliaryByRuleDetailId(Long)` / `selectBufferBySharding(...)` / `insertBufferPosting(...)` / `updateBufferPostingById(...)`

**本次只需补充以下缺失方法**：

```java
// 在 AccountingRuleRepository 中补充
AccountingRulePO selectRuleById(Long id);
Page<AccountingRulePO> pageRule(Page<AccountingRulePO> pageParam, LambdaQueryWrapper<AccountingRulePO> wrapper);

// 按规则ID查询规则明细（已有 selectDetailsWithAuxiliary，但需要单独的明细查询用于更新时全量替换）
void deleteRuleDetailByRuleId(Long ruleId);         // 逻辑删除该规则下的全部明细
void deleteRuleAuxiliaryByRuleDetailId(Long ruleDetailId);  // 逻辑删除指定明细的辅助核算项

// 停用联动校验用
long countVoucherByRuleBusinessKey(String businessCode, String tradingCode, String payChannel);
```

> `countVoucherByRuleBusinessKey` 是跨域查询（凭证域），用于规则停用校验。
> 为避免在 AccountingRuleRepository 中引入凭证域查询，
> 建议在 `AccountingVoucherRepository` 中新增该方法，
> `RuleApplicationService` 同时注入 `AccountingRuleRepository` 和 `AccountingVoucherRepository`。
> 即实际调用：`accountingVoucherRepository.countByBusinessKey(businessCode, tradingCode, payChannel)`。

---

## 5. 接口契约

所有接口路径前缀：`/accounting/config/rule`

### 5.1 POST `/accounting/config/rule` — 创建记账规则

**请求体** (`RuleCreateRequest`):
```json
{
  "ruleName": "付款-现金规则",
  "voucherType": "PAYMENT",
  "businessCode": "PAYMENT",
  "tradingCode": "CASH_PAY",
  "payChannel": "CASH",
  "isOpenAccount": false,
  "freezeDuration": 0,
  "preRuleId": 0,
  "status": 1,
  "entries": [
    {
      "rowNum": 1,
      "fundsType": "PRINCIPAL",
      "subjectCode": "101001",
      "accountScope": 1,
      "debitCredit": 1,
      "currency": "CNY",
      "isUnilateral": false,
      "extendScript": "",
      "summary": "现金付款",
      "auxiliaries": [
        {
          "auxType": "DEPT",
          "auxCode": "DEPT_001",
          "allocationMethod": 1,
          "allocationValue": 0,
          "extendScript": ""
        }
      ]
    },
    {
      "rowNum": 2,
      "fundsType": "PRINCIPAL",
      "subjectCode": "201001",
      "accountScope": 1,
      "debitCredit": 2,
      "currency": "CNY",
      "isUnilateral": false,
      "extendScript": "",
      "summary": "应付账款",
      "auxiliaries": []
    }
  ]
}
```

**校验规则**：
- `ruleName`：必填，长度 1-32
- `voucherType` / `businessCode` / `tradingCode` / `payChannel`：必填，长度 1-32
- `status`：必填（1-待启用 / 2-启用 / 3-停用）
- `entries`：必填，至少 2 行（借贷各一）

**业务逻辑**：
1. **唯一键校验**：检查 `business_code + trading_code + pay_channel` 组合是否已存在，存在则报 `PARAM_ERROR`
2. **科目存在性校验**：对每条明细行的 `subjectCode` 查询科目，不存在报 `SUBJECT_NOT_FOUND`
3. **借贷平衡校验**：
   - 汇总所有 `debitCredit=1`（借方）的金额字段占位（本次只校验分录行存在借贷双方）
   - 汇总所有 `debitCredit=2`（贷方）的金额字段占位
   - **必须借贷双方都有至少一行**，否则报 `PARAM_ERROR`（"记账规则必须包含借贷双方分录"）
   - > 注意：金额平衡在凭证生成时校验（Step 10），规则层只校验借贷双方都有分录
4. **SpEL 脚本预加载校验**：
   - 对每条明细的 `extendScript`，若不为空，使用 `SpelExpressionParser` 尝试解析
   - 解析失败报 `PARAM_ERROR`，说明脚本语法错误
5. **辅助核算项校验**：
   - 按比例分摊（`allocationMethod=3`）时，校验同一明细行下所有辅助核算项的 `allocationValue` 之和是否等于 1.0（允许 0.000001 精度误差）
6. 在同一事务中：
   - 插入 `t_accounting_rule`
   - 批量插入 `t_accounting_rule_detail`
   - 批量插入 `t_accounting_rule_auxiliary`
7. 返回成功

### 5.2 PUT `/accounting/config/rule/{ruleId}` — 更新记账规则

**请求体** (`RuleUpdateRequest`):
```json
{
  "ruleName": "付款-现金规则（新）",
  "voucherType": "PAYMENT",
  "isOpenAccount": false,
  "freezeDuration": 0,
  "preRuleId": 0,
  "status": 1,
  "entries": [
    // 同创建，全量替换明细与辅助核算项
  ]
}
```

**校验规则**：
- 不允许修改 `businessCode` / `tradingCode` / `payChannel`（创建时确定，请求体不含此三字段）

**业务逻辑**：
1. 按 `ruleId` 查询规则，不存在报 `RULE_NOT_FOUND`
2. 若 `status=2`（已启用），禁止修改，报 `OPERATION_NOT_ALLOWED`（"已启用的规则不允许修改，请先停用再修改"）
3. 对 entries 执行与创建时相同的校验（科目存在、借贷双方、SpEL、辅助核算）
4. 在同一事务中：
   - 更新 `t_accounting_rule` 基本信息
   - 删除旧明细（`deleteByRuleId`，逻辑删除）
   - 删除旧辅助核算项
   - 插入新明细
   - 插入新辅助核算项
5. 返回成功

### 5.3 DELETE `/accounting/config/rule/{ruleId}` — 停用记账规则

> 规则不允许物理删除，此接口实现**停用**操作。

**业务逻辑**：
1. 按 `ruleId` 查询规则，不存在报 `RULE_NOT_FOUND`
2. **停用联动校验**：
   - 校验无关联凭证引用：查询 `t_accounting_voucher` 中 `business_code + trading_code + pay_channel` 匹配且 `is_delete=0` 的记录数
3. 校验不通过返回 `OPERATION_NOT_ALLOWED`，说明原因
4. 将 `status` 更新为 3（停用）
5. 返回成功

### 5.4 GET `/accounting/config/rule/{ruleId}` — 查询单个规则

**响应**：`ApiResponse<RuleResponse>`（含全部明细与辅助核算项）

### 5.5 GET `/accounting/config/rule/page` — 分页查询规则

**请求参数**（`RuleQueryRequest` 继承 `PageRequest`）：
- `businessCode`：可选，按业务线过滤
- `tradingCode`：可选，按交易编码过滤
- `status`：可选，按状态过滤

**响应**：`ApiResponse<PageResponse<RuleResponse>>`

**业务逻辑**：
1. 使用 `AccountingRuleRepository` 构建分页查询
2. 按 `id` 升序排列
3. 不走缓存

---

## 6. 编码要点

### 6.1 DTO 设计

```java
// RuleResponse — 规则响应 DTO
{
  Long id;
  String ruleName;
  String voucherType;
  String businessCode;
  String tradingCode;
  String payChannel;
  Boolean isOpenAccount;
  Integer freezeDuration;
  Long preRuleId;
  Integer status;            // 1-待启用, 2-启用, 3-停用
  List<RuleEntryResponse> entries;
}

// RuleEntryResponse — 规则明细响应 DTO
{
  Long id;
  Integer rowNum;
  String fundsType;
  String subjectCode;
  Integer accountScope;      // 1-内部, 2-外部
  Integer debitCredit;       // 1-借, 2-贷
  String currency;
  Boolean isUnilateral;
  String extendScript;
  String summary;
  List<RuleAuxiliaryResponse> auxiliaries;
}

// RuleAuxiliaryResponse — 辅助核算项响应 DTO
{
  Long id;
  String auxType;
  String auxCode;
  Integer allocationMethod;  // 1-不分摊, 2-固定金额, 3-按比例
  BigDecimal allocationValue;
  String extendScript;
}
```

### 6.2 借贷双方校验

```java
boolean hasDebit = entries.stream().anyMatch(e -> e.getDebitCredit() == 1);
boolean hasCredit = entries.stream().anyMatch(e -> e.getDebitCredit() == 2);
if (!hasDebit || !hasCredit) {
    throw new ServiceException(ResultCode.PARAM_ERROR,
        "记账规则必须包含借贷双方分录行");
}
```

### 6.3 SpEL 脚本校验

```java
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.SpelParseException;

private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();

private void validateSpelScript(String script) {
    if (script == null || script.isBlank()) {
        return;
    }
    try {
        SPEL_PARSER.parseExpression(script);
    } catch (SpelParseException e) {
        throw new ServiceException(ResultCode.PARAM_ERROR,
            "SpEL脚本语法错误：" + e.getMessage());
    }
}
```

### 6.4 按比例分摊补差校验

```java
// 对同一明细行下的辅助核算项
if (auxiliaries.stream().anyMatch(a -> a.getAllocationMethod() == 3)) {
    BigDecimal totalRatio = auxiliaries.stream()
        .filter(a -> a.getAllocationMethod() == 3)
        .map(AccountingRuleAuxiliaryPO::getAllocationValue)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (totalRatio.compareTo(new BigDecimal("1.000000")) != 0) {
        throw new ServiceException(ResultCode.PARAM_ERROR,
            "按比例分摊的辅助核算项，分摊值之和必须等于1");
    }
}
```

### 6.5 异常处理

| 场景 | 异常 | ResultCode |
|------|------|-----------|
| 规则不存在 | ServiceException | RULE_NOT_FOUND |
| 唯一键冲突 | ServiceException | PARAM_ERROR |
| 科目不存在 | ServiceException | SUBJECT_NOT_FOUND |
| 缺少借贷方分录 | ServiceException | PARAM_ERROR |
| SpEL 脚本语法错误 | ServiceException | PARAM_ERROR |
| 分摊比例不等于 1 | ServiceException | PARAM_ERROR |
| 已启用规则禁止修改 | ServiceException | OPERATION_NOT_ALLOWED |
| 停用时有关联凭证 | ServiceException | OPERATION_NOT_ALLOWED |

### 6.6 事务规范

- 创建/更新/停用操作必须使用 `TransactionTemplate`，**严禁 `@Transactional`**
- 规则创建/更新涉及三表（rule / detail / auxiliary）写入，必须在同一事务中完成

### 6.7 跨域查询说明（规则停用校验）

规则停用需查询 `t_accounting_voucher` 中是否有该规则生成的凭证。
由于凭证属于凭证域，规则属于规则域，**不应在 `AccountingRuleRepository` 中引入凭证查询**。

正确做法：在 `RuleApplicationService` 中注入 `AccountingVoucherRepository`，
调用 `accountingVoucherRepository.countByBusinessKey(businessCode, tradingCode, payChannel)`。

需要在 `AccountingVoucherRepository` 中新增方法：
```java
long countByBusinessKey(@Param("businessCode") String businessCode,
                        @Param("tradingCode") String tradingCode,
                        @Param("payChannel") String payChannel);
// XML: SELECT COUNT(*) FROM t_accounting_voucher
//        WHERE business_code = #{businessCode}
//          AND trading_code = #{tradingCode}
//          AND pay_channel = #{payChannel}
//          AND is_delete = 0
```

---

## 7. 完成标准（Checklist）

- [ ] 记账规则 CRUD 接口全部实现（创建 / 更新 / 停用 / 查询 / 分页查询）
- [ ] 记账规则明细行借贷双方校验（必须同时有借方和贷方分录）
- [ ] 辅助核算项按比例分摊补差校验（前 N-1 按比例，最后一条补差 → 本次只校验总和=1）
- [ ] 规则停用时检查无关联凭证引用
- [ ] SpEL 脚本预加载校验（语法校验，非法脚本拒绝保存）
- [ ] 已启用规则禁止修改（必须先停用）
- [ ] 单测全通，含借贷平衡校验与分摊补差场景

---

## 8. 下一步

完成后进入 Step 8，详见 `docs/prompt/step-08-account-opening.md`。
