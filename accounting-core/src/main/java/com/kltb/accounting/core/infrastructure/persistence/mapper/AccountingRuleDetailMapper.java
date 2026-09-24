// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/AccountingRuleDetailMapper.java
package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingRuleDetailPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 记账规则明细表数据访问层。
 */
@Mapper
public interface AccountingRuleDetailMapper extends BaseMapper<AccountingRuleDetailPO> {

    /**
     * 按规则ID查询规则明细（含辅助核算项联查）
     *
     * @param ruleId 规则ID
     * @return 规则明细列表
     */
    java.util.List<AccountingRuleDetailPO> selectWithAuxiliary(@Param("ruleId") Long ruleId);

    /**
     * 按科目编码统计记账规则明细数量（停用科目校验用）
     *
     * @param subjectCode 科目编码
     * @return 关联该科目的未删除规则明细数
     */
    default int countBySubjectCode(String subjectCode) {
        return this.selectCount(new LambdaQueryWrapper<AccountingRuleDetailPO>()
                .eq(AccountingRuleDetailPO::getSubjectCode, subjectCode)
                .eq(AccountingRuleDetailPO::getIsDelete, 0)).intValue();
    }
}