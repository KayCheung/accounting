package com.kltb.accounting.core.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kltb.accounting.api.response.DashboardOverviewResponse;
import com.kltb.accounting.core.domain.enums.*;
import com.kltb.accounting.core.domain.service.EodStatusDomainService;
import com.kltb.accounting.core.domain.service.TrialBalanceDomainService;
import com.kltb.accounting.core.domain.service.TrialBalanceDomainService.TrialBalanceResult;
import com.kltb.accounting.core.infrastructure.cache.AccountingDateCache;
import com.kltb.accounting.core.infrastructure.persistence.entity.*;
import com.kltb.accounting.core.infrastructure.persistence.mapper.*;
import com.kltb.accounting.core.infrastructure.persistence.repository.ManualVoucherApplyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 系统概览与运营看板应用服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardApplicationService {

    private final AccountingDateCache accountingDateCache;
    private final EodStatusDomainService eodStatusDomainService;
    private final TrialBalanceDomainService trialBalanceDomainService;
    private final ManualVoucherApplyRepository manualVoucherApplyRepository;
    private final ManualVoucherApplyMapper manualVoucherApplyMapper;
    private final AccountingVoucherMapper accountingVoucherMapper;
    private final BusinessRecordMapper businessRecordMapper;
    private final BufferPostingDetailMapper bufferPostingDetailMapper;
    private final AccountFreezeDetailMapper accountFreezeDetailMapper;
    private final AccountMapper accountMapper;

    /**
     * 获取系统概览大盘与待办数据
     *
     * @return 概览响应 DTO
     */
    public DashboardOverviewResponse getDashboardOverview() {
        LocalDate currentDate = accountingDateCache.getCurrentDate();
        if (currentDate == null) {
            currentDate = LocalDate.now();
        }

        DashboardOverviewResponse.DashboardOverviewResponseBuilder builder = DashboardOverviewResponse.builder();
        builder.accountingDate(currentDate.toString());

        // 1. 全局日切营业状态
        try {
            EodStatusPO po = eodStatusDomainService.findByDate(currentDate);
            if (po == null) {
                builder.eodStatus(EodStatusEnum.NOT_STARTED.getCode());
                builder.eodStatusDesc("正常营业");
            } else {
                builder.eodStatus(po.getStatusCode());
                String desc = (po.getStatusCode() == 1 || po.getStatusCode() == 8)
                        ? "正常营业"
                        : EodStatusEnum.getDescByCode(po.getStatusCode());
                builder.eodStatusDesc(desc);
            }
        } catch (Exception e) {
            log.warn("[DASHBOARD] 获取日切状态降级: {}", e.getMessage());
            builder.eodStatus(EodStatusEnum.NOT_STARTED.getCode());
            builder.eodStatusDesc("正常营业");
        }

        // 2. 试算平衡指示灯
        try {
            TrialBalanceResult trialResult = trialBalanceDomainService.executeTrialBalance(currentDate);
            if (trialResult != null) {
                builder.trialBalanced(trialResult.isPassed());
                builder.trialBalanceDiff(trialResult.getDiff() != null ? trialResult.getDiff() : BigDecimal.ZERO);
                builder.trialDebitTotal(trialResult.getTotalDebit() != null ? trialResult.getTotalDebit() : BigDecimal.ZERO);
                builder.trialCreditTotal(trialResult.getTotalCredit() != null ? trialResult.getTotalCredit() : BigDecimal.ZERO);
            } else {
                builder.trialBalanced(true);
                builder.trialBalanceDiff(BigDecimal.ZERO);
                builder.trialDebitTotal(BigDecimal.ZERO);
                builder.trialCreditTotal(BigDecimal.ZERO);
            }
        } catch (Exception e) {
            log.warn("[DASHBOARD] 试算平衡检测降级: {}", e.getMessage());
            builder.trialBalanced(true);
            builder.trialBalanceDiff(BigDecimal.ZERO);
            builder.trialDebitTotal(BigDecimal.ZERO);
            builder.trialCreditTotal(BigDecimal.ZERO);
        }

        // 3. 手工凭证待办事项统计（面向 MAKER / AUDITOR / REVIEWER / BOOKKEEPER）
        try {
            Long draftCount = manualVoucherApplyRepository.countByStatus(ManualVoucherApplyStatusEnum.DRAFT);
            builder.makerDraftCount(draftCount != null ? draftCount : 0L);

            Long rejectedCount = manualVoucherApplyMapper.selectCount(new LambdaQueryWrapper<ManualVoucherApplyPO>()
                    .in(ManualVoucherApplyPO::getApplyStatus,
                            List.of(ManualVoucherApplyStatusEnum.AUDIT_REJECTED, ManualVoucherApplyStatusEnum.REVIEW_REJECTED))
                    .eq(ManualVoucherApplyPO::getIsDelete, 0));
            builder.makerRejectedCount(rejectedCount != null ? rejectedCount : 0L);

            Long auditCount = manualVoucherApplyRepository.countByStatus(ManualVoucherApplyStatusEnum.PENDING_AUDIT);
            builder.pendingAuditCount(auditCount != null ? auditCount : 0L);

            Long reviewCount = manualVoucherApplyRepository.countByStatus(ManualVoucherApplyStatusEnum.PENDING_REVIEW);
            builder.pendingReviewCount(reviewCount != null ? reviewCount : 0L);

            Long bookCount = manualVoucherApplyRepository.countByStatus(ManualVoucherApplyStatusEnum.PENDING_BOOKKEEPING);
            builder.pendingBookkeepingCount(bookCount != null ? bookCount : 0L);
        } catch (Exception e) {
            log.warn("[DASHBOARD] 统计手工凭证待办降级: {}", e.getMessage());
            builder.makerDraftCount(0L);
            builder.makerRejectedCount(0L);
            builder.pendingAuditCount(0L);
            builder.pendingReviewCount(0L);
            builder.pendingBookkeepingCount(0L);
        }

        // 4. 异常治理待办统计（面向 OPERATOR / ADMIN）
        try {
            // 过账失败异常凭证数
            Long failedPostingCount = accountingVoucherMapper.selectCount(new LambdaQueryWrapper<AccountingVoucherPO>()
                    .eq(AccountingVoucherPO::getStatus, VoucherStatusEnum.FAILED.getCode())
                    .eq(AccountingVoucherPO::getIsDelete, 0));
            builder.failedPostingCount(failedPostingCount != null ? failedPostingCount : 0L);

            // 挂起/掉单待补偿流水笔数（PROCESSING）
            Long hangingJournalCount = businessRecordMapper.selectCount(new LambdaQueryWrapper<BusinessRecordPO>()
                    .eq(BusinessRecordPO::getStatus, BusinessRecordStatusEnum.PROCESSING.getCode())
                    .eq(BusinessRecordPO::getIsDelete, 0));
            builder.hangingJournalCount(hangingJournalCount != null ? hangingJournalCount : 0L);

            // 缓冲记账待入账存量明细笔数
            Long pendingBufferCount = bufferPostingDetailMapper.selectCount(new LambdaQueryWrapper<BufferPostingDetailPO>()
                    .eq(BufferPostingDetailPO::getStatus, BufferStatusEnum.PENDING.getCode())
                    .eq(BufferPostingDetailPO::getIsDelete, 0));
            builder.pendingBufferCount(pendingBufferCount != null ? pendingBufferCount : 0L);

            // 缓冲记账失败异常明细笔数
            Long failedBufferCount = bufferPostingDetailMapper.selectCount(new LambdaQueryWrapper<BufferPostingDetailPO>()
                    .eq(BufferPostingDetailPO::getStatus, BufferStatusEnum.FAILED.getCode())
                    .eq(BufferPostingDetailPO::getIsDelete, 0));
            builder.failedBufferCount(failedBufferCount != null ? failedBufferCount : 0L);

            // 资金冻结中工单数
            Long activeFreezeCount = accountFreezeDetailMapper.selectCount(new LambdaQueryWrapper<AccountFreezeDetailPO>()
                    .eq(AccountFreezeDetailPO::getStatus, FreezeStatusEnum.FROZEN.getCode())
                    .eq(AccountFreezeDetailPO::getIsDelete, 0));
            builder.activeFreezeCount(activeFreezeCount != null ? activeFreezeCount : 0L);
        } catch (Exception e) {
            log.warn("[DASHBOARD] 统计异常治理指标降级: {}", e.getMessage());
            builder.failedPostingCount(0L);
            builder.hangingJournalCount(0L);
            builder.pendingBufferCount(0L);
            builder.failedBufferCount(0L);
            builder.activeFreezeCount(0L);
        }

        // 5. 今日业务规模指标（面向 ADMIN / OPERATOR）
        try {
            // 今日生成凭证总数
            Long todayVoucherCount = accountingVoucherMapper.selectCount(new LambdaQueryWrapper<AccountingVoucherPO>()
                    .eq(AccountingVoucherPO::getAccountingDate, currentDate)
                    .eq(AccountingVoucherPO::getIsDelete, 0));
            builder.todayVoucherCount(todayVoucherCount != null ? todayVoucherCount : 0L);

            // 今日已成功过账凭证数
            Long todayPostedVoucherCount = accountingVoucherMapper.selectCount(new LambdaQueryWrapper<AccountingVoucherPO>()
                    .eq(AccountingVoucherPO::getAccountingDate, currentDate)
                    .eq(AccountingVoucherPO::getStatus, VoucherStatusEnum.POSTED.getCode())
                    .eq(AccountingVoucherPO::getIsDelete, 0));
            builder.todayPostedVoucherCount(todayPostedVoucherCount != null ? todayPostedVoucherCount : 0L);

            // 今日流水总数
            Long todayJournalCount = businessRecordMapper.selectCount(new LambdaQueryWrapper<BusinessRecordPO>()
                    .eq(BusinessRecordPO::getAccountingDate, currentDate)
                    .eq(BusinessRecordPO::getIsDelete, 0));
            builder.todayJournalCount(todayJournalCount != null ? todayJournalCount : 0L);

            // 正常有效分户账户总数
            Long activeAccountCount = accountMapper.selectCount(new LambdaQueryWrapper<AccountPO>()
                    .eq(AccountPO::getStatus, AccountStatusEnum.NORMAL.getCode())
                    .eq(AccountPO::getIsDelete, 0));
            builder.activeAccountCount(activeAccountCount != null ? activeAccountCount : 0L);
        } catch (Exception e) {
            log.warn("[DASHBOARD] 统计业务规模指标降级: {}", e.getMessage());
            builder.todayVoucherCount(0L);
            builder.todayPostedVoucherCount(0L);
            builder.todayJournalCount(0L);
            builder.activeAccountCount(0L);
        }

        return builder.build();
    }
}
