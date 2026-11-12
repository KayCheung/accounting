package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.response.GlRepairResponse;
import com.kltb.accounting.core.domain.service.TrialBalanceDomainService.GlReconciliationResult;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountBalancePO;
import com.kltb.accounting.core.infrastructure.persistence.entity.EodStatusPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountBalanceMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountingVoucherEntryMapper;
import com.kltb.accounting.core.infrastructure.persistence.repository.EodStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * 总账与分户账核对线上智能修复领域服务
 * <p>
 * 遵循金融核心架构规范：严禁使用 @Transactional，必须使用 TransactionTemplate。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlReconciliationRepairDomainService {

    private final AccountingVoucherEntryMapper voucherEntryMapper;
    private final AccountBalanceMapper accountBalanceMapper;
    private final EodDomainService eodDomainService;
    private final TrialBalanceDomainService trialBalanceDomainService;
    private final EodStatusRepository eodStatusRepository;
    private final TransactionTemplate transactionTemplate;

    /**
     * 针对指定会计日执行总分核对一键诊断与自动修复
     *
     * @param accountingDate 会计日期
     * @param targetSubjectCode 指定科目（可选，为空则全科目）
     * @return 修复响应报告
     */
    public GlRepairResponse repairGlReconciliation(LocalDate accountingDate, String targetSubjectCode) {
        log.info("[GL-REPAIR-START] 开始执行总分核对一键修复: date={}, subjectCode={}", accountingDate, targetSubjectCode);
        List<String> repairLogs = new ArrayList<>();

        // 1. 扫描历史断档日期
        List<LocalDate> voucherDates = voucherEntryMapper.selectDistinctDatesWithEntriesBeforeDate(accountingDate);
        List<LocalDate> balanceDates = accountBalanceMapper.selectDistinctDatesBeforeDate(accountingDate);
        Set<LocalDate> balanceDateSet = new HashSet<>(balanceDates != null ? balanceDates : Collections.emptyList());

        List<LocalDate> gapDates = new ArrayList<>();
        if (voucherDates != null) {
            for (LocalDate d : voucherDates) {
                if (!balanceDateSet.contains(d) && d.isBefore(accountingDate)) {
                    gapDates.add(d);
                }
            }
        }
        gapDates.sort(Comparator.naturalOrder());

        BigDecimal repairedAmount = BigDecimal.ZERO;
        int repairedAccountCount = 0;

        // 2. 逐日按时序自动回补历史断档日期的日余额
        if (!gapDates.isEmpty()) {
            repairLogs.add("检测到存在 " + gapDates.size() + " 个历史未日切断档会计日: " + gapDates);
            for (LocalDate gapDate : gapDates) {
                try {
                    List<AccountBalancePO> gapBalances = eodDomainService.calculateDailyBalances(gapDate);
                    eodDomainService.upsertDailyBalances(gapDate, gapBalances);

                    BigDecimal dateSum = gapBalances.stream()
                            .map(AccountBalancePO::getDebitAmount)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    repairedAmount = repairedAmount.add(dateSum);

                    repairLogs.add("成功回补历史断档日 [" + gapDate + "] 的日余额结转，结转账户数: "
                            + gapBalances.size() + "，发生额: ¥" + dateSum);
                    log.info("[GL-REPAIR-BACKFILL] 回补历史日余额完成: date={}, count={}, amount={}",
                            gapDate, gapBalances.size(), dateSum);
                } catch (Exception e) {
                    log.error("[GL-REPAIR-BACKFILL-ERROR] 回补历史日余额异常: date={}, error={}", gapDate, e.getMessage(), e);
                    repairLogs.add("回补历史日 [" + gapDate + "] 异常: " + e.getMessage());
                }
            }
        } else {
            repairLogs.add("未检测到前序历史未结转断档日");
        }

        // 3. 重新计算并刷新当前会计日的日余额表
        try {
            List<AccountBalancePO> targetBalances = eodDomainService.calculateDailyBalances(accountingDate);
            eodDomainService.upsertDailyBalances(accountingDate, targetBalances);
            repairedAccountCount = targetBalances.size();
            repairLogs.add("成功重新计算并刷新当前会计日 [" + accountingDate + "] 日余额表，生成有效分户日余额数: " + targetBalances.size());
            log.info("[GL-REPAIR-REFRESH] 刷新当前会计日余额完成: date={}, count={}", accountingDate, targetBalances.size());
        } catch (Exception e) {
            log.error("[GL-REPAIR-REFRESH-ERROR] 刷新当前日余额异常: date={}, error={}", accountingDate, e.getMessage(), e);
            repairLogs.add("刷新当前会计日余额异常: " + e.getMessage());
        }

        // 4. 重新执行总分核对，验证修复效果
        GlReconciliationResult glResult = trialBalanceDomainService.executeGlReconciliation(accountingDate);
        boolean passed = glResult.isPassed();
        BigDecimal remainingDiff = glResult.getTotalDiff();

        if (passed) {
            repairLogs.add("重新执行总分核对：全部科目总账与分户账核对一致，总分差额已归零 (¥0.00)");

            // 若之前因 GL_RECONCILIATION 处于阻断状态，重置日切状态以便重新执行
            transactionTemplate.execute(status -> {
                EodStatusPO eodStatus = eodStatusRepository.findByDate(accountingDate);
                if (eodStatus != null && com.kltb.accounting.core.domain.enums.EodStatusEnum.FAILED.equals(eodStatus.getEodStatus())
                        && "GL_RECONCILIATION".equals(eodStatus.getFailedStage())) {
                    eodStatusRepository.resetStatus(accountingDate);
                    repairLogs.add("已清除会计日 [" + accountingDate + "] 的总分核对失败阻断标记，状态恢复为未开始/可日切");
                }
                return null;
            });
        } else {
            repairLogs.add("重新执行总分核对：仍存在剩余差额 ¥" + remainingDiff + "，不平科目数: " + glResult.getDiffDetails().size());
        }

        String msg = passed
                ? ("总分核对智能修复成功！已自动回溯补齐 " + gapDates.size() + " 个历史断档日，当前总分差额已清零。")
                : ("智能修复已完成历史结转，但当前会计日仍存在剩余差额 ¥" + remainingDiff + "，请点击查看分户明细核实。");

        return GlRepairResponse.builder()
                .success(passed)
                .repairedDateCount(gapDates.size())
                .repairedAccountCount(repairedAccountCount)
                .repairedAmount(repairedAmount)
                .remainingDiff(remainingDiff)
                .message(msg)
                .repairLogs(repairLogs)
                .build();
    }
}
