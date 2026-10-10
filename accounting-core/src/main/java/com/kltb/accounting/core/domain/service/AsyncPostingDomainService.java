package com.kltb.accounting.core.domain.service;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.*;
import com.kltb.accounting.core.infrastructure.account.AccountBalanceCalculator;
import com.kltb.accounting.core.infrastructure.account.AccountValidator;
import com.kltb.accounting.core.infrastructure.messaging.LocalMessageService;
import com.kltb.accounting.core.infrastructure.messaging.PostingMessagePayload;
import com.kltb.accounting.core.infrastructure.persistence.entity.*;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountDetailMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.MessageReceiptMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.SubAccountDetailMapper;
import com.kltb.accounting.core.infrastructure.persistence.repository.*;
import com.kltb.accounting.core.shared.exception.AccountException;
import com.kltb.accounting.core.shared.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
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
    private final BusinessRecordRepository businessRecordRepository;

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
            entry.getChangeDirection() != null ? entry.getChangeDirection().getCode() : null,
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
        // 1. 参数预校验（Fast-Fail）
        if (payload == null) {
            throw new ServiceException(ResultCode.PARAM_ERROR, "异步过账消息体不能为空");
        }
        if (StrUtil.isBlank(payload.getVoucherNo()) || StrUtil.isBlank(payload.getEntryId())
            || StrUtil.isBlank(payload.getAccountNo())) {
            throw new ServiceException(ResultCode.PARAM_ERROR, "异步过账消息体核心参数缺失");
        }
        if (payload.getAmount() == null || payload.getChangeDirection() == null) {
            throw new ServiceException(ResultCode.PARAM_ERROR, "异步过账消息体金额或变动方向缺失");
        }

        // 2. 查询并校验分录（幂等检查）
        AccountingVoucherEntryPO entry = accountingVoucherRepository
            .selectEntriesByVoucherNo(payload.getVoucherNo())
            .stream()
            .filter(e -> payload.getEntryId().equals(e.getEntryId()))
            .findFirst()
            .orElse(null);

        if (entry == null) {
            log.error("[ASYNC-POSTING] 分录不存在: entryId={}, voucherNo={}", payload.getEntryId(), payload.getVoucherNo());
            throw new AccountException(ResultCode.DATA_NOT_FOUND, "分录不存在: " + payload.getEntryId());
        }

        if (entry.getStatus() == VoucherEntryStatusEnum.POSTED) {
            log.info("[ASYNC-POSTING] 分录已过账，幂等跳过: entryId={}", payload.getEntryId());
            return;
        }

        // 3. 查询并校验关联凭证（严格由凭证驱动，元数据完整性预检）
        AccountingVoucherPO voucher = accountingVoucherRepository.selectByVoucherNoSimple(payload.getVoucherNo());
        if (voucher == null) {
            log.error("[ASYNC-POSTING] 异步过账凭证不存在: voucherNo={}", payload.getVoucherNo());
            throw new ServiceException(ResultCode.VOUCHER_NOT_FOUND, "异步过账凭证不存在: voucherNo=" + payload.getVoucherNo());
        }
        if (StrUtil.isBlank(voucher.getTxnNo())) {
            log.error("[ASYNC-POSTING] 异步过账凭证事务号缺失: voucherNo={}", payload.getVoucherNo());
            throw new ServiceException(ResultCode.PARAM_ERROR, "异步过账凭证事务号(txnNo)缺失，拒绝过账: voucherNo=" + payload.getVoucherNo());
        }

        // 4. 查询主账户并校验状态（Fast-Fail）
        AccountPO account = accountRepository.selectByAccountNo(payload.getAccountNo());
        AccountValidator.validateExists(account, payload.getAccountNo());
        AccountValidator.validatePostable(account, payload.getChangeDirection());

        // 5. 按 account_no 加悲观锁（行锁，获取最新持久态）
        List<AccountPO> lockedAccounts = accountRepository.selectForUpdateBatch(
            Collections.singletonList(payload.getAccountNo()));
        if (lockedAccounts.isEmpty()) {
            throw new AccountException(ResultCode.ACCOUNT_NOT_FOUND,
                "加锁查询账户为空: accountNo=" + payload.getAccountNo());
        }
        AccountPO lockedAccount = lockedAccounts.get(0);
        AccountValidator.validatePostable(lockedAccount, payload.getChangeDirection());

        // 6. 查询子账户并加锁（精准过滤可用余额子账户）
        List<SubAccountPO> subs = subAccountRepository.selectForUpdate(payload.getAccountNo());
        SubAccountPO subAccount = subs != null
            ? subs.stream().filter(s -> BalanceTypeEnum.AVAILABLE.equals(s.getBalanceType())).findFirst().orElse(null)
            : null;

        // 7. 保存变更前余额
        BigDecimal oldBalance = lockedAccount.getBalance();
        BigDecimal subOldBalance = subAccount != null ? subAccount.getBalance() : null;

        // 8. 试算新余额（遵循绝对值法则，若余额不足在此阶段直接抛出异常，此时数据库未发生任何变更）
        BigDecimal newBalance = AccountBalanceCalculator.calculateNewBalance(
            oldBalance,
            payload.getAmount(),
            payload.getChangeDirection()
        );

        BigDecimal subNewBalance = null;
        if (subAccount != null) {
            subNewBalance = AccountBalanceCalculator.calculateNewBalance(
                subOldBalance,
                payload.getAmount(),
                payload.getChangeDirection()
            );
        }

        // 9. 更新主账户余额（乐观锁版本号由 MyBatis-Plus 插件自动管理）
        lockedAccount.setBalance(newBalance);
        accountRepository.updateById(lockedAccount);

        // 10. 更新子账户余额（乐观锁版本号由 MyBatis-Plus 插件自动管理）
        if (subAccount != null) {
            subAccount.setBalance(subNewBalance);
            subAccountRepository.updateById(subAccount);
        }

        // 11. 写入 t_account_detail
        AccountDetailPO detail = new AccountDetailPO();
        detail.setVoucherNo(voucher.getVoucherNo())
            .setEntryId(payload.getEntryId())
            .setTxnNo(voucher.getTxnNo())
            .setTraceNo(voucher.getTraceNo())
            .setTraceSeq(voucher.getTraceSeq() != null ? voucher.getTraceSeq() : 0)
            .setSubjectCode(payload.getSubjectCode())
            .setAccountNo(payload.getAccountNo())
            .setBusinessCode(voucher.getBusinessCode())
            .setTradingCode(voucher.getTradingCode())
            .setPayChannel(voucher.getPayChannel())
            .setTradeType(voucher.getTradeType())
            .setTradeTime(voucher.getTradeTime())
            .setDebitCredit(DebitCreditEnum.fromCode(payload.getDebitCredit()))
            .setChangeDirection(payload.getChangeDirection() == 1
                ? ChangeDirectionEnum.INCREASE
                : ChangeDirectionEnum.DECREASE)
            .setCurrency(payload.getCurrency())
            .setPreBalance(oldBalance)
            .setAmount(payload.getAmount())
            .setPostBalance(newBalance)
            .setAccountingDate(voucher.getAccountingDate())
            .setSummary(StrUtil.isNotBlank(payload.getSummary()) ? payload.getSummary() : voucher.getSummary())
            .setTenantId(voucher.getTenantId());
        accountDetailMapper.insert(detail);

        // 12. 写入 t_sub_account_detail
        if (subAccount != null) {
            SubAccountDetailPO subDetail = new SubAccountDetailPO();
            subDetail.setVoucherNo(voucher.getVoucherNo())
                .setEntryId(payload.getEntryId())
                .setTxnNo(voucher.getTxnNo())
                .setTraceNo(voucher.getTraceNo())
                .setTraceSeq(voucher.getTraceSeq() != null ? voucher.getTraceSeq() : 0)
                .setAccountNo(payload.getAccountNo())
                .setBalanceType(subAccount.getBalanceType() != null ? subAccount.getBalanceType() : BalanceTypeEnum.AVAILABLE)
                .setTradingCode(voucher.getTradingCode())
                .setTradeType(voucher.getTradeType())
                .setTradeTime(voucher.getTradeTime())
                .setDebitCredit(DebitCreditEnum.fromCode(payload.getDebitCredit()))
                .setChangeDirection(payload.getChangeDirection() == 1
                    ? ChangeDirectionEnum.INCREASE
                    : ChangeDirectionEnum.DECREASE)
                .setCurrency(payload.getCurrency())
                .setPreBalance(subOldBalance)
                .setAmount(payload.getAmount())
                .setPostBalance(subNewBalance)
                .setAccountingDate(voucher.getAccountingDate())
                .setSummary(StrUtil.isNotBlank(payload.getSummary()) ? payload.getSummary() : voucher.getSummary())
                .setTenantId(voucher.getTenantId());
            subAccountDetailMapper.insert(subDetail);
        }

        // 13. 更新分录状态为已过账
        entry.setStatus(VoucherEntryStatusEnum.POSTED);
        entry.setBalanceUpdateTime(LocalDateTime.now());
        accountingVoucherRepository.updateEntryById(entry);

        // 14. 检查并联动更新凭证/事务状态
        updateStatusIfAllNonBufferPosted(payload.getVoucherNo());

        // 15. 更新本地消息状态为已发送
        localMessageService.markSent(payload.getEntryId());

        // 16. 写入消息回执
        MessageReceiptPO receipt = new MessageReceiptPO();
        receipt.setMessageId(payload.getEntryId());
        receipt.setBusinessKey(payload.getEntryId());
        receipt.setStatus(ReceiptStatusEnum.SUCCESS);
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

            // 更新流水状态为成功
            if (voucher != null && StrUtil.isNotBlank(voucher.getTraceNo()) && businessRecordRepository != null) {
                businessRecordRepository.updateStatusByTraceNo(
                    voucher.getTraceNo(), BusinessRecordStatusEnum.SUCCESS);
            }

            log.info("[ASYNC-POSTING] 所有非缓冲分录已过账，凭证/事务/流水状态已联动更新: voucherNo={}", voucherNo);
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
