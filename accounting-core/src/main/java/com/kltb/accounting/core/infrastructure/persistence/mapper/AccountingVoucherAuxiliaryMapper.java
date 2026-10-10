// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/AccountingVoucherAuxiliaryMapper.java
package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kltb.accounting.core.infrastructure.persistence.bo.*;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherAuxiliaryPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 记账凭证辅助核算项目数据访问层。
 */
@Mapper
public interface AccountingVoucherAuxiliaryMapper extends BaseMapper<AccountingVoucherAuxiliaryPO> {

    /**
     * 批量插入辅助核算项（红冲用）
     */
    int batchInsert(@Param("list") List<AccountingVoucherAuxiliaryPO> list);

    /**
     * 查询指定核算类别下有凭证记录的去重核算项目及其统计
     */
    List<AuxiliaryItemStatBO> selectDistinctAuxItems(
            @Param("auxType") String auxType,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * 查询辅助核算账簿统计 KPI 概览指标
     */
    AuxiliarySummaryBO selectAuxiliarySummary(
            @Param("auxType") String auxType,
            @Param("auxCode") String auxCode,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("subjectCode") String subjectCode,
            @Param("subjectCategory") Integer subjectCategory,
            @Param("keyword") String keyword);

    /**
     * 查询辅助核算交叉对比矩阵单元格金额
     */
    List<AuxiliaryMatrixCellBO> selectMatrixCellAmounts(
            @Param("auxType") String auxType,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("subjectCode") String subjectCode,
            @Param("subjectCategory") Integer subjectCategory,
            @Param("keyword") String keyword);

    /**
     * 查询辅助核算科目分布明细
     */
    List<AuxiliarySubjectBreakdownBO> selectSubjectBreakdown(
            @Param("auxType") String auxType,
            @Param("auxCode") String auxCode,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("subjectCode") String subjectCode,
            @Param("subjectCategory") Integer subjectCategory,
            @Param("keyword") String keyword);

    /**
     * 分页查询辅助核算凭证分录流水明细
     */
    IPage<AuxiliaryEntryRecordBO> selectEntryRecords(
            IPage<AuxiliaryEntryRecordBO> page,
            @Param("auxType") String auxType,
            @Param("auxCode") String auxCode,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("subjectCode") String subjectCode,
            @Param("subjectCategory") Integer subjectCategory,
            @Param("keyword") String keyword);
}