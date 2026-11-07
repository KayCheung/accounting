package com.kltb.accounting.core.infrastructure.persistence.dto;

import com.kltb.accounting.core.infrastructure.persistence.entity.AccountPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.SubAccountPO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 账户及其子账户的聚合DTO，用于联查场景
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AccountWithSubAccountsDTO extends AccountPO {

    /**
     * 该账户下的子账户列表（可用余额 + 冻结余额）
     */
    private List<SubAccountPO> subAccounts;
}
