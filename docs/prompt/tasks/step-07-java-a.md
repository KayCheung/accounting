# step-07-java-a · 开户模板管理接口（F-3）

> **Step 7 子任务** | 归属：`@Java` 工程师-A
> 前置依赖：Step 3（PO/Mapper 已生成）、Step 5（自定义 Mapper 方法已就绪）、Step 6（接口风格参考）

---

## 1. 任务目标

实现开户模板管理完整接口，包含模板 CRUD、关联科目末级校验、自动开户标志校验、停用联动校验等业务逻辑，
为配置管理页面提供开户模板维护能力。

---

## 2. 必读资源

在开始编码前，按顺序读取：

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、POJO 规范、事务规范 |
| 2 | `docs/ai-rules/accounting.md` | 财务律法、科目编码规则 |
| 3 | `docs/sql/4-subject.sql` | `t_account_template` 表 DDL |
| 4 | `accounting-core/.../entity/AccountTemplatePO.java` | 已有 PO 类 |
| 5 | `accounting-core/.../mapper/AccountTemplateMapper.java` | 已有 Mapper |
| 6 | `accounting-api/.../response/ApiResponse.java` | 统一响应体 |
| 7 | `accounting-api/.../response/PageResponse.java` | 分页响应体 |
| 8 | `accounting-api/.../request/PageRequest.java` | 分页请求基类 |
| 9 | `accounting-api/.../constant/ResultCode.java` | 错误码枚举 |
| 10 | Step 7 主文件 `docs/prompt/step-07-template-rule.md` | 模板业务约束 |
| 11 | `docs/design/domain-model.md` | 科目域、模板域模型说明 |
| 12 | `docs/design/flowchart/auto_account_opening_flow.mmd` | 自动开户流程图（模板关联逻辑参考） |

---

## 3. 需要创建的文件

```
accounting-api/
└── src/main/java/com/kltb/accounting/api/
    ├── request/
    │   └── TemplateCreateRequest.java          # 创建模板请求
    │   └── TemplateUpdateRequest.java           # 更新模板请求
    │   └── TemplateQueryRequest.java            # 分页查询请求（继承 PageRequest）
    └── interfaces/
        └── TemplateController.java              # 开户模板 Controller

accounting-core/
└── src/main/java/com/kltb/accounting/core/
    ├── application/
    │   └── TemplateApplicationService.java      # 模板应用服务（用例编排）
    └── application/converter/
        └── TemplateConverter.java               # PO ↔ Request/Response 转换
```

> 注意：`SubjectRepository` 已由 Step 5 创建，模板相关方法在其中维护，本次不新建 Repository。

---

## 4. SubjectRepository 模板方法补充要求

`SubjectRepository` 已由 Step 5 创建，已有模板相关方法：
- `selectTemplateByBusinessKey(String, Integer, String)` / `insertTemplate(AccountTemplatePO)` / `updateTemplateById(AccountTemplatePO)`

**本次只需补充以下缺失方法**：

```java
// 在 SubjectRepository 中补充
AccountTemplatePO selectTemplateById(Long id);
Page<AccountTemplatePO> pageTemplate(Page<AccountTemplatePO> pageParam, LambdaQueryWrapper<AccountTemplatePO> wrapper);
```

> 模板停用联动校验需要查询账户数量，但 `t_account` 无 `template_id` 字段，
> 因此需要通过 `subject_code + owner_type/customer_type` 间接关联。
> 需在 `AccountMapper` 中新增 `countBySubjectCodeAndOwnerType` 方法（见 §6.3）。
>
> 科目停用校验用 `countBySubjectCode` 需要在 `AccountTemplateMapper` 中新增（见 §6.2 科目联动校验）：
> ```java
> int countBySubjectCode(@Param("subjectCode") String subjectCode);
> // XML: SELECT COUNT(*) FROM t_account_template WHERE subject_code = #{subjectCode} AND is_delete = 0
> ```

---

## 5. 接口契约

所有接口路径前缀：`/accounting/config/template`

### 5.1 POST `/accounting/config/template` — 创建开户模板

**请求体** (`TemplateCreateRequest`):
```json
{
  "templateName": "现金账户模板",
  "businessCode": "PAYMENT",
  "customerType": 1,
  "autoOpen": true,
  "subjectCode": "101001",
  "accountType": "CASH",
  "currency": "CNY",
  "balanceDirection": 1,
  "acctNoRule": "CASH-{yyyyMMdd}-{seq}",
  "acctNameRule": "{subjectName}-{ownerName}",
  "status": 1
}
```

**校验规则**：
- `templateName`：必填，长度 1-32
- `businessCode`：必填，长度 1-32
- `customerType`：必填（1-个人 / 2-企业 / 99-其他）
- `subjectCode`：必填，长度 1-32
- `accountType`：必填，长度 1-32
- `currency`：必填，默认 CNY
- `balanceDirection`：必填（1-借 / 2-贷）

**业务逻辑**：
1. **科目校验**：按 `subjectCode` 查询科目，验证：
   - 科目存在，不存在报 `SUBJECT_NOT_FOUND`
   - 科目为末级（`is_leaf=1`），否则报 `OPERATION_NOT_ALLOWED`
   - 科目允许开户（`allow_open_account=1`），否则报 `OPERATION_NOT_ALLOWED`
2. **唯一键校验**：检查 `business_code + customer_type + subject_code` 组合是否已存在（排除 `is_delete=0`），存在则报 `PARAM_ERROR`
3. 填充审计字段，插入 `t_account_template`
4. 返回成功

### 5.2 PUT `/accounting/config/template/{templateId}` — 更新开户模板

**请求体** (`TemplateUpdateRequest`):
```json
{
  "autoOpen": true,
  "accountType": "CASH_NEW",
  "currency": "CNY",
  "balanceDirection": 1,
  "acctNoRule": "CASH-{yyyyMMdd}-{seq}",
  "acctNameRule": "{subjectName}-{ownerName}",
  "status": 1
}
```

**校验规则**：
- 不允许修改 `businessCode` / `customerType` / `subjectCode`（创建时确定，请求体不含此三字段）

**业务逻辑**：
1. 按 `templateId` 查询模板，不存在报 `TEMPLATE_NOT_FOUND`
2. 更新允许修改的字段
3. 返回成功

### 5.3 DELETE `/accounting/config/template/{templateId}` — 停用开户模板

> 模板不允许物理删除，此接口实现**停用**操作。

**业务逻辑**：
1. 按 `templateId` 查询模板，不存在报 `TEMPLATE_NOT_FOUND`
2. **停用联动校验**：
   - 按模板的 `subjectCode + customerType` 查询 `t_account` 中已关联且 `status != 3`（未注销）的账户数
   - 调用 `AccountMapper.countBySubjectCodeAndOwnerType(template.getSubjectCode(), template.getCustomerType())`
3. 校验不通过返回 `OPERATION_NOT_ALLOWED`，说明原因
4. 将 `status` 更新为 3（停用）
5. 返回成功

### 5.4 GET `/accounting/config/template/{templateId}` — 查询单个模板

**响应**：`ApiResponse<TemplateResponse>`

### 5.5 GET `/accounting/config/template/page` — 分页查询模板

**请求参数**（`TemplateQueryRequest` 继承 `PageRequest`）：
- `businessCode`：可选，按业务线过滤
- `customerType`：可选，按客户类型过滤
- `status`：可选，按状态过滤

**响应**：`ApiResponse<PageResponse<TemplateResponse>>`

**业务逻辑**：
1. 使用 `SubjectRepository.pageTemplate` 构建分页查询
2. 按 `id` 升序排列
3. 不走缓存

---

## 6. 编码要点

### 6.1 DTO 设计

```java
// TemplateResponse — 统一响应 DTO
{
  Long id;
  String businessCode;
  Integer customerType;      // 1-个人, 2-企业, 99-其他
  Boolean autoOpen;
  String subjectCode;
  String accountType;
  String currency;
  Integer balanceDirection;  // 1-借, 2-贷
  String acctNoRule;
  String acctNameRule;
  Integer status;            // 1-待启用, 2-启用, 3-停用
}
```

### 6.2 科目联动校验

```java
AccountSubjectPO subject = accountSubjectRepository.selectByCode(subjectCode);
if (subject == null) {
    throw new ServiceException(ResultCode.SUBJECT_NOT_FOUND, "科目不存在：" + subjectCode);
}
if (!Boolean.TRUE.equals(subject.getLeaf())) {
    throw new ServiceException(ResultCode.OPERATION_NOT_ALLOWED,
        "开户模板关联科目必须为末级科目：" + subjectCode);
}
if (!Boolean.TRUE.equals(subject.getAllowOpenAccount())) {
    throw new ServiceException(ResultCode.OPERATION_NOT_ALLOWED,
        "开户模板关联科目必须允许开户：" + subjectCode);
}
```

### 6.3 停用联动校验

> 注意：`t_account` 无 `template_id` 字段，模板与账户的关联通过 `subject_code + owner_type/customer_type` 间接关联。

```java
// 1. 按 ID 查模板
AccountTemplatePO template = subjectRepository.selectTemplateById(templateId);
if (template == null) {
    throw new ServiceException(ResultCode.TEMPLATE_NOT_FOUND);
}

// 2. 按模板的科目编码 + 客户类型查关联账户数
long activeAccountCount = accountMapper.countBySubjectCodeAndOwnerType(
    template.getSubjectCode(), template.getCustomerType());
if (activeAccountCount > 0) {
    throw new ServiceException(ResultCode.OPERATION_NOT_ALLOWED,
        "该模板存在已关联账户（" + activeAccountCount + "个），无法停用");
}
```

> 需要在 `AccountMapper` 中新增：
> ```java
> long countBySubjectCodeAndOwnerType(@Param("subjectCode") String subjectCode,
>                                      @Param("ownerType") Integer ownerType);
> // XML: SELECT COUNT(*) FROM t_account WHERE subject_code = #{subjectCode}
> //        AND owner_type = #{ownerType} AND is_delete = 0 AND status != 3
> // EXPLAIN verified: type=ref, key=uk_owner_id(owner_id, subject_code)
> ```

### 6.4 异常处理

| 场景 | 异常 | ResultCode |
|------|------|-----------|
| 模板不存在 | ServiceException | TEMPLATE_NOT_FOUND |
| 科目不存在 | ServiceException | SUBJECT_NOT_FOUND |
| 科目非末级 | ServiceException | OPERATION_NOT_ALLOWED |
| 科目不允许开户 | ServiceException | OPERATION_NOT_ALLOWED |
| 唯一键冲突 | ServiceException | PARAM_ERROR |
| 停用时有关联账户 | ServiceException | OPERATION_NOT_ALLOWED |

### 6.5 事务规范

- 创建/更新/停用操作必须使用 `TransactionTemplate`，**严禁 `@Transactional`**

---

## 7. 完成标准（Checklist）

- [ ] 开户模板 CRUD 接口全部实现（创建 / 更新 / 停用 / 查询 / 分页查询）
- [ ] 关联科目末级 + `allow_open_account=1` 校验
- [ ] 唯一键组合校验（business_code + customer_type + subject_code）
- [ ] 停用联动校验（无关联已开户账户）
- [ ] 单测全通，含科目联动校验场景

---

## 8. 下一步

完成后进入 Step 8，详见 `docs/prompt/step-08-account-opening.md`。
