package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.core.domain.enums.AvailableStatusEnum;
import com.kltb.accounting.core.domain.enums.TransferTypeEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.PeriodEndTransferRulePO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.PeriodEndTransferRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

/**
 * 期末结转规则仓储
 */
@Repository
@RequiredArgsConstructor
public class PeriodEndTransferRuleRepository {

    private final PeriodEndTransferRuleMapper ruleMapper;

    /**
     * 查询启用状态的规则列表（按执行顺序升序）
     */
    public List<PeriodEndTransferRulePO> selectEnabledRules(TransferTypeEnum transferType) {
        List<PeriodEndTransferRulePO> result = ruleMapper.selectEnabledRules(
                transferType != null ? transferType.getCode() : null);
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 查询在指定会计日期下满足自动触发条件的规则列表
     * <p>
     * 过滤条件：已启用 + 支持自动结转 + 结转周期满足触发条件（每日/月末/季末/年末）
     *
     * @param accountingDate 会计日期
     * @param transferType   结转类型（可选）
     * @return 满足自动触发条件的规则列表
     */
    public List<PeriodEndTransferRulePO> selectAutoTriggerableRules(java.time.LocalDate accountingDate, TransferTypeEnum transferType) {
        List<PeriodEndTransferRulePO> enabledRules = selectEnabledRules(transferType);
        if (enabledRules.isEmpty()) {
            return Collections.emptyList();
        }
        return enabledRules.stream()
                .filter(r -> Boolean.TRUE.equals(r.getAutoTransfer()))
                .filter(r -> r.getPeriodCycle() == null || r.getPeriodCycle().isTriggerable(accountingDate))
                .toList();
    }

    /**
     * 分页查询结转规则
     */
    public Page<PeriodEndTransferRulePO> selectPage(int pageNo, int pageSize,
                                                    String ruleCode, String ruleName,
                                                    Integer transferType, Integer status,
                                                    Boolean autoTransfer, Integer periodCycle) {
        Page<PeriodEndTransferRulePO> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<PeriodEndTransferRulePO> wrapper = new LambdaQueryWrapper<PeriodEndTransferRulePO>()
                .eq(PeriodEndTransferRulePO::getIsDelete, 0);

        boolean hasCode = ruleCode != null && !ruleCode.isBlank();
        boolean hasName = ruleName != null && !ruleName.isBlank();
        if (hasCode && hasName && ruleCode.trim().equalsIgnoreCase(ruleName.trim())) {
            String keyword = ruleCode.trim();
            wrapper.and(w -> w.like(PeriodEndTransferRulePO::getRuleCode, keyword)
                    .or().like(PeriodEndTransferRulePO::getRuleName, keyword));
        } else {
            wrapper.like(hasCode, PeriodEndTransferRulePO::getRuleCode, hasCode ? ruleCode.trim() : null)
                    .like(hasName, PeriodEndTransferRulePO::getRuleName, hasName ? ruleName.trim() : null);
        }

        wrapper.orderByAsc(PeriodEndTransferRulePO::getExecuteOrder)
                .orderByDesc(PeriodEndTransferRulePO::getId);

        if (transferType != null) {
            wrapper.eq(PeriodEndTransferRulePO::getTransferType, TransferTypeEnum.fromCode(transferType));
        }
        if (status != null) {
            wrapper.eq(PeriodEndTransferRulePO::getStatus, AvailableStatusEnum.fromCode(status));
        }
        if (autoTransfer != null) {
            wrapper.eq(PeriodEndTransferRulePO::getAutoTransfer, autoTransfer);
        }
        if (periodCycle != null) {
            wrapper.eq(PeriodEndTransferRulePO::getPeriodCycle, com.kltb.accounting.core.domain.enums.PeriodCycleEnum.fromCode(periodCycle));
        }

        return ruleMapper.selectPage(page, wrapper);
    }


    /**
     * 根据主键查询规则
     */
    public PeriodEndTransferRulePO findById(Long id) {
        return ruleMapper.selectOne(new LambdaQueryWrapper<PeriodEndTransferRulePO>()
                .eq(PeriodEndTransferRulePO::getId, id)
                .eq(PeriodEndTransferRulePO::getIsDelete, 0), false);
    }

    /**
     * 根据规则编码查询
     */
    public PeriodEndTransferRulePO findByRuleCode(String ruleCode) {
        return ruleMapper.selectOne(new LambdaQueryWrapper<PeriodEndTransferRulePO>()
                .eq(PeriodEndTransferRulePO::getRuleCode, ruleCode)
                .eq(PeriodEndTransferRulePO::getIsDelete, 0), false);
    }

    /**
     * 检查规则编码是否存在
     */
    public boolean existsByRuleCode(String ruleCode, Long excludeId) {
        LambdaQueryWrapper<PeriodEndTransferRulePO> wrapper = new LambdaQueryWrapper<PeriodEndTransferRulePO>()
                .eq(PeriodEndTransferRulePO::getRuleCode, ruleCode)
                .eq(PeriodEndTransferRulePO::getIsDelete, 0);
        if (excludeId != null) {
            wrapper.ne(PeriodEndTransferRulePO::getId, excludeId);
        }
        Long count = ruleMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    /**
     * 插入新规则
     */
    public void insert(PeriodEndTransferRulePO rule) {
        ruleMapper.insert(rule);
    }

    /**
     * 更新规则
     */
    public int updateById(PeriodEndTransferRulePO rule) {
        return ruleMapper.updateById(rule);
    }

    /**
     * 切换状态
     */
    public int updateStatus(Long id, AvailableStatusEnum status) {
        return ruleMapper.update(null, new LambdaUpdateWrapper<PeriodEndTransferRulePO>()
                .eq(PeriodEndTransferRulePO::getId, id)
                .eq(PeriodEndTransferRulePO::getIsDelete, 0)
                .set(PeriodEndTransferRulePO::getStatus, status));
    }

    /**
     * 逻辑删除规则
     */
    public int deleteById(Long id) {
        return ruleMapper.update(null, new LambdaUpdateWrapper<PeriodEndTransferRulePO>()
                .eq(PeriodEndTransferRulePO::getId, id)
                .eq(PeriodEndTransferRulePO::getIsDelete, 0)
                .set(PeriodEndTransferRulePO::getIsDelete, System.currentTimeMillis()));
    }
}
