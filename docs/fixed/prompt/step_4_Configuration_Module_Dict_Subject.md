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
   - **逻辑点**：在创建或更新科目时，若判定 `is_leaf == 1`（末级）且 `allow_post == 1`（允许记账）且 `allow_open_account == 1`（允许建明细账户）。
   - **处理**：调用 AccountDomainService 及其相关 Repository 开立对应的内部账户。
   - **开户规则**：
     - 内部账户的 `owner_id` 设置为 `INNER`
     - 内部账户的 `owner_type` 设置为 `99-其他`
     - 内部账户的 `account_no` 生成规则：`INNER-{subject_code}-{sequence}`
     - 内部账户的 `account_name` 设置为：`{subject_name}-内部账户`
     - 内部账户的 `balance_direction` 继承科目的 `debit_credit`
     - 同时创建两个子账户：
       - 可用子账户：`balance_type=1`（可用余额）
       - 冻结子账户：`balance_type=2`（冻结余额）
     - 子账户的 `balance_direction` 与主账户保持一致
     - 子账户的初始余额均为 `0.00`
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

## 6. 内部账户开户详细流程

### 6.1 触发条件
在创建或更新科目时，满足以下所有条件时触发内部账户开户：
1. `is_leaf == 1`（末级科目）
2. `allow_post == 1`（允许记账）
3. `allow_open_account == 1`（允许建明细账户）

### 6.2 开户步骤
1. **生成账户编号**：
   - 规则：`INNER-{subject_code}-{sequence}`
   - 示例：`INNER-1001-001`
   - 序号生成：可以使用数据库序列或 Redis 自增

2. **创建主账户**（`t_account`）：
   ```
   owner_id: "INNER"
   owner_type: 99 (其他)
   account_no: "INNER-{subject_code}-{sequence}"
   account_name: "{subject_name}-内部账户"
   account_type: "INNER" (内部账户类型，需在字典中定义)
   subject_code: {当前科目编码}
   currency: "CNY"
   balance_direction: {继承科目的 debit_credit}
   opening_balance: 0.00
   balance: 0.00
   status: 1 (正常)
   risk_status: 1 (正常)
   open_date: {当前日期}
   ```

3. **创建可用子账户**（`t_sub_account`）：
   ```
   account_no: {主账户的 account_no}
   balance_type: 1 (可用余额)
   balance_direction: {继承主账户的 balance_direction}
   balance: 0.00
   ```

4. **创建冻结子账户**（`t_sub_account`）：
   ```
   account_no: {主账户的 account_no}
   balance_type: 2 (冻结余额)
   balance_direction: {继承主账户的 balance_direction}
   balance: 0.00
   ```

### 6.3 异常处理
- 如果账户编号生成失败，抛出 `ServiceException(ResultCode.SYSTEM_ERROR)`
- 如果账户创建失败，抛出 `AccountException(ResultCode.ACCOUNT_OPEN_FAILED)`
- 如果子账户创建失败，需要回滚主账户创建，保证数据一致性

### 6.4 事务控制
- 科目创建/更新与内部账户开户必须在同一个事务中
- 使用编程式事务 `TransactionTemplate` 确保原子性

## 7. 校验点 (Checkpoints)
- [ ] 分页接口是否均使用了 `ApiResponse<PageResponse<T>>` 的嵌套结构？
- [ ] 科目新增逻辑是否包含完整的内部账户开户流程？
- [ ] 内部账户开户是否同时创建了可用和冻结两个子账户？
- [ ] 内部账户的 `owner_id` 是否设置为 `INNER`？
- [ ] 内部账户的 `balance_direction` 是否正确继承科目的 `debit_credit`？
- [ ] 字典和科目的二级缓存是否实现了变更后的分布式同步清除？
- [ ] 所有的 API 请求（Request）和响应（Response）实体是否均定义在 `accounting-api` 模块？
- [ ] 科目变更保护逻辑是否正确实现（被引用后禁止修改核心属性）？

## 8. 下一步行动
- **Step 9: Account Template & Opening (开户模板与自动开户)**
- **内容**：实现 `AccountTemplate` 配置，并开发基于模板的一键开立多子账户（资产、负债等）的领域服务。
