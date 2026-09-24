# step-06-java-b · 科目基础 CRUD 接口

> **Step 6 子任务** | 归属：`@Java` 工程师-B
> 前置依赖：Step 3（PO/Mapper 已生成）、Step 5（自定义 Mapper 方法已就绪）

---

## 1. 任务目标

实现会计科目基础 CRUD 接口，包含科目编码前缀校验、停用联动校验、末级记账联动等业务逻辑，
为配置管理页面提供科目维护能力。

---

## 2. 必读资源

在开始编码前，按顺序读取：

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、POJO 规范、事务规范 |
| 2 | `docs/ai-rules/accounting.md` | 财务律法、科目编码规则 |
| 3 | `docs/sql/4-subject.sql` | `t_account_subject` 表 DDL |
| 4 | `accounting-core/.../entity/AccountSubjectPO.java` | 已有 PO 类 |
| 5 | `accounting-core/.../mapper/AccountSubjectMapper.java` | 已有 Mapper |
| 6 | `accounting-api/.../response/ApiResponse.java` | 统一响应体 |
| 7 | `accounting-api/.../response/PageResponse.java` | 分页响应体 |
| 8 | `accounting-api/.../request/PageRequest.java` | 分页请求基类 |
| 9 | `accounting-api/.../constant/ResultCode.java` | 错误码枚举 |
| 10 | Step 6 主文件 `docs/prompt/step-06-dict-subject.md` | 科目业务约束 |

---

## 3. 需要创建的文件

```
accounting-api/
└── src/main/java/com/kltb/accounting/api/
    ├── request/
    │   └── SubjectCreateRequest.java          # 创建科目请求
    │   └── SubjectUpdateRequest.java           # 更新科目请求
    │   └── SubjectQueryRequest.java            # 分页查询请求（继承 PageRequest）
    └── interfaces/
        └── SubjectController.java              # 科目管理 Controller

accounting-core/
└── src/main/java/com/kltb/accounting/core/
    ├── application/
    │   └── SubjectApplicationService.java      # 科目应用服务（用例编排）
    └── application/converter/
        └── SubjectConverter.java               # PO ↔ Request/Response 转换
```

> 注意：`SubjectRepository` 已由 Step 5 创建，本次不新建，只需补充缺失方法。
> `AccountSubjectMapper` 需在 XML 中补充 `existsByParentCode` 方法的 SQL。

---

## 4. SubjectRepository 补充要求

`SubjectRepository` 已由 Step 5 创建（科目与模板持久化仓储），现有方法：
- `selectByCode(String)` / `insertSubject(AccountSubjectPO)` / `updateSubjectById(AccountSubjectPO)`
- `selectTreeByParent(Long)` / `selectSubjectTree(Long)` / `selectLeafForPosting()`
- `selectAuxiliaryBySubjectCode(String)` / `selectTemplateByBusinessKey(...)` / `insertTemplate(...)` / `updateTemplateById(...)`

**本次只需补充以下缺失方法**（方法命名风格与已有方法保持一致）：

```java
// 在 SubjectRepository 中补充
Page<AccountSubjectPO> pageSubject(Page<AccountSubjectPO> pageParam, LambdaQueryWrapper<AccountSubjectPO> wrapper);

// 停用联动校验用
boolean existsSubjectByParentCode(String parentSubjectCode);       // 是否存在子科目（科目停用校验）
long countTemplateBySubjectCode(String subjectCode);               // 统计关联模板数（科目停用校验 → 复用 SubjectRepository 的 templateMapper）
```

> `countTemplateBySubjectCode` 需要在 `AccountTemplateMapper` 中新增对应方法（见 §6.3 停用联动校验）。
> `existsSubjectByParentCode` 需要在 `AccountSubjectMapper` 中新增对应方法。

---

## 5. 接口契约

所有接口路径前缀：`/accounting/config/subject`

### 5.1 POST `/accounting/config/subject` — 创建科目

**请求体** (`SubjectCreateRequest`):
```json
{
  "subjectCode": "101001",
  "subjectName": "现金-人民币",
  "subjectLevel": 3,
  "parentSubjectId": 1001,
  "subjectCategory": 1,
  "nature": 4,
  "debitCredit": 1,
  "leaf": true,
  "allowPost": true,
  "allowOpenAccount": false,
  "status": 1
}
```

**校验规则**：
- `subjectCode`：必填，长度 1-32
- `subjectName`：必填，长度 1-64
- `subjectLevel`：必填，≥1
- `parentSubjectId`：必填（顶级科目传 0）
- `subjectCategory`：必填（0-6）
- `nature`：必填（1-4）
- `debitCredit`：必填（1=借，2=贷）

**业务逻辑**：
1. 若 `parentSubjectId != 0`：
   - 查询父科目是否存在，不存在报 `SUBJECT_NOT_FOUND`
   - **编码前缀校验**：`subjectCode` 必须以父科目 `subjectCode` 开头（如父 101，子必须 101xxx）
2. 检查 `subjectCode` 唯一键是否已存在，存在则报 `PARAM_ERROR`
3. 若 `leaf=true` 且 `allowPost=true`，预留触发开户检查接口（本次 Step 8 实现，留 TODO 注释）
4. 插入科目记录
5. 返回成功

### 5.2 PUT `/accounting/config/subject/{subjectCode}` — 更新科目

**请求体** (`SubjectUpdateRequest`):
```json
{
  "subjectName": "现金-人民币（新）",
  "subjectCategory": 1,
  "nature": 4,
  "leaf": true,
  "allowPost": true,
  "allowOpenAccount": false,
  "status": 1
}
```

**校验规则**：
- 不允许修改 `subjectCode`（路径参数定位）
- **修改限制**：已有账户关联的科目，禁止修改 `subjectCode` / `debitCredit` / `subjectCategory`
  - `subjectCode` 不可修改（天然满足，不在请求体中）
  - `debitCredit` / `subjectCategory` 不在请求体中（天然保护）

**业务逻辑**：
1. 按 `subjectCode` 查询科目，不存在报 `SUBJECT_NOT_FOUND`
2. 更新允许修改的字段
3. 返回成功

### 5.3 DELETE `/accounting/config/subject/{subjectCode}` — 停用科目

> 科目不允许物理删除，此接口实现**停用**操作。

**业务逻辑**：
1. 按 `subjectCode` 查询科目，不存在报 `SUBJECT_NOT_FOUND`
2. **停用联动校验**（必须全部通过）：
   - 校验无子科目：`existsSubjectByParentCode(subjectCode)` 返回 false
   - 校验无开户模板引用：查询 `t_account_template` 中 `subject_code` 关联且 `is_delete=0` 的记录
   - 校验无记账规则引用：查询 `t_accounting_rule_detail` 中 `subject_code` 关联且 `is_delete=0` 的记录
3. 任一校验不通过，返回 `OPERATION_NOT_ALLOWED`，说明具体原因
4. 将 `status` 更新为 2（停用）
5. 返回成功

### 5.4 GET `/accounting/config/subject/{subjectCode}` — 查询单个科目

**响应**：`ApiResponse<SubjectResponse>`

### 5.5 GET `/accounting/config/subject/page` — 分页查询科目

**请求参数**（`SubjectQueryRequest` 继承 `PageRequest`）：
- `subjectCategory`：可选，按账类过滤
- `status`：可选，按状态过滤
- `leaf`：可选，按是否末级过滤

**响应**：`ApiResponse<PageResponse<SubjectResponse>>`

**业务逻辑**：
1. 使用 `AccountSubjectRepository` 构建分页查询
2. 按 `subjectCode` 升序排列

---

## 6. 编码要点

### 6.1 DTO 设计

```java
// SubjectResponse — 统一响应 DTO
{
  Long id;
  String subjectCode;
  String subjectName;
  Integer subjectLevel;
  Long parentSubjectId;
  Integer subjectCategory;    // 0-表外,1-资产,2-负债,3-权益,4-共同,5-成本,6-损益
  Integer nature;             // 1-非特殊,2-销账,3-贷款,4-现金
  Integer debitCredit;        // 1-借,2-贷
  Boolean leaf;
  Boolean allowPost;
  Boolean allowOpenAccount;
  Integer status;             // 1-启用,2-停用
}
```

### 6.2 科目编码前缀校验

```java
// ✅ 正确：同时验证父科目存在 + 编码前缀匹配
AccountSubjectPO parent = subjectRepository.selectByCode(parentSubjectCode);
if (parent == null) {
    throw new ServiceException(ResultCode.SUBJECT_NOT_FOUND);
}
if (!subjectCode.startsWith(parent.getSubjectCode())) {
    throw new ServiceException(ResultCode.PARAM_ERROR,
        "科目编码必须以父科目编码为前缀，父科目：" + parent.getSubjectCode());
}
```

### 6.3 停用联动校验

```java
// 1. 检查子科目
if (subjectRepository.existsSubjectByParentCode(subjectCode)) {
    throw new ServiceException(ResultCode.OPERATION_NOT_ALLOWED,
        "该科目存在子科目，无法停用");
}

// 2. 检查开户模板引用（通过 SubjectRepository 的 templateMapper）
if (subjectRepository.countTemplateBySubjectCode(subjectCode) > 0) {
    throw new ServiceException(ResultCode.OPERATION_NOT_ALLOWED,
        "该科目被开户模板引用，无法停用");
}

// 3. 检查记账规则引用（需在 AccountingRuleDetailMapper 中补充）
if (accountingRuleDetailMapper.countBySubjectCode(subjectCode) > 0) {
    throw new ServiceException(ResultCode.OPERATION_NOT_ALLOWED,
        "该科目被记账规则引用，无法停用");
}
```

> `AccountingRuleDetailMapper.countBySubjectCode` 为本次新增方法（见 §6.3）。

### 6.4 异常处理

| 场景 | 异常 | ResultCode |
|------|------|-----------|
| 科目不存在 | ServiceException | SUBJECT_NOT_FOUND |
| 父科目不存在 | ServiceException | SUBJECT_NOT_FOUND |
| 编码前缀不匹配 | ServiceException | PARAM_ERROR |
| 编码已存在 | ServiceException | PARAM_ERROR |
| 停用时有子科目/引用 | ServiceException | OPERATION_NOT_ALLOWED |

### 6.5 事务规范

- 创建/更新/停用操作必须使用 `TransactionTemplate`，**严禁 `@Transactional`**

### 6.6 新增 Mapper 方法规格

**AccountTemplateMapper**（已有 `selectByBusinessKey`，本次新增）：
```java
int countBySubjectCode(@Param("subjectCode") String subjectCode);
// XML: SELECT COUNT(*) FROM t_account_template WHERE subject_code = #{subjectCode} AND is_delete = 0
```

**AccountingRuleDetailMapper**（已有 `selectWithAuxiliary`，本次新增）：
```java
int countBySubjectCode(@Param("subjectCode") String subjectCode);
// XML: SELECT COUNT(*) FROM t_accounting_rule_detail WHERE subject_code = #{subjectCode} AND is_delete = 0
```

**AccountSubjectMapper**（已有 `selectTreeByParent` / `selectLeafForPosting`，本次新增）：
```java
boolean existsByParentCode(@Param("parentSubjectCode") String parentSubjectCode);
// XML: SELECT 1 FROM t_account_subject WHERE subject_code LIKE CONCAT(#{parentSubjectCode}, '%')
//       AND subject_code != #{parentSubjectCode} AND is_delete = 0 LIMIT 1
// 返回 1 表示存在子科目，0 表示不存在
```

---

## 7. 完成标准（Checklist）

- [ ] 科目 CRUD 接口全部实现（创建 / 更新 / 停用 / 查询 / 分页查询）
- [ ] 科目编码前缀校验实现（子科目编码必须以父科目编码开头 + 验证父科目存在）
- [ ] 停用联动校验实现（无子科目 / 无模板引用 / 无规则引用）
- [ ] 末级且 `allowPost=true` 时预留开户检查接口（TODO 注释，Step 8 实现）
- [ ] 单测全通，含停用联动校验场景

---

## 8. 下一步

完成后进入 Step 7，详见 `docs/prompt/step-07-template-rule.md`。
