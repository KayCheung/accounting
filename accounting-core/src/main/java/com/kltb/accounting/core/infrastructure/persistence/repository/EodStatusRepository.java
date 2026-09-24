package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.EodStatusPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.EodStatusMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 日切状态持久化仓储（Step 17S 新增）
 */
@Repository
@RequiredArgsConstructor
public class EodStatusRepository {

    private final EodStatusMapper eodStatusMapper;

    /**
     * 创建日切状态记录（初始状态=1 未开始）
     *
     * @param accountingDate 会计日期
     * @return 新创建的PO
     */
    public EodStatusPO createStatus(LocalDate accountingDate) {
        EodStatusPO po = new EodStatusPO();
        po.setAccountingDate(accountingDate);
        po.setEodStatus(1);
        po.setTotalDurationMs(0L);
        eodStatusMapper.insert(po);
        return po;
    }

    /**
     * 按会计日期查询日切状态
     *
     * @param accountingDate 会计日期
     * @return 状态PO，不存在时返回null
     */
    public EodStatusPO findByDate(LocalDate accountingDate) {
        return eodStatusMapper.selectOne(
                new LambdaQueryWrapper<EodStatusPO>()
                        .eq(EodStatusPO::getAccountingDate, accountingDate),
                false);
    }

    /**
     * 查询最近一条已完成的日切记录
     *
     * @return 状态PO，无记录时返回null
     */
    public EodStatusPO findLatestCompleted() {
        return eodStatusMapper.selectOne(
                new LambdaQueryWrapper<EodStatusPO>()
                        .eq(EodStatusPO::getEodStatus, 8)
                        .orderByDesc(EodStatusPO::getAccountingDate)
                        .last("LIMIT 1"),
                false);
    }

    /**
     * 更新日切阶段状态
     *
     * @param accountingDate 会计日期
     * @param status         新状态值
     */
    public void updateStatus(LocalDate accountingDate, int status) {
        int affected = eodStatusMapper.update(null,
                new LambdaUpdateWrapper<EodStatusPO>()
                        .eq(EodStatusPO::getAccountingDate, accountingDate)
                        .set(EodStatusPO::getEodStatus, status));
        if (affected == 0) {
            throw new IllegalStateException("日切状态记录不存在: accountingDate=" + accountingDate);
        }
    }

    /**
     * 标记日切完成
     *
     * @param accountingDate 会计日期
     * @param durationMs     总耗时（毫秒）
     */
    public void markCompleted(LocalDate accountingDate, long durationMs) {
        int affected = eodStatusMapper.update(null,
                new LambdaUpdateWrapper<EodStatusPO>()
                        .eq(EodStatusPO::getAccountingDate, accountingDate)
                        .set(EodStatusPO::getEodStatus, 8)
                        .set(EodStatusPO::getArchiveDateTime, LocalDateTime.now())
                        .set(EodStatusPO::getTotalDurationMs, durationMs));
        if (affected == 0) {
            throw new IllegalStateException("日切状态记录不存在: accountingDate=" + accountingDate);
        }
    }

    /**
     * 标记日切失败
     *
     * @param accountingDate 会计日期
     * @param failedStage    失败阶段
     * @param failReason     失败原因
     */
    public void markFailed(LocalDate accountingDate, String failedStage, String failReason) {
        int affected = eodStatusMapper.update(null,
                new LambdaUpdateWrapper<EodStatusPO>()
                        .eq(EodStatusPO::getAccountingDate, accountingDate)
                        .set(EodStatusPO::getEodStatus, 9)
                        .set(EodStatusPO::getFailedStage, failedStage)
                        .set(EodStatusPO::getFailReason, failReason));
        if (affected == 0) {
            throw new IllegalStateException("日切状态记录不存在: accountingDate=" + accountingDate);
        }
    }

    /**
     * 更新切日完成时间
     *
     * @param accountingDate 会计日期
     * @param switchDateTime 切日完成时间
     */
    public void updateSwitchDateTime(LocalDate accountingDate, LocalDateTime switchDateTime) {
        int affected = eodStatusMapper.update(null,
                new LambdaUpdateWrapper<EodStatusPO>()
                        .eq(EodStatusPO::getAccountingDate, accountingDate)
                        .set(EodStatusPO::getSwitchDateTime, switchDateTime));
        if (affected == 0) {
            throw new IllegalStateException("日切状态记录不存在: accountingDate=" + accountingDate);
        }
    }
}
