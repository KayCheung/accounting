package com.kltb.accounting.core.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountDetailPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.math.BigDecimal;

/**
 * 账户明细表 Mapper 接口
 * <p>
 * DDL: docs/sql/1-account.sql (t_account_detail)
 * </p>
 */
@Mapper
public interface AccountDetailMapper extends BaseMapper<AccountDetailPO> {

    /**
     * 按凭证号查询账户明细列表
     */
    default List<AccountDetailPO> selectByVoucherNo(String voucherNo) {
        return this.selectList(new LambdaQueryWrapper<AccountDetailPO>()
                .eq(AccountDetailPO::getVoucherNo, voucherNo)
                .eq(AccountDetailPO::getIsDelete, 0));
    }

    /**
     * 分页查询账户明细（按 accountNo + 日期范围 + 类型过滤）
     * <p>
     * 功能描述：按账户编号、日期范围、交易类别、借贷方向等条件分页查询账户明细，
     * 使用 MyBatis-Plus Page 对象分页，自动执行 count 查询。
     * <p>
     * 是否记账：否，纯查询操作。
     * <p>
     * 异常处理：无特殊异常处理，参数为空时返回空列表。
     *
     * @param accountNo   账户编号
     * @param startDate   起始日期（可选）
     * @param endDate     结束日期（可选）
     * @param tradeType   交易类别（可选）
     * @param debitCredit 借贷方向（可选）
     * @param offset      偏移量
     * @param limit       每页条数
     * @return 分页结果（含 total/pages/records）
     */
    default IPage<AccountDetailPO> selectPageByCondition(String accountNo, LocalDate startDate, LocalDate endDate,
                                                          Integer tradeType, Integer debitCredit,
                                                          long offset, int limit) {
        LambdaQueryWrapper<AccountDetailPO> wrapper = new LambdaQueryWrapper<AccountDetailPO>()
                .eq(AccountDetailPO::getAccountNo, accountNo)
                .eq(AccountDetailPO::getIsDelete, 0)
                .ge(startDate != null, AccountDetailPO::getAccountingDate, startDate)
                .le(endDate != null, AccountDetailPO::getAccountingDate, endDate)
                .eq(tradeType != null, AccountDetailPO::getTradeType, tradeType)
                .eq(debitCredit != null, AccountDetailPO::getDebitCredit, debitCredit)
                .orderByDesc(AccountDetailPO::getTradeTime, AccountDetailPO::getId);
        return this.selectPage(new Page<>(offset / limit + 1, limit), wrapper);
    }

    /**
     * 统计账户明细总数（条件同 selectPageByCondition）
     * <p>
     * 功能描述：按与分页查询相同的条件统计账户明细总数。
     * <p>
     * 是否记账：否，纯查询操作。
     * <p>
     * 异常处理：无特殊异常处理。
     */
    default Long countByCondition(String accountNo, LocalDate startDate, LocalDate endDate,
                                   Integer tradeType, Integer debitCredit) {
        LambdaQueryWrapper<AccountDetailPO> wrapper = new LambdaQueryWrapper<AccountDetailPO>()
                .eq(AccountDetailPO::getAccountNo, accountNo)
                .eq(AccountDetailPO::getIsDelete, 0)
                .ge(startDate != null, AccountDetailPO::getAccountingDate, startDate)
                .le(endDate != null, AccountDetailPO::getAccountingDate, endDate)
                .eq(tradeType != null, AccountDetailPO::getTradeType, tradeType)
                .eq(debitCredit != null, AccountDetailPO::getDebitCredit, debitCredit);
        return this.selectCount(wrapper);
    }

    /**
     * 查询指定账户在指定会计日的最后一条 post_balance（余额核对用）
     *
     * @param accountNo      账户编号
     * @param accountingDate 会计日期
     * @return 最后一条明细的 post_balance，无记录时返回 null
     */
    default BigDecimal selectLastPostBalance(String accountNo, LocalDate accountingDate) {
        AccountDetailPO po = this.selectOne(new LambdaQueryWrapper<AccountDetailPO>()
                .select(AccountDetailPO::getPostBalance)
                .eq(AccountDetailPO::getAccountNo, accountNo)
                .eq(AccountDetailPO::getAccountingDate, accountingDate)
                .eq(AccountDetailPO::getIsDelete, 0)
                .orderByDesc(AccountDetailPO::getId)
                .last("LIMIT 1"));
        return po != null ? po.getPostBalance() : null;
    }
}
