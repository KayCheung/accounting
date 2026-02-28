# Step 7. Domain Modeling & Persistence Layer (全量持久层映射)

## 1. 任务目标 (Mission)
基于 `1-init-schema.sql` 全量 DDL，在 DDD 架构下完成基础设施层（Infrastructure）的物理映射。
1. **PO 全量化**：映射 DDL 中定义的全部 25 张物理表，确保字段类型、精度及主键策略 100% 匹配。
2. **BaseEntity 约束**：定义全局实体基类，落实自增主键与时间戳逻辑删除方案。
3. **枚举全量化**：提取所有业务状态字段，转化为领域枚举。
4. **仓储架构闭环**：确立 Mapper、Converter 与基于 ServiceImpl 的 Repository 实现规范。

## 2. 核心基类规范 (BaseEntity)
> 存放路径：`infrastructure/persistence/entity/BaseEntity.java`

- **主键策略**：统一使用自增 ID (`IdType.AUTO`)。
- **逻辑删除**：配置为 `0-正常`，`非0-删除时间戳`（利用 `@TableLogic` 的 `delval` 特性解决唯一索引冲突）。
- **通用字段**：包含 `id`, `tenantId`, `isDelete`, `createTime`, `updateTime`。
- **自动填充**：`createTime` 与 `updateTime` 需配合 MyBatis-Plus 公共字段填充器处理。

## 3. 全量枚举定义 (Domain Enums)
> 存放路径：`domain/enums/`
> 规范：仅标注 `@EnumValue`。

- **通用状态**: `AvailableStatus` (1-待启用, 2-启用, 3-停用)。
- **科目类**: `SubjectCategoryEnum` (0-6类), `NatureEnum` (1-4类), `DebitCreditEnum` (1-借, 2-贷), `BalanceDirectionEnum` (1-借, 2-贷)。
- **账户类**: `AccountStatusEnum` (1-正常, 2-冻结, 3-注销), `BalanceTypeEnum` (1-可用, 2-冻结)。
- **规则类**: `VoucherTypeEnum`, `AccountingModeEnum` (1-实时, 2-异步)。
- **凭证类**: `VoucherStatusEnum` (1-未过账, 2-过账中, 3-已过账, 4-失败)。
- **缓冲类**: `BufferStatusEnum` (1-待处理, 2-处理中, 3-成功, 4-失败)。
- **消息类**: `MessageStatusEnum` (1-待发送, 2-发送中, 3-已发送, 4-失败, 5-失效), `ReceiptStatusEnum` (1-成功, 2-失败)。

## 4. 全量持久化实体映射表 (Infrastructure POs)
> 存放路径：`infrastructure/persistence/entity/`
> **设计规约：全量 25 张表映射，所有 PO 必须继承 `BaseEntity`。**

## 5. 基础设施层组件规范
### 5.1 Mapper 路径分离
- **Java 接口**：`infrastructure/persistence/mapper/`
- **XML 映射**：`src/main/resources/mapper/`
- **查询要求**：必须默认过滤 `is_delete`，`AccountMapper` 需针对余额更新实现乐观锁 SQL。

### 5.2 对象转换器 (Converter)
- **存放路径**：`infrastructure/persistence/converter/`
- **职责**：定义 PO 与 Domain Entity 的映射契约，处理枚举对象与数据库数值的类型转换。

### 5.3 仓储实现 (Repository)
- **存放路径**：`infrastructure/persistence/repository/`
- **实现方案**：继承 MyBatis-Plus 的 `ServiceImpl` 并实现领域层 Repository 接口。
- **使用原则**：优先复用 `ServiceImpl` 内置方法；涉及转换逻辑或多表聚合时再自定义实现。

## 5. 辅助工具定义 (Shared Utility)
- **存放路径**：`accounting-core` -> `src/main/java/com/kltb/accounting/core/shared/util/`
- **TreeNode<T> 接口**：定义 `getId`, `getParentId` 等契约，`AccountSubject` 需实现此接口。
- **TreeUtil 契约**：提供泛型树构建逻辑（需在构建前确保数据已过滤逻辑删除），用于 Step 8 的科目树维护。

## 6. 校验点 (Checkpoints)
- [ ] 实体类数量是否与 DDL 表数量完全一致（约 25 个）？
- [ ] 所有 `TINYINT` 字段是否均有对应的领域枚举映射，并且被实体类引用？
- [ ] `BaseEntity` 的逻辑删除是否正确配置为时间戳？
- [ ] Repository 是否正确继承 `ServiceImpl` 并引用了对应的 Mapper？

## 7. 下一步行动
- **Step 8: 配置管理模块 (Configuration Module)**
- **内容**：实现科目树、开户模板、记账规则、缓冲策略的全量 CRUD 维护。