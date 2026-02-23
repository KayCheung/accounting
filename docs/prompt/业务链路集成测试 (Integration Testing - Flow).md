# Task 10: Integration Tests for Posting Flow
请使用 Mockito 和内存数据库（如 H2）编写 `RealTimePostingService` 的集成测试：
1. **全链路入账测试**:
   - 构造 `BusinessRecord` 报文，验证其经过 `RuleEngine` 后生成的凭证分录（`t_voucher_entry_detail`）是否与 DDL 字段一致。
   - 校验分录的 `entry_id` 是否按 `voucher_no + line_no` 唯一生成。
2. **红冲逻辑测试**:
   - 模拟一笔已入账凭证，执行 `StornoService`。
   - 断言新凭证：金额必须为负、`trans_type` 为 '红'、`orig_voucher_no` 正确指向原凭证。
   - 校验红冲后账户的 `balance` 是否精准回到初始水位。
3. **冻结联动测试**:
   - 测试“解冻并扣款”场景：验证 `t_sub_account` 中的 `冻结余额` 减少、`t_account` 余额变动以及凭证生成的原子性。