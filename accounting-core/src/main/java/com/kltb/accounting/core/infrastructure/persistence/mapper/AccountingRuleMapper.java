// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/AccountingRuleMapper.java
package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingRulePO;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kltb.accounting.core.domain.enums.RuleStatusEnum;

import java.util.List;

/**
 * 记账规则表数据访问层。
 */
@Mapper
public interface AccountingRuleMapper extends BaseMapper<AccountingRulePO> {

    /**
     * 按业务键查询记账规则
     *
     * @param businessCode 业务线编码
     * @param tradingCode 交易编码
     * @param payChannel 支付渠道
     * @return 记账规则PO，不存在时返回null
     */
    default AccountingRulePO selectByBusinessKey(String businessCode,
                                                  String tradingCode,
                                                  String payChannel) {
        return this.selectOne(new LambdaQueryWrapper<AccountingRulePO>()
                .eq(AccountingRulePO::getBusinessCode, businessCode)
                .eq(AccountingRulePO::getTradingCode, tradingCode)
                .eq(AccountingRulePO::getPayChannel, payChannel)
                .eq(AccountingRulePO::getStatus, RuleStatusEnum.ENABLED)
                .eq(AccountingRulePO::getIsDelete, 0));
    }

    /**
     * 查询已启用的记账规则
     *
     * @return 已启用的规则列表
     */
    default List<AccountingRulePO> selectEnabledRules() {
        return this.selectList(new LambdaQueryWrapper<AccountingRulePO>()
                .eq(AccountingRulePO::getStatus, RuleStatusEnum.ENABLED)
                .eq(AccountingRulePO::getIsDelete, 0));
    }
}