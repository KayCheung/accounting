### 变更说明 (Description)
<!-- 请简要说明本 PR 解决的问题或引入的新功能 -->

### 关联 Issue (Related Issue)
- Fixes #

### 变更类型 (Change Type)
- [ ] 💡 新特性 (New Feature)
- [ ] 🐛 Bug 修复 (Bug Fix)
- [ ] 📝 文档更新 (Documentation Update)
- [ ] 🚀 性能优化 (Performance Improvement)
- [ ] ♻️ 代码重构 (Code Refactoring)
- [ ] 🧪 单元测试 / 集成测试 (Unit/Integration Test)

---

### ⚠️ 核心财务铁律自检清单 (Financial Tenets Checklist)
本系统为底层资金与账务核心，必须严格满足以下准则（未满足一律不予合并）：
- [ ] **绝对值法则**：凭证分录金额、交易流水明细金额必须严格大于 0（`BigDecimal > 0`），无负数运算。
- [ ] **严禁在 SQL 中计算余额**：禁止使用 `SET balance = balance - #{amount}`，余额由领域模型计算并采用乐观锁（`version`）更新。
- [ ] **事务边界**：未使用 `@Transactional`，严格采用 `TransactionTemplate` 精确控制事务。
- [ ] **多账户加锁顺序**：涉及多账户加锁时，已按 `account_no` 升序获取 Redisson 分布式锁，避免死锁。
- [ ] **金额精度**：金额均使用 `BigDecimal` 并以 String 构造，金额比较使用 `compareTo()` 而非 `equals()`。
- [ ] **凭证不可变性**：未破坏“先证后账”与“红冲调账”原则。

---

### 验证与测试 (Verification & Testing)
- [ ] 本地全量单元测试已通过（`mvn clean test`）
- [ ] 若涉及前端，`npm run type-check` 与 `npm run build` 打包无报错
- [ ] 附上自测日志或相关截图（如有）：
