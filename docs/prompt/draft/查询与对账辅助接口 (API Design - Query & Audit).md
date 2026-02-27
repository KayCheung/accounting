# Task 15: Query and Audit API Design
设计多维度的账务查询接口：
1. **余额查询**：支持按 `account_no` 查询当前可用、冻结及总余额，并实时返回。
2. **明细流水查询**：`GET /v1/account/details`。
   - 支持分页，支持按 `accounting_date` 范围、`trans_code`、`dr_cr_flag` 进行过滤。
3. **凭证追溯**：通过 `trace_no` 或 `voucher_no` 反查完整的会计分录（包含辅助核算项明细）。
4. **性能要求**：
   - 账户明细查询必须利用 `idx_account_no` 索引，严禁全表扫描。