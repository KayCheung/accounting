package com.kltb.accounting.core.domain.service;

import com.alibaba.fastjson2.JSON;
import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.MessageStatusEnum;
import com.kltb.accounting.core.domain.enums.VoucherEntryStatusEnum;
import com.kltb.accounting.core.domain.enums.VoucherStatusEnum;
import com.kltb.accounting.core.infrastructure.account.AccountBalanceCalculator;
import com.kltb.accounting.core.infrastructure.messaging.LocalMessageService;
import com.kltb.accounting.core.infrastructure.messaging.PostingMessagePayload;
import com.kltb.accounting.core.infrastructure.persistence.entity.*;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountDetailMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.MessageReceiptMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.SubAccountDetailMapper;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingVoucherRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubAccountRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.TransactionRepository;
import com.kltb.accounting.core.shared.exception.AccountException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 异步过账领域服务
 * <p>
 * 职责：本地消息写入 + 消费端过账逻辑 + 状态联动
 * <p>
 * 是否记账：是（消费端执行余额变更）
 * 异常处理：
 *   - [AccountException] → 余额不足 → 消费失败，触发重试
 *   - [RuntimeException] → 系统异常 → 消费失败，触发重试
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncPostingDomainService {

    private final LocalMessageService localMessageService;
    private final AccountingVoucherRepository accountingVoucherRepository;
    private final AccountRepository accountRepository;
    private final SubAccountRepository subAccountRepository;
    private final AccountDetailMapper accountDetailMapper;
    private final SubAccountDetailMapper subAccountDetailMapper;
    private final MessageReceiptMapper messageReceiptMapper;
    private final TransactionRepository transactionRepository;
    private final RollbackDomainService rollbackDomainService;

    private static final String POSTING_TOPIC = "posting_topic";
    private static final String POSTING_TAG = "POSTING_ENTRY";

    /**
     * 写入异步过账本地消息（与实时过账同事务）
     *
     * @param entry   分录记录（is_unilateral=0 且 is_buffered=0）
     * @param voucher 凭证记录
     */
    public void writeAsyncPostingMessage(AccountingVoucherEntryPO entry, AccountingVoucherPO voucher) {
        String payload = JSON.toJSONString(new PostingMessagePayload(
            voucher.getVoucherNo(),
            entry.getEntryId(),
            entry.getAccountNo(),
            entry.getSubjectCode(),
            entry.getDebitCredit() != null ? entry.getDebitCredit().getCode() : null,
            entry.getChangeDirection(),
            entry.getAmount(),
            entry.getAccountingDate(),
            entry.getCurrency(),
            entry.getSummary()
        ));

        LocalMessagePO message = new LocalMessagePO();
        message.setMessageId(UUID.randomUUID().toString().replace("-", ""));
        message.setTopic(POSTING_TOPIC);
        message.setTag(POSTING_TAG);
        message.setBusinessKey(entry.getEntryId());
        message.setPayload(payload);
        message.setStatus(MessageStatusEnum.PENDING);
        message.setRetryCount(0);
        message.setMaxRetry(3);
        message.setNextRetryTime(LocalDateTime.now());

        localMessageService.save(message);
    }

    /**
     * MQ 消费端：执行异步过账（独立事务）
     * <p>
     * 幂等：先检查分录 status 是否已是 2，是则直接返回成功
     */
    public void consumeAsyncPostingMessage(PostingMessagePayload payload) {
        // 1. 幂等检查
        AccountingVoucherEntryPO entry = accountingVoucherRepository
            .selectEntriesByVoucherNo(payload.getVoucherNo())
            .stream()
            .filter(e -> e.getEntryId().equals(payload.getEntryId()))
            .findFirst()
            .orElse(null);

        if (entry == null) {
            log.error("[ASYNC-POSTING] 分录不存在: entryId={}", payload.getEntryId());
            throw new AccountException(ResultCode.DATA_NOT_FOUND, "分录不存在: " + payload.getEntryId());
        }

        if (entry.getStatus() == VoucherEntryStatusEnum.POSTED) {
            log.info("[ASYNC-POSTING] 分录已过账，跳过: entryId={}", payload.getEntryId());
            return;
        }

        // 2. 查询账户
        AccountPO account = accountRepository.selectByAccountNo(payload.getAccountNo());
        if (account == null) {
            throw new AccountException(ResultCode.ACCOUNT_NOT_FOUND,
                "账户不存在: accountNo=" + payload.getAccountNo());
        }

        // 3. 按 account_no 加锁
        List<AccountPO> lockedAccounts = accountRepository.selectForUpdateBatch(
            java.util.Collections.singletonList(payload.getAccountNo()));
        if (lockedAccounts.isEmpty()) {
            throw new AccountException(ResultCode.ACCOUNT_NOT_FOUND,
                "加锁查询账户为空: accountNo=" + payload.getAccountNo());
        }

        // 4. 查询子账户并加锁
        List<SubAccountPO> subs = subAccountRepository.selectForUpdate(payload.getAccountNo());
        SubAccountPO subAccount = subs != null && !subs.isEmpty() ? subs.get(0) : null;

        // 5. 保存变更前余额
        BigDecimal oldBalance = account.getBalance();
        BigDecimal subOldBalance = subAccount != null ? subAccount.getBalance() : null;

        // 6. 余额计算
        BigDecimal newBalance = AccountBalanceCalculator.calculateNewBalance(
            oldBalance,
            payload.getAmount(),
            payload.getChangeDirection()
        );

        // 7. 更新主账户余额 + version
        account.setBalance(newBalance);
        account.setVersion(account.getVersion() != null ? account.getVersion() + 1 : 1);
        accountRepository.updateById(account);

        // 8. 更新子账户余额
        BigDecimal subNewBalance = null;
        if (subAccount != null) {
            subNewBalance = AccountBalanceCalculator.calculateNewBalance(
                subOldBalance,
                payload.getAmount(),
                payload.getChangeDirection()
            );
            subAccount.setBalance(subNewBalance);
            subAccount.setVersion(subAccount.getVersion() != null ? subAccount.getVersion() + 1 : 1);
            subAccountRepository.updateById(subAccount);
        }

        // 9. 写入 t_account_detail
        AccountDetailPO detail = new AccountDetailPO();
        detail.setVoucherNo(payload.getVoucherNo())
            .setEntryId(payload.getEntryId())
            .setSubjectCode(payload.getSubjectCode())
            .setAccountNo(payload.getAccountNo())
            .setDebitCredit(com.kltb.accounting.core.domain.enums.DebitCreditEnum.fromCode(payload.getDebitCredit()))
            .setChangeDirection(payload.getChangeDirection() == 1
                ? com.kltb.accounting.core.domain.enums.ChangeDirectionEnum.INCREASE
                : com.kltb.accounting.core.domain.enums.ChangeDirectionEnum.DECREASE)
            .setCurrency(payload.getCurrency())
            .setPreBalance(oldBalance)
            .setAmount(payload.getAmount())
            .setPostBalance(newBalance)
            .setAccountingDate(payload.getAccountingDate())
            .setSummary(payload.getSummary());
        accountDetailMapper.insert(detail);

        // 10. 写入 t_sub_account_detail
        if (subAccount != null) {
            SubAccountDetailPO subDetail = new SubAccountDetailPO();
            subDetail.setVoucherNo(payload.getVoucherNo())
                .setEntryId(payload.getEntryId())
                .setAccountNo(payload.getAccountNo())
                .setBalanceType(com.kltb.accounting.core.domain.enums.BalanceTypeEnum.AVAILABLE)
                .setDebitCredit(com.kltb.accounting.core.domain.enums.DebitCreditEnum.fromCode(payload.getDebitCredit()))
                .setChangeDirection(payload.getChangeDirection() == 1
                    ? com.kltb.accounting.core.domain.enums.ChangeDirectionEnum.INCREASE
                    : com.kltb.accounting.core.domain.enums.ChangeDirectionEnum.DECREASE)
                .setCurrency(payload.getCurrency())
                .setPreBalance(subOldBalance)
                .setAmount(payload.getAmount())
                .setPostBalance(subNewBalance)
                .setAccountingDate(payload.getAccountingDate())
                .setSummary(payload.getSummary());
            subAccountDetailMapper.insert(subDetail);
        }

        // 11. 更新分录状态为已过账
        entry.setStatus(VoucherEntryStatusEnum.POSTED);
        entry.setBalanceUpdateTime(LocalDateTime.now());
        accountingVoucherRepository.updateEntryById(entry);

        // 12. 检查并联动更新凭证/事务状态
        updateStatusIfAllNonBufferPosted(payload.getVoucherNo());

        // 13. 更新本地消息状态为已发送
        localMessageService.markSent(payload.getEntryId());

        // 14. 写入消息回执
        MessageReceiptPO receipt = new MessageReceiptPO();
        receipt.setMessageId(payload.getEntryId());
        receipt.setBusinessKey(payload.getEntryId());
        receipt.setStatus(com.kltb.accounting.core.domain.enums.ReceiptStatusEnum.SUCCESS);
        receipt.setReceivedTime(LocalDateTime.now());
        receipt.setProcessedTime(LocalDateTime.now());
        messageReceiptMapper.insert(receipt);

        log.info("[ASYNC-POSTING] 异步过账成功: entryId={}, accountNo={}", payload.getEntryId(), payload.getAccountNo());
    }

    /**
     * 检查凭证所有非缓冲分录（含异步+实时）是否都已过账
     */
    public boolean areAllNonBufferEntriesPosted(String voucherNo) {
        List<AccountingVoucherEntryPO> entries = accountingVoucherRepository
            .selectEntriesByVoucherNo(voucherNo);
        return entries.stream()
            .filter(e -> e.getBuffered() == null || e.getBuffered() != 1)
            .allMatch(e -> e.getStatus() == VoucherEntryStatusEnum.POSTED);
    }

    /**
     * 联动更新凭证状态和事务状态
     * 当所有非缓冲分录都过账成功后：
     *   - 凭证 status=3(已过账)
     *   - 事务 status=2(成功)
     */
    public void updateStatusIfAllNonBufferPosted(String voucherNo) {
        if (areAllNonBufferEntriesPosted(voucherNo)) {
            // 更新凭证状态
            accountingVoucherRepository.updateStatusByVoucherNo(voucherNo, VoucherStatusEnum.POSTED.getCode());

            // 更新事务状态
            AccountingVoucherPO voucher = accountingVoucherRepository.selectByVoucherNoSimple(voucherNo);
            if (voucher != null && voucher.getTxnNo() != null) {
                TransactionPO txn = transactionRepository.selectByTxnNo(voucher.getTxnNo());
                if (txn != null) {
                    transactionRepository.updateStatusByTxnNo(
                        voucher.getTxnNo(),
                        com.kltb.accounting.core.domain.enums.TransactionStatusEnum.SUCCESS,
                        null,
                        LocalDateTime.now()
                    );
                }
            }

            log.info("[ASYNC-POSTING] 所有非缓冲分录已过账，凭证/事务状态已联动更新: voucherNo={}", voucherNo);
        }
    }

    /**
     * 异步消费失败后的回滚入口（由 PostingMessageConsumer 调用）
     * <p>
     * 当 MQ 消费重试超限且不可重试时，调用 RollbackDomainService 执行单边回滚。
     */
    public void executeRollbackOnAsyncFailure(String voucherNo, String entryId, String failReason) {
        // 先查询凭证关联的 txnNo
        AccountingVoucherPO voucher = accountingVoucherRepository.selectByVoucherNoSimple(voucherNo);
        if (voucher == null) {
            log.error("[ASYNC-POSTING] 回滚失败，凭证不存在: voucherNo={}", voucherNo);
            return;
        }
        String txnNo = voucher.getTxnNo();

        rollbackDomainService.executeRollbackForAsyncFailure(voucherNo, txnNo, failReason);
    }
}
