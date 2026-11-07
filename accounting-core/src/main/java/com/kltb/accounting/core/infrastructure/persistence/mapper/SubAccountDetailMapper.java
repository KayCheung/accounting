// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/SubAccountDetailMapper.java
package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.core.infrastructure.persistence.entity.SubAccountDetailPO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;

/**
 * 子账户明细表 Mapper 接口
 * <p>
 * DDL: docs/sql/1-account.sql (t_sub_account_detail)
 * </p>
 */
@Mapper
public interface SubAccountDetailMapper extends BaseMapper<SubAccountDetailPO> {

    /**
     * 分页查询子账户明细（按 accountNo + 子账户类型 + 日期范围 + 交易类别 + 借贷方向过滤）
     *
     * @param accountNo   账户编号
     * @param balanceType 子账户类型（可选：1-可用余额, 2-冻结余额）
     * @param startDate   起始日期（可选）
     * @param endDate     结束日期（可选）
     * @param tradeType   交易类别（可选）
     * @param debitCredit 借贷方向（可选）
     * @param offset      偏移量
     * @param limit       每页条数
     * @return 分页结果
     */
    default IPage<SubAccountDetailPO> selectPageByCondition(String accountNo, Integer balanceType,
                                                            LocalDate startDate, LocalDate endDate,
                                                            Integer tradeType, Integer debitCredit,
                                                            long offset, int limit) {
        LambdaQueryWrapper<SubAccountDetailPO> wrapper = new LambdaQueryWrapper<SubAccountDetailPO>()
                .eq(SubAccountDetailPO::getAccountNo, accountNo)
                .eq(SubAccountDetailPO::getIsDelete, 0)
                .eq(balanceType != null, SubAccountDetailPO::getBalanceType, balanceType)
                .ge(startDate != null, SubAccountDetailPO::getAccountingDate, startDate)
                .le(endDate != null, SubAccountDetailPO::getAccountingDate, endDate)
                .eq(tradeType != null, SubAccountDetailPO::getTradeType, tradeType)
                .eq(debitCredit != null, SubAccountDetailPO::getDebitCredit, debitCredit)
                .orderByDesc(SubAccountDetailPO::getTradeTime, SubAccountDetailPO::getId);
        return this.selectPage(new Page<>(offset / limit + 1, limit), wrapper);
    }

    /**
     * 统计子账户明细总数
     */
    default Long countByCondition(String accountNo, Integer balanceType,
                                   LocalDate startDate, LocalDate endDate,
                                   Integer tradeType, Integer debitCredit) {
        LambdaQueryWrapper<SubAccountDetailPO> wrapper = new LambdaQueryWrapper<SubAccountDetailPO>()
                .eq(SubAccountDetailPO::getAccountNo, accountNo)
                .eq(SubAccountDetailPO::getIsDelete, 0)
                .eq(balanceType != null, SubAccountDetailPO::getBalanceType, balanceType)
                .ge(startDate != null, SubAccountDetailPO::getAccountingDate, startDate)
                .le(endDate != null, SubAccountDetailPO::getAccountingDate, endDate)
                .eq(tradeType != null, SubAccountDetailPO::getTradeType, tradeType)
                .eq(debitCredit != null, SubAccountDetailPO::getDebitCredit, debitCredit);
        return this.selectCount(wrapper);
    }
}
