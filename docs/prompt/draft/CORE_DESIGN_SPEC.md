# 账务核心系统设计准则 (Core Design Spec)

## 1. 核心记账逻辑
- **计算模式**：严禁在 SQL 中使用 `balance = balance + ?`。所有余额变更必须在 Java 应用层计算后，使用绝对值覆盖更新（Update by Value）。
- **借贷平衡算法**：采用“同向相加、异向相减”。
  - 资产类/成本类/损益支出类：借方（+），贷方（-）。
  - 负债类/权益类/损益收入类：贷方（+），借方（-）。
- **并发控制**：
  - 实时记账：使用 `SELECT ... FOR UPDATE` 悲观锁锁定 `t_account`。
  - 缓冲记账：使用 `version` 乐观锁更新。

## 2. 幂等与防重
- **业务幂等**：利用 `t_business_record` 的 `uk_trace_no`。
- **分录幂等**：利用 `t_voucher_entry_detail` 的 `uk_entry_id` (由 `voucher_no` + `line_no` 拼接)。

## 3. 冲正逻辑 (Red Storno)
- **镜像红冲**：不采用借贷方向对调，而是采用“方向不变，金额取负”（Original Amount * -1）的原则生成红冲凭证。

## 4. 子账户逻辑
- 一个 `t_account` 对应两个 `t_sub_account`：可用余额（AVAILABLE）和冻结余额（FROZEN）。
- 冻结操作仅在子账户间平移额度，不触发总账分录；解冻扣款则同步触发子账户额度平移与总账凭证生成。