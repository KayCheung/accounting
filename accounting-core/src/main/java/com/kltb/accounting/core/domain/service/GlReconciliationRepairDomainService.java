package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.response.GlRepairResponse;
import com.kltb.accounting.core.domain.enums.EodStatusEnum;
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

        // 1. 扫描历史异常与断档日期（严格早于 accountingDate）
        List<LocalDate> voucherDates = voucherEntryMapper.selectDistinctDatesWithEntriesBeforeDate(accountingDate);
        List<LocalDate> balanceDates = accountBalanceMapper.selectDistinctDatesBeforeDate(accountingDate);
        Set<LocalDate> balanceDateSet = new HashSet<>(balanceDates != null ? balanceDates : Collections.emptyList());

        Set<LocalDate> datesToRepair = new TreeSet<>();
        if (voucherDates != null) {
            for (LocalDate d : voucherDates) {
                if (!d.isBefore(accountingDate)) {
                    continue;
                }
                // 场景 A: 存在已过账凭证但未生成日余额
                if (!balanceDateSet.contains(d)) {
                    datesToRepair.add(d);
                    repairLogs.add("检测到历史日 [" + d + "] 存在已过账凭证但缺失日余额表记录，标记重算");
                    continue;
                }
                // 场景 B: 日余额发生额与凭证发生额不一致（例如 MQ 延迟消费落账导致历史日余额失效）
                BigDecimal voucherDebit = voucherEntryMapper.sumDailyDebitTotalByDate(d);
                BigDecimal balanceDebit = accountBalanceMapper.sumDailyDebitTotalByDate(d);
                if (voucherDebit != null && balanceDebit != null && voucherDebit.compareTo(balanceDebit) != 0) {
                    datesToRepair.add(d);
                    repairLogs.add("检测到历史日 [" + d + "] 凭证借方发生额 (¥" + voucherDebit + ") 与日余额表 (¥" + balanceDebit + ") 不一致，标记重算");
                    log.info("[GL-REPAIR-DIFF-DETECTED] date={}, voucherDebit={}, balanceDebit={}", d, voucherDebit, balanceDebit);
                }
            }
        }

        BigDecimal repairedAmount = BigDecimal.ZERO;
        int repairedAccountCount = 0;
        int repairedDateCount = datesToRepair.size();

        // 2. 若检测到历史断档/不一致，从最早异常日按时序自动滚动重算各日余额至 accountingDate 前一日
        if (!datesToRepair.isEmpty()) {
            LocalDate earliestDate = datesToRepair.iterator().next();
            repairLogs.add("检测到存在 " + datesToRepair.size() + " 个历史未同步/断档会计日: " + datesToRepair);
            repairLogs.add("自最早异常日 [" + earliestDate + "] 起按时序滚动重算并补全日余额...");

            // 收集 earliestDate 到 accountingDate.minusDays(1) 之间的所有需要结转的日期
            Set<LocalDate> rollDates = new TreeSet<>(datesToRepair);
            try {
                List<LocalDate> intermediateDates = accountBalanceMapper.selectDistinctDatesBetweenDates(earliestDate, accountingDate.minusDays(1));
                if (intermediateDates != null) {
                    rollDates.addAll(intermediateDates);
                }
            } catch (Exception e) {
                log.warn("[GL-REPAIR] 获取区间历史日期失败: {}", e.getMessage());
            }

            for (LocalDate rollDate : rollDates) {
                try {
                    List<AccountBalancePO> dayBalances = eodDomainService.calculateDailyBalances(rollDate);
                    eodDomainService.upsertDailyBalances(rollDate, dayBalances);

                    BigDecimal dateDebit = dayBalances.stream()
                            .map(AccountBalancePO::getDebitAmount)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    repairedAmount = repairedAmount.add(dateDebit);

                    repairLogs.add("成功回补并校准历史日 [" + rollDate + "] 日余额，结转账户数: "
                            + dayBalances.size() + "，借方发生额: ¥" + dateDebit);
                    log.info("[GL-REPAIR-BACKFILL] 历史日重算完成: date={}, count={}, amount={}",
                            rollDate, dayBalances.size(), dateDebit);
                } catch (Exception e) {
                    log.error("[GL-REPAIR-BACKFILL-ERROR] 历史日重算异常: date={}, error={}", rollDate, e.getMessage(), e);
                    repairLogs.add("重算历史日 [" + rollDate + "] 异常: " + e.getMessage());
                }
            }
        } else {
            repairLogs.add("历史各日余额发生额与凭证发生额一致，未检测到前序历史未结转断档日");
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

        // 3.5 级联滚动重算并同步当前会计日之后的所有后序会计日（向前级联，保证期初期末链条连贯）
        try {
            List<LocalDate> futureDates = voucherEntryMapper.selectDistinctDatesWithEntriesAfterDate(accountingDate);
            if (futureDates != null && !futureDates.isEmpty()) {
                repairLogs.add("检测到当前会计日之后存在 " + futureDates.size() + " 个后序已记账会计日: " + futureDates + "，开始向前级联重算...");
                for (LocalDate fDate : futureDates) {
                    List<AccountBalancePO> fBalances = eodDomainService.calculateDailyBalances(fDate);
                    eodDomainService.upsertDailyBalances(fDate, fBalances);
                    repairLogs.add("成功级联重算并同步后序会计日 [" + fDate + "] 日余额表，生成有效分户日余额数: " + fBalances.size());
                    log.info("[GL-REPAIR-CASCADE-FORWARD] 级联后序日重算完成: date={}, count={}", fDate, fBalances.size());

                    // 若后序会计日因前序差额导致日切处于失败阻断，重置其失败标记
                    transactionTemplate.execute(status -> {
                        EodStatusPO fStatus = eodStatusRepository.findByDate(fDate);
                        if (fStatus != null && EodStatusEnum.FAILED.equals(fStatus.getEodStatus())) {
                            eodStatusRepository.resetStatus(fDate);
                            repairLogs.add("已清除后序会计日 [" + fDate + "] 的日切失败阻断标记，状态恢复为未开始/可日切");
                        }
                        return null;
                    });
                }
            }
        } catch (Exception e) {
            log.error("[GL-REPAIR-CASCADE-FORWARD-ERROR] 级联后序日余额重算异常: {}", e.getMessage(), e);
            repairLogs.add("级联后序日余额重算异常: " + e.getMessage());
        }

        // 4. 重新执行总分核对，验证修复效果
        GlReconciliationResult glResult = trialBalanceDomainService.executeGlReconciliation(accountingDate);
        boolean passed = glResult.isPassed();
        BigDecimal remainingDiff = glResult.getTotalDiff();

        if (passed) {
            repairLogs.add("重新执行总分核对：全部科目总账与分户账核对一致，总分差额已归零 (¥0.00)");

            // 若之前因失败处于阻断状态，重置日切状态以便重新执行
            transactionTemplate.execute(status -> {
                EodStatusPO eodStatus = eodStatusRepository.findByDate(accountingDate);
                if (eodStatus != null && EodStatusEnum.FAILED.equals(eodStatus.getEodStatus())) {
                    eodStatusRepository.resetStatus(accountingDate);
                    repairLogs.add("已清除会计日 [" + accountingDate + "] 的日切失败阻断标记，状态恢复为未开始/可日切");
                }
                return null;
            });
        } else {
            repairLogs.add("重新执行总分核对：仍存在剩余差额 ¥" + remainingDiff + "，不平科目数: " + glResult.getDiffDetails().size());
        }

        String msg = passed
                ? ("总分核对智能修复成功！已自动回溯校准 " + repairedDateCount + " 个历史异常会计日，当前总分差额已清零。")
                : ("智能修复已完成历史结转，但当前会计日仍存在剩余差额 ¥" + remainingDiff + "，请点击查看分户明细核实。");

        return GlRepairResponse.builder()
                .success(passed)
                .repairedDateCount(repairedDateCount)
                .repairedAccountCount(repairedAccountCount)
                .repairedAmount(repairedAmount)
                .remainingDiff(remainingDiff)
                .message(msg)
                .repairLogs(repairLogs)
                .build();
    }
}
