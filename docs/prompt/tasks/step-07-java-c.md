# step-07-java-c · 缓冲入账规则管理接口（F-5）

> **Step 7 子任务** | 归属：`@Java` 工程师-C
> 前置依赖：Step 3（PO/Mapper 已生成）、Step 7-java-b（记账规则校验逻辑参考）

---

## 1. 任务目标

实现缓冲入账规则管理完整接口，包含规则 CRUD、时间区间不重叠校验、科目/账户非空校验、生效/失效时间合法性校验等业务逻辑，
为配置管理页面提供缓冲入账规则维护能力。

---

## 2. 必读资源

在开始编码前，按顺序读取：

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、POJO 规范、事务规范 |
| 2 | `docs/ai-rules/accounting.md` | 缓冲入账三种模式、状态机 |
| 3 | `docs/sql/3-rule.sql` | `t_buffer_posting_rule` 表 DDL |
| 4 | `accounting-core/.../entity/BufferPostingRulePO.java` | 已有 PO 类 |
| 5 | `accounting-core/.../mapper/BufferPostingRuleMapper.java` | 已有 Mapper |
| 6 | `accounting-api/.../response/ApiResponse.java` | 统一响应体 |
| 7 | `accounting-api/.../response/PageResponse.java` | 分页响应体 |
| 8 | `accounting-api/.../request/PageRequest.java` | 分页请求基类 |
| 9 | `accounting-api/.../constant/ResultCode.java` | 错误码枚举 |
| 10 | Step 7 主文件 `docs/prompt/step-07-template-rule.md` | 缓冲规则业务约束 |
| 11 | `docs/design/domain-model.md` | 缓冲规则域模型说明 |
| 12 | `docs/design/flowchart/buffer_posting_modes.mmd` | 缓冲记账三种模式流程图 |
| 13 | `docs/design/flowchart/standard_posting_flow_detailed.mmd` | 标准入账全流程（缓冲规则在凭证生成中的位置参考） |

---

## 3. 需要创建的文件

```
accounting-api/
└── src/main/java/com/kltb/accounting/api/
    ├── request/
    │   └── BufferRuleCreateRequest.java          # 创建缓冲规则请求
    │   └── BufferRuleUpdateRequest.java           # 更新缓冲规则请求
    │   └── BufferRuleQueryRequest.java            # 分页查询请求（继承 PageRequest）
    └── interfaces/
        └── BufferRuleController.java              # 缓冲入账规则 Controller

accounting-core/
└── src/main/java/com/kltb/accounting/core/
    ├── application/
    │   └── BufferRuleApplicationService.java      # 缓冲规则应用服务（用例编排）
    ├── application/converter/
    │   └── BufferRuleConverter.java               # PO ↔ Request/Response 转换
    └── infrastructure/persistence/repository/
        └── BufferPostingRuleRepository.java       # 缓冲规则仓储（封装 Mapper）
```

---

## 4. BufferPostingRuleRepository 要求

Step 5 未创建 `BufferPostingRuleRepository`，本次需要补充：

```java
@Repository
public class BufferPostingRuleRepository {
    private final BufferPostingRuleMapper bufferPostingRuleMapper;

    // 基础 CRUD
    void insert(BufferPostingRulePO po);
    boolean updateById(BufferPostingRulePO po);
    BufferPostingRulePO selectById(Long id);
    Page<BufferPostingRulePO> page(Page<BufferPostingRulePO> pageParam, LambdaQueryWrapper<BufferPostingRulePO> wrapper);

    // 业务查询 — 时间区间重叠检测
    // 查询同一 businessCode + tradingCode + payChannel 下，
    // 与 [effectiveTime, expirationTime] 有重叠的启用中规则
    List<BufferPostingRulePO> selectOverlappingRules(
        String businessCode, String tradingCode, String payChannel,
        LocalDateTime effectiveTime, LocalDateTime expirationTime,
        Long excludeId  // 更新时排除自身
    );
}
```

> `selectOverlappingRules` 需要在 `src/main/resources/mapper/BufferPostingRuleMapper.xml` 中编写自定义 SQL：
> ```xml
> <select id="selectOverlappingRules" resultType="BufferPostingRulePO">
>     SELECT * FROM t_buffer_posting_rule
>     WHERE business_code = #{businessCode}
>       AND trading_code = #{tradingCode}
>       AND pay_channel = #{payChannel}
>       AND is_delete = 0
>       AND status = 2
>       AND effective_time &lt; #{expirationTime}
>       AND expiration_time &gt; #{effectiveTime}
>       AND id != #{excludeId}
> </select>
> ```
>
> 同时在 `BufferPostingRuleMapper.java` 中声明：
> ```java
> List<BufferPostingRulePO> selectOverlappingRules(
>     @Param("businessCode") String businessCode,
>     @Param("tradingCode") String tradingCode,
>     @Param("payChannel") String payChannel,
>     @Param("effectiveTime") LocalDateTime effectiveTime,
>     @Param("expirationTime") LocalDateTime expirationTime,
>     @Param("excludeId") Long excludeId);
> ```

---

## 5. 接口契约

所有接口路径前缀：`/accounting/config/buffer-rule`

### 5.1 POST `/accounting/config/buffer-rule` — 创建缓冲入账规则

**请求体** (`BufferRuleCreateRequest`):
```json
{
  "ruleName": "现金缓冲规则",
  "bufferMode": 1,
  "businessCode": "PAYMENT",
  "tradingCode": "CASH_PAY",
  "payChannel": "CASH",
  "subjectCode": "101001",
  "accountNo": "",
  "debitCredit": 1,
  "effectiveTime": "2026-01-01 00:00:00",
  "expirationTime": "2099-12-31 23:59:59"
}
```

**校验规则**：
- `ruleName`：必填，长度 1-32
- `bufferMode`：必填（1-逐条 / 2-日间批量 / 3-日终批量）
- `businessCode` / `tradingCode` / `payChannel`：必填，长度 1-32
- `debitCredit`：必填（1-借 / 2-贷）
- `effectiveTime` / `expirationTime`：必填，格式 `yyyy-MM-dd HH:mm:ss`

**业务逻辑**：
1. **非空校验**：`subjectCode` 与 `accountNo` 必须有一个不为空，都为空报 `PARAM_ERROR`
2. **时间合法性校验**：`effectiveTime` 不得晚于 `expirationTime`，否则报 `PARAM_ERROR`
3. **时间区间重叠校验**：
   - 按 `businessCode + tradingCode + payChannel` 查询已启用（`status=2`）的规则
   - 检查是否存在时间区间重叠：`existing.effectiveTime < new.expirationTime AND existing.expirationTime > new.effectiveTime`
   - 存在重叠报 `OPERATION_NOT_ALLOWED`，说明冲突规则名称
4. 插入 `t_buffer_posting_rule`
5. 返回成功

### 5.2 PUT `/accounting/config/buffer-rule/{ruleId}` — 更新缓冲入账规则

**请求体** (`BufferRuleUpdateRequest`):
```json
{
  "ruleName": "现金缓冲规则（新）",
  "bufferMode": 2,
  "subjectCode": "101001",
  "accountNo": "",
  "debitCredit": 1,
  "effectiveTime": "2026-01-01 00:00:00",
  "expirationTime": "2099-12-31 23:59:59"
}
```

**校验规则**：
- 不允许修改 `businessCode` / `tradingCode` / `payChannel`（创建时确定）

**业务逻辑**：
1. 按 `ruleId` 查询规则，不存在报 `BUFFER_RULE_NOT_FOUND`
2. 执行与创建时相同的校验（非空、时间合法性、时间区间重叠）
   - 重叠检测时排除自身（`excludeId = ruleId`）
3. 更新字段
4. 返回成功

### 5.3 DELETE `/accounting/config/buffer-rule/{ruleId}` — 停用缓冲入账规则

> 规则不允许物理删除，此接口实现**停用**操作。

**业务逻辑**：
1. 按 `ruleId` 查询规则，不存在报 `BUFFER_RULE_NOT_FOUND`
2. 无联动校验（缓冲规则停用不影响已入缓冲的数据）
3. 将 `status` 更新为 3（停用）
4. 返回成功

### 5.4 GET `/accounting/config/buffer-rule/{ruleId}` — 查询单个缓冲规则

**响应**：`ApiResponse<BufferRuleResponse>`

### 5.5 GET `/accounting/config/buffer-rule/page` — 分页查询缓冲规则

**请求参数**（`BufferRuleQueryRequest` 继承 `PageRequest`）：
- `businessCode`：可选，按业务线过滤
- `bufferMode`：可选，按缓冲模式过滤
- `status`：可选，按状态过滤

**响应**：`ApiResponse<PageResponse<BufferRuleResponse>>`

**业务逻辑**：
1. 使用 `BufferPostingRuleRepository` 构建分页查询
2. 按 `id` 升序排列
3. 不走缓存

---

## 6. 编码要点

### 6.1 DTO 设计

```java
// BufferRuleResponse — 缓冲规则响应 DTO
{
  Long id;
  String ruleName;
  Integer bufferMode;        // 1-逐条, 2-日间批量, 3-日终批量
  String businessCode;
  String tradingCode;
  String payChannel;
  String subjectCode;
  String accountNo;
  Integer debitCredit;       // 1-借, 2-贷
  LocalDateTime effectiveTime;
  LocalDateTime expirationTime;
  Integer status;            // 1-待启用, 2-启用, 3-停用
}
```

### 6.2 时间区间重叠校验

```java
// 时间重叠条件：existing.effectiveTime < new.expirationTime AND existing.expirationTime > new.effectiveTime
List<BufferPostingRulePO> overlapping = repository.selectOverlappingRules(
    businessCode, tradingCode, payChannel,
    effectiveTime, expirationTime, excludeId);

if (!overlapping.isEmpty()) {
    String conflictNames = overlapping.stream()
        .map(BufferPostingRulePO::getRuleName)
        .collect(Collectors.joining("、"));
    throw new ServiceException(ResultCode.OPERATION_NOT_ALLOWED,
        "该业务组合下存在时间区间重叠的缓冲规则：" + conflictNames);
}
```

### 6.3 科目/账户非空校验

```java
if ((subjectCode == null || subjectCode.isBlank())
    && (accountNo == null || accountNo.isBlank())) {
    throw new ServiceException(ResultCode.PARAM_ERROR,
        "会计科目与账户编号必须有一个不为空");
}
```

### 6.4 时间合法性校验

```java
if (effectiveTime.isAfter(expirationTime)) {
    throw new ServiceException(ResultCode.PARAM_ERROR,
        "生效时间不得晚于失效时间");
}
```

### 6.5 异常处理

| 场景 | 异常 | ResultCode |
|------|------|-----------|
| 缓冲规则不存在 | ServiceException | BUFFER_RULE_NOT_FOUND |
| 科目与账户都为空 | ServiceException | PARAM_ERROR |
| 生效时间晚于失效时间 | ServiceException | PARAM_ERROR |
| 时间区间重叠 | ServiceException | OPERATION_NOT_ALLOWED |

### 6.6 事务规范

- 创建/更新/停用操作必须使用 `TransactionTemplate`，**严禁 `@Transactional`**

---

## 7. 完成标准（Checklist）

- [ ] 缓冲入账规则 CRUD 接口全部实现（创建 / 更新 / 停用 / 查询 / 分页查询）
- [ ] 时间区间不重叠校验（同一 business_code + trading_code + pay_channel）
- [ ] subject_code 与 account_no 至少一个不为空校验
- [ ] 生效时间不晚于失效时间校验
- [ ] 单测全通，含时间区间重叠校验场景（边界值：刚好相接不重叠、部分重叠、完全包含）

---

## 8. 下一步

完成后进入 Step 8，详见 `docs/prompt/step-08-account-opening.md`。
