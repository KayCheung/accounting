// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/PeriodEndTransferRuleMapper.java
package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.domain.enums.AvailableStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.PeriodEndTransferRulePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 期末结转规则 Mapper
 * <p>
 * 对应表：t_period_end_transfer_rule
 */
@Mapper
public interface PeriodEndTransferRuleMapper extends BaseMapper<PeriodEndTransferRulePO> {

    /**
     * 查询启用状态的结转规则，按执行顺序升序
     *
     * @param transferType 结转类型（可选，null 表示查全部）
     * @return 启用中的规则列表
     */
    default List<PeriodEndTransferRulePO> selectEnabledRules(@Param("transferType") Integer transferType) {
        LambdaQueryWrapper<PeriodEndTransferRulePO> wrapper = new LambdaQueryWrapper<PeriodEndTransferRulePO>()
                .eq(PeriodEndTransferRulePO::getStatus, AvailableStatusEnum.ENABLED)
                .eq(PeriodEndTransferRulePO::getIsDelete, 0)
                .orderByAsc(PeriodEndTransferRulePO::getExecuteOrder);
        if (transferType != null) {
            wrapper.eq(PeriodEndTransferRulePO::getTransferType, transferType);
        }
        return this.selectList(wrapper);
    }
}
