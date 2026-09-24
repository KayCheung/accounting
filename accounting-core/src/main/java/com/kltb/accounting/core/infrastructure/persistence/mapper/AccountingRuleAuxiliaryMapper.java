// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/AccountingRuleAuxiliaryMapper.java
package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingRuleAuxiliaryPO;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/**
 * 记账规则辅助核算项表数据访问层。
 */
@Mapper
public interface AccountingRuleAuxiliaryMapper extends BaseMapper<AccountingRuleAuxiliaryPO> {

    /**
     * 按规则ID批量查询辅助核算项
     *
     * @param ruleId 规则ID
     * @return 辅助核算项列表
     */
    default java.util.List<AccountingRuleAuxiliaryPO> selectByRuleId(Long ruleId) {
        return this.selectList(new LambdaQueryWrapper<AccountingRuleAuxiliaryPO>()
                .eq(AccountingRuleAuxiliaryPO::getRuleId, ruleId)
                .eq(AccountingRuleAuxiliaryPO::getIsDelete, 0)
                .orderByAsc(AccountingRuleAuxiliaryPO::getRuleDetailId)
                .orderByAsc(AccountingRuleAuxiliaryPO::getId));
    }
}