# Task 11: Concurrency and Idempotency Tests
1. **并发更新测试**:
   - 使用 `CountDownLatch` 或 `CompletableFuture` 模拟 50 个线程同时对同一个热点 `t_account` 进行加减操作。
   - 验证在悲观锁 `FOR UPDATE` 作用下，最终余额是否等于 $初始余额 + \sum 发生额$，且没有任何一笔更新丢失。
2. **幂等性压力测试**:
   - 模拟网络超时导致的重复请求：使用相同的 `trace_no` 发起 10 次记账请求。
   - 验证系统是否只产生了一笔 `t_business_record` 和一组会计凭证，后续请求是否返回一致的成功结果。