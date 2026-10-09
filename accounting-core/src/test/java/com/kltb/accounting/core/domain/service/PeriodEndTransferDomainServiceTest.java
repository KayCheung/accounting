package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.response.TransferPreviewResponse;
import com.kltb.accounting.core.domain.enums.*;
import com.kltb.accounting.core.infrastructure.account.BusinessNoGenerator;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountBalancePO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.PeriodEndTransferRulePO;
import com.kltb.accounting.core.infrastructure.persistence.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PeriodEndTransferDomainServiceTest {

    @Mock
    private PeriodEndTransferRuleRepository ruleRepository;

    @Mock
    private PeriodEndTransferRecordRepository recordRepository;

    @Mock
    private AccountBalanceRepository accountBalanceRepository;

    @Mock
    private AccountingVoucherRepository voucherRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private BusinessNoGenerator businessNoGenerator;

    @InjectMocks
    private PeriodEndTransferDomainService transferDomainService;

    @Test
    @DisplayName("期末结转试算预览：只读计算，匹配账户生成借贷平衡预览分录")
    void previewTransfer_success_balanced() {
        LocalDate date = LocalDate.of(2026, 10, 8);

        PeriodEndTransferRulePO rule = new PeriodEndTransferRulePO();
        rule.setRuleCode("TR_REV_PROFIT");
        rule.setRuleName("主营业务收入损益结转");
        rule.setTransferType(TransferTypeEnum.PROFIT_LOSS);
        rule.setSourceSubjectCode("60*");
        rule.setTargetSubjectCode("410301");
        rule.setTransferDirection(TransferDirectionEnum.CREDIT_TO_DEBIT); // 贷方余额转借方
        rule.setStatus(AvailableStatusEnum.ENABLED);

        when(ruleRepository.selectEnabledRules(any())).thenReturn(List.of(rule));
        when(ruleRepository.findByRuleCode("TR_REV_PROFIT")).thenReturn(rule);

        AccountBalancePO balance = new AccountBalancePO();
        balance.setAccountingDate(date);
        balance.setSubjectCode("600101");
        balance.setAccountNo("ACT600101001");
        balance.setCurrency("CNY");
        balance.setBalanceDirection(BalanceDirectionEnum.CREDIT);
        balance.setEndBalance(new BigDecimal("1000.00"));

        when(accountBalanceRepository.selectByDate(date)).thenReturn(List.of(balance));

        AccountSubjectPO targetSubject = new AccountSubjectPO();
        targetSubject.setSubjectCode("410301");
        targetSubject.setSubjectName("本年利润");
        when(subjectRepository.selectByCode("410301")).thenReturn(targetSubject);

        AccountSubjectPO srcSubject = new AccountSubjectPO();
        srcSubject.setSubjectCode("600101");
        srcSubject.setSubjectName("主营业务收入-通用");
        when(subjectRepository.selectByCode("600101")).thenReturn(srcSubject);

        TransferPreviewResponse preview = transferDomainService.previewTransfer(date, "TR_REV_PROFIT", null);

        assertThat(preview).isNotNull();
        assertThat(preview.getAccountingDate()).isEqualTo(date);
        assertThat(preview.getTotalRules()).isEqualTo(1);
        assertThat(preview.getActiveRules()).isEqualTo(1);
        assertThat(preview.getGrandTotalAmount()).isEqualByComparingTo("1000.00");
        assertThat(preview.isAllBalanced()).isTrue();
        assertThat(preview.getRulePreviews()).hasSize(1);
        assertThat(preview.getRulePreviews().get(0).getEntries()).hasSize(2);
    }

    @Test
    @DisplayName("期末结转执行：同日同规则已执行过且未强制重试时，幂等跳过")
    void executeTransfer_idempotentSkip() {
        LocalDate date = LocalDate.of(2026, 10, 8);

        PeriodEndTransferRulePO rule = new PeriodEndTransferRulePO();
        rule.setRuleCode("TR_REV_PROFIT");
        rule.setStatus(AvailableStatusEnum.ENABLED);

        when(ruleRepository.findByRuleCode("TR_REV_PROFIT")).thenReturn(rule);
        when(recordRepository.existsSuccessfulTransfer(date, "TR_REV_PROFIT")).thenReturn(true);

        List<PeriodEndTransferDomainService.TransferRuleResult> results =
                transferDomainService.executeTransfer(date, "TR_REV_PROFIT", null, false);

        assertThat(results).isEmpty();
        verify(transactionTemplate, never()).execute(any());
    }

    @Test
    @DisplayName("期末结转执行：正常执行生成凭证并冲销源余额")
    @SuppressWarnings("unchecked")
    void executeTransfer_success() {
        LocalDate date = LocalDate.of(2026, 10, 8);

        PeriodEndTransferRulePO rule = new PeriodEndTransferRulePO();
        rule.setRuleCode("TR_REV_PROFIT");
        rule.setRuleName("主营业务收入结转");
        rule.setTransferType(TransferTypeEnum.PROFIT_LOSS);
        rule.setSourceSubjectCode("60*");
        rule.setTargetSubjectCode("410301");
        rule.setTransferDirection(TransferDirectionEnum.CREDIT_TO_DEBIT);
        rule.setStatus(AvailableStatusEnum.ENABLED);

        when(ruleRepository.findByRuleCode("TR_REV_PROFIT")).thenReturn(rule);
        when(recordRepository.existsSuccessfulTransfer(date, "TR_REV_PROFIT")).thenReturn(false);
        when(businessNoGenerator.generateTransferNo(date)).thenReturn("TRN20261008001");
        when(businessNoGenerator.generateVoucherNo("PET", date)).thenReturn("PET20261008001");
        when(businessNoGenerator.generateEntryId()).thenReturn("ENT001", "ENT002");

        AccountBalancePO balance = new AccountBalancePO();
        balance.setAccountingDate(date);
        balance.setSubjectCode("6001");
        balance.setAccountNo("ACT6001");
        balance.setCurrency("CNY");
        balance.setBalanceDirection(BalanceDirectionEnum.CREDIT);
        balance.setDebitAmount(BigDecimal.ZERO);
        balance.setCreditAmount(new BigDecimal("2000.00"));
        balance.setEndBalance(new BigDecimal("2000.00"));

        when(accountBalanceRepository.selectByDate(date)).thenReturn(List.of(balance));

        // 模拟 transactionTemplate.execute 直接执行回调
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(null);
        });

        List<PeriodEndTransferDomainService.TransferRuleResult> results =
                transferDomainService.executeTransfer(date, "TR_REV_PROFIT", null, false);

        assertThat(results).hasSize(1);
        PeriodEndTransferDomainService.TransferRuleResult r = results.get(0);
        assertThat(r.getStatus()).isEqualTo(TransferRecordStatusEnum.SUCCESS);
        assertThat(r.getVoucherNo()).isEqualTo("PET20261008001");
        assertThat(r.getTotalAmount()).isEqualByComparingTo("2000.00");

        // 验证源账户余额被借贷发生额清零
        assertThat(balance.getEndBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(balance.getDebitAmount()).isEqualByComparingTo("2000.00");

        verify(voucherRepository).insert(any());
        verify(voucherRepository, times(2)).insertEntry(any());
        verify(accountBalanceRepository).batchUpsert(anyList());
        verify(recordRepository).insert(any());
    }

    @Test
    @DisplayName("结转周期判定：isTriggerable 正确识别每日、月末、季末与年末")
    void periodCycle_isTriggerable_correct() {
        LocalDate nonMonthEnd = LocalDate.of(2026, 10, 8);
        LocalDate monthEndNormal = LocalDate.of(2026, 10, 31);
        LocalDate quarterEnd = LocalDate.of(2026, 9, 30);
        LocalDate yearEnd = LocalDate.of(2026, 12, 31);

        // DAILY: 任何日期均可触发
        assertThat(PeriodCycleEnum.DAILY.isTriggerable(nonMonthEnd)).isTrue();
        assertThat(PeriodCycleEnum.DAILY.isTriggerable(monthEndNormal)).isTrue();

        // MONTHLY: 仅月末触发
        assertThat(PeriodCycleEnum.MONTHLY.isTriggerable(nonMonthEnd)).isFalse();
        assertThat(PeriodCycleEnum.MONTHLY.isTriggerable(monthEndNormal)).isTrue();

        // QUARTERLY: 仅季末月末触发
        assertThat(PeriodCycleEnum.QUARTERLY.isTriggerable(nonMonthEnd)).isFalse();
        assertThat(PeriodCycleEnum.QUARTERLY.isTriggerable(monthEndNormal)).isFalse(); // 10月非季末
        assertThat(PeriodCycleEnum.QUARTERLY.isTriggerable(quarterEnd)).isTrue();     // 9月为季末

        // YEARLY: 仅年末 12-31 触发
        assertThat(PeriodCycleEnum.YEARLY.isTriggerable(monthEndNormal)).isFalse();
        assertThat(PeriodCycleEnum.YEARLY.isTriggerable(quarterEnd)).isFalse();
        assertThat(PeriodCycleEnum.YEARLY.isTriggerable(yearEnd)).isTrue();

        // MANUAL: 自动任务始终不可触发
        assertThat(PeriodCycleEnum.MANUAL.isTriggerable(yearEnd)).isFalse();
    }

    @Test
    @DisplayName("自动结转执行：仅执行 autoTransfer=true 且满足当前日期周期的规则")
    @SuppressWarnings("unchecked")
    void executeAutoTransfer_filterAutoAndCycle() {
        LocalDate date = LocalDate.of(2026, 10, 8);

        PeriodEndTransferRulePO dailyAutoRule = new PeriodEndTransferRulePO();
        dailyAutoRule.setRuleCode("RULE_DAILY");
        dailyAutoRule.setRuleName("每日结转规则");
        dailyAutoRule.setSourceSubjectCode("60*");
        dailyAutoRule.setStatus(AvailableStatusEnum.ENABLED);
        dailyAutoRule.setAutoTransfer(true);
        dailyAutoRule.setPeriodCycle(PeriodCycleEnum.DAILY);

        when(ruleRepository.selectAutoTriggerableRules(date, null)).thenReturn(List.of(dailyAutoRule));
        when(accountBalanceRepository.selectByDate(date)).thenReturn(List.of());

        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(null);
        });

        List<PeriodEndTransferDomainService.TransferRuleResult> results =
                transferDomainService.executeAutoTransfer(date, null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getRuleCode()).isEqualTo("RULE_DAILY");
        assertThat(results.get(0).getStatus()).isEqualTo(TransferRecordStatusEnum.SUCCESS);
        verify(ruleRepository).selectAutoTriggerableRules(date, null);
    }
}


