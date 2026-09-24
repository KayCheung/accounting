package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingRuleAuxiliaryPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingRuleDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingRulePO;
import com.kltb.accounting.core.infrastructure.persistence.entity.BufferPostingDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountingRuleAuxiliaryMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountingRuleDetailMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountingRuleMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.BufferPostingDetailMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 记账规则与缓冲记账持久化仓储
 */
@Repository
@RequiredArgsConstructor
public class AccountingRuleRepository {

    private final AccountingRuleMapper ruleMapper;
    private final AccountingRuleDetailMapper ruleDetailMapper;
    private final AccountingRuleAuxiliaryMapper ruleAuxiliaryMapper;
    private final BufferPostingDetailMapper bufferPostingDetailMapper;

    /**
     * 按业务键查询记账规则
     *
     * @param businessCode 业务线编码
     * @param tradingCode 交易编码
     * @param payChannel 支付渠道
     * @return 规则PO，不存在时返回null
     */
    public AccountingRulePO selectByBusinessKey(String businessCode, String tradingCode, String payChannel) {
        return ruleMapper.selectByBusinessKey(businessCode, tradingCode, payChannel);
    }

    /**
     * 查询已启用的记账规则
     *
     * @return 已启用规则列表，无数据时返回空列表
     */
    public List<AccountingRulePO> selectEnabledRules() {
        List<AccountingRulePO> result = ruleMapper.selectEnabledRules();
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 插入规则
     */
    public void insertRule(AccountingRulePO rule) {
        ruleMapper.insert(rule);
    }

    /**
     * 更新规则
     */
    public boolean updateRuleById(AccountingRulePO rule) {
        return ruleMapper.updateById(rule) > 0;
    }

    /**
     * 按规则ID查询规则明细（含辅助核算项）
     *
     * @param ruleId 规则ID
     * @return 规则明细列表，无数据时返回空列表
     */
    public List<AccountingRuleDetailPO> selectDetailsWithAuxiliary(Long ruleId) {
        List<AccountingRuleDetailPO> result = ruleDetailMapper.selectWithAuxiliary(ruleId);
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 插入规则明细
     */
    public void insertRuleDetail(AccountingRuleDetailPO detail) {
        ruleDetailMapper.insert(detail);
    }

    /**
     * 按规则明细ID查询辅助核算项
     *
     * @param ruleDetailId 规则明细ID
     * @return 辅助核算项列表，无数据时返回空列表
     */
    public List<AccountingRuleAuxiliaryPO> selectAuxiliaryByRuleDetailId(Long ruleDetailId) {
        List<AccountingRuleAuxiliaryPO> result = ruleAuxiliaryMapper.selectList(
                new LambdaQueryWrapper<AccountingRuleAuxiliaryPO>()
                        .eq(AccountingRuleAuxiliaryPO::getRuleDetailId, ruleDetailId)
                        .eq(AccountingRuleAuxiliaryPO::getIsDelete, 0));
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 按规则ID批量查询所有辅助核算项（一次查询，避免N+1）
     *
     * @param ruleId 规则ID
     * @return 按明细ID分组的辅助核算项映射
     */
    public Map<Long, List<AccountingRuleAuxiliaryPO>> selectAuxiliariesByRuleId(Long ruleId) {
        List<AccountingRuleAuxiliaryPO> result = ruleAuxiliaryMapper.selectByRuleId(ruleId);
        if (result == null || result.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, List<AccountingRuleAuxiliaryPO>> map = new HashMap<>();
        for (AccountingRuleAuxiliaryPO aux : result) {
            map.computeIfAbsent(aux.getRuleDetailId(), k -> new ArrayList<>()).add(aux);
        }
        return map;
    }

    /**
     * 插入辅助核算项
     */
    public void insertRuleAuxiliary(AccountingRuleAuxiliaryPO auxiliary) {
        ruleAuxiliaryMapper.insert(auxiliary);
    }

    /**
     * 按分片值查询缓冲记账明细
     *
     * @param sharding 分片值
     * @param status 状态（可选）
     * @return 缓冲记账明细列表，无数据时返回空列表
     */
    public List<BufferPostingDetailPO> selectBufferBySharding(Long sharding, Integer status) {
        List<BufferPostingDetailPO> result = bufferPostingDetailMapper.selectBySharding(sharding, status);
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 插入缓冲记账明细
     */
    public void insertBufferPosting(BufferPostingDetailPO detail) {
        bufferPostingDetailMapper.insert(detail);
    }

    /**
     * 更新缓冲记账明细（带乐观锁）
     */
    public boolean updateBufferPostingById(BufferPostingDetailPO detail) {
        return bufferPostingDetailMapper.updateById(detail) > 0;
    }

    /**
     * 按ID查询记账规则
     */
    public AccountingRulePO selectRuleById(Long id) {
        return ruleMapper.selectOne(new LambdaQueryWrapper<AccountingRulePO>()
                .eq(AccountingRulePO::getId, id)
                .eq(AccountingRulePO::getIsDelete, 0));
    }

    /**
     * 分页查询记账规则
     */
    public Page<AccountingRulePO> pageRule(Page<AccountingRulePO> pageParam,
                                            LambdaQueryWrapper<AccountingRulePO> wrapper) {
        return ruleMapper.selectPage(pageParam, wrapper);
    }

    /**
     * 逻辑删除指定规则下的全部明细
     */
    public void deleteRuleDetailByRuleId(Long ruleId) {
        ruleDetailMapper.delete(new LambdaQueryWrapper<AccountingRuleDetailPO>()
                .eq(AccountingRuleDetailPO::getRuleId, ruleId)
                .eq(AccountingRuleDetailPO::getIsDelete, 0));
    }

    /**
     * 逻辑删除指定明细的辅助核算项
     */
    public void deleteRuleAuxiliaryByRuleDetailId(Long ruleDetailId) {
        ruleAuxiliaryMapper.delete(new LambdaQueryWrapper<AccountingRuleAuxiliaryPO>()
                .eq(AccountingRuleAuxiliaryPO::getRuleDetailId, ruleDetailId)
                .eq(AccountingRuleAuxiliaryPO::getIsDelete, 0));
    }

    /**
     * 批量插入规则明细
     */
    public void insertRuleDetails(List<AccountingRuleDetailPO> details) {
        for (AccountingRuleDetailPO detail : details) {
            ruleDetailMapper.insert(detail);
        }
    }

    /**
     * 批量插入辅助核算项
     */
    public void insertRuleAuxiliaries(List<AccountingRuleAuxiliaryPO> auxiliaries) {
        for (AccountingRuleAuxiliaryPO auxiliary : auxiliaries) {
            ruleAuxiliaryMapper.insert(auxiliary);
        }
    }
}
