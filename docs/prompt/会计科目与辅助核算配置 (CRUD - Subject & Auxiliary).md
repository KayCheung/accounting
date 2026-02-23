# Task 17: Account Subject Management (CRUD)
请实现会计科目（t_account_subject）及辅助核算配置（t_account_subject_auxiliary）的后端逻辑：
1. **树形结构维护**：
   - 实现科目的增删改查。新增子科目时，自动继承父科目的 `account_class` 和 `subject_path`。
   - 逻辑校验：只有 `is_leaf=1` 的末级科目才允许设置 `allow_post=1`（允许记账）。
2. **辅助核算绑定**：
   - 维护科目与辅助核算类型的关联关系。
   - 校验：若科目已有关联余额或账户，禁止修改其必填的辅助核算项，防止历史数据断裂。
3. **状态流转**：
   - 实现科目的启用/停用。停用前必须校验其下所有账户余额是否为零，且无未过账凭证。