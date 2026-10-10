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
     * 定时任务/手工触发：定期生成并预热指定会计日期的所有财务报表
     *
     * @param accountingDate 会计日期
     */
    public void generateAndArchiveReports(LocalDate accountingDate) {
        log.info("[REPORT-JOB] 定期生成并预热财务报表开始: date={}", accountingDate);

        // 1. 生成资产负债表
        BalanceSheetQueryRequest bsReq = new BalanceSheetQueryRequest();
        bsReq.setAccountingDate(accountingDate);
        bsReq.setCompareYearStart(true);
        BalanceSheetResponse bsResp = reportDomainService.generateBalanceSheet(bsReq);
        log.info("[REPORT-JOB] 资产负债表生成完成: date={}, 平衡={}, 资产总计={}",
                accountingDate, bsResp.getBalanced(), bsResp.getTotalAssetEnd());

        // 2. 生成利润表
        IncomeStatementQueryRequest isReq = new IncomeStatementQueryRequest();
        isReq.setYear(accountingDate.getYear());
        isReq.setMonth(accountingDate.getMonthValue());
        isReq.setCompareType(1);
        IncomeStatementResponse isResp = reportDomainService.generateIncomeStatement(isReq);
        log.info("[REPORT-JOB] 利润表生成完成: period={}, 营收={}, 净利润={}",
                isResp.getPeriodDesc(), isResp.getKpi().getRevenueMonth(), isResp.getKpi().getNetProfitMonth());

        log.info("[REPORT-JOB] 定期生成并预热财务报表完毕: date={}", accountingDate);
    }

    /**
     * 获取系统实际配置的所有科目级次列表
     */
    public java.util.List<Integer> getSubjectLevels() {
        return subjectRepository.selectDistinctLevels();
    }
}
