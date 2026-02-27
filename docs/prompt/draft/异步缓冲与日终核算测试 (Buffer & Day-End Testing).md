\# Task 12: Tests for Async Buffer and Day-End Roll

1\. \*\*缓冲入账 Job 测试\*\*:

&nbsp;  - 往 `t\_buffer\_posting\_detail` 写入多笔待处理记录。

&nbsp;  - 启动 `BufferPostingJob`，验证其是否能按账户正确汇总金额并批量更新余额。

&nbsp;  - 模拟更新失败（如乐观锁版本冲突），验证重试机制和错误日志记录。

2\. \*\*日终平衡核算测试\*\*:

&nbsp;  - 在 `t\_voucher\_entry\_detail` 中制造一笔人为的“借贷不平”数据。

&nbsp;  - 运行 `DayEndReconciler`，验证系统是否能精准识别出差额并成功触发告警。

&nbsp;  - 验证日切后 `t\_account\_balance` 生成的数据是否与当日所有明细账累加值相等。

