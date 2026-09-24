// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/BusinessRecordMapper.java
package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.BusinessRecordPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

/**
 * 业务记账流水 Mapper
 * <p>
 * 对应表：t_business_record
 */
@Mapper
public interface BusinessRecordMapper extends BaseMapper<BusinessRecordPO> {

    /**
     * 按 traceNo + traceSeq 查询流水（幂等检查用）
     */
    @Select("SELECT * FROM t_business_record WHERE trace_no = #{traceNo} AND trace_seq = #{traceSeq} AND is_delete = 0 LIMIT 1")
    BusinessRecordPO selectByTraceNo(@Param("traceNo") String traceNo,
                                      @Param("traceSeq") Integer traceSeq);

    /**
     * 按 traceNo 查询流水（凭证生成用，不关心 traceSeq）
     */
    @Select("SELECT * FROM t_business_record WHERE trace_no = #{traceNo} AND is_delete = 0 ORDER BY id DESC LIMIT 1")
    BusinessRecordPO selectByTraceNoOnly(@Param("traceNo") String traceNo);

    /**
     * 按 traceNo 更新流水状态（开户失败时使用）
     */
    @Update("UPDATE t_business_record SET status = #{status}, update_time = NOW() WHERE trace_no = #{traceNo} AND is_delete = 0")
    int updateStatusByTraceNo(@Param("traceNo") String traceNo,
                               @Param("status") Integer status);

    /**
     * 按会计日期和状态统计业务流水数量（Step 17 P0-4）
     */
    default int countByAccountingDateAndStatus(LocalDate accountingDate, Integer status) {
        LambdaQueryWrapper<BusinessRecordPO> wrapper = new LambdaQueryWrapper<BusinessRecordPO>()
                .eq(BusinessRecordPO::getAccountingDate, accountingDate)
                .eq(BusinessRecordPO::getIsDelete, 0);
        if (status != null) {
            wrapper.eq(BusinessRecordPO::getStatus, status);
        }
        return this.selectCount(wrapper).intValue();
    }
}
