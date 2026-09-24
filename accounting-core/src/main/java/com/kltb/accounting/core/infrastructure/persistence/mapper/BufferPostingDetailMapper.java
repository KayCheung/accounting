// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/BufferPostingDetailMapper.java
package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.BufferPostingDetailPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 缓冲记账明细表数据访问层。
 */
@Mapper
public interface BufferPostingDetailMapper extends BaseMapper<BufferPostingDetailPO> {

    /**
     * 按分片值查询缓冲记账明细
     * <p>
     * 用于分片扫描，同一账户必须在同一分片。
     *
     * @param sharding 分片值
     * @param status 状态（可选）
     * @return 缓冲记账明细列表
     */
    default java.util.List<BufferPostingDetailPO> selectBySharding(@Param("sharding") Long sharding,
                                                                    @Param("status") Integer status) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BufferPostingDetailPO> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BufferPostingDetailPO>()
                        .eq(BufferPostingDetailPO::getSharding, sharding)
                        .eq(BufferPostingDetailPO::getIsDelete, 0);
        if (status != null) {
            wrapper.eq(BufferPostingDetailPO::getStatus, status);
        }
        return this.selectList(wrapper);
    }

    /**
     * 按 accountNo 查询待入账缓冲金额汇总（SQL 层聚合）
     * <p>
     * 功能描述：汇总指定账户所有 PENDING 和 PROCESSING 状态的缓冲金额。
     * <p>
     * 是否记账：否，纯查询操作。
     * <p>
     * 异常处理：无数据时返回 BigDecimal.ZERO（COALESCE）。
     *
     * @param accountNo 账户编号
     * @return 待入账缓冲金额总和
     */
    BigDecimal sumPendingAmountByAccountNo(@Param("accountNo") String accountNo);

    /**
     * 批量查询待入账缓冲明细（按会计日期 + 模式 + 状态过滤）
     */
    default List<BufferPostingDetailPO> selectPendingByCondition(
            LocalDate accountingDate, Integer bufferMode, Integer status, int limit) {
        LambdaQueryWrapper<BufferPostingDetailPO> wrapper = new LambdaQueryWrapper<BufferPostingDetailPO>()
                .eq(BufferPostingDetailPO::getAccountingDate, accountingDate)
                .eq(BufferPostingDetailPO::getIsDelete, 0)
                .orderByAsc(BufferPostingDetailPO::getId)
                .last("LIMIT " + limit);
        if (bufferMode != null) {
            wrapper.eq(BufferPostingDetailPO::getBufferMode, bufferMode);
        }
        if (status != null) {
            wrapper.eq(BufferPostingDetailPO::getStatus, status);
        }
        return this.selectList(wrapper);
    }

    /**
     * 查询某账户某会计日期最后一条缓冲明细（Running Balance 校验用）
     */
    default BufferPostingDetailPO selectLastDetailByAccountAndDate(
            String accountNo, LocalDate accountingDate) {
        return this.selectOne(new LambdaQueryWrapper<BufferPostingDetailPO>()
                .eq(BufferPostingDetailPO::getAccountNo, accountNo)
                .eq(BufferPostingDetailPO::getAccountingDate, accountingDate)
                .eq(BufferPostingDetailPO::getIsDelete, 0)
                .orderByDesc(BufferPostingDetailPO::getTradeTime)
                .orderByDesc(BufferPostingDetailPO::getId)
                .last("LIMIT 1"));
    }

    /**
     * 按分片值范围查询缓冲明细（用于 Job 分片扫描）
     */
    default List<BufferPostingDetailPO> selectByShardingRange(
            Long shardingStart, Long shardingEnd, LocalDate accountingDate, Integer status, int limit) {
        LambdaQueryWrapper<BufferPostingDetailPO> wrapper = new LambdaQueryWrapper<BufferPostingDetailPO>()
                .ge(BufferPostingDetailPO::getSharding, shardingStart)
                .le(BufferPostingDetailPO::getSharding, shardingEnd)
                .eq(BufferPostingDetailPO::getAccountingDate, accountingDate)
                .eq(BufferPostingDetailPO::getIsDelete, 0)
                .orderByAsc(BufferPostingDetailPO::getId)
                .last("LIMIT " + limit);
        if (status != null) {
            wrapper.eq(BufferPostingDetailPO::getStatus, status);
        }
        return this.selectList(wrapper);
    }

    /**
     * 按会计日期和状态统计缓冲明细数量（Step 17 P0-1）
     */
    default int countByAccountingDateAndStatus(LocalDate accountingDate, Integer status) {
        LambdaQueryWrapper<BufferPostingDetailPO> wrapper = new LambdaQueryWrapper<BufferPostingDetailPO>()
                .eq(BufferPostingDetailPO::getAccountingDate, accountingDate)
                .eq(BufferPostingDetailPO::getIsDelete, 0);
        if (status != null) {
            wrapper.eq(BufferPostingDetailPO::getStatus, status);
        }
        return this.selectCount(wrapper).intValue();
    }

    /**
     * 按账户汇总缓冲金额（用于 bufferMode=2 日间批量）
     * <p>
     * 涉及 GROUP BY + SUM 聚合，XML 实现。
     */
    List<Map<String, Object>> sumByAccountNo(
            @Param("accountingDate") LocalDate accountingDate,
            @Param("status") Integer status);
}