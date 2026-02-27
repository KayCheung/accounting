# Step 6. Middleware Integration & Infrastructure (中间件集成与基础设施封装)

## 1. 任务目标 (Mission)
本阶段旨在完成基建底座的“多维上下文感知”与“高可靠”封装。
1. **全链路上下文隔离**：通过 `ThreadLocal` 结合 `ContextInterceptor`，实现租户 ID、用户信息在全链路（Rest -> MQ -> DB）的自动传递与鉴权。
2. **中间件增强**：配置 MyBatis-Plus 乐观锁、多租户插件及 Redisson 锁模版。
3. **链路审计**：利用 Skywalking 原生能力追踪流水，并在消息与日志中闭环 traceId。

## 2. 上下文管理与鉴权 (Request Context)

### 2.1 SecurityContextHolder
- **功能**：使用 `ThreadLocal` 存储 `tenantId` 和 `UserInfo`（包含 userId, userName 等）。
- **线程安全性**：提供静态方法进行存取，并确保在请求结束时强制执行 `clear()`。

### 2.2 @RequireUser 注解
- **定位**：标注于 Controller 方法。
- **逻辑**：用于标识该接口必须具备用户信息方可访问，实现财务操作的可审计性。

### 2.3 ContextInterceptor
- **租户解析**：强制从 Header `X-Tenant-Id` 获取租户 ID。若缺失，直接抛出 `PARAM_ERROR`。
- **用户解析**：尝试从 Header `X-User-Info`（Base64 编码的 JSON）解析用户信息并存入上下文。
- **动态鉴权**：检查目标方法是否带有 `@RequireUser`。若有且用户信息为空，抛出 `UNAUTHORIZED(401)`。
- **清理机制**：在 `afterCompletion` 中清理 ThreadLocal，防止线程复用导致的数据污染。

## 3. 持久层增强 (MyBatis-Plus Plug-ins)

### 3.1 MybatisPlusConfig
- **多租户插件 (TenantLineInnerInterceptor)**：
    - 自动拦截 SQL，拼装 `tenant_id = ?` 条件。
    - 需配置忽略表（如公共配置、字典表等）。
- **乐观锁插件 (OptimisticLockerInnerInterceptor)**：
    - 为账户余额、凭证状态等更新提供最后一道并发版本防御。
- **分页插件**：配置 MySQL 类型的分页拦截器。

## 4. 分布式锁底座 (Redisson)

### 4.1 DistributedLockTemplate
- **强制约束**：封装 `execute` 方法，支持 `lockKey`, `waitTime`, `leaseTime`。
- **自动隔离**：锁 Key 自动拼接 `tenantId` 前缀，实现租户间的物理冲突隔离。
- **异常处理**：加锁失败必须抛出 `ServiceException(ResultCode.IDEMPOTENT_CONFLICT)`。

## 5. 消息底座 (Aliyun ONS)

### 5.1 OnsProducerTemplate
- **元数据透传**：在 `Message` 的 `UserProperties` 中强制注入 `tenantId` 和 `userId`。
- **链路审计**：利用 Skywalking 自动注入 traceId 的特性（已安装插件），在日志中闭环上下文。
- **可靠性**：配合本地消息表，在发送失败时记录日志，不阻塞主业务事务。

## 6. 工具类接入
- **Skywalking SDK**：直接调用 `org.apache.skywalking.apm.toolkit.trace.TraceContext.traceId()`，严禁自研。

## 7. 下一步行动
- 完成基建封装后，进入 **Phase 3 (Domain Modeling)**。
- **Step 7**: 定义 `Account` (账户)、`SubAccount` (分账)、`BusinessRecord` (业务流水) 的领域模型及 DDL 脚本。