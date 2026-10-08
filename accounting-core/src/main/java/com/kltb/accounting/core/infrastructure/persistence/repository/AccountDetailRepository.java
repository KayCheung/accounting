package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountFreezeDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountDetailMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountFreezeDetailMapper;
import com.kltb.accounting.core.shared.exception.AccountException;
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
        if (detail == null) {
            throw new AccountException(ResultCode.PARAM_ERROR, "待插入账户明细对象不能为空");
        }
        int affected = accountDetailMapper.insert(detail);
        if (affected == 0) {
            throw new AccountException(ResultCode.SYSTEM_ERROR,
                    "写入账户明细失败: entryId=" + detail.getEntryId());
        }
    }

    /**
     * 插入冻结明细
     */
    public void insertFreeze(AccountFreezeDetailPO freezeDetail) {
        if (freezeDetail == null) {
            throw new AccountException(ResultCode.PARAM_ERROR, "待插入冻结明细对象不能为空");
        }
        int affected = accountFreezeDetailMapper.insert(freezeDetail);
        if (affected == 0) {
            throw new AccountException(ResultCode.SYSTEM_ERROR,
                    "写入冻结明细失败: voucherNo=" + freezeDetail.getVoucherNo());
        }
    }

    /**
     * 更新冻结明细（带乐观锁）
     */
    public boolean updateFreezeById(AccountFreezeDetailPO freezeDetail) {
        if (freezeDetail == null) {
            throw new AccountException(ResultCode.PARAM_ERROR, "待更新冻结明细对象不能为空");
        }
        int affected = accountFreezeDetailMapper.updateById(freezeDetail);
        if (affected == 0) {
            throw new AccountException(ResultCode.OPTIMISTIC_LOCK_FAILED,
                    "冻结明细更新失败(乐观锁版本冲突或记录不存在): voucherNo=" + freezeDetail.getVoucherNo());
        }
        return true;
    }

    /**
     * 批量写入账户明细
     */
    public void batchInsert(List<AccountDetailPO> details) {
        if (details == null || details.isEmpty()) {
            return;
        }
        for (AccountDetailPO detail : details) {
            insert(detail);
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
