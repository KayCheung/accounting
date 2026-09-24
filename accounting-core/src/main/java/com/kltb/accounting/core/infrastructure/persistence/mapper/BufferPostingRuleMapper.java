// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/BufferPostingRuleMapper.java
package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.BufferPostingRulePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 缓冲入账规则表数据访问层。
 */
@Mapper
public interface BufferPostingRuleMapper extends BaseMapper<BufferPostingRulePO> {

    /**
     * 查询同一业务组合下时间区间重叠的启用中规则
     *
     * @param businessCode 业务线编码
     * @param tradingCode 交易编码
     * @param payChannel 支付渠道
     * @param effectiveTime 生效时间
     * @param expirationTime 失效时间
     * @param excludeId 排除自身ID（更新时使用）
     * @return 重叠的规则列表
     */
    List<BufferPostingRulePO> selectOverlappingRules(@Param("businessCode") String businessCode,
                                                      @Param("tradingCode") String tradingCode,
                                                      @Param("payChannel") String payChannel,
                                                      @Param("effectiveTime") LocalDateTime effectiveTime,
                                                      @Param("expirationTime") LocalDateTime expirationTime,
                                                      @Param("excludeId") Long excludeId);

    /**
     * 查询匹配的缓冲规则
     * P1-6 修复：subject_code/account_no 含 NULL/空串处理
     */
    List<BufferPostingRulePO> selectMatchingRules(
            @Param("businessCode") String businessCode,
            @Param("tradingCode") String tradingCode,
            @Param("payChannel") String payChannel,
            @Param("subjectCode") String subjectCode,
            @Param("accountNo") String accountNo,
            @Param("debitCredit") Integer debitCredit,
            @Param("accountingDate") LocalDate accountingDate);
}