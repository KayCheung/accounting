# Step 1: Enums Definition
请根据 DDL 生成以下核心枚举类：

1. **核心枚举生成**：
   - 根据 DDL 表结构中的字段及描述生成对应的枚举类。
   - 重点包括：`SubjectCategory` (subject_category), `SubjectNature` (nature), `DebitCredit` (debit_credit), `OwnerType` (owner_type), `ImpactType` (impact_type), `SubAccountOp` (sub_account_op), `BalanceType` (balance_type), `TradeType` (trade_type) 等。
2. **通用枚举**：
   - 定义通用状态枚举 `AvailableStatus`：1-待启用, 2-启用, 3-停用。
3. **技术要求**：
   - **字段结构**：包含 `Integer code`（或 `String code`）和 `String desc`。
   - **类型映射**：凡是 DDL 中定义为 TINYINT 或 INT 的字段，枚举 Code 对应 `Integer`；VARCHAR 对应 `String`。
   - **注解支持**：
      - 使用 Lombok 的 `@Getter` 和 `@AllArgsConstructor`。
      - 枚举类的 `code` 字段必须标注 MyBatis-Plus 的 `@EnumValue` 注解。
   - **逻辑增强**：提供一个静态方法 `ofCode(code)`，用于根据 code 返回对应的枚举对象。



```
AccountSubject
   
subject_category: 
	账类：1-资产类,2-负债类,3-权益类,4-共同类,5-成本类,6-损益类,0-表外科目
nature:
    科目性质：1-非特殊性科目,2-销账类科目,3-贷款类科目,4-现金类科目
debit_credit:
	借贷方向：1-借；2-贷
status：
	状态：1-待启用；2-启用，3-停用
    可以抽象一个通用的 AvailableStatus (EABLE, DISABLE)
customer_type：
    客户类型：1-个人,2-企业,99-其他
owner_type：
    所有者类型：1-个人,2-企业,99-其他
t_account -> status：
    账户状态：1-正常,2-冻结,3-注销
balance_type：
    余额类型：1-可用余额,2-冻结余额
trade_type：
    交易类别：1-正常,2-调账,3-红,4-蓝
t_transaction.status：
	事务状态：1-未提交,2-部分提交,3-全部提交,4-部分回滚,5-全部回滚,6-失败
accounting_mode：
	记账模式：1-实时,2-异步
allocation_method：
	分摊方式：1-不分摊,2-固定金额,3-按比例
posting_type：
	入账类型：1-手工凭证,2-机制凭证
t_accounting_voucher.status：
	凭证状态：1-未过账,2-过账中,3-已过账,4-过账失败
t_accounting_voucher_entry.status：
	凭证状态：1-未过账,2-已过账
change_direction：
	增减方向：1-增,2-减（后续做数据分析时能明确知道是支出还是收入）
t_buffer_posting_detail.status
	缓冲入账状态：1-待入账,2-入账处理中,3-入账成功,4-入账失败
buffer_mode：
    缓冲入账模式：1-异步逐条入账,2-日间批量入账,3-日终批量汇总入账
t_account_freeze_detail.status：
	状态：1-冻结,2-已解冻
impact_type：
   影响类型：1-总额变动,2-内部划转/冻结
sub_account_op：
   子账户操作路径：1-可用变动, 2-冻结变动, 3-可用转冻结, 4-冻结转可用
```