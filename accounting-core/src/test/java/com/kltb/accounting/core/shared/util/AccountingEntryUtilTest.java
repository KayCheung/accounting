package com.kltb.accounting.core.shared.util;

import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherEntryPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class AccountingEntryUtilTest {

    @Test
    @DisplayName("countDistinctAccounts: 集合为空或 null 返回 0")
    void countDistinctAccounts_emptyOrNull_shouldReturnZero() {
        assertThat(AccountingEntryUtil.countDistinctAccounts(null, Object::toString)).isEqualTo(0);
        assertThat(AccountingEntryUtil.countDistinctAccounts(Collections.emptyList(), Object::toString)).isEqualTo(0);
    }

    @Test
    @DisplayName("countDistinctAccounts: 正确去重并忽略空白账号")
    void countDistinctAccounts_shouldDeduplicateAndIgnoreBlank() {
        List<String> accounts = List.of("ACC001", "ACC002", "ACC001", "  ", "", "ACC003");
        int count = AccountingEntryUtil.countDistinctAccounts(accounts, s -> s);
        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("countDistinctAccountsFromEntryMap: 映射为空或 null 返回 0")
    void countDistinctAccountsFromEntryMap_emptyOrNull_shouldReturnZero() {
        assertThat(AccountingEntryUtil.countDistinctAccountsFromEntryMap(null)).isEqualTo(0);
        assertThat(AccountingEntryUtil.countDistinctAccountsFromEntryMap(Collections.emptyMap())).isEqualTo(0);
    }

    @Test
    @DisplayName("countDistinctAccountsFromEntryMap: 跨多凭证聚合去重分户账号")
    void countDistinctAccountsFromEntryMap_shouldDeduplicateAcrossVouchers() {
        AccountingVoucherEntryPO e1 = new AccountingVoucherEntryPO();
        e1.setAccountNo("ACC_A");
        AccountingVoucherEntryPO e2 = new AccountingVoucherEntryPO();
        e2.setAccountNo("ACC_B");

        AccountingVoucherEntryPO e3 = new AccountingVoucherEntryPO();
        e3.setAccountNo("ACC_B");
        AccountingVoucherEntryPO e4 = new AccountingVoucherEntryPO();
        e4.setAccountNo("ACC_C");
        AccountingVoucherEntryPO e5 = new AccountingVoucherEntryPO();
        e5.setAccountNo(null);

        Map<String, List<AccountingVoucherEntryPO>> map = Map.of(
                "VOU_1", List.of(e1, e2),
                "VOU_2", List.of(e3, e4, e5)
        );

        int count = AccountingEntryUtil.countDistinctAccountsFromEntryMap(map);
        assertThat(count).isEqualTo(3); // ACC_A, ACC_B, ACC_C
    }
}
