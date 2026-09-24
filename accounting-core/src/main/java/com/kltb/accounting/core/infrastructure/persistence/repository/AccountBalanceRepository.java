package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountBalancePO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountBalanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class AccountBalanceRepository {

    private final AccountBalanceMapper accountBalanceMapper;

    public void batchUpsert(List<AccountBalancePO> list) {
        if (list == null || list.isEmpty()) {
            return;
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
}
