# Step 2: Domain Entities & Services
请根据 DDL 以及 Step 1 生成的枚举，生成实体类、Mapper 接口及 Service 类：

1. **基础父类 (BaseEntity)**：
    - 创建一个 `BaseEntity` 类，包含公共审计字段：`createTime`, `updateTime`, `createId`, `createName`, `updateId`, `updateName`, `isDelete`, `tenantId`。
2. **业务实体类 (Entities)**：
    - 根据 DDL 生成所有实体类（如 `Account`, `SubAccount`, `AccountingVoucher` 等），并继承 `BaseEntity`。
3. **技术要求**：
    - **框架标准**：使用 Jakarta Persistence 注解（非 javax），使用 MyBatis-Plus 相关注解。
    - **特殊处理**：
        - ID 生成策略：所有的主键基于MySQL 自增长，请统一使用 `IdType.AUTO`，建议将该字段定义到 `BaseEntity`
        - 乐观锁：`version` 字段标注 `@Version`。
        - 逻辑删除：`isDelete` 字段标注 `@TableLogic`，统一使用 `java.lang.Long`。
        - 租户ID：`tenantId` 统一使用 `java.lang.Integer`。
        - 用户ID：`createId` 和 `updateId` 统一使用 `java.lang.String`。
        - 时间类型：统一使用 `java.time.LocalDateTime`。
        - 金额类型：统一使用 `java.math.BigDecimal`，并注释说明 `DECIMAL(18,6)` 精度。
        - 枚举字段：枚举字段统一使用 Step 1 定义的枚举。
        - 类型对齐：
            - bigint > Long
            - int/tinyint > Integer
            - varchar/text > String
            - decimal > BigDecimal
    - **增强逻辑**：
        - `AccountingVoucherEntry`：增加 `generateEntryId()` 方法（逻辑：凭证号 + 行号）。
        - `SubAccount`：增加 `isFrozenAccount()` 逻辑判断方法。
        - `AccountSubject`：增加 `List<AccountSubject> children` 字段（标注 `@TableField(exist = false)`）。
    - **关联字段**：若表之间存在业务关联，可在实体类中增加关联实体字段，使用 `@TableField(exist = false)` 标识。
4. **Mapper 层**：
    - 为每个实体生成对应的 Mapper 接口，继承 MyBatis-Plus 的 `BaseMapper<T>`。
5. **Service 层（轻量化模式）**：
    - **不创建接口**：Service 类直接继承 MyBatis-Plus 的 `ServiceImpl<Mapper, Entity>`。
    - **命名规范**：类名直接命名为 `AccountService`、`SubAccountService` 等（无需 Impl 后缀）。
    - 每个 Service 需标注 Spring 的 `@Service` 注解。
   
   