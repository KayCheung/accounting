// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/TransactionMapper.java
package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.TransactionPO;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.Map;

/**
 * 事务 Mapper
 * <p>
 * 对应表：t_transaction
 */
@Mapper
public interface TransactionMapper extends BaseMapper<TransactionPO> {

    /**
     * 按 traceNo 查询事务记录
     */
    default TransactionPO selectByTraceNo(String traceNo) {
        return this.selectOne(new LambdaQueryWrapper<TransactionPO>()
                .eq(TransactionPO::getTraceNo, traceNo)
                .eq(TransactionPO::getIsDelete, 0)
                .last("LIMIT 1"));
    }

    /**
     * 按事务编号查询事务
     */
    default TransactionPO selectByTxnNo(String txnNo) {
        return this.selectOne(new LambdaQueryWrapper<TransactionPO>()
                .eq(TransactionPO::getTxnNo, txnNo)
                .eq(TransactionPO::getIsDelete, 0)
                .last("LIMIT 1"));
    }

    /**
     * 按事务状态和日期范围统计数量（Step 12 P0-3）
     */
    default int countByStatusAndDateRange(
            Integer status, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<TransactionPO> wrapper = new LambdaQueryWrapper<TransactionPO>()
                .eq(TransactionPO::getIsDelete, 0);
        if (status != null) {
            wrapper.eq(TransactionPO::getStatus, status);
        }
        if (startDate != null) {
            wrapper.ge(TransactionPO::getAccountingDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(TransactionPO::getAccountingDate, endDate);
        }
        return this.selectCount(wrapper).intValue();
    }

    /**
     * 查询事务耗时统计（AVG/MAX/MIN，仅统计已完成事务）（Step 12 P0-3）
     */
    Map<String, Object> selectDurationStats(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        @Param("businessCode") String businessCode);
}
