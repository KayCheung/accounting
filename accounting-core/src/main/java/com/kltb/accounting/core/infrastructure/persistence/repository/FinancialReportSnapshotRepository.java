package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.FinancialReportSnapshotPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.FinancialReportSnapshotMapper;
import com.kltb.accounting.core.shared.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 财务报表归档快照仓储
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class FinancialReportSnapshotRepository {

    private final FinancialReportSnapshotMapper snapshotMapper;

    /**
     * 保存或更新报表快照（幂等归档）
     *
     * @param snapshot 快照实体
     */
    public void saveOrUpdate(FinancialReportSnapshotPO snapshot) {
        if (snapshot == null) {
            return;
        }
        Integer tenantId = TenantContext.get();
        if (snapshot.getTenantId() == null) {
            snapshot.setTenantId(tenantId != null ? tenantId : TenantContext.SYSTEM_TENANT);
        }

        LambdaQueryWrapper<FinancialReportSnapshotPO> wrapper = new LambdaQueryWrapper<FinancialReportSnapshotPO>()
                .eq(FinancialReportSnapshotPO::getReportType, snapshot.getReportType())
                .eq(FinancialReportSnapshotPO::getAccountingDate, snapshot.getAccountingDate())
                .eq(FinancialReportSnapshotPO::getPeriodType, snapshot.getPeriodType())
                .eq(FinancialReportSnapshotPO::getIsDelete, 0L);

        FinancialReportSnapshotPO existing = snapshotMapper.selectOne(wrapper, false);
        if (existing != null) {
            existing.setReportContent(snapshot.getReportContent());
            existing.setTotalAsset(snapshot.getTotalAsset());
            existing.setTotalLiabilityEquity(snapshot.getTotalLiabilityEquity());
            existing.setIsBalanced(snapshot.getIsBalanced());
            existing.setReportName(snapshot.getReportName());
            snapshotMapper.updateById(existing);
            log.info("[REPORT-SNAPSHOT] 更新已有报表归档快照: id={}, type={}, date={}, period={}",
                    existing.getId(), existing.getReportType(), existing.getAccountingDate(), existing.getPeriodType());
        } else {
            snapshotMapper.insert(snapshot);
            log.info("[REPORT-SNAPSHOT] 新增报表归档快照: id={}, type={}, date={}, period={}",
                    snapshot.getId(), snapshot.getReportType(), snapshot.getAccountingDate(), snapshot.getPeriodType());
        }
    }

    /**
     * 查询指定日期和类型的报表快照
     *
     * @param reportType     报表类型
     * @param accountingDate 会计日期
     * @param periodType     期间类型
     * @return 快照实体，不存在时返回 null
     */
    public FinancialReportSnapshotPO findSnapshot(String reportType, LocalDate accountingDate, String periodType) {
        if (accountingDate == null || reportType == null) {
            return null;
        }
        String pType = Objects.requireNonNullElse(periodType, "DAY");
        return snapshotMapper.selectOne(new LambdaQueryWrapper<FinancialReportSnapshotPO>()
                .eq(FinancialReportSnapshotPO::getReportType, reportType)
                .eq(FinancialReportSnapshotPO::getAccountingDate, accountingDate)
                .eq(FinancialReportSnapshotPO::getPeriodType, pType)
                .eq(FinancialReportSnapshotPO::getIsDelete, 0L), false);
    }
}
