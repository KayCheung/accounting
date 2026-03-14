# 示例：幂等入账流程（Idempotent Posting）

## 场景说明

标准实时记账入口，展示完整的**幂等 + 分布式锁 + 编程式事务**链路。

---

## 完整链路伪代码

```java
public ApiResponse<PostingResponse> post(PostingRequest request) {
    // 1. 入口幂等锁（含 tenantId 前缀，实现租户隔离）
    return lockTemplate.execute(
        "accounting:" + tenantId + ":lock:idempotent:trace:" + request.getTraceNo(),
        () -> doPost(request)
    );
}

private PostingResponse doPost(PostingRequest request) {
    // 2. 幂等查询（trace_no + trace_seq）
    Optional<BusinessRecord> existing =
        businessRecordRepo.findByTraceNoAndTraceSeq(request.getTraceNo(), request.getTraceSeq());
    if (existing.isPresent()) {
        return buildIdempotentResponse(existing.get()); // 幂等返回，不重复记账
    }

    // 3. 确定会计日期 + 持久化业务流水（会计日期在此确定，全链路不可变更）
    LocalDate accountingDate = accountingDateService.currentDate();
    businessRecordRepo.save(buildRecord(request, accountingDate));

    // 4. 规则匹配 → 凭证生成 → 借贷平衡校验（不平衡则抛异常，不写库）
    AccountingRule rule = ruleService.match(request);
    AccountingVoucher voucher = voucherService.generate(request, rule, accountingDate);
    voucherService.validateBalance(voucher);

    // 5. 缓冲路径分流
    Optional<BufferRule> bufferRule = bufferRuleService.match(rule);
    if (bufferRule.isPresent()) {
        return handleBufferedPosting(voucher, bufferRule.get());
    }

    // 6. 引擎执行锁（防异步重试与实时路径竞态）
    return lockTemplate.execute(
        "accounting:" + tenantId + ":lock:posting:trx:" + voucher.getVoucherNo(),
        () -> executePosting(voucher)
    );
}

private PostingResponse executePosting(AccountingVoucher voucher) {
    Transaction txn = transactionRepo.createProcessing(voucher);
    voucher.setTxnNo(txn.getTxnNo()); // 回填 txnNo

    return transactionTemplate.execute(status -> {
        try {
            // 7. 按 account_no 升序加悲观锁（防死锁）
            List<String> sortedNos = voucher.getEntries().stream()
                    .filter(e -> e.getIsUnilateral() == 1)
                    .map(VoucherEntry::getAccountNo)
                    .distinct().sorted().collect(Collectors.toList());
            List<Account> locked = accountRepo.selectForUpdate(sortedNos);

            // 8. 逐条分录执行余额更新
            for (VoucherEntry entry : voucher.getEntries()) {
                if (entry.getIsUnilateral() == 0) {
                    localMessageService.sendAsync(entry); // 异步分录走 MQ
                    continue;
                }
                Account account = locked.stream()
                        .filter(a -> a.getAccountNo().equals(entry.getAccountNo()))
                        .findFirst()
                        .orElseThrow(() -> new AccountException(ResultCode.ACCOUNT_NOT_FOUND));

                BigDecimal newBalance = calculateNewBalance(account, entry);

                // 写明细（Pre/Post 快照）→ 更新余额（绝对值写入）→ 更新子账户
                accountDetailRepo.save(buildDetail(account, entry, newBalance));
                account.setBalance(newBalance);
                accountRepo.updateById(account);
                subAccountService.updateBalance(entry, newBalance);
            }

            // 9. 更新凭证状态 → POSTED(3)，事务状态 → SUCCESS
            voucherRepo.markPosted(voucher.getVoucherNo());
            transactionRepo.markSuccess(txn.getTxnNo());
            return buildSuccessResponse(voucher);

        } catch (Exception e) {
            status.setRollbackOnly();
            transactionRepo.markFailed(txn.getTxnNo(), e.getMessage());
            log.error("[记账失败] voucherNo={}, traceNo={}, error={}",
                    voucher.getVoucherNo(), voucher.getTraceNo(), e.getMessage(), e);
            throw new ServiceException(ResultCode.POSTING_FAILED, e.getMessage());
        }
    });
}
```

---

## 关键状态机

```
BusinessRecord:  INIT → PROCESSING → SUCCESS / FAILED
Transaction:     PROCESSING → SUCCESS / FAILED
Voucher:         PENDING(1) → POSTING(2) → POSTED(3) / FAILED(4)
VoucherEntry:    PENDING(1) → POSTED(2)
LocalMessage:    PENDING(1) → SENDING(2) → SENT(3) / FAILED(4)
```
