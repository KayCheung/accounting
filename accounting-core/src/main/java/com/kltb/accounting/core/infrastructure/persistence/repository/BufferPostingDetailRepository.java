package com.kltb.accounting.core.infrastructure.persistence.repository;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.BufferStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.BufferPostingDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.BufferPostingDetailMapper;
import com.kltb.accounting.core.shared.exception.AccountException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 缓冲记账明细持久化仓储
 */
@Repository
@RequiredArgsConstructor
public class BufferPostingDetailRepository {

    private final BufferPostingDetailMapper bufferPostingDetailMapper;

    /**
     * 按 accountNo 查询待入账缓冲金额汇总
     */
    public BigDecimal sumPendingAmountByAccountNo(String accountNo) {
        BigDecimal result = bufferPostingDetailMapper.sumPendingAmountByAccountNo(accountNo);
        return result != null ? result : BigDecimal.ZERO;
    }

    /**
     * 批量查询待入账缓冲明细
     */
    public List<BufferPostingDetailPO> selectPendingByCondition(
            LocalDate accountingDate, Integer bufferMode, Integer status, int limit) {
        return bufferPostingDetailMapper.selectPendingByCondition(
                accountingDate, bufferMode, status, limit);
    }

    /**
     * 按账户汇总缓冲金额（GROUP BY）
     */
    public List<Map<String, Object>> sumByAccountNo(LocalDate accountingDate, Integer status) {
        return bufferPostingDetailMapper.sumByAccountNo(accountingDate, status);
    }

    /**
     * 查询某账户某会计日期最后一条缓冲明细
     */
    public BufferPostingDetailPO selectLastDetailByAccountAndDate(String accountNo, LocalDate accountingDate) {
        return bufferPostingDetailMapper.selectLastDetailByAccountAndDate(accountNo, accountingDate);
    }

    /**
     * 查询某会计日期已成功入账的缓冲明细（Running Balance 校验用）
     */
    public List<BufferPostingDetailPO> selectSuccessByDate(LocalDate accountingDate, int limit) {
        return bufferPostingDetailMapper.selectPendingByCondition(
                accountingDate, null, BufferStatusEnum.SUCCESS.getCode(), limit);
    }

    /**
     * 按分片值范围查询缓冲明细
     */
    public List<BufferPostingDetailPO> selectByShardingRange(
            Long shardingStart, Long shardingEnd, LocalDate accountingDate, Integer status, int limit) {
        return bufferPostingDetailMapper.selectByShardingRange(
                shardingStart, shardingEnd, accountingDate, status, limit);
    }

    /**
     * 按 ID 查询
     */
    public BufferPostingDetailPO selectById(Long id) {
        return bufferPostingDetailMapper.selectById(id);
    }

    /**
     * 更新为处理中
     */
    public void updateToProcessing(Long id) {
        BufferPostingDetailPO po = new BufferPostingDetailPO();
        po.setId(id);
        po.setStatus(BufferStatusEnum.PROCESSING);
        po.setStartTime(LocalDateTime.now());
        int affected = bufferPostingDetailMapper.updateById(po);
        if (affected == 0) {
            throw new AccountException(ResultCode.OPTIMISTIC_LOCK_FAILED,
                    "缓冲明细更新冲突: id=" + id);
        }
    }

    /**
     * 更新为成功
     */
    public void updateToSuccess(Long id, LocalDateTime completeTime) {
        BufferPostingDetailPO po = new BufferPostingDetailPO();
        po.setId(id);
        po.setStatus(BufferStatusEnum.SUCCESS);
        po.setCompleteTime(completeTime);
        int affected = bufferPostingDetailMapper.updateById(po);
        if (affected == 0) {
            throw new AccountException(ResultCode.OPTIMISTIC_LOCK_FAILED,
                    "缓冲明细更新冲突: id=" + id);
        }
    }

    /**
     * 更新为失败
     */
    public void updateToFailed(Long id, String failReason) {
        BufferPostingDetailPO po = new BufferPostingDetailPO();
        po.setId(id);
        po.setStatus(BufferStatusEnum.FAILED);
        po.setFailReason(failReason);
        po.setCompleteTime(LocalDateTime.now());
        int affected = bufferPostingDetailMapper.updateById(po);
        if (affected == 0) {
            throw new AccountException(ResultCode.OPTIMISTIC_LOCK_FAILED,
                    "缓冲明细更新冲突: id=" + id);
        }
    }

    /**
     * 增加重试次数
     */
    public void incrementRetryCount(Long id, String failReason) {
        BufferPostingDetailPO po = bufferPostingDetailMapper.selectById(id);
        if (po != null) {
            po.setRetryCount((po.getRetryCount() != null ? po.getRetryCount() : 0) + 1);
            po.setFailReason(failReason);
            po.setCompleteTime(LocalDateTime.now());
            bufferPostingDetailMapper.updateById(po);
        }
    }

    /**
     * 按会计日期和状态统计缓冲明细数量（Step 17 P0-1）
     */
    public int countByAccountingDateAndStatus(LocalDate accountingDate, Integer status) {
        return bufferPostingDetailMapper.countByAccountingDateAndStatus(accountingDate, status);
    }

    /**
     * 插入缓冲明细
     */
     public void insert(BufferPostingDetailPO po) {
         bufferPostingDetailMapper.insert(po);
     }

    /**
     * 按系统跟踪号查询缓冲记账明细列表
     */
    public List<BufferPostingDetailPO> selectByTraceNo(String traceNo) {
        if (StrUtil.isBlank(traceNo)) {
            return Collections.emptyList();
        }
        return bufferPostingDetailMapper.selectList(
                new LambdaQueryWrapper<BufferPostingDetailPO>()
                        .eq(BufferPostingDetailPO::getTraceNo, traceNo)
                        .eq(BufferPostingDetailPO::getIsDelete, 0));
    }

    /**
     * 按凭证号查询缓冲记账明细列表
     */
    public List<BufferPostingDetailPO> selectByVoucherNo(String voucherNo) {
        if (StrUtil.isBlank(voucherNo)) {
            return Collections.emptyList();
        }
        return bufferPostingDetailMapper.selectList(
                new LambdaQueryWrapper<BufferPostingDetailPO>()
                        .eq(BufferPostingDetailPO::getVoucherNo, voucherNo)
                        .eq(BufferPostingDetailPO::getIsDelete, 0));
    }

    /**
     * 批量作废缓冲记账明细（流水异常回滚使用，防止 Job 继续批处理入账）
     */
    public void markFailedByTraceNo(String traceNo, String reason) {
        if (StrUtil.isBlank(traceNo)) {
            return;
        }
        List<BufferPostingDetailPO> list = selectByTraceNo(traceNo);
        for (BufferPostingDetailPO detail : list) {
            if (detail.getStatus() == BufferStatusEnum.PENDING || detail.getStatus() == BufferStatusEnum.PROCESSING) {
                updateToFailed(detail.getId(), reason);
            }
        }
    }

    /**
     * 分页多条件查询缓冲记账明细
     */
    public IPage<BufferPostingDetailPO> selectPageByCondition(
            LocalDate accountingDate, String accountNo, String voucherNo,
            Integer bufferMode, Integer status, LocalDate startDate, LocalDate endDate,
            long pageNo, long pageSize) {
        return bufferPostingDetailMapper.selectPageByCondition(
                accountingDate, accountNo, voucherNo, bufferMode, status, startDate, endDate, pageNo, pageSize);
    }
}
