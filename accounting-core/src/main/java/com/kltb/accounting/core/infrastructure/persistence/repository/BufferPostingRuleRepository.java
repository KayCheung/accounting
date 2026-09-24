package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.core.infrastructure.persistence.entity.BufferPostingRulePO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.BufferPostingRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 缓冲入账规则持久化仓储
 */
@Repository
@RequiredArgsConstructor
public class BufferPostingRuleRepository {

    private final BufferPostingRuleMapper bufferPostingRuleMapper;

    /**
     * 插入缓冲规则
     */
    public void insert(BufferPostingRulePO po) {
        bufferPostingRuleMapper.insert(po);
    }

    /**
     * 更新缓冲规则
     */
    public boolean updateById(BufferPostingRulePO po) {
        return bufferPostingRuleMapper.updateById(po) > 0;
    }

    /**
     * 按ID查询缓冲规则
     */
    public BufferPostingRulePO selectById(Long id) {
        return bufferPostingRuleMapper.selectOne(new LambdaQueryWrapper<BufferPostingRulePO>()
                .eq(BufferPostingRulePO::getId, id)
                .eq(BufferPostingRulePO::getIsDelete, 0));
    }

    /**
     * 分页查询缓冲规则
     */
    public Page<BufferPostingRulePO> page(Page<BufferPostingRulePO> pageParam,
                                           LambdaQueryWrapper<BufferPostingRulePO> wrapper) {
        return bufferPostingRuleMapper.selectPage(pageParam, wrapper);
    }

    /**
     * 查询同一业务组合下时间区间重叠的启用中规则
     *
     * @param businessCode 业务线编码
     * @param tradingCode 交易编码
     * @param payChannel 支付渠道
     * @param effectiveTime 生效时间
     * @param expirationTime 失效时间
     * @param excludeId 排除自身ID（更新时使用）
     * @return 重叠的规则列表，无数据时返回空列表
     */
    public List<BufferPostingRulePO> selectOverlappingRules(String businessCode, String tradingCode,
                                                              String payChannel, LocalDateTime effectiveTime,
                                                              LocalDateTime expirationTime, Long excludeId) {
        List<BufferPostingRulePO> result = bufferPostingRuleMapper.selectOverlappingRules(
                businessCode, tradingCode, payChannel, effectiveTime, expirationTime, excludeId);
        return result != null ? result : Collections.emptyList();
    }
}
