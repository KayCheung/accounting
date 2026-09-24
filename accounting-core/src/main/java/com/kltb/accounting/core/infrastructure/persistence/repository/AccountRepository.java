package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.AccountStatusEnum;
import com.kltb.accounting.core.domain.enums.RiskStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.dto.AccountWithSubAccountsDTO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountMapper;
import com.kltb.accounting.core.shared.exception.AccountException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * 账户持久化仓储
 */
@Repository
@RequiredArgsConstructor
public class AccountRepository {

    private final AccountMapper accountMapper;

    /**
     * 按账户编号加悲观锁查询
     *
     * @param accountNo 账户编号
     * @return 账户PO，不存在时返回null
     */
    public AccountPO selectForUpdate(String accountNo) {
        return accountMapper.selectForUpdate(accountNo);
    }

    /**
     * 按所有者查询账户列表
     *
     * @param ownerId 所有者ID
     * @return 账户列表，无数据时返回空列表
     */
    public List<AccountPO> selectByOwnerId(String ownerId) {
        List<AccountPO> result = accountMapper.selectList(new LambdaQueryWrapper<AccountPO>()
                .eq(AccountPO::getOwnerId, ownerId)
                .eq(AccountPO::getIsDelete, 0));
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 按账户编号查询余额 + 状态 + 风控状态
     *
     * @param accountNo 账户编号
     * @return 账户PO（仅含余额相关字段），不存在时返回null
     */
    public AccountPO selectBalanceByAccountNo(String accountNo) {
        return accountMapper.selectBalanceByAccountNo(accountNo);
    }

    /**
     * 按账户编号查询（不带锁）
     *
     * @param accountNo 账户编号
     * @return 账户PO，不存在时返回null
     */
    public AccountPO selectByAccountNo(String accountNo) {
        return accountMapper.selectOne(new LambdaQueryWrapper<AccountPO>()
                .eq(AccountPO::getAccountNo, accountNo)
                .eq(AccountPO::getIsDelete, 0));
    }

    /**
     * 按账户编号列表加悲观锁批量查询
     * 调用方必须保证 accountNos 已按升序排序
     */
    public List<AccountPO> selectForUpdateBatch(List<String> accountNos) {
        return accountMapper.selectForUpdateBatch(accountNos);
    }

    /**
     * 插入账户
     */
    public void insert(AccountPO account) {
        accountMapper.insert(account);
    }

    /**
     * 更新账户（带乐观锁）
     */
    public boolean updateById(AccountPO account) {
        return accountMapper.updateById(account) > 0;
    }

    /**
     * 按账户编号联查账户及其子账户
     *
     * @param accountNo 账户编号
     * @return 账户含子账户的DTO，不存在时返回null
     */
    public AccountWithSubAccountsDTO selectWithSubAccounts(String accountNo) {
        return accountMapper.selectWithSubAccounts(accountNo);
    }

    /**
     * 按所有者ID + 科目编码检查账户是否存在（防重复开户）
     *
     * @param ownerId 所有者ID
     * @param subjectCode 科目编码
     * @return true 表示账户已存在
     */
    public boolean existsByOwnerIdAndSubjectCode(String ownerId, String subjectCode) {
        return accountMapper.existsByOwnerIdAndSubjectCode(ownerId, subjectCode);
    }

    /**
     * 按所有者ID + 科目编码查询账户
     *
     * @param ownerId 所有者ID
     * @param subjectCode 科目编码
     * @return 账户PO，不存在时返回null
     */
    public AccountPO selectByOwnerIdAndSubjectCode(String ownerId, String subjectCode) {
        return accountMapper.selectByOwnerIdAndSubjectCode(ownerId, subjectCode);
    }

    /**
     * 按账户编号更新状态（乐观锁）
     */
    public void updateStatus(String accountNo, AccountStatusEnum status, Long version) {
        int affected = accountMapper.updateStatusByAccountNo(accountNo, status, version);
        if (affected == 0) {
            throw new AccountException(ResultCode.OPTIMISTIC_LOCK_FAILED, "账户状态更新冲突: " + accountNo);
        }
    }

    /**
     * 按账户编号更新风控状态（乐观锁）
     */
    public void updateRiskStatus(String accountNo, RiskStatusEnum riskStatus, Long version) {
        int affected = accountMapper.updateRiskStatusByAccountNo(accountNo, riskStatus, version);
        if (affected == 0) {
            throw new AccountException(ResultCode.OPTIMISTIC_LOCK_FAILED, "账户风控状态更新冲突: " + accountNo);
        }
    }

    /**
     * 按账户编号更新余额（乐观锁）
     */
    public void updateBalance(String accountNo, BigDecimal newBalance, Long version) {
        int affected = accountMapper.updateBalance(accountNo, newBalance, version);
        if (affected == 0) {
            throw new AccountException(ResultCode.OPTIMISTIC_LOCK_FAILED, "主账户余额更新冲突: " + accountNo);
        }
    }

    /**
     * 分页查询账户
     */
    public Page<AccountPO> selectPage(Page<AccountPO> pageParam, LambdaQueryWrapper<AccountPO> wrapper) {
        return accountMapper.selectPage(pageParam, wrapper);
    }
}
