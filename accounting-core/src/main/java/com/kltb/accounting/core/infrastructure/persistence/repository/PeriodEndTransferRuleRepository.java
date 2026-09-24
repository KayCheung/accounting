package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.kltb.accounting.core.domain.enums.TransferTypeEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.PeriodEndTransferRulePO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.PeriodEndTransferRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PeriodEndTransferRuleRepository {

    private final PeriodEndTransferRuleMapper ruleMapper;

    public List<PeriodEndTransferRulePO> selectEnabledRules(TransferTypeEnum transferType) {
        List<PeriodEndTransferRulePO> result = ruleMapper.selectEnabledRules(
                transferType != null ? transferType.getCode() : null);
        return result != null ? result : Collections.emptyList();
    }
}
