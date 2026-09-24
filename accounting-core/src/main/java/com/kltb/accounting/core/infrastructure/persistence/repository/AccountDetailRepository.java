package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountFreezeDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountDetailMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountFreezeDetailMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * 账户明细与冻结明细持久化仓储
 */
@Repository
@RequiredArgsConstructor
public class AccountDetailRepository {

    private final AccountDetailMapper accountDetailMapper;
    private final AccountFreezeDetailMapper accountFreezeDetailMapper;

    /**
     * 按凭证号查询账户明细列表
     *
     * @param voucherNo 凭证号
     * @return 明细列表，无数据时返回空列表
     */
    public List<AccountDetailPO> selectByVoucherNo(String voucherNo) {
        List<AccountDetailPO> result = accountDetailMapper.selectByVoucherNo(voucherNo);
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 插入账户明细
     */
    public void insert(AccountDetailPO detail) {
        accountDetailMapper.insert(detail);
    }

    /**
     * 插入冻结明细
     */
    public void insertFreeze(AccountFreezeDetailPO freezeDetail) {
        accountFreezeDetailMapper.insert(freezeDetail);
    }

    /**
     * 更新冻结明细（带乐观锁）
     */
    public boolean updateFreezeById(AccountFreezeDetailPO freezeDetail) {
        return accountFreezeDetailMapper.updateById(freezeDetail) > 0;
    }

    /**
     * 批量写入账户明细
     */
    public void batchInsert(List<AccountDetailPO> details) {
        for (AccountDetailPO detail : details) {
            accountDetailMapper.insert(detail);
        }
    }

    /**
     * 分页查询账户明细（按 accountNo + 日期范围 + 类型过滤）
     *
     * @param accountNo   账户编号
     * @param startDate   起始日期
     * @param endDate     结束日期
     * @param tradeType   交易类别
     * @param debitCredit 借贷方向
     * @param offset      偏移量
     * @param limit       每页条数
     * @return 分页结果（含 total/pages）
     */
    public IPage<AccountDetailPO> selectPageByCondition(String accountNo, LocalDate startDate, LocalDate endDate,
                                                         Integer tradeType, Integer debitCredit,
                                                         long offset, int limit) {
        return accountDetailMapper.selectPageByCondition(accountNo, startDate, endDate,
                tradeType, debitCredit, offset, limit);
    }

    /**
     * 统计账户明细总数
     */
    public Long countByCondition(String accountNo, LocalDate startDate, LocalDate endDate,
                                  Integer tradeType, Integer debitCredit) {
        return accountDetailMapper.countByCondition(accountNo, startDate, endDate, tradeType, debitCredit);
    }
}
