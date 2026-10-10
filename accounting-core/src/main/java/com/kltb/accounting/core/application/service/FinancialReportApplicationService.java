package com.kltb.accounting.core.application.service;

import com.kltb.accounting.api.request.BalanceSheetQueryRequest;
import com.kltb.accounting.api.request.GeneralLedgerQueryRequest;
import com.kltb.accounting.api.request.IncomeStatementQueryRequest;
import com.kltb.accounting.api.request.SubsidiaryLedgerQueryRequest;
import com.kltb.accounting.api.response.BalanceSheetResponse;
import com.kltb.accounting.api.response.GeneralLedgerResponse;
import com.kltb.accounting.api.response.IncomeStatementResponse;
import com.kltb.accounting.api.response.SubsidiaryLedgerResponse;
import com.kltb.accounting.core.domain.service.FinancialReportDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 财务报表应用服务（Application Service）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialReportApplicationService {

    private final FinancialReportDomainService reportDomainService;
    private final com.kltb.accounting.core.infrastructure.persistence.repository.SubjectRepository subjectRepository;
    private final com.kltb.accounting.core.infrastructure.persistence.repository.FinancialReportSnapshotRepository snapshotRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * 查询资产负债表
     */
    public BalanceSheetResponse getBalanceSheet(BalanceSheetQueryRequest request) {
        log.info("[REPORT-APP] 查询资产负债表: date={}", request.getAccountingDate());
        return reportDomainService.generateBalanceSheet(request);
    }

    /**
     * 查询利润表
     */
    public IncomeStatementResponse getIncomeStatement(IncomeStatementQueryRequest request) {
        log.info("[REPORT-APP] 查询利润表: year={}, month={}, compareType={}",
                request.getYear(), request.getMonth(), request.getCompareType());
        return reportDomainService.generateIncomeStatement(request);
    }

    /**
     * 查询科目总账
     */
    public GeneralLedgerResponse getGeneralLedger(GeneralLedgerQueryRequest request) {
        log.info("[REPORT-APP] 查询科目总账: range={} ~ {}, level={}, kw={}",
                request.getStartDate(), request.getEndDate(), request.getSubjectLevel(), request.getKeyword());
        return reportDomainService.generateGeneralLedger(request);
    }

    /**
     * 查询科目明细账
     */
    public SubsidiaryLedgerResponse getSubsidiaryLedger(SubsidiaryLedgerQueryRequest request) {
        log.info("[REPORT-APP] 查询科目明细账: subject={}, account={}, range={} ~ {}",
                request.getSubjectCode(), request.getAccountNo(), request.getStartDate(), request.getEndDate());
        return reportDomainService.generateSubsidiaryLedger(request);
    }

    /**
     * 定时任务/手工触发：定期生成并持久化归档指定会计日期的所有财务报表
     *
     * @param accountingDate 会计日期
     */
    public void generateAndArchiveReports(LocalDate accountingDate) {
        log.info("[REPORT-JOB] 定期生成并归档财务报表开始: date={}", accountingDate);

        // 1. 生成资产负债表并持久化归档
        BalanceSheetQueryRequest bsReq = new BalanceSheetQueryRequest();
        bsReq.setAccountingDate(accountingDate);
        bsReq.setCompareYearStart(true);
        BalanceSheetResponse bsResp = reportDomainService.generateBalanceSheet(bsReq);
        log.info("[REPORT-JOB] 资产负债表生成完成: date={}, 平衡={}, 资产总计={}",
                accountingDate, bsResp.getBalanced(), bsResp.getTotalAssetEnd());

        try {
            com.kltb.accounting.core.infrastructure.persistence.entity.FinancialReportSnapshotPO bsSnapshot =
                    new com.kltb.accounting.core.infrastructure.persistence.entity.FinancialReportSnapshotPO();
            bsSnapshot.setReportType("BALANCE_SHEET");
            bsSnapshot.setReportName("资产负债表");
            bsSnapshot.setAccountingDate(accountingDate);
            bsSnapshot.setPeriodType("DAY");
            bsSnapshot.setTotalAsset(bsResp.getTotalAssetEnd());
            bsSnapshot.setTotalLiabilityEquity(bsResp.getTotalLiabilityAndEquityEnd());
            bsSnapshot.setIsBalanced(Boolean.TRUE.equals(bsResp.getBalanced()) ? 1 : 0);
            bsSnapshot.setReportContent(objectMapper.writeValueAsString(bsResp));
            snapshotRepository.saveOrUpdate(bsSnapshot);
            log.info("[REPORT-JOB] 资产负债表持久化归档成功: date={}", accountingDate);
        } catch (Exception e) {
            log.error("[REPORT-JOB] 资产负债表持久化归档失败: date={}, err={}", accountingDate, e.getMessage(), e);
        }

        // 2. 生成利润表并持久化归档
        IncomeStatementQueryRequest isReq = new IncomeStatementQueryRequest();
        isReq.setYear(accountingDate.getYear());
        isReq.setMonth(accountingDate.getMonthValue());
        isReq.setCompareType(1);
        IncomeStatementResponse isResp = reportDomainService.generateIncomeStatement(isReq);
        log.info("[REPORT-JOB] 利润表生成完成: period={}, 营收={}, 净利润={}",
                isResp.getPeriodDesc(), isResp.getKpi().getRevenueMonth(), isResp.getKpi().getNetProfitMonth());

        try {
            com.kltb.accounting.core.infrastructure.persistence.entity.FinancialReportSnapshotPO isSnapshot =
                    new com.kltb.accounting.core.infrastructure.persistence.entity.FinancialReportSnapshotPO();
            isSnapshot.setReportType("INCOME_STATEMENT");
            isSnapshot.setReportName("利润表");
            isSnapshot.setAccountingDate(accountingDate);
            isSnapshot.setPeriodType("MONTH");
            if (isResp.getKpi() != null) {
                isSnapshot.setTotalAsset(isResp.getKpi().getRevenueMonth());
                isSnapshot.setTotalLiabilityEquity(isResp.getKpi().getNetProfitMonth());
            }
            isSnapshot.setIsBalanced(1);
            isSnapshot.setReportContent(objectMapper.writeValueAsString(isResp));
            snapshotRepository.saveOrUpdate(isSnapshot);
            log.info("[REPORT-JOB] 利润表持久化归档成功: date={}", accountingDate);
        } catch (Exception e) {
            log.error("[REPORT-JOB] 利润表持久化归档失败: date={}, err={}", accountingDate, e.getMessage(), e);
        }

        log.info("[REPORT-JOB] 定期生成并归档财务报表完毕: date={}", accountingDate);
    }

    /**
     * 获取系统实际配置的所有科目级次列表
     */
    public java.util.List<Integer> getSubjectLevels() {
        return subjectRepository.selectDistinctLevels();
    }
}
