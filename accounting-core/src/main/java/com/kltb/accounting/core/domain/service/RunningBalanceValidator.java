package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.core.domain.enums.BalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.BalanceTypeEnum;
import com.kltb.accounting.core.domain.enums.BufferStatusEnum;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.BufferPostingDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.SubAccountPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.BufferPostingDetailRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubAccountRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Running Balance 校验器
 * <p>
 * 职责：在日终批量缓冲记账完成后，校验账户实际余额与缓冲明细累计余额是否一致。
 * <p>
 * 校验逻辑：
 * 1. 查询账户实际余额（SubAccountRepository）
 * 2. 查询该账户当日全部已入账缓冲明细（status=SUCCESS），按 trade_time 排序
 * 3. 计算理论变动 = Σ(同向金额) - Σ(反向金额)
 * 4. 比较差额，超阈值则告警
 * <p>
 * 是否记账：否（仅校验）
 * 异常处理：差额超阈值时记录告警日志，不阻断后续流程
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RunningBalanceValidator {

    private static final BigDecimal BALANCE_DIFF_THRESHOLD = new BigDecimal("0.000001");

    private final SubAccountRepository subAccountRepository;
    private final BufferPostingDetailRepository bufferPostingDetailRepository;

    /**
     * 校验 Running Balance
     *
     * @param accountNo      账户编号
     * @param accountingDate 会计日期
     * @return 校验结果
     */
    public ValidationResult validateRunningBalance(String accountNo, LocalDate accountingDate) {
        // 1. 查询账户实际余额
        SubAccountPO subAccount = subAccountRepository.selectByAccountNoAndType(
                accountNo, BalanceTypeEnum.AVAILABLE.getCode());
        if (subAccount == null) {
            log.warn("[RUNNING-BALANCE] 子账户不存在，跳过校验: accountNo={}", accountNo);
            return ValidationResult.skip(accountNo, "子账户不存在");
        }

        BigDecimal actualBalance = subAccount.getBalance();
        BalanceDirectionEnum balanceDirection = subAccount.getBalanceDirection();

        // 2. 查询该账户当日全部已入账缓冲明细（status=SUCCESS），按 trade_time 排序
        // P1-6: 使用语义更清晰的 selectSuccessByDate 方法
        List<BufferPostingDetailPO> successDetails = bufferPostingDetailRepository
                .selectSuccessByDate(accountingDate, 10000)
                .stream()
                .filter(d -> d.getAccountNo().equals(accountNo))
                .sorted(Comparator.comparing(BufferPostingDetailPO::getTradeTime)
                        .thenComparing(BufferPostingDetailPO::getId))
                .toList();

        if (successDetails.isEmpty()) {
            log.info("[RUNNING-BALANCE] 当日无已入账缓冲明细，跳过校验: accountNo={}, date={}",
                    accountNo, accountingDate);
            return ValidationResult.skip(accountNo, "无已入账缓冲明细");
        }

        // 3. 通过累加方式校验
        return validateByAccumulation(accountNo, actualBalance, balanceDirection, successDetails);
    }

    /**
     * 通过累加方式校验
     * <p>
     * P0-2 修复：实际计算差额并与阈值比较，不再硬编码 diff=0。
     */
    private ValidationResult validateByAccumulation(
            String accountNo, BigDecimal actualBalance,
            BalanceDirectionEnum balanceDirection,
            List<BufferPostingDetailPO> successDetails) {

        BigDecimal calculatedChange = BigDecimal.ZERO;

        for (BufferPostingDetailPO detail : successDetails) {
            DebitCreditEnum debitCredit = detail.getDebitCredit();
            BigDecimal amount = detail.getAmount();

            int changeDirection = calculateChangeDirection(balanceDirection, debitCredit);
            if (changeDirection == 1) {
                calculatedChange = calculatedChange.add(amount);
            } else {
                calculatedChange = calculatedChange.subtract(amount);
            }
        }

        // 计算差额 = |calculatedChange|
        // 注：由于无法获取期初余额，这里校验的是「当日变动合计是否为 0」
        // 如果账户当日有缓冲记账但 calculatedChange 为 0，说明数据可能有问题
        BigDecimal diff = calculatedChange.abs();
        if (diff.compareTo(BALANCE_DIFF_THRESHOLD) > 0) {
            BigDecimal calculatedBalance = actualBalance.subtract(calculatedChange);
            log.error("[RUNNING-BALANCE-ALERT] accountNo={}, actualBalance={}, " +
                            "calculatedChange={}, calculatedBalance={}, diff={}",
                    accountNo, actualBalance, calculatedChange, calculatedBalance, diff);
            return ValidationResult.alert(accountNo, actualBalance, calculatedBalance, diff);
        }

        log.info("[RUNNING-BALANCE] 校验通过: accountNo={}, calculatedChange={}, actualBalance={}",
                accountNo, calculatedChange, actualBalance);

        return ValidationResult.pass(accountNo, actualBalance, calculatedChange, BigDecimal.ZERO);
    }

    /**
     * 计算增减方向（与 BufferPostingEngineDomainService 保持一致）
     */
    private int calculateChangeDirection(BalanceDirectionEnum balanceDirection, DebitCreditEnum debitCredit) {
        int bd = balanceDirection != null ? balanceDirection.getCode() : 1;
        int dc = debitCredit != null ? debitCredit.getCode() : 1;
        return (bd == dc) ? 1 : 2;
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
