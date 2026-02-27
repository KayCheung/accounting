# Step 5. Project Initialization & API Infrastructure (工程初始化与 API 基础设施)

## 1. 任务目标 (Mission)
按照 `docs/design/项目骨架.md` 初始化物理工程，确立 API 模块契约。
1. **主权隔离**：`accounting-api` 严禁引入任何持久层（MyBatis-Plus/JDBC）或数据库驱动，保持纯净性，仅包含 DTO、Facade 和枚举。
2. **API 标准化**：为所有出入参和 Controller 补齐 OpenAPI 3.0 (Swagger) 标注，输出分页基础 DTO。

## 2. 工程骨架与 POM 管理
- **根 POM**: 统一管理所有依赖版本号。
- **API 模块**: 仅允许依赖 `swagger-annotations` (v3), `jackson-annotations`, `jakarta.validation-api`。

## 3. 基础契约定义

### 3.1 统一响应 (response)
- **ResultCode**: 统一定义 SUCCESS, INSUFFICIENT_BALANCE, IDEMPOTENT_CONFLICT, SYSTEM_ERROR 等。
- **ApiResponse<T>**: 包含 `code`, `message`, `data`, `traceId`, `timestamp`。

### 3.2 分页模型 (request/response)
- **PageRequest**: 包含 `pageNo` (default 1), `pageSize` (default 10)。
- **PageResponse<T>**: 包含 `total`, `pages`, `list`, `current`。

### 3.3 核心业务枚举 (enums)
- **EntryDirection**: 借(1, DEBIT), 贷(2, CREDIT)。
- **AccountStatus**: 正常, 冻结, 止付, 销户。
- **枚举规范**：包含 `code` 与 `desc`，标注 `@JsonValue` 用于 JSON 序列化。

## 4. 业务门面接口 (Facade & DTO)
- **Facade**:
    - `VoucherFacade`: 凭证记账、红冲、状态查询。
    - `BalanceFacade`: 实时余额查询、余额快照流水查询。
    - `AccountingFacade`: 账户开户、状态变更。
- **VoucherRequest**: 包含 `@NotBlank` 校验的 `traceNo`, `traceSeq`, 金额 `BigDecimal`, 及 `List<EntryRequest>`。

## 5. 下一步行动
- 完成代码生成后，进入 **Step 6** 搭建 `accounting-core` 基础设施（Nacos 配置加载、Redisson 封装、ONS 客户端初始化）。

## 5. 状态同步说明
- 完成本阶段代码生成后，进入 **Step 6** 搭建 `accounting-core` 核心实现模块的配置环境（Nacos, Redisson, ONS）。