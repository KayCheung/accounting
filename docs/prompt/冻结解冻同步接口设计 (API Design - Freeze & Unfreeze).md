# Task 14: Freeze and Unfreeze API Design
实现同步的余额冻结与解冻接口：
1. **冻结接口**：`POST /v1/account/freeze`。
   - 参数：`account_no`, `amount`, `biz_type`, `trace_no` (用于后续解冻关联)。
   - 逻辑：通过悲观锁锁定子账户，平移“可用”至“冻结”。
2. **解冻接口**：`POST /v1/account/unfreeze`。
   - 参数：`orig_trace_no` (指向原冻结流水), `amount` (支持部分解冻)。
   - 逻辑：支持“解冻并退回可用”与“解冻并转记账（扣款）”两种模式。
3. **安全校验**：
   - 严格校验冻结余额是否足够，防止冻结余额出现负数。