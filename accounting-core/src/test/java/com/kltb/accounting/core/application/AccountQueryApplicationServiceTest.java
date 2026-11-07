package com.kltb.accounting.core.application;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.api.request.AccountPageQueryRequest;
import com.kltb.accounting.api.response.AccountPageResponse;
import com.kltb.accounting.api.response.PageResponse;
import com.kltb.accounting.core.domain.enums.AccountStatusEnum;
import com.kltb.accounting.core.domain.enums.BalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.BalanceTypeEnum;
import com.kltb.accounting.core.domain.enums.OwnerTypeEnum;
import com.kltb.accounting.core.domain.enums.RiskStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.SubAccountPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubjectRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubAccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountQueryApplicationServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private SubAccountRepository subAccountRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @InjectMocks
    private AccountQueryApplicationService service;

    @Test
    @DisplayName("page: 正常分页查询，正确组装科目名称与子账户可用/冻结余额")
    void page_normal_shouldAssembleBalancesAndSubjectName() {
        AccountPO account = new AccountPO();
        account.setId(101L);
        account.setAccountNo("0012026030100001");
        account.setAccountName("张三-现金账户");
        account.setOwnerId("CUST001");
        account.setOwnerType(OwnerTypeEnum.INDIVIDUAL);
        account.setSubjectCode("100101");
        account.setAccountType("CASH");
        account.setCurrency("CNY");
        account.setBalanceDirection(BalanceDirectionEnum.DEBIT);
        account.setBalance(new BigDecimal("5000.00"));
        account.setStatus(AccountStatusEnum.NORMAL);
        account.setRiskStatus(RiskStatusEnum.NORMAL);
        account.setOpenDate(LocalDate.of(2026, 3, 1));
        account.setCreateTime(LocalDateTime.now());

        Page<AccountPO> repoPage = new Page<>(1, 10);
        repoPage.setRecords(List.of(account));
        repoPage.setTotal(1L);

        when(accountRepository.selectPage(any(), any())).thenReturn(repoPage);

        // Sub accounts: available 4000, frozen 1000
        SubAccountPO availableSub = new SubAccountPO();
        availableSub.setAccountNo("0012026030100001");
        availableSub.setBalanceType(BalanceTypeEnum.AVAILABLE);
        availableSub.setBalance(new BigDecimal("4000.00"));

        SubAccountPO frozenSub = new SubAccountPO();
        frozenSub.setAccountNo("0012026030100001");
        frozenSub.setBalanceType(BalanceTypeEnum.FROZEN);
        frozenSub.setBalance(new BigDecimal("1000.00"));

        when(subAccountRepository.selectByAccountNos(List.of("0012026030100001")))
                .thenReturn(List.of(availableSub, frozenSub));

        // Subject name
        AccountSubjectPO subject = new AccountSubjectPO();
        subject.setSubjectCode("100101");
        subject.setSubjectName("库存现金");
        when(subjectRepository.selectList(any())).thenReturn(List.of(subject));

        AccountPageQueryRequest req = new AccountPageQueryRequest();
        req.setPageNo(1);
        req.setPageSize(10);
        req.setAccountCategory("CUSTOMER");

        PageResponse<AccountPageResponse> result = service.page(req);

        assertThat(result).isNotNull();
        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getList()).hasSize(1);

        AccountPageResponse row = result.getList().get(0);
        assertThat(row.getAccountNo()).isEqualTo("0012026030100001");
        assertThat(row.getSubjectName()).isEqualTo("库存现金");
        assertThat(row.getBalance()).isEqualByComparingTo("5000.00");
        assertThat(row.getAvailableBalance()).isEqualByComparingTo("4000.00");
        assertThat(row.getFrozenBalance()).isEqualByComparingTo("1000.00");
        assertThat(row.getOwnerTypeDesc()).isEqualTo("个人");
        assertThat(row.getStatusDesc()).isEqualTo("正常");
        assertThat(row.getAccountTypeName()).isEqualTo("现金账户");
    }

    @Test
    @DisplayName("page: 无数据时返回空列表")
    void page_emptyResult_shouldReturnEmpty() {
        Page<AccountPO> emptyPage = new Page<>(1, 10);
        emptyPage.setRecords(Collections.emptyList());
        emptyPage.setTotal(0L);

        when(accountRepository.selectPage(any(), any())).thenReturn(emptyPage);

        PageResponse<AccountPageResponse> result = service.page(new AccountPageQueryRequest());

        assertThat(result).isNotNull();
        assertThat(result.getTotal()).isEqualTo(0L);
        assertThat(result.getList()).isEmpty();
    }
}
