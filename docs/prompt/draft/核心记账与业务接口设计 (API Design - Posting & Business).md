# Task 13: Standard Posting API Design
请基于 RESTful 风格设计账务核心入账接口：
1. **接口定义**：`POST /v1/accounting/post`。
2. **请求参数**：
   - 包含 `trace_no` (唯一跟踪号), `trans_code` (交易编码), `amount` (总金额), `trans_time`。
   - 包含 `details` 列表，支持多明细录入（对应 `t_business_detail`）。
3. **幂等逻辑**：
   - 接口必须实现前置幂等检查。若 `trace_no` 已存在，需根据 `t_business_record` 的状态返回原始处理结果，严禁重复记账。
4. **响应结构**：
   - 返回统一的 `Result<T>` 对象，包含 `code`, `message`, 以及生成的 `voucher_no`。
5. **异常处理**：
   - 针对“余额不足”、“账户状态异常（止入/止出）”、“规则不匹配”定义明确的业务错误码。