package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.infrastructure.persistence.entity.EodStatusPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.EodStatusMapper;
import com.kltb.accounting.core.shared.exception.AccountException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 日切状态持久化仓储（Step 17S 新增）
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class EodStatusRepository {

    private final EodStatusMapper eodStatusMapper;

    /**
     * 创建日切状态记录（支持日切失败重试幂等重置）
     *
     * @param accountingDate 会计日期
     * @return 状态PO
     */
    public EodStatusPO createStatus(LocalDate accountingDate) {
        EodStatusPO existing = findByDate(accountingDate);
        if (existing != null) {
            if (existing.getEodStatus() != null && existing.getEodStatus() == 8) {
                throw new AccountException(ResultCode.EOD_ALREADY_EXECUTED,
                        "会计日 [" + accountingDate + "] 日切已完成，不可重复执行");
            }
            log.info("[EOD-STATUS] 会计日 [{}] 已存在日切记录(status={})，重置状态进行重试",
                    accountingDate, existing.getEodStatus());
            resetStatus(accountingDate);
            existing.setEodStatus(1);
            existing.setFailedStage("");
            existing.setFailReason("");
            existing.setTotalDurationMs(0L);
            existing.setArchiveDateTime(null);
            return existing;
        }

        EodStatusPO po = new EodStatusPO();
        po.setAccountingDate(accountingDate);
        po.setEodStatus(1);
        po.setTotalDurationMs(0L);
        try {
            eodStatusMapper.insert(po);
            return po;
        } catch (DuplicateKeyException e) {
            log.warn("[EOD-STATUS] 并发创建日切状态冲突，转为重试重置模式: date={}", accountingDate);
            EodStatusPO retryPo = findByDate(accountingDate);
            if (retryPo != null) {
                if (retryPo.getEodStatus() != null && retryPo.getEodStatus() == 8) {
                    throw new AccountException(ResultCode.EOD_ALREADY_EXECUTED,
                            "会计日 [" + accountingDate + "] 日切已完成，不可重复执行");
                }
                resetStatus(accountingDate);
                retryPo.setEodStatus(1);
                retryPo.setFailedStage("");
                retryPo.setFailReason("");
                retryPo.setTotalDurationMs(0L);
                retryPo.setArchiveDateTime(null);
                return retryPo;
            }
            throw e;
        }
    }

    /**
     * 重置日切状态记录（用于失败后重试）
     *
     * @param accountingDate 会计日期
     */
    public void resetStatus(LocalDate accountingDate) {
        int affected = eodStatusMapper.update(null,
                new LambdaUpdateWrapper<EodStatusPO>()
                        .eq(EodStatusPO::getAccountingDate, accountingDate)
                        .set(EodStatusPO::getEodStatus, 1)
                        .set(EodStatusPO::getFailedStage, "")
                        .set(EodStatusPO::getFailReason, "")
                        .set(EodStatusPO::getTotalDurationMs, 0L)
                        .set(EodStatusPO::getArchiveDateTime, null));
        if (affected == 0) {
            throw new IllegalStateException("日切状态记录不存在: accountingDate=" + accountingDate);
        }
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
