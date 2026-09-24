package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.infrastructure.persistence.entity.SubAccountPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.SubAccountMapper;
import com.kltb.accounting.core.shared.exception.AccountException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * 子账户持久化仓储
 */
@Repository
@RequiredArgsConstructor
public class SubAccountRepository {

    private final SubAccountMapper subAccountMapper;

    /**
     * 按账户编号查询子账户列表
     *
     * @param accountNo 账户编号
     * @return 子账户列表，无数据时返回空列表
     */
    public List<SubAccountPO> selectByAccountNo(String accountNo) {
        List<SubAccountPO> result = subAccountMapper.selectList(new LambdaQueryWrapper<SubAccountPO>()
                .eq(SubAccountPO::getAccountNo, accountNo)
                .eq(SubAccountPO::getIsDelete, 0));
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 按账户编号查询所有子账户余额
     *
     * @param accountNo 账户编号
     * @return 子账户余额列表，无数据时返回空列表
     */
    public List<SubAccountPO> selectBalanceByAccountNo(String accountNo) {
        List<SubAccountPO> result = subAccountMapper.selectBalanceByAccountNo(accountNo);
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 按账户编号加悲观锁查询子账户
     *
     * @param accountNo 账户编号
     * @return 子账户列表，无数据时返回空列表
     */
    public List<SubAccountPO> selectForUpdate(String accountNo) {
        List<SubAccountPO> result = subAccountMapper.selectForUpdate(accountNo);
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 按账户编号 + 余额类型查询单个子账户
     *
     * @param accountNo   账户编号
     * @param balanceType 余额类型：1-可用, 2-冻结
     * @return 子账户 PO，不存在时返回 null
     */
    public SubAccountPO selectByAccountNoAndType(String accountNo, Integer balanceType) {
        return subAccountMapper.selectOne(new LambdaQueryWrapper<SubAccountPO>()
                .eq(SubAccountPO::getAccountNo, accountNo)
                .eq(SubAccountPO::getBalanceType, balanceType)
                .eq(SubAccountPO::getIsDelete, 0)
                .last("LIMIT 1"));
    }

    /**
     * 按账户编号 + 余额类型更新余额（乐观锁）
     *
     * @param accountNo   账户编号
     * @param balanceType 余额类型：1-可用, 2-冻结
     * @param newBalance  新余额
     * @param version     当前版本号
     */
    public void updateBalance(String accountNo, Integer balanceType, BigDecimal newBalance, Long version) {
        int affected = subAccountMapper.updateBalance(accountNo, balanceType, newBalance, version);
        if (affected == 0) {
            throw new AccountException(ResultCode.OPTIMISTIC_LOCK_FAILED, "子账户余额更新冲突: " + accountNo);
        }
    }

    /**
     * 插入子账户
     */
    public void insert(SubAccountPO subAccount) {
        subAccountMapper.insert(subAccount);
    }

    /**
     * 更新子账户（带乐观锁）
     */
    public boolean updateById(SubAccountPO subAccount) {
        return subAccountMapper.updateById(subAccount) > 0;
    }

    /**
     * 批量按账户编号查询子账户列表
     *
     * @param accountNos 账户编号列表
     * @return 子账户列表，无数据返回空列表
     */
    public List<SubAccountPO> selectByAccountNos(List<String> accountNos) {
        if (accountNos == null || accountNos.isEmpty()) {
            return Collections.emptyList();
        }
        List<SubAccountPO> result = subAccountMapper.selectList(new LambdaQueryWrapper<SubAccountPO>()
                .in(SubAccountPO::getAccountNo, accountNos)
                .eq(SubAccountPO::getIsDelete, 0));
        return result != null ? result : Collections.emptyList();
    }
}
