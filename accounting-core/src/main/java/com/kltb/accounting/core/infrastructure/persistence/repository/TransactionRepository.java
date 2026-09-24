// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/repository/TransactionRepository.java
package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kltb.accounting.core.domain.enums.TransactionStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.TransactionPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.TransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 事务持久化仓储
 */
@Repository
@RequiredArgsConstructor
public class TransactionRepository {

    private final TransactionMapper transactionMapper;

    /**
     * 按 traceNo 查询事务记录
     */
    public TransactionPO selectByTraceNo(String traceNo) {
        return transactionMapper.selectByTraceNo(traceNo);
    }

    /**
     * 保存事务记录
     */
    public void save(TransactionPO transaction) {
        transactionMapper.insert(transaction);
    }

    /**
     * 按事务编号更新事务状态
     */
    public void updateStatusByTxnNo(String txnNo, TransactionStatusEnum status,
                                     String failReason, LocalDateTime finishTime) {
        LambdaUpdateWrapper<TransactionPO> wrapper = new LambdaUpdateWrapper<TransactionPO>()
                .eq(TransactionPO::getTxnNo, txnNo)
                .set(TransactionPO::getStatus, status != null ? status.getCode() : null);
        if (failReason != null) {
            wrapper.set(TransactionPO::getFailReason, failReason);
        }
        if (finishTime != null) {
            wrapper.set(TransactionPO::getFinishTime, finishTime);
        }
        transactionMapper.update(null, wrapper);
    }

    /**
     * 按事务编号查询事务
     */
    public TransactionPO selectByTxnNo(String txnNo) {
        return transactionMapper.selectByTxnNo(txnNo);
    }

    /**
     * 按事务状态和日期范围统计数量（Step 12 P0-4）
     */
    public int countByStatusAndDateRange(Integer status, LocalDate startDate, LocalDate endDate) {
        return transactionMapper.countByStatusAndDateRange(status, startDate, endDate);
    }

    /**
     * 按会计日期和状态统计事务数量（Step 17 P0-5）
     */
    public int countByAccountingDateAndStatus(LocalDate accountingDate, Integer status) {
        LambdaQueryWrapper<TransactionPO> wrapper = new LambdaQueryWrapper<TransactionPO>()
                .eq(TransactionPO::getAccountingDate, accountingDate)
                .eq(TransactionPO::getIsDelete, 0);
        if (status != null) {
            wrapper.eq(TransactionPO::getStatus, status);
        }
        return transactionMapper.selectCount(wrapper).intValue();
    }

    /**
     * 查询事务耗时统计（Step 12 P0-4）
     */
    public Map<String, Object> selectDurationStats(LocalDate startDate, LocalDate endDate, String businessCode) {
        return transactionMapper.selectDurationStats(startDate, endDate, businessCode);
    }
}
