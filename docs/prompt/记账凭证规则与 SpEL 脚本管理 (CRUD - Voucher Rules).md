# Task 19: Voucher Rule & Script Management (CRUD)
实现记账规则（t_voucher_rule）及其明细（t_voucher_rule_detail）的深度管理：
1. **复合编辑**：
   - 支持凭证规则头与多行明细的原子性保存（Transaction 保护）。
   - 自动维护 `line_no` 行号。
2. **脚本安全校验**：
   - 在保存 `rule_script` 时，调用之前定义的 `SpEL Sandbox` 进行预编译检查，确保脚本无语法错误且不包含非法关键字。
3. **版本化思路**：
   - 修改已启用的规则时，提示用户进行“模拟试算（Dry Run）”。
   - 记录规则变更日志，确保每一版入账逻辑可回溯。