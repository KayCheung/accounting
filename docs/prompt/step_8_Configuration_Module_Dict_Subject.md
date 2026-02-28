# Step 8. Configuration Module - Dict & Subject (配置管理模块实现)

## 1. 任务目标
实现账务系统的静态数据底座，重点在于元数据的完整性与会计科目的逻辑严密性：
1. **字典管理**：在单表 `t_dictionary` 中实现类型与项的统一维护，并引入二级缓存。
2. **会计科目管理**：实现具备严格层级校验、属性继承及自动开户逻辑的科目树体系。
3. **接口标准化**：在 `accounting-api` 模块下定义符合规范的标准化接口，所有返回结果必须使用 `ApiResponse` 包装，分页结果使用 `PageResponse`。

## 2. 字典数据逻辑规范 (Dictionary Module)

### 2.1 业务约束
- **单表存储**：`dict_type`（类型）与 `dict_code`（项）统一在 `t_dictionary` 维护。
- **关联检查**：停用或逻辑删除 `dict_type` 或 `dict_code` 前，必须前置检查业务关联（如账户属性、规则命中等），存在引用时禁止操作。
- **缓存策略**：引入 **Caffeine + Redis** 二级缓存。
   - 读取逻辑：本地缓存 -> Redis -> 数据库回源。
   - 同步逻辑：数据库变更后，通过同步清除各节点的本地缓存及 Redis 缓存。

## 3. 会计科目管理逻辑 (Subject Management)

### 3.1 核心校验与自动联动
1. **层级一致性**：下级科目必须强制继承父科目的 `subject_category`（类别）与 `balance_direction`（余额方向）。
2. **编码前缀校验**：子科目编码必须以父科目编码开头。
3. **内部账户联动**：
   - **逻辑点**：在创建或更新科目时，若判定 `is_leaf == 1`（末级）且 `allow_post == 1`（允许记账）。
   - **处理**：// TODO: 调用 AccountDomainService 及其相关 Repository 开立对应的内部账户。
4. **变更保护**：科目一旦被模板引用或产生流水，禁止修改核心账务属性及逻辑删除。

### 3.2 树形展现形式
- **Full Tree**：完整属性树，用于科目管理维护页面。
- **Simple Tree**：精简属性树（仅含 ID、Code、Name、IsLeaf），用于记账规则等界面的下拉选择器。

## 4. 接口与契约定义 (accounting-api)
> 规范：所有返回结果统一包装在 `ApiResponse` 中；分页结果包装为 `ApiResponse<PageResponse<T>>`。

### 4.1 字典接口 (DictionaryApi)
- `ApiResponse<PageResponse<DictResponse>> pageQuery(DictQueryRequest request)`
- `ApiResponse<DictResponse> getDetail(DictDetailRequest request)`
- `ApiResponse<Void> create(DictCreateRequest request)`
- `ApiResponse<Void> update(DictUpdateRequest request)`
- `ApiResponse<Void> remove(DictRemoveRequest request)`

### 4.2 科目接口 (SubjectApi)
- `ApiResponse<PageResponse<SubjectResponse>> pageQuery(SubjectQueryRequest request)`
- `ApiResponse<SubjectResponse> getDetail(SubjectDetailRequest request)`
- `ApiResponse<List<SubjectTreeResponse>> getFullTree(SubjectTreeRequest request)`
- `ApiResponse<List<SubjectSimpleTreeResponse>> getSimpleTree(SubjectTreeRequest request)`
- `ApiResponse<Void> create(SubjectCreateRequest request)`
- `ApiResponse<Void> update(SubjectUpdateRequest request)`
- `ApiResponse<Void> remove(SubjectRemoveRequest request)`

## 5. 基础设施与实现规范
- **仓储层**：基于 `ServiceImpl`，在 Repository 层手动处理 PO 到 Domain Entity 的转换。
- **分页封装**：将 MyBatis-Plus 的 `Page` 结果集转换为 `PageResponse` 对象。
- **逻辑删除**：级联更新当前节点及子节点的 `is_delete` 为当前时间戳。

## 6. 校验点 (Checkpoints)
- [ ] 分页接口是否均使用了 `ApiResponse<PageResponse<T>>` 的嵌套结构？
- [ ] 科目新增逻辑是否包含末级科目自动开户的 `// TODO` 标记？
- [ ] 字典和科目的二级缓存是否实现了变更后的分布式同步清除？
- [ ] 所有的 API 请求（Request）和响应（Response）实体是否均定义在 `accounting-api` 模块？

## 7. 下一步行动
- **Step 9: Account Template & Opening (开户模板与自动开户)**
- **内容**：实现 `AccountTemplate` 配置，并开发基于模板的一键开立多子账户（资产、负债等）的领域服务。