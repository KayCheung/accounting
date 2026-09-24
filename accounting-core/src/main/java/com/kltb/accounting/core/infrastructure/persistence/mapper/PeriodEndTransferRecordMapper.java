// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/PeriodEndTransferRecordMapper.java
package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.PeriodEndTransferRecordPO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.List;

/**
 * 期末结转记录 Mapper
 * <p>
 * 对应表：t_period_end_transfer_record
 */
@Mapper
public interface PeriodEndTransferRecordMapper extends BaseMapper<PeriodEndTransferRecordPO> {

    /**
     * 按会计日期查询结转记录
     *
     * @param accountingDate 会计日期
     * @return 该日期的所有结转记录
     */
    default List<PeriodEndTransferRecordPO> selectByAccountingDate(LocalDate accountingDate) {
        return this.selectList(new LambdaQueryWrapper<PeriodEndTransferRecordPO>()
                .eq(PeriodEndTransferRecordPO::getAccountingDate, accountingDate)
                .eq(PeriodEndTransferRecordPO::getIsDelete, 0));
    }
}
