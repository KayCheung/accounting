# Task 9: Unit Tests for Core Components
请为 `BalanceCalculator` 和 `AccountingRuleEvaluator` 编写 JUnit 5 测试用例：
1. **BalanceCalculatorTest**:
   - 覆盖所有 `AccountClass`（资产、负债、权益等）。
   - 测试“借/贷”方向与“增/减”动作的所有组合，验证结果是否符合会计恒等式。
   - 测试 **边界值**：余额为 0 时的扣减、大额 `BigDecimal` 运算（18位精度）。
2. **SpEL Sandbox Test**:
   - 编写恶意脚本测试：尝试调用 `java.lang.Runtime`, `System.exit`, 或通过反射访问私有字段，验证沙箱是否能正确拦截并抛出安全异常。
   - 验证白名单功能：确保 `BigDecimal` 的常用运算和预设工具类能正常执行。