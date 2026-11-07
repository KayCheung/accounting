package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.core.domain.enums.TransferRecordStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.PeriodEndTransferRecordPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.PeriodEndTransferRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 期末结转记录仓储
 */
@Repository
@RequiredArgsConstructor
public class PeriodEndTransferRecordRepository {

    private final PeriodEndTransferRecordMapper recordMapper;

    /**
     * 按会计日期查询结转记录
     */
    public List<PeriodEndTransferRecordPO> selectByAccountingDate(LocalDate accountingDate) {
        return recordMapper.selectByAccountingDate(accountingDate);
    }

    /**
     * 检查同日同规则是否存在成功执行记录
     */
    public boolean existsSuccessfulTransfer(LocalDate accountingDate, String ruleCode) {
        Long count = recordMapper.selectCount(new LambdaQueryWrapper<PeriodEndTransferRecordPO>()
                .eq(PeriodEndTransferRecordPO::getAccountingDate, accountingDate)
                .eq(PeriodEndTransferRecordPO::getRuleCode, ruleCode)
                .eq(PeriodEndTransferRecordPO::getStatus, TransferRecordStatusEnum.SUCCESS)
                .eq(PeriodEndTransferRecordPO::getIsDelete, 0));
        return count != null && count > 0;
    }

    /**
     * 分页查询结转记录
     */
    public Page<PeriodEndTransferRecordPO> selectPage(int pageNo, int pageSize,
                                                      LocalDate startDate, LocalDate endDate,
                                                      String transferNo, String ruleCode,
                                                      String voucherNo, Integer status) {
        Page<PeriodEndTransferRecordPO> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<PeriodEndTransferRecordPO> wrapper = new LambdaQueryWrapper<PeriodEndTransferRecordPO>()
                .eq(PeriodEndTransferRecordPO::getIsDelete, 0)
                .ge(startDate != null, PeriodEndTransferRecordPO::getAccountingDate, startDate)
                .le(endDate != null, PeriodEndTransferRecordPO::getAccountingDate, endDate)
                .eq(transferNo != null && !transferNo.isBlank(), PeriodEndTransferRecordPO::getTransferNo, transferNo)
                .eq(ruleCode != null && !ruleCode.isBlank(), PeriodEndTransferRecordPO::getRuleCode, ruleCode)
                .eq(voucherNo != null && !voucherNo.isBlank(), PeriodEndTransferRecordPO::getVoucherNo, voucherNo)
                .orderByDesc(PeriodEndTransferRecordPO::getAccountingDate)
                .orderByDesc(PeriodEndTransferRecordPO::getId);

        if (status != null) {
            wrapper.eq(PeriodEndTransferRecordPO::getStatus, TransferRecordStatusEnum.fromCode(status));
        }

        return recordMapper.selectPage(page, wrapper);
    }

    /**
     * 按结转流水号查询记录
     */
    public PeriodEndTransferRecordPO findByTransferNo(String transferNo) {
        return recordMapper.selectOne(new LambdaQueryWrapper<PeriodEndTransferRecordPO>()
                .eq(PeriodEndTransferRecordPO::getTransferNo, transferNo)
                .eq(PeriodEndTransferRecordPO::getIsDelete, 0), false);
    }

    /**
     * 插入结转记录
     */
    public void insert(PeriodEndTransferRecordPO record) {
        recordMapper.insert(record);
    }

    /**
     * 逻辑删除结转记录（用于强制重试时清理）
     */
    public int deleteByDateAndRule(LocalDate accountingDate, String ruleCode) {
        return recordMapper.update(null, new LambdaUpdateWrapper<PeriodEndTransferRecordPO>()
                .eq(PeriodEndTransferRecordPO::getAccountingDate, accountingDate)
                .eq(PeriodEndTransferRecordPO::getRuleCode, ruleCode)
                .eq(PeriodEndTransferRecordPO::getIsDelete, 0)
                .set(PeriodEndTransferRecordPO::getIsDelete, System.currentTimeMillis()));
    }
}
