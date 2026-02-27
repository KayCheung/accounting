# Task 16: Sandbox Trial API Design
实现规则预验证接口（Dry Run）：
1. **接口定义**：`POST /v1/accounting/simulate-post`。
2. **功能描述**：
   - 接收完整的入账报文。
   - 模拟运行 `RuleEngine`，但不执行数据库 `UPDATE` 动作。
3. **返回结果**：
   - 返回模拟生成的 `AccountingVoucher` 预览。
   - 返回该笔交易预计导致的账户余额变动预览。
4. **用途**：用于运营后台在修改入账规则后，通过存量业务数据进行逻辑回归。