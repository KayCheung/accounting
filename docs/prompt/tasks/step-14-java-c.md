# step-14-java-c · FreezeApplicationService + FreezeController + DTO

> **Step 14 子任务** | 归属：`@Java` 工程师-C
> 前置依赖：Step 14 Java-A（领域服务已就绪）+ Java-B（金额校验已就绪）

---

## 1. 任务目标

实现资金冻结应用服务、Controller、DTO、Assembler。

核心职责：
1. **FreezeApplicationService**：用例编排（5 个接口：冻结/解冻/扣款/查询/列表）
2. **FreezeController**：5 个 REST 接口
3. **DTO**：3 个 Request + 1 个 Response
4. **FreezeAssembler**：PO ↔ DTO 转换

---

## 2. DTO 定义

### FundFreezeRequest
- `accountNo`：必填，长度 1-32
- `freezeAmount`：必填，> 0，精度 DECIMAL(18,6)
- `expireTime`：选填，默认 2099-12-31
- `reason`：选填，长度 1-128

### FundUnfreezeRequest
- `freezeId`：必填（对应 t_account_freeze_detail.voucher_no）
- `unfreezeAmount`：必填，> 0
- `reason`：选填

### FreezeDeductRequest
- `freezeId`：必填
- `deductAmount`：必填，> 0
- `reason`：选填

### FreezeDetailResponse
- `freezeId` / `accountNo` / `freezeAmount` / `status` / `statusDesc`
- `expireTime` / `tradeTime` / `createTime` / `summary`

---

## 3. FreezeAssembler

`toDetailResponse(AccountFreezeDetailPO)` → FreezeDetailResponse
- `accountNo` 从 `businessCode` 映射（DDL 无 account_no 字段）

---

## 4. FreezeApplicationService

纯编排层，不包含业务逻辑：
- `freezeFund(FundFreezeRequest)` → 委托 DomainService → DTO 转换
- `unfreezeFund(FundUnfreezeRequest)` → 委托 DomainService
- `deductFromFreeze(FreezeDeductRequest)` → 委托 DomainService
- `queryFreezeRecord(String)` → 委托 DomainService → DTO 转换
- `queryFreezeRecords(String, Integer)` → 委托 DomainService → DTO 列表转换

---

## 5. FreezeController

| Method | Path | 说明 |
|--------|------|------|
| POST | `/accounting/account/freeze/fund` | 资金冻结 |
| POST | `/accounting/account/freeze/unfreeze` | 资金解冻 |
| POST | `/accounting/account/freeze/deduct` | 冻结扣款 |
| GET | `/accounting/account/freeze/{freezeId}` | 查询冻结记录 |
| GET | `/accounting/account/freeze/list` | 冻结记录列表 |

---

## 6. 完成标准（Checklist）

- [X] `FreezeApplicationService` 编排 5 个用例（无业务逻辑，仅参数校验 + 委托 + DTO 转换）
- [X] `FreezeController` 实现 5 个接口
- [X] `FundFreezeRequest` / `FundUnfreezeRequest` / `FreezeDeductRequest` DTO
- [X] `FreezeDetailResponse` DTO
- [X] 所有 DTO 使用 `jakarta.validation` 注解校验
- [X] `FreezeAssembler` 完成 PO ↔ DTO 转换
- [X] `accountNo` 从 `businessCode` 映射（DDL 限制 workaround）
- [X] Swagger/OpenAPI 注解完整（@Tag / @Operation）
- [X] 编译通过（mvn compile -DskipTests SUCCESS）
