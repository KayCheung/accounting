# step-06-java-c · 科目树形查询 + 辅助核算项接口

> **Step 6 子任务** | 归属：`@Java` 工程师-C
> 前置依赖：Step 6-java-b（科目 Service 的状态校验方法），建议 Java-B 完成后开始

---

## 1. 任务目标

实现科目树形结构查询接口（懒加载 + 全量两种模式）和辅助核算项 CRUD 接口，
为配置管理前端提供科目树展示和辅助核算维度配置能力。

---

## 2. 必读资源

在开始编码前，按顺序读取：

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、POJO 规范 |
| 2 | `docs/sql/4-subject.sql` | `t_account_subject` + `t_account_subject_auxiliary` DDL |
| 3 | `accounting-core/.../entity/AccountSubjectPO.java` | 科目 PO 类 |
| 4 | `accounting-core/.../entity/AccountSubjectAuxiliaryPO.java` | 辅助核算项 PO 类 |
| 5 | `accounting-core/.../mapper/AccountSubjectMapper.java` | 科目 Mapper（含 `selectTreeByParent`） |
| 6 | `accounting-core/.../mapper/AccountSubjectAuxiliaryMapper.java` | 辅助核算项 Mapper |
| 7 | `accounting-core/.../repository/SubjectRepository.java` | 已有科目仓储（Step 5 创建） |
| 8 | `accounting-api/.../response/ApiResponse.java` | 统一响应体 |
| 9 | Step 6 主文件 `docs/prompt/step-06-dict-subject.md` | 科目业务约束 |

---

## 3. 需要创建的文件

```
accounting-api/
└── src/main/java/com/kltb/accounting/api/
    ├── request/
    │   └── AuxiliaryCreateRequest.java      # 创建辅助核算项请求
    │   └── AuxiliaryUpdateRequest.java       # 更新辅助核算项请求
    └── interfaces/
        └── SubjectTreeController.java        # 科目树 + 辅助核算项 Controller

accounting-core/
└── src/main/java/com/kltb/accounting/core/
    ├── application/
    │   └── SubjectTreeApplicationService.java  # 科目树 + 辅助核算项应用服务
    └── application/converter/
        └── SubjectTreeConverter.java           # PO ↔ Request/Response 转换
```

---

## 4. 接口契约

### 4.1 GET `/accounting/config/subject/tree` — 科目树形查询

**请求参数**：
- `parentId`：可选，父科目 ID
  - 不传 → 返回全量树（从顶级科目开始）
  - 传值 → 懒加载模式，返回该科目下的直接子节点列表
- `status`：可选，按状态过滤（默认只返回启用状态 `status=1`）

**响应**：
```json
// 全量模式 / 懒加载模式统一返回扁平列表
{
  "code": "0",
  "data": [
    {
      "id": 1,
      "subjectCode": "101",
      "subjectName": "现金",
      "subjectLevel": 1,
      "parentSubjectId": 0,
      "subjectCategory": 1,
      "nature": 4,
      "debitCredit": 1,
      "leaf": false,
      "allowPost": false,
      "status": 1
    },
    {
      "id": 2,
      "subjectCode": "101001",
      "subjectName": "现金-人民币",
      "subjectLevel": 2,
      "parentSubjectId": 1,
      "subjectCategory": 1,
      "nature": 4,
      "debitCredit": 1,
      "leaf": true,
      "allowPost": true,
      "status": 1
    }
  ]
}
```

> 前端自行组装树形结构，后端返回扁平列表 + 携带 `parentSubjectId` 即可。

**业务逻辑**：

1. **懒加载模式**（`parentId` 有值）：
   - 调用 `AccountSubjectMapper.selectTreeByParent(parentId)`
   - 过滤 `status=1` 的记录（除非显式传 `status` 参数）
   - 返回子科目列表

2. **全量模式**（`parentId` 为空）：
   - 查询全部科目（`is_delete=0`）
   - 过滤 `status=1` 的记录（除非显式传 `status` 参数）
   - 按 `subjectCode` 升序排列
   - 返回全部科目列表

### 4.2 POST `/accounting/config/subject/{subjectCode}/auxiliary` — 为科目添加辅助核算项

**请求体** (`AuxiliaryCreateRequest`):
```json
{
  "auxiliaryType": "CUSTOMER",
  "required": true,
  "defaultAuxCode": ""
}
```

**校验规则**：
- `auxiliaryType`：必填，长度 1-32
- `required`：必填，true=必填，false=可选

**业务逻辑**：
1. 检查科目是否存在（按 `subjectCode`），不存在报 `SUBJECT_NOT_FOUND`
2. 检查 `subjectCode + auxiliaryType` 唯一键是否已存在，存在则报 `PARAM_ERROR`
3. 插入 `t_account_subject_auxiliary`
4. 返回成功

### 4.3 DELETE `/accounting/config/subject/{subjectCode}/auxiliary/{auxiliaryType}` — 删除辅助核算项

**业务逻辑**：
1. 按 `subjectCode + auxiliaryType` 查询，不存在报 `DATA_NOT_FOUND`
2. **必填保护**：`required=true` 的项目不允许删除，返回 `OPERATION_NOT_ALLOWED`
3. 执行逻辑删除（`is_delete = System.currentTimeMillis()`）
4. 返回成功

### 4.4 PUT `/accounting/config/subject/{subjectCode}/auxiliary/{auxiliaryType}` — 更新辅助核算项

**请求体** (`AuxiliaryUpdateRequest`):
```json
{
  "required": false,
  "defaultAuxCode": "DEFAULT_CUSTOMER"
}
```

**业务逻辑**：
1. 按 `subjectCode + auxiliaryType` 查询，不存在报 `DATA_NOT_FOUND`
2. 更新 `required` 和 `defaultAuxCode`
3. 返回成功

### 4.5 GET `/accounting/config/subject/{subjectCode}/auxiliary` — 查询科目的辅助核算项列表

**响应**：`ApiResponse<List<AuxiliaryResponse>>`

**业务逻辑**：
1. 按 `subjectCode` 查询辅助核算项列表
2. 按 `id` 升序排列

---

## 5. 编码要点

### 5.1 DTO 设计

```java
// AuxiliaryResponse — 辅助核算项响应 DTO
{
  Long id;
  String subjectCode;
  String auxiliaryType;
  Boolean required;       // true=必填, false=可选
  String defaultAuxCode;
}
```

### 5.2 辅助核算项必填保护 + 唯一键预检查

```java
// 删除前：先查后判（避免依赖数据库唯一键异常）
AccountSubjectAuxiliaryPO aux = subjectAuxiliaryMapper.selectOne(
    new LambdaQueryWrapper<AccountSubjectAuxiliaryPO>()
        .eq(AccountSubjectAuxiliaryPO::getSubjectCode, subjectCode)
        .eq(AccountSubjectAuxiliaryPO::getAuxiliaryType, auxiliaryType)
        .eq(AccountSubjectAuxiliaryPO::getIsDelete, 0));
if (aux == null) {
    throw new ServiceException(ResultCode.DATA_NOT_FOUND);
}
if (Boolean.TRUE.equals(aux.getRequired())) {
    throw new ServiceException(ResultCode.OPERATION_NOT_ALLOWED,
        "该辅助核算项为必填项，不允许删除");
}
aux.setIsDelete(System.currentTimeMillis());
subjectAuxiliaryMapper.updateById(aux);
```

> 创建时同样需要预检查：先查询 `subjectCode + auxiliaryType` 是否已存在，
> 存在则报 `PARAM_ERROR`，而非依赖数据库唯一键异常。

### 5.3 树形查询复用

- `SubjectRepository` 已有 `selectTreeByParent(Long parentSubjectId)` 方法（Step 5 创建）
- `SubjectRepository` 已有 `selectSubjectTree(Long rootSubjectId)` 方法，支持全量树加载
- 全量查询直接复用已有方法，无需额外 Mapper 方法

### 5.4 辅助核算项预检查

```java
// 创建辅助核算项前预检查
AccountSubjectAuxiliaryPO existing = subjectAuxiliaryMapper.selectOne(
    new LambdaQueryWrapper<AccountSubjectAuxiliaryPO>()
        .eq(AccountSubjectAuxiliaryPO::getSubjectCode, subjectCode)
        .eq(AccountSubjectAuxiliaryPO::getAuxiliaryType, auxiliaryType)
        .eq(AccountSubjectAuxiliaryPO::getIsDelete, 0));
if (existing != null) {
    throw new ServiceException(ResultCode.PARAM_ERROR,
        "该科目的辅助核算项已存在：" + auxiliaryType);
}
```

### 5.4 异常处理

| 场景 | 异常 | ResultCode |
|------|------|-----------|
| 科目不存在 | ServiceException | SUBJECT_NOT_FOUND |
| 辅助核算项不存在 | ServiceException | DATA_NOT_FOUND |
| 辅助核算项已存在 | ServiceException | PARAM_ERROR |
| 必填项禁止删除 | ServiceException | OPERATION_NOT_ALLOWED |

### 5.5 事务规范

- 辅助核算项的创建/更新/删除操作必须使用 `TransactionTemplate`，**严禁 `@Transactional`**

---

## 6. 完成标准（Checklist）

- [ ] 科目树形接口实现（懒加载 + 全量两种模式）
- [ ] 辅助核算项 CRUD 接口实现（创建 / 更新 / 删除 / 查询列表）
- [ ] 辅助核算项必填校验：`required=true` 的项目不允许删除
- [ ] 单测全通，含三级树形结构正确性验证

---

## 7. 下一步

完成后进入 Step 7，详见 `docs/prompt/step-07-template-rule.md`。
