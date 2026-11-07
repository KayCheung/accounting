package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.core.domain.enums.BalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.BalanceTypeEnum;
import com.kltb.accounting.core.domain.enums.BufferStatusEnum;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.BufferPostingDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.SubAccountPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountDetailMapper;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.BufferPostingDetailRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubAccountRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Running Balance 校验器
 * <p>
 * 职责：校验账户实际余额与明细流水连续性及总分一致性。
 * <p>
 * 校验逻辑：
 * 1. 总分一致性：主账户余额 vs 子账户(可用+冻结)余额合计
 * 2. 流水末态核对：实际动账余额 vs 当日最后一条动账明细 t_account_detail 的 post_balance
 * <p>
 * 是否记账：否（仅校验）
 * 异常处理：差额超阈值时记录告警日志，不阻断后续流程
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RunningBalanceValidator {

    private static final BigDecimal BALANCE_DIFF_THRESHOLD = new BigDecimal("0.000001");

    private final AccountRepository accountRepository;
    private final SubAccountRepository subAccountRepository;
    private final BufferPostingDetailRepository bufferPostingDetailRepository;
    private final AccountDetailMapper accountDetailMapper;

    /**
     * 校验 Running Balance
     *
     * @param accountNo      账户编号
     * @param accountingDate 会计日期
     * @return 校验结果
     */
    public ValidationResult validateRunningBalance(String accountNo, LocalDate accountingDate) {
        // 1. 查询账户与子账户
        AccountPO account = accountRepository.selectByAccountNo(accountNo);
        SubAccountPO subAccount = subAccountRepository.selectByAccountNoAndType(
                accountNo, BalanceTypeEnum.AVAILABLE.getCode());
        if (subAccount == null && account == null) {
            log.warn("[RUNNING-BALANCE] 账户不存在，跳过校验: accountNo={}", accountNo);
            return ValidationResult.skip(accountNo, "账户不存在");
        }

        BigDecimal actualBalance = (subAccount != null) ? subAccount.getBalance() : account.getBalance();

        // 2. 检查当日是否有已入账缓冲明细
        List<BufferPostingDetailPO> successDetails = bufferPostingDetailRepository
                .selectSuccessByDate(accountingDate, 10000)
                .stream()
                .filter(d -> d.getAccountNo().equals(accountNo))
                .sorted(Comparator.comparing((BufferPostingDetailPO d) -> d.getTradeTime() != null ? d.getTradeTime() : LocalDateTime.MIN)
                        .thenComparing(BufferPostingDetailPO::getId))
                .toList();

        if (successDetails.isEmpty()) {
            log.info("[RUNNING-BALANCE] 当日无已入账缓冲明细，跳过校验: accountNo={}, date={}",
                    accountNo, accountingDate);
            return ValidationResult.skip(accountNo, "无已入账缓冲明细");
        }

        // 3. 维度一：总分一致性核对（主账户 vs 子账户合计）
        if (account != null) {
            List<SubAccountPO> subs = subAccountRepository.selectByAccountNo(accountNo);
            BigDecimal subTotal = subs.stream()
                    .map(SubAccountPO::getBalance)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal accountSubDiff = account.getBalance().subtract(subTotal).abs();
            if (accountSubDiff.compareTo(BALANCE_DIFF_THRESHOLD) > 0) {
                log.error("[RUNNING-BALANCE-ALERT] accountNo={}, 主账户余额({})与子账户合计余额({})不一致，总分失衡！",
                        accountNo, account.getBalance(), subTotal);
                return ValidationResult.alert(accountNo, account.getBalance(), subTotal, accountSubDiff);
            }
        }

        // 4. 维度二：流水末态核对（Running Balance 核心校验）
        // 查询该账户在当日的最后一条动账流水 post_balance
        BigDecimal lastPostBalance = accountDetailMapper.selectLastPostBalance(accountNo, accountingDate);
        if (lastPostBalance == null) {
            // 当日存在 SUCCESS 的缓冲明细，但 t_account_detail 中却没有任何动账流水（流水遗漏）
            BigDecimal diff = actualBalance.abs();
            log.error("[RUNNING-BALANCE-ALERT] accountNo={}, 当日存在 {} 条已成功缓冲明细，但未生成账户明细流水(t_account_detail)！",
                    accountNo, successDetails.size());
            return ValidationResult.alert(accountNo, actualBalance, BigDecimal.ZERO, diff);
        }

        BigDecimal diff = actualBalance.subtract(lastPostBalance).abs();
        if (diff.compareTo(BALANCE_DIFF_THRESHOLD) > 0) {
            log.error("[RUNNING-BALANCE-ALERT] accountNo={}, actualBalance={}, lastPostBalance={}, diff={}",
                    accountNo, actualBalance, lastPostBalance, diff);
            return ValidationResult.alert(accountNo, actualBalance, lastPostBalance, diff);
        }

        log.info("[RUNNING-BALANCE] 校验通过: accountNo={}, actualBalance={}, lastPostBalance={}",
                accountNo, actualBalance, lastPostBalance);

        return ValidationResult.pass(accountNo, actualBalance, lastPostBalance, BigDecimal.ZERO);
    }

    /**
     * 校验结果
     */
    @Data
    public static class ValidationResult {
        private String accountNo;
        private boolean passed;
        private boolean isAlert;
        private BigDecimal actualBalance;
        private BigDecimal calculatedBalance;
        private BigDecimal diff;
        private String message;

        public static ValidationResult pass(String accountNo, BigDecimal actual,
                BigDecimal calculated, BigDecimal diff) {
            ValidationResult r = new ValidationResult();
            r.accountNo = accountNo;
            r.passed = true;
            r.isAlert = false;
            r.actualBalance = actual;
            r.calculatedBalance = calculated;
            r.diff = diff;
            r.message = "校验通过";
            return r;
        }

        public static ValidationResult alert(String accountNo, BigDecimal actual,
                BigDecimal calculated, BigDecimal diff) {
            ValidationResult r = new ValidationResult();
            r.accountNo = accountNo;
            r.passed = false;
            r.isAlert = true;
            r.actualBalance = actual;
            r.calculatedBalance = calculated;
            r.diff = diff;
            r.message = "Running Balance 不一致";
            return r;
        }

        public static ValidationResult skip(String accountNo, String reason) {
            ValidationResult r = new ValidationResult();
            r.accountNo = accountNo;
            r.passed = true;
            r.isAlert = false;
            r.message = "跳过: " + reason;
            return r;
        }
    }
}
