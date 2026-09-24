package com.kltb.accounting.core.application.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.api.request.PostingExecuteRequest;
import com.kltb.accounting.api.response.PostingExecuteResponse;
import com.kltb.accounting.core.application.assembler.PostingAssembler;
import com.kltb.accounting.core.domain.enums.AccountStatusEnum;
import com.kltb.accounting.core.domain.enums.TransactionStatusEnum;
import com.kltb.accounting.core.domain.enums.VoucherEntryStatusEnum;
import com.kltb.accounting.core.domain.enums.VoucherStatusEnum;
import com.kltb.accounting.core.domain.service.AsyncPostingDomainService;
import com.kltb.accounting.core.domain.service.PostingDomainService;
import com.kltb.accounting.core.domain.service.RollbackDomainService;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherEntryPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.TransactionPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingVoucherRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.TransactionRepository;
import com.kltb.accounting.core.infrastructure.redis.DistributedLockTemplate;
import com.kltb.accounting.core.shared.exception.AccountException;
import com.kltb.accounting.core.shared.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 过账编排应用服务
 * <p>
 * 核心职责：统一事务边界 + 分布式锁 + 分录分流 + 状态联动 + 重试机制
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostingApplicationService {

    private final PostingDomainService postingDomainService;
    private final AsyncPostingDomainService asyncPostingDomainService;
    private final RollbackDomainService rollbackDomainService;
    private final AccountingVoucherRepository accountingVoucherRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionTemplate transactionTemplate;
    private final DistributedLockTemplate distributedLockTemplate;
    private final PostingAssembler assembler;

    private static final int MAX_RETRY = 3;
    private static final long[] RETRY_DELAYS = {10_000, 30_000, 60_000}; // 10s, 30s, 60s

    /**
     * 执行过账（含重试机制）
     * <p>
     * P1-3 修复：重试期间不标记 FAILED，只在最终失败时才调用 markTransactionFailed
     */
    public PostingExecuteResponse executePosting(PostingExecuteRequest request) {
        int retryCount = 0;
        while (true) {
            try {
                return doExecutePosting(request);
            } catch (Exception e) {
                // 先查询凭证获取 txnNo / traceNo（重试前就resolve，避免重复查询）
                AccountingVoucherPO voucher = accountingVoucherRepository.selectByVoucherNoSimple(request.getVoucherNo());
                String txnNo = Optional.ofNullable(voucher).map(AccountingVoucherPO::getTxnNo).orElse(null);
                String traceNo = Optional.ofNullable(voucher).map(AccountingVoucherPO::getTraceNo).orElse(null);

                if (retryCount >= MAX_RETRY || !rollbackDomainService.isRetryable(e)) {
                    // 最终失败：标记 FAILED 状态
                    rollbackDomainService.markTransactionFailed(
                        txnNo, request.getVoucherNo(), traceNo, e.getMessage());
                    throw e;
                }
                // 可重试异常：不标记 FAILED，等待后重试
                try {
                    Thread.sleep(RETRY_DELAYS[retryCount]);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new AccountException(ResultCode.SYSTEM_ERROR, "重试被中断", ie);
                }
                retryCount++;
                log.warn("过账执行重试: attempt={}/{} voucherNo={}",
                    retryCount, MAX_RETRY, request.getVoucherNo());
            }
        }
    }

    /**
     * 过账执行核心逻辑（内部方法）
     */
    private PostingExecuteResponse doExecutePosting(PostingExecuteRequest request) {
        String voucherNo = request.getVoucherNo();

        // 1. 查询凭证
        AccountingVoucherPO voucher = accountingVoucherRepository.selectByVoucherNoSimple(voucherNo);
        if (voucher == null) {
            throw new ServiceException(ResultCode.VOUCHER_NOT_FOUND, "凭证不存在: " + voucherNo);
        }
        if (voucher.getStatus() != VoucherStatusEnum.PENDING) {
            throw new ServiceException(ResultCode.VOUCHER_STATUS_ILLEGAL,
                "凭证状态非法，仅允许未过账凭证执行过账: " + voucherNo
                    + ", 当前状态=" + (voucher.getStatus() != null ? voucher.getStatus().getDesc() : "null"));
        }

        // 2. 查询所有非缓冲分录
        List<AccountingVoucherEntryPO> allEntries = accountingVoucherRepository
            .selectEntriesByVoucherNo(voucherNo)
            .stream()
            .filter(e -> e.getBuffered() == null || e.getBuffered() != 1)
            .collect(Collectors.toList());

        // 3. 账户状态检查：收集所有非缓冲分录涉及的账户
        List<String> accountNos = allEntries.stream()
            .map(AccountingVoucherEntryPO::getAccountNo)
            .distinct()
            .sorted()
            .collect(Collectors.toList());

        for (String accountNo : accountNos) {
            AccountPO account = accountRepository.selectByAccountNo(accountNo);
            if (account == null) {
                throw new AccountException(ResultCode.ACCOUNT_NOT_FOUND,
                    "账户不存在: accountNo=" + accountNo);
            }
            if (account.getStatus() == AccountStatusEnum.FROZEN) {
                throw new AccountException(ResultCode.ACCOUNT_FROZEN,
                    "账户已冻结: accountNo=" + accountNo);
            }
            if (account.getStatus() == AccountStatusEnum.CANCELLED) {
                throw new AccountException(ResultCode.ACCOUNT_CANCELLED,
                    "账户已注销: accountNo=" + accountNo);
            }
        }

        // 4. 分布式锁
        String lockKey = "posting:trx:" + voucherNo;

        return distributedLockTemplate.execute(lockKey, 0, 60, () -> {
            // 5. 在事务中执行过账
            return transactionTemplate.execute(status -> {
                // a. 更新凭证状态为过账中
                voucher.setStatus(VoucherStatusEnum.POSTING);
                accountingVoucherRepository.updateById(voucher);

                // b. 分录分流
                List<AccountingVoucherEntryPO> realTimeEntries = new ArrayList<>();
                List<AccountingVoucherEntryPO> asyncEntries = new ArrayList<>();

                for (AccountingVoucherEntryPO entry : allEntries) {
                    if (entry.getUnilateral() != null && entry.getUnilateral() == 1) {
                        // 实时分录
                        realTimeEntries.add(entry);
                    } else if (entry.getBuffered() == null || entry.getBuffered() != 1) {
                        // 异步分录（非缓冲）
                        asyncEntries.add(entry);
                    }
                    // 缓冲分录跳过
                }

                // c. 实时过账（按 account_no 升序排列）
                realTimeEntries.sort(Comparator.comparing(AccountingVoucherEntryPO::getAccountNo));
                if (!realTimeEntries.isEmpty()) {
                    postingDomainService.executeRealTimePosting(
                        realTimeEntries, voucher.getAccountingDate());
                }

                // d. 异步过账：写入本地消息
                for (AccountingVoucherEntryPO entry : asyncEntries) {
                    asyncPostingDomainService.writeAsyncPostingMessage(entry, voucher);
                }

                // e. 状态联动
                boolean hasAsync = !asyncEntries.isEmpty();
                if (!hasAsync) {
                    // 无异步分录：凭证状态=已过账
                    voucher.setStatus(VoucherStatusEnum.POSTED);
                    accountingVoucherRepository.updateById(voucher);
                }
                // 有异步分录：凭证状态保持 2(过账中)，等待 MQ 消费完成后联动更新

                // f. 事务状态更新
                if (voucher.getTxnNo() != null) {
                    TransactionPO txn = transactionRepository.selectByTxnNo(voucher.getTxnNo());
                    if (txn != null) {
                        // 回填 relateAccountCount
                        txn.setRelateAccountCount(accountNos.size());

                        if (!hasAsync) {
                            // 无异步分录：事务状态=成功
                            transactionRepository.updateStatusByTxnNo(
                                voucher.getTxnNo(),
                                TransactionStatusEnum.SUCCESS,
                                null,
                                LocalDateTime.now()
                            );
                        }
                        // 有异步分录：事务状态保持 1(处理中)
                    }
                }

                // g. 构建响应
                List<AccountingVoucherEntryPO> allNonBufferEntries = new ArrayList<>(realTimeEntries);
                allNonBufferEntries.addAll(asyncEntries);

                TransactionPO txn = voucher.getTxnNo() != null
                    ? transactionRepository.selectByTxnNo(voucher.getTxnNo()) : null;

                return assembler.toResponse(voucher, allNonBufferEntries, txn);
            });
        });
    }

    /**
     * 查询过账状态
     */
    public PostingExecuteResponse getPostingStatus(String voucherNo) {
        AccountingVoucherPO voucher = accountingVoucherRepository.selectByVoucherNo(voucherNo);
        if (voucher == null) {
            throw new ServiceException(ResultCode.VOUCHER_NOT_FOUND, "凭证不存在: " + voucherNo);
        }

        List<AccountingVoucherEntryPO> entries = accountingVoucherRepository
            .selectEntriesByVoucherNo(voucherNo);

        TransactionPO txn = voucher.getTxnNo() != null
            ? transactionRepository.selectByTxnNo(voucher.getTxnNo()) : null;

        return assembler.toResponse(voucher, entries, txn);
    }

    /**
     * 查询事务状态
     */
    public com.kltb.accounting.api.response.TransactionStatusResponse getTransactionStatus(String txnNo) {
        TransactionPO transaction = transactionRepository.selectByTxnNo(txnNo);
        if (transaction == null) {
            throw new ServiceException(ResultCode.DATA_NOT_FOUND, "事务不存在: " + txnNo);
        }

        // 查找关联凭证
        AccountingVoucherPO voucher = accountingVoucherRepository
            .selectByTraceNo(transaction.getTraceNo())
            .stream()
            .filter(v -> txnNo.equals(v.getTxnNo()))
            .findFirst()
            .orElse(null);

        return assembler.toTransactionResponse(transaction, voucher);
    }
}
