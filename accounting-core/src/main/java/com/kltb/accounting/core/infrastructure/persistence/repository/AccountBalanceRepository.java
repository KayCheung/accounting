package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountBalancePO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountBalanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import com.kltb.accounting.core.shared.context.TenantContext;

@Repository
@RequiredArgsConstructor
public class AccountBalanceRepository {

    private final AccountBalanceMapper accountBalanceMapper;

    public void batchUpsert(List<AccountBalancePO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Integer defaultTenant = TenantContext.get();
        for (AccountBalancePO po : list) {
            if (po.getTenantId() == null) {
                po.setTenantId(defaultTenant != null ? defaultTenant : TenantContext.SYSTEM_TENANT);
            }
        }
        accountBalanceMapper.batchUpsertBalance(list);
    }

    public List<AccountBalancePO> selectByDate(LocalDate accountingDate) {
        List<AccountBalancePO> result = accountBalanceMapper.selectList(
                new LambdaQueryWrapper<AccountBalancePO>()
                        .eq(AccountBalancePO::getAccountingDate, accountingDate)
                        .eq(AccountBalancePO::getIsDelete, 0));
        return result != null ? result : Collections.emptyList();
    }

    public AccountBalancePO selectPreviousDayBalance(String accountNo, LocalDate previousDate) {
        return accountBalanceMapper.selectOne(new LambdaQueryWrapper<AccountBalancePO>()
                .eq(AccountBalancePO::getAccountNo, accountNo)
                .eq(AccountBalancePO::getAccountingDate, previousDate)
                .eq(AccountBalancePO::getIsDelete, 0)
                .last("LIMIT 1"));
    }

    /**
     * 查询指定会计日期前最近的一笔账户日余额记录（跨天/容错）
     *
     * @param accountNo      账户编号
     * @param accountingDate 会计日期
     * @return 最近一笔日余额PO，若无则返回null
     */
    public AccountBalancePO selectLatestBalanceBeforeDate(String accountNo, LocalDate accountingDate) {
        return accountBalanceMapper.selectOne(new LambdaQueryWrapper<AccountBalancePO>()
                .eq(AccountBalancePO::getAccountNo, accountNo)
                .lt(AccountBalancePO::getAccountingDate, accountingDate)
                .eq(AccountBalancePO::getIsDelete, 0)
                .orderByDesc(AccountBalancePO::getAccountingDate)
                .last("LIMIT 1"));
    }

    /**
     * 按会计日期区间查询日余额记录
     */
    public List<AccountBalancePO> selectByDateRange(LocalDate startDate, LocalDate endDate) {
        List<AccountBalancePO> result = accountBalanceMapper.selectList(
                new LambdaQueryWrapper<AccountBalancePO>()
                        .ge(AccountBalancePO::getAccountingDate, startDate)
                        .le(AccountBalancePO::getAccountingDate, endDate)
                        .eq(AccountBalancePO::getIsDelete, 0)
                        .orderByAsc(AccountBalancePO::getAccountingDate));
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 按科目和会计日期区间查询日余额记录
     */
    public List<AccountBalancePO> selectBySubjectAndDateRange(String subjectCode, LocalDate startDate, LocalDate endDate) {
        List<AccountBalancePO> result = accountBalanceMapper.selectList(
                new LambdaQueryWrapper<AccountBalancePO>()
                        .eq(AccountBalancePO::getSubjectCode, subjectCode)
                        .ge(AccountBalancePO::getAccountingDate, startDate)
                        .le(AccountBalancePO::getAccountingDate, endDate)
                        .eq(AccountBalancePO::getIsDelete, 0)
                        .orderByAsc(AccountBalancePO::getAccountingDate));
        return result != null ? result : Collections.emptyList();
    }
}
