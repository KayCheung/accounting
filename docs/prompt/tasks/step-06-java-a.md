# step-06-java-a · 字典管理接口（F-1）含缓存

> **Step 6 子任务** | 归属：`@Java` 工程师-A
> 前置依赖：Step 4（`DictionaryCacheService` 已存在）、Step 5（`DictionaryRepository` 已存在）

---

## 1. 任务目标

实现字典管理完整接口，包含 CRUD + 二级缓存管理，为配置管理页面提供字典数据查询与维护能力。

---

## 2. 必读资源

在开始编码前，按顺序读取：

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、POJO 规范、事务规范 |
| 2 | `docs/sql/6-infra.sql` | `t_dictionary` 表 DDL |
| 3 | `accounting-core/.../entity/DictionaryPO.java` | 已有 PO 类 |
| 4 | `accounting-core/.../mapper/DictionaryMapper.java` | 已有 Mapper |
| 5 | `accounting-core/.../repository/DictionaryRepository.java` | 已有 Repository |
| 6 | `accounting-core/.../redis/DictionaryCacheService.java` | 已有缓存服务 |
| 7 | `accounting-api/.../response/ApiResponse.java` | 统一响应体 |
| 8 | `accounting-api/.../response/PageResponse.java` | 分页响应体 |
| 9 | `accounting-api/.../request/PageRequest.java` | 分页请求基类 |
| 10 | `accounting-api/.../constant/ResultCode.java` | 错误码枚举 |

---

## 3. 需要创建的文件

```
accounting-api/
└── src/main/java/com/kltb/accounting/api/
    ├── request/
    │   └── DictCreateRequest.java          # 创建字典请求
    │   └── DictUpdateRequest.java           # 更新字典请求
    │   └── DictQueryRequest.java            # 分页查询请求（继承 PageRequest）
    └── interfaces/
        └── DictController.java              # 字典管理 Controller

accounting-core/
└── src/main/java/com/kltb/accounting/core/
    ├── application/
    │   └── DictApplicationService.java      # 字典应用服务（用例编排）
    └── application/converter/
        └── DictConverter.java               # PO ↔ Request/Response 转换
```

---

## 4. 接口契约

所有接口路径前缀：`/accounting/config/dict`

### 4.1 POST `/accounting/config/dict` — 创建字典项

**请求体** (`DictCreateRequest`):
```json
{
  "dictType": "auxiliary_type",
  "dictCode": "CUSTOMER",
  "dictName": "客户",
  "dictNameEn": "Customer",
  "sortOrder": 10,
  "groupKey": "",
  "status": 1,
  "extJson": ""
}
```

**校验规则**：
- `dictType`：必填，长度 1-32
- `dictCode`：必填，长度 1-32
- `dictName`：必填，长度 1-64
- `status`：必填，1=启用，2=停用

**业务逻辑**（Application Service）：
1. 检查 `dictType + dictCode` 唯一键是否已存在（排除 `is_delete=0`）
2. 填充审计字段（`createId`/`createName` 从上下文获取，当前可填默认值）
3. 插入 `t_dictionary`
4. 清除该 `dictType` 的二级缓存（调用 `DictionaryCacheService.invalidate()`）

### 4.2 PUT `/accounting/config/dict/{dictType}/{dictCode}` — 更新字典项

**请求体** (`DictUpdateRequest`):
```json
{
  "dictName": "客户（新）",
  "dictNameEn": "Customer (New)",
  "sortOrder": 20,
  "groupKey": "",
  "status": 1,
  "extJson": ""
}
```

**校验规则**：
- 不允许修改 `dictType` 和 `dictCode`（路径参数定位，请求体不含此二字段）

**业务逻辑**：
1. 按 `dictType + dictCode` 查询，不存在则报 `DATA_NOT_FOUND`
2. 更新允许修改的字段（`dictName`/`dictNameEn`/`sortOrder`/`groupKey`/`status`/`extJson`）
3. 清除该 `dictType` 的二级缓存

### 4.3 DELETE `/accounting/config/dict/{dictType}/{dictCode}` — 删除字典项

**业务逻辑**：
1. 按 `dictType + dictCode` 查询，不存在则报 `DATA_NOT_FOUND`
2. **拦截**：`is_system=1`（系统内置）禁止删除，返回 `OPERATION_NOT_ALLOWED`
3. 执行逻辑删除（`is_delete = System.currentTimeMillis()`）
4. 清除该 `dictType` 的二级缓存

### 4.4 GET `/accounting/config/dict/{dictType}` — 按类型查询字典列表

**响应**：该类型下全部字典项列表（非分页，用于前端下拉选择器）

**业务逻辑**：
1. 优先走 `DictionaryCacheService.getByType(dictType)`
2. 返回 `ApiResponse<List<DictResponse>>`

### 4.5 GET `/accounting/config/dict/page` — 分页查询字典

**请求参数**（`DictQueryRequest` 继承 `PageRequest`）：
- `dictType`：可选，按类型过滤
- `status`：可选，按状态过滤
- `groupKey`：可选，按分组键过滤

**响应**：`ApiResponse<PageResponse<DictResponse>>`

**业务逻辑**：
1. 使用 `DictionaryRepository` 构建分页查询
2. 按 `sortOrder` 升序排列
3. **不走缓存**（分页查询条件多变，缓存命中率低）

### 4.6 POST `/accounting/config/dict/cache/refresh` — 手动刷新缓存

**请求参数**：
- `dictType`：可选，不传则刷新全部字典缓存

**业务逻辑**：
1. 传了 `dictType` → 调用 `DictionaryCacheService.refresh(dictType)`
2. 不传 → 调用 `DictionaryCacheService.refreshAll()`
3. 返回操作成功

---

## 5. 编码要点

### 5.1 DTO 设计

```java
// DictResponse — 统一响应 DTO
{
  Long id;
  String dictType;
  String dictCode;
  String dictName;
  String dictNameEn;
  Integer sortOrder;
  String groupKey;
  Integer status;       // 1=启用, 2=停用
  Boolean system;       // 是否系统内置
  String extJson;
}
```

### 5.2 缓存策略

| 操作 | 缓存动作 |
|------|---------|
| 创建 | `invalidate(dictType)` |
| 更新 | `invalidate(dictType)` |
| 删除 | `invalidate(dictType)` |
| 按类型查询 | `getByType(dictType)`（读缓存） |
| 分页查询 | 不走缓存 |
| 手动刷新 | `refresh(dictType)` 或 `refreshAll()` |

### 5.3 Converter 规范

- `DictConverter` 负责 `DictionaryPO ↔ DictCreateRequest/DictUpdateRequest/DictResponse` 转换
- Controller 和 Service 中不得直接操作 PO 字段进行返回
- 使用 MapStruct 或手写转换方法均可（推荐手写，保持简洁）

### 5.4 异常处理

| 场景 | 异常 | ResultCode |
|------|------|-----------|
| 字典项不存在 | ServiceException | DATA_NOT_FOUND |
| 系统内置字典禁止删除 | ServiceException | OPERATION_NOT_ALLOWED |
| 唯一键冲突 | ServiceException | PARAM_ERROR |

---

## 6. 完成标准（Checklist）

- [ ] 字典 CRUD 接口全部实现（创建 / 更新 / 删除 / 按类型查询 / 分页查询）
- [ ] `is_system=1` 的字典禁止删除，接口层拦截
- [ ] 字典变更后同步清除 Caffeine + Redis 二级缓存
- [ ] 手动刷新缓存接口实现（`POST /accounting/config/dict/cache/refresh`）
- [ ] 单测全通，含缓存命中和缓存清除场景

---

## 7. 下一步

完成后进入 Step 7，详见 `docs/prompt/step-07-template-rule.md`。
