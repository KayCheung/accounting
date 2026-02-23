# Task 18: Account Template & Auto-Opening (CRUD)
实现开户模板（t_account_template）管理及联动开户逻辑：
1. **模板维护**：
   - CRUD 操作，支持按 `business_code` 和 `customer_type` 筛选模板。
   - 属性包括：关联科目、币种、余额方向、账号生成规则（account_rule）。
2. **联动逻辑**：
   - 当模板 `auto_open=1` 时，编写一个 `AccountFactory` 组件。
   - 逻辑：根据模板配置，自动为新客户创建一条 `t_account` 记录，并同步初始化两条 `t_sub_account`（可用余额与冻结余额）。
3. **校验**：
   - 一个业务线（business_code）下同一客户类型的同一账户类型，模板必须唯一。