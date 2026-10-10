package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.api.request.AuxiliaryLedgerQueryRequest;
import com.kltb.accounting.core.infrastructure.persistence.bo.*;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherAuxiliaryPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountingVoucherAuxiliaryMapper;
import com.kltb.accounting.core.shared.exception.AccountException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 记账凭证辅助核算项仓储层
 */
@Repository
@RequiredArgsConstructor
public class AccountingVoucherAuxiliaryRepository {

    private final AccountingVoucherAuxiliaryMapper auxiliaryMapper;

    /**
     * 查询指定辅助核算类别下的去重项目列表与发生统计
     */
    public List<AuxiliaryItemStatBO> selectDistinctItems(String auxType, LocalDate startDate, LocalDate endDate) {
        if (auxType == null || auxType.isBlank()) {
            return Collections.emptyList();
        }
        List<AuxiliaryItemStatBO> list = auxiliaryMapper.selectDistinctAuxItems(auxType, startDate, endDate);
        return list != null ? list : Collections.emptyList();
    }

    /**
     * 查询多维辅助核算账簿统计 KPI 概览指标
     */
    public AuxiliarySummaryBO selectSummary(AuxiliaryLedgerQueryRequest request) {
        if (request == null || request.getAuxType() == null || request.getAuxType().isBlank()) {
            return new AuxiliarySummaryBO();
        }
        AuxiliarySummaryBO summary = auxiliaryMapper.selectAuxiliarySummary(
                request.getAuxType(),
                request.getAuxCode(),
                request.getStartDate(),
                request.getEndDate(),
                request.getSubjectCode(),
                request.getSubjectCategory(),
                request.getKeyword()
        );
        return summary != null ? summary : new AuxiliarySummaryBO();
    }

    /**
     * 查询辅助核算交叉矩阵单元格金额
     */
    public List<AuxiliaryMatrixCellBO> selectMatrixCellAmounts(AuxiliaryLedgerQueryRequest request) {
        if (request == null || request.getAuxType() == null || request.getAuxType().isBlank()) {
            return Collections.emptyList();
        }
        List<AuxiliaryMatrixCellBO> list = auxiliaryMapper.selectMatrixCellAmounts(
                request.getAuxType(),
                request.getStartDate(),
                request.getEndDate(),
                request.getSubjectCode(),
                request.getSubjectCategory(),
                request.getKeyword()
        );
        return list != null ? list : Collections.emptyList();
    }

    /**
     * 查询辅助核算科目分布明细
     */
    public List<AuxiliarySubjectBreakdownBO> selectSubjectBreakdown(AuxiliaryLedgerQueryRequest request) {
        if (request == null || request.getAuxType() == null || request.getAuxType().isBlank()) {
            return Collections.emptyList();
        }
        List<AuxiliarySubjectBreakdownBO> list = auxiliaryMapper.selectSubjectBreakdown(
                request.getAuxType(),
                request.getAuxCode(),
                request.getStartDate(),
                request.getEndDate(),
                request.getSubjectCode(),
                request.getSubjectCategory(),
                request.getKeyword()
        );
        return list != null ? list : Collections.emptyList();
    }

    /**
     * 分页查询辅助凭证分录记录
     */
    public IPage<AuxiliaryEntryRecordBO> selectEntryPage(IPage<AuxiliaryEntryRecordBO> page, AuxiliaryLedgerQueryRequest request) {
        if (request == null || request.getAuxType() == null || request.getAuxType().isBlank()) {
            return page;
        }
        return auxiliaryMapper.selectEntryRecords(
                page,
                request.getAuxType(),
                request.getAuxCode(),
                request.getStartDate(),
                request.getEndDate(),
                request.getSubjectCode(),
                request.getSubjectCategory(),
                request.getKeyword()
        );
    }

    /**
     * 单条插入辅助核算项
     */
    public void insert(AccountingVoucherAuxiliaryPO po) {
        if (po == null) {
            throw new AccountException(ResultCode.PARAM_ERROR, "待插入辅助核算项不能为空");
        }
        int affected = auxiliaryMapper.insert(po);
        if (affected == 0) {
            throw new AccountException(ResultCode.SYSTEM_ERROR, "插入辅助核算项失败: entryId=" + po.getEntryId());
        }
    }

    /**
     * 批量插入辅助核算项
     */
    public void batchInsert(List<AccountingVoucherAuxiliaryPO> list) {
        if (list != null && !list.isEmpty()) {
            auxiliaryMapper.batchInsert(list);
        }
    }

    /**
     * 按凭证号查询关联的辅助核算项
     */
    public List<AccountingVoucherAuxiliaryPO> selectByVoucherNo(String voucherNo) {
        if (voucherNo == null || voucherNo.isBlank()) {
            return Collections.emptyList();
        }
        return auxiliaryMapper.selectList(new LambdaQueryWrapper<AccountingVoucherAuxiliaryPO>()
                .eq(AccountingVoucherAuxiliaryPO::getVoucherNo, voucherNo)
                .eq(AccountingVoucherAuxiliaryPO::getIsDelete, 0));
    }

    /**
     * 按分录ID集合批量查询关联的辅助核算项
     */
    public List<AccountingVoucherAuxiliaryPO> selectByEntryIds(Collection<String> entryIds) {
        if (entryIds == null || entryIds.isEmpty()) {
            return Collections.emptyList();
        }
        return auxiliaryMapper.selectList(new LambdaQueryWrapper<AccountingVoucherAuxiliaryPO>()
                .in(AccountingVoucherAuxiliaryPO::getEntryId, entryIds)
                .eq(AccountingVoucherAuxiliaryPO::getIsDelete, 0));
    }
}
