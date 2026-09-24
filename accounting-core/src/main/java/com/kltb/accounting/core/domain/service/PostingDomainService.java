package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.AccountStatusEnum;
import com.kltb.accounting.core.domain.enums.VoucherEntryStatusEnum;
import com.kltb.accounting.core.infrastructure.account.AccountBalanceCalculator;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherEntryPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.SubAccountDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.SubAccountPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountDetailRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubAccountDetailRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubAccountRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingVoucherRepository;
import com.kltb.accounting.core.shared.exception.AccountException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 实时过账领域服务
 * <p>
 * 职责：加锁 → 余额计算 → 账户更新 → 明细快照 → 分录状态更新
 * 注意：此类不包含事务，由 Application Service 统一控制事务边界。
 * <p>
 * 是否记账：是
 * 异常处理：
 *   - [AccountException] → 余额不足 / 账户状态异常 → 由上层 catch 触发回滚
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostingDomainService {

    private final AccountRepository accountRepository;
    private final SubAccountRepository subAccountRepository;
    private final AccountDetailRepository accountDetailRepository;
    private final SubAccountDetailRepository subAccountDetailRepository;
    private final AccountingVoucherRepository accountingVoucherRepository;

    /**
     * 实时过账：锁定账户 → 计算余额 → 更新余额 → 记录明细
     *
     * @param entries        需要实时过账的分录列表（is_unilateral=1，调用方按 account_no 升序排列）
     * @param accountingDate 会计日期
     */
    public void executeRealTimePosting(
        List<AccountingVoucherEntryPO> entries,
        LocalDate accountingDate) {

        if (entries == null || entries.isEmpty()) {
            return;
        }

        // 按 account_no 分组去重（保持升序）
        List<String> accountNos = entries.stream()
            .map(AccountingVoucherEntryPO::getAccountNo)
            .distinct()
            .collect(Collectors.toList());

        // 批量加锁查询账户
        List<AccountPO> accounts = accountRepository.selectForUpdateBatch(accountNos);
        Map<String, AccountPO> accountMap = accounts.stream()
            .collect(Collectors.toMap(AccountPO::getAccountNo, a -> a));

        // 查询子账户
        Map<String, List<SubAccountPO>> subAccountMap = new LinkedHashMap<>();
        for (String accountNo : accountNos) {
            List<SubAccountPO> subs = subAccountRepository.selectForUpdate(accountNo);
            subAccountMap.put(accountNo, subs);
        }

        // 逐分录处理
        List<AccountDetailPO> accountDetails = new ArrayList<>();
        List<SubAccountDetailPO> subAccountDetails = new ArrayList<>();

        for (AccountingVoucherEntryPO entry : entries) {
            AccountPO account = accountMap.get(entry.getAccountNo());
            if (account == null) {
                throw new AccountException(ResultCode.ACCOUNT_NOT_FOUND,
                    "账户不存在: accountNo=" + entry.getAccountNo());
            }

            // 检查账户状态
            if (account.getStatus() != AccountStatusEnum.NORMAL) {
                String msg = "账户状态异常: accountNo=" + entry.getAccountNo()
                    + ", status=" + (account.getStatus() != null ? account.getStatus().getDesc() : "null");
                if (account.getStatus() == AccountStatusEnum.FROZEN) {
                    throw new AccountException(ResultCode.ACCOUNT_FROZEN, msg);
                } else if (account.getStatus() == AccountStatusEnum.CANCELLED) {
                    throw new AccountException(ResultCode.ACCOUNT_CANCELLED, msg);
                }
                throw new AccountException(ResultCode.ACCOUNT_STATUS_ILLEGAL, msg);
            }

            // 获取对应子账户
            List<SubAccountPO> subs = subAccountMap.get(entry.getAccountNo());
            SubAccountPO subAccount = subs != null && !subs.isEmpty() ? subs.get(0) : null;

            // 保存变更前的余额（用于明细快照）
            BigDecimal oldBalance = account.getBalance();
            BigDecimal subOldBalance = subAccount != null ? subAccount.getBalance() : null;

            // 计算新余额
            BigDecimal newBalance = AccountBalanceCalculator.calculateNewBalance(
                oldBalance,
                entry.getAmount(),
                entry.getChangeDirection()
            );

            // 更新主账户余额 + version
            account.setBalance(newBalance);
            account.setVersion(account.getVersion() != null ? account.getVersion() + 1 : 1);
            accountRepository.updateById(account);

            // 更新子账户余额
            BigDecimal subNewBalance = null;
            if (subAccount != null) {
                subNewBalance = AccountBalanceCalculator.calculateNewBalance(
                    subOldBalance,
                    entry.getAmount(),
                    entry.getChangeDirection()
                );
                subAccount.setBalance(subNewBalance);
                subAccount.setVersion(subAccount.getVersion() != null ? subAccount.getVersion() + 1 : 1);
                subAccountRepository.updateById(subAccount);
            }

            // 写入 t_account_detail
            AccountDetailPO detail = new AccountDetailPO();
            detail.setVoucherNo(entry.getVoucherNo())
                .setEntryId(entry.getEntryId())
                .setTxnNo(null) // 由调用方填充
                .setTraceNo(null) // 由调用方填充
                .setTraceSeq(null)
                .setSubjectCode(entry.getSubjectCode())
                .setAccountNo(entry.getAccountNo())
                .setBusinessCode(null) // 由调用方填充
                .setTradingCode(null)
                .setPayChannel(null)
                .setTradeType(null)
                .setTradeTime(null)
                .setDebitCredit(entry.getDebitCredit())
                .setChangeDirection(entry.getChangeDirection() == 1
                    ? com.kltb.accounting.core.domain.enums.ChangeDirectionEnum.INCREASE
                    : com.kltb.accounting.core.domain.enums.ChangeDirectionEnum.DECREASE)
                .setCurrency(entry.getCurrency())
                .setPreBalance(oldBalance)
                .setAmount(entry.getAmount())
                .setPostBalance(newBalance)
                .setAccountingDate(accountingDate)
                .setSummary(entry.getSummary());
            accountDetails.add(detail);

            // 写入 t_sub_account_detail
            if (subAccount != null) {
                SubAccountDetailPO subDetail = new SubAccountDetailPO();
                subDetail.setVoucherNo(entry.getVoucherNo())
                    .setEntryId(entry.getEntryId())
                    .setTxnNo(null)
                    .setTraceNo(null)
                    .setTraceSeq(null)
                    .setAccountNo(entry.getAccountNo())
                    .setBalanceType(com.kltb.accounting.core.domain.enums.BalanceTypeEnum.AVAILABLE)
                    .setTradingCode(null)
                    .setTradeType(null)
                    .setTradeTime(null)
                    .setDebitCredit(entry.getDebitCredit())
                    .setChangeDirection(entry.getChangeDirection() == 1
                        ? com.kltb.accounting.core.domain.enums.ChangeDirectionEnum.INCREASE
                        : com.kltb.accounting.core.domain.enums.ChangeDirectionEnum.DECREASE)
                    .setCurrency(entry.getCurrency())
                    .setPreBalance(subOldBalance)
                    .setAmount(entry.getAmount())
                    .setPostBalance(subNewBalance)
                    .setAccountingDate(accountingDate)
                    .setSummary(entry.getSummary());
                subAccountDetails.add(subDetail);
            }

            // 更新分录状态为已过账
            entry.setStatus(VoucherEntryStatusEnum.POSTED);
            entry.setBalanceUpdateTime(LocalDateTime.now());
        }

        // 批量写入明细
        accountDetailRepository.batchInsert(accountDetails);
        if (!subAccountDetails.isEmpty()) {
            subAccountDetailRepository.batchInsert(subAccountDetails);
        }
    }

    /**
     * 检查凭证所有实时分录是否都已过账
     */
    public boolean areAllRealTimeEntriesPosted(String voucherNo) {
        List<AccountingVoucherEntryPO> entries = accountingVoucherRepository
            .selectEntriesByVoucherNo(voucherNo);
        return entries.stream()
            .filter(e -> e.getUnilateral() != null && e.getUnilateral() == 1)
            .allMatch(e -> e.getStatus() == VoucherEntryStatusEnum.POSTED);
    }
}
